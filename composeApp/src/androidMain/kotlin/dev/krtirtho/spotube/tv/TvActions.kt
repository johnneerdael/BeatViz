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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.krtirtho.spotube.core.audioplayer.AudioPlayerQueue
import dev.krtirtho.spotube.core.audioplayer.QueueEntry
import dev.krtirtho.spotube.core.playback.CollectionPlaybackHelper
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/** Shows the hold-OK / Menu action sheet for a card, row or track. */
@Stable
class TvActionController {
    var item by mutableStateOf<TvItem?>(null)
        private set

    fun show(item: TvItem) {
        this.item = item
    }

    fun dismiss() {
        item = null
    }
}

val LocalTvActions = staticCompositionLocalOf { TvActionController() }

private data class TvAction(val label: String, val run: suspend () -> Unit)

@Composable
fun TvActionSheetHost(
    controller: TvActionController,
    navigator: TvNavigator,
    playback: CollectionPlaybackHelper = koinInject(),
    audioPlayerQueue: AudioPlayerQueue = koinInject(),
) {
    // Outlives the sheet: actions keep running after it closes.
    val scope = rememberCoroutineScope()
    val item = controller.item ?: return

    val actions = remember(item) {
        buildList {
            val track = item.track
            when (val route = item.route) {
                is TvRoute.Playlist -> {
                    add(TvAction("Play") { playback.playPlaylist(route.id) })
                    add(TvAction("Shuffle play") { playback.playPlaylist(route.id, shuffle = true) })
                    add(TvAction("Play next") { playback.playPlaylistNext(route.id) })
                    add(TvAction("Add to queue") { playback.addPlaylistToQueue(route.id) })
                    add(TvAction("Open playlist") { navigator.navigate(route) })
                }
                is TvRoute.Album -> {
                    add(TvAction("Play") { playback.playAlbum(route.id) })
                    add(TvAction("Shuffle play") { playback.playAlbum(route.id, shuffle = true) })
                    add(TvAction("Play next") { playback.playAlbumNext(route.id) })
                    add(TvAction("Add to queue") { playback.addAlbumToQueue(route.id) })
                    add(TvAction("Open album") { navigator.navigate(route) })
                }
                is TvRoute.Artist -> {
                    add(TvAction("Play popular tracks") { playback.playArtistTopTracks(route.id) })
                    add(TvAction("Add popular tracks to queue") { playback.addArtistTopTracksToQueue(route.id) })
                    add(TvAction("Open artist") { navigator.navigate(route) })
                }
                else -> Unit
            }
            if (track != null) {
                val entry = QueueEntry.StreamingTrack(track = track, url = "")
                add(TvAction("Play") { audioPlayerQueue.playTracks(listOf(track), 0) })
                add(TvAction("Play next") { audioPlayerQueue.addAllAfterCurrent(listOf(entry)) })
                add(TvAction("Add to queue") { audioPlayerQueue.addToQueue(entry) })
                track.album?.let { album ->
                    add(TvAction("Go to album") { navigator.navigate(TvRoute.Album(album.id)) })
                }
                track.artists.forEach { artist ->
                    add(TvAction("Go to ${artist.name}") { navigator.navigate(TvRoute.Artist(artist.id)) })
                }
            }
        }
    }
    if (actions.isEmpty()) {
        SideEffect { controller.dismiss() }
        return
    }

    Dialog(
        onDismissRequest = controller::dismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val firstFocus = remember { FocusRequester() }
        LaunchedEffect(item) { runCatching { firstFocus.requestFocus() } }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(TvColors.Background.copy(alpha = 0.6f)),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Column(
                modifier = Modifier
                    .width(420.dp)
                    .fillMaxSize()
                    .background(TvColors.PanelRaised)
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(bottom = 16.dp),
                ) {
                    TvArtwork(url = item.imageUrl, size = 72.dp, shape = artworkShape(item.circle))
                    Column {
                        Text(
                            item.title,
                            color = TvColors.TextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(item.subtitle, color = TvColors.TextSecondary, fontSize = TvType.Body, maxLines = 1)
                    }
                }
                actions.forEachIndexed { index, action ->
                    Text(
                        text = action.label,
                        color = TvColors.TextPrimary,
                        fontSize = 16.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (index == 0) Modifier.focusRequester(firstFocus) else Modifier)
                            .clip(TvCardShape)
                            .tvFocusable(
                                focusedBackground = TvColors.Highlight,
                                onClick = {
                                    controller.dismiss()
                                    scope.launch { action.run() }
                                },
                            )
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                    )
                }
            }
        }
    }
}
