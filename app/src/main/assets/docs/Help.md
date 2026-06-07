# UIN Tool 使用帮助

## 目录

- [应用概述](#应用概述)
- [界面导航](#界面导航)
- [插件仓库](#插件仓库)
- [插件管理](#插件管理)
- [插件开发](#插件开发)
- [插件运行](#插件运行)
- [权限管理](#权限管理)
- [备份恢复](#备份恢复)
- [日志查看](#日志查看)
- [桌面小部件](#桌面小部件)
- [UI个性化](#ui个性化)
- [常见问题](#常见问题)
- [联系支持](#联系支持)

---

## 应用概述

UIN Tool 是一个轻量级的插件化框架，允许您动态加载和运行第三方插件。插件可以是：

| 插件类型 | 说明 | 适用场景 |
|----------|------|----------|
| **原生插件** | 使用 Java 代码开发，性能最优 | 需要高性能、复杂交互的功能 |
| **Web插件** | 使用 HTML/CSS/JS 开发，快速迭代 | 界面丰富、更新频繁的功能 |

### 主要功能

| 功能 | 说明 |
|------|------|
| 📦 插件仓库 | 从 GitHub 官方仓库浏览、搜索和安装插件 |
| 🔌 插件管理 | 导入、导出、卸载插件，支持单文件、批量、插件集导入 |
| 🛠️ 开发工具 | 创建插件向导、导出开发模板、查看开发文档 |
| 🔐 权限管理 | 统一管理应用权限和插件独立权限 |
| 💾 备份恢复 | 备份/恢复所有插件和配置 |
| 📋 运行日志 | 查看、导出、清空运行日志和崩溃报告 |
| 🖥️ 桌面小部件 | 在桌面添加快速启动小部件（列表小部件、1x1快捷方式） |
| 🎨 UI个性化 | 自定义主题颜色和圆角大小 |
| 📚 内置文档 | 开发文档、使用帮助、更新日志、关于信息 |
| 🌐 网络请求 | Web插件支持 HTTP GET/POST 请求 |
| 📁 文件系统 | Web插件支持文件读写操作 |
| 📡 传感器 | Web插件支持加速度计、陀螺仪等传感器 |

---

## 界面导航

### 底部导航栏

```

┌─────────────────────────────────────────────────────────────┐
│                        导航栏                                │
├─────────────┬─────────────┬─────────────┬─────────────────┤
│    开发      │    工具      │    仓库      │      管理       │
│   (Dev)     │   (Tools)   │   (Repo)    │    (Manage)     │
└─────────────┴─────────────┴─────────────┴─────────────────┘

```

#### 开发页面 (Dev)

| 功能 | 说明 |
|------|------|
| 创建新插件 | 打开插件创建向导，支持原生和Web插件 |
| 导出模板 | 导出插件开发模板和文档到工作目录 |
| 查看文档 | 查看开发文档、使用帮助、更新日志、关于信息 |

#### 工具页面 (Tools)

| 功能 | 说明 |
|------|------|
| 分类浏览 | 按分类查看插件（全部/未分类/自定义分类） |
| 搜索插件 | 按名称、ID、作者、描述搜索 |
| 视图切换 | 列表视图、分类视图切换 |
| 运行插件 | 点击插件图标运行 |
| 长按菜单 | 长按显示插件详情（运行、快捷方式、修改分类、卸载） |

#### 仓库页面 (Repo) - 新增于 v2.0.0

| 功能 | 说明 |
|------|------|
| 浏览插件 | 从 GitHub 官方仓库浏览所有可用插件 |
| 搜索插件 | 支持按名称、ID、描述、作者搜索 |
| 安装插件 | 一键下载并安装插件，显示实时进度 |
| 打开插件 | 已安装插件可直接打开 |
| 插件详情 | 点击卡片查看版本、大小、更新日志等信息 |
| 镜像加速 | 自动选择最快镜像加速下载 |
| 下拉刷新 | 刷新插件列表 |

#### 管理页面 (Manage)

| 功能 | 说明 |
|------|------|
| 插件管理 | 导入、导出、卸载插件 |
| 权限管理 | 管理应用权限和插件权限 |
| 查看文档 | 使用帮助、开发文档、更新日志、关于 |
| 运行日志 | 查看和导出日志 |
| 备份恢复 | 备份/恢复插件和配置 |
| UI个性化 | 自定义主题颜色和圆角 |
| 开发者选项 | 签名验证开关等高级设置 |

---

## 插件仓库

插件仓库是 v2.0.0 新增的核心功能，允许您直接从 GitHub 官方仓库浏览和安装插件。

### 浏览插件

1. 点击底部「仓库」标签
2. 系统自动加载 GitHub 上的所有可用插件
3. 每个插件卡片显示：
   - 插件名称
   - 插件 ID
   - 版本号
   - 文件大小
   - 更新日期
4. 点击卡片可查看详细信息

### 搜索插件

1. 点击仓库页面的搜索图标 🔍
2. 输入关键词（支持插件名称、ID、描述、作者）
3. 实时显示搜索结果
4. 点击清除按钮清空搜索

### 安装插件

1. 在仓库列表中找到要安装的插件
2. 点击「安装」按钮
3. 系统自动下载并安装
4. 下载进度实时显示：
   - 下载百分比
   - 已下载大小 / 总大小
5. 安装完成后按钮变为「打开」

### 打开已安装插件

1. 已安装的插件显示「打开」按钮
2. 点击「打开」直接运行插件

### 插件详情

点击插件卡片可查看详细信息：
- 完整插件名称
- 插件 ID
- 版本信息
- 文件大小
- 更新日期
- 作者信息
- 详细描述
- 更新日志

### 镜像加速

为了提升国内用户的下载速度，插件仓库内置了多个 GitHub 镜像站：
- FastGit
- Moeyy
- ghproxy
- gitclone
- 其他备用镜像

系统会自动测试并选择最快的可用镜像。

### 下拉刷新

如果插件列表没有更新，可以：
1. 在仓库页面向下滑动
2. 松手后自动刷新
3. 系统重新从 GitHub 获取最新插件列表

---

## 插件管理

### 插件格式说明

插件文件扩展名为 `.tpk`，本质上是 ZIP 压缩包。

#### 原生插件结构
```

plugin.tpk
├── plugin.json      # 插件配置文件
├── icon.png         # 插件图标 (建议 128x128)
├── plugin.dex       # 编译后的 DEX 文件
├── src/             # Java 源码目录
└── res/             # 资源文件目录

```

#### Web 插件结构
```

plugin.tpk
├── plugin.json      # 插件配置文件
├── icon.png         # 插件图标 (建议 128x128)
└── web/             # Web 资源目录
├── index.html   # 入口页面 (必须)
├── style.css    # 样式文件 (可选)
└── script.js    # JavaScript 文件 (可选)

```

### plugin.json 配置说明

```json
{
    "pluginId": "com.example.myplugin",      // 插件唯一ID (必填)
    "version": 1,                             // 版本号 (数字)
    "versionName": "1.0.0",                  // 版本名
    "minHostVersion": 1,                      // 最低宿主版本
    "name": "我的插件",                       // 插件名称 (必填)
    "author": "开发者",                       // 作者
    "description": "插件描述",                // 描述
    "icon": "icon.png",                       // 图标文件名
    "mainClass": "com.example.MainPlugin",    // 主类 (原生插件必填)
    "apiLevel": 21,                           // API级别
    "uiType": "native",                       // UI类型: native/web
    "entry": "web/index.html"                 // 入口文件 (Web插件)
}
```

导入插件

单文件导入

1. 点击底部「管理」
2. 点击「插件管理」
3. 点击「导入插件」按钮
4. 选择 .tpk 文件
5. 等待导入完成并查看结果

批量导入

1. 点击「批量导入」按钮
2. 选择多个 .tpk 文件
3. 系统会依次导入并显示进度
4. 导入完成后显示成功/失败统计

插件集导入

1. 点击「插件集」按钮
2. 选择包含多个 .tpk 文件的 ZIP 包
3. 系统自动解压并导入所有插件
4. 适用于批量备份恢复

导出插件

1. 在插件列表中勾选要导出的插件（支持多选）
2. 点击「全选」可快速选择全部
3. 点击「导出」按钮
4. 插件被打包为 ZIP 文件保存在工作目录
5. 文件名格式：Plugins_时间戳.zip

卸载插件

单个卸载

1. 点击插件进入详情页
2. 点击「卸载」按钮
3. 确认卸载

批量卸载

1. 勾选要卸载的插件
2. 点击「卸载」按钮
3. 确认批量卸载
4. 显示卸载结果统计

插件分类管理

修改分类

1. 点击插件进入详情页
2. 点击「修改分类」
3. 选择已有分类
4. 或选择「新建分类」创建新分类
5. 确认保存

删除分类

1. 在修改分类时选择「新建分类」
2. 在分类管理对话框中长按要删除的分类
3. 确认删除
4. 该分类下的插件自动移动到"未分类"

---

插件开发

创建原生插件

1. 点击「开发」→「创建新插件」
2. 选择「原生代码 UI」
3. 填写插件信息：
   · 插件ID：唯一标识，如 com.example.myplugin
   · 插件名称：显示名称
   · 作者：开发者名称
   · 描述：功能描述
   · 版本号：数字版本
   · 版本名：显示版本
   · 主类名：完整包名+类名
4. 选择插件图标（可选）
5. 编写 Java 代码
6. 添加资源文件（可选）
7. 生成项目文件

创建 Web 插件

1. 点击「开发」→「创建新插件」
2. 选择「WebView UI」
3. 选择 Web 插件类型：
   · 完整模板：生成示例 HTML/CSS/JS
   · 空白模板：生成基础框架
   · 导入已有项目：导入 ZIP 包
   · 跳过：只创建空目录
4. 填写插件信息
5. 编写或导入 HTML/CSS/JS
6. 生成项目文件

导出开发模板

点击「导出模板」会在工作目录生成：

```
UIN_Tool/
├── native_plugin_template.tpk    # 原生插件模板
├── web_plugin_template.tpk       # Web 插件模板
└── docs/                         # 开发文档
    ├── README.md                 # 开发文档
    ├── Help.md                   # 使用帮助
    ├── About.md                  # 关于
    ├── CHANGELOG.md              # 更新日志
    └── CONTRIBUTORS.md           # 贡献者名单
```

Web 插件 JavaScript API

基础功能

```javascript
// 显示提示
UINPlugin.callHost('toast', '消息内容');

// 关闭插件
UINPlugin.callHost('finish', '');

// 输出日志
UINPlugin.callHost('log', '调试信息');

// 弹窗提示
UINPlugin.callHost('alert', '提示内容');

// 确认对话框
UINPlugin.callHost('confirm', '确认内容');

// 震动
UINPlugin.callHost('vibrate', '100');

// 复制到剪贴板
UINPlugin.callHost('copy', '文本内容');

// 打开链接
UINPlugin.callHost('openUrl', 'https://...');

// 分享
UINPlugin.callHost('share', '分享内容');
```

网络请求 (v2.6.0 新增)

```javascript
// GET 请求
const callbackId = 'get_' + Date.now();
window.UINPluginCallbacks = window.UINPluginCallbacks || {};
window.UINPluginCallbacks[callbackId] = function(response) {
    const data = JSON.parse(response);
    if (data.success) {
        console.log('请求成功:', data.data);
    }
};
UINPlugin.httpGet('https://api.example.com/data', callbackId);

// POST 请求
UINPlugin.httpPost('https://api.example.com/submit', '{"key":"value"}', callbackId);
```

文件系统 (v2.6.0 新增)

```javascript
// 写入文件
UINPlugin.writeFile('test.txt', '文件内容');

// 读取文件
const content = UINPlugin.readFile('test.txt');

// 删除文件
UINPlugin.deleteFile('test.txt');

// 列出文件
const files = UINPlugin.listFiles('');
```

传感器 (v2.6.0 新增)

```javascript
// 启动加速度计
const sensorCallbackId = 'sensor_' + Date.now();
window.UINPluginCallbacks[sensorCallbackId] = function(data) {
    const sensorData = JSON.parse(data);
    console.log('X:', sensorData.x, 'Y:', sensorData.y, 'Z:', sensorData.z);
};
UINPlugin.startSensor('accelerometer', sensorCallbackId);

// 停止传感器
UINPlugin.stopSensor();

// 获取可用传感器
const sensors = JSON.parse(UINPlugin.getAvailableSensors());
```

信息获取

```javascript
// 获取插件信息
var info = JSON.parse(UINPlugin.getPluginInfo());

// 获取设备信息
var device = JSON.parse(UINPlugin.getDeviceInfo());

// 获取网络信息
var network = JSON.parse(UINPlugin.getNetworkInfo());

// 获取当前时间
var time = UINPlugin.getCurrentTime();

// 获取宿主版本
var version = UINPlugin.getAppVersion();
```

UI 控制

```javascript
// 设置标题
UINPlugin.setTitle('新标题');

// 全屏模式
UINPlugin.setFullscreen(true);
UINPlugin.setFullscreen(false);
```

存储和系统

```javascript
// 存储数据
UINPlugin.setStorage('key', 'value');
var value = UINPlugin.getStorage('key');
UINPlugin.removeStorage('key');
UINPlugin.clearStorage();

// 获取插件目录
var dir = UINPlugin.getPluginDir();

// 打开系统设置
UINPlugin.openSettings();
UINPlugin.openAppSettings();
```

Web 插件生命周期事件

```javascript
// 插件恢复运行
window.addEventListener('resume', () => {
    console.log('插件恢复运行');
});

// 插件暂停运行
window.addEventListener('pause', () => {
    console.log('插件暂停运行');
});

// 插件销毁
window.addEventListener('destroy', () => {
    console.log('插件销毁');
    // 清理传感器等资源
    UINPlugin.stopSensor();
});
```

编译原生插件

手机端编译

1. 确保已安装 ECJ 和 D8 编译工具
2. 准备好 host-sdk.jar
3. 执行编译命令：

```bash
cd /storage/emulated/0/Works/UIN_Tool
ecj -d . -cp host-sdk.jar MainPlugin.java
d8 --lib host-sdk.jar --min-api 21 --output . *.class
mv classes.dex plugin.dex
cp plugin.dex /storage/emulated/0/UIN_Tool/com.example.nativeplugin/
```

电脑端编译

```bash
# 1. 编译 Java 文件
javac -d . -cp host-sdk.jar src/**/*.java

# 2. 打包为 jar
jar cvf plugin.jar com/

# 3. 转换为 dex
d8 --lib android.jar --min-api 21 --output . plugin.jar

# 4. 重命名
mv classes.dex plugin.dex
```

---

插件运行

运行插件

1. 点击底部「工具」
2. 浏览插件列表
3. 点击插件运行

视图模式

模式 说明
分类视图 按分类分组显示，显示插件数量，支持折叠/展开
列表视图 统一列表显示

搜索插件

1. 点击搜索图标 🔍
2. 输入关键词（支持名称、ID、作者、描述）
3. 实时筛选结果
4. 点击清除按钮清空搜索

长按菜单

在工具页面长按插件，会弹出详情对话框，包含：

· 运行：直接运行插件
· 创建快捷方式：在桌面创建快捷图标
· 修改分类：更改插件所属分类
· 卸载：卸载插件

创建桌面快捷方式

1. 长按插件或进入插件详情页
2. 点击「创建快捷方式」按钮
3. 在弹出的系统对话框中确认
4. 桌面即生成快捷图标

---

权限管理

应用权限列表

权限类别 权限项 说明
📁 存储 读取/写入存储 导入导出插件
🌐 网络 访问网络/获取状态 插件网络功能
📷 相机 相机 拍照/扫码功能
🎤 麦克风 录音 语音功能
📍 位置 精确/粗略/后台位置 定位功能
📞 电话 拨打电话/读取状态 电话功能
📨 短信 发送/读取/接收短信 短信功能
👥 联系人 读取/写入联系人 联系人功能
📅 日历 读取/写入日历 日历功能
⚙️ 系统 悬浮窗/修改设置/通知等 系统级功能
♿ 无障碍 无障碍服务 自动化操作
🔧 高级 安装未知应用/使用情况等 高级功能
🛠️ 工具 Root/Shizuku/Dhizuku 权限增强工具

权限操作

操作 说明
一键授权 批量请求所有普通权限
刷新状态 更新当前权限状态
单个授权 点击权限项单独授权

插件权限管理

1. 点击「插件权限」
2. 选择要配置的插件
3. 勾选允许的权限
4. 保存配置

💡 提示：插件权限独立于应用权限，可以精细控制每个插件的权限范围。

---

备份恢复

创建备份

1. 点击「管理」→「备份恢复」
2. 点击「备份全部」
3. 等待备份完成

备份内容：

· ✅ 所有已安装插件
· ✅ 应用配置 (SharedPreferences)
· ✅ 工作目录设置
· ✅ UI 主题配置
· ✅ 应用设置

备份文件位置：

```
/storage/emulated/0/UIN_Tool/backups/UIN_Tool_Backup_时间戳.zip
```

恢复备份

1. 选择要恢复的备份文件
2. 确认恢复操作
3. 等待恢复完成
4. 重新打开应用

⚠️ 注意：恢复操作会覆盖现有插件和配置！

管理备份文件

操作 说明
查看详情 点击备份文件查看大小和日期
恢复 点击「恢复」按钮
删除 点击「删除」按钮

---

日志查看

日志内容

日志文件包含：

· 应用启动信息（设备信息、版本）
· 插件加载记录
· 功能调用记录
· 错误和警告信息
· 崩溃报告（含堆栈信息）
· 传感器数据记录
· 网络请求记录

日志位置

```
/storage/emulated/0/UIN_Tool/logs/uin_tool_日期.log
/storage/emulated/0/UIN_Tool/logs/crash_日期.log
```

日志操作

操作 说明
刷新 重新加载最新日志
清空今日 删除今日日志文件
清空全部 删除所有历史日志文件
导出 导出日志为文本文件

崩溃自动弹窗

应用崩溃后会自动打开日志页面，显示崩溃报告，方便用户查看错误信息并反馈给开发者。

---

桌面小部件

列表小部件 (4x2)

显示3个常用插件，点击直接运行。

添加方法：

1. 长按桌面空白处
2. 选择「小部件」或「窗口小工具」
3. 找到「UIN Tool」小部件
4. 拖拽到桌面
5. 选择要显示的插件（最多3个）

使用方法：

· 点击插件按钮直接运行
· 点击刷新按钮更新列表

1x1 快捷方式小部件

绑定单个插件的桌面快捷方式。

添加方法：

1. 长按桌面空白处
2. 选择「小部件」
3. 找到「插件快捷方式」小部件
4. 拖拽到桌面
5. 选择要绑定的插件

使用方法：

· 点击图标直接运行绑定的插件

---

UI个性化

颜色配置

选项 说明
主题色 主要界面颜色
深色主题色 状态栏等深色区域
浅色主题色 按钮等浅色区域
强调色 重要元素颜色
成功色 成功提示颜色
警告色 警告提示颜色
错误色 错误提示颜色
信息色 信息提示颜色
背景色 页面背景
表面色 卡片背景
主文本色 主要文本颜色
副文本色 次要文本颜色

形状配置

选项 说明 默认值
小圆角 小元素圆角 8dp
中圆角 中等元素圆角 12dp
大圆角 大元素圆角 16dp
超大圆角 超大元素圆角 24dp
按钮圆角 按钮圆角大小 12dp
卡片圆角 卡片圆角大小 16dp
弹窗圆角 对话框圆角大小 20dp

配置操作

操作 说明
预览 实时预览效果
重置 恢复默认配置
导出 导出配置为 JSON 文件
导入 从 JSON 文件导入配置
应用 应用当前配置

---

常见问题

1. 如何获取更多插件？

· 📦 从「仓库」页面直接安装官方插件
· 🔧 自己开发插件
· 👥 社区分享

2. 插件安全吗？

· ✅ 插件导入时进行 SHA-256 签名验证
· ✅ 插件权限独立控制
· ⚠️ 建议只从可信来源获取插件

3. 工作目录在哪里？

默认路径：/storage/emulated/0/UIN_Tool/

```
UIN_Tool/
├── backups/       # 备份文件
├── logs/          # 日志文件
├── [插件ID]/      # 插件目录
└── *.tpk          # 导入的插件文件
```

4. 如何修改工作目录？

当前版本不支持修改，使用默认路径。

5. 插件占用多少空间？

类型 大小
简单插件 50-200 KB
复杂插件 1-5 MB
Web插件 主要取决于资源文件

6. 支持在线更新吗？

· v2.0.0 新增：插件仓库支持浏览和下载最新版本
· 插件自动更新功能开发中

7. 如何反馈问题？

1. 导出运行日志
2. 截图错误信息
3. 发送到开发者邮箱或 GitHub Issues

8. 应用闪退怎么办？

1. 重新打开应用（会自动显示崩溃日志）
2. 查看运行日志分析原因
3. 清除应用数据
4. 重装应用

9. Web 插件不显示？

检查项：

· web/index.html 是否存在
· HTML 是否有语法错误
· JavaScript 是否有报错
· 查看运行日志

10. 如何更新 UIN Tool 本身？

1. 下载新版 APK
2. 直接安装覆盖（数据保留）

11. 插件导入失败？

可能原因：

· 签名验证失败
· plugin.json 格式错误
· 缺少必要文件
· 存储空间不足

12. 小部件不显示？

· 检查小部件是否已添加
· 尝试重新添加
· 检查是否有已安装插件
· 刷新小部件列表

13. 如何查看版本更新内容？

点击「管理」→「查看文档」→「更新日志」

14. 如何管理插件分类？

在插件详情弹窗中点击「修改分类」：

· 选择已有分类
· 选择「新建分类」创建新分类
· 在分类管理对话框中可长按删除自定义分类

15. 仓库页面加载失败怎么办？

· 检查网络连接
· 下拉刷新重试
· 检查 GitHub 是否可访问
· 镜像站会自动切换

16. 下载插件速度慢怎么办？

· 仓库页面会自动选择最快的镜像站
· 可以尝试切换网络（WiFi/移动数据）
· 稍后重试

17. Web 插件网络请求失败？

· 检查 URL 是否正确
· 确认网络连接正常
· 查看日志中的错误信息

18. 传感器没有数据？

· 检查设备是否支持该传感器
· 确认已授予传感器权限
· 查看日志中的传感器状态

19. 文件读写失败？

· 确认文件名不包含非法字符
· 检查存储权限
· 确认磁盘空间充足

---

快捷键

操作 方式
返回 物理/虚拟返回键
搜索 点击搜索图标
刷新 下拉刷新
全选 点击全选按钮

---

联系支持

渠道 联系方式
📧 邮箱 undefinedinvalidnull@outlook.com
🌐 Github https://github.com/Undefined-Invalid-Null/UIN-Tool
📦 插件仓库 https://github.com/UIN-Tool-Plugins
💬 QQ群 511875883

---

版本历史

版本 更新内容
2.6.0 新增 Web 插件网络请求 API、文件系统 API、传感器 API；完善搜索功能；优化插件加载稳定性
2.0.0 新增插件仓库、GitHub 集成、镜像加速、下载进度条、底部导航栏新增仓库页面
1.1.0 新增分类管理、长按菜单、1x1小部件、更新日志查看、错误处理增强
1.0.0 初始版本，基础功能

---

最后更新： 2026年6月7日

文档版本： 2.6.0

© 2026 UIN Team. All Rights Reserved.
