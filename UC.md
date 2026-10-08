# UC.md — 变更记录（从 6.1.0 / build25 起）

> 规则：只记「修复」与「新增」；不记编译类小改动；不记新功能自身的修复。
> 标准代码目录：`/storage/emulated/0/AndroidIDEProjects/UIN_Tool/`；本地改完需 `cp` 同步。
> 问题依据：`UP.md`（编号对应 A-xx / B-xx / C-xx / ...）。

---

## 6.1.0 (build25) — 进行中

### [批次1] 启动与权限
- **B-28 / G-17** `SplashActivity.kt`：删除 `hasRequestedPermission` 闩锁（首次拒绝后"允许"按钮永久失效）；新增 `onResume()` 权限复检——已授权则清弹窗并继续导航，未授权且无任何弹窗时重新显示权限说明，修复"去设置返回后永久卡在开屏页"。
- **B-29** `SplashActivity.kt`：自定义危险权限（OPEN_PLUGIN / RUN_COMMAND）申请补到 Android 11+ 路径与「存储已授权的老用户」启动路径（原先仅 API<30 分支申请），并加进程内 `hasRequestedCustomPermission` 防重复。
- **B-07** `UinApplication.kt`：`Logger.init(Constants.LOG_DIR)` 提前到 `onCreate` 开头，使 Application 启动段（崩溃处理、UIConfig、ServiceLocator 等）日志不再丢失；`Logger.init` 自身幂等且初始化失败会留给后续调用重试。
- **C-03** `PluginManager.kt` / `PluginHostActivity.kt` / `MainActivity.kt`：插件权限请求由"假异步"改为真实回执——新增 `pendingPermissionRequests` 表登记 `requestCode → (宿主Activity, 回调)`，由 `dispatchPermissionResult()` 在 `onRequestPermissionsResult` 回填真实授权结果；`requestPluginPermissionsByGroups` 的进度/完成回调改为回执后才触发（含特殊权限时整体恒为未完成）；宿主销毁时 `failPendingPermissionRequests(activity)` 仅中止自己发起的请求。
- **C-03（根因）** `PluginManager.kt`：构造时持 `applicationContext`，`context as? Activity` 恒为 null → 权限弹窗**从未真正发起**；新增 `resolveRequestActivity()` + `onHostActivityResumed/Destroyed`，由 `MainActivity.onResume/onDestroy` 登记前台宿主。
- **B-13 / C-21** `MainActivity.kt`：`onDestroy` 改为仅在 `isFinishing && !isChangingConfigurations` 时才调 `SharedSupervisor.onHostExit()`，修复旋转/系统重建 Activity 误杀 supervisor 与全部插件后端。

### [批次2] 资源与主题 / 界面
- **A-02** `AndroidManifest.xml`：`android:label` 由包名改为 `@string/source_manage`（应为应用名；沿用现有字符串资源，不新增文案）。
- **A-24** `AndroidManifest.xml`：`icon`/`roundIcon` 由不存在的 `@drawable/app_icon*` 改为实际存在的 `@mipmap/ic_launcher` / `@mipmap/ic_launcher_round`。
- **A-03** `res/values/themes.xml`：`windowLightStatusBar=false`，修复浅色状态栏图标在浅色背景上不可见。
- **G-24** `widget/WidgetConfigureActivity.kt`：进程/城市列表 `indexOf` 结果先判 `-1` 再取值，避免越界崩溃。
- **D-08** `data/remote/MirrorManager.kt`：`testMirrorReachable` 的网络探测包进 `withContext(Dispatchers.IO)`。
- **A-01** `ui/screen/help/HelpScreen.kt`：按 `UIConfig.getLanguage()` 读取 `docs/en/Help.md` 或 `docs/zh-cn/Help.md`（缺省回退 zh-cn），并改用 `use {}` 关闭流。
- **F-02 / G-18 / D-04 / D-11** `ui/screen/proot/ContainerManagementScreen.kt`：新增 `loadContainerState()`（IO 线程加载容器列表+默认容器）与 `refreshContainers()`，`LaunchedEffect` 改为异步加载；下载/重置对话框确认按钮**真正接线**（原先构造参数但不赋值 → 点确认无反应）；删除/重置/改名/导入/下载全部移出主线程并在完成后刷新列表；proot 安装按钮移 `Dispatchers.IO`；下载流程接 `ProgressInfo` 进度并做 100ms 节流；重置按钮仅在 `findRootfsAsset != null` 时显示，避免误删导入的容器。
- **A-01/D-01/D-03/D-04** `proot/ProotInstaller.kt`、`proot/RootfsManager.kt`：下载先写 `.tmp` → 校验（`PROOT_MIN_VALID_SIZE` 64KB）→ 原子 `renameTo`，失败清理，残缺文件自愈；新增 `finalizeInstall()`（删旧→改名→全部成功才写就绪标记，返回值不再被忽略）与 `hasEnoughSpace()` 解压前空间预检；`installFromAsset`/`installFromFile` 增加进度回调与 staging 失败清理。

### [批次3] 主线程 / ANR（P0-C）
- **D-02 / D-20** `terminal/SimpleTerminalActivity.kt`：`startNewSession()` 拆为 `buildSessionPlan()`（proot 参数 + `buildInteractiveShell` 内部 chmod/waitFor 与 find 全树扫描）与 `createSession()`，前者跑 `Dispatchers.IO`、后者回主线程，新建会话不再卡顿；命令构建失败改为 Toast 提示（新增字符串 `terminal_session_start_failed`，中英文）；`intentCommand` 改为一次性消费（新增 `activeSessionCommand` 供 `restartSession` 沿用），修复每次新建会话重复执行同一条插件命令。
- **F-03** `ui/screen/manage/PluginManageScreen.kt`：单个/批量导入、插件套装导入、插件集导出、单个/批量卸载、下拉刷新共 7 处 `scope.launch` 改为 `launch(Dispatchers.IO)`（`copyUriToFile`/`installPlugin`/`refreshPlugins`/zip 读写原先全在主线程）。
- **G-05** `ui/screen/backup/BackupScreen.kt`：备份打包与恢复（解压+目录回写）改 `scope.launch(Dispatchers.IO)`；列表加载与下拉刷新移入 IO；进度 UI 沿用既有 `progressValue/progressMessage` 分段更新。
- **G-01** `ui/screen/backup/backup`：恢复解压改为复用 `FileManager.unzipFile()`（`isSafeZipEntry` + canonical 路径双重校验），删除本文件第二套无防护解压实现，失败抛异常走既有错误提示（修复 `../` 写出 tempDir 外）。
- **G-02** `ui/screen/backup/BackupScreen.kt`：备份插件数解析改 `Regex("Backup_(\\d+)_")`，原先 `split("_")` 取倒数第二段拿到的是日期 → 恒为 0/错乱。
- **B-01** `plugin/SharedSupervisor.kt` + `UinApplication.kt`：`killStaleProcesses()` 脚本改为回显命中的 PID——无残留进程时零等待，命中时用 `kill -0` 轮询（最多 6×50ms）替代固定 `Thread.sleep(300)`；整个清理+环境自检移入同一后台线程（保留先清理后安装的顺序），冷启动主线程不再被 `exec`+`waitFor`+`sleep` 阻塞。
- **D-07** `proot/RootfsManager.kt`：`listContainers()` 过滤 `*.staging` 与隐藏目录（残留 staging 不再被当成容器），并增加按容器目录 mtime 失效的结果缓存（安装/删除/重置都会改 mtime），避免每次刷新做 size+count 全树扫描。

### [批次4] 主题与可读性（P1-A）
- **E-01** `ui/components/unified/UnifiedComponents.kt`：输入框 `textStyle` 的 `Color(0xFF333333)` 改为 `MaterialTheme.colorScheme.onSurface`（原为深灰字压深底，深色模式几乎不可读）。
- **E-14** `utils/MarkdownRenderer.kt`：`getCss()` 24 处与 `getEmptyHtml()` 3 处浅色硬编码全部改为按 `UIConfig.shouldUseDarkTheme()` 选取的双套色值（正文/标题/目录/代码块/引用条/表格边框与斑马纹/分隔线/链接/空文档卡片），深色模式下不再黑字浅底。
- **核对结论（不改码）**：E-02（搜索框 `0xFFF5F5F5`、toast `0xFF323232`）与 E-27（`UINCard` 品牌色）在本代码库不成立——`NeuSearchBar`/`UnifiedCard`/`NeuCard` 已分别走 `colorScheme`/`AppColors`，全库无 `brandPrimary`、无对应 toast 组合；E-15 的 `Text("UIN Tool")`、`18%` 硬编码列宽同样不存在。E-10（三套色源收敛）、A-35（View/Compose 深色开关分裂）属结构性重构，留待决策。

### [批次5] 插件体系（P1-B）
- **C-02** `plugin/PluginHostActivity.kt`：`onResume/onPause` 改传 `currentInstanceKey`（原先传 `currentPluginId`，与 `onPluginResume/Pause(instanceKey)` 必然 miss → WebView `onResume/pauseTimers`、resume/pause JS 事件、原生 `onResume/onPause` 从未执行）。
- **C-26** `plugin/PluginManager.kt`：`refreshPlugins` 防抖由「窗口内直接 return 丢弃」改为「pending 标记 + 到期必刷」（Handler 延迟补跑，避开 API29 的 `hasCallbacks`），安装/卸载后紧跟的刷新不再被吞；原扫描逻辑抽为 `performRefreshPlugins()`。
- **C-14** `plugin/PluginHostActivity.kt`：新增 `normalizeBackendPath()`——path 必须以单个 `/` 开头，拒绝 `@`、`//`、`:`、`\`、`..` 与控制字符（query/fragment 中允许 `:`），不合法直接回调错误，堵住 `@evil.com/` 让 OkHttp 解析到外部 host 绕过 INTERNET 能力校验的出站绕过。
- **C-15** `plugin/PluginJSInterface.kt`：`openUrl`（含 `callHost("openUrl")`）加 scheme 白名单 `http/https/mailto/tel`（新增 `ALLOWED_URL_SCHEMES`），拦截 `intent://`（可构造任意显式组件+extras）、`file://`、`market://`、`javascript:`。
- **C-17** `plugin/PluginJSInterface.kt`：`sendEvent` 的事件名改走既有 `jsString()`，新增 `toDetailJs()`——`data` 为合法 JSON 对象/数组字面量才原样注入，已加引号的保持原样，其余按字符串字面量 `JSONObject.quote` 转义；修复含引号/换行即脚本破损、事件静默丢失。
- **C-06** `plugin/PluginJSInterface.kt`：8 处手拼 JSON 收口——7 处 `{"error":"${e.message}"}` 改走既有 `errJson()`，1 处权限回调改 `JSONObject().apply{...}.toString()`，消息含引号/换行不再产出非法 JSON。
- **C-12** `plugin/PluginManager.kt` + `UinApplication.kt`：`retainedWebViews` 由无界 static map 改为上限 4 的 FIFO 缓存（淘汰即 destroy，条目只含宿主已销毁的 WebView）；`UinApplication.onTrimMemory` 在 `TRIM_MEMORY_BACKGROUND` 及以上调用 `clearRetainedWebViews()`，内存吃紧时立即释放数十 MB WebView。

### [批次6] 数据与备份（P1-C）
- **G-03** `data/repository/BackupRepositoryImpl.kt`：恢复流程改状态机回滚——新增 `swapped` 标记，被顶替的 `oldDir` 保留到「刷新插件列表 + 清临时文件 + 重载列表」全部成功后才删除；异常时若已顶替且旧目录仍在则删除新目录并把旧目录改名还原（原条件 `stageDir!=null && oldDir!=null` 成功后恒为假，回滚实为死代码）；两处 rename 失败路径也补 `oldDir = null`。
- **G-04 / D-24** `data/local/FileManager.kt`：`zipFileRecursive` 单文件失败改为记日志后**上抛**，`zipDirectory` 由「静默漏文件仍返回 true」变为返回 false。
- **G-04 / D-25** `data/local/FileManager.kt`：`unzipFile` 增加条目数（50 万）与累计解压字节数（4GB）上限，命中即返回 false（原先完全无 zip bomb 限流）。
- **D-28** `data/local/PreferenceManager.kt` + `ConfigRepositoryImpl.kt`：`workFolder`/`viewMode` 的 StateFlow 下沉到 PreferenceManager（唯一写入口），`set*` 同步更新 Flow，仓库只转发；原先仓库构造时读一次 SP 缓存，MainActivity/SplashActivity/SettingsViewModel 各自直写 SP → 仓库 StateFlow 永不更新。

### [批次7] 错误可见性（N-02 / P1-D）
- **F-14 / D-31** `ui/viewmodel/RepoViewModel.kt` + `ui/screen/repo/RepoScreen.kt`：源加载失败、未启用任何源、下载失败、安装失败四类错误原先只写日志 → 空列表毫无提示。`loadPlugins()` 开始即清 `error`、无启用源与异常都写入 `uiState.error`（新增字符串 `no_enabled_sources`、`source_list_load_failed`，中英文）；`RepoScreen` LazyColumn 顶部新增「加载失败」内联卡片（错误详情 + 重试按钮）与「安装失败」内联卡片（错误详情 + 知道了），`RepoViewModel` 新增 `clearInstallError()`。
- **F-14（根因）** `RepoViewModel.kt`：`downloadAndInstall` 末尾无条件 `_downloadProgress.value = DownloadProgress(isDownloading=false)` 把刚写入的失败原因立刻抹掉，且 `downloadFile` 返回 null 分支完全不设 error → 下载/安装失败全程静默；改为只置 `isDownloading=false` 并补 null 分支 error，catch 分支错误消息改用 `download_failed_error` 带上异常原因。
- **C-43** `PluginHostActivity.kt`：后端启动 90s 超时回调原先只把 `isBackendStarting` 置回 false（用户面对完全无提示的卡住），现补 `AppToast.error(backend_start_timed_out)`（带 isDestroyed/isFinishing 保护）。
- **G-20** `ui/screen/proot/ContainerManagementScreen.kt`：下载失败与 proot 安装失败的消息在原有文案后追加异常原因（`"下载失败:xxx (原因)"`）；核对结论：导入/导出/删除/重置/改名/下载本就写 `statusMessage` 并在 UI 显示（`statusMessage` 于 :388 渲染），原 G-20「完全静默」判断仅对这两条成立。

### [批次8] 构建与依赖（低风险项）
- **A-06** `app/build.gradle` + `UinApplication.kt` + `AndroidManifest.xml`：minSdk 23 ≥ 21 走系统原生 multidex，删除 `multiDexEnabled`、`multiDexKeepProguard`、`androidx.multidex:multidex:2.0.1` 依赖、`MultiDex.install()` 与 `attachBaseContext` 覆写、以及并不存在的 `android.permission.MULTI_DEX` 权限；`app/multidex-config.pro` 更名为 `app/app-keep.pro`（仍作为 proguardFiles 规则文件，注释注明其全局 keep 与 A-05 一并决策）。
- **A-07** `app/build.gradle`：`core:1.13.1` 与 `core-ktx:1.12.0` 声明统一为 1.13.1，移除 `resolutionStrategy force "androidx.core:core-ktx:1.13.1"`（声明值与生效值不一致的根源）。
- **A-08** `app/build.gradle`：删除 app 模块 4 个零引用的 `io.noties.markwon:*` 声明（全仓仅 `termux-shared` 自行声明使用，重复打包）。
- **A-09** `app/build.gradle`：删除全仓零引用的 `commons-lang3`、`error_prone_annotations`、`org.brotli:dec`；`asm/asm-commons/asm-tree` 同样零引用但 `libs/host-sdk.jar`（compileOnly，编译器反射加载）可能在运行期用到字节码 API，按 UP.md 建议加注释保留。
- **A-13** `app/build.gradle`：release/debug 的 `getDefaultProguardFile('proguard-android.txt')` 换为 `proguard-android-optimize.txt`（配合 `-dontoptimize` 现状不改变实际行为）。
- **A-23（部分）** `.github/workflows/android-build.yml`：去掉 unit tests 步骤的 `continue-on-error: true`，测试失败开始阻断 CI；lint 步骤的门禁与「补 release 构建 job」分别属 A-18 / A-12，留作决策。

### [批次10] 版本与文档
- `app/build.gradle`：`versionCode 24 → 25`、`versionName "6.0.0" → "6.1.0"`（6.1.0 = build25）。
- 文档同步：`app/src/main/assets/docs/{en,zh-cn}/{Help.md,README.md,About.md,CHANGELOG.md}` 与根 `README.md` 的版本徽章、当前版本、版本表、文档版本/对应应用版本/更新日期全部改为 6.1.0 / Build 25 / 2026-10-05，并在中英文 CHANGELOG 各新增 `[6.1.0]` 分节（按启动与权限、主线程/ANR、主题、插件宿主、数据与备份、错误可见性、构建七类汇总本轮修复）。
- **D-31①** `data/remote/GitHubApiService.kt` + `ui/viewmodel/RepoViewModel.kt`：`fetchAllSourcesPlugins` 逐源失败改为汇总到 `lastFetchFailures`（「显示名: 原因」），`fetchSourcePlugins` 索引失败由「返回空列表吞原因」改为上抛，`loadPlugins()` 成功路径也把失败源写进 `uiState.error`（部分源挂掉时列表照常展示 + 横幅提示，不再只在全挂抛异常时才有提示）。
- **F-07（核对结论，不改码）** `ui/screen/manage/PluginManageScreen.kt`：单个/批量导入、插件套装导入导出、单个/批量卸载、权限请求均已走 `showResultDialog` 或 `AppToast.error/warning`；「更新」走仓库侧 `downloadAndInstall`，其失败已由本批次 F-14 内联横幅覆盖。

### [批次8-补] 计划外构建项
- **A-11** `app/build.gradle`：删除 app 模块两处 `externalNativeBuild{ndkBuild}`（顶层 path 指向 `src/main/cpp/Android.mk`，该文件仅一行 `LOCAL_PATH`、不定义任何 module；`defaultConfig` 里的 cFlags 还混入 `-Wl,--gc-sections` 且 abiFilters 重复），`sourceSets.main.jni.srcDirs("src/main/cpp")` 保留覆盖以避免误编 `src/main/jni/Android.mk`(pty)——全仓 `System.loadLibrary` 只有 `libtermux` 与 local-socket 库，原生库实际由 `terminal-emulator`/`termux-shared` 自行构建。

### 决策记录（用户 2026-10-05 拍板）
- **N-01 自检页**：暂不做，6.1.0 维持纯修复。
- **A-05（全局 keep 收敛）**：保持现状，列入待决策。
- **A-18（lint 门禁恢复）**：保持关闭，列入待决策。
- **A-10（zstd `@aar`）、A-14（`-ignorewarnings`）**：本轮不动，列入待决策。
- **批次9 死代码删除**：未获批；用户要求先核验「新拟态组件」与「ViewModel」的真实引用情况（核验结论见对话与下方）。

### [批次9（部分·经用户批准）] 死代码删除 — 仅 ViewModel 批
- **G-22** 删除 `ui/viewmodel/` 下 9 个零引用文件（`BackupViewModel.kt`、`MainViewModel.kt`、`MirrorViewModel.kt`、`NativePluginWizardViewModel.kt`、`PluginPermissionViewModel.kt`、`PluginViewModel.kt`、`SettingsViewModel.kt`、`WebPluginWizardViewModel.kt`、`ViewModelFactory.kt`），目录仅保留唯一在用的 `RepoViewModel.kt`。
- 删除前核验（kt/java/xml/pro/gradle 全扫）：这些类名在 `ui/viewmodel/` 之外引用数均为 **0**（`SettingsViewModel` 仅出现在 `PreferenceManager.kt` 一行注释），无 Manifest/反射/keep 规则依赖，各 `*UiState` 亦零引用。
- 同批核验**未删**项（UP.md 说法与实测不符，留给决策）：`ui/components/UIComponents.kt` 实际被 15+ 屏以 `UIComponents.*` 调用（非零引用）；`unified/NeumorphicComponents.kt` 为半活（`NeuColors`/`NeuStatusIndicator`/`NeuRadio`/`currentNeuIntensity` 在用）；`unified/NeuModifiers.kt` 是新拟态骨架、活；`core/plugin` 组中 `PluginContext`(PluginManager 15 处)、`PluginInterface` 活，不能整组删；`NeumorphicFoundation.kt` 文件不存在（UP.md 笔误）。

### [233轮次·高优先] 错误可见性 / 构建 / 数据 / 插件入口
- **B-31** `SplashActivity.kt` + `plugin/PluginJSInterface.kt`：`SplashActivity` 后端启动失败的原生 `Toast` 改 `AppToast.error()`（跟随主题、切主线程）；`PluginJSInterface` 的 `showToast`/`showToastLong`/`showLoading` 在主线程 handler 为 null 时的兜底由原生 `Toast.makeText` 改为 `AppToast.show/showLong`——原生 Toast 直接在 JS 线程调用会抛异常，插件脚本在该窗口调 toast 必崩。
- **B-64 / F-08** `ui/screen/dev/BasePluginWizardScreen.kt` + `core/compiler/JavaToDexCompiler.kt`：`startCompileAndPackage` 的 `scope.launch` 改 `scope.launch(Dispatchers.IO)`，`JavaToDexCompiler.compileAndPackage` 主体包 `withContext(Dispatchers.IO)`、`packageTpk` 改 `withContext(IO)`——编译期的 dex/zip/进程调用原先跑在主线程，点「编译」即 ANR。F-08 结论：占位包是设计内回退且失败已有 `AppToast.error`+`onError`，仅补齐 IO 调度。
- **F-09** `ui/screen/dev/PluginWizardViewModel.kt`：新增 `pyStr()` 转义 helper，`generateCuiFiles()` 写入 Python 脚本的 `print(Str.get(...))` 改为 `print(${pyStr(Str.get(...))})`——多语言文案（含引号/非 ASCII）原样内联进 Python 源码，运行期 NameError/SyntaxError，脚本必然失败。
- **D-05** `proot/RootfsManager.kt`：`extractTar` 新增 `pendingLinks` 账（`Triple<路径,目标,类型>`），硬链接目标尚未解出时先记账，归档遍历结束后多轮补齐（无进展即 break 并 warning）；原先目标条目排在引用者之后就永久跳过。
- **D-06** `proot/RootfsManager.kt`：导出改为 `ExportItem(file, relPath, flag, linkName)` 按 `NOFOLLOW_LINKS` 真实类型收集（symlink/dir/regular + `hardLinkSeen` 硬链接去重），写出循环按 flag 分支：symlink 用 `Os.lstat` 取目标、`size=0`，dir/硬链接 `size=0` 直接写目录项，regular 照旧拷贝——原先把 symlink 当普通文件读内容（多数为 null），导出的 tar 损坏。
- **D-09** `data/remote/SourceIndexFetcher.kt`：① 缓存改 `ConcurrentHashMap` 并新增 `etags` 表，API/raw 请求真正发送 `If-None-Match`、命中 304 回用缓存（注释一直声称带 ETag 但从未发送）；② 新增 `rateLimitedUntilMs` 冷却期，403/429 记 `Retry-After`/`X-RateLimit-Reset`，冷却期内直接跳过 API（未认证 60 次/小时，原先每刷一次必打满）；③ 阻塞 `execute()` 改 `executeAsync()`（`suspendCancellableCoroutine` + `enqueue`，取消时 `call.cancel()`），raw 路径去掉 `?t=` 时间戳改用 ETag。
- **C-11** `AndroidManifest.xml`：`PluginOpenDispatchActivity`（584 行分享/打开中转页）此前从未注册 → 全仓 0 调用、分享目标功能不可达，连带 `PluginHostActivity.EXTRA_OPEN_DATA` 恒为 null；按 `docs/{en,zh-cn}/README.md` 已宣称的 4 组 filter 注册（`SEND text/*`、`SEND */*`、`SEND_MULTIPLE */*`、`VIEW` 的 7 类 MIME，无 `*/*` 通配），`exported=true` + `launchMode=singleTask` + `Theme.UIN.Tool`。**不加** `OPEN_PLUGIN` 权限：该权限为 dangerous，加了普通分享方看不到本应用，插件宿主入口仍由 OPEN_PLUGIN 保护。
- **A-04** `app/build.gradle` + `keystore.properties`（新建）+ `.gitignore`：debug 签名口令不再明文写在源码，改按「环境变量 `UIN_STORE_PASSWORD`/`UIN_KEY_PASSWORD` > 项目根 `keystore.properties` > `~/.gradle/gradle.properties` 的 `uin.storePassword`」读取，三处全无则抛 `GradleException` 并给出创建指引；`keystore.properties` 已写入本机与设备两份（口令不变），并加入 `.gitignore`。
- **A-16（列决策，不改版本）** `build.gradle`：AGP 8.9.1 官方配 Gradle 8.11.1+，wrapper 为 9.0.0；实测设备端 `.gradle/9.0.0` 状态目录存在、6.0.0→6.1.0 构建完整跑通，任意一侧改版本都要重新下发行版/依赖、有把可用构建改坏的风险 → 加注释保持现状，待拍板（回退 wrapper 8.11.1 或升 AGP）。
- **核对结论（不改码）**：**E-16** 不成立——`utils/SecurityUtils.kt` 只有 64 行 SHA-256 签名校验，全仓无 `AES`/`Cipher`/`SecretKeySpec`/`DEFAULT_KEY`，UP.md 引用的 ECB 代码已不存在；**E-19 / G-08** 已在批次2 修好（HelpScreen 按语言读 `docs/<lang>/Help.md`）；**G-19** 已在批次3 修好；**A-49** 版本号已在批次10 统一到 6.1.0/Build 25，「单一版本源」属重构、留决策。

