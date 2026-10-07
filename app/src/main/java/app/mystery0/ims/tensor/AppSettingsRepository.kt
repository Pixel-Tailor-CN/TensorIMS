package app.mystery0.ims.tensor

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AppTheme { SYSTEM, LIGHT, DARK }

/** 仅保存应用偏好，与 SIM 配置和自动恢复相互独立。 */
class AppSettingsRepository(context: Context) {
    private val preferences = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
    private val _theme = MutableStateFlow(
        AppTheme.entries.firstOrNull { it.name == preferences.getString("theme", null) } ?: AppTheme.SYSTEM,
    )
    val theme = _theme.asStateFlow()
    private val _autoCapture = MutableStateFlow(preferences.getBoolean("auto_capture", false))
    val autoCapture = _autoCapture.asStateFlow()

    fun setTheme(theme: AppTheme) {
        preferences.edit().putString("theme", theme.name).apply()
        _theme.value = theme
    }

    fun setAutoCapture(enabled: Boolean) {
        preferences.edit().putBoolean("auto_capture", enabled).apply()
        _autoCapture.value = enabled
        LogcatRepository.setAutomaticCapture(enabled)
    }
}
