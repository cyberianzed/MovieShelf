package com.movieshelf.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object Palette {
    val Background = Color(0xFF0E1116)
    val Surface = Color(0xFF181D25)
    val Elevated = Color(0xFF232A35)
    val Line = Color(0xFF2E3644)
    val Text = Color(0xFFF8FAFC)
    val Muted = Color(0xFF94A3B8)
    val Focus = Color(0xFFF1F5F9)
    /** Padampetti's mint (its logo's TV); text on it is OnAccent, not white. */
    val Accent = Color(0xFF2DD4BF)
    val AccentSoft = Color(0xFF123F3B)
    val OnAccent = Color(0xFF06302B)
    val Star = Color(0xFFFFC94D)
    val Good = Color(0xFF1FA463)
    val GoodText = Color(0xFF7CE3A8)
    val Error = Color(0xFFFF9C9C)
}

/** Sizes derived from the window so two poster rows fit a TV (960x540dp) and still grow on desktop. */
data class Dims(val poster: Dp, val heroHeight: Dp, val gutter: Dp, val tv: Boolean) {
    val posterHeight: Dp get() = poster * 1.5f
    val cardTextHeight: Dp get() = 46.dp
    val cardHeight: Dp get() = posterHeight + cardTextHeight
    val railCollapsed: Dp get() = 76.dp
    val railExpanded: Dp get() = 216.dp

    companion object {
        fun compute(width: Dp, height: Dp, tv: Boolean): Dims {
            val poster = (((height - 150.dp) / 2 - 46.dp) / 1.5f).coerceIn(118.dp, 184.dp)
            return Dims(
                poster = poster,
                // Spotlight + control strip (~76dp) + a peek of the first month must fit on screen,
                // whatever the device: a 55" 4K TV is 960x540dp, a laptop ~1366x700dp, a monitor ~1900x1000dp.
                heroHeight = (height - 150.dp).coerceIn(240.dp, 560.dp),
                gutter = if (tv || width > 1100.dp) 36.dp else 24.dp,
                tv = tv
            )
        }
    }
}

val LocalDims = staticCompositionLocalOf { Dims(150.dp, 360.dp, 28.dp, false) }
val LocalToday = staticCompositionLocalOf { Today(2026, 1, 1) }
val LocalBetterPosters = staticCompositionLocalOf { false }

/** Tracks whether the user is driving the app with keys (TV remote / keyboard) or a pointer. */
class InputMode { var keyboard by mutableStateOf(false) }

/**
 * Arrow keys move focus. Android TV does this natively, Compose Desktop does not (only Tab),
 * so handle any arrow press nothing else consumed; consuming it stops Android moving twice.
 */
@Composable
fun Modifier.arrowKeyFocus(): Modifier {
    val focusManager = LocalFocusManager.current
    return onKeyEvent { ev ->
        if (ev.type != KeyEventType.KeyDown) return@onKeyEvent false
        val direction = when (ev.key) {
            Key.DirectionLeft -> FocusDirection.Left
            Key.DirectionRight -> FocusDirection.Right
            Key.DirectionUp -> FocusDirection.Up
            Key.DirectionDown -> FocusDirection.Down
            else -> return@onKeyEvent false
        }
        focusManager.moveFocus(direction)
    }
}
