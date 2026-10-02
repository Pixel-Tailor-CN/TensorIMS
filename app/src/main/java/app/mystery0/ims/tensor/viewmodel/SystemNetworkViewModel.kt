package app.mystery0.ims.tensor.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.mystery0.ims.tensor.R
import app.mystery0.ims.tensor.ShizukuProvider
import app.mystery0.ims.tensor.model.validateCaptivePortalUrls
import app.mystery0.ims.tensor.privilege.PrivilegeRuntime
import app.mystery0.ims.tensor.ui.CaptivePortalMode
import app.mystery0.ims.tensor.ui.CaptivePortalNotice
import app.mystery0.ims.tensor.ui.CaptivePortalReadRequest
import app.mystery0.ims.tensor.ui.CaptivePortalUiState
import app.mystery0.ims.tensor.ui.captivePortalReadRequest
import app.mystery0.ims.tensor.ui.completeCaptivePortalRead
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SystemNetworkViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(CaptivePortalUiState())
    val uiState = _uiState.asStateFlow()

    /** 页面自动进入和连接刷新仅更新快照，保留所有未提交输入。 */
    fun loadCaptivePortal() = readCaptivePortal(discardDraft = false)

    /** 仅从用户已确认的刷新入口调用，迟到结果仍不能覆盖新输入。 */
    fun discardDraftAndReload() = readCaptivePortal(discardDraft = true)

    private fun readCaptivePortal(discardDraft: Boolean) {
        val current = _uiState.value
        if (current.loading || current.saving) return
        val status = PrivilegeRuntime.status.value
        if (!status.isReady) {
            _uiState.value = current.copy(operationError = getApplication<Application>().getString(R.string.backend_required))
            return
        }
        val request = captivePortalReadRequest(current, status.epoch, discardDraft)
        // 同步置位，避免连续进入页面或点击刷新创建两个覆盖顺序不定的读取。
        _uiState.value = current.copy(loading = true, operationError = null, notice = CaptivePortalNotice.NONE)
        viewModelScope.launch {
            try {
                val (settings, error) = ShizukuProvider.readCaptivePortalSettings(getApplication())
                val epoch = PrivilegeRuntime.status.value.epoch
                _uiState.value = completeCaptivePortalRead(_uiState.value, request, epoch, settings, error)
                if (epoch != request.epoch && PrivilegeRuntime.status.value.isReady) loadCaptivePortal()
            } catch (cancelled: CancellationException) {
                _uiState.value = _uiState.value.copy(loading = false)
                throw cancelled
            } catch (_: Exception) {
                _uiState.value = completeCaptivePortalRead(_uiState.value, request, PrivilegeRuntime.status.value.epoch,
                    null, getApplication<Application>().getString(R.string.backend_portal_read_failed))
            }
        }
    }

    fun setMode(mode: CaptivePortalMode) {
        if (_uiState.value.mode == mode) return
        _uiState.value = editedState().copy(mode = mode)
    }

    fun setHttpUrl(value: String) {
        if (_uiState.value.httpUrl == value) return
        _uiState.value = editedState().copy(httpUrl = value)
    }

    fun setHttpsUrl(value: String) {
        if (_uiState.value.httpsUrl == value) return
        _uiState.value = editedState().copy(httpsUrl = value)
    }

    private fun editedState(): CaptivePortalUiState = _uiState.value.copy(
        draftRevision = _uiState.value.draftRevision + 1,
        httpError = null, httpsError = null, operationError = null, notice = CaptivePortalNotice.NONE,
    )

    fun saveCaptivePortal() {
        val snapshot = _uiState.value
        val backend = PrivilegeRuntime.status.value
        if (snapshot.loading || snapshot.saving || !backend.isReady) return
        val validation = if (snapshot.mode == CaptivePortalMode.CUSTOM)
            validateCaptivePortalUrls(snapshot.httpUrl, snapshot.httpsUrl) else null
        if (validation != null && !validation.isValid) {
            _uiState.value = snapshot.copy(httpError = validation.httpError, httpsError = validation.httpsError,
                operationError = null, notice = CaptivePortalNotice.NONE)
            return
        }
        val request = captivePortalReadRequest(snapshot, backend.epoch, discardDraft = true)
        _uiState.value = snapshot.copy(saving = true, operationError = null, notice = CaptivePortalNotice.NONE)
        viewModelScope.launch {
            try {
                val error = if (snapshot.mode == CaptivePortalMode.CUSTOM) {
                    ShizukuProvider.writeCaptivePortalSettings(getApplication(), requireNotNull(validation?.settings))
                } else ShizukuProvider.resetCaptivePortalSettings(getApplication())
                if (error == null) {
                    reloadAfterSave(request, if (snapshot.mode == CaptivePortalMode.CUSTOM) CaptivePortalNotice.SAVED else CaptivePortalNotice.RESET)
                } else {
                    _uiState.value = _uiState.value.copy(saving = false, operationError = error)
                }
            } catch (cancelled: CancellationException) {
                _uiState.value = _uiState.value.copy(saving = false)
                throw cancelled
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(saving = false,
                    operationError = getApplication<Application>().getString(R.string.backend_portal_save_failed))
            }
        }
    }

    fun clearNotice() {
        if (_uiState.value.notice != CaptivePortalNotice.NONE) {
            _uiState.value = _uiState.value.copy(notice = CaptivePortalNotice.NONE)
        }
    }

    private suspend fun reloadAfterSave(request: CaptivePortalReadRequest, notice: CaptivePortalNotice) {
        val (settings, error) = ShizukuProvider.readCaptivePortalSettings(getApplication())
        val epoch = PrivilegeRuntime.status.value.epoch
        val next = completeCaptivePortalRead(_uiState.value, request, epoch, settings, error)
        // 保存期间新编辑的输入继续保留；成功通知只代表本次已确认写入。
        _uiState.value = if (request.epoch == epoch && settings != null && error == null) next.copy(notice = notice) else next
    }
}
