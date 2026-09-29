package com.smac.macrobuilder

import android.content.pm.PackageManager
import android.os.IBinder
import android.view.InputEvent
import org.lsposed.hiddenapibypass.HiddenApiBypass
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper
import java.lang.reflect.Method

object ShizukuInput {
    private var manager: Any? = null
    private var injectMethod: Method? = null
    private var readyAt = 0L
    private var readyValue = false

    fun ready(): Boolean {
        val now = System.currentTimeMillis()
        if (now - readyAt < 1000) return readyValue
        readyValue = try {
            Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (e: Throwable) {
            false
        }
        readyAt = now
        return readyValue
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

    fun inject(ev: InputEvent): Boolean {
        if (!ready() || !bind()) {
            Diag.lastInject = "unavailable"
            return false
        }
        val ok = try {
            injectMethod!!.invoke(manager, ev, 0) as? Boolean ?: false
        } catch (e: Throwable) {
            false
        }
        Diag.lastInject = if (ok) "ok" else "failed"
        return ok
    }
}
