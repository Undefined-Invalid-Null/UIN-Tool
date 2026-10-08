# ============================================================
# 强制 Guava 库打包在主 DEX
# ============================================================
-keep class com.google.common.** { *; }
-keep class com.google.common.io.** { *; }
-keep class com.google.common.io.RecursiveDeleteOption { *; }
-keep class com.google.common.io.MoreFiles { *; }
-keep class com.google.common.collect.** { *; }
-keep class com.google.common.base.** { *; }
-keep class com.google.common.util.** { *; }
-keep class com.google.common.primitives.** { *; }
-keep class com.google.common.cache.** { *; }
-keep class com.google.common.hash.** { *; }
-keep class com.google.common.net.** { *; }
-keep class com.google.common.reflect.** { *; }
-keep class com.google.common.eventbus.** { *; }
-keep class com.google.common.graph.** { *; }
-keep class com.google.common.math.** { *; }
-keep class com.google.common.escape.** { *; }
-keep class com.google.common.html.** { *; }
-keep class com.google.common.xml.** { *; }
-keep class com.google.common.annotations.** { *; }
-keep class com.google.common.util.concurrent.** { *; }

# ============================================================
# app-keep.pro（原 multidex-config.pro，A-06 更名）
# minSdk 23 已走系统原生 multidex，本文件仅作为 proguard/R8 keep 规则使用
# 注意：文件内全局 keep 是否收紧见 A-05，收紧前 minifyEnabled 形同虚设
# ============================================================
-keep class com.UIN.Tool.** { *; }
-keep class com.UIN.Tool.UinApplication { *; }
-keep class com.UIN.Tool.app.** { *; }
-keep class com.UIN.Tool.app.TermuxApplication { *; }
-keep class com.UIN.Tool.app.TermuxActivity { *; }
-keep class com.UIN.Tool.app.TermuxService { *; }
-keep class com.UIN.Tool.shared.** { *; }
-keep class com.UIN.Tool.shared.file.FileUtils { *; }
-keep class com.UIN.Tool.shared.termux.** { *; }
-keep class com.UIN.Tool.core.** { *; }
-keep class com.UIN.Tool.core.di.ServiceLocator { *; }
-keep class com.UIN.Tool.data.** { *; }
-keep class com.UIN.Tool.ui.** { *; }
-keep class com.UIN.Tool.utils.** { *; }
-keep class com.UIN.Tool.log.** { *; }
-keep class com.UIN.Tool.di.** { *; }
-keep class com.UIN.Tool.model.** { *; }

# ============================================================
# 保留所有 public 方法
# ============================================================
-keepclassmembers class * {
    public *;
}

# ============================================================
# 保留 Kotlin 伴生对象
# ============================================================
-keepclassmembers class ** {
    public static final ** Companion;
}

# ============================================================
# 保留注解
# ============================================================
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes Exceptions
-keepattributes InnerClasses
-keepattributes EnclosingMethod

# ============================================================
# 防止 R8 优化移除
# ============================================================
-dontwarn com.google.common.**
-dontoptimize