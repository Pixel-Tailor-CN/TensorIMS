package app.mystery0.ims.tensor.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/** 说明可以换行，图标和操作始终相对整行垂直居中。 */
@Composable
fun GroupedSettingsItem(
    title: String,
    summary: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    first: Boolean = false,
    last: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(
                topStart = if (first) 24.dp else 4.dp,
                topEnd = if (first) 24.dp else 4.dp,
                bottomStart = if (last) 24.dp else 4.dp,
                bottomEnd = if (last) 24.dp else 4.dp,
            ))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .then(modifier)
            .then(if (onClick != null) Modifier.clickable(enabled = enabled, onClick = onClick) else Modifier)
            .heightIn(min = 72.dp).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        val opacity = if (enabled) 1f else 0.5f
        Icon(icon, null, modifier = Modifier.alpha(opacity), tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f).alpha(opacity)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (trailing != null) {
            trailing()
        } else if (onClick != null) {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, modifier = Modifier.alpha(opacity))
        }
    }
}
