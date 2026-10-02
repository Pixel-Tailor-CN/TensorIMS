package app.mystery0.ims.tensor.embedded.adb

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.EOFException

class AdbPairingProtocolTest {
    private val peerMessage = ByteArray(32) { (it + 1).toByte() }
    private val encryptedPeerInfo = ByteArray(8208) { 7 }
    private val publicKey = "test-public-key\u0000".toByteArray()

    @Test fun exchangesOfficialSizedFramesInOrderAndClearsDecryptedPeerInfo() {
        val cipher = TestCipher()
        val stages = mutableListOf<PairingStage>()
        val output = ByteArrayOutputStream()
        AdbPairingProtocol.exchange(peerInput(), output, publicKey, cipher, stages::add)
        assertEquals(listOf(PairingStage.SPAKE2_EXCHANGE, PairingStage.PEER_INFO_EXCHANGE), stages)
        assertArrayEquals(peerMessage, cipher.receivedMessage)
        assertEquals(8192, cipher.sentPeerInfo!!.size)
        assertEquals(0, cipher.sentPeerInfo!![0].toInt())
        assertArrayEquals(publicKey, cipher.sentPeerInfo!!.copyOfRange(1, publicKey.size + 1))
        assertTrue(cipher.decryptedResponse.all { it == 0.toByte() })
        val frames = ByteArrayInputStream(output.toByteArray())
        assertEquals(32, PairingFrame.read(frames, 0).payload.size)
        assertEquals(8208, PairingFrame.read(frames, 1).payload.size)
        assertEquals(0, frames.available())
    }

    @Test fun rejectsSpakeMessageLargerThanBoringSslLimitBeforeCallingNativeCipher() {
        val cipher = TestCipher()
        val incoming = PairingFrame(0, ByteArray(33)).encode()
        assertThrows(AdbProtocolException::class.java) {
            AdbPairingProtocol.exchange(ByteArrayInputStream(incoming), ByteArrayOutputStream(), publicKey, cipher) {}
        }
        assertNull(cipher.receivedMessage)
    }

    @Test fun invalidPasswordAuthenticationIsNotReportedAsPortMismatch() {
        val cipher = TestCipher(authenticated = false)
        assertThrows(InvalidPairingCodeException::class.java) {
            AdbPairingProtocol.exchange(peerInput(), ByteArrayOutputStream(), publicKey, cipher) {}
        }
    }

    @Test fun malformedAuthenticatedPeerInfoNeverReportsSuccessAndIsCleared() {
        for (response in listOf(ByteArray(8191), ByteArray(8192), ByteArray(8192).also { it[0] = 1 })) {
            val cipher = TestCipher(response = response)
            assertThrows(AdbProtocolException::class.java) {
                AdbPairingProtocol.exchange(peerInput(), ByteArrayOutputStream(), publicKey, cipher) {}
            }
            assertTrue(response.all { it == 0.toByte() })
        }
    }

    @Test fun truncatedPeerInfoNeverReportsSuccess() {
        val input = ByteArrayInputStream(PairingFrame(0, peerMessage).encode() + PairingFrame(1, encryptedPeerInfo).encode().dropLast(1))
        assertThrows(EOFException::class.java) {
            AdbPairingProtocol.exchange(input, ByteArrayOutputStream(), publicKey, TestCipher()) {}
        }
    }

    private fun peerInput() = ByteArrayInputStream(PairingFrame(0, peerMessage).encode() + PairingFrame(1, encryptedPeerInfo).encode())

    private inner class TestCipher(private val authenticated: Boolean = true,
                                   response: ByteArray = ByteArray(8192).also { it[0] = 1; it[1] = 'a'.code.toByte() }) : PairingCipher {
        var receivedMessage: ByteArray? = null
        var sentPeerInfo: ByteArray? = null
        val decryptedResponse = response
        override fun message() = ByteArray(32) { 2 }
        override fun initCipher(peer: ByteArray): Boolean { receivedMessage = peer; return true }
        override fun encrypt(data: ByteArray): ByteArray { sentPeerInfo = data.copyOf(); return encryptedPeerInfo }
        override fun decrypt(data: ByteArray) = if (authenticated) decryptedResponse else null
        override fun close() = Unit
    }
}
