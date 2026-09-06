# GBA diagnostic ROM

This original ARM program fills the GBA screen with yellow and cyan bands. It contains no third-party game data, BIOS or logo assets. The emulator must boot directly into the ROM without an external BIOS logo check.

Requires Python 3 and Clang with the ARM assembler target:

```bash
python3 tools/test-fixtures/make_gba_smoke.py build/test-roms/RiftDeck_QA.gba
```

Import the generated folder through RiftDeck, select GBA.emu in emulator settings and launch. The expected result is a yellow upper band and a cyan lower band. Test both the raw file and a ZIP containing exactly that ROM; return to check the selected game and play history.

Use an isolated Android emulator for synthetic libraries and process-recreation tests. The diagnostic image is padded to 1 MiB to satisfy the tested emulator's minimum ROM size.

## Storage permission integration test

On an isolated test device, authorize a diagnostic ROM directory through RiftDeck first. The opt-in `SafPermissionIntegrationTest` accepts an instrumentation argument named `permissionTestTree` with that directory's SAF tree URI. It scans real documents into an in-memory database, releases the persisted read grant, then verifies that records and favorites survive and ROM reading fails. Reauthorize the test directory through the system picker afterward. Without the argument the test is skipped.
