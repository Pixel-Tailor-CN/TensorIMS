package io.github.vvb2060.ims.privileged

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.PersistableBundle
import android.telephony.CarrierConfigManager
import android.telephony.SubscriptionManager
import io.github.vvb2060.ims.model.TargetConfigMapper
import io.github.vvb2060.ims.model.TargetConfigProtocol
import io.github.vvb2060.ims.model.TargetConfigSnapshot

/** 必须在统一 shell 权限委托内调用；只读合并后的实际配置，不使用通用默认配置补缺项。 */
internal class TargetConfigurationReader(private val context: Context, val subId: Int) {
    val manager: CarrierConfigManager = context.getSystemService(CarrierConfigManager::class.java)

    @SuppressLint("MissingPermission")
    fun identity(): String {
        require(subId >= 0) { "An active SIM is required" }
        val info = context.getSystemService(SubscriptionManager::class.java)
            .activeSubscriptionInfoList.orEmpty().firstOrNull { it.subscriptionId == subId }
            ?: error("SIM is no longer active")
        // subId 会被复用，ICCID 仅用于摘要校验，不保存或打印原始卡号。
        check(info.iccId.isNotBlank()) { "SIM identity is unavailable" }
        return PersistentVolteBackup.identityDigest(info.iccId)
    }

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    fun raw(): Map<String, Any> {
        // 无 keys 的公开重载兼容 Android 13，且保留私有键；空/未加载结果不能当作默认配置。
        val values = manager.getConfigForSubId(subId) ?: error("Carrier configuration is unavailable")
        check(values.getBoolean(CarrierConfigManager.KEY_CARRIER_CONFIG_APPLIED_BOOL, false)) {
            "Carrier configuration is not loaded"
        }
        return values.keySet().mapNotNull { key -> values[key]?.let { key to it } }.toMap()
    }

    fun snapshot(expected: Map<String, Any>? = null): TargetConfigSnapshot {
        val identity = identity()
        val raw = raw()
        check(expected == null || TargetConfigMapper.matches(raw, expected)) { "Configuration changed during verification" }
        val values = TargetConfigMapper.read(raw, Build.VERSION.SDK_INT)
        check(identity() == identity) { "SIM changed while reading configuration" }
        check(values.isNotEmpty()) { "Carrier configuration contains no supported keys" }
        val defaults = defaults()
        return TargetConfigSnapshot(subId, identity, values,
            recovery = values.keys.associateWith { TargetConfigMapper.recovery(it, defaults) })
    }

    @Suppress("DEPRECATION")
    fun defaults(): Map<String, Any> = try {
        // 隐藏 API 在 Android 13+ 以反射读取；它是框架默认参数，不是该卡运营商默认。
        // 反射失败只取消参数项的单项恢复，不影响普通布尔开关和当前配置读取。
        val values = CarrierConfigManager::class.java.getMethod("getDefaultConfig")
            .invoke(null) as PersistableBundle
        values.keySet().mapNotNull { key -> values[key]?.let { key to it } }.toMap()
    } catch (e: Exception) {
        android.util.Log.w("TargetConfiguration", "Default parameters unavailable", e)
        emptyMap()
    }

    companion object {
        fun encode(snapshot: TargetConfigSnapshot): Bundle = Bundle().apply {
            putBoolean(TargetConfigProtocol.COMPLETE, true)
            putInt(TargetConfigProtocol.SUB_ID, snapshot.subId)
            putString(TargetConfigProtocol.IDENTITY, snapshot.identity)
            putBundle(TargetConfigProtocol.VALUES, TargetConfigProtocol.encode(snapshot.values))
            putString(TargetConfigProtocol.ERROR, snapshot.error)
            putBundle(TargetConfigProtocol.RECOVERY, Bundle().apply {
                snapshot.recovery.forEach { (feature, policy) -> putString(feature.name, policy.name) }
            })
        }
    }
}
