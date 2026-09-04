# AGENTS.md

## 1. Project Overview

This project is an Android retro handheld game frontend / launcher designed primarily for physical gamepad operation.

The first target device is:

- KONKR Pocket Advance

The frontend should provide a fast, controller-first way to browse platforms, discover games, view game details, and launch external emulators.

The product should feel like a dedicated gaming handheld interface rather than a traditional Android application.

Primary goals:

1. Fast startup.
2. Smooth controller navigation.
3. Large, readable UI for a compact handheld screen.
4. Reliable ROM library scanning.
5. Instant-feeling transitions.
6. Clean separation between UI, data, platform integration, and optional native code.
7. Easy support for additional Android handhelds later.
8. A distinctive cyberpunk / neon visual language inspired by futuristic street-tech aesthetics, without copying copyrighted UI assets or branding.

---

## 2. Core Technology Stack

Use the following stack unless there is a strong technical reason not to.

### Application

- Kotlin
- Android SDK
- Gradle Kotlin DSL
- Minimum architecture should favor modern Android APIs.

### UI

- Jetpack Compose
- Material 3 only where useful; custom components are preferred for the main handheld UI.
- Navigation Compose
- Compose animation APIs
- Android gamepad / key event APIs

Primary screens:

- Home
- Platform
- Game Detail
- Settings

### Local Data

- Room
- SQLite
- Kotlin Flow

Room stores:

- Platforms
- Games
- ROM locations
- Metadata
- Favorites
- Play history
- Emulator mappings
- Scraped media references
- Scan state

### Images

- Coil

Use Coil for:

- Box art
- Screenshots
- Logos
- Background artwork

Always request appropriately sized images.

Do not decode full-resolution artwork when displaying thumbnail-sized content.

### Video

- AndroidX Media3

Use for:

- Gameplay previews
- Trailer previews
- Optional background video previews

Video playback must never block controller navigation or page transitions.

### Async / Reactive

- Kotlin Coroutines
- Kotlin Flow
- StateFlow

Avoid blocking the main thread.

### Preferences

- DataStore

Use DataStore for:

- UI preferences
- Theme
- Sorting mode
- Default emulator
- Scan directories
- Video preview settings
- Sound settings
- Performance settings

Do not use SharedPreferences for new code.

---

## 3. Optional Native Core

The initial implementation should remain Kotlin-only unless profiling proves native code is necessary.

Native code is optional.

If required, use:

- C++
- Android NDK
- CMake
- JNI

The Native Core may contain only performance-sensitive work such as:

- Large ROM directory scanning
- CRC32
- MD5 / SHA hashing
- Archive inspection
- ZIP handling
- 7z handling
- Binary metadata parsing
- Large batch filesystem operations

Do NOT move ordinary business logic into C++.

Do NOT implement UI in C++.

Do NOT create JNI APIs without a measured performance reason.

Preferred architecture:

```text
Compose UI
    |
ViewModel
    |
Use Cases
    |
Repository
    |
+----------------------+
| Kotlin Data Layer    |
| Android Integration  |
+----------------------+
           |
        optional
           |
          JNI
           |
      Native Core
```

---

## 4. Project Architecture

Use a layered architecture.

Recommended package layout:

```text
app/
└── src/main/java/<package>/
    ├── app/
    │   ├── LauncherApplication.kt
    │   ├── MainActivity.kt
    │   └── navigation/
    │
    ├── core/
    │   ├── common/
    │   ├── model/
    │   ├── ui/
    │   ├── input/
    │   ├── emulator/
    │   ├── storage/
    │   ├── media/
    │   └── performance/
    │
    ├── data/
    │   ├── database/
    │   │   ├── entity/
    │   │   ├── dao/
    │   │   └── migration/
    │   ├── repository/
    │   ├── scanner/
    │   ├── scraper/
    │   └── mapper/
    │
    ├── domain/
    │   ├── model/
    │   ├── repository/
    │   └── usecase/
    │
    ├── feature/
    │   ├── home/
    │   ├── platform/
    │   ├── game/
    │   ├── search/
    │   ├── favorites/
    │   └── settings/
    │
    └── nativecore/
```

