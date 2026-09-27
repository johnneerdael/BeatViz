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

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

enum class PhoneKeyboardStatus { Off, NoNetwork, CouldNotStart, Waiting, Connected }

data class PhoneKeyboardState(
    val enabled: Boolean = false,
    val status: PhoneKeyboardStatus = PhoneKeyboardStatus.Off,
    val address: String? = null,
    val pairingUrl: String? = null,
    val portChanged: Boolean = false,
    /** True while enable/disable/forget runs (UI disables its buttons). */
    val busy: Boolean = false,
)

/** What the phone shows for the field being edited on the TV. */
data class PhoneField(
    val label: String,
    val hint: String = "",
    val obscure: Boolean = false,
    /** "done", "search" or "go" (the phone keyboard's Enter label). */
    val action: String = "done",
    val value: String = "",
)

interface PhoneFieldHandle {
    /** Mirrors an edit made on the TV to the phone. */
    fun textChanged(value: String)
    fun close()
    val isActive: Boolean
}

/**
 * Phone keyboard: remembered pairing, LAN server and the one text field the
 * phone currently types into (ported from BeatVisualizer). Process singleton.
 */
class PhoneKeyboardService private constructor(context: Context) : PhoneStatusSource {
    private val appContext = context.applicationContext
    private val prefs: SharedPreferences = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val opLock = Mutex()
    private val lock = Any()
    private val assetCache = mutableMapOf<String, ByteArray>()

    private val _state = MutableStateFlow(PhoneKeyboardState(enabled = prefs.getBoolean(KEY_ENABLED, false)))
    val state: StateFlow<PhoneKeyboardState> = _state.asStateFlow()

    private val revFlow = MutableStateFlow(0)
    private var server: PhoneInputServer? = null
    private var channel: PhoneChannel? = null
    private var ip: String? = null
    private var lastInputAt = 0L
    private var active: ActiveField? = null
    private var watchJob: Job? = null

    private inner class ActiveField(
        val id: String,
        val field: PhoneField,
        val onText: (String) -> Unit,
        val onSubmit: () -> Unit,
    ) : PhoneFieldHandle {
        @Volatile var value: String = field.value

        override val isActive: Boolean get() = synchronized(lock) { active === this }

        override fun textChanged(value: String) {
            synchronized(lock) {
                if (active !== this || this.value == value) return
                this.value = value
            }
            bump()
        }

        override fun close() {
            synchronized(lock) {
                if (active !== this) return
                active = null
            }
            bump()
        }

        fun toJson(): JsonObject = buildJsonObject {
            put("id", JsonPrimitive(id))
            put("label", JsonPrimitive(field.label))
            put("hint", JsonPrimitive(field.hint))
            put("obscure", JsonPrimitive(field.obscure))
            put("value", JsonPrimitive(value))
            put("action", JsonPrimitive(field.action))
        }
    }

    // ---- Controller (Phone keyboard screen) ----

    /** Starts the server at app launch when the feature was left on. */
    fun start() = serial {
        if (!_state.value.enabled) return@serial
        startServer()
        watchNetwork()
    }

    fun enable() = serial {
        if (_state.value.enabled && server != null) return@serial
        prefs.edit().putBoolean(KEY_ENABLED, true).apply()
        _state.update { it.copy(enabled = true) }
        startServer()
        watchNetwork()
    }

    fun disable() = serial {
        watchJob?.cancel()
        watchJob = null
        stopServer()
        prefs.edit().remove(KEY_SID).remove(KEY_KEY).putBoolean(KEY_ENABLED, false).apply()
        _state.update { PhoneKeyboardState(enabled = false, status = PhoneKeyboardStatus.Off) }
    }

    /** New pairing secret: the old phone is locked out and must scan again. */
    fun forgetPhone() = serial {
        stopServer()
        prefs.edit().remove(KEY_SID).remove(KEY_KEY).apply()
        startServer()
    }

    // ---- Host (text fields) ----

    /**
     * Makes [field] the one the phone types into. Returns null while the phone
     * keyboard is off. Callbacks run on the main thread.
     */
    fun activate(field: PhoneField, onText: (String) -> Unit, onSubmit: () -> Unit): PhoneFieldHandle? {
        if (server == null) return null
        val handle = ActiveField(newFieldId(), field, onText, onSubmit)
        synchronized(lock) { active = handle }
        bump()
        return handle
    }

    val isRunning: Boolean get() = server != null

    val hasActiveField: Boolean get() = synchronized(lock) { active != null }

    // ---- Status source (server) ----

    override val rev: Int get() = revFlow.value

    override fun snapshot(): JsonObject = buildJsonObject {
        put("rev", JsonPrimitive(revFlow.value))
        put("field", synchronized(lock) { active }?.toJson() ?: JsonNull)
    }

    override suspend fun awaitChange(since: Int, timeoutMillis: Long) {
        withTimeoutOrNull(timeoutMillis) { revFlow.first { it != since || server == null } }
    }

