# UIN Tool — 项目结构与功能总结

## 概述

**UIN Tool** 是一个基于 Android 的插件化框架应用，允许用户动态加载和运行两种类型的插件：

- **原生插件**（Java + DEX）：通过 `DexClassLoader` 动态加载，性能最优。
- **Web 插件**（HTML/CSS/JS）：通过 WebView 渲染，支持热更新、无需重新编译。

提供完整的插件生态：开发向导、管理导入导出、权限控制、备份恢复、日志系统、桌面小部件和 UI 个性化。

---

## 项目结构

```
UIN-Tool/
├── app/
│   ├── build.gradle                    # 应用模块构建配置
│   ├── proguard-rules.pro              # 混淆规则
│   └── libs/
│       └── Dhizuku-API-2.5.4.aar      # Dhizuku API (系统权限增强)
│   └── src/main/
│       ├── AndroidManifest.xml          # 所有权限声明、四大组件注册
│       ├── assets/
│       │   ├── compiler/                # Java→DEX 编译器 (ecj.jar, host-sdk.jar, r8.jar)
│       │   ├── docs/                    # 内置文档 (About, Help, README, CONTRIBUTORS)
│       │   ├── plugin_templates/        # Web 插件模板文件
│       │   ├── templates/               # 已打包的 TPK 模板 (native, web)
│       │   ├── template.tpk             # 基础模板
│       │   └── ui_config.json           # 默认 UI 配置文件
│       └── java/com/UIN/Tool/
│           ├── MainActivity.java        # 主界面 (底部导航: 工具/开发/管理)
│           ├── UinApplication.java      # Application 入口
│           ├── compiler/
│           │   └── JavaToDexCompiler.java  # Java 源码 → DEX 编译管线
│           ├── plugin/                  # ★ 核心插件引擎
│           │   ├── PluginManager.java   #  插件管理核心 (安装/卸载/加载/生命周期)
│           │   ├── PluginInfo.java      #  插件元数据 (plugin.json 映射)
│           │   ├── PluginInterface.java #  原生插件接口
│           │   ├── PluginContext.java   #  插件资源隔离 Context
│           │   ├── PluginHostActivity.java # 插件宿主 Activity
│           │   ├── PluginJSInterface.java  # Web 插件 JS Bridge
│           │   ├── PluginWebInterface.java # WebView ←→ 插件通信
│           │   ├── PluginWebChromeClient.java # WebView ChromeClient
│           │   └── WebPluginProxy.java  #  Web 插件代理
│           ├── service/
│           │   └── UinAccessibilityService.java # 无障碍服务
│           ├── ui/
│           │   ├── common/              # 基类与工具
│           │   │   ├── BaseActivity.java
│           │   │   ├── BaseFragment.java
│           │   │   ├── DialogHelper.java
│           │   │   ├── IconHelper.java
│           │   │   └── PermissionHelper.java
│           │   ├── tools/               # 底部「工具」Tab
│           │   │   ├── ToolsFragment.java   #  插件浏览 (分类/列表/搜索)
│           │   │   ├── CategoryAdapter.java #  分类 ExpandableListView 适配器
│           │   │   ├── GridAdapter.java
│           │   │   ├── ToolsAdapter.java
│           │   │   └── PluginShortcutHelper.java
│           │   ├── dev/                 # 底部「开发」Tab
│           │   │   ├── DevFragment.java          #  开发入口
│           │   │   ├── NativePluginWizardActivity.java # 原生插件创建向导
│           │   │   ├── WebPluginWizardActivity.java   # Web 插件创建向导
│           │   │   ├── CodeEditorActivity.java       # 代码编辑器
│           │   │   ├── DevDocActivity.java           # 开发文档
│           │   │   ├── BasePluginWizardActivity.java # 向导基类
│           │   │   └── FileTreeAdapter.java
│           │   ├── manage/              # 底部「管理」Tab
│           │   │   └── ManageFragment.java  #  管理面板 (插件/权限/备份/UI/日志/小部件)
│           │   ├── plugin/
│           │   │   └── PluginManageActivity.java # 插件管理 Activity
│           │   ├── permission/
│           │   │   ├── PermissionManagerActivity.java       # 应用权限管理
│           │   │   └── PluginPermissionManagerActivity.java # 插件权限管理
│           │   ├── backup/
│           │   │   └── BackupManagerActivity.java # 备份恢复
│           │   ├── log/
│           │   │   └── LogViewerActivity.java    # 日志查看器
│           │   ├── docs/
│           │   │   └── DocViewerActivity.java    # 文档查看器
│           │   ├── help/
│           │   │   └── HelpActivity.java         # 帮助
│           │   ├── settings/
│           │   │   └── UIConfigActivity.java     # UI 个性化配置
│           │   └── widget/              # 桌面小部件
│           │       ├── UINWidgetProvider.java
│           │       ├── UINWidgetService.java
│           │       ├── UINWidgetConfigureActivity.java
│           │       ├── WidgetClickActivity.java
│           │       ├── WidgetConfig.java
│           │       ├── WidgetConfigureActivity.java
│           │       └── SystemEventReceiver.java
│           └── utils/
│               ├── CompilerUtils.java       # 编译工具
│               ├── CrashHandler.java        # 崩溃捕获
│               ├── FileUtils.java           # 文件操作
│               ├── LogUtils.java            # 日志系统
│               ├── MarkdownRenderer.java    # Markdown 渲染
│               ├── PreferencesUtils.java    # SharedPreferences 封装
│               ├── TemplateUtils.java       # 模板工具
│               └── UIConfig.java            # UI 配置管理器
├── build.gradle               # 根构建配置
├── settings.gradle            # 项目设置
├── gradle.properties          # Gradle 属性
├── gradlew / gradlew.bat     # Gradle Wrapper
├── .github/workflows/android-build.yml  # CI
├── .gitignore
└── README.md                 # 项目说明文档
```

