# UIN Tool 开发文档

## 目录

- [快速开始](#快速开始)
- [插件类型](#插件类型)
- [原生插件开发](#原生插件开发)
- [Web插件开发](#web插件开发)
- [PluginInterface 接口详解](#plugininterface-接口详解)
- [JavaScript API 完整参考](#javascript-api-完整参考)
- [打包与导入](#打包与导入)
- [发布到插件仓库](#发布到插件仓库)
- [调试技巧](#调试技巧)
- [常见问题](#常见问题)
- [最佳实践](#最佳实践)

---

## 快速开始

### 第一步：创建插件

1. 打开 UIN Tool App
2. 点击底部导航栏的「开发」标签
3. 点击「创建新插件」按钮
4. 选择插件类型：
   - **原生插件 (Native)**：使用 Java 代码动态创建 UI，性能最优
   - **Web插件 (WebView)**：使用 HTML/CSS/JS 开发 UI，支持热更新

### 第二步：配置插件信息

| 字段 | 说明 | 示例 | 必填 |
|------|------|------|------|
| 插件ID | 唯一标识符，域名倒序格式 | `com.example.myplugin` | ✅ |
| 插件名称 | 在列表中显示的名称 | `我的插件` | ✅ |
| 作者 | 开发者名称 | `张三` | ❌ |
| 描述 | 插件功能说明 | `这是一个示例插件` | ❌ |
| 版本号 | 数字版本，用于版本比较 | `1` | ✅ |
| 版本名 | 显示版本号 | `1.0.0` | ✅ |
| 主类名 | 入口类的完整路径 | `com.example.MainPlugin` | ✅ |

### 第三步：编写代码

根据选择的插件类型，编写对应的代码。详见下方各章节。

### 第四步：编译原生插件（重要）

> ⚠️ **注意**：目前 UIN Tool 仅支持生成插件模板，Java 代码编译成 DEX 需要用户自己操作。Web 插件无需编译。

#### 手机端编译指南

**准备工作：**
1. 确保已安装 ECJ 和 D8 编译工具
2. 准备好 `host-sdk.jar`（从 UIN Tool 的 assets/compiler 目录获取）

**编译步骤：**

```bash
# 1. 进入工作目录
cd /storage/emulated/0/Works/UIN_Tool

# 2. 编译 Java 文件为 class
ecj -d . -cp host-sdk.jar MainPlugin.java

# 3. 转换 class 为 DEX
d8 --lib host-sdk.jar --min-api 21 --output . *.class

# 4. 重命名 DEX 文件
mv classes.dex plugin.dex

# 5. 复制到插件目录
cp plugin.dex /storage/emulated/0/UIN_Tool/com.example.nativeplugin/
```

第五步：打包并导入

1. 将编译好的 plugin.dex 与 plugin.json、icon.png 等文件一起打包为 ZIP
2. 将 ZIP 重命名为 .tpk
3. 点击底部「管理」标签
4. 点击「插件管理」
5. 点击「导入插件」按钮
6. 选择 .tpk 文件
7. 等待导入完成，即可在「工具」标签中看到插件

第六步：发布到插件仓库

完成插件开发后，可以将其发布到官方插件仓库，让更多用户发现和使用。详见 发布到插件仓库 章节。

---

插件类型

对比表格

特性 原生插件 Web插件
UI开发方式 Java 代码动态创建 HTML/CSS/JS
开发效率 中等 高
运行性能 高 中等
热更新 需重新编译 DEX 无需编译，直接修改文件
学习成本 需懂 Android 开发 懂前端即可
调试难度 中等 低（浏览器 DevTools）
适合场景 复杂交互、高性能要求 简单界面、快速迭代、动态内容
编译方式 需要手动编译成 DEX 无需编译
网络请求 原生支持 通过 JS API
文件系统 完全访问 受限访问（插件目录）
传感器 完全访问 通过 JS API

如何选择

· 选择原生插件：需要访问系统 API、复杂动画、高性能计算、自定义 View
· 选择 Web插件：快速原型、界面频繁变动、已有 Web 项目、希望前端开发者参与

---

原生插件开发

基本结构

```java
package com.example;

import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.UIN.Tool.plugin.PluginInterface;

public class MainPlugin implements PluginInterface {

    private Context context;
    private View rootView;

    @Override
    public View onCreateView(Context context, ViewGroup container, Bundle savedInstanceState) {
        this.context = context;

        // 创建根布局（建议使用 Application Context 避免主题问题）
        Context appContext = context.getApplicationContext();
        LinearLayout layout = new LinearLayout(appContext);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 50, 50, 50);

        // 标题
        TextView title = new TextView(appContext);
        title.setText("我的插件");
        title.setTextSize(24);
        title.setTextColor(0xFF37474F);
        title.setPadding(0, 0, 0, 20);

        // 按钮
        Button button = new Button(appContext);
        button.setText("点击我");
        button.setBackgroundColor(0xFF37474F);
        button.setTextColor(0xFFFFFFFF);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(context, "插件运行成功！", Toast.LENGTH_SHORT).show();
            }
        });

        layout.addView(title);
        layout.addView(button);

        rootView = layout;
        return rootView;
    }

    @Override
    public void onResume() {
        // 插件恢复时调用，可以刷新数据
    }

    @Override
    public void onPause() {
        // 插件暂停时调用，可以暂停动画或保存临时数据
    }

    @Override
    public void onDestroy() {
        // 清理资源
        if (rootView != null) {
            // 释放资源
        }
    }

    @Override
    public boolean onBackPressed() {
        // 返回 true 表示消费了返回事件
        // 返回 false 表示让系统处理（关闭插件）
        return false;
    }

    @Override
    public Bundle onSaveInstanceState() {
        Bundle bundle = new Bundle();
        bundle.putString("key", "value");
        return bundle;
    }
}
```

支持的 Android 控件

控件 说明 常用方法
TextView 文本显示 setText(), setTextSize(), setTextColor()
EditText 文本输入 getText(), setHint(), addTextChangedListener()
Button 按钮 setText(), setOnClickListener()
ImageView 图片显示 setImageResource(), setImageBitmap()
LinearLayout 线性布局 setOrientation(), setGravity()
RelativeLayout 相对布局 addRule()
FrameLayout 帧布局 用于层叠视图
GridLayout 网格布局 setColumnCount(), setRowCount()
RecyclerView 列表视图 setAdapter(), setLayoutManager()
ScrollView 滚动视图 包裹内容使其可滚动
WebView 网页视图 loadUrl(), loadData()
ProgressBar 进度条 setProgress(), setVisibility()
CheckBox 复选框 setChecked(), isChecked()
RadioButton 单选按钮 setChecked(), isChecked()
Switch 开关 setChecked(), isChecked()
SeekBar 滑块 setProgress(), setOnSeekBarChangeListener()

布局示例

线性布局（垂直）

```java
LinearLayout layout = new LinearLayout(appContext);
layout.setOrientation(LinearLayout.VERTICAL);
layout.setLayoutParams(new ViewGroup.LayoutParams(
    ViewGroup.LayoutParams.MATCH_PARENT,
    ViewGroup.LayoutParams.WRAP_CONTENT
));

TextView textView = new TextView(appContext);
textView.setText("标题");
layout.addView(textView);

Button button = new Button(appContext);
button.setText("按钮");
layout.addView(button);
```

线性布局（水平）

```java
LinearLayout rowLayout = new LinearLayout(appContext);
rowLayout.setOrientation(LinearLayout.HORIZONTAL);
rowLayout.setGravity(Gravity.CENTER_VERTICAL);

TextView label = new TextView(appContext);
label.setText("标签：");

EditText input = new EditText(appContext);
input.setHint("请输入内容");
input.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

rowLayout.addView(label);
rowLayout.addView(input);
```

相对布局

```java
RelativeLayout layout = new RelativeLayout(appContext);

TextView centerText = new TextView(appContext);
centerText.setText("居中显示");
RelativeLayout.LayoutParams centerParams = new RelativeLayout.LayoutParams(
    RelativeLayout.LayoutParams.WRAP_CONTENT,
    RelativeLayout.LayoutParams.WRAP_CONTENT
);
centerParams.addRule(RelativeLayout.CENTER_IN_PARENT);
centerText.setLayoutParams(centerParams);

Button bottomButton = new Button(appContext);
bottomButton.setText("底部按钮");
RelativeLayout.LayoutParams bottomParams = new RelativeLayout.LayoutParams(
    RelativeLayout.LayoutParams.WRAP_CONTENT,
    RelativeLayout.LayoutParams.WRAP_CONTENT
);
bottomParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
bottomParams.addRule(RelativeLayout.CENTER_HORIZONTAL);
bottomButton.setLayoutParams(bottomParams);

layout.addView(centerText);
layout.addView(bottomButton);
```

列表视图（ListView）

```java
ListView listView = new ListView(appContext);
String[] items = {"选项1", "选项2", "选项3"};
ArrayAdapter<String> adapter = new ArrayAdapter<>(appContext, 
    android.R.layout.simple_list_item_1, items);
listView.setAdapter(adapter);

listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        Toast.makeText(context, "点击了: " + items[position], Toast.LENGTH_SHORT).show();
    }
});
```

访问插件资源

```java
// 获取插件目录
File pluginDir = context.getFilesDir().getParentFile();
String pluginPath = pluginDir.getAbsolutePath();

// 读取插件中的文件
File configFile = new File(pluginPath, "config.json");
if (configFile.exists()) {
    String content = readFileToString(configFile);
}
```

---

Web插件开发

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
    "entry": "web/index.html"
}
```

💡 提示：Web 插件不需要编译，修改 HTML/CSS/JS 后直接重新打开插件即可生效。

HTML 模板示例

```html
<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0, user-scalable=yes">
    <title>我的Web插件</title>
    <link rel="stylesheet" href="style.css">
</head>
<body>
    <div class="container">
        <div class="header">
            <h1>我的插件</h1>
            <p>这是一个Web插件示例</p>
        </div>
        
        <div class="content">
            <div class="card">
                <h3>基础功能</h3>
                <button onclick="showToast()" class="btn-primary">显示提示</button>
                <button onclick="closePlugin()" class="btn-danger">关闭插件</button>
            </div>
            
            <div class="card">
                <h3>网络请求</h3>
                <button onclick="testHttpGet()" class="btn-info">GET 请求</button>
                <button onclick="testHttpPost()" class="btn-info">POST 请求</button>
            </div>
            
            <div class="card">
                <h3>传感器</h3>
                <button onclick="startAccelerometer()" class="btn-secondary">加速度计</button>
                <button onclick="stopSensor()" class="btn-danger">停止传感器</button>
            </div>
            
            <div class="card">
                <h3>文件系统</h3>
                <button onclick="writeTestFile()" class="btn-secondary">写入文件</button>
                <button onclick="readTestFile()" class="btn-secondary">读取文件</button>
                <button onclick="listFiles()" class="btn-secondary">列出文件</button>
            </div>
        </div>
        
        <div class="footer">
            <span>版本 1.0.0</span>
        </div>
    </div>
    
    <script src="script.js"></script>
</body>
</html>
```

CSS 样式示例

```css
* {
    margin: 0;
    padding: 0;
    box-sizing: border-box;
}

body {
    font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
    background: #f5f7fa;
    min-height: 100vh;
}

.container {
    max-width: 600px;
    margin: 0 auto;
    padding: 16px;
}

.header {
    background: linear-gradient(135deg, #37474F 0%, #263238 100%);
    color: white;
    padding: 32px 20px;
    text-align: center;
    border-radius: 24px;
    margin-bottom: 20px;
}

.header h1 {
    font-size: 28px;
    margin-bottom: 8px;
}

.header p {
    opacity: 0.85;
    font-size: 14px;
}

.card {
    background: white;
    border-radius: 16px;
    padding: 20px;
    margin-bottom: 16px;
    box-shadow: 0 2px 8px rgba(0,0,0,0.05);
}

.card h3 {
    color: #37474F;
    margin-bottom: 16px;
    font-size: 16px;
}

button {
    border: none;
    padding: 10px 20px;
    border-radius: 24px;
    font-size: 14px;
    font-weight: 500;
    cursor: pointer;
    margin-right: 10px;
    margin-bottom: 10px;
}

.btn-primary {
    background: #37474F;
    color: white;
}

.btn-primary:hover {
    background: #263238;
}

.btn-info {
    background: #2196F3;
    color: white;
}

.btn-secondary {
    background: #607D8B;
    color: white;
}

.btn-danger {
    background: #F44336;
    color: white;
}

.footer {
    text-align: center;
    padding: 16px;
    font-size: 12px;
    color: #999;
}
```

JavaScript 示例

```javascript
// ==================== 基础功能 ====================

// 显示 Toast 提示
function showToast() {
    UINPlugin.callHost('toast', 'Hello from WebView!');
}

// 关闭插件
function closePlugin() {
    UINPlugin.callHost('finish', '');
}

// 输出日志
function logMessage(message) {
    UINPlugin.callHost('log', message);
    console.log(message);
}

// ==================== 网络请求 ====================

// GET 请求
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
        }
        delete window.UINPluginCallbacks[callbackId];
    };
    
    UINPlugin.httpGet(url, callbackId);
}

// POST 请求
function testHttpPost() {
    const url = 'https://httpbin.org/post';
    const postData = JSON.stringify({test: 'Hello World'});
    const callbackId = 'post_' + Date.now();
    
    window.UINPluginCallbacks = window.UINPluginCallbacks || {};
    window.UINPluginCallbacks[callbackId] = function(response) {
        const data = JSON.parse(response);
        if (data.success) {
            console.log('POST 成功:', data.data);
            alert('请求成功！');
        } else {
            console.error('POST 失败:', data.error);
        }
        delete window.UINPluginCallbacks[callbackId];
    };
    
    UINPlugin.httpPost(url, postData, callbackId);
}

// ==================== 传感器 ====================

let currentSensorCallback = null;

function startAccelerometer() {
    const callbackId = 'sensor_' + Date.now();
    
    window.UINPluginCallbacks = window.UINPluginCallbacks || {};
    window.UINPluginCallbacks[callbackId] = function(data) {
        const sensorData = JSON.parse(data);
        if (sensorData.success) {
            document.getElementById('sensor-output').innerHTML = 
                `X: ${sensorData.x.toFixed(2)}<br>Y: ${sensorData.y.toFixed(2)}<br>Z: ${sensorData.z.toFixed(2)}`;
        }
    };
    
    UINPlugin.startSensor('accelerometer', callbackId);
    currentSensorCallback = callbackId;
}

function stopSensor() {
    if (currentSensorCallback) {
        delete window.UINPluginCallbacks[currentSensorCallback];
        currentSensorCallback = null;
    }
    UINPlugin.stopSensor();
}

// ==================== 文件系统 ====================

function writeTestFile() {
    const success = UINPlugin.writeFile('test.txt', 'Hello from Web Plugin!');
    if (success) {
        alert('文件写入成功');
        listFiles();
    } else {
        alert('文件写入失败');
    }
}

function readTestFile() {
    const content = UINPlugin.readFile('test.txt');
    if (content) {
        alert('文件内容: ' + content);
    } else {
        alert('文件不存在');
    }
}

function listFiles() {
    const files = UINPlugin.listFiles('');
    alert('文件列表:\n' + (files.length ? files.join('\n') : '空目录'));
}

// ==================== 信息获取 ====================

// 获取插件信息
function getPluginInfo() {
    try {
        const infoStr = UINPlugin.getPluginInfo();
        const info = JSON.parse(infoStr);
        alert(`插件名称: ${info.name}\n插件ID: ${info.pluginId}\n版本: ${info.versionName}`);
        logMessage(`插件信息: ${infoStr}`);
    } catch (e) {
        logMessage(`获取插件信息失败: ${e.message}`);
    }
}

// 获取设备信息
function getDeviceInfo() {
    try {
        const infoStr = UINPlugin.getDeviceInfo();
        const info = JSON.parse(infoStr);
        alert(`设备: ${info.brand} ${info.device}\nAndroid: ${info.android}\nAPI: ${info.api}`);
        logMessage(`设备信息: ${infoStr}`);
    } catch (e) {
        logMessage(`获取设备信息失败: ${e.message}`);
    }
}

// ==================== 存储功能 ====================

// 保存数据
function saveData(key, value) {
    UINPlugin.setStorage(key, value);
    logMessage(`保存数据: ${key} = ${value}`);
}

// 读取数据
function loadData(key) {
    const value = UINPlugin.getStorage(key);
    logMessage(`读取数据: ${key} = ${value}`);
    return value;
}

// ==================== 剪贴板 ====================

// 复制文本
function copyText(text) {
    UINPlugin.callHost('copy', text);
    logMessage(`复制: ${text}`);
}

// 粘贴文本
function pasteText() {
    const text = UINPlugin.paste();
    logMessage(`粘贴: ${text}`);
    return text;
}

// ==================== 系统功能 ====================

// 打开系统设置
function openSettings() {
    UINPlugin.openSettings();
}

// 打开应用设置
function openAppSettings() {
    UINPlugin.openAppSettings();
}

// 修改标题
function setTitle(title) {
    UINPlugin.setTitle(title);
}

// 震动
function vibrate(duration) {
    UINPlugin.callHost('vibrate', duration || '100');
}

// ==================== 生命周期事件 ====================

// 页面加载完成
document.addEventListener('DOMContentLoaded', () => {
    console.log('Web 插件已加载');
    logMessage('插件启动成功');
});

// 插件恢复运行
window.addEventListener('resume', () => {
    console.log('插件恢复运行');
    logMessage('插件恢复运行');
});

// 插件暂停运行
window.addEventListener('pause', () => {
    console.log('插件暂停运行');
    logMessage('插件暂停运行');
});

// 插件销毁
window.addEventListener('destroy', () => {
    console.log('插件被销毁');
    logMessage('插件被销毁');
    if (currentSensorCallback) {
        stopSensor();
    }
});
```

---

PluginInterface 接口详解

方法说明

方法 说明 必须实现 调用时机
onCreateView 创建插件视图 ✅ 是 插件首次打开时
onResume 插件恢复 ❌ 否 从其他页面返回时
onPause 插件暂停 ❌ 否 切换到其他页面时
onDestroy 插件销毁 ❌ 否 插件被关闭时
onBackPressed 返回键按下 ❌ 否 用户按下返回键时
onSaveInstanceState 保存状态 ❌ 否 系统需要保存状态时
onActivityResult Activity 结果 ❌ 否 startActivityForResult 返回时

完整实现示例

```java
package com.example;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.UIN.Tool.plugin.PluginInterface;

public class MainPlugin implements PluginInterface {

    private Context context;
    private View rootView;
    private int clickCount = 0;

    @Override
    public View onCreateView(Context context, ViewGroup container, Bundle savedInstanceState) {
        this.context = context;
        Context appContext = context.getApplicationContext();

        LinearLayout layout = new LinearLayout(appContext);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 50, 50, 50);

        TextView title = new TextView(appContext);
        title.setText("我的插件");
        title.setTextSize(24);
        title.setTextColor(0xFF37474F);
        title.setPadding(0, 0, 0, 20);

        TextView counter = new TextView(appContext);
        counter.setText("点击次数: 0");
        counter.setTextSize(16);
        counter.setTextColor(0xFF666666);
        counter.setPadding(0, 0, 0, 20);

        Button button = new Button(appContext);
        button.setText("点击我");
        button.setBackgroundColor(0xFF37474F);
        button.setTextColor(0xFFFFFFFF);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clickCount++;
                counter.setText("点击次数: " + clickCount);
                Toast.makeText(context, "点击了 " + clickCount + " 次", Toast.LENGTH_SHORT).show();
            }
        });

        layout.addView(title);
        layout.addView(counter);
        layout.addView(button);

        rootView = layout;
        
        // 恢复保存的状态
        if (savedInstanceState != null) {
            clickCount = savedInstanceState.getInt("clickCount", 0);
            counter.setText("点击次数: " + clickCount);
        }
        
        return rootView;
    }

    @Override
    public void onResume() {
        Toast.makeText(context, "插件恢复", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onPause() {
        Toast.makeText(context, "插件暂停", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onDestroy() {
        if (rootView != null) {
            // 清理资源
        }
        Toast.makeText(context, "插件销毁", Toast.LENGTH_SHORT).show();
    }

    @Override
    public boolean onBackPressed() {
        if (clickCount > 0) {
            Toast.makeText(context, "再按一次退出", Toast.LENGTH_SHORT).show();
            clickCount = 0;
            return true;
        }
        return false;
    }

    @Override
    public Bundle onSaveInstanceState() {
        Bundle bundle = new Bundle();
        bundle.putInt("clickCount", clickCount);
        return bundle;
    }
    
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        // 处理 Activity 返回结果
        Toast.makeText(context, "Activity 返回: " + requestCode, Toast.LENGTH_SHORT).show();
    }
}
```

---

JavaScript API 完整参考

callHost - 调用宿主方法

```javascript
// 显示 Toast 提示
UINPlugin.callHost('toast', '消息内容');

// 关闭插件
UINPlugin.callHost('finish', '');

// 输出日志到宿主 Log
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
const postCallbackId = 'post_' + Date.now();
const postData = JSON.stringify({key: 'value'});
window.UINPluginCallbacks[postCallbackId] = function(response) {
    const data = JSON.parse(response);
    if (data.success) {
        console.log('POST 成功:', data.data);
    }
};
UINPlugin.httpPost('https://api.example.com/submit', postData, postCallbackId);
```

startSensor / stopSensor - 传感器

```javascript
// 启动传感器
const sensorCallbackId = 'sensor_' + Date.now();
window.UINPluginCallbacks[sensorCallbackId] = function(data) {
    const sensorData = JSON.parse(data);
    if (sensorData.success) {
        console.log('传感器数据:', sensorData);
        // 加速度计: sensorData.x, sensorData.y, sensorData.z
        // 陀螺仪: sensorData.x, sensorData.y, sensorData.z
        // 光线: sensorData.lux
        // 接近: sensorData.distance
    }
};
UINPlugin.startSensor('accelerometer', sensorCallbackId);

// 停止传感器
UINPlugin.stopSensor();

// 获取可用传感器
const sensors = JSON.parse(UINPlugin.getAvailableSensors());
console.log('可用传感器:', sensors);
```

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
console.log('文件列表:', files);

// 获取插件目录
const pluginDir = UINPlugin.getPluginDir();
console.log('插件目录:', pluginDir);
```

getPluginInfo - 获取插件信息

```javascript
const infoStr = UINPlugin.getPluginInfo();
const info = JSON.parse(infoStr);
console.log(info.name);        // 插件名称
console.log(info.pluginId);    // 插件ID
console.log(info.versionName); // 版本名
console.log(info.author);      // 作者
console.log(info.description); // 描述
```

getDeviceInfo - 获取设备信息

```javascript
const infoStr = UINPlugin.getDeviceInfo();
const info = JSON.parse(infoStr);
console.log(info.brand);        // 品牌
console.log(info.device);       // 型号
console.log(info.android);      // Android 版本
console.log(info.api);          // API 级别
console.log(info.screenWidth);  // 屏幕宽度
console.log(info.screenHeight); // 屏幕高度
```

getNetworkInfo - 获取网络信息

```javascript
const infoStr = UINPlugin.getNetworkInfo();
const info = JSON.parse(infoStr);
console.log(info.connected);    // 是否连接
console.log(info.type);         // 网络类型
console.log(info.isWifi);       // 是否 WiFi
console.log(info.isMobile);     // 是否移动网络
```

getCurrentTime - 获取当前时间

```javascript
const time = UINPlugin.getCurrentTime();
console.log(time); // 2026-06-07 12:00:00
```

getAppVersion - 获取宿主版本

```javascript
const version = UINPlugin.getAppVersion();
console.log('宿主版本:', version);
```

setTitle - 设置标题

```javascript
UINPlugin.setTitle('新标题');
```

setFullscreen - 全屏模式

```javascript
// 进入全屏
UINPlugin.setFullscreen(true);

// 退出全屏
UINPlugin.setFullscreen(false);
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

粘贴 API

```javascript
// 从剪贴板粘贴
const text = UINPlugin.paste();
console.log(text);
```

系统 API

```javascript
// 打开系统设置
UINPlugin.openSettings();

// 打开应用设置
UINPlugin.openAppSettings();
```

完整使用示例

```javascript
// 创建插件对象
const MyPlugin = {
    // 显示消息
    showMessage(msg) {
        UINPlugin.callHost('toast', msg);
    },
    
    // 关闭
    close() {
        UINPlugin.callHost('finish', '');
    },
    
    // 获取插件信息
    getInfo() {
        return JSON.parse(UINPlugin.getPluginInfo());
    },
    
    // 获取设备信息
    getDevice() {
        return JSON.parse(UINPlugin.getDeviceInfo());
    },
    
    // 保存设置
    saveSetting(key, value) {
        UINPlugin.setStorage(key, value);
        this.showMessage(`已保存: ${key}`);
    },
    
    // 加载设置
    loadSetting(key) {
        return UINPlugin.getStorage(key);
    },
    
    // 复制文本
    copy(text) {
        UINPlugin.callHost('copy', text);
        this.showMessage('已复制');
    },
    
    // 粘贴文本
    paste() {
        return UINPlugin.paste();
    },
    
    // HTTP GET 请求
    get(url, callback) {
        const callbackId = 'get_' + Date.now();
        window.UINPluginCallbacks = window.UINPluginCallbacks || {};
        window.UINPluginCallbacks[callbackId] = function(response) {
            const data = JSON.parse(response);
            callback(data);
            delete window.UINPluginCallbacks[callbackId];
        };
        UINPlugin.httpGet(url, callbackId);
    },
    
    // 启动传感器
    startSensor(type, onData) {
        const callbackId = 'sensor_' + Date.now();
        window.UINPluginCallbacks = window.UINPluginCallbacks || {};
        window.UINPluginCallbacks[callbackId] = function(data) {
            const sensorData = JSON.parse(data);
            onData(sensorData);
        };
        UINPlugin.startSensor(type, callbackId);
        return callbackId;
    },
    
    // 停止传感器
    stopSensor(callbackId) {
        if (callbackId) {
            delete window.UINPluginCallbacks[callbackId];
        }
        UINPlugin.stopSensor();
    },
    
    // 写入文件
    writeFile(name, content) {
        return UINPlugin.writeFile(name, content);
    },
    
    // 读取文件
    readFile(name) {
        return UINPlugin.readFile(name);
    },
    
    // 列出文件
    listFiles() {
        return UINPlugin.listFiles('');
    }
};

// 页面加载完成
document.addEventListener('DOMContentLoaded', () => {
    console.log('插件已启动');
    MyPlugin.showMessage('插件加载成功');
    
    // 加载保存的设置
    const theme = MyPlugin.loadSetting('theme');
    if (theme) {
        document.body.className = theme;
    }
});

// 生命周期事件
window.addEventListener('resume', () => {
    console.log('插件恢复');
});

window.addEventListener('pause', () => {
    console.log('插件暂停');
});

window.addEventListener('destroy', () => {
    console.log('插件销毁');
});
```

---

打包与导入

打包方式

方式一：使用 IDE 打包

1. 在「开发」页面点击「生成项目文件」
2. 系统自动生成 TPK 文件到工作目录
3. 工作目录默认：/storage/emulated/0/UIN_Tool/

方式二：手动打包

1. 将插件文件整理到文件夹中
2. 确保有 plugin.json 和 web/index.html（Web插件）或 plugin.dex（原生插件）
3. 压缩为 ZIP 格式
4. 重命名为 .tpk 扩展名

文件结构检查

原生插件结构

```
plugin.tpk
├── plugin.json      # 必需
├── icon.png         # 可选，建议提供
├── plugin.dex       # 必需
└── src/             # 可选，源码
    └── com/example/MainPlugin.java
```

Web插件结构

```
plugin.tpk
├── plugin.json      # 必需
├── icon.png         # 可选，建议提供
└── web/             # 必需
    ├── index.html   # 必需
    ├── style.css    # 可选
    └── script.js    # 可选
```

plugin.json 完整字段

字段 类型 说明
pluginId string 唯一标识符，必填
version int 版本号，必填
versionName string 显示版本名，必填
minHostVersion int 最低宿主版本，必填
name string 插件名称，必填
author string 作者，可选
description string 描述，可选
icon string 图标文件名，可选
mainClass string 主类名，原生插件必填
updateUrl string 更新检查 URL，可选
apiLevel int 最低 API 级别，默认 21
category string 分类，默认"未分类"
uiType string UI类型: native/web，默认 native
entry string 入口文件，Web插件必填

导入步骤

1. 打开 UIN Tool App
2. 点击底部「管理」标签
3. 点击「插件管理」
4. 点击「导入插件」按钮
5. 选择 .tpk 文件
6. 等待导入完成
7. 在「工具」标签中查看插件

批量导入

1. 在插件管理页面点击「批量导入」
2. 选择多个 .tpk 文件
3. 系统自动依次导入

插件集导入

1. 将多个插件打包成一个 ZIP 文件
2. 在插件管理页面点击「插件集」
3. 选择 ZIP 文件
4. 系统自动解压并导入所有插件

---

发布到插件仓库

🆕 v2.0.0 新增功能：UIN Tool 现在支持从 GitHub 官方仓库直接安装插件。开发者可以将自己的插件发布到官方仓库，让用户更方便地发现和安装。

仓库要求

要将插件发布到官方仓库，需要满足以下要求：

要求 说明
仓库名称 必须为插件 ID（如 com.example.myplugin）
仓库描述 必须为插件名称
Release 标签 格式：{版本代码}-{版本名称}（如 1-1.0.0）
Release 资产 必须包含 .tpk 文件
仓库可见性 必须是公开仓库

发布步骤

1. 创建 GitHub 仓库

1. 登录 GitHub
2. 在 UIN-Tool-Plugins 组织中创建新仓库（或在自己账号下创建后申请收录）
3. 仓库名称设置为插件 ID（如 com.example.myplugin）
4. 仓库描述设置为插件名称
5. 选择公开仓库

2. 上传插件文件

```bash
# 克隆仓库
git clone https://github.com/UIN-Tool-Plugins/your.plugin.id
cd your.plugin.id

# 复制 TPK 文件
cp your-plugin.tpk .

# 提交并推送
git add your-plugin.tpk
git commit -m "Add plugin v1.0.0"
git push
```

3. 创建 Release

1. 在 GitHub 仓库页面点击 "Releases"
2. 点击 "Create a new release"
3. 填写信息：
   · Tag version: 1-1.0.0（格式：版本代码-版本名称）
   · Release title: 版本 1.0.0
   · Description: 更新日志
4. 上传 .tpk 文件到 Assets
5. 点击 "Publish release"

4. 自动验证

发布后，系统会自动：

· ✅ 验证 TPK 文件结构
· ✅ 检查 plugin.json 格式
· ✅ 验证命名规范
· ✅ 在 Release 页面评论验证结果

验证通过后

插件会在 24 小时内出现在 UIN Tool 的「仓库」页面中，用户可以直接浏览和安装。

更新插件

1. 修改插件代码
2. 更新 plugin.json 中的版本号
3. 重新打包为 .tpk 文件
4. 在 GitHub 创建新的 Release
5. Tag 格式：{新版本代码}-{新版本名称}

---

调试技巧

1. 使用日志输出

原生插件

```java
import com.UIN.Tool.utils.LogUtils;

LogUtils.i("TAG", "信息");
LogUtils.d("TAG", "调试");
LogUtils.e("TAG", "错误", exception);
LogUtils.success("TAG", "成功");
```

Web插件

```javascript
UINPlugin.callHost('log', '调试信息');
console.log('控制台输出');
```

2. 查看运行日志

· 在「管理」标签点击「运行日志」
· 可查看、导出、清空日志
· 崩溃日志会自动保存

3. WebView 远程调试

1. 在 Chrome 浏览器打开 chrome://inspect
2. 确保 WebView 调试已启用
3. 可以看到 WebView 页面并调试

4. 使用 Toast 快速调试

原生插件

```java
Toast.makeText(context, "调试信息", Toast.LENGTH_SHORT).show();
```

Web插件

```javascript
UINPlugin.callHost('toast', '调试信息');
```

5. 文件系统调试

· 插件工作目录：/storage/emulated/0/UIN_Tool/{pluginId}/
· 日志目录：/storage/emulated/0/UIN_Tool/logs/
· 可通过文件管理器查看文件

6. 传感器调试

```javascript
// 检查可用传感器
const sensors = JSON.parse(UINPlugin.getAvailableSensors());
console.log('可用传感器:', sensors);

// 启动传感器后查看回调数据
UINPlugin.startSensor('accelerometer', callbackId);
```

7. 网络请求调试

```javascript
// 在回调中打印详细数据
window.UINPluginCallbacks[callbackId] = function(response) {
    console.log('原始响应:', response);
    const data = JSON.parse(response);
    console.log('解析后数据:', data);
};
```

---

常见问题

Q1: 插件导入失败？

可能原因：

· 文件不是有效的 .tpk 格式
· 缺少 plugin.json 文件
· JSON 格式错误
· 原生插件缺少 plugin.dex
· Web插件缺少 web/index.html
· 签名验证失败

解决方案：

· 确保使用正确的打包方式
· 检查 plugin.json 格式是否正确
· 重新打包后再试
· 在开发者选项中可忽略签名验证

Q2: Web 插件修改后不生效？

Web 插件修改 HTML/CSS/JS 后，只需关闭并重新打开插件即可，无需重新编译或重新导入。

Q3: 插件无法调用宿主权限？

在「管理」→「权限管理」→「插件权限」中为插件授予所需权限。

Q4: 如何调试插件？

1. 使用 LogUtils 输出日志
2. 在「管理」→「运行日志」中查看
3. 崩溃日志会自动保存
4. Web插件可用 Chrome DevTools 调试

Q5: 插件支持哪些 Android 版本？

最低支持 Android 5.0 (API 21)，推荐使用 Android 8.0+。

Q6: 如何更新插件？

重新导入同 ID 的插件即可覆盖更新。

Q7: Web 插件如何传递复杂数据？

使用 JSON 格式：

```javascript
// 发送
UINPlugin.callPlugin('processData', JSON.stringify({
    type: 'user',
    data: { name: '张三', age: 18 }
}));
```

Q8: 插件图标显示不出来？

· 确保图标文件名为 icon.png
· 图标放在插件根目录
· 推荐使用 128x128 像素的 PNG 图片

Q9: 如何让插件开机自启？

插件本身不支持开机自启，但可以通过无障碍服务实现自动化。

Q10: 如何分享插件？

1. 在插件管理页面选择要导出的插件
2. 点击「导出」
3. 选择导出位置
4. 分享生成的 ZIP 文件

Q11: 如何管理插件分类？

在插件详情弹窗中点击「修改分类」：

· 选择已有分类
· 选择「新建分类」创建新分类
· 在分类管理对话框中可长按删除自定义分类

Q12: 小部件不显示插件？

· 检查小部件是否已配置插件
· 尝试重新配置小部件
· 检查是否有已安装插件
· 刷新小部件列表

Q13: 如何将插件发布到官方仓库？

详见 发布到插件仓库 章节。

Q14: Web 插件网络请求失败？

· 检查 URL 是否正确
· 确认网络连接正常
· 检查是否需要配置网络安全策略
· 查看日志中的错误信息

Q15: 传感器没有数据？

· 检查设备是否支持该传感器
· 使用 getAvailableSensors() 检查可用传感器
· 确认已授予传感器权限
· 检查传感器是否被其他应用占用

Q16: 文件读写失败？

· 确认文件名不包含非法字符
· 检查文件路径是否在插件目录内
· 确认有存储权限
· 检查磁盘空间是否充足

---

最佳实践

1. 命名规范

· 插件ID：使用域名倒序，如 com.example.myplugin
· 包名：与插件ID一致或相关
· 类名：使用 PascalCase，如 MainPlugin

2. 性能优化

· 避免在 onCreateView 中执行耗时操作
· 使用异步任务处理 I/O 操作
· Web插件注意优化图片大小和 CSS 选择器
· 传感器使用完毕后及时停止

3. 内存管理

· 在 onDestroy 中释放资源
· 避免内存泄漏（如静态变量持有 Context）
· Web插件注意清理 WebView
· 及时注销传感器监听器

4. 用户体验

· 提供插件图标
· 添加插件描述
· 使用合适的分类
· 响应返回键确认退出
· 显示加载状态和错误提示

5. 安全性

· 不要存储敏感信息明文
· 验证输入数据
· 使用 HTTPS 进行网络请求
· 验证文件路径防止目录遍历

6. 版本管理

· 使用语义化版本号
· 保持 minHostVersion 合理
· 发布时使用正确的 Release Tag 格式

7. 代码组织

```
src/
└── com/example/
    ├── MainPlugin.java      # 主入口
    ├── utils/               # 工具类
    │   └── Helper.java
    └── ui/                  # UI组件
        └── CustomView.java
```

8. 错误处理

```java
try {
    // 可能出错的代码
} catch (Exception e) {
    LogUtils.e("Plugin", "错误", e);
    // 显示友好提示
}
```

9. Web 插件最佳实践

· 使用事件监听处理生命周期
· 及时清理传感器和网络请求回调
· 使用 window.UINPluginCallbacks 管理异步回调
· 对大文件使用分块读写

10. 传感器使用建议

```javascript
// 检查传感器可用性
const sensors = JSON.parse(UINPlugin.getAvailableSensors());
if (!sensors.accelerometer) {
    alert('设备不支持加速度计');
    return;
}

// 启动传感器
let sensorRunning = false;
function startWithTimeout() {
    startAccelerometer();
    sensorRunning = true;
    // 30秒后自动停止
    setTimeout(() => {
        if (sensorRunning) {
            stopSensor();
            sensorRunning = false;
        }
    }, 30000);
}
```

---

技术支持

· 📧 邮箱：undefinedinvalidnull@outlook.com
· 🌐 GitHub：https://github.com/Undefined-Invalid-Null/UIN-Tool
· 📦 插件仓库：https://github.com/UIN-Tool-Plugins
· 💬 QQ群：511875883

---

更新日志

v2.6.0 (2026-06-07)

新增内容：

· Web 插件网络请求 API（httpGet/httpPost）
· Web 插件文件系统 API（writeFile/readFile/deleteFile/listFiles）
· Web 插件传感器 API（startSensor/stopSensor/getAvailableSensors）
· 完整的 JavaScript API 参考文档
· 传感器调试指南
· 网络请求调试指南
· Web 插件最佳实践

更新内容：

· 完善 PluginInterface 接口说明（新增 onActivityResult）
· 更新打包和导入说明
· 新增常见问题解答

v2.0.0 (2026-06-07)

新增内容：

· 发布到插件仓库完整指南
· GitHub Release 格式说明
· 仓库命名和描述规范
· 自动验证流程说明

v1.1.0 (2026-06-07)

新增内容：

· 分类管理 API 说明
· 长按菜单功能说明
· 1x1 小部件开发说明
· 更新日志查看功能

v1.0.0 (2064-06-06)

首次发布：

· 初始版本发布
· 支持原生插件和 Web插件
· 提供完整的 JavaScript API
· 支持插件权限管理
· 支持备份恢复功能

---

文档版本: 2.6.0
最后更新: 2026-06-07
