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

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** URL-safe base64 without padding (the phone page's encoding). */
fun encodeB64(bytes: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

/** Throws [IllegalArgumentException] on malformed input. */
fun decodeB64(text: String): ByteArray = Base64.getUrlDecoder().decode(text)

data class PhoneEnvelope(val n: String, val c: String) {
    fun toJson(): String = buildJsonObject {
        put("n", JsonPrimitive(n))
        put("c", JsonPrimitive(c))
    }.toString()

    companion object {
        fun fromJson(text: String): PhoneEnvelope {
            val obj = runCatching { Json.parseToJsonElement(text) as? JsonObject }.getOrNull()
                ?: throw PhoneChannelRejected("bad envelope")
            val n = (obj["n"] as? JsonPrimitive)?.takeIf { it.isString }?.content
            val c = (obj["c"] as? JsonPrimitive)?.takeIf { it.isString }?.content
            if (n == null || c == null) throw PhoneChannelRejected("bad envelope")
            return PhoneEnvelope(n, c)
        }
    }
}

sealed interface PhoneInput {
    val field: String?

    data class Text(val value: String, override val field: String?) : PhoneInput {
        override fun toString() = "Text(${value.length} chars)"
    }

    data class Enter(override val field: String?) : PhoneInput
    data object Ping : PhoneInput {
        override val field: String? = null
    }
}

class PhoneChannelRejected(val reason: String) : Exception(reason)

/**
 * AES-256-GCM channel between the TV and the phone page (ported from
 * BeatVisualizer / Flow-TV, same wire format). The key only travels in the QR
 * code's URL fragment, which browsers never send over the network.
 */
class PhoneChannel(sessionId: ByteArray, key: ByteArray) {
    private val sid = sessionId.copyOf()
    private val secret = key.copyOf()
    private var lastSeq = 0L
    private var boundHost: String? = null

    val sessionId: ByteArray get() = sid.copyOf()
    val key: ByteArray get() = secret.copyOf()
    val fragment: String get() = "${encodeB64(sid)}.${encodeB64(secret)}"

    /** Decrypts one message from the phone; the first valid sender's address is pinned. */
    @Synchronized
    fun open(envelope: PhoneEnvelope, remoteHost: String): PhoneInput {
        boundHost?.let { if (it != remoteHost) throw PhoneChannelRejected("bound to another device") }
        val nonce: ByteArray
        val sealed: ByteArray
        try {
            nonce = decodeB64(envelope.n)
            sealed = decodeB64(envelope.c)
        } catch (_: IllegalArgumentException) {
            throw PhoneChannelRejected("bad encoding")
        }
        if (nonce.size != NONCE_LENGTH || sealed.size < TAG_LENGTH) throw PhoneChannelRejected("bad size")

        val plain = try {
            cipher(Cipher.DECRYPT_MODE, nonce, "c2s").doFinal(sealed)
        } catch (_: AEADBadTagException) {
            throw PhoneChannelRejected("bad seal")
        }
        val parsed: JsonObject? = try {
            Json.parseToJsonElement(plain.decodeToString()) as? JsonObject
        } catch (_: Exception) {
            null
        } finally {
            plain.fill(0)
        }
        val payload = parsed ?: throw PhoneChannelRejected("bad payload")

        val seq = (payload["seq"] as? JsonPrimitive)?.takeUnless { it.isString }?.longOrNull
        val type = payload.string("type")
        val fieldElement = payload["field"]
        val field = payload.string("field")
        if (seq == null || type == null || (fieldElement != null && fieldElement !is JsonNull && field == null)) {
            throw PhoneChannelRejected("bad payload")
        }
        if (seq <= lastSeq) throw PhoneChannelRejected("replayed")
        val value = payload.string("value")
        val input = when (type) {
            "text" -> {
                if (value == null) throw PhoneChannelRejected("bad payload")
                if (value.length > MAX_TEXT_LENGTH) throw PhoneChannelRejected("too long")
                PhoneInput.Text(value, field)
            }
            "key" -> if (value == "ENTER") PhoneInput.Enter(field) else throw PhoneChannelRejected("unknown key")
            "ping" -> PhoneInput.Ping
            else -> throw PhoneChannelRejected("unknown type")
        }
        lastSeq = seq
        boundHost = remoteHost
        return input
    }

    /** Encrypts a status object for the phone. */
    fun seal(status: JsonObject): PhoneEnvelope {
        val nonce = randomBytes(NONCE_LENGTH)
        val sealed = cipher(Cipher.ENCRYPT_MODE, nonce, "s2c").doFinal(status.toString().encodeToByteArray())
        return PhoneEnvelope(encodeB64(nonce), encodeB64(sealed))
    }

    // javax's GCM output is ciphertext || tag, the same layout noble-ciphers uses.
    private fun cipher(mode: Int, nonce: ByteArray, direction: String): Cipher =
        Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(mode, SecretKeySpec(secret, "AES"), GCMParameterSpec(TAG_LENGTH * 8, nonce))
            updateAAD(sid + direction.encodeToByteArray())
        }

    private fun JsonObject.string(name: String): String? =
        (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull

    companion object {
        const val NONCE_LENGTH = 12
        const val TAG_LENGTH = 16
        const val SESSION_ID_LENGTH = 16
        const val KEY_LENGTH = 32
        const val MAX_TEXT_LENGTH = 2048

        private val random = SecureRandom()

        fun random(): PhoneChannel = PhoneChannel(randomBytes(SESSION_ID_LENGTH), randomBytes(KEY_LENGTH))

        fun randomBytes(n: Int): ByteArray = ByteArray(n).also(random::nextBytes)
    }
}
