"""Acceleration Force portal, version 2 (built from scratch).

Every texture is white (or grey) with alpha; the renderer tints it per force
(positive = gold / white-hot, negative = crimson), so both portals share the art.
  portal2_void.png  - dark disc behind everything (depth / contrast)
  portal2_swirl.png - three logarithmic spiral arms with flowing turbulence
  portal2_rim.png   - bright ring with flame tongues licking outwards
  portal2_tunnel.png- thin soft ring, stacked deeper and smaller = a tunnel
  portal2_core.png  - white-hot glow at the far end of the tunnel
  portal2_spark.png - small soft spark used for embers spiralling inwards
usage: python3 tools/gen_portal_v2.py [preview.png]
"""
import math
import sys
import numpy as np
from PIL import Image

OUT = "src/main/resources/assets/flash/textures/effect/"
N = 256
rng = np.random.default_rng(11)

yy, xx = np.mgrid[0:N, 0:N]
X = (xx - (N - 1) / 2) / ((N - 1) / 2)
Y = (yy - (N - 1) / 2) / ((N - 1) / 2)
R = np.sqrt(X * X + Y * Y)
T = np.arctan2(Y, X)


def smooth(e0, e1, x):
    t = np.clip((x - e0) / (e1 - e0), 0, 1)
    return t * t * (3 - 2 * t)


def turb(theta, r, octaves=6, base=3, seed=0):
    """Smooth periodic-in-angle noise: sum of angular harmonics with radial drift."""
    g = np.random.default_rng(seed)
    out = np.zeros_like(theta)
    amp, total = 1.0, 0.0
    for o in range(octaves):
        n = base * (2 ** o) + g.integers(0, 3)
        m = g.uniform(2, 6) * (1.6 ** o)
        ph = g.uniform(0, 2 * math.pi)
        out += amp * np.sin(n * theta + m * r + ph + 0.7 * np.sin(m * 0.5 * r + ph))
        total += amp
        amp *= 0.55
    return out / total


def save(name, alpha, grey=1.0, additive=True):
    """additive textures are drawn with an additive blend that ignores alpha, so their
    colour is premultiplied (black = nothing); the others are white with alpha."""
    a = np.clip(alpha, 0, 1)
    img = np.zeros((N, N, 4), np.uint8)
    if additive:
        img[..., 0:3] = (a * 255 * grey).astype(np.uint8)[..., None]
    else:
        img[..., 0:3] = int(255 * grey)
    img[..., 3] = (a * 255).astype(np.uint8)
    Image.fromarray(img, "RGBA").save(OUT + name)
    return a


# dark void: almost opaque in the middle, soft edge
void = save("portal2_void.png", 0.97 * (1 - smooth(0.76, 0.99, R)), grey=1.0, additive=False)

# spiral arms
arms = 0.5 + 0.5 * np.cos(3 * (T + 2.6 * np.log(np.maximum(R, 1e-3))))
arms = arms ** 4
n1 = 0.5 + 0.5 * turb(T + 1.8 * np.log(np.maximum(R, 1e-3)), R * 6, seed=3)
swirl = arms * (0.45 + 0.75 * n1) + 0.25 * n1 ** 2
swirl *= smooth(0.05, 0.30, R) * (1 - smooth(0.78, 0.97, R))
swirl = save("portal2_swirl.png", swirl / swirl.max() * 0.95)

# rim with flame tongues
r0 = 0.80
band = np.exp(-((R - r0) / 0.045) ** 2)
fl = 0.5 + 0.5 * turb(T, R * 14, octaves=5, base=7, seed=5)
tongues = (fl ** 3) * np.exp(-np.maximum(0, R - r0) / 0.075) * smooth(r0 - 0.06, r0, R)
inner = 0.35 * np.exp(-np.maximum(0, r0 - R) / 0.09) * (R < r0)
rim = band * (0.75 + 0.25 * fl) + 0.9 * tongues + inner * (0.6 + 0.4 * fl)
rim *= 1 - smooth(0.96, 1.0, R)
rim = save("portal2_rim.png", rim / rim.max(), additive=False)
save("portal2_rim_add.png", rim)

