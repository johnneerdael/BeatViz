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

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.krtirtho.spotube.core.navigation.NavigationCommands
import dev.krtirtho.spotube.core.navigation.Routes
import dev.krtirtho.spotube.core.webview.WebViewController
import dev.krtirtho.spotube.tv.phone.PhoneAnyFieldBridge
import dev.krtirtho.spotube.tv.phone.PhoneWebViewBridge
import dev.krtirtho.spotube.core.ui.base.LocalBaseUITheme
import dev.krtirtho.spotube.core.ui.base.rememberBaseUITheme
import dev.krtirtho.spotube.core.ui.theming.SpotubeTheme
import dev.krtirtho.spotube.modules.settings.SettingsRepository
import dev.krtirtho.spotube.modules.settings.Theme
import dev.krtirtho.spotube.modules.settings.UserSettings
import dev.krtirtho.spotube.modules.webview.WebViewScreen
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun TvApp() {
    val settingsRepository: SettingsRepository = koinInject()
    val userSettings by settingsRepository.userSettings.collectAsStateWithLifecycle(initialValue = UserSettings())
    val navigationCommands: NavigationCommands = koinInject()
    val navigator = remember { TvNavigator() }
    val actions = remember { TvActionController() }
    var isWebViewOpen by remember { mutableStateOf(false) }

    // Upstream code (plugins, stock settings screens) navigates through
    // NavigationCommands; translate those into TV screens.
    LaunchedEffect(navigationCommands, navigator) {
        launch {
            navigationCommands.navigationCommandFlow.collect { route ->
                if (route == Routes.WebView) {
                    isWebViewOpen = true
                } else {
                    navigator.navigate(route)
                }
            }
        }
        launch {
            navigationCommands.navigationPopCommandFlow.collect { route ->
                when {
                    isWebViewOpen && (route == null || route == Routes.WebView) -> isWebViewOpen = false
                    route == Routes.WebView -> Unit
                    else -> navigator.pop()
                }
            }
        }
    }

    // The TV interface is always dark, whatever the phone/desktop theme setting says.
    SpotubeTheme(settings = userSettings.copy(theme = Theme.DARK)) {
        CompositionLocalProvider(LocalBaseUITheme provides rememberBaseUITheme()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(TvColors.Background)
            ) {
                CompositionLocalProvider(LocalTvActions provides actions) {
                    TvShell(navigator = navigator)
                }
                TvActionSheetHost(controller = actions, navigator = navigator)

                if (navigator.isPlayerOpen) {
                    TvFullScreenPlayer(onClose = { navigator.isPlayerOpen = false })
                }

                if (isWebViewOpen) {
                    val webViewController: WebViewController = koinInject()
                    WebViewScreen(webViewController)
                    PhoneWebViewBridge(webViewController)
                }
                // Any other focused text field can be typed from the phone too.
                PhoneAnyFieldBridge(enabled = !isWebViewOpen)
            }
        }
    }

    BackHandler(enabled = navigator.canPop || navigator.isPlayerOpen) {
        if (navigator.isPlayerOpen) {
            navigator.isPlayerOpen = false
        } else {
            navigator.pop()
        }
    }
    // Declared last so it wins while the sign-in page is open.
    BackHandler(enabled = isWebViewOpen) {
        navigationCommands.pop(Routes.WebView)
    }
}
