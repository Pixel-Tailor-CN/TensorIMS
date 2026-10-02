package app.mystery0.ims.tensor.ui

/** 动作状态由 Activity 级 ViewModel 持有，旋转与导航不重新提交启动请求。 */
enum class BackendAction { CHOOSE_MODE, PAIR, ROOT, WIRELESS, RECOVER }

data class BackendActionState(
    val action: BackendAction? = null,
    val notice: Int? = null,
    val error: String? = null,
) {
    val inProgress: Boolean get() = action != null
}
