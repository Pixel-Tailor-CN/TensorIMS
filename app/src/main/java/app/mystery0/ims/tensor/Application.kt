package app.mystery0.ims.tensor

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.UserManager
import app.mystery0.ims.tensor.privilege.PrivilegeRuntime

class Application : Application() {
    val settings by lazy { AppSettingsRepository(this) }
    private val restoreDelegate = lazy { AutoRestoreController(this) }
    val autoRestore by restoreDelegate
    private val idleStop by lazy { EmbeddedIdleStopController(settings, autoRestore) }

    override fun onCreate() {
        super.onCreate()
        // 配置保存在凭据加密存储中；首次解锁前不能读取或创建 SharedPreferences。
        if (getSystemService(UserManager::class.java).isUserUnlocked) {
            LogcatRepository.setAutomaticCapture(settings.autoCapture.value)
            PrivilegeRuntime.initialize(this)
            autoRestore.start()
            idleStop.start(this)
        } else {
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    if (intent.action != Intent.ACTION_USER_UNLOCKED) return
                    unregisterReceiver(this)
                    LogcatRepository.setAutomaticCapture(settings.autoCapture.value)
                    PrivilegeRuntime.initialize(this@Application)
                    autoRestore.start()
                    idleStop.start(this@Application)
                }
            }
            registerReceiver(receiver, IntentFilter(Intent.ACTION_USER_UNLOCKED), RECEIVER_NOT_EXPORTED)
        }
    }

    override fun onTerminate() {
        super.onTerminate()
        if (restoreDelegate.isInitialized()) autoRestore.close()
        LogcatRepository.stopAndClear()
    }
}
