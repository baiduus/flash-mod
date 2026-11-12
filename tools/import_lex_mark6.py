#!/usr/bin/env python3
"""Lex Luthor's war suit built on the Iron Man Mark 6 of Legends: Superheroes (used with permission, private use):
the Mark 6 model and texture recoloured to Lex's green and purple, the glow pass, the homing missile model, the
Stark HUD textures recoloured to green, the ability icons and the Iron Man sounds.

    python3 tools/import_lex_mark6.py <extracted Legends-Superheroes jar>
"""
import colorsys
import json
import os
import shutil
import sys

from PIL import Image

SRC = sys.argv[1] + "/assets/legends_superheroes"
A = "src/main/resources/assets/flash"


def hsv_map(im, fn):
    im = im.convert("RGBA")
    px = im.load()
    for y in range(im.height):
        for x in range(im.width):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            h, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
            out = fn(h, s, v)
            if out is not None:
                nr, ng, nb = colorsys.hsv_to_rgb(*out)
                px[x, y] = (int(nr * 255), int(ng * 255), int(nb * 255), a)
    return im


def lex_colors(h, s, v):
    """Iron Man red -> Lex green, Iron Man gold -> Lex purple, the white lights -> Kryptonite green."""
    if s < 0.18:
        if v > 0.85:
            return 0.33, 0.35, v  # lights: pale green
        return None  # metal greys stay
    if h < 0.05 or h > 0.93:
        return 0.36, min(1.0, s * 0.85), v * 0.85  # red plates -> green
    if 0.05 <= h < 0.17:
        return 0.79, min(1.0, s * 1.1), v * 0.8  # gold -> purple
    if 0.45 < h < 0.7:
        return 0.36, s, v  # blue lights -> green
    return None


def glow(im):
    """Only the lights (eyes, arc reactor, repulsors): bright, low saturation pixels."""
    im = im.convert("RGBA")
    out = Image.new("RGBA", im.size, (0, 0, 0, 0))
    src = im.load()
    dst = out.load()
    for y in range(im.height):
        for x in range(im.width):
            r, g, b, a = src[x, y]
            if a == 0:
                continue
            h, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
            if (v > 0.88 and s < 0.25) or (0.45 < h < 0.7 and s > 0.35 and v > 0.6):
                nr, ng, nb = colorsys.hsv_to_rgb(0.33, 0.55, 1.0)
                dst[x, y] = (int(nr * 255), int(ng * 255), int(nb * 255), 255)
    return out


def main():
    os.makedirs(A + "/geo/lex", exist_ok=True)
    os.makedirs(A + "/textures/lex", exist_ok=True)
    os.makedirs(A + "/textures/gui/lex", exist_ok=True)
    os.makedirs(A + "/sounds/lex", exist_ok=True)
    shutil.copy(SRC + "/geo/item/ironman/mark6.geo.json", A + "/geo/lex/mark6.geo.json")
    # the bone animations of the suit (faceplate, back thrusters, wrist lasers, overdrive)
    shutil.copy(SRC + "/animations/item/armor/iron_man/mark6.animation.json", A + "/geo/lex/mark6.animation.json")
    base = Image.open(SRC + "/textures/suits/iron_man_mark6/mark6_skinless.png")
    hsv_map(base, lex_colors).save(A + "/textures/lex/mark6.png")
    glow(base).save(A + "/textures/lex/mark6_glow.png")
    # item icons: the Mark 6 pieces of Legends, recoloured
    for src, dst in (("head", "helmet"), ("chest", "chestplate"), ("legs", "leggings"), ("feet", "boots")):
        hsv_map(Image.open(SRC + f"/textures/item/armors/iron_man_mark6/iron_man_{src}.png"), lex_colors).save(
            A + f"/textures/item/lex_armor_{dst}.png")
    # the homing missile: dark purple body, green lights
    shutil.copy(SRC + "/geo/homing_missile.geo.json", A + "/geo/lex/missile.geo.json")

    def missile(h, s, v):
        if 0.08 <= h < 0.18 and s > 0.4:
            return 0.33, 0.8, min(1.0, v * 1.1)
        if s < 0.2:
            return 0.78, 0.25, v
        return None

    hsv_map(Image.open(SRC + "/textures/entity/homing_missile.png"), missile).save(A + "/textures/lex/missile.png")
    # the Stark HUD, cyan -> Lex green
    for n in ("default", "rectile", "targeting", "hud_panel", "minimap_radar", "info", "vitals", "hud_arrow"):
        im = hsv_map(Image.open(SRC + f"/textures/gui/ironman/{n}.png"),
                     lambda h, s, v: (0.36, s, v) if 0.4 < h < 0.62 else None)
        im.save(A + f"/textures/gui/lex/{n}.png")
    # skill icons
    for n in ("lasers", "missiles", "micro_missiles", "repulsor_shield"):
        shutil.copy(SRC + f"/textures/item/icons/{n}.png", A + f"/textures/gui/lex/icon_{n}.png")
    sounds = ["repulsor_blast1", "repulsor_blast2", "repulsor_blast3", "repulsor_beam", "repulsor_continuous",
              "repulsor_charge", "repulsor_impact", "unibeam_charge", "unibeam_blast1", "unibeam_powerdown", "laser",
              "missile", "micromissilering", "armor_overdrive", "armor_overdrive_off", "ironman_helmet_open",
              "ironman_helmet_close", "ironman_powerdown", "ironman_recharge", "jetpack_thrust_on_1",
              "jetpack_thrust_on_2", "jetpack_thrust_on_3", "jetpack_thrust_loop", "jetpack_thrust_off_1",
              "jetpack_thrust_off_2", "jetpack_thrust_off_3"]
    for n in sounds:
        shutil.copy(SRC + f"/sounds/{n}.ogg", A + f"/sounds/lex/{n}.ogg")
    p = A + "/sounds.json"
    with open(p) as f:
        d = json.load(f)
    events = {
        "repulsor_blast": ["repulsor_blast1", "repulsor_blast2", "repulsor_blast3"],
        "repulsor_beam": ["repulsor_beam"], "repulsor_continuous": ["repulsor_continuous"],
        "repulsor_charge": ["repulsor_charge"], "repulsor_impact": ["repulsor_impact"],
        "unibeam_charge": ["unibeam_charge"], "unibeam_blast": ["unibeam_blast1"],
        "unibeam_powerdown": ["unibeam_powerdown"], "laser": ["laser"], "missile": ["missile"],
        "micromissiles": ["micromissilering"], "overdrive": ["armor_overdrive"], "overdrive_off": ["armor_overdrive_off"],
        "helmet_open": ["ironman_helmet_open"], "helmet_close": ["ironman_helmet_close"],
        "powerdown": ["ironman_powerdown"], "recharge": ["ironman_recharge"],
        "thrust_start": ["jetpack_thrust_on_1", "jetpack_thrust_on_2", "jetpack_thrust_on_3"],
        "thrust_loop": ["jetpack_thrust_loop"],
        "thrust_end": ["jetpack_thrust_off_1", "jetpack_thrust_off_2", "jetpack_thrust_off_3"],
    }
    for k, names in events.items():
        d["lex." + k] = {"sounds": [{"name": "flash:lex/" + n} for n in names], "subtitle": "subtitles.flash.lex"}
    with open(p, "w") as f:
        json.dump(d, f, indent=2)
    print("ok")


main()
