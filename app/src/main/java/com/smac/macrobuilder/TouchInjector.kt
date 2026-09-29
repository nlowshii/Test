package com.smac.macrobuilder

import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import kotlinx.coroutines.delay
import java.util.concurrent.atomic.AtomicInteger

object TouchInjector {
    private val nextId = AtomicInteger(1)

    private fun allocId(): Int {
        val id = nextId.getAndUpdate { if (it >= 8) 1 else it + 1 }
        return id
    }

    private fun event(action: Int, id: Int, x: Float, y: Float, downTime: Long, eventTime: Long): Boolean {
        val props = arrayOf(MotionEvent.PointerProperties().apply {
            this.id = id
            toolType = MotionEvent.TOOL_TYPE_FINGER
        })
        val coords = arrayOf(MotionEvent.PointerCoords().apply {
            this.x = x
            this.y = y
            pressure = 1f
            size = 1f
        })
        val ev = MotionEvent.obtain(
            downTime, eventTime, action, 1, props, coords, 0, 0, 1f, 1f, 0, 0,
            InputDevice.SOURCE_TOUCHSCREEN, 0
        )
        val ok = ShizukuInput.inject(ev)
        ev.recycle()
        return ok
    }

    suspend fun tap(x: Float, y: Float): Boolean {
        val id = allocId()
        val down = SystemClock.uptimeMillis()
        val ok1 = event(MotionEvent.ACTION_DOWN, id, x, y, down, down)
        delay(30)
        val ok2 = event(MotionEvent.ACTION_UP, id, x, y, down, SystemClock.uptimeMillis())
        return ok1 && ok2
    }

    suspend fun hold(x: Float, y: Float, ms: Long): Boolean {
        val id = allocId()
        val down = SystemClock.uptimeMillis()
        val ok1 = event(MotionEvent.ACTION_DOWN, id, x, y, down, down)
        if (!ok1) return false
        delay(ms.coerceAtLeast(1))
        return event(MotionEvent.ACTION_UP, id, x, y, down, SystemClock.uptimeMillis())
    }

    suspend fun swipe(x1: Float, y1: Float, x2: Float, y2: Float, durationMs: Long): Boolean {
        val id = allocId()
        val down = SystemClock.uptimeMillis()
        if (!event(MotionEvent.ACTION_DOWN, id, x1, y1, down, down)) return false
        val steps = (durationMs / 16L).toInt().coerceIn(2, 60)
        for (i in 1..steps) {
            val f = i.toFloat() / steps
            val x = x1 + (x2 - x1) * f
            val y = y1 + (y2 - y1) * f
            event(MotionEvent.ACTION_MOVE, id, x, y, down, SystemClock.uptimeMillis())
            delay((durationMs / steps).coerceAtLeast(1))
        }
        return event(MotionEvent.ACTION_UP, id, x2, y2, down, SystemClock.uptimeMillis())
    }
}
