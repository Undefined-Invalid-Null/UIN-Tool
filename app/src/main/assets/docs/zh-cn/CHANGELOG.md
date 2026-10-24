# UIN Tool 更新日志

本文档记录了 UIN Tool 的所有重要版本更新和功能变化。

---

## 版本命名规则

- **主版本号**：重大架构变更或不兼容的 API 修改
- **次版本号**：新增功能，向下兼容
- **修订号**：问题修复，向下兼容

---

## [6.2.0] - 2026-10-16

> 代码编辑器专项：把原本只在插件创建向导内可用的纯内存草稿框，重写为可独立对外调用的完整代码编辑器 —— 完整文件树 + 多标签 + 项目级打开 + 直接磁盘读写 + `ACTION_VIEW` 对外入口，外加两轮 Sora Editor 能力审计与 Unified 组件体系的界面统一，并修复了四轮「语法高亮整体消失」的深层根因。新增功能、界面美化、调用入口三项齐做。

### 对外入口与文件访问

- `CodeEditorActivity` 在 Manifest 正式 `exported=true` 并声明独立的 `ACTION_VIEW` filter（`text/*` 与 `application/json|xml|javascript|x-sh`），其他应用可通过「打开方式」直接调起编辑器（已知代价：系统选择器会出现两个 UIN Tool 图标）
- 文件访问改为**默认申请权限 + 直接文件访问**：进入编辑器发起存储权限申请，获批后直接用 `java.io.File` 读写；`content://` 保留 `ContentResolver` 作为解析不出真实路径时的兜底，不再是并列主路径
- Android 11+ 走 `MANAGE_EXTERNAL_STORAGE`（判定 + 跳系统设置页），Android 10 及以下走 `READ/WRITE_EXTERNAL_STORAGE`
- 新增 `ACTION_OPEN_DOCUMENT_TREE`，可手动指定项目根目录；打开新文件夹时同步建立文件树
- **修「选完文件夹毫无反应」**：`OpenDocumentTree` 返回的是 **tree URI**（`…/tree/primary%3AMine`），而 `resolveRealPath` 只认 `isDocumentUri()` 的普通文档 URI —— tree URI 上该判定恒为 false，于是永远走不到 docId 解析，只能落到注定失败的 `_data` 列查询，返回 null 后调用方静默 `return`。现对 tree URI 改取 `getTreeDocumentId()` 并复用同一套 `primary:` / `UUID:` 映射；仍解析不出时给出**明确失败提示**，不再无声返回
- **读取前内存预检**：原实现只靠 `catch (OutOfMemoryError)` 兜底，但真正 OOM 时 ART 已进入「反复 blocking GC / 强制回收 SoftReferences」状态（设备日志实测主线程停摆 12 秒以上），表现为**整个界面卡死**。现按「峰值 3.25 倍（byte[] 1 倍 + UTF-16 解码 2 倍 + 25% 余量）」在读取前预检，装不下直接给「文件过大，内存不足」的明确文案。该阈值随机器剩余内存浮动，**不是**新的固定大小硬上限；25% 余量是设备实测所需 —— 31.4MB 文件申请 62.8MB 的 char[] 时距堆上限只差 6MB，理论 3 倍零余量会在预检通过后被并发分配挤到 OOM
- **修「同一个文件有时打不开、有时又能打开」**：预检读的是 `freeMemory`（**已提交堆**里的空闲量），刚关掉一个大文档时那段内存还没回收，available 偏小 → 被误判装不下。设备日志实测：同一 29.9MB 文件 25 秒前刚打开成功，关掉再开就被挡住（available=88MB，阈值=29MB）；而用「打开方式」进来是冷启动、堆是空的，同一个文件又打得开 —— 表现成「目录树里打不开、外部打开却可以」。现改为先 `System.gc()` 再量一次，仍装不下才判定失败（只在读之前触发；字节已在手上时 GC 也回收不掉，不做无用功）。**批次41 再修一层「gc 完就立刻量」**：`System.gc()` 触发的是并发 GC，常在收尾完成前就返回，紧接着测量会**低估 available 10~48MB** —— 2026-10-17 实测同一 31MB 文件在 21:58:06 / 21:58:36 被连判两次「文件过大」，而 40 秒后堆真正回收完、同一文件又打得开。现改为 gc 后每 80ms 复测一次、最多等 320ms 让收尾完成再判定
- **进程重建后恢复上次打开的文档**：新增 `last_open_file` / `last_open_uri` 持久化，从多任务窗口回来（全新 Activity + 空 Intent，既无 `ACTION_VIEW` 也无 `project_dir`）时自动重开，不再落进「未打开文件」空态、看着像内容全没了。插件向导模式同样在进程重建后重新打开整套草稿；顺带修掉 `projectDirPath` 被 `fromRestore` 跳过导致退出时回传 `RESULT_CANCELED`、向导读不回目录的问题。**恢复失败时把原因写进空态常驻显示**（文件已删除 / 内存不足 / 分享方授权已回收），不再是笼统的「未打开文件」；进程重建后重投的 `ACTION_VIEW` 或向导草稿读不出来时，也回落到上次文档而不是停在空态
- **`singleTop` 复用编辑器实例，修「反复打开同一文件被判文件过大 / 越开越卡」**：`CodeEditorActivity` 原为 `standard`，文件管理器的 `VIEW` 自带 `CLEAR_TOP` → 每次「打开方式」都**销毁重建**：新实例拿不到旧标签页，同一文件在堆里还占着旧副本时被迫二次读取。设备日志实测：打开成功后 25 秒再开即失败 —— 堆已 451MB/475MB，申请 62.8MB char[] 距上限仅差 6MB，或预检因旧副本未回收而判「文件过大」。改 `singleTop` 后 `onNewIntent` 复用本实例，查重直接切回已打开的标签，**同一文件的重复打开零额外内存**；`onNewIntent` 同步 `setIntent()`，保证进程重建时投递的是最新 Intent
- 插件向导改为先落盘草稿到 `cacheDir/editor_draft/<ts>/`、Intent 只传 `project_dir` 字符串，彻底规避 `TransactionTooLargeException`、废弃的 `getSerializableExtra` 与进程重建丢稿
- **长按顶栏保存图标 = 系统「另存为」**（`ActivityResultContracts.CreateDocument`）：可同时另指定**文件名与位置**；原「写入失败才弹目录选择器」的兜底另存为保持不变

### 架构重写

- 新增 `EditorModels` / `EditorViewModel` / `FileAccess` / `EditorSettingsStore` 四层；`CodeEditorActivity` 43 → 289 行，`CodeEditorScreen` 1118 → 2000+ 行
- 多标签：`EditorTab` 以 `savedContent` / `content` 对比得出 `isModified`，取代旧版「`hasChanges` 只在 `SET_NEW_TEXT` 时置 true」的失效逻辑（打字不触发、未保存标记恒不显示）
- 文件树：递归收集全部文本文件；支持新建文件 / 新建文件夹（支持 `a/b` 嵌套路径）、重命名、删除；目录可展开折叠
- 桥接层 `EditorController` 改用 `subscribeEvent(ContentChangeEvent / SelectionChangeEvent)` 驱动，取代旧版 40×50ms 轮询 hack
- 设置应用做「已应用设置三元组去重」，避免每次按键触发的重组都重设编辑器属性引发 relayout

### 编辑功能

