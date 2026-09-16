# TensorIMS 设置中心与 Captive Portal 配置实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 TensorIMS 从单页功能堆叠结构改为可扩展的设置中心，并新增设备级 Captive Portal HTTP/HTTPS 地址修改与恢复系统默认功能。

**Architecture:** 保留单 `MainActivity`，使用 Navigation Compose 承载首页、IMS 配置、系统网络、Captive Portal 和高级工具页面；`MainViewModel` 继续管理 SIM、IMS、Shizuku 与持久化 VoLTE，新增 `SystemNetworkViewModel` 专门管理设备级网络设置。Captive Portal 读写通过新的 Instrumentation 复用现有 `ShizukuProvider -> shell permission delegation` 特权链路，与 SIM `Feature`、配置历史和自动恢复完全分离。

**Tech Stack:** Kotlin 2.4.20、Jetpack Compose、Material 3、AndroidX Navigation Compose 2.10.1、StateFlow、Shizuku、Instrumentation、SettingsProvider、JUnit 4。

**Spec:** `docs/plans/2026-09-16-settings-center-captive-portal-design.md`

## Global Constraints

- Android 13 及以上；`compileSdk/targetSdk = 37`、`minSdk = 33`、JVM target 21、仅 `arm64-v8a`。
- 沟通、文档、新增代码注释使用中文；日志 message 使用英文；Git 提交信息使用中文。
- 不引入 Hilt、Dagger、Room；新增依赖仅限本计划明确需要的 `androidx.navigation:navigation-compose:2.10.1`。
- Captive Portal 是设备级设置，不进入 `Feature`、`FeatureConfigMapper`、`sim_config_<subId>` 历史、自动恢复或持久化 VoLTE 流程。
- 首版只读写 `captive_portal_http_url` 和 `captive_portal_https_url`，不修改 portal mode、fallback URL、other URL 或 DeviceConfig。
- “系统默认”通过删除 SettingsProvider 覆盖实现，不硬编码 Google 或第三方默认 URL。
- SettingsProvider 回读一致只表示“已写入系统设置”，UI/README 不宣称 NetworkStack 一定已经采用该地址。
- 所有特权读写继续复用 `ShizukuProvider` 的 Instrumentation 串行互斥和 `runWithShellPermissionDelegation` 错误边界。
- 纯 Kotlin URL 规范化/校验按 TDD 执行；Compose 页面与真实 SettingsProvider 特权写入遵循仓库约定，以 Debug 编译、lint、差异检查和真机验证为准。
- 每个实施任务完成后按 `superpowers:requesting-code-review` 做独立审查；Critical/Important 问题修复后再进入下一任务。

---

## File Structure

### 新增文件

- `app/src/main/java/io/github/vvb2060/ims/model/CaptivePortalSettings.kt`：设备级 Captive Portal 值、URL 校验结果与规范化。
- `app/src/test/java/io/github/vvb2060/ims/model/CaptivePortalSettingsTest.kt`：纯 Kotlin URL 校验单元测试。
- `app/src/main/java/io/github/vvb2060/ims/privileged/CaptivePortalSettingsModifier.kt`：SettingsProvider 读取、写入、删除和读回验证。
- `app/src/main/java/io/github/vvb2060/ims/viewmodel/SystemNetworkViewModel.kt`：Captive Portal 页面状态和保存调度。
- `app/src/main/java/io/github/vvb2060/ims/ui/navigation/TensorImsRoutes.kt`：固定导航 route。
- `app/src/main/java/io/github/vvb2060/ims/ui/screens/HomeScreen.kt`：轻量首页。
- `app/src/main/java/io/github/vvb2060/ims/ui/screens/ImsConfigScreen.kt`：分组 IMS 草稿与底部应用操作。
- `app/src/main/java/io/github/vvb2060/ims/ui/screens/SystemNetworkScreen.kt`：设备级网络设置目录。
- `app/src/main/java/io/github/vvb2060/ims/ui/screens/CaptivePortalScreen.kt`：系统默认/自定义编辑页。
- `app/src/main/java/io/github/vvb2060/ims/ui/screens/AdvancedToolsScreen.kt`：IMS 状态、持久化 VoLTE、重启、重置和日志。
- `app/src/main/java/io/github/vvb2060/ims/ui/components/DeviceStatusCard.kt`：紧凑设备/Shizuku 状态与详情 Dialog。
- `app/src/main/java/io/github/vvb2060/ims/ui/components/SettingsListItem.kt`：首页和分类页统一入口行。

### 修改文件

- `gradle/libs.versions.toml`：增加 Navigation 2.10.1 版本和 `navigation-compose` catalog 项。
- `app/build.gradle.kts`：增加 `implementation(libs.androidx.navigation.compose)`。
- `app/src/main/java/io/github/vvb2060/ims/ui/MainActivity.kt`：缩减为状态收集、NavHost、页面回调和 Activity 生命周期对接。
- `app/src/main/java/io/github/vvb2060/ims/ShizukuProvider.kt`：增加 Captive Portal Instrumentation 调用接口。
- `app/src/main/AndroidManifest.xml`：注册 `CaptivePortalSettingsModifier` Instrumentation。
- `app/src/main/res/values/strings.xml`、`app/src/main/res/values-zh-rCN/strings.xml`：新增导航、分组、Captive Portal、确认和错误文案。
- `README.md`、`README_CN.md`：增加系统网络/Captive Portal 功能与兼容限制。
- `AGENTS.md`：补充新 UI 页面结构和设备级网络特权入口。

现有 `AutoRestoreCard.kt`、`PersistentVolteCard.kt`、`LogcatActivity.kt`、`ConfigurationRepository.kt` 和 `ConfigurationOperations.kt` 继续复用，不改变其数据语义。

---

### Task 1: 增加 Navigation Compose 依赖

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`

**Interfaces:**
- Consumes: 当前 Version Catalog 与 Compose BOM。
- Produces: `libs.androidx.navigation.compose`，供后续 `NavHost` / `rememberNavController` 使用。

> 本任务属于依赖配置，按 TDD skill 的 configuration 例外处理；不新增无意义单元测试，以依赖解析和 Debug 编译作为验证。

- [ ] **Step 1: 在 Version Catalog 增加固定版本和 library alias**

```toml
[versions]
navigation = "2.10.1"

[libraries]
androidx-navigation-compose = { module = "androidx.navigation:navigation-compose", version.ref = "navigation" }
```

将 `navigation` 放在现有 AndroidX 版本区域，不更改 Compose BOM 或 Material 3 版本。

- [ ] **Step 2: 在 app 模块增加依赖**

```kotlin
implementation(libs.androidx.navigation.compose)
```

放在现有 `androidx.activity.compose` 附近，不增加 Kotlin Serialization plugin。

- [ ] **Step 3: 验证依赖可以解析并编译**

```powershell
.\gradlew.bat :app:assembleDebug --stacktrace --console=plain
```

Expected: `BUILD SUCCESSFUL`，没有 dependency resolution error。

- [ ] **Step 4: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts
git commit -m "构建：增加 Compose 导航依赖"
```

