package com.lowsii.macrobuilder

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.KeyEvent
import android.view.ViewGroup
import android.widget.EditText
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
    private lateinit var macroAc: MaterialAutoCompleteTextView
    private lateinit var nameEt: TextInputEditText
    private lateinit var hotkeyBtn: MaterialButton
    private lateinit var toggleSw: MaterialSwitch
    private lateinit var togetherSw: MaterialSwitch
    private lateinit var repeatEt: TextInputEditText
    private lateinit var actionsBox: LinearLayout
    private lateinit var addBtn: MaterialButton
    private lateinit var shizukuText: TextView

    private val types = listOf("tap", "long_press", "swipe", "delay", "key")
    private val match = ViewGroup.LayoutParams.MATCH_PARENT
    private val wrap = ViewGroup.LayoutParams.WRAP_CONTENT
    private val permListener = Shizuku.OnRequestPermissionResultListener { _, _ -> refreshShizuku() }

    private val macro: Macro get() = cfg.macros[cur]

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()
    private fun t(en: String, id: String) = if (lang == "id") id else en
    private fun typeLabels() = listOf("Tap", "Long Press", "Swipe", "Delay", t("Key", "Tombol"))
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

        root.addView(TextView(this).apply {
            text = "Minecraft Macro Builder"
            setTextAppearance(MR.style.TextAppearance_Material3_HeadlineSmall)
            setPadding(0, dp(8), 0, dp(4))
        })

        val (setupCard, setupBox) = card()
        setupBox.addView(TextView(this).apply {
            text = t(
                "1) Enable the accessibility service.\n2) Key actions need Shizuku running and granted.\n3) Set up a macro and hotkey, then Save.\n4) Open Minecraft Bedrock and press the hotkey.",
                "1) Aktifkan layanan aksesibilitas.\n2) Aksi tombol membutuhkan Shizuku yang berjalan dan diizinkan.\n3) Atur macro dan hotkey, lalu Simpan.\n4) Buka Minecraft Bedrock dan tekan hotkey."
            )
        })
        setupBox.addView(btn(t("Open accessibility settings", "Buka pengaturan aksesibilitas"), true) {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })
        shizukuText = TextView(this).apply { setPadding(0, dp(12), 0, 0) }
        setupBox.addView(shizukuText)
        setupBox.addView(btn(t("Grant Shizuku access", "Beri akses Shizuku"), true) { requestShizuku() })
        root.addView(setupCard)

        val (langTil, langAc) = dropdown(t("Language", "Bahasa"))
        val langs = listOf("English", "Bahasa Indonesia")
        langAc.setSimpleItems(langs.toTypedArray())
        langAc.setText(if (lang == "id") langs[1] else langs[0], false)
        langAc.setOnItemClickListener { _, _, pos, _ ->
            val n = if (pos == 1) "id" else "en"
            if (n != lang) {
                getSharedPreferences("settings", MODE_PRIVATE).edit().putString("lang", n).apply()
                draft = cfg
                recreate()
            }
        }
        langTil.layoutParams = lp(12)
        root.addView(langTil)

        onlySw = MaterialSwitch(this).apply {
            text = t("Only active in Minecraft Bedrock", "Hanya aktif di Minecraft Bedrock")
            layoutParams = lp(8)
            setOnCheckedChangeListener { _, c -> if (!loading) cfg.onlyMinecraft = c }
        }
        root.addView(onlySw)

        val (macroTil, ac) = dropdown(t("Macro", "Macro"))
        macroAc = ac
        macroAc.setOnItemClickListener { _, _, pos, _ -> if (pos != cur) { cur = pos; refresh() } }
        val macroRow = row()
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
                    "Tekan tombol di keyboard, boleh dengan Ctrl/Alt/Shift, mis. F6 atau Ctrl+F7.")
            ) { macro.trigger = MacroStore.combo(it); refreshHotkey() }
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
                macro.actions.add(MacroAction("tap", 500f, 500f))
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
        refreshShizuku()
    }

    override fun onDestroy() {
        try { Shizuku.removeRequestPermissionResultListener(permListener) } catch (e: Throwable) {}
        super.onDestroy()
    }

    private fun refreshShizuku() {
        val running = try { Shizuku.pingBinder() } catch (e: Throwable) { false }
        shizukuText.text = when {
            KeyInjector.ready() -> t("Shizuku: ready", "Shizuku: siap")
            running -> t("Shizuku: running, access not granted", "Shizuku: berjalan, akses belum diberikan")
            else -> t("Shizuku: not running", "Shizuku: tidak berjalan")
        }
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
        onlySw.isChecked = cfg.onlyMinecraft
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

    private fun capture(title: String, msg: String, onKey: (KeyEvent) -> Unit) {
        val dlg = MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setMessage(msg)
            .setNegativeButton(t("Cancel", "Batal"), null)
            .create()
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

        val head = row()
        val (typeTil, typeAc) = dropdown("#${i + 1}")
        val labels = typeLabels()
        typeAc.setSimpleItems(labels.toTypedArray())
        typeAc.setText(labels[types.indexOf(a.type).coerceAtLeast(0)], false)
        typeAc.setOnItemClickListener { _, _, pos, _ ->
            if (types[pos] != a.type) {
                a.type = types[pos]
                if (a.type == "key" && a.key == 0) a.key = KeyEvent.KEYCODE_C
                actionsBox.post { rebuildActions() }
            }
        }
        head.addView(typeTil, w(0))
        head.addView(btn("✕", true) {
            macro.actions.removeAt(i)
            rebuildActions()
        }, LinearLayout.LayoutParams(wrap, wrap).apply { marginStart = dp(8) })
        box.addView(head)

        if (a.type == "key") {
            box.addView(btn(t("Key: ${MacroStore.keyName(a.key)} (tap to change)", "Tombol: ${MacroStore.keyName(a.key)} (ketuk untuk ganti)"), true) {
                capture(
                    t("Press the key", "Tekan tombol"),
                    t("Press the key this action should send.", "Tekan tombol yang akan dikirim aksi ini.")
                ) { a.key = it.keyCode; rebuildActions() }
            })
        } else if (a.type != "delay") {
            val coords = row()
            fun coord(hint: String, v: Float, set: (Float) -> Unit) {
                val (til, et) = field(hint, true) { set(it.toFloatOrNull() ?: 0f) }
                et.setText(v.toInt().toString())
                coords.addView(til, w(2))
            }
            coord("X", a.x) { a.x = it }
            coord("Y", a.y) { a.y = it }
            if (a.type == "swipe") {
                coord("X2", a.x2) { a.x2 = it }
                coord("Y2", a.y2) { a.y2 = it }
            }
            box.addView(coords)
        }

        val label = TextView(this).apply { setPadding(0, dp(8), 0, 0) }
        fun upd() {
            label.text = when (a.type) {
                "tap" -> t("Delay after tap", "Jeda setelah tap")
                "long_press" -> t("Hold duration", "Lama tahan")
                "swipe" -> t("Swipe duration", "Durasi swipe")
                "key" -> t("Key hold duration", "Lama tahan tombol")
                else -> t("Delay", "Jeda")
            } + ": ${a.ms} ms"
        }
        val slider = Slider(this).apply {
            valueFrom = 10f
            valueTo = 2000f
            stepSize = 10f
            value = (a.ms.toInt().coerceIn(10, 2000) / 10 * 10).toFloat()
            addOnChangeListener { _, v, _ -> a.ms = v.toLong(); upd() }
        }
        upd()
        box.addView(label)
        box.addView(slider)
        return card
    }

    private fun save() {
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
