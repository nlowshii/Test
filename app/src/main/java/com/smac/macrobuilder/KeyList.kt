package com.smac.macrobuilder

import android.view.KeyEvent

object KeyList {
    val entries: List<Pair<String, Int>> = buildList {
        add("Left Click" to MacroStore.MOUSE_LEFT)
        add("Right Click" to MacroStore.MOUSE_RIGHT)
        add("Middle Click" to MacroStore.MOUSE_MIDDLE)
        for (i in 0 until 26) add(('A' + i).toString() to (KeyEvent.KEYCODE_A + i))
        for (i in 0..9) add(i.toString() to (KeyEvent.KEYCODE_0 + i))
        for (i in 1..12) add("F$i" to (KeyEvent.KEYCODE_F1 + i - 1))
        add("Space" to KeyEvent.KEYCODE_SPACE)
        add("Enter" to KeyEvent.KEYCODE_ENTER)
        add("Escape" to KeyEvent.KEYCODE_ESCAPE)
        add("Tab" to KeyEvent.KEYCODE_TAB)
        add("Backspace" to KeyEvent.KEYCODE_DEL)
        add("Shift" to KeyEvent.KEYCODE_SHIFT_LEFT)
        add("Ctrl" to KeyEvent.KEYCODE_CTRL_LEFT)
        add("Alt" to KeyEvent.KEYCODE_ALT_LEFT)
        add("Arrow Up" to KeyEvent.KEYCODE_DPAD_UP)
        add("Arrow Down" to KeyEvent.KEYCODE_DPAD_DOWN)
        add("Arrow Left" to KeyEvent.KEYCODE_DPAD_LEFT)
        add("Arrow Right" to KeyEvent.KEYCODE_DPAD_RIGHT)
    }

    val names: List<String> = entries.map { it.first }
    val codes: List<Int> = entries.map { it.second }

    fun displayName(code: Int): String =
        entries.firstOrNull { it.second == code }?.first ?: MacroStore.keyName(code)
}
