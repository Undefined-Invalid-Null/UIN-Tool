package com.UIN.Tool.domain.model

data class PermissionItem(
    val iconRes: Int,
    val category: String,
    val permission: String,
    val isSpecial: Boolean = false,
    val isGranted: Boolean = false
)