package app.mystery0.ims.tensor.privileged

import android.app.Instrumentation
import android.util.Log

/** 只有本次 begin 成功才 end；服务端另持有清理状态，业务结果无法伪造清理确认。 */
fun Instrumentation.runWithShellPermissionDelegation(tag: String, block: () -> Unit): Throwable? {
    var delegationStarted = false
    var failure: Throwable? = null
    val explicit = this as? SessionInstrumentation ?: return IllegalStateException("Missing explicit privilege session")
    try {
        explicit.privilegeSession.beginDelegation(explicit.operationId)
        delegationStarted = true
        explicit.verifyTargetIdentities()
        block()
    } catch (t: Throwable) {
        failure = t
    } finally {
        if (delegationStarted) {
            try {
                explicit.privilegeSession.endDelegation(explicit.operationId)
                Log.i(tag, "Stopped owned shell permission delegation")
            } catch (cleanup: Throwable) {
                Log.e(tag, "Failed to stop owned shell permission delegation", cleanup)
                if (failure == null) failure = cleanup else failure.addSuppressed(cleanup)
            }
        }
    }
    return failure
}
