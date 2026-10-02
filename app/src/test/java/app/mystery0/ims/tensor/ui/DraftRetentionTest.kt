package app.mystery0.ims.tensor.ui

import app.mystery0.ims.tensor.model.CaptivePortalSettings
import app.mystery0.ims.tensor.privilege.SimReadResult
import org.junit.Assert.*
import org.junit.Test

class DraftRetentionTest {
    private val original = CaptivePortalSettings("http://old.example/check", "https://old.example/check")
    private val refreshed = CaptivePortalSettings("http://new.example/check", "https://new.example/check")
    private fun loaded() = completeCaptivePortalRead(
        CaptivePortalUiState(), CaptivePortalReadRequest(1, 0, false), 1, original, null,
    )
    private fun edited() = loaded().copy(
        httpUrl = "http://draft.example/check", httpsUrl = "https://draft.example/check", draftRevision = 1,
    )

    @Test fun `第一次成功读取初始化草稿和系统快照`() {
        val state = loaded()
        assertEquals(original, state.storedSettings)
        assertEquals(original.httpUrl, state.httpUrl)
        assertFalse(state.isDirty)
        assertTrue(state.hasLoaded)
    }

    @Test fun `自动重新进入和模式切换后的刷新只更新快照而保留脏草稿`() {
        val draft = edited()
        val request = captivePortalReadRequest(draft, 2, discardDraft = false)
        val result = completeCaptivePortalRead(draft.copy(loading = true), request, 2, refreshed, null)
        assertEquals(draft.httpUrl, result.httpUrl)
        assertEquals(draft.httpsUrl, result.httpsUrl)
        assertEquals(draft.mode, result.mode)
        assertEquals(refreshed, result.storedSettings)
        assertTrue(result.isDirty)
    }

    @Test fun `主动刷新需要确认丢弃且确认后才替换原有草稿`() {
        val draft = edited()
        assertTrue(draft.isDirty)
        val request = captivePortalReadRequest(draft, 1, discardDraft = true)
        val result = completeCaptivePortalRead(draft, request, 1, refreshed, null)
        assertEquals(refreshed.httpUrl, result.httpUrl)
        assertFalse(result.isDirty)
    }

    @Test fun `自动读取开始后输入的草稿不会被迟到结果覆盖`() {
        val before = loaded()
        val request = captivePortalReadRequest(before, 1, discardDraft = false)
        val later = before.copy(httpUrl = "http://later.example/check", draftRevision = 1)
        val result = completeCaptivePortalRead(later, request, 1, refreshed, null)
        assertEquals(later.httpUrl, result.httpUrl)
        assertEquals(refreshed, result.storedSettings)
    }

    @Test fun `确认丢弃后新输入仍由版本保护不被迟到结果覆盖`() {
        val before = edited()
        val request = captivePortalReadRequest(before, 1, discardDraft = true)
        val later = before.copy(httpUrl = "http://later.example/check", draftRevision = 2)
        val result = completeCaptivePortalRead(later, request, 1, refreshed, null)
        assertEquals(later.httpUrl, result.httpUrl)
        assertTrue(result.isDirty)
    }

    @Test fun `切换后的旧代际回包不替换草稿也不替换系统快照`() {
        val draft = edited().copy(loading = true)
        val request = captivePortalReadRequest(draft, 1, discardDraft = true)
        val result = completeCaptivePortalRead(draft, request, 2, refreshed, null)
        assertEquals(draft.copy(loading = false), result)
    }

    @Test fun `读取失败保留草稿和最后成功的系统值`() {
        val draft = edited().copy(loading = true)
        val result = completeCaptivePortalRead(draft, captivePortalReadRequest(draft, 1), 1, null, "Read failed")
        assertEquals(draft.httpUrl, result.httpUrl)
        assertEquals(draft.storedSettings, result.storedSettings)
        assertEquals("Read failed", result.operationError)
        assertFalse(result.loading)
    }

    @Test fun `系统默认模式下未提交的地址也属于应保留草稿`() {
        val draft = CaptivePortalUiState(httpUrl = "http://draft.example/check", draftRevision = 1)
        assertTrue(draft.isDirty)
        val result = completeCaptivePortalRead(draft, captivePortalReadRequest(draft, 1), 1, original, null)
        assertEquals(CaptivePortalMode.SYSTEM_DEFAULT, result.mode)
        assertEquals(draft.httpUrl, result.httpUrl)
    }

    @Test fun `读卡失败但后端READY时仍保留卡片和选择来源`() {
        val lastGood = SimListUiState(listOf(-1, 11, 22))
        val result = completeSimListRead(lastGood, SimReadResult(emptyList(), "Delegation busy"), 4, 4, -1)
        assertSame(lastGood.items, result.items)
        assertTrue(result.items.contains(11))
        assertEquals("Delegation busy", result.error)
    }

    @Test fun `成功读取零张卡与读卡失败不同且不伪造所有SIM`() {
        val lastGood = SimListUiState(listOf(-1, 11, 22), "Old error")
        val result = completeSimListRead(lastGood, SimReadResult(emptyList()), 4, 4, -1)
        assertTrue(result.items.isEmpty())
        assertNull(result.error)
    }

    @Test fun `切换后读卡旧代际成功和失败都不覆盖当前卡片或错误`() {
        val current = SimListUiState(listOf(-1, 33), "Current error")
        assertSame(current, completeSimListRead(current, SimReadResult(listOf(11)), 3, 4, -1))
        assertSame(current, completeSimListRead(current, SimReadResult(emptyList(), "Old error"), 3, 4, -1))
    }

    @Test fun `重新读取成功清除错误并发布真实卡列表`() {
        val current = SimListUiState(listOf(-1, 11), "Read failed")
        val result = completeSimListRead(current, SimReadResult(listOf(22, 33)), 4, 4, -1)
        assertEquals(listOf(-1, 22, 33), result.items)
        assertNull(result.error)
    }
}
