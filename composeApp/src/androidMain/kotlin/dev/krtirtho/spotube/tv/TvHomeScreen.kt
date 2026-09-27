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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed as listItemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.krtirtho.plugin_interfaces.plugin_apis.metadata.browse.MetadataBrowseItem
import dev.krtirtho.plugin_interfaces.plugin_apis.metadata.common.PaginationStrategy
import dev.krtirtho.plugin_interfaces.plugin_apis.metadata.track.MetadataTrack
import dev.krtirtho.spotube.core.audioplayer.AudioPlayerQueue
import dev.krtirtho.spotube.core.audioplayer.QueueEntry
import dev.krtirtho.spotube.modules.home.HomeScreenRepository
import dev.krtirtho.spotube.modules.home.HomeScreenState
import dev.krtirtho.spotube.modules.home.HomeScreenViewModel
import dev.krtirtho.spotube.modules.plugin.PluginManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

private val RecentsTitle = Regex("recent|jump back in|recently played", RegexOption.IGNORE_CASE)

/** Plays [tracks] starting at [index] (used for track cards and search results). */
suspend fun AudioPlayerQueue.playTracks(tracks: List<MetadataTrack>, index: Int) {
    if (tracks.isEmpty()) return
    load(
        entries = tracks.map { QueueEntry.StreamingTrack(track = it, url = "") },
        autoPlay = true,
        startPosition = index.coerceIn(0, tracks.lastIndex),
    )
}

@Composable
fun TvHomeScreen(
    navigator: TvNavigator,
    viewModel: HomeScreenViewModel = koinViewModel(),
    pluginManager: PluginManager = koinInject(),
    audioPlayerQueue: AudioPlayerQueue = koinInject(),
) {
    val plugin by pluginManager.selectedMetadataPlugin.collectAsStateWithLifecycle()
    val loggedIn by remember(plugin) { plugin?.loggedInFlow ?: MutableStateFlow(false) }.collectAsState()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    when {
        plugin == null -> {
            TvMessage(
                title = "Connect your Spotify account",
                body = "Install the Spotify metadata plugin, then sign in to see your home feed.",
                actionLabel = "Open plugins",
                onAction = { navigator.navigate(TvRoute.Plugins) },
            )
            return
        }
        !loggedIn -> {
            TvMessage(
                title = "Sign in to Spotify",
                body = "Your home feed, library and recommendations appear after you sign in.",
                actionLabel = "Sign in",
                onAction = { navigator.navigate(TvRoute.Plugins) },
            )
            return
        }
    }

    when (val current = state) {
        HomeScreenState.Loading -> TvMessage(title = "Building your home…")
        is HomeScreenState.Error -> TvMessage(
            title = "Couldn't load your home feed",
            body = current.message,
            actionLabel = "Try again",
            onAction = viewModel::refresh,
        )
        is HomeScreenState.Data -> {
            val genreId = current.selectedGenreId
            val sections = genreId?.let { current.browseSections[it] }.orEmpty()
            val recents = sections.firstOrNull { RecentsTitle.containsMatchIn(it.title) }
            val listState = rememberLazyListState()
            val nearEnd by remember {
                derivedStateOf {
                    val info = listState.layoutInfo
                    val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
                    info.totalItemsCount > 0 && last >= info.totalItemsCount - 2
                }
            }
            LaunchedEffect(nearEnd, genreId) {
                val hasMore = genreId != null && current.paginationStrategies[genreId] != null
                if (nearEnd && hasMore && current is HomeScreenState.Data.Loaded) {
                    viewModel.loadMoreData()
                }
            }

            fun open(item: TvItem, rowTracks: List<MetadataTrack>, index: Int) {
                val route = item.route
                if (route != null) {
                    navigator.navigate(route)
                } else if (item.track != null) {
                    scope.launch { audioPlayerQueue.playTracks(rowTracks, rowTracks.indexOf(item.track).coerceAtLeast(0)) }
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(0f to TvColors.HeaderGradientTop, 0.35f to TvColors.Panel)),
                contentPadding = PaddingValues(top = 20.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(28.dp),
            ) {
                if (current.genres.size > 1) {
                    item(key = "genres") {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = TvDimens.ContentPadding),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(current.genres, key = { it.id }) { genre ->
                                TvChip(
                                    text = genre.name,
                                    selected = genre.id == genreId,
                                    onClick = { viewModel.selectGenre(genre.id) },
                                )
                            }
                        }
                    }
                }

                if (recents != null) {
                    item(key = "shortcuts") {
                        val tiles = recents.items.take(8).map { it.toTvItem() }
                        val tracks = recents.items.tracks()
                        Column(
                            modifier = Modifier.padding(horizontal = TvDimens.ContentPadding),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            tiles.chunked(4).forEach { rowTiles ->
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    rowTiles.forEachIndexed { i, tile ->
                                        TvShortcutTile(
                                            item = tile,
                                            modifier = Modifier.weight(1f),
                                            onClick = { open(tile, tracks, i) },
                                        )
                                    }
                                    repeat(4 - rowTiles.size) { Box(Modifier.weight(1f)) }
                                }
                            }
                        }
                    }
                }

                listItemsIndexed(sections, key = { index, section -> "section:$index:${section.title}" }) { _, section ->
                    val cards = section.items.map { it.toTvItem() }
                    val tracks = section.items.tracks()
                    TvCardRow(
                        title = section.title,
                        description = section.description,
                        items = cards,
                        onShowAll = section.moreLink?.let { link ->
                            {
                                if (genreId != null) {
                                    navigator.navigate(TvRoute.BrowseSection(genreId, link, section.title))
                                }
                            }
                        },
                        onItemClick = { index, item -> open(item, tracks, index) },
                    )
                }

                if (current is HomeScreenState.Data.LoadingMore) {
                    item(key = "loading-more") {
                        Text(
                            "Loading more…",
                            color = TvColors.TextSecondary,
                            modifier = Modifier.padding(horizontal = TvDimens.ContentPadding),
                        )
                    }
                }
            }
        }
    }
}

