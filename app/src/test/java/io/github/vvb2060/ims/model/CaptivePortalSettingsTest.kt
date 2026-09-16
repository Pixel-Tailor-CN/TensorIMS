package io.github.vvb2060.ims.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptivePortalSettingsTest {
    @Test
    fun validUrlsAreTrimmedAndAccepted() {
        val result = validateCaptivePortalUrls(
            "  http://example.com/generate_204  ",
            "  https://example.com/generate_204  ",
        )

        assertTrue(result.isValid)
        assertEquals("http://example.com/generate_204", result.settings?.httpUrl)
        assertEquals("https://example.com/generate_204", result.settings?.httpsUrl)
    }

    @Test
    fun emptyHttpUrlIsRequired() {
        val result = validateCaptivePortalUrls("", "https://example.com/generate_204")

        assertFalse(result.isValid)
        assertEquals(CaptivePortalUrlError.REQUIRED, result.httpError)
        assertNull(result.httpsError)
    }

    @Test
    fun httpFieldRejectsHttpsScheme() {
        val result = validateCaptivePortalUrls(
            "https://example.com/generate_204",
            "https://example.com/generate_204",
        )

        assertFalse(result.isValid)
        assertEquals(CaptivePortalUrlError.WRONG_SCHEME, result.httpError)
    }

    @Test
    fun httpsFieldRejectsHttpScheme() {
        val result = validateCaptivePortalUrls(
            "http://example.com/generate_204",
            "http://example.com/generate_204",
        )

        assertFalse(result.isValid)
        assertEquals(CaptivePortalUrlError.WRONG_SCHEME, result.httpsError)
    }

    @Test
    fun urlWithoutHostIsInvalid() {
        val result = validateCaptivePortalUrls("http:///generate_204", "https:///generate_204")

        assertFalse(result.isValid)
        assertEquals(CaptivePortalUrlError.INVALID_URL, result.httpError)
        assertEquals(CaptivePortalUrlError.INVALID_URL, result.httpsError)
    }

    @Test
    fun relativeUrlsAreInvalid() {
        val result = validateCaptivePortalUrls("/generate_204", "generate_204")

        assertFalse(result.isValid)
        assertEquals(CaptivePortalUrlError.WRONG_SCHEME, result.httpError)
        assertEquals(CaptivePortalUrlError.WRONG_SCHEME, result.httpsError)
    }
}
