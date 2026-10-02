package app.mystery0.ims.tensor.ui

import app.mystery0.ims.tensor.model.CaptivePortalSettings
import app.mystery0.ims.tensor.model.CaptivePortalUrlError

enum class CaptivePortalMode { SYSTEM_DEFAULT, CUSTOM }
enum class CaptivePortalNotice { NONE, SAVED, RESET }

/** 系统快照和未提交输入分开，自动进入/重连不能静默丢弃草稿。 */
data class CaptivePortalUiState(
    val loading: Boolean = false,
    val saving: Boolean = false,
    val mode: CaptivePortalMode = CaptivePortalMode.SYSTEM_DEFAULT,
    val httpUrl: String = "",
    val httpsUrl: String = "",
    val storedSettings: CaptivePortalSettings = CaptivePortalSettings(null, null),
    val httpError: CaptivePortalUrlError? = null,
    val httpsError: CaptivePortalUrlError? = null,
    val operationError: String? = null,
    val notice: CaptivePortalNotice = CaptivePortalNotice.NONE,
    val draftRevision: Long = 0,
    val hasLoaded: Boolean = false,
) {
    val isDirty: Boolean get() = mode != modeFor(storedSettings) ||
        httpUrl != storedSettings.httpUrl.orEmpty() || httpsUrl != storedSettings.httpsUrl.orEmpty()
}

/** 显式丢弃只针对点击确认时的输入版本，不授权覆盖请求发出后的新输入。 */
data class CaptivePortalReadRequest(val epoch: Long, val draftRevision: Long, val keepDraft: Boolean)

fun captivePortalReadRequest(state: CaptivePortalUiState, epoch: Long, discardDraft: Boolean = false) =
    CaptivePortalReadRequest(epoch, state.draftRevision, state.isDirty && !discardDraft)

fun completeCaptivePortalRead(
    current: CaptivePortalUiState,
    request: CaptivePortalReadRequest,
    currentEpoch: Long,
    settings: CaptivePortalSettings?,
    error: String?,
): CaptivePortalUiState {
    if (request.epoch != currentEpoch) return current.copy(loading = false, saving = false)
    if (settings == null || error != null) return current.copy(
        loading = false, saving = false, operationError = error ?: "No complete captive portal result",
        notice = CaptivePortalNotice.NONE,
    )
    val next = current.copy(loading = false, saving = false, storedSettings = settings,
        hasLoaded = true, operationError = null, notice = CaptivePortalNotice.NONE)
    if (request.keepDraft || current.draftRevision != request.draftRevision) return next
    return next.copy(mode = modeFor(settings), httpUrl = settings.httpUrl.orEmpty(),
        httpsUrl = settings.httpsUrl.orEmpty(), httpError = null, httpsError = null)
}

private fun modeFor(settings: CaptivePortalSettings): CaptivePortalMode =
    if (settings.hasOverride) CaptivePortalMode.CUSTOM else CaptivePortalMode.SYSTEM_DEFAULT
