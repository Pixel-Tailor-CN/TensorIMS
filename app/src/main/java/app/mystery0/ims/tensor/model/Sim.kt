package app.mystery0.ims.tensor.model

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable

@Immutable
data class SimSelection(
    val subId: Int,
    val displayName: String,
    val carrierName: String,
    val simSlotIndex: Int,
    val showTitle: String = buildString {
        append("SIM ")
        append(simSlotIndex + 1)
        append(": ")
        append(displayName.trim().ifBlank { carrierName.trim() })
    }
)