Keep feature-specific UI inside `feature`.

Keep shared UI primitives inside `core/ui`.

Keep emulator launch logic inside `core/emulator`.

Keep ROM scanning outside ViewModels.

---

## 5. UI Design Principles

This is a handheld interface first.

Do not design it like a phone app.

The user is expected to control the UI primarily with:

- D-pad
- Left analog stick
- A / B / X / Y buttons
- Shoulder buttons
- Start / Select

Touch may be supported, but touch is secondary.

### Visual Direction

Primary visual direction:

- Dark background
- Neon yellow as the strongest focus/accent color
- Cyan secondary accent
- Magenta tertiary accent
- High-contrast text
- Sharp technical geometry
- Subtle cyberpunk overlays
- Sparse scanline / grid / HUD details
- Strong focus state

Avoid:

- Excessive glow
- Heavy blur
- Expensive real-time shader effects
- Constant animated backgrounds
- Tiny typography
- Phone-style bottom navigation
- Dense settings screens
- Excessively rounded Material cards

The UI should feel futuristic and energetic while remaining readable on a small display.

---

## 6. Controller-First Navigation

Every screen must be fully usable without touch.

This is a hard requirement.

### Focus Rules

Every interactive item must have an explicit, visible focus state.

Focused items should use combinations of:

- Bright border
- Scale increase
- Contrast increase
- Neon highlight
- Subtle glow
- Motion

Recommended focus scale:

```text
1.00 -> 1.03 ~ 1.06
```

Avoid large scale changes that cause layout movement.

### Directional Navigation

D-pad behavior must be deterministic.

Never rely entirely on Compose's default focus resolution for complex layouts.

For important screens, define explicit focus relationships using:

- `FocusRequester`
- `focusProperties`
- `focusTarget`
- custom directional navigation where necessary

### Global Controller Mapping

Default mapping:

```text
A      Confirm / Launch
B      Back
X      Context action / Details
Y      Favorite
L1/R1  Previous / Next platform or category
L2/R2  Optional fast navigation
Start  Menu / Quick actions
Select Search / Filters / secondary action
D-pad  Navigation
Stick  Navigation
```

Mapping must be configurable later.

Do not hard-code visual button glyphs directly into business logic.

---

## 7. Home Screen

The Home screen should immediately communicate:

- Current platform
- Recently played games
- Favorites
- Installed / configured systems
- Continue playing

Recommended hierarchy:

```text
Top status/navigation
        |
Platform selector
        |
Primary game carousel
        |
Recent / Favorite shortcuts
```

On first launch, show a simple empty state:

```text
No games found
Add a ROM folder
```

The empty state must be navigable with a controller.

---

## 8. Platform Screen

The Platform screen displays games from one system.

Initial target library:

- Game Boy Advance

The architecture must support more platforms without feature rewrites.

Examples:

- GB
- GBC
- GBA
- NES
- SNES
- Mega Drive
- PS1
- PSP
- N64
- Dreamcast

### Game Grid

Prioritize large covers.

Avoid showing too many covers at once.

A compact handheld display should favor clarity over density.

Each game item may display:

- Box art
- Favorite state
- Played state
- Optional region marker

Avoid long metadata text inside the grid.

### Fast Scroll

Large libraries must support:

- Alphabet jump
- Shoulder-button page navigation
- Optional fast-scroll mode

A 5,000+ item library must remain responsive.

---

## 9. Game Detail Screen

Show only information useful before launching.

Recommended fields:

- Cover
- Title
- Platform
- Release year
- Developer
- Genre
- Playtime
- Last played
- Favorite state
- Screenshot / preview
- Primary Play button

Optional:

- Description
- RetroAchievements integration
- Multiple emulator/core selection
- Save-state actions

The Play action must always be visually dominant.

---

## 10. Settings

Settings should be controller-friendly.

Use a two-level structure where possible.

Recommended sections:

```text
Library
Emulators
Appearance
Input
Video Preview
Scraping
Storage
Performance
About
```

Avoid deep nested settings navigation.

Any destructive action requires a clear confirmation.

