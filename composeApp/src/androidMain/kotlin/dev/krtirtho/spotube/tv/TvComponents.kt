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

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Makes an element reachable with the D-pad: a white outline (and optional
 * background/scale) while focused. OK/Enter activates [onClick]; holding OK or
 * pressing the remote's Menu key activates [onLongClick].
 *
 * [focusKey] lets the screen remember this element, so Back restores focus to
 * it; [initialFocus] marks the element a screen focuses when first opened.
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.tvFocusable(
    shape: Shape = TvCardShape,
    focusedScale: Float = 1f,
    focusedBackground: Color? = null,
    showBorder: Boolean = true,
    focusKey: String? = null,
    initialFocus: Boolean = false,
    onFocusChanged: ((Boolean) -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    onClick: (() -> Unit)?,
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (focused) focusedScale else 1f, label = "tvFocusScale")
    val focusScope = LocalTvFocusScope.current
    val focusRequester = remember { FocusRequester() }
    val memoryKey = focusKey ?: if (initialFocus) "initial" else null

    LaunchedEffect(focused) {
        onFocusChanged?.invoke(focused)
        if (focused && focusScope != null && memoryKey != null) {
            focusScope.memory.record(focusScope.screen, memoryKey)
        }
    }
    LaunchedEffect(focusScope?.screen, memoryKey) {
        if (focusScope != null && memoryKey != null &&
            focusScope.memory.claim(focusScope.screen, memoryKey, initialFocus)
        ) {
            // Let the first frame lay out before moving focus into it.
            withFrameNanos { }
            runCatching { focusRequester.requestFocus() }
        }
    }

    val base = this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .then(
            if (focused && focusedBackground != null) {
                Modifier.background(focusedBackground, shape)
            } else {
                Modifier
            }
        )
        .then(
            if (focused && showBorder) {
                Modifier.border(TvDimens.FocusBorder, TvColors.Focus, shape)
            } else {
                Modifier
            }
        )
        .focusRequester(focusRequester)
        .then(
            if (onLongClick != null) {
                Modifier.onKeyEvent { event ->
                    if (event.key == Key.Menu && event.type == KeyEventType.KeyUp) {
                        onLongClick()
                        true
                    } else {
                        false
                    }
                }
            } else {
                Modifier
            }
        )

    if (onClick != null || onLongClick != null) {
        base.combinedClickable(
            interactionSource = interactionSource,
            indication = null,
            onLongClick = onLongClick,
            onClick = { onClick?.invoke() },
        )
    } else {
        base.focusable(interactionSource = interactionSource)
    }
}

@Composable
fun TvChip(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(TvPillShape)
            .background(if (selected) TvColors.ChipSelected else TvColors.Chip, TvPillShape)
            .tvFocusable(shape = TvPillShape, focusedScale = 1.05f, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        Text(
            text = text,
            color = if (selected) Color.Black else TvColors.TextPrimary,
            fontSize = TvType.Body,
            maxLines = 1,
        )
    }
}

@Composable
fun TvIconButton(
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    iconSize: Dp = 22.dp,
    tint: Color = TvColors.TextSecondary,
    background: Color = Color.Transparent,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(background, CircleShape)
            .tvFocusable(
                shape = CircleShape,
                focusedScale = 1.1f,
                focusedBackground = TvColors.Highlight,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(iconSize),
        )
    }
}

/** The round green play button used on collection headers. */
@Composable
fun TvPlayButton(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    initialFocus: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(TvColors.Accent, CircleShape)
            .tvFocusable(
                shape = CircleShape,
                focusedScale = 1.1f,
                focusKey = "play",
                initialFocus = initialFocus,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (isPlaying) TvIcons.Pause else TvIcons.Play,
            contentDescription = if (isPlaying) "Pause" else "Play",
            tint = TvColors.OnAccent,
            modifier = Modifier.size(size * 0.45f),
        )
    }
}

@Composable
fun TvTextButton(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = TvColors.TextSecondary,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(TvPillShape)
            .tvFocusable(shape = TvPillShape, focusedBackground = TvColors.Highlight, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(text = text, color = color, fontSize = TvType.Body, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
fun TvPillButton(
    text: String,
    modifier: Modifier = Modifier,
    primary: Boolean = true,
    icon: ImageVector? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .clip(TvPillShape)
            .background(if (primary) TvColors.TextPrimary else TvColors.Chip, TvPillShape)
            .tvFocusable(shape = TvPillShape, focusedScale = 1.05f, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val contentColor = if (primary) Color.Black else TvColors.TextPrimary
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(18.dp))
        }
        Text(text = text, color = contentColor, fontSize = TvType.Body, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun TvSectionTitle(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    onShowAll: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.Bottom,
    ) {
        androidx.compose.foundation.layout.Column(modifier = Modifier.weight(1f)) {
            if (!description.isNullOrBlank()) {
                Text(
                    text = description,
                    color = TvColors.TextSecondary,
                    fontSize = TvType.Small,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = title,
                color = TvColors.TextPrimary,
                fontSize = TvType.SectionTitle,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (onShowAll != null) {
            TvTextButton(text = "Show all", onClick = onShowAll)
        }
    }
}

@Composable
fun TvMessage(
    title: String,
    modifier: Modifier = Modifier,
    body: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        androidx.compose.foundation.layout.Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, color = TvColors.TextPrimary, fontSize = TvType.SectionTitle, fontWeight = FontWeight.Bold)
            if (body != null) {
                Text(body, color = TvColors.TextSecondary, fontSize = TvType.Body)
            }
            if (actionLabel != null && onAction != null) {
                TvPillButton(text = actionLabel, onClick = onAction)
            }
        }
    }
}
