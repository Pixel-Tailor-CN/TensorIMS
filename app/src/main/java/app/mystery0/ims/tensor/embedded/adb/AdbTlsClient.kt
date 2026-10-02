/* 基于 Shizuku AdbClient 改写；仅支持 TLS 与一个私有固定启动命令，不提供外部命令接口。 */
package app.mystery0.ims.tensor.embedded.adb

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

internal class AdbTlsClient(private val connection: LoopbackConnection, private val identity: AdbIdentity) {
    fun bootstrap(command: String) {
        val raw = connection.connect()
        raw.outputStream.write(AdbFrame(AdbFrame.CNXN, AdbFrame.VERSION, AdbFrame.MAX_PAYLOAD, "host::\u0000".toByteArray()).encode())
        raw.outputStream.flush()
        val greeting = AdbFrame.read(raw.inputStream)
        // 禁止 AUTH/明文连接回退，也不提交新公钥来触发系统授权对话框。
        if (greeting.command != AdbFrame.STLS || greeting.arg0 != AdbFrame.VERSION || greeting.payload.isNotEmpty()) throw AdbProtocolException()
        raw.outputStream.write(AdbFrame(AdbFrame.STLS, AdbFrame.VERSION, 0).encode()); raw.outputStream.flush()
        val tls = connection.upgrade(identity)
        bootstrapSession(tls.inputStream, tls.outputStream, command)
    }

    companion object {
        internal fun bootstrapSession(input: InputStream, output: OutputStream, command: String) {
            val connected = AdbFrame.read(input)
            if (connected.command != AdbFrame.CNXN || connected.arg0 < AdbFrame.VERSION || connected.arg1 <= 0) throw AdbProtocolException()
            val service = "shell:$command\u0000".toByteArray(Charsets.UTF_8)
            if (service.size > minOf(connected.arg1, AdbFrame.MAX_PAYLOAD)) throw AdbProtocolException()
            output.write(AdbFrame(AdbFrame.OPEN, 1, 0, service).encode()); output.flush()
            val open = AdbFrame.read(input)
            // AOSP 在 OPEN 失败时回复 CLSE(0, localId)，不应误判成端口/配对协议错误。
            if (open.command == AdbFrame.CLSE && open.arg0 == 0 && open.arg1 == 1 && open.payload.isEmpty()) {
                throw AdbServiceRejectedException()
            }
            if (open.command != AdbFrame.OKAY || open.arg0 <= 0 || open.arg1 != 1 || open.payload.isNotEmpty()) throw AdbProtocolException()
            val remoteId = open.arg0
            var receivedBytes = 0
            while (true) {
                val frame = AdbFrame.read(input)
                if (frame.arg1 != 1) throw AdbProtocolException()
                when (frame.command) {
                    AdbFrame.WRTE -> {
                        if (frame.arg0 != remoteId) throw AdbProtocolException()
                        // 启动输出不写入日志；限制总量，防止非 adbd 回环端口无限输出。
                        receivedBytes += frame.payload.size
                        if (receivedBytes > 65536) throw AdbProtocolException()
                        output.write(AdbFrame(AdbFrame.OKAY, 1, remoteId).encode()); output.flush()
                    }
                    AdbFrame.CLSE -> {
                        // AOSP 为兼容旧 adbd 接受已建立流的 CLSE(0, localId)；仅限本连接的唯一流。
                        if ((frame.arg0 != 0 && frame.arg0 != remoteId) || frame.payload.isNotEmpty()) throw AdbProtocolException()
                        output.write(AdbFrame(AdbFrame.CLSE, 1, remoteId).encode()); output.flush()
                        return
                    }
                    else -> throw AdbProtocolException()
                }
            }
        }
    }
}

internal class AdbServiceRejectedException : IOException("ADB service rejected")
