# UIN Tool

![Version](https://img.shields.io/badge/version-5.7.0-blue)
![Build](https://img.shields.io/badge/build-23-green)
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

### Current Version: v5.7.0 (Build 23)

| Item | Info |
|------|------|
| Version Number | 5.7.0 |
| Version Code | 23 |
| Update Date | August 31, 2026 |
| Minimum Android Version | 6.0 (API 23) |
| Target Android Version | 9 (API 28) |
| Compile SDK Version | 36 (Android 16) |
| Architecture | arm64-v8a |

### Version History

| Version | Build | Date | Highlights |
|---------|-------|------|------------|
| v5.7.0 | 23 | 2026-08-31 | Multi-source repo aggregation, plugin icon caching, incremental CI build, UI refinements |
| v5.6.0 | 22 | 2026-08-28 | Neumorphism style, multilingual switching, translucent effect control |
| v5.5.0 | 21 | 2026-08-22 | Crash fixes, clipboard pseudo-permissions, development wizard completion, real Termux shared supervisor |
| v5.4.0 | 20 | 2026-08-10 | Plugin openWith relay, plugin multi-instance |
| v5.3.0 | 19 | 2026-07-31 | Unified UI components, color picker, category management, Shizuku/Dhizuku |
| v5.2.0 | 18 | 2026-07-24 | Packaging refinement, update logic, Alpine speedup |
| v5.1.0 | 17 | 2026-07-17 | Backend runtime refactoring, CUI terminal optimization |
| v5.0.0 | 16 | 2026-07-10 | Full i18n, dynamic theme engine, bottom nav refactoring |
| v4.5.0 | 15 | 2026-07-03 | Proot container runtime, custom backend, pre-start command |
| v4.4.0 | 13 | 2026-06-24 | Plugin data persistence, permission system, code editor upgrade |
| v4.2.0 | 11 | 2026-06-17 | Termux backend integration, plugin notice |
| v4.0.0 | 9 | 2026-06-10 | Kotlin migration, Compose UI, MVVM architecture |

> For detailed changelogs, see [CHANGELOG](changelog.md).

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
