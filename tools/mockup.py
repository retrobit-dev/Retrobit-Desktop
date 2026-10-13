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
    panels = [
        panel("LINUX · retrOS GNU/Linux — desktop of icons & widgets, one icon selected (double tap opens), widget edit mode", "linux"),
        panel("macOS · Retro System 9 — menu bar, widgets, dock (an app fills the screen, never a floating window)", "macos"),
        panel("WINDOWS · Retrodows 95 — desktop icons & widgets, start menu, workspace switcher, taskbar, tray", "windows"),
        panel("APPS · third-party apps fill the screen — the drawer in the launcher's frame; an installed app gets the same title rail & dock from the chrome overlay", "apps"),
        float_panel(),
    ]
    w = sum(p.width for p in panels) + 40 * (len(panels) + 1)
    h = panels[0].height + 180
    sheet = Image.new("RGB", (w, h), (0x2A, 0x2A, 0x2A))
    d = ImageDraw.Draw(sheet)
    big = ImageFont.truetype(os.path.join(FONT_DIR, "pixel_operator_bold.ttf"), 80)
    d.text((40, 20), "RETROBIT DESKTOP", font=big, fill=PAPER)
    d.text((42, 92), "e-paper launcher for android tablets · pixel operator typeface (cc0) · tap selects, double tap opens, hold for the menu · widgets (chrono, calendar, agenda, tasks, launcher, power, ...) you add, remove and hold to resize · hold the pen for right click · floating windows for our own apps (drag the title bar, resize from eight edges, minimize / maximize + restore / close, the size is remembered) · third-party apps fill the screen, framed by the cyberdeck chrome (border + title rail + dock) · keyboard floats over and moves nothing · safe area for the punch-hole camera",
           font=mono, fill=(0xC9, 0xC5, 0xB8))
    x = 40
    for p in panels:
        sheet.paste(p, (x, 136))
        x += p.width + 40
    sheet.save(OUT)
    print("wrote", OUT, sheet.size)


if __name__ == "__main__":
    main()