### [批次11] 构建与 Manifest（中优先）
- **A-17** 根 `build.gradle`：删除 AGP 8 已移除的 `afterEvaluate { lintOptions{} }` 统一关 lint 段（与 `app/build.gradle` 的新 `lint{}` 并存自相矛盾），lint 开关只留 gradle.properties + app 的 `lint{}`。
- **A-19** `gradle.properties`：`minSdkVersion 21 → 23`（termux-shared / terminal-emulator / terminal-view / termux-am-library 都读该属性，与 app 的 `minSdk 23` 统一）。
- **A-20** `app/build.gradle`：修正过期注释「项目为 35 / AGP 8.7.2」→「compileSdk 36 / AGP 8.9.1」。
- **A-22** `.github/workflows/android-build.yml`：两处 `actions/checkout` 加 `lfs: true`；构建前新增「rootfs 体积校验」步骤（`<100MB` 判定为 LFS 指针即 fail），避免启用 LFS 后 CI 打出坏 APK 且不报错。
- **A-36 + A-15** `res/values/attrs.xml` + `app/proguard-rules.pro`：删除零引用的 `declare-styleable CustomCardView`（自声明 `cardCornerRadius` 与 Material 同名属性冲突）与对应「自定义 View 三构造器 keep」规则（app 无自定义 View）。
- **A-42** `AndroidManifest.xml`：补 `<uses-permission>` `com.UIN.Tool.permission.OPEN_PLUGIN` / `RUN_COMMAND`——原先只声明 `<permission>`，SplashActivity 的运行时申请拿不到授权；并修正 RUN_COMMAND 声明处的过期注释（全 Manifest 无组件挂该权限，现作为预留门禁保留，对外命令通道仍是实体 Termux 的 `com.termux.permission.RUN_COMMAND`）。
- **A-43** `AndroidManifest.xml`：删除普通应用永远拿不到/零引用的 `BIND_APPWIDGET`、`READ_LOGS`、`DUMP`、`WRITE_SECURE_SETTINGS`、`SET_ALARM`；**保留** `REORDER_TASKS`（`PluginHostActivity.kt:226` 的 `moveTaskToFront` 需要，UP.md 建议删除会导致 SecurityException）与 `com.termux.permission.RUN_COMMAND`（实体 Termux 后端在用）。
- **A-44 / G-26** `AndroidManifest.xml`：`WidgetProvider` / `Widget1x1Provider` `exported true → false`。自定义 action `REFRESH_WIDGET(_1x1)` 原先暴露给全系统，任意第三方可伪造广播触发刷新；改 false 后系统仍投递 `APPWIDGET_UPDATE`，本应用自身刷新广播（均带 `setPackage`）不受影响（**需实测小部件刷新**）。
- **A-45** `AndroidManifest.xml`：`TrampolineActivity` `exported true → false` 并移除无 `<data>` 约束的 VIEW/BROWSABLE filter——该页可用 `target=plugin&plugin_id=` 直接拉起受 `OPEN_PLUGIN` 保护的 `PluginHostActivity`，构成权限绕过；全仓与文档均无引用，无功能损失（同类 B-40 归入删除待批）。

### [批次12] 启动、生命周期、线程与更新链路（中优先）
- **B-01/B-02/B-03/B-04/B-05/B-08** `UinApplication.kt`：新增 `appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO + 记日志的 CoroutineExceptionHandler)`；原 `onCreate` 主线程串行的「建工作目录/清 .uin/扫 icons 缓存/版本升级清理/清桌面快捷方式/遍历 assets 加载 TextMate 语法与主题」全部移入 `appScope.launch`（保留顺序与逐段 try/catch 归因）；`autoInstallEnvironment` 的裸 `Thread{}` 与内层 `MainScope().launch{}` 一并改 `appScope`（可取消、有异常处理）。
- **B-14** `MainActivity.kt`：`checkPermissions` 的 1s `postDelayed` 改 `lifecycleScope.launch { delay(1000); isDestroyed/isFinishing 判断 }`，`showPermissionDialog()` 入口加销毁保护——销毁后不再弹 `AlertDialog` 造成 `WindowLeaked`。
- **B-15** `MainActivity.kt`：崩溃后 500ms 跳日志页的 `postDelayed` 同样改 `lifecycleScope` + 销毁判断，Activity 销毁后不再 `startActivity`。
- **B-16** `MainActivity.kt`：`closeAllPluginActivities()` 由空实现补全——按 `appTasks` 的 `baseIntent/topActivity` 组件名 `finishAndRemoveTask()` 关闭插件宿主任务，与两处「关闭所有插件页面」日志一致。
- **B-17** `MainActivity.kt`：新增 `uiConfigOrNull()`，`onCreate/onResume` 改为 `UIConfig.isInitialized()` 判空 + `UIConfig.init` 包 try/catch——Application 段 init 失败被吞后不再必抛 `IllegalStateException`。
- **B-18** `MainActivity.kt`：`handleShortcutIntent` 不再打印完整 `intent.extras`（会落共享存储日志），改打印 `data/action` 白名单字段。
- **B-19** `MainActivity.kt`：新增 `applyDeepLink()` 解析 `uin://tools`、`uin://check_update`（Manifest 早已声明但代码从不读 `intent.data`），onCreate/onNewIntent 均接入，深链优先于 shortcut extra。
- **B-21/B-54** `MainActivity.kt` / `SplashActivity.kt` / `AppContainer.kt` / `UpdateChecker.kt` / `UpdateDownloader.kt`：更新检查与下载统一走 `ServiceLocator` 单例（原每次组合/下载 `new`，各自再 new OkHttpClient）；`UpdateChecker`/`UpdateDownloader` 的 OkHttpClient 改为由 `AppContainer` 注入共享——原先两处 `Cache(cacheDir,"okhttp_cache")` 指向同一目录，存在 `CACHE DIRECTORY IS ALREADY IN USE` 风险（顺带使 B-55 的 `getUpdateChecker/getUpdateDownloader` 死入口变为在用）。
- **B-22 / B-62** `UpdateDownloader.kt`：`onStart/onProgress/onSuccess/onFailed` 四个回调统一切主线程（`mainHandler.post`），调用方不再在工作线程写 Compose 状态 / 拉安装。
- **B-23** `MainActivity.kt`：静默更新进度弹窗的「取消」改为 `silentDownloader?.cancelDownload()`（原只把弹窗关掉，后台继续下载、下完还拉起安装）。
- **B-32** `SplashActivity.kt`：开屏更新检查的 5s 超时 `postDelayed` 改为同一 `LaunchedEffect` 内的 `launch { delay(...) }`，组合销毁即取消，不再在销毁后回调 `onNavigate()`。
- **B-34** `SplashActivity.kt`：两处「退出」按钮的 `Process.killProcess + System.exit(1)` 改 `finishAffinity()`（`PermissionExplainDialog` 内补 `LocalContext`）。
- **B-56** `UpdateChecker.kt`：`fetchReleases` 的 `response` 包进 `execute().use{}`——原非 2xx 分支直接 throw 导致连接永不归还连接池。
- **B-57** `UpdateChecker.kt`：镜像探测由「直连 + 各镜像串行 HEAD（每步 8s，最坏 8×N 秒）」改为两轮并发竞速（第一轮：直连+启用镜像；第二轮：默认镜像兜底），单轮耗时 ≈ 单次超时。
- **B-58** `UpdateChecker.kt` / `UpdateDownloader.kt` / `MainActivity.kt` / `SplashActivity.kt`：新增 `clearUpdateListener()` / `clearDownloadListener()`，`checkUpdate()` 用开始时的 listener 快照；MainContent 与开屏组合 `DisposableEffect.onDispose` 清理——Activity/页面销毁后不再回调。
- **B-60** `UpdateDownloader.kt`：`isDownloading` / `downloadThread` 加 `@Volatile`，`cancelDownload()` 立即生效。

### [批次13] 编译、日志与缓存（中优先）
- **B-65** `core/compiler/JavaToDexCompiler.kt`：原生插件编译被禁用时**不再产出占位 TPK/占位 dex**——`compileAndPackage` 走 `onError` 并删除陈旧产物后直接返回；`packageTpk` 遇到「native 且无真实 dex（magic 校验）」时关闭 Zip、删除半成品、回调 `onError` 并返回 false（原先是往包里写 `// 原生插件编译功能暂时禁用` 字节，装上去是个坏插件、用户却以为成功）。
- **B-68 / B-69 / B-70 / B-72** `log/Logger.kt`：日志落盘由「每条日志开→写→关 FileWriter（在调用线程，多为主线程）」改为**单写线程 + 4096 条有界队列 + 长持 FileWriter 批量落盘**（调用线程只入队，队列满丢弃并一次性 logcat 告警）；轮转前 flush+close 旧 writer（原轮转后仍往旧句柄写）；写失败/轮转失败/清理失败不再完全静默，至少 `Log.e` 一次；`clearAllLogs()` 清完后重新 `init()` 出新文件（原只置 `isInitialized=false`，`logFile` 仍指向已删文件 → 之后日志静默丢失）；删除文件末尾重复定义的 `Long.formatFileSize()` 扩展，`Logger.file()` 改调 `utils.formatFileSize(long)`。
- **B-73 / B-74 / B-75** `cache/DiskCache.kt`：内存缓存由无上限 `ConcurrentHashMap` 改为**访问序 LinkedHashMap + 8MB 字节上限**（超出淘汰最久未访问）；`cleanup()` 由「只遍历 .meta」改为**遍历数据文件本身**（缺 meta 回退文件 mtime）并清孤儿 meta，另加 64MB 磁盘总容量上限按最旧淘汰——原先缺 meta 的数据文件永不清理、磁盘无限增长；`put`/`writeMeta` 改「写临时文件 → rename」原子写，避免半截缓存字节。
- **B-77** `constants/AppConstants.kt`：`APP_VERSION`/`APP_VERSION_CODE` 的异常回退由写死 `"1.0.0"`/`0` 改为 `BuildConfig.VERSION_NAME`/`VERSION_CODE`。

### [批次14] 插件体系（中优先）
- **C-04** `PluginJSInterface.checkPermission`：统一要求插件在 plugin.json 声明该权限（原只有 requestPermission 查声明，checkPermission 可盲探宿主已授权的任意权限与撤销态）。
- **C-07** `PluginHostActivity`：`showEnvProgress` 由 Toast 升级为**不可取消的进度对话框**（ProgressDialog，支持更新文案、主线程保护），`dismissEnvProgress` 不再是空函数（9 处调用点此前以为在收起进度条、实际什么都没有）。
- **C-09** `SharedSupervisor`：`killStaleProcesses`/`anyPidsAlive` 的 `waitFor()` 加 5s/3s 超时并 destroy，避免子进程僵死挂住调用线程（原先已在 appScope 后台线程，见 B-01）。
- **C-13** 三个 Web 模板（`web/index.html`、`blank_index.html`、`simple_index.html.tmpl`）加 **CSP meta**：`script-src 'self' 'unsafe-inline'` 禁远程脚本（远程代码可拿到全部 UINPlugin 桥方法），img/media/connect 保持开放以免影响功能；仅影响新生成插件。
- **C-16（部分）** `CAPABILITY_MAP` 补 `ping/resolveDns/dns/dnsLookup → INTERNET`、`sendNotification → POST_NOTIFICATIONS|VIBRATE`，并在 4 个网络探测方法与 `sendNotification` 上接入 `checkCapability`（原先完全无校验：未声明 INTERNET 也能内网探测、可任意发通知）。统一 `PermissionGuard.can()` 与 takeScreenshot/openApp 等仍留决策。
- **C-18** `PluginHostActivity.injectJSInterface`：本 Activity 内**复用已注入的桥**（addJavascriptInterface 跨导航有效），不再每次 onPageFinished remove+new；`PluginJSInterface` 的 OkHttpClient/Cache 提升为**伴生对象共享单例**，根治多实例同用 `okhttp_cache` 目录抛 `CACHE DIRECTORY IS ALREADY IN USE` 与旧 client 无人回收。
- **C-19** 两处 `MainScope().launch` → `lifecycleScope.launch`（随宿主销毁自动取消）。
- **C-20** 等待后台环境安装的自旋 `Thread{sleep(300)}` → `lifecycleScope.launch{delay(300)}`，加 `isFinishing/isDestroyed` 保护。
- **C-22** `killStaleProcesses` 开头判 `keepAliveFile().exists() && isSupervisorAlive()`：**keep_alive 开启时不再杀常驻 supervisor/后端**（原先一重启宿主就掐掉用户开着的常驻服务）。
- **C-24** 清理任务（`checkIdleBackends` 每端口最多 500ms socket connect）移出主 Looper → 专用 `uin-backend-cleanup` HandlerThread；`requestGracefulStop` 的端口探测+等待改为工作线程 + `CountDownLatch(800ms)` 有界等待（原主线程最坏阻塞 ~900ms）。
- **C-25** `minHostVersion` 由误比 `Build.VERSION.SDK_INT` 改比 `BuildConfig.VERSION_CODE`（原先 26>23 会把本该可用的插件全拒装）。
- **C-27** `jsString` 补 U+2028/U+2029 转义（否则 `evaluateJavascript` 整段 SyntaxError、回调永久丢失；PluginHostActivity 侧早已处理）。
- **C-29** `supervisor.sh`：PPID 解析改 `sed 's/^.*) //'` 后取字段，修 comm 含空格/右括号时错位 → `find_children` 漏杀子进程。
- **C-33** 模板 `server.py.tmpl` 的 `/stop` 加 **UIN_TOKEN 校验**（宿主随启动命令注入随机令牌，优雅停止与健康检查带 `X-UIN-Token` 头；未注入时保持兼容），堵掉「任意本机进程/任意网页 no-cors fetch 关掉后端」。
- **C-34 / F-10** `TemplateUtils`：渲染按上下文分档转义（INLINE 去换行控制符 / JAVA 字符串字面量 / HTML 文本 / RAW 文档），`generateJavaCode` 对 CLASS_NAME/PACKAGE_NAME 做合法标识符清洗；`generatePluginJson` 改 `JSONObject` 序列化（原字符串模板手拼、name 含引号即坏 JSON）。配合 CSP 即模板注入面收口。
- **C-39** 消息监听器内层 `MutableList` → `CopyOnWriteArrayList`（分发时注册/注销不再 `ConcurrentModificationException`）。
- **C-40** `PluginContext`：写文件空间预检 `content.length` → `toByteArray().size`（UTF-8 中文原先严重低估）；KV 日志只打 key 与长度、不再打值；插件资源加载失败不再静默降级（记 warn）。
- **C-36** `start.sh.tmpl`：apt 装依赖失败改写 **stderr**，宿主可采集「为什么没起来」。
- **C-44** `PluginJSInterface`：末段 ping/DNS 函数重新缩进回类体；`addEventListener` 不再假装成功，返回 `unsupported_use_window_addEventListener`（真实通道是 sendEvent → window.dispatchEvent）。

### [批次14·续] 插件体系补充（C-31/C-32/C-37/C-41）
- **C-31** `supervisor.sh`：日志按天删之外再按 **1MB 封顶**（超了截尾保留 512KB），修「单日无大小上限」；`PluginBackendManager.perfLog` 同样 1MB 封顶 + **超 7 天自动删除**（原先 perf_*.log 无任何清理，按天堆积）。
- **C-32** 引号补齐：`buildScriptCommand` 的 `cd "$baseDir"`、`ProotContainerManager` 的 `export k='<quoted>'` 与 `cd "$pluginMount"`、supervisor.sh 的 `cat "$CTRL/pid/$k"`、`for f in "$CTRL"/{stop,pid}/*`、`rm -f "$CTRL"/{done,pid}/*`、`ls "$CTRL"/pid` —— 路径含空格不再把命令拆散。
- **C-37** `PluginJSInterface`：新增 `readBodyCapped()` 流式读取并按 **20MB** 封顶（Content-Length 预检 + 流式二次校验），4 个 http* 方法统一改用，替代原先 `body.string()` 整块进内存；`downloadFile` 加 Content-Length 预检 + 流式 **200MB** 上限（超限抛错并回报，不再可能写满磁盘）。HTTP 四段逻辑的去重抽函数留重构清单。
- **C-41** `PluginContext.resolveSafe()` 暴露，`PluginJSInterface.getSafeFile`（14 处调用点不变）改为委托单一实现，消除两份同名校验逻辑（另有 `PluginSharedData` 第三份，留重构清单）。
- **列决策（不改码）**：**C-01** `PluginHostActivity exported=true+OPEN_PLUGIN` 是文档第 12.3 节明确写的「第三方应用直接唤起插件」特性，收紧即砍掉该特性（TrampolineActivity 绕过已由 A-45 堵）；**C-05/C-08/C-35/C-42**（零调用函数/死模板/测试插件资产）属删除类，进待批清单；**C-38** 后端键语义当前由 `PluginHostActivity.backendKey = pluginId` 统一（实例键分支无人走），是否删掉实例键能力列决策。

### [批次15] proot / rootfs / 终端（中优先）
- **D-10** `RootfsManager`：新增 `lockFor(container)`，**deleteContainer / exportContainer 与 install 使用同一把 per-container 锁**（原先只有 install 加锁，导出可读到一半被换掉的 rootfs、删除可删掉正在写入的 staging）。
- **D-12（部分）** `countTarEntries` 的 `catch(_){}` 静默返回 0 改为记 `Logger.e`（调用方据此算进度，0 会让 UI 总数恒空）。
- **D-13** `/tmp`、`/var/tmp` chmod `511(0777)` → `1023(0o1777)` 补 sticky 位；`setFilePermissions` 的 mask 由 `0o777` 改 `0o7777`，不再抹掉 setuid/setgid/sticky（rootfs 特殊权限被改坏）。
- **D-14** 新增 `writeHostFile()`：写 `resolv.conf`/`hosts`/apt/dpkg 配置前判 `Files.isSymbolicLink`（是则删链接本身、绝不跟随写到容器外），各步骤独立 `runCatching` —— 修「dangling symlink → FileNotFoundException → 整个安装在 rename 前失败只剩 staging」。
- **D-15** 新增 `openCompressedStream()`：**按魔数嗅探** xz/gzip/zstd（后缀仅兜底），asset 安装、`countTarEntries`、`installFromFile` 三处统一（原先后缀判断且 asset 与文件两侧条件还不一致）。
- **D-16** `ProotRuntime.executeCommandWithOutput`：stderr 不再并入 stdout（`error` 恒空串修好）+ **60s 超时 `destroyForcibly`**（原 `waitFor()` 无参会永久挂住）+ 输出**流式读取封顶 1MB**（原 `readText()` 循环日志可 OOM）。
- **D-18** `ProotRuntime.executeCommand`：补 `config.environmentVars`（原先完全忽略 → `execCommand(extraEnv)` 静默失效）、cwd 只在宿主真实目录存在时设置（原把容器内路径当宿主 cwd）、补 `PROOT_TMP_DIR`（buildCommand 里算好的 `envWithL2s` 从未被使用）。
- **D-19** `SimpleTerminalActivity` CUI 分支不再手工拼 proot 参数，改走 **`ProotRuntime.buildCommand`**（原漏 FakeSysdata 挂载 → 容器内 `/proc/stat`、`/sys/fs/selinux` 缺失，top/htop/apt 异常）。
- **D-21** 会话名 key 由列表下标改 **`session.mHandle`**（`sessionNames`/`renameHandle`），`closeSession` 一并清理 —— 修删一条后名字错位到别的会话。
- **D-22** `SimpleTerminalActivity` 加 `configChanges`（旋转不再 onDestroy → 会话不再被 SIGKILL）；`keepScreenOn` 改为**按会话存活状态**开关（原先恒亮耗电）。
- **D-23** `TerminalSession.finishIfRunning`：`Os.getpgid + Os.killpg` **杀整个进程组**（原只杀 ptrace 主进程，容器内 shell 及子进程可能成孤儿继续跑），失败回退单杀。
- **D-37** `FakeSysdata`：`/proc/stat` 改按 `availableProcessors()` **动态生成 cpu 行**（原固定 8 核，4 核真机 `nproc=8` 导致 make -j 过度并行）；`/proc/version` 内核串改为 `6.17.0-UIN-Tool` 与 `--kernel-release` 对齐（原 6.2.1 与 6.17.0 打架）。
- **D-38** tar 路径穿越检查补 `File.separator`（`/a/rootfs` 不再放行 `/a/rootfs_evil`）。
- **D-39** `ProotInstaller.getPrimaryAbi()` 返回可空，未知 ABI 不再兜底 `aarch64`（x86/riscv 设备会装错二进制），候选名不再拼 abi。
- **D-40** `PluginInfo.toJson/fromJson` 补 `backendEnv`、`frontendConfig`（原先两字段既不解析也不序列化 → `info.backendEnv` 恒空、插件声明的后端环境变量永久失效），新增 `parseStringMap/parseAnyMap`。
- **D-44（部分）** `"%.1f MB"` / `"%.0f KB"` 补 `Locale.US`（德语等环境输出逗号）；`installingLocks` 为 per-container 有界集合（>不移除，移除会引入「持锁已释放但新线程拿新锁」的竞态，列决策）。
- **核对不改码**：**D-26** 回滚已是 `swapped && oldDir != null` 状态机（G-03 已修，UP 快照过期）；**D-11/D-20/D-24/D-25/D-28/D-31** 此前批次已修。

