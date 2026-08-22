# UIN Tool 更新日志

本文档记录了 UIN Tool 的所有重要版本更新和功能变化。

---

## 版本命名规则

- **主版本号**：重大架构变更或不兼容的 API 修改
- **次版本号**：新增功能，向下兼容
- **修订号**：问题修复，向下兼容

---

## [5.5.0] - 2026-08-22

> 本版本聚焦**原生插件兼容性修复、实体 Termux 架构升级与开发向导补全**：修复原生插件因接口默认方法字节码不兼容导致的崩溃（`-Xjvm-default=all` + 重建 host-sdk.jar + 重新打包 plugin.dex）、CUI 插件 proot 容器内启动脚本路径错误、崩溃后日志页不跳转、MarkdownRenderer `appendReplacement` 崩溃、DocViewerScreen 闪退等问题；补齐剪贴板伪权限声明（`READ_CLIPBOARD`/`WRITE_CLIPBOARD`）；**开发向导补全所有插件字段**并同步更新内置文档。新增**打开插件前的权限提示**、**实体 Termux 共享 Supervisor**（单个常驻进程管理所有插件后端，冷启动省掉 ~5s 初始化开销）、**启动环境自动安装**（bootstrap + Alpine 后台预装）、**4 个静态桌面快捷方式**替换动态插件快捷方式。

### 🐛 原生插件崩溃修复

- 原生插件 `AbstractMethodError` 崩溃：插件 `plugin.dex` 按旧 host-sdk.jar 编译（无 `onHostEvent` 等默认方法），运行时 Kotlin 默认方法以抽象方法形式存在 → 调用崩溃。修复：build.gradle 开启 `-Xjvm-default=all`（真实 JVM default 方法）、重建 host-sdk.jar（Java 镜像，默认方法签名与运行时一致）、按模板重新打包 plugin.dex、宿主增加反射守卫
- CUI 插件 proot 容器 `scripts/script.py` 找不到：proot 会将工作目录重置为 `/root`，启动命令现改为 `cd /plugins/<id> && <启动命令>`

### 🔐 剪贴板权限（伪权限）

- 新增伪权限 `READ_CLIPBOARD` / `WRITE_CLIPBOARD`：仅需在 plugin.json `permissions` 中声明即生效，无需运行时授权
- 开发向导「权限」弹窗已包含这两个伪权限；权限管理页对伪权限只做「声明 + 封禁」控制

### 🧙 开发向导字段补全

- 后端：`backendStartCommand`、`backendTimeout`、`backendHealthCheck`
- 外部内容接收（openWith）：开关 + 接收者名称 + MIME 类型 + 文本/链接/文件接收开关
- 权限：弹窗多选已覆盖全部常用权限（含伪权限）
- 插件说明（notice）保留
- **字段精简（v5.5.0 后期）**：不再提供「资源限制（最大内存/最长 CPU 时间/最大并发任务数）、依赖项、API 级别、后端保活」等输入项（不写向导生成的 plugin.json，见 12.3）；**修复代码编辑器始终显示默认 MainPlugin.java**——主类名变更后同步重新生成入口文件

### ⚖️ 打开插件前的权限提示

- **Web 插件**（有/无后端均可）：打开前弹窗列出**尚未授予**的权限，提供「确定」「不再提示」「管理权限」——「管理权限」直接跳转到该插件的权限管理页
- **原生插件**：每次打开都提示所需（声明）权限，提供「确定」「不再显示」——**先弹窗再打开插件**（原生权限由系统强制，应用层不拦截/不封禁）
- 权限管理页仅管理 **Web 插件** 权限（原生/CUI 插件不再显示）
- 权限中文显示名完善：如 `android.permission.READ_EXTERNAL_STORAGE` → 读取外部存储、`WRITE_EXTERNAL_STORAGE` → 写入外部存储
- 权限提示弹窗改用**宿主统一风格**（`UnifiedDialog`）渲染，与其它系统弹窗视觉一致
- 权限管理页移除**刷新按钮**，列表在授权/撤销后自动刷新

### 📥 保留会话单窗口

- 「关闭时保留会话」开关默认**开启**：默认启用单窗口去重（共享端口模式 + Web 插件时，同一插件**只保留一个后台窗口**——重复打开时把已有窗口带到前台，不再多开实例，多任务窗口仅显示一个）
- 用户在「开发工具」中显式关闭后，Web / CUI 插件每次打开独立实例（支持多开）

### 🧹 实体 Termux 后端自动回收

