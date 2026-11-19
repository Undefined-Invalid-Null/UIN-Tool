# UIN Tool

![Version](https://img.shields.io/badge/version-7.0.0-blue)
![Build](https://img.shields.io/badge/build-27-green)
![Android](https://img.shields.io/badge/Android-6.0%2B-brightgreen)
![License](https://img.shields.io/badge/license-MIT-orange)
![Kotlin](https://img.shields.io/badge/Kotlin-2.4.21-purple)
![Compose](https://img.shields.io/badge/Jetpack%20Compose-2026.09.00-blue)

## 应用简介

UIN Tool 是一个基于 Kotlin + Jetpack Compose 重构的 Android 插件化框架应用，允许用户动态加载和运行第三方插件。无论是原生 Java 插件还是 Web 技术栈（HTML/CSS/JS）的插件，都能在 UIN Tool 中无缝运行。它提供了一个完整的插件生态系统，包括插件开发、管理、运行、权限控制、**PRoot 容器后端**以及内置的**双窗格文件管理器**等功能。

### 核心理念

- **开放**：任何人都可以开发插件，支持原生 Java 和 Web 技术栈
- **安全**：插件权限授权，支持签名验证，防止恶意插件
- **高效**：原生性能，Web 插件支持热更新，无需重新编译
- **易用**：可视化开发向导，无需复杂配置即可创建插件
- **灵活**：支持网格/列表视图切换，支持分类管理
- **现代化**：基于 Jetpack Compose 构建，Material 3 设计语言
- **强大**：内置 PRoot Linux 环境，支持 Python/Node.js/PHP 后端
- **持久化**：插件数据独立存储，更新时自动保留用户数据
- **全能文件管理**：双窗格浏览、归档就地解压与打包、图片查看器、APK 信息页、书签与历史、递归搜索、深链定位、并排对比、按类型着色的文件图标
- **统一打开方式**：每个后缀可设默认打开器；内置 / 插件 / 系统建议 / 全部四档候选，插件档直接用顶栏搜索

---

## 终端功能（基于 PRoot）

UIN Tool **内置完整的 Linux 环境**，使用 [PRoot](https://github.com/proot-me/proot) 实现用户空间 Linux 系统，无需 Root 即可运行完整的 Debian/Ubuntu/Arch Linux。

### 终端特性

| 特性 | 说明 |
|------|------|
| **Linux 发行版** | Debian (默认)、Ubuntu、Arch Linux、Alpine |
| **包管理器** | APT、pacman、apk |
| **开发工具** | gcc、clang、make、git 等 |
| **脚本语言** | Python、Node.js、Ruby、Perl 等 |
| **文本编辑器** | vim、nano、emacs 等 |
| **网络工具** | curl、wget、openssh 等 |
| **多会话** | 支持多个终端会话同时运行 |

### 架构说明

UIN Tool 支持两种后端模式：

1. **内置 PRoot 模式（默认）**：直接使用 proot 二进制 + rootfs，无需外部应用
2. **Real Termux 模式**：兼容外部 Termux 应用，提供更完整的环境

### 终端使用场景

- **开发调试**：在 Android 设备上直接编写和调试代码
- **服务器管理**：通过 SSH 管理远程服务器
- **学习 Linux**：无需 Root 即可体验完整的 Linux 环境
- **自动化脚本**：编写 Shell/Python 脚本实现自动化任务

### 致谢 Termux

> 终端功能使用 PRoot 实现用户空间 Linux 环境。`terminal-emulator` 和 `terminal-view` 模块基于 [Termux](https://github.com/termux/termux-app) 项目改编。感谢 Termux 团队的开源贡献！

---

## 文件管理（双窗格）

底栏「文件」是内置文件管理器：左右两个窗格各自独立浏览，复制与移动在两窗之间流转。

| 能力 | 说明 |
|------|------|
| **双窗格** | 每个窗格有自己的浏览栈；窗格比例可拖动，也可在设置里锁定或每次恢复默认 |
| **选择与操作** | 长按进入选择模式；复制 / 移动 / 重命名 / 压缩 / 解压 / 分享 / 隐藏 / 添加书签 / 属性 / 删除 |
| **归档** | zip / 7z / tar 及 gzip、bzip2、xz、zstd 外层包就地浏览；可写归档支持写回，只读归档会标注；支持 zip AES-256 密码与分卷 |
| **搜索** | 递归搜索、正则、内容匹配、体积过滤；结果可整页展开并「在结果中搜索」 |
| **打开方式** | 内置 / 插件 / 系统建议 / 全部四档；内置能力为可收拢网格；每个后缀可设默认打开器；插件档直接用顶栏搜索，可把本地文件直接交给插件 |
| **查看器** | 图片查看器（缩放、翻页、GIF）、APK 信息页（权限、签名、安装、分享） |
| **对比** | 选中两个文件或两个文件夹，并排查看差异 |
| **导航** | 书签与历史、常用位置侧栏、`uin://files` 深链定位 |

文件图标按类型着色（图片、视频、音频、文档、代码、归档等各有一套颜色），不再是一排灰色图标。

---

## 版本信息

### 当前版本：v7.0.0 (Build 28)

| 项目 | 信息 |
|------|------|
| 版本号 | 7.0.0 |
| 版本代码 | 27 |
| 更新日期 | 2026年11月18日 |
| 最低 Android 版本 | 6.0 (API 23) |
| 目标 Android 版本 | 9 (API 28) |
| 编译 SDK 版本 | 37 |
| 架构 | arm64-v8a |

### 版本历史

| 版本 | 构建号 | 日期 | 亮点 |
|------|--------|------|------|
| v7.0.0 | 27 | 2026-11-18 | 双窗格文件管理器（归档就地浏览与写入、图片查看器、APK 信息页、压缩解压、书签历史搜索、深链定位、并排对比），打开方式合并为单一入口；代码编辑器全文搜索重做并新增 9 种语法；插件权限管理改为两级页面；风格系统按风格隔离配置；构建链升至 AGP 9.1.1 / Kotlin 2.4.21 / Gradle 9.3.1 / compileSdk 37 / Compose BOM 2026.09.00 / Coil 3.6.3 |
| v6.2.0 | 26 | 2026-10-31 | 代码编辑器专项（文件树/多标签/直接磁盘读写/ACTION_VIEW 对外入口、Unified 界面统一、四轮语法高亮根因修复、sora 0.24.6 + Kotlin 2.3.10） |
| v6.1.0 | 25 | 2026-10-07 | 全量缺陷修复（启动/权限/主线程卡顿/深色主题/插件宿主安全/备份回滚/错误可见性/构建去重） |
| v6.0.0 | 24 | 2026-09-26 | 去除 Termux 依赖、PRoot 容器系统、容器管理 UI、rootfs 导出压缩级别 |
| v5.7.0 | 23 | 2026-08-31 | 多源仓库聚合、插件图标缓存、增量 CI 构建、UI 优化 |
| v5.6.0 | 22 | 2026-08-28 | 新拟态风格、多语言切换、半透明效果控制 |
| v5.5.0 | 21 | 2026-08-22 | 崩溃修复、剪贴板伪权限、开发向导补全、实体 Termux 共享 Supervisor |
| v5.4.0 | 20 | 2026-08-10 | 插件 openWith 中转、插件多开 |
| v5.3.0 | 19 | 2026-07-31 | 统一 UI 组件、颜色选择器、分类管理、Shizuku/Dhizuku |
| v5.2.0 | 18 | 2026-07-24 | 打包优化、更新逻辑、Alpine 加速 |
| v5.1.0 | 17 | 2026-07-17 | 后端运行时重构、CUI 终端优化 |
| v5.0.0 | 16 | 2026-07-10 | 全面国际化、动态主题引擎、底部导航重构 |
| v4.5.0 | 15 | 2026-07-03 | Proot 容器运行时、自定义后端、预启动命令 |
| v4.4.0 | 13 | 2026-06-24 | 插件数据持久化、权限系统、代码编辑器升级 |
| v4.2.0 | 11 | 2026-06-17 | Termux 后端集成、插件说明 |
| v4.0.0 | 9 | 2026-06-10 | Kotlin 迁移、Compose UI、MVVM 架构 |

> 详细更新日志请查看 [CHANGELOG](CHANGELOG.md)。

---

## 功能列表

### 已实现功能

| 模块 | 功能 | 状态 | 说明 |
|------|------|------|------|
| 启动体验 | SplashActivity | 已实现 | 应用启动页、权限检查 |
| 启动体验 | 引导页系统 | 已实现 | 首次启动引导 |
| 启动体验 | 应用图标快捷方式 | 已实现 | 长按图标快捷菜单，图标跟主题色 |
| 启动体验 | 权限请求弹窗 | 已实现 | 存储权限说明 |
| 应用更新 | 自动更新检查 | 已实现 | 启动时自动检查 |
| 应用更新 | 强制更新机制 | 已实现 | Release Tag 第三段为 1 时强制更新 |
| 应用更新 | 版本忽略 | 已实现 | 可忽略普通版本 |
| 应用更新 | 应用内下载 | 已实现 | 前台服务显示下载进度 |
| GitHub 加速 | 镜像站管理 | 已实现 | 独立管理页 |
| GitHub 加速 | 内置镜像站 | 已实现 | 4 个默认镜像 |
| GitHub 加速 | 自定义镜像 | 已实现 | 手动添加镜像 |
| GitHub 加速 | 导入/导出 | 已实现 | TXT 格式 |
| GitHub 加速 | CDN 加速 | 已实现 | 可开关 |
| 终端 | 终端模拟器 | 已实现 | 基于 PRoot |
| 终端 | Linux 环境 | 已实现 | APT / pacman / apk 包管理 |
| 终端 | 多会话支持 | 已实现 | 多个会话同时运行 |
| 后端集成 | Python 后端 | 已实现 | 自动启动 Python |
| 后端集成 | Node.js 后端 | 已实现 | 自动启动 Node.js |
| 后端集成 | PHP 后端 | 已实现 | 自动启动 PHP |
| 后端集成 | 二进制后端 | 已实现 | 选择可执行文件 |
| 后端集成 | 健康检查 | 已实现 | /health 端点 |
| 后端集成 | 进程管理 | 已实现 | 自动启动/停止 |
| 插件引擎 | 动态 DEX 加载 | 已实现 | DexClassLoader |
| 插件引擎 | 资源隔离 | 已实现 | 独立 Context |
| 插件引擎 | 生命周期管理 | 已实现 | 完整生命周期 |
| 插件引擎 | WebView 支持 | 已实现 | HTML/CSS/JS |
| 插件引擎 | JS Bridge API | 已实现 | 170+ API |
| 插件引擎 | 网络请求 API | 已实现 | HTTP GET/POST/PUT/DELETE |
| 插件引擎 | 文件系统 API | 已实现 | 读写/删除/复制/移动 |
| 插件引擎 | 存储 API | 已实现 | KV + JSON + 批量操作 |
| 插件引擎 | 数据持久化 | 已实现 | 独立 data/ 目录 |
| 插件管理 | 导入插件 | 已实现 | TPK 文件导入 |
| 插件管理 | 导出插件 | 已实现 | ZIP 包导出 |
| 插件管理 | 批量导入 | 已实现 | 多个 TPK 文件 |
| 插件管理 | 插件集导入 | 已实现 | ZIP 批量导入 |
| 插件管理 | 分类管理 | 已实现 | 增删改分类（单个/批量） |
| 插件管理 | 签名验证 | 已实现 | SHA-256 TOFU 校验 |
| 插件管理 | 插件说明 | 已实现 | notice 字段展示 |
| 插件仓库 | GitHub 集成 | 已实现 | 官方仓库与第三方源 |
| 插件权限 | 权限声明 | 已实现 | plugin.json 声明 |
| 插件权限 | 权限检查 | 已实现 | 启动前检查 |
| 插件权限 | 权限请求 | 已实现 | 分组请求 |
| 插件权限 | 权限状态 | 已实现 | 可视化状态 |
| 插件权限 | 持久化授权 | 已实现 | 状态持久化 |
| 插件权限 | Material 3 弹窗 | 已实现 | 统一风格 |
| 插件权限 | Shizuku/Dhizuku 支持 | 已实现 | 官方 API |
| 文档系统 | 文档中心 | 已实现 | 集中文档管理 |
| 开发工具 | 原生插件向导 | 已实现 | Kotlin/Java 插件 |
| 开发工具 | Web 插件向导 | 已实现 | Web 插件创建 |
| 开发工具 | CUI 终端向导 | 已实现 | CUI 插件创建 |
| 开发工具 | 代码编辑器 | 已实现 | Sora Editor 引擎 |
| 开发工具 | 语法高亮 | 已实现 | 30+ 语言 |
| 开发工具 | 编辑器主题 | 已实现 | 28+ 主题 |
| 开发工具 | 模板导出 | 已实现 | 导出模板文档 |
| 文件管理 | 双窗格浏览 | 已实现 | 独立浏览栈、可调比例 |
| 文件管理 | 归档就地浏览 | 已实现 | zip / 7z / tar 及外层压缩 |
| 文件管理 | 压缩与解压 | 已实现 | zip AES-256、分卷 |
| 文件管理 | 递归搜索 | 已实现 | 正则、内容匹配、体积过滤 |
| 文件管理 | 图片查看器 | 已实现 | 缩放、翻页、GIF |
| 文件管理 | APK 信息页 | 已实现 | 权限、签名、安装、分享 |
| 文件管理 | 并排对比 | 已实现 | 两个文件或两个文件夹 |
| 文件管理 | 打开方式 | 已实现 | 内置 / 插件 / 系统建议 / 全部，按后缀设默认 |
| 文件管理 | 书签与历史 | 已实现 | 抽屉与侧栏 |
| 文件管理 | 深链定位 | 已实现 | `uin://files` |
| UI 个性化 | 颜色配置 | 已实现 | 38+ 颜色可调 |
| UI 个性化 | 颜色选择器 | 已实现 | 可视化取色器 + RGB/Alpha |
| UI 个性化 | 渐变背景 | 已实现 | 单色/多色渐变 + 方向设置 |
| UI 个性化 | 圆角配置 | 已实现 | 7 种圆角选项 |
| UI 个性化 | 尺寸配置 | 已实现 | 按钮/间距/图标 |
| UI 个性化 | 字体配置 | 已实现 | 字体大小/粗细 |
| UI 个性化 | 导入/导出 | 已实现 | 配置备份 |
| 桌面小部件 | 3x3 小部件 | 已实现 | 显示 9 个插件 |
| 桌面小部件 | 1x1 快捷方式 | 已实现 | 单个插件快捷方式 |

---

## 技术栈详情

| 技术 | 版本 | 用途 |
|------|------|------|
| Kotlin | 2.4.21 | 主要开发语言 |
| Jetpack Compose | BOM 2026.09.00 | 声明式 UI 框架（foundation / ui / animation 1.12.1） |
| Compose Material 3 | 1.4.0 | Material 3 组件 |
| Android SDK | API 37 | Android 框架 |
| OkHttp | 4.12.0 | HTTP 客户端 |
| Sora Editor | 0.24.7 | 代码编辑器 |
| Coil | 3.6.3 | 图片加载（图片查看器、插件图标） |
| Apache Commons Compress | 1.28.0 | 归档读写主引擎（zip / 7z / tar 及各外层压缩格式） |
| zip4j | 2.11.6 | zip 加密读写、Zip64、分卷 |
| xz | 1.10 | xz / lzma / 7z-LZMA 后端 |
| zstd-jni | 1.5.7-21 | zstd 解压后端 |
| brotli dec | 0.1.2 | brotli 解压后端 |

---

## 快速开始

### 安装应用

1. 从 Releases 下载最新 APK
2. 在设备上启用「允许安装未知来源应用」
3. 安装 APK

### 首次启动

1. 应用启动时显示存储权限说明弹窗
2. 点击「去授权」授予存储权限
3. 首次启动显示引导页
4. 阅读引导内容后点击「开始体验」

### 使用终端

1. 点击底部「开发」标签
2. 点击「打开终端」启动终端
3. 首次启动离线解压内置 rootfs 就绪（随应用内置，无需联网）

常用终端命令：

```bash
# 更新软件源
apt update

# 安装 Python
apt install python3

# 安装 git
apt install git

# 安装 Node.js
apt install nodejs
```

### 使用文件管理器

1. 点击底部「文件」标签
2. 左右窗格各自浏览；拖动中间分隔条调整比例
3. 长按条目进入选择模式，或打开长按菜单执行复制 / 移动 / 压缩 / 打开方式等操作
4. 长按文件 →「打开方式」可选择内置能力、系统应用或已安装插件

### 安装插件

方式一：从仓库安装

1. 点击底部「仓库」标签
2. 浏览可用插件
3. 点击「安装」按钮

方式二：本地导入

1. 将 .tpk 文件传输到手机
2. 点击底部「管理」→「插件管理」
3. 点击「导入」选择文件

### 创建插件

1. 点击底部「开发」标签
2. 点击「创建插件」
3. 选择前端类型（原生 UI / 纯 WebView / WebView + 后端 / CUI 终端）
4. 按照向导填写插件信息
5. 点击「完成」生成项目文件

### 使用代码编辑器

1. 在插件创建向导中进入代码编辑器，或从文件管理器「打开方式」进入
2. 左侧边栏显示项目文件列表
3. 点击文件进行编辑
4. 支持 30+ 编程语言语法高亮
5. 支持撤销/重做、查找替换、代码格式化

### 插件数据存储

Web 插件 (JavaScript)：

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
console.log('KV 数量:', stats.kvCount);
console.log('文件数量:', stats.fileCount);
```

原生插件 (Kotlin)：

```kotlin
val pctx = PluginContext(context, pluginDir)
pctx.putString("key", "value")
val value = pctx.getString("key")
pctx.writeFile("data.txt", "content")
```

---

## 常见问题

问：如何安装插件？
答：三种方式：从「仓库」页面直接安装、导入 .tpk 文件、批量导入或插件集导入。

问：Web 插件和原生插件有什么区别？
答：Web 插件使用 HTML/CSS/JS 开发，无需编译，修改后即时生效；原生插件使用 Java 开发，性能更好，但需要编译。

问：如何开发自己的插件？
答：点击底部「开发」→「创建插件」，选择类型后按照向导操作即可。

问：如何自定义 UI 颜色和圆角？
答：在「管理」→「UI 个性化」中，可自定义 38+ 颜色和 7 种圆角大小。

问：文件管理里怎么把文件交给插件打开？
答：长按文件 →「打开方式」→ 在插件区搜索并点选目标插件。文件不会复制到 `.incoming/`，插件通过 `openData.filePath` 直接读取原文件。

问：为什么「打开方式」里没有某个插件？
答：只有已安装、在 `plugin.json` 中声明了 `openWith`、且接受当前文件类型（MIME / 文件）的插件才会出现。

问：插件数据存储在哪里？
答：每个插件的数据存储在 /storage/emulated/0/UIN_Tool/plugins/{pluginId}/data/ 目录，KV 数据存储在 SharedPreferences 中。

问：更新插件会丢失数据吗？
答：不会。更新插件时 data/ 目录自动保留，用户数据不会丢失。

问：权限状态会持久化吗？
答：会。权限授予/封禁状态按插件持久化；「不再提示/不再显示」选择也按插件持久化。

问：Web 插件支持哪些 API？
答：支持 170+ API，涵盖设备信息、传感器、位置、网络、文件系统、存储、权限、UI、剪贴板、振动、通知、事件、系统操作等。

问：代码编辑器支持哪些语言？
答：支持 30+ 编程语言，包括 Java、Kotlin、Python、JavaScript、TypeScript、HTML、CSS、JSON、XML、Markdown、Shell、SQL、Go、Rust、PHP、Ruby、Swift、Dart、Lua、Scala、Perl、Haskell、Elixir、Erlang、Clojure、Groovy、Dockerfile、Makefile、INI、Properties、TOML、YAML 等。

问：如何导出插件数据？
答：Web 插件可使用 UINPlugin.exportData() 导出所有数据为 JSON 格式。

问：如何使用终端功能？
答：点击底部「开发」→「打开终端」。rootfs 随应用内置、首次使用离线解压就绪，无需联网下载。

---

## 开源许可

本项目使用 MIT 许可证。

### 第三方组件许可

- **zip4j**（`net.lingala.zip4j`）— Apache-2.0。
- **7-Zip-JBinding-4Android**（`com.sorrowblue.sevenzipjbinding`，内含 7-Zip 16.02 引擎）—
  引擎 **LGPL-2.1+**（含 unRAR 附加条款），绑定层 Apache-2.0。
  源码获取：Maven Central 坐标 `com.sorrowblue.sevenzipjbinding:7-Zip-JBinding-4Android:16.02-2.03.1`
  的 sources 包；上游 <https://github.com/borisbrodski/sevenzipjbinding>、<https://www.7-zip.org/>。
  本项目未修改该库，`.so` 以独立文件随 APK 分发，可自行替换。
- 其余 Apache-2.0 / MIT 组件（commons-compress、junrar、Coil、apksig 等）见仓库
  `app/build.gradle` 的依赖注释。

---

## 贡献者

| 贡献者 | 角色 | 贡献 |
|--------|------|------|
| UIN Team | 核心开发 | 架构设计、核心功能 |
| 一支电笔 | 功能开发 | 1x1 桌面小部件功能 |
| Termux Team | 上游项目 | 终端模拟器核心引擎 |

---

## 联系方式

| 渠道 | 地址 |
|------|------|
| GitHub | https://github.com/Undefined-Invalid-Null/UIN-Tool |
| 电子邮箱 | undefinedinvalidnull@outlook.com |
| 插件仓库 | https://github.com/UIN-Tool-Plugins |
| QQ 群 | 511875883 |

---

© 2026 UIN Team. All Rights Reserved.
