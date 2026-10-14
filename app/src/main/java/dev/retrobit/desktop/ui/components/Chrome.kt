package dev.retrobit.desktop.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.staticCompositionLocalOf
import dev.retrobit.desktop.ui.theme.ChromePattern
import dev.retrobit.desktop.ui.theme.PixelDisplay
import dev.retrobit.desktop.ui.theme.PixelMono
import dev.retrobit.desktop.ui.input.LocalKeyboardRequester
import dev.retrobit.desktop.ui.theme.LocalPalette
import dev.retrobit.desktop.ui.theme.rememberChromeBrush
import dev.retrobit.desktop.ui.theme.rememberPanelTexture

/** Global UI scale (settings › display › scale). */
val LocalUiScale = staticCompositionLocalOf { 1f }

@Composable
fun sp(base: Float): TextUnit = (base * LocalUiScale.current).sp

/**
 * Pixel Operator is drawn on a 16 px em grid: one glyph pixel maps to exactly one device pixel
 * when the rendered font size is a multiple of 16 px. Anything in between resamples the glyphs and
 * the crisp pixel look is lost, so every text size request is snapped to that grid.
 * 13sp on a xxhdpi tablet becomes 32 px (2 font pixels per glyph pixel) instead of 39 px.
 */
@Composable
fun pixelSp(size: Float): TextUnit {
    val d = androidx.compose.ui.platform.LocalDensity.current
    val unit = d.density * d.fontScale
    val requestedPx = size * LocalUiScale.current * unit
    val snappedPx = maxOf(16f, kotlin.math.round(requestedPx / 16f) * 16f)
    return (snappedPx / unit).sp
}

@Composable
fun dp(base: Float): Dp = (base * LocalUiScale.current).dp

/**
 * Scaled dp converted to pixels. Must be called from a composable context (e.g. to precompute
 * layout metrics before handing them to a non-composable LaunchedEffect).
 */
@Composable
fun px(base: Float): Float {
    val d = androidx.compose.ui.platform.LocalDensity.current
    val value = dp(base)
    return with(d) { value.toPx() }
}

/** Same as [px] but with a raw dp value that is not scaled by the UI scale setting. */
@Composable
fun rawPx(base: Float): Float {
    val d = androidx.compose.ui.platform.LocalDensity.current
    return with(d) { base.dp.toPx() }
}

/** Text in the e-paper style: monospace body, pixel display font for headings. */
@Composable
fun RetroText(
    text: String,
    modifier: Modifier = Modifier,
    size: Float = 13f,
    color: Color? = null,
    weight: FontWeight = FontWeight.Normal,
    display: Boolean = false,
    maxLines: Int = 1,
    overflow: TextOverflow = TextOverflow.Ellipsis,
) {
    val p = LocalPalette.current
    BasicText(
        text = text,
        modifier = modifier,
        maxLines = maxLines,
        overflow = overflow,
        style = TextStyle(
            color = color ?: p.ink,
            fontSize = pixelSp(if (display) size * 1.45f else size),
            fontFamily = if (display) PixelDisplay else PixelMono,
            fontWeight = weight,
            letterSpacing = 0.sp,
        ),
    )
}

/** Classless 3D bevel panel — the foundation of the retro chrome. */
@Composable
fun RetroPanel(
    modifier: Modifier = Modifier,
    raised: Boolean = true,
    borderWidth: Dp = dp(2f),
    fill: Color? = null,
    borderColor: Color? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val p = LocalPalette.current
    val bg = fill ?: p.paper
    val bc = borderColor ?: p.ink
    Box(modifier.background(bg).border(borderWidth, bc).padding(borderWidth), content = content)
}

/**
 * Drop shadow under a panel, menu or window.
 *
 * The e-paper palettes print a **checker dither** (the classic 1-bit look). The native ones drop a
 * real, soft shadow the way their desktop does — Windows 95 its hard grey offset, the modern ones a
 * couple of translucent layers that read as a blur without costing a render effect.
 */
