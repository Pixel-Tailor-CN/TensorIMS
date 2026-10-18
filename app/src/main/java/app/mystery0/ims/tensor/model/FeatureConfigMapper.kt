package app.mystery0.ims.tensor.model

import android.os.Build
import android.os.Bundle

/** 旧读取入口与目标协议共用映射，缺键或类型不符时不再用功能默认值补全。 */
object FeatureConfigMapper {
    val readKeys: Array<String> = Feature.entries.flatMap(TargetConfigMapper::keys).distinct().toTypedArray()

    @Suppress("DEPRECATION")
    fun fromBundle(bundle: Bundle): Map<Feature, FeatureValue> = TargetConfigMapper.read(
        bundle.keySet().mapNotNull { key -> bundle.get(key)?.let { key to it } }.toMap(),
        Build.VERSION.SDK_INT,
    )
}
