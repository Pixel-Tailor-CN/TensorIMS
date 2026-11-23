package app.mystery0.ims.tensor.model

enum class ImsConfigPreset { RECOMMENDED, CHINA_5G, CHINA_LTE, ALL_ENABLED }

/**
 * 预设只声明目标意图，不读取或写入系统；缺失项目保留当前配置。名称格式、USSD 方式和 5G NR 模式始终由用户选择。
 * current 为界面当前值（实际值加草稿）：预设开启 5G NR 时附带已知的模式，避免把仅 SA / 仅 NSA 改回 NSA + SA；
 * 未知组合或 5G 关闭时不附带，按缺省模式写入 [1, 2]。
 */
fun imsConfigPreset(
    preset: ImsConfigPreset,
    current: Map<Feature, FeatureValue> = emptyMap(),
): Map<Feature, FeatureValue> {
    val values = when (preset) {
        ImsConfigPreset.RECOMMENDED -> mapOf(
            Feature.VOLTE to true, Feature.VT to true, Feature.VOWIFI to false,
            Feature.VOWIFI_ROAMING to false, Feature.VONR to true,
            Feature.FIVE_G_NR to true, Feature.FIVE_G_THRESHOLDS to true,
            Feature.SHOW_4G_FOR_LTE to false,
        )
        ImsConfigPreset.CHINA_5G, ImsConfigPreset.CHINA_LTE -> {
            val nr = preset == ImsConfigPreset.CHINA_5G
            mapOf(Feature.VOLTE to true, Feature.VOWIFI to false, Feature.VOWIFI_ROAMING to false,
                Feature.VONR to nr, Feature.VT to true, Feature.CROSS_SIM to true, Feature.UT to true,
                Feature.FIVE_G_NR to nr, Feature.FIVE_G_THRESHOLDS to nr, Feature.FIVE_G_PLUS_ICON to nr,
                Feature.ENHANCED_4G_LTE to true, Feature.HIDE_LTE_PLUS_DATA_ICON to false, Feature.SHOW_4G_FOR_LTE to false)
        }
        // “全部开启”包含始终显示 RAT 开关；整数选项没有“开启”含义，不能选择任意默认值。
        ImsConfigPreset.ALL_ENABLED -> Feature.entries.filter { it.valueType == FeatureValueType.BOOLEAN }.associateWith { true }
    }
    val targets = values.mapValues { FeatureValue(it.value, FeatureValueType.BOOLEAN) }
    val mode = current[Feature.FIVE_G_NR_MODE]
        ?.takeIf { values[Feature.FIVE_G_NR] == true && CarrierConfigChoices.isValidTarget(Feature.FIVE_G_NR_MODE, it) }
    return if (mode == null) targets else targets + (Feature.FIVE_G_NR_MODE to mode)
}

/** 过滤并规整用户自定义预设目标：丢弃非法枚举、空白字符串以及关闭 5G NR 时的模式项。 */
fun sanitizeCustomPreset(targets: Map<Feature, FeatureValue>): Map<Feature, FeatureValue> {
    val nrExplicitlyOff = targets[Feature.FIVE_G_NR]?.data == false
    val cleaned = LinkedHashMap<Feature, FeatureValue>()
    targets.forEach { (feature, value) ->
        if (nrExplicitlyOff && feature == Feature.FIVE_G_NR_MODE) return@forEach
        if (!CarrierConfigChoices.isValidTarget(feature, value)) return@forEach
        if (feature.valueType == FeatureValueType.STRING) {
            val text = (value.data as String).trim()
            if (text.isNotEmpty()) cleaned[feature] = FeatureValue(text, FeatureValueType.STRING)
        } else {
            cleaned[feature] = value
        }
    }
    return cleaned
}

/** 从 SharedPreferences 原始键值对安全还原自定义预设，忽略未知键或类型错乱条目。 */
fun parseCustomPreset(raw: Map<String, *>): Map<Feature, FeatureValue>? {
    val parsed = LinkedHashMap<Feature, FeatureValue>()
    Feature.entries.forEach { feature ->
        val value = raw[feature.name] ?: return@forEach
        val matched = when (feature.valueType) {
            FeatureValueType.BOOLEAN -> (value as? Boolean)?.let { FeatureValue(it, FeatureValueType.BOOLEAN) }
            FeatureValueType.STRING -> (value as? String)?.let { FeatureValue(it, FeatureValueType.STRING) }
            FeatureValueType.INTEGER -> (value as? Int)?.let { FeatureValue(it, FeatureValueType.INTEGER) }
        }
        if (matched != null) parsed[feature] = matched
    }
    return sanitizeCustomPreset(parsed).takeIf { it.isNotEmpty() }
}

/**
 * 从当前编辑器状态提取适合保存为自定义预设的目标：
 * - 字符串项（运营商名称、IMS User Agent）仅在用户主动编辑过或历史已明确覆盖且与当前一致时保留，
 *   避免将系统默认运营商名/UA 误保存为不可逆覆盖目标。
 * - 恢复需重置（RESET_REQUIRED）的布尔项为 false 时不纳入，避免跨卡套用时触发无意义的跳过提示。
 */
fun extractCustomPresetTargets(
    state: ImsEditorState,
    savedHistory: Map<Feature, FeatureValue> = emptyMap(),
): Map<Feature, FeatureValue> {
    val candidates = LinkedHashMap<Feature, FeatureValue>()
    state.values.forEach { (feature, value) ->
        if (feature.valueType == FeatureValueType.STRING) {
            val explicitlyEdited = feature in state.edits
            val confirmedInHistory = savedHistory[feature] == value
            if (!explicitlyEdited && !confirmedInHistory) return@forEach
        }
        if (feature.valueType == FeatureValueType.BOOLEAN &&
            value.data == false &&
            state.recovery(feature) == ConfigRecovery.RESET_REQUIRED
        ) {
            return@forEach
        }
        candidates[feature] = value
    }
    return sanitizeCustomPreset(candidates)
}
