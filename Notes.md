# UIN Tool - 新拟态升级研究笔记

> **⚠️ 归档快照（2026-08-23 记录）** — 本文是 5.6.0 UI 美化阶段的研究笔记，非当前状态文档。
> 下方「构建系统升级方案」表中的目标**已全部落地**（AGP 8.9.1、compileSdk 36、Compose BOM 2025.08.00、Gradle 9.0.0 不变、targetSdk 28 不变、Kotlin 2.1.0 不变）。
> 当前进度看 `Progress.md`，变更看 `UC.md`，问题清单看 `TODO233.md`。

## 一、构建系统升级方案

| 组件 | 当前 | 目标 | 原因 |
|------|------|------|------|
| AGP | 8.7.2 | **8.9.1** | compileSdk 36 最低要求 8.9.1 |
| compileSdk | 35 | **36** | 使用 Compose UI 1.9.0 新 API |
| Compose BOM | 2024.09.00 | **2025.08.00** | 含 dropShadow / innerShadow |
| Gradle | 9.0.0 | **不变** | AGP 8.9 要求 ≥8.11.1，当前已满足 |
| targetSdk | 28 | **不变** | 影响 data 目录和运行时行为，不改 |
| Kotlin | 2.1.0 | **不变** | 已兼容 Compose compiler 2.1.0 |
| NDK | 29.0.x | **不变** | 无冲突 |
| minSdk | 23 | **不变** | — |

### AGP 兼容性矩阵（官方文档）

| compileSdk | 最低 AGP | 最低 Gradle |
|------------|----------|------------|
| 33 | 8.1.0 | 8.0 |
| 34 | 8.1.1 | 8.0 |
| 35 | 8.6.0 | 8.7 |
| **36** | **8.9.1** | **8.11.1** |
| 37 | 9.1.1 | 9.3.1 |

### AGP → Gradle 最低版本映射

| AGP 版本 | 最低 Gradle |
|----------|------------|
| 8.7 | 8.9 |
| 8.8 | 8.10.2 |
| **8.9** | **8.11.1** |
| 8.10 | 8.11.1 |
| 8.11 | 8.13 |
| 8.12 | 8.13 |
| 8.13 | 8.13 |

### compileSdk 与 targetSdk 的区别

- **compileSdk** — 只决定编译时能看到哪些 API，纯编译期设置，**不影响** data 目录、二进制文件、运行时行为
- **targetSdk** — 决定运行时行为（权限模型、data 目录访问、存储访问框架等）
- **minSdk** — 决定最低安装版本

---

## 二、Compose UI 1.9.0 新 Shadow API

### 发布信息

- **版本**: Compose UI 1.9.0
- **BOM**: 2025.08.00
- **发布时间**: 2025年8月
- **官方博客**: https://android-developers.googleblog.com/2025/08/whats-new-in-jetpack-compose-august-25-release.html
- **官方文档**: https://developer.android.com/develop/ui/compose/graphics/draw/shadows
- **官方示例代码**: https://github.com/android/snippets/blob/main/compose/snippets/src/main/java/com/example/compose/snippets/graphics/Shadows.kt

### 新增类型（`androidx.compose.ui.graphics.shadow`）

```kotlin
class Shadow(
    radius: Dp,                          // 模糊半径
    color: Color = Color.Black,          // 阴影颜色
    spread: Dp = 0.dp,                   // 扩展/收缩
    offset: DpOffset = DpOffset.Zero,    // 偏移量
    alpha: Float = 1.0f,                 // 透明度 (0.0-1.0)
    blendMode: BlendMode = DefaultBlendMode,  // 混合模式
    brush: Brush? = null                 // 渐变画刷（可选）
)
```

### 新增修饰符（`androidx.compose.ui.draw`）

#### dropShadow — 外阴影

```kotlin
// 方式1：传入 Shadow 对象（适合静态阴影）
fun Modifier.dropShadow(shape: Shape, shadow: Shadow): Modifier

// 方式2：lambda 版本（适合动画，避免重组）
fun Modifier.dropShadow(shape: Shape, block: DropShadowScope.() -> Unit): Modifier
```

