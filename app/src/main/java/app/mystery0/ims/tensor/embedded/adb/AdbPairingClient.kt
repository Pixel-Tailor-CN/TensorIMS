/* 基于 Shizuku 配对协议改写；修改：回环限定、边界校验、取消清理与零敏感日志。 */
package app.mystery0.ims.tensor.embedded.adb

import moe.shizuku.manager.adb.PairingContext
import java.io.IOException

internal class AdbPairingClient(private val connection: LoopbackConnection, private val identity: AdbIdentity) {
    fun pair(code: String, onStage: (PairingStage) -> Unit = {}) {
        require(LaunchInput.validPairCode(code))
        val publicKey = identity.adbPublicKey
        onStage(PairingStage.CONNECT)
        connection.connect()
        onStage(PairingStage.TLS_HANDSHAKE)
        val tls = connection.upgrade(identity)
        val codeBytes = code.toByteArray(Charsets.US_ASCII)
        var exported: ByteArray? = null
        var password: ByteArray? = null
        try {
            onStage(PairingStage.TLS_EXPORTER)
            exported = AdbTlsExporter.export(tls)
            password = codeBytes + exported
            onStage(PairingStage.NATIVE_CONTEXT)
            NativePairingCipher(PairingContext(password)).use { pairing ->
                AdbPairingProtocol.exchange(tls.inputStream, tls.outputStream, publicKey, pairing, onStage)
            }
        } finally {
            // String 由公共 UI 契约传入，不能保证 JVM 清除；不保存、不记录，派生可变缓冲区主动覆盖。
            codeBytes.fill(0); exported?.fill(0); password?.fill(0)
        }
    }
}

private class NativePairingCipher(private val context: PairingContext) : PairingCipher {
    override fun message() = context.message()
    override fun initCipher(peer: ByteArray) = context.initCipher(peer)
    override fun encrypt(data: ByteArray) = context.encrypt(data)
    override fun decrypt(data: ByteArray) = context.decrypt(data)
    override fun close() = context.close()
}

internal class InvalidPairingCodeException : IOException("Pairing authentication failed")
