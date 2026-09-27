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

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.SystemClock
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.inputmethod.InputMethodManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.krtirtho.spotube.core.webview.WebViewController
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * Types into whatever view has input focus by sending key events, like a
 * hardware keyboard would. Works for Compose text fields and web page inputs
 * alike. Tracks what it typed so each update only sends the difference
 * (assumes the caret is at the end, which it is while typing from the phone).
 */
class KeyInjector(private val activity: Activity) {
    private val keyMap: KeyCharacterMap = KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD)
    private var typed = ""

    fun reset(current: String = "") {
        typed = current
    }

    fun setText(value: String) {
        val common = typed.commonPrefixWith(value).length
        repeat(typed.length - common) { press(KeyEvent.KEYCODE_DEL) }
        type(value.substring(common))
        typed = value
    }

    fun enter() = press(KeyEvent.KEYCODE_ENTER)

    private fun type(text: String) {
        for (char in text) {
            val events = keyMap.getEvents(charArrayOf(char))
            if (events != null) {
                events.forEach { activity.dispatchKeyEvent(it) }
            } else {
                // Characters the virtual keymap can't produce (accents, emoji).
                activity.dispatchKeyEvent(
                    KeyEvent(SystemClock.uptimeMillis(), char.toString(), KeyCharacterMap.VIRTUAL_KEYBOARD, 0)
                )
            }
        }
    }

    private fun press(keyCode: Int) {
        val now = SystemClock.uptimeMillis()
        activity.dispatchKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0))
        activity.dispatchKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0))
    }
}

/**
 * Binds one of our own text fields (e.g. Search) to the phone while [active]:
 * the phone shows its label and current text, edits flow both ways.
 */
@Composable
fun PhoneFieldBinding(
    active: Boolean,
    field: PhoneField,
    value: String,
    onText: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    val context = LocalContext.current
    val service = remember { PhoneKeyboardService.get(context) }
    val state by service.state.collectAsStateWithLifecycle()
    val latestOnText by rememberUpdatedState(onText)
    val latestOnSubmit by rememberUpdatedState(onSubmit)
    val latestValue by rememberUpdatedState(value)
    val running = state.pairingUrl != null
    val handleHolder = remember { arrayOfNulls<PhoneFieldHandle>(1) }

    DisposableEffect(active, running) {
        if (active && running) {
            handleHolder[0] = service.activate(
                field.copy(value = latestValue),
                onText = { latestOnText(it) },
                onSubmit = { latestOnSubmit() },
            )
        }
        onDispose {
            handleHolder[0]?.close()
            handleHolder[0] = null
        }
    }
    LaunchedEffect(value) {
        handleHolder[0]?.textChanged(value)
    }
}

/**
 * While the sign-in WebView (or any plugin web page) is open, the phone types
 * into the input selected on the TV. The page is asked what that input is, so
 * the phone shows its label and hides passwords.
 */
@Composable
fun PhoneWebViewBridge(webViewController: WebViewController) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() } ?: return
    val service = remember { PhoneKeyboardService.get(context) }
    val injector = remember(activity) { KeyInjector(activity) }

    LaunchedEffect(webViewController) {
        var handle: PhoneFieldHandle? = null
        var lastDescriptor: String? = null
        try {
            while (isActive) {
                if (service.isRunning) {
                    val raw = runCatching { webViewController.evaluateJavascript(ACTIVE_INPUT_JS) }.getOrNull()
                    val info = parseInputInfo(raw)
                    val descriptor = info?.descriptor
                    if (descriptor != lastDescriptor || (handle != null && !handle.isActive)) {
                        handle?.close()
                        handle = null
                        lastDescriptor = descriptor
                        if (info != null) {
                            injector.reset(info.value)
                            handle = service.activate(
                                PhoneField(
                                    label = info.label.ifBlank { "Web page field" },
                                    hint = info.placeholder,
                                    obscure = info.obscure,
                                    action = "go",
                                    value = info.value,
                                ),
                                onText = injector::setText,
                                onSubmit = injector::enter,
                            )
                        }
                    }
                }
                delay(700)
            }
        } finally {
            handle?.close()
        }
    }
}

