"""Zoom suit (Hunter Zolomon, The Flash season 2): an all-black suit and full black mask
with black lenses; the only colour is a faint electric-blue edge where the Flash suit has
its gold lines. Made by recolouring the Flash suit textures, so the shapes stay the same."""
import colorsys
from PIL import Image

A = "src/main/resources/assets/flash/textures/"


def zoom(path_in, path_out):
    img = Image.open(A + path_in).convert("RGBA")
    px = img.load()
    for y in range(img.height):
        for x in range(img.width):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            h, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
            hd = h * 360
            if s < 0.25 and v > 0.6:
                # white parts (eye lenses, chest circle): glossy black lenses with a blue glint
                col = (16, 20, 28)
                if v > 0.85:
                    col = (24, 38, 58)
            elif (hd < 25 or hd > 300) and s >= 0.25:
                # suit red -> matte black / charcoal, keeping the shading
                k = 0.05 + 0.17 * v
                col = (int(255 * k * 0.92), int(255 * k * 0.94), int(255 * k))
            else:
                # gold / tan lines -> dark gunmetal with a cold blue tint
                k = 0.09 + 0.16 * v
                col = (int(255 * k * 0.80), int(255 * k * 0.92), int(255 * k * 1.15))
            px[x, y] = (min(255, col[0]), min(255, col[1]), min(255, col[2]), a)
    img.save(A + path_out)


for suffix in ("layer_1", "layer_2", "open_layer_1"):
    zoom(f"models/armor/flash_suit_{suffix}.png", f"models/armor/zoom_suit_{suffix}.png")

POS = {"R": (176, 20, 36), "D": (92, 8, 22), "G": (242, 196, 78), "L": (255, 235, 170)}
Z = {(176, 20, 36): (34, 36, 42), (92, 8, 22): (12, 13, 16), (242, 196, 78): (40, 92, 150),
     (255, 235, 170): (90, 150, 210)}
for part in ("helmet", "chestplate", "leggings", "boots"):
    img = Image.open(A + f"item/flash_suit_{part}.png").convert("RGBA")
    px = img.load()
    for y in range(16):
        for x in range(16):
            r, g, b, a = px[x, y]
            if a and (r, g, b) in Z:
                px[x, y] = Z[(r, g, b)] + (a,)
    img.save(A + f"item/zoom_suit_{part}.png")
