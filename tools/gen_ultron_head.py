"""Ultron's head v3, after the Age of Ultron close-up: a bright polished chrome skull made of plates, a domed crown
with a ridge, red light glowing in the seams of the forehead; small slanted red eyes deep under a heavy brow; a long
face narrowing to a pointed chin; at each side of the jaw a big round disc with a red core, and a hooked blade
curling forward from it; a neck of cables.

Painted at 8 texture pixels per model pixel. The red seams are painted twice: on the face and on a thin full-bright
film just over it (bone glow_film), so they shine. Writes geo/ultron_head.geo.json and textures/entity/ultron_head.png
(drawn on the head by UltronLayer).
usage (from the repo root): python3 tools/gen_ultron_head.py
"""
import math
import os
import random
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen_geo_machines as g  # noqa: E402

random.seed(19)
shade = g.shade
noise = g.noise

CHROME = (196, 200, 210)
STEEL = (140, 144, 156)
DEEP = (46, 48, 56)


def seams_at(u, v):
    """The glowing seams of the forehead (u 0..1 across, v 0..1 down the front plate)."""
    d = abs(u - 0.5)
    # a V from the top of the brow ridge up and out, and a second, shorter, inside it
    for off, k in ((0.07, 1.5), (0.045, 0.75)):
        if v < 0.21:
            want = off + k * (0.21 - v)
            if abs(d - want) < 0.014:
                return True
    # short seams under the eyes, slanting down and out
    if 0.6 < v < 0.7 and abs(d - (0.22 + 0.9 * (v - 0.6))) < 0.012:
        return True
    return False


LIGHTS = [(146, 150, 162), (143, 147, 159), (138, 141, 153), (135, 139, 150), (127, 131, 141), (119, 123, 132)]
DARKS = [(107, 110, 119), (89, 91, 99), (70, 72, 80), (65, 66, 74), (57, 59, 65), (46, 47, 52)]


def cell_rand(face, cx, cy, salt):
    h = hash((face, cx, cy, salt)) & 0xFFFF
    return h / 65535.0


_SALT = [0]


def blocky(light=True, rim=True):
    """The Lex suit's painting: one colour per model pixel, picked from its greys, darker at the rims."""
    _SALT[0] += 1
    salt = _SALT[0]

    def f(face, x, y, w, h):
        cx, cy = int(x), int(y)
        r = cell_rand(face, cx, cy, salt)
        pal = LIGHTS if light else DARKS
        i = int(r * len(pal)) % len(pal)
        if face == "bottom":
            i = min(len(pal) - 1, i + 2)
        edge = rim and face not in ("top", "bottom") and (cx == 0 or cx >= int(w - 0.01) or cy >= int(h - 0.01))
        if edge and light:
            return DARKS[min(len(DARKS) - 1, int(r * 3))] + (255,)
        return pal[i] + (255,)
    return f


def chrome(base=CHROME, bands=True):
    """Polished chrome: bright and dark reflections in bands, darker rims."""
    def f(face, x, y, w, h):
        k = noise() * 0.96
        if face == "top":
            k *= 1.12
        elif face == "bottom":
            k *= 0.55
        else:
            t = x / max(0.5, w)
            k *= 0.78 + 0.32 * math.sin(math.pi * t) ** 0.7
            if bands:
                k *= 0.9 + 0.16 * math.sin((y * 1.7 + x * 0.6))
            k *= 1.0 - 0.15 * (y / max(1.0, h))
        if face not in ("top", "bottom") and ((w > 1.0 and (x < 0.12 or x > w - 0.12))
                                             or (h > 1.0 and (y < 0.12 or y > h - 0.12))):
            k *= 0.6
        return shade(base, k)
    return f


def face_paint():
    def f(face, x, y, w, h):
        if face != "north":
            return chrome()(face, x, y, w, h)
        u, v = x / w, y / h
        if seams_at(u, v):
            return (210, 40, 24, 255)
        d = abs(u - 0.5)
        k = noise() * (0.78 + 0.3 * math.cos(d * 5.5)) * (1.0 - 0.18 * v)
        # dark panel seams down the long face
        if v > 0.62 and abs(d - 0.16) < 0.012:
            return shade(DEEP, 1.0)
        return shade(CHROME, k)
    return f


def film():
    def f(face, x, y, w, h):
        if face == "north" and seams_at(x / w, y / h):
            return (255, 90, 50, 255)
        return (0, 0, 0, 0)
    return f


def dark():
    def f(face, x, y, w, h):
        return shade(DEEP, noise() * (0.8 + 0.3 * ((x + y) % 1.0 > 0.5)))
    return f


