"""Hulk body model (layered, like the reference) and its 128x128 texture.
Writes src/main/java/com/flash/client/HulkModel.java and textures/entity/hulk.png."""
import math
import random
from PIL import Image

random.seed(9)
TEX = 128
OUT_PNG = "src/main/resources/assets/flash/textures/entity/hulk.png"
OUT_JAVA = "src/main/java/com/flash/client/HulkModel.java"

# part -> list of (name, kind, (x, y, z), (w, h, d), inflate)
PARTS = {
    "head": [
        ("skull", "face", (-4, -8, -4), (8, 8, 8), 0.0),
        ("hair", "hair", (-4, -8.6, -4), (8, 3, 8), 0.4),
        ("brow", "brow", (-4, -5.7, -4.7), (8, 1.3, 1), 0.0),
        ("jaw", "jaw", (-3.5, -2.4, -4.5), (7, 2.4, 1), 0.0),
    ],
    "body": [
        ("traps", "skin_hi", (-5, -2.6, -2.4), (10, 3, 5), 0.0),
        ("chest", "skin", (-6.5, -0.4, -3.5), (13, 7, 7), 0.0),
        ("pec_r", "pec", (-6.1, 0.1, -4.4), (6, 4.6, 1), 0.0),
        ("pec_l", "pec", (0.1, 0.1, -4.4), (6, 4.6, 1), 0.0),
        ("abdomen", "skin", (-5, 6.5, -3), (10, 4.5, 6), 0.0),
        ("abs", "abs", (-3, 6.7, -3.7), (6, 3.6, 1), 0.0),
        ("lats", "skin_hi", (-6, 0.2, 2.6), (12, 6, 1.4), 0.0),
        ("waist", "shorts", (-5.3, 10.1, -3.3), (10.6, 2.3, 6.6), 0.0),
    ],
    "right_arm": [
        ("delt", "skin_hi", (-7.6, -3.6, -3.6), (7, 5.6, 7.2), 0.0),
        ("upper", "skin", (-6.9, 1.6, -3.0), (5.6, 4.4, 6), 0.0),
        ("bicep", "skin_hi", (-6.5, 1.9, -3.7), (4.8, 3.4, 1), 0.0),
        ("fore", "skin", (-7.4, 5.6, -3.5), (6.6, 4.8, 7), 0.0),
        ("fist", "fist", (-7.1, 10.2, -3.2), (6, 3.6, 6.4), 0.0),
    ],
    "right_leg": [
        ("thigh", "shorts", (-3.7, -0.2, -3.3), (6.3, 6.6, 6.6), 0.0),
        ("hem", "shorts_torn", (-4.0, 4.4, -3.6), (6.9, 3, 7.2), 0.0),
        ("calf", "skin", (-3.3, 6.4, -3.0), (5.7, 4.2, 6), 0.0),
        ("foot", "foot", (-3.5, 10.3, -3.7), (6.1, 1.7, 6.8), 0.0),
    ],
}
MIRROR = {"left_arm": "right_arm", "left_leg": "right_leg"}
PIVOT = {"head": (0, 0, 0), "body": (0, 0, 0), "right_arm": (-5, 2, 0), "left_arm": (5, 2, 0),
         "right_leg": (-1.9, 12, 0), "left_leg": (1.9, 12, 0)}

# ---------------- packing ----------------
def unwrap(w, h, d):
    return 2 * d + 2 * w, d + h


cubes = []
for part, lst in PARTS.items():
    for c in lst:
        name, kind, pos, size, inf = c
        W, H, D = [int(math.ceil(v - 1e-6)) for v in size]
        cubes.append([part, name, kind, pos, size, inf, (W, H, D)])

order = sorted(range(len(cubes)), key=lambda i: -unwrap(*cubes[i][6])[1])
x = y = rowh = 0
for i in order:
    W, H, D = cubes[i][6]
    uw, uh = unwrap(W, H, D)
    if x + uw > TEX:
        x = 0
        y += rowh
        rowh = 0
    cubes[i].append((x, y))
    x += uw
    rowh = max(rowh, uh)
assert y + rowh <= TEX, "texture too small"

# ---------------- painting ----------------
img = Image.new("RGBA", (TEX, TEX), (0, 0, 0, 0))
px = img.load()
G = [(46, 104, 30), (58, 124, 36), (72, 146, 44), (88, 168, 54), (108, 190, 66), (132, 212, 84)]
HAIR = [(16, 16, 20), (26, 26, 32), (38, 38, 46)]
P = [(64, 22, 92), (86, 32, 120), (108, 46, 148), (130, 64, 172)]


