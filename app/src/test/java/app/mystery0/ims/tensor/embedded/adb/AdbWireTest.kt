package app.mystery0.ims.tensor.embedded.adb

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.EOFException
import java.nio.ByteBuffer
import java.nio.ByteOrder

class AdbWireTest {
    @Test fun rejectsPortsOutsideLoopbackServiceRange() {
        assertFalse(LaunchInput.validPort(0))
        assertFalse(LaunchInput.validPort(65536))
        assertTrue(LaunchInput.validPort(37001))
    }
    @Test fun pairingRequiresExactlySixAsciiDigits() {
        for (code in listOf("", "12345", "1234567", "１２３４５６", "12345\n", "12 456")) {
            assertFalse(LaunchInput.validPairCode(code))
        }
        assertTrue(LaunchInput.validPairCode("001234"))
    }
    @Test fun adbReadsPartialStreamsAndChecksPayloadChecksum() {
        val bytes = hex("57525445020000000100000003000000c6000000a8adabba414243")
        val slow = object : ByteArrayInputStream(bytes) {
            override fun read(b: ByteArray, off: Int, len: Int) = super.read(b, off, minOf(len, 1))
        }
        val frame = AdbFrame.read(slow)
        assertEquals(AdbFrame.WRTE, frame.command)
        assertArrayEquals(byteArrayOf(65, 66, 67), frame.payload)
        bytes[24] = 0
        assertThrows(AdbProtocolException::class.java) { AdbFrame.read(ByteArrayInputStream(bytes)) }
    }
    @Test fun rejectsLengthBeforeReadingPayloadAndBadMagic() {
        for (size in listOf(-1, 4097, Int.MAX_VALUE)) {
            val header = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN)
                .putInt(AdbFrame.WRTE).putInt(2).putInt(1).putInt(size).putInt(0).putInt(AdbFrame.WRTE.inv()).array()
            assertThrows(AdbProtocolException::class.java) { AdbFrame.read(ByteArrayInputStream(header)) }
        }
        val bytes = hex("575254450200000001000000000000000000000000000000")
        assertThrows(AdbProtocolException::class.java) { AdbFrame.read(ByteArrayInputStream(bytes)) }
    }
    @Test fun truncatedPayloadNeverReportsSuccess() {
        assertThrows(EOFException::class.java) {
            AdbFrame.read(ByteArrayInputStream(hex("57525445020000000100000003000000c6000000a8adabba41")))
        }
    }
    @Test fun outgoingFrameHasCorrectWireHeader() {
        assertArrayEquals(hex("434e584e00000001001000000700000032020000bcb1a7b1686f73743a3a00"),
            AdbFrame(AdbFrame.CNXN, 0x01000000, 4096, "host::\u0000".toByteArray()).encode())
    }
    @Test fun pairingHeaderIsBigEndianAndBounded() {
        assertArrayEquals(hex("010000000020"), PairingFrame(0, ByteArray(32)).encode().take(6).toByteArray())
        for (header in listOf("020000000020", "010200000020", "010000000000", "010000004001", "0100ffffffff")) {
            assertThrows(AdbProtocolException::class.java) { PairingFrame.read(ByteArrayInputStream(hex(header)), 0) }
        }
    }
    @Test fun rejectsWrongPairingPhase() {
        assertThrows(AdbProtocolException::class.java) {
            PairingFrame.read(ByteArrayInputStream(hex("01010000000100")), 0)
        }
    }
    private fun hex(value: String) = value.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
