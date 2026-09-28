package com.lowsii.macrobuilder

import android.content.Context
import android.view.KeyEvent
import org.json.JSONArray
import org.json.JSONObject

data class MacroAction(
    var type: String,          // tap | long_press | swipe | delay
    var x: Float = 0f, var y: Float = 0f,
    var x2: Float = 0f, var y2: Float = 0f,
    var ms: Long = 100
)

data class Macro(
    var name: String,
    var trigger: String,       // contoh: "F6" atau "CTRL+F6"
    var toggle: Boolean,       // true = tekan sekali mulai, tekan lagi berhenti
    var repeat: Int,           // 0 + toggle = tanpa henti
    val actions: MutableList<MacroAction>
)

data class Config(var onlyMinecraft: Boolean, val macros: MutableList<Macro>)

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
        {"type": "tap", "x": 850, "y": 420, "ms": 100},
        {"type": "long_press", "x": 850, "y": 420, "ms": 400},
        {"type": "swipe", "x": 800, "y": 400, "x2": 1000, "y2": 400, "ms": 300},
        {"type": "delay", "ms": 500}
      ]
    }
  ]
}
""".trimIndent()

    fun combo(ev: KeyEvent): String {
        val parts = mutableListOf<String>()
        if (ev.isCtrlPressed) parts += "CTRL"
        if (ev.isAltPressed) parts += "ALT"
        if (ev.isShiftPressed) parts += "SHIFT"
        parts += KeyEvent.keyCodeToString(ev.keyCode).removePrefix("KEYCODE_")
        return parts.joinToString("+")
    }

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
                    .take(10).toMutableList()
            )
        }.toMutableList()
        return Config(o.optBoolean("onlyMinecraft", true), macros)
    }

    private fun action(a: JSONObject) = MacroAction(
        type = a.getString("type"),
        x = a.optDouble("x", 0.0).toFloat(), y = a.optDouble("y", 0.0).toFloat(),
        x2 = a.optDouble("x2", 0.0).toFloat(), y2 = a.optDouble("y2", 0.0).toFloat(),
        ms = a.optLong("ms", 100)
    )

    fun toJson(c: Config): String {
        val macros = JSONArray()
        c.macros.forEach { m ->
            val acts = JSONArray()
            m.actions.forEach { a ->
                acts.put(JSONObject()
                    .put("type", a.type)
                    .put("x", a.x.toDouble()).put("y", a.y.toDouble())
                    .put("x2", a.x2.toDouble()).put("y2", a.y2.toDouble())
                    .put("ms", a.ms))
            }
            macros.put(JSONObject()
                .put("name", m.name).put("trigger", m.trigger)
                .put("toggle", m.toggle).put("repeat", m.repeat)
                .put("actions", acts))
        }
        return JSONObject().put("onlyMinecraft", c.onlyMinecraft)
            .put("macros", macros).toString(2)
    }

    fun raw(ctx: Context): String =
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY, null) ?: DEFAULT_JSON

    fun save(ctx: Context, json: String) {
        parse(json) // validasi
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString(KEY, json).apply()
    }

    fun load(ctx: Context): Config =
        try { parse(raw(ctx)) } catch (e: Exception) { Config(true, mutableListOf()) }
}
