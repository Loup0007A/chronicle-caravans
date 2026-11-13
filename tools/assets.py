#!/usr/bin/env python3
"""Dessine les textures des nouveautés 1.1 à 1.3 : enseigne d'auberge, atlas, peaux des gardes et des bâtisseurs,
quai de chargement, comptoir de commerce, colis, charte de relais ; écrit leurs modèles et états de blocs
(dont le poteau d'attelage et les niveaux de la borne).
Usage : python3 tools/assets.py [--preview]  (--preview : planche agrandie dans /tmp/assets_preview.png)"""
import json
import os
import random

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'chroniclecaravans')


def save(img, *path):
    p = os.path.join(ASSETS, *path)
    os.makedirs(os.path.dirname(p), exist_ok=True)
    img.save(p)


def jsave(obj, *path):
    p = os.path.join(ASSETS, *path)
    os.makedirs(os.path.dirname(p), exist_ok=True)
    with open(p, 'w') as f:
        json.dump(obj, f, indent=2)
        f.write('\n')


def shade(c, k):
    return tuple(max(0, min(255, int(v * k))) for v in c[:3]) + (255,)


rnd = random.Random(7)

# ------------------------------------------------------------------ inn sign board (16x16)
img = Image.new('RGBA', (16, 16))
wood = (176, 132, 82)
for y in range(16):
    for x in range(16):
        k = 0.92 + 0.12 * rnd.random() - (0.08 if y % 5 == 0 else 0)
        img.putpixel((x, y), shade(wood, k))
for i in range(16):
    for (x, y) in ((i, 0), (i, 15), (0, i), (15, i)):
        img.putpixel((x, y), shade((92, 60, 30), 1))
# A bed: white pillow, red blanket, dark frame
for x in range(3, 13):
    img.putpixel((x, 11), (70, 45, 25, 255))
for y in range(8, 12):
    img.putpixel((3, y), (70, 45, 25, 255))
    img.putpixel((12, y), (70, 45, 25, 255))
for x in range(4, 7):
    for y in range(8, 11):
        img.putpixel((x, y), (236, 236, 228, 255))
for x in range(7, 12):
    for y in range(8, 11):
        img.putpixel((x, y), (178, 38, 38, 255) if y > 8 else (210, 60, 60, 255))
# A star above (the inn's sign)
for (x, y) in ((8, 3), (7, 4), (8, 4), (9, 4), (8, 5), (6, 4), (10, 4)):
    img.putpixel((x, y), (240, 200, 70, 255))
save(img, 'textures', 'block', 'inn_sign.png')

# ------------------------------------------------------------------ atlas (16x16 item)
img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
cover = (120, 70, 36)
for y in range(1, 15):
    for x in range(2, 14):
        img.putpixel((x, y), shade(cover, 0.95 + 0.1 * rnd.random()))
for y in range(2, 14):
    for x in range(4, 13):
        img.putpixel((x, y), shade((233, 217, 176), 0.95 + 0.08 * rnd.random()))
for y in range(1, 15):
    img.putpixel((2, y), (80, 46, 20, 255))
    img.putpixel((3, y), (100, 58, 26, 255))
# A route with three stops
pts = [(5, 11), (7, 9), (8, 7), (10, 6), (11, 4)]
for (x, y) in pts:
    img.putpixel((x, y), (150, 40, 30, 255))
for (x, y) in ((6, 10), (9, 6)):
    img.putpixel((x, y), (110, 80, 40, 255))
for (x, y) in ((5, 11), (8, 7), (11, 4)):
    img.putpixel((x, y), (30, 120, 60, 255))
save(img, 'textures', 'item', 'caravan_atlas.png')

# ------------------------------------------------------------------ skins (64x64, humanoid layout)


