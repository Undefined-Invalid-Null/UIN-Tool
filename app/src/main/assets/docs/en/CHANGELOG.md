# UIN Tool Changelog

This document records all important version updates and feature changes of UIN Tool.

---

## Version Naming Rules

- **Major version**: Major architectural changes or incompatible API modifications
- **Minor version**: New features, backward compatible
- **Patch version**: Bug fixes, backward compatible

---

## [5.6.0] - 2026-08-28

> This version focuses on **UI system refinement, neumorphism style addition, and language switching functionality**: refines the UI personalization system, adds neumorphism (Neumorphism) style, adds language switching functionality (supports Follow System/Simplified Chinese/English). Total of 92 files modified, 6240 lines added, 3850 lines deleted.

### UI System Refinement

- Refines unified component system: All pages uniformly use UnifiedButton, UnifiedCard, UnifiedTextField, UnifiedSwitch, UnifiedIconButton, UnifiedSlider, UnifiedChip, and other components
- Fixes multiple issues with un-unified original Material3 components (CodeEditorScreen, BackupScreen, BackendSettingsScreen, WidgetConfigScreen, UIConfigPropertyPanel)
- Fixes OnboardingScreen hardcoded colors to theme colors
- Unifies neumorphism shadow effects (neuRaised/neuInset) using BlurMaskFilter implementation
- UnifiedButton supports automatic text wrapping for long text, no longer truncates
- DropdownPropertyRow uses native Row+Text instead of disabled UnifiedTextField, fixes dropdown text gray issue
- UnifiedDialogs refactoring: Popup + AnimatedVisibility for pure fade animation, dialogBackgroundOf() supports gradient backgrounds
- UIComponents bridging layer: All old UIComponents methods delegate to Unified* components
- ManageScreen, PluginManageScreen, ToolsScreen, DevToolsScreen, PermissionManagerScreen, and other pages fully use Unified components

### Neumorphism Style

- Adds neumorphism (Neumorphism) style presets, supports customizable shadow strength, inner shadow, and glow effects
- New file: NeuModifiers.kt (381 lines) - BlurMaskFilter rendering engine, neuRaised/neuInset/neuPressable/neuGlow modifiers
- New file: NeuEffects.kt - Custom ripple and glow effects
- New file: NeumorphicComponents.kt - NeuCard/NeuButton/NeuInput/NeuSwitch and other components
- New file: SpecialComponents.kt - Special components
- New file: UIConfigPreviewScreen.kt - Neumorphism preview page (35 control displays)
- New file: UIConfigNeumorphismPreview.kt - Neumorphism standalone preview
- New file: UIConfigModels.kt - ConfigState data model, loadConfigFromUIConfig/saveConfigToUIConfig serialization
- New file: StylePresets.kt - Default/Neumorphism presets, toConfigState() extension
- New file: StyleManager.kt - Style switching, caching, reset
- Neumorphism component support: cards, buttons, input fields, switches, chips, sliders, etc.
- Neumorphism animation speed adjustable (fast/medium/slow)

### Language Switching

- Adds language switching functionality (Content Properties > Text > Language)
- Supports: Follow System, Simplified Chinese, English
- UinApplication.applyLocale() sets Locale and updates Configuration
- Language settings persisted, takes effect after restart
- UI personalization page all labels support multilingual (Str.get() localization)
- UIConfigSidebar 30+ tree node labels localized

### Other Improvements

- Translucent effect (originally glass effect) renamed, supports transparency adjustment slider
- Fixes translucent effect transparency adjustment not taking effect (glassBackground now correctly applies alpha override)
- Fixes documentation center page top spacing issue (DocBrowserScreen contentPadding)
- Fixes 15 Chinese/English string misalignment issues (neumorphism/animation strings moved to correct positions)
- Ripple effect toggle note: This toggle controls Material3 native ripple; neumorphism components use independent shadow transition animations as click feedback, the two do not affect each other
- New file: SharedSupervisor.kt - Shared Supervisor process management
- build.gradle: versionCode 22, versionName "5.6.0"
- AndroidManifest.xml: Multiple activity attribute updates
- Multiple .tpk test plugin packages updated

---

## [5.5.0] - 2026-08-22

> This version focuses on **native plugin compatibility fixes, real Termux architecture upgrade, and development wizard completion**: fixes native plugin crashes caused by interface default method bytecode incompatibility (`-Xjvm-default=all` + rebuild host-sdk.jar + repackage plugin.dex), CUI plugin proot container script path error, crash log page not jumping, MarkdownRenderer `appendReplacement` crash, DocViewerScreen crash, etc.; adds clipboard pseudo-permission declaration (`READ_CLIPBOARD`/`WRITE_CLIPBOARD`); **development wizard completes all plugin fields** and updates built-in documentation. Adds **permission prompt before opening plugin**, **real Termux shared Supervisor** (single resident process manages all plugin backends, cold startup saves ~5s initialization overhead), **startup environment auto-installation** (bootstrap + Alpine background pre-installation), **4 static desktop shortcuts** replacing dynamic plugin shortcuts.

### Native Plugin Crash Fixes

- Native plugin `AbstractMethodError` crash: Plugin `plugin.dex` compiled against old host-sdk.jar (without `onHostEvent` and other default methods), Kotlin default methods exist as abstract methods at runtime → calling crash. Fix: build.gradle enables `-Xjvm-default=all` (real JVM default methods), rebuild host-sdk.jar (Java mirror, default method signatures match runtime), repackage plugin.dex per template, host adds reflection guard
- CUI plugin proot container `scripts/script.py` not found: proot resets working directory to `/root`, startup command changed to `cd /plugins/<id> && <startup command>`

### Clipboard Permissions (Pseudo-permissions)

- Adds pseudo-permissions `READ_CLIPBOARD` / `WRITE_CLIPBOARD`: only need to be declared in plugin.json `permissions` to take effect, no runtime authorization needed
- Development wizard "Permissions" popup already includes these two pseudo-permissions; permission management page only does "declaration + blocking" control for pseudo-permissions

### Development Wizard Field Completion

- Backend: `backendStartCommand`, `backendTimeout`, `backendHealthCheck`
- External content reception (openWith): toggle + receiver name + MIME type + text/link/file reception toggle
- Permissions: popup multi-select covers all common permissions (including pseudo-permissions)
- Plugin notice (notice) retained
- **Field streamlining (v5.5.0 later)**: No longer provides "Resource limits (max memory/max CPU time/max concurrent tasks), dependencies, API level, backend keep-alive" input fields (not written to wizard-generated plugin.json, see 12.3); **fixes code editor always showing default MainPlugin.java** — entry file regenerated synchronously after main class name change

