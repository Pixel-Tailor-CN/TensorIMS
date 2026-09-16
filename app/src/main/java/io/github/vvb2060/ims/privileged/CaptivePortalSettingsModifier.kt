package io.github.vvb2060.ims.privileged

import android.app.Activity
import android.app.Instrumentation
import android.os.Bundle
import android.provider.Settings

class CaptivePortalSettingsModifier : Instrumentation() {
    companion object {
        private const val TAG = "CaptivePortalSettings"

        const val ACTION = "action"
        const val ACTION_READ = "read"
        const val ACTION_WRITE = "write"
        const val ACTION_RESET = "reset"

        const val HTTP_URL = "http_url"
        const val HTTPS_URL = "https_url"

        const val RESULT_SUCCESS = "success"
        const val RESULT_MESSAGE = "message"
        const val RESULT_HTTP_URL = "result_http_url"
        const val RESULT_HTTPS_URL = "result_https_url"

        // NetworkStack 会读取这些 SettingsProvider key；使用字符串避免依赖隐藏字段的 SDK 可见性。
        private const val HTTP_KEY = "captive_portal_http_url"
        private const val HTTPS_KEY = "captive_portal_https_url"
    }

    override fun onCreate(arguments: Bundle) {
        val result = Bundle()
        val failure = runWithShellPermissionDelegation(TAG) {
            when (arguments.getString(ACTION)) {
                ACTION_READ -> read(result)
                ACTION_WRITE -> write(arguments, result)
                ACTION_RESET -> reset(result)
                else -> error("Unknown captive portal action")
            }
        }
        if (failure != null) {
            result.putBoolean(RESULT_SUCCESS, false)
            result.putString(RESULT_MESSAGE, failure.toPrivilegedErrorMessage())
        }
        finish(Activity.RESULT_OK, result)
    }

    private fun read(result: Bundle) {
        val resolver = context.contentResolver
        putSettingsIntoResult(result)
        result.putBoolean(RESULT_SUCCESS, true)
    }

    private fun write(arguments: Bundle, result: Bundle) {
        val httpUrl = requireNotNull(arguments.getString(HTTP_URL))
        val httpsUrl = requireNotNull(arguments.getString(HTTPS_URL))
        val resolver = context.contentResolver

        check(Settings.Global.putString(resolver, HTTP_KEY, httpUrl)) {
            "Failed to write captive portal HTTP URL"
        }
        check(Settings.Global.putString(resolver, HTTPS_KEY, httpsUrl)) {
            "Failed to write captive portal HTTPS URL"
        }

        val storedHttp = Settings.Global.getString(resolver, HTTP_KEY)
        val storedHttps = Settings.Global.getString(resolver, HTTPS_KEY)
        check(storedHttp == httpUrl) { "Captive portal HTTP URL readback mismatch" }
        check(storedHttps == httpsUrl) { "Captive portal HTTPS URL readback mismatch" }

        result.putString(RESULT_HTTP_URL, storedHttp)
        result.putString(RESULT_HTTPS_URL, storedHttps)
        result.putBoolean(RESULT_SUCCESS, true)
    }

    private fun reset(result: Bundle) {
        val resolver = context.contentResolver

        // SettingsProvider 对 null 的具体返回值在不同系统版本可能有差异，因此最终以回读结果判定。
        Settings.Global.putString(resolver, HTTP_KEY, null)
        Settings.Global.putString(resolver, HTTPS_KEY, null)

        val storedHttp = Settings.Global.getString(resolver, HTTP_KEY)
        val storedHttps = Settings.Global.getString(resolver, HTTPS_KEY)
        check(storedHttp == null) { "Captive portal HTTP URL was not cleared" }
        check(storedHttps == null) { "Captive portal HTTPS URL was not cleared" }

        result.putBoolean(RESULT_SUCCESS, true)
    }

    private fun putSettingsIntoResult(result: Bundle) {
        val resolver = context.contentResolver
        result.putString(RESULT_HTTP_URL, Settings.Global.getString(resolver, HTTP_KEY))
        result.putString(RESULT_HTTPS_URL, Settings.Global.getString(resolver, HTTPS_KEY))
    }
}
