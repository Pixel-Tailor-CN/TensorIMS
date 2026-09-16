# TensorIMS 设置中心与 Captive Portal 配置实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 TensorIMS 从单页功能堆叠结构改为可扩展的设置中心，并新增设备级 Captive Portal HTTP/HTTPS 地址修改与恢复系统默认功能。

**Architecture:** 保留单 `MainActivity`，新增 Navigation Compose 承载首页、IMS 配置、系统网络、Captive Portal 和高级工具页面；`MainViewModel` 继续管理 IMS/SIM/Shizuku，新增 `SystemNetworkViewModel` 管理设备级网络配置。Captive Portal 写入通过新的 Instrumentation 走现有 `ShizukuProvider -> shell permission delegation` 特权链路，与 SIM `Feature`、配置历史和自动恢复彻底分离。

**Tech Stack:** Kotlin 2.4.20、Jetpack Compose、Material 3、AndroidX Navigation Compose 2.10.1、StateFlow、Shizuku、Instrumentation、SettingsProvider、JUnit 4。

**Spec:** `docs/plans/2026-09-16-settings-center-captive-portal-design.md`

## Global Constraints

- Android 13 及以上；`compileSdk/targetSdk = 37`、`minSdk = 33`、JVM target 21、仅 `arm64-v8a`。
- 沟通、文档、新增代码注释使用中文；日志 message 使用英文；Git 提交信息使用中文。
- 不引入 Hilt、Dagger、Room；新增依赖仅限本计划明确需要的 `androidx.navigation:navigation-compose:2.10.1`。
- Captive Portal 是设备级设置，不进入 `Feature`、`FeatureConfigMapper`、`sim_config_<subId>` 历史、自动恢复或持久化 VoLTE 流程。
- 首版只读写 `captive_portal_http_url` 和 `captive_portal_https_url`，不修改 portal mode、fallback URL、other URL 或 DeviceConfig。
- “系统默认”通过删除 SettingsProvider 覆盖实现，不硬编码 Google 或第三方默认 URL。
- SettingsProvider 回读一致只表示“已写入系统设置”，UI/README 不宣称 NetworkStack 一定已采用该地址。
- 所有特权读写继续复用 `ShizukuProvider` 的 Instrumentation 串行互斥和 `runWithShellPermissionDelegation` 错误边界。
- 纯 Kotlin URL 规范化/校验按 TDD 执行；Compose 页面与真实 SettingsProvider 特权写入遵循项目约定，以 Debug 编译、lint、差异检查和真机验证为准。
- 每个实施任务完成后按 `superpowers:requesting-code-review` 做独立审查；Critical/Important 问题修复后再进入下一任务。

---

## File Structure

### 新增文件

- `app/src/main/java/io/github/vvb2060/ims/model/CaptivePortalSettings.kt`：设备级 Captive Portal 值、输入校验结果与 URL 规范化。
- `app/src/test/java/io/github/vvb2060/ims/model/CaptivePortalSettingsTest.kt`：纯 Kotlin URL 校验单元测试。
- `app/src/main/java/io/github/vvb2060/ims/privileged/CaptivePortalSettingsModifier.kt`：SettingsProvider 读取、写入、删除和读回验证。
- `app/src/main/java/io/github/vvb2060/ims/viewmodel/SystemNetworkViewModel.kt`：Captive Portal 页面状态和保存调度。
- `app/src/main/java/io/github/vvb2060/ims/ui/navigation/TensorImsRoutes.kt`：固定导航 route。
- `app/src/main/java/io/github/vvb2060/ims/ui/screens/HomeScreen.kt`：轻量首页。
- `app/src/main/java/io/github/vvb2060/ims/ui/screens/ImsConfigScreen.kt`：按组展示 `Feature` 草稿与底部应用操作。
- `app/src/main/java/io/github/vvb2060/ims/ui/screens/SystemNetworkScreen.kt`：设备级网络设置目录。
- `app/src/main/java/io/github/vvb2060/ims/ui/screens/CaptivePortalScreen.kt`：系统默认/自定义编辑页。
- `app/src/main/java/io/github/vvb2060/ims/ui/screens/AdvancedToolsScreen.kt`：IMS 状态、持久化 VoLTE、重启、重置和日志。
- `app/src/main/java/io/github/vvb2060/ims/ui/components/DeviceStatusCard.kt`：紧凑设备/Shizuku 状态与详情 Dialog。
- `app/src/main/java/io/github/vvb2060/ims/ui/components/SettingsListItem.kt`：首页和分类页统一列表项样式。

