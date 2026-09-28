package com.smac.macrobuilder

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.hypot

class Overlays(
    private val svc: AccessibilityService,
    private val onMacro: (Macro) -> Unit,
    private val onTouch: (Macro, Float, Float) -> Unit
) {
    private val wm = svc.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val pos = svc.getSharedPreferences("float_pos", Context.MODE_PRIVATE)
    private val views = mutableListOf<View>()
    private val slop = ViewConfiguration.get(svc).scaledTouchSlop
    private var cfg: Config? = null
    private var inTarget = false
    private var editMode = false
    private var menuOpen = false

    private fun px(dp: Int) = FloatUi.px(svc, dp)

    private fun t(en: String, id: String): String {
        val l = svc.getSharedPreferences("settings", Context.MODE_PRIVATE).getString("lang", "en")
        return if (l == "id") id else en
    }

    fun update(c: Config, target: Boolean) {
        cfg = c
        inTarget = target
        render()
    }

    fun setTarget(target: Boolean) {
        if (target != inTarget) {
            inTarget = target
            render()
        }
    }

    fun clear() {
        removeAll()
        cfg = null
        menuOpen = false
        editMode = false
    }

    private fun removeAll() {
        views.forEach { v ->
            try { wm.removeView(v) } catch (e: Exception) {}
        }
        views.clear()
    }

    private fun render() {
        removeAll()
        val c = cfg ?: return
        if (c.floatEnabled) {
            c.floats.forEachIndexed { i, m ->
                if (m.kind == "touch") {
                    if (inTarget) addTouch(m, i)
                } else if (!c.onlyTarget || inTarget) {
                    addMacro(m, i)
                }
            }
        }
        if (c.menuEnabled && inTarget) addHandle()
    }

    private fun params(w: Int, h: Int, x: Int, y: Int): WindowManager.LayoutParams {
        val lp = WindowManager.LayoutParams(
            w, h,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.TOP or Gravity.START
        lp.x = x
        lp.y = y
        if (Build.VERSION.SDK_INT >= 28) {
            lp.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        return lp
    }

    private fun add(v: View, lp: WindowManager.LayoutParams) {
        try {
            wm.addView(v, lp)
            views.add(v)
        } catch (e: Exception) {}
    }

    private fun save(kx: String, x: Int, ky: String, y: Int) {
        pos.edit().putInt(kx, x).putInt(ky, y).apply()
    }

    private fun bind(
        view: View,
        lp: WindowManager.LayoutParams,
        draggable: Boolean,
        onMoved: (Int, Int) -> Unit,
        onTap: () -> Unit
    ) {
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
                        if (draggable) {
                            val dx = e.rawX - sx
                            val dy = e.rawY - sy
                            if (!moved && hypot(dx, dy) > slop) moved = true
                            if (moved) {
                                lp.x = ox + dx.toInt()
                                lp.y = oy + dy.toInt()
                                try { wm.updateViewLayout(v, lp) } catch (ex: Exception) {}
                            }
                        }
                    }
                    MotionEvent.ACTION_UP -> {
                        v.scaleX = 1f
                        v.scaleY = 1f
                        if (moved) onMoved(lp.x, lp.y) else onTap()
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        v.scaleX = 1f
                        v.scaleY = 1f
                    }
                }
                return true
            }
        })
    }

    private fun addMacro(m: Macro, index: Int) {
        val size = px(m.size)
        val view = FloatUi.create(svc, m)
        val lp = params(
            size, size,
            pos.getInt("${m.id}_x", px(24)),
            pos.getInt("${m.id}_y", px(120) + index * (size + px(12)))
        )
        bind(view, lp, true, { x, y -> save("${m.id}_x", x, "${m.id}_y", y) }) { onMacro(m) }
        add(view, lp)
    }

    private fun markerSize() = px(48)

    private fun markerCenter(m: Macro, index: Int): Pair<Float, Float> {
        val ms = markerSize()
        val dm = svc.resources.displayMetrics
        val x = pos.getInt("${m.id}_mx", dm.widthPixels / 2 - ms / 2 + index * ms)
        val y = pos.getInt("${m.id}_my", dm.heightPixels / 2 - ms / 2)
        return (x + ms / 2f) to (y + ms / 2f)
    }

    private fun addTouch(m: Macro, index: Int) {
        val size = px(m.size)
        val trigger = FloatUi.create(svc, m)
        val tlp = params(
            size, size,
            pos.getInt("${m.id}_x", px(24)),
            pos.getInt("${m.id}_y", px(120) + index * (size + px(12)))
        )
        bind(trigger, tlp, editMode, { x, y -> save("${m.id}_x", x, "${m.id}_y", y) }) {
            if (!editMode) {
                val c = markerCenter(m, index)
                onTouch(m, c.first, c.second)
            }
        }
        add(trigger, tlp)
        if (editMode) addMarker(m, index)
    }

    private fun addMarker(m: Macro, index: Int) {
        val ms = markerSize()
        val marker = TextView(svc).apply {
            text = FloatUi.label(m)
            gravity = Gravity.CENTER
            includeFontPadding = false
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12f)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0x66000000)
                setStroke(px(3), FloatUi.color(svc))
            }
        }
        val c = markerCenter(m, index)
        val mlp = params(ms, ms, (c.first - ms / 2f).toInt(), (c.second - ms / 2f).toInt())
        bind(marker, mlp, true, { x, y -> save("${m.id}_mx", x, "${m.id}_my", y) }) {}
        add(marker, mlp)
    }

    private fun addHandle() {
        val size = px(44)
        val handle = TextView(svc).apply {
            text = "≡"
            gravity = Gravity.CENTER
            includeFontPadding = false
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, 22f)
            alpha = 0.9f
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(FloatUi.color(svc))
            }
        }
        val hlp = params(size, size, pos.getInt("menu_x", px(8)), pos.getInt("menu_y", px(8)))
        bind(handle, hlp, true, { x, y -> save("menu_x", x, "menu_y", y) }) {
            menuOpen = !menuOpen
            render()
        }
        add(handle, hlp)
        if (menuOpen) addPanel(hlp.x, hlp.y, size)
    }

    private fun addPanel(hx: Int, hy: Int, hs: Int) {
        val c = cfg ?: return
        val accent = FloatUi.color(svc)
        val soft = 0x33FFFFFF
        val col = LinearLayout(svc).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(12), px(12), px(12), px(12))
            background = GradientDrawable().apply {
                setColor(0xEE202124.toInt())
                cornerRadius = px(16).toFloat()
            }
        }

        fun rowParams() = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = px(8) }

        fun label(s: String, sp: Float, bold: Boolean = false): TextView = TextView(svc).apply {
            text = s
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
            if (bold) paint.isFakeBoldText = true
        }

        fun pill(s: String, fill: Int, onClick: () -> Unit): TextView = TextView(svc).apply {
            text = s
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setPadding(px(12), px(10), px(12), px(10))
            background = GradientDrawable().apply {
                setColor(fill)
                cornerRadius = px(20).toFloat()
            }
            setOnClickListener { onClick() }
            layoutParams = rowParams()
        }

        col.addView(label("beta", 16f, true))
        col.addView(pill(t("+ Touch button", "+ Tombol sentuh"), accent) { addTouchButton() })
        col.addView(
            pill(
                t("Edit layout", "Edit layout") + if (editMode) ": ON" else ": OFF",
                if (editMode) 0xFF2E7D32.toInt() else soft
            ) {
                editMode = !editMode
                render()
            }
        )

        c.floats.filter { it.kind == "touch" }.forEach { m ->
            val r = LinearLayout(svc).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = rowParams()
            }
            r.addView(
                label("${m.label.ifBlank { m.name }}  ·  ${m.taps}x", 14f),
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            )
            r.addView(TextView(svc).apply {
                text = "✕"
                setTextColor(Color.WHITE)
                setPadding(px(12), px(6), px(12), px(6))
                setOnClickListener { removeButton(m.id) }
            })
            col.addView(r)
        }

        if (editMode) {
            col.addView(
                label(
                    t(
                        "Drag each ring onto a game control, then turn Edit layout off.",
                        "Seret tiap cincin ke kontrol game, lalu matikan Edit layout."
                    ),
                    12f
                ).apply { layoutParams = rowParams() }
            )
        }
        col.addView(pill(t("Close", "Tutup"), soft) {
            menuOpen = false
            render()
        })

        val width = px(248)
        val x = hx.coerceAtMost(svc.resources.displayMetrics.widthPixels - width).coerceAtLeast(0)
        add(col, params(width, WindowManager.LayoutParams.WRAP_CONTENT, x, hy + hs + px(8)))
    }

    private fun addTouchButton() {
        val c = MacroStore.loadOrNull(svc) ?: return
        val n = c.floats.count { it.kind == "touch" } + 1
        if (n > 8) return
        c.floats.add(
            Macro(
                "Touch $n", "", false, 1, false, mutableListOf(),
                id = MacroStore.newId(), label = "T$n", kind = "touch"
            )
        )
        c.floatEnabled = true
        editMode = true
        MacroStore.save(svc, MacroStore.toJson(c))
    }

    private fun removeButton(id: String) {
        val c = MacroStore.loadOrNull(svc) ?: return
        c.floats.removeAll { it.id == id }
        MacroStore.save(svc, MacroStore.toJson(c))
    }
}
