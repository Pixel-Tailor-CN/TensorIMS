package app.mystery0.ims.tensor.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.mystery0.ims.tensor.R
import java.util.Locale

@Composable
fun AppSettingsScreen(
    languageTag: String,
    supportedLanguageTags: List<String>,
    onSelectLanguage: (String) -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.app_settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back))
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            Modifier.padding(innerPadding).verticalScroll(rememberScrollState()).selectableGroup(),
        ) {
            Text(
                stringResource(R.string.app_language),
                Modifier.padding(16.dp),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                stringResource(R.string.app_language_summary),
                Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
            (listOf("") + supportedLanguageTags).forEach { tag ->
                // 语言名称使用本语言书写，避免用户切换到不熟悉的语言后找不到恢复入口。
                val label = when (tag) {
                    "" -> stringResource(R.string.app_language_auto)
                    "en" -> "English"
                    "zh-CN" -> "简体中文"
                    else -> Locale.forLanguageTag(tag).let { it.getDisplayName(it) }
                }
                val selected = languageTag.substringBefore(',') == tag
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .selectable(selected = selected, role = Role.RadioButton, onClick = { onSelectLanguage(tag) })
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = selected, onClick = null)
                    Text(label, Modifier.padding(start = 12.dp))
                }
            }
        }
    }
}