### 修改文件

- `gradle/libs.versions.toml`：增加 Navigation 2.10.1 版本和 `navigation-compose` catalog 项。
- `app/build.gradle.kts`：增加 `implementation(libs.androidx.navigation.compose)`。
- `app/src/main/java/io/github/vvb2060/ims/ui/MainActivity.kt`：缩减为状态收集、NavHost、页面间回调和 Activity 生命周期对接。
- `app/src/main/java/io/github/vvb2060/ims/ShizukuProvider.kt`：增加 Captive Portal Instrumentation 调用接口。
- `app/src/main/AndroidManifest.xml`：注册 `CaptivePortalSettingsModifier` Instrumentation。
- `app/src/main/res/values/strings.xml`、`values-zh-rCN/strings.xml`：新增导航、分组、Captive Portal、确认和错误文案。
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
- Produces: `libs.androidx.navigation.compose`，供 Task 5 的 `NavHost`/`rememberNavController` 使用。

> 本任务属于依赖配置，按 TDD skill 的 configuration 例外处理；不新增无意义单元测试，以依赖解析和 Debug 编译作为验证。

- [ ] **Step 1: 在 Version Catalog 增加固定版本和 library alias**

```toml
[versions]
navigation = "2.10.1"

[libraries]
androidx-navigation-compose = { module = "androidx.navigation:navigation-compose", version.ref = "navigation" }
```

保持现有版本项顺序，在 AndroidX 依赖区域加入 `navigation`，不要更改 Compose BOM 或 Material 3 版本。

- [ ] **Step 2: 在 app 模块增加依赖**

```kotlin
implementation(libs.androidx.navigation.compose)
```

放在现有 `androidx.activity.compose` / Compose 依赖附近，不增加 Kotlin Serialization plugin。

- [ ] **Step 3: 验证依赖可以编译**

Run:

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

- [ ] **Step 1: 先写失败测试**

创建测试文件：

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
    fun urlWithoutHostIsInvalid() {
        val result = validateCaptivePortalUrls("http:///generate_204", "https:///generate_204")
        assertFalse(result.isValid)
        assertEquals(CaptivePortalUrlError.INVALID_URL, result.httpError)
        assertEquals(CaptivePortalUrlError.INVALID_URL, result.httpsError)
    }
}
```

- [ ] **Step 2: 运行测试并确认 RED**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "io.github.vvb2060.ims.model.CaptivePortalSettingsTest" --stacktrace --console=plain
```

Expected: FAIL，因为 `CaptivePortalSettings` / `validateCaptivePortalUrls` 尚不存在。

- [ ] **Step 3: 写最小实现**

创建：

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
    return CaptivePortalValidationResult(
        settings = settings,
        httpError = http.second,
        httpsError = https.second,
    )
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
    if (uri.host.isNullOrBlank()) {
        return null to CaptivePortalUrlError.INVALID_URL
    }
    return value to null
}
```

- [ ] **Step 4: 运行测试并确认 GREEN**

Run the same command as Step 2.

Expected: all `CaptivePortalSettingsTest` tests PASS。

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
- Consumes: `CaptivePortalSettings`、`runWithShellPermissionDelegation`、`ShizukuProvider.startInstrumentation`。
- Produces:
  - `CaptivePortalSettingsModifier.ACTION_READ`
  - `CaptivePortalSettingsModifier.ACTION_WRITE`
  - `CaptivePortalSettingsModifier.ACTION_RESET`
  - `ShizukuProvider.readCaptivePortalSettings(context): Pair<CaptivePortalSettings?, String?>`
  - `ShizukuProvider.writeCaptivePortalSettings(context, settings): String?`
  - `ShizukuProvider.resetCaptivePortalSettings(context): String?`

> SettingsProvider 写入依赖 shell permission delegation，无法由当前 JVM 单测真实覆盖；本任务按仓库约定以编译 + 真机读回验证为验收，不伪造 mock 成功测试。

- [ ] **Step 1: 创建 Instrumentation 协议和 SettingsProvider 操作**

实现以下常量和行为：

```kotlin
package io.github.vvb2060.ims.privileged

