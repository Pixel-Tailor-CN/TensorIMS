package app.mystery0.ims.tensor.model

/**
 * 参考 Carrier IMS 的数字 ISO 兼容方法，不修改基带 MCC/MNC，也不保证 TikTok 可用。
 * 使用字符串键兼容 SDK 可见性差异；公开常量从 Android 14 才提供，入口保守限定 14+。
 * Locale 区域接受三位 ASCII 数字；不能沿用早期五位数字，否则系统应用可能崩溃。
 */
object TikTokNetworkFix {
    const val COUNTRY_ISO_KEY = "sim_country_iso_override_string"

    fun isNumericIso(value: String): Boolean =
        value.length == 3 && value.all { it in '0'..'9' } && value != "000"

    /** 随机值只在用户确认后创建，写入、回读和自动恢复共用同一明确目标。 */
    fun newIso(): String = (1..999).random().toString().padStart(3, '0')
}
