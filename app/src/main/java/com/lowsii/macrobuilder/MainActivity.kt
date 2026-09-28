package com.lowsii.macrobuilder

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.widget.*

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (16 * resources.displayMetrics.density).toInt()

        val info = TextView(this).apply {
            text = "1) Aktifkan layanan aksesibilitas.\n" +
                "2) Edit macro (JSON) di bawah lalu Simpan.\n" +
                "3) Buka Minecraft Bedrock dan tekan hotkey (mis. F6).\n\n" +
                "Tipe aksi: tap, long_press, swipe, delay."
        }
        val open = Button(this).apply {
            text = "Buka Pengaturan Aksesibilitas"
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        }
        val editor = EditText(this).apply {
            setText(MacroStore.raw(this@MainActivity))
            typeface = Typeface.MONOSPACE
            textSize = 12f
            gravity = Gravity.TOP
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            minLines = 18
        }
        val save = Button(this).apply {
            text = "Simpan"
            setOnClickListener {
                try {
                    MacroStore.save(this@MainActivity, editor.text.toString())
                    toast("Tersimpan")
                } catch (e: Exception) {
                    toast("JSON tidak valid: ${e.message}")
                }
            }
        }
        val reset = Button(this).apply {
            text = "Reset ke contoh"
            setOnClickListener { editor.setText(MacroStore.DEFAULT_JSON) }
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            fitsSystemWindows = true
            listOf(info, open, editor, save, reset).forEach { addView(it) }
        }
        setContentView(ScrollView(this).apply { addView(root); fitsSystemWindows = true })
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()
}
