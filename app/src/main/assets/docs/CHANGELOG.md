# UIN Tool 更新日志

本文档记录了 UIN Tool 的所有重要版本更新和功能变化。

---

## 版本命名规则

- **主版本号**：重大架构变更或不兼容的 API 修改
- **次版本号**：新增功能，向下兼容
- **修订号**：问题修复，向下兼容

---

## [4.4.0] - 2026-07-28

### 🎉 重大更新：插件数据持久化存储 + 权限系统完善

#### 插件数据持久化存储

- **统一数据存储系统**：
  - 基于 SharedPreferences 的键值对存储
  - 支持 String、Int、Long、Boolean、Float、JSON 全类型
  - 每个插件拥有独立的存储空间（`plugin_data_{pluginId}`）
  - 数据版本管理，支持插件升级时数据迁移

- **插件数据目录**：
  - `data/` 目录：插件用户数据存储
  - `cache/` 目录：插件缓存文件存储
  - 更新插件时自动保留 `data/` 目录，用户数据不丢失
  - 卸载插件时自动清理所有数据

- **文件系统 API（Web 插件 JavaScript）**：
  - `writeFile(fileName, content)`：写入文件
  - `readFile(fileName)`：读取文件
  - `deleteFile(fileName)`：删除文件
  - `fileExists(fileName)`：检查文件是否存在
  - `listFiles()`：列出所有文件
  - `getFileSize(fileName)`：获取文件大小
  - `clearCache()`：清理缓存

- **KV 存储 API（Web 插件 JavaScript）**：
  - `setStorage(key, value)` / `getStorage(key)`：字符串存储
  - `setStorageInt(key, value)` / `getStorageInt(key, default)`：整数存储
  - `setStorageBool(key, value)` / `getStorageBool(key, default)`：布尔存储
  - `setStorageFloat(key, value)` / `getStorageFloat(key, default)`：浮点数存储
  - `setStorageJSON(key, json)` / `getStorageJSON(key)`：JSON 存储
  - `removeStorage(key)`：删除键
  - `clearStorage()`：清空所有数据
  - `containsStorageKey(key)`：检查键是否存在
  - `getAllStorage()`：获取所有数据
  - `getStorageKeys()`：获取所有键

- **批量操作 API（Web 插件 JavaScript）**：
  - `setStorageBatch(jsonData)`：批量写入
  - `getStorageBatch(keys)`：批量读取

- **数据统计 API（Web 插件 JavaScript）**：
  - `getStorageStats()`：获取存储统计（KV 数量、文件数量、总大小、缓存大小）
  - `getDataVersion()`：获取数据版本
  - `exportData()`：导出所有数据为 JSON
  - `importData(jsonData)`：从 JSON 导入数据

- **原生插件存储 API（Kotlin）**：
  - `PluginContext.putString/getString`：字符串存储
  - `PluginContext.putInt/getInt`：整数存储
  - `PluginContext.putBoolean/getBoolean`：布尔存储
  - `PluginContext.putJSON/getJSON`：JSON 存储
  - `PluginContext.writeFile/readFile`：文件读写
  - `PluginContext.deletePluginFile`：删除文件
  - `PluginContext.listPluginFiles`：列出文件
  - `PluginContext.getPluginFileSize`：获取文件大小
  - `PluginContext.getStorageStats`：获取存储统计
  - `PluginContext.getPermissionState/setPermissionState`：权限状态管理

- **数据迁移**：
  - 旧版 `web_plugin_` SharedPreferences 数据自动迁移到新系统
  - 首次启动时自动执行迁移，用户无感知

---

#### 权限系统全面完善

- **永久授权状态**：
  - 插件权限状态持久化存储到 SharedPreferences
  - 状态值：0=未授权（显示弹窗），1=已授权（直接进入），2=已拒绝（直接进入）
  - 一次授权，永久生效，不再重复弹窗

- **权限状态管理**：
  - `PluginContext.getPermissionState()`：读取权限状态
  - `PluginContext.setPermissionState(state)`：写入权限状态
  - `PluginContext.shouldShowPermissionDialog()`：检查是否需要弹窗
  - 支持清除权限状态（用于调试或重新授权）

- **权限弹窗优化**：
  - Material Design 3 风格弹窗
  - 显示所有缺失权限及其说明
  - 普通权限和特殊权限分组显示
  - 点击取消直接退出，不进入插件
  - 点击授权请求权限，授予后进入插件

- **权限请求流程**：
  - 状态 1：直接进入插件
  - 状态 0 或 2：检查实际权限，缺失则弹窗
  - 用户点击授权 → 请求权限 → 授予后状态设为 1
  - 用户点击取消 → 状态设为 2，退出插件
  - 部分权限被拒绝 → Toast 提示，状态设为 1，不再弹窗

- **特殊权限处理**：
  - 悬浮窗、修改系统设置等特殊权限
  - 引导用户去系统设置手动开启
  - 特殊权限被拒绝后仍可进入插件（部分功能不可用）

