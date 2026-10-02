package app.mystery0.ims.tensor.embedded.adb

import org.lsposed.hiddenapibypass.LSPass
import java.io.IOException
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import javax.net.ssl.SSLSocket

internal object AdbTlsExporter {
    const val LABEL = "adb-label\u0000"
    const val KEY_SIZE = 64

    fun export(socket: SSLSocket): ByteArray {
        val type = Class.forName("com.android.org.conscrypt.Conscrypt")
        // 先使用已开放的系统入口；需要时只查找此隐藏方法，避免重复修改进程全局豁免列表。
        // Android 后续版本可能不允许多次调用 VMRuntime.setHiddenApiExemptions。
        val parameters = arrayOf(SSLSocket::class.java, String::class.java, ByteArray::class.java, Int::class.javaPrimitiveType!!)
        val method = try { type.getDeclaredMethod("exportKeyingMaterial", *parameters) }
        catch (_: NoSuchMethodException) { LSPass.getDeclaredMethod(type, "exportKeyingMaterial", *parameters) }
        method.isAccessible = true
        return invoke(method, socket)
    }

    internal fun invoke(method: Method, socket: SSLSocket): ByteArray {
        // AOSP 的 label 包含结尾 NUL，context 必须为 null 而非空数组；不允许伪造或降级导出材料。
        val material = try { method.invoke(null, socket, LABEL, null, KEY_SIZE) as? ByteArray }
        catch (failure: InvocationTargetException) { throw failure.targetException }
        if (material == null) throw IOException("TLS exporter unavailable")
        if (material.size != KEY_SIZE) {
            material.fill(0)
            throw AdbProtocolException()
        }
        return material
    }
}
