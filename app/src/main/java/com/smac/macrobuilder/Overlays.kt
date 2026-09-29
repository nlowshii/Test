package com.smac.macrobuilder

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import java.io.File
import kotlin.math.hypot

class Overlays(
    private val svc: AccessibilityService,
    private val onMacro: (Macro) -> Unit,
    private val onTouch: (Macro, Float, Float) -> Unit
) {
    private val wm = svc.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val pos = svc.getSharedPreferences("float_pos", Context.MODE_PRIVATE)
    private val handler = Handler(Looper.getMainLooper())
    private val slop = ViewConfiguration.get(svc).scaledTouchSlop
    private val buttonViews = mutableListOf<View>()
    private val menuViews = mutableListOf<View>()
    private val match = ViewGroup.LayoutParams.MATCH_PARENT
    private val wrap = ViewGroup.LayoutParams.WRAP_CONTENT
    private val blue = 0xFF3D5AFE.toInt()
    private val red = 0xFFFF6B6B.toInt()
    private val green = 0xFF7CE38B.toInt()
    private val panelBg = 0xF0141821.toInt()
    private val rowBg = 0x22FFFFFF
    private val rowSel = 0x553D5AFE
    private val soft = 0x33FFFFFF
    private val dim = 0xBBFFFFFF.toInt()

    private var cfg: Config? = null
    private var inTarget = false
    private var editMode = false
    private var menuOpen = false
    private var current = "menu"
    private var dirty = false

    var lastJson = ""
        private set

    private var handleView: TextView? = null
    private var handleLp: WindowManager.LayoutParams? = null
    private var listBox: LinearLayout? = null
    private var settingsBox: LinearLayout? = null
    private var statusView: TextView? = null

    private val persistTask = Runnable { persist() }
    private val clearStatus = Runnable { statusView?.text = "" }

    private fun px(dp: Int) = FloatUi.px(svc, dp)

    private fun t(en: String, id: String): String {
        val l = svc.getSharedPreferences("settings", Context.MODE_PRIVATE).getString("lang", "en")
        return if (l == "id") id else en
    }

    fun update(c: Config, target: Boolean, json: String) {
        if (dirty) {
            handler.removeCallbacks(persistTask)
            dirty = false
        }
        cfg = c
        lastJson = json
        inTarget = target
        renderButtons()
        renderMenu()
    }

    fun setTarget(target: Boolean) {
        if (target != inTarget) {
            inTarget = target
            renderButtons()
            renderMenu()
        }
    }

    fun clear() {
        handler.removeCallbacks(persistTask)
        if (dirty) persist()
        removeViews(buttonViews)
        removeViews(menuViews)
        cfg = null
        menuOpen = false
        editMode = false
    }

    private fun removeViews(list: MutableList<View>) {
        list.forEach { v ->
            try { wm.removeView(v) } catch (e: Exception) {}
        }
        list.clear()
    }

    private fun add(v: View, lp: WindowManager.LayoutParams, list: MutableList<View>) {
        try {
            wm.addView(v, lp)
            list.add(v)
        } catch (e: Exception) {}
    }

    private fun changed() {
        dirty = true
        handler.removeCallbacks(persistTask)
        handler.postDelayed(persistTask, 400)
    }

    private fun persist() {
        val c = cfg ?: return
        handler.removeCallbacks(persistTask)
        dirty = false
        val json = MacroStore.toJson(c)
        lastJson = json
        MacroStore.save(svc, json)
        statusView?.text = t("Saved", "Tersimpan")
        handler.removeCallbacks(clearStatus)
        handler.postDelayed(clearStatus, 1500)
    }

    private fun savePos(kx: String, x: Int, ky: String, y: Int) {
        pos.edit().putInt(kx, x).putInt(ky, y).apply()
    }

    private fun params(w: Int, h: Int, x: Int, y: Int): WindowManager.LayoutParams {
        val lp = WindowManager.LayoutParams(
            w, h,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_SPLIT_TOUCH or
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

    private fun renderButtons() {
        removeViews(buttonViews)
        val c = cfg ?: return
        if (!c.floatEnabled) return
        c.floats.forEachIndexed { i, m ->
            if (!m.enabled) return@forEachIndexed
            if (m.kind == "touch") {
                if (inTarget) addTouch(m, i)
            } else if (!c.onlyTarget || inTarget) {
                addMacro(m, i)
            }
        }
    }

    private fun addMacro(m: Macro, index: Int) {
        val size = px(m.size)
        val view = FloatUi.create(svc, m)
        val lp = params(
            size, size,
            pos.getInt("${m.id}_x", px(24)),
            pos.getInt("${m.id}_y", px(120) + index * (size + px(12)))
        )
        bind(view, lp, true, { x, y -> savePos("${m.id}_x", x, "${m.id}_y", y) }) { onMacro(m) }
        add(view, lp, buttonViews)
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
        bind(trigger, tlp, editMode, { x, y -> savePos("${m.id}_x", x, "${m.id}_y", y) }) {
            if (!editMode) {
                val c = markerCenter(m, index)
                onTouch(m, c.first, c.second)
            }
        }
        add(trigger, tlp, buttonViews)
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
                setStroke(px(3), blue)
            }
        }
        val c = markerCenter(m, index)
        val mlp = params(ms, ms, (c.first - ms / 2f).toInt(), (c.second - ms / 2f).toInt())
        bind(marker, mlp, true, { x, y -> savePos("${m.id}_mx", x, "${m.id}_my", y) }) {}
        add(marker, mlp, buttonViews)
    }

    private fun renderMenu() {
        removeViews(menuViews)
        handleView = null
        handleLp = null
        listBox = null
        settingsBox = null
        statusView = null
        val c = cfg ?: return
        if (!c.menuEnabled || !inTarget) return
        addHandle(c)
        if (menuOpen) addPanel()
    }

    private fun applyHandleStyle(v: TextView, lp: WindowManager.LayoutParams, c: Config) {
        val size = px(c.menuSize)
        lp.width = size
        lp.height = size
        v.background = GradientDrawable().apply {
            setColor(blue)
            cornerRadius = size / 2f * c.menuRadius / 100f
        }
        v.alpha = c.menuOpacity / 100f
        v.setTextSize(TypedValue.COMPLEX_UNIT_DIP, c.menuSize * 0.5f)
    }

    private fun styleHandle() {
        val v = handleView ?: return
        val lp = handleLp ?: return
        val c = cfg ?: return
        applyHandleStyle(v, lp, c)
        try { wm.updateViewLayout(v, lp) } catch (e: Exception) {}
    }

    private fun addHandle(c: Config) {
        val v = TextView(svc).apply {
            text = "≡"
            gravity = Gravity.CENTER
            includeFontPadding = false
            setTextColor(Color.WHITE)
        }
        val lp = params(px(c.menuSize), px(c.menuSize), pos.getInt("menu_x", px(8)), pos.getInt("menu_y", px(8)))
        applyHandleStyle(v, lp, c)
        handleView = v
        handleLp = lp
        bind(v, lp, true, { x, y -> savePos("menu_x", x, "menu_y", y) }) {
            menuOpen = !menuOpen
            renderMenu()
        }
        add(v, lp, menuViews)
    }

    private fun lin(w: Int, h: Int, weight: Float = 0f, top: Int = 0, start: Int = 0) =
        LinearLayout.LayoutParams(w, h, weight).apply {
            topMargin = px(top)
            marginStart = px(start)
        }

    private fun rr(fill: Int, radius: Int, stroke: Int = 0, strokeW: Int = 0): GradientDrawable =
        GradientDrawable().apply {
            setColor(fill)
            cornerRadius = px(radius).toFloat()
            if (strokeW > 0) setStroke(px(strokeW), stroke)
        }

    private fun tv(s: String, sp: Float = 13f, color: Int = Color.WHITE, bold: Boolean = false): TextView =
        TextView(svc).apply {
            text = s
            setTextColor(color)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
            if (bold) paint.isFakeBoldText = true
        }

    private fun pill(s: String, fill: Int, onClick: () -> Unit): TextView = tv(s, 13f).apply {
        gravity = Gravity.CENTER
        setPadding(px(12), px(8), px(12), px(8))
        background = rr(fill, 16)
        setOnClickListener { onClick() }
    }

    private fun toggle(on: Boolean, onChange: (Boolean) -> Unit): View {
        val track = FrameLayout(svc)
        val thumb = View(svc)
        val tp = FrameLayout.LayoutParams(px(14), px(14)).apply { setMargins(px(3), 0, px(3), 0) }
        track.addView(thumb, tp)
        var state = on
        fun paint() {
            track.background = rr(if (state) blue else 0x55FFFFFF, 10)
            thumb.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.WHITE)
            }
            tp.gravity = (if (state) Gravity.END else Gravity.START) or Gravity.CENTER_VERTICAL
            thumb.layoutParams = tp
        }
        paint()
        track.setOnClickListener {
            state = !state
            paint()
            onChange(state)
        }
        return track
    }

    private fun sliderRow(
        title: String,
        lo: Int,
        hi: Int,
        step: Int,
        value: Int,
        onChange: (Int) -> Unit
    ): View {
        val box = LinearLayout(svc).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = lin(match, wrap, top = 8)
        }
        box.addView(tv(title, 12f, dim))
        val start = (value.coerceIn(lo, hi) - lo) / step
        val valueBox = tv((lo + start * step).toString(), 12f).apply {
            gravity = Gravity.CENTER
            setPadding(px(8), px(4), px(8), px(4))
            minWidth = px(44)
            background = rr(0x22000000, 6, soft, 1)
        }
        val bar = SeekBar(svc).apply {
            this.max = (hi - lo) / step
            progress = start
            progressTintList = ColorStateList.valueOf(blue)
            thumbTintList = ColorStateList.valueOf(blue)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                    val v = lo + p * step
                    valueBox.text = v.toString()
                    if (fromUser) onChange(v)
                }

                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        }
        val line = LinearLayout(svc).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        line.addView(bar, lin(0, wrap, weight = 1f))
        line.addView(valueBox, lin(wrap, wrap, start = 8))
        box.addView(line)
        return box
    }

    private fun swatch(fill: Int, chosen: Boolean, caption: String, onClick: () -> Unit): View {
        val v = TextView(svc).apply {
            text = caption
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(fill)
                setStroke(px(if (chosen) 3 else 1), if (chosen) Color.WHITE else 0x66FFFFFF)
            }
            setOnClickListener { onClick() }
        }
        v.layoutParams = LinearLayout.LayoutParams(px(28), px(28)).apply {
            marginEnd = px(6)
            topMargin = px(6)
        }
        return v
    }

    private fun header(
        title: String,
        enabled: Boolean?,
        onEnabled: (Boolean) -> Unit,
        onReset: () -> Unit
    ): View {
        val r = LinearLayout(svc).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        r.addView(tv("Reset", 12f, red).apply {
            setPadding(px(10), px(5), px(10), px(5))
            background = rr(0, 8, red, 1)
            setOnClickListener { onReset() }
        })
        if (enabled != null) r.addView(toggle(enabled, onEnabled), lin(px(38), px(20), start = 8))
        r.addView(tv(title, 14f, Color.WHITE, true), lin(0, wrap, weight = 1f, start = 8))
        return r
    }

    private fun moduleRow(id: String, name: String, enabled: Boolean?, onToggle: (Boolean) -> Unit): View {
        val r = LinearLayout(svc).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(px(10), px(9), px(10), px(9))
            background = rr(if (current == id) rowSel else rowBg, 12)
            setOnClickListener {
                current = id
                fillList()
                fillSettings()
            }
        }
        if (enabled != null) r.addView(toggle(enabled, onToggle), lin(px(38), px(20)))
        r.addView(tv(name, 13f), lin(0, wrap, weight = 1f, start = if (enabled != null) 8 else 0))
        r.addView(tv("›", 18f, 0x99FFFFFF.toInt()))
        return r
    }

    private fun addPanel() {
        val dm = svc.resources.displayMetrics
        val width = minOf(dm.widthPixels - px(24), px(640))
        val height = minOf(dm.heightPixels - px(24), px(300))
        val root = LinearLayout(svc).apply {
            orientation = LinearLayout.VERTICAL
            background = rr(panelBg, 18, soft, 1)
            setPadding(px(12), px(10), px(12), px(12))
        }

        val head = LinearLayout(svc).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        head.addView(tv("◆", 16f, blue, true))
        head.addView(tv(svc.getString(R.string.app_name), 15f, Color.WHITE, true), lin(wrap, wrap, start = 6))
        val status = tv("", 11f, green)
        statusView = status
        head.addView(status, lin(0, wrap, weight = 1f, start = 10))
        head.addView(tv(t("Edit layout", "Edit layout"), 12f, dim), lin(wrap, wrap))
        head.addView(toggle(editMode) {
            editMode = it
            renderButtons()
        }, lin(px(38), px(20), start = 6))
        head.addView(pill(t("+ New button", "+ Tombol baru"), blue) { addTouchButton() }, lin(wrap, wrap, start = 10))
        head.addView(tv("✕", 16f).apply {
            setPadding(px(12), px(4), px(4), px(4))
            setOnClickListener {
                menuOpen = false
                renderMenu()
            }
        })
        root.addView(head, lin(match, wrap))

        val body = LinearLayout(svc).apply { orientation = LinearLayout.HORIZONTAL }
        val left = ScrollView(svc)
        val lbox = LinearLayout(svc).apply { orientation = LinearLayout.VERTICAL }
        left.addView(lbox)
        val right = ScrollView(svc)
        val rbox = LinearLayout(svc).apply { orientation = LinearLayout.VERTICAL }
        right.addView(rbox)
        listBox = lbox
        settingsBox = rbox
        body.addView(left, lin(0, match, weight = 11f))
        body.addView(View(svc).apply { setBackgroundColor(soft) }, lin(px(1), match, start = 8))
        body.addView(right, lin(0, match, weight = 9f, start = 10))
        root.addView(body, lin(match, 0, weight = 1f, top = 8))

        fillList()
        fillSettings()

        add(
            root,
            params(width, height, (dm.widthPixels - width) / 2, (dm.heightPixels - height) / 2),
            menuViews
        )
    }

    private fun fillList() {
        val box = listBox ?: return
        val c = cfg ?: return
        box.removeAllViews()
        val rows = mutableListOf<View>()
        rows.add(moduleRow("menu", t("Mod menu icon", "Ikon mod menu"), null) {})
        c.floats.forEach { m ->
            rows.add(moduleRow(m.id, m.name.ifBlank { m.label }, m.enabled) { on ->
                m.enabled = on
                renderButtons()
                fillSettings()
                changed()
            })
        }
        rows.chunked(2).forEach { pair ->
            val line = LinearLayout(svc).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = lin(match, wrap, top = 6)
            }
            pair.forEachIndexed { i, v -> line.addView(v, lin(0, wrap, weight = 1f, start = if (i == 0) 0 else 6)) }
            if (pair.size == 1) line.addView(View(svc), lin(0, 1, weight = 1f, start = 6))
            box.addView(line)
        }
    }

    private fun fillSettings() {
        val box = settingsBox ?: return
        val c = cfg ?: return
        box.removeAllViews()
        val m = c.floats.firstOrNull { it.id == current }
        if (m == null) {
            current = "menu"
            menuSettings(box, c)
        } else {
            floatSettings(box, m)
        }
    }

    private fun menuSettings(box: LinearLayout, c: Config) {
        box.addView(header(t("Mod menu icon", "Ikon mod menu"), null, {}) {
            c.menuSize = 44
            c.menuOpacity = 90
            c.menuRadius = 100
            styleHandle()
            fillSettings()
            changed()
        })
        box.addView(sliderRow(t("Size", "Ukuran"), 32, 80, 4, c.menuSize) {
            c.menuSize = it
            styleHandle()
            changed()
        })
        box.addView(sliderRow(t("Opacity", "Opasitas"), 20, 100, 5, c.menuOpacity) {
            c.menuOpacity = it
            styleHandle()
            changed()
        })
        box.addView(sliderRow(t("Roundness", "Kebulatan"), 0, 100, 5, c.menuRadius) {
            c.menuRadius = it
            styleHandle()
            changed()
        })
    }

    private fun resetFloat(m: Macro) {
        m.size = 56
        m.opacity = 90
        m.radius = 100
        m.bgColor = 0
        m.bgImage = false
        if (m.kind == "touch") {
            m.taps = 3
            m.interval = 60
            m.hold = false
            m.holdMs = 400
        }
        renderButtons()
        fillSettings()
        changed()
    }

    private fun floatSettings(box: LinearLayout, m: Macro) {
        box.addView(header(m.name.ifBlank { m.label }, m.enabled, { on ->
            m.enabled = on
            renderButtons()
            fillList()
            changed()
        }) { resetFloat(m) })

        box.addView(sliderRow(t("Size", "Ukuran"), 32, 160, 4, m.size) {
            m.size = it
            renderButtons()
            changed()
        })
        box.addView(sliderRow(t("Opacity", "Opasitas"), 20, 100, 5, m.opacity) {
            m.opacity = it
            renderButtons()
            changed()
        })
        box.addView(sliderRow(t("Roundness", "Kebulatan"), 0, 100, 5, m.radius) {
            m.radius = it
            renderButtons()
            changed()
        })

        box.addView(tv(t("Background", "Latar belakang"), 12f, dim).apply { layoutParams = lin(match, wrap, top = 10) })
        FloatUi.PALETTE.chunked(6).forEach { chunk ->
            val line = LinearLayout(svc).apply { orientation = LinearLayout.HORIZONTAL }
            chunk.forEach { c ->
                val fill = if (c == 0) FloatUi.accent(svc) else c
                line.addView(swatch(fill, !m.bgImage && m.bgColor == c, "") {
                    m.bgColor = c
                    m.bgImage = false
                    renderButtons()
                    fillSettings()
                    changed()
                })
            }
            box.addView(line)
        }
        if (FloatUi.hasImage(svc, m)) {
            val line = LinearLayout(svc).apply { orientation = LinearLayout.HORIZONTAL }
            line.addView(swatch(0xFF555555.toInt(), m.bgImage, "IMG") {
                m.bgImage = true
                renderButtons()
                fillSettings()
                changed()
            })
            box.addView(line)
        }
        box.addView(sliderRow(t("Custom hue", "Warna kustom"), 0, 360, 10, 0) {
            m.bgColor = Color.HSVToColor(floatArrayOf(it.toFloat(), 0.65f, 0.85f))
            m.bgImage = false
            renderButtons()
            changed()
        })

        if (m.kind == "touch") {
            box.addView(sliderRow(t("Taps per press", "Jumlah tap per tekan"), 1, 10, 1, m.taps) {
                m.taps = it
                changed()
            })
            box.addView(sliderRow(t("Interval between taps", "Jeda antar tap"), 40, 500, 10, m.interval) {
                m.interval = it
                changed()
            })
            val holdRow = LinearLayout(svc).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = lin(match, wrap, top = 10)
            }
            holdRow.addView(tv(t("Hold instead of tap", "Tahan, bukan tap"), 13f), lin(0, wrap, weight = 1f))
            holdRow.addView(toggle(m.hold) {
                m.hold = it
                changed()
            }, lin(px(38), px(20)))
            box.addView(holdRow)
            box.addView(sliderRow(t("Hold duration", "Lama tahan"), 100, 5000, 100, m.holdMs) {
                m.holdMs = it
                changed()
            })
        } else {
            box.addView(
                tv(t("Edit the actions of this button in the app.", "Ubah aksi tombol ini di aplikasi."), 12f, 0x99FFFFFF.toInt())
                    .apply { layoutParams = lin(match, wrap, top = 10) }
            )
        }

        box.addView(pill(t("Delete button", "Hapus tombol"), 0x55FF4D4D) { removeButton(m.id) }
            .apply { layoutParams = lin(match, wrap, top = 14) })
    }

    private fun addTouchButton() {
        val c = cfg ?: return
        if (c.floats.size >= 8) return
        val n = c.floats.count { it.kind == "touch" } + 1
        val m = Macro(
            "Touch $n", "", false, 1, false, mutableListOf(),
            id = MacroStore.newId(), label = "T$n", kind = "touch"
        )
        c.floats.add(m)
        c.floatEnabled = true
        editMode = true
        current = m.id
        renderButtons()
        renderMenu()
        persist()
    }

    private fun removeButton(id: String) {
        val c = cfg ?: return
        c.floats.removeAll { it.id == id }
        File(svc.filesDir, "bg_$id.png").delete()
        current = "menu"
        renderButtons()
        fillList()
        fillSettings()
        persist()
    }
}
