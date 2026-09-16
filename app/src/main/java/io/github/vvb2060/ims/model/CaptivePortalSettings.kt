package io.github.vvb2060.ims.model

import java.net.URI

data class CaptivePortalSettings(
    val httpUrl: String?,
    val httpsUrl: String?,
) {
    val hasOverride: Boolean
        get() = !httpUrl.isNullOrBlank() || !httpsUrl.isNullOrBlank()
}

enum class CaptivePortalUrlError {
    REQUIRED,
    INVALID_URL,
    WRONG_SCHEME,
}

data class CaptivePortalValidationResult(
    val settings: CaptivePortalSettings?,
    val httpError: CaptivePortalUrlError?,
    val httpsError: CaptivePortalUrlError?,
) {
    val isValid: Boolean
        get() = settings != null && httpError == null && httpsError == null
}

fun validateCaptivePortalUrls(
    httpRaw: String,
    httpsRaw: String,
): CaptivePortalValidationResult {
    val http = validateUrl(httpRaw, "http")
    val https = validateUrl(httpsRaw, "https")
    val settings = if (http.error == null && https.error == null) {
        CaptivePortalSettings(http.value, https.value)
    } else {
        null
    }
    return CaptivePortalValidationResult(
        settings = settings,
        httpError = http.error,
        httpsError = https.error,
    )
}

private data class UrlValidation(
    val value: String?,
    val error: CaptivePortalUrlError?,
)

private fun validateUrl(
    raw: String,
    expectedScheme: String,
): UrlValidation {
    val value = raw.trim()
    if (value.isEmpty()) {
        return UrlValidation(null, CaptivePortalUrlError.REQUIRED)
    }

    val uri = try {
        URI(value)
    } catch (_: Exception) {
        return UrlValidation(null, CaptivePortalUrlError.INVALID_URL)
    }

    if (!uri.scheme.equals(expectedScheme, ignoreCase = true)) {
        return UrlValidation(null, CaptivePortalUrlError.WRONG_SCHEME)
    }
    if (uri.host.isNullOrBlank() || !uri.isAbsolute) {
        return UrlValidation(null, CaptivePortalUrlError.INVALID_URL)
    }

    return UrlValidation(value, null)
}
