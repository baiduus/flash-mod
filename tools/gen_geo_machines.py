"""Geo models (Bedrock format, read by GeoModel / GeoAnimation) for the gamma chamber and
the Acceleration Force portal frame: geometry, animations and a painted box-UV texture.

Bones whose name starts with "glow" are drawn full-bright by the renderers.
usage: python3 tools/gen_geo_machines.py
"""
import json
import math
import random
from PIL import Image

R = "src/main/resources/assets/flash/"
S = 2  # texture pixels per model unit
random.seed(7)


# ----------------------------------------------------------------------------------
# helpers
# ----------------------------------------------------------------------------------

class Model:
    def __init__(self, name, tex_w, tex_h):
        self.name = name
        self.tw = tex_w
        self.th = tex_h
        self.bones = []
        self.cubes = []  # (bone, cube dict, material)
        self.cx = 0
        self.cy = 0
        self.row_h = 0

    def bone(self, name, pivot=(0, 0, 0), parent=None, rotation=None):
        b = {"name": name, "pivot": list(pivot), "cubes": []}
        if parent:
            b["parent"] = parent
        if rotation:
            b["rotation"] = list(rotation)
        self.bones.append(b)
        return b

    def cube(self, bone, origin, size, material, rotation=None, pivot=None, inflate=0.0):
        dx, dy, dz = size
        w = int(math.ceil(2 * (dx + dz)))
        h = int(math.ceil(dz + dy))
        if self.cx + w > self.tw:
            self.cx = 0
            self.cy += self.row_h
            self.row_h = 0
        u, v = self.cx, self.cy
        self.cx += w + 1
        self.row_h = max(self.row_h, h + 1)
        if self.cy + h > self.th:
            raise SystemExit("texture too small for " + self.name)
        c = {"origin": list(origin), "size": list(size), "uv": [u, v]}
        if rotation:
            c["rotation"] = list(rotation)
            c["pivot"] = list(pivot or (0, 0, 0))
        if inflate:
            c["inflate"] = inflate
        bone["cubes"].append(c)
        self.cubes.append((c, material))

    def save(self):
        geo = {"format_version": "1.12.0", "minecraft:geometry": [{
            "description": {"identifier": "geometry." + self.name, "texture_width": self.tw,
                            "texture_height": self.th},
            "bones": [b for b in self.bones]}]}
        for b in geo["minecraft:geometry"][0]["bones"]:
            if not b["cubes"]:
                del b["cubes"]
        json.dump(geo, open(R + "geo/%s.geo.json" % self.name, "w"), indent=1)
        img = Image.new("RGBA", (self.tw * S, self.th * S), (0, 0, 0, 0))
        px = img.load()
        for c, mat in self.cubes:
            dx, dy, dz = c["size"]
            u, v = c["uv"]
            faces = {
                "top": (u + dz, v, dx, dz), "bottom": (u + dz + dx, v, dx, dz),
                "west": (u, v + dz, dz, dy), "north": (u + dz, v + dz, dx, dy),
                "east": (u + dz + dx, v + dz, dz, dy), "south": (u + 2 * dz + dx, v + dz, dx, dy)}
            for face, (fu, fv, fw, fh) in faces.items():
                W = max(1, int(round(fw * S)))
                H = max(1, int(round(fh * S)))
                for yy in range(H):
                    for xx in range(W):
                        X = int(round(fu * S)) + xx
                        Y = int(round(fv * S)) + yy
                        if 0 <= X < img.width and 0 <= Y < img.height:
                            px[X, Y] = mat(face, xx / S, yy / S, fw, fh)
        img.save(R + "textures/entity/%s.png" % self.name, optimize=True)


def clamp(c):
    return max(0, min(255, int(c)))


def shade(rgb, k, a=255):
    return (clamp(rgb[0] * k), clamp(rgb[1] * k), clamp(rgb[2] * k), a)


def noise():
    return 0.93 + random.random() * 0.12


