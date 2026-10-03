# RiftDeck Usage and Development Guide

[Back to README](README.en.md) · [简体中文](GUIDE.md) | **English**

## Current Features

* Controller navigation with D-pad / stick, A/B/X/Y actions, L1/R1 categories, L2/R2 library paging and first-letter jumps in the displayed title order.
* Home, game library, game details and controller-friendly settings.
* Fixed dark Rift palette, reduced motion and collapsible sidebar. Preferences use DataStore.
* Android Home launcher support. Use **Settings → Home launcher → Set as default home**. Android asks you to confirm; the current Home app stays unchanged if you cancel.
* Persistent SAF folder access, recursive `.gba` / `.zip` scanning, incremental updates, cancellation and removed-file detection.
* Room library storage, favorites, recent games, sorting and name / filename search. Select “Chinese / System input” and press A to open the system keyboard, then switch to Chinese Pinyin to enter Chinese text. B returns to the controller keyboard without losing the draft. Use “Keyboard settings” or “On-screen keyboard” in the search dialog if Chinese or the software keyboard is not enabled.
* Installed-emulator selection and saved configuration. GBA.emu has been tested with an original diagnostic ROM through both SAF and ZIP extraction.
* Launch count, last-played time and estimated session duration, with page, selection and scroll restoration when returning.
* LAN handheld pairing, ROM transfers on request and automatic save sync with preserved conflict versions.

New installations start with an empty library. Choose **Add ROM folder**, grant access in Android's folder picker, then select an installed GBA emulator in **Settings → Emulators**. Use game files you are entitled to run. Android's picker and the external emulator have their own input and storage settings. Configure a writable save directory in the emulator; emulator launches grant ROM read access, while save sync requires a separately authorized folder in LAN sharing.

The generic emulator ZIP extraction flow requires one GBA ROM per archive. Unsafe paths, ambiguous archives and oversized files are rejected. Extracted ROMs use a private temporary cache and a limited FileProvider read grant; older files are cleaned after 24 hours when the frontend resumes or prepares another ROM.

Duplicate detection recognizes the same document imported through overlapping folders. Candidates with matching filenames or titles are checked in the background; verified identical content appears once while the database retains each location. Different versions remain separate. Playtime estimates the interval between launch and return, capped at 24 hours per session; it cannot distinguish paused or backgrounded emulator time. A successful Android launch does not guarantee the emulator accepts every ROM.

Local covers are loaded with Coil. Put a matching PNG, WebP, JPG or JPEG beside the ROM, then rescan (for example, `Game.gba` and `Game.png`). Matching ignores case, preserves region tags and prefers PNG, then WebP, JPG and JPEG when several exist. Replacing or deleting a cover updates the next scan; unavailable or damaged images fall back to a geometric placeholder.

Local Pegasus descriptions, covers and videos are supported. Settings control image/video previews, playback delay and looping. RetroArch G with mGBA reads the original GBA / ZIP directly: RiftDeck checks readability while RetroArch handles archive contents, preserving existing save-path associations. Online metadata scraping, additional emulator-specific adapters and an Android app drawer are planned.

See [CORE_IMPLEMENTATION.md](CORE_IMPLEMENTATION.md) for verification evidence and remaining hardware checks.

## Target Devices

### KONKR Pocket Advance

The KONKR Pocket Advance is the first reference device for RiftDeck development.

The interface is designed around compact landscape handheld displays and physical controls.

RiftDeck is not intended to remain device-specific. The UI architecture is designed to adapt to different:

* screen sizes
* aspect ratios
* pixel densities
* controller layouts
* Android gaming handhelds

Planned layouts include support for:

* 4:3
* 16:9
* 16:10

## Design

RiftDeck aims to feel like a dedicated gaming system rather than a traditional Android application.

The default visual direction combines:

* dark surfaces
* high-contrast typography
* neon yellow focus accents
* cyan and magenta secondary accents
* technical geometry
* subtle futuristic HUD elements
* strong controller focus indicators

Visual effects are intentionally kept lightweight.

Responsiveness and readability always take priority over decorative effects.

## Tech Stack

Implemented: Kotlin, Jetpack Compose, Navigation Compose, Room / KSP, Coil, Coroutines / Flow and DataStore. ROM scanning and ZIP preparation run in Kotlin on background dispatchers.

Media3 previews are implemented; an application Baseline Profile and navigation sampling tool are available; automated macrobenchmarks are planned. There is no native core; C++ will only be considered after profiling demonstrates a need.

## Architecture

RiftDeck follows a layered architecture.

```text
Compose UI
    │
    ▼
ViewModel
    │
    ▼
Use Cases
    │
    ▼
Repository
    │
    ├── Room
    ├── Android APIs
    ├── Storage
    ├── Metadata Providers
    └── Emulator Integration
            │
            ▼
      Native Core
        (optional)
```

Recommended project structure:

```text
app/
core/
data/
domain/
feature/
```

The architecture keeps UI, storage, emulator integration, ROM scanning, and platform-specific logic separated.

See [AGENTS.md](AGENTS.md) for detailed architecture and development guidelines.

## Performance

Performance is a core product requirement.

RiftDeck is designed around:

* fast startup
* low input latency
* smooth game-grid scrolling
* background ROM scanning
* incremental database updates
* appropriately sized image decoding
* delayed video preview playback
* minimal work on the Android main thread