- 实体 Termux 的进程由 com.termux 自己的 UID 启动，宿主跨应用沙箱无法直接 kill；现改为**共享 supervisor 统一管理**：supervisor 每轮通过 `kill -0 $pid` 检查各插件进程是否存活，死了直接清理；空闲回收通过 `idle/<key>.start` 记录启动时间戳 + `idle/<key>` 超时分钟数判断（0=无限），超时即递归 SIGKILL 杀掉后端进程树，**不依赖插件实现 `/stop`**
- 空闲回收时长支持**自定义任意分钟数**（设置页「自定义（分钟）」输入框），也可选预设 3 / 5 / 10 / 15 分钟（默认 5）或「**无限**」（永不自动回收，仅主动停止时结束；不写 idle 文件）
- 宿主不再轮询判空闲，只做端口探测与状态清理；supervisor 负责实际杀进程
- 配合「后端运行设置」中的空闲回收超时，用户退出插件满设定时长后后端自动被杀，无需手动进 Termux 敲命令

### 🧾 权限按钮文案改短

- 权限管理页「一键授权/撤销所有权限」按钮改为短文案「**全部授权**」/「**全部撤销**」（en：`Grant All` / `Revoke All`），布局 `weight(1f)` 下完整显示不截断

### 🧱 插件模板按类型完善

- `README.md.tmpl` 重写为**类型化渲染**：根据插件类型（原生 / Web / Web+后端 / CUI）分别生成对应的目录树、开发指南、打包步骤与打包文件说明，修复原模板中 `{{MAIN_CLASS_PATH}}`/`{{WEB_SECTION}}`/`{{DEVELOPMENT_GUIDE}}` 等从未传入的占位符与 `{{PLUGIN_ID}` 拼写错误
- Web 模板变量补充 `PLUGIN_DESCRIPTION`，空白首页随插件描述渲染

### 📄 文档与兼容

- `permissions` / `dependencies` / MIME 等列表字段**同时兼容逗号分隔字符串与 JSON 数组**两种格式（向导回读 plugin.json 同样复用兼容解析）
- 测试插件 `plugin.json` 精简：移除 `signature`/`updateUrl`/`notice`/`backendRuntime`/`backendPort`/`backendEntry`/`backendPreCommand`/`backendMaxRetries`/`backendLogLevel`/`backendArgs` 等不必要或遗留字段，仅保留真实生效字段；`web_plugin_template` 改为纯前端（无 `plugin.dex`/`src/`）
- 内置文档（README / Help / CHANGELOG）同步更新；版本号升至 **5.5.0（Build 21）**

### 🏗️ 实体 Termux 共享 Supervisor

- 实体 Termux（proot 或本机模式）改用**单个常驻共享 supervisor**：容器/会话只初始化一次，所有插件后端作为 supervisor 的子进程运行，后续插件启动省掉 proot 初始化开销（冷启动约 5s）
- 内置 Termux（alpine，约 2s）保持不变（每插件独立 proot）
- 通信协议（控制目录 `<plugins根>/.uin/`）：`cmd/<key>.cmd`（启动命令）、`pid/<key>`（后端 PID）、`stop/<key>`（停止请求）、`idle/<key>`（空闲分钟数，0=无限）、`idle/<key>.start`（启动时间戳）、`alive`（supervisor 存活标记）、`host_alive`（宿主心跳，每 30s touch，超时 300s supervisor 自退）、`shutdown`（退出标记）、`keep_alive`（后台保活标记）
- proot 启动：`proot-distro login <container> --bind '<plugins根>:/plugins' -- sh -lc 'sh /plugins/.uin/supervisor.sh /plugins'`；本机：`sh '<plugins根>/.uin/supervisor.sh' '<plugins根>'`
- 空闲回收：supervisor 按各插件 `idle/<key>.start` 启动时间戳独立超时递归杀进程树；`kill -0 $pid` 检测进程存活
- 宿主存活期间 supervisor 常驻（即使所有后端回收完）；宿主退出时写 `shutdown` 标记，supervisor 自退（容器随之退出）
- 软件启动时后台自动预热 supervisor（`prewarm`），保存后端设置时同样触发
- 后端设置页新增「共享调度器」状态卡（RUN_COMMAND 权限 + supervisor 存活状态）
- 性能：去掉 probeRealTermux（省 1.5~4s）、轮询间隔 200ms、host_alive 超时 300s；热启动 ~0.5s、温重启 ~2s、冷启动 ~4s

### 🔋 后台保活

- 新增「后台保活」开关（后端设置页 → 实体 Termux 区块）
- 开启后：写入 `keep_alive` 标记，supervisor 不再因宿主进程被杀而退出（仅凭显式 `shutdown` 退出）；配合**电池优化豁免**（`ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`）+ 通知栏保活 + **Shizuku/Dhizuku** 权限保持后台存活
- 显示当前 Shizuku/Dhizuku 权限状态（可用/不可用）

