/* 基于 Shizuku AdbKey 改写；修改：独立身份、noBackup 原子密文、无密钥日志。许可见 assets/licenses。 */
package app.mystery0.ims.tensor.embedded.adb

import android.annotation.SuppressLint
import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.cert.X509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.ByteArrayInputStream
import java.io.File
import java.math.BigInteger
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.Principal
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.RSAKeyGenParameterSpec
import java.security.spec.RSAPublicKeySpec
import java.util.Base64
import java.util.Date
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLEngine
import javax.net.ssl.X509ExtendedKeyManager
import javax.net.ssl.X509ExtendedTrustManager

internal class AdbIdentity private constructor(private val key: RSAPrivateKey) {
    private val publicKey = KeyFactory.getInstance("RSA").generatePublic(RSAPublicKeySpec(key.modulus, RSAKeyGenParameterSpec.F4)) as RSAPublicKey
    private val certificate: X509Certificate by lazy {
        val name = X500Name("CN=TensorIMS independent ADB")
        val encoded = X509v3CertificateBuilder(name, BigInteger.ONE, Date(0), Date(4102444800000L), name,
            SubjectPublicKeyInfo.getInstance(publicKey.encoded)).build(JcaContentSignerBuilder("SHA256withRSA").build(key)).encoded
        CertificateFactory.getInstance("X.509").generateCertificate(ByteArrayInputStream(encoded)) as X509Certificate
    }
    val adbPublicKey: ByteArray by lazy {
        // Android 的 RSA 公钥线格式使用 Montgomery 参数与小端 32 位字，与 X.509 格式不同。
        val r32 = BigInteger.ONE.shiftLeft(32)
        val rr = BigInteger.ONE.shiftLeft(2048).modPow(BigInteger.TWO, publicKey.modulus)
        val buffer = ByteBuffer.allocate(524).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(64).putInt(publicKey.modulus.mod(r32).modInverse(r32).negate().toInt())
        for (number in listOf(publicKey.modulus, rr)) {
            var value = number
            repeat(64) { buffer.putInt(value.toInt()); value = value.shiftRight(32) }
        }
        buffer.putInt(publicKey.publicExponent.toInt())
        (Base64.getEncoder().encodeToString(buffer.array()) + " TensorIMS@localhost\u0000").toByteArray(Charsets.UTF_8)
    }
    val tlsContext: SSLContext by lazy {
        SSLContext.getInstance("TLSv1.3").apply {
            init(arrayOf(object : X509ExtendedKeyManager() {
                override fun chooseClientAlias(types: Array<out String>, issuers: Array<out Principal>?, socket: Socket?) = if ("RSA" in types) "tensorims" else null
                override fun getCertificateChain(alias: String?) = if (alias == "tensorims") arrayOf(certificate) else null
                override fun getPrivateKey(alias: String?): PrivateKey? = if (alias == "tensorims") key else null
                override fun getClientAliases(type: String?, issuers: Array<out Principal>?) = if (type == "RSA") arrayOf("tensorims") else null
                override fun getServerAliases(type: String?, issuers: Array<out Principal>?): Array<String>? = null
                override fun chooseServerAlias(type: String?, issuers: Array<out Principal>?, socket: Socket?): String? = null
            }), arrayOf(AdbLoopbackTrust()), SecureRandom())
        }
    }
    companion object {
        private const val ALIAS = "app.mystery0.ims.tensor.adb.wrap.v1"
        private val AAD = "app.mystery0.ims.tensor:adb:v1".toByteArray(Charsets.UTF_8)
        @Synchronized fun load(context: Context, pairing: Boolean): AdbIdentity {
            val file = AtomicFile(File(context.noBackupFilesDir, "tensorims_adb_identity.v1"))
            val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            var wrappingKey = try { store.getKey(ALIAS, null) as? SecretKey }
            catch (failure: Exception) { if (pairing) null else throw AdbIdentityUnavailableException() }
            if (file.baseFile.exists() && wrappingKey != null) {
                try {
                    val encrypted = file.readFully()
                    check(encrypted.size in 29..8192)
                    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                    cipher.init(Cipher.DECRYPT_MODE, wrappingKey, GCMParameterSpec(128, encrypted, 0, 12))
                    cipher.updateAAD(AAD)
                    val clear = cipher.doFinal(encrypted, 12, encrypted.size - 12)
                    return try {
                        AdbIdentity(KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(clear)) as RSAPrivateKey)
                    } finally { clear.fill(0) }
                } catch (failure: Exception) {
                    // 连接路径绝不默默换身份；用户明确重新配对才允许更新失效的凭据。
                    if (!pairing) throw AdbIdentityUnavailableException()
                    wrappingKey = null
                }
            }
            if (!pairing) throw AdbIdentityUnavailableException()
            if (wrappingKey == null) {
                wrappingKey = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
                    init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                        .setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
                }.generateKey()
            }
            val privateKey = KeyPairGenerator.getInstance("RSA").apply {
                initialize(RSAKeyGenParameterSpec(2048, RSAKeyGenParameterSpec.F4))
            }.generateKeyPair().private as RSAPrivateKey
            val clear = privateKey.encoded
            try {
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.ENCRYPT_MODE, wrappingKey)
                cipher.updateAAD(AAD)
                val encrypted = cipher.iv + cipher.doFinal(clear)
                val output = file.startWrite()
                try { output.write(encrypted); file.finishWrite(output) }
                catch (failure: Exception) { file.failWrite(output); throw failure }
            } finally { clear.fill(0) }
            return AdbIdentity(privateKey)
        }
    }
}

internal class AdbIdentityUnavailableException : java.io.IOException("ADB identity unavailable")

// 无线 ADB 使用设备自签名证书，不能按公网 CA 校验；本客户端硬限制 127.0.0.1。
// 配对的身份认证由 TLS exporter 绑定的 SPAKE2 完成；启动成功另需真实 shell/root Binder 握手。
@SuppressLint("TrustAllX509TrustManager", "CustomX509TrustManager")
private class AdbLoopbackTrust : X509ExtendedTrustManager() {
    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    override fun checkClientTrusted(chain: Array<out X509Certificate>?, type: String?) = Unit
    override fun checkClientTrusted(chain: Array<out X509Certificate>?, type: String?, socket: Socket?) = Unit
    override fun checkClientTrusted(chain: Array<out X509Certificate>?, type: String?, engine: SSLEngine?) = Unit
    override fun checkServerTrusted(chain: Array<out X509Certificate>?, type: String?) = Unit
    override fun checkServerTrusted(chain: Array<out X509Certificate>?, type: String?, socket: Socket?) = Unit
    override fun checkServerTrusted(chain: Array<out X509Certificate>?, type: String?, engine: SSLEngine?) = Unit
}
