package app.mystery0.ims.tensor.ui

import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FloatingToolbarDefaults.floatingToolbarVerticalNestedScroll
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.mystery0.ims.tensor.R
import app.mystery0.ims.tensor.model.LogLevel
import app.mystery0.ims.tensor.ui.components.LogList
import app.mystery0.ims.tensor.ui.components.LogcatToolbar
import app.mystery0.ims.tensor.ui.components.SingleChoiceDialog
import app.mystery0.ims.tensor.viewmodel.LogcatViewModel
import kotlinx.coroutines.launch

class LogcatActivity : BaseActivity() {
    private val viewModel: LogcatViewModel by viewModels()

    @Composable
    override fun content() {
        val logs = viewModel.logs
        var filter by remember { mutableStateOf(LogLevel.DEBUG) }
        var expanded by rememberSaveable { mutableStateOf(true) }
        var filterMenuExpanded by remember { mutableStateOf(false) }

        val listState = rememberLazyListState()
        val scope = rememberCoroutineScope()

        Box(
            modifier = Modifier
                .fillMaxSize()
                .floatingToolbarVerticalNestedScroll(
                    expanded = expanded,
                    onExpand = { expanded = true },
                    onCollapse = { expanded = false },
                )
        ) {
            Scaffold(
                contentWindowInsets = WindowInsets(0.dp),
                floatingActionButtonPosition = FabPosition.Center,
                floatingActionButton = {
                    Box(Modifier.navigationBarsPadding()) {
                    LogcatToolbar(
                        expanded = expanded,
                        onBack = { finish() },
                        onClearAll = { viewModel.clearLogs() },
                        onExport = { viewModel.exportLogFile() },
                        onFilterClick = { filterMenuExpanded = true },
                        onScrollDown = {
                            if (logs.isNotEmpty()) {
                                scope.launch {
                                    listState.animateScrollToItem(logs.lastIndex)
                                }
                            }
                        }
                    )
                    }
                }
            ) { innerPadding ->
                LogList(
                    listState = listState,
                    innerPadding = innerPadding,
                    logs = logs.filter {
                        it.level.isLevelEnabled(filter)
                    },
                )
            }
        }

        SingleChoiceDialog(
            openDialog = filterMenuExpanded,
            title = stringResource(R.string.title_filter_log_level),
            list = LogLevel.entries.toList(),
            initialValue = filter,
            converter = { it.tag },
            onDismiss = { filterMenuExpanded = false },
            onConfirm = {
                filter = it
            }
        )
    }
}
