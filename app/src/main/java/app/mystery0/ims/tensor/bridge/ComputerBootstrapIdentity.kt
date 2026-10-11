package app.mystery0.ims.tensor.bridge

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/** 将包管理器安装身份与原生目录绑定成稳定摘要，不接收电脑端自述字段。 */
object ComputerBootstrapIdentity {
    fun digest(identity: InstalledIdentity, nativeLibraryDir: String): String {
        require(nativeLibraryDir.isNotBlank())
        val fields = listOf(
            BridgeProtocol.PACKAGE,
            identity.userId.toString(),
            identity.uid.toString(),
            identity.versionCode.toString(),
            identity.signer,
            identity.apkPath,
            nativeLibraryDir,
        )
        val canonical = fields.joinToString(separator = "") { "${it.length}:$it" }
        return MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }
}