- 查找 / 替换：默认只显示搜索框，展开箭头才出替换框；结果计数改订阅 `PublishSearchResultEvent`（原实现在异步 `search()` 返回后立刻读数，恒显示 0 / 旧值），正则模式补 `RegexBackrefGrammar.DEFAULT` 让 `$1` 反向引用真正生效
- 保存语义：保存 / 另存为 / 未保存退出确认，退出前提示未保存改动（原版无 `BackHandler`，返回即静默丢稿）
- 状态栏实时显示「行:列 / 字符数」
- 硬件键盘快捷键：Ctrl+S 保存、Ctrl+F 搜索、Ctrl+G 跳转行、Ctrl+Shift+Z 重做（内建 Ctrl+Z 分支不看 Shift，原本会把 redo 变回 undo）；其余按键一律放行不抢
- ⋮ 菜单补全：撤销 / 重做（按 `canUndo()` / `canRedo()` 置灰）、缩进 / 反缩进、剪切整行、选中整行、跳转行、跳到文首 / 文末、格式化、全选
- 符号输入栏 `SymbolInputView`，插入改走 `commitText()` 正门（`(` 才会自动补 `)`、才会走自动缩进与引号处理）
- 设置面板 28 项并持久化：字号、tab 宽度、自动换行、行号、行号分隔线、非打印字符、括号匹配、缩进参考线、行列面板停靠位置、连字、光标闪烁、选中弹出窗、放大镜、双指缩放、行距倍数、字宽、符号栏、新行换行符（LF / CRLF / CR）、高亮代码块、函数占位字符、大文件省耗、文件树多行显示等
- 编辑器主题独立于应用主题（默认 dracula，9 种 Sora 主题可选），不再跟随系统（系统未定义编辑器主题）
- 代码格式化：新增 `CodeFormatters`，**JSON（json / json5 / jsonc）逐 token 保值重排**（只重算缩进与冒号后空格，数值、字符串字面量、转义写法、键顺序、JSON5 单引号与尾逗号全部原样搬运，同时做结构校验、不合法一律返回 null 不动原文档），**YAML 保守整理**（块标量内逐字节保留、行首 tab 展开为空格、去行尾空白、折叠连续空行，不重排缩进层级）；后台线程计算后经 `onFormatSucceed()` 应用（自带批编辑 = 整份替换合成一步撤销），切标签与期间打字都会丢弃过期结果；不支持的扩展名菜单直接置灰
- 点击代码中的 `#RRGGBB` 色值即可复制到剪贴板并选中该区间
- **修「大文件打开后卡到没法输入、GC 连轴转、切标签 OOM 崩溃、回来恢复被判文件过大」（批次41）**：十六进制色值背景扫描此前对 200 万行文件**全量跑正则**，单轮 22~64 秒、每秒约 45MB 正则中间对象，设备日志实测 GC 每 0.5~1.5 秒一轮、堆长期顶在 400MB（界面卡顿、输入无响应），且扫描线程在编辑器销毁后仍持有整份旧文档 —— 多任务回来重建时旧内容钉在堆里，恢复打开被内存预检连判两次「文件过大」（21:58:06 / 21:58:36 实测）。现改为：无 `#` 的行先做**零分配预筛**直接跳过（同文件实测从 22~64 秒降到亚秒级）、每 4096 行检查取消标志（`detach()` 即刻中止扫描、不再钉住旧文档）、单文件最多 **5 万处**色值背景即停（顺带兜住「每行都是 # 注释」的文件）；另外**切回大标签页重建文档前显式等一次回收**，避免新旧两份大文档双份驻留把堆顶穿（21:59:52 OutOfMemoryError 崩溃对应）
- **修「文件名过长时行尾 ⋮ 显示不出来」，目录树补长按菜单与多行显示开关（批次42）**：行内文件名 `Text` 未加 `weight(1f)`，按**整行宽度**测量 —— 省略号要到整行宽度才触发，长名直接把 ⋮ 顶出可视区。现文本约束到剩余空间、超长裁切省略号（原「Spacer 把 ⋮ 推到行尾」的定位作用由该 weight 一并承担），⋮ 恒可见。新增编辑器设置「**文件树多行显示**」（默认关 = 单行裁切；开 = 名称换多行、行高自适应）；**长按**文件 / 文件夹行 = 点 ⋮ 弹出同一菜单（重命名 / 删除 / 属性），菜单锚定在 ⋮ 处
- **顶栏 / 标签栏 / 底部栏整轮重排：信息栏 + 分页导航 + 属性增强（批次43）**：顶栏**不再显示文件名**（标签栏已有），右侧新增**撤销 / 重做**（按 `canUndo()/canRedo()` 置灰，光标与内容回调 bump `historyTick` 刷新）；标签栏 38 → 44dp、关闭图标 12 → 16dp，点 × 不再误触相邻标签，**长按标签直接打开完整属性**；标签栏下方新增**信息栏** —— `n/N行`、`n字节`、`n字符`、`选中n个` 随光标与编辑实时刷新（uri/未落盘标签字节显示 —）。底部状态栏重排：**分页切换 `n/N`** 置于左下（仅多页文档显示；点击带涟漪，下拉框顶部为整数输入框、只许数字且必须落在 1~N，回车或点列表即跳页 —— 每页 2000 行，光标在哪页就显示哪页），右侧为匹配数、只读徽标、**编码按钮**、⚙ 设置（固定最右，批次44 定稿）；**移除底部自动换行图标**（开关保留在编辑器设置里），行:列与选中数移入信息栏。**属性弹窗**（文件树 ⋮ 与长按标签共用）新增**行数 / 字节 / 字符数** —— IO 线程流式统计、永不整读进内存（>128MB 只报字节数），且**每行可点击（涟漪）一键复制该值**
- **修「点分页即崩」，底部/顶栏按反馈微调，属性连点复制的提示改为最新一条顶上（批次44）**：分页下拉的页码列表用了 LazyColumn —— DropdownMenu 弹层按**固有尺寸(intrinsics)**测量内容，而 LazyColumn 属 SubcomposeLayout、不支持 intrinsics，点开即 `IllegalStateException`（crash_2026-10-18.log 11:43:45 实测，栈穿 AndroidPopup → Scroll → Lazy）。改用普通 Column + 限高 220dp + 竖向滚动（行组件均为常规布局，弹层测得了）。底部栏按反馈再调：**分页单独在左下，编码按钮与 ⚙ 设置挪回右侧、设置固定最右**。顶栏：**撤销/重做移到动作区所有图标最左侧**，并收紧为 40dp（其余图标 48dp 不变）。属性弹窗连续点多行复制时「已复制到剪贴板」不再隔很久逐条冒出 —— Snackbar 本是串行队列，现改为**先撤正在显示的、最新一条立即顶上**（`AppToast.show`）
- **顶栏「…」省略号之谜移除、六图标收紧，编码完整显示，分页输入框改同搜索框，通知堆积审计全清（批次45）**：用户反馈“顶栏最左侧三个横点、点不动” —— 实为标题「代码编辑器」被右侧图标挤到零宽后省略成的**纯文本「…」**（不是控件），按反馈**整个移除标题**（文件名本就在标签栏）；顶栏撤销/重做/保存/查找/语法/⋮ 六个图标统一 **40dp**，图标间视觉空隙 24 → 16dp。底部**编码按钮去掉 90dp 宽度上限**，完整显示「自动检测·GB18030」等全称。分页下拉的页码输入改用**搜索框同款 `UnifiedTextField`**（原生 m3 TextField 在弹层里光标水滴定位跑偏），`UnifiedTextField` 新增可选 `keyboardOptions/keyboardActions` 透传参数，越界页码以新增文案「页码须在 1..N 之内」标红。**通知堆积全库审计**（子 agent 扫描）：插件 JS 桥 `toast/toastLong/showLoading` 三处裸 `Toast`、十六进制色值点击复制的裸 `Toast` 全部改走 AppToast；插件 `sendNotification` 的 ID 由时间戳改为**按插件固定 ID**，连发原位替换、状态栏不再堆积；其余 ~100 处走 AppToast、编辑器事件流 latest-wins、下载通知固定 ID —— 均确认无队列问题
- **超大文件不再拒开：分页窗口模式（EmEditor 式磁盘溢写，批次46）**：内存预检失败（整文件装不下堆，含「多任务回来重读 OOM」的真实事故）的文件不再报「文件过大」，自动降级为**分页窗口模式**：打开时流式扫一遍建「每页首字节偏移 + 总行数」的**小索引**（10GB 级仅几十 KB，字节级扫描免解码 —— 白名单编码的行终止符 0x0A/0x0D 不可能落在多字节序列内，UTF-16/32 按端序对齐单元判定），编辑器每次只载入**当前页（2000 行）**，整档不进堆。信息栏带**「窗口模式」徽标**、行号换算为**绝对行**（编辑器行号栏为页内相对行号，Help 18.12 有说明）；**分页控件变成真翻页**（换页先把当前页改动落 spool 再读新页，`contentRev` 版本号触发 sora 重设文本）；「跳转到行」按全文绝对行号自动切页定位。**保存 = 原文件 + 改动页**流式拼接写临时文件后**原子替换**，未改动页按原样字节拷贝（不经过解码，原字节分毫不动），保存后重建页索引；保存在**裸后台线程**进行，退出编辑器不中断（「保存并退出」不丢写）。配套：窗口标签强制大文件省耗（关语法/缩进线）、换编码即重建索引回第 1 页、另存为显式拦截（防只写出当前页的静默截断）、索引重建失败锁只读防按废偏移再写。整读快路径与守卫逻辑完全不动 —— 装得下的文件照旧整读

