#!/usr/bin/env python3
"""Dessine la feuille de textures des interfaces (textures/gui/ledger.png, 256×256) : cadre en bois et laiton,
parchemin, cartes, onglets, boutons, emplacements, barres, médaillons, sceau, icônes, rose des vents, épingles.
Les coordonnées doivent rester celles de client/screen/UiKit.java.

Usage : python3 tools/gui.py [--preview]   (--preview : maquette de l'interface dans /tmp/ledger_preview.png)
"""
import math
import os
import random
import sys

from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'chroniclecaravans', 'textures', 'gui', 'ledger.png')

rnd = random.Random(1234)

WOOD_OUT = (30, 19, 11)
WOOD_D = (62, 40, 24)
WOOD_M = (94, 63, 38)
WOOD_L = (124, 86, 53)
WOOD_HL = (152, 110, 70)
BRASS_D = (112, 80, 30)
BRASS_M = (178, 136, 58)
BRASS_L = (236, 202, 118)
PAPER = (234, 218, 180)
INK = (59, 42, 26)


def clamp(v):
    return max(0, min(255, int(round(v))))


def mul(c, k, a=255):
    return (clamp(c[0] * k), clamp(c[1] * k), clamp(c[2] * k), a)


def lerp(a, b, t):
    return tuple(clamp(a[i] + (b[i] - a[i]) * t) for i in range(3)) + (255,)


img = Image.new('RGBA', (256, 256), (0, 0, 0, 0))
px = img.load()


def put(x, y, c):
    if len(c) == 3:
        c = c + (255,)
    px[x, y] = c


def periodic_noise(w, h, cells, seed):
    """Value noise that tiles (w×h), with `cells` lattice cells per side."""
    r = random.Random(seed)
    grid = [[r.random() for _ in range(cells)] for _ in range(cells)]

    def smooth(t):
        return t * t * (3 - 2 * t)

    out = [[0.0] * w for _ in range(h)]
    for y in range(h):
        for x in range(w):
            gx, gy = x * cells / w, y * cells / h
            x0, y0 = int(gx), int(gy)
            tx, ty = smooth(gx - x0), smooth(gy - y0)
            a = grid[y0 % cells][x0 % cells]
            b = grid[y0 % cells][(x0 + 1) % cells]
            c = grid[(y0 + 1) % cells][x0 % cells]
            d = grid[(y0 + 1) % cells][(x0 + 1) % cells]
            out[y][x] = (a * (1 - tx) + b * tx) * (1 - ty) + (c * (1 - tx) + d * tx) * ty
    return out


# ------------------------------------------------------------------ parchment tile (64,0,64,64)
def paper_tile(ox, oy):
    n1 = periodic_noise(64, 64, 4, 7)
    n2 = periodic_noise(64, 64, 16, 8)
    for y in range(64):
        for x in range(64):
            k = 0.955 + 0.05 * n1[y][x] + 0.03 * n2[y][x] + rnd.uniform(-0.012, 0.012)
            c = mul(PAPER, k)
            put(ox + x, oy + y, c)
    # A few fibres and specks
    for _ in range(22):
        x, y = rnd.randrange(64), rnd.randrange(64)
        length = rnd.randint(2, 5)
        horizontal = rnd.random() < 0.7
        for i in range(length):
            xx, yy = (x + (i if horizontal else 0)) % 64, (y + (0 if horizontal else i)) % 64
            r, g, b, a = px[ox + xx, oy + yy]
            put(ox + xx, oy + yy, mul((r, g, b), 0.95))
    for _ in range(10):
        x, y = rnd.randrange(64), rnd.randrange(64)
        r, g, b, a = px[ox + x, oy + y]
        put(ox + x, oy + y, mul((r, g, b), 0.88))


paper_tile(64, 0)


