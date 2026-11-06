"""Textures of the Acceleration Force portal (positive = gold, negative = red).
usage: python3 gen_portal.py <assets/flash/textures/effect dir>"""
import math, random, sys
from PIL import Image, ImageFilter

out = sys.argv[1]
random.seed(7)

THEMES = {
    "pos": {"outer": (255, 120, 10), "mid": (255, 190, 40), "inner": (255, 245, 170), "hot": (255, 255, 235),
            "core_c": (255, 215, 110), "core_e": (255, 110, 8)},
    "neg": {"outer": (120, 4, 16), "mid": (230, 24, 36), "inner": (255, 110, 100), "hot": (255, 225, 215),
            "core_c": (30, 0, 6), "core_e": (200, 12, 28)},
}

def lerp(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))

def grad(th, t):
    """outer -> mid -> inner -> hot along t (0 outer .. 1 centre)."""
    if t < 0.4:
        return lerp(th["outer"], th["mid"], t / 0.4)
    if t < 0.8:
        return lerp(th["mid"], th["inner"], (t - 0.4) / 0.4)
    return lerp(th["inner"], th["hot"], (t - 0.8) / 0.2)

def swirl(name, th, size=256, arms=5, twist=3.2):
    im = Image.new("RGBA", (size, size), (0, 0, 0, 0)); px = im.load()
    c = (size - 1) / 2
    for y in range(size):
        for x in range(size):
            dx, dy = (x - c) / c, (y - c) / c
            r = math.hypot(dx, dy)
            if r > 1.0 or r < 0.03:
                continue
            th_a = math.atan2(dy, dx)
            v = 0.5 + 0.5 * math.cos(arms * th_a - twist * math.log(r + 0.05) * 3.0)
            v = v ** 2.2
            fall = (1 - r) ** 0.8 * min(1.0, r * 6)
            a = max(0.0, min(1.0, v * fall * 1.35))
            col = grad(th, 1 - r)
            px[x, y] = col + (int(255 * a),)
    return im.filter(ImageFilter.GaussianBlur(0.6))

def core(name, th, size=128):
    im = Image.new("RGBA", (size, size), (0, 0, 0, 0)); px = im.load()
    c = (size - 1) / 2
    for y in range(size):
        for x in range(size):
            r = math.hypot((x - c) / c, (y - c) / c)
            if r > 1.0:
                continue
            col = lerp(th["core_c"], th["core_e"], min(1.0, r ** 1.5))
            a = 0.62 * (1 - r ** 2.5) if name == "pos" else (0.92 - 0.55 * r ** 2) * (1 - r ** 6)
            px[x, y] = col + (int(255 * max(0, min(1, a))),)
    return im

def ring(name, th, w=128, h=32):
    im = Image.new("RGBA", (w, h), (0, 0, 0, 0)); px = im.load()
    phase = [random.random() * 6.28 for _ in range(4)]
    for x in range(w):
        u = x / w
        # energy flowing along the ring: tileable (integer number of cycles)
        e = 0.55 + 0.25 * math.sin(2 * math.pi * 3 * u + phase[0]) + 0.20 * math.sin(2 * math.pi * 7 * u + phase[1])
        for y in range(h):
            v = (y + 0.5) / h * 2 - 1
            core_line = math.exp(-(v / 0.18) ** 2)
            halo = math.exp(-(v / 0.62) ** 2)
            i = min(1.0, core_line * (0.85 + 0.3 * e) + halo * 0.45 * e)
            a = min(1.0, core_line * 1.0 + halo * 0.7 * e)
            col = grad(th, 0.25 + 0.75 * i)
            px[x, y] = col + (int(255 * max(0, min(1, a))),)
    return im

for name, th in THEMES.items():
    suffix = "" if name == "pos" else "_negative"
    swirl(name, th).save(f"{out}/portal_swirl{suffix}.png")
    core(name, th).save(f"{out}/portal_core{suffix}.png")
    ring(name, th).save(f"{out}/portal_ring{suffix}.png")