### 🐛 崩溃修复与体验优化

- 修复 MarkdownRenderer `appendReplacement` 崩溃：替换字符串中含 `$` 时被当作组引用导致 `IllegalArgumentException`，三处 `appendReplacement` 全部改用 `Matcher.quoteReplacement()` 转义
- 修复 DocViewerScreen 闪退：`MarkdownRenderer.toHtml()` 异常未捕获导致整个 Activity 崩溃，新增 try-catch 显示错误信息
- 修复 DocViewerScreen 背景色与宿主不一致：CSS body background 由硬编码 `#f5f5f5` 改为 `transparent`，WebView 通过 `UIConfig.getBackgroundColor()` 设置背景色

### 🚀 启动环境自动安装

- 软件启动时后台自动检测并安装 Termux bootstrap + Alpine 容器（仅内置模式），无需用户手动操作
- bootstrap 安装完成后立即解除终端阻塞（`_isEnvironmentInstalling = false`），终端黑屏问题修复
- Alpine 安装完全异步，不阻塞终端创建；安装期间有 Toast 提示
- `ensureAlpine` 加入 `_isAlpineInstalling` 锁，防止并发 restore 导致 "container is busy"
- 安装 bootstrap 后自动写入环境变量文件（`writeEnvironmentToFile`），确保终端 bash 有正确环境

### 📱 静态桌面快捷方式

- 动态插件快捷方式替换为 **4 个常用页面静态快捷方式**：文档（DocBrowserActivity）、终端（TermuxActivity）、后端设置（BackendSettingsActivity）、UI 个性化（UIConfigActivity）
- 移除 `PluginManager` 中动态快捷方式的创建/刷新/删除逻辑，不再因频繁刷新超限

---

## [5.4.0] - 2026-08-10

> 本版本是**主动能力扩展（capability）**：新增**插件接收外部内容**（系统「分享 / 用其他应用打开」→ 中转页选择插件）与**插件多开**（同一插件同时运行多个独立实例），并新增「用…打开」中转页搜索与插件管理页「新增桌面快捷方式」入口。

### 📥 插件接收外部内容（Intent 中转，openWith）

- 新增**向外开放通道**：系统/其它应用「分享」或「用其他应用打开」的**文本、链接、文件**可发送给 UIN Tool，中转页列出所有声明 `openWith` 且匹配的插件，由用户选择交给哪个插件处理
- `plugin.json` 新增 **`openWith`** 字段：`enabled` / `label`（中转页显示名）/ `mimeTypes`（支持的 MIME，支持 `text/*`、`*/*` 等通配）/ `acceptText` / `acceptUrl` / `acceptFile`（分别开关文本、链接、文件三类）
- 支持**单文件、多文件（`SEND_MULTIPLE`）**接收，文件自动复制进插件 `<插件目录>/.incoming/`，供插件后端（proot 容器中挂载的 `/plugins/<id>/.incoming`）直接读取
- **Web 插件**读取方式：宿主注入 `window.UINOpenData` / `window.getOpenData()`，亦可用 `UINPlugin.getOpenData()`（返回 JSON 字符串 `{}`，含 `kind`/`type`/`mime`/`url`/`text`/`uri`/`name`/`filePath`/`files` 等字段）
- **原生插件**读取方式：`onHostEvent("host.open", bundle)` 携带 `instanceId` / `openDataJson`
- 仅 1 个匹配插件时**自动打开**；无匹配时中转页提示；中转页支持**搜索**（按显示名 / 插件 ID / 描述过滤）
- 声明 `openWith` 的插件会在系统分享面板中以「UIN Tool」入口出现（`ACTION_SEND` / `SEND_MULTIPLE` / `VIEW` + `text/*`、`*/*`、通用 MIME）

### 🧩 插件多开（Multi-Instance）

- **Web / CUI 插件默认支持多开**：每次打开都启动新实例，实例间页面、JS 接口、后端相互隔离
- **原生插件多开**：默认单实例（多次打开复用同一实例），开发工具页开启「原生插件多开（实验性）」后每次新建独立 `PluginInterface` 实例与 View
- 每个实例拥有全局唯一 **`instanceId`**，宿主层以「实例键」（`pluginId:instanceId`）隔离生命周期回调、WebView 缓存、原生插件实例与后端
- **后端与实例隔离**：默认多实例复用同一后端进程（共享端口）；「开发工具」的「每实例独立后端端口」开启后，每个实例独享后端进程与端口（`startBackendInstance` 按实例键启动），互不干扰

### 🔎 中转页搜索

- 「用…打开」中转页新增**搜索框**：按插件显示名 / 插件 ID / 描述实时过滤候选插件，方便插件较多时快速定位