def metal(base, seams=4.0, rivets=True, hazard=False):
    def f(face, x, y, w, h):
        k = noise()
        if face in ("top", "bottom"):
            k *= 1.08 if face == "top" else 0.7
        else:
            k *= 1.0 - 0.25 * (y / max(1.0, h))  # darker towards the bottom
        if face not in ("top", "bottom") and h >= 2 and (x < 0.5 or x > w - 0.5):
            k *= 0.7  # edge shadow
        if seams and face not in ("top", "bottom") and h > seams and (y % seams) < 0.5:
            k *= 0.65
        if rivets and face not in ("top", "bottom") and w >= 4 and h >= 3:
            if (abs(x - 1.0) < 0.5 or abs(x - (w - 1.0)) < 0.5) and (abs(y - 1.0) < 0.5 or abs(y - (h - 1.0)) < 0.5):
                return shade(base, 1.5)
        if hazard and face not in ("top", "bottom") and 1.0 <= y < 2.0:
            stripe = int((x + y) * 1.0) % 2 == 0
            return shade((235, 200, 30) if stripe else (30, 30, 30), noise())
        return shade(base, k)
    return f


def glass(tint):
    def f(face, x, y, w, h):
        edge = x < 0.5 or x > w - 0.5 or y < 0.5 or y > h - 0.5
        streak = abs((x - y * 0.35) % 9.0 - 2.0) < 0.6
        if edge:
            return shade(tint, 0.9, 150)
        if streak:
            return shade((220, 255, 225), 1.0, 105)
        return shade(tint, noise(), 55)
    return f


def glow(rgb, core=(235, 255, 235), alpha=235):
    def f(face, x, y, w, h):
        cx = abs(x - w / 2.0) / max(0.5, w / 2.0)
        cy = abs(y - h / 2.0) / max(0.5, h / 2.0)
        d = max(cx, cy) if face in ("top", "bottom") else cx
        t = max(0.0, 1.0 - d)
        col = tuple(rgb[i] + (core[i] - rgb[i]) * t * 0.8 for i in range(3))
        return shade(col, 0.9 + random.random() * 0.15, alpha)
    return f


def screen():
    def f(face, x, y, w, h):
        if face == "north" and 0.5 <= x < w - 0.5 and 0.5 <= y < h - 0.5:
            line = int(y * 2) % 2 == 0 and random.random() < 0.7
            return (60, 255, 90, 255) if line else (10, 50, 18, 255)
        return (20, 24, 28, 255)
    return f


def keys(times_values):
    return {("%.4f" % t).rstrip("0").rstrip(".") if t else "0.0": {"vector": list(v)} for t, v in times_values}


def save_anims(name, anims):
    out = {"format_version": "1.8.0", "animations": {}}
    for n, (length, bones) in anims.items():
        out["animations"][n] = {"loop": True, "animation_length": length, "bones": bones}
    json.dump(out, open(R + "animations/%s.animation.json" % name, "w"), indent=1)


def wave(length, amp, steps=8, phase=0.0, axis=1, base=(0, 0, 0)):
    out = []
    for i in range(steps + 1):
        t = length * i / steps
        v = list(base)
        v[axis] += amp * math.sin(2 * math.pi * (i / steps) + phase)
        out.append((round(t, 4), [round(c, 3) for c in v]))
    return keys(out)


def spin(length, turns=1.0, axis=1, steps=8):
    out = []
    for i in range(steps + 1):
        v = [0, 0, 0]
        v[axis] = 360.0 * turns * i / steps
        out.append((round(length * i / steps, 4), v))
    return keys(out)


# ----------------------------------------------------------------------------------
# gamma chamber (1 x 1 block footprint, 3 blocks + top tall)
# ----------------------------------------------------------------------------------

