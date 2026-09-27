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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.krtirtho.plugin_interfaces.plugin_apis.metadata.browse.MetadataBrowseItem
import dev.krtirtho.plugin_interfaces.plugin_apis.metadata.browse.MetadataBrowseSection
import dev.krtirtho.plugin_interfaces.plugin_apis.metadata.common.PaginationStrategy
import dev.krtirtho.spotube.modules.home.HomeScreenRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Paged list state shared by the "Show all" grid and the category page. */
data class TvPagedState<T>(
    val items: List<T> = emptyList(),
    val next: PaginationStrategy? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
) {
    val hasMore: Boolean get() = next != null && !isLoading
}

/** Items of one home-feed/category section ("Show all"). */
class TvBrowseSectionViewModel(
    private val genreId: String,
    private val sectionId: String,
    private val repository: HomeScreenRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(TvPagedState<MetadataBrowseItem>())
    val state: StateFlow<TvPagedState<MetadataBrowseItem>> = _state.asStateFlow()

    init {
        load(null)
    }

    fun loadMore() {
        val current = _state.value
        if (current.hasMore) load(current.next)
    }

    private fun load(pagination: PaginationStrategy?) {
        _state.value = _state.value.copy(isLoading = true)
        viewModelScope.launch {
            runCatching { repository.sublist(genreId, sectionId, pagination) }
                .onSuccess { result ->
                    val newItems = result?.items.orEmpty()
                    _state.value = _state.value.copy(
                        items = _state.value.items + newItems,
                        // Stop when a page adds nothing; some plugins keep returning a cursor.
                        next = if (newItems.isEmpty()) null else result?.nextPagination,
                        isLoading = false,
                        error = null,
                    )
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(isLoading = false, error = e.message ?: "Unknown error")
                }
        }
    }
}

/** Sections of a Spotify browse category page (Pop, Hip-Hop, ...). */
class TvGenreViewModel(
    private val genreId: String,
    private val repository: HomeScreenRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(TvPagedState<MetadataBrowseSection>())
    val state: StateFlow<TvPagedState<MetadataBrowseSection>> = _state.asStateFlow()

    init {
        load(null)
    }

    fun loadMore() {
        val current = _state.value
        if (current.hasMore) load(current.next)
    }

    private fun load(pagination: PaginationStrategy?) {
        _state.value = _state.value.copy(isLoading = true)
        viewModelScope.launch {
            runCatching { repository.list(genreId, pagination) }
                .onSuccess { result ->
                    val newSections = result?.items.orEmpty()
                    _state.value = _state.value.copy(
                        items = _state.value.items + newSections,
                        next = if (newSections.isEmpty()) null else result?.nextPagination,
                        isLoading = false,
                        error = null,
                    )
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(isLoading = false, error = e.message ?: "Unknown error")
                }
        }
    }
}