---

### Task 2: 用 TDD 定义 Captive Portal 模型与 URL 校验

**Files:**
- Create: `app/src/main/java/io/github/vvb2060/ims/model/CaptivePortalSettings.kt`
- Create: `app/src/test/java/io/github/vvb2060/ims/model/CaptivePortalSettingsTest.kt`

**Interfaces:**
- Consumes: Java `java.net.URI`。
- Produces:
  - `data class CaptivePortalSettings(val httpUrl: String?, val httpsUrl: String?)`
  - `enum class CaptivePortalUrlError { REQUIRED, INVALID_URL, WRONG_SCHEME }`
  - `data class CaptivePortalValidationResult(...)`
  - `fun validateCaptivePortalUrls(httpRaw: String, httpsRaw: String): CaptivePortalValidationResult`

- [ ] **Step 1: 写失败测试**

```kotlin
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
    fun urlsWithoutHostAreInvalid() {
        val result = validateCaptivePortalUrls("http:///generate_204", "https:///generate_204")
        assertFalse(result.isValid)
        assertEquals(CaptivePortalUrlError.INVALID_URL, result.httpError)
        assertEquals(CaptivePortalUrlError.INVALID_URL, result.httpsError)
    }
}
```

- [ ] **Step 2: 运行测试确认 RED**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "io.github.vvb2060.ims.model.CaptivePortalSettingsTest" --stacktrace --console=plain
```

Expected: FAIL，因为 `CaptivePortalSettings` / `validateCaptivePortalUrls` 尚不存在。

- [ ] **Step 3: 写最小实现**

```kotlin
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
    val settings = if (http.second == null && https.second == null) {
        CaptivePortalSettings(http.first, https.first)
    } else {
        null
    }
    return CaptivePortalValidationResult(settings, http.second, https.second)
}

private fun validateUrl(
    raw: String,
    expectedScheme: String,
): Pair<String?, CaptivePortalUrlError?> {
    val value = raw.trim()
    if (value.isEmpty()) return null to CaptivePortalUrlError.REQUIRED
    val uri = try {
        URI(value)
    } catch (_: Exception) {
        return null to CaptivePortalUrlError.INVALID_URL
    }
    if (!uri.scheme.equals(expectedScheme, ignoreCase = true)) {
        return null to CaptivePortalUrlError.WRONG_SCHEME
    }
    if (uri.host.isNullOrBlank()) return null to CaptivePortalUrlError.INVALID_URL
    return value to null
}
```

- [ ] **Step 4: 运行测试确认 GREEN**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "io.github.vvb2060.ims.model.CaptivePortalSettingsTest" --stacktrace --console=plain
```

Expected: all tests PASS。

- [ ] **Step 5: 运行整个 JVM 测试集**

```powershell
.\gradlew.bat :app:testDebugUnitTest --stacktrace --console=plain
```

Expected: PASS。

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/io/github/vvb2060/ims/model/CaptivePortalSettings.kt app/src/test/java/io/github/vvb2060/ims/model/CaptivePortalSettingsTest.kt
git commit -m "功能：增加联网检测地址校验模型"
```

---

### Task 3: 增加 Captive Portal 特权读取/写入链路

**Files:**
- Create: `app/src/main/java/io/github/vvb2060/ims/privileged/CaptivePortalSettingsModifier.kt`
- Modify: `app/src/main/java/io/github/vvb2060/ims/ShizukuProvider.kt`
- Modify: `app/src/main/AndroidManifest.xml`

**Interfaces:**
- Consumes: `runWithShellPermissionDelegation`、`ShizukuProvider.startInstrumentation`。
- Produces:
  - `CaptivePortalSettingsModifier.ACTION_READ`
  - `CaptivePortalSettingsModifier.ACTION_WRITE`
  - `CaptivePortalSettingsModifier.ACTION_RESET`
  - `ShizukuProvider.readCaptivePortalSettings(context): Pair<CaptivePortalSettings?, String?>`
  - `ShizukuProvider.writeCaptivePortalSettings(context, settings): String?`
  - `ShizukuProvider.resetCaptivePortalSettings(context): String?`

> SettingsProvider 写入依赖真实 shell permission delegation，当前 JVM 单测不能证明系统写入行为；本任务以 Debug 编译 + Task 9 真机读回作为验收，不写 mock 成功测试。

- [ ] **Step 1: 创建 Instrumentation 协议和 SettingsProvider 操作**

```kotlin
package io.github.vvb2060.ims.privileged

import android.app.Activity
import android.app.Instrumentation
import android.os.Bundle
import android.provider.Settings

class CaptivePortalSettingsModifier : Instrumentation() {
    companion object {
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

        // AOSP NetworkStack 使用的 SettingsProvider key；字符串形式避免依赖隐藏字段可见性。
        private const val HTTP_KEY = "captive_portal_http_url"
        private const val HTTPS_KEY = "captive_portal_https_url"
    }

