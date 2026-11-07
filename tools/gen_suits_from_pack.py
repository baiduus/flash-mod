"""Builds the four Flash suits (Flash, Reverse Flash, Phantom, Zoom) from the
Flash asset-pack textures (player-skin layout, 160 units = 960 px, 6 px/unit).

  layer_1      = layer_0 (head, body, arms, boots) with the lower face covered
  open_layer_1 = layer_0_skinless (the player's own eyes and mouth show)
  layer_2      = layer_1_skinless (the trousers)
The other suits are recoloured: the red parts and the gold parts get the
colours of the character, keeping the shading.
usage: python3 tools/gen_suits_from_pack.py <pack>/assets/legends_superheroes/textures
"""
import colorsys
import sys
from PIL import Image

SRC = sys.argv[1]
OUT = "src/main/resources/assets/flash/textures/"
U = 6  # pixels per texture unit

# (main = the red of the suit, accent = the gold lightning / trims)
SUITS = {
    "flash_suit": None,
    "negative_suit": {"main": (0.135, 0.85, 1.00), "accent": (0.0, 0.92, 0.95)},
    "phantom_suit": {"main": (0.0, 0.0, 0.17), "accent": (0.0, 0.95, 0.95)},
    "zoom_suit": {"main": (0.62, 0.12, 0.20), "accent": (0.62, 0.10, 0.30), "two_tone": True},
}


def kind(r, g, b):
    h, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
    if s < 0.35 or v < 0.08:
        return None
    if h < 0.045 or h > 0.93:
        return "main"
    if 0.09 < h < 0.20:
        return "accent"
    return None


def recolor(img, pal):
    if pal is None:
        return img
    img = img.copy()
    px = img.load()
    for y in range(img.height):
        for x in range(img.width):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            k = kind(r, g, b)
            if k is None:
                continue
            h, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
            th, ts, tv = pal[k]
            # keep the shading: the source brightness relative to its usual level
            rel = v / (0.88 if k == "main" else 0.95)
            val = tv * rel
            boots = (x < 16 * U and 16 * U <= y < 32 * U) or (16 * U <= x < 32 * U and 48 * U <= y < 64 * U)
            if k == "accent" and pal.get("two_tone") and boots:
                # Zoom's boots are black too (only the lightning is blue)
                k = "main"
                th, ts, tv = pal[k]
                v = 0.62 + 0.3 * rel
            if k == "main" and pal.get("two_tone"):
                # Zoom: black and a lighter black alternate, so the details of the suit read
                val = 0.05 if v < 0.62 else 0.11 + 0.22 * min(1.0, (v - 0.62) / 0.3)
            nr, ng, nb = colorsys.hsv_to_rgb(th, ts, max(0.0, min(1.0, val)))
            px[x, y] = (int(nr * 255), int(ng * 255), int(nb * 255), a)
    return img


def cover_face(img):
    """Fills the skin-coloured lower face of the head front (8..16, 8..16) with the mask red."""
    img = img.copy()
    px = img.load()
    x0, y0 = 8 * U, 8 * U
    for x in range(x0, x0 + 8 * U):
        last = None
        for y in range(y0, y0 + 8 * U):
            r, g, b, a = px[x, y]
            h, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
            skin = a > 0 and 0.03 < h < 0.12 and s < 0.45 and v > 0.6
            if skin and y >= y0 + 5 * U and last is not None:
                px[x, y] = last
            elif not skin and a > 0 and kind(r, g, b) == "main":
                last = (r, g, b, a)
    return img


EYES = {"negative_suit": ((255, 235, 225), (220, 20, 20)), "phantom_suit": ((255, 60, 50), (200, 0, 0)),
        "zoom_suit": ((52, 55, 62), (18, 19, 24))}


def eyes(img, name):
    """Lens colours of each character (white part, coloured part) on the head front."""
    if name not in EYES:
        return img
    img = img.copy()
    px = img.load()
    white, iris = EYES[name]
    for y in range(8 * U, 16 * U):
        for x in range(8 * U, 16 * U):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            if r > 225 and g > 225 and b > 225:
                px[x, y] = white + (a,)
            elif b > 200 and r < 160:
                px[x, y] = iris + (a,)
    return img


base = SRC + "/suits/flash/"
layer0 = Image.open(base + "layer_0.png").convert("RGBA")
layer0_open = Image.open(base + "layer_0_skinless.png").convert("RGBA")
layer1 = Image.open(base + "layer_1_skinless.png").convert("RGBA")
closed = cover_face(layer0)
# colour patches for the rebuilt chest emblem (tools/fix_suit_emblem.py): disc at (100, 0), accent at (116, 0)
EMBLEM = {"flash_suit": ((245, 245, 240), (255, 205, 40)), "negative_suit": ((20, 20, 22), (225, 30, 25)),
          "phantom_suit": ((15, 15, 18), (230, 25, 20)), "zoom_suit": ((14, 14, 17), (62, 64, 72))}


def emblem(img, name):
    img = img.copy()
    px = img.load()
    disc, accent = EMBLEM[name]
    for (u0, col) in ((100, disc), (116, accent)):
        for y in range(0, 16 * U):
            for x in range(u0 * U, (u0 + 16) * U):
                k = 0.92 + 0.08 * (((x // U) + (y // U)) % 2)
                px[x, y] = (int(col[0] * k), int(col[1] * k), int(col[2] * k), 255)
    return img


for name, pal in SUITS.items():
    emblem(eyes(recolor(closed, pal), name), name).save(OUT + "models/armor/%s_layer_1.png" % name, optimize=True)
    emblem(recolor(layer0_open, pal), name).save(OUT + "models/armor/%s_open_layer_1.png" % name, optimize=True)
    recolor(layer1, pal).save(OUT + "models/armor/%s_layer_2.png" % name, optimize=True)
    for piece, src in (("helmet", "head"), ("chestplate", "chest"), ("leggings", "legs"), ("boots", "feet")):
        icon = Image.open(SRC + "/item/armors/flash/flash_%s.png" % src).convert("RGBA")
        recolor(icon, pal).save(OUT + "item/%s_%s.png" % (name, piece), optimize=True)
# glowing lenses (eyes_5) drawn full-bright over the closed mask
GLOW = {"flash_suit": (255, 255, 255), "negative_suit": (255, 70, 50), "phantom_suit": (255, 40, 30)}
eyes5 = Image.open(base + "eyes_5.png").convert("RGBA")
for name, col in GLOW.items():
    e = eyes5.copy()
    px = e.load()
    for y in range(e.height):
        for x in range(e.width):
            if px[x, y][3]:
                px[x, y] = col + (px[x, y][3],)
    e.save(OUT + "models/armor/%s_eyes.png" % name, optimize=True)
print("ok")
