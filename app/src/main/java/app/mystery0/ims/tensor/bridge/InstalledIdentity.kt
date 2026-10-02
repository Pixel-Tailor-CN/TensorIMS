package app.mystery0.ims.tensor.bridge

import android.content.Context
import android.content.pm.PackageManager
import java.security.MessageDigest

/** 信任根取自目标用户当前安装记录；启动参数中的证书仅用于一致性核验。 */
data class InstalledIdentity(val uid: Int, val userId: Int, val versionCode: Long, val signer: String, val apkPath: String) {
    fun requireCurrent(context: Context, callingUid: Int) {
        check(callingUid == uid) { "AUTH_FAILED: caller UID" }
        check(read(context) == this) { "AUTH_FAILED: installation changed" }
        val packages = context.packageManager.getPackagesForUid(uid)?.toSet().orEmpty()
        check(packages == setOf(BridgeProtocol.PACKAGE)) { "AUTH_FAILED: shared UID" }
    }
    companion object {
        fun read(context: Context): InstalledIdentity {
            val info = context.packageManager.getPackageInfo(BridgeProtocol.PACKAGE,
                PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()))
            val app = requireNotNull(info.applicationInfo)
            check(app.packageName == BridgeProtocol.PACKAGE)
            val certs = requireNotNull(info.signingInfo).apkContentsSigners
            check(certs.isNotEmpty()) { "AUTH_FAILED: no installed signer" }
            val signer = certs.map { digest(it.toByteArray()) }.sorted().joinToString(",")
            return InstalledIdentity(app.uid, app.uid / 100000, info.longVersionCode, signer, app.sourceDir)
        }
        private fun digest(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it.toInt() and 255) }
    }
}
