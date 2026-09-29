/*
 * Copyright (C) 2026 Sonic Liberation
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



package io.github.sonic_liberation.spotube_plugin_spotify.core

import dev.krtirtho.plugin_interfaces.extras.spotor.JsonContentSerializer
import dev.krtirtho.plugin_interfaces.extras.spotor.SpotrClient
import dev.krtirtho.plugin_interfaces.host_apis.Cookie
import dev.krtirtho.plugin_interfaces.host_apis.HttpClientAPI
import dev.krtirtho.plugin_interfaces.host_apis.PersistedStorageAPI
import dev.krtirtho.plugin_interfaces.host_apis.WebViewAPI
import dev.krtirtho.plugin_interfaces.plugin_apis.core.CoreAPI
import dev.krtirtho.plugin_interfaces.plugin_apis.core.PluginUpdateInfo
import io.github.sonic_liberation.spotify_gql_client.gql.CredentialsFromCookieResult
import io.github.sonic_liberation.spotify_gql_client.gql.SpotifyGQLBaseClient
import io.github.sonic_liberation.spotube_plugin_spotify.services.TOTP
import io.github.sonic_liberation.spotube_plugin_spotify.services.TOTPAlgorithm
import io.github.sonic_liberation.spotube_plugin_spotify.services.TOTPEncoding
import io.github.sonic_liberation.spotube_plugin_spotify.services.TOTPOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import net.swiftzer.semver.SemVer
import kotlin.js.Date
import kotlin.random.Random
import kotlin.time.Clock


private val json = Json { ignoreUnknownKeys = true }

class RealCoreAPI(
    httpClient: HttpClientAPI,
    private val totp: TOTP,
    private val storage: PersistedStorageAPI,
    private val webView: WebViewAPI
) : CoreAPI {
    private val scope = CoroutineScope(Dispatchers.Main)
    private var refreshJob: Job? = null
    private var client = SpotrClient(httpClient) {
        serializer = JsonContentSerializer(json)
    }

    override suspend fun checkPluginUpdates(currentVersion: SemVer): PluginUpdateInfo? {
        return null
    }

    override fun supportMarkdownText(currentVersion: SemVer): String {
        return "Support us please!"
    }

    override val requiresAuthentication = true

    private val loggedInStateFlow = MutableStateFlow(false)
    override val loggedInFlow = loggedInStateFlow.asStateFlow()

    init {
        scope.launch {
            restoreSession()
            scheduleForRefresh()
        }
    }

    private suspend fun restoreSession() {
        val credentialsRaw = storage.getString("credentials") ?: return
        val credentials = json.decodeFromString<CredentialsFromCookieResult>(credentialsRaw)
        SpotifyGQLBaseClient.credentials = credentials

        // The stored access token may already be expired (or about to expire) if the app
        // was closed/backgrounded for a while. If we mark the session as logged in right away,
        // outgoing requests can race the refresh and fail with 401. So refresh eagerly first.
        val now = Clock.System.now().toEpochMilliseconds()
        if (credentials.expiration - now <= REFRESH_MARGIN_MILLIS) {
            try {
                storeSession(credentialsFromCookie(credentials.cookies))
            } catch (e: Throwable) {
                console.log("[RealCoreAPI.restoreSession] Failed to refresh expired session: ${e.message}")
            }
        }

        loggedInStateFlow.value = true
    }

    private suspend fun storeSession(
        credentials: CredentialsFromCookieResult
    ) {
        storage.putString("credentials", json.encodeToString(credentials))
        SpotifyGQLBaseClient.credentials = credentials
    }

    private fun scheduleForRefresh() {
        // Cancel any previously running refresh chain before starting a new one, otherwise
        // multiple overlapping chains can pile up (e.g. one started from restoreSession/init
        // and another from login()), each recursing on its own and hammering the token endpoint.
        refreshJob?.cancel()
        refreshJob = scope.launch {
            val expiration = SpotifyGQLBaseClient.credentials?.expiration
            val cookies = SpotifyGQLBaseClient.credentials?.cookies

            if (expiration == null || cookies == null) {
                console.log("[RealCoreAPI.scheduleForRefresh] Cannot schedule refresh: missing credentials")
                return@launch
            }

            // Some token responses (e.g. reason=transport) can come back with a lifetime shorter
            // than REFRESH_MARGIN_MILLIS. Subtracting a fixed margin in that case would produce a
            // delay of 0 forever, causing a tight refresh loop. Always enforce a sane minimum
            // delay so we never re-request the token faster than MIN_REFRESH_DELAY_MILLIS.
            val remainingMillis = expiration - Clock.System.now().toEpochMilliseconds()
            val delayMillis =
                (remainingMillis - REFRESH_MARGIN_MILLIS).coerceAtLeast(MIN_REFRESH_DELAY_MILLIS)
            console.log("[RealCoreAPI.scheduleForRefresh] Scheduling refresh in ${delayMillis}ms")

            delay(timeMillis = delayMillis)

            try {
                storeSession(credentialsFromCookie(cookies))
            } catch (e: Throwable) {
                // Don't let a transient failure kill the refresh chain; back off and retry later
                // instead of recursing immediately, which would otherwise busy-loop on errors.
                console.log("[RealCoreAPI.scheduleForRefresh] Failed to refresh session: ${e.message}")
                delay(timeMillis = MIN_REFRESH_DELAY_MILLIS)
            }

            scheduleForRefresh()
        }
    }

    private suspend fun generateTimedOnTimePassword(secret: String): String {
        val response = client.get {
            url("https://open.spotify.com/api/server-time")
        }
        val timestampSeconds = response.body<ServerTimeResponse>().serverTime

        val res = this.totp.generate(
            key = secret, options = TOTPOptions(
                digits = 6,
                algorithm = TOTPAlgorithm.SHA1,
                encoding = TOTPEncoding.BASE32,
                period = 30,
                timestamp = timestampSeconds
            )
        )

        return res.otp
    }

    private suspend fun getLatestNuance(cached: Boolean = true): NuanceInfo {
        val timestamp = Clock.System.now().toEpochMilliseconds()
        client.get {
            url("https://gist.githubusercontent.com/raw/22ed9c6ba463899e933427f7de1f0eef/nuances.json")
            if (!cached) {
                parameter("t", timestamp.toString())
            }
        }.body<List<NuanceInfo>>().let { data ->
            return data.maxByOrNull { it.v } ?: throw Exception("No nuances found")
        }
    }

    private fun randomBytesFromMath(length: Int): String {
        val bytes = mutableListOf<String>()

        for (i in 0 until length) {
            bytes.add(Random.nextInt(256).toString())
        }

        return bytes.joinToString("")
    }


    private suspend fun getToken(
        mode: String = "transport",
        totp: String, spDc: String, totpVer: Int
    ): GetTokenResponse {
        val accessTokenUrl =
            "https://open.spotify.com/api/token?reason=$mode&productType=web-player&totp=$totp&totpServer=$totp&totpVer=$totpVer"

        val userAgent =
            "${Date.now()}${Random.nextInt(100) * 1000}${randomBytesFromMath(16)}".split("")
                .joinToString("")

        console.log("Requesting token with URL: $accessTokenUrl")
        console.log("Requesting token with User-Agent: $userAgent")
        console.log("Requesting token with sp_dc: $spDc")

        return client.get {
            url(accessTokenUrl)
            headers {
                append("User-Agent", userAgent)
                append("Cookie", spDc)
            }
        }.let { it ->
            if (it.statusCode != 200) {
                throw Exception("Failed to get token: ${it.statusCode} - ${it.bodyAsText()}")
            }
            val body = it.body<SpotifyTokenResponse>()

            GetTokenResponse(
                body = body, headers = it.headers.map { (key, value) ->
                    key to value.joinToString(";")
                }.toMap()
            )
        }
    }

    private suspend fun credentialsFromCookie(cookies: List<Cookie>): CredentialsFromCookieResult {
        val spDc = cookies.firstOrNull { it.name == "sp_dc" }?.value
            ?: throw Exception("sp_dc cookie not found")


        val nuance = getLatestNuance()

        val totp = generateTimedOnTimePassword(nuance.s)

        val token = runCatching {
            getToken(
                totp = totp,
                spDc = "sp_dc=$spDc;",
                mode = "transport",
                totpVer = nuance.v,
            )
        }
            .getOrElse {
                // We bust cache of nuance
                val nuance = getLatestNuance(cached = false)
                val totp = generateTimedOnTimePassword(nuance.s)
                getToken(
                    totp = totp,
                    spDc = "sp_dc=$spDc;",
                    mode = "transport",
                    totpVer = nuance.v,
                )
            }

        return CredentialsFromCookieResult(
            cookies = cookies,
            accessToken = token.body.accessToken,
            expiration = token.body.accessTokenExpirationTimestampMs
        )
    }


    companion object {
        private val exp = Regex(
            """^https://accounts\.spotify\.com/[^/]+/status($|\?.*)$"""
        )

        // Refresh the access token a bit before it actually expires, so in-flight/queued
        // requests don't race against expiration and fail with 401.
        private const val REFRESH_MARGIN_MILLIS = 60_000L

        // Floor for the refresh delay. Guards against tight refresh loops when a token's
        // actual lifetime is shorter than REFRESH_MARGIN_MILLIS, or on repeated failures.
        private const val MIN_REFRESH_DELAY_MILLIS = 30_000L
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override suspend fun login() {
        webView.webviewCreatedFlow()
            .take(1)
            .flatMapLatest {
                webView.urlChangeFlow()
            }.onEach { url ->
                val safeUrl =
                    if (url.endsWith("/") && url.length > 1) url.substring(
                        0,
                        url.length - 1
                    ) else url

                val hasMatch = exp.matches(safeUrl)

                if (hasMatch) {
                    val cookies = webView.getCookies("https://spotify.com")
                    storeSession(credentialsFromCookie(cookies))
                    webView.exitWebView()
                    loggedInStateFlow.value = true
                    scheduleForRefresh()
                }
            }.launchIn(scope)

        webView.navigateTo("https://accounts.spotify.com/")
    }

    override suspend fun logout() {
        refreshJob?.cancel()
        SpotifyGQLBaseClient.credentials = null
        storage.remove("credentials")
        loggedInStateFlow.value = false
    }
}