### Permission Prompt Before Opening Plugin

- **Web plugins** (with/without backend): Before opening, popup lists **ungranted** permissions, provides "OK" / "Don't Prompt Again" / "Manage Permissions" — "Manage Permissions" jumps directly to that plugin's permission management page
- **Native plugins**: Each opening prompts required (declared) permissions, provides "OK" / "Don't Show Again" — **popup first then open plugin** (native permissions enforced by system, application layer does not intercept/block)
- Permission management page only manages **Web plugin** permissions (native/CUI plugins no longer displayed)
- Permission Chinese display name improvement: e.g., `android.permission.READ_EXTERNAL_STORAGE` → Read External Storage, `WRITE_EXTERNAL_STORAGE` → Write External Storage
- Permission prompt popup changed to **host unified style** (`UnifiedDialog`) rendering, visually consistent with other system popups
- Permission management page removes **refresh button**, list auto-refreshes after granting/revoking

### Keep Session Single Window

- "Keep Session on Close" toggle **enabled by default**: default single-window deduplication (shared port mode + Web plugins, same plugin **keeps only one background window** — repeated openings bring existing window to the foreground, no more multi-instance, multi-task window only shows one)
- User explicitly disables in "Development Tools", Web / CUI plugins create independent instances each time (supporting multi-instance)

### Real Termux Backend Auto-Reclamation

- Real Termux processes launched by com.termux's own UID, host cross-app sandbox cannot directly kill; now changed to **shared supervisor unified management**: supervisor checks each plugin process liveness via `kill -0 $pid` each round, cleans up if dead; idle reclamation judges via `idle/<key>.start` startup timestamp + `idle/<key>` timeout minutes (0=infinite), recursively SIGKILL kills backend process tree on timeout, **does not depend on plugin implementing `/stop`**
- Idle reclamation duration supports **custom arbitrary minutes** (settings page "Custom (minutes)" input box), also selectable preset 3 / 5 / 10 / 15 minutes (default 5) or "**Unlimited**" (never auto-reclaimed, only stops when actively stopped; does not write idle files)
- Host no longer polls to determine idleness, only does port detection and state cleanup; supervisor handles actual process killing
- Combined with "Backend Runtime Settings" idle reclamation timeout, after user exits plugin for set duration, backend is auto-killed without manually entering Termux to run commands

### Permission Button Text Shortened

- Permission management page "Grant/Revoke All Permissions" button changed to short text "**Grant All**" / "**Revoke All**" (en: `Grant All` / `Revoke All`), layout `weight(1f)` ensures complete display without truncation

### Plugin Templates Refined by Type

- `README.md.tmpl` rewritten to **typed rendering**: generates corresponding directory tree, development guide, packaging steps, and packaging file description based on plugin type (native / Web / Web+backend / CUI), fixes `{{MAIN_CLASS_PATH}}`/`{{WEB_SECTION}}`/`{{DEVELOPMENT_GUIDE}}` placeholders and `{{PLUGIN_ID}` typo that were never passed in original template
- Web template variable adds `PLUGIN_DESCRIPTION`, blank homepage renders with plugin description

### Documentation and Compatibility

- `permissions` / `dependencies` / MIME and other list fields **simultaneously compatible with comma-separated strings and JSON arrays** (wizard reads back plugin.json also reuses compatible parsing)
- Test plugin `plugin.json` streamlined: removes `signature`/`updateUrl`/`notice`/`backendRuntime`/`backendPort`/`backendEntry`/`backendPreCommand`/`backendMaxRetries`/`backendLogLevel`/`backendArgs` and other unnecessary or legacy fields, only retains actually effective fields; `web_plugin_template` changed to pure frontend (no `plugin.dex`/`src/`)
- Built-in documentation (README / Help / CHANGELOG) updated; version number upgraded to **5.5.0 (Build 21)**

### Real Termux Shared Supervisor

- Real Termux (proot or native mode) changed to **single resident shared supervisor**: container/session initialized only once, all plugin backends run as supervisor child processes, subsequent plugin startup saves proot initialization overhead (cold startup about 5s)
- Built-in Termux (alpine, about 2s) remains unchanged (each plugin has its own proot)
- Communication protocol (control directory `<plugins_root>/.uin/`): `cmd/<key>.cmd` (startup command), `pid/<key>` (backend PID), `stop/<key>` (stop request), `idle/<key>` (idle minutes, 0=infinite), `idle/<key>.start` (startup timestamp), `alive` (supervisor alive marker), `host_alive` (host heartbeat, touch every 30s, supervisor auto-exits on 300s timeout), `shutdown` (exit marker), `keep_alive` (background keep-alive marker)
- proot startup: `proot-distro login <container> --bind '<plugins_root>:/plugins' -- sh -lc 'sh /plugins/.uin/supervisor.sh /plugins'`; native: `sh '<plugins_root>/.uin/supervisor.sh' '<plugins_root>'`
- Idle reclamation: supervisor independently times out and recursively kills process trees per plugin based on `idle/<key>.start` startup timestamps; `kill -0 $pid` detects process liveness
- Supervisor stays resident during host lifetime (even after all backends reclaimed); host writes `shutdown` marker on exit, supervisor auto-exits (container exits accordingly)
- Software preheats supervisor (`prewarm`) in background on startup, also triggered when saving backend settings
- Backend settings page adds "Shared Scheduler" status card (RUN_COMMAND permission + supervisor alive status)
- Performance: removes probeRealTermux (saves 1.5~4s), polling interval 200ms, host_alive 300s timeout; warm startup ~0.5s, warm restart ~2s, cold startup ~4s

### Background Keep-Alive

- Adds "Background Keep-Alive" toggle (backend settings page → real Termux section)
- When enabled: writes `keep_alive` marker, supervisor no longer exits when host process is killed (only exits on explicit `shutdown`); combined with **battery optimization exemption** (`ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`) + notification bar keep-alive + **Shizuku/Dhizuku** permissions to maintain background survival
- Displays current Shizuku/Dhizuku permission status (available/unavailable)

### Crash Fixes and Experience Optimization

- Fixes MarkdownRenderer `appendReplacement` crash: replacement string containing `$` treated as group reference causing `IllegalArgumentException`, all three `appendReplacement` calls changed to use `Matcher.quoteReplacement()` escaping
- Fixes DocViewerScreen crash: `MarkdownRenderer.toHtml()` exception uncaught causing entire Activity crash, adds try-catch to display error information
- Fixes DocViewerScreen background color inconsistent with host: CSS body background changed from hardcoded `#f5f5f5` to `transparent`, WebView sets background color via `UIConfig.getBackgroundColor()`

