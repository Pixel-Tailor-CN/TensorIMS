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

        // Settings 的公开 putString(null) 仍走 PUT_global；恢复系统默认需要调用 SettingsProvider 的删除路径。
        // Android 13+ 的 SettingsProvider 使用这一稳定的 call() 方法名处理 global 表删除。
        private const val DELETE_GLOBAL_METHOD = "DELETE_global"
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
                // 回滚同样使用真正的删除路径，并以最终回读为判定，避免原值为 null 时留下空记录。
                putValue(HTTP_KEY, originalHttp)
                putValue(HTTPS_KEY, originalHttps)
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
        val resolver = context.contentResolver
        if (value == null) {
            // 不依赖隐藏 Settings 常量，直接使用 AOSP SettingsProvider 的稳定 call 协议。
            resolver.call(Settings.Global.CONTENT_URI, DELETE_GLOBAL_METHOD, key, null)
            return
        }
        check(Settings.Global.putString(resolver, key, value)) {
            "Failed to write $key"
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
