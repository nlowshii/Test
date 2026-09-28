package com.smac.macrobuilder

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.util.TypedValue
import android.view.Gravity
import android.widget.TextView

object FloatUi {
    fun px(ctx: Context, dp: Int): Int = (dp * ctx.resources.displayMetrics.density).toInt()

    fun color(ctx: Context): Int =
        if (Build.VERSION.SDK_INT >= 31) ctx.getColor(android.R.color.system_accent1_600)
        else 0xFF3F51B5.toInt()

    fun label(m: Macro): String = m.label.ifBlank { m.name }.take(3)

    fun create(ctx: Context, m: Macro): TextView {
        val size = px(ctx, m.size)
        return TextView(ctx).apply {
            text = label(m)
            contentDescription = m.name
            gravity = Gravity.CENTER
            includeFontPadding = false
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, (m.size * 0.3f).coerceIn(10f, 30f))
            background = GradientDrawable().apply {
                setColor(color(ctx))
                cornerRadius = size / 2f * m.radius / 100f
            }
            alpha = m.opacity / 100f
        }
    }
}