    override fun onCreate(arguments: Bundle) {
        val result = Bundle()
        val failure = runWithShellPermissionDelegation("CaptivePortalSettings") {
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
        result.putString(RESULT_HTTP_URL, Settings.Global.getString(resolver, HTTP_KEY))
        result.putString(RESULT_HTTPS_URL, Settings.Global.getString(resolver, HTTPS_KEY))
        result.putBoolean(RESULT_SUCCESS, true)
    }

    private fun write(arguments: Bundle, result: Bundle) {
        val http = requireNotNull(arguments.getString(HTTP_URL))
        val https = requireNotNull(arguments.getString(HTTPS_URL))
        val resolver = context.contentResolver
        check(Settings.Global.putString(resolver, HTTP_KEY, http)) { "Failed to write HTTP URL" }
        check(Settings.Global.putString(resolver, HTTPS_KEY, https)) { "Failed to write HTTPS URL" }
        check(Settings.Global.getString(resolver, HTTP_KEY) == http) { "HTTP URL readback mismatch" }
        check(Settings.Global.getString(resolver, HTTPS_KEY) == https) { "HTTPS URL readback mismatch" }
        result.putBoolean(RESULT_SUCCESS, true)
    }

    private fun reset(result: Bundle) {
        val resolver = context.contentResolver
        Settings.Global.putString(resolver, HTTP_KEY, null)
        Settings.Global.putString(resolver, HTTPS_KEY, null)
        check(Settings.Global.getString(resolver, HTTP_KEY) == null) { "HTTP URL was not cleared" }
        check(Settings.Global.getString(resolver, HTTPS_KEY) == null) { "HTTPS URL was not cleared" }
        result.putBoolean(RESULT_SUCCESS, true)
    }
}
```

恢复默认以读回结果作为最终成功判据，避免“删除一个本来就不存在的 key”因 API 布尔返回值产生假失败。

- [ ] **Step 2: 注册 Instrumentation**

```xml
<instrumentation
    android:name=".privileged.CaptivePortalSettingsModifier"
    android:label="CaptivePortalSettingsModifier"
    android:targetPackage="${applicationId}" />
```

不新增 manifest `WRITE_SECURE_SETTINGS` 普通权限声明；实际身份来自现有 shell permission delegation。

- [ ] **Step 3: 给 ShizukuProvider 增加读写接口**

```kotlin
suspend fun readCaptivePortalSettings(context: Context): Pair<CaptivePortalSettings?, String?> {
    val args = Bundle().apply {
        putString(CaptivePortalSettingsModifier.ACTION, CaptivePortalSettingsModifier.ACTION_READ)
    }
    val result = startInstrumentation(context, CaptivePortalSettingsModifier::class.java, args, true)
        ?: return null to "No instrumentation result"
    if (!result.getBoolean(CaptivePortalSettingsModifier.RESULT_SUCCESS)) {
        return null to (result.getString(CaptivePortalSettingsModifier.RESULT_MESSAGE)
            ?: "Incomplete captive portal result")
    }
    return CaptivePortalSettings(
        httpUrl = result.getString(CaptivePortalSettingsModifier.RESULT_HTTP_URL),
        httpsUrl = result.getString(CaptivePortalSettingsModifier.RESULT_HTTPS_URL),
    ) to null
}

suspend fun writeCaptivePortalSettings(
    context: Context,
    settings: CaptivePortalSettings,
): String? {
    val http = requireNotNull(settings.httpUrl)
    val https = requireNotNull(settings.httpsUrl)
    val args = Bundle().apply {
        putString(CaptivePortalSettingsModifier.ACTION, CaptivePortalSettingsModifier.ACTION_WRITE)
        putString(CaptivePortalSettingsModifier.HTTP_URL, http)
        putString(CaptivePortalSettingsModifier.HTTPS_URL, https)
    }
    val result = startInstrumentation(context, CaptivePortalSettingsModifier::class.java, args, true)
        ?: return "No instrumentation result"
    return if (result.getBoolean(CaptivePortalSettingsModifier.RESULT_SUCCESS)) null
    else result.getString(CaptivePortalSettingsModifier.RESULT_MESSAGE)
        ?: "Incomplete captive portal result"
}

suspend fun resetCaptivePortalSettings(context: Context): String? {
    val args = Bundle().apply {
        putString(CaptivePortalSettingsModifier.ACTION, CaptivePortalSettingsModifier.ACTION_RESET)
    }
    val result = startInstrumentation(context, CaptivePortalSettingsModifier::class.java, args, true)
        ?: return "No instrumentation result"
    return if (result.getBoolean(CaptivePortalSettingsModifier.RESULT_SUCCESS)) null
    else result.getString(CaptivePortalSettingsModifier.RESULT_MESSAGE)
        ?: "Incomplete captive portal result"
}
```

`writeCaptivePortalSettings` 只接收 Task 2 校验后生成的非空 URL；ViewModel 不绕过校验调用自定义写入。

- [ ] **Step 4: Debug 编译**

```powershell
.\gradlew.bat :app:assembleDebug --stacktrace --console=plain
```

Expected: `BUILD SUCCESSFUL`。

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/io/github/vvb2060/ims/privileged/CaptivePortalSettingsModifier.kt app/src/main/java/io/github/vvb2060/ims/ShizukuProvider.kt app/src/main/AndroidManifest.xml
git commit -m "功能：增加联网检测地址特权读写"
```

---

### Task 4: 新增 SystemNetworkViewModel

**Files:**
- Create: `app/src/main/java/io/github/vvb2060/ims/viewmodel/SystemNetworkViewModel.kt`

**Interfaces:**
- Consumes: Task 2 `validateCaptivePortalUrls`，Task 3 `ShizukuProvider` 三个接口。
- Produces:
  - `enum class CaptivePortalMode { SYSTEM_DEFAULT, CUSTOM }`
  - `enum class CaptivePortalNotice { NONE, SAVED, RESET }`
  - `data class CaptivePortalUiState(...)`
  - `loadCaptivePortal()` / `setMode()` / `setHttpUrl()` / `setHttpsUrl()` / `saveCaptivePortal()` / `clearNotice()`

- [ ] **Step 1: 创建稳定 UI state**

```kotlin
enum class CaptivePortalMode { SYSTEM_DEFAULT, CUSTOM }
enum class CaptivePortalNotice { NONE, SAVED, RESET }

data class CaptivePortalUiState(
    val loading: Boolean = false,
    val saving: Boolean = false,
    val mode: CaptivePortalMode = CaptivePortalMode.SYSTEM_DEFAULT,
    val httpUrl: String = "",
    val httpsUrl: String = "",
    val storedSettings: CaptivePortalSettings = CaptivePortalSettings(null, null),
    val httpError: CaptivePortalUrlError? = null,
    val httpsError: CaptivePortalUrlError? = null,
    val operationError: String? = null,
    val notice: CaptivePortalNotice = CaptivePortalNotice.NONE,
)
```

ViewModel 使用 `MutableStateFlow(CaptivePortalUiState())`，只对外暴露 `asStateFlow()`。

- [ ] **Step 2: 实现读取逻辑**

```kotlin
fun loadCaptivePortal() {
    if (_uiState.value.loading || _uiState.value.saving) return
    viewModelScope.launch {
        _uiState.value = _uiState.value.copy(loading = true, operationError = null)
        val (settings, error) = ShizukuProvider.readCaptivePortalSettings(application)
        if (settings == null) {
            _uiState.value = _uiState.value.copy(loading = false, operationError = error)
            return@launch
        }
        _uiState.value = CaptivePortalUiState(
            storedSettings = settings,
            mode = if (settings.hasOverride) CaptivePortalMode.CUSTOM
            else CaptivePortalMode.SYSTEM_DEFAULT,
            httpUrl = settings.httpUrl.orEmpty(),
            httpsUrl = settings.httpsUrl.orEmpty(),
        )
    }
}
```

- [ ] **Step 3: 实现编辑函数**

```kotlin
fun setMode(mode: CaptivePortalMode) {
    _uiState.value = _uiState.value.copy(
        mode = mode,
        operationError = null,
        notice = CaptivePortalNotice.NONE,
    )
}

fun setHttpUrl(value: String) {
    _uiState.value = _uiState.value.copy(
        httpUrl = value,
        httpError = null,
        operationError = null,
        notice = CaptivePortalNotice.NONE,
    )
}

fun setHttpsUrl(value: String) {
    _uiState.value = _uiState.value.copy(
        httpsUrl = value,
        httpsError = null,
        operationError = null,
        notice = CaptivePortalNotice.NONE,
    )
}

fun clearNotice() {
    _uiState.value = _uiState.value.copy(notice = CaptivePortalNotice.NONE)
}
```

切换到“系统默认”不清空输入缓存，用户切回自定义时仍能继续本次编辑。

- [ ] **Step 4: 实现保存与保存后真实回读**

```kotlin
fun saveCaptivePortal() {
    if (_uiState.value.saving) return
    val snapshot = _uiState.value
    if (snapshot.mode == CaptivePortalMode.CUSTOM) {
        val validation = validateCaptivePortalUrls(snapshot.httpUrl, snapshot.httpsUrl)
        if (!validation.isValid) {
            _uiState.value = snapshot.copy(
                httpError = validation.httpError,
                httpsError = validation.httpsError,
                operationError = null,
                notice = CaptivePortalNotice.NONE,
            )
            return
        }
        val settings = requireNotNull(validation.settings)
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(saving = true, operationError = null)
            val error = ShizukuProvider.writeCaptivePortalSettings(application, settings)
            if (error == null) loadAfterSave(CaptivePortalNotice.SAVED)
            else _uiState.value = _uiState.value.copy(saving = false, operationError = error)
        }
        return
    }

    viewModelScope.launch {
        _uiState.value = _uiState.value.copy(saving = true, operationError = null)
        val error = ShizukuProvider.resetCaptivePortalSettings(application)
        if (error == null) loadAfterSave(CaptivePortalNotice.RESET)
        else _uiState.value = _uiState.value.copy(saving = false, operationError = error)
    }
}

private suspend fun loadAfterSave(notice: CaptivePortalNotice) {
    val (settings, error) = ShizukuProvider.readCaptivePortalSettings(application)
    if (settings == null) {
        _uiState.value = _uiState.value.copy(
            saving = false,
            operationError = error ?: "Failed to verify saved settings",
            notice = CaptivePortalNotice.NONE,
        )
        return
    }
    _uiState.value = CaptivePortalUiState(
        storedSettings = settings,
        mode = if (settings.hasOverride) CaptivePortalMode.CUSTOM
        else CaptivePortalMode.SYSTEM_DEFAULT,
        httpUrl = settings.httpUrl.orEmpty(),
        httpsUrl = settings.httpsUrl.orEmpty(),
        notice = notice,
    )
}
```

保存成功通知只能在再次读取 provider 成功之后出现。

- [ ] **Step 5: Debug 编译**

```powershell
.\gradlew.bat :app:assembleDebug --stacktrace --console=plain
```

Expected: PASS。

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/io/github/vvb2060/ims/viewmodel/SystemNetworkViewModel.kt
git commit -m "功能：增加系统网络配置状态管理"
```

---

### Task 5: 建立导航壳与轻量首页

**Files:**
- Create: `app/src/main/java/io/github/vvb2060/ims/ui/navigation/TensorImsRoutes.kt`
- Create: `app/src/main/java/io/github/vvb2060/ims/ui/screens/HomeScreen.kt`
- Create: `app/src/main/java/io/github/vvb2060/ims/ui/components/DeviceStatusCard.kt`
- Create: `app/src/main/java/io/github/vvb2060/ims/ui/components/SettingsListItem.kt`
- Modify: `app/src/main/java/io/github/vvb2060/ims/ui/MainActivity.kt`

**Interfaces:**
- Consumes: `MainViewModel`、`SystemNetworkViewModel`、现有 `AutoRestoreCard`。
- Produces: 5 个固定 route、Activity 级 selected SIM 状态、轻量首页和后续页面的完整回调接线。

- [ ] **Step 1: 定义固定 routes**

```kotlin
package io.github.vvb2060.ims.ui.navigation

object TensorImsRoutes {
    const val HOME = "home"
    const val IMS_CONFIG = "ims_config"
    const val SYSTEM_NETWORK = "system_network"
    const val CAPTIVE_PORTAL = "captive_portal"
    const val ADVANCED_TOOLS = "advanced_tools"
}
```

不通过 route argument 传 `SimSelection`。

- [ ] **Step 2: 创建两个通用首页组件**

`SettingsListItem` 使用整行点击与 48dp 以上触控区：

```kotlin
@Composable
fun SettingsListItem(
    title: String,
    summary: String,
    enabled: Boolean = true,
    leadingContent: @Composable (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick),
        headlineContent = { Text(title) },
        supportingContent = { Text(summary) },
        leadingContent = leadingContent,
        trailingContent = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null) },
    )
}
```

`DeviceStatusCard` 接收 `SystemInfo`、`ShizukuStatus`、刷新/授权/详情回调；READY 时只显示状态和详情入口，`NOT_RUNNING`/`NO_PERMISSION`/`NEED_UPDATE` 时分别显示对应操作。所有状态同时显示文字，不只依赖颜色。

- [ ] **Step 3: 创建 HomeScreen**

```kotlin
@Composable
fun HomeScreen(
    systemInfo: SystemInfo,
    shizukuStatus: ShizukuStatus,
    allSimList: List<SimSelection>,
    selectedSim: SimSelection?,
    autoRestoreEnabled: Boolean,
    onSelectSim: (SimSelection) -> Unit,
    onRefresh: () -> Unit,
    onRequestShizukuPermission: () -> Unit,
    onOpenImsConfig: () -> Unit,
    onOpenSystemNetwork: () -> Unit,
    onOpenAdvancedTools: () -> Unit,
    onAutoRestoreEnabledChange: (Boolean) -> Unit,
) {
    Scaffold(topBar = { CenterAlignedTopAppBar(title = { Text(stringResource(R.string.app_name)) }) }) { padding ->
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DeviceStatusCard(
                systemInfo = systemInfo,
                shizukuStatus = shizukuStatus,
                onRefresh = onRefresh,
                onRequestPermission = onRequestShizukuPermission,
            )
            SimSelectorCard(allSimList, selectedSim, onSelectSim)
            Card(Modifier.padding(horizontal = 16.dp)) {
                Column {
                    SettingsListItem(
                        title = stringResource(R.string.ims_config),
                        summary = stringResource(R.string.ims_config_summary),
                        enabled = selectedSim != null,
                        onClick = onOpenImsConfig,
                    )
                    HorizontalDivider()
                    SettingsListItem(
                        title = stringResource(R.string.system_network),
                        summary = stringResource(R.string.system_network_summary),
                        onClick = onOpenSystemNetwork,
                    )
                    HorizontalDivider()
                    SettingsListItem(
                        title = stringResource(R.string.advanced_tools),
                        summary = stringResource(R.string.advanced_tools_summary),
                        onClick = onOpenAdvancedTools,
                    )
                }
            }
            AutoRestoreCard(autoRestoreEnabled, onAutoRestoreEnabledChange)
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }
}
```

`SimSelectorCard` 从现有 `SimCardSelectionCard` 提取，仅保留 SIM 列表/选择，不保留“查看系统配置”和“重启 IMS”。`DeviceStatusCard` 的详情 Dialog 继续展示 app version、build、安全补丁与 GitHub 入口。

- [ ] **Step 4: 把 MainActivity 改为完整 NavHost 接线**

Activity 字段：

```kotlin
private val viewModel: MainViewModel by viewModels()
private val systemNetworkViewModel: SystemNetworkViewModel by viewModels()
```

`content()` 的核心接线按以下代码实现；各 screen 的签名由 Tasks 6-8 固定，实施时同一提交必须保证编译通过：

```kotlin
val systemInfo by viewModel.systemInfo.collectAsStateWithLifecycle()
val shizukuStatus by viewModel.shizukuStatus.collectAsStateWithLifecycle()
val allSimList by viewModel.allSimList.collectAsStateWithLifecycle()
val isOperationInProgress by viewModel.isOperationInProgress.collectAsStateWithLifecycle()
val persistentVolteState by viewModel.persistentVolteState.collectAsStateWithLifecycle()
val autoRestoreEnabled by viewModel.autoRestoreEnabled.collectAsStateWithLifecycle()
val captivePortalState by systemNetworkViewModel.uiState.collectAsStateWithLifecycle()

