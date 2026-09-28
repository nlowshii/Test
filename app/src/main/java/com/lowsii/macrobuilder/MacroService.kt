package com.lowsii.macrobuilder

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.*
import kotlin.coroutines.resume

class MacroService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val running = mutableMapOf<String, Job>()
    private var foreground = ""

    override fun onAccessibilityEvent(e: AccessibilityEvent) {
        if (e.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            e.packageName?.let { foreground = it.toString() }
        }
    }

    override fun onInterrupt() {}

    override fun onKeyEvent(ev: KeyEvent): Boolean {
        val cfg = MacroStore.load(this)
        if (cfg.onlyMinecraft && foreground != MacroStore.MC_PACKAGE) return false

        val combo = combo(ev)
        val macro = cfg.macros.firstOrNull { it.trigger.equals(combo, ignoreCase = true) }
            ?: return false

        if (ev.action == KeyEvent.ACTION_DOWN && ev.repeatCount == 0) start(macro)
        return true // tombol hotkey tidak diteruskan ke game
    }

    private fun combo(ev: KeyEvent): String {
        val parts = mutableListOf<String>()
        if (ev.isCtrlPressed) parts += "CTRL"
        if (ev.isAltPressed) parts += "ALT"
        if (ev.isShiftPressed) parts += "SHIFT"
        parts += KeyEvent.keyCodeToString(ev.keyCode).removePrefix("KEYCODE_")
        return parts.joinToString("+")
    }

    private fun start(m: Macro) {
        val old = running[m.name]
        if (old?.isActive == true) {
            if (m.toggle) old.cancel()
            return
        }
        running[m.name] = scope.launch {
            var i = 0
            val infinite = m.toggle && m.repeat == 0
            val total = maxOf(1, m.repeat)
            while (isActive && (infinite || i < total)) {
                for (a in m.actions) { if (!isActive) break; exec(a) }
                i++
                delay(1) // cegah loop tanpa jeda
            }
        }
    }

    private suspend fun exec(a: MacroAction) {
        when (a.type) {
            "delay" -> delay(a.ms)
            "tap" -> { gesture(Path().apply { moveTo(a.x, a.y) }, 50); delay(a.ms) }
            "long_press" -> gesture(Path().apply { moveTo(a.x, a.y) }, a.ms)
            "swipe" -> gesture(Path().apply { moveTo(a.x, a.y); lineTo(a.x2, a.y2) }, a.ms)
        }
    }

    private suspend fun gesture(path: Path, duration: Long) =
        suspendCancellableCoroutine<Unit> { c ->
            val g = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, duration.coerceAtLeast(1)))
                .build()
            val cb = object : GestureResultCallback() {
                override fun onCompleted(d: GestureDescription?) { if (c.isActive) c.resume(Unit) }
                override fun onCancelled(d: GestureDescription?) { if (c.isActive) c.resume(Unit) }
            }
            if (!dispatchGesture(g, cb, null) && c.isActive) c.resume(Unit)
        }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