def g(i, x, y):
    j = i + (1 if (x * 7 + y * 3) % 11 == 0 else 0) - (1 if (x * 5 + y * 9) % 13 == 0 else 0)
    return G[max(0, min(5, j))]


SHADE = {"top": 1, "bottom": -2, "front": 0, "back": -1, "right": -1, "left": -1}


def painter(kind, face, fw, fh):
    s = SHADE[face]

    def skin(x, y, base=2):
        v = base + s
        if face in ("front", "back", "right", "left"):
            if y == 0:
                v += 1          # light catches the top edge
            if y == fh - 1:
                v -= 1
        return g(v, x, y)

    if kind in ("skin", "skin_hi"):
        b = 3 if kind == "skin_hi" else 2
        return lambda x, y: skin(x, y, b)
    if kind == "pec":
        def f(x, y):
            if face == "front":
                if y == fh - 1:
                    return G[0]
                return g(4 if y < 2 else 3, x, y)
            return skin(x, y, 3)
        return f
    if kind == "abs":
        def f(x, y):
            if face == "front":
                if x in (fw // 2 - 1, fw // 2) or y == fh // 2:
                    return G[1]
                return g(3, x, y)
            return skin(x, y, 2)
        return f
    if kind == "fist":
        def f(x, y):
            if face == "front" and y == 0:
                return G[0]
            if face == "front" and x % 2 == 0 and y == 1:
                return G[1]
            return skin(x, y, 2)
        return f
    if kind == "foot":
        return lambda x, y: skin(x, y, 1)
    if kind == "brow":
        return lambda x, y: G[0] if face == "front" else G[1]
    if kind == "jaw":
        def f(x, y):
            if face == "front":
                # closed mouth: a line in the skin tone, just a little darker
                if y == 0 and 1 <= x <= fw - 2:
                    return G[1]
            return skin(x, y, 2)
        return f
    if kind == "face":
        FACE = ["HHHHHHHH",
                "HhHHHhHH",
                "gggggggg",
                "gggggggg",
                "gWkggWkg",
                "ggggnngg",
                "gggggggg",
                "gggggggg"]

        def f(x, y):
            if face == "front":
                ch = FACE[min(7, y * 8 // fh)][min(7, x * 8 // fw)]
                if ch in "Hh":
                    return random.choice(HAIR)
                if ch == "W":
                    return (235, 240, 225)
                if ch == "k":
                    return (30, 60, 24)
                if ch == "n":
                    return G[0]
                return g(3, x, y)
            if face == "top":
                return random.choice(HAIR)
            if face == "bottom":
                return g(1, x, y)
            limit = 4 if face == "back" else 3
            return random.choice(HAIR) if y < limit else skin(x, y, 2)
        return f
    if kind == "hair":
        def f(x, y):
            if face == "top":
                return random.choice(HAIR)
            if face == "bottom":
                return None
            # jagged strands hanging down
            hang = 1 + ((x * 3 + (0 if face == "front" else 1)) % 3)
            if face == "front":
                hang = 1 if x % 3 else 2
            return random.choice(HAIR) if y < hang else None
        return f
    if kind == "shorts":
        def f(x, y):
            v = 1 + (1 if face == "top" else 0) - (1 if face == "bottom" else 0)
            c = P[max(0, min(3, v + (1 if (x * 5 + y * 7) % 9 == 0 else 0)))]
            if face in ("front", "back") and (x + 2 * y) % 7 == 0 and y > 1:
                c = P[0]                                   # cloth folds
            # green patches showing through the torn cloth, like the reference
            if face in ("front", "right", "left") and y >= fh - 3 and (x * 13 + y * 7) % 5 == 0:
                c = g(2, x, y)
            return c
        return f
    if kind == "shorts_torn":
        def f(x, y):
            if face in ("top", "bottom"):
                return None
            edge = 1 + (x * 7 + 3) % 3                    # ragged bottom edge
            if y > edge:
                return None
            if y == edge:
                return P[0]
            return P[2] if (x + y) % 4 else P[1]
        return f
    raise ValueError(kind)


def faces(u, v, W, H, D):
    return {
        "top": (u + D, v, W, D),
        "bottom": (u + D + W, v, W, D),
        "right": (u, v + D, D, H),
        "front": (u + D, v + D, W, H),
        "left": (u + D + W, v + D, D, H),
        "back": (u + D + W + D, v + D, W, H),
    }


for part, name, kind, pos, size, inf, (W, H, D), (u, v) in cubes:
    for face, (fx, fy, fw, fh) in faces(u, v, W, H, D).items():
        paint = painter(kind, face, fw, fh)
        for yy in range(fh):
            for xx in range(fw):
                c = paint(xx, yy)
                if c is not None:
                    px[fx + xx, fy + yy] = tuple(c) + (255,)
img.save(OUT_PNG)

# ---------------- java ----------------
def f(v):
    return ("%.2f" % v).rstrip("0").rstrip(".") + "F"


lines = []
for part in ("head", "body", "right_arm", "left_arm", "right_leg", "left_leg"):
    src = MIRROR.get(part, part)
    mirror = part in MIRROR
    builder = ["CubeListBuilder.create()"]
    for c in cubes:
        if c[0] != src:
            continue
        _, name, kind, (bx, by, bz), (w, h, d), inf, _, (u, v) = c
        if mirror:
            bx = -(bx + w)
        mir = ".mirror()" if mirror else ""
        deform = "new CubeDeformation(%s)" % f(inf)
        builder.append("                        /* %s */ .texOffs(%d, %d)%s.addBox(%s, %s, %s, %s, %s, %s, %s)"
                       % (name, u, v, mir, f(bx), f(by), f(bz), f(w), f(h), f(d), deform))
    pv = PIVOT[part]
    lines.append('        root.addOrReplaceChild("%s", %s,\n                PartPose.offset(%s, %s, %s));'
                 % (part, "\n".join(builder), f(pv[0]), f(pv[1]), f(pv[2])))
lines.append('        root.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);')

java = '''package com.flash.client;

import com.flash.FlashMod;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;

/**
 * The Hulk's body, drawn over the player (generated by tools/gen_hulk_model.py, do
 * not edit by hand). Built in layers like the reference: hair over the head, a
 * frowning brow and a jaw; traps, a broad chest with two pec plates, abs and lats;
 * huge shoulders, biceps, forearms thicker than the upper arm and big fists; torn
 * purple shorts with a ragged hem over green calves. The player is also drawn
 * bigger every rage level, see {@link HulkClient}.
 */
public class HulkModel extends HumanoidModel<AbstractClientPlayer> {

    public static final ModelLayerLocation LAYER =
            new ModelLayerLocation(new ResourceLocation(FlashMod.MODID, "hulk"), "main");
    public static final ResourceLocation TEXTURE =
            new ResourceLocation(FlashMod.MODID, "textures/entity/hulk.png");

    public HulkModel(ModelPart root) {
        super(root);
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
%s
        return LayerDefinition.create(mesh, %d, %d);
    }
}
''' % ("\n".join(lines), TEX, TEX)
open(OUT_JAVA, "w").write(java)
import json, sys
if len(sys.argv) > 1:
    els = []
    for part in ("head", "body", "right_arm", "left_arm", "right_leg", "left_leg"):
        src = MIRROR.get(part, part)
        pv = PIVOT[part]
        for c in cubes:
            if c[0] != src:
                continue
            _, name, kind, (bx, by, bz), (w, h, d), inf, (W, H, D), (u, v) = c
            if part in MIRROR:
                bx = -(bx + w)
            x0, y0, z0 = pv[0] + bx - inf, pv[1] + by - inf, pv[2] + bz - inf
            x1, y1, z1 = x0 + w + 2 * inf, y0 + h + 2 * inf, z0 + d + 2 * inf
            # model y down -> json y up (feet at 0, head at 32); x mirrored so we look at the front
            fr = [8 - x1, 24 - y1, 8 + z0]
            to = [8 - x0, 24 - y0, 8 + z1]
            fc = faces(u, v, W, H, D)
            def uv(k):
                fx, fy, fw, fh = fc[k]
                return [fx / TEX * 16, fy / TEX * 16, (fx + fw) / TEX * 16, (fy + fh) / TEX * 16]
            els.append({"from": fr, "to": to, "faces": {
                "north": {"uv": uv("front"), "texture": "#m"}, "south": {"uv": uv("back"), "texture": "#m"},
                "up": {"uv": uv("top"), "texture": "#m"}, "down": {"uv": uv("bottom"), "texture": "#m"},
                "east": {"uv": uv("right"), "texture": "#m"}, "west": {"uv": uv("left"), "texture": "#m"}}})
    out = sys.argv[1]
    import os
    os.makedirs(out + "/models/block", exist_ok=True); os.makedirs(out + "/textures/block", exist_ok=True)
    json.dump({"transform": {"scale": [16, 16, 16]}, "elements": els}, open(out + "/models/block/hulk.json", "w"))
    img.save(out + "/textures/block/machine_atlas.png"); img.save(out + "/textures/block/machine_glow.png")
print("cubes", len(cubes), "rows end", y + rowh)
