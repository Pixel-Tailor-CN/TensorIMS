package app.mystery0.ims.tensor.model

import android.os.Bundle

/** 目标意图置于嵌套 Bundle，避免协议元数据混入 CarrierConfig。 */
object TargetConfigProtocol {
    const val ACTION = "_target_action"
    const val READ = "read"
    const val APPLY = "apply"
    const val VALUES = "_target_values"
    const val IDENTITY = "_target_identity"
    const val ERROR = "_target_error"
    const val SUB_ID = "_target_sub_id"
    const val COMPLETE = "_target_complete"
    const val RECOVERY = "_target_recovery"
    const val WFC_NAME_FORMAT_CONSISTENT = "_target_wfc_name_format_consistent"

    fun encode(values: Map<Feature, FeatureValue>): Bundle = Bundle().apply {
        values.forEach { (feature, value) ->
            require(CarrierConfigChoices.isWellTyped(feature, value)) { "Invalid configuration type: ${feature.name}" }
            when (value.valueType) {
                FeatureValueType.BOOLEAN -> putBoolean(feature.name, value.data as Boolean)
                FeatureValueType.STRING -> putString(feature.name, value.data as String)
                FeatureValueType.INTEGER -> putInt(feature.name, value.data as Int)
            }
        }
    }

    @Suppress("DEPRECATION")
    fun decode(bundle: Bundle): Map<Feature, FeatureValue> = buildMap {
        Feature.entries.forEach { feature ->
            val raw = bundle.get(feature.name) ?: return@forEach
            val value = FeatureValue(raw, feature.valueType)
            // 快照保留未识别的实际整数值；可选范围只在编辑和写入入口检查。
            if (CarrierConfigChoices.isWellTyped(feature, value)) {
                put(feature, value)
            }
        }
    }

    fun snapshot(subId: Int, result: Bundle?): TargetConfigSnapshot {
        if (result == null || !result.getBoolean(COMPLETE) || result.getInt(SUB_ID, -1) != subId) {
            return TargetConfigSnapshot(subId, error = "No complete configuration result")
        }
        val snapshot = TargetConfigSnapshot(subId, result.getString(IDENTITY).orEmpty(),
            result.getBundle(VALUES)?.let(::decode).orEmpty(), result.getString(ERROR),
            Feature.entries.mapNotNull { feature ->
                val name = result.getBundle(RECOVERY)?.getString(feature.name)
                ConfigRecovery.entries.firstOrNull { it.name == name }?.let { feature to it }
            }.toMap(), wfcNameFormatConsistent = result.getBoolean(WFC_NAME_FORMAT_CONSISTENT, false))
        return if (snapshot.error == null && (snapshot.identity.isBlank() || snapshot.values.isEmpty() ||
                    !snapshot.recovery.keys.containsAll(snapshot.values.keys))) {
            snapshot.copy(error = "Incomplete configuration snapshot")
        } else snapshot
    }
}