**DropShadowScope 属性：**
- `radius: Float` — 模糊半径（像素）
- `color: Color` — 阴影颜色
- `spread: Float` — 扩展/收缩（像素）
- `offset: Offset` — 偏移量（像素）
- `alpha: Float` — 透明度
- `brush: Brush?` — 渐变画刷
- `blendMode: BlendMode` — 混合模式

#### innerShadow — 内阴影

```kotlin
// 方式1：传入 Shadow 对象
fun Modifier.innerShadow(shape: Shape, shadow: Shadow): Modifier

// 方式2：lambda 版本
fun Modifier.innerShadow(shape: Shape, block: InnerShadowScope.() -> Unit): Modifier
```

**InnerShadowScope 属性：** 与 DropShadowScope 相同

### 关键顺序规则（官方文档明确说明）

> **dropShadow 必须在 background 之前**
> **innerShadow 必须在 background 之后**

如果顺序错误：
- dropShadow 在 background 之后 → 阴影被背景遮挡
- innerShadow 在 background 之前 → 内阴影被背景覆盖

```kotlin
// ✅ 正确：外阴影 → 背景 → 内阴影
Modifier
    .dropShadow(shape, shadow1)
    .dropShadow(shape, shadow2)
    .background(color, shape)        // 背景在中间
    .innerShadow(shape, shadow3)
    .innerShadow(shape, shadow4)

// ❌ 错误
Modifier
    .background(color, shape)
    .dropShadow(shape, shadow1)      // 被背景遮挡
```

### 链式调用

多个 dropShadow / innerShadow 可以链式叠加：

```kotlin
Modifier
    .dropShadow(shape, Shadow(radius = 40.dp, color = Color(0xB3000000), offset = DpOffset(2.dp, 8.dp)))
    .dropShadow(shape, Shadow(radius = 4.dp, color = Color(0x66000000), offset = DpOffset(0.dp, 4.dp)))
    .background(color, shape)
    .innerShadow(shape, Shadow(radius = 12.dp, color = Color(0xCC000000), offset = DpOffset(6.dp, 6.dp)))
    .innerShadow(shape, Shadow(radius = 3.dp, color = Color(0x40FFFFFF), offset = DpOffset(0.dp, 0.dp)))
```

---

## 三、官方新拟态配方

### Google 官方 Snippets（NeumorphicRaisedButton）

来源: https://github.com/android/snippets/blob/main/compose/snippets/src/main/java/com/example/compose/snippets/graphics/Shadows.kt

```kotlin
@Composable
fun NeumorphicRaisedButton(
    shape: RoundedCornerShape = RoundedCornerShape(30.dp)
) {
    val bgColor = Color(0xFFe0e0e0)
    val lightShadow = Color(0xFFFFFFFF)
    val darkShadow = Color(0xFFb1b1b1)
    val upperOffset = -10.dp
    val lowerOffset = 10.dp
    val radius = 15.dp
    val spread = 0.dp

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .wrapContentSize(Alignment.Center)
            .size(240.dp)
            // 亮色阴影（左上）
            .dropShadow(
                shape,
                shadow = Shadow(
                    radius = radius,
                    color = lightShadow,
                    spread = spread,
                    offset = DpOffset(upperOffset, upperOffset)
                ),
            )
            // 暗色阴影（右下）
            .dropShadow(
                shape,
                shadow = Shadow(
                    radius = radius,
                    color = darkShadow,
                    spread = spread,
                    offset = DpOffset(lowerOffset, lowerOffset)
                ),
            )
            // 背景（必须在 dropShadow 之后）
            .background(bgColor, shape)
    )
}
```

**效果原理：**
- 左上角白色阴影 = 模拟光源照射的高光
- 右下角灰色阴影 = 模拟被遮挡的暗影
- 两个阴影叠加产生凸起的 3D 视觉效果

---

