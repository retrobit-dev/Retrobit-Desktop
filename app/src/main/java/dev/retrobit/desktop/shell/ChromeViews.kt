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

    /** Called with an action id: "home", "hide", "panel", "kbd", "eye", "rail:mode", "app:<pkg>". */
    var onAction: ((String) -> Unit)? = null

    /** Called on any press, so the service can keep the rail awake / bring it back. */
    var onInteract: (() -> Unit)? = null

    /** A key that was held: the dock uses this for "shell here" / pin and unpin. */
    var onHold: ((String) -> Unit)? = null

    /** A tap on an empty spot of a rail: show or hide the rails right away. */
    var onEmptyTap: (() -> Unit)? = null

    var state: CyberdeckChrome.State = CyberdeckChrome.State()

    /** Clock, battery and network readouts, refreshed by the service. */
    var clock: String = ""

    /** Battery level, -1 when the device does not report one: drawn as a small gauge. */
    var batteryPercent: Int = -1
    var charging: Boolean = false
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
    private val iconPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    // the dock prints app icons in grayscale, like the rest of the e-paper shell; a native theme
    // shows them the way its own dock does - in colour
    private val grayFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
    private val hits = ArrayList<Pair<RectF, String>>()

    /** Launcher scale × the chrome's own size: rail height, keys, text and icons all follow. */
    private fun scale(): Float = (state.scale * state.size).coerceIn(0.6f, 2.4f)

    private fun dp(v: Float): Float = v * density * scale()

    /** Pixel Operator is crisp only when the size is a multiple of its 16 px em. */
    private fun snap16(px: Float): Float = maxOf(16f, round(px / 16f) * 16f)

    private fun snap8(px: Float): Float = maxOf(8f, round(px / 8f) * 8f)

    /** Window chrome of the active palette, or `null` while the rails are printed in ink on paper. */
    private val skin: CyberdeckChrome.Skin? get() = state.skin

    /**
     * Foreground of the surface being drawn. On a native theme the top rail is that desktop's
     * title bar, so its text uses the theme's title colour; the dock sits on the window face, where
     * the palette ink is already the right colour.
     */
    private fun fg(active: Boolean): Int {
        val s = skin ?: return if (active) state.ink.paper else state.ink.ink
        if (active) return s.plateText
        return if (topRail) s.titleText else state.ink.ink
    }

    /** What a key or a tile sits on: the surface itself, unless it is switched on. */
    private fun bg(active: Boolean): Int {
        val s = skin ?: return if (active) state.ink.ink else state.ink.paper
        return if (active) s.plate else Color.TRANSPARENT
    }

    /** Hairline of a key that is not switched on (a native key is an outline, not an ink box). */
    private fun hair(active: Boolean): Int {
        val s = skin ?: return Color.BLACK
        return if (active) s.border else SkinPaint.hairline(fg(false), 0.35f)
    }

    /** Readouts riding the top rail: the theme's title colour on a native strip. */
    private fun railText(): Int = skin?.titleText ?: state.ink.ink
    private fun railSoft(): Int = skin?.soft ?: state.ink.mid

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
        val skin = this.skin
        if (topRail && inset > 0) {
            // the band the app under us uses for its status bar, plus the clock, so no part of the
            // app's own system chrome shows through the frame: printed and hatched on paper, the
            // plain face of the theme on a native desktop
            if (skin != null) {
                bar.color = skin.face
                canvas.drawRect(0f, 0f, width.toFloat(), inset.toFloat(), bar)
            } else {
                bar.color = state.ink.paper
                canvas.drawRect(0f, 0f, width.toFloat(), inset.toFloat(), bar)
                bar.color = state.ink.mid
                var x = 0f
                while (x < width) {
                    canvas.drawRect(x, 0f, x + dp(1.4f), inset.toFloat(), bar)
                    x += dp(7f)
                }
            }
        }

        if (skin != null) {
            // a native desktop: the top rail is its title bar (gradient or flat strip) and the dock
            // is its bar - both run the full width, so only the face and the gradient matter here
            val strip = RectF(0f, y0, width.toFloat(), height.toFloat())
            SkinPaint.face(
                canvas, bar, strip, 0f,
                if (topRail) skin.titleTop else skin.face,
                if (topRail) skin.titleBottom else skin.face,
            )
        } else {
            bar.color = state.ink.paper
            canvas.drawRect(0f, y0, width.toFloat(), height.toFloat(), bar)
        }
        bar.color = Color.BLACK
        if (topRail) canvas.drawRect(0f, height - dp(2f), width.toFloat(), height.toFloat(), bar)
        else canvas.drawRect(0f, 0f, width.toFloat(), dp(2f), bar)

        title.textSize = snap16(dp(15f))
        small.textSize = snap16(dp(13f))

        if (topRail) drawTop(canvas, y0, h) else drawDock(canvas, y0, h)
        if (state.flash.isNotBlank()) flash(canvas, y0, h)
    }

    /** A short confirmation ("pinned to the dock: Files") printed centre-rail, inverted. */
    private fun flash(canvas: Canvas, y0: Float, h: Float) {
        val size = snap16(dp(13f))
        small.textSize = size
        val room = (width - dp(140f)).coerceAtLeast(dp(120f))
        val shown = TextUtils.ellipsize(state.flash, small, room, TextUtils.TruncateAt.END).toString()
        val w = small.measureText(shown) + dp(20f)
        val rect = RectF((width - w) / 2f, y0 + dp(4f), (width + w) / 2f, y0 + h - dp(4f))
        val skin = this.skin
        if (skin != null) {
            // a native desktop raises a small rounded toast instead of printing an inverted bar
            val r = skin.tile(1f, 12f)
            SkinPaint.shadow(canvas, bar, rect, r, dp(3f))
            SkinPaint.face(canvas, bar, rect, r, skin.plate, skin.plate)
            SkinPaint.frame(canvas, bar, rect, r, maxOf(1f, dp(1f)), skin.border)
            small.color = skin.plateText
            small.textAlign = Paint.Align.LEFT
            canvas.drawText(shown, rect.left + dp(10f), rect.centerY() + size * 0.34f, small)
            return
        }
        bar.color = state.ink.ink
        canvas.drawRect(rect, bar)
        bar.color = Color.BLACK
        canvas.drawRect(rect.left, rect.top, rect.right, rect.top + dp(1.6f), bar)
        canvas.drawRect(rect.left, rect.bottom - dp(1.6f), rect.right, rect.bottom, bar)
        small.color = state.ink.paper
        small.textAlign = Paint.Align.LEFT
        canvas.drawText(shown, rect.left + dp(10f), rect.centerY() + size * 0.34f, small)
    }

    /** What is left of an auto-hidden rail: a thin paper strip with a grip, still touchable. */
    private fun drawHandle(canvas: Canvas) {
        val h = height.toFloat()
        val skin = this.skin
        bar.color = skin?.face ?: state.ink.paper
        canvas.drawRect(0f, 0f, width.toFloat(), h, bar)
        bar.color = Color.BLACK
        if (topRail) canvas.drawRect(0f, h - dp(2f), width.toFloat(), h, bar)
        else canvas.drawRect(0f, 0f, width.toFloat(), dp(2f), bar)
        bar.color = skin?.let { SkinPaint.hairline(it.border, 0.75f) } ?: state.ink.ink
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
        right = box(canvas, right, top, key, "eye", "hide rails", Glyph.EYE, growRight = false) - dp(5f)
        right = box(canvas, right, top, key, "home", "desktop", Glyph.HOME, growRight = false) - dp(5f)
        right = box(canvas, right, top, key, "panel", "quick panel", Glyph.SETTINGS, growRight = false) - dp(5f)
        right = box(canvas, right, top, key, "kbd", "keyboard", Glyph.KEYBOARD, growRight = false) - dp(5f)

        // readouts, right to left: the live clock, the battery gauge, the signal bars and transport
        val baseline = y0 + h / 2f + small.textSize * 0.34f
        val cy = y0 + h / 2f
        // On a narrow screen (portrait, or a large UI scale) the keys and the readouts alone can
        // fill the rail: keep a strip for the app title and drop readouts that would eat into it.
        val titleStart = maxOf(notchLeft.toFloat(), 0f) + hPad
        val titleGuard = titleStart + dp(140f)
        small.color = railText()
        if (clock.isNotBlank()) {
            small.textAlign = Paint.Align.RIGHT
            canvas.drawText(clock, right - dp(4f), baseline, small)
            right -= small.measureText(clock) + dp(12f)
        }
        small.textAlign = Paint.Align.LEFT
        if (net.isNotBlank() && right - small.measureText(net) - dp(35f) > titleGuard) {
            small.textAlign = Paint.Align.RIGHT
            canvas.drawText(net, right, baseline, small)
            right -= small.measureText(net) + dp(6f)
            small.textAlign = Paint.Align.LEFT
            val barsW = dp(15f)
            drawSignal(canvas, right - barsW, cy, net)
            right -= barsW + dp(14f)
        } else if (net.isNotBlank() && right - dp(33f) > titleGuard) {
            // no room for the transport label: the bars alone still say how good the link is
            val barsW = dp(15f)
            drawSignal(canvas, right - barsW, cy, net)
            right -= barsW + dp(14f)
        }
        if (batteryPercent >= 0 && right - dp(38f) > titleGuard) {
            val batW = dp(26f)
            drawBattery(canvas, right - batW, cy, batteryPercent, charging)
            right -= batW + dp(12f)
        }

        // the app in front, starting after the punch-hole camera
        var x = maxOf(notchLeft.toFloat(), 0f) + hPad
        if (state.inFrame) {
            val m = snap8(h * 0.20f)
            bar.color = skin?.accent ?: state.ink.accent
            canvas.drawRect(x, y0 + h / 2f - m / 2f, x + m, y0 + h / 2f + m / 2f, bar)
            x += m + dp(6f)
        }
        val room = (right - x - hPad).coerceAtLeast(dp(30f))
        val name = TextUtils.ellipsize(state.title, title, room, TextUtils.TruncateAt.END).toString()
        title.color = fg(false)
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

        // the divider doubles as the pinned / most-used-first switch of the dock
        val used = state.railMode == "used"
        val modeW = dp(34f)
        val modeRect = RectF(x, top, x + modeW, top + itemH)
        tile(canvas, modeRect, active = false)
        val mg = snap8(itemH * 0.52f)
        drawGlyph(canvas, if (used) Glyph.STAR else Glyph.BARS, modeRect.centerX() - mg / 2f, modeRect.centerY() - mg / 2f, mg, false)
        hits += modeRect to "rail:mode"
        x = modeRect.right + dp(8f)

        // right edge belongs to the hide key: reserve it before the app list
        var rx = width - hPad
        val dockKey = snap8(itemH * 0.76f)
        val dockKeyTop = top + (itemH - dockKey) / 2f
        rx = box(canvas, rx, dockKeyTop, dockKey, "hide", "hide", Glyph.CLOSE, growRight = false) - dp(6f)
        rx = box(canvas, rx, dockKeyTop, dockKey, "eye", "hide rails", Glyph.EYE, growRight = false) - dp(6f)
        rx = box(canvas, rx, dockKeyTop, dockKey, "panel", "quick panel", Glyph.SETTINGS, growRight = false) - dp(6f)
        rx = box(canvas, rx, dockKeyTop, dockKey, "kbd", "keyboard", Glyph.KEYBOARD, growRight = false) - dp(8f)

        // apps, left to right: an app fills the screen now, so the dock lists what to open - not
        // which windows are up (there are none)
        val pinned = state.dock.take(8)
        val endOfPinned = (rx - dp(20f)).coerceAtLeast(x + dp(80f))
        pinned.forEach { entry ->
            val w = appWidth(entry, labelSize, iconSize)
            if (x + w > endOfPinned) return@forEach
            x = app(canvas, x, top, itemH, labelSize, iconSize, entry.action, entry.label, entry.pkg, null, entry.active, entry.pinned) + dp(6f)
        }
    }

    /**
     * Signal strength for a transport: four bars, filled to match how good the link is
     * (wifi and ethernet full, cellular three, anything else two, offline none).
     */
    private fun drawSignal(canvas: Canvas, x: Float, cy: Float, net: String) {
        val bars = when (net) {
            "WIFI", "ETH" -> 4
            "CELL", "VPN" -> 3
            "NET" -> 2
            else -> 0
        }
        val w = dp(2.6f)
        val gap = dp(1.4f)
        val bottom = cy + dp(6f)
        for (i in 0 until 4) {
            val barH = dp(3f) + dp(3f) * i
            val bx = x + i * (w + gap)
            bar.color = if (i < bars) railText() else railSoft()
            canvas.drawRect(bx, bottom - barH, bx + w, bottom, bar)
        }
    }

    /** A palm-sized battery gauge: outline, proportional fill and a bolt when it is charging. */
    private fun drawBattery(canvas: Canvas, x: Float, cy: Float, percent: Int, charging: Boolean) {
        val w = dp(20f)
        val hgt = dp(10f)
        val top = cy - hgt / 2f
        bar.color = railText()
        // outline, 1.5 dp thick, plus the little nub on the right
        canvas.drawRect(x, top, x + w, top + dp(1.5f), bar)
        canvas.drawRect(x, top + hgt - dp(1.5f), x + w, top + hgt, bar)
        canvas.drawRect(x, top, x + dp(1.5f), top + hgt, bar)
        canvas.drawRect(x + w - dp(1.5f), top, x + w, top + hgt, bar)
        canvas.drawRect(x + w, cy - dp(2f), x + w + dp(2.5f), cy + dp(2f), bar)
        canvas.drawRect(x + w + dp(2.5f), cy - dp(1f), x + w + dp(3.5f), cy + dp(1f), bar)
        val inner = (w - dp(5f)).coerceAtLeast(1f)
        val fill = (inner * (percent.coerceIn(0, 100) / 100f)).coerceAtLeast(if (percent > 0) dp(1.5f) else 0f)
        if (fill > 0f) canvas.drawRect(x + dp(2.5f), top + dp(2.5f), x + dp(2.5f) + fill, top + hgt - dp(2.5f), bar)
        if (charging) {
            // bolt: two offset strokes, readable at 10 dp
            val bx = x + w / 2f
            canvas.drawRect(bx - dp(1.5f), top - dp(5f), bx + dp(2.5f), top - dp(3.5f), bar)
            canvas.drawRect(bx - dp(3.5f), top - dp(3.5f), bx + dp(0.5f), top - dp(2f), bar)
        }
    }

    /**
     * One key or tile: on e-paper a paper box with four ink rules (inverted when it is on), on a
     * native theme the rounded outline that desktop would draw - filled with the selection plate
     * only while it is on.
     */
    private fun tile(canvas: Canvas, rect: RectF, active: Boolean) {
        val skin = this.skin
        if (skin != null) {
            val r = skin.tile()
            if (active) SkinPaint.face(canvas, bar, rect, r, skin.plate, skin.plate)
            val width = maxOf(1f, dp(if (skin.borderDp > 0f) skin.borderDp.coerceAtMost(1.6f) else 1f))
            SkinPaint.frame(canvas, bar, rect, r, width, hair(active))
            return
        }
        bar.color = bg(active)
        canvas.drawRect(rect, bar)
        bar.color = Color.BLACK
        canvas.drawRect(rect.left, rect.top, rect.right, rect.top + dp(1.6f), bar)
        canvas.drawRect(rect.left, rect.bottom - dp(1.6f), rect.right, rect.bottom, bar)
        canvas.drawRect(rect.left, rect.top, rect.left + dp(1.6f), rect.bottom, bar)
        canvas.drawRect(rect.right - dp(1.6f), rect.top, rect.right, rect.bottom, bar)
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
        pinned: Boolean = false,
    ): Float {
        val w = minOf(appWidth(CyberdeckChrome.Bar(label, action, active, pkg), labelSize, iconSize), dp(150f))
        return paintItem(canvas, x, top, w, itemH, labelSize, iconSize, action, label, pkg, glyph, active, growRight = true, pinned = pinned)
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
        pinned: Boolean,
        width: Float,
    ): Float = paintItem(canvas, right - width, top, width, itemH, labelSize, iconSize, action, label, pkg, null, active, growRight = true, pinned = pinned)

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
        pinned: Boolean = false,
    ): Float {
        val rect = RectF(left, top, left + w, top + itemH)
        // the tile: paper with an ink frame on e-paper, a rounded plate on a native dock, filled
        // with the theme's selection colour when this is the app in front
        tile(canvas, rect, active)

        val cx = rect.centerX()
        val iconTop = rect.top + dp(3f)
        val icon = if (pkg != null) icons(pkg) else null
        when {
            icon != null -> {
                val dst = RectF(cx - iconSize / 2f, iconTop, cx + iconSize / 2f, iconTop + iconSize)
                iconPaint.colorFilter = if (skin != null) null else grayFilter
                canvas.drawBitmap(icon, null, dst, iconPaint)
            }
            glyph != null -> {
                val g = snap8(iconSize * 0.72f)
                drawGlyph(canvas, glyph, cx - g / 2f, iconTop + (iconSize - g) / 2f, g, active)
            }
        }
        val textPaint = if (active) title else small
        textPaint.textSize = labelSize
        textPaint.color = fg(active)
        val room = w - dp(6f) - if (pinned) dp(9f) else 0f
        if (pinned) {
            // a small filled square marks "this one is pinned": a hold takes it off the dock
            bar.color = fg(active)
            val m = snap8(dp(5f) * 1.2f)
            canvas.drawRect(rect.left + dp(4f), rect.top + dp(4f), rect.left + dp(4f) + m, rect.top + dp(4f) + m, bar)
        }
        val name = TextUtils.ellipsize(label, textPaint, room, TextUtils.TruncateAt.END).toString()
        canvas.drawText(name, cx - textPaint.measureText(name) / 2f, rect.bottom - dp(4f), textPaint)

        hits += hitArea(rect) to action
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

        tile(canvas, rect, active)

        if (glyph != null) {
            val g = snap8(size * 0.74f)
            drawGlyph(canvas, glyph, rect.left + (boxW - g) / 2f, rect.top + (size - g) / 2f, g, active)
        } else {
            title.textSize = snap16(size * 0.42f)
            title.color = fg(active)
            canvas.drawText(shown, rect.left + pad, rect.centerY() + title.textSize * 0.34f, title)
        }
        hits += hitArea(rect) to action
        return if (growRight) rect.right else rect.left
    }

    /**
     * The touch target of a key: the drawn box grown by a few dp, because a pen and a thumb are both
     * wider than a 30 dp key. Clamped to the rail so a target never swallows its neighbour.
     */
    private fun hitArea(r: RectF): RectF {
        val growX = dp(3f)
        val growY = dp(4f)
        return RectF(
            (r.left - growX).coerceAtLeast(0f),
            (r.top - growY).coerceAtLeast(0f),
            (r.right + growX).coerceAtMost(width.toFloat()),
            (r.bottom + growY).coerceAtMost(height.toFloat()),
        )
    }

    /** One 16x16 pixel-art glyph, printed like [dev.retrobit.desktop.ui.components.GlyphIcon]. */
    private fun drawGlyph(canvas: Canvas, glyph: Glyph, x: Float, y: Float, size: Float, invert: Boolean) {
        val cell = size / 16f
        val ink = fg(invert)
        // a native key is an outline: the "hole" pixels of a glyph show the surface through, and
        // the plate of a key that is on fills them instead
        val paper = if (skin != null) bg(invert) else if (invert) state.ink.ink else state.ink.paper
        val mid = if (skin != null) SkinPaint.hairline(ink, 0.55f) else state.ink.mid
        glyph.rows.forEachIndexed { row, cols ->
            cols.forEachIndexed { col, ch ->
                val color = when (ch) {
                    '#' -> ink
                    'o' -> paper
                    '+' -> mid
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
                downAt = System.currentTimeMillis()
                downX = event.x
                downY = event.y
                pressed = hits.firstOrNull { it.first.contains(event.x, event.y) }?.second
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                // a finger that slid off the key cancels it: a rail must never fire by accident
                val action = hits.firstOrNull { it.first.contains(event.x, event.y) }?.second
                if (action != pressed) {
                    pressed = null
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                val action = hits.firstOrNull { it.first.contains(event.x, event.y) }?.second
                val held = System.currentTimeMillis() - downAt
                val still = kotlin.math.abs(event.x - downX) < dp(14f) &&
                    kotlin.math.abs(event.y - downY) < dp(14f)
                pressed = null
                invalidate()
                performClick()
                when {
                    action != null && still && held >= holdMs -> runCatching { onHold?.invoke(action) }
                    action != null && still -> runCatching { onAction?.invoke(action) }
                    action == null && still -> runCatching { onEmptyTap?.invoke() }
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
    private var downAt = 0L
    private var downX = 0f
    private var downY = 0f

    /** How long a key has to rest before it counts as "held" (dock pin / shell). */
    private var holdMs = 420L

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
