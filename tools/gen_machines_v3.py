"""Machines v3, made from scratch: the Flash's Transformation Chamber and the Hulk's Gamma Chamber.

Both are geo models (read by GeoModel / GeoAnimation and drawn by block entity renderers) with a painted
box-UV texture. Bones starting with "glow" are drawn full-bright, bones starting with "glass" last and see-through.

Transformation Chamber: written here in the world frame of the machine facing south (x east, y up, z south = the
open front, pixels, origin on the cabin floor centre at the level of the controller block's bottom); saved
mirrored on x, as the renderer draws geo models (it flips x back).

usage (from the repo root): python3 tools/gen_machines_v3.py
"""
import math
import os
import random
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen_geo_machines as g  # noqa: E402
from PIL import Image  # noqa: E402

random.seed(11)
shade = g.shade
noise = g.noise
clamp = g.clamp
SQ3 = math.sqrt(3.0)


# ----------------------------------------------------------------------------------
# materials
# ----------------------------------------------------------------------------------

def plate(base, seams=6.0, rivets=True, trim=None, sheen=0.0):
    """Painted metal: panel seams, edge shadow, rivets, an optional trim stripe along the top edge."""
    def f(face, x, y, w, h):
        side = face not in ("top", "bottom")
        k = noise()
        if face == "top":
            k *= 1.1
        elif face == "bottom":
            k *= 0.65
        else:
            k *= 1.04 - 0.22 * (y / max(1.0, h))
            if sheen:
                k *= 1.0 + sheen * math.sin(math.pi * (x + 0.25) / max(0.5, w))
        if side and (x < 0.5 or x > w - 0.5) and w > 1.5:
            k *= 0.72
        if side and (y < 0.5 or y > h - 0.5) and h > 1.5:
            k *= 0.8
        if seams and side and h > seams + 1 and 0.5 < y < h - 0.5 and (y % seams) < 0.5:
            k *= 0.62
        if rivets and side and w >= 3 and h >= 3:
            if (abs(x - 0.75) < 0.5 or abs(x - (w - 0.75)) < 0.5) and (abs(y - 0.75) < 0.5 or abs(y - (h - 0.75)) < 0.5):
                return shade(base, 1.45)
        if trim and side and y < 0.5:
            return shade(trim, noise())
        return shade(base, k)
    return f


def hazard(a=(240, 196, 30), b=(26, 26, 28), period=2.0):
    def f(face, x, y, w, h):
        if face in ("top", "bottom"):
            return shade(b, noise() * (1.1 if face == "top" else 0.7))
        stripe = int((x + y) / period) % 2 == 0
        k = noise() * (0.8 if (y < 0.5 or y > h - 0.5) else 1.0)
        return shade(a if stripe else b, k)
    return f


def lightglow(rgb, core=(255, 250, 220), alpha=240):
    """A lit strip: bright core along the long axis."""
    def f(face, x, y, w, h):
        if w >= h:
            t = 1.0 - abs(y - h / 2.0) / max(0.5, h / 2.0)
        else:
            t = 1.0 - abs(x - w / 2.0) / max(0.5, w / 2.0)
        t = max(0.0, t)
        col = tuple(rgb[i] + (core[i] - rgb[i]) * t * 0.85 for i in range(3))
        return shade(col, 0.92 + random.random() * 0.12, alpha)
    return f


def beam(rgb, alpha=120):
    def f(face, x, y, w, h):
        t = 1.0 - abs(x - w / 2.0) / max(0.5, w / 2.0)
        a = int(alpha * (0.35 + 0.65 * max(0.0, t)))
        streak = (y * 0.7 + x * 3.0) % 5.0 < 0.8
        col = (255, 255, 235) if streak else rgb
        return shade(col, 1.0, min(255, a + (60 if streak else 0)))
    return f


