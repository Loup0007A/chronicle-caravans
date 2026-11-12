#!/usr/bin/env python3
"""Draws the mod logo (pixel art): a caravan crossing dunes at sunset.
Usage: python3 tools/logo.py  ->  src/main/resources/chroniclecaravans_logo.png + curseforge/avatar.png
"""
from PIL import Image

W = H = 64
img = Image.new("RGB", (W, H))
px = img.load()


def lerp(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


# Sky: deep violet at the top, orange near the horizon
SKY = [(0.0, (38, 24, 74)), (0.45, (150, 60, 110)), (0.75, (240, 130, 70)), (1.0, (255, 205, 120))]
HORIZON = 44
for y in range(H):
    t = min(1.0, y / HORIZON)
    for i in range(len(SKY) - 1):
        (t0, c0), (t1, c1) = SKY[i], SKY[i + 1]
        if t0 <= t <= t1:
            c = lerp(c0, c1, (t - t0) / (t1 - t0))
            break
    for x in range(W):
        px[x, y] = c

# Sun
cx, cy, r = 44, 34, 11
for y in range(H):
    for x in range(W):
        d = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5
        if d <= r:
            px[x, y] = (255, 236, 170) if d < r - 2 else (255, 214, 130)

# A few stars
for x, y in [(6, 5), (15, 11), (27, 4), (52, 7), (59, 15), (36, 13), (9, 19)]:
    px[x, y] = (255, 240, 220)

import math


def dune(base, amp, freq, phase, color):
    for x in range(W):
        top = int(base + amp * math.sin(x * freq + phase))
        for y in range(top, H):
            px[x, y] = color


dune(43, 3.0, 0.11, 0.6, (178, 92, 60))
dune(48, 2.5, 0.16, 2.4, (120, 58, 52))

INK = (30, 16, 34)


def rect(x0, y0, w, h, c=INK):
    for y in range(y0, y0 + h):
        for x in range(x0, x0 + w):
            if 0 <= x < W and 0 <= y < H:
                px[x, y] = c


def llama(x, y, load):
    """Silhouette facing right; (x, y) is the top-left corner of the body."""
    rect(x, y, 8, 4)  # body
    rect(x, y + 4, 1, 4)
    rect(x + 2, y + 4, 1, 4)  # hind legs
    rect(x + 5, y + 4, 1, 4)
    rect(x + 7, y + 4, 1, 4)  # front legs
    rect(x + 7, y - 4, 2, 5)  # neck
    rect(x + 7, y - 6, 3, 2)  # head
    rect(x + 7, y - 7, 1, 1)  # ear
    rect(x - 1, y, 1, 2)  # tail
    rect(x + 2, y - 2, 4, 2, load)  # pack
    rect(x + 2, y, 4, 1, (240, 200, 90))  # blanket trim


def walker(x, y):
    rect(x, y, 3, 5)  # robe
    rect(x, y + 5, 1, 3)
    rect(x + 2, y + 5, 1, 3)
    rect(x, y - 3, 3, 3)  # hooded head
    rect(x + 4, y - 4, 1, 12)  # staff


GROUND = 53
walker(49, GROUND - 8)
llama(35, GROUND - 8, (70, 140, 200))
llama(21, GROUND - 8, (200, 70, 70))
llama(7, GROUND - 8, (90, 170, 90))
# Lead ropes
for x0, x1 in [(45, 49), (31, 35), (17, 21)]:
    for x in range(x0, x1):
        px[x, GROUND - 9] = (240, 200, 90)

# Foreground sand
for x in range(W):
    top = int(53 + 1.5 * math.sin(x * 0.2 + 1.0))
    for y in range(top, H):
        px[x, y] = (58, 30, 44)

img.resize((256, 256), Image.NEAREST).save("src/main/resources/chroniclecaravans_logo.png")
img.resize((400, 400), Image.NEAREST).save("curseforge/avatar.png")
print("logo written")