### ➕ 插件管理页新增快捷方式

- 插件管理页列表项的**启动按钮改为「新增桌面快捷方式」**（图标 `＋`），点击直接为该插件创建桌面快捷方式，无需进入详情再操作

### 📦 版本升级

- 版本号升级至 **5.4.0（Build 20）**

---

## [5.3.0] - 2026-08-08

> 本版本是一次**全面完善（refinement）**：统一 UI 组件体系并落地玻璃效果、完善颜色选择器（可视化取色器 + 夜间模式）、增强插件分类管理、新增 Shizuku / Dhizuku 权限支持，并核实修复了 UI 个性化页面的全部功能。

### 🎨 新增渐变背景特效

- 新增**全局渐变背景**：可在「UI 个性化」→「效果」页开启/关闭，**所有页面统一生效**
- 支持**单选（单色渐变）/ 多选（多色渐变）**两种模式，多选可选 2-6 色，可添加/删除/修改
- 支持**渐变方向设置**：可分别指定「**起始方向**」与「**结束方向**」（6 方向可选）
- **默认单色渐变（按主题自适应）**：浅色 `#FFC4D6DF` / 深色 `#FF4C4F51`，方向**右下 → 左上**；旧版「多色三色默认」自动迁移，用户自定义配置不受影响
- 配置随保存持久化，**支持导出/导入**（含 `color`/`color_dark` 字段）与「恢复默认」
- 渐变绘制在主题根布局，**顶部标题栏跟随渐变背景**，深浅色主题自动跟随

### 🪟 玻璃效果调高透明度

- 玻璃卡片/玻璃背景透明度整体调高，玻璃质感更明显

### 🎨 统一 UI 组件体系（全面落地）

- `Unified*` 组件成为**唯一实现源**：统一按钮/卡片/输入框/文本/开关/标签/图标按钮/列表项/进度条/对话框体系等
- `UIComponents` 重构为**薄委托层**，全部屏幕（20+）迁移直接使用 `Unified*` 组件，API 完全兼容
- 统一卡片（Glass）/输入框/对话框支持**玻璃效果**：半透明背景、无边框、无阴影、跟随主题

### 🎨 颜色选择器完善

- 新增**可视化取色器**（色相条 + 饱和度/亮度面板，点击/拖动任意取色）与**十六进制输入框**，与 RGB/Alpha 实时同步
- 弹窗配色**跟随深浅色主题**（夜间模式），保留滑块与预设色板

### 🧩 插件管理与分类

- 插件详情页与导出/删除操作栏新增「**更换分类**」，支持单个/批量修改，可选已有分类或自定义
- 分类筛选栏支持**横向滑动**；插件项**长按**弹出详情弹窗（信息、文件结构、plugin.json 原文），可直接**更换分类 / 卸载**
- 复选框**仅在选择模式下显示**，顶栏新增选择模式开关；**批量删除修复**：支持一次删除全部选中插件（带数量确认框）

### 🎨 插件列表滚动条（移除）

- 工具页插件列表与插件管理页列表**移除右侧竖向滚动条**，列表项**原样滚动**、无动画位移

### 🪟 页面滑动切换动画不再渐暗

- `slide_in/out_left/right.xml` 全移除 alpha 淡入/淡出，新旧页面滑入/滑出**全程保持明亮**，仅保留位移与缩放

### 🪟 底部导航悬浮样式 / 向导操作栏 / 按钮描边

- 底部导航改为**悬浮样式**：卡片背景、圆角与阴影，覆盖在内容之上不裁切；点击**按压缩放**、选中图标放大、无涟漪
- 开发向导「下一步」操作栏改为**悬浮式纯透明**：去除固定高度/卡片底色/阴影，紧贴按钮；内容区**全高滚动**
- 描边按钮（Outlined）新增显式 1dp 主题描边，白色/透明背景按钮轮廓清晰

### 🪟 弹窗背景与主背景完全一致

- 弹窗（统一对话框/加载/底部/更新/颜色选择器/Alert）背景与主页面**同款渐变、完全不透明**，不再透出背后组件
- 原 Material3 `AlertDialog` 统一替换为 `UnifiedAlertDialog`；透明文字按钮加 1dp 主题描边（`UnifiedDialogTextButton`）

### 🎨 UI 个性化页面优化

- 顶部标签栏跟随背景色并移除底部描边线
- 渐变「模式 / 起始 / 结束方向」改为**下拉框**，全部渐变配置合并为一张卡片
- 代码编辑器文件列表背景跟随主背景
- **功能核实修复**：涟漪开关真实生效、状态栏/导航栏颜色可自定义、深色主色（`primary_dark`）接入主题

### 🔐 权限管理增强 + 状态自动刷新

