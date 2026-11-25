"""Ultron (Age of Ultron's Prime body), made from scratch on the Lex suit's skeleton: the same root bones and
pivots as geo/lex/mark6 (Head, Body, RightArm, LeftArm, RightLeg, LeftLeg), so it follows the player's model
the same way, but every plate is new: a tall tapered head with a jaw and a crest, slanted red eyes and the
jagged mouth, a broad chest round the red core with lit chevrons, a narrow segmented waist, angled pauldrons,
tapered forearms, plated legs with knee guards and pointed feet. Chrome and graphite, red light.

Bones starting with "glow" are drawn full-bright (UltronLayer). Front is -z (as the Lex model).
usage (from the repo root): python3 tools/gen_ultron.py
"""
import math
import os
import random
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen_geo_machines as g  # noqa: E402
import gen_machines_v3 as v3  # noqa: E402

random.seed(23)
shade = g.shade
noise = g.noise


def chrome(base=(186, 192, 204), lines=True):
    """Polished alloy: a soft vertical sheen, dark panel lines on the long faces."""
    def f(face, x, y, w, h):
        k = noise() * 0.97
        if face == "top":
            k *= 1.12
        elif face == "bottom":
            k *= 0.62
        else:
            k *= 0.9 + 0.22 * math.sin(math.pi * (x + 0.3) / max(0.6, w)) - 0.12 * (y / max(1.0, h))
        if face not in ("top", "bottom") and w > 1.2 and (x < 0.5 or x > w - 0.5):
            k *= 0.78
        if lines and face not in ("top", "bottom") and h > 4 and abs(y - h * 0.55) < 0.25:
            k *= 0.55
        return shade(base, k)
    return f


def graphite(base=(44, 47, 56)):
    def f(face, x, y, w, h):
        k = noise() * (1.05 if face == "top" else 0.7 if face == "bottom" else 1.0)
        if face not in ("top", "bottom") and (int(y * 2) % 3 == 0):
            k *= 0.8  # fine ribbing of the joints
        return shade(base, k)
    return f


def face_plate():
    """Ultron's face: dark alloy with a lighter brow."""
    def f(face, x, y, w, h):
        if face != "north":
            return shade((150, 156, 168), noise())
        k = noise() * (0.92 + 0.12 * math.sin(math.pi * (x + 0.3) / max(0.6, w)))
        if 0.36 * h < y < 0.6 * h and (0.35 < x < w * 0.45 or w * 0.55 < x < w - 0.35):
            return shade((34, 30, 34), k)  # the eye sockets
        if y > 0.66 * h and w * 0.2 < x < w * 0.8:
            return shade((120, 124, 136), k)  # the mouth plate
        return shade((176, 182, 194), k)
    return f


RED = v3.lightglow((235, 20, 14), core=(255, 120, 90))
REDC = v3.lightglow((250, 36, 20), core=(255, 200, 170), alpha=250)


