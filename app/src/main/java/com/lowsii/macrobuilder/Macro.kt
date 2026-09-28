package com.lowsii.macrobuilder

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class MacroAction(
    val type: String,          // tap | long_press | swipe | delay
    val x: Float = 0f, val y: Float = 0f,
    val x2: Float = 0f, val y2: Float = 0f,
    val ms: Long = 100
)

data class Macro(
    val name: String,
    val trigger: String,       // contoh: "F6" atau "CTRL+F6"
    val toggle: Boolean,       // true = tekan sekali mulai, tekan lagi berhenti
    val repeat: Int,           // jumlah ulang; 0 + toggle = tanpa henti
    val actions: List<MacroAction>
)

data class Config(val onlyMinecraft: Boolean, val macros: List<Macro>)

object MacroStore {
    const val MC_PACKAGE = "com.mojang.minecraftpe"
    private const val PREF = "macros"
    private const val KEY = "json"

    val DEFAULT_JSON = """
{
  "onlyMinecraft": true,
  "macros": [
    {
      "name": "Quick Action",
      "trigger": "F6",
      "toggle": false,
      "repeat": 3,
      "actions": [
        {"type": "tap", "x": 850, "y": 420},
        {"type": "delay", "ms": 100},
        {"type": "long_press", "x": 850, "y": 420, "ms": 400},
        {"type": "delay", "ms": 200},
        {"type": "swipe", "x": 800, "y": 400, "x2": 1000, "y2": 400, "ms": 300},
        {"type": "delay", "ms": 500}
      ]
    },
    {
      "name": "Auto Tap",
      "trigger": "CTRL+F6",
      "toggle": true,
      "repeat": 0,
      "actions": [
        {"type": "tap", "x": 1200, "y": 600},
        {"type": "delay", "ms": 150}
      ]
    }
  ]
}
""".trimIndent()

    fun parse(json: String): Config {
        val o = JSONObject(json)
        val arr = o.getJSONArray("macros")
        val macros = (0 until arr.length()).map { i ->
            val m = arr.getJSONObject(i)
            val acts = m.getJSONArray("actions")
            Macro(
                name = m.getString("name"),
                trigger = m.getString("trigger"),
                toggle = m.optBoolean("toggle", false),
                repeat = m.optInt("repeat", 1),
                actions = (0 until acts.length()).map { j -> action(acts.getJSONObject(j)) }
            )
        }
        return Config(o.optBoolean("onlyMinecraft", true), macros)
    }

    private fun action(a: JSONObject) = MacroAction(
        type = a.getString("type"),
        x = a.optDouble("x", 0.0).toFloat(), y = a.optDouble("y", 0.0).toFloat(),
        x2 = a.optDouble("x2", 0.0).toFloat(), y2 = a.optDouble("y2", 0.0).toFloat(),
        ms = a.optLong("ms", 100)
    )

    fun raw(ctx: Context): String =
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY, null) ?: DEFAULT_JSON

    fun save(ctx: Context, json: String) {
        parse(json) // validasi dulu, lempar exception kalau salah
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString(KEY, json).apply()
    }

    fun load(ctx: Context): Config =
        try { parse(raw(ctx)) } catch (e: Exception) { Config(true, emptyList()) }
}
