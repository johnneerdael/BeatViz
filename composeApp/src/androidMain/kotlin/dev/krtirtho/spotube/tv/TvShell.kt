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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import dev.krtirtho.spotube.modules.plugin.PluginScreen
import dev.krtirtho.spotube.modules.settings.SettingsScreen
import dev.krtirtho.spotube.modules.shell.LocalAppShellBottomInset
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun TvShell(navigator: TvNavigator) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TvColors.Background)
            .padding(horizontal = TvDimens.Gap, vertical = TvDimens.Gap),
    ) {
        TvTopBar(navigator = navigator)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(TvDimens.Gap),
        ) {
            TvLibraryPanel(
                navigator = navigator,
                modifier = Modifier
                    .width(TvDimens.LibraryWidth)
                    .fillMaxHeight(),
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(TvPanelShape)
                    .background(TvColors.Panel),
            ) {
                // Stock screens reserve room for their own player; ours sits outside the panel.
                CompositionLocalProvider(LocalAppShellBottomInset provides 0.dp) {
                    TvMainContent(navigator = navigator)
                }
            }
        }
        TvPlayerBar(onOpenFullScreen = { navigator.isPlayerOpen = true })
    }
}

@Composable
private fun TvMainContent(navigator: TvNavigator) {
    when (val route = navigator.current) {
        TvRoute.Home -> TvHomeScreen(navigator = navigator)
        TvRoute.Search -> TvSearchScreen(navigator = navigator)
        TvRoute.LikedSongs -> TvLikedSongsScreen(navigator = navigator)
        is TvRoute.Playlist -> TvPlaylistScreen(playlistId = route.id, navigator = navigator)
        is TvRoute.Album -> TvAlbumScreen(albumId = route.id, navigator = navigator)
        is TvRoute.Artist -> TvArtistScreen(artistId = route.id, navigator = navigator)
        is TvRoute.BrowseSection -> TvBrowseSectionScreen(route = route, navigator = navigator)
        TvRoute.Settings -> SettingsScreen(settingsViewModel = koinViewModel())
        TvRoute.Plugins -> PluginScreen(viewModel = koinViewModel())
    }
}

@Composable
private fun TvTopBar(navigator: TvNavigator) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(TvDimens.TopBarHeight),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.weight(1f))
        TvIconButton(
            icon = TvIcons.Home,
            contentDescription = "Home",
            size = 44.dp,
            tint = if (navigator.current == TvRoute.Home) TvColors.TextPrimary else TvColors.TextSecondary,
            background = TvColors.PanelRaised,
            onClick = { navigator.navigate(TvRoute.Home) },
        )
        Row(
            modifier = Modifier
                .width(420.dp)
                .height(44.dp)
                .clip(TvPillShape)
                .background(TvColors.PanelRaised, TvPillShape)
                .tvFocusable(shape = TvPillShape, onClick = { navigator.navigate(TvRoute.Search) })
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(TvIcons.Search, contentDescription = null, tint = TvColors.TextSecondary, modifier = Modifier.size(20.dp))
            Text("What do you want to play?", color = TvColors.TextSecondary, fontSize = TvType.Body)
        }
        Spacer(Modifier.weight(1f))
        TvIconButton(
            icon = TvIcons.Settings,
            contentDescription = "Settings",
            onClick = { navigator.navigate(TvRoute.Settings) },
        )
    }
}