var selectedSubId by rememberSaveable { mutableStateOf<Int?>(null) }
val selectedSim = allSimList.firstOrNull { it.subId == selectedSubId }

LaunchedEffect(allSimList, selectedSubId) {
    if (selectedSubId != null && selectedSim == null) selectedSubId = null
}
LaunchedEffect(selectedSim?.subId, shizukuStatus) {
    viewModel.selectPersistentVolteSim(
        selectedSim?.subId?.takeIf { it >= 0 && shizukuStatus == ShizukuStatus.READY },
    )
}

val navController = rememberNavController()
NavHost(navController, startDestination = TensorImsRoutes.HOME) {
    composable(TensorImsRoutes.HOME) {
        HomeScreen(
            systemInfo = systemInfo,
            shizukuStatus = shizukuStatus,
            allSimList = allSimList,
            selectedSim = selectedSim,
            autoRestoreEnabled = autoRestoreEnabled,
            onSelectSim = { selectedSubId = it.subId },
            onRefresh = {
                viewModel.updateShizukuStatus()
                viewModel.loadSimList()
            },
            onRequestShizukuPermission = { viewModel.requestShizukuPermission(0) },
            onOpenImsConfig = { navController.navigate(TensorImsRoutes.IMS_CONFIG) },
            onOpenSystemNetwork = { navController.navigate(TensorImsRoutes.SYSTEM_NETWORK) },
            onOpenAdvancedTools = { navController.navigate(TensorImsRoutes.ADVANCED_TOOLS) },
            onAutoRestoreEnabledChange = viewModel::setAutoRestoreEnabled,
        )
    }
    composable(TensorImsRoutes.IMS_CONFIG) {
        ImsConfigScreen(
            selectedSim = selectedSim,
            shizukuStatus = shizukuStatus,
            isOperationInProgress = isOperationInProgress,
            loadConfiguration = viewModel::loadConfiguration,
            loadDefaults = viewModel::loadDefaultPreferences,
            onApplyConfiguration = viewModel::onApplyConfiguration,
            onBack = { navController.popBackStack() },
        )
    }
    composable(TensorImsRoutes.SYSTEM_NETWORK) {
        SystemNetworkScreen(
            state = captivePortalState,
            onLoad = systemNetworkViewModel::loadCaptivePortal,
            onOpenCaptivePortal = { navController.navigate(TensorImsRoutes.CAPTIVE_PORTAL) },
            onBack = { navController.popBackStack() },
        )
    }
    composable(TensorImsRoutes.CAPTIVE_PORTAL) {
        CaptivePortalScreen(
            state = captivePortalState,
            shizukuStatus = shizukuStatus,
            onSetMode = systemNetworkViewModel::setMode,
            onHttpUrlChange = systemNetworkViewModel::setHttpUrl,
            onHttpsUrlChange = systemNetworkViewModel::setHttpsUrl,
            onSave = systemNetworkViewModel::saveCaptivePortal,
            onClearNotice = systemNetworkViewModel::clearNotice,
            onBack = { navController.popBackStack() },
        )
    }
    composable(TensorImsRoutes.ADVANCED_TOOLS) {
        AdvancedToolsScreen(
            selectedSim = selectedSim,
            shizukuStatus = shizukuStatus,
            isOperationInProgress = isOperationInProgress,
            persistentVolteState = persistentVolteState?.takeIf { it.subId == selectedSim?.subId },
            onLoadImsStatus = viewModel::loadRealSystemConfig,
            onEnablePersistentVolte = { subId -> viewModel.onPersistentVolteChange(subId, false) },
            onRestorePersistentVolte = { subId -> viewModel.onPersistentVolteChange(subId, true) },
            onRefreshPersistentVolte = viewModel::refreshPersistentVolte,
            onRestartIms = viewModel::onResetIms,
            onResetConfiguration = viewModel::onResetConfiguration,
            onOpenLogcat = {
                startActivity(Intent(this@MainActivity, LogcatActivity::class.java))
            },
            onBack = { navController.popBackStack() },
        )
    }
}
```

- [ ] **Step 5: 保留 Activity 生命周期刷新**

```kotlin
override fun onResume() {
    super.onResume()
    viewModel.updateShizukuStatus()
    viewModel.refreshPersistentVolte()
}
```

- [ ] **Step 6: 编译并 Commit**

```powershell
.\gradlew.bat :app:assembleDebug --stacktrace --console=plain
```

```bash
git add app/src/main/java/io/github/vvb2060/ims/ui app/src/main/java/io/github/vvb2060/ims/ui/navigation app/src/main/java/io/github/vvb2060/ims/ui/screens/HomeScreen.kt
git commit -m "界面：建立设置中心导航与首页"
```

---

### Task 6: 把 IMS Feature 重构为独立分组页面

**Files:**
- Create: `app/src/main/java/io/github/vvb2060/ims/ui/screens/ImsConfigScreen.kt`
- Modify: `app/src/main/java/io/github/vvb2060/ims/ui/MainActivity.kt`（删除已迁移的旧 Features UI helper）

**Interfaces:**
- Consumes: `Feature`、`FeatureValue`、`MainViewModel.loadConfiguration/loadDefaultPreferences/onApplyConfiguration`。
- Produces: `ImsConfigScreen(...)`，不暴露重置运营商配置动作。

- [ ] **Step 1: 固定页面接口和 Feature 分组**

```kotlin
@Composable
fun ImsConfigScreen(
    selectedSim: SimSelection?,
    shizukuStatus: ShizukuStatus,
    isOperationInProgress: Boolean,
    loadConfiguration: (Int) -> Map<Feature, FeatureValue>?,
    loadDefaults: () -> Map<Feature, FeatureValue>,
    onApplyConfiguration: (SimSelection, Map<Feature, FeatureValue>) -> Unit,
    onBack: () -> Unit,
)
```

```kotlin
private val callFeatures = listOf(
    Feature.VOLTE,
    Feature.VOWIFI,
    Feature.VOWIFI_ROAMING,
    Feature.VONR,
    Feature.VT,
    Feature.CROSS_SIM,
    Feature.UT,
)
private val networkFeatures = listOf(Feature.FIVE_G_NR, Feature.FIVE_G_THRESHOLDS)
private val displayFeatures = listOf(
    Feature.FIVE_G_PLUS_ICON,
    Feature.ENHANCED_4G_LTE,
    Feature.HIDE_LTE_PLUS_DATA_ICON,
    Feature.SHOW_4G_FOR_LTE,
)
private val advancedFeatures = listOf(Feature.CARRIER_NAME, Feature.IMS_USER_AGENT)
```

- [ ] **Step 2: 把草稿状态留在 IMS 页面并在 SIM 变化时重载**

```kotlin
val featureSwitches = remember { mutableStateMapOf<Feature, FeatureValue>() }

