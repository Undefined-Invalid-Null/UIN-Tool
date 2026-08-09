// app/src/main/java/com/UIN/Tool/ui/screen/permission/PermissionExplainScreen.kt
package com.UIN.Tool.ui.screen.permission

import com.UIN.Tool.R
import com.UIN.Tool.utils.Str
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
import com.UIN.Tool.ui.components.UIComponents
import com.UIN.Tool.ui.components.unified.*
import com.UIN.Tool.ui.theme.AppColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionExplainScreen(
    onBack: () -> Unit
) {
    Scaffold(
        containerColor = AppColors.pageBackground(),
        topBar = {
            UIComponents.ManageTopAppBar(
                titleText = Str.get(R.string.permission_description),
                onBack = onBack
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
            UnifiedBodyText(
                Str.get(R.string.uin_tool_needs_the_following_permiss),
                modifier = Modifier.padding(bottom = 16.dp)
            )

            PermissionExplainCard(
                icon = Icons.Default.Folder,
                title = Str.get(R.string.storage_permission),
                description = Str.get(R.string.storage_permission_desc)
            )

            PermissionExplainCard(
                icon = Icons.Default.Wifi,
                title = Str.get(R.string.network_permission),
                description = Str.get(R.string.network_permission_desc)
            )

            PermissionExplainCard(
                icon = Icons.Default.Camera,
                title = Str.get(R.string.camera_microphone_permission),
                description = Str.get(R.string.camera_microphone_permission_desc)
            )

            UnifiedCard(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            ) {
                UnifiedBodyText(
                    Str.get(R.string.other_permissions_location_phone_sms),
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
    UnifiedCard(
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
                UnifiedBodyText(title)
            }
            Spacer(modifier = Modifier.height(8.dp))
            UnifiedBodyText(description)
        }
    }
}