package app.mystery0.ims.tensor.embedded.adb

import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Modifier

class PairingNativeAbiTest {
    @Test fun javaMethodsMatchTheOfficialShizukuJniRegistrationTable() {
        // 只校验注册契约，不在 JVM 加载仅 arm64 Android 可执行的 libadb.so。
        val type = Class.forName("moe.shizuku.manager.adb.PairingContext", false, javaClass.classLoader)
        val methods = type.declaredMethods.filter { Modifier.isNative(it.modifiers) }.associateBy { it.name }
        assertEquals(setOf("nativeConstructor", "nativeMsg", "nativeInitCipher", "nativeEncrypt", "nativeDecrypt", "nativeDestroy"), methods.keys)
        val signatures = mapOf(
            "nativeConstructor" to Pair(Long::class.javaPrimitiveType, listOf(Boolean::class.javaPrimitiveType, ByteArray::class.java)),
            "nativeMsg" to Pair(ByteArray::class.java, listOf(Long::class.javaPrimitiveType)),
            "nativeInitCipher" to Pair(Boolean::class.javaPrimitiveType, listOf(Long::class.javaPrimitiveType, ByteArray::class.java)),
            "nativeEncrypt" to Pair(ByteArray::class.java, listOf(Long::class.javaPrimitiveType, ByteArray::class.java)),
            "nativeDecrypt" to Pair(ByteArray::class.java, listOf(Long::class.javaPrimitiveType, ByteArray::class.java)),
            "nativeDestroy" to Pair(Void.TYPE, listOf(Long::class.javaPrimitiveType)))
        signatures.forEach { (name, signature) ->
            val method = methods.getValue(name)
            assertEquals(signature.first, method.returnType)
            assertEquals(signature.second, method.parameterTypes.toList())
            assertEquals(name == "nativeConstructor", Modifier.isStatic(method.modifiers))
        }
    }
}