### [批次16] UI 组件、可访问性与一致性（中优先）
- **E-06** `UnifiedComponents`：`UnifiedCard` / 导航胶囊 / 圆形图标按钮的 `clickable` 补 `role = Role.Button`，自绘 neumorphic `UnifiedSwitch` 补 `role = Role.Switch`（TalkBack 不再朗读成普通容器）。
- **E-07** 自绘 neumorphic `UnifiedSwitch`：由 54x30 的触控面改为 `minimumInteractiveComponentSize()`（≥48dp）承载点击，视觉轨道仍 54x30 居中 —— 触控目标达标且不改变视觉。
- **E-14 附带 / E-18** `PluginWebInterface.showToast` 的非 Activity 兜底分支原先在调用线程直接 `Toast`（Android 11+ 抛异常）→ 改 `AppToast`（内部切主线程）。另核对 `Extensions.kt` 的 `showToast`/`applyPluginIntent` 已在先前批次移除，现存 4 处 showToast 均主线程/`AppToast` ✓。
- **E-17 核对不改**：`SecurityUtils` 里 `readFile`/`countFileLines`/`processStream` 三件套已不存在（验签改为流式哈希），双遍全量 IO 已随重构消失。
- **F-15** 安装单飞：`PluginManager.installPlugin` 增 `installingIds`（`ConcurrentHashMap.newKeySet`）按 pluginId 去重，重复触发直接丢弃并记 warning；`RepoScreen.RepoPluginCard` 增 `installEnabled` 参数，任一插件下载中其它卡片的「安装/更新」按钮一并禁用（VM 层原有的 `isDownloading` 早退保留）。
- **G-12** `LogViewerScreen`：新增 `readTailLines()`（`RandomAccessFile` 读末尾 ≤2MB + `takeLast(2000)` + 截断首行丢弃 + UTF-8），日志与崩溃日志两处不再 `readLines()` 全量进内存。
- **G-14** onboarding 三处硬编码版本串 `"6.0.0"`/`"5.2.0"` → `BuildConfig.VERSION_NAME`。
- **G-15** `OnboardingActivity` onCreate 按 `UIConfig` 设置 `statusBarColor`/`navigationBarColor`（原先固定 XML `primary_dark`，与 Compose 动态配色分裂，与 MainActivity/SplashActivity 对齐）。
- **G-21** 门禁与底层锁统一：`RootfsManager` 锁由 `synchronized(Any)` 换 `ReentrantLock`（`lock.withLock`），新增 `isBusy(name)`/`anyBusy()`；`ContainerManagementScreen` 的导入/下载按钮改 `enabled = !isInstalling || !RootfsManager.anyBusy()`，删除/重置确认先 `isBusy` 拦截并提示（新增字符串 `container_busy` 中英各一条）。
- **G-27** `WidgetConfig.load`：本小部件没有单独配置时回退读 `widget_global_config` 的 `global_plugin_1/2/3` —— 配置页「全局配置」从死开关变为实际生效。

### [批次16·续] 骨架屏、卸载反馈与备份错误内联
- **E-04** `SpecialComponents.SkeletonItem`：渐变 `Brush` 改为 `remember` 单次创建，位移改 `graphicsLayer { translationX }`（内层盒宽为视口 3 倍、位移区间 `[-2w, 0]` 不露白），外层 `BoxWithConstraints` + `clip` 裁切 —— 每帧不再重建 Brush/触发重组（原来在 `background()` 里读动画值重建 `Brush.linearGradient`）。
- **F-07** `PluginManageScreen.uninstallPlugin`：`pluginManager.uninstallPlugin()` 返回 `false`（ID 非法/删除失败）原先完全静默 → 成功才关对话框并提示，失败 `AppToast.error` + `AppLog.e`。
- **G-07** `BackupScreen`：新增 `lastError` 状态，备份/恢复/删除的失败原因**内联卡片**（红色正文 + 「知道了」按钮）保留到下次成功或手动关闭；成功时清空；三处 catch 均写入，Toast 保留。

### [批次17·上] 安全与稳定性（中优先）
- **C-28** `supervisor.sh`：宿主存活检测取 mtime 失败时（`stat` 不可用）原写法 `|| echo 0` → `n-0` 恒超时 → **误判宿主死亡、杀光后端并自杀**；改为取不到 mtime 就跳过本轮（fail-open）+ 日志。`bash -n` 语法核对通过。
- **B-47** `WebPluginProxy.sendEvent`：事件名正则白名单 `^[A-Za-z0-9_][A-Za-z0-9_.-]*$`（原样拼进 JS 字符串/属性名，含引号即注入）；`data` 先用 `JSONTokener` 校验是否合法 JSON 片段，否则 `JSONObject.quote()` 转义。
- **B-48** `PluginWebInterface.openUrl`：scheme 白名单只放行 `http/https`，拦截 `intent://`、`tel:`、`file:`、`javascript:` 等（新增字符串 `blocked_url_scheme` 中英）。
- **B-50** `PluginEventBus`：`debugEnabled` 改 `@Volatile private` + `setDebugEnabled()`；新增 `removePlugin(pluginId)` 清理该插件的监听器与粘性事件，并在 `PluginManager.uninstallPlugin` 成功分支调用（原静态 map 永不清理，`clearAll()` 全仓无调用）。
- **B-52** `UinAccessibilityService`：`TYPES_ALL_MASK` → `TYPE_WINDOW_STATE_CHANGED`、`notificationTimeout` 100ms → 5s，移除逐事件 `Logger.d`（每条 UI 事件都会唤醒服务并写日志）。
- **B-53** `ServiceLocator`：初始化失败不再只置 flag —— 记录 `initError` 与 `appContext`，`checkInitialized()` 先自动重试一次，仍失败抛带 cause 与原始消息的 `IllegalStateException`（原先启动即崩且完全无归因）。
- **核对已修**：**B-08**（`UinApplication.appScope = SupervisorJob+IO`，批次12）、**B-22**（`UpdateDownloader.notify*` 全部 `mainHandler.post`，回调已在主线程）。

### [批次17·中] 数据层与 proot 安全
- **D-27** `PreferenceManager`：①`savePluginPermissions` 改单次 `edit()` 批量提交（原 N 个 key N 次 `apply()`）②4 个 SharedPreferences 改 `by lazy`（原构造即打开，主线程首次访问触发磁盘加载）③`sources_json`/`enabled_source_ids`/`last_repo_refresh_time` 三个裸字符串 key 收敛进 `AppConstants.KEY_*` ④新增 `getStringSafe/getBooleanSafe/getLongSafe/getIntSafe`，**22 处 getter 全部替换**（历史类型不符时返回默认值而不是 `ClassCastException` 崩溃）。
- **D-32** `MirrorManager`：镜像能力位 `MirrorStyle{HOST_REPLACEMENT, PATH_PREFIX, GIT_ONLY, UNKNOWN}`（原按 `contains("ghproxy"/...)` 子串猜模板）—— `gitclone`（仅 git 克隆）不再拼出 `"$mirror/$url"` 的非法 API 地址；未知镜像对 `api.github.com` 回退原地址（不再把「根路径可达」当「可代理 API」）。`forceHttps` 对 **localhost / 192.168.x / 10.x / 172.16-31.x / 127.x / IPv4·IPv6 字面量** 不再强升 https（原局域网 http 镜像必失败）。
- **D-17** `ProotRuntime`：去掉 `--link2symlink`（原先「每条命令都删 `.l2s` + 全盘 `find -delete` 清理」与「继续传该 flag」两套逻辑打架）；`buildCommand`/`buildInteractiveShell`/`executeCommand` 不再创建 `.l2s`、不再设 `PROOT_L2S_DIR`；`cleanupBrokenL2sSymlinks` 在 `.l2s` 不存在时直接返回（原每次 proot 命令都全盘遍历 rootfs）；`getDefaultEnvWithL2s` 保留 API 但只返回默认 env；`PluginBackendManager` 同步去掉 l2s 环境变量。
- **D-33** `ProotRuntime` 最小挂载集：基础集改为 `/dev /proc /sys`，`/storage/emulated/0` 改由新偏好 `KEY_PROOT_BIND_SHARED_STORAGE` 控制（默认 true 保持旧行为）；`PreferenceManager.isProotBindSharedStorage/set…`；`ContainerManagementScreen` 新增「挂载宿主共享存储」开关（新增字符串 `bind_shared_storage` + `bind_shared_storage_desc` 中英）。

### [批次17·下] 日志脱敏与容器口径
- **B-71（脱敏部分）** `Logger.init` 不再把日志文件**绝对路径**写进日志本身（只记文件名）；`PluginWebInterface`/`PluginJSInterface` 的复制、分享日志由「剪贴板内容前 50 字」改为**只记字符数**；`TrampolineActivity`/`WidgetConfigureActivity` 的 `Intent: $intent` 全量打印改为只记 action / extras keySet。（日志目录是否迁私有目录仍列决策 B-78/D-34/G-06）
- **F-01** 容器安装口径统一：`DownloadableDistro` 增 `bundled` 标志 —— 只有 Debian 有内置 assets（实测 43MB，原界面写「~27 MB 下载」），alpine/ubuntu/arch 走 `installFromAsset` **必然失败却可正常勾选**；现在未内置的行置灰、radio 禁用、确认按钮仅 bundled 可点，行内提示「内置资源，无需下载 / 未内置，暂无法离线安装」（新增字符串 `distro_bundled_hint`、`distro_not_bundled` 中英），入口按钮文案 「下载」→「安装」。

### [批次17·下2] 分类持久化 key、保活单源、无障碍与清单注释
- **D-41** `PluginInfo.category` 默认值改稳定 key `CATEGORY_UNCATEGORIZED="uncategorized"`（原默认 `Str.get(R.string.uncategorized)` 被 `toJson` 持久化，切语言后分类错乱）；新增 `displayCategory()` 在展示处映射为本地化文案；`updatePluginCategory` 把本地化「未分类」/空串归一回 key。`ToolsScreen`、`PluginManageScreen`、`PluginManager.getAllCategories/getPluginsByCategory` 全部改走展示映射。
- **C-23** 保活状态双源合一：`BackendConfig.setKeepAliveEnabled` 写 prefs 后**单向同步** supervisor 读取的 `keep_alive` 标记文件；设置页开关改调该方法（原开关只写文件、prefs 仅 Save 时写 → 下次进页显示与 supervisor 实际行为不一致）。
- **A-38** 会话关闭按钮 `✕` 补 `contentDescription="@string/close"`。
- **A-46/A-47** AndroidManifest 补注释：`<queries>` 在 targetSdk 28 下不生效、为升级 targetSdk≥30 预留；`dataExtractionRules` 在升级 targetSdk 时的迁移项。
- **B-09** 全局崩溃处理器的 `catch` 回退不再静默绕过记录 —— 先 `Log.e` 把原始崩溃落 logcat 兜底再交回默认处理器。
- **D-35（本批）** 该处硬编码中文日志改为英文。

### [批次17·下3] supervisor 退出码回传与僵尸回收
- **C-30** `supervisor.sh`：
  - 进程检测改「`/proc/<pid>/stat` 状态 Z/X **或** `kill -0` 失败」——原 `kill -0` 对僵尸恒返回成功 → 死掉的后端既不清理也不回收，僵尸累积；
  - 死亡/被杀/空闲回收/退出各路径统一 `wait <pid>` 收割退出码，写 `rc/<key>.rc`（cmd 拾取同 key 时先清旧 rc，stop/idle 标注原因）；
  - 宿主侧新增 `SharedSupervisor.readExitCode(key)`（`clearBackendRecord/clearAllBackendRecords` 一并清理 rc），`PluginBackendManager.waitForRealTermuxReady` **就绪超时时读回 rc** 并打日志：`backend <key> exited before ready (supervisor rc=N)`，不再只有轮询超时。
  - `sh -n` 语法检查通过；`buildScriptCommand` 用 `exec` 启动（PID 即后端 PID），`wait` 收割路径不受影响。

### [批次17·下4] 杂项（A-37/A-21/B-11/B-12）
- **A-37** `widget_layout_grid.xml` 9 处 `contentDescription="插件图标"` 硬编码改 `@string/plugin_icon`（两语言包已有该键）。
- **A-21** CI 注释「AGP 8.13 要求 JDK 17+」与实际 `AGP 8.9.1` 对齐。
- **B-11** `UinApplication` 两处中文日志改英文（评论/文档不计）。
- **B-12** `instance = this` 提前到 `attachBaseContext()`，onCreate 之前访问 `getInstance()` 不再抛未初始化异常。
- 备注 **A-40**（onboarding 兜底版本）此前已按 G-14 用 `BuildConfig.VERSION_NAME` 完成；本机 `./gradlew -v` 可用（Gradle 9/JDK21），但**无 Android SDK**（`ANDROID_HOME` 空），仍无法编译校验，改动沿用括号配平 + 逐处断言的静态校验方式。

### [批次17·下5] Trampoline / Splash / 更新链路
- **B-41** `TrampolineActivity` 去掉固定 `postDelayed(50ms)`（纯增时延），直接分发；随之移除 `Handler/Looper` 导入。
- **B-42** `TrampolineActivity` 不再靠伪造 `ACTION_MAIN+CATALOG_LAUNCHER` 表达「打开主界面」，改带 `MainActivity.EXTRA_FROM_TRAMPOLINE`；`MainActivity` 的两处 `isFromLauncher` 判定均排除该 extra —— 修复经 trampoline 跳主界面被误判为「桌面启动」而关掉全部插件页。
- **B-24** 手动下载入口不再硬编码 `https://github.com/.../releases/tag/`：`UpdateChecker.getReleasePageUrl(tagName)` 按本次探测到的镜像（`getDownloadMirrorUrl(..., useCdn=true)`）生成地址，未命中镜像回退原地址。
- **B-36** 忽略版本读写统一到 `PreferenceManager`（`SplashActivity` 的 `isVersionIgnored/setVersionIgnored` 改调 `get/setIgnoredVersion`，不再直读 `KEY_IGNORE_VERSION`）；删除 `UpdateChecker.ignoreVersion()` 死包装（`IConfigRepository` 上的同名死接口留待 D-43 一并处理）。
- **B-37** 删除 `AppConstants.SPLASH_DELAY`(1500L，零引用)，保留 `SplashActivity.kt` 自有的 700L。
- **B-38 之外的 B-39** 删除恒 false 的 `isCheckingPermission`（字段、传参、组合参数三处）与空包装 `navigateToOnboardingOrMain()`（组合参数直接 `navigateToNext()`）。
- **B-63** `UpdateDownloader` 的 `String.format` 加 `Locale.US`。

### [批次17·下6] 文档 i18n 与开屏文案
- **G-09 / A-48** 补齐 `assets/docs/en/CONTRIBUTORS.md`（184 行中文文档英译，含表头列数修正），`DocViewerScreen` 删除 `docType != "contributors"` 的硬编码回退特判，英文环境统一走 `docs/en/`。
- **B-38** `SplashActivity`：`contentDescription="App Icon"` 改 `Str.get(R.string.app_icon_desc)`（新增 en/zh 键），`Text("UIN Tool")` 改 `Str.get(R.string.app_name)`，权限拒绝弹窗写死的 `0xFFFFEBEE`/`0xFFD32F2F` 改 `colorScheme.errorContainer/onErrorContainer/error`（深色自适配）。
- **过期项核实**：`E-03`(UINCard)、`E-05`(UINModal) 引用的组件在当前代码库已不存在（相关组件改造后未保留同名 API）→ 按现状不可执行；`E-21` 中 ②模板转义已由 C-34 完成、③`utils/PermissionManager.kt` 已不存在（现为 `PermissionUtils` + `PluginPermissionManager`），仅①快捷方式图标仍为固定尺寸位图（已由 `ShortcutManager.kt` 迁移至 `PluginShortcutHelper.kt`，72px）。`F-04` 建议复用的 `UINEmptyState/UINErrorCard/UILoadingSkeleton` 当前不存在，需先建统一组件再收口。

### [批次17·下7] 过期版本号 + 复核（A-03/A-24/A-42/A-44/A-45 已修）
- **A-39** 预览屏与侧栏 4 处写死版本号（`UIN Tool 5.6.0` / `v5.7.0 …` / `v5.6.0 Menu`）改 `BuildConfig.VERSION_NAME`。
- **复核确认已完成**（UP 未更新）：**A-03** 白天 `windowLightStatusBar=false` ✓；**A-24** `android:icon/@mipmap/ic_launcher` ✓；**A-42** `uses-permission OPEN_PLUGIN/RUN_COMMAND` + 注释 ✓；**A-44** 两个 Widget receiver `exported="false"` ✓；**A-45** `TrampolineActivity exported="false"` ✓（故 B-42 的 extra 区分用于防误判，非权限绕过）。
- 同步改用「全量遍历比对」脚本（原 `diff -rq | grep '^文件'` 漏掉**新增**文件，本次发现 `docs/en/CONTRIBUTORS.md` 未同步）。

### [批次17·下8] B-61 更新下载改前台服务
- 新增 `core/update/UpdateDownloadService`（`exported=false`，已注册 Manifest）：仅负责 `startForeground` 持有前台优先级，**通知 ID 与 `UpdateDownloader` 的进度通知同为 1001**，`startForeground` 之后下载器的 `notify(1001,…)` 直接续写同一条前台通知（进度/文案无缝）；channel 复用 `update_download_channel`（先判存在再建）。
- `UpdateDownloader.startDownload` 开始时 `start()`，**成功 / 失败 / cancelDownload** 三条路径均 `stop()`；启动失败（Android 12+ 后台启动受限）只降级为原行为，不阻断下载。
- 遗留细化：通知仍无「取消」按钮（需补广播/Service extra 承接取消动作），列为后续小项。

### [批次17·下9] B-33 开屏更新检查改「每次开屏都查、窗口区分」
- **B-33** 原 `LaunchedEffect` 仅 `isFirstLaunch` 才检查更新 —— 首次安装必然没有新版本，「开屏更新弹窗」几乎永远是空跑；现在**每次开屏都查**，等待窗口区分：首启/进引导页 5s（`UPDATE_CHECK_TIMEOUT`），日常启动 1.5s（新增 `UPDATE_CHECK_TIMEOUT_FAST`），超时置 `isCompleted` 并进主界面（`MainActivity` 的每日静默检查兜底，不漏提示）。
- 联网成功/无更新时 `setLastUpdateCheckDay(today)`（与 MainActivity 同口径）→ 同一天不再重复发同样的请求；超时后才返回的结果因 `isCompleted` 早退、不写当日额度，MainActivity 会重新检查。
- 开屏更新弹窗「在浏览器打开 release 页」也改走 `UpdateChecker.getReleasePageUrl()`（B-24 同源），不再硬编码 GitHub 直连。

### [批次17·下10] F-04 空态/错误卡统一
- `ui/components/unified/SpecialComponents.kt` 新增两个共用组件（与既有 `SkeletonItem/Card/ListItem` 同文件）：
  - `UnifiedEmptyState(icon, title, subtitle?, modifier)` —— 64dp 图标 → 标题 → 可选副标题，垂直居中，色走 `onSurfaceVariant`；
  - `UnifiedErrorCard(title, message?, actionLabel?, onAction?, modifier)` —— `UnifiedCard` 底 + `colorScheme.error` 图标 + 可选操作按钮。
- **PluginManageScreen** 自绘空态（Box+Column+Icon+Text×2）改用 `UnifiedEmptyState`（文案/分支逻辑保持原样）；**RepoScreen** 空态同样收口，且两张手写错误卡（`load_error` / `install_error`）改 `UnifiedErrorCard`，重试/我知道了按钮行为不变；骨架本就共用 `UIComponents.PluginListSkeleton` 未动。
- `ManageScreen` 是入口 Hub、无列表空态/骨架（核实无对应实现），故 F-04 三处中实际统一两处。

### [批次17·下11] B-61 续：通知栏取消按钮
- `UpdateDownloadService` 通知加「取消下载」Action（`PendingIntent.getService` 投回自身，`EXTRA_CANCEL` 分支 → 调用回接点后 `stopSelf()`）；回接点 `setCancelAction(...)` 由 `UpdateDownloader` 在 `startDownload` 注册、**成功/失败/取消三条路径统一 `setCancelAction(null)` 反注册**，避免服务侧持有过期下载器引用。
- 修掉一处顺序 bug：`start()` 里原本会清空刚注册的回接点（先 `setCancelAction` 后 `start` → 按钮永远无效），已移除该清空逻辑。
- 复核：**F-06**（镜像 URL `isValidMirrorUrl` + 容器名校验）在代码里已带 `// F-06` 注释实现完成，UC 此前漏记。

### [批次17·下12] G-10 文档加载收口
- 新增 `ui/screen/docs/DocLoader`：`fileNameFor(docType)`（docType→文件名映射）、`isEnglish()`（UIConfig=en 或 system+系统 locale=en）、`load(fileName)`（**en 缺失自动回退 zh-cn**）、`errorText(e)` 统一错误文案。
- `DocViewerScreen.loadDocument` 与 `HelpScreen` 的 `LaunchedEffect` 都改为调 `DocLoader`，删掉两份各自维护的「语言判定→拼路径→读文件」；HelpScreen 顺带去掉 `failed_to_load_help_document_e_messa` 分支（改用统一 `errorText`），未用 import 已清。
- 说明：**入口**仍保留两个（HelpActivity 直达 Help.md、DocBrowser 目录树），按「先不删」约定不合并文件，只合并加载实现。

### [批次17·下13] 日志英文化（A-41/D-35/B-11 收口）+ 预览屏 Toast
- 全仓 `Logger.*` / `AppLog.*` 中的**中文文案清零**（60 处 → 0）：`RootfsManager` 28、`ProotInstaller` 12、`UinApplication` 10、`PluginShortcutHelper` 8、`WidgetPinnedReceiver` 2，全部译为固定英文并保留插值变量与异常参数（D-35 口径：日志固定英文，可检索、不随语言漂移）。
- `UIConfigPreviewScreen` 三处演示 Toast（插件安装成功/连接失败/有可用更新）改 `Str.get`：复用 `update_available`，新增 `connection_failed`（Connection failed / 连接失败）、`plugin_install_success`（Plugin installed successfully / 插件安装成功）两键，两语言包同步。
- 该屏其余大量中文**界面**字面量属 A-41 未完部分（预览/演示文案），单独列后续批次。