### Startup Environment Auto-Installation

- Software auto-detects and installs Termux bootstrap + Alpine container in background on startup (built-in mode only), no manual operation needed
- Bootstrap installation immediately unblocks terminal (`_isEnvironmentInstalling = false`), terminal black screen issue fixed
- Alpine installation fully asynchronous, does not block terminal creation; Toast prompt during installation
- `ensureAlpine` adds `_isAlpineInstalling` lock, prevents concurrent restore causing "container is busy"
- After installing bootstrap, auto-writes environment variable file (`writeEnvironmentToFile`), ensures terminal bash has correct environment

### Static Desktop Shortcuts

- Dynamic plugin shortcuts replaced with **4 commonly used page static shortcuts**: Docs (DocBrowserActivity), Terminal (TermuxActivity), Backend Settings (BackendSettingsActivity), UI Personalization (UIConfigActivity)
- Removes dynamic shortcut creation/refresh/deletion logic in `PluginManager`, no longer exceeds limit due to frequent refreshes

---

## [5.4.0] - 2026-08-10

> This version is a **proactive capability extension**: adds **plugin receiving external content** (system "Share / Open with other apps" → relay page selects plugin) and **plugin multi-instance** (same plugin runs multiple independent instances simultaneously), and adds relay page search and plugin management page "Add Desktop Shortcut" entry.

### Plugin Receives External Content (Intent Relay, openWith)

- Adds **outbound open channel**: system/other apps "Share" or "Open with other apps" **text, links, files** can be sent to UIN Tool; relay page lists all plugins declaring `openWith` that match, user selects which plugin to handle
- `plugin.json` adds **`openWith`** field: `enabled` / `label` (relay page display name) / `mimeTypes` (supported MIME, supports `text/*`, `*/*` wildcards) / `acceptText` / `acceptUrl` / `acceptFile` (individual toggles for text, links, files)
- Supports **single file, multi-file (`SEND_MULTIPLE`)** reception, files auto-copied into plugin `<pluginDir>/.incoming/`, readable by plugin backend (proot container mounts `/plugins/<id>/.incoming`)
- **Web plugins** read via: host injects `window.UINOpenData` / `window.getOpenData()`, can also use `UINPlugin.getOpenData()` (returns JSON string `{}`, contains `kind`/`type`/`mime`/`url`/`text`/`uri`/`name`/`filePath`/`files` fields)
- **Native plugins** read via: `onHostEvent("host.open", bundle)` carries `instanceId` / `openDataJson`
- Auto-opens when only 1 plugin matches; no match shows relay page prompt; relay page supports **search** (filter by display name / plugin ID / description)
- Plugins declaring `openWith` appear in system share panel as "UIN Tool" entry (`ACTION_SEND` / `SEND_MULTIPLE` / `VIEW` + `text/*`, `*/*`, generic MIME)

### Plugin Multi-Instance (Multi-Instance)

- **Web / CUI plugins support multi-instance by default**: each opening starts new instance, page state, JS interface, backend mutually isolated
- **Native plugin multi-instance**: default single instance (reuses same instance on repeated opening), after enabling "Native Plugin Multi-Instance (Experimental)" in development tools page, each opening creates independent `PluginInterface` instance and View
- Each instance has globally unique **`instanceId`**, host isolates lifecycle callbacks, WebView cache, native plugin instances and backend by "instance key" (`pluginId:instanceId`)
- **Backend and instance isolation**: default multi-instance reuses same backend process (shared port); "Per-Instance Independent Backend Port" in "Development Tools" when enabled, each instance has its own backend process and port (`startBackendInstance` starts by instance key), mutually independent

### Relay Page Search

- "Open with..." relay page adds **search box**: filters candidate plugins by display name / plugin ID / description in real-time, convenient for quick positioning when there are many plugins

### Plugin Management Page Adds Shortcut

- Plugin management page list item "**Launch**" button changed to "**Add Desktop Shortcut**" (icon `+`), click to directly create desktop shortcut for that plugin, no need to enter details first

### Version Upgrade

- Version number upgraded to **5.4.0 (Build 20)**

---

## [5.3.0] - 2026-08-08

> This version is a **comprehensive refinement**: unified UI component system with glass effect implementation, enhanced color picker (visual color picker + dark mode), enhanced plugin category management, adds Shizuku / Dhizuku permission support, and verifies and fixes all UI personalization page features.

### New Global Gradient Background Effect

- Adds **global gradient background**: can be enabled/disabled in "UI Personalization" > "Effects" page, takes effect on **all pages**
- Supports **single-select (single-color gradient) / multi-select (multi-color gradient)** two modes, multi-select can choose 2-6 colors, supports add/delete/modify
- Supports **gradient direction setting**: can separately specify "**start direction**" and "**end direction**" (6 directions selectable)
- **Default single-color gradient (theme-adaptive)**: light `#FFC4D6DF` / dark `#FF4C4F51`, direction **bottom-right → top-left**; old "multi-color three-color default" auto-migrates, user custom configurations unaffected
- Configuration persists with save, **supports export/import** (includes `color`/`color_dark` fields) and "Restore Defaults"
- Gradient drawn on theme root layout, **top title bar follows gradient background**, auto-adapts with light/dark theme

### Glass Effect Transparency Increased

- Glass card/glass background transparency increased overall, glass texture more pronounced

### Unified UI Component System (Full Implementation)

- `Unified*` components become the **sole implementation source**: unified buttons/cards/input fields/text/switches/tags/icon buttons/list items/progress bars/dialog system, etc.
- `UIComponents` refactored into **thin delegation layer**, all screens (20+) migrated to directly use `Unified*` components, API fully compatible
- Unified cards (Glass)/input fields/dialogs support **glass effect**: semi-transparent background, no border, no shadow, follows theme

### Color Picker Enhancement

- Adds **visual color picker** (hue bar + saturation/brightness panel, click/drag to pick any color) and **hex input box**, real-time sync with RGB/Alpha
- Dialog color scheme **follows light/dark theme** (night mode), retains sliders and preset palette

### Plugin Management and Categories