def box(img, u, v, w, h, d, colors):
    """Paints a box of a humanoid model: colors = dict(top, bottom, front, back, side)."""
    def fill(x0, y0, ww, hh, c):
        for y in range(y0, y0 + hh):
            for x in range(x0, x0 + ww):
                cc = c(x - x0, y - y0) if callable(c) else c
                img.putpixel((x, y), cc)
    fill(u + d, v, w, d, colors['top'])
    fill(u + d + w, v, w, d, colors['bottom'])
    fill(u, v + d, d, h, colors['side'])
    fill(u + d, v + d, w, h, colors['front'])
    fill(u + d + w, v + d, d, h, colors['side'])
    fill(u + d + w + d, v + d, w, h, colors['back'])


def noisy(c, amount=0.08):
    return lambda x, y: shade(c, 1 - amount / 2 + amount * rnd.random())


def skin(name, hair, tunic, trim, pants, boots, hat=None):
    img = Image.new('RGBA', (64, 64), (0, 0, 0, 0))
    face = (214, 168, 128)
    # Head
    def head_front(x, y):
        if y < 2:
            return shade(hair, 1)
        if y == 3 and x in (1, 2, 5, 6):
            return (255, 255, 255, 255) if x in (1, 6) else (40, 50, 90, 255)
        if y == 6 and 2 <= x <= 5:
            return shade(face, 0.75)
        if y == 5 and x in (3, 4):
            return shade(face, 0.85)
        return noisy(face, 0.05)(x, y)
    def head_side(x, y):
        return shade(hair, 1) if y < 3 or (y < 6 and x > 5) else noisy(face, 0.05)(x, y)
    box(img, 0, 0, 8, 8, 8, {'top': noisy(hair), 'bottom': noisy(face), 'front': head_front, 'back': noisy(hair), 'side': head_side})
    # Body (tunic with a belt)
    def body(x, y):
        if y == 7:
            return (60, 40, 20, 255) if x not in (3, 4) else (200, 170, 60, 255)
        if y >= 8:
            return noisy(pants)(x, y)
        if x in (0, 7):
            return noisy(trim)(x, y)
        return noisy(tunic)(x, y)
    box(img, 16, 16, 8, 12, 4, {'top': noisy(trim), 'bottom': noisy(pants), 'front': body, 'back': body, 'side': lambda x, y: body(0 if x < 2 else 7, y)})
    # Arms: sleeves then hands
    def arm(x, y):
        if y >= 10:
            return noisy(face, 0.05)(x, y)
        if y == 9:
            return noisy(trim)(x, y)
        return noisy(tunic)(x, y)
    box(img, 40, 16, 4, 12, 4, {'top': noisy(tunic), 'bottom': noisy(face), 'front': arm, 'back': arm, 'side': arm})
    # Legs: trousers then boots
    def leg(x, y):
        return noisy(boots)(x, y) if y >= 9 else noisy(pants)(x, y)
    box(img, 0, 16, 4, 12, 4, {'top': noisy(pants), 'bottom': noisy(boots), 'front': leg, 'back': leg, 'side': leg})
    if hat:
        # Hat layer (u 32): a brim and a crown
        def hat_side(x, y):
            return noisy(hat)(x, y) if y < 3 else ((0, 0, 0, 0) if y > 3 else shade(hat, 0.8))
        box(img, 32, 0, 8, 8, 8, {'top': noisy(hat), 'bottom': (0, 0, 0, 0), 'front': hat_side, 'back': hat_side, 'side': hat_side})
    save(img, 'textures', 'entity', name + '.png')


skin('caravan_guard', hair=(70, 46, 26), tunic=(52, 74, 120), trim=(200, 170, 70), pants=(70, 60, 50), boots=(50, 34, 20))
skin('road_builder', hair=(120, 80, 40), tunic=(176, 96, 40), trim=(120, 64, 26), pants=(60, 80, 130), boots=(80, 52, 28), hat=(222, 184, 60))

# ------------------------------------------------------------------ inn sign models


