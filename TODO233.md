# 233 条全部关闭（原 233，批次1~17）

~~- **A-04** [安全|高] `app/build.gradle:51-52`~~  → 已处理（已修）
  问题: 调试 keystore `storePassword/keyPassword 'xrj45yWGLbsO7W0v'` 明文入库；`app/testkey_untrusted.jks` 被 git 跟踪（`.gitignore:3` 只忽略 `keystore.jks`，文件名不匹配）
  建议: 口令移到 `~/.gradle/gradle.properties` 或环境变量；调整 ignore 规则
~~- **A-16** [构建|高] 根 `build.gradle:9` vs `gradle/wrapper/gradle-wrapper.properties:3`~~  → 已处理（决策注释已加，版本待拍板）
  问题: AGP 8.9.1 配 Gradle 9.0.0（AGP 8.9 官方支持到 8.11.x）；`settings.gradle` 无 `pluginManagement/dependencyResolutionManagement`，仍是老式 buildscript+allprojects
  建议: 升级 AGP 或回退 wrapper；插件版本与仓库声明迁入 settings.gradle
~~- **A-25** [重复|高] `RES/drawable/*`~~  → 已处理（见 UC 批次17·下51（shrinkResources 按拍板不开））
  问题: 136 个 drawable 中 **116 个直接零引用**（严格可达性口径 127），含 `bg_button_*`、`glass_*`、`ic_card_*`、`ic_nav_*`、`indicator_dot_*`、`terminal_key_*` 等整族；`shrinkResources false` 使其全部进包
  建议: 删除未用 drawable；打开 `shrinkResources true` 并补 `res/raw/keep.xml`
~~- **A-49** [坏味道|高] `app/build.gradle:17-18` + 根 `README.md:3,55,59` + `ASSETS/docs/**` 14 处~~  → 已处理（版本已统一，单一版本源留决策）
  问题: 版本号 `6.0.0`/`Build 24` 散落 **至少 14 处**；根 README 更新日期 2026-09-22 与 CHANGELOG 2026-09-26 已不一致
  建议: 建立单一版本源（见 7.4）
~~- **B-31** [bug|高] `SRC/SplashActivity.kt:322-325` + `core/update/UpdateDownloader.kt:110,183`~~  → 已处理（已修）
  问题: `onFailed` 在下载线程直接 `Toast` → Android 11+ 非 Looper 线程 Toast 抛 RuntimeException；异常被 `UinApplication.kt:140-143` 吞掉 → **下载失败无任何提示**
  建议: 用 `AppToast`（已切主线程）
~~- **B-64** [性能/ANR|高] `SRC/core/compiler/JavaToDexCompiler.kt:66-106,110-215` + `ui/screen/dev/BasePluginWizardScreen.kt:52,216`~~  → 已处理（已修）
  问题: `suspend` 内做同步 zip/递归 IO，调用方是主线程 `rememberCoroutineScope` → 打大项目包卡 UI
  建议: `withContext(Dispatchers.IO)`
~~- **C-11** [bug|高] `PluginOpenDispatchActivity.kt:53-583`（全类，584 行）+ `MAN` 无对应 `<activity>`~~  → 已处理（已修）
  问题: **未在 Manifest 注册、全仓 0 调用**；但 `ASSETS/docs/zh-cn/README.md:2048,2082` 与 en 版 :2050,2084 明确宣称"已声明 Intent-filter"。连带：`EXTRA_OPEN_DATA` 唯一写入点 :284 → `PluginHostActivity.kt:150/:490` 恒读 null → `UINPlugin.g
  建议: 注册 + `ACTION_SEND/SEND_MULTIPLE/VIEW` filter（含 OPEN_PLUGIN 保护），或整类删除并同步改文档
~~- **D-05** [bug|高] `SRC/proot/RootfsManager.kt:422-447`（关键 :432-442）~~  → 已处理（已修）
  问题: 导入硬链接仅当目标已存在才复制，否则 `:441` 打一条 warning 后**丢弃且不返回失败**（tar 中目标常在链接之后出现）→ 导入的 rootfs 可能缺文件
  建议: 建待补链接表，解压结束后统一补，或用 `Os.link()`
~~- **D-06** [bug|高] `SRC/proot/RootfsManager.kt:331-341`（:332 `if(!file.isFile) return`）、`:356-362`~~  → 已处理（已修）
  问题: 导出只收 isFile：符号链接被展开成普通文件、**悬挂链接与空目录整体丢失**、硬链接复制成多份 → 导出→导入往返后结构损坏、体积膨胀
  建议: `Files.readAttributes(...,NOFOLLOW_LINKS)` 区分类型分别写 entry
~~- **D-09** [网络/并发|高] `SRC/data/remote/SourceIndexFetcher.kt:34,43,45-129`~~  → 已处理（已修）
  问题: ①注释称带 ETag 实际**无 If-None-Match** ②无 403/429 限流识别、无重试退避（未认证 60 次/小时）③`execute()` 阻塞且取消不中断 ④`cache` 为普通 mutableMap 并发不安全
  建议: enqueue+取消挂钩；ETag 落盘；ConcurrentHashMap；识别 X-RateLimit-Reset 退避
~~- **E-16** [安全|高] `utils/SecurityUtils.kt:114,158,176-184,230-233,287-302,304-311`~~  → 已处理（核对不成立：全仓无 AES/ECB）
  问题: ①文件加密用 `AES/ECB/PKCS5Padding`（ECB 不安全，`KEY_CHECK_CONST` 还以 ECB 密文写入 prefs）②`loadOrCreateKey` **catch 内吞异常直接返回 `DEFAULT_KEY`（硬编码字面量）** → 密钥读取失败仍"正常"解密，得到垃圾数据 ③`deriveKeyAndIv` 用 md5 加盐两次而非 PBKDF2 ④`rea
  建议: ECB→AES-GCM；删除默认密钥回退（失败应显式报错）；PBKDF2；流式；`SecureRandom`
~~- **E-19** [bug|高] `ui/screen/help/HelpScreen.kt:38`（= A-01）+ `MAN` 无 help activity~~  → 已处理（批次2 已修）
  问题: 帮助页 assets 路径错 → 必然失败；且 help 作为独立 Activity（`MAN:254-257`）与 7 主题内的 markdown 树状目录功能重复
  建议: 三选一：修路径并合入 docs；或删 Help Activity + 主题；**清单归入待决策**
~~- **F-08** [性能/bug|高] `ui/screen/dev/BasePluginWizardScreen.kt:52,216` + `core/compiler/JavaToDexCompiler.kt`（= B-64/B-65）~~  → 已处理（随 B-64 修）
  问题: 向导内同步打包卡 UI；失败仍产占位包
  建议: IO 调度 + 失败即报错
~~- **F-09** [bug|高] `ui/screen/dev/PluginWizardViewModel.kt:154-168`~~  → 已处理（已修）
  问题: 生成 Python 模板时把 `Str.get(...)` **未求值**结果写进插件源码（`print(Str.get(...))` 会 NameError），模板占位符语义未区分"宿主渲染"与"运行时渲染"
  建议: 分 `{{HOST_x}}`/`{{RUN_x}}` 两套变量；生成后做语法烟测
~~- **G-08** [bug|高] `ui/screen/help/HelpScreen.kt:38`（= A-01）~~  → 已处理（批次2 已修）
  问题: 读 `assets/docs/Help.md` 不存在 → 打开必失败
  建议: 见 E-19
~~- **G-19** [性能|高] `ui/screen/proot/ContainerManagementScreen.kt:58,127,352,377`（= D-07）~~  → 已处理（批次3 已修）
  问题: 主线程全树 walk × 每容器，列表卡顿
  建议: 缓存 + IO
~~- **A-17** [构建|中] 根 `build.gradle:26-35`~~  → 已处理（见 UC 批次11~16）
  问题: `afterEvaluate { lintOptions {} }` 在 AGP 8 已移除，与 `app/build.gradle:119-121` 的新 `lint {}` 并存自相矛盾
  建议: 删除根工程该段
~~- **A-19** [构建|中] `gradle.properties:13` vs `app/build.gradle:15`~~  → 已处理（见 UC 批次11~16）
  问题: 属性 `minSdkVersion=21` 而 app 为 `minSdk 23`，库模块读属性 → 配置源不唯一
  建议: 统一为 23 或注明意图
~~- **A-20** [构建|中] `gradle.properties:9,11` + `app/build.gradle:9,205`~~  → 已处理（见 UC 批次11~16）
  问题: compileSdk 36 靠 `suppressUnsupportedCompileSdk=36` 压警告；三处版本口径注释不一致（35/36、AGP 8.7.2/8.9.1）
  建议: 升 AGP 正式支持或回 compileSdk 35，同步修正注释
~~- **A-22** [构建|中] `.github/workflows/android-build.yml:38,92` + `.gitattributes:1-2`~~  → 已处理（见 UC 批次11~16）
  问题: checkout 未开 `lfs: true`，而 `.gitattributes` 对 `*.tar.xz` 声明了 LFS（当前未真正生效）→ 一旦启用 LFS，CI 只拿到指针文件，APK 内 rootfs 损坏且无失败提示
  建议: 加 `lfs: true` 并在 build 后校验文件大小；或移除 LFS 声明
~~- **A-27** [重复|中] `RES/drawable/ic_export.xml`、`ic_import.xml`；`ic_error/ic_help`；`ic_change_category/ic_dev_tools/ic_nav_dev`；`ic_card_log/ic_view_docs`；`bg_button_flat/bg_card_clickable`~~  → 已处理（见 UC 批次17·下57 判前提过期（10件目标已随下51 消失，导出/导入图标现走 Compose Icons））
  问题: 5 组 pathData 完全相同的图标（导出/导入同图、error 与 help 同图，语义已错）
  建议: 合并；导出/导入配正确语义图标
~~- **A-28** [坏味道|中] `RES/drawable/ic_back.xml` vs `ic_arrow_back.xml`；`ic_next/prev` vs `ic_chevron_*`；`*_black` 后缀族~~  → 已处理（见 UC 批次17·下57（三套命名随下51 消失；残余 2 件 *_black 零引用死件删除））
  问题: 三套图标命名体系并存
  建议: 制定 `ic_<语义>_<方向>[_variant]` 规范并统一替换
~~- **A-29** [重复|中] `RES/values/strings.xml`~~  → 已处理（见 UC 批次17·下57（删 568 零引用键×双侧，1810 键 parity 通过））
  问题: 2364 键中 **501 键(21.2%) 零引用**（仅 kt 口径 535/22.6%；含死代码贡献则 615/26.0%）；另有 146 组重复英文文案
  建议: 以 501 为执行基线（先删死代码再复算）；两侧语言同步删
~~- **A-30** [坏味道|中] `RES/values/strings.xml:13-15,25-32` 等~~  → 已处理（见 UC 批次17·下57（formatted="false" 双侧全清 1810×2；115 个 36 字符词中截断键改词界键名，138 处引用改写，4 冲突键保留原名））
  问题: 653 键(27.6%) 为自动生成 slug（如 `a_1x1_shortcut_that_opens_a_plugin_w`），多被截断；1676/2364 条 `formatted="false"` 关闭占位符校验
  建议: 键名规范化；`formatted=false` 只留真正含字面 `%` 的
~~- **A-32** [重复|中] `RES/values/dimens.xml`~~  → 已处理（见 UC 批次17·下51）
  问题: 50 个 dimen 中 45 个零引用（Compose 用 `AppDimens`）
  建议: 删除或改为唯一来源
