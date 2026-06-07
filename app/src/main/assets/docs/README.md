# UIN Tool 开发文档

## 目录

- [快速开始](#快速开始)
- [插件类型](#插件类型)
- [原生插件开发](#原生插件开发)
- [Web插件开发](#web插件开发)
- [PluginInterface 接口详解](#plugininterface-接口详解)
- [JavaScript API 完整参考](#javascript-api-完整参考)
- [打包与导入](#打包与导入)
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

### 第四步：生成并导入

1. 点击「生成项目文件」按钮
2. 系统会在工作目录生成项目文件和 TPK 包
3. 点击底部「管理」标签
4. 点击「插件管理」
5. 点击「导入插件」按钮
6. 选择生成的 `.tpk` 文件
7. 等待导入完成，即可在「工具」标签中看到插件

---

## 插件类型

### 对比表格

| 特性 | 原生插件 | Web插件 |
|------|----------|---------|
| UI开发方式 | Java 代码动态创建 | HTML/CSS/JS |
| 开发效率 | 中等 | 高 |
| 运行性能 | 高 | 中等 |
| 热更新 | 需重新编译 DEX | 无需编译，直接修改文件 |
| 学习成本 | 需懂 Android 开发 | 懂前端即可 |
| 调试难度 | 中等 | 低（浏览器 DevTools） |
| 适合场景 | 复杂交互、高性能要求 | 简单界面、快速迭代、动态内容 |

### 如何选择

- **选择原生插件**：需要访问系统 API、复杂动画、高性能计算、自定义 View
- **选择 Web插件**：快速原型、界面频繁变动、已有 Web 项目、希望前端开发者参与

---

## 原生插件开发

### 基本结构

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

        // 创建根布局
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 50, 50, 50);

        // 标题
        TextView title = new TextView(context);
        title.setText("我的插件");
        title.setTextSize(24);
        title.setPadding(0, 0, 0, 20);

        // 按钮
        Button button = new Button(context);
        button.setText("点击我");
        button.setOnClickListener(v -> {
            Toast.makeText(context, "插件运行成功！", Toast.LENGTH_SHORT).show();
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
LinearLayout layout = new LinearLayout(context);
layout.setOrientation(LinearLayout.VERTICAL);
layout.setLayoutParams(new ViewGroup.LayoutParams(
    ViewGroup.LayoutParams.MATCH_PARENT,
    ViewGroup.LayoutParams.WRAP_CONTENT
));

TextView textView = new TextView(context);
textView.setText("标题");
layout.addView(textView);

Button button = new Button(context);
button.setText("按钮");
layout.addView(button);
```

线性布局（水平）

```java
LinearLayout rowLayout = new LinearLayout(context);
rowLayout.setOrientation(LinearLayout.HORIZONTAL);
rowLayout.setGravity(Gravity.CENTER_VERTICAL);

TextView label = new TextView(context);
label.setText("标签：");

EditText input = new EditText(context);
input.setHint("请输入内容");
input.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

rowLayout.addView(label);
rowLayout.addView(input);
```

相对布局

```java
RelativeLayout layout = new RelativeLayout(context);

TextView centerText = new TextView(context);
centerText.setText("居中显示");
RelativeLayout.LayoutParams centerParams = new RelativeLayout.LayoutParams(
    RelativeLayout.LayoutParams.WRAP_CONTENT,
    RelativeLayout.LayoutParams.WRAP_CONTENT
);
centerParams.addRule(RelativeLayout.CENTER_IN_PARENT);
centerText.setLayoutParams(centerParams);

Button bottomButton = new Button(context);
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
ListView listView = new ListView(context);
String[] items = {"选项1", "选项2", "选项3"};
ArrayAdapter<String> adapter = new ArrayAdapter<>(context, 
    android.R.layout.simple_list_item_1, items);
listView.setAdapter(adapter);

listView.setOnItemClickListener((parent, view, position, id) -> {
    Toast.makeText(context, "点击了: " + items[position], Toast.LENGTH_SHORT).show();
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

// 使用 PluginContext 获取资源
PluginContext pluginContext = new PluginContext(context, pluginPath);
// 可以访问插件目录下的 res 文件夹
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
                <h3>信息获取</h3>
                <button onclick="getPluginInfo()" class="btn-info">获取插件信息</button>
                <button onclick="getDeviceInfo()" class="btn-info">获取设备信息</button>
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

完整实现示例

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
    private int clickCount = 0;

    @Override
    public View onCreateView(Context context, ViewGroup container, Bundle savedInstanceState) {
        this.context = context;

        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 50, 50, 50);

        TextView title = new TextView(context);
        title.setText("我的插件");
        title.setTextSize(24);
        title.setPadding(0, 0, 0, 20);

        TextView counter = new TextView(context);
        counter.setText("点击次数: 0");
        counter.setTextSize(16);
        counter.setPadding(0, 0, 0, 20);

        Button button = new Button(context);
        button.setText("点击我");
        button.setOnClickListener(v -> {
            clickCount++;
            counter.setText("点击次数: " + clickCount);
            Toast.makeText(context, "点击了 " + clickCount + " 次", Toast.LENGTH_SHORT).show();
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
        // 插件恢复，可以刷新数据
        Toast.makeText(context, "插件恢复", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onPause() {
        // 插件暂停，保存当前状态
        Toast.makeText(context, "插件暂停", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onDestroy() {
        // 清理资源
        Toast.makeText(context, "插件销毁", Toast.LENGTH_SHORT).show();
    }

    @Override
    public boolean onBackPressed() {
        // 返回 true 表示消费了返回事件，插件不会关闭
        // 返回 false 表示让系统处理，默认会关闭插件
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

setTitle - 设置标题

```javascript
UINPlugin.setTitle('新标题');
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

```
# 原生插件
plugin.tpk
├── plugin.json      # 必需
├── icon.png         # 可选，建议提供
├── plugin.dex       # 必需
└── src/             # 可选，源码
    └── com/example/MainPlugin.java

# Web插件
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

调试技巧

1. 使用日志输出

```java
// 原生插件
import com.UIN.Tool.utils.LogUtils;

LogUtils.i("TAG", "信息");
LogUtils.d("TAG", "调试");
LogUtils.e("TAG", "错误", exception);
LogUtils.success("TAG", "成功");
```

```javascript
// Web插件
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

```java
Toast.makeText(context, "调试信息", Toast.LENGTH_SHORT).show();
```

```javascript
UINPlugin.callHost('toast', '调试信息');
```

5. 文件系统调试

· 插件工作目录：/storage/emulated/0/UIN_Tool/{pluginId}/
· 日志目录：/storage/emulated/0/UIN_Tool/logs/
· 可通过文件管理器查看文件

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

在「管理」->「权限管理」->「插件权限」中为插件授予所需权限。

Q4: 如何调试插件？

1. 使用 LogUtils 输出日志
2. 在「管理」->「运行日志」中查看
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

```java
// Java 端接收
public void onJsCall(String method, String params) {
    JSONObject json = new JSONObject(params);
    String type = json.getString("type");
    // 处理数据
}
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

3. 内存管理

· 在 onDestroy 中释放资源
· 避免内存泄漏（如静态变量持有 Context）
· Web插件注意清理 WebView

4. 用户体验

· 提供插件图标
· 添加插件描述
· 使用合适的分类
· 响应返回键确认退出

5. 安全性

· 不要存储敏感信息明文
· 验证输入数据
· 使用 HTTPS 进行网络请求

6. 版本管理

· 使用语义化版本号
· 保持 minHostVersion 合理
· 提供更新 URL 支持自动更新

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

---

技术支持

· 📧 邮箱：undefinedinvalidnull@outlook.com
· 🌐 GitHub：https://github.com/Undefined-Invalid-Null/UIN-Tool
· 💬 QQ群：511875883

---

更新日志

v1.1.0 (2026-06-07)

新增内容：

· 分类管理 API 说明
· 长按菜单功能说明
· 1x1 小部件开发说明
· 更新日志查看功能

v1.0.0 (2024-06-01)

· 初始版本发布
· 支持原生插件和 Web插件
· 提供完整的 JavaScript API
· 支持插件权限管理
· 支持备份恢复功能

---

文档版本: 1.1.0
最后更新: 2026-06-07
