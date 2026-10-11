package app.mystery0.ims.tensor.embedded

import android.app.IActivityManager
import android.content.AttributionSource
import android.content.Context
import android.os.Bundle
import android.os.IBinder
import android.os.Process
import android.os.ServiceManager
import android.os.UserHandle
import app.mystery0.ims.tensor.bridge.BridgeProtocol

/** 集中封装 Android 13+ app_process 所需的隐藏 API 反射入口。 */
internal object BootstrapPlatform {
    fun systemContextForUser(userId: Int): Context {
        require(userId >= 0)
        var stage = "activity-thread"
        try {
            val threadClass = Class.forName("android.app.ActivityThread")
            val thread = threadClass.getMethod("systemMain").invoke(null)
            stage = "system-context"
            val context = threadClass.getMethod("getSystemContext").invoke(thread) as Context
            stage = "user-handle"
            val user = UserHandle::class.java.getDeclaredMethod("of", Int::class.javaPrimitiveType)
                .invoke(null, userId)
            stage = "user-context"
            return Context::class.java.getMethod(
                "createContextAsUser",
                UserHandle::class.java,
                Int::class.javaPrimitiveType,
            ).invoke(context, user, 0) as Context
        } catch (failure: Throwable) {
            throw BootstrapPlatformFailure(stage, failure)
        }
    }

    fun callProvider(method: String, extras: Bundle, userId: Int): Bundle {
        val manager = IActivityManager.Stub.asInterface(
            requireNotNull(ServiceManager.getService(Context.ACTIVITY_SERVICE)),
        )
        var acquired = false
        try {
            val holder = manager.javaClass.getMethod(
                "getContentProviderExternal",
                String::class.java,
                Int::class.javaPrimitiveType,
                IBinder::class.java,
                String::class.java,
            ).invoke(manager, BridgeProtocol.AUTHORITY, userId, null, "TensorIMS bootstrap")
                ?: error("Provider unavailable")
            acquired = true
            val provider = requireNotNull(holder.javaClass.getField("provider").get(holder))
            val source = AttributionSource.Builder(Process.myUid()).build()
            return requireNotNull(
                provider.javaClass.getMethod(
                    "call",
                    AttributionSource::class.java,
                    String::class.java,
                    String::class.java,
                    String::class.java,
                    Bundle::class.java,
                ).invoke(provider, source, BridgeProtocol.AUTHORITY, method, null, extras) as? Bundle,
            ) { "Provider returned no result" }
        } finally {
            if (acquired) runCatching {
                manager.javaClass.getMethod(
                    "removeContentProviderExternalAsUser",
                    String::class.java,
                    IBinder::class.java,
                    Int::class.javaPrimitiveType,
                ).invoke(manager, BridgeProtocol.AUTHORITY, null, userId)
            }
        }
    }
}

internal class BootstrapPlatformFailure(val stage: String, cause: Throwable) : RuntimeException(cause)