- **窗口模式补上整档搜索/替换：「全文」流式引擎（批次47）**：分页大文件此前只能搜当前页（sora 检索器以整档文档为前提），批次47 新增 `WholeSearch` 流式引擎补齐整档能力：搜索面板在窗口模式下多一个**「全文」开关**，打开后逐页扫描（供页顺序「内存改动页 → 磁盘 spool → 原盘」，未保存的改动对全文立即可见），450ms 防抖、进度实时显示、随时可取消（每页检查协程取消位）；结果按 `绝对行:列` 列表展示（最多列 500 条、总数照常全量统计），**点按结果自动切页并选中高亮匹配文本**（`pendingSelection` 升级为带长度的 `PendingSel`，跨页落地后 4 参 `setSelection` 高亮区间）。替换为**「全部替换（全文）」**（整档语义下没有「当前匹配」）：逐页改写进**磁盘 spool**（`cacheDir/uin_spool/`，内存恒定 = 一页 —— GB 级文件替换高频词也不会把堆撑爆），保存时由写回供给器与原文件流式拼接落盘，保存/丢弃/关标签自动清理 spool；替换与保存/翻页**互斥**（三者都触碰页供给与 spool 簿记），扫描基于「用户看到的文本」—— 手改页先迁进 spool 再替换，且 spool 版本撤掉旧内存 overlay、绝不被遮蔽。跨 2000 行页界的匹配向后重叠一页**识别并跳过不替**（结果里上报「另有 N 处跨页匹配未替换」），绝不产生跨页缝的半截改写；正则替换沿用 `$1` 反向引用语义。替换全程经写回缓冲，**保存后才真正落盘**，结果列表在替换完成后自动重跑刷新。整读模式不显示该开关 —— sora 本就搜整档，无需二次实现
- **文件编码：自动检测 + 可手动选择**。底部状态栏新增**编码按钮**（直接显示当前编码，自动档显示「自动检测·UTF-8」这类形式），点击弹出选择器；6.2.0 批次27 应用户要求把可选列表**扩充为按本机能力过滤的常用全集**（UTF-8 / GB18030 / GBK / Big5 / Big5-HKSCS / UTF-16、UTF-32 各变体 / Shift_JIS / EUC-JP / EUC-KR / ISO-2022-JP / ISO-8859-x / windows-125x / KOI8-R / TIS-620 等），`Charset.isSupported` 逐项过滤 —— 设备解不了的编码不会出现在列表里，杜绝「选了解不动、静默回退 UTF-8」。判定顺序为 **BOM → 严格 UTF-8 校验 → GBK/GB18030 启发式 → ISO-8859-1 兜底**；此前一律按 UTF-8 解码，GBK 老文件读出来是 `锟斤拷` 类乱码且无从改正。选手动编码会**立即按该编码重新读取本文档**（基线一并更新）。保存时**按打开时判定的编码原样回写**，不会把 GBK 文件悄悄存成 UTF-8

### 语法高亮根因修复

- **扩展名 → scope 映射**：7 个映射与语法文件实际声明不符（`m/mm`、`c` 被误归 `source.cpp` 等），未注册 scope 改为回退 `text.plain` 而非沿用上一个文件的语言；`.tsx` / `.jsx` 高亮失效一并修复
- **每目录只注册第一个语法**：`GrammarDefinition.getName()` 返回目录名，`GrammarRegistry` 用它做去重键 → 同目录下只有第一个语法能注册。改为用语法文件完整路径作全局唯一名称
- **共享 raw 规则表被写死**：`Grammar` 构造时 `ObjectCloner.deepClone` 末分支从不真正克隆普通对象，同一 scope 的 raw 被 registry 与所有 `include` 它的语法共享；先编译者把 rule id 写死在共享对象上，后编译实例 `getRule(id)` 抛异常杀死分析线程。改为切换语言时反射重新解析出全新对象图并逐出缓存；同时移除会在后台线程并发编译同一实例的 `testTokenization`
- **语法资产 captures 结构损坏**：XML / YAML 4 个语法资产中 `captures` 键下混入裸字符串，tm4e 编译期抛 `ClassCastException`，`registerRule` 在写入映射表之前就异常 → 该 id 永久留在共享 raw 却查不到 → 分析线程死亡、完全无高亮。修复 4 个资产，并在 `loadGrammars()` 加入**加载期校验器**，以后同类损坏一启动就能在日志里看到文件名与具体路径
- **上游下划线 token 崩溃**：sora 0.24.4 的 `TextMateAnalyzer` 对所有 token 都取 `obtainNoExt` span，随后又调 `setUnderlineColor` → 遇到带下划线的 token（HTML 会产出）`NoExtSpanImpl` 直接抛 `UnsupportedOperationException`，整条 `AsyncAnalysis` 线程死亡、该文件高亮全灭。上游 0.24.5 修复，本版随 sora 升到 0.24.6 一并解决
- TextMate 主题色修正：sora 用 `>=255` 自定义色号，普通 `EditorColorScheme.getColor` 查不到返回 0 → 透明；`TEXT_SELECTED` / `TEXT_ACTION_WINDOW_*` 同理，逐一补色，修掉「页面空白、全选才看见字」「全选改字色」「浮层图标透明」
- 语法加载改读语法文件**自身声明**的 `scopeName`，跳过 injection 与损坏资产；清理两个内容为字面 `404: Not Found` 的 14 字节残壳语法文件

### 界面美化

- 全量改用 Unified 组件体系（`UnifiedDialog` / `UnifiedTextField` / `UnifiedEmptyState` / `UnifiedCard` 等），新增 `AppColors` / `AppDimens`
- 侧栏改 `ModalNavigationDrawer`：左上角换抽屉开关（原为 ArrowBack 退出箭头），退出交给返回键；左缘锚定，最左侧滑动打开、主体区缩放不触发，打开后拖抽屉关闭
- 目录树文件名 `bodyMedium`、行高 34 → 40dp；⋮ 菜单由近似全屏 Dialog 改为锚定按钮旁的 170dp 弹层
- 编辑器 chrome（标签栏 / 状态栏 / 符号栏 / 搜索面板）统一用 `pageBackground()`，渐变与玻璃模式下透出、与应用整体一致；模态浮层（弹窗 / 底部弹层 / 抽屉）保持不透明，修复编辑器内弹窗透到下层的问题
- 修复 UI 面板与按钮过透明（`translucentAlpha` 默认值与下限、`glassBorder` 恒透明）
- 无障碍：`AndroidView` 补 `contentDescription`，TalkBack 能读出当前编辑的文件名
- 设置面板由底部 `ModalBottomSheet` 改为**全屏设置页**（顶部固定「返回 + 标题」栏 + 正文滚动），28 项不再挤在弹层里
- 目录树 ⋮ 菜单新增「**属性**」：弹窗展示名称 / 路径 / 类型 / 大小 / 修改时间（只读）
- **符号栏加高**：每个符号装进固定 44×44dp 触控区（原为约 30dp 宽的裸文本点击区），字号升到 `bodyLarge`
- 标签栏点击去掉水波纹（选中态已有底色，涟漪在 38dp 窄条上反而糊住文件名）
- **行号分隔线可见**：TextMate 主题把 `LINE_DIVIDER` 置为 TRANSPARENT，导致该开关怎么拨都看不见；改用行号色半透明版重设
- **括号匹配改为可见的细描边（修「括号匹配无效果」）**：描边宽度取自 `textBorderWidth`，原先被设成 4dp（正文内边距），粗得喧宾夺主。上一版把 `HIGHLIGHTED_DELIMITERS_BORDER` 置 0 想「变细」，但 sora 的 `drawRowBackgroundRectWithBorder` 在 `borderColor == 0` 时**连描边一起 return**，只剩 sora 默认 11% 黑的背景高亮 → 观感就是「开了没反应」。现**恢复可见描边色**（优先主题给出的括号前景色，否则用当前行号色）并把 `textBorderWidth` 收到 **1.5dp**（sora 默认 1dp），描边明显变细但仍看得见。十六进制色块维持无描边设计
- **行列面板位置真正生效**：此前只调 `setDisplayLnPanel`、从未把 `lnPanelPosition` 应用到编辑器（改了没反应）；6.1 又在 `applyRuntimeSettings` 后半段**重复赋值**一次，把左/右之外的档位切回 `FOLLOW`（永远贴右侧），前一次设置被覆盖。现删除重复赋值，全程 `LineInfoPanelPositionMode.FIXED`，五个方位各自固定停靠。**需说明**：该「行:列」气泡本身只在**拖动右侧竖向滚动条**期间出现（sora 的 `drawLineInfoPanel` 被 `holdVerticalScrollBar()` 门控，且没有公开 API 强制常显），这是上游既有行为、不在本版改动范围
- **系统返回键逐层收起覆盖层**：原 `BackHandler` 只有「关抽屉 / 未保存确认 / 退出」三档，设置页或查找面板打开时按返回会**直接退出编辑器**。现按 设置页 → 查找面板 → 编码选择器 → 目录树抽屉 → 未保存确认 → 退出 的顺序逐层收起
- 横向滚动条滑块补圆角药丸，与纵向一致（此前横向是方角实心块）
- **底部状态栏瘦身**：去掉重复的文件名（顶栏 / 标签栏已经显示）；布局改为**左区独占 `weight(1f)` 可伸缩、右区（匹配数 / 编码 / 换行 / 设置）固定不参与压缩**，匹配数与编码各自封顶宽度加省略号 —— 6.1 的旧布局里文件名、匹配数、编码标签依次抢占宽度，窄屏下会把 ⚙ 设置图标顶出屏幕

