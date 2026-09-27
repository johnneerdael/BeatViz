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
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.krtirtho.plugin_interfaces.plugin_apis.metadata.search.MetadataSupportedSearchType
import dev.krtirtho.spotube.core.audioplayer.AudioPlayerQueue
import dev.krtirtho.spotube.core.audioplayer.QueueEntry
import dev.krtirtho.spotube.modules.home.HomeScreenState
import dev.krtirtho.spotube.modules.home.HomeScreenViewModel
import dev.krtirtho.spotube.modules.search.SearchScreenViewModel
import dev.krtirtho.spotube.tv.phone.PhoneField
import dev.krtirtho.spotube.tv.phone.PhoneFieldBinding
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

private fun MetadataSupportedSearchType.label() = when (this) {
    MetadataSupportedSearchType.ALL -> "All"
    MetadataSupportedSearchType.TRACK -> "Songs"
    MetadataSupportedSearchType.ARTIST -> "Artists"
    MetadataSupportedSearchType.ALBUM -> "Albums"
    MetadataSupportedSearchType.PLAYLIST -> "Playlists"
    MetadataSupportedSearchType.USER -> "Profiles"
}

@Composable
fun TvSearchScreen(
    navigator: TvNavigator,
    viewModel: SearchScreenViewModel = koinViewModel(),
    homeViewModel: HomeScreenViewModel = koinViewModel(),
    audioPlayerQueue: AudioPlayerQueue = koinInject(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val homeState by homeViewModel.uiState.collectAsStateWithLifecycle()
    // Spotify's browse categories (the web player's "Browse all" on Search).
    val categories = (homeState as? HomeScreenState.Data)?.genres.orEmpty().filterNot { isHomeFilter(it.id) }
    val currentEntry by audioPlayerQueue.currentQueueEntryFlow.collectAsStateWithLifecycle()
    val currentTrackId = (currentEntry as? QueueEntry.StreamingTrack)?.track?.id
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }

    val type = state.selectedSearchType ?: MetadataSupportedSearchType.ALL
    val showTracks = type == MetadataSupportedSearchType.ALL || type == MetadataSupportedSearchType.TRACK
    val showRow = { t: MetadataSupportedSearchType -> type == MetadataSupportedSearchType.ALL || type == t }
    val tracks = state.tracks.items

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 20.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item(key = "field") {
            TvSearchField(
                query = state.query,
                onQueryChange = viewModel::onQueryChange,
                onSearch = { keyboard?.hide() },
                focusRequester = focusRequester,
                modifier = Modifier.padding(horizontal = TvDimens.ContentPadding),
            )
        }

        if (state.query.isBlank()) {
            if (state.recentSearches.isNotEmpty()) {
                item(key = "recent-title") {
                    TvSectionTitle(title = "Recent searches", modifier = Modifier.padding(horizontal = TvDimens.ContentPadding))
                }
                item(key = "recent") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = TvDimens.ContentPadding),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(state.recentSearches) { recent ->
                            TvChip(text = recent, selected = false, onClick = { viewModel.applyRecentSearch(recent) })
                        }
                    }
                }
            }
            if (categories.isNotEmpty()) {
                item(key = "browse-title") {
                    TvSectionTitle(title = "Browse all", modifier = Modifier.padding(horizontal = TvDimens.ContentPadding))
                }
                categories.chunked(4).forEachIndexed { rowIndex, row ->
                    item(key = "browse-row-$rowIndex") {
                        Row(
                            modifier = Modifier.padding(horizontal = TvDimens.ContentPadding),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            row.forEach { genre ->
                                TvCategoryTile(
                                    name = genre.name,
                                    modifier = Modifier.weight(1f),
                                    onClick = { navigator.navigate(TvRoute.Genre(genre.id, genre.name)) },
                                )
                            }
                            repeat(4 - row.size) { Box(Modifier.weight(1f)) }
                        }
                    }
                }
            }
            return@LazyColumn
        }

        if (state.supportedSearchTypes.size > 1) {
            item(key = "types") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = TvDimens.ContentPadding),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.supportedSearchTypes) { searchType ->
                        TvChip(
                            text = searchType.label(),
                            selected = searchType == type,
                            onClick = { viewModel.onTabSelected(searchType) },
                        )
                    }
                }
            }
        }

        if (showTracks && tracks.isNotEmpty()) {
            item(key = "songs-title") {
                TvSectionTitle(title = "Songs", modifier = Modifier.padding(horizontal = TvDimens.ContentPadding))
            }
            tvTrackRows(
                tracks = if (type == MetadataSupportedSearchType.ALL) tracks.take(5) else tracks,
                currentTrackId = currentTrackId,
                showAlbumColumn = true,
                onTrackClick = { index, _ -> scope.launch { audioPlayerQueue.playTracks(tracks, index) } },
            )
            if (type == MetadataSupportedSearchType.TRACK && state.tracks.hasNextPage) {
                item(key = "more-songs") {
                    Box(Modifier.padding(horizontal = TvDimens.ContentPadding)) {
                        TvPillButton(text = "Load more", primary = false, onClick = viewModel::loadNextTracks)
                    }
                }
            }
        }
        if (showRow(MetadataSupportedSearchType.ARTIST)) {
            item(key = "artists") {
                TvCardRow(
                    title = "Artists",
                    items = state.artists.items.map { it.toTvItem() },
                    onItemClick = { _, item -> item.route?.let(navigator::navigate) },
                )
            }
        }
        if (showRow(MetadataSupportedSearchType.ALBUM)) {
            item(key = "albums") {
                TvCardRow(
                    title = "Albums",
                    items = state.albums.items.map { it.toTvItem() },
                    onItemClick = { _, item -> item.route?.let(navigator::navigate) },
                )
            }
        }
        if (showRow(MetadataSupportedSearchType.PLAYLIST)) {
            item(key = "playlists") {
                TvCardRow(
                    title = "Playlists",
                    items = state.playlists.items.map { it.toTvItem() },
                    onItemClick = { _, item -> item.route?.let(navigator::navigate) },
                )
            }
        }
        val isLoading = state.tracks.isLoading || state.artists.isLoading || state.albums.isLoading || state.playlists.isLoading
        val nothingFound = !isLoading && tracks.isEmpty() && state.artists.items.isEmpty() &&
            state.albums.items.isEmpty() && state.playlists.items.isEmpty()
        if (isLoading || nothingFound) {
            item(key = "status") {
                Text(
                    if (isLoading) "Searching…" else "No results for \"${state.query}\"",
                    color = TvColors.TextSecondary,
                    fontSize = TvType.Body,
                    modifier = Modifier.padding(horizontal = TvDimens.ContentPadding),
                )
            }
        }
    }
}

