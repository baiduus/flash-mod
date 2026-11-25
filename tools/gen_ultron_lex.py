"""Ultron v3: the Lex suit itself (geo/lex/mark6, the Mark 6 of Legends: Superheroes), recoloured in Ultron's
silver and gunmetal with red lights, and a head of his own made here (no faceplate that opens): a silver skull with
a ridge down the middle and red lines over it, deep sockets with the red flower-shaped eyes, the grille of the mouth
and the blades at the sides.

Writes geo/ultron.geo.json (the Mark 6 without its helmet), textures/entity/ultron.png and ultron_glow.png (the
recoloured textures), geo/ultron_head.geo.json and textures/entity/ultron_head.png.
usage (from the repo root): python3 tools/gen_ultron_lex.py
"""
import colorsys
import copy
import json
import math
import os
import random
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen_geo_machines as g  # noqa: E402
import gen_machines_v3 as v3  # noqa: E402
from PIL import Image  # noqa: E402

R = g.R
random.seed(5)
shade = g.shade
noise = g.noise


def body():
    geo = json.load(open(R + "geo/lex/mark6.geo.json"))
    gm = geo["minecraft:geometry"][0]
    gm["description"]["identifier"] = "geometry.ultron"
    drop = set()
    names = {b["name"]: b for b in gm["bones"]}

    def under_head(n):
        while n in names:
            if n == "Head":
                return True
            n = names[n].get("parent")
        return False

    out = []
    for b in gm["bones"]:
        if b["name"] == "Head":
            b = copy.deepcopy(b)
            b.pop("cubes", None)  # Ultron's own head is drawn there (ultron_head.geo.json)
            out.append(b)
        elif under_head(b["name"]):
            drop.add(b["name"])
        else:
            out.append(b)
    gm["bones"] = out
    json.dump(geo, open(R + "geo/ultron.geo.json", "w"), indent=1)

    src = Image.open(R + "textures/lex/mark6.png").convert("RGBA")
    px = src.load()
    for y in range(src.height):
        for x in range(src.width):
            r, gg, b, a = px[x, y]
            if a == 0:
                continue
            h, s, v = colorsys.rgb_to_hsv(r / 255.0, gg / 255.0, b / 255.0)
            if s < 0.18:
                continue  # already grey (joints, metal)
            if 0.2 < h < 0.45:
                # Lex green -> polished silver
                k = min(1.0, 0.2 + v * 1.4)
                c = (int(146 * k), int(150 * k), int(162 * k))
            else:
                # LexCorp purple -> dark gunmetal
                k = min(1.0, 0.3 + v * 1.1)
                c = (int(70 * k), int(72 * k), int(80 * k))
            px[x, y] = c + (a,)
    src.save(R + "textures/entity/ultron.png", optimize=True)

    glow = Image.open(R + "textures/lex/mark6_glow.png").convert("RGBA")
    gp = glow.load()
    for y in range(glow.height):
        for x in range(glow.width):
            r, gg, b, a = gp[x, y]
            if a:
                gp[x, y] = (255, 46, 32, a)
    glow.save(R + "textures/entity/ultron_glow.png", optimize=True)
    print("body: dropped", sorted(drop))


# ----------------------------------------------------------------------------------
# the head
# ----------------------------------------------------------------------------------

SILVER = (132, 136, 148)
DARKS = (62, 64, 72)


def metal(base=SILVER, grooves=True):
    def f(face, x, y, w, h):
        k = noise() * 0.97
        if face == "top":
            k *= 1.12
        elif face == "bottom":
            k *= 0.6
        else:
            k *= 0.88 + 0.24 * math.sin(math.pi * (x + 0.2) / max(0.5, w)) - 0.1 * (y / max(1.0, h))
        if grooves and face not in ("top", "bottom") and h > 2.5 and int(y * 3) % 5 == 0:
            k *= 0.78
        if face not in ("top", "bottom") and w > 1.2 and (x < 0.3 or x > w - 0.3):
            k *= 0.7
        return shade(base, k)
    return f


def grille():
    """The mouth: dark slots between silver teeth."""
    def f(face, x, y, w, h):
        if face != "north":
            return shade(DARKS, noise())
        if int(x * 3.0) % 2 == 0 and 0.2 < y < h - 0.2:
            return shade((182, 186, 198), noise() * (1.0 - 0.3 * y / h))
        return shade((22, 22, 26), noise())
    return f


def eye():
    """A red eye with Ultron's flower: a white-hot centre, red petals, a dark ring."""
    def f(face, x, y, w, h):
        if face != "north":
            return shade((40, 10, 10), noise())
        cx, cy = w / 2.0, h / 2.0
        dx, dy = (x - cx) / (w / 2.0), (y - cy) / (h / 2.0)
        r = math.hypot(dx, dy)
        a = math.atan2(dy, dx)
        petal = 0.55 + 0.25 * math.cos(6 * a)
        if r < 0.22:
            return (255, 246, 236, 255)
        if r < petal:
            return (255, 170, 150, 255) if r < petal * 0.7 else (255, 60, 40, 255)
        if r < 1.0:
            return (190, 16, 12, 255)
        return (60, 6, 6, 255)
    return f


