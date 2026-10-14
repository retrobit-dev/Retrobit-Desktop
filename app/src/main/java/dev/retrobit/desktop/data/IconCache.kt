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
    /** Roughly 12 MB of bitmaps: enough for a tablet-sized desktop, small enough to stay polite. */
    private const val MAX_BYTES = 12 * 1024 * 1024

    /**
     * Icons are cached by **bytes**, not by count: a 128 px icon is ~64 KB, so a count-based cache of
     * 600 entries could quietly hold 40 MB and make the whole launcher feel heavy.
     */
    private val cache = object : LruCache<String, ImageBitmap>(MAX_BYTES) {
        override fun sizeOf(key: String, value: ImageBitmap): Int =
            (value.width * value.height * 4).coerceAtLeast(1)
    }

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
        edge: Color?,
        monogram: Boolean,
    ) = "${app.key}|$mode|$theme|${ink.toArgb()}|${paper.toArgb()}|${accent?.toArgb() ?: 0}" +
        "|${edge?.toArgb() ?: 0}|$monogram"

    fun peek(
        app: AppEntry,
        mode: IconMode,
        theme: IconTheme,
        ink: Color,
        paper: Color,
        accent: Color? = null,
        edge: Color? = null,
        monogram: Boolean = false,
    ): ImageBitmap? = cache.get(key(app, mode, theme, ink, paper, accent, edge, monogram))

    fun get(
        app: AppEntry,
        mode: IconMode,
        theme: IconTheme,
        ink: Color,
        paper: Color,
        accent: Color? = null,
        edge: Color? = null,
        monogram: Boolean = false,
        label: String? = null,
    ): ImageBitmap? {
        val k = key(app, mode, theme, ink, paper, accent, edge, monogram)
        cache.get(k)?.let { return it }
        val drawable = runCatching { app.info.getBadgedIcon(0) }.getOrNull()
            ?: runCatching { app.info.getIcon(0) }.getOrNull()
            ?: return null
        val bmp = when (mode) {
            IconMode.COLOR -> render(drawable, 128)
            IconMode.MONO -> mono(render(drawable, 128), ink.toArgb(), paper.toArgb())
            IconMode.DITHER -> dither(render(drawable, 64), ink.toArgb(), paper.toArgb())
        }
        val framed = frame(bmp, theme, ink.toArgb(), paper.toArgb(), accent?.toArgb() ?: 0, edge?.toArgb() ?: 0)
        if (monogram) stamp(framed, label ?: app.label, ink.toArgb(), paper.toArgb(), theme)
        val img = framed.asImageBitmap()
        cache.put(k, img)
        return img
    }

    /**
     * Desktop-theme icon framing. Every icon becomes a tile: a plate in the palette colours, the
     * artwork masked to the shape, and then the trim that shape calls for — the bevel and accent
     * band of a Windows 95 button, the thin outline of an e-paper print, the gloss of an Aqua tile,
     * the accent trim of Yaru, the ring of a GNOME circle. A mixed set of third-party icons then
     * still looks like the desktop theme it is sitting on.
     */
    private fun frame(
        src: Bitmap,
        theme: IconTheme,
        ink: Int,
        paper: Int,
        accent: Int = 0,
        edge: Int = 0,
    ): Bitmap {
        if (theme == IconTheme.PLAIN) return src
        val size = src.width
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        // `edge` is the border of a native desktop theme; the e-paper palettes leave it 0 and keep
        // printing the frame in ink
        val frameColor = if (edge != 0) edge else ink
        val circle = theme == IconTheme.CIRCLE
        // the shape vocabulary is shared with the internal icons of the shell (IconTheme.tileRadius)
        val radius = IconTheme.tileRadius(theme, size.toFloat())
        val inset = IconTheme.tileInset(theme, size.toFloat())
        val body = Rect(inset.toInt(), inset.toInt(), size - inset.toInt(), size - inset.toInt())
        // a circle is drawn as an oval across the whole (square) body; everything else as a rounded box
        val path = android.graphics.Path().apply {
            if (circle) {
                addOval(android.graphics.RectF(body), android.graphics.Path.Direction.CW)
            } else {
                addRoundRect(android.graphics.RectF(body), radius, radius, android.graphics.Path.Direction.CW)
            }
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
        // frame stroke: thick ink on the e-paper print, thin and pale on the native tiles
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = if (edge != 0) size * 0.035f else size * 0.06f
            color = frameColor
        }
        val accented = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = if (edge != 0) size * 0.03f else size * 0.045f
            color = if (accent != 0) accent else frameColor
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
            IconTheme.SQUIRCLE -> {
                // Windows 11 and friends: no outline, just a hairline and a light top edge
                val hair = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = size * 0.02f
                    color = frameColor
                    alpha = 150
                }
                canvas.drawPath(path, hair)
                val gloss = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = size * 0.022f
                    color = paper
                    alpha = 90
                }
                canvas.save()
                canvas.clipPath(path)
                canvas.drawLine(body.left + 1f, body.top + 1f, body.right - 1f, body.top + 1f, gloss)
                canvas.restore()
            }
            IconTheme.AQUA -> {
                // macOS: a thin edge, a glassy highlight across the top and a soft inner shadow below
                val hair = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = size * 0.028f
                    color = frameColor
                }
                canvas.drawPath(path, hair)
                canvas.save()
                canvas.clipPath(path)
                val gloss = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = size * 0.10f
                    color = 0xFFFFFFFF.toInt()
                    alpha = 60
                }
                canvas.drawLine(body.left + 2f, body.top + size * 0.10f, body.right - 2f, body.top + size * 0.10f, gloss)
                val foot = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = size * 0.045f
                    color = frameColor
                    alpha = 70
                }
                canvas.drawLine(body.left + 2f, body.bottom - size * 0.03f, body.right - 2f, body.bottom - size * 0.03f, foot)
                canvas.restore()
            }
            IconTheme.YARU -> {
                // Ubuntu / Yaru: a rounded square with the accent trim along the bottom edge
                val hair = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = size * 0.024f
                    color = frameColor
                    alpha = 170
                }
                canvas.drawPath(path, hair)
                if (accent != 0) {
                    val trim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        style = Paint.Style.STROKE
                        strokeWidth = size * 0.055f
                        color = accent
                    }
                    canvas.save()
                    canvas.clipPath(path)
                    canvas.drawLine(
                        body.left + size * 0.16f, body.bottom - size * 0.045f,
                        body.right - size * 0.16f, body.bottom - size * 0.045f, trim,
                    )
                    canvas.restore()
                }
            }
            IconTheme.CIRCLE -> canvas.drawPath(path, accented)
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
        val radius = if (theme == IconTheme.CLASSIC || theme == IconTheme.PLAIN) 0f else plate * 0.28f
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
