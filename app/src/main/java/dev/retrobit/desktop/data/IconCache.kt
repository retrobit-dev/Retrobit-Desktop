package dev.retrobit.desktop.data

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.util.LruCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import dev.retrobit.desktop.model.AppEntry
import dev.retrobit.desktop.model.IconMode
import dev.retrobit.desktop.model.IconTheme

/**
 * Renders app icons into the e-paper aesthetic:
 *  COLOR  – untouched
 *  MONO   – grayscale mapped onto the palette's ink→paper ramp
 *  DITHER – 1-bit ordered (Bayer) dither at low resolution, outlined, shown with nearest-neighbour scaling
 */
object IconCache {
    private val cache = LruCache<String, ImageBitmap>(600)

    private val bayer = intArrayOf(0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5)

    /** Ordered-dither threshold of a 4×4 Bayer matrix, used by the wallpaper renderer as well. */
    fun bayerThreshold(x: Int, y: Int): Float = (bayer[(y and 3) * 4 + (x and 3)] + 0.5f) / 16f

    fun clear() = cache.evictAll()

    private fun key(app: AppEntry, mode: IconMode, theme: IconTheme, ink: Color, paper: Color) =
        "${app.key}|$mode|$theme|${ink.toArgb()}|${paper.toArgb()}"

    fun peek(app: AppEntry, mode: IconMode, theme: IconTheme, ink: Color, paper: Color): ImageBitmap? =
        cache.get(key(app, mode, theme, ink, paper))

    fun get(app: AppEntry, mode: IconMode, theme: IconTheme, ink: Color, paper: Color): ImageBitmap? {
        val k = key(app, mode, theme, ink, paper)
        cache.get(k)?.let { return it }
        val drawable = runCatching { app.info.getBadgedIcon(0) }.getOrNull()
            ?: runCatching { app.info.getIcon(0) }.getOrNull()
            ?: return null
        val bmp = when (mode) {
            IconMode.COLOR -> render(drawable, 128)
            IconMode.MONO -> mono(render(drawable, 128), ink.toArgb(), paper.toArgb())
            IconMode.DITHER -> dither(render(drawable, 40), ink.toArgb(), paper.toArgb())
        }
        val framed = frame(bmp, theme, ink.toArgb(), paper.toArgb())
        val img = framed.asImageBitmap()
        cache.put(k, img)
        return img
    }

    /**
     * Desktop-theme icon shapes: the app icon is masked/framed so a mixed set of third-party icons
     * still looks like it belongs to the active desktop.
     */
    private fun frame(src: Bitmap, theme: IconTheme, ink: Int, paper: Int): Bitmap {
        if (theme == IconTheme.PLAIN) return src
        val size = src.width
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val radius = when (theme) {
            IconTheme.CLASSIC -> 0f
            IconTheme.ROUNDED -> size * 0.20f
            IconTheme.FLAT -> size * 0.06f
            else -> 0f
        }
        val inset = when (theme) {
            IconTheme.CLASSIC -> size * 0.05f
            IconTheme.ROUNDED -> size * 0.04f
            else -> 0f
        }
        val body = Rect(inset.toInt(), inset.toInt(), size - inset.toInt(), size - inset.toInt())
        val path = android.graphics.Path().apply { addRoundRect(android.graphics.RectF(body), radius, radius, android.graphics.Path.Direction.CW) }
        canvas.save()
        canvas.clipPath(path)
        canvas.drawBitmap(src, null, body, paint)
        canvas.restore()
        // 1-bit style outline so the icon sits on the paper like a print
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = size * 0.035f
            color = ink
        }
        when (theme) {
            IconTheme.CLASSIC -> {
                canvas.drawRect(body, stroke)
                // bevel highlight along the top/left, shadow along the bottom/right
                val hi = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = paper; strokeWidth = size * 0.02f }
                canvas.drawLine(body.left + 1f, body.top + 1f, body.right - 1f, body.top + 1f, hi)
                canvas.drawLine(body.left + 1f, body.top + 1f, body.left + 1f, body.bottom - 1f, hi)
            }
            IconTheme.ROUNDED, IconTheme.FLAT -> canvas.drawPath(path, stroke)
            else -> Unit
        }
        return out
    }

    private fun render(d: Drawable, size: Int): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        d.setBounds(0, 0, size, size)
        d.draw(c)
        return bmp
    }

    private fun lum(p: Int): Float {
        val r = (p shr 16) and 0xFF
        val g = (p shr 8) and 0xFF
        val b = p and 0xFF
        return (0.299f * r + 0.587f * g + 0.114f * b) / 255f
    }

    private fun mix(a: Int, b: Int, t: Float): Int {
        val tt = t.coerceIn(0f, 1f)
        fun ch(s: Int) = (((a shr s) and 0xFF) * (1 - tt) + ((b shr s) and 0xFF) * tt).toInt() and 0xFF
        return (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    private fun mono(src: Bitmap, ink: Int, paper: Int): Bitmap {
        val w = src.width
        val h = src.height
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)
        for (i in px.indices) {
            val p = px[i]
            val a = (p ushr 24) and 0xFF
            if (a == 0) continue
            // contrast curve so icons read well on paper
            var l = lum(p)
            l = ((l - 0.5f) * 1.35f + 0.5f).coerceIn(0f, 1f)
            px[i] = (a shl 24) or mix(ink, paper, l)
        }
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(px, 0, w, 0, 0, w, h)
        return out
    }

    private fun dither(src: Bitmap, ink: Int, paper: Int): Bitmap {
        val w = src.width
        val h = src.height
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)
        val solid = BooleanArray(w * h) { ((px[it] ushr 24) and 0xFF) > 110 }
        val out = IntArray(w * h)
        val inkO = ink or (0xFF shl 24)
        val paperO = paper or (0xFF shl 24)
        for (y in 0 until h) for (x in 0 until w) {
            val i = y * w + x
            if (!solid[i]) { out[i] = 0; continue }
            val edge = x == 0 || y == 0 || x == w - 1 || y == h - 1 ||
                !solid[i - 1] || !solid[i + 1] || !solid[i - w] || !solid[i + w]
            if (edge) { out[i] = inkO; continue }
            val l = ((lum(px[i]) - 0.5f) * 1.2f + 0.5f).coerceIn(0f, 1f)
            val t = (bayer[(y and 3) * 4 + (x and 3)] + 0.5f) / 16f
            out[i] = if (l < t) inkO else paperO
        }
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bmp.setPixels(out, 0, w, 0, 0, w, h)
        return bmp
    }
}
