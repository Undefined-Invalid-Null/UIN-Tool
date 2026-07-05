# proguard-rules.pro

# ==================== 保留 Compose ====================
-keep class androidx.compose.** { *; }
-keep class androidx.compose.runtime.** { *; }
-keep class androidx.compose.ui.** { *; }
-keep class androidx.compose.material3.** { *; }

# ==================== 保留 Kotlin 反射 ====================
-keep class kotlin.reflect.** { *; }
-keep class kotlin.Metadata { *; }

# ==================== 保留插件相关类 ====================
-keep class com.UIN.Tool.plugin.** { *; }
-keep class com.UIN.Tool.domain.model.PluginInfo { *; }
-keep class com.UIN.Tool.utils.Constants { *; }

# ==================== 保留 ECJ 编译器 ====================
-keep class org.eclipse.jdt.** { *; }
-dontwarn org.eclipse.jdt.**

# ==================== 保留 Janino ====================
-keep class org.codehaus.janino.** { *; }
-keep class org.codehaus.commons.** { *; }

# ==================== 保留 R8 ====================
-keep class com.android.tools.r8.** { *; }

# ==================== 保留 OKHttp ====================
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**

# ==================== 保留 Retrofit ====================
-keep class retrofit2.** { *; }
-keep interface retrofit2.** { *; }
-dontwarn retrofit2.**
-keep class com.google.gson.** { *; }

# ==================== 保留 Markdown 渲染 ====================
-keep class org.commonmark.** { *; }

# ==================== 保留 Sora Editor ====================
-keep class io.github.rosemoe.sora.** { *; }

# ==================== 保留无障碍服务 ====================
-keep class com.UIN.Tool.core.accessibility.UinAccessibilityService { *; }

# ==================== 保留小部件 ====================
-keep class com.UIN.Tool.widget.** { *; }

# ==================== 保留 Activity ====================
-keep class com.UIN.Tool.**Activity { *; }
-keep class com.UIN.Tool.SplashActivity { *; }
-keep class com.UIN.Tool.MainActivity { *; }

# ==================== 保留 ViewModel ====================
-keep class com.UIN.Tool.ui.viewmodel.** { *; }

# ==================== 保留 Compose 可组合函数 ====================
-keepclassmembers class * {
    @androidx.compose.runtime.Composable *;
}

# ==================== 保留注解 ====================
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod

# ==================== 禁用警告 ====================
-dontwarn kotlinx.coroutines.**
-dontwarn org.jetbrains.annotations.**
-dontwarn javax.lang.model.**
-dontwarn com.sun.**
-dontwarn sun.**

# ==================== 保留枚举 ====================
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ==================== 保留 Parcelable ====================
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# ==================== 移除日志代码（Release 模式） ====================
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
}
-assumenosideeffects class com.UIN.Tool.log.Logger {
    public static *** d(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
    public static *** success(...);
    public static *** action(...);
    public static *** param(...);
    public static *** enter(...);
    public static *** exit(...);
}