- 新增 **Shizuku** 与 **Dhizuku** 权限支持（完全采用官方 API）
- 权限勾选状态**自动刷新**（回调/授权监听/返回页面触发），不再需手动下拉

### 🏗️ 代码结构优化

- 镜像常量、文件工具、大小格式化收敛到单一实现；仓储命名统一（`BackupRepository` → `IBackupRepository`）

### 📦 版本升级

- 版本号升级至 **5.3.0（Build 19）**

---

## [5.2.0] - 2026-08-06

### 📦 打包逻辑完善（打包所有内容）

- `JavaToDexCompiler.packageTpk` 重写：不再按类型只挑目录打包，改为递归整包项目目录（`web/`、`scripts/`、`scripts/backend/server.py`、`start.sh`、`res/`、`src/` 及任意资源全部打入 TPK），跳过隐藏文件与 `.tpk` 输出物，避免重复条目
- 显式添加 `plugin.json`、`icon.png`、`README.md`、原生占位/真实 `plugin.dex`（会优先识别真实 DEX 的 `dex\n` magic）
- Web 插件无 `web/index.html` 时仍写入默认页兜底

### 🔄 更新逻辑完善

- 静默更新（每天一次）：新增 `KEY_LAST_UPDATE_CHECK`（epoch day）与 `get/setLastUpdateCheckDay`、`get/setLastChangelog`，`MainContent` 顶部 `LaunchedEffect` 每天只触发一次后台检查，有新版本且未被忽略时弹出更新框
- 新增共享组件 `ui/components/UpdateContent.kt`（`ReleaseChangelog` Markdown 渲染 + `UpdateDialog` 弹窗 + `UpdateContent` 主体），Splash 开屏与管理页「检查更新」共用同一 UI
- 管理页「检查更新」卡片由纯文本 ConfirmDialog（只显示前 200 字、无 Markdown）改为完整 Markdown 的 `UpdateDialog`
- 版本更新引导页：`isVersionUpdate` 且有变更日志时使用全屏 Markdown 页面（`VersionUpdateScreen`），与弹窗共用 `ReleaseChangelog` 渲染

### 🚀 内置 Termux 装 Alpine 提速

- `ProotContainerManager.ensureAlpine()` 删除联网的 `pkg install proot-distro -y`（首装变慢的主要原因），现在只做存在性检查；首次只剩 `proot-distro restore`（解压约 19MB rootfs）一次性开销
- 若确实发现 proot-distro 缺失，只在日志/状态提示，restore 以清晰报错结束，不再静默联网

### 🧩 开发向导完善

- 打包完成不再自动退出：底部按钮打包前显示「Package」(打包)，成功后变为「Finish」(完成)，点击才退出
- 创建插件配置页补齐所有字段（最低宿主版本、API 级别、分类、更新地址、依赖项），新增权限多选（37 个权限 chip）
- `plugin.json` 编辑弹窗使用新 `JsonSyntaxHighlighter`（`VisualTransformation`）实现 JSON 语法高亮
- 配置字段右侧的信息图标改为统一总览：各字段旁的说明图标已移除，仅保留标题行右侧的总览按钮（弹窗完整介绍所有字段）

### 🖥️ 代码编辑器

- 文件树 `FileTreeItem` 改用 `combinedClickable`：长按弹出锚定菜单（查看属性 / 重命名）
- 属性弹窗显示文件名、类型、行数、字符数；重命名弹窗校验（非空、不重复），跨 `files`/`contents`/`currentFile` 同步重命名并提示成功/失败

### 🧾 插件管理页

- 点击插件卡片打开新的滚动详情对话框（`PluginDetailDialog`）：
  - **plugin.json 字段**：ID、版本、最低宿主版本、API 级别、名称、作者、描述、分类、界面类型、入口、主类、更新地址、插件说明、依赖、权限、启动命令
  - **文件结构**：插件目录内文件树（目录/文件带大小）
  - **plugin.json 原文**：从磁盘读取并格式化（失败回退 `PluginInfo.toJson()`）
  - 底部显示插件总大小与文件数，可直接运行插件

### ⚙️ 后端设置整理

- 「后端运行设置」从弹窗改为完整页面（`BackendSettingsActivity` + `BackendSettingsScreen`，带返回栏）：实现/环境/容器/空闲回收设置
- 实体 Termux 下新增「初始化命令」卡片，右上角复制图标一键复制；命令逻辑抽到 `BackendConfig.buildRealTermuxSetupCode()`，与插件运行引导共用同一实现
- 管理页卡片排序：插件管理 → 权限 → 开发工具 → 文档 → 备份 → UI 个性化 → 后端设置 → GitHub 加速 → 小组件 → 检查更新
- 开发页移除后端运行设置入口与旧 `BackendSettingsDialog.kt`

