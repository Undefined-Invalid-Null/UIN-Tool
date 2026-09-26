package com.UIN.Tool.ui.screen.proot

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.UIN.Tool.ui.theme.UINToolTheme

class ContainerManagementActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(applyLocaleContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            UINToolTheme {
                ContainerManagementScreen()
            }
        }
    }

    private fun applyLocaleContext(base: Context): Context {
        val uiConfig = try { com.UIN.Tool.utils.UIConfig.getInstance() } catch (_: Exception) { return base }
        val lang = uiConfig.getLanguage()
        val locale = when (lang) {
            "zh" -> java.util.Locale.CHINESE
            "en" -> java.util.Locale.ENGLISH
            else -> java.util.Locale.getDefault()
        }
        java.util.Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocales(android.os.LocaleList(locale))
        return base.createConfigurationContext(config)
    }
}
