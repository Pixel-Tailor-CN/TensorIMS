package app.mystery0.ims.tensor.model

/** 当前整数可以来自系统扩展；只有用户明确选择的已知选项允许进入写入目标。 */
object CarrierConfigChoices {
    private val choices = mapOf(
        Feature.VOWIFI_NAME_FORMAT to listOf(0, 1, 4, 6),
        Feature.USSD_METHOD to listOf(0, 1, 2, 3),
    )

    fun selectableValues(feature: Feature): List<Int> = choices[feature].orEmpty()

    fun isSelectable(feature: Feature, value: Int): Boolean = value in selectableValues(feature)

    fun isWellTyped(feature: Feature, value: FeatureValue): Boolean =
        value.valueType == feature.valueType && when (feature.valueType) {
            FeatureValueType.BOOLEAN -> value.data is Boolean
            FeatureValueType.STRING -> value.data is String
            FeatureValueType.INTEGER -> value.data is Int
        }

    fun isValidTarget(feature: Feature, value: FeatureValue): Boolean =
        isWellTyped(feature, value) && (feature.valueType != FeatureValueType.INTEGER ||
            isSelectable(feature, value.data as Int))
}