@Composable
private fun TvCategoryTile(name: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .height(96.dp)
            .clip(TvCardShape)
            .background(rememberCategoryColor(name), TvCardShape)
            .tvFocusable(focusedScale = 1.04f, focusKey = "category/$name", onClick = onClick)
            .padding(14.dp),
    ) {
        Text(name, color = TvColors.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 2)
    }
}

@Composable
private fun TvSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    // With the phone keyboard on, the search box is mirrored on the phone while focused.
    PhoneFieldBinding(
        active = focused,
        field = PhoneField(label = "Search", hint = "What do you want to play?", action = "search"),
        value = query,
        onText = onQueryChange,
        onSubmit = onSearch,
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(TvPillShape)
            .background(TvColors.PanelRaised, TvPillShape)
            .border(TvDimens.FocusBorder, if (focused) TvColors.Focus else Color.Transparent, TvPillShape)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(TvIcons.Search, contentDescription = null, tint = TvColors.TextSecondary, modifier = Modifier.size(22.dp))
        Box(modifier = Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text("What do you want to play?", color = TvColors.TextSecondary, fontSize = 16.sp)
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                interactionSource = interactionSource,
                textStyle = TextStyle(color = TvColors.TextPrimary, fontSize = 16.sp),
                cursorBrush = SolidColor(TvColors.TextPrimary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
            )
        }
    }
}
