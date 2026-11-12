#!/usr/bin/env python3
"""Draws Lex Luthor's green and purple war suit (armor layers, item icons, the missile) and its animations.
Run from the repository root."""
import colorsys
import json
import os
import random

from PIL import Image, ImageDraw

A = "src/main/resources/assets/flash"
T = A + "/textures"
M = A + "/models"

GREEN = (46, 140, 62)
GREEN_D = (26, 92, 40)
GREEN_L = (92, 186, 96)
PURPLE = (104, 40, 148)
PURPLE_D = (66, 22, 98)
PURPLE_L = (150, 82, 196)
CORE = (150, 255, 140)
CORE_L = (226, 255, 220)
STEEL = (70, 76, 86)


def plate(d, box, base=GREEN, dark=GREEN_D, light=GREEN_L, trim=PURPLE):
    x0, y0, x1, y1 = box
    d.rectangle([x0, y0, x1 - 1, y1 - 1], fill=base)
    d.line([(x0, y1 - 1), (x1 - 1, y1 - 1)], fill=dark)
    d.line([(x1 - 1, y0), (x1 - 1, y1 - 1)], fill=dark)
    d.line([(x0, y0), (x1 - 1, y0)], fill=light)
    if trim is not None:
        d.line([(x0, y0), (x0, y1 - 1)], fill=trim)


def noise(im, amount, seed):
    rnd = random.Random(seed)
    px = im.load()
    for y in range(im.height):
        for x in range(im.width):
            r, g, b, a = px[x, y]
            if a:
                k = rnd.randint(-amount, amount)
                px[x, y] = (max(0, min(255, r + k)), max(0, min(255, g + k)), max(0, min(255, b + k)), a)


def layer1():
    im = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    # helmet: green shell, purple crest, the visor with two glowing green eyes
    for box in [(8, 0, 16, 8), (16, 0, 24, 8), (0, 8, 8, 16), (8, 8, 16, 16), (16, 8, 24, 16), (24, 8, 32, 16)]:
        plate(d, box)
    d.rectangle([11, 0, 12, 7], fill=PURPLE)  # crest on top
    d.rectangle([24, 8, 31, 9], fill=PURPLE)
    d.rectangle([8, 8, 15, 9], fill=PURPLE)
    d.rectangle([9, 11, 14, 13], fill=(18, 24, 22))  # visor
    d.point([(10, 12), (13, 12)], fill=CORE_L)
    d.point([(9, 12), (14, 12)], fill=CORE)
    d.rectangle([10, 14, 13, 15], fill=PURPLE_D)  # jaw
    # body: green chest plates, purple belt and the Kryptonite core in the middle
    for box in [(20, 16, 28, 20), (28, 16, 36, 20), (16, 20, 20, 32), (20, 20, 28, 32), (28, 20, 32, 32), (32, 20, 40, 32)]:
        plate(d, box)
    d.rectangle([20, 29, 27, 31], fill=PURPLE)
    d.rectangle([32, 29, 39, 31], fill=PURPLE)
    d.rectangle([16, 29, 19, 31], fill=PURPLE)
    d.rectangle([28, 29, 31, 31], fill=PURPLE)
    d.line([(20, 20), (23, 24)], fill=PURPLE_L)
    d.line([(27, 20), (24, 24)], fill=PURPLE_L)
    d.ellipse([21, 22, 26, 27], fill=PURPLE_D)
    d.ellipse([22, 23, 25, 26], fill=CORE)
    d.point([(23, 24)], fill=CORE_L)
    d.rectangle([34, 22, 37, 27], fill=STEEL)  # back: the power pack
    d.point([(35, 23), (36, 23)], fill=CORE)
    # arms: green with purple forearm guards and the missile pods on the shoulders
    for box in [(44, 16, 48, 20), (48, 16, 52, 20), (40, 20, 44, 32), (44, 20, 48, 32), (48, 20, 52, 32), (52, 20, 56, 32)]:
        plate(d, box)
    d.rectangle([40, 26, 55, 29], fill=PURPLE)
    d.rectangle([44, 16, 47, 19], fill=STEEL)
    d.point([(45, 17), (46, 18)], fill=(20, 20, 24))
    d.rectangle([40, 31, 55, 31], fill=GREEN_D)
    d.point([(45, 30), (49, 30)], fill=CORE)
    # boots: purple soles, green shins
    for box in [(4, 16, 8, 20), (8, 16, 12, 20), (0, 20, 4, 32), (4, 20, 8, 32), (8, 20, 12, 32), (12, 20, 16, 32)]:
        plate(d, box)
    d.rectangle([0, 28, 15, 31], fill=PURPLE)
    d.rectangle([8, 16, 11, 19], fill=PURPLE_D)
    noise(im, 4, 7)
    im.save(T + "/models/armor/lex_layer_1.png")


