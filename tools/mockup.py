#!/usr/bin/env python3
"""
Generates docs/ui-preview.png — a design mockup of the three desktop shells using the exact
palette values, fonts (VT323 / IBM Plex Mono), glyphs and dither patterns that the Kotlin code uses.

It is a mockup, not a screenshot: it documents the layout intent of
ui/desktop/Shell{Linux,Macos,Windows}.kt. Run with: python3 tools/mockup.py
"""
from PIL import Image, ImageDraw, ImageFont
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
FONT_DIR = os.path.join(ROOT, "app/src/main/res/font")
OUT = os.path.join(ROOT, "docs/ui-preview.png")

PAPER = (0xE8, 0xE5, 0xDA)
INK = (0x1B, 0x1B, 0x1B)
PATTERN = tuple(int(PAPER[i] * 0.7 + INK[i] * 0.3) for i in range(3))

# Pixel Operator (CC0 1.0) — the same files the app bundles.
# Pixel fonts are designed on an 8/16 px grid: sizes that are multiples of 8 stay crisp.
px_font = ImageFont.truetype(os.path.join(FONT_DIR, "pixel_operator_bold.ttf"), 48)
px_small = ImageFont.truetype(os.path.join(FONT_DIR, "pixel_operator_bold.ttf"), 32)
mono = ImageFont.truetype(os.path.join(FONT_DIR, "pixel_operator_mono.ttf"), 16)
mono_med = ImageFont.truetype(os.path.join(FONT_DIR, "pixel_operator_mono_hb.ttf"), 16)
mono_bold = ImageFont.truetype(os.path.join(FONT_DIR, "pixel_operator_mono_bold.ttf"), 16)
mono_tiny = ImageFont.truetype(os.path.join(FONT_DIR, "pixel_operator_mono.ttf"), 14)

BAYER = [
    0, 8, 2, 10,
    12, 4, 14, 6,
    3, 11, 1, 9,
    15, 7, 13, 5,
]


def bayer_level(x, y):
    return BAYER[(y & 3) * 4 + (x & 3)]