def trefoil(base=(20, 24, 20), sign=(80, 255, 90), face_only=("top",)):
    """Radiation sign on the given faces."""
    def f(face, x, y, w, h):
        if face not in face_only:
            return shade(base, noise())
        cx, cy = w / 2.0, h / 2.0
        r = math.hypot(x - cx, y - cy) / (min(w, h) / 2.0)
        a = math.degrees(math.atan2(y - cy, x - cx)) % 120.0
        if r < 0.18 or (0.3 < r < 0.85 and 30 < a < 90):
            return shade(sign, 0.95 + random.random() * 0.1)
        if 0.92 < r < 1.0:
            return shade(sign, 0.8)
        return shade(base, noise())
    return f


def glassmat(tint, alpha=60):
    def f(face, x, y, w, h):
        edge = x < 0.4 or x > w - 0.4 or y < 0.4 or y > h - 0.4
        streak = abs((x - y * 0.4) % 8.0 - 2.0) < 0.5
        if edge:
            return shade(tint, 0.85, 170)
        if streak:
            return shade((225, 255, 230), 1.0, 110)
        hexes = ((int(y / 1.5) % 2) * 0.75 + x) % 1.5 < 0.12 or (y % 1.5) < 0.12
        return shade(tint, noise(), alpha + (35 if hexes else 0))
    return f


def screen(rgb=(60, 255, 90)):
    def f(face, x, y, w, h):
        if face in ("north", "south") and 0.5 <= x < w - 0.5 and 0.5 <= y < h - 0.5:
            line = int(y * 2) % 2 == 0 and random.random() < 0.7
            return shade(rgb, 1.0) if line else shade(rgb, 0.22)
        return (20, 24, 28, 255)
    return f


# ----------------------------------------------------------------------------------
# shapes
# ----------------------------------------------------------------------------------

def hexprism(m, bone, a, y, h, mat, rot=0.0, cx=0.0, cz=0.0):
    """Hexagonal prism, apothem a (flat side towards z at rot 0)."""
    s = 2.0 * a / SQ3
    for k in range(3):
        m.cube(bone, (cx - s / 2, y, cz - a), (s, h, 2 * a), mat, rotation=(0, rot + 60 * k, 0), pivot=(cx, 0, cz))


def ngon_ring(m, bone, n, a, y, h, t, mat, rot=0.0, skip=(), width_k=1.0):
    """Ring of n flat panels at apothem a (inner face), thickness t outwards."""
    w = 2.0 * (a + t) * math.tan(math.pi / n) * width_k
    for k in range(n):
        if k in skip:
            continue
        m.cube(bone, (-w / 2, y, a), (w, h, t), mat, rotation=(0, rot + 360.0 / n * k, 0), pivot=(0, 0, 0))