---

## [5.1.0] - 2026-08-06

### ⚙️ 后端运行架构重构（核心）

**新增全局后端运行设置：**
- 新增「后端运行设置」（`BackendConfig` + 开发页/管理页设置弹窗），全局统一控制所有后端插件的运行环境，持久化于 `uin_backend_prefs`：
  - **内置 Termux**（默认）：使用应用内置的精简 Termux，强制走 Proot 共享 Alpine 容器（`alpine`）
  - **实体 Termux**（`com.termux`）：通过 `RUN_COMMAND` 拉起外部 Termux，可再选 Termux 原生环境或 Proot 容器（容器名可配置，默认 `alpine`）
- 新增**空闲自动回收**：后端空闲超过可配置时间（默认 5 分钟，可设 3/5/10/15）自动停止，活动请求会刷新计时

**后端启动命令统一（`backendStartCommand`）：**
- 移除旧式按语言解释器启动（python/node/php/… + `backendPort`/`backendEntry`/`backendPreCommand`），全部统一为 `backend = "other"` + `backendStartCommand` 单一路径
- 插件打开后宿主执行 `sh -lc` 启动脚本，环境变量（`$PORT`、`$PLUGIN_ID`、`$PLUGIN_DIR`、`$WORK_DIR` 等）内联注入
- 旧式后端在加载时自动迁移（内存中合成启动命令），无需改动已发布插件
- **移除启动前命令（`backendPreCommand`）弹窗流程**：删除「现在运行/稍后/取消」询问对话框与 `PreCommandResultReceiver`，`pre_cmd_done` 标记不再使用

**实体 Termux 支持：**
- 新增 `RealTermuxRuntime` 封装 `com.termux.app.RunCommandService` 的 `RUN_COMMAND` 意图，新增 `com.termux.permission.RUN_COMMAND` 权限声明与 `com.termux` 包探测
- 启动失败时自动探测并给出引导：`allow-external-apps=true`、`termux-setup-storage`、`proot-distro install`、RUN_COMMAND 权限授予说明
- 实体 Termux 进程无法被宿主终止，后端停止改为调用约定的 HTTP `/stop` 接口优雅退出

### 🖥️ CUI 终端启动优化

- 内置 Termux：直接前台启动全屏 `TermuxActivity`（`EXTRA_SESSION_ACTION = SWITCH_TO_NEW_SESSION_AND_DONT_OPEN_ACTIVITY`），不再依赖悬浮窗权限
- 实体 Termux：通过 `RUN_COMMAND` 创建会话后拉起 `com.termux` 全屏终端
- 两路径均改为纯淡入转场（`overridePendingTransition(R.anim.fade_in, 0)`），修复交叉淡入淡出期间露出系统桌面的空档期

### 🧰 开发工具整合

- 「运行日志」与「开发者选项」合并为独立的「开发工具」页面（`DevToolsActivity`/`DevToolsScreen`），从管理页单一菜单进入
- 管理页移除「运行日志」「开发者选项」两个入口，新增「开发工具」与「后端运行设置」入口
- 崩溃后自动跳转到「开发工具」页面展示崩溃日志（原跳转日志页逻辑迁移至新页面）

### 🛠️ 插件开发完善

- web + 后端插件向导不再生成 `web/script.js`，改为生成脚本内联的 `web/index.html`（`simple_index.html.tmpl`）
- 后端模板改为统一生成 `scripts/start.sh`（启动命令）+ `scripts/backend/server.py`（读 `$PORT` + `/health`、`/stop` 端点），不再按语言生成不同后端入口
- `plugin.json` 编辑对话框补全后端字段（`backend="other"`、`backendStartCommand`、`backendStartEntry`、`backendAutoStart`、`backendTimeout`、`backendHealthCheck`），`applyPluginJson` 同步读回
- 开发页移除按语言选择后端的对话框，「Web UI + 后端」统一进入向导填写启动命令

---

## [5.0.0] - 2026-08-05

### 🌐 全量国际化（i18n）

- 全应用硬编码中文文案迁移至 string 资源：默认英文（en）+ 完整简体中文（zh-rCN），移除日文资源
- 覆盖 2600+ 字符串键，涉及全部屏幕：主界面、插件管理、权限、仓库、日志、备份、镜像、文档/帮助、开发向导、代码编辑器、桌面小部件等
- 修复资源键兼容性问题：数字开头键、Java 保留字键、撇号转义（aapt2 flatten）、缺失 Activity 标签等

### 🎨 动态主题引擎