- Plugin details page and export/delete action bar add "**Change Category**", supports individual/batch modification, can select existing categories or custom
- Category filter bar supports **horizontal swipe**; plugin item **long-press** shows details popup (info, file structure, plugin.json raw text), can directly **change category / uninstall**
- Checkboxes **only show in selection mode**, top bar adds selection mode toggle; **batch delete fix**: supports deleting all selected plugins at once (with quantity confirmation dialog)

### Plugin List Scrollbar (Removed)

- Tools page plugin list and plugin management page list **remove right-side vertical scrollbar**, list items **scroll as-is**, no animation displacement

### Page Slide Transition No Longer Fades Dark

- `slide_in/out_left/right.xml` all remove alpha fade-in/fade-out, old/new pages slide in/out **maintaining brightness throughout**, only retains displacement and scale

### Bottom Navigation Floating Style / Wizard Action Bar / Button Outline

- Bottom navigation changed to **floating style**: card background, rounded corners and shadow, covers content without clipping; clicking **press-to-scale**, selected icon enlarges, no ripple
- Development wizard "Next" action bar changed to **floating pure transparent**: removes fixed height/card background/shadow, tight to buttons; content area **full-height scrolls**
- Outline buttons (Outlined) add explicit 1dp theme outline, white/transparent background buttons have clear contours

### Dialog Background Fully Consistent with Main Background

- Dialogs (unified dialogs/loading/bottom/update/color picker/alert) background **same gradient as main page, fully opaque**, no longer shows through to components behind
- Original Material3 `AlertDialog` unified replaced with `UnifiedAlertDialog`; transparent text buttons add 1dp theme outline (`UnifiedDialogTextButton`)

### UI Personalization Page Optimization

- Top tab bar follows background color and removes bottom border line
- Gradient "mode / start / end direction" changed to **dropdown**, all gradient configuration merged into single card
- Code editor file list background follows main background
- **Feature verification & fixes**: ripple toggle actually works, status bar/navigation bar color customizable, dark primary (`primary_dark`) integrated into theme

### Permission Management Enhancement + Status Auto-Refresh

- Adds **Shizuku** and **Dhizuku** permission support (fully using official API)
- Permission checkmark status **auto-refreshes** (callback/authorization listener/page return trigger), no longer needs manual pull-to-refresh

### Code Structure Optimization

- Mirror constants, file utilities, size formatting converged to single implementation; Repository naming unified (`BackupRepository` → `IBackupRepository`)

### Version Upgrade

- Version number upgraded to **5.3.0 (Build 19)**

---

## [5.2.0] - 2026-08-06

### Packaging Logic Refinement (Package Everything)

