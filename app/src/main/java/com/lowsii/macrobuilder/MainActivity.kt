package com.lowsii.macrobuilder

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.*

class MainActivity : Activity() {

    private lateinit var cfg: Config
    private var cur = 0
    private var loading = false

    private val types = listOf("tap", "long_press", "swipe", "delay")
    private val labels = listOf("Tap", "Long Press", "Swipe", "Delay")
    private val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    private val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

    private lateinit var onlySw: Switch
    private lateinit var macroSpinner: Spinner
    private lateinit var nameEdit: EditText
    private lateinit var hotkeyBtn: Button
    private lateinit var toggleSw: Switch
    private lateinit var repeatEdit: EditText
    private lateinit var actionsBox: LinearLayout
    private lateinit var addBtn: Button

    private val macro: Macro get() = cfg.macros[cur]
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()

    private fun EditText.onText(f: (String) -> Unit) {
        addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { if (!loading) f(s.toString()) }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })
    }

    private fun newMacro() = Macro("Macro ${cfg.macros.size + 1}", "", false, 1, mutableListOf())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cfg = try { MacroStore.parse(MacroStore.raw(this)) }
        catch (e: Exception) { MacroStore.parse(MacroStore.DEFAULT_JSON) }
        if (cfg.macros.isEmpty()) cfg.macros.add(newMacro())

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(24))
        }

        root.addView(TextView(this).apply {
            text = "1) Aktifkan layanan aksesibilitas.\n2) Atur macro & hotkey di bawah, lalu Simpan.\n3) Buka Minecraft Bedrock dan tekan hotkey."
        })
        root.addView(Button(this).apply {
            text = "Buka Pengaturan Aksesibilitas"
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        })

        onlySw = Switch(this).apply {
            text = "Hanya aktif di Minecraft Bedrock"
            isChecked = cfg.onlyMinecraft
            setOnCheckedChangeListener { _, c -> if (!loading) cfg.onlyMinecraft = c }
        }
        root.addView(onlySw)

        // pemilih macro
        macroSpinner = Spinner(this)
        macroSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                if (!loading && pos != cur) { cur = pos; refresh() }
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
        val macroRow = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        macroRow.addView(macroSpinner, LinearLayout.LayoutParams(0, WRAP, 1f))
        macroRow.addView(Button(this).apply {
            text = "+ Macro"
            setOnClickListener {
                if (cfg.macros.size >= 5) { toast("Maksimal 5 macro"); return@setOnClickListener }
                cfg.macros.add(newMacro()); cur = cfg.macros.size - 1; refresh()
            }
        })
        macroRow.addView(Button(this).apply {
            text = "Hapus"
            setOnClickListener {
                if (cfg.macros.size <= 1) { toast("Minimal 1 macro"); return@setOnClickListener }
                cfg.macros.removeAt(cur); cur = minOf(cur, cfg.macros.size - 1); refresh()
            }
        })
        root.addView(macroRow)

        nameEdit = EditText(this).apply { hint = "Nama macro"; setSingleLine(); onText { macro.name = it } }
        root.addView(nameEdit)

        hotkeyBtn = Button(this).apply { setOnClickListener { captureHotkey() } }
        root.addView(hotkeyBtn)

        toggleSw = Switch(this).apply {
            text = "Toggle (tekan sekali mulai, tekan lagi berhenti)"
            setOnCheckedChangeListener { _, c -> if (!loading) macro.toggle = c }
        }
        root.addView(toggleSw)

        repeatEdit = EditText(this).apply {
            hint = "Ulangi (0 = tanpa henti, hanya jika toggle)"
            inputType = InputType.TYPE_CLASS_NUMBER
            onText { macro.repeat = it.toIntOrNull() ?: 1 }
        }
        root.addView(repeatEdit)

        root.addView(TextView(this).apply {
            text = "Aksi (maks 10) — slider ke kiri = lebih cepat"
            setPadding(0, dp(12), 0, 0)
        })
        actionsBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(actionsBox)

        addBtn = Button(this).apply {
            setOnClickListener {
                if (macro.actions.size < 10) {
                    macro.actions.add(MacroAction("tap", 500f, 500f, 0f, 0f, 100))
                    rebuildActions()
                }
            }
        }
        root.addView(addBtn)

        root.addView(Button(this).apply {
            text = "Simpan"
            setOnClickListener { save() }
        })
        val io = LinearLayout(this)
        io.addView(Button(this).apply {
            text = "Ekspor JSON"
            setOnClickListener {
                val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("macro", MacroStore.toJson(cfg)))
                toast("JSON disalin ke clipboard")
            }
        }, LinearLayout.LayoutParams(0, WRAP, 1f))
        io.addView(Button(this).apply {
            text = "Impor JSON"
            setOnClickListener {
                try {
                    val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                    val t = cm.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
                    val c = MacroStore.parse(t)
                    if (c.macros.isEmpty()) throw Exception("macro kosong")
                    cfg = c; cur = 0; refresh(); toast("Diimpor, jangan lupa Simpan")
                } catch (e: Exception) { toast("Clipboard bukan JSON macro valid") }
            }
        }, LinearLayout.LayoutParams(0, WRAP, 1f))
        root.addView(io)

        setContentView(ScrollView(this).apply { addView(root); fitsSystemWindows = true })
        refresh()
    }

    private fun refresh() {
        loading = true
        onlySw.isChecked = cfg.onlyMinecraft
        macroSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item,
            cfg.macros.mapIndexed { i, m -> "${i + 1}. ${m.name}" })
        macroSpinner.setSelection(cur)
        nameEdit.setText(macro.name)
        toggleSw.isChecked = macro.toggle
        repeatEdit.setText(macro.repeat.toString())
        loading = false
        refreshHotkey()
        rebuildActions()
    }

    private fun refreshHotkey() {
        hotkeyBtn.text = if (macro.trigger.isBlank()) "Hotkey: (belum diatur) — ketuk untuk atur"
        else "Hotkey: ${macro.trigger} — ketuk untuk ganti"
    }

    private fun captureHotkey() {
        val dlg = AlertDialog.Builder(this)
            .setTitle("Tekan tombol hotkey")
            .setMessage("Tekan tombol di keyboard (boleh dengan Ctrl/Alt/Shift), mis. F6 atau Ctrl+F7.")
            .setNegativeButton("Batal", null)
            .create()
        dlg.setOnKeyListener { d, code, ev ->
            if (code == KeyEvent.KEYCODE_BACK) return@setOnKeyListener false
            if (ev.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener true
            if (KeyEvent.isModifierKey(code)) return@setOnKeyListener true
            macro.trigger = MacroStore.combo(ev)
            refreshHotkey()
            d.dismiss()
            true
        }
        dlg.show()
    }

    private fun rebuildActions() {
        actionsBox.removeAllViews()
        macro.actions.forEachIndexed { i, a -> actionsBox.addView(actionRow(i, a)) }
        addBtn.isEnabled = macro.actions.size < 10
        addBtn.text = "+ Tambah Aksi (${macro.actions.size}/10)"
    }

    private fun num(hint: String, v: Float, set: (Float) -> Unit) = EditText(this).apply {
        this.hint = hint
        textSize = 13f
        inputType = InputType.TYPE_CLASS_NUMBER
        setText(v.toInt().toString())
        onText { set(it.toFloatOrNull() ?: 0f) }
        layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
    }

    private fun actionRow(i: Int, a: MacroAction): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(8))
            background = GradientDrawable().apply {
                setColor(0xFFEDEDF2.toInt()); cornerRadius = dp(12).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(8) }
        }

        val head = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        head.addView(TextView(this).apply { text = "#${i + 1}"; setPadding(0, 0, dp(8), 0) })
        val sp = Spinner(this)
        sp.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, labels)
        sp.setSelection(types.indexOf(a.type).coerceAtLeast(0))
        var ready = false
        sp.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                if (!ready) { ready = true; return }
                if (types[pos] != a.type) { a.type = types[pos]; actionsBox.post { rebuildActions() } }
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
        head.addView(sp, LinearLayout.LayoutParams(0, WRAP, 1f))
        head.addView(Button(this).apply {
            text = "✕"
            setOnClickListener { macro.actions.removeAt(i); rebuildActions() }
        })
        box.addView(head)

        if (a.type != "delay") {
            val row = LinearLayout(this)
            row.addView(num("x", a.x) { a.x = it })
            row.addView(num("y", a.y) { a.y = it })
            if (a.type == "swipe") {
                row.addView(num("x2", a.x2) { a.x2 = it })
                row.addView(num("y2", a.y2) { a.y2 = it })
            }
            box.addView(row)
        }

        val label = TextView(this)
        fun upd() {
            label.text = when (a.type) {
                "tap" -> "Jeda setelah tap"
                "long_press" -> "Lama tahan"
                "swipe" -> "Durasi swipe"
                else -> "Jeda"
            } + ": ${a.ms} ms"
        }
        val sb = SeekBar(this).apply {
            max = 200
            progress = ((a.ms - 10) / 10).toInt().coerceIn(0, 200)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, u: Boolean) { a.ms = 10L + p * 10L; upd() }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        }
        upd()
        box.addView(label)
        box.addView(sb)
        return box
    }

    private fun save() {
        val trig = cfg.macros.map { it.trigger.uppercase() }
        when {
            cfg.macros.any { it.trigger.isBlank() } -> toast("Ada macro yang belum punya hotkey")
            trig.toSet().size != trig.size -> toast("Ada hotkey yang sama antar macro")
            else -> { MacroStore.save(this, MacroStore.toJson(cfg)); toast("Tersimpan") }
        }
    }
}