def ultron():
    m = g.Model("ultron", 128, 128)
    CH = chrome()
    CHD = chrome((150, 156, 168))
    CHL = chrome((206, 212, 224), lines=False)
    GR = graphite()
    FACE = face_plate()

    # ---------------- head ----------------
    head = m.bone("Head", (0, 24, 0))
    m.cube(head, (-3.4, 25.6, -3.6), (6.8, 7.4, 7.6), CH)            # skull
    m.cube(head, (-3.0, 33.0, -3.0), (6.0, 0.8, 6.6), CHL)          # crown
    m.cube(head, (-3.5, 26.2, -4.1), (7.0, 5.4, 0.6), FACE)         # face plate
    m.cube(head, (-2.6, 24.0, -3.9), (5.2, 2.4, 5.0), CHD)          # jaw
    m.cube(head, (-1.8, 23.6, -4.0), (3.6, 1.0, 2.0), CHD)          # chin
    for s in (-1, 1):
        # cheek blades and the side fins swept back
        m.cube(head, (-0.4 + s * 3.4, 24.6, -3.2), (0.8, 4.6, 4.2), CHD, rotation=(0, s * -10, 0),
               pivot=(s * 3.4, 26, -1))
        m.cube(head, (s * 3.5 - 0.35, 27.5, -1.0), (0.7, 3.6, 5.4), CHL, rotation=(-14, 0, s * -6),
               pivot=(s * 3.5, 29, 1.5))
    # crest: three plates running back over the skull
    m.cube(head, (-0.45, 32.6, -3.4), (0.9, 1.2, 7.6), CHL, rotation=(-6, 0, 0), pivot=(0, 33, 0))
    for s in (-1, 1):
        m.cube(head, (s * 1.6 - 0.3, 32.4, -2.6), (0.6, 0.9, 6.4), CH, rotation=(-6, 0, s * 8), pivot=(s * 1.6, 33, 0))
    m.cube(head, (-2.4, 26.0, 3.7), (4.8, 5.6, 0.6), CHD)           # back of the head
    eyes = m.bone("glow_eyes", (0, 29, -4), parent="Head")
    for s in (-1, 1):
        m.cube(eyes, (s * 1.65 - 1.05, 29.0, -4.3), (2.1, 0.75, 0.5), REDC, rotation=(0, 0, s * 12),
               pivot=(s * 1.65, 29.4, -4.1))
    mouth = m.bone("glow_mouth", (0, 26, -4), parent="Head")
    m.cube(mouth, (-1.4, 26.9, -4.35), (2.8, 0.3, 0.4), RED)
    for s in (-1, 1):
        m.cube(mouth, (s * 1.5 - 0.6, 26.9, -4.35), (1.2, 0.3, 0.4), RED, rotation=(0, 0, s * 28),
               pivot=(s * 1.4, 27.05, -4.15))
    for x in (-0.7, 0.0, 0.7):
        m.cube(mouth, (x - 0.12, 26.55, -4.36), (0.24, 0.45, 0.4), RED)

    # ---------------- body ----------------
    body = m.bone("Body", (0, 24, 0))
    m.cube(body, (-4.6, 18.6, -2.7), (9.2, 5.4, 5.4), CH)            # chest
    for s in (-1, 1):
        m.cube(body, (-1.9 + s * 2.6, 18.0, -3.2), (3.8, 5.6, 1.2), CHL, rotation=(0, s * 12, 0),
               pivot=(s * 2.6, 20, -2.6))                              # pectoral plates
        m.cube(body, (s * 4.9 - 1.2, 20.8, -2.4), (2.4, 3.6, 4.8), CHD)   # side of the chest
    m.cube(body, (-4.2, 23.6, -2.2), (8.4, 1.0, 4.4), GR)            # collar
    m.cube(body, (-1.3, 24.0, -1.4), (2.6, 1.2, 2.8), GR)            # neck
    m.cube(body, (-3.3, 12.6, -2.0), (6.6, 6.2, 4.0), GR)            # waist core
    for i in range(3):
        y = 13.0 + i * 1.9
        w = 5.2 + i * 0.5
        m.cube(body, (-w / 2, y, -2.45), (w, 1.5, 0.8), CH)            # abdominal plates
    for s in (-1, 1):
        m.cube(body, (s * 3.0 - 0.6, 12.6, -2.1), (1.2, 6.0, 4.2), CHD, rotation=(0, 0, s * 6),
               pivot=(s * 3.0, 15, 0))                                 # obliques
    m.cube(body, (-4.0, 10.6, -2.3), (8.0, 2.2, 4.6), CHD)           # pelvis
    m.cube(body, (-1.4, 10.0, -2.6), (2.8, 2.2, 0.8), CH)            # codpiece
    # back: spine and the shoulder blades
    m.cube(body, (-0.8, 12.6, 2.4), (1.6, 11.0, 0.8), CHL)
    for s in (-1, 1):
        m.cube(body, (s * 2.3 - 1.6, 18.4, 2.2), (3.2, 4.8, 1.0), CH, rotation=(10, s * -14, 0),
               pivot=(s * 2.3, 21, 2.6))
    core = m.bone("glow_core", (0, 21, -3), parent="Body")
    m.cube(core, (-1.0, 20.0, -3.35), (2.0, 2.0, 0.5), REDC)
    m.cube(core, (-1.0, 20.0, -3.35), (2.0, 2.0, 0.5), REDC, rotation=(0, 0, 45), pivot=(0, 21, -3.1))
    lines = m.bone("glow_lines", (0, 21, -3), parent="Body")
    for s in (-1, 1):
        # chevrons from the core to the shoulders and down to the waist
        m.cube(lines, (s * 2.4 - 1.5, 22.0, -3.3), (3.0, 0.35, 0.3), RED, rotation=(0, s * 12, s * 22),
               pivot=(s * 2.4, 22.2, -3.2))
        m.cube(lines, (s * 2.1 - 1.3, 19.3, -3.3), (2.6, 0.35, 0.3), RED, rotation=(0, s * 12, s * -26),
               pivot=(s * 2.1, 19.5, -3.2))
        m.cube(lines, (s * 1.2 - 0.15, 13.2, -2.95), (0.3, 4.6, 0.3), RED)

    # ---------------- arms ----------------
    for name, s in (("RightArm", -1), ("LeftArm", 1)):
        arm = m.bone(name, (s * 5, 22, 0))
        x0 = s * 5.9  # centre of the limb
        m.cube(arm, (x0 - 1.7, 16.2, -1.7), (3.4, 6.0, 3.4), GR)       # upper arm
        m.cube(arm, (x0 - 2.0, 17.6, -2.0), (4.0, 3.4, 4.0), CHD)      # bicep plate
        # pauldron: a shell and a ridge, angled off the shoulder
        m.cube(arm, (x0 - 2.6, 21.0, -2.6), (5.2, 2.6, 5.2), CH, rotation=(0, 0, s * -16), pivot=(x0, 22, 0))
        m.cube(arm, (x0 - 0.4 + s * 0.8, 23.3, -2.2), (0.8, 0.9, 4.4), CHL, rotation=(0, 0, s * -16),
               pivot=(x0, 22, 0))
        m.cube(arm, (x0 - 1.6, 15.4, -1.6), (3.2, 1.2, 3.2), GR)       # elbow
        m.cube(arm, (x0 - 1.4, 15.6, 1.2), (2.8, 1.4, 1.0), CHL)       # elbow guard
        m.cube(arm, (x0 - 2.0, 11.6, -2.0), (4.0, 4.2, 4.0), CH)       # forearm
        m.cube(arm, (x0 - 1.0 + s * 1.3, 12.2, -1.6), (2.0, 3.4, 3.2), CHL, rotation=(0, 0, s * 4),
               pivot=(x0, 14, 0))                                     # forearm blade
        m.cube(arm, (x0 - 1.6, 10.0, -1.6), (3.2, 1.8, 3.2), GR)       # hand
        for i in range(3):
            m.cube(arm, (x0 - 1.4 + i * 1.0, 9.0, -1.7), (0.8, 1.2, 1.2), CHD)  # fingers
        glow = m.bone("glow_" + name.lower(), (x0, 12, 0), parent=name)
        m.cube(glow, (x0 + s * 2.0 - 0.15, 12.4, -1.0), (0.3, 3.0, 2.0), RED)    # forearm light
        m.cube(glow, (x0 - 0.9, 9.95, -0.9), (1.8, 0.2, 1.8), REDC)              # palm

    # ---------------- legs ----------------
    for name, s in (("RightLeg", -1), ("LeftLeg", 1)):
        leg = m.bone(name, (s * 2, 12, 0))
        x0 = s * 2.05
        m.cube(leg, (x0 - 2.0, 6.6, -2.0), (4.0, 4.8, 4.0), CH)        # thigh
        m.cube(leg, (x0 - 1.4 + s * 0.9, 7.2, -1.8), (2.8, 3.8, 3.6), CHD)  # outer thigh plate
        m.cube(leg, (x0 - 1.7, 5.2, -1.7), (3.4, 1.6, 3.4), GR)        # knee joint
        m.cube(leg, (x0 - 1.3, 4.6, -2.6), (2.6, 2.6, 1.2), CHL, rotation=(12, 0, 0), pivot=(x0, 6, -2))  # knee
        m.cube(leg, (x0 - 1.8, 1.8, -1.8), (3.6, 3.6, 3.6), CH)        # shin
        m.cube(leg, (x0 - 1.2, 2.0, -2.3), (2.4, 3.0, 0.8), CHL)       # shin guard
        m.cube(leg, (x0 - 1.4, 2.2, 1.4), (2.8, 2.6, 1.0), CHD)        # calf
        m.cube(leg, (x0 - 1.9, 0.0, -2.2), (3.8, 1.9, 4.2), CHD)       # foot
        m.cube(leg, (x0 - 1.4, 0.0, -3.6), (2.8, 1.3, 1.5), CH)        # toe
        glow = m.bone("glow_" + name.lower(), (x0, 3, -2), parent=name)
        m.cube(glow, (x0 - 0.15, 2.3, -2.5), (0.3, 2.4, 0.3), RED)
        m.cube(glow, (x0 + s * 2.0 - 0.15, 7.6, -1.0), (0.3, 3.0, 2.0), RED)
    m.save()


