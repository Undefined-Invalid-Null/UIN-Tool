# UIN Tool

![Version](https://img.shields.io/badge/version-5.7.0-blue)
![Build](https://img.shields.io/badge/build-23-green)
![Android](https://img.shields.io/badge/Android-6.0%2B-brightgreen)
![License](https://img.shields.io/badge/license-MIT-orange)
![Kotlin](https://img.shields.io/badge/Kotlin-2.1.0-purple)
![Compose](https://img.shields.io/badge/Jetpack%20Compose-2024.09.00-blue)

## 应用简介

UIN Tool 是一个基于 Kotlin + Jetpack Compose 重构的 Android 插件化框架应用，允许用户动态加载和运行第三方插件。无论是原生 Java 插件还是 Web 技术栈（HTML/CSS/JS）的插件，都能在 UIN Tool 中无缝运行。它提供了一个完整的插件生态系统，包括插件开发、管理、运行、权限控制以及 **Termux 后端集成**。

### 核心理念

- **开放**：任何人都可以开发插件，支持原生 Java 和 Web 技术栈
- **安全**：插件权限授权，支持签名验证，防止恶意插件
- **高效**：原生性能，Web 插件支持热更新，无需重新编译
- **易用**：可视化开发向导，无需复杂配置即可创建插件
- **灵活**：支持网格/列表视图切换，支持分类管理
- **现代化**：基于 Jetpack Compose 构建，Material 3 设计语言，支持新拟态风格
- **国际化**：支持应用内多语言切换，无需更改系统语言
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

### 当前版本：v5.7.0 (Build 23)

| 项目 | 信息 |
|------|------|
| 版本号 | 5.7.0 |
| 版本代码 | 23 |
| 更新日期 | 2026年8月31日 |
| 最低 Android 版本 | 6.0 (API 23) |
| 目标 Android 版本 | 9 (API 28) |
| 编译 SDK 版本 | 36 (Android 16) |
| 架构 | arm64-v8a |

### 版本历史

| 版本 | 构建号 | 日期 | 亮点 |
|------|--------|------|------|
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

> 详细更新日志请查看 [CHANGELOG](changelog.md)。

---

## 功能列表

### 已实现功能

| 模块 | 功能 | 状态 | 说明 |
|------|------|------|------|
| 启动体验 | SplashActivity | 已实现 | 应用启动页、权限检查 |
| 启动体验 | 引导页系统 | 已实现 | 首次启动引导 |
| 启动体验 | 应用图标快捷方式 | 已实现 | 长按图标快捷菜单 |
| 启动体验 | 权限请求弹窗 | 已实现 | 存储权限说明 |
| 应用更新 | 自动更新检查 | 已实现 | 启动时自动检查 |
| 应用更新 | 强制更新机制 | 已实现 | 支持强制更新 |
| 应用更新 | 版本忽略 | 已实现 | 可忽略版本 |
| 应用更新 | 应用内下载 | 已实现 | 显示下载进度 |
| GitHub 加速 | 镜像站管理 | 已实现 | 独立管理页 |
| GitHub 加速 | 内置镜像站 | 已实现 | 13+ 默认镜像 |
| GitHub 加速 | 自定义镜像 | 已实现 | 手动添加镜像 |
| GitHub 加速 | 导入/导出 | 已实现 | TXT 格式 |
| GitHub 加速 | CDN 加速 | 已实现 | 可开关 |
| 终端 (Termux) | 终端模拟器 | 已实现 | 基于 Termux 适配 |
| 终端 (Termux) | Linux 环境 | 已实现 | APT 包管理 |
| 终端 (Termux) | 多会话支持 | 已实现 | 多个会话同时运行 |
| 终端 (Termux) | 多窗口支持 | 已实现 | Android 7.0+ |
| 终端 (Termux) | 终端设置 | 已实现 | 字体/配色/快捷键 |
| 后端集成 | Python 后端 | 已实现 | 自动启动 Termux Python |
| 后端集成 | Node.js 后端 | 已实现 | 自动启动 Termux Node.js |
| 后端集成 | PHP 后端 | 已实现 | 自动启动 Termux PHP |
| 后端集成 | 二进制后端 | 已实现 | 选择可执行文件 |
| 后端集成 | 健康检查 | 已实现 | /health 端点 |
| 后端集成 | 进程管理 | 已实现 | 自动启动/停止 |
| 插件引擎 | 动态 DEX 加载 | 已实现 | DexClassLoader |
| 插件引擎 | 资源隔离 | 已实现 | 独立 Context |
| 插件引擎 | 生命周期管理 | 已实现 | 完整生命周期 |
| 插件引擎 | WebView 支持 | 已实现 | HTML/CSS/JS |
| 插件引擎 | JS Bridge API | 已实现 | 140+ API |
| 插件引擎 | 网络请求 API | 已实现 | HTTP GET/POST/PUT/DELETE |
| 插件引擎 | 文件系统 API | 已实现 | 读写/删除/复制/移动 |
| 插件引擎 | 存储 API | 已实现 | KV + JSON + 批量操作 |
| 插件引擎 | 数据持久化 | 已实现 | 独立 data/ 目录 |
| 插件管理 | 导入插件 | 已实现 | TPK 文件导入 |
| 插件管理 | 导出插件 | 已实现 | ZIP 包导出 |
| 插件管理 | 批量导入 | 已实现 | 多个 TPK 文件 |
| 插件管理 | 插件集导入 | 已实现 | ZIP 批量导入 |
| 插件管理 | 分类管理 | 已实现 | 增删改分类（单个/批量） |
| 插件管理 | 签名验证 | 已实现 | SHA-256 验证 |
| 插件管理 | 插件说明 | 已实现 | notice 字段展示 |
| 插件仓库 | GitHub 集成 | 已实现 | 官方仓库 |
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
| 开发工具 | 代码编辑器 | 已实现 | Sora Editor 引擎 |
| 开发工具 | 语法高亮 | 已实现 | 30+ 语言 |
| 开发工具 | 编辑器主题 | 已实现 | 28+ 主题 |
| 开发工具 | 模板导出 | 已实现 | 导出模板文档 |
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
| Kotlin | 2.1.0 | 主要开发语言 |
| Jetpack Compose | 2024.09.00 | 声明式 UI 框架 |
| Compose Material 3 | 1.3.0 | Material 3 组件 |
| Compose Navigation | 2.7.7 | 页面导航 |
| Android SDK | API 36 | Android 框架 |
| Kotlin Coroutines | 1.7.3 | 异步编程 |
| OkHttp | 4.12.0 | HTTP 客户端 |
| Retrofit | 2.11.0 | REST API |
| Gson | 2.11.0 | JSON 解析 |
| CommonMark | 0.22.0 | Markdown 渲染 |
| Sora Editor | 0.24.4 | 代码编辑器 |
| Sora Editor TextMate | 0.24.4 | 语法高亮 |
| MultiDex | 2.0.1 | Multi-DEX 支持 |
| NDK | 29.0.14033849 | C/C++ 原生支持 |

---

## 快速开始

### 安装应用

1. 从 Releases 下载最新 APK
2. 在设备上启用「允许安装未知应用」
3. 安装 APK

### 首次启动

1. 应用启动时显示存储权限说明弹窗
2. 点击「去授权」授予存储权限
3. 首次启动显示引导页
4. 阅读引导内容后点击「开始体验」

### 使用终端

1. 点击底部「Dev」标签
2. 点击「打开终端」启动终端
3. 首次启动自动安装 Linux 环境（约 30-60 秒）

常用终端命令：

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

### 安装插件

方式一：从仓库安装

1. 点击底部「Repo」标签
2. 浏览可用插件
3. 点击「安装」按钮

方式二：本地导入

1. 将 .tpk 文件传输到手机
2. 点击底部「管理」>「插件管理」
3. 点击「导入」选择文件

### 创建插件

1. 点击底部「Dev」标签
2. 点击「创建插件」
3. 选择前端类型（原生 UI / 纯 WebView / WebView + 后端）
4. 若选择 WebView + 后端，选择后端语言（Python/Node.js/PHP/二进制）
5. 按向导填写插件信息
6. 点击「完成」生成项目文件

### 使用代码编辑器

1. 在插件创建向导中进入代码编辑器
2. 左侧边栏显示项目文件列表
3. 点击文件进行编辑
4. 支持 30+ 编程语言语法高亮
5. 点击调色板图标切换编辑器主题
6. 支持撤销/重做功能
7. 支持添加/删除文件
8. 点击「完成」保存所有更改

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
答：三种方式：直接从「仓库」页面安装、导入 .tpk 文件、批量导入或插件集导入。

问：Web 插件和原生插件有什么区别？
答：Web 插件使用 HTML/CSS/JS 开发，无需编译，修改即时生效；原生插件使用 Java 开发，性能更好，但需要编译。

问：如何开发自己的插件？
答：点击底部「Dev」>「创建插件」，选择类型按向导操作。

问：如何自定义 UI 颜色和圆角？
答：在「管理」>「UI 个性化」中，可自定义 38+ 颜色和 7 种圆角大小。

问：插件数据存储在哪里？
答：每个插件的数据存储在 /storage/emulated/0/UIN_Tool/plugins/{pluginId}/data/ 目录，KV 数据存储在 SharedPreferences 中。

问：更新插件会丢失数据吗？
答：不会。更新插件时 data/ 目录自动保留，用户数据不会丢失。

问：权限状态会持久化吗？
答：会。授权后权限状态永久保存，下次打开不再重复弹窗。

问：Web 插件支持哪些 API？
答：支持 140+ API，涵盖设备信息、传感器、位置、网络、文件系统、存储、权限、UI、剪贴板、振动、通知、系统操作等。

问：代码编辑器支持哪些语言？
答：支持 30+ 编程语言，包括 Java、Kotlin、Python、JavaScript、TypeScript、HTML、CSS、JSON、XML、Markdown、Shell、SQL、Go、Rust、PHP、Ruby、Swift、Dart、Lua、Scala、Perl、Haskell、Elixir、Erlang、Clojure、Groovy、Dockerfile、Makefile、INI、Properties、TOML、YAML 等。

问：如何导出插件数据？
答：Web 插件可使用 UINPlugin.exportData() 导出所有数据为 JSON 格式。

问：如何使用终端功能？
答：点击底部「Dev」>「打开终端」；Linux 环境首次使用时自动安装。

---

## 开源许可

本项目使用 MIT 许可证。

---

## 贡献者

| 贡献者 | 角色 | 贡献 |
|--------|------|------|
| UIN Team | 核心开发 | 架构设计、核心功能 |
| Yi Zhi Dian Bi | 功能开发 | 1x1 桌面小部件功能 |
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