private fun List<MetadataBrowseItem>.tracks(): List<MetadataTrack> =
    filterIsInstance<MetadataBrowseItem.Track>().map { it.data }

/** "Show all" for one home-feed section, as a grid. */
@Composable
fun TvBrowseSectionScreen(
    route: TvRoute.BrowseSection,
    navigator: TvNavigator,
    repository: HomeScreenRepository = koinInject(),
    audioPlayerQueue: AudioPlayerQueue = koinInject(),
) {
    val items = remember(route) { mutableStateListOf<MetadataBrowseItem>() }
    var next by remember(route) { mutableStateOf<PaginationStrategy?>(null) }
    var isLoading by remember(route) { mutableStateOf(true) }
    var error by remember(route) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun load(pagination: PaginationStrategy?) {
        isLoading = true
        runCatching { repository.sublist(route.genreId, route.sectionId, pagination) }
            .onSuccess { result ->
                val newItems = result?.items.orEmpty()
                items.addAll(newItems)
                // Stop when a page adds nothing, some plugins keep returning a cursor.
                next = if (newItems.isEmpty()) null else result?.nextPagination
            }
            .onFailure { error = it.message ?: "Unknown error" }
        isLoading = false
    }

    LaunchedEffect(route) { load(null) }

    val gridState = rememberLazyGridState()
    val nearEnd by remember {
        derivedStateOf {
            val info = gridState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount > 0 && last >= info.totalItemsCount - 6
        }
    }
    LaunchedEffect(nearEnd, next, isLoading) {
        if (nearEnd && next != null && !isLoading) load(next)
    }

    if (items.isEmpty()) {
        when {
            error != null -> TvMessage(title = "Couldn't load ${route.title}", body = error)
            isLoading -> TvMessage(title = "Loading…")
            else -> TvMessage(title = "Nothing here yet")
        }
        return
    }

    val cards = items.map { it.toTvItem() }
    val tracks = items.tracks()
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = route.title,
            color = TvColors.TextPrimary,
            fontSize = TvType.SectionTitle,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = TvDimens.ContentPadding, top = 20.dp, bottom = 12.dp),
        )
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Adaptive(TvDimens.CardWidth),
            contentPadding = PaddingValues(horizontal = TvDimens.ContentPadding - 8.dp, vertical = 8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            itemsIndexed(cards, key = { index, item -> "${item.key}#$index" }) { _, item ->
                TvCard(item = item, onClick = {
                    val itemRoute = item.route
                    if (itemRoute != null) {
                        navigator.navigate(itemRoute)
                    } else if (item.track != null) {
                        scope.launch { audioPlayerQueue.playTracks(tracks, tracks.indexOf(item.track).coerceAtLeast(0)) }
                    }
                })
            }
        }
    }
}