def chamber():
    m = Model("gamma_chamber", 256, 256)
    DARK = metal((44, 48, 54))
    MID = metal((112, 118, 126), seams=8.0)
    BASE = metal((52, 56, 62), hazard=True, rivets=False)
    GLASS = glass((90, 230, 110))
    GREEN = glow((40, 210, 70))
    BEAM = glow((60, 255, 90), alpha=170)

    frame = m.bone("frame")
    m.cube(frame, (-8, 0, -8), (16, 3, 16), BASE)
    for o, s in (((-7, 3, -7), (14, 1, 2)), ((-7, 3, 5), (14, 1, 2)), ((-7, 3, -5), (2, 1, 10)), ((5, 3, -5), (2, 1, 10))):
        m.cube(frame, o, s, MID)
    for x in (-8, 6):
        for z in (-8, 6):
            m.cube(frame, (x, 4, z), (2, 40, 2), MID)
            m.cube(frame, (x - 0.25 if x < 0 else x + 0.25, 3, z - 0.25 if z < 0 else z + 0.25), (2, 2, 2), DARK)
            m.cube(frame, (x, 42, z), (2, 2, 2), DARK, inflate=0.25)
    m.cube(frame, (-8, 44, -8), (16, 3, 16), DARK)
    m.cube(frame, (-6, 47, -6), (12, 2, 12), MID)
    m.cube(frame, (-2, 49, -2), (4, 1, 4), DARK)
    # pipes at the back and the control panel at the front
    for x in (-4, 3):
        m.cube(frame, (x, 4, 7), (1, 40, 1), DARK)
    m.cube(frame, (3, 4, -9), (4, 6, 1), DARK)
    m.cube(frame, (-7, 4, -9), (3, 3, 1), DARK)

    glassb = m.bone("glass")
    m.cube(glassb, (-6, 4, -8), (12, 40, 1), GLASS)
    m.cube(glassb, (-6, 4, 7), (12, 40, 1), GLASS)
    m.cube(glassb, (-8, 4, -6), (1, 40, 12), GLASS)
    m.cube(glassb, (7, 4, -6), (1, 40, 12), GLASS)

    pad = m.bone("glow_pad", (0, 3, 0))
    m.cube(pad, (-5, 3, -5), (10, 1, 10), GREEN)
    top = m.bone("glow_top", (0, 43, 0))
    m.cube(top, (-5, 43, -5), (10, 1, 10), GREEN)
    scr = m.bone("glow_screen", (5, 7, -9))
    m.cube(scr, (4, 6, -10), (2, 3, 1), screen())
    lamp = m.bone("glow_lamp", (-5.5, 6, -9))
    m.cube(lamp, (-6, 5, -10), (1, 1, 1), GREEN)
    for name, y in (("glow_ring_low", 10), ("glow_ring_high", 30)):
        b = m.bone(name, (0, y, 0))
        m.cube(b, (-6, y, -7), (12, 1, 1), GREEN)
        m.cube(b, (-6, y, 6), (12, 1, 1), GREEN)
        m.cube(b, (-7, y, -7), (1, 1, 14), GREEN)
        m.cube(b, (6, y, -7), (1, 1, 14), GREEN)
    beam = m.bone("glow_beam", (0, 4, 0))
    m.cube(beam, (-1.5, 4, -1.5), (3, 39, 3), BEAM)
    cap = m.bone("glow_capsule", (0, 50, 0))
    m.cube(cap, (-1, 50, -1), (2, 5, 2), GREEN)
    m.cube(cap, (-1.5, 55, -1.5), (3, 1, 3), DARK)
    m.save()

    hidden = keys([(0, [0, 1, 0])])
    save_anims("gamma_chamber", {
        "idle": (4.0, {
            "glow_ring_low": {"position": wave(4.0, 2.0)},
            "glow_ring_high": {"position": wave(4.0, 2.0, phase=math.pi)},
            "glow_beam": {"scale": hidden},
            "glow_top": {"scale": wave(4.0, 0.06, base=(1, 1, 1), axis=0)},
        }),
        "loaded": (2.0, {
            "glow_ring_low": {"position": wave(2.0, 4.0)},
            "glow_ring_high": {"position": wave(2.0, 4.0, phase=math.pi)},
            "glow_beam": {"scale": keys([(0, [0.25, 1, 0.25]), (1.0, [0.4, 1, 0.4]), (2.0, [0.25, 1, 0.25])])},
            "glow_capsule": {"scale": keys([(0, [1, 1, 1]), (1.0, [1.25, 1.05, 1.25]), (2.0, [1, 1, 1])])},
        }),
        "active": (1.0, {
            "glow_ring_low": {"position": keys([(0, [0, 0, 0]), (0.5, [0, 30, 0]), (1.0, [0, 0, 0])])},
            "glow_ring_high": {"position": keys([(0, [0, 0, 0]), (0.5, [0, -24, 0]), (1.0, [0, 0, 0])])},
            "glow_beam": {"scale": keys([(0, [0.9, 1, 0.9]), (0.25, [1.7, 1, 1.7]), (0.5, [1.0, 1, 1.0]),
                                         (0.75, [1.9, 1, 1.9]), (1.0, [0.9, 1, 0.9])])},
            "glow_capsule": {"scale": keys([(0, [1, 1, 1]), (0.5, [1.35, 0.9, 1.35]), (1.0, [1, 1, 1])])},
            "glow_pad": {"scale": keys([(0, [1, 1, 1]), (0.5, [1.15, 1, 1.15]), (1.0, [1, 1, 1])])},
        }),
    })