### 性能与资源

- **大文件省耗（新设置，默认开）**：内容超过约 **200 万字符**时自动关掉语法高亮、粘性滚动与块缩进线，并把十六进制色提示限制在前 1 万行内，滚动不再卡顿；**手动选定语法仍保留高亮**（用户明确要求优先于自动省耗）。**关掉该开关 → 上述全部措施一并恢复**（所有大文件损耗都可由用户关闭）。**同批移除「超 32MB 强制只读」**：内存装得下的文件一律可编辑（装不下由内存预检给「文件过大」明确文案）；只读标记改为每次打开文档归零 —— 旧实现只在另存为时复位，打开过一个超大文件后，**之后打开任何文件都保持只读**（用户反馈「大文件不可编辑」的根因之一）
- 十六进制颜色扫描移出主线程：原在 ContentChange 后于主线程跑完整段正则（大文件表现为「打完一行字就顿一下」），改后台线程扫描 + 防重入 + 过期结果丢弃后重排
- `isModified` 由每键 O(n) 全文比较改为 O(1) 布尔标记
- `EditorController.detach()` 现在调用 `CodeEditor.release()`（此前只置空字段）—— 修复每次 AndroidView 重建都泄漏一个 `TextMateLanguage` 后台分析线程与 `Formatter`
- 读文件改为 `ReadResult` 三态（成功 / 过大 / 不可读），补 `catch(OutOfMemoryError)`（`catch(Exception)` 拦不住 Error、会直接崩进程），失败给出明确文案；**不设固定大小上限**（原 8MB 硬上限已移除），内存不足时直接说明而不是崩溃

### 日志体系

- 新增 `LogcatRecorder`：应用启动时后台守护线程持续把 logcat 落盘到应用同名目录，轮转与保留策略完全对齐 `Logger`，因此「清空日志」对它同样生效；`READ_LOGS` 是 signature 级普通安装拿不到，但 Android 允许进程读自己 UID 的日志行，未授权时记录器照样有效
- 日志页 logcat 模式改为优先读落盘文件，**导出按当前来源导出对应文件**（修掉「切到 logcat 后导出的仍是空的 AppLog 文件」），导出文件名也带上来源避免互相覆盖
- 开发工具页移除内嵌的旧日志查看器拷贝，日志能力统一收敛到 `LogViewerScreen`
- **修「闪退后重开跳的是开发工具页、而不是日志页面」**：崩溃跳转目标写死为 `DevToolsActivity`，而它内嵌的运行日志区块早在批次37 就已移除、崩溃内容其实在 `LogViewerScreen`（入口在「管理」页）。现改跳 `LogViewerActivity`，且不再在跳转前提前清掉 `just_crashed` 标记 —— 日志页读到标记后显示「发生异常、请查看下方崩溃日志」提示条并自行清除；跳转失败时兜底清除，避免标记残留导致每次启动都往日志页跳

### 补全

- 关键字补全表新增 XML（75）/ HTML（49）/ CSS / Gradle（48）/ 配置类（42，json / yaml / toml / ini / properties 共用）—— 此前这几类扩展名全部返回空数组，**本 App 最常编辑的配置文件补全一直是死的**
- 刻意只列标签名与属性名、不列 `android:layout_width` 这类带冒号的属性：sora 的前缀回扫遇到 `:` 即断开，词条写全名会补成 `android:android:layout_width`
- 未注册 scope 回退 `text.plain`，不再沿用上一个文件的语言

### 依赖与构建

- sora-editor BOM **0.24.4 → 0.24.6**（随上游修复下划线 token 崩溃；逐项比对两个 tag 的公开 API，确认零删除项被本工程引用）
- Kotlin **2.1.0 → 2.3.10**（0.24.6 的 AAR 用 Kotlin 2.3.x 编译，2.1.0 编译器读不了 metadata 2.3.0，35 条 `e:` 全是这个；版本刻意与 sora 编译器对齐），`kotlin.plugin.compose` 与 `kotlin-stdlib` 同步上调
- 删除零引用死依赖 `language-java`（Java 高亮走的是 `assets/textmate/java` 下的 TextMate 语法文件，与该 AAR 无关）
- 删除死代码类 `EditorUiFlags`（4 个字段全仓零引用）
- 删除零消费者死开关 `autoSave`

### 有意不做（已逐条回源码查证）

- **XML 格式化**：平台 `javax.xml` Transformer 是 pretty-printer 而非保值重排（会统一属性引号、剥掉标签间空白文本节点、可能丢掉 DOCTYPE 内部子集，还带 XXE 加固的坑），与「只动空白、绝不动值」直接冲突；XML 在本项目主要是 AndroidManifest / layout，Android Studio 本来就管格式
- **`QuickQuoteHandler`**：只能由 `Language.getQuickQuoteHandler()` 提供，装了就会关掉内建的选区自动包裹；而 `DefaultSymbolPairs` 本来就带 `"` 与 `'` 的自动闭合与选区包裹，做了等于负收益
- **诊断标记 / inlay hints / 大纲**：Sora 有 API 但 TextMate 分析器不产数据源，加了是死 UI
- **snippet 片段补全**：缺片段库这层产品输入
- **多标签独立撤销历史**：`Content` 天然持有 `UndoManager`，每个标签已隔离，切回来历史清空；要保留就得缓存 `Content` 对象，与 `isModified` 的读取点冲突
- **`ContentIO` 流式读写**：已有内存预检（3.25 倍峰值 + 强制回收）+ OOM 兜底（且已不设硬上限、大文件不再强制只读），收益被高估
- **`disallowSuggestions`**：为真会强制重启输入连接、拒绝组字中的 composing text，会打断中文 / 日文输入

### 批次53：代码编辑器「搜索/替换一律整档」+ 插件签名验证修复

- **搜索/替换对所有文件一律按整档执行（删掉「全文」开关）**：此前小文件走 sora 页内检索、分页大文件要手动开「全文」开关，两套状态互相抢（引出「整读大文件不支持替换」「请打开全文」等只有开发者能懂的提示）。现在唯一路径 = `WholeSearch` 整档扫描（小文件单页内存秒扫、大文件流式分页，450ms 防抖、进度可查、随时取消）；上一个/下一个在整档结果间移动，结果列表点行也推进命中游标；状态行与「重新搜索/全部结果」按钮拆成独立两行，长文案再长也不会互相遮挡；完整结果列表新增右侧滚动条、条目少时自适应高度不再恒撑满；
- **替换按文档形态自动分路**：分页打开的文件仍走磁盘写回缓冲逐页改写（保存后落盘、跨页界匹配跳过不替）；完整加载的文件改为内存中一次性重写（`WholeSearch.replaceInString`，撤销历史重置、保存后写入磁盘），确认弹窗按形态提示差异；整档替换进行中编辑器强制只读，防止编辑被替换结果静默覆盖；
- **修插件更新永远装不上**：签名验证原为「首装文件哈希按插件 ID 永久钉死」，同插件任何更新包都被判「文件被篡改」。改为存 `版本:哈希` —— 同版本哈希必须一致（真正的防篡改）、版本不同则记录新哈希放行，旧的裸哈希记录自动迁移，已卡住的用户直接解锁；
- **结果过期与越界防护**：文档被编辑后整档结果标记「内容已修改，结果可能过期」并给手动重搜入口（不自动重扫 —— GB 级每键一扫不可行）；点击旧结果 / 上一个 / 下一个先把行、列夹到当前文档边界，绝不再因旧行号越界崩溃；切标签后旧标签的结果不再显示/跳转，同一查询自动对新标签重搜；
- **修复清单**：早退分支（空文档/索引建立中）不再残留上一个扫描任务的结果；空查询同时撤下已完成的替换横幅；窗口替换的替换锁改在 `finally` 里释放（视图归位异常时不再永久卡死该标签的保存/翻页/搜索）；正则替换的 `$N`/`${N}` 反向引用组索引越界、未参与匹配的组一律按字面量回显不再抛错；反向引用改在原文里展开（不再被已改写的上下文带偏）；流式扫描补 `MULTILINE` 对齐 sora 原语义（`^`/`$` 匹配每行首尾）；删除状态栏已死的「匹配数」显示与 sora 页内检索残留；**替换中途切标签不再静默取消** —— 替换在后台继续跑完（被替换标签只读、保存/翻页/再发起替换被挡，其他标签不受影响；替换全局单实例），关闭被替换标签才中止，关标签竞态下可能重建的 spool 目录也一并兜底清理；
- **随本轮移除的旧行为**：信息栏「窗口模式」徽标（改为字符数标「本页」）与替换完成后的自动重跑刷新（改为手动「重新搜索」）—— 批次46/47 条目中的对应描述以本条为准。

