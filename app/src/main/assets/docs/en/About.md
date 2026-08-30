# UIN Tool

![Version](https://img.shields.io/badge/version-5.6.0-blue)
![Build](https://img.shields.io/badge/build-21-green)
![Android](https://img.shields.io/badge/Android-6.0%2B-brightgreen)
![License](https://img.shields.io/badge/license-MIT-orange)
![Kotlin](https://img.shields.io/badge/Kotlin-2.1.0-purple)
![Compose](https://img.shields.io/badge/Jetpack%20Compose-2024.09.00-blue)

## App Introduction

UIN Tool is an Android plugin framework application rebuilt with Kotlin + Jetpack Compose, allowing users to dynamically load and run third-party plugins. Whether it's native Java plugins or Web technology stack (HTML/CSS/JS) plugins, they can run seamlessly in UIN Tool. It provides a complete plugin ecosystem, including plugin development, management, runtime, permission control, and **Termux backend integration**.

### Core Philosophy

- **Open**: Anyone can develop plugins, supporting native Java and Web technology stacks
- **Secure**: Plugin permission authorization, supports signature verification, prevents malicious plugins
- **Efficient**: Native performance, Web plugins support hot updates without recompilation
- **Easy to use**: Visual development wizard, create plugins without complex configuration
- **Flexible**: Supports grid/list view switching, supports category management
- **Modern**: Built on Jetpack Compose, Material 3 design language, supports neumorphism style
- **Internationalized**: Supports in-app language switching without changing system language
- **Powerful**: Built-in Termux terminal environment, supports Python/Node.js/PHP backends
- **Persistent**: Plugin data independently stored, user data automatically preserved on updates

---

## Terminal Features (Based on Termux)

UIN Tool **includes a complete terminal environment**, with its core engine adapted from [Termux](https://github.com/termux/termux-app), providing users with a powerful Linux command-line experience.

### Terminal Features

| Feature | Description |
|---------|-------------|
| **Shell Support** | bash, zsh, fish, and other mainstream shells |
| **Package Manager** | APT (Debian/Ubuntu software sources) |
| **Development Tools** | gcc, clang, make, git, etc. |
| **Script Languages** | Python, Node.js, Ruby, Perl, etc. |
| **Text Editors** | vim, nano, emacs, etc. |
| **Network Tools** | curl, wget, openssh, etc. |
| **Multi-Session** | Multiple terminal sessions running simultaneously |
| **Multi-Window** | Android 7.0+ multi-window support |
| **Custom Shortcuts** | Configurable hardware/software keyboard shortcuts |
| **Terminal Color Schemes** | Customizable themes and color schemes |

### Terminal Use Cases

- **Development & Debugging**: Write and debug code directly on Android devices
- **Server Management**: Manage remote servers via SSH
- **Learning Linux**: Experience a complete Linux environment without root
- **Automation Scripts**: Write Shell/Python scripts for automated tasks

### Termux Acknowledgments

> The terminal feature implementation is based on the [Termux](https://github.com/termux/termux-app) project, a well-known Android terminal emulator and Linux environment. UIN Tool has adapted and enhanced Termux's core code, seamlessly integrating it into the plugin framework. Thanks to the Termux team for their open-source contribution!

---

## Version Information

### Current Version: v5.6.0 (Build 22)

| Item | Info |
|------|------|
| Version Number | 5.6.0 |
| Version Code | 22 |
| Update Date | August 28, 2026 |
| Minimum Android Version | 6.0 (API 23) |
| Target Android Version | 14 (API 34) |
| Compile SDK Version | 35 (Android 15) |
| Architecture | arm64-v8a |

### Version History

#### v5.6.0 (Build 22) - Neumorphism Style, Multilingual Switching, and Translucent Effect Control

**Neumorphism Style (Neumorphism):**
- Adds neumorphism UI style, providing soft concave-convex light-shadow effects for cards, buttons, and other components
- Enable in "UI Personalization" > "Effects" page, mutually exclusive with glass effect

**Multilingual Switching:**
- Supports directly switching language within the app (Chinese/English, etc.) without changing system language
- Select in "Manage" > "UI Personalization" > "Language" page

**Translucent Effect Transparency Control:**
- Glass effect and neumorphism effect support transparency (alpha) adjustment
- Drag the slider on the "Effects" page for real-time preview

#### v5.5.0 (Build 21) - Compatibility Fixes, Real Termux Architecture Upgrade, and Development Wizard Completion

**Native Plugin Crash Fixes:**
- Fixes native plugin `AbstractMethodError` crash (`-Xjvm-default=all` + rebuild host-sdk.jar + repackage plugin.dex + host reflection guard)
- CUI plugin proot container script path error fix
- Crash log page not jumping fix
- Fixes MarkdownRenderer `appendReplacement` crash (`$` treated as group reference)
- Fixes DocViewerScreen crash + background color inconsistent with host

**Clipboard Pseudo-permissions:**
- Adds pseudo-permissions `READ_CLIPBOARD` / `WRITE_CLIPBOARD`, effective just by declaration

**Development Wizard Completion:**
- Backend (`backendStartCommand`/`backendTimeout`/`backendHealthCheck`), openWith, all permissions popup multi-select; retains plugin notice (notice)
- Wizard reads back plugin.json `permissions`/`dependencies` compatible with both JSON array and comma string
- Plugin templates render README by type (native/Web/Web+backend/CUI)
- **Field Streamlining**: Removes max memory / max CPU time / max concurrent tasks, dependencies, API level, backend keep-alive input fields (no longer written to wizard-generated plugin.json)
- Fixes code editor always showing default MainPlugin.java (entry file regenerated synchronously after main class name change)

**Permission Prompt Before Opening Plugin:**
- Web plugins show ungranted permissions (OK/Don't Prompt Again/Manage Permissions); native plugins show required permissions (OK/Don't Show Again), **popup first then open plugin**
- Permission popups use host unified style rendering; permission management page removes refresh button, button text changed to "Grant All" / "Revoke All"

**Keep Session Single Window:**
- "Keep Session on Close" **enabled by default** (default single-window deduplication: same Web plugin keeps only one background window); user explicitly disables to support multi-instance

**Real Termux Shared Supervisor:**
- Real Termux uses a single resident shared supervisor, container/session initialized only once, all plugin backends run as supervisor child processes, cold startup saves ~5s initialization overhead
- Communication protocol (`<plugins_root>/.uin/`): cmd/pid/stop/idle/idle.start/alive/host_alive/shutdown/keep_alive
- Idle reclamation: supervisor independently times out and recursively kills process trees per plugin based on `idle/<key>.start` startup timestamps, `kill -0 $pid` detects process liveness
- Performance: warm startup ~0.5s, warm restart ~2s, cold startup ~4s (removes probeRealTermux, 200ms polling, host_alive 300s timeout)

**Startup Environment Auto-Installation:**
- Software auto-detects and installs Termux bootstrap + Alpine container in background on startup (built-in mode only)
- Bootstrap installation immediately unblocks terminal, terminal no longer shows black screen

**Static Desktop Shortcuts:**
- 4 commonly used page static shortcuts: Docs, Terminal, Backend Settings, UI Personalization

#### v5.4.0 (Build 20) - Proactive Capability Extension: Plugin Receives External Content (openWith Relay) + Plugin Multi-Instance

**Plugin Receives External Content (Intent Relay, openWith):**
- System/other apps "Share" or "Open with other apps" **text, links, files** can be sent to UIN Tool; relay page lists all plugins declaring `openWith` that match, user selects which plugin to handle
- `plugin.json` adds **`openWith`** field (`enabled` / `label` / `mimeTypes` / `acceptText` / `acceptUrl` / `acceptFile`), supports MIME wildcard matching
- Supports single file / multi-file (`SEND_MULTIPLE`) reception, files copied into plugin `.incoming/` directory for backend direct reading
- **Web plugins**: Host injects `window.UINOpenData` / `window.getOpenData()`, can also use `UINPlugin.getOpenData()` to read; **native plugins**: `onHostEvent("host.open", bundle)` carries `instanceId` / `openDataJson`
- Auto-opens when only 1 plugin matches; relay page supports **real-time search** (filter by display name / plugin ID / description)

**Plugin Multi-Instance (Multi-Instance):**
- **Web / CUI plugins support multi-instance by default**; **native plugins** single instance by default, enable "Native Plugin Multi-Instance (Experimental)" in development tools page
- Each instance has globally unique `instanceId`, host isolates lifecycle / WebView / native instances / backend by instance key (`pluginId:instanceId`)
- Backend: default multi-instance shares same process (shared port); "Per-Instance Independent Backend Port" in "Development Tools" enables each instance to have its own process and port

**Other:**
- Plugin management page list item "Launch" button changed to "**Add Desktop Shortcut**" (`+` icon)
- Relay page adds search; version number upgraded to 5.4.0 (Build 20)

#### v5.3.0 (Build 19) - Comprehensive Refinement: Unified UI Component System + Color Picker/Category Management/Permission Management Enhancement

**Global Gradient Background:**
- Adds **global gradient background effect**, configurable in "UI Personalization" > "Effects" page, takes effect on **all pages**, top title bar also follows gradient
- Supports **toggle** to disable; supports **single-select (single-color gradient)** / **multi-select (multi-color gradient)** two modes, multi-select can add/edit/delete 2-6 colors
- Supports **gradient direction setting**: separately specify "start direction" and "end direction" (up/down/top-left/bottom-left/top-right/bottom-right, 6 directions)
- **Default light blue** (`#FFC4D6DF → #FFD6E8F2 → #FFEAF4FA`, bottom-right → top-left), auto-adapts with light/dark theme; configuration participates in save/reset/export/import

**Glass Effect / Navigation / Cards / Button Optimization:**
- **Glass transparency increased**: Glass cards/backgrounds more transparent and bright
- **Bottom navigation floating style**: Rounded corners on all sides, whitespace on left/right/bottom, no longer flush at bottom; navigation **covers content**, scrolling cards show from surrounding transparent whitespace without clipping
- **Floating navigation completely follows card style**: Opaque card background (more white and solid than glass cards), card rounded corners and shadow; content area **full-height scrolling** behind floating bar (eliminates horizontal clipping line); clicking has **press-to-scale** feedback, selected icon **enlarges**, **no ripple, no border**
- **Dialog background follows main background and ensures readability**: When gradient/glass is enabled, dialog background shows through main background, transparency maintained at high level (0.95), text clear; fixes plugin dialog background covering plugin page content
- **Plugin management page card style unified**: Checkboxes only show in selection mode (top bar list icon enters/exits); batch delete supports **deleting all selected plugins at once** (with quantity confirmation dialog)
- **Button outline improved**: Outlined buttons with 1dp theme outline, white/transparent background buttons have clear contours

**UI Personalization Page Optimization:**
- Top "Colors / Shape / Size / Font / Effects" **tab bar follows background color** (transparent when gradient enabled), **removes bottom border line**
- Effects page "Gradient Mode" changed to **dropdown** (single/multi-select), **collapsed by default**, click to expand
- Gradient "start / end direction" also changed to **dropdown**, all gradient configuration (toggle/mode/direction/colors) **merged into single card**, more centralized configuration
- **Feature Verification & Fixes**: **Ripple toggle actually works** (globally disables click ripples when off), **status bar/navigation bar color keys** no longer overridden by theme (takes effect on save), **dark primary (`primary_dark`)** mapped to secondary container text color (auto-migrates old defaults on upgrade)

**Code Editor / Development Wizard Optimization:**
- Code editor **file list background follows main background** (transparent when gradient enabled, showing global gradient)
- Development wizard bottom "Next" action bar changed to **floating pure transparent**: covers content without clipping cards, surrounding transparent whitespace shows scrolling cards; **background completely transparent, no card background color or shadow**, only "Previous / Next" buttons provide height, tight to buttons

**Permission Status Auto-Refresh:**
- Fixes issue where manual pull-to-refresh was required after granting; permission checkmark state derived from refresh key, authorization callback/Shizuku listener/system settings return auto-refresh
- Permission page adds **foreground resume (ON_RESUME) auto-refresh**

**Unified UI Component System:**
- `Unified*` components become the sole implementation source (buttons 6 variants/3 sizes, cards 4 variants, input fields, text, switches, tags, icon buttons, list items, progress bars, empty/loading states, dialog system)
- `UIComponents` refactored into thin delegation layer, single source of behavior, API fully compatible; adds `UnifiedIconButton`/`UnifiedListItem`/`UnifiedLinearProgressIndicator`
- All Compose screens migrated to directly use `Unified*` components (only retains top bar/pull-to-refresh/skeleton screen and some custom color dialog containers)
- Unified cards/input fields/dialogs support **glass effect**: semi-transparent background, no border, no shadow, color follows theme

**Color Picker Enhancement:**
- `FullColorPickerDialog` supports **theme following (dark mode)**, color scheme follows light/dark theme
- Adds **visual color picker** (hue bar + saturation/brightness panel, click/drag to pick color), real-time sync with RGB/Alpha sliders and preset palette
- Adds **hex color input box** (`#RRGGBB` / `#AARRGGBB`), input shows real-time preview; dialog title changed to "Color Picker"

**Plugin Category Management Enhancement:**
- Plugin details page adds "Change Category" entry; export/delete action bar adds "Change Category" button, supports **batch category modification**
- Category popup supports **selecting existing categories** or **custom new categories**

**Plugin Details Enhancement (Tools/Management Page):**
- Tools page plugin item **long-press** shows same details popup as management page, details popup adds "**Change Category**" and "**Uninstall**" buttons, can directly modify category or uninstall plugin (with confirmation dialog)

**Permission Management Enhancement:**
- Adds **Shizuku** and **Dhizuku** permission support, **fully using official API** (adds `dev.rikka.shizuku:api` / `dev.rikka.shizuku:provider` and `io.github.iamr0s:Dhizuku-API` dependencies)
  - **Shizuku**: `Shizuku.pingBinder()` + `checkSelfPermission()` detection, `requestPermission()` requests authorization (results real-time callback refresh), Manifest adds `ShizukuProvider` (fixes crash)
  - **Dhizuku**: `Dhizuku.init()` + `isPermissionGranted()` detection, `requestPermission()` requests authorization (fixes authorization success not checked)

**Code Structure Improvement:**
- Mirror constants unified (`UpdateChecker` reuses `AppConstants.DEFAULT_MIRRORS`); file utilities unified (`FileUtils` delegates to `FileManager`); size formatting converged to `Extensions.formatFileSize`; `BackupRepository` → `IBackupRepository` naming unified

#### v5.2.0 (Build 18) - Packaging and Update Refinement + Development Wizard/Editor/Plugin Details Enhancement

**Packaging Logic Refinement (Package Everything):**
- `packageTpk` rewrite: recursively packages entire project directory (`web/`, `scripts/`, `scripts/backend/server.py`, `start.sh`, `res/`, `src/`, and any resources all included in TPK), explicitly adds `plugin.json`, `icon.png`, `README.md`, native placeholder/real `plugin.dex`; skips hidden files and `.tpk` output; Web without `index.html` writes default page as fallback

**Update Logic Refinement:**
- Silent update check (once daily) via `KEY_LAST_UPDATE_CHECK`; new version and not ignored shows update dialog; adds shared `UpdateContent.kt` component (`ReleaseChangelog` Markdown rendering + `UpdateDialog`), shared between Splash and management page "Check for Updates"; `VersionUpdateScreen` full-screen Markdown version update page

**Built-in Termux Alpine Installation Speedup:**
- `ensureAlpine()` removes network `pkg install proot-distro -y` (main cause of slow first install), only does existence check; first install only has rootfs decompression one-time overhead

**Development Wizard Refinement:**
- Packaging no longer auto-exits (button Package → Finish); configuration page completes all fields + permission multi-select (37 permissions); `plugin.json` editor JSON syntax highlighting; field info icons removed, only retains title row overview button

**Code Editor:**
- File tree long-press menu (view properties / rename), properties popup and rename validation

**Plugin Management Page:**
- Click plugin opens scrollable details dialog: plugin.json fields + file structure tree + plugin size/file count + plugin.json raw text + run button

**Backend Settings Reorganization:**
- "Backend Runtime Settings" changed to complete page; real Termux initialization command card one-click copy (shared `BackendConfig.buildRealTermuxSetupCode()`); management page card rearrangement; development page removes backend settings entry and old dialog

#### v5.1.0 (Build 17) - Backend Runtime Architecture Refactoring + Development Tools Integration

**Backend Runtime Architecture Refactoring:**
- Adds global "Backend Runtime Settings" (development page plugin tool card / management page menu): **Built-in Termux** (default, forced Proot Alpine container) or **Real Termux** (`com.termux`'s `RUN_COMMAND`), real Termux optionally selects Termux native / Proot container environment
- Backend startup unified to "startup command" mode (`backendStartCommand`): removes language-based interpreter (python/node/php/...) and pre-start command popup flow, legacy plugins auto-migrate
- Adds backend **idle auto-reclamation** (default 5 minutes, configurable) and real Termux ready detection / one-click guide (`allow-external-apps`, `termux-setup-storage`, `proot-distro install`)
- Real Termux processes cannot be killed, backend stop changed to calling HTTP `/stop` endpoint for graceful shutdown

**CUI Terminal Startup Optimization:**
- Full-screen terminal changed to direct foreground launch (`SWITCH_TO_NEW_SESSION_AND_DONT_OPEN_ACTIVITY`), no longer depends on overlay permission
- Pure fade transition (`fade_in, 0`), fixes system desktop visible during cross-fade gap

**Development Tools Integration:**
- "Runtime Logs" and "Developer Options" merged into standalone "Development Tools" page; management page adds "Backend Runtime Settings" menu item
- Auto-jumps to "Development Tools" page to display crash log after crash

**Plugin Development Refinement:**
- web + backend plugin wizard generates inlined script `index.html`, no longer generates `web/script.js`
- Backend template unified to `scripts/start.sh` + `scripts/backend/server.py` (reads `$PORT`, `/health`, `/stop` endpoints)
- `plugin.json` editor dialog completes backend fields (`backendStartCommand`, etc.), `applyPluginJson` reads back synchronously

#### v5.0.0 (Build 16) - Full Internationalization + Dynamic Theme Engine + Comprehensive UI Optimization

**Full Internationalization (i18n):**
- All hardcoded Chinese text in the app migrated to string resources: default English (en) + complete Simplified Chinese (zh-rCN), removed Japanese resources
- Covers 2600+ string keys, involving all screens (main interface, plugin management, permissions, repository, logs, backup, mirrors, documentation/help, development wizard, code editor, desktop widgets, etc.)

**Dynamic Theme Engine:**
- Adds JSON dynamic theme engine: `UINToolTheme` reads UIConfig color scheme and takes effect immediately, supports dark mode synchronization
- Plugin WebView injects `--uin-*` CSS variables, theme color synchronizes with app
- Corner radius, font size all changed to configuration-driven, covering all pages
- Management bottom navigation bar and system status bar color follows theme, fixes purple theme residue

**Bottom Navigation Refactoring:**
- Bottom navigation changed to self-drawn (Row + clickable(indication=null)), avoids material3 NavigationBar / LocalIndication version differences
- Top thin border drawn along rounded corners, indicator icon changed to terminal prompt `>_`
- Tab switching adds horizontal slide + fade-in transition animation (AnimatedContent)

**Pull-to-Refresh Unification:**
- Removes all page top-right refresh icons, unified to Material 3 PullToRefreshBox pull-to-refresh
- Empty list state also supports pull-to-refresh; indicator uses theme color and fixed center
- 8 refreshable pages show "last update time", time fades in and auto-fades out after 1 second

**UI Optimization & Interaction:**
- Plugin list add/remove animation (animateItem + key); repository/plugin management loading changed to skeleton screen
- Mirror management "add mirror" changed to bottom-right FAB; Toast unified to Material Snackbar (global host + lifecycle-aware)
- Glass effects applied to all cards and dialogs (UI personalization toggle control)
- Global Activity switching smooth fade-in/fade-out; window switching horizontal slide
- Management page top bar unified to `ManageTopAppBar`, color follows theme and page background

**Other Optimizations:**
- Light/dark color palettes can be edited and used simultaneously, app loads theme on startup
- Splash restores transparent background icon, faster startup (700ms fade-in scale animation)
- Fixes management page back/save button click not working, mirror dialog purple background, etc.

#### v4.5.0 (Build 15) - Proot Container Runtime + Custom Backend

**Proot Container Runtime (`backendRuntime: "proot"`):**
- Plugin backend can run in a **shared Alpine container**, isolated from host environment
- Auto-initializes Termux environment on first use, restores Alpine container offline from `assets/alpine.tar.xz` via `proot-distro restore`
- `assets/alpine.tar.xz` is a backup generated by `proot-distro backup alpine`, built-in pre-installed Python environment
- Can use `apk add` to install dependencies inside the container, does not pollute host Termux environment
- Plugin directory automatically bound to `/plugins/<pluginId>` inside the container, entry file directly visible in the container
- `127.0.0.1:PORT` inside the container communicates with host, backend API calls require no extra configuration
- Environment pipeline: Termux ready → Alpine ready → pre-start command → start backend

**Pre-Start Command (`backendPreCommand`):**
- Plugin can configure a pre-start command, executed in the Termux terminal (e.g., install dependencies, initialize data)
- On first open, popup to choose: "Run Now" / "Later" / "Cancel"
- After successful execution (exit 0) once, permanently skipped (`pre_cmd_done` marker, stored in `plugin_data_<id>`)
- On execution failure, automatically returns to plugin page and shows exit code and error message

**Custom Backend Mode (`backend: "other"`):**
- Host does not auto-start backend process; the pre-start command launches the service in the terminal
- Backend readiness determined by TCP port polling (200ms), timeout relaxed to 90s+ to accommodate container cold startup
- Supports portless plugins (`backendPort: 0`), pre-command session alive means running

**Backend Connection Speedup:**
- Three OkHttpClient instances (PluginBackendManager / PluginHostActivity / PluginJSInterface) add `.proxy(Proxy.NO_PROXY)`, prevents system proxy hijacking loopback traffic
- `waitForReady` removes 1s hardcoded delay, changed to 200ms TCP port detection + HTTP health check polling
- When stopping backend, terminates by process group `SIGKILL` (`Os.kill(-pid, SIGKILL)`), ensures proot child processes also exit

**Other Fixes:**
- Fixes onboarding flash and re-popup after skipping: removes SplashActivity dual navigation paths, unified to Compose-driven, fixes permission dialog first-frame flash

**Wizard & Documentation:**
- Plugin wizard supports "Backend Runtime Environment" (Termux native / Proot container) and "Pre-Start Command" configuration
- Backend selection adds "Custom (Manual Start)" type
- **CUI Terminal Plugin**: Create plugin adds "CUI Terminal (Command-Line Interface)" type, 4-step wizard auto-generates `scripts/script.py` example script and startup command configuration
- Changelog, help documentation, README updated

### Template Export Refactoring + Development Tools Optimization

**Plugin Template Export Refactoring:**
- Export template changed to directly copy **7 packaged plugins** from `assets/test_plugins/` (cuitest / othertest / termux / allapi / storage / NativeTestPlugin / web_plugin_template) as importable ready-made templates
- Auto-generates `README.txt` on export, listing each template file's purpose and import usage
- Cleans up original scattered plugin templates in assets (`templates/`, `test_plugins/` old files, root `template.tpk`), unified by built-in packaged plugins

**Export Flow Fix:**
- Fixes "Exporting..." button getting stuck: export changed to background thread execution, resets state on main thread after completion
- Toast display thread-safe: background thread calling Toast no longer crashes (auto-switches to main thread for display)

**UI Optimization:**
- Create plugin related buttons changed to pure theme color (`PrimaryButton`), removes gradient style

**Build Optimization:**
- Streamlines `proguard-rules.pro`: only retains important code that might be deleted by R8 — **shell plugin host placeholder implementation** (`com.UIN.Tool.plugin.**` / `com.UIN.Tool.core.plugin.**`, interfaces and host classes DexClassLoader depends on when loading external dex), `@JavascriptInterface` methods, plugin JSON models, etc.; removes overly broad rules like "retain all androidx/compose/classes with empty constructors", reduces release package size

---

#### v4.4.4 (Build 14) - Plugin Dialog System Unification + Interaction Fixes

**Plugin Dialog System Unification:**
- Plugin dialogs all changed to app's built-in Compose unified dialog components (`UnifiedDialog` / `UnifiedConfirmDialog` / `UnifiedInfoDialog`)
- Removes old `UnifiedViewDialog` custom popup implementation
- JS `alert` / `confirm` / confirm dialog / input dialog / special permission popups unified through the same dialog components

**Dialog Queue Mechanism:**
- Multiple popup requests display in sequence, no longer overwrite each other
- Automatically shows the next popup after the previous one closes
- Adds callback-style popup API: `showConfirmDialog(title, message, callbackId)`, `showPromptDialog(title, hint, callbackId)`

**Interaction Fixes:**
- Fixes plugin page unable to scroll/click: dialog overlay hidden by default, only shows when popup is displayed
- Fixes confirm dialog not showing
- Fixes screenshot function unable to save (changed to view drawing capture method)
- Fixes screenshot silent failure when no storage permission

---

#### v4.4.0 (Build 13) - Major Update: Plugin Data Persistent Storage + Permission System Completion

**Plugin Data Persistent Storage:**

**Core Storage System:**
- **Unified data storage**: SharedPreferences-based key-value storage, supports String/Int/Long/Boolean/Float/JSON full types
- **Independent storage isolation**: Each plugin has an independent `data/` directory, data does not interfere
- **File storage system**: Supports read/write, delete, copy, move, list, get size, and other complete file operations
- **Secure path protection**: Prevents path traversal attacks, ensures files can only operate within plugin directory
- **Disk space check**: Checks available space before writing to prevent disk full
- **Concurrency safety**: Uses ReentrantReadWriteLock to ensure multi-thread safety
- **Data version management**: Supports plugin data version migration, auto-migrates on upgrade
- **Data statistics**: KV count, file count, total size, cache size, and other information

**Web Plugin Storage API (JavaScript):**
```javascript
// KV storage
UINPlugin.setStorage('key', 'value');
UINPlugin.getStorage('key');
UINPlugin.setStorageInt('key', 123);
UINPlugin.setStorageBool('key', true);
UINPlugin.setStorageJSON('key', JSON.stringify({name: 'test'}));

// Batch operations
UINPlugin.setStorageBatch(JSON.stringify({k1:'v1', k2:'v2'}));
const result = JSON.parse(UINPlugin.getStorageBatch('["k1","k2"]'));

// File operations
UINPlugin.writeFile('notes.txt', 'Hello World');
const content = UINPlugin.readFile('notes.txt');
UINPlugin.deleteFile('notes.txt');

// Storage statistics
const stats = JSON.parse(UINPlugin.getStorageStats());
console.log('KV:', stats.kvCount, 'Files:', stats.fileCount);
```

Native Plugin Storage API (Kotlin):

```kotlin
val pctx = PluginContext(context, pluginDir)
pctx.putString("key", "value")
val value = pctx.getString("key")
pctx.writeFile("data.txt", "content")
val content = pctx.readFile("data.txt")
```

Permission System Fully Completed:

· Permanent authorization state: Plugin permission status persisted, one-time authorization permanently effective
· Permission state values: 0=unauthorized (show popup), 1=authorized (enter directly), 2=denied (enter directly)
· One-click authorization: Supports granting all permissions at once
· Permission grouping: Regular and special permissions grouped requests
· Special permission guidance: Overlay, modify system settings, and other special permissions guide users to system settings to enable
· Permission popup optimization: Material Design 3 style popup, intuitive permission description display
· Permission status query: Visual view of each permission's status

Code Editor Enhancement:

· TextMate syntax highlighting: Supports 30+ programming languages
· 28+ editor themes: Dark/light theme free switching
· File tree management: Sidebar file list, supports add/delete files
· File icons: Different icons based on file type
· Undo/Redo: Complete editing history

API Extension:
Web plugins add 140+ API interfaces, covering the following categories:

· Device information (16): Model, version, screen, memory, CPU, build info, etc.
· Sensors (9): Accelerometer, gyroscope, light, proximity, magnetic field, orientation, pressure, temperature, humidity
· Location services (2): Get location, reverse geocoding
· Screen/Display (5): Brightness, auto brightness, display info, font scaling
· System settings (7): Airplane mode, Bluetooth, WiFi, mobile data, location, NFC, auto-rotate, do not disturb
· Storage info (3): Total capacity, available capacity, usage percentage
· Network data (11): Network info, WiFi info, signal strength, carrier, IP, speed, Ping
· Battery (5): Level, health, voltage, temperature, technology
· Audio (5): Volume, max volume, mute, headphone status
· Time/Date (5): Current time, timezone, daylight saving
· System language (4): System language, country, region
· App management (7): App list, open app, app info
· File operations (15): Read/write/delete, copy/move, directory operations, file info
· Network requests (5): GET, POST, PUT, DELETE, download
· Permissions (3): Check, request, batch request
· UI (4): Loading, confirm dialog, input dialog
· Clipboard (3): Copy, get, clear
· Vibration (2): Vibrate, cancel
· Notifications (2): Send, cancel
· System operations (10): Open various settings, fullscreen, keep awake, screenshot
· Events (2): Send event, add listener

Data Migration:

· Old `web_plugin_` SharedPreferences data auto-migrates to new storage system
· Plugin data/ directory preserved on update, user data not lost
· Auto-cleans all data when uninstalling plugin

Bug Fixes:

· Fixes permission popup clicking cancel still entering plugin
· Fixes permission state persistence failure
· Fixes some permissions still repeatedly popup after being denied
· Optimizes permission request flow, one-time authorization permanently effective

---

v4.2.0 (July 24, 2026) - Termux Backend Integration

Major Update: Plugin Backend Support

Termux Backend Integration:

· Web plugins can directly start Termux backend services (Python/Node.js/PHP/binary)
· Backend services auto-start, completely transparent to users
· HTTP API communication (no WebSocket needed)
· Backend must provide /health health check endpoint
· Backend process lifecycle management (auto-stops when plugin closes)
· Auto port allocation (default 8000)

Backend Template:

· Adds python_template.tpk template
· Uses Python built-in http.server, no need to install Flask
· Compatible with Python 3.14+ (removes cgi module dependency)
· Supports computation, logging, querying, system commands, and other APIs

Plugin Notice Feature:

· Plugins can declare notice field in plugin.json
· Notice popup auto-displays on first plugin open
· Users can choose "Don't prompt again" or "Remind later"
· Plugin management page can view complete notice

Plugin Creation Wizard Optimization:

· Unified "Create Plugin" entry, popup selects frontend type
· Supports native UI, pure WebView, WebView + backend three modes
· Backend selection: Python, Node.js, PHP, binary files
· Web plugins auto-generate blank HTML/CSS/JS files
· Binary backend supports directly selecting executable files
· Wizard steps dynamically adjust based on type (4-5 steps)

Backend Management Enhancement:

· PluginBackendManager unified backend process management
· Supports multi-language interpreter path auto-finding
· Output monitoring (stdout/stderr)
· Health check (waits for service ready)
· Python path prioritizes Termux's python command

UI Unification:

· Popups unified to Compose AlertDialog, white background Material 3 style
· Development page button colors unified
· Removes all Emoji

---

v4.1.0 (July 17, 2026) - Major Update: Code Editor Upgrade (Sora Editor)

Code Editor Comprehensive Upgrade:

· Sora Editor Integration: Replaces old simple editor, uses professional Sora Editor engine
· TextMate Syntax Highlighting: Supports syntax highlighting for 30+ programming languages
· Theme System: Built-in 28+ code editor themes (dark/light)
· Language Support: Java, Kotlin, Python, JavaScript, TypeScript, HTML, CSS, JSON, XML, Markdown, Shell, SQL, Go, Rust, PHP, Ruby, Swift, Dart, Lua, Scala, Perl, Haskell, Elixir, Erlang, Clojure, Groovy, Dockerfile, Makefile, INI, Properties, TOML, YAML, etc.
· Line Numbers: Supports line number display
· Code Folding: Supports code block folding
· Bracket Matching: Auto-highlight matching brackets
· Auto-Indentation: Smart auto-indentation
· Undo/Redo: Complete undo/redo history
· Theme Switching: One-click editor theme switching
· File Tree Management: Sidebar file list, supports add/delete files
· File Icons: Different icons based on file type

Other Optimizations:

· Fixes plugin permission management UI
· Fixes backup/restore functionality
· Optimizes app startup speed
· Unifies Toast and popup styles

Technical Details:

· Sora Editor version: 0.24.4
· Uses editor-bom for unified version management
· language-textmate module provides syntax highlighting

---

v4.0.0 (July 14, 2026) - Major Refactoring

Major Technology Stack Upgrade:

Architecture Refactoring:

· Kotlin Migration: Core code migrated from Java to Kotlin
· Jetpack Compose: UI fully migrated to declarative Compose framework
· MVVM Architecture: Introduces ViewModel + StateFlow reactive state management
· Dependency Injection: ServiceLocator unified service instance management
· Repository Pattern: Data layer and business layer separation

Terminal Integration (Based on Termux):

· Built-in Termux Engine: Integrates Termux terminal emulator core
· Complete Linux Environment: Supports APT package management, bash/zsh, etc.
· Multi-Session Management: Supports running multiple terminal sessions simultaneously
· Terminal Settings: Font, color scheme, keyboard, shortcuts all configurable

UI Comprehensive Upgrade:

· Material 3 Design: Adopts latest Material Design specification
· Dark Mode Support: Complete dark/light theme switching
· Glass Effect: Frosted glass texture UI components
· Complete Color Picker: RGB + Alpha channel independent adjustment
· 38+ Color Configuration Items: All colors customizable
· 7 Corner Radius Configurations: Comprehensive UI shape control

Feature Enhancement:

· Plugin Permission System: Plugin permission management based on Android permission model
· Plugin Dependency Check: Auto-checks and prompts for missing dependencies
· UI Configuration Import/Export: Supports configuration backup and sharing
· Complete Backup System: Backup plugins, configurations, UI themes

Development Experience Optimization:

· Plugin Creation Wizard: Visual step-by-step guidance for creating plugins
· Built-in Code Editor: Supports zoom, syntax highlighting, file management
· Web Project Import: Supports importing existing Web project ZIP packages

Performance Optimization:

· Startup speed optimization
· Memory usage optimization
· Widget refresh mechanism optimization

---

## Feature List

### Implemented Features

| Module | Feature | Status | Description |
|--------|---------|--------|-------------|
| Launch Experience | SplashActivity | Implemented | App splash screen, permission check |
| Launch Experience | Onboarding System | Implemented | First launch onboarding |
| Launch Experience | App Icon Shortcuts | Implemented | Long-press icon shortcut menu |
| Launch Experience | Permission Request Dialog | Implemented | Storage permission description |
| App Update | Auto Update Check | Implemented | Auto-check on startup |
| App Update | Force Update Mechanism | Implemented | Supports force update |
| App Update | Version Ignore | Implemented | Can ignore versions |
| App Update | In-App Download | Implemented | Shows download progress |
| GitHub Acceleration | Mirror Site Management | Implemented | Standalone management page |
| GitHub Acceleration | Built-in Mirror Sites | Implemented | 13+ default mirrors |
| GitHub Acceleration | Custom Mirrors | Implemented | Manual mirror addition |
| GitHub Acceleration | Import/Export | Implemented | TXT format |
| GitHub Acceleration | CDN Acceleration | Implemented | Toggleable |
| Terminal (Termux) | Terminal Emulator | Implemented | Based on Termux adaptation |
| Terminal (Termux) | Linux Environment | Implemented | APT package management |
| Terminal (Termux) | Multi-Session Support | Implemented | Multiple sessions simultaneously |
| Terminal (Termux) | Multi-Window Support | Implemented | Android 7.0+ |
| Terminal (Termux) | Terminal Settings | Implemented | Font/color/shortcuts |
| Backend Integration | Python Backend | Implemented | Auto-starts Termux Python |
| Backend Integration | Node.js Backend | Implemented | Auto-starts Termux Node.js |
| Backend Integration | PHP Backend | Implemented | Auto-starts Termux PHP |
| Backend Integration | Binary Backend | Implemented | Select executable files |
| Backend Integration | Health Check | Implemented | /health endpoint |
| Backend Integration | Process Management | Implemented | Auto-start/stop |
| Plugin Engine | Dynamic DEX Loading | Implemented | DexClassLoader |
| Plugin Engine | Resource Isolation | Implemented | Independent Context |
| Plugin Engine | Lifecycle Management | Implemented | Complete lifecycle |
| Plugin Engine | WebView Support | Implemented | HTML/CSS/JS |
| Plugin Engine | JS Bridge API | Implemented | 140+ APIs |
| Plugin Engine | Network Request API | Implemented | HTTP GET/POST/PUT/DELETE |
| Plugin Engine | File System API | Implemented | Read/write/delete/copy/move |
| Plugin Engine | Storage API | Implemented | KV + JSON + batch operations |
| Plugin Engine | Data Persistence | Implemented | Independent data/ directory |
| Plugin Management | Import Plugins | Implemented | TPK file import |
| Plugin Management | Export Plugins | Implemented | ZIP package export |
| Plugin Management | Batch Import | Implemented | Multiple TPK files |
| Plugin Management | Plugin Set Import | Implemented | ZIP batch import |
| Plugin Management | Category Management | Implemented | Add/delete/change category (individual/batch) |
| Plugin Management | Signature Verification | Implemented | SHA-256 verification |
| Plugin Management | Plugin Notice | Implemented | notice field display |
| Plugin Repository | GitHub Integration | Implemented | Official repository |
| Plugin Permissions | Permission Declaration | Implemented | plugin.json declaration |
| Plugin Permissions | Permission Check | Implemented | Pre-start check |
| Plugin Permissions | Permission Request | Implemented | Grouped requests |
| Plugin Permissions | Permission Status | Implemented | Visual status |
| Plugin Permissions | Permanent Authorization | Implemented | State persistence |
| Plugin Permissions | Material 3 Popup | Implemented | Unified style |
| Plugin Permissions | Shizuku/Dhizuku Support | Implemented | Official API (`Shizuku`/`Dhizuku` detection + authorization, results real-time refresh) |
| Documentation System | Documentation Center | Implemented | Centralized document management |
| Development Tools | Native Plugin Wizard | Implemented | Kotlin/Java plugins |
| Development Tools | Web Plugin Wizard | Implemented | Web plugin creation |
| Development Tools | Code Editor | Implemented | Sora Editor engine |
| Development Tools | Syntax Highlighting | Implemented | 30+ languages |
| Development Tools | Editor Themes | Implemented | 28+ themes |
| Development Tools | Template Export | Implemented | Export template docs |
| UI Personalization | Color Configuration | Implemented | 38+ colors adjustable |
| UI Personalization | Color Picker | Implemented | Visual color picker (hue bar + saturation/brightness panel) + RGB/Alpha |
| UI Personalization | Gradient Background | Implemented | Single/multi-color gradient + direction settings, toggleable, default light blue |
| UI Personalization | Corner Radius Configuration | Implemented | 7 corner radius options |
| UI Personalization | Size Configuration | Implemented | Button/spacing/icon |
| UI Personalization | Font Configuration | Implemented | Font size/boldness |
| UI Personalization | Import/Export | Implemented | Configuration backup |
| Desktop Widgets | 3x3 Widget | Implemented | Shows 9 plugins |
| Desktop Widgets | 1x1 Shortcut | Implemented | Single plugin shortcut |

---

## Technology Stack Details

| Technology | Version | Purpose |
|-----------|---------|---------|
| Kotlin | 2.1.0 | Primary development language |
| Jetpack Compose | 2024.09.00 | Declarative UI framework |
| Compose Material 3 | 1.3.0 | Material 3 components |
| Compose Navigation | 2.7.7 | Page navigation |
| Android SDK | API 35 | Android framework |
| Kotlin Coroutines | 1.7.3 | Asynchronous programming |
| OkHttp | 4.12.0 | HTTP client |
| Retrofit | 2.11.0 | REST API |
| Gson | 2.11.0 | JSON parsing |
| CommonMark | 0.22.0 | Markdown rendering |
| Sora Editor | 0.24.4 | Code editor |
| Sora Editor TextMate | 0.24.4 | Syntax highlighting |
| MultiDex | 2.0.1 | Multi-DEX support |
| NDK | 29.0.14033849 | C/C++ native support |

---

## Quick Start

### Install App

1. Download the latest APK from Releases
2. Enable "Install unknown apps" on the device
3. Install APK

### First Launch

1. App shows storage permission description dialog on startup
2. Click "Go to Authorize" to grant storage permissions
3. Onboarding page displays on first launch
4. Click "Start Experiencing" after reading the onboarding content

### Use Terminal

1. Click the "Dev" tab at the bottom
2. Click "Open Terminal" to launch terminal
3. Linux environment auto-installs on first launch (about 30-60 seconds)

Common terminal commands:

```bash
# Update package sources
pkg update

# Install Python
pkg install python

# Install git
pkg install git

# Install Node.js
pkg install nodejs
```

### Install Plugins

Method 1: Install from repository

1. Click the "Repo" tab at the bottom
2. Browse available plugins
3. Click the "Install" button

Method 2: Local import

1. Transfer .tpk file to phone
2. Click "Manage" > "Plugin Management" at the bottom
3. Click "Import" and select file

### Create Plugin

1. Click the "Dev" tab at the bottom
2. Click "Create Plugin"
3. Select frontend type (Native UI / Pure WebView / WebView + Backend)
4. If selecting WebView + Backend, select backend language (Python/Node.js/PHP/binary)
5. Fill in plugin information according to the wizard
6. Click "Finish" to generate project files

### Use Code Editor

1. Enter code editor in the plugin creation wizard
2. Left sidebar shows project file list
3. Click file to edit
4. Supports syntax highlighting for 30+ programming languages
5. Click palette icon to switch editor theme
6. Supports undo/redo functionality
7. Supports add/delete files
8. Click "Finish" to save all changes

### Plugin Data Storage

Web Plugin (JavaScript):

```javascript
// Store data
UINPlugin.setStorage('username', 'John');
UINPlugin.setStorageInt('score', 100);
UINPlugin.setStorageJSON('config', JSON.stringify({theme: 'dark'}));

// Read data
const name = UINPlugin.getStorage('username');
const score = UINPlugin.getStorageInt('score', 0);
const config = JSON.parse(UINPlugin.getStorageJSON('config'));

// File operations
UINPlugin.writeFile('notes.txt', 'Hello World');
const content = UINPlugin.readFile('notes.txt');

// View storage statistics
const stats = JSON.parse(UINPlugin.getStorageStats());
console.log('KV count:', stats.kvCount);
console.log('File count:', stats.fileCount);
```

Native Plugin (Kotlin):

```kotlin
val pctx = PluginContext(context, pluginDir)
pctx.putString("key", "value")
val value = pctx.getString("key")
pctx.writeFile("data.txt", "content")
```

---

## FAQ

Q: How to install plugins?
A: Three methods: install directly from the "Repository" page, import .tpk files, batch import or plugin set import.

Q: What is the difference between Web plugins and native plugins?
A: Web plugins use HTML/CSS/JS development, no compilation needed, changes take effect immediately; native plugins use Java development, better performance, but require compilation.

Q: How to develop your own plugin?
A: Click "Dev" > "Create Plugin" at the bottom, select the type and follow the wizard.

Q: How to customize UI colors and corner radius?
A: In "Manage" > "UI Personalization", you can customize 38+ colors and 7 corner radius sizes.

Q: Where is plugin data stored?
A: Each plugin's data is stored in /storage/emulated/0/UIN_Tool/plugins/{pluginId}/data/ directory, KV data is stored in SharedPreferences.

Q: Will updating a plugin lose data?
A: No. The data/ directory is automatically preserved when updating plugins, user data is not lost.

Q: Will permission status be persisted?
A: Yes. After authorization, permission status is permanently saved; no duplicate popups on next opening.

Q: What APIs do Web plugins support?
A: Supports 140+ APIs, covering device info, sensors, location, network, file system, storage, permissions, UI, clipboard, vibration, notifications, system operations, etc.

Q: What languages does the code editor support?
A: Supports 30+ programming languages including Java, Kotlin, Python, JavaScript, TypeScript, HTML, CSS, JSON, XML, Markdown, Shell, SQL, Go, Rust, PHP, Ruby, Swift, Dart, Lua, Scala, Perl, Haskell, Elixir, Erlang, Clojure, Groovy, Dockerfile, Makefile, INI, Properties, TOML, YAML, etc.

Q: How to export plugin data?
A: Web plugins can use UINPlugin.exportData() to export all data as JSON format.

Q: How to use the terminal feature?
A: Click "Dev" > "Open Terminal" at the bottom; the Linux environment auto-installs on first use.

---

## Open Source License

This project uses the MIT License.

---

## Contributors

| Contributor | Role | Contribution |
|------------|------|--------------|
| UIN Team | Core Development | Architecture design, core features |
| Yi Zhi Dian Bi | Feature Development | 1x1 desktop widget functionality |
| Termux Team | Upstream Project | Terminal emulator core engine |

---

## Contact Information

| Channel | Contact Info |
|---------|-------------|
| GitHub | https://github.com/Undefined-Invalid-Null/UIN-Tool |
| Email | undefinedinvalidnull@outlook.com |
| Plugin Repository | https://github.com/UIN-Tool-Plugins |
| QQ Group | 511875883 |

---

© 2026 UIN Team. All Rights Reserved.