import android.app.Activity
import android.app.Instrumentation
import android.os.Bundle
import android.provider.Settings
import io.github.vvb2060.ims.model.CaptivePortalSettings

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

        // NetworkStack 使用的 SettingsProvider key。使用字符串避免依赖隐藏字段可见性。
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
        check(Settings.Global.putString(resolver, HTTP_KEY, null)) { "Failed to clear HTTP URL" }
        check(Settings.Global.putString(resolver, HTTPS_KEY, null)) { "Failed to clear HTTPS URL" }
        check(Settings.Global.getString(resolver, HTTP_KEY) == null) { "HTTP URL was not cleared" }
        check(Settings.Global.getString(resolver, HTTPS_KEY) == null) { "HTTPS URL was not cleared" }
        result.putBoolean(RESULT_SUCCESS, true)
    }
}
```

如果设备实际验证发现 `Settings.Global.putString(..., null)` 在目标 Android 版本返回 false，但 key 已成功删除，则不要取消读回验证；改为以读回为最终判定，并在计划“验证结果”记录该系统行为。

- [ ] **Step 2: 注册 Instrumentation**

在 manifest 的现有 instrumentation 列表中加入：

```xml
<instrumentation
    android:name=".privileged.CaptivePortalSettingsModifier"
    android:label="CaptivePortalSettingsModifier"
    android:targetPackage="${applicationId}" />
```

不新增 manifest `WRITE_SECURE_SETTINGS` 普通权限声明；实际身份来自现有 shell permission delegation。

- [ ] **Step 3: 给 ShizukuProvider 增加读写接口**

增加 import 后，实现：

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
    val args = Bundle().apply {
        putString(CaptivePortalSettingsModifier.ACTION, CaptivePortalSettingsModifier.ACTION_WRITE)
        putString(CaptivePortalSettingsModifier.HTTP_URL, settings.httpUrl)
        putString(CaptivePortalSettingsModifier.HTTPS_URL, settings.httpsUrl)
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

写接口只接收 Task 2 已验证过的非空 URL；ViewModel 不允许绕过校验调用自定义写入。

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
  - `SystemNetworkViewModel.loadCaptivePortal()`
  - `setMode(mode)` / `setHttpUrl(value)` / `setHttpsUrl(value)` / `saveCaptivePortal()` / `clearNotice()`

- [ ] **Step 1: 创建稳定 UI state**

使用以下字段，避免把 Compose 类型放进 ViewModel：

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

- [ ] **Step 2: 实现读取逻辑**

`loadCaptivePortal()` 必须：

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

- [ ] **Step 3: 实现编辑与保存逻辑**

`setHttpUrl`/`setHttpsUrl` 更新输入时清除对应字段错误和旧 notice；`setMode` 切换到系统默认时不擦除输入框缓存，用户切回自定义时仍保留本次编辑。

`saveCaptivePortal()` 使用：

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
```

`loadAfterSave(notice)` 必须重新调用 provider 读取实际 provider 值，并把 notice 保留下来；不要只用本地输入假装保存成功。

- [ ] **Step 4: Debug 编译**

```powershell
.\gradlew.bat :app:assembleDebug --stacktrace --console=plain
```

