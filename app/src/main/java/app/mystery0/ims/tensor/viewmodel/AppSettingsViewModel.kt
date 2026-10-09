package app.mystery0.ims.tensor.viewmodel

import android.app.Application
import android.app.LocaleManager
import android.os.LocaleList
import androidx.lifecycle.AndroidViewModel
import app.mystery0.ims.tensor.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.xmlpull.v1.XmlPullParser

class AppSettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val settings = (application as app.mystery0.ims.tensor.Application).settings
    val theme = settings.theme
    val autoCapture = settings.autoCapture
    val idleStopEnabled = settings.idleStopEnabled
    val idleStopMinutes = settings.idleStopMinutes

    fun setIdleStopEnabled(enabled: Boolean) = settings.setIdleStopEnabled(enabled)
    fun setIdleStopMinutes(minutes: Int) = settings.setIdleStopMinutes(minutes)

    fun selectTheme(theme: app.mystery0.ims.tensor.AppTheme) = settings.setTheme(theme)

    fun setAutoCapture(enabled: Boolean) = settings.setAutoCapture(enabled)

    private val localeManager = application.getSystemService(LocaleManager::class.java)

    // 与系统声明共用语言列表，新增翻译时不需要另维护一份选择器名单。
    val supportedLanguageTags: List<String> = application.resources.getXml(R.xml.locales_config).use { parser ->
        buildList {
            while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == XmlPullParser.START_TAG && parser.name == "locale") {
                    parser.getAttributeValue("http://schemas.android.com/apk/res/android", "name")
                        ?.let { add(it) }
                }
                parser.next()
            }
        }
    }
    private val _languageTag = MutableStateFlow(localeManager.applicationLocales.toLanguageTags())
    val languageTag = _languageTag.asStateFlow()

    fun refreshLanguage() {
        _languageTag.value = localeManager.applicationLocales.toLanguageTags()
    }

    fun selectLanguage(tag: String) {
        require(tag.isEmpty() || tag in supportedLanguageTags)
        // minSdk 为 33，直接使用系统 API 持久化；空列表表示跟随系统，不复制偏好设置。
        val locales = LocaleList.forLanguageTags(tag)
        if (localeManager.applicationLocales != locales) localeManager.applicationLocales = locales
        refreshLanguage()
    }
}
