"""Generates the Tachyon Amplifier textures (chest device, item icon) and the prettier ring texture."""
import math
from PIL import Image, ImageDraw, ImageFilter

RES = "src/main/resources/assets/flash/textures/"
SS = 8


def device(size, pal):
    big = size * SS
    img = Image.new("RGBA", (big, big), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    c = big / 2
    u = big / 32.0

    def circle(r, fill):
        d.ellipse([c - r * u, c - r * u, c + r * u, c + r * u], fill=fill)

    # four diagonal clamps
    for sx in (-1, 1):
        for sy in (-1, 1):
            p0 = (c + sx * 14.5 * u, c + sy * 14.5 * u)
            p1 = (c + sx * 8.0 * u, c + sy * 8.0 * u)
            d.line([p0, p1], fill=pal["steel_dark"], width=int(4.6 * u))
            d.line([p0, p1], fill=pal["steel"], width=int(3.2 * u))
            q0 = (c + sx * 14.0 * u, c + sy * 13.0 * u)
            q1 = (c + sx * 8.6 * u, c + sy * 7.6 * u)
            d.line([q0, q1], fill=pal["steel_light"], width=int(1.0 * u))
            # rivet
            rx, ry = c + sx * 13.2 * u, c + sy * 13.2 * u
            d.ellipse([rx - 1.0 * u, ry - 1.0 * u, rx + 1.0 * u, ry + 1.0 * u], fill=pal["steel_dark"])

    circle(12.6, pal["steel_dark"])
    circle(11.8, pal["steel"])
    circle(10.4, pal["steel_dark"])
    circle(9.6, pal["glow_edge"])
    # glowing disc with radial gradient
    for i in range(18, 0, -1):
        r = 9.0 * i / 18.0
        t = i / 18.0
        col = tuple(int(pal["glow_core"][k] * (1 - t) + pal["glow_mid"][k] * t) for k in range(3)) + (255,)
        circle(r, col)
    circle(6.4, pal["core"])
    # inner ring
    d.ellipse([c - 6.4 * u, c - 6.4 * u, c + 6.4 * u, c + 6.4 * u], outline=pal["glow_mid"], width=int(0.8 * u))
    # lightning bolt
    bolt = [(18.6, 7.0), (10.6, 17.4), (15.2, 17.4), (13.0, 25.2), (21.6, 14.2), (16.8, 14.2), (19.6, 7.0)]
    bolt = [(x * u, y * u) for x, y in bolt]
    d.polygon(bolt, fill=pal["bolt"])
    # soft glow behind the bolt
    glow = img.filter(ImageFilter.GaussianBlur(radius=1.2 * u))
    out = Image.alpha_composite(glow.point(lambda v: v), img)
    return out.resize((size, size), Image.BOX)


BLUE = dict(steel_dark=(34, 42, 58, 255), steel=(78, 92, 116, 255), steel_light=(150, 170, 198, 255),
            glow_edge=(20, 120, 220, 255), glow_mid=(40, 170, 255), glow_core=(170, 235, 255),
            core=(10, 40, 90, 255), bolt=(235, 250, 255, 255))
RED = dict(steel_dark=(46, 30, 34, 255), steel=(104, 70, 76, 255), steel_light=(190, 140, 140, 255),
           glow_edge=(200, 30, 20, 255), glow_mid=(255, 70, 40), glow_core=(255, 190, 120),
           core=(80, 8, 8, 255), bolt=(255, 240, 200, 255))

PLATE = ["HGFFFGH",
         "GHFFFHG",
         "FFIIIFF",
         "FFIIIFF",
         "FFIIIFF",
         "GHFFFHG",
         "HGFFFGH"]
# 10x10 core (2 texels per pixel): lightning bolt from the top right to the bottom left
CORE = ["..oooooo..",
        ".occcWWWo.",
        "occcWWWcco",
        "occWWWccco",
        "ocWWWWWWco",
        "occcWWWcco",
        "occcWWccco",
        "occWWcccco",
        ".ocWcccco.",
        "..oooooo.."]

# grey metal casing, bright blue tachyon core (1 texel per model pixel, like the suits)
SUIT = dict(F=(0x6b, 0x70, 0x78), I=(0x3c, 0x40, 0x47), G=(0x8e, 0x94, 0x9e), H=(0xb8, 0xbe, 0xc6),
            o=(0x1e, 0x7c, 0xff), c=(0x5c, 0xc8, 0xff), W=(0xf4, 0xfd, 0xff),
            side=(0x4f, 0x53, 0x5a), core_side=(0x1e, 0x7c, 0xff))
NEG = dict(F=(0x6b, 0x70, 0x78), I=(0x3c, 0x40, 0x47), G=(0x8e, 0x94, 0x9e), H=(0xb8, 0xbe, 0xc6),
           o=(0xff, 0x2a, 0x1e), c=(0xff, 0x7a, 0x5a), W=(0xff, 0xf2, 0xd8),
           side=(0x4f, 0x53, 0x5a), core_side=(0xff, 0x2a, 0x1e))


def device_texture(pal, path):
    # 32x16: plate front at (0,0) 7x7, core front at (8,0) 10x10, side texels at (30,0) and (31,0)
    img = Image.new("RGBA", (32, 16), (0, 0, 0, 0))
    px = img.load()
    for y, row in enumerate(PLATE):
        for x, ch in enumerate(row):
            px[x, y] = pal[ch] + (255,)
    for y, row in enumerate(CORE):
        for x, ch in enumerate(row):
            if ch != ".":
                px[8 + x, y] = pal[ch] + (255,)
    px[30, 0] = pal["side"] + (255,)
    px[31, 0] = pal["core_side"] + (255,)
    img.save(path)


def icon(pal, path):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    for y, row in enumerate(PLATE):            # plate 7x7 scaled x2 -> 14x14
        for x, ch in enumerate(row):
            for dy in range(2):
                for dx in range(2):
                    px[1 + x * 2 + dx, 1 + y * 2 + dy] = pal[ch] + (255,)
    for y, row in enumerate(CORE):             # core 10x10 on top
        for x, ch in enumerate(row):
            if ch != ".":
                px[3 + x, 3 + y] = pal[ch] + (255,)
    img.save(path)


device_texture(SUIT, RES + "misc/tachyon_device.png")
device_texture(NEG, RES + "misc/tachyon_device_negative.png")
icon(SUIT, RES + "item/tachyon_amplifier.png")


def ring_texture():
    # 16x8: left 8x8 = gold band, right 8x8 = red gem plate with a yellow bolt
    img = Image.new("RGBA", (16, 8))
    px = img.load()
    gold = [(255, 238, 150), (255, 214, 80), (240, 180, 40), (212, 148, 24), (170, 108, 16), (120, 74, 10)]
    for y in range(8):
        for x in range(8):
            t = y / 7.0
            idx = min(5, int(t * 6))
            r, g, b = gold[idx]
            if x in (0, 7):
                r, g, b = max(0, r - 40), max(0, g - 40), max(0, b - 10)
            if (x + y) % 5 == 0 and 0 < x < 7:
                r, g, b = min(255, r + 14), min(255, g + 14), min(255, b + 10)
            px[x, y] = (r, g, b, 255)
    bolt = {(4, 1), (3, 2), (4, 2), (2, 3), (3, 3), (4, 3), (3, 4), (4, 4), (5, 4), (4, 5), (3, 6)}
    for y in range(8):
        for x in range(8):
            edge = x in (0, 7) or y in (0, 7)
            if edge:
                col = (255, 214, 80, 255) if (x + y) % 2 == 0 else (212, 148, 24, 255)
            else:
                shade = 190 - y * 12
                col = (shade, 14, 18, 255)
                if (x, y) in bolt:
                    col = (255, 236, 90, 255)
            px[8 + x, y] = col
    img.save(RES + "misc/ring_band.png")


ring_texture()