~~- **A-33** [重复|中] `RES/layout`(11 中 7 未用)、`menu`(2/2)、`color`(5/5)、`xml/widget_info.xml`、`anim` 部分~~  → 已处理（见 UC 批次17·下51）
  问题: 未用：`activity_settings`、`dialog_rename_session`、`item_terminal_sessions_list`、`preference_markdown_text`、`view_terminal_toolbar_text_input`、`widget_layout`、`widget_list_item`、`bottom_nav_menu`、`repo_me
  建议: 统一删除
~~- **A-34** [重复|中] `RES/values/themes.xml:109-151`、`values-night/themes.xml:32-40,54-74`、`values/styles.xml:12,17-19`~~  → 已处理（见 UC 批次17·下51）
  问题: 死主题：`Theme.TermuxApp.DayNight.*`、`Theme.TermuxActivity.*`、`TermuxAlertDialogStyle`、`Theme.UIN.Tool.NoActionBar/.Markdown/.Translucent/.Dialog` 等 13-15 条无引用（存活仅 `Theme.UIN.Tool` + `Theme.UIN.Tool.Splas
  建议: 删除
~~- **A-36** [构建|中] `RES/values/attrs.xml:8-10`~~  → 已处理（见 UC 批次11~16）
  问题: 自声明 `cardCornerRadius` 与 Material 同名属性冲突（资源合并期风险），且无对应自定义 View
  建议: 删除
~~- **A-37** [硬编码|中] `RES/layout/widget_layout_grid.xml:58,92,126,160,194,228,262,296,330`~~  → 已处理（见 UC 批次17·下4）
  问题: 9 处 `contentDescription="插件图标"` 写死中文
  建议: 抽 `@string/widget_plugin_icon_desc` 并补 en/zh
~~- **A-39** [硬编码|中] `SRC/ui/screen/manage/UIConfigPreviewScreen.kt:211,284,410,718,779,781,790,858,859,897,1277`~~  → 已处理（见 UC 批次17·下7）
  问题: 预览屏 121 处中文字面量；**`:284 "UIN Tool 5.6.0"`、`:859 "UIN Tool v5.7.0 已准备安装"` 为过期版本号
  建议: 改 `Str.get(R.string.*)`；版本用 `BuildConfig.VERSION_NAME`
~~- **A-40** [硬编码|中] `SRC/ui/screen/onboarding/OnboardingActivity.kt:17`、`OnboardingScreen.kt:57,69,155`~~  → 已处理（见 UC 批次17·下4）
  问题: 兜底版本 `"6.0.0"` 与 `"5.2.0"` 散落 3+ 处
  建议: 统一 `BuildConfig.VERSION_NAME`
~~- **A-41** [硬编码|中] `SRC/**`（108 文件 278 处）~~  → 已处理（见 UC 批次17·下13/23）
  问题: Compose UI 之外大量中文日志/Toast 未走 `Str.get()`（RootfsManager 21 处、UinApplication 13 处、ProotInstaller 12 处）
  建议: 分批抽取，优先用户可见文案
~~- **A-42** [bug|中] `MAN:425-436`~~  → 已处理（见 UC 批次11~16）
  问题: 两个自定义 dangerous 权限仅声明 `<permission>` 缺 `<uses-permission>`；`SplashActivity.kt:246-260` 运行时申请、`PluginHostActivity:176` 用 `OPEN_PLUGIN` 保护；`:431` 注释称 RUN_COMMAND 保护服务但实际无组件使用该保护
  建议: 补 `<uses-permission>`；RUN_COMMAND 要么挂到组件要么删
~~- **A-43** [坏味道|中] `MAN:79-81,84,68,74-78`~~  → 已处理（见 UC 批次11~16）
  问题: 6.0.0 已宣称去 Termux 但仍声明 `com.termux.permission.RUN_COMMAND`；`MULTI_DEX` 非平台权限；`BIND_APPWIDGET`/`READ_LOGS`/`DUMP`/`WRITE_SECURE_SETTINGS` 等正常应用不可授予（本应被 lint ProtectedPermissions 检出，却被 A-18 关闭）
  建议: 逐条审计 47 条权限，删除不可授予/不用项
~~- **A-44** [安全|中] `MAN:347-371`~~  → 已处理（见 UC 批次11~16）
  问题: `WidgetProvider`/`Widget1x1Provider` `exported="true"` 且含自定义 action `com.UIN.Tool.REFRESH_WIDGET(_1x1)`，任意第三方可伪造广播触发刷新
  建议: 加 signature 级权限或改 exported=false（需实测 APPWIDGET_UPDATE）
~~- **A-45** [安全|中] `MAN:301-314` + `SRC/TrampolineActivity.kt:36-47,98-101`~~  → 已处理（见 UC 批次11~16）
  问题: `TrampolineActivity` exported=true 无 permission、intent-filter 无 `<data>`，却能以 `target=plugin` 启动受 `OPEN_PLUGIN` 保护的 PluginHostActivity → **权限绕过**
  建议: 加 scheme/host 约束或 exported=false
~~- **A-48** [硬编码|中] `ASSETS/docs/en/`~~  → 已处理（见 UC 批次17·下6）
  问题: en 缺 `CONTRIBUTORS.md`（zh-cn 有 184 行），`DocViewerScreen.kt:104` 用硬编码特判回退
  建议: 补英文文档并删特判
~~- **A-50** [重复|中] `ASSETS/Github_Mirrors.txt`(10.9KB)、`rootfs-check.sh`(4.4KB)、`ui_config.json`~~  → 已处理（见 UC 批次17·下52）
  问题: 三个 asset 全仓无读取代码
  建议: 删除或接线（见 G-36 建议把 rootfs-check.sh 接入自检页）
~~- **A-51** [性能|中] `ASSETS/proot/proot-*`(658KB)~~  → 已处理（见 UC 批次17·下52（用户拍板保留））
  问题: 仅打包 arm64-v8a 却内置 4 个 ABI 的 proot，3 个永不执行
  建议: 只保留 aarch64 或按 ABI 下载
~~- **A-52** [性能|中] `ASSETS/debian-rootfs.tar.xz`(43MB)~~  → 已处理（用户确认不动：非搁置，rootfs 43MB 维持 main/assets 现状，见 UC 批次17·下61）
  问题: 占仓库 74%；`ContainerManagementScreen.kt:75-77` 又把 debian 列为"可下载 ~27MB"，内置与下载双轨口径
  建议: 6.1.0 评估改为首次启动下载 + sha256 校验
~~- **B-02** [性能|中] `SRC/UinApplication.kt:113-118`、`:245-341`、`:482-526`~~  → 已处理（见 UC 批次17·下18）
  问题: 主线程同步遍历 assets 加载全部 TextMate 语法/主题
  建议: `Dispatchers.Default` 懒加载
~~- **B-03** [性能|中] `SRC/UinApplication.kt:74-79`、`:572-618`~~  → 已处理（见 UC 批次17·下18）
  问题: 主线程批量 `mkdirs/createNewFile`、删遗留 Termux 目录（权限未授时全部失败仍执行）
  建议: 移到 IO 协程，权限就绪后执行
~~- **B-04** [性能|中] `SRC/UinApplication.kt:88-92`、`:546-570`~~  → 已处理（见 UC 批次17·下18）
  问题: 主线程 `deleteRecursively` 遍历整个 plugins 目录
  建议: 移到 `Dispatchers.IO`
~~- **B-06** [坏味道|中] `SRC/UinApplication.kt:52-121`~~  → 已处理（见 UC 批次17·下30）
  问题: Application 是 God-class：10 段 try/catch 串行初始化，异常只记日志继续，顺序隐式耦合
  建议: 抽 `StartupTask`(id/优先级/调度器)
~~- **B-08** [并发|中] `SRC/UinApplication.kt:201-242`、`:225-233`~~  → 已处理（见 UC 批次12（17·上复核已修））
  问题: 裸 `Thread{}` 内再 `MainScope().launch{}`：不可取消、无异常处理器（抛异常走全局 handler 直接杀进程）
  建议: 定义 `appScope = CoroutineScope(SupervisorJob()+Dispatchers.IO)`
~~- **B-10** [i18n|中] `SRC/UinApplication.kt:154-167`~~  → 已处理（见 UC 批次17·下35）
  问题: 语言仅 zh/en，用已废弃 `resources.updateConfiguration`，无"跟随系统"与 per-app locale
  建议: `Configuration.setLocale` + `AppCompatDelegate.setApplicationLocales`
~~- **B-14** [bug|中] `SRC/MainActivity.kt:198-200`、`:377-392`~~  → 已处理（见 UC 批次11~16）
  问题: `postDelayed(1s)` 后直接 `AlertDialog.show()`，Handler 未在 onDestroy 移除 → 销毁后弹窗 `WindowLeaked`
  建议: 改 lifecycleScope + 判 `isDestroyed`
~~- **B-15** [内存泄漏|中] `SRC/MainActivity.kt:122-134`~~  → 已处理（见 UC 批次11~16）
  问题: 崩溃跳转 500ms Handler 未取消，销毁后仍 startActivity
  建议: 存引用并 onDestroy 移除
~~- **B-16** [bug|中] `SRC/MainActivity.kt:157-160,217-220,235-236`~~  → 已处理（见 UC 批次11~16）
  问题: 日志称"关闭所有插件页面"但 `closeAllPluginActivities()` **是空实现**
  建议: 实现或删除
~~- **B-17** [bug|中] `SRC/MainActivity.kt:139,447`~~  → 已处理（见 UC 批次11~16）
  问题: `UIConfig.getInstance()` 未判 `isInitialized`，Application init 失败已被吞 → onResume 必抛 `IllegalStateException`
  建议: 提供 `getInstanceOrNull()` 或降级默认主题
~~- **B-18** [安全|中] `SRC/MainActivity.kt:291,296` + `TrampolineActivity.kt:34`~~  → 已处理（见 UC 批次11~16）
  问题: 打印完整 `intent.extras`/Intent 到 logcat 与**共享存储日志**
  建议: 仅 DEBUG 或白名单 key
~~- **B-19** [bug|中] `MAN:158-169` vs `SRC/MainActivity.kt:158-169,288-325`~~  → 已处理（见 UC 批次11~16）
  问题: 声明 `uin://tools`、`uin://check_update` 深链但代码从不解析 `intent.data` → **深链实际无效**
  建议: 解析 `Uri.host/path` 统一路由
~~- **B-21** [性能|中] `SRC/MainActivity.kt:544-545,580`~~  → 已处理（见 UC 批次11~16）
  问题: 每次组合/下载 `new UpdateChecker/UpdateDownloader`，各自 new OkHttpClient，与 ServiceLocator 单例并存
  建议: 统一走 ServiceLocator
~~- **B-22** [并发|中] `SRC/MainActivity.kt:584-590`~~  → 已处理（见 UC 批次12（17·上复核已修））
  问题: 下载回调在工作线程直接写 Compose 状态、调 installApk
  建议: 回调统一切主线程
~~- **B-23** [bug|中] `SRC/MainActivity.kt:617-640`（取消 `:635`）~~  → 已处理（见 UC 批次11~16）
  问题: 静默更新进度弹窗"取消"只置 `silentProgress=false`，**不停止下载**，后台下完仍拉起安装
  建议: 调 `downloader.cancelDownload()`