def faces(tex, uv=None):
    out = {}
    for f in ('north', 'south', 'east', 'west', 'up', 'down'):
        out[f] = {'texture': tex}
    return out


board = faces('#planks')
board['north'] = {'texture': '#board', 'uv': [0, 0, 16, 16]}
board['south'] = {'texture': '#board', 'uv': [16, 0, 0, 16]}
model = {
    'parent': 'minecraft:block/block',
    'textures': {
        'particle': 'minecraft:block/dark_oak_planks',
        'post': 'minecraft:block/dark_oak_log',
        'planks': 'minecraft:block/dark_oak_planks',
        'chain': 'minecraft:block/iron_block',
        'board': 'chroniclecaravans:block/inn_sign',
    },
    'elements': [
        {'from': [1, 0, 7], 'to': [3, 16, 9], 'faces': faces('#post')},
        {'from': [3, 14, 7.5], 'to': [15, 15.5, 8.5], 'faces': faces('#planks')},
        {'from': [4, 3, 7.5], 'to': [14, 12.5, 8.5], 'faces': board},
        {'from': [5, 12.5, 7.75], 'to': [5.5, 14, 8.25], 'faces': faces('#chain')},
        {'from': [12.5, 12.5, 7.75], 'to': [13, 14, 8.25], 'faces': faces('#chain')},
        {'from': [0, 0, 6], 'to': [4, 1, 10], 'faces': faces('#planks')},
    ],
}
jsave(model, 'models', 'block', 'inn_sign.json')
jsave({'parent': 'chroniclecaravans:block/inn_sign'}, 'models', 'item', 'inn_sign.json')
jsave({'parent': 'minecraft:item/generated', 'textures': {'layer0': 'chroniclecaravans:item/caravan_atlas'}}, 'models', 'item', 'caravan_atlas.json')
jsave({'variants': {
    'facing=north': {'model': 'chroniclecaravans:block/inn_sign'},
    'facing=east': {'model': 'chroniclecaravans:block/inn_sign', 'y': 90},
    'facing=south': {'model': 'chroniclecaravans:block/inn_sign', 'y': 180},
    'facing=west': {'model': 'chroniclecaravans:block/inn_sign', 'y': 270},
}}, 'blockstates', 'inn_sign.json')

# ------------------------------------------------------------------ 1.2: loading dock, trading post, parcel
rnd = random.Random(12)
DARK = (54, 35, 18)
FRAME = (96, 64, 34)
PLANK = (152, 110, 64)
IRON = (176, 176, 182)
IRON_D = (104, 104, 112)
IRON_L = (226, 226, 232)


def px(img, x, y, c, k=1.0):
    img.putpixel((x, y), shade(c, k))


def planks(img, x0, y0, x1, y1, base, seams, vertical=False, grain=0.10):
    """Planks with darker seams (rows, or columns when vertical) and a little grain."""
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            pos = x if vertical else y
            k = 1 - grain / 2 + grain * rnd.random()
            if pos in seams:
                k *= 0.72
            elif (pos - 1) in seams:
                k *= 1.08
            px(img, x, y, base, k)


def outline(img, c=DARK):
    for i in range(16):
        for (x, y) in ((i, 0), (i, 15), (0, i), (15, i)):
            px(img, x, y, c)


def crate(seams=(5, 10)):
    img = Image.new('RGBA', (16, 16))
    planks(img, 1, 1, 14, 14, PLANK, seams)
    outline(img)
    for i in range(1, 15):
        for (x, y) in ((i, 1), (i, 14), (1, i), (14, i)):
            px(img, x, y, FRAME, 0.95 + 0.1 * rnd.random())
    # Iron corner brackets with a rivet
    for (cx, cy, dx, dy) in ((1, 1, 1, 1), (14, 1, -1, 1), (1, 14, 1, -1), (14, 14, -1, -1)):
        for (x, y) in ((cx, cy), (cx + dx, cy), (cx + 2 * dx, cy), (cx, cy + dy), (cx, cy + 2 * dy), (cx + dx, cy + dy)):
            px(img, x, y, IRON, 0.92 + 0.12 * rnd.random())
        px(img, cx + dx, cy + dy, IRON_L)
        px(img, cx + 2 * dx, cy + dy, IRON_D)
        px(img, cx + dx, cy + 2 * dy, IRON_D)
    return img