### 批次54：设备实测修复（OOM / 假分页 / 搜索后无法编辑 / 弹窗与滚动条）

- **修整读大文档逐键复制全文导致的 OOM 崩溃**（2026-10-21 22:26 实测：30MB 文档每个按键都 `Content.toString()` 全文 —— 57MB 字符数组一键一份，堆 531/537MB 顶穿）：内容超 200 万字符的文档编辑时只置脏标记，不再逐键复制；最新文本在**保存 / 搜索 / 替换 / 切走标签**前统一回灌一次（脏标记驱动，干净文档零开销），落盘与搜索快照始终是编辑器里的最新内容；
- **超 16MB 的文件一律分页窗口打开（真分页）**：整读打开只是「当下装得下」，编辑稳态还要付 sora 内容树 + 基线快照多份拷贝，且整读档的 n/N 分页控件只是光标位置换算（看似分页、实则并未分页）。现在 16MB 以上自动走分页窗口模式（每页 2000 行、真换页、真跳转）；整读小文件的「跳到页」改走与「跳转到行」同一条已验证路径，跳完光标落页首行、n/N 随之更新；
- **修「搜索后无法编辑文本」**：搜索框持焦时点编辑器拿不回输入焦点（软键盘仍进搜索框），要点搜索结果或关掉面板才能恢复 —— 现在触摸编辑器即还焦；
- **全部结果弹窗限高随屏幕**：上限改为屏幕高度的 55%（竖屏仍不超过 480dp），横屏/短屏不再顶出对话框；
- **结果列表滚动条可点按/拖动**：按比例跳转列表位置（热区加宽到 20dp，拖动中拇指跟手）；
- **默认主题改为 github-dark-dimmed**（原 dracula）：主题选择器保留、随时可切换；已存过主题的老配置不受影响。

### 批次55：深度复查修复（崩溃 / 静默丢改动 / 并发竞态）

- **修「跳转到行」在大文件索引扫描期的崩溃**：分页索引尚未建成（总行数为 0）时确认跳转，会把目标行夹进 `1..0` 的非法区间抛异常 —— 现在索引就绪前不执行跳转；
- **修分页翻页的内存无界增长**：每载入一页都把整页文本存进基线字典（连续翻几百页即驻留上百 MB，512MB 堆内存机型上必然崩溃）—— 改存 8 字节页指纹，脏判定语义不变；
- **另存为不再保存旧快照、不再抹脏标记**：大文档编辑后另存为此前写的是打开时的旧文本、且把标签标成「已保存」—— 现在先回灌最新内容再写入；
- **Ctrl+S 不再绕过只读锁**：分页索引重建失败后文件处于临时只读，但快捷键保存入口此前不检查只读、会按作废页偏移把文件写碎 —— 现在保存入口统一拦截并提示重新打开；
- **「保存并关闭」保存失败时保留标签**：此前不看保存结果直接关签，改动只剩缓存里的孤儿副本、界面再无入口 —— 现在同步保存成功才关；窗口档/「另存为」的异步保存推迟到完成回调兑现，失败则标签保留；
- **退出确认的「保存」保存全部脏标签**：此前只保存活动标签，多标签同时改脏时其余改动随退出静默丢失；
- **按新编码重建分页索引期间全面互斥**：重建中编辑/翻页/替换/保存全部挡下 —— 此前重建完成会把这几十秒内的编辑与替换成果连同旧缓冲一并静默抹掉；
- **文件树删除前先查未保存改动**：命中已打开且有未保存修改的标签时拦下删除并提示先处理 —— 此前删除后直接强制关签，编辑一并消失；
- **重命名同步窗口档内部文件指针**：此前重命名后分页档仍指向旧路径 —— 翻页读旧路径失败、保存会把改名前的文件“复活”一份；正在进行保存/替换/重建编码时拒绝重命名；
- **修替换横幅竞态**：关掉被替换标签 A 后立刻在标签 B 发起替换，A 协程的收尾会把 B 刚发的横幅清掉、B 失去只读保护被编辑覆盖 —— 替换引入代际号，收尾写横幅前核对代际；取消方同步收尾横幅；搜索的异常收尾同样核对代际（被取消的旧扫描异常不再把新结果盖成「表达式无效」）；
- **修整读文档替换 0 命中置脏**：查询没有任何命中也把标签标成已修改 —— 现在内容没变就不置脏、不触碰编辑器；
- **统一切活入口**：所有直接切换活动标签的路径（重复打开激活、新建议签等）都先回灌脏大文档 —— 防止切走后编辑器按过期文本重置、编辑"消失"；
- **后台回写改原子操作 + 事件归属校验**：保存线程/替换收尾对标签列表的读改写改为原子 update，与主线程按键编辑交错不再吞输入或脏标记；切签瞬间旧编辑器的迟到内容事件按编辑器所挂标签校验归属，不再可能把 A 的文本写进 B。

### 批次56：会话缓存 / 分页字节上限 / 搜索面板瘦身 / 顶部「全部」/ 设置页动画

- **搜索面板去掉结果预览列表**：面板内不再铺 200dp 的匹配行（占空间且与弹窗重复）——「重新搜索」与「全部结果」并入状态行右侧（不再单独占一行，面板更矮）；匹配的浏览与选择只走「全部结果」弹窗（完整列表、懒加载预览、点行跳转）；
- **分页不再只按行数**：新增每页 **1MB 字节上限**（行数 2000 与字节上限**先到者断页**）—— 行极密或单行超长的病态文件不再把上百 MB 塞进一页；单行超上限仍独占一页（页界必须落在行首，不能从行中间切开）；索引快速读首页同样按此上限截断；保存快速重建的行数校验同步改为按「每页实际行数」比对；
- **新增会话缓存，关闭软件后恢复原样**：标签清单/激活项/窗口档页码/整读档未保存正文/窗口档改动页在停止输入 1.2 秒后自动落盘（退出时再兜底抓一次），下次打开编辑器自动恢复标签与未保存修改（文件本体按预期重新从磁盘加载）；全文替换的磁盘缓冲与超 400 万字符的超长草稿暂不跨会话恢复（v1 范围）；
- **顶栏新增「全部」图标**：点开下拉「保存全部文件 / 关闭全部文件」；关闭全部前若有未保存修改，先弹「未保存的修改将丢失」确认；
- **设置页改为滑入/滑出动画**：打开自右滑入 + 淡入；关闭（点页内返回或系统返回键）向右滑出 + 淡出，动画放完才真正卸载。
- **批次56 复查修复**：复访已改页不再静默丢改动（翻页基线曾被污染成改动版指纹，flush 误判「未修改」清掉改动页 —— 会话恢复的窗口档改动首翻即丢，同为所修）；冷启动「上次打开」与会话恢复不再把同一文件开成两个标签（命中就地并入、草稿覆盖内容）；恢复期间（大文件索引未完成）不再落中间态快照 —— 此前会把未消费的改动页草稿当孤儿误删，索引期间被杀进程即永久丢改动；快照写盘按序号单调落盘、不再乱序回退，指纹覆盖重命名/换编码/脏标记翻转；会话条目带文件大小+修改时间，文件被外部改过时放弃补挂改动页并提示，防写坏文件。三轮复查再修：保存开始自动取消在途全文搜索（rename 后旧索引结果会错位）；保存写盘窗口内不落快照（防把上一会话已持久化的改动页当孤儿误删，写盘中被杀/失败可完整回滚）；窗口档当前页的编辑先入捕获面再拍快照（被系统回收不再丢当前页键入）；恢复期分页临时签不抢焦点、关签/索引失败/头读失败均显式终结待恢复项；分页索引构建改用带限定的字节上限常量（漏限定会编译失败）。第五轮再修：非末页行数变化的编辑在「保存→再保存」往返中不再行重复/丢行 —— 全量重建切了页边界时按新布局对齐视图（保存期间无新输入则安全重设并标干净；有则临时只读、提示重新打开，内容原样保留）；并发集合改用线程安全构造；「关闭全部」同步清空待恢复项。第六轮再修：分页索引失效（死页）标签曾被任意一次「重新打开文件」解锁全局只读后二次保存拼坏文件 —— 改为保存/翻页/全文替换三入口逐签硬拦；保存的对齐判定与状态更新整体上主线程串行，保存期间的一次键入不再被静默覆盖；置只读/死标前复查标签存活，不给已关签种僵尸标记；保存失败兜底从 Exception 扩到 Throwable 并先撤保存锁（OOM 不再让保存与全项目快照永久卡死）。第七轮收尾：换编码重建成功即解除死标（该签已被完全重置，不再误拦保存/翻页/替换）；重建失败分支的簿记同样上主线程闭合竞态窗口；两处保存失败分支对已关标签补清 spool，防缓存目录孤儿。

### 批次57：搜索结果编辑器内高亮 / 全量结果弹窗修整 / 去掉「加载更多」

