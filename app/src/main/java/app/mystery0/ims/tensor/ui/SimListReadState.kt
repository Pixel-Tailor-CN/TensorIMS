package app.mystery0.ims.tensor.ui

import app.mystery0.ims.tensor.privilege.SimReadResult

/** error 非空时 items 只是最近一次成功快照，不能据失败清空选择或草稿。 */
data class SimListUiState<T>(val items: List<T> = emptyList(), val error: String? = null)

fun <T> completeSimListRead(
    current: SimListUiState<T>,
    result: SimReadResult<T>,
    requestedEpoch: Long,
    currentEpoch: Long,
    allSimItem: T,
): SimListUiState<T> = when {
    requestedEpoch != currentEpoch -> current
    result.error != null -> current.copy(error = result.error)
    result.sims.isEmpty() -> SimListUiState()
    else -> SimListUiState(listOf(allSimItem) + result.sims)
}
