// OnboardingScreen.kt
package com.UIN.Tool.ui.screen.onboarding

import com.UIN.Tool.R
import com.UIN.Tool.utils.Str
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
import com.UIN.Tool.ui.theme.AppDimens

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
            OnboardingItem(Str.get(R.string.version_update), Str.get(R.string.onboarding_version_updated, versionName ?: "4.5.0"), Icons.Outlined.SystemUpdate),
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
            .background(colorScheme.background)
            .padding(16.dp)
    ) {
        // 跳过按钮
        if (pagerState.currentPage > 0) {
            Text(
                text = Str.get(R.string.skip),
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

                        Text(
                            text = Str.get(R.string.welcome_to_uin_tool),
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = colorScheme.onBackground
                            ),
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "v${versionName ?: "4.5.0"}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // ✅ 作者的话卡片 - 淡蓝色背景
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp),
                            shape = RoundedCornerShape(AppDimens.cardCornerRadius),
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
                                        text = Str.get(R.string.a_word_from_the_author),
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF0D47A1)
                                        )
                                    )
                                }

                                Text(
                                    text = Str.get(R.string.i_have_always_wanted_a_truly_free_to) +
                                           Str.get(R.string.one_that_can_be_extended_anytime_to_) +
                                           Str.get(R.string.uin_tool_is_my_answer_to_that_idea_n) +
                                           Str.get(R.string.it_is_just_a_framework_the_real_soul) +
                                           Str.get(R.string.i_hope_you_can_create_something_of_y) +
                                           Str.get(R.string.and_share_your_plugins_with_more_peo) +
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
                            shape = RoundedCornerShape(AppDimens.cardCornerRadius),
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
                                        text = Str.get(R.string.terminal_environment_notice),
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFBF360C)
                                        )
                                    )
                                }

                                Text(
                                    text = Str.get(R.string.before_first_use_open_the_dev_page_a) +
                                           Str.get(R.string.then_wait_for_the_terminal_environme) +
                                           Str.get(R.string.due_to_a_package_name_change_pkg_can) +
                                           Str.get(R.string.this_will_be_fixed_in_a_future_versi) +
                                           Str.get(R.string.to_install_packages_please_report_it),
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = Color(0xFFBF360C)
                                    ),
                                    lineHeight = 20.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = Str.get(R.string.swipe_left_right_to_browse_features),
                            style = MaterialTheme.typography.bodySmall,
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
                text = if (pagerState.currentPage == items.size - 1) Str.get(R.string.start_exploring) else Str.get(R.string.next),
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}