LaunchedEffect(selectedSim?.subId) {
    featureSwitches.clear()
    selectedSim?.let { sim ->
        featureSwitches.putAll(loadConfiguration(sim.subId) ?: loadDefaults())
    }
}
```

历史加载操作使用 `loadConfiguration(requireNotNull(selectedSim).subId)`；“恢复默认草稿”只调用 `loadDefaults()`，不执行系统写入。

- [ ] **Step 3: 布尔项改成整行设置项，字符串项改成编辑 Dialog**

布尔项：

```kotlin
@Composable
private fun BooleanFeatureRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(description, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}
```

字符串项显示当前值摘要，点击后用 `AlertDialog + OutlinedTextField` 编辑并回写 `FeatureValue(value, FeatureValueType.STRING)`；不在主列表长期展示 TextField。

- [ ] **Step 4: 用 section + sticky bottom action 组织页面**

```kotlin
Scaffold(
    topBar = { ImsConfigTopBar(onBack, onLoadHistory, onRestoreDraftDefaults) },
    bottomBar = {
        Surface(tonalElevation = 3.dp) {
            Button(
                modifier = Modifier.fillMaxWidth().padding(16.dp).heightIn(min = 48.dp),
                enabled = selectedSim != null && !isOperationInProgress &&
                    shizukuStatus == ShizukuStatus.READY,
                onClick = {
                    selectedSim?.let { onApplyConfiguration(it, featureSwitches.toMap()) }
                },
            ) { Text(stringResource(R.string.apply_changes)) }
        }
    },
) { padding ->
    LazyColumn(contentPadding = padding) {
        featureSection(R.string.calling, callFeatures, featureSwitches, onFeatureChange)
        featureSection(R.string.network, networkFeatures, featureSwitches, onFeatureChange)
        featureSection(R.string.display, displayFeatures, featureSwitches, onFeatureChange)
        featureSection(R.string.advanced_overrides, advancedFeatures, featureSwitches, onFeatureChange)
        item { ImsTips() }
    }
}
```

`featureSection` 是同文件私有 helper，按 `FeatureValueType` 调用 Boolean 或 String row。原“重置运营商配置”不出现在本页。

- [ ] **Step 5: 编译并 Commit**

```powershell
.\gradlew.bat :app:assembleDebug --stacktrace --console=plain
```

```bash
git add app/src/main/java/io/github/vvb2060/ims/ui/screens/ImsConfigScreen.kt app/src/main/java/io/github/vvb2060/ims/ui/MainActivity.kt
git commit -m "界面：重构 IMS 配置分组页面"
```

---

### Task 7: 实现系统网络目录与 Captive Portal 编辑页

**Files:**
- Create: `app/src/main/java/io/github/vvb2060/ims/ui/screens/SystemNetworkScreen.kt`
- Create: `app/src/main/java/io/github/vvb2060/ims/ui/screens/CaptivePortalScreen.kt`

**Interfaces:**
- Consumes: Task 4 `CaptivePortalUiState` 与操作回调。
- Produces: `SystemNetworkScreen(...)`、`CaptivePortalScreen(...)`。

- [ ] **Step 1: 实现 SystemNetworkScreen 接口与自动加载**

```kotlin
@Composable
fun SystemNetworkScreen(
    state: CaptivePortalUiState,
    onLoad: () -> Unit,
    onOpenCaptivePortal: () -> Unit,
    onBack: () -> Unit,
) {
    LaunchedEffect(Unit) { onLoad() }
    val summary = when {
        state.loading -> stringResource(R.string.loading)
        state.operationError != null -> stringResource(R.string.status_unavailable)
        state.storedSettings.hasOverride -> stringResource(R.string.captive_portal_custom)
        else -> stringResource(R.string.captive_portal_system_default)
    }
    Scaffold(topBar = { BackTopAppBar(R.string.system_network, onBack) }) { padding ->
        Column(Modifier.padding(padding)) {
            Text(
                stringResource(R.string.connectivity_detection),
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.titleSmall,
            )
            SettingsListItem(
                title = stringResource(R.string.captive_portal),
                summary = summary,
                onClick = onOpenCaptivePortal,
            )
        }
    }
}
```

- [ ] **Step 2: 固定 CaptivePortalScreen 接口**

```kotlin
@Composable
fun CaptivePortalScreen(
    state: CaptivePortalUiState,
    shizukuStatus: ShizukuStatus,
    onSetMode: (CaptivePortalMode) -> Unit,
    onHttpUrlChange: (String) -> Unit,
    onHttpsUrlChange: (String) -> Unit,
    onSave: () -> Unit,
    onClearNotice: () -> Unit,
    onBack: () -> Unit,
)
```

- [ ] **Step 3: 实现模式选择、字段和错误映射**

```kotlin
@Composable
private fun captivePortalErrorText(
    error: CaptivePortalUrlError?,
    expectedScheme: String,
): String? = when (error) {
    CaptivePortalUrlError.REQUIRED -> stringResource(R.string.captive_portal_error_required)
    CaptivePortalUrlError.INVALID_URL -> stringResource(R.string.captive_portal_error_invalid_url)
    CaptivePortalUrlError.WRONG_SCHEME -> stringResource(
        R.string.captive_portal_error_wrong_scheme,
        expectedScheme,
    )
    null -> null
}
```

页面始终显示实验性限制说明；使用两行 `RadioButton` 选择 `SYSTEM_DEFAULT` / `CUSTOM`。只有 CUSTOM 模式显示：

```kotlin
OutlinedTextField(
    value = state.httpUrl,
    onValueChange = onHttpUrlChange,
    label = { Text(stringResource(R.string.captive_portal_http_url)) },
    isError = state.httpError != null,
    supportingText = captivePortalErrorText(state.httpError, "http")?.let { text -> { Text(text) } },
    singleLine = true,
)
OutlinedTextField(
    value = state.httpsUrl,
    onValueChange = onHttpsUrlChange,
    label = { Text(stringResource(R.string.captive_portal_https_url)) },
    isError = state.httpsError != null,
    supportingText = captivePortalErrorText(state.httpsError, "https")?.let { text -> { Text(text) } },
    singleLine = true,
)
```

- [ ] **Step 4: 实现保存按钮和一次性结果提示**

```kotlin
Button(
    onClick = onSave,
    enabled = !state.loading && !state.saving && shizukuStatus == ShizukuStatus.READY,
    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
) {
    if (state.saving) CircularProgressIndicator(Modifier.size(20.dp))
    else Text(stringResource(R.string.save))
}
```

```kotlin
LaunchedEffect(state.notice) {
    when (state.notice) {
        CaptivePortalNotice.SAVED -> snackbarHostState.showSnackbar(
            context.getString(R.string.captive_portal_saved_notice),
        )
        CaptivePortalNotice.RESET -> snackbarHostState.showSnackbar(
            context.getString(R.string.captive_portal_reset_notice),
        )
        CaptivePortalNotice.NONE -> return@LaunchedEffect
    }
    onClearNotice()
}
```

保存成功文案固定表达“已写入系统设置；重新连接网络后验证是否生效”，不写“已生效”。`state.operationError` 使用错误 Snackbar 或页面内 error text 展示，但绝不触发成功 notice。

- [ ] **Step 5: 编译并 Commit**

```powershell
.\gradlew.bat :app:assembleDebug --stacktrace --console=plain
```

```bash
git add app/src/main/java/io/github/vvb2060/ims/ui/screens/SystemNetworkScreen.kt app/src/main/java/io/github/vvb2060/ims/ui/screens/CaptivePortalScreen.kt
git commit -m "界面：增加系统网络与联网检测设置"
```

---

### Task 8: 把即时操作迁移到高级工具

**Files:**
- Create: `app/src/main/java/io/github/vvb2060/ims/ui/screens/AdvancedToolsScreen.kt`
- Modify: `app/src/main/java/io/github/vvb2060/ims/ui/MainActivity.kt`（删除旧主页面对应布局 helper）

**Interfaces:**
- Consumes: 现有 `SystemConfigDialog`、`PersistentVolteCard`、`MainViewModel` 业务函数、`LogcatActivity`。
- Produces: `AdvancedToolsScreen(...)`，所有危险/即时操作集中在单独页面。

- [ ] **Step 1: 固定页面接口**

```kotlin
@Composable
fun AdvancedToolsScreen(
    selectedSim: SimSelection?,
    shizukuStatus: ShizukuStatus,
    isOperationInProgress: Boolean,
    persistentVolteState: PersistentVolteState?,
    onLoadImsStatus: suspend (Int) -> ImsCapabilityStatus?,
    onEnablePersistentVolte: (Int) -> Unit,
    onRestorePersistentVolte: (Int) -> Unit,
    onRefreshPersistentVolte: () -> Unit,
    onRestartIms: (SimSelection) -> Unit,
    onResetConfiguration: (SimSelection) -> Unit,
    onOpenLogcat: () -> Unit,
    onBack: () -> Unit,
)
```

- [ ] **Step 2: 实现状态与日志入口**

```kotlin
val singleSim = selectedSim?.takeIf { it.subId >= 0 }
val scope = rememberCoroutineScope()
var imsStatus by remember { mutableStateOf<ImsCapabilityStatus?>(null) }
var showImsDialog by remember { mutableStateOf(false) }

