package com.systemverktoy.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

internal class PinStore(context: Context) {
    private val prefs = context.getSharedPreferences("pin", Context.MODE_PRIVATE)
    val configured get() = prefs.contains("hash")
    val blockedUntil get() = prefs.getLong("blocked", 0)
    private fun hash(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, 120_000, 256)
        return try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded }
        finally { spec.clearPassword() }
    }
    fun create(pin: String) {
        require(pin.length == 4 && pin.all { it in '0'..'9' })
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        check(prefs.edit().putString("salt", Base64.getEncoder().encodeToString(salt))
            .putString("hash", Base64.getEncoder().encodeToString(hash(pin, salt))).commit())
    }
    fun verify(pin: String, now: Long): Boolean {
        if (!configured || now < blockedUntil) return false
        val salt = Base64.getDecoder().decode(prefs.getString("salt", null))
        val valid = MessageDigest.isEqual(hash(pin, salt), Base64.getDecoder().decode(prefs.getString("hash", null)))
        val failures = if (valid) 0 else prefs.getInt("failures", 0) + 1
        check(prefs.edit().putInt("failures", if (failures >= 5) 0 else failures)
            .putLong("blocked", if (failures >= 5) now + 60_000 else 0).commit())
        return valid
    }
    fun reset() { check(prefs.edit().clear().commit()) }
}

class PinResetReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "com.systemverktoy.app.RESET_PIN") PinStore(context).reset()
    }
}

@Composable
internal fun PinGate(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val store = remember { PinStore(context) }
    var unlocked by remember { mutableStateOf(false) }
    var generation by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { unlocked = false; generation++ }
    if (unlocked) content() else key(generation) { PinScreen(store) { unlocked = true } }
}

@Composable
private fun PinScreen(store: PinStore, onUnlock: () -> Unit) {
    var confirmation by remember { mutableStateOf<String?>(null) }
    var digits by remember { mutableStateOf("0000") }
    var enteredPlaces by remember { mutableStateOf(setOf<Int>()) }
    var showCurrentDigit by remember { mutableStateOf(false) }
    var cursor by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val focus = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    val inputMode = LocalInputModeManager.current
    LaunchedEffect(Unit) { inputMode.requestInputMode(InputMode.Keyboard); focus.requestFocus(); while (true) { now = System.currentTimeMillis(); delay(250) } }
    fun submit() {
        if (busy || now < store.blockedUntil) return
        if (enteredPlaces.size != 4) { message = "Tast alle fire sifrene."; return }
        val entered = digits
        digits = "0000"; cursor = 0; enteredPlaces = emptySet(); showCurrentDigit = false
        if (!store.configured && confirmation == null) { confirmation = entered; return }
        if (!store.configured && confirmation != entered) { confirmation = null; message = "Kodene var ulike. Prøv igjen."; return }
        busy = true
        scope.launch {
            val success = withContext(Dispatchers.Default) {
                if (!store.configured) { store.create(entered); true } else store.verify(entered, System.currentTimeMillis())
            }
            busy = false
            if (success) onUnlock() else message = "Feil PIN-kode."
        }
    }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(64.dp)
        .focusRequester(focus).onPreviewKeyEvent { event ->
            val code = event.nativeKeyEvent.keyCode
            val digit = when (code) {
                in android.view.KeyEvent.KEYCODE_0..android.view.KeyEvent.KEYCODE_9 -> code - android.view.KeyEvent.KEYCODE_0
                in android.view.KeyEvent.KEYCODE_NUMPAD_0..android.view.KeyEvent.KEYCODE_NUMPAD_9 -> code - android.view.KeyEvent.KEYCODE_NUMPAD_0
                else -> null
            }
            val handled = digit != null || event.key in listOf(Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft,
                Key.DirectionRight, Key.DirectionCenter, Key.Enter, Key.NumPadEnter)
            if (handled && event.type == KeyEventType.KeyDown && !busy && now >= store.blockedUntil) {
                when {
                    digit != null -> {
                        digits = digits.replaceRange(cursor, cursor + 1, digit.toString())
                        enteredPlaces = enteredPlaces + cursor
                        showCurrentDigit = false
                        cursor = (cursor + 1).coerceAtMost(3)
                    }
                    event.key in listOf(Key.DirectionLeft, Key.DirectionRight) -> {
                        enteredPlaces = enteredPlaces + cursor
                        cursor = (cursor + if (event.key == Key.DirectionLeft) -1 else 1).coerceIn(0, 3)
                        enteredPlaces = enteredPlaces + cursor
                        showCurrentDigit = true
                    }
                    event.key in listOf(Key.DirectionUp, Key.DirectionDown) -> {
                        val value = (digits[cursor].digitToInt() + if (event.key == Key.DirectionUp) 1 else 9) % 10
                        digits = digits.replaceRange(cursor, cursor + 1, value.toString())
                        enteredPlaces = enteredPlaces + cursor
                        showCurrentDigit = true
                    }
                    event.nativeKeyEvent.repeatCount == 0 -> submit()
                }
            }
            handled
        }.focusable(), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Text(if (store.configured) "Tast PIN-kode" else if (confirmation == null) "Velg PIN-kode" else "Gjenta PIN-koden", fontSize = 28.sp, color = Color(0xFFEEF1F4))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            repeat(4) { i -> Text(when {
                i == cursor && showCurrentDigit -> digits[i].toString()
                i in enteredPlaces -> "●"
                else -> "○"
            }, color = Color(0xFFEEF1F4), fontSize = 32.sp, modifier = Modifier.testTag("pin-digit-$i").border(2.dp,
                if (i == cursor) Color.White else Color.Transparent).padding(16.dp)) }
        }
        Text("Tallknapper eller ↑ / ↓ endrer siffer fra 0 · ← / → flytter · OK bekrefter", fontSize = 16.sp, color = Color(0xFFEEF1F4))
        Text(if (now < store.blockedUntil) "Vent ${(store.blockedUntil - now + 999) / 1000} sekunder." else if (busy) "Kontrollerer…" else message, fontSize = 18.sp, color = Color(0xFFEEF1F4))
    }
}
