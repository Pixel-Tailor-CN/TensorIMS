package app.mystery0.ims.tensor.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import app.mystery0.ims.tensor.ui.theme.TensorIMSTheme

abstract class BaseActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            TensorIMSTheme {
                content()
            }
        }
    }

    @Composable
    abstract fun content()
}