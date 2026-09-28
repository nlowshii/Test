package com.lowsii.macrobuilder

import android.content.Context
import android.view.KeyEvent
import org.json.JSONArray
import org.json.JSONObject

data class MacroAction(
    var type: String,
    var x: Float = 0f,
    var y: Float = 0f,
    var x2: Float = 0f,
    var y2: Float = 0f,
    var ms: Long = 100,
    var key: Int = 0
)

data class Macro(
    var name: String,
    var trigger: String,
    var toggle: Boolean,
    var repeat: Int,
    var together: Boolean,
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
      "name": "Keys C + M",
      "trigger": "F6",
      "toggle": false,
      "repeat": 1,
      "together": true,
      "actions": [
        {"type": "key", "key": 31, "ms": 150},
        {"type": "key", "key": 41, "ms": 150}
      ]
    }
  ]
}
""".trimIndent()

    fun keyName(code: Int): String = KeyEvent.keyCodeToString(code).removePrefix("KEYCODE_")

    fun combo(ev: KeyEvent): String {
        val parts = mutableListOf<String>()
        if (ev.isCtrlPressed) parts += "CTRL"
        if (ev.isAltPressed) parts += "ALT"
        if (ev.isShiftPressed) parts += "SHIFT"
        parts += keyName(ev.keyCode)
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
                together = m.optBoolean("together", false),
                actions = (0 until acts.length()).map { j -> action(acts.getJSONObject(j)) }
                    .take(10).toMutableList()
            )
        }.toMutableList()
        return Config(o.optBoolean("onlyMinecraft", true), macros)
    }

    private fun action(a: JSONObject) = MacroAction(
        type = a.getString("type"),
        x = a.optDouble("x", 0.0).toFloat(),
        y = a.optDouble("y", 0.0).toFloat(),
        x2 = a.optDouble("x2", 0.0).toFloat(),
        y2 = a.optDouble("y2", 0.0).toFloat(),
        ms = a.optLong("ms", 100),
        key = a.optInt("key", 0)
    )

    fun toJson(c: Config): String {
        val macros = JSONArray()
        c.macros.forEach { m ->
            val acts = JSONArray()
            m.actions.forEach { a ->
                acts.put(
                    JSONObject()
                        .put("type", a.type)
                        .put("x", a.x.toDouble()).put("y", a.y.toDouble())
                        .put("x2", a.x2.toDouble()).put("y2", a.y2.toDouble())
                        .put("ms", a.ms)
                        .put("key", a.key)
                )
            }
            macros.put(
                JSONObject()
                    .put("name", m.name).put("trigger", m.trigger)
                    .put("toggle", m.toggle).put("repeat", m.repeat)
                    .put("together", m.together)
                    .put("actions", acts)
            )
        }
        return JSONObject().put("onlyMinecraft", c.onlyMinecraft)
            .put("macros", macros).toString(2)
    }

    fun raw(ctx: Context): String =
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY, null) ?: DEFAULT_JSON

    fun save(ctx: Context, json: String) {
        parse(json)
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString(KEY, json).apply()
    }

    fun load(ctx: Context): Config =
        try { parse(raw(ctx)) } catch (e: Exception) { Config(true, mutableListOf()) }
}
