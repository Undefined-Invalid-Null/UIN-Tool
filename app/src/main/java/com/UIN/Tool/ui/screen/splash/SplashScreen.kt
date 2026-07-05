// app/src/main/java/com/UIN/Tool/ui/screen/splash/SplashScreen.kt
package com.UIN.Tool.ui.screen.splash

import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.UIN.Tool.R
import com.UIN.Tool.ui.components.UIComponents
import com.UIN.Tool.utils.Constants

@Composable
fun SplashScreen(
    onNavigate: () -> Unit
) {
    LaunchedEffect(Unit) {
        Handler(Looper.getMainLooper()).postDelayed({
            onNavigate()
        }, 1500)
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_launcher_foreground),
                contentDescription = "App Icon",
                modifier = Modifier.size(120.dp)
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            UIComponents.TitleText("UIN Tool")
            
            Spacer(modifier = Modifier.height(16.dp))
            
            UIComponents.CaptionText(
                "版本 ${Constants.APP_VERSION} (Build ${Constants.APP_VERSION_CODE})"
            )
        }
    }
}