ARROW_UP = [(3, 7, 8), (4, 6, 9), (5, 5, 10), (6, 4, 11), (7, 6, 9), (8, 6, 9), (9, 6, 9), (10, 6, 9), (11, 6, 9)]


def paint_arrow(img, color, down):
    pts = set()
    for (row, a, b) in ARROW_UP:
        r = 15 - row if down else row
        for x in range(a, b + 1):
            pts.add((x, r))
    for (x, y) in pts:
        # Stencilled paint: darker on the seams, a little worn
        base = img.getpixel((x, y))
        seam = sum(base[:3]) < sum(shade(PLANK, 0.8)[:3])
        k = (0.8 if seam else 1.0) * (0.92 + 0.1 * rnd.random())
        px(img, x, y, color, k)
    # Darker paint on the right and lower edges of the arrow, for some relief
    for (x, y) in pts:
        if (x + 1, y) not in pts or (x, y + 1) not in pts:
            img.putpixel((x, y), shade(img.getpixel((x, y)), 0.82))


GREEN = (92, 182, 84)
BLUE = (84, 146, 214)
side_out = crate()
paint_arrow(side_out, GREEN, False)
save(side_out, 'textures', 'block', 'loading_dock_side_out.png')
side_in = crate()
paint_arrow(side_in, BLUE, True)
save(side_in, 'textures', 'block', 'loading_dock_side_in.png')

# Top: a hatch of vertical planks held by two iron bands, with a ring
top = Image.new('RGBA', (16, 16))
planks(top, 1, 1, 14, 14, shade(PLANK, 1.06), (5, 10), vertical=True)
outline(top)
for i in range(1, 15):
    for (x, y) in ((i, 1), (i, 14), (1, i), (14, i)):
        px(top, x, y, FRAME, 0.95 + 0.1 * rnd.random())
for y in (4, 11):
    for x in range(2, 14):
        px(top, x, y, IRON, 0.94 + 0.1 * rnd.random())
        px(top, x, y + 1, IRON_D, 0.95 + 0.1 * rnd.random())
    px(top, 3, y, IRON_L)
    px(top, 12, y, IRON_L)
for (x, y) in ((7, 6), (8, 6), (6, 7), (9, 7), (6, 8), (9, 8), (7, 9), (8, 9)):
    px(top, x, y, IRON_L if y < 8 else IRON)
save(top, 'textures', 'block', 'loading_dock_top.png')

bottom = Image.new('RGBA', (16, 16))
planks(bottom, 1, 1, 14, 14, shade(PLANK, 0.9), (5, 10))
outline(bottom)
save(bottom, 'textures', 'block', 'loading_dock_bottom.png')

for name, side in (('loading_dock_in', 'loading_dock_side_in'), ('loading_dock_out', 'loading_dock_side_out')):
    jsave({'parent': 'minecraft:block/cube_bottom_top', 'textures': {
        'side': 'chroniclecaravans:block/' + side,
        'top': 'chroniclecaravans:block/loading_dock_top',
        'bottom': 'chroniclecaravans:block/loading_dock_bottom'}}, 'models', 'block', name + '.json')
jsave({'variants': {
    'outgoing=false': {'model': 'chroniclecaravans:block/loading_dock_in'},
    'outgoing=true': {'model': 'chroniclecaravans:block/loading_dock_out'},
}}, 'blockstates', 'loading_dock.json')
jsave({'parent': 'chroniclecaravans:block/loading_dock_out'}, 'models', 'item', 'loading_dock.json')

