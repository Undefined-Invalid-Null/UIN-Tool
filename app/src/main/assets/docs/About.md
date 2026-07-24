# UIN Tool

![Version](https://img.shields.io/badge/version-4.2.0-blue)
![Build](https://img.shields.io/badge/build-12-green)
![Android](https://img.shields.io/badge/Android-6.0%2B-brightgreen)
![License](https://img.shields.io/badge/license-MIT-orange)
![Kotlin](https://img.shields.io/badge/Kotlin-2.1.0-purple)
![Compose](https://img.shields.io/badge/Jetpack%20Compose-2024.09.00-blue)

## 应用简介

UIN Tool 是一个基于 Kotlin + Jetpack Compose 重构的 Android 插件化框架应用，允许用户动态加载和运行第三方插件。无论是原生 Java 插件还是 Web 技术栈（HTML/CSS/JS）的插件，都能在 UIN Tool 中无缝运行。它提供了一个完整的插件生态系统，包括插件开发、管理、运行、权限控制以及 **Termux 后端集成**等功能。

### 核心理念

- **开放**：任何人都可以开发插件，支持原生 Java 和 Web 技术栈
- **安全**：插件权限独立管理，支持签名验证，防止恶意插件
- **高效**：原生性能，Web 插件支持热更新，无需重新编译
- **易用**：可视化开发向导，无需复杂配置即可创建插件
- **灵活**：支持网格/列表视图切换，支持分类管理
- **现代化**：基于 Jetpack Compose 构建，Material 3 设计语言
- **强大**：内置 Termux 终端环境，支持 Python/Node.js/PHP 后端

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

### 当前版本：v4.2.0 (Build 12)

| 项目 | 信息 |
|------|------|
| 版本号 | 4.2.0 |
| 版本代码 | 12 |
| 更新日期 | 2026年7月24日 |
| 最低 Android 版本 | 6.0 (API 23) |
| 目标 Android 版本 | 14 (API 34) |
| 编译 SDK 版本 | 35 (Android 15) |
| 架构 | arm64-v8a |

### 版本历史

#### v4.2.0 (2026年7月24日) - 🎉 Termux 后端集成

**🎉 重大更新：插件后端支持**

**Termux 后端集成：**
- Web 插件可直接启动 Termux 后端服务（Python/Node.js/PHP/二进制）
- 后端服务自动启动，用户完全无感知
- HTTP API 通信（无需 WebSocket）
- 后端必须提供 `/health` 健康检查端点
- 后端进程生命周期管理（插件关闭时自动停止）
- 端口自动分配（默认 8000）

**后端模板：**
- 新增 `python_template.tpk` 模板
- 使用 Python 内置 `http.server`，无需安装 Flask
- 兼容 Python 3.14+（移除 `cgi` 模块依赖）
- 支持计算、记录、查询、系统命令等 API

**插件说明功能：**
- 插件可在 `plugin.json` 中声明 `notice` 字段
- 首次打开插件时自动显示说明弹窗
- 用户可选择「不再提示」或「稍后提醒」
- 插件管理页面可查看完整说明

**插件创建向导优化：**
- 统一「创建插件」入口，弹窗选择前端类型
- 支持原生 UI、纯 WebView、WebView + 后端三种模式
- 后端选择：Python、Node.js、PHP、二进制文件
- Web 插件默认生成空白 HTML/CSS/JS 文件
- 二进制后端支持直接选择可执行文件
- 向导步骤根据类型动态调整（4-5 步）

**后端管理增强：**
- PluginBackendManager 统一管理后端进程
- 支持多语言解释器路径自动查找
- 输出监控（stdout/stderr）
- 健康检查（等待服务就绪）
- Python 路径优先使用 Termux 的 `python` 命令

**UI 统一：**
- 弹窗统一使用 Compose AlertDialog，白色背景 Material 3 风格
- 开发页面按钮颜色统一
- 移除所有 Emoji

#### v4.1.0 (2026年7月17日) - 🎉 代码编辑器升级

**🎉 重大更新：Sora Editor 集成**

**代码编辑器全面升级：**
- **Sora Editor 集成**：替换原有简陋编辑器，采用专业的 Sora Editor 引擎
- **TextMate 语法高亮**：支持 30+ 种编程语言的语法高亮
- **主题系统**：内置 28+ 种代码编辑器主题（深色/浅色）
- **语言支持**：Java、Kotlin、Python、JavaScript、TypeScript、HTML、CSS、JSON、XML、Markdown、Shell、SQL、Go、Rust、PHP、Ruby、Swift、Dart、Lua、Scala、Perl、Haskell、Elixir、Erlang、Clojure、Groovy、Dockerfile、Makefile、INI、Properties、TOML、YAML 等
- **行号显示**：支持行号显示
- **代码折叠**：支持代码块折叠
- **括号匹配**：自动高亮匹配括号
- **自动缩进**：智能自动缩进
- **撤销/重做**：完整的撤销/重做历史
- **主题切换**：一键切换编辑器主题
- **文件树管理**：侧边栏文件列表，支持添加/删除文件
- **文件图标**：根据文件类型显示不同图标

**其他优化：**
- 修复插件权限管理 UI
- 修复备份恢复功能
- 优化应用启动速度
- 统一 Toast 和弹窗样式

**技术细节：**
- Sora Editor 版本：0.24.4
- 使用 editor-bom 统一管理版本
- language-textmate 模块提供语法高亮

#### v4.0.0 (2026年7月14日) - 🎉 重大重构

**🎉 技术栈全面升级：**

**架构重构：**
- **Kotlin 迁移**：核心代码从 Java 迁移到 Kotlin
- **Jetpack Compose**：UI 全面迁移到声明式 Compose 框架
- **MVVM 架构**：引入 ViewModel + StateFlow 响应式状态管理
- **依赖注入**：ServiceLocator 统一管理服务实例
- **Repository 模式**：数据层与业务层分离

**终端集成（基于 Termux）：**
- **内置 Termux 引擎**：集成 Termux 终端模拟器核心
- **完整 Linux 环境**：支持 APT 包管理、bash/zsh 等 Shell
- **多会话管理**：支持同时运行多个终端会话
- **终端设置**：字体、配色、键盘、快捷键全面可配置

**UI 全面升级：**
- **Material 3 设计**：采用最新 Material Design 规范
- **深色模式支持**：完整的深色/浅色主题切换
- **全新玻璃效果**：毛玻璃质感的 UI 组件
- **完整颜色选择器**：RGB + Alpha 通道独立调节
- **38+ 颜色配置项**：全部颜色可自定义
- **7 种圆角配置**：全面控制 UI 形状

**功能增强：**
- **插件权限系统**：基于 Android 权限模型的插件权限管理
- **插件依赖检查**：自动检查并提示缺失依赖
- **UI 配置导入导出**：支持配置备份和分享
- **完整的备份系统**：备份插件、配置、UI 主题

**开发体验优化：**
- **插件创建向导**：可视化步骤引导创建插件
- **内置代码编辑器**：支持缩放、语法高亮、文件管理
- **Web 项目导入**：支持导入已有的 Web 项目 ZIP 包

**性能优化：**
- 启动速度优化
- 内存占用优化
- 小部件刷新机制优化

#### v3.10.0 (2026年6月9日)

**🎉 重大更新：GitHub 加速 + 强制更新机制**

- GitHub 加速管理页面（13+ 内置镜像站）
- 强制更新机制（Release Tag 支持 `forceFlag`）
- 镜像站导入/导出/测试功能
- CDN 加速开关

#### v3.5.0 (2026年6月8日)

**🐛 修复：** 开发页面按钮显示问题

#### v3.4.0 (2026年6月8日)

**🎉 重大更新：** 应用快捷方式 + 自动更新 + UI 全面优化

#### v3.0.0 (2026年6月8日)

**🎉 重大更新：** 启动体验与文档系统

#### v2.8.0 (2026年6月8日)

**🎉 重大更新：** 应用内更新与版本管理

#### v2.6.0 (2026年6月7日)

**🎉 重大更新：** 插件功能全面增强（网络请求、文件系统、传感器支持）

#### v2.0.0 (2026年6月7日)

**🎉 重大更新：** 插件仓库功能

#### v1.0.0 (2026年6月6日)

首次正式发布

---

## 功能清单

### ✅ 已实现功能

| 模块 | 功能 | 状态 | 说明 |
|------|------|------|------|
| **启动体验** | SplashActivity | ✅ | 应用启动页，权限检查 |
| **启动体验** | 引导页系统 | ✅ | 首次启动引导页 |
| **启动体验** | 应用图标快捷方式 | ✅ | 长按图标快捷菜单 |
| **启动体验** | 权限请求对话框 | ✅ | 存储权限说明 |
| **应用更新** | 自动更新检测 | ✅ | 启动时自动检查 |
| **应用更新** | 强制更新机制 | ✅ | 支持强制更新 |
| **应用更新** | 版本忽略功能 | ✅ | 可忽略版本 |
| **应用更新** | 应用内下载 | ✅ | 显示下载进度 |
| **GitHub 加速** | 镜像站管理 | ✅ | 独立管理页面 |
| **GitHub 加速** | 内置镜像站 | ✅ | 13+ 个默认镜像 |
| **GitHub 加速** | 自定义镜像 | ✅ | 手动添加镜像 |
| **GitHub 加速** | 导入/导出 | ✅ | TXT 格式 |
| **GitHub 加速** | CDN 加速 | ✅ | 可开关 |
| **终端 (Termux)** | 终端模拟器 | ✅ | 基于 Termux 改编 |
| **终端 (Termux)** | Linux 环境 | ✅ | APT 包管理 |
| **终端 (Termux)** | 多会话支持 | ✅ | 同时运行多个会话 |
| **终端 (Termux)** | 多窗口支持 | ✅ | Android 7.0+ |
| **终端 (Termux)** | 终端设置 | ✅ | 字体/配色/快捷键 |
| **后端集成** | Python 后端 | ✅ | 自动启动 Termux Python |
| **后端集成** | Node.js 后端 | ✅ | 自动启动 Termux Node.js |
| **后端集成** | PHP 后端 | ✅ | 自动启动 Termux PHP |
| **后端集成** | 二进制后端 | ✅ | 选择可执行文件 |
| **后端集成** | 健康检查 | ✅ | /health 端点 |
| **后端集成** | 进程管理 | ✅ | 自动启动/停止 |
| **插件引擎** | 动态加载 DEX | ✅ | DexClassLoader |
| **插件引擎** | 资源隔离 | ✅ | 独立 Context |
| **插件引擎** | 生命周期管理 | ✅ | 完整生命周期 |
| **插件引擎** | WebView 支持 | ✅ | HTML/CSS/JS |
| **插件引擎** | JS 桥接 API | ✅ | UINPlugin 对象 |
| **插件引擎** | 网络请求 API | ✅ | HTTP GET/POST |
| **插件引擎** | 文件系统 API | ✅ | 读写删除文件 |
| **插件引擎** | 存储 API | ✅ | localStorage |
| **插件管理** | 导入插件 | ✅ | TPK 文件导入 |
| **插件管理** | 导出插件 | ✅ | ZIP 包导出 |
| **插件管理** | 批量导入 | ✅ | 多个 TPK 文件 |
| **插件管理** | 插件集导入 | ✅ | ZIP 批量导入 |
| **插件管理** | 分类管理 | ✅ | 添加/删除分类 |
| **插件管理** | 签名验证 | ✅ | SHA-256 验证 |
| **插件管理** | 插件说明 | ✅ | notice 字段显示 |
| **插件仓库** | GitHub 集成 | ✅ | 官方仓库 |
| **插件权限** | 权限声明 | ✅ | plugin.json 声明 |
| **插件权限** | 权限检查 | ✅ | 启动前检查 |
| **插件权限** | 权限请求 | ✅ | 分组请求 |
| **插件权限** | 权限状态 | ✅ | 可视化状态 |
| **文档系统** | 文档中心 | ✅ | 集中管理文档 |
| **开发工具** | 原生插件向导 | ✅ | Kotlin/Java 插件 |
| **开发工具** | Web 插件向导 | ✅ | Web 插件创建 |
| **开发工具** | 代码编辑器 | ✅ | Sora Editor 引擎 |
| **开发工具** | 语法高亮 | ✅ | 30+ 种语言 |
| **开发工具** | 编辑器主题 | ✅ | 28+ 种主题 |
| **开发工具** | 模板导出 | ✅ | 导出模板文档 |
| **UI 个性化** | 颜色配置 | ✅ | 38+ 颜色可调 |
| **UI 个性化** | 颜色选择器 | ✅ | RGB + Alpha |
| **UI 个性化** | 圆角配置 | ✅ | 7 种圆角 |
| **UI 个性化** | 尺寸配置 | ✅ | 按钮/间距/图标 |
| **UI 个性化** | 字体配置 | ✅ | 字体大小/加粗 |
| **UI 个性化** | 导入/导出 | ✅ | 配置备份 |
| **桌面小部件** | 3x3 小部件 | ✅ | 显示 9 个插件 |
| **桌面小部件** | 1x1 快捷方式 | ✅ | 单个插件快捷方式 |

---

## 项目结构

```
UIN_Tool/
├── app/
│   ├── src/main/
│   │   ├── java/com/UIN/Tool/
│   │   │   ├── app/                         # Termux 终端核心 (基于 Termux 改编)
│   │   │   │   ├── TermuxActivity.java      # 终端主界面
│   │   │   │   ├── TermuxService.java       # 终端服务
│   │   │   │   ├── TermuxInstaller.java     # 环境安装器
│   │   │   │   └── terminal/                # 终端视图组件
│   │   │   ├── core/                        # 核心功能模块 (Kotlin)
│   │   │   │   ├── compiler/                # Java→DEX 编译
│   │   │   │   ├── di/                      # 依赖注入
│   │   │   │   ├── plugin/                  # 插件核心框架
│   │   │   │   └── update/                  # 更新系统
│   │   │   ├── data/                        # 数据层
│   │   │   ├── domain/                      # 领域层
│   │   │   ├── plugin/                      # 插件框架
│   │   │   │   ├── PluginManager.kt         # 插件管理器
│   │   │   │   ├── PluginHostActivity.kt    # 插件宿主
│   │   │   │   ├── PluginBackendManager.kt  # 后端管理器
│   │   │   │   └── PluginJSInterface.kt     # JS 桥接
│   │   │   ├── ui/                          # UI 界面 (Compose)
│   │   │   │   ├── components/              # 可复用组件
│   │   │   │   ├── screen/                  # 页面 Screen
│   │   │   │   ├── theme/                   # 主题定义
│   │   │   │   └── viewmodel/               # ViewModel
│   │   │   ├── utils/                       # 工具类
│   │   │   └── widget/                      # 桌面小部件
│   │   ├── res/                             # 资源文件
│   │   ├── assets/                          # 静态资源
│   │   ├── cpp/                             # C/C++ 代码
│   │   └── AndroidManifest.xml              # 清单文件
│   └── build.gradle                         # 模块构建文件
├── build.gradle                             # 项目构建文件
└── README.md                                # 项目文档
```

---

## 技术栈详情

| 技术 | 版本 | 用途 |
|------|------|------|
| Kotlin | 2.1.0 | 主要开发语言 |
| Jetpack Compose | 2024.09.00 | 声明式 UI 框架 |
| Compose Material 3 | 1.3.0 | Material 3 组件 |
| Compose Navigation | 2.7.7 | 页面导航 |
| Android SDK | API 35 | Android 框架 |
| Kotlin Coroutines | 1.7.3 | 异步编程 |
| OkHttp | 4.12.0 | HTTP 客户端 |
| Retrofit | 2.11.0 | REST API |
| Gson | 2.11.0 | JSON 解析 |
| CommonMark | 0.22.0 | Markdown 渲染 |
| Sora Editor | 0.24.4 | 代码编辑器 |
| Sora Editor TextMate | 0.24.4 | 语法高亮 |
| MultiDex | 2.0.1 | 多 DEX 支持 |
| NDK | 29.0.14033849 | C/C++ 原生支持 |

---

## 快速开始

### 安装应用

1. 从 [Releases](https://github.com/Undefined-Invalid-Null/UIN-Tool/releases) 下载最新 APK
2. 在设备上启用「允许安装未知来源应用」
3. 安装 APK

### 首次启动

1. 应用启动后显示存储权限说明对话框
2. 点击「去授权」授予存储权限
3. 首次启动显示引导页
4. 阅读引导内容后点击「开始体验」

### 使用终端

1. 点击底部「开发」标签
2. 点击「打开终端」启动终端
3. 首次启动会自动安装 Linux 环境（约 30-60 秒）

**终端常用命令：**

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

**方式一：从仓库安装**
1. 点击底部「仓库」标签
2. 浏览可用插件
3. 点击「安装」按钮

**方式二：本地导入**
1. 将 .tpk 文件传输到手机
2. 点击底部「管理」→「插件管理」
3. 点击「导入」选择文件

### 创建插件

1. 点击底部「开发」标签
2. 点击「创建插件」
3. 选择前端类型（原生 UI / 纯 WebView / WebView + 后端）
4. 如选择 WebView + 后端，选择后端语言（Python/Node.js/PHP/二进制）
5. 按照向导填写插件信息
6. 点击「完成」生成项目文件

### 使用代码编辑器

1. 在插件创建向导中进入代码编辑器
2. 左侧边栏显示项目文件列表
3. 点击文件即可编辑
4. 支持 30+ 种编程语言语法高亮
5. 点击调色板图标可切换编辑器主题
6. 支持撤销/重做功能
7. 支持添加/删除文件
8. 点击「完成」保存所有更改

---

## 开源协议

本项目采用 MIT License 开源协议。

---

## 致谢

### 开源项目依赖

| 项目 | 许可证 | 用途 |
|------|--------|------|
| Android SDK | Apache 2.0 | 基础框架 |
| Jetpack Compose | Apache 2.0 | UI 框架 |
| Material Components | Apache 2.0 | UI 组件库 |
| Kotlin | Apache 2.0 | 编程语言 |
| Coroutines | Apache 2.0 | 异步编程 |
| OkHttp | Apache 2.0 | HTTP 客户端 |
| Retrofit | Apache 2.0 | REST API |
| Gson | Apache 2.0 | JSON 解析 |
| CommonMark | BSD-2-Clause | Markdown 渲染 |
| Sora Editor | GPL-3.0 | 代码编辑器 |

### 🔥 特别致谢：Termux

UIN Tool 的终端功能基于 Termux 项目改编。Termux 是一个功能强大的 Android 终端模拟器和 Linux 环境，由 Termux 团队开发和维护。

我们在 Termux 核心代码的基础上进行了以下适配和增强：
- 集成到 UIN Tool 的插件化框架中
- 优化 UI 交互体验
- 增强与 Compose 界面的协同
- 保持与 Termux 生态的兼容性

**Termux 项目地址：** https://github.com/termux/termux-app

此外感谢这个项目，我们的 Termux 源码及 BootStrap 均由此生成（进行了一些修改）：
https://github.com/robertkirkman/termux-generator

---

## 贡献者名单

| 贡献者 | 角色 | 贡献内容 |
|--------|------|----------|
| UIN Team | 核心开发 | 架构设计、核心功能 |
| 一支电笔 | 功能开发 | 1x1 桌面小部件功能 |
| Termux 团队 | 上游项目 | 终端模拟器核心引擎 |

---

## 联系方式

| 渠道 | 地址 |
|------|------|
| GitHub | https://github.com/Undefined-Invalid-Null/UIN-Tool |
| 电子邮箱 | undefinedinvalidnull@outlook.com |
| 插件仓库 | https://github.com/UIN-Tool-Plugins |
| QQ 群 | 511875883 |

---

## 常见问题

**Q: 如何安装插件？**
A: 三种方式：从「仓库」页面直接安装、导入 .tpk 文件、批量导入或插件集导入。

**Q: Web 插件和原生插件有什么区别？**
A: Web 插件使用 HTML/CSS/JS 开发，无需编译，修改后即时生效；原生插件使用 Java 开发，性能更好，但需要编译。

**Q: 如何开发自己的插件？**
A: 点击底部「开发」→「创建插件」，选择类型后按照向导操作即可。

**Q: 如何自定义 UI 颜色和圆角？**
A: 在「管理」→「UI 个性化」中，可以自定义 38+ 种颜色和 7 种圆角大小。

**Q: 插件说明功能如何使用？**
A: 在 `plugin.json` 中添加 `notice` 字段，首次打开插件时会自动显示说明弹窗，用户可选择不再提示。

**Q: 如何创建带后端的 Web 插件？**
A: 点击「创建插件」→ 选择「Web UI + 后端」→ 选择后端语言（Python/Node.js/PHP/二进制），向导会自动生成对应的后端文件。

**Q: Python 后端需要安装什么依赖？**
A: 使用 Python 内置的 `http.server`，无需安装 Flask 等第三方库，完全兼容 Python 3.14+。

**Q: 代码编辑器支持哪些语言？**
A: 支持 Java、Kotlin、Python、JavaScript、TypeScript、HTML、CSS、JSON、XML、Markdown、Shell、SQL、Go、Rust、PHP、Ruby、Swift、Dart、Lua、Scala、Perl、Haskell、Elixir、Erlang、Clojure、Groovy、Dockerfile、Makefile、INI、Properties、TOML、YAML 等 30+ 种编程语言。

**Q: 代码编辑器如何切换主题？**
A: 点击编辑器右上角的调色板图标，在弹出对话框中选择喜欢的主题即可（共 28+ 种主题）。

**Q: 插件签名验证失败怎么办？**
A: 可以在「管理」→「开发者选项」中开启「忽略签名验证」（仅用于测试）。

**Q: 如何备份插件和数据？**
A: 在「管理」→「备份恢复」中点击「创建备份」。

**Q: 应用崩溃了怎么办？**
A: 应用会自动捕获崩溃日志，下次启动时会自动打开日志查看器。

**Q: GitHub 加速功能如何使用？**
A: 在「管理」→「GitHub 加速」中配置镜像站和 CDN 加速。

**Q: 强制更新是什么？**
A: 当 Release Tag 格式为 {versionCode}-{versionName}-1 时，会强制用户更新，无法跳过。

**Q: 终端功能如何使用？**
A: 点击底部「开发」→「打开终端」，首次使用会自动安装 Linux 环境，安装完成后即可使用。

**Q: 终端如何访问手机文件？**
A: 终端中的 `~/storage/shared/` 目录对应手机共享存储（内部存储根目录）。

---

## 最后更新

本文档最后更新于：**2026年7月24日**

---

© 2026 UIN Team. All Rights Reserved.