def layer2():
    im = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    # legs
    for box in [(4, 16, 8, 20), (8, 16, 12, 20), (0, 20, 4, 32), (4, 20, 8, 32), (8, 20, 12, 32), (12, 20, 16, 32)]:
        plate(d, box, base=PURPLE, dark=PURPLE_D, light=PURPLE_L, trim=GREEN)
    d.rectangle([4, 23, 7, 26], fill=GREEN)  # knee plates
    d.point([(5, 24), (6, 25)], fill=GREEN_L)
    # waist
    for box in [(20, 16, 28, 20), (28, 16, 36, 20), (16, 20, 20, 32), (20, 20, 28, 32), (28, 20, 32, 32), (32, 20, 40, 32)]:
        plate(d, box, base=PURPLE, dark=PURPLE_D, light=PURPLE_L, trim=GREEN)
    d.rectangle([22, 28, 25, 31], fill=GREEN)
    noise(im, 4, 8)
    im.save(T + "/models/armor/lex_layer_2.png")


def recolor_icon(src, dst):
    """The suit icons: the shapes of the Flash suit, recoloured (red -> green, gold -> purple)."""
    im = Image.open(src).convert("RGBA")
    px = im.load()
    for y in range(im.height):
        for x in range(im.width):
            r, g, b, a = px[x, y]
            if not a:
                continue
            h, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
            if s < 0.3:
                continue
            if h < 0.06 or h > 0.92:
                nh, ns = 0.36, min(1.0, s * 0.9)
            elif h < 0.2:
                nh, ns = 0.78, min(1.0, s * 0.9)
            else:
                continue
            nr, ng, nb = colorsys.hsv_to_rgb(nh, ns, v * 0.9)
            px[x, y] = (int(nr * 255), int(ng * 255), int(nb * 255), a)
    im.save(dst)


def icons():
    for part in ("helmet", "chestplate", "leggings", "boots"):
        recolor_icon(T + f"/item/flash_suit_{part}.png", T + f"/item/lex_armor_{part}.png")
        with open(M + f"/item/lex_armor_{part}.json", "w") as f:
            json.dump({"parent": "minecraft:item/generated", "textures": {"layer0": f"flash:item/lex_armor_{part}"}},
                      f, indent=1)
    # the missile: a little purple rocket with a green tip, flying to the right
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.rectangle([3, 7, 11, 8], fill=PURPLE)
    d.line([(3, 7), (11, 7)], fill=PURPLE_L)
    d.rectangle([12, 7, 13, 8], fill=CORE)
    d.point([(14, 7), (14, 8)], fill=CORE_L)
    d.polygon([(2, 5), (4, 7), (2, 7)], fill=STEEL)
    d.polygon([(2, 10), (4, 8), (2, 8)], fill=STEEL)
    d.rectangle([0, 7, 1, 8], fill=(255, 170, 60))
    im.save(T + "/item/lex_missile.png")
    with open(M + "/item/lex_missile.json", "w") as f:
        json.dump({"parent": "minecraft:item/generated", "textures": {"layer0": "flash:item/lex_missile"}}, f, indent=1)


os.makedirs(T + "/models/armor", exist_ok=True)
layer1()
layer2()
icons()
print("ok")
