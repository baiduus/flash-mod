#!/usr/bin/env python3
"""The Tachyon Amplifier, redone from scratch: a chest reactor worn on a harness.

An armoured back plate, an octagonal accelerator ring around a recessed glowing core, a spinning tachyon rotor in
front of it, two capacitor tubes on the sides and straps over the shoulders. One 32x16 atlas per colour of the core
(it follows the wearer's lightning: positive -> red, negative -> yellow, V-9 -> blue; the item is blue), drawn by
FlashAmplifierLayer and by the 3D item model (models/item/tachyon_amplifier.json).

Atlas layout (pixels):
  (0,0) 8x8   back plate front        (8,0)  plate side texel
  (10,0) 4x4  core front              (16..23,0) single texels (see TEX)
  (24,0) 2x6  capacitor tube front    (0,8)  8x8 item: plate front with the ring painted (unused by the layer)

    python3 tools/gen_amplifier_v2.py
"""
import math
import random

from PIL import Image

OUT = "src/main/resources/assets/flash/textures/"
random.seed(11)

STEEL_D = (0x2a, 0x2e, 0x36)
STEEL = (0x4a, 0x50, 0x5b)
STEEL_L = (0x86, 0x8e, 0x9b)
STEEL_XL = (0xc4, 0xca, 0xd3)
STRAP = (0x26, 0x22, 0x24)

CORES = {
    "positive": ((0x9c, 0x10, 0x10), (0xff, 0x46, 0x2e), (0xff, 0xd6, 0xa0)),
    "negative": ((0xa0, 0x6a, 0x00), (0xff, 0xc8, 0x20), (0xff, 0xfa, 0xd8)),
    "v9": ((0x0c, 0x4a, 0xb0), (0x3c, 0xb4, 0xff), (0xe0, 0xf8, 0xff)),
}


def jitter(c, k=6):
    return tuple(max(0, min(255, v + random.randint(-k, k))) for v in c) + (255,)


def mix(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3)) + (255,)


def atlas(core):
    dark, mid, hot = core
    im = Image.new("RGBA", (32, 16), (0, 0, 0, 0))
    px = im.load()
    # back plate: brushed steel, darker bevel, panel seam and four rivets
    for y in range(8):
        for x in range(8):
            edge = x in (0, 7) or y in (0, 7)
            c = STEEL_D if edge else (STEEL_L if y == 1 else STEEL)
            if y == 4 and 1 <= x <= 6:
                c = STEEL_D
            px[x, y] = jitter(c, 4)
    for x, y in ((1, 1), (6, 1), (1, 6), (6, 6)):
        px[x, y] = STEEL_XL + (255,)
    px[8, 0] = STEEL_D + (255,)
    # core: hot centre fading out to the colour
    for y in range(4):
        for x in range(4):
            d = math.hypot(x - 1.5, y - 1.5) / 2.2
            px[10 + x, y] = mix(hot, mid, min(1.0, d))
    tex = [STEEL_D, STEEL, STEEL_L, mid, hot, STRAP, dark, STEEL_XL]
    for i, c in enumerate(tex):
        px[16 + i, 0] = c + (255,)
    # capacitor tube: glass with the glow in bands
    for y in range(6):
        for x in range(2):
            px[24 + x, y] = mix(hot, mid, 0.35) if y % 2 == 0 else mix(mid, dark, 0.4)
    # item plate (front view of the whole device for the 3D item's back face)
    for y in range(8):
        for x in range(8):
            px[x, 8 + y] = jitter(STEEL, 4)
    return im


def main():
    for name, core in CORES.items():
        atlas(core).save(OUT + "misc/tachyon_amp_" + name + ".png")
    atlas(CORES["v9"]).save(OUT + "item/tachyon_amplifier_model.png")
    print("ok")


main()
