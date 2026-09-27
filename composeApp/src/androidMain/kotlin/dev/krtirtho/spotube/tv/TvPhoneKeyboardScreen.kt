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

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import dev.krtirtho.spotube.tv.phone.PhoneKeyboardService
import dev.krtirtho.spotube.tv.phone.PhoneKeyboardState
import dev.krtirtho.spotube.tv.phone.PhoneKeyboardStatus

fun phoneKeyboardStatusText(state: PhoneKeyboardState): String = when (state.status) {
    PhoneKeyboardStatus.Off -> "Off"
    PhoneKeyboardStatus.NoNetwork -> "No network — connect the TV to Wi-Fi or Ethernet"
    PhoneKeyboardStatus.CouldNotStart -> "Could not start"
    PhoneKeyboardStatus.Connected -> "Phone connected"
    PhoneKeyboardStatus.Waiting ->
        if (state.portChanged) "Waiting for your phone (new address — scan again)" else "Waiting for your phone"
}

/**
 * Type into the TV from a phone on the same network (ported from BeatVisualizer):
 * scan the QR code once, keep the page open, and every text field on the TV —
 * Search, the Spotify sign-in page, settings — can be typed from the phone.
 */
@Composable
fun TvPhoneKeyboardScreen() {
    val context = LocalContext.current
    val service = remember { PhoneKeyboardService.get(context) }
    val state by service.state.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(48.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.widthIn(max = 460.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text("Phone keyboard", color = TvColors.TextPrimary, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Type into the TV from your phone on the same Wi-Fi. Scan the code once and keep the page " +
                        "open: whenever a text field is selected on the TV — Search, the Spotify sign-in page, " +
                        "settings — it appears on your phone.",
                    color = TvColors.TextSecondary,
                    fontSize = 16.sp,
                )
                Text(
                    phoneKeyboardStatusText(state),
                    color = if (state.status == PhoneKeyboardStatus.Connected) TvColors.Accent else TvColors.TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                state.address?.let {
                    Text(it, color = TvColors.TextSecondary, fontSize = TvType.Body)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 8.dp)) {
                    if (state.enabled) {
                        TvPillButton(
                            text = "Turn off",
                            primary = false,
                            modifier = Modifier,
                            onClick = { if (!state.busy) service.disable() },
                        )
                        TvPillButton(
                            text = "Forget phone",
                            primary = false,
                            onClick = { if (!state.busy) service.forgetPhone() },
                        )
                    } else {
                        TvPillButtonInitial(text = "Turn on", onClick = { if (!state.busy) service.enable() })
                    }
                }
                if (state.enabled) {
                    Text(
                        "Forget phone makes a new code: the old phone can no longer type into this TV.",
                        color = TvColors.TextMuted,
                        fontSize = TvType.Small,
                    )
                }
            }
            val url = state.pairingUrl
            if (url != null) {
                Box(
                    modifier = Modifier
                        .clip(TvCardShape)
                        .background(Color.White)
                        .padding(16.dp),
                ) {
                    QrCode(data = url, size = 260.dp)
                }
            }
        }
    }
}

@Composable
private fun TvPillButtonInitial(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(TvPillShape)
            .background(TvColors.Accent, TvPillShape)
            .tvFocusable(shape = TvPillShape, focusedScale = 1.05f, initialFocus = true, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 10.dp),
    ) {
        Text(text, color = TvColors.OnAccent, fontSize = TvType.Body, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun QrCode(data: String, size: Dp) {
    val matrix: BitMatrix = remember(data) {
        QRCodeWriter().encode(
            data,
            BarcodeFormat.QR_CODE,
            0,
            0,
            mapOf(EncodeHintType.MARGIN to 0, EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M),
        )
    }
    Canvas(modifier = Modifier.size(size)) {
        val cell = this.size.minDimension / matrix.width
        for (y in 0 until matrix.height) {
            for (x in 0 until matrix.width) {
                if (matrix[x, y]) {
                    drawRect(
                        color = Color.Black,
                        topLeft = Offset(x * cell, y * cell),
                        size = Size(cell + 0.5f, cell + 0.5f),
                    )
                }
            }
        }
    }
}
