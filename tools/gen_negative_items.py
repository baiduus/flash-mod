#!/usr/bin/env python3
"""Generates the textures and 3D item models of the Negative Acceleration Force
content: worn Flash suit, tachyon, corrupted matter, negative capsule, syringe
and the red dimension blocks. Run from the repository root."""
import json, os, random
from PIL import Image

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "flash")
TEX_ITEM = os.path.join(ROOT, "textures", "item")
TEX_BLOCK = os.path.join(ROOT, "textures", "block")
TEX_EFFECT = os.path.join(ROOT, "textures", "effect")
MODEL_ITEM = os.path.join(ROOT, "models", "item")
for d in (TEX_ITEM, TEX_BLOCK, TEX_EFFECT, MODEL_ITEM):
    os.makedirs(d, exist_ok=True)

def save_json(path, data):
    with open(path, "w") as f:
        json.dump(data, f, indent=1)

# ------------------------------------------------------------------ suit
REDS = [(0x5A, 0x05, 0x09), (0x6E, 0x0A, 0x10), (0x7E, 0x0C, 0x12),
        (0x8B, 0x0E, 0x14), (0xA3, 0x18, 0x20), (0xB5, 0x20, 0x28)]
GOLD, GOLD_L, GOLD_D = (0xD9, 0xA5, 0x3A), (0xF0, 0xC8, 0x5C), (0xA8, 0x7A, 0x22)
CREAM, CREAM_D = (0xE8, 0xD3, 0xB0), (0xCD, 0xB2, 0x8A)
TEAR, TEAR_IN = (0x2A, 0x04, 0x06), (0xA8, 0x5A, 0x50)

D = 3                 # texture pixels per model unit (finer than vanilla so the emblem reads)
SHEET = 256
# name: (from, to) in model units
SUIT = {
    "torso":  ((4, 0, 6), (12, 12, 10)),
    "padL":   ((0, 7, 6), (4, 12, 10)),
    "padR":   ((12, 7, 6), (16, 12, 10)),
    "slvL":   ((1, 3, 6), (4, 7, 10)),
    "slvR":   ((12, 3, 6), (15, 7, 10)),
    "colL":   ((4, 12, 6), (7, 14, 10)),
    "colR":   ((9, 12, 6), (12, 14, 10)),
}
FACES = ["north", "south", "east", "west", "up", "down"]

def face_size(box, face):
    """Size of a face in model units."""
    (x0, y0, z0), (x1, y1, z1) = box
    dx, dy, dz = x1 - x0, y1 - y0, z1 - z0
    if face in ("north", "south"):
        return dx, dy
    if face in ("east", "west"):
        return dz, dy
    return dx, dz

def pack(items, size):
    """Simple shelf packer. items: list of (key, w, h) in pixels."""
    items = sorted(items, key=lambda i: (-i[2], -i[1]))
    pos, x, y, shelf = {}, 0, 0, 0
    for key, w, h in items:
        if x + w > size:
            x, y, shelf = 0, y + shelf, 0
        assert y + h <= size, "sheet too small"
        pos[key] = (x, y)
        x += w
        shelf = max(shelf, h)
    return pos

rnd = random.Random(1305)

