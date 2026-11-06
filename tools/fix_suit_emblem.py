"""Copies the Flash suit geo of the asset pack with a rebuilt chest emblem.

The pack's emblem was 2 disc cubes and 5 tilted bolt cubes all within 0.03 px of
depth (they fought each other and the chest). Here it is a clean octagonal ring,
a disc in front of it and a zig-zag bolt in front of the disc, each layer a
quarter pixel apart. The cubes use solid colour patches painted by
gen_suits_from_pack.py: DISC at (100, 0) and ACCENT at (116, 0).
usage: python3 tools/fix_suit_emblem.py <pack>/geo/suits/flash/flash.geo.json
"""
import json
import sys

DISC = [100, 0]
ACCENT = [116, 0]
src = json.load(open(sys.argv[1]))
geo = src["minecraft:geometry"][0]
bones = [b for b in geo["bones"] if not b["name"].startswith("logo")]
for b in bones:
    if b["name"] == "body":
        b["cubes"] = [c for c in b["cubes"] if c["uv"] not in ([30, 63], [31, 56])]


def cube(x0, y0, x1, y1, z0, depth, uv):
    return {"origin": [x0, y0, z0], "size": [round(x1 - x0, 3), round(y1 - y0, 3), depth], "uv": uv}


cy = 21.5
emblem = {"name": "emblem", "parent": "body", "pivot": [0, 24, 0], "cubes": [
    # ring (octagon, radius 2.5)
    cube(-2.5, cy - 1.25, 2.5, cy + 1.25, -2.85, 0.3, ACCENT),
    cube(-1.25, cy - 2.5, 1.25, cy + 2.5, -2.85, 0.3, ACCENT),
    cube(-2.0, cy - 2.0, 2.0, cy + 2.0, -2.85, 0.3, ACCENT),
    # disc (octagon, radius 2)
    cube(-2.0, cy - 1.0, 2.0, cy + 1.0, -3.1, 0.25, DISC),
    cube(-1.0, cy - 2.0, 1.0, cy + 2.0, -3.1, 0.25, DISC),
    cube(-1.6, cy - 1.6, 1.6, cy + 1.6, -3.1, 0.25, DISC),
    # bolt (zig-zag from the top right to the bottom left)
    cube(0.1, cy + 0.9, 0.9, cy + 1.8, -3.35, 0.25, ACCENT),
    cube(-0.3, cy + 0.1, 0.5, cy + 1.0, -3.35, 0.25, ACCENT),
    cube(-0.3, cy - 0.25, 0.9, cy + 0.25, -3.35, 0.25, ACCENT),
    cube(-0.1, cy - 1.0, 0.7, cy - 0.15, -3.35, 0.25, ACCENT),
    cube(-0.9, cy - 1.8, -0.0, cy - 0.9, -3.35, 0.25, ACCENT),
]}
bones.insert([b["name"] for b in bones].index("body") + 1, emblem)
geo["bones"] = bones
json.dump(src, open("src/main/resources/assets/flash/geo/flash_suit.geo.json", "w"), indent=1)
print("ok", len(bones))
