# RiftDeck Usage and Development Guide

[Back to README](README.en.md) · [简体中文](GUIDE.md) | **English**

## Current Features

* Controller navigation with D-pad / stick, A/B/X/Y actions, L1/R1 categories, L2/R2 library paging and first-letter jumps in the displayed title order.
* Home, game library, game details and controller-friendly settings.
* System, light and dark appearance; Rift, Ocean and Ember palettes; reduced motion. Preferences use DataStore.
* Android Home launcher support. Use **Settings → Home launcher → Set as default home**. Android asks you to confirm; the current Home app stays unchanged if you cancel.
* Persistent SAF folder access, recursive `.gba` / `.zip` scanning, incremental updates, cancellation and removed-file detection.
* Room library storage, favorites, recent games, sorting and name / filename search. Search includes a controller keyboard; touch input supports the system keyboard.
* Installed-emulator selection and saved configuration. GBA.emu has been tested with an original diagnostic ROM through both SAF and ZIP extraction.
* Launch count, last-played time and estimated session duration, with page, selection and scroll restoration when returning.

New installations start with an empty library. Choose **Add ROM folder**, grant access in Android's folder picker, then select an installed GBA emulator in **Settings → Emulators**. Use game files you are entitled to run. Android's picker and the external emulator have their own input and storage settings. Configure a writable save directory in the emulator; RiftDeck shares ROMs with read access and does not manage emulator save files.

ZIP files must contain one GBA ROM. Unsafe paths, ambiguous archives and oversized files are rejected. Extracted ROMs use a private temporary cache and a limited FileProvider read grant; older files are cleaned after 24 hours when the frontend resumes or prepares another ROM.

Duplicate detection currently recognizes the same document imported through overlapping folders. Identical copies with different document IDs remain separate. Playtime estimates the interval between launch and return, capped at 24 hours per session; it cannot distinguish paused or backgrounded emulator time. A successful Android launch does not guarantee the emulator accepts every ROM.

Local covers are loaded with Coil. Put a matching PNG, WebP, JPG or JPEG beside the ROM, then rescan (for example, `Game.gba` and `Game.png`). Matching ignores case, preserves region tags and prefers PNG, then WebP, JPG and JPEG when several exist. Replacing or deleting a cover updates the next scan; unavailable or damaged images fall back to a geometric placeholder.

Metadata scraping, video previews, additional emulator-specific adapters and an Android app drawer are planned.

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

Media3 previews, Baseline Profiles and macrobenchmarks are planned. There is no native core; C++ will only be considered after profiling demonstrates a need.

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

RiftDeck is designed as a local-first application.

Core functionality does not require an account or online service.

Your ROM library remains local by default.

Metadata services may optionally be used to retrieve information such as:

* game titles
* cover artwork
* screenshots
* descriptions
* release information

RiftDeck does not upload ROM files.

## Roadmap

The application shell, local GBA library and first external emulator launch flow are implemented. Remaining work focuses on target-handheld validation, metadata / artwork, optional video previews, performance profiling and more platforms.

## Building

Use JDK 17 and Android SDK 36. The repository includes the Gradle wrapper; Room schemas are versioned under `app/schemas`.

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
./gradlew :app:connectedDebugAndroidTest
```

The second command requires a connected Android test device or emulator. Use an isolated test device for instrumentation. See `tools/test-fixtures` for a source-built diagnostic ROM without third-party game data.

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
