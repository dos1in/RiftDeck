#!/usr/bin/env python3
"""Refuse releases that cannot upgrade every published stable RiftDeck APK."""

import argparse
from dataclasses import dataclass
import json
from pathlib import Path
import re
import subprocess
import sys
import tempfile


MAX_APK_BYTES = 256 * 1024 * 1024
MAX_VERSION_CODE = 2_100_000_000
STABLE_TAG = re.compile(r"[vV]?(0|[1-9][0-9]{0,9})\.(0|[1-9][0-9]{0,9})\.(0|[1-9][0-9]{0,9})")
APK_NAME = re.compile(r"[A-Za-z0-9][A-Za-z0-9._-]{0,159}\.apk", re.IGNORECASE)
ARCHITECTURE = re.compile(r"(?:^|[-_.])(?:arm64(?:-v8a)?|armv7|arm|armeabi(?:-v7a)?|aarch64|x86(?:_64)?)(?:[-_.]|$)", re.IGNORECASE)


class ReleaseValidationError(ValueError):
    pass


@dataclass(frozen=True)
class PublishedApk:
    name: str
    size: int


@dataclass(frozen=True)
class StableRelease:
    tag: str
    version: tuple[int, int, int]
    apks: tuple[PublishedApk, ...]


@dataclass(frozen=True)
class ApkVersion:
    version: tuple[int, int, int]
    code: int


def parse_stable_version(value):
    if not isinstance(value, str):
        return None
    match = STABLE_TAG.fullmatch(value)
    if not match:
        return None
    version = tuple(map(int, match.groups()))
    return version if all(component <= 2_147_483_647 for component in version) else None


def requested_version(name, code):
    version = parse_stable_version(name)
    # Match Gradle's official release inputs: no tag prefix and at most nine digits.
    if version is None or name != ".".join(map(str, version)) or any(component > 999_999_999 for component in version):
        raise ReleaseValidationError("Use a stable major.minor.patch version name")
    if not isinstance(code, str) or not re.fullmatch(r"[1-9][0-9]{0,9}", code) or not 1 <= int(code) <= MAX_VERSION_CODE:
        raise ReleaseValidationError("Use an Android version code between 1 and 2100000000")
    return ApkVersion(version, int(code))


def parse_release_pages(pages):
    """gh api --paginate --slurp returns one array of page arrays, including [[]]."""
    if not isinstance(pages, list) or any(not isinstance(page, list) for page in pages):
        raise ReleaseValidationError("Invalid published release list")
    releases = []
    for page in pages:
        for row in page:
            if not isinstance(row, dict) or type(row.get("draft")) is not bool or type(row.get("prerelease")) is not bool:
                raise ReleaseValidationError("Invalid published release flags")
            if row["draft"] or row["prerelease"]:
                continue
            version = parse_stable_version(row.get("tag_name"))
            if version is None:
                continue
            tag = row["tag_name"]
            assets = row.get("assets")
            if not isinstance(assets, list):
                raise ReleaseValidationError("Invalid published release assets")
            official_names = {name.lower() for name in (
                f"RiftDeck-{tag}.apk", f"RiftDeck-{'.'.join(map(str, version))}.apk", "RiftDeck.apk", "app-release.apk",
            )}
            apks = []
            names = set()
            candidates = []
            for asset in assets:
                if not isinstance(asset, dict) or not isinstance(asset.get("name"), str):
                    raise ReleaseValidationError("Invalid published release asset")
                name = asset["name"]
                if not APK_NAME.fullmatch(name) or "debug" in name.lower() or "unsigned" in name.lower() or ARCHITECTURE.search(name):
                    continue
                candidates.append(asset)
            official = [asset for asset in candidates if asset["name"].lower() in official_names]
            # The app also accepts one unambiguously named universal APK from this repository.
            if not official and len(candidates) > 1:
                raise ReleaseValidationError("Published stable release APK is ambiguous")
            for asset in official or candidates:
                name = asset["name"]
                if name.lower() in names or asset.get("state") != "uploaded":
                    raise ReleaseValidationError("Official release APK is ambiguous or unavailable")
                size = asset.get("size")
                if type(size) is not int or not 1 <= size <= MAX_APK_BYTES:
                    raise ReleaseValidationError("Invalid official release APK size")
                names.add(name.lower())
                apks.append(PublishedApk(name, size))
            releases.append(StableRelease(tag, version, tuple(apks)))
    return tuple(releases)