    override fun accept(input: PhoneInput): Boolean {
        val wasConnected = isConnected()
        lastInputAt = System.currentTimeMillis()
        if (!wasConnected) refreshStatus()
        val target = synchronized(lock) { active }
        return when (input) {
            PhoneInput.Ping -> true
            is PhoneInput.Text -> {
                if (target == null || input.field != target.id) return false
                target.value = input.value
                scope.launch(Dispatchers.Main) { target.onText(input.value) }
                true
            }
            is PhoneInput.Enter -> {
                if (target == null || input.field != target.id) return false
                scope.launch(Dispatchers.Main) { target.onSubmit() }
                true
            }
        }
    }

    // ---- Internals ----

    private fun serial(op: suspend () -> Unit) {
        scope.launch {
            opLock.withLock {
                _state.update { it.copy(busy = true) }
                try {
                    op()
                } finally {
                    _state.update { it.copy(busy = false) }
                }
            }
        }
    }

    private fun bump() {
        revFlow.update { it + 1 }
    }

    private fun isConnected() = System.currentTimeMillis() - lastInputAt < CONNECTED_WINDOW_MS

    private fun refreshStatus() {
        _state.update { current ->
            if (current.status == PhoneKeyboardStatus.Waiting || current.status == PhoneKeyboardStatus.Connected) {
                current.copy(status = if (isConnected()) PhoneKeyboardStatus.Connected else PhoneKeyboardStatus.Waiting)
            } else {
                current
            }
        }
    }

    /**
     * Re-checks the LAN address while enabled: recovers from "No network"
     * (launched before Wi-Fi was up) and follows a changed address, keeping
     * the pairing. Also lets "Phone connected" fall back to "Waiting".
     */
    private fun watchNetwork() {
        if (watchJob?.isActive == true) return
        watchJob = scope.launch {
            while (isActive) {
                delay(NETWORK_RECHECK_MS)
                refreshStatus()
                if (!_state.value.enabled || _state.value.busy) continue
                val current = LanAddress.resolve()
                if (current != null && (current != ip || server == null)) {
                    opLock.withLock { if (_state.value.enabled) startServer() }
                }
            }
        }
    }

    private fun loadOrCreateChannel(): PhoneChannel {
        val sid = prefs.getString(KEY_SID, null)
        val key = prefs.getString(KEY_KEY, null)
        if (sid != null && key != null) {
            runCatching {
                val sidBytes = decodeB64(sid)
                val keyBytes = decodeB64(key)
                if (sidBytes.size == PhoneChannel.SESSION_ID_LENGTH && keyBytes.size == PhoneChannel.KEY_LENGTH) {
                    return PhoneChannel(sidBytes, keyBytes)
                }
            }
        }
        val fresh = PhoneChannel.random()
        prefs.edit()
            .putString(KEY_SID, encodeB64(fresh.sessionId))
            .putString(KEY_KEY, encodeB64(fresh.key))
            .apply()
        return fresh
    }

    private suspend fun startServer() {
        stopServer()
        val address = LanAddress.resolve()
        ip = address
        if (address == null) {
            _state.update { it.copy(status = PhoneKeyboardStatus.NoNetwork, address = null, pairingUrl = null) }
            return
        }
        val newChannel = loadOrCreateChannel()
        val newServer = PhoneInputServer(channel = newChannel, source = this, assets = ::asset)
        var portChanged = false
        try {
            newServer.start(address, PREFERRED_PORT)
        } catch (_: Throwable) {
            try {
                newServer.start(address, 0)
                portChanged = true
            } catch (_: Throwable) {
                _state.update { it.copy(status = PhoneKeyboardStatus.CouldNotStart, address = null, pairingUrl = null) }
                return
            }
        }
        server = newServer
        channel = newChannel
        lastInputAt = 0L
        val url = "http://$address:${newServer.port}/"
        _state.update {
            it.copy(
                status = PhoneKeyboardStatus.Waiting,
                address = url,
                pairingUrl = "$url#${newChannel.fragment}",
                portChanged = portChanged,
            )
        }
    }

    /** Stops the server before the channel (and its key) is dropped. */
    private fun stopServer() {
        val current = server
        server = null
        current?.stop()
        channel = null
        val hadActive = synchronized(lock) { (active != null).also { active = null } }
        bump() // releases held /status requests
        if (hadActive) bump()
        _state.update { it.copy(address = null, pairingUrl = null) }
    }

    private fun asset(name: String): ByteArray = synchronized(assetCache) {
        assetCache.getOrPut(name) { appContext.assets.open("phone-keyboard/$name").use { it.readBytes() } }
    }

    private fun newFieldId(): String = encodeB64(PhoneChannel.randomBytes(9))

    companion object {
        private const val PREFS = "beatviz_phone_keyboard"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_SID = "sid"
        private const val KEY_KEY = "key"
        private const val PREFERRED_PORT = 47320
        private const val CONNECTED_WINDOW_MS = 60_000L
        private const val NETWORK_RECHECK_MS = 15_000L

        @Volatile
        private var instance: PhoneKeyboardService? = null

        fun get(context: Context): PhoneKeyboardService =
            instance ?: synchronized(this) {
                instance ?: PhoneKeyboardService(context).also { instance = it }
            }
    }
}
