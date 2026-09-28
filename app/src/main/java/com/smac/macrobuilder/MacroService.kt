package com.smac.macrobuilder

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class MacroService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val running = mutableMapOf<String, Job>()
    private var foreground = ""

    override fun onServiceConnected() {
        super.onServiceConnected()
        Diag.connected = true
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Diag.connected = false
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(e: AccessibilityEvent) {
        if (e.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            e.packageName?.let { foreground = it.toString() }
        }
    }

    override fun onInterrupt() {}

    private fun activePackage(): String {
        val p = try { rootInActiveWindow?.packageName?.toString() } catch (e: Exception) { null }
        return if (p.isNullOrEmpty()) foreground else p
    }

    override fun onKeyEvent(ev: KeyEvent): Boolean {
        if (KeyInjector.held.contains(ev.keyCode)) return false
        val cfg = MacroStore.load(this)
        val combo = MacroStore.combo(ev)
        val pkg = activePackage()
        val first = ev.action == KeyEvent.ACTION_DOWN && ev.repeatCount == 0
        if (first) Diag.lastKey = "$combo @ ${pkg.ifEmpty { "?" }}"
        if (cfg.onlyTarget && pkg != cfg.targetPackage) {
            if (first) Diag.result = "other_app"
            return false
        }
        val macro = cfg.macros.firstOrNull { it.trigger.equals(combo, ignoreCase = true) }
        if (macro == null) {
            if (first) Diag.result = "no_macro"
            return false
        }
        if (first) start(macro)
        return true
    }

    private fun start(m: Macro) {
        Diag.arg = m.name
        val old = running[m.name]
        if (old?.isActive == true) {
            if (m.toggle) {
                old.cancel()
                Diag.result = "stopped"
            } else {
                Diag.result = "busy"
            }
            return
        }
        Diag.result = "started"
        running[m.name] = scope.launch {
            var i = 0
            val infinite = m.toggle && m.repeat == 0
            val total = maxOf(1, m.repeat)
            while (isActive && (infinite || i < total)) {
                runActions(m)
                i++
                delay(1)
            }
        }
    }

    private suspend fun runActions(m: Macro) {
        val list = m.actions.toList()
        var i = 0
        while (i < list.size) {
            if (list[i].type == "key") {
                var j = i
                while (j < list.size && list[j].type == "key") j++
                pressKeys(list.subList(i, j), m.together)
                i = j
            } else {
                exec(list[i])
                i++
            }
        }
    }

    private suspend fun pressKeys(group: List<MacroAction>, together: Boolean) {
        if (together) {
            try {
                group.forEach { KeyInjector.send(it.key, true) }
                delay(group.maxOf { it.ms })
            } finally {
                group.forEach { KeyInjector.send(it.key, false) }
            }
        } else {
            for (k in group) {
                try {
                    KeyInjector.send(k.key, true)
                    delay(k.ms)
                } finally {
                    KeyInjector.send(k.key, false)
                }
            }
        }
    }

    private suspend fun exec(a: MacroAction) {
        when (a.type) {
            "delay" -> delay(a.ms)
            "tap" -> {
                gesture(Path().apply { moveTo(a.x, a.y) }, 50)
                delay(a.ms)
            }
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
        Diag.connected = false
        scope.cancel()
        super.onDestroy()
    }
}