The project targets smooth operation even with large ROM libraries.

Native optimization will only be introduced after profiling identifies an actual bottleneck.

## Storage

RiftDeck uses Android's Storage Access Framework where possible.

The application is designed to support:

* internal storage
* SD cards
* removable storage
* multiple ROM directories
* persistent folder permissions

ROM files remain on the user's device.

## Local-First

### ROM sharing and automatic save sync between handhelds

Connect both handhelds to the same LAN and open **Settings → LAN sharing**. Enable sharing on each device, select writable ROM folders and the folders actually used by your emulators for saves. Save paths must match relative to the selected folders. Configure an accessible save location in your emulator first; Android prevents selecting another app's private folders.

On one device choose **Allow a new pairing**, then enter its 16-character code on the other device's **Devices** page. The code lasts two minutes and is single-use. Discovery is automatic; a displayed address and port can be entered if discovery is unavailable. Paired transfers use encryption and content checksums and stay on your LAN.

- Browse a paired device's ROMs and choose files to receive. Downloads are scanned into the local library; different files with the same name are preserved.
- Enable automatic save sync on both devices. A foreground service checks every 15 seconds, preserves files while offline and retries on reconnect. Its notification offers Stop sharing.
- Different initial saves or changes on both devices produce a conflict. Both originals and `.riftdeck-conflict-` copies are retained. Choose the local or peer version in **Conflicts**; a stale choice is rejected if the files changed. Deletions never delete files on the peer.
- Save writes pause before launching an emulator through RiftDeck and resume on return. Stopping sharing cancels transfers. Configuration, pairings and sync history persist; reopening the app restores enabled sharing after process termination.

Supported ROMs are `.gba` / `.zip`, up to 2 GiB per file. Common battery saves and save states are supported up to 64 MiB per file. Only supported files in the selected save folder are synchronized; conflict copies are excluded from automatic sync. The SAF provider must support creation and rename; a failed safe replacement leaves the original intact.

### Automatic APK updates

By default, entering or returning to the frontend checks the latest stable GitHub release in the background. Successful checks are limited to once per 24 hours; failed network checks retry after at least one hour. Checks do not block the library, download APKs or install them automatically. A notice appears on Home when other dialogs are closed, and deferring a version suppresses repeated notices for that version.

**Settings → About** provides manual checks, the automatic-check toggle, release notes, download cancellation and installation. Downloads are checked for SHA-256, size, package identity, Android compatibility, increasing version code and signing compatibility before Android asks for installation confirmation. If permission is required, allow RiftDeck to install unknown apps and choose Install APK again after returning. Cancelling installation leaves the existing app usable; verified downloads survive process recreation.

The source is public `dos1in/RiftDeck` GitHub Releases. Only stable `vmajor.minor.patch` tags with a compatible APK and GitHub SHA-256 digest are accepted. Debug, unsigned, prerelease and ambiguous assets are excluded. An unavailable release is reported explicitly. Checks request release information without sending ROMs, saves or folder contents.

Publishers must use a signing key compatible with existing installations and increase Android's `versionCode`. The manual **Publish Android release** workflow tests and publishes signed APKs after signing Secrets and version inputs are configured; see [.github/CI.md](.github/CI.md). Local builds accept `-PappVersionName=0.1.1 -PappVersionCode=2`. Without signing configuration, a Release build remains unsigned and cannot be used for an in-app installation.

RiftDeck is designed as a local-first application.

Core functionality does not require an account or online service.

Your ROM library remains local by default.

Metadata services may optionally be used to retrieve information such as:

* game titles
* cover artwork
* screenshots
* descriptions
* release information

RiftDeck does not upload ROM files to online services; ROM transfers take place only between paired LAN devices.

## Roadmap

The application shell, local GBA library, external emulator launch flow, local metadata / artwork and optional video previews are implemented. Target-handheld checks cover D-pad navigation, SD card reinsertion and return after exiting RetroArch. Follow-up work includes release performance profiling, online metadata scraping and more platforms.

## Building

Use JDK 17 and Android SDK 36. The repository includes the Gradle wrapper; Room schemas are versioned under `app/schemas`.

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
./gradlew :app:connectedDebugAndroidTest
```

The second command requires a connected Android test device or emulator. Use an isolated test device for instrumentation. See `tools/test-fixtures` for a source-built diagnostic ROM without third-party game data.

For development across computers, securely distribute the same signing key and fill in the local signing configuration on each machine. Debug and Release use the same signing configuration. The key and local password configuration are excluded from Git. See [.github/CI.md](.github/CI.md) for configuration, environment overrides and certificate checks.

## Contributing

RiftDeck is still in an early stage of development.

Contributions, bug reports, design discussions, emulator configurations, device compatibility reports, and performance improvements are welcome.

Before making code changes, please read:

[AGENTS.md](AGENTS.md)

Important project principles:

1. Controller navigation comes first.
2. Performance is a feature.
3. Core functionality should remain local-first.
4. Avoid unnecessary dependencies.
5. Do not introduce native code without a measured reason.
6. Keep platform and emulator integrations modular.
7. Optimize for real handheld hardware, not only Android emulators.

## Acknowledgements

RiftDeck is inspired by the long history of open-source emulation frontends and the retro handheld community.

RiftDeck itself is a frontend and does not contain emulator cores or copyrighted game content.
