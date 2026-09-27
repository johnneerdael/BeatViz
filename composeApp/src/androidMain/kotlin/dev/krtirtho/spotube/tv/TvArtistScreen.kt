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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import dev.krtirtho.spotube.core.audioplayer.AudioPlayerInterface
import dev.krtirtho.spotube.core.audioplayer.AudioPlayerQueue
import dev.krtirtho.spotube.core.audioplayer.PlayerState
import dev.krtirtho.spotube.core.audioplayer.QueueEntry
import dev.krtirtho.spotube.modules.artist.ArtistScreenState
import dev.krtirtho.spotube.modules.artist.ArtistViewModel
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import java.text.NumberFormat

@Composable
fun TvArtistScreen(
    artistId: String,
    navigator: TvNavigator,
    viewModel: ArtistViewModel = koinViewModel(key = artistId, parameters = { parametersOf(artistId) }),
    audioPlayer: AudioPlayerInterface = koinInject(),
    audioPlayerQueue: AudioPlayerQueue = koinInject(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val playerState by audioPlayer.playerStateFlow.collectAsStateWithLifecycle()
    val currentEntry by audioPlayerQueue.currentQueueEntryFlow.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    when (val current = state) {
        ArtistScreenState.Loading -> TvMessage(title = "Loading artist…")
        is ArtistScreenState.Error -> TvMessage(
            title = "Couldn't load this artist",
            body = current.message,
            actionLabel = "Try again",
            onAction = viewModel::refresh,
        )
        is ArtistScreenState.Loaded -> {
            val artist = current.artist
            val currentTrackId = (currentEntry as? QueueEntry.StreamingTrack)?.track?.id
            val topTrackIds = current.topTracks.map { it.id }.toSet()
            val playingTopTracks = currentTrackId != null && currentTrackId in topTrackIds
            val isPlaying = playingTopTracks && playerState == PlayerState.PLAYING
            val imageUrl = artist.thumbnails.bestUrl(1000)
            val headerColor = rememberDominantColor(imageUrl)

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                item(key = "header") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp)
                            .background(headerColor),
                    ) {
                        if (imageUrl != null) {
                            AsyncImage(
                                model = imageUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                alignment = Alignment.TopCenter,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))))
                        )
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(TvDimens.ContentPadding),
                        ) {
                            Text(
                                artist.name,
                                color = TvColors.TextPrimary,
                                fontSize = 72.sp,
                                lineHeight = 76.sp,
                                fontWeight = FontWeight.Black,
                                maxLines = 1,
                            )
                            artist.followersCount?.let { followers ->
                                Text(
                                    "${NumberFormat.getIntegerInstance().format(followers)} followers",
                                    color = TvColors.TextPrimary,
                                    fontSize = TvType.Body,
                                )
                            }
                        }
                    }
                }
                item(key = "actions") {
                    Row(
                        modifier = Modifier.padding(horizontal = TvDimens.ContentPadding),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        TvPlayButton(isPlaying = isPlaying, initialFocus = true, onClick = {
                            when {
                                isPlaying -> scope.launch { audioPlayer.pause() }
                                playingTopTracks -> scope.launch { audioPlayer.play() }
                                else -> viewModel.playTopTracks()
                            }
                        })
                        TvPillButton(
                            text = if (current.isArtistSaved) "Following" else "Follow",
                            primary = false,
                            onClick = viewModel::toggleSavedArtist,
                        )
                    }
                }
                if (current.topTracks.isNotEmpty()) {
                    item(key = "popular-title") {
                        TvSectionTitle(title = "Popular", modifier = Modifier.padding(horizontal = TvDimens.ContentPadding))
                    }
                    tvTrackRows(
                        tracks = current.topTracks.take(10),
                        currentTrackId = currentTrackId,
                        showAlbumColumn = false,
                        onTrackClick = { _, track -> viewModel.playTopTracksFromTrack(track) },
                    )
                }
                item(key = "discography") {
                    TvCardRow(
                        title = "Discography",
                        items = current.albums.map { it.toTvItem() },
                        onItemClick = { _, item -> item.route?.let(navigator::navigate) },
                    )
                }
                item(key = "playlists") {
                    TvCardRow(
                        title = "Featuring ${artist.name}",
                        items = current.featuredPlaylists.map { it.toTvItem() },
                        onItemClick = { _, item -> item.route?.let(navigator::navigate) },
                    )
                }
                item(key = "related") {
                    TvCardRow(
                        title = "Fans also like",
                        items = current.relatedArtists.map { it.toTvItem() },
                        onItemClick = { _, item -> item.route?.let(navigator::navigate) },
                    )
                }
                if (!artist.biography.isNullOrBlank()) {
                    item(key = "about") {
                        Column(
                            modifier = Modifier.padding(horizontal = TvDimens.ContentPadding),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            TvSectionTitle(title = "About")
                            Text(
                                artist.biography.orEmpty(),
                                color = TvColors.TextSecondary,
                                fontSize = TvType.Body,
                                maxLines = 6,
                            )
                        }
                    }
                }
            }
        }
    }
}