## 四、sinasamaki 进阶新拟态（Brush 渐变版本）

来源: https://www.sinasamaki.com/new-shadow-api-for-jetpack-compose/

```kotlin
// 使用 Brush 渐变的新拟态效果
modifier = Modifier
    .dropShadow(shape = shape) {
        radius = 50f
        brush = Brush.verticalGradient(
            colors = listOf(White, White, Zinc950.copy(alpha = .2f))
        )
    }
    .border(
        width = 2.dp,
        shape = shape,
        brush = Brush.verticalGradient(
            colors = listOf(White, Zinc950.copy(alpha = .3f))
        ),
    )
    .size(300.dp)
    .background(
        color = Zinc300,
        shape = shape,
    )
    .innerShadow(shape = shape) {
        radius = 90f
        brush = Brush.verticalGradient(
            colors = listOf(White, Zinc950.copy(alpha = .2f))
        )
    }
```

**优势：** 渐变阴影比纯色阴影更自然，模拟真实光照的柔和过渡。

---

## 五、HTML → Compose 效果映射

### HTML 变量定义

```css
:root {
    --sd: rgba(0, 0, 0, .12);     /* 暗色阴影 */
    --sl: rgba(255, 255, 255, .85); /* 亮色阴影 */
    --gw: rgba(74, 138, 158, .35);  /* 辉光色 */
    --shadow-sm: 2px 2px 5px var(--sd);
    --shadow-md: 4px 4px 10px var(--sd);
    --shadow-lg: 8px 8px 20px var(--sd);
}
```

### 效果对照表

| 元素 | HTML CSS | Compose 实现 |
|------|----------|-------------|
| **卡片凸起** | `box-shadow: 4px 4px 10px var(--sd)` | `dropShadow(Shadow(radius=10.dp, color=Color(0x1F000000), offset=DpOffset(4.dp, 4.dp)))` |
| **按钮凸起** | `box-shadow: 2px 2px 5px var(--sd)` | `dropShadow(Shadow(radius=5.dp, color=Color(0x1F000000), offset=DpOffset(2.dp, 2.dp)))` |
| **开关轨道凹陷** | `inset 3px 3px 6px var(--sd), inset -3px -3px 6px var(--sl)` | `innerShadow×2`: dark(offset(3,3)) + light(offset(-3,-3)) |
| **开关激活凹陷** | `inset 2px 2px 4px rgba(0,0,0,.2)` | `innerShadow(Shadow(radius=4.dp, color=Color(0x33000000), offset=DpOffset(2.dp, 2.dp)))` |
| **开关旋钮凸起** | `2px 2px 5px var(--sd)` | `dropShadow(Shadow(radius=5.dp, color=Color(0x1F000000), offset=DpOffset(2.dp, 2.dp)))` |
| **输入框凹陷** | `inset 3px 3px 6px var(--sd), inset -3px -3px 6px var(--sl)` | `innerShadow×2`: dark(offset(3,3)) + light(offset(-3,-3)) |
| **导航栏** | `box-shadow: 0 -8px 30px var(--sd)` | `dropShadow(Shadow(radius=30.dp, offset=DpOffset(0.dp, -8.dp)))` |
| **抽屉** | `box-shadow: 6px 0 24px var(--sd)` | `dropShadow(Shadow(radius=24.dp, offset=DpOffset(6.dp, 0.dp)))` |

### 新拟态凸起效果完整配方

```kotlin
// 标准凸起（卡片、按钮）
Modifier
    .dropShadow(shape, Shadow(radius = 15.dp, color = Color.White, offset = DpOffset(-10.dp, -10.dp)))
    .dropShadow(shape, Shadow(radius = 15.dp, color = Color(0xFFb1b1b1), offset = DpOffset(10.dp, 10.dp)))
    .background(bgColor, shape)
```

### 新拟态凹陷效果完整配方