- 新增 JSON 动态主题引擎：`UINToolTheme` 读取 UIConfig 配色即时生效，支持深色模式联动
- 插件 WebView 注入 `--uin-*` CSS 变量，主题色随应用同步
- 圆角半径、字号全部改为配置驱动，覆盖所有页面
- 管理底部导航栏与系统状态栏颜色跟随主题，修复紫色主题残留

### 🧭 底部导航重构

- 底部导航改为自绘（Row + clickable(indication=null)），规避 material3 NavigationBar / LocalIndication 版本差异
- 顶部细边框沿圆角收口绘制，指示图标改为终端提示符 `>_`
- 标签页切换加入水平滑动 + 淡入过渡动画（AnimatedContent）

### 🔄 下拉刷新统一

- 移除全部页面右上角刷新图标，统一改为 Material 3 PullToRefreshBox 下拉刷新
- 空列表状态同样支持下拉；指示器使用主题色并固定居中
- 8 个刷新页显示"上次更新时间"，时间淡入并在 1 秒后自动淡出

### ✨ UI 优化与交互

- 插件列表增删动画（animateItem + key）
- 仓库/插件管理加载改骨架屏（呼吸闪烁占位）
- 镜像管理"加镜像"改为右下角 FAB
- Toast 统一改为 Material Snackbar（全局宿主 + 生命周期感知）
- 玻璃特效落地到全部卡片与弹窗（UI 个性化开关控制）
- 全局 Activity 切换为「**滑动 + 淡入淡出**」：旧屏滑出并淡出、新屏滑入并淡入（`slide_in/out_*` 含位移与透明度动画）
- 页面滑动切换动画**新增缩放效果**：旧屏滑出时**缩小**（scale 1.0→0.9）、新屏滑入时**放大**（scale 0.9→1.0），pivot 取视图中心，切换更有前后层次感（位移 + 缩放 + 透明度复合动画）
- 管理页顶部栏统一为 `ManageTopAppBar`，颜色跟随主题与页面背景

### 🛠️ 其他优化

- 深浅色配色板可同时编辑混用，应用启动即加载主题
- 开屏恢复透明背景图标、加快启动（700ms 淡入缩放动画）
- 修复管理页返回/保存按钮点击无效、镜像弹窗紫色背景等问题

---

## [4.5.0] - 2026-08-03

### 🐧 Proot 容器运行时 + 自定义后端

#### Proot 容器运行时（`backendRuntime: "proot"`）

- 插件后端可在**共享 Alpine 容器**中运行，与宿主机环境隔离
- 首次使用时自动初始化 Termux 环境，并通过 `proot-distro restore` 从 `assets/alpine.tar.xz` 离线恢复 Alpine 容器
- `assets/alpine.tar.xz` 为 `proot-distro backup alpine` 生成的备份，内置预装 Python 等依赖环境
- 容器内可直接使用 `apk add` 安装依赖，不污染宿主 Termux 环境
- 插件目录自动绑定到容器内 `/plugins/<pluginId>`，入口文件在容器中直接可见
- 容器内 `127.0.0.1:PORT` 与宿主机互通，后端 API 调用无需额外配置
- 环境流水线：Termux 就绪 → Alpine 就绪 → 启动前命令 → 启动后端

#### 启动前命令（`backendPreCommand`）

- 插件可配置一条启动前命令，在 Termux 终端中执行（如安装依赖、初始化数据）
- 首次打开时弹窗选择：「现在运行」「稍后」「取消」
- 命令执行成功（exit 0）一次后永久跳过（`pre_cmd_done` 标记，存于 `plugin_data_<id>`）
- 执行失败时自动回到插件页并提示退出码与错误信息

#### 自定义后端模式（`backend: "other"`）

- 宿主不自动启动后端进程，由启动前命令在终端中自行启动服务
- 通过 TCP 端口轮询（200ms）判定后端就绪，超时放宽至 90s+ 以兼容容器冷启动
- 支持无端口插件（`backendPort: 0`），以 pre-command 会话存活即运行中

#### 后端连接提速

- 三处 OkHttpClient（PluginBackendManager / PluginHostActivity / PluginJSInterface）增加 `.proxy(Proxy.NO_PROXY)`，避免系统代理劫持 loopback 流量
- `waitForReady` 去掉 1s 硬编码延迟，改为 200ms TCP 端口探测 + HTTP 健康检查轮询
- 停止后端时按进程组 `SIGKILL`（`Os.kill(-pid, SIGKILL)`），确保 proot 子进程一并退出

#### 其他修复

- 修复引导页（Onboarding）闪烁与跳过后再弹出的问题：去除 SplashActivity 双重导航路径，统一由 Compose 驱动，并修复权限弹窗首帧闪现

#### 向导与文档