# ------------------------------------------------------------------ trading post
CLOTH = (52, 128, 70)
CREAM = (236, 222, 184)
GOLD = (226, 182, 64)
EMERALD = (64, 214, 118)
WOOD = (122, 84, 48)

# Front of the counter (the model shows rows 7-15, columns 1-14): planks, a green cloth with an emerald
front = Image.new('RGBA', (16, 16))
planks(front, 0, 0, 15, 15, WOOD, (3, 7, 11), vertical=True)
for x in range(1, 15):
    px(front, x, 7, DARK)
    px(front, x, 15, DARK)
for y in range(7, 16):
    px(front, 1, y, DARK)
    px(front, 14, y, DARK)
for y in range(8, 13):
    for x in range(2, 14):
        px(front, x, y, CLOTH, 0.9 + 0.12 * rnd.random() - (0.1 if y == 8 else 0))
for x in range(2, 14):
    if x % 2 == 0:
        px(front, x, 13, CLOTH, 0.8)
    px(front, x, 12, GOLD, 0.9 + 0.1 * rnd.random())
for (x, y, k) in ((7, 9, 1.25), (8, 9, 1.0), (6, 10, 1.0), (7, 10, 1.1), (8, 10, 0.95), (9, 10, 0.8), (7, 11, 0.85), (8, 11, 0.7)):
    px(front, x, y, EMERALD, k)
save(front, 'textures', 'block', 'trading_post_front.png')

side = Image.new('RGBA', (16, 16))
planks(side, 0, 0, 15, 15, WOOD, (3, 7, 11), vertical=True)
for x in range(1, 15):
    px(side, x, 7, DARK)
    px(side, x, 15, DARK)
for y in range(7, 16):
    px(side, 1, y, DARK)
    px(side, 14, y, DARK)
for i in range(11):
    # A diagonal brace
    x, y = 2 + i * 12 // 10, 14 - i * 6 // 10
    px(side, min(13, x), max(8, y), FRAME, 1.1)
save(side, 'textures', 'block', 'trading_post_side.png')

# Counter top: light planks, an open ledger, a stack of emeralds and a few coins
ctop = Image.new('RGBA', (16, 16))
planks(ctop, 0, 0, 15, 15, (176, 134, 82), (4, 9, 14))
for x in range(16):
    px(ctop, x, 0, FRAME)
for y in range(3, 10):
    for x in range(2, 9):
        edge = y in (3, 9) or x in (2, 8)
        px(ctop, x, y, (120, 52, 36) if edge else CREAM, 1.0 if edge else 0.95 + 0.06 * rnd.random())
for y in range(4, 9):
    px(ctop, 5, y, (170, 150, 120))
for (x, y) in ((3, 5), (4, 5), (6, 5), (7, 5), (3, 7), (4, 7), (6, 7)):
    px(ctop, x, y, (110, 100, 90))
for (x, y, k) in ((11, 4, 1.2), (12, 4, 1.0), (10, 5, 1.0), (11, 5, 1.1), (12, 5, 0.9), (13, 5, 0.75), (11, 6, 0.8), (12, 6, 0.65)):
    px(ctop, x, y, EMERALD, k)
for (x, y, k) in ((10, 8, 1.15), (11, 8, 0.9), (12, 9, 1.0), (13, 9, 0.8)):
    px(ctop, x, y, GOLD, k)
save(ctop, 'textures', 'block', 'trading_post_top.png')