```kotlin
// 标准凹陷（输入框、开关轨道）
Modifier
    .background(bgColor, shape)
    .innerShadow(shape, Shadow(radius = 6.dp, color = Color(0x1F000000), offset = DpOffset(3.dp, 3.dp)))
    .innerShadow(shape, Shadow(radius = 6.dp, color = Color(0xD9FFFFFF), offset = DpOffset(-3.dp, -3.dp)))
```

### 新拟态按压效果完整配方

```kotlin
// 按下状态（开关激活、按钮按下）
Modifier
    .background(bgColor, shape)
    .innerShadow(shape, Shadow(radius = 4.dp, color = Color(0x33000000), offset = DpOffset(2.dp, 2.dp)))
```

---

## 六、亮度主题适配

### 浅色模式

```kotlin
val neuBgColor = Color(0xFFF5F7FA)       // 背景色
val neuLightShadow = Color(0xD9FFFFFF)    // 亮阴影（白色 85%）
val neuDarkShadow = Color(0x1F000000)     // 暗阴影（黑色 12%）
```

### 深色模式

```kotlin
val neuBgColor = Color(0xFF1E1E22)        // 背景色
val neuLightShadow = Color(0x08FFFFFF)    // 亮阴影（白色 3%）
val neuDarkShadow = Color(0xA6000000)     // 暗阴影（黑色 65%）
```

---

## 七、性能注意事项

1. **lambda 版本更适合动画** — `dropShadow(shape) { ... }` 避免频繁重组
2. **Shadow 对象适合静态阴影** — `dropShadow(shape, Shadow(...))` 简洁
3. **过多链式阴影有性能开销** — 每个 dropShadow/innerShadow 都是独立的图形层
4. **建议不超过 2-3 层** — 除非有特殊需求

---

## 八、现有文件清单

### 构建文件
- `/home/Projects/Android/UIN_Tool/build.gradle` — 根目录，AGP classpath
- `/home/Projects/Android/UIN_Tool/app/build.gradle` — app 模块，compileSdk、BOM
- `/home/Projects/Android/UIN_Tool/gradle/wrapper/gradle-wrapper.properties` — Gradle 版本

### 组件文件
- `app/src/main/java/com/UIN/Tool/ui/components/unified/UnifiedComponents.kt` — UnifiedCard/Button/Switch
- `app/src/main/java/com/UIN/Tool/ui/components/unified/NeumorphicComponents.kt` — 26 个新拟态组件
- `app/src/main/java/com/UIN/Tool/ui/components/unified/SpecialComponents.kt` — SpeedDial/通知/骨架屏/弹性滚动

### 配置文件
- `app/src/main/java/com/UIN/Tool/utils/UIConfig.kt` — 新拟态/动画速度配置读写
- `app/src/main/java/com/UIN/Tool/ui/screen/manage/UIConfigScreen.kt` — 6 Tab UI 个性化页面
- `app/src/main/res/values/strings.xml` — 中文字符串

### 同步目标
- `/storage/emulated/0/AndroidIDEProjects/UIN_Tool/` — 真实项目目录

---

## 九、第三方库实现方案（参考）

### BoltUIX/NeumorphicCompose（自定义 innerShadow，无新 API）

来源: https://github.com/BoltUIX/NeumorphicCompose

**核心原理：** 使用 `drawWithContent` + `BlurMaskFilter` 自定义 innerShadow，不依赖 Compose 1.9.0。

```kotlin
// 自定义 innerShadow 修饰符（旧方案，兼容性好）
fun Modifier.innerShadow(
    shape: Shape,
    color: Color = Color.Black,
    blur: Dp = 4.dp,
    offsetX: Dp = 2.dp,
    offsetY: Dp = 2.dp,
    spread: Dp = 0.dp
) = drawWithContent {
    drawContent()
    drawIntoCanvas { canvas ->
        val paint = Paint()
        paint.color = color
        canvas.saveLayer(size.toRect(), paint)
        canvas.drawOutline(shadowOutline, paint)
        paint.asFrameworkPaint().apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
            maskFilter = BlurMaskFilter(blur.toPx(), BlurMaskFilter.Blur.NORMAL)
        }
        paint.color = Color.Black
        canvas.translate(offsetX.toPx(), offsetY.toPx())
        canvas.drawOutline(shadowOutline, paint)
        canvas.restore()
    }
}
```