- **权限状态可视化**：
  - 插件管理页面显示权限状态
  - 权限管理页面可查看每个插件的权限详情
  - 一键授权所有权限

---

#### Web 插件 API 大幅扩展

新增 **140+ 个 API** 接口，覆盖以下类别：

| 分类 | API 数量 | 说明 |
|------|---------|------|
| 设备信息 | 16 | 型号、版本、屏幕、内存、CPU、构建信息等 |
| 传感器 | 9 | 加速度计、陀螺仪、光线、距离、磁场、方向、气压、温度、湿度 |
| 位置服务 | 2 | 获取位置、反向地理编码 |
| 屏幕/显示 | 5 | 亮度、自动亮度、显示信息、字体缩放 |
| 系统设置 | 7 | 飞行模式、蓝牙、WiFi、移动数据、位置、NFC、自动旋转、勿扰 |
| 存储信息 | 3 | 总容量、可用容量、使用百分比 |
| 网络数据 | 11 | 网络信息、WiFi信息、信号强度、运营商、IP、速度、Ping |
| 电池 | 5 | 电量、健康、电压、温度、技术 |
| 音频 | 5 | 音量、最大音量、静音、耳机状态 |
| 时间/日期 | 5 | 当前时间、时区、夏令时 |
| 系统语言 | 4 | 系统语言、国家、区域 |
| 应用管理 | 7 | 应用列表、打开应用、应用信息 |
| 文件操作 | 15 | 读写删除、复制移动、目录操作、文件信息 |
| 网络请求 | 5 | GET、POST、PUT、DELETE、下载 |
| 权限 | 3 | 检查、请求、批量请求 |
| UI | 4 | 加载、确认对话框、输入对话框 |
| 剪贴板 | 3 | 复制、获取、清空 |
| 振动 | 2 | 震动、取消 |
| 通知 | 2 | 发送、取消 |
| 系统操作 | 10 | 打开各种设置、全屏、常亮、截图 |
| 事件 | 2 | 发送事件、添加监听 |

---

#### 代码编辑器增强

- **语法高亮修复**：修复 TextMate 语法加载问题
- **主题应用优化**：切换文件时正确应用主题
- **性能优化**：减少不必要的重组

---

#### Bug 修复

- 修复权限弹窗点击取消后仍进入插件的问题
- 修复权限状态持久化失败的问题
- 修复部分权限被拒绝后仍反复弹窗的问题
- 修复 `PluginPermissionManager` 方法签名错误
- 修复 `PluginJSInterface` 缺失 `ping` 等 API 的问题
- 修复 `setStorageBatch` 方法不存在的问题
- 修复权限请求结果回调未正确转发的问题

---

## [4.2.0] - 2026-07-24

### 🎉 重大更新：Termux 后端集成 + 插件开发增强

#### Termux 后端集成

- **插件后端支持**：Web 插件可直接启动 Termux 后端服务
  - 支持 Python、Node.js、PHP 语言后端
  - 支持二进制可执行文件作为后端
  - 后端服务自动启动，用户无感知
  - 后端进程生命周期管理（插件关闭时自动停止）

- **后端通信协议**：
  - HTTP API 通信（无需 WebSocket）
  - 后端必须提供 `/health` 健康检查端点
  - 后端自动分配端口（默认 8000）

- **后端模板**：`python_template.tpk` 模板
  - 使用 Python 内置 `http.server`，无需安装 Flask
  - 兼容 Python 3.14+（移除 `cgi` 模块依赖）
  - 支持计算、记录、查询、系统命令等 API

#### 插件系统增强

- **插件说明功能**：
  - 插件可在 `plugin.json` 中声明 `notice` 字段
  - 首次打开插件时自动显示说明弹窗
  - 用户可选择「不再提示」或「稍后提醒」
  - 插件管理页面可查看完整说明

- **插件创建向导优化**：
  - 统一「创建插件」入口，弹窗选择前端类型
  - 支持原生 UI、纯 WebView、WebView + 后端三种模式
  - 后端选择：Python、Node.js、PHP、二进制文件
  - Web 插件默认生成空白 HTML/CSS/JS 文件
  - 二进制后端支持直接选择可执行文件

#### 开发工具增强

- **导出模板**：新增 `python_template.tpk` 模板
- **代码编辑器**：
  - 原生和 Web 插件都支持代码编辑器
  - 导入已有 Web 项目 (ZIP) 功能

#### 后端管理

- **PluginBackendManager**：统一管理后端进程
  - 进程启动/停止/状态查询
  - 输出监控（stdout/stderr）
  - 健康检查（等待服务就绪）
  - 端口自动分配

---

### 🎨 UI 统一与优化

- 插件说明弹窗使用 Compose `AlertDialog`
- 白色背景，Material 3 按钮样式
- 移除所有 Emoji
- 开发页面按钮颜色统一

---

## [4.1.0] - 2026-07-17

