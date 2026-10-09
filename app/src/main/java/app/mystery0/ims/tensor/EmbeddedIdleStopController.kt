package app.mystery0.ims.tensor

import android.app.Activity
import android.app.Application
import android.os.Bundle
import app.mystery0.ims.tensor.embedded.WirelessAdbService
import app.mystery0.ims.tensor.privilege.EmbeddedIdleTimer
import app.mystery0.ims.tensor.privilege.EmbeddedIdleStopPolicy
import app.mystery0.ims.tensor.privilege.OperationCoordinator
import app.mystery0.ims.tensor.privilege.PrivilegeRuntime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** 仅在应用进程存活时尽力计时；不申请保活、闹钟、唤醒锁或额外前台服务。 */
class EmbeddedIdleStopController(
    private val settings: AppSettingsRepository,
    private val restore: AutoRestoreController,
) : Application.ActivityLifecycleCallbacks {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val foreground = MutableStateFlow(false)
    private var startedActivities = 0

    fun start(application: Application) {
        application.registerActivityLifecycleCallbacks(this)
        scope.launch {
            val preferences = combine(settings.idleStopEnabled, settings.idleStopMinutes) { enabled, minutes -> enabled to minutes }
            val taskActive = combine(OperationCoordinator.busy, WirelessAdbService.state, restore.running) { busy, wireless, restoring ->
                busy || wireless.active || restoring
            }
            val requests = combine(preferences, foreground, taskActive, PrivilegeRuntime.status,
                OperationCoordinator.completionVersion) { preference, visible, active, status, version ->
                if (EmbeddedIdleStopPolicy.canSchedule(preference.first, visible, active, status)) {
                    EmbeddedIdleTimer.Request(preference.second, status.epoch, version)
                } else null
            }
            EmbeddedIdleTimer().observe(requests) { request ->
                PrivilegeRuntime.stopEmbeddedWhenIdle(request.epoch) {
                    // 获取协调器锁后再次检查。此时 busy 包含本次关闭事务，不能用于自我否决。
                    settings.idleStopEnabled.value && settings.idleStopMinutes.value == request.minutes &&
                        OperationCoordinator.completionVersion.value == request.completionVersion && !foreground.value && !WirelessAdbService.state.value.active && !restore.running.value
                }
            }
        }
    }

    override fun onActivityStarted(activity: Activity) {
        startedActivities++
        foreground.value = true
    }
    override fun onActivityStopped(activity: Activity) {
        startedActivities = (startedActivities - 1).coerceAtLeast(0)
        foreground.value = startedActivities > 0
    }
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}