Expected: PASS。

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/io/github/vvb2060/ims/viewmodel/SystemNetworkViewModel.kt
git commit -m "功能：增加系统网络配置状态管理"
```

---

### Task 5: 重构单页 UI 为设置中心导航结构

**Files:**
- Create: `app/src/main/java/io/github/vvb2060/ims/ui/navigation/TensorImsRoutes.kt`
- Create: `app/src/main/java/io/github/vvb2060/ims/ui/screens/HomeScreen.kt`
- Create: `app/src/main/java/io/github/vvb2060/ims/ui/screens/ImsConfigScreen.kt`
- Create: `app/src/main/java/io/github/vvb2060/ims/ui/screens/SystemNetworkScreen.kt`
- Create: `app/src/main/java/io/github/vvb2060/ims/ui/screens/CaptivePortalScreen.kt`
- Create: `app/src/main/java/io/github/vvb2060/ims/ui/screens/AdvancedToolsScreen.kt`
- Create: `app/src/main/java/io/github/vvb2060/ims/ui/components/DeviceStatusCard.kt`
- Create: `app/src/main/java/io/github/vvb2060/ims/ui/components/SettingsListItem.kt`
- Modify: `app/src/main/java/io/github/vvb2060/ims/ui/MainActivity.kt`

**Interfaces:**
- Consumes: `MainViewModel`、`SystemNetworkViewModel`、Task 4 UI state、现有 `AutoRestoreCard` / `PersistentVolteCard` / `SystemConfigDialog`。
- Produces: 5 个固定 route，首页只导航，IMS 草稿/即时高级操作/设备级网络设置各归其位。

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

- [ ] **Step 2: 把 MainActivity 缩减为 Activity 级状态与 NavHost**

`MainActivity` 新增 `SystemNetworkViewModel`，保存 `selectedSubId` 而不是完整对象：

```kotlin
private val viewModel: MainViewModel by viewModels()
private val systemNetworkViewModel: SystemNetworkViewModel by viewModels()
```

Compose 顶层核心结构：

```kotlin
val allSimList by viewModel.allSimList.collectAsStateWithLifecycle()
var selectedSubId by rememberSaveable { mutableStateOf<Int?>(null) }
val selectedSim = allSimList.firstOrNull { it.subId == selectedSubId }

LaunchedEffect(allSimList, selectedSubId) {
    if (selectedSubId != null && selectedSim == null) selectedSubId = null
}

val navController = rememberNavController()
NavHost(navController = navController, startDestination = TensorImsRoutes.HOME) {
    composable(TensorImsRoutes.HOME) { /* HomeScreen，传具体回调 */ }
    composable(TensorImsRoutes.IMS_CONFIG) { /* ImsConfigScreen */ }
    composable(TensorImsRoutes.SYSTEM_NETWORK) { /* SystemNetworkScreen */ }
    composable(TensorImsRoutes.CAPTIVE_PORTAL) { /* CaptivePortalScreen */ }
    composable(TensorImsRoutes.ADVANCED_TOOLS) { /* AdvancedToolsScreen */ }
}
```

实际代码不得保留注释占位；五个 destination 在同一次提交中全部接到下述完整 screen composable。

- [ ] **Step 3: 实现 HomeScreen**

公开签名固定为：

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
)
```

页面顺序严格按设计文档：`DeviceStatusCard -> SIM 选择 -> 3 个 SettingsListItem -> AutoRestoreCard -> navigation bar inset`。

IMS 配置 row 在 `selectedSim == null` 时禁用；系统网络永远可点击；高级工具永远可进入。原 `SimCardSelectionCard` 的 IMS 状态/重启按钮从首页删除。

- [ ] **Step 4: 实现 ImsConfigScreen 并迁移 Feature 草稿**

页面签名：

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

Feature 分组使用固定列表，避免以后靠 enum 顺序隐式分组：

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

private val networkFeatures = listOf(
    Feature.FIVE_G_NR,
    Feature.FIVE_G_THRESHOLDS,
)

private val displayFeatures = listOf(
    Feature.FIVE_G_PLUS_ICON,
    Feature.ENHANCED_4G_LTE,
    Feature.HIDE_LTE_PLUS_DATA_ICON,
    Feature.SHOW_4G_FOR_LTE,
)

