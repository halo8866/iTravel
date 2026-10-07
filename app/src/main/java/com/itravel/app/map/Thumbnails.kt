package com.itravel.app.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.LruCache
import androidx.core.content.ContextCompat
import com.itravel.app.R
import kotlin.math.max
import kotlin.math.min

/**
 * Builds rounded-square photo thumbnails used as map marker icons,
 * plus a default branded pin for places without a photo.
 */
object Thumbnails {

    private const val TARGET_DP = 48

    private val cache = object : LruCache<String, Bitmap>(48) {
        override fun sizeOf(key: String, value: Bitmap): Int = 1
    }

    fun sizePx(context: Context): Int =
        (TARGET_DP * context.resources.displayMetrics.density).toInt()

    fun markerIcon(context: Context, path: String?): Drawable {
        val px = sizePx(context)
        if (path.isNullOrBlank()) return BitmapDrawable(context.resources, defaultBitmap(context, px))
        val key = "$path#$px"
        cache.get(key)?.let { return BitmapDrawable(context.resources, it) }
        val decoded = decodeSampled(path, px)
        val bmp = if (decoded != null) frame(centerCrop(decoded, px), px) else defaultBitmap(context, px)
        cache.put(key, bmp)
        return BitmapDrawable(context.resources, bmp)
    }

    fun evict(path: String) {
        cache.remove("$path#0")
        // keys carry size suffix; snapshot removal is best-effort
        cache.evictAll()
    }

    // ------------------------------------------------------------------ internals

    private fun decodeSampled(path: String, px: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        val largest = max(bounds.outWidth, bounds.outHeight)
        while (largest / (sample * 2) >= px * 2) sample *= 2
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeFile(path, opts)
    }

    private fun centerCrop(src: Bitmap, px: Int): Bitmap {
        val scale = max(px.toFloat() / src.width, px.toFloat() / src.height)
        val sw = px / scale
        val sh = px / scale
        val left = (src.width - sw) / 2f
        val top = (src.height - sh) / 2f
        val cropped = Bitmap.createBitmap(
            src,
            left.toInt().coerceAtLeast(0),
            top.toInt().coerceAtLeast(0),
            sw.toInt().coerceAtMost(src.width),
            sh.toInt().coerceAtMost(src.height)
        )
        return Bitmap.createScaledBitmap(cropped, px, px, true)
    }

    private fun frame(src: Bitmap, px: Int): Bitmap {
        val out = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val radius = px * 0.22f
        val border = px * 0.07f

        val photoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = BitmapShader(src, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        }
        val rect = RectF(border, border, px - border, px - border)
        canvas.drawRoundRect(rect, radius, radius, photoPaint)

        // subtle bottom shadow inside frame
        val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, px * 0.65f, 0f, px - border,
                Color.argb(60, 0, 0, 0), Color.TRANSPARENT, Shader.TileMode.CLAMP
            )
        }
        canvas.drawRoundRect(rect, radius, radius, shadow)

        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = border
            color = Color.WHITE
        }
        canvas.drawRoundRect(rect, radius, radius, borderPaint)
        return out
    }

    private fun defaultBitmap(context: Context, px: Int): Bitmap {
        val out = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val border = px * 0.07f
        val rect = RectF(border, border, px - border, px - border)
        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#2E7D6B") }
        canvas.drawRoundRect(rect, px * 0.22f, px * 0.22f, bg)
        val pin: Drawable? = ContextCompat.getDrawable(context, R.drawable.ic_pin)
        if (pin != null) {
            val pad = (px * 0.24f).toInt()
            pin.setBounds(pad, pad, px - pad, px - pad)
            pin.draw(canvas)
        }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = border
            color = Color.WHITE
        }
        canvas.drawRoundRect(rect, px * 0.22f, px * 0.22f, borderPaint)
        return out
    }
}