### [批次17·下14] D-29 网络/偏好单例收口
- `ServiceLocator.getOkHttpClient()` 暴露容器共享 client。
- `RepoViewModel`：删掉私有 `OkHttpClient.Builder(...)`（自带 cache 目录）——`init` 改 `ServiceLocator.getGitHubApiService()`，原始请求改 `ServiceLocator.getOkHttpClient()`；两处 `PreferenceManager(context)` 也改容器单例。
- `GitHubMirrorScreen`：`PreferenceManager(context)` + `MirrorManager(OkHttpClient())` → `ServiceLocator.getPreferenceManager()/getMirrorManager()`（原每次进页新建一套网络栈）。
- `PluginManageScreen` / `ManageScreen` / `SourceManageScreen` 的 `PreferenceManager(context)` 同样改容器单例。
- **未做（说明）**：`IBackupRepository`/`BackupRepositoryImpl` 全仓**零引用**（死接口+死实现），按「先不删」不注册以免新增死配置；`FileManager` 无状态、四处 new 的成本可忽略，暂不进 DI。

### [批次17·下15] B-59 更新列表排序改双字段比较
- `UpdateChecker` 删除 `toComparableVersion()`（「code + 1e9 基线上移」hack），排序改为显式双字段 `sortWith`：两侧都有 `versionCode` → 比 code（降序）；任一侧缺失 → 回退 `compareVersionTriplets`，平局再比 code。两套刻度不再混进同一数轴。
- 静态校验：括号配平 `83/83`、`230/230`；`toComparableVersion` 全仓无残留引用。
- 附带核实：A-49 版本单源已全量完成（`app/build.gradle` 6.1.0/25、README 徽章与版本表、docs README 双语「对应应用版本」均已同步，About/CHANGELOG 中 6.0.0 属历史版本表，非陈旧），`grep 6.0.0` 仅剩无关 AOSP 注释。

### [批次17·下16] B-79 静默更新逻辑下沉 UpdateViewModel
- 新增 `ui/viewmodel/UpdateViewModel.kt`：`UpdateUiState(pending/force/downloading/progressPct)` + `checkSilentlyIfNeeded()/dismissPending()/startDownload()/openReleasePage()/ignoreCurrent()/cancelDownload()`；监听器在 `onCleared()` 统一清（替代原组合 `DisposableEffect` 清理，旋转时不再误清）。
- `MainActivity.MainContent` 130 行静默更新块（`remember { mutableStateOf }` ×5 + `LaunchedEffect` 内嵌 listener + 下载/取消/忽略回调）替换为 `viewModel()` + `collectAsState()` 渲染；顺带删掉因此变死的 `MAIN_CONTENT_TAG` 常量与 `UpdateChecker/UpdateDownloader/ReleaseInfo/AppToast/LocalDate` 五个 import。
- 收益：旋转/重建不再重置进行中的检查与下载进度（原实现组合销毁即丢状态）。
- 静态校验：MainActivity `141/141` `421/421`、UpdateViewModel `25/25` `72/72` 配平；`silent*` 旧变量与 `toComparableVersion` 无残留；`UpdateDialog`/`UpdateChecker`/`UpdateDownloader`/`PreferenceManager` 全部 API 签名逐一比对通过。

### [批次17·下17] C-01 插件宿主 Activity 关闭导出
- `AndroidManifest`：`PluginHostActivity` `exported` true→**false**，移除其上的 `android:permission="com.UIN.Tool.permission.OPEN_PLUGIN"`。
- 改前逐一核实全部启动点均为本应用内显式 Intent：`MainActivity:298`、`TrampolineActivity:97`、`WidgetProvider:419/687`、`PluginManager:676`、`PluginOpenDispatchActivity:282`、`PluginJSInterface:2383`；快捷方式（`PluginShortcutHelper`）指向 `MainActivity`，小部件用本应用创建的 PendingIntent（按 creator 身份发送，不受 exported 影响）→ 关闭导出不破坏任何现有链路。
- 第三方入口保持 `PluginOpenDispatchActivity`（exported + VIEW/BROWSABLE filter，其内部再显式跳宿主）；`<permission>` 声明与 `uses-permission` 保留（A-42 的开屏补申请路径仍引用）。
- 同步修正 Manifest 内两处随事实失效的注释（中转页注释、A-45 绕过注释）。XML `minidom` 校验通过。

### [批次17·下18] B-80 环境编排下沉 EnvironmentBootstrap + 过期项核实
- 新增 `core/bootstrap/EnvironmentBootstrap.kt`：`autoInstall(appScope, context)`（proot 二进制 → 默认 rootfs 容器两段安装 + Toast）与 `isEnvironmentInstalling()` 标志原样迁出。
- `UinApplication`：删除 49 行的 `autoInstallEnvironment()` 与 companion 持有的 `_isEnvironmentInstalling`，调用点改为 `EnvironmentBootstrap.autoInstall(appScope, this@UinApplication)`；`PluginHostActivity:409/414` 两处轮询改指 `EnvironmentBootstrap.isEnvironmentInstalling()`。
- 静态校验：三文件括号配平；`PluginHostActivity` 括号 1046/1048 属**改动前既有**（与设备旧版逐字节比对确认），非本次回归；旧引用 0 残留。
- 同时核实一批「已修但未标记」的中优先项：**B-53**（ServiceLocator 失败重试+`initError` 归因+cause 随异常带出，已完成）、**D-27**（4 个 SP 全部 `lazy`、`getStringSafe/getBooleanSafe/...` 类型兜底、`savePluginPermissions` 单次 edit 批量提交，已完成）、**D-34 主体**（Logger 已是 `logQueue`+专职写线程+满队丢弃计数，异步单线程写入已落地；仅剩「日志迁私有目录」未做，牵涉 G-11 查看器，见后）、**B-02/B-03/B-04/B-05/B-08**（已统一挪 `appScope(Dispatchers.IO)`，代码内注释显式标注）、**B-22**（`UpdateDownloader` 回调本就 `mainHandler.post` 回主线程）、**C-05 前半**（`clearRetainedWebViews` 已挂 `UinApplication.onTrimMemory`，即 C-12 修复）。

### [批次17·下19] F-05 后端设置页状态收 ViewModel（第 1 屏）
- 新增 `ui/viewmodel/BackendSettingsViewModel.kt`：6 个 `MutableState`（impl/env/container/idleMin/idleInput/keepAlive）由 `AndroidViewModel` 持有，初值仍从 `BackendConfig` 读。
- `BackendSettingsScreen`：`var impl by remember { mutableStateOf(...) }` ×6 改 `var impl by vm.implState` —— 委托语法不变，页内全部 `impl = key` / `idleInput = ...` 等读写点**零改动**；保存链路（`BackendConfig.setImplementation/setEnvironment/setContainer/setIdleTimeoutMinutes/setKeepAliveEnabled`）原样保留。
- 语义确认：这 5 组 key 全仓**只有本页写**（grep 全量核对），故不做「回页面重读」，避免冲掉未保存编辑；旋转/重建后未保存输入不再丢失。
- 静态校验：两文件括号配平 `2/2`、`63/63`、`18/18`、`222/222`；`BackendConfig` import 仍被页内多处使用。

### [批次17·下20] F-05 镜像页状态 + 协程收 ViewModel（第 2 屏）
- 新增 `ui/viewmodel/GitHubMirrorViewModel.kt`：9 个页面状态（mirrors/enabledMirrors/useCdn/isLoading/showResetDialog/showAddDialog/showTestResult/isRefreshing/lastRefreshTime）+ 5 个方法（`loadMirrors/refresh/importMirrors/exportMirrors/testAllMirrors`），手写 `scope.launch` 全部改 `viewModelScope`，I/O 改用 applicationContext。
- `GitHubMirrorScreen`：4 个组合内本地函数与 `rememberCoroutineScope` 删除，状态改 `by mirrorViewModel.xxxState` 委托（页内赋值点零改动）；下拉刷新的 `scope.launch{...delay(400)...}` 块收敛为 `onRefresh = { mirrorViewModel.refresh() }`。
- 清理因此失效的 import：`LocalContext`/`MirrorManager`/`PreferenceManager`/`AppLog`/`AppToast`/`delay`/`launch` + 无引用的 `val context`；`PreferenceManager` 单例保留在页内供保存按钮写入（`setEnabledMirrors/setUseCdn`）。
- 语义保持：进入页面 `LaunchedEffect` 仍调 `loadMirrors()` 重读偏好，不会读到 VM 缓存的旧配置。
- 静态校验：两文件括号配平 `91/91` `180/180`；被删标识符全文件 0 残留。

### [批次17·下21] A-54 CI 收紧 + A-53 脚本归位 + B-66 编译器回调自清
- `.github/workflows/android-build.yml`（A-54）：顶层 `permissions: contents: read`；两个 job 的 checkout 后各加 `gradle/actions/wrapper-validation@v4`；`assembleDebug` 后生成 `sha256sum.txt` 并一并上传（产物可校验）。
- A-53：`app/Project_ic.py` 与 `.bak`（开发机绝对路径私有脚本）移出 app 模块 → `scripts/`（纯移动，不删）；全仓无任何 gradle/yml/md 引用，移动零影响。
- B-66：`JavaToDexCompiler` 新增 `clearListeners()`；`BasePluginWizardScreen` 4 个终态回调（native/web 各自的 onComplete/onError）末尾自清，不再让编译器实例持有上一次界面的 lambda；同时两处 `JavaToDexCompiler(context)` → `(appContext)`，消除 Activity 引用；`getMainClass` 的 `catch { "" }` 补 `Logger.w` 归因。
- 保留未动（按「先不删」）：`addDirToZip`（唯一调用是自递归，实为死代码）；`ServiceLocator.getJavaToDexCompiler` 死入口（B-55 属清理建议）。
- 附核实：D-32 已全量完成（`MirrorStyle` 能力位枚举、GIT_ONLY 不拼 API、UNKNOWN 只对下载生效、D-32③ 局域网保留 http），A-21/A-22 亦已在 workflow 中落地。

### [批次17·下22] B-27 返回键迁 Compose BackHandler
- `MainActivity` 删除 `override fun onBackPressed()`（deprecated 重写），新增 import `androidx.activity.compose.BackHandler`。
- `MainContent` 增参 `onBackRequest: () -> Unit = {}`，体内 `BackHandler { onBackRequest() }`；`setContent` 传入 `{ AppLog.d(TAG, "back pressed"); showExitDialog = true }` —— 行为与原先逐点一致（直接改状态，不重建组合）。
- 静态校验：MainActivity `143/143`、`422/422` 配平；`onBackPressed` 0 残留。

### [批次17·下23] A-41 收尾 + B-26 tab 状态可保存
- A-41：全仓 `Logger./AppLog./Toast` 中的中文残留清零（补 `PluginShortcutHelper:85` → `"pin 1x1 shortcut requested: ..."`）。核实结论：`.kt` 内剩余中文 99% 是注释/KDoc；用户可见文案仅剩 ① `UIConfigPreviewScreen` 的**演示假数据**（"插件管理器/更新日志"等 mock 文案，属预览素材）② `PluginWizardViewModel` 生成进插件工程的 README/buildSteps Markdown 模板（F-09 已要求生成时求值，改 i18n 属独立决策）——均未动。
- B-26（部分）：`MainContent` 的 `selectedTab` 由 `remember` 改 `rememberSaveable` 并补 `androidx.compose.runtime.saveable.rememberSaveable` import——旋转/进程重建后保留用户所在 tab，不再被 intent 的 `initialTab` 重置；Activity 侧 `selectedTab`/`tabSignal` 仍作为外部导航（深链/shortcut/onNewIntent）的单一入口。
- 静态校验：MainActivity `143/143`、`422/422` 配平。

### [批次17·下24] C-42/F-11 演示 TPK 移出正式包 + B-25 说明
- `app/src/main/assets/test_plugins/`（8 个 TPK，120KB）→ `app/src/debug/assets/test_plugins/`：APK 内路径不变，debug 构建照常导出模板，release 包不再携带演示产物；`DevScreen.exportTemplates` 的 `assets.list` 空列表分支（`no_tpk_templates_in_assets_test_plug` 提示）本来就有，release 下优雅降级。
- `DevScreen` 注释与 `docs/{zh-cn,en}/README.md` 各补一句「仅 debug 构建内置」口径。
- B-25 说明：`PluginManager.refreshPlugins` 已有 500ms 防抖 + pending 合并（C-26），onResume 的调用与安装/卸载事件流会自动合并，剩余「事件驱动」属重构建议，未动。

### [批次17·下25] F-17/E-07 底部导航可选语义
- `MainActivity` 底部 4 tab：容器 `Row` 加 `.semantics { selectableGroup() }`；每项 `clickable + 手工 role=Tab` 改为 `selectable(selected = isSelected, role = Role.Tab, indication = null, interactionSource ...)` —— 选中态进入语义树（读屏朗读「已选择」），并按 Tab 归组播报序号。
- 触控区核实：`AppDimens.bottomNavHeight = 56.dp`、每项 `weight(1f).fillMaxHeight()` ≥ 48dp 最小触控目标（E-07 已在 `UnifiedComponents` 修过开关类，此处导航同标准）。
- 新增 import `foundation.selection.selectable`、`ui.semantics.selectableGroup`；括号配平 `143/143`、`423/423`。

### [批次17·下26] 更新检查单例 listener 归拢 UpdateViewModel（B-58/B-79/B-24 残留）
- 背景：`UpdateChecker`/`UpdateDownloader` 是单监听字段，管理页 `performCheckUpdate`/`startDownload` 每次 `setOnUpdateListener/setOnDownloadListener` 会覆盖静默检查（UpdateViewModel）的回调且从不清理；另 `ManageScreen` 的「浏览器打开 release 页」仍硬编码 GitHub 直连（B-24 在 Splash/静默弹窗已修、此处漏网）。
- `UpdateViewModel`：新增唯一 `updateListener`（静默+手动共用）与 `ensureRegistered()`（每次发起检查前重挂，防 Splash `clearUpdateListener` 时序窗口）；新增 `ManualCheckHandler`（onUpdateAvailable/onNoUpdate/onFailed 一次性回调）+ `checkManually(handler)`；监听回调中「手动检查进行时只分手动回调、不写静默 pending」，避免管理页与全局弹窗重复弹；静默每日额度逻辑原样保留（成功/无更新时写 `setLastUpdateCheckDay`）。
- `startDownload(context, target = null)`：支持管理页指定 release（缺省仍用 pending）；状态新增 `downloadingVersion/downloadedBytes/totalBytes`，进度回调写入。
- `ManageScreen`：删本地 listener 块、`isCheckingUpdate/hasNewVersion/showProgressDialog/progressMessage`（`hasNewVersion` 本就只写不读）、自建 `UnifiedLoadingDialog` 与私有 `startDownload`；改 `val updateViewModel: UpdateViewModel = viewModel()`（与 MainContent 同为 Activity 级 store → 同一实例），手动检查走 `checkManually`，下载走 `updateViewModel.startDownload(context, target)`——进度/失败提示由 MainContent 全局进度弹窗（B-23 取消按钮）统一渲染；`onManualDownload` 改 `updateChecker.getReleasePageUrl(tagName)`（B-24 收尾）。
- `MainActivity`：全局下载弹窗文案由 `"56%"` 升级为 `downloading_release_versionname_prog`（版本+百分比+已下/总字节，原管理页文案），补 `formatFileSize` import。
- 清理无用 import：ManageScreen 的 `UpdateChecker/UpdateDownloader/PreferenceManager/formatFileSize/launch/java.io.File`，补 `lifecycle.viewmodel.compose.viewModel`、`ui.viewmodel.UpdateViewModel`。
- 时序核实：`checkUpdate()` 入口即快照 listener（B-58），VM 注册与 checkUpdate 同主线程相邻调用，Splash 的 clear 不可能插入其间；Splash 自身检查结果经其快照送达，不受影响。
- 静态校验：UpdateViewModel `0/0`、ManageScreen `0/0` 配平；MainActivity 括号差 `+1` 与设备旧版完全一致（既有字符串假阳性）；无 `setOnUpdateListener` 残留于管理页。

### [批次17·下27] A-31 字符串键对齐 CI 卡点
- 新增 `scripts/check_strings_parity.py`：解析 `values/strings.xml` 与 `values-zh-rCN/strings.xml`，校验 ①键集合一致（string/string-array/plurals）②单文件无重复键 ③同键格式化占位符一致（`formatted="false"` 跳过）；不一致 exit 1。
- `.github/workflows/android-build.yml` 的 `lint-and-test` job 在 checkout 后、JDK/SDK 之前插入 `python3 scripts/check_strings_parity.py` 步骤（快速失败）。
- 本地实跑：`OK: 2378 keys aligned, 0 placeholder diffs`（TODO 基线 2364 已随新增字符串增长，口径不变）。

### [批次17·下28] D-35 第1批：日志固定英文（D-35 部分）
- `BackupRepositoryImpl` 19 处、`MirrorManager` 1 处 `Logger.*/Str.get(...)` → 固定英文字符串字面量（含 `${...}` 参数插值），与 A-41「日志英文可检索」口径统一；zh 设备日志不再随语言变化。
- 保留非日志用途的 `Str.get`：`progress(...)` 进度回调（UI 文案）与 `return Str.get(...)` 校验消息（进 UI 提示），import 均仍被使用。
- 范围核实：D-35 所列 `ProotInstaller:30-80`/`RootfsManager:97-257` 的硬编码中文日志已在 A-41 清零；`TERM/TerminalSession.java` 在当前代码库不存在（引用过期）。
- 留决策：proot 族 9 处中文 `IOException` 消息（`无法清理旧容器目录` 等）是**双用途**——既进日志又被 `ContainerManagementScreen` 拼进 `container_*_failed %1$s` 展示给用户，直接翻英会让中文 UI 出现英文片段，彻底方案需资源化（新 key ×2 locale）或拆分「日志消息/用户原因」，未动。

### [批次17·下29] G-13 帮助/日志入口接线（原为不可达页面）
- 核实：`HelpActivity` 与 `LogViewerActivity` 全仓**零启动点**（仅 Manifest 声明，exported=false）——崩溃导航 `MainActivity:139` 实际打开的是 `DevToolsActivity(auto_open)`；即帮助/日志页平时根本进不去，比 G-13 描述的「埋得深」更严重。
- `ManageScreen.softwareItems` 最前新增两项：**帮助**（`R.string.help` + `help_and_faq`，`Icons.Default.Help` → `HelpActivity`）、**运行日志**（`runtime_logs` + `runtime_log_desc`，`Icons.Default.List` → `LogViewerActivity`），标题/描述复用既有双语键（不新增字符串）；启动失败 `AppLog.e` + `AppToast.error`（`failed_to_load_help_document` / `failed_to_open_log_page`）。
- 两 Activity 均自包含（`UINToolTheme` + onBack/finish），无入参依赖，直启可用；补 import `ui.screen.help.HelpActivity`、`ui.screen.log.LogViewerActivity`。
- 静态校验：ManageScreen 括号 `0/0` 配平。

### [批次17·下30] B-06 启动序列声明式任务化
- `UinApplication.onCreate` 的 4 段主线程 try/catch + `appScope.launch` 内 7 段后台 try/catch → 声明式 `StartupTask(id, run)` 列表 + 统一执行器 `runStartupTasks()`：顺序、逐段异常隔离、先后依赖（crash handler→Logger→UIConfig/ServiceLocator；workdir→…→killStaleProcesses→环境安装）与原先逐字一致。
- 执行器新增每任务归因与耗时日志：`startup task <id> ok (Nms)` / `failed (Nms)`（英文、可 grep，契合 D-35 方向）；失败文案不再用 `Str.get`（`work_directory_initialization_failed` 等键随之无日志侧引用，资源保留）。
- 原 B-07/B-01/B-02/B-80 等顺序注释全部随任务定义保留；`this@UinApplication` 显式指向宿主。
- 静态校验：UinApplication 括号 `0/0` 配平；`appScope.launch { try {` 旧结构0残留。

### [批次17·下31] E-21① 快捷方式图标按密度缩放
- `PluginShortcutHelper`：固定 `createScaledBitmap(bitmap, 72, 72)`（72px 不随密度，高密度屏被 launcher 放大发虚）→ 新增 `shortcutIconBitmap()`：`72dp × density`（下限 72px、上限 256px），Oreo+ 的 `Icon.createWithBitmap` 与旧版 `EXTRA_SHORTCUT_ICON` 两条路径同口径。
- 未采用建议中的 `Icon.createWithAdaptiveBitmap`：插件图标是任意整幅位图，套自适应遮罩会被内缩进安全区反而变小；密度化缩放解决的是实际的清晰度/尺寸问题。
- E-21 其余两项早已处理：②模板转义=C-34；③`utils/PermissionManager.kt` 已不存在（现为 `PermissionUtils` + `PluginPermissionManager`，见前序核实）。
- 静态校验：PluginShortcutHelper 括号 `0/0`；旧 `createScaledBitmap(bitmap, 72, 72` 0 残留。

### [批次17·下32] E-08 主题三件套键位拆分（重组面收窄）
- `UINToolTheme` 原实现：`configVersion` 同时作为 colorScheme/shapes/typography 的 remember 粗键 → 任意配置保存（含只改字号/圆角）三者全部换实例，读 `MaterialTheme.colorScheme` 的整棵子树连带重组。
- 改为「订阅/快照」两层：`configVersion` 仍被 collect（保证保存后本组合重组），但三类对象改按**真实输入**做键——`colorInputsSnapshot(dark + 13 个颜色键的字符串)`、`shapeInputsSnapshot(5 个圆角)`、`typographyInputsSnapshot(4 个字号)`；快照相等则 `remember` 复用旧实例。
- 效果：改字号只换 typography、改圆角只换 shapes、改单色只换 colorScheme，其余两类实例恒定 → 对应 Local 的读者全部 skip；`resolvedDark` 变化仍正确触发 colorScheme 重建。
- 静态校验：Theme.kt 括号 `0/0`；3 处快照函数与 3 处调用点齐备。

### [批次17·下33] 核实批次：D-43 结论 + 全量可达性扫描
- **D-43（IConfigRepository 抽象不对称）结论：对象层近乎死代码，改对称性无意义**。全仓引用仅 3 处定义链：`MainActivity:103` 的 `configRepository` lazy 属性（**零调用点**）、`AppContainer.configRepository`、`ServiceLocator.getConfigRepository()`；实现类 19 个 suspend setter 全是对 `PreferenceManager` 的同步直套（D-29 后业务已直连 PM）。不改签名，留待删除类决策（与 C-05 同批处理时一并删）。
- **Activity 可达性扫描**（Manifest 35 个 vs 全仓类引用）：`HelpActivity`/`LogViewerActivity` 已在下29接线；`WidgetConfigureActivity` 由 `widget_info.xml` 的 `android:configure` 引用 ✓；`PluginOpenDispatchActivity` 为 exported 分享入口 ✓；新发现 **`PermissionExplainActivity` 同为孤儿**（exported=false、零启动点，`Destinations.PermissionExplain` 路由也无人用）——Splash 走的是自绘 `PermissionExplainDialog`，正是 G-16「两套权限流程」的具象证据；接线方式取决于 G-16 的 PermissionFlow 合并决策，本轮不动。
- 中优先「非删除、非设计决策」的可实施项至此已扫空；剩余活跃项均属：删除类（A-25~A-34、B-43 族、C-05 余项等，用户指示先不删）、设计决策（E-09 主题双源、B-10 locale、C-38/G-25 实例键、G-16 权限合并、F-16/G-28/E-12/E-13）、大重构（D-42、F-12、B-81）、被阻塞（G-11 随 B-78/A-52、E-20 随 E-19）。

