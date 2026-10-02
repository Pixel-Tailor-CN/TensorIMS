package app.mystery0.ims.tensor.privileged

import android.os.Bundle
import app.mystery0.ims.tensor.model.TargetConfigProtocol

/** Broker 与主入口共用逐卡阶段和回读策略，不能绕过未知结果保护。 */
class BrokerInstrumentation : SessionInstrumentation() {
    private var arguments = Bundle()
    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        this.arguments = Bundle(requireNotNull(arguments))
        start()
    }
    override fun onStart() {
        if (arguments.containsKey(TargetConfigProtocol.ACTION)) applyTargetConfiguration(arguments)
        else applyLegacyConfiguration(arguments)
    }
}
