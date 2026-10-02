/* 基于 Shizuku 配对协议改写；修改：回环限定、边界校验、取消清理与零敏感日志。 */
package app.mystery0.ims.tensor.embedded.adb

import moe.shizuku.manager.adb.PairingContext
import org.lsposed.hiddenapibypass.LSPass
import java.io.IOException
import javax.net.ssl.SSLSocket

internal class AdbPairingClient(private val connection: LoopbackConnection, private val identity: AdbIdentity) {
    fun pair(code: String) {
        require(LaunchInput.validPairCode(code))
        connection.connect()
        val tls = connection.upgrade(identity)
        val codeBytes = code.toByteArray(Charsets.US_ASCII)
        var exported: ByteArray? = null
        var password: ByteArray? = null
        try {
            // Android 的 TLS exporter 是隐藏 API，必须使用系统 Conscrypt；不可替换为随机值。
            // 与现有 ShizukuProvider 一样保留隐藏 API 兼容豁免，反射不可用时明确失败。
            LSPass.setHiddenApiExemptions("")
            val method = Class.forName("com.android.org.conscrypt.Conscrypt").getDeclaredMethod(
                "exportKeyingMaterial", SSLSocket::class.java, String::class.java, ByteArray::class.java, Int::class.javaPrimitiveType)
            exported = method.invoke(null, tls, "adb-label\u0000", null, 64) as? ByteArray
                ?: throw IOException("TLS exporter unavailable")
            if (exported.size != 64) throw AdbProtocolException()
            password = codeBytes + exported
            PairingContext(password).use { pairing ->
                val output = tls.outputStream
                val input = tls.inputStream
                output.write(PairingFrame(0, pairing.message()).encode()); output.flush()
                val peerMessage = PairingFrame.read(input, 0).payload
                if (peerMessage.size > 32 || !pairing.initCipher(peerMessage)) throw AdbProtocolException()
                val peerInfo = ByteArray(PairingFrame.PEER_INFO_SIZE)
                val publicKey = identity.adbPublicKey
                if (publicKey.size >= peerInfo.size) throw AdbProtocolException()
                // PeerInfo 类型 0 为主机 RSA 公钥；类型 1 为设备 GUID。
                publicKey.copyInto(peerInfo, 1)
                val encrypted = pairing.encrypt(peerInfo) ?: throw AdbProtocolException()
                output.write(PairingFrame(1, encrypted).encode()); output.flush()
                val peerEncrypted = PairingFrame.read(input, 1).payload
                val response = pairing.decrypt(peerEncrypted) ?: throw InvalidPairingCodeException()
                try {
                    if (response.size != PairingFrame.PEER_INFO_SIZE || response[0].toInt() != 1 || response[1] == 0.toByte()) throw AdbProtocolException()
                } finally { response.fill(0) }
            }
        } finally {
            // String 由公共 UI 契约传入，不能保证 JVM 清除；不保存、不记录，派生可变缓冲区主动覆盖。
            codeBytes.fill(0); exported?.fill(0); password?.fill(0)
        }
    }
}
internal class InvalidPairingCodeException : IOException("Pairing authentication failed")