# ------------------------------------------------------------------ wooden frame (0,0,48,48), border 12
def wood_frame(ox, oy, size=48, border=12):
    grain = periodic_noise(size, size, 6, 11)
    for y in range(size):
        for x in range(size):
            d = min(x, y, size - 1 - x, size - 1 - y)
            if d >= border:
                continue
            horizontal = min(y, size - 1 - y) < min(x, size - 1 - x)
            g = grain[y][x] if horizontal else grain[x][y]
            stripe = 0.5 + 0.5 * math.sin((x if not horizontal else y) * 0.9 + g * 6)
            base = lerp(WOOD_M, WOOD_L, 0.35 * stripe + 0.25 * g)
            if d == 0:
                c = WOOD_OUT + (255,)
            elif d == 1:
                c = mul(base, 1.18)
            elif d == border - 3:
                c = mul(base, 0.72)
            elif d == border - 2:
                c = BRASS_D + (255,)
            elif d == border - 1:
                c = mul(PAPER, 0.62)
            else:
                # bevel: top/left lighter, bottom/right darker
                light = (y < border and x > y) or (x < border and y > x)
                c = mul(base, 1.06 if (y == d or x == d) and light else 0.97)
            put(ox + x, oy + y, c)
    # Brass corner plates with rivets
    for cx, cy in ((0, 0), (size - 9, 0), (0, size - 9), (size - 9, size - 9)):
        for y in range(9):
            for x in range(9):
                edge = x in (0, 8) or y in (0, 8)
                c = BRASS_D if edge else lerp(BRASS_M, BRASS_L, (8 - x - y) / 16 + 0.3)
                put(ox + cx + x, oy + cy + y, c)
        for rx, ry in ((3, 3), (5, 5)):
            put(ox + cx + rx, oy + cy + ry, BRASS_L)
            put(ox + cx + rx + 1, oy + cy + ry, BRASS_D)


wood_frame(0, 0)


