package app.mystery0.ims.tensor.embedded.adb

import java.io.Closeable
import java.io.InputStream
import java.io.OutputStream

/** 只记录阶段常量，不包含端口、配对码、证书或 TLS 导出材料。 */
internal enum class PairingStage(val diagnosticName: String) {
    IDENTITY("identity"),
    CONNECT("connect"),
    TLS_HANDSHAKE("tls-handshake"),
    TLS_EXPORTER("tls-exporter"),
    NATIVE_CONTEXT("native-context"),
    SPAKE2_EXCHANGE("spake2-exchange"),
    PEER_INFO_EXCHANGE("peer-info-exchange")
}

/** 原生密码实现与有界线协议分离，允许在无 Android/JNI 的环境复核报文及失败路径。 */
internal interface PairingCipher : Closeable {
    fun message(): ByteArray
    fun initCipher(peer: ByteArray): Boolean
    fun encrypt(data: ByteArray): ByteArray?
    fun decrypt(data: ByteArray): ByteArray?
}

internal object AdbPairingProtocol {
    // BoringSSL 的 SPAKE2_MAX_MSG_SIZE 为 32；64 是派生密钥长度，不能混为报文长度。
    const val SPAKE2_MAX_MESSAGE_SIZE = 32

    fun exchange(input: InputStream, output: OutputStream, publicKey: ByteArray,
                 pairing: PairingCipher, onStage: (PairingStage) -> Unit) {
        onStage(PairingStage.SPAKE2_EXCHANGE)
        val message = pairing.message()
        if (message.size !in 1..SPAKE2_MAX_MESSAGE_SIZE) throw AdbProtocolException()
        output.write(PairingFrame(0, message).encode()); output.flush()
        val peerMessage = PairingFrame.read(input, 0).payload
        if (peerMessage.size > SPAKE2_MAX_MESSAGE_SIZE || !pairing.initCipher(peerMessage)) throw AdbProtocolException()

        onStage(PairingStage.PEER_INFO_EXCHANGE)
        if (publicKey.isEmpty() || publicKey.size >= PairingFrame.PEER_INFO_SIZE) throw AdbProtocolException()
        val peerInfo = ByteArray(PairingFrame.PEER_INFO_SIZE)
        // AOSP PeerInfo 类型 0 为主机 RSA 公钥；类型 1 为设备 GUID。
        publicKey.copyInto(peerInfo, 1)
        val encrypted = try { pairing.encrypt(peerInfo) ?: throw AdbProtocolException() }
        finally { peerInfo.fill(0) }
        output.write(PairingFrame(1, encrypted).encode()); output.flush()
        val peerEncrypted = PairingFrame.read(input, 1).payload
        val response = pairing.decrypt(peerEncrypted) ?: throw InvalidPairingCodeException()
        try {
            if (response.size != PairingFrame.PEER_INFO_SIZE || response[0].toInt() != 1 || response[1] == 0.toByte()) throw AdbProtocolException()
        } finally { response.fill(0) }
    }
}
