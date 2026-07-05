package com.UIN.Tool.domain.model

data class MirrorItem(
    val name: String,
    val url: String,
    val remark: String = "",
    val isDefault: Boolean = false,
    var reachable: Boolean? = null
)