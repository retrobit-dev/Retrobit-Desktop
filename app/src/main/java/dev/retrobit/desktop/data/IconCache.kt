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

    private fun key(
        app: AppEntry,
        mode: IconMode,
        theme: IconTheme,
        ink: Color,
        paper: Color,
        accent: Color?,
        monogram: Boolean,
    ) = "${app.key}|$mode|$theme|${ink.toArgb()}|${paper.toArgb()}|${accent?.toArgb() ?: 0}|$monogram"

    fun peek(
        app: AppEntry,
        mode: IconMode,
        theme: IconTheme,
        ink: Color,
        paper: Color,
        accent: Color? = null,
        monogram: Boolean = false,
    ): ImageBitmap? = cache.get(key(app, mode, theme, ink, paper, accent, monogram))

    fun get(
        app: AppEntry,
        mode: IconMode,
        theme: IconTheme,
        ink: Color,
        paper: Color,
        accent: Color? = null,
        monogram: Boolean = false,
        label: String? = null,
    ): ImageBitmap? {
        val k = key(app, mode, theme, ink, paper, accent, monogram)
        cache.get(k)?.let { return it }
        val drawable = runCatching { app.info.getBadgedIcon(0) }.getOrNull()
            ?: runCatching { app.info.getIcon(0) }.getOrNull()
            ?: return null
        val bmp = when (mode) {
            IconMode.COLOR -> render(drawable, 128)
            IconMode.MONO -> mono(render(drawable, 128), ink.toArgb(), paper.toArgb())
            IconMode.DITHER -> dither(render(drawable, 64), ink.toArgb(), paper.toArgb())
        }
        val framed = frame(bmp, theme, ink.toArgb(), paper.toArgb(), accent?.toArgb() ?: 0)
        if (monogram) stamp(framed, label ?: app.label, ink.toArgb(), paper.toArgb(), theme)
        val img = framed.asImageBitmap()
        cache.put(k, img)
        return img
    }

    /**
     * Desktop-theme icon framing. Every icon is printed like a tile on paper: a plate in the palette
     * paper, the artwork masked to the shape, an ink outline and — when the palette has one — an
     * accent band, so a mixed set of third-party icons still looks like the active desktop theme.
     */
    private fun frame(src: Bitmap, theme: IconTheme, ink: Int, paper: Int, accent: Int = 0): Bitmap {
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
            IconTheme.CLASSIC -> size * 0.06f
            IconTheme.ROUNDED -> size * 0.05f
            else -> size * 0.03f
        }
        val body = Rect(inset.toInt(), inset.toInt(), size - inset.toInt(), size - inset.toInt())
        val path = android.graphics.Path().apply {
            addRoundRect(android.graphics.RectF(body), radius, radius, android.graphics.Path.Direction.CW)
        }
        // paper plate first: the artwork sits on paper instead of a transparent hole
        paint.style = Paint.Style.FILL
        paint.color = paper
        canvas.drawPath(path, paint)
        // the artwork itself
        paint.style = Paint.Style.FILL
        canvas.save()
        canvas.clipPath(path)
        canvas.drawBitmap(src, null, body, paint)
        canvas.restore()
        // ink outline (thicker than before: it has to read at a glance on e-paper)
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = size * 0.06f
            color = ink
        }
        val accented = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = size * 0.045f
            color = if (accent != 0) accent else ink
        }
        when (theme) {
            IconTheme.CLASSIC -> {
                canvas.drawRect(body, stroke)
                // bevel highlight along the top/left, shadow along the bottom/right
                val hi = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = paper; strokeWidth = size * 0.025f }
                canvas.drawLine(body.left + 1f, body.top + 1f, body.right - 1f, body.top + 1f, hi)
                canvas.drawLine(body.left + 1f, body.top + 1f, body.left + 1f, body.bottom - 1f, hi)
                // accent band along the bottom edge, the way a printed card is trimmed
                val band = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.FILL
                    color = if (accent != 0) accent else ink
                }
                canvas.drawRect(
                    body.left + size * 0.06f, body.bottom - size * 0.11f,
                    body.right - size * 0.06f, body.bottom - size * 0.045f, band,
                )
            }
            IconTheme.ROUNDED, IconTheme.FLAT -> {
                canvas.drawPath(path, stroke)
                canvas.drawPath(path, accented)
            }
            else -> Unit
        }
        return out
    }

    /**
     * Stamps the app's first letter into the lower-left corner of the icon. After dithering, a
     * folder-shaped or unfamiliar icon is hard to tell apart — the letter makes it obvious.
     */
    private fun stamp(bmp: Bitmap, label: String, ink: Int, paper: Int, theme: IconTheme) {
        val letter = label.trim().firstOrNull()?.uppercaseChar() ?: return
        if (!letter.isLetterOrDigit()) return
        val size = bmp.width
        val canvas = Canvas(bmp)
        val plate = size * 0.46f
        val left = size * 0.03f
        val top = size - plate - size * 0.03f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.style = Paint.Style.FILL
        paint.color = paper
        val radius = if (theme == IconTheme.ROUNDED) plate * 0.28f else 0f
        if (radius > 0f) {
            canvas.drawRoundRect(left, top, left + plate, top + plate, radius, radius, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = size * 0.04f
            paint.color = ink
            canvas.drawRoundRect(left, top, left + plate, top + plate, radius, radius, paint)
        } else {
            canvas.drawRect(left, top, left + plate, top + plate, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = size * 0.04f
            paint.color = ink
            canvas.drawRect(left, top, left + plate, top + plate, paint)
        }
        val text = Paint(Paint.ANTI_ALIAS_FLAG)
        text.color = ink
        text.textSize = plate * 0.82f
        text.typeface = android.graphics.Typeface.MONOSPACE
        text.textAlign = Paint.Align.CENTER
        val metrics = text.fontMetrics
        val cx = left + plate / 2f
        val cy = top + plate / 2f - (metrics.ascent + metrics.descent) / 2f
        canvas.drawText(letter.toString(), cx, cy, text)
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
