package app.mystery0.ims.tensor.privilege

import android.content.Context
import android.os.Bundle
import app.mystery0.ims.tensor.bridge.InstalledIdentity
import rikka.shizuku.ShizukuBinderWrapper

/** 官方 Binder 只在官方适配器持有，不进入业务 Instrumentation 的静态状态。 */
class OfficialPrivilegeSession(context: Context, operation: OperationType, arguments: Bundle, operationId: String, epoch: Long) :
    ExplicitPrivilegeSession(context, InstalledIdentity.read(context), operation,
        OperationArguments.validate(operation, arguments), operationId, epoch, { ShizukuBinderWrapper(it) })
