package com.lowsii.macrobuilder

import android.content.pm.PackageManager
import android.os.IBinder
import android.os.SystemClock
import android.view.InputDevice
import android.view.InputEvent
import android.view.KeyCharacterMap
import android.view.KeyEvent
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

    fun send(code: Int, down: Boolean): Boolean {
        if (!ready() || !bind()) return false
        if (down) held.add(code) else held.remove(code)
        val now = SystemClock.uptimeMillis()
        val event = KeyEvent(
            now, now,
            if (down) KeyEvent.ACTION_DOWN else KeyEvent.ACTION_UP,
            code, 0, 0,
            KeyCharacterMap.VIRTUAL_KEYBOARD, 0, 0,
            InputDevice.SOURCE_KEYBOARD
        )
        return try {
            injectMethod!!.invoke(manager, event, 0) as? Boolean ?: false
        } catch (e: Throwable) {
            false
        }
    }
}
