#!/usr/bin/env python3
"""Renders docs/cyberdeck-chrome.png: what the cyberdeck frame looks like over a full-screen app.

The numbers are copied from shell/ChromeViews.kt (rail heights, snapped text sizes, key sizes) and
the glyphs are parsed from ui/components/Glyphs.kt, so the picture is a faithful mockup of the
overlay - useful because the overlay itself can only be seen on the tablet.

  panel 1: rails up (title rail + dock), over an app that opened full screen
  panel 2: the same after the rails auto-hide (only the black frame and the two handles)

Run with:  python3 tools/chrome_mockup.py
"""
import os
import re

from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "docs", "cyberdeck-chrome.png")
GLYPHS = os.path.join(ROOT, "app/src/main/java/dev/retrobit/desktop/ui/components/Glyphs.kt")
FONT_DIR = os.path.join(ROOT, "app/src/main/res/font")

# the tablet: 2560x1600 px, density 2.0 (1280x800 dp)
SCREEN_W, SCREEN_H, DENSITY = 2560, 1600, 2.0

PAPER = (232, 229, 218)
INK = (27, 27, 27)
BLACK = (0, 0, 0)
ACCENT = (63, 63, 63)
APP_BG = (247, 246, 243)
APP_LINE = (176, 174, 168)

mono = ImageFont.truetype(os.path.join(FONT_DIR, "pixel_operator_mono.ttf"), 32)
mono_small = ImageFont.truetype(os.path.join(FONT_DIR, "pixel_operator_mono.ttf"), 32)
bold = ImageFont.truetype(os.path.join(FONT_DIR, "pixel_operator_bold.ttf"), 32)


def snap16(px):
    return max(16.0, round(px / 16.0) * 16.0)


def snap8(px):
    return max(8.0, round(px / 8.0) * 8.0)


def glyphs():
    """The 16x16 pixel art of the keys the rails use, straight out of Glyphs.kt."""
    src = open(GLYPHS).read()
    out = {}
    for name in ("HOME", "GRID", "CLOSE"):
        block = re.search(r"\n    %s\(\n((?:        \"[^\"]*\",\n)+)    \)," % name, src)
        out[name] = re.findall(r'"([^"]*)"', block.group(1))
    return out


def draw_glyph(d, art, x, y, size, invert=False):
    cell = size / 16.0
    for r, row in enumerate(art):
        for c, ch in enumerate(row):
            color = {"#": PAPER if invert else INK, "o": INK if invert else PAPER, "+": ACCENT}.get(ch)
            if color:
                d.rectangle([x + c * cell, y + r * cell, x + c * cell + cell, y + r * cell + cell],
                            fill=color)


def app_background(d):
    """A stand-in for an app that the system opened full screen."""
    d.rectangle([0, 0, SCREEN_W, SCREEN_H], fill=APP_BG)
    d.rectangle([0, 0, SCREEN_W, 64], fill=(238, 237, 233))
    d.text((96, 16), "9:41", font=mono, fill=(90, 90, 88))
    d.text((SCREEN_W - 360, 16), "wifi  84%", font=mono, fill=(90, 90, 88))
    d.rectangle([64, 130, SCREEN_W - 64, 250], outline=APP_LINE, width=3)
    d.text((100, 165), "an app that opened full screen", font=bold, fill=(70, 70, 68))
    y = 330
    for width in (1500, 2100, 1800, 1200, 2000, 900, 1700):
        d.rectangle([100, y, 100 + width, y + 34], fill=APP_LINE)
        y += 74
    d.rectangle([100, y + 60, 700, y + 200], fill=(232, 231, 227))


def title_rail(d, art, title, status, in_frame=True, can_refit=True):
    """Mirrors RailView.drawTop()."""
    inset = 64                                    # status bar band we cover
    h = 40 * DENSITY
    d.rectangle([0, 0, SCREEN_W, inset], fill=PAPER)
    d.rectangle([0, 0, SCREEN_W, inset], outline=None)
    x = 0
    while x < SCREEN_W:                           # the hatched band over the app's status bar
        d.rectangle([x, 0, x + 1.4 * DENSITY, inset], fill=(150, 148, 142))
        x += 7 * DENSITY
    d.rectangle([0, inset, SCREEN_W, inset + h], fill=PAPER)
    d.rectangle([0, inset + h - 2 * DENSITY, SCREEN_W, inset + h], fill=BLACK)

    h_pad = 8 * DENSITY
    key = snap8(h * 0.76)
    top = inset + (h - key) / 2.0

    right = SCREEN_W - h_pad
    for name, width in (("CLOSE", key), ("HOME", key), ("GRID", key if can_refit else 0)):
        if width == 0:
            continue
        right -= width
        d.rectangle([right, top, right + width, top + key], fill=PAPER, outline=BLACK, width=2)
        g = snap8(key * 0.74)
        draw_glyph(d, art[name], right + (width - g) / 2.0, top + (key - g) / 2.0, g)
        right -= 5 * DENSITY

    tw = d.textlength(status, font=mono_small)
    d.text((right - 4 * DENSITY - tw, inset + h / 2.0 - 20), status, font=mono_small, fill=INK)
    right -= tw + 10 * DENSITY

    x = 92 + h_pad                              # notchLeft + padding: clear of the camera hole
    if in_frame:
        m = snap8(h * 0.20)
        d.rectangle([x, inset + h / 2 - m / 2, x + m, inset + h / 2 + m / 2], fill=ACCENT)
        x += m + 6 * DENSITY
    d.text((x + 2, inset + h / 2.0 - 20), title, font=bold, fill=INK)


