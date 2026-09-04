# RiftDeck

**A controller-first retro game frontend for Android handhelds.**

RiftDeck is an open-source game library and emulator frontend designed specifically for Android gaming handhelds.

It provides a fast, focused interface for browsing ROM libraries, viewing game metadata, managing favorites and recent games, and launching games through external emulators.

RiftDeck is built around physical controls first. Every core interaction is designed to work with a D-pad, analog stick, and face buttons without requiring touch input.

> **Browse. Launch. Play.**

---

## Status

RiftDeck is currently under active development.

The first target device is the **KONKR Pocket Advance**, with **Game Boy Advance** as the first fully supported platform.

The architecture is designed to support additional Android handhelds and retro platforms over time.

---

## Features

### Controller-First Navigation

RiftDeck is designed for handheld controls from the start.

* Full D-pad navigation
* Analog stick navigation
* A / B / X / Y actions
* Shoulder-button navigation
* Clear and consistent focus states
* No touch input required for normal use

### Game Library

* Scan local ROM folders
* Organize games by platform
* Browse games with cover artwork
* Favorites
* Recently played games
* Play history
* Search
* Sorting
* Incremental library scanning

### Game Details

View useful information before launching a game:

* Cover artwork
* Screenshots
* Platform
* Release year
* Developer
* Genre
* Playtime
* Last played
* Favorite status

Optional video previews are powered by AndroidX Media3.

### External Emulator Support

RiftDeck acts as a frontend rather than an emulator.

Games are launched through installed Android emulators using configurable Android intents.

The emulator integration layer is designed to support applications such as:

* RetroArch
* standalone emulators
* platform-specific emulators
* custom emulator configurations

RiftDeck does not distribute ROMs, BIOS files, or emulator binaries.

---

## First Supported Platform

### Game Boy Advance

The first development milestone focuses on delivering an excellent GBA experience.

Initial ROM formats:

```text
.gba
.zip
```

Planned GBA functionality includes:

* ROM folder scanning
* Cover artwork
* Metadata
* Favorites
* Recent games
* Search and sorting
* Emulator launching
* Play history
* Restoring library position after returning from an emulator

More platforms will be added after the GBA experience is stable.

---

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

---

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

---

## Tech Stack

RiftDeck is built with modern Android technologies.

```text
App
│
├── Kotlin
│
├── Jetpack Compose
│   ├── Home
│   ├── Platform
│   ├── Game Details
│   └── Settings
│
├── Room
│   └── Game Library
│
├── Coil
│   └── Artwork
│
├── Media3
│   └── Video Previews
│
├── Coroutines / Flow
│
├── DataStore
│
└── Native Core (optional)
    │
    └── C++
        ├── ROM Scanning
        ├── CRC / SHA
        ├── Archive Processing
        └── Large Filesystem Operations
```

### Kotlin First

RiftDeck is intentionally Kotlin-first.

Native C++ code is optional and will only be introduced when profiling demonstrates a measurable performance benefit.

UI, application logic, Android integration, and most library operations remain in Kotlin.

---

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

---

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

---

## Storage

RiftDeck uses Android's Storage Access Framework where possible.

The application is designed to support:

* internal storage
* SD cards
* removable storage
* multiple ROM directories
* persistent folder permissions

ROM files remain on the user's device.

---

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

---

## Roadmap

### Phase 1 — Application Shell

* [ ] Application architecture
* [ ] Navigation
* [ ] Controller input layer
* [ ] Focus system
* [ ] RiftDeck theme
* [ ] Mock GBA library

### Phase 2 — GBA Library

* [ ] ROM directory selection
* [ ] GBA ROM scanning
* [ ] Room database
* [ ] Game library
* [ ] Favorites
* [ ] Recent games
* [ ] Search
* [ ] Sorting

### Phase 3 — Emulator Integration

* [ ] Emulator configuration
* [ ] Generic emulator launcher
* [ ] GBA emulator support
* [ ] Return-to-library state restoration

### Phase 4 — Metadata

* [ ] Metadata provider abstraction
* [ ] Cover artwork
* [ ] Screenshots
* [ ] Background metadata scraping

### Phase 5 — Media

* [ ] Video previews
* [ ] Media3 integration
* [ ] Preview lifecycle management

### Phase 6 — Performance

* [ ] Baseline Profiles
* [ ] Macrobenchmarks
* [ ] Compose profiling
* [ ] Image cache tuning
* [ ] ROM scanning benchmarks

### Phase 7 — Additional Platforms

Planned platforms may include:

* Game Boy
* Game Boy Color
* NES
* SNES
* Mega Drive / Genesis
* PlayStation
* Nintendo 64
* PSP
* Dreamcast

---

## Building

RiftDeck is an Android project built with Gradle.

Requirements will be documented as the initial project structure stabilizes.

Typical development environment:

* Android Studio
* Android SDK
* Kotlin
* JDK
* Gradle

Clone the repository:

```bash
git clone https://github.com/dos1in/RiftDeck.git
cd RiftDeck
```

Then open the project in Android Studio.

---

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

---

## ROMs and Copyright

RiftDeck does not provide, host, download, or distribute copyrighted game ROMs or BIOS files.

Users are responsible for ensuring that any game files used with RiftDeck are obtained and used in accordance with applicable laws and licenses.

---

## License

RiftDeck source code is licensed under the **Mozilla Public License 2.0**.

See [LICENSE](LICENSE) for the complete license terms.

---

## Trademark

The RiftDeck name, logo, application icon, and other distinctive brand assets are not licensed under the Mozilla Public License 2.0.

Modified versions and forks must not present themselves as official RiftDeck releases unless explicitly authorized.

See [TRADEMARKS.md](TRADEMARKS.md) for details.

---

## Acknowledgements

RiftDeck is inspired by the long history of open-source emulation frontends and the retro handheld community.

RiftDeck itself is a frontend and does not contain emulator cores or copyrighted game content.
