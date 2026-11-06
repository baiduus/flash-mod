"""Ghost of the Acceleration Force (Black Flash style): its suit (the Zoom suit with the
grey/blue details turned red), its 64x64 mob skin (decayed face) and the fragment icon."""
import colorsys
from PIL import Image

A = "src/main/resources/assets/flash/textures/"


def reddify(src, dst):
    img = Image.open(A + src).convert("RGBA")
    px = img.load()
    for y in range(img.height):
        for x in range(img.width):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            if b > r + 6 and b > 30:  # the cold blue/grey details of the Zoom suit
                h, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
                r2, g2, b2 = colorsys.hsv_to_rgb(0.995, 0.85, min(1.0, 0.35 + v * 1.6))
                px[x, y] = (int(r2 * 255), int(g2 * 255), int(b2 * 255), a)
    img.save(A + dst)


for suffix in ("layer_1", "layer_2", "open_layer_1"):
    reddify(f"models/armor/zoom_suit_{suffix}.png", f"models/armor/phantom_suit_{suffix}.png")
for part in ("helmet", "chestplate", "leggings", "boots"):
    reddify(f"item/zoom_suit_{part}.png", f"item/phantom_suit_{part}.png")

# ---- mob skin (64x64, classic layout in the top half) ----
l1 = Image.open(A + "models/armor/phantom_suit_layer_1.png").convert("RGBA")
l2 = Image.open(A + "models/armor/phantom_suit_layer_2.png").convert("RGBA")
skin = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
skin.paste(l1, (0, 0))
# full legs from the leggings layer, boots from layer 1 on top of them
legs = l2.crop((0, 16, 16, 32))
skin.paste(legs, (0, 16))
boots = l1.crop((0, 16, 16, 32))
skin.alpha_composite(boots, (0, 16))
# no hat layer
for y in range(0, 16):
    for x in range(32, 64):
        skin.putpixel((x, y), (0, 0, 0, 0))
px = skin.load()
K = (20, 20, 22, 255)
S = (106, 94, 78, 255)    # decayed skin
s = (70, 60, 48, 255)     # cracks
W = (236, 236, 228, 255)  # eyes / teeth
w = (150, 150, 146, 255)
D = (40, 10, 12, 255)     # mouth edge
P = (196, 70, 90, 255)    # tongue
R = (210, 20, 28, 255)    # red bolt
FACE = ["KKKKKKKK",
        "KKKKKKKK",
        "KSsSSsSK",
        "KWWSSWWK",
        "KSwssSwK",
        "SDWWWWDS",
        "SDPPPPDS",
        "sSWWWWSs"]
pal = {"K": K, "S": S, "s": s, "W": W, "w": w, "D": D, "P": P}
for y, row in enumerate(FACE):
    for x, ch in enumerate(row):
        px[8 + x, 8 + y] = pal[ch]
# decay creeping on the cheeks (head sides, lower half)
for side_x in (0, 16):
    for y in range(13, 16):
        for x in range(side_x + 1, side_x + 6):
            if (x + y) % 3:
                px[x, y] = S if (x * 7 + y) % 4 else s
# red lightning "ears" on both sides of the head
EAR = [(4, 9), (3, 10), (4, 10), (2, 11), (3, 11), (4, 11), (3, 12), (2, 13)]
for (x, y) in EAR:
    px[x, y] = R
    px[16 + (7 - x), y] = R
# chest emblem: white circle with a red bolt (body front 20..27, 20..31)
EMB = [".WWW.",
       "WWWRW",
       "WWRWW",
       "WRWWW",
       ".WWW."]
for y, row in enumerate(EMB):
    for x, ch in enumerate(row):
        if ch != ".":
            px[21 + x + 1, 21 + y] = W if ch == "W" else R
skin.save(A + "entity/acceleration_phantom.png")

# ---- fragment icon ----
FRAG = ["................",
        "......KK........",
        ".....KRRK.......",
        "....KRRRRK......",
        "....KRRRRRK.....",
        "...KRRrRRRRK....",
        "...KRRRrRRRK....",
        "..KRRRRRrRRRK...",
        "..KRRrRRRRRRK...",
        "...KRRrRRRRK....",
        "....KRRRrRK.....",
        ".....KRRRK......",
        "......KRK.......",
        ".......K........",
        "................",
        "................"]
fp = {"K": (18, 18, 20, 255), "R": (44, 40, 44, 255), "r": (210, 24, 30, 255)}
icon = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
for y, row in enumerate(FRAG):
    for x, ch in enumerate(row):
        if ch in fp:
            icon.putpixel((x, y), fp[ch])
icon.save(A + "item/phantom_fragment.png")
