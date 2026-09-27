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

import android.graphics.Bitmap
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Square (or round, for artists) cover with title and subtitle underneath. */
@Composable
fun TvCard(
    item: TvItem,
    modifier: Modifier = Modifier,
    focusKey: String? = null,
    initialFocus: Boolean = false,
    onClick: () -> Unit,
) {
    val actions = LocalTvActions.current
    Column(
        modifier = modifier
            .width(TvDimens.CardWidth)
            .clip(TvCardShape)
            .tvFocusable(
                focusedScale = 1.04f,
                focusedBackground = TvColors.PanelRaised,
                focusKey = focusKey ?: item.key,
                initialFocus = initialFocus,
                onLongClick = { actions.show(item) },
                onClick = onClick,
            )
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        TvArtwork(
            url = item.imageUrl,
            size = TvDimens.CardArt,
            shape = artworkShape(item.circle),
            placeholder = if (item.circle) TvIcons.Artist else TvIcons.Music,
        )
        Text(
            text = item.title,
            color = TvColors.TextPrimary,
            fontSize = TvType.Body,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = item.subtitle,
            color = TvColors.TextSecondary,
            fontSize = TvType.Small,
            lineHeight = 16.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.height(32.dp),
        )
    }
}

/** A titled, horizontally scrolling row of cards (one home-feed section). */
@OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
@Composable
fun TvCardRow(
    title: String,
    items: List<TvItem>,
    modifier: Modifier = Modifier,
    description: String? = null,
    onShowAll: (() -> Unit)? = null,
    rowKey: String = title,
    initialFocus: Boolean = false,
    onItemClick: (index: Int, item: TvItem) -> Unit,
) {
    if (items.isEmpty()) return
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TvSectionTitle(
            title = title,
            description = description,
            onShowAll = onShowAll,
            modifier = Modifier.padding(horizontal = TvDimens.ContentPadding),
        )
        CompositionLocalProvider(LocalBringIntoViewSpec provides rememberRowPivotSpec(TvDimens.ContentPadding)) {
            LazyRow(
                // Coming back to a row puts focus on the card you left, not the first one.
                modifier = Modifier.focusRestorer(),
                contentPadding = PaddingValues(horizontal = TvDimens.ContentPadding - 8.dp),
            ) {
                itemsIndexed(items, key = { index, item -> "${item.key}#$index" }) { index, item ->
                    TvCard(
                        item = item,
                        focusKey = "$rowKey/${item.key}#$index",
                        initialFocus = initialFocus && index == 0,
                        onClick = { onItemClick(index, item) },
                    )
                }
            }
        }
    }
}

/** Compact tile used for the "recents" grid at the top of Home. */
@Composable
fun TvShortcutTile(
    item: TvItem,
    modifier: Modifier = Modifier,
    initialFocus: Boolean = false,
    onClick: () -> Unit,
) {
    val actions = LocalTvActions.current
    Row(
        modifier = modifier
            .height(56.dp)
            .clip(TvCardShape)
            .background(Color.White.copy(alpha = 0.08f), TvCardShape)
            .tvFocusable(
                focusedBackground = Color.White.copy(alpha = 0.2f),
                focusKey = "shortcut/${item.key}",
                initialFocus = initialFocus,
                onLongClick = { actions.show(item) },
                onClick = onClick,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TvArtwork(url = item.imageUrl, size = 56.dp, shape = androidx.compose.foundation.shape.RoundedCornerShape(topStart = 6.dp, bottomStart = 6.dp))
        Text(
            text = item.title,
            color = TvColors.TextPrimary,
            fontSize = TvType.Body,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(end = 12.dp),
        )
    }
}

/**
 * Average colour of an image, darkened so white text stays readable.
 * Used for the gradient behind collection headers, like the web player does.
 */
@Composable
fun rememberDominantColor(url: String?, fallback: Color = TvColors.HeaderGradientTop): Color {
    val context = LocalContext.current
    var color by remember(url) { mutableStateOf(fallback) }
    LaunchedEffect(url) {
        if (url.isNullOrBlank()) return@LaunchedEffect
        val request = ImageRequest.Builder(context)
            .data(url)
            .size(24)
            .allowHardware(false)
            .build()
        val result = SingletonImageLoader.get(context).execute(request) as? SuccessResult ?: return@LaunchedEffect
        color = withContext(Dispatchers.Default) { averageColor(result.image.toBitmap()) } ?: fallback
    }
    val animated by animateColorAsState(color, label = "dominantColor")
    return animated
}

private fun averageColor(bitmap: Bitmap): Color? {
    var r = 0L
    var g = 0L
    var b = 0L
    var count = 0L
    val step = maxOf(1, minOf(bitmap.width, bitmap.height) / 24)
    for (x in 0 until bitmap.width step step) {
        for (y in 0 until bitmap.height step step) {
            val pixel = bitmap.getPixel(x, y)
            r += (pixel shr 16) and 0xFF
            g += (pixel shr 8) and 0xFF
            b += pixel and 0xFF
            count++
        }
    }
    if (count == 0L) return null
    // Darken to ~55% so the header never gets too bright behind white text.
    val factor = 0.55f
    return Color(
        red = (r / count) / 255f * factor,
        green = (g / count) / 255f * factor,
        blue = (b / count) / 255f * factor,
    )
}
