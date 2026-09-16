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
        putSettingsIntoResult(result)
        result.putBoolean(RESULT_SUCCESS, true)
    }

    private fun write(arguments: Bundle, result: Bundle) {
        val httpUrl = requireNotNull(arguments.getString(HTTP_URL))
        val httpsUrl = requireNotNull(arguments.getString(HTTPS_URL))
        replaceSettings(httpUrl, httpsUrl, result)
    }

    private fun reset(result: Bundle) {
        replaceSettings(null, null, result)
    }

    /**
     * 两个地址必须作为一组更新。任一写入或回读失败时尽力恢复原值，避免留下半套配置。
     */
    private fun replaceSettings(
        targetHttp: String?,
        targetHttps: String?,
        result: Bundle,
    ) {
        val resolver = context.contentResolver
        val originalHttp = Settings.Global.getString(resolver, HTTP_KEY)
        val originalHttps = Settings.Global.getString(resolver, HTTPS_KEY)

        try {
            putValue(HTTP_KEY, targetHttp)
            putValue(HTTPS_KEY, targetHttps)
            verifyValues(targetHttp, targetHttps)
        } catch (failure: Throwable) {
            try {
                // 回滚同样以回读为最终判定，不能只相信 putString 的返回值。
                Settings.Global.putString(resolver, HTTP_KEY, originalHttp)
                Settings.Global.putString(resolver, HTTPS_KEY, originalHttps)
                verifyValues(originalHttp, originalHttps)
            } catch (rollbackFailure: Throwable) {
                failure.addSuppressed(rollbackFailure)
            }
            throw failure
        }

        putSettingsIntoResult(result)
        result.putBoolean(RESULT_SUCCESS, true)
    }

    private fun putValue(key: String, value: String?) {
        val accepted = Settings.Global.putString(context.contentResolver, key, value)
        // 部分 SettingsProvider 实现在删除不存在的 key 时可能返回 false；删除以回读结果为准。
        if (value != null) {
            check(accepted) { "Failed to write $key" }
        }
    }

    private fun verifyValues(expectedHttp: String?, expectedHttps: String?) {
        val resolver = context.contentResolver
        check(Settings.Global.getString(resolver, HTTP_KEY) == expectedHttp) {
            "Captive portal HTTP URL readback mismatch"
        }
        check(Settings.Global.getString(resolver, HTTPS_KEY) == expectedHttps) {
            "Captive portal HTTPS URL readback mismatch"
        }
    }

    private fun putSettingsIntoResult(result: Bundle) {
        val resolver = context.contentResolver
        result.putString(RESULT_HTTP_URL, Settings.Global.getString(resolver, HTTP_KEY))
        result.putString(RESULT_HTTPS_URL, Settings.Global.getString(resolver, HTTPS_KEY))
    }
}
