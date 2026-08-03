# ============================================================
# R8 构建错误修复 - 缺失类忽略规则（必须放在最前面）
# ============================================================

-dontwarn java.lang.invoke.MethodHandleProxies
-dontwarn javax.lang.model.element.Modifier
-dontwarn kotlin.Cloneable$DefaultImpls

# 其他可能缺失的类
-dontwarn com.github.luben.zstd.**
-dontwarn org.brotli.dec.**
-dontwarn org.objectweb.asm.**
-dontwarn org.apache.commons.compress.**

# 忽略所有警告（作为最后手段）
-ignorewarnings

# ============================================================
# 空壳插件宿主占位实现（DexClassLoader 运行时加载外部 dex）
# ============================================================
# 插件通过 PluginManager 用 DexClassLoader 加载外部 plugin.dex，
# 以 loadClass(mainClass).newInstance() as PluginInterface 实例化，
# 并通过 WebView JS 桥 / JSON 配置名驱动调用。因此插件接口、宿主
# 占位类、JS 桥方法都必须原样保留，否则 R8 混淆后运行时会崩溃。

# 插件接口契约（外部 dex 编译依赖 & 运行时转型目标，含全部方法签名）
-keep public interface com.UIN.Tool.plugin.PluginInterface { public *; }
-keep public interface com.UIN.Tool.core.plugin.PluginInterface { public *; }

# 宿主占位实现：插件管理器、宿主 Activity、插件上下文、JS 桥、
# Web 代理、后端/权限/proot 容器、广播接收器等全部按原名保留
-keep class com.UIN.Tool.plugin.** { *; }
-keep class com.UIN.Tool.core.plugin.** { *; }

# WebView JS 桥 @JavascriptInterface 方法（JS 按方法名调用，必须保留）
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# ============================================================
# 插件 JSON 配置 / 仓库数据解析模型（按字段名解析）
# ============================================================

-keep class com.UIN.Tool.domain.model.PluginInfo { *; }
-keep class com.UIN.Tool.domain.model.RepoPluginInfo { *; }
-keep class com.UIN.Tool.domain.model.ReleaseInfo { *; }
-keep class com.UIN.Tool.domain.model.BackupInfo { *; }
-keep class com.UIN.Tool.domain.model.MirrorItem { *; }

# ============================================================
# Android 清单组件（AGP 会自动保留，此处保底）
# ============================================================

-keep public class * extends android.app.Activity
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider
-keep public class * extends android.app.Application

# 自定义 View（布局按类名 inflate）
-keep public class * extends android.view.View {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

# ============================================================
# R8 基础保留
# ============================================================

-keepattributes *Annotation*,Signature,Exceptions,InnerClasses,EnclosingMethod

-keepclasseswithmembernames class * {
    native <methods>;
}

-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# 行号信息（便于崩溃堆栈定位）
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
