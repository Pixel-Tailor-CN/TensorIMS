package app.mystery0.ims.tensor.ui

import app.mystery0.ims.tensor.model.Feature
import app.mystery0.ims.tensor.model.FeatureValueType
import app.mystery0.ims.tensor.model.SimSelection

/**
 * SIM 列表刷新后按 subId 重新关联选择，避免继续操作已经失效的订阅。
 */
fun reconcileSelectedSim(
    current: SimSelection?,
    simList: List<SimSelection>,
): SimSelection? {
    current?.let { selected ->
        simList.firstOrNull { it.subId == selected.subId }?.let { return it }
    }
    return simList.firstOrNull { it.subId != -1 } ?: simList.firstOrNull()
}

/**
 * 批量配置不支持单卡字符串覆盖，因此在“所有 SIM”模式下隐藏相关输入项。
 */
fun visibleFeaturesForSelection(isAllSim: Boolean): List<Feature> {
    if (!isAllSim) return Feature.entries
    return Feature.entries.filterNot {
        it.valueType == FeatureValueType.STRING
    }
}
