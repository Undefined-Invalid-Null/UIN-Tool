# proguard-rules-release.pro

# ==================== 更激进的优化 ====================
-optimizationpasses 5
-dontusemixedcaseclassnames
-dontskipnonpubliclibraryclasses
-verbose

# ==================== 移除日志代码 ====================
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

# ==================== 合并类 ====================
-mergeinterfacesaggressively
-allowaccessmodification
-repackageclasses ''
-renamesourcefileattribute SourceFile
-keepattributes SourceFile,LineNumberTable