~~- **B-32** [内存泄漏|中] `SRC/SplashActivity.kt:399-411`~~  → 已处理（见 UC 批次11~16）
  问题: `LaunchedEffect` 内 `Handler.postDelayed(5s)` 超时未取消，销毁后仍回调 `onNavigate()`
  建议: `withTimeoutOrNull` 或 effect 结束移除
~~- **B-33** [设计|中] `SRC/SplashActivity.kt:399-462`~~  → 已处理（见 UC 批次17·下9）
  问题: 更新检查**仅 isFirstLaunch 才执行**，5s 超时 vs 镜像探测最坏数十秒 → 开屏更新弹窗几乎必然被跳过
  建议: 与每日静默检查合并为 UpdateService
~~- **B-34** [坏味道|中] `SRC/SplashActivity.kt:480-483,703-706`~~  → 已处理（见 UC 批次11~16）
  问题: "退出"按钮 `Process.killProcess` + `System.exit(1)`
  建议: `finishAffinity()`
~~- **B-40** [坏味道|中] `SRC/TrampolineActivity.kt` 全类 + `MAN:301-314`~~  → 已处理（见 UC 批次17·下56（全类+Manifest 声明+MainActivity extra 残留））
  问题: 全仓无调用、intent-filter 无 `<data>` → 实际不可达（但见 A-45 安全面；外部是否依赖待确认）
  建议: 加埋点后删，或补 `<data>` 并接入
~~- **B-43** [坏味道|中] `SRC/core/plugin/`（8 文件 919 行）~~  → 已处理（见 UC 批次17·下46）
  问题: `WebPluginProxy`、`PluginWebInterface`、`PluginWebChromeClient`、`PluginSharedData`、`PluginBroadcastReceiver`、`IPluginCommunication`、`PluginContext`、`PluginInterface` 全部零引用（与 `plugin/` 下同名类重复）；仅 `PluginE
  建议: 删除或 `@Deprecated`（keep 规则风险见 7.5）
~~- **B-44** [bug|中] `SRC/core/plugin/PluginSharedData.kt:28-33,47-51`~~  → 已处理（见 UC 批次17·下46）
  问题: `init()` 无调用 → 使用点必抛 `IllegalStateException`（死代码）
  建议: 随 B-43 处理
~~- **B-45** [bug|中] `SRC/core/plugin/PluginBroadcastReceiver.kt:42-61,77-87`~~  → 已处理（见 UC 批次17·下46）
  问题: 未 init + IntentFilter 加了 `addDataScheme("package")` 而自定义广播无 data → 即使注册也收不到
  建议: 随 B-43
~~- **B-46** [内存泄漏|中] `SRC/core/plugin/WebPluginProxy.kt:26,42,185-195`~~  → 已处理（见 UC 批次17·下46）
  问题: 持 Activity context，onDestroy 不置空
  建议: 随 B-43 删除
~~- **B-47** [安全|中] `SRC/core/plugin/WebPluginProxy.kt:156-167`~~  → 已处理（见 UC 批次17·上）
  问题: `sendEvent` 把 eventName 直接拼 JS，无转义/白名单
  建议: JSON 编码
~~- **B-48** [安全|中] `SRC/core/plugin/PluginWebInterface.kt:122-136`~~  → 已处理（见 UC 批次17·上）
  问题: `openUrl` 不校验 scheme，可发起 `intent://`/`tel:`/`file:`
  建议: 白名单 http/https
~~- **B-49** [安全|中] `SRC/core/plugin/PluginContext.kt:29-44`~~  → 已处理（见 UC 批次17·下46）
  问题: 反射隐藏 API `addAssetPath`；与 `plugin/PluginContext.kt` 重复且后者更全
  建议: 删除
~~- **B-50** [内存泄漏|中] `SRC/core/plugin/PluginEventBus.kt:21,24,196,213-218`~~  → 已处理（见 UC 批次17·上）
  问题: 静态 map 永不清理，`clearAll()` 无调用；`debugEnabled` 裸 public var
  建议: 提供绑定生命周期的注册句柄
~~- **B-52** [性能|中] `SRC/core/accessibility/UinAccessibility.kt:44-48,26-30`~~  → 已处理（见 UC 批次17·上）
  问题: `TYPES_ALL_MASK` + `notificationTimeout=100`，每事件打日志；`getInstance()` 无调用
  建议: 收敛事件类型，移除 per-event 日志
~~- **B-53** [bug|中] `SRC/core/di/ServiceLocator.kt:23-31,64-68`~~  → 已处理（见 UC 批次17·上）
  问题: init 失败仅置 flag 且被吞，后续 `getXxx()` 直接 `IllegalStateException` → 启动即崩溃无归因
  建议: 失败重试 + 明确日志
~~- **B-54** [性能|中] `SRC/core/di/AppContainer.kt:20-27` vs `core/update/UpdateChecker.kt:32-37`~~  → 已处理（见 UC 批次11~16）
  问题: 两处各自 new OkHttpClient 却指向**同一 `cacheDir/okhttp_cache`**（双 Cache 实例同目录冲突风险），连接池不共享
  建议: 单例 OkHttpClient 注入全项目
~~- **B-56** [bug|中] `SRC/core/update/UpdateChecker.kt:202-213`~~  → 已处理（见 UC 批次11~16）
  问题: 非成功分支直接 throw，**response 未 close** → 连接泄漏
  建议: `.use { }`
~~- **B-57** [性能|中] `SRC/core/update/UpdateChecker.kt:132-191`~~  → 已处理（见 UC 批次11~16）
  问题: 直连 + 多镜像**串行** HEAD 探测，每步 8s（最坏 8×N 秒）；异常不 disconnect
  建议: 并发竞速 + 总超时预算
~~- **B-58** [并发|中] `SRC/core/update/UpdateChecker.kt:42,51-53,59,129`~~  → 已处理（见 UC 批次11~16）
  问题: 裸 Thread 不可取消；listener 永不置空 → Activity 销毁后仍回调
  建议: 协程 + try/finally 清 listener
~~- **B-60** [并发|中] `SRC/core/update/UpdateDownloader.kt:35-36,134,171,182,212`~~  → 已处理（见 UC 批次11~16）
  问题: `isDownloading`/`downloadThread` 无 `@Volatile` → `cancelDownload()` 可能不生效
  建议: 加 volatile 或改协程 Job
~~- **B-61** [性能|中] `SRC/core/update/UpdateDownloader.kt:110-188,244-266`~~  → 已处理（见 UC 批次17·下8/11）
  问题: 普通 Thread 下载，无前台服务、通知无取消按钮，退后台被回收即静默中断
  建议: WorkManager/前台服务
~~- **B-62** [并发|中] `SRC/core/update/UpdateDownloader.kt:150,172,183`~~  → 已处理（见 UC 批次11~16）
  问题: 三回调均在工作线程触发，调用方直接 Toast/写 UI
  建议: 类内统一切主线程
~~- **B-65** [bug|中] `SRC/core/compiler/JavaToDexCompiler.kt:90-103,152-173`~~  → 已处理（见 UC 批次11~16）
  问题: 原生编译已禁用却仍产出"占位 TPK/占位 dex"（中文字节串）→ 用户以为成功，插件不可用
  建议: 直接 onError 失败
~~- **B-68** [性能|中] `SRC/log/Logger.kt:75-88,132-147`~~  → 已处理（见 UC 批次11~16）
  问题: 每条日志同步 `FileWriter` 开→写→关，且在调用线程（多为主线程）；超限当场轮转
  建议: 专用日志线程 + 队列批量刷盘
~~- **B-71** [安全|中] `SRC/log/Logger.kt:60,191-196` + `constants/AppConstants.kt:55-59`~~  → 已处理（见 UC 批次17·下58（LOG_DIR 迁 getExternalFilesDir；脱敏 Logger:73 此前已落）；下60 拍板2.0 回退私有化（便利优先），脱敏保留）
  问题: 日志写**共享存储**，记录绝对路径、完整 Intent、剪贴板前 50 字
  建议: 脱敏；默认落应用私有目录
~~- **B-73** [内存泄漏|中] `SRC/cache/DiskCache.kt:20,39,54,67`~~  → 已处理（见 UC 批次11~16）
  问题: `memoryCache` 无上限 `ConcurrentHashMap<String, ByteArray>`，图标全量常驻
  建议: LRU + 字节上限(如 8MB)
~~- **B-74** [bug|中] `SRC/cache/DiskCache.kt:114-135`~~  → 已处理（见 UC 批次11~16）
  问题: cleanup 只遍历 `.meta` → **缺 meta 的数据文件永不清理**，磁盘无限增长
  建议: 按数据文件 mtime 清理 + 总容量上限
~~- **B-78** [安全|中] `SRC/constants/AppConstants.kt:52-64`~~  → 已处理（见 UC 批次17·下58（日志/备份/TPK 迁应用私有外部目录；插件/下载留共享为如实残留面）；下60 拍板2.0 反向决策：维持共享（隐私孤岛无增益+MANAGE 既有硬门），脱敏兜底）
  问题: 全部工作数据（日志/备份/TPK）在公共共享存储，仅靠 MANAGE_EXTERNAL_STORAGE
  建议: 敏感数据迁 `getExternalFilesDir()`
~~- **B-79** [坏味道|中] `SRC/MainActivity.kt:53-81,170-196,528-640`~~  → 已处理（见 UC 批次17·下16）
  问题: Activity 内嵌"每日静默更新检查+下载+弹窗"业务逻辑
  建议: 下沉 UpdateViewModel
~~- **B-80** [坏味道|中] `SRC/UinApplication.kt:194-243`~~  → 已处理（见 UC 批次17·下18）
  问题: Application 直接编排 proot/plugin 容器安装与 Toast
  建议: 抽 EnvironmentBootstrap
~~- **B-81** [坏味道|中] `SRC/core/` 目录结构~~  → 已处理（见 UC 批次17·下44）
  问题: core 混装 di/update/UI 侧 WebView 桥/无障碍/编译器，与顶层 `plugin/` 概念重叠
  建议: core 只留 di/log/cache/update