SettingsListItem(
    title = stringResource(R.string.system_config_title),
    summary = stringResource(R.string.view_system_config_summary),
    enabled = singleSim != null && shizukuStatus == ShizukuStatus.READY,
    onClick = {
        val sim = singleSim ?: return@SettingsListItem
        scope.launch {
            imsStatus = onLoadImsStatus(sim.subId)
            showImsDialog = imsStatus != null
        }
    },
)
SettingsListItem(
    title = stringResource(R.string.logcat),
    summary = stringResource(R.string.logcat_summary),
    onClick = onOpenLogcat,
)
```

`showImsDialog && imsStatus != null` 时继续复用 `SystemConfigDialog`。

- [ ] **Step 3: 迁移 PersistentVolteCard**

```kotlin
PersistentVolteCard(
    state = persistentVolteState,
    singleSimSelected = singleSim != null,
    shizukuReady = shizukuStatus == ShizukuStatus.READY,
    busy = isOperationInProgress,
    onEnable = { singleSim?.let { onEnablePersistentVolte(it.subId) } },
    onRestore = { singleSim?.let { onRestorePersistentVolte(it.subId) } },
    onRefresh = onRefreshPersistentVolte,
)
```

不改变备份文件、恢复规则和自动恢复排除语义。

- [ ] **Step 4: 给重启 IMS 与重置运营商配置增加确认 Dialog**

两个入口仅在 `singleSim != null && shizukuStatus == READY && !isOperationInProgress` 时可用。确认状态使用 `enum class ConfirmAction { RESTART_IMS, RESET_CARRIER_CONFIG }?`；Dialog 正文包含 `singleSim.showTitle`。

确认回调：

```kotlin
when (confirmAction) {
    ConfirmAction.RESTART_IMS -> singleSim?.let(onRestartIms)
    ConfirmAction.RESET_CARRIER_CONFIG -> singleSim?.let(onResetConfiguration)
    null -> Unit
}
confirmAction = null
```

重置仍调用现有 `MainViewModel.onResetConfiguration`，继续保持“先恢复持久化 VoLTE 原值，再清 CarrierConfig”的既有业务顺序。

- [ ] **Step 5: 编译并 Commit**

```powershell
.\gradlew.bat :app:assembleDebug --stacktrace --console=plain
```

```bash
git add app/src/main/java/io/github/vvb2060/ims/ui/screens/AdvancedToolsScreen.kt app/src/main/java/io/github/vvb2060/ims/ui/MainActivity.kt
git commit -m "界面：迁移即时操作到高级工具"
```

---

### Task 9: 补全文案、文档并执行完整验证

**Files:**
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-zh-rCN/strings.xml`
- Modify: `README.md`
- Modify: `README_CN.md`
- Modify: `AGENTS.md`
- Modify after verification: `docs/plans/2026-09-16-settings-center-captive-portal-plan.md`（只追加真实验证结果）