# ------------------------------------------------------------------ cards (24×24, border 4)
def card(ox, oy, fill, fill_a, border, border_a, shadow=True):
    for y in range(24):
        for x in range(24):
            d = min(x, y, 23 - x, 23 - y)
            if d == 0:
                c = border + (border_a,)
            elif d == 1 and shadow and (x == 22 or y == 22):
                c = border + (border_a // 2,)
            elif d == 1 and (x == 1 or y == 1):
                c = (255, 248, 225, 70)
            else:
                c = fill + (fill_a,)
            put(ox + x, oy + y, c)
    # rounded look: soften the corners
    for x, y in ((0, 0), (23, 0), (0, 23), (23, 23)):
        put(ox + x, oy + y, (0, 0, 0, 0))


card(128, 0, (120, 88, 52), 28, (92, 64, 36), 150)        # CARD
card(152, 0, (236, 182, 82), 70, (176, 120, 40), 230)     # CARD_HOVER
card(176, 0, (90, 80, 70), 55, (100, 88, 76), 110)        # CARD_DIM


def inset(ox, oy, w=24, h=24):
    for y in range(h):
        for x in range(w):
            d = min(x, y, w - 1 - x, h - 1 - y)
            if d == 0:
                c = (x == w - 1 or y == h - 1) and (250, 236, 200) or (70, 50, 32)
            elif d == 1:
                c = (x == w - 2 or y == h - 2) and (210, 186, 140) or (40, 28, 18)
            else:
                t = y / (h - 1)
                c = lerp((74, 56, 38), (38, 27, 18), t)[:3]
            put(ox + x, oy + y, c)


inset(200, 0)


def slot(ox, oy, interior):
    for y in range(18):
        for x in range(18):
            if x == 0 or y == 0:
                c = (96, 72, 46)
            elif x == 17 or y == 17:
                c = (250, 238, 208)
            elif x == 1 or y == 1:
                c = mul(interior, 0.86)[:3]
            else:
                c = interior
            put(ox + x, oy + y, c)
    put(ox + 17, oy, (170, 150, 120))
    put(ox, oy + 17, (170, 150, 120))


slot(224, 0, (198, 174, 130))       # SLOT
slot(224, 18, (232, 196, 112))      # SLOT_HL


# ------------------------------------------------------------------ buttons (32×18, border 4)
def button(ox, oy, top, bottom, outline, highlight):
    for y in range(18):
        for x in range(32):
            d = min(x, y, 31 - x, 17 - y)
            if d == 0:
                c = outline
            elif d == 1 and y == 1:
                c = highlight
            elif d == 1 and y == 16:
                c = mul(bottom, 0.7)[:3]
            else:
                c = lerp(top, bottom, y / 17)[:3]
                if (x * 7 + y * 3) % 11 == 0:
                    c = mul(c, 0.95)[:3]
            put(ox + x, oy + y, c)
    for x, y in ((0, 0), (31, 0), (0, 17), (31, 17)):
        put(ox + x, oy + y, (0, 0, 0, 0))


button(128, 24, (150, 104, 62), (104, 70, 40), WOOD_OUT, (196, 150, 98))   # BUTTON
button(160, 24, (186, 136, 76), (132, 92, 50), BRASS_D, (246, 212, 140))   # BUTTON_HOVER
button(192, 24, (128, 118, 106), (98, 90, 82), (60, 54, 48), (150, 142, 132))  # BUTTON_OFF


# ------------------------------------------------------------------ bars (64×8)
def bar_bg(ox, oy):
    for y in range(8):
        for x in range(64):
            d = min(x, y, 63 - x, 7 - y)
            c = (60, 42, 26) if d == 0 else (x == 63 or y == 7) and (200, 176, 130) or (44, 32, 22)
            if d >= 1:
                c = (52, 38, 26)
            put(ox + x, oy + y, c)


def bar_fill(ox, oy, light, dark):
    for y in range(8):
        for x in range(64):
            c = lerp(light, dark, y / 7)[:3]
            if y == 0:
                c = mul(light, 1.15)[:3]
            put(ox + x, oy + y, c)


bar_bg(0, 48)
bar_fill(0, 56, (120, 210, 96), (40, 120, 52))       # BAR_GREEN
bar_fill(160, 64, (248, 214, 120), (176, 120, 40))   # BAR_GOLD


# ------------------------------------------------------------------ tabs (48×22 / active 48×24, border 6)
def tab(ox, oy, h, fill_top, fill_bottom, outline, connect=False):
    for y in range(h):
        for x in range(48):
            # rounded top corners (radius 3)
            if y < 3:
                cut = 3 - y
                if x < cut or x > 47 - cut:
                    continue
            edge = x == 0 or x == 47 or y == 0 or (y < 3 and (x == 3 - y or x == 44 + y))
            if edge:
                c = outline
            elif connect and y >= h - 3:
                c = fill_bottom
            else:
                c = lerp(fill_top, fill_bottom, y / (h - 1))[:3]
                if y == 1:
                    c = mul(c, 1.12)[:3]
            put(ox + x, oy + y, c)
        if not connect:
            pass


tab(0, 64, 22, (112, 74, 44), (82, 54, 32), WOOD_OUT)                      # TAB
tab(48, 64, 22, (146, 100, 58), (108, 72, 42), BRASS_D)                    # TAB_HOVER
tab(96, 64, 24, (242, 228, 192), PAPER, WOOD_OUT, connect=True)           # TAB_ACTIVE


# ------------------------------------------------------------------ scroll bar
def scroll(ox, oy, w, h, fill, outline):
    for y in range(h):
        for x in range(w):
            d = min(x, y, w - 1 - x, h - 1 - y)
            put(ox + x, oy + y, outline if d == 0 else fill)


scroll(144, 64, 8, 32, (70, 52, 34), (40, 28, 18))
for y in range(16):
    for x in range(8):
        d = min(x, y, 7 - x, 15 - y)
        put(152 + x, 64 + y, BRASS_D if d == 0 else lerp(BRASS_L, BRASS_M, x / 7)[:3])


# ------------------------------------------------------------------ medallions (26×26)
def medal(ox, oy, ring_out, ring, inner_top, inner_bottom, alpha_inner=255):
    c0 = 12.5
    for y in range(26):
        for x in range(26):
            r = math.hypot(x - c0, y - c0)
            if r > 12.6:
                continue
            if r > 11.6:
                c = ring_out + (255,)
            elif r > 9.6:
                shade = 1.15 if (x + y) < 22 else 0.85
                c = mul(ring, shade)
            elif r > 8.8:
                c = ring_out + (255,)
            else:
                if alpha_inner == 0:
                    continue
                c = lerp(inner_top, inner_bottom, y / 25)[:3] + (alpha_inner,)
            put(ox + x, oy + y, c)


medal(0, 90, (40, 34, 30), (96, 88, 80), (70, 62, 56), (44, 38, 34))         # MEDAL_LOCKED
medal(26, 90, WOOD_OUT, BRASS_M, (246, 234, 202), (214, 192, 150))           # MEDAL_OPEN
medal(52, 90, (110, 64, 10), (240, 196, 92), (250, 226, 150), (204, 150, 60))  # MEDAL_DONE
medal(78, 90, (255, 246, 210), (255, 236, 160), (0, 0, 0), (0, 0, 0), 0)     # MEDAL_HOVER (ring only)


# ------------------------------------------------------------------ wax seal (28×28)
def seal(ox, oy):
    c0 = 13.5
    for y in range(28):
        for x in range(28):
            a = math.atan2(y - c0, x - c0)
            r = math.hypot(x - c0, y - c0)
            edge = 12.6 + 0.9 * math.sin(a * 7) + 0.4 * math.sin(a * 13)
            if r > edge:
                continue
            if r > edge - 1.2:
                c = (96, 16, 14)
            elif r > 9.5:
                c = mul((168, 34, 28), 1.1 if (x + y) < 26 else 0.85)[:3]
            elif r > 8.6:
                c = (110, 20, 16)
            else:
                c = lerp((186, 48, 38), (140, 26, 22), y / 27)[:3]
            put(ox + x, oy + y, c)
    for x, y in ((8, 7), (9, 7), (7, 8)):
        put(ox + x, oy + y, (232, 120, 104))


seal(104, 90)


# ------------------------------------------------------------------ icons (16×16 cells at y=128)
def icon(i):
    return 16 * i, 128


def draw_pixels(i, rows, colors):
    ox, oy = icon(i)
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch in colors:
                put(ox + x, oy + y, colors[ch])


GOLD = (236, 190, 70)
GOLD_D = (150, 104, 30)
GREEN = (60, 160, 70)
GREEN_D = (30, 100, 44)
RED = (196, 60, 46)
RED_D = (120, 30, 24)
INKC = INK
GREY = (130, 120, 110)
WHITE = (250, 246, 236)
BLUE = (90, 120, 190)
BROWN = (150, 104, 60)
BROWN_D = (96, 62, 34)

draw_pixels(0, [  # lock
    "...dddd...",
    "..d....d..",
    "..d....d..",
    "..d....d..",
    ".GGGGGGGG.",
    ".GgggggggG",
    ".GggdDggG.",
    ".GgggDgggG",
    ".GggggggG.",
    ".GGGGGGGG.",
], {'d': (90, 84, 78), 'G': GOLD_D, 'g': GOLD, 'D': (60, 40, 20)})
draw_pixels(1, [  # arrow right
    "..........",
    ".....k....",
    ".....kk...",
    "kkkkkkkk..",
    "kkkkkkkkk.",
    "kkkkkkkk..",
    ".....kk...",
    ".....k....",
], {'k': INKC})
draw_pixels(2, [  # trend up (good, green)
    "....g.....",
    "...ggg....",
    "..ggggg...",
    ".ggggggg..",
    "gggggggggg",
    "...ggg....",
    "...ggg....",
    "...ggg....",
], {'g': GREEN})
draw_pixels(3, [  # trend down (bad, red)
    "...rrr....",
    "...rrr....",
    "...rrr....",
    "rrrrrrrrrr",
    ".rrrrrrr..",
    "..rrrrr...",
    "...rrr....",
    "....r.....",
], {'r': RED})
draw_pixels(4, [  # star
    "....y.....",
    "....y.....",
    "...yyy....",
    "yyyyyyyyy.",
    ".yyyyyyy..",
    "..yyyyy...",
    "..yy.yy...",
    ".yy...yy..",
], {'y': GOLD})
draw_pixels(5, [  # clock
    "..kkkkk...",
    ".kwwwwwk..",
    "kwwwkwwwk.",
    "kwwwkwwwk.",
    "kwwwkkkwk.",
    "kwwwwwwwk.",
    ".kwwwwwk..",
    "..kkkkk...",
], {'k': INKC, 'w': WHITE})
draw_pixels(6, [  # parcel
    "..........",
    ".bbbbsbbb.",
    ".bBBBsBBb.",
    ".sssssssss",
    ".bBBBsBBb.",
    ".bBBBsBBb.",
    ".bbbbsbbb.",
    "..........",
], {'b': BROWN_D, 'B': BROWN, 's': (230, 220, 190)})
draw_pixels(7, [  # moon
    "...yyy....",
    "..yy......",
    ".yy.......",
    ".yy.......",
    ".yy.......",
    ".yyy...y..",
    "..yyyyyy..",
    "...yyyy...",
], {'y': (230, 220, 150)})
draw_pixels(8, [  # storm
    "..cccc....",
    ".cccccccc.",
    "cccccccccc",
    ".cccccccc.",
    "....yy....",
    "...yy.....",
    "....yy....",
    "...y......",
], {'c': (120, 124, 140), 'y': (250, 220, 80)})
draw_pixels(9, [  # fire
    "....r.....",
    "...rr.....",
    "...ror....",
    "..roor.r..",
    "..rooorr..",
    ".roooyorr.",
    ".rooyyyor.",
    "..bbbbbb..",
], {'r': (210, 70, 30), 'o': (240, 150, 40), 'y': (255, 230, 120), 'b': BROWN_D})
draw_pixels(10, [  # check
    ".........g",
    "........gg",
    ".......gg.",
    "g.....gg..",
    "gg...gg...",
    ".gg.gg....",
    "..ggg.....",
    "...g......",
], {'g': GREEN})
draw_pixels(11, [  # cross
    "r......r..",
    "rr....rr..",
    ".rr..rr...",
    "..rrrr....",
    "..rrrr....",
    ".rr..rr...",
    "rr....rr..",
    "r......r..",
], {'r': RED})
draw_pixels(12, [  # guild coin
    "..gggggg..",
    ".gyyyyyyg.",
    "gyyGGGGyyg",
    "gyGyyyyGyg",
    "gyGyyyyGyg",
    "gyyGGGGyyg",
    ".gyyyyyyg.",
    "..gggggg..",
], {'g': GOLD_D, 'y': GOLD, 'G': (200, 150, 50)})
draw_pixels(13, [  # road
    "..p....p..",
    "..p.d..p..",
    ".p..d...p.",
    ".p......p.",
    ".p..d...p.",
    "p...d....p",
    "p.........",
    "p...d....p",
], {'p': BROWN_D, 'd': (200, 170, 90)})
draw_pixels(14, [  # shield
    "bbbbbbbbb.",
    "bGGGGrrrb.",
    "bGGGGrrrb.",
    "bGGGGrrrb.",
    "brrrrGGGb.",
    ".brrrGGb..",
    "..brrGb...",
    "...bbb....",
], {'b': (60, 50, 40), 'G': (200, 200, 210), 'r': (160, 50, 40)})
draw_pixels(15, [  # scales
    "....k.....",
    "kkkkkkkkk.",
    "k...k...k.",
    "k...k...k.",
    "kk..k..kk.",
    "....k.....",
    "....k.....",
    "..kkkkk...",
], {'k': INKC})


# ------------------------------------------------------------------ compass rose (0,144,48,48)
def compass(ox, oy):
    c0 = 23.5
    d = ImageDraw.Draw(img)
    # Outer rings
    for y in range(48):
        for x in range(48):
            r = math.hypot(x - c0, y - c0)
            if 18.5 < r < 19.6 or 15.2 < r < 15.9:
                put(ox + x, oy + y, INK + (200,))
    # Main points (N S E W), filled half dark, half light
    pts = []
    for k in range(4):
        a = k * math.pi / 2 - math.pi / 2
        tip = (ox + c0 + math.cos(a) * 22, oy + c0 + math.sin(a) * 22)
        left = (ox + c0 + math.cos(a - math.pi / 2) * 3.6, oy + c0 + math.sin(a - math.pi / 2) * 3.6)
        right = (ox + c0 + math.cos(a + math.pi / 2) * 3.6, oy + c0 + math.sin(a + math.pi / 2) * 3.6)
        center = (ox + c0, oy + c0)
        d.polygon([tip, left, center], fill=(150, 40, 30, 235) if k == 0 else INK + (225,))
        d.polygon([tip, right, center], fill=(226, 196, 150, 235))
        d.line([tip, left, center, right, tip], fill=INK + (240,))
    for k in range(4):
        a = k * math.pi / 2 - math.pi / 4
        tip = (ox + c0 + math.cos(a) * 13, oy + c0 + math.sin(a) * 13)
        left = (ox + c0 + math.cos(a - math.pi / 2) * 2.5, oy + c0 + math.sin(a - math.pi / 2) * 2.5)
        right = (ox + c0 + math.cos(a + math.pi / 2) * 2.5, oy + c0 + math.sin(a + math.pi / 2) * 2.5)
        d.polygon([tip, left, (ox + c0, oy + c0), right], fill=(120, 94, 60, 220))
    put(ox + 23, oy + 23, BRASS_L)
    put(ox + 24, oy + 24, BRASS_D)


compass(0, 144)


# ------------------------------------------------------------------ pins (10×14 at y=144, x=48..)
def pin(ox, oy, color):
    c0x, c0y = 4.5, 4.5
    for y in range(14):
        for x in range(10):
            r = math.hypot(x - c0x, y - c0y)
            if r <= 4.4:
                c = mul(color, 1.25 if (x + y) < 6 else (1.0 if r < 3.4 else 0.75))
                if r > 3.6:
                    c = mul(color, 0.55)
                put(ox + x, oy + y, c)
    for y in range(9, 14):
        put(ox + 4, oy + y, (60, 60, 64))
        put(ox + 5, oy + y, (110, 110, 118))
    put(ox + 3, oy + 3, (255, 255, 255))


pin(48, 144, (200, 50, 40))
pin(58, 144, (230, 180, 60))
pin(68, 144, (70, 110, 200))
pin(78, 144, (60, 160, 70))

os.makedirs(os.path.dirname(OUT), exist_ok=True)
img.save(OUT)
print('ok', OUT)


# ------------------------------------------------------------------ preview (approximation of UiKit)
def nine(dst, sx, sy, sw, sh, b, x, y, w, h):
    src = img.crop((sx, sy, sx + sw, sy + sh))

    def tile(part, tx, ty, tw, th):
        pw, ph = part.size
        if pw == 0 or ph == 0 or tw <= 0 or th <= 0:
            return
        for yy in range(ty, ty + th, ph):
            for xx in range(tx, tx + tw, pw):
                cw, ch = min(pw, tx + tw - xx), min(ph, ty + th - yy)
                piece = part.crop((0, 0, cw, ch))
                dst.alpha_composite(piece, (xx, yy))
    mw, mh = sw - 2 * b, sh - 2 * b
    tile(src.crop((0, 0, b, b)), x, y, b, b)
    tile(src.crop((sw - b, 0, sw, b)), x + w - b, y, b, b)
    tile(src.crop((0, sh - b, b, sh)), x, y + h - b, b, b)
    tile(src.crop((sw - b, sh - b, sw, sh)), x + w - b, y + h - b, b, b)
    tile(src.crop((b, 0, sw - b, b)), x + b, y, w - 2 * b, b)
    tile(src.crop((b, sh - b, sw - b, sh)), x + b, y + h - b, w - 2 * b, b)
    tile(src.crop((0, b, b, sh - b)), x, y + b, b, h - 2 * b)
    tile(src.crop((sw - b, b, sw, sh - b)), x + w - b, y + b, b, h - 2 * b)
    if mw > 0 and mh > 0:
        tile(src.crop((b, b, sw - b, sh - b)), x + b, y + b, w - 2 * b, h - 2 * b)


if '--preview' in sys.argv:
    W, H = 352, 220
    canvas = Image.new('RGBA', (W + 40, H + 60), (40, 40, 40, 255))
    x0, y0 = 20, 40
    # tabs
    for i in range(5):
        tx = x0 + 14 + i * 64
        if i == 0:
            continue
        nine(canvas, 0 if i != 2 else 48, 64, 48, 22, 6, tx, y0 - 18, 62, 22)
    nine(canvas, 64, 0, 64, 64, 0, x0 + 6, y0 + 6, W - 12, H - 12)
    nine(canvas, 0, 0, 48, 48, 12, x0, y0, W, H)
    nine(canvas, 96, 64, 48, 24, 6, x0 + 14, y0 - 18, 62, 24)
    nine(canvas, 200, 0, 24, 24, 4, x0 + 14, y0 + 14, 38, 44)
    for r in range(5):
        nine(canvas, 128 if r != 1 else 152, 0, 24, 24, 4, x0 + 14, y0 + 68 + r * 26, W - 36, 24)
        canvas.alpha_composite(img.crop((224, 0, 242, 18)), (x0 + 17, y0 + 71 + r * 26))
    canvas.alpha_composite(img.crop((104, 90, 132, 118)), (x0 + W - 46, y0 + 16))
    for k in range(4):
        canvas.alpha_composite(img.crop((26 * k, 90, 26 * k + 26, 116)), (x0 + 60 + 34 * k, y0 + 190))
    big = canvas.resize((canvas.width * 3, canvas.height * 3), Image.NEAREST)
    big.save('/tmp/ledger_preview.png')
    img.resize((768, 768), Image.NEAREST).save('/tmp/ledger_sheet.png')
    print('preview /tmp/ledger_preview.png')
