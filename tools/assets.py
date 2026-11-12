#!/usr/bin/env python3
"""Dessine les textures des nouveautés 1.1 : enseigne d'auberge, atlas, peaux des gardes et des bâtisseurs,
et écrit les modèles de l'enseigne.  Usage : python3 tools/assets.py"""
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
print('ok')
