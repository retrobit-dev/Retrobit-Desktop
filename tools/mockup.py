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

SHEET_BG = (0x2A, 0x2A, 0x2A)   # background of the whole sheet, shadows blend into it
PAPER = (0xE8, 0xE5, 0xDA)
INK = (0x1B, 0x1B, 0x1B)
PATTERN = tuple(int(PAPER[i] * 0.7 + INK[i] * 0.3) for i in range(3))
ACCENT = INK            # the paper palette has no accent of its own: the square prints in ink

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


def glyph_tile(d, x, y, size, kind="folder", label="", paper=PAPER, ink=INK, shape="flat", selected=False):
    """16x16-ish pixel-art app icon placeholder + label, mimicking IconMode.MONO output.

    `shape` documents IconTheme.AUTO: flat (Linux square), rounded (macOS), classic (Windows bevel).
    """
    inner = size - 10
    if selected:
        # the selected plate the app paints behind the icon (Modifier.background(ink))
        d.rectangle([x - 3, y - 3, x + inner + 3, y + inner + 3], fill=ink)
        paper, ink = ink, paper
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
        if selected:
            # inverted name chip (ink plate, paper text)
            d.rectangle([x + inner / 2 - tw / 2 - 4, y + inner + 2, x + inner / 2 + tw / 2 + 4, y + inner + 16], fill=ink)
            d.text((x + inner / 2 - tw / 2, y + inner + 4), label, font=mono_tiny, fill=paper)
        else:
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


def shell_frame(d, box, title, clock="13:37:09", dock=("Terminal", "Files", "Notes", "Camera", "Settings")):
    """One of our own apps, **full screen**, in the frame the launcher draws around it.

    Mirrors `ui/desktop/ShellHost.kt`: a 3.5 dp black border all round, a 40 dp title rail (accent
    square + app name on the left, live clock and the panel / desktop keys on the right) and the
    54 dp dock strip along the bottom. There are no floating windows and no desktop boxes any more:
    every app fills the screen and this frame is what makes it read as a window.
    """
    x0, y0, x1, y1 = box
    d.rectangle(box, fill=INK)                                   # the frame (3.5 dp of ink)
    bw, rail_h, dock_h = 8, 44, 58
    rail_top = y0 + bw
    d.rectangle([x0 + bw, rail_top, x1 - bw, rail_top + rail_h], fill=PAPER)
    ax, ay = x0 + bw + 12, rail_top + rail_h / 2 - 5
    d.rectangle([ax, ay, ax + 10, ay + 10], fill=ACCENT)         # the accent square of ShellRail
    d.text((ax + 20, rail_top + rail_h / 2 - 11), title, font=mono_bold, fill=INK)
    # readouts and keys, right to left: clock, quick panel, desktop
    tx = x1 - bw - 12
    for label in ("desktop", "panel"):
        tw = d.textlength(label, font=mono_tiny)
        d.rectangle([tx - tw - 14, rail_top + 8, tx, rail_top + rail_h - 8], fill=PAPER, outline=INK, width=2)
        d.text((tx - tw - 7, rail_top + rail_h / 2 - 6), label, font=mono_tiny, fill=INK)
        tx -= tw + 24
    cw = d.textlength(clock, font=mono)
    d.text((tx - cw, rail_top + rail_h / 2 - 10), clock, font=mono, fill=INK)
    # the content area the app owns
    content = [x0 + bw, rail_top + rail_h, x1 - bw, y1 - bw - dock_h]
    d.rectangle(content, fill=PAPER)
    d.line([content[0], content[3], content[2], content[3]], fill=INK, width=2)
    # dock: the desktop key, the apps, the panel key
    dy0, dy1 = content[3] + 2, y1 - bw
    d.rectangle([x0 + bw, dy0, x1 - bw, dy1], fill=PAPER)
    kx, ks = x0 + bw + 12, 42
    glyph_tile(d, kx, dy0 + 5, ks, kind="grid", label="", shape="flat")
    d.text((kx + 4, dy1 - 20), "desktop", font=mono_tiny, fill=INK)
    d.line([kx + ks + 10, dy0 + 10, kx + ks + 10, dy1 - 10], fill=INK, width=2)
    tx = kx + ks + 24
    for label in dock:
        tw = d.textlength(label, font=mono_tiny)
        d.rectangle([tx, dy0 + 6, tx + 40, dy0 + 46], fill=PAPER, outline=INK, width=2)
        d.rectangle([tx + 12, dy0 + 16, tx + 28, dy0 + 32], fill=INK)
        d.text((tx + 20 - tw / 2, dy1 - 20), label, font=mono_tiny, fill=INK)
        tx += 48
    tw = d.textlength("panel", font=mono_tiny)
    d.rectangle([x1 - bw - tw - 22, dy0 + 8, x1 - bw - 10, dy1 - 10], fill=PAPER, outline=INK, width=2)
    d.text((x1 - bw - tw - 16, dy1 - 20), "panel", font=mono_tiny, fill=INK)
    return content



def window_box(d, box, title, kind="term", focused=True, readout=None, snap_layer=False):
    """A floating window of one of our own apps (ui/wm/WindowFrame.kt).

    A framed box with a title bar you drag, a glyph, the three buttons and a resize grip in the
    bottom right corner. The focused window is drawn with an ink title bar, a heavier frame and a
    dithered drop shadow, and prints its live size while it is being resized.
    """
    x0, y0, x1, y1 = box
    if focused:
        hard_shadow(d, box, offset=6, cell=3)
    d.rectangle(box, fill=PAPER, outline=INK, width=3 if focused else 2)
    th = 34
    if focused:
        d.rectangle([x0 + 3, y0 + 3, x1 - 3, y0 + th], fill=INK)
    d.line([x0 + 2, y0 + th, x1 - 2, y0 + th], fill=INK, width=2)
    # the title glyph, printed at 16x16 like the app's own pixel icons
    gx, gy, fg = x0 + 10, y0 + 12, (PAPER if focused else INK)
    if kind == "term":
        for i in range(4):
            d.rectangle([gx + i * 3, gy + i * 2, gx + i * 3 + 2, gy + 2 + i * 2], fill=fg)
        d.rectangle([gx, gy + 11, gx + 13, gy + 13], fill=fg)
    else:
        for a in range(2):
            for b in range(2):
                d.rectangle([gx + a * 8, gy + b * 8, gx + a * 8 + 6, gy + b * 8 + 6], fill=fg)
    d.text((x0 + 36, y0 + 11), title, font=mono_bold, fill=fg)
    # the three title-bar keys, right to left: close, maximize, minimize
    for i, sym in enumerate(("x", "max", "min")):
        bx = x1 - 8 - (i + 1) * 26
        d.rectangle([bx, y0 + 8, bx + 20, y0 + 28], fill=PAPER, outline=INK, width=2)
        cx, cy = bx + 10, y0 + 18
        if sym == "x":
            d.line([cx - 5, cy - 5, cx + 5, cy + 5], fill=INK, width=2)
            d.line([cx + 5, cy - 5, cx - 5, cy + 5], fill=INK, width=2)
        elif sym == "max":
            d.rectangle([cx - 6, cy - 6, cx + 6, cy + 6], outline=INK, width=2)
        else:
            d.line([cx - 6, cy + 5, cx + 6, cy + 5], fill=INK, width=2)
    if readout:
        tw = d.textlength(readout, font=mono_tiny)
        d.text((x1 - 8 - 78 - tw, y0 + 11), readout, font=mono_tiny, fill=PAPER if focused else INK)
    # the grip printed in the corner the window can be resized from
    gx, gy = x1 - 18, y1 - 18
    for i in range(3):
        d.line([gx + i * 5, y1 - 6, x1 - 6, gy + i * 5], fill=INK, width=2)
    return (x0 + 4, y0 + th + 4, x1 - 4, y1 - 4)


