package io.github.vvb2060.ims.model

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

    fun encode(values: Map<Feature, FeatureValue>): Bundle = Bundle().apply {
        values.forEach { (feature, value) ->
            when (value.valueType) {
                FeatureValueType.BOOLEAN -> putBoolean(feature.name, value.data as Boolean)
                FeatureValueType.STRING -> putString(feature.name, value.data as String)
            }
        }
    }

    @Suppress("DEPRECATION")
    fun decode(bundle: Bundle): Map<Feature, FeatureValue> = buildMap {
        Feature.entries.forEach { feature ->
            val value = bundle.get(feature.name)
            if ((feature.valueType == FeatureValueType.BOOLEAN && value is Boolean) ||
                (feature.valueType == FeatureValueType.STRING && value is String)) {
                put(feature, FeatureValue(requireNotNull(value), feature.valueType))
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
            }.toMap())
        return if (snapshot.error == null && (snapshot.identity.isBlank() || snapshot.values.isEmpty() ||
                    !snapshot.recovery.keys.containsAll(snapshot.values.keys))) {
            snapshot.copy(error = "Incomplete configuration snapshot")
        } else snapshot
    }
}