private val advancedFeatures = listOf(
    Feature.CARRIER_NAME,
    Feature.IMS_USER_AGENT,
)
```

`FeatureValueType.BOOLEAN` 使用整行 `toggleable + Switch(onCheckedChange = null)`；`STRING` 使用列表项 + 编辑 `AlertDialog`，Dialog 内保留 `OutlinedTextField`。

`Scaffold.bottomBar` 只显示主操作“应用更改”；历史加载和“恢复默认草稿”放 TopAppBar menu。原直接 `onResetConfiguration` 不出现在本页。

现有 `Tips()` 内容移动到 IMS 页列表底部。

- [ ] **Step 5: 实现 AdvancedToolsScreen**

签名：

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

页面复用 `SystemConfigDialog` 和 `PersistentVolteCard`。IMS 实时状态、持久化 VoLTE、重启 IMS、重置运营商配置在“所有 SIM”或未选择单卡时禁用；日志始终可打开。

重启 IMS 和重置运营商配置增加确认 Dialog。重置确认文案包含 `selectedSim.showTitle`，确认后仍调用现有 `MainViewModel.onResetConfiguration`，不复制业务逻辑。

- [ ] **Step 6: 实现 SystemNetworkScreen**

进入页面时调用 `systemNetworkViewModel.loadCaptivePortal()`。列表只有一个 Captive Portal row，summary 规则：

```kotlin
val summary = when {
    state.loading -> stringResource(R.string.loading)
    state.operationError != null -> stringResource(R.string.status_unavailable)
    state.storedSettings.hasOverride -> stringResource(R.string.captive_portal_custom)
    else -> stringResource(R.string.captive_portal_system_default)
}
```

row 点击进入 `TensorImsRoutes.CAPTIVE_PORTAL`。

- [ ] **Step 7: 实现 CaptivePortalScreen**

页面收集 `CaptivePortalUiState`，提供系统默认/自定义两行 RadioButton。只有 CUSTOM 模式显示两个 `OutlinedTextField`。

错误映射固定为：

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

底部保存按钮调用 `saveCaptivePortal()`。`notice == SAVED` 显示“已写入系统设置；重新连接网络后验证是否生效”；`RESET` 显示“已恢复系统默认设置；重新连接网络后验证是否生效”。展示后调用 `clearNotice()`，避免旋转/返回重复提示。

页面顶部始终显示实验性限制说明，不以 Shizuku 成功状态替代限制说明。

- [ ] **Step 8: 把设备状态收敛为紧凑卡片**

`DeviceStatusCard` 正常时只显示：设备型号、Android 版本、Shizuku 文本状态和“设备详情”。详情 Dialog 展示 app version、build、安全补丁、GitHub 入口。

`NOT_RUNNING`、`NO_PERMISSION`、`NEED_UPDATE` 显示对应操作；`READY` 不显示多余“刷新权限/申请权限”双按钮。

- [ ] **Step 9: 连接 Activity onResume 与现有状态刷新**

保留：

```kotlin
override fun onResume() {
    super.onResume()
    viewModel.updateShizukuStatus()
    viewModel.refreshPersistentVolte()
}
```

并确保 selected single SIM 变化后继续调用 `viewModel.selectPersistentVolteSim(subId)`；所有 SIM 或 null 时传 null。

- [ ] **Step 10: Debug 编译 + 基础 UI 差异检查**

```powershell
.\gradlew.bat :app:assembleDebug --stacktrace --console=plain
git diff --check
```

Expected: PASS；`MainActivity.kt` 明显缩短，现有业务操作没有被复制到 UI 层。

- [ ] **Step 11: Commit**

```bash
git add app/src/main/java/io/github/vvb2060/ims/ui app/src/main/java/io/github/vvb2060/ims/ui/components app/src/main/java/io/github/vvb2060/ims/ui/navigation app/src/main/java/io/github/vvb2060/ims/ui/screens
git commit -m "界面：重构为分层设置中心"
```

---

### Task 6: 补全中英文资源、README 与代理文档

**Files:**
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-zh-rCN/strings.xml`
- Modify: `README.md`
- Modify: `README_CN.md`
- Modify: `AGENTS.md`

**Interfaces:**
- Consumes: Task 5 使用的所有 `R.string.*`。
- Produces: 完整中英文 UI 文案、用户文档兼容说明、新项目结构说明。

- [ ] **Step 1: 增加导航和分组文案**

中英文至少覆盖：

```text
IMS 配置 / IMS configuration
系统网络 / System network
高级工具 / Advanced tools
通话 / Calling
网络 / Network
显示 / Display
高级覆盖 / Advanced overrides
应用更改 / Apply changes
恢复默认草稿 / Restore default draft
设备详情 / Device details
状态不可用 / Status unavailable
```

