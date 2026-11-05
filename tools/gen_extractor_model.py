"""Builds the Tachyon Extractor models with a real window onto a hollow chamber.
usage: python3 gen_extractor_model.py <assets/flash dir>"""
import json, sys
from PIL import Image
base = sys.argv[1]

def face(uv, tex, glow=False):
    f = {"uv": uv, "texture": tex}
    if glow:
        f["forge_data"] = {"block_light": 15, "sky_light": 15}
    return f

def build(on):
    textures = {
        "front": "flash:block/tachyon_extractor_front", "back": "flash:block/tachyon_extractor_back",
        "side": "flash:block/tachyon_extractor_side", "side_left": "flash:block/tachyon_extractor_side_left",
        "top": "flash:block/tachyon_extractor_top", "bottom": "flash:block/tachyon_extractor_bottom",
        "glass": "flash:block/tachyon_extractor_glass", "particle": "flash:block/tachyon_extractor_side"}
    if on:
        textures.update({"front_e": "flash:block/tachyon_extractor_front_emissive",
                         "side_e": "flash:block/tachyon_extractor_side_emissive",
                         "side_left_e": "flash:block/tachyon_extractor_side_left_emissive",
                         "top_e": "flash:block/tachyon_extractor_top_emissive"})
    full = [0, 0, 16, 16]
    els = []
    # hollow body: every face but the front, which becomes four strips around the window
    els.append({"name": "body", "from": [0, 0, 0], "to": [16, 16, 16], "faces": {
        "south": face(full, "#back"), "east": face(full, "#side"), "west": face(full, "#side_left"),
        "up": face(full, "#top"), "down": face(full, "#bottom")}})
    for name, fr, to, uv in (
            ("front_bottom", [0, 0, 0], [16, 2, 0], [0, 14, 16, 16]),
            ("front_top", [0, 8, 0], [16, 16, 0], [0, 0, 16, 8]),
            ("front_left", [0, 2, 0], [4, 8, 0], [12, 8, 16, 14]),
            ("front_right", [12, 2, 0], [16, 8, 0], [0, 8, 4, 14])):
        els.append({"name": name, "from": fr, "to": to, "faces": {"north": face(uv, "#front")}})
    # chamber walls: each plane faces INTO the chamber so it is visible through the window
    ch = on
    els += [
        {"name": "chamber_back", "from": [4, 1, 14], "to": [12, 9, 14], "faces": {"north": face([4, 7, 12, 15], "#back", ch)}},
        {"name": "chamber_floor", "from": [4, 1, 1], "to": [12, 1, 14], "faces": {"up": face([4, 1, 12, 14], "#bottom", ch)}},
        {"name": "chamber_ceiling", "from": [4, 9, 1], "to": [12, 9, 14], "faces": {"down": face([4, 1, 12, 14], "#top", ch)}},
        {"name": "chamber_left", "from": [4, 1, 1], "to": [4, 9, 14], "faces": {"east": face([1, 7, 14, 15], "#side", ch)}},
        {"name": "chamber_right", "from": [12, 1, 1], "to": [12, 9, 14], "faces": {"west": face([1, 7, 14, 15], "#side_left", ch)}},
    ]
    els.append({"name": "coil", "from": [4, 16, 4], "to": [12, 17, 12], "faces": {
        "up": face([4, 4, 12, 12], "#top"), "north": face([4, 0, 12, 1], "#side"),
        "south": face([4, 0, 12, 1], "#side"), "east": face([4, 0, 12, 1], "#side"),
        "west": face([4, 0, 12, 1], "#side")}})
    # glass pane just in front of the opening (both sides visible)
    els.append({"name": "window_glass", "from": [4, 2, -0.25], "to": [12, 8, -0.25], "faces": {
        "north": face([0, 0, 16, 16], "#glass"), "south": face([0, 0, 16, 16], "#glass")}})
    if on:
        g = lambda uv, t: face(uv, t, True)
        els.append({"name": "body_e", "from": [-0.02, -0.02, -0.02], "to": [16.02, 16.02, 16.02], "faces": {
            "north": g(full, "#front_e"), "east": g(full, "#side_e"), "west": g(full, "#side_left_e"),
            "up": g(full, "#top_e")}})
        els.append({"name": "coil_e", "from": [4, 16.02, 4], "to": [12, 17.02, 12], "faces": {
            "up": g([4, 4, 12, 12], "#top_e"), "north": g([4, 0, 12, 1], "#side_e"),
            "south": g([4, 0, 12, 1], "#side_e"), "east": g([4, 0, 12, 1], "#side_e"),
            "west": g([4, 0, 12, 1], "#side_e")}})
    return {"credit": "Flash mod - tachyon extractor", "render_type": "minecraft:translucent",
            "textures": textures, "elements": els}

for name, on in (("tachyon_extractor", False), ("tachyon_extractor_on", True)):
    json.dump(build(on), open(f"{base}/models/block/{name}.json", "w"), indent=1)

# the lamp pixels painted inside the old window would float in front of the item
p = f"{base}/textures/block/tachyon_extractor_front_emissive.png"
im = Image.open(p).convert("RGBA"); px = im.load()
for y in range(8, 14):
    for x in range(4, 12):
        px[x, y] = (0, 0, 0, 0)
im.save(p)
