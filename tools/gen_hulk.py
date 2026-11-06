"""Hulk textures: the 64x64 Hulk skin (classic layout), gamma reactor block, gamma bomb,
gamma capsule and the gamma syringe."""
import colorsys
import random
from PIL import Image

A = "src/main/resources/assets/flash/textures/"
random.seed(4)

G = [(54, 120, 34), (66, 140, 40), (80, 160, 48), (96, 180, 58), (118, 200, 72)]  # greens dark->light
GD = (38, 90, 26)
HAIR = [(18, 18, 22), (28, 28, 34), (40, 40, 48)]
P = [(70, 26, 96), (92, 36, 124), (112, 48, 150), (132, 64, 172)]   # purples
WHITE = (236, 240, 230)
MOUTH = (30, 16, 16)
TEETH = (220, 220, 200)

img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
px = img.load()


def put(x, y, c):
    px[x, y] = tuple(c) + (255,)


def green(level, x, y):
    i = max(0, min(4, level + (1 if (x * 7 + y * 3) % 11 == 0 else 0) - (1 if (x * 5 + y * 9) % 13 == 0 else 0)))
    return G[i]


def fill(x0, y0, w, h, fn):
    for y in range(h):
        for x in range(w):
            put(x0 + x, y0 + y, fn(x, y, w, h))


# ---------------- head (0..32, 0..16) ----------------
fill(8, 0, 8, 8, lambda x, y, w, h: random.choice(HAIR))          # top: hair
fill(16, 0, 8, 8, lambda x, y, w, h: green(1, x, y))              # bottom (chin/neck)
FACE = ["HHHHHHHH",
        "HHHhHHHH",
        "HgggggHH",
        "BBgggBBg",
        "gWWgWWgg",
        "gggnnggg",
        "gMTTTTMg",
        "ggMMMMgg"]
fp = {"H": None, "h": None, "g": "g", "B": (24, 40, 18), "W": WHITE, "n": GD, "M": MOUTH, "T": TEETH}
for y, row in enumerate(FACE):
    for x, ch in enumerate(row):
        if ch in "Hh":
            put(8 + x, 8 + y, random.choice(HAIR))
        elif ch == "g":
            put(8 + x, 8 + y, green(2 if y < 6 else 1, x, y))
        else:
            put(8 + x, 8 + y, fp[ch])
# angry brow slant: inner ends lower
put(8 + 2, 8 + 3, (24, 40, 18))
put(8 + 5, 8 + 3, (24, 40, 18))
for side in (0, 16):        # head sides: hair on top, green with an ear
    fill(side, 8, 8, 8, lambda x, y, w, h: random.choice(HAIR) if y < 3 or (y < 4 and x > 4) else green(2, x, y))
    put(side + 3, 8 + 4, GD)
    put(side + 3, 8 + 5, GD)
fill(24, 8, 8, 8, lambda x, y, w, h: random.choice(HAIR) if y < 5 else green(1, x, y))   # back
# hat layer: messy hair volume on top/sides (transparent elsewhere)
for x in range(40, 48):
    for y in range(0, 8):
        if random.random() < 0.85:
            put(x, y, random.choice(HAIR))
for x0 in (32, 40, 48, 56):
    for x in range(x0, x0 + 8):
        for y in range(8, 10 + (1 if x0 in (32, 48) else 0)):
            if random.random() < 0.8 and not (x0 == 40 and y > 8):
                put(x, y, random.choice(HAIR))

# ---------------- body (16..40, 16..32) ----------------
fill(20, 16, 8, 4, lambda x, y, w, h: green(2, x, y))     # top
fill(28, 16, 8, 4, lambda x, y, w, h: green(1, x, y))     # bottom
fill(16, 20, 4, 12, lambda x, y, w, h: green(1, x, y))    # sides
fill(28, 20, 4, 12, lambda x, y, w, h: green(1, x, y))


def chest(x, y, w, h):
    if y >= 10:                       # waistband of the pants
        return P[1] if y == 10 else P[0]
    if x in (3, 4) and y < 5:         # line between the pecs
        return G[0]
    if y < 5:                         # pecs, lighter at the top
        return green(4 if y < 2 else 3, x, y)
    if y == 5:
        return G[0]
    # abs: 2x2 blocks with dark grooves
    if x in (3, 4) or y in (7,):
        return G[1]
    return green(3, x, y)


fill(20, 20, 8, 12, chest)


def back(x, y, w, h):
    if y >= 10:
        return P[1] if y == 10 else P[0]
    if x in (3, 4):
        return G[1]
    return green(2 if y < 5 else 1, x, y)


fill(32, 20, 8, 12, back)

