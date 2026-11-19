# UIN Tool

![Version](https://img.shields.io/badge/version-7.0.0-blue)
![Build](https://img.shields.io/badge/build-27-green)
![Android](https://img.shields.io/badge/Android-6.0%2B-brightgreen)
![License](https://img.shields.io/badge/license-MIT-orange)
![Kotlin](https://img.shields.io/badge/Kotlin-2.4.21-purple)
![Compose](https://img.shields.io/badge/Jetpack%20Compose-2026.09.00-blue)

## App Introduction

UIN Tool is an Android plugin framework application rebuilt with Kotlin + Jetpack Compose, allowing users to dynamically load and run third-party plugins. Whether it is native Java plugins or Web technology stack (HTML/CSS/JS) plugins, they can run seamlessly in UIN Tool. It provides a complete plugin ecosystem, including plugin development, management, runtime, permission control, **PRoot container backend**, and a built-in **dual-pane file manager**.

### Core Philosophy

- **Open**: Anyone can develop plugins, supporting native Java and Web technology stacks
- **Secure**: Plugin permission authorization, supports signature verification, prevents malicious plugins
- **Efficient**: Native performance, Web plugins support hot updates without recompilation
- **Easy to use**: Visual development wizard, create plugins without complex configuration
- **Flexible**: Supports grid/list view switching, supports category management
- **Modern**: Built on Jetpack Compose, Material 3 design language
- **Powerful**: Built-in PRoot Linux environment, supports Python/Node.js/PHP backends
- **Persistent**: Plugin data independently stored, user data automatically preserved on updates
- **Full file management**: dual-pane browsing, in-place archive extract and pack, image viewer, APK info, bookmarks and history, recursive search, deep links, side-by-side compare, type-coloured file icons
- **Unified Open With**: one default opener per suffix; four candidate categories — built-in / plugins / suggested / all — with the plugins category using the top search box

---

## Terminal Features (Based on PRoot)

UIN Tool **includes a complete Linux environment**, powered by [PRoot](https://github.com/proot-me/proot) for user-space Linux without root access.

### Terminal Features

| Feature | Description |
|---------|-------------|
| **Linux Distributions** | Debian (default), Ubuntu, Arch Linux, Alpine |
| **Package Managers** | APT, pacman, apk |
| **Development Tools** | gcc, clang, make, git, etc. |
| **Script Languages** | Python, Node.js, Ruby, Perl, etc. |
| **Text Editors** | vim, nano, emacs, etc. |
| **Network Tools** | curl, wget, openssh, etc. |
| **Multi-Session** | Multiple terminal sessions running simultaneously |

### Architecture

UIN Tool supports two backend modes:

1. **Built-in PRoot mode (default)**: uses the proot binary + rootfs directly, no external app required
2. **Real Termux mode**: compatible with an external Termux app for a more complete environment

### Terminal Use Cases

- **Development and debugging**: write and debug code directly on the Android device
- **Server management**: manage remote servers over SSH
- **Learning Linux**: experience a complete Linux environment without root
- **Automation scripts**: write Shell/Python scripts for automated tasks

### Termux Acknowledgments

> The terminal uses PRoot to provide a user-space Linux environment. The `terminal-emulator` and `terminal-view` modules are adapted from the [Termux](https://github.com/termux/termux-app) project. Thanks to the Termux team for their open-source contribution.

---

## File Manager (Dual Pane)

The Files entry in the bottom bar is a built-in file manager: two panes browse independently, with copy and move flowing between them.

| Capability | Description |
|------------|-------------|
| **Dual pane** | Each pane keeps its own browsing stack; the pane ratio can be dragged, locked, or reset on entry in settings |
| **Selection and actions** | Long-press to enter selection mode; copy / move / rename / compress / extract / share / hide / add bookmark / properties / delete |
| **Archives** | Browse zip / 7z / tar and gzip, bzip2, xz, zstd outer layers in place; writable archives support write-back and read-only ones are marked; zip AES-256 passwords and split volumes supported |
| **Search** | Recursive search, regex, content matching, size filters; results expand to a full page with "Search in results" |
| **Open With** | Four categories — built-in / plugins / suggested / all; built-in capabilities are a collapsible grid; one default opener per suffix; the plugins category uses the top search box and can hand a local file straight to a plugin |
| **Viewers** | Image viewer (zoom, paging, GIF), APK info page (permissions, signature, install, share) |
| **Compare** | Select two files or two folders to see differences side by side |
| **Navigation** | Bookmarks and history, frequent-locations sidebar, `uin://files` deep links |

File icons are coloured by type (images, video, audio, documents, code, archives and more each have their own colour) instead of a wall of grey icons.

---

## Version Information

### Current Version: v7.0.0 (Build 27)

| Item | Info |
|------|------|
| Version Name | 7.0.0 |
| Version Code | 27 |
| Update Date | November 19, 2026 |
| Minimum Android | 6.0 (API 23) |
| Target Android | 9 (API 28) |
| Compile SDK | 37 |
| Architecture | arm64-v8a |

### Version History

| Version | Build | Date | Highlights |
|---------|-------|------|------------|
| v7.0.0 | 27 | 2026-11-19 | Dual-pane file manager (in-place archive browse and write, image viewer, APK info, compress/extract, bookmarks/history/search, deep links, side-by-side compare), Open With merged into a single entry; full-text search reworked with nine new syntaxes; plugin permissions moved to a two-level page; per-style configuration isolation; toolchain raised to AGP 9.1.1 / Kotlin 2.4.21 / Gradle 9.3.1 / compileSdk 37 / Compose BOM 2026.09.00 / Coil 3.6.3 |
| v6.2.0 | 26 | 2026-10-31 | Code editor focus (file tree / tabs / direct disk access / ACTION_VIEW external entry, Unified UI, four rounds of syntax-highlighting root-cause fixes, sora 0.24.6 + Kotlin 2.3.10) |
| v6.1.0 | 25 | 2026-10-07 | Full defect sweep (startup / permissions / main-thread jank / dark theme / plugin host security / backup rollback / error visibility / build deduplication) |
| v6.0.0 | 24 | 2026-09-26 | Removed the Termux dependency, PRoot container system, container management UI, rootfs export compression level |
| v5.7.0 | 23 | 2026-08-31 | Multi-source repository aggregation, plugin icon cache, incremental CI builds, UI polish |
| v5.6.0 | 22 | 2026-08-28 | Neumorphism style, language switching, translucency controls |
| v5.5.0 | 21 | 2026-08-22 | Crash fixes, clipboard pseudo-permissions, wizard completion, real-Termux shared supervisor |
| v5.4.0 | 20 | 2026-08-10 | Plugin openWith relay, plugin multi-instance |
| v5.3.0 | 19 | 2026-07-31 | Unified UI components, colour picker, category management, Shizuku/Dhizuku |
| v5.2.0 | 18 | 2026-07-24 | Packaging fixes, update logic, Alpine acceleration |
| v5.1.0 | 17 | 2026-07-17 | Backend runtime refactor, CUI terminal improvements |
| v5.0.0 | 16 | 2026-07-10 | Full internationalization, dynamic theme engine, bottom navigation refactor |
| v4.5.0 | 15 | 2026-07-03 | Proot container runtime, custom backends, pre-start commands |
| v4.4.0 | 13 | 2026-06-24 | Plugin data persistence, permission system, code editor upgrade |
| v4.2.0 | 11 | 2026-06-17 | Termux backend integration, plugin notices |
| v4.0.0 | 9 | 2026-06-10 | Kotlin migration, Compose UI, MVVM architecture |

> For the full changelog see [CHANGELOG](CHANGELOG.md).

---

> **Signing key change (from 7.0.0)**: this release uses a new signing key. Installing over an older build will be rejected ("App not installed" or a signature mismatch); uninstall the old version first. Uninstalling clears app data, so back it up beforehand.

## Feature List

### Implemented Features

| Module | Feature | Status | Notes |
|--------|---------|--------|-------|
| Startup | SplashActivity | Done | Splash screen, permission check |
| Startup | Onboarding | Done | First-launch guide |
| Startup | App icon shortcuts | Done | Long-press menu, icons follow the theme colour |
| Startup | Permission dialog | Done | Storage permission explanation |
| Updates | Automatic update check | Done | Checked on startup |
| Updates | Force update | Done | Enforced when the third tag segment is 1 |
| Updates | Version ignore | Done | Ignore normal versions |
| Updates | In-app download | Done | Foreground service with progress |
| GitHub acceleration | Mirror management | Done | Dedicated page |
| GitHub acceleration | Built-in mirrors | Done | 4 defaults |
| GitHub acceleration | Custom mirrors | Done | Add manually |
| GitHub acceleration | Import/export | Done | TXT format |
| GitHub acceleration | CDN acceleration | Done | Toggleable |
| Terminal | Terminal emulator | Done | Based on PRoot |
| Terminal | Linux environment | Done | APT / pacman / apk |
| Terminal | Multi-session | Done | Several sessions at once |
| Backend | Python backend | Done | Starts Python automatically |
| Backend | Node.js backend | Done | Starts Node.js automatically |
| Backend | PHP backend | Done | Starts PHP automatically |
| Backend | Binary backend | Done | Choose an executable |
| Backend | Health check | Done | /health endpoint |
| Backend | Process management | Done | Automatic start/stop |
| Plugin engine | Dynamic DEX loading | Done | DexClassLoader |
| Plugin engine | Resource isolation | Done | Independent Context |
| Plugin engine | Lifecycle management | Done | Full lifecycle |
| Plugin engine | WebView support | Done | HTML/CSS/JS |
| Plugin engine | JS Bridge API | Done | 170+ APIs |
| Plugin engine | Network API | Done | HTTP GET/POST/PUT/DELETE |
| Plugin engine | File system API | Done | Read/write/delete/copy/move |
| Plugin engine | Storage API | Done | KV + JSON + batch operations |
| Plugin engine | Data persistence | Done | Independent data/ directory |
| Plugin management | Import plugins | Done | TPK import |
| Plugin management | Export plugins | Done | ZIP export |
| Plugin management | Batch import | Done | Multiple TPK files |
| Plugin management | Plugin set import | Done | ZIP batch import |
| Plugin management | Category management | Done | Add/edit/delete categories (single/batch) |
| Plugin management | Signature verification | Done | SHA-256 TOFU |
| Plugin management | Plugin notice | Done | notice field |
| Repository | GitHub integration | Done | Official and third-party sources |
| Plugin permissions | Permission declaration | Done | plugin.json |
| Plugin permissions | Permission check | Done | Checked before launch |
| Plugin permissions | Permission request | Done | Grouped requests |
| Plugin permissions | Permission state | Done | Visual state |
| Plugin permissions | Persisted grants | Done | State persistence |
| Plugin permissions | Material 3 dialog | Done | Unified style |
| Plugin permissions | Shizuku/Dhizuku | Done | Official APIs |
| Documentation | Documentation center | Done | Central document management |
| Dev tools | Native plugin wizard | Done | Kotlin/Java plugins |
| Dev tools | Web plugin wizard | Done | Web plugin creation |
| Dev tools | CUI terminal wizard | Done | CUI plugin creation |
| Dev tools | Code editor | Done | Sora Editor engine |
| Dev tools | Syntax highlighting | Done | 30+ languages |
| Dev tools | Editor themes | Done | 28+ themes |
| Dev tools | Template export | Done | Export template docs |
| File manager | Dual-pane browsing | Done | Independent stacks, adjustable ratio |
| File manager | In-place archive browsing | Done | zip / 7z / tar and outer layers |
| File manager | Compress and extract | Done | zip AES-256, split volumes |
| File manager | Recursive search | Done | Regex, content match, size filters |
| File manager | Image viewer | Done | Zoom, paging, GIF |
| File manager | APK info | Done | Permissions, signature, install, share |
| File manager | Side-by-side compare | Done | Two files or two folders |
| File manager | Open With | Done | Built-in / plugins / suggested / all, per-suffix default |
| File manager | Bookmarks and history | Done | Sheet and sidebar |
| File manager | Deep links | Done | `uin://files` |
| UI personalization | Colour configuration | Done | 38+ adjustable colours |
| UI personalization | Colour picker | Done | Visual picker + RGB/Alpha |
| UI personalization | Gradient background | Done | Single/multi-colour gradients + direction |
| UI personalization | Corner radius | Done | 7 options |
| UI personalization | Size configuration | Done | Buttons/spacing/icons |
| UI personalization | Font configuration | Done | Size/weight |
| UI personalization | Import/export | Done | Configuration backup |
| Desktop widgets | 3x3 widget | Done | Shows 9 plugins |
| Desktop widgets | 1x1 shortcut | Done | Single plugin shortcut |

---

## Technology Stack Details

| Technology | Version | Purpose |
|------------|---------|---------|
| Kotlin | 2.4.21 | Main development language |
| Jetpack Compose | BOM 2026.09.00 | Declarative UI (foundation / ui / animation 1.12.1) |
| Compose Material 3 | 1.4.0 | Material 3 components |
| Android SDK | API 37 | Android framework |
| OkHttp | 4.12.0 | HTTP client |
| Sora Editor | 0.24.7 | Code editor |
| Coil | 3.6.3 | Image loading (image viewer, plugin icons) |
| Apache Commons Compress | 1.28.0 | Main archive engine (zip / 7z / tar and outer compression formats) |
| zip4j | 2.11.6 | zip encryption, Zip64, split volumes |
| xz | 1.10 | xz / lzma / 7z-LZMA backend |
| zstd-jni | 1.5.7-21 | zstd decompression backend |
| brotli dec | 0.1.2 | brotli decompression backend |

---

## Quick Start

### Install the App

1. Download the latest APK from Releases
2. Enable "Install unknown apps" on the device
3. Install the APK

### First Launch

1. A storage permission dialog appears on startup
2. Tap "Grant" to allow storage access
3. The onboarding guide appears on first launch
4. Read it and tap "Get Started"

### Use the Terminal

1. Tap the "Dev" tab at the bottom
2. Tap "Open Terminal"
3. On first use the built-in rootfs is unpacked offline (bundled with the app, no network needed)

Common terminal commands:

```bash
# Update package sources
apt update

# Install Python
apt install python3

# Install git
apt install git

# Install Node.js
apt install nodejs
```

### Use the File Manager

1. Tap the "Files" tab at the bottom
2. Browse with the two panes; drag the divider to adjust the ratio
3. Long-press an item for selection mode, or use the long-press menu for copy / move / compress / open with and more
4. Long-press a file → "Open with" to choose a built-in capability, a system app, or an installed plugin

### Install Plugins

Option 1: install from the repository

1. Tap the "Repo" tab at the bottom
2. Browse available plugins
3. Tap "Install"

Option 2: import locally

1. Copy the .tpk file to the phone
2. Go to "Manage" → "Plugin Management"
3. Tap "Import" and choose the file

### Create a Plugin

1. Tap the "Dev" tab at the bottom
2. Tap "Create Plugin"
3. Choose the frontend type (native UI / pure WebView / WebView + backend / CUI terminal)
4. Fill in the plugin information in the wizard
5. Tap "Finish" to generate the project files

### Use the Code Editor

1. Open the code editor from the plugin creation wizard, or through "Open with" in the file manager
2. The left sidebar shows the project files
3. Tap a file to edit it
4. Syntax highlighting for 30+ languages
5. Undo/redo, find and replace, code formatting

### Plugin Data Storage

Web plugins (JavaScript):

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

// Storage statistics
const stats = JSON.parse(UINPlugin.getStorageStats());
console.log('KV count:', stats.kvCount);
console.log('File count:', stats.fileCount);
```

Native plugins (Kotlin):

```kotlin
val pctx = PluginContext(context, pluginDir)
pctx.putString("key", "value")
val value = pctx.getString("key")
pctx.writeFile("data.txt", "content")
```

---

## FAQ

Q: How do I install plugins?
A: Three ways: install directly from the "Repo" page, import a .tpk file, or use batch import / plugin set import.

Q: What is the difference between Web and native plugins?
A: Web plugins use HTML/CSS/JS, need no compilation and take effect immediately after editing; native plugins use Java, perform better, but need compilation.

Q: How do I develop my own plugin?
A: Tap "Dev" → "Create Plugin" at the bottom and follow the wizard.

Q: How do I customize UI colours and corner radius?
A: Go to "Manage" → "UI Personalization" to adjust 38+ colours and 7 corner radius options.

Q: How do I hand a file from the file manager to a plugin?
A: Long-press the file → "Open with" → search and pick the plugin in the plugin section. The file is not copied into `.incoming/`; the plugin reads the original through `openData.filePath`.

Q: Why is a plugin missing from "Open with"?
A: Only installed plugins that declare `openWith` in `plugin.json` and accept the current file type (MIME / file) are listed.

Q: Where is plugin data stored?
A: Each plugin's data lives in /storage/emulated/0/UIN_Tool/plugins/{pluginId}/data/, with KV data in SharedPreferences.

Q: Will updating a plugin lose data?
A: No. The data/ directory is preserved automatically on update.

Q: Are permission states persisted?
A: Yes. Granted/blocked states are persisted per plugin; the "Do not ask again" choice is persisted per plugin too.

Q: Which APIs do Web plugins support?
A: 170+ APIs covering device info, sensors, location, network, file system, storage, permissions, UI, clipboard, vibration, notifications, events, system actions and more.

Q: Which languages does the code editor support?
A: 30+ languages including Java, Kotlin, Python, JavaScript, TypeScript, HTML, CSS, JSON, XML, Markdown, Shell, SQL, Go, Rust, PHP, Ruby, Swift, Dart, Lua, Scala, Perl, Haskell, Elixir, Erlang, Clojure, Groovy, Dockerfile, Makefile, INI, Properties, TOML, YAML and more.

Q: How do I export plugin data?
A: Web plugins can use UINPlugin.exportData() to export all data as JSON.

Q: How do I use the terminal?
A: Tap "Dev" → "Open Terminal" at the bottom. The rootfs is bundled with the app and unpacked offline on first use; no network download is needed.

---

## Open Source License

This project is licensed under the MIT License.

### Third-party Components

- **zip4j** (`net.lingala.zip4j`) — Apache-2.0.
- **7-Zip-JBinding-4Android** (`com.sorrowblue.sevenzipjbinding`, containing the 7-Zip 16.02 engine) —
  the engine is **LGPL-2.1+** (with the unRAR additional terms), the binding layer is Apache-2.0.
  Source: the sources package for Maven Central coordinate `com.sorrowblue.sevenzipjbinding:7-Zip-JBinding-4Android:16.02-2.03.1`;
  upstream <https://github.com/borisbrodski/sevenzipjbinding>, <https://www.7-zip.org/>.
  This project does not modify the library; the `.so` ships as a separate file in the APK and can be replaced.
- Other Apache-2.0 / MIT components (commons-compress, junrar, Coil, apksig, etc.) are listed in the dependency comments in `app/build.gradle`.

---

## Contributors

| Contributor | Role | Contribution |
|-------------|------|--------------|
| UIN Team | Core development | Architecture and core features |
| Yi Zhi Dian Bi | Feature development | 1x1 desktop widget |
| Termux Team | Upstream project | Terminal emulator core |

---

## Contact

| Channel | Address |
|---------|---------|
| GitHub | https://github.com/Undefined-Invalid-Null/UIN-Tool |
| Email | undefinedinvalidnull@outlook.com |
| Plugin repository | https://github.com/UIN-Tool-Plugins |
| QQ group | 511875883 |

---

© 2026 UIN Team. All Rights Reserved.
