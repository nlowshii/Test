package com.smac.macrobuilder

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.util.TypedValue
import android.view.Gravity
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory
import java.io.File

object FloatUi {
    val PALETTE: List<Int> = listOf(
        0,
        0xFF3B5BDB.toInt(),
        0xFFE03131.toInt(),
        0xFF2F9E44.toInt(),
        0xFFF08C00.toInt(),
        0xFF9C36B5.toInt(),
        0xFFD6336C.toInt(),
        0xFF0C8599.toInt(),
        0xFF495057.toInt(),
        0xFF000000.toInt(),
        0xFFFFFFFF.toInt()
    )

    fun px(ctx: Context, dp: Int): Int = (dp * ctx.resources.displayMetrics.density).toInt()

    fun accent(ctx: Context): Int =
        if (Build.VERSION.SDK_INT >= 31) ctx.getColor(android.R.color.system_accent1_600)
        else 0xFF3F51B5.toInt()

    fun label(m: Macro): String = m.label.ifBlank { m.name }.take(3)

    fun bgFile(ctx: Context, m: Macro): File = File(ctx.filesDir, "bg_${m.id}.png")

    fun hasImage(ctx: Context, m: Macro): Boolean = m.id.isNotBlank() && bgFile(ctx, m).exists()

    fun saveBackground(ctx: Context, m: Macro, uri: Uri): Boolean {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= 512 && bounds.outHeight / (sample * 2) >= 512) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val src = ctx.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            } ?: return false
            val side = minOf(src.width, src.height)
            val square = Bitmap.createBitmap(src, (src.width - side) / 2, (src.height - side) / 2, side, side)
            val out = Bitmap.createScaledBitmap(square, 256, 256, true)
            bgFile(ctx, m).outputStream().use { out.compress(Bitmap.CompressFormat.PNG, 100, it) }
            true
        } catch (e: Exception) {
            false
        }
    }

    fun create(ctx: Context, m: Macro): TextView {
        val size = px(ctx, m.size)
        val bmp = if (m.bgImage && hasImage(ctx, m)) BitmapFactory.decodeFile(bgFile(ctx, m).path) else null
        val fill = if (m.bgColor == 0) accent(ctx) else m.bgColor
        val dark = bmp != null || ColorUtils.calculateLuminance(fill) < 0.6
        val radius = size / 2f * m.radius / 100f
        return TextView(ctx).apply {
            text = label(m)
            contentDescription = m.name
            gravity = Gravity.CENTER
            includeFontPadding = false
            setTextColor(if (dark) Color.WHITE else Color.BLACK)
            setShadowLayer(4f, 0f, 1f, if (dark) Color.BLACK else Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, (m.size * 0.3f).coerceIn(10f, 30f))
            background = if (bmp != null) {
                RoundedBitmapDrawableFactory.create(ctx.resources, bmp).apply {
                    cornerRadius = radius
                    setAntiAlias(true)
                }
            } else {
                GradientDrawable().apply {
                    setColor(fill)
                    cornerRadius = radius
                }
            }
            alpha = m.opacity / 100f
        }
    }
}
