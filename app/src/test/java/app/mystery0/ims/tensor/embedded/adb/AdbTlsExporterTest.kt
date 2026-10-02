package app.mystery0.ims.tensor.embedded.adb

import org.junit.Assert.*
import org.junit.Test
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLException
import javax.net.ssl.SSLSocket

class AdbTlsExporterTest {
    @Test fun exporterUsesNulTerminatedLabelNullContextAnd64Bytes() {
        val socket = SSLContext.getDefault().socketFactory.createSocket() as SSLSocket
        socket.use {
            ExporterFixture.failure = null
            ExporterFixture.result = ByteArray(64) { 3 }
            assertArrayEquals(ExporterFixture.result, AdbTlsExporter.invoke(method(), socket))
            assertSame(socket, ExporterFixture.socket)
            assertEquals("adb-label\u0000", ExporterFixture.label)
            assertNull(ExporterFixture.context)
            assertEquals(64, ExporterFixture.length)
        }
    }

    @Test fun rejectsShortExporterOutputInsteadOfPairingWithDifferentPassword() {
        val socket = SSLContext.getDefault().socketFactory.createSocket() as SSLSocket
        socket.use {
            ExporterFixture.failure = null
            val material = ByteArray(63) { 3 }
            ExporterFixture.result = material
            assertThrows(AdbProtocolException::class.java) { AdbTlsExporter.invoke(method(), socket) }
            assertTrue(material.all { it == 0.toByte() })
        }
    }

    @Test fun exposesUnderlyingSafeFailureTypeInsteadOfInvocationTargetException() {
        val socket = SSLContext.getDefault().socketFactory.createSocket() as SSLSocket
        socket.use {
            val failure = SSLException("must not log secret diagnostics")
            ExporterFixture.failure = failure
            try {
                assertSame(failure, assertThrows(SSLException::class.java) { AdbTlsExporter.invoke(method(), socket) })
            } finally { ExporterFixture.failure = null }
        }
    }

    private fun method() = ExporterFixture::class.java.getDeclaredMethod("exportKeyingMaterial", SSLSocket::class.java,
        String::class.java, ByteArray::class.java, Int::class.javaPrimitiveType)

    object ExporterFixture {
        var socket: SSLSocket? = null
        var label: String? = null
        var context: ByteArray? = null
        var length = 0
        var result: ByteArray? = null
        var failure: SSLException? = null
        @JvmStatic fun exportKeyingMaterial(socket: SSLSocket, label: String, context: ByteArray?, length: Int): ByteArray? {
            this.socket = socket; this.label = label; this.context = context; this.length = length
            failure?.let { throw it }
            return result
        }
    }
}