---

## 11. ROM Library Model

Recommended domain model:

```kotlin
data class Game(
    val id: Long,
    val platformId: Long,
    val title: String,
    val sortTitle: String,
    val romUri: String,
    val fileName: String,
    val fileSize: Long,
    val crc32: String?,
    val sha1: String?,
    val favorite: Boolean,
    val hidden: Boolean,
    val playCount: Int,
    val playTimeSeconds: Long,
    val lastPlayedAt: Long?,
    val coverUri: String?,
    val screenshotUri: String?,
    val videoUri: String?,
)
```

Database entities do not need to exactly match domain models.

Do not leak Room entities directly into the UI layer.

---

## 12. ROM Scanning

ROM scanning must be incremental.

Never block the UI until a full scan completes.

Recommended pipeline:

```text
Selected Folder
     |
DocumentFile / SAF
     |
Directory Enumeration
     |
Extension Filter
     |
Platform Detection
     |
ROM Identity
     |
Database Upsert
     |
Metadata Matching
     |
Media Linking
```

The scanner should emit progress through Flow.

Example:

```kotlin
sealed interface ScanProgress {
    data object Idle : ScanProgress
    data class Scanning(
        val scanned: Int,
        val discovered: Int
    ) : ScanProgress
    data class Completed(
        val total: Int
    ) : ScanProgress
    data class Failed(
        val error: Throwable
    ) : ScanProgress
}
```

### Scan Requirements

Must support:

- Multiple ROM directories
- Removable storage
- SAF URIs
- Folder removal
- Incremental rescans
- Deleted ROM detection
- Duplicate detection

Do not hash every file unnecessarily.

Use staged identity:

```text
Path + size
    ↓
CRC32 when required
    ↓
SHA1 only when required
```

---

## 13. Archive Handling

Initial release may support:

- `.gba`
- `.zip`

Later:

- `.7z`

Do not automatically extract large archives permanently.

Prefer inspection or temporary extraction.

Any temporary files must be cleaned safely.

Archive operations must run on background dispatchers.

---

## 14. Emulator Integration

The frontend launches external emulators.

Do not embed emulation cores in the first version.

Create a generic emulator abstraction.

Example:

```kotlin
interface EmulatorLauncher {
    suspend fun canLaunch(
        emulator: EmulatorConfig,
        game: Game
    ): Boolean

    suspend fun launch(
        emulator: EmulatorConfig,
        game: Game
    ): LaunchResult
}
```

Emulator configuration should support:

```text
Package name
Activity
Intent action
URI
Extras
ROM argument
Core argument
Platform mapping
```

Never scatter emulator-specific intent code across features.

Use adapter implementations.

Example:

```text
RetroArchLauncher
PPSSPPLauncher
DolphinLauncher
DuckStationLauncher
GenericIntentLauncher
```

---

## 15. Android Storage

Prefer:

- Storage Access Framework
- Persistable URI permissions

Do not require broad filesystem permissions unless strictly necessary.

Treat removable SD cards as first-class storage.

All scanning code must handle inaccessible or removed storage gracefully.

---

## 16. Image Performance

Artwork is one of the biggest frontend performance risks.

Use Coil.

Rules:

1. Always request display-sized images.
2. Use memory and disk cache.
3. Avoid loading original-resolution artwork for grid cells.
4. Prefer stable image URLs / paths.
5. Use placeholders.
6. Avoid crossfade on very dense fast-scrolling grids when it causes jank.
7. Preload neighboring game covers selectively.
8. Do not preload the whole library.

Game grid thumbnails should have predictable dimensions.

---

## 17. Video Preview Performance

Video preview is optional.

Never autoplay video immediately when focus changes rapidly.

Recommended behavior:

```text
Focus game
   |
wait 500-800 ms
   |
still focused?
   |
start preview
```

Stop playback immediately when:

- Focus leaves
- Screen changes
- App is backgrounded
- Emulator is launched

Only one player should be active.

Reuse Media3 player instances where practical.

---

## 18. Compose Performance Rules

Performance is a core product feature.

### Required

Use:

