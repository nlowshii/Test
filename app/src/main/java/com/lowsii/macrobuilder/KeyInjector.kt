package com.smac.macrobuilder

import android.content.pm.PackageManager
import android.content.res.Resources
import android.os.IBinder
import android.os.SystemClock
import android.view.InputDevice
import android.view.InputEvent
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.MotionEvent
import org.lsposed.hiddenapibypass.HiddenApiBypass
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper
import java.lang.reflect.Method

object KeyInjector {
    val held = mutableSetOf<Int>()

    private var manager: Any? = null
    private var injectMethod: Method? = null

    fun ready(): Boolean = try {
        Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (e: Throwable) {
        false
    }

    private fun bind(): Boolean {
        if (manager != null && injectMethod != null) return true
        return try {
            HiddenApiBypass.addHiddenApiExemptions("")
            val binder = ShizukuBinderWrapper(SystemServiceHelper.getSystemService("input"))
            val stub = Class.forName("android.hardware.input.IInputManager\$Stub")
            manager = stub.getMethod("asInterface", IBinder::class.java).invoke(null, binder)
            injectMethod = Class.forName("android.hardware.input.IInputManager")
                .getMethod("injectInputEvent", InputEvent::class.java, Int::class.javaPrimitiveType)
            true
        } catch (e: Throwable) {
            manager = null
            injectMethod = null
            false
        }
    }

    private fun inject(ev: InputEvent): Boolean = try {
        injectMethod!!.invoke(manager, ev, 0) as? Boolean ?: false
    } catch (e: Throwable) {
        false
    }

    fun send(code: Int, down: Boolean): Boolean {
        if (!ready() || !bind()) {
            Diag.lastInject = "unavailable"
            return false
        }
        val ok = if (code < 0) sendMouse(code, down) else sendKey(code, down)
        Diag.lastInject = if (ok) "ok" else "failed"
        return ok
    }

    private fun sendKey(code: Int, down: Boolean): Boolean {
        if (down) held.add(code) else held.remove(code)
        val now = SystemClock.uptimeMillis()
        val event = KeyEvent(
            now, now,
            if (down) KeyEvent.ACTION_DOWN else KeyEvent.ACTION_UP,
            code, 0, 0,
            KeyCharacterMap.VIRTUAL_KEYBOARD, 0, 0,
            InputDevice.SOURCE_KEYBOARD
        )
        return inject(event)
    }

    private fun sendMouse(code: Int, down: Boolean): Boolean {
        val button = when (code) {
            MacroStore.MOUSE_LEFT -> MotionEvent.BUTTON_PRIMARY
            MacroStore.MOUSE_RIGHT -> MotionEvent.BUTTON_SECONDARY
            else -> MotionEvent.BUTTON_TERTIARY
        }
        return if (down) {
            val a = mouse(MotionEvent.ACTION_DOWN, 0, button)
            val b = mouse(MotionEvent.ACTION_BUTTON_PRESS, button, button)
            a && b
        } else {
            val a = mouse(MotionEvent.ACTION_BUTTON_RELEASE, button, 0)
            val b = mouse(MotionEvent.ACTION_UP, 0, 0)
            a && b
        }
    }

    private fun mouse(action: Int, actionButton: Int, state: Int): Boolean {
        val now = SystemClock.uptimeMillis()
        val dm = Resources.getSystem().displayMetrics
        val props = arrayOf(MotionEvent.PointerProperties().apply {
            id = 0
            toolType = MotionEvent.TOOL_TYPE_MOUSE
        })
        val coords = arrayOf(MotionEvent.PointerCoords().apply {
            x = dm.widthPixels / 2f
            y = dm.heightPixels / 2f
            pressure = 1f
            size = 1f
        })
        val ev = MotionEvent.obtain(
            now, now, action, 1, props, coords, 0, state, 1f, 1f, 0, 0,
            InputDevice.SOURCE_MOUSE, 0
        )
        if (actionButton != 0) ev.actionButton = actionButton
        val ok = inject(ev)
        ev.recycle()
        return ok
    }
}