# (the body itself is now tools/gen_ultron_body.py; ultron() above was the first try)


# ----------------------------------------------------------------------------------
# The surgery table: a bed-sized operating table with two robotic claws hanging from an arch, the laser in the
# middle and a monitor at the foot. World frame of a table facing south (x east, y up, z south), pixels, origin
# at the bottom centre of its head block (the foot block is z -24..-8). Saved mirrored on x like the
# Transformation Chamber (SurgeryTableRenderer flips it back).
# ----------------------------------------------------------------------------------

def surgery():
    m = g.Model("surgery_table", 256, 256)
    w = v3.World(m)
    CH = chrome()
    CHD = chrome((140, 146, 158))
    GR = graphite()
    PAD = v3.plate((52, 60, 76), seams=0, rivets=False)
    SCR = v3.screen((255, 60, 40))
    BLUE = v3.lightglow((60, 170, 255), core=(220, 245, 255))

    frame = w.bone("frame")
    w.cube(frame, (-6.5, 0, -22.5), (13, 1.5, 29), GR)                # floor plate
    w.cube(frame, (-3.5, 1.5, -18), (7, 5.5, 20), CHD)                # pedestal
    w.cube(frame, (-7.5, 7, -23.5), (15, 3, 31), CH)                  # table top
    w.cube(frame, (-6.5, 10, -23), (13, 1, 30), PAD)                  # cushion
    w.cube(frame, (-4, 11, 3.5), (8, 1, 3.5), PAD)                    # head rest
    # the arch over the chest
    for s in (-1, 1):
        w.cube(frame, (s * 8.75 - 0.75, 0, -5.5), (1.5, 29, 3), CHD)
        w.cube(frame, (s * 8.75 - 1.25, 0, -6.0), (2.5, 3, 4), GR)
    w.cube(frame, (-9.5, 27, -5.75), (19, 3, 3.5), CH)
    w.cube(frame, (-1.5, 25.5, -5.0), (3, 1.5, 2), GR)                 # laser head
    # the monitor at the foot
    w.cube(frame, (5.5, 0, -24.5), (2, 17, 2), GR)
    w.cube(frame, (3.5, 15, -25.2), (6, 5, 1.2), GR, rot=(-15, 0, 0), pivot=(6.5, 17, -24.6))

    glow = w.bone("glow_strips")
    for s in (-1, 1):
        w.cube(glow, (s * 7.55 - 0.15, 8, -23), (0.3, 1, 30), BLUE)
    w.cube(glow, (-8, 26.9, -4.9), (16, 0.3, 1.8), BLUE)
    scr = w.bone("glow_screen", (6.5, 17, -24.6))
    w.cube(scr, (4, 15.5, -25.5), (5, 4, 0.4), SCR, rot=(-15, 0, 0), pivot=(6.5, 17, -24.6))
    laser = w.bone("glow_laser", (0, 25.5, -4))
    w.cube(laser, (-0.4, 13, -4.4), (0.8, 12.5, 0.8), REDC)

    for name, s in (("arm_l", 1), ("arm_r", -1)):
        x = s * 4.0
        arm = w.bone(name, (x, 27, -4))
        w.cube(arm, (x - 1.2, 25.5, -5.2), (2.4, 2, 2.4), GR)
        w.cube(arm, (x - 0.8, 23.5, -4.8), (1.6, 3, 1.6), CH)
        fore = w.bone(name + "_fore", (x, 23.6, -4), parent=name)
        w.cube(fore, (x - 1.0, 22.6, -5.0), (2, 2, 2), GR)
        w.cube(fore, (x - 0.7, 21.0, -4.7), (1.4, 2.5, 1.4), CHD)
        claw = w.bone(name + "_claw", (x, 21, -4), parent=name + "_fore")
        w.cube(claw, (x - 1.1, 20.2, -5.1), (2.2, 1, 2.2), CH)
        for k, (dx, dz) in enumerate(((-0.7, -0.7), (0.7, -0.7), (0.0, 0.8))):
            f = w.bone("%s_f%d" % (name, k), (x + dx, 20.3, -4 + dz), parent=name + "_claw")
            w.cube(f, (x + dx - 0.25, 18.6, -4 + dz - 0.25), (0.5, 1.8, 0.5), CHD)
        tip = w.bone("glow_" + name + "_tip", (x, 20, -4), parent=name + "_claw")
        w.cube(tip, (x - 0.3, 19.6, -4.3), (0.6, 0.6, 0.6), REDC)
    m.save()

    keys, spin = g.keys, g.spin
    # (rotations here are in the geo frame: about z, positive turns +y towards +x of the geo, mirrored world)
    rest = {}
    op = {}
    for name, s in (("arm_l", 1), ("arm_r", -1)):
        rest[name] = {"rotation": keys([(0, [0, 0, -s * 35])])}
        rest[name + "_fore"] = {"rotation": keys([(0, [0, 0, -s * 45])])}
        op[name] = {"rotation": keys([(0, [0, 0, -s * 4]), (0.5, [8, 0, -s * 8]), (1.0, [0, 0, -s * 4])])}
        op[name + "_fore"] = {"rotation": keys([(0, [0, 0, s * 6]), (0.5, [-6, 0, s * 12]), (1.0, [0, 0, s * 6])])}
        for k in range(3):
            ang = 25 if k < 2 else -25
            op["%s_f%d" % (name, k)] = {"rotation": keys([(0, [ang, 0, 0]), (0.25, [0, 0, 0]),
                                                           (0.5, [ang, 0, 0]), (0.75, [0, 0, 0]),
                                                           (1.0, [ang, 0, 0])])}
    rest["glow_laser"] = {"scale": keys([(0, [0, 1, 0])])}
    op["glow_laser"] = {"scale": keys([(0, [1, 1, 1]), (0.5, [1.8, 1, 1.8]), (1.0, [1, 1, 1])])}
    g.save_anims("surgery_table", {"idle": (1.0, rest), "operate": (1.0, op)})


def nanocore_icon():
    """The Ultron nanocore: a hexagonal alloy capsule with the red core (16 x 16)."""
    from PIL import Image
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    for y in range(16):
        for x in range(16):
            cx, cy = x - 7.5, y - 7.5
            r = max(abs(cx) * 0.866 + abs(cy) * 0.5, abs(cy))
            if r > 7.2:
                continue
            if r > 6.2:
                px[x, y] = (70, 74, 86, 255)
            elif r > 4.4:
                k = 0.85 + 0.25 * (-(cx + cy) / 10.0)
                px[x, y] = tuple(max(0, min(255, int(c * k))) for c in (196, 202, 214)) + (255,)
                if (x + y) % 5 == 0:
                    px[x, y] = (120, 124, 138, 255)
            elif r > 3.4:
                px[x, y] = (40, 30, 34, 255)
            else:
                t = 1.0 - r / 3.4
                px[x, y] = (255, int(40 + 180 * t * t), int(28 + 160 * t * t), 255)
    img.save(g.R + "textures/item/ultron_nanocore.png")


if __name__ == "__main__":
    surgery()
    nanocore_icon()
    print("ok surgery")