/**
 * Fallback for every other text field (e.g. upstream's Settings and Plugins
 * screens): whenever some field accepts text and nothing more specific is
 * bound, the phone types into it with key events.
 */
@Composable
fun PhoneAnyFieldBridge(enabled: Boolean) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() } ?: return
    val service = remember { PhoneKeyboardService.get(context) }
    val injector = remember(activity) { KeyInjector(activity) }
    val imm = remember(activity) { activity.getSystemService(InputMethodManager::class.java) }

    LaunchedEffect(enabled) {
        if (!enabled) return@LaunchedEffect
        var handle: PhoneFieldHandle? = null
        try {
            while (isActive) {
                val accepting = service.isRunning && imm?.isAcceptingText == true
                when {
                    handle != null && (!accepting || !handle.isActive) -> {
                        if (handle.isActive) handle.close()
                        handle = null
                    }
                    handle == null && accepting && !service.hasActiveField -> {
                        injector.reset()
                        handle = service.activate(
                            PhoneField(label = "Text field on the TV", action = "done"),
                            onText = injector::setText,
                            onSubmit = injector::enter,
                        )
                    }
                }
                delay(500)
            }
        } finally {
            handle?.close()
        }
    }
}

private data class WebInputInfo(
    val descriptor: String,
    val label: String,
    val placeholder: String,
    val obscure: Boolean,
    val value: String,
)

private fun parseInputInfo(raw: String?): WebInputInfo? {
    if (raw.isNullOrBlank() || raw == "null") return null
    // evaluateJavascript returns the JSON-encoded result; our script returns a JSON string.
    val element = runCatching { Json.parseToJsonElement(raw) }.getOrNull() ?: return null
    val obj = when {
        element is JsonObject -> element
        element is JsonPrimitive && element.isString ->
            runCatching { Json.parseToJsonElement(element.content) as? JsonObject }.getOrNull()
        else -> null
    } ?: return null
    fun str(name: String) = (obj[name] as? JsonPrimitive)?.contentOrNull.orEmpty()
    val descriptor = str("d").ifEmpty { return null }
    return WebInputInfo(
        descriptor = descriptor,
        label = str("label"),
        placeholder = str("placeholder"),
        obscure = (obj["obscure"] as? JsonPrimitive)?.booleanOrNull == true,
        value = str("value"),
    )
}

/** Describes the focused text input on the page, or null. */
private const val ACTIVE_INPUT_JS = """
(function() {
  var el = document.activeElement;
  if (!el) return null;
  var tag = el.tagName;
  var textTypes = ['text','email','password','search','tel','url','number',''];
  var isInput = tag === 'INPUT' && textTypes.indexOf((el.getAttribute('type') || '').toLowerCase()) >= 0;
  if (!isInput && tag !== 'TEXTAREA' && !el.isContentEditable) return null;
  var label = el.getAttribute('aria-label') || '';
  if (!label && el.id) {
    var l = document.querySelector('label[for="' + el.id.replace(/"/g, '') + '"]');
    if (l) label = l.textContent.trim();
  }
  if (!label && el.labels && el.labels.length) label = el.labels[0].textContent.trim();
  if (!label) label = el.getAttribute('name') || '';
  var type = (el.getAttribute('type') || tag).toLowerCase();
  return JSON.stringify({
    d: location.host + '|' + (el.id || el.name || '') + '|' + type + '|' + Array.prototype.indexOf.call(document.querySelectorAll('input,textarea'), el),
    label: label,
    placeholder: el.getAttribute('placeholder') || '',
    obscure: type === 'password',
    value: type === 'password' ? '' : (el.value || '')
  });
})()
"""
