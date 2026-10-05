package app.mystery0.ims.tensor.model

import org.junit.Assert.*
import org.junit.Test

class TikTokNetworkFixTest {
    private val feature = Feature.TIKTOK_NETWORK_FIX
    private fun text(value: String) = FeatureValue(value, FeatureValueType.STRING)

    @Test
    fun `拒绝可能导致 Locale 异常的区域格式和伪恢复值`() {
        for (invalid in listOf("", "cn", "12345", "12", "000", "１２３", "1a3")) {
            assertFalse(TikTokNetworkFix.isNumericIso(invalid))
            assertThrows(IllegalArgumentException::class.java) {
                TargetConfigMapper.writes(feature, text(invalid), emptyMap())
            }
        }
        assertTrue(TikTokNetworkFix.isNumericIso("001"))
        assertTrue(TikTokNetworkFix.isNumericIso("999"))
    }

    @Test
    fun `数字目标只写 ISO 且重读与恢复保持同一字符串`() {
        val target = text("007")
        val expected = mapOf(TikTokNetworkFix.COUNTRY_ISO_KEY to "007")
        assertEquals(expected, TargetConfigMapper.writes(feature, target, emptyMap()))
        val actual = TargetConfigMapper.read(expected, 34)
        assertEquals(target, actual[feature])
        assertEquals(expected, TargetConfigMapper.writes(feature, actual.getValue(feature), emptyMap()))
        assertEquals(mapOf(feature to target), confirmedTargetHistory(emptyMap(), mapOf(feature to target), actual))
        assertEquals(ConfigRecovery.RESET_REQUIRED, TargetConfigMapper.recovery(feature, expected))
    }

    @Test
    fun `旧系统缺键和类型错误不伪造状态但正常 ISO 保留真实值`() {
        assertFalse(TargetConfigMapper.read(mapOf(TikTokNetworkFix.COUNTRY_ISO_KEY to "123"), 33).containsKey(feature))
        assertFalse(TargetConfigMapper.read(emptyMap(), 34).containsKey(feature))
        assertFalse(TargetConfigMapper.read(mapOf(TikTokNetworkFix.COUNTRY_ISO_KEY to true), 34).containsKey(feature))
        assertEquals(text("cn"), TargetConfigMapper.read(mapOf(TikTokNetworkFix.COUNTRY_ISO_KEY to "cn"), 34)[feature])
        assertEquals(text(""), TargetConfigMapper.read(mapOf(TikTokNetworkFix.COUNTRY_ISO_KEY to ""), 34)[feature])
    }

    @Test
    fun `所有 SIM 和预设不能隐式启用区域兼容`() {
        for (preset in ImsConfigPreset.entries) assertFalse(imsConfigPreset(preset).containsKey(feature))
        val snapshot = TargetConfigSnapshot(1, "identity", mapOf(feature to text("cn")))
        val staged = stageTargetEdits(ImsEditorState(subId = -1, snapshots = listOf(snapshot)), mapOf(feature to text("123")))
        assertTrue(staged.skipped)
        assertTrue(staged.edits.isEmpty())
    }
}