def float_panel():
    """The desktop with floating windows of our own apps: the terminal focused and being resized,
    notes.txt behind it, a dashed snap preview against the right half, and the task list open."""
    w, h = 780, 1060
    img = Image.new("RGB", (w, h), PAPER)
    d = ImageDraw.Draw(img)
    grid(img)
    for cx, cy, sx, sy in [(14, 14, 1, 1), (w - 14, 14, -1, 1), (14, h - 14, 1, -1), (w - 14, h - 14, -1, -1)]:
        d.line([cx, cy, cx + sx * 26, cy], fill=INK, width=2)
        d.line([cx, cy, cx, cy + sy * 26], fill=INK, width=2)

    # ── the snap preview: dragging a window against the right edge fills half the desktop ──
    snap = (400, 44, w - 8, h - 8)
    fill_dither(d, snap, PATTERN, cell=4, level_max=3)
    dashed_rect(d, snap, dash=12, gap=8, width=3)

    # ── notes.txt, not focused: paper title bar, thinner frame ──
    nx0, ny0, nx1, ny1 = 120, 470, 470, 900
    content = window_box(d, (nx0, ny0, nx1, ny1), "notes.txt", kind="grid", focused=False)
    for i, line in enumerate(["# todo", "", "- solder the new rail", "- print the e-paper case", "- flash 176 on the tablet", "", "# done", "- floating windows", "- keyboard overlay"]):
        d.text((content[0] + 12, content[1] + 10 + i * 22), line, font=mono_tiny, fill=INK)
    d.rectangle([content[0] + 8, content[3] - 34, content[2] - 8, content[3] - 6], fill=PAPER, outline=INK, width=2)
    d.text((content[0] + 16, content[3] - 28), "new note…", font=mono_tiny, fill=PATTERN)

    # ── the focused terminal window, with its live size readout while resizing ──
    tx0, ty0, tx1, ty1 = 100, 90, 560, 560
    content = window_box(d, (tx0, ty0, tx1, ty1), "terminal", kind="term", focused=True, readout="740x470")
    lines = [
        ("retrobit ~ $ win list", INK),
        ("windows of our own apps - 2 open on desktop 1", INK),
        ("  terminal  floating  1520x940 at 780,300", PATTERN),
        ("  notes     minimized 1200x1100 at 120,470", PATTERN),
        ("retrobit ~ $ keyboard overlay", INK),
        ("keyboard -> floats over the desktop; nothing moves,", PATTERN),
        ("            not even a window frame", PATTERN),
        ("retrobit ~ $ _", INK),
    ]
    for i, (line, fill) in enumerate(lines):
        d.text((content[0] + 10, content[1] + 8 + i * 22), line, font=mono_tiny, fill=fill)

    cursor(d, tx1 - 26, ty1 - 40, cell=3, pressed=True)

    # ── top panel with the new task list ──
    d.rectangle([0, 0, w, 44], fill=PAPER)
    bevel(d, [0, 0, w - 1, 44])
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
    rx = pager(d, rx - (3 * 27 + 12), 9, size=22, cells=3, active=0)
    chip, cw = "[LINUX]", d.textlength("[LINUX]", font=mono_tiny)
    d.rectangle([rx - cw - 12, 9, rx - 4, 35], fill=PAPER, outline=INK, width=2)
    d.text((rx - cw - 8, 12), chip, font=mono_tiny, fill=INK)
    pix_diamond(d, 14, 10, cell=5)
    # "windows" is the panel's task list now
    text_run(d, 48, 13, ["retrOS", "system", "apps", "term"], mono, gap=14)
    tw = d.textlength("windows", font=mono)
    d.rectangle([48 + tw + 60, 7, 48 + tw + 60 + d.textlength("windows", font=mono) + 12, 37], fill=INK)
    d.text((48 + tw + 66, 13), "windows", font=mono, fill=PAPER)

    # ── the task list the panel opens ──
    mx0, my0 = 60, 60
    mx1, my1 = 320, 216
    d.rectangle([mx0, my0, mx1, my1], fill=PAPER, outline=INK, width=3)
    d.rectangle([mx0, my0, mx1, my0 + 26], fill=PATTERN)
    d.text((mx0 + 10, my0 + 6), "WINDOWS", font=mono_tiny, fill=INK)
    d.text((mx0 + 10, my0 + 34), "floating windows of our own apps", font=mono_tiny, fill=PATTERN)
    row = my0 + 58
    for label, state in [("terminal   (resizing)", ""), ("notes.txt  (minimized)", ""), ("minimize all windows", ""), ("close all windows", ""), ("reset window sizes", "")]:
        tw = d.textlength(label, font=mono_tiny)
        d.rectangle([mx0 + 8, row - 4, mx1 - 8, row + 18], fill=PAPER, outline=INK, width=2)
        d.text((mx0 + 16, row), label, font=mono_tiny, fill=INK)
        row += 26

    # ── left dock, under the windows ──
    d.rectangle([0, 44, 78, h], fill=PAPER)
    bevel(d, [0, 44, 78, h])
    for i, label in enumerate(["apps", "shell", "mon", "note", "setup"]):
        glyph_tile(d, 12, 60 + i * 84, 52, kind="grid" if i % 2 == 0 else "term", label="")
        d.text((16, 116 + i * 84), label, font=mono_tiny, fill=INK)
    return img


# ── palettes ────────────────────────────────────────────────────────────────────
# Mirrors ui/theme/Theme.kt: the e-paper palettes paint the desk in their own paper, the native ones
# bring the colour that desktop really uses behind its icons.
# The twelve palettes exactly as ui/theme/Theme.kt defines them: the six e-paper tones
# (paper / ink / accent, desk == paper) and the six native desktops with their real window skin.
PALETTES = [
    dict(name="paper", kind="paper", paper=(0xE8, 0xE5, 0xDA), ink=(0x1B, 0x1B, 0x1B), accent=(0x3F, 0x3F, 0x3F)),
    dict(name="sepia", kind="paper", paper=(0xEA, 0xDF, 0xC6), ink=(0x2E, 0x24, 0x18), accent=(0x8C, 0x6B, 0x3F)),
    dict(name="tri-color", kind="paper", paper=(0xED, 0xEB, 0xE4), ink=(0x16, 0x16, 0x16), accent=(0xB1, 0x28, 0x1F)),
    dict(name="ink", kind="paper", paper=(0x17, 0x17, 0x17), ink=(0xDA, 0xD6, 0xCA), accent=(0x8C, 0x88, 0x78)),
    dict(name="phosphor", kind="paper", paper=(0x0B, 0x11, 0x0B), ink=(0x9C, 0xF0, 0x8A), accent=(0x4F, 0xB9, 0x3F)),
    dict(name="amber", kind="paper", paper=(0x13, 0x0D, 0x05), ink=(0xFF, 0xB5, 0x42), accent=(0xE0, 0x7A, 0x16)),
    dict(name="windows 95", kind="native", paper=(0xC0, 0xC0, 0xC0), ink=(0x00, 0x00, 0x00), accent=(0x00, 0x00, 0x80),
         border=(0x00, 0x00, 0x00), button=(0xC0, 0xC0, 0xC0), button_text=(0x00, 0x00, 0x00),
         title=((0x00, 0x00, 0x80), (0x10, 0x84, 0xD0)), title_text=(0xFF, 0xFF, 0xFF),
         desk=((0x00, 0x80, 0x80), (0x00, 0x80, 0x80)), corner=0, lights=False, flat=False, diagonal=False),
    dict(name="windows 11", kind="native", paper=(0x20, 0x20, 0x20), ink=(0xFF, 0xFF, 0xFF), accent=(0x60, 0xCD, 0xFF),
         border=(0x3A, 0x3A, 0x3A), button=(0x2B, 0x2B, 0x2B), button_text=(0xFF, 0xFF, 0xFF),
         title=((0x2B, 0x2B, 0x2B), (0x2B, 0x2B, 0x2B)), title_text=(0xFF, 0xFF, 0xFF),
         desk=((0x1B, 0x1F, 0x24), (0x0F, 0x12, 0x16)), corner=8, lights=False, flat=True, diagonal=False),
    dict(name="aqua", kind="native", paper=(0xF4, 0xF4, 0xF6), ink=(0x1D, 0x1D, 0x1F), accent=(0x0A, 0x84, 0xFF),
         border=(0xB9, 0xB9, 0xBF), button=(0xF0, 0xF0, 0xF2), button_text=(0x1D, 0x1D, 0x1F),
         title=((0xF7, 0xF7, 0xF8), (0xDC, 0xDC, 0xDE)), title_text=(0x1D, 0x1D, 0x1F),
         desk=((0x3F, 0x72, 0xA8), (0x1E, 0x4E, 0x7E)), corner=10, lights=True, flat=False, diagonal=False),
    dict(name="aqua dark", kind="native", paper=(0x1E, 0x1E, 0x20), ink=(0xF2, 0xF2, 0xF7), accent=(0x0A, 0x84, 0xFF),
         border=(0x3C, 0x3C, 0x3F), button=(0x2E, 0x2E, 0x30), button_text=(0xF2, 0xF2, 0xF7),
         title=((0x3A, 0x3A, 0x3C), (0x2C, 0x2C, 0x2E)), title_text=(0xF2, 0xF2, 0xF7),
         desk=((0x17, 0x19, 0x1D), (0x0B, 0x0C, 0x0F)), corner=10, lights=True, flat=False, diagonal=False),
    dict(name="ubuntu", kind="native", paper=(0xF6, 0xF5, 0xF4), ink=(0x2E, 0x34, 0x36), accent=(0xE9, 0x54, 0x20),
         border=(0xBE, 0xBA, 0xB6), button=(0x4A, 0x4A, 0x4A), button_text=(0xFF, 0xFF, 0xFF),
         title=((0x3D, 0x3D, 0x3D), (0x2C, 0x2C, 0x2C)), title_text=(0xFF, 0xFF, 0xFF),
         desk=((0x77, 0x21, 0x6F), (0x4B, 0x17, 0x49)), corner=12, lights=False, flat=True, diagonal=True),
    dict(name="adwaita", kind="native", paper=(0x24, 0x24, 0x24), ink=(0xFF, 0xFF, 0xFF), accent=(0x78, 0xAE, 0xED),
         border=(0x3D, 0x3D, 0x3D), button=(0x30, 0x30, 0x30), button_text=(0xFF, 0xFF, 0xFF),
         title=((0x30, 0x30, 0x30), (0x24, 0x24, 0x24)), title_text=(0xFF, 0xFF, 0xFF),
         desk=((0x1A, 0x1A, 0x1A), (0x14, 0x14, 0x14)), corner=12, lights=False, flat=True, diagonal=False),
]


