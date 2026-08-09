# UIN Tool

![Version](https://img.shields.io/badge/version-5.3.0-blue)
![Build](https://img.shields.io/badge/build-19-green)
![Android](https://img.shields.io/badge/Android-6.0%2B-brightgreen)
![License](https://img.shields.io/badge/license-MIT-orange)
![Kotlin](https://img.shields.io/badge/Kotlin-2.1.0-purple)
![Compose](https://img.shields.io/badge/Jetpack%20Compose-2024.09.00-blue)

## 应用简介

UIN Tool 是一个基于 Kotlin + Jetpack Compose 重构的 Android 插件化框架应用，允许用户动态加载和运行第三方插件。无论是原生 Java 插件还是 Web 技术栈（HTML/CSS/JS）的插件，都能在 UIN Tool 中无缝运行。它提供了一个完整的插件生态系统，包括插件开发、管理、运行、权限控制以及 **Termux 后端集成**等功能。

### 核心理念

- **开放**：任何人都可以开发插件，支持原生 Java 和 Web 技术栈
- **安全**：插件权限授权，支持签名验证，防止恶意插件
- **高效**：原生性能，Web 插件支持热更新，无需重新编译
- **易用**：可视化开发向导，无需复杂配置即可创建插件
- **灵活**：支持网格/列表视图切换，支持分类管理
- **现代化**：基于 Jetpack Compose 构建，Material 3 设计语言
- **强大**：内置 Termux 终端环境，支持 Python/Node.js/PHP 后端
- **持久化**：插件数据独立存储，更新时自动保留用户数据

---

## 终端功能（基于 Termux）

UIN Tool **内置完整的终端环境**，核心引擎基于 [Termux](https://github.com/termux/termux-app) 改编，为用户提供强大的 Linux 命令行体验。

### 终端特性

| 特性 | 说明 |
|------|------|
| **Shell 支持** | bash、zsh、fish 等主流 Shell |
| **包管理器** | APT (Debian/Ubuntu 软件源) |
| **开发工具** | gcc、clang、make、git 等 |
| **脚本语言** | Python、Node.js、Ruby、Perl 等 |
| **文本编辑器** | vim、nano、emacs 等 |
| **网络工具** | curl、wget、openssh 等 |
| **多会话** | 支持多个终端会话同时运行 |
| **多窗口** | Android 7.0+ 多窗口支持 |
| **自定义快捷键** | 可配置的硬件/软件键盘快捷键 |
| **终端配色** | 可自定义主题和配色方案 |

### 终端使用场景

- **开发调试**：在 Android 设备上直接编写和调试代码
- **服务器管理**：通过 SSH 管理远程服务器
- **学习 Linux**：无需 Root 即可体验完整的 Linux 环境
- **自动化脚本**：编写 Shell/Python 脚本实现自动化任务

### 致谢 Termux

> 终端功能的实现基于 [Termux](https://github.com/termux/termux-app) 项目，这是一个知名的 Android 终端模拟器和 Linux 环境。UIN Tool 在 Termux 核心代码的基础上进行了适配和增强，将其无缝集成到插件化框架中。感谢 Termux 团队的开源贡献！

---

## 版本信息

### 当前版本：v5.3.0 (Build 19)

| 项目 | 信息 |
|------|------|
| 版本号 | 5.3.0 |
| 版本代码 | 19 |
| 更新日期 | 2026年8月8日 |
| 最低 Android 版本 | 6.0 (API 23) |
| 目标 Android 版本 | 14 (API 34) |
| 编译 SDK 版本 | 35 (Android 15) |
| 架构 | arm64-v8a |

### 版本历史

#### v5.3.0 (Build 19) - 🎨 全面完善：统一 UI 组件体系 + 颜色选择器/分类管理/权限管理增强

**🎨 渐变背景 / 玻璃 / 导航 / 弹窗优化：**
- 新增**全局渐变背景**：在「UI 个性化」→「效果」页配置，所有页面统一生效，顶部标题栏跟随渐变；支持开关节、单选/多选两种模式、可设起止方向（6 方向）
- **默认单色渐变（按主题自适应）**：浅色 `#FFC4D6DF` / 深色 `#FF4C4F51`（右下 → 左上），随深浅色主题自动适配；配置参与保存/重置/导出/导入
- 页面切换动画**新旧屏全程明亮**（移除 alpha 淡入/淡出，仅保留位移 + 缩放）；插件列表**移除右侧竖向滚动条**、原样滚动
- 底部导航改为**悬浮样式**：卡片背景/圆角/阴影，覆盖内容之上不裁切，点击按压缩放、无涟漪；弹窗背景与主背景**同款渐变、完全不透明**，原 Material3 `AlertDialog` 统一替换为 `UnifiedAlertDialog`

**🎨 UI 个性化页面优化：**
- 功能**核实修复**：涟漪开关真实生效、状态栏/导航栏颜色可自定义、深色主色接入主题
- 渐变模式/起止方向改为**下拉框**，渐变配置合并为一张卡片；标签栏跟随背景色并移除底部描边线

**🧩 插件分类 / 详情管理：**
- 插件详情页与导出/删除操作栏新增「更换分类」（支持批量与自定义）；分类筛选栏可横向滑动
- 工具页插件项**长按弹出详情弹窗**，可更换分类/卸载；管理页复选框仅在选择模式显示，批量删除支持一次删除全部选中

**🔐 权限管理：**
- 新增 **Shizuku** 与 **Dhizuku** 权限支持（完全采用官方 API）；权限勾选状态自动刷新（授权后无需手动下拉）

**🎨 颜色选择器 / 组件体系：**
- 颜色选择器新增**可视化取色器**与**十六进制输入**，支持夜间模式跟随主题
- `Unified*` 组件成为唯一实现源，全部屏幕统一迁移；卡片/输入框/对话框支持玻璃效果

**🏗️ 其他：**
- 镜像常量、文件工具、大小格式化收敛到单一实现；`BackupRepository` → `IBackupRepository` 命名统一

#### v5.0.0 (Build 16) - 🌐 全量国际化 + 动态主题引擎 + UI 全面优化

**🌐 全量国际化（i18n）：**
- 全应用硬编码中文文案迁移至 string 资源：默认英文（en）+ 完整简体中文（zh-rCN），移除日文资源
- 覆盖 2600+ 字符串键，涉及全部屏幕（主界面、插件管理、权限、仓库、日志、备份、镜像、文档/帮助、开发向导、代码编辑器、桌面小部件等）
- 修复资源键兼容性问题：数字开头键、Java 保留字键、撇号转义（aapt2 flatten）、缺失 Activity 标签等

**🎨 动态主题引擎：**
- 新增 JSON 动态主题引擎：`UINToolTheme` 读取 UIConfig 配色即时生效，支持深色模式联动
- 插件 WebView 注入 `--uin-*` CSS 变量，主题色随应用同步
- 圆角半径、字号全部改为配置驱动，覆盖所有页面
- 管理底部导航栏与系统状态栏颜色跟随主题，修复紫色主题残留

**🧭 底部导航重构：**
- 底部导航改为自绘（Row + clickable(indication=null)），规避 material3 NavigationBar / LocalIndication 版本差异
- 顶部细边框沿圆角收口绘制，指示图标改为终端提示符 `>_`
- 标签页切换加入水平滑动 + 淡入过渡动画（AnimatedContent）

**🔄 下拉刷新统一：**
- 移除全部页面右上角刷新图标，统一改为 Material 3 PullToRefreshBox 下拉刷新
- 空列表状态同样支持下拉；指示器使用主题色并固定居中
- 8 个刷新页显示"上次更新时间"，时间淡入并在 1 秒后自动淡出

**✨ UI 优化与交互：**
- 插件列表增删动画（animateItem + key）；仓库/插件管理加载改骨架屏
- 镜像管理"加镜像"改为右下角 FAB；Toast 统一改为 Material Snackbar（全局宿主 + 生命周期感知）
- 玻璃特效落地到全部卡片与弹窗（UI 个性化开关控制）
- 全局 Activity 切换为「**滑动 + 淡入淡出**」：旧屏滑出并淡出、新屏滑入并淡入
- 管理页顶部栏统一为 `ManageTopAppBar`，颜色跟随主题与页面背景

**🛠️ 其他优化：**
- 深浅色配色板可同时编辑混用，应用启动即加载主题
- 开屏恢复透明背景图标、加快启动（700ms 淡入缩放动画）
- 修复管理页返回/保存按钮点击无效、镜像弹窗紫色背景等问题

#### v4.5.0 (Build 15) - 🐧 Proot 容器运行时 + 自定义后端

**🐧 Proot 容器运行时（`backendRuntime: "proot"`）：**
- 插件后端可在共享 Alpine 容器中运行，与宿主机环境隔离
- 首次使用时自动初始化 Termux 环境，并通过 `proot-distro restore` 从 `assets/alpine.tar.xz` 离线恢复 Alpine 容器（备份内置预装 Python 环境）
- 容器内可使用 `apk add` 安装依赖，不污染宿主 Termux 环境
- 插件目录自动绑定到容器内 `/plugins/<pluginId>`，容器内 `127.0.0.1:PORT` 与宿主互通
- 环境流水线：Termux 就绪 → Alpine 就绪 → 启动前命令 → 启动后端

**⚡ 启动前命令（`backendPreCommand`）：**
- 首次打开时弹窗选择：「现在运行」「稍后」「取消」
- 命令在 Termux 终端中执行，成功（exit 0）一次后永久跳过（`pre_cmd_done`）
- 执行失败时回到插件页并提示退出码与错误信息

**🔧 自定义后端模式（`backend: "other"`）：**
- 宿主不自动启动后端进程，由启动前命令在终端中自行启动服务
- TCP 端口轮询（200ms）判定就绪，超时放宽至 90s+ 兼容容器冷启动
- 支持无端口插件（`backendPort: 0`）

**🚀 后端连接提速：**
- 三处 OkHttpClient 增加 `.proxy(Proxy.NO_PROXY)`，避免系统代理劫持 loopback 流量
- `waitForReady` 改为 200ms TCP 探测 + HTTP 轮询，去掉 1s 硬编码延迟
- 停止后端时按进程组 `SIGKILL`，确保 proot 子进程一并退出

**🐛 其他修复：**
- 修复引导页（Onboarding）闪烁与跳过后再弹出的问题：去除 SplashActivity 双重导航路径，统一由 Compose 驱动，并修复权限弹窗首帧闪现

**🖥️ CUI 终端插件：**
- 新增「CUI 终端（命令行界面）」插件类型：前端为全屏终端，打开后在插件目录执行启动命令
- 4 步创建向导，自动生成 `scripts/script.py` 示例脚本，支持配置启动命令与运行环境（termux/proot）
- 支持 CUI + 后端 / CUI + Proot 组合模式

**🧰 模板导出重构 + 开发工具优化：**
- 插件模板导出改为从 `assets/test_plugins/` 导出 7 个打包好的插件（cuitest / othertest / termux / allapi / storage / NativeTestPlugin / web_plugin_template），自动生成 `README.txt` 说明
- 清理 assets 中原有零散插件模板，统一由内置打包插件接管
- 修复「导出中...」一直卡住的问题（导出后台线程化），Toast 显示线程安全化
- 创建插件按钮改为纯主题色，移除渐变色

**⚙️ 构建优化：**
- 精简 `proguard-rules.pro`，仅保留可能被 R8 删除的重要代码（空壳插件宿主占位实现、`@JavascriptInterface` 方法、插件 JSON 模型），缩小 release 包体积

#### v4.4.4 (Build 14) - 🎉 插件弹窗系统统一 + 交互修复

**🎉 重大更新：插件弹窗系统统一**

**插件弹窗系统统一：**
- 插件弹窗全部改用应用内置的 Compose 统一对话框组件（UnifiedDialog / UnifiedConfirmDialog / UnifiedInfoDialog）
- 移除旧的 UnifiedViewDialog 自定义弹窗
- JS alert / confirm / 确认对话框 / 输入对话框 / 特殊权限弹窗统一走同一套弹窗组件

**弹窗排队机制：**
- 多个弹窗请求按顺序排队显示，不再互相覆盖
- 上一个弹窗关闭后自动展示下一个
- 新增回调式弹窗 API：showConfirmDialog(title, message, callbackId)、showPromptDialog(title, hint, callbackId)

**交互修复：**
- 修复插件页面无法滚动/点击的问题：对话框覆盖层默认隐藏，仅在弹窗显示时显示
- 修复确认对话框不显示的问题
- 修复截图功能无法保存的问题（改用视图绘制方式捕获画面）

#### v4.4.0 (2026年7月28日) - 🎉 持久化存储 + 权限系统完善

**🎉 重大更新：插件数据持久化存储**

**核心存储系统：**
- **统一数据存储**：基于 SharedPreferences 的键值对存储，支持 String/Int/Long/Boolean/Float/JSON 全类型
- **独立存储隔离**：每个插件拥有独立的 `data/` 目录，数据互不干扰
- **文件存储系统**：支持读写、删除、复制、移动、列出、获取大小等完整文件操作
- **安全路径防护**：防止路径遍历攻击，确保文件只能在插件目录内操作
- **磁盘空间检查**：写入前检查可用空间，防止磁盘写满
- **并发安全**：使用 ReentrantReadWriteLock 保证多线程安全
- **数据版本管理**：支持插件数据版本迁移，升级时自动迁移
- **数据统计**：KV 数量、文件数量、总大小、缓存大小等信息

**插件数据目录结构：**
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
│   ├── config.json      # 配置文件
│   ├── settings.txt     # 用户设置
│   └── images/          # 用户资源
└── cache/               # ✅ 插件缓存目录
└── temp_*.dat

```

**Web 插件存储 API（JavaScript）：**
```javascript
// KV 存储
UINPlugin.setStorage('key', 'value');
UINPlugin.getStorage('key');
UINPlugin.setStorageInt('key', 123);
UINPlugin.setStorageBool('key', true);
UINPlugin.setStorageJSON('key', JSON.stringify({name: 'test'}));

// 批量操作
UINPlugin.setStorageBatch(JSON.stringify({k1:'v1', k2:'v2'}));
const result = JSON.parse(UINPlugin.getStorageBatch('["k1","k2"]'));

// 文件操作
UINPlugin.writeFile('notes.txt', 'Hello World');
const content = UINPlugin.readFile('notes.txt');
UINPlugin.deleteFile('notes.txt');

// 存储统计
const stats = JSON.parse(UINPlugin.getStorageStats());
console.log('KV:', stats.kvCount, '文件:', stats.fileCount);
```

原生插件存储 API（Kotlin）：

```kotlin
val pctx = PluginContext(context, pluginDir)
pctx.putString("key", "value")
val value = pctx.getString("key")
pctx.writeFile("data.txt", "content")
val content = pctx.readFile("data.txt")
```

权限系统全面完善：

· 永久授权状态：插件权限状态持久化存储，一次授权永久生效
· 权限状态值：0=未授权（显示弹窗），1=已授权（直接进入），2=已拒绝（直接进入）
· 一键授权：支持一次性授予所有权限
· 权限分组：普通权限和特殊权限分组请求
· 特殊权限引导：悬浮窗、修改系统设置等特殊权限引导用户去系统设置开启
· 权限弹窗优化：Material Design 3 风格弹窗，直观展示权限说明
· 权限状态查询：可视化查看每个权限的状态

代码编辑器增强：

· TextMate 语法高亮：支持 30+ 种编程语言
· 28+ 编辑器主题：深色/浅色主题自由切换
· 文件树管理：侧边栏文件列表，支持添加/删除文件
· 文件图标：根据文件类型显示不同图标
· 撤销/重做：完整的编辑历史

API 扩展：
Web 插件新增 140+ 个 API 接口，覆盖以下类别：

· 设备信息（16个）：型号、版本、屏幕、内存、CPU、构建信息等
· 传感器（9个）：加速度计、陀螺仪、光线、距离、磁场、方向、气压、温度、湿度
· 位置服务（2个）：获取位置、反向地理编码
· 屏幕/显示（5个）：亮度、自动亮度、显示信息、字体缩放
· 系统设置（7个）：飞行模式、蓝牙、WiFi、移动数据、位置、NFC、自动旋转、勿扰
· 存储信息（3个）：总容量、可用容量、使用百分比
· 网络数据（11个）：网络信息、WiFi信息、信号强度、运营商、IP、速度、Ping
· 电池（5个）：电量、健康、电压、温度、技术
· 音频（5个）：音量、最大音量、静音、耳机状态
· 时间/日期（5个）：当前时间、时区、夏令时
· 系统语言（4个）：系统语言、国家、区域
· 应用管理（7个）：应用列表、打开应用、应用信息
· 文件操作（15个）：读写删除、复制移动、目录操作、文件信息
· 网络请求（5个）：GET、POST、PUT、DELETE、下载
· 权限（3个）：检查、请求、批量请求
· UI（4个）：加载、确认对话框、输入对话框
· 剪贴板（3个）：复制、获取、清空
· 振动（2个）：震动、取消
· 通知（2个）：发送、取消
· 系统操作（10个）：打开各种设置、全屏、常亮、截图
· 事件（2个）：发送事件、添加监听

数据迁移：

· 旧版 web_plugin_ SharedPreferences 数据自动迁移到新存储系统
· 插件更新时保留 data/ 目录，用户数据不丢失
· 卸载插件时自动清理所有数据

Bug 修复：

· 修复权限弹窗点击取消进入插件的问题
· 修复权限状态持久化失败的问题
· 修复部分权限被拒绝后仍反复弹窗的问题
· 优化权限请求流程，一次授权永久生效

v4.2.0 (2026年7月24日) - 🎉 Termux 后端集成

🎉 重大更新：插件后端支持

Termux 后端集成：

· Web 插件可直接启动 Termux 后端服务（Python/Node.js/PHP/二进制）
· 后端服务自动启动，用户完全无感知
· HTTP API 通信（无需 WebSocket）
· 后端必须提供 /health 健康检查端点
· 后端进程生命周期管理（插件关闭时自动停止）
· 端口自动分配（默认 8000）

后端模板：

· 新增 python_template.tpk 模板
· 使用 Python 内置 http.server，无需安装 Flask
· 兼容 Python 3.14+（移除 cgi 模块依赖）
· 支持计算、记录、查询、系统命令等 API

插件说明功能：

· 插件可在 plugin.json 中声明 notice 字段
· 首次打开插件时自动显示说明弹窗
· 用户可选择「不再提示」或「稍后提醒」
· 插件管理页面可查看完整说明

插件创建向导优化：

· 统一「创建插件」入口，弹窗选择前端类型
· 支持原生 UI、纯 WebView、WebView + 后端三种模式
· 后端选择：Python、Node.js、PHP、二进制文件
· Web 插件默认生成空白 HTML/CSS/JS 文件
· 二进制后端支持直接选择可执行文件
· 向导步骤根据类型动态调整（4-5 步）

后端管理增强：

· PluginBackendManager 统一管理后端进程
· 支持多语言解释器路径自动查找
· 输出监控（stdout/stderr）
· 健康检查（等待服务就绪）
· Python 路径优先使用 Termux 的 python 命令

UI 统一：

· 弹窗统一使用 Compose AlertDialog，白色背景 Material 3 风格
· 开发页面按钮颜色统一
· 移除所有 Emoji

v4.1.0 (2026年7月17日) - 🎉 代码编辑器升级

🎉 重大更新：Sora Editor 集成

代码编辑器全面升级：

· Sora Editor 集成：替换原有简陋编辑器，采用专业的 Sora Editor 引擎
· TextMate 语法高亮：支持 30+ 种编程语言的语法高亮
· 主题系统：内置 28+ 种代码编辑器主题（深色/浅色）
· 语言支持：Java、Kotlin、Python、JavaScript、TypeScript、HTML、CSS、JSON、XML、Markdown、Shell、SQL、Go、Rust、PHP、Ruby、Swift、Dart、Lua、Scala、Perl、Haskell、Elixir、Erlang、Clojure、Groovy、Dockerfile、Makefile、INI、Properties、TOML、YAML 等
· 行号显示：支持行号显示
· 代码折叠：支持代码块折叠
· 括号匹配：自动高亮匹配括号
· 自动缩进：智能自动缩进
· 撤销/重做：完整的撤销/重做历史
· 主题切换：一键切换编辑器主题
· 文件树管理：侧边栏文件列表，支持添加/删除文件
· 文件图标：根据文件类型显示不同图标

其他优化：

· 修复插件权限管理 UI
· 修复备份恢复功能
· 优化应用启动速度
· 统一 Toast 和弹窗样式

技术细节：

· Sora Editor 版本：0.24.4
· 使用 editor-bom 统一管理版本
· language-textmate 模块提供语法高亮

v4.0.0 (2026年7月14日) - 🎉 重大重构

🎉 技术栈全面升级：

架构重构：

· Kotlin 迁移：核心代码从 Java 迁移到 Kotlin
· Jetpack Compose：UI 全面迁移到声明式 Compose 框架
· MVVM 架构：引入 ViewModel + StateFlow 响应式状态管理
· 依赖注入：ServiceLocator 统一管理服务实例
· Repository 模式：数据层与业务层分离

终端集成（基于 Termux）：

· 内置 Termux 引擎：集成 Termux 终端模拟器核心
· 完整 Linux 环境：支持 APT 包管理、bash/zsh 等 Shell
· 多会话管理：支持同时运行多个终端会话
· 终端设置：字体、配色、键盘、快捷键全面可配置

UI 全面升级：

· Material 3 设计：采用最新 Material Design 规范
· 深色模式支持：完整的深色/浅色主题切换
· 全新玻璃效果：毛玻璃质感的 UI 组件
· 完整颜色选择器：RGB + Alpha 通道独立调节
· 38+ 颜色配置项：全部颜色可自定义
· 7 种圆角配置：全面控制 UI 形状

功能增强：

· 插件权限系统：基于 Android 权限模型的插件权限管理
· 插件依赖检查：自动检查并提示缺失依赖
· UI 配置导入导出：支持配置备份和分享
· 完整的备份系统：备份插件、配置、UI 主题

开发体验优化：

· 插件创建向导：可视化步骤引导创建插件
· 内置代码编辑器：支持缩放、语法高亮、文件管理
· Web 项目导入：支持导入已有的 Web 项目 ZIP 包

性能优化：

· 启动速度优化
· 内存占用优化
· 小部件刷新机制优化

---

功能清单

✅ 已实现功能

模块 功能 状态 说明
启动体验 SplashActivity ✅ 应用启动页，权限检查
启动体验 引导页系统 ✅ 首次启动引导页
启动体验 应用图标快捷方式 ✅ 长按图标快捷菜单
启动体验 权限请求对话框 ✅ 存储权限说明
应用更新 自动更新检测 ✅ 启动时自动检查
应用更新 强制更新机制 ✅ 支持强制更新
应用更新 版本忽略功能 ✅ 可忽略版本
应用更新 应用内下载 ✅ 显示下载进度
GitHub 加速 镜像站管理 ✅ 独立管理页面
GitHub 加速 内置镜像站 ✅ 13+ 个默认镜像
GitHub 加速 自定义镜像 ✅ 手动添加镜像
GitHub 加速 导入/导出 ✅ TXT 格式
GitHub 加速 CDN 加速 ✅ 可开关
终端 (Termux) 终端模拟器 ✅ 基于 Termux 改编
终端 (Termux) Linux 环境 ✅ APT 包管理
终端 (Termux) 多会话支持 ✅ 同时运行多个会话
终端 (Termux) 多窗口支持 ✅ Android 7.0+
终端 (Termux) 终端设置 ✅ 字体/配色/快捷键
后端集成 Python 后端 ✅ 自动启动 Termux Python
后端集成 Node.js 后端 ✅ 自动启动 Termux Node.js
后端集成 PHP 后端 ✅ 自动启动 Termux PHP
后端集成 二进制后端 ✅ 选择可执行文件
后端集成 健康检查 ✅ /health 端点
后端集成 进程管理 ✅ 自动启动/停止
插件引擎 动态加载 DEX ✅ DexClassLoader
插件引擎 资源隔离 ✅ 独立 Context
插件引擎 生命周期管理 ✅ 完整生命周期
插件引擎 WebView 支持 ✅ HTML/CSS/JS
插件引擎 JS 桥接 API ✅ 140+ 个 API
插件引擎 网络请求 API ✅ HTTP GET/POST/PUT/DELETE
插件引擎 文件系统 API ✅ 读写删除复制移动
插件引擎 存储 API ✅ KV + JSON + 批量操作
插件引擎 数据持久化 ✅ 独立 data/ 目录
插件管理 导入插件 ✅ TPK 文件导入
插件管理 导出插件 ✅ ZIP 包导出
插件管理 批量导入 ✅ 多个 TPK 文件
插件管理 插件集导入 ✅ ZIP 批量导入
插件管理 分类管理 ✅ 添加/删除/更换分类（单个/批量）
插件管理 签名验证 ✅ SHA-256 验证
插件管理 插件说明 ✅ notice 字段显示
插件仓库 GitHub 集成 ✅ 官方仓库
插件权限 权限声明 ✅ plugin.json 声明
插件权限 权限检查 ✅ 启动前检查
插件权限 权限请求 ✅ 分组请求
插件权限 权限状态 ✅ 可视化状态
插件权限 永久授权 ✅ 状态持久化
插件权限 Material 3 弹窗 ✅ 统一风格
插件权限 Shizuku/Dhizuku 支持 ✅ 官方 API（`Shizuku`/`Dhizuku` 检测+授权，结果实时刷新）
文档系统 文档中心 ✅ 集中管理文档
开发工具 原生插件向导 ✅ Kotlin/Java 插件
开发工具 Web 插件向导 ✅ Web 插件创建
开发工具 代码编辑器 ✅ Sora Editor 引擎
开发工具 语法高亮 ✅ 30+ 种语言
开发工具 编辑器主题 ✅ 28+ 种主题
开发工具 模板导出 ✅ 导出模板文档
UI 个性化 颜色配置 ✅ 38+ 颜色可调
UI 个性化 颜色选择器 ✅ 可视化取色器（色相条 + 饱和度/亮度面板）+ 十六进制输入 + RGB/Alpha
UI 个性化 渐变背景 ✅ 单选/多色渐变 + 方向设置，可开关，默认浅蓝
UI 个性化 圆角配置 ✅ 7 种圆角
UI 个性化 尺寸配置 ✅ 按钮/间距/图标
UI 个性化 字体配置 ✅ 字体大小/加粗
UI 个性化 导入/导出 ✅ 配置备份
桌面小部件 3x3 小部件 ✅ 显示 9 个插件
桌面小部件 1x1 快捷方式 ✅ 单个插件快捷方式

---

技术栈详情

技术 版本 用途
Kotlin 2.1.0 主要开发语言
Jetpack Compose 2024.09.00 声明式 UI 框架
Compose Material 3 1.3.0 Material 3 组件
Compose Navigation 2.7.7 页面导航
Android SDK API 35 Android 框架
Kotlin Coroutines 1.7.3 异步编程
OkHttp 4.12.0 HTTP 客户端
Retrofit 2.11.0 REST API
Gson 2.11.0 JSON 解析
CommonMark 0.22.0 Markdown 渲染
Sora Editor 0.24.4 代码编辑器
Sora Editor TextMate 0.24.4 语法高亮
MultiDex 2.0.1 多 DEX 支持
NDK 29.0.14033849 C/C++ 原生支持

---

快速开始

安装应用

1. 从 Releases 下载最新 APK
2. 在设备上启用「允许安装未知来源应用」
3. 安装 APK

首次启动

1. 应用启动后显示存储权限说明对话框
2. 点击「去授权」授予存储权限
3. 首次启动显示引导页
4. 阅读引导内容后点击「开始体验」

使用终端

1. 点击底部「开发」标签
2. 点击「打开终端」启动终端
3. 首次启动会自动安装 Linux 环境（约 30-60 秒）

终端常用命令：

```bash
# 更新软件源
pkg update

# 安装 Python
pkg install python

# 安装 git
pkg install git

# 安装 Node.js
pkg install nodejs
```

安装插件

方式一：从仓库安装

1. 点击底部「仓库」标签
2. 浏览可用插件
3. 点击「安装」按钮

方式二：本地导入

1. 将 .tpk 文件传输到手机
2. 点击底部「管理」→「插件管理」
3. 点击「导入」选择文件

创建插件

1. 点击底部「开发」标签
2. 点击「创建插件」
3. 选择前端类型（原生 UI / 纯 WebView / WebView + 后端）
4. 如选择 WebView + 后端，选择后端语言（Python/Node.js/PHP/二进制）
5. 按照向导填写插件信息
6. 点击「完成」生成项目文件

使用代码编辑器

1. 在插件创建向导中进入代码编辑器
2. 左侧边栏显示项目文件列表
3. 点击文件即可编辑
4. 支持 30+ 种编程语言语法高亮
5. 点击调色板图标可切换编辑器主题
6. 支持撤销/重做功能
7. 支持添加/删除文件
8. 点击「完成」保存所有更改

插件数据存储

Web 插件（JavaScript）：

```javascript
// 存储数据
UINPlugin.setStorage('username', 'John');
UINPlugin.setStorageInt('score', 100);
UINPlugin.setStorageJSON('config', JSON.stringify({theme: 'dark'}));

// 读取数据
const name = UINPlugin.getStorage('username');
const score = UINPlugin.getStorageInt('score', 0);
const config = JSON.parse(UINPlugin.getStorageJSON('config'));

// 文件操作
UINPlugin.writeFile('notes.txt', 'Hello World');
const content = UINPlugin.readFile('notes.txt');

// 查看存储统计
const stats = JSON.parse(UINPlugin.getStorageStats());
console.log('KV数量:', stats.kvCount);
console.log('文件数量:', stats.fileCount);
```

原生插件（Kotlin）：

```kotlin
val pctx = PluginContext(context, pluginDir)
pctx.putString("key", "value")
val value = pctx.getString("key")
pctx.writeFile("data.txt", "content")
```

---

常见问题

Q: 如何安装插件？
A: 三种方式：从「仓库」页面直接安装、导入 .tpk 文件、批量导入或插件集导入。

Q: Web 插件和原生插件有什么区别？
A: Web 插件使用 HTML/CSS/JS 开发，无需编译，修改后即时生效；原生插件使用 Java 开发，性能更好，但需要编译。

Q: 如何开发自己的插件？
A: 点击底部「开发」→「创建插件」，选择类型后按照向导操作即可。

Q: 如何自定义 UI 颜色和圆角？
A: 在「管理」→「UI 个性化」中，可以自定义 38+ 种颜色和 7 种圆角大小。

Q: 插件数据存储在哪里？
A: 每个插件的数据存储在 /storage/emulated/0/UIN_Tool/plugins/{pluginId}/data/ 目录下，KV 数据存储在 SharedPreferences 中。

Q: 更新插件会丢失数据吗？
A: 不会。更新插件时会自动保留 data/ 目录，用户数据不会丢失。

Q: 插件权限状态会持久化吗？
A: 会。一次授权后，权限状态永久保存，下次打开不再重复弹窗。

Q: Web 插件支持哪些 API？
A: 支持 140+ 个 API，涵盖设备信息、传感器、位置、网络、文件系统、存储、权限、UI、剪贴板、振动、通知、系统操作等。

Q: 代码编辑器支持哪些语言？
A: 支持 Java、Kotlin、Python、JavaScript、TypeScript、HTML、CSS、JSON、XML、Markdown、Shell、SQL、Go、Rust、PHP、Ruby、Swift、Dart、Lua、Scala、Perl、Haskell、Elixir、Erlang、Clojure、Groovy、Dockerfile、Makefile、INI、Properties、TOML、YAML 等 30+ 种编程语言。

Q: 如何导出插件数据？
A: Web 插件可使用 UINPlugin.exportData() 导出所有数据为 JSON 格式。

Q: 终端功能如何使用？
A: 点击底部「开发」→「打开终端」，首次使用会自动安装 Linux 环境。

---

开源协议

本项目采用 MIT License 开源协议。

---

贡献者名单

贡献者 角色 贡献内容
UIN Team 核心开发 架构设计、核心功能
一支电笔 功能开发 1x1 桌面小部件功能
Termux 团队 上游项目 终端模拟器核心引擎

---

联系方式

渠道 地址
GitHub https://github.com/Undefined-Invalid-Null/UIN-Tool
电子邮箱 undefinedinvalidnull@outlook.com
插件仓库 https://github.com/UIN-Tool-Plugins
QQ 群 511875883

---

© 2026 UIN Team. All Rights Reserved.