- **搜索结果在编辑器内高亮**（新增）：全文搜索完成后，所有命中自动涂上主题 `editor.findMatchBackground` 色块（github-dark-dimmed = `#966600`，前景文字仍由 sora 后画、压在色块之上）—— 不点「全部结果」也能一眼扫到全部命中；窗口档只画当前页（编辑器里只有这一页），行/列口径与跳转一致，非发起标签自动清除（与面板同一门槛），单文件上限 1 万条。**编辑期间不重算**：sora 自带随插入/删除自动平移（含撤销/重做），高亮跟着内容走、不回跳，「内容已修改」横幅照常提示结果过期；与十六进制色块共用 `setHighlightTexts` 唯一通道，按颜色类型（搜索 = EditorColor、色块 = ConstColor）判归属互相带载、互不覆盖，归并保序写入避免中段插入的 O(n²) 搬移；`setEditorLanguage` 清容器后与色块成对补写（`restoreDecorations`）。
- **去掉「加载更多」，搜索一趟全量收齐**：全量结果页不再分 10 万一批续扫（10 万批次与 50 万硬上限一并移除），扫描完成即一次性收集全部命中、「已载入 N / N 条」一次到位；并行护栏改为**仅有界窗口**才触发 —— 无界收集时各分块只收自己范围、合并恰为全局总数、无冗余放大，保持 4 路并行不退单线程；合并窗口覆盖全部时直接复用结果，省掉 drop/take 的两份列表复制。
- **修全量结果弹窗占满全屏 / 内容截断**：弹窗总高按 min(600dp, 75% 屏高) 封顶，列表行改 `weight(1f, fill = false)`、滚动条高度跟随实测列表高 —— 短列表弹窗随之变矮，横屏下确认按钮不再被顶出屏幕。
- **删「仅列前500条」截断提示**：该文案随预览列表移出面板后已无意义，并入普通完成文案；`editor_search_whole_truncated` 及加载相关三键（`full_more` / `full_loading` / `full_failed`）中英同步删除，字符串键 **1951** 对齐、占位符差异 0。
- **修「编辑后搜索高亮偏移 / 旁边空格被涂」**（用户反馈）：高亮重画效果的键改为只取结果**列表引用**，不再取整个结果对象 —— 此前每次编辑触发的「内容已修改」过期标记（对象 copy）都会让效果重启、用编辑前的旧行列重画一遍，高亮被拽回原位（表现为偏移、刚敲的空格被算进旧区间）。现在编辑期间一律由 sora 的插入/删除回调自动平移，重画只发生在「新一轮搜索出结果 / 结果被清空」时。
- **修「关闭搜索面板再打开，结果与高亮消失」**（用户反馈）：查询/大小写/全词/正则/命中游标提升到 Screen 级持有 —— 面板收起时整树卸载、内部状态清零，重开会以空查询触发「空输入即清结果」把已搜完的结果与高亮一起误清；提升后收起重开**原样保留**（同签名且结果有效时连重扫都跳过），且只有面板的**关闭图标 X** 才清除结果与高亮，顶栏搜索图标/返回键只是收起。
- **插件向导草稿模式**（用户反馈）：创建插件时编辑器**只显示向导文件** —— 用户没关的设备文件等其他标签隐藏而非关闭（向导结束编辑器关闭后随会话恢复原样回来）；插件草稿**不进编辑器缓存** —— 会话快照按草稿目录前缀剔除（防抖/退出兜底/指纹三路同时生效）、不记「上次打开」（进程被回收后不会再把插件草稿复活成文档），上一轮向导的遗留草稿签在下一轮进入时直接撤掉。

### 国际化

- 中英文字符串键从 1867 增至 **1951**，占位符差异 0

## [6.1.0] - 2026-10-07

> 维护版本：针对 6.0.0 代码库的一轮全面缺陷修复（清单 233 项全部关闭），覆盖启动与权限、主线程/ANR、深色主题、插件宿主正确性与安全、备份恢复数据安全、错误可见性、构建卫生，以及后续的死代码与资源清理、架构收口、国际化、可访问性与 CI 收紧。无新功能。

### 启动、权限与日志

- 开屏不再卡死：Android 11+ 与「已授存储」用户补申请自定义危险权限（OPEN_PLUGIN / RUN_COMMAND），`onResume` 复检权限，拒绝后「允许」按钮不再永久失效
- 插件权限请求现在返回**真实**回调结果（原先请求根本未发起），进度/完成回调改为真实回执后触发
- `Logger.init()` 提前到 `Application.onCreate` 开头，启动阶段日志不再丢失
- 旋转/系统重建 Activity 不再误杀 supervisor 与全部插件后端
- 冷启动进程清理移到后台线程，用 `kill -0` 轮询替代主线程固定 sleep

### 主线程 / ANR

- 终端新建会话拆为「命令构建（IO 线程）+ 会话创建（主线程）」，命令构建失败改为 Toast 提示，intent 命令只消费一次
- 插件单个/批量/套装导入、导出、下拉刷新、备份打包与恢复全部移出主线程
- 容器列表加载与下载接进度回调并做 100ms 节流

### 主题与可读性

- 输入框文字改走 `colorScheme.onSurface`；Markdown 渲染器 27 处浅色硬编码改为随深浅色切换
- 帮助页按语言读取对应 `Help.md`（en / zh-cn）

### 插件宿主

- `onResume/onPause` 改传 instance key，WebView resume/pause 与插件生命周期事件真正生效
- 后端路径归一化拒绝 `@`、`//`、`:`、`\\`、`..` 与控制字符（堵住出站 host 绕过），`openUrl` 强制 `http/https/mailto/tel` 白名单
- 事件载荷与错误 JSON 正确转义（引号/换行不再导致脚本破损、事件静默丢失）
- 保留 WebView 上限 4 个（FIFO，淘汰即 destroy），内存吃紧时清空
- 后端启动 90s 超时改为 Toast 提示，不再无提示卡住

### 数据与备份

- 恢复回滚真正生效（原条件为死代码），被顶替的插件目录仅在全部成功后删除
- zip 失败改为上抛，不再静默产出残缺压缩包；解压增加条目数与总字节数上限（zip bomb 防护）
- 工作目录 / 视图模式的 StateFlow 改为实时生效

### 错误可见性

- 源加载失败、未启用任何源、下载失败、安装失败改为列表内联横幅 + 重试/知道了（原先只写日志，下载失败路径还会立刻抹掉自己的错误信息）

### 构建

- 移除无效 multidex 配置（minSdk 23 走系统原生 multidex），`multidex-config.pro` 更名 `app-keep.pro`
- `core`/`core-ktx` 统一 1.13.1，去掉 `resolutionStrategy.force`
- 去掉 app 模块重复的 Markwon 声明与死依赖（`commons-lang3`、`error_prone_annotations`、`org.brotli:dec`）
- 换用 `proguard-android-optimize.txt`；CI 单测不再 `continue-on-error`

### 安全与插件入口

- 插件宿主分发 `PluginOpenDispatchActivity` 在 Manifest 正式注册并关闭 `exported`，外部应用一律经显式 Intent 进入；文档中的 Intent-filter 清单与实际声明核对一致
- 删除孤儿权限解释栈，开屏权限对话框成为唯一流（用户拍板）；删除自权限申请流
- JS 桥 5 个关键入口（callHost / openUrl / getLocation / getSerialNumber / getMacAddress）完成审计基线核对，判定全部 PASS
- supervisor 退出码回传与僵尸后端回收；日志脱敏与容器状态口径统一

### 启动、更新与长任务

- 启动序列改为声明式任务；首次启动 IO 全部移出主线程
- 更新下载改前台服务并支持通知栏取消；开屏改为每次启动都检查、按窗口区分新旧
- 更新列表改双字段排序；静默更新与更新检查 listener 归拢到 `UpdateViewModel`
- 新增统一长任务框架（注册表 + 全局宿主），备份导出/恢复与容器导入/导出/下载接入，进度与错误内联展示
- 小部件后台周期刷新接入 WorkManager 并提供 UIConfig 开关
- 后端设置页、镜像页的状态与协程全部收口 ViewModel

### 架构与状态收口

- `core/` 只保留契约文件，3 个非契约单元迁出；删除 `core/plugin` 下 8 件死文件
- `PluginInfo` 死运行时字段删除、状态外移；三个数据模型 74 个 `var` 改 `val`、突变点收敛
- 网络 / 偏好单例收口；环境编排下沉 `EnvironmentBootstrap`
- 全量迁 AppCompat 并接入 `AppCompatDelegate` 夜间模式（用户拍板）
- 存储迁移整组执行；日志/备份/TPK 存储方向回调回迁共享工作目录（用户拍板）

### 界面、可访问性与深色主题