~~- **C-01** [安全|中] `MAN:173-178`(exported:175, permission:176)、权限声明 `MAN:425-429`(protectionLevel:429)~~  → 已处理（见 UC 批次17·下17）
  问题: `PluginHostActivity` exported="true"，但全仓 7 个启动点全在本应用内（`MainActivity.kt:252`、`TrampolineActivity.kt:98`、`WidgetProvider.kt:409/677`、`PluginManager.kt:607`、`PluginOpenDispatchActivity.kt:282`、`PluginJSI
  建议: 改 `exported="false"` 并移除 permission
~~- **C-04** [bug/安全|中] `PluginJSInterface.kt:1949-1960` vs `:1961-1971`~~  → 已处理（见 UC 批次11~16）
  问题: `checkPermission` 不查 `pluginInfo.permissions`，`requestPermission` 却强制要求声明 → 未声明权限返回 true/denied 矛盾，插件无法依赖返回值做 UI 分支
  建议: checkPermission 同样要求已声明
~~- **C-05** [坏味道|中] `PluginManager.kt:94,109,1151,482,565,946`、`PluginBackendManager.kt:467`~~  → 已处理（见 UC 批次17·下50）
  问题: 7 个函数零调用：`clearRetainedWebViews`、`clearWebViewCache`、`clearAllPlugins`、`updatePlugin`、`hasPluginUpdate`、`showPermissionGuidance`、`shutdown`；前两个死代码直接导致 C-12 的 WebView 无法释放
  建议: 删除或接线（clearRetainedWebViews 挂 onTrimMemory）
~~- **C-07** [坏味道|中] `PluginHostActivity.kt:524-525`(空体)；调用点 **9 处** `:379,429,443,452,464,1280,1294,1302,1699`~~  → 已处理（见 UC 批次11~16）
  问题: `dismissEnvProgress()` 是空函数，而 `showEnvProgress`(:519-522) 只是 Toast → 调用方以为在收起进度弹窗，实际无进度条可关
  建议: 实现为真实 Dialog 并接进度，或删空函数与全部调用
~~- **C-09** [性能|中] `UinApplication.kt:95` → `SharedSupervisor.kt:136-148`（exec+waitFor+sleep(300)）；`SharedSupervisor.kt:157`(sleep(2000) 定义于 :151-163，调用 :272)~~  → 已处理（见 UC 批次11~16）
  问题: 主线程阻塞仅前者（0.3~1s）；sleep(2000) 在后台线程（prewarm:410-416 / startBackendFor Thread PluginManager.kt:1069），**不计主线程问题**
  建议: killStaleProcesses 移 IO；waitFor 加超时
~~- **C-13** [安全|中] `PluginHostActivity.kt:1061`、`:1105-1117`(仅拦主框架)、`:1446-1451`；`ASSETS/plugin_templates/web/*`~~  → 已处理（见 UC 批次11~16）
  问题: 主框架导航受控但**子资源不受控**：插件页 `<script src="https://...">` 可加载远程脚本，远程代码即获得 174 个桥方法；模板无 CSP
  建议: 模板加 CSP；`shouldInterceptRequest` 拦非自身 origin 的 script/xhr
~~- **C-16** [安全|中] `PluginJSInterface.kt` 多处~~  → 已处理（见 UC 批次11~16）
  问题: **权限校验 4 套语义并存**：①声明+授权+未封禁(hasPluginPermission:2720-2731)：getDeviceId:243/getSerialNumber:272/getMacAddress:286/getOperatorInfo:1001/getLocation:633/getAddress:664/getWifiInfo:961/getSignalStrength:9
  建议: 全部收进 `CAPABILITY_MAP`：ping/DNS→INTERNET、sendNotification→POST_NOTIFICATIONS、takeScreenshot→声明+WRITE、openApp/openSettings→低危组；统一为 `PermissionGuard.can()`
~~- **C-18** [并发/内存|中] `PluginHostActivity.kt:1122-1124,1461-1471` + `PluginJSInterface.kt:133-139`~~  → 已处理（见 UC 批次11~16）
  问题: 每次 onPageFinished remove+add 新桥，旧 OkHttpClient/连接池无关闭路径；多实例 `Cache(Constants.CACHE_DIR,"okhttp_cache")` 同目录 → 可能抛 `CACHE DIRECTORY IS ALREADY IN USE`
  建议: 桥注入一次并复用；OkHttpClient/Cache 单例
~~- **C-19** [并发|中] `PluginHostActivity.kt:423,1274`（MainScope().launch 无 cancel）~~  → 已处理（见 UC 批次11~16）
  问题: Activity 销毁后协程仍会执行 `showPluginInfoDialog`/`startBackendAfterEnv`/`finish()`(:431-454,:1279-1310)
  建议: 改 lifecycleScope 或保存 Job 取消
~~- **C-20** [bug|中] `PluginHostActivity.kt:408-417`~~  → 已处理（见 UC 批次11~16）
  问题: `while (isEnvironmentInstalling()) Thread.sleep(300)` 忙等**无超时不可取消**；结束后回主线程再入流水线，Activity 已销毁仍继续
  建议: 协程 flowWithLifecycle + 5min 上限 + 失败回调
~~- **C-22** [bug|中] `UinApplication.kt:95` + `SharedSupervisor.kt:136-148` vs `:240-254`(keep_alive)~~  → 已处理（见 UC 批次11~16）
  问题: **"后台保活"与启动清理互斥**：开启 keep_alive 后重启 App，killStaleProcesses 立即杀 supervisor 与所有后端 → 保活无法跨进程重启
  建议: kill 前检查 `keepAliveFile().exists()`
~~- **C-24** [性能|中] `PluginBackendManager.kt:847-866`(调用线程 sleep(400))、`:1141-1150`(isPortOpen 500ms)、`:135-141`(主 Looper Handler)~~  → 已处理（见 UC 批次11~16）
  问题: `checkIdleBackends` 在**主线程**对每端口做最多 500ms socket connect，stopBackend 再 sleep(400) → 周期性阻塞可达 ~1s/次
  建议: cleanup 移后台线程；端口探测异步
~~- **C-25** [bug|中] `PluginManager.kt:273-277`~~  → 已处理（见 UC 批次11~16）
  问题: `info.minHostVersion > Build.VERSION.SDK_INT`：**宿主版本号却与 Android API 级别比较** → 该兼容闸门在 minSdk 23+ 永不生效
  建议: 与 `BuildConfig.VERSION_CODE` 比较
~~- **C-29** [bug|中] `supervisor.sh:21`~~  → 已处理（见 UC 批次11~16）
  问题: `awk '{print $4}' /proc/pid/stat` 未剥离 comm 中空格/右括号 → PPID 解析错位，pgrep 不可用时 `find_children` 找不到子进程 → `kt`(:27-30) 杀不干净进程树
  建议: `sed 's/.*) //'` 后取字段，或读 `/proc/pid/status` 的 PPid
~~- **C-33** [安全|中] `ASSETS/plugin_templates/backend/server.py.tmpl:17-19,28-30`~~  → 已处理（见 UC 批次11~16）
  问题: 模板内置**未鉴权 `/stop`**：任意本机进程乃至任意网页 `fetch(...,{mode:'no-cors'})` 打 `127.0.0.1:<port>/stop` 即可关闭插件后端（模板被所有插件复用）
  建议: 加 `UIN_TOKEN` 校验（宿主注入，健康检查同带）
~~- **C-34** [安全|中] `SRC/utils/TemplateUtils.kt:23-29`(renderTemplate 纯 replace 无转义) + `backend/start.sh.tmpl:2`、`server.py.tmpl:2`、`web/simple_index.html.tmpl:46`、`NativePlugin.java.tmpl:33,39`~~  → 已处理（见 UC 批次11~16）
  问题: **模板变量注入**：`{{PLUGIN_NAME}}` 出现在 shell 注释、JS 注释、**Java 字符串字面量**与 HTML；名称含 `"`、`\`、换行即可生成无法编译的 Java 或在执行 start.sh 时注入命令
  建议: 按目标语言转义；向导校验 name/author 非法字符
~~- **C-37** [性能|中] `PluginJSInterface.kt:1746-1893`(HTTP 四段重复)、`:1776-1780`(`body.string()` 全量入内存)、`:1894-1946`(downloadFile 无大小上限、每次新建 Thread)~~  → 已处理（见 UC 批次11~16）
  问题: HTTP 实现四份拷贝；超大响应/文件可 OOM 或写满磁盘（未走 `hasEnoughSpace`，对比 `PluginContext.kt:227-239`）
  建议: 抽 `HttpBridge.request()`；加 Content-Length/流式上限(20MB)与统一线程池
~~- **C-38** [bug|中] `PluginHostActivity.kt:81-84`(backendKey 恒为 pluginId) vs `PluginBackendManager.kt:505-507`、`SharedSupervisor.kt:358`(允许实例键) + `stopBackend:788-816`、`checkIdleBackends:153-186`~~  → 已处理（见 UC 批次17·下36）
  问题: 后端键语义不一致：start 支持实例键、stop/回收按 pluginId → 一旦启用独立端口多开将 miss 并**留下孤儿进程**（当前被 :81-84 兜住）
  建议: 明确单一语义
~~- **D-10** [并发|中] `SRC/proot/RootfsManager.kt:94-95,195-196` vs `:252-267,295-383` + `ContainerManagementScreen.kt:87,151,611`~~  → 已处理（见 UC 批次11~16）
  问题: 锁只覆盖 install；`deleteContainer`/`resetContainer`(先删后装)/`exportContainer` 不加锁，UI 的 `isInstalling` 也不拦截导出导入 → 可与安装并发读写同一容器
  建议: 统一 per-container 读写锁；UI 入口统一受门禁
~~- **D-12** [性能|中] `SRC/proot/RootfsManager.kt:171-187`~~  → 已处理（见 UC 批次11~16）
  问题: `countTarEntries` 为拿总数**把整个压缩包完整解压一遍**（导入=拷贝+计数+安装共 3 次全量 IO），`catch(_:{})` 吞异常返回 0
  建议: 与安装合并一遍扫描；异常记日志
~~- **D-13** [bug|中] `SRC/proot/RootfsManager.kt:142,145,229,232,487-492` vs `RootfsSetup.kt:50`(死代码)~~  → 已处理（见 UC 批次11~16）
  问题: `/tmp` chmod `511`(0777)**丢 sticky 位**（死代码用 1777，两处冲突）；`setFilePermissions` 屏蔽 setuid/setgid/sticky 并强制 or 0600/0700，改写 rootfs 特殊权限
  建议: 统一 `0o1777`；权限只保留 `0o7777` 全集
~~- **D-14** [bug|中] `SRC/proot/RootfsManager.kt:494-504,512-522`~~  → 已处理（见 UC 批次11~16）
  问题: 收尾无条件覆盖 `/etc/resolv.conf`、`/etc/hosts`；若为符号链接（systemd-resolved 常见）且目标不存在 → FileNotFoundException → **整个安装失败**（rename 之前，只剩 staging 残留）
  建议: 写前判 `Files.isSymbolicLink`；各步骤独立 runCatching
~~- **D-15** [bug|中] `SRC/proot/RootfsManager.kt:114-131,209-215`~~  → 已处理（见 UC 批次11~16）
  问题: 压缩格式按**文件名后缀**判断而非魔数；asset 侧与文件侧条件还不一致
  建议: 读前 6 字节嗅探 xz/zstd/gzip magic
~~- **D-16** [bug/OOM|中] `SRC/proot/ProotRuntime.kt:219-234`~~  → 已处理（见 UC 批次11~16）
  问题: `executeCommandWithOutput` 无超时(`waitFor()` 无参)、`readText()` 全量进内存(循环输出可 OOM)、`CommandResult.error` 恒为空串(:230 写死)
  建议: 加超时+destroyForcibly；流式限长；回填 stderr
~~- **D-17** [坏味道|中] `SRC/proot/ProotRuntime.kt:20-26` vs `:158-185`~~  → 已处理（见 UC 批次17·中）
  问题: 仍传 `--link2symlink` 却在注释称"内网存储不需要"并每次删 `.l2s`；buildCommand 先建 `.l2s`(:72-73) 再于下次启动删掉，两套逻辑打架
  建议: 二选一
~~- **D-18** [bug|中] `SRC/proot/ProotRuntime.kt:193-217,207-211`~~  → 已处理（见 UC 批次11~16）
  问题: `executeCommand` 只注入 `DEFAULT_ENV`，**忽略 `config.environmentVars`**（`ProotContainerManager.execCommand(extraEnv=)` 静默失效）；`pb.directory(File(rootfs, workingDir))` 把容器内路径当宿主 cwd，`workingDir=/plugins/<id>
  建议: 合并 env；cwd 不存在则 mkdirs 或回退 rootfs 根
~~- **D-19** [bug|中] `SRC/terminal/SimpleTerminalActivity.kt:441-464,473-475`~~  → 已处理（见 UC 批次11~16）
  问题: CUI 分支手工重复拼装 proot 参数，**漏掉 FakeSysdata 挂载** → 该路径容器内 `/proc/stat`、`/sys/fs/selinux` 缺失，`top/htop/apt` 表现异常
  建议: 统一走 `ProotRuntime.buildCommand`
~~- **D-21** [bug|中] `SRC/terminal/SimpleTerminalActivity.kt:109,401-416,503-513`~~  → 已处理（见 UC 批次11~16）
  问题: 会话名以 `position` 为 key，`closeSession` 删除后不重排 → 重命名/显示名错位到别的会话
  建议: 改用 `mHandle` 作 key
~~- **D-22** [生命周期|中] `SRC/terminal/SimpleTerminalActivity.kt:216-220,244` + `MAN:145-148`~~  → 已处理（见 UC 批次11~16）
  问题: 未锁 `screenOrientation`/`configChanges` → 横竖屏走 onDestroy，**所有会话被 SIGKILL 且不可恢复**；`keepScreenOn=true` 常亮耗电
  建议: 锁方向或保留会话；按需 keepScreenOn
~~- **D-23** [并发/进程|中] `TERM/TerminalSession.java:235-243`~~  → 已处理（见 UC 批次11~16）
  问题: `finishIfRunning` 只 `Os.kill(mShellPid, SIGKILL)` 不杀进程组 → ptracer 被杀后 tracee（容器内 shell 及子进程）可能成孤儿继续运行
  建议: `Os.killpg(Os.getpgid(pid), SIGKILL)` + 退出后校验残留 pid
~~- **D-26** [bug|中] `SRC/data/repository/BackupRepositoryImpl.kt:224-232` vs `:199-207`~~  → 已处理（见 UC 批次11~16）
  问题: 回滚条件要求 `stageDir != null && oldDir != null`，但成功交换后两者已被置 null → **注释宣称的"已顶替后回滚"是死代码，实际永不回滚**
  建议: 用状态枚举(PREPARED/SWAPPED/COMMITTED)驱动
~~- **D-27** [性能/坏味道|中] `SRC/data/local/PreferenceManager.kt:142-147,9-12,176,229,239,250,267-269`~~  → 已处理（见 UC 批次17·中）
  问题: ①`savePluginPermissions` 每 key 单独 `apply()` ②构造即打开 4 个 SP（主线程首次访问触发磁盘加载）③多个 key 硬编码未走常量 ④`getPrefs()` 泄漏抽象 ⑤getter 无类型兜底（历史非字符串值抛 ClassCastException）
  建议: 单次 edit 批量写；key 收敛常量；类型安全包装
~~- **D-29** [架构|中] `SRC/core/di/AppContainer.kt:29-46`、`ServiceLocator.kt:54`、`BackupRepositoryImpl.kt:23`、`BackupViewModel.kt:30`、`GitHubMirrorScreen.kt:48`、`MirrorViewModel.kt:28`、`RepoViewModel.kt:73`~~  → 已处理（见 UC 批次17·下14）
  问题: DI 不完整：FileManager/IBackupRepository 未注册（VM 手工 new）、UI 各自 `MirrorManager(OkHttpClient())`、GitHubApiService 自建 → 单例耦合 + 网络策略碎片化
  建议: 全部收敛 AppContainer
~~- **D-32** [bug|中] `SRC/data/remote/MirrorManager.kt:52-76,79-82`~~  → 已处理（见 UC 批次17·中）
  问题: ①按子串 contains("ghproxy"/"fastgit"/"moeyy") 分派模板，else 一律 `"$mirror/$url"`，对 `gitclone.com`(在 DEFAULT_MIRRORS 中)拼出非法 API URL ②"根路径可达"被当作"可代理 API" ③`forceHttps` 强改用户自建局域网 http 镜像 → 必失败
  建议: 镜像声明式能力位(apiProxy/downloadProxy)；allowInsecure 开关
~~- **D-33** [安全|中] `SRC/proot/ProotRuntime.kt:12,20-26,85-94`~~  → 已处理（见 UC 批次17·中）
  问题: 无条件 bind `/storage/emulated/0`、`/dev`、`/proc`、`/sys` 且 `--change-id=0:0` → 容器内进程（含插件后端）以 root 读写全部共享存储与宿主设备节点
  建议: 最小挂载集 + 用户可开关
~~- **D-34** [性能/泄露|中] `SRC/log/Logger.kt:75-88,132-147,159` + `AppConstants.kt:59` + `ProotRuntime.kt:214`~~  → 已处理（见 UC 批次17·下58 闭环（异步单线程写 + 脱敏此前批次已落，私有目录下58 落地）；下60 私有化部分经拍板2.0 回退，异步单线程+脱敏保留）
  问题: 每条日志同步开-写-关（无缓冲无锁，多线程交错）；日志写**共享外部存储**含绝对路径、完整命令与环境变量
  建议: 异步单线程写入 + 私有目录 + 脱敏
~~- **E-03** [主题|中] `UNI:248-249,288-289,334-337,745-761`~~  → 已处理（见 UC 批次17·下6）
  问题: `UINCard` 圆角/描边不一致：generalRadius 16dp / 288→12dp / 334→8dp / 自定义 override 各不相同，`UINCard(outlined=true)` 用硬编码灰 `Color(0xFFE0E0E0)`（深色不可见）
  建议: 收敛到 `AppDimens.cardRadius` + `colorScheme.outline`
~~- **E-04** [性能|中] `UNI:990-1015,1041-1145`、`UnifiedFoundation.kt:415-560`~~  → 已处理（见 UC 批次11~16）
  问题: `UISkeletonBox`/`UILoadingShimmer` 用 `infiniteTransition.animateFloat` 无限重组+多层 translate；`rememberInfiniteTransition` 无 `label`；shimmer 硬编码 `Color(0xFFE0E0E0)`
  建议: 单 brush 动画 + `graphicsLayer`；深浅色改 surfaceVariant
~~- **E-05** [可访问性|中] `UNI:1568-1645`(UINModal)、`:1720-1748`~~  → 已处理（见 UC 批次17·下6）
  问题: 自绘 `ModalBottomSheet`（Box+AnimatedVisibility）**无 focus/无障碍/背板**：不响应返回键、不可 Tab 聚焦、TalkBack 不可知；**UINModal 是否被引用需核**（UIConfigPreview 为死代码，若仅其引用则整组件为死）
  建议: 改用 Compose `ModalBottomSheet`；核 E-25
~~- **E-06** [可访问性|中] `UNI` 全文件 + `ui/onboarding/OnboardingScreen.kt:167-171` 等~~  → 已处理（见 UC 批次11~16）
  问题: 组件级缺 `contentDescription`/语义：`UINCard`/`UINButton` 无 `semantics { role = Role.Button }`；`UINCodeField` 无 `TestTag+contentDescription`；`UILinearProgress` 无 progress 语义
  建议: 统一补 semantics；关键组件加 TestTag 供 UI 测试
~~- **E-07** [可访问性|中] `UNI:540-542,613-614,647-648,698-699,728-729,773,804,840` 等~~  → 已处理（见 UC 批次11~16）
  问题: 触控目标普遍 <48dp：`UISwitch` 40dp、`UINBadge` 16dp、导航胶囊 18dp 高、`UILinearProgress` 6dp；Material 最小 48dp
  建议: 隐形扩大 hitbox（`Modifier.minimumInteractiveComponentSize` 或 minimumTouchTarget）
~~- **E-08** [性能|中] `ui/theme/Theme.kt:402,411`~~  → 已处理（见 UC 批次17·下32）
  问题: `configVersion`/`UIConfig` 变化在 MaterialTheme **最外层**取值 → 任何配置改动（含字号、圆角）触发整棵 Compose 树重组，而非局部
  建议: 缩小读取范围（拆子 CompositionLocal）或 key 到实际消费组件
~~- **E-09** [一致性|中] `ui/theme/Theme.kt:206`(shouldUseDarkTheme)、`A-35`~~  → 已处理（见 UC 批次17·下34）
  问题: Compose 深浅色由 `UIConfig.shouldUseDarkTheme`（用户可固定）决定，XML 资源跟随系统 uiMode → 强制深色+系统浅色时 View 界面与 Compose 界面分裂
  建议: 见 A-35
~~- **E-11** [死代码|中] `ui/theme/Color.kt:81-148`、`Theme.kt:487-640`~~  → 已处理（见 UC 批次17·下56（原目标已随文件演化消失，另删 Theme.kt 25 个零引用死色值））
  问题: 深浅色 `*Light/*Dark` 别名 57 处 + `LinearProgressGradients/BlockGradientColors/VoteColors` 三组 Compose colorScheme 扩展全部零引用（仅 Preview 部分引用）
  建议: 删除或接到 Unified 组件
~~- **E-12** [一致性|中] `ui/theme/Type.kt` vs `UnifiedFoundation.kt:604-769`~~  → 已处理（见 UC 批次17·下38）
  问题: 两套字体体系：`MaterialTheme.typography`（Type.kt 其余死）与 `AppTypography`（Unified 实际用），字号映射规则不同（有 `fontSizeScale`/`compress` 逻辑）
  建议: 明确 AppTypography 为唯一，或由 Type.kt 生成
~~- **E-13** [一致性|中] `ui/theme/Shape.kt:1-18`、`Dimens.kt:1-37`、`UnifiedFoundation.kt:565-601`(AppDimens)~~  → 已处理（见 UC 批次17·下38）
  问题: 三套尺寸/形状源（Shape 全死、Dimens 仅 ~10 处、AppDimens 主用），且 AppDimens 内 `extraSmall=8dp` 与 Dimens `xSmall=4dp`/Unified 内 `small=4dp` 数值互相矛盾
  建议: 删 Shape/Dimens，AppDimens 收口
~~- **E-17** [性能|中] `utils/SecurityUtils.kt:327-440`~~  → 已处理（见 UC 批次11~16）
  问题: `readFile` 按行读、`countFileLines` 再读一遍全文件（**2 遍**），`processStream` 逐字节缓冲 4096 → 大文件双重全量 IO
  建议: 合并单遍
~~- **E-18** [线程/安全|中] `utils/Extensions.kt:53-56`、`:27-30`、`:63-66`~~  → 已处理（见 UC 批次11~16）
  问题: ①`showToast` 在**任意线程**调 Toast（Android 11+ 非主线程抛 RuntimeException，与 B-31 同根因）②`applyPluginIntent` 用 `intent.component?.shortClassName` 字符串 `!=` 比较 ③`shortClassName` 展示英文类名无 i18n
  建议: 用 `AppToast`（已切主线程，见 F 区）；比较用 ComponentName
~~- **E-21** [坏味道|中] `utils/ShortcutManager.kt:57,64,144`、`utils/TemplateUtils.kt:23-29`(=C-34)、`utils/PermissionManager.kt`~~  → 已处理（见 UC 批次17·下31）
  问题: ①`ShortcutManager` 桌面快捷方式硬编码 60dp 位图（不可缩放）②模板无转义 ③`utils/PermissionManager.kt` 与 `PluginPermissionManager` **同名不同层**（宿主运行时权限 vs 插件声明权限），命名易混淆
  建议: 快捷方式用 AdaptiveIcon；命名区分 `HostRuntimePermission` / `PluginPermissionGate`
~~- **E-23** [一致性|中] `ui/components/*`（区汇总）~~  → 已处理（见 UC 批次17·下48 全量迁移）
  问题: `UIComponents.kt`（1,326 行）import 仅 Spacer/Icons 可解析、`Neumorphic*` 仅死 Preview 用；三套并存导致同一按钮在不同界面圆角/字号不同
  建议: 见 6.1 结论 + 死代码章
~~- **E-24** [死代码候选|中] `ui/components/NeumorphicComponents.kt`~~  → 已处理（见 UC 批次17·下48 判早已执行）
  问题: 引用者仅 `UIConfigPreviewScreen`（零调用）→ **Neumorphic 全套实为死代码**（引用死屏）
  建议: 与 UIConfigPreviewScreen 一并删除（需用户确认，见 D 章待决策）
~~- **E-25** [死代码候选|中] `ui/components/UIComponents.kt`~~  → 已处理（见 UC 批次17·下48 判过期误判（文件已随 E-23 删除））
  问题: 1,326 行、真实可达 import 仅 Spacer/Icons → 死
  建议: 删除
~~- **F-01** [bug|中] `ui/screen/manage/ContainerManagementScreen.kt:58-77`~~  → 已处理（见 UC 批次17·下 容器口径）
  问题: 把内置 Debian 列为"可下载 ~27MB"，与 A-52 内置 43MB 双轨口径冲突；用户选"下载"实为走另一套 rootfs 路径
  建议: 统一口径（内置 vs 可选下载，二选一并明示）
~~- **F-04** [一致性|中] `ui/screen/manage/PluginManageScreen.kt` 多处（空状态/加载/错误）~~  → 已处理（见 UC 批次17·下10）
  问题: 与 `PluginRepositoryScreen`、`ManageScreen` 各自实现一套空态/骨架/错误卡，文案与按钮不一致
  建议: 复用 `UnifiedComponents` 的 `UINEmptyState/UILoadingSkeleton/UINErrorCard`
~~- **F-05** [状态管理|中] `ui/screen/manage/BackendSettingsScreen.kt:52,263,405`、`MirrorScreen/GitHubMirrorScreen`、`SettingsScreen.kt:155-157` 等~~  → 已处理（见 UC 批次17·下19/20）
  问题: 大量 `mutableStateOf` 手写状态 + 手写协程，未进 ViewModel → 配置旋转丢失、无法单测
  建议: 按 tab 抽 ViewModel（`ui/viewmodel/` 已有 10 个几乎全死，可复用其一）
~~- **F-06** [表单校验|中] `ui/screen/manage/*SettingsScreen.kt`（backend/mirror/update/source 等）~~  → 已处理（见 UC 批次11~16）
  问题: 多处输入框无非空/格式/范围校验（镜像 URL、端口、超时秒数），非法值直接落盘后启动失败
  建议: 统一 `FieldState` 校验 + 保存前拦截
~~- **F-10** [安全|中] `ui/screen/dev/`（模板相关）+ `C-34`~~  → 已处理（见 UC 批次11~16）
  问题: 向导未校验 plugin name/author 中的引号、反斜杠、换行 → 进 shell/JS/Java 模板即注入
  建议: 校验 + 按宿主转义
~~- **F-12** [坏味道|中] `ui/screen/dev/`（15 文件 4,724 行）~~  → 已处理（见 UC 批次17·下45 判过期关闭）
  问题: 向导步骤、表单、预览、签名多处重复自定义输入组件，未复用 `UINTextInput/UINButton`
  建议: 组件替换，收敛行数
~~- **F-13** [架构|中] `ui/screen/repo/RepoScreen.kt:48` + `ui/viewmodel/RepoViewModel.kt:73`~~  → 已处理（见 UC 批次17·下48 判已解决）
  问题: 全区唯一存活的 ViewModel 却手工 new MirrorManager（D-29）；其余 9 个 ViewModel 零引用（见死代码章）
  建议: 纳 AppContainer；死 VM 待决策
~~- **F-15** [并发|中] `ui/screen/repo/*`（安装/导入）~~  → 已处理（见 UC 批次11~16）
  问题: 安装中无全局门禁，可并发触发同一插件两次安装（PluginManager 防抖 C-26 又丢弃）
  建议: 单飞（in-flight 去重）+ 状态驱动按钮禁用
~~- **F-16** [一致性|中] `ui/screen/tools/`~~  → 已处理（见 UC 批次17·下40/41/42）
  问题: 工具页多为一次性 IO 操作，未复用统一长任务框架（5.1 建议 1）
  建议: 接入
~~- **G-06** [权限|中] `ui/screen/backup/BackupScreen.kt`（路径选择/删除）~~  → 已处理（见 UC 批次17·下58（备份目录私有化，备份功能自身不再依赖 MANAGE_EXTERNAL_STORAGE）；下60 拍板2.0 维持共享（授权面为全 App 既有硬门、非备份独有））
  问题: 依赖 MANAGE_EXTERNAL_STORAGE，Android 11+ 所有文件访问授权跳转有成功率问题；未覆盖 Android 14+ 精细媒体权限
  建议: 关键数据迁 `getExternalFilesDir()`（= B-78）
~~- **G-07** [错误可见性|中] `ui/screen/backup/BackupScreen.kt`（导出/导入完成态）~~  → 已处理（见 UC 批次11~16）
  问题: 失败仅 Toast 或静默，无"上次失败原因"可回溯
  建议: 结果内联展示 + 写入日志入口
~~- **G-09** [i18n|中] `ui/screen/docs/DocViewerScreen.kt:100-113` + `ASSETS/docs/en/CONTRIBUTORS.md` 缺失~~  → 已处理（见 UC 批次17·下6）
  问题: en 缺 CONTRIBUTORS，硬编码 `if (file=="CONTRIBUTORS.md") 回退 zh` 特判
  建议: 补英文文档删特判（= A-48）
~~- **G-10** [一致性|中] `ui/screen/docs/` 树状目录~~  → 已处理（见 UC 批次17·下12）
  问题: 与 `HelpScreen` 功能重复（两套目录树、两套 markdown 渲染入口）
  建议: 归并单一入口
~~- **G-11** [接线|中] `ui/screen/log/LogViewerScreen`（日志查看器）~~  → 已处理（见 UC 批次17·下58（LogViewer/DevTools 经 Constants.LOG_DIR 单点自动随迁，零改码）；下60 路径回迁后同读共享、前提回调，零改码）
  问题: 日志文件写共享存储（B-71），查看器读取依赖同路径；若 A-52/B-78 迁移私有目录，查看器需同步
  建议: 随 D-34 一并改
~~- **G-12** [性能|中] `ui/screen/log/*`~~  → 已处理（见 UC 批次11~16）
  问题: 全量读日志进内存（日志无限增长风险，Logger 有 500 行轮转但历史多文件）
  建议: 分页/Tail 读取（只读末 N 行）
~~- **G-14** [硬编码|中] `ui/screen/onboarding/OnboardingScreen.kt:57,69,155`、`OnboardingActivity.kt:17`~~  → 已处理（见 UC 批次11~16）
  问题: 兜底版本串 `"6.0.0"`/`"5.2.0"` 三处（= A-40）
  建议: BuildConfig
~~- **G-15** [主题|中] `ui/screen/onboarding/*`~~  → 已处理（见 UC 批次11~16）
  问题: 独立 Activity 主题与 Compose 主题分裂（= E-09/A-35），onboarding 深色适配需实测
  建议: 统一进入前 setTheme + UIConfig
~~- **G-16** [架构|中] `ui/screen/permission/*`（7 文件 1,452 行）~~  → 已处理（见 UC 批次17·下37）
  问题: 权限解释页与 `SplashActivity` 的权限申请流程分离，两套 state/文案
  建议: 与 B-28/B-29 合并为单一 `PermissionFlow`
~~- **G-21** [状态|中] `ui/screen/proot/*`~~  → 已处理（见 UC 批次11~16）
  问题: `isInstalling` 单 bool 不与 D-10 锁联动，可并发导入导出
  建议: 统一门禁
~~- **G-23** [架构|中] `ui/viewmodel/`（存活者 RepoViewModel.kt:73）~~  → 已处理（见 UC 批次17·下48 判已解决）
  问题: 手工 new MirrorManager 绕过 DI（= D-29）
  建议: AppContainer
~~- **G-25** [架构|中] `widget/WidgetProvider.kt:409,677`~~  → 已处理（见 UC 批次17·下36）
  问题: 小部件启动 `PluginHostActivity`（带 pluginId）——实例键错位（C-02）使该路径 resume/pause 同样失效
  建议: 随 C-02 修
~~- **G-26** [安全|中] `MAN:347-371`（= A-44）~~  → 已处理（见 UC 批次11~16）
  问题: 两个 Provider exported=true + 自定义 action 可被伪造广播触发
  建议: signature 权限或 exported=false
~~- **G-27** [bug|中] `widget/WidgetConfigureActivity.kt`（全局配置读写）~~  → 已处理（见 UC 批次11~16）
  问题: "小部件全局配置"UI 存在但写入的键无处消费（`WidgetProvider` 未读）→ 全局配置是死开关
  建议: Provider 读取该配置或删除 UI
~~- **A-15** [构建|低] `app/proguard-rules.pro:61-65`、`RES/values/attrs.xml:7-10`~~  → 已处理（见 UC 批次11~16）
  问题: 保留自定义 View 三构造器，但 app 无自定义 View；`declare-styleable CustomCardView` 无对应类
  建议: 删除该块与 styleable
~~- **A-21** [构建|低] `.github/workflows/android-build.yml:8`~~  → 已处理（见 UC 批次17·下4）
  问题: 注释称"AGP 8.13 要求 JDK 17+"，实际 AGP 8.9.1
  建议: 与 build.gradle 同步
~~- **A-26** [重复|低] `RES/drawable/glass_vackground_dark.xml:1`~~  → 已处理（见 UC 批次17·下51）
  问题: 文件名拼写错误(vackground)，内容与 `glass_background_dark.xml` 除声明外相同，两者均无引用
  建议: 删除
~~- **A-38** [硬编码|低] `RES/layout/terminal_session_item.xml:23`~~  → 已处理（见 UC 批次17·下2）
  问题: `android:text="✕"` 作关闭按钮，无可访问性说明
  建议: 改图标 + contentDescription
~~- **A-46** [坏味道|低] `MAN:90-94`、`:110`~~  → 已处理（见 UC 批次17·下2）
  问题: `<queries>`(targetSdk≥30 才生效) 与 `tools:targetApi="33"` 在 targetSdk 28 下无效/误导
  建议: 注释说明"为升级预留"或删除
~~- **A-47** [安全|低] `MAN:98` + `RES/xml/backup_rules.xml:8`~~  → 已处理（见 UC 批次17·下2）
  问题: `allowBackup=true` 且白名单仅 `ui_config.json`（合理）；注意 `data_extraction_rules`(API31+) 在 targetSdk 28 下不生效
  建议: 6.1.0 明确升级 targetSdk 时的迁移项
~~- **A-53** [坏味道|低] `app/Project_ic.py`、`Project_ic.py.bak`~~  → 已处理（见 UC 批次17·下21）
  问题: 开发机私有脚本（含设备绝对路径）残留在 app 模块，被 `.gitignore` 掩盖
  建议: 移出仓库或正规化到 `scripts/`
~~- **A-54** [构建|低] `.github/workflows/android-build.yml` 整体~~  → 已处理（见 UC 批次17·下21）
  问题: 无 `permissions:` 最小化、无 wrapper 校验、无 NDK 缓存、无产物哈希/签名
  建议: 补 wrapper-validation、`permissions: contents: read`、NDK 缓存
~~- **B-05** [性能|低] `SRC/UinApplication.kt:81-86` + `SRC/cache/DiskCache.kt:114-135`~~  → 已处理（见 UC 批次17·下18）
  问题: 每次冷启动主线程扫描 `cache/icons`
  建议: 并入后台清理
~~- **B-09** [错误处理|低] `SRC/UinApplication.kt:128-152`~~  → 已处理（见 UC 批次17·下2）
  问题: 非线程崩溃仅记录不终止（合理），但 catch 回退 `previous.uncaughtException` 会绕过记录
  建议: logcat 兜底
~~- **B-11** [硬编码|低] `SRC/UinApplication.kt:142,181,197,204-237,553,595`~~  → 已处理（见 UC 批次17·下4）
  问题: 日志中英混杂，绕过 `Str.get`
  建议: 统一英文或统一走资源
~~- **B-12** [坏味道|低] `SRC/UinApplication.kt:30-37`~~  → 已处理（见 UC 批次17·下4）
  问题: 静态 `lateinit instance`，attachBaseContext 前访问会抛
  建议: `by lazy` 或前置校验
~~- **B-20** [坏味道|低] `SRC/MainActivity.kt:293-308` vs `:311-325`~~  → 已处理（见 UC 批次11~16）
  问题: tab 映射两份重复实现，onNewIntent 中连续执行两遍
  建议: 合并为 `Intent.toTab()`
~~- **B-24** [硬编码|低] `SRC/MainActivity.kt:603`~~  → 已处理（见 UC 批次17·下5）
  问题: 手动下载地址硬编码 GitHub URL，绕过镜像
  建议: 走 MirrorManager
~~- **B-25** [性能|低] `SRC/MainActivity.kt:454`~~  → 已处理（见 UC 批次17·下24）
  问题: onResume 每次 `pluginManager.refreshPlugins()`
  建议: 事件驱动
~~- **B-26** [坏味道|低] `SRC/MainActivity.kt:517-526,170-196`~~  → 已处理（见 UC 批次17·下23）
  问题: tab 状态在 Activity 与 Compose 各存一份
  建议: 单一状态源
~~- **B-27** [坏味道|低] `SRC/MainActivity.kt:468-472`~~  → 已处理（见 UC 批次17·下22）
  问题: 重写已废弃 `onBackPressed`
  建议: 迁 BackHandler
~~- **B-30** [坏味道|低] `SRC/SplashActivity.kt:116-122,246-262`~~  → 已处理（见 UC 批次17·下49）
  问题: 向自己声明的权限发运行时申请，结果只打日志
  建议: 明确用途或删除
~~- **B-35** [性能|低] `SRC/SplashActivity.kt:177-179,264-298`~~  → 已处理（见 UC 批次17·下49）
  问题: 主线程批量 mkdirs + Logger.init（首启最慢路径）
  建议: 移协程
~~- **B-36** [坏味道|低] `SRC/SplashActivity.kt:1041-1052` vs `data/local/PreferenceManager.kt:64-71`~~  → 已处理（见 UC 批次17·下5）
  问题: `KEY_IGNORE_VERSION` 两套读写实现（另有 `UpdateChecker.ignoreVersion()` 与 `IConfigRepository` 两个死包装）
  建议: 保留 PreferenceManager 一套
~~- **B-37** [硬编码|低] `SRC/SplashActivity.kt:65-66` vs `constants/AppConstants.kt:198`~~  → 已处理（见 UC 批次17·下5）
  问题: `SPLASH_DELAY` 两处不同值(700/1500)，常量零引用
  建议: 保留一处
~~- **B-38** [硬编码|低] `SRC/SplashActivity.kt:512,526,790-808`~~  → 已处理（见 UC 批次17·下6）
  问题: `contentDescription="App Icon"`、`Text("UIN Tool")` 未走资源；权限拒绝弹窗红色系写死（`0xFFD32F2F`/`0xFFFFEBEE`）不适配深色
  建议: `stringResource` / `colorScheme.error`
~~- **B-39** [坏味道|低] `SRC/SplashActivity.kt:81,153,196-199`~~  → 已处理（见 UC 批次17·下5）
  问题: `isCheckingPermission` 恒 false；`navigateToOnboardingOrMain` 空包装
  建议: 删除
~~- **B-41** [坏味道|低] `SRC/TrampolineActivity.kt:43-48`~~  → 已处理（见 UC 批次17·下5）
  问题: 固定 `postDelayed(50ms)` 再跳转，纯增时延
  建议: 直接 startActivity
~~- **B-42** [坏味道|低] `SRC/TrampolineActivity.kt:60`~~  → 已处理（见 UC 批次17·下5）
  问题: 伪造 `ACTION_MAIN+CATALOG_LAUNCHER` 导致 Main 误判"桌面启动"
  建议: 用专用 extra
~~- **B-51** [内存泄漏|低] `SRC/core/plugin/PluginWebChromeClient.kt:38-47`~~  → 已处理（见 UC 批次17·下46）
  问题: 用可能已销毁的 Activity 弹 Dialog；类未被使用
  建议: 随 B-43
~~- **B-55** [坏味道|低] `SRC/core/di/ServiceLocator.kt:51,54,56` + `AppContainer.kt:49-60`~~  → 已处理（见 UC 批次17·下50）
  问题: `getJavaToDexCompiler`/`getGitHubApiService`/`getMirrorManager` 零引用 → DI 三个 lazy 永不初始化
  建议: 清理死入口
~~- **B-59** [坏味道|低] `SRC/core/update/UpdateChecker.kt:300-312`~~  → 已处理（见 UC 批次17·下15）
  问题: 版本排序靠"code+1e9 上移"的 hack
  建议: 双字段比较 + 单测
~~- **B-63** [硬编码|低] `SRC/core/update/UpdateDownloader.kt:284-291`~~  → 已处理（见 UC 批次17·下5）
  问题: `String.format` 未指定 Locale；单位 B/KB/MB 写死
  建议: Locale + 资源化
~~- **B-66** [坏味道|低] `SRC/core/compiler/JavaToDexCompiler.kt:33-49,236-255,344-346`~~  → 已处理（见 UC 批次17·下21）
  问题: listener 不清理；`addDirToZip` 零引用；`getMainClass` 吞异常
  建议: 清理 + 记日志
~~- **B-67** [坏味道|低] `SRC/core/compiler/CompilerUtils.kt:10-66`~~  → 已处理（见 UC 批次17·下50）
  问题: 整个 object 零引用
  建议: 删除
~~- **B-69** [错误处理|低] `SRC/log/Logger.kt:84-86,104-106,123-125,238-240,246-248`~~  → 已处理（见 UC 批次11~16）
  问题: 多处 catch 完全静默
  建议: logcat 一次 + 计数
~~- **B-70** [bug|低] `SRC/log/Logger.kt:231-241,251-263`~~  → 已处理（见 UC 批次11~16）
  问题: `clearAllLogs()/clear()` 置 `isInitialized=false` 但 `logFile` 仍指已删文件 → 后续日志静默丢失
  建议: 清理后重建并恢复状态
~~- **B-72** [坏味道|低] `SRC/log/Logger.kt:266-268`~~  → 已处理（见 UC 批次11~16）
  问题: 文件末尾重复定义 `Long.formatFileSize()` 扩展（易递归误用）
  建议: 删除
~~- **B-75** [并发|低] `SRC/cache/DiskCache.kt:33-78,21,151-153`~~  → 已处理（见 UC 批次11~16）
  问题: 读写非原子（半截字节）；memoryCache 无失效联动
  建议: 临时文件 + rename
~~- **B-76** [坏味道|低] `SRC/constants/AppConstants.kt:171-189,198-201`~~  → 已处理（见 UC 批次17·下50）
  问题: 多个常量零引用；`MAX_PLUGIN_NAME_LENGTH` 与 `PLUGIN_MAX_NAME_LENGTH` 重复
  建议: 清理合并
~~- **B-77** [坏味道|低] `SRC/constants/AppConstants.kt:23-45`~~  → 已处理（见 UC 批次11~16）
  问题: `APP_VERSION` 懒加载依赖 getAppContext()，覆盖安装后需重启才准
  建议: 用 BuildConfig 兜底
~~- **C-08** [坏味道|低] `PluginBackendManager.kt:662-671`(函数体在)、`:592,:594`(注释/日志称已删)~~  → 已处理（见 UC 批次17·下50）
  问题: `probeRealTermux` **并未删除**，函数体完整存在但零调用；:594 的 perfLog 是伪指标
  建议: 删除或恢复调用
~~- **C-23** [bug|低] `BackendConfig.kt:134-139`(prefs) vs `SharedSupervisor.kt:97,:240-254`(文件) + `BackendSettingsScreen.kt:52,:263,:405`~~  → 已处理（见 UC 批次17·下2）
  问题: 保活状态**双源**：开关即时写文件、prefs 仅 Save 写入且仅 isReal → UI 显示与 supervisor 实际行为不一致
  建议: 统一到 prefs 并单向同步文件
~~- **C-27** [bug|低] `PluginJSInterface.kt:2648-2665`(jsString) vs `PluginHostActivity.kt:1486-1488`~~  → 已处理（见 UC 批次11~16）
  问题: 两套 JS 转义不一致：jsString 未转义 U+2028/U+2029 → 后端响应/文件内容含其一时 `evaluateJavascript` SyntaxError，回调永久丢失
  建议: 复用 `JSONObject.quote` + 补转义
~~- **C-30** [坏味道|低] `supervisor.sh:76-98,62-72,140-143`~~  → 已处理（见 UC 批次17·下3）
  问题: 启动失败无回传（cmd 拾取不记录退出码，靠下一轮 `kill -0` 清理）；`kill -0` 对僵尸返回成功且不 `wait` 回收 → 僵尸可能累积；宿主只能靠端口轮询得"未就绪"
  建议: `echo $? > $CTRL/done/$k.rc` 回传；显式 wait；宿主读 `.rc` 展示失败原因
~~- **C-31** [性能|低] `supervisor.sh:134-138` + `SharedSupervisor.kt:422-432` + `PluginBackendManager.kt:38-47`~~  → 已处理（见 UC 批次11~16）
  问题: 日志轮转不完整：`supervisor.log` 留 500 行、`backend_*.log` 仅按天删（单日无上限）、`perf_*.log` 按天堆积**无任何清理**
  建议: 统一按大小封顶(1MB×3)+超期清理
~~- **C-32** [坏味道|低] `supervisor.sh:92,105,132,152` + `PluginBackendManager.kt:647-656`(`cd $baseDir && exec $startCmd` 未引号) + `ProotContainerManager.kt:127-128`(`export $k=$v` 未引号)~~  → 已处理（见 UC 批次11~16）
  问题: 变量普遍不加引号，路径含空格即失效（当前受 isValidPluginId 约束，风险受控）
  建议: 关键变量统一 `"${var}"`，用 SharedSupervisor.kt:419 现成 `shQ()`
~~- **C-35** [坏味道|低] `TemplateUtils.kt:95-114`(`generatePluginJson`)~~  → 已处理（见 UC 批次17·下50）
  问题: 字符串拼 JSON 且 `"permissions": []` 硬编码，**全仓 0 调用**（实际走 PluginWizardViewModel.kt:297-330 的 JSONObject）→ 第 8 个死函数
  建议: 删除
~~- **C-36** [bug|低] `ASSETS/plugin_templates/backend/start.sh.tmpl:10-16`、`start_binary.sh.tmpl:10-15`~~  → 已处理（见 UC 批次11~16）
  问题: `apt install -y python3 2>/dev/null` 失败仅打印一行 `exit 1`，无 stdout 回传 → 宿主只见"后端未就绪"；二进制模板与 python 模板目录布局不一致
  建议: 失败输出到 stderr 供宿主采集；README 补布局说明
~~- **C-39** [并发|低] `PluginBackendManager.kt:61`、`:423-430`~~  → 已处理（见 UC 批次11~16）
  问题: 外层 ConcurrentHashMap 但内层 `MutableList` 注册/注销/回调无同步 → `ConcurrentModificationException`
  建议: 内层改 CopyOnWriteArrayList
~~- **C-40** [bug/安全|低] `PluginContext.kt:244,263`、`:130`、`:52-75`~~  → 已处理（见 UC 批次11~16）
  问题: 磁盘预检用 `content.length()` 当字节数（低估 UTF-8）；插件 KV 值 `value.take(50)` 落宿主日志（隐私）；资源加载失败静默降级
  建议: `toByteArray().size`；日志只打 key
~~- **C-41** [坏味道|低] `PluginJSInterface.kt:1521-1534` vs `PluginContext.kt:205-225`~~  → 已处理（见 UC 批次11~16）
  问题: `getSafeFile` 两份重复实现
  建议: 收敛为 `PluginContext.resolveSafe()`
~~- **C-42** [坏味道|低] `ASSETS/test_plugins/**`(8 tpk 约123KB)~~  → 已处理（见 UC 批次17·下24；下61 用户拍板回 main/assets（双构建内置便于借鉴），debug 源集删除）
  问题: 仅被 `DevScreen.kt:310-316`（导出模板）引用却随正式包分发
  建议: 移到 debug sourceSet
~~- **C-44** [坏味道|低] `PluginJSInterface.kt:2540-2612`（缩进脱离类体）、`:2535-2537`~~  → 已处理（见 UC 批次11~16）
  问题: 末段 ping/DNS 函数顶格缩进错乱；`addEventListener` 只打日志，插件以为注册成功实际收不到事件
  建议: 格式化；空实现返回 permissionDeniedJson
~~- **D-30** [坏味道/安全|低] `SRC/data/remote/NetworkClient.kt:9-38`~~  → 已处理（见 UC 批次17·下50）
  问题: 全仓零引用的死代码，却含第二套 Retrofit/Gson 栈与 `HttpLoggingInterceptor`（会打印请求行）
  建议: 删除
~~- **D-35** [国际化|低] `BackupRepositoryImpl.kt:67,69`、`MirrorManager.kt:134`、`ProotInstaller.kt:30-80`、`RootfsManager.kt:97-257` + `TERM/TerminalSession.java:353-361`~~  → 已处理（见 UC 批次17·下59：9 处中文异常文案固定英文；3 处引用过期自愈，TerminalSession 已不存在）
  问题: 日志文案一部分走 `Str.get`（随语言变化无法检索）一部分硬编码中文；`[Process completed - press Enter]` 硬编码英文
  建议: 日志统一固定英文/结构化 key
~~- **D-36** [死代码|低] `SRC/proot/RootfsSetup.kt:1-105`(整文件死)、`ContainerConfig.kt:12-13,17-20`~~  → 已处理（见 UC 批次17·下50）
  问题: RootfsSetup 无调用方（且是 1777/0777 冲突源）；`ContainerConfig.isReady` 无人使用且只判 `bin/sh` 存在；`default()` 硬编码 debian 与 BackendConfig 重复；`environmentVars` 被 ProotRuntime 忽略
  建议: 删除死代码
~~- **D-37** [体验|低] `SRC/proot/FakeSysdata.kt:21-37,96` + `ProotRuntime.kt:27`~~  → 已处理（见 UC 批次11~16）
  问题: 伪造固定 8 核 CPU、内核 6.2.1，与 `--kernel-release`(6.17.0) 矛盾；`nproc`=8 在 4 核真机致 `make -j` 过度并行
  建议: 按 `availableProcessors()` 生成，统一内核串
~~- **D-38** [安全|低] `SRC/proot/RootfsManager.kt:399-402`~~  → 已处理（见 UC 批次11~16）
  问题: 路径穿越防护用 `startsWith(rootCanonical.path)` 未加分隔符（`/a/rootfs` 放行 `/a/rootfs_evil`）；因入口已 normalize 暂不可利用
  建议: 加 `File.separator` 或 Path.startsWith
~~- **D-39** [bug|低] `SRC/proot/ProotInstaller.kt:84-93`~~  → 已处理（见 UC 批次11~16）
  问题: 未知 ABI 兜底返回 `aarch64`(:91)，x86 设备装错二进制
  建议: 明确返回"不支持"
~~- **D-40** [bug|低] `SRC/domain/model/PluginInfo.kt:48,68` vs `:268-323,355-417`（消费方 `PluginBackendManager.kt:737`）~~  → 已处理（见 UC 批次11~16）
  问题: `backendEnv`、`frontendConfig` **既不解析也不序列化** → `info.backendEnv` 恒为空，插件声明的后端环境变量永久失效
  建议: fromJson/toJson 补两字段
~~- **D-41** [国际化|低] `SRC/domain/model/PluginInfo.kt:24,370,281`~~  → 已处理（见 UC 批次17·下2）
  问题: `category` 默认值用本地化串 `Str.get(R.string.uncategorized)` 并被 `toJson` 持久化 → 切换语言后分类不一致，UI 文案混入数据
  建议: 存稳定 key，展示时映射
~~- **D-42** [坏味道|低] `PluginInfo.kt:80-83,25,282`、`SourceInfo.kt:8-16`、`RepoPluginInfo.kt:10-29`~~  → 已处理（见 UC 批次17·下63：①RepoPluginInfo 派生态外移 InstalledMeta、③signature 死字段删除〔真校验=SHA-256 文件钉扎〕、②框架映射经用户拍板登记不动）
  问题: 运行时状态(isBackendRunning/pid…)混入 domain model；模型大量 var + 手工 `as?` 逐字段取值；`signature` 由 plugin.json 自带并回写（**自证签名**）
  建议: 状态外移；改 data class + 框架映射并校验
~~- **D-43** [一致性|低] `SRC/domain/repository/IConfigRepository.kt:13-69`~~  → 已处理（见 UC 批次17·下54 整层删除）
  问题: 同接口里 getter 返回 StateFlow、其余同步、setter 却 suspend → 抽象不对称
  建议: 统一 Flow 读 + 普通写
~~- **D-44** [坏味道|低] `SRC/proot/RootfsManager.kt:30,547`~~  → 已处理（见 UC 批次11~16）
  问题: `installingLocks` 永不清理；`"%.1f MB".format` 未指定 Locale（德语输出逗号）
  建议: 用完移除；Locale.US
~~- **E-20** [主题|低] `ui/screen/help/HelpScreen.kt:60-74,107`~~  → 已处理（判已解决：DayNight+values-night 全覆盖、深色链路由下34/E-14 闭环，硬编码文案随批次2 消失，见 UC 批次17·下62）
  问题: 独立 Activity 主题下深色兼容未知（同 E-09 分裂），文案硬编码
  建议: 随 E-19 处理
~~- **E-22** [性能|低] `utils/` 目录~~  → 已处理（见 UC 批次11~16）
  问题: `Extensions.kt:55` `Looper.myLooper()==Looper.getMainLooper()` 判断外直接 Toast；`FileUtils.kt` 未接；多处 `formatFileSize` 重复实现（含 B-72）
  建议: 统一收敛 IO/格式化工具
~~- **E-26** [死代码候选|低] `ui/theme/Theme.kt`、`Color.kt`、`Shape.kt` 中仅供 Preview 的部分~~  → 已处理（见 UC 批次17·下56 判前提过期（theme 三文件 0 个 @Preview））
  问题: `@Preview` 按源码读是可达，但 release 不编译 → Preview 无运行价值
  建议: 可保留少量作为文档；成批的死 preview 随文件删除
~~- **F-11** [打包|低] `ui/screen/dev/DevScreen.kt:310-316` + `C-42`~~  → 已处理（见 UC 批次17·下24）
  问题: 导出模板依赖 `ASSETS/test_plugins/**`（随正式包 123KB）
  建议: debug sourceSet
~~- **F-17** [可访问性|低] `ui/screen/manage/`（tab 结构）~~  → 已处理（见 UC 批次17·下25）
  问题: tab/列表项触控区、选中态语义弱（同 E-07）；分隔线硬编码浅色
  建议: semantics + colorScheme
~~- **G-28** [行为|低] `widget/WidgetProvider.kt`（刷新间隔/后台刷新）~~  → 已处理（见 UC 批次17·下39）
  问题: 刷新依赖广播轮询，Doze/厂商杀后台时不更新
  建议: `setExactAlarm`/WorkManager + 用户开关
~~- **G-29** [资源|低] `widget/` 与 `RES/layout/widget_*`（= A-32/A-33）~~  → 已处理（见 UC 批次17·下51）
  问题: 布局/dimens/string 有一半零引用（`widget_info.xml` 孤儿副本）
  建议: 随资源清理
~~- **A-31** [正面|—] `RES/values/strings.xml` vs `values-zh-rCN/strings.xml`~~  → 已处理（见 UC 批次17·下27 CI 卡点）
  问题: **键集合完全对齐**（2364/2364、0 重复、占位符差异 0），可作为 6.1.0 质量基线
  建议: CI 加自动比对脚本防回归
~~- **A-50b** [保留|—] `ASSETS/compiler/host-sdk.jar`~~  → 已处理（既定保留）
  问题: 与 `app/libs/host-sdk.jar` MD5 相同、代码无读取；**用户已明确：保留，不删除**（本文件所有清理任务均不涉及它）
  建议: 不动
~~- **C-10** [安全|—] `PluginJSInterface.kt:210`(callHost)、`:2366-2381`(openUrl)、`:633`(getLocation)、`:272`(getSerialNumber)、`:286`(getMacAddress)~~  → 已处理（见 UC 批次17·下47 判已审计关闭）
  问题: 关键 JS 桥入口锚点（权限矩阵见 4.3）
  建议: 审计基线
~~- **C-28** [bug|\] `ASSETS/supervisor/supervisor.sh:50-52`~~  → 已处理（见 UC 批次17·上）
  问题: `h=$(stat -c %Y ... \
  建议: echo 0)`：stat 不可用时 h=0 → `n-0 >= HOST_ALIVE_TIMEOUT` 恒真 → **误判宿主死亡，杀光所有后端并自杀**（fail-closed 无回退）
~~- **G-13** [docs|发现性] `ui/screen/help~~  → 已处理（见 UC 批次17·下29）
  问题: log` 的隐藏性
  建议: 日志/文档入口埋在 manage tab 深处，帮助=失败页 → 用户遇到问题无自救路径