**Interfaces:**
- Consumes: Tasks 5-8 使用的全部 `R.string.*`。
- Produces: 完整中英文 UI 文案、用户文档兼容限制、可复现验证记录。

- [ ] **Step 1: 增加设置中心与 Captive Portal 中英文资源**

中文固定语义至少包含：

```text
IMS 配置
系统网络
高级工具
通话
网络
显示
高级覆盖
应用更改
恢复默认草稿
设备详情
状态不可用
联网检测
Captive Portal
联网检测地址
系统默认
自定义
HTTP 地址
HTTPS 地址
Android 使用这些地址判断当前网络是否可访问互联网。
该设置属于实验性功能；部分系统版本可能优先使用 NetworkStack 资源配置。
更改后建议重新连接当前网络以触发新的检测流程。
此项不能为空
请输入有效的绝对 URL
此字段必须使用 %1$s 协议
已写入系统设置；重新连接网络后验证是否生效
已恢复系统默认设置；重新连接网络后验证是否生效
```

英文表达同一限制，不使用 “effective” 或 “applied successfully” 暗示实际 probe 已切换。

- [ ] **Step 2: README 与 AGENTS 同步架构/兼容边界**

`README_CN.md` 与 `README.md` 增加“系统网络 / Captive Portal HTTP/HTTPS 地址覆盖”，并紧邻说明：NetworkStack 可能优先使用 resource overlay，TensorIMS 只能确认 SettingsProvider 值，实际是否被 NetworkMonitor 使用需以设备验证为准。

