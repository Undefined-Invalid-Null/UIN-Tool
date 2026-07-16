// app/src/main/java/com/UIN/Tool/ui/screen/onboarding/OnboardingScreen.kt
package com.UIN.Tool.ui.screen.onboarding

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.UIN.Tool.ui.components.UIComponents
import kotlinx.coroutines.launch

data class OnboardingItem(
    val title: String,
    val description: String,
    val icon: ImageVector
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(
    onNavigateToMain: () -> Unit,
    isVersionUpdate: Boolean = false,
    versionName: String? = null
) {
    val scope = rememberCoroutineScope()
    val colorScheme = MaterialTheme.colorScheme

    val items = if (isVersionUpdate) {
        listOf(
            OnboardingItem(
                "版本更新",
                "UIN Tool 已更新到 v${versionName ?: "4.0.0"}\n\n新功能、优化和修复已就绪！",
                Icons.Outlined.SystemUpdate
            ),
            OnboardingItem(
                "插件管理",
                "• 一键导入/导出插件\n• 支持 TPK 和 ZIP 格式\n• 插件分类管理",
                Icons.Outlined.Folder
            ),
            OnboardingItem(
                "插件开发",
                "• 可视化插件创建向导\n• 内置代码编辑器\n• 支持原生和Web插件",
                Icons.Outlined.DeveloperMode
            ),
            OnboardingItem(
                "开始使用",
                "现在开始探索 UIN Tool 的新功能吧！",
                Icons.Outlined.RocketLaunch
            )
        )
    } else {
        listOf(
            OnboardingItem(
                "欢迎使用 UIN Tool",
                "UIN Tool 是一个强大的插件化工具平台\n\n支持原生 Java 插件和 Web 插件\n让您轻松扩展应用功能",
                Icons.Outlined.RocketLaunch
            ),
            OnboardingItem(
                "插件管理",
                "• 一键导入/导出插件\n• 支持 TPK 和 ZIP 格式\n• 插件分类管理\n• 备份恢复所有数据",
                Icons.Outlined.Folder
            ),
            OnboardingItem(
                "插件开发工具",
                "• 可视化插件创建向导\n• 内置代码编辑器\n• 打包为 TPK 文件",
                Icons.Outlined.DeveloperMode
            ),
            OnboardingItem(
                "Web 插件支持",
                "• 使用 HTML/CSS/JS 开发\n• 无需编译，即改即用\n• JS 桥接调用原生功能",
                Icons.Outlined.Language
            ),
            OnboardingItem(
                "一切就绪",
                "现在开始探索 UIN Tool 的更多功能吧！",
                Icons.Outlined.CheckCircle
            )
        )
    }

    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { items.size }
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.background)
            .padding(16.dp)
    ) {
        // 跳过按钮
        if (pagerState.currentPage > 0) {
            Text(
                text = "跳过",
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
                    .clickable { onNavigateToMain() },
                textAlign = TextAlign.End
            )
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f)
        ) { page ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(
                            colorScheme.primaryContainer.copy(alpha = 0.15f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = items[page].icon,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = colorScheme.primary.copy(alpha = 0.7f)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = items[page].title,
                    style = MaterialTheme.typography.titleLarge.copy(
                        color = colorScheme.onBackground
                    ),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = items[page].description,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = colorScheme.onSurfaceVariant
                    ),
                    textAlign = TextAlign.Center
                )
            }
        }

        // 指示器
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            repeat(items.size) { index ->
                Box(
                    modifier = Modifier
                        .padding(4.dp)
                        .size(if (index == pagerState.currentPage) 8.dp else 6.dp)
                        .clip(CircleShape)
                        .background(
                            if (index == pagerState.currentPage)
                                colorScheme.primary
                            else
                                colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                        )
                )
            }
        }

        // 按钮
        Button(
            onClick = {
                if (pagerState.currentPage == items.size - 1) {
                    onNavigateToMain()
                } else {
                    scope.launch {
                        pagerState.animateScrollToPage(pagerState.currentPage + 1)
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .height(48.dp)
                .align(Alignment.CenterHorizontally),
            colors = ButtonDefaults.buttonColors(
                containerColor = colorScheme.primary,
                contentColor = colorScheme.onPrimary
            ),
            shape = MaterialTheme.shapes.medium
        ) {
            Text(
                text = if (pagerState.currentPage == items.size - 1) "开始体验" else "下一步",
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}