### [批次17·下34] E-09 方案B：全量迁 AppCompat + AppCompatDelegate 夜间模式（用户拍板：可维护性/功能优先）
- **背景**：Compose 侧跟 `UIConfig.shouldUseDarkTheme`（可固定深/浅/跟随系统），XML 侧（values-night 资源、窗口配置、Theme DayNight 解析）跟系统 uiMode → 用户固定深色+系统浅色时双侧分裂。
- **可行性审计**：`Theme.UIN.Tool` parent = `Theme.MaterialComponents.DayNight.NoActionBar`（⊃ AppCompat，无主题崩溃风险）；appcompat 经 material 1.12.0 已在类路径（PluginHostActivity 本就是 AppCompatActivity）；Manifest 30 个 Activity 中唯一非 AppCompat 主题是 `TrampolineActivity`（`@android:style/Theme.Translucent.NoTitleBar`）→ **保持 ComponentActivity 不迁**。
- **实施**：
  1. `app/build.gradle` 显式声明 `androidx.appcompat:appcompat:1.7.0`（compileSdk 36 / minSdk 23 兼容）；
  2. **27 个 Activity**（含 MainActivity/SplashActivity 及 4 个内嵌 Activity 的 Screen 文件）`ComponentActivity` → `AppCompatActivity`（import + 继承点机械替换，逐文件断言无残留）；
  3. `UinApplication.applyNightMode()`：`getThemeMode()` → `MODE_NIGHT_YES/NO/FOLLOW_SYSTEM`，`getDefaultNightMode()!=mode` 才 set（防重建循环）；在 `uiConfigInit` 启动任务里调用；
  4. `MainActivity` 的 `configVersion` 收集器在 `applyTheme` 后追加 `applyNightMode()` —— 改主题即全局重挂，**AppCompat 自动重建已启动界面**（停留中的 XML/Compose 界面即时变色，无需手动 recreate）。
- **效果**：深/浅/跟随系统三档在 Compose、XML 资源、窗口三处同源；「跟随系统」由 delegate 透传，运行中系统切夜间实时生效（原方案 A 的「覆盖后不跟随」缺陷不存在）。
- **风险与缓解**：①27 文件迁移无编译验证 → 逐文件括号配平对比设备基线全部一致、`ComponentActivity` 残留仅 Trampoline（预期）；②AppCompat 的 LayoutInflater factory 接管 XML 演化 → 项目仅 Compose + 少量 widget XML，属标准行为；③ `onBackPressed` 覆写（LogViewer/DevTools）在 AppCompatActivity 下语义不变。
- 静态校验：28 个改动 .kt 与基线配平差异 0；build.gradle 括号 `0`。

### [批次17·下35] B-10 方案A：locale 三套机制收敛（用户拍板：应用内为准）
- **现状**（比清单更复杂）：①`UinApplication.applyLocale` = `Locale.setDefault` + 废弃的 `updateConfiguration`（`Str.get` 走 app context，全靠它）②`ContainerManagementActivity`/`SimpleTerminalActivity` 各自 `attachBaseContext + createConfigurationContext` 重复两份 ③设置页「跟随系统」选项早已存在（清单"无跟随系统"过期）。
- **实施**（复用下34 的 AppCompat 基建）：
  1. `applyLocale()` 单点收敛：目标 locale 计算 → `Locale.setDefault` → **`AppCompatDelegate.setApplicationLocales`**（27 个 Activity 的 attachBaseContext/XML 上下文 + 系统「应用语言」集成；`tag=""` 即跟随系统）→ 保留 app resources `updateConfiguration`（`Str.get` 即时切换通道）；
  2. **修既有 bug**：切过 zh/en 再切「跟随系统」时 `Locale.getDefault()` 拿到的是被 `setDefault` 污染的值 → `attachBaseContext` 在首次污染前抓 `systemDefaultLocale` 快照，system 分支用它还原；
  3. 删除两份重复 `attachBaseContext/applyLocaleContext`（它们已是 AppCompatActivity，delegate 统一接管）；ContainerManagementActivity 的 `Context/Configuration` import 随之移除。
- **行为口径**：应用内设置为权威——系统设置页改的应用语言会在下一次 `applyLocale`（启动或下拉变更）被覆盖回 UIConfig 值（已在注释中声明）。
- **注意**：`setApplicationLocales` 会触发已启动 Activity 重建，设置页下拉变更后重建并从 UIConfig 回读，值已先 `setLanguage` 落盘，无丢失。
- 静态校验：3 文件括号配平与基线一致（0/0）；`applyLocaleContext` 0 残留；`systemDefaultLocale` 声明/赋值/引用三点齐备。

### [批次17·下36] C-38/G-25 方案B：后端独立端口多开（实例键全链路，用户拍板）
- **基线核实**：`startBackendInstance` 本就按实例键起独立进程/端口（`findAvailablePort` per key，supervisor `requestStop/clearBackendRecord/rc/pid/idle` 文件全按任意 key）——**start/supervisor 侧早已支持**；不一致只在宿主侧（`backendKey` 恒 pluginId、hold/释放/卸载/状态全按 pluginId）。共享 WebView 重绑靠 `sendBackendReadyToWebView(port)` 推新端口（`window._onBackendReady(port)`），端口切换页面可感知。
- **实施**：
  1. `PluginHostActivity.backendKey = currentInstanceKey.ifEmpty{pluginId}`；新增 `heldBackendKey`（onNewIntent 可换 pluginId，释放必须用 acquire 时的键），两处 start 成功回调 `acquireBackend(key)+heldBackendKey=key`，destroy `releaseBackend(heldBackendKey)`，`markActivity(heldBackendKey ?: backendKey)`；
  2. `PluginBackendManager` 新增 `keyToPluginIds` 反查表（start 成功登记 / stopBackend、闲置死端口清理、进程退出监听三处清除）+ `stopBackendsOfPlugin(pluginId)`（归组停全部实例键）+ `isAnyBackendRunningForPlugin(pluginId)`；`releaseHold` 的保留 WebView 判定改按键查插件 ID（`retainedWebViews` 本就是插件级），归零停 **本实例键**；注释从「共享端口引用计数」改「实例持有」；
  3. `PluginManager.stopBackend` → `stopBackendsOfPlugin`（卸载/全清自动覆盖全部实例），`isBackendRunning` → `isAnyBackendRunningForPlugin`（否则实例键后恒 false，卸载守卫漏停）；acquire/release 参数改 key。
- **调用面审计**（保证无按 pluginId 查实例后端的活路径）：`PBM.callApi(pluginId)`/`PM.callBackendApi`/`PM.getBackendPort`/`PM.startBackend(pluginId)` 全仓**零调用者**（JS 走宿主 `callBackendApi`→直连自身 `backendPort`；`PluginJSInterface:2594` 走 `activity.getBackendPort()`）；唯一活守卫是 uninstall 的 `isBackendRunning`（已改 any）。
- **已知边界（未动）**：①跨插件消息信箱按 `target=pluginId` 寻址（协议级，多实例消费存在竞争，留后续）；②保留会话接管后旧实例键的后端靠**空闲回收**兜底清理（hold 残留在 stopBackend 时自清）；③`keepAlive` 插件不 release，同旧语义。
- 静态校验：3 文件配平差异 vs 设备基线全 MATCH（(1,4)/(1,1)/(0,0)）；`acquireBackend(currentPluginId)`/`releaseBackend(currentPluginId)` 0 残留；`keyToPluginIds` 8 处齐。

### [批次17·下37] G-16 方案A：删除孤儿权限解释栈，Splash 对话框为唯一流（用户拍板）
- **核实**：活流程=Splash 内 `PermissionExplainDialog`+内联 state（存储权限）；`PermissionManagerActivity`/`PluginPermissionActivity` 是插件权限管理（ManageScreen/权限门有引用），不属重复。死栈=`PermissionExplainActivity`+`PermissionExplainScreen`（19+101 行，零启动点）+`Destinations.PermissionExplain` 路由 + Manifest activity 块——与 Splash 对话框构成 G-16「两套 state/文案」。清单建议引用的 B-28/B-29 已不存在。
- **实施**：删 2 个 .kt 文件、Manifest `<activity>` 块（XML minidom 校验 OK）、Destinations 路由行；全仓 grep 余下 `PermissionExplain*` 匹配全为 Splash 保留流程（`showPermissionExplain`/`PermissionExplainDialog`）✓；`permission_explain_*` 字符串资源未动（资源删除归 A-25~A-34 统一清理，本轮「先不删」口径仅限整类，此为用户拍板的单点删除）。
- 设备侧同步删对应 2 文件（同步脚本已含删除逻辑）。

### [批次17·下38] E-12/E-13 方案A：字号/形状收口为单一映射 + 清死码（用户拍板）
- **核实（清单过期）**：`Dimens.kt`/`UnifiedFoundation.kt` 已不存在；E-12 实为三源——①Type.kt 静态 Typography（24/22/20…仅 uninit fallback，**与主路径映射不一致**）②Theme.buildDynamicTypography（UIConfig 默认 20/14/12/18）③AppDimens.text 被 40+ 处直读（与②同读 4 键，同源不算分裂）；另组件内 46 处硬编码 `.sp`（记录，留后续）。E-13 残留=Shape.kt 3 个零引用 `CornerRadius*` 死常量 + 静态 Shapes 与动态默认错位。
- **实施**：
  1. **Type.kt**：新增唯一映射 `buildTypography(title,body,caption,section)`（15 槽位），静态 `val Typography = buildTypography(20,14,12,18)` ≡ 默认动态；原 24/22/20/16/13/11/10 手写梯度全部作废；
  2. **Shape.kt**：删 `CornerRadiusSmall/Medium/Large` 死常量；新增唯一映射 `buildShapes(cornerSmall,cornerMedium,cornerLarge,cornerExtraLarge,dialog)`，静态 `Shapes = buildShapes(8,12,16,24,20)` ≡ UIConfig 默认；
  3. **Theme.kt**：`buildDynamicTypography/buildDynamicShapes` 改为一行转调共享映射（**注意**：现网 `buildDynamicShapes` 的 large 槽读 `getCornerRadiusExtraLarge()`（24）而非 card（16）——按实际行为收口，未改语义）；顺删 4 个因此不再使用的 import（TextStyle/FontWeight/RoundedCornerShape/sp）；
  4. **Appdimens.kt**：`radiusSmall/Medium/Large` 的 uninit fallback 4/8/12 → **8/12/16**（≡ UIConfig 默认，原值为旧梯度残留）。
- **过程记录**：Theme.kt 第一次替换因 `\n }\n` 锚点选错吞掉 NoRippleIndication/NoRippleNode 段（配平 -1 被静态校验抓住），从设备基线精确回补后 diff 仅剩预期两函数。
- 静态校验：4 文件配平 vs 基线全 MATCH；diff=仅预期改动。

### [批次17·下39] G-28 方案A：小部件后台周期刷新 = WorkManager + UIConfig 开关（用户拍板）
- **现状**：`widget_info.xml updatePeriodMillis="0"`（系统周期更新关死），刷新仅应用内广播（`WidgetProvider.sendRefreshIntent/forceRefreshAllWidgets`），无 AlarmManager/WorkManager——应用死后小部件永不更新。
- **实施**：
  1. `app/build.gradle` 增 `androidx.work:work-runtime-ktx:2.9.1`（compileSdk 36 满足；manifest 无 startup 移除，AAR 合并自带初始化 Provider）；
  2. 新文件 `widget/WidgetRefreshScheduler.kt`：`WidgetRefreshScheduler.apply(context)` 幂等调度——开 `enqueueUniquePeriodicWork(KEEP, 15min)` / 关 `cancelUniqueWork`；`WidgetRefreshWorker : CoroutineWorker` 调**既有** `WidgetProvider.forceRefreshAllWidgets`（同渲染路径，不另造轮子），失败 `Result.retry()`；
  3. `UIConfig`：新增 `widget.autoRefresh` 段（`isWidgetAutoRefreshEnabled` 默认 **true** / `setWidgetAutoRefreshEnabled`，写法照 setLanguage 模式，`saveConfig` 自带 configVersion 递增）；
  4. `UinApplication.backgroundStartupTasks` 尾增 `widgetRefreshSchedule` 任务（主线程 uiConfigInit 先行，后台调度）；
  5. `WidgetConfigScreen`（ManageScreen 两处进入，活页面）末尾新增 `BooleanPropertyRow` 开关（复用 uiconfig 现成组件，带说明行），变更即写 UIConfig + 重应用调度；
  6. 新字符串 `widget_auto_refresh`/`widget_auto_refresh_desc` 双语（parity 2380 keys OK）。
- **口径**：15min=WorkManager 周期下限，Doze 下**延后但会执行**（非精确定时，无 SCHEDULE_EXACT_ALARM 权限负担——按拍板排除了精确闹钟方案）；KEEP 不重置周期计时。
- 静态校验：4 .kt 配平 MATCH（新文件 0/0）、双 XML minidom OK、parity OK。

### [批次17·下40] F-16 方案B 第1步：统一长任务框架落地（注册表 + 全局宿主）
- **新文件 `ui/components/LongTask.kt`**：
  - `LongTask(key,title,detail,progress,error,done,cancellable,onCancel)` 单活跃任务模型；`progress=null`=不定长；
  - `LongTaskRegistry`（StateFlow）：`begin`（覆盖旧任务）/`update`（**按 key 匹配，过期任务晚到更新忽略**）/`finish`/`fail`/`dismiss`/`isActive`；
  - `LongTaskHost`：模态遮罩（`PointerEventPass.Initial` 消费全部指针事件阻断底层交互）+ 居中 `UnifiedCard`（标题 + 进度条[null 走 material3 不定长] + detail + error + 按钮行）；成功终态 1.5s 自动收起，失败可关闭（`R.string.close`），进行中按 `cancellable` 显示取消（`R.string.cancel`）。
- **MainActivity**：`setContent` 内 `MainContent` 外包 `Box(fillMaxSize)`，追加 `LongTaskHost()` 根部覆盖；配平 (0,1)≡基线。
- 字符串复用现有 close/cancel，无新增。
- 下一步（下41）：BackupScreen 导出/恢复改走注册表，删除其局部三件套（progressValue/progressMessage/showProgress）与内嵌进度卡。

### [批次17·下41] F-16 方案B 第2步：BackupScreen 全面接入（导出+恢复）
- **框架补** `LongTaskRegistry.updateDetail(key, detail)`（只改文案、进度保持——恢复流程中 `refreshing_plugin_list` 这种「换文案不换进度」的站点专用）。
- **BackupScreen**：
  - 删局部三件套 `progressValue/progressMessage/showProgress` 声明与全部 47 处赋值；
  - 导出（`LONG_TASK_EXPORT`）/恢复（`LONG_TASK_RESTORE`）：`begin` →15 处 `update(key,msg,value)`（6 导出 + 9 恢复）+1 处 `updateDetail` → 成功 `update(...,1f)+finish`，失败 `fail`（`lastError` 内联与 Toast 的 G-07 口径保留），无插件早退 `dismiss`，`finally` 只清 `isLoading/showRestoreConfirm`；
  - 删内嵌进度卡（`if (showProgress) item{...}`），空列表门 `!showProgress` → `!isLoading`；
  - 删不再用的 `UnifiedCaptionText/UnifiedLinearProgressIndicator` import，增 `LongTaskRegistry` import + 2 个私有键常量。
- 进度 UI 现由 MainActivity 根部 `LongTaskHost` 模态展示（后台切换/离开备份页任务仍可见）；成功 1.5s 自动收起。
- 静态校验：BackupScreen/LongTask 配平 (0,0)≡(0,0) 基线；终检 `showProgress/progressValue/progressMessage/两组件引用` 全仓 0 残留；`UnifiedCard` 余 4 处仍用 import 有效。

### [批次17·下42] F-16 方案B 第3步：ContainerManagementScreen 接入（导入/导出/下载）
- **替换**：删 `ProgressInfo` data class + `progressInfo` 局部状态 + 整个进度对话框（`// Progress dialog` 块）；新增局部 `publishContainerProgress(title,current,total,bytes,totalBytes,currentFile)`——**逐字复刻**原对话框的文案拼装（`container_export_progress`/`container_import_entry_count`/字节对 `formatSize`/空则 `container_export_scanning`/`currentFile` 追加），进度 fraction `total>0 → current/total，否则 null`（原对话框此处是定长 0f，改为不定长条——记录的行为差异）。
- **三流接入**（键 `CONTAINER_TASK="container_op"`，`isInstalling` 串行门禁下同时只有一个）：
  - 导入：`begin(container_importing)` → 回调 `publish` → 成功 `finish`/失败 `fail(message)`，catch 补 `fail`，finally 只清 `isInstalling`；
  - 导出：`begin(exporting,name)` → 回调 `publish` → finish/fail，catch/finally 同上（`exportContainerName` 清理保留）；
  - 下载：`begin(downloading)` →100ms 节流回调 `publish(progressTitle,count,0,…)` → finish/fail。
- `statusMessage` 屏内行内反馈（line444 等）**原样保留**（G-20/G-21 口径不动）；`formatSize` 文件级函数复用。
- 静态校验：whole-text 配平新 (0,0) vs 旧 (0,-1)——经**逐行前缀扫描**判定旧值为跨行字符串剥离伪影，两版均 (0,0) 且前缀恒非负；`progressInfo/ProgressInfo(` 0 残留；`LongTaskRegistry.`12 处 + `publish` 4 处。
- **F-16 完成度**：框架 + BackupScreen（导出/恢复）+ ContainerManagement（导入/导出/下载）三大户接入；框架键冲突由单活跃任务 + key 匹配更新兜底。

### [批次17·下43] D-42 方案A1：PluginInfo 死运行时字段删除（状态外移第一步）
- **决策**：D-42 拆分三步——A1（本次，删死字段）→ A2（var→val data class 收敛，延后）→ A3（签名从自证改真校验，安全设计，延后）。
- **删除**：`PluginInfo.kt` 尾部「运行时状态」段 4 字段 `isBackendRunning/currentBackendPort/backendPid/lastStartTime` + 注释头（529→523 行）；`openWith` 前尾逗号收敛。
- **核实依据**：4 字段全仓零读点（仅命中的3处是 `PluginManager.isBackendRunning(pluginId)`/`PluginInterface.isBackendRunning()` 两个**同名无关函数**）；JSON `put/get` 只序列化 `backendPort` 等配置字段（与 `currentBackendPort` 无关）；`SourceInfo/RepoPluginInfo` 无同类死字段（`isInstalled/hasUpdate` 为活的 UI 态，属 A2 范畴）。
- 校验：逐行配平 old/new 均 (0,0,0,0)（注：自写 linescan 曾出三元表达式 bug 导致 pd 翻倍，重写后正确）；`grep` 死字段名 domain/测试 0 命中；`openWith` 8 处完好。

### [批次17·下44] B-81 方案B：core/ 只留契约文件，3个非契约单元挪出
- **决策依据**：`proguard-rules.pro:28/33` `-keep public interface PluginInterface` 表明 `core.plugin` 是对外 SDK 契约，移包会破坏外部插件二进制兼容 → **core.plugin 原地保留**；建议里的 `log/cache` 本就不在 core（清单过期）。
- **搬迁**（包前缀 `core.X → 顶层 X`，文件同名移动，空目录已删）：
  - `core/accessibility/UinAccessibility.kt` → `accessibility/`（类 `UinAccessibilityService`）
  - `core/compiler/{CompilerUtils,JavaToDexCompiler}.kt` → `compiler/`
  - `core/bootstrap/EnvironmentBootstrap.kt` → `bootstrap/`
- **同步改引用**（共5文件）：`Manifest:450` `.core.accessibility.UinAccessibilityService` → `.accessibility...`；`UinApplication:159`/`PluginHostActivity:414/419` FQN bootstrap 调用；`AppContainer:60`/`BasePluginWizardScreen:30` FQN/import compiler；`JavaToDexCompiler.kt:1` 头注释路径修正。
- **core 现状** = `di/ plugin/ update/`（2858→约2100行）。keep 规则**无需改**：`app-keep.pro` `core.**` 仍覆盖保留的三包（超集无害）；挪出的 accessibility 为 Manifest 组件由 AGP 自动 keep，compiler/bootstrap 静态可达；全仓无类名反射（仅 TAG 字面量）、assets 无 FQCN 引用。
- 校验：8 个触碰文件逐行配平全部与基线一致（PluginHostActivity 的 (-2,8) 为既有基线怪癖）；Manifest minidom OK；`grep com.UIN.Tool.core` 残留全部属 di/plugin/update；设备侧由同步脚本反向删除4个旧路径。

### [批次17·下45] F-12 过期判定 + ConfigFieldRow 空包装删除
- **核实推翻原前提**：dev/ 全区**零裸 TextField/BasicTextField**（唯一 `OutlinedTextField` 为 JSON 语法高亮编辑器，`visualTransformation=highlighter` 合理保留）；向导/对话框已统一 `UnifiedTextField`（原计数18=子串误计）；「签名」区仅 `DevToolsScreen` 的 Switch 开关无输入框；`Preview` composable 不存在。**F-12 判过期关闭**（复用目标已达成）。
- **残余冗余**：`ConfigFieldRow` 曾承载字段级信息图标、图标移除后退化为 `Box(modifier){content()}` 空包装，PluginWizardSteps 内20处（定义1+用19）。
- **删除**：15 处裸包直接解包（内层反缩进4空格）+4 处 `weight` 包装解包且 `Modifier.weight(1f)` 迁入子 `UnifiedTextField`（`weight(1f).fillMaxWidth()`，RowScope 直接子级语义等价）；删定义（含 doc 注释）。1023→966 行（−57）。
- 校验：逐行配平 old/new 均 (0,0,0,0)；`ConfigFieldRow` 全仓 0 残留；`Box(` 余3处（他用，import 保留）；抽查裸包/weight/PermissionPickerField 三形态解包正确、定义区空行分隔完好；`error=` 行既有错位缩进为原样保留。
- **决策队列状态**：本批完成 D-42→A1、B-81→B、F-12→关；**D-42 A2（var→val）/A3（真签名）登记为后续独立项**；剩余 = 阻塞项 G-11/E-20、删除类（等用户逐项）、A2/A3 后续项。

