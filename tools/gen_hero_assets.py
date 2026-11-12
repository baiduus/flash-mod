#!/usr/bin/env python3
"""Generates the art of the power system: the Kryptonian cell, the power capsules (Kryptonian blood, gamma
essence, Speed Force, Asgardian essence, red gamma essence) and their syringes, and the new Processor (block model, item model and
textures, drawn from scratch). Run from the repository root."""
import colorsys
import json
import os
import random

from PIL import Image, ImageDraw

A = "src/main/resources/assets/flash"
T = A + "/textures"
M = A + "/models"


def recolor(src, dst, hue, sat=None, bright=1.0):
    """Moves every strongly coloured pixel (the liquid) to a new hue; the glass and metal stay."""
    im = Image.open(src).convert("RGBA")
    px = im.load()
    for y in range(im.height):
        for x in range(im.width):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            h, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
            if s > 0.45:
                nr, ng, nb = colorsys.hsv_to_rgb(hue, s if sat is None else sat, min(1.0, v * bright))
                px[x, y] = (int(nr * 255), int(ng * 255), int(nb * 255), a)
    im.save(dst)


CAPSULES = {
    # id: (hue, saturation, brightness)
    "kryptonian_blood": (0.98, 0.85, 1.05),
    "hulk_essence": (0.31, 0.9, 1.0),
    "flash_essence": (0.12, 0.95, 1.15),
    "thor_essence": (0.58, 0.85, 1.15),
    # the Red Hulk's gamma (made in the Processor from the green one): a dark ruby
    "red_hulk_essence": (0.995, 1.0, 0.78),
}


def capsules():
    with open(M + "/item/negative_capsule.json") as f:
        cap = json.load(f)
    with open(M + "/item/syringe_negative.json") as f:
        syr = json.load(f)
    for cid, (h, s, b) in CAPSULES.items():
        recolor(T + "/item/negative_capsule.png", T + f"/item/{cid}_capsule.png", h, s, b)
        recolor(T + "/item/syringe_negative.png", T + f"/item/syringe_{cid}.png", h, s, b)
        c = json.loads(json.dumps(cap))
        c["credit"] = "Herois_baiduu - power capsule"
        c["textures"] = {"0": f"flash:item/{cid}_capsule", "particle": f"flash:item/{cid}_capsule"}
        with open(M + f"/item/{cid}_capsule.json", "w") as f:
            json.dump(c, f, indent=1)
        sy = json.loads(json.dumps(syr))
        sy["textures"] = {"0": f"flash:item/syringe_{cid}", "particle": f"flash:item/syringe_{cid}"}
        with open(M + f"/item/syringe_{cid}.json", "w") as f:
            json.dump(sy, f, indent=1)
    # the syringe picks its model by content index
    with open(M + "/item/syringe.json") as f:
        s = json.load(f)
    s["overrides"] = [o for o in s["overrides"] if o["predicate"]["flash:content"] <= 3]
    for i, cid in enumerate(CAPSULES, start=4):
        s["overrides"].append({"predicate": {"flash:content": i}, "model": f"flash:item/syringe_{cid}"})
    with open(M + "/item/syringe.json", "w") as f:
        json.dump(s, f, indent=1)


def cell():
    """A Kryptonian cell: a crystal shard held in a dark Kryptonian frame, glowing pale blue-white."""
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    # frame
    d.rectangle([4, 12, 11, 14], fill=(46, 52, 62, 255))
    d.rectangle([5, 14, 10, 15], fill=(30, 34, 42, 255))
    d.point([(4, 12), (11, 12)], fill=(90, 100, 116, 255))
    # crystal (a hexagonal prism seen from the side, pointed top)
    shape = [(6, 12), (6, 5), (8, 1), (10, 5), (10, 12)]
    d.polygon(shape, fill=(150, 220, 255, 255))
    d.polygon([(8, 1), (10, 5), (10, 12), (8, 12)], fill=(96, 178, 236, 255))
    d.line([(8, 2), (8, 11)], fill=(225, 250, 255, 255))
    d.point([(7, 4), (7, 7), (9, 9)], fill=(255, 255, 255, 255))
    # the glyph of the House of El, a dot of gold
    d.point([(7, 13), (8, 13)], fill=(236, 190, 70, 255))
    im.save(T + "/item/kryptonian_cell.png")
    with open(M + "/item/kryptonian_cell.json", "w") as f:
        json.dump({"parent": "minecraft:item/generated", "textures": {"layer0": "flash:item/kryptonian_cell"}},
                  f, indent=1)


# ----------------------------------------------------------------------
# The Processor
# ----------------------------------------------------------------------

def noise(im, amount, seed):
    rnd = random.Random(seed)
    px = im.load()
    for y in range(im.height):
        for x in range(im.width):
            r, g, b, a = px[x, y]
            if a:
                k = rnd.randint(-amount, amount)
                px[x, y] = (max(0, min(255, r + k)), max(0, min(255, g + k)), max(0, min(255, b + k)), a)


