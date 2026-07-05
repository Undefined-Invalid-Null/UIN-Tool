// app/src/main/java/com/UIN/Tool/ui/screen/permission/PermissionExplainScreen.kt
package com.UIN.Tool.ui.screen.permission

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.UIN.Tool.ui.components.UIComponents

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionExplainScreen(
    navController: NavController
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("权限说明") },
                navigationIcon = {
                    UIComponents.IconButton(
                        icon = Icons.Default.ArrowBack,
                        onClick = { navController.navigateUp() }
                    )
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            UIComponents.BodyText(
                "UIN Tool 需要以下权限来正常使用各项功能：",
                modifier = Modifier.padding(bottom = 16.dp)
            )

            PermissionExplainCard(
                icon = Icons.Default.Folder,
                title = "📁 存储权限",
                description = """
                    • 导入/导出插件文件 (.tpk, .zip)
                    • 备份和恢复插件数据
                    • 读取和写入工作目录
                    • 导出日志文件

                    💡 提示：我们不会读取您的个人文件，仅操作 UIN_Tool 目录下的文件。
                """.trimIndent()
            )

            PermissionExplainCard(
                icon = Icons.Default.Wifi,
                title = "🌐 网络权限",
                description = """
                    • 从 GitHub 插件仓库下载插件
                    • 检查应用更新
                    • 插件网络请求功能

                    💡 提示：仅用于插件下载和更新功能。
                """.trimIndent()
            )

            PermissionExplainCard(
                icon = Icons.Default.Camera,
                title = "📷 相机/麦克风权限",
                description = """
                    • 插件可能需要调用相机拍照
                    • 插件可能需要录音功能

                    💡 提示：这些权限仅在您安装的插件需要时才会使用。
                """.trimIndent()
            )

            UIComponents.Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            ) {
                UIComponents.BodyText(
                    "其他权限（位置、电话、短信等）仅在插件需要时才会请求使用。",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                )
            }
        }
    }
}

@Composable
fun PermissionExplainCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String
) {
    UIComponents.Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                UIComponents.BodyText(title)
            }
            Spacer(modifier = Modifier.height(8.dp))
            UIComponents.BodyText(description)
        }
    }
}