- 空态与错误卡统一收口 `UnifiedEmptyState`；补齐骨架屏、卸载反馈与备份错误内联
- 返回键迁 Compose `BackHandler`；tab 状态改为可保存；底部导航项补充可选语义；快捷方式图标按密度缩放
- 主题三件套（颜色 / 字号 / 形状）键位拆分与单一映射收口，收窄配置保存的重组面
- **根级 `LocalContentColor` 兜底**：渐变开启时页面容器为透明，Material3 `contentColorFor` 无匹配会回落到根默认黑色，导致深色下未显式着色的文本（如插件名）渲染成黑字——现以 `onBackground` 作全树兜底，深浅色自动正确
- 默认风格深色配色对齐新拟态色系，并按「背景 < 输入 < 卡片」三级提亮，深色下卡片与背景区分恢复
- 深色渐变防污染：渐变 5 字段按模式读取、风格切换保留当前主题模式、缺省回落深色默认色
- 桌面图标由 Termux `>_` 恢复为 UIN 原版字标（矢量 + 5 组密度 PNG 重绘）
- 源管理、文档查看等页面移除遮挡主题根渐变的不透明包装层

### 国际化与文案

- 文档中英双语对齐（`en/` 与 `zh-cn/` 各 5 篇），文档加载收口
- 日志与固定文案英文化收口；双用途文案固定英文；`locale` 三套机制收敛为「应用内切换为准」（用户拍板）
- strings 中英键集合纳入 CI 卡点，防止键集合与占位符回归
- 管理页「文档中心」提至软件组首位，移除独立「使用帮助」入口（帮助内容仍为文档中心首篇）；软件组标题改为「**软件管理**」
- 终端选中操作栏「复制 / 粘贴 / 更多」中文化（覆盖 terminal-view 模块默认英文）；终端复制提示统一为「已复制到剪贴板」
- proot 安装提示文案修正为「正在安装 proot 容器… / proot 容器安装完成」
- 创建插件弹窗的 4 个前端类型选项改为标准卡片并居中对齐

### 死代码与资源清理

- 删除 Kotlin 死代码组（8 类目标）、`TrampolineActivity`、死色值与死 Preview
- 删除 186 项零引用资源与 3 项过期 assets（保留 proot 全部与 `host-sdk.jar`），并执行资源规范化
- 演示 TPK 移出正式包；孤儿文件整层删除；`PluginInfo` 运行时死字段清理

### 构建与 CI

- CI 收紧、脚本归位、编译器回调自清；strings 中英键对齐纳入卡点
- 首次真机构建修复链：避免使用 `@hide` API（`Os.getpgid`/`killpg`）、恢复被误删的主题、修正注释内裸 `/*` 导致的嵌套注释未闭合、58 个编译错误归并为 9 簇根因、补齐 `R` 导入、消除 `getPrefs` 的 JVM 签名撞车

## [6.0.0] - 2026-09-26

> 本次更新是一次**重大架构变革**：彻底去除对 Termux 环境的依赖（bootstrap、proot-distro、TermuxActivity 等），引入自包含的 proot 容器系统，直接支持 Debian rootfs，并新增完整的容器管理 UI。应用不再继承 TermuxApplication，直接继承 Android Application。终端用 Kotlin 重写（SimpleTerminalActivity）。共修改 100+ 个文件，新增 3000+ 行，删除 5000+ 行。

### 去除 Termux 环境依赖

- **移除 Termux bootstrap**：不再需要 `bootstrap-aarch64.zip` 或 native `termux-bootstrap.c`/`termux-bootstrap-zip.S` 代码；启动环境自动安装从 bootstrap + Alpine 流水线改为 proot binary + Debian rootfs 直接流水线
- **移除 proot-distro 依赖**：不再需要 `proot-distro install/restore` 命令；rootfs 通过 `RootfsManager.extractTar()` 从内置 `debian-rootfs.tar.xz` 直接解压
- **移除 TermuxActivity**：旧版 Java 终端（`app/` 目录，32 个文件）删除；替换为 `SimpleTerminalActivity`（Kotlin，1 个文件），直接使用 `TerminalView` + `TerminalSession`
- **移除 TermuxService 和 RunCommandService**：后端进程管理不再需要
- **移除 TermuxApplication 基类**：`UinApplication` 现在直接继承 `android.app.Application` 而非 `TermuxApplication`
- **精简 Termux shared 库**：从 121 个文件缩减至 83 个；删除 `crash/`、`data/`、`extrakeys/`、`file/`、`interact/`、`models/`、`notification/`、`plugins/`、`shell/`、`terminal/`、`theme/` 子目录及 `TermuxAppSharedProperties`/`TermuxSharedProperties`
- **移除 Android 组件**：`DocumentsProvider`、`ContentProvider`、`TermuxOpenReceiver`、`RealTermuxProbeReceiver`、`SystemEventReceiver` 全部移除
- **移除权限**：`MANAGE_DOCUMENTS`、`RUN_COMMAND` 不再声明
- **移除 assets**：`alpine.tar.xz`（Alpine rootfs 备份）、`xz-aarch64/` 解压器

### PRoot 容器系统（新增）

- **新增 `proot/` 模块**，包含 6 个文件：
  - `ContainerConfig.kt` — 容器配置数据类（名称、rootfs 路径、绑定挂载、环境变量）
  - `ProotInstaller.kt` — 从 assets 安装 proot 二进制到 app files 目录
  - `ProotRuntime.kt` — 构建 proot 命令行，支持绑定挂载、伪造内核版本、环境变量；使用 `--link2symlink`、`--sysvipc`、`--change-id=0:0`
  - `RootfsManager.kt` — 完整容器生命周期：创建、删除、列表、导出/导入 tar 归档；支持 tar.gz/tar.xz/tar.zst 格式，正确处理硬链接
  - `RootfsSetup.kt` — 解压后修复（resolv.conf、hosts、/tmp 权限、/root、/etc/shells）
  - `FakeSysdata.kt` — 创建假的 /proc 和 /sys 条目以兼容 Android 上的 proot
- **默认容器从 Alpine 改为 Debian**（`BackendConfig.kt`：`CONTAINER_DEFAULT = "debian"`）
- **容器存储位置**：`context.filesDir/containers/debian/rootfs`（内部存储以兼容符号链接）

### 容器管理 UI（新增）

- **新增 `ui/screen/proot/` 模块**：
  - `ContainerManagementActivity.kt` — Activity 包装器
  - `ContainerManagementScreen.kt` — 完整 Compose UI，管理容器（列表、下载发行版、导出/导入、删除、设置默认）
- **功能**：在线下载发行版、导出容器为 `.tar.zst`（可分享）、从 `.tar.zst`/`.tar.xz`/`.tar.gz` 导入、删除容器、设置默认容器
- **导出压缩级别**：可调节 0-22（zstd），默认 9；越低越快，越高越小；根据设备内存自动限制上限防止 OOM

### 终端重写

- **新增 `SimpleTerminalActivity.kt`**：Kotlin 终端，使用 `TerminalView` + `TerminalSession` 类和 Compose UI，直接集成 proot 容器
- **新增布局文件**：`terminal_activity_layout.xml`、`terminal_session_item.xml`、`terminal_key_bg.xml`、`terminal_key_ripple.xml`
- **移除布局**：`activity_termux.xml`、`view_terminal_toolbar_extra_keys.xml`

### 插件系统更新

- **`ProotContainerManager.kt` 重写**：不再依赖 Termux 前缀路径（`/data/data/.../files/usr/bin/proot-distro`）；直接使用 `ProotInstaller`、`ProotRuntime`、`RootfsManager`
- **`PluginHostActivity.kt` 更新**：使用 `MainScope().launch` 协程；进度通过 `AppToast` 显示
- **`DevScreen.kt` 更新**：终端按钮启动 `SimpleTerminalActivity` 而非 `TermuxActivity`

### 插件后端启动修复

- **环境变量注入 proot 命令**：`PORT`、`PLUGIN_ID`、`PLUGIN_DIR`、`WORK_DIR`、`HOME`、`PYTHONUNBUFFERED` 现在在容器内启动命令前 export
- **proot 宿主环境清理**：移除污染容器的 Termux 宿主路径（`PREFIX`、`TMPDIR`、`TERMUX_*`）；设置干净的容器 PATH 和 `PROOT_TMP_DIR`/`PROOT_L2S_DIR`
- **Shell 检测修复**：`findShell()` 现在优先查找 `/bin/sh` 并验证符号链接目标，避免断开的符号链接（如 Debian 最小 rootfs 中 `/bin/bash` -> `/usr/bin/bash`）
- **`PluginInfo.getInstallCommand()` 更新**：包管理器命令从 `pkg install` 改为 `apt install`，使用正确的 Debian 包名

### CUI 插件改进