def red_face(w, h, light=0):
    """Clustered reddish noise (2x2 cells with a little 1px grain), like the reference print."""
    im = Image.new("RGBA", (w, h))
    cells = {}
    for y in range(h):
        for x in range(w):
            key = (x // 2, y // 2)
            if key not in cells:
                cells[key] = rnd.choice([2, 3, 3, 4, 2, 1, 5, 3, 3, 4])
            v = cells[key] + light
            if rnd.random() < 0.12:
                v += rnd.choice([-1, 1])
            if y > h * 0.8:
                v -= 1
            im.putpixel((x, y), REDS[max(0, min(5, v))] + (255,))
    return im

def paint_suit():
    from PIL import ImageDraw
    jobs, faces = [], {}
    for name, box in SUIT.items():
        for f in FACES:
            w, h = face_size(box, f)
            jobs.append(((name, f), int(w * D), int(h * D)))
    pos = pack(jobs, SHEET)
    for (name, f), w, h in jobs:
        light = 1 if f == "up" else (-1 if f == "down" else 0)
        faces[(name, f)] = red_face(w, h, light)

    def draw(name, f):
        return ImageDraw.Draw(faces[(name, f)])

    def zig(name, f, y0, mirror=False):
        """Stepped gold zig-zag line across a limb face (rising to the outside)."""
        im = faces[(name, f)]
        w, h = im.size
        d = ImageDraw.Draw(im)
        pts, x, y = [], 0, y0
        while x < w:
            pts.append((x, y)); x += 3; pts.append((x, y)); y -= 2 if (x // 3) % 2 else 1
        if mirror:
            pts = [(w - 1 - px, py) for px, py in pts]
        d.line(pts, fill=GOLD + (255,), width=2)
        d.line([(px, py - 1) for px, py in pts[:len(pts) // 2]], fill=GOLD_L + (255,), width=1)

    def tear(name, f, cx, cy, length, angle_up=True):
        im = faces[(name, f)]
        d = ImageDraw.Draw(im)
        dx = length
        dy = -length * 0.55 if angle_up else length * 0.55
        d.line([(cx - dx, cy - dy), (cx + dx, cy + dy)], fill=TEAR + (255,), width=2)
        d.line([(cx - dx * 0.5, cy - dy * 0.5 + 2), (cx + dx * 0.4, cy + dy * 0.4 + 2)], fill=TEAR_IN + (255,), width=1)

    # ------------- chest front (24 x 36)
    t = faces[("torso", "north")]
    d = ImageDraw.Draw(t)
    d.ellipse((4, 4, 20, 20), fill=REDS[0] + (255,))                 # dark ring
    d.ellipse((5, 5, 19, 19), fill=CREAM + (255,))
    d.ellipse((6, 6, 14, 14), fill=(0xF1, 0xE2, 0xC6, 255))            # highlight
    d.polygon([(15, 5), (9, 12), (12, 12), (8, 19), (17, 11), (13, 11), (16, 5)], fill=GOLD + (255,))
    d.line([(15, 6), (11, 12)], fill=GOLD_L + (255,), width=1)
    d.line([(21, 3), (3, 31)], fill=GOLD_D + (255,), width=1)          # thin diagonal across the emblem
    # belt V and side lines
    for sign in (1, -1):
        def X(x):
            return x if sign == 1 else 23 - x
        d.line([(X(0), 17), (X(2), 20), (X(3), 25), (X(5), 29), (X(8), 32), (X(11), 34)], fill=GOLD + (255,), width=1)
        d.line([(X(0), 31), (X(4), 31), (X(6), 33)], fill=GOLD + (255,), width=1)
    d.line([(5, 34), (11, 34)], fill=GOLD_D + (255,), width=1)
    d.line([(12, 34), (18, 34)], fill=GOLD_D + (255,), width=1)
    tear("torso", "north", 4, 12, 3, True)       # left of the emblem
    tear("torso", "north", 16, 24, 3, True)      # lower right
    tear("torso", "north", 20, 4, 3, False)      # upper right
    # ------------- chest back
    for x0, x1 in ((3, 21),):
        draw("torso", "south").line([(x0, 33), (x1, 33)], fill=GOLD_D + (255,), width=1)
    tear("torso", "south", 7, 12, 4, False)
    tear("torso", "south", 17, 20, 4, True)
    # ------------- shoulder pads and sleeves
    for name in ("padL", "padR"):
        for f in ("north", "south"):
            zig(name, f, 22, mirror=(name == "padR"))
        d = draw(name, "north")
        d.rectangle((0, 0, 3, 2), fill=REDS[5] + (255,))             # lit corner
    tear("padR", "north", 7, 7, 3, False)
    for name in ("slvL", "slvR"):
        for f in ("north", "south"):
            zig(name, f, 7, mirror=(name == "slvR"))
    for name in ("colL", "colR"):
        for f in ("north", "south", "east", "west"):
            w, h = faces[(name, f)].size
            draw(name, f).rectangle((0, 0, w - 1, 1), fill=REDS[0] + (255,))
    img = Image.new("RGBA", (SHEET, SHEET), (0, 0, 0, 0))
    for key, (x0, y0) in pos.items():
        img.paste(faces[key], (x0, y0))
    img.save(os.path.join(TEX_ITEM, "torn_suit.png"))
    return pos

def suit_model(pos):
    elements = []
    for name, (frm, to) in SUIT.items():
        faces = {}
        for f in FACES:
            w, h = face_size((frm, to), f)
            x0, y0 = pos[(name, f)]
            faces[f] = {"uv": [x0 * 16 / SHEET, y0 * 16 / SHEET,
                               (x0 + int(w * D)) * 16 / SHEET, (y0 + int(h * D)) * 16 / SHEET],
                        "texture": "#0"}
        elements.append({"name": name, "from": list(frm), "to": list(to), "faces": faces})
    save_json(os.path.join(MODEL_ITEM, "torn_suit.json"), {
        "credit": "Flash mod - torn suit chestplate, modelled from scratch",
        "textures": {"0": "flash:item/torn_suit", "particle": "flash:item/torn_suit"},
        "elements": elements,
        "display": {
            "gui": {"rotation": [18, 160, 0], "translation": [0, -0.5, 0], "scale": [0.95, 0.95, 0.95]},
            "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.5, 0.5, 0.5]},
            "fixed": {"rotation": [0, 180, 0], "translation": [0, -1, 0], "scale": [0.95, 0.95, 0.95]},
            "thirdperson_righthand": {"rotation": [0, 0, 0], "translation": [0, 3, 2], "scale": [0.4, 0.4, 0.4]},
            "thirdperson_lefthand": {"rotation": [0, 0, 0], "translation": [0, 3, 2], "scale": [0.4, 0.4, 0.4]},
            "firstperson_righthand": {"rotation": [0, 180, 0], "translation": [0, 1, 0], "scale": [0.45, 0.45, 0.45]},
            "firstperson_lefthand": {"rotation": [0, 180, 0], "translation": [0, 1, 0], "scale": [0.45, 0.45, 0.45]},
            "head": {"rotation": [0, 180, 0], "translation": [0, 13, 7], "scale": [1, 1, 1]},
        },
    })

# ------------------------------------------------------------------ small items
def flat_model(name):
    save_json(os.path.join(MODEL_ITEM, name + ".json"), {
        "parent": "minecraft:item/generated",
        "textures": {"layer0": "flash:item/" + name},
    })

def paint_tachyon():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    glow = (255, 200, 40, 90)
    for y in range(16):
        for x in range(16):
            d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
            if d < 7.4:
                img.putpixel((x, y), (255, 190, 30, int(95 * (1 - d / 7.4))))
    # crystal diamond
    for y in range(2, 14):
        half = 5 - abs(y - 7.5) * 0.65
        for x in range(16):
            if abs(x - 7.5) <= half:
                t = (x - 7.5 + half) / max(1, 2 * half)
                col = (255, 242, 120) if t > 0.45 else (255, 200, 40)
                if abs(x - 7.5) > half - 1.0:
                    col = (200, 130, 10)
                img.putpixel((x, y), col + (255,))
    # white-hot bolt in the middle
    for x, y in [(8, 3), (7, 4), (7, 5), (6, 6), (7, 6), (8, 6), (7, 7), (6, 8), (6, 9), (5, 10), (9, 5), (8, 7)]:
        img.putpixel((x, y), (255, 255, 235, 255))
    img.save(os.path.join(TEX_ITEM, "tachyon.png"))
    flat_model("tachyon")

def paint_matter():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 255))
    base = [(18, 6, 24), (26, 8, 34), (34, 10, 40), (12, 4, 18)]
    for y in range(16):
        for x in range(16):
            img.putpixel((x, y), rnd.choice(base) + (255,))
    # glowing crimson veins
    x, y = 1, 3
    for _ in range(26):
        img.putpixel((x % 16, y % 16), rnd.choice([(190, 20, 40), (255, 50, 60), (130, 10, 30)]) + (255,))
        x += rnd.choice([1, 1, 0]); y += rnd.choice([1, 0, -1, 1])
    x, y = 14, 12
    for _ in range(18):
        img.putpixel((x % 16, y % 16), rnd.choice([(190, 20, 40), (255, 50, 60)]) + (255,))
        x -= rnd.choice([1, 1, 0]); y -= rnd.choice([1, 0, -1])
    img.save(os.path.join(TEX_ITEM, "corrupted_matter.png"))
    blob = [((5, 3, 5), (11, 9, 11)), ((3, 5, 6), (6, 8, 9)), ((10, 6, 5), (13, 9, 8)),
            ((6, 9, 6), (9, 12, 9)), ((7, 0, 7), (10, 3, 10)), ((4, 2, 9), (7, 5, 12)),
            ((9, 3, 9), (12, 6, 12)), ((2, 9, 7), (5, 11, 10))]
    elements = []
    for i, (a, b) in enumerate(blob):
        faces = {f: {"uv": [0, 0, 16, 16], "texture": "#0"} for f in FACES}
        elements.append({"name": "blob%d" % i, "from": list(a), "to": list(b), "faces": faces})
    save_json(os.path.join(MODEL_ITEM, "corrupted_matter.json"), {
        "credit": "Flash mod - corrupted matter, modelled from scratch",
        "textures": {"0": "flash:item/corrupted_matter", "particle": "flash:item/corrupted_matter"},
        "elements": elements,
        "display": block_display(0.62),
    })

def block_display(s):
    return {
        "gui": {"rotation": [30, 225, 0], "translation": [0, 0, 0], "scale": [s, s, s]},
        "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.25, 0.25, 0.25]},
        "fixed": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.5, 0.5, 0.5]},
        "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.375, 0.375, 0.375]},
        "thirdperson_lefthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.375, 0.375, 0.375]},
        "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 0, 0], "scale": [0.4, 0.4, 0.4]},
        "firstperson_lefthand": {"rotation": [0, 225, 0], "translation": [0, 0, 0], "scale": [0.4, 0.4, 0.4]},
    }

def paint_capsule():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 255))
    for y in range(8):
        for x in range(8):               # metal (top-left 8x8)
            c = rnd.choice([(88, 88, 96), (110, 110, 118), (70, 70, 78)])
            if y in (0, 7) or x in (0, 7):
                c = (150, 150, 160)
            img.putpixel((x, y), c + (255,))
        for x in range(8, 16):           # liquid (top-right 8x8)
            c = rnd.choice([(170, 12, 22), (205, 24, 34), (140, 8, 18)])
            if (x + y) % 5 == 0:
                c = (255, 90, 90)
            img.putpixel((x, y), c + (255,))
    for y in range(8, 16):               # glass (bottom 16x8)
        for x in range(16):
            c = (170, 210, 235) if x not in (0, 15) else (120, 160, 190)
            if x in (3, 4):
                c = (235, 248, 255)
            img.putpixel((x, y), c + (255,))
    img.save(os.path.join(TEX_ITEM, "negative_capsule.png"))
    metal, liquid, glass = [0, 0, 8, 8], [8, 0, 16, 8], [0, 8, 16, 16]
    def el(a, b, uv, name):
        return {"name": name, "from": a, "to": b, "faces": {f: {"uv": uv, "texture": "#0"} for f in FACES}}
    save_json(os.path.join(MODEL_ITEM, "negative_capsule.json"), {
        "credit": "Flash mod - negative force capsule, modelled from scratch",
        "textures": {"0": "flash:item/negative_capsule", "particle": "flash:item/negative_capsule"},
        "elements": [
            el([5.5, 0, 5.5], [10.5, 2, 10.5], metal, "cap_bottom"),
            el([5.5, 12, 5.5], [10.5, 14, 10.5], metal, "cap_top"),
            el([6, 2, 6], [10, 12, 10], glass, "tube"),
            el([6.5, 3, 6.5], [9.5, 11, 9.5], liquid, "core") if False else el([5.9, 3, 5.9], [10.1, 11, 10.1], liquid, "liquid_band"),
        ],
        "display": block_display(0.8),
    })