@Composable
fun Modifier.retroShadow(offset: Dp = dp(4f), color: Color? = null): Modifier {
    val p = LocalPalette.current
    val d = offset
    val skin = p.skin
    if (skin == null) {
        val brush = rememberChromeBrush(ChromePattern.CHECKER, color ?: p.ink.copy(alpha = 0.9f), dp(1f))
        return this.drawBehind {
            val o = d.toPx()
            drawRect(brush = brush, topLeft = Offset(o, o), size = Size(size.width, size.height))
        }
    }
    val flat = skin.isFlatTitle && skin.cornerDp == 0f
    val shadow = color ?: Color.Black
    return this.drawBehind {
        val o = d.toPx()
        if (flat) {
            // Windows 95: one solid grey/black offset, no transparency tricks
            drawRect(shadow.copy(alpha = 0.45f), topLeft = Offset(o, o), size = Size(size.width, size.height))
        } else {
            val steps = listOf(
                o * 1.00f to 0.16f,
                o * 0.75f to 0.10f,
                o * 0.50f to 0.07f,
            )
            steps.forEach { (off, alpha) ->
                drawRect(shadow.copy(alpha = alpha), topLeft = Offset(off, off), size = Size(size.width, size.height))
            }
        }
    }
}

@Composable
fun HatchedDivider(modifier: Modifier = Modifier, thickness: Dp = dp(3f)) {
    val p = LocalPalette.current
    // native themes draw a plain rule where the e-paper palettes print a hatch
    val texture = rememberPanelTexture()
    if (texture == null) Box(modifier.fillMaxWidth().height(dp(1f)).background(p.edge.copy(alpha = 0.45f)))
    else Box(modifier.fillMaxWidth().height(thickness).background(texture))
}

/** Solid 1px rule, optionally double (macOS separators). */
@Composable
fun Rule(modifier: Modifier = Modifier, double: Boolean = false, color: Color? = null) {
    val p = LocalPalette.current
    val c = color ?: p.ink.copy(alpha = 0.5f)
    Canvas(modifier.fillMaxWidth().height(if (double) dp(3f) else dp(1f))) {
        val h = this.size.height
        drawRect(c, Offset(0f, 0f), Size(this.size.width, 1f))
        if (double) drawRect(c, Offset(0f, h - 1f), Size(this.size.width, 1f))
    }
}

/**
 * Dithered horizontal ink gradient for title bars. Rendered at low resolution and scaled
 * with FilterQuality.None so the pixels stay crisp.
 */
@Composable
fun DitherTitleBar(modifier: Modifier = Modifier, bmp: ImageBitmap, content: @Composable BoxScope.() -> Unit) {
    Box(modifier) {
        Image(
            bitmap = bmp,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.FillBounds,
            filterQuality = FilterQuality.None,
        )
        Box(Modifier.fillMaxSize(), content = content)
    }
}

@Composable
fun RetroButton(
    label: String = "",
    modifier: Modifier = Modifier,
    glyph: Glyph? = null,
    active: Boolean = false,
    enabled: Boolean = true,
    display: Boolean = false,
    contentColor: Color? = null,
    minWidth: Dp = dp(0f),
    onClick: () -> Unit,
) {
    val p = LocalPalette.current
    // an active key is a "plate": ink in the e-paper palettes, the theme's own selection colour in
    // the native ones, so a pressed button in Windows 95 goes navy and in Ubuntu orange
    val bg = when {
        !enabled -> p.paper
        active -> p.plate
        else -> p.paper
    }
    val fg = when {
        !enabled -> p.mid
        active -> p.plateText
        contentColor != null -> contentColor
        else -> p.ink
    }
    Box(
        modifier = modifier
            .height(dp(34f))
            .then(if (minWidth > 0.dp) Modifier.width(minWidth) else Modifier)
            .fillMaxWidth()
            .background(bg)
            .border(dp(1.6f), if (!enabled) p.mid else if (active) p.plate else p.edge)
            .then(
                if (enabled) Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                ) else Modifier
            )
            .padding(horizontal = dp(10f)),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            if (glyph != null) {
                GlyphIcon(
                    glyph,
                    dp(15f),
                    tint = if (active) p.plateText else contentColor,
                    clear = if (active) p.plate else null,
                )
                Spacer(Modifier.width(dp(6f)))
            }
            if (label.isNotEmpty()) {
                RetroText(
                    text = label,
                    color = fg,
                    size = 12f,
                    display = display,
                    weight = if (active) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.padding(vertical = dp(2f)),
                )
            }
        }
    }
}