---

## 核心功能模块

### 1. 插件引擎 (`plugin/`)

| 类 | 职责 |
|---|---|
| `PluginManager` | 单例核心：加载/安装/卸载/分类/搜索插件；管理 `DexClassLoader` 和 `WebView` 缓存；签名验证 (SHA-256)；生命周期分发 |
| `PluginInfo` | 插件元数据模型 (`plugin.json`)，含 `pluginId`、`uiType`(native/web)、`entry`、`category` 等 |
| `PluginInterface` | 原生插件必须实现的接口：`onCreateView`、`onResume`、`onPause`、`onDestroy`、`onBackPressed` |
| `PluginContext` | 继承 `ContextWrapper`，为原生插件提供独立的 `AssetManager`/`Resources` (资源隔离) |
| `PluginHostActivity` | 插件运行宿主 Activity，根据 `uiType` 分发到原生 View 或 WebView |
| `PluginJSInterface` | Web 插件 JS Bridge，通过 `@JavascriptInterface` 暴露给 JS 的 `UINPlugin` 对象，提供 Toast、剪贴板、存储、设备信息、分享、震动等 API |
| `PluginWebInterface` | 另一套 JS Bridge，用于 `WebPluginProxy` 场景 |
| `WebPluginProxy` | Web 插件代理，处理 JS 调用 |

### 2. 编译器 (`compiler/`)

| 类 | 职责 |
|---|---|
| `JavaToDexCompiler` | Java 源码 → DEX 编译管线：使用 **ECJ** (Eclipse Compiler for Java) 编译为 `.class` → 打包为 `.jar` → 使用 **D8** 转换为 `classes.dex`；同时支持将项目打包为 `.tpk` 插件包 |

### 3. UI 层 (`ui/`)

#### 主界面架构 (单 Activity 多 Fragment)

- **`MainActivity`**：底部导航栏 (BottomNavigationView)，承载三个 Fragment：
  - **`ToolsFragment`** → **工具** Tab：按分类/列表展示已安装插件，支持搜索、点击运行
  - **`DevFragment`** → **开发** Tab：创建插件（原生/Web 向导）、导出模板、查看文档
  - **`ManageFragment`** → **管理** Tab：插件管理、权限管理、文档、日志、备份、UI 配置、小部件

#### 管理功能入口 (ManageFragment 卡片)

