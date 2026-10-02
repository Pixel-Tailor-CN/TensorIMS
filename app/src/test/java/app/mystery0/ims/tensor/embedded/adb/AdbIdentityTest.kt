package app.mystery0.ims.tensor.embedded.adb

import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.cert.X509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.RSAKeyGenParameterSpec
import java.util.Base64
import java.util.Date
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.net.ssl.*

class AdbIdentityTest {
    @Test fun adbPublicKeyUsesAospRsa2048MontgomeryWireFormat() {
        val pair = keyPair()
        val identity = identity(pair)
        val public = pair.public as RSAPublicKey
        val encoded = identity.adbPublicKey.toString(Charsets.US_ASCII)
        assertTrue(encoded.endsWith(" TensorIMS@localhost\u0000"))
        val bytes = Base64.getDecoder().decode(encoded.substringBefore(' '))
        assertEquals(524, bytes.size)
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(64, buffer.int)
        val inverse = buffer.int.toLong() and 0xffffffffL
        val modulus = ByteArray(256).also(buffer::get)
        val rr = ByteArray(256).also(buffer::get)
        assertEquals(public.modulus, BigInteger(1, modulus.reversedArray()))
        assertEquals(BigInteger.ONE.shiftLeft(4096).mod(public.modulus), BigInteger(1, rr.reversedArray()))
        assertEquals(65537, buffer.int)
        assertEquals(0xffffffffL, public.modulus.toLong() * inverse and 0xffffffffL)
    }

    @Test fun tls13ClientPresentsTheSameRsaIdentityThatPairingAdvertises() {
        val clientPair = keyPair()
        val client = identity(clientPair)
        val serverPair = keyPair()
        val cert = certificate(serverPair)
        val store = KeyStore.getInstance("PKCS12").apply {
            load(null)
            setKeyEntry("server", serverPair.private, charArrayOf(), arrayOf(cert))
        }
        val managers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply { init(store, charArrayOf()) }
        val trust = object : X509TrustManager {
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String) = Unit
            override fun checkClientTrusted(chain: Array<out X509Certificate>, authType: String) {
                assertArrayEquals(clientPair.public.encoded, chain.single().publicKey.encoded)
                chain.single().verify(clientPair.public)
            }
        }
        val context = SSLContext.getInstance("TLSv1.3").apply { init(managers.keyManagers, arrayOf(trust), SecureRandom()) }
        val executor = Executors.newSingleThreadExecutor()
        try {
            (context.serverSocketFactory.createServerSocket(0, 1, java.net.InetAddress.getLoopbackAddress()) as SSLServerSocket).use { server ->
                server.needClientAuth = true
                server.enabledProtocols = arrayOf("TLSv1.3")
                server.soTimeout = 3000
                val verified = executor.submit<Boolean> {
                    (server.accept() as SSLSocket).use { socket ->
                        socket.soTimeout = 3000
                        socket.startHandshake()
                        assertEquals(23, socket.inputStream.read())
                        socket.outputStream.write(42)
                        true
                    }
                }
                LoopbackConnection(server.localPort).use { connection ->
                    connection.connect()
                    val socket = connection.upgrade(client)
                    socket.outputStream.write(23)
                    assertEquals(42, socket.inputStream.read())
                }
                assertTrue(verified.get(5, TimeUnit.SECONDS))
            }
        } finally { executor.shutdownNow() }
    }

    private fun identity(pair: KeyPair): AdbIdentity = AdbIdentity::class.java.getDeclaredConstructor(RSAPrivateKey::class.java)
        .apply { isAccessible = true }.newInstance(pair.private)

    private fun keyPair() = KeyPairGenerator.getInstance("RSA").apply {
        initialize(RSAKeyGenParameterSpec(2048, RSAKeyGenParameterSpec.F4))
    }.generateKeyPair()

    private fun certificate(pair: KeyPair): X509Certificate {
        val name = X500Name("CN=ADB test")
        val bytes = X509v3CertificateBuilder(name, BigInteger.ONE, Date(0), Date(4102444800000L), name,
            SubjectPublicKeyInfo.getInstance(pair.public.encoded)).build(JcaContentSignerBuilder("SHA256withRSA").build(pair.private)).encoded
        return CertificateFactory.getInstance("X.509").generateCertificate(ByteArrayInputStream(bytes)) as X509Certificate
    }
}
