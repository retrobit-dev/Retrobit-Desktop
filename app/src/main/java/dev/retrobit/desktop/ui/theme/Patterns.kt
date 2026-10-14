package dev.retrobit.desktop.ui.theme

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.retrobit.desktop.model.WallPattern
import kotlin.math.max
import kotlin.math.roundToInt

object Bayer {
    private val m4 = intArrayOf(0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5)
    fun threshold(x: Int, y: Int): Float = (m4[(y and 3) * 4 + (x and 3)] + 0.5f) / 16f
    fun level(x: Int, y: Int): Int = m4[(y and 3) * 4 + (x and 3)]
}

/** Extra patterns used for chrome (shadows, title bars), not selectable as wallpaper. */
enum class ChromePattern { CHECKER, STRIPES_H, STIPPLE }

private fun cellRect(c: Canvas, p: Paint, cx: Int, cy: Int, cell: Int) {
    c.drawRect((cx * cell).toFloat(), (cy * cell).toFloat(), ((cx + 1) * cell).toFloat(), ((cy + 1) * cell).toFloat(), p)
}

fun wallpaperTile(pattern: WallPattern, color: Color, cellPx: Int): ImageBitmap? {
    val cell = max(1, cellPx)
    val paint = Paint().apply { this.color = color.toArgb(); isAntiAlias = false; style = Paint.Style.FILL }
    return when (pattern) {
        WallPattern.PLAIN -> null
        WallPattern.DOTS -> {
            val n = 7
            val bmp = Bitmap.createBitmap(n * cell, n * cell, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            paint.isAntiAlias = true
            c.drawCircle(n * cell / 2f, n * cell / 2f, cell * 0.95f, paint)
            bmp.asImageBitmap()
        }
        WallPattern.GRID -> {
            val n = 16
            val bmp = Bitmap.createBitmap(n * cell, n * cell, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            val lw = max(1, cell / 2).toFloat()
            // dotted grid lines
            for (i in 0 until n step 2) {
                c.drawRect(i * cell.toFloat(), 0f, i * cell + lw, lw, paint)
                c.drawRect(0f, i * cell.toFloat(), lw, i * cell + lw, paint)
            }
            // crosshair at the intersection
            c.drawRect(0f, 0f, cell * 1.5f, lw * 2, paint)
            c.drawRect(0f, 0f, lw * 2, cell * 1.5f, paint)
            bmp.asImageBitmap()
        }
        WallPattern.DITHER -> {
            val n = 8
            val bmp = Bitmap.createBitmap(n * cell, n * cell, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            for (y in 0 until n) for (x in 0 until n) if (Bayer.level(x, y) < 2) cellRect(c, paint, x, y, cell)
            bmp.asImageBitmap()
        }
        WallPattern.HATCH -> {
            val n = 8
            val bmp = Bitmap.createBitmap(n * cell, n * cell, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            for (i in 0 until n) cellRect(c, paint, i, n - 1 - i, cell)
            bmp.asImageBitmap()
        }
    }
}

fun chromeTile(pattern: ChromePattern, color: Color, cellPx: Int): ImageBitmap {
    val cell = max(1, cellPx)
    val paint = Paint().apply { this.color = color.toArgb(); isAntiAlias = false }
    return when (pattern) {
        ChromePattern.CHECKER -> {
            val bmp = Bitmap.createBitmap(2 * cell, 2 * cell, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            cellRect(c, paint, 0, 0, cell); cellRect(c, paint, 1, 1, cell)
            bmp.asImageBitmap()
        }
        ChromePattern.STRIPES_H -> {
            val bmp = Bitmap.createBitmap(cell, 3 * cell, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            cellRect(c, paint, 0, 1, cell)
            bmp.asImageBitmap()
        }
        ChromePattern.STIPPLE -> {
            val bmp = Bitmap.createBitmap(4 * cell, 4 * cell, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            for (y in 0 until 4) for (x in 0 until 4) if (Bayer.level(x, y) < 4) cellRect(c, paint, x, y, cell)
            bmp.asImageBitmap()
        }
    }
}

private fun ImageBitmap.brush(): Brush = ShaderBrush(ImageShader(this, TileMode.Repeated, TileMode.Repeated))

@Composable
fun rememberWallpaperBrush(pattern: WallPattern, color: Color, cell: Dp = 2.dp): Brush? {
    val px = with(LocalDensity.current) { cell.toPx() }.roundToInt()
    return remember(pattern, color, px) { wallpaperTile(pattern, color, px)?.brush() }
}

@Composable
fun rememberChromeBrush(pattern: ChromePattern, color: Color, cell: Dp = 1.dp): Brush {
    val px = with(LocalDensity.current) { cell.toPx() }.roundToInt()
    return remember(pattern, color, px) { chromeTile(pattern, color, px).brush() }
}

/**
 * Horizontal ink→paper gradient rendered with an ordered dither (the e-paper version of
 * the classic Win95 title bar). Returned at "cell" resolution; draw it with FilterQuality.None.
 */
fun ditherGradient(wCells: Int, hCells: Int, ink: Color, start: Float = 0.35f): ImageBitmap {
    val w = max(1, wCells)
    val h = max(1, hCells)
    val px = IntArray(w * h)
    val inkI = ink.toArgb()
    for (y in 0 until h) for (x in 0 until w) {
        val t = x.toFloat() / w
        val cover = if (t < start) 1f else (1f - (t - start) / (1f - start)).coerceIn(0f, 1f)
        px[y * w + x] = if (cover > Bayer.threshold(x, y)) inkI else 0
    }
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    bmp.setPixels(px, 0, w, 0, 0, w, h)
    return bmp.asImageBitmap()
}