**凸起效果（NeuMorphicUP）：** 两次 innerShadow，亮色 + 暗色
```kotlin
Box(
    modifier = modifier
        .background(Color.Transparent, shape)
        .innerShadow(shape, color = lightColor, blur = padding/2, offsetX = padding, offsetY = padding)
        .innerShadow(shape, color = shadowColor, blur = padding/2, offsetX = -padding, offsetY = -padding)
)
```

**凹陷效果（NeuMorphicDown）：** 交换亮/暗色顺序
```kotlin
Box(
    modifier = modifier
        .background(Color.Transparent, shape)
        .innerShadow(shape, color = shadowColor, blur = padding/2, offsetX = padding, offsetY = padding)
        .innerShadow(shape, color = lightColor, blur = padding/2, offsetX = -padding, offsetY = -padding)
)
```

**开关实现：** 凹陷轨道 + 凸起旋钮
```kotlin
Box(modifier) {
    NeuMorphicDown(shape = CircleShape)  // 凹陷轨道
    Box(align = if (isChecked) CenterEnd else CenterStart) {
        NeuMorphicUP(shape = CircleShape)  // 凸起旋钮
    }
}
```

### 优劣对比

| 方案 | 优点 | 缺点 |
|------|------|------|
| **Compose 1.9.0 dropShadow/innerShadow** | 官方 API，性能好，支持 Brush 渐变 | 需要 BOM 2025.08.00 + compileSdk 36 |
| **自定义 drawWithContent** | 兼容旧版本，无需升级 | 性能较差，不支持 Brush，代码复杂 |
| **sinasamaki Brush 渐变** | 最自然的阴影效果 | 需要 Compose 1.9.0 |

---

## 十、完整新拟态组件清单

基于 HTML 预览和现有代码，需要实现的组件：

### 凸起类（Raised / Extruded）
1. `NeuCard` — 卡片，圆角 14dp
2. `NeuButton` — 按钮，圆角 12dp
3. `NeuFAB` — 浮动操作按钮，圆形
4. `NeuChip` — 标签，圆角 20dp
5. `NeuBadge` — 徽章，圆形/圆角
6. `NeuAvatar` — 头像，圆形
7. `NeuListItem` — 列表项
8. `NeuToolbar` — 工具栏

### 凹陷类（Pressed / Inset / Flat）
9. `NeuTextField` — 输入框，圆角 12dp
10. `NeuSearchBar` — 搜索栏
11. `NeuSwitch` — 开关（凹陷轨道 + 凸起旋钮）
12. `NeuCheckbox` — 复选框
13. `NeuRadio` — 单选按钮
14. `NeuSlider` — 滑块（凹陷轨道 + 凸起滑块）
15. `NeuProgressBar` — 进度条（凹陷轨道 + 凸起进度）
16. `NeuCircularProgress` — 圆形进度
17. `NeuAccordion` — 手风琴（展开/收起）

### 按压过渡类（Press Animation）
18. `NeuPressableCard` — 可按压卡片（凸起 → 凹陷过渡）
19. `NeuPressableButton` — 可按压按钮

### 特殊效果类
20. `NeuStatusIndicator` — 状态指示器（带辉光）
21. `NeuColorDot` — 颜色圆点
22. `NeuEmptyState` — 空状态
23. `NeuAlert` — 提示框
24. `NeuBanner` — 横幅
25. `NeuPagination` — 分页器
26. `NeuStepper` — 步骤指示器

---

## 十一、HTML 预览完整控件与效果清单（3107 行，已逐行验证）

### A. 基础 UI 控件（25 类）

