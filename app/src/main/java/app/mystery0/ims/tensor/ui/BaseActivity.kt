package app.mystery0.ims.tensor.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.activity.SystemBarStyle
import app.mystery0.ims.tensor.AppTheme
import app.mystery0.ims.tensor.Application
import app.mystery0.ims.tensor.ui.theme.TensorIMSTheme

abstract class BaseActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val theme by (application as Application).settings.theme.collectAsStateWithLifecycle()
            val dark = when (theme) {
                AppTheme.SYSTEM -> isSystemInDarkTheme()
                AppTheme.LIGHT -> false
                AppTheme.DARK -> true
            }
            SideEffect {
                val bars = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT) { dark }
                enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
                // 所有页面均允许内容透过三键导航区域，主题更新后也不恢复系统底色。
                window.isNavigationBarContrastEnforced = false
            }
            TensorIMSTheme(darkTheme = dark) {
                content()
            }
        }
    }

    @Composable
    abstract fun content()
}