/** Compact square icon button (title bar controls, tray buttons). */
@Composable
fun RetroIconButton(
    glyph: Glyph? = null,
    label: String? = null,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    enabled: Boolean = true,
    boxSize: Dp = dp(22f),
    onClick: () -> Unit,
) {
    val p = LocalPalette.current
    Box(
        modifier = modifier
            .size(boxSize)
            .background(if (active) p.plate else p.paper)
            .border(dp(1.4f), if (!enabled) p.mid else if (active) p.plate else p.edge)
            .then(
                if (enabled) Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick,
                ) else Modifier
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (glyph != null) {
            GlyphIcon(
                glyph,
                boxSize * 0.62f,
                tint = if (active) p.plateText else null,
                clear = if (active) p.plate else null,
            )
        } else if (label != null) {
            RetroText(label, color = if (active) p.plateText else if (enabled) p.ink else p.mid, size = 11f, display = true)
        }
    }
}

@Composable
fun RetroTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    enabled: Boolean = true,
    singleLine: Boolean = true,
    onSubmit: (() -> Unit)? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    textSize: Float = 13f,
    fill: Color? = null,
    focusRequester: androidx.compose.ui.focus.FocusRequester? = null,
) {
    val p = LocalPalette.current
    val focus = LocalFocusManager.current
    val keyboard = LocalKeyboardRequester.current
    val pen = dev.retrobit.desktop.ui.input.LocalPointerRouter.current
    val fieldFocus = focusRequester ?: remember { FocusRequester() }
    // one identity per field: the pen goes back to being a pen while any of them has the focus
    val fieldKey = remember { Any() }
    var focused by remember { mutableStateOf(false) }
    DisposableEffect(fieldKey) {
        onDispose { if (focused) pen.textFocusChanged(fieldKey, false) }
    }
    Box(
        modifier
            .background(fill ?: p.paper)
            .border(dp(1.6f), if (enabled) p.edge else p.mid)
            .padding(horizontal = dp(8f), vertical = dp(7f)),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (value.isEmpty() && placeholder.isNotEmpty()) {
            RetroText(placeholder, color = p.mid, size = textSize, maxLines = if (singleLine) 1 else 6)
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = singleLine,
            textStyle = TextStyle(color = p.ink, fontSize = pixelSp(textSize), fontFamily = PixelMono),
            cursorBrush = SolidColor(p.ink),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = if (singleLine) ImeAction.Done else ImeAction.Default),
            keyboardActions = KeyboardActions(onDone = { onSubmit?.invoke(); focus.clearFocus() }),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(fieldFocus)
                // the pen / mouse hovers a text field as an I-beam, not as the e-paper arrow
                .pointerHoverIcon(PointerIcon.Text)
                .onFocusChanged {
                    focused = it.isFocused
                    pen.textFocusChanged(fieldKey, it.isFocused)
                    if (it.isFocused) keyboard.rememberFocus(fieldFocus)
                }
                .onKeyEvent { ev ->
                    // physical keyboards: enter submits single line fields
                    if (singleLine && ev.type == KeyEventType.KeyDown &&
                        (ev.key == Key.Enter || ev.key == Key.NumPadEnter)
                    ) {
                        onSubmit?.invoke()
                        focus.clearFocus()
                        true
                    } else {
                        false
                    }
                },
        )
    }
}

