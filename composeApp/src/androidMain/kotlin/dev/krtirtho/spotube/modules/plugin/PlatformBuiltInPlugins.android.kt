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

package dev.krtirtho.spotube.modules.plugin

import app.cash.zipline.ZiplineService
import dev.krtirtho.spotube.core.webview.WebViewController
import dev.krtirtho.spotube.core.zipline.host_apis.RealCryptoAPI
import dev.krtirtho.spotube.core.zipline.host_apis.RealHttpClientAPI
import dev.krtirtho.spotube.core.zipline.host_apis.RealPersistedStorageAPI
import dev.krtirtho.spotube.core.zipline.host_apis.RealSystemInformationAPI
import dev.krtirtho.spotube.core.zipline.host_apis.RealWebViewAPI
import dev.krtirtho.spotube.spotify_builtin.SpotifyBuiltInPlugin
import kotlinx.coroutines.CoroutineScope
import org.koin.mp.KoinPlatform
import kotlin.reflect.KClass

internal actual fun platformBuiltInPlugins(): List<PluginEntry> = listOf(SPOTIFY_BUILT_IN_PLUGIN)

internal actual fun platformDefaultSelectedPlugins(): Map<PluginAbility, PluginEntry> =
    mapOf(PluginAbility.METADATA to SPOTIFY_BUILT_IN_PLUGIN)

internal actual fun platformBuiltInPluginServices(
    plugin: PluginEntry,
    scope: CoroutineScope,
): Map<KClass<*>, ZiplineService>? {
    if (plugin != SPOTIFY_BUILT_IN_PLUGIN) return null
    // Same host services a downloaded (Zipline) plugin gets, see ZiplinePluginService.
    val webViewController: WebViewController = KoinPlatform.getKoin().get()
    return SpotifyBuiltInPlugin.services(
        httpClient = RealHttpClientAPI(),
        webView = RealWebViewAPI(scope, webViewController, plugin.id),
        storage = RealPersistedStorageAPI(plugin),
        crypto = RealCryptoAPI(scope.coroutineContext),
        systemInfo = RealSystemInformationAPI(),
    )
}