- `JavaToDexCompiler.packageTpk` rewrite: no longer selects only specific directories by type for packaging, changed to recursively package entire project directory (`web/`, `scripts/`, `scripts/backend/server.py`, `start.sh`, `res/`, `src/`, and any resources all included in TPK), skips hidden files and `.tpk` output, avoids duplicate entries
- Explicitly adds `plugin.json`, `icon.png`, `README.md`, native placeholder/real `plugin.dex` (prioritizes recognizing real DEX's `dex\n` magic)
- Web plugins without `web/index.html` still write default page as fallback

### Update Logic Refinement

- Silent update (once daily): adds `KEY_LAST_UPDATE_CHECK` (epoch day) and `get/setLastUpdateCheckDay`, `get/setLastChangelog`, `MainContent` top `LaunchedEffect` triggers background check once daily, shows update dialog when new version exists and not ignored
- Adds shared component `ui/components/UpdateContent.kt` (`ReleaseChangelog` Markdown rendering + `UpdateDialog` popup + `UpdateContent` body), shared between Splash and management page "Check for Updates"
- Management page "Check for Updates" card changed from plain text ConfirmDialog (only shows first 200 characters, no Markdown) to full Markdown `UpdateDialog`
- Version update guide page: `isVersionUpdate` with changelog uses full-screen Markdown page (`VersionUpdateScreen`), shares `ReleaseChangelog` rendering with popup

### Built-in Termux Alpine Installation Speedup

- `ProotContainerManager.ensureAlpine()` removes network `pkg install proot-distro -y` (main cause of slow first install), now only does existence check; first install only has `proot-distro restore` (decompresses about 19MB rootfs) one-time overhead
- If proot-distro is indeed missing, only logs/status prompt, restore ends with clear error, no longer silently uses network

### Development Wizard Refinement

- Packaging no longer auto-exits: bottom button shows "Package" before packaging, changes to "Finish" after success, click to exit
- Create plugin configuration page completes all fields (minimum host version, API level, category, update URL, dependencies), adds permission multi-select (37 permissions chip)
- `plugin.json` editor popup uses new `JsonSyntaxHighlighter` (`VisualTransformation`) for JSON syntax highlighting
- Configuration field info icons changed to unified overview: info icons beside each field removed, only retains title row overview button (popup introduces all fields completely)

### Code Editor

- File tree `FileTreeItem` uses `combinedClickable`: long-press shows anchored menu (view properties / rename)
- Properties popup shows filename, type, line count, character count; rename popup validation (non-empty, no duplicates), cross `files`/`contents`/`currentFile` synchronized rename with success/failure prompt

### Plugin Management Page

- Click plugin card opens new scrollable details dialog (`PluginDetailDialog`):
  - **plugin.json fields**: ID, version, minimum host version, API level, name, author, description, category, UI type, entry, main class, update URL, plugin notice, dependencies, permissions, startup command
  - **File structure**: Plugin directory file tree (directories/files with sizes)
  - **plugin.json raw text**: Read from disk and formatted (fallback to `PluginInfo.toJson()` on failure)
  - Bottom shows total plugin size and file count, can directly run plugin

### Backend Settings Reorganization

- "Backend Runtime Settings" changed from popup to complete page (`BackendSettingsActivity` + `BackendSettingsScreen`, with back bar): implementation/environment/container/idle reclamation settings
- Real Termux section adds "Initialization Command" card, upper right copy icon one-click copy; command logic extracted to `BackendConfig.buildRealTermuxSetupCode()`, shared with plugin runtime guide
- Management page card order: Plugin Management → Permissions → Development Tools → Documentation → Backup → UI Personalization → Backend Settings → GitHub Acceleration → Widgets → Check for Updates
- Development page removes backend runtime settings entry and old `BackendSettingsDialog.kt`

---

## [5.1.0] - 2026-08-06

### Backend Runtime Architecture Refactoring (Core)

**Adds Global Backend Runtime Settings:**
- Adds "Backend Runtime Settings" (`BackendConfig` + development/management page settings popup), globally controls runtime environment for all backend plugins, persisted in `uin_backend_prefs`:
  - **Built-in Termux** (default): Uses app's built-in lightweight Termux, forced through Proot shared Alpine container (`alpine`)
  - **Real Termux** (`com.termux`): Launches external Termux via `RUN_COMMAND`, can optionally select Termux native or Proot container (container name configurable, default `alpine`)
- Adds **idle auto-reclamation**: Backend idle beyond configurable time (default 5 minutes, can set 3/5/10/15) auto-stops, active requests refresh the timer

**Backend Startup Command Unified (`backendStartCommand`):**
- Removes old language-based startup (python/node/php/... + `backendPort`/`backendEntry`/`backendPreCommand`), all unified to `backend = "other"` + `backendStartCommand` single path
- After plugin opens, host executes `sh -lc` to start script, environment variables (`$PORT`, `$PLUGIN_ID`, `$PLUGIN_DIR`, `$WORK_DIR`, etc.) inlined
- Legacy backends auto-migrate on loading (synthesize startup command in memory), no changes needed for published plugins
- **Removes pre-start command (`backendPreCommand`) popup flow**: deletes "Run Now/Later/Cancel" query dialog and `PreCommandResultReceiver`, `pre_cmd_done` marker no longer used

**Real Termux Support:**
- Adds `RealTermuxRuntime` wrapping `com.termux.app.RunCommandService`'s `RUN_COMMAND` intent, adds `com.termux.permission.RUN_COMMAND` permission declaration and `com.termux` package detection
- On startup failure, auto-detects and provides guide: `allow-external-apps=true`, `termux-setup-storage`, `proot-distro install`, RUN_COMMAND permission grant instructions
- Real Termux processes cannot be terminated by host, backend stop changed to calling agreed HTTP `/stop` endpoint for graceful shutdown

### CUI Terminal Startup Optimization

- Built-in Termux: Directly launches full-screen `TermuxActivity` in foreground (`EXTRA_SESSION_ACTION = SWITCH_TO_NEW_SESSION_AND_DONT_OPEN_ACTIVITY`), no longer depends on overlay permission
- Real Termux: Launches `com.termux` full-screen terminal after creating session via `RUN_COMMAND`
- Both paths changed to pure fade transition (`overridePendingTransition(R.anim.fade_in, 0)`), fixes system desktop visible during cross-fade gap

### Development Tools Integration

- "Runtime Logs" and "Developer Options" merged into standalone "Development Tools" page (`DevToolsActivity`/`DevToolsScreen`), entered from single management page menu
- Management page removes "Runtime Logs" and "Developer Options" entries, adds "Development Tools" and "Backend Runtime Settings" entries
- Auto-jumps to "Development Tools" page to display crash log after crash (original log page logic migrated to new page)

### Plugin Development Refinement

- web + backend plugin wizard no longer generates `web/script.js`, changed to generate inlined script `web/index.html` (`simple_index.html.tmpl`)
- Backend template changed to unified generation of `scripts/start.sh` (startup command) + `scripts/backend/server.py` (reads `$PORT` + `/health`, `/stop` endpoints), no longer generates different backend entries by language
- `plugin.json` editor dialog completes backend fields (`backend="other"`, `backendStartCommand`, `backendStartEntry`, `backendAutoStart`, `backendTimeout`, `backendHealthCheck`), `applyPluginJson` reads back synchronously
- Development page removes language-based backend selection dialog, "Web UI + Backend" unified enters wizard to fill startup command

---

## [5.0.0] - 2026-08-05

### Full Internationalization (i18n)

- All hardcoded Chinese text in the app migrated to string resources: default English (en) + complete Simplified Chinese (zh-rCN), removed Japanese resources
- Covers 2600+ string keys, involving all screens: main interface, plugin management, permissions, repository, logs, backup, mirrors, documentation/help, development wizard, code editor, desktop widgets, etc.

### Dynamic Theme Engine

- Adds JSON dynamic theme engine: `UINToolTheme` reads UIConfig color scheme and takes effect immediately, supports dark mode synchronization
- Plugin WebView injects `--uin-*` CSS variables, theme color synchronizes with app
- Corner radius, font size all changed to configuration-driven, covering all pages
- Management bottom navigation bar and system status bar color follows theme, fixes purple theme residue

### Bottom Navigation Refactoring

- Bottom navigation changed to self-drawn (Row + clickable(indication=null)), avoids material3 NavigationBar / LocalIndication version differences
- Top thin border drawn along rounded corners, indicator icon changed to terminal prompt `>_`
- Tab switching adds horizontal slide + fade-in transition animation (AnimatedContent)

### Pull-to-Refresh Unification

- Removes all page top-right refresh icons, unified to Material 3 PullToRefreshBox pull-to-refresh
- Empty list state also supports pull-to-refresh; indicator uses theme color and fixed center
- 8 refreshable pages show "last update time", time fades in and auto-fades out after 1 second

### UI Optimization & Interaction

- Plugin list add/remove animation (animateItem + key)
- Repository/plugin management loading changed to skeleton screen (breathing flash placeholder)
- Mirror management "add mirror" changed to bottom-right FAB
- Toast unified to Material Snackbar (global host + lifecycle-aware)
- Glass effects applied to all cards and dialogs (UI personalization toggle control)
- Global Activity switching changed to "**slide + fade-in/fade-out**: old screen slides out and fades out, new screen slides in and fades in (`slide_in/out_*` includes displacement and opacity animation)
- Page slide transition animation **adds scale effect**: old screen **shrinks** when sliding out (scale 1.0→0.9), new screen **enlarges** when sliding in (scale 0.9→1.0), pivot at view center, switching has more front-to-back depth (displacement + scale + opacity compound animation)
- Management page top bar unified to `ManageTopAppBar`, color follows theme and page background

### Other Optimizations

- Light/dark color palettes can be edited and used simultaneously, app loads theme on startup
- Splash restores transparent background icon, faster startup (700ms fade-in scale animation)
- Fixes management page back/save button click not working, mirror dialog purple background, etc.

---

## [4.5.0] - 2026-08-03

### Proot Container Runtime + Custom Backend

#### Proot Container Runtime (`backendRuntime: "proot"`)

- Plugin backend can run in a **shared Alpine container**, isolated from host environment
- Auto-initializes Termux environment on first use, restores Alpine container offline from `assets/alpine.tar.xz` via `proot-distro restore`
- `assets/alpine.tar.xz` is backup generated by `proot-distro backup alpine`, built-in pre-installed Python and other dependencies
- Can use `apk add` inside container to install dependencies, does not pollute host Termux environment
- Plugin directory auto-bound to `/plugins/<pluginId>` inside container, entry file directly visible in container
- `127.0.0.1:PORT` inside container communicates with host, backend API calls require no extra configuration
- Environment pipeline: Termux ready → Alpine ready → pre-start command → start backend

#### Pre-Start Command (`backendPreCommand`)

- Plugins can configure a pre-start command, executed in the Termux terminal (e.g., install dependencies, initialize data)
- On first open, popup to choose: "Run Now" / "Later" / "Cancel"
- After successful execution (exit 0) once, permanently skipped (`pre_cmd_done` marker, stored in `plugin_data_<id>`)
- On execution failure, automatically returns to plugin page and shows exit code and error message

#### Custom Backend Mode (`backend: "other"`)

- Host does not auto-start backend process; pre-start command launches service in terminal
- Backend readiness determined by TCP port polling (200ms), timeout relaxed to 90s+ for container cold startup
- Supports portless plugins (`backendPort: 0`), pre-command session alive means running

#### Backend Connection Speedup

- Three OkHttpClient instances add `.proxy(Proxy.NO_PROXY)`, prevents system proxy hijacking loopback traffic
- `waitForReady` removes 1s hardcoded delay, changed to 200ms TCP port detection + HTTP health check polling
- When stopping backend, terminates by process group `SIGKILL` (`Os.kill(-pid, SIGKILL)`), ensures proot child processes also exit

#### Other Fixes

- Fixes onboarding flash and re-popup after skipping: removes SplashActivity dual navigation paths, unified to Compose-driven, fixes permission dialog first-frame flash

#### Wizard & Documentation

- Plugin wizard supports "Backend Runtime Environment" (Termux native / Proot container) and "Pre-Start Command" configuration
- Backend selection adds "Custom (Manual Start)" type
- **CUI Terminal Plugin**: Create plugin adds "CUI Terminal (Command-Line Interface)" type, 4-step wizard auto-generates `scripts/script.py` example script and startup command configuration
- Changelog, help documentation, README updated

### Template Export Refactoring + Development Tools Optimization

#### Plugin Template Export Refactoring

- Export template changed to directly copy **7 packaged plugins** from `assets/test_plugins/` (cuitest / othertest / termux / allapi / storage / NativeTestPlugin / web_plugin_template) as importable ready-made templates
- Auto-generates `README.txt` on export, listing each template file's purpose and import usage
- Cleans up original scattered plugin templates in assets, unified by built-in packaged plugins

#### Export Flow Fix

- Fixes "Exporting..." button getting stuck: export changed to background thread execution, resets state on main thread after completion
- Toast display thread-safe: background thread calling Toast no longer crashes (auto-switches to main thread)

#### UI Optimization

- Create plugin related buttons changed to pure theme color (`PrimaryButton`), removes gradient style

#### Build Optimization

- Streamlines `proguard-rules.pro`: only retains important code that might be deleted by R8 — **shell plugin host placeholder implementation**, `@JavascriptInterface` methods, plugin JSON models, etc.; removes overly broad rules, reduces release package size

---

## [4.4.4] - 2026-08-02

### Plugin Dialog System Unification + Interaction Fixes

#### Plugin Dialog System Unification

- Plugin dialogs all changed to app's built-in Compose unified dialog components (`UnifiedDialog` / `UnifiedConfirmDialog` / `UnifiedInfoDialog`)
- Removes old `UnifiedViewDialog` custom popup implementation
- JS `alert` / `confirm` / confirm dialog / input dialog / special permission popups unified through the same dialog components

#### Dialog Queue Mechanism

- Multiple popup requests display in sequence, no longer overwrite each other
- Automatically shows the next popup after the previous one closes
- Popup requests support callback style: `showConfirmDialog(title, message, callbackId)`, `showPromptDialog(title, hint, callbackId)`

#### Interaction Fixes

- Fixes plugin page unable to scroll/click: dialog overlay hidden by default, only shows when popup is displayed
- Fixes confirm dialog not showing
- Fixes screenshot function unable to save (changed to view drawing capture method)
- Fixes screenshot silent failure when no storage permission

---

## [4.4.0] - 2026-07-28

### Major Update: Plugin Data Persistent Storage + Permission System Completion

#### Plugin Data Persistent Storage

- **Unified Data Storage System**:
  - SharedPreferences-based key-value storage
  - Supports String, Int, Long, Boolean, Float, JSON full types
  - Each plugin has independent storage space (`plugin_data_{pluginId}`)
  - Data version management, supports plugin upgrade data migration

- **Plugin Data Directory**:
  - `data/` directory: Plugin user data storage
  - `cache/` directory: Plugin cache file storage
  - Plugin update auto-preserves `data/` directory, user data not lost
  - Auto-cleans all data when uninstalling plugin

- **File System API (Web Plugin JavaScript)**:
  - `writeFile(fileName, content)`: Write file
  - `readFile(fileName)`: Read file
  - `deleteFile(fileName)`: Delete file
  - `fileExists(fileName)`: Check if file exists
  - `listFiles()`: List all files
  - `getFileSize(fileName)`: Get file size
  - `clearCache()`: Clear cache

- **KV Storage API (Web Plugin JavaScript)**:
  - `setStorage(key, value)` / `getStorage(key)`: String storage
  - `setStorageInt(key, value)` / `getStorageInt(key, default)`: Integer storage
  - `setStorageBool(key, value)` / `getStorageBool(key, default)`: Boolean storage
  - `setStorageFloat(key, value)` / `getStorageFloat(key, default)`: Float storage
  - `setStorageJSON(key, json)` / `getStorageJSON(key)`: JSON storage
  - `removeStorage(key)`: Delete key
  - `clearStorage()`: Clear all data
  - `containsStorageKey(key)`: Check key existence
  - `getAllStorage()`: Get all data
  - `getStorageKeys()`: Get all keys

- **Batch Operations API (Web Plugin JavaScript)**:
  - `setStorageBatch(jsonData)`: Batch write
  - `getStorageBatch(keys)`: Batch read

- **Data Statistics API (Web Plugin JavaScript)**:
  - `getStorageStats()`: Get storage statistics (KV count, file count, total size, cache size)
  - `getDataVersion()`: Get data version
  - `exportData()`: Export all data as JSON
  - `importData(jsonData)`: Import data from JSON

- **Native Plugin Storage API (Kotlin)**:
  - `PluginContext.putString/getString`: String storage
  - `PluginContext.putInt/getInt`: Integer storage
  - `PluginContext.putBoolean/getBoolean`: Boolean storage
  - `PluginContext.putJSON/getJSON`: JSON storage
  - `PluginContext.writeFile/readFile`: File read/write
  - `PluginContext.deletePluginFile`: Delete file
  - `PluginContext.listPluginFiles`: List files
  - `PluginContext.getPluginFileSize`: Get file size
  - `PluginContext.getStorageStats`: Get storage statistics
  - `PluginContext.getPermissionState/setPermissionState`: Permission state management

- **Data Migration**:
  - Old `web_plugin_` SharedPreferences data auto-migrates to new system
  - Auto-executes migration on first launch, transparent to users

---

#### Permission System Fully Completed

- **Permanent Authorization State**:
  - Plugin permission status persisted to SharedPreferences
  - State values: 0=unauthorized (show popup), 1=authorized (enter directly), 2=denied (enter directly)
  - One-time authorization, permanently effective, no repeat popups

- **Permission State Management**:
  - `PluginContext.getPermissionState()`: Read permission state
  - `PluginContext.setPermissionState(state)`: Write permission state
  - `PluginContext.shouldShowPermissionDialog()`: Check if popup needed
  - Supports clearing permission state (for debugging or re-authorization)

- **Permission Popup Optimization**:
  - Material Design 3 style popup
  - Displays all missing permissions and their descriptions
  - Regular and special permissions grouped display
  - Click cancel exits directly, does not enter plugin
  - Click authorize requests permission, enters plugin after granting

- **Permission Request Flow**:
  - State 1: Enter plugin directly
  - State 0 or 2: Check actual permissions, popup if missing
  - User clicks authorize → request permission → set state to 1 after granting
  - User clicks cancel → set state to 2, exit plugin
  - Some permissions denied → Toast prompt, set state to 1, no more popup

- **Special Permission Handling**:
  - Overlay, modify system settings, and other special permissions
  - Guide users to system settings to manually enable
  - Can still enter plugin after special permissions are denied (some features unavailable)

- **Permission Status Visualization**:
  - Plugin management page displays permission status
  - Permission management page can view each plugin's permission details
  - One-click authorize all permissions

---

#### Web Plugin API Major Expansion

Adds **140+ API** interfaces, covering the following categories:

| Category | API Count | Description |
|----------|-----------|-------------|
| Device Info | 16 | Model, version, screen, memory, CPU, build info, etc. |
| Sensors | 9 | Accelerometer, gyroscope, light, proximity, magnetic field, orientation, pressure, temperature, humidity |
| Location Services | 2 | Get location, reverse geocoding |
| Screen/Display | 5 | Brightness, auto brightness, display info, font scaling |
| System Settings | 7 | Airplane mode, Bluetooth, WiFi, mobile data, location, NFC, auto-rotate, do not disturb |
| Storage Info | 3 | Total capacity, available capacity, usage percentage |
| Network Data | 11 | Network info, WiFi info, signal strength, carrier, IP, speed, Ping |
| Battery | 5 | Level, health, voltage, temperature, technology |
| Audio | 5 | Volume, max volume, mute, headphone status |
| Time/Date | 5 | Current time, timezone, daylight saving |
| System Language | 4 | System language, country, region |
| App Management | 7 | App list, open app, app info |
| File Operations | 15 | Read/write/delete, copy/move, directory operations, file info |
| Network Requests | 5 | GET, POST, PUT, DELETE, download |
| Permissions | 3 | Check, request, batch request |
| UI | 4 | Loading, confirm dialog, input dialog |
| Clipboard | 3 | Copy, get, clear |
| Vibration | 2 | Vibrate, cancel |
| Notifications | 2 | Send, cancel |
| System Operations | 10 | Open various settings, fullscreen, keep awake, screenshot |
| Events | 2 | Send event, add listener |

---

#### Code Editor Enhancement

- **Syntax Highlighting Fix**: Fixes TextMate syntax loading issue
- **Theme Application Optimization**: Correctly applies theme when switching files
- **Performance Optimization**: Reduces unnecessary recomposition

---

#### Bug Fixes

- Fixes permission popup clicking cancel still entering plugin
- Fixes permission state persistence failure
- Fixes some permissions still repeatedly popup after being denied
- Fixes `PluginPermissionManager` method signature error
- Fixes `PluginJSInterface` missing `ping` and other APIs
- Fixes `setStorageBatch` method does not exist
- Fixes permission request result callback not properly forwarded

---

## [4.2.0] - 2026-07-24

### Major Update: Termux Backend Integration + Plugin Development Enhancement

#### Termux Backend Integration

- **Plugin Backend Support**: Web plugins can directly start Termux backend services
  - Supports Python, Node.js, PHP language backends
  - Supports binary executable files as backends
  - Backend services auto-start, transparent to users
  - Backend process lifecycle management (auto-stops when plugin closes)

- **Backend Communication Protocol**:
  - HTTP API communication (no WebSocket needed)
  - Backend must provide `/health` health check endpoint
  - Backend auto-allocates port (default 8000)

- **Backend Template**: `python_template.tpk` template
  - Uses Python built-in `http.server`, no need to install Flask
  - Compatible with Python 3.14+ (removes `cgi` module dependency)
  - Supports computation, logging, querying, system commands, and other APIs

#### Plugin System Enhancement

- **Plugin Notice Feature**:
  - Plugins can declare `notice` field in `plugin.json`
  - Notice popup auto-displays on first plugin open
  - Users can choose "Don't prompt again" or "Remind later"
  - Plugin management page can view complete notice

- **Plugin Creation Wizard Optimization**:
  - Unified "Create Plugin" entry, popup selects frontend type
  - Supports native UI, pure WebView, WebView + backend three modes
  - Backend selection: Python, Node.js, PHP, binary files
  - Web plugins auto-generate blank HTML/CSS/JS files
  - Binary backend supports directly selecting executable files

#### Development Tools Enhancement

- **Export Templates**: Adds `python_template.tpk` template
- **Code Editor**:
  - Both native and Web plugins support code editor
  - Import existing Web projects (ZIP) functionality

#### Backend Management

- **PluginBackendManager**: Unified backend process management
  - Process start/stop/status query
  - Output monitoring (stdout/stderr)
  - Health check (waits for service ready)
  - Port auto-allocation

---

### UI Unification and Optimization

- Plugin notice popup uses Compose `AlertDialog`
- White background, Material 3 button style
- Removes all Emoji
- Development page button colors unified

---

## [4.1.0] - 2026-07-17

### Major Update: Code Editor Upgrade (Sora Editor)

#### Code Editor Comprehensive Upgrade

- **Sora Editor Integration**: Uses professional Sora Editor engine
  - Based on `io.github.rosemoe:sora-editor` 0.24.4
  - Uses `AndroidView` for perfect embedding in Compose
  - Supports syntax highlighting for 30+ programming languages

- **TextMate Syntax Highlighting**:
  - Supports Java, Kotlin, Python, JavaScript, TypeScript
  - Supports HTML, CSS, JSON, XML, Markdown
  - Supports Shell, SQL, Go, Rust, PHP, Ruby, Swift
  - Supports Dart, Lua, Scala, Perl, Haskell, Elixir
  - Supports Erlang, Clojure, Groovy, Dockerfile, Makefile
  - Supports INI, Properties, TOML, YAML, and other 30+ languages

- **Theme System**: Built-in 28+ code editor themes
  - Dark themes: dark-plus, dracula, one-dark-pro, material-theme, etc.
  - Light themes: vitesse-light, github-light, solarized-light, etc.
  - One-click theme switching, takes effect in real-time

- **Editor Feature Enhancement**:
  - Line number display
  - Code folding
  - Bracket matching highlighting
  - Smart auto-indentation
  - Undo/Redo history
  - File tree management (sidebar)
  - File type icons

---

## [4.0.0] - 2026-07-14

### Major Refactoring: Kotlin + Jetpack Compose Comprehensive Upgrade

#### Technology Stack Comprehensive Upgrade

- **Kotlin Full Migration**: Core code migrated from Java to Kotlin
  - 134 Kotlin files, 39 Java files
  - Leverages Kotlin null safety, coroutines, extension functions, etc.
- **Jetpack Compose**: UI fully migrated to declarative Compose framework
  - Compose 2024.09.00 BOM
  - Material 3 design language
- **MVVM Architecture**: Introduces ViewModel + StateFlow reactive state management
- **Repository Pattern**: Data layer and business layer separation
- **Dependency Injection**: ServiceLocator unified service instance management

#### UI Comprehensive Upgrade

- **Material 3 Design System**: New visual style
- **Dark Mode Completion**: Complete dark/light theme switching
- **Glass Effect**: Frosted glass texture UI components
- **Complete Color Picker**: RGB + Alpha channel independent adjustment
- **38+ Color Configuration Items**: All colors customizable
- **7 Corner Radius Configurations**: Comprehensive UI shape control

#### Terminal Features (Based on Termux)

- **Built-in Termux Engine**: Complete Termux terminal emulator integration
- **Complete Linux Environment**: APT package manager, bash/zsh, etc.
- **Multi-Session Support**: Multiple terminal sessions simultaneously
- **Multi-Window Support**: Android 7.0+ multi-window/split-screen

#### Plugin System Enhancement

- **Plugin Permission System**: Plugin permission management based on Android permission model
- **Plugin Dependency Check**: Auto-checks and prompts for missing dependencies
- **UI Configuration Import/Export**: Supports configuration backup and sharing
- **Complete Backup System**: Backup plugins, configurations, UI themes

#### Development Tools Enhancement

- **Plugin Creation Wizard**: Visual step-by-step guidance for creating plugins
- **Built-in Code Editor**: Supports zoom, syntax highlighting, file management
- **Web Project Import**: Supports importing existing Web project ZIP packages

#### Performance Optimization

- Cold startup time optimized by 30%
- Compose recomposition optimization
- WebView cache pool management
- Memory usage optimization

---

## Upgrade Guide

### From v5.0.0 to v5.1.0

v5.1.0 refactored the backend runtime architecture, please note before upgrading:

1. **Backend runtime environment changed to global configuration**: Legacy fields in old plugins (`backendRuntime`/`backendPort`/`backendEntry`/`backendBinary`, etc.) are no longer used in the new startup workflow; they are auto-migrated to `backendStartCommand` on loading (completed in memory, not written back to plugin file), no plugin changes needed
2. **Pre-start command popup removed**: The old "Run Now/Later/Cancel" `backendPreCommand` popup flow has been deleted, `pre_cmd_done` marker no longer used
3. **Runtime environment switching**: If you need to use real Termux to run the backend, switch in "Backend Runtime Settings" on the "Dev"/"Manage" page, and follow the guide to enable `allow-external-apps`, execute `termux-setup-storage`, grant RUN_COMMAND permission
4. **Backend stop method changed**: Real Termux processes cannot be terminated by the host; backends need to implement the `/stop` endpoint for graceful shutdown
5. **New idle auto-reclamation**: Backend idle beyond set time (default 5 minutes) auto-stops

### From v4.4.0 to v4.4.4

v4.4.4 is a plugin dialog and interaction fix version, please note before upgrading:

1. **Dialog appearance changed**: Plugin dialogs unified to built-in Compose dialog components, style and interaction slightly adjusted
2. **Dialog queuing**: Multiple popup requests will display sequentially, no longer overwrite each other
3. **New APIs**: Adds `showConfirmDialog`, `showPromptDialog` callback-style popup APIs
4. **Compatibility**: Fully backward compatible, plugins do not need modification

### From v4.2.0 to v4.4.0

v4.4.0 is a data persistence and permission system completion version, please note before upgrading:

1. **Data auto-migration**: Old plugin data auto-migrates to new storage system, no manual operation needed
2. **Permission state reset**: Some plugin permission states may need re-authorization after upgrade
3. **API compatibility**: New APIs are fully backward compatible, old plugins do not need modification
4. **Storage location change**: Plugin data is now stored in the `data/` directory

### From v4.1.0 to v4.2.0

v4.2.0 is a Termux backend integration version, please note before upgrading:

1. **Termux environment**: Requires Termux terminal environment to be installed
2. **Python backend**: First use auto-installs FastAPI dependencies
3. **Plugin notice**: Old plugins can manually add `notice` field to `plugin.json`
4. **Compatibility**: Fully backward compatible

### From v3.x to v4.0.0

v4.0.0 is a major refactoring, please note before upgrading:

1. **UI completely rewritten**: New Compose-based UI
2. **Configuration incompatible**: Old UI configurations need to be reset
3. **Plugin compatible**: Plugin system remains backward compatible
4. **Terminal feature enhancement**: Complete terminal experience based on Termux

---

| Item | Info |
|------|------|
| Document Version | 5.5.0 |
| Last Updated | August 22, 2026 |
| Corresponding App Version | v5.5.0 (Build 21) |

---

© 2026 UIN Team. All Rights Reserved.