- Stable immutable UI models
- `remember`
- `derivedStateOf` when appropriate
- `LazyColumn`
- `LazyRow`
- `LazyVerticalGrid`
- Stable keys
- ViewModel-level state
- Flow collection with lifecycle awareness

### Avoid

- Creating heavy objects during recomposition
- Reading files during composition
- Database access inside composables
- Large bitmap decoding on main thread
- Unbounded animations
- Nested scrolling containers without need
- Heavy blur
- Continuous shader animations
- Large animated gradients
- Excessive `graphicsLayer`
- Passing giant mutable objects through composables

Every list item must have a stable key.

Example:

```kotlin
items(
    items = games,
    key = { it.id }
) { game ->
    GameCard(game)
}
```

---

## 19. Performance Targets

The frontend should feel instant.

Target goals:

### Startup

Warm startup:

- Aim for < 500 ms to usable UI where realistic.

Cold startup:

- Show useful content as early as possible.
- Do not wait for scanning or scraping.

### Navigation

Controller input to visible response:

- Target < 50 ms perceived latency.

### Scrolling

Target:

- 60 FPS minimum
- 120 FPS where supported

Do not make 120 FPS a requirement for core correctness.

### Main Thread

No:

- Filesystem scans
- Hashing
- Archive operations
- Network requests
- Database migrations
- Large image decoding

on the main thread.

---

## 20. Data Flow

Preferred feature flow:

```text
UI Event
   |
ViewModel
   |
Use Case
   |
Repository
   |
Database / Android API / Network
   |
Flow
   |
ViewModel State
   |
Compose UI
```

Use unidirectional data flow.

Example:

```kotlin
data class PlatformUiState(
    val isLoading: Boolean = false,
    val games: List<GameUiModel> = emptyList(),
    val focusedGameId: Long? = null,
    val error: String? = null,
)
```

Do not make composables own domain state.

---

## 21. Scraping

Scraping is optional in the first milestone but architecture should allow it.

Create an abstraction:

```kotlin
interface MetadataProvider {
    suspend fun search(game: Game): MetadataResult
}
```

Potential metadata:

- Title
- Description
- Developer
- Publisher
- Release date
- Genre
- Box art
- Screenshot
- Video
- Logo

Scraping must be:

- Rate limited
- Cancelable
- Resume-capable
- Background-friendly

Never block game launching on scraping.

---

## 22. Theme System

The default theme uses the project's neon cyberpunk direction.

Theme tokens should include:

```kotlin
data class FrontendTheme(
    val background: Color,
    val surface: Color,
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val focusBorder: Color,
)
```

Do not hard-code theme colors across composables.

Create shared tokens.

Later themes may include:

- Neon
- Minimal Dark
- Classic Handheld
- CRT
- OLED Black

Animations should also be theme-aware.

---

## 23. Sound Design

Optional navigation sounds may be supported.

Sound effects:

- Focus move
- Confirm
- Back
- Favorite
- Error

Rules:

- Very short
- Low latency
- Never block input
- User can disable them
- Avoid playing sounds for repeated high-frequency focus movement if it becomes noisy

---

## 24. Gamepad Input Layer

Do not duplicate key handling across every screen.

Create a centralized abstraction.

Example:

```kotlin
enum class GameAction {
    Up,
    Down,
    Left,
    Right,
    Confirm,
    Back,
    Details,
    Favorite,
    PreviousCategory,
    NextCategory,
    Menu,
}
```

Map Android key codes into domain actions.

This allows future support for:

- Different controller layouts
- Custom remapping
- Xbox-style layout
- Nintendo-style layout

---

## 25. Device Adaptation

First target:

- KONKR Pocket Advance

However, do not hard-code UI dimensions to a single physical device.

Build responsive layouts around:

- Logical width
- Logical height
- Aspect ratio
- Density
- Font scale
- Safe content bounds

Prefer landscape-first layouts.

The UI must remain usable on:

- 4:3 displays
- 16:9 displays
- 16:10 displays
- compact Android handheld displays

Do not assume touch targets are the primary interaction target.

---

## 26. GBA-First Development

The first fully supported platform should be Game Boy Advance.

