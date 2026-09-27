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

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.krtirtho.spotube.core.navigation.Routes

/** Screens of the TV interface (the main panel's content). */
sealed interface TvRoute {
    data object Home : TvRoute
    data object Search : TvRoute
    data object LikedSongs : TvRoute
    data object Settings : TvRoute
    data object Plugins : TvRoute
    data class Playlist(val id: String) : TvRoute
    data class Album(val id: String) : TvRoute
    data class Artist(val id: String) : TvRoute
    data class BrowseSection(val genreId: String, val sectionId: String, val title: String) : TvRoute
}

@Stable
class TvNavigator {
    private val stack = mutableStateListOf<TvRoute>(TvRoute.Home)

    val current: TvRoute get() = stack.last()
    val canPop: Boolean get() = stack.size > 1

    /** Full-screen player shown on top of everything. */
    var isPlayerOpen by mutableStateOf(false)

    fun navigate(route: TvRoute) {
        if (route == current) return
        // Top-level destinations reset the stack, like the web player's Home button.
        if (route == TvRoute.Home) {
            stack.clear()
            stack.add(TvRoute.Home)
            return
        }
        stack.add(route)
    }

    fun pop(): Boolean {
        if (!canPop) return false
        stack.removeAt(stack.lastIndex)
        return true
    }

    /** Maps upstream routes (sent through NavigationCommands) to TV screens. */
    fun navigate(route: Routes) {
        when (route) {
            Routes.Home -> navigate(TvRoute.Home)
            Routes.Search -> navigate(TvRoute.Search)
            Routes.Settings -> navigate(TvRoute.Settings)
            Routes.Plugins -> navigate(TvRoute.Plugins)
            Routes.SavedTracks -> navigate(TvRoute.LikedSongs)
            is Routes.Playlist -> navigate(TvRoute.Playlist(route.playlistId))
            is Routes.Album -> navigate(TvRoute.Album(route.albumId))
            is Routes.Artist -> navigate(TvRoute.Artist(route.artistId))
            // Library lives in the side panel; lyrics/devices/jam are not part of the TV app.
            else -> Unit
        }
    }
}
