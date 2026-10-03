# GBA diagnostic ROM

This original ARM program fills the GBA screen with yellow and cyan bands. It contains no third-party game data, BIOS or logo assets. The emulator must boot directly into the ROM without an external BIOS logo check.

Requires Python 3 and Clang with the ARM assembler target:

```bash
python3 tools/test-fixtures/make_gba_smoke.py build/test-roms/RiftDeck_QA.gba
```

Import the generated folder through RiftDeck, select GBA.emu in emulator settings and launch. The expected result is a yellow upper band and a cyan lower band. Test both the raw file and a ZIP containing exactly that ROM; return to check the selected game and play history.

Use an isolated Android emulator for synthetic libraries and process-recreation tests. The diagnostic image is padded to 1 MiB to satisfy the tested emulator's minimum ROM size.

## Signed APK update fixtures

Android test builds run `make_update_apks.py` through `generateUpdateTestFixtures`. It uses Android SDK build tools and the existing debug signing key to generate small manifest-only APKs with a newer version, a different signer, an old version code, and a different package. These files live under `app/build/generated/update-fixtures-assets/update-fixtures` and are packaged only in the test APK. They are parsed by APK validation tests; never install them over the frontend.

For a custom Debug signing configuration, pass its keystore with `--keystore` and its alias with `--key-alias`. Provide the passwords through `RIFTDECK_FIXTURE_STORE_PASSWORD` and `RIFTDECK_FIXTURE_KEY_PASSWORD`; Gradle supplies these from the active Debug signing configuration. The tool passes environment variable names to `apksigner`, keeping passwords out of command arguments and logs. The matching, old-version, and wrong-package fixtures use this configuration. The wrong-signature fixture always uses an independently generated test key with separate test credentials, regardless of the custom signing configuration.

On an isolated emulator, build the test APK with `./gradlew :app:assembleDebugAndroidTest -PupdateUiFixtures=true` and install both Debug APKs. The optional test-only instrumentation runner `com.riftdeck.UpdateUiFixtureRunner` accepts `mode=available` or `mode=ready`: `adb shell am instrument -w -e mode available com.riftdeck.test/com.riftdeck.UpdateUiFixtureRunner`. It seeds synthetic release notes and private update metadata to inspect the normal controller UI and Android installation confirmation. Cancel the installer. This runner is not included in production APKs and is not part of the automatic test suite. Rebuild without the property to restore the normal test runner.

## Storage permission integration test

On an isolated test device, authorize a diagnostic ROM directory through RiftDeck first. The opt-in `SafPermissionIntegrationTest` accepts an instrumentation argument named `permissionTestTree` with that directory's SAF tree URI. It scans real documents into an in-memory database, releases the persisted read grant, then verifies that records and favorites survive and ROM reading fails. Reauthorize the test directory through the system picker afterward. Without the argument the test is skipped.
