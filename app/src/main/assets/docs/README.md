# UIN Tool 开发文档

## 版本信息

| 项目 | 信息 |
|------|------|
| 文档版本 | 4.5.0 |
| 对应应用版本 | v4.5.0 (Build 15) |
| 最后更新 | 2026年8月4日 |

---

## 📑 目录

### 一、快速开始
- [1.1 创建插件](#11-创建插件)
- [1.2 配置插件信息](#12-配置插件信息)
- [1.3 编写代码](#13-编写代码)
- [1.4 编译与打包](#14-编译与打包)
- [1.5 导入运行](#15-导入运行)

### 二、插件类型
- [2.1 对比表格](#21-对比表格)
- [2.2 如何选择](#22-如何选择)

### 三、原生插件开发
- [3.1 基本结构](#31-基本结构)
- [3.2 支持的 Android 控件](#32-支持的-android-控件)
- [3.3 布局示例](#33-布局示例)
- [3.4 访问插件资源](#34-访问插件资源)
- [3.5 插件数据存储 API](#35-插件数据存储-api)

### 四、Web 插件开发（无后端）
- [4.1 目录结构](#41-目录结构)
- [4.2 plugin.json 配置](#42-pluginjson-配置)
- [4.3 HTML 模板示例](#43-html-模板示例)
- [4.4 JavaScript 示例](#44-javascript-示例)

### 五、Web 插件开发（带后端）
- [5.1 概述](#51-概述)
- [5.2 支持的后端语言](#52-支持的后端语言)
- [5.3 Python 后端开发](#53-python-后端开发)
- [5.4 Node.js 后端开发](#54-nodejs-后端开发)
- [5.5 PHP 后端开发](#55-php-后端开发)
- [5.6 二进制后端开发](#56-二进制后端开发)
- [5.7 前端与后端通信](#57-前端与后端通信)
- [5.8 后端 API 规范](#58-后端-api-规范)

### 六、CUI 终端插件开发（v4.5.0 新增）
- [6.1 CUI 插件是什么](#61-cui-插件是什么)
- [6.2 目录结构与 plugin.json](#62-目录结构与-pluginjson)
- [6.3 创建 CUI 插件（向导）](#63-创建-cui-插件向导)
- [6.4 终端脚本开发](#64-终端脚本开发)
- [6.5 运行流程与生命周期](#65-运行流程与生命周期)
- [6.6 CUI 与后端/Proot 的关系](#66-cui-与后端proot-的关系)

### 七、插件数据持久化存储（v4.4.0 新增）
- [7.1 概述](#71-概述)
- [7.2 数据目录结构](#72-数据目录结构)
- [7.3 Web 插件存储 API](#73-web-插件存储-api)
- [7.4 原生插件存储 API](#74-原生插件存储-api)
- [7.5 数据迁移](#75-数据迁移)
- [7.6 数据版本管理](#76-数据版本管理)

### 八、权限系统（v4.4.0 完善）
- [8.1 权限状态管理](#81-权限状态管理)
- [8.2 权限状态值](#82-权限状态值)
- [8.3 权限请求的实际流程](#83-权限请求的实际流程)
- [8.4 权限声明](#84-权限声明)
- [8.5 权限类型](#85-权限类型)

### 九、插件说明功能
- [9.1 概述](#91-概述)
- [9.2 配置方法](#92-配置方法)
- [9.3 用户交互](#93-用户交互)

### 十、PluginInterface 接口详解
- [10.1 方法说明](#101-方法说明)
- [10.2 完整实现示例](#102-完整实现示例)

### 十一、JavaScript API 完整参考（v4.5.0）
- [11.1 基础 API](#111-基础-api)
- [11.2 存储 API](#112-存储-api)
- [11.3 文件系统 API](#113-文件系统-api)
- [11.4 网络请求 API](#114-网络请求-api)
- [11.5 设备信息 API](#115-设备信息-api)
- [11.6 传感器 API](#116-传感器-api)
- [11.7 系统 API](#117-系统-api)
- [11.8 权限 API](#118-权限-api)
- [11.9 后端通信 API](#119-后端通信-api)
- [11.10 数据统计 API](#1110-数据统计-api)

### 十二、打包与导入
- [12.1 打包方式](#121-打包方式)
- [12.2 文件结构](#122-文件结构)
- [12.3 plugin.json 完整字段](#123-pluginjson-完整字段)

### 十三、发布到插件仓库
- [13.1 仓库要求](#131-仓库要求)
- [13.2 发布步骤](#132-发布步骤)

### 十四、终端功能
- [14.1 概述](#141-概述)
- [14.2 终端特性](#142-终端特性)
- [14.3 常用命令](#143-常用命令)

### 十五、UI 个性化开发
- [15.1 颜色系统](#151-颜色系统)
- [15.2 颜色配置项](#152-颜色配置项)
- [15.3 形状配置](#153-形状配置)

### 十六、调试技巧
- [16.1 日志输出](#161-日志输出)
- [16.2 查看运行日志](#162-查看运行日志)
- [16.3 WebView 远程调试](#163-webview-远程调试)

### 十七、常见问题
- [17.1 Q1-Q15](#十七常见问题)

### 十八、最佳实践
- [18.1 命名规范](#181-命名规范)
- [18.2 性能优化](#182-性能优化)
- [18.3 数据存储最佳实践](#183-数据存储最佳实践)
- [18.4 安全性](#184-安全性)
- [18.5 版本管理](#185-版本管理)

### 十九、技术支持
- [19.1 联系方式](#191-联系方式)

---

## 一、快速开始

### 1.1 创建插件

1. 打开 UIN Tool App
2. 点击底部导航栏的「**开发**」标签
3. 点击「**创建插件**」按钮
4. 选择前端类型：
   - **原生 UI**：Android View 原生界面
   - **纯 WebView**：仅 HTML/CSS/JS，无后端
   - **WebView + 后端**：HTML/CSS/JS + 后端服务
   - **CUI 终端**：全屏终端中运行脚本（v4.5.0 新增）
5. 如选择「WebView + 后端」，选择后端语言：
   - Python（推荐，使用内置 http.server）
   - Node.js
   - PHP
   - 二进制文件
   - 自定义（other，宿主不自动启动，由启动前命令手动拉起）
6. 按照向导完成配置

### 1.2 配置插件信息

| 字段 | 说明 | 示例 | 必填 |
|------|------|------|------|
| 插件ID | 唯一标识符，域名倒序格式 | com.example.myplugin | ✅ |
| 插件名称 | 在列表中显示的名称 | 我的插件 | ✅ |
| 作者 | 开发者名称 | 张三 | ❌ |
| 描述 | 插件功能说明 | 这是一个示例插件 | ❌ |
| 插件说明 | 首次打开显示的说明 | 欢迎使用！ | ❌ |
| 版本号 | 数字版本，用于版本比较 | 1 | ✅ |
| 版本名 | 显示版本号 | 1.0.0 | ✅ |
| 主类名 | 入口类的完整路径（原生插件） | com.example.MainPlugin | ✅ |
| 入口文件 | Web 插件入口（Web 插件） | web/index.html | ✅ |

### 1.3 编写代码

根据选择的插件类型，编写对应的代码。详见下方各章节。

### 1.4 编译与打包

**原生插件（当前状态）**
- 应用内编译功能暂时不可用：向导打包出的 `plugin.dex` 为占位文本，无法加载
- 建议使用 Web 插件，或使用 PC 端编译出真实 `plugin.dex` 后放入插件目录

**Web 插件（推荐）**
- 无需编译：修改 HTML/CSS/JS 后直接生效
- 向导会自动生成空白模板文件

**带后端的 Web 插件**
- 后端文件（server.py / server.js / index.php）自动生成空白模板
- 无需额外编译
- ⚠️ 生成后需手动修正 `plugin.json`：将 `entry` 改回 `web/index.html`（向导会误写为后端脚本路径），并把 `scripts/` 后端文件放入插件安装目录（当前打包器不打包 `scripts/`）

### 1.5 导入运行

1. 点击底部「**管理**」→「**插件管理**」
2. 点击「**导入**」选择生成的 TPK 文件
3. 等待导入完成
4. 在「**工具**」页面中点击插件运行

---

## 二、插件类型

### 2.1 对比表格

| 特性 | 原生插件 | 纯 Web 插件 | Web + 后端插件 | CUI 终端插件 |
|------|----------|-------------|----------------|--------------|
| 开发语言 | Kotlin/Java | HTML/CSS/JS | HTML/CSS/JS + 后端语言 | Python/Shell 脚本 |
| UI 开发方式 | 代码动态创建 | HTML 布局 | HTML 布局 | 全屏终端 |
| 开发效率 | 中等 | 高 | 高 | 高 |
| 运行性能 | 高 | 中等 | 中等 | 高 |
| 热更新 | 需重新编译 | 无需编译 | 无需编译 | 无需编译 |
| 后端支持 | 无 | 无 | Python/Node.js/PHP/二进制/Deno/Go 等 12 种 | 可选（见 6.6） |
| 数据持久化 | ✅ PluginContext | ✅ UINPlugin API | ✅ UINPlugin API | ✅ 脚本内自行处理 |
| 学习成本 | 需懂 Android | 懂前端即可 | 懂前端 + 后端 | 懂脚本即可 |
| 编译方式 | 需要编译 | 无需编译 | 无需编译 | 无需编译 |
| 适用场景 | 需要访问系统 API | 快速原型/已有 Web 项目 | 需要后端计算 | 命令行工具/脚本 |

### 2.2 如何选择

| 场景 | 推荐类型 |
|------|----------|
| 需要访问 Android 系统 API | 原生插件 |
| 快速原型开发 | Web 插件 |
| 已有 Web 项目 | Web 插件 |
| 需要后端计算或数据处理 | Web + 后端插件 |
| 需要持久化存储数据 | Web 插件（使用 setStorage） |
| 需要调用 Linux 命令 | Web + Python/Node.js |
| 命令行工具 / 脚本自动化 / 交互式 REPL | CUI 终端插件 |

---

## 三、原生插件开发

### 3.1 基本结构

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
            text = "我的插件"
            textSize = 24f
            setTextColor(0xFF37474F.toInt())
            setPadding(0, 0, 0, 20)
        }

        val counterText = TextView(appContext).apply {
            text = "点击次数: 0"
            textSize = 16f
            setTextColor(0xFF666666.toInt())
            setPadding(0, 0, 0, 20)
        }

        val button = Button(appContext).apply {
            text = "点击我"
            setBackgroundColor(0xFF37474F.toInt())
            setTextColor(0xFFFFFFFF.toInt())
            setOnClickListener {
                clickCount++
                counterText.text = "点击次数: $clickCount"
                Toast.makeText(context, "点击了 $clickCount 次", Toast.LENGTH_SHORT).show()
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

### 3.2 支持的 Android 控件

| 控件 | 说明 | 常用方法 |
|------|------|----------|
| TextView | 文本显示 | setText(), setTextSize(), setTextColor() |
| EditText | 文本输入 | getText(), setHint() |
| Button | 按钮 | setText(), setOnClickListener() |
| ImageView | 图片显示 | setImageResource(), setImageBitmap() |
| LinearLayout | 线性布局 | setOrientation(), setGravity() |
| RelativeLayout | 相对布局 | addRule() |
| FrameLayout | 帧布局 | 层叠视图 |
| ScrollView | 滚动视图 | 包裹内容 |
| ProgressBar | 进度条 | setProgress(), setVisibility() |
| CheckBox | 复选框 | setChecked(), isChecked() |
| Switch | 开关 | setChecked(), isChecked() |

### 3.3 布局示例

```kotlin
// 线性布局（垂直）
val layout = LinearLayout(appContext).apply {
    orientation = LinearLayout.VERTICAL
    layoutParams = ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    )
}

// 线性布局（水平）
val rowLayout = LinearLayout(appContext).apply {
    orientation = LinearLayout.HORIZONTAL
    gravity = Gravity.CENTER_VERTICAL
}
```

### 3.4 访问插件资源

`onCreateView` 的 `context` 参数实为 `PluginContext`（继承 `ContextWrapper`），可直接强转后访问插件目录、插件资源和数据：

```kotlin
// 获取插件目录（插件安装目录绝对路径，勿用 context.filesDir）
val pctx = context as PluginContext
val pluginDir = pctx.getPluginDir()                 // /storage/emulated/0/UIN_Tool/plugins/{pluginId}
val pluginDataDir = pctx.getPluginDataDir()         // .../data/（文件沙箱，自动创建）
val pluginCacheDir = pctx.getPluginCacheDir()       // .../cache/（自动创建）

// 读取插件安装目录下文件
val configFile = File(pctx.getPluginFilePath("config.json"))
if (configFile.exists()) {
    val content = configFile.readText()
}

// 读取图片（icon.png 位于插件安装目录）
val iconFile = File(pctx.getPluginFilePath("icon.png"))
if (iconFile.exists()) {
    val bitmap = BitmapFactory.decodeFile(iconFile.absolutePath)
    imageView.setImageBitmap(bitmap)
}

// 插件资源（res/ 与安装目录经 AssetManager 挂载，可直接访问）
val res = pctx.resources               // 已重定向到插件资源
val assets = pctx.assets                // 含插件目录 + 插件 res/
```

> ⚠️ `PluginContext` 覆写了 `getDataDir()`/`getCacheDir()`（废弃别名返回插件 data/ 与 cache/），也覆写了 `getAssets()`/`getResources()`（重定向到插件目录）。它**没有**覆写 `getFilesDir()`——返回的是宿主 App 内部 files 目录，**不是**插件目录，请勿用它定位插件文件。

### 3.5 插件数据存储 API

`onCreateView` 收到的 `context` 已经是 `PluginContext`，无需自行构造；以下 API 均基于该实例：

```kotlin
// 获取 PluginContext（onCreateView 中可直接用传入的 context）
val pctx = context as PluginContext

// ============ KV 存储（SharedPreferences: plugin_data_{pluginId}）============
pctx.putString("username", "JohnDoe")
val username = pctx.getString("username", "Guest")

pctx.putInt("score", 100)
val score = pctx.getInt("score", 0)

pctx.putLong("longKey", 123456789L)          // 长整数
val longVal = pctx.getLong("longKey", 0L)

pctx.putBoolean("isLoggedIn", true)
val isLoggedIn = pctx.getBoolean("isLoggedIn", false)

pctx.putFloat("floatKey", 3.14f)             // 浮点
val floatVal = pctx.getFloat("floatKey", 0f)

val json = JSONObject().apply {
    put("theme", "dark")
    put("fontSize", 14)
}
pctx.putJSON("config", json)
val config = pctx.getJSON("config")

pctx.remove("temp_data")                      // 删除键
val exists = pctx.contains("username")        // 检查键
val keys = pctx.getAllKeys()                  // 全部键
val entries = pctx.getAllEntries()            // 全部键值
pctx.clearAll()                               // 清空全部 KV

// ============ 文件存储（沙箱根目录 = 插件 data/）============
pctx.writeFile("notes.txt", "Hello World")    // 写文本（自动检查磁盘空间）
val content = pctx.readFile("notes.txt")      // 读文本，不存在返回 null
pctx.writeFileBytes("bin.dat", byteArrayOf(1, 2, 3))  // 写字节
val bytes = pctx.readFileBytes("bin.dat")     // 读字节

pctx.deletePluginFile("notes.txt")            // 删除文件
val files = pctx.listPluginFiles()            // 列出 data/ 下文件名
val fileExists = pctx.fileExists("notes.txt")
val size = pctx.getPluginFileSize("notes.txt")

// ============ 缓存管理 ============
pctx.clearPluginCache()                       // 删除并重建 cache/

// ============ 全部数据清理 ============
pctx.deleteAllPluginData()                    // 清空 KV + 删除 data/ + cache/

// ============ 数据统计 ============
val stats = pctx.getStorageStats()
println("KV数量: ${stats.kvCount}")
println("文件数量: ${stats.fileCount}")
println("总大小: ${stats.totalFileSize}")
println("缓存大小: ${stats.cacheSize}")

// ============ 数据版本管理 ============
pctx.setDataVersion(2)                        // 写入版本号
val dataVersion = pctx.getDataVersion()       // 读取（默认 0）
pctx.markDataMigrated()                       // 标记已迁移
val migrated = pctx.isDataMigrated()

// ============ 权限状态管理 ============
val state = pctx.getPermissionState()         // 0/1/2
pctx.setPermissionState(1)  // 1=已授权
pctx.shouldShowPermissionDialog()             // state==0 时 true
pctx.getPermissionStateDescription()          // "未授权"/"已授权"/"已拒绝"
pctx.clearPermissionState()                   // 重置为未授权
```

---

## 四、Web 插件开发（无后端）

### 4.1 目录结构

```
your-plugin/
├── plugin.json          # 插件配置文件（必需）
├── icon.png             # 插件图标（建议 128x128）
└── web/                 # Web 资源目录（必需）
    ├── index.html       # 主页面（必需）
    ├── style.css        # 样式文件（可选）
    └── script.js        # JavaScript 文件（可选）
```

### 4.2 plugin.json 配置

```json
{
    "pluginId": "com.example.webplugin",
    "version": 1,
    "versionName": "1.0.0",
    "minHostVersion": 1,
    "name": "Web插件示例",
    "author": "开发者名称",
    "description": "这是一个Web插件示例",
    "notice": "欢迎使用 Web 插件！",
    "icon": "icon.png",
    "mainClass": "",
    "apiLevel": 21,
    "uiType": "web",
    "entry": "web/index.html",
    "permissions": "android.permission.INTERNET,android.permission.VIBRATE"
}
```

### 4.3 HTML 模板示例

```html
<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>我的Web插件</title>
    <link rel="stylesheet" href="style.css">
</head>
<body>
    <div class="container">
        <h1>我的插件</h1>
        <button onclick="showToast()">显示提示</button>
        <button onclick="closePlugin()">关闭插件</button>
        <button onclick="saveData()">保存数据</button>
        <button onclick="loadData()">读取数据</button>
    </div>
    <script src="script.js"></script>
</body>
</html>
```

### 4.4 JavaScript 示例

```javascript
// ============ 基础功能 ============
function showToast() {
    UINPlugin.callHost('toast', 'Hello from WebView!');
}

function closePlugin() {
    UINPlugin.callHost('finish', '');
}

// ============ 存储 API ============
function saveData() {
    UINPlugin.setStorage('username', 'JohnDoe');
    UINPlugin.setStorageInt('score', 100);
    UINPlugin.setStorageJSON('config', JSON.stringify({theme: 'dark'}));
    UINPlugin.callHost('toast', '数据已保存');
}

function loadData() {
    const name = UINPlugin.getStorage('username');
    const score = UINPlugin.getStorageInt('score', 0);
    const config = JSON.parse(UINPlugin.getStorageJSON('config'));
    UINPlugin.callHost('toast', `用户: ${name}, 分数: ${score}`);
}

// ============ 文件操作 ============
function saveFile() {
    UINPlugin.writeFile('notes.txt', 'Hello World');
    UINPlugin.callHost('toast', '文件已保存');
}

function readFile() {
    const content = UINPlugin.readFile('notes.txt');
    UINPlugin.callHost('toast', '内容: ' + content);
}

// ============ 批量操作 ============
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
    console.log('批量读取:', result);
}

// ============ 存储统计 ============
function getStats() {
    const stats = JSON.parse(UINPlugin.getStorageStats());
    UINPlugin.callHost('toast', `KV: ${stats.kvCount}, 文件: ${stats.fileCount}`);
}

// ============ 生命周期 ============
document.addEventListener('DOMContentLoaded', () => {
    console.log('Web 插件已加载');
});

window.addEventListener('resume', () => { console.log('插件恢复'); });
window.addEventListener('pause', () => { console.log('插件暂停'); });
window.addEventListener('destroy', () => { console.log('插件销毁'); });
```

---

## 五、Web 插件开发（带后端）

### 5.1 概述

v4.2.0 新增功能：Web 插件可启动 Termux 后端服务，提供计算、数据处理、系统命令执行等能力。

后端服务特点：

· 自动启动：用户打开插件时自动启动后端（`backendAutoStart: true`），完全无感知
· 进程管理：插件关闭时自动停止后端（可配置保持运行）
· HTTP 通信：使用 HTTP API，无需 WebSocket
· 多语言支持：Python、Node.js、PHP、二进制程序、Deno、Go、Ruby、Perl、Lua、Java、Rust，以及自定义 other 模式

### 5.2 支持的后端语言

| 后端类型 | 启动命令（termux 运行时） | 入口文件 | 适用场景 |
|------|------|----------|----------|
| python | `python`（自动探测路径） | server.py | 数据处理、AI、Web 服务 |
| node | `node` | server.js | Web 服务、实时应用 |
| php | `php -S 127.0.0.1:<port> -t <docRoot>` | index.php | Web 服务 |
| binary | 可执行文件 | 任意 | 已有程序、系统工具 |
| deno | `deno run --allow-net --allow-read` | server.ts | 现代 TypeScript 服务 |
| go | `go run` | main.go | 编译型后端 |
| ruby | `ruby` | server.rb | 脚本服务 |
| perl | `perl` | server.pl | 脚本服务 |
| lua | `lua` | server.lua | 轻量脚本 |
| java | `java -jar <jar>` / `java -cp <dir> <class>` | Main.java | 企业级后端 |
| rust | `cargo run --release --bin <name>` | main.rs | 高性能后端 |
| other | 宿主不启动，由启动前命令手动拉起 | — | 常驻服务、手动启动 |

> 安装命令：未配置 `backendInstallCmd` 时，宿主按语言给出默认 `pkg install` 命令（python→`pkg install python`、node→`pkg install nodejs`、php→`pkg install php`、java→`pkg install openjdk-17` 等）。环境检查命令同理（`python --version`、`node --version` 等）。

### 5.3 Python 后端开发

目录结构

```
your-plugin/
├── plugin.json
├── icon.png
├── web/
│   ├── index.html
│   ├── style.css
│   └── script.js
└── scripts/
    └── backend/
        └── server.py          # Python 后端入口
```

plugin.json 配置

```json
{
    "pluginId": "com.example.pythonbackend",
    "version": 1,
    "versionName": "1.0.0",
    "uiType": "web",
    "entry": "web/index.html",
    "backend": "python",
    "backendPort": 8000,
    "backendEntry": "scripts/backend/server.py",
    "backendAutoStart": true,
    "backendTimeout": 30
}
```

server.py 模板

```python
#!/usr/bin/env python
# -*- coding: utf-8 -*-
import sys
import os
import json
import time
from http.server import HTTPServer, BaseHTTPRequestHandler
from socketserver import ThreadingMixIn

PORT = int(os.environ.get("PORT", 8000))
PLUGIN_DIR = os.environ.get("PLUGIN_DIR", ".")

class SimpleHandler(BaseHTTPRequestHandler):
    def do_GET(self):
        if self.path == '/' or self.path == '/health':
            self.send_json(200, {"status": "healthy", "timestamp": time.time()})
        else:
            self.send_json(404, {"error": "Not Found"})

    def do_POST(self):
        content_length = int(self.headers.get('Content-Length', 0))
        body = self.rfile.read(content_length).decode('utf-8') if content_length > 0 else '{}'
        try:
            data = json.loads(body) if body else {}
        except:
            data = {}

        if self.path == '/api/compute':
            self.handle_compute(data)
        elif self.path == '/api/echo':
            self.handle_echo(data)
        else:
            self.send_json(404, {"error": "Not Found"})

    def handle_compute(self, data):
        try:
            expression = data.get('expression', '')
            safe_dict = {
                "sum": lambda x: sum(x) if x else 0,
                "len": len, "abs": abs,
                "min": lambda x: min(x) if x else None,
                "max": lambda x: max(x) if x else None,
                "round": round, "pow": pow,
                "sqrt": lambda x: x ** 0.5 if x >= 0 else None,
                "pi": 3.141592653589793,
            }
            if expression:
                result = eval(expression, {"__builtins__": {}}, safe_dict)
            else:
                result = {"message": "No expression"}
            self.send_json(200, {"status": "ok", "result": result})
        except Exception as e:
            self.send_json(400, {"status": "error", "error": str(e)})

    def handle_echo(self, data):
        self.send_json(200, {"status": "ok", "echo": data, "timestamp": time.time()})

    def send_json(self, status, data):
        self.send_response(status)
        self.send_header('Content-Type', 'application/json')
        self.send_header('Access-Control-Allow-Origin', '*')
        self.end_headers()
        self.wfile.write(json.dumps(data).encode())

class ThreadedHTTPServer(ThreadingMixIn, HTTPServer):
    daemon_threads = True
    allow_reuse_address = True

if __name__ == "__main__":
    print(f"Python 后端启动 (端口: {PORT})")
    server = ThreadedHTTPServer(('127.0.0.1', PORT), SimpleHandler)
    server.serve_forever()
```

### 5.4 Node.js 后端开发

目录结构

```
your-plugin/
├── plugin.json
├── web/
│   ├── index.html
│   ├── style.css
│   └── script.js
└── scripts/
    └── backend/
        └── server.js           # Node.js 后端入口
```

plugin.json 配置

```json
{
    "pluginId": "com.example.nodebackend",
    "version": 1,
    "versionName": "1.0.0",
    "uiType": "web",
    "entry": "web/index.html",
    "backend": "node",
    "backendPort": 8000,
    "backendEntry": "scripts/backend/server.js",
    "backendAutoStart": true,
    "backendTimeout": 30
}
```

server.js 模板（使用 Node 内置 `http` 模块，无需安装依赖）

```javascript
const http = require('http');

const PORT = process.env.PORT || 8000;

const server = http.createServer((req, res) => {
    res.setHeader('Content-Type', 'application/json');
    res.setHeader('Access-Control-Allow-Origin', '*');

    if (req.method === 'GET' && (req.url === '/' || req.url === '/health')) {
        res.end(JSON.stringify({ status: 'healthy' }));
        return;
    }

    if (req.method === 'POST' && req.url === '/api/echo') {
        let body = '';
        req.on('data', chunk => body += chunk);
        req.on('end', () => {
            res.end(JSON.stringify({ status: 'ok', echo: JSON.parse(body || '{}') }));
        });
        return;
    }

    res.statusCode = 404;
    res.end(JSON.stringify({ error: 'Not Found' }));
});

server.listen(PORT, '127.0.0.1', () => {
    console.log(`Node.js 后端启动 (端口: ${PORT})`);
});
```

### 5.5 PHP 后端开发

目录结构

```
your-plugin/
├── plugin.json
├── web/
│   ├── index.html
│   ├── style.css
│   └── script.js
└── scripts/
    └── backend/
        └── index.php           # PHP 后端入口
```

plugin.json 配置

```json
{
    "pluginId": "com.example.phpbackend",
    "version": 1,
    "versionName": "1.0.0",
    "uiType": "web",
    "entry": "web/index.html",
    "backend": "php",
    "backendPort": 8000,
    "backendEntry": "scripts/backend/index.php",
    "backendPhpDocRoot": "scripts/backend",
    "backendAutoStart": true,
    "backendTimeout": 30
}
```

> PHP 后端由宿主以 `php -S 127.0.0.1:<port> -t <docRoot>` 启动内置 Web 服务器。`backendPhpDocRoot` 指定文档根目录，留空则使用插件目录。入口文件使用超全局变量读取请求：

```php
<?php
header('Content-Type: application/json; charset=utf-8');
header('Access-Control-Allow-Origin: *');

$path = parse_url($_SERVER['REQUEST_URI'], PHP_URL_PATH);
$method = $_SERVER['REQUEST_METHOD'];

if (($path === '/' || $path === '/health') && $method === 'GET') {
    echo json_encode(['status' => 'healthy']);
} elseif ($path === '/api/echo' && $method === 'POST') {
    $body = json_decode(file_get_contents('php://input'), true) ?? [];
    echo json_encode(['status' => 'ok', 'echo' => $body]);
} else {
    http_response_code(404);
    echo json_encode(['error' => 'Not Found']);
}
```

### 5.6 二进制后端开发

目录结构

```
your-plugin/
├── plugin.json
├── web/
│   ├── index.html
│   ├── style.css
│   └── script.js
└── scripts/
    └── backend/
        └── myapp               # 可执行文件（已编译）
```

plugin.json 配置

```json
{
    "pluginId": "com.example.binarybackend",
    "version": 1,
    "versionName": "1.0.0",
    "uiType": "web",
    "entry": "web/index.html",
    "backend": "binary",
    "backendPort": 8000,
    "backendEntry": "scripts/backend/myapp",
    "backendBinary": "scripts/backend/myapp",
    "backendArgs": "--port,8000",
    "backendAutoStart": true,
    "backendTimeout": 30
}
```

| 字段 | 说明 |
|------|------|
| `backendBinary` | 可执行文件路径；留空则使用 `backendEntry` |
| `backendArgs` | 附加命令行参数，逗号分隔（如 `--port,8000`） |
| `backendInstallCmd` | 首次使用时的安装命令（默认 `pkg install <语言>`，binary 可自定义） |
| `backendCheckCmd` | 环境检查命令（默认 `--version`） |

> 二进制程序需为 arm64-v8a 架构。宿主会以 `<backendBinary> <backendArgs...>` 方式启动。

### 5.7 前端与后端通信

```javascript
// 通过 JS 接口调用后端
function callBackend() {
    const callbackId = 'api_' + Date.now();
    window.UINPluginCallbacks = window.UINPluginCallbacks || {};
    window.UINPluginCallbacks[callbackId] = function(response) {
        const data = JSON.parse(response);
        console.log('后端响应:', data);
    };
    UINPlugin.callBackendApi('/api/compute', 'POST', JSON.stringify({
        expression: 'sum([1,2,3,4,5])'
    }), callbackId);
}

// 直接使用 fetch
function fetchBackend() {
    fetch('http://127.0.0.1:8000/api/echo', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ message: 'Hello' })
    })
    .then(res => res.json())
    .then(data => console.log(data));
}
```

### 5.8 后端 API 规范

宿主通过 HTTP 与后端通信，后端必须遵守以下约定：

**健康检查**

- 路径：`backendHealthCheck` 指定（默认 `/health`），**必须带前导 `/`**（宿主不会自动补全）
- 要求：返回 HTTP 200 即视为就绪
- 检测方式：宿主每 **200ms** 先做 TCP 端口探测（500ms 连接超时），端口打开后再发 **GET** 请求健康检查端点（部分后端如 Python `http.server` 未实现 `do_HEAD`，故用 GET 而非 HEAD）
- 超时：termux 运行时封顶 `min(backendTimeout, 30)` 秒；proot 运行时放宽至 `max(backendTimeout, 120)` 秒；other 模式 `max(backendTimeout, 90)` 秒
- 兜底：健康检查超时但后端进程仍存活时，宿主记 warning 并按「就绪」继续使用（不会失败）

**请求路径**

- `GET /`：欢迎页/状态
- `POST /api/<接口>`：业务接口，请求体为 JSON，响应为 JSON（仅为推荐约定）

**公共约定**

- 响应头建议带 `Access-Control-Allow-Origin: *`（前端 fetch 直连需要）
- 监听地址必须是 `127.0.0.1`（宿主只访问 loopback）
- 端口由 `backendPort` 指定；`backendPort` 为 `0` 时**普通后端会自动分配一个空闲端口**（8000 起）。前端通过 `UINPlugin.callBackendApi(path, method, body, callbackId)` 或 `fetch('http://127.0.0.1:<port>' + path)` 调用

**环境变量注入**

后端进程启动时宿主注入以下环境变量：

| 变量 | termux 运行时 | proot 运行时（容器内） |
|------|---------------|------------------------|
| `PATH` | `/data/data/com.UIN.Tool/files/usr/bin:/system/bin:...` | 同左 |
| `PORT` | 实际监听端口 | 同左 |
| `PLUGIN_ID` | 插件 ID | 同左 |
| `PLUGIN_DIR` | 插件目录绝对路径 | `/plugins/{pluginId}`（容器内路径） |
| `WORK_DIR` | `/storage/emulated/0/UIN_Tool` | `/plugins/{pluginId}` |
| `PYTHONUNBUFFERED` | `1` | `1` |
| `TERMUX_HOME` | `/data/data/com.UIN.Tool/files/home` | 同左 |
| `TERMUX_PREFIX` | `/data/data/com.UIN.Tool/files/usr` | 同左 |
| `PREFIX`/`HOME`/`TMPDIR` | — | 追加（容器解析必需） |
| `TERMUX__PREFIX` 等 | — | 追加（`proot-distro` 识别容器目录必需） |

**后端生命周期**

- 插件打开 → 宿主启动后端（`backendAutoStart: true` 才触发；`backendAutoStart: false` 时 Web 插件连启动前命令询问都不会出现）
- 就绪判定：TCP 端口轮询 + HTTP 健康检查（见上）
- 插件关闭 → 宿主按进程组 `SIGKILL` 停止后端（`backendKeepAlive: true` 时保持运行）
- ⚠️ `backendMaxRetries` 为**预留字段**：当前版本启动流程为单次尝试，**不会自动重试**（仅保留在序列化中）
- `backendLogLevel` 同样为预留字段：宿主日志级别按固定规则（termux=DEBUG、proot=INFO、stderr=ERROR），不读取该配置

### 5.9 Proot 容器运行时与自定义后端（v4.5.0 新增）

v4.5.0 新增两种后端运行方式：

· 运行环境（backendRuntime）：`"termux"`（默认，宿主环境）或 `"proot"`（共享 Alpine 容器）
· 后端类型（backend）：在原有类型之外新增 `"other"`（自定义，宿主不自动启动后端）

Proot 容器运行时（backendRuntime: "proot"）

插件后端可在共享 Alpine 容器中运行，与宿主机环境隔离。首次使用时自动初始化 Termux 环境，并从内置 assets（按 `alpine` 前缀查找，兼容 AndroidIDE 改写扩展名）离线恢复 Alpine 容器。

> alpine.tar.xz 为 `proot-distro backup alpine` 生成的备份，内置预装好的 Python 等依赖环境，恢复时通过 `proot-distro restore` 一键还原，无需联网安装依赖。

plugin.json 示例：

```json
{
    "pluginId": "com.example.prootbackend",
    "version": 1,
    "versionName": "1.0.0",
    "uiType": "web",
    "entry": "web/index.html",
    "backend": "python",
    "backendRuntime": "proot",
    "backendPort": 8000,
    "backendEntry": "scripts/backend/server.py",
    "backendAutoStart": true,
    "backendTimeout": 30,
    "backendHealthCheck": "/health"
}
```

容器说明：

· 容器固定名称为 alpine，位于 `$PREFIX/var/lib/proot-distro/containers/alpine/rootfs`
· 备份已预装 Python 环境，容器内可直接运行 Python 后端脚本
· 插件目录经 `--bind <插件目录>:/plugins/{pluginId}` 绑定到容器内；`backendEntry` 使用相对路径即可（容器内按 `/plugins/{pluginId}` 解析）
· 容器内 `127.0.0.1:PORT` 与宿主机互通，前端可直接调用后端 API
· 容器内额外的依赖可通过 `apk add` / `pip install` 在启动前命令中安装，不会污染宿主 Termux 环境
· 后端运行环境初始化流水线：Termux 就绪 → Alpine 就绪 → 启动前命令（可选）→ 启动后端
· 容器冷启动较慢，就绪超时放宽至至少 120 秒

启动前命令（backendPreCommand，仅 proot / other 运行时生效）

> ⚠️ **Web 插件 + termux 运行时（backendRuntime 留空）下，`backendPreCommand` 完全不执行**，宿主直接启动后端。

pre-command 仅对 proot / other 运行时生效，首次打开插件时询问是否执行（常用于安装额外依赖、初始化数据）：

· 弹窗三选项：「现在运行」「稍后」「取消」
· 「现在运行」在**后台**执行（`bash -lc`，`EXTRA_BACKGROUND=true`），**不会弹出终端窗口**；执行成功（exit 0）一次后写入 `pre_cmd_done` 标记（存于 `plugin_data_{pluginId}`），下次打开不再询问
· 执行完成后宿主自动启动后端；失败时会回到插件页提示退出码与错误信息
· 「稍后」：非 other 模式直接启动后端；other 模式提示「后端未启动（需执行启动前命令）」
· 「取消」：不执行命令也不启动后端
· 「稍后」「取消」均不会标记 `pre_cmd_done`，下次打开会再次询问

自定义后端模式（backend: "other"）

宿主不自动启动后端进程，由 `backendPreCommand` 后台启动常驻服务，适合「容器内长驻服务」「手动启动」等场景。**注意：other 模式即使 `backendRuntime` 非 proot，也会无条件初始化 Alpine 容器**。

```json
{
    "pluginId": "com.example.otherbackend",
    "uiType": "web",
    "entry": "web/index.html",
    "backend": "other",
    "backendPort": 8000,
    "backendAutoStart": true,
    "backendTimeout": 60,
    "backendPreCommand": "proot-distro login alpine --bind /storage/emulated/0/UIN_Tool/plugins/com.example.otherbackend:/plugins/com.example.otherbackend -- python3 /plugins/com.example.otherbackend/server.py"
}
```

· `backendPort > 0`：宿主通过 TCP 端口轮询（200ms）判定后端就绪，超时放宽至至少 90 秒
· `backendPort = 0`：视为**无端口插件**，宿主不检测端口，立即返回「运行中」（假定 pre-command 常驻服务已在运行）

---

## 六、CUI 终端插件开发（v4.5.0 新增）

### 6.1 CUI 插件是什么

**CUI 插件**（`uiType: "cui"`，Command-line User Interface）是前端以**全屏终端**形式呈现的插件：插件打开后，宿主不再渲染页面或 WebView，而是直接拉起一个真实的终端窗口（基于内置 Termux 引擎），在插件目录中执行你配置的启动命令。

- 适合：命令行工具、脚本自动化、交互式解释器、服务控制台等
- 插件页面只显示一个占位提示，真正的界面就是全屏终端
- 终端中可以使用宿主自带的 bash、python3 等命令
- 脚本可通过 `backendPreCommand` 中的 `export PLUGIN_ID=... PLUGIN_DIR=$(pwd)` 获取插件信息

### 6.2 目录结构与 plugin.json

CUI 插件结构非常简单：

```
plugin.tpk
├── plugin.json        # 必需
├── icon.png           # 可选
└── scripts/           # 脚本目录
    └── script.py      # 终端启动脚本（示例）
```

`plugin.json` 关键字段：

```json
{
    "pluginId": "com.example.cuitest",
    "version": 1,
    "versionName": "1.0.0",
    "minHostVersion": 1,
    "name": "CUI终端测试",
    "author": "UIN Tool",
    "description": "演示 CUI 模式",
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

| 字段 | 说明 |
|------|------|
| `uiType` | 固定为 `"cui"`，决定宿主走全屏终端流程 |
| `entry` | CUI 插件没有页面入口，留空 |
| `backendPreCommand` | **启动命令**：插件每次打开时，在插件目录中执行 `bash -lc "<此命令>"`。必须用 `export PLUGIN_ID=... PLUGIN_DIR=$(pwd)` 自行注入环境变量，因为终端会话不会自动注入 |
| `backend` | 可选。填 `""` 表示纯终端；也可配合后端字段同时启动一个 HTTP 后端 |
| `backendRuntime` | `"termux"`（默认）或 `"proot"`（在 Alpine 容器中执行） |

### 6.3 创建 CUI 插件（向导）

1. 「开发」→「创建插件」→ 选择「**CUI 终端（命令行界面）**」
2. 向导共 4 步：基本信息 → **编辑终端脚本** → 生成项目文件 → 完成
3. 在「编辑终端脚本」步骤中，可以点击「打开代码编辑器」修改 `scripts/script.py`
4. 基本信息步骤中提供「**启动命令**」输入框：
   - 标签：启动命令（插件打开时在终端中执行）
   - 默认值：`python3 scripts/script.py`
   - 留空时默认执行 `python3 scripts/script.py`
   - 建议加上 `export PLUGIN_ID=... PLUGIN_DIR=$(pwd)` 前缀以获取插件环境信息

向导会自动生成一个 `scripts/script.py` 示例脚本（含 `code.interact()` 交互式解释器）：

```python
#!/usr/bin/env python3
# -*- coding: utf-8 -*-
# CUI 插件终端示例脚本，插件打开后在此终端中运行。
import os

print("=" * 48)
print(" CUI 插件终端已启动")
print("=" * 48)
print("插件 ID : " + os.environ.get("PLUGIN_ID", "?"))
print("插件目录: " + os.getcwd())
print("-" * 48)
print("输入 exit 或 Ctrl-D 结束会话。")

import code
code.interact(banner="", local=locals())
```

### 6.4 终端脚本开发

脚本会以插件目录（`<PLUGIN_DIR>/<pluginId>`）为工作目录运行，可通过环境变量获取信息：

| 环境变量 | 说明 | 获取方式 |
|----------|------|----------|
| `PLUGIN_ID` | 插件 ID | 需在启动命令中 `export PLUGIN_ID=...` |
| `PLUGIN_DIR` | 插件目录 | 需在启动命令中 `export PLUGIN_DIR=$(pwd)` |
| `PWD` | 当前目录 | 终端默认就是插件目录 |

> 终端会话**不会**自动注入 `PLUGIN_ID` / `PLUGIN_DIR`，必须在 `backendPreCommand` 中手动 export（如内置 cuitest 插件所示）。

Python 示例（带交互式 REPL）：

```python
#!/usr/bin/env python3
import os
import code

pid = os.environ.get("PLUGIN_ID", "?")
pdir = os.environ.get("PLUGIN_DIR", os.getcwd())
print("插件 ID :", pid)
print("插件目录:", pdir)
print("输入 exit 或 Ctrl-D 结束。")
code.interact(banner="", local=locals())
```

Shell 示例（直接跑命令后退出）：

```bash
# backendPreCommand 示例：
# export PLUGIN_ID=com.example.tool PLUGIN_DIR=$(pwd); bash scripts/run.sh
echo "插件目录: $PWD"
ls -la
python3 scripts/tool.py
```

脚本结束后终端会话自动关闭（脚本不结束则终端保持打开）。

### 6.5 运行流程与生命周期

1. 用户打开 CUI 插件 → `PluginHostActivity` 显示占位视图：「正在打开终端并执行命令... 若未自动弹出，请在通知栏查看 UIN Tool 终端。」
2. 宿主确保 Termux 环境就绪（首次会自动初始化 bootstrap）
3. 若配置了 `backendRuntime: "proot"` 或 `backend: "other"`，先确保 Alpine 容器就绪（从内置 `alpine.tar.xz` 离线恢复）
4. 通过 `RunCommandService` 以 `bash -lc "<启动命令>"`（`backendPreCommand`）在插件目录启动**全屏终端会话**（TermuxActivity）；启动命令留空时以 `bash -l` 打开交互式登录 Shell
5. 若同时配置了 `backend` 且 `backendAutoStart: true`（非 other），宿主还会在后台额外启动 HTTP 后端
6. 终端会话独立于插件页存活：脚本/Shell 退出即关闭；插件页关闭**不会**强制杀死终端（会话归 TermuxService 管理）。但若曾启动 HTTP 后端且 `backendKeepAlive` 为 false，关闭插件页会停掉该后端
7. ⚠️ **Android 10+**：从后台拉起终端需要「显示在其他应用上层」悬浮窗权限；未授予时终端会话只出现在通知栏（不会自动弹出全屏终端）

### 6.6 CUI 与后端/Proot 的关系

- **纯 CUI**：`backend` 留空，只有终端，无 HTTP 后端
- **CUI + 后端**：`backend: "python"` 等 + `backendAutoStart: true`，宿主在拉起终端的同时后台启动后端，脚本可调用 `127.0.0.1:<port>`
- **CUI + Proot**：`backendRuntime: "proot"`，启动命令在 Alpine 容器内执行（需自行用 proot-distro 登录），适合需要隔离环境的场景
- **CUI + other**：`backend: "other"` 时宿主不自动启动后端，启动命令通常是常驻服务，由终端自行拉起

注意：CUI 的 `backendPreCommand` 是「每次打开都执行的启动命令」，与 Web 插件后端的一次性 pre-command（`pre_cmd_done` 标记）语义不同。

---

## 七、插件数据持久化存储

### 7.1 概述

v4.4.0 新增完整的插件数据持久化存储系统，每个插件拥有独立的存储空间，数据在插件更新时自动保留。

### 7.2 数据目录结构

插件安装目录位于 `/storage/emulated/0/UIN_Tool/plugins/{pluginId}/`：

```
/storage/emulated/0/UIN_Tool/plugins/
└── {pluginId}/
    ├── plugin.json          # 插件配置
    ├── plugin.dex           # 原生插件 DEX
    ├── web/                 # Web 插件文件
    │   ├── index.html
    │   ├── style.css
    │   └── script.js
    ├── data/                # ✅ 插件文件数据目录（沙箱根，自动创建）
    │   ├── config.json
    │   ├── settings.txt
    │   └── images/
    └── cache/               # ✅ 插件缓存目录（自动创建）
        └── temp_*.dat
```

> **KV 数据不在插件目录中**：KV 存储（`setStorage`/`putString` 等）存放在宿主应用私有 `SharedPreferences`，文件名 `plugin_data_{pluginId}`（应用卸载时随宿主清除）。`data/` 目录只存文件类数据，两者互相独立。
>
> 升级/重装插件时宿主会把 `data/` 先备份到临时目录再还原（保证用户数据保留）；卸载插件时 `deleteAllPluginData()` 连同 KV、`data/`、`cache/` 一并删除。

### 7.3 Web 插件存储 API

```javascript
// ============ KV 存储 ============
// 字符串
UINPlugin.setStorage('username', 'JohnDoe');
const name = UINPlugin.getStorage('username');

// 整数
UINPlugin.setStorageInt('score', 100);
const score = UINPlugin.getStorageInt('score', 0);

// 布尔值
UINPlugin.setStorageBool('isLoggedIn', true);
const loggedIn = UINPlugin.getStorageBool('isLoggedIn', false);

// 浮点数
UINPlugin.setStorageFloat('rating', 4.5);
const rating = UINPlugin.getStorageFloat('rating', 0);

// JSON
UINPlugin.setStorageJSON('config', JSON.stringify({theme: 'dark'}));
const config = JSON.parse(UINPlugin.getStorageJSON('config'));

// 删除
UINPlugin.removeStorage('temp');

// 清空
UINPlugin.clearStorage();

// 检查键存在
const exists = UINPlugin.containsStorageKey('username');

// 获取所有键
const keys = JSON.parse(UINPlugin.getStorageKeys());

// 获取所有数据
const allData = JSON.parse(UINPlugin.getAllStorage());

// ============ 批量操作 ============
// 批量写入
const batchData = {
    key1: 'value1',
    key2: 'value2',
    key3: 'value3'
};
UINPlugin.setStorageBatch(JSON.stringify(batchData));

// 批量读取
const batchKeys = JSON.stringify(['key1', 'key2', 'key3']);
const batchResult = JSON.parse(UINPlugin.getStorageBatch(batchKeys));

// ============ 文件操作 ============
// 写入文件
UINPlugin.writeFile('notes.txt', 'Hello World');

// 读取文件
const content = UINPlugin.readFile('notes.txt');

// 删除文件
UINPlugin.deleteFile('notes.txt');

// 检查文件存在
const fileExists = UINPlugin.fileExists('notes.txt');

// 列出文件
const files = JSON.parse(UINPlugin.listFiles());

// 获取文件大小
const size = UINPlugin.getFileSize('notes.txt');

// 清理缓存
UINPlugin.clearCache();

// ============ 数据统计 ============
const stats = JSON.parse(UINPlugin.getStorageStats());
console.log('KV数量:', stats.kvCount);
console.log('文件数量:', stats.fileCount);
console.log('总大小:', stats.totalFileSize);
console.log('缓存大小:', stats.cacheSize);
console.log('数据版本:', stats.dataVersion);

// ============ 数据导入导出 ============
// 导出数据
const exported = UINPlugin.exportData();

// 导入数据
UINPlugin.importData(exported);
```

### 7.4 原生插件存储 API

原生插件在 `onCreateView` 中收到的 `context` 即为 `PluginContext`，无需自行构造：

```kotlin
val pctx = context as PluginContext

// ============ KV 存储（SharedPreferences: plugin_data_{pluginId}）============
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

// ============ 文件存储（沙箱根 = 插件 data/）============
pctx.writeFile("data.txt", "content")
val content = pctx.readFile("data.txt")
pctx.deletePluginFile("data.txt")
val files = pctx.listPluginFiles()
val fileExists = pctx.fileExists("data.txt")
val size = pctx.getPluginFileSize("data.txt")
pctx.clearPluginCache()                       // 清缓存目录
pctx.deleteAllPluginData()                    // 清 KV + data/ + cache/

// ============ 数据统计 ============
val stats = pctx.getStorageStats()            // kvCount/fileCount/totalFileSize/cacheSize
```

> 注：`PluginContext` 提供了一批 `@Deprecated` 兼容别名（`getDataDir`→`getPluginDataDir`、`getCacheDir`→`getPluginCacheDir`、`listFiles`→`listPluginFiles`、`getFileSize`→`getPluginFileSize`、`clearCache`→`clearPluginCache`、`deleteFile`→`deletePluginFile`、`deleteAllData`→`deleteAllPluginData`），新代码请直接使用新名，避免混淆。

### 7.5 数据迁移

旧版 `web_plugin_{pluginId}` SharedPreferences 中的数据会在创建 `PluginContext` 时自动迁移到 `plugin_data_{pluginId}` 并清空旧表，无需开发者额外操作。

### 7.6 数据版本管理

```kotlin
// 获取数据版本
val version = pctx.getDataVersion()

// 设置数据版本
pctx.setDataVersion(2)

// 迁移标记
pctx.markDataMigrated()       // 标记已完成迁移
val migrated = pctx.isDataMigrated()
```

---

## 八、权限系统

### 8.1 权限状态管理

权限状态持久化存储，一次授权永久生效：

```kotlin
// 读取权限状态
val state = pctx.getPermissionState()

// 设置权限状态
pctx.setPermissionState(1)  // 1=已授权
pctx.setPermissionState(2)  // 2=已拒绝

// 检查是否需要弹窗
val shouldShow = pctx.shouldShowPermissionDialog()
```

### 8.2 权限状态值

| 状态值 | 含义 | 行为 |
|--------|------|------|
| 0 | 未授权 | 显示权限弹窗 |
| 1 | 已授权 | 直接进入插件 |
| 2 | 已拒绝 | 直接进入插件（状态0或2都会检查实际权限） |

### 8.3 权限请求的实际流程

> 说明：`shouldShowPermissionDialog()`/`setPermissionState()` 定义的「按状态自动弹窗」流程目前为**预留设计**（代码中尚无 UI 调用入口）。当前版本权限请求通过以下两条路径发生：

1. **权限管理界面**：插件安装后，可在「插件管理 → 权限」中按分组请求插件声明的权限。
2. **JS 运行时请求**：Web 插件调用 `UINPlugin.checkPermission()` / `UINPlugin.requestPermission()` / `UINPlugin.requestPermissions()`：
   - **普通权限**（相机、录音、定位等）：走系统运行时权限弹窗，结果经 callbackId 回传 `{"success","allGranted","results":{perm:bool}}`
   - **特殊权限**（悬浮窗、无障碍、安装未知应用等）：宿主弹对话框引导用户去**系统设置页**手动授权，授权结果需用户自行返回

权限状态（`permission_state`）会被持久化到 KV 中，但「状态=0 自动弹窗」「状态=2 自动退出」等联动逻辑尚未接线。

### 8.4 权限声明

**⚠️ 权限以逗号分隔的字符串声明**（宿主按 `,` 拆分；写成 JSON 数组会被解析为空列表）：

```json
{
    "permissions": "android.permission.CAMERA,android.permission.RECORD_AUDIO,android.permission.ACCESS_FINE_LOCATION"
}
```

### 8.5 权限类型

| 权限（简名） | 完整权限名 | 说明 | 类型 |
|------|------|------|------|
| READ_EXTERNAL_STORAGE | android.permission.READ_EXTERNAL_STORAGE | 读取存储 | 普通 |
| WRITE_EXTERNAL_STORAGE | android.permission.WRITE_EXTERNAL_STORAGE | 写入存储 | 普通 |
| INTERNET | android.permission.INTERNET | 访问网络 | 普通 |
| CAMERA | android.permission.CAMERA | 相机 | 普通 |
| RECORD_AUDIO | android.permission.RECORD_AUDIO | 录音 | 普通 |
| ACCESS_FINE_LOCATION | android.permission.ACCESS_FINE_LOCATION | 精确位置 | 普通 |
| MANAGE_EXTERNAL_STORAGE | android.permission.MANAGE_EXTERNAL_STORAGE | 管理所有文件 | 特殊 |
| SYSTEM_ALERT_WINDOW | android.permission.SYSTEM_ALERT_WINDOW | 悬浮窗 | 特殊 |
| WRITE_SETTINGS | android.permission.WRITE_SETTINGS | 修改系统设置 | 特殊 |
| REQUEST_INSTALL_PACKAGES | android.permission.REQUEST_INSTALL_PACKAGES | 安装未知应用 | 特殊 |
| PACKAGE_USAGE_STATS | android.permission.PACKAGE_USAGE_STATS | 应用使用统计 | 特殊 |
| ACCESSIBILITY | android.permission.ACCESSIBILITY | 无障碍服务 | 特殊 |
| POST_NOTIFICATIONS | android.permission.POST_NOTIFICATIONS | 发送通知（Android 13+） | 特殊 |

---

## 九、插件说明功能

### 9.1 概述

v4.2.0 新增功能：插件可在 plugin.json 中声明 notice 字段，首次打开时自动显示说明弹窗。

### 9.2 配置方法

```json
{
    "pluginId": "com.example.myplugin",
    "name": "我的插件",
    "notice": "欢迎使用我的插件！\n\n功能说明：\n1. 点击按钮执行操作\n2. 数据自动保存\n3. 支持导出导入"
}
```

### 9.3 用户交互

| 按钮 | 行为 |
|------|------|
| 知道了 | 关闭弹窗，当前会话不再显示 |
| 不再提示 | 永久关闭该插件的说明 |
| 稍后提醒 | 关闭弹窗，下次打开再次显示 |

---

## 十、PluginInterface 接口详解

### 10.1 方法说明

原生插件实现 `com.UIN.Tool.plugin.PluginInterface`。宿主通过 `DexClassLoader` 加载 `plugin.dex` 后 `loadClass(mainClass).newInstance() as PluginInterface` 实例化（要求 public 类 + public 无参构造器），实例缓存在 `WeakReference` 中，每次打开插件都会重新走一遍加载流程。

| 方法 | 说明 | 宿主实际调用 | 调用时机 |
|------|------|--------------|----------|
| onCreateView(context, container, savedInstanceState) | **必须实现**，创建插件 UI | ✅ | 插件打开时（每次打开都会重新执行）。`context` 实为 `PluginContext`，`savedInstanceState` **恒为 null**（宿主硬编码传入），返回的 View 会挂到宿主全屏容器 |
| onResume | 插件恢复 | ✅ | Activity onResume 时 |
| onPause | 插件暂停 | ✅ | Activity onPause 时（切走/锁屏） |
| onDestroy | 插件销毁 | ✅ | Activity onDestroy 时（宿主先按 `backendKeepAlive` 决定是否停后端，再销毁 WebView、清空实例与 ClassLoader 缓存） |
| onBackPressed | 返回键拦截 | ✅ | 用户按返回键；返回 `true` 表示消费，否则宿主先试 WebView `goBack()` 再退出 |
| onSaveInstanceState | 保存状态 | ❌ **不调用** | 预留。宿主 onSaveInstanceState 只保存 pluginId 与 WebView 状态，不转发给插件 |
| onActivityResult | Activity 结果 | ✅ | `startActivityForResult` 返回时 |
| onRequestPermissionsResult | 权限请求结果 | ✅ | 权限请求完成时 |
| getPluginTitle | 返回插件页标题 | ❌ **不调用** | 预留。标题需用 `setPluginTitle()`/JS 设置 |
| getPluginMenuItems | 返回菜单项列表 | ❌ **不调用** | 预留。`PluginMenuItem(id, title, icon, onClick)` 数据类已定义但宿主未接入 |
| onHostEvent | 接收宿主事件 | ✅ | 宿主事件通道。真实事件名为 `"plugin_call_<method>"`，由后端 `callPlugin` 触发并携带参数 Bundle |
| sendHostEvent | 发送事件给宿主 | ❌ **不调用** | 预留。无反向桥接 |
| getHostService | 获取宿主服务 | ❌ **不调用** | 预留，永远返回 null |
| isBackendRunning | 后端是否运行中 | ❌ **不注入** | 预留空实现 |
| getBackendPort | 获取后端端口 | ❌ **不注入** | 预留空实现 |
| callBackendApi | 调用后端 API | ❌ **不注入** | 预留空实现 |
| executeBackendTask | 执行后端任务 | ❌ **不注入** | 预留空实现 |

> ⚠️ **原生插件使用后端**：`isBackendRunning`/`getBackendPort`/`callBackendApi`/`executeBackendTask` 这四个方法宿主**不会注入实际实现**，调用永远是空操作。原生插件需要访问后端时，应通过 `PluginContext` 的 `baseContext` 强转宿主 Activity：

```kotlin
val host = (context.baseContext as? com.UIN.Tool.plugin.PluginHostActivity)
host?.isBackendReady()                 // Boolean：后端是否就绪
host?.getBackendPort()                 // Int：后端端口（未就绪为 0）
host?.callBackendApi("/api/hello", "GET", null) { success, data -> }
```

> 状态保持：`onCreateView` 的 `savedInstanceState` 恒为 null，旋转/重建后宿主仅按 pluginId 重新加载插件，插件自身状态需依赖 KV 持久化（`pctx.putString` 等）保存。

### 10.2 完整实现示例

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
        // 获取 PluginContext 用于数据存储
        pctx = context as? PluginContext

        val appContext = context.applicationContext
        val layout = LinearLayout(appContext).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 50, 50, 50)
        }

        // 读取保存的数据
        clickCount = pctx?.getInt("click_count", 0) ?: 0

        val counterText = TextView(appContext).apply {
            text = "点击次数: $clickCount"
            textSize = 16f
            setTextColor(0xFF666666.toInt())
            setPadding(0, 0, 0, 20)
        }

        val button = Button(appContext).apply {
            text = "点击我"
            setBackgroundColor(0xFF37474F.toInt())
            setTextColor(0xFFFFFFFF.toInt())
            setOnClickListener {
                clickCount++
                counterText.text = "点击次数: $clickCount"
                // 保存数据
                pctx?.putInt("click_count", clickCount)
                Toast.makeText(context, "点击了 $clickCount 次", Toast.LENGTH_SHORT).show()
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

---

## 十一、JavaScript API 完整参考（v4.5.0）

Web 插件通过宿主注入的全局对象 **`UINPlugin`** 调用原生能力，由 `PluginJSInterface` 提供（共 **170+** 个 `@JavascriptInterface` 方法）。所有方法返回值若为 `String (JSON)` 表示需在 JS 端 `JSON.parse`。

### 11.1 基础 API

**通用桥 `callHost(action, data)`**

```javascript
UINPlugin.callHost('toast', '短消息');          // Toast 提示
UINPlugin.callHost('toastLong', '长消息');       // 长 Toast
UINPlugin.callHost('finish', '');                // 关闭插件页面
UINPlugin.callHost('log', '信息');               // 普通日志
UINPlugin.callHost('logError', '错误');          // 错误日志
UINPlugin.callHost('logWarning', '警告');        // 警告日志
UINPlugin.callHost('alert', '提示内容');         // 提示弹窗
UINPlugin.callHost('confirm', '确认内容');       // 确认弹窗
UINPlugin.callHost('vibrate', '200');            // 振动（毫秒，需 VIBRATE 权限）
UINPlugin.callHost('copy', '要复制的文本');      // 复制到剪贴板
UINPlugin.callHost('openUrl', 'https://example.com'); // 打开链接
UINPlugin.callHost('share', '分享内容');         // 系统分享
UINPlugin.callHost('setTitle', '新标题');        // 设置插件页标题
UINPlugin.callHost('setFullscreen', 'true');     // 全屏（字符串 true/false）
UINPlugin.callHost('setKeepScreenOn', 'true');   // 屏幕常亮（字符串 true/false）
UINPlugin.callHost('sendNotification', '标题,消息'); // 发送通知
UINPlugin.callHost('takeScreenshot', '');        // 截图（需存储权限，存到下载目录 screenshots/）
```

**对话框 / 加载（v4.4.4 起弹窗统一走宿主 Compose 队列）**

```javascript
// 确认对话框（回调 JSON：{success, confirmed} 或 {ignored:true}）
UINPlugin.showConfirmDialog('标题', '内容', callbackId);

// 输入对话框（回调 JSON：{success, confirmed, value}）
UINPlugin.showPromptDialog('标题', '提示文字', callbackId);

// 加载提示（Toast 形式，非真实 Loading 层）
UINPlugin.showLoading('正在处理...');
UINPlugin.hideLoading();  // 空操作，预留
```

**回调机制**

异步方法（如 `httpGet`、`requestPermission`、`startSensor`、`showConfirmDialog`）需要传入一个 `callbackId`。JS 侧先注册回调，原生在主线程用 `evaluateJavascript` 回传 **JSON 字符串**：

```javascript
window.UINPluginCallbacks = window.UINPluginCallbacks || {};
const callbackId = 'cb_' + Date.now();
window.UINPluginCallbacks[callbackId] = function (resp) {
    const data = JSON.parse(resp);   // 原生回传的是 JSON 字符串
    console.log('回调:', data);
    delete window.UINPluginCallbacks[callbackId];
};
UINPlugin.httpGet('https://example.com', callbackId);
```

**宿主注入的 JS 钩子**

| 钩子 | 触发时机 | 说明 |
|------|----------|------|
| `window._onUINPluginReady()` | 页面加载完成、接口注入后 | 可安全调用 `UINPlugin.*` |
| `window._onBackendReady(port)` | 后端就绪 | 参数为实际监听端口 |
| `window._onBackendProgress(progress, message)` | 后端启动过程中 | 进度与提示信息 |

### 11.2 存储 API

存储为插件私有 KV（SharedPreferences `plugin_data_{pluginId}`）：

| API | 说明 | 返回值 |
|-----|------|--------|
| setStorage(key, value) | 存储字符串 | void |
| getStorage(key) | 读取字符串 | String |
| setStorageInt(key, value) | 存储整数 | void |
| getStorageInt(key, default) | 读取整数 | Int |
| setStorageLong(key, value) | 存储长整数 | void |
| getStorageLong(key, default) | 读取长整数 | Long |
| setStorageBool(key, value) | 存储布尔 | void |
| getStorageBool(key, default) | 读取布尔 | Boolean |
| setStorageFloat(key, value) | 存储浮点数 | void |
| getStorageFloat(key, default) | 读取浮点数 | Float |
| setStorageJSON(key, json) | 存储 JSON（内部校验后写入） | void |
| getStorageJSON(key) | 读取 JSON（无值返回 `"{}"`） | String |
| removeStorage(key) | 删除键 | void |
| clearStorage() | 清空所有 KV | void |
| containsStorageKey(key) | 检查键存在 | Boolean |
| getAllStorage() | 获取全部键值 | String (JSON) |
| getStorageKeys() | 获取全部键 | String (JSON 数组) |
| getAllKeys() | 同 getStorageKeys | String (JSON 数组) |
| getAllData() | 同 getAllStorage | String (JSON) |
| setStorageBatch(jsonData) | 批量写入（JSON 对象逐 key 存字符串） | Boolean |
| getStorageBatch(keys) | 批量读取（传入 JSON 数组，返回 {key:value}） | String (JSON) |

### 11.3 文件系统 API

文件沙箱根目录为插件 `data/`。安全：宿主拒绝 `..`、`/../`、以 `/` 开头的路径，并校验 canonicalPath 前缀防止目录穿越。

| API | 说明 | 返回值 |
|-----|------|--------|
| writeFile(fileName, content) | 写入文本文件 | Boolean |
| readFile(fileName) | 读取文本（不存在返回 null） | String |
| deleteFile(fileName) | 删除文件 | Boolean |
| fileExists(fileName) | 检查文件存在 | Boolean |
| listFiles() | 列出 data/ 根下文件名 | String (JSON 数组) |
| getFileList(directory) | 列出指定目录（空=data/ 根），逐项返回 name/path/size/isFile/isDirectory/lastModified | String (JSON 数组) |
| getFileSize(fileName) | 文件大小 | Long |
| getFileInfo(fileName) | 文件信息（name/path/size/isFile/isDirectory/exists/lastModified/canRead/canWrite/canExecute） | String (JSON) |
| isDirectory(fileName) | 是否为目录 | Boolean |
| createDir(dirName) | 创建目录 | Boolean |
| deleteDir(dirName) | 删除目录 | Boolean |
| renameFile(oldName, newName) | 重命名 | Boolean |
| copyFile(srcName, dstName) | 复制（覆盖式） | Boolean |
| moveFile(srcName, dstName) | 移动 | Boolean |
| exists(path) | 判断路径存在 | Boolean |
| clearCache() | 清空插件 cache/ 目录 | void |
| clearPluginCache() | 同 clearCache | void |

### 11.4 网络请求 API

```javascript
// HTTP 请求（均需 INTERNET 权限，回调 {"success":bool, "statusCode":..., "data":...}）
UINPlugin.httpGet(url, callbackId);
UINPlugin.httpPost(url, jsonBody, callbackId);   // application/json
UINPlugin.httpPut(url, jsonBody, callbackId);
UINPlugin.httpDelete(url, callbackId);

// 下载文件到插件 data/（回调 {"success":true,"file":...,"size":...}）
UINPlugin.downloadFile(url, fileName, callbackId);

// Ping（线程内 ping -c 1 -W 5，失败回退 isReachable；回调含 host/ip/time）
UINPlugin.ping(host, callbackId);

// DNS 解析（回调 {"success":bool,"host":"...","ips":[]}）
UINPlugin.resolveDns(host, callbackId);
UINPlugin.dns(host, callbackId);        // 同 resolveDns
const ips = JSON.parse(UINPlugin.dnsLookup(host)); // 同步版 DNS 解析

// 网络状态
const net = JSON.parse(UINPlugin.getNetworkInfo());  // connected/type/subtype/isWifi/isMobile
UINPlugin.isNetworkAvailable();
UINPlugin.isWifiConnected();
UINPlugin.isMobileConnected();
UINPlugin.getIpAddress();   // 非 loopback IPv4，失败 "0.0.0.0"
const wifi = JSON.parse(UINPlugin.getWifiInfo());    // ssid/bssid/rssi/linkSpeed/frequency/ip/networkId
UINPlugin.getSignalStrength();                       // WiFi RSSI，无连接 -100
const op = JSON.parse(UINPlugin.getOperatorInfo());  // 运营商/Sim 信息
```

### 11.5 设备信息 API

```javascript
// 插件与宿主信息
const plugin = JSON.parse(UINPlugin.getPluginInfo());   // plugin.json 全字段
const pluginVersion = UINPlugin.getPluginVersion();     // 插件 versionName
const pluginVersionCode = UINPlugin.getPluginVersionCode(); // 插件 version
const appVersion = UINPlugin.getAppVersion();           // 宿主 versionName
const appVersionCode = UINPlugin.getAppVersionCode();   // 宿主 versionCode
const hostVersion = UINPlugin.getHostVersion();         // = getAppVersion()

// 设备标识
const androidId = UINPlugin.getAndroidId();        // ANDROID_ID
const deviceId = UINPlugin.getDeviceId();          // 需 READ_PHONE_STATE，未授权返回 "permission_denied"
const serial = UINPlugin.getSerialNumber();        // Build.getSerial()
const mac = UINPlugin.getMacAddress();
const fingerprint = UINPlugin.getFingerprint();    // Build.FINGERPRINT
const hw = JSON.parse(UINPlugin.getHardwareInfo());    // hardware/board/bootloader/radio/cpu_abi/...
const bootTime = UINPlugin.getBootTime();
const uptime = UINPlugin.getUptime();

// 设备基本信息
const model = UINPlugin.getDeviceModel();
const version = UINPlugin.getAndroidVersion();
const api = UINPlugin.getApiLevel();
const density = UINPlugin.getScreenDensity();
const device = JSON.parse(UINPlugin.getDeviceInfo());  // android/api/device/manufacturer/brand/.../packageName
const screen = JSON.parse(UINPlugin.getScreenSize());  // width/height/widthDp/heightDp

// 内存
const totalMem = UINPlugin.getTotalMemory();
const freeMem = UINPlugin.getFreeMemory();
const mem = JSON.parse(UINPlugin.getMemoryUsage());    // total/used/free/percentage

// CPU 与构建信息
const cpuInfo = UINPlugin.getCpuInfo();     // cat /proc/cpuinfo 原文
const buildInfo = JSON.parse(UINPlugin.getBuildInfo());

// 时间 / 语言
UINPlugin.getCurrentTime();                 // "yyyy-MM-dd HH:mm:ss"
UINPlugin.getCurrentTimestamp();            // System.currentTimeMillis()
UINPlugin.getSystemTime();                  // 同 getCurrentTimestamp
UINPlugin.getTimezone();                    // TimeZone 时区 ID
UINPlugin.getTimezoneOffset();              // 时区偏移（小时）
UINPlugin.isDaylightSaving();               // 是否夏令时
UINPlugin.getSystemLanguage();              // Locale.language
UINPlugin.getSystemCountry();               // Locale.country
UINPlugin.getLocale();                      // Locale.toString()
UINPlugin.getDisplayLanguage();             // Locale.displayLanguage

// 设备存储（内部存储分区）
UINPlugin.getTotalStorage();
UINPlugin.getFreeStorage();
UINPlugin.getUsedStorage();
UINPlugin.getStoragePercentage();

// 应用管理
UINPlugin.isAppInstalled('com.example.app');
UINPlugin.getAppName('com.example.app');
UINPlugin.getAppVersion('com.example.app');  // 指定应用的 versionName
UINPlugin.openApp('com.example.app');        // 启动应用
const appInfo = JSON.parse(UINPlugin.getAppInfo());  // 宿主自身 packageName/versionName/.../sharedUserId
```

### 11.6 传感器 API

| API | 说明 | 返回值 |
|-----|------|--------|
| getAccelerometer() | 加速度计（传感器信息，非实时值） | String (JSON) |
| getGyroscope() | 陀螺仪 | String (JSON) |
| getLightSensor() | 光线传感器 | String (JSON) |
| getProximitySensor() | 距离传感器 | String (JSON) |
| getMagneticField() | 磁场传感器 | String (JSON) |
| getOrientation() | 方向传感器 | String (JSON) |
| getPressureSensor() | 气压传感器 | String (JSON) |
| getTemperatureSensor() | 温度传感器 | String (JSON) |
| getHumiditySensor() | 湿度传感器 | String (JSON) |
| getAvailableSensors() | 可用传感器布尔表（accelerometer/gyroscope/magnetic/light/proximity/pressure） | String (JSON) |

**连续实时回调**

```javascript
// 开始监听（type 支持 accelerometer/gyroscope/magnetic/light/proximity/pressure）
// 启动成功先回调 {"success":true,...}，随后 onSensorChanged 持续回调 x/y/z 或 lux/distance/pressure/values + timestamp/accuracy
UINPlugin.startSensor('accelerometer', callbackId);

// 停止监听
UINPlugin.stopSensor();
```

### 11.7 系统 API

```javascript
// 打开系统页面
UINPlugin.openSettings();
UINPlugin.openAppSettings();        // 本应用详情页
UINPlugin.openWifiSettings();
UINPlugin.openBluetoothSettings();
UINPlugin.openLocationSettings();
UINPlugin.openUrl('https://example.com');  // 打开任意链接
UINPlugin.share('分享内容');

// 状态查询
UINPlugin.isAirplaneModeOn();
UINPlugin.isBluetoothOn();
UINPlugin.isWifiOn();
UINPlugin.isMobileDataOn();
UINPlugin.isLocationOn();
UINPlugin.isNfcOn();
UINPlugin.isAutoRotateOn();
UINPlugin.isDndOn();
UINPlugin.isDarkMode();

// 屏幕
const brightness = UINPlugin.getScreenBrightness();  // 异常返回 -1
UINPlugin.getAutoBrightness();                       // 是否为自动亮度
const displayInfo = JSON.parse(UINPlugin.getDisplayInfo()); // width/height/density/xdpi/...
const fontScale = UINPlugin.getFontScale();

// 电池（唯一电池方法）
const battery = JSON.parse(UINPlugin.getBatteryInfo()); // level/isCharging/status

// 音频
const volume = UINPlugin.getVolume();
const maxVolume = UINPlugin.getMaxVolume();
const volumePct = UINPlugin.getVolumePercentage();
const hasHeadphones = UINPlugin.isHeadphonesConnected();

// 剪贴板
UINPlugin.setClipboard('text');
UINPlugin.copyToClipboard('text');  // 同 setClipboard
UINPlugin.getClipboard();
UINPlugin.paste();                  // 同 getClipboard
UINPlugin.clearClipboard();

// 通知（渠道 plugin_notification_channel；Android 13+ 需通知权限）
UINPlugin.sendNotification('标题', '消息');
UINPlugin.cancelNotification(id);

// 插件页控制
UINPlugin.setTitle('新标题');
UINPlugin.setFullscreen(true);
UINPlugin.setKeepScreenOn(true);
UINPlugin.takeScreenshot();         // 需存储权限，保存到下载目录 screenshots/
UINPlugin.getPluginDir();           // 插件根目录绝对路径
UINPlugin.getBackendStatus();       // "running:{port}" / "starting" / "unknown"
```

### 11.8 权限 API

```javascript
// 检查权限（普通 + 特殊权限）
UINPlugin.checkPermission('android.permission.CAMERA');

// 请求单个权限（回调 {"success":bool, ...}）
// 特殊权限（悬浮窗/无障碍/安装未知应用等）弹对话框引导去系统设置页
UINPlugin.requestPermission('android.permission.CAMERA', callbackId);

// 批量请求（JSON 数组字符串，回调 {"success","allGranted","results":{perm:bool}}）
UINPlugin.requestPermissions('["android.permission.CAMERA","android.permission.RECORD_AUDIO"]', callbackId);
```

### 11.9 后端通信 API

```javascript
// 获取后端状态
const status = UINPlugin.getBackendStatus();   // "running:{port}" / "starting" / "unknown"

// 调用后端 API（请求 http://127.0.0.1:{backendPort}{path}，GET/POST/PUT/DELETE，回调 {"success":bool,"data":...}）
UINPlugin.callBackendApi('/api/compute', 'POST', JSON.stringify({
    expression: 'sum([1,2,3,4,5])'
}), callbackId);

// 检查后端就绪
function isBackendReady() {
    const status = UINPlugin.getBackendStatus();
    return status && status.startsWith('running:');
}
```

### 11.10 数据统计 API

```javascript
// 获取存储统计
const stats = JSON.parse(UINPlugin.getStorageStats());
// stats.kvCount, stats.fileCount, stats.totalFileSize, stats.cacheSize, stats.dataVersion

// 获取插件数据大小
const size = UINPlugin.getPluginDataSize();

// 获取数据版本
const version = UINPlugin.getDataVersion();

// 清空全部插件数据（KV + data/ + cache/）
UINPlugin.clearAllPluginData();

// 导出数据（返回 JSON：{pluginId,pluginName,version,exportTime,data:{...}}）
const exported = UINPlugin.exportData();

// 导入数据
UINPlugin.importData(exported);
```

---

## 十二、打包与导入

### 12.1 打包方式

方式一：使用向导打包

1. 在「开发」页面点击「创建插件」
2. 按照向导完成配置
3. 在最后一步点击「完成」
4. 系统自动生成 TPK 包
5. 位置：/storage/emulated/0/UIN_Tool/tpk/

方式二：手动打包

1. 将插件文件整理到文件夹中
2. 确保有 plugin.json 和必要文件
3. 压缩为 ZIP 格式
4. 重命名为 .tpk 扩展名

### 12.2 文件结构

原生插件

```
plugin.tpk
├── plugin.json      # 必需
├── icon.png         # 可选
├── plugin.dex       # 必需（宿主要求；当前向导打包为占位文件，需自行放置真实 DEX）
├── src/             # 可选
└── res/             # 可选
```

> ⚠️ 当前应用内编译功能已禁用：向导生成的原生插件 TPK 中 `plugin.dex` 是一段占位文本（`// 原生插件编译功能暂时禁用`），并非真实 DEX，安装后无法加载。请使用 PC 端编译出真实的 `plugin.dex` 后替换。

Web 插件（无后端）

```
plugin.tpk
├── plugin.json      # 必需
├── icon.png         # 可选
└── web/             # 必需
    ├── index.html   # 必需
    ├── style.css    # 可选
    └── script.js    # 可选
```

Web 插件（带后端）

```
plugin.tpk
├── plugin.json      # 必需
├── icon.png         # 可选
└── web/             # 必需
    ├── index.html   # 必需
    ├── style.css    # 可选
    └── script.js    # 可选
```

> ⚠️ **当前打包器对 Web 插件只打包 `web/` 目录**（不存在时自动生成默认 `index.html`），**不会打包 `scripts/` 后端文件**。后端文件需在生成后手动放入插件安装目录（`/storage/emulated/0/UIN_Tool/plugins/{pluginId}/scripts/backend/`），否则后端入口缺失无法启动。
>
> ⚠️ **向导已知问题**：选择「WebView + 后端」创建时，向导会把 `plugin.json` 的 `entry` 覆盖为后端脚本路径（如 `scripts/backend/server.py`）。Web 插件的 `entry` 应指向 HTML 页面（`web/index.html`），请在生成后手动改回，否则 WebView 会尝试把脚本当页面加载。

CUI 插件（v4.5.0 新增）

```
plugin.tpk
├── plugin.json      # 必需（uiType: "cui"）
├── icon.png         # 可选
└── scripts/         # 脚本目录
    └── script.py    # 终端启动脚本
```

> 打包规则（`packageTpk`）：原生插件打包 `plugin.json` + `icon.png` + `plugin.dex`（占位）+ `src/` + `res/`；Web 插件打包 `plugin.json` + `icon.png` + `web/`（**不含 scripts/**）；CUI 插件打包 `plugin.json` + `icon.png` + `scripts/`。若项目目录存在 `README.md` 也会一并打包。

### 12.3 plugin.json 完整字段

`plugin.json` 是插件的心脏，位于 `.tpk` 包根目录。宿主通过 `PluginInfo.fromJson()` 解析该文件，因此**字段名必须与下表完全一致**（区分大小写）。

#### 12.3.1 完整示例

一个带 Python 后端 + Proot 容器的 Web 插件完整示例：

```json
{
    "pluginId": "com.example.myplugin",
    "version": 2,
    "versionName": "2.1.0",
    "minHostVersion": 1,
    "name": "我的插件",
    "author": "开发者",
    "description": "插件描述",
    "notice": "首次打开显示的说明",
    "icon": "icon.png",
    "mainClass": "com.example.MainPlugin",
    "updateUrl": "https://github.com/UIN-Tool-Plugins/myplugin",
    "apiLevel": 21,
    "category": "工具",
    "uiType": "web",
    "entry": "web/index.html",
    "permissions": "android.permission.INTERNET",
    "dependencies": "com.example.lib",
    "backend": "python",
    "backendRuntime": "proot",
    "backendPort": 8000,
    "backendEntry": "scripts/backend/server.py",
    "backendAutoStart": true,
    "backendKeepAlive": false,
    "backendPreCommand": "pip install requests",
    "backendTimeout": 30,
    "backendHealthCheck": "/health",
    "backendMaxRetries": 3,
    "backendLogLevel": "info",
    "backendArgs": "",
    "maxMemory": 512,
    "maxCpuTime": 60,
    "maxConcurrentTasks": 5
}
```

#### 12.3.2 基础信息字段

| 字段 | 类型 | 默认值 | 必填 | 详细说明 |
|------|------|--------|------|----------|
| `pluginId` | string | `""` | ✅ | **插件唯一标识符**，域名倒序格式（如 `com.example.myplugin`）。用于插件目录名、数据隔离、启动路由，安装后不可随意更改。 |
| `version` | int | `1` | ✅ | 数字版本号，用于版本比较（升级检测）。升级插件时需递增。 |
| `versionName` | string | `1.0.0` | ✅ | 显示给用户的版本名。 |
| `minHostVersion` | int | `1` | ✅ | 最低宿主版本号（与宿主 Build 版本比较），不满足则提示升级宿主。 |
| `name` | string | `""` | ✅ | 插件显示名称，出现在插件列表、终端标题、快捷方式等。 |
| `author` | string | `""` | ❌ | 插件作者。 |
| `description` | string | `""` | ❌ | 插件功能描述，显示在插件管理页。 |
| `notice` | string | `""` | ❌ | 插件说明。首次打开插件时自动弹窗显示（可选「不再提示」「稍后提醒」）。 |
| `icon` | string | `icon.png` | ❌ | 图标文件名，相对插件根目录（建议 128x128 PNG）。 |
| `mainClass` | string | `""` | 原生✅ | **原生插件入口类完整类名**（如 `com.example.MainPlugin`）。宿主用 `DexClassLoader` 加载后 `loadClass(mainClass).newInstance() as PluginInterface` 实例化。Web/CUI 插件留空。 |
| `updateUrl` | string | `""` | ❌ | 插件更新检测地址（GitHub Release 页），用于应用内检查更新。 |
| `apiLevel` | int | `21` | ❌ | 插件要求的宿主 API 等级（最小 21）。 |
| `category` | string | `未分类` | ❌ | 插件分类名，用于插件管理页分类展示。 |
| `signature` | string | `""` | ❌ | 插件签名（宿主安装时写入并校验，防篡改）。一般由宿主维护，勿手写。 |
| `uiType` | string | `native` | ✅ | 前端类型：`native`（原生 View）、`web`（WebView）、`cui`（全屏终端）。决定宿主的加载分支。 |
| `entry` | string | `web/index.html` | Web✅ | Web 插件入口页面（相对插件根目录，宿主用 `file://<pluginDir>/<entry>` 加载）。CUI 留空。⚠️ 向导在创建带后端 Web 插件时会把此字段误写为后端脚本路径，生成后需改回 `web/index.html`。 |
| `permissions` | string | `""` | ❌ | 所需权限列表，**逗号分隔字符串**（如 `android.permission.INTERNET,android.permission.VIBRATE`）。⚠️ 宿主按 `,` 拆分解析，写成 JSON 数组会被忽略。 |
| `dependencies` | string | `""` | ❌ | 依赖插件 ID 列表，**逗号分隔字符串**（宿主按 `,` 拆分；JSON 数组形式会被忽略）。宿主启动前检查依赖是否存在。 |
| `frontendConfig` | object | `{}` | ❌ | **预留字段**。模型已声明（`Map<String, Any>`），但当前版本不参与 JSON 读写、无任何消费者，勿在 plugin.json 中填写。 |

#### 12.3.3 后端配置字段

| 字段 | 类型 | 默认值 | 必填 | 详细说明 |
|------|------|--------|------|----------|
| `backend` | string | `""` | ❌ | 后端类型：`python`、`node`、`php`、`binary`（二进制）、`deno`、`go`、`ruby`、`perl`、`lua`、`java`、`rust`、`other`（自定义，宿主不自动启动）。留空表示无后端。 |
| `backendRuntime` | string | `""` | ❌ | 后端运行环境：`termux`（默认，宿主 Termux 环境）或 `proot`（共享 Alpine 容器，隔离环境）。v4.5.0 新增。 |
| `backendPort` | int | `0` | ❌ | 后端监听端口。`0` 表示无端口（配合 `other` 或 CUI 使用）。填端口时宿主通过 TCP 轮询 + 健康检查判定就绪。 |
| `backendEntry` | string | `scripts/backend/server.py` | ❌ | 后端入口文件路径（相对插件根目录）。PHP 以外语言用解释器 + 入口启动。 |
| `backendAutoStart` | boolean | `true` | ❌ | 打开插件时是否自动启动后端。`false` 时仅当 `backendPreCommand` 手动启动。 |
| `backendKeepAlive` | boolean | `false` | ❌ | 插件关闭后是否保持后端运行。`true` 时宿主不在 onDestroy 时停止后端。 |
| `backendPreCommand` | string | `""` | ❌ | 启动前命令。**两种语义**：① Web 后端：首次打开弹窗询问执行，成功（exit 0）一次后跳过（`pre_cmd_done`）；② CUI 插件：作为每次打开的终端启动命令。 |
| `backendTimeout` | int | `30` | ❌ | 后端就绪等待超时（秒）。容器冷启动场景宿主会自动放宽至 90s+。 |
| `backendHealthCheck` | string | `/health` | ❌ | 健康检查端点路径。宿主轮询该路径返回 200 即视为就绪。 |
| `backendMaxRetries` | int | `3` | ❌ | 后端启动失败后的最大重试次数。 |
| `backendLogLevel` | string | `info` | ❌ | 后端日志级别（`debug`/`info`/`warn`/`error`），影响宿主日志记录。 |
| `backendArgs` | string | `""` | ❌ | 附加命令行参数，**逗号分隔字符串**（如 `--port,8000`）。⚠️ 宿主按 `,` 拆分解析，JSON 数组形式会被忽略。 |
| `backendEnv` | object | `{}` | ❌ | **注意：当前不读取该 JSON 字段。** 模型已声明并在运行时使用（termux 环境注入 env；proot 下经 `--env k=v` 透传给容器），但 `fromJson()`/`toJson()` 尚未读写它，因此写在 plugin.json 中不会生效，仅能由宿主程序内部设置。 |

#### 12.3.4 后端语言专用字段

| 字段 | 类型 | 默认值 | 适用后端 | 详细说明 |
|------|------|--------|----------|----------|
| `backendBinary` | string | `""` | binary | 可执行文件路径（相对插件根目录）。留空则用 `backendEntry`。 |
| `backendInstallCmd` | string | `""` | 全部 | 自定义首次安装命令（如 `pkg install xx`）。留空按语言默认（python→`pkg install python`，node→`pkg install nodejs` 等）。 |
| `backendCheckCmd` | string | `""` | 全部 | 自定义环境检查命令。留空按语言默认（`python --version` 等）。 |
| `backendPhpDocRoot` | string | `""` | php | PHP 内置服务器文档根目录（`php -S 127.0.0.1:<port> -t <docRoot>`）。留空用插件目录。 |
| `backendJavaClass` | string | `""` | java | Java 后端主类名（`java -cp <pluginDir> <class>`）。 |
| `backendJavaJar` | string | `""` | java | Java 可执行 JAR 路径（`java -jar <jar>`，优先于 class）。 |

#### 12.3.5 资源限制字段

| 字段 | 类型 | 默认值 | 详细说明 |
|------|------|--------|----------|
| `maxMemory` | int | `512` | 后端内存上限（MB）。 |
| `maxCpuTime` | int | `60` | 后端 CPU 时间上限（秒）。 |
| `maxConcurrentTasks` | int | `5` | 并发任务数上限。 |

#### 12.3.6 各类型最小配置

**原生插件**

```json
{
    "pluginId": "com.example.native",
    "name": "原生示例",
    "version": 1,
    "versionName": "1.0.0",
    "uiType": "native",
    "mainClass": "com.example.MainPlugin"
}
```

**Web 插件（无后端）**

```json
{
    "pluginId": "com.example.web",
    "name": "Web示例",
    "version": 1,
    "versionName": "1.0.0",
    "uiType": "web",
    "entry": "web/index.html"
}
```

**Web 插件（带 Python 后端）**

```json
{
    "pluginId": "com.example.webapi",
    "name": "Web后端示例",
    "version": 1,
    "versionName": "1.0.0",
    "uiType": "web",
    "entry": "web/index.html",
    "backend": "python",
    "backendPort": 8000,
    "backendEntry": "scripts/backend/server.py",
    "backendAutoStart": true
}
```

**CUI 插件**

```json
{
    "pluginId": "com.example.cui",
    "name": "CUI示例",
    "version": 1,
    "versionName": "1.0.0",
    "uiType": "cui",
    "entry": "",
    "backendPreCommand": "export PLUGIN_ID=com.example.cui PLUGIN_DIR=$(pwd); python3 scripts/script.py"
}
```

### 12.4 导出内置模板（v4.5.0）

在「开发」页面点击「**导出模板**」，应用会将内置的打包插件从 `assets/test_plugins/` 复制到 `/storage/emulated/0/UIN_Tool/templates/`，并自动生成 `README.txt` 说明。这些模板可直接导入体验，覆盖 CUI 终端、自定义后端、Termux 后端、全接口测试、存储测试、原生插件、Web 纯前端等类型。

| 模板文件 | 说明 |
|----------|------|
| `com.example.cuitest.tpk` | CUI 终端插件示例（全屏终端执行脚本） |
| `com.example.othertest.tpk` | 自定义后端插件示例（other 模式，pre-command 手动启动） |
| `com.example.termuxtest.tpk` | Termux 后端插件示例（Python 后端） |
| `com.test.allapi.tpk` | 全接口测试插件 |
| `com.test.storage.tpk` | 存储测试插件 |
| `NativeTestPlugin.tpk` | 原生插件示例 |
| `web_plugin_template.tpk` | Web 插件模板（纯前端） |

---

## 十三、发布到插件仓库

### 13.1 仓库要求

| 要求 | 说明 |
|------|------|
| 仓库名称 | 必须为插件 ID（如 `com.example.myplugin`） |
| 仓库描述 | 必须为插件名称 |
| Release Tag | 格式：`{版本代码}-{版本名称}`（宿主按第一个 `-` 拆分，如 `1-1.0.0`） |
| Release 资产 | 必须包含 `.tpk` 文件（宿主取第一个 .tpk 资产，无完整性校验） |
| 仓库可见性 | 必须是公开仓库 |

> 说明：宿主在拉取插件列表时按 `repos?per_page=100` 分页读取（单页上限 100），并过滤名称以 `.github` 开头或为 `Docs`/`docs` 的仓库。

### 13.2 发布步骤

1. 创建 GitHub 仓库（在 UIN-Tool-Plugins 组织中），仓库名 = 插件 ID
2. 上传插件文件
3. 创建 Release（Tag: 1-1.0.0）
4. 上传 .tpk 文件到 Assets
5. 强制更新：Tag 格式 `{版本代码}-{版本名称}-1`（如 `2-1.0.1-1`；注意宿主按第一个 `-` 拆分，此时版本名会解析为 `1.0.1-1`）

---

## 十四、终端功能

### 14.1 概述

UIN Tool 内置完整的终端环境，核心引擎基于 Termux 改编。

### 14.2 终端特性

| 特性 | 说明 |
|------|------|
| Shell | 默认 bash；zsh、fish 等需 `pkg install` 自行安装 |
| 包管理器 | `pkg`/`apt`（Termux 自有软件源 termux-packages，非 Debian/Ubuntu 源） |
| 开发工具 | gcc、clang、make、git |
| 脚本语言 | Python、Node.js、Ruby 等（`pkg install` 安装） |
| 网络工具 | curl、wget、openssh |
| 多会话 | 支持多个终端会话同时运行 |
| 多窗口 | Android 7.0+ 多窗口支持（新建窗口按钮） |
| 安全模式 | 新建会话可开启安全模式 |

终端环境变量：`HOME=/data/data/com.UIN.Tool/files/home`、`PREFIX=/data/data/com.UIN.Tool/files/usr`、`TMPDIR=$PREFIX/tmp`、`PATH=$PREFIX/bin` 等。会话由 `TermuxService`（前台服务 + 通知）管理，`TermuxActivity` 销毁/重建不会中断会话。

### 14.3 常用命令

```bash
# 更新软件源（Termux 自有仓库）
pkg update

# 安装 Python
pkg install python

# 安装 Node.js
pkg install nodejs

# 安装 git
pkg install git

# SSH 连接服务器
ssh user@hostname
```

---

## 十五、UI 个性化开发

### 15.1 颜色系统

```kotlin
val uiConfig = UIConfig.getInstance()

// 获取颜色
val primaryColor = uiConfig.getPrimaryColor()
val textPrimaryColor = uiConfig.getTextPrimaryColor()

// 更新颜色
uiConfig.updateColor("primary", "#FF1A3A4A")

// 保存配置
uiConfig.saveConfig()
```

### 15.2 颜色配置项

| 分类 | 颜色项 |
|------|--------|
| 主色调 | primary, primary_dark, primary_light, accent |
| 辅助色 | success, warning, error, info |
| 文本色 | text_primary, text_secondary, text_hint, text_primary_inverse |
| 背景色 | background, surface, surface_variant |
| 边框色 | divider, glass_background, disabled |

### 15.3 形状配置

```kotlin
// 获取圆角
val cornerRadius = uiConfig.getCardCornerRadius()
val buttonRadius = uiConfig.getButtonCornerRadius()

// 更新圆角
uiConfig.updateShape("cardCornerRadius", 16)
uiConfig.updateShape("buttonCornerRadius", 12)
```

---

## 十六、调试技巧

### 16.1 日志输出

原生插件：

```kotlin
import com.UIN.Tool.log.Logger

Logger.i("TAG", "信息")
Logger.e("TAG", "错误", exception)
```

Web 插件：

```javascript
UINPlugin.callHost('log', '调试信息');
console.log('控制台输出');
```

后端 Python：

```python
print("调试信息")
```

### 16.2 查看运行日志

· 在「管理」→「运行日志」中查看
· 崩溃日志自动保存
· 日志位置：/storage/emulated/0/UIN_Tool/logs/

### 16.3 WebView 远程调试

1. 在 Chrome 浏览器打开 chrome://inspect
2. 确保 WebView 调试已启用
3. 支持断点、控制台、网络监控

---

## 十七、常见问题

Q1: 插件导入失败？

可能原因：文件不是有效的 .tpk 格式、缺少 plugin.json、JSON 格式错误、签名验证失败。

Q2: 原生插件编译失败？

当前状态：原生插件编译功能暂时禁用。建议使用 Web 插件替代。

Q3: Web 插件修改后不生效？

Web 插件修改 HTML/CSS/JS 后，关闭并重新打开插件即可，无需重新编译。

Q4: 插件无法调用宿主权限？

在「管理」→「权限管理」→「插件权限」中为插件授予所需权限。

Q5: 如何调试插件？

使用 Logger 输出日志，在「管理」→「运行日志」中查看。Web 插件可用 Chrome DevTools 调试。

Q6: 插件数据存储在哪里？

数据存储在 /storage/emulated/0/UIN_Tool/plugins/{pluginId}/data/ 目录，KV 数据存储在 SharedPreferences 中。

Q7: 更新插件会丢失数据吗？

不会。更新插件时自动保留 data/ 目录，用户数据不丢失。

Q8: 权限状态会持久化吗？

会。一次授权后，权限状态永久保存，下次打开不再重复弹窗。

Q9: Web 插件支持哪些 API？

支持 170+ 个 API，涵盖存储、文件、网络、设备信息、传感器、系统操作、权限等。

Q10: 如何导出插件数据？

Web 插件可使用 UINPlugin.exportData() 导出所有数据为 JSON 格式。

Q11: 如何清除插件数据？

Web 插件可使用 UINPlugin.clearStorage() 和 UINPlugin.clearCache()。

Q12: 如何重置插件权限状态？

在 PluginContext 中调用 clearPermissionState() 方法，或在设备上清除应用数据。

Q13: CUI 插件脚本如何获取插件 ID 和目录？

终端会话不会自动注入环境变量，需要在启动命令中手动 export，例如：`export PLUGIN_ID=com.example.xxx PLUGIN_DIR=$(pwd); python3 scripts/script.py`。

Q14: 如何导出内置插件模板？

在「开发」页面点击「导出模板」，内置的 7 个打包插件会被复制到 `/storage/emulated/0/UIN_Tool/templates/`，并生成 README.txt。

Q15: 导出模板一直显示「导出中...」？

旧版本有该问题，v4.5.0 已修复：导出改为后台线程执行，完成后自动复位状态。

---

## 十八、最佳实践

### 18.1 命名规范

· 插件ID：域名倒序，如 com.example.myplugin
· 类名：PascalCase，如 MainPlugin
· 包名：与插件ID一致

### 18.2 性能优化

· 避免在 onCreateView 中执行耗时操作
· 使用协程处理异步任务
· Web 插件优化图片和 CSS 选择器
· 传感器使用后及时停止

### 18.3 数据存储最佳实践

· 使用 setStorageJSON 存储复杂数据结构
· 定期清理缓存数据
· 敏感数据不要明文存储
· 使用 exportData 和 importData 备份用户数据
· 插件版本升级时注意数据兼容性

### 18.4 安全性

· 不要存储敏感信息明文
· 验证输入数据
· 使用 HTTPS
· 验证文件路径防止目录遍历

### 18.5 版本管理

· 使用语义化版本号
· 发布时使用正确的 Release Tag 格式
· 强制更新使用 -1 后缀

---

## 十九、技术支持

### 19.1 联系方式

| 渠道 | 联系方式 |
|------|----------|
| 邮箱 | undefinedinvalidnull@outlook.com |
| GitHub | https://github.com/Undefined-Invalid-Null/UIN-Tool |
| 插件仓库 | https://github.com/UIN-Tool-Plugins |
| QQ 群 | 511875883 |

---

文档信息

| 项目 | 信息 |
|------|------|
| 文档版本 | 4.5.0 |
| 对应应用版本 | v4.5.0 (Build 15) |
| 最后更新 | 2026年8月4日 |

---

© 2026 UIN Team. All Rights Reserved.

