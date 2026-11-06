"""Texture for the mod's suit stand model (FlashSuitStandModel, 64x64) and its item icon."""
import random
from PIL import Image

OUT = "src/main/resources/assets/flash/textures/"
random.seed(7)

METAL = [(0x24, 0x27, 0x2e), (0x2e, 0x33, 0x3c), (0x3a, 0x40, 0x4b)]
METAL_LIGHT = (0x5a, 0x63, 0x72)
RED = [(0x75, 0x0e, 0x24), (0x83, 0x0d, 0x1f), (0x92, 0x15, 0x1f)]
RED_DARK = (0x49, 0x08, 0x25)
GOLD = (0xdd, 0xa9, 0x7a)
GOLD_LIGHT = (0xe5, 0xc4, 0x8b)
GOLD_DARK = (0xc7, 0x92, 0x68)

img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
px = img.load()


def noisy(pal):
    return random.choice(pal) + (255,)


def faces(u, v, w, h, d):
    """Vanilla cube unwrap: dict face -> (x, y, width, height)."""
    return {
        "top": (u + d, v, w, d),
        "bottom": (u + d + w, v, w, d),
        "right": (u, v + d, d, h),
        "front": (u + d, v + d, w, h),
        "left": (u + d + w, v + d, d, h),
        "back": (u + d + w + d, v + d, w, h),
    }


def fill(rect, painter):
    x0, y0, w, h = rect
    for y in range(h):
        for x in range(w):
            px[x0 + x, y0 + y] = painter(x, y, w, h)


def metal(x, y, w, h):
    if y == 0:
        return METAL_LIGHT + (255,)
    return noisy(METAL)


def red(x, y, w, h):
    if y == 0 or x == 0 or x == w - 1:
        return RED_DARK + (255,)
    return noisy(RED)


def cube(u, v, w, h, d, side=metal, front=None, top=None):
    f = faces(u, v, w, h, d)
    for name, rect in f.items():
        painter = side
        if name == "front" and front:
            painter = front
        if name in ("top", "bottom") and top:
            painter = top
        fill(rect, painter)


# head: smooth graphite with a thin red visor line
def head_front(x, y, w, h):
    if y == 2 and 0 < x < w - 1:
        return (0xb0, 0x18, 0x2a, 255)
    if y == 3 and 1 < x < w - 2:
        return RED_DARK + (255,)
    return noisy(METAL)


cube(0, 0, 6, 6, 6, front=head_front)
cube(24, 0, 2, 1, 2)
# shoulder bar: red with a gold edge
def bar(x, y, w, h):
    if y == 0:
        return GOLD_DARK + (255,)
    return noisy(RED)


cube(0, 12, 10, 2, 3, side=bar, top=lambda x, y, w, h: noisy(RED))
# chest frame with the lightning emblem (gold bolt on a white circle, like the suit)
EMBLEM = ["..WWW..",
          ".WW.BW.",
          ".W.BW..",
          ".WBBBW.",
          "..WBWW."]


def chest_front(x, y, w, h):
    ch = EMBLEM[y][x] if y < len(EMBLEM) and x < len(EMBLEM[0]) else "."
    if ch == "W":
        return (0xde, 0xff, 0xff, 255)
    if ch == "B":
        return GOLD_LIGHT + (255,)
    return noisy(RED)


cube(0, 17, 7, 5, 2, side=red, front=chest_front)
cube(26, 12, 2, 4, 2)
cube(18, 24, 6, 2, 3, side=bar)
# pole with gold rings every few pixels
def pole(x, y, w, h):
    if y in (0, 4, 9):
        return GOLD_DARK + (255,)
    return noisy(METAL)


cube(34, 12, 2, 10, 2, side=pole)


# dais: graphite with a gold rim and a red ring on top
def dais_top(x, y, w, h):
    edge = x == 0 or y == 0 or x == w - 1 or y == h - 1
    ring = (x in (2, w - 3) and 2 <= y <= h - 3) or (y in (2, h - 3) and 2 <= x <= w - 3)
    if edge:
        return GOLD + (255,)
    if ring:
        return (0xb0, 0x18, 0x2a, 255)
    return noisy(METAL)


def gold_side(x, y, w, h):
    return GOLD_DARK + (255,) if (x + y) % 3 else GOLD + (255,)


cube(0, 30, 9, 1, 9, side=gold_side, top=dais_top)


def base_top(x, y, w, h):
    edge = x == 0 or y == 0 or x == w - 1 or y == h - 1
    inner = x == 1 or y == 1 or x == w - 2 or y == h - 2
    diag = (x == y or x == w - 1 - y) and 1 < x < w - 2
    if edge:
        return METAL_LIGHT + (255,)
    if inner:
        return RED_DARK + (255,)
    if diag:
        return GOLD_DARK + (255,)
    return noisy(METAL)


cube(0, 40, 12, 1, 12, side=lambda x, y, w, h: METAL_LIGHT + (255,), top=base_top)
# arms: red joint, graphite rods
cube(44, 0, 3, 3, 3, side=red)
cube(44, 6, 2, 9, 2)
img.save(OUT + "entity/suit_stand.png")

# item icon (16x16): front view of the new stand
icon = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
ip = icon.load()


def rect(x0, y0, x1, y1, col):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            ip[x, y] = col + (255,)


rect(6, 0, 9, 3, METAL[2])          # head
ip[6, 1] = ip[7, 1] = ip[8, 1] = ip[9, 1] = (0xb0, 0x18, 0x2a, 255)
rect(7, 4, 8, 4, METAL[1])          # neck
rect(3, 5, 12, 5, RED[2])           # shoulder bar
rect(2, 5, 2, 10, METAL[2])          # arms
rect(13, 5, 13, 10, METAL[2])
rect(5, 6, 10, 8, RED[1])            # chest
ip[7, 7] = ip[8, 7] = GOLD_LIGHT + (255,)
ip[8, 6] = (0xde, 0xff, 0xff, 255)
ip[7, 8] = (0xde, 0xff, 0xff, 255)
rect(7, 9, 8, 12, METAL[1])          # pole
rect(5, 13, 10, 13, GOLD)            # dais
rect(3, 14, 12, 14, METAL[2])        # base
rect(3, 15, 12, 15, METAL[0])
icon.save(OUT + "item/flash_suit_stand.png")