def processor_textures():
    os.makedirs(T + "/block/processor", exist_ok=True)
    P = T + "/block/processor/"
    # dark armoured metal with panel seams and rivets
    im = Image.new("RGBA", (16, 16), (54, 60, 70, 255))
    d = ImageDraw.Draw(im)
    d.rectangle([0, 0, 15, 15], outline=(36, 40, 48, 255))
    d.line([(0, 7), (15, 7)], fill=(38, 42, 50, 255))
    d.line([(0, 8), (15, 8)], fill=(72, 80, 92, 255))
    for p in [(2, 2), (13, 2), (2, 13), (13, 13)]:
        d.point(p, fill=(120, 130, 146, 255))
    noise(im, 4, 1)
    im.save(P + "metal.png")
    # brushed light steel trim
    im = Image.new("RGBA", (16, 16), (150, 158, 170, 255))
    d = ImageDraw.Draw(im)
    for y in range(16):
        d.line([(0, y), (15, y)], fill=(140 + (y * 7) % 22, 148 + (y * 7) % 22, 162 + (y * 7) % 22, 255))
    d.rectangle([0, 0, 15, 15], outline=(96, 104, 116, 255))
    noise(im, 3, 2)
    im.save(P + "trim.png")
    # vents with a status led
    im = Image.new("RGBA", (16, 16), (46, 50, 58, 255))
    d = ImageDraw.Draw(im)
    for y in range(3, 13, 3):
        d.rectangle([3, y, 12, y + 1], fill=(14, 16, 20, 255))
        d.line([(3, y + 2), (12, y + 2)], fill=(80, 88, 100, 255))
    d.rectangle([0, 0, 15, 15], outline=(34, 38, 44, 255))
    d.point([(13, 1), (14, 1)], fill=(90, 240, 255, 255))
    im.save(P + "vent.png")
    # glow strips (on / off)
    for name, top, low in (("glow", (210, 255, 255), (40, 200, 255)), ("glow_off", (60, 90, 100), (30, 50, 60))):
        im = Image.new("RGBA", (16, 16), (0, 0, 0, 255))
        px = im.load()
        for y in range(16):
            for x in range(16):
                k = abs(x - 7.5) / 7.5
                c = tuple(int(top[i] * (1 - k) + low[i] * k) for i in range(3))
                px[x, y] = c + (255,)
        im.save(P + name + ".png")
    # emitter pad (the floor of the chamber): rings of light (on / off)
    for name, on in (("pad", True), ("pad_off", False)):
        im = Image.new("RGBA", (16, 16), (28, 32, 38, 255))
        d = ImageDraw.Draw(im)
        for r, c in ((7, (40, 200, 255) if on else (40, 60, 70)), (5, (30, 34, 42)),
                     (4, (120, 235, 255) if on else (50, 70, 80)), (2, (30, 34, 42)),
                     (1, (230, 255, 255) if on else (60, 80, 90))):
            d.ellipse([7.5 - r, 7.5 - r, 7.5 + r, 7.5 + r], fill=c + (255,))
        im.save(P + name + ".png")
    # control screen (the progress bar is drawn over it)
    im = Image.new("RGBA", (16, 16), (12, 22, 30, 255))
    d = ImageDraw.Draw(im)
    for x in range(0, 16, 4):
        d.line([(x, 0), (x, 15)], fill=(18, 40, 52, 255))
    for y in range(0, 16, 4):
        d.line([(0, y), (15, y)], fill=(18, 40, 52, 255))
    d.rectangle([0, 0, 15, 15], outline=(60, 70, 84, 255))
    d.rectangle([2, 2, 5, 3], fill=(60, 200, 240, 255))
    d.point([(12, 2), (13, 2)], fill=(255, 190, 60, 255))
    im.save(P + "screen.png")
    # chamber glass (drawn by the renderer)
    im = Image.new("RGBA", (16, 16), (120, 220, 255, 60))
    d = ImageDraw.Draw(im)
    d.line([(2, 13), (13, 2)], fill=(230, 250, 255, 110))
    d.line([(4, 14), (14, 4)], fill=(230, 250, 255, 70))
    d.rectangle([0, 0, 15, 15], outline=(150, 230, 255, 140))
    im.save(P + "glass.png")
    # bottom
    im = Image.new("RGBA", (16, 16), (30, 34, 40, 255))
    noise(im, 3, 3)
    im.save(P + "bottom.png")


def face(tex, uv=(0, 0, 16, 16), glow=False):
    f = {"uv": list(uv), "texture": tex}
    if glow:
        f["forge_data"] = {"block_light": 15, "sky_light": 15}
    return f


