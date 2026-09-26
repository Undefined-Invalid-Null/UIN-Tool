package com.UIN.Tool.proot

import java.io.File

data class ContainerConfig(
    val name: String,
    val rootfsPath: File,
    val workingDir: String = "/root",
    val extraBindMounts: List<Pair<String, String>> = emptyList(),
    val environmentVars: Map<String, String> = emptyMap()
) {
    val isReady: Boolean
        get() = rootfsPath.isDirectory && File(rootfsPath, "bin/sh").exists()

    companion object {
        fun default(context: android.content.Context): ContainerConfig {
            val containersDir = File(context.filesDir, "containers")
            val debianDir = File(containersDir, "debian")
            return ContainerConfig(
                name = "debian",
                rootfsPath = File(debianDir, "rootfs")
            )
        }
    }
}
