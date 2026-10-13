package dev.retrobit.desktop.shell

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import android.view.MotionEvent
import android.view.View
import dev.retrobit.desktop.R
import dev.retrobit.desktop.ui.components.Glyph

/**
 * The two rails of the cyberdeck chrome.
 *
 * Both are plain `View`s on purpose: they are added straight to the window manager by
 * [ChromeService], where a Compose host would need a lifecycle owner and a saved-state registry for
 * no gain. Everything is drawn in the launcher's own vocabulary — palette paper, ink rules, the
 * Pixel Operator face and the same 16x16 glyphs the desktop uses — so the chrome is hard to tell
 * apart from the frames drawn inside the launcher.
 */
class RailView(
    context: Context,
    /** Top rail (title + clock + window buttons) or the dock strip along the bottom. */
    private val topRail: Boolean,
) : View(context) {

    /** Called with an action id: "home", "fit", "hide", "win:<id>", "app:<pkg>". */
    var onAction: ((String) -> Unit)? = null

    var state: CyberdeckChrome.State = CyberdeckChrome.State()

    /** Clock and battery readouts, refreshed by the service once a second. */
    var clock: String = ""
    var battery: String = ""

    /** Status bar band (top rail) / navigation bar band (dock) that has to stay clear. */
    var inset: Int = 0

    private val density = context.resources.displayMetrics.density
    private val font: Typeface = runCatching { resources.getFont(R.font.pixel_operator_mono) }
        .getOrDefault(Typeface.MONOSPACE)
    private val label = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = font
        textSize = 12f * density
    }
    private val small = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = font
        textSize = 10.5f * density
    }
    private val bar = Paint().apply { style = Paint.Style.FILL }
    private val hits = ArrayList<Pair<RectF, String>>()

    private fun scale(): Float = state.scale.coerceIn(0.6f, 2f)

    private fun dp(v: Float): Float = v * density * scale()

    /** Height of the drawn rail, without [inset]. */
    fun barHeight(): Int = ((if (topRail) 30f else 46f) * density * scale()).toInt()

    /** Total height this view wants, including the system band it covers (top) or leaves free. */
    fun totalHeight(): Int = barHeight() + inset

    override fun onDraw(canvas: Canvas) {
        hits.clear()
        val ink = state.ink
        val w = width.toFloat()
        val h = barHeight().toFloat()
        val y0 = (if (topRail) inset else 0).toFloat()

        // the band the app under us uses for its own status bar: paper and hatched, so no part of
        // the app's system chrome shows through the frame
        if (topRail && inset > 0) {
            bar.color = ink.paper
            canvas.drawRect(0f, 0f, w, inset.toFloat(), bar)
            bar.color = ink.mid
            var x = 0f
            while (x < w) {
                canvas.drawRect(x, 0f, x + dp(1.4f), inset.toFloat(), bar)
                x += dp(6f)
            }
        }

        bar.color = ink.paper
        canvas.drawRect(0f, y0, w, y0 + h, bar)
        bar.color = ink.ink
        if (topRail) canvas.drawRect(0f, y0 + h - dp(1.6f), w, y0 + h, bar)
        else canvas.drawRect(0f, y0, w, y0 + dp(1.6f), bar)

        if (topRail) drawTop(canvas, w, y0, h) else drawBottom(canvas, w, y0, h)
    }

    // ── the title rail ──────────────────────────────────────────────────────────
    private fun drawTop(canvas: Canvas, w: Float, y0: Float, h: Float) {
        val pad = dp(7f)
        val size = h - dp(8f)
        val top = y0 + dp(4f)
        var right = w - pad

        right = key(canvas, right, top, size, "hide", "hide", Glyph.CLOSE, growRight = false) - dp(4f)
        right = key(canvas, right, top, size, "home", "desktop", Glyph.HOME, growRight = false) - dp(4f)
        if (state.canRefit) {
            right = key(canvas, right, top, size, "fit", "fit frame", Glyph.GRID, growRight = false) - dp(4f)
        }

        val status = listOf(clock, battery).filter { it.isNotBlank() }.joinToString("  ")
        if (status.isNotBlank()) {
            small.color = state.ink.mid
            small.textAlign = Paint.Align.RIGHT
            val y = y0 + h / 2f + small.textSize * 0.36f
            canvas.drawText(status, right - pad, y, small)
            right -= small.measureText(status) + dp(9f)
            small.textAlign = Paint.Align.LEFT
        }

        var x = pad
        if (state.inFrame) {
            bar.color = state.ink.accent
            canvas.drawRect(x, y0 + h / 2f - dp(3f), x + dp(6f), y0 + h / 2f + dp(3f), bar)
            x += dp(11f)
        }
        val room = (right - x - pad).coerceAtLeast(dp(24f))
        val title = TextUtils.ellipsize(state.title, label, room, TextUtils.TruncateAt.END).toString()
        label.color = state.ink.ink
        canvas.drawText(title, x, y0 + h / 2f + label.textSize * 0.36f, label)
    }

    // ── the dock ────────────────────────────────────────────────────────────────
    private fun drawBottom(canvas: Canvas, w: Float, y0: Float, h: Float) {
        val pad = dp(7f)
        val size = h - dp(8f)
        val top = y0 + dp(4f)
        var x = pad

        x = key(canvas, x, top, size, "home", "desktop", Glyph.HOME, growRight = true) + dp(9f)

        bar.color = state.ink.mid
        canvas.drawRect(x, top + dp(5f), x + dp(1.6f), top + size - dp(5f), bar)
        x += dp(11f)

        // right edge is reserved for the hide key
        val rightStart = w - pad - size - dp(6f)
        state.dock.take(7).forEach { entry ->
            if (x + dp(52f) > rightStart - dp(160f)) return@forEach
            x = key(canvas, x, top, size, entry.action, entry.label, null, growRight = true, maxW = dp(140f), active = entry.active) + dp(5f)
        }

        // open app windows, right to left, before the hide key
        var rx = rightStart - dp(6f)
        state.windows.takeLast(4).reversed().forEach { entry ->
            val width = dp(104f)
            if (rx - width < x + dp(6f)) return@forEach
            rx = key(canvas, rx, top, size, entry.action, entry.label, null, growRight = false, maxW = width, active = entry.active) - dp(5f)
        }

        // what is in front, and which desktop we are on
        val info = "${state.title}  ${state.desktop}/${state.desks}"
        val room = rx - x - dp(10f)
        if (room > dp(70f)) {
            small.color = state.ink.mid
            val text = TextUtils.ellipsize(info, small, room, TextUtils.TruncateAt.END).toString()
            if (text.isNotBlank()) canvas.drawText(text, x + dp(8f), y0 + h / 2f + small.textSize * 0.36f, small)
        }

        key(canvas, w - pad, top, size, "hide", "hide", Glyph.CLOSE, growRight = false)
    }

    /**
     * Draws one key. [x] is the free edge on the side the key grows to ([growRight] = left edge,
     * otherwise right edge); the return value is the new free edge on that same side.
     */
    private fun key(
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
            TextUtils.ellipsize(text, label, room, TextUtils.TruncateAt.END).toString()
        val boxW = if (glyph != null) size else (label.measureText(shown) + pad * 2f).coerceAtMost(maxW)
        val left = if (growRight) x else x - boxW
        val rect = RectF(left, top, left + boxW, top + size)

        // an active window is printed inverted, like a pressed key on the desktop
        bar.color = if (active) state.ink.ink else state.ink.paper
        canvas.drawRect(rect, bar)
        bar.color = state.ink.ink
        canvas.drawRect(rect.left, rect.top, rect.right, rect.top + dp(1.4f), bar)
        canvas.drawRect(rect.left, rect.bottom - dp(1.4f), rect.right, rect.bottom, bar)
        canvas.drawRect(rect.left, rect.top, rect.left + dp(1.4f), rect.bottom, bar)
        canvas.drawRect(rect.right - dp(1.4f), rect.top, rect.right, rect.bottom, bar)

        if (glyph != null) {
            val g = size * 0.62f
            drawGlyph(canvas, glyph, rect.left + (boxW - g) / 2f, rect.top + (size - g) / 2f, g, if (active) 1 else 0)
        } else {
            label.color = if (active) state.ink.paper else state.ink.ink
            canvas.drawText(shown, rect.left + pad, rect.centerY() + label.textSize * 0.36f, label)
        }
        hits += rect to action
        return if (growRight) rect.right else rect.left
    }

    /** One 16x16 pixel-art glyph, printed like [dev.retrobit.desktop.ui.components.GlyphIcon]. */
    private fun drawGlyph(canvas: Canvas, glyph: Glyph, x: Float, y: Float, size: Float, invert: Int) {
        val cell = size / 16f
        glyph.rows.forEachIndexed { row, cols ->
            cols.forEachIndexed { col, ch ->
                val color = when (ch) {
                    '#' -> if (invert == 1) state.ink.paper else state.ink.ink
                    'o' -> if (invert == 1) state.ink.ink else state.ink.paper
                    '+' -> state.ink.mid
                    else -> Color.TRANSPARENT
                }
                if (color != Color.TRANSPARENT) {
                    bar.color = color
                    canvas.drawRect(
                        x + col * cell, y + row * cell,
                        x + col * cell + cell + 0.5f, y + row * cell + cell + 0.5f,
                        bar,
                    )
                }
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
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

    /** Id of the key under the finger right now (drawn inverted as feedback). */
    private var pressed: String? = null

    override fun performClick(): Boolean = super.performClick()
}

/**
 * The frame itself: a paper-and-ink border around the whole screen, drawn behind the rails and
 * **not** touchable, so the app underneath keeps every one of its own gestures.
 */
class BorderView(context: Context) : View(context) {

    var state: CyberdeckChrome.State = CyberdeckChrome.State()

    private val density = context.resources.displayMetrics.density
    private val bar = Paint().apply { style = Paint.Style.FILL }

    override fun onDraw(canvas: Canvas) {
        val edge = (3f * density * state.scale.coerceIn(0.6f, 2f)).coerceAtLeast(2f)
        val w = width.toFloat()
        val h = height.toFloat()
        bar.color = state.ink.ink
        canvas.drawRect(0f, 0f, w, edge, bar)
        canvas.drawRect(0f, h - edge, w, h, bar)
        canvas.drawRect(0f, 0f, edge, h, bar)
        canvas.drawRect(w - edge, 0f, w, h, bar)
        bar.color = state.ink.mid
        val tick = edge * 4f
        canvas.drawRect(edge, edge, edge + tick, edge * 2f, bar)
        canvas.drawRect(w - edge - tick, edge, w - edge, edge * 2f, bar)
        canvas.drawRect(edge, h - edge * 2f, edge + tick, h - edge, bar)
        canvas.drawRect(w - edge - tick, h - edge * 2f, w - edge, h - edge, bar)
    }
}
