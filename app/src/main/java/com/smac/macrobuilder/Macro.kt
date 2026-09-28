package com.smac.macrobuilder

import android.content.Context
import android.content.SharedPreferences
import android.view.KeyEvent
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

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
    val actions: MutableList<MacroAction>,
    var id: String = "",
    var label: String = "",
    var size: Int = 56,
    var opacity: Int = 90,
    var radius: Int = 100
)

data class Config(
    var onlyTarget: Boolean,
    val macros: MutableList<Macro>,
    var targetPackage: String = MacroStore.MC_PACKAGE,
    val floats: MutableList<Macro> = mutableListOf(),
    var floatEnabled: Boolean = false
)

object MacroStore {
    const val MC_PACKAGE = "com.mojang.minecraftpe"
    const val MOUSE_LEFT = -1
    const val MOUSE_RIGHT = -2
    const val MOUSE_MIDDLE = -3
    private const val PREF = "macros"
    private const val KEY = "json"

    val DEFAULT_JSON = """
{
  "onlyTarget": true,
  "targetPackage": "com.mojang.minecraftpe",
  "floatEnabled": false,
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

    fun newId(): String = UUID.randomUUID().toString().take(8)

    fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    fun keyName(code: Int): String = when (code) {
        MOUSE_LEFT -> "Left Click"
        MOUSE_RIGHT -> "Right Click"
        MOUSE_MIDDLE -> "Middle Click"
        else -> KeyEvent.keyCodeToString(code).removePrefix("KEYCODE_")
    }

    fun combo(ev: KeyEvent): String {
        val parts = mutableListOf<String>()
        if (ev.isCtrlPressed) parts += "CTRL"
        if (ev.isAltPressed) parts += "ALT"
        if (ev.isShiftPressed) parts += "SHIFT"
        parts += keyName(ev.keyCode)
        return parts.joinToString("+")
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

    private fun parseMacro(m: JSONObject): Macro {
        val acts = m.getJSONArray("actions")
        return Macro(
            name = m.getString("name"),
            trigger = m.optString("trigger", ""),
            toggle = m.optBoolean("toggle", false),
            repeat = m.optInt("repeat", 1),
            together = m.optBoolean("together", false),
            actions = (0 until acts.length()).map { action(acts.getJSONObject(it)) }
                .take(10).toMutableList(),
            id = m.optString("id", ""),
            label = m.optString("label", ""),
            size = m.optInt("size", 56),
            opacity = m.optInt("opacity", 90),
            radius = m.optInt("radius", 100)
        )
    }

    private fun parseList(arr: JSONArray?): MutableList<Macro> {
        if (arr == null) return mutableListOf()
        return (0 until arr.length()).map { parseMacro(arr.getJSONObject(it)) }.toMutableList()
    }

    fun parse(json: String): Config {
        val o = JSONObject(json)
        val floats = parseList(o.optJSONArray("floats"))
        floats.forEach { if (it.id.isBlank()) it.id = newId() }
        return Config(
            o.optBoolean("onlyTarget", true),
            parseList(o.getJSONArray("macros")),
            o.optString("targetPackage", MC_PACKAGE).ifBlank { MC_PACKAGE },
            floats,
            o.optBoolean("floatEnabled", false)
        )
    }

    private fun macroJson(m: Macro): JSONObject {
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
        return JSONObject()
            .put("name", m.name).put("trigger", m.trigger)
            .put("toggle", m.toggle).put("repeat", m.repeat)
            .put("together", m.together)
            .put("id", m.id).put("label", m.label)
            .put("size", m.size).put("opacity", m.opacity).put("radius", m.radius)
            .put("actions", acts)
    }

    fun toJson(c: Config): String {
        val macros = JSONArray()
        c.macros.forEach { macros.put(macroJson(it)) }
        val floats = JSONArray()
        c.floats.forEach { floats.put(macroJson(it)) }
        return JSONObject()
            .put("onlyTarget", c.onlyTarget)
            .put("targetPackage", c.targetPackage)
            .put("floatEnabled", c.floatEnabled)
            .put("macros", macros)
            .put("floats", floats)
            .toString(2)
    }

    fun raw(ctx: Context): String = prefs(ctx).getString(KEY, null) ?: DEFAULT_JSON

    fun save(ctx: Context, json: String) {
        parse(json)
        prefs(ctx).edit().putString(KEY, json).apply()
    }

    fun load(ctx: Context): Config =
        try { parse(raw(ctx)) } catch (e: Exception) { Config(true, mutableListOf()) }
}