def disc_face():
    """The side discs: a polished rim, dark rings inside, a red core (painted on their outer faces)."""
    def f(face, x, y, w, h):
        if face not in ("west", "east"):
            return LIGHTS[int(cell_rand(face, int(x), int(y), 99) * 6)] + (255,)
        cx, cy = w / 2.0, h / 2.0
        r = math.hypot(x - cx, y - cy) / (min(w, h) / 2.0)
        if r > 0.82:
            return LIGHTS[int(cell_rand(face, int(x * 2), int(y * 2), 98) * 6)] + (255,)
        if r > 0.7:
            return shade(DEEP, 0.8)
        if r > 0.42:
            ring = int(r * 22) % 2
            return shade((70, 72, 80) if ring else (34, 36, 42), noise())
        if r > 0.3:
            return (120, 14, 10, 255)
        return (255, 70, 40, 255)
    return f


def eye():
    def f(face, x, y, w, h):
        if face != "north":
            return (60, 6, 6, 255)
        cx, cy = w / 2.0, h / 2.0
        r = math.hypot((x - cx) / (w / 2.0), (y - cy) / (h / 2.0))
        if r < 0.3:
            return (255, 235, 200, 255)
        if r < 0.7:
            return (255, 110, 50, 255)
        return (220, 30, 16, 255)
    return f


def core():
    def f(face, x, y, w, h):
        return (255, 80, 45, 255)
    return f


def red_line():
    def f(face, x, y, w, h):
        return (255, 60, 36, 255)
    return f


RED_LINE = red_line()