| # | 控件 | HTML class | 对应 Compose | 阴影类型 |
|---|------|-----------|-------------|---------|
| 1 | 按钮（主要） | `.nb.p` | Button | dropShadow → innerShadow(按压) |
| 2 | 按钮（次要） | `.nb` | Button | dropShadow → innerShadow(按压) |
| 3 | 按钮（线框） | `.nb.out` | OutlinedButton | 无阴影 |
| 4 | 按钮（文本） | `.nb.txt` | TextButton | 无阴影 |
| 5 | 按钮（危险） | `.nb.danger` | Button | dropShadow |
| 6 | 按钮（成功） | `.nb.success` | Button | dropShadow |
| 7 | 卡片 | `.nc` | Card | dropShadow(凸起) + 涟漪 |
| 8 | 输入框 | `.ni` | TextField | innerShadow(凹陷) |
| 9 | 搜索栏 | `.search-bar` | TextField | innerShadow(凹陷) |
| 10 | 开关 | `.nt` | Switch | innerShadow(轨道) + dropShadow(旋钮) |
| 11 | 芯片 | `.chip` | FilterChip | dropShadow(凸起) |
| 12 | 滑块 | `input[type="range"]` | Slider | innerShadow(轨道) + dropShadow(滑块) |
| 13 | 复选框 | `.cb` | Checkbox | border + 填充动画 |
| 14 | 单选框 | `.radio` | RadioButton | border + 内圆点 |
| 15 | 折叠面板 | `.accordion` | Card + 动画 | dropShadow(头) |
| 16 | 列表项 | `.li` | ListItem | dropShadow + 光效扫描 |
| 17 | 进度条 | `.progress` | LinearProgressIndicator | innerShadow(轨道) |
| 18 | 环形进度 | `.cp-ring` | CircularProgressIndicator | SVG圆弧 |
| 19 | 分页 | `.pagination` | Row | dropShadow(按钮) |
| 20 | 步进器 | `.stepper` | Row | dropShadow(容器) |
| 21 | 滚动标签 | `.scroll-tab` | ScrollableTabRow | dropShadow |
| 22 | 颜色选择器 | `.cp` | 圆形Box | dropShadow + border |
| 23 | 评分 | `.star` | Row + Icon | scale动画 |
| 24 | 文件上传 | `.file-upload` | Box + border | 虚线border + 背景 |
| 25 | 时间选择器 | `.tp` | Row | dropShadow(按钮) |

### B. 容器/覆盖层控件（8 类）

| # | 控件 | HTML class | 阴影/效果 |
|---|------|-----------|----------|
| 26 | 抽屉 | `.drawer` | backdrop-filter:blur(16px) + 右侧阴影 |
| 27 | 对话框 | `.dlg` | 背景模糊遮罩 + 弹性缩放动画 |
| 28 | Bottom Sheet | `.bs` | 上方阴影 + 手势拖拽关闭 |
| 29 | 下拉菜单 | `.dd-menu` | backdrop-filter:blur(16px) + 阴影 |
| 30 | 横幅 | `.banner` | 无阴影(渐变背景) + 关闭按钮 |
| 31 | 代码块 | `.code-block` | dropShadow(深色背景) |
| 32 | 骨架屏 | `.skel` | shimmer动画(渐变位移) |
| 33 | 空状态 | `.empty` | 卡片包裹 + 居中内容 |

### C. 状态指示器（4 类）

| # | 控件 | 效果 |
|---|------|------|
| 34 | 在线指示器 | 绿色圆点 + glow(box-shadow: 0 0 6px) |
| 35 | 离线指示器 | 灰色圆点，无glow |
| 36 | 忙碌指示器 | 黄色圆点 + glow |
| 37 | 错误指示器 | 红色圆点 + glow |

### D. 特殊控件（5 类）

| # | 控件 | 效果 |
|---|------|------|
| 38 | FAB | 渐变背景 + 阴影 + 滚动隐藏/显示 + hover旋转90° |
| 39 | 快速拨号 | FAB + 展开子项(交错动画) + FAB旋转45° |
| 40 | Toast | 底部弹出 + 自动消失 + 关闭按钮 |
| 41 | 头像 | 圆形 + 渐变背景 + hover缩放 |
| 42 | 头像组 | 重叠排列(margin-left: -8px) |

