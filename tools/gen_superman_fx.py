"""Slices the Superman pack effect textures into mod textures.

usage: python3 tools/gen_superman_fx.py <pack>/efeitos/texturas <mod>/src/main/resources/assets/flash
"""
import json
import os
import sys

from PIL import Image

src, dst = sys.argv[1], sys.argv[2]
part = os.path.join(dst, "textures/particle/superman")
fx = os.path.join(dst, "textures/fx/superman")
eff = os.path.join(dst, "textures/mob_effect")
for d in (part, fx, eff):
    os.makedirs(d, exist_ok=True)


def load(name):
    return Image.open(os.path.join(src, name)).convert("RGBA")


def cells(img, n):
    w, h = img.size[0] // n, img.size[1] // n
    return [img.crop((c * w, r * h, (c + 1) * w, (r + 1) * h)) for r in range(n) for c in range(n)]


def save(img, name, folder=part):
    img.save(os.path.join(folder, name + ".png"))


b = cells(load("fx/breath39_fx.png"), 4)
for i in range(4):
    save(b[i], "cloud_%d" % i)
save(b[4], "dot")
save(b[5], "sparkle")
save(b[6], "snowflake")
save(b[7], "ice_crystal")
for i in range(4):
    save(b[8 + i], "frost_%d" % i)
save(b[12], "streak")
for i, c in enumerate(cells(load("fx/puff37.png"), 2)):
    save(c, "puff_%d" % i)
for i, c in enumerate(cells(load("fx/flight46/dust.png"), 2)):
    save(c, "dust_%d" % i)
for i, c in enumerate(cells(load("fx/flight46/vapor.png"), 2)):
    save(c, "vapor_%d" % i)
save(load("fx/glow37.png"), "glow")
solar = load("fx/solar38.png")
save(solar.crop((32, 64, 96, 128)), "solar_star")
save(load("fx/flight46/spray.png").crop((0, 0, 32, 96)), "spray")

for name, out in (("fx/sun40.png", "sun"), ("fx/solar38.png", "solar"), ("fx/glow37.png", "glow"),
                  ("fx/flight46/ring.png", "ring"), ("fx/flight46/trail.png", "trail"),
                  ("fx/breath39_flow.png", "breath_flow"), ("clash43/atlas.png", "clash")):
    save(load(name), out, fx)
for name in ("frozen", "bleeding"):
    save(load("mob_effect/%s.png" % name), name, eff)

types = {
    "sm_cloud": ["cloud_%d" % i for i in range(4)],
    "sm_frost_cloud": ["cloud_%d" % i for i in range(4)],
    "sm_snow": ["snowflake", "ice_crystal", "snowflake"],
    "sm_frost": ["frost_%d" % i for i in range(4)],
    "sm_heat_glow": ["glow"],
    "sm_solar_glow": ["glow"],
    "sm_sparkle": ["sparkle", "solar_star"],
    "sm_ember": ["dot"],
    "sm_puff": ["puff_%d" % i for i in range(4)],
    "sm_dust": ["dust_%d" % i for i in range(4)],
    "sm_vapor": ["vapor_%d" % i for i in range(4)],
    "sm_solar_ray": ["spray"],
}
pdir = os.path.join(dst, "particles")
os.makedirs(pdir, exist_ok=True)
for t, tex in types.items():
    with open(os.path.join(pdir, t + ".json"), "w") as f:
        json.dump({"textures": ["flash:superman/" + x for x in tex]}, f, indent=2)
print("ok", len(types), "particle types")
