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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.krtirtho.plugin_interfaces.plugin_apis.metadata.browse.MetadataBrowseItem
import dev.krtirtho.plugin_interfaces.plugin_apis.metadata.track.MetadataTrack
import dev.krtirtho.spotube.core.audioplayer.AudioPlayerQueue
import dev.krtirtho.spotube.core.audioplayer.QueueEntry
import dev.krtirtho.spotube.modules.home.HomeScreenRepository
import dev.krtirtho.spotube.modules.home.HomeScreenState
import dev.krtirtho.spotube.modules.home.HomeScreenViewModel
import dev.krtirtho.spotube.modules.plugin.PluginManager
import io.github.sonic_liberation.spotube_plugin_spotify.core.RealMetadataBrowseAPI
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/** Genre ids for the Home feed ("All" and the web player's chips); others are browse categories. */
fun isHomeFilter(genreId: String) = genreId == RealMetadataBrowseAPI.HOME_GENRE ||
    genreId.startsWith("${RealMetadataBrowseAPI.HOME_GENRE}:")

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
            // Spotify is built in, so this only shows while plugins start up.
            TvMessage(title = "Starting…")
            return
        }
        !loggedIn -> {
            TvSignIn(onSignIn = {
                pluginManager.launchTask { plugin?.use { coreAPI.login() } }
            })
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
            val allSections = genreId?.let { current.browseSections[it] }.orEmpty()
            // The untitled shortcuts grid (marked by our plugin fork) renders as tiles, not a row.
            val shortcuts = allSections.firstOrNull { it.description == RealMetadataBrowseAPI.SHORTCUTS_MARKER }
            val sections = allSections.filter { it !== shortcuts }
            val homeFilters = current.genres.filter { isHomeFilter(it.id) }
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
                if (homeFilters.size > 1) {
                    item(key = "genres") {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = TvDimens.ContentPadding),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(homeFilters, key = { it.id }) { genre ->
                                TvChip(
                                    text = genre.name,
                                    selected = genre.id == genreId,
                                    onClick = { viewModel.selectGenre(genre.id) },
                                )
                            }
                        }
                    }
                }

                if (shortcuts != null) {
                    item(key = "shortcuts") {
                        val tiles = shortcuts.items.take(8).map { it.toTvItem() }
                        val tracks = shortcuts.items.tracks()
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
                                            initialFocus = tile === tiles.first(),
                                            onClick = { open(tile, tracks, i) },
                                        )
                                    }
                                    repeat(4 - rowTiles.size) { Box(Modifier.weight(1f)) }
                                }
                            }
                        }
                    }
                }

                listItemsIndexed(sections, key = { index, section -> "section:$index:${section.title}" }) { index, section ->
                    val cards = section.items.map { it.toTvItem() }
                    val tracks = section.items.tracks()
                    TvCardRow(
                        title = section.title,
                        description = section.description,
                        items = cards,
                        rowKey = "home/$index/${section.title}",
                        initialFocus = shortcuts == null && index == 0,
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

/** "Show all" for one home-feed or category section, as a grid. */
@Composable
fun TvBrowseSectionScreen(
    route: TvRoute.BrowseSection,
    navigator: TvNavigator,
    repository: HomeScreenRepository = koinInject(),
    audioPlayerQueue: AudioPlayerQueue = koinInject(),
) {
    val viewModel = viewModel(key = "browse:${route.genreId}:${route.sectionId}") {
        TvBrowseSectionViewModel(route.genreId, route.sectionId, repository)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    val gridState = rememberLazyGridState()
    val nearEnd by remember {
        derivedStateOf {
            val info = gridState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount > 0 && last >= info.totalItemsCount - 6
        }
    }
    LaunchedEffect(nearEnd, state.hasMore) {
        if (nearEnd) viewModel.loadMore()
    }

    if (state.items.isEmpty()) {
        when {
            state.error != null -> TvMessage(title = "Couldn't load ${route.title}", body = state.error)
            state.isLoading -> TvMessage(title = "Loading…")
            else -> TvMessage(title = "Nothing here yet")
        }
        return
    }

    val cards = state.items.map { it.toTvItem() }
    val tracks = state.items.tracks()
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
            itemsIndexed(cards, key = { index, item -> "${item.key}#$index" }) { index, item ->
                TvCard(
                    item = item,
                    focusKey = "grid/${item.key}#$index",
                    initialFocus = index == 0,
                    onClick = {
                        val itemRoute = item.route
                        if (itemRoute != null) {
                            navigator.navigate(itemRoute)
                        } else if (item.track != null) {
                            scope.launch { audioPlayerQueue.playTracks(tracks, tracks.indexOf(item.track).coerceAtLeast(0)) }
                        }
                    },
                )
            }
        }
    }
}

/** A Spotify browse category (from Search): its sections as rows, like the web player. */
@Composable
fun TvGenreScreen(
    route: TvRoute.Genre,
    navigator: TvNavigator,
    repository: HomeScreenRepository = koinInject(),
    audioPlayerQueue: AudioPlayerQueue = koinInject(),
) {
    val viewModel = viewModel(key = "genre:${route.id}") { TvGenreViewModel(route.id, repository) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val headerColor = rememberCategoryColor(route.name)

    val listState = rememberLazyListState()
    val nearEnd by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount > 0 && last >= info.totalItemsCount - 2
        }
    }
    LaunchedEffect(nearEnd, state.hasMore) {
        if (nearEnd) viewModel.loadMore()
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(0f to headerColor, 0.4f to TvColors.Panel)),
        contentPadding = PaddingValues(top = 40.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        item(key = "title") {
            Text(
                route.name,
                color = TvColors.TextPrimary,
                fontSize = TvType.PageTitle,
                fontWeight = FontWeight.Black,
                modifier = Modifier.padding(horizontal = TvDimens.ContentPadding),
            )
        }
        if (state.items.isEmpty()) {
            item(key = "status") {
                Text(
                    when {
                        state.error != null -> "Couldn't load ${route.name}: ${state.error}"
                        state.isLoading -> "Loading…"
                        else -> "Nothing here yet"
                    },
                    color = TvColors.TextSecondary,
                    modifier = Modifier.padding(horizontal = TvDimens.ContentPadding),
                )
            }
        }
        listItemsIndexed(state.items, key = { index, section -> "genre-section:$index:${section.title}" }) { index, section ->
            val tracks = section.items.tracks()
            TvCardRow(
                title = section.title,
                description = section.description,
                items = section.items.map { it.toTvItem() },
                rowKey = "genre/$index/${section.title}",
                initialFocus = index == 0,
                onShowAll = section.moreLink?.let { link ->
                    { navigator.navigate(TvRoute.BrowseSection(route.id, link, section.title)) }
                },
                onItemClick = { _, item ->
                    val itemRoute = item.route
                    if (itemRoute != null) {
                        navigator.navigate(itemRoute)
                    } else if (item.track != null) {
                        scope.launch { audioPlayerQueue.playTracks(tracks, tracks.indexOf(item.track).coerceAtLeast(0)) }
                    }
                },
            )
        }
    }
}

/** Stable, saturated colour per category name (the web player colours its category tiles too). */
fun categoryColor(name: String): Color {
    val hue = ((name.hashCode() % 360) + 360) % 360
    return Color.hsl(hue.toFloat(), saturation = 0.55f, lightness = 0.38f)
}

@Composable
fun rememberCategoryColor(name: String): Color = remember(name) { categoryColor(name) }
