package dev.retrobit.desktop.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.util.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import kotlin.math.roundToInt

enum class WallpaperSource(val label: String) {
    PATTERN("Pattern"),
    DEVICE("Device wallpaper"),
    IMAGE("My image"),
}

enum class WallpaperTone(val label: String, val hint: String) {
    HALFTONE("1-bit halftone", "Bayer dither, ink on paper"),
    GRAY("Grayscale dither", "several ink levels"),
    SOFT("Soft gray", "washed-out print, no dither"),
}

/**
 * Turns any picture (the tablet's own wallpaper or an image the user picked) into something an
 * e-paper panel could actually show: grayscale, contrast, then ordered dithering into the current
 * palette's ink/paper colours. The result is deliberately low resolution and is scaled up with
 * nearest-neighbour filtering, exactly like the icon renderer does.
 */
class WallpaperRepository(private val context: Context) {

    /** Current device wallpaper, or null when the platform refuses to hand it over. */
    fun deviceWallpaper(maxPx: Int = 2048): Bitmap? = runCatching {
        val manager = android.app.WallpaperManager.getInstance(context)
        val drawable = manager.drawable ?: return@runCatching null
        val w = drawable.intrinsicWidth.takeIf { it > 0 } ?: 1440
        val h = drawable.intrinsicHeight.takeIf { it > 0 } ?: 2560
        val scale = (maxPx.toFloat() / maxOf(w, h)).coerceAtMost(1f)
        val bmp = Bitmap.createBitmap(
            (w * scale).toInt().coerceAtLeast(64),
            (h * scale).toInt().coerceAtLeast(64),
            Bitmap.Config.ARGB_8888,
        )
        val canvas = Canvas(bmp)
        drawable.setBounds(0, 0, bmp.width, bmp.height)
        drawable.draw(canvas)
        bmp
    }.getOrNull()