RED = v3.lightglow((235, 26, 18), core=(255, 120, 100))


def head():
    g.S = 4
    m = g.Model("ultron_head", 96, 96)
    S = metal()
    SL = metal((176, 180, 192), grooves=False)
    D = metal(DARKS, grooves=False)
    G = grille()
    E = eye()

    hd = m.bone("Head", (0, 24, 0))
    # the skull: a block with its upper edges cut, a ridge down the middle
    m.cube(hd, (-4.2, 25.6, -4.3), (8.4, 6.6, 8.6), S)
    m.cube(hd, (-3.6, 32.0, -3.8), (7.2, 1.0, 7.8), S)
    for s in (-1, 1):
        m.cube(hd, (s * 3.9 - 0.7, 31.0, -4.0), (1.4, 1.4, 8.0), S, rotation=(0, 0, s * -40), pivot=(s * 3.9, 31.7, 0))
    m.cube(hd, (-0.9, 32.6, -4.6), (1.8, 0.8, 9.0), SL)                    # the ridge
    m.cube(hd, (-0.9, 27.0, -4.75), (1.8, 6.2, 0.6), SL)                   # ... down the forehead
    # the face: brow, cheeks falling to a narrow jaw
    for s in (-1, 1):
        m.cube(hd, (s * 2.0 - 1.9, 29.9, -4.85), (3.8, 0.9, 0.9), SL, rotation=(0, 0, s * -10),
               pivot=(s * 2.0, 30.3, -4.4))                                # brow
        m.cube(hd, (s * 3.1 - 1.2, 25.0, -4.6), (2.4, 3.6, 1.2), S, rotation=(0, s * 14, s * 10),
               pivot=(s * 3.1, 26.8, -4))                                  # cheekbones
        m.cube(hd, (s * 2.6 - 0.6, 23.6, -4.1), (1.2, 2.4, 3.6), S, rotation=(0, 0, s * 18),
               pivot=(s * 2.6, 25, -2.3))                                  # jaw sides
        m.cube(hd, (s * 2.0 - 1.1, 28.05, -4.55), (2.2, 1.7, 0.4), D)       # eye socket
        m.cube(hd, (s * 2.0 - 0.75, 28.25, -4.7), (1.5, 1.3, 0.3), E)       # the eye (its flower)
        # the blades at the sides of the head, swept back
        m.cube(hd, (s * 4.35 - 0.3, 24.2, -3.0), (0.6, 6.0, 2.2), SL, rotation=(-16, s * 22, 0),
               pivot=(s * 4.3, 27, -2))
        m.cube(hd, (s * 4.2 - 0.6, 26.0, -1.4), (1.2, 2.6, 4.2), D)          # temple
    m.cube(hd, (-0.6, 26.6, -4.75), (1.2, 1.6, 0.6), SL)                   # nose bridge
    m.cube(hd, (-2.0, 23.6, -4.45), (4.0, 2.6, 0.7), G)                   # mouth grille
    m.cube(hd, (-1.6, 23.0, -3.9), (3.2, 0.8, 2.6), S)                     # chin
    m.cube(hd, (-1.8, 22.8, -1.8), (3.6, 1.4, 3.6), D)                     # neck
    m.cube(hd, (-3.2, 25.4, 4.2), (6.4, 6.0, 0.4), S)                      # back plate
    # the red lines over the head
    ln = m.bone("glow_lines", (0, 28, 0), parent="Head")
    for s in (-1, 1):
        m.cube(ln, (s * 1.45 - 0.12, 30.6, -4.62), (0.24, 2.0, 0.2), RED)        # up the forehead
        m.cube(ln, (s * 1.45 - 0.12, 32.85, -4.2), (0.24, 0.2, 8.0), RED)        # over the skull
        m.cube(ln, (s * 3.35 - 0.12, 25.8, -4.65), (0.24, 2.0, 0.2), RED, rotation=(0, 0, s * 14),
               pivot=(s * 3.35, 26.8, -4.55))                                    # down the cheeks
        m.cube(ln, (s * 4.32 - 0.1, 27.2, -3.8), (0.2, 0.24, 6.5), RED)          # along the sides
        m.cube(ln, (s * 2.0 - 1.25, 27.95, -4.6), (2.5, 0.2, 0.2), RED)          # under the eyes
    m.cube(ln, (-1.6, 24.75, -4.52), (3.2, 0.22, 0.2), RED)                      # the mouth's slit
    gl = m.bone("glow_eyes", (0, 29, -4.6), parent="Head")
    for s in (-1, 1):
        m.cube(gl, (s * 2.0 - 0.75, 28.25, -4.72), (1.5, 1.3, 0.05), E)
    m.save()
    g.S = 2


if __name__ == "__main__":
    body()
    # (the head is tools/gen_ultron_head.py; head() above was its first try)
    print("ok")
