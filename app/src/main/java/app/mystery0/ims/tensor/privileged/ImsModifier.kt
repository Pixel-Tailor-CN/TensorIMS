package app.mystery0.ims.tensor.privileged

import android.os.Build
import android.os.Bundle
import android.telephony.CarrierConfigManager
import app.mystery0.ims.tensor.model.FiveGPlusConfig
import app.mystery0.ims.tensor.model.VoWifiRoamingConfig
import app.mystery0.ims.tensor.model.TargetConfigProtocol

class ImsModifier : SessionInstrumentation() {
    private var targetArguments: Bundle? = null

    override fun onStart() {
        targetArguments?.let {
            if (it.containsKey(TargetConfigProtocol.ACTION)) applyTargetConfiguration(it)
            else applyLegacyConfiguration(it)
        }
    }
    companion object Companion {
        private const val TAG = "ImsModifier"
        const val BUNDLE_SELECT_SIM_ID = "select_sim_id"
        const val BUNDLE_RESET = "reset"
        const val BUNDLE_RESULT = "result"
        const val BUNDLE_RESULT_MSG = "result_msg"

        fun buildResetBundle(): Bundle = Bundle().apply {
            putBoolean(BUNDLE_RESET, true)
        }

        fun buildBundle(
            carrierName: String?,
            imsUserAgent: String?,
            enableVoLTE: Boolean,
            enableVoWiFi: Boolean,
            enableVoWifiRoaming: Boolean,
            enableVT: Boolean,
            enableVoNR: Boolean,
            enableCrossSIM: Boolean,
            enableUT: Boolean,
            enable5GNR: Boolean,
            enable5GThreshold: Boolean,
            enable5GPlusIcon: Boolean,
            enableEnhanced4GLte: Boolean,
            hideLtePlusDataIcon: Boolean,
            enableShow4GForLTE: Boolean,
        ): Bundle {
            val bundle = Bundle()
            // 运营商名称
            if (carrierName?.isNotBlank() == true) {
                bundle.putBoolean(CarrierConfigManager.KEY_CARRIER_NAME_OVERRIDE_BOOL, true)
                bundle.putString(CarrierConfigManager.KEY_CARRIER_NAME_STRING, carrierName)
                bundle.putString(CarrierConfigManager.KEY_CARRIER_CONFIG_VERSION_STRING, ":3")
            }
            // IMS User Agent
            if (imsUserAgent?.isNotBlank() == true) {
                bundle.putString(
                    CarrierConfigManager.Ims.KEY_IMS_USER_AGENT_STRING,
                    imsUserAgent
                )
            }

            // VoLTE 配置
            if (enableVoLTE) {
                bundle.putBoolean(CarrierConfigManager.KEY_CARRIER_VOLTE_AVAILABLE_BOOL, true)
            }

            // Enhanced 4G LTE / LTE+ 配置。与 VoLTE 拆分，避免开启 VoLTE 时隐式修改图标行为。
            if (enableEnhanced4GLte) {
                bundle.putBoolean(CarrierConfigManager.KEY_EDITABLE_ENHANCED_4G_LTE_BOOL, true)
                bundle.putBoolean(CarrierConfigManager.KEY_ENHANCED_4G_LTE_ON_BY_DEFAULT_BOOL, true)
                bundle.putBoolean(CarrierConfigManager.KEY_HIDE_ENHANCED_4G_LTE_BOOL, false)
                bundle.putBoolean(CarrierConfigManager.KEY_HIDE_LTE_PLUS_DATA_ICON_BOOL, false)
            }
            if (hideLtePlusDataIcon) {
                bundle.putBoolean(CarrierConfigManager.KEY_HIDE_LTE_PLUS_DATA_ICON_BOOL, true)
            }

            // LTE 显示为 4G
            if (enableShow4GForLTE) {
                bundle.putBoolean("show_4g_for_lte_data_icon_bool", true)
            }

            // VT (视频通话) 配置
            if (enableVT) {
                bundle.putBoolean(CarrierConfigManager.KEY_CARRIER_VT_AVAILABLE_BOOL, true)
            }

            // UT 补充服务配置
            if (enableUT) {
                bundle.putBoolean(CarrierConfigManager.KEY_CARRIER_SUPPORTS_SS_OVER_UT_BOOL, true)
            }

            // 跨 SIM 通话配置
            if (enableCrossSIM) {
                bundle.putBoolean(
                    CarrierConfigManager.KEY_CARRIER_CROSS_SIM_IMS_AVAILABLE_BOOL,
                    true
                )
                bundle.putBoolean(
                    CarrierConfigManager.KEY_ENABLE_CROSS_SIM_CALLING_ON_OPPORTUNISTIC_DATA_BOOL,
                    true
                )
            }

            // VoWiFi 配置
            if (enableVoWiFi) {
                bundle.putBoolean(CarrierConfigManager.KEY_CARRIER_WFC_IMS_AVAILABLE_BOOL, true)
                bundle.putBoolean(
                    CarrierConfigManager.KEY_CARRIER_WFC_SUPPORTS_WIFI_ONLY_BOOL,
                    true
                )
                bundle.putBoolean(CarrierConfigManager.KEY_EDITABLE_WFC_MODE_BOOL, true)
                bundle.putBoolean(CarrierConfigManager.KEY_EDITABLE_WFC_ROAMING_MODE_BOOL, true)
                // KEY_SHOW_WIFI_CALLING_ICON_IN_STATUS_BAR_BOOL
                bundle.putBoolean("show_wifi_calling_icon_in_status_bar_bool", true)
                // KEY_WFC_SPN_FORMAT_IDX_INT
                bundle.putInt("wfc_spn_format_idx_int", 6)
            }

            // 漫游 VoWiFi 可能产生额外费用，关闭时不写入，避免覆盖运营商默认值。
            VoWifiRoamingConfig.putOverride(bundle, enableVoWifiRoaming)

            // VoNR (5G 语音) 配置
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                if (enableVoNR) {
                    bundle.putBoolean(CarrierConfigManager.KEY_VONR_ENABLED_BOOL, true)
                    bundle.putBoolean(CarrierConfigManager.KEY_VONR_SETTING_VISIBILITY_BOOL, true)
                }
            }

            // 5G NR 配置
            if (enable5GNR) {
                bundle.putIntArray(
                    CarrierConfigManager.KEY_CARRIER_NR_AVAILABILITIES_INT_ARRAY,
                    intArrayOf(
                        CarrierConfigManager.CARRIER_NR_AVAILABILITY_NSA,
                        CarrierConfigManager.CARRIER_NR_AVAILABILITY_SA
                    )
                )
                if (enable5GPlusIcon) {
                    // 只有达到较高 NR 聚合带宽并满足频段条件时，系统才显示 5G+ 图标。
                    FiveGPlusConfig.putOverrides(bundle)
                }
                if (enable5GThreshold) {
                    bundle.putIntArray(
                        CarrierConfigManager.KEY_5G_NR_SSRSRP_THRESHOLDS_INT_ARRAY,  // Boundaries: [-140 dBm, -44 dBm]
                        intArrayOf(
                            -128,  /* SIGNAL_STRENGTH_POOR */
                            -118,  /* SIGNAL_STRENGTH_MODERATE */
                            -108,  /* SIGNAL_STRENGTH_GOOD */
                            -98,  /* SIGNAL_STRENGTH_GREAT */
                        )
                    )
                }
            }
            return bundle
        }
    }

    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        targetArguments = Bundle(requireNotNull(arguments))
        // legacy 的有限回读也在工作线程运行，避免阻塞主线程。
        start()
    }
}
