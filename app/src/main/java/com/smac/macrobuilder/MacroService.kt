package com.smac.macrobuilder

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Path
import android.os.Handler
import android.os.Looper
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
    private val handler = Handler(Looper.getMainLooper())
    private var foreground = ""
    private var overlays: Overlays? = null

    @Volatile private var keyCfg = Config(true, mutableListOf())
    @Volatile private var hotCodes: Set<Int> = emptySet()
    @Volatile private var pkgCache = ""

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "json") handler.post { rebuildFloats() }
    }
    private val evaluate = Runnable { syncFloats() }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Diag.connected = true
        overlays = Overlays(this, { fire(it) }, { m, x, y -> touch(m, x, y) })
        MacroStore.prefs(this).registerOnSharedPreferenceChangeListener(prefsListener)
        rebuildFloats()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        shutdown()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        shutdown()
        scope.cancel()
        super.onDestroy()
    }

    private fun shutdown() {
        Diag.connected = false
        handler.removeCallbacksAndMessages(null)
        MacroStore.prefs(this).unregisterOnSharedPreferenceChangeListener(prefsListener)
        overlays?.clear()
        overlays = null
    }

    override fun onAccessibilityEvent(e: AccessibilityEvent) {
        if (e.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val p = e.packageName?.toString()
            if (p != null && p != packageName) foreground = p
            handler.removeCallbacks(evaluate)
            handler.postDelayed(evaluate, 300)
        }
    }

    override fun onInterrupt() {}

    private fun activePackage(): String {
        val p = try { rootInActiveWindow?.packageName?.toString() } catch (e: Exception) { null }
        return if (p.isNullOrEmpty()) foreground else p
    }

    private fun computeHot(c: Config): Set<Int> {
        val out = mutableSetOf<Int>()
        c.macros.forEach { m ->
            val base = m.trigger.substringAfterLast('+').trim()
            if (base.isNotEmpty()) {
                val code = KeyEvent.keyCodeFromString("KEYCODE_$base")
                if (code != KeyEvent.KEYCODE_UNKNOWN) out.add(code)
            }
        }
        return out
    }

    private fun rebuildFloats() {
        val json = MacroStore.raw(this)
        val o = overlays ?: return
        if (json == o.lastJson) return
        val c = MacroStore.load(this)
        keyCfg = c
        hotCodes = computeHot(c)
        pkgCache = activePackage()
        o.update(c, pkgCache == c.targetPackage, json)
    }

    private fun syncFloats() {
        pkgCache = activePackage()
        overlays?.setTarget(pkgCache == keyCfg.targetPackage)
    }

    private fun fire(m: Macro) {
        Diag.lastKey = "floating: ${m.name}"
        start(m, "f:${m.id}")
    }

    private fun touch(m: Macro, x: Float, y: Float) {
        Diag.lastKey = "touch: ${m.name}"
        Diag.arg = m.name
        Diag.result = "started"
        if (KeyInjector.ready()) {
            scope.launch { touchViaShizuku(m, x, y) }
        } else {
            touchViaGesture(m, x, y)
        }
    }

    private suspend fun touchViaShizuku(m: Macro, x: Float, y: Float) {
        if (m.hold) {
            val down = KeyInjector.touchDown(x, y)
            if (down != null) {
                delay(m.holdMs.toLong().coerceAtLeast(1))
                KeyInjector.touchUp(x, y, down)
            } else {
                touchViaGesture(m, x, y)
            }
        } else {
            val n = m.taps.coerceIn(1, 10)
            val step = m.interval.toLong().coerceAtLeast(40)
            for (i in 0 until n) {
                if (!KeyInjector.touchTap(x, y)) {
                    touchViaGesture(m, x, y)
                    return
                }
                if (i < n - 1) delay(step)
            }
        }
    }

    private fun touchViaGesture(m: Macro, x: Float, y: Float) {
        val builder = GestureDescription.Builder()
        if (m.hold) {
            val path = Path().apply { moveTo(x, y) }
            builder.addStroke(
                GestureDescription.StrokeDescription(path, 0, m.holdMs.toLong().coerceAtLeast(1))
            )
        } else {
            val n = m.taps.coerceIn(1, 10)
            val step = m.interval.toLong().coerceAtLeast(40)
            for (i in 0 until n) {
                val path = Path().apply { moveTo(x, y) }
                builder.addStroke(GestureDescription.StrokeDescription(path, i * step, 30))
            }
        }
        dispatchGesture(builder.build(), null, null)
    }

    private fun findMacro(cfg: Config, ev: KeyEvent): Macro? {
        val exact = MacroStore.combo(ev)
        cfg.macros.firstOrNull { it.trigger.equals(exact, ignoreCase = true) }?.let { return it }
        val base = MacroStore.keyName(ev.keyCode)
        return cfg.macros.firstOrNull { it.trigger.equals(base, ignoreCase = true) }
    }

    override fun onKeyEvent(ev: KeyEvent): Boolean {
        val code = ev.keyCode
        val first = ev.action == KeyEvent.ACTION_DOWN && ev.repeatCount == 0
        if (code !in hotCodes) {
            if (first) Diag.lastKey = "${MacroStore.keyName(code)} @ ${pkgCache.ifEmpty { "?" }}"
            return false
        }
        if (KeyInjector.held.contains(code)) return false
        val cfg = keyCfg
        var pkg = pkgCache
        if (first && cfg.onlyTarget && pkg != cfg.targetPackage) {
            pkg = activePackage()
            pkgCache = pkg
        }
        if (first) Diag.lastKey = "${MacroStore.combo(ev)} @ ${pkg.ifEmpty { "?" }}"
        if (cfg.onlyTarget && pkg != cfg.targetPackage) {
            if (first) Diag.result = "other_app"
            return false
        }
        val macro = findMacro(cfg, ev)
        if (macro == null) {
            if (first) Diag.result = "no_macro"
            return false
        }
        if (first) start(macro, "k:${macro.trigger}")
        return true
    }

    private fun start(m: Macro, key: String) {
        Diag.arg = m.name
        val old = running[key]
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
        running[key] = scope.launch {
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
}
