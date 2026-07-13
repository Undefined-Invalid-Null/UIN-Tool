# UIN Tool 开发规范

> 本文档定义了 UIN Tool 项目的代码规范、Git 提交规范、分支管理策略等，所有贡献者请严格遵守。

## 目录

- [一、代码规范](#一代码规范)
- [二、命名规范](#二命名规范)
- [三、Git 提交规范](#三git-提交规范)
- [四、分支管理](#四分支管理)
- [五、版本规范](#五版本规范)
- [六、注释规范](#六注释规范)
- [七、资源文件规范](#七资源文件规范)
- [八、插件开发规范](#八插件开发规范)

---

## 一、代码规范

### 1.1 通用规范

| 规则 | 说明 |
|------|------|
| 缩进 | 4 个空格，禁止使用 Tab |
| 行宽 | 不超过 120 字符 |
| 文件编码 | UTF-8 |
| 换行符 | LF (Unix 风格) |
| 花括号 | 不换行风格（K&R） |

### 1.2 Java 代码规范

```java
// ✅ 正确：花括号不换行
public void method() {
    if (condition) {
        doSomething();
    }
}

// ❌ 错误：花括号换行
public void method() 
{
    if (condition) 
    {
        doSomething();
    }
}
```

导入顺序：

```java
1. android 包
2. androidx 包
3. 第三方库包 (com.google, com.squareup 等)
4. com.UIN.Tool 内部包
5. java/javax 包

// 各组之间空一行
```

访问修饰符顺序：

```java
public → protected → private
abstract → static → final → transient → volatile → synchronized → native → strictfp
```

1.3 XML 规范

```xml
<!-- ✅ 正确 -->
<LinearLayout
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="vertical"
    android:padding="16dp" />

<!-- ❌ 错误：属性在同一行 -->
<LinearLayout android:layout_width="match_parent" android:layout_height="wrap_content" />
```

属性顺序：

1. android:id
2. android:layout_width / android:layout_height
3. android:layout_* (layout 属性)
4. android:background / android:padding*
5. 其他属性
6. tools:* / app:*

1.4 格式化配置

项目根目录应包含以下配置文件：

.editorconfig：

```ini
root = true

[*]
charset = utf-8
end_of_line = lf
insert_final_newline = true
trim_trailing_whitespace = true
indent_style = space
indent_size = 4

[*.xml]
indent_size = 4

[*.json]
indent_size = 2

[*.md]
trim_trailing_whitespace = false
```

checkstyle.xml（可选，用于 CI 检查）：

```xml
<?xml version="1.0"?>
<!DOCTYPE module PUBLIC "-//Checkstyle//DTD Checkstyle Configuration 1.3//EN"
    "https://checkstyle.org/dtds/configuration_1_3.dtd">
<module name="Checker">
    <module name="TreeWalker">
        <module name="Indentation">
            <property name="basicOffset" value="4"/>
            <property name="braceAdjustment" value="0"/>
            <property name="caseIndent" value="4"/>
        </module>
        <module name="LeftCurly">
            <property name="option" value="eol"/>
        </module>
        <module name="RightCurly">
            <property name="option" value="same"/>
        </module>
        <module name="WhitespaceAround"/>
        <module name="NoWhitespaceBefore"/>
        <module name="WhitespaceAfter"/>
    </module>
    <module name="FileTabCharacter"/>
    <module name="NewlineAtEndOfFile"/>
</module>
```

---

二、命名规范

2.1 Java 类命名

类型 命名规则 示例
Activity 功能名 + Activity MainActivity, PluginManageActivity
Fragment 功能名 + Fragment ToolsFragment, DevFragment
Adapter 功能名 + Adapter PluginListAdapter, CategoryAdapter
Service 功能名 + Service UinAccessibilityService
Util 功能名 + Utils LogUtils, FileUtils
接口 形容词或名词 + 接口 PluginInterface, OnDownloadListener
抽象类 Abstract + 功能名 BaseActivity, BaseFragment
数据类 功能名 + Info/Bean PluginInfo, RemotePluginInfo

2.2 变量命名

类型 命名规则 示例
成员变量 camelCase pluginManager, workFolder
静态常量 UPPER_SNAKE_CASE MAX_LOG_SIZE, DEFAULT_TIMEOUT
局部变量 camelCase tempFile, pluginId
控件变量 控件类型缩写 + 功能名 btnSubmit, tvName, rvPlugins

控件前缀规范：

控件 前缀 示例
TextView/EditText tv / et tvTitle, etSearch
Button btn btnSubmit, btnCancel
ImageView iv ivIcon, ivAvatar
RecyclerView rv rvPlugins, rvList
LinearLayout ll llContainer
RelativeLayout rl rlParent
ProgressBar pb pbLoading
CheckBox cb cbSelect
Spinner spinner spinnerCategory

2.3 资源文件命名

类型 命名规则 示例
布局文件 组件类型_功能名 activity_main, fragment_tools, item_plugin
Drawable ic_功能名 ic_search, ic_delete, ic_extension
颜色 语义化名称 primary, text_primary, background
字符串 用途描述 title_plugin_manage, btn_import
动画 动作_方向 slide_in_right, fade_out

2.4 包结构

```
com.UIN.Tool/
├── MainActivity.java              # 主入口
├── UinApplication.java            # Application
├── compiler/                      # 编译相关
├── plugin/                        # 插件核心
│   ├── PluginManager.java         # 插件管理器
│   ├── PluginInfo.java            # 插件信息
│   ├── PluginInterface.java       # 插件接口
│   └── PluginContext.java         # 插件上下文
├── service/                       # 服务
│   └── UinAccessibilityService.java
├── ui/                            # UI 界面
│   ├── common/                    # 通用基类
│   ├── dev/                       # 开发界面
│   ├── manage/                    # 管理界面
│   ├── plugin/                    # 插件管理
│   ├── tools/                     # 工具界面
│   ├── backup/                    # 备份恢复
│   ├── log/                       # 日志界面
│   ├── permission/                # 权限管理
│   ├── settings/                  # 设置界面
│   └── docs/                      # 文档界面
├── utils/                         # 工具类
└── widget/                        # 小部件
```

---

三、Git 提交规范

3.1 提交信息格式

```
<type>(<scope>): <subject>

[body]

[footer]
```

3.2 Type 类型

Type 说明 示例
feat 新功能 feat(plugin): 添加插件批量导入功能
fix Bug 修复 fix(compiler): 修复 DEX 编译路径错误
docs 文档更新 docs: 更新 README 安装说明
style 代码格式（不影响功能） style: 统一代码缩进为 4 空格
refactor 重构 refactor(plugin): 重构 PluginManager 加载逻辑
perf 性能优化 perf: 优化插件列表加载速度
test 测试相关 test: 添加 PluginInfo 单元测试
chore 构建/工具配置 chore: 升级 Gradle 到 8.0
revert 回滚 revert: 回滚 feat(plugin) 提交

3.3 Scope 范围

Scope 说明
plugin 插件相关功能
compiler 编译相关
ui 界面相关
widget 小部件
permission 权限
backup 备份恢复
log 日志
utils 工具类
build 构建配置

3.4 提交示例

```bash
# 新功能
git commit -m "feat(store): 添加远程插件商店功能"

# Bug 修复
git commit -m "fix(plugin): 修复 Web 插件加载失败时崩溃的问题"

# 带详细说明
git commit -m "feat(compiler): 支持 Web 插件热更新

- 添加 Web 插件文件监控
- 修改后自动刷新 WebView
- 添加 Web 插件调试模式

Closes #123"

# 文档更新
git commit -m "docs: 添加开发规范文档"
```

3.5 提交频率

· 每次提交应该是一个逻辑上完整的变更
· 避免一个提交包含多个不相关的修改
· 避免“临时保存”、“WIP”等无意义提交
· 鼓励小步提交，便于 Code Review

---

四、分支管理

4.1 分支模型

```
main          # 生产分支，仅接受 release 合并
├── develop   # 开发主分支
├── feature/* # 功能分支（从 develop 切出）
├── fix/*     # 修复分支（从 develop 或 main 切出）
└── release/* # 发布分支（从 develop 切出）
```

4.2 分支命名

分支类型 命名格式 示例
功能分支 feature/功能名 feature/plugin-store
修复分支 fix/问题描述 fix/webview-crash
发布分支 release/版本号 release/v1.2.0

4.3 工作流程

1. 新功能：从 develop 切出 feature/xxx → 开发 → 提交 PR → 合并回 develop
2. Bug 修复：从 develop 切出 fix/xxx → 修复 → 提交 PR → 合并回 develop
3. 紧急修复：从 main 切出 fix/xxx → 修复 → 提交 PR → 合并回 main 和 develop
4. 版本发布：从 develop 切出 release/x.x.x → 测试 → 合并到 main 并打 Tag

4.4 PR 规范

PR 标题格式与 commit 相同：<type>(<scope>): <subject>

PR 描述应包含：

```markdown
## 变更说明
（描述本次 PR 做了什么）

## 测试情况
- [ ] 本地测试通过
- [ ] 单元测试通过

## 相关 Issue
Closes #123
```

---

五、版本规范

5.1 版本号格式

```
主版本号.次版本号.修订号

示例：1.2.3
- 1：主版本号（重大架构变更，不兼容更新）
- 2：次版本号（新功能，向后兼容）
- 3：修订号（Bug 修复，小改动）
```

5.2 版本号与 versionCode 对应

versionName versionCode 说明
1.0.0 10000 正式版，主版本 1，次版本 0，修订 0
1.2.0 10200 新增功能
1.2.1 10201 Bug 修复
2.0.0 20000 重大更新

versionCode 计算公式：主版本 * 10000 + 次版本 * 100 + 修订

5.3 发布 Tag

```
v1.2.0
v1.2.1
v2.0.0-beta.1  # 测试版
```

---

六、注释规范

6.1 类注释

每个类都必须有 Javadoc 注释：

```java
/**
 * 插件管理器
 * 
 * <p>负责插件的安装、卸载、加载和生命周期管理。
 * 支持原生插件 (DEX) 和 Web 插件 (WebView) 两种类型。
 * 
 * @author UIN Team
 * @since 1.0.0
 */
public class PluginManager {
    // ...
}
```

6.2 方法注释

```java
/**
 * 安装插件
 *
 * @param tpkFilePath TPK 文件路径
 * @param originalFileName 原始文件名
 * @return 安装成功返回 PluginInfo，失败返回 null
 */
public PluginInfo installPlugin(String tpkFilePath, String originalFileName) {
    // ...
}
```

6.3 复杂逻辑行内注释

```java
// 检查是否为 Web 插件，Web 插件不需要 DEX 文件
if ("web".equals(pluginInfo.uiType)) {
    // 跳过 DEX 验证
    continue;
}
```

6.4 TODO/FIXME 注释

```java
// TODO(v2.0): 支持增量更新
// FIXME: 内存泄漏，需要检查 WebView 销毁逻辑
```

---

七、资源文件规范

7.1 strings.xml 规范

```xml
<resources>
    <!-- ==================== 页面标题 ==================== -->
    <string name="title_plugin_manage">插件管理</string>
    <string name="title_permission_manager">权限管理</string>
    
    <!-- ==================== 按钮文本 ==================== -->
    <string name="btn_import">导入</string>
    <string name="btn_export">导出</string>
    
    <!-- ==================== 提示消息 ==================== -->
    <string name="toast_import_success">导入成功：%s</string>
    <string name="toast_import_failed">导入失败：%s</string>
</resources>
```

7.2 colors.xml 规范

```xml
<resources>
    <!-- ==================== 主色调 ==================== -->
    <color name="primary">#FF37474F</color>
    <color name="primary_dark">#FF263238</color>
    
    <!-- ==================== 文本颜色 ==================== -->
    <color name="text_primary">#FF212121</color>
    <color name="text_secondary">#FF757575</color>
    
    <!-- ==================== 背景色 ==================== -->
    <color name="background">#FFF8F8F8</color>
</resources>
```

7.3 dimens.xml 规范

```xml
<resources>
    <!-- ==================== 间距 ==================== -->
    <dimen name="spacing_sm">8dp</dimen>
    <dimen name="spacing_md">12dp</dimen>
    <dimen name="spacing_lg">16dp</dimen>
    
    <!-- ==================== 圆角 ==================== -->
    <dimen name="radius_sm">4dp</dimen>
    <dimen name="radius_md">8dp</dimen>
    <dimen name="radius_lg">12dp</dimen>
    
    <!-- ==================== 字体 ==================== -->
    <dimen name="text_caption">10sp</dimen>
    <dimen name="text_body">12sp</dimen>
    <dimen name="text_title">16sp</dimen>
</resources>
```

---

八、插件开发规范

此部分供插件开发者参考

8.1 plugin.json 规范

```json
{
  "pluginId": "com.example.myplugin",
  "version": 1,
  "versionName": "1.0.0",
  "minHostVersion": 1,
  "name": "插件名称",
  "author": "作者名",
  "description": "插件描述，不超过200字",
  "icon": "icon.png",
  "mainClass": "com.example.MainPlugin",
  "uiType": "native",
  "apiLevel": 21,
  "category": "工具"
}
```

字段说明：

字段 类型 必填 说明
pluginId String ✅ 唯一标识，格式如 com.example.xxx
version int ✅ 版本号，递增整数
versionName String ✅ 版本名称，如 1.0.0
minHostVersion int ✅ 最低宿主版本
name String ✅ 显示名称
author String ✅ 作者
description String ❌ 描述
icon String ❌ 图标文件名，默认 icon.png
mainClass String ✅(原生) 入口类全限定名
uiType String ✅ native 或 web
category String ❌ 分类，默认 未分类

8.2 原生插件接口规范

```java
public class MainPlugin implements PluginInterface {
    
    @Override
    public View onCreateView(Context context, ViewGroup container, Bundle savedInstanceState) {
        // 必须返回一个有效的 View
        // 不能返回 null
    }
    
    @Override
    public void onResume() { }
    
    @Override
    public void onPause() { }
    
    @Override
    public void onDestroy() { }
    
    @Override
    public boolean onBackPressed() {
        return false; // true 表示消费了返回事件
    }
    
    @Override
    public Bundle onSaveInstanceState() {
        return null;
    }
}
```

8.3 Web 插件规范

· 入口文件必须是 web/index.html
· 可通过 UINPlugin JavaScript 对象调用宿主功能
· 支持的生命周期事件：resume、pause、destroy

---

附录

A. 工具推荐

工具 用途
Android Studio 开发 IDE
Spotless 代码格式化插件
GitLens Git 增强插件
SonarLint 代码质量检查

B. 检查清单（提交前）

· 代码已格式化（使用 Spotless 或 IDE 格式化）
· 无未使用的 import
· 无 Lint 警告
· Commit message 符合规范
· 已测试功能正常
· 已更新相关文档

---

最后更新：2024-01-15
维护者：UIN Team
