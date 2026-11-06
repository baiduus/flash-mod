"""V-9 textures: the negative syringe / capsule recoloured to the Velocity-9 blue."""
import colorsys
from PIL import Image

T = "src/main/resources/assets/flash/textures/item/"


def to_blue(src, dst):
    img = Image.open(T + src).convert("RGBA")
    px = img.load()
    for y in range(img.height):
        for x in range(img.width):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            h, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
            if s > 0.35 and (h < 0.11 or h > 0.88):  # the red parts
                h = 0.56  # cyan-blue
                s = min(1.0, s * 0.95)
                v = min(1.0, v * 1.12 + 0.05)
                r2, g2, b2 = colorsys.hsv_to_rgb(h, s, v)
                px[x, y] = (int(r2 * 255), int(g2 * 255), int(b2 * 255), a)
    img.save(T + dst)


to_blue("syringe_negative.png", "syringe_v9.png")
to_blue("negative_capsule.png", "v9_capsule.png")