| 卡片 | 目标 Activity | 功能 |
|---|---|---|
| 插件管理 | `PluginManageActivity` | 导入/导出/批量导入/卸载 TPK 插件 |
| 权限管理 | `PermissionManagerActivity` | 应用权限开关列表 |
| 文档 | `DocViewerActivity` | 查看内置开发/帮助/关于文档 (Markdown) |
| 日志 | `LogViewerActivity` | 查看/导出/清空运行日志 |
| 备份恢复 | `BackupManagerActivity` | 备份/恢复插件和配置 |
| UI 配置 | `UIConfigActivity` | 自定义主题色、圆角、深色模式 |
| 桌面小部件 | — | 刷新小部件 |
| 开发者选项 | — | 忽略签名验证开关 |

### 4. 桌面小部件 (`ui/widget/`)

- **`UINWidgetProvider`**：AppWidgetProvider，提供可滚动的插件列表小部件
- **`UINWidgetService`**：RemoteViewsService，为小部件提供插件数据
- **`WidgetClickActivity`**：小部件点击透明桥梁 Activity
- **`SystemEventReceiver`**：监听开机完成和应用替换广播，刷新小部件

### 5. 无障碍服务 (`service/`)

- **`UinAccessibilityService`**：支持插件执行自动化操作

### 6. 工具类 (`utils/`)

| 类 | 职责 |
|---|---|
| `LogUtils` | 日志系统：带时间戳/级别的日志写入文件 `UIN_Tool/logs/`，支持 `enter`/`exit`/`success`/`action` 等结构化日志，自动截断 2MB |
| `FileUtils` | 文件操作：Uri→文件复制、ZIP 解压、目录递归删除/复制、文件→字符串 |
| `UIConfig` | UI 配置管理器：从 `ui_config.json` 读取主题色/圆角等配置，应用于 Activity 状态栏/导航栏/组件 |
| `CrashHandler` | 全局崩溃捕获：写入日志文件 |
| `PreferencesUtils` | SharedPreferences 封装：工作目录、插件签名、小部件配置等 |
| `MarkdownRenderer` | Markdown→Html 转换，用于文档查看器 |
| `TemplateUtils` | 插件模板操作 |
| `IconHelper` | 插件图标加载 |
| `DialogHelper` | 通用对话框构建 |

### 7. 插件包格式 (TPK)

插件以 `.tpk` 文件分发，本质是 ZIP 包，结构如下：

```
plugin.tpk
├── plugin.json       # 元数据 (必需)
├── icon.png          # 图标
├── plugin.dex        # 原生插件 DEX (native 类型必需)
├── web/              # Web 插件目录 (web 类型)
│   ├── index.html
│   ├── style.css
│   └── script.js
├── src/              # Java 源码 (可选，用于模板)
└── res/              # 资源文件 (可选)
```

`plugin.json` 关键字段：

| 字段 | 说明 |
|---|---|
| `pluginId` | 唯一标识 (如 `com.example.myplugin`) |
| `uiType` | `"native"` 或 `"web"` |
| `entry` | Web 插件入口文件 (如 `"web/index.html"`) |
| `mainClass` | 原生插件主类全名 |
| `category` | 分类 (如 "工具", "游戏") |
| `minHostVersion` | 最低宿主编译版本 |

---

## 技术栈

| 技术 | 版本 | 用途 |
|---|---|---|
| Java | 1.8 | 开发语言 |
| Android SDK | API 34 (target 34, min 21) | 框架 |
| Material Components | 1.11.0 | UI 组件库 |
| AndroidX | — | 支持库 |
| ViewBinding | — | 视图绑定 |
| DexClassLoader | — | 动态加载 DEX |
| ECJ + D8 | — | Java 编译、DEX 转换 |
| AccessibilityService | — | 自动化操作 |
| AppWidget | — | 桌面小部件 |
| Gradle | 8.x | 构建系统 |
| Dhizuku API | 2.5.4 | 系统权限增强 |

---

## 构建与运行

```bash
# 克隆
git clone https://github.com/Undefined-Invalid-Null/UIN-Tool.git
cd UIN-Tool

# 构建 Debug APK
./gradlew assembleDebug

# APK 路径: app/build/outputs/apk/debug/app-debug.apk
```

---

## 许可证

本项目采用 MIT License 开源。

© 2026 UIN Team. All rights reserved.