Initial ROM formats:

```text
.gba
.zip
```

First release should optimize the GBA experience before expanding platform support.

Required GBA features:

- Scan GBA ROM folders
- Display cover art
- Browse game library
- Favorite game
- Track play history
- Launch configured emulator
- Resume browsing after returning from emulator
- Game details
- Sorting
- Search
- Recent games

Architecture must remain platform-independent.

Never put GBA-specific assumptions into generic repository or UI APIs unless necessary.

---

## 27. Returning From Emulator

When returning to the frontend:

- Restore previous screen.
- Restore selected game.
- Restore scroll position.
- Refresh last-played metadata.
- Do not reload the entire library.
- Do not reset focus to the first item.

The user should feel like they returned to the exact same shelf position.

---

## 28. Error Handling

Errors must be understandable with controller-only navigation.

Examples:

```text
ROM file unavailable
Emulator not installed
Emulator configuration invalid
Storage permission lost
Metadata server unavailable
Archive unsupported
```

Provide a clear primary action.

Example:

```text
Emulator not installed

[A] Choose another emulator
[B] Back
```

Avoid raw exception messages in production UI.

---

## 29. Logging

Use structured logs.

Recommended tags:

```text
Library
Scanner
Database
Emulator
Input
Media
Scraper
Performance
Navigation
```

Never log:

- User credentials
- Authentication tokens
- Private server keys

File paths may be logged in debug builds but should be handled carefully in release builds.

---

## 30. Testing

Minimum test areas:

### Unit Tests

- ROM filename parsing
- Platform detection
- Sort title normalization
- Emulator mapping
- Metadata mapping
- Duplicate detection
- Scan state transitions

### Database Tests

- Inserts
- Updates
- Deletes
- Migrations
- Incremental scan behavior

### UI Tests

Important controller flows:

```text
Launch app
↓
Select GBA
↓
Move focus
↓
Open details
↓
Favorite
↓
Back
↓
Launch game
```

### Manual Hardware Testing

Always test important milestones on the real target handheld.

Emulator testing alone is insufficient for:

- D-pad navigation
- Key repeat
- Focus behavior
- Input latency
- Small-screen readability
- SD card behavior
- Emulator return flow

---

## 31. Profiling

Before introducing C++ optimizations, profile first.

Use:

- Android Studio Profiler
- System Trace
- Compose Layout Inspector
- Compose recomposition metrics
- Macrobenchmark
- Baseline Profiles

Native Core must only be introduced when profiling identifies a measurable bottleneck.

Examples of justified native optimization:

- Hashing tens of thousands of ROMs
- Archive enumeration
- Very large filesystem scans

Examples that do NOT justify JNI:

- Sorting a normal game list
- Updating favorites
- Reading Room entities
- UI state mapping

---

## 32. Baseline Profiles

Add Baseline Profile support before production release.

Focus benchmark flows on:

```text
App launch
Home rendering
Open GBA platform
Scroll game grid
Open Game Detail
Return to library
```

Startup and first-navigation performance are more important than micro-benchmarks.

---

## 33. Dependency Policy

Keep dependencies conservative.

Before adding a library, ask:

1. Can AndroidX already do this?
2. Can Kotlin stdlib already do this?
3. Is the dependency maintained?
4. Does it materially reduce complexity?
5. Does it increase startup or APK size significantly?

Avoid adding libraries for trivial helpers.

---

## 34. Coding Style

Use idiomatic Kotlin.

Prefer:

```kotlin
val
```

over mutable state.

Prefer immutable models.

Prefer small pure functions.

Prefer explicit names.

Avoid:

- God classes
- Static global state
- Singleton repositories without DI
- Deep inheritance
- Reflection-heavy frameworks
- Business logic inside composables
- Business logic inside Activities

---

## 35. Compose Component Rules

Shared components should live under:

```text
core/ui/components
```

Examples:

```text
GameCard
PlatformChip
FocusFrame
ControllerHintBar
GameCover
GameMetadataRow
PrimaryGameAction
EmptyLibraryState
```

Feature-specific components remain inside their feature package.

