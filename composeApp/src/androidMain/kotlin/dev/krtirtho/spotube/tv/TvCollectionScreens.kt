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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.krtirtho.plugin_interfaces.plugin_apis.metadata.album.MetadataAlbumType
import dev.krtirtho.plugin_interfaces.plugin_apis.metadata.track.MetadataTrack
import dev.krtirtho.spotube.core.audioplayer.AudioPlayerInterface
import dev.krtirtho.spotube.core.audioplayer.AudioPlayerQueue
import dev.krtirtho.spotube.core.audioplayer.PlayerState
import dev.krtirtho.spotube.core.audioplayer.QueueEntry
import dev.krtirtho.spotube.modules.album.AlbumScreenState
import dev.krtirtho.spotube.modules.album.AlbumViewModel
import dev.krtirtho.spotube.modules.playlist.PlaylistScreenState
import dev.krtirtho.spotube.modules.playlist.PlaylistViewModel
import dev.krtirtho.spotube.modules.saved_tracks.SAVED_TRACKS_COLLECTION_ID
import dev.krtirtho.spotube.modules.saved_tracks.SavedTracksScreenState
import dev.krtirtho.spotube.modules.saved_tracks.SavedTracksViewModel
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun TvPlaylistScreen(
    playlistId: String,
    navigator: TvNavigator,
    viewModel: PlaylistViewModel = koinViewModel(key = playlistId, parameters = { parametersOf(playlistId) }),
    audioPlayerQueue: AudioPlayerQueue = koinInject(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val collection by audioPlayerQueue.currentCollectionEntryFlow.collectAsStateWithLifecycle()
    when (val current = state) {
        PlaylistScreenState.Loading -> TvMessage(title = "Loading playlist…")
        is PlaylistScreenState.Error -> TvMessage(
            title = "Couldn't load this playlist",
            body = current.message,
            actionLabel = "Try again",
            onAction = viewModel::refresh,
        )
        is PlaylistScreenState.Data -> {
            val playlist = current.playlist
            val owner = playlist?.owner?.let { it.displayName ?: it.username }
            TvCollectionPage(
                typeLabel = "Playlist",
                title = playlist?.title.orEmpty(),
                description = playlist?.description,
                meta = listOfNotNull(owner, playlist?.trackCount?.let { "$it songs" }).joinToString(" • "),
                imageUrl = playlist?.thumbnails.bestUrl(600),
                tracks = current.tracks,
                isSaved = current.isSaved,
                isThisCollectionQueued = collection.let { audioPlayerQueue.isPlaylistPlaying(playlistId) },
                hasMore = current.nextPagination != null && current is PlaylistScreenState.Data.Loaded,
                showAlbumColumn = true,
                onLoadMore = viewModel::loadNextTracksPage,
                onPlay = viewModel::playPlaylist,
                onShuffle = viewModel::shufflePlayPlaylist,
                onToggleSaved = viewModel::toggleSavedPlaylist,
                onTrackClick = viewModel::playPlaylistFromTrack,
                navigator = navigator,
            )
        }
    }
}

@Composable
fun TvAlbumScreen(
    albumId: String,
    navigator: TvNavigator,
    viewModel: AlbumViewModel = koinViewModel(key = albumId, parameters = { parametersOf(albumId) }),
    audioPlayerQueue: AudioPlayerQueue = koinInject(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val collection by audioPlayerQueue.currentCollectionEntryFlow.collectAsStateWithLifecycle()
    when (val current = state) {
        AlbumScreenState.Loading -> TvMessage(title = "Loading album…")
        is AlbumScreenState.Error -> TvMessage(
            title = "Couldn't load this album",
            body = current.message,
            actionLabel = "Try again",
            onAction = viewModel::refresh,
        )
        is AlbumScreenState.Data -> {
            val album = current.album
            val type = when (album?.albumType) {
                MetadataAlbumType.Single -> "Single"
                MetadataAlbumType.Collection -> "Compilation"
                else -> "Album"
            }
            TvCollectionPage(
                typeLabel = type,
                title = album?.title.orEmpty(),
                description = null,
                meta = listOfNotNull(
                    album?.artists?.joinToString(", ") { it.name },
                    album?.releaseDate?.take(4),
                    album?.trackCount?.let { "$it songs" },
                ).filter { it.isNotBlank() }.joinToString(" • "),
                imageUrl = album?.thumbnails.bestUrl(600),
                tracks = current.tracks,
                isSaved = current.isSaved,
                isThisCollectionQueued = collection.let { audioPlayerQueue.isAlbumPlaying(albumId) },
                hasMore = current.nextPagination != null && current is AlbumScreenState.Data.Loaded,
                showAlbumColumn = false,
                onLoadMore = viewModel::loadNextTracksPage,
                onPlay = viewModel::playAlbum,
                onShuffle = viewModel::shufflePlayAlbum,
                onToggleSaved = viewModel::toggleSavedAlbum,
                onTrackClick = viewModel::playAlbumFromTrack,
                navigator = navigator,
            )
        }
    }
}

@Composable
fun TvLikedSongsScreen(
    navigator: TvNavigator,
    viewModel: SavedTracksViewModel = koinViewModel(
        key = SAVED_TRACKS_COLLECTION_ID,
        parameters = { parametersOf() },
    ),
    audioPlayerQueue: AudioPlayerQueue = koinInject(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val collection by audioPlayerQueue.currentCollectionEntryFlow.collectAsStateWithLifecycle()
    when (val current = state) {
        SavedTracksScreenState.Loading -> TvMessage(title = "Loading your liked songs…")
        is SavedTracksScreenState.Error -> TvMessage(
            title = "Couldn't load your liked songs",
            body = current.message,
            actionLabel = "Try again",
            onAction = viewModel::refresh,
        )
        is SavedTracksScreenState.Data -> TvCollectionPage(
            typeLabel = "Playlist",
            title = "Liked Songs",
            description = null,
            meta = "${current.totalCount} songs",
            imageUrl = null,
            headerBrush = LikedSongsBrush,
            tracks = current.tracks,
            isSaved = null,
            isThisCollectionQueued = collection.let { audioPlayerQueue.isSavedTracksPlaying() },
            hasMore = current.nextPagination != null && current is SavedTracksScreenState.Data.Loaded,
            showAlbumColumn = true,
            onLoadMore = viewModel::loadNextTracksPage,
            onPlay = viewModel::playSavedTracks,
            onShuffle = viewModel::shufflePlaySavedTracks,
            onToggleSaved = {},
            onTrackClick = viewModel::playSavedTracksFromTrack,
            navigator = navigator,
        )
    }
}

/**
 * Web-player style collection page: colour-matched header with the big cover,
 * play/shuffle/save actions, then the numbered track list.
 */
@Composable
private fun TvCollectionPage(
    typeLabel: String,
    title: String,
    description: String?,
    meta: String,
    imageUrl: String?,
    tracks: List<MetadataTrack>,
    isSaved: Boolean?,
    isThisCollectionQueued: Boolean,
    hasMore: Boolean,
    showAlbumColumn: Boolean,
    onLoadMore: () -> Unit,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    onToggleSaved: () -> Unit,
    onTrackClick: (MetadataTrack) -> Unit,
    navigator: TvNavigator,
    headerBrush: Brush? = null,
    audioPlayer: AudioPlayerInterface = koinInject(),
    audioPlayerQueue: AudioPlayerQueue = koinInject(),
) {
    val scope = rememberCoroutineScope()
    val playerState by audioPlayer.playerStateFlow.collectAsStateWithLifecycle()
    val currentEntry by audioPlayerQueue.currentQueueEntryFlow.collectAsStateWithLifecycle()
    val currentTrackId = (currentEntry as? QueueEntry.StreamingTrack)?.track?.id
    val isPlaying = isThisCollectionQueued && playerState == PlayerState.PLAYING
    val headerColor = rememberDominantColor(imageUrl)

    val listState = rememberLazyListState()
    val nearEnd by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount > 0 && last >= info.totalItemsCount - 8
        }
    }
    LaunchedEffect(nearEnd, hasMore) {
        if (nearEnd && hasMore) onLoadMore()
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp),
    ) {
        item(key = "header") {
            TvCollectionHeader(
                typeLabel = typeLabel,
                title = title,
                description = description,
                meta = meta,
                imageUrl = imageUrl,
                background = headerBrush ?: Brush.verticalGradient(listOf(headerColor, headerColor.copy(alpha = 0.6f))),
                coverBrush = headerBrush,
            )
        }
        item(key = "actions") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(headerColor.copy(alpha = 0.45f), Color.Transparent)))
                    .padding(horizontal = TvDimens.ContentPadding, vertical = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                TvPlayButton(isPlaying = isPlaying, initialFocus = true, onClick = {
                    when {
                        isPlaying -> scope.launch { audioPlayer.pause() }
                        isThisCollectionQueued -> scope.launch { audioPlayer.play() }
                        else -> onPlay()
                    }
                })
                TvIconButton(icon = TvIcons.Shuffle, contentDescription = "Shuffle play", size = 48.dp, iconSize = 26.dp, onClick = onShuffle)
                if (isSaved != null) {
                    TvIconButton(
                        icon = if (isSaved) TvIcons.HeartFilled else TvIcons.Heart,
                        contentDescription = if (isSaved) "Remove from Your Library" else "Save to Your Library",
                        size = 48.dp,
                        iconSize = 26.dp,
                        tint = if (isSaved) TvColors.Accent else TvColors.TextSecondary,
                        onClick = onToggleSaved,
                    )
                }
            }
        }
        item(key = "columns") {
            TvTrackColumnsHeader(showAlbumColumn = showAlbumColumn)
        }
        tvTrackRows(
            tracks = tracks,
            currentTrackId = currentTrackId,
            showAlbumColumn = showAlbumColumn,
            onTrackClick = { _, track -> onTrackClick(track) },
        )
    }
}