# Board at the back: a striped awning cloth (the model shows rows 0-4)
board = Image.new('RGBA', (16, 16))
for y in range(16):
    for x in range(16):
        c = CLOTH if (x // 2) % 2 == 0 else CREAM
        k = 0.92 + 0.1 * rnd.random()
        if y % 5 == 0:
            k *= 0.75
        if y % 5 == 4:
            k *= 0.88 if x % 2 == 0 else 1.05
        px(board, x, y, c, k)
save(board, 'textures', 'block', 'trading_post_board.png')


def box_faces(**tex):
    return {f: ({'texture': t} if isinstance(t, str) else t) for f, t in tex.items()}


post = {
    'parent': 'minecraft:block/block',
    'textures': {
        'particle': 'minecraft:block/spruce_planks',
        'planks': 'minecraft:block/spruce_planks',
        'front': 'chroniclecaravans:block/trading_post_front',
        'side': 'chroniclecaravans:block/trading_post_side',
        'top': 'chroniclecaravans:block/trading_post_top',
        'board': 'chroniclecaravans:block/trading_post_board',
        'post': 'minecraft:block/stripped_spruce_log',
    },
    'elements': [
        {'from': [1, 0, 1], 'to': [15, 9, 15], 'faces': box_faces(north='#front', south='#side', east='#side', west='#side', down='#planks')},
        {'from': [0, 9, 0], 'to': [16, 11, 16], 'faces': box_faces(north='#planks', south='#planks', east='#planks', west='#planks', up='#top', down='#planks')},
        {'from': [0, 11, 13], 'to': [1.5, 16, 16], 'faces': box_faces(north='#post', south='#post', east='#post', west='#post', up='#post')},
        {'from': [14.5, 11, 13], 'to': [16, 16, 16], 'faces': box_faces(north='#post', south='#post', east='#post', west='#post', up='#post')},
        {'from': [1.5, 11.5, 14], 'to': [14.5, 16, 15.5], 'faces': box_faces(
            north={'texture': '#board', 'uv': [0, 0, 13, 4.5]}, south='#planks', up='#planks', down='#planks')},
    ],
}
jsave(post, 'models', 'block', 'trading_post.json')
jsave({'parent': 'chroniclecaravans:block/trading_post'}, 'models', 'item', 'trading_post.json')
jsave({'variants': {
    'facing=north': {'model': 'chroniclecaravans:block/trading_post'},
    'facing=east': {'model': 'chroniclecaravans:block/trading_post', 'y': 90},
    'facing=south': {'model': 'chroniclecaravans:block/trading_post', 'y': 180},
    'facing=west': {'model': 'chroniclecaravans:block/trading_post', 'y': 270},
}}, 'blockstates', 'trading_post.json')

# ------------------------------------------------------------------ parcel (16x16 item): kraft paper, string, wax seal
parcel = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
KRAFT = (184, 140, 88)
for y in range(5, 14):
    for x in range(2, 13):
        px(parcel, x, y, KRAFT, 0.92 + 0.1 * rnd.random())
for y in range(3, 13):
    for x in range(13, 15):
        if y - 3 <= (x - 13) * 2 + 1 or x == 13:
            px(parcel, x, y, KRAFT, 0.72 + 0.06 * rnd.random())
for (y, a, b) in ((3, 4, 14), (4, 3, 13)):
    for x in range(a, b + 1):
        px(parcel, x, y, KRAFT, 1.15 + 0.05 * rnd.random())
for x in range(2, 15):
    for y in range(2, 15):
        c = parcel.getpixel((x, y))
        if c[3] == 0:
            continue
        n = [parcel.getpixel((x + dx, y + dy))[3] if 0 <= x + dx < 16 and 0 <= y + dy < 16 else 0 for (dx, dy) in ((1, 0), (-1, 0), (0, 1), (0, -1))]
        if 0 in n:
            parcel.putpixel((x, y), shade(c, 0.62))
STRING = (240, 230, 196)
for y in range(4, 14):
    px(parcel, 7, y, STRING, 0.95 if y > 4 else 1.05)
for x in range(2, 13):
    px(parcel, x, 9, STRING, 0.95)
for (x, y) in ((13, 7), (14, 6)):
    px(parcel, x, y, STRING, 0.8)
for (x, y, k) in ((7, 8, 1.15), (6, 9, 1.0), (7, 9, 0.95), (8, 9, 0.8), (7, 10, 0.7), (6, 8, 1.0), (8, 10, 0.65), (6, 10, 0.8), (8, 8, 0.9)):
    px(parcel, x, y, (172, 36, 32), k)
save(parcel, 'textures', 'item', 'caravan_parcel.png')
jsave({'parent': 'minecraft:item/generated', 'textures': {'layer0': 'chroniclecaravans:item/caravan_parcel'}}, 'models', 'item', 'caravan_parcel.json')

# ------------------------------------------------------------------ relay charter (16x16 item): parchment, ribbon, emerald wax seal
charter = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
PAPER = (232, 214, 170)
for y in range(3, 13):
    for x in range(3, 12):
        px(charter, x, y, PAPER, 0.93 + 0.09 * rnd.random())
for x in range(2, 13):
    # Rolled ends, top and bottom
    px(charter, x, 2, PAPER, 0.80)
    px(charter, x, 3, PAPER, 1.08)
    px(charter, x, 13, PAPER, 0.74)
    px(charter, x, 12, PAPER, 1.02)
for (x0, y0) in ((2, 2), (12, 2), (2, 13), (12, 13)):
    px(charter, x0, y0, PAPER, 0.62)
for y in range(2, 14):
    for x in range(2, 13):
        c = charter.getpixel((x, y))
        if c[3] and (x in (2, 12) or y in (2, 13)):
            charter.putpixel((x, y), shade(c, 0.78))
INK = (92, 64, 40)
for (y, a, b) in ((5, 4, 10), (7, 4, 9), (9, 4, 7)):
    for x in range(a, b + 1):
        if rnd.random() > 0.18:
            px(charter, x, y, INK, 0.9 + 0.2 * rnd.random())
# Ribbon and seal (emerald green, the guilds' colour)
RIBBON = (40, 120, 64)
for (x, y) in ((9, 11), (10, 12), (10, 13), (11, 14), (8, 12), (8, 13), (7, 14)):
    px(charter, x, y, RIBBON, 0.9)
SEAL = (36, 168, 84)
for (x, y, k) in ((9, 9, 1.2), (10, 9, 1.0), (8, 10, 1.05), (9, 10, 0.95), (10, 10, 0.85), (11, 10, 0.75), (9, 11, 0.7), (10, 11, 0.62), (11, 9, 0.8), (8, 9, 0.9)):
    px(charter, x, y, SEAL, k)
px(charter, 9, 10, (210, 240, 200), 1.0)
save(charter, 'textures', 'item', 'relay_charter.png')
jsave({'parent': 'minecraft:item/generated', 'textures': {'layer0': 'chroniclecaravans:item/relay_charter'}}, 'models', 'item', 'relay_charter.json')


# ------------------------------------------------------------------ hitching post (model with vanilla textures)
def el(frm, to, tex, faces=('north', 'south', 'east', 'west', 'up', 'down')):
    return {'from': frm, 'to': to, 'faces': {f: {'texture': (tex[f] if isinstance(tex, dict) else tex)} for f in faces}}


log = {'north': '#side', 'south': '#side', 'east': '#side', 'west': '#side', 'up': '#top', 'down': '#top'}
jsave({'parent': 'minecraft:block/block',
       'textures': {'particle': 'minecraft:block/stripped_spruce_log', 'side': 'minecraft:block/stripped_spruce_log',
                    'top': 'minecraft:block/stripped_spruce_log_top', 'cap': 'minecraft:block/spruce_planks', 'iron': 'minecraft:block/iron_block'},
       'elements': [
           el([6, 0, 6], [10, 14, 10], log),
           el([5, 14, 5], [11, 16, 11], '#cap'),
           el([5.5, 1, 5.5], [10.5, 2, 10.5], '#cap'),
           # The iron ring the leads tie to, on two sides
           el([10, 11, 7.5], [12.5, 11.75, 8.5], '#iron'),
           el([11.75, 9, 7.5], [12.5, 11, 8.5], '#iron'),
           el([10, 8.25, 7.5], [12.5, 9, 8.5], '#iron'),
           el([3.5, 11, 7.5], [6, 11.75, 8.5], '#iron'),
           el([3.5, 9, 7.5], [4.25, 11, 8.5], '#iron'),
           el([3.5, 8.25, 7.5], [6, 9, 8.5], '#iron'),
       ]}, 'models', 'block', 'hitching_post.json')
jsave({'variants': {'': {'model': 'chroniclecaravans:block/hitching_post'}}}, 'blockstates', 'hitching_post.json')
jsave({'parent': 'chroniclecaravans:block/hitching_post'}, 'models', 'item', 'hitching_post.json')


# ------------------------------------------------------------------ milestone tiers: gold collar, then guild pennants
def tier_model(elements):
    return {'textures': {'particle': 'minecraft:block/stone_bricks', 'gold': 'minecraft:block/gold_block',
                         'pole': 'minecraft:block/dark_oak_planks', 'flag': 'minecraft:block/green_wool'}, 'elements': elements}


def pennant(axis, sign):
    """A small pole out of the cap and a green pennant hanging from it, on one side (x or z, + or -)."""
    def box(a0, a1, y0, y1, c0, c1):
        lo, hi = (a0, a1) if sign > 0 else (16 - a1, 16 - a0)
        return ([lo, y0, c0], [hi, y1, c1]) if axis == 'x' else ([c0, y0, lo], [c1, y1, hi])
    pole = box(12.5, 16, 12, 12.75, 7.5, 8.5)
    flag = box(14.5, 15.75, 7.5, 12, 7.75, 8.25)
    return [el(pole[0], pole[1], '#pole'), el(flag[0], flag[1], '#flag')]


jsave(tier_model([el([3, 3, 3], [13, 4, 13], '#gold'), el([3.75, 10, 3.75], [12.25, 11, 12.25], '#gold')]), 'models', 'block', 'milestone_tier2.json')
jsave(tier_model(pennant('x', 1) + pennant('x', -1)), 'models', 'block', 'milestone_tier3.json')
jsave(tier_model(pennant('z', 1) + pennant('z', -1)), 'models', 'block', 'milestone_tier4.json')
jsave({'multipart': [
    {'apply': {'model': 'chroniclecaravans:block/milestone'}},
    {'when': {'tier': '2|3|4'}, 'apply': {'model': 'chroniclecaravans:block/milestone_tier2'}},
    {'when': {'tier': '3|4'}, 'apply': {'model': 'chroniclecaravans:block/milestone_tier3'}},
    {'when': {'tier': '4'}, 'apply': {'model': 'chroniclecaravans:block/milestone_tier4'}},
]}, 'blockstates', 'milestone.json')

import sys
if '--preview' in sys.argv:
    names = [('block', 'loading_dock_side_out'), ('block', 'loading_dock_side_in'), ('block', 'loading_dock_top'), ('block', 'loading_dock_bottom'),
             ('block', 'trading_post_front'), ('block', 'trading_post_side'), ('block', 'trading_post_top'), ('block', 'trading_post_board'),
             ('item', 'caravan_parcel'), ('block', 'inn_sign'), ('item', 'caravan_atlas'), ('item', 'relay_charter')]
    sheet = Image.new('RGBA', (len(names) * 136, 136), (40, 40, 48, 255))
    for i, (kind, n) in enumerate(names):
        t = Image.open(os.path.join(ASSETS, 'textures', kind, n + '.png')).convert('RGBA').resize((128, 128), Image.NEAREST)
        sheet.alpha_composite(t, (i * 136 + 4, 4))
    sheet.save('/tmp/assets_preview.png')
print('ok')
