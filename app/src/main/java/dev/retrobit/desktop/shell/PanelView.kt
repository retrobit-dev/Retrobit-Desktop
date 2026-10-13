package dev.retrobit.desktop.shell

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import dev.retrobit.desktop.R
import dev.retrobit.desktop.ui.components.Glyph
import kotlin.math.round

/**
 * The quick panel: everything the cyberdeck needs while another app owns the screen, on one sheet —
 * brightness, rotation lock, night mode, the chrome switches, the app rules and the app in front.
 *
 * Drawn with `Canvas` like the rails, in the same palette, the same pixel font and the same 16x16
 * glyphs, so it reads as a part of the desktop rather than as a system dialog.
 */
class PanelView(context: Context) : View(context) {

    var onAction: ((String) -> Unit)? = null

    var state: PanelChrome.State = PanelChrome.State()

    private val density = context.resources.displayMetrics.density
    private val display: Typeface = runCatching { resources.getFont(R.font.pixel_operator_bold) }
        .getOrDefault(Typeface.DEFAULT_BOLD)
    private val mono: Typeface = runCatching { resources.getFont(R.font.pixel_operator_mono) }
        .getOrDefault(Typeface.MONOSPACE)
    private val head = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = display }
    private val text = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = mono }
    private val bar = Paint().apply { style = Paint.Style.FILL }
    private val glyphPaint = Paint().apply { style = Paint.Style.FILL }
    private val hits = ArrayList<Pair<RectF, String>>()

    /** The brightness groove, dragged with the pen / finger. */
    private var track = RectF()
    private var dragging = false

    private fun scale(): Float = state.scale.coerceIn(0.6f, 2f)

    private fun dp(v: Float): Float = v * density * scale()

    private fun snap16(px: Float): Float = maxOf(16f, round(px / 16f) * 16f)

    private fun snap8(px: Float): Float = maxOf(8f, round(px / 8f) * 8f)

    override fun onDraw(canvas: Canvas) {
        hits.clear()
        // scrim: the desktop behind the panel is dimmed, like a system sheet
        bar.color = Color.argb(150, 0, 0, 0)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bar)

        val pad = dp(20f)
        val cardW = (width - pad * 2f).coerceAtMost(dp(1040f))
        val cardH = (height - pad * 2f).coerceAtMost(dp(640f))
        val left = (width - cardW) / 2f
        val top = (height - cardH) / 2f
        val card = RectF(left, top, left + cardW, top + cardH)

        bar.color = state.paper
        canvas.drawRect(card, bar)
        border(canvas, card, dp(3f), Color.BLACK)

        head.textSize = snap16(dp(17f))
        text.textSize = snap16(dp(13f))

        var y = card.top + dp(14f)
        val x = card.left + dp(20f)
        val right = card.right - dp(20f)

        // ── header ──────────────────────────────────────────────────────────────
        head.color = state.ink
        canvas.drawText("QUICK PANEL", x, y + head.textSize * 0.82f, head)
        text.color = state.mid
        canvas.drawText(
            "no need to leave the app - " + (if (state.chromeOn) "cyberdeck chrome on" else "chrome off"),
            x + head.measureText("QUICK PANEL") + dp(14f), y + head.textSize * 0.82f, text,
        )
        key(canvas, right - snap8(dp(34f)), y, snap8(dp(34f)), "close", glyph = Glyph.CLOSE)
        if (state.clock.isNotBlank()) {
            // live clock, right-aligned in front of the close key: the panel is the cyberdeck HUD
            text.textAlign = Paint.Align.RIGHT
            canvas.drawText(state.clock, right - snap8(dp(34f)) - dp(12f), y + head.textSize * 0.82f, text)
            text.textAlign = Paint.Align.LEFT
        }
        y += dp(34f) + dp(6f)
        rule(canvas, x, right, y)
        y += dp(10f)

        // ── brightness ──────────────────────────────────────────────────────────
        y = section(canvas, x, y, "BRIGHTNESS", state.brightness?.let { "${(it * 100).toInt()}%" } ?: "device")
        val barH = snap8(dp(26f))
        val autoW = snap8(dp(74f))
        val minusW = snap8(dp(38f))
        val plusW = snap8(dp(38f))
        val trackRight = right - autoW - minusW - plusW - dp(18f)
        track = RectF(x + dp(2f), y, trackRight, y + barH)
        bar.color = state.paper
        canvas.drawRect(track, bar)
        border(canvas, track, dp(1.6f), Color.BLACK)
        val level = state.brightness ?: 0.5f
        bar.color = state.ink
        canvas.drawRect(track.left + dp(2f), track.top + dp(2f), track.left + dp(2f) + (track.width() - dp(4f)) * level, track.bottom - dp(2f), bar)
        text.color = state.ink
        canvas.drawText("B", track.left + dp(6f), track.centerY() + text.textSize * 0.34f, text)
        hits += RectF(track) to "bright:drag"
        var bx = trackRight + dp(6f)
        key(canvas, bx, y, minusW, "bright:-", label = "B-")
        bx += minusW + dp(4f)
        key(canvas, bx, y, plusW, "bright:+", label = "B+")
        bx += plusW + dp(6f)
        key(canvas, bx, y, autoW, "bright:auto", label = "auto", active = state.brightness == null)
        y += barH + dp(6f)
        y = hint(canvas, x, y, "drag the groove, or use B- / B+ · auto means the tablet decides")
        y += dp(6f)

        // ── display ─────────────────────────────────────────────────────────────
        y = section(canvas, x, y, "DISPLAY", null)
        val q = (right - x - dp(12f)) / 4f
        key(canvas, x, y, q, "rotate:toggle", glyph = Glyph.ROTATE, label = if (state.rotateLocked) "rotate locked" else "auto rotate", active = state.rotateLocked)
        key(canvas, x + q + dp(4f), y, q, "night:toggle", glyph = Glyph.SPARK, label = if (state.night) "night on" else "night off", active = state.night)
        key(canvas, x + (q + dp(4f)) * 2f, y, q, "chrome:toggle", glyph = Glyph.GRID, label = if (state.chromeOn) "chrome on" else "chrome off", active = state.chromeOn)
        key(canvas, x + (q + dp(4f)) * 3f, y, q, "rails:toggle", glyph = Glyph.EYE, label = "hide rails now")
        val qh = snap8(dp(30f))
        y += qh + dp(10f)

        // ── rails ───────────────────────────────────────────────────────────────
        y = section(canvas, x, y, "RAILS", null)
        val half = (right - x - dp(8f)) / 2f
        var rx = x
        key(canvas, rx, y, half * 0.44f, "mode:pinned", glyph = Glyph.STAR, label = "pinned apps", active = state.railMode != "used")
        key(canvas, rx + half * 0.46f, y, half * 0.54f, "mode:used", glyph = Glyph.BARS, label = "most used first", active = state.railMode == "used")
        rx = x + half + dp(8f)
        key(canvas, rx, y, half / 2f - dp(4f), "rail:top", label = "title rail", active = state.topRail)
        key(canvas, rx + half / 2f, y, half / 2f - dp(4f), "rail:dock", label = "dock", active = state.dockOn)
        y += snap8(dp(34f)) + dp(6f)

        key(canvas, x, y, half, "autohide:toggle", glyph = Glyph.EYE, label = if (state.autoHide) "auto-hide on" else "auto-hide off", active = state.autoHide)
        rx = x + half + dp(8f)
        val seg = (half - dp(12f)) / 4f
        listOf(4, 6, 10, 20).forEachIndexed { i, secs ->
            key(canvas, rx + i * (seg + dp(4f)), y, seg, "autohide:$secs", label = "${secs}s", active = state.autoHide && state.autoHideSecs == secs)
        }
        y += snap8(dp(30f)) + dp(10f)

        // ── the app in front ────────────────────────────────────────────────────
        y = section(canvas, x, y, "IN FRAME NOW", if (state.apps.isEmpty()) "nothing is open" else null)
        if (state.apps.isEmpty()) {
            y = hint(canvas, x, y, "open an app from the desktop dock and it appears here: one tap brings it back and fits it into its frame, a hold opens its shell.")
        } else {
            var cx = x
            state.apps.take(4).forEach { app ->
                val w = minOf(text.measureText(app.label) + dp(30f), dp(280f))
                if (cx + w > right) return@forEach
                chip(canvas, cx, y, w, snap8(dp(30f)), app.label, "win:" + app.pkg, Glyph.HOME)
                cx += w + dp(6f)
            }
            y += snap8(dp(30f)) + dp(6f)
            y = hint(canvas, x, y, "tap = bring it back and fit it into its frame · hold = open its shell here")
            y += dp(6f)
        }

        // ── per-app rules ───────────────────────────────────────────────────────
        y = section(canvas, x, y, "NO CHROME FOR", if (state.noChrome.isEmpty()) "every app gets the frame" else null)
        val rules = state.noChrome.take(6)
        if (rules.isNotEmpty()) {
            var cx = x
            rules.forEach { pkg ->
                val label = state.apps.firstOrNull { it.pkg == pkg }?.label ?: pkg.substringAfterLast('.')
                val w = minOf(text.measureText(label) + dp(30f), dp(240f))
                if (cx + w > right) return@forEach
                chip(canvas, cx, y, w, snap8(dp(30f)), label, "rule:$pkg", Glyph.CLOSE)
                cx += w + dp(6f)
            }
            y += snap8(dp(30f)) + dp(6f)
        }
        hint(canvas, x, y, "hold an app in the dock (while chrome is up) to add it here · tap a chip to give it the frame back")

        // ── footer ──────────────────────────────────────────────────────────────
        val foot = card.bottom - dp(52f)
        rule(canvas, x, right, foot)
        key(canvas, x, foot + dp(10f), snap8(dp(34f)), "close", label = "close panel")
        text.color = state.mid
        text.textSize = snap16(dp(12f))
        val note = "esc or a tap outside closes too"
        canvas.drawText(note, right - text.measureText(note), foot + dp(10f) + dp(34f) * 0.62f, text)
    }

    // ── small helpers ───────────────────────────────────────────────────────────
    private fun border(canvas: Canvas, r: RectF, w: Float, color: Int) {
        bar.color = color
        canvas.drawRect(r.left, r.top, r.right, r.top + w, bar)
        canvas.drawRect(r.left, r.bottom - w, r.right, r.bottom, bar)
        canvas.drawRect(r.left, r.top, r.left + w, r.bottom, bar)
        canvas.drawRect(r.right - w, r.top, r.right, r.bottom, bar)
    }

    private fun rule(canvas: Canvas, x: Float, right: Float, y: Float) {
        bar.color = state.mid
        canvas.drawRect(x, y, right, y + dp(1.4f), bar)
    }

    private fun section(canvas: Canvas, x: Float, y: Float, title: String, note: String?): Float {
        head.textSize = snap16(dp(13f))
        head.color = state.ink
        canvas.drawText(title, x, y + head.textSize * 0.86f, head)
        if (note != null) {
            text.textSize = snap16(dp(12f))
            text.color = state.mid
            canvas.drawText(note, x + head.measureText(title) + dp(10f), y + head.textSize * 0.86f, text)
            text.textSize = snap16(dp(13f))
        }
        return y + head.textSize + dp(8f)
    }

    private fun hint(canvas: Canvas, x: Float, y: Float, message: String): Float {
        text.textSize = snap16(dp(12f))
        text.color = state.mid
        canvas.drawText(message, x, y + text.textSize * 0.9f, text)
        text.textSize = snap16(dp(13f))
        return y + text.textSize + dp(2f)
    }

    /** One labelled key: a pixel glyph (or a label) that runs an action. */
    private fun key(
        canvas: Canvas,
        x: Float,
        y: Float,
        w: Float,
        action: String,
        glyph: Glyph? = null,
        label: String? = null,
        active: Boolean = false,
    ) {
        val h = snap8(dp(30f))
        val rect = RectF(x, y, x + w, y + h)
        bar.color = if (active) state.ink else state.paper
        canvas.drawRect(rect, bar)
        border(canvas, rect, dp(1.6f), Color.BLACK)
        var tx = rect.left + dp(7f)
        var room = w - dp(14f)
        if (glyph != null) {
            val g = snap8(h * 0.62f)
            drawGlyph(canvas, glyph, tx, rect.top + (h - g) / 2f, g, active)
            tx += g + dp(6f)
            room -= g + dp(6f)
        }
        if (label != null && room > dp(10f)) {
            text.textSize = snap16(h * 0.42f)
            text.color = if (active) state.paper else state.ink
            val shown = TextUtils.ellipsize(label, text, room, TextUtils.TruncateAt.END).toString()
            canvas.drawText(shown, tx, rect.centerY() + text.textSize * 0.34f, text)
            text.textSize = snap16(dp(13f))
        }
        hits += rect to action
    }

    /** One app chip: a name plus a small mark. */
    private fun chip(canvas: Canvas, x: Float, y: Float, w: Float, h: Float, label: String, action: String, glyph: Glyph) {
        val rect = RectF(x, y, x + w, y + h)
        bar.color = state.paper
        canvas.drawRect(rect, bar)
        border(canvas, rect, dp(1.6f), Color.BLACK)
        val g = snap8(h * 0.56f)
        drawGlyph(canvas, glyph, rect.left + dp(6f), rect.top + (h - g) / 2f, g, false)
        text.textSize = snap16(h * 0.42f)
        text.color = state.ink
        val room = w - g - dp(16f)
        val shown = TextUtils.ellipsize(label, text, room, TextUtils.TruncateAt.END).toString()
        canvas.drawText(shown, rect.left + dp(10f) + g, rect.centerY() + text.textSize * 0.34f, text)
        text.textSize = snap16(dp(13f))
        hits += rect to action
    }

    private fun drawGlyph(canvas: Canvas, glyph: Glyph, x: Float, y: Float, size: Float, invert: Boolean) {
        val cell = size / 16f
        glyph.rows.forEachIndexed { row, cols ->
            cols.forEachIndexed { col, ch ->
                val color = when (ch) {
                    '#' -> if (invert) state.paper else state.ink
                    'o' -> if (invert) state.ink else state.paper
                    '+' -> state.mid
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

    // ── input ───────────────────────────────────────────────────────────────────
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                dragging = hits.firstOrNull { it.second == "bright:drag" && it.first.contains(event.x, event.y) } != null
                if (dragging) {
                    moveBrightness(event.x)
                    return true
                }
                val hit = hits.firstOrNull { it.first.contains(event.x, event.y) }
                if (hit == null) {
                    // a tap on the dimmed desktop closes the panel
                    performClick()
                    onAction?.invoke("close")
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (dragging) moveBrightness(event.x)
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (dragging) {
                    dragging = false
                    return true
                }
                val hit = hits.firstOrNull { it.first.contains(event.x, event.y) }
                if (hit != null) {
                    performClick()
                    onAction?.invoke(hit.second)
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun moveBrightness(x: Float) {
        if (track.width() <= 0f) return
        val value = ((x - track.left) / track.width()).coerceIn(0.05f, 1f)
        onAction?.invoke("bright:set:%.2f".format(java.util.Locale.US, value))
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE) {
            onAction?.invoke("close")
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun performClick(): Boolean = super.performClick()
}