# ---------------- arms (40..56, 16..32) ----------------
fill(44, 16, 4, 4, lambda x, y, w, h: green(3, x, y))     # shoulder top
fill(48, 16, 4, 4, lambda x, y, w, h: green(1, x, y))     # hand bottom


def arm(x, y, w, h):
    if y >= 9:                        # big fists, darker knuckles
        return G[1] if y == 9 else green(2, x, y) if (x + y) % 3 else G[0]
    if y < 4:                         # shoulders / biceps highlight
        return green(4 if x in (1, 2) else 3, x, y)
    if y == 5:
        return G[1]
    return green(2, x, y)


for x0 in (40, 44, 48, 52):
    fill(x0, 20, 4, 12, arm)

# ---------------- legs (0..16, 16..32): torn purple pants ----------------
fill(4, 16, 4, 4, lambda x, y, w, h: P[1])                # top
fill(8, 16, 4, 4, lambda x, y, w, h: green(1, x, y))      # foot sole
TORN = [7, 8, 7, 9]   # first green row (from the top of the leg side) per column


def leg(x, y, w, h):
    edge = TORN[x % 4] + (1 if (x // 4) % 2 else 0)
    if y < edge:
        return P[2] if (x + y) % 5 == 0 else P[1] if y % 3 else P[3]
    if y == edge:
        return P[0]
    return green(2 if y < 10 else 1, x, y)


fill(0, 20, 16, 12, leg)
img.save(A + "entity/hulk.png")

# ---------------- gamma reactor block (16x16) ----------------
STEEL = [(54, 60, 66), (68, 76, 84), (86, 94, 104)]


def tex(name, fn):
    t = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            t.putpixel((x, y), tuple(fn(x, y)) + (255,))
    t.save(A + "block/" + name + ".png")


def side(x, y):
    if x in (0, 15) or y in (0, 15):
        return STEEL[0]
    if x in (1, 14) or y in (1, 14):
        return STEEL[2]
    if 4 <= x <= 11 and 3 <= y <= 12:          # glass window with the gamma core
        cx, cy = x - 7.5, y - 7.5
        d = (cx * cx + cy * cy) ** 0.5
        if x in (4, 11) or y in (3, 12):
            return (30, 34, 38)
        return (140, 255, 110) if d < 2.2 else (80, 210, 60) if d < 3.5 else (40, 130, 30)
    if y in (5, 9) and x in (2, 13):
        return (240, 200, 40)                  # warning lights
    return random.choice(STEEL[:2])


def top(x, y):
    if x in (0, 15) or y in (0, 15):
        return STEEL[0]
    cx, cy = x - 7.5, y - 7.5
    d = (cx * cx + cy * cy) ** 0.5
    if d < 2.5:
        return (160, 255, 120)
    if d < 3.5:
        return (60, 170, 40)
    if d < 4.5:
        return (30, 34, 38)
    if (x + y) % 4 == 0:
        return (240, 200, 40) if d > 6 else STEEL[2]       # hazard stripes
    return random.choice(STEEL[:2])


def bottom(x, y):
    return STEEL[0] if x in (0, 15) or y in (0, 15) else random.choice(STEEL[:2])


tex("gamma_reactor_side", side)
tex("gamma_reactor_top", top)
tex("gamma_reactor_bottom", bottom)

# ---------------- gamma bomb item ----------------
bomb = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
bp = bomb.load()
for y in range(16):
    for x in range(16):
        cx, cy = x - 7.5, y - 8.5
        d = (cx * cx + cy * cy) ** 0.5
        if d < 5.6:
            c = (40, 44, 48) if d > 4.6 else (70, 200, 50) if d < 2.2 else (46, 120, 32)
            if (x, y) in ((6, 6), (7, 6), (6, 7)):
                c = (180, 255, 150)
            bp[x, y] = c + (255,)
for y in range(1, 4):                     # fuse / cap
    bp[7, y] = (70, 76, 84, 255)
    bp[8, y] = (90, 96, 104, 255)
bp[9, 1] = (255, 200, 40, 255)
bomb.save(A + "item/gamma_bomb.png")


# ---------------- gamma capsule / syringe: recolour the V-9 ones (blue -> green) ----------------
def to_green(src, dst):
    im = Image.open(A + src).convert("RGBA")
    p = im.load()
    for y in range(im.height):
        for x in range(im.width):
            r, g, b, a = p[x, y]
            if a == 0:
                continue
            h, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
            if s > 0.35 and 0.45 < h < 0.75:
                r2, g2, b2 = colorsys.hsv_to_rgb(0.30, s, v)
                p[x, y] = (int(r2 * 255), int(g2 * 255), int(b2 * 255), a)
    im.save(A + dst)


to_green("item/v9_capsule.png", "item/gamma_capsule.png")
to_green("item/syringe_v9.png", "item/syringe_gamma.png")
