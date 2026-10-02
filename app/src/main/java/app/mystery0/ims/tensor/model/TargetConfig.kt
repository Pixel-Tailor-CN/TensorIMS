package app.mystery0.ims.tensor.model

enum class ConfigRecovery { SWITCH, SYSTEM_DEFAULT, RESET_REQUIRED }

/** values 中缺项表示未知或不支持，不表示关闭。identity 只保存 SIM 标识摘要。 */
data class TargetConfigSnapshot(
    val subId: Int,
    val identity: String = "",
    val values: Map<Feature, FeatureValue> = emptyMap(),
    val error: String? = null,
    val recovery: Map<Feature, ConfigRecovery> = emptyMap(),
)

fun commonValues(values: List<Map<Feature, FeatureValue>>): Map<Feature, FeatureValue> =
    values.firstOrNull().orEmpty().filter { (feature, value) -> values.all { it[feature] == value } }

data class TargetEdits(val edits: Map<Feature, FeatureValue>, val skipped: Boolean)

/** 草稿只包含明确编辑；共同原值用于撤销，未知值不能用 Feature 默认值补齐。 */
fun stageTargetEdits(state: ImsEditorState, values: Map<Feature, FeatureValue>): TargetEdits {
    val edits = state.edits.toMutableMap()
    var skipped = false
    values.forEach { (feature, supplied) ->
        if (feature !in state.supported || supplied.valueType != feature.valueType ||
            (state.subId == -1 && feature.valueType == FeatureValueType.STRING)) {
            skipped = true
            return@forEach
        }
        val value = if (feature.valueType == FeatureValueType.STRING)
            FeatureValue((supplied.data as String).trim(), FeatureValueType.STRING) else supplied
        if (state.recovery(feature) == ConfigRecovery.RESET_REQUIRED &&
            (value.data == false || (value.data is String && value.data.isBlank()))) {
            if (state.common[feature] == value) edits.remove(feature) else skipped = true
            return@forEach
        }
        // 显示值相同不代表完整参数一致，例如仅 SA 与 NSA+SA 都显示开启。
        edits[feature] = value
    }
    return TargetEdits(edits, skipped)
}

/** 重读保留同一组 SIM 的明确草稿；身份或可读范围变化时丢弃不再安全的目标。 */
fun refreshedTargetState(previous: ImsEditorState, snapshots: List<TargetConfigSnapshot>): ImsEditorState {
    val fresh = ImsEditorState(subId = previous.subId, snapshots = snapshots)
    if (!fresh.ready) {
        // 临时读取失败不能吞掉草稿；保留原身份供重试校验，并禁止用旧快照写入。
        return previous.copy(loading = false, applying = false, applied = false,
            snapshots = previous.snapshots.map { old -> old.copy(error = "Configuration refresh failed") })
    }
    val sameIdentities = previous.snapshots.isNotEmpty() &&
        previous.snapshots.associate { it.subId to it.identity } == snapshots.associate { it.subId to it.identity }
    return if (sameIdentities) fresh.copy(edits = stageTargetEdits(fresh, previous.edits).edits) else fresh
}

/** 不把已被系统或其他工具改动的旧目标重新纳入自动恢复。 */
fun confirmedTargetHistory(
    previous: Map<Feature, FeatureValue>,
    edits: Map<Feature, FeatureValue>,
    current: Map<Feature, FeatureValue>,
): Map<Feature, FeatureValue> = previous.filter { (feature, value) -> current[feature] == value } + edits

/** 旧版关闭项是“不处理”；NR 优化和字符串还受旧构造器条件约束。 */
fun legacyTargets(config: Map<Feature, FeatureValue>): Map<Feature, FeatureValue> =
    config.filter { (feature, value) ->
        when {
            feature in setOf(Feature.FIVE_G_THRESHOLDS, Feature.FIVE_G_PLUS_ICON) ->
                config[Feature.FIVE_G_NR]?.data == true && value.data == true
            value.valueType == FeatureValueType.STRING -> (value.data as String).isNotBlank()
            else -> value.data == true
        }
    }.toMutableMap().apply {
        // 旧版 Enhanced LTE 会显式显示 LTE+；保留真实写入意图，不泛化其他 false。
        if (config[Feature.ENHANCED_4G_LTE]?.data == true && config[Feature.HIDE_LTE_PLUS_DATA_ICON]?.data != true) {
            put(Feature.HIDE_LTE_PLUS_DATA_ICON, FeatureValue(false, FeatureValueType.BOOLEAN))
        }
    }

data class ImsEditorState(
    val subId: Int? = null,
    val snapshots: List<TargetConfigSnapshot> = emptyList(),
    val edits: Map<Feature, FeatureValue> = emptyMap(),
    val loading: Boolean = false,
    val applying: Boolean = false,
    val error: String? = null,
    val applied: Boolean = false,
    val notice: Int? = null,
) {
    val ready: Boolean get() = snapshots.isNotEmpty() && snapshots.all { it.error == null } && !loading
    val common: Map<Feature, FeatureValue> get() = commonValues(snapshots.map { it.values })
    val values: Map<Feature, FeatureValue> get() = common + edits
    val supported: Set<Feature> get() = snapshots.firstOrNull()?.values?.keys.orEmpty()
        .filter { feature -> snapshots.all { feature in it.values } }.toSet()

    fun recovery(feature: Feature): ConfigRecovery = snapshots.mapNotNull { it.recovery[feature] }
        .takeIf { it.size == snapshots.size && it.isNotEmpty() }
        ?.let { policies ->
            if (ConfigRecovery.RESET_REQUIRED in policies) ConfigRecovery.RESET_REQUIRED else policies.first()
        } ?: ConfigRecovery.RESET_REQUIRED
}
