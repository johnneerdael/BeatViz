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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size

/**
 * Shown until the built-in Spotify plugin has a session. "Sign in" opens
 * Spotify's own login page in a WebView; the plugin keeps the session after that.
 */
@Composable
fun TvSignIn(onSignIn: () -> Unit, onPhoneKeyboard: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.widthIn(max = 560.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(TvColors.Accent, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(TvIcons.Music, contentDescription = null, tint = TvColors.OnAccent, modifier = Modifier.size(36.dp))
            }
            Text(
                "Sign in to Spotify",
                color = TvColors.TextPrimary,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "Your home feed, library and recommendations come from your Spotify account. " +
                    "A free account works.",
                color = TvColors.TextSecondary,
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TvPillButtonFocusable(text = "Sign in", onClick = onSignIn)
                TvPillButton(text = "Type with your phone", primary = false, onClick = onPhoneKeyboard)
            }
            Row(modifier = Modifier.padding(top = 8.dp)) {
                Text(
                    "Tip: sign in with email and password; turn on the phone keyboard to type them " +
                        "from your phone. Google, Facebook and Apple buttons often don't work in a TV web view.",
                    color = TvColors.TextMuted,
                    fontSize = TvType.Small,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** A primary pill button that takes focus when the screen opens. */
@Composable
private fun TvPillButtonFocusable(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(TvPillShape)
            .background(TvColors.Accent, TvPillShape)
            .tvFocusable(shape = TvPillShape, focusedScale = 1.06f, initialFocus = true, onClick = onClick)
            .padding(horizontal = 36.dp, vertical = 14.dp),
    ) {
        Text(text, color = TvColors.OnAccent, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}