def dock(d, art, items, active_index=None):
    """Mirrors RailView.drawDock(): icon over name, real app icons printed in grayscale."""
    inset = 48                                   # navigation gesture band we stay clear of
    h = 54 * DENSITY
    top = SCREEN_H - inset - h
    d.rectangle([0, top, SCREEN_W, top + h], fill=PAPER)
    d.rectangle([0, top, SCREEN_W, top + 2 * DENSITY], fill=BLACK)

    h_pad = 7 * DENSITY
    item_top = top + 5 * DENSITY
    item_h = h - 10 * DENSITY
    label_size = snap16(item_h * 0.30)
    icon_size = snap8(item_h - label_size - 5 * DENSITY)
    label_font = ImageFont.truetype(os.path.join(FONT_DIR, "pixel_operator_mono.ttf"), int(label_size))

    x = h_pad

    def tile(left, label, is_active, icon_kind, width=None):
        text_w = d.textlength(label, font=label_font)
        w = width or min(max(text_w, icon_size) + 12 * DENSITY, 150 * DENSITY)
        bg = INK if is_active else PAPER
        fg = PAPER if is_active else INK
        d.rectangle([left, item_top, left + w, item_top + item_h], fill=bg, outline=BLACK, width=2)
        cx = left + w / 2.0
        icon_y = item_top + 3 * DENSITY
        if icon_kind == "glyph":
            g = snap8(icon_size * 0.72)
            draw_glyph(d, art["HOME"], cx - g / 2, icon_y + (icon_size - g) / 2, g, invert=is_active)
        else:
            d.rectangle([cx - icon_size / 2, icon_y, cx + icon_size / 2, icon_y + icon_size],
                        fill=(150, 150, 148) if not is_active else (210, 208, 202),
                        outline=BLACK if not is_active else PAPER, width=2)
            d.rectangle([cx - icon_size / 4, icon_y + icon_size / 4,
                         cx + icon_size / 4, icon_y + icon_size * 0.72],
                        fill=(96, 96, 94) if not is_active else (150, 148, 142))
        d.text((cx - text_w / 2, item_top + item_h - 4 * DENSITY - label_size), label,
               font=label_font, fill=fg)
        return left + w

    x = tile(x, "desktop", False, "glyph") + 8 * DENSITY
    d.rectangle([x, item_top + 6 * DENSITY, x + 1.6 * DENSITY, item_top + item_h - 6 * DENSITY], fill=(120, 118, 112))
    x += 10 * DENSITY
    for i, label in enumerate(items):
        x = tile(x, label, active_index == i, "icon") + 6 * DENSITY
    right = SCREEN_W - h_pad - snap8(item_h * 0.76)
    d.rectangle([right, item_top + (item_h - snap8(item_h * 0.76)) / 2,
                 right + snap8(item_h * 0.76), item_top + (item_h + snap8(item_h * 0.76)) / 2],
                fill=PAPER, outline=BLACK, width=2)
    g = snap8(snap8(item_h * 0.76) * 0.74)
    draw_glyph(d, art["CLOSE"], right + (snap8(item_h * 0.76) - g) / 2,
               item_top + (item_h - g) / 2, g)


def handles(d):
    """Auto-hidden: only the black frame and a thin, touchable handle at each edge."""
    d.rectangle([0, 0, SCREEN_W, 8 * DENSITY], fill=PAPER)
    d.rectangle([0, 8 * DENSITY - 2 * DENSITY, SCREEN_W, 8 * DENSITY], fill=BLACK)
    cx = SCREEN_W / 2.0
    d.rectangle([cx - 22 * DENSITY, 8 * DENSITY - 3.2 * DENSITY, cx + 22 * DENSITY, 8 * DENSITY - 1.6 * DENSITY],
                fill=INK)
    d.rectangle([0, SCREEN_H - 8 * DENSITY, SCREEN_W, SCREEN_H], fill=PAPER)
    d.rectangle([0, SCREEN_H - 8 * DENSITY, SCREEN_W, SCREEN_H - 8 * DENSITY + 2 * DENSITY], fill=BLACK)
    d.rectangle([cx - 22 * DENSITY, SCREEN_H - 8 * DENSITY + 1.6 * DENSITY,
                 cx + 22 * DENSITY, SCREEN_H - 8 * DENSITY + 3.2 * DENSITY], fill=INK)


def frame(d):
    edge = 3.5 * DENSITY
    d.rectangle([0, 0, SCREEN_W, edge], fill=BLACK)
    d.rectangle([0, SCREEN_H - edge, SCREEN_W, SCREEN_H], fill=BLACK)
    d.rectangle([0, 0, edge, SCREEN_H], fill=BLACK)
    d.rectangle([SCREEN_W - edge, 0, SCREEN_W, SCREEN_H], fill=BLACK)


def panel(rails_up):
    art = glyphs()
    img = Image.new("RGB", (SCREEN_W, SCREEN_H))
    d = ImageDraw.Draw(img)
    app_background(d)
    if rails_up:
        title_rail(d, art, "Files", "13:37  bat 84%+  WIFI")
        dock(d, art, ["Browser", "Camera", "Notes", "Gallery"], active_index=0)
    else:
        handles(d)
    frame(d)
    return img


def main():
    panels = [panel(True), panel(False)]
    out = Image.new("RGB", (1280, 800 * 2 + 8), (255, 255, 255))
    for i, p in enumerate(panels):
        out.paste(p.resize((1280, 800), Image.NEAREST), (0, i * (800 + 8)))
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    out.save(OUT)
    print("wrote", OUT, out.size)


if __name__ == "__main__":
    main()
