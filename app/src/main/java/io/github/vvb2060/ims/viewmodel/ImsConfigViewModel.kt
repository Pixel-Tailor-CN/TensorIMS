package io.github.vvb2060.ims.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.vvb2060.ims.ConfigurationOperations
import io.github.vvb2060.ims.R
import io.github.vvb2060.ims.ShizukuProvider
import io.github.vvb2060.ims.model.Feature
import io.github.vvb2060.ims.model.FeatureValue
import io.github.vvb2060.ims.model.ImsEditorState
import io.github.vvb2060.ims.model.TargetConfigSnapshot
import io.github.vvb2060.ims.model.stageTargetEdits
import io.github.vvb2060.ims.model.commonValues
import io.github.vvb2060.ims.model.refreshedTargetState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 当前快照与待应用目标分离；切卡后忽略旧结果，但完成已发出的写入和历史记录。 */
class ImsConfigViewModel(private val app: Application) : AndroidViewModel(app) {
    private val repository = (app as io.github.vvb2060.ims.Application).autoRestore.repository
    private val _state = MutableStateFlow(ImsEditorState())
    val state = _state.asStateFlow()
    private var generation = 0
    private var shizukuReady = false

    fun load(subId: Int?, ready: Boolean, discardEdits: Boolean = false) {
        shizukuReady = ready
        val previous = if (!discardEdits && _state.value.subId == subId) _state.value else ImsEditorState(subId = subId)
        val request = ++generation
        _state.value = previous.copy(loading = true, applying = false, applied = false, error = null)
        if (!ready || subId == null) return
        viewModelScope.launch {
            try {
                val snapshots = ConfigurationOperations.run {
                    if (request != generation || !shizukuReady) return@run emptyList()
                    val ids = if (subId == -1) ShizukuProvider.readSimInfoList(app).map { it.subId }
                        else listOf(subId)
                    ids.map { ShizukuProvider.readTargetConfig(app, it) }
                }
                if (request != generation) return@launch
                _state.value = refreshedTargetState(previous, snapshots).copy(
                    error = if (snapshots.isEmpty()) app.getString(R.string.ims_no_active_sim)
                        else snapshots.filter { it.error != null }.joinToString("\n") {
                            "SIM ${it.subId}: ${it.error}"
                        }.ifBlank { null })
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                Log.e("ImsConfigViewModel", "Configuration read failed", e)
                if (request == generation) _state.value = _state.value.copy(loading = false, error = e.message)
            }
        }
    }

    fun edit(feature: Feature, value: FeatureValue) = editAll(mapOf(feature to value))

    fun editAll(values: Map<Feature, FeatureValue>) {
        val current = _state.value
        if (!current.ready || current.applying || ConfigurationOperations.busy.value || !shizukuReady) return
        val staged = stageTargetEdits(current, values)
        _state.value = current.copy(edits = staged.edits, applied = false,
            notice = if (staged.skipped) R.string.ims_some_targets_skipped else null)
    }

    fun loadHistory() {
        val current = _state.value
        if (current.subId == null || !current.ready || current.applying) return
        val histories = current.snapshots.map { repository.loadTargetsForSim(it.subId, it.identity).orEmpty() }
        val targets = commonValues(histories)
        if (targets.isNullOrEmpty()) {
            _state.value = current.copy(notice = R.string.ims_no_history)
        } else editAll(targets)
    }

    fun apply() {
        val current = _state.value
        if (!current.ready || current.applying || current.edits.isEmpty() || !shizukuReady ||
            ConfigurationOperations.busy.value) return
        val request = generation
        val targets = current.edits.toMap()
        _state.value = current.copy(applying = true, error = null, notice = null, applied = false)
        viewModelScope.launch {
            val results = mutableListOf<TargetConfigSnapshot>()
            try {
                ConfigurationOperations.run {
                    if (current.subId == -1) {
                        val activeIds = ShizukuProvider.readSimInfoList(app).map { it.subId }.toSet()
                        check(activeIds == current.snapshots.map { it.subId }.toSet()) {
                            app.getString(R.string.ims_sim_list_changed)
                        }
                    }
                    for (snapshot in current.snapshots) {
                        if (request != generation || !shizukuReady) break
                        val result = ShizukuProvider.applyTargetConfig(app, snapshot.subId, snapshot.identity, targets)
                        results += result
                        if (result.error == null) {
                            repository.saveTargets(snapshot.subId, targets, result.identity, result.values)
                            repository.markManualApply(listOf(snapshot.subId))
                        }
                    }
                }
                if (request != generation) return@launch
                val failed = results.filter { it.error != null }
                val complete = results.size == current.snapshots.size && failed.isEmpty()
                _state.value = current.copy(
                    snapshots = current.snapshots.map { original ->
                        results.firstOrNull { it.subId == original.subId && it.error == null } ?: original
                    },
                    edits = if (complete) emptyMap() else targets,
                    applying = false,
                    applied = complete,
                    error = if (complete) null else app.getString(R.string.ims_apply_incomplete) + "\n" +
                        failed.joinToString("\n") { "SIM ${it.subId}: ${it.error}" },
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                Log.e("ImsConfigViewModel", "Configuration apply failed", e)
                if (request == generation) _state.value = current.copy(error = e.message)
            }
        }
    }
}
