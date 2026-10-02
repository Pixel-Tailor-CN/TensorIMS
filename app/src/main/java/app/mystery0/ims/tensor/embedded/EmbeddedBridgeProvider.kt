package app.mystery0.ims.tensor.embedded

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import app.mystery0.ims.tensor.bridge.BridgeProtocol

/** exported 只为 shell/root 引导；权限依据真实 UID 与单次挑战，不能单靠 signature 权限。 */
class EmbeddedBridgeProvider : ContentProvider() {
    override fun onCreate() = true
    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        require(method == BridgeProtocol.PROVIDER_METHOD) { "Unknown bridge method" }
        val uid = Binder.getCallingUid()
        require(uid == 0 || uid == 2000) { "AUTH_FAILED: bootstrap UID" }
        val challenge = requireNotNull(extras?.getString(BridgeProtocol.CHALLENGE))
        val binder = requireNotNull(extras.getBinder(BridgeProtocol.SERVER))
        return Bundle().apply {
            putBoolean("accepted", EmbeddedConnection.accept(requireNotNull(context), uid, challenge, binder))
        }
    }
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException()
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException()
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException()
}