def paint_syringe(name, liquid):
    """16x16 sheet: metal (0-8,0-8), barrel side (8-12,0-8), barrel cap (12-16,0-4)."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y in range(8):
        for x in range(8):
            c = rnd.choice([(150, 152, 160), (176, 178, 186), (128, 130, 138)])
            if y == 0 or x == 0:
                c = (205, 206, 214)
            img.putpixel((x, y), c + (255,))
    for y in range(8):
        for x in range(4):
            glass = (178, 214, 236) if x in (0, 3) else (214, 236, 250)
            c = glass
            if liquid and x in (1, 2) and y >= 2:
                c = liquid[0] if x == 1 else liquid[1]
            if y % 2 == 0 and x == 3 and not liquid:
                c = (110, 130, 150)       # graduation marks
            img.putpixel((8 + x, y), c + (255,))
    for y in range(4):
        for x in range(4):
            img.putpixel((12 + x, y), (96, 100, 110, 255) if (x + y) % 2 else (120, 124, 134, 255))
    img.save(os.path.join(TEX_ITEM, name + ".png"))
    metal, barrel, cap = [0, 0, 8, 8], [8, 0, 12, 8], [12, 0, 16, 4]
    def el(a, b, uv_side, uv_cap, label):
        faces = {f: {"uv": uv_side, "texture": "#0"} for f in ("north", "south", "east", "west")}
        faces["up"] = {"uv": uv_cap, "texture": "#0"}
        faces["down"] = {"uv": uv_cap, "texture": "#0"}
        return {"name": label, "from": a, "to": b, "faces": faces}
    elements = [
        el([7.5, 0, 7.5], [8.5, 3, 8.5], metal, [0, 0, 1, 1], "needle"),
        el([6.5, 3, 6.5], [9.5, 4, 9.5], metal, [0, 0, 3, 3], "hub"),
        el([6, 4, 6], [10, 12, 10], barrel, cap, "barrel"),
        el([4, 12, 5.5], [12, 13, 10.5], metal, [0, 0, 8, 5], "flange"),
        el([7.25, 13, 7.25], [8.75, 15, 8.75], metal, [0, 0, 2, 2], "rod"),
        el([6, 15, 6], [10, 16, 10], metal, [0, 0, 4, 4], "thumb"),
    ]
    return {
        "credit": "Flash mod - syringe, modelled from scratch",
        "textures": {"0": "flash:item/" + name, "particle": "flash:item/" + name},
        "elements": elements,
        "display": {
            "gui": {"rotation": [20, -35, 40], "translation": [0, 0, 0], "scale": [0.95, 0.95, 0.95]},
            "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.5, 0.5, 0.5]},
            "fixed": {"rotation": [0, 0, 35], "translation": [0, 0, 0], "scale": [0.9, 0.9, 0.9]},
            "thirdperson_righthand": {"rotation": [60, 0, 0], "translation": [0, 3, 1], "scale": [0.55, 0.55, 0.55]},
            "thirdperson_lefthand": {"rotation": [60, 0, 0], "translation": [0, 3, 1], "scale": [0.55, 0.55, 0.55]},
            "firstperson_righthand": {"rotation": [0, -90, 35], "translation": [1.13, 3.2, 1.13], "scale": [0.62, 0.62, 0.62]},
            "firstperson_lefthand": {"rotation": [0, 90, -35], "translation": [1.13, 3.2, 1.13], "scale": [0.62, 0.62, 0.62]},
        },
    }

def syringe_models():
    save_json(os.path.join(MODEL_ITEM, "syringe_empty.json"), paint_syringe("syringe", None))
    save_json(os.path.join(MODEL_ITEM, "syringe_negative.json"),
              paint_syringe("syringe_negative", [(190, 14, 26), (255, 60, 70)]))
    # Base model with predicates: add new entries here for future syringe contents.
    save_json(os.path.join(MODEL_ITEM, "syringe.json"), {
        "parent": "flash:item/syringe_empty",
        "overrides": [
            {"predicate": {"flash:content": 1}, "model": "flash:item/syringe_negative"},
        ],
    })

# ------------------------------------------------------------------ red dimension blocks
def tint_block(src, dst, fn):
    im = Image.open(os.path.join(TEX_BLOCK, src)).convert("RGBA")
    out = Image.new("RGBA", im.size)
    for y in range(im.size[1]):
        for x in range(im.size[0]):
            r, g, b, a = im.getpixel((x, y))
            out.putpixel((x, y), fn(r, g, b) + (a,))
    out.save(os.path.join(TEX_BLOCK, dst))

def red_blocks():
    clamp = lambda v: max(0, min(255, int(v)))
    tint_block("acceleration_stone.png", "negative_stone.png",
               lambda r, g, b: (clamp(r * 1.2 + 26), clamp(g * 0.30), clamp(b * 0.34 + 2)))
    tint_block("acceleration_crystal.png", "negative_crystal.png",
               lambda r, g, b: (clamp(r + 8), clamp(g * 0.22), clamp(b * 0.20)))
    # red versions of the portal effect textures
    for name in ("portal_vortex", "portal_glow"):
        src = os.path.join(TEX_EFFECT, name + ".png")
        if not os.path.exists(src):
            continue
        im = Image.open(src).convert("RGBA")
        out = Image.new("RGBA", im.size)
        for y in range(im.size[1]):
            for x in range(im.size[0]):
                r, g, b, a = im.getpixel((x, y))
                # gold/orange -> red, blue -> dark crimson; keep the white-hot cores light pink
                lum = (r + g + b) / 3.0
                if b > r * 0.9 and b > 120:
                    out.putpixel((x, y), (clamp(b * 0.85), clamp(g * 0.12), clamp(r * 0.2), a))
                else:
                    out.putpixel((x, y), (clamp(r), clamp(g * 0.16 + (lum - 120) * 0.25 if lum > 200 else g * 0.16), clamp(b * 0.22), a))
        out.save(os.path.join(TEX_EFFECT, name + "_negative.png"))

if __name__ == "__main__":
    pos = paint_suit()
    suit_model(pos)
    paint_tachyon()
    paint_matter()
    paint_capsule()
    syringe_models()
    red_blocks()
    print("ok")
