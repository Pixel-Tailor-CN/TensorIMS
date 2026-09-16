package io.github.vvb2060.ims.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.vvb2060.ims.ShizukuProvider
import io.github.vvb2060.ims.model.CaptivePortalSettings
import io.github.vvb2060.ims.model.CaptivePortalUrlError
import io.github.vvb2060.ims.model.validateCaptivePortalUrls
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class CaptivePortalMode {
    SYSTEM_DEFAULT,
    CUSTOM,
}

enum class CaptivePortalNotice {
    NONE,
    SAVED,
    RESET,
}

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
)

class SystemNetworkViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(CaptivePortalUiState())
    val uiState = _uiState.asStateFlow()

    fun loadCaptivePortal() {
        val current = _uiState.value
        if (current.loading || current.saving) return

        viewModelScope.launch {
            _uiState.value = current.copy(
                loading = true,
                operationError = null,
                notice = CaptivePortalNotice.NONE,
            )
            val (settings, error) = ShizukuProvider.readCaptivePortalSettings(getApplication())
            if (settings == null) {
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    operationError = error ?: "Failed to read captive portal settings",
                )
                return@launch
            }
            _uiState.value = stateFromStoredSettings(settings)
        }
    }

    fun setMode(mode: CaptivePortalMode) {
        _uiState.value = _uiState.value.copy(
            mode = mode,
            httpError = null,
            httpsError = null,
            operationError = null,
            notice = CaptivePortalNotice.NONE,
        )
    }

    fun setHttpUrl(value: String) {
        _uiState.value = _uiState.value.copy(
            httpUrl = value,
            httpError = null,
            operationError = null,
            notice = CaptivePortalNotice.NONE,
        )
    }

    fun setHttpsUrl(value: String) {
        _uiState.value = _uiState.value.copy(
            httpsUrl = value,
            httpsError = null,
            operationError = null,
            notice = CaptivePortalNotice.NONE,
        )
    }

    fun saveCaptivePortal() {
        val snapshot = _uiState.value
        if (snapshot.loading || snapshot.saving) return

        if (snapshot.mode == CaptivePortalMode.CUSTOM) {
            val validation = validateCaptivePortalUrls(snapshot.httpUrl, snapshot.httpsUrl)
            if (!validation.isValid) {
                _uiState.value = snapshot.copy(
                    httpError = validation.httpError,
                    httpsError = validation.httpsError,
                    operationError = null,
                    notice = CaptivePortalNotice.NONE,
                )
                return
            }
            val settings = requireNotNull(validation.settings)
            viewModelScope.launch {
                _uiState.value = _uiState.value.copy(
                    saving = true,
                    operationError = null,
                    notice = CaptivePortalNotice.NONE,
                )
                val error = ShizukuProvider.writeCaptivePortalSettings(getApplication(), settings)
                if (error == null) {
                    reloadAfterSave(CaptivePortalNotice.SAVED)
                } else {
                    _uiState.value = _uiState.value.copy(
                        saving = false,
                        operationError = error,
                    )
                }
            }
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                saving = true,
                operationError = null,
                notice = CaptivePortalNotice.NONE,
            )
            val error = ShizukuProvider.resetCaptivePortalSettings(getApplication())
            if (error == null) {
                reloadAfterSave(CaptivePortalNotice.RESET)
            } else {
                _uiState.value = _uiState.value.copy(
                    saving = false,
                    operationError = error,
                )
            }
        }
    }

    fun clearNotice() {
        if (_uiState.value.notice != CaptivePortalNotice.NONE) {
            _uiState.value = _uiState.value.copy(notice = CaptivePortalNotice.NONE)
        }
    }

    private suspend fun reloadAfterSave(notice: CaptivePortalNotice) {
        val (settings, error) = ShizukuProvider.readCaptivePortalSettings(getApplication())
        if (settings == null) {
            _uiState.value = _uiState.value.copy(
                saving = false,
                operationError = error ?: "Failed to verify captive portal settings",
                notice = CaptivePortalNotice.NONE,
            )
            return
        }
        _uiState.value = stateFromStoredSettings(settings).copy(notice = notice)
    }

    private fun stateFromStoredSettings(settings: CaptivePortalSettings): CaptivePortalUiState {
        return CaptivePortalUiState(
            storedSettings = settings,
            mode = if (settings.hasOverride) {
                CaptivePortalMode.CUSTOM
            } else {
                CaptivePortalMode.SYSTEM_DEFAULT
            },
            httpUrl = settings.httpUrl.orEmpty(),
            httpsUrl = settings.httpsUrl.orEmpty(),
        )
    }
}