### [批次17·下46] B-43族拍板执行：core/plugin 死文件8件删除 + B-44/45/46/49/51 随之解决
- **拍板**：用户选定 core/plugin 缺陷组后，核实5项（B-44/45/46/49/51）修复方式全部=删除（随 B-43族），遂对 B-43族 整组拍板「删8件+修keep」。
- **死活普查（逐一 grep 实证）**：删 `WebPluginProxy`（全仓从未被构造）、`PluginWebInterface`（唯一使用者=proxy）、`PluginWebChromeClient`、`PluginSharedData`（init 零调用）、`PluginBroadcastReceiver`（零引用+Manifest 未注册）、`IPluginCommunication`、`PluginContext`（core 版为重复，`plugin/PluginContext` 才活）、`PluginInterface`（core 版零 importer，`plugin/PluginInterface` 才是活契约）；共919行。**保留** `PluginEventBus`/`PluginMessage`（活：PluginManager/PluginBackendManager 引用）；幸存者对8死件零同包依赖（先验后删）。
- **keep 修正**：`proguard-rules.pro` 删死契约行 `-keep public interface com.UIN.Tool.core.plugin.PluginInterface`（**活契约 `plugin.PluginInterface` 的 keep 在27行早已存在**，先前只 grep `core` 漏看，误报「活契约无 keep」已更正）；`-keep class com.UIN.Tool.core.plugin.**` 通配保留（继续覆盖 EventBus/Message）；注释中「Web 代理、广播接收器」字样随删同步移除。
- **残留**：`pluginshareddata_not_initialized_cal` 双语字符串成孤儿（代码随文件死）——按既定约定 **strings 删除属拍板类，保留**（同 G-16 permission_explain_* 处理），parity 脚本不受影响（双语齐全）。
- 终检：死类名全仓引用 0（除孤儿字符串）；`core.plugin` 仅剩2包声明+EventBus 2引用+keep通配。

### [批次17·下47] C-10 审计基线：JS 桥5个关键入口审计（零改码，判定全 PASS）
- **审计对象**（清单锚点→现行行号，文件2926行）：
  1. **callHost:237** — 固定 `when` 分发16个良性动作（toast/log/finish/vibrate/copy/setTitle…），无任意方法分发/反射；未知 action 仅告警；其中 openUrl 路径再入 C-15 scheme 白名单。**PASS**（附注：`data` 全量进 Info 日志，本地可接受）。
  2. **getSerialNumber:299** — 三重闸（READ_PHONE_STATE）；API≥O 无特权 `Build.getSerial()` 抛 SecurityException→捕获返 "unknown"（fail-closed）；API29+ 同理恒抛。**PASS**。
  3. **getMacAddress:313** — 三重闸（READ_PHONE_STATE 或 ACCESS_WIFI_STATE）；现代 Android 本就返回匿名 MAC。**PASS**。
  4. **getLocation:660** — 三重闸（FINE 或 COARSE）；无 provider 返显式错误。**PASS**（getDeviceId 内额外的 `ContextCompat` 双查是冗余——`hasPluginPermission` 已含宿主授权检查，无害不一致，不动）。
  5. **openUrl:2450** — **已由 C-15 加固**：`ALLOWED_URL_SCHEMES={http,https,mailto,tel}` 拦截 `intent://`/`file://`/`javascript:`/`market://`。**PASS**。
- **机制确认**：`hasPluginPermission`(2881) = 插件声明 ∧ 宿主授权(运行时/特殊,2864) ∧ 未封禁(2870)；敏感接口统一走 `CAPABILITY_MAP`→`checkCapability`(2909) 声明式 allowlist（「唯一真源」KDoc:93）。
- **清单缺陷记录**：C-10 所引「权限矩阵见 4.3」在 TODO233 中**不存在**（断链交叉引用）；事实矩阵=CAPABILITY_MAP+hasPluginPermission 双机制（本审计即基线）。**C-10 判已审计关闭，零代码改动**。

### [批次17·下48] F-13/G-23 判已解决 + E-23 全量迁移执行（E-24/E-25 判定修正）
- **F-13 判已解决**（零改码）：D-29 已收敛——`RepoViewModel` 走 `ServiceLocator.getGitHubApiService/getOkHttpClient`、`GitHubMirrorViewModel` 走 `getMirrorManager()`、UI 层 `MirrorManager(` 手工 new **零残留**（仅 AppContainer 合规单例 + UpdateChecker 内部自有 client，不在 D-29 点名清单）；存活 VM 现为4个（Repo/GitHubMirror/Update/BackendSettings）**全部有引用**——「9个死VM」早已在早前批次删除，F-13「死VM待决策」子句失效。**G-23 同判已解决**（无死VM可决）。
- **E-24 判早已执行**：`NeumorphicComponents.kt` 与引用者 `UIConfigPreviewScreen` 均已不存在（零引用）；**E-25 判过期误判**：`UIComponents.kt` 当时活跃（57处调用×19文件）。
- **E-23 全量迁移**（用户拍板「本批全量迁移」）：
  - **关键发现**：UIComponents 多数成员已是 Unified 薄适配器（PrimaryButton→UnifiedButton、Card→UnifiedCard、对话框/ListItem/TextInput 同理），故迁移=改名而非重写；`UnifiedButton` 默认 variant=Primary/size=Medium、`UnifiedCard` 默认=适配器回退值 → 直接改名等价。
  - **映射**（12成员）：7个改名直换（PrimaryButton→`UnifiedButton`、SecondaryButton→`UnifiedButton(variant = ButtonVariant.Outlined, `仅2站插参、TitleText/BodyText/CaptionText→Unified*Text、Card→`UnifiedCard`、ConfirmDialog→`UnifiedConfirmDialog`）；5个自实现成员（ManageTopAppBar/PullRefreshIndicator/LastUpdatedCaption/PluginListSkeleton/currentTimeString）**整体搬迁**至新文件 `unified/UnifiedPageChrome.kt`（214行，体部逐字迁移，CaptionText 内部改调 UnifiedCaptionText）。
  - **导入手术**：19文件删 `import ...UIComponents`；已有 `unified.*` 的10文件仅删行，无星标的9文件替换为 `import com.UIN.Tool.ui.components.unified.*`（与既有显式 unified 导入共存合法）；MainActivity 注释引用同步改名。
  - **UIComponents.kt（611行）随迁空删除**——属「全量迁移」拍板的固有收尾。
  - **提取器两bug与修法**：①无大括号表达式函数（currentTimeString）向前吞掉下一成员 → 改用对象成员边界 `^    \}$` 定位结束；②参数默认值 `= {}` 使括号计数提前归零截断 ManageTopAppBar → 同样由缩进边界解决；断言=5块互异+各含特征串。
  - 校验：19文件逐行配平与基线**全等**；`UnifiedPageChrome.kt` (0,0,0,0)；`UIComponents` 全仓0引用；统一包内5新名零冲突（unified 无裸 Card/IconButton/TextButton 与 m3 星导入撞名）；SecondaryButton 2站插参实样正确。

### [批次17·下49] 拍板执行：B-30 自权限申请流删除 + B-35 首启 IO 移出主线程
- **拍板来源**：五组决策问题（Kotlin死码/自权限/资源/assets/大改项），本批执行 ② 与随批非删除项 B-35。
- **B-30 删除（SplashActivity 1068→1047行）**：删 `customPermissionLauncher` 定义、`requestCustomPermissionIfNeeded()` 函数、`hasRequestedCustomPermission` 状态、4处调用（requestPermissionGranted/onCreate老用户补申请/checkPermissionAfterReturn/requestNormalStoragePermission）、2条字符串（`custom_permission_granted`/`custom_permission_request_failed` 双语同步删，2380→2378 parity OK）。**依据**：C-01 后宿主 exported=false，OPEN_PLUGIN「防第三方拉起」用途消失；全仓无组件挂 `android:permission`；申请结果仅打日志；Manifest `<permission>`×2 + `<uses-permission>`×2 按 A-42 既定「预留门禁」**保留不动**。随删 `AppConstants.OPEN_PLUGIN_PERMISSION/RUN_COMMAND_PERMISSION` 转为零引用（登记下50 B-76 一并删）。
- **B-35**：`createWorkDirectory()`（8×mkdirs+Logger.init+日志横幅）整体移 `lifecycleScope.launch(Dispatchers.IO)`；`workDirectoryReady`→`@Volatile` + 新增 `@Volatile workDirectoryInProgress` 双闸（onResume 连触防重入）；Toast 改 `withContext(Dispatchers.Main)`；`preferenceManager.setWorkFolder` 线程安全（SharedPreferences apply）。**行为差异记录**：工作目录创建与开屏700ms倒计时/更新检查并行，导航不再隐式等待 mkdirs 完成——MainActivity:158 自有 `Logger.init` 兜底，mkdirs 紧随完成，风险低。
- 校验：Splash 逐行配平 (0,0,0,0)；B-30 全部标识符 0 残留；parity 2378 OK。

### [批次17·下50] ①Kotlin 死代码组执行（拍板：全删）— 8类目标、5文件删改+3文件移除
- **C-05×6**：`PluginManager` 删 `clearWebViewCache`(带@JvmStatic)、`updatePlugin`(含原子更新doc，61行)、`hasPluginUpdate`、`showPermissionGuidance`(空壳，只调onReRequest)、`clearAllPlugins`；`PluginBackendManager` 删 `shutdown`、`probeRealTermux`(含"实体Termux前端探测"横幅)。**终检0残留**：`probeRealTermux` 余2处=PluginBackendManager 自身注释/perfLog标签（描述"probe已跳过"仍属实，纯字符串无编译影响，保留）；`shutdown` 全仓命中=SharedSupervisor 的 shutdown 标记文件机制（另一事物）。
- **C-08/C-35**：`probeRealTermux`(BackendManager:710)、`generatePluginJson`(TemplateUtils:157，JSON手拼版) 随上删除；向导走 `PluginWizardViewModel.generatePluginJsonContent`(活)不受影响。
- **整文件删除3件**：`proot/RootfsSetup.kt`、`data/remote/NetworkClient.kt`、`compiler/CompilerUtils.kt` —— 均先扫**顶层声明名**（RootfsSetup/NetworkClient/CompilerUtils 均为 object，成员 createService/copyAssetToCache/findJavaFiles/findClassFiles 只能经 object 限定或含 object 名的 import 访问）确认0引用后删；proguard 无相关 keep。
- **B-55**：删 `ServiceLocator.getJavaToDexCompiler` + `AppContainer.javaToDexCompiler` lazy（同生共死，唯一消费者即该getter）；`JavaToDexCompiler` 类本体活（BasePluginWizardScreen:269/313 直接构造）。
- **D-36**：`ContainerConfig` 删 `isReady` 属性 + `companion default(context)`（companion 随之整块移除，类降为无 body 的 data class）；`environmentVars` 活（D-18已接）保留；`ProotContainerManager.isReady(context)` 是另一类的同名活方法，不受影响。
- **B-76**：AppConstants 删 **44常量**（42纯死 + B-30 转死的 OPEN_PLUGIN_PERMISSION/RUN_COMMAND_PERMISSION）+ 6个清空节头（自定义权限/权限请求码/Activity结果码/偏好设置键（镜像主题小部件）/UI配置/其他常量）；88→44常量。保守保留 `APP_NAME`/`PREF_MAIN`（文件内二次使用）。终检 zero-outside 仅余这2个保守项。
- **Manifest**：A-42 注释同步 B-30（原句"SplashActivity/设置页却在申请它"已过期→改为"B-30 已删除该无用申请流"），XML minidom 解析通过。
- **新转死（未在拍板范围，记录不删）**：`PluginManager.preserveUserData`（唯一调用方=已删的updatePlugin）；C-34/F-10 时期形成的 updatePlugin 系列孤儿 strings（`plugin_updated_*`/`user_data_preserved_*`/`all_plugins_cleaned`/`backend_message_service_stopped`/`real_termux_probe_failed_*` 等）按既定约定保留。
- 校验：8个改动文件逐行配平前后均 (0,0,0,0)；删除标识符全仓 0 残留（方法引用形态 `::name` 亦查）；死常量复扫通过。

### [批次17·下51] ③资源组执行（拍板：非strings全删）— 186项
- **A-25/A-26**：删 116/136 零引用 `res/drawable/*.xml`（含拼写重复对 glass_vackground_dark + glass_background_dark——两者均死）。**保留20个**活 drawable。
- **A-32**：`values/dimens.xml` 删 45/50 死 `<dimen>`（无 values-night/dimens.xml，无需双侧同步）。
- **A-33/G-29**：删 7 layout（activity_settings/dialog_rename_session/item_terminal_sessions_list/preference_markdown_text/view_terminal_toolbar_text_input/widget_layout/widget_list_item）、2 menu（bottom_nav_menu/repo_menu）、5 `res/color` CSL（bottom_nav_color/checkbox_color/radio_color/switch_color/switch_track_color）、3 anim（anim/slide_in_top/slide_out_top）、`res/xml/widget_info.xml`（0引用，活的是 uin_widget_info）。
- **A-34**：删 7 死主题——themes.xml×4（Theme.UIN.Tool.NoActionBar/Markdown/Translucent/Dialog）+ styles.xml×3（TermuxAlertDialogStyle、TermuxActivity.Drawer.ButtonBarStyle.Light、ThemeOverlay.UIN.Tool.Toolbar）；`Theme.UIN.Tool.Splash`/`WindowAnimation`/`TermuxActivity.Drawer.ButtonBarStyle.Dark` 等活主题保留。
- **删除前三重终检**（吸取样式类点号/下划线形态教训）：①全树任意扩展名复扫 0 残留；②Java 侧 `R.style.` 引用=**0条**（死主题不可能被下划线形态命中）；③`parent="X"`/`@style/X` 全 res 扫描——唯一命中=Markdown(同死)的 parent 指向 NoActionBar(同死)，成对删除安全；`.Light` 无任何 child、`TermuxActivity.Drawer.ButtonBarStyle.Dark` parent 非 `.Light`。dimen/drawable 另做「排除自身目录」的反向 blob 终检=0残留。
- **未做（按拍板）**：shrinkResources 不开（`ContainerManagementScreen:835` 存在 `getIdentifier` 动态取 string，开了会误删）；A-29×576 strings 未动（拍板③=非strings）；A-27/A-28/A-30 图标与键名规范化未选。
- 校验：dimens/themes/styles 三个被改 XML minidom 解析通过；最终全树残检 0；parity 2378 不受影响。

### [批次17·下52] ④assets 执行（用户指定：删3文本，留 proot 全部 + host-sdk.jar）
- 删 `assets/Github_Mirrors.txt`、`assets/rootfs-check.sh`、`assets/ui_config.json`（本地+设备双侧；同步脚本只反删 .kt/.xml，assets 需手动）。终检：Kotlin 与 assets 内 .sh 侧均 0 读取（MirrorManager 走 `DEFAULT_MIRRORS` 常量+prefs；UIConfig 不经 assets 读 json；备份功能用 filesDir 轨道）。
- **按用户改判保留**：`assets/proot/` 四个 ABI 二进制**全部保留**（推翻 A-51 的删3留1提议）、`debian-rootfs.tar.xz`（A-52 未拍板）、`host-sdk.jar`（既定）、rootfs-check.sh 原「留待接 G-36 接线」选项**未选**→直接删。

### [批次17·下53] D-42 A2 执行（拍板选项）：三模型 74 var→val + 4处突变点收敛
- **范围**：清单 D-42 点名的 `PluginInfo`(45 var)、`SourceInfo`(9)、`RepoPluginInfo`(20) 全量 var→val；MirrorItem/ReleaseInfo 不在点名范围未动。
- **突变点普查方法**：限定式 `.field =` 全仓扫 + 无限定行首赋值全仓扫（431命中逐条人工核=Compose具名参/构造器/copy实参/其他类型），排除项实证：RootfsManager `entry.size`=TarArchiveEntry、PHA `backendPort`=Activity自有字段(112行)、UpdateChecker `release.*`=ReleaseInfo。
- **4处真突变重构**（均保语义）：
  1. **GitHubApiService.fetchSourcePlugins**：`RepoPluginInfo()` + 16字段后赋值 → 单构造器实参（tpkPath/iconPath 条件改为构造时 `if` 表达式，默认图标路径逻辑逐字等价）。
  2. **RepoViewModel.checkForUpdates**：原地改 `plugin.isInstalled/installedVersion/hasUpdate` → `plugins.map { copy(...) }` 返回新列表；调用点改为 `val updatedPlugins = checkForUpdates(plugins)` 后赋 `allPlugins/_plugins/_filteredPlugins`（附带修复：原突变发生在 StateFlow 赋值之后且同实例，徽标要等下次重 emit 才可见——现在立即可见；行为差异已记录）。`hasUpdate` copy 实参与原条件逐字同义。
  3. **RepoViewModel 安装后 sourceId**：`info.sourceId = ...` → `installedInfo.copy(sourceId = plugin.sourceId)`（原 info 是 installPlugin 一次性返回实例，refreshPlugins 已在安装内部先行触发，突变本就不落盘；copy 保持调用形态完全等价）。
  4. **PluginInfo**：a) `fromJson` 把 D-40 的 `backendEnv/frontendConfig` 构造后补赋值**折入构造器**（补了 openWith 尾逗号）；b) `migrateLegacyBackend()` 唯一调用方=fromJson → 改签名返回 `PluginInfo`（早退返 `this`，主体返 `copy(backendStartCommand/backendStartEntry/backend/backendRuntime/backendPreCommand)`，5字段与原赋值逐字同）；c) **PluginManager:244 目录名回填** → `if (isEmpty) copy(pluginId=dir.name) else info`（对齐既有 `updatePluginCategory` 的 copy+持久化先例，D-41 批次已确立）。
- **过程bug与修复**：①openWith 闭合 `}` 后漏逗号（插入 backendEnv 实参时）——现场读行发现并补；②fromJson 改链式后 try 块末语句变 `val` 声明（非表达式）会编译错——补回尾表达式 `info`。两处均在终检前拦截。
- **终检**：3模型 `var` 残留0；限定式突变0（3非本类命中已实证归属）；6改动文件逐行配平全 (0,0,0,0)；无反射序列化器（无 Gson/Moshi/kotlinx.serialization）——全 val 安全。

### [批次17·下54] D-43 前提核实→整层删除（拍板：删整层）
- **核实过程（拍板依据）**：读 IConfigRepository(70行/28方法) 与 ConfigRepositoryImpl(157行) 发现实现 100% 是对 PreferenceManager 的一行转发；全仓扫 `IConfigRepository`/`ConfigRepositoryImpl`/`getConfigRepository`/`configRepository`（含 `::` 方法引用形态）→ **3 处引用全是定义/装配，方法调用 0 处**：MainActivity:103 声明 `configRepository` 后从未使用（死字段）；清单 B-36 早已标注「IConfigRepository 两个死包装」。据此向用户提问，用户拍板「删整层」。
- **删除清单**：
  1. 文件删除：`domain/repository/IConfigRepository.kt`（28 方法抽象）+ `data/repository/ConfigRepositoryImpl.kt`（157 行纯转发）。
  2. `AppContainer.kt`：摘 2 个 import + `configRepository` lazy 属性（「仓库」分区保留 pluginRepository）。
  3. `ServiceLocator.kt`：摘 `getConfigRepository()` 访问器。
  4. `MainActivity.kt`：摘 import + :103 死字段。
- **连带核查**：`AppContainer.preferenceManager` 未孤儿化（UpdateChecker:45、ServiceLocator.getPreferenceManager 仍在消费）；MainActivity 自建 PreferenceManager(:145) 属既有行为未动。
- **终检**：全仓 4 关键字残留 0 命中；3 改动文件配平 (0,0,0,0)；domain/repository 余 IBackupRepository/IPluginRepository 两接口。配置读写单真源=PreferenceManager（D-28 既有格局）。
- 后续（登记，非本批）：UpdateChecker 内 `release.downloadUrl`（ReleaseInfo 仍 var）留待其批次；B-36 已核实于批次17·下5 完成（原备注误记为待办，已更正）。

### [批次17·下55] 清单批次17 全量对账回写（110项划线，233→16）
- **背景**：`TODO233.md` 划线约定为「批次11~16 回写」，批次17 全部 54 个子批次执行后**从未回写**——125 项未划线中 109 项实际已执行。
- **对账方法**：以 UC 批次17 全部块为证据源，对 125 未划线项 + A-50b 逐项建证据窗口（ID→批次标题+特征行），逐项判定三类：已执行（划线）、部分执行/留决策（保留）、拍板未选/阻塞（保留）；证据不足项回读清单原文与 UC 块全文复核。自动分类脚本以「零未分类」为断言，首轮漏 3 项（B-80/F-01/B-26）即报错，补齐后通过。
- **划线 110 项**：按子批次归档（`→ 已处理（见 UC 批次17·下X）`），特殊判定单独注明——A-25（shrinkResources 按拍板不开）、A-51（用户拍板保留）、E-24（判早已执行）、E-25（判过期误判，文件已随 E-23 删除）、F-12（判过期关闭）、F-13/G-23（判已解决）、C-10（判已审计关闭）、E-03/E-05（过期判定）、B-08/B-22（批次12 已修，17·上复核）。
- **补证**：D-30/B-67 两项 UC 中无 ID 级证据——回查下50「整文件删除3件」实为 `NetworkClient.kt`/`CompilerUtils.kt`，正是该两项建议目标 → 划线。B-36 核实批次17·下5 已完成（Splash 侧统一+UpdateChecker 死包装删除，IConfigRepository 部分随下54 关闭），**更正下54 中「B-36 留待各自批次」的过时备注**。
- **保留 16 项**（真实未闭环）：A-27/28/29/30（图标键名规范化，拍板未选）、A-52（rootfs 43MB 未拍板）、B-40（TrampolineActivity 删除待批）、B-71/B-78/D-34/G-06（共享存储→私有目录决策组，脱敏/异步部分已完成）、D-35（proot 9 处双用途文案留决策）、D-42（A3 真签名未做）、E-11/E-26（死色值/死 Preview）、E-20（随主题决策）、G-11（日志查看器随 D-34 迁移）。
- **终检**：划线格式 217 条全合规（~~ 头行+后缀，无半截/坏对）；217+16=233 ✓；标题更新为「剩余 16 条（原 233，按优先级）」。

### [批次17·下56] 删除拍板三项执行：E-11 死色值 / E-26 死 Preview / B-40 TrampolineActivity
- **前提重实证（清单部分过期）**：`ui/theme/Color.kt` 已不存在、Theme.kt 仅 522 行（清单引用的 487-640 段已消失）、三组扩展（LinearProgressGradients/BlockGradientColors/VoteColors）与 theme 三文件 @Preview 均 0 命中 → E-11/E-26 的原始目标已随既往批次演化消失。
- **B-40 可达性实证后删**：LAUNCHER 归属=SplashActivity（Manifest:123-130）；TrampolineActivity 声明 exported=false、无 intent-filter、仓内零启动点（A-45 早已摘 filter）→ 安全删除。**删**：`TrampolineActivity.kt`(115行)、Manifest 跳板声明块（含 A-45 注释头，minidom 校验 OK）、MainActivity 连带残留（`EXTRA_FROM_TRAMPOLINE` 常量+kdoc、onCreate/onNewIntent 两处 `!getBooleanExtra(...)` 条件行、B-42 注释 2 行——extra 已无人置入，条件退化为恒真项）。
- **E-11 名下新实证残余**：对 Theme.kt 全部色值常量做「外部引用+文件内引用」双计数普查——**25 个双零死色值**删除（SuccessGreen/WarningOrange/TextHint/TextDisabled/BackgroundCardWhite/BackgroundDarkGray/SurfaceCardWhite/CardShadow/CardRipple/Glass×3/Overlay×3/Gray×4/NavItem×2/Gradient×4），连带 6 个整空节头（卡片相关/玻璃效果/叠加色/灰色调/导航栏/渐变）；**保活**喂给 LightColorScheme/DarkColorScheme 的供线（Primary*/AccentBlue/ErrorRed/InfoBlue/Text*/BackgroundGray/Surface*/DividerGray/BorderGray/Dark*）——两色板本身活（Theme.kt:325 动态基底 + :421 回退）。
- **E-26**：theme 三文件 @Preview 计数 0 → 判前提过期，零改码关闭。
- **终检**：25 色值 + EXTRA_FROM_TRAMPOLINE + TrampolineActivity 全仓 0 残留；Theme.kt/MainActivity.kt 配平 (0,0,0,0)；Manifest XML 解析通过；清单划线 3（余 13）。

