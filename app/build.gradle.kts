plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.UIN.Tool"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.UIN.Tool"
        minSdk = 23
        targetSdk = 35
        versionCode = 10
        versionName = "4.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        resourceConfigurations += listOf("en", "zh-rCN", "ja")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
            isShrinkResources = false
        }
    }

    compileOptions {
        // ✅ 启用核心库脱糖（修复 language-textmate 依赖问题）
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
        }
    }

    buildFeatures {
        compose = true
        viewBinding = true
        dataBinding = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/INDEX.LIST"
            excludes += "/META-INF/DEPENDENCIES"
            excludes += "/META-INF/*.kotlin_module"
            excludes += "**/*.proto"
            excludes += "**/*.kotlin_builtins"
        }
    }
}

configurations.all {
    exclude(group = "com.google.guava", module = "listenablefuture")
}

dependencies {
    // ==================== 核心框架 ====================
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")

    // ==================== Compose ====================
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material3:material3-window-size-class")
    implementation("androidx.compose.runtime:runtime-livedata")
    implementation("androidx.compose.material:material-icons-extended")

    // ==================== Compose Navigation ====================
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // ==================== Material ====================
    implementation("com.google.android.material:material:1.11.0")

    // ==================== 网络请求 ====================
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")

    // ❌ 移除 org.json
    // implementation("org.json:json:20240303")

    // ==================== Markdown 渲染 ====================
    implementation("org.commonmark:commonmark:0.22.0")

    // ==================== 其他 ====================
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.2.0")
    implementation("androidx.core:core-splashscreen:1.0.1")

    // ==================== Sora Editor ====================
    val soraVersion = "0.23.5"
    implementation("io.github.Rosemoe.sora-editor:editor:$soraVersion")
    implementation("io.github.Rosemoe.sora-editor:language-textmate:$soraVersion")
    implementation("io.github.Rosemoe.sora-editor:language-java:$soraVersion")

    // ==================== R8/D8 编译器 ====================
    implementation("com.android.tools:r8:8.7.18")

    // ==================== ✅ ECJ 编译器（运行时需要） ====================
    implementation(files("libs/ecj.jar"))
    implementation(files("libs/tools.jar"))

    // ==================== ✅ 编译时依赖（不打包到 APK） ====================
    compileOnly(files("libs/android.jar"))
    compileOnly(files("libs/host-sdk.jar"))

    // ==================== 解决 ProfileVerifier 依赖问题 ====================
    implementation("com.google.guava:guava:31.1-android")

    // ==================== ✅ 核心库脱糖（修复 language-textmate 问题） ====================
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")

    // ==================== 测试 ====================
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.5.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.09.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}