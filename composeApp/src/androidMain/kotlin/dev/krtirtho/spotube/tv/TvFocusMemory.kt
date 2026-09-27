/*
 * Copyright (C) 2026 Kingkor Roy Tirtho and Spotube Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package dev.krtirtho.spotube.tv

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp

/**
 * Remembers which element had focus on each screen, so Back returns focus to
 * the card or row you left, and a fresh screen focuses its designated first
 * element instead of whatever Android picks.
 */
@Stable
class TvFocusMemory {
    private val lastFocused = mutableMapOf<String, String>()
    private val pendingRestore = mutableSetOf<String>()

    /** Call when a screen becomes visible (new or returned to via Back). */
    fun onScreenShown(screen: String) {
        pendingRestore += screen
    }

    fun record(screen: String, key: String) {
        lastFocused[screen] = key
    }

    /**
     * True once per screen visit for the element that should take focus: the
     * remembered element, or the initial element when nothing was remembered.
     */
    fun claim(screen: String, key: String, isInitial: Boolean): Boolean {
        if (screen !in pendingRestore) return false
        val remembered = lastFocused[screen]
        val wins = if (remembered != null) remembered == key else isInitial
        if (wins) pendingRestore -= screen
        return wins
    }

    fun forget(screen: String) {
        lastFocused -= screen
        pendingRestore -= screen
    }
}

data class TvFocusScope(val screen: String, val memory: TvFocusMemory)

val LocalTvFocusScope = compositionLocalOf<TvFocusScope?> { null }

/**
 * Keeps the focused element at ~30% from the top of vertical lists (the usual
 * TV "pivot"), instead of scrolling only just enough to show it.
 */
@OptIn(ExperimentalFoundationApi::class)
class TvPivotBringIntoViewSpec(
    private val parentFraction: Float,
    private val leadingPx: Float = 0f,
) : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        val initialTarget = (parentFraction * containerSize).coerceAtLeast(leadingPx)
        val spaceForItem = containerSize - initialTarget
        val target = if (size <= containerSize && spaceForItem < size) containerSize - size else initialTarget
        return offset - target
    }
}

@Composable
fun rememberVerticalPivotSpec(): TvPivotBringIntoViewSpec =
    remember { TvPivotBringIntoViewSpec(parentFraction = 0.3f) }

/** Rows keep the focused card at the leading edge (after the row's padding). */
@Composable
fun rememberRowPivotSpec(leading: Dp): TvPivotBringIntoViewSpec {
    val px = with(LocalDensity.current) { leading.toPx() }
    return remember(px) { TvPivotBringIntoViewSpec(parentFraction = 0f, leadingPx = px) }
}