def mix(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


# The window chrome of the native themes, exactly the values in ui/theme/Theme.kt.
SKIN_WIN95 = dict(
    window=(0xC0, 0xC0, 0xC0), border=(0x00, 0x00, 0x00),
    title_top=(0x00, 0x00, 0x80), title_bot=(0x10, 0x84, 0xD0), title_text=(0xFF, 0xFF, 0xFF),
    text=(0x00, 0x00, 0x00), button=(0xC0, 0xC0, 0xC0), button_text=(0x00, 0x00, 0x00), dot=(0x00, 0x00, 0x80), radius=0, lights=False,
)
SKIN_AQUA = dict(
    window=(0xF4, 0xF4, 0xF6), border=(0xB9, 0xB9, 0xBF),
    title_top=(0xF7, 0xF7, 0xF8), title_bot=(0xDC, 0xDC, 0xDE), title_text=(0x1D, 0x1D, 0x1F),
    text=(0x1D, 0x1D, 0x1F), button=(0xE4, 0xE4, 0xE8), button_text=(0x5A, 0x5A, 0x5F), dot=(0x0A, 0x84, 0xFF), radius=10, lights=True,
)
SKIN_WIN11 = dict(
    window=(0x20, 0x20, 0x20), border=(0x3A, 0x3A, 0x3A),
    title_top=(0x2B, 0x2B, 0x2B), title_bot=(0x2B, 0x2B, 0x2B), title_text=(0xFF, 0xFF, 0xFF),
    text=(0xFF, 0xFF, 0xFF), button=(0x2B, 0x2B, 0x2B), button_text=(0xFF, 0xFF, 0xFF), dot=(0x60, 0xCD, 0xFF), radius=8, lights=False,
)
SKIN_UBUNTU = dict(
    window=(0xF6, 0xF5, 0xF4), border=(0xBE, 0xBA, 0xB6),
    title_top=(0x3D, 0x3D, 0x3D), title_bot=(0x2C, 0x2C, 0x2C), title_text=(0xFF, 0xFF, 0xFF),
    text=(0x2E, 0x34, 0x36), button=(0x4A, 0x4A, 0x4A), button_text=(0xFF, 0xFF, 0xFF), dot=(0xE9, 0x54, 0x20), radius=12, lights=False,
)
# the real traffic lights of macOS: close / minimize / zoom
AQUA_LIGHTS = [(0xFF, 0x5F, 0x57), (0xFE, 0xBC, 0x2E), (0x28, 0xC8, 0x40)]


def native_window(d, box, title, skin, glyph_kind="term", content_bg=None):
    """A floating window dressed in the **real** chrome of a desktop theme.

    Mirrors ui/wm/WindowFrame.kt: a colour gradient in the title bar, the desktop's own buttons
    (beveled boxes or flat discs, traffic lights on macOS), rounded corners when the skin has
    them, and a soft layered shadow instead of the e-paper printing plate and hard offset.
    """
    x0, y0, x1, y1 = box
    radius = skin["radius"]
    bar = 26 if radius == 0 else 32
    # shadow: a single hard square for Windows 95, three translucent layers everywhere else
    if radius == 0:
        d.rectangle([x0 + 4, y0 + 4, x1 + 4, y1 + 4], fill=mix(SHEET_BG, (0, 0, 0), 0.45))
    else:
        for i, a in ((7, 0.30), (5, 0.22), (3, 0.14)):
            d.rounded_rectangle([x0 + i, y0 + i, x1 + i, y1 + i], radius=radius + 2, fill=mix(SHEET_BG, (0, 0, 0), a))
    if radius == 0:
        d.rectangle(box, fill=skin["window"], outline=skin["border"], width=2)
    else:
        d.rounded_rectangle(box, radius=radius, fill=skin["window"], outline=skin["border"])
    # title bar: the real gradient (Windows 95 navy -> blue, Aqua snow, Ubuntu dark headerbar)
    for i in range(bar - 4):
        t = i / max(1, bar - 5)
        if radius == 0:
            d.line([x0 + 3, y0 + 3 + i, x1 - 3, y0 + 3 + i], fill=mix(skin["title_top"], skin["title_bot"], t))
    if radius != 0:
        d.rounded_rectangle([x0 + 1, y0 + 1, x1 - 1, y0 + bar], radius=radius, fill=mix(skin["title_top"], skin["title_bot"], 0.5))
        d.rectangle([x0 + 2, y0 + bar - 6, x1 - 2, y0 + bar], fill=mix(skin["title_top"], skin["title_bot"], 1.0))
    # ~16 px pixel glyph before the name
    gx, gy = x0 + 12, y0 + 3 + (bar - 4) // 2 - 7
    if glyph_kind == "term":
        for i in range(4):
            d.rectangle([gx + i * 4, gy + i * 2, gx + i * 4 + 3, gy + 3 + i * 2], fill=skin["title_text"])
        d.rectangle([gx, gy + 12, gx + 15, gy + 15], fill=skin["title_text"])
    else:
        for a in range(2):
            for b in range(2):
                d.rectangle([gx + a * 9, gy + b * 9, gx + a * 9 + 7, gy + b * 9 + 7], fill=skin["title_text"])
    d.text((x0 + 34, y0 + 3 + (bar - 4) // 2 - 6), title, font=mono_tiny, fill=skin["title_text"])
    # buttons: traffic lights on the left (macOS), boxes or discs on the right (everything else)
    bw = 18
    if skin["lights"]:
        for j, col in enumerate(AQUA_LIGHTS):
            cx = x0 + 20 + j * 20
            cy = y0 + 3 + (bar - 4) / 2
            d.ellipse([cx - 7, cy - 7, cx + 7, cy + 7], fill=col)
            d.ellipse([cx - 7, cy - 7, cx + 7, cy + 7], outline=mix(col, (0, 0, 0), 0.25))
    else:
        for i, sym in enumerate(("x", "max", "min")):
            bx = x1 - 8 - (i + 1) * (bw + 2)
            by = y0 + 3 + (bar - 4) / 2 - bw / 2
            if radius == 0:   # the beveled box of Windows 95
                d.rectangle([bx, by, bx + bw, by + bw], fill=skin["button"], outline=(0, 0, 0))
                d.line([bx, by, bx + bw, by], fill=(0xFF, 0xFF, 0xFF), width=2)
                d.line([bx, by, bx, by + bw], fill=(0xFF, 0xFF, 0xFF), width=2)
                d.line([bx, by + bw, bx + bw, by + bw], fill=(0x40, 0x40, 0x40), width=2)
                d.line([bx + bw, by, bx + bw, by + bw], fill=(0x40, 0x40, 0x40), width=2)
            else:             # the modern themes draw a bare glyph plus a coloured accent dot
                pass
            cx, cy = bx + bw / 2, by + bw / 2
            if sym == "x":
                d.line([cx - 4, cy - 4, cx + 4, cy + 4], fill=skin["button_text"], width=2)
                d.line([cx + 4, cy - 4, cx - 4, cy + 4], fill=skin["button_text"], width=2)
            elif sym == "max":
                d.rectangle([cx - 5, cy - 5, cx + 5, cy + 5], outline=skin["button_text"], width=2)
            else:
                d.line([cx - 5, cy + 4, cx + 5, cy + 4], fill=skin["button_text"], width=2)
            if radius != 0:   # GNOME keeps a coloured dot in the corner of every window button
                dot = (0xE8, 0x11, 0x23) if sym == "x" else skin["dot"]
                d.ellipse([bx + bw - 6, by + bw - 6, bx + bw, by + bw], fill=dot)
    content = (x0 + 4, y0 + bar, x1 - 4, y1 - 4)
    d.rectangle(content, fill=content_bg or skin["window"])
    return content


def native_widget(d, box, skin, title, lines, meter=None):
    """A desktop widget in the chrome of a native theme: ui/desktop/Widgets.kt, native path.

    The window face with the theme's rounded corners and border, a header bar painted with the
    theme's **own title gradient**, and - if the widget has one - a meter in the theme accent over
    the theme's soft face. The e-paper palettes print a hatched header and ink cells instead.
    """
    x0, y0, x1, y1 = box
    radius = skin["radius"]
    bar = 21 if radius == 0 else 23
    # soft shadow (real desktop widgets float), a hard square one for Windows 95
    if radius == 0:
        d.rectangle([x0 + 3, y0 + 3, x1 + 3, y1 + 3], fill=mix(SHEET_BG, (0, 0, 0), 0.45))
    else:
        for i, a in ((6, 0.30), (4, 0.20), (2, 0.12)):
            d.rounded_rectangle([x0 + i, y0 + i, x1 + i, y1 + i], radius=radius + 2, fill=mix(SHEET_BG, (0, 0, 0), a))
    if radius == 0:
        d.rectangle(box, fill=skin["window"], outline=skin["border"], width=2)
    else:
        d.rounded_rectangle(box, radius=radius, fill=skin["window"], outline=skin["border"])
    # the header bar: the theme's real title gradient (flat for Windows 11 / GNOME)
    for i in range(bar - 2):
        t = i / max(1, bar - 3)
        d.line([x0 + 2, y0 + 2 + i, x1 - 2, y0 + 2 + i], fill=mix(skin["title_top"], skin["title_bot"], t))
    # a 12 px glyph then the widget name, both in the header's own text colour
    gx, gy = x0 + 7, y0 + 2 + (bar - 2) // 2 - 5
    d.rectangle([gx, gy, gx + 9, gy + 9], fill=skin["title_text"])
    d.text((gx + 15, y0 + 2 + (bar - 2) // 2 - 6), title, font=mono_tiny, fill=skin["title_text"])
    d.line([x0 + 1, y0 + bar, x1 - 1, y0 + bar], fill=skin["border"])
    cy = y0 + bar + 8
    for line in lines:
        d.text((x0 + 9, cy), line, font=mono_tiny, fill=skin["text"])
        cy += 16
    if meter is not None:
        frac, label = meter
        my0, my1 = cy + 4, cy + 14
        r = (my1 - my0) / 2
        d.rounded_rectangle([x0 + 9, my0, x1 - 9, my1], radius=r, fill=mix(skin["window"], skin["text"], 0.13))
        if frac > 0:
            d.rounded_rectangle([x0 + 9, my0, x0 + 9 + (x1 - 9 - (x0 + 9)) * frac, my1], radius=r, fill=skin["dot"])
        d.text((x0 + 9, my1 + 4), label, font=mono_tiny, fill=skin["text"])
    return (x0 + 9, cy)


def native_desktop(kind):
    """One panel per native desktop theme, drawn in that desktop's real colours - the same values
    the Kotlin NativeSkin carries, so the mockup documents what the launcher actually paints."""
    w, h = 780, 1060
    img = Image.new("RGB", (w, h), PAPER)
    d = ImageDraw.Draw(img)

    if kind == "win95":
        desk, panel, taskbar, ink = (0x00, 0x80, 0x80), (0xC0, 0xC0, 0xC0), (0xC0, 0xC0, 0xC0), (0x00, 0x00, 0x00)
        d.rectangle([0, 0, w, h], fill=desk)
        # desktop icons: silver plates, label shadows, the real navy selection under the name
        for i, (label, sel) in enumerate([("My Computer", False), ("Recycle Bin", False), ("Terminal", True), ("Notes", False)]):
            gx, gy = 26, 40 + i * 96
            d.rectangle([gx, gy, gx + 52, gy + 52], fill=panel, outline=(0, 0, 0), width=2)
            d.rectangle([gx + 13, gy + 15, gx + 39, gy + 37], fill=(0xFF, 0xFF, 0xFF), outline=(0, 0, 0))
            d.text((gx + 17, gy + 20), ">>", font=mono_tiny, fill=(0x00, 0x80, 0x80))
            tw = d.textlength(label, font=mono_tiny)
            d.text((gx + 26 - tw / 2 + 1, gy + 59), label, font=mono_tiny, fill=(0x00, 0x00, 0x00))
            d.text((gx + 26 - tw / 2, gy + 58), label, font=mono_tiny, fill=(0xFF, 0xFF, 0xFF))
            if sel:
                d.rectangle([gx + 26 - tw / 2 - 3, gy + 56, gx + 26 + tw / 2 + 3, gy + 73], fill=(0x00, 0x00, 0x80))
                d.text((gx + 26 - tw / 2, gy + 58), label, font=mono_tiny, fill=(0x00, 0x80, 0x80))
        # two floating windows in the real 95 chrome
        content = native_window(d, (150, 130, 640, 470), "terminal", SKIN_WIN95, content_bg=(0x00, 0x00, 0x00))
        for i, line in enumerate(["rb-sh 1.0 / retrobit cyberdeck", "", "C:\\RETROBIT> palette win95",
                                  "desk #008080", "title #000080 > #1084D0",
                                  "panel #C0C0C0 / accent #000080", "C:\\RETROBIT> _"]):
            d.text((content[0] + 10, content[1] + 8 + i * 20), line, font=mono_tiny,
                   fill=(0xFF, 0xFF, 0xFF) if i == 0 else (0xC0, 0xC0, 0xC0))
        content = native_window(d, (110, 500, 520, 800), "notes.txt", SKIN_WIN95, glyph_kind="grid")
        for i, line in enumerate(["# real colours, not e-paper", "", "- teal desk #008080",
                                  "- silver window face", "- navy > blue title bar",
                                  "- beveled buttons", "- hard square shadow"]):
            d.text((content[0] + 12, content[1] + 8 + i * 19), line, font=mono_tiny, fill=(0x00, 0x00, 0x00))
        # taskbar, start button and the tray clock
        d.rectangle([0, h - 44, w, h], fill=taskbar)
        d.line([0, h - 44, w, h - 44], fill=(0xFF, 0xFF, 0xFF), width=2)
        d.rectangle([4, h - 40, 96, h - 6], fill=taskbar, outline=(0, 0, 0))
        d.line([4, h - 40, 96, h - 40], fill=(0xFF, 0xFF, 0xFF), width=2)
        d.line([4, h - 40, 4, h - 6], fill=(0xFF, 0xFF, 0xFF), width=2)
        d.text((16, h - 30), "Start", font=mono_bold, fill=ink)
        for i, label in enumerate(["terminal", "notes.txt"]):
            bx = 104 + i * 118
            d.rectangle([bx, h - 40, bx + 112, h - 6], fill=taskbar, outline=(0, 0, 0))
            d.line([bx, h - 40, bx + 112, h - 40], fill=(0xFF, 0xFF, 0xFF), width=2)
            d.text((bx + 8, h - 30), label, font=mono_tiny, fill=ink)
        d.rectangle([w - 150, h - 40, w - 6, h - 6], fill=taskbar, outline=(0, 0, 0))
        d.text((w - 142, h - 30), "13:37", font=mono_tiny, fill=ink)
        # a gadget on the teal: silver face, the navy > blue header bar, an ink-on-silver meter
        native_widget(d, (500, 830, w - 24, h - 66), SKIN_WIN95, "chrono",
                      ["13:37:09", "Wed, 14 Oct 2026"], meter=(0.62, "RAM  8.4 / 12 GB"))
    elif kind == "aqua":
        desk_top, desk_bot = (0x3F, 0x72, 0xA8), (0x1E, 0x4E, 0x7E)
        for y in range(h):
            d.line([0, y, w, y], fill=mix(desk_top, desk_bot, y / h))
        # menu bar
        d.rectangle([0, 0, w, 30], fill=(0xF4, 0xF4, 0xF6))
        d.line([0, 30, w, 30], fill=(0xB9, 0xB9, 0xBF))
        d.text((12, 8), "Finder  File  Edit  View  Window  Help", font=mono_tiny, fill=(0x1D, 0x1D, 0x1F))
        d.text((w - 96, 8), "13:37:09", font=mono_tiny, fill=(0x1D, 0x1D, 0x1F))
        # two windows: snow chrome, grey gradient bar, real traffic lights on the left
        content = native_window(d, (120, 96, 620, 400), "terminal", SKIN_AQUA, content_bg=(0xFF, 0xFF, 0xFF))
        for i, line in enumerate(["macOS Aqua - window face #F4F4F6",
                                  "title #F7F7F8 > #DCDCDE",
                                  "lights #FF5F57 / #FEBC2E / #28C840",
                                  "desk #3F72A8 > #1E4E7E",
                                  "selection blue #0A84FF"]):
            d.text((content[0] + 12, content[1] + 10 + i * 19), line, font=mono_tiny, fill=(0x1D, 0x1D, 0x1F))
        content = native_window(d, (90, 450, 520, 780), "notes.txt", SKIN_AQUA, glyph_kind="grid", content_bg=(0xFF, 0xFF, 0xFF))
        for i, line in enumerate(["# notes", "", "- corner radius 10", "- real soft shadow",
                                  "- no scanlines, no dither", "- icons in full colour"]):
            d.text((content[0] + 12, content[1] + 10 + i * 19), line, font=mono_tiny, fill=(0x1D, 0x1D, 0x1F))
        # dock
        dw = 400
        dx = (w - dw) // 2
        d.rounded_rectangle([dx, h - 84, dx + dw, h - 16], radius=16, fill=(0xF4, 0xF4, 0xF6), outline=(0xB9, 0xB9, 0xBF))
        for i in range(6):
            gx = dx + 24 + i * 60
            d.rounded_rectangle([gx, h - 72, gx + 44, h - 28], radius=10, fill=(0xE4, 0xE4, 0xE8), outline=(0xB9, 0xB9, 0xBF))
            col = [(0x0A, 0x84, 0xFF), (0x34, 0xC7, 0x59), (0xFF, 0x95, 0x00), (0xFF, 0x3B, 0x30), (0x30, 0xD1, 0x58), (0x5E, 0x5C, 0xE6)][i]
            d.rectangle([gx + 13, h - 62, gx + 31, h - 38], fill=col)
        # a dashboard widget: snow face, grey gradient bar, system-blue meter
        native_widget(d, (60, 810, 420, h - 100), SKIN_AQUA, "chrono",
                      ["13:37:09"], meter=(0.62, "RAM  8.4 / 12 GB"))
    elif kind == "win11":
        desk_top, desk_bot = (0x1B, 0x1F, 0x24), (0x0F, 0x12, 0x16)
        for y in range(h):
            d.line([0, y, w, y], fill=mix(desk_top, desk_bot, y / h))
        # desktop icons in colour: rounded tiles with white labels, the selected one on the accent
        for i, (label, col, sel) in enumerate([("Terminal", (0x0C, 0xC4, 0xC4), True), ("Files", (0xFF, 0xC1, 0x2E), False),
                                               ("Notes", (0x60, 0xCD, 0xFF), False)]):
            gx, gy = 30, 40 + i * 100
            d.rounded_rectangle([gx, gy, gx + 54, gy + 54], radius=8, fill=(0x2B, 0x2B, 0x2B), outline=(0x3A, 0x3A, 0x3A))
            d.rounded_rectangle([gx + 13, gy + 13, gx + 41, gy + 41], radius=4, fill=col)
            tw = d.textlength(label, font=mono_tiny)
            if sel:
                d.rounded_rectangle([gx + 27 - tw / 2 - 5, gy + 58, gx + 27 + tw / 2 + 5, gy + 74], radius=4, fill=(0x60, 0xCD, 0xFF))
                d.text((gx + 27 - tw / 2, gy + 60), label, font=mono_tiny, fill=(0x00, 0x24, 0x3D))
            else:
                d.text((gx + 27 - tw / 2, gy + 60), label, font=mono_tiny, fill=(0xFF, 0xFF, 0xFF))
        # two Mica windows: flat dark title bar, 8 px corners, accent dots on the buttons
        content = native_window(d, (150, 130, 640, 470), "terminal", SKIN_WIN11, content_bg=(0x0C, 0x0C, 0x0C))
        for i, line in enumerate(["Windows 11 - face #202020",
                                  "title bar #2B2B2B (flat)",
                                  "desk #1B1F24 > #0F1216",
                                  "accent #60CDFF, text #00243D",
                                  "corner radius 8"]):
            d.text((content[0] + 10, content[1] + 10 + i * 19), line, font=mono_tiny, fill=(0xE8, 0xE8, 0xE8))
        content = native_window(d, (110, 500, 520, 800), "notes.txt", SKIN_WIN11, glyph_kind="grid")
        for i, line in enumerate(["# notes", "", "- rounded corners (8)", "- real soft shadow",
                                  "- flat window buttons", "- no scanlines, no dither"]):
            d.text((content[0] + 12, content[1] + 10 + i * 19), line, font=mono_tiny, fill=(0xFF, 0xFF, 0xFF))
        # the centred taskbar of Windows 11, with the blue Start squares
        d.rectangle([0, h - 44, w, h], fill=(0x20, 0x20, 0x20))
        d.line([0, h - 44, w, h - 44], fill=(0x3A, 0x3A, 0x3A))
        cx0 = w // 2 - 5 * 27
        for i in range(5):
            bx = cx0 + i * 54
            if i == 0:
                for a in range(2):
                    for b in range(2):
                        d.rectangle([bx + a * 12, h - 34 + b * 12, bx + a * 12 + 10, h - 34 + b * 12 + 10], fill=(0x4C, 0xC2, 0xFF))
            else:
                col = [(0x0C, 0xC4, 0xC4), (0xFF, 0xC1, 0x2E), (0x60, 0xCD, 0xFF), (0xE8, 0xE8, 0xE8)][i - 1]
                d.rounded_rectangle([bx + 2, h - 34, bx + 24, h - 12], radius=4, fill=col)
        d.text((w - 96, h - 32), "13:37", font=mono_tiny, fill=(0xFF, 0xFF, 0xFF))
        # a Windows 11 widget: Mica face, flat bar, accent meter
        native_widget(d, (470, 830, w - 24, h - 66), SKIN_WIN11, "sysinfo",
                      ["BAT 86%  +", "RAM  8.4 / 12 GB"], meter=(0.58, "DISK 412 / 512 GB"))
    else:  # ubuntu
        desk_top, desk_bot = (0x77, 0x21, 0x6F), (0x4B, 0x17, 0x49)
        for y in range(h):
            t = y / h
            d.line([0, y, w, y], fill=mix(mix(desk_top, desk_bot, t), (0x5E, 0x1B, 0x5C), 0.25))
        # top bar (GNOME shell)
        d.rectangle([0, 0, w, 30], fill=(0x1D, 0x1D, 0x1D))
        d.text((12, 8), "Activities", font=mono_tiny, fill=(0xFF, 0xFF, 0xFF))
        d.text((w - 150, 8), "17:37  EN  100%  13:37", font=mono_tiny, fill=(0xFF, 0xFF, 0xFF))
        # windows: dark Yaru headerbar, flat GNOME discs, the close button in an orange disc
        content = native_window(d, (130, 96, 640, 420), "terminal", SKIN_UBUNTU, content_bg=(0xFF, 0xFF, 0xFF))
        for i, line in enumerate(["Ubuntu / Yaru - face #F6F5F4",
                                  "headerbar #3D3D3D > #2C2C2C",
                                  "desk #77216F > #4B1749 (diagonal)",
                                  "accent dot + close #E81123",
                                  "corner radius 12"]):
            d.text((content[0] + 14, content[1] + 10 + i * 19), line, font=mono_tiny, fill=(0x2E, 0x34, 0x36))
        content = native_window(d, (100, 460, 520, 780), "notes.txt", SKIN_UBUNTU, glyph_kind="grid", content_bg=(0xFF, 0xFF, 0xFF))
        for i, line in enumerate(["# notes", "", "- flat GNOME buttons", "- light window face",
                                  "- highlight #E95420", "- rounded corners (12)"]):
            d.text((content[0] + 14, content[1] + 10 + i * 19), line, font=mono_tiny, fill=(0x2E, 0x34, 0x36))
        # ubuntu dock, vertical, with the orange app tile
        d.rounded_rectangle([10, 200, 76, 720], radius=14, fill=(0x1D, 0x1D, 0x1D))
        for i in range(7):
            gy = 216 + i * 70
            d.rounded_rectangle([20, gy, 66, gy + 46], radius=10, fill=(0x3D, 0x3D, 0x3D))
            col = [(0xE9, 0x54, 0x20), (0x77, 0x21, 0x6F), (0xF6, 0xF5, 0xF4), (0xE9, 0x54, 0x20),
                   (0x5E, 0x27, 0x5B), (0xF6, 0xF5, 0xF4), (0xE9, 0x54, 0x20)][i]
            d.rectangle([33, gy + 11, 53, gy + 35], fill=col)
        # a GNOME widget: light face, dark headerbar, orange accent meter
        native_widget(d, (470, 810, w - 24, h - 66), SKIN_UBUNTU, "sysinfo",
                      ["BAT 86%  +", "RAM  8.4 / 12 GB"], meter=(0.58, "DISK 412 / 512 GB"))
    return img


def icon_shape_tile(d, x, y, size, shape, paper, ink, accent, edge, desk, label):
    """One icon tile in the shape a theme draws, with that theme's own colours.

    Mirrors IconCache.frame() in the app: classic 95 is the beveled square with the navy trim,
    fluent the soft rounded square, aqua the glossy tile with a pale edge, yaru the rounded square
    with the orange trim, gnome the circle with a thin ring, and the e-paper palettes the printed
    square / rounded / flat tile in ink on paper.
    """
    pad = 10
    # the desk behind the tile: a gradient, or a flat paper card for the e-paper side
    if desk is not None:
        for i in range(size + pad + 26):
            d.line([x, y + i, x + size + 2 * pad, y + i], fill=mix(desk[0], desk[1], i / (size + pad + 25)))
    else:
        d.rectangle([x, y, x + size + 2 * pad, y + size + pad + 14], fill=paper)

    inset = size * (0.06 if shape == "classic" else 0.03)
    left, top = x + pad + inset, y + pad + inset
    right, bottom = x + pad + size - inset, y + pad + size - inset
    radius = {"classic": 0.0, "rounded": 0.20, "flat": 0.06, "squircle": 0.24,
              "aqua": 0.26, "yaru": 0.16, "circle": 0.50}.get(shape, 0.0) * size
    box = [left, top, right, bottom]

    if shape == "circle":
        d.ellipse(box, fill=paper, outline=ink, width=max(2, int(size * 0.05)))
    else:
        d.rounded_rectangle(box, radius=radius, fill=paper)

    # the "app" itself: a coloured plate inside the tile
    inner = size * 0.26
    ibox = [left + inner, top + inner, right - inner, bottom - inner]
    if shape == "circle":
        d.ellipse(ibox, fill=accent)
    else:
        d.rounded_rectangle(ibox, radius=size * (0.02 if shape == "classic" else 0.10), fill=accent)

    # the trim of each shape
    if shape == "classic":
        d.rectangle(box, outline=ink, width=max(2, int(size * 0.07)))
        d.line([left + 3, top + 3, right - 3, top + 3], fill=paper, width=max(1, int(size * 0.03)))
        d.line([left + 3, top + 3, left + 3, bottom - 3], fill=paper, width=max(1, int(size * 0.03)))
        d.rectangle([left + size * 0.10, bottom - size * 0.13, right - size * 0.10, bottom - size * 0.06], fill=accent)
    elif shape in ("rounded", "flat"):
        d.rounded_rectangle(box, radius=radius, outline=ink, width=max(2, int(size * 0.05)))
    elif shape == "squircle":
        d.rounded_rectangle(box, radius=radius, outline=edge, width=max(1, int(size * 0.02)))
        d.line([left + 4, top + 4, right - 4, top + 4], fill=(0x8A, 0x8A, 0x8A), width=max(1, int(size * 0.02)))
    elif shape == "aqua":
        d.rounded_rectangle(box, radius=radius, outline=edge, width=max(1, int(size * 0.035)))
        d.line([left + 4, top + size * 0.10, right - 4, top + size * 0.10], fill=(0xFF, 0xFF, 0xFF), width=max(2, int(size * 0.07)))
        d.line([left + 4, bottom - size * 0.03, right - 4, bottom - size * 0.03], fill=edge, width=max(2, int(size * 0.04)))
    elif shape == "yaru":
        d.rounded_rectangle(box, radius=radius, outline=edge, width=max(1, int(size * 0.025)))
        d.line([left + size * 0.18, bottom - size * 0.06, right - size * 0.18, bottom - size * 0.06], fill=accent, width=max(2, int(size * 0.055)))
    tw = d.textlength(label, font=mono_tiny)
    d.text((x + pad + size / 2 - tw / 2, y + size + pad + 16), label, font=mono_tiny, fill=(0xC9, 0xC5, 0xB8))


ICON_SHAPES = [
    # label, shape, paper (tile face), ink (e-paper frame / ring), accent, desk (gradient or None)
    ("classic 95", "classic", (0xC0, 0xC0, 0xC0), (0x00, 0x00, 0x00), (0x00, 0x00, 0x80), ((0x00, 0x80, 0x80), (0x00, 0x80, 0x80))),
    ("fluent", "squircle", (0x20, 0x20, 0x20), (0x3A, 0x3A, 0x3A), (0x60, 0xCD, 0xFF), ((0x1B, 0x1F, 0x24), (0x0F, 0x12, 0x16))),
    ("aqua", "aqua", (0xF4, 0xF4, 0xF6), (0xB9, 0xB9, 0xBF), (0x0A, 0x84, 0xFF), ((0x3F, 0x72, 0xA8), (0x1E, 0x4E, 0x7E))),
    ("aqua dark", "aqua", (0x1E, 0x1E, 0x20), (0x3C, 0x3C, 0x3F), (0x0A, 0x84, 0xFF), ((0x17, 0x19, 0x1D), (0x0B, 0x0C, 0x0F))),
    ("yaru", "yaru", (0xF6, 0xF5, 0xF4), (0xBE, 0xBA, 0xB6), (0xE9, 0x54, 0x20), ((0x77, 0x21, 0x6F), (0x4B, 0x17, 0x49))),
    ("gnome", "circle", (0x24, 0x24, 0x24), (0x78, 0xAE, 0xED), (0x78, 0xAE, 0xED), ((0x1A, 0x1A, 0x1A), (0x14, 0x14, 0x14))),
    # the e-paper side: the print tile keeps its ink outline and takes the shape from the shell style
    ("square (win)", "classic", (0xE8, 0xE5, 0xDA), (0x1B, 0x1B, 0x1B), (0x1B, 0x1B, 0x1B), None),
    ("rounded (mac)", "rounded", (0xE8, 0xE5, 0xDA), (0x1B, 0x1B, 0x1B), (0x8C, 0x6B, 0x3F), None),
    ("flat (linux)", "flat", (0xE8, 0xE5, 0xDA), (0x1B, 0x1B, 0x1B), (0x3F, 0x3F, 0x3F), None),
]


def palette_chip(d, x, y, w, h, pal):
    """One entry of the Settings palette picker, drawn the way the launcher paints it.

    E-paper: a paper card with the ink frame and a flat accent band. Native: the desk colour
    (gradient, or diagonal on Ubuntu) behind a window using that desktop's real chrome - rounded
    corners, its own title gradient, its own buttons, its own selection colour.
    """
    x1, y1 = x + w, y + h
    paper, ink, accent = pal["paper"], pal["ink"], pal["accent"]
    if pal["kind"] == "paper":
        d.rectangle([x, y, x1, y1], fill=paper, outline=ink, width=2)
        d.rectangle([x + 3, y + 3, x1 - 3, y + 22], fill=accent, outline=ink)
        for i, sw in enumerate([paper, ink, accent]):
            d.rectangle([x + 8 + i * 30, y + 30, x + 30 + i * 30, y + 52], fill=sw, outline=ink)
        d.text((x + (w - d.textlength(pal["name"], font=mono_tiny)) / 2, y1 - 24), pal["name"], font=mono_tiny, fill=ink)
        return
    # ── native: the desk, then a real window on top of it ──
    (dt, db) = pal["desk"]
    for i in range(h):
        t = i / h
        if pal["diagonal"]:
            c = mix(mix(dt, db, t), mix(dt, db, 1 - t), 0.35)
        else:
            c = mix(dt, db, t)
        d.line([x, y + i, x1, y + i], fill=c)
    wx0, wx1 = x + int(w * 0.07), x1 - int(w * 0.07)
    wy0, wy1 = y + int(h * 0.10), y1 - 44
    bar = 22 if pal["corner"] == 0 else 26
    if pal["corner"]:
        d.rounded_rectangle([wx0, wy0, wx1, wy1], radius=pal["corner"], fill=paper, outline=pal["border"])
    else:
        d.rectangle([wx0, wy0, wx1, wy1], fill=paper, outline=pal["border"], width=2)
    tt, tb = pal["title"]
    for i in range(bar - 3):
        t = i / max(1, bar - 4)
        d.line([wx0 + 2, wy0 + 2 + i, wx1 - 2, wy0 + 2 + i], fill=mix(tt, tb, t))
    if pal["lights"]:
        for j, col in enumerate([(0xFF, 0x5F, 0x57), (0xFE, 0xBC, 0x2E), (0x28, 0xC8, 0x40)]):
            cx, cy = wx0 + 12 + j * 13, wy0 + 2 + (bar - 3) / 2
            d.ellipse([cx - 5, cy - 5, cx + 5, cy + 5], fill=col)
    else:
        for j in range(3):
            bx = wx1 - 8 - (j + 1) * 12
            by = wy0 + 2 + (bar - 3) / 2 - 5
            if pal["flat"]:
                d.ellipse([bx, by, bx + 10, by + 10], fill=pal["button"])
            else:
                d.rectangle([bx, by, bx + 10, by + 10], fill=pal["button"], outline=(0, 0, 0))
                d.line([bx, by, bx + 10, by], fill=(0xFF, 0xFF, 0xFF))
    d.text((wx0 + 6, wy0 + 5), "Aa", font=mono_tiny, fill=pal["title_text"])
    # the body of the window: button face, selection colour, text
    d.rectangle([wx0 + 2, wy0 + bar, wx1 - 2, wy1 - 2], fill=paper)
    for i, sw in enumerate([pal["button"], accent, ink]):
        d.rectangle([wx0 + 10 + i * 28, wy0 + bar + 8, wx0 + 32 + i * 28, wy0 + bar + 28], fill=sw, outline=pal["border"])
    # the name sits on the desk colour, so pick the side of the desk it reads against
    nw = d.textlength(pal["name"], font=mono_tiny)
    luma = sum(db) / 3
    d.text((x + (w - nw) / 2, y1 - 26), pal["name"], font=mono_tiny,
           fill=(0x0B, 0x0B, 0x0B) if luma > 140 else (0xFF, 0xFF, 0xFF))


def pager(d, x, y, size=26, cells=3, active=0):
    """Workspace switcher: one numbered square per page of desktop icons and widgets."""
    p = INK
    for i in range(cells):
        bx = x + i * (size + 5)
        filled = i == active
        d.rectangle([bx, y, bx + size, y + size], fill=p if filled else PAPER, outline=p, width=2)
        num = str(i + 1)
        nw = d.textlength(num, font=mono_tiny)
        d.text((bx + size / 2 - nw / 2, y + 2), num, font=mono_tiny, fill=PAPER if filled else INK)
    return x


def widget_grip(d, x1, y1, size=11):
    """The manual-resize grip printed inside the bottom-right corner of a widget."""
    for i in range(3):
        o = i * 4
        d.line([x1 - size + o, y1 - 3, x1 - 3, y1 - size + o], fill=INK, width=1)


def dashed_rect(d, box, dash=9, gap=6, width=2, color=INK):
    """The edit-mode frame a held widget draws around itself."""
    x0, y0, x1, y1 = box
    x = x0
    while x < x1:
        d.line([x, y0, min(x + dash, x1), y0], fill=color, width=width)
        d.line([x, y1, min(x + dash, x1), y1], fill=color, width=width)
        x += dash + gap
    y = y0
    while y < y1:
        d.line([x0, y, x0, min(y + dash, y1)], fill=color, width=width)
        d.line([x1, y, x1, min(y + dash, y1)], fill=color, width=width)
        y += dash + gap


def edit_button(d, x, y, label, width=None, active=False):
    """A RetroButton/RetroIconButton in the widget edit chrome."""
    tw = d.textlength(label, font=mono_tiny)
    bw = width if width else tw + 16
    d.rectangle([x, y, x + bw, y + 24], fill=INK if active else PAPER, outline=INK, width=2)
    d.text((x + (bw - tw) / 2, y + 5), label, font=mono_tiny, fill=PAPER if active else INK)
    return x + bw


def widget_edit_chrome(d, box, name, size_text):
    """What a widget looks like while it is being edited: dashed frame, name chip, toolbar.

    Mirrors ui/desktop/WidgetFrame.kt (WidgetSlot + WidgetToolbar).
    """
    x0, y0, x1, y1 = box
    dashed_rect(d, box)
    chip = f"{name} {size_text}"
    cw = d.textlength(chip, font=mono_tiny)
    d.rectangle([x0 - 4, y0 - 26, x0 - 4 + cw + 14, y0 - 4], fill=INK)
    d.text((x0 + 2, y0 - 22), chip, font=mono_tiny, fill=PAPER)
    bx = x1 - 4
    for label, w in (("remove", 64), ("fit", 40), ("v", 22), ("^", 22)):
        bx -= w + 5
        edit_button(d, bx, y1 + 8, label, width=w)
    return y1 + 40


def add_widget_panel(d, box, marks=None):
    """The widget panel of the edit mode (DesktopWidgets / AddWidgetBar)."""
    x0, y0, x1, y1 = box
    if marks is None:
        marks = [True, False, True, False, False, False, False, False]
    d.rectangle([x0, y0, x1, y1], fill=PAPER, outline=INK, width=2)
    d.text((x0 + 8, y0 + 8), "widgets", font=mono_bold, fill=INK)
    names = ("chrono *", "calendar", "sysinfo *", "notes", "agenda", "tasks", "launcher", "power")
    for i, name in enumerate(names):
        bx = x0 + 8 + (i % 2) * ((x1 - x0 - 24) / 2 + 8)
        by = y0 + 30 + (i // 2) * 28
        bw = (x1 - x0 - 24) / 2
        edit_button(d, bx, by, name, width=bw, active=marks[i] if i < len(marks) else False)
    bx = x0 + 8
    for label, w in (("done", 54), ("reset sizes", 92), ("reset layout", 96)):
        bx = edit_button(d, bx, y0 + 146, label, width=w) + 6


def calendar_widget(d, box, mirror=False):
    """Compact calendar: month grid, dots on days with entries, a few agenda lines.

    Mirrors the simplified ui/desktop/CalendarWidget.kt: no frames around the day cells, no agenda
    editor and no footer bar — month, grid, one line about the selected day, up to three entries.
    """
    x0, y0, x1, y1 = box
    d.rectangle([x0, y0, x1, y1], fill=PAPER, outline=INK, width=2)
    # tiny pixel-art calendar glyph, then the month name (ASCII only: the bundled font has no blocks)
    d.rectangle([x0 + 8, y0 + 5, x0 + 22, y0 + 21], fill=PAPER, outline=INK, width=1)
    d.rectangle([x0 + 8, y0 + 5, x0 + 22, y0 + 10], fill=INK)
    for gx in range(2):
        for gy in range(2):
            d.rectangle([x0 + 12 + gx * 5, y0 + 12 + gy * 5, x0 + 14 + gx * 5, y0 + 14 + gy * 5], fill=INK)
    d.text((x0 + 28, y0 + 5), "october 2026", font=mono_tiny, fill=INK)
    for i, ch in enumerate("< >"):
        d.rectangle([x1 - 66 + i * 20, y0 + 4, x1 - 50 + i * 20, y0 + 22], fill=PAPER, outline=INK, width=1)
        d.text((x1 - 62 + i * 20, y0 + 5), ch, font=mono_tiny, fill=INK)
    d.rectangle([x1 - 26, y0 + 4, x1 - 8, y0 + 22], fill=INK)
    d.text((x1 - 21, y0 + 5), "+", font=mono_tiny, fill=PAPER)
    d.line([x0, y0 + 26, x1, y0 + 26], fill=INK, width=2)

    grid_top = y0 + 33
    cw = (x1 - x0 - 16) / 7
    for i, head in enumerate(("mo", "tu", "we", "th", "fr", "sa", "su")):
        d.text((x0 + 8 + i * cw, grid_top), head, font=mono_tiny, fill=INK)
    # October 2026 starts on a Thursday; show 12 Oct .. 1 Nov plus the selected day
    days = [None] * 3 + [d for d in range(1, 32)] + [None]
    highlighted = {0: {3: "evt", 6: "task"}, 1: {0: "birthday", 4: "evt"}}
    for i, day in enumerate(days[:42]):
        col, row = i % 7, i // 7
        cx = x0 + 8 + col * cw + cw / 2
        cy = grid_top + 15 + row * 19
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

    agenda_top = grid_top + 15 + 6 * 19 + 4
    d.line([x0 + 1, agenda_top - 4, x1 - 1, agenda_top - 4], fill=INK, width=2)
    day_label = "wed, 14 oct 2026"
    d.text((x0 + 8, agenda_top + 2), day_label, font=mono_tiny, fill=INK)
    summary = "3 entries \u00b7 1 task"
    sx = x0 + 8 + d.textlength(day_label, font=mono_tiny) + 10
    if sx + d.textlength(summary, font=mono_tiny) < x1 - 8:
        d.text((sx, agenda_top + 2), summary, font=mono_tiny, fill=INK)
    rows = [("14:00", "widget resize demo"), ("16:30", "flash v1.13.0 build"), ("--:--", "retro desk day *")]
    for i, (time, title) in enumerate(rows):
        ty = agenda_top + 20 + i * 19
        if ty + 16 > y1 - 6:
            break
        d.text((x0 + 8, ty + 1), time, font=mono_tiny, fill=INK)
        d.text((x0 + 48, ty + 1), title, font=mono_tiny, fill=INK)
    widget_grip(d, x1 - 3, y1 - 3, size=12)


def agenda_widget(d, box):
    """The agenda widget: the next two weeks, one line per entry (ui/desktop/WidgetsExtra.kt)."""
    x0, y0, x1, y1 = box
    d.rectangle([x0, y0, x1, y1], fill=PAPER, outline=INK, width=2)
    d.rectangle([x0 + 1, y0 + 1, x1 - 1, y0 + 26], fill=PATTERN)
    d.text((x0 + 8, y0 + 5), "agenda", font=mono_tiny, fill=INK)
    d.line([x0, y0 + 27, x1, y0 + 27], fill=INK, width=2)
    rows = [("today", "13", "flash build", "14:00"), ("th", "15", "standup", "09:30"),
            ("fr", "16", "retro desk day", "")]
    for i, (dow, day, title, time) in enumerate(rows):
        ty = y0 + 34 + i * 19
        d.text((x0 + 8, ty), dow, font=mono_tiny, fill=INK)
        d.text((x0 + 62, ty), day, font=mono_tiny, fill=INK)
        d.text((x0 + 84, ty), title, font=mono_tiny, fill=INK)
        if time:
            d.text((x1 - 46, ty), time, font=mono_tiny, fill=INK)
    widget_grip(d, x1 - 3, y1 - 3, size=12)


def panel(caption, style):
    w, h = 780, 1060
    img = Image.new("RGB", (w, h), PAPER)
    d = ImageDraw.Draw(img)

    # wallpaper
    if style == "linux":
        grid(img)
    elif style == "macos":
        halftone(img)
    elif style == "apps":
        # One app, one screen: the drawer IS the screen and the launcher draws the frame around it
        # (ui/desktop/ShellHost.kt). Third-party apps get the very same rails from the chrome overlay.
        c0, c1, c2, c3 = shell_frame(d, (8, 8, w - 8, h - 54), "applications")
        d.rectangle([c0 + 16, c1 + 16, c2 - 16, c1 + 50], fill=PAPER, outline=INK, width=2)
        d.text((c0 + 26, c1 + 26), "search applications…", font=mono_tiny, fill=PATTERN)
        d.text((c2 - 112, c1 + 26), "[ 24 / 96 ]", font=mono_tiny, fill=INK)
        ly = c1 + 70
        d.text((c0 + 18, ly), "most used", font=mono_tiny, fill=PATTERN)
        d.line([c0 + 96, ly + 8, c2 - 18, ly + 8], fill=PATTERN, width=1)
        ly += 26
        for name, pkg, uses in [
            ("Files", "com.android.documentsui", 42),
            ("Browser", "com.huawei.browser", 31),
            ("Notes", "com.huawei.notepad", 17),
            ("Terminal", "dev.retrobit.desktop", 12),
        ]:
            pix_app_dot(d, c0 + 22, ly, 12)
            d.text((c0 + 44, ly - 2), name, font=mono_tiny, fill=INK)
            d.text((c0 + 44, ly + 13), pkg, font=mono_tiny, fill=PATTERN)
            d.text((c2 - 66, ly + 4), "x" + str(uses), font=mono_tiny, fill=INK)
            ly += 36
        d.text((c0 + 18, ly + 4), "all apps, a-z", font=mono_tiny, fill=PATTERN)
        d.line([c0 + 106, ly + 12, c2 - 18, ly + 12], fill=PATTERN, width=1)
        ly += 32
        for name, pkg in [
            ("Camera", "com.huawei.camera"),
            ("Clock", "com.android.deskclock"),
            ("Gallery", "com.android.gallery3d"),
            ("Mail", "com.huawei.email"),
            ("Music", "com.android.music"),
            ("Settings", "com.android.settings"),
        ]:
            pix_app_dot(d, c0 + 22, ly, 12)
            d.text((c0 + 44, ly - 2), name, font=mono_tiny, fill=INK)
            d.text((c0 + 44, ly + 13), pkg, font=mono_tiny, fill=PATTERN)
            ly += 36
        d.text((c0 + 18, ly + 8), "tap selects · double tap opens · hold for the menu", font=mono_tiny, fill=INK)
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
        # right-hand cluster is laid out first so the menu run knows where to stop
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
        rx = pager(d, rx - (3 * 27 + 12), 9, size=22, cells=3, active=0)
        chip = "[LINUX]"
        cw = d.textlength(chip, font=mono_tiny)
        d.rectangle([rx - cw - 12, 9, rx - 4, 35], fill=PAPER, outline=INK, width=2)
        d.text((rx - cw - 8, 12), chip, font=mono_tiny, fill=INK)
        limit = rx - cw - 24

        pix_diamond(d, 14, 10, cell=5)
        # the panel menu: no task list - an app fills the screen, so there is nothing to list here
        text_run(d, 48, 13, ["retrOS", "system", "apps", "term"], mono, gap=14)
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
            # one icon is selected (tap selects, double tap opens): ink plate + inverted name chip
            glyph_tile(d, 100 + (i % 4) * 96, 70, 62, kind=kind, label=label, shape="flat",
                       selected=label == "Terminal")
        for i, (kind, label) in enumerate([("monitor", "Clock"), ("grid", "Notes")]):
            glyph_tile(d, 100 + i * 96, 190, 62, kind=kind, label=label, shape="flat")
        # widgets
        d.rectangle([w - 250, 60, w - 20, 210], fill=PAPER, outline=INK, width=2)
        d.rectangle([w - 250, 60, w - 20, 86], fill=PATTERN)
        d.text((w - 240, 64), "chrono", font=mono_tiny, fill=INK)
        d.text((w - 240, 96), "13:37:09", font=px_font, fill=INK)
        d.text((w - 240, 150), "Mon, 13 Oct 2026", font=mono, fill=INK)
        d.text((w - 240, 176), "uptime 2d 4h 12m", font=mono_tiny, fill=INK)
        widget_grip(d, w - 23, 207)
        agenda_widget(d, (w - 250, 228, w - 20, 316))
        # widget edit mode: the user held the calendar, so it is framed, named and re-sizable
        calendar_widget(d, (500, 340, w - 20, 660))
        widget_edit_chrome(d, (500, 340, w - 20, 660), "calendar", "280x300")
        add_widget_panel(d, (110, 300, 470, 700))
        cursor(d, 168, 660, cell=3)
    elif style == "macos":
        d.rectangle([0, 0, w, 40], fill=PAPER)
        d.line([0, 40, w, 40], fill=INK, width=2)
        pix_diamond(d, 14, 8, cell=5)
        text_run(d, 48, 11, ["File", "Edit", "View", "Help"], mono_med, gap=22)
        clock_w = d.textlength("13:37", font=px_small) + d.textlength(":09", font=mono_med)
        sec_w = d.textlength(":09", font=mono_med)
        d.text((w - 14 - d.textlength("13:37", font=px_small) - sec_w - 2, 4), "13:37", font=px_small, fill=INK)
        d.text((w - 16 - sec_w, 16), ":09", font=mono_med, fill=INK)
        rx = w - 24 - clock_w
        rx = pager(d, rx - (3 * 25 + 12), 8, size=20, cells=3, active=0)
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
        d.text((40, 106), "13:37:09", font=px_font, fill=INK)
        d.text((40, 160), "Mon, 13 Oct 2026", font=mono, fill=INK)
        widget_grip(d, 257, 217)
        calendar_widget(d, (28, 240, 244, 640))
        agenda_widget(d, (262, 240, w - 60, 390))
        # notes widget
        d.rectangle([262, 410, w - 60, 640], fill=PAPER, outline=INK, width=2)
        d.rectangle([262, 410, w - 60, 436], fill=PATTERN)
        d.text((272, 414), "notes", font=mono_tiny, fill=INK)
        for i, line in enumerate(["tap & hold any icon", "for its options", "", "type `help` in the", "terminal for the list"]):
            d.text((272, 452 + i * 26), line, font=mono, fill=INK)
        widget_grip(d, w - 63, 637)
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
        widget_grip(d, w - 23, 247)
        d.rectangle([w - 240, 166, w - 30, 180], fill=PAPER, outline=INK, width=2)
        d.rectangle([w - 238, 168, w - 100, 178], fill=INK)
        calendar_widget(d, (w - 250, 290, w - 20, 680))
        # the desktop is icons and widgets only: the drawer is a full-screen app now (see the
        # APPS panel), so nothing floats on top of the wallpaper here
        # taskbar (kept clear of the caption band at the bottom)
        tb = h - 46 - 62
        d.rectangle([0, tb, w, h], fill=PAPER)
        bevel(d, [0, tb, w - 1, h - 1])
        d.rectangle([8, tb + 8, 130, tb + 52], fill=PAPER, outline=INK, width=2)
        pix_diamond(d, 16, tb + 20, cell=4)
        d.text((48, tb + 20), "Start", font=mono_bold, fill=INK)
        px_ = pager(d, 146, tb + 7, size=30, cells=3, active=0)
        chip_x = px_ + 3 * 34 + 12
        d.rectangle([chip_x, tb + 11, chip_x + 84, tb + 47], fill=PAPER, outline=INK, width=2)
        d.text((chip_x + 8, tb + 18), "[WIN]", font=mono_tiny, fill=INK)
        tray = "WIFI 92%  13:37:09"
        tray_w = d.textlength(tray, font=mono_tiny)
        d.rectangle([w - tray_w - 60, tb + 10, w - 8, tb + 50], fill=PATTERN)
        d.rectangle([w - tray_w - 52, tb + 16, w - tray_w - 26, tb + 42], fill=PAPER, outline=INK, width=2)
        d.text((w - tray_w - 46, tb + 18), "KB", font=mono_tiny, fill=INK)
        d.text((w - tray_w - 16, tb + 18), tray, font=mono_tiny, fill=INK)

    # caption
    d.rectangle([0, h - 46, w, h], fill=INK)
    d.text((16, h - 40), caption, font=mono_bold, fill=PAPER)
    return img


def main():
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    # the five e-paper panels on the first row, the three native desktops on the second
    epaper = [
        panel("LINUX · retrOS GNU/Linux — desktop of icons & widgets, one icon selected (double tap opens), widget edit mode", "linux"),
        panel("macOS · Retro System 9 — menu bar, widgets, dock (an app fills the screen, never a floating window)", "macos"),
        panel("WINDOWS · Retrodows 95 — desktop icons & widgets, start menu, workspace switcher, taskbar, tray", "windows"),
        panel("APPS · third-party apps fill the screen — the drawer in the launcher's frame; an installed app gets the same title rail & dock from the chrome overlay", "apps"),
        float_panel(),
    ]
    native = [
        native_desktop("win95"),
        native_desktop("win11"),
        native_desktop("aqua"),
        native_desktop("ubuntu"),
    ]
    gap, margin = 40, 40
    ph = epaper[0].height
    w = max(sum(p.width for p in epaper) + gap * (len(epaper) + 1),
            sum(p.width for p in native) + gap * (len(native) + 1))
    row2 = 136 + ph + 56
    strip_y = row2 + ph + 40
    h = strip_y + 200 + 150
    sheet = Image.new("RGB", (w, h), SHEET_BG)
    d = ImageDraw.Draw(sheet)
    big = ImageFont.truetype(os.path.join(FONT_DIR, "pixel_operator_bold.ttf"), 80)
    d.text((margin, 20), "RETROBIT DESKTOP", font=big, fill=PAPER)
    d.text((margin + 2, 92), "e-paper launcher for android tablets · pixel operator typeface (cc0) · tap selects, double tap opens, hold for the menu · widgets (chrono, calendar, agenda, tasks, launcher, power, ...) you add, remove and hold to resize · hold the pen for right click · floating windows for our own apps (drag the title bar, resize from eight edges, minimize / maximize + restore / close, the size is remembered) · 12 palettes: 6 e-paper tones + 6 native desktop themes that paint their own real chrome (rounded window corners, rounded menus, soft shadows) · third-party apps fill the screen, framed by the cyberdeck chrome (border + title rail + dock) · keyboard floats over and moves nothing · safe area for the punch-hole camera",
           font=mono, fill=(0xC9, 0xC5, 0xB8))
    x = margin
    for p in epaper:
        sheet.paste(p, (x, 136))
        x += p.width + gap
    d.text((margin, row2 - 30), "NATIVE THEMES (4 of the 6) — not an e-paper tone of a desktop: the real chrome, in colour. Windows 95 keeps its silver face, "
                                "navy > blue title gradient, beveled boxes and teal desk; Aqua its snow chrome, traffic lights and grey title gradient; "
                                "Ubuntu its dark headerbar, orange accent and aubergine desk, Windows 11 its flat Mica chrome and 8 px corners. "
                                "Rounded corners, real shadows, no dither, no scanlines. (Windows 11 and Adwaita are the dark flat pair, Aqua dark is the graphite macOS.)",
           font=mono, fill=(0xC9, 0xC5, 0xB8))
    row_w = sum(p.width for p in native) + gap * (len(native) - 1)
    x = (w - row_w) // 2          # centre the native row under the five e-paper panels
    for p in native:
        sheet.paste(p, (x, row2))
        x += p.width + gap

    # ── the palette row: the 6 e-paper tones, then the 6 native desktops ──
    d.text((margin, strip_y - 6), "PALETTES — every one drawn with the values the code uses: left six are e-paper (paper, ink, accent); "
                                  "right six are native desktop skins (desk gradient behind a window with that desktop's own title bar and buttons)",
           font=mono, fill=(0xC9, 0xC5, 0xB8))
    y = strip_y + 34
    chip_w = (w - 2 * margin - 6 * 11) / 12
    for i, pal in enumerate(PALETTES):
        palette_chip(d, margin + i * (chip_w + 6), y, chip_w, 150, pal)

    # ── the icon shapes: the tile every theme draws around an app icon ──
    icon_y = y + 150 + 40
    d.text((margin, icon_y - 24), "ICON SHAPES — the tile each theme draws around an app icon: Windows 95 the beveled square with its navy trim, "
                                  "Windows 11 the soft rounded square, Aqua the glossy tile with a pale edge, Ubuntu the rounded square with the orange trim, "
                                  "GNOME the circle; the e-paper palettes print a 1-bit tile and take the shape from the desktop style",
           font=mono, fill=(0xC9, 0xC5, 0xB8))
    tile = 54
    cell = tile + 20 + 34
    x = margin + 4
    for label, shape, paper, ink, accent, desk in ICON_SHAPES:
        icon_shape_tile(d, x, icon_y, tile, shape, paper, ink, accent, ink, desk, label)
        x += cell
    sheet.save(OUT)
    print("wrote", OUT, sheet.size)


if __name__ == "__main__":
    main()