    /** Decodes a user picked image, downsampled so we never blow up memory. */
    fun loadImage(uri: Uri, maxPx: Int = 2048): Bitmap? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = android.graphics.ImageDecoder.createSource(context.contentResolver, uri)
            android.graphics.ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                val size: Size = info.size
                val scale = (maxPx.toFloat() / maxOf(size.width, size.height)).coerceAtMost(1f)
                decoder.setTargetSize((size.width * scale).toInt().coerceAtLeast(64), (size.height * scale).toInt().coerceAtLeast(64))
                decoder.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            val sample = (maxOf(bounds.outWidth, bounds.outHeight).coerceAtLeast(1) / maxPx).coerceAtLeast(1)
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        }
    }.getOrNull()

    /**
     * Renders [src] in the e-paper style of the current palette.
     * [cells] is the target width in "panel pixels": the smaller, the chunkier the dither.
     */
    fun render(src: Bitmap, paper: Color, ink: Color, tone: WallpaperTone, cells: Int = 240): ImageBitmap {
        val aspect = src.height.toFloat() / src.width.coerceAtLeast(1)
        val w = cells.coerceIn(48, 720)
        val h = (w * aspect).toInt().coerceIn(32, 900)
        val small = Bitmap.createScaledBitmap(src, w, h, true)
        val px = IntArray(w * h)
        small.getPixels(px, 0, w, 0, 0, w, h)

        val paperArgb = paper.toArgb()
        val inkArgb = ink.toArgb()
        val out = IntArray(w * h)
        val inkOpaque = inkArgb or (0xFF shl 24)
        val paperOpaque = paperArgb or (0xFF shl 24)
        // 5 steps from paper (0) to ink (4) — enough for a believable e-paper ramp
        val ramp = IntArray(5) { i -> mix(paperArgb, inkArgb, i / 4f) or (0xFF shl 24) }

        for (y in 0 until h) for (x in 0 until w) {
            val p = px[y * w + x]
            var l = luminance(p)
            l = ((l - 0.5f) * 1.25f + 0.48f).coerceIn(0f, 1f)
            val i = y * w + x
            out[i] = when (tone) {
                WallpaperTone.HALFTONE -> {
                    val t = IconCache.bayerThreshold(x, y)
                    // darker pixels are more likely to become ink
                    if (1f - l > t) inkOpaque else paperOpaque
                }
                WallpaperTone.GRAY -> {
                    // pick a level from the dithered value so shading keeps its tone
                    val shade = (1f - l) * 4f
                    val base = shade.toInt().coerceIn(0, 4)
                    val frac = shade - base
                    val t = IconCache.bayerThreshold(x, y)
                    ramp[(base + if (frac > t) 1 else 0).coerceIn(0, 4)]
                }
                WallpaperTone.SOFT -> ramp[(4 - (l * 4f).toInt()).coerceIn(0, 4)]
            }
        }
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bmp.setPixels(out, 0, w, 0, 0, w, h)
        return bmp.asImageBitmap()
    }

    /**
     * Wallpaper for a **native** theme: the picture keeps its real colours instead of being printed
     * as e-paper. It is only posterised a little (16 levels per channel) so a photo still reads as
     * part of a retro desktop, and rendered larger than the print version because colour can carry
     * the extra detail.
     */
    fun renderColor(src: Bitmap, cells: Int = 520, posterise: Int = 16): ImageBitmap {
        val aspect = src.height.toFloat() / src.width.coerceAtLeast(1)
        val w = cells.coerceIn(64, 1080)
        val h = (w * aspect).toInt().coerceIn(48, 1400)
        val small = Bitmap.createScaledBitmap(src, w, h, true)
        val px = IntArray(w * h)
        small.getPixels(px, 0, w, 0, 0, w, h)
        val steps = (posterise - 1).coerceAtLeast(1).toFloat()
        val out = IntArray(w * h) { i ->
            val p = px[i]
            val r = (((p shr 16) and 0xFF) / 255f * steps).roundToInt() * 255 / steps.toInt()
            val g = (((p shr 8) and 0xFF) / 255f * steps).roundToInt() * 255 / steps.toInt()
            val b = ((p and 0xFF) / 255f * steps).roundToInt() * 255 / steps.toInt()
            val a = (p ushr 24) and 0xFF
            (a shl 24) or (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)
        }
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bmp.setPixels(out, 0, w, 0, 0, w, h)
        return bmp.asImageBitmap()
    }

    fun takePersistable(uri: Uri) = runCatching {
        context.contentResolver.takePersistableUriPermission(
            uri,
            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
        )
    }

    private fun luminance(p: Int): Float {
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

    /** Crops [src] to the desktop aspect ratio so a portrait photo still fills the screen. */
    fun cropToAspect(src: Bitmap, aspect: Float): Bitmap {
        val current = src.width.toFloat() / src.height.coerceAtLeast(1)
        if (kotlin.math.abs(current - aspect) < 0.01f) return src
        return if (current > aspect) {
            val w = (src.height * aspect).toInt().coerceAtLeast(1)
            val x = (src.width - w) / 2
            Bitmap.createBitmap(src, x, 0, w, src.height)
        } else {
            val h = (src.width / aspect).toInt().coerceAtLeast(1)
            val y = (src.height - h) / 2
            Bitmap.createBitmap(src, 0, y, src.width, h)
        }
    }

    /** Small helper for tests/tools: raw crop rect maths. */
    fun cropRect(width: Int, height: Int, aspect: Float): Rect {
        val current = width.toFloat() / height.coerceAtLeast(1)
        return if (current > aspect) {
            val w = (height * aspect).toInt().coerceAtLeast(1)
            val x = (width - w) / 2
            Rect(x, 0, x + w, height)
        } else {
            val h = (width / aspect).toInt().coerceAtLeast(1)
            val y = (height - h) / 2
            Rect(0, y, width, y + h)
        }
    }

    companion object {
        internal val fakePaint = Paint()
    }
}
