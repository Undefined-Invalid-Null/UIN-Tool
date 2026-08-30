# UIN Tool Development Documentation

## Version Information

| Item | Info |
|------|------|
| Document Version | 5.6.0 |
| Corresponding App Version | v5.6.0 (Build 22) |
| Last Updated | August 28, 2026 |

---

## 📑 Table of Contents

### I. Quick Start
- [1.1 Create Plugin](#11-create-plugin)
- [1.2 Configure Plugin Information](#12-configure-plugin-information)
- [1.3 Write Code](#13-write-code)
- [1.4 Compile and Package](#14-compile-and-package)
- [1.5 Import and Run](#15-import-and-run)

### II. Plugin Types
- [2.1 Comparison Table](#21-comparison-table)
- [2.2 How to Choose](#22-how-to-choose)

### III. Native Plugin Development
- [3.1 Basic Structure](#31-basic-structure)
- [3.2 Supported Android Controls](#32-supported-android-controls)
- [3.3 Layout Examples](#33-layout-examples)
- [3.4 Accessing Plugin Resources](#34-accessing-plugin-resources)
- [3.5 Plugin Data Storage API](#35-plugin-data-storage-api)

### IV. Web Plugin Development (Without Backend)
- [4.1 Directory Structure](#41-directory-structure)
- [4.2 plugin.json Configuration](#42-pluginjson-configuration)
- [4.3 HTML Template Example](#43-html-template-example)
- [4.4 JavaScript Example](#44-javascript-example)

### V. Web Plugin Development (With Backend)
- [5.1 Overview](#51-overview)
- [5.2 Backend Runtime Settings (Global)](#52-backend-runtime-settingsglobal)
  - [5.2.1 Backend Implementation](#521-backend-implementation)
  - [5.2.2 Backend Environment (Real Termux Only)](#522-backend-environment-real-termux-only)
  - [5.2.3 Idle Auto Reclamation](#523-idle-auto-reclamation)
  - [5.2.4 Real Termux Initialization Command](#524-real-termux-initialization-command)
  - [5.2.5 Real Termux Shared Supervisor](#525-real-termux-shared-supervisor)
  - [5.2.6 How the Runtime Environment is Used](#526-how-the-runtime-environment-is-used)
- [5.3 Startup Command and Backend Files](#53-startup-command-and-backend-files)
- [5.4 plugin.json Configuration](#54-pluginjson-configuration)
- [5.5 Frontend-Backend Communication](#55-frontend-backend-communication)
- [5.6 Backend API Specification](#56-backend-api-specification)

### VI. CUI Terminal Plugin Development (New in v4.5.0)
- [6.1 What is a CUI Plugin](#61-what-is-a-cui-plugin)
- [6.2 Directory Structure and plugin.json](#62-directory-structure-and-pluginjson)
- [6.3 Create CUI Plugin (Wizard)](#63-create-cui-pluginwizard)
- [6.4 Terminal Script Development](#64-terminal-script-development)
- [6.5 Runtime Flow and Lifecycle](#65-runtime-flow-and-lifecycle)
- [6.6 CUI and Backend/Proot Relationship](#66-cui-and-backendproot-relationship)

### VII. Plugin Data Persistent Storage (New in v4.4.0)
- [7.1 Overview](#71-overview)
- [7.2 Data Directory Structure](#72-data-directory-structure)
- [7.3 Web Plugin Storage API](#73-web-plugin-storage-api)
- [7.4 Native Plugin Storage API](#74-native-plugin-storage-api)
- [7.5 Data Migration](#75-data-migration)
- [7.6 Data Version Management](#76-data-version-management)

### VIII. Permission System (Enhanced in v4.4.0)
- [8.1 Permission Interaction Model](#81-permission-interaction-model)
- [8.2 Permission State Management](#82-permission-state-management)
- [8.3 Actual Permission Request Flow](#83-actual-permission-request-flow)
- [8.4 Permission Declaration](#84-permission-declaration)
- [8.5 Permission Types](#85-permission-types)

### IX. Plugin Notice Feature
- [9.1 Overview](#91-overview)
- [9.2 Configuration Method](#92-configuration-method)
- [9.3 User Interaction](#93-user-interaction)

### X. PluginInterface Detailed Reference
- [10.1 Method Description](#101-method-description)
- [10.2 Complete Implementation Example](#102-complete-implementation-example)
- [10.3 Plugin Multi-Instance (New in v5.4.0)](#103-plugin-multi-instancev540-new)

### XI. JavaScript API Complete Reference (v4.5.0)
- [11.1 Basic API](#111-basic-api)
- [11.2 Storage API](#112-storage-api)
- [11.3 File System API](#113-file-system-api)
- [11.4 Network Request API](#114-network-request-api)
- [11.5 Device Info API](#115-device-info-api)
- [11.6 Sensor API](#116-sensor-api)
- [11.7 System API](#117-system-api)
- [11.8 Permission API](#118-permission-api)
- [11.9 Backend Communication API](#119-backend-communication-api)
- [11.10 Data Statistics API](#1110-data-statistics-api)

### XII. Packaging and Importing
- [12.1 Packaging Methods](#121-packaging-methods)
- [12.2 File Structure](#122-file-structure)
- [12.3 plugin.json Complete Fields](#123-pluginjson-complete-fields)
  - [12.3.4 External Content Reception (openWith, New in v5.4.0)](#1234-external-content-receptionopenwithv540-new)

### XIII. Publishing to Plugin Repository
- [13.1 Repository Requirements](#131-repository-requirements)
- [13.2 Publishing Steps](#132-publishing-steps)

### XIV. Terminal Features
- [14.1 Overview](#141-overview)
- [14.2 Terminal Features](#142-terminal-features)
- [14.3 Common Commands](#143-common-commands)

### XV. UI Personalization Development
- [15.1 Color System](#151-color-system)
- [15.2 Color Configuration Items](#152-color-configuration-items)
- [15.3 Shape Configuration](#153-shape-configuration)
- [15.4 Effects Configuration (Updated in v5.6.0)](#154-effects-configurationv560-updated)
- [15.5 Multilingual Support (New in v5.6.0)](#155-multilingual-supportv560-new)

### XVI. Debugging Tips
- [16.1 Log Output](#161-log-output)
- [16.2 Viewing Runtime Logs](#162-viewing-runtime-logs)
- [16.3 WebView Remote Debugging](#163-webview-remote-debugging)

### XVII. FAQ
- [17.1 Q1-Q15](#xviifaq)

### XVIII. Best Practices
- [18.1 Naming Conventions](#181-naming-conventions)
- [18.2 Performance Optimization](#182-performance-optimization)
- [18.3 Data Storage Best Practices](#183-data-storage-best-practices)
- [18.4 Security](#184-security)
- [18.5 Version Management](#185-version-management)

### XIX. Technical Support
- [19.1 Contact Information](#191-contact-information)

---

## I. Quick Start

### 1.1 Create Plugin

1. Open the UIN Tool App
2. Click the "**Dev**" tab in the bottom navigation bar
3. Click the "**Create Plugin**" button
4. Select frontend type:
   - **Native UI**: Android View native interface
   - **Pure WebView**: HTML/CSS/JS only, no backend
   - **WebView + Backend**: HTML/CSS/JS + backend service
   - **CUI Terminal**: Full-screen terminal running scripts (new in v4.5.0)
5. If selecting "WebView + Backend", fill in the **backend startup command** in the wizard (default `sh scripts/start.sh`); the backend runtime environment is globally configured in "Backend Runtime Settings" on the "Manage" page (built-in Termux / real Termux)
6. Complete configuration according to the wizard

### 1.2 Configure Plugin Information

The wizard "Basic Information" page can configure the following fields (all correspond to fields in `plugin.json`):

| Field | Description | Example | Required |
|------|-------------|---------|----------|
| Plugin ID | Unique identifier, reverse domain name format | com.example.myplugin | Yes |
| Plugin Name | Display name in the list | My Plugin | No |
| Author | Developer name | John Doe | No |
| Description | Plugin functionality description | This is a sample plugin | No |
| Plugin Notice | Notice shown on first open (notice) | Welcome! | No |
| Version Number | Numeric version, for version comparison | 1 | Yes |
| Version Name | Display version number | 1.0.0 | Yes |
| Main Class Name | Full path of the entry class (native plugin) | com.example.MainPlugin | Yes |
| Entry File | Web plugin entry (Web plugin) | web/index.html | Yes |
| Permissions | Permissions declared by the plugin (popup multi-select) | See 8.4 | No |
| Minimum Host Version | Minimum host version number | 1 | No |
| Category | Plugin category name | Tools | No |
| Update URL | Plugin update check URL | https://.../plugin.json | No |
| Backend Startup Command | Web+backend plugin startup command (executed with `sh -lc`) | sh scripts/start.sh | Web+Backend Yes |
| Backend Timeout | Backend ready wait timeout (seconds) | 30 | No |
| Health Check Path | Backend ready detection endpoint | /health | No |
| Receive External Content | Whether to appear in system share / "Open with other apps" selection (openWith) | Off | No |
| Receiver Name | openWith relay page display name (empty uses plugin name) | Writing Assistant | No |
| MIME Types | File types supported by openWith (comma-separated) | text/*,application/pdf | No |
| Receive Types | openWith accepts text / links / files | All | No |

> Icon, plugin code, and resource files are configured in subsequent wizard steps. `signature` is maintained by the host, do not write manually.
>
> Since v5.5.0, wizard fields are streamlined: no longer provides "Max Memory / Max CPU Time / Max Concurrent Tasks (resource limits), Dependencies, API Level, Backend Keep-Alive" input fields (these fields are not written to wizard-generated plugin.json; `backendMaxRetries`/`backendLogLevel`/`backendEnv` are reserved fields, see 12.3.3). "Plugin Notice (notice)" is retained.

### 1.3 Write Code

Based on the selected plugin type, write the corresponding code. See each section below for details.

### 1.4 Compile and Package

**Native Plugins (Current Status)**
- In-app compilation is temporarily unavailable: the `plugin.dex` generated by the wizard is placeholder text and cannot be loaded
- It is recommended to use Web plugins, or compile a real `plugin.dex` on PC and place it in the plugin directory

**Web Plugins (Recommended)**
- No compilation needed: changes to HTML/CSS/JS take effect immediately
- The wizard automatically generates blank template files

**Web Plugins with Backend**
- The wizard generates `scripts/start.sh` (startup command) and `scripts/backend/server.py` (backend service, built-in http.server)
- No additional compilation needed
- Since v5.2.0, the packager **recursively packages the entire project directory**: `web/`, `scripts/`, `scripts/backend/server.py`, `start.sh`, and any resources are all included in the TPK, no need to manually place backend files

### 1.5 Import and Run

1. Click "**Manage**" > "**Plugin Management**" at the bottom
2. Click "**Import**" and select the generated TPK file
3. Wait for the import to complete
4. Click the plugin to run on the "**Tools**" page

---

## II. Plugin Types

### 2.1 Comparison Table

| Feature | Native Plugin | Pure Web Plugin | Web + Backend Plugin | CUI Terminal Plugin |
|---------|---------------|-----------------|---------------------|---------------------|
| Development Language | Kotlin/Java | HTML/CSS/JS | HTML/CSS/JS + Startup Command (e.g., Python) | Python/Shell Scripts |
| UI Development Method | Code dynamic creation | HTML layout | HTML layout | Full-screen terminal |
| Development Efficiency | Medium | High | High | High |
| Runtime Performance | High | Medium | Medium | High |
| Hot Update | Requires recompilation | No compilation needed | No compilation needed | No compilation needed |
| Backend Support | None | None | Unified startup command mode (`backendStartCommand`), runtime environment globally configured | Optional (see 6.6) |
| Data Persistence | PluginContext | UINPlugin API | UINPlugin API | Handled within scripts |
| Learning Curve | Requires Android knowledge | Frontend knowledge only | Frontend + Backend knowledge | Script knowledge only |
| Compilation Method | Requires compilation | No compilation needed | No compilation needed | No compilation needed |
| Suitable Scenarios | Need to access system APIs | Quick prototyping / existing Web projects | Need backend computation | Command-line tools / scripts |

### 2.2 How to Choose

| Scenario | Recommended Type |
|----------|-----------------|
| Need to access Android system APIs | Native plugin |
| Quick prototyping | Web plugin |
| Existing Web project | Web plugin |
| Need backend computation or data processing | Web + Backend plugin |
| Need persistent data storage | Web plugin (using setStorage) |
| Need to call Linux commands | Web + Python/Node.js |
| Command-line tools / script automation / interactive REPL | CUI terminal plugin |

---

## III. Native Plugin Development

### 3.1 Basic Structure

```kotlin
package com.example

import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.UIN.Tool.plugin.PluginInterface

class MainPlugin : PluginInterface {

    private var context: Context? = null
    private var rootView: View? = null
    private var clickCount = 0

    override fun onCreateView(
        context: Context,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        this.context = context
        val appContext = context.applicationContext

        val layout = LinearLayout(appContext).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 50, 50, 50)
        }

        val title = TextView(appContext).apply {
            text = "My Plugin"
            textSize = 24f
            setTextColor(0xFF37474F.toInt())
            setPadding(0, 0, 0, 20)
        }

        val counterText = TextView(appContext).apply {
            text = "Click count: 0"
            textSize = 16f
            setTextColor(0xFF666666.toInt())
            setPadding(0, 0, 0, 20)
        }

        val button = Button(appContext).apply {
            text = "Click Me"
            setBackgroundColor(0xFF37474F.toInt())
            setTextColor(0xFFFFFFFF.toInt())
            setOnClickListener {
                clickCount++
                counterText.text = "Click count: $clickCount"
                Toast.makeText(context, "Clicked $clickCount times", Toast.LENGTH_SHORT).show()
            }
        }

        layout.addView(title)
        layout.addView(counterText)
        layout.addView(button)

        rootView = layout
        return rootView
    }

    override fun onResume() { }
    override fun onPause() { }
    override fun onDestroy() { rootView = null }
    override fun onBackPressed(): Boolean = false
    override fun onSaveInstanceState(): Bundle? = null
}
```

### 3.2 Supported Android Controls

| Control | Description | Common Methods |
|---------|-------------|----------------|
| TextView | Text display | setText(), setTextSize(), setTextColor() |
| EditText | Text input | getText(), setHint() |
| Button | Button | setText(), setOnClickListener() |
| ImageView | Image display | setImageResource(), setImageBitmap() |
| LinearLayout | Linear layout | setOrientation(), setGravity() |
| RelativeLayout | Relative layout | addRule() |
| FrameLayout | Frame layout | Stacked views |
| ScrollView | Scroll view | Wraps content |
| ProgressBar | Progress bar | setProgress(), setVisibility() |
| CheckBox | Checkbox | setChecked(), isChecked() |
| Switch | Toggle | setChecked(), isChecked() |

### 3.3 Layout Examples

```kotlin
// Linear layout (vertical)
val layout = LinearLayout(appContext).apply {
    orientation = LinearLayout.VERTICAL
    layoutParams = ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    )
}

// Linear layout (horizontal)
val rowLayout = LinearLayout(appContext).apply {
    orientation = LinearLayout.HORIZONTAL
    gravity = Gravity.CENTER_VERTICAL
}
```

### 3.4 Accessing Plugin Resources

The `context` parameter in `onCreateView` is actually `PluginContext` (inherits from `ContextWrapper`), which can be directly cast to access the plugin directory, plugin resources, and data:

```kotlin
// Get plugin directory (absolute path of plugin installation directory, do not use context.filesDir)
val pctx = context as PluginContext
val pluginDir = pctx.getPluginDir()                 // /storage/emulated/0/UIN_Tool/plugins/{pluginId}
val pluginDataDir = pctx.getPluginDataDir()         // .../data/ (file sandbox, auto-created)
val pluginCacheDir = pctx.getPluginCacheDir()       // .../cache/ (auto-created)

// Read files in plugin installation directory
val configFile = File(pctx.getPluginFilePath("config.json"))
if (configFile.exists()) {
    val content = configFile.readText()
}

// Read images (icon.png is in the plugin installation directory)
val iconFile = File(pctx.getPluginFilePath("icon.png"))
if (iconFile.exists()) {
    val bitmap = BitmapFactory.decodeFile(iconFile.absolutePath)
    imageView.setImageBitmap(bitmap)
}

// Plugin resources (res/ and installation directory mounted via AssetManager, directly accessible)
val res = pctx.resources               // Redirected to plugin resources
val assets = pctx.assets                // Contains plugin directory + plugin res/
```

> ⚠️ `PluginContext` overrides `getDataDir()`/`getCacheDir()` (deprecated aliases returning plugin data/ and cache/), and also overrides `getAssets()`/`getResources()` (redirected to plugin directory). It does **not** override `getFilesDir()` — returns the host app's internal files directory, **not** the plugin directory. Do not use it to locate plugin files.

### 3.5 Plugin Data Storage API

The `context` received in `onCreateView` is already `PluginContext`, no need to construct it yourself; the following APIs are all based on this instance:

```kotlin
// Get PluginContext (in onCreateView, use the passed context directly)
val pctx = context as PluginContext

// ============ KV Storage (SharedPreferences: plugin_data_{pluginId}) ============
pctx.putString("username", "JohnDoe")
val username = pctx.getString("username", "Guest")

pctx.putInt("score", 100)
val score = pctx.getInt("score", 0)

pctx.putLong("longKey", 123456789L)          // Long integer
val longVal = pctx.getLong("longKey", 0L)

pctx.putBoolean("isLoggedIn", true)
val isLoggedIn = pctx.getBoolean("isLoggedIn", false)

pctx.putFloat("floatKey", 3.14f)             // Float
val floatVal = pctx.getFloat("floatKey", 0f)

val json = JSONObject().apply {
    put("theme", "dark")
    put("fontSize", 14)
}
pctx.putJSON("config", json)
val config = pctx.getJSON("config")

pctx.remove("temp_data")                      // Delete key
val exists = pctx.contains("username")        // Check key
val keys = pctx.getAllKeys()                  // All keys
val entries = pctx.getAllEntries()            // All key-value pairs
pctx.clearAll()                               // Clear all KV

// ============ File Storage (sandbox root = plugin data/) ============
pctx.writeFile("notes.txt", "Hello World")    // Write text (auto-checks disk space)
val content = pctx.readFile("notes.txt")      // Read text, returns null if not exists
pctx.writeFileBytes("bin.dat", byteArrayOf(1, 2, 3))  // Write bytes
val bytes = pctx.readFileBytes("bin.dat")     // Read bytes

pctx.deletePluginFile("notes.txt")            // Delete file
val files = pctx.listPluginFiles()            // List file names under data/
val fileExists = pctx.fileExists("notes.txt")
val size = pctx.getPluginFileSize("notes.txt")

// ============ Cache Management ============
pctx.clearPluginCache()                       // Delete and recreate cache/

// ============ Full Data Cleanup ============
pctx.deleteAllPluginData()                    // Clear KV + delete data/ + cache/

// ============ Data Statistics ============
val stats = pctx.getStorageStats()
println("KV count: ${stats.kvCount}")
println("File count: ${stats.fileCount}")
println("Total size: ${stats.totalFileSize}")
println("Cache size: ${stats.cacheSize}")

// ============ Data Version Management ============
pctx.setDataVersion(2)                        // Write version number
val dataVersion = pctx.getDataVersion()       // Read (default 0)
pctx.markDataMigrated()                       // Mark as migrated
val migrated = pctx.isDataMigrated()

// ============ Permission State Management ============
// Since v5.5.0, the old permission_state (0/1/2 single value) API is deprecated.
// Permission status is now determined per permission (granted / blocked).
// Old interfaces are retained for compatibility but no longer drive the interaction flow.
// Please use PluginPermissionManager's
// checkPluginPermission / setPermissionBlocked / getPluginPermissionStatus.
// Example (old interface, deprecated):
val state = pctx.getPermissionState()         // 0/1/2 (@Deprecated)
pctx.setPermissionState(1)  // 1=granted (@Deprecated)
pctx.shouldShowPermissionDialog()             // true when state==0 (@Deprecated)
pctx.getPermissionStateDescription()          // "Unauthorized"/"Authorized"/"Denied" (@Deprecated)
pctx.clearPermissionState()                   // Reset to unauthorized (@Deprecated)
```

---

## IV. Web Plugin Development (Without Backend)

### 4.1 Directory Structure

```
your-plugin/
├── plugin.json          # Plugin configuration file (required)
├── icon.png             # Plugin icon (recommended 128x128)
└── web/                 # Web resource directory (required)
    ├── index.html       # Main page (required)
    ├── style.css        # Style file (optional)
    └── script.js        # JavaScript file (optional)
```

### 4.2 plugin.json Configuration

```json
{
    "pluginId": "com.example.webplugin",
    "version": 1,
    "versionName": "1.0.0",
    "minHostVersion": 1,
    "name": "Web Plugin Example",
    "author": "Developer Name",
    "description": "This is a Web plugin example",
    "notice": "Welcome to the Web plugin!",
    "icon": "icon.png",
    "mainClass": "",
    "apiLevel": 21,
    "uiType": "web",
    "entry": "web/index.html",
    "permissions": "android.permission.INTERNET,android.permission.VIBRATE"
}
```

### 4.3 HTML Template Example

```html
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>My Web Plugin</title>
    <link rel="stylesheet" href="style.css">
</head>
<body>
    <div class="container">
        <h1>My Plugin</h1>
        <button onclick="showToast()">Show Toast</button>
        <button onclick="closePlugin()">Close Plugin</button>
        <button onclick="saveData()">Save Data</button>
        <button onclick="loadData()">Load Data</button>
    </div>
    <script src="script.js"></script>
</body>
</html>
```

### 4.4 JavaScript Example

```javascript
// ============ Basic Features ============
function showToast() {
    UINPlugin.callHost('toast', 'Hello from WebView!');
}

function closePlugin() {
    UINPlugin.callHost('finish', '');
}

// ============ Storage API ============
function saveData() {
    UINPlugin.setStorage('username', 'JohnDoe');
    UINPlugin.setStorageInt('score', 100);
    UINPlugin.setStorageJSON('config', JSON.stringify({theme: 'dark'}));
    UINPlugin.callHost('toast', 'Data saved');
}

function loadData() {
    const name = UINPlugin.getStorage('username');
    const score = UINPlugin.getStorageInt('score', 0);
    const config = JSON.parse(UINPlugin.getStorageJSON('config'));
    UINPlugin.callHost('toast', `User: ${name}, Score: ${score}`);
}

// ============ File Operations ============
function saveFile() {
    UINPlugin.writeFile('notes.txt', 'Hello World');
    UINPlugin.callHost('toast', 'File saved');
}

function readFile() {
    const content = UINPlugin.readFile('notes.txt');
    UINPlugin.callHost('toast', 'Content: ' + content);
}

// ============ Batch Operations ============
function batchSave() {
    const data = {
        key1: 'value1',
        key2: 'value2',
        key3: 'value3'
    };
    UINPlugin.setStorageBatch(JSON.stringify(data));
}

function batchLoad() {
    const keys = JSON.stringify(['key1', 'key2', 'key3']);
    const result = JSON.parse(UINPlugin.getStorageBatch(keys));
    console.log('Batch read:', result);
}

// ============ Storage Statistics ============
function getStats() {
    const stats = JSON.parse(UINPlugin.getStorageStats());
    UINPlugin.callHost('toast', `KV: ${stats.kvCount}, Files: ${stats.fileCount}`);
}

// ============ Lifecycle ============
document.addEventListener('DOMContentLoaded', () => {
    console.log('Web plugin loaded');
});

window.addEventListener('resume', () => { console.log('Plugin resumed'); });
window.addEventListener('pause', () => { console.log('Plugin paused'); });
window.addEventListener('destroy', () => { console.log('Plugin destroyed'); });
```

---

## V. Web Plugin Development (With Backend)

### 5.1 Overview

Web plugins can start Termux backend services, providing computation, data processing, system command execution, and other capabilities. Since v5.1.0, the backend runtime architecture has been refactored to a **unified "startup command" mode**:

· All backend plugins use `backend: "other"` + `backendStartCommand` as the single path, no longer differentiated by language interpreter (python/node/php/...)
· The runtime environment is **globally configured** by the user within the software (built-in Termux / real Termux), plugins do not need to worry about it
· The host executes `sh -lc "<startup command>"` and injects `$PORT`, `$PLUGIN_ID`, `$PLUGIN_DIR`, `$WORK_DIR` and other environment variables
· Legacy backends (`backend: "python"` etc.) are automatically migrated to startup command mode on loading, no changes needed for published plugins

### 5.2 Backend Runtime Settings (Global)

Click "**Backend Runtime Settings**" on the "Manage" page to enter the standalone settings page, which can **globally configure the runtime environment for all backend plugins**, persisted in `uin_backend_prefs`, taking effect for all Web + Backend / CUI plugins.

> ⚠️ Since v5.2.0, "Backend Runtime Settings" has been changed from a popup to a **standalone page** (`BackendSettingsActivity`), only exists on the management page; the development page no longer provides an entry point.

#### 5.2.1 Backend Implementation

| Setting | Options | Description |
|---------|---------|-------------|
| **Backend Implementation** | **Built-in Termux** (default) | Uses the app's **built-in lightweight Termux** (no need to install anything), forces **Proot shared Alpine container** (fixed container name `alpine`) to run the plugin backend, achieving environment isolation |
| | **Real Termux** | Calls the externally installed **Termux** (`com.termux`) `RUN_COMMAND` service to run the plugin backend, suitable for scenarios needing native Termux ecosystem (pip/npm/apk, etc.) |

- **Built-in Termux**: No network needed for first installation; Alpine rootfs is restored offline from app assets (about 19MB, one-time decompression); first installation time does not involve network
- **Real Termux**: Requires Termux to be installed on the device and initialization completed once (see 5.2.4 initialization command), otherwise a guide will pop up if startup fails

#### 5.2.2 Backend Environment (Real Termux Only)

| Setting | Options | Description |
|---------|---------|-------------|
| **Backend Environment** | **Termux Native** | Run startup command directly in Termux native environment |
| | **Proot Container** | Run in a Proot container (e.g., `alpine`, `ubuntu`, etc.), container name configurable, must be installed first in Termux using `proot-distro install <containerName>` |

- Selecting Proot container requires filling in **container name** (default `alpine`); use `proot-distro list` to view installed containers
- Built-in Termux **forces** Proot Alpine container, this setting is not applicable

#### 5.2.3 Idle Auto Reclamation

| Setting | Options | Description |
|---------|---------|-------------|
| **Idle Reclamation Timeout** | 3 / 5 / 10 / 15 minutes (default 5) / Unlimited | Backend automatically stops after being idle for this duration to save resources; active requests refresh the timer; supports custom arbitrary minute values; selecting "Unlimited" means never automatically reclaimed |

- When stopping, the host first calls the agreed HTTP `/stop` endpoint for graceful shutdown (recommended to implement in the startup script, see 5.6)
- **Built-in Termux**: Additionally terminates by process group `SIGKILL`
- **Real Termux**: Idle reclamation is managed uniformly by the shared supervisor (see 5.2.5), with independent timeout recursive process tree killing per plugin based on `idle/<key>.start` startup timestamps, **does not depend on the plugin implementing `/stop`**; the host only does port detection and state cleanup

#### 5.2.4 Real Termux Initialization Command

When selecting Real Termux, the settings page displays an "**Initialization Command**" card at the bottom; click the copy icon in the upper right corner to copy with one click (command is uniformly generated by `BackendConfig.buildRealTermuxSetupCode()`, sharing the same implementation as the plugin runtime guide prompt):

```sh
mkdir -p ~/.termux; grep -q '^allow-external-apps=true' ~/.termux/termux.properties 2>/dev/null || echo 'allow-external-apps=true' >> ~/.termux/termux.properties; termux-setup-storage; termux-reload-settings 2>/dev/null || true
```

This command completes the following in order:
1. Writes `allow-external-apps=true` in `~/.termux/termux.properties` (allows external apps to launch Termux via `RUN_COMMAND`)
2. Executes `termux-setup-storage` to authorize storage access
3. `termux-reload-settings` reloads configuration

> If startup fails, the host will automatically detect missing items and provide corresponding guidance (`allow-external-apps`, `termux-setup-storage`, `proot-distro install`, `RUN_COMMAND` permission).

#### 5.2.5 Real Termux Shared Supervisor

Real Termux (proot or native mode) uses a **single resident shared supervisor**: the container/session is only initialized once, and all plugin backends run as supervisor child processes. Subsequent plugin startup saves proot initialization overhead (cold startup about 5s). Built-in Termux (alpine, about 2s) remains unchanged (each plugin has its own proot).

- Communication protocol (control directory `<plugins_root>/.uin/`): `cmd/<key>.cmd` (startup command), `pid/<key>` (backend PID), `stop/<key>` (stop request), `idle/<key>` (idle minutes), `idle/<key>.start` (startup timestamp), `alive` (supervisor alive marker), `host_alive` (host heartbeat, touch every 30s, supervisor auto-exits on 300s timeout), `shutdown` (exit marker), `keep_alive` (background keep-alive marker)
- proot startup: `proot-distro login <container> --bind '<plugins_root>:/plugins' -- sh -lc 'sh /plugins/.uin/supervisor.sh /plugins'`
- Native startup: `sh '<plugins_root>/.uin/supervisor.sh' '<plugins_root>'`
- Idle reclamation: supervisor independently times out and recursively kills process trees per plugin based on `idle/<key>.start` startup timestamps; `kill -0 $pid` detects process liveness
- Supervisor stays resident during host lifetime; host writes `shutdown` marker on exit, supervisor auto-exits
- Software preheats supervisor (`prewarm`) in the background on startup, also triggered when saving backend settings
- Backend settings page adds "Shared Scheduler" status card (RUN_COMMAND permission + supervisor alive status)
- **Background Keep-Alive** (optional): When enabled, supervisor no longer exits when host process is killed, combined with battery optimization exemption + notification bar + Shizuku/Dhizuku permissions to maintain background survival

#### 5.2.6 How the Runtime Environment is Used

After a plugin is opened, the host selects the execution path based on global settings:

- **Built-in Termux**: `proot-distro login alpine --bind <pluginDir>:/plugins/<id> -- sh -lc "<startup command>"`, plugin directory is bind-mounted read-only into the container
- **Real Termux + Termux Native**: `/bin/bash -lc "<startup command>"` (working directory = plugin directory)
- **Real Termux + Proot Container**: `proot-distro login <containerName> --bind <pluginDir>:/plugins/<id> -- sh -lc "<startup command>"`

All three paths inject `$PORT`, `$PLUGIN_ID`, `$PLUGIN_DIR`, `$WORK_DIR` environment variables via `sh -lc`, plugins do not need to be aware of the runtime environment.

### 5.3 Startup Command and Backend Files

Backend plugins generated by the wizard contain the following files:

```
your-plugin/
├── plugin.json
├── icon.png
├── web/
│   └── index.html          # Frontend page (scripts already inlined, no longer generates script.js)
└── scripts/
    ├── start.sh            # Startup command entry (host executes with sh -lc)
    └── backend/
        └── server.py       # Backend service example (reads $PORT, contains /health, /stop endpoints)
```

`scripts/start.sh` template (rendered by `backend/start.sh.tmpl`):

```sh
#!/usr/bin/env sh
# Host has injected environment variables: PORT (dynamic port), PLUGIN_ID, PLUGIN_DIR, WORK_DIR
set -e
cd "$(dirname "$0")"
# ---- Dependency detection: dynamically find interpreter, environment-agnostic (Termux uses pkg, container uses apk) ----
if ! command -v python3 >/dev/null 2>&1; then
    echo "[start.sh] python3 not found, installing..."
    pkg install python -y 2>/dev/null || apk add python3 -y 2>/dev/null || {
        echo "[start.sh] failed to install python3, exit"
        exit 1
    }
fi
echo "[start.sh] starting backend on 127.0.0.1:${PORT:-8000}"
exec python3 scripts/backend/server.py
```

`scripts/backend/server.py` template (rendered by `backend/server.py.tmpl`, built-in `http.server`, no third-party dependencies):

```python
#!/usr/bin/env python3
import json, os, threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

PORT = int(os.environ.get("PORT", "8000"))
PLUGIN_ID = os.environ.get("PLUGIN_ID", "")

class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        if self.path == "/health":
            return self._json({"status": "ok", "plugin": PLUGIN_ID})
        if self.path == "/stop":
            return self._stop()
        return self._json({"path": self.path, "method": "GET"})
    def do_POST(self):
        if self.path == "/stop":
            return self._stop()
        length = int(self.headers.get("Content-Length", 0) or 0)
        body = self.rfile.read(length).decode("utf-8", "ignore") if length else ""
        return self._json({"path": self.path, "method": "POST", "body": body})
    def _stop(self):
        self._json({"status": "stopping"})
        threading.Thread(target=self.server.shutdown, daemon=True).start()
    def _json(self, data):
        payload = json.dumps(data, ensure_ascii=False).encode("utf-8")
        self.send_response(200)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(payload)))
        self.end_headers()
        self.wfile.write(payload)
    def log_message(self, *args):
        pass

if __name__ == "__main__":
    print(f"[server.py] listening on 127.0.0.1:{PORT}")
    ThreadingHTTPServer(("127.0.0.1", PORT), Handler).serve_forever()
```

### 5.4 plugin.json Configuration

```json
{
    "pluginId": "com.example.pythonbackend",
    "version": 1,
    "versionName": "1.0.0",
    "uiType": "web",
    "entry": "web/index.html",
    "backend": "other",
    "backendStartCommand": "sh scripts/start.sh",
    "backendStartEntry": "scripts/start.sh",
    "backendAutoStart": true,
    "backendTimeout": 30,
    "backendHealthCheck": "/health"
}
```

| Field | Description |
|-------|-------------|
| `backend` | Fixed as `"other"` |
| `backendStartCommand` | **Startup command**: host executes with `sh -lc` in the plugin directory; defaults to `sh scripts/start.sh` if empty |
| `backendStartEntry` | Relative path of the startup script within the plugin directory (default `scripts/start.sh`) |
| `backendAutoStart` | Whether to automatically start the backend when opening the plugin |
| `backendTimeout` | Ready wait timeout (seconds) |
| `backendHealthCheck` | Health check endpoint path (must have leading `/`, default `/health`) |

> Legacy fields `backendPort`, `backendEntry`, `backendBinary`, `backendPreCommand`, etc. are no longer used by the new workflow; `migrateLegacyBackend()` automatically converts them to `backendStartCommand` when the plugin is loaded (completed in memory, not written back to the plugin file).

### 5.5 Frontend-Backend Communication

The frontend uniformly calls through `UINPlugin.callBackendApi` proxy, the host sends HTTP requests to `http://127.0.0.1:<dynamicPort>/<path>`, the frontend does not need to worry about the actual port:

**Callback Mechanism (Must Read)**

The fourth parameter `callbackId` of `callBackendApi(path, method, body, callbackId)` is a **string** (not a callback function). The correct usage is to first register the callback function to `window.UINPluginCallbacks[callbackId]`; the host returns with a **JSON string** (like `{"success": true, "data": "..."}`), the JS side needs `JSON.parse` to parse:

```javascript
function callBackend() {
    const callbackId = 'cb_' + Date.now();
    window.UINPluginCallbacks = window.UINPluginCallbacks || {};
    window.UINPluginCallbacks[callbackId] = function (res) {
        const data = JSON.parse(res);          // {success: bool, data: string}
        console.log('Backend response:', data);
        if (data.success) {
            // data.data is the backend response body string, may need JSON.parse again
        } else {
            console.error('Backend error:', data.data);
        }
        delete window.UINPluginCallbacks[callbackId];   // Clean up after use
    };
    UINPlugin.callBackendApi('/hello', 'GET', '', callbackId);
}
```

- `method`: `GET` / `POST` / `PUT` / `DELETE` (other values treated as GET)
- `body`: Request body for POST/PUT, host sends as `application/json`, pass `''` which becomes `'{}'`
- When backend is not ready, directly calls back `{"success": false, "data": "Backend not ready"}`
- When backend is ready, the host calls `window._onBackendReady(port)`; status can also be queried with `UINPlugin.getBackendStatus()` (returns `running:{port}` / `starting` / `unknown`)

> ℹ️ The example page generated by `simple_index.html.tmpl` includes the above call (`callBackend()` calls `/hello` and displays the response), scripts are already inlined in `index.html`, no longer generates `web/script.js`. Historical template `web/script.js` still uses `window.UINPluginCallbacks` to register callbacks per this contract.

### 5.6 Backend API Specification

The host communicates with the backend via HTTP; the backend must follow these conventions:

**Health Check**

- Path: Specified by `backendHealthCheck` (default `/health`), must have leading `/`
- Requirement: HTTP 200 response indicates readiness
- Detection method: Host first performs TCP port detection, then sends a **GET** request to the health check endpoint after the port is open

**Request Paths**

- `GET /`: Welcome page / status
- `POST /api/<endpoint>`: Business endpoint, request body is JSON, response is JSON (recommended convention)
- `GET|POST /stop`: Graceful shutdown endpoint called when the host stops the backend (real Termux processes cannot be terminated by the host, can only exit through this endpoint)

**Common Conventions**

- Response headers should include `Access-Control-Allow-Origin: *` (needed for frontend fetch direct connection)
- Listening address must be `127.0.0.1`
- Port is dynamically assigned by the host, injected via `$PORT` (no need to fix the port in the plugin)

**Environment Variable Injection**

| Variable | Description |
|----------|-------------|
| `PORT` | Actual listening port (dynamically assigned by host) |
| `PLUGIN_ID` | Plugin ID |
| `PLUGIN_DIR` | Backend directory: native is the plugin directory, inside proot container is `/plugins/{pluginId}` |
| `WORK_DIR` | Same as `PLUGIN_DIR` (host injects with `WORK_DIR=$baseDir`); built-in process-level default is `/storage/emulated/0/UIN_Tool`, but will be overridden by inline `export` in container/command |
| `PYTHONUNBUFFERED` | `1` (Python output flushes immediately) |

**Environment Variable Injection Method (By Runtime Environment)**

- **Built-in Termux (proot Alpine container)**: Host injects via `ProcessBuilder` process environment (including `PORT`/`PLUGIN_ID`/`PLUGIN_DIR`/`WORK_DIR` and Termux-specific variables), then enters the container with `proot-distro login alpine --bind <pluginDir>:/plugins/<id> -- sh -lc "..."`; container inherits process environment. Backend actually runs inside the container, `PLUGIN_DIR`/`WORK_DIR` are `/plugins/{pluginId}`.
- **Real Termux Native**: Host launches `com.termux.RUN_COMMAND`, since the intent has **no environment variable channel**, the host inlines environment variables into the `sh -lc` command string (`export PORT=...; export PLUGIN_ID=...; cd <pluginDir> && <startup command>`).
- **Real Termux proot container**: Same as above, inlined into `sh -lc` then enters the container via `proot-distro login <container> --bind <pluginDir>:/plugins/<id> -- sh -lc "..."`.

**Runtime Environment Constraints (Real Termux)**

- Needs `allow-external-apps=true` set in real Termux (`.termux/termux.properties`), `termux-setup-storage` executed to grant storage permission, and `com.termux.permission.RUN_COMMAND` granted. Plugins are on shared storage (`/storage/emulated/0/UIN_Tool/plugins/`), Termux needs to be able to read this directory.
- Real Termux processes **cannot be terminated by the host across apps**; stopping the backend can only rely on the backend responding to the `/stop` endpoint — therefore real Termux backends **must implement `/stop`** (built-in Termux additionally uses `SIGKILL` by process group as fallback beyond `/stop`).
- Real Termux backend cold startup is slower (especially proot containers), ready timeout has been relaxed to `max(backendTimeout, 60/120)`, the host will prompt when timeout occurs but **will not automatically kill already-started processes** (built-in version cleans up if the process has already exited).

**Backend Lifecycle**

- Plugin opens → Host auto-starts backend (`backendAutoStart: true`); real Termux managed by shared supervisor (see 5.2.5), first startup takes about 5s to initialize container/session, subsequent plugin startups are immediately available
- Ready detection: first TCP port detection (500ms connection timeout), then **GET** health check after port is open (not HEAD, to avoid 501), 200 response indicates readiness; retries every 200ms until timeout
- Plugin closes → Host calls `GET http://127.0.0.1:<port>/stop` for graceful shutdown (built-in Termux additionally terminates by process group; real Termux killed recursively by supervisor by PID)
- Idle reclamation: Backend automatically stops after exceeding "idle reclamation timeout" (global setting, default 5 minutes, presets 3/5/10/15 minutes or custom arbitrary minutes; set to "Unlimited" to never auto-reclaim) without being called; WebView direct requests also refresh the timer to prevent premature reclamation. **Real Termux**: Managed by shared supervisor with independent timeout recursive process tree killing per plugin based on `idle/<key>.start` startup timestamps (does not depend on plugin implementing `/stop`); host only does port detection and state cleanup. When "Unlimited" is selected, no idle files are written, backend only stops when actively stopped


## VI. CUI Terminal Plugin Development (New in v4.5.0)

### 6.1 What is a CUI Plugin

**CUI plugins** (`uiType: "cui"`, Command-line User Interface) are plugins whose frontend is presented as a **full-screen terminal**: when the plugin opens, the host no longer renders pages or WebView, but directly launches a real terminal window (based on the built-in Termux engine) to execute the startup command you configured in the plugin directory.

- Suitable for: command-line tools, script automation, interactive interpreters, service consoles, etc.
- The plugin page only shows a placeholder prompt; the real interface is the full-screen terminal
- The terminal can use the host's built-in bash, python3, and other commands
- Scripts can obtain plugin information via `export PLUGIN_ID=... PLUGIN_DIR=$(pwd)` in `backendPreCommand`

### 6.2 Directory Structure and plugin.json

CUI plugin structure is very simple:

```
plugin.tpk
├── plugin.json        # Required
├── icon.png           # Optional
└── scripts/           # Scripts directory
    └── script.py      # Terminal startup script (example)
```

`plugin.json` key fields:

```json
{
    "pluginId": "com.example.cuitest",
    "version": 1,
    "versionName": "1.0.0",
    "minHostVersion": 1,
    "name": "CUI Terminal Test",
    "author": "UIN Tool",
    "description": "Demonstrates CUI mode",
    "icon": "icon.png",
    "mainClass": "",
    "apiLevel": 21,
    "uiType": "cui",
    "entry": "",
    "backend": "",
    "backendRuntime": "",
    "backendPort": 0,
    "backendEntry": "",
    "backendAutoStart": false,
    "backendKeepAlive": false,
    "backendPreCommand": "export PLUGIN_ID=com.example.cuitest PLUGIN_DIR=$(pwd); python3 scripts/script.py"
}
```

| Field | Description |
|-------|-------------|
| `uiType` | Fixed as `"cui"`, determines the host uses full-screen terminal flow |
| `entry` | CUI plugins have no page entry, leave empty |
| `backendPreCommand` | **Startup command**: executed each time the plugin opens in the plugin directory with `bash -lc "<this command>"`. Must use `export PLUGIN_ID=... PLUGIN_DIR=$(pwd)` to inject environment variables, because terminal sessions do not auto-inject them |
| `backend` | Optional. Fill `""` for pure terminal; can also be combined with backend fields to simultaneously start an HTTP backend |
| `backendRuntime` | `"termux"` (default) or `"proot"` (execute in Alpine container) |

### 6.3 Create CUI Plugin (Wizard)

1. "Dev" > "Create Plugin" > Select "**CUI Terminal (Command-Line Interface)**"
2. Wizard has 4 steps: Basic Info > **Edit Terminal Script** > Generate Project Files > Done
3. In the "Edit Terminal Script" step, you can click "Open Code Editor" to modify `scripts/script.py`
4. Basic Info step provides "**Startup Command**" input box:
   - Label: Startup Command (executed in terminal when plugin opens)
   - Default: `python3 scripts/script.py`
   - Empty defaults to `python3 scripts/script.py`
   - Recommended to add `export PLUGIN_ID=... PLUGIN_DIR=$(pwd)` prefix to obtain plugin environment information

The wizard auto-generates a `scripts/script.py` example script (with `code.interact()` interactive interpreter):

```python
#!/usr/bin/env python3
# -*- coding: utf-8 -*-
# CUI plugin terminal example script, runs in terminal after plugin opens.
import os

print("=" * 48)
print(" CUI Plugin Terminal Started")
print("=" * 48)
print("Plugin ID : " + os.environ.get("PLUGIN_ID", "?"))
print("Plugin Dir: " + os.getcwd())
print("-" * 48)
print("Type exit or Ctrl-D to end session.")

import code
code.interact(banner="", local=locals())
```

### 6.4 Terminal Script Development

Scripts run with the plugin directory (`<PLUGIN_DIR>/<pluginId>`) as the working directory; information can be obtained via environment variables:

| Environment Variable | Description | How to Obtain |
|----------------------|-------------|---------------|
| `PLUGIN_ID` | Plugin ID | Must `export PLUGIN_ID=...` in startup command |
| `PLUGIN_DIR` | Plugin directory | Must `export PLUGIN_DIR=$(pwd)` in startup command |
| `PWD` | Current directory | Terminal defaults to plugin directory |

> Terminal sessions do **not** auto-inject `PLUGIN_ID` / `PLUGIN_DIR`, must manually export in `backendPreCommand` (as shown in the built-in cuitest plugin).

Python example (with interactive REPL):

```python
#!/usr/bin/env python3
import os
import code

pid = os.environ.get("PLUGIN_ID", "?")
pdir = os.environ.get("PLUGIN_DIR", os.getcwd())
print("Plugin ID :", pid)
print("Plugin Dir:", pdir)
print("Type exit or Ctrl-D to end.")
code.interact(banner="", local=locals())
```

Shell example (run commands then exit):

```bash
# backendPreCommand example:
# export PLUGIN_ID=com.example.tool PLUGIN_DIR=$(pwd); bash scripts/run.sh
echo "Plugin Dir: $PWD"
ls -la
python3 scripts/tool.py
```

The terminal session automatically closes after the script ends (if the script does not end, the terminal remains open).

### 6.5 Runtime Flow and Lifecycle

1. User opens CUI plugin → `PluginHostActivity` displays placeholder view: "Opening full-screen terminal to execute command..."
2. Host selects execution environment based on global "Backend Runtime Settings": built-in Termux uses environment pipeline (Termux ready → Alpine ready, if `backendRuntime: "proot"` is configured); real Termux directly calls `RUN_COMMAND`
3. Through `RunCommandService`, starts **full-screen terminal session** in the plugin directory with `bash -lc "<startup command>"` (`backendPreCommand`), foreground directly launches `TermuxActivity` / `com.termux` full-screen terminal (no longer depends on overlay permission); when startup command is empty, opens interactive login Shell with `bash -l`
4. Terminal session survives independently of the plugin page: closes when script/Shell exits; closing the plugin page **does not** force-kill the terminal (session managed by TermuxService)

### 6.6 CUI and Backend/Proot Relationship

- **Pure CUI**: `backend` left empty, only terminal, no HTTP backend
- **CUI + Backend**: `backend: "other"` + `backendStartCommand` + `backendAutoStart: true`, host starts backend in background while launching terminal
- **CUI + Proot**: `backendRuntime: "proot"`, startup command executes inside Alpine container, suitable for scenarios needing isolated environment
- **CUI + other**: `backend: "other"` means host does not auto-start backend, startup command is typically a resident service launched by the terminal itself

Note: CUI's `backendPreCommand` is a "startup command executed each time the plugin opens", semantically different from Web plugin backend's `backendStartCommand` (host executes in background with `sh -lc`, used to start HTTP service).

---

## VII. Plugin Data Persistent Storage

### 7.1 Overview

v4.4.0 adds a complete plugin data persistent storage system; each plugin has an independent storage space, and data is automatically preserved when the plugin is updated.

### 7.2 Data Directory Structure

Plugin installation directory is at `/storage/emulated/0/UIN_Tool/plugins/{pluginId}/`:

```
/storage/emulated/0/UIN_Tool/plugins/
└── {pluginId}/
    ├── plugin.json          # Plugin configuration
    ├── plugin.dex           # Native plugin DEX
    ├── web/                 # Web plugin files
    │   ├── index.html
    │   ├── style.css
    │   └── script.js
    ├── data/                # Plugin file data directory (sandbox root, auto-created)
    │   ├── config.json
    │   ├── settings.txt
    │   └── images/
    └── cache/               # Plugin cache directory (auto-created)
        └── temp_*.dat
```

> **KV data is not in the plugin directory**: KV storage (`setStorage`/`putString`, etc.) is stored in the host app's private `SharedPreferences`, file name `plugin_data_{pluginId}` (cleared with the host on app uninstall). The `data/` directory only stores file-type data; the two are independent.
>
> When upgrading/reinstalling a plugin, the host first backs up `data/` to a temporary directory then restores it (ensuring user data is preserved); when uninstalling a plugin, `deleteAllPluginData()` deletes KV, `data/`, and `cache/` together.

### 7.3 Web Plugin Storage API

```javascript
// ============ KV Storage ============
// String
UINPlugin.setStorage('username', 'JohnDoe');
const name = UINPlugin.getStorage('username');

// Integer
UINPlugin.setStorageInt('score', 100);
const score = UINPlugin.getStorageInt('score', 0);

// Boolean
UINPlugin.setStorageBool('isLoggedIn', true);
const loggedIn = UINPlugin.getStorageBool('isLoggedIn', false);

// Float
UINPlugin.setStorageFloat('rating', 4.5);
const rating = UINPlugin.getStorageFloat('rating', 0);

// JSON
UINPlugin.setStorageJSON('config', JSON.stringify({theme: 'dark'}));
const config = JSON.parse(UINPlugin.getStorageJSON('config'));

// Delete
UINPlugin.removeStorage('temp');

// Clear
UINPlugin.clearStorage();

// Check key exists
const exists = UINPlugin.containsStorageKey('username');

// Get all keys
const keys = JSON.parse(UINPlugin.getStorageKeys());

// Get all data
const allData = JSON.parse(UINPlugin.getAllStorage());

// ============ Batch Operations ============
// Batch write
const batchData = {
    key1: 'value1',
    key2: 'value2',
    key3: 'value3'
};
UINPlugin.setStorageBatch(JSON.stringify(batchData));

// Batch read
const batchKeys = JSON.stringify(['key1', 'key2', 'key3']);
const batchResult = JSON.parse(UINPlugin.getStorageBatch(batchKeys));

// ============ File Operations ============
// Write file
UINPlugin.writeFile('notes.txt', 'Hello World');

// Read file
const content = UINPlugin.readFile('notes.txt');

// Delete file
UINPlugin.deleteFile('notes.txt');

// Check file exists
const fileExists = UINPlugin.fileExists('notes.txt');

// List files
const files = JSON.parse(UINPlugin.listFiles());

// Get file size
const size = UINPlugin.getFileSize('notes.txt');

// Clear cache
UINPlugin.clearCache();

// ============ Data Statistics ============
const stats = JSON.parse(UINPlugin.getStorageStats());
console.log('KV count:', stats.kvCount);
console.log('File count:', stats.fileCount);
console.log('Total size:', stats.totalFileSize);
console.log('Cache size:', stats.cacheSize);
console.log('Data version:', stats.dataVersion);

// ============ Data Import/Export ============
// Export data
const exported = UINPlugin.exportData();

// Import data
UINPlugin.importData(exported);
```

### 7.4 Native Plugin Storage API

The `context` received by native plugins in `onCreateView` is `PluginContext`, no need to construct it yourself:

```kotlin
val pctx = context as PluginContext

// ============ KV Storage (SharedPreferences: plugin_data_{pluginId}) ============
pctx.putString("key", "value")
val value = pctx.getString("key", "default")

pctx.putInt("count", 100)
val count = pctx.getInt("count", 0)

pctx.putLong("longKey", 123L)
val longVal = pctx.getLong("longKey", 0L)

pctx.putBoolean("enabled", true)
val enabled = pctx.getBoolean("enabled", false)

pctx.putFloat("floatKey", 1.5f)
val floatVal = pctx.getFloat("floatKey", 0f)

pctx.putJSON("config", JSONObject().apply { put("theme", "dark") })
val config = pctx.getJSON("config")

pctx.remove("temp")
pctx.clearAll()
val keys = pctx.getAllKeys()
val entries = pctx.getAllEntries()
val hasKey = pctx.contains("key")

// ============ File Storage (sandbox root = plugin data/) ============
pctx.writeFile("data.txt", "content")
val content = pctx.readFile("data.txt")
pctx.deletePluginFile("data.txt")
val files = pctx.listPluginFiles()
val fileExists = pctx.fileExists("data.txt")
val size = pctx.getPluginFileSize("data.txt")
pctx.clearPluginCache()                       // Clear cache directory
pctx.deleteAllPluginData()                    // Clear KV + data/ + cache/

// ============ Data Statistics ============
val stats = pctx.getStorageStats()            // kvCount/fileCount/totalFileSize/cacheSize
```

> Note: `PluginContext` provides several `@Deprecated` compatibility aliases (`getDataDir`→`getPluginDataDir`, `getCacheDir`→`getPluginCacheDir`, `listFiles`→`listPluginFiles`, `getFileSize`→`getPluginFileSize`, `clearCache`→`clearPluginCache`, `deleteFile`→`deletePluginFile`, `deleteAllData`→`deleteAllPluginData`); new code should use the new names directly to avoid confusion.

### 7.5 Data Migration

Data in the old `web_plugin_{pluginId}` SharedPreferences is automatically migrated to `plugin_data_{pluginId}` when `PluginContext` is created, with the old table cleared; no additional action needed from developers.

### 7.6 Data Version Management

```kotlin
// Get data version
val version = pctx.getDataVersion()

// Set data version
pctx.setDataVersion(2)

// Migration marker
pctx.markDataMigrated()       // Mark as migrated
```

---

## VIII. Permission System

### 8.1 Permission Interaction Model

> Since v5.5.0, plugin permission interaction is divided into two types: **Web plugins** (with/without backend) and **native plugins**.

**Web plugins** (`uiType: "web"`, including `web+backend`):
- The permission management page can manage all its declared permissions (grant / revoke / block), **no refresh button** — the list auto-refreshes after granting/revoking; button row provides "**Grant All**" / "**Revoke All**" short text buttons (shortened from v5.5.0, ensuring complete display).
- When opening the plugin, the host **first pops up a permission prompt dialog then opens the plugin** (dialog uses **host unified style** rendering): lists **ungranted** permissions, provides three options "OK" / "Don't Prompt Again" / "Manage Permissions" — "Manage Permissions" jumps directly to that plugin's permission management page.

**Native plugins** (`uiType: "native"`):
- Native plugins are in-process Android code that can directly call system APIs; **permissions are enforced by the host** (throws `SecurityException` when not granted), the host cannot fine-grain intercept or block at the application layer.
- Each time the plugin is opened, the host **first pops up a permission prompt dialog then opens the plugin** (dialog uses **host unified style** rendering): lists the plugin's declared (required) permissions, provides two options "OK" / "Don't Show Again".
- The permission management page **does not list** native plugins (native permissions are managed by the system, not controllable at the application layer).

> "Don't prompt/Don't show" selections are persisted per plugin (`plugin_permission_prompts` preference); after selection, they can be re-triggered from the permission management page or other entry points.

### 8.2 Permission State Management

> Note: Since v5.5.0, the old "auto-popup by status" `permission_state` (0/1/2 single value) API is deprecated. Permission status is now **determined per permission** (granted / blocked), no longer using a single state value to drive popup logic. Old interfaces are retained for compatibility but no longer used in the interaction flow.

### 8.3 Actual Permission Request Flow

1. **Permission prompt when opening plugin** (dialog uses **host unified style** rendering): Web plugins list ungranted permissions (OK / Don't Prompt Again / Manage Permissions); native plugins list required permissions (OK / Don't Show Again) — **popup first then open plugin**.
2. **Permission management interface**: After plugin installation, permissions declared by the plugin can be managed in "Plugin Management > Permissions" (Web plugins only, **no refresh button**, auto-refreshes after granting/revoking). Button row provides "**Grant All**" / "**Revoke All**" two short text buttons (shortened from v5.5.0 from original "Grant/Revoke All Permissions", ensuring complete display), supporting batch operations on the entire plugin.
3. **JS runtime request**: Web plugins call `UINPlugin.checkPermission()` / `UINPlugin.requestPermission()` / `UINPlugin.requestPermissions()`:
   - **Regular permissions** (camera, recording, location, etc.): Uses system runtime permission popup, results returned via callbackId as `{"success","allGranted","results":{perm:bool}}`
   - **Special permissions** (overlay, accessibility, install unknown apps, etc.): Host shows dialog guiding user to **system settings page** for manual authorization; authorization results need user to return manually

### 8.4 Permission Declaration

**Both declaration formats are compatible** (host parses by priority: JSON array first, comma-separated string second):

```json
{
    "permissions": "android.permission.CAMERA,android.permission.RECORD_AUDIO,android.permission.ACCESS_FINE_LOCATION"
}
```

```json
{
    "permissions": ["android.permission.CAMERA", "android.permission.RECORD_AUDIO", "android.permission.ACCESS_FINE_LOCATION"]
}
```

> Note: The old host only recognized comma-separated strings and ignored JSON arrays; since v5.5.0, `parseStringList()` is compatible with both formats for list fields like "permissions, dependencies, MIME types", and the wizard generates strings using **popup multi-select** for the "Permissions" field. Pseudo-permissions (`READ_CLIPBOARD` / `WRITE_CLIPBOARD`) are declared in `permissions` just like real permissions, only need to be declared to take effect, no runtime authorization needed.

### 8.5 Permission Types

| Permission (Short Name) | Full Permission Name | Description | Type |
|-------------------------|---------------------|-------------|------|
| READ_EXTERNAL_STORAGE | android.permission.READ_EXTERNAL_STORAGE | Read external storage | Regular |
| WRITE_EXTERNAL_STORAGE | android.permission.WRITE_EXTERNAL_STORAGE | Write external storage | Regular |
| INTERNET | android.permission.INTERNET | Access network | Regular |
| CAMERA | android.permission.CAMERA | Camera | Regular |
| RECORD_AUDIO | android.permission.RECORD_AUDIO | Record audio | Regular |
| ACCESS_FINE_LOCATION | android.permission.ACCESS_FINE_LOCATION | Precise location | Regular |
| MANAGE_EXTERNAL_STORAGE | android.permission.MANAGE_EXTERNAL_STORAGE | Manage all files | Special |
| SYSTEM_ALERT_WINDOW | android.permission.SYSTEM_ALERT_WINDOW | Overlay window | Special |
| WRITE_SETTINGS | android.permission.WRITE_SETTINGS | Modify system settings | Special |
| REQUEST_INSTALL_PACKAGES | android.permission.REQUEST_INSTALL_PACKAGES | Install unknown apps | Special |
| PACKAGE_USAGE_STATS | android.permission.PACKAGE_USAGE_STATS | App usage statistics | Special |
| ACCESSIBILITY | android.permission.ACCESSIBILITY | Accessibility service | Special |
| POST_NOTIFICATIONS | android.permission.POST_NOTIFICATIONS | Send notifications (Android 13+) | Special |
| READ_CLIPBOARD | (Pseudo-permission) | Read clipboard (`getClipboard`/`paste`/`clearClipboard`) | Pseudo-permission |
| WRITE_CLIPBOARD | (Pseudo-permission) | Write clipboard (`setClipboard`/`copyToClipboard`) | Pseudo-permission |

> The wizard "Permissions" popup already includes all common permissions (including `READ_CLIPBOARD` / `WRITE_CLIPBOARD`); the permission management page only does "declaration + blocking" control for pseudo-permissions, does not initiate runtime authorization.

---

## IX. Plugin Notice Feature

### 9.1 Overview

New feature in v4.2.0: plugins can declare a notice field in plugin.json, and the notice dialog is automatically displayed on first opening.

### 9.2 Configuration Method

```json
{
    "pluginId": "com.example.myplugin",
    "name": "My Plugin",
    "notice": "Welcome to my plugin!\n\nFeature Description:\n1. Click buttons to execute operations\n2. Data is automatically saved\n3. Supports import and export"
}
```

### 9.3 User Interaction

| Button | Behavior |
|--------|----------|
| Got it | Close dialog, do not show again for current session |
| Don't prompt again | Permanently close this plugin's notice |
| Remind later | Close dialog, show again next time the plugin is opened |

---

## X. PluginInterface Detailed Reference

### 10.1 Method Description

Native plugins implement `com.UIN.Tool.plugin.PluginInterface`. The host loads `plugin.dex` via `DexClassLoader` and instantiates with `loadClass(mainClass).newInstance() as PluginInterface` (requires public class + public no-arg constructor), instances are cached in `WeakReference`; each time the plugin is opened, the loading process is re-executed.

| Method | Description | Host Actually Calls | Call Timing |
|--------|-------------|--------------------|----|
| onCreateView(context, container, savedInstanceState) | **Must implement**, creates plugin UI | Yes | When plugin opens (re-executes every time). `context` is actually `PluginContext`, `savedInstanceState` **is always null** (host hardcodes it), returned View is attached to host's full-screen container |
| onResume | Plugin resumes | Yes | When Activity resumes |
| onPause | Plugin pauses | Yes | When Activity pauses (switching away/lock screen) |
| onDestroy | Plugin destroyed | Yes | When Activity is destroyed (host first decides whether to stop backend based on `backendKeepAlive`, then destroys WebView, clears instance and ClassLoader cache) |
| onBackPressed | Back key interception | Yes | User presses back key; returning `true` means consumed, otherwise host first tries WebView `goBack()` then exits |
| onSaveInstanceState | Save state | **Not called** | Reserved. Host's onSaveInstanceState only saves pluginId and WebView state, does not forward to plugin |
| onActivityResult | Activity result | Yes | When `startActivityForResult` returns |
| onRequestPermissionsResult | Permission request result | Yes | When permission request completes |
| getPluginTitle | Return plugin page title | **Not called** | Reserved. Title needs to be set via `setPluginTitle()`/JS |
| getPluginMenuItems | Return menu item list | **Not called** | Reserved. `PluginMenuItem(id, title, icon, onClick)` data class is defined but host not connected |
| onHostEvent | Receive host events | Yes | Host event channel. Real event name is `"plugin_call_<method>"`, triggered by backend `callPlugin` with parameter Bundle |
| sendHostEvent | Send events to host | **Not called** | Reserved. No reverse bridge |
| getHostService | Get host service | **Not called** | Reserved, always returns null |
| isBackendRunning | Whether backend is running | **Not injected** | Reserved empty implementation |
| getBackendPort | Get backend port | **Not injected** | Reserved empty implementation |
| callBackendApi | Call backend API | **Not injected** | Reserved empty implementation |
| executeBackendTask | Execute backend task | **Not injected** | Reserved empty implementation |

> ⚠️ **Native plugins using backend**: The host **does not inject actual implementations** for `isBackendRunning`/`getBackendPort`/`callBackendApi`/`executeBackendTask`; calls are always no-ops. When native plugins need to access the backend, they should cast the host Activity via `PluginContext`'s `baseContext`:

```kotlin
val host = (context.baseContext as? com.UIN.Tool.plugin.PluginHostActivity)
host?.isBackendReady()                 // Boolean: whether backend is ready
host?.getBackendPort()                 // Int: backend port (0 if not ready)
host?.callBackendApi("/api/hello", "GET", null) { success, data -> }
```

> State preservation: `onCreateView`'s `savedInstanceState` is always null; after rotation/recreation, the host only reloads the plugin by pluginId. Plugin's own state needs to rely on KV persistence (`pctx.putString`, etc.) for preservation.

### 10.2 Complete Implementation Example

```kotlin
package com.example

import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.UIN.Tool.plugin.PluginInterface
import com.UIN.Tool.plugin.PluginContext

class MainPlugin : PluginInterface {

    private var pctx: PluginContext? = null
    private var rootView: View? = null
    private var clickCount = 0

    override fun onCreateView(
        context: Context,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Get PluginContext for data storage
        pctx = context as? PluginContext

        val appContext = context.applicationContext
        val layout = LinearLayout(appContext).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 50, 50, 50)
        }

        // Read saved data
        clickCount = pctx?.getInt("click_count", 0) ?: 0

        val counterText = TextView(appContext).apply {
            text = "Click count: $clickCount"
            textSize = 16f
            setTextColor(0xFF666666.toInt())
            setPadding(0, 0, 0, 20)
        }

        val button = Button(appContext).apply {
            text = "Click Me"
            setBackgroundColor(0xFF37474F.toInt())
            setTextColor(0xFFFFFFFF.toInt())
            setOnClickListener {
                clickCount++
                counterText.text = "Click count: $clickCount"
                // Save data
                pctx?.putInt("click_count", clickCount)
                Toast.makeText(context, "Clicked $clickCount times", Toast.LENGTH_SHORT).show()
            }
        }

        layout.addView(counterText)
        layout.addView(button)

        rootView = layout
        return rootView
    }

    override fun onResume() { }
    override fun onPause() { }
    override fun onDestroy() { rootView = null }
    override fun onBackPressed(): Boolean = false
    override fun onSaveInstanceState(): Bundle? = null
}
```

### 10.3 Plugin Multi-Instance (New in v5.4.0)

The host isolates multiple running instances of the same plugin using "**instance keys**" (`pluginId:instanceId`), each instance has a globally unique `instanceId`.

**Default Behavior by Type**

| Plugin Type | Default Behavior | Description |
|-------------|-----------------|-------------|
| Web | **Supports multi-instance** | Each opening starts a new WebView instance; page state, injected JS interfaces, and dialogHost do not interfere |
| CUI | **Supports multi-instance** | Each opening starts a new foreground terminal session (distinguished by instance key) |
| Native | **Single instance** | Default reuses the same `PluginInterface` instance and View; after enabling "Native Plugin Multi-Instance (Experimental)" in "Development Tools", each opening creates a new independent instance and View (`ClassLoader` still shared) |

**Host Isolation Dimensions**:

- **Lifecycle callbacks**: `onPluginResume`/`onPluginPause`/`onPluginDestroy` etc. are all routed by instance key
- **WebView cache**: `PluginManager`'s instance WebView cache uses instance key as key; `onResume` only dispatches `resume` event to the current instance
- **JS interface**: Instances are distinguished via `PluginHostActivity.kt`'s `currentInstanceKey`; `PluginJSInterface.getInstanceId()` can get the current instance ID
- **Native instance**: `pluginInstances` cache default reuses by pluginId; after enabling native multi-instance, creates new instance each time with `newInstance()`

**Backend Multi-Instance (Backend Plugins)**:

| Backend Multi-Instance Mode | Behavior | Configuration |
|----------------------------|----------|---------------|
| **Shared Port** | All instances of the same plugin share the same backend process (the first launched instance holds its primary instance key) | Default; "Development Tools" > "Per-Instance Independent Backend Port" **off** |
| **Independent Port** | Each instance launches an independent backend process and port (`PluginBackendManager.startBackendInstance` starts by instance key), instances independent of each other | "Development Tools" > "Per-Instance Independent Backend Port" **on** |

> In multi-instance scenarios with independent port mode configured, the plugin backend perceives which instance it is via the `$PORT` environment variable (`$PORT` is exclusive to that instance); in shared port mode, only the primary instance holds the backend, other instances reuse its `$PORT`.

**Keep Session Single Window (v5.5.0)**: "Keep Session on Close" toggle is **enabled by default** — default single-window deduplication (shared port mode + Web plugins keep only one background window for the same plugin, repeated openings bring it to the foreground and end this startup, no more multi-instance, multi-task window only shows one). Users can explicitly disable it in "Development Tools", after which Web / CUI plugins create independent instances each time (supporting multi-instance).

---

## XI. JavaScript API Complete Reference (v4.5.0)

Web plugins call native capabilities through the host-injected global object **`UINPlugin`**, provided by `PluginJSInterface` (a total of **170+** `@JavascriptInterface` methods). All method return values marked as `String (JSON)` indicate that `JSON.parse` is needed on the JS side.

### 11.1 Basic API

**Universal Bridge `callHost(action, data)`**

```javascript
UINPlugin.callHost('toast', 'Short message');          // Toast notification
UINPlugin.callHost('toastLong', 'Long message');       // Long Toast
UINPlugin.callHost('finish', '');                       // Close plugin page
UINPlugin.callHost('log', 'Info');                      // Normal log
UINPlugin.callHost('logError', 'Error');                // Error log
UINPlugin.callHost('logWarning', 'Warning');            // Warning log
UINPlugin.callHost('alert', 'Alert content');           // Alert dialog
UINPlugin.callHost('confirm', 'Confirm content');       // Confirm dialog
UINPlugin.callHost('vibrate', '200');                   // Vibrate (milliseconds, requires VIBRATE permission)
UINPlugin.callHost('copy', 'Text to copy');             // Copy to clipboard
UINPlugin.callHost('openUrl', 'https://example.com');   // Open link
UINPlugin.callHost('share', 'Share content');           // System share
UINPlugin.callHost('setTitle', 'New title');            // Set plugin page title
UINPlugin.callHost('setFullscreen', 'true');            // Fullscreen (string true/false)
UINPlugin.callHost('setKeepScreenOn', 'true');          // Keep screen on (string true/false)
UINPlugin.callHost('sendNotification', 'Title,Message'); // Send notification
UINPlugin.callHost('takeScreenshot', '');               // Screenshot (requires storage permission, saves to downloads/screenshots/)
```

**Dialogs / Loading (Since v4.4.4, popups unified through host Compose queue)**

```javascript
// Confirm dialog (callback JSON: {success, confirmed} or {ignored:true})
UINPlugin.showConfirmDialog('Title', 'Content', callbackId);

// Input dialog (callback JSON: {success, confirmed, value})
UINPlugin.showPromptDialog('Title', 'Hint', callbackId);

// Loading indicator (Toast form, not a real Loading layer)
UINPlugin.showLoading('Processing...');
UINPlugin.hideLoading();  // No-op, reserved
```

**Callback Mechanism**

Asynchronous methods (like `httpGet`, `requestPermission`, `startSensor`, `showConfirmDialog`) require passing a `callbackId`. The JS side registers the callback first, and the native side returns a **JSON string** on the main thread using `evaluateJavascript`:

```javascript
window.UINPluginCallbacks = window.UINPluginCallbacks || {};
const callbackId = 'cb_' + Date.now();
window.UINPluginCallbacks[callbackId] = function (resp) {
    const data = JSON.parse(resp);   // Native side returns JSON string
    console.log('Callback:', data);
    delete window.UINPluginCallbacks[callbackId];
};
UINPlugin.httpGet('https://example.com', callbackId);
```

**Host-Injected JS Hooks**

| Hook | Trigger Timing | Description |
|------|---------------|-------------|
| `window._onUINPluginReady()` | After page load complete, interface injected | Can safely call `UINPlugin.*` |
| `window._onBackendReady(port)` | Backend ready | Parameter is the actual listening port |
| `window._onBackendProgress(progress, message)` | During backend startup | Progress and prompt information |

**External Content Reception (openWith, New in v5.4.0)**

Content passed to this plugin from system "Share / Open with other apps" or third-party app explicit intents can be read via `UINPlugin.getOpenData()` (returns **JSON string**, returns `{}` when no data). The host also injects `window.UINOpenData` (same content string) and `window.getOpenData()` (equivalent function):

```javascript
// Read external open content
const openData = JSON.parse(UINPlugin.getOpenData() || '{}');
if (openData.kind === 'url') {
    console.log('Received link:', openData.url);
} else if (openData.kind === 'file') {
    console.log('Received file:', openData.name);
    console.log('Local path:', openData.filePath);
    console.log('Container path:', openData.containerPath);
} else if (openData.kind === 'text') {
    console.log('Received text:', openData.text);
}
```

Field structure (including `kind` / `type` / `when` / `mime` / `url` / `text` / `uri` / `name` / `filePath` / `files` / `streamCount` and "text vs link" discrimination, `.incoming/` naming rules), host receiving Action/MIME lists, and third-party apps **skipping relay page to directly invoke plugin** (`plugin_id` / `instance_id` / `open_data` extras) complete description, see this document **12.3.4 External Content Reception (openWith)**.

**Outbound Intents (Since v4.5.0)**

Web plugins can also initiate system intents; the two are equivalent (`callHost` and `@JavascriptInterface` direct methods both available):

```javascript
UINPlugin.callHost('openUrl', 'https://example.com'); // ACTION_VIEW, opens link in system browser/app
UINPlugin.openUrl('https://example.com');             // Same as above (@JavascriptInterface)
UINPlugin.callHost('share', 'Share content');          // ACTION_SEND text/plain, shows share panel
UINPlugin.share('Share content');                      // Same as above
```

> `openUrl` parameter is a bare URL (opens with `ACTION_VIEW` automatically); `share` parameter is plain text (`EXTRA_TEXT`). Intent relay (openWith) received external content is independent of these sending APIs.

### 11.2 Storage API

Storage is plugin-private KV (SharedPreferences `plugin_data_{pluginId}`):

| API | Description | Return Value |
|-----|-------------|--------------|
| setStorage(key, value) | Store string | void |
| getStorage(key) | Read string | String |
| setStorageInt(key, value) | Store integer | void |
| getStorageInt(key, default) | Read integer | Int |
| setStorageLong(key, value) | Store long integer | void |
| getStorageLong(key, default) | Read long integer | Long |
| setStorageBool(key, value) | Store boolean | void |
| getStorageBool(key, default) | Read boolean | Boolean |
| setStorageFloat(key, value) | Store float | void |
| getStorageFloat(key, default) | Read float | Float |
| setStorageJSON(key, json) | Store JSON (internally validated before writing) | void |
| getStorageJSON(key) | Read JSON (returns `"{}"` if no value) | String |
| removeStorage(key) | Delete key | void |
| clearStorage() | Clear all KV | void |
| containsStorageKey(key) | Check key existence | Boolean |
| getAllStorage() | Get all key-value pairs | String (JSON) |
| getStorageKeys() | Get all keys | String (JSON array) |
| getAllKeys() | Same as getStorageKeys | String (JSON array) |
| getAllData() | Same as getAllStorage | String (JSON) |
| setStorageBatch(jsonData) | Batch write (JSON object, stores each key as string) | Boolean |
| getStorageBatch(keys) | Batch read (pass JSON array, returns {key:value}) | String (JSON) |

### 11.3 File System API

File sandbox root directory is plugin `data/`. Security: host rejects `..`, `/../`, paths starting with `/`, and validates canonicalPath prefix to prevent directory traversal.

| API | Description | Return Value |
|-----|-------------|--------------|
| writeFile(fileName, content) | Write text file | Boolean |
| readFile(fileName) | Read text (returns null if not exists) | String |
| deleteFile(fileName) | Delete file | Boolean |
| fileExists(fileName) | Check file existence | Boolean |
| listFiles() | List file names under data/ root | String (JSON array) |
| getFileList(directory) | List specified directory (empty=data/ root), returns name/path/size/isFile/isDirectory/lastModified per item | String (JSON array) |
| getFileSize(fileName) | File size | Long |
| getFileInfo(fileName) | File info (name/path/size/isFile/isDirectory/exists/lastModified/canRead/canWrite/canExecute) | String (JSON) |
| isDirectory(fileName) | Whether it is a directory | Boolean |
| createDir(dirName) | Create directory | Boolean |
| deleteDir(dirName) | Delete directory | Boolean |
| renameFile(oldName, newName) | Rename | Boolean |
| copyFile(srcName, dstName) | Copy (overwrite mode) | Boolean |
| moveFile(srcName, dstName) | Move | Boolean |
| exists(path) | Check path existence | Boolean |
| clearCache() | Clear plugin cache/ directory | void |
| clearPluginCache() | Same as clearCache | void |

### 11.4 Network Request API

```javascript
// HTTP requests (all require INTERNET permission, callback {"success":bool, "statusCode":..., "data":...})
UINPlugin.httpGet(url, callbackId);
UINPlugin.httpPost(url, jsonBody, callbackId);   // application/json
UINPlugin.httpPut(url, jsonBody, callbackId);
UINPlugin.httpDelete(url, callbackId);

// Download file to plugin data/ (callback {"success":true,"file":...,"size":...})
UINPlugin.downloadFile(url, fileName, callbackId);

// Ping (ping -c 1 -W 5 in thread, falls back to isReachable on failure; callback contains host/ip/time)
UINPlugin.ping(host, callbackId);

// DNS resolution (callback {"success":bool,"host":"...","ips":[]})
UINPlugin.resolveDns(host, callbackId);
UINPlugin.dns(host, callbackId);        // Same as resolveDns
const ips = JSON.parse(UINPlugin.dnsLookup(host)); // Synchronous DNS resolution

// Network status
const net = JSON.parse(UINPlugin.getNetworkInfo());  // connected/type/subtype/isWifi/isMobile
UINPlugin.isNetworkAvailable();
UINPlugin.isWifiConnected();
UINPlugin.isMobileConnected();
UINPlugin.getIpAddress();   // Non-loopback IPv4, returns "0.0.0.0" on failure
const wifi = JSON.parse(UINPlugin.getWifiInfo());    // ssid/bssid/rssi/linkSpeed/frequency/ip/networkId
UINPlugin.getSignalStrength();                       // WiFi RSSI, -100 when not connected
const op = JSON.parse(UINPlugin.getOperatorInfo());  // Carrier/Sim info
```

### 11.5 Device Info API

```javascript
// Plugin and host info
const plugin = JSON.parse(UINPlugin.getPluginInfo());   // All plugin.json fields
const pluginVersion = UINPlugin.getPluginVersion();     // Plugin versionName
const pluginVersionCode = UINPlugin.getPluginVersionCode(); // Plugin version
const appVersion = UINPlugin.getAppVersion();           // Host versionName
const appVersionCode = UINPlugin.getAppVersionCode();   // Host versionCode
const hostVersion = UINPlugin.getHostVersion();         // = getAppVersion()

// Device identifiers
const androidId = UINPlugin.getAndroidId();        // ANDROID_ID
const deviceId = UINPlugin.getDeviceId();          // Requires READ_PHONE_STATE, returns "permission_denied" if unauthorized
const serial = UINPlugin.getSerialNumber();        // Build.getSerial()
const mac = UINPlugin.getMacAddress();
const fingerprint = UINPlugin.getFingerprint();    // Build.FINGERPRINT
const hw = JSON.parse(UINPlugin.getHardwareInfo());    // hardware/board/bootloader/radio/cpu_abi/...
const bootTime = UINPlugin.getBootTime();
const uptime = UINPlugin.getUptime();

// Device basic info
const model = UINPlugin.getDeviceModel();
const version = UINPlugin.getAndroidVersion();
const api = UINPlugin.getApiLevel();
const density = UINPlugin.getScreenDensity();
const device = JSON.parse(UINPlugin.getDeviceInfo());  // android/api/device/manufacturer/brand/.../packageName
const screen = JSON.parse(UINPlugin.getScreenSize());  // width/height/widthDp/heightDp

// Memory
const totalMem = UINPlugin.getTotalMemory();
const freeMem = UINPlugin.getFreeMemory();
const mem = JSON.parse(UINPlugin.getMemoryUsage());    // total/used/free/percentage

// CPU and build info
const cpuInfo = UINPlugin.getCpuInfo();     // cat /proc/cpuinfo raw text
const buildInfo = JSON.parse(UINPlugin.getBuildInfo());

// Time / Language
UINPlugin.getCurrentTime();                 // "yyyy-MM-dd HH:mm:ss"
UINPlugin.getCurrentTimestamp();            // System.currentTimeMillis()
UINPlugin.getSystemTime();                  // Same as getCurrentTimestamp
UINPlugin.getTimezone();                    // TimeZone ID
UINPlugin.getTimezoneOffset();              // Timezone offset (hours)
UINPlugin.isDaylightSaving();               // Whether daylight saving time
UINPlugin.getSystemLanguage();              // Locale.language
UINPlugin.getSystemCountry();               // Locale.country
UINPlugin.getLocale();                      // Locale.toString()
UINPlugin.getDisplayLanguage();             // Locale.displayLanguage

// Device storage (internal storage partition)
UINPlugin.getTotalStorage();
UINPlugin.getFreeStorage();
UINPlugin.getUsedStorage();
UINPlugin.getStoragePercentage();

// App management
UINPlugin.isAppInstalled('com.example.app');
UINPlugin.getAppName('com.example.app');
UINPlugin.getAppVersion('com.example.app');  // Specified app's versionName
UINPlugin.openApp('com.example.app');        // Launch app
const appInfo = JSON.parse(UINPlugin.getAppInfo());  // Host's own packageName/versionName/.../sharedUserId
```

### 11.6 Sensor API

| API | Description | Return Value |
|-----|-------------|--------------|
| getAccelerometer() | Accelerometer (sensor info, not real-time value) | String (JSON) |
| getGyroscope() | Gyroscope | String (JSON) |
| getLightSensor() | Light sensor | String (JSON) |
| getProximitySensor() | Proximity sensor | String (JSON) |
| getMagneticField() | Magnetic field sensor | String (JSON) |
| getOrientation() | Orientation sensor | String (JSON) |
| getPressureSensor() | Pressure sensor | String (JSON) |
| getTemperatureSensor() | Temperature sensor | String (JSON) |
| getHumiditySensor() | Humidity sensor | String (JSON) |
| getAvailableSensors() | Available sensor boolean table (accelerometer/gyroscope/magnetic/light/proximity/pressure) | String (JSON) |

**Continuous Real-time Callbacks**

```javascript
// Start monitoring (type supports accelerometer/gyroscope/magnetic/light/proximity/pressure)
// On successful start, first callbacks {"success":true,...}, then onSensorChanged continuously callbacks x/y/z or lux/distance/pressure/values + timestamp/accuracy
UINPlugin.startSensor('accelerometer', callbackId);

// Stop monitoring
UINPlugin.stopSensor();
```

### 11.7 System API

```javascript
// Open system pages
UINPlugin.openSettings();
UINPlugin.openAppSettings();        // This app's details page
UINPlugin.openWifiSettings();
UINPlugin.openBluetoothSettings();
UINPlugin.openLocationSettings();
UINPlugin.openUrl('https://example.com');  // Open any link
UINPlugin.share('Share content');

// Status queries
UINPlugin.isAirplaneModeOn();
UINPlugin.isBluetoothOn();
UINPlugin.isWifiOn();
UINPlugin.isMobileDataOn();
UINPlugin.isLocationOn();
UINPlugin.isNfcOn();
UINPlugin.isAutoRotateOn();
UINPlugin.isDndOn();
UINPlugin.isDarkMode();

// Screen
const brightness = UINPlugin.getScreenBrightness();  // Returns -1 on error
UINPlugin.getAutoBrightness();                       // Whether auto brightness
const displayInfo = JSON.parse(UINPlugin.getDisplayInfo()); // width/height/density/xdpi/...
const fontScale = UINPlugin.getFontScale();

// Battery (unique battery method)
const battery = JSON.parse(UINPlugin.getBatteryInfo()); // level/isCharging/status

// Audio
const volume = UINPlugin.getVolume();
const maxVolume = UINPlugin.getMaxVolume();
const volumePct = UINPlugin.getVolumePercentage();
const hasHeadphones = UINPlugin.isHeadphonesConnected();

// Clipboard
UINPlugin.setClipboard('text');
UINPlugin.copyToClipboard('text');  // Same as setClipboard
UINPlugin.getClipboard();
UINPlugin.paste();                  // Same as getClipboard
UINPlugin.clearClipboard();

// Notifications (channel plugin_notification_channel; Android 13+ requires notification permission)
UINPlugin.sendNotification('Title', 'Message');
UINPlugin.cancelNotification(id);

// Plugin page control
UINPlugin.setTitle('New title');
UINPlugin.setFullscreen(true);
UINPlugin.setKeepScreenOn(true);
UINPlugin.takeScreenshot();         // Requires storage permission, saves to downloads/screenshots/
UINPlugin.getPluginDir();           // Plugin root directory absolute path
UINPlugin.getBackendStatus();       // "running:{port}" / "starting" / "unknown"
```

### 11.8 Permission API

```javascript
// Check permission (regular + special permissions)
UINPlugin.checkPermission('android.permission.CAMERA');

// Request single permission (callback {"success":bool, ...})
// Special permissions (overlay/accessibility/install unknown apps) show dialog guiding to system settings page
UINPlugin.requestPermission('android.permission.CAMERA', callbackId);

// Batch request (JSON array string, callback {"success","allGranted","results":{perm:bool}})
UINPlugin.requestPermissions('["android.permission.CAMERA","android.permission.RECORD_AUDIO"]', callbackId);
```

### 11.9 Backend Communication API

```javascript
// Get backend status
const status = UINPlugin.getBackendStatus();   // "running:{port}" / "starting" / "unknown"

// Call backend API (request to 127.0.0.1 host dynamic port {path}, GET/POST/PUT/DELETE, callback {"success":bool,"data":...})
UINPlugin.callBackendApi('/api/compute', 'POST', JSON.stringify({
    expression: 'sum([1,2,3,4,5])'
}), callbackId);

// Check backend ready
function isBackendReady() {
    const status = UINPlugin.getBackendStatus();
    return status && status.startsWith('running:');
}
```

### 11.10 Data Statistics API

```javascript
// Get storage statistics
const stats = JSON.parse(UINPlugin.getStorageStats());
// stats.kvCount, stats.fileCount, stats.totalFileSize, stats.cacheSize, stats.dataVersion

// Get plugin data size
const size = UINPlugin.getPluginDataSize();

// Get data version
const version = UINPlugin.getDataVersion();

// Clear all plugin data (KV + data/ + cache/)
UINPlugin.clearAllPluginData();

// Export data (returns JSON: {pluginId,pluginName,version,exportTime,data:{...}})
const exported = UINPlugin.exportData();

// Import data
UINPlugin.importData(exported);
```

---

## XII. Packaging and Importing

### 12.1 Packaging Methods

Method 1: Use the wizard to package

1. Click "Create Plugin" on the "Dev" page
2. Complete configuration according to the wizard
3. Click "Finish" on the last step
4. System automatically generates TPK package
5. Location: /storage/emulated/0/UIN_Tool/tpk/

Method 2: Manual packaging

1. Organize plugin files into a folder
2. Ensure there is a plugin.json and necessary files
3. Compress to ZIP format
4. Rename with .tpk extension

### 12.2 File Structure

Native plugin

```
plugin.tpk
├── plugin.json      # Required
├── icon.png         # Optional
├── plugin.dex       # Required (host requires; current wizard packages placeholder file, need to place real DEX yourself)
├── src/             # Optional
└── res/             # Optional
```

> ⚠️ Current in-app compilation is disabled: the `plugin.dex` generated by the wizard in native plugin TPK is a placeholder text (`// Native plugin compilation temporarily disabled`), not a real DEX, and cannot be loaded after installation. Please compile a real `plugin.dex` on PC and replace it.

Web plugin (without backend)

```
plugin.tpk
├── plugin.json      # Required
├── icon.png         # Optional
└── web/             # Required
    ├── index.html   # Required
    ├── style.css    # Optional
    └── script.js    # Optional
```

Web plugin (with backend)

```
plugin.tpk
├── plugin.json      # Required
├── icon.png         # Optional
└── web/             # Required
    ├── index.html   # Required
    ├── style.css    # Optional
    └── script.js    # Optional
```

> ✅ **Since v5.2.0, the packager recursively packages the entire project directory**: `web/`, `scripts/`, `scripts/backend/server.py`, `start.sh`, `res/`, `src/`, and any resources are all included in the TPK (skipping hidden files and `.tpk` output). Web plugins without `web/index.html` auto-generate a default page as fallback.
>
> ℹ️ Web plugin's `entry` must point to an HTML page (`web/index.html`). Since v5.1.0, the wizard correctly generates `entry: "web/index.html"` when creating "WebView + Backend" plugins, no manual modification needed (older wizard versions sometimes incorrectly pointed to backend script paths, newly generated plugins no longer have this issue after upgrading).

CUI plugin (new in v4.5.0)

```
plugin.tpk
├── plugin.json      # Required (uiType: "cui")
├── icon.png         # Optional
└── scripts/         # Scripts directory
    └── script.py    # Terminal startup script
```

> Packaging rules (`packageTpk`, since v5.2.0): Recursively packages the entire project directory — explicitly adds `plugin.json`, `icon.png`, `README.md`, native placeholder/real `plugin.dex` (prioritizes recognizing real DEX's `dex\n` magic), then recursively includes `web/`, `scripts/`, `res/`, `src/`, etc. all directories; skips hidden files and `.tpk` output to avoid duplicate entries. Web plugins without `web/index.html` auto-generate a default page as fallback.

### 12.3 plugin.json Complete Fields

`plugin.json` is the heart of the plugin, located in the `.tpk` package root directory. The host parses this file via `PluginInfo.fromJson()`, so **field names must exactly match the table below** (case-sensitive).

#### 12.3.1 Complete Example

A complete example of a Web plugin with Python backend + Proot container:

```json
{
    "pluginId": "com.example.myplugin",
    "version": 2,
    "versionName": "2.1.0",
    "minHostVersion": 1,
    "name": "My Plugin",
    "author": "Developer",
    "description": "Plugin description",
    "notice": "Notice shown on first open",
    "icon": "icon.png",
    "mainClass": "com.example.MainPlugin",
    "updateUrl": "https://github.com/UIN-Tool-Plugins/myplugin",
    "category": "Tools",
    "uiType": "web",
    "entry": "web/index.html",
    "permissions": "android.permission.INTERNET",
    "backend": "other",
    "backendStartCommand": "sh scripts/start.sh",
    "backendStartEntry": "scripts/start.sh",
    "backendAutoStart": true,
    "backendTimeout": 30,
    "backendHealthCheck": "/health",
    "openWith": {
        "enabled": true,
        "label": "My Receiver",
        "mimeTypes": "text/*",
        "acceptText": true,
        "acceptUrl": true,
        "acceptFile": true
    }
}
```

> The above is the **complete fields generated by the wizard** (since v5.5.0 no longer includes `apiLevel`/`dependencies`/`backendKeepAlive`/`backendMaxRetries`/`backendLogLevel`/`maxMemory`/`maxCpuTime`/`maxConcurrentTasks`; these fields can still be manually written in plugin.json, see 12.3.3 / 12.3.5).

#### 12.3.2 Basic Information Fields

| Field | Type | Default | Required | Detailed Description |
|-------|------|---------|----------|---------------------|
| `pluginId` | string | `""` | Yes | **Plugin unique identifier**, reverse domain name format (e.g., `com.example.myplugin`). Used for plugin directory name, data isolation, startup routing; cannot be changed arbitrarily after installation. |
| `version` | int | `1` | Yes | Numeric version number, used for version comparison (upgrade detection). Must increment when upgrading the plugin. |
| `versionName` | string | `1.0.0` | Yes | Version name displayed to users. |
| `minHostVersion` | int | `1` | Yes | Minimum host version number (compared with host Build version); prompts host upgrade if not met. |
| `name` | string | `""` | Yes | Plugin display name, appears in plugin list, terminal title, shortcuts, etc. |
| `author` | string | `""` | No | Plugin author. |
| `description` | string | `""` | No | Plugin functionality description, displayed on the plugin management page. |
| `notice` | string | `""` | No | Plugin notice. Automatically shown in a popup on first plugin open (options: "Don't prompt again", "Remind later"). |
| `icon` | string | `icon.png` | No | Icon filename, relative to plugin root (recommended 128x128 PNG). |
| `mainClass` | string | `""` | Native Yes | **Native plugin entry class full class name** (e.g., `com.example.MainPlugin`). Host loads via `DexClassLoader` then instantiates with `loadClass(mainClass).newInstance() as PluginInterface`. Leave empty for Web/CUI plugins. |
| `updateUrl` | string | `""` | No | Plugin update check URL (GitHub Release page), used for in-app update checks. |
| `apiLevel` | int | `21` | No | Plugin required host API level (minimum 21). |
| `category` | string | `Uncategorized` | No | Plugin category name, used for category display on the plugin management page. |
| `signature` | string | `""` | No | Plugin signature (written and verified by the host on installation, prevents tampering). Generally maintained by the host, do not write manually. |
| `uiType` | string | `native` | Yes | Frontend type: `native` (native View), `web` (WebView), `cui` (full-screen terminal). Determines the host's loading branch. |
| `entry` | string | `web/index.html` | Web Yes | Web plugin entry page (relative to plugin root, host loads with `file://<pluginDir>/<entry>`). Leave empty for CUI. Must point to an HTML page; correctly generated since v5.1.0 wizard, older plugins with `entry` pointing to script path need manual correction. |
| `permissions` | string | `""` | No | Required permissions list. **Both formats acceptable**: comma-separated string (e.g., `android.permission.INTERNET,android.permission.VIBRATE`) or JSON array. Since v5.5.0, `parseStringList()` is compatible with both formats; old versions only recognized comma-separated strings. Includes pseudo-permissions (`READ_CLIPBOARD`/`WRITE_CLIPBOARD`). Generated via wizard "Permissions" popup multi-select. |
| `dependencies` | string | `""` | No | Dependency plugin ID list. **Both formats acceptable** (comma-separated string or JSON array). Host checks dependency existence before startup. |
| `frontendConfig` | object | `{}`` | No | **Reserved field**. Model declared (`Map<String, Any>`), but current version does not participate in JSON read/write, has no consumers, do not fill in plugin.json. |

#### 12.3.3 Backend Configuration Fields

> Since v5.1.0, backend is unified to "startup command" mode (`backendStartCommand`); legacy language-based startup fields (`backendPort`/`backendEntry`/`backendBinary`/`backendPreCommand`, etc.) are no longer used in the new workflow, and are automatically converted to `backendStartCommand` by `migrateLegacyBackend()` when the plugin is loaded. The runtime environment is globally configured in "Backend Runtime Settings" on the "Manage" page. The wizard can configure `backendStartCommand`, `backendTimeout`, `backendHealthCheck` (since v5.5.0, wizard is streamlined, no longer provides "Backend Keep-Alive" input field).

| Field | Type | Default | Required | Detailed Description |
|-------|------|---------|----------|---------------------|
| `backend` | string | `""` | No | Backend type: `other` (unified startup command mode). Leave empty means no backend. Legacy values (`python`/`node`/`php`, etc.) are automatically migrated to `other` on loading. |
| `backendStartCommand` | string | `""` | web+backend Yes | **Startup command**: host executes with `sh -lc` in the plugin directory (dependency detection + start backend). Defaults to `sh scripts/start.sh` when empty. Host injects `$PORT`, `$PLUGIN_ID`, `$PLUGIN_DIR`, `$WORK_DIR`. |
| `backendStartEntry` | string | `scripts/start.sh` | No | Relative path of the startup script within the plugin directory. |
| `backendAutoStart` | boolean | `true` | No | Whether to auto-start the backend when opening the plugin. |
| `backendTimeout` | int | `30` | No | Backend ready wait timeout (seconds). Actual effective value is relaxed based on runtime environment (see 5.6): built-in Termux always proot container → `max(backendTimeout, 120)` seconds; real Termux proot → `max(backendTimeout, 120)` seconds; real Termux native → `max(backendTimeout, 60)` seconds. |
| `backendHealthCheck` | string | `/health` | No | Health check endpoint path. Host polls this path and 200 response indicates readiness. |
| `backendMaxRetries` | int | `3` | No | **Reserved field**. Model declared, participates in JSON read/write, but host currently has no retry logic (prompts on failure, does not auto-retry). |
| `backendLogLevel` | string | `info` | No | **Reserved field**. Model declared, participates in JSON read/write, but host currently does not switch log level based on it (logs are fixed output, does not read this value). |
| `backendKeepAlive` | boolean | `false` | No | Whether to keep backend running after plugin closes. When `true`, host does not stop backend in onDestroy. |
| `backendEnv` | object | `{}`` | No | **Note: Current version does not read this JSON field, and `fromJson()`/`toJson()` has not read/written it yet; writing it in plugin.json will not take effect** (can only be set internally by the host program). Pass-through behavior varies by environment: built-in Termux injects via `ProcessBuilder` process environment, proot container inherits this environment; **real Termux uses `RUN_COMMAND` intent, has no environment variable channel, `backendEnv` does not pass through at all** — when variables are needed, please write them directly into `backendStartCommand` (e.g., `export KEY=value; ...`). |

> Legacy fields (still readable and auto-migrated on loading, not recommended for new plugins): `backendRuntime` (`termux`/`proot`), `backendPort`, `backendEntry`, `backendPreCommand`, `backendBinary`, `backendInstallCmd`, `backendCheckCmd`, `backendPhpDocRoot`, `backendJavaClass`, `backendJavaJar`, `backendArgs`.

#### 12.3.4 External Content Reception (openWith, New in v5.4.0)

`openWith` (intent relay) declares that this plugin can receive **text, links, files** passed from the system "Share / Open with other apps". After declaration, the plugin appears in the system share panel's "UIN Tool" entry and the in-app "Select Receiving Plugin" relay page. **Since v5.5.0, the wizard "Basic Information" page can directly configure** (toggle + receiver name + MIME type + text/link/file reception toggle).

```json
"openWith": {
    "enabled": true,
    "label": "Writing Assistant",
    "mimeTypes": "text/*,application/pdf",
    "acceptText": true,
    "acceptUrl": true,
    "acceptFile": true
}
```

| Field | Type | Default | Required | Detailed Description |
|-------|------|---------|----------|---------------------|
| `enabled` | boolean | `true` | No | Whether to enable receiving external content. When `false`, the plugin does not appear in the relay page or system share entry. |
| `label` | string | `""` | No | Display name in the relay page (empty uses plugin `name`). |
| `mimeTypes` | string | `""` | No | Supported file MIME types, **comma-separated string or JSON array both acceptable** (e.g., `"text/*,application/pdf"`; since v5.5.0, `parseStringList()` is compatible with both formats, old versions only recognized comma strings). Matching rules: rule `*/*` always matches; rule ending with `/*` (e.g., `text/*`) compares by type prefix (case-insensitive, rule value lowercased); otherwise exact match; passing empty MIME or empty rule list containing `*/*` means accepting any. |
| `acceptText` | boolean | `true` | No | Whether to accept plain text (`ACTION_SEND`'s `EXTRA_TEXT`). |
| `acceptUrl` | boolean | `true` | No | Whether to accept URLs/links (auto-identifies `http(s)`, `magnet:`, or `Patterns.WEB_URL`). |
| `acceptFile` | boolean | `true` | No | Whether to accept files (`content://` / `file://`, including `SEND_MULTIPLE` multi-select). |

**Matching rules**: The relay page first filters by "content type" (`file` / `text` / `url`) checking `enabled` and `accept*` toggles, then by MIME exact/wildcard matching (rule `*/*` always matches; `xxx/*` matches by category prefix, case-insensitive; otherwise exact match; passing empty MIME, empty rule list, or containing `*/*` means accepting any). When only 1 plugin matches, **auto-opens**; when no matches, the relay page shows "No installed plugins can receive this content"; when multiple matches, displays a candidate list with **real-time search** (filtering by `label` / `pluginId` / `description`), user clicks to open.

**File landing**: After the user selects a plugin, the host copies the shared files into the plugin directory's `.incoming/`, and writes `filePath` (absolute local path **after copying**), `incomingName` (filename after copying), and `containerPath` (proot container mount path `/plugins/<id>/.incoming/<filename>`) into the openData JSON; the plugin backend can mount and read directly.
- **Naming rule**: `<timestamp>_<sanitized original filename>`; illegal characters `\ / : * ? " < > |` and whitespace are uniformly replaced with `_`, max 120 characters retained, preventing same-name/path-character files from overwriting each other.
- Both `content://` and `file://` sources are copied (`file://` can be read directly but is also copied to `.incoming/`, `filePath` points to this copy).
- When copy fails (URI expired / no read permission), the corresponding file retains the original `uri` but has no `filePath` / `containerPath`. `.incoming/` **does not auto-clean**: Web plugin's JS `fs` API is sandboxed to the plugin `data/` directory, cannot directly delete files in `.incoming/`, needs cleanup by the **backend process** (inside proot container: `rm -f /plugins/<id>/.incoming/*`); native plugins can use `getPluginDir()` to access and clean up themselves.

**openData JSON structure** (host injected, read-only by plugin):

| Top-level Field | Type | Description |
|-----------------|------|-------------|
| `kind` / `type` | string | Content type: `file` / `text` / `url` |
| `when` | long | Reception timestamp |
| `mime` | string | MIME declared by sender's intent (from `intent.type`); for `kind=file`, guesses by filename extension if not declared |
| `url` | string | Link when `kind=url` |
| `text` | string | Plain text when `kind=text`; when `kind=url`, if sender also attached `EXTRA_TEXT` along with the link, it is also included |
| `uri` / `name` | string | Original Uri (string) and display filename when `kind=file` |
| `filePath` | string | When `kind=file`: `file://` source writes local path during collection phase; **after selecting plugin and copying into `.incoming/`, overwritten with the absolute path of this copy** |
| `incomingName` / `containerPath` | string | Filename and container path after copying into `.incoming/` (`kind=file`) |
| `files` | array | Array for multi-file (`SEND_MULTIPLE`), each item contains `uri` / `name` / `mime` / `filePath` / `incomingName` / `containerPath` |
| `streamCount` | int | File count (multi-select) |

> **"Text vs link" discrimination**: If the shared plain text is identified as a link (`Patterns.WEB_URL` full string match, or starts with `http://` / `https://` / `magnet:`), the host classifies it as `kind` / `type = url` and sets the `url` field to that text; otherwise classifies as `kind=text`. That is, "sharing a link" vs "sharing text" is automatically distinguished before entering the plugin.

**Reading methods**:
- **Web plugin**: Host injects `window.UINOpenData` (JSON string) and `window.getOpenData()` (equivalent function, returns JSON string) when WebView is created; both return `'{}'` when no data. Can also call `UINPlugin.getOpenData()` (`@JavascriptInterface`, equivalent return, returns `{}` when no data), then `JSON.parse()` to read.
- **Native plugin**: When the view is loaded, the host calls `PluginInterface.onHostEvent("host.open", Bundle)`, Bundle contains `instanceId`, `openDataJson` (JSON string, may be null), `multiInstanceEnabled`.
- Data is only passed with the **current session**, **not persisted**; re-opening the plugin (without new intent) returns `{}` from `getOpenData()`.

**Host-received Intent-filter list** (declared in `AndroidManifest.xml` by `PluginOpenDispatchActivity`):

| Action | MIME | Scenario |
|--------|------|----------|
| `ACTION_SEND` | `text/*` | Share plain text / link |
| `ACTION_SEND` | `*/*` | Share any single file |
| `ACTION_SEND_MULTIPLE` | `*/*` | Share multiple files |
| `ACTION_VIEW` | `application/*`, `audio/*`, `image/*`, `message/*`, `multipart/*`, `text/*`, `video/*` | "Open with other apps" file |

> ⚠️ The `ACTION_VIEW` branch **does not have** `*/*` wildcard, and does not include `font/*`, `model/*`, etc. categories; unknown type files not in the above list can only be dispatched to UIN Tool via "Share" (`ACTION_SEND`) or external explicit intents (see below).

**External apps directly invoking plugin (skipping relay page)**

`PluginHostActivity` is declared as `exported="true"`, any app can send an explicit intent to it to directly open a plugin and deliver data; the result is equivalent to opening after openWith relay completion:

| Extra | Type | Description |
|-------|------|-------------|
| `plugin_id` | String | **Required**, plugin ID to open (`PluginHostActivity.EXTRA_PLUGIN_ID`) |
| `instance_id` | String | Optional, instance ID (specifies target instance for multi-instance; auto-generated if omitted) |
| `open_data` | String | Optional, openData JSON string (structure see table above), plugin side reads with `getOpenData()` / `onHostEvent("host.open")` |

```kotlin
// Third-party app example: no relay page needed, directly delivers text to a plugin supporting openWith
val intent = Intent(Intent.ACTION_VIEW).apply {
    setClassName("com.UIN.Tool", "com.UIN.Tool.plugin.PluginHostActivity")
    putExtra("plugin_id", "com.example.editor")
    putExtra("open_data",
        """{"kind":"text","type":"text","when":1700000000000,"text":"Content from third-party app"}"""
    )
    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
context.startActivity(intent)
```

> Third-party apps can also directly send `ACTION_SEND` + `text/*` / `*/*` (or `SEND_MULTIPLE`) to `PluginOpenDispatchActivity`, letting users select a plugin from the relay page; effect is consistent with the system share panel.

#### 12.3.5 Resource Limit Fields

> **Reserved fields**: Model declared and participates in JSON read/write, but host currently does not enforce. Since v5.5.0, the development wizard **no longer provides** these input fields, nor writes them to wizard-generated plugin.json; they can still be retained when manually writing plugin.json.

| Field | Type | Default | Detailed Description |
|-------|------|---------|---------------------|
| `maxMemory` | int | `512` | Backend memory limit (MB, reserved, not currently enforced). |
| `maxCpuTime` | int | `60` | Backend CPU time limit (seconds, reserved, not currently enforced). |
| `maxConcurrentTasks` | int | `5` | Concurrent task limit (reserved, not currently enforced). |

#### 12.3.6 Minimum Configuration for Each Type

**Native Plugin**

```json
{
    "pluginId": "com.example.native",
    "name": "Native Example",
    "version": 1,
    "versionName": "1.0.0",
    "uiType": "native",
    "mainClass": "com.example.MainPlugin"
}
```

**Web Plugin (Without Backend)**

```json
{
    "pluginId": "com.example.web",
    "name": "Web Example",
    "version": 1,
    "versionName": "1.0.0",
    "uiType": "web",
    "entry": "web/index.html"
}
```

**Web Plugin (With Backend)**

```json
{
    "pluginId": "com.example.webapi",
    "name": "Web Backend Example",
    "version": 1,
    "versionName": "1.0.0",
    "uiType": "web",
    "entry": "web/index.html",
    "backend": "other",
    "backendStartCommand": "sh scripts/start.sh",
    "backendStartEntry": "scripts/start.sh",
    "backendAutoStart": true,
    "backendTimeout": 30,
    "backendHealthCheck": "/health"
}
```

**CUI Plugin**

```json
{
    "pluginId": "com.example.cui",
    "name": "CUI Example",
    "version": 1,
    "versionName": "1.0.0",
    "uiType": "cui",
    "entry": "",
    "backendPreCommand": "export PLUGIN_ID=com.example.cui PLUGIN_DIR=$(pwd); python3 scripts/script.py"
}
```

### 12.4 Export Built-in Templates (v4.5.0)

Click "**Export Template**" on the "Dev" page; the app copies built-in packaged plugins from `assets/test_plugins/` to `/storage/emulated/0/UIN_Tool/templates/` and auto-generates a `README.txt` description. These templates can be directly imported for experience, covering CUI terminal, custom backend, Termux backend, full API test, storage test, native plugin, Web pure frontend, and other types.

| Template File | Description |
|---------------|-------------|
| `com.example.cuitest.tpk` | CUI terminal plugin example (full-screen terminal executing scripts) |
| `com.example.othertest.tpk` | Custom backend plugin example (other mode, startup command launched) |
| `com.example.termuxtest.tpk` | Termux backend plugin example (Python backend) |
| `com.test.allapi.tpk` | Full API test plugin |
| `com.test.storage.tpk` | Storage test plugin |
| `com.uin.compression.tpk` | File compression tool plugin example (Web + backend) |
| `NativeTestPlugin.tpk` | Native plugin example |
| `web_plugin_template.tpk` | Web pure frontend plugin template (no backend, no `plugin.dex`/`src/`) |

---

## XIII. Publishing to Plugin Repository

### 13.1 Repository Requirements

| Requirement | Description |
|-------------|-------------|
| Repository name | Must be the plugin ID (e.g., `com.example.myplugin`) |
| Repository description | Must be the plugin name |
| Release Tag | Format: `{version code}-{version name}` (host splits at first `-`, e.g., `1-1.0.0`) |
| Release assets | Must contain `.tpk` file (host takes first .tpk asset, no integrity check) |
| Repository visibility | Must be public |

> Note: Host reads plugin lists paginated with `repos?per_page=100` (100 per page limit), and filters repositories whose names start with `.github` or are `Docs`/`docs`.

### 13.2 Publishing Steps

1. Create GitHub repository (in UIN-Tool-Plugins organization), repository name = plugin ID
2. Upload plugin files
3. Create Release (Tag: 1-1.0.0)
4. Upload .tpk file to Assets
5. Force update: Tag format `{version code}-{version name}-1` (e.g., `2-1.0.1-1`; note the host splits at first `-`, so version name will be parsed as `1.0.1-1`)

---

## XIV. Terminal Features

### 14.1 Overview

UIN Tool includes a complete terminal environment; core engine is based on Termux adaptation.

### 14.2 Terminal Features

| Feature | Description |
|---------|-------------|
| Shell | Default bash; zsh, fish, etc. need `pkg install` to install |
| Package Manager | `pkg`/`apt` (Termux's own software source termux-packages, not Debian/Ubuntu sources) |
| Development Tools | gcc, clang, make, git |
| Script Languages | Python, Node.js, Ruby, etc. (install with `pkg install`) |
| Network Tools | curl, wget, openssh |
| Multi-Session | Multiple terminal sessions running simultaneously |
| Multi-Window | Android 7.0+ multi-window support (new window button) |
| Safe Mode | New sessions can enable safe mode |

Terminal environment variables: `HOME=/data/data/com.UIN.Tool/files/home`, `PREFIX=/data/data/com.UIN.Tool/files/usr`, `TMPDIR=$PREFIX/tmp`, `PATH=$PREFIX/bin`, etc. Sessions are managed by `TermuxService` (foreground service + notification); `TermuxActivity` destruction/recreation does not interrupt sessions.

### 14.3 Common Commands

```bash
# Update package sources (Termux's own repository)
pkg update

# Install Python
pkg install python

# Install Node.js
pkg install nodejs

# Install git
pkg install git

# SSH to server
ssh user@hostname
```

---

## XV. UI Personalization Development

### 15.1 Color System

```kotlin
val uiConfig = UIConfig.getInstance()

// Get color
val primaryColor = uiConfig.getPrimaryColor()
val textPrimaryColor = uiConfig.getTextPrimaryColor()

// Update color
uiConfig.updateColor("primary", "#FF1A3A4A")

// Save configuration
uiConfig.saveConfig()
```

### 15.2 Color Configuration Items

| Category | Color Items |
|----------|-------------|
| Primary Colors | primary, primary_dark, primary_light, accent |
| Auxiliary Colors | success, warning, error, info |
| Text Colors | text_primary, text_secondary, text_hint, text_primary_inverse |
| Background Colors | background, surface, surface_variant |
| Border Colors | divider, glass_background, disabled |

### 15.3 Shape Configuration

```kotlin
// Get corner radius
val cornerRadius = uiConfig.getCardCornerRadius()
val buttonRadius = uiConfig.getButtonCornerRadius()

// Update corner radius
uiConfig.updateShape("cardCornerRadius", 16)
uiConfig.updateShape("buttonCornerRadius", 12)
```

### 15.4 Effects Configuration (Updated in v5.6.0)

| Effect | Description |
|--------|-------------|
| Gradient Background | Global gradient background, supports single/multi-select modes and 6-direction settings (new in v5.3.0) |
| Glass Effect | Frosted glass texture UI components (semi-transparent background, no border, no shadow) |
| Neumorphism Style | Soft concave-convex light-shadow effect UI components (new in v5.6.0, mutually exclusive with glass effect) |
| Ripple Effect | Click ripple feedback toggle |
| Translucent Effect Transparency | Glass effect / Neumorphism effect alpha transparency adjustment (new in v5.6.0) |

### 15.5 Multilingual Support (New in v5.6.0)

Supports directly switching display language within the app (Chinese/English, etc.) without changing the system language. Language configuration participates in save/reset/export/import.

---

## XVI. Debugging Tips

### 16.1 Log Output

Native plugin:

```kotlin
import com.UIN.Tool.log.Logger

Logger.i("TAG", "Info")
Logger.e("TAG", "Error", exception)
```

Web plugin:

```javascript
UINPlugin.callHost('log', 'Debug info');
console.log('Console output');
```

Backend Python:

```python
print("Debug info")
```

### 16.2 Viewing Runtime Logs

· View in "Manage" > "Development Tools" (includes runtime logs and developer options)
· Crash logs are auto-saved; the next time you open the app, it automatically jumps to this page
· Log location: /storage/emulated/0/UIN_Tool/logs/

### 16.3 WebView Remote Debugging

1. Open chrome://inspect in Chrome browser
2. Ensure WebView debugging is enabled
3. Supports breakpoints, console, network monitoring

---

## XVII. FAQ

Q1: Plugin import fails?

Possible reasons: file is not a valid .tpk format, missing plugin.json, JSON format error, signature verification failed.

Q2: Native plugin compilation fails?

Current status: native plugin compilation is temporarily disabled. It is recommended to use Web plugins instead.

Q3: Web plugin changes not taking effect?

After modifying HTML/CSS/JS in a Web plugin, close and reopen the plugin; no recompilation needed.

Q4: Plugin cannot call host permissions?

Grant required permissions to the plugin in "Manage" > "Permission Management" > "Plugin Permissions".

Q5: How to debug plugins?

Use Logger to output logs, view in "Manage" > "Development Tools". Web plugins can be debugged with Chrome DevTools.

Q6: Where is plugin data stored?

Data is stored in /storage/emulated/0/UIN_Tool/plugins/{pluginId}/data/ directory, KV data is stored in SharedPreferences.

Q7: Will updating a plugin lose data?

No. The data/ directory is automatically preserved when updating plugins, user data is not lost.

Q8: Will permission status be persisted?

Yes. After authorization, permission status is permanently saved; no duplicate popups on next opening.

Q9: What APIs do Web plugins support?

Supports 170+ APIs, covering storage, files, network, device info, sensors, system operations, permissions, etc.

Q10: How to export plugin data?

Web plugins can use UINPlugin.exportData() to export all data as JSON format.

Q11: How to clear plugin data?

Web plugins can use UINPlugin.clearStorage() and UINPlugin.clearCache().

Q12: How to reset plugin permissions?

Since v5.5.0, permission status is managed per permission (granted / blocked), and can be reset (grant / revoke / block) or the "Don't prompt/Don't show" setting removed for Web plugins on the "Plugin Management > Permissions" page; native plugin permissions are managed by the system. The old `clearPermissionState()` (permission_state single value) interface is deprecated.

Q13: How do CUI plugin scripts get the plugin ID and directory?

Terminal sessions do not auto-inject environment variables; you must manually export them in the startup command, e.g.: `export PLUGIN_ID=com.example.xxx PLUGIN_DIR=$(pwd); python3 scripts/script.py`.

Q14: How to export built-in plugin templates?

Click "Export Template" on the "Dev" page; the 7 built-in packaged plugins will be copied to `/storage/emulated/0/UIN_Tool/templates/`, and a README.txt will be generated.

Q15: Export template keeps showing "Exporting..."?

This issue existed in older versions, fixed in v4.5.0: export now runs on a background thread, automatically resetting state when complete.

---

## XVIII. Best Practices

### 18.1 Naming Conventions

· Plugin ID: Reverse domain name, e.g., com.example.myplugin
· Class name: PascalCase, e.g., MainPlugin
· Package name: Consistent with plugin ID

### 18.2 Performance Optimization

· Avoid time-consuming operations in onCreateView
· Use coroutines for async tasks
· Web plugins optimize images and CSS selectors
· Stop sensors promptly after use

### 18.3 Data Storage Best Practices

· Use setStorageJSON for complex data structures
· Regularly clean up cache data
· Do not store sensitive data in plaintext
· Use exportData and importData to backup user data
· Pay attention to data compatibility when upgrading plugin versions

### 18.4 Security

· Do not store sensitive information in plaintext
· Validate input data
· Use HTTPS
· Validate file paths to prevent directory traversal

### 18.5 Version Management

· Use semantic versioning
· Use correct Release Tag format when publishing
· Use -1 suffix for force updates

---

## XIX. Technical Support

### 19.1 Contact Information

| Channel | Contact Info |
|---------|-------------|
| Email | undefinedinvalidnull@outlook.com |
| GitHub | https://github.com/Undefined-Invalid-Null/UIN-Tool |
| Plugin Repository | https://github.com/UIN-Tool-Plugins |
| QQ Group | 511875883 |

---

Document Information

| Item | Info |
|------|------|
| Document Version | 5.6.0 |
| Corresponding App Version | v5.6.0 (Build 22) |
| Last Updated | August 28, 2026 |

---

© 2026 UIN Team. All Rights Reserved.
