# UIN Tool 开发文档

## 版本信息

| 项目 | 信息 |
|------|------|
| 文档版本 | 4.0.0 |
| 对应应用版本 | v4.0.0 (Build 10) |
| 最后更新 | 2026年7月14日 |

---

## 📑 目录

### 一、快速开始
- [第一步：创建插件](#第一步创建插件)
- [第二步：配置插件信息](#第二步配置插件信息)
- [第三步：编写代码](#第三步编写代码)
- [第四步：编译与打包](#第四步编译与打包)
- [第五步：打包并导入](#第五步打包并导入)

### 二、插件类型
- [对比表格](#对比表格)
- [如何选择](#如何选择)

### 三、原生插件开发
- [基本结构 (Kotlin)](#基本结构-kotlin)
- [支持的 Android 控件](#支持的-android-控件)
- [布局示例](#布局示例)
- [访问插件资源](#访问插件资源)

### 四、Web 插件开发
- [目录结构](#目录结构)
- [plugin.json 配置](#pluginjson-配置)
- [HTML 模板示例](#html-模板示例)
- [JavaScript 示例](#javascript-示例)

### 五、PluginInterface 接口详解
- [方法说明](#方法说明)
- [完整实现示例 (Kotlin)](#完整实现示例-kotlin)

### 六、JavaScript API 完整参考
- [callHost - 调用宿主方法](#callhost---调用宿主方法)
- [httpGet / httpPost - 网络请求](#httpget--httppost---网络请求)
- [传感器 API](#传感器-api)
- [文件系统 API](#文件系统-api)
- [存储 API](#存储-api)
- [信息获取 API](#信息获取-api)
- [系统 API](#系统-api)
- [粘贴 API](#粘贴-api)

### 七、打包与导入
- [打包方式](#打包方式)
- [文件结构](#文件结构)
- [plugin.json 完整字段](#pluginjson-完整字段)

### 八、插件权限系统
- [权限声明](#权限声明)
- [权限类型](#权限类型)
- [权限请求流程](#权限请求流程)
- [权限状态查看](#权限状态查看)

### 九、发布到插件仓库
- [仓库要求](#仓库要求)
- [发布步骤](#发布步骤)

### 十、终端功能开发（基于 Termux）
- [概述](#概述)
- [终端特性](#终端特性)
- [终端使用场景](#终端使用场景)
- [终端常用命令](#终端常用命令)
- [在插件中调用终端命令](#在插件中调用终端命令)
- [致谢 Termux](#致谢-termux)

### 十一、UI 个性化开发
- [颜色系统](#颜色系统)
- [颜色配置项](#颜色配置项)
- [形状配置](#形状配置)
- [主题系统](#主题系统)

### 十二、调试技巧
- [日志输出](#日志输出)
- [查看运行日志](#查看运行日志)
- [WebView 远程调试](#webview-远程调试)
- [传感器调试](#传感器调试)

### 十三、常见问题
- [插件导入失败](#q1-插件导入失败)
- [原生插件编译失败](#q2-原生插件编译失败)
- [Web 插件修改后不生效](#q3-web-插件修改后不生效)
- [插件无法调用宿主权限](#q4-插件无法调用宿主权限)
- [如何调试插件](#q5-如何调试插件)
- [Web 插件如何传递复杂数据](#q6-web-插件如何传递复杂数据)
- [强制更新是什么](#q7-强制更新是什么)
- [GitHub 加速功能如何使用](#q8-github-加速功能如何使用)
- [终端功能如何使用](#q9-终端功能如何使用)
- [插件权限系统有什么作用](#q10-插件权限系统有什么作用)
- [如何恢复 UI 配置](#q11-如何恢复-ui-配置)
- [如何导出开发模板](#q12-如何导出开发模板)

### 十四、最佳实践
- [命名规范](#命名规范)
- [性能优化](#性能优化)
- [内存管理](#内存管理)
- [安全性](#安全性)
- [权限声明](#权限声明-1)
- [版本管理](#版本管理)

### 十五、技术支持

---

## 一、快速开始

### 第一步：创建插件

1. 打开 UIN Tool App
2. 点击底部导航栏的「**开发**」标签
3. 点击「**创建原生插件**」或「**创建 Web 插件**」
4. 按照向导完成配置

### 第二步：配置插件信息

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

### 第三步：编写代码

根据选择的插件类型，编写对应的代码。详见下方各章节。

### 第四步：编译与打包

> ⚠️ **重要提示**：目前 UIN Tool 的原生插件编译功能暂时禁用（原因：Android 环境缺少 tools.jar 支持），Web 插件无需编译。

**原生插件（当前状态）**

- 编译功能暂时不可用：原生插件编译功能正在重构中
- 临时解决方案：
  1. 使用 PC 端编译 Java 源码为 DEX
  2. 或使用 Web 插件替代
  3. 或等待后续版本更新

**Web 插件（推荐）**

- 无需编译：修改 HTML/CSS/JS 后直接生效
- 即时预览：重新打开插件即可看到变化

### 第五步：打包并导入

1. 插件向导会自动生成项目文件和 TPK 包
2. 点击底部「**管理**」→「**插件管理**」
3. 点击「**导入**」选择 TPK 文件
4. 等待导入完成，即可在「**工具**」页面中看到插件

---

## 二、插件类型

### 对比表格

| 特性 | 原生插件 | Web 插件 |
|------|----------|----------|
| 开发语言 | Kotlin/Java | HTML/CSS/JS |
| UI 开发方式 | 代码动态创建 | HTML 布局 |
| 开发效率 | 中等 | 高 |
| 运行性能 | 高 | 中等 |
| 热更新 | 需重新编译 | 无需编译 |
| 学习成本 | 需懂 Android 开发 | 懂前端即可 |
| 调试难度 | 中等 | 低（浏览器 DevTools） |
| 适合场景 | 复杂交互、高性能 | 快速迭代、动态内容 |
| 编译方式 | 需要编译成 DEX | 无需编译 |
| 系统 API 访问 | 完全访问 | 通过 JS 接口 |
| 文件类型 | .dex | .html/.css/.js |

### 如何选择

- **选择原生插件**：需要访问系统 API、复杂动画、高性能计算、自定义 View
- **选择 Web 插件**：快速原型、界面频繁变动、已有 Web 项目、前端开发者参与

---

## 三、原生插件开发

### 基本结构 (Kotlin)

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
import com.UIN.Tool.core.plugin.PluginInterface

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

        // 使用 Application Context 避免主题问题
        val appContext = context.applicationContext
        val layout = LinearLayout(appContext).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 50, 50, 50)
        }

        // 标题
        val title = TextView(appContext).apply {
            text = "我的插件"
            textSize = 24f
            setTextColor(0xFF37474F.toInt())
            setPadding(0, 0, 0, 20)
        }

        // 计数器
        val counterText = TextView(appContext).apply {
            text = "点击次数: 0"
            textSize = 16f
            setTextColor(0xFF666666.toInt())
            setPadding(0, 0, 0, 20)
        }

        // 按钮
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

    override fun onResume() {
        // 插件恢复时调用
    }

    override fun onPause() {
        // 插件暂停时调用
    }

    override fun onDestroy() {
        rootView = null
    }

    override fun onBackPressed(): Boolean {
        return false
    }

    override fun onSaveInstanceState(): Bundle? {
        return Bundle().apply {
            putInt("clickCount", clickCount)
        }
    }
}
```

支持的 Android 控件

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
SeekBar 滑块 setProgress(), setOnSeekBarChangeListener()

布局示例

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

// 相对布局
val layout = RelativeLayout(appContext)
val centerText = TextView(appContext).apply {
    text = "居中显示"
    layoutParams = RelativeLayout.LayoutParams(
        RelativeLayout.LayoutParams.WRAP_CONTENT,
        RelativeLayout.LayoutParams.WRAP_CONTENT
    ).apply {
        addRule(RelativeLayout.CENTER_IN_PARENT)
    }
}
```

访问插件资源

```kotlin
// 获取插件目录
val pluginDir = context.filesDir.parentFile
val pluginPath = pluginDir?.absolutePath ?: ""

// 读取配置文件
val configFile = File(pluginPath, "config.json")
if (configFile.exists()) {
    val content = configFile.readText()
}

// 读取图片资源
val iconFile = File(pluginPath, "icon.png")
if (iconFile.exists()) {
    val bitmap = BitmapFactory.decodeFile(iconFile.absolutePath)
    imageView.setImageBitmap(bitmap)
}
```

---

四、Web 插件开发

目录结构

```
your-plugin/
├── plugin.json          # 插件配置文件（必需）
├── icon.png             # 插件图标（建议 128x128）
└── web/                 # Web 资源目录（必需）
    ├── index.html       # 主页面（必需）
    ├── style.css        # 样式文件（可选）
    └── script.js        # JavaScript 文件（可选）
```

plugin.json 配置

```json
{
    "pluginId": "com.example.webplugin",
    "version": 1,
    "versionName": "1.0.0",
    "minHostVersion": 1,
    "name": "Web插件示例",
    "author": "开发者名称",
    "description": "这是一个Web插件示例",
    "icon": "icon.png",
    "mainClass": "",
    "apiLevel": 21,
    "uiType": "web",
    "entry": "web/index.html",
    "permissions": ["INTERNET", "VIBRATE"]
}
```

💡 提示：Web 插件不需要编译，修改 HTML/CSS/JS 后直接重新打开插件即可生效。

HTML 模板示例

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

JavaScript 示例

```javascript
// ==================== 基础功能 ====================

function showToast() {
    UINPlugin.callHost('toast', 'Hello from WebView!');
}

function closePlugin() {
    UINPlugin.callHost('finish', '');
}

function logMessage(message) {
    UINPlugin.callHost('log', message);
    console.log(message);
}

// ==================== 网络请求 ====================

function testHttpGet() {
    const url = 'https://api.github.com/orgs/UIN-Tool-Plugins/repos';
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
    
    UINPlugin.httpGet(url, callbackId);
}

function testHttpPost() {
    const url = 'https://httpbin.org/post';
    const postData = JSON.stringify({test: 'Hello World', time: Date.now()});
    const callbackId = 'post_' + Date.now();
    
    window.UINPluginCallbacks = window.UINPluginCallbacks || {};
    window.UINPluginCallbacks[callbackId] = function(response) {
        const data = JSON.parse(response);
        if (data.success) {
            console.log('POST 成功:', data.data);
            alert('POST 请求成功！');
        } else {
            console.error('POST 失败:', data.error);
            alert('请求失败: ' + data.error);
        }
        delete window.UINPluginCallbacks[callbackId];
    };
    
    UINPlugin.httpPost(url, postData, callbackId);
}

// ==================== 传感器 ====================

function startAccelerometer() {
    const callbackId = 'sensor_' + Date.now();
    window.UINPluginCallbacks = window.UINPluginCallbacks || {};
    window.UINPluginCallbacks[callbackId] = function(data) {
        const sensorData = JSON.parse(data);
        if (sensorData.success) {
            console.log(`X: ${sensorData.x}, Y: ${sensorData.y}, Z: ${sensorData.z}`);
            document.getElementById('output').innerHTML = 
                `X: ${sensorData.x.toFixed(2)}<br>Y: ${sensorData.y.toFixed(2)}<br>Z: ${sensorData.z.toFixed(2)}`;
        }
    };
    UINPlugin.startSensor('accelerometer', callbackId);
    showToast('启动加速度计');
}

function stopSensor() {
    UINPlugin.stopSensor();
    showToast('传感器已停止');
}

// ==================== 文件系统 ====================

function writeTestFile() {
    const content = `测试文件内容\n时间: ${new Date().toLocaleString()}`;
    const success = UINPlugin.writeFile('test.txt', content);
    alert(success ? '文件写入成功' : '文件写入失败');
}

function readTestFile() {
    const content = UINPlugin.readFile('test.txt');
    alert(content ? '文件内容:\n' + content : '文件不存在');
}

function listFiles() {
    const files = UINPlugin.listFiles('');
    alert('文件列表:\n' + (files.length ? files.join('\n') : '目录为空'));
}

// ==================== 存储 ====================

function saveData() {
    UINPlugin.setStorage('key', 'value');
    showToast('已保存');
}

function loadData() {
    const value = UINPlugin.getStorage('key');
    showToast('读取: ' + value);
}

function clearStorage() {
    UINPlugin.clearStorage();
    showToast('已清空');
}

// ==================== 生命周期 ====================

document.addEventListener('DOMContentLoaded', () => {
    console.log('Web 插件已加载');
});

window.addEventListener('resume', () => {
    console.log('插件恢复');
});

window.addEventListener('pause', () => {
    console.log('插件暂停');
    stopSensor(); // 暂停时停止传感器
});

window.addEventListener('destroy', () => {
    console.log('插件销毁');
    stopSensor();
});
```

---

五、PluginInterface 接口详解

方法说明

方法 说明 必须实现 调用时机
onCreateView 创建插件视图 ✅ 是 插件首次打开时
onResume 插件恢复 ❌ 否 从其他页面返回时
onPause 插件暂停 ❌ 否 切换到其他页面时
onDestroy 插件销毁 ❌ 否 插件被关闭时
onBackPressed 返回键按下 ❌ 否 用户按下返回键时
onSaveInstanceState 保存状态 ❌ 否 系统需要保存状态时
onActivityResult Activity 结果 ❌ 否 startActivityForResult 返回时
onRequestPermissionsResult 权限请求结果 ❌ 否 权限请求完成时

完整实现示例 (Kotlin)

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
import com.UIN.Tool.core.plugin.PluginInterface

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

        val resetButton = Button(appContext).apply {
            text = "重置"
            setBackgroundColor(0xFF607D8B.toInt())
            setTextColor(0xFFFFFFFF.toInt())
            setOnClickListener {
                clickCount = 0
                counterText?.text = "点击次数: 0"
                Toast.makeText(context, "已重置", Toast.LENGTH_SHORT).show()
            }
        }

        layout.addView(title)
        layout.addView(counterText)
        layout.addView(button)
        layout.addView(resetButton)

        rootView = layout

        // 恢复保存的状态
        savedInstanceState?.let {
            clickCount = it.getInt("clickCount", 0)
            counterText?.text = "点击次数: $clickCount"
        }

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

六、JavaScript API 完整参考

callHost - 调用宿主方法

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
```

httpGet / httpPost - 网络请求

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

传感器 API

```javascript
// 启动传感器
const callbackId = 'sensor_' + Date.now();
UINPlugin.startSensor('accelerometer', callbackId);

// 停止传感器
UINPlugin.stopSensor();

// 获取可用传感器
const sensors = JSON.parse(UINPlugin.getAvailableSensors());
// 返回: { accelerometer: true, gyroscope: true, ... }
```

支持的传感器类型：

类型 说明 回调数据
accelerometer 加速度计 x, y, z
gyroscope 陀螺仪 x, y, z
magneticField 磁场计 x, y, z
light 光线传感器 lux
proximity 接近传感器 distance
pressure 压力传感器 pressure

文件系统 API

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

存储 API

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

信息获取 API

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
```

系统 API

```javascript
// 设置标题
UINPlugin.setTitle('新标题');

// 全屏模式
UINPlugin.setFullscreen(true);  // 进入全屏
UINPlugin.setFullscreen(false); // 退出全屏

// 打开系统设置
UINPlugin.openSettings();

// 打开应用设置
UINPlugin.openAppSettings();

// 权限检查
const granted = UINPlugin.checkPermission('android.permission.CAMERA');

// 请求权限
UINPlugin.requestPermission('android.permission.CAMERA', callbackId);
```

---

七、打包与导入

打包方式

方式一：使用向导打包

1. 在「开发」页面点击「创建原生插件」或「创建 Web 插件」
2. 按照向导完成配置
3. 在最后一步点击「完成」
4. 系统自动生成项目文件和 TPK 包
5. 工作目录：/storage/emulated/0/UIN_Tool/

方式二：手动打包

1. 将插件文件整理到文件夹中
2. 确保有 plugin.json 和必要文件
3. 压缩为 ZIP 格式
4. 重命名为 .tpk 扩展名

文件结构

原生插件结构：

```
plugin.tpk
├── plugin.json      # 必需
├── icon.png         # 可选
├── plugin.dex       # 必需（当前需手动编译）
├── src/             # 可选
└── res/             # 可选
```

Web 插件结构：

```
plugin.tpk
├── plugin.json      # 必需
├── icon.png         # 可选
└── web/             # 必需
    ├── index.html   # 必需
    ├── style.css    # 可选
    ├── script.js    # 可选
    └── assets/      # 可选
```

plugin.json 完整字段

字段 类型 说明 必填
pluginId string 唯一标识符 ✅
version int 版本号 ✅
versionName string 显示版本名 ✅
minHostVersion int 最低宿主版本 ✅
name string 插件名称 ✅
author string 作者 ❌
description string 描述 ❌
icon string 图标文件名 ❌
mainClass string 主类名（原生插件） 原生✅
updateUrl string 更新检查 URL ❌
apiLevel int 最低 API 级别 ❌
category string 分类 ❌
uiType string native/web ❌
entry string 入口文件（Web 插件） Web✅
permissions array 所需权限列表 ❌
dependencies array 依赖插件列表 ❌

---

八、插件权限系统

权限声明

在 plugin.json 中声明插件需要的权限：

```json
{
    "pluginId": "com.example.myplugin",
    "name": "我的插件",
    "permissions": [
        "android.permission.CAMERA",
        "android.permission.RECORD_AUDIO",
        "android.permission.ACCESS_FINE_LOCATION"
    ]
}
```

权限类型

权限 说明 类型
READ_EXTERNAL_STORAGE 读取存储 普通
WRITE_EXTERNAL_STORAGE 写入存储 普通
MANAGE_EXTERNAL_STORAGE 管理所有文件 特殊
INTERNET 访问网络 普通
CAMERA 相机 普通
RECORD_AUDIO 录音 普通
ACCESS_FINE_LOCATION 精确位置 普通
ACCESS_BACKGROUND_LOCATION 后台位置 特殊
SYSTEM_ALERT_WINDOW 悬浮窗 特殊
WRITE_SETTINGS 修改系统设置 特殊
REQUEST_INSTALL_PACKAGES 安装未知应用 特殊
ACCESSIBILITY 无障碍权限 特殊

权限请求流程

1. 插件安装时：系统记录插件声明的权限
2. 插件启动时：自动检查权限状态
3. 权限缺失时：显示权限说明对话框
4. 用户授权：分组请求权限
5. 权限授予后：正常加载插件

权限状态查看

在「管理」→「权限管理」→「插件权限」中：

· 查看每个插件的权限状态
· 手动请求未授予的权限
· 查看权限详细说明

---

九、发布到插件仓库

仓库要求

要求 说明
仓库名称 必须为插件 ID
仓库描述 必须为插件名称
Release Tag 格式：{版本代码}-{版本名称}
Release 资产 必须包含 .tpk 文件
仓库可见性 必须是公开仓库

发布步骤

1. 创建 GitHub 仓库
   · 在 UIN-Tool-Plugins 组织中创建仓库
   · 仓库名称设置为插件 ID
2. 上传插件文件
   ```bash
   git clone https://github.com/UIN-Tool-Plugins/your.plugin.id
   cd your.plugin.id
   cp your-plugin.tpk .
   git add your-plugin.tpk
   git commit -m "Add plugin v1.0.0"
   git push
   ```
3. 创建 Release
   · Tag: 1-1.0.0 (格式：版本代码-版本名称)
   · 上传 .tpk 文件到 Assets
4. 强制更新
   · Tag 格式：{版本代码}-{版本名称}-1
   · 例：2-1.0.1-1 表示强制更新

---

十、终端功能开发（基于 Termux）

概述

UIN Tool 内置完整的终端环境，核心引擎基于 Termux 改编。终端功能为插件开发者提供了强大的 Linux 命令行工具链。

终端特性

特性 说明
Shell 支持 bash、zsh、fish 等
包管理器 APT (Debian/Ubuntu 软件源)
开发工具 gcc、clang、make、git
脚本语言 Python、Node.js、Ruby
文本编辑器 vim、nano、emacs
网络工具 curl、wget、openssh
多会话 支持多个终端会话同时运行
多窗口 Android 7.0+ 多窗口支持

终端使用场景

· 插件开发调试：在 Android 设备上直接编译和测试
· 服务器管理：通过 SSH 管理远程服务器
· 学习 Linux：无需 Root 即可体验完整的 Linux 环境
· 自动化脚本：编写 Shell/Python 脚本

终端常用命令

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

# 查看存储目录
ls ~/storage/shared/
```

在插件中调用终端命令

原生插件可以通过 Runtime.exec() 执行终端命令，但建议使用 UIN Tool 提供的 RunCommandService：

```java
// 通过 RunCommandService 执行命令
Intent intent = new Intent(RUN_COMMAND_SERVICE.ACTION_RUN_COMMAND);
intent.setClass(context, RunCommandService.class);
intent.putExtra(RUN_COMMAND_SERVICE.EXTRA_COMMAND_PATH, "/system/bin/sh");
intent.putExtra(RUN_COMMAND_SERVICE.EXTRA_ARGUMENTS, new String[]{"-c", "echo hello"});
context.startService(intent);
```

致谢 Termux

终端功能的实现基于 Termux 项目改编，感谢 Termux 团队的开源贡献！

---

十一、UI 个性化开发

颜色系统

UIN Tool 提供 38+ 颜色配置项，全部可通过 UIConfig 自定义：

```kotlin
val uiConfig = UIConfig.getInstance()

// 获取颜色
val primaryColor = uiConfig.getPrimaryColor()
val textPrimaryColor = uiConfig.getTextPrimaryColor()

// 更新颜色
uiConfig.updateColor("primary", "#FF1A3A4A")
uiConfig.updateColor("text_primary", "#FF212121")

// 保存配置
uiConfig.saveConfig()
```

颜色配置项

分类 颜色项
主色调 primary, primary_dark, primary_light, accent
辅助色 success, warning, error, info
文本色 text_primary, text_secondary, text_hint, text_primary_inverse
背景色 background, surface, surface_variant
边框色 divider, glass_background, disabled

形状配置

```kotlin
// 获取圆角
val cornerRadius = uiConfig.getCardCornerRadius()
val buttonRadius = uiConfig.getButtonCornerRadius()

// 更新圆角
uiConfig.updateShape("cardCornerRadius", 16)
uiConfig.updateShape("buttonCornerRadius", 12)
```

主题系统

UIN Tool 使用 Jetpack Compose 主题系统：

```kotlin
// 在 Compose 中使用
UINToolTheme {
    // UI 内容
}

// 获取主题颜色
val colorScheme = MaterialTheme.colorScheme
val primary = colorScheme.primary
val surface = colorScheme.surface
```

---

十二、调试技巧

日志输出

原生插件：

```kotlin
import com.UIN.Tool.log.Logger

Logger.i("TAG", "信息")
Logger.d("TAG", "调试")
Logger.e("TAG", "错误", exception)
Logger.success("TAG", "成功")
Logger.enter("TAG", "方法名")
Logger.exit("TAG", "方法名", startTime)
Logger.param("TAG", "参数名", 参数值)
```

Web 插件：

```javascript
UINPlugin.callHost('log', '调试信息');
console.log('控制台输出');
```

查看运行日志

· 在「管理」→「运行日志」中查看
· 崩溃日志自动保存
· 日志位置：/storage/emulated/0/UIN_Tool/logs/

WebView 远程调试

1. 在 Chrome 浏览器打开 chrome://inspect
2. 确保 WebView 调试已启用
3. 支持断点、控制台、网络监控

传感器调试

```javascript
// 检查可用传感器
const sensors = JSON.parse(UINPlugin.getAvailableSensors());
console.log('可用传感器:', sensors);

// 启动传感器并查看数据
UINPlugin.startSensor('accelerometer', callbackId);
```

---

十三、常见问题

Q1: 插件导入失败？

可能原因：

· 文件不是有效的 .tpk 格式
· 缺少 plugin.json 文件
· JSON 格式错误
· 原生插件缺少 plugin.dex
· Web 插件缺少 web/index.html
· 签名验证失败

解决方案：

· 确保使用正确的打包方式
· 检查 plugin.json 格式
· 在开发者选项中可忽略签名验证（仅测试用）

Q2: 原生插件编译失败？

当前状态： 原生插件编译功能暂时禁用（Android 环境缺少 tools.jar 支持）

临时方案：

1. 使用 PC 端编译 Java → DEX
2. 或使用 Web 插件替代
3. 等待后续版本更新

Q3: Web 插件修改后不生效？

Web 插件修改 HTML/CSS/JS 后，关闭并重新打开插件即可，无需重新编译。

Q4: 插件无法调用宿主权限？

在「管理」→「权限管理」→「插件权限」中为插件授予所需权限。

Q5: 如何调试插件？

1. 使用 Logger 输出日志
2. 在「管理」→「运行日志」中查看
3. 崩溃日志自动保存
4. Web 插件可用 Chrome DevTools 调试

Q6: Web 插件如何传递复杂数据？

使用 JSON 格式：

```javascript
UINPlugin.callPlugin('processData', JSON.stringify({
    type: 'user',
    data: { name: '张三', age: 18 }
}));
```

Q7: 强制更新是什么？

当 Release Tag 格式为 {版本代码}-{版本名称}-1 时，会强制用户更新，无法跳过。

Q8: GitHub 加速功能如何使用？

在「管理」→「GitHub 加速」中：

· 添加自定义镜像站
· 勾选启用的镜像站
· 开启 CDN 加速
· 点击「保存设置」

Q9: 终端功能如何使用？

点击底部「开发」→「打开终端」，首次使用会自动安装 Linux 环境。

Q10: 插件权限系统有什么作用？

插件在 plugin.json 中声明所需权限，启动前自动检查并请求，确保插件安全运行。

Q11: 如何恢复 UI 配置？

在「管理」→「UI 个性化」中点击「重置」按钮。

Q12: 如何导出开发模板？

在「开发」页面点击「导出模板」，系统自动生成到工作目录。

---

十四、最佳实践

命名规范

· 插件ID：域名倒序，如 com.example.myplugin
· 类名：PascalCase，如 MainPlugin
· 包名：与插件ID一致

性能优化

· 避免在 onCreateView 中执行耗时操作
· 使用协程处理异步任务
· Web 插件优化图片和 CSS 选择器
· 传感器使用后及时停止

内存管理

· 在 onDestroy 中释放资源
· 使用 Application Context 创建 View
· Web 插件注意清理 WebView
· 及时注销传感器监听器

安全性

· 不要存储敏感信息明文
· 验证输入数据
· 使用 HTTPS
· 验证文件路径防止目录遍历

权限声明

```json
{
    "permissions": [
        "android.permission.INTERNET",
        "android.permission.VIBRATE"
    ]
}
```

版本管理

· 使用语义化版本号
· 发布时使用正确的 Release Tag 格式
· 强制更新使用 -1 后缀

---

十五、技术支持

渠道 地址
📧 邮箱 undefinedinvalidnull@outlook.com
🌐 GitHub https://github.com/Undefined-Invalid-Null/UIN-Tool
📦 插件仓库 https://github.com/UIN-Tool-Plugins
💬 QQ群 511875883

---

文档信息

项目 信息
文档版本 4.0.0
对应应用版本 v4.0.0 (Build 10)
最后更新 2026年7月14日

---

© 2026 UIN Team. All Rights Reserved.

