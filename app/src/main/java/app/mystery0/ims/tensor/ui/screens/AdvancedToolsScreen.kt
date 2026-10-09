package app.mystery0.ims.tensor.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.mystery0.ims.tensor.R
import app.mystery0.ims.tensor.model.PersistentVolteState
import app.mystery0.ims.tensor.model.ShizukuStatus
import app.mystery0.ims.tensor.model.SimSelection
import app.mystery0.ims.tensor.ui.components.PersistentVolteCard
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight

@Composable
fun AdvancedToolsScreen(
    selectedSim: SimSelection?,
    shizukuStatus: ShizukuStatus,
    isOperationInProgress: Boolean,
    persistentVolteState: PersistentVolteState?,
    onEnablePersistentVolte: (Int) -> Unit,
    onRestorePersistentVolte: (Int) -> Unit,
    onRefreshPersistentVolte: () -> Unit,
    onBack: () -> Unit,
) {
    val singleSimSelected = (selectedSim?.subId ?: -1) >= 0
    Scaffold(contentWindowInsets = pageContentInsets, topBar = {
        CenterAlignedTopAppBar(
            title = { Text(stringResource(R.string.advanced_tools)) },
            navigationIcon = { IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back))
            } },
        )
    }) { innerPadding ->
        Column(Modifier.pageContentPadding(innerPadding).verticalScroll(rememberScrollState())) {
            Text(
                text = selectedSim?.takeIf { singleSimSelected }?.showTitle
                    ?: stringResource(R.string.advanced_tools_single_sim_hint),
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            PersistentVolteCard(
                state = persistentVolteState?.takeIf { it.subId == selectedSim?.subId },
                singleSimSelected = singleSimSelected,
                shizukuReady = shizukuStatus == ShizukuStatus.READY,
                busy = isOperationInProgress,
                onEnable = { selectedSim?.let { onEnablePersistentVolte(it.subId) } },
                onRestore = { selectedSim?.let { onRestorePersistentVolte(it.subId) } },
                onRefresh = onRefreshPersistentVolte,
            )
            ScrollEndSpacer(innerPadding)
        }
    }
}
