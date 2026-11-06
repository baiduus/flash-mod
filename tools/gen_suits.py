"""Generates armor layer textures and item icons for the Flash suits.
usage: python3 gen_suits.py skin_red.png out_assets_dir
Positive suit = the supplied red skin; negative suit = red<->gold colour swap."""
import sys, colorsys
from PIL import Image

skin = Image.open(sys.argv[1]).convert("RGBA")
out = sys.argv[2]

def swap(im):
    im = im.copy(); px = im.load()
    for y in range(im.height):
        for x in range(im.width):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            h, s, v = colorsys.rgb_to_hsv(r/255, g/255, b/255)
            hd = h*360
            if hd < 25 or hd > 300:      # red -> gold
                nh = 46/360; ns = min(1, s*0.95); nv = min(1, v*1.45+0.12)
            else:                        # gold / tan -> deep red
                nh = 355/360; ns = min(1, max(s, 0.7)); nv = v*0.8
            r2, g2, b2 = colorsys.hsv_to_rgb(nh, ns, nv)
            px[x, y] = (int(r2*255), int(g2*255), int(b2*255), a)
    return im

def clear(im, box):
    px = im.load()
    for y in range(box[1], box[3]):
        for x in range(box[0], box[2]):
            px[x, y] = (0, 0, 0, 0)

def layers(base):
    base = base.crop((0, 0, 64, 32)).copy()
    # layer 1: helmet (head), chestplate (body+arms), boots (low legs)
    l1 = base.copy()
    # close the mouth/chin area with the suit colour (it used to be left open)
    from collections import Counter
    lp = l1.load()
    main = Counter(lp[x, y] for x in list(range(0, 8)) + list(range(16, 32))
                   for y in range(8, 13) if lp[x, y][3] > 0).most_common(1)[0][0]
    for y in range(13, 16):
        for x in range(8, 16):
            lp[x, y] = main
    clear(l1, (0, 16, 16, 27))        # legs: keep only boots (bottom rows)
    clear(l1, (0, 16, 16, 20))
    # layer 2: leggings (waist + legs)
    l2 = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    l2.paste(base.crop((0, 16, 16, 32)), (0, 16))     # full legs
    l2.paste(base.crop((16, 16, 40, 32)).crop((0, 8, 24, 16)), (16, 24))  # waist rows
    return l1, l2

# ---- item icons (16x16, ascii templates; R=main, D=dark, G=gold, L=light) ----
ICONS = {
 "helmet": [
 "................",
 "....DDDDDDDD....",
 "...DRRRRRRRRD...",
 "..DRRRRRRRRRRD..",
 "..DRRGRRRRGRRD..",
 "..DRRRRRRRRRRD..",
 "..DRRRRRRRRRRD..",
 "..DRRLLRRLLRRD..",
 "..DRRRRRRRRRRD..",
 "..DRR......RRD..",
 "..DRR......RRD..",
 "..DDD......DDD..",
 "................","................","................","................"],
 "chestplate": [
 "................",
 "..DDD......DDD..",
 ".DRRRD....DRRRD.",
 ".DRRRRDDDDRRRRD.",
 ".DRRRRRRRRRRRRD.",
 ".DRRRRRLLRRRRRD.",
 ".DRRRRLGGLRRRRD.",
 "..DRRRRLLRRRRD..",
 "..DRRRRRRRRRRD..",
 "..DGGGGGGGGGGD..",
 "..DRRRRRRRRRRD..",
 "..DRRRRRRRRRRD..",
 "..DDDDDDDDDDDD..",
 "................","................","................"],
 "leggings": [
 "................",
 "..DDDDDDDDDDDD..",
 "..DRRRRRRRRRRD..",
 "..DGGGGGGGGGGD..",
 "..DRRRRRRRRRRD..",
 "..DRRRRRRRRRRD..",
 "..DRRRDDDDRRRD..",
 "..DRRRD..DRRRD..",
 "..DRRRD..DRRRD..",
 "..DRRRD..DRRRD..",
 "..DRRRD..DRRRD..",
 "..DRRRD..DRRRD..",
 "..DDDDD..DDDDD..",
 "................","................","................"],
 "boots": [
 "................","................","................","................",
 "................",
 "..DDDD....DDDD..",
 "..DRRD....DRRD..",
 "..DRRD....DRRD..",
 "..DRRD....DRRD..",
 ".DRRRD....DRRRD.",
 ".DGGGD....DGGGD.",
 ".DRRRRD..DRRRRD.",
 ".DDDDDD..DDDDDD.",
 "................","................","................"],
}
POS = {"R": (176, 20, 36), "D": (92, 8, 22), "G": (242, 196, 78), "L": (255, 235, 170)}

def icon(rows, pal):
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0)); px = im.load()
    for y, row in enumerate(rows):
        for x, c in enumerate(row):
            if c in pal:
                px[x, y] = pal[c] + (255,)
    return im

NEG = {"R": (244, 196, 52), "D": (150, 110, 10), "G": (190, 22, 34), "L": (255, 240, 170)}

import os
os.makedirs(out + "/textures/models/armor", exist_ok=True)
os.makedirs(out + "/textures/item", exist_ok=True)
os.makedirs(out + "/models/item", exist_ok=True)
for name, src in (("flash_suit", skin), ("negative_suit", swap(skin))):
    l1, l2 = layers(src)
    l1.save(f"{out}/textures/models/armor/{name}_layer_1.png")
    l2.save(f"{out}/textures/models/armor/{name}_layer_2.png")
for name, pal in (("flash_suit", POS), ("negative_suit", NEG)):
    for part, rows in ICONS.items():
        icon(rows, pal).save(f"{out}/textures/item/{name}_{part}.png")
        open(f"{out}/models/item/{name}_{part}.json", "w").write(
            '{\n  "parent": "minecraft:item/generated",\n  "textures": {\n    "layer0": "flash:item/%s_%s"\n  }\n}\n' % (name, part))