def head():
    g.S = 8
    m = g.Model("ultron_head", 128, 128)
    C = blocky()
    S = blocky()
    D = blocky(light=False, rim=False)
    FACE = blocky()
    DISC = disc_face()
    E = eye()
    CORE = core()

    hd = m.bone("Head", (0, 24, 0))
    # the domed crown, in layers, its edges cut
    m.cube(hd, (-3.9, 26.2, -3.6), (7.8, 5.6, 7.6), C)
    m.cube(hd, (-3.3, 31.8, -3.2), (6.6, 0.9, 6.9), C)
    m.cube(hd, (-2.3, 32.7, -2.6), (4.6, 0.6, 5.8), C)
    for s in (-1, 1):
        m.cube(hd, (s * 3.55 - 0.6, 30.9, -3.3), (1.2, 1.3, 7.1), C, rotation=(0, 0, s * -40),
               pivot=(s * 3.55, 31.55, 0))
        m.cube(hd, (s * 2.75 - 0.5, 32.1, -2.9), (1.0, 0.9, 6.4), C, rotation=(0, 0, s * -38),
               pivot=(s * 2.75, 32.5, 0))
    m.cube(hd, (-3.3, 31.0, -4.0), (6.6, 1.2, 1.2), C, rotation=(40, 0, 0), pivot=(0, 31.6, -3.4))
    m.cube(hd, (-0.55, 32.9, -3.6), (1.1, 0.7, 7.6), S)                       # the ridge over the crown
    # the back of the head: plates in layers, the ridge running down to the neck, a vent
    m.cube(hd, (-3.0, 26.4, 3.8), (6.0, 5.0, 0.5), S)                         # the back plate
    m.cube(hd, (-2.3, 27.0, 4.2), (4.6, 3.8, 0.45), C)                        # second plate
    for s in (-1, 1):
        m.cube(hd, (s * 2.7 - 0.9, 28.6, 3.6), (1.8, 2.6, 0.7), C, rotation=(0, s * 22, 0),
               pivot=(s * 2.7, 29.9, 4.0))                                  # side plates swept round
        m.cube(hd, (s * 1.6 - 0.35, 25.2, 3.6), (0.7, 1.6, 0.6), D)          # cable sockets
    m.cube(hd, (-0.55, 24.6, 4.5), (1.1, 8.6, 0.5), S)                        # the ridge down the back
    for i in range(3):
        m.cube(hd, (-1.6, 27.4 + i * 0.75, 4.62), (3.2, 0.35, 0.12), D)       # the vent
    m.cube(hd, (-2.6, 23.4, 1.2), (5.2, 1.8, 2.6), S)                         # nape guard
    # the long face (painted), narrowing to the chin
    m.cube(hd, (-3.6, 24.4, -4.1), (7.2, 7.0, 0.5), FACE)
    for s in (-1, 1):
        m.cube(hd, (s * 2.9 - 0.9, 23.2, -3.9), (1.8, 3.6, 4.0), C, rotation=(0, s * 20, s * 12),
               pivot=(s * 2.9, 25, -2))                                     # the jaw falling in
    m.cube(hd, (-1.5, 22.4, -4.15), (3.0, 2.2, 2.6), C)                       # long chin
    m.cube(hd, (-0.8, 21.9, -4.0), (1.6, 0.7, 2.0), S)                        # its point
    # heavy brow over small deep eyes, ridge to a nose plate
    for s in (-1, 1):
        m.cube(hd, (s * 1.7 - 1.7, 28.9, -4.75), (3.4, 0.95, 0.75), C, rotation=(0, s * 6, s * -11),
               pivot=(s * 1.7, 29.35, -4.4))
        m.cube(hd, (s * 1.6 - 0.95, 27.95, -4.3), (1.9, 0.95, 0.25), D, rotation=(0, 0, s * -10),
               pivot=(s * 1.6, 28.4, -4.2))                                  # the socket
        m.cube(hd, (s * 2.6 - 0.8, 26.3, -4.45), (1.6, 0.55, 0.4), S, rotation=(0, s * 12, s * -18),
               pivot=(s * 2.6, 26.7, -4.3))                                  # cheek plate
    m.cube(hd, (-0.45, 26.0, -4.6), (0.9, 3.6, 0.5), C)                       # nose ridge
    m.cube(hd, (-1.4, 24.5, -4.4), (2.8, 0.3, 0.3), D)                        # the mouth's slit
    m.cube(hd, (-1.8, 23.05, -4.3), (0.7, 0.7, 0.4), S)                       # the knob by the chin
    # the side discs and the hooked blades curling forward from them
    for s in (-1, 1):
        xc = s * 4.1
        m.cube(hd, (xc - 0.55, 24.3, -2.0), (1.1, 3.6, 3.6), DISC)
        m.cube(hd, (xc - 0.5, 24.3, -2.0), (1.0, 3.6, 3.6), DISC, rotation=(45, 0, 0), pivot=(xc, 26.1, -0.2))
        m.cube(hd, (xc + s * 0.25 - 0.55, 24.5, -1.8), (1.1, 3.2, 3.2), DISC)
        m.cube(hd, (xc + s * 0.3 - 0.3, 27.4, -0.6), (0.6, 2.6, 0.9), C, rotation=(-18, 0, s * -10),
               pivot=(xc, 27.6, -0.2))                                       # the blade rising
        m.cube(hd, (xc + s * 0.55 - 0.3, 29.4, -3.0), (0.6, 0.8, 3.0), C, rotation=(-22, s * 12, 0),
               pivot=(xc + s * 0.55, 29.8, -1.4))                            # ... turning forward
        m.cube(hd, (xc + s * 0.75 - 0.3, 27.4, -3.6), (0.6, 2.0, 0.7), C, rotation=(14, 0, s * 8),
               pivot=(xc + s * 0.75, 29.2, -3.3))                            # ... and hooking down
    # the neck of cables
    for i, x in enumerate((-1.3, -0.45, 0.45, 1.3)):
        m.cube(hd, (x - 0.35, 22.0, -1.6 + (i % 2) * 0.4), (0.7, 2.4, 0.7), D)
    m.cube(hd, (-1.6, 21.6, -0.6), (3.2, 2.6, 2.6), S)
    lit = m.bone("glow_seams", (0, 28, -4.2), parent="Head")
    for s in (-1, 1):
        # the V of the forehead, two lines each side, and a line under each eye
        for (x0, y0, x1, y1) in ((0.45, 29.9, 2.6, 31.3), (0.35, 30.4, 1.3, 31.35)):
            dx, dy = x1 - x0, y1 - y0
            ln = math.hypot(dx, dy)
            ang = math.degrees(math.atan2(dy, dx))
            mx, my = s * (x0 + x1) / 2.0, (y0 + y1) / 2.0
            m.cube(lit, (mx - ln / 2, my - 0.14, -4.16), (ln, 0.28, 0.05), RED_LINE, rotation=(0, 0, s * ang),
                   pivot=(mx, my, -4.14))
        m.cube(lit, (s * 2.4 - 0.5, 27.4, -4.16), (1.0, 0.24, 0.05), RED_LINE, rotation=(0, 0, s * 30),
               pivot=(s * 2.4, 27.52, -4.14))
    # red seams at the back
    for s in (-1, 1):
        m.cube(lit, (s * 1.9 - 0.12, 26.8, 4.66), (0.24, 3.4, 0.05), RED_LINE, rotation=(0, 0, s * 12),
               pivot=(s * 1.9, 28.5, 4.68))
    m.cube(lit, (-1.5, 30.95, 4.32), (3.0, 0.22, 0.05), RED_LINE)
    # red light along the crown's ridge
    m.cube(lit, (-0.12, 33.6, -3.4), (0.24, 0.05, 7.0), RED_LINE)
    eyes = m.bone("glow_eyes", (0, 28.4, -4.3), parent="Head")
    for s in (-1, 1):
        m.cube(eyes, (s * 1.6 - 0.65, 28.1, -4.36), (1.3, 0.6, 0.1), E, rotation=(0, 0, s * -10),
               pivot=(s * 1.6, 28.4, -4.3))
        m.cube(eyes, (s * 4.1 + s * 0.52 - 0.05, 25.55, -0.75), (0.1, 1.1, 1.1), CORE)
    m.save()
    g.S = 2


if __name__ == "__main__":
    head()
    print("ok")
