package app.mystery0.ims.tensor.privileged

import android.app.Activity
import android.app.Instrumentation
import android.os.Bundle
import app.mystery0.ims.tensor.bridge.BridgeProtocol
import app.mystery0.ims.tensor.bridge.CarrierWriteProgress
import app.mystery0.ims.tensor.bridge.CarrierWritePermissionDenied
import android.os.PersistableBundle
import android.os.SystemClock
import android.util.Log
import app.mystery0.ims.tensor.model.TargetConfigMapper
import app.mystery0.ims.tensor.model.TargetConfigProtocol
import app.mystery0.ims.tensor.model.TargetConfigSnapshot

/** 主路径与 Broker 共享目标语义，必须在 Instrumentation 工作线程执行有限回读。 */
internal fun Instrumentation.applyTargetConfiguration(arguments: Bundle) {
    val subId = arguments.getInt(TargetConfigProtocol.SUB_ID, -1)
    var snapshot = TargetConfigSnapshot(subId)
    val progress = CarrierWriteProgress()
    val failure = runWithShellPermissionDelegation("TargetConfiguration") {
        val reader = TargetConfigurationReader(context, subId)
        val identity = arguments.getString(TargetConfigProtocol.IDENTITY).orEmpty()
        check(identity.isNotBlank() && reader.identity() == identity) { "SIM identity changed; refresh configuration" }
        snapshot = reader.snapshot()
        val encoded = requireNotNull(arguments.getBundle(TargetConfigProtocol.VALUES)) { "Missing target configuration" }
        val targets = TargetConfigProtocol.decode(encoded)
        require(targets.isNotEmpty() && targets.size == encoded.size()) { "Invalid target configuration" }
        check(targets.keys.all { it in snapshot.values }) { "Target configuration has unsupported or unreadable features" }
        val defaults = reader.defaults()
        val expected = targets.flatMap { (feature, value) ->
            TargetConfigMapper.writes(feature, value, defaults).entries.map { it.key to it.value }
        }.toMap()
        check(expected.isNotEmpty()) { "No writable targets" }
        check(reader.identity() == identity) { "SIM identity changed before writing" }
        if (!TargetConfigMapper.matches(reader.raw(), expected)) {
            val values = PersistableBundle().apply {
                expected.forEach { (key, value) ->
                    when (value) {
                        is Boolean -> putBoolean(key, value)
                        is String -> putString(key, value)
                        is Int -> putInt(key, value)
                        is IntArray -> putIntArray(key, value)
                        else -> error("Unsupported carrier configuration type")
                    }
                }
            }
            progress.beginWrite()
            try { invokeCarrierOverride(reader.manager, subId, values) } catch (denied: CarrierWritePermissionDenied) {
                progress.permissionRejected()
                throw denied
            }
            progress.writeAccepted()
        }
        // 服务端异步合并；在主进程 15 秒超时以内回读，不能把 API 返回当作配置生效。
        val deadline = SystemClock.elapsedRealtime() + 4_000L
        var verified = false
        do {
            check(reader.identity() == identity) { "SIM identity changed during verification" }
            if (TargetConfigMapper.matches(reader.raw(), expected)) {
                snapshot = reader.snapshot(expected)
                check(snapshot.identity == identity) { "SIM identity changed during verification" }
                verified = true
                break
            }
            Thread.sleep(150)
        } while (SystemClock.elapsedRealtime() < deadline)
        check(verified) { "Configuration read-back did not match; refresh or retry" }
        Log.i("TargetConfiguration", "Target configuration verified for subId=$subId")
    }
    if (failure != null) {
        Log.e("TargetConfiguration", "Target configuration failed for subId=$subId", failure)
        snapshot = snapshot.copy(error = failure.toPrivilegedErrorMessage())
    }
    val result = TargetConfigurationReader.encode(snapshot)
    if (failure != null) {
        if (progress.uncertainOnFailure) result.putString(BridgeProtocol.ERROR_CODE, "OPERATION_INDETERMINATE")
        else if (progress.retryAllowed) result.putBoolean(BridgeProtocol.BROKER_RETRY_ALLOWED, true)
    }
    finish(Activity.RESULT_OK, result)
}
