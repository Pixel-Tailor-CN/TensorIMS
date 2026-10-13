package app.mystery0.ims.tensor.model

/** 请求完成与覆盖删除核验分开，不将未核验的删除显示为成功。 */
data class CarrierConfigResetResult(val verified: Boolean = false, val requestCompleted: Boolean = false, val message: String? = null)