- 插件向导支持「后端运行环境」（Termux 本机 / Proot 容器）与「启动前命令」配置
- 后端选择新增「自定义（手动启动）」类型
- **CUI 终端插件**：创建插件新增「CUI 终端（命令行界面）」类型，4 步向导自动生成 `scripts/script.py` 示例脚本与启动命令配置
- 更新日志、帮助文档、README 同步更新

### 🧰 模板导出重构 + 开发工具优化

#### 插件模板导出重构

- 导出模板改为直接从 `assets/test_plugins/` 导出 **7 个打包好的插件**（cuitest / othertest / termux / allapi / storage / NativeTestPlugin / web_plugin_template），作为可导入的现成模板
- 导出时自动生成 `README.txt`，列出每个模板文件的用途与导入使用方法
- 清理了 assets 中原有的零散插件模板（`templates/`、`test_plugins/` 旧文件、根目录 `template.tpk`），统一由内置打包插件接管

#### 导出流程修复

- 修复「导出中...」按钮一直卡住的问题：导出改为在后台线程执行，完成后回到主线程复位状态
- Toast 显示线程安全化：后台线程调用 Toast 不再崩溃（自动切回主线程显示）

#### UI 优化

- 创建插件相关按钮改为纯主题色（`PrimaryButton`），移除渐变色样式

#### 构建优化

- 精简 `proguard-rules.pro`：仅保留可能被 R8 删除的重要代码——**空壳插件宿主占位实现**（`com.UIN.Tool.plugin.**` / `com.UIN.Tool.core.plugin.**`，即 DexClassLoader 加载外部 dex 所依赖的接口与宿主类）、`@JavascriptInterface` 方法、插件 JSON 模型等；移除「保留全部 androidx/compose/带空构造类」等过宽规则，缩小 release 包体积

---

## [4.4.4] - 2026-08-02

### 🎉 插件弹窗系统统一 + 交互修复

#### 插件弹窗系统统一

- 插件弹窗全部改用应用内置的 Compose 统一对话框组件（`UnifiedDialog` / `UnifiedConfirmDialog` / `UnifiedInfoDialog`）
- 移除旧的 `UnifiedViewDialog` 自定义弹窗实现
- JS `alert` / `confirm` / 确认对话框 / 输入对话框 / 特殊权限弹窗统一走同一套弹窗组件

#### 弹窗排队机制

- 多个弹窗请求按顺序排队显示，不再互相覆盖
- 上一个弹窗关闭后自动展示下一个
- 弹窗请求支持回调方式：`showConfirmDialog(title, message, callbackId)`、`showPromptDialog(title, hint, callbackId)`

#### 交互修复

- 修复插件页面无法滚动/点击的问题：对话框覆盖层默认隐藏，仅在弹窗显示时显示
- 修复确认对话框不显示的问题
- 修复截图功能无法保存的问题（改用视图绘制方式捕获画面）
- 修复截图时无存储权限静默失败的问题

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

### 从 v5.0.0 升级到 v5.1.0

v5.1.0 重构了后端运行架构，升级前请注意：

1. **后端运行环境改为全局配置**：旧插件中的 `backendRuntime`/`backendPort`/`backendEntry`/`backendBinary` 等字段不再用于新式启动流程，加载时自动迁移为 `backendStartCommand`（内存中完成，不写回插件文件），无需改动插件
2. **启动前命令弹窗移除**：旧版「现在运行/稍后/取消」的 `backendPreCommand` 弹窗流程已删除，`pre_cmd_done` 标记不再使用
3. **运行环境切换**：如需使用实体 Termux 运行后端，需在「开发」/「管理」页的「后端运行设置」中切换，并按引导开启 `allow-external-apps`、执行 `termux-setup-storage`、授予 RUN_COMMAND 权限
4. **后端停止方式变化**：实体 Termux 进程无法被宿主终止，后端需实现 `/stop` 端点以优雅退出
5. **新增空闲自动回收**：后端空闲超过设定时间（默认 5 分钟）会自动停止

### 从 v4.4.0 升级到 v4.4.4

v4.4.4 是插件弹窗与交互修复版本，升级前请注意：

1. **弹窗外观变化**：插件弹窗统一改用内置 Compose 对话框组件，样式与交互略有调整
2. **弹窗排队**：多个弹窗请求将依次显示，不再互相覆盖
3. **API 新增**：新增 `showConfirmDialog`、`showPromptDialog` 回调式弹窗 API
4. **兼容性**：完全向后兼容，插件无需修改

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
| 文档版本 | 5.5.0 |
| 最后更新 | 2026年8月22日 |
| 对应应用版本 | v5.5.0 (Build 21) |

---

© 2026 UIN Team. All Rights Reserved.