def ngon_disc(m, bone, n, a, y, h, mat, rot=0.0):
    """Filled n-gon (n even), apothem a: n/2 boxes."""
    s = 2.0 * a * math.tan(math.pi / n)
    for k in range(n // 2):
        m.cube(bone, (-s / 2, y, -a), (s, h, 2 * a), mat, rotation=(0, rot + 360.0 / n * k, 0), pivot=(0, 0, 0))


# ----------------------------------------------------------------------------------
# Gamma Chamber v4 (Hulk): hexagonal gamma containment cell
# ----------------------------------------------------------------------------------

def gamma():
    m = g.Model("gamma_chamber", 256, 320)
    ARMOR = plate((74, 84, 66), seams=7.0)
    DARK = plate((38, 42, 44), seams=0, rivets=True)
    STEEL = plate((132, 138, 140), seams=5.0, rivets=False, sheen=0.15)
    HAZ = hazard()
    GLASS = glassmat((80, 235, 100))
    GREEN = lightglow((50, 235, 80), core=(220, 255, 220))
    BEAMM = beam((70, 255, 100), alpha=165)
    PAD = trefoil()
    SIGN = trefoil(base=(30, 34, 30), face_only=("north",))

    frame = m.bone("frame")
    # base: armoured hexagon, hazard band, steel floor ring
    hexprism(m, frame, 9.0, 0, 2.5, DARK)
    hexprism(m, frame, 8.6, 2.5, 1.5, HAZ)
    ngon_ring(m, frame, 6, 5.2, 4, 0.6, 2.2, STEEL)
    # front console with the capsule gauge
    m.cube(frame, (-3.5, 0, -11.2), (7, 6, 2.6), DARK)
    m.cube(frame, (-3, 6, -10.6), (6, 1.2, 2), STEEL, rotation=(-25, 0, 0), pivot=(0, 6, -10))
    # six struts at the corners, with feet and heads
    R = 9.0 / math.cos(math.radians(30))
    for k in range(6):
        a = 30 + 60 * k
        m.cube(frame, (-1.3, 2.5, R - 2.0), (2.6, 42, 2.6), ARMOR, rotation=(0, a, 0), pivot=(0, 0, 0))
        m.cube(frame, (-1.8, 2.5, R - 2.5), (3.6, 4, 3.6), DARK, rotation=(0, a, 0), pivot=(0, 0, 0))
        m.cube(frame, (-1.8, 40.5, R - 2.5), (3.6, 4, 3.6), DARK, rotation=(0, a, 0), pivot=(0, 0, 0))
        # hose down the back three struts
        if k in (2, 3, 4):
            m.cube(frame, (-0.6, 8, R + 0.6), (1.2, 32, 1.2), DARK, rotation=(0, a, 0), pivot=(0, 0, 0))
    # mid containment collar
    ngon_ring(m, frame, 6, 7.9, 22, 3, 1.3, DARK)
    # head: hexagon, hazard band, steel deck
    hexprism(m, frame, 9.0, 44, 3, DARK)
    hexprism(m, frame, 8.6, 47, 1.2, HAZ)
    hexprism(m, frame, 7.2, 48.2, 1.6, STEEL)
    # radiation sign on the front of the head
    m.cube(frame, (-2.5, 44.3, -9.4), (5, 2.6, 0.5), SIGN)
    # capsule column and its cage
    hexprism(m, frame, 2.4, 49.8, 3.2, STEEL)
    for k in range(3):
        m.cube(frame, (-0.35, 53, 1.9), (0.7, 4.6, 0.7), DARK, rotation=(0, 120 * k, 0), pivot=(0, 0, 0))
    hexprism(m, frame, 2.2, 57.4, 0.8, DARK)

    fins = m.bone("fins", (0, 50, 0))
    for k in range(6):
        m.cube(fins, (-0.5, 49.8, 3.0), (1.0, 3.6, 4.6), ARMOR, rotation=(0, 60 * k, 0), pivot=(0, 0, 0))
        m.cube(fins, (-0.7, 53.2, 5.5), (1.4, 0.8, 2.4), DARK, rotation=(0, 60 * k, 0), pivot=(0, 0, 0))

    glass = m.bone("glass_cell")
    s = 2.0 * 7.7 / SQ3
    for k in range(6):
        m.cube(glass, (-s / 2 + 0.9, 4.0, 7.2), (s - 1.8, 40.5, 0.5), GLASS, rotation=(0, 60 * k, 0), pivot=(0, 0, 0))

    pad = m.bone("glow_pad", (0, 4, 0))
    hexprism(m, pad, 4.8, 4.0, 0.6, PAD)
    vents = m.bone("glow_vents", (0, 23.5, 0))
    for k in range(6):
        m.cube(vents, (-2.5, 23.0, 9.2), (5, 1, 0.3), GREEN, rotation=(0, 60 * k, 0), pivot=(0, 0, 0))
        m.cube(vents, (-3.3, 45.0, 9.05), (6.6, 0.8, 0.3), GREEN, rotation=(0, 60 * k, 0), pivot=(0, 0, 0))
    scr = m.bone("glow_screen", (0, 4, -11))
    m.cube(scr, (-2.5, 1.5, -11.5), (5, 3.2, 0.4), screen())
    m.cube(scr, (-2.4, 6.4, -10.5), (1, 0.6, 1), GREEN)
    m.cube(scr, (1.4, 6.4, -10.5), (1, 0.6, 1), GREEN)
    emitter = m.bone("glow_emitter", (0, 42, 0))
    for i, (a, y) in enumerate(((4.4, 43.2), (3.3, 42.2), (2.2, 41.2), (1.1, 40.2))):
        hexprism(m, emitter, a, y, 1.0, GREEN)
    for name, y in (("glow_ring_low", 12), ("glow_ring_high", 32)):
        b = m.bone(name, (0, y, 0))
        ngon_ring(m, b, 6, 6.4, y, 0.8, 0.5, GREEN, rot=30)
    bm = m.bone("glow_beam", (0, 4.6, 0))
    hexprism(m, bm, 2.0, 4.6, 35.6, BEAMM)
    hexprism(m, bm, 1.0, 4.6, 35.6, BEAMM, rot=30)
    cap = m.bone("glow_capsule", (0, 53, 0))
    hexprism(m, cap, 1.1, 53.0, 4.4, GREEN)
    m.save()

    keys = g.keys
    wave, spin = g.wave, g.spin
    hidden = keys([(0, [0, 1, 0])])
    g.save_anims("gamma_chamber", {
        "idle": (6.0, {
            "fins": {"rotation": spin(6.0, 0.25)},
            "glow_ring_low": {"position": wave(6.0, 1.5), "rotation": spin(6.0, 1.0 / 6)},
            "glow_ring_high": {"position": wave(6.0, 1.5, phase=math.pi), "rotation": spin(6.0, -1.0 / 6)},
            "glow_beam": {"scale": hidden},
            "glow_emitter": {"scale": wave(6.0, 0.05, base=(1, 1, 1), axis=0)},
        }),
        "loaded": (2.0, {
            "fins": {"rotation": spin(2.0, 0.5)},
            "glow_ring_low": {"position": wave(2.0, 4.0), "rotation": spin(2.0, 1.0 / 3)},
            "glow_ring_high": {"position": wave(2.0, 4.0, phase=math.pi), "rotation": spin(2.0, -1.0 / 3)},
            "glow_beam": {"scale": keys([(0, [0.2, 1, 0.2]), (1.0, [0.35, 1, 0.35]), (2.0, [0.2, 1, 0.2])])},
            "glow_capsule": {"scale": keys([(0, [1, 1, 1]), (1.0, [1.3, 1.04, 1.3]), (2.0, [1, 1, 1])])},
        }),
        "active": (1.0, {
            "fins": {"rotation": spin(1.0, 2.0)},
            "glow_ring_low": {"position": keys([(0, [0, 0, 0]), (0.5, [0, 26, 0]), (1.0, [0, 0, 0])]),
                              "rotation": spin(1.0, 1.0)},
            "glow_ring_high": {"position": keys([(0, [0, 0, 0]), (0.5, [0, -24, 0]), (1.0, [0, 0, 0])]),
                               "rotation": spin(1.0, -1.0)},
            "glow_beam": {"scale": keys([(0, [1.0, 1, 1.0]), (0.25, [1.8, 1, 1.8]), (0.5, [1.1, 1, 1.1]),
                                         (0.75, [2.0, 1, 2.0]), (1.0, [1.0, 1, 1.0])])},
            "glow_capsule": {"scale": keys([(0, [1, 1, 1]), (0.5, [1.4, 0.9, 1.4]), (1.0, [1, 1, 1])])},
            "glow_pad": {"scale": keys([(0, [1, 1, 1]), (0.5, [1.15, 1, 1.15]), (1.0, [1, 1, 1])])},
            "glow_emitter": {"scale": keys([(0, [1, 1, 1]), (0.5, [1.2, 1.1, 1.2]), (1.0, [1, 1, 1])])},
        }),
    })


# ----------------------------------------------------------------------------------
# Transformation Chamber v10 (Flash): Speed Force capsule
# ----------------------------------------------------------------------------------

class World:
    """Cubes given in the machine's world frame (x east), saved mirrored on x for the geo renderer."""

    def __init__(self, m):
        self.m = m

    def bone(self, name, pivot=(0, 0, 0), parent=None):
        return self.m.bone(name, (-pivot[0], pivot[1], pivot[2]), parent=parent)

    def cube(self, bone, origin, size, mat, rot=None, pivot=(0, 0, 0)):
        x, y, z = origin
        sx = size[0]
        if rot:
            self.m.cube(bone, (-(x + sx), y, z), size, mat, rotation=(-rot[0], -rot[1], rot[2]),
                        pivot=(-pivot[0], pivot[1], pivot[2]))
        else:
            self.m.cube(bone, (-(x + sx), y, z), size, mat)

    def bar_xy(self, bone, p0, p1, width, depth, z, mat):
        """A flat bar from p0 to p1 (x, y) in the plane z .. z + depth."""
        dx, dy = p1[0] - p0[0], p1[1] - p0[1]
        L = math.hypot(dx, dy) + width * 0.6
        mx, my = (p0[0] + p1[0]) / 2, (p0[1] + p1[1]) / 2
        ang = math.degrees(math.atan2(dy, dx))
        self.cube(bone, (mx - L / 2, my - width / 2, z), (L, width, depth), mat, rot=(0, 0, ang), pivot=(mx, my, z))

    def bar_xz(self, bone, p0, p1, width, y, h, mat):
        """A flat bar from p0 to p1 (x, z) lying at height y .. y + h."""
        dx, dz = p1[0] - p0[0], p1[1] - p0[1]
        L = math.hypot(dx, dz) + width * 0.6
        mx, mz = (p0[0] + p1[0]) / 2, (p0[1] + p1[1]) / 2
        ang = math.degrees(math.atan2(-dz, dx))
        self.cube(bone, (mx - L / 2, y, mz - width / 2), (L, h, width), mat, rot=(0, ang, 0), pivot=(mx, y, mz))

    def bolt_xy(self, bone, cx, cy, size, width, z, depth, mat, flip=False):
        pts = [(0.45, 1.0), (-0.45, 0.08), (0.45, 0.2), (-0.45, -1.0)]
        f = -1 if flip else 1
        pts = [(cx + f * px * size, cy + py * size) for px, py in pts]
        for a, b in zip(pts, pts[1:]):
            self.bar_xy(bone, a, b, width, depth, z, mat)

    # shapes around a vertical axis through (cx, cz)
    def ring(self, bone, n, a, y, h, t, mat, rot=0.0, keep=None, cx=0.0, cz=0.0, width_k=1.0):
        w = 2.0 * (a + t) * math.tan(math.pi / n) * width_k
        for k in range(n):
            ang = rot + 360.0 / n * k
            if keep and not keep(((ang + 180) % 360) - 180):
                continue
            self.cube(bone, (cx - w / 2, y, cz + a), (w, h, t), mat, rot=(0, ang, 0), pivot=(cx, 0, cz))

    def disc(self, bone, n, a, y, h, mat, rot=0.0, cx=0.0, cz=0.0):
        s = 2.0 * a * math.tan(math.pi / n)
        for k in range(n // 2):
            self.cube(bone, (cx - s / 2, y, cz - a), (s, h, 2 * a), mat, rot=(0, rot + 360.0 / n * k, 0),
                      pivot=(cx, 0, cz))


def flash():
    g.S = 1  # one texture pixel per model pixel, like the vanilla blocks
    m = g.Model("transformation_chamber", 1024, 640)
    w = World(m)
    RED = plate((150, 22, 28), seams=8.0, sheen=0.12)
    REDD = plate((92, 12, 18), seams=6.0)
    GRAPH = plate((34, 34, 40), seams=0, rivets=True)
    GOLD = plate((236, 170, 36), seams=0, rivets=False, sheen=0.2)
    CHROME = plate((176, 182, 192), seams=0, rivets=False, sheen=0.3)
    HAZ = hazard((245, 200, 40), (30, 28, 30), period=3.0)
    YEL = lightglow((255, 196, 30), core=(255, 255, 230))
    BOLT = lightglow((255, 214, 40), core=(255, 255, 255), alpha=250)
    BEAMM = beam((255, 210, 60), alpha=110)

    frame = w.bone("frame")
    # platform: wide dodecagon, gold trim, raised cabin deck (top at 8 px = the cabin floor)
    w.disc(frame, 12, 29.0, 0, 4, GRAPH, rot=15)
    w.ring(frame, 12, 27.6, 3.4, 0.8, 1.6, GOLD, rot=15)
    w.disc(frame, 12, 21.5, 4, 4, REDD, rot=15)
    w.ring(frame, 12, 20.6, 7.4, 0.8, 1.2, GOLD, rot=15)
    # entrance ramp at the front
    w.cube(frame, (-9, 4, 21), (18, 2.2, 6), HAZ)
    w.cube(frame, (-9, 0, 27), (18, 4, 2.5), GRAPH)

    # cabin shell: back and sides of a 12-gon, open front
    back = lambda a: abs(a) > 50  # noqa: E731
    w.ring(frame, 12, 20.0, 8, 54, 3.2, RED, keep=back)
    w.ring(frame, 12, 23.0, 8, 2, 1.2, GRAPH, keep=back)
    w.ring(frame, 12, 23.0, 36, 2, 1.2, GRAPH, keep=back)
    w.ring(frame, 12, 23.0, 60, 2, 1.2, GRAPH, keep=back)
    # front pillars where the shell ends
    for sgn in (-1, 1):
        a = math.radians(45 * sgn)
        px, pz = 22.0 * math.sin(a), 22.0 * math.cos(a)
        w.cube(frame, (px - 2.2, 4, pz - 2.2), (4.4, 64, 4.4), CHROME, rot=(0, 45 * sgn, 0), pivot=(px, 0, pz))
        w.cube(frame, (px - 2.8, 4, pz - 2.8), (5.6, 5, 5.6), GRAPH, rot=(0, 45 * sgn, 0), pivot=(px, 0, pz))
        w.cube(frame, (px - 2.8, 63, pz - 2.8), (5.6, 5, 5.6), GRAPH, rot=(0, 45 * sgn, 0), pivot=(px, 0, pz))
    # crown: ring, gold trim, stepped dome, emitter and the lightning rod
    w.disc(frame, 12, 24.0, 62, 6, GRAPH, rot=15)
    w.ring(frame, 12, 22.8, 67.2, 1, 1.6, GOLD, rot=15)
    w.disc(frame, 12, 18.0, 68, 4, RED, rot=15)
    w.disc(frame, 12, 12.0, 72, 4, REDD, rot=15)
    w.disc(frame, 8, 5.0, 76, 6, CHROME, rot=22.5)
    w.ring(frame, 8, 5.0, 79, 1, 0.8, GOLD, rot=22.5)
    w.cube(frame, (-0.8, 82, -0.8), (1.6, 4, 1.6), CHROME)
    # back capacitor towers
    for sgn in (-1, 1):
        cx, cz = 22.5 * sgn, -23.5
        w.cube(frame, (cx - 5, 0, cz - 5), (10, 6, 10), GRAPH)
        w.disc(frame, 8, 3.2, 6, 50, CHROME, rot=22.5, cx=cx, cz=cz)
        for i in range(6):
            w.ring(frame, 8, 3.2, 10 + i * 7.5, 2.2, 1.0, REDD, rot=22.5, cx=cx, cz=cz)
        w.disc(frame, 8, 4.2, 56, 3, GRAPH, rot=22.5, cx=cx, cz=cz)
        w.cube(frame, (cx - 1, 59, cz - 1), (2, 4, 2), GOLD)
        # cables into the shell
        w.cube(frame, (cx - 1 if sgn > 0 else cx - 0.5, 44, cz + 3), (1.5, 1.5, 6), GRAPH)
    # side port for the potion container (model x 0..0.3, z -0.22..0.67)
    w.cube(frame, (-32, 0, -4.3), (5, 30, 14), GRAPH)
    w.cube(frame, (-31.6, 6, -3.6), (4.2, 18, 12.6), REDD)
    w.cube(frame, (-27, 16, -1.5), (5.5, 5, 6), CHROME)

    deco = w.bone("glow_strips")
    # light slits up the inner face of the shell
    w.ring(deco, 12, 19.7, 12, 46, 0.3, YEL, keep=back, width_k=0.12)
    # gold lightning bolt on the back wall (inner face)
    w.bolt_xy(deco, 0, 36, 15, 2.6, -19.7, 0.5, BOLT, flip=True)
    # tower coils, port light
    for sgn in (-1, 1):
        cx, cz = 22.5 * sgn, -23.5
        for i in range(6):
            w.ring(deco, 8, 3.7, 13.5 + i * 7.5, 1.0, 0.4, YEL, rot=22.5, cx=cx, cz=cz)
    w.cube(deco, (-27.3, 8, -2), (0.4, 14, 9.6), YEL)
    # outside: lit seams between the shell panels and a big bolt on the back
    w.ring(deco, 12, 23.2, 12, 46, 0.3, YEL, rot=15, keep=lambda a: abs(a) > 60, width_k=0.08)
    w.bolt_xy(deco, 0, 36, 19, 3.2, -23.85, 0.5, BOLT)

    floor = w.bone("glow_floor", (0, 8, 0))
    # the floor emblem: a ring and a bolt
    w.ring(floor, 16, 14.0, 8.0, 0.25, 1.4, YEL)
    pts = [(5.0, -11.0), (-5.0, 0.8), (5.0, -0.8), (-5.0, 11.0)]
    for p0, p1 in zip(pts, pts[1:]):
        w.bar_xz(floor, p0, p1, 2.6, 8.0, 0.3, BOLT)

    emitter = w.bone("glow_emitter", (0, 61, 0))
    w.disc(emitter, 8, 7.0, 60.0, 1.6, YEL, rot=22.5)
    w.disc(emitter, 8, 4.0, 58.6, 1.4, BOLT, rot=22.5)

    for name, y in (("ring_high", 48), ("ring_low", 42)):
        rb = w.bone(name, (0, y, 0))
        w.ring(rb, 16, 15.5, y, 2.2, 1.6, CHROME)
        gl = w.bone("glow_" + name, (0, y, 0), parent=name)
        w.ring(gl, 16, 15.2, y + 0.5, 1.2, 0.3, YEL)

    bm = w.bone("glow_beam", (0, 8, 0))
    w.disc(bm, 8, 5.5, 8.2, 50, BEAMM, rot=22.5)
    m.save()

    keys, wave, spin = g.keys, g.wave, g.spin
    hidden = keys([(0, [0, 1, 0])])
    g.save_anims("transformation_chamber", {
        "idle": (8.0, {
            "ring_high": {"position": wave(8.0, 1.0), "rotation": spin(8.0, 0.25)},
            "ring_low": {"position": wave(8.0, 1.0, phase=math.pi), "rotation": spin(8.0, -0.25)},
            "glow_beam": {"scale": hidden},
            "glow_floor": {"scale": wave(8.0, 0.03, base=(1, 1, 1), axis=0)},
        }),
        "active": (1.6, {
            "ring_high": {"position": keys([(0, [0, 0, 0]), (0.8, [0, -38, 0]), (1.6, [0, 0, 0])]),
                          "rotation": spin(1.6, 3.0)},
            "ring_low": {"position": keys([(0, [0, 0, 0]), (0.4, [0, -32, 0]), (1.2, [0, 4, 0]), (1.6, [0, 0, 0])]),
                         "rotation": spin(1.6, -3.0)},
            "glow_beam": {"scale": keys([(0, [0.8, 1, 0.8]), (0.4, [1.3, 1, 1.3]), (0.8, [0.9, 1, 0.9]),
                                         (1.2, [1.4, 1, 1.4]), (1.6, [0.8, 1, 0.8])])},
            "glow_emitter": {"scale": keys([(0, [1, 1, 1]), (0.4, [1.2, 1, 1.2]), (0.8, [1, 1, 1]),
                                            (1.2, [1.25, 1, 1.25]), (1.6, [1, 1, 1])])},
            "glow_floor": {"scale": keys([(0, [1, 1, 1]), (0.8, [1.06, 1, 1.06]), (1.6, [1, 1, 1])])},
        }),
    })


if __name__ == "__main__":
    g.S = 2
    gamma()
    flash()
    print("ok")
