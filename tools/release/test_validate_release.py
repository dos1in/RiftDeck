import json
from pathlib import Path
import subprocess
import unittest
from unittest.mock import patch

from validate_release import (
    ApkVersion, PublishedApk, ReleaseValidationError, StableRelease, check_published,
    parse_apk_badging, parse_release_pages, parse_stable_version, requested_version, run_command, validate_increment,
)


def row(tag="v1.2.3", *, draft=False, prerelease=False, assets=None):
    return {"tag_name": tag, "draft": draft, "prerelease": prerelease,
            "assets": assets if assets is not None else [{"name": f"RiftDeck-{tag}.apk", "state": "uploaded", "size": 42}]}


class ReleaseValidationTest(unittest.TestCase):
    def test_stable_versions_compare_numerically_without_overflow(self):
        self.assertGreater(parse_stable_version("v1.10.0"), parse_stable_version("1.9.9"))
        self.assertEqual((2_147_483_647, 0, 0), parse_stable_version("2147483647.0.0"))
        for value in ("01.2.3", "1.2", "1.2.3-beta", "1.2.3+build", " 1.2.3", "2147483648.0.0", None):
            self.assertIsNone(parse_stable_version(value))

    def test_workflow_input_limits_match_gradle(self):
        self.assertEqual(ApkVersion((1, 2, 3), 2_100_000_000), requested_version("1.2.3", "2100000000"))
        for name, code in (("v1.2.3", "2"), ("1000000000.0.0", "2"), ("1.2.3", "0"), ("1.2.3", "02"),
                           ("1.2.3", "2100000001"), ("1.2.3", "9" * 500)):
            with self.assertRaises(ReleaseValidationError):
                requested_version(name, code)

    def test_all_pages_formal_releases_and_official_asset_aliases(self):
        aliases = [{"name": name, "size": 42, "state": "uploaded"} for name in
                   ("RiftDeck-v1.2.3.apk", "RiftDeck-1.2.3.apk", "RiftDeck.apk", "app-release.apk", "RiftDeck-debug.apk", "RiftDeck-arm64.apk")]
        releases = parse_release_pages([[row(assets=aliases), row("v99.0.0", draft=True)],
                                       [row("v98.0.0", prerelease=True), row("v2.0.0"), row("v3.0.0-rc")]])
        self.assertEqual(["v1.2.3", "v2.0.0"], [release.tag for release in releases])
        self.assertEqual(4, len(releases[0].apks))

    def test_first_release_requires_no_existing_apk_or_network_download(self):
        releases = parse_release_pages([[]])
        self.assertEqual(0, validate_increment(requested_version("0.1.0", "1"), releases,
                                              lambda *_: self.fail("First release must not inspect an APK")))

    def test_legacy_single_universal_apk_matches_app_selection_policy(self):
        assets = [{"name": name, "size": 42, "state": "uploaded"} for name in
                  ("RiftDeck-universal.apk", "RiftDeck-debug.apk", "RiftDeck-arm64.apk", "RiftDeck-x86_64.apk")]
        releases = parse_release_pages([[row(assets=assets)]])
        self.assertEqual((PublishedApk("RiftDeck-universal.apk", 42),), releases[0].apks)
        with self.assertRaises(ReleaseValidationError):
            parse_release_pages([[row(assets=assets + [{"name": "another.apk", "size": 42, "state": "uploaded"}])]])

    def test_name_must_exceed_every_release_not_only_latest_in_array(self):
        releases = parse_release_pages([[row("v1.10.0"), row("v1.2.0")]])
        for name in ("1.10.0", "1.3.0", "0.99.0"):
            with self.assertRaises(ReleaseValidationError):
                validate_increment(requested_version(name, "99"), releases, lambda *_: self.fail("Name is rejected before downloads"))

    def test_code_must_exceed_maximum_of_all_published_apks(self):
        releases = parse_release_pages([[row("v1.2.3"), row("v1.3.0")]])
        codes = {"v1.2.3": 70, "v1.3.0": 20}
        inspect = lambda tag, _: ApkVersion(parse_stable_version(tag), codes[tag])
        for code in ("20", "70"):
            with self.assertRaises(ReleaseValidationError):
                validate_increment(requested_version("1.4.0", code), releases, inspect)
        self.assertEqual(2, validate_increment(requested_version("1.4.0", "71"), releases, inspect))

    def test_every_official_apk_alias_is_inspected(self):
        release = StableRelease("v1.2.3", (1, 2, 3), (PublishedApk("RiftDeck.apk", 42), PublishedApk("app-release.apk", 42)))
        with self.assertRaises(ReleaseValidationError):
            validate_increment(requested_version("1.3.0", "100"), [release],
                               lambda _, asset: ApkVersion((1, 2, 3), 10 if asset.name == "RiftDeck.apk" else 100))

    def test_aapt_parses_realistic_badging_and_long_version_code(self):
        text = "package: name='com.riftdeck' versionCode='42' versionName='1.2.3' platformBuildVersionName='16'\napplication-label:'RiftDeck'\n"
        self.assertEqual(ApkVersion((1, 2, 3), 42), parse_apk_badging(text))
        self.assertEqual(4_294_967_338, parse_apk_badging(text.replace("versionCode='42'", "versionCode='42' versionCodeMajor='1'")).code)

    def test_malformed_package_metadata_cannot_bypass_comparison(self):
        valid = "package: name='com.riftdeck' versionCode='42' versionName='1.2.3'"
        for text in ("", valid + "\n" + valid, valid.replace("com.riftdeck", "com.other"), valid.replace("'42'", "'bad'"),
                     valid.replace("'1.2.3'", "'1.2.3-beta'"), valid + " versionCode='1'"):
            with self.assertRaises(ReleaseValidationError):
                parse_apk_badging(text)
        with self.assertRaises(ReleaseValidationError):
            validate_increment(requested_version("1.3.0", "99"), parse_release_pages([[row()]]), lambda *_: ApkVersion((1, 2, 4), 1))

    def test_invalid_asset_flags_sizes_or_duplicates_fail_closed(self):
        for pages in ([[{"tag_name": "v1.2.3", "draft": "false", "prerelease": False}]],
                      [[row(assets=[{"name": "RiftDeck.apk", "state": "uploaded", "size": True}])]],
                      [[row(assets=[{"name": "RiftDeck.apk", "state": "new", "size": 42}])]],
                      [[row(assets=[{"name": "RiftDeck.apk", "state": "uploaded", "size": 42},
                                     {"name": "riftdeck.apk", "state": "uploaded", "size": 42}])]], [{}]):
            with self.assertRaises(ReleaseValidationError):
                parse_release_pages(pages)

    def test_subprocess_errors_are_sanitized_without_credentials(self):
        failure = subprocess.CalledProcessError(1, ["gh"], stderr="Authorization: secret-token")
        with patch("validate_release.subprocess.run", side_effect=failure):
            with self.assertRaises(ReleaseValidationError) as caught:
                run_command(["gh"])
        self.assertNotIn("secret-token", str(caught.exception))

    def test_cli_orchestration_uses_paginated_read_only_api_and_aapt(self):
        commands = []

        def fake_run(arguments, **_):
            commands.append([str(value) for value in arguments])
            if arguments[1] == "api":
                return json.dumps([[row()]])
            if arguments[1] == "release":
                Path(arguments[-1]).write_bytes(b"x" * 42)
                return ""
            return "package: name='com.riftdeck' versionCode='42' versionName='1.2.3'\n"

        with patch("validate_release.run_command", side_effect=fake_run):
            self.assertEqual((1, 1), check_published("dos1in/RiftDeck", requested_version("1.3.0", "43"), Path("/sdk/aapt")))
        self.assertIn("--paginate", commands[0])
        self.assertIn("--slurp", commands[0])
        self.assertEqual("download", commands[1][2])
        self.assertEqual(["/sdk/aapt", "dump", "badging"], commands[2][:3])


if __name__ == "__main__":
    unittest.main()