- [ ] **Step 2: 增加 Captive Portal 全部文案**

中文固定语义：

```text
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

英文要表达同一限制，不使用“effective/applied successfully”暗示实际 probe 已切换。

- [ ] **Step 3: README 增加功能和限制**

`README_CN.md` 功能列表加入“系统网络 / Captive Portal HTTP/HTTPS 地址覆盖”；紧邻功能说明增加：NetworkStack 可能优先使用资源 overlay，TensorIMS 只能验证 SettingsProvider 写入值，实际生效需以设备网络验证为准。

`README.md` 同步英文内容。

- [ ] **Step 4: AGENTS.md 更新结构说明**

在 UI 包结构和特权入口中增加：

```text
- ui/screens：首页、IMS 配置、系统网络、Captive Portal 和高级工具页面。
- SystemNetworkViewModel：设备级网络设置状态，不参与 SIM 配置历史或自动恢复。
- CaptivePortalSettingsModifier：通过 shell permission delegation 读写 Captive Portal SettingsProvider key。
```

业务约定明确 Captive Portal 不属于 `Feature`。

- [ ] **Step 5: 编译资源**

```powershell
.\gradlew.bat :app:assembleDebug --stacktrace --console=plain
```

Expected: PASS，没有 missing resource。

- [ ] **Step 6: Commit**

```bash
git add app/src/main/res/values/strings.xml app/src/main/res/values-zh-rCN/strings.xml README.md README_CN.md AGENTS.md
git commit -m "文档：补充设置中心与联网检测说明"
```

---

### Task 7: 完整验证、真机兼容记录与独立审查

**Files:**
- Modify: `docs/plans/2026-09-16-settings-center-captive-portal-plan.md`（只追加“验证结果”，不改需求）
- Modify only if review finds defects: implementation files from Tasks 2-6

**Interfaces:**
- Consumes: 全部实现。
- Produces: 可复现的编译/测试/真机结果和审查结论。

- [ ] **Step 1: 运行 JVM 测试**

```powershell
.\gradlew.bat :app:testDebugUnitTest --stacktrace --console=plain
```

Expected: PASS。

- [ ] **Step 2: 运行 Debug 编译和 lint**

```powershell
.\gradlew.bat :app:assembleDebug --stacktrace --console=plain
.\gradlew.bat lint --stacktrace --console=plain
```

Expected: PASS；如果 lint 有仓库既有 warning，记录 baseline 与本次新增 warning 的区别，不把既有问题误归因于本 PR。

- [ ] **Step 3: 运行差异检查**

```bash
git diff --check
```

Expected: 无 whitespace error。

- [ ] **Step 4: 安装 Debug APK 并验证导航/现有 IMS 回归**

```powershell
adb devices -l
adb install -r app\build\outputs\apk\debug\app-debug.apk
adb shell am force-stop io.github.vvb2060.ims
adb shell am start -n io.github.vvb2060.ims/.ui.MainActivity
```

人工检查：

1. 首页只有紧凑状态、SIM、三个分类入口和自动恢复。
2. IMS 配置可以加载历史、恢复默认草稿、编辑字符串项、应用配置。
3. 自动恢复开关和历史语义未改变。
4. 高级工具能读取 IMS 状态；单卡下持久化 VoLTE/重启/重置可用；所有 SIM 下这些即时操作禁用；日志仍可打开。
5. 深色模式、动态颜色和系统返回手势正常。

- [ ] **Step 5: 验证 Captive Portal SettingsProvider 写入/删除**

进入自定义模式，填写一组测试用 HTTP/HTTPS 地址并保存。随后：

```powershell
adb shell settings get global captive_portal_http_url
adb shell settings get global captive_portal_https_url
```

Expected: 与 UI 保存值一致。

切换“系统默认”并保存，再执行同样命令。Expected: 输出 `null` 或设备对应的“key 不存在”表现；同时 app 内读取应显示“系统默认”。

- [ ] **Step 6: 验证 NetworkStack 实际行为边界**

写入自定义值后，手动断开并重新连接测试 Wi-Fi；清空 logcat 后采集：

```powershell
adb logcat -c
adb logcat -d -v time NetworkMonitor:D ConnectivityService:D AndroidRuntime:E "*:S"
```

如果系统日志没有暴露 probe URL，则记录“SettingsProvider 已验证，NetworkStack 实际 probe URL 无法由当前日志确认”，不能写成生效。

如果能看到 probe URL，记录设备型号、Android/API、NetworkStack 实际使用地址，判断该版本资源 overlay 是否覆盖 SettingsProvider。

- [ ] **Step 7: 验证异常路径**

覆盖：Shizuku 未运行、Shizuku 未授权、URL 协议错误、空字段、写入失败/Instrumentation 空结果。任何失败都不得显示 SAVED/RESET 成功 notice。

- [ ] **Step 8: 请求独立代码审查**

按 `superpowers:requesting-code-review` 对照本设计与实施计划审查：

- Captive Portal 是否误进入 SIM 配置/自动恢复。
- SettingsProvider 写入/删除是否有误报成功。
- Navigation 是否丢失 selected SIM 或破坏返回栈。
- 高风险操作是否仍保留原业务顺序和确认。
- MainActivity 是否只承担承载/状态连接，没有新增业务写入。
- 中英文文案是否错误宣称“已生效”。

Critical/Important 问题全部修复后，重新执行 Steps 1-7 中受影响的验证。

- [ ] **Step 9: 在本计划末尾写入真实验证结果并 Commit**

验证结果必须写具体日期、设备型号、Android/API、编译/测试结果和 Captive Portal 实际观察，不使用“全部正常”一类不可复现描述。

```bash
git add docs/plans/2026-09-16-settings-center-captive-portal-plan.md
git commit -m "验证：记录设置中心与联网检测测试结果"
```

---

## Self-Review

### 1. Spec coverage

- 首页轻量化：Task 5 Steps 2-3、8。
- IMS Feature 分组与底部主操作：Task 5 Step 4。
- 系统网络一级入口：Task 5 Step 6。
- Captive Portal 系统默认/自定义、HTTP/HTTPS 校验：Tasks 2、4、5 Step 7。
- 不加入 SIM 历史/自动恢复：Global Constraints + Task 3/4 的独立接口。
- 高级工具迁移：Task 5 Step 5。
- 持久化 VoLTE 语义不变：Global Constraints + Task 5 Step 5。
- NetworkStack resource 优先级限制和非误导文案：Global Constraints + Task 6 + Task 7 Step 6。
- 中英文文档、AGENTS：Task 6。
- 编译、测试、lint、真机、代码审查：Task 7。

没有发现设计要求缺少实施任务。

### 2. Placeholder scan

计划中没有 `TBD`、`TODO`、`implement later`、无内容的“写测试”步骤或未定义的邻接接口。Task 5 的 NavHost 示例仅展示核心结构，并明确要求同一提交连接五个完整 screen；各 screen 的签名、数据来源和行为均在同一任务中定义。

### 3. Type consistency

- `CaptivePortalSettings` 在 Tasks 2、3、4 中参数名一致：`httpUrl` / `httpsUrl`。
- URL 错误枚举在 Tasks 2、4、5 中一致：`REQUIRED` / `INVALID_URL` / `WRONG_SCHEME`。
- provider API 在 Task 3 定义，Task 4 只消费这三个接口。
- route 常量只由 Task 5 的 `TensorImsRoutes` 定义并消费。
- `MainViewModel` 不新增 Captive Portal 状态，设备级网络职责固定在 `SystemNetworkViewModel`。

---

## Execution Handoff

本 PR 当前阶段只提交设计和计划文档，不执行以上 Tasks 1-7。

用户审核并确认后，推荐在同一 PR 分支 `feat/settings-center-captive-portal` 上使用 `superpowers:subagent-driven-development` 按任务执行；若当前运行环境没有 subagent，则使用 `superpowers:executing-plans` 分批执行，并在每个任务后设置 review checkpoint。实现开始前先按 `superpowers:using-git-worktrees` 检查/建立隔离工作区和干净 baseline。
