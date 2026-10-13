package dev.retrobit.desktop.shell

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import android.view.MotionEvent
import android.view.View
import dev.retrobit.desktop.R
import dev.retrobit.desktop.ui.components.Glyph
import kotlin.math.round

/**
 * The two rails of the cyberdeck chrome.
 *
 * Both are plain `View`s on purpose: they are added straight to the window manager by
 * [ChromeService], where a Compose host would need a lifecycle owner and a saved-state registry for
 * no gain. Everything is drawn in the launcher's own vocabulary — palette paper, ink rules, the
 * Pixel Operator face and the same 16x16 glyphs the desktop uses — so the chrome is hard to tell
 * apart from the frames drawn inside the launcher.
 *
 * Two rules keep the readouts sharp and legible outdoors (the whole point of an e-paper shell):
 *  · **Pixel Operator is drawn on a 16 px em grid**, so every text size is snapped to a multiple of
 *    16 px — anything else resamples the glyphs and the pixel look is lost;
 *  · **glyphs and app icons are snapped to multiples of 8 px**, so their cells stay square.
 */
class RailView(
    context: Context,
    /** Top rail (title + clock + window keys) or the dock strip along the bottom. */
    private val topRail: Boolean,
) : View(context) {

    /** Called with an action id: "home", "fit", "hide", "win:<id>", "app:<pkg>". */
    var onAction: ((String) -> Unit)? = null

    /** Called on any press, so the service can keep the rail awake / bring it back. */
    var onInteract: (() -> Unit)? = null

    var state: CyberdeckChrome.State = CyberdeckChrome.State()

    /** Clock, battery and network readouts, refreshed by the service. */
    var clock: String = ""
    var battery: String = ""
    var net: String = ""

    /** Status bar band (top rail) / navigation bar band (dock) that has to stay clear. */
    var inset: Int = 0

    /** Camera cutout on the left edge: the title has to start after it. */
    var notchLeft: Int = 0

    /** Auto-hidden rails shrink to a handle: only that strip is drawn (and touchable). */
    var handlesOnly: Boolean = false

    /** Resolves an app icon, if the service has one for that package. */
    var icons: (String) -> Bitmap? = { null }

    private val density = context.resources.displayMetrics.density
    private val display: Typeface = runCatching { resources.getFont(R.font.pixel_operator_bold) }
        .getOrDefault(Typeface.DEFAULT_BOLD)
    private val mono: Typeface = runCatching { resources.getFont(R.font.pixel_operator_mono) }
        .getOrDefault(Typeface.MONOSPACE)
    private val title = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = display }
    private val small = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = mono }
    private val bar = Paint().apply { style = Paint.Style.FILL }
    private val glyphPaint = Paint().apply { style = Paint.Style.FILL }
    private val iconPaint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
        // the dock prints app icons in grayscale, like the rest of the shell
        colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
    }
    private val hits = ArrayList<Pair<RectF, String>>()

    private fun scale(): Float = state.scale.coerceIn(0.6f, 2f)

    private fun dp(v: Float): Float = v * density * scale()

    /** Pixel Operator is crisp only when the size is a multiple of its 16 px em. */
    private fun snap16(px: Float): Float = maxOf(16f, round(px / 16f) * 16f)

    private fun snap8(px: Float): Float = maxOf(8f, round(px / 8f) * 8f)

    /** Height of the drawn rail, without [inset]. */
    fun barHeight(): Int = (if (topRail) dp(40f) else dp(54f)).toInt().coerceAtLeast(48)

    /** Height of the handle the rail shrinks to when it auto-hides. */
    fun handleHeight(): Int = dp(8f).toInt().coerceAtLeast(4)

    /** Total height this view wants, including the system band it covers (top) or leaves free. */
    fun totalHeight(): Int = barHeight() + inset

    override fun onDraw(canvas: Canvas) {
        hits.clear()
        if (handlesOnly) {
            drawHandle(canvas)
            return
        }
        val h = height.toFloat()
        val y0 = (if (topRail) inset else 0).toFloat()
        if (topRail && inset > 0) {
            // the band the app under us uses for its status bar: paper and hatched, plus the clock,
            // so no part of the app's own system chrome shows through the frame
            bar.color = state.ink.paper
            canvas.drawRect(0f, 0f, width.toFloat(), inset.toFloat(), bar)
            bar.color = state.ink.mid
            var x = 0f
            while (x < width) {
                canvas.drawRect(x, 0f, x + dp(1.4f), inset.toFloat(), bar)
                x += dp(7f)
            }
        }

        bar.color = state.ink.paper
        canvas.drawRect(0f, y0, width.toFloat(), height.toFloat(), bar)
        bar.color = Color.BLACK
        if (topRail) canvas.drawRect(0f, height - dp(2f), width.toFloat(), height.toFloat(), bar)
        else canvas.drawRect(0f, 0f, width.toFloat(), dp(2f), bar)

        title.textSize = snap16(dp(15f))
        small.textSize = snap16(dp(13f))

        if (topRail) drawTop(canvas, y0, h) else drawDock(canvas, y0, h)
    }

    /** What is left of an auto-hidden rail: a thin paper strip with a grip, still touchable. */
    private fun drawHandle(canvas: Canvas) {
        val h = height.toFloat()
        bar.color = state.ink.paper
        canvas.drawRect(0f, 0f, width.toFloat(), h, bar)
        bar.color = Color.BLACK
        if (topRail) canvas.drawRect(0f, h - dp(2f), width.toFloat(), h, bar)
        else canvas.drawRect(0f, 0f, width.toFloat(), dp(2f), bar)
        bar.color = state.ink.ink
        val cx = width / 2f
        val grip = dp(22f)
        val thick = maxOf(2f, dp(1.6f))
        if (topRail) {
            canvas.drawRect(cx - grip, h - thick * 2f, cx + grip, h - thick, bar)
        } else {
            canvas.drawRect(cx - grip, thick, cx + grip, thick * 2f, bar)
        }
    }

    // ── the title rail ──────────────────────────────────────────────────────────
    private fun drawTop(canvas: Canvas, y0: Float, h: Float) {
        val hPad = dp(8f)
        val key = snap8(h * 0.76f)
        val top = y0 + (h - key) / 2f

        // keys, right to left: hide, desktop, fit the frame
        var right = width - hPad
        right = box(canvas, right, top, key, "hide", "hide", Glyph.CLOSE, growRight = false) - dp(5f)
        right = box(canvas, right, top, key, "home", "desktop", Glyph.HOME, growRight = false) - dp(5f)
        if (state.canRefit) {
            right = box(canvas, right, top, key, "fit", "fit frame", Glyph.GRID, growRight = false) - dp(5f)
        }

        // readouts: clock, battery, network
        val status = listOf(clock, battery, net).filter { it.isNotBlank() }.joinToString("  ")
        val baseline = y0 + h / 2f + small.textSize * 0.34f
        if (status.isNotBlank()) {
            small.color = state.ink.ink
            small.textAlign = Paint.Align.RIGHT
            canvas.drawText(status, right - dp(4f), baseline, small)
            small.textAlign = Paint.Align.LEFT
            right -= small.measureText(status) + dp(10f)
        }

        // the app in front, starting after the punch-hole camera
        var x = maxOf(notchLeft.toFloat(), 0f) + hPad
        if (state.inFrame) {
            val m = snap8(h * 0.20f)
            bar.color = state.ink.accent
            canvas.drawRect(x, y0 + h / 2f - m / 2f, x + m, y0 + h / 2f + m / 2f, bar)
            x += m + dp(6f)
        }
        val room = (right - x - hPad).coerceAtLeast(dp(30f))
        val name = TextUtils.ellipsize(state.title, title, room, TextUtils.TruncateAt.END).toString()
        title.color = state.ink.ink
        canvas.drawText(name, x, y0 + h / 2f + title.textSize * 0.34f, title)
    }

    // ── the dock ────────────────────────────────────────────────────────────────
    private fun drawDock(canvas: Canvas, y0: Float, h: Float) {
        val hPad = dp(7f)
        val top = y0 + dp(5f)
        val itemH = h - dp(10f)
        val labelSize = snap16(itemH * 0.30f)
        val iconSize = snap8(itemH - labelSize - dp(5f))
        small.textSize = labelSize
        title.textSize = labelSize

        var x = hPad
        x = app(canvas, x, top, itemH, labelSize, iconSize, "home", "desktop", null, Glyph.HOME) + dp(8f)

        bar.color = state.ink.mid
        canvas.drawRect(x, top + dp(6f), x + dp(1.6f), top + itemH - dp(6f), bar)
        x += dp(10f)

        // right edge belongs to the hide key: reserve it plus the open windows
        var rx = width - hPad
        rx = box(canvas, rx, top + (itemH - snap8(itemH * 0.76f)) / 2f, snap8(itemH * 0.76f), "hide", "hide", Glyph.CLOSE, growRight = false) - dp(8f)

        val windows = state.windows.takeLast(3)
        val winRoom = windows.size * dp(120f)
        val pinned = state.dock.take(6)
        val endOfPinned = (width - hPad - winRoom - dp(150f)).coerceAtLeast(x + dp(80f))
        pinned.forEach { entry ->
            val w = appWidth(entry, labelSize, iconSize)
            if (x + w > endOfPinned) return@forEach
            x = app(canvas, x, top, itemH, labelSize, iconSize, entry.action, entry.label, entry.pkg, null, entry.active) + dp(6f)
        }

        windows.reversed().forEach { entry ->
            val w = minOf(appWidth(entry, labelSize, iconSize), dp(132f))
            if (rx - w < x + dp(6f)) return@forEach
            rx = appRight(canvas, rx, top, itemH, labelSize, iconSize, entry.action, entry.label, entry.pkg, entry.active, w) - dp(6f)
        }
    }

    /** Width of a dock item: the icon column, or the label when it is wider. */
    private fun appWidth(entry: CyberdeckChrome.Bar, labelSize: Float, iconSize: Float): Float {
        val textPaint = if (entry.active) title else small
        textPaint.textSize = labelSize
        val text = textPaint.measureText(entry.label)
        return (maxOf(text, iconSize) + dp(12f)).coerceAtMost(dp(150f))
    }

    /**
     * One dock item: a paper tile with the app icon (or a pixel glyph) over its name. It is the same
     * shape as a desktop icon, only wider, so the dock reads at a glance.
     */
    private fun app(
        canvas: Canvas,
        x: Float,
        top: Float,
        itemH: Float,
        labelSize: Float,
        iconSize: Float,
        action: String,
        label: String,
        pkg: String?,
        glyph: Glyph?,
        active: Boolean = false,
    ): Float {
        val w = minOf(appWidth(CyberdeckChrome.Bar(label, action, active, pkg), labelSize, iconSize), dp(150f))
        return paintItem(canvas, x, top, w, itemH, labelSize, iconSize, action, label, pkg, glyph, active, growRight = true)
    }

    private fun appRight(
        canvas: Canvas,
        right: Float,
        top: Float,
        itemH: Float,
        labelSize: Float,
        iconSize: Float,
        action: String,
        label: String,
        pkg: String?,
        active: Boolean,
        width: Float,
    ): Float = paintItem(canvas, right - width, top, width, itemH, labelSize, iconSize, action, label, pkg, null, active, growRight = true)

    private fun paintItem(
        canvas: Canvas,
        left: Float,
        top: Float,
        w: Float,
        itemH: Float,
        labelSize: Float,
        iconSize: Float,
        action: String,
        label: String,
        pkg: String?,
        glyph: Glyph?,
        active: Boolean,
        growRight: Boolean,
    ): Float {
        val rect = RectF(left, top, left + w, top + itemH)
        // the tile: paper, ink frame, inverted when this is the window in front
        bar.color = if (active) state.ink.ink else state.ink.paper
        canvas.drawRect(rect, bar)
        bar.color = Color.BLACK
        canvas.drawRect(rect.left, rect.top, rect.right, rect.top + dp(1.6f), bar)
        canvas.drawRect(rect.left, rect.bottom - dp(1.6f), rect.right, rect.bottom, bar)
        canvas.drawRect(rect.left, rect.top, rect.left + dp(1.6f), rect.bottom, bar)
        canvas.drawRect(rect.right - dp(1.6f), rect.top, rect.right, rect.bottom, bar)

        val cx = rect.centerX()
        val iconTop = rect.top + dp(3f)
        val icon = if (pkg != null) icons(pkg) else null
        when {
            icon != null -> {
                val dst = RectF(cx - iconSize / 2f, iconTop, cx + iconSize / 2f, iconTop + iconSize)
                canvas.drawBitmap(icon, null, dst, iconPaint)
            }
            glyph != null -> {
                val g = snap8(iconSize * 0.72f)
                drawGlyph(canvas, glyph, cx - g / 2f, iconTop + (iconSize - g) / 2f, g, active)
            }
        }
        val textPaint = if (active) title else small
        textPaint.textSize = labelSize
        textPaint.color = if (active) state.ink.paper else state.ink.ink
        val room = w - dp(6f)
        val name = TextUtils.ellipsize(label, textPaint, room, TextUtils.TruncateAt.END).toString()
        canvas.drawText(name, cx - textPaint.measureText(name) / 2f, rect.bottom - dp(4f), textPaint)

        hits += rect to action
        return if (growRight) rect.right else rect.left
    }

    /**
     * Draws one key of the title rail. [x] is the free edge on the side the key grows to
     * ([growRight] = left edge, otherwise right edge); the return value is the new free edge.
     */
    private fun box(
        canvas: Canvas,
        x: Float,
        top: Float,
        size: Float,
        action: String,
        text: String,
        glyph: Glyph?,
        growRight: Boolean,
        maxW: Float = Float.MAX_VALUE,
        active: Boolean = false,
    ): Float {
        val pad = dp(6f)
        val room = (maxW - if (glyph != null) size else 0f).coerceAtLeast(dp(24f))
        val shown = if (glyph != null) "" else
            TextUtils.ellipsize(text, title, room, TextUtils.TruncateAt.END).toString()
        val boxW = if (glyph != null) size else (title.measureText(shown) + pad * 2f).coerceAtMost(maxW)
        val left = if (growRight) x else x - boxW
        val rect = RectF(left, top, left + boxW, top + size)

        bar.color = if (active) state.ink.ink else state.ink.paper
        canvas.drawRect(rect, bar)
        bar.color = Color.BLACK
        canvas.drawRect(rect.left, rect.top, rect.right, rect.top + dp(1.6f), bar)
        canvas.drawRect(rect.left, rect.bottom - dp(1.6f), rect.right, rect.bottom, bar)
        canvas.drawRect(rect.left, rect.top, rect.left + dp(1.6f), rect.bottom, bar)
        canvas.drawRect(rect.right - dp(1.6f), rect.top, rect.right, rect.bottom, bar)

        if (glyph != null) {
            val g = snap8(size * 0.74f)
            drawGlyph(canvas, glyph, rect.left + (boxW - g) / 2f, rect.top + (size - g) / 2f, g, active)
        } else {
            title.textSize = snap16(size * 0.42f)
            title.color = if (active) state.ink.paper else state.ink.ink
            canvas.drawText(shown, rect.left + pad, rect.centerY() + title.textSize * 0.34f, title)
        }
        hits += rect to action
        return if (growRight) rect.right else rect.left
    }

    /** One 16x16 pixel-art glyph, printed like [dev.retrobit.desktop.ui.components.GlyphIcon]. */
    private fun drawGlyph(canvas: Canvas, glyph: Glyph, x: Float, y: Float, size: Float, invert: Boolean) {
        val cell = size / 16f
        glyph.rows.forEachIndexed { row, cols ->
            cols.forEachIndexed { col, ch ->
                val color = when (ch) {
                    '#' -> if (invert) state.ink.paper else state.ink.ink
                    'o' -> if (invert) state.ink.ink else state.ink.paper
                    '+' -> state.ink.mid
                    else -> Color.TRANSPARENT
                }
                if (color != Color.TRANSPARENT) {
                    glyphPaint.color = color
                    canvas.drawRect(
                        x + col * cell, y + row * cell,
                        x + col * cell + cell + 0.6f, y + row * cell + cell + 0.6f,
                        glyphPaint,
                    )
                }
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                onInteract?.invoke()
                pressed = hits.firstOrNull { it.first.contains(event.x, event.y) }?.second
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP -> {
                val action = hits.firstOrNull { it.first.contains(event.x, event.y) }?.second
                pressed = null
                invalidate()
                if (action != null) {
                    performClick()
                    runCatching { onAction?.invoke(action) }
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                pressed = null
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    /** Id of the key under the finger right now (reserved for press feedback). */
    private var pressed: String? = null

    override fun performClick(): Boolean = super.performClick()
}

/**
 * The frame itself: a **black** border around the whole screen, drawn behind the rails and **not**
 * touchable, so the app underneath keeps every one of its own gestures. Black is deliberate: the
 * frame has to read as the window edge over any app, whatever the palette of the desktop is.
 */
class BorderView(context: Context) : View(context) {

    var state: CyberdeckChrome.State = CyberdeckChrome.State()

    private val density = context.resources.displayMetrics.density
    private val bar = Paint().apply { style = Paint.Style.FILL }

    override fun onDraw(canvas: Canvas) {
        val edge = (3.5f * density * state.scale.coerceIn(0.6f, 2f)).coerceAtLeast(2.5f)
        val w = width.toFloat()
        val h = height.toFloat()
        bar.color = Color.BLACK
        canvas.drawRect(0f, 0f, w, edge, bar)
        canvas.drawRect(0f, h - edge, w, h, bar)
        canvas.drawRect(0f, 0f, edge, h, bar)
        canvas.drawRect(w - edge, 0f, w, h, bar)
    }
}
