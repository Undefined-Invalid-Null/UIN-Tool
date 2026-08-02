// OnboardingScreen.kt
package com.UIN.Tool.ui.screen.onboarding

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
            OnboardingItem("版本更新", "UIN Tool 已更新到 v${versionName ?: "4.4.4"}\n\n新功能、优化和修复已就绪！", Icons.Outlined.SystemUpdate),
            OnboardingItem("插件管理", "• 一键导入/导出插件\n• 支持 TPK 和 ZIP 格式\n• 插件分类管理", Icons.Outlined.Folder),
            OnboardingItem("插件开发", "• 可视化插件创建向导\n• 内置代码编辑器\n• 支持原生和Web插件", Icons.Outlined.DeveloperMode),
            OnboardingItem("开始使用", "现在开始探索 UIN Tool 的新功能吧！", Icons.Outlined.RocketLaunch)
        )
    } else {
        listOf(
            OnboardingItem("欢迎使用 UIN Tool", "UIN Tool 是一个强大的插件化工具平台\n\n支持原生 Java 插件和 Web 插件\n内置 Termux 终端环境", Icons.Outlined.RocketLaunch),
            OnboardingItem("插件管理", "• 一键导入/导出插件\n• 支持 TPK 和 ZIP 格式\n• 插件分类管理\n• 备份恢复所有数据", Icons.Outlined.Folder),
            OnboardingItem("插件开发工具", "• 可视化插件创建向导\n• 内置代码编辑器\n• 支持原生和 Web 插件", Icons.Outlined.DeveloperMode),
            OnboardingItem("Web 插件支持", "• 使用 HTML/CSS/JS 开发\n• 无需编译，即改即用\n• JS 桥接调用原生功能", Icons.Outlined.Language),
            OnboardingItem("一切就绪", "现在开始探索 UIN Tool 的更多功能吧！", Icons.Outlined.CheckCircle)
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
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // 首页显示作者的话 + 终端提示
                if (page == 0 && !isVersionUpdate) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                    ) {
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(
                                    colorScheme.primaryContainer.copy(alpha = 0.2f)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.RocketLaunch,
                                contentDescription = null,
                                modifier = Modifier.size(40.dp),
                                tint = colorScheme.primary
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "欢迎使用 UIN Tool",
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = colorScheme.onBackground
                            ),
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "v${versionName ?: "4.4.4"}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // ✅ 作者的话卡片 - 淡蓝色背景
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0xFFE3F2FD)  // 淡蓝色
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(bottom = 6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.FormatQuote,
                                        contentDescription = null,
                                        tint = Color(0xFF1565C0),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "作者的话",
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF0D47A1)
                                        )
                                    )
                                }

                                Text(
                                    text = "一直以来，我都希望能在手机上有一个真正自由的工具平台，\n" +
                                           "可以按自己的需求随时扩展功能，而不是等开发者更新。\n\n" +
                                           "UIN Tool 就是我对这个想法的回答，\n" +
                                           "它只是一个框架，真正的灵魂在于你 —— 每一位使用者。\n\n" +
                                           "希望你能用它创造出属于你自己的东西，\n" +
                                           "也欢迎分享你的插件给更多人。\n\n" +
                                           "— UIN Team",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = Color(0xFF0D47A1)
                                    ),
                                    lineHeight = 20.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // ✅ 终端环境提示卡片
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0xFFFFF3E0)  // 淡橙色
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(bottom = 6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Terminal,
                                        contentDescription = null,
                                        tint = Color(0xFFE65100),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "终端环境提示",
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFBF360C)
                                        )
                                    )
                                }

                                Text(
                                    text = "⚠️ 首次使用前，请先打开「开发」页面点击「打开终端」，\n" +
                                           "等待终端环境初始化完成（约 30-60 秒）。\n\n" +
                                           "⚠️ 由于包名变更，终端内暂时无法使用 pkg 安装软件包，\n" +
                                           "此问题将在后续版本中修复（可能选择构建部分软件包供下载）。\n\n" +
                                           "💡 如需安装软件包，请在 Termux 社区反馈。",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = Color(0xFFBF360C)
                                    ),
                                    lineHeight = 20.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "左右滑动浏览功能介绍",
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                } else {
                    // 其他页面
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(RoundedCornerShape(20.dp))
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
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.onBackground
                        ),
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = items[page].description,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            color = colorScheme.onSurfaceVariant
                        ),
                        textAlign = TextAlign.Center
                    )
                }
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
                        .clip(RoundedCornerShape(50))
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