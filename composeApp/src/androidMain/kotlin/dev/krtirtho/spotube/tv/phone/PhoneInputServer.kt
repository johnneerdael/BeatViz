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

package dev.krtirtho.spotube.tv.phone

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.receiveText
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.serialization.json.JsonObject
import java.util.concurrent.atomic.AtomicInteger

/** What the server shows the phone and where phone input goes. */
interface PhoneStatusSource {
    val rev: Int

    fun snapshot(): JsonObject

    /** Suspends until [rev] differs from [since] or [timeoutMillis] passes. */
    suspend fun awaitChange(since: Int, timeoutMillis: Long)

    /** False when the input doesn't apply (e.g. a stale field id) -> 403. */
    fun accept(input: PhoneInput): Boolean
}

/**
 * LAN server for the phone page (ported from BeatVisualizer / Flow-TV), with
 * `/status` held open until the status changes.
 */
class PhoneInputServer(
    private val channel: PhoneChannel,
    private val source: PhoneStatusSource,
    private val assets: (String) -> ByteArray,
    private val holdForMillis: Long = 25_000,
    private val maxHeld: Int = 4,
    private val maxBody: Int = 16 * 1024,
) {
    private var server: EmbeddedServer<*, *>? = null
    private val held = AtomicInteger(0)

    var port: Int = 0
        private set

    /** Binds [host]:[requestedPort] (0 = any free port). Throws if binding fails. */
    suspend fun start(host: String, requestedPort: Int) {
        val embedded = embeddedServer(factory = CIO, host = host, port = requestedPort) {
            routing {
                get("/") { asset("index.html", "text/html; charset=utf-8") }
                get("/app.js") { asset("app.js", "text/javascript; charset=utf-8") }
                get("/noble-ciphers.js") { asset("noble-ciphers-2.4.0.min.js", "text/javascript; charset=utf-8") }
                post("/input") { input() }
                get("/status") { status() }
            }
        }
        embedded.start(wait = false)
        try {
            port = embedded.engine.resolvedConnectors().first().port
        } catch (e: Throwable) {
            runCatching { embedded.stop(0, 500) }
            throw e
        }
        server = embedded
    }

    fun stop() {
        val current = server ?: return
        server = null
        runCatching { current.stop(gracePeriodMillis = 0, timeoutMillis = 1_000) }
    }

    private fun ApplicationCall.noStore() {
        response.header("Cache-Control", "no-store")
        response.header("Referrer-Policy", "no-referrer")
        response.header("X-Content-Type-Options", "nosniff")
    }

    private suspend fun io.ktor.server.routing.RoutingContext.asset(name: String, type: String) {
        call.noStore()
        call.respondBytes(assets(name), ContentType.parse(type))
    }

    private suspend fun io.ktor.server.routing.RoutingContext.input() {
        call.noStore()
        val length = call.request.headers["Content-Length"]?.toIntOrNull()
        if (length != null && length > maxBody) {
            call.respond(HttpStatusCode.Forbidden)
            return
        }
        val body = call.receiveText()
        if (body.length > maxBody) {
            call.respond(HttpStatusCode.Forbidden)
            return
        }
        val accepted = try {
            val envelope = PhoneEnvelope.fromJson(body)
            val phoneInput = channel.open(envelope, call.request.local.remoteAddress)
            source.accept(phoneInput)
        } catch (_: PhoneChannelRejected) {
            false
        }
        call.respond(if (accepted) HttpStatusCode.NoContent else HttpStatusCode.Forbidden)
    }

    private suspend fun io.ktor.server.routing.RoutingContext.status() {
        call.noStore()
        val since = call.request.queryParameters["rev"]?.toIntOrNull() ?: -1
        if (since == source.rev && held.incrementAndGet() <= maxHeld) {
            try {
                source.awaitChange(since, holdForMillis)
            } finally {
                held.decrementAndGet()
            }
        } else if (since == source.rev) {
            held.decrementAndGet()
        }
        val envelope = channel.seal(source.snapshot())
        call.respondText(envelope.toJson(), ContentType.Application.Json)
    }
}