---

## 36. Accessibility

Controller-first does not mean accessibility can be ignored.

Provide:

- Content descriptions where appropriate
- Logical focus order
- Readable contrast
- Scalable text within reasonable bounds
- Reduced motion option

Do not communicate state only through color.

Example:

Favorite state should use:

- Icon
- Color
- Semantics

---

## 37. Localization

All visible strings must use Android string resources.

Do not hard-code English text in composables.

Initial languages may include:

- English
- Simplified Chinese

Architecture should support more languages later.

Game metadata may remain in the language provided by the metadata source.

---

## 38. Security and Privacy

This app is primarily local-first.

Principles:

- ROM library stays local by default.
- No account required for core functionality.
- No telemetry unless explicitly designed and disclosed.
- Metadata scraping should transmit only the minimum data necessary.

Never upload ROM files.

Never upload save files without explicit user action.

---

## 39. Release Milestones

### Milestone 1 — Shell

Implement:

- App shell
- Theme
- Navigation
- Controller input
- Focus system
- Mock GBA library

No real ROM scanning required.

### Milestone 2 — Local GBA Library

Implement:

- SAF folder selection
- `.gba` scan
- Room database
- Game grid
- Favorites
- Search
- Sorting

### Milestone 3 — Emulator Launch

Implement:

- Emulator configuration
- Generic launch abstraction
- At least one working GBA emulator integration
- Return-to-library state restoration

### Milestone 4 — Metadata

Implement:

- Cover art
- Screenshots
- Metadata provider abstraction
- Background scraping

### Milestone 5 — Media

Implement:

- Media3 video preview
- Preview delay
- Playback lifecycle
- Settings

### Milestone 6 — Optimization

Implement:

- Baseline Profiles
- Macrobenchmark
- Scan profiling
- Image cache tuning

Only introduce C++ after this milestone if justified.

### Milestone 7 — More Platforms

Expand to:

- GB
- GBC
- NES
- SNES
- PS1

Platform definitions should be data-driven.

---

## 40. Definition of Done

A feature is not complete unless:

- It works with controller-only navigation.
- Focus state is always visible.
- It does not perform blocking work on the main thread.
- It handles process recreation where relevant.
- It handles missing storage gracefully.
- It works on the first target handheld.
- It follows the architecture boundaries in this document.
- It does not introduce unnecessary native code.
- It maintains smooth navigation with a realistically sized game library.

---

## 41. Agent Instructions

When modifying this project, coding agents must follow these rules.

### Before Coding

1. Read this file.
2. Inspect existing architecture before creating new modules.
3. Reuse established patterns.
4. Determine whether the change affects controller navigation.
5. Determine whether the change affects performance.

### During Coding

1. Keep controller navigation working.
2. Keep UI state immutable where practical.
3. Keep I/O off the main thread.
4. Do not bypass repositories from UI code.
5. Do not introduce JNI unless requested or profiling justifies it.
6. Avoid unnecessary dependencies.
7. Preserve focus and scroll state.
8. Favor simple code over clever abstractions.

### After Coding

Verify:

```text
Build passes
Unit tests pass
Controller navigation works
No obvious main-thread I/O
No broken focus path
No unnecessary recompositions
No regression in game launching
```

For UI changes, always verify:

```text
D-pad only
A/B buttons
Focus visibility
Small-screen readability
Fast repeated navigation
```

---

## 42. Non-Goals for Initial Version

Do not implement these unless explicitly requested:

- Built-in emulator cores
- Cloud ROM streaming
- Online multiplayer
- Social network
- Full Android app launcher replacement
- Storefront
- ROM downloading
- ROM distribution
- Real-time animated 3D backgrounds
- Large plugin system
- Complex user accounts

Keep the first version focused:

```text
Scan
Browse
Focus
Launch
Return
```

That loop must feel excellent.

---

## 43. Product Priority

When tradeoffs appear, use this order:

```text
Controller UX
>
Responsiveness
>
Reliability
>
Visual polish
>
Feature count
>
Cross-platform abstraction
```

The project succeeds when the handheld feels like a console, not when it has the largest settings page.
