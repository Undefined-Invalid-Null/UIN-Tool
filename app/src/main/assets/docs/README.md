# UIN Tool 开发文档

## 版本信息

| 项目 | 信息 |
|------|------|
| 文档版本 | 4.2.0 |
| 对应应用版本 | v4.2.0 (Build 12) |
| 最后更新 | 2026年7月24日 |

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

### 六、插件说明功能
- [6.1 概述](#61-概述)
- [6.2 配置方法](#62-配置方法)
- [6.3 用户交互](#63-用户交互)

### 七、PluginInterface 接口详解
- [7.1 方法说明](#71-方法说明)
- [7.2 完整实现示例](#72-完整实现示例)

### 八、JavaScript API 完整参考
- [8.1 callHost - 调用宿主方法](#81-callhost---调用宿主方法)
- [8.2 httpGet / httpPost - 网络请求](#82-httpget--httppost---网络请求)
- [8.3 传感器 API](#83-传感器-api)
- [8.4 文件系统 API](#84-文件系统-api)
- [8.5 存储 API](#85-存储-api)
- [8.6 信息获取 API](#86-信息获取-api)
- [8.7 系统 API](#87-系统-api)
- [8.8 后端通信 API](#88-后端通信-api)

### 九、打包与导入
- [9.1 打包方式](#91-打包方式)
- [9.2 文件结构](#92-文件结构)
- [9.3 plugin.json 完整字段](#93-pluginjson-完整字段)

### 十、插件权限系统
- [10.1 权限声明](#101-权限声明)
- [10.2 权限类型](#102-权限类型)
- [10.3 权限请求流程](#103-权限请求流程)

### 十一、发布到插件仓库
- [11.1 仓库要求](#111-仓库要求)
- [11.2 发布步骤](#112-发布步骤)

### 十二、终端功能
- [12.1 概述](#121-概述)
- [12.2 终端特性](#122-终端特性)
- [12.3 常用命令](#123-常用命令)
- [12.4 在插件中调用终端命令](#124-在插件中调用终端命令)

### 十三、UI 个性化开发
- [13.1 颜色系统](#131-颜色系统)
- [13.2 颜色配置项](#132-颜色配置项)
- [13.3 形状配置](#133-形状配置)
- [13.4 主题系统](#134-主题系统)

### 十四、调试技巧
- [14.1 日志输出](#141-日志输出)
- [14.2 查看运行日志](#142-查看运行日志)
- [14.3 WebView 远程调试](#143-webview-远程调试)
- [14.4 后端调试](#144-后端调试)

### 十五、常见问题
- [15.1 Q1-Q15](#151-常见问题)

### 十六、最佳实践
- [16.1 命名规范](#161-命名规范)
- [16.2 性能优化](#162-性能优化)
- [16.3 内存管理](#163-内存管理)
- [16.4 安全性](#164-安全性)
- [16.5 版本管理](#165-版本管理)

### 十七、技术支持
- [17.1 联系方式](#171-联系方式)

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
| 版本号 | 数字版本，用于版本比较 | 1 | ✅ |
| 版本名 | 显示版本号 | 1.0.0 | ✅ |
| 主类名 | 入口类的完整路径（原生插件） | com.example.MainPlugin | ✅ |
| 入口文件 | Web 插件入口（Web 插件） | web/index.html | ✅ |
| 插件说明 | 首次打开时显示的说明 | 欢迎使用！ | ❌ |

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
| 学习成本 | 需懂 Android | 懂前端即可 | 懂前端 + 后端 |
| 编译方式 | 需要编译 | 无需编译 | 无需编译 |

### 2.2 如何选择

| 场景 | 推荐类型 |
|------|----------|
| 需要访问 Android 系统 API | 原生插件 |
| 快速原型开发 | Web 插件 |
| 已有 Web 项目 | Web 插件 |
| 需要后端计算或数据处理 | Web + 后端插件 |
| 需要调用 Linux 命令 | Web + Python/Node.js |
| 需要运行已有二进制程序 | Web + 二进制后端 |

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
    "permissions": ["INTERNET", "VIBRATE"]
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
        <button onclick="testHttpGet()">GET 请求</button>
    </div>
    <script src="script.js"></script>
</body>
</html>
```

### 4.4 JavaScript 示例

```javascript
// 基础功能
function showToast() {
    UINPlugin.callHost('toast', 'Hello from WebView!');
}

function closePlugin() {
    UINPlugin.callHost('finish', '');
}

// 网络请求
function testHttpGet() {
    const callbackId = 'get_' + Date.now();
    window.UINPluginCallbacks = window.UINPluginCallbacks || {};
    window.UINPluginCallbacks[callbackId] = function(response) {
        const data = JSON.parse(response);
        if (data.success) {
            console.log('GET 成功:', data.data);
            alert('请求成功！');
        } else {
            console.error('GET 失败:', data.error);
            alert('请求失败: ' + data.error);
        }
        delete window.UINPluginCallbacks[callbackId];
    };
    UINPlugin.httpGet('https://api.github.com/orgs/UIN-Tool-Plugins/repos', callbackId);
}

// 存储
function saveData() {
    UINPlugin.setStorage('key', 'value');
    UINPlugin.callHost('toast', '已保存');
}

function loadData() {
    const value = UINPlugin.getStorage('key');
    UINPlugin.callHost('toast', '读取: ' + value);
}

// 生命周期
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
- **自动启动**：用户打开插件时自动启动后端，完全无感知
- **进程管理**：插件关闭时自动停止后端（可配置保持运行）
- **HTTP 通信**：使用 HTTP API，无需 WebSocket
- **多语言支持**：Python、Node.js、PHP、二进制程序

### 5.2 支持的后端语言

| 语言 | 命令 | 入口文件 | 适用场景 |
|------|------|----------|----------|
| Python | python | server.py | 数据处理、AI、Web 服务 |
| Node.js | node | server.js | Web 服务、实时应用 |
| PHP | php | index.php | Web 服务 |
| 二进制 | 可执行文件 | myapp | 已有程序、系统工具 |

### 5.3 Python 后端开发

**目录结构**
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

**plugin.json 配置**
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

**server.py 模板（使用 Python 内置 http.server）**
```python
#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
UIN Tool Python 后端
使用 Python 内置 http.server，无需安装 Flask
"""

import sys
import os
import json
import time
import subprocess
from datetime import datetime
from http.server import HTTPServer, BaseHTTPRequestHandler
from socketserver import ThreadingMixIn

PORT = int(os.environ.get("PORT", 8000))
PLUGIN_DIR = os.environ.get("PLUGIN_DIR", ".")
WORK_DIR = os.environ.get("WORK_DIR", "/")

class SimpleHandler(BaseHTTPRequestHandler):
    """HTTP 请求处理器"""

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
        elif self.path == '/api/record':
            self.handle_record(data)
        elif self.path == '/api/query':
            self.handle_query(data)
        elif self.path == '/api/system':
            self.handle_system(data)
        elif self.path == '/api/echo':
            self.handle_echo(data)
        else:
            self.send_json(404, {"error": "Not Found"})

    def do_OPTIONS(self):
        self.send_response(200)
        self.send_header('Access-Control-Allow-Origin', '*')
        self.send_header('Access-Control-Allow-Methods', 'GET, POST, OPTIONS')
        self.send_header('Access-Control-Allow-Headers', 'Content-Type')
        self.end_headers()

    def handle_compute(self, data):
        """计算任务"""
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

    def handle_record(self, data):
        """记录数据"""
        try:
            record = {
                "id": int(time.time() * 1000),
                "timestamp": datetime.now().isoformat(),
                "type": data.get('type', 'default'),
                "data": data.get('data', {}),
                "extra": data.get('extra', '')
            }
            log_file = os.path.join(PLUGIN_DIR, "records.log")
            with open(log_file, "a") as f:
                f.write(json.dumps(record) + "\n")
            self.send_json(200, {"status": "ok", "record_id": record["id"]})
        except Exception as e:
            self.send_json(400, {"status": "error", "error": str(e)})

    def handle_query(self, data):
        """查询记录"""
        try:
            limit = data.get('limit', 20)
            records = []
            log_file = os.path.join(PLUGIN_DIR, "records.log")
            if os.path.exists(log_file):
                with open(log_file, "r") as f:
                    lines = f.readlines()
                    for line in lines[-limit:]:
                        try:
                            records.append(json.loads(line))
                        except:
                            pass
            self.send_json(200, {"status": "ok", "records": records})
        except Exception as e:
            self.send_json(400, {"status": "error", "error": str(e)})

    def handle_system(self, data):
        """执行系统命令"""
        try:
            command = data.get('command', '')
            if not command:
                self.send_json(400, {"status": "error", "error": "No command"})
                return
            # 命令白名单
            allowed = ['uptime', 'date', 'whoami', 'pwd', 'ls', 'echo', 'cat', 'head', 'tail']
            if command.split()[0] not in allowed:
                self.send_json(403, {"status": "error", "error": "Command not allowed"})
                return
            proc = subprocess.run(command, shell=True, capture_output=True, timeout=30, cwd=WORK_DIR)
            self.send_json(200, {
                "status": "ok",
                "stdout": proc.stdout.decode('utf-8', errors='ignore'),
                "stderr": proc.stderr.decode('utf-8', errors='ignore')
            })
        except subprocess.TimeoutExpired:
            self.send_json(408, {"status": "error", "error": "Timeout"})
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
    print(f"插件目录: {PLUGIN_DIR}")
    try:
        server = ThreadedHTTPServer(('127.0.0.1', PORT), SimpleHandler)
        server.serve_forever()
    except KeyboardInterrupt:
        print("服务停止")
        server.shutdown()
```

**前端调用示例**
```javascript
// 调用后端计算
function callCompute() {
    fetch('http://127.0.0.1:8000/api/compute', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ expression: 'sum([1,2,3,4,5])' })
    })
    .then(res => res.json())
    .then(data => {
        console.log('计算结果:', data);
    });
}

// 调用后端系统命令
function callSystem() {
    fetch('http://127.0.0.1:8000/api/system', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ command: 'uptime' })
    })
    .then(res => res.json())
    .then(data => {
        console.log('命令输出:', data.stdout);
    });
}
```

### 5.4 Node.js 后端开发

**plugin.json 配置**
```json
{
    "backend": "node",
    "backendPort": 8000,
    "backendEntry": "scripts/backend/server.js"
}
```

**server.js 模板**
```javascript
// scripts/backend/server.js
const http = require('http');
const PORT = process.env.PORT || 8000;

const server = http.createServer((req, res) => {
    res.setHeader('Access-Control-Allow-Origin', '*');
    res.setHeader('Content-Type', 'application/json');

    if (req.url === '/health') {
        res.end(JSON.stringify({ status: 'healthy' }));
        return;
    }

    if (req.method === 'POST' && req.url === '/api/echo') {
        let body = '';
        req.on('data', chunk => body += chunk);
        req.on('end', () => {
            try {
                const data = JSON.parse(body);
                res.end(JSON.stringify({ status: 'ok', echo: data }));
            } catch(e) {
                res.end(JSON.stringify({ status: 'error', error: e.message }));
            }
        });
        return;
    }

    res.end(JSON.stringify({ message: 'Node.js backend OK' }));
});

server.listen(PORT, '127.0.0.1', () => {
    console.log(`Node.js 后端启动 (端口: ${PORT})`);
});
```

### 5.5 PHP 后端开发

**plugin.json 配置**
```json
{
    "backend": "php",
    "backendPort": 8000,
    "backendEntry": "index.php",
    "backendPhpDocRoot": "scripts/backend"
}
```

**index.php 模板**
```php
<?php
// scripts/backend/index.php
$port = getenv('PORT') ?: 8000;

if ($_SERVER['REQUEST_URI'] === '/health') {
    header('Content-Type: application/json');
    echo json_encode(['status' => 'healthy']);
    exit;
}

if ($_SERVER['REQUEST_METHOD'] === 'POST' && $_SERVER['REQUEST_URI'] === '/api/echo') {
    $input = json_decode(file_get_contents('php://input'), true);
    header('Content-Type: application/json');
    echo json_encode(['status' => 'ok', 'echo' => $input]);
    exit;
}

header('Content-Type: application/json');
echo json_encode(['message' => 'PHP backend OK']);
```

### 5.6 二进制后端开发

**plugin.json 配置**
```json
{
    "backend": "binary",
    "backendPort": 8000,
    "backendEntry": "backend/myapp",
    "backendBinary": "myapp",
    "backendArgs": ["--port", "8000"]
}
```

**说明**：
- `backendEntry`：可执行文件在插件目录中的路径
- `backendBinary`：可执行文件名（用于 PATH 查找）
- `backendArgs`：启动参数列表

### 5.7 前端与后端通信

**使用 UINPlugin.callBackendApi()**
```javascript
// 通过 JS 接口调用后端
const callbackId = 'api_' + Date.now();
window.UINPluginCallbacks = window.UINPluginCallbacks || {};
window.UINPluginCallbacks[callbackId] = function(response) {
    const data = JSON.parse(response);
    console.log('后端响应:', data);
};
UINPlugin.callBackendApi('/api/compute', 'POST', JSON.stringify({
    expression: 'sum([1,2,3,4,5])'
}), callbackId);
```

**直接使用 fetch**
```javascript
// 直接 HTTP 请求（需要知道端口）
fetch('http://127.0.0.1:8000/api/compute', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ expression: 'sum([1,2,3,4,5])' })
})
.then(res => res.json())
.then(data => console.log(data));
```

### 5.8 后端 API 规范

| 端点 | 方法 | 说明 | 必需 |
|------|------|------|------|
| /health | GET | 健康检查 | ✅ |
| /api/compute | POST | 计算任务 | ❌ |
| /api/record | POST | 记录数据 | ❌ |
| /api/query | POST | 查询记录 | ❌ |
| /api/system | POST | 系统命令 | ❌ |
| /api/echo | POST | 回显测试 | ❌ |

**健康检查响应格式**
```json
{
    "status": "healthy",
    "timestamp": 1234567890.123
}
```

**API 错误响应格式**
```json
{
    "status": "error",
    "error": "错误描述"
}
```

---

## 六、插件说明功能

### 6.1 概述

v4.2.0 新增功能：插件可在 `plugin.json` 中声明 `notice` 字段，首次打开时自动显示说明弹窗。

### 6.2 配置方法

```json
{
    "pluginId": "com.example.myplugin",
    "name": "我的插件",
    "notice": "欢迎使用我的插件！\n\n功能说明：\n1. 点击按钮执行操作\n2. 数据自动保存\n3. 支持导出导入\n\n注意事项：\n- 需要存储权限\n- 首次使用请先配置"
}
```

### 6.3 用户交互

用户首次打开插件时，会显示弹窗：

| 按钮 | 行为 |
|------|------|
| 知道了 | 关闭弹窗，当前会话不再显示 |
| 不再提示 | 永久关闭该插件的说明 |
| 稍后提醒 | 关闭弹窗，下次打开再次显示 |

**在插件管理中查看说明**

在「管理」→「插件管理」→ 点击插件 → 详情对话框会显示 `notice` 内容。

---

## 七、PluginInterface 接口详解

### 7.1 方法说明

| 方法 | 说明 | 必须实现 | 调用时机 |
|------|------|----------|----------|
| onCreateView | 创建插件视图 | ✅ 是 | 插件首次打开时 |
| onResume | 插件恢复 | ❌ 否 | 从其他页面返回时 |
| onPause | 插件暂停 | ❌ 否 | 切换到其他页面时 |
| onDestroy | 插件销毁 | ❌ 否 | 插件被关闭时 |
| onBackPressed | 返回键按下 | ❌ 否 | 用户按下返回键时 |
| onSaveInstanceState | 保存状态 | ❌ 否 | 系统需要保存状态时 |
| onActivityResult | Activity 结果 | ❌ 否 | startActivityForResult 返回时 |
| onRequestPermissionsResult | 权限请求结果 | ❌ 否 | 权限请求完成时 |

### 7.2 完整实现示例

```kotlin
package com.example

import android.content.Context
import android.content.Intent
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
    private var counterText: TextView? = null
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

        counterText = TextView(appContext).apply {
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
                counterText?.text = "点击次数: $clickCount"
                Toast.makeText(context, "点击了 $clickCount 次", Toast.LENGTH_SHORT).show()
            }
        }

        layout.addView(title)
        layout.addView(counterText)
        layout.addView(button)

        rootView = layout
        return rootView
    }

    override fun onResume() {
        Toast.makeText(context, "插件恢复", Toast.LENGTH_SHORT).show()
    }

    override fun onPause() {
        Toast.makeText(context, "插件暂停", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        rootView = null
        Toast.makeText(context, "插件销毁", Toast.LENGTH_SHORT).show()
    }

    override fun onBackPressed(): Boolean {
        return if (clickCount > 0) {
            Toast.makeText(context, "再按一次退出", Toast.LENGTH_SHORT).show()
            clickCount = 0
            counterText?.text = "点击次数: 0"
            true
        } else {
            false
        }
    }

    override fun onSaveInstanceState(): Bundle? {
        return Bundle().apply {
            putInt("clickCount", clickCount)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        Toast.makeText(context, "Activity 返回: $requestCode", Toast.LENGTH_SHORT).show()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        // 处理权限请求结果
    }
}
```

---

## 八、JavaScript API 完整参考

### 8.1 callHost - 调用宿主方法

```javascript
// 显示 Toast 提示
UINPlugin.callHost('toast', '消息内容');

// 关闭插件
UINPlugin.callHost('finish', '');

// 输出日志
UINPlugin.callHost('log', '调试信息');

// 显示弹窗
UINPlugin.callHost('alert', '弹窗内容');

// 显示确认框
UINPlugin.callHost('confirm', '确认内容');

// 震动 (参数为毫秒数)
UINPlugin.callHost('vibrate', '100');

// 复制到剪贴板
UINPlugin.callHost('copy', '要复制的文本');

// 打开链接
UINPlugin.callHost('openUrl', 'https://www.example.com');

// 分享内容
UINPlugin.callHost('share', '要分享的文本');

// 设置标题
UINPlugin.setTitle('新标题');

// 全屏模式
UINPlugin.setFullscreen(true);
UINPlugin.setFullscreen(false);
```

### 8.2 httpGet / httpPost - 网络请求

```javascript
// GET 请求
const callbackId = 'get_' + Date.now();
window.UINPluginCallbacks = window.UINPluginCallbacks || {};
window.UINPluginCallbacks[callbackId] = function(response) {
    const data = JSON.parse(response);
    if (data.success) {
        console.log('请求成功:', data.data);
    } else {
        console.error('请求失败:', data.error);
    }
};
UINPlugin.httpGet('https://api.example.com/data', callbackId);

// POST 请求
const postData = JSON.stringify({key: 'value'});
UINPlugin.httpPost('https://api.example.com/submit', postData, callbackId);
```

### 8.3 传感器 API

```javascript
// 启动传感器
const callbackId = 'sensor_' + Date.now();
UINPlugin.startSensor('accelerometer', callbackId);

// 停止传感器
UINPlugin.stopSensor();

// 获取可用传感器
const sensors = JSON.parse(UINPlugin.getAvailableSensors());
```

支持的传感器类型：

| 类型 | 说明 | 回调数据 |
|------|------|----------|
| accelerometer | 加速度计 | x, y, z |
| gyroscope | 陀螺仪 | x, y, z |
| magneticField | 磁场计 | x, y, z |
| light | 光线传感器 | lux |
| proximity | 接近传感器 | distance |
| pressure | 压力传感器 | pressure |

### 8.4 文件系统 API

```javascript
// 写入文件
const success = UINPlugin.writeFile('test.txt', '文件内容');

// 读取文件
const content = UINPlugin.readFile('test.txt');

// 删除文件
const deleted = UINPlugin.deleteFile('test.txt');

// 列出文件
const files = UINPlugin.listFiles('');

// 获取插件目录
const pluginDir = UINPlugin.getPluginDir();
```

### 8.5 存储 API

```javascript
// 存储数据
UINPlugin.setStorage('key', 'value');

// 读取数据
const value = UINPlugin.getStorage('key');

// 删除数据
UINPlugin.removeStorage('key');

// 清空所有数据
UINPlugin.clearStorage();
```

### 8.6 信息获取 API

```javascript
// 获取插件信息
const info = JSON.parse(UINPlugin.getPluginInfo());

// 获取设备信息
const device = JSON.parse(UINPlugin.getDeviceInfo());

// 获取网络信息
const network = JSON.parse(UINPlugin.getNetworkInfo());

// 获取当前时间
const time = UINPlugin.getCurrentTime();

// 获取宿主版本
const version = UINPlugin.getAppVersion();

// 获取后端状态
const status = UINPlugin.getBackendStatus();
// 返回: "running:8000" 或 "stopped" 或 "starting"
```

### 8.7 系统 API

```javascript
// 打开系统设置
UINPlugin.openSettings();

// 打开应用设置
UINPlugin.openAppSettings();

// 权限检查
const granted = UINPlugin.checkPermission('android.permission.CAMERA');

// 请求权限
UINPlugin.requestPermission('android.permission.CAMERA', callbackId);
```

### 8.8 后端通信 API

```javascript
// 获取后端状态
const status = UINPlugin.getBackendStatus();

// 调用后端 API
UINPlugin.callBackendApi('/api/compute', 'POST', JSON.stringify({
    expression: 'sum([1,2,3,4,5])'
}), callbackId);

// 检查后端是否就绪
function isBackendReady() {
    const status = UINPlugin.getBackendStatus();
    return status && status.startsWith('running:');
}
```

---

## 九、打包与导入

### 9.1 打包方式

**方式一：使用向导打包**
1. 在「开发」页面点击「创建插件」
2. 按照向导完成配置
3. 在最后一步点击「完成」
4. 系统自动生成 TPK 包
5. 位置：`/storage/emulated/0/UIN_Tool/tpk/`

**方式二：手动打包**
1. 将插件文件整理到文件夹中
2. 确保有 plugin.json 和必要文件
3. 压缩为 ZIP 格式
4. 重命名为 `.tpk` 扩展名

### 9.2 文件结构

**原生插件**
```
plugin.tpk
├── plugin.json      # 必需
├── icon.png         # 可选
├── plugin.dex       # 必需（当前需手动编译）
├── src/             # 可选
└── res/             # 可选
```

**Web 插件（无后端）**
```
plugin.tpk
├── plugin.json      # 必需
├── icon.png         # 可选
└── web/             # 必需
    ├── index.html   # 必需
    ├── style.css    # 可选
    └── script.js    # 可选
```

**Web 插件（带后端）**
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

### 9.3 plugin.json 完整字段

| 字段 | 类型 | 说明 | 必填 |
|------|------|------|------|
| pluginId | string | 唯一标识符 | ✅ |
| version | int | 版本号 | ✅ |
| versionName | string | 显示版本名 | ✅ |
| minHostVersion | int | 最低宿主版本 | ✅ |
| name | string | 插件名称 | ✅ |
| author | string | 作者 | ❌ |
| description | string | 描述 | ❌ |
| notice | string | 首次打开显示的说明 | ❌ |
| icon | string | 图标文件名 | ❌ |
| mainClass | string | 主类名（原生插件） | 原生✅ |
| uiType | string | native/web | ❌ |
| entry | string | 入口文件（Web 插件） | Web✅ |
| permissions | array | 所需权限列表 | ❌ |
| dependencies | array | 依赖插件列表 | ❌ |
| backend | string | 后端类型 | ❌ |
| backendPort | int | 后端端口 | ❌ |
| backendEntry | string | 后端入口路径 | ❌ |
| backendAutoStart | boolean | 自动启动 | ❌ |
| backendTimeout | int | 启动超时（秒） | ❌ |
| backendHealthCheck | string | 健康检查路径 | ❌ |

---

## 十、插件权限系统

### 10.1 权限声明

```json
{
    "permissions": [
        "android.permission.CAMERA",
        "android.permission.RECORD_AUDIO",
        "android.permission.ACCESS_FINE_LOCATION"
    ]
}
```

### 10.2 权限类型

| 权限 | 说明 | 类型 |
|------|------|------|
| READ_EXTERNAL_STORAGE | 读取存储 | 普通 |
| WRITE_EXTERNAL_STORAGE | 写入存储 | 普通 |
| MANAGE_EXTERNAL_STORAGE | 管理所有文件 | 特殊 |
| INTERNET | 访问网络 | 普通 |
| CAMERA | 相机 | 普通 |
| RECORD_AUDIO | 录音 | 普通 |
| ACCESS_FINE_LOCATION | 精确位置 | 普通 |
| SYSTEM_ALERT_WINDOW | 悬浮窗 | 特殊 |
| WRITE_SETTINGS | 修改系统设置 | 特殊 |
| ACCESSIBILITY | 无障碍权限 | 特殊 |

### 10.3 权限请求流程

1. 插件安装时：系统记录插件声明的权限
2. 插件启动时：自动检查权限状态
3. 权限缺失时：显示权限说明对话框
4. 用户授权：分组请求权限
5. 权限授予后：正常加载插件

---

## 十一、发布到插件仓库

### 11.1 仓库要求

| 要求 | 说明 |
|------|------|
| 仓库名称 | 必须为插件 ID |
| 仓库描述 | 必须为插件名称 |
| Release Tag | 格式：{版本代码}-{版本名称} |
| Release 资产 | 必须包含 .tpk 文件 |
| 仓库可见性 | 必须是公开仓库 |

### 11.2 发布步骤

1. 创建 GitHub 仓库（在 UIN-Tool-Plugins 组织中）
2. 上传插件文件
3. 创建 Release（Tag: 1-1.0.0）
4. 上传 .tpk 文件到 Assets
5. 强制更新：Tag 格式 {版本代码}-{版本名称}-1

---

## 十二、终端功能

### 12.1 概述

UIN Tool 内置完整的终端环境，核心引擎基于 Termux 改编。

### 12.2 终端特性

| 特性 | 说明 |
|------|------|
| Shell 支持 | bash、zsh、fish 等 |
| 包管理器 | APT (Debian/Ubuntu 软件源) |
| 开发工具 | gcc、clang、make、git |
| 脚本语言 | Python、Node.js、Ruby |
| 网络工具 | curl、wget、openssh |
| 多会话 | 支持多个终端会话同时运行 |
| 多窗口 | Android 7.0+ 多窗口支持 |

### 12.3 常用命令

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

### 12.4 在插件中调用终端命令

原生插件可以通过 `Runtime.exec()` 执行终端命令：

```kotlin
Runtime.getRuntime().exec(arrayOf("/system/bin/sh", "-c", "echo hello"))
```

---

## 十三、UI 个性化开发

### 13.1 颜色系统

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

### 13.2 颜色配置项

| 分类 | 颜色项 |
|------|--------|
| 主色调 | primary, primary_dark, primary_light, accent |
| 辅助色 | success, warning, error, info |
| 文本色 | text_primary, text_secondary, text_hint, text_primary_inverse |
| 背景色 | background, surface, surface_variant |
| 边框色 | divider, glass_background, disabled |

### 13.3 形状配置

```kotlin
// 获取圆角
val cornerRadius = uiConfig.getCardCornerRadius()
val buttonRadius = uiConfig.getButtonCornerRadius()

// 更新圆角
uiConfig.updateShape("cardCornerRadius", 16)
uiConfig.updateShape("buttonCornerRadius", 12)
```

### 13.4 主题系统

```kotlin
// 在 Compose 中使用
UINToolTheme {
    // UI 内容
}

// 获取主题颜色
val colorScheme = MaterialTheme.colorScheme
```

---

## 十四、调试技巧

### 14.1 日志输出

**原生插件：**
```kotlin
import com.UIN.Tool.log.Logger

Logger.i("TAG", "信息")
Logger.e("TAG", "错误", exception)
```

**Web 插件：**
```javascript
UINPlugin.callHost('log', '调试信息');
console.log('控制台输出');
```

**后端 Python：**
```python
print("调试信息")
```

### 14.2 查看运行日志

- 在「管理」→「运行日志」中查看
- 崩溃日志自动保存
- 日志位置：`/storage/emulated/0/UIN_Tool/logs/`

### 14.3 WebView 远程调试

1. 在 Chrome 浏览器打开 `chrome://inspect`
2. 确保 WebView 调试已启用
3. 支持断点、控制台、网络监控

### 14.4 后端调试

```python
# 在 Python 后端中打印调试信息
print(f"请求路径: {self.path}")
print(f"请求数据: {data}")
```

---

## 十五、常见问题

**Q1: 插件导入失败？**

可能原因：文件不是有效的 .tpk 格式、缺少 plugin.json、JSON 格式错误、签名验证失败。

解决方案：确保使用正确的打包方式，检查 plugin.json 格式，在开发者选项中可忽略签名验证（仅测试用）。

**Q2: 原生插件编译失败？**

当前状态：原生插件编译功能暂时禁用。临时方案：使用 PC 端编译 Java → DEX，或使用 Web 插件替代。

**Q3: Web 插件修改后不生效？**

Web 插件修改 HTML/CSS/JS 后，关闭并重新打开插件即可，无需重新编译。

**Q4: 插件无法调用宿主权限？**

在「管理」→「权限管理」→「插件权限」中为插件授予所需权限。

**Q5: 如何调试插件？**

使用 Logger 输出日志，在「管理」→「运行日志」中查看。Web 插件可用 Chrome DevTools 调试。

**Q6: Web 插件如何传递复杂数据？**

使用 JSON 格式：
```javascript
UINPlugin.callPlugin('processData', JSON.stringify({
    type: 'user',
    data: { name: '张三', age: 18 }
}));
```

**Q7: 强制更新是什么？**

当 Release Tag 格式为 `{版本代码}-{版本名称}-1` 时，会强制用户更新，无法跳过。

**Q8: GitHub 加速功能如何使用？**

在「管理」→「GitHub 加速」中配置镜像站和 CDN 加速。

**Q9: 终端功能如何使用？**

点击「开发」→「打开终端」，首次使用会自动安装 Linux 环境。

**Q10: 插件权限系统有什么作用？**

插件在 plugin.json 中声明所需权限，启动前自动检查并请求，确保插件安全运行。

**Q11: 如何恢复 UI 配置？**

在「管理」→「UI 个性化」中点击「重置」按钮。

**Q12: 如何导出开发模板？**

在「开发」页面点击「导出模板」，系统自动生成到工作目录。

**Q13: 如何创建带后端的 Web 插件？**

点击「创建插件」→ 选择「WebView + 后端」→ 选择后端语言（Python/Node.js/PHP/二进制）。

**Q14: Python 后端需要安装什么依赖？**

使用 Python 内置的 `http.server`，无需安装 Flask 等第三方库。

**Q15: 插件说明功能如何使用？**

在 `plugin.json` 中添加 `notice` 字段，首次打开时自动显示。

---

## 十六、最佳实践

### 16.1 命名规范

- 插件ID：域名倒序，如 `com.example.myplugin`
- 类名：PascalCase，如 `MainPlugin`
- 包名：与插件ID一致

### 16.2 性能优化

- 避免在 `onCreateView` 中执行耗时操作
- 使用协程处理异步任务
- Web 插件优化图片和 CSS 选择器
- 传感器使用后及时停止

### 16.3 内存管理

- 在 `onDestroy` 中释放资源
- 使用 Application Context 创建 View
- Web 插件注意清理 WebView

### 16.4 安全性

- 不要存储敏感信息明文
- 验证输入数据
- 使用 HTTPS
- 验证文件路径防止目录遍历

### 16.5 版本管理

- 使用语义化版本号
- 发布时使用正确的 Release Tag 格式
- 强制更新使用 `-1` 后缀

---

## 十七、技术支持

| 渠道 | 联系方式 |
|------|----------|
| 邮箱 | undefinedinvalidnull@outlook.com |
| GitHub | https://github.com/Undefined-Invalid-Null/UIN-Tool |
| 插件仓库 | https://github.com/UIN-Tool-Plugins |
| QQ 群 | 511875883 |

---

## 文档信息

| 项目 | 信息 |
|------|------|
| 文档版本 | 4.2.0 |
| 对应应用版本 | v4.2.0 (Build 12) |
| 最后更新 | 2026年7月24日 |

---

© 2026 UIN Team. All Rights Reserved.