def parse_apk_badging(text):
    lines = [line for line in text.splitlines() if line.startswith("package: ")]
    if len(lines) != 1:
        raise ReleaseValidationError("Could not read official APK package")
    fields = {}
    for key, value in re.findall(r"(?:^|\s)(name|versionCode|versionCodeMajor|versionName)='([^']*)'", lines[0]):
        if key in fields:
            raise ReleaseValidationError("Ambiguous official APK package")
        fields[key] = value
    if fields.get("name") != "com.riftdeck":
        raise ReleaseValidationError("Official release APK has the wrong package")
    version = parse_stable_version(fields.get("versionName"))
    code = fields.get("versionCode", "")
    major = fields.get("versionCodeMajor", "0")
    if version is None or not re.fullmatch(r"[0-9]{1,19}", code) or not re.fullmatch(r"[0-9]{1,10}", major):
        raise ReleaseValidationError("Invalid official APK version")
    code, major = int(code), int(major)
    if major > 0x7fff_ffff or code > 0x7fff_ffff_ffff_ffff or (major and code > 0xffff_ffff):
        raise ReleaseValidationError("Invalid official APK version code")
    return ApkVersion(version, (major << 32) | code)


def validate_increment(requested, releases, inspect_apk):
    for release in releases:
        if requested.version <= release.version:
            raise ReleaseValidationError(f"Version name must exceed published {release.tag}")
    checked = 0
    for release in releases:
        for asset in release.apks:
            previous = inspect_apk(release.tag, asset)
            if previous.version != release.version:
                raise ReleaseValidationError(f"Published {release.tag} APK does not match its stable tag")
            if requested.code <= previous.code:
                raise ReleaseValidationError(f"Version code must exceed {previous.code} from {release.tag}")
            checked += 1
    return checked


def run_command(arguments, *, timeout=120):
    try:
        return subprocess.run([str(value) for value in arguments], check=True, capture_output=True, text=True, timeout=timeout).stdout
    except (OSError, subprocess.SubprocessError):
        # Do not echo command environments, authentication headers, or remote stderr.
        raise ReleaseValidationError("Could not inspect published releases; publication stopped") from None


def check_published(repository, requested, aapt):
    if not re.fullmatch(r"[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+", repository):
        raise ReleaseValidationError("Invalid GitHub repository")
    response = run_command(["gh", "api", "--hostname", "github.com", "--paginate", "--slurp",
                            f"repos/{repository}/releases?per_page=100"])
    try:
        releases = parse_release_pages(json.loads(response))
    except json.JSONDecodeError:
        raise ReleaseValidationError("Invalid published release response") from None
    with tempfile.TemporaryDirectory(prefix="riftdeck-release-check-") as temporary:
        destination = Path(temporary) / "published.apk"

        def inspect(tag, asset):
            destination.unlink(missing_ok=True)
            run_command(["gh", "release", "download", tag, "--repo", f"github.com/{repository}",
                         "--pattern", asset.name, "--output", destination])
            if not destination.is_file() or destination.stat().st_size != asset.size:
                raise ReleaseValidationError("Published APK download is incomplete")
            return parse_apk_badging(run_command([aapt, "dump", "badging", destination], timeout=30))

        checked = validate_increment(requested, releases, inspect)
    return len(releases), checked


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repository", required=True)
    parser.add_argument("--version-name", required=True)
    parser.add_argument("--version-code", required=True)
    parser.add_argument("--aapt", type=Path, required=True)
    args = parser.parse_args()
    try:
        requested = requested_version(args.version_name, args.version_code)
        releases, apks = check_published(args.repository, requested, args.aapt)
        print(f"Release version verified against {releases} stable releases and {apks} official APKs")
    except ReleaseValidationError as error:
        print(f"Release validation failed: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
