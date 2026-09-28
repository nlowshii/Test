package com.smac.macrobuilder

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import rikka.shizuku.Shizuku
import com.google.android.material.R as MR

class MainActivity : AppCompatActivity() {

    private lateinit var cfg: Config
    private var cur = 0
    private var loading = false
    private var lang = "en"

    private lateinit var onlySw: MaterialSwitch
    private lateinit var pkgEt: TextInputEditText
    private lateinit var macroAc: MaterialAutoCompleteTextView
    private lateinit var nameEt: TextInputEditText
    private lateinit var hotkeyBtn: MaterialButton
    private lateinit var toggleSw: MaterialSwitch
    private lateinit var togetherSw: MaterialSwitch
    private lateinit var repeatEt: TextInputEditText
    private lateinit var actionsBox: LinearLayout
    private lateinit var addBtn: MaterialButton
    private lateinit var statusText: TextView

    private val types = listOf("tap", "long_press", "swipe", "delay", "key")
    private val match = ViewGroup.LayoutParams.MATCH_PARENT
    private val wrap = ViewGroup.LayoutParams.WRAP_CONTENT
    private val handler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            refreshStatus()
            handler.postDelayed(this, 1000)
        }
    }
    private val permListener = Shizuku.OnRequestPermissionResultListener { _, _ -> refreshStatus() }

    private val macro: Macro get() = cfg.macros[cur]

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()
    private fun t(en: String, id: String) = if (lang == "id") id else en
    private fun typeLabels() = listOf("Tap", "Long Press", "Swipe", "Delay", t("Key / Mouse", "Tombol / Mouse"))
    private fun lp(top: Int = 0) = LinearLayout.LayoutParams(match, wrap).apply { topMargin = dp(top) }
    private fun w(m: Int = 4) = LinearLayout.LayoutParams(0, wrap, 1f).apply {
        marginStart = dp(m)
        marginEnd = dp(m)
    }

    private fun EditText.onText(f: (String) -> Unit) {
        addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { if (!loading) f(s.toString()) }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })
    }

    private fun newMacro() =
        Macro("Macro ${cfg.macros.size + 1}", "", false, 1, false, mutableListOf())

    private fun newAction() = MacroAction("key", ms = 150, key = KeyEvent.KEYCODE_C)

    private fun btn(text: String, outlined: Boolean = false, onClick: () -> Unit): MaterialButton {
        val b = if (outlined) MaterialButton(this, null, MR.attr.materialButtonOutlinedStyle)
        else MaterialButton(this)
        b.text = text
        b.setOnClickListener { onClick() }
        b.layoutParams = lp(8)
        return b
    }

    private fun card(): Pair<MaterialCardView, LinearLayout> {
        val c = MaterialCardView(this)
        c.radius = dp(16).toFloat()
        c.cardElevation = 0f
        c.strokeWidth = 0
        c.setCardBackgroundColor(MaterialColors.getColor(this, MR.attr.colorSurfaceContainerHigh, 0))
        c.layoutParams = lp(12)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(16))
        }
        c.addView(box)
        return c to box
    }

    private fun row() = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        layoutParams = lp(8)
    }

    private fun field(
        hint: String,
        number: Boolean = false,
        onChange: (String) -> Unit
    ): Pair<TextInputLayout, TextInputEditText> {
        val til = TextInputLayout(this)
        til.hint = hint
        val et = TextInputEditText(til.context)
        if (number) et.inputType = InputType.TYPE_CLASS_NUMBER else et.setSingleLine()
        til.addView(et, LinearLayout.LayoutParams(match, wrap))
        et.onText(onChange)
        til.layoutParams = lp(8)
        return til to et
    }

    private fun dropdown(hint: String): Pair<TextInputLayout, MaterialAutoCompleteTextView> {
        val til = TextInputLayout(this, null, MR.attr.textInputOutlinedExposedDropdownMenuStyle)
        til.hint = hint
        val ac = MaterialAutoCompleteTextView(til.context)
        ac.inputType = InputType.TYPE_NULL
        til.addView(ac, LinearLayout.LayoutParams(match, wrap))
        return til to ac
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lang = getSharedPreferences("settings", MODE_PRIVATE).getString("lang", "en") ?: "en"
        cfg = draft ?: try {
            MacroStore.parse(MacroStore.raw(this))
        } catch (e: Exception) {
            MacroStore.parse(MacroStore.DEFAULT_JSON)
        }
        draft = null
        if (cfg.macros.isEmpty()) cfg.macros.add(newMacro())

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(32))
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(TextView(this).apply {
            text = "Simple Macro"
            setTextAppearance(MR.style.TextAppearance_Material3_HeadlineSmall)
        }, LinearLayout.LayoutParams(0, wrap, 1f))
        header.addView(MaterialButton(this, null, MR.attr.materialIconButtonStyle).apply {
            setIconResource(R.drawable.ic_settings)
            contentDescription = t("Settings", "Pengaturan")
            setOnClickListener { showSettings() }
        }, LinearLayout.LayoutParams(wrap, wrap))
        root.addView(header)

        val (setupCard, setupBox) = card()
        setupBox.addView(TextView(this).apply {
            text = t(
                "1) Enable the accessibility service.\n2) Key and mouse actions need Shizuku running and granted.\n3) Set up a macro and hotkey, then Save.\n4) Open the target app and press the hotkey.",
                "1) Aktifkan layanan aksesibilitas.\n2) Aksi tombol dan mouse membutuhkan Shizuku yang berjalan dan diizinkan.\n3) Atur macro dan hotkey, lalu Simpan.\n4) Buka aplikasi target dan tekan hotkey."
            )
        })
        setupBox.addView(btn(t("Open accessibility settings", "Buka pengaturan aksesibilitas"), true) {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })
        statusText = TextView(this).apply { setPadding(0, dp(12), 0, 0) }
        setupBox.addView(statusText)
        setupBox.addView(btn(t("Grant Shizuku access", "Beri akses Shizuku"), true) { requestShizuku() })
        root.addView(setupCard)

        onlySw = MaterialSwitch(this).apply {
            text = t("Only active in the target app", "Hanya aktif di aplikasi target")
            layoutParams = lp(12)
            setOnCheckedChangeListener { _, c -> if (!loading) cfg.onlyTarget = c }
        }
        root.addView(onlySw)

        val (pkgTil, pk) = field(t("Target app package", "Paket aplikasi target")) { cfg.targetPackage = it.trim() }
        pkgEt = pk
        root.addView(pkgTil)

        val (macroTil, ac) = dropdown("Macro")
        macroAc = ac
        macroAc.setOnItemClickListener { _, _, pos, _ -> if (pos != cur) { cur = pos; refresh() } }
        val macroRow = row()
        macroRow.layoutParams = lp(16)
        macroRow.addView(macroTil, w(0))
        macroRow.addView(btn("+", true) {
            if (cfg.macros.size >= 5) {
                toast(t("Maximum 5 macros", "Maksimal 5 macro"))
            } else {
                cfg.macros.add(newMacro())
                cur = cfg.macros.size - 1
                refresh()
            }
        }, LinearLayout.LayoutParams(wrap, wrap).apply { marginStart = dp(8) })
        macroRow.addView(btn(t("Delete", "Hapus"), true) {
            if (cfg.macros.size <= 1) {
                toast(t("At least 1 macro is required", "Minimal 1 macro"))
            } else {
                cfg.macros.removeAt(cur)
                cur = minOf(cur, cfg.macros.size - 1)
                refresh()
            }
        }, LinearLayout.LayoutParams(wrap, wrap).apply { marginStart = dp(8) })
        root.addView(macroRow)

        val (nameTil, name) = field(t("Macro name", "Nama macro")) { macro.name = it }
        nameEt = name
        root.addView(nameTil)

        hotkeyBtn = btn("") {
            capture(
                t("Press the hotkey", "Tekan hotkey"),
                t("Press a key on the keyboard, optionally with Ctrl/Alt/Shift, e.g. F6 or Ctrl+F7.",
                    "Tekan tombol di keyboard, boleh dengan Ctrl/Alt/Shift, mis. F6 atau Ctrl+F7."),
                { macro.trigger = MacroStore.combo(it); refreshHotkey() }
            )
        }
        root.addView(hotkeyBtn)

        toggleSw = MaterialSwitch(this).apply {
            text = t("Toggle (press to start, press again to stop)", "Toggle (tekan untuk mulai, tekan lagi untuk berhenti)")
            layoutParams = lp(8)
            setOnCheckedChangeListener { _, c -> if (!loading) macro.toggle = c }
        }
        root.addView(toggleSw)

        togetherSw = MaterialSwitch(this).apply {
            text = t("Press key actions together", "Tekan aksi tombol bersamaan")
            layoutParams = lp(4)
            setOnCheckedChangeListener { _, c -> if (!loading) macro.together = c }
        }
        root.addView(togetherSw)

        val (repeatTil, rep) = field(t("Repeat (0 = endless, toggle only)", "Ulangi (0 = tanpa henti, hanya toggle)"), true) {
            macro.repeat = it.toIntOrNull() ?: 1
        }
        repeatEt = rep
        root.addView(repeatTil)

        root.addView(TextView(this).apply {
            text = t("Actions (max 10). Slider left = faster.", "Aksi (maks 10). Slider ke kiri = lebih cepat.")
            setTextAppearance(MR.style.TextAppearance_Material3_TitleMedium)
            setPadding(0, dp(20), 0, 0)
        })
        actionsBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(actionsBox)

        addBtn = btn("") {
            if (macro.actions.size < 10) {
                macro.actions.add(newAction())
                rebuildActions()
            }
        }
        root.addView(addBtn)

        root.addView(btn(t("Save", "Simpan")) { save() })
        val io = row()
        io.addView(btn(t("Export JSON", "Ekspor JSON"), true) {
            val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("macro", MacroStore.toJson(cfg)))
            toast(t("JSON copied to clipboard", "JSON disalin ke clipboard"))
        }, w())
        io.addView(btn(t("Import JSON", "Impor JSON"), true) {
            try {
                val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                val text = cm.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
                val c = MacroStore.parse(text)
                if (c.macros.isEmpty()) throw IllegalArgumentException()
                cfg = c
                cur = 0
                refresh()
                toast(t("Imported, remember to Save", "Diimpor, jangan lupa Simpan"))
            } catch (e: Exception) {
                toast(t("Clipboard is not a valid macro JSON", "Clipboard bukan JSON macro yang valid"))
            }
        }, w())
        root.addView(io)

        val scroll = ScrollView(this).apply {
            addView(root)
            clipToPadding = false
        }
        ViewCompat.setOnApplyWindowInsetsListener(scroll) { v, ins ->
            val b = ins.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(b.left, b.top, b.right, b.bottom)
            ins
        }
        setContentView(scroll)

        try { Shizuku.addRequestPermissionResultListener(permListener) } catch (e: Throwable) {}
        refresh()
    }

    override fun onResume() {
        super.onResume()
        handler.post(tick)
    }

    override fun onPause() {
        handler.removeCallbacks(tick)
        super.onPause()
    }

    override fun onDestroy() {
        try { Shizuku.removeRequestPermissionResultListener(permListener) } catch (e: Throwable) {}
        super.onDestroy()
    }

    private fun showSettings() {
        val langs = arrayOf("English", "Bahasa Indonesia")
        MaterialAlertDialogBuilder(this)
            .setTitle(t("Language", "Bahasa"))
            .setSingleChoiceItems(langs, if (lang == "id") 1 else 0) { d, which ->
                val n = if (which == 1) "id" else "en"
                d.dismiss()
                if (n != lang) {
                    getSharedPreferences("settings", MODE_PRIVATE).edit().putString("lang", n).apply()
                    draft = cfg
                    recreate()
                }
            }
            .setNegativeButton(t("Close", "Tutup"), null)
            .show()
    }

    private fun refreshStatus() {
        val running = try { Shizuku.pingBinder() } catch (e: Throwable) { false }
        val svc = if (Diag.connected) t("running", "berjalan") else t("not running", "tidak berjalan")
        val shz = when {
            KeyInjector.ready() -> t("ready", "siap")
            running -> t("running, access not granted", "berjalan, akses belum diberikan")
            else -> t("not running", "tidak berjalan")
        }
        val res = when (Diag.result) {
            "started" -> t("started: ${Diag.arg}", "dimulai: ${Diag.arg}")
            "stopped" -> t("stopped: ${Diag.arg}", "dihentikan: ${Diag.arg}")
            "busy" -> t("already running: ${Diag.arg}", "sedang berjalan: ${Diag.arg}")
            "other_app" -> t("ignored, foreground app is not the target", "diabaikan, aplikasi di depan bukan target")
            "no_macro" -> t("no macro uses this hotkey", "tidak ada macro dengan hotkey ini")
            else -> "-"
        }
        val inj = when (Diag.lastInject) {
            "ok" -> t("ok", "berhasil")
            "failed" -> t("failed", "gagal")
            "unavailable" -> t("Shizuku unavailable", "Shizuku tidak tersedia")
            else -> "-"
        }
        statusText.text = listOf(
            t("Accessibility service: $svc", "Layanan aksesibilitas: $svc"),
            "Shizuku: $shz",
            t("Last key: ${Diag.lastKey}", "Tombol terakhir: ${Diag.lastKey}"),
            t("Result: $res", "Hasil: $res"),
            t("Last injection: $inj", "Injeksi terakhir: $inj")
        ).joinToString("\n")
    }

    private fun requestShizuku() {
        val running = try { Shizuku.pingBinder() } catch (e: Throwable) { false }
        when {
            !running -> toast(t("Start Shizuku first", "Jalankan Shizuku terlebih dahulu"))
            KeyInjector.ready() -> toast(t("Access already granted", "Akses sudah diberikan"))
            else -> try {
                Shizuku.requestPermission(1)
            } catch (e: Throwable) {
                toast(t("Could not request access", "Gagal meminta akses"))
            }
        }
    }

    private fun refresh() {
        loading = true
        onlySw.isChecked = cfg.onlyTarget
        pkgEt.setText(cfg.targetPackage)
        val names = cfg.macros.mapIndexed { i, m -> "${i + 1}. ${m.name}" }
        macroAc.setSimpleItems(names.toTypedArray())
        macroAc.setText(names[cur], false)
        nameEt.setText(macro.name)
        toggleSw.isChecked = macro.toggle
        togetherSw.isChecked = macro.together
        repeatEt.setText(macro.repeat.toString())
        loading = false
        refreshHotkey()
        rebuildActions()
    }

    private fun refreshHotkey() {
        hotkeyBtn.text = if (macro.trigger.isBlank()) t("Hotkey: not set (tap to set)", "Hotkey: belum diatur (ketuk untuk atur)")
        else t("Hotkey: ${macro.trigger} (tap to change)", "Hotkey: ${macro.trigger} (ketuk untuk ganti)")
    }

    private fun capture(
        title: String,
        msg: String,
        onKey: (KeyEvent) -> Unit,
        onMouse: ((Int) -> Unit)? = null
    ) {
        val body = FrameLayout(this).apply {
            setPadding(dp(24), dp(8), dp(24), dp(8))
            isClickable = true
            addView(TextView(context).apply { text = msg })
        }
        val dlg = MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setView(body)
            .setNegativeButton(t("Cancel", "Batal"), null)
            .create()
        if (onMouse != null) {
            body.setOnTouchListener { _, ev ->
                if (ev.actionMasked == MotionEvent.ACTION_DOWN && ev.getToolType(0) == MotionEvent.TOOL_TYPE_MOUSE) {
                    val s = ev.buttonState
                    val code = when {
                        (s and MotionEvent.BUTTON_SECONDARY) != 0 -> MacroStore.MOUSE_RIGHT
                        (s and MotionEvent.BUTTON_TERTIARY) != 0 -> MacroStore.MOUSE_MIDDLE
                        (s and MotionEvent.BUTTON_PRIMARY) != 0 -> MacroStore.MOUSE_LEFT
                        else -> 0
                    }
                    if (code != 0) {
                        onMouse(code)
                        dlg.dismiss()
                    }
                    true
                } else {
                    false
                }
            }
        }
        dlg.setOnKeyListener { d, code, ev ->
            if (code == KeyEvent.KEYCODE_BACK) return@setOnKeyListener false
            if (ev.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener true
            if (KeyEvent.isModifierKey(code)) return@setOnKeyListener true
            onKey(ev)
            d.dismiss()
            true
        }
        dlg.show()
    }

    private fun rebuildActions() {
        actionsBox.removeAllViews()
        macro.actions.forEachIndexed { i, a -> actionsBox.addView(actionCard(i, a)) }
        addBtn.isEnabled = macro.actions.size < 10
        addBtn.text = t("+ Add action (${macro.actions.size}/10)", "+ Tambah aksi (${macro.actions.size}/10)")
    }

    private fun actionCard(i: Int, a: MacroAction): MaterialCardView {
        val (card, box) = card()

        val label = TextView(this).apply { setPadding(0, dp(8), 0, 0) }
        val keyBtn = MaterialButton(this, null, MR.attr.materialButtonOutlinedStyle)
        keyBtn.layoutParams = lp(8)
        val coords = row()

        fun coord(hint: String, v: Float, set: (Float) -> Unit): TextInputLayout {
            val (til, et) = field(hint, true) { set(it.toFloatOrNull() ?: 0f) }
            et.setText(v.toInt().toString())
            coords.addView(til, w(2))
            return til
        }
        coord("X", a.x) { a.x = it }
        coord("Y", a.y) { a.y = it }
        val x2 = coord("X2", a.x2) { a.x2 = it }
        val y2 = coord("Y2", a.y2) { a.y2 = it }

        fun applyType() {
            val isKey = a.type == "key"
            val isSwipe = a.type == "swipe"
            keyBtn.visibility = if (isKey) View.VISIBLE else View.GONE
            coords.visibility = if (isKey || a.type == "delay") View.GONE else View.VISIBLE
            x2.visibility = if (isSwipe) View.VISIBLE else View.GONE
            y2.visibility = if (isSwipe) View.VISIBLE else View.GONE
            keyBtn.text = t(
                "Key: ${MacroStore.keyName(a.key)} (tap to change)",
                "Tombol: ${MacroStore.keyName(a.key)} (ketuk untuk ganti)"
            )
            label.text = when (a.type) {
                "tap" -> t("Delay after tap", "Jeda setelah tap")
                "long_press" -> t("Hold duration", "Lama tahan")
                "swipe" -> t("Swipe duration", "Durasi swipe")
                "key" -> t("Hold duration", "Lama tahan")
                else -> t("Delay", "Jeda")
            } + ": ${a.ms} ms"
        }

        keyBtn.setOnClickListener {
            capture(
                t("Press a key or click a mouse button", "Tekan tombol atau klik tombol mouse"),
                t("Press a key on the keyboard, or click a mouse button (left, right, middle) inside this dialog.",
                    "Tekan tombol di keyboard, atau klik tombol mouse (kiri, kanan, tengah) di dalam dialog ini."),
                { a.key = it.keyCode; applyType() },
                { a.key = it; applyType() }
            )
        }

        val head = row()
        val (typeTil, typeAc) = dropdown("#${i + 1}")
        val labels = typeLabels()
        typeAc.setSimpleItems(labels.toTypedArray())
        typeAc.setText(labels[types.indexOf(a.type).coerceAtLeast(0)], false)
        typeAc.setOnItemClickListener { _, _, pos, _ ->
            a.type = types[pos]
            if (a.type == "key" && a.key == 0) a.key = KeyEvent.KEYCODE_C
            applyType()
        }
        head.addView(typeTil, w(0))
        head.addView(btn("✕", true) {
            macro.actions.removeAt(i)
            rebuildActions()
        }, LinearLayout.LayoutParams(wrap, wrap).apply { marginStart = dp(8) })

        val slider = Slider(this).apply {
            valueFrom = 10f
            valueTo = 2000f
            stepSize = 10f
            value = (a.ms.toInt().coerceIn(10, 2000) / 10 * 10).toFloat()
            addOnChangeListener { _, v, _ ->
                a.ms = v.toLong()
                applyType()
            }
        }

        box.addView(head)
        box.addView(keyBtn)
        box.addView(coords)
        box.addView(label)
        box.addView(slider)
        applyType()
        return card
    }

    private fun save() {
        if (cfg.targetPackage.isBlank()) cfg.targetPackage = MacroStore.MC_PACKAGE
        val trig = cfg.macros.map { it.trigger.uppercase() }
        when {
            cfg.macros.any { it.trigger.isBlank() } ->
                toast(t("A macro has no hotkey", "Ada macro yang belum punya hotkey"))
            trig.toSet().size != trig.size ->
                toast(t("Two macros share the same hotkey", "Ada hotkey yang sama antar macro"))
            else -> {
                MacroStore.save(this, MacroStore.toJson(cfg))
                toast(t("Saved", "Tersimpan"))
            }
        }
    }

    companion object {
        private var draft: Config? = null
    }
}