`AGENTS.md` 增加：

```text
- ui/screens：首页、IMS 配置、系统网络、Captive Portal 和高级工具页面。
- SystemNetworkViewModel：设备级网络设置状态，不参与 SIM 配置历史或自动恢复。
- CaptivePortalSettingsModifier：通过 shell permission delegation 读写 Captive Portal SettingsProvider key。
```

业务约定明确 Captive Portal 不属于 `Feature`。

- [ ] **Step 3: 运行 JVM 测试、Debug 编译、lint 与差异检查**

```powershell
.\gradlew.bat :app:testDebugUnitTest --stacktrace --console=plain
.\gradlew.bat :app:assembleDebug --stacktrace --console=plain
.\gradlew.bat lint --stacktrace --console=plain
```

```bash
git diff --check
```

Expected: tests/build PASS；无本次新增 lint error；`git diff --check` 无 whitespace error。若 lint 有仓库既有 warning，记录 baseline 与新增 warning 的区别。

- [ ] **Step 4: 安装 Debug APK 验证导航和现有 IMS 回归**

```powershell
adb devices -l
adb install -r app\build\outputs\apk\debug\app-debug.apk
adb shell am force-stop io.github.vvb2060.ims
adb shell am start -n io.github.vvb2060.ims/.ui.MainActivity
```

人工检查：

1. 首页只有紧凑状态、SIM、三个分类入口和自动恢复。
2. IMS 配置能加载历史、恢复默认草稿、编辑字符串项并应用；自动恢复历史语义未改变。
3. 高级工具能读取 IMS 状态；单卡下持久化 VoLTE/重启/重置可用；所有 SIM 下这些单卡即时操作禁用；日志仍可打开。
4. 深色模式、动态颜色、旋转/进程重建后的 selected SIM 和系统返回手势正常。

- [ ] **Step 5: 验证 Captive Portal SettingsProvider 写入与删除**

先在 UI 写入测试 HTTP/HTTPS 地址，再执行：

```powershell
adb shell settings get global captive_portal_http_url
adb shell settings get global captive_portal_https_url
```

Expected: 与 UI 保存值一致。

选择“系统默认”保存，再执行同样命令。Expected: 输出 `null` 或设备对应的 key 不存在表现；App 内重新读取显示“系统默认”。

- [ ] **Step 6: 验证 NetworkStack 实际行为边界**

写入自定义值后手动断开并重新连接测试 Wi-Fi，采集：

```powershell
adb logcat -c
adb logcat -d -v time NetworkMonitor:D ConnectivityService:D AndroidRuntime:E "*:S"
```

如果日志没有暴露 probe URL，验证记录写“SettingsProvider 已验证，NetworkStack 实际 probe URL 无法由当前日志确认”，不能写成生效。

如果日志能看到 probe URL，记录设备型号、Android/API 与实际使用地址，并判断该版本 resource overlay 是否覆盖 SettingsProvider。

- [ ] **Step 7: 验证异常路径**

逐项覆盖：Shizuku 未运行、Shizuku 未授权、HTTP/HTTPS 协议错误、空字段、Instrumentation 空结果/失败。任何失败均不得显示 SAVED/RESET 成功 notice。

- [ ] **Step 8: 按 Superpowers 请求独立代码审查**

审查清单：

```text
Captive Portal 是否误进入 SIM 配置或自动恢复
SettingsProvider 写入/删除是否存在误报成功
Navigation 是否丢失 selected SIM 或破坏返回栈
高风险操作是否保留原有业务顺序与确认
MainActivity 是否只承担承载/状态连接而没有新增业务写入
中英文文案是否错误宣称“已生效”
```

Critical/Important 问题全部修复后，重新运行受影响的 Steps 3-7。

- [ ] **Step 9: 更新文档并 Commit**

```bash
git add app/src/main/res/values/strings.xml app/src/main/res/values-zh-rCN/strings.xml README.md README_CN.md AGENTS.md
git commit -m "文档：补充设置中心与联网检测说明"
```

验证结束后在本文件末尾追加“验证结果”，写具体日期、设备型号、Android/API、测试/编译结果、SettingsProvider 回读与 NetworkStack 实际观察，然后：

```bash
git add docs/plans/2026-09-16-settings-center-captive-portal-plan.md
git commit -m "验证：记录设置中心与联网检测测试结果"
```

---

## Self-Review

### 1. Spec coverage

- 首页轻量化：Task 5。
- IMS Feature 分组、字符串编辑 Dialog、底部主操作：Task 6。
- 系统网络一级入口与 Captive Portal 页面：Task 7。
- Captive Portal 与 SIM 历史/自动恢复隔离：Global Constraints + Tasks 3-4。
- 高级工具迁移与危险操作确认：Task 8。
- 持久化 VoLTE 语义不变：Global Constraints + Task 8。
- NetworkStack resource 优先级限制和非误导文案：Global Constraints + Tasks 7、9。
- 中英文文档、AGENTS、测试、编译、lint、真机、代码审查：Task 9。

没有发现设计要求缺少实施任务。

### 2. Placeholder scan

已逐段检查：没有 `TBD`、`TODO`、`implement later`、空的 route body、未定义的 `loadAfterSave` 或只写“添加适当处理”的步骤。所有跨任务接口均在生产方任务中定义，并在消费方使用相同名称与参数。

### 3. Type consistency

- `CaptivePortalSettings` 在 Tasks 2-4 中统一使用 `httpUrl` / `httpsUrl`。
- URL 错误枚举统一为 `REQUIRED` / `INVALID_URL` / `WRONG_SCHEME`。
- Provider API 只由 Task 3 定义，Task 4 消费同名接口。
- UI state 只由 `SystemNetworkViewModel` 持有；screen 只接收 state + callback，不直接执行 SettingsProvider 写入。
- route 只由 `TensorImsRoutes` 定义；selected SIM 只以 Activity 顶层 `selectedSubId` 保存并从实时 SIM 列表重新解析。
- `MainViewModel` 不新增 Captive Portal 状态，设备级网络职责固定在 `SystemNetworkViewModel`。

---

## Execution Handoff

本 PR 当前阶段只提交设计和计划文档，不执行以上 Tasks 1-9。

用户审核并确认后，在同一 PR 分支 `feat/settings-center-captive-portal` 上执行。优先使用 `superpowers:subagent-driven-development`；当前运行环境没有 subagent 时使用 `superpowers:executing-plans` 分批执行，并在每个任务后设置 review checkpoint。实现开始前先按 `superpowers:using-git-worktrees` 检查/建立隔离工作区和干净 baseline。
