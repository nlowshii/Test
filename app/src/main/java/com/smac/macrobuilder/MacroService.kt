package com.smac.macrobuilder

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Path
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
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
    private val overlays = mutableListOf<View>()
    private var foreground = ""
    private var floatCfg: Config? = null
    private var windowManager: WindowManager? = null

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "json") handler.post { rebuildFloats() }
    }
    private val evaluate = Runnable { syncFloats() }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Diag.connected = true
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
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
        handler.removeCallbacks(evaluate)
        MacroStore.prefs(this).unregisterOnSharedPreferenceChangeListener(prefsListener)
        removeFloats()
    }

    override fun onAccessibilityEvent(e: AccessibilityEvent) {
        if (e.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            e.packageName?.let { foreground = it.toString() }
            handler.removeCallbacks(evaluate)
            handler.postDelayed(evaluate, 300)
        }
    }

    override fun onInterrupt() {}

    private fun activePackage(): String {
        val p = try { rootInActiveWindow?.packageName?.toString() } catch (e: Exception) { null }
        return if (p.isNullOrEmpty()) foreground else p
    }

    private fun rebuildFloats() {
        removeFloats()
        floatCfg = MacroStore.load(this)
        syncFloats()
    }

    private fun syncFloats() {
        val cfg = floatCfg ?: return
        val show = cfg.floatEnabled && cfg.floats.isNotEmpty() &&
            (!cfg.onlyTarget || activePackage() == cfg.targetPackage)
        if (show && overlays.isEmpty()) {
            cfg.floats.forEachIndexed { i, m -> addFloat(m, i) }
        } else if (!show && overlays.isNotEmpty()) {
            removeFloats()
        }
    }

    private fun removeFloats() {
        val wm = windowManager
        overlays.forEach { v ->
            try { wm?.removeView(v) } catch (e: Exception) {}
        }
        overlays.clear()
    }

    private fun savePos(id: String, x: Int, y: Int) {
        getSharedPreferences("float_pos", MODE_PRIVATE).edit()
            .putInt("${id}_x", x).putInt("${id}_y", y).apply()
    }

    private fun addFloat(m: Macro, index: Int) {
        val wm = windowManager ?: return
        val view = FloatUi.create(this, m)
        val size = FloatUi.px(this, m.size)
        val pos = getSharedPreferences("float_pos", MODE_PRIVATE)
        val lp = WindowManager.LayoutParams(
            size, size,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.TOP or Gravity.START
        lp.x = pos.getInt("${m.id}_x", FloatUi.px(this, 24))
        lp.y = pos.getInt("${m.id}_y", FloatUi.px(this, 120) + index * (size + FloatUi.px(this, 12)))
        val slop = ViewConfiguration.get(this).scaledTouchSlop

        view.setOnTouchListener(object : View.OnTouchListener {
            private var sx = 0f
            private var sy = 0f
            private var ox = 0
            private var oy = 0
            private var moved = false

            override fun onTouch(v: View, e: MotionEvent): Boolean {
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        sx = e.rawX
                        sy = e.rawY
                        ox = lp.x
                        oy = lp.y
                        moved = false
                        v.scaleX = 0.92f
                        v.scaleY = 0.92f
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = e.rawX - sx
                        val dy = e.rawY - sy
                        if (!moved && Math.hypot(dx.toDouble(), dy.toDouble()) > slop) moved = true
                        if (moved) {
                            lp.x = ox + dx.toInt()
                            lp.y = oy + dy.toInt()
                            wm.updateViewLayout(v, lp)
                        }
                    }
                    MotionEvent.ACTION_UP -> {
                        v.scaleX = 1f
                        v.scaleY = 1f
                        if (moved) savePos(m.id, lp.x, lp.y) else fire(m)
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        v.scaleX = 1f
                        v.scaleY = 1f
                    }
                }
                return true
            }
        })

        try {
            wm.addView(view, lp)
            overlays.add(view)
        } catch (e: Exception) {}
    }

    private fun fire(m: Macro) {
        Diag.lastKey = "floating: ${m.name}"
        start(m, "f:${m.id}")
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
