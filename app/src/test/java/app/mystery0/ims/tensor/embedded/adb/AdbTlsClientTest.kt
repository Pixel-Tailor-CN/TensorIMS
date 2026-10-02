package app.mystery0.ims.tensor.embedded.adb

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class AdbTlsClientTest {
    private val connected = AdbFrame(AdbFrame.CNXN, AdbFrame.VERSION, 4096, "device::\u0000".toByteArray())
    private val opened = AdbFrame(AdbFrame.OKAY, 42, 1)

    @Test fun acknowledgesOutputAndAcceptsAospCompatibleCloseWithoutRemoteId() {
        val output = ByteArrayOutputStream()
        AdbTlsClient.bootstrapSession(input(connected, opened, AdbFrame(AdbFrame.WRTE, 42, 1, "ready".toByteArray()),
            AdbFrame(AdbFrame.CLSE, 0, 1)), output, "fixed-command")
        val replies = ByteArrayInputStream(output.toByteArray())
        val open = AdbFrame.read(replies)
        assertEquals(AdbFrame.OPEN, open.command)
        assertArrayEquals("shell:fixed-command\u0000".toByteArray(), open.payload)
        val ack = AdbFrame.read(replies)
        assertEquals(AdbFrame.OKAY, ack.command)
        assertEquals(42, ack.arg1)
        val close = AdbFrame.read(replies)
        assertEquals(AdbFrame.CLSE, close.command)
        assertEquals(42, close.arg1)
        assertEquals(0, replies.available())
    }

    @Test fun initialCloseIsServiceRejectionRatherThanPairingPortFailure() {
        assertThrows(AdbServiceRejectedException::class.java) {
            AdbTlsClient.bootstrapSession(input(connected, AdbFrame(AdbFrame.CLSE, 0, 1)), ByteArrayOutputStream(), "fixed-command")
        }
    }

    @Test fun rejectsCrossStreamPacketsAndUnexpectedClosePayloads() {
        for (frame in listOf(AdbFrame(AdbFrame.WRTE, 0, 1), AdbFrame(AdbFrame.WRTE, 43, 1),
            AdbFrame(AdbFrame.CLSE, 43, 1), AdbFrame(AdbFrame.CLSE, 42, 2), AdbFrame(AdbFrame.CLSE, 42, 1, byteArrayOf(1)))) {
            assertThrows(AdbProtocolException::class.java) {
                AdbTlsClient.bootstrapSession(input(connected, opened, frame), ByteArrayOutputStream(), "fixed-command")
            }
        }
    }

    @Test fun rejectsUnboundedOutputBeforeAcknowledgingTheExcess() {
        val frames = listOf(connected, opened) + List(17) { AdbFrame(AdbFrame.WRTE, 42, 1, ByteArray(4096)) }
        val output = ByteArrayOutputStream()
        assertThrows(AdbProtocolException::class.java) {
            AdbTlsClient.bootstrapSession(input(*frames.toTypedArray()), output, "fixed-command")
        }
        val replies = ByteArrayInputStream(output.toByteArray())
        AdbFrame.read(replies)
        repeat(16) { assertEquals(AdbFrame.OKAY, AdbFrame.read(replies).command) }
        assertEquals(0, replies.available())
    }

    private fun input(vararg frames: AdbFrame) = ByteArrayInputStream(frames.fold(byteArrayOf()) { bytes, frame -> bytes + frame.encode() })
}
