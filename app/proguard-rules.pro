# ============================================================
# 解决 R8 构建错误 - 缺失类忽略规则（必须放在最前面）
# ============================================================

# 忽略缺失的类（这些类在运行时不需要）
-dontwarn java.lang.invoke.MethodHandleProxies
-dontwarn javax.lang.model.element.Modifier
-dontwarn kotlin.Cloneable$DefaultImpls

# 忽略其他可能缺失的类
-dontwarn com.github.luben.zstd.**
-dontwarn org.brotli.dec.**
-dontwarn org.objectweb.asm.**
-dontwarn org.apache.commons.compress.**

# 忽略所有警告（作为最后手段）
-ignorewarnings

# 保留这些类即使它们不存在
-keep class java.lang.invoke.** { *; }
-keep class javax.lang.model.** { *; }
-keep class kotlin.Cloneable** { *; }

# ============================================================
# 插件系统 - 必须保留所有插件相关类和接口
# ============================================================

# 保留所有插件接口和实现类（使用注解标记）
-keep public interface com.UIN.Tool.plugin.PluginInterface
-keep public class * implements com.UIN.Tool.plugin.PluginInterface {
    public <init>();
    public *;
}

# 保留所有插件相关的类
-keep class com.UIN.Tool.core.plugin.** { *; }
-keep class com.UIN.Tool.plugin.** { *; }

# 保留所有插件可能继承/实现的类
-keep class * extends android.app.Activity { *; }
-keep class * extends android.app.Service { *; }
-keep class * extends android.content.BroadcastReceiver { *; }
-keep class * extends android.content.ContentProvider { *; }

# ============================================================
# WebView 与 JavaScript 接口 - JS 调用的方法必须保留
# ============================================================

# 保留所有 @JavascriptInterface 方法
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# 保留 PluginWebInterface 和 PluginJSInterface
-keep class com.UIN.Tool.core.plugin.PluginWebInterface { *; }
-keep class com.UIN.Tool.plugin.PluginJSInterface { *; }
-keepclassmembers class com.UIN.Tool.plugin.PluginJSInterface {
    @android.webkit.JavascriptInterface *;
}

# ============================================================
# 反射调用的类 - 必须保留完整类名和方法名
# ============================================================

# 保留所有插件信息类
-keep class com.UIN.Tool.domain.model.PluginInfo { *; }
-keep class com.UIN.Tool.domain.model.RepoPluginInfo { *; }
-keep class com.UIN.Tool.domain.model.ReleaseInfo { *; }
-keep class com.UIN.Tool.domain.model.BackupInfo { *; }
-keep class com.UIN.Tool.domain.model.MirrorItem { *; }

# ============================================================
# DexClassLoader 相关
# ============================================================

# 保留所有可能被 DexClassLoader 加载的类
-keep class * {
    public <init>();
}

# 保留所有 Kotlin 伴生对象（反射可能需要）
-keepclassmembers class * {
    public static final ** Companion;
}

# ============================================================
# 序列化 / JSON 解析
# ============================================================

# 保留 Gson/JSONObject 解析的字段
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keep class com.google.gson.** { *; }
-keep class org.json.** { *; }

# ============================================================
# 保留所有 Activity/Service/Receiver/Provider（清单中声明的）
# ============================================================

-keep public class * extends android.app.Activity
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider
-keep public class * extends android.appwidget.AppWidgetProvider
-keep public class * extends android.accessibilityservice.AccessibilityService

# ============================================================
# 保留所有 Android 组件子类
# ============================================================

-keep public class * extends android.app.Application
-keep public class * extends android.app.Fragment
-keep public class * extends androidx.fragment.app.Fragment

# ============================================================
# 保留所有自定义 View
# ============================================================

-keep public class * extends android.view.View {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
    public void set*(...);
}

# ============================================================
# Kotlin 反射相关
# ============================================================

-keep class kotlin.reflect.** { *; }
-keep class kotlin.Metadata { *; }
-keepclassmembers class kotlin.Metadata {
    public *;
}

# ============================================================
# 保留所有枚举（插件可能使用）
# ============================================================

-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ============================================================
# 保留所有注解（运行时保留）
# ============================================================

-keepattributes *Annotation*
-keepattributes Signature
-keepattributes Exceptions

# ============================================================
# 保留 Kotlin 默认构造函数
# ============================================================

-keepclassmembers class * {
    public <init>();
}

# ============================================================
# 保留行号信息（便于调试）
# ============================================================

-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ============================================================
# 保留所有 Native 方法
# ============================================================

-keepclasseswithmembernames class * {
    native <methods>;
}

# ============================================================
# 保留所有 Parcelable 实现
# ============================================================

-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# ============================================================
# 保留 Compose 相关（如果使用了 Jetpack Compose）
# ============================================================

-keep class androidx.compose.** { *; }
-keep class androidx.compose.runtime.** { *; }
-keep class androidx.compose.ui.** { *; }
-keep class androidx.compose.material.** { *; }
-keep class androidx.compose.foundation.** { *; }

# ============================================================
# 保留 Material Design 相关
# ============================================================

-keep class com.google.android.material.** { *; }
-dontwarn com.google.android.material.**

# ============================================================
# 保留 AndroidX 核心库
# ============================================================

-keep class androidx.** { *; }
-dontwarn androidx.**