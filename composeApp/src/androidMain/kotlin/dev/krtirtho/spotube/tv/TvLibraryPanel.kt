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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import dev.krtirtho.spotube.modules.library.album.LibraryAlbumsState
import dev.krtirtho.spotube.modules.library.album.LibraryAlbumsViewModel
import dev.krtirtho.spotube.modules.library.artist.LibraryArtistsState
import dev.krtirtho.spotube.modules.library.artist.LibraryArtistsViewModel
import dev.krtirtho.spotube.modules.library.playlist.LibraryPlaylistsState
import dev.krtirtho.spotube.modules.library.playlist.LibraryPlaylistsViewModel
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

private enum class LibraryFilter(val label: String) {
    Playlists("Playlists"),
    Artists("Artists"),
    Albums("Albums"),
}

/** Left "Your Library" panel: the signed-in user's saved playlists, artists and albums. */
@Composable
fun TvLibraryPanel(
    navigator: TvNavigator,
    modifier: Modifier = Modifier,
    playlistsViewModel: LibraryPlaylistsViewModel = koinViewModel(),
    albumsViewModel: LibraryAlbumsViewModel = koinViewModel(),
    artistsViewModel: LibraryArtistsViewModel = koinViewModel(),
) {
    val playlistsState by playlistsViewModel.uiState.collectAsStateWithLifecycle()
    val albumsState by albumsViewModel.uiState.collectAsStateWithLifecycle()
    val artistsState by artistsViewModel.uiState.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableStateOf<LibraryFilter?>(null) }

    val playlists = (playlistsState as? LibraryPlaylistsState.Data)?.items.orEmpty().map { it.toLibraryItem() }
    val albums = (albumsState as? LibraryAlbumsState.Data)?.items.orEmpty().map { it.toTvItem() }
    val artists = (artistsState as? LibraryArtistsState.Data)?.items.orEmpty().map { it.toTvItem() }

    val items = when (filter) {
        null -> playlists + artists + albums
        LibraryFilter.Playlists -> playlists
        LibraryFilter.Artists -> artists
        LibraryFilter.Albums -> albums
    }
    val showLikedSongs = filter == null || filter == LibraryFilter.Playlists
    val isLoading = playlistsState is LibraryPlaylistsState.Loading &&
        albumsState is LibraryAlbumsState.Loading &&
        artistsState is LibraryArtistsState.Loading

    val listState = rememberLazyListState()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val nearEnd by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount > 0 && last >= info.totalItemsCount - 5
        }
    }
    LaunchedEffect(nearEnd, filter) {
        if (!nearEnd) return@LaunchedEffect
        scope.launch {
            if (filter == null || filter == LibraryFilter.Playlists) {
                if ((playlistsState as? LibraryPlaylistsState.Data.Loaded)?.nextPagination != null) {
                    playlistsViewModel.loadMoreData()
                }
            }
            if (filter == null || filter == LibraryFilter.Artists) {
                if ((artistsState as? LibraryArtistsState.Data.Loaded)?.nextPagination != null) {
                    artistsViewModel.loadMoreData()
                }
            }
            if (filter == null || filter == LibraryFilter.Albums) {
                if ((albumsState as? LibraryAlbumsState.Data.Loaded)?.nextPagination != null) {
                    albumsViewModel.loadMoreData()
                }
            }
        }
    }

    Column(
        modifier = modifier
            .clip(TvPanelShape)
            .background(TvColors.Panel),
    ) {
        Text(
            text = "Your Library",
            color = TvColors.TextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 12.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(LibraryFilter.entries) { entry ->
                TvChip(
                    text = entry.label,
                    selected = filter == entry,
                    onClick = { filter = if (filter == entry) null else entry },
                )
            }
        }
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.padding(top = 8.dp),
        ) {
            if (showLikedSongs) {
                item(key = "liked-songs") {
                    TvLikedSongsRow(
                        selected = navigator.current == TvRoute.LikedSongs,
                        onClick = { navigator.navigate(TvRoute.LikedSongs) },
                    )
                }
            }
            itemsIndexed(items, key = { index, item -> "${item.key}#$index" }) { _, item ->
                TvLibraryRow(
                    item = item,
                    selected = navigator.current == item.route,
                    onClick = { item.route?.let(navigator::navigate) },
                )
            }
            if (isLoading) {
                item(key = "loading") {
                    Text(
                        "Loading your library…",
                        color = TvColors.TextSecondary,
                        fontSize = TvType.Body,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun TvLibraryRow(item: TvItem, selected: Boolean, onClick: () -> Unit) {
    val actions = LocalTvActions.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(TvCardShape)
            .background(if (selected) TvColors.Highlight else Color.Transparent, TvCardShape)
            .tvFocusable(
                focusedBackground = TvColors.PanelRaised,
                onLongClick = { actions.show(item) },
                onClick = onClick,
            )
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TvArtwork(
            url = item.imageUrl,
            size = 48.dp,
            shape = artworkShape(item.circle),
            placeholder = if (item.circle) TvIcons.Artist else TvIcons.Music,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                item.title,
                color = TvColors.TextPrimary,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                item.subtitle,
                color = TvColors.TextSecondary,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun TvLikedSongsRow(selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(TvCardShape)
            .background(if (selected) TvColors.Highlight else Color.Transparent, TvCardShape)
            .tvFocusable(focusedBackground = TvColors.PanelRaised, onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(TvCardShape)
                .background(LikedSongsBrush),
            contentAlignment = Alignment.Center,
        ) {
            Icon(TvIcons.HeartFilled, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text("Liked Songs", color = TvColors.TextPrimary, fontSize = 15.sp, maxLines = 1)
            Text("Playlist", color = TvColors.TextSecondary, fontSize = 13.sp, maxLines = 1)
        }
    }
}

val LikedSongsBrush = Brush.linearGradient(listOf(Color(0xFF450AF5), Color(0xFFC4EFD9)))
