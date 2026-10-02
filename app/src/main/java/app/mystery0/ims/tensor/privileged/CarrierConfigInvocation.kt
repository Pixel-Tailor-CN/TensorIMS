package app.mystery0.ims.tensor.privileged

import android.os.PersistableBundle
import android.telephony.CarrierConfigManager
import app.mystery0.ims.tensor.bridge.CarrierWritePermissionDenied

/** 只在实际 overrideConfig 调用明确抛出权限拒绝时标记；回读异常不能冒充写前拒绝。 */
internal fun invokeCarrierOverride(manager: CarrierConfigManager, subId: Int, values: PersistableBundle?) {
    val method = try {
        manager.javaClass.getMethod("overrideConfig", Int::class.javaPrimitiveType,
            PersistableBundle::class.java, Boolean::class.javaPrimitiveType)
    } catch (_: NoSuchMethodException) {
        manager.javaClass.getMethod("overrideConfig", Int::class.javaPrimitiveType, PersistableBundle::class.java)
    }
    try {
        if (method.parameterTypes.size == 3) method.invoke(manager, subId, values, false)
        else method.invoke(manager, subId, values)
    } catch (failure: java.lang.reflect.InvocationTargetException) {
        if (failure.targetException is SecurityException) throw CarrierWritePermissionDenied(failure.targetException)
        throw failure
    }
}

internal fun carrierValues(values: Map<String, Any>): PersistableBundle = PersistableBundle().apply {
    values.forEach { (key, value) ->
        when (value) {
            is Boolean -> putBoolean(key, value)
            is String -> putString(key, value)
            is Int -> putInt(key, value)
            is IntArray -> putIntArray(key, value)
            else -> error("Unsupported carrier configuration type")
        }
    }
}
