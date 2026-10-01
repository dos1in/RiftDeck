#!/usr/bin/env python3
"""Create small signed test APKs; they are never included in the application APK."""
import argparse
import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile


def run(*arguments):
    subprocess.run([str(value) for value in arguments], check=True, capture_output=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--sdk", type=Path, required=True)
    parser.add_argument("--keystore", type=Path, required=True)
    parser.add_argument("--base-code", type=int, default=1)
    parser.add_argument("--base-version", default="0.1.0")
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    assert re.fullmatch(r"\d+\.\d+\.\d+", args.base_version)
    version = args.base_version.split(".")
    version[-1] = str(int(version[-1]) + 1)
    new_version = ".".join(version)
    tools = args.sdk / "build-tools" / "35.0.0"
    if not tools.exists():
        choices = [p for p in (args.sdk / "build-tools").iterdir() if re.fullmatch(r"\d+\.\d+\.\d+", p.name)]
        tools = max(choices, key=lambda p: tuple(map(int, p.name.split("."))))
    java_home = os.environ.get("JAVA_HOME")
    keytool = Path(java_home) / "bin" / "keytool" if java_home else shutil.which("keytool")
    assert keytool, "JDK keytool is required"
    args.output.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="update-fixtures-", dir=args.output.parent) as temporary:
        work = Path(temporary)
        other_key = work / "other.jks"
        run(keytool, "-genkeypair", "-keystore", other_key, "-alias", "androiddebugkey",
            "-storepass", "android", "-keypass", "android", "-keyalg", "RSA", "-keysize", "2048",
            "-validity", "3650", "-dname", "CN=RiftDeck Update Test")
        fixtures = (
            ("matching.apk", "com.riftdeck", args.base_code + 1, args.keystore),
            ("wrong-signature.apk", "com.riftdeck", args.base_code + 1, other_key),
            ("old-version.apk", "com.riftdeck", args.base_code, args.keystore),
            ("wrong-package.apk", "com.riftdeck.other", args.base_code + 1, args.keystore),
        )
        for name, package, code, key in fixtures:
            manifest = work / "AndroidManifest.xml"
            manifest.write_text(f'''<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="{package}" android:versionCode="{code}" android:versionName="{new_version}">
    <uses-sdk android:minSdkVersion="29" android:targetSdkVersion="36" />
    <application android:label="RiftDeck Update Test" android:hasCode="false" />
</manifest>''', encoding="utf-8")
            unsigned = work / "unsigned.apk"
            aligned = work / "aligned.apk"
            run(tools / "aapt2", "link", "-I", args.sdk / "platforms/android-36/android.jar",
                "--manifest", manifest, "-o", unsigned)
            run(tools / "zipalign", "-f", "-p", "4", unsigned, aligned)
            run(tools / "apksigner", "sign", "--ks", key, "--ks-key-alias", "androiddebugkey",
                "--ks-pass", "pass:android", "--key-pass", "pass:android", "--v4-signing-enabled", "false",
                "--out", args.output / name, aligned)
            print(f"Generated update-fixtures/{name}")


if __name__ == "__main__":
    main()
