package com.UIN.Tool.proot

import java.io.File

data class ContainerConfig(
    val name: String,
    val rootfsPath: File,
    val workingDir: String = "/root",
    val extraBindMounts: List<Pair<String, String>> = emptyList(),
    val environmentVars: Map<String, String> = emptyMap()
)