### 🎉 重大更新：代码编辑器升级 (Sora Editor)

#### 代码编辑器全面升级

- **Sora Editor 集成**：采用专业的 Sora Editor 引擎
  - 基于 `io.github.rosemoe:sora-editor` 0.24.4
  - 使用 `AndroidView` 在 Compose 中完美嵌入
  - 支持 30+ 种编程语言语法高亮

- **TextMate 语法高亮**：
  - 支持 Java、Kotlin、Python、JavaScript、TypeScript
  - 支持 HTML、CSS、JSON、XML、Markdown
  - 支持 Shell、SQL、Go、Rust、PHP、Ruby、Swift
  - 支持 Dart、Lua、Scala、Perl、Haskell、Elixir
  - 支持 Erlang、Clojure、Groovy、Dockerfile、Makefile
  - 支持 INI、Properties、TOML、YAML 等 30+ 种语言

- **主题系统**：内置 28+ 种代码编辑器主题
  - 深色主题：dark-plus、dracula、one-dark-pro、material-theme 等
  - 浅色主题：vitesse-light、github-light、solarized-light 等
  - 一键切换主题，实时生效

- **编辑器功能增强**：
  - 行号显示
  - 代码折叠
  - 括号匹配高亮
  - 智能自动缩进
  - 撤销/重做历史
  - 文件树管理（侧边栏）
  - 文件类型图标

---

## [4.0.0] - 2026-07-14

### 🎉 重大重构：Kotlin + Jetpack Compose 全面升级

#### 技术栈全面升级

- **Kotlin 全面迁移**：核心代码从 Java 迁移到 Kotlin
  - 134 个 Kotlin 文件，39 个 Java 文件
  - 利用 Kotlin 空安全、协程、扩展函数等特性
- **Jetpack Compose**：UI 全面迁移到声明式 Compose 框架
  - Compose 2024.09.00 BOM
  - Material 3 设计语言
- **MVVM 架构**：引入 ViewModel + StateFlow 响应式状态管理
- **Repository 模式**：数据层与业务层分离
- **依赖注入**：ServiceLocator 统一管理服务实例

#### UI 全面升级

- **Material 3 设计系统**：全新视觉风格
- **深色模式完善**：完整的深色/浅色主题切换
- **玻璃效果**：毛玻璃质感的 UI 组件
- **完整颜色选择器**：RGB + Alpha 通道独立调节
- **38+ 颜色配置项**：全部颜色可自定义
- **7 种圆角配置**：全面控制 UI 形状

#### 终端功能（基于 Termux）

- **内置 Termux 引擎**：完整集成 Termux 终端模拟器
- **完整 Linux 环境**：APT 包管理器、bash/zsh 等
- **多会话支持**：同时运行多个终端会话
- **多窗口支持**：Android 7.0+ 多窗口/分屏

#### 插件系统增强

- **插件权限系统**：基于 Android 权限模型的插件权限管理
- **插件依赖检查**：自动检查并提示缺失依赖
- **UI 配置导入导出**：支持配置备份和分享
- **完整的备份系统**：备份插件、配置、UI 主题

#### 开发工具增强

- **插件创建向导**：可视化步骤引导创建插件
- **内置代码编辑器**：支持缩放、语法高亮、文件管理
- **Web 项目导入**：支持导入已有的 Web 项目 ZIP 包

#### 性能优化

- 冷启动时间优化 30%
- Compose 重组优化
- WebView 缓存池管理
- 内存占用优化

---

## 升级指南

### 从 v4.2.0 升级到 v4.4.0

v4.4.0 是数据持久化和权限系统完善版本，升级前请注意：

1. **数据自动迁移**：旧版插件数据自动迁移到新存储系统，无需手动操作
2. **权限状态重置**：升级后部分插件的权限状态可能需要重新授权
3. **API 兼容性**：新增 API 完全向后兼容，旧插件无需修改
4. **存储位置变化**：插件数据现在存储在 `data/` 目录下

### 从 v4.1.0 升级到 v4.2.0

v4.2.0 是 Termux 后端集成版本，升级前请注意：

1. **Termux 环境**：需要 Termux 终端环境已安装
2. **Python 后端**：首次使用会自动安装 FastAPI 依赖
3. **插件说明**：旧插件可手动添加 `notice` 字段到 `plugin.json`
4. **兼容性**：完全向下兼容

### 从 v3.x 升级到 v4.0.0

v4.0.0 是一次重大重构，升级前请注意：

1. **UI 完全重写**：基于 Compose 的全新 UI
2. **配置不兼容**：旧版 UI 配置需要重新设置
3. **插件兼容**：插件系统保持向后兼容
4. **终端功能增强**：基于 Termux 的完整终端体验

---

| 项目 | 信息 |
|------|------|
| 文档版本 | 4.4.0 |
| 最后更新 | 2026年7月28日 |
| 对应应用版本 | v4.4.0 (Build 13) |

---

© 2026 UIN Team. All Rights Reserved.
