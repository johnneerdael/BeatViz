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

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Colours and sizes of the TV interface (dark, web-player style layout). */
object TvColors {
    val Background = Color(0xFF000000)
    val Panel = Color(0xFF121212)
    val PanelRaised = Color(0xFF1F1F1F)
    val Highlight = Color(0xFF2A2A2A)
    val Chip = Color(0xFF2A2A2A)
    val ChipSelected = Color(0xFFFFFFFF)
    val TextPrimary = Color(0xFFFFFFFF)
    val TextSecondary = Color(0xFFB3B3B3)
    val TextMuted = Color(0xFF7A7A7A)
    val Accent = Color(0xFF1ED760)
    val OnAccent = Color(0xFF000000)
    val Focus = Color(0xFFFFFFFF)
    val HeaderGradientTop = Color(0xFF4A3B5C)
}

object TvDimens {
    val Gap = 8.dp
    val PanelRadius = 8.dp
    val LibraryWidth = 300.dp
    val CardWidth = 150.dp
    val CardArt = 134.dp
    val PlayerBarHeight = 72.dp
    val TopBarHeight = 52.dp
    val ContentPadding = 24.dp
    val FocusBorder = 2.dp
}

object TvType {
    val PageTitle = 56.sp
    val SectionTitle = 22.sp
    val Body = 14.sp
    val Small = 12.sp
}

val TvCardShape = RoundedCornerShape(6.dp)
val TvPanelShape = RoundedCornerShape(TvDimens.PanelRadius)
val TvPillShape = RoundedCornerShape(50)
