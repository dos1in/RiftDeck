# RiftDeck

[简体中文](README.md) | **English**

<p align="center">
  <img src="branding/readme/riftdeck-logo.png" alt="RiftDeck" width="640">
</p>

**A controller-first retro game frontend built for KPA.**

RiftDeck is an open-source game library and emulator frontend built for KPA (KONKR Pocket Advance). Browse games and manage favorites with a D-pad, stick and face buttons, then launch through an external emulator.

Actively developed, with **Game Boy Advance** as the first supported platform. Core features need no account, and ROMs stay on your device.

## Interface Preview

The updated home screen uses a compact split layout: browse games on the left, view artwork and metadata on the right, and switch between all games, favorites and recent games at the top. This preview uses a sample library and placeholder artwork.

![Updated RiftDeck home screen with a game list on the left, artwork and metadata on the right, and controller hints along the bottom](design-demos/home-compact/A.png)

## Core Features

* Controller-first home, library, details and settings, with category switching and fast paging.
* Local `.gba` / `.zip` scanning, incremental updates, search, sorting and local covers.
* Favorites and recent games, launch counts and estimated playtime, with selection and scroll restoration on return.
* Saved external-emulator configuration, with the GBA.emu launch flow verified.
* Light and dark appearance, multiple palettes, reduced motion and optional Android Home launcher support.

## Quick Start

1. Install RiftDeck and a compatible GBA emulator.
2. Choose **Add ROM folder** and grant access.
3. Select your installed emulator in **Settings → Emulators**, then choose a game to launch.

Each ZIP must contain one GBA ROM. Configure the save directory in your emulator; RiftDeck does not manage saves. For local covers, place a matching image beside the ROM, such as `Game.gba` and `Game.png`, then rescan.

## Documentation

* [Usage and development guide](GUIDE.en.md): detailed features, limitations, architecture, building and roadmap.
* [Implementation and verification](CORE_IMPLEMENTATION.md): evidence and remaining hardware checks.
* [Development guidelines](AGENTS.md): read before contributing.

## License and Copyright

Source code is licensed under [MPL-2.0](LICENSE). The name, logo, app icon and other brand assets follow separate [trademark rules](TRADEMARKS.md) and are excluded from the MPL-2.0 grant. Modified versions and forks must not present themselves as official releases without authorization.

RiftDeck contains no emulator cores and does not provide, host, download or distribute copyrighted ROMs or BIOS files. Ensure your game files are obtained and used in accordance with applicable laws and licenses.
