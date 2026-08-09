// OnboardingScreen.kt
package com.UIN.Tool.ui.screen.onboarding

import com.UIN.Tool.R
import com.UIN.Tool.utils.Str
import com.UIN.Tool.ui.components.ReleaseChangelog
import com.UIN.Tool.ui.components.unified.ButtonSize
import com.UIN.Tool.ui.components.unified.ButtonVariant
import com.UIN.Tool.ui.components.unified.UnifiedBodyText
import com.UIN.Tool.ui.components.unified.UnifiedButton
import com.UIN.Tool.ui.components.unified.UnifiedCaptionText
import com.UIN.Tool.ui.components.unified.UnifiedCard
import com.UIN.Tool.ui.components.unified.UnifiedSectionTitle
import com.UIN.Tool.ui.components.unified.UnifiedTitleText
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import com.UIN.Tool.ui.theme.AppDimens
import com.UIN.Tool.ui.theme.AppColors

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
    versionName: String? = null,
    releaseNotes: String? = null
) {
    // 版本更新提示：优先以全屏方式展示 Markdown 变更日志（与「检查更新」弹窗共用渲染）
    if (isVersionUpdate && !releaseNotes.isNullOrBlank()) {
        VersionUpdateScreen(
            versionName = versionName ?: "5.2.0",
            releaseNotes = releaseNotes,
            onNavigateToMain = onNavigateToMain
        )
        return
    }

    val scope = rememberCoroutineScope()
    val colorScheme = MaterialTheme.colorScheme

    val items = if (isVersionUpdate) {
        listOf(
            OnboardingItem(Str.get(R.string.version_update), Str.get(R.string.onboarding_version_updated, versionName ?: "5.2.0"), Icons.Outlined.SystemUpdate),
            OnboardingItem(Str.get(R.string.plugin_management), Str.get(R.string.one_tap_import_export_plugins_n_supp), Icons.Outlined.Folder),
            OnboardingItem(Str.get(R.string.plugin_development), Str.get(R.string.visual_plugin_creation_wizard_n_buil), Icons.Outlined.DeveloperMode),
            OnboardingItem(Str.get(R.string.get_started), Str.get(R.string.now_explore_the_new_features_of_uin_), Icons.Outlined.RocketLaunch)
        )
    } else {
        listOf(
            OnboardingItem(Str.get(R.string.welcome_to_uin_tool), Str.get(R.string.uin_tool_is_a_powerful_plugin_platfo), Icons.Outlined.RocketLaunch),
            OnboardingItem(Str.get(R.string.plugin_management), Str.get(R.string.one_tap_import_export_plugins_n_supp_2), Icons.Outlined.Folder),
            OnboardingItem(Str.get(R.string.plugin_development_tools), Str.get(R.string.visual_plugin_creation_wizard_n_buil_2), Icons.Outlined.DeveloperMode),
            OnboardingItem(Str.get(R.string.web_plugin_support), Str.get(R.string.develop_with_html_css_js_n_no_compil), Icons.Outlined.Language),
            OnboardingItem(Str.get(R.string.all_set), Str.get(R.string.now_explore_more_features_of_uin_too), Icons.Outlined.CheckCircle)
        )
    }

    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { items.size }
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(com.UIN.Tool.ui.theme.AppColors.pageBackground())
            .padding(16.dp)
    ) {
        // 跳过按钮
        if (pagerState.currentPage > 0) {
            UnifiedBodyText(
                text = Str.get(R.string.skip),
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
                                .clip(RoundedCornerShape(AppDimens.radiusXXLarge))
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

                        UnifiedTitleText(
                            text = Str.get(R.string.welcome_to_uin_tool),
                            color = colorScheme.onBackground,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        UnifiedBodyText(
                            text = "v${versionName ?: "5.2.0"}",
                            color = colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // ✅ 作者的话 + 终端环境提示卡片
                        UnifiedCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp)
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
                                    UnifiedSectionTitle(
                                        text = Str.get(R.string.a_word_from_the_author),
                                        color = Color(0xFF0D47A1)
                                    )
                                }

                                UnifiedBodyText(
                                    text = Str.get(R.string.onboarding_author_words),
                                    color = Color(0xFF0D47A1)
                                )

                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 12.dp),
                                    color = Color(0xFF1565C0).copy(alpha = 0.15f)
                                )

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
                                    UnifiedSectionTitle(
                                        text = Str.get(R.string.terminal_environment_notice),
                                        color = Color(0xFFBF360C)
                                    )
                                }

                                UnifiedBodyText(
                                    text = Str.get(R.string.onboarding_terminal_notice),
                                    color = Color(0xFFBF360C)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        UnifiedCaptionText(
                            text = Str.get(R.string.swipe_left_right_to_browse_features),
                            color = colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                } else {
                    // 其他页面
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(RoundedCornerShape(AppDimens.radiusXXLarge))
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

                    UnifiedTitleText(
                        text = items[page].title,
                        color = colorScheme.onBackground,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    UnifiedBodyText(
                        text = items[page].description,
                        color = colorScheme.onSurfaceVariant,
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
        UnifiedButton(
            text = if (pagerState.currentPage == items.size - 1) Str.get(R.string.start_exploring) else Str.get(R.string.next),
            onClick = {
                if (pagerState.currentPage == items.size - 1) {
                    onNavigateToMain()
                } else {
                    scope.launch {
                        pagerState.animateScrollToPage(pagerState.currentPage + 1)
                    }
                }
            },
            variant = ButtonVariant.Primary,
            size = ButtonSize.Large,
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .align(Alignment.CenterHorizontally)
        )
    }
}

// ==================== 版本更新全屏页（Markdown 变更日志，与更新弹窗共用渲染） ====================

@Composable
fun VersionUpdateScreen(
    versionName: String,
    releaseNotes: String,
    onNavigateToMain: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(com.UIN.Tool.ui.theme.AppColors.pageBackground())
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp, bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(AppDimens.radiusXXLarge))
                    .background(colorScheme.primaryContainer.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.SystemUpdate,
                    contentDescription = null,
                    modifier = Modifier.size(36.dp),
                    tint = colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            UnifiedTitleText(
                text = Str.get(R.string.version_update),
                color = colorScheme.onBackground,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(4.dp))

            UnifiedBodyText(
                text = Str.get(R.string.onboarding_version_updated, versionName),
                color = colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Description,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                UnifiedSectionTitle(
                    text = Str.get(R.string.changelog),
                    color = colorScheme.onSurface
                )
            }
        }

        ReleaseChangelog(
            markdown = releaseNotes,
            modifier = Modifier.weight(1f),
            minHeight = 120,
            maxHeight = Int.MAX_VALUE
        )

        Spacer(modifier = Modifier.height(12.dp))

        UnifiedButton(
            text = Str.get(R.string.start_exploring),
            onClick = onNavigateToMain,
            variant = ButtonVariant.Primary,
            size = ButtonSize.Large,
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .align(Alignment.CenterHorizontally)
        )
    }
}