def box(name, fr, to, tex, faces="nsewud", glow=False, uvs=None):
    m = {"n": "north", "s": "south", "e": "east", "w": "west", "u": "up", "d": "down"}
    out = {"name": name, "from": fr, "to": to, "faces": {}}
    for k in faces:
        uv = (uvs or {}).get(k, (0, 0, 16, 16))
        out["faces"][m[k]] = face(tex, uv, glow)
    return out


def processor_model(on):
    g = "#glow" if on else "#glow_off"
    pad = "#pad" if on else "#pad_off"
    e = []
    # plinth and trim
    e.append(box("plinth", [0, 0, 0], [16, 2, 16], "#metal", "nsewu"))
    e.append(box("plinth_bottom", [0, 0, 0], [16, 0, 16], "#bottom", "d"))
    e.append(box("trim", [1, 2, 1], [15, 3, 15], "#trim"))
    # four corner pillars, each with a light strip facing out
    for x0, z0 in ((1, 1), (13, 1), (1, 13), (13, 13)):
        e.append(box("pillar", [x0, 3, z0], [x0 + 2, 13, z0 + 2], "#trim", "nsew", uvs={k: (0, 3, 2, 13) for k in "nsew"}))
        sz = z0 - 0.05 if z0 == 1 else z0 + 2.05
        e.append(box("pillar_light", [x0 + 0.5, 4, min(sz, z0 + 2)], [x0 + 1.5, 12, max(sz, z0)], g,
                     "n" if z0 == 1 else "s", glow=on, uvs={"n": (6, 0, 10, 16), "s": (6, 0, 10, 16)}))
    # the chamber floor: the emitter pad
    e.append(box("emitter_pad", [3, 3, 3], [13, 3.5, 13], pad, "u", glow=on))
    e.append(box("pad_rim", [3, 3, 3], [13, 3.5, 13], "#trim", "nsew", uvs={k: (0, 0, 16, 1) for k in "nsew"}))
    # side cell ports (the cells / capsules go in through the sides)
    for x0 in (0, 15):
        e.append(box("port", [x0, 4, 5], [x0 + 1, 10, 11], "#vent", "nsewud"))
    # back power conduit
    e.append(box("conduit", [5, 4, 15], [11, 11, 16], "#vent", "nsewud"))
    e.append(box("conduit_light", [7.5, 5, 16.02], [8.5, 10, 16.02], g, "s", glow=on))
    # the crown: cap, ring of light under it, the coil and its core on top
    e.append(box("cap", [1, 13, 1], [15, 15, 15], "#metal", "nsewud"))
    e.append(box("cap_glow", [4, 12.6, 4], [12, 13, 12], g, "d", glow=on))
    e.append(box("coil", [5, 15, 5], [11, 16, 11], "#trim"))
    e.append(box("core", [6.5, 16, 6.5], [9.5, 17.5, 9.5], g, "nsewu", glow=on))
    e.append(box("antenna_l", [2, 15, 2], [3, 18, 3], "#trim", "nsewu"))
    e.append(box("antenna_r", [13, 15, 13], [14, 18, 14], "#trim", "nsewu"))
    e.append(box("antenna_tip", [2, 18, 2], [3, 18.5, 3], g, "nsewu", glow=on))
    # the front control screen on the plinth (the progress bar is drawn on it)
    e.append(box("screen", [4, 0.5, -0.5], [12, 2.5, 0], "#screen", "nsewu",
                 uvs={"n": (0, 0, 16, 8)}))
    return {
        "credit": "Herois_baiduu - the Processor, modelled from scratch",
        "render_type": "minecraft:cutout",
        "textures": {
            "metal": "flash:block/processor/metal", "trim": "flash:block/processor/trim",
            "vent": "flash:block/processor/vent", "glow": "flash:block/processor/glow",
            "glow_off": "flash:block/processor/glow_off", "pad": "flash:block/processor/pad",
            "pad_off": "flash:block/processor/pad_off", "screen": "flash:block/processor/screen",
            "bottom": "flash:block/processor/bottom", "particle": "flash:block/processor/metal",
        },
        "elements": e,
        "display": {
            "gui": {"rotation": [30, 225, 0], "translation": [0, -1, 0], "scale": [0.55, 0.55, 0.55]},
            "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.25, 0.25, 0.25]},
            "fixed": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.5, 0.5, 0.5]},
            "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.375, 0.375, 0.375]},
            "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 0, 0], "scale": [0.4, 0.4, 0.4]},
            "firstperson_lefthand": {"rotation": [0, 225, 0], "translation": [0, 0, 0], "scale": [0.4, 0.4, 0.4]},
        },
    }


def processor():
    processor_textures()
    with open(M + "/block/tachyon_extractor.json", "w") as f:
        json.dump(processor_model(False), f, indent=1)
    with open(M + "/block/tachyon_extractor_on.json", "w") as f:
        json.dump(processor_model(True), f, indent=1)


capsules()
cell()
processor()
print("ok")
