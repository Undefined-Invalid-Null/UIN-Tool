// app/src/main/java/com/UIN/Tool/plugin/PluginEditorApi.java
package com.UIN.Tool.plugin;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;

import java.io.File;

/**
 * 6.2.0 批次53：**原生插件专用**的宿主代码编辑器 API（不经过 WebView JS 桥）。
 *
 * 本类同时是 Web 侧 {@code UINPlugin.callHost('openEditor', …)} 的落地实现 ——
 * 两套入口（原生静态调用 / Web 桥）、一份实现（都从这里发 Intent）。
 *
 * 分发方式：本类是**唯一**被追加进 host-sdk.jar 的类型（{@code app/libs/host-sdk.jar}
 * 与 {@code assets/compiler/host-sdk.jar} 同步更新），原生插件用它做编译期签名；
 * 运行期由宿主 APK 里的同名类接管（插件 DexClassLoader 的父加载器优先）。
 * 之所以不往 {@code PluginInterface} 里加方法：接口加方法会让老插件 dex 在宿主回调时
 * 抛 AbstractMethodError（见 PluginHostActivity 的反射兼容逻辑），独立静态类零风险。
 *
 * 类名经 {@link Class#forName} 解析而不用类字面量 —— 让本类的 class 文件不依赖
 * 编辑器 Activity 的类型，宿主与 SDK jar 两端都能独立编译。
 *
 * 用法（原生插件，onCreateView 收到的 context 即 PluginContext）：
 * <pre>
 * PluginEditorApi.openEditor(context, "/sdcard/notes/a.txt");   // 文件 → 直接打开
 * PluginEditorApi.openEditor(context, "/sdcard/myproject");     // 目录 → 浏览模式（树 + 首个文本文件）
 * PluginEditorApi.openFile(context, "/sdcard/myproject/a.md", "/sdcard/myproject"); // 文件 + 目录树
 * PluginEditorApi.openProject(context, "/sdcard/myproject");    // 仅目录浏览
 * </pre>
 */
public final class PluginEditorApi {

    private PluginEditorApi() {}

    /** 代码编辑器 Activity 全名（字符串引用，见类注释）。 */
    private static final String EDITOR_ACTIVITY = "com.UIN.Tool.ui.screen.dev.CodeEditorActivity";

    /**
     * Intent.FLAG_ACTIVITY_NEW_TASK（0x10000000）—— 用字面量内联而非引用常量：
     * SDK 端用桩类编译，字面量保证桩与真机两侧生成的字节码完全一致，不存在常量漂移。
     */
    private static final int FLAG_ACTIVITY_NEW_TASK = 0x10000000;

    /**
     * 打开宿主代码编辑器：path 是文件 → 打开该文件；是目录 → 以浏览模式打开项目树。
     *
     * @return true = Intent 已发出；false = 参数为空或启动失败
     */
    public static boolean openEditor(Context context, String path) {
        if (context == null || path == null || path.length() == 0) return false;
        File f = new File(path);
        if (f.isDirectory()) return openProject(context, path);
        return openFile(context, path, null);
    }

    /**
     * 打开单个文件；projectDir 非空时同时挂上该目录的目录树（左侧可切换同项目其他文件）。
     */
    public static boolean openFile(Context context, String filePath, String projectDir) {
        if (context == null || filePath == null || filePath.length() == 0) return false;
        // 批次53修：误把**目录**当文件传入时降级为浏览模式打开（否则编辑器只挂树无文档托底，
        // 停在「未打开文件」空态，还误报「文件不存在」）。
        if (new File(filePath).isDirectory()) return openProject(context, filePath);
        return launch(context, filePath,
                (projectDir == null || projectDir.length() == 0) ? null : projectDir,
                false);
    }

    /**
     * 浏览模式打开项目目录：只挂目录树并自动打开首个文本文件
     * （区别于向导的 project_dir —— 向导会收集打开目录下**全部**文本文件）。
     */
    public static boolean openProject(Context context, String dirPath) {
        if (context == null || dirPath == null || dirPath.length() == 0) return false;
        return launch(context, null, dirPath, true);
    }

    private static boolean launch(Context context, String filePath,
                                  String projectDir, boolean browseOnly) {
        try {
            Class<?> cls = Class.forName(EDITOR_ACTIVITY);
            Intent intent = new Intent(context, cls);
            if (filePath != null) intent.putExtra("file_path", filePath);
            if (projectDir != null) {
                intent.putExtra("project_dir", projectDir);
                intent.putExtra("browse_only", browseOnly);
            }
            // PluginContext 之类包装层要拆到底层 Activity：从 Activity 启动才进同一任务栈
            // （编辑器返回键可回到插件页）；真·非 Activity 上下文才补 NEW_TASK。
            if (!isActivityContext(context)) intent.addFlags(FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            return true;
        } catch (Throwable t) {
            // 编辑器不存在（宿主版本过旧）/ 上下文不可用 —— 静默失败，由调用方决定提示。
            return false;
        }
    }

    /** 拆 ContextWrapper 链找 Activity（最多 16 层，防环）。 */
    private static boolean isActivityContext(Context context) {
        Context c = context;
        int guard = 0;
        while (c instanceof ContextWrapper && !(c instanceof Activity) && guard++ < 16) {
            c = ((ContextWrapper) c).getBaseContext();
        }
        return c instanceof Activity;
    }
}