# tunnel rib
tunnel = np.exp(-((R - 0.86) / 0.07) ** 2) * (0.7 + 0.3 * (0.5 + 0.5 * turb(T, R * 8, octaves=3, base=5, seed=9)))
tunnel *= 1 - smooth(0.97, 1.0, R)
tunnel = save("portal2_tunnel.png", tunnel)

# soft outer halo that lights the surroundings
halo = save("portal2_halo.png", 0.55 * np.exp(-((R - 0.62) / 0.30) ** 2) * (1 - smooth(0.9, 1.0, R)))

# white-hot core
core = save("portal2_core.png", np.exp(-(R / 0.38) ** 2) * (1 - smooth(0.9, 1.0, R)))

# spark (64 px)
sy, sx = np.mgrid[0:64, 0:64]
sr = np.sqrt(((sx - 31.5) / 31.5) ** 2 + ((sy - 31.5) / 31.5) ** 2)
spark = np.exp(-(sr / 0.30) ** 2) + 0.35 * np.exp(-(sr / 0.75) ** 2)
img = np.zeros((64, 64, 4), np.uint8)
sa = np.clip(spark * (1 - smooth(0.9, 1.0, sr)), 0, 1)
img[..., 0:3] = (sa * 255).astype(np.uint8)[..., None]
img[..., 3] = (sa * 255).astype(np.uint8)
Image.fromarray(img, "RGBA").save(OUT + "portal2_spark.png")


# ---------------------------------------------------------------- preview
def preview(path, negative=False, t=1.3):
    S = 512
    canvas = np.zeros((S, S, 3), np.float32)
    canvas[:] = np.array([0.45, 0.62, 0.85]) if not negative else np.array([0.45, 0.62, 0.85])
    canvas[S // 2:] = np.array([0.30, 0.45, 0.20])  # ground

    def layer(alpha, size, angle, color, a, mode):
        im = Image.fromarray((np.clip(alpha, 0, 1) * 255).astype(np.uint8))
        im = im.rotate(math.degrees(angle), resample=Image.BILINEAR)
        px = int(S * size)
        im = im.resize((px, px), Image.BILINEAR)
        arr = np.asarray(im, np.float32) / 255 * a
        x0 = (S - px) // 2
        sub = canvas[x0:x0 + px, x0:x0 + px]
        col = np.array(color, np.float32)
        if mode == "add":
            sub += arr[..., None] * col
        else:
            sub[:] = sub * (1 - arr[..., None]) + col * arr[..., None]

    if negative:
        hot, mid, deep, dark = (1.0, 0.85, 0.80), (1.0, 0.16, 0.12), (0.55, 0.02, 0.05), (0.06, 0.0, 0.01)
    else:
        hot, mid, deep, dark = (1.0, 0.97, 0.80), (1.0, 0.62, 0.10), (0.95, 0.30, 0.02), (0.07, 0.03, 0.0)
    layer(void, 0.78, 0, dark, 1.0, "alpha")
    # tunnel ribs: deeper = smaller and dimmer
    for i in range(6):
        k = i / 5.0
        layer(tunnel, 0.74 * (1 - 0.13 * i), t * (0.4 + 0.25 * i), deep if i < 3 else mid, 0.55 * (1 - 0.6 * k), "add")
    layer(core, 0.42, 0, hot, 0.95, "add")
    layer(swirl, 0.80, t * 0.9, mid, 0.85, "add")
    layer(swirl, 0.62, -t * 1.4 + 1.0, hot, 0.45, "add")
    layer(halo, 1.0, 0, deep, 0.55, "add")
    layer(rim, 0.98, t * 0.35, mid, 0.95, "alpha")
    layer(rim, 0.98, t * 0.35, mid, 0.45, "add")
    layer(rim, 0.95, -t * 0.55 + 2.0, hot, 0.30, "add")
    out = np.clip(canvas, 0, 1)
    Image.fromarray((out * 255).astype(np.uint8)).save(path)


if len(sys.argv) > 1:
    preview(sys.argv[1])
    preview(sys.argv[1].replace(".png", "_neg.png"), negative=True)