- **移除占位页面**：CUI 插件不再显示"正在全屏终端执行命令"占位页，直接启动终端
- **启动命令传递给终端**：`SimpleTerminalActivity` 现在通过 Intent extras 接收可选命令；CUI 插件传递 `backendPreCommand`（含环境变量 export）
- **插件目录使用宿主路径**：CUI 终端使用 `/storage/emulated/0/UIN_Tool/plugins/<id>`（通过 `/storage/emulated/0` 绑定挂载可访问），不再使用不存在的容器路径 `/plugins/<id>`

### 插件图标加载

- **新增 `PluginIcon` composable**：在 `Dispatchers.IO` 上异步加载 `plugins/<id>/icon.png`，失败时回退显示插件名首字母
- **应用于**：`PluginListItem`、`PluginGridItem`（ToolsScreen）、`PluginManageItem`（PluginManageScreen）

### 文档更新

- 版本更新至 6.0.0（build 24），所有文档同步
- 终端章节："基于 Termux" → "基于 PRoot"
- 包管理器命令：全文 `pkg` → `apt`
- Alpine → Debian 引用在活动文档中已更新
- v6.0.0 更新日志已编写

### 其他变化

- `termux-shared` 模块保留（用于 `terminal-emulator` 和 `terminal-view` JNI）但大幅精简
- 注释清理：`build.gradle` 头部从 `// Termux/build.gradle` 改为 `// UIN Tool/build.gradle`

---

## [5.7.0] - 2026-08-31

> 本版本聚焦**插件分发系统重构、更新检测、CI 自动化、网络缓存优化**：从单仓库单插件模式切换为源（Source）+ 多插件模式，支持多源聚合、自动版本更新检测、GitHub Actions CI 自动构建、图标磁盘缓存与 HTTP 条件请求。共修改 50+ 个文件，新增 2500+ 行。

### 插件分发系统重构

- 全新源（Source）架构：每个 GitHub 仓库作为"源"包含多个插件，通过 `source.json` 索引
- 新增 `SourceIndexFetcher`：支持 GitHub API + raw URL 双通道获取插件索引，内置缓存
- 新增 `SourceManageActivity`：源管理页面，支持启用/禁用/删除自定义源
- `RepoViewModel` 重构：多源插件聚合、按源筛选、搜索过滤
- `RepoScreen` 更新：源 FilterChip 筛选、"有更新"标记、详情对话框显示版本对比
- 移除 `backendPort` 字段：后端端口由 `findAvailablePort()` 自动分配，避免端口冲突

### 插件更新检测

- 新增 `hasUpdate` / `installedVersion` 字段：比较本地已安装版本与远程源版本
- 已安装插件有更新时显示"有更新"标记，按钮变为"更新"
- 详情对话框显示当前已安装版本，支持一键更新

### CI 自动化

- `build.yml`：触发路径包含 `.github/workflows/**`；deploy 使用 `git clone --branch dist`（token 认证）；增量构建使用 `github.event.before` 精确检测变更；自动复制 `plugin.json` + `icon.png` + `.tpk` 到 dist 分支
- `generate-index.sh`：从 dist 目录读取 `plugin.json`，支持 `visible` 文件隐藏插件
- `build-plugin.sh`：native 编译使用 `host-sdk.jar`；支持绝对路径；`other` 类型调用插件自带 `build.sh`
- 插件构建脚本 `build.sh`：支持 `chmod +x` 确保二进制可执行

### 网络缓存优化

- 新增 `DiskCache` 磁盘缓存工具：SHA-256 哈希文件命名、内存+磁盘双层缓存、1 天 TTL 自动过期
- HTTP 条件请求：存储 `ETag` / `Last-Modified` 头，发送 `If-None-Match` / `If-Modified-Since` 避免重复下载未变更资源
- 图标缓存：`CachedPluginIcon` Composable 优先从磁盘缓存加载，回退到网络条件请求
- 启动时缓存清理：应用启动时自动清除过期缓存条目

### UI 改进

- 插件详情对话框显示插件图标（64dp 居中）
- 详情对话框确认按钮使用主题色背景 + 白色文字
- 插件卡片列表"打开"按钮使用描边样式（透明底 + 主题色文字 + 描边）
- 统一 UI 修复：下拉框样式跨页面对齐、按钮颜色在默认与新拟态风格间保持一致、硬编码文字移入字符串资源

### Bug 修复

- 修复 `filemanager` 插件路径错误：从 app 内部存储改为 `/storage/emulated/0/UIN_Tool/plugins`
- 修复 `calculator` 后端启动失败：移除不必要的 `pip install flask` 依赖
- 修复 `sysinfo` 插件二进制权限问题：构建时添加 `chmod +x`
- 修复 `RepoScreen` 缺少 `AppColors` 导入
- 修复 `SourceManageActivity` 主题使用 `UINToolTheme`
- 修复 `RepoPluginCard` 图标加载智能转型错误（委托属性）

---

## [5.6.0] - 2026-08-30

> 本版本聚焦**UI 系统完善、新拟态风格新增、语言切换功能**：完善 UI 个性化系统，新增新拟态（Neumorphism）风格，新增语言切换功能（支持跟随系统/简体中文/English）。共修改 92 个文件，新增 6240 行，删除 3850 行。

### 🎨 UI 系统完善

- 完善统一组件系统：所有页面统一使用 UnifiedButton、UnifiedCard、UnifiedTextField、UnifiedSwitch、UnifiedIconButton、UnifiedSlider、UnifiedChip 等组件
- 修复多处原始 Material3 组件未统一的问题（CodeEditorScreen、BackupScreen、BackendSettingsScreen、WidgetConfigScreen、UIConfigPropertyPanel）
- 修复 OnboardingScreen 硬编码颜色为主题色
- 统一新拟态阴影效果（neuRaised/neuInset）使用 BlurMaskFilter 实现
- UnifiedButton 支持长文本自动换行，不再截断
- DropdownPropertyRow 使用原生 Row+Text 替代 disabled UnifiedTextField，修复下拉框文字灰色问题
- UnifiedDialogs 重构：Popup + AnimatedVisibility 实现纯淡入淡出动画，dialogBackgroundOf() 支持渐变背景
- UIComponents 桥接层：所有旧 UIComponents 方法委托给 Unified* 组件
- ManageScreen、PluginManageScreen、ToolsScreen、DevToolsScreen、PermissionManagerScreen 等页面全面使用 Unified 组件

### ✨ 新拟态风格

- 新增新拟态（Neumorphism）风格预设，支持自定义阴影强度、内阴影、辉光效果
- 新增文件：NeuModifiers.kt（381行）- BlurMaskFilter 渲染引擎，neuRaised/neuInset/neuPressable/neuGlow 等修饰符
- 新增文件：NeuEffects.kt - 自定义波纹和辉光效果
- 新增文件：NeumorphicComponents.kt - NeuCard/NeuButton/NeuInput/NeuSwitch 等组件
- 新增文件：SpecialComponents.kt - 特殊组件
- 新增文件：UIConfigPreviewScreen.kt - 新拟态预览页面（35个控件展示）
- 新增文件：UIConfigNeumorphismPreview.kt - 新拟态独立预览
- 新增文件：UIConfigModels.kt - ConfigState 数据模型，loadConfigFromUIConfig/saveConfigToUIConfig 序列化
- 新增文件：StylePresets.kt - Default/Neumorphism 预设，toConfigState() 扩展
- 新增文件：StyleManager.kt - 风格切换、缓存、重置
- 新拟态组件支持：卡片、按钮、输入框、开关、芯片、滑块等
- 新拟态动画速度可调（快/中/慢）

### 🌐 语言切换

- 新增语言切换功能（内容属性 > 文字 > 语言）
- 支持：跟随系统、简体中文、English
- UinApplication.applyLocale() 设置 Locale 并更新 Configuration
- 语言设置持久化保存，重启后生效
- UI 个性化页面所有标签支持多语言（Str.get() 本地化）
- UIConfigSidebar 30+ 树节点标签本地化

### 🔧 其他改进

- 半透明效果（原玻璃效果）重命名，支持透明度调节滑块
- 修复半透明效果透明度调节不生效的问题（glassBackground 现在正确应用 alpha 覆盖）
- 修复文档中心页面顶部间距问题（DocBrowserScreen contentPadding）
- 修复 15 个中英文字符串错位问题（neumorphism/animation 字符串移到正确位置）
- 波纹效果开关说明：该开关控制 Material3 原生波纹，新拟态组件使用独立的阴影过渡动画作为点击反馈，两者互不影响
- 新增文件：SharedSupervisor.kt - 共享 Supervisor 进程管理
- build.gradle: versionCode 22, versionName "5.6.0"
- AndroidManifest.xml: 多处 activity 属性更新
- 多个 .tpk 测试插件包更新
- 修复部分已知问题

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
| 文档版本 | 6.2.0 |
| 最后更新 | 2026年10月16日 |
| 对应应用版本 | v6.2.0 (Build 26) |

---

© 2026 UIN Team. All Rights Reserved.