@Composable
private fun TvCollectionHeader(
    typeLabel: String,
    title: String,
    description: String?,
    meta: String,
    imageUrl: String?,
    background: Brush,
    coverBrush: Brush?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(background)
            .padding(start = TvDimens.ContentPadding, end = TvDimens.ContentPadding, top = 40.dp, bottom = 24.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        if (coverBrush != null) {
            Box(
                modifier = Modifier
                    .size(200.dp)
                    .clip(TvCardShape)
                    .background(coverBrush),
                contentAlignment = Alignment.Center,
            ) {
                Icon(TvIcons.HeartFilled, contentDescription = null, tint = Color.White, modifier = Modifier.size(72.dp))
            }
        } else {
            TvArtwork(url = imageUrl, size = 200.dp)
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(typeLabel, color = TvColors.TextPrimary, fontSize = TvType.Body)
            Text(
                title,
                color = TvColors.TextPrimary,
                fontSize = if (title.length > 24) 36.sp else TvType.PageTitle,
                lineHeight = if (title.length > 24) 40.sp else 60.sp,
                fontWeight = FontWeight.Black,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (!description.isNullOrBlank()) {
                Text(description, color = TvColors.TextSecondary, fontSize = TvType.Body, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (meta.isNotBlank()) {
                Text(meta, color = TvColors.TextPrimary, fontSize = TvType.Body, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
fun TvTrackColumnsHeader(showAlbumColumn: Boolean) {
    Column(modifier = Modifier.padding(horizontal = TvDimens.ContentPadding)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("#", color = TvColors.TextSecondary, fontSize = TvType.Body, modifier = Modifier.width(36.dp))
            Text("Title", color = TvColors.TextSecondary, fontSize = TvType.Body, modifier = Modifier.weight(1f))
            if (showAlbumColumn) {
                Text("Album", color = TvColors.TextSecondary, fontSize = TvType.Body, modifier = Modifier.weight(0.7f))
            }
            Text("Time", color = TvColors.TextSecondary, fontSize = TvType.Body, modifier = Modifier.width(56.dp))
        }
        Box(Modifier.padding(bottom = 8.dp).fillMaxWidth().height(1.dp).background(TvColors.Highlight))
    }
}

/** Numbered track rows; OK plays from that track. */
fun LazyListScope.tvTrackRows(
    tracks: List<MetadataTrack>,
    currentTrackId: String?,
    showAlbumColumn: Boolean,
    showArtwork: Boolean = true,
    onTrackClick: (Int, MetadataTrack) -> Unit,
) {
    itemsIndexed(tracks, key = { index, track -> "track:${track.id}#$index" }) { index, track ->
        TvTrackRow(
            index = index,
            track = track,
            isCurrent = track.id == currentTrackId,
            showAlbumColumn = showAlbumColumn,
            showArtwork = showArtwork,
            onClick = { onTrackClick(index, track) },
        )
    }
}

@Composable
fun TvTrackRow(
    index: Int,
    track: MetadataTrack,
    isCurrent: Boolean,
    showAlbumColumn: Boolean,
    showArtwork: Boolean,
    onClick: () -> Unit,
) {
    val titleColor = if (isCurrent) TvColors.Accent else TvColors.TextPrimary
    val actions = LocalTvActions.current
    Row(
        modifier = Modifier
            .padding(horizontal = TvDimens.ContentPadding)
            .fillMaxWidth()
            .clip(TvCardShape)
            .tvFocusable(
                focusedBackground = TvColors.Highlight,
                focusKey = "track/${track.id}#$index",
                onLongClick = { actions.show(track.toTvItem()) },
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "${index + 1}",
            color = if (isCurrent) TvColors.Accent else TvColors.TextSecondary,
            fontSize = TvType.Body,
            modifier = Modifier.width(36.dp),
        )
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (showArtwork) {
                TvArtwork(url = (track.album?.thumbnails ?: track.thumbnails).bestUrl(100), size = 40.dp, shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
            }
            Column {
                Text(track.title, color = titleColor, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(if (track.explicit == true) "E" else null, track.artists.joinToString(", ") { it.name }).joinToString("  "),
                    color = TvColors.TextSecondary,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (showAlbumColumn) {
            Text(
                track.album?.title.orEmpty(),
                color = TvColors.TextSecondary,
                fontSize = TvType.Body,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(0.7f).padding(end = 12.dp),
            )
        }
        Text(formatDuration(track.durationMs), color = TvColors.TextSecondary, fontSize = TvType.Body, modifier = Modifier.width(56.dp))
    }
}