/** Small status/tag badge. */
@Composable
fun Tag(text: String, modifier: Modifier = Modifier, inverted: Boolean = false) {
    val p = LocalPalette.current
    Box(
        modifier
            .background(if (inverted) p.plate else p.paper)
            .border(dp(1.2f), p.edge)
            .padding(horizontal = dp(5f), vertical = dp(1f)),
    ) {
        RetroText(text, color = if (inverted) p.plateText else p.ink, size = 10f)
    }
}

/** Thick ASCII-ish progress bar drawn as pixels. */
@Composable
fun RetroProgress(fraction: Float, modifier: Modifier = Modifier, cells: Int = 20, thickness: Dp = dp(10f)) {
    val p = LocalPalette.current
    if (p.isNative) {
        // a real desktop meter: a rounded track in the theme's soft face, filled with the accent
        Canvas(modifier.fillMaxWidth().height(thickness)) {
            val frac = fraction.coerceIn(0f, 1f)
            val r = androidx.compose.ui.geometry.CornerRadius(size.height / 2f, size.height / 2f)
            drawRoundRect(p.soft, cornerRadius = r)
            if (frac > 0f) {
                drawRoundRect(
                    p.accent,
                    size = Size(size.width * frac, size.height),
                    cornerRadius = r,
                )
            }
        }
        return
    }
    Canvas(modifier.fillMaxWidth().height(thickness)) {
        val cellW = size.width / cells
        val filled = (fraction.coerceIn(0f, 1f) * cells)
        drawRect(p.paper)
        drawRect(p.ink, size = Size(size.width, 1.5f))
        drawRect(p.ink, topLeft = Offset(0f, size.height - 1.5f), size = Size(size.width, 1.5f))
        for (i in 0 until cells) {
            if (i < filled.toInt()) {
                drawRect(p.ink, topLeft = Offset(i * cellW + 1f, 3f), size = Size(cellW - 2f, size.height - 6f))
            } else if (i == filled.toInt() && filled % 1f > 0.4f) {
                drawRect(p.mid, topLeft = Offset(i * cellW + 1f, 3f), size = Size(cellW - 2f, size.height - 6f))
            }
        }
    }
}

/** Infinite horizontal marquee for labels that do not fit. */
@Composable
fun MarqueeText(text: String, modifier: Modifier = Modifier, size: Float = 12f, maxLines: Int = 1) {
    val p = LocalPalette.current
    BasicText(
        text = text,
        maxLines = maxLines,
        modifier = modifier,
        overflow = TextOverflow.Ellipsis,
        style = TextStyle(color = p.ink, fontSize = pixelSp(size), fontFamily = PixelMono),
    )
}

/**
 * Dotted scanline overlay to sell the e-paper panel look. The native themes skip it: a real desktop
 * has no scanlines over it.
 */
@Composable
fun Scanlines(modifier: Modifier = Modifier, alpha: Float = 0.05f) {
    val p = LocalPalette.current
    if (p.isNative) return
    val brush = rememberChromeBrush(ChromePattern.STRIPES_H, p.ink.copy(alpha = alpha), dp(2f))
    Box(modifier.fillMaxSize().background(brush))
}

@Composable
fun GrayImage(image: ImageBitmap, modifier: Modifier = Modifier, tint: Color? = null) {
    Image(
        bitmap = image,
        contentDescription = null,
        modifier = modifier,
        contentScale = ContentScale.Fit,
        filterQuality = FilterQuality.None,
        colorFilter = tint?.let { ColorFilter.tint(it) },
    )
}

/** Rectangular clickable surface with no ripple (e-paper should not animate). */
@Composable
fun FlatClickable(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier.clip(RectangleShape).clickable(
            enabled = enabled,
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick,
        ),
        content = content,
    )
}

fun DrawScope.hatch(brush: Brush, cell: Float = 2f) {
    drawRect(brush)
}

@Composable
fun ProvideUiScale(scale: Float, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalUiScale provides scale, content = content)
}