def fill_dither(draw, box, color, cell=3, level_max=6):
    x0, y0, x1, y1 = box
    for y in range(y0, y1, cell):
        for x in range(x0, x1, cell):
            if bayer_level(x // cell, y // cell) < level_max:
                draw.rectangle([x, y, x + cell - 1, y + cell - 1], fill=color)


def halftone(img, color=PATTERN, cell=7, radius=2):
    d = ImageDraw.Draw(img)
    w, h = img.size
    for cy in range(cell, h, cell):
        for cx in range(cell, w, cell):
            d.ellipse([cx - radius, cy - radius, cx + radius, cy + radius], fill=color)


def grid(img, color=PATTERN, step=42):
    d = ImageDraw.Draw(img)
    w, h = img.size
    for x in range(0, w, step):
        d.line([x, 0, x, h], fill=color)
    for y in range(0, h, step):
        d.line([0, y, w, y], fill=color)


def text_run(d, x, y, items, font, gap=16, fill=INK):
    """Draws a row of labels spaced by the widest metric of the actual font."""
    for item in items:
        d.text((x, y), item, font=font, fill=fill)
        x += d.textlength(item, font=font) + gap
    return x


def lab(d, x, y, text, font, fill=INK):
    d.text((x, y), text, font=font, fill=fill)
    return d.textlength(text, font=font)


def bevel(d, box, width=2, color=INK):
    d.rectangle(box, outline=color, width=width)


def hard_shadow(d, box, offset=5, cell=2):
    x0, y0, x1, y1 = box
    for y in range(y0 + offset, y1 + offset, cell):
        for x in range(x0 + offset, x1 + offset, cell):
            if bayer_level(x // cell, y // cell) < 9:
                d.rectangle([x, y, x + cell - 1, y + cell - 1], fill=INK)


def dither_title_bar(d, box, cells=14, height_cells=3):
    """Win95-style ink gradient rendered as an ordered dither."""
    x0, y0, x1, y1 = box
    cw = (x1 - x0) / cells
    ch = (y1 - y0) / height_cells
    for cy in range(height_cells):
        for cx in range(cells):
            t = cx / cells
            cover = 1.0 if t < 0.35 else max(0.0, 1.0 - (t - 0.35) / 0.65)
            if cover > (bayer_level(cx, cy) + 0.5) / 16.0:
                d.rectangle(
                    [x0 + cx * cw, y0 + cy * ch, x0 + (cx + 1) * cw - 1, y0 + (cy + 1) * ch - 1],
                    fill=INK,
                )


def glyph_tile(d, x, y, size, kind="folder", label="", paper=PAPER, ink=INK, shape="flat"):
    """16x16-ish pixel-art app icon placeholder + label, mimicking IconMode.MONO output.

    `shape` documents IconTheme.AUTO: flat (Linux square), rounded (macOS), classic (Windows bevel).
    """
    inner = size - 10
    if shape == "rounded":
        d.rounded_rectangle([x, y, x + inner, y + inner], radius=12, fill=paper, outline=ink, width=2)
    elif shape == "classic":
        d.rectangle([x, y, x + inner, y + inner], fill=paper)
        bevel(d, [x, y, x + inner, y + inner], width=2)
        d.rectangle([x + 3, y + 3, x + inner - 3, y + inner - 3], outline=ink, width=1)
    else:
        d.rectangle([x, y, x + inner, y + inner], fill=paper, outline=ink, width=2)
    if kind == "folder":
        d.rectangle([x + 5, y + 5, x + inner - 5, y + inner - 10], fill=ink)
        d.rectangle([x + 8, y + 3, x + inner - 12, y + 6], fill=ink)
    elif kind == "term":
        for i in range(4):
            d.rectangle([x + 6 + i * 4, y + 8 + i * 3, x + 8 + i * 4, y + 10 + i * 3], fill=ink)
        d.rectangle([x + 22, y + 17, x + inner - 5, y + 20], fill=ink)
    elif kind == "monitor":
        d.rectangle([x + 4, y + 6, x + inner - 4, y + inner - 12], fill=ink)
        d.rectangle([x + 8, y + 10, x + inner - 8, y + inner - 16], fill=paper)
        d.rectangle([x + inner // 2 - 5, y + inner - 12, x + inner // 2 + 5, y + inner - 8], fill=ink)
        d.rectangle([x + 6, y + inner - 8, x + inner - 6, y + inner - 5], fill=ink)
    elif kind == "grid":
        for gx in range(2):
            for gy in range(3):
                d.rectangle([x + 4 + gx * 12, y + 4 + gy * 12, x + 12 + gx * 12, y + 12 + gy * 12], fill=ink)
    if label:
        tw = d.textlength(label, font=mono_tiny)
        d.text((x + inner / 2 - tw / 2, y + inner + 4), label, font=mono_tiny, fill=ink)


CURSOR_ROWS = [
    "##          ",
    "###         ",
    "####        ",
    "#o##        ",
    "#oo##       ",
    "#ooo##      ",
    "#oooo##     ",
    "#ooooo##    ",
    "#oooooo##   ",
    "#ooooooo##  ",
    "#####oooo#  ",
    "   #ooooo#  ",
    "   #ooooo#  ",
    "    #ooo#   ",
    "    #ooo#   ",
    "     ##     ",
]


DIAMOND = [
    "  ##  ",
    " #### ",
    "######",
    " #### ",
    "  ##  ",
]


def pix_diamond(d, x, y, cell=4, fill=INK):
    """The launcher's pixel-art diamond mark (the bundled font has no block glyphs)."""
    for ry, row in enumerate(DIAMOND):
        for rx, ch in enumerate(row):
            if ch == "#":
                d.rectangle([x + rx * cell, y + ry * cell, x + (rx + 1) * cell - 1, y + (ry + 1) * cell - 1], fill=fill)


def pix_app_dot(d, x, y, size=12, fill=INK):
    """Small pixel-art app square used inside list rows (stands in for an app icon glyph)."""
    d.rectangle([x, y, x + size - 1, y + size - 1], outline=fill, width=2)
    d.rectangle([x + 4, y + 4, x + size - 4, y + size - 4], fill=fill)


def cursor(d, x, y, cell=3, pressed=False):
    fg = PAPER if pressed else INK
    bg = INK if pressed else PAPER
    for py, row in enumerate(CURSOR_ROWS):
        for px, ch in enumerate(row):
            if ch != " ":
                d.rectangle([x + px * cell - 1, y + py * cell - 1,
                             x + (px + 1) * cell, y + (py + 1) * cell], fill=bg)
    for py, row in enumerate(CURSOR_ROWS):
        for px, ch in enumerate(row):
            if ch != " ":
                d.rectangle([x + px * cell, y + py * cell,
                             x + (px + 1) * cell - 1, y + (py + 1) * cell - 1], fill=fg)


def window(d, box, title, glyph_label=None, win95=False, mac=False, body_lines=()):
    x0, y0, x1, y1 = box
    hard_shadow(d, box, offset=5)
    d.rectangle([x0, y0, x1, y1], fill=PAPER, outline=INK, width=2)
    bar_h = 34
    if win95:
        d.rectangle([x0 + 2, y0 + 2, x1 - 2, y0 + bar_h], fill=PAPER)
        dither_title_bar(d, (x0 + 2, y0 + 2, x1 - 2, y0 + bar_h))
        tx, ty = x0 + 10, y0 + 8
        d.text((tx, ty), title, font=mono_bold, fill=PAPER)
        for i, label in enumerate(["_", "□", "x"]):
            bx = x1 - 30 - i * 26
            d.rectangle([bx, y0 + 7, bx + 20, y0 + 27], fill=PAPER, outline=INK, width=2)
            d.text((bx + 6, y0 + 4), label, font=mono, fill=INK)
    elif mac:
        d.rectangle([x0 + 2, y0 + 2, x1 - 2, y0 + bar_h], fill=PAPER)
        tw = d.textlength(title, font=mono_bold)
        d.text(((x0 + x1) / 2 - tw / 2, y0 + 8), title, font=mono_bold, fill=INK)
        for i, label in enumerate(["x", "-", "+"]):
            cx = x0 + 16 + i * 24
            d.ellipse([cx - 8, y0 + 10, cx + 8, y0 + 26], fill=PAPER, outline=INK, width=2)
            d.text((cx - 4, y0 + 6), label, font=mono_tiny, fill=INK)
        d.line([x0 + 2, y0 + bar_h + 2, x1 - 2, y0 + bar_h + 2], fill=INK, width=1)
    else:
        d.rectangle([x0 + 2, y0 + 2, x1 - 2, y0 + bar_h], fill=INK)
        d.text((x0 + 10, y0 + 8), title, font=mono_bold, fill=PAPER)
        for i, label in enumerate(["x", "+", "-"]):
            bx = x1 - 30 - i * 26
            d.rectangle([bx, y0 + 7, bx + 20, y0 + 27], fill=PAPER, outline=INK, width=2)
            d.text((bx + 6, y0 + 4), label, font=mono, fill=INK)
    y = y0 + bar_h + 14
    for line in body_lines:
        d.text((x0 + 14, y), line, font=mono, fill=INK)
        y += 22
    return y


def pager(d, x, y, size=26, cells=3, active=0, windows=(1, 0, 1)):
    """Virtual desktop switcher: numbered squares with window-count dots."""
    p = INK
    for i in range(cells):
        bx = x + i * (size + 5)
        filled = i == active
        d.rectangle([bx, y, bx + size, y + size], fill=p if filled else PAPER, outline=p, width=2)
        num = str(i + 1)
        nw = d.textlength(num, font=mono_tiny)
        d.text((bx + size / 2 - nw / 2, y + 2), num, font=mono_tiny, fill=PAPER if filled else INK)
        for k in range(windows[i] if i < len(windows) else 0):
            dx = bx + size / 2 - 4 + k * 4
            d.rectangle([dx, y + size - 6, dx + 2, y + size - 4], fill=PAPER if filled else INK)
    return x


def calendar_widget(d, box, mirror=False):
    """Compact calendar: month grid, dots on days with entries, agenda for the selected day.

    Mirrors the layout of ui/desktop/CalendarWidget.kt (title bar, weekday row, 6x7 grid, agenda).
    """
    x0, y0, x1, y1 = box
    d.rectangle([x0, y0, x1, y1], fill=PAPER, outline=INK, width=2)
    d.rectangle([x0 + 1, y0 + 1, x1 - 1, y0 + 26], fill=PATTERN)
    d.line([x0, y0 + 27, x1, y0 + 27], fill=INK, width=2)
    # tiny pixel-art calendar glyph, then the month name (ASCII only: the bundled font has no blocks)
    d.rectangle([x0 + 8, y0 + 5, x0 + 22, y0 + 21], fill=PAPER, outline=INK, width=1)
    d.rectangle([x0 + 8, y0 + 5, x0 + 22, y0 + 10], fill=INK)
    for gx in range(2):
        for gy in range(2):
            d.rectangle([x0 + 12 + gx * 5, y0 + 12 + gy * 5, x0 + 14 + gx * 5, y0 + 14 + gy * 5], fill=INK)
    d.text((x0 + 28, y0 + 5), "october 2026", font=mono_tiny, fill=INK)
    for i, ch in enumerate("< >"):
        d.rectangle([x1 - 44 + i * 20, y0 + 4, x1 - 28 + i * 20, y0 + 22], fill=PAPER, outline=INK, width=1)
        d.text((x1 - 40 + i * 20, y0 + 5), ch, font=mono_tiny, fill=INK)

    grid_top = y0 + 34
    cw = (x1 - x0 - 16) / 7
    for i, head in enumerate(("mo", "tu", "we", "th", "fr", "sa", "su")):
        d.text((x0 + 8 + i * cw, grid_top), head, font=mono_tiny, fill=INK)
    # October 2026 starts on a Thursday; show 12 Oct .. 1 Nov plus the selected day
    days = [None] * 3 + [d for d in range(1, 32)] + [None]
    highlighted = {0: {3: "evt", 6: "task"}, 1: {0: "birthday", 4: "evt"}}
    for i, day in enumerate(days[:42]):
        col, row = i % 7, i // 7
        cx = x0 + 8 + col * cw + cw / 2
        cy = grid_top + 16 + row * 19
        if day is None:
            continue
        selected = day == 14
        if selected:
            d.rectangle([cx - cw / 2 + 1, cy - 8, cx + cw / 2 - 1, cy + 12], fill=INK)
        elif day == 13:
            d.rectangle([cx - cw / 2 + 1, cy - 8, cx + cw / 2 - 1, cy + 12], fill=PATTERN)
        label = str(day)
        tw = d.textlength(label, font=mono_tiny)
        d.text((cx - tw / 2, cy - 6), label, font=mono_tiny, fill=PAPER if selected else INK)
        mark = highlighted.get(row, {}).get(col)
        if mark:
            d.rectangle([cx - 2, cy + 9, cx + 2, cy + 12], fill=PAPER if selected else INK)

    agenda_top = y0 + 34 + 16 + 6 * 19 + 6
    d.line([x0 + 1, agenda_top - 6, x1 - 1, agenda_top - 6], fill=INK, width=2)
    d.text((x0 + 8, agenda_top - 4), "wed, 14 oct 2026", font=mono_tiny, fill=INK)
    d.rectangle([x1 - 26, agenda_top - 6, x1 - 8, agenda_top + 12], fill=INK)
    d.text((x1 - 21, agenda_top - 4), "+", font=mono_tiny, fill=PAPER)
    rows = [("evt", "edge snapping demo"), ("tsk", "flash v1.4.0 build"), ("bir", "retro desk day")]
    for i, (tag, title) in enumerate(rows):
        ty = agenda_top + 18 + i * 20
        if ty + 16 > y1 - 22:
            break
        d.rectangle([x0 + 8, ty, x0 + 8 + 24, ty + 16], fill=INK)
        d.text((x0 + 11, ty + 1), tag, font=mono_tiny, fill=PAPER)
        d.text((x0 + 38, ty + 1), title, font=mono_tiny, fill=INK)
        d.text((x1 - 40, ty + 1), "del", font=mono_tiny, fill=INK)
    d.line([x0 + 1, y1 - 20, x1 - 1, y1 - 20], fill=INK, width=2)
    d.text((x0 + 8, y1 - 17), "6 entries \u00b7 2 open tasks", font=mono_tiny, fill=INK)


def panel(caption, style):
    w, h = 780, 1060
    img = Image.new("RGB", (w, h), PAPER)
    d = ImageDraw.Draw(img)

    # wallpaper
    if style == "linux":
        grid(img)
    elif style == "macos":
        halftone(img)
    else:
        fill_dither(d, (0, 0, w, h), PATTERN, cell=4, level_max=4)

    # registration marks (drawn by Wallpaper.kt)
    for cx, cy, sx, sy in [(14, 14, 1, 1), (w - 14, 14, -1, 1), (14, h - 14, 1, -1), (w - 14, h - 14, -1, -1)]:
        d.line([cx, cy, cx + sx * 26, cy], fill=INK, width=2)
        d.line([cx, cy, cx, cy + sy * 26], fill=INK, width=2)

    if style == "linux":
        # top panel
        d.rectangle([0, 0, w, 44], fill=PAPER)
        bevel(d, [0, 0, w - 1, 44])
        # right-hand cluster is laid out first so the window buttons know where to stop
        clock_w = d.textlength("13:37", font=px_small) + d.textlength(":09", font=mono_med)
        sec_w = d.textlength(":09", font=mono_med)
        d.text((w - 14 - d.textlength("13:37", font=px_small) - sec_w - 2, 6), "13:37", font=px_small, fill=INK)
        d.text((w - 16 - sec_w, 20), ":09", font=mono_med, fill=INK)
        rx = w - 24 - clock_w
        for label in ("WIFI 92%", "KB"):
            tw = d.textlength(label, font=mono_tiny)
            if label == "KB":
                d.rectangle([rx - tw - 18, 10, rx - 6, 32], fill=PAPER, outline=INK, width=2)
                d.text((rx - tw - 12, 12), label, font=mono_tiny, fill=INK)
                rx -= tw + 30
            else:
                d.text((rx - tw - 10, 12), label, font=mono_tiny, fill=INK)
                rx -= tw + 20
        rx = pager(d, rx - (3 * 27 + 12), 9, size=22, cells=3, active=0, windows=(2, 1, 0))
        chip = "[LINUX]"
        cw = d.textlength(chip, font=mono_tiny)
        d.rectangle([rx - cw - 12, 9, rx - 4, 35], fill=PAPER, outline=INK, width=2)
        d.text((rx - cw - 8, 12), chip, font=mono_tiny, fill=INK)
        limit = rx - cw - 24

        pix_diamond(d, 14, 10, cell=5)
        x = text_run(d, 48, 13, ["retrOS", "system", "apps", "term"], mono, gap=14)
        x += 8
        for label, active in [("terminal", True), ("notes.txt", False)]:
            tw = d.textlength(label, font=mono)
            if x + tw + 26 > limit:
                break
            if active:
                d.rectangle([x, 8, x + tw + 20, 36], fill=INK)
                d.text((x + 10, 12), label, font=mono, fill=PAPER)
            else:
                d.rectangle([x, 8, x + tw + 20, 36], fill=PAPER, outline=INK, width=2)
                d.text((x + 10, 12), label, font=mono, fill=INK)
            x += tw + 28
        # left dock
        d.rectangle([0, 44, 78, h], fill=PAPER)
        bevel(d, [0, 44, 78, h])
        for i, (kind, label) in enumerate([("grid", "apps"), ("search", "find"), ("term", "shell")]):
            glyph_tile(d, 12, 60 + i * 84, 52, kind="grid" if kind != "search" else "monitor", label="")
            d.text((16, 116 + i * 84), label, font=mono_tiny, fill=INK)
        d.line([10, 320, 68, 320], fill=INK, width=2)
        for i in range(4):
            d.rectangle([16, 340 + i * 60, 62, 388 + i * 60], fill=PAPER, outline=INK, width=2)
            d.rectangle([24, 348 + i * 60, 54, 372 + i * 60], fill=INK)
        d.text((16, h - 150), "mon", font=mono_tiny, fill=INK)
        d.text((16, h - 96), "note", font=mono_tiny, fill=INK)
        d.text((16, h - 42), "setup", font=mono_tiny, fill=INK)
        # desktop icons, freely placed on the 10x5 grid (flat square frames on Linux)
        for i, (kind, label) in enumerate([("folder", "Files"), ("monitor", "Browser"), ("term", "Terminal"), ("grid", "Gallery")]):
            glyph_tile(d, 100 + (i % 4) * 96, 70, 62, kind=kind, label=label, shape="flat")
        for i, (kind, label) in enumerate([("monitor", "Clock"), ("grid", "Notes")]):
            glyph_tile(d, 100 + i * 96, 190, 62, kind=kind, label=label, shape="flat")
        # widgets
        d.rectangle([w - 250, 60, w - 20, 210], fill=PAPER, outline=INK, width=2)
        d.rectangle([w - 250, 60, w - 20, 86], fill=PATTERN)
        d.text((w - 240, 64), "chrono", font=mono_tiny, fill=INK)
        d.text((w - 240, 96), "13:37", font=px_font, fill=INK)
        d.text((w - 240, 150), "Mon, 13 Oct 2026", font=mono, fill=INK)
        d.text((w - 240, 176), "uptime 2d 4h 12m", font=mono_tiny, fill=INK)
        calendar_widget(d, (500, 300, w - 20, 690))
        window(d, (110, 300, 470, 700), "terminal", body_lines=[
            "operator@retrobit:~$ neofetch",
            "   .-----------.   OS: retrOS GNU/Linux",
            "   | retR0BIT  |   WM: retrobit-wm",
            "   '-----------'   Theme: Paper/halftone",
            "     '-------'     Memory: 3.1G / 7.8G",
        ])
        cursor(d, 292, 700, cell=3)
        d.text((115, 720), "operator@retrobit:~$ tiling on", font=mono, fill=INK)
        d.rectangle([110, 760, w - 40, 830], fill=PAPER, outline=INK, width=2)
        d.text((122, 775), "± tiling master–stack aktif", font=mono, fill=INK)
    elif style == "macos":
        d.rectangle([0, 0, w, 40], fill=PAPER)
        d.line([0, 40, w, 40], fill=INK, width=2)
        pix_diamond(d, 14, 8, cell=5)
        text_run(d, 48, 11, ["File", "View", "Window", "Help"], mono_med, gap=22)
        clock_w = d.textlength("13:37", font=px_small) + d.textlength(":09", font=mono_med)
        sec_w = d.textlength(":09", font=mono_med)
        d.text((w - 14 - d.textlength("13:37", font=px_small) - sec_w - 2, 4), "13:37", font=px_small, fill=INK)
        d.text((w - 16 - sec_w, 16), ":09", font=mono_med, fill=INK)
        rx = w - 24 - clock_w
        rx = pager(d, rx - (3 * 25 + 12), 8, size=20, cells=3, active=0, windows=(1, 0, 1))
        for label, kind in [("WIFI 92%", "text"), ("KB", "button"), ("[MACOS]", "button")]:
            tw = d.textlength(label, font=mono_tiny)
            if kind == "button":
                d.rectangle([rx - tw - 16, 10, rx - 4, 32], fill=PAPER, outline=INK, width=2)
                d.text((rx - tw - 10, 11), label, font=mono_tiny, fill=INK)
                rx -= tw + 28
            else:
                d.text((rx - tw - 8, 11), label, font=mono_tiny, fill=INK)
                rx -= tw + 18
        # right-aligned desktop icons
        for (kind, label), (ix, iy) in zip(
            [("folder", "Files"), ("monitor", "Safari"), ("grid", "Photos"), ("term", "Terminal")],
            [(w - 120, 58), (w - 120, 158), (w - 216, 58), (w - 216, 158)],
        ):
            glyph_tile(d, ix, iy, 62, kind=kind, label=label, shape="rounded")
        # left widgets
        d.rectangle([30, 70, 260, 220], fill=PAPER, outline=INK, width=2)
        d.rectangle([30, 70, 260, 96], fill=PATTERN)
        d.text((40, 74), "chrono", font=mono_tiny, fill=INK)
        d.text((40, 106), "13:37", font=px_font, fill=INK)
        d.text((40, 160), "Mon, 13 Oct 2026", font=mono, fill=INK)
        calendar_widget(d, (28, 240, 244, 640))
        window(d, (262, 260, w - 60, 640), "notes.txt", mac=True, body_lines=[
            "RETROBIT NOTES",
            "==============",
            "",
            "- tap & hold any icon for options",
            "- type `help` in the terminal",
        ])
        # dock
        dock_y = h - 130
        d.rectangle([60, dock_y, 60 + 5 * 84 + 160, dock_y + 100], fill=PAPER, outline=INK, width=3)
        d.text((92, dock_y + 12), "▣", font=px_small, fill=INK)
        d.line([140, dock_y + 14, 140, dock_y + 86], fill=INK, width=2)
        for i in range(5):
            d.rectangle([150 + i * 84, dock_y + 16, 216 + i * 84, dock_y + 82], fill=PAPER, outline=INK, width=2)
            d.rectangle([168 + i * 84, dock_y + 30, 198 + i * 84, dock_y + 66], fill=INK)
        d.line([150 + 5 * 84, dock_y + 14, 150 + 5 * 84, dock_y + 86], fill=INK, width=2)
        d.text((160 + 5 * 84, dock_y + 34), "term", font=mono_tiny, fill=INK)
        ox = 250
        for t in ["terminal", "notes.txt"]:
            tw = d.textlength(t, font=mono_tiny)
            d.rectangle([ox, dock_y - 30, ox + tw + 16, dock_y - 6], fill=INK)
            d.text((ox + 8, dock_y - 27), t, font=mono_tiny, fill=PAPER)
            ox += tw + 26
    else:
        fill_dither(d, (0, 0, w, h), PATTERN, cell=4, level_max=4)
        for i, (kind, label) in enumerate([("monitor", "My Computer"), ("folder", "Documents"), ("grid", "Network")]):
            glyph_tile(d, 24, 28 + i * 132, 64, kind=kind, label=label, shape="classic")
        d.rectangle([w - 250, 60, w - 20, 250], fill=PAPER, outline=INK, width=2)
        d.rectangle([w - 250, 60, w - 20, 86], fill=PATTERN)
        d.text((w - 240, 64), "sysinfo", font=mono_tiny, fill=INK)
        d.text((w - 240, 96), "BAT 92%", font=mono_med, fill=INK)
        d.rectangle([w - 240, 120, w - 30, 134], fill=PAPER, outline=INK, width=2)
        d.rectangle([w - 238, 122, w - 60, 132], fill=INK)
        d.text((w - 240, 146), "RAM 3.1G / 7.8G", font=mono_tiny, fill=INK)
        d.rectangle([w - 240, 166, w - 30, 180], fill=PAPER, outline=INK, width=2)
        d.rectangle([w - 238, 168, w - 100, 178], fill=INK)
        calendar_widget(d, (w - 250, 290, w - 20, 680))
        window(d, (230, 250, w - 258, 660), "Applications", win95=True, body_lines=[
            "search applications…        [ 24/96 ]",
        ])
        for i, row in enumerate([["Files", "Camera", "Clock"], ["Notes", "Browser", "Gallery"], ["Recorder", "Mail", "Music"]]):
            for j, name in enumerate(row):
                bx = 240 + j * 92
                by = 250 + 34 + 44 + i * 42
                pix_app_dot(d, bx, by, 14)
                d.text((bx + 22, by), name, font=mono_tiny, fill=INK)
        d.text((244, 250 + 34 + 44 + 3 * 42 + 14), "96 apps - long-press for options", font=mono_tiny, fill=INK)
        # taskbar (kept clear of the caption band at the bottom)
        tb = h - 46 - 62
        d.rectangle([0, tb, w, h], fill=PAPER)
        bevel(d, [0, tb, w - 1, h - 1])
        d.rectangle([8, tb + 8, 130, tb + 52], fill=PAPER, outline=INK, width=2)
        pix_diamond(d, 16, tb + 20, cell=4)
        d.text((48, tb + 20), "Start", font=mono_bold, fill=INK)
        d.rectangle([138, tb + 12, 300, tb + 48], fill=INK)
        d.text((148, tb + 18), "Applications", font=mono, fill=PAPER)
        px_ = pager(d, 310, tb + 7, size=30, cells=3, active=0, windows=(1, 1, 0))
        chip_x = px_ + 3 * 34 + 12
        d.rectangle([chip_x, tb + 11, chip_x + 84, tb + 47], fill=PAPER, outline=INK, width=2)
        d.text((chip_x + 8, tb + 18), "[WIN]", font=mono_tiny, fill=INK)
        tray = "WIFI 92%  13:37:09"
        tray_w = d.textlength(tray, font=mono_tiny)
        d.rectangle([w - tray_w - 60, tb + 10, w - 8, tb + 50], fill=PATTERN)
        d.rectangle([w - tray_w - 52, tb + 16, w - tray_w - 26, tb + 42], fill=PAPER, outline=INK, width=2)
        d.text((w - tray_w - 46, tb + 18), "KB", font=mono_tiny, fill=INK)
        d.text((w - tray_w - 16, tb + 18), tray, font=mono_tiny, fill=INK)
        cursor(d, 620, 700, cell=3, pressed=True)

    # caption
    d.rectangle([0, h - 46, w, h], fill=INK)
    d.text((16, h - 40), caption, font=mono_bold, fill=PAPER)
    return img


def main():
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    panels = [
        panel("LINUX · retrOS GNU/Linux — panel, dock, calendar widget, free icon grid", "linux"),
        panel("macOS · Retro System 9 — menu bar, dock, launchpad", "macos"),
        panel("WINDOWS · Retrodows 95 — start menu, taskbar, tray, desktop widget", "windows"),
    ]
    w = sum(p.width for p in panels) + 40 * (len(panels) + 1)
    h = panels[0].height + 180
    sheet = Image.new("RGB", (w, h), (0x2A, 0x2A, 0x2A))
    d = ImageDraw.Draw(sheet)
    big = ImageFont.truetype(os.path.join(FONT_DIR, "pixel_operator_bold.ttf"), 80)
    d.text((40, 20), "RETROBIT DESKTOP", font=big, fill=PAPER)
    d.text((42, 92), "e-paper launcher for android tablets · pixel operator typeface (cc0) · m-pencil as a mouse · device wallpaper in dither · editable calendar · free icon placement on a 10x5 grid · snapping & virtual desktops",
           font=mono, fill=(0xC9, 0xC5, 0xB8))
    x = 40
    for p in panels:
        sheet.paste(p, (x, 136))
        x += p.width + 40
    sheet.save(OUT)
    print("wrote", OUT, sheet.size)


if __name__ == "__main__":
    main()