### [批次17·下57] A-27/28/29/30 资源规范化执行（拍板：仅规范化，A-52 不动）
- **A-27 判前提过期**：10 件目标（ic_export/import/error/help/change_category/dev_tools/nav_dev/card_log/view_docs/bg_button_flat/bg_card_clickable）全部已随下51 消失；导出/导入语义图标现走 Compose `Icons.filled.*`，无可合并对象 → 零改码关闭。
- **A-28**：三套命名体系目标（ic_back/arrow_back、next/prev/chevron）已随下51 消失；残余 `current_session_black.xml`/`session_ripple_black.xml` 全树（app/src 全扩展名）0 引用实证后删除——二者实为亮色版的暗色配色变体，selector 只引用亮色版；drawable 20→18。暗色需求如再现应走 `drawable-night/` 同名限定符（记录，未实施）。
- **A-29 执行**：引用扫描通道=①R.string token 全语料（app/src 所有 .kt/.java/.xml）②@string/@array 全 XML ③裸键名 token 守恒（防 getIdentifier/字面量动态取值）④descriptionKey 4 个位置参数字面量白名单（ContainerManagementScreen:131-134，唯一 getIdentifier 点 :835）⑤Str.get 仅收 Int 资源 ID（无按名重载）。单遍分词法（避免 2378×全库重扫超时）→ **568 零引用键**（清单基线 501 + 既往死码批次孤儿，含下46/下50 登记的 plugin_updated_*/pluginshareddata_* 孤儿串——A-29 即其拍板出口）双侧删除，2378→1810×2，parity 通过、XML 解析通过、键唯一。
- **A-30 双执行**：
  - **formatted="false"**：带裸 `%` 判定（valid spec/%% 之外即裸）——en/zh 全部 1810 键**零裸%** → 双侧属性全清（3620 处；清单时代 1676 中的有%者已随 A-29 键删除）。
  - **截断键重命名**：精确检测=「key 是 slug(value) 的前缀且第 37 字符落于词中（len≥33）」→ **115 键**（含 4 个 `_2` 后缀变体），新名=slug 按词界取 ≤40 字符最长前缀；4 键因与现存键冲突保留原名（launchedeffect_..._a_2/success_false_error_could_not_get_st/you_are_already..._versio_2/your_launcher...pinnin_2，日志类，宁留不撞）。改写=`name=`×2 侧 + `R.string.`/`@string.` ×138 处/32 文件（词边界正则，`_versio` vs `_versio_2` 前缀类由 \b 区隔）。
- **终检**：旧键定义/引用全 0；新键定义两侧各 1、键唯一；formatted 残留 0；parity 1810 对齐 0 占位符差异；双 XML 解析通过；清单划线 4（余 12）。

### [批次17·下58] 存储迁移整组执行（拍板：B-71/B-78/D-34/G-06/G-11 本批整组）
- **路径切换（AppConstants.kt）**：敏感三目录 `LOG_DIR/BACKUP_DIR/TPK_DIR` 从共享 `/sdcard/UIN_Tool/*` 改为 `getExternalFilesDir()/` 计算属性（新增 `init(context)` + `PRIVATE_ROOT`，未初始化读取即 error 快速失败防静默写错位）；`WORK_DIR/PLUGIN_DIR/DOWNLOAD_DIR/TEMP_DIR/CACHE_DIR` 留共享（插件/下载仍走 MANAGE 面，B-78 残留面如实保留）。`Constants.init(this)` 落 `UinApplication.onCreate` 最早段（line84，先于 loggerInit 等一切 LOG_DIR 访问）。
- **旧数据搬迁（SplashActivity.createWorkDirectory，IO 协程）**：`logs/backups/tpk` 三目录旧共享件→私有，幂等（目标同名存在即跳过并作废旧件——仅同日日志可能碰撞 Logger.init 新建件；renameTo 失败回退 copy+delete；迁空后 `legacy.delete()` 删旧目录；失败件留原地次启重试）。搬迁量写日志（新串 `storage_migration_moved` %1$s/%2$d × 双语）。
- **配套摘除**：`UinApplication.initWorkDirectory` 的共享子目录清单去掉 `"logs"`（防每启动重建旧共享日志目录）。
- **随迁自动闭合**：G-11（LogViewerScreen/DevToolsScreen 读 `Constants.LOG_DIR` 单点，零改码）、G-06（BackupScreen/BackupRepositoryImpl 读 `Constants.BACKUP_DIR`，备份功能自身不再碰共享存储）。G-06 余留说明：MainActivity 全局 MANAGE 硬门（Exit 按钮）因插件/下载仍在共享而保留，属 B-78 残留面、不在本项建议内。
- **文档同步 8 行**：Help 日志位置（en/zh 各 2 行）+ README tpk 位置、日志位置（en/zh 各 2 行）改 `/storage/emulated/0/Android/data/com.UIN.Tool/files/…`，并补 Android 11+ `Android/data` 浏览受限→经电脑 USB 或应用内「导出日志」说明；plugins/templates/工作目录路径不动（确为共享、扫描确认 0 处需改）。
- **反馈路径**：原「日志落共享供用户取用反馈」注释撤销，改注 LogViewer SAF 导出（exportLauncher 已有，不依赖共享目录）。
- **终检**：3 个改动 .kt 剥字面量括号扫描全 (0,0,0,0)；parity **1811 对齐 0 占位符差异**；代码残留（`WORK_DIR,"logs"` 等）仅迁移块与私有构造两处合法点；assets 旧路径 0；无属性级路径捕获（全部函数内运行时读取，init 时序安全）；`initWorkDirectory` 在 startup 任务列表 line141（init 之后）。

### [批次17·下59] D-35 双用途文案固定英文（拍板：大改三项仅执行 D-35）
- **先行侦察纠偏**：清单 5 处引用中 3 处已过期/自愈——`MirrorManager.kt:134`（现 data/remote/，:178 已是固定英文）、`BackupRepositoryImpl:67,69`（已固定英文；其 Str.get 全在 progress 回调/清单校验=UI 文案，本地化正确，不动）、`TERM/TerminalSession.java` 整文件不存在且 `[Process completed - press Enter]` 全工程 0 残留（随终端重写消失）。
- **真实残留 = 恰好 9 处中文异常文案**（ProotInstaller 62/66/70 + RootfsManager 148/153/241/246/337/342）：均为 `Result.failure(IOException(中文))`。消费面侦察证实其只进日志（PluginHostActivity:434/448/1326/1340、EnvironmentBootstrap:52/73 均 Logger.e；UI 失败弹窗用本地化 `R.string.proot_install_failed`/`debian_container_install_failed_make` 等独立串，不透传 e.message）→ 按建议「日志统一固定英文」英文化，**零新增资源键**。
- **9 处译文**：proot asset content invalid / failed to replace truncated proot binary / proot binary atomic rename failed / failed to clean old container dir / failed to rename staged container dir / insufficient storage: about N MB free space required ×2 / failed to create temp install dir ×2。
- **终检**：proot 包中文字符串 0；两文件剥字面量括号扫描 (0,0,0,0)；D-35 五处引用全部闭合。
- **注**：全库仍有 634 处日志走 Str.get（UinApplication/MainActivity 等，非 D-35 引用范围，且为工程现行约定——本批新增 `storage_migration_moved` 亦循此约定）。是否全局反向属新决策，未在本项内动。

### [批次17·下60] 存储方向回调：日志/备份/TPK 回迁共享工作目录（用户拍板 2.0：便利优先）
- **决策**：撤销下58 的私有化，三目录回 `/storage/emulated/0/UIN_Tool/{logs,backups,tpk}`。用户质疑「迁私有不方便用户，放原工作目录行不行」，经评估采纳——**理由**：①`UIN_Tool/` 整个工作目录本就共享（插件代码/下载/模板同侧），只藏 3 个子目录的隐私孤岛无安全增益；②App 已硬门 `MANAGE_EXTERNAL_STORAGE`（MainActivity 不给授权即退出），回迁权限零变化，G-06 的授权跳转问题是全 App 既有门槛而非备份独有；③B-71 脱敏（不写 token/绝对路径/剪贴板）已落地，兜住「他应用可读日志」唯一残余风险；④恢复原始「用户反馈要求」——文件管理器直接拷日志/备份。
- **代码回退**：`AppConstants` 三路径还原为 `File(WORK_DIR,…)` 纯 val，删除下58 的 `init(context)`/`PRIVATE_ROOT`/`privateRoot`/Context import（全库 0 残留）；`UinApplication` 移除 `Constants.init(this)`，`initWorkDirectory` 子目录清单补回 `"logs"`。
- **Splash 迁移块改反向兜底**：私有侧（`getExternalFilesDir(null)/{logs,backups,tpk}`）若有迁移残留 → 搬回共享（同名即作废旧件、renameTo 失败 copy+delete、迁空删私有子目录），未跑过迁移的设备私有侧不存在零开销；沿用 `storage_migration_moved` 串（方向中性）。
- **文档还原 8 行**：Help/README 中日志与 tpk 路径回 `/storage/emulated/0/UIN_Tool/…`，移除下58 所加两条 Android 11+ `Android/data` 取用说明（私有路径已不存在该问题面）。
- **清单修订**：B-71/B-78/D-34/G-06/G-11 五条划线备注追加「拍板2.0 回退/维持共享」结论（关闭性质由「迁私有」改为「反向决策+脱敏兜底」），余 3 条不变（A-52 搁置、D-42/E-20 进行中）。
- **终检**：3 改动 .kt 剥字面量括号扫描 (0,0,0,0)；`PRIVATE_ROOT`/`Constants.init` 0；docs `Android/data/com.UIN` 0；parity 1811 对齐 0 差异；`storage_migration_moved` 仍被使用。

### [批次17·下61] test_plugins 回 main（双构建内置）+ A-52 确认不动
- **用户拍板**：`app/src/debug/assets/test_plugins/`（8 个演示 TPK 约 123KB，下24 按 C-42 移入 debug-only）**回 `app/src/main/assets/test_plugins/`**——release 与 debug 均内置，便于用户借鉴参考；`app/src/debug/` 源集随之清空删除。方向为下24 的用户级反转（便利/借鉴优先，同拍板 2.0 口径）。
- **配套**：`DevScreen.exportTemplates` 注释块改回双构建口径（空列表分支 `no_tpk_templates_in_assets_test_plugins` 降级为异常兜底保留，正常路径 assets.list 恒非空）；`docs/{en,zh-cn}/README.md` 模板导出段口径改「debug 与 release 构建均内置（便于用户借鉴）」。
- **A-52 确认不动**：`debian-rootfs.tar.xz`（43MB main/assets）经用户**确认不动**（非搁置）——从剩余清单划线关闭，不实施任何优化/迁移。
- **终检**：`src/debug|debug/assets` 引用 0；DevScreen 配平 (0,0,0,0)；本地 `app/src/` 仅剩 main 源集；清单划线 +2（A-52、本项记录于 C-42 备注），余 2。

### [批次17·下62] E-20 判已解决（HelpScreen 深色兼容 + 文案硬编码，零改码关闭）
- **引用漂移**：清单 `HelpScreen.kt:60-74,107` 对应现 79 行文件，`:107` 不存在；原问题点已被既往批次结构性移除。
- **深色兼容（原「独立 Activity 主题下未知」）**：`HelpActivity`=AppCompatActivity + `UINToolTheme`；Manifest 主题 `Theme.UIN.Tool` 为 `Theme.MaterialComponents.DayNight.NoActionBar` 且 `values-night/themes.xml` 全套覆盖（含 `windowLightStatusBar=false` 双侧、statusBar/navBar 配色）；全夜间切换走下34 的 AppCompatDelegate 单源；WebView 文档 HTML 经 `MarkdownRenderer.toHtml` → `UIConfig.shouldUseDarkTheme()` 双套色（E-14/批次4）——E-09 的主题分裂已在下34 收口，Help 链路日/夜全通。
- **文案硬编码（原 :107）**：文件中文面仅 2 行注释；可见文案全部 `Str.get`（help_2/loading_help_document/failed_to_load_help_document），错误兜底 `DocLoader.errorText` 亦为资源串 `failed_to_load_document_e_message`——硬编码随批次2 A-01/E-19 重做消失。
- **终检**：判定依据逐点复核（DayNight parent、values-night 覆盖、shouldUseDarkTheme 调用点 :87/:812、errorText 资源化）；清单划线 1（余 1：D-42）。

### [批次17·下63] D-42 三子项拍板执行：①状态外移 ③删 signature ②框架映射登记不动
- **①RepoPluginInfo 派生态外移**：删 `isInstalled/installedVersion/hasUpdate` 三字段；新增嵌套 `RepoViewModel.InstalledMeta`（isInstalled/installedVersion/hasUpdate）+ `_installedMeta: StateFlow<Map<String, InstalledMeta>>` 按 pluginId 索引；`checkForUpdates`（copy 回写 model）改 `buildInstalledMeta`（纯 map 构建 + updateCount 日志不变）。RepoScreen 六处消费改写：收集 `installedMeta`、卡片调用点与 `RepoPluginCard` 签名新增 `hasUpdate` 形参（体内 2 处 `plugin.hasUpdate`→形参）、详情对话框 `plugin.isInstalled/installedVersion/hasUpdate` → `installedIds.contains`（与既有 :336 同源同型）/`installedMeta[...]`。GitHubApiService 构造点本就未涉三字段 ✓。
- **③signature 死字段删除（自证签名收口）**：`PluginInfo` 三点删除——`val signature`（model 字段）、`toJson` `put("signature")`、`fromJson` `optString("signature")`；全仓 0 消费者实证。安全归属说明写入字段位注释：真校验 = `SecurityUtils` SHA-256 **文件指纹 TOFU 钉扎**（首导入记 prefs、后续比对，`PluginManager:420` 拦截导入），独立于 plugin.json。docs 无该字段 schema 描述（0 处需改）。
- **②框架映射：用户拍板登记不动**——手写 53 opt*/51 put 虽为管道工，但已全 val 化 + D-40/D-41 兼容语义（JSON 数组/逗号双格式、migrateLegacyBackend 后处理）迁移面大、无编译环境仅静态验证，收益/风险比低，登记为决策结论不再实施。
- **扫描器修正（附带）**：linescan2 的 `/* */` 正则预剥会把 `"/*"` 字面量（MIME 通配）后 247 字符真代码误删致假阳性——块注释改入状态机、正则不再触碰字面量；4 改动文件复检全 (0,0,0,0,code)。
- **终检**：`plugin.isInstalled|hasUpdate|installedVersion`/`checkForUpdates`/`val signature`/`put("signature"` 全仓 0；parity 1811 对齐；`RepoPluginCard` 单一调用点已带新参。**清单 233/233 全部关闭**。

### [批次17·下64] 首次真机构建失败修复：D-23 使用 @hide API（Os.getpgid/killpg）
- **现象**（`/storage/emulated/0/UIN_Tool/build_output.txt`）：`:terminal-emulator:compileDebugJavaWithJavac` FAILED，`TerminalSession.java:243/245 cannot find symbol getpgid(int)/killpg(int,int) location: class Os`——批次15 D-23 的「杀进程组」实现**从未经过编译验证**（清单17轮全部静态验证，此前最后一次成功构建早于批次15）。
- **根因**：`android.system.Os.getpgid/killpg` 是 **@hide API**，compileSdk 36 的公开 android.jar 不含其 stub；`Os.kill` 本身是公开 API（javac 只报上述两处）。
- **修复（保持 D-23 语义：组杀+失败回退单杀）**：新增 `readProcessGroup(pid)` 解析 `/proc/<pid>/stat`（最后一个 `)` 后取 state/ppid/pgrp，第3个字段= pgrp），组杀改用公开 API `Os.kill(-pgid, SIGKILL)`（POSIX libc kill(2) 负号 = 对整组发信号）；**自保护守卫** `pgid>0 && ownPgid>0 && pgid!=ownPgid`——shell 未自立组（= app 自己的组，Android app 进程即组首）时跳过组杀避免连自己一起杀，退化为改前单杀。读取失败/无权限 → -1 → 回退单杀，各层级均为 fail-open。
- **横扫**：全 6 模块 `Os.*` 调用清单核对——其余（stat/lstat/fstat/symlink/readlink/chmod/kill 等）均为公开 API；`getpwuid` 在 termux-shared 走的是**反射**（`ReflectionUtils.getDeclaredMethod`），无编译期依赖 ✓。
- **扫描盲区更正**：`[批次17·下59]` 记的「`TERM/TerminalSession.java` 整文件不存在」**结论有误**——当时只 grep 了 `app/src`，实际文件在 `terminal-emulator/src/main/java/.../TerminalSession.java`（清单引用的 TERM/ 路径是模块间移动后的旧引用）。复核该文件当前中文仅为 D-23 注释、字符串字面量 0 中文 → **D-35 实质结论（无中文残留）仍成立**，但证据链由此更正。此后验证范围含全部 6 模块。
- **风险预告**：本次构建在 terminal-emulator 即失败，`app` 的 Kotlin（下57~63 大量改动：568 删键/115 改名/RepoViewModel 状态外移）**尚未编译到**——修复后重构可能继续暴露后续错误，逐轮消费 build_output。

### [批次17·下65] 构建修复②：A-34 误删「死主题」ThemeOverlay.UIN.Tool.Toolbar
- **现象**：`:app:processDebugResources` FAILED——`termux-shared/.../layout/partial_primary_toolbar.xml:16` 引用 `@style/ThemeOverlay.UIN.Tool.Toolbar` 找不到。
- **根因**：**A-34** 批次删 7 个「死主题」时判定其无引用并删除，但引用在 **termux-shared 模块的 layout**（被 activity_report/activity_text_io include），当时扫描只覆盖 `app/res` ——与 TerminalSession 扫描盲区同款病。
- **修复**：从设备旧 release 构建产物（`app/build/intermediates/.../values.xml`）挖回原定义（`parent=ThemeOverlay.AppCompat.DayNight.ActionBar` + `colorControlNormal=@color/white`），回填 `termux-shared/values/styles.xml`（紧邻其兄弟样式 BaseToolbar.Title/Subtitle），注释标明「勿再当死主题删」。其余 6 个死主题全模块 0 引用 ✓。
- **进展**：terminal-emulator 编译通过（下64 修复生效）、资源链接通过；构建推进到 `:app:compileDebugKotlin`。

### [批次17·下66] 构建修复③：AppConstants.kt 注释内裸 `/*` 致 Kotlin 嵌套注释未闭合
- **现象**：`:app:compileDebugKotlin` FAILED——`AppConstants.kt:47 Missing '}'`、`:157 Unclosed comment`。
- **根因**：**下60 拍板2.0 回迁注释**里写了 `回迁共享工作目录 /sdcard/UIN_Tool/*——`，其中 `l/*` 形成 `/*` 序列——**Kotlin 块注释支持嵌套**（与 Java 不同），doc 注释深度 +1，行56 的 `*/` 只降到深度1 → 注释吞掉文件其余全部内容；`:47 Missing '}'` 是级联误报。当时未对 AppConstants.kt 跑 linescan，漏检。
- **修复**：注释改为 `/sdcard/UIN_Tool 目录——` + 就地加「不可写通配式字样」防回归注释。
- **全量加固**：状态机重扫 6 模块全部 `.kt`——括号/注释态全平；并专项检测「注释态内出现 `/*`」的嵌套隐患——0 残留（其余命中均在字符串字面量或已闭合行内注释，安全）。

### [批次17·下67] 首轮 Kotlin 全量编译修复（58 错 → 9 簇根因）
- **背景**：资源链接通过后，`:app:compileDebugKotlin` 首次编译（下57~63 改动从未编译过 + UI美化工作流的存量新文件），爆 58 错、9 簇根因：
- **①UpdateChecker.kt:211** `fetchReleases` 块体里 `try` 是语句未返回 → 加 `return try`（B-56 `.use` 重构时漏 return）。
- **②UpdateDownloadService.kt:102** `android.R.drawable.ic_menu_close_cancel` 不存在 → 正确名 `ic_menu_close_clear_cancel`。
- **③BackupRepositoryImpl.kt:83** 下59 D-35 英文改写时写了**空字符字面量 `''`**（Kotlin 非法）→ `""`。
- **④RootfsManager.kt:173** `android.system.StatFs` 不存在（是 `android.os.StatFs`；:175 compareTo 为级联）。
- **⑤RootfsManager.kt:525** `TarArchiveEntry.setLinkFlag` **在公开 API 从未存在**（javap 核 1.21/1.26.1/1.26.2/1.28.0 全无），D-17 导出代码凭空捏造 API → 改用真实构造器 **`TarArchiveEntry(entryName, item.flag)`**（字节码核实直接 `putfield linkFlag`）；连带 :527 setLinkName 原本合法。
- **⑥UnifiedPageChrome.kt（30 错簇）** 三宗罪：缺 `androidx.compose.runtime.Composable` 导入 + `utils.AppColors/AppDimens` **错包**（实为 `ui.theme.AppColors/AppDimens`，MainActivity 同款正确导入为证）→ 修 3 行导入；DevTools/LogViewer/PluginManage 5 处「@Composable invocation」全为该文件级联。
- **⑦UIConfigPreviewScreen.kt:638+** 缺 `import com.UIN.Tool.utils.Str`（MatchGroup/candidates 错均为 K2 级联噪音）。
- **全仓预检**：脚本扫全部 `.kt`——`Str.get`/`@Composable`/`AppColors`/`AppDimens`/`AppToast` 的「使用 vs 导入」一致性 + 错包残留 = **0 ✓**；6 改动文件 linescan 全平。

### [批次17·下68] 构建修复④：UIConfigPreviewScreen 缺 `import com.UIN.Tool.R`
- 上轮 6 错残余根因：该文件无 `R` 导入，`R.string.*` 被通配导入（material3/foundation 等）的 8 个 `class R : Any` 搅浑（K2 报 candidates×8 + `Unresolved 'string'`）；文件本身恰好只有这 3 处 R 引用。补 `import com.UIN.Tool.R`。全仓预检：非 `com.UIN.Tool` 包内用 `R.*` 而缺导入的 = 仅此 1 文件 ✓。

