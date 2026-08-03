# UIN Tool 开发文档

## 版本信息

| 项目 | 信息 |
|------|------|
| 文档版本 | 4.5.0 |
| 对应应用版本 | v4.5.0 (Build 15) |
| 最后更新 | 2026年8月3日 |

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

### 六、插件数据持久化存储（v4.4.0 新增）
- [6.1 概述](#61-概述)
- [6.2 数据目录结构](#62-数据目录结构)
- [6.3 Web 插件存储 API](#63-web-插件存储-api)
- [6.4 原生插件存储 API](#64-原生插件存储-api)
- [6.5 数据迁移](#65-数据迁移)
- [6.6 数据统计](#66-数据统计)

### 七、权限系统（v4.4.0 完善）
- [7.1 权限状态管理](#71-权限状态管理)
- [7.2 权限状态值](#72-权限状态值)
- [7.3 权限弹窗行为](#73-权限弹窗行为)
- [7.4 权限声明](#74-权限声明)
- [7.5 权限类型](#75-权限类型)

### 八、插件说明功能
- [8.1 概述](#81-概述)
- [8.2 配置方法](#82-配置方法)
- [8.3 用户交互](#83-用户交互)

### 九、PluginInterface 接口详解
- [9.1 方法说明](#91-方法说明)
- [9.2 完整实现示例](#92-完整实现示例)

### 十、JavaScript API 完整参考（v4.4.0 扩展）
- [10.1 基础 API](#101-基础-api)
- [10.2 存储 API](#102-存储-api)
- [10.3 文件系统 API](#103-文件系统-api)
- [10.4 网络请求 API](#104-网络请求-api)
- [10.5 设备信息 API](#105-设备信息-api)
- [10.6 传感器 API](#106-传感器-api)
- [10.7 系统 API](#107-系统-api)
- [10.8 后端通信 API](#108-后端通信-api)
- [10.9 数据统计 API](#109-数据统计-api)

### 十一、打包与导入
- [11.1 打包方式](#111-打包方式)
- [11.2 文件结构](#112-文件结构)
- [11.3 plugin.json 完整字段](#113-pluginjson-完整字段)

### 十二、发布到插件仓库
- [12.1 仓库要求](#121-仓库要求)
- [12.2 发布步骤](#122-发布步骤)

### 十三、终端功能
- [13.1 概述](#131-概述)
- [13.2 终端特性](#132-终端特性)
- [13.3 常用命令](#133-常用命令)

### 十四、UI 个性化开发
- [14.1 颜色系统](#141-颜色系统)
- [14.2 颜色配置项](#142-颜色配置项)
- [14.3 形状配置](#143-形状配置)

### 十五、调试技巧
- [15.1 日志输出](#151-日志输出)
- [15.2 查看运行日志](#152-查看运行日志)
- [15.3 WebView 远程调试](#153-webview-远程调试)

### 十六、常见问题
- [16.1 Q1-Q20](#161-常见问题)

### 十七、最佳实践
- [17.1 命名规范](#171-命名规范)
- [17.2 性能优化](#172-性能优化)
- [17.3 数据存储最佳实践](#173-数据存储最佳实践)
- [17.4 安全性](#174-安全性)
- [17.5 版本管理](#175-版本管理)

### 十八、技术支持
- [18.1 联系方式](#181-联系方式)

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
5. 如选择「WebView + 后端」，选择后端语言：
   - Python（推荐，使用内置 http.server）
   - Node.js
   - PHP
   - 二进制文件
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
- 编译功能暂时不可用
- 建议使用 Web 插件或 PC 端编译

**Web 插件（推荐）**
- 无需编译：修改 HTML/CSS/JS 后直接生效
- 向导会自动生成空白模板文件

**带后端的 Web 插件**
- 后端文件（server.py / server.js / index.php）自动生成空白模板
- 无需额外编译

### 1.5 导入运行

1. 点击底部「**管理**」→「**插件管理**」
2. 点击「**导入**」选择生成的 TPK 文件
3. 等待导入完成
4. 在「**工具**」页面中点击插件运行

---

## 二、插件类型

### 2.1 对比表格

| 特性 | 原生插件 | 纯 Web 插件 | Web + 后端插件 |
|------|----------|-------------|----------------|
| 开发语言 | Kotlin/Java | HTML/CSS/JS | HTML/CSS/JS + 后端语言 |
| UI 开发方式 | 代码动态创建 | HTML 布局 | HTML 布局 |
| 开发效率 | 中等 | 高 | 高 |
| 运行性能 | 高 | 中等 | 中等 |
| 热更新 | 需重新编译 | 无需编译 | 无需编译 |
| 后端支持 | 无 | 无 | Python/Node.js/PHP/二进制 |
| 数据持久化 | ✅ PluginContext | ✅ UINPlugin API | ✅ UINPlugin API |
| 学习成本 | 需懂 Android | 懂前端即可 | 懂前端 + 后端 |
| 编译方式 | 需要编译 | 无需编译 | 无需编译 |

### 2.2 如何选择

| 场景 | 推荐类型 |
|------|----------|
| 需要访问 Android 系统 API | 原生插件 |
| 快速原型开发 | Web 插件 |
| 已有 Web 项目 | Web 插件 |
| 需要后端计算或数据处理 | Web + 后端插件 |
| 需要持久化存储数据 | Web 插件（使用 setStorage） |
| 需要调用 Linux 命令 | Web + Python/Node.js |

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

3.2 支持的 Android 控件

控件 说明 常用方法
TextView 文本显示 setText(), setTextSize(), setTextColor()
EditText 文本输入 getText(), setHint()
Button 按钮 setText(), setOnClickListener()
ImageView 图片显示 setImageResource(), setImageBitmap()
LinearLayout 线性布局 setOrientation(), setGravity()
RelativeLayout 相对布局 addRule()
FrameLayout 帧布局 层叠视图
ScrollView 滚动视图 包裹内容
ProgressBar 进度条 setProgress(), setVisibility()
CheckBox 复选框 setChecked(), isChecked()
Switch 开关 setChecked(), isChecked()

3.3 布局示例

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

3.4 访问插件资源

```kotlin
// 获取插件目录
val pluginDir = context.filesDir.parentFile
val pluginPath = pluginDir?.absolutePath ?: ""

// 读取配置文件
val configFile = File(pluginPath, "config.json")
if (configFile.exists()) {
    val content = configFile.readText()
}

// 读取图片
val iconFile = File(pluginPath, "icon.png")
if (iconFile.exists()) {
    val bitmap = BitmapFactory.decodeFile(iconFile.absolutePath)
    imageView.setImageBitmap(bitmap)
}
```

3.5 插件数据存储 API

```kotlin
// 获取 PluginContext
val pluginDir = File(Constants.PLUGIN_DIR, pluginId)
val pctx = PluginContext(context, pluginDir.absolutePath)

// ============ KV 存储 ============
// 存储字符串
pctx.putString("username", "JohnDoe")
val username = pctx.getString("username", "Guest")

// 存储整数
pctx.putInt("score", 100)
val score = pctx.getInt("score", 0)

// 存储布尔值
pctx.putBoolean("isLoggedIn", true)
val isLoggedIn = pctx.getBoolean("isLoggedIn", false)

// 存储 JSON
val json = JSONObject().apply {
    put("theme", "dark")
    put("fontSize", 14)
}
pctx.putJSON("config", json)
val config = pctx.getJSON("config")

// 删除键
pctx.remove("temp_data")

// 检查键是否存在
val exists = pctx.contains("username")

// ============ 文件存储 ============
// 写入文件
pctx.writeFile("notes.txt", "Hello World")

// 读取文件
val content = pctx.readFile("notes.txt")

// 删除文件
pctx.deletePluginFile("notes.txt")

// 列出文件
val files = pctx.listPluginFiles()

// 文件是否存在
val fileExists = pctx.fileExists("notes.txt")

// 获取文件大小
val size = pctx.getPluginFileSize("notes.txt")

// ============ 缓存管理 ============
pctx.clearPluginCache()

// ============ 数据统计 ============
val stats = pctx.getStorageStats()
println("KV数量: ${stats.kvCount}")
println("文件数量: ${stats.fileCount}")
println("总大小: ${stats.totalFileSize}")

// ============ 权限状态管理 ============
val state = pctx.getPermissionState()
pctx.setPermissionState(1)  // 1=已授权
```

---

四、Web 插件开发（无后端）

4.1 目录结构

```
your-plugin/
├── plugin.json          # 插件配置文件（必需）
├── icon.png             # 插件图标（建议 128x128）
└── web/                 # Web 资源目录（必需）
    ├── index.html       # 主页面（必需）
    ├── style.css        # 样式文件（可选）
    └── script.js        # JavaScript 文件（可选）
```

4.2 plugin.json 配置

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
    "permissions": ["INTERNET", "VIBRATE"]
}
```

4.3 HTML 模板示例

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

4.4 JavaScript 示例

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

五、Web 插件开发（带后端）

5.1 概述

v4.2.0 新增功能：Web 插件可启动 Termux 后端服务，提供计算、数据处理、系统命令执行等能力。

后端服务特点：

· 自动启动：用户打开插件时自动启动后端，完全无感知
· 进程管理：插件关闭时自动停止后端（可配置保持运行）
· HTTP 通信：使用 HTTP API，无需 WebSocket
· 多语言支持：Python、Node.js、PHP、二进制程序

5.2 支持的后端语言

语言 命令 入口文件 适用场景
Python python server.py 数据处理、AI、Web 服务
Node.js node server.js Web 服务、实时应用
PHP php index.php Web 服务
二进制 可执行文件 myapp 已有程序、系统工具

5.3 Python 后端开发

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

5.4 前端与后端通信

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

5.9 Proot 容器运行时与自定义后端（v4.5.0 新增）

v4.5.0 新增两种后端运行方式：

· 运行环境（backendRuntime）：`"termux"`（默认，宿主环境）或 `"proot"`（共享 Alpine 容器）
· 后端类型（backend）：在原有类型之外新增 `"other"`（自定义，宿主不自动启动后端）

Proot 容器运行时（backendRuntime: "proot"）

插件后端可在共享 Alpine 容器中运行，与宿主机环境隔离。首次使用时自动初始化 Termux 环境，并从内置的 `assets/alpine.tar.xz` 离线恢复 Alpine 容器。

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
· 插件目录自动绑定到容器内 `/plugins/{pluginId}`，`backendEntry` 使用相对路径即可
· 容器内 `127.0.0.1:PORT` 与宿主机互通，前端可直接调用后端 API
· 容器内额外的依赖可通过 `apk add` / `pip install` 在启动前命令中安装，不会污染宿主 Termux 环境
· 后端运行环境初始化流水线：Termux 就绪 → Alpine 就绪 → 启动前命令 → 启动后端

启动前命令（backendPreCommand）

插件可配置一条启动前命令，首次打开插件时在 Termux 终端中执行（常用于安装额外依赖、初始化数据）。

· 弹窗三选项：「现在运行」「稍后」「取消」
· 命令执行成功（exit 0）一次后永久跳过（pre_cmd_done 标记，存于 plugin_data_{pluginId}）
· 执行失败时自动回到插件页并提示退出码与错误信息
· 「稍后」「取消」均不会标记 pre_cmd_done，下次打开会再次询问

自定义后端模式（backend: "other"）

宿主不自动启动后端进程，由 `backendPreCommand` 在 Termux 终端中自行启动服务，适合「容器内长驻服务」「手动启动」等场景。

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

· backendPort > 0 时，宿主通过 TCP 端口轮询（200ms）判定后端就绪
· backendPort 为 0 时视为无端口插件，pre-command 会话存活即运行中
· 轮询超时自动放宽至 90s+，兼容容器冷启动场景

---

六、插件数据持久化存储

6.1 概述

v4.4.0 新增完整的插件数据持久化存储系统，每个插件拥有独立的存储空间，数据在插件更新时自动保留。

6.2 数据目录结构

```
/storage/emulated/0/UIN_Tool/plugins/
└── {pluginId}/
    ├── plugin.json          # 插件配置
    ├── plugin.dex           # 原生插件 DEX
    ├── web/                 # Web 插件文件
    │   ├── index.html
    │   ├── style.css
    │   └── script.js
    ├── data/                # ✅ 插件数据目录
    │   ├── config.json
    │   ├── settings.txt
    │   └── images/
    └── cache/               # ✅ 插件缓存目录
        └── temp_*.dat
```

6.3 Web 插件存储 API

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

6.4 原生插件存储 API

```kotlin
// 获取 PluginContext
val pluginDir = File(Constants.PLUGIN_DIR, pluginId)
val pctx = PluginContext(context, pluginDir.absolutePath)

// ============ KV 存储 ============
pctx.putString("key", "value")
val value = pctx.getString("key", "default")

pctx.putInt("count", 100)
val count = pctx.getInt("count", 0)

pctx.putBoolean("enabled", true)
val enabled = pctx.getBoolean("enabled", false)

pctx.putJSON("config", JSONObject().apply { put("theme", "dark") })
val config = pctx.getJSON("config")

pctx.remove("temp")
pctx.clearAll()

// ============ 文件存储 ============
pctx.writeFile("data.txt", "content")
val content = pctx.readFile("data.txt")
pctx.deletePluginFile("data.txt")
val files = pctx.listPluginFiles()

// ============ 数据统计 ============
val stats = pctx.getStorageStats()
```

6.5 数据迁移

旧版 web_plugin_ SharedPreferences 数据会自动迁移到新存储系统，无需开发者额外操作。

6.6 数据版本管理

```kotlin
// 获取数据版本
val version = pctx.getDataVersion()

// 设置数据版本
pctx.setDataVersion(2)
```

---

七、权限系统

7.1 权限状态管理

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

7.2 权限状态值

状态值 含义 行为
0 未授权 显示权限弹窗
1 已授权 直接进入插件
2 已拒绝 直接进入插件（状态0或2都会检查实际权限）

7.3 权限弹窗行为

用户操作 状态变化 结果
点击"授权"，全部授予 state = 1 Toast "✅ 所有权限已授予"，进入插件
点击"授权"，部分拒绝 state = 1 Toast "⚠️ 权限被拒绝: XXX"，进入插件
点击"取消" state = 2 Toast "已取消权限请求"，退出插件

7.4 权限声明

```json
{
    "permissions": [
        "android.permission.CAMERA",
        "android.permission.RECORD_AUDIO",
        "android.permission.ACCESS_FINE_LOCATION"
    ]
}
```

7.5 权限类型

权限 说明 类型
READ_EXTERNAL_STORAGE 读取存储 普通
WRITE_EXTERNAL_STORAGE 写入存储 普通
MANAGE_EXTERNAL_STORAGE 管理所有文件 特殊
INTERNET 访问网络 普通
CAMERA 相机 普通
RECORD_AUDIO 录音 普通
ACCESS_FINE_LOCATION 精确位置 普通
SYSTEM_ALERT_WINDOW 悬浮窗 特殊
WRITE_SETTINGS 修改系统设置 特殊
ACCESSIBILITY 无障碍权限 特殊

---

八、插件说明功能

8.1 概述

v4.2.0 新增功能：插件可在 plugin.json 中声明 notice 字段，首次打开时自动显示说明弹窗。

8.2 配置方法

```json
{
    "pluginId": "com.example.myplugin",
    "name": "我的插件",
    "notice": "欢迎使用我的插件！\n\n功能说明：\n1. 点击按钮执行操作\n2. 数据自动保存\n3. 支持导出导入"
}
```

8.3 用户交互

按钮 行为
知道了 关闭弹窗，当前会话不再显示
不再提示 永久关闭该插件的说明
稍后提醒 关闭弹窗，下次打开再次显示

---

九、PluginInterface 接口详解

9.1 方法说明

方法 说明 必须实现 调用时机
onCreateView 创建插件视图 ✅ 插件首次打开时
onResume 插件恢复 ❌ 从其他页面返回时
onPause 插件暂停 ❌ 切换到其他页面时
onDestroy 插件销毁 ❌ 插件被关闭时
onBackPressed 返回键按下 ❌ 用户按下返回键时
onSaveInstanceState 保存状态 ❌ 系统需要保存状态时
onActivityResult Activity 结果 ❌ startActivityForResult 返回时
onRequestPermissionsResult 权限请求结果 ❌ 权限请求完成时

9.2 完整实现示例

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

十、JavaScript API 完整参考（v4.4.0）

10.1 基础 API

```javascript
// Toast 提示
UINPlugin.callHost('toast', '消息');
UINPlugin.callHost('toastLong', '长消息');

// 关闭插件
UINPlugin.callHost('finish', '');

// 日志
UINPlugin.callHost('log', '信息');
UINPlugin.callHost('logError', '错误');
UINPlugin.callHost('logWarning', '警告');

// 弹窗
UINPlugin.callHost('alert', '提示内容');
UINPlugin.callHost('confirm', '确认内容');

// 确认对话框（带回调，v4.4.4 新增）
UINPlugin.showConfirmDialog('标题', '内容', callbackId);

// 输入对话框（带回调，v4.4.4 新增）
UINPlugin.showPromptDialog('标题', '提示文字', callbackId);

// 振动
UINPlugin.callHost('vibrate', '200');

// 剪贴板
UINPlugin.callHost('copy', '要复制的文本');

// 打开链接
UINPlugin.callHost('openUrl', 'https://example.com');

// 分享
UINPlugin.callHost('share', '分享内容');

// 标题
UINPlugin.setTitle('新标题');

// 全屏
UINPlugin.setFullscreen(true);
```

10.2 存储 API

API 说明 返回值
setStorage(key, value) 存储字符串 void
getStorage(key) 读取字符串 String
setStorageInt(key, value) 存储整数 void
getStorageInt(key, default) 读取整数 Int
setStorageBool(key, value) 存储布尔 void
getStorageBool(key, default) 读取布尔 Boolean
setStorageFloat(key, value) 存储浮点数 void
getStorageFloat(key, default) 读取浮点数 Float
setStorageJSON(key, json) 存储 JSON void
getStorageJSON(key) 读取 JSON String
removeStorage(key) 删除键 void
clearStorage() 清空所有 void
containsStorageKey(key) 检查键存在 Boolean
getAllStorage() 获取所有数据 String (JSON)
getStorageKeys() 获取所有键 String (JSON)
setStorageBatch(jsonData) 批量写入 Boolean
getStorageBatch(keys) 批量读取 String (JSON)

10.3 文件系统 API

API 说明 返回值
writeFile(fileName, content) 写入文件 Boolean
readFile(fileName) 读取文件 String
deleteFile(fileName) 删除文件 Boolean
fileExists(fileName) 检查文件存在 Boolean
listFiles() 列出文件 String (JSON)
getFileSize(fileName) 获取文件大小 Long
clearCache() 清理缓存 void
createDir(dirName) 创建目录 Boolean
deleteDir(dirName) 删除目录 Boolean
getFileInfo(fileName) 获取文件信息 String (JSON)

10.4 网络请求 API

```javascript
// GET 请求
UINPlugin.httpGet(url, callbackId);

// POST 请求
UINPlugin.httpPost(url, jsonBody, callbackId);

// PUT 请求
UINPlugin.httpPut(url, jsonBody, callbackId);

// DELETE 请求
UINPlugin.httpDelete(url, callbackId);

// 下载文件
UINPlugin.downloadFile(url, fileName, callbackId);

// Ping
UINPlugin.ping(host, callbackId);
```

10.5 设备信息 API

```javascript
// 设备基本信息
const model = UINPlugin.getDeviceModel();
const version = UINPlugin.getAndroidVersion();
const api = UINPlugin.getApiLevel();
const density = UINPlugin.getScreenDensity();

// 屏幕信息
const screen = JSON.parse(UINPlugin.getScreenSize());

// 内存信息
const totalMem = UINPlugin.getTotalMemory();
const freeMem = UINPlugin.getFreeMemory();

// CPU 信息
const cpuInfo = UINPlugin.getCpuInfo();

// 构建信息
const buildInfo = JSON.parse(UINPlugin.getBuildInfo());

// Android ID
const androidId = UINPlugin.getAndroidId();

// 系统语言
const lang = UINPlugin.getSystemLanguage();
const country = UINPlugin.getSystemCountry();

// 时区
const timezone = UINPlugin.getTimezone();
```

10.6 传感器 API

API 说明 返回值
getAccelerometer() 加速度计 String (JSON)
getGyroscope() 陀螺仪 String (JSON)
getLightSensor() 光线传感器 String (JSON)
getProximitySensor() 距离传感器 String (JSON)
getMagneticField() 磁场传感器 String (JSON)
getOrientation() 方向传感器 String (JSON)
getPressureSensor() 气压传感器 String (JSON)
getTemperatureSensor() 温度传感器 String (JSON)
getHumiditySensor() 湿度传感器 String (JSON)

10.7 系统 API

```javascript
// 系统设置
UINPlugin.openSettings();
UINPlugin.openAppSettings();
UINPlugin.openWifiSettings();
UINPlugin.openBluetoothSettings();
UINPlugin.openLocationSettings();

// 状态查询
const isAirplane = UINPlugin.isAirplaneModeOn();
const isBluetooth = UINPlugin.isBluetoothOn();
const isWifi = UINPlugin.isWifiOn();
const isLocation = UINPlugin.isLocationOn();
const isDnd = UINPlugin.isDndOn();
const isDark = UINPlugin.isDarkMode();

// 屏幕
const brightness = UINPlugin.getScreenBrightness();
const fontScale = UINPlugin.getFontScale();
const displayInfo = JSON.parse(UINPlugin.getDisplayInfo());

// 电池
const battery = JSON.parse(UINPlugin.getBatteryInfo());
const health = JSON.parse(UINPlugin.getBatteryHealth());
const voltage = UINPlugin.getBatteryVoltage();
const temperature = UINPlugin.getBatteryTemperature();

// 音频
const volume = UINPlugin.getVolume();
const maxVolume = UINPlugin.getMaxVolume();
const hasHeadphones = UINPlugin.isHeadphonesConnected();

// 剪贴板
UINPlugin.setClipboard('text');
const clipboard = UINPlugin.getClipboard();
UINPlugin.clearClipboard();

// 通知
UINPlugin.sendNotification('标题', '消息');
UINPlugin.cancelNotification(id);
```

10.8 后端通信 API

```javascript
// 获取后端状态
const status = UINPlugin.getBackendStatus();

// 调用后端 API
UINPlugin.callBackendApi('/api/compute', 'POST', JSON.stringify({
    expression: 'sum([1,2,3,4,5])'
}), callbackId);

// 检查后端就绪
function isBackendReady() {
    const status = UINPlugin.getBackendStatus();
    return status && status.startsWith('running:');
}
```

10.9 数据统计 API

```javascript
// 获取存储统计
const stats = JSON.parse(UINPlugin.getStorageStats());
// stats.kvCount, stats.fileCount, stats.totalFileSize, stats.cacheSize, stats.dataVersion

// 获取插件数据大小
const size = UINPlugin.getPluginDataSize();

// 获取数据版本
const version = UINPlugin.getDataVersion();

// 导出数据
const exported = UINPlugin.exportData();

// 导入数据
UINPlugin.importData(exported);
```

---

十一、打包与导入

11.1 打包方式

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

11.2 文件结构

原生插件

```
plugin.tpk
├── plugin.json      # 必需
├── icon.png         # 可选
├── plugin.dex       # 必需（当前需手动编译）
├── src/             # 可选
└── res/             # 可选
```

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
├── web/             # 必需
│   ├── index.html   # 必需
│   ├── style.css    # 可选
│   └── script.js    # 可选
└── scripts/         # 后端目录
    └── backend/
        └── server.py # 后端入口
```

11.3 plugin.json 完整字段

字段 类型 说明 必填
pluginId string 唯一标识符 ✅
version int 版本号 ✅
versionName string 显示版本名 ✅
minHostVersion int 最低宿主版本 ✅
name string 插件名称 ✅
author string 作者 ❌
description string 描述 ❌
notice string 首次打开显示的说明 ❌
icon string 图标文件名 ❌
mainClass string 主类名（原生插件） 原生✅
uiType string native/web ❌
entry string 入口文件（Web 插件） Web✅
permissions array 所需权限列表 ❌
dependencies array 依赖插件列表 ❌
backend string 后端类型 ❌
backendPort int 后端端口 ❌
backendEntry string 后端入口路径 ❌
backendAutoStart boolean 自动启动 ❌
backendTimeout int 启动超时（秒） ❌
backendHealthCheck string 健康检查路径 ❌
backendRuntime string 运行环境（termux/proot，v4.5.0） ❌
backendPreCommand string 启动前命令（v4.5.0） ❌

### 11.4 导出内置模板（v4.5.0）

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

十二、发布到插件仓库

12.1 仓库要求

要求 说明
仓库名称 必须为插件 ID
仓库描述 必须为插件名称
Release Tag 格式：{版本代码}-{版本名称}
Release 资产 必须包含 .tpk 文件
仓库可见性 必须是公开仓库

12.2 发布步骤

1. 创建 GitHub 仓库（在 UIN-Tool-Plugins 组织中）
2. 上传插件文件
3. 创建 Release（Tag: 1-1.0.0）
4. 上传 .tpk 文件到 Assets
5. 强制更新：Tag 格式 {版本代码}-{版本名称}-1

---

十三、终端功能

13.1 概述

UIN Tool 内置完整的终端环境，核心引擎基于 Termux 改编。

13.2 终端特性

特性 说明
Shell 支持 bash、zsh、fish 等
包管理器 APT (Debian/Ubuntu 软件源)
开发工具 gcc、clang、make、git
脚本语言 Python、Node.js、Ruby
网络工具 curl、wget、openssh
多会话 支持多个终端会话同时运行
多窗口 Android 7.0+ 多窗口支持

13.3 常用命令

```bash
# 更新软件源
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

十四、UI 个性化开发

14.1 颜色系统

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

14.2 颜色配置项

分类 颜色项
主色调 primary, primary_dark, primary_light, accent
辅助色 success, warning, error, info
文本色 text_primary, text_secondary, text_hint, text_primary_inverse
背景色 background, surface, surface_variant
边框色 divider, glass_background, disabled

14.3 形状配置

```kotlin
// 获取圆角
val cornerRadius = uiConfig.getCardCornerRadius()
val buttonRadius = uiConfig.getButtonCornerRadius()

// 更新圆角
uiConfig.updateShape("cardCornerRadius", 16)
uiConfig.updateShape("buttonCornerRadius", 12)
```

---

十五、调试技巧

15.1 日志输出

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

15.2 查看运行日志

· 在「管理」→「运行日志」中查看
· 崩溃日志自动保存
· 日志位置：/storage/emulated/0/UIN_Tool/logs/

15.3 WebView 远程调试

1. 在 Chrome 浏览器打开 chrome://inspect
2. 确保 WebView 调试已启用
3. 支持断点、控制台、网络监控

---

十六、常见问题

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

支持 140+ 个 API，涵盖存储、文件、网络、设备信息、传感器、系统操作等。

Q10: 如何导出插件数据？

Web 插件可使用 UINPlugin.exportData() 导出所有数据为 JSON 格式。

Q11: 如何清除插件数据？

Web 插件可使用 UINPlugin.clearStorage() 和 UINPlugin.clearCache()。

Q12: 如何重置插件权限状态？

在 PluginContext 中调用 clearPermissionState() 方法，或在设备上清除应用数据。

---

十七、最佳实践

17.1 命名规范

· 插件ID：域名倒序，如 com.example.myplugin
· 类名：PascalCase，如 MainPlugin
· 包名：与插件ID一致

17.2 性能优化

· 避免在 onCreateView 中执行耗时操作
· 使用协程处理异步任务
· Web 插件优化图片和 CSS 选择器
· 传感器使用后及时停止

17.3 数据存储最佳实践

· 使用 setStorageJSON 存储复杂数据结构
· 定期清理缓存数据
· 敏感数据不要明文存储
· 使用 exportData 和 importData 备份用户数据
· 插件版本升级时注意数据兼容性

17.4 安全性

· 不要存储敏感信息明文
· 验证输入数据
· 使用 HTTPS
· 验证文件路径防止目录遍历

17.5 版本管理

· 使用语义化版本号
· 发布时使用正确的 Release Tag 格式
· 强制更新使用 -1 后缀

---

十八、技术支持

渠道 联系方式
邮箱 undefinedinvalidnull@outlook.com
GitHub https://github.com/Undefined-Invalid-Null/UIN-Tool
插件仓库 https://github.com/UIN-Tool-Plugins
QQ 群 511875883

---

文档信息

项目 信息
文档版本 4.5.0
对应应用版本 v4.5.0 (Build 15)
最后更新 2026年8月3日

---

© 2026 UIN Team. All Rights Reserved.

