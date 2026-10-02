/* 基于 Shizuku ADB 协议改写；修改：有界读取、严格阶段校验、禁止输出载荷。许可见 assets/licenses。 */
package app.mystery0.ims.tensor.embedded.adb

import java.io.DataInputStream
import java.io.InputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

class AdbProtocolException : IOException("Invalid ADB protocol")

object LaunchInput {
    fun validPort(port: Int) = port in 1..65535
    fun validPairCode(code: String) = code.length == 6 && code.all { it in '0'..'9' }
}

internal data class AdbFrame(val command: Int, val arg0: Int, val arg1: Int, val payload: ByteArray = byteArrayOf()) {
    fun encode(): ByteArray {
        require(payload.size <= MAX_PAYLOAD)
        return ByteBuffer.allocate(24 + payload.size).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(command).putInt(arg0).putInt(arg1).putInt(payload.size)
            .putInt(checksum(payload)).putInt(command.inv()).put(payload).array()
    }
    companion object {
        const val CNXN = 0x4e584e43
        const val STLS = 0x534c5453
        const val OPEN = 0x4e45504f
        const val OKAY = 0x59414b4f
        const val CLSE = 0x45534c43
        const val WRTE = 0x45545257
        const val VERSION = 0x01000000
        const val MAX_PAYLOAD = 4096
        private val COMMANDS = setOf(CNXN, STLS, OPEN, OKAY, CLSE, WRTE)

        fun read(input: InputStream): AdbFrame {
            val stream = DataInputStream(input)
            val bytes = ByteArray(24).also(stream::readFully)
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            val command = buffer.int
            val arg0 = buffer.int
            val arg1 = buffer.int
            val size = buffer.int
            val checksum = buffer.int
            val magic = buffer.int
            // 在分配内存前拒绝负长度、超长数据以及错误命令，避免恶意端口拖垮应用。
            if (command !in COMMANDS || magic != command.inv() || size !in 0..MAX_PAYLOAD) throw AdbProtocolException()
            val payload = ByteArray(size).also(stream::readFully)
            if (checksum(payload) != checksum) throw AdbProtocolException()
            return AdbFrame(command, arg0, arg1, payload)
        }
        private fun checksum(data: ByteArray) = data.sumOf { it.toInt() and 0xff }
    }
}

internal data class PairingFrame(val type: Int, val payload: ByteArray) {
    fun encode(): ByteArray {
        require(type in 0..1 && payload.size in 1..MAX_PAYLOAD)
        return ByteBuffer.allocate(6 + payload.size).order(ByteOrder.BIG_ENDIAN)
            .put(1.toByte()).put(type.toByte()).putInt(payload.size).put(payload).array()
    }
    companion object {
        const val PEER_INFO_SIZE = 8192
        const val MAX_PAYLOAD = PEER_INFO_SIZE * 2
        fun read(input: InputStream, type: Int): PairingFrame {
            val stream = DataInputStream(input)
            val header = ByteBuffer.wrap(ByteArray(6).also(stream::readFully)).order(ByteOrder.BIG_ENDIAN)
            val version = header.get().toInt()
            val actualType = header.get().toInt()
            val size = header.int
            if (version != 1 || actualType !in 0..1 || actualType != type || size !in 1..MAX_PAYLOAD) throw AdbProtocolException()
            return PairingFrame(actualType, ByteArray(size).also(stream::readFully))
        }
    }
}
