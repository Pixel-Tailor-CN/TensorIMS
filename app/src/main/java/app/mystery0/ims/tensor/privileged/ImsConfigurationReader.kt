package app.mystery0.ims.tensor.privileged

import android.app.Activity
import android.app.Instrumentation
import android.os.Bundle
import app.mystery0.ims.tensor.model.TargetConfigProtocol
import app.mystery0.ims.tensor.model.TargetConfigSnapshot

/** 配置读取与 IMS 注册能力查询分离，避免用当前网络能力反推配置开关。 */
class ImsConfigurationReader : SessionInstrumentation() {
    private var subId: Int = -1

    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        subId = arguments?.getInt(TargetConfigProtocol.SUB_ID, -1) ?: -1
        start()
    }

    override fun onStart() {
        var snapshot = TargetConfigSnapshot(subId)
        val failure = runWithShellPermissionDelegation("ImsConfigurationReader") {
            snapshot = TargetConfigurationReader(context, subId).snapshot()
        }
        if (failure != null) snapshot = snapshot.copy(error = failure.toPrivilegedErrorMessage())
        finish(Activity.RESULT_OK, TargetConfigurationReader.encode(snapshot))
    }
}
