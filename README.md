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
- **统一打开方式**：每个后缀可设默认打开器；内置能力（可收拢网格）/ 系统应用 / 已安装插件三档候选，插件区支持搜索

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

---

## 文件管理（双窗格）

底栏「文件」是内置文件管理器：左右两个窗格各自独立浏览，复制与移动在两窗之间流转。

| 能力 | 说明 |
|------|------|
| **双窗格** | 每个窗格有自己的浏览栈；窗格比例可拖动，也可在设置里锁定或每次恢复默认 |
| **选择与操作** | 长按进入选择模式；复制 / 移动 / 重命名 / 压缩 / 解压 / 分享 / 隐藏 / 添加书签 / 属性 / 删除 |
| **归档** | zip / 7z / tar 及 gzip、bzip2、xz、zstd 外层包就地浏览；可写归档支持写回，只读归档会标注；支持 zip AES-256 密码与分卷 |
| **搜索** | 递归搜索、正则、内容匹配、体积过滤；结果可整页展开并「在结果中搜索」 |
| **打开方式** | 内置能力（可收拢网格）/ 系统建议 / 全部三档；每个后缀可设默认打开器；插件区带独立搜索，可把本地文件直接交给插件 |
| **查看器** | 图片查看器（缩放、翻页、GIF）、APK 信息页（权限、签名、安装、分享） |
| **对比** | 选中两个文件或两个文件夹，并排查看差异 |
| **导航** | 书签与历史、常用位置侧栏、`uin://files` 深链定位 |

文件图标按类型着色（图片、视频、音频、文档、代码、归档等各有一套颜色），不再是一排灰色图标。

---

## 版本信息

### 当前版本：v7.0.0 (Build 27)

| 项目 | 信息 |
|------|------|
| 版本号 | 7.0.0 |
| 版本代码 | 27 |
| 更新日期 | 2026年11月19日 |
| 最低 Android 版本 | 6.0 (API 23) |
| 目标 Android 版本 | 9 (API 28) |
| 编译 SDK 版本 | 37 |
| 架构 | arm64-v8a |

> **签名变更（7.0.0 起）**：本版改用新的签名密钥。从旧版覆盖安装会被系统拒绝（提示「应用未安装」或签名不一致），请先卸载旧版再安装；卸载会清除应用数据，请提前备份。

> 详细更新日志请查看 [CHANGELOG](app/src/main/assets/docs/zh-cn/CHANGELOG.md)。

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

### 使用终端

1. 点击底部「开发」标签
2. 点击「打开终端」启动终端
3. 首次启动离线解压内置 rootfs 就绪（随应用内置，无需联网）

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

---

## 常见问题

Q: 如何安装插件？
A: 三种方式：从「仓库」页面直接安装、导入 .tpk 文件、批量导入或插件集导入。

Q: Web 插件和原生插件有什么区别？
A: Web 插件使用 HTML/CSS/JS 开发，无需编译，修改后即时生效；原生插件使用 Java 开发，性能更好，但需要编译。

Q: 如何开发自己的插件？
A: 点击底部「开发」→「创建插件」，选择类型后按照向导操作即可。

Q: 终端功能如何使用？
A: 点击底部「开发」→「打开终端」。rootfs 随应用内置、首次使用离线解压就绪，无需联网下载。

Q: 文件管理里怎么把文件交给插件打开？
A: 长按文件 →「打开方式」→ 在插件区搜索并点选目标插件。文件不会复制到 `.incoming/`，插件通过 `openData.filePath` 直接读取原文件。

Q: 为什么「打开方式」里没有某个插件？
A: 只有已安装、在 `plugin.json` 中声明了 `openWith`、且接受当前文件类型（MIME / 文件）的插件才会出现。可先在「插件管理」确认插件已安装并声明了 `openWith`。

Q: 插件数据存储在哪里？
A: 每个插件的数据存储在 /storage/emulated/0/UIN_Tool/plugins/{pluginId}/data/ 目录下。

Q: 更新插件会丢失数据吗？
A: 不会。更新插件时会自动保留 data/ 目录，用户数据不会丢失。

---

## 开源协议

本项目采用 MIT License 开源协议。

---

## 贡献者名单

| 贡献者 | 角色 | 贡献内容 |
|--------|------|----------|
| UIN Team | 核心开发 | 架构设计、核心功能 |
| 一支电笔 | 功能开发 | 1x1 桌面小部件功能 |

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
