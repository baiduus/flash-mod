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
    "zoom_suit": {"main": (0.62, 0.35, 0.20), "accent": (0.58, 0.85, 1.00)},
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
            nr, ng, nb = colorsys.hsv_to_rgb(th, ts, max(0.0, min(1.0, tv * rel)))
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
        "zoom_suit": ((235, 240, 245), (20, 22, 30))}


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
for name, pal in SUITS.items():
    eyes(recolor(closed, pal), name).save(OUT + "models/armor/%s_layer_1.png" % name, optimize=True)
    recolor(layer0_open, pal).save(OUT + "models/armor/%s_open_layer_1.png" % name, optimize=True)
    recolor(layer1, pal).save(OUT + "models/armor/%s_layer_2.png" % name, optimize=True)
    for piece, src in (("helmet", "head"), ("chestplate", "chest"), ("leggings", "legs"), ("boots", "feet")):
        icon = Image.open(SRC + "/item/armors/flash/flash_%s.png" % src).convert("RGBA")
        recolor(icon, pal).save(OUT + "item/%s_%s.png" % (name, piece), optimize=True)
print("ok")
