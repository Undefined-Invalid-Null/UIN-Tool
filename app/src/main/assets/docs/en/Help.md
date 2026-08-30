# UIN Tool User Guide

## Version Information

| Item | Info |
|------|------|
| Document Version | 5.6.0 |
| Corresponding App Version | v5.6.0 (Build 22) |
| Last Updated | August 28, 2026 |

---

## 📑 Table of Contents

### I. App Overview
- [1.1 Core Features](#11-core-features)
- [1.2 Plugin Type Comparison](#12-plugin-type-comparison)

### II. First Launch
- [2.1 Startup Flow](#21-startup-flow)
- [2.2 Permission Requests](#22-permission-requests)
- [2.3 Onboarding](#23-onboarding)

### III. Interface Navigation
- [3.1 Bottom Navigation Bar](#31-bottom-navigation-bar)
- [3.2 Development Page](#32-development-page)
- [3.3 Tools Page](#33-tools-page)
- [3.4 Repository Page](#34-repository-page)
- [3.5 Management Page](#35-management-page)

### IV. App Shortcuts
- [4.1 Shortcut List](#41-shortcut-list)
- [4.2 Usage](#42-usage)
- [4.3 Dynamic Plugin Shortcuts](#43-dynamic-plugin-shortcuts)

### V. Terminal Features (Based on Termux)
- [5.1 Access Method](#51-access-method)
- [5.2 Terminal Features](#52-terminal-features)
- [5.3 Common Commands](#53-common-commands)
- [5.4 Terminal Settings](#54-terminal-settings)

### VI. Auto Update
- [6.1 Startup Auto Detection](#61-startup-auto-detection)
- [6.2 Force Update Mechanism](#62-force-update-mechanism)
- [6.3 Update Options](#63-update-options)

### VII. GitHub Acceleration
- [7.1 Access Method](#71-access-method)
- [7.2 Mirror Site Management](#72-mirror-site-management)
- [7.3 CDN Acceleration](#73-cdn-acceleration)
- [7.4 Save Settings](#74-save-settings)

### VIII. Documentation Center
- [8.1 Access Method](#81-access-method)
- [8.2 Available Documents](#82-available-documents)

### IX. Plugin Repository
- [9.1 Browse Plugins](#91-browse-plugins)
- [9.2 Search Plugins](#92-search-plugins)
- [9.3 Install Plugins](#93-install-plugins)

### X. Plugin Management
- [10.1 Plugin Format](#101-plugin-format)
- [10.2 plugin.json Configuration](#102-pluginjson-configuration)
- [10.3 Import Plugins](#103-import-plugins)
- [10.4 Export Plugins](#104-export-plugins)
- [10.5 Uninstall Plugins](#105-uninstall-plugins)
- [10.6 Plugin Category Filtering](#106-plugin-category-filtering)
- [10.7 Add Desktop Shortcut (v5.4.0)](#107-add-desktop-shortcutv540)

### XI. Plugin Development
- [11.1 Create Plugin](#111-create-plugin)
- [11.2 Wizard Step Description](#112-wizard-step-description)
- [11.3 Plugin Notice Feature](#113-plugin-notice-feature)
- [11.4 Web Plugin JavaScript API](#114-web-plugin-javascript-api)
- [11.5 Plugin Data Persistent Storage](#115-plugin-data-persistent-storage)

### XII. Plugin Runtime
- [12.1 Run Plugin](#121-run-plugin)
- [12.2 View Mode](#122-view-mode)
- [12.3 Search Plugins](#123-search-plugins)
- [12.4 Long Press Actions](#124-long-press-actions)
- [12.5 Backend Runtime Settings (Web + Backend / CUI Plugins)](#125-backend-runtime-settingsweb--backend--cui-plugins)
- [12.6 Plugin Receives External Content (openWith Relay)](#126-plugin-receives-external-contentopenwith-relayv540-new)
- [12.7 Plugin Multi-Instance](#127-plugin-multi-instancev540-new)

### XIII. Permission Management
- [13.1 Permission Description](#131-permission-description)
- [13.2 App Permission List](#132-app-permission-list)
- [13.3 Plugin Permission Management](#133-plugin-permission-management)
- [13.4 Permission Persistence](#134-permission-persistence)
- [13.5 Shizuku/Dhizuku Permissions](#135-shizukudhizuku-permissions)

### XIV. Backup & Restore
- [14.1 Create Backup](#141-create-backup)
- [14.2 Restore Backup](#142-restore-backup)
- [14.3 Manage Backup Files](#143-manage-backup-files)

### XV. Development Tools (Runtime Logs + Developer Options)
- [15.1 Entry Point](#151-entry-point)
- [15.2 Log Content](#152-log-content)
- [15.3 Log Location](#153-log-location)
- [15.4 Log Operations](#154-log-operations)
- [15.5 Developer Options](#155-developer-options)

### XVI. Desktop Widgets
- [16.1 3x3 List Widget](#161-3x3-list-widget)
- [16.2 1x1 Shortcut](#162-1x1-shortcut)

### XVII. UI Personalization
- [17.1 Color Configuration (38+ Color Items)](#171-color-configuration38-color-items)
- [17.2 Color Picker](#172-color-picker)
- [17.3 Shape Configuration](#173-shape-configuration)
- [17.4 Size Configuration](#174-size-configuration)
- [17.5 Font Configuration](#175-font-configuration)
- [17.6 Effects Configuration](#176-effects-configuration)
- [17.7 Icon Tint Toggle](#177-icon-tint-toggle)
- [17.8 Configuration Operations](#178-configuration-operations)

### XVIII. FAQ
- [18.1 FAQ (Q1-Q35)](#181-faqq1-q35)

### XIX. Contact Support
- [19.1 Contact Information](#191-contact-information)

---

## I. App Overview

UIN Tool is a lightweight plugin framework application that allows you to dynamically load and run third-party plugins. Version 5.6.0 focuses on **Neumorphism style, multilingual switching, and translucent effect control**: adds Neumorphism UI style providing soft concave-convex light-shadow effects for cards, buttons, and other components (enabled in the "Effects" page, mutually exclusive with glass effect); supports in-app language switching (Chinese/English, etc., no need to change system language); glass effect and neumorphism effect support transparency (alpha) adjustment (drag the slider for real-time preview). Version 5.5.0 focuses on **compatibility fixes, real Termux architecture upgrade, and development wizard completion**: fixes native plugin `AbstractMethodError` crash, CUI plugin proot container script path issue, MarkdownRenderer `appendReplacement` crash, DocViewerScreen crash, etc.; adds clipboard pseudo-permissions (`READ_CLIPBOARD`/`WRITE_CLIPBOARD`, effective just by declaration), completes development wizard fields (backend timeout/health check/openWith/permission multi-select, retains plugin notice), **pops up permission prompt dialog before opening plugin** (Web plugin shows ungranted permissions / native plugin shows required permissions, unified style dialog), removes refresh button on permission management page and changes button text to "Grant All"/"Revoke All", **"Keep Session on Close" enabled by default (single-window deduplication)**, users must explicitly disable to support multi-instance; **real Termux shared Supervisor** (single resident process manages all plugin backends, cold startup saves ~5s initialization overhead, warm startup ~0.5s); **startup environment auto-installation** (bootstrap + Alpine background pre-installation, terminal no longer black screen); **4 static desktop shortcuts** replace dynamic plugin shortcuts; also **streamlines wizard fields** (no longer provides max memory/max CPU time/max concurrent tasks, dependencies, API level, backend keep-alive input fields) and fixes code editor always showing default MainPlugin.java. Version 5.4.0 is a **proactive capability extension**: **adds plugin receiving external content** -- text, links, files shared or opened with other apps by the system/other apps can be sent to UIN Tool, the relay page lists all plugins declaring `openWith` that match the content for you to choose which plugin to handle (auto-opens when only 1 match, relay page supports real-time search; files are copied into the plugin `.incoming/` directory for backend reading, Web plugins use `window.getOpenData()` / `UINPlugin.getOpenData()` to read, native plugins receive via `onHostEvent("host.open", bundle)`); **adds plugin multi-instance** -- Web/CUI plugins support multi-instance by default (each opening is an independent instance, page/JS interface/backend do not interfere), native plugins are single instance by default, can enable "Native Plugin Multi-Instance (Experimental)" in the development tools page, each instance has an independent `instanceId`, backend supports shared port / independent port two multi-instance modes; also **changes the launch button on plugin management page list items to "Add Desktop Shortcut"** (`+` icon). Version 5.3.0 is a **comprehensive refinement**: **adds global gradient background** -- can be enabled/disabled in "UI Personalization" > "Effects" page, supports single-select (single-color gradient)/multi-select (multi-color gradient) two modes, can set **start/end direction** separately (6 directions selectable), default single-color gradient adapts by theme (light `#FFC4D6DF` / dark `#FF4C4F51`, bottom-right → top-left), takes effect on all pages and **top title bar follows gradient**; **glass transparency increased**, **plugin management page card style unified** (checkboxes only show in selection mode), **bottom navigation floating style** (completely follows card style: opaque card background + rounded corners + shadow, covers content, scrolling cards show from surrounding whitespace without clipping, press to compress and scale, selected icon enlarges, no ripple), **button outline improved** (white/transparent background outline buttons have clear contours); **dialog background follows main background** and maintains high transparency (0.95) ensuring text readability, also fixes plugin dialog background covering plugin page content; **fixes permission status auto-refresh** -- auto-updates checkmarks after granting, no manual pull-to-refresh needed; **unified UI component system** -- `Unified*` components become the sole implementation source (buttons/cards/input fields/text/switches/tags/dialogs, etc.), `UIComponents` refactored into a thin delegation layer, all screens migrated to use unified components, cards/input fields/dialogs support glass effect (semi-transparent background, no border, no shadow, follows theme); **enhanced color picker** -- visual color picker (hue bar + saturation/brightness panel, click/drag anywhere to pick any color), supports hex color input, retains RGB/Alpha sliders and preset palette, supports dark mode theme following; **enhanced plugin category management** -- plugin details page and export/delete action bar both...

### 1.1 Core Features

| Feature Module | Description |
|----------------|-------------|
| Plugin System | Supports native plugins (Java/Kotlin) and Web plugins (HTML/CSS/JS) |
| Data Persistence | Each plugin has an independent data/ directory, user data automatically preserved on update |
| Terminal Environment | Built-in complete Linux terminal based on Termux |
| Backend Integration | Web plugins can start backend services (unified startup command mode, built-in Termux / real Termux globally selectable) |
| Container Runtime | Proot shared Alpine container, backend and host environment isolated |
| Plugin Repository | Browse, search, and install plugins from the official GitHub repository |
| Development Tools | Plugin creation wizard, code editor (Sora Editor), template export |
| Permission Management | App permissions and independent plugin permission management, state persistence |
| Backup & Restore | Backup/restore all plugins and configurations |
| UI Personalization | 38+ color items, 7 corner radius options, complete color picker, global gradient background (adjustable direction), neumorphism style, translucent effect transparency control |
| Multilingual Support | In-app language switching (Chinese/English, etc.), no need to change system language |
| Documentation Center | Development docs, user guide, changelog, about information |
| Desktop Widgets | List widget (3x3) and 1x1 shortcut |
| External Content Reception | System share / open with other apps relay processing (openWith), can receive text / links / files |
| Plugin Multi-Instance | Run multiple independent instances of the same plugin simultaneously (Web/CUI default, native optional) |

### 1.2 Plugin Type Comparison

| Feature | Native Plugin | Web Plugin |
|---------|---------------|------------|
| Development Language | Kotlin/Java | HTML/CSS/JS |
| Performance | Best | Good |
| Hot Update | Requires recompilation | No compilation needed |
| Development Barrier | Higher | Lower |
| System API Access | Full access | Via JS interface (170+ APIs) |
| Data Storage | PluginContext API | UINPlugin Storage API |
| Backend Support | None | Unified startup command mode (built-in Termux / real Termux globally selectable) |

---

## II. First Launch

### 2.1 Startup Flow

User clicks icon → permission check → SplashActivity (splash screen) → check first launch

```
├── Yes → OnboardingActivity (onboarding) → MainActivity
└── No → MainActivity
```

### 2.2 Permission Requests

On first launch, the app requests storage permissions:

1. Displays permission description dialog
2. Click "Go to Authorize" to jump to system settings
3. Automatically returns to the app after granting permissions
4. If permissions are denied, the app will exit

### 2.3 Onboarding

On first install or version update, the onboarding page is automatically displayed:

| Page | Content |
|------|---------|
| Page 1 | Welcome message / version update notes |
| Page 2 | Plugin management features introduction |
| Page 3 | Plugin development tools introduction |
| Page 4 | Web plugin support introduction |
| Page 5 | Ready to go, start experiencing |

How to use:
- Click "Next" to turn the page
- Click "Skip" to go directly to the main interface
- On the last page, click "Start Experiencing"

---

## III. Interface Navigation

### 3.1 Bottom Navigation Bar

The bottom navigation has a **floating style**: style completely follows the card (opaque card background, rounded corners and shadow), covers content, scrolling cards show from surrounding transparent whitespace without clipping. Clicking has press-to-scale feedback, selected icon enlarges, no ripple.

| Tab | Page | Function |
|-----|------|----------|
| Dev | Dev | Terminal, plugin creation wizard, template export |
| Tools | Tools | Run installed plugins |
| Repo | Repo | Browse and install plugins |
| Manage | Manage | Plugin management, permissions, settings |

### 3.2 Development Page

| Function | Description |
|----------|-------------|
| Open Terminal | Launch Termux terminal environment |
| Terminal Settings | Configure terminal font, color scheme, keyboard, etc. |
| Create Plugin | Unified entry, select frontend type and fill in backend startup command |
| Export Template | Export built-in packaged plugin templates (with README.txt description) |

> 💡 **Tip**: When creating plugins, you can choose from four modes: "Native UI", "Pure WebView", "WebView + Backend", "CUI Terminal". The backend runtime environment (built-in Termux / real Termux) for WebView + Backend plugins is globally configured in "Backend Runtime Settings" on the "Manage" page.

### 3.3 Tools Page

| Function | Description |
|----------|-------------|
| Category Browsing | Browse plugins by category |
| Search Plugins | Search by name, ID, author, description |
| View Toggle | List view / grid view toggle |
| Run Plugin | Click plugin icon to run |
| Long Press Menu | Long press to show plugin details |

### 3.4 Repository Page

| Function | Description |
|----------|-------------|
| Browse Plugins | Browse all available plugins from the official GitHub repository |
| Search Plugins | Supports keyword search |
| Install Plugins | One-click download and install plugins |
| Open Plugin | Installed plugins can be opened directly |
| Mirror Acceleration | Auto-select fastest mirror for accelerated downloads |

### 3.5 Management Page

| Function | Description |
|----------|-------------|
| Plugin Management | Import, export, uninstall plugins |
| Permission Management | Manage app permissions and plugin permissions |
| Documentation Center | User guide, development docs, etc. |
| GitHub Acceleration | Configure mirror sites and CDN acceleration |
| Development Tools | Runtime log viewing (export/clear) + developer options (signature verification toggle, etc.) |
| Backend Runtime Settings | Globally switch built-in Termux / real Termux, idle timeout, etc. |
| Backup & Restore | Backup/restore plugins and configurations |
| UI Personalization | Customize theme colors and corner radius |
| Widget Configuration | Widget configuration and usage guide |
| Check for Updates | Check for new app versions |

---

## IV. App Shortcuts

Long-pressing the app icon brings up a shortcut menu (Android 7.1+) for quick access to commonly used features.

### 4.1 Shortcut List

| Shortcut | Function |
|----------|----------|
| Docs | Open documentation browser (DocBrowserActivity) |
| Terminal | Open built-in terminal (TermuxActivity) |
| Backend Settings | Open backend runtime settings page (BackendSettingsActivity) |
| UI Personalization | Open UI personalization settings page (UIConfigActivity) |

### 4.2 Usage

1. Long press the UIN Tool icon on the desktop
2. Select the desired function from the popup menu
3. The app automatically jumps to the corresponding page

### 4.3 Plugin Desktop Shortcuts

You can create desktop icon shortcuts for individual plugins via "Manage" > "Plugin Management" by clicking the **`+`** button on the right side of the plugin list item (see "10.7 Add Desktop Shortcut").

---

## V. Terminal Features (Based on Termux)

> 🔥 **New in v4.0.0**: UIN Tool includes a complete terminal environment.

### 5.1 Access Method

1. Click the "Dev" tab at the bottom
2. Click the "Open Terminal" button
3. The Linux environment is automatically installed on first use

### 5.2 Terminal Features

| Feature | Description |
|---------|-------------|
| Shell Support | bash, zsh, fish, and other mainstream shells |
| Package Manager | APT (Debian/Ubuntu software sources) |
| Development Tools | gcc, clang, make, git, etc. |
| Script Languages | Python, Node.js, Ruby, Perl, etc. |
| Text Editors | vim, nano, emacs, etc. |
| Network Tools | curl, wget, openssh, etc. |
| Multi-Session | Multiple terminal sessions running simultaneously |
| Multi-Window | Android 7.0+ multi-window support |
| Custom Shortcuts | Configurable hardware/software keyboard shortcuts |

### 5.3 Common Commands

```bash
# Update package sources
pkg update

# Install Python
pkg install python

# Install Node.js
pkg install nodejs

# Install git
pkg install git

# SSH to server
ssh user@hostname

# View storage directory
ls ~/storage/shared/
```

### 5.4 Terminal Settings

1. Click the "Terminal Settings" button
2. Configurable items:
   · Terminal font size and style
   · Color scheme
   · Keyboard shortcuts
   · Extra function keys

---

## VI. Auto Update

### 6.1 Startup Auto Detection

Each time the app starts, the system automatically checks for the latest version on GitHub.

### 6.2 Force Update Mechanism

When the Release Tag format is {version code}-{version name}-1, users are forced to update:

| Tag Format | Example | Force Update |
|---|---|---|
| x-y-z and z=1 | 2-1.0.1-1 | Yes |
| x-y-z and z=0 | 2-1.0.1-0 | No |
| x-y | 2-1.0.1 | No |

Force update characteristics:

· The "Skip Update" button is hidden in the dialog
· Users must choose "Auto Download" or "Manual Download"

### 6.3 Update Options

| Option | Description |
|---|---|
| Auto Download | Download APK within the app, install directly when complete |
| Manual Download | Open browser to GitHub Releases page |
| Skip Update | Close dialog (available for normal updates) |
| Ignore This Version | Record the ignored version, no longer prompt next time |

---

## VII. GitHub Acceleration

### 7.1 Access Method

1. Click the "Manage" tab at the bottom
2. Click the "GitHub Acceleration" card

### 7.2 Mirror Site Management

Built-in mirror sites:

| Mirror Site Name | URL |
|---|---|
| FastGit | https://hub.fastgit.xyz |
| GhProxy | https://ghproxy.net |
| Mirror GhProxy | https://mirror.ghproxy.com |
| Moeyy | https://github.moeyy.xyz |
| GitClone | https://gitclone.com |
| GhApi | https://gh.api.99988866.xyz |

Enable/disable mirror sites:

1. Check the mirror sites you want to enable in the mirror site list
2. The system will select the fastest available mirror

Add custom mirror sites:

1. Click the "Add" button
2. Fill in the mirror site name and URL
3. Click "Add" to save

Import/export mirror site list:

· Import: Click "Import" and select a TXT format file
· Export: Click "Export" to save the current list

Test availability:
Click the "Test Availability" button, and the system will test each mirror site one by one.

### 7.3 CDN Acceleration

| Status | Effect |
|---|---|
| Enabled | Use CDN proxy to accelerate downloads |
| Disabled | Use original GitHub address for downloads |

### 7.4 Save Settings

After configuration is complete, click the "Save Settings" button to save all configurations.

---

## VIII. Documentation Center

### 8.1 Access Method

1. Click the "Manage" tab at the bottom
2. Click the "Documentation Center" card

### 8.2 Available Documents

| Document | Content |
|---|---|
| User Guide | This document, usage guide |
| Development Docs | Plugin development guide, API reference |
| Changelog | Version update history |
| About | App information, version, credits |
| Contributors | Contributors list |

---

## IX. Plugin Repository

### 9.1 Browse Plugins

1. Click the "Repo" tab at the bottom
2. The system automatically loads available plugins
3. Each plugin card displays:
   · Plugin name
   · Plugin ID
   · Version number
   · File size
   · Update date

### 9.2 Search Plugins

1. Click the search box on the repository page
2. Enter keywords (supports name, ID, description, author)
3. Search results are displayed in real-time

### 9.3 Install Plugins

1. Find the plugin you want to install in the repository list
2. Click the "Install" button
3. The system automatically downloads and installs
4. Download progress is displayed in real-time
5. After installation, the button changes to "Open"

---

## X. Plugin Management

### 10.1 Plugin Format

Plugin files have the extension .tpk, which is essentially a ZIP archive.

Native plugin structure:

```
plugin.tpk
├── plugin.json      # Plugin configuration file
├── icon.png         # Plugin icon (recommended 128x128)
├── plugin.dex       # Compiled DEX file
├── src/             # Source directory
└── res/             # Resource files directory
```

Web plugin structure:

```
plugin.tpk
├── plugin.json      # Plugin configuration file
├── icon.png         # Plugin icon
├── web/             # Web resource directory
│   ├── index.html   # Entry page (required)
│   ├── style.css    # Style file (optional)
│   └── script.js    # JavaScript file (optional)
├── data/            # Plugin data directory (created at runtime)
└── cache/           # Plugin cache directory (created at runtime)
```

### 10.2 plugin.json Configuration

```json
{
    "pluginId": "com.example.myplugin",
    "version": 1,
    "versionName": "1.0.0",
    "minHostVersion": 1,
    "name": "My Plugin",
    "author": "Developer",
    "description": "Plugin description",
    "notice": "Welcome! This notice will be shown on first open",
    "icon": "icon.png",
    "apiLevel": 21,
    "uiType": "web",
    "entry": "web/index.html",
    "permissions": "android.permission.INTERNET,android.permission.VIBRATE",
    "backend": "other",
    "backendStartCommand": "sh scripts/start.sh",
    "backendStartEntry": "scripts/start.sh",
    "backendAutoStart": true,
    "backendTimeout": 30,
    "backendHealthCheck": "/health"
}
```

> Note: Native plugins with `uiType: "native"` use `mainClass` to specify the entry class, and do not need `entry` or backend fields; the above example is a Web + Backend plugin (`uiType: "web"`, `entry` points to an HTML page, backend uses the unified `backendStartCommand` startup command). For complete field descriptions, see the development documentation README 12.3.

### 10.3 Import Plugins

| Method | Description |
|---|---|
| Single File Import | Select a single .tpk file |
| Batch Import | Select multiple .tpk files |
| Plugin Set Import | Select a ZIP package containing multiple .tpk files |

### 10.4 Export Plugins

1. Check the plugins to export (supports multi-select)
2. Click the "Export" button
3. Plugins are packaged as ZIP files

### 10.5 Uninstall Plugins

1. Check the plugins to uninstall
2. Click the "Uninstall" button
3. Confirm uninstallation

### 10.6 Plugin Category Filtering

Plugin categories come from the `category` field in plugin.json (default "Uncategorized"; the current wizard does not provide category input, requiring manual editing of plugin.json). The category bar at the top of the "Tools" page can filter the plugin list by category. When there are many categories, **you can swipe left and right horizontally** to view all categories without truncation.

Since v5.3.0, supports **changing categories** within the app:

- Plugin details popup adds "Change Category" entry (accessible from both plugin management page and tools page long press), can directly modify individual plugin categories
- "Plugin Management" export/delete action bar adds "Change Category" button, supports **batch category modification**
- Category popup supports **selecting existing categories** or **custom new categories**
- Plugin details popup adds "**Uninstall**" button (can uninstall directly from both management and tools pages, with confirmation dialog before uninstalling)

### 10.7 Add Desktop Shortcut (v5.4.0)

The **`+` button** on the right side of each plugin list item in the "Plugin Management" page is used to quickly **create a desktop shortcut** for that plugin:

1. Go to "Manage" > "Plugin Management"
2. Find the target plugin, click the **`+`** button on the right side of the list item
3. A system desktop shortcut confirmation appears (Android 8.0+) or is created directly (older versions), clicking confirm creates the plugin shortcut on the desktop
4. Click the desktop shortcut to open the plugin directly; for more shortcut creation methods, see "IV. App Shortcuts"

> Since v5.4.0, this button has been changed from "Launch" to "Add Shortcut"; to run a plugin, click the plugin list item itself or the "Run" button in the details popup.

---

## XI. Plugin Development

### 11.1 Create Plugin

1. Click the "Dev" tab at the bottom
2. Click "Create Plugin"
3. Select frontend type:
   · Native UI: Android View native interface
   · Pure WebView: HTML/CSS/JS only, no backend
   · WebView + Backend: HTML/CSS/JS + backend service
   · CUI Terminal: Full-screen terminal running scripts (new in v4.5.0)
4. If selecting WebView + Backend, fill in the **backend startup command** (default `sh scripts/start.sh`, customizable); the backend runtime environment (built-in Termux / real Termux) is globally configured in "Backend Runtime Settings" on the "Manage" page
5. Fill in plugin information according to the wizard
6. Click "Finish" to generate project files

### 11.2 Wizard Step Description

| Type | Steps | Step Content |
|------|-------|--------------|
| Native UI | 5 steps | Config → Icon → Code → Resources → Package |
| Pure WebView | 4 steps | Config → Icon → Web Code → Package |
| Web + Backend | 4 steps | Config → Icon → Web Code → Package (auto-generates scripts/start.sh + server.py) |
| CUI Terminal | 4 steps | Config → Terminal Script → Generate Project Files → Package |

> Wizard "Config" page fields (streamlined from v5.5.0): Plugin ID / Name / Author / Description / **Plugin Notice (notice)** / Version / Version Name / Main Class Name (native) / Entry File (Web) / Permission Multi-Select / Minimum Host Version / Category / Update URL; Web+Backend additionally provides "Backend Startup Command / Backend Timeout / Health Check Path"; openWith (receive external content) toggle and reception configuration are optional. **No longer provided**: Max Memory / Max CPU Time / Max Concurrent Tasks (resource limits), Dependencies, API Level, Backend Keep-Alive (these fields are not written to wizard-generated plugin.json, but can still be retained when manually writing, see 12.3).

### 11.3 Plugin Notice Feature

Add the notice field in plugin.json:

```json
{
    "notice": "Welcome to my plugin!\n\nFeature Description:\n1. Click buttons to execute operations\n2. Data is automatically saved\n\nNotes:\n- Storage permission required\n- Please configure first on initial use"
}
```

On first opening the plugin, the notice dialog is automatically displayed. Users can choose:

· "Got it": Close dialog, do not show again (for this session)
· "Don't show again": Permanently close this plugin's notice
· "Remind later": Close dialog, show again next time the plugin is opened

### 11.4 Web Plugin JavaScript API

```javascript
// Call host features
UINPlugin.callHost('toast', 'Message');
UINPlugin.callHost('finish', '');
UINPlugin.callHost('vibrate', '200');

// HTTP requests
UINPlugin.httpGet(url, callbackId);
UINPlugin.httpPost(url, data, callbackId);

// File system
UINPlugin.writeFile('test.txt', 'content');
UINPlugin.readFile('test.txt');
UINPlugin.listFiles('');

// Storage
UINPlugin.setStorage('key', 'value');
UINPlugin.getStorage('key');
UINPlugin.clearStorage();

// Get information
UINPlugin.getPluginInfo();
UINPlugin.getDeviceInfo();
UINPlugin.getAppVersion();
UINPlugin.getBackendStatus();
```

### 11.5 Plugin Data Persistent Storage

New feature in v4.4.0

Each plugin has an independent data storage space, and data is automatically preserved when the plugin is updated.

Web plugin storage API:

```javascript
// KV storage
UINPlugin.setStorage('username', 'JohnDoe');
const name = UINPlugin.getStorage('username');
UINPlugin.setStorageInt('score', 100);
UINPlugin.setStorageBool('isLoggedIn', true);
UINPlugin.setStorageJSON('config', JSON.stringify({theme: 'dark'}));

// Batch operations
UINPlugin.setStorageBatch(JSON.stringify({k1:'v1', k2:'v2'}));
const result = JSON.parse(UINPlugin.getStorageBatch('["k1","k2"]'));

// File operations
UINPlugin.writeFile('notes.txt', 'Hello World');
const content = UINPlugin.readFile('notes.txt');
UINPlugin.deleteFile('notes.txt');

// Data statistics
const stats = JSON.parse(UINPlugin.getStorageStats());
console.log('KV:', stats.kvCount, 'Files:', stats.fileCount);

// Import/Export
const exported = UINPlugin.exportData();
UINPlugin.importData(exported);
```

Native plugin storage API:

```kotlin
val pctx = PluginContext(context, pluginDir)
pctx.putString("key", "value")
val value = pctx.getString("key")
pctx.writeFile("data.txt", "content")
val content = pctx.readFile("data.txt")
```

---

## XII. Plugin Runtime

### 12.1 Run Plugin

1. Click "Tools" at the bottom
2. Browse the plugin list
3. Click the plugin to run

### 12.2 View Mode

| Mode | Description |
|---|---|
| List View | Display plugin details list |
| Grid View | Display plugin icons in a grid |

### 12.3 Search Plugins

1. Click the search icon
2. Enter keywords
3. Results are filtered in real-time

### 12.4 Long Press Actions

**Long-pressing** a plugin on the "Tools" page brings up the same **details popup** as the "Plugin Management" page, allowing quick viewing and running without switching pages:

- **Plugin Information**: ID, version, minimum host version, API level, name, author, description, category, UI type, entry, main class, update URL, and other plugin.json fields
- **File Structure**: File tree within the plugin directory (directories/files with sizes)
- **plugin.json Raw Text**: Read from disk and formatted
- Bottom displays **total plugin size and file count**, can directly **run the plugin**

> For plugin import, export, uninstall, and other operations, please use "Manage" > "Plugin Management".

### 12.5 Backend Runtime Settings (Web + Backend / CUI Plugins)

Plugins with backends (WebView + Backend, CUI) need to start a backend service (HTTP service, etc.). How the host starts it and what runtime environment to use is **globally configured** in the "**Backend Runtime Settings**" on the "Manage" page, taking effect for all backend plugins.

> ⚠️ Since v5.2.0, "Backend Runtime Settings" has been changed to a **standalone page** (`BackendSettingsActivity`), only available on the management page; the development page no longer has an entry point.

#### 12.5.1 Backend Implementation: Built-in Termux or Real Termux

| Option | Description | Suitable Scenario |
|--------|-------------|-------------------|
| **Built-in Termux** (default) | Uses the app's built-in lightweight Termux, forces Proot shared Alpine container to run the backend, **no need to install anything** | Out-of-the-box, want to save trouble |
| **Real Termux** | Calls Termux installed on the device (`com.termux`) to run the backend, can use native Termux ecosystem | Needs pip/npm/apk and other complete package ecosystem |

- **Built-in Termux**: Alpine rootfs is built into the app (offline recovery from assets, about 19MB, one-time decompression), no network installation required
- **Real Termux**: Requires Termux to be installed on the device with initialization completed, otherwise a guide will pop up automatically if startup fails

#### 12.5.2 Backend Environment (Real Termux Only)

| Option | Description |
|--------|-------------|
| **Termux Native** | Run directly in the Termux native environment |
| **Proot Container** | Run inside a Proot container (container name configurable, default `alpine`, e.g., `ubuntu` needs to be installed first) |

- Selecting Proot container requires filling in the container name; use `proot-distro list` to view installed containers
- Built-in Termux **forces** Proot Alpine container, this option is not applicable

#### 12.5.3 Idle Auto Reclamation

The backend automatically stops after being idle for the set duration to avoid long-term resource usage; plugin activity requests refresh the timer. After exiting the plugin for the set duration, its backend is automatically cleaned up. Duration options include preset 3 / 5 / 10 / 15 minutes (default 5 minutes), or enter any number of minutes in the "Custom (minutes)" input box; selecting "**Unlimited**" means the backend will never be automatically reclaimed, only stopping when actively stopped.

- When stopping, the host first calls the HTTP `/stop` endpoint for **graceful shutdown**
- **Built-in Termux**: Additionally terminates by process group `SIGKILL`
- **Real Termux**: Idle reclamation is managed uniformly by the shared supervisor (see 12.5.5), with independent timeout recursive process tree killing per plugin based on `idle/<key>.start` startup timestamps, **does not depend on the plugin implementing `/stop`**; the host only does port detection and state cleanup. Selecting "Unlimited" does not write idle files. Please ensure `com.termux.permission.RUN_COMMAND` is granted and "Allow external apps to run commands" is enabled

#### 12.5.4 Real Termux Initialization Command

After selecting "Real Termux", the settings page displays an "**Initialization Command**" card; click the copy icon to copy to clipboard, paste into Termux and execute once:

```sh
mkdir -p ~/.termux; grep -q '^allow-external-apps=true' ~/.termux/termux.properties 2>/dev/null || echo 'allow-external-apps=true' >> ~/.termux/termux.properties; termux-setup-storage; termux-reload-settings 2>/dev/null || true
```

This command completes the following in order:
1. Writes `allow-external-apps=true` (allows external apps to launch Termux)
2. `termux-setup-storage` authorizes storage
3. `termux-reload-settings` reloads configuration

> If backend startup fails, the host will automatically detect missing items and prompt: `allow-external-apps` not enabled, `termux-setup-storage` not executed, container not installed or missing `RUN_COMMAND` permission, etc. Follow the prompts to resolve.

#### 12.5.5 Real Termux Shared Supervisor

Real Termux (proot or native mode) uses a **single resident shared supervisor**: the container/session is only initialized once, and all plugin backends run as supervisor child processes. Subsequent plugin startup saves proot initialization overhead (cold startup about 5s). Built-in Termux (alpine, about 2s) remains unchanged (each plugin has its own proot).

- Communication protocol (control directory `<plugins_root>/.uin/`): `cmd/<key>.cmd` (startup command), `pid/<key>` (backend PID), `stop/<key>` (stop request), `idle/<key>` (idle minutes), `idle/<key>.start` (startup timestamp), `alive` (supervisor alive marker), `host_alive` (host heartbeat, touch every 30s, supervisor auto-exits on 300s timeout), `shutdown` (exit marker), `keep_alive` (background keep-alive marker)
- proot startup: `proot-distro login <container> --bind '<plugins_root>:/plugins' -- sh -lc 'sh /plugins/.uin/supervisor.sh /plugins'`
- Native startup: `sh '<plugins_root>/.uin/supervisor.sh' '<plugins_root>'`
- Idle reclamation: supervisor independently times out and recursively kills process trees per plugin based on `idle/<key>.start` startup timestamps; `kill -0 $pid` detects process liveness
- Supervisor stays resident during host lifetime; host writes `shutdown` marker on exit, supervisor auto-exits
- Software preheats supervisor (`prewarm`) in the background on startup, also triggered when saving backend settings
- Backend settings page adds "Shared Scheduler" status card (RUN_COMMAND permission + supervisor alive status)
- **Background Keep-Alive** (optional): When enabled, supervisor no longer exits when host process is killed, combined with battery optimization exemption + notification bar + Shizuku/Dhizuku permissions to maintain background survival

#### 12.5.6 How the Runtime Environment Takes Effect

After a plugin is opened, the host selects the execution path based on global settings and uniformly injects `$PORT`, `$PLUGIN_ID`, `$PLUGIN_DIR`, `$WORK_DIR` environment variables:

- **Built-in Termux**: `proot-distro login alpine --bind <pluginDir>:/plugins/<id> -- sh -lc "<startup command>"`
- **Real Termux + Native**: `bash -lc "<startup command>"` (working directory = plugin directory)
- **Real Termux + Proot Container**: `proot-distro login <containerName> --bind <pluginDir>:/plugins/<id> -- sh -lc "<startup command>"`

The plugin does not need to be aware of the runtime environment; it just needs to read environment variables according to the convention, listen on `$PORT`, and implement `/health` and `/stop`.

#### 12.6 Plugin Receives External Content (openWith Relay, New in v5.4.0)

Content **shared** or **opened with other apps** by the system/other apps can be handed to plugins that support that content:

1. In any app, click "Share" or "Open with other apps", select **UIN Tool**
2. Enter the "Select Receiving Plugin" relay page, which displays plugins that have declared `openWith` and match the content type
3. Click a plugin to hand the content to it for processing; when only 1 plugin matches, it opens automatically

- Supports **text / links / files (including multi-select)** three types of content
- Files are automatically copied into the plugin's `.incoming/` directory, readable by the plugin backend
- Relay page supports **search**: real-time filtering by plugin display name / plugin ID / description
- Only plugins whose authors have declared `openWith` in `plugin.json` will appear in the relay page and system share entry

#### 12.7 Plugin Multi-Instance (New in v5.4.0)

The same plugin can run **multiple independent instances** simultaneously:

- **Web / CUI plugins**: Each opening is a new instance; page state, JS interface, and backend do not interfere between instances
- **Native plugins**: Single instance by default (reuses the same instance when opened repeatedly); after enabling "Native Plugin Multi-Instance (Experimental)" in "Manage" > "Development Tools", each opening creates a new independent instance
- Backend multi-instance ("Per-Instance Independent Backend Port" toggle in "Development Tools"): When **off**, multiple instances share the same backend process (shared port); when **on**, each instance has its own backend process and port, independent of each other
- **Keep Session Single Window (v5.5.0)**: "Keep Session on Close" toggle is **enabled by default** (default single-window deduplication: shared port mode + Web plugins keep only one background window for the same plugin, repeated openings bring it to the foreground, no more multi-instance); users can explicitly disable it in "Development Tools", after which Web / CUI plugins create independent instances each time (supporting multi-instance)

---

## XIII. Permission Management

### 13.1 Permission Description

UIN Tool provides a detailed permission description page explaining the purpose of each permission.

Access method:

1. Click "Manage" > "Permission Management"
2. Click the "Permission Description" button

### 13.2 App Permission List

| Category | Permission Item | Description |
|---|---|---|
| Storage | Read/Write Storage | Import/export plugins |
| Network | Access Network/Get Status | Plugin network features |
| Camera | Camera | Photo/scan features |
| Microphone | Record Audio | Voice features |
| Location | Precise/Coarse Location | Location features |
| Phone | Make Calls/Read Status | Phone features |
| SMS | Send/Read/Receive SMS | SMS features |
| Contacts | Read/Write Contacts | Contact features |
| Calendar | Read/Write Calendar | Calendar features |
| System | Floating Window/Modify Settings, etc. | System-level features |
| Accessibility | Accessibility Service | Automated operations |
| Advanced | Install Unknown Apps, etc. | Advanced features |

### 13.3 Plugin Permission Management

Plugins declare required permissions in plugin.json (**comma-separated strings or JSON arrays are both acceptable**, both formats are parsed for compatibility):

```json
{
    "permissions": "android.permission.INTERNET,android.permission.VIBRATE"
}
```

```json
{
    "permissions": ["android.permission.INTERNET", "android.permission.VIBRATE", "READ_CLIPBOARD"]
}
```

> The development wizard "Permissions" field uses a popup multi-select, which already includes all common permissions (including pseudo-permissions `READ_CLIPBOARD` / `WRITE_CLIPBOARD`). Pseudo-permissions only need to be declared to take effect, no runtime authorization needed. Chinese display name examples for permissions: `android.permission.READ_EXTERNAL_STORAGE` → Read External Storage.

Permission interaction flow (v5.5.0):

1. Plugin declares required permissions in plugin.json
2. **Plugin opens after permission prompt dialog pops up first** (dialog uses host unified style rendering):
   - **Web plugins** (with/without backend): Lists **ungranted** permissions, provides "OK" / "Don't Prompt Again" / "Manage Permissions" — "Manage Permissions" jumps directly to that plugin's permission management page
   - **Native plugins**: Lists required (declared) permissions, provides "OK" / "Don't Show Again" (prompts each time the plugin is opened, unless "Don't Show Again" is selected)
3. Permission management page only manages **Web plugin** permissions (native/CUI plugins do not display on the permission management page, native permissions are enforced by the system); the page has **no refresh button** — the list auto-refreshes after granting/revoking; button row provides "**Grant All**" / "**Revoke All**" short text buttons (shortened from v5.5.0, ensuring complete display)
4. Plugin is loaded after permissions are granted

### 13.4 Permission Persistence

> Since v5.5.0, the old "auto-popup by status" `permission_state` (0/1/2 single value) API is deprecated. Permission status is now determined **per permission** (granted / blocked). "Don't prompt/Don't show" selections are persisted per plugin (`plugin_permission_prompts`).

### 13.5 Shizuku/Dhizuku Permissions

v5.3.0 adds Shizuku and Dhizuku permission support, **using official APIs** (`dev.rikka.shizuku:api` / `dev.rikka.shizuku:provider` and `io.github.iamr0s:Dhizuku-API`):
- **Shizuku**: `Shizuku.pingBinder()` + `checkSelfPermission()` detects service and authorization status; `Shizuku.requestPermission()` requests authorization, results are **refreshed in real-time** via `addRequestPermissionResultListener` callback; Manifest registers `rikka.shizuku.ShizukuProvider`
- **Dhizuku**: `Dhizuku.init()` + `isPermissionGranted()` detects authorization status; `Dhizuku.requestPermission()` requests authorization, callback **refreshes in real-time**

Steps to grant permissions:

1. First install the **Shizuku** or **Dhizuku** app on the device (users need to install the corresponding app themselves)
2. Start Shizuku/Dhizuku and complete its startup/authorization process (e.g., Shizuku starts via adb or wireless debugging)
3. Enter UIN Tool "Manage" > "Permission Management" page, find the **Shizuku / Dhizuku** permission item
4. The app will **detect service and API permission authorization status** (whether the service is available / whether authorization has been granted)
5. Click the corresponding item, the app will **request authorization through the official authorization interface**, adding this app to the authorization list
6. After authorization is complete, return to the app to use the related capabilities

---

## XIV. Backup & Restore

### 14.1 Create Backup

1. Click "Manage" > "Backup & Restore"
2. Select backup options (including UI configuration, app settings)
3. Click "Create Backup"
4. Wait for the backup to complete

Backup contents:

· All installed plugins
· Plugin data (data/ directory)
· UI theme configuration
· App settings
· Working directory configuration
· Mirror site configuration

### 14.2 Restore Backup

1. Select the backup file to restore
2. Confirm the restore operation
3. Wait for the restore to complete
4. Reopen the app

⚠️ Note: The restore operation will overwrite existing plugins and configurations!

### 14.3 Manage Backup Files

· Click a backup file to view details
· Click "Restore" to restore the backup
· Click "Delete" to delete the backup

---

## XV. Development Tools (Runtime Logs + Developer Options)

### 15.1 Entry Point

Click "Manage" > "Development Tools" at the bottom to enter the standalone development tools page. This page merges the original "Runtime Logs" and "Developer Options" entries, centrally managing runtime log viewing and advanced development settings.

### 15.2 Log Content

Log files contain:

· App startup information
· Plugin loading records
· Error and warning messages
· Crash reports (with stack trace information)
· Backend output logs

### 15.3 Log Location

```
/storage/emulated/0/UIN_Tool/logs/uin_tool_date.log
/storage/emulated/0/UIN_Tool/logs/crash_date.log
```

### 15.4 Log Operations

| Operation | Description |
|---|---|
| Pull to Refresh | Reload latest logs |
| Clear | Delete current log file |
| Clear All | Delete all historical logs |
| Export | Export logs as text file |

> After the app crashes, the next time you open it, it will automatically jump to this page to display the crash log.

### 15.5 Developer Options

| Option | Description |
|---|---|
| Ignore Signature Verification (for development) | Ignore plugin signature verification, allow unsigned/abnormal signature plugins to run (for debugging only, disabled by default) |
| Native Plugin Multi-Instance (Experimental) | Allow the same native plugin to open multiple independent instances; when disabled, native plugins are single-instance (disabled by default) |
| Per-Instance Independent Backend Port | Each plugin instance runs its own independent backend process/port; when disabled, instances share one backend (disabled by default) |

> Click "Save" after modifying developer options for changes to take effect.

---

## XVI. Desktop Widgets

### 16.1 3x3 List Widget

Displays 9 plugins, click to run directly.

How to add:

1. Long press an empty area on the desktop
2. Select "Widgets"
3. Find the "UIN Tool" widget
4. Drag to the desktop
5. Configure which plugins to display

Configuration method:

1. In "Manage" > "Widget Configuration"
2. Select the plugin for each position
3. Save the configuration

### 16.2 1x1 Shortcut

Desktop shortcut bound to a single plugin.

How to add:

1. Long press an empty area on the desktop
2. Select "Widgets"
3. Find the "Plugin Shortcut" widget
4. Drag to the desktop
5. Select the plugin to bind

---

## XVII. UI Personalization

### 17.1 Color Configuration (38+ Color Items)

| Category | Color Items |
|---|---|
| Primary Colors | Theme color, dark theme color, light theme color, accent color |
| Auxiliary Colors | Success color, warning color, error color, info color |
| Text Colors | Primary text, secondary text, hint text, inverse text |
| Background Colors | Background color, surface color, variant surface color |
| Border Colors | Divider color, glass background color, disabled color |

### 17.2 Color Picker

Click any color configuration item to open the color picker:

· RGB/Alpha sliders independently adjustable (0-255)
· Visual color picker: hue bar + saturation/brightness panel, click/drag anywhere to pick any color (new in v5.3.0)
· Real-time color preview
· Hex color input: supports `#RRGGBB` / `#AARRGGBB`, input shows real-time preview (new in v5.3.0)
· 16-grid preset palette for quick selection
· Dialog follows theme (dark mode auto-adapts to dark color scheme, new in v5.3.0)

### 17.3 Shape Configuration

| Option | Description |
|---|---|
| Small Corner Radius | Small element corner radius |
| Medium Corner Radius | Medium element corner radius |
| Large Corner Radius | Large element corner radius |
| Extra Large Corner Radius | Extra large element corner radius |
| Button Corner Radius | Button corner radius size |
| Card Corner Radius | Card corner radius size |
| Dialog Corner Radius | Dialog corner radius size |

### 17.4 Size Configuration

| Option | Description |
|---|---|
| Button Height/Min Width/Shadow | Button style |
| Card Shadow/Padding | Card style |
| Small/Medium/Large Spacing | Layout spacing |
| Small/Medium/Large Icons | Icon sizes |
| Progress Bar Height | Progress bar style |

### 17.5 Font Configuration

| Option | Description |
|---|---|
| Title Font Size | Title text size |
| Body Font Size | Body text size |
| Auxiliary Font Size | Auxiliary text size |
| Section Title Size | Section title size |
| Text Boldness | Global text weight |

**Feature Description** (verified and fixed item by item from v5.3.0, ensuring all take real effect):
- **Ripple Effect Toggle**: When disabled, globally disables click ripples (theme layer injects no-ripple indicator, unified across cards/buttons/list items)
- **Status Bar / Navigation Bar Color**: Set on the "Colors" page, **takes effect immediately on save**, no longer overridden by theme background color
- **Dark Primary (primary_dark)**: Maps to secondary container text color (`onSecondaryContainer`), old defaults auto-migrated on upgrade

### 17.6 Effects Configuration

| Option | Description |
|---|---|
| Gradient Background | Unified gradient background for all pages (new in v5.3.0) |
| Glass Effect | Frosted glass texture UI components |
| Neumorphism Style | Soft concave-convex light-shadow effect UI components (new in v5.6.0, mutually exclusive with glass effect) |
| Ripple Effect | Click ripple feedback |
| Translucent Effect Transparency | Glass effect / Neumorphism effect alpha transparency adjustment (new in v5.6.0) |

**Gradient Background** (new in v5.3.0):

- **Toggle**: Controls whether the global gradient background is enabled; when disabled, reverts to theme background color
- **Gradient Mode**: Selected via **dropdown** (collapsed by default, click to expand):
  - **Single-select (single-color gradient)**: Gradient from selected color to background color
  - **Multi-select (multi-color gradient)**: Gradient composed of 2-6 colors, can **add colors**, **edit** each color (click edit icon to open color picker), or **delete** (keep at least 1 color)
- **Gradient Direction**: Can set "**Start Direction**" and "**End Direction**" separately (up/down/top-left/bottom-left/top-right/bottom-right, 6 directions), selected via **dropdown**, colors arranged along start → end direction
- **Default single-color gradient (theme-adaptive)**: Light `#FFC4D6DF` / Dark `#FF4C4F51` (direction **bottom-right → top-left**), ready to use out of the box; old "multi-color three-color default" auto-migrates to new default, user custom configurations unaffected
- Gradient background covers **all pages** (main interface four tabs + various management/tools/help/documentation sub-pages), auto-adapts with light/dark theme
- Gradient configuration participates in **save/reset/export/import**
- All gradient configuration (toggle/mode/direction/colors) **merged into a single card**, more centralized configuration
- The "Colors / Shape / Size / Font / Effects" **tab bar at the top of this page follows the background color** (transparent when gradient is enabled), and **has no bottom border line**
- Main interface **bottom navigation** is floating style: style completely follows card (opaque card background, rounded corners and shadow); navigation **covers content**, scrolling cards show from surrounding transparent whitespace without clipping; clicking has press-to-scale feedback, selected icon enlarges, no ripple
- Development wizard bottom "Next" action bar is **floating pure transparent**: covers content without clipping cards, **background completely transparent, no card background color or shadow**, only the "Previous / Next" buttons provide height, tight to buttons, no extra white background
- Code editor **file list background follows main background** (transparent when gradient is enabled, showing global gradient)
- Plugin management page **checkboxes only show in selection mode** (top bar list icon enters/exits selection mode); batch delete supports **deleting all selected plugins at once** (with quantity confirmation dialog)
- White/transparent background **outline buttons** have 1dp theme outline, clear contours

### 17.7 Icon Tint Toggle

| Status | Effect |
|---|---|
| Enabled | All icons use theme color tinting |
| Disabled | Icons keep original colors |

### 17.8 Configuration Operations

| Operation | Description |
|---|---|
| Real-time Preview | Top card shows current theme effect in real-time |
| Reset | Restore default configuration |
| Export | Export configuration as JSON file |
| Import | Import configuration from JSON file |
| Save | Save current configuration |
| Language Switching | Switch app display language (Chinese/English, etc.), no need to change system language (new in v5.6.0) |

---

## XVIII. FAQ

### 18.1 FAQ (Q1-Q35)

Q1: How to get more plugins?
A: Install official plugins from the "Repository" page, or develop your own plugins.

Q2: Are plugins safe?
A: Plugins undergo SHA-256 signature verification during import, with independent permission control. It is recommended to only obtain plugins from trusted sources.

Q3: Where is the working directory?
A: Default path: /storage/emulated/0/UIN_Tool/

Q4: How to use the terminal feature?
A: Click "Dev" > "Open Terminal"; the Linux environment is automatically installed on first use.

Q5: Native plugin compilation fails?
A: The current native plugin compilation feature is temporarily disabled (Android environment lacks tools.jar support). It is recommended to use Web plugins or compile on a PC.

Q6: Web plugin changes not taking effect?
A: Close and reopen the plugin; no recompilation needed.

Q7: How to manage plugin permissions?
A: Plugins declare permissions in plugin.json. When opening the plugin, a permission prompt pops up first: Web plugins show ungranted permissions (OK/Don't Prompt Again/Manage Permissions); native plugins show required permissions (OK/Don't Show Again). The permission management page manages Web plugin permissions.

Q8: App crashes, what to do?
A: Reopen the app (it will automatically display the crash log), check the runtime log to analyze the cause, or clear app data and reinstall.

Q9: How to customize UI colors?
A: In "Manage" > "UI Personalization", adjust color configurations.

Q10: How to update UIN Tool?
A: Automatic detection of new versions on startup, or manually check in "Manage" > "Check for Updates".

Q11: Repository page fails to load?
A: Check network connection, pull to refresh to retry, configure mirror sites in "GitHub Acceleration".

Q12: Slow plugin download speed?
A: Enable CDN acceleration in "GitHub Acceleration", check more mirror sites.

Q13: Widget not showing plugins?
A: Check if the widget has configured plugins, try reconfiguring, refresh the widget list.

Q14: Is there an onboarding page on first launch?
A: The onboarding page is automatically displayed on first install or version update.

Q15: No shortcuts when long-pressing app icon?
A: Requires Android 7.1+ system; some third-party launchers may not support it.

Q16: What is force update?
A: When the Release Tag format is {version code}-{version name}-1, users are forced to update and cannot skip.

Q17: How to export development templates?
A: Click "Export Template" on the "Dev" page; the 7 built-in packaged plugins are copied to `/storage/emulated/0/UIN_Tool/templates/` and a README.txt is generated.

Q18: What does the icon tint toggle do?
A: When enabled, all icons use theme color tinting; when disabled, icons keep original colors.

Q19: How to restore UI configuration?
A: Click the "Reset" button in "UI Personalization".

Q20: How to view version update content?
A: Click "Manage" > "Documentation Center" > "Changelog"

Q21: How to create a Web plugin with backend?
A: Click "Create Plugin" > Select "WebView + Backend" > Fill in backend startup command (default `sh scripts/start.sh`). The wizard auto-generates `scripts/start.sh` and `scripts/backend/server.py`; the backend runtime environment (built-in Termux / real Termux) is globally configured in "Backend Runtime Settings" on the "Manage" page.

Q22: What needs to be installed for the backend?
A: The startup script `scripts/start.sh` automatically detects and installs dependencies (`pkg install python` / `apk add python3`), no need to manually install third-party libraries.

Q23: How to use the plugin notice feature?
A: Add the notice field in plugin.json; it is automatically displayed on first opening.

Q24: What languages does the code editor support?
A: Supports 30+ programming languages, including Java, Kotlin, Python, JavaScript, TypeScript, HTML, CSS, JSON, XML, Markdown, Shell, SQL, Go, Rust, PHP, Ruby, Swift, Dart, Lua, Scala, Perl, Haskell, Elixir, Erlang, Clojure, Groovy, Dockerfile, Makefile, INI, Properties, TOML, YAML, etc.

Q25: Where is plugin data stored?
A: Plugin data is stored in /storage/emulated/0/UIN_Tool/plugins/{pluginId}/data/ directory, KV data is stored in SharedPreferences. Data is automatically preserved when plugins are updated.

Q26: How to clear plugin data?
A: Web plugins can use UINPlugin.clearStorage() and UINPlugin.clearCache(), or uninstall the plugin in "Plugin Management".

Q27: Will permission status be persisted?
A: Yes. Permission grant/block status is persisted per plugin; "Don't prompt/Don't show" selections are also persisted per plugin, stopping popups after selection (native plugins prompt each time they are opened, unless "Don't Show Again" is selected).

Q28: How to reset plugin permissions?
A: Since v5.5.0, permission status is managed per permission (granted / blocked), and can be reset (grant / revoke / block) or the "Don't prompt/Don't show" setting removed for Web plugins on the "Plugin Management > Permissions" page; native plugin permissions are managed by the system. The old `clearPermissionState()` (permission_state single value) interface is deprecated.

Q29: How to create a CUI terminal plugin?
A: "Dev" > "Create Plugin" > Select "CUI Terminal", the 4-step wizard auto-generates `scripts/script.py` example script and startup command configuration; it runs in a full-screen terminal after opening.

Q30: How to declare permissions in plugin.json?
A: Use **comma-separated strings or JSON arrays** (both formats are parsed for compatibility), e.g., `"permissions": "android.permission.INTERNET,android.permission.VIBRATE"` or `"permissions": ["android.permission.INTERNET", "READ_CLIPBOARD"]`. Pseudo-permissions (`READ_CLIPBOARD` / `WRITE_CLIPBOARD`) only need to be declared to take effect.

Q31: Why is there no backend after importing a "WebView + Backend" plugin?
A: The old packager only packaged `web/` for Web plugins, not the `scripts/` backend files, requiring manual placement in the installation directory. Since v5.2.0, packaging recursively includes the entire project directory (`scripts/`, `scripts/backend/server.py`, `start.sh`, etc. all included in the TPK), no manual placement needed; also, the wizard-generated `entry` has correctly pointed to `web/index.html` since v5.1.0.

Q32: Why does a permission prompt pop up when opening a plugin?
A: Since v5.5.0, a permission prompt appears before opening a plugin: Web plugins list ungranted permissions (OK/Don't Prompt Again/Manage Permissions); native plugins list required permissions (OK/Don't Show Again). Selecting "Don't prompt/Don't show" stops the popups, and Web plugin permissions can be re-managed on the permission management page.

Q33: What is the neumorphism style?
A: Neumorphism is a UI design style that makes interface elements appear to emerge from or sink into the background through soft concave-convex light-shadow effects. Enable it in "Manage" > "UI Personalization" > "Effects" page; mutually exclusive with glass effect (only one can be enabled at a time).

Q34: How to switch the app language?
A: In "Manage" > "UI Personalization" > "Language" page, you can directly switch the app display language (Chinese/English, etc.) without changing the system language; switching takes effect immediately.

Q35: How to adjust the transparency of glass effect or neumorphism?
A: In "Manage" > "UI Personalization" > "Effects" page, after enabling glass effect or neumorphism, a transparency slider appears; drag it to preview and adjust the effect in real-time.

---

## XIX. Contact Support

### 19.1 Contact Information

| Channel | Contact Info |
|---|---|
| Email | undefinedinvalidnull@outlook.com |
| GitHub | https://github.com/Undefined-Invalid-Null/UIN-Tool |
| Plugin Repository | https://github.com/UIN-Tool-Plugins |
| QQ Group | 511875883 |

---

## Version History

| Version | Update Summary |
|---|---|
| v5.6.0 | Neumorphism style: soft concave-convex light-shadow effects for cards, buttons, and other components, mutually exclusive with glass effect; multilingual switching: switch Chinese/English language directly in the app without changing system language; translucent effect transparency control: glass effect/neumorphism supports alpha adjustment |
| v5.5.0 | Compatibility fixes (native plugin AbstractMethodError, CUI proot path, MarkdownRenderer crash, DocViewerScreen crash); clipboard pseudo-permissions (READ_CLIPBOARD/WRITE_CLIPBOARD); development wizard completion (backend timeout/health check/openWith/all permissions multi-select, streamlined fields); permission prompt dialog before opening plugin (unified style); keep session single window enabled by default; real Termux shared Supervisor (cold startup saves ~5s); startup environment auto-installation (bootstrap + Alpine background pre-install); 4 static desktop shortcuts |
| v5.4.0 | Plugin receives external content (openWith relay, new in v5.4.0): system "Share/Open with other apps" → relay page selects plugin, supports text/links/files (including multi-select), files copied into plugin `.incoming/` directory, Web plugins read via `window.getOpenData()` / `UINPlugin.getOpenData()`, native plugins receive via `onHostEvent("host.open")`, auto-opens when only 1 match, relay page supports search; Plugin multi-instance (new in v5.4.0): Web/CUI plugins support multi-instance by default, native plugins single instance, can enable "Native Plugin Multi-Instance (Experimental)" in development tools, each instance independent `instanceId`, backend supports shared port / independent port two modes; plugin management page launch button changed to "Add Desktop Shortcut" |
| v5.3.0 | Comprehensive refinement: unified UI component system (`Unified*` sole implementation source + glass effect, all screens migrated); global gradient background (adjustable direction: start/end direction 6 options, default light blue bottom-right→top-left) + glass transparency increased + top title bar follows gradient; plugin management page card style unified (checkboxes only in selection mode) + batch delete fix (delete all selected at once) + floating bottom navigation (completely follows card style, covers content, no card clipping, press-to-scale + selected icon enlarge, no ripple) + button outline improved + dialog background follows main background (transparency 0.95 ensures readability); UI personalization page optimization (tab bar follows background color and no bottom border, gradient mode and direction changed to dropdowns, gradient config merged into single card) + **feature verification fixes** (ripple toggle actually works, status bar/navigation bar color keys work, `primary_dark` integrated into theme); code editor file list follows main background; development wizard bottom "Next" action bar changed to floating **pure transparent** (no card background color or shadow, tight to buttons); color picker upgraded to visual color picker (hue bar + saturation/brightness panel) with hex input and dark mode support; plugin categories support individual/batch changing and custom new categories; plugin details popup adds "Change Category" and "Uninstall" buttons (accessible from tools page long press); adds Shizuku/Dhizuku permission support (using official APIs, real-time refresh); code structure refinement (mirror constants/file utilities/size formatting consolidated, Repository naming unified) |
| v5.2.0 | Packaging recursively includes entire project directory (no longer only web/); silent update check + unified update dialog UI (ReleaseChangelog Markdown); built-in Termux Alpine installation speedup; development wizard completion (packaging no longer auto-exits, config fields completed + permission multi-select, JSON syntax highlighting); code editor file tree long-press properties/rename; plugin details dialog (plugin.json fields + file structure + size) |
| v5.1.0 | Backend runtime architecture refactoring (built-in Termux / real Termux global switch, startup command unified, idle auto-reclamation); CUI terminal foreground direct start + pure fade transition; runtime logs and developer options merged into "Development Tools" page |
| v5.0.0 | Full internationalization (i18n); dynamic JSON theme engine (`--uin-*` CSS variables); bottom navigation self-drawn refactoring; pull-to-refresh unified; skeleton screen/animations/glass effects and other comprehensive UI optimizations |
| v4.5.0 | Proot container runtime (shared Alpine isolated environment); custom backend (other) mode; CUI terminal plugin; template export refactoring (7 built-in packaged templates + README.txt) |
| v4.4.4 | Plugin dialog system unification (built-in Compose dialogs); dialog queuing; fix plugin interaction and screenshot issues |
| v4.4.0 | Plugin data persistent storage (data/ directory); permission system completion (state persistence); 140+ Web API extensions |
| v4.2.0 | Termux backend integration (Python/Node.js/PHP/binary); plugin notice feature; creation wizard optimization |
| v4.1.0 | Sora Editor integration; 30+ language syntax highlighting; 28+ editor themes |
| v4.0.0 | Kotlin + Compose comprehensive refactoring; terminal features (based on Termux); Material 3 UI; complete color picker; plugin permission system |
| v3.10.0 | GitHub acceleration feature; force update mechanism; mirror site management |
| v3.5.0 | Fix development page button display issue |
| v3.4.0 | App shortcuts; auto update; UI personalization comprehensive upgrade |
| v3.0.0 | SplashActivity; onboarding; documentation center; permission description |
| v2.8.0 | In-app update check, download, install |
| v2.6.0 | Web plugin network request, file system, sensor API |
| v2.0.0 | Plugin repository, GitHub integration, mirror acceleration |
| v1.1.0 | Category management, long-press menu, 1x1 widget |
| v1.0.0 | Initial version |

---

| Item | Info |
|---|---|
| Document Version | 5.6.0 |
| Last Updated | August 28, 2026 |
| Corresponding App Version | v5.6.0 (Build 22) |

---

© 2026 UIN Team. All Rights Reserved.
