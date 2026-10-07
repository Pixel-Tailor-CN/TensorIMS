package app.mystery0.ims.tensor.ui.screens

import android.content.ActivityNotFoundException
import android.content.Intent
import android.widget.Toast
import androidx.core.net.toUri
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.mystery0.ims.tensor.AppTheme
import app.mystery0.ims.tensor.BuildConfig
import app.mystery0.ims.tensor.R
import java.util.Locale

@Composable
fun AppSettingsScreen(
    theme: AppTheme,
    autoCapture: Boolean,
    languageTag: String,
    supportedLanguageTags: List<String>,
    onSelectTheme: (AppTheme) -> Unit,
    onAutoCaptureChange: (Boolean) -> Unit,
    onSelectLanguage: (String) -> Unit,
    onOpenLogcat: () -> Unit,
    onBack: () -> Unit,
) {
    var dialog by rememberSaveable { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    fun openLink(url: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, R.string.settings_link_unavailable, Toast.LENGTH_SHORT).show()
        }
    }
    Scaffold(topBar = {
        CenterAlignedTopAppBar(
            title = { Text(stringResource(R.string.app_settings)) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back))
                }
            },
        )
    }) { padding ->
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            SettingsSection(stringResource(R.string.settings_appearance)) {
                SettingsRow(
                    title = stringResource(R.string.settings_theme), summary = themeLabel(theme),
                    first = true,
                    icon = Icons.Rounded.Palette, onClick = { dialog = "theme" },
                )
                SettingsRow(
                    title = stringResource(R.string.app_language),
                    last = true,
                    summary = languageLabel(languageTag.substringBefore(',')),
                    icon = Icons.Rounded.Language, onClick = { dialog = "language" },
                )
            }
            SettingsSection(stringResource(R.string.settings_logs)) {
                SettingsRow(
                    title = stringResource(R.string.settings_auto_capture),
                    first = true,
                    summary = stringResource(R.string.settings_auto_capture_summary),
                    icon = Icons.Rounded.BugReport,
                    modifier = Modifier.toggleable(value = autoCapture, role = Role.Switch, onValueChange = onAutoCaptureChange),
                    trailing = { Switch(checked = autoCapture, onCheckedChange = null) },
                )
                SettingsRow(
                    title = stringResource(R.string.application_logs),
                    last = true,
                    summary = stringResource(R.string.application_logs_summary),
                    icon = Icons.Rounded.Description, onClick = onOpenLogcat,
                )
            }
            SettingsSection(stringResource(R.string.settings_about)) {
                SettingsRow(
                    title = stringResource(R.string.settings_version),
                    first = true,
                    summary = stringResource(R.string.settings_version_value, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
                    icon = Icons.Rounded.Info,
                )
                SettingsRow(
                    title = "PixelTailorCN", summary = "pixel.mystery0.app", icon = Icons.Rounded.Public,
                    onClick = { openLink("https://pixel.mystery0.app") },
                )
                SettingsRow(
                    last = true,
                    title = stringResource(R.string.settings_telegram), summary = "@pixel_tailor_cn", icon = Icons.Rounded.Forum,
                    onClick = { openLink("https://t.me/pixel_tailor_cn") },
                )
            }
        }
    }
    when (dialog) {
        "theme" -> SettingsChoiceDialog(
            title = stringResource(R.string.settings_theme),
            values = AppTheme.entries.map { it.name },
            labels = AppTheme.entries.map { themeLabel(it) },
            selected = theme.name, onDismiss = { dialog = null },
            onConfirm = { dialog = null; onSelectTheme(AppTheme.valueOf(it)) },
        )
        "language" -> {
            val tags = listOf("") + supportedLanguageTags
            SettingsChoiceDialog(
                title = stringResource(R.string.app_language), values = tags,
                labels = tags.map { languageLabel(it) }, selected = languageTag.substringBefore(','),
                onDismiss = { dialog = null }, onConfirm = { dialog = null; onSelectLanguage(it) },
            )
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, Modifier.padding(horizontal = 8.dp), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        // 与系统设置一致：同组条目以背景色细缝分隔，首尾圆角由条目控制。
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp), content = content)
    }
}

@Composable
private fun themeLabel(theme: AppTheme): String = stringResource(when (theme) {
    AppTheme.SYSTEM -> R.string.app_language_auto
    AppTheme.LIGHT -> R.string.settings_theme_light
    AppTheme.DARK -> R.string.settings_theme_dark
})

@Composable
private fun languageLabel(tag: String): String = when (tag) {
    "" -> stringResource(R.string.app_language_auto)
    "en" -> "English"
    "zh-CN" -> "简体中文"
    // 使用本语言名称，便于切换到陌生语言后恢复。
    else -> Locale.forLanguageTag(tag).let { it.getDisplayName(it) }
}

@Composable
private fun SettingsChoiceDialog(
    title: String, values: List<String>, labels: List<String>, selected: String,
    onDismiss: () -> Unit, onConfirm: (String) -> Unit,
) {
    var draft by rememberSaveable(selected) { mutableStateOf(selected) }
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()).selectableGroup()) {
                values.forEachIndexed { index, value ->
                    Row(
                        Modifier.fillMaxWidth().selectable(selected = draft == value, role = Role.RadioButton, onClick = { draft = value })
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = draft == value, onClick = null)
                        Text(labels[index], Modifier.padding(start = 12.dp))
                    }
                }
            }
        },
        confirmButton = { TextButton(enabled = draft in values, onClick = { onConfirm(draft) }) { Text(stringResource(android.R.string.ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } },
    )
}

/** 说明可以换行，图标和操作始终相对整行垂直居中。 */
@Composable
private fun SettingsRow(
    title: String,
    summary: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    first: Boolean = false,
    last: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(
                topStart = if (first) 24.dp else 4.dp,
                topEnd = if (first) 24.dp else 4.dp,
                bottomStart = if (last) 24.dp else 4.dp,
                bottomEnd = if (last) 24.dp else 4.dp,
            ))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .then(modifier)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .heightIn(min = 72.dp).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (trailing != null) {
            trailing()
        } else if (onClick != null) {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null)
        }
    }
}
