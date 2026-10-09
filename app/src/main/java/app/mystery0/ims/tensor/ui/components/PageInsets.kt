package app.mystery0.ims.tensor.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection

// 底部不裁切滚动视口，安全距离放在滚动内容末尾。
val pageContentInsets: WindowInsets
    @Composable get() = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)

@Composable
fun Modifier.pageContentPadding(padding: PaddingValues): Modifier {
    val direction = LocalLayoutDirection.current
    val viewportPadding = PaddingValues(
        start = padding.calculateStartPadding(direction),
        top = padding.calculateTopPadding(),
        end = padding.calculateEndPadding(direction),
    )
    return fillMaxSize().padding(viewportPadding).consumeWindowInsets(viewportPadding)
}

@Composable
fun ScrollEndSpacer(padding: PaddingValues) {
    val navigationHeight = with(LocalDensity.current) { WindowInsets.navigationBars.getBottom(this).toDp() }
    Spacer(Modifier.height(maxOf(navigationHeight, padding.calculateBottomPadding())))
}