### [批次17·下69] 构建修复⑤：PreferenceManager JVM 签名撞车（getPrefs）
- **现象**：2 错——`PreferenceManager.kt:13/307 Platform declaration clash: getPrefs()Landroid/content/SharedPreferences;`。
- **根因**：D-27 中批次把 SP 改为 `private val prefs by lazy`（JVM 生成 `getPrefs()`），却留着旧的 `fun getPrefs()`（正是 D-27 清单④「getPrefs() 泄漏抽象」点名要清的函数）→ 同类同签名。全仓 0 调用者（含 Java/JS/JSON/XML）→ 删除死函数。
- **预检**：脚本扫「val x + fun getX」跨类同名 21 处，均为文件级误报（编译器同轮未报 = 类作用域无真实撞车）；PreferenceManager linescan 平。

### [批次17·下70] 桌面图标恢复：Termux `>_` → UIN 原版字标
- **用户反馈**：桌面图标变成了 Termux 的 `>_` 图标，要求换回原来的。
- **溯源**：`mipmap-anydpi-v26/ic_launcher{,_round}.xml` 接线 `@android:color/black + @drawable/ic_foreground`（Termux `>_` 字形，termux-app 原始素材），5 组 mipmap PNG 同为该图。查设备 8月11 release 构建产物——**接线与 PNG 当时即如此**（fork 复制初始态，非批次改坏；A-24 把清单从不存在的 `@drawable/app_icon*` 改到 `@mipmap/ic_launcher` 后此接线才真正上屏）。
- **原版素材一直就在仓内**：`drawable/ic_launcher_foreground.xml`（512×512，白底 + 531 条扫描线描出的 **UIN 花体字标**，与启动页 `ic_splash_foreground.xml` 同一笔画、仅多一层 `#FFFFFF` fill 底），此前 0 引用；`drawable/ic_launcher_background.xml`（白色 shape）**被下51 当零引用误删**（删时仓内已有 widget 等引用同族素材，教训同 A-34：零引用扫描须全模块全类型）。
- **修复**：① 从 8月11 release 产物按原字节恢复 `ic_launcher_background.xml`；② 两个 anydpi XML 改接 `background=@drawable/ic_launcher_background`、`foreground=@drawable/ic_launcher_foreground`、`monochrome=@drawable/ic_splash_foreground`——monochrome 必须用**无白底版**（否则 Android 13 主题图标取 alpha 会得到整块实心方片）；视觉 = 白底 + 黑色 UIN 字标，与 PNG 一致；③ 5 组 `ic_launcher{,_round}.png`（48/72/96/144/192）用 Java2D 渲染器（`/tmp/opencode/VectorPng.java`：pathData 仅 M/h/v/z、非零缠绕填充 + 1px 描边 + premultiplied 面积降采样）从 `ic_launcher_foreground.xml` 重绘替换——minSdk 23 的 API23-25 回退图不再是 `>_`。
- **保留**：`ic_foreground.xml`（Termux 字形）现全仓 0 引用但**有意不删**（A-34 教训，且无害）。
- **校验**：3 个改动 XML well-formed、三素材存在且引用闭环 ✓；512 预览渲染出 UIN 字标、144 小图清晰 ✓；Manifest `@mipmap/ic_launcher` 不动。

### [批次17·下71] 用户7连改：图标git回退 + 终端复制文案 + 文档中心置顶 + 源管理渐变 + 文档查看白底 + proot安装文案 + 深色渐变防污染
- **①图标（用git恢复上一版）**：`git restore` 全套 mipmap（anydpi 2 + 5组 PNG）回 HEAD——撤销下70 的重接线/重绘；清单 `icon/roundIcon` 手工回 HEAD 值 `@drawable/ic_launcher_foreground`（512 矢量白底 UIN 字标直充桌面图标，与 HEAD 行为完全一致；@mipmap 全仓不再被引用，mipmap 文件留在 HEAD 态）。
- **②终端复制弹「日志已复制」**：链路核查为单链、无全局剪贴板监听、无双 handler——纯文案错位：`terminal_log_copied`（2dc5e69 建 SimpleTerminalActivity 时写错的 key）唯一调用点就是终端选中复制回调。`SimpleTerminalActivity.kt:165` 改用全仓统一的 `copied_to_clipboard`（已复制到剪贴板）；`terminal_log_copied` 两 locale key 成 0 引用**有意保留**（A-34 教训，不删资源）。
- **③文档中心**：核查**未被删除**——DocBrowserActivity/5 个文档（help/dev/changelog/about/contributors 覆盖 assets 全部）/Manifest 注册/快捷方式全在，只是埋在管理→软件组第 7 项（G-13 又把帮助/日志提到它前面）。`ManageScreen` docs 项移到**软件组第一位**（注释拍板恢复可见性）。死副本 DevDocScreen/DevDocActivity 零启动点，保留记录不动。
- **④源管理渐变**：`SourceManageActivity` 的 `Surface(color=colorScheme.background)` 是全仓唯一盖住主题根 Box 渐变的 Activity 包装层 → 删 Surface 直接 `UINToolTheme { SourceManageScreen() }`（与 PluginManage/Help/DocViewer 同款），连带清 3 个失效 import；页面根 Column 本身无背景，渐变与深浅自动生效。
- **⑤文档查看白底**：`DocViewerScreen` WebView 底色原读 `UIConfig.getBackgroundColor()`——只读浅色 `theme` 区且白色兜底，深色下形成白矩形 → 改 `Color.TRANSPARENT`（MarkdownRenderer 的 body/.container 本就 transparent，透出 Scaffold 的 `pageBackground()` 渐变）。同类遗留：HelpScreen WebView 未设底色（默认白），用户未点名，记录不改。
- **⑥proot 安装文案**：`container_installing_please_wait`/`container_install_completed` 中英 4 值改「正在安装proot容器，请稍候…/proot容器安装完成/Installing proot container…/Proot container installation complete」；调用点 `EnvironmentBootstrap.kt:66/71` 传的 containerName 因串无 `%1$s` 被 String.format 忽略（无害）保持原样。
- **⑦深色下切风格后渐变变浅（复现：默认→深色→新拟态→默认）**：渲染侧（`gradientBackgroundBrush`）核验本就按 dark 分键，坏在数据面三层缺陷，修复：
  1. `loadConfigFromUIConfig` 渐变5字段**全模式化**（原 `gradientMode/Colors/From/To` 恒读浅键——深色下任何保存都把浅值写进 `*_dark`）；
  2. 新增 `ConfigState.refreshGradientForMode()`（按当前模式归位单槽位），深色 chip 切换后即调用（原 chip 只 copy themeMode 不重载）；
  3. `StyleManager.switchStyle` 结果统一 `copy(themeMode=当前持久化值).refreshGradientForMode()`——风格预设只带浅色一组渐变且 themeMode="system"，既防浅值污染 `color_dark/colors_dark`，也防切风格把用户显式深浅改回 system；
  4. `getGradientColorsStringDark()` 旧配置缺 `colors_dark` 时回落**深色默认**三色（原错误回落浅色）。
- **终检**：8 改动 .kt linescan 全平；strings parity 1811 ✓（0 占位差）；Manifest/双 strings XML well-formed；横扫 6 项（terminal_log_copied 0 引用、无第二个不透明 Surface Activity、manifest icon 已回 @drawable、refreshGradientForMode 3 调用点闭环、Theme.kt:221 为浅色分支非误读、switchStyle 仅 3 调用点均已覆盖）。

### [批次17·下72] 用户4连改：移除使用帮助入口 + 默认风格深色仿新拟态 + 终端选择栏中文化 + 前端类型卡片去纯白
- **①移除使用帮助入口**：用户拍板「使用帮助替换成文档中心，不要专门留个使用帮助的入口」——下71 已把文档中心置顶，本轮再删 `ManageScreen.softwareItems` 的 `help` 项（Help.md 本就是文档中心首条目），并清掉随之失效的 `HelpActivity` import；`AppLog` 仍被其余条目使用保留。HelpActivity/Manifest 注册/帮助文档资源全部保留（无入口≠删功能，A-34 教训）。软件组现为：文档中心→运行日志→权限→外观→开发者→备份→镜像→更新。
- **②默认风格深色配色模仿新拟态**：`StylePresets.DEFAULT` 深色列有 5 字段与 NEUMORPHISM 不同 → 对齐：`primaryColorDark #FF8B949E→#FF7A8490`、`accentColorDark #FF8B949E→#FF6A9AAE`、`backgroundColorDark #FF2A2A2A→#FF2A2E34`、`surfaceColorDark #FF363636→#FF2A2E34`、`surfaceVariantColorDark #FF3F3F3F→#FF252930`（其余深色字段本就相同；浅色列/玻璃/圆角/开关一律不动——只借深色调色板，不变成新拟态样式）。脚本比对：DEFAULT vs NEUMORPHISM 深色字段差异现为 0。切换默认风格时 switchStyle（下71 已双写 theme/theme_dark）即落这套深色。
- **③终端长按选择栏英文 Copy/Paste**：根因为 terminal-view 模块只有 `values/strings.xml` 英文（无 values-zh*），浮动栏三项 `copy_text/paste_text/text_selection_more` 由 `TextSelectionCursorController.menu.add(R.string.*)` 引用，app 无同名 key 可覆盖 → 在 app `values/` + `values-zh-rCN/` 各补 3 个同名 key（Copy/Paste/More…、复制/粘贴/更多…）。资源合并规则：app 模块同名覆盖 library 模块，Java 零改动。附带确认：该栏仅此 3 项（无 Select all/Share，「更多…」点开为系统注入项，非本仓菜单）。
- **④前端类型选择卡片纯白**：「创建插件」对话框（`choose_frontend_type`）4 个 `UnifiedButton` 写死 `containerColor=surfaceVariant`，在渐变/玻璃页面上呈固定灰白、与管理页卡片脱节 → 改为 `if (AppColors.glassEnabled()) AppColors.glassBackground() else MaterialTheme.colorScheme.surface`（与管理页卡片同款玻璃感知取色，深浅模式自动跟随），4 处一次替换；顶部「白色背景」注释同步更正。
- **终检**：3 改动 .kt linescan 全平；strings parity 1814（3 新 key 双 locale 齐）；双 strings XML well-formed；DEFAULT/NEUMORPHISM 深色差异脚本=0；softwareItems 顺序含 docs 首位且无 help；DevScreen 4 处新取色 + AppColors(:27) 导入在。

### [批次17·下73] 深色反色两根因：根级 LocalContentColor 兜底 + 默认风格深色卡片分级提亮
- **用户反馈**：部分页面深色没模仿成功——插件页面插件名一个白一个黑；几个卡片还是没区别。
- **根因①（白/黑混杂）**：各页 `Scaffold(containerColor = AppColors.pageBackground())`，渐变开启时 pageBackground=Transparent → m3 `contentColorFor(Transparent)` 主题无匹配 → 回落 `LocalContentColor.current`，而其**根默认是 Color.Black**（UINToolTheme 从未提供）→ 深色下所有**未显式着色**的 Text（`PluginGridItem:491`、`PluginManageItem:1454` 的 `Text(plugin.name)` 无 color 参数、卡片内 caption 等）全成黑字；显式 `onSurface/onBackground` 的文本照常浅色 → 同屏一白一黑。渐变关时 Scaffold container=background 能匹配出 onBackground，故"部分页面"才复现。
- **修复①**：`Theme.kt` UINToolTheme 的 content 根部加 `CompositionLocalProvider(LocalContentColor provides colorScheme.onBackground)`——深色=浅字、浅色=深字的全树兜底；就近的 Surface/Card/Button/Scaffold 已提供的 contentColor 仍然优先（CompositionLocal 就近覆盖），全仓 21 处用 pageBackground() 作容器的组件一并修复，无逐页改色。
- **根因②（卡片无区别）**：下72 把默认风格深色 surface 直接抄成与 background 同值 `#2A2E34`、glass 抄成 `#2A2A2A`——新拟态卡片靠 neuRaised 阴影与背景区分，默认风格无阴影，玻璃/卡片色又与背景几乎同色 → 卡片、前端类型对话框按钮全与背景融为一体。
- **修复②**：`StylePresets.DEFAULT` 深色三字段按同色系**背景<输入<卡片**分级提亮（其余深色字段仍与新拟态一致，脚本比对仅这 3 处有意差异）：`surfaceColorDark #2A2E34→#343B44`、`surfaceVariantColorDark #252930→#2E343B`、`glassBackgroundColorDark #B32A2A2A→#B3343B44`；背景保持新拟态 `#2A2E34`。
- **注意**：预设改动需**重新选择一次「默认」风格**才会把新 theme_dark 写入配置（switchStyle 落盘）；根级文字色修复重启即生效。
- **终检**：Theme.kt/StylePresets linescan 平；DEFAULT vs NEUMORPHISM 深色差异脚本=3（surface/surface_variant/glass 有意分级）；`pageBackground()` 组件 21 个均落入根级兜底范围；无 strings/manifest 改动。

### [批次17·下74] 前端类型弹窗选项改为真正的卡片（UnifiedCard），与其他卡片同一套颜色代码
- **用户澄清**：「卡片样式代码（颜色相关）照抄」指的**是创建插件弹窗里那 4 个前端类型选项**，要和其他卡片风格一致。
- **问题**：这 4 个选项此前是 `UnifiedButton`，下72 只把 `containerColor` 从写死的 `surfaceVariant` 改成 `glass/surface`——颜色值虽然对上了，但组件仍是**按钮**：`buttonCornerRadius`（12dp）、`buttonHeight` 最小高 44dp、14sp Medium 居中文本、`ButtonDefaults.buttonColors` 配色链，与卡片（`cardCornerRadius` 16dp、`cardPadding` 16dp、Elevated 分支 `glass ? glassBackground : surface`、`contentColorFor` 解析）不是同一套代码，所以看起来仍与别处卡片不同。
- **修复（照抄卡片代码）**：4 个选项由 `UnifiedButton` 改为 `UnifiedCard(onClick = …) { Text(bodyMedium, onSurface) }`——直接复用 `UnifiedCard` Elevated 分支，容器色/圆角/内边距/点击态/新拟态分支全部走与其他卡片完全相同的代码，不再手写任何 `containerColor`。本页原有 3 张卡（终端/插件开发工具/快速开始）即此写法，现在弹窗内选项与之一致。连带清掉 DevScreen 中因此失效的 `AppColors` import。
- **未动**：弹窗底部取消按钮仍是 `UnifiedDialogTextButton`；本页其余 5 个 `UnifiedButton`（打开终端/创建插件等）保持按钮形态不变。
- **终检**：DevScreen linescan 平；`containerColor` 在本文件 0 处；`AppColors` 在本文件 0 处（全仓他处仍正常使用）；`UnifiedCard(` 本文件 7 处（原 3 + 新 4）。

### [批次17·下75] 前端类型卡片文字居中 + 管理页「软件」组标题改「软件管理」
- **①卡片文字居中**：下74 改成 UnifiedCard 后文字默认左对齐 → 4 个选项的 `Text` 补 `modifier = Modifier.fillMaxWidth()` + `textAlign = TextAlign.Center`（并补 `androidx.compose.ui.text.style.TextAlign` 导入），居中方式与其他按钮一致；卡片容器色/圆角代码不变。
- **②组标题**：管理页软件组标题 `R.string.software` 中文「软件」→「**软件管理**」，英文 `Software` → `Software Management`。该 key 全仓仅 `ManageScreen:378` 这一处引用（组标题），无其他副作用。
- **终检**：DevScreen linescan 平；`TextAlign` 5 处（1 导入+4 使用）；strings parity 1814 ✓；双 strings XML well-formed；`R.string.software` 引用点确认唯一。

### [批次17·下76] 文档全量更新：按 UC.md 回写 6.1.0 + 日期统一 10-07 + 修 6 处陈旧页脚
- **背景**：用户要求「更新一下文档，根据 UC.md；检查文档有没有问题；日期也要更新；看看有没有没更新的」。核对范围：工作区 4 份（`UC/TODO233/Progress/Notes.md`）+ 工程根 3 份 + 应用内 `docs/{en,zh-cn}` 10 份。
- **日期更新**：9 处 `2026-10-05 → 2026-10-07`（zh/en 各 Help/README/About/CHANGELOG + 根 `README.md`）；`UC.md` 内 2 处为历史拍板记录，按 append-only 保留不动。
- **CHANGELOG 按 UC.md 回写**：中英 6.1.0 条目各新增 7 节 — 安全与插件入口 / 启动、更新与长任务 / 架构与状态收口 / 界面、可访问性与深色主题 / 国际化与文案 / 死代码与资源清理 / 构建与 CI；简介行改为「233 项全部关闭」口径。中英分节逐行对齐（各 14 节 / 58 条 bullet）。
- **发现并修复 6 处陈旧页脚**（本轮主要问题）：6 份文档**尾部**版本表仍停在 `5.7.0 / Build 23 / 2026-08-31`，`CHANGELOG` 更写成 `文档版本 5.5.0 / 2026-08-22` —— 与**自身页头** `6.1.0 / Build 25` 自相矛盾（批次10 只改了页头，漏了页脚）→ 统一为 `6.1.0 / v6.1.0 (Build 25) / 2026-10-07`；`CONTRIBUTORS.md` 页脚 `2026年8月 → 2026年10月`（zh/en）。
- **中英内容差**：`Help.md` §5.2 终端特性 zh 比 en 多一行「自定义快捷键」→ 补 `| Custom Shortcuts | Configurable hardware/software keyboard shortcuts |`，恢复逐表行数相等。
- **`Progress.md` 重写**：原文件停在 `8月23日`「5.6.0 UI 美化」（16 次 WebUI 预览、用户十七次评判未完成），已完全过期 → 改写为 `6.1.0 (build25) 全量缺陷修复 — 进度记录`：233/233 关闭、98 个批次表、10 条修复主线、已知未决/待决策、工具与验证、版本信息表。
- **核对结论（判定无需改动）**：版本徽章/版本表/页头页脚现全为 6.1.0 Build 25；「使用帮助」独立入口移除后无文档误述（`Help.md:254/439` 两处均指文档中心**内部**文档列表，正确）；`Intent-filter` 声明与 Manifest 一致（`PluginOpenDispatchActivity:181` 已注册，原 C-11 已不成立）；`test_plugins` 双构建口径文档已随下61 更新；`About` 版本历史中 5.7.0 / 6.0.0 等旧行属历史记录，保留。
- **验证**：中英 5 对文档结构比对全 OK（标题数、表块数、每表行数全等）；今日改动 19 个 `.kt` linescan 全 `(0, 0, 0, 0)`；`check_strings_parity` = `OK 1814 keys`；本轮**仅改 `.md`**，无 `.kt` / `.xml` 变更。
- **待办（构建）**：真机构建仍 `BUILD FAILED`，但 `build_output.txt`（02:51）**早于** `PreferenceManager.kt` 下69 修复落盘（02:54），`getPrefs` 现全仓 0 引用 → 该日志属过期输出，需重跑验证。

### [批次17·下77] Notes.md 加归档横幅（收尾）
- **对象**：`/home/Projects/Android/Notes.md` —— 唯一仍未更新的工作区文档，原样停在 5.6.0 UI 美化阶段的「新拟态升级研究笔记」，且无任何日期/状态标识，易被误读为当前状态。
- **处置**：非状态文档（研究笔记），**不重写**，只在标题下加归档横幅：注明记录日期 `2026-08-23`、声明其「构建系统升级方案」表 6 项目标已全部落地（实测对照 `build.gradle` / `app/build.gradle` / `gradle-wrapper.properties`：AGP 8.9.1、compileSdk 36、Compose BOM 2025.08.00、Gradle 9.0.0 不变、targetSdk 28 不变、Kotlin 2.1.0 不变），并指向 `Progress.md` / `UC.md` / `TODO233.md` 三份现行文档。
- **至此工作区 4 份文档状态**：`UC.md` 现行（含本条下77）、`Progress.md` 已重写为 6.1.0、`TODO233.md` 标题已为「233 条全部关闭」无需改、`Notes.md` 已标注归档。

### [批次17·下78] 构建修复⑥：lintVitalAnalyzeRelease 因 lint 中间产物被禁用而崩溃
- **现象**：`BUILD FAILED in 35m 32s` —— **Kotlin/Java 编译已全部通过**（`grep '^e: \|error:'` = 0 行；`:app:compileReleaseKotlin`、`:app:compileReleaseJavaWithJavac` 均成功，下69 `getPrefs` 修复生效）。唯一失败任务：`:app:lintVitalAnalyzeRelease`。
- **报错**：`Could not resolve ... :app:releaseCompileClasspath` → `ExtractAarTransform: terminal-view/build/intermediates/local_aar_for_lint/release/out.aar` → `FileNotFoundException: termux-am-library/build/intermediates/local_aar_for_lint/release/out.aar (No such file or directory)`；同一缺失文件连带 terminal-view / termux-shared / terminal-emulator 三条 transform 全挂（日志 531/756/1011）。
- **根因（输入被禁、消费者照跑）**：4 个库模块各自用 `tasks.whenTaskAdded { … task.enabled = false }` 禁掉 lint 任务 —— `termux-am-library:65`、`terminal-view:64`、`terminal-emulator:87` 按 `name.contains("lint"/"Lint")` **全禁**，`termux-shared:99` 禁 6 个具体名（含 `bundleReleaseLocalLintAar`）；而 `app` 模块**没有**这段禁用。于是产出 `local_aar_for_lint/release/out.aar` 的 `bundleReleaseLocalLintAar` 在 4 个库全部 SKIPPED（日志 222/224/227/236），`:app:lintVitalAnalyzeRelease` 却照常执行并去解析这些文件 → 必然 FileNotFoundException。与日志完全吻合：3 个全禁模块的 `prepareLintJarForPublish`/`generateReleaseLintModel` 也 SKIPPED，而 `termux-shared` 因只禁具体名、其 `generateReleaseLintModel`（日志 229）/`generateReleaseLintVitalModel`（237）反而执行。
- **无效配置**：`gradle.properties:6-8` 的 `android.lint.checkReleaseBuilds=false` / `android.lint.enableVital=false` / `android.lint.enabled=false` **不是 AGP 认的 gradle 属性**（lint 只在 `lint { }` DSL 里读），属空转；`:3 android.disableAutomaticLintVital=true` 也未能拦住 `assembleRelease` 触发的 lintVital。四行均在 git HEAD 中，非本轮引入。
- **修复**：`app/build.gradle` 的 `lint { }` 块新增 `checkReleaseBuilds = false`（AGP 真正读取的位置）→ `:app:lintVitalAnalyzeRelease` 不再进任务图。**只关 release 的 lint vital，不关 `lint` 任务**，故 CI 的 `./gradlew lint`（`android-build.yml:74`）不受影响、质量门保持。
- **决策依据**：与既定决策 **A-18（lint 门禁恢复：保持关闭，列入待决策）** 一致；CI lint 步骤本就 `continue-on-error: true`，不阻断。
- **同错误类全仓扫描**：`grep -rn --include='*.gradle' 'whenTaskAdded|task.enabled = false'` 命中 4 处（即上列 4 库），均已覆盖；全仓 `lint { }` 仅 `app` 一处，已修；`checkReleaseBuilds/abortOnError` 无其他配置点。
- **验证**：`app/build.gradle` 大/小括号配平 0/0、`checkReleaseBuilds = false` 恰 1 次、lint 块结构完好。本轮仅改 1 行 Groovy，无 `.kt` 变更（故不跑 linescan，strings 键数不受影响）。