### E. 复合组件（5 类）

| # | 控件 | 效果 |
|---|------|------|
| 43 | 页面切换 | 标签栏 + 内容区(左右滑动动画) |
| 44 | 滑动切换 | 全屏卡片(左右滑动 + 前一页缩小) |
| 45 | 工具栏 | 水平排列(汉堡+标题+按钮) |
| 46 | 面包屑 | 路径导航 + 分隔符 |
| 47 | 通知面板 | 圆点颜色区分 + 时间戳 |

### F. 全局效果（12 种）

| # | 效果 | 实现方式 | Compose 等价 |
|---|------|---------|-------------|
| 1 | 新拟态凸起 | `box-shadow: var(--shadow-md)` | dropShadow × 2 |
| 2 | 新拟态凹陷 | `box-shadow: inset 3px 3px 6px` | innerShadow × 2 |
| 3 | 按压过渡 | `:active { box-shadow: inset; transform: scale(.96) }` | animateable innerShadow |
| 4 | 涟漪效果 | `createRipple()` + `ncRipple`动画 | Modifier.clickable + ripple |
| 5 | 背景涟漪 | 点击空白区域的 `bgRip` 动画 | Modifier.pointerInput |
| 6 | 淡入动画 | IntersectionObserver + `.fade-in.visible` | animateItem() / LaunchedEffect |
| 7 | 骨架屏 | `shimmer` 渐变位移动画 | infiniteTransition |
| 8 | 滚动隐藏FAB | scroll事件判断方向 | LazyListState |
| 9 | 长按菜单 | 600ms定时器 + 动态创建DOM | LongClickGestureDetector |
| 10 | 列表项光效 | `::before` 左→右扫描 | Modifier.drawBehind |
| 11 | 汉堡菜单动画 | 三线→X变换 | animateFloatAsState |
| 12 | 背景模糊 | `backdrop-filter: blur(16px)` | Modifier.blur (Compose 1.9.0) |

### G. 动画关键帧（9 种）

| # | 动画名 | 效果 | CSS |
|---|--------|------|-----|
| 1 | `fd` | 向下淡入 | opacity 0→1, translateY -20→0 |
| 2 | `fu` | 向上淡入 | opacity 0→1, translateY 20→0 |
| 3 | `su` | 滑入 | opacity 0→1, translateY 30→0 |
| 4 | `dlgIn` | 弹性进入 | scale(.85)→1, translateY 20→0 |
| 5 | `fi` | 淡入 | opacity 0→1 |
| 6 | `scaleIn` | 水平缩放 | scaleX(0)→1 |
| 7 | `shimmer` | 骨架屏 | background-position 200%→-200% |
| 8 | `bgRip` | 背景涟漪 | scale(0)→3, opacity .4→0 |
| 9 | `ncRipple` | 卡片涟漪 | scale(0)→4, opacity .5→0 |

### H. 手势交互（6 种）

| # | 手势 | 组件 | 效果 |
|---|------|------|------|
| 1 | 点击 | 卡片 | 涟漪 + 反馈 |
| 2 | 长按(600ms) | 卡片 | 弹出上下文菜单 |
| 3 | 拖拽 | Bottom Sheet | 手势拖拽关闭 |
| 4 | 横滑 | 滑动切换 | 页面切换 |
| 5 | 滚动 | 主容器 | FAB隐藏/显示 + 淡入触发 |
| 6 | 长按重复 | 步进器 | 120ms间隔持续增减 |

---

## 十二、关键注意事项

1. **modifier 顺序至关重要** — dropShadow 在 background 前，innerShadow 在 background 后
2. **元素背景必须与页面背景一致** — 新拟态的核心原则：元素和背景同色
3. **性能** — 每个 dropShadow/innerShadow 都是独立图形层，建议不超过 2-3 层
4. **动画** — 使用 lambda 版本 `dropShadow(shape) { ... }` 更适合动画
5. **深色模式** — 需要调整亮/暗阴影颜色和透明度
6. **按压状态** — 从凸起过渡到凹陷，交换 dropShadow 和 innerShadow

