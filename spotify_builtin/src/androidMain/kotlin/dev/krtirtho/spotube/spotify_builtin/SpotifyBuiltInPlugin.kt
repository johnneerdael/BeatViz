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

package dev.krtirtho.spotube.spotify_builtin

import app.cash.zipline.ZiplineService
import dev.krtirtho.plugin_interfaces.host_apis.CryptoAPI
import dev.krtirtho.plugin_interfaces.host_apis.HttpClientAPI
import dev.krtirtho.plugin_interfaces.host_apis.PersistedStorageAPI
import dev.krtirtho.plugin_interfaces.host_apis.SystemInformationAPI
import dev.krtirtho.plugin_interfaces.host_apis.WebViewAPI
import dev.krtirtho.plugin_interfaces.plugin_apis.core.CoreAPI
import dev.krtirtho.plugin_interfaces.plugin_apis.metadata.album.MetadataAlbumAPI
import dev.krtirtho.plugin_interfaces.plugin_apis.metadata.artist.MetadataArtistAPI
import dev.krtirtho.plugin_interfaces.plugin_apis.metadata.browse.MetadataBrowseAPI
import dev.krtirtho.plugin_interfaces.plugin_apis.metadata.playlist.MetadataPlaylistAPI
import dev.krtirtho.plugin_interfaces.plugin_apis.metadata.search.MetadataSearchAPI
import dev.krtirtho.plugin_interfaces.plugin_apis.metadata.track.MetadataTrackAPI
import dev.krtirtho.plugin_interfaces.plugin_apis.metadata.user.MetadataUserAPI
import io.github.sonic_liberation.spotify_gql_client.gql.SpotifyGQLClient
import io.github.sonic_liberation.spotube_plugin_spotify.core.RealMetadataAlbumAPI
import io.github.sonic_liberation.spotube_plugin_spotify.core.RealMetadataArtistAPI
import io.github.sonic_liberation.spotube_plugin_spotify.core.RealMetadataBrowseAPI
import io.github.sonic_liberation.spotube_plugin_spotify.core.RealMetadataPlaylistAPI
import io.github.sonic_liberation.spotube_plugin_spotify.core.RealMetadataSearchAPI
import io.github.sonic_liberation.spotube_plugin_spotify.core.RealMetadataTrackAPI
import io.github.sonic_liberation.spotube_plugin_spotify.core.RealMetadataUserAPI
import io.github.sonic_liberation.spotube_plugin_spotify.services.TOTP
import kotlin.reflect.KClass

/**
 * Wires the Spotify plugin in-process, the way the plugin's JS entry point
 * (spotube_plugin_spotify/src/jsMain/.../js.kt) does it through Zipline.
 */
object SpotifyBuiltInPlugin {
    fun services(
        httpClient: HttpClientAPI,
        webView: WebViewAPI,
        storage: PersistedStorageAPI,
        crypto: CryptoAPI,
        systemInfo: SystemInformationAPI,
    ): Map<KClass<*>, ZiplineService> {
        val gql = SpotifyGQLClient(httpClient, systemInfo)
        return mapOf(
            CoreAPI::class to SpotifyCoreAPI(
                httpClient = httpClient,
                totp = TOTP(crypto),
                storage = storage,
                webView = webView,
            ),
            MetadataAlbumAPI::class to RealMetadataAlbumAPI(gql),
            MetadataArtistAPI::class to RealMetadataArtistAPI(gql),
            MetadataBrowseAPI::class to RealMetadataBrowseAPI(gql),
            MetadataPlaylistAPI::class to RealMetadataPlaylistAPI(gql),
            MetadataSearchAPI::class to RealMetadataSearchAPI(gql),
            MetadataTrackAPI::class to RealMetadataTrackAPI(gql),
            MetadataUserAPI::class to RealMetadataUserAPI(gql),
        )
    }
}
