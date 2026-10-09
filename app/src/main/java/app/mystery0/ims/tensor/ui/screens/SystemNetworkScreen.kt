package app.mystery0.ims.tensor.ui.screens

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import app.mystery0.ims.tensor.ui.components.pageContentInsets
import app.mystery0.ims.tensor.ui.components.pageContentPadding
import app.mystery0.ims.tensor.ui.components.ScrollEndSpacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.mystery0.ims.tensor.ui.components.CaptivePortalRefreshDialog
import app.mystery0.ims.tensor.R
import app.mystery0.ims.tensor.ui.components.SettingsListItem
import app.mystery0.ims.tensor.ui.CaptivePortalUiState

@Composable
fun SystemNetworkScreen(
    state: CaptivePortalUiState,
    onRefresh: () -> Unit,
    onDiscardDraftAndReload: () -> Unit,
    onOpenCaptivePortal: () -> Unit,
    onBack: () -> Unit,
) {
    var confirmRefresh by rememberSaveable { mutableStateOf(false) }
    if (confirmRefresh) CaptivePortalRefreshDialog(
        onConfirm = { confirmRefresh = false; onDiscardDraftAndReload() },
        onDismiss = { confirmRefresh = false },
    )

    LaunchedEffect(Unit) {
        onRefresh()
    }

    Scaffold(
        contentWindowInsets = pageContentInsets,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.system_network)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(
                        onClick = { if (state.isDirty) confirmRefresh = true else onRefresh() },
                        enabled = !state.loading && !state.saving,
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.refresh))
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(modifier = Modifier.pageContentPadding(innerPadding).verticalScroll(rememberScrollState())) {
            SettingsListItem(
                title = stringResource(R.string.captive_portal),
                summary = when {
                    state.loading -> stringResource(R.string.loading)
                    state.operationError != null -> stringResource(R.string.status_unavailable)
                    state.storedSettings.hasOverride -> stringResource(R.string.captive_portal_custom)
                    else -> stringResource(R.string.captive_portal_system_default)
                },
                enabled = !state.loading,
                onClick = onOpenCaptivePortal,
            )
            state.operationError?.let { error ->
                Text(
                    text = error,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            ScrollEndSpacer(innerPadding)
        }
    }
}