---

## 十三、边缘拉伸效果（UI.html 专属）

### HTML 实现分析（L396-463）

```javascript
// 状态
eState = { startY: 0, stretch: 0, locked: false, canStretch: false, dir: '' };
const maxS = 50; // 最大拉伸像素

// touchstart: 检测是否在顶部/底部
if (sw.scrollTop <= 0) eState.canStretch = 'top';
else if (sw.scrollTop + sw.clientHeight >= sw.scrollHeight - 1) eState.canStretch = 'bottom';

// touchmove: 拉伸计算
stretch = Math.min(dy * 0.35, maxS);  // 拉伸量 = 手指位移 × 0.35，上限 50px
sw.style.transform = 'scaleY(' + (1 + stretch * 0.002) + ')';  // 内容缩放
topBar.style.transform = 'scaleX(' + (stretch / maxS) + ')';   // 顶部指示条

// touchend: 弹性回弹
sw.style.transition = 'transform .5s cubic-bezier(.34, 1.56, .64, 1)';  // 弹簧回弹
sw.style.transform = '';  // 清除 transform 回到原位
```

### 核心效果

| 元素 | 拉伸时 | 松手后 |
|------|--------|--------|
| 内容区 `#scrollWrap` | `scaleY(1.0 ~ 1.1)` + 原点在顶/底 | 弹簧动画回弹到 `scaleY(1)` |
| 顶部指示条 `#stretchTop` | `scaleX(0 → 1)`，4px 高，蓝色 | `scaleX(0)` 淡出 |
| 底部指示条 `#stretchBot` | `scaleX(0 → 1)`，4px 高，蓝色 | `scaleX(0)` 淡出 |

### 关键参数

- `scaleY(1 + stretch * 0.002)`: 最大拉伸 10%（50 × 0.002 = 0.1）
- `dy * 0.35`: 手指位移的 35% 转化为拉伸量
- `maxS = 50`: 最大拉伸像素 50px
- `cubic-bezier(.34, 1.56, .64, 1)`: 弹簧回弹缓动

### Compose 实现方案

```kotlin
@Composable
fun StretchOverscrollEffect() {
    val offsetY = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    // 检测是否在顶部/底部
                    // 记录起始 Y
                    // 计算拉伸量
                    // 松手后弹簧回弹
                }
            }
            .graphicsLayer {
                // 拉伸时 scaleY
                scaleY = 1f + (offsetY.value / size.height) * 0.1f
                transformOrigin = if (isAtTop) 
                    TransformOrigin(0.5f, 0f)  // 顶部为原点
                else 
                    TransformOrigin(0.5f, 1f)  // 底部为原点
            }
    ) {
        // 内容
    }
}
```

**核心 API:**
- `Animatable` 控制拉伸量
- `graphicsLayer { scaleY }` GPU 级别缩放
- `TransformOrigin` 控制缩放原点
- `spring(DampingRatioLowBouncy)` 弹簧回弹

---

## 十四、执行顺序

1. ✅ 研究 Compose UI 1.9.0 新 API
2. ✅ 研究第三方库实现方案
3. ✅ 记录笔记到 Notes.md
4. ✅ 验证 HTML 预览全部 47 类控件 + 12 种效果 + 9 种动画 + 6 种手势
5. 升级构建系统（AGP 8.9.1, compileSdk 36, BOM 2025.08.00）
6. 编译验证基础升级无错
7. 创建 NeuModifiers.kt — dropShadow/innerShadow 辅助函数
8. 重写 UnifiedComponents 中的 neumorphism（Card、Button、Switch）
9. 重写 NeumorphicComponents 全部组件（26 个）
10. UIConfigScreen 预览增强
11. 同步到真实项目
12. 最终编译验证
