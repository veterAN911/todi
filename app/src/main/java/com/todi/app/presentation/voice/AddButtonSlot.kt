package com.todi.app.presentation.voice

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.time.LocalDate

class AddButtonRequest(
    val targetDate: LocalDate?,
    val onTap: () -> Unit,
)

class AddButtonSlot {
    private var owner: Any? = null

    var request by mutableStateOf<AddButtonRequest?>(null)
        private set

    fun show(
        owner: Any,
        request: AddButtonRequest,
    ) {
        if (this.owner === owner && this.request === request) return
        this.owner = owner
        this.request = request
    }

    fun clear(owner: Any) {
        if (this.owner !== owner) return
        this.owner = null
        this.request = null
    }
}

val LocalAddButtonSlot = staticCompositionLocalOf<AddButtonSlot?> { null }

@Composable
fun rememberDockedAddButton(
    targetDate: LocalDate?,
    onTap: () -> Unit,
): Boolean {
    val slot = LocalAddButtonSlot.current ?: return false
    val owner = remember { Any() }
    val latestOnTap by rememberUpdatedState(onTap)
    val request = remember(targetDate) { AddButtonRequest(targetDate) { latestOnTap() } }
    SideEffect { slot.show(owner, request) }
    DisposableEffect(owner) { onDispose { slot.clear(owner) } }
    return true
}

internal val ADD_BUTTON_SIZE = 56.dp

internal val ADD_BUTTON_SINK = 10.dp

internal val ADD_BUTTON_LINE_GAP = 8.dp

internal val ADD_BUTTON_SHOULDER = ADD_BUTTON_SIZE / 2 - ADD_BUTTON_SINK

internal val ADD_BUTTON_OVERHANG = ADD_BUTTON_SIZE - ADD_BUTTON_SINK

@Composable
fun addButtonClearance(): Dp = if (LocalAddButtonSlot.current != null) ADD_BUTTON_OVERHANG + 12.dp else FLOATING_ADD_BUTTON_CLEARANCE

private val FLOATING_ADD_BUTTON_CLEARANCE = ADD_BUTTON_SIZE + 32.dp
