package com.sweetapps.facembti

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.sweetapps.facembti.ui.theme.FaceMBTITheme
import com.sweetapps.facembti.ui.AppNav

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // 스플래시 스크린 설치
        installSplashScreen()

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FaceMBTITheme {
                AppNav()
            }
        }
    }
}
