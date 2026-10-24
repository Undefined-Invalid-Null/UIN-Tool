# UIN Tool User Guide

## Version Information

| Item | Info |
|------|------|
| Document Version | 6.2.0 |
| Corresponding App Version | v6.2.0 (Build 26) |
| Last Updated | October 16, 2026 |

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

### V. Terminal Features (Based on PRoot)
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

### XVIII. Code Editor
- [18.1 How to Open](#181-how-to-open)
- [18.2 Layout](#182-layout)
- [18.3 File Tree & Tabs](#183-file-tree--tabs)
- [18.4 Saving & File Access](#184-saving--file-access)
- [18.5 Find & Replace](#185-find--replace)
- [18.6 More Actions Menu](#186-more-actions-menu)
- [18.7 Hardware Keyboard Shortcuts](#187-hardware-keyboard-shortcuts)
- [18.8 Editor Settings](#188-editor-settings)
- [18.9 Syntax Highlighting](#189-syntax-highlighting)
- [18.10 Code Formatting](#1810-code-formatting)
- [18.11 Known Limitations](#1811-known-limitations)

### XIX. FAQ
- [19.1 FAQ (Q1-Q35)](#191-faqq1-q35)

### XX. Contact Support
- [20.1 Contact Information](#201-contact-information)

---

## I. App Overview

UIN Tool is a lightweight plugin framework that lets you dynamically load and run third-party plugins on Android. It supports both **native plugins** (Java/Kotlin) and **Web plugins** (HTML/CSS/JS), with a built-in Linux terminal, backend runtime, and permission management.

### 1.1 Core Features

| Feature Module | Description |
|----------------|-------------|
| Plugin System | Native (Java/Kotlin) and Web (HTML/CSS/JS) plugins |
| Data Persistence | Independent data/ directory per plugin, preserved on update |
| Terminal | Built-in Linux terminal powered by PRoot |
| Backend Integration | Unified startup command mode, built-in Debian container/real Termux globally selectable |
| Container Runtime | Proot shared Debian container, backend and host isolated |
| Plugin Repository | Browse, search, install from GitHub sources |
| Development Tools | Creation wizard, code editor (Sora Editor), template export |
| Permission Management | App + plugin permissions, state persistence |
| Backup & Restore | Backup/restore plugins and configurations |
| UI Personalization | 38+ colors, gradient background, neumorphism style, glass effects |
| Multilingual Support | In-app language switching (Chinese/English) |
| Desktop Widgets | List widget (3x3) and 1x1 shortcut |
| External Content | System share/open-with relay (openWith) |
| Multi-Instance | Multiple independent plugin instances simultaneously |

### 1.2 Plugin Type Comparison

| Feature | Native Plugin | Web Plugin |
|---------|---------------|------------|
| Development Language | Kotlin/Java | HTML/CSS/JS |
| Performance | Best | Good |
| Hot Update | Requires recompilation | No compilation needed |
| Development Barrier | Higher | Lower |
| System API Access | Full access | Via JS interface (170+ APIs) |
| Data Storage | PluginContext API | UINPlugin Storage API |
| Backend Support | None | Unified startup command mode |

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
| Open Terminal | Launch terminal environment |
| Terminal Settings | Configure terminal font, color scheme, keyboard, etc. |
| Create Plugin | Unified entry, select frontend type and fill in backend startup command |
| Export Template | Export built-in packaged plugin templates (with README.txt description) |

> 💡 **Tip**: When creating plugins, you can choose from four modes: "Native UI", "Pure WebView", "WebView + Backend", "CUI Terminal". The backend runtime environment (built-in Debian container / real Termux) for WebView + Backend plugins is globally configured in "Backend Runtime Settings" on the "Manage" page.

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
| Backend Runtime Settings | Globally switch built-in Debian container / real Termux, idle timeout, etc. |
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
| Terminal | Open built-in terminal |
| Backend Settings | Open backend runtime settings page (BackendSettingsActivity) |
| UI Personalization | Open UI personalization settings page (UIConfigActivity) |

### 4.2 Usage

1. Long press the UIN Tool icon on the desktop
2. Select the desired function from the popup menu
3. The app automatically jumps to the corresponding page

### 4.3 Plugin Desktop Shortcuts

You can create desktop icon shortcuts for individual plugins via "Manage" > "Plugin Management" by clicking the **`+`** button on the right side of the plugin list item (see "10.7 Add Desktop Shortcut").

---

## V. Terminal Features (Based on PRoot)

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
apt update

# Install Python
apt install python3

# Install Node.js
apt install nodejs

# Install git
apt install git

# SSH to server
ssh user@hostname
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
4. If selecting WebView + Backend, fill in the **backend startup command** (default `sh scripts/start.sh`, customizable); the backend runtime environment (built-in Debian container / real Termux) is globally configured in "Backend Runtime Settings" on the "Manage" page
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

#### 12.5.1 Backend Implementation: Built-in Debian Container or Real Termux

| Option | Description | Suitable Scenario |
|--------|-------------|-------------------|
| **Built-in Debian container** (default) | Uses the app's built-in Debian container via PRoot, **no need to install anything** | Out-of-the-box, want to save trouble |
| **Real Termux** | Calls Termux installed on the device (`com.termux`) to run the backend, can use native Termux ecosystem | Needs pip/npm/apk and other complete package ecosystem |

- **Built-in Debian container**: Debian rootfs is built into the app (offline recovery from assets), no network installation required
- **Real Termux**: Requires Termux to be installed on the device with initialization completed, otherwise a guide will pop up automatically if startup fails

#### 12.5.2 Backend Environment (Real Termux Only)

| Option | Description |
|--------|-------------|
| **Termux Native** | Run directly in the Termux native environment |
| **Proot Container** | Run inside a Proot container (container name configurable, default `debian`) |

- Built-in Debian container **forces** direct PRoot execution, this option is not applicable

#### 12.5.3 Idle Auto Reclamation

The backend automatically stops after being idle for the set duration to avoid long-term resource usage; plugin activity requests refresh the timer. After exiting the plugin for the set duration, its backend is automatically cleaned up. Duration options include preset 3 / 5 / 10 / 15 minutes (default 5 minutes), or enter any number of minutes in the "Custom (minutes)" input box; selecting "**Unlimited**" means the backend will never be automatically reclaimed, only stopping when actively stopped.

- When stopping, the host first calls the HTTP `/stop` endpoint for **graceful shutdown**
- **Built-in Debian container**: Additionally terminates by process group `SIGKILL`
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

Real Termux (proot or native mode) uses a **single resident shared supervisor**: the container/session is only initialized once, and all plugin backends run as supervisor child processes. Subsequent plugin startup saves proot initialization overhead (cold startup about 5s). Built-in Debian container remains unchanged (each plugin has its own proot).

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

- **Built-in Debian container**: `proot --link2symlink -0 -r <debian_rootfs> -b /dev -b /proc -b /sys -b <pluginDir>:/plugins/<id> -- sh -lc "<startup command>"`
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

## XVIII. Code Editor

Since 6.2.0 the code editor is no longer just a draft surface inside the plugin wizard — it is a full standalone editor with a file tree, multiple tabs, direct disk read/write, find & replace, syntax highlighting and code formatting, and other apps can hand files to it through "Open with".

### 18.1 How to Open

| Entry | Description |
|---|---|
| Plugin development wizard | Tap "Open code editor" in the wizard; it edits the wizard's draft and writes back on save |
| "Open with" from other apps | File managers, browsers etc. open a file "with UIN Tool" and go straight to the editor |

Supported types: `text/*` (txt, csv, markdown, logs, ...) plus `application/json`, `application/xml`, `application/javascript`, `application/x-sh`, `application/x-yaml`. Both `file://` and `content://` sources are supported.

> **Note**: the system "Open with" chooser shows **two** UIN Tool icons. This is an intentional trade-off — the editor and the plugin dispatch page each declare their own `ACTION_VIEW`; both work, and picking the editor entry opens the editor.

Storage permission is requested on entry:

| Android version | How |
|---|---|
| Android 11 and above | "All files access" — jumps to the system settings page to enable it |
| Android 10 and below | Requests read / write external storage |

You can browse and view without granting (`content://` files never need storage permission), but you cannot write back — the status bar then shows "Read-only". Once granted, the file tree defaults to the external storage root so you can pick files immediately.

### 18.2 Layout

| Area | Contents |
|---|---|
| Top bar | ☰ file tree on the left; the action cluster reads, left to right: **undo / redo**, save, find, syntax selector (palette icon), ⋮ more-actions — six icons in a tight **40dp** layout (reduced spacing). **No title and no file name** (since batch 45; the title used to get squeezed into an ellipsis by the icons, and the file name lives in the tab strip anyway) |
| Tab bar | Multiple tabs; unsaved tabs carry a dot marker; tap × to close; **long-press a tab to open its full properties** |
| Information bar | Directly below the tab strip: `n/N lines`, `n bytes`, `n chars`, `n selected` — refreshed live with the cursor and edits |
| Search panel | Opened by ⋮ → Find or `Ctrl+F` |
| Editor area | Main region; a 28dp edge strip along the far-left screen edge — swipe right on it to pull out the file tree |
| Symbol bar | Quick-symbol strip at the bottom; enable it in the editor settings (off by default); it sits directly above the status bar |
| Status bar | **Bottom-left: page switcher `n/N`** (shown only for multi-page documents; tapping it opens a dropdown where you can type a page number); **right side: read-only badge, encoding button (encoding type shown in full, never truncated), ⚙ settings (pinned rightmost)**. **No file name here**; Ln/Col and the selection count moved to the information bar, the match count now lives in the search panel's status line, and the word-wrap toggle now lives in the editor settings. The right group never compresses, so ⚙ is never pushed off screen on narrow displays |
| File tree drawer | Slides in from the left; the ☰ icon at the top-left opens / closes it |

### 18.3 File Tree & Tabs

| Action | How |
|---|---|
| Open folder | FolderOpen icon at the top of the drawer → system folder picker; the chosen directory becomes the tree root. If the result cannot be turned into a readable directory the app now **reports the failure explicitly**, instead of silently doing nothing as in 6.1 |
| New file / folder | Created inside the selected directory; the name may contain `a/b` to create nested folders automatically |
| Rename / delete / properties | Tap the row's ⋮: rename, delete, or view **Properties** (name, path, type, size, lines, bytes, characters, modified time). **Tap any row** in the dialog to copy its value. Delete removes everything underneath and is **irreversible** |
| Long-press / long names | **Long-press** any file or folder row = tapping its trailing ⋮, opening the same menu (rename / delete / properties), anchored at the ⋮. An over-long file name is **clipped to one line** with an ellipsis by default so the ⋮ always stays visible; turn on **File tree multi-line names** (see 18.8) in the editor settings to wrap the name across lines with an adaptive row height |
| Long-press a tab | **Long-press** a file tab in the top tab strip to open that file's **full properties** dialog directly (same dialog as ⋮ → Properties); line/character counts are tallied on a background stream, and only byte size is reported for very large files |
| Switch files | Tapping a file opens it in a new tab; switching back and forth loses nothing |
| Close tab | Tabs with unsaved changes prompt before closing |

When no file is open, the editor shows a "No file open" empty state whose button expands the file tree directly.

### 18.4 Saving & File Access

| Action | Description |
|---|---|
| Save | Save icon in the top bar (or `Ctrl+S`), writes back to the original file **using the encoding detected when it was opened** — a GBK file is never silently re-saved as UTF-8 |
| Save as | **Long-press** the Save icon → the system "Save as" dialog, where you can pick both the **file name and the location**; the current tab switches to the new file |
| Exit confirmation | Pressing back with unsaved changes asks to Save / Discard / Cancel |
| Back-button layering | While the settings page, find panel, encoding picker or file-tree drawer is open, the system back button **closes them one layer at a time** (innermost first); only once all are closed does it reach "unsaved changes → exit" (in 6.1, backing out of settings exited the editor outright) |
| Encoding | The encoding button in the status bar → choose **Auto-detect** or a manual encoding. From 6.2.0 the picker lists the **device-supported** common set: UTF-8 / GB18030 / GBK / Big5 / UTF-16(LE/BE) / UTF-32(LE/BE) / Shift_JIS / EUC-JP / EUC-KR / ISO-8859-x / windows-125x / KOI8-R / TIS-620, etc. (encodings the device cannot decode are filtered out, so you can never pick one that silently falls back). **Picking a manual encoding immediately re-reads the document with it** and the status bar shows the chosen value |

**Auto-detect**: when a file is opened its bytes are inspected — BOM first, then a strict UTF-8 validation, then a GBK/GB18030 heuristic (legacy Chinese files), with ISO-8859-1 as the last resort. Through 6.1 every file was decoded as UTF-8, so GBK files came out as `锟斤拷`-style mojibake; 6.2.0 both detects them correctly and gives you a manual override.

**Size limit**: files over 16 MB now always open in **windowed mode** automatically (real pagination, 2000 lines per page — see 18.12); any other file that fits in the available memory can be opened, and a **memory pre-check runs before reading** (3.25× the file size plus a force-collected heap **whose concurrent sweep is allowed to finish before measuring** — ART returns from `System.gc()` early, and measuring mid-collection used to misjudge files that actually fit; the slack prevents passing the check and then dying halfway through the read, while the GC stops a document that was closed a moment ago from being mistaken for "file too large"). An oversized file reports "file too large, out of memory" right away instead of freezing the UI for ten-plus seconds (repeated GC triggered by an OOM) and only then failing. Re-opening the same file through "open with" **no longer re-reads it either** — the editor reuses the same instance and just switches back to the already-open tab, so a second attempt can never be judged "file too large". Keep **Large-file mode** on (see 18.8) when opening very large files so highlighting and sticky scroll do not slow down scrolling.

**Session restore**: if the system kills the process, coming back through the recents screen re-opens the last document instead of dropping you on the "No file open" empty state; plugin-wizard mode restores its whole draft set. If restoring fails (file deleted, out of memory, the sender's grant already revoked), the empty state **shows the actual failure reason** instead of a generic "No file open".

### 18.5 Find & Replace

⋮ → Find, or press `Ctrl+F`:

| Control | Effect |
|---|---|
| `Aa` | Match case |
| `.*` | Regular expression |
| `abc` | Whole word |
| `Prev` / `Next` | Jump between matches across the whole file |
| Status line | Above the list: progress / `N matches in whole file` / `No matches in whole file`; "All results" opens the full list when there are matches |
| Expand arrow | Reveals the Replace field and the "Replace all (whole file)" button |

In regex mode the replacement text supports `$1`, `$2` backreferences.

Searching and replacing always operate on the **whole file for every document** (paged huge files are scanned page by page with a streaming pass, everything else in a single in-memory pass; progress is visible and cancellable), no toggle needed: results are listed as `absolute line:column`, and tapping one selects and **highlights the match**. Replace has a single entry — "Replace all (whole file)": paged files are rewritten page by page into the on-disk write-back spool and only land on disk on save; fully-loaded files are rewritten in one in-memory pass (undo history resets; reaches disk when you save). Both ask for confirmation first (details in 18.12). The full-results list's scrollbar is now **tappable and draggable** for proportional jumping (20 dp hit area, thumb follows the finger while dragging); the "All results" dialog caps its list height relative to the screen (55%, still at most 480 dp in portrait) so it no longer overflows in landscape or on short screens.

### 18.6 More Actions Menu

| Group | Items |
|---|---|
| Edit | Undo, Redo (greyed out when no history), Cut, Copy, Paste, Select all |
| Line | Duplicate line, Cut line, Select line, Indent, Outdent |
| Navigate | Go to line (1-based), Go to document start, Go to document end |
| Other | Format code (see 18.10) |

### 18.7 Hardware Keyboard Shortcuts

Available with a hardware keyboard or the system split keyboard:

| Key | Action |
|---|---|
| `Ctrl+S` | Save |
| `Ctrl+F` | Open find |
| `Ctrl+G` | Go to line |
| `Ctrl+Z` | Undo |
| `Ctrl+Shift+Z` | Redo |
| `Ctrl+C` / `Ctrl+X` / `Ctrl+V` / `Ctrl+A` | Copy / Cut / Paste / Select all (system default; the editor does not intercept them) |

### 18.8 Editor Settings

Open the settings panel from ⚙ at the bottom-right of the status bar. All 28 items are described below.

**Appearance & Typography**

| Setting | Control | Default | Meaning |
|---|---|---|---|
| Theme | Button | dracula | The editor's color scheme. **Independent of the app's dark / light mode** — there is no such thing as a system "code editor theme". Several dark and light themes are bundled (dracula, one-dark-pro, github-light, ...) |
| Font size | Slider 10–24 | 14 | Body font size in sp; follows the system font scaling |
| Line spacing | Slider 0.8–2.0 | 1.0 | Vertical spacing **multiplier** between lines. Below 1 is tighter, above 1 is airier |
| Text width | Slider 0.8–1.3 | 1.0 | Horizontal stretch of characters, **without changing the font size**; raise it on wide screens to reduce horizontal scrolling |
| Ligatures | Switch | On | Renders `->`, `=>`, `!=` as single ligatures in monospace fonts that support them (e.g. Fira Code). **No visible change with the default font** — that is expected |

**Display & Assistance**

| Setting | Control | Default | Meaning |
|---|---|---|---|
| Line numbers | Switch | On | The line-number gutter on the left |
| Gutter divider | Switch | On | The vertical line between the line-number gutter and the text; when off the numbers sit flush against the code |
| Word wrap | Switch | Off | Wraps lines that exceed the screen width instead of scrolling horizontally |
| Highlight current line | Switch | On | Lays a background band across the line containing the caret |
| Highlight block | Switch | On | Shades the innermost code block (inside braces) containing the caret, making nesting easier to see |
| Block guide | Switch | On | Draws vertical indent guides; useful when aligning deeply nested code |
| Show whitespace | Switch | Off | Draws spaces, tabs and line-ending characters — handy for "why is this misaligned?" |
| Show placeholders | Switch | On | Renders `void`, `null` etc. in a more conspicuous style |
| Bracket matching | Switch | On | Highlights the matching bracket when you type or delete one. The pair is drawn with a thin **~1.5dp stroke** (sora's default is 1dp); the earlier 6.2.0 build zeroed the stroke colour, which left only an 11%-alpha background that was effectively invisible — it is visible again now, just thinner |
| Large-file mode | Switch | On | When the content exceeds roughly **2M characters**, automatically turns off syntax highlighting, sticky scroll and the block guide, and limits hex-color hints to the first 10,000 lines so scrolling stays smooth; **manually picking a syntax still keeps highlighting** (that is an explicit request). Turn it off and **every one of those measures is lifted immediately** (full syntax and hints return) — and the hint scan itself is now optimized: lines without `#` are skipped at **zero cost**, the scan **aborts the moment the editor closes**, and each file produces at most **50,000 highlights**, so full-scan mode no longer tanks performance. From 6.2.0 there is **no more "read-only over 32MB"** — any file that fits in memory is editable, and the read-only flag no longer sticks to files opened afterwards |
| File tree multi-line names | Switch | Off | How the file tree shows a name too long for the row: **Off** (default) clips it to a single line with an ellipsis so the trailing ⋮ button always stays visible; **On** wraps the name across lines with an adaptive row height. In both modes **long-pressing a row** equals tapping ⋮ (see 18.3) |

**Scrolling & Positioning**

| Setting | Control | Default | Meaning |
|---|---|---|---|
| Sticky scroll | Switch | On | Pins the declaration of the enclosing block (function / class header) to the top so you always know which function you are in while scrolling |
| Pinch to zoom | Switch | On | Two-finger pinch adjusts the font size. When off, pinching no longer changes the size; the file-tree drawer gesture is unaffected |
| Magnifier | Switch | On | Shows a magnifier above your finger while dragging a selection |
| Line:column panel | Switch | On | Shows a floating "Ln X, Col Y" bubble **while you drag the right-hand vertical scrollbar**. Note: the bubble only exists during that drag (sora's built-in behaviour) — it is **not** a caret-following status readout |
| Panel position | Segmented left/top/right/bottom/center | Center | The **corner where that bubble docks**; each option pins it to the corresponding screen position (top-left / top / right / bottom / center) and it stays put while you drag. 6.2.0 fix: anything other than left/right used to be forced back into FOLLOW mode, which always hugs the right edge — it looked like "nothing happened" when you picked an option |

**Input & Editing**

| Setting | Control | Default | Meaning |
|---|---|---|---|
| Auto completion | Switch | On | Pops up keyword suggestions while typing. Built-in word lists cover XML, HTML, CSS, Gradle, JSON, YAML and other config formats |
| Cursor blink | Switch | On | Whether the caret blinks |
| Selection popup | Switch | On | Shows a Cut / Copy / Paste popup after selecting text |
| Symbol bar | Switch | Off | Shows a horizontally scrollable strip of symbols (`{}()[];:,=+-*/<>&\|!?'"._@#$%`) at the bottom. Tapping one inserts it through exactly the same path as the soft keyboard, so tapping `(` auto-completes `)` and tapping `"` pairs quotes |
| Tab width | Segmented 2/4/8 | 4 | How many spaces a Tab key press or auto-indent occupies; the block guides follow it |
| Line separator | Segmented LF/CRLF/CR | LF | Which line ending **newly inserted lines** use from now on. It **does not rewrite line endings already in the document**. LF is for Unix/Linux, CRLF for Windows, CR for classic macOS |

### 18.9 Syntax Highlighting

- Tap the palette icon in the top bar to open the language list (50+ languages plus `Auto` and `Plain Text`)
- By default the language is inferred from the **file extension** and shown as `Auto (by extension)`
- Picking a language manually makes the current tab remember it and override the extension guess; switching back to `Auto` restores the guess
- Grammars still load in the background for the first few seconds after a cold start, so a file opened during that window may briefly have no highlighting; it appears automatically once loading finishes

### 18.10 Code Formatting

| Aspect | Description |
|---|---|
| Supported types | `json`, `json5`, `jsonc`, `yaml`, `yml`; **other types grey the menu item out** so there is no "tapped and nothing happened" |
| How JSON is handled | Token-preserving re-indentation — only indentation and post-colon spacing are recomputed; numbers, strings, escape spelling, key order, JSON5 single quotes and trailing commas are carried over verbatim |
| How YAML is handled | Only three provably safe things: block scalars (`\|` / `>`) preserved byte-for-byte, leading tabs expanded to spaces, trailing whitespace and blank runs cleaned. **Indentation levels are never reordered** |
| Validation | Invalid content (unbalanced braces, unterminated strings, ...) shows "Cannot format" and **leaves the document completely untouched** |
| Undo | One formatting run is a **single undo step**; one `Ctrl+Z` reverts everything |
| Why not XML | The platform XML serializer is a pretty-printer, not a value-preserving re-indenter: it normalizes attribute quotes, strips whitespace-only text nodes and may drop a DOCTYPE internal subset. That conflicts with "touch whitespace, never touch values". The XML this app deals with is mainly AndroidManifest and layout files, which Android Studio already formats |

### 18.11 Known Limitations

- **Git integration** is not provided yet


### 18.12 Windowed Mode (Very Large Files)

Files over 16 MB (as well as files rejected by the memory pre-check because the whole file does not fit in the heap) are no longer rejected with "File too large". Instead they are opened in **windowed mode** automatically — an Android-scaled take on EmEditor's "spill to disk + section window" architecture:

- **The whole file never enters memory**: opening streams one scan to build a tiny index of "byte offset of each page start + total line count" (only tens of KB even for 10 GB files); the editor only loads the **current page** (2000 lines) at a time;
- The info bar labels the character count with **"this page"** (that figure is the current page's characters, not the whole file); line numbers there are **absolute** (page × 2000 + in-page line). The editor's own gutter numbers are **page-relative** (each page restarts at 1);
- **The pager really switches pages**: edits on the current page go into a spool first, then the new page is read from disk. "Go to line" takes an **absolute line number** and jumps to the target page automatically;
- **Undo/redo and formatting apply to the current page only** (the undo stack resets on page switch). Find/replace **always operates on the whole file** regardless of paging: pages are scanned one by one with visible, cancellable progress, results are listed as `absolute line:column` and tapping one jumps to that page with the match highlighted. "Replace all (whole file)" rewrites page by page into an **on-disk spool** (memory stays at one page) that is streamed into the file on save; matches spanning the 2000-line page boundary are detected, **skipped and reported**, never half-replaced across the seam;
- **Switching tabs does not cancel a running replace**: it keeps going in the background — while it runs, the replaced tab is read-only and saving/page-switching/starting another replace are blocked (other tabs are unaffected); once it finishes, returning to the original tab shows the "replace finished" result banner. Only closing the replaced tab aborts the replace (the original file was never touched by it, so it stays intact);
- **Saving = original file + edited pages**, streamed together: unmodified pages are **byte-copied verbatim** (no decode round-trip), edited pages are re-encoded with the file's charset; the result is written to a temp file and atomically swapped in, then the page index is rebuilt. Saving runs on a background thread and survives leaving the editor ("save and exit" loses nothing);
- Changing the charset **rebuilds the index** with the new encoding and returns to page 1 (same semantics as a full reload, edits are overwritten);
- **Save As is not supported** in this mode (the whole document is not in memory — it could only write out the current page) — save to the original file instead. If index rebuilding fails after a save, the tab is locked read-only with a hint to reopen the file.


---

## XIX. FAQ

### 19.1 FAQ (Q1-Q35)

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
A: Click "Create Plugin" > Select "WebView + Backend" > Fill in backend startup command (default `sh scripts/start.sh`). The wizard auto-generates `scripts/start.sh` and `scripts/backend/server.py`; the backend runtime environment (built-in Debian container / real Termux) is globally configured in "Backend Runtime Settings" on the "Manage" page.

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

## XX. Contact Support

### 20.1 Contact Information

| Channel | Contact Info |
|---|---|
| Email | undefinedinvalidnull@outlook.com |
| GitHub | https://github.com/Undefined-Invalid-Null/UIN-Tool |
| Plugin Repository | https://github.com/UIN-Tool-Plugins |
| QQ Group | 511875883 |

---

| Item | Info |
|---|---|
| Document Version | 6.2.0 |
| Last Updated | October 16, 2026 |
| Corresponding App Version | v6.2.0 (Build 26) |

---

© 2026 UIN Team. All Rights Reserved.
