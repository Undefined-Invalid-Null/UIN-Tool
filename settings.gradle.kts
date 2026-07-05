// settings.gradle.kts
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        // ✅ 添加 JitPack 镜像站（插件管理）
        maven { url = uri("https://jitpack.io") }
        // ✅ 国内镜像站（加速 JitPack 下载）
        maven { url = uri("https://maven.fastmirror.net/repository/jitpack/") }
        // ✅ 备用镜像：阿里云
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        // ✅ 备用镜像：华为云
        maven { url = uri("https://repo.huaweicloud.com/repository/maven") }
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // ✅ 添加 JitPack 镜像站（依赖管理）
        maven { url = uri("https://jitpack.io") }
        // ✅ 国内镜像站（加速 JitPack 下载）
        maven { url = uri("https://maven.fastmirror.net/repository/jitpack/") }
        // ✅ 备用镜像：阿里云
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        // ✅ 备用镜像：华为云
        maven { url = uri("https://repo.huaweicloud.com/repository/maven") }
        // ✅ 备用镜像：腾讯云
        maven { url = uri("https://mirrors.tencent.com/nexus/repository/maven-public") }
        // ✅ 备用镜像：网易
        maven { url = uri("https://mirrors.163.com/maven/repository/maven-public") }
    }
}

rootProject.name = "UIN_Tool"
include(":app")