# ----------------------------------------------------------------------------------
# Acceleration Force portal frame (ring in the XY plane, radius ~ 1 block = 16 px)
# ----------------------------------------------------------------------------------

def portal():
    m = Model("acceleration_portal_frame", 192, 128)
    GOLD = metal((200, 200, 200), seams=0, rivets=False)
    DARKM = metal((90, 90, 90), seams=0, rivets=False)
    GLOWW = glow((210, 210, 210), core=(255, 255, 255), alpha=240)

    def ring(name, radius, count, size, mat, offset=0.0, parent=None, z=0.0):
        b = m.bone(name, (0, 0, 0), parent=parent)
        sx, sy, sz = size
        for i in range(count):
            a = 360.0 * i / count + offset
            m.cube(b, (-sx / 2, radius - sy / 2, z - sz / 2), (sx, sy, sz), mat, rotation=(0, 0, a), pivot=(0, 0, 0))
        return b

    root = m.bone("root")
    ring("ring_outer", 17.5, 24, (4.6, 2, 2), GOLD, parent="root")
    ring("ring_back", 16.0, 16, (6.2, 1, 1), DARKM, offset=11.25, parent="root", z=0.8)
    ring("glow_ring_inner", 14.5, 32, (3.0, 1, 1), GLOWW, parent="root")
    # lightning fins: jagged pieces pointing outwards, each a pair of tilted bars
    fins = m.bone("glow_fins", (0, 0, 0), parent="root")
    for i in range(8):
        a = 45.0 * i + 22.5
        m.cube(fins, (-0.6, 18.5, -0.6), (1.2, 4, 1.2), GLOWW, rotation=(0, 0, a + 8), pivot=(0, 0, 0))
        m.cube(fins, (-0.6, 21.8, -0.6), (1.2, 3, 1.2), GLOWW, rotation=(0, 0, a - 6), pivot=(0, 0, 0))
    claws = m.bone("claws", (0, 0, 0), parent="root")
    for i in range(4):
        a = 90.0 * i
        m.cube(claws, (-2, 18.5, -1.5), (4, 3, 3), DARKM, rotation=(0, 0, a), pivot=(0, 0, 0))
        m.cube(claws, (-1, 21.5, -1), (2, 2, 2), GOLD, rotation=(0, 0, a), pivot=(0, 0, 0))
    m.save()

    save_anims("acceleration_portal_frame", {
        "spin": (8.0, {
            "ring_outer": {"rotation": spin(8.0, 1.0, axis=2)},
            "ring_back": {"rotation": spin(8.0, -0.5, axis=2)},
            "glow_ring_inner": {"rotation": spin(8.0, -2.0, axis=2)},
            "glow_fins": {"rotation": spin(8.0, 0.5, axis=2),
                          "scale": keys([(0, [1, 1, 1]), (1.0, [1.08, 1.08, 1]), (2.0, [1, 1, 1]),
                                         (3.0, [1.1, 1.1, 1]), (4.0, [1, 1, 1]), (5.0, [1.06, 1.06, 1]),
                                         (6.0, [1, 1, 1]), (7.0, [1.1, 1.1, 1]), (8.0, [1, 1, 1])])},
            "claws": {"rotation": keys([(0, [0, 0, 0]), (4.0, [0, 0, 22.5]), (8.0, [0, 0, 0])])},
        }),
    })


if __name__ == "__main__":
    chamber()
    portal()
    print("ok")
