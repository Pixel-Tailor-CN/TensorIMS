package app.mystery0.ims.tensor.embedded.adb

import java.io.Closeable
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import javax.net.ssl.SSLSocket

/** 只允许当前设备的 IPv4 回环地址，不解析主机名，不接受远端地址。 */
internal class LoopbackConnection(private val port: Int) : Closeable {
    private val raw = Socket()
    @Volatile private var tls: SSLSocket? = null
    @Volatile private var closed = false
    init { require(LaunchInput.validPort(port)) }
    fun connect(): Socket {
        check(!closed)
        raw.tcpNoDelay = true
        raw.soTimeout = 8000
        raw.connect(InetSocketAddress(InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1)), port), 8000)
        check(!closed)
        return raw
    }
    fun upgrade(identity: AdbIdentity): SSLSocket {
        check(!closed)
        val socket = identity.tlsContext.socketFactory.createSocket(raw, "127.0.0.1", port, true) as SSLSocket
        tls = socket
        // 取消可能发生在包装 Socket 的同时；再次检查确保新建 Socket 不遗留。
        if (closed) { socket.close(); throw java.io.IOException("Connection closed") }
        socket.soTimeout = 8000
        socket.enabledProtocols = arrayOf("TLSv1.3")
        socket.startHandshake()
        return socket
    }
    override fun close() {
        closed = true
        // Conscrypt.close 会尝试发送 close_notify；先中断原始 TCP，避免取消卡在 TLS 写锁或网络写入。
        runCatching { raw.close() }
        runCatching { tls?.close() }
    }
}
