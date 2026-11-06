#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Gerador do modelo e das texturas da Maquina de Transformacao do Flash
(Minecraft Forge 1.20.1, mod "flash").

Gera, a partir de uma lista de caixas (cuboides) descrita em blocos reais:
  * assets/flash/models/block/transformation_chamber.json (+ _active)
  * assets/flash/models/block/potion_container.json       (+ _filled)
  * assets/flash/textures/block/machine_atlas.png          (metal, opaco)
  * assets/flash/textures/block/machine_glow.png (+ .mcmeta) (emissivo, animado)
  * assets/flash/textures/block/machine_glow_active.png (+ .mcmeta)

Uso:   python3 gen_machine.py <pasta_de_saida>

Coordenadas do desenho ("design"): X = direita de quem olha a frente,
Y = para cima, Z = profundidade a partir do plano frontal (Z=0) para dentro.
A celula do bloco controlador e X[0,1] x Z[0,1]. O modelo e escrito para
facing=south (o blockstate gira os outros) com transform raiz de escala S.
"""
import json
import math
import os
import sys

import numpy as np
from PIL import Image, ImageDraw

D = 16          # texels por bloco (densidade padrao do Minecraft)
FRAMES = 8      # quadros da animacao emissiva
OUT = sys.argv[1] if len(sys.argv) > 1 else "out"

FACES = ("front", "back", "left", "right", "top", "bottom")
CARDINAL = {"front": "south", "back": "north", "right": "east",
            "left": "west", "top": "up", "bottom": "down"}
MIRROR_FACE = {"left": "right", "right": "left"}


# ----------------------------------------------------------------------
# Geometria
# ----------------------------------------------------------------------
class Box:
    def __init__(self, name, x, y, z, mats, skip=(), dens=None, occlude=True):
        self.name = name
        self.occlude = occlude
        self.x, self.y, self.z = tuple(x), tuple(y), tuple(z)
        self.mats = mats          # face -> kind ("g:" = emissivo)
        self.skip = set(skip)
        self.dens = dens or {}
        self.visible = {}

    def lo(self):
        return (self.x[0], self.y[0], self.z[0])

    def hi(self):
        return (self.x[1], self.y[1], self.z[1])


def _mats(kw):
    base = kw.pop("all", None)
    mats = {}
    for f in FACES:
        mats[f] = kw.pop(f, base)
    return mats


class Model:
    def __init__(self, name, scale):
        self.name = name
        self.scale = scale
        self.boxes = []

    def box(self, name, x, y, z, skip=(), dens=None, occlude=True, **kw):
        mats = _mats(kw)
        b = Box(name, x, y, z, mats, skip, dens, occlude)
        self.boxes.append(b)
        return b

    def sym(self, name, x, y, z, skip=(), dens=None, **kw):
        """Caixa + espelho em torno de X=2 (corpo da maquina)."""
        self.box(name + "_L", x, y, z, skip, dens, **dict(kw))
        kw2 = {}
        for k, v in kw.items():
            kw2[MIRROR_FACE.get(k, k)] = v
        sk2 = [MIRROR_FACE.get(s, s) for s in skip]
        d2 = {MIRROR_FACE.get(k, k): v for k, v in (dens or {}).items()}
        self.box(name + "_R", (4 - x[1], 4 - x[0]), y, z, sk2, d2, **kw2)


def covered(a, face, boxes):
    """Face de 'a' totalmente encostada/enterrada em outra caixa?"""
    axis = {"front": 2, "back": 2, "left": 0, "right": 0,
            "top": 1, "bottom": 1}[face]
    pos_side = face in ("back", "right", "top")
    lo, hi = a.lo(), a.hi()
    plane = hi[axis] if pos_side else lo[axis]
    others = [i for i in range(3) if i != axis]
    eps = 1e-6
    for b in boxes:
        if b is a or not b.occlude:
            continue
        bl, bh = b.lo(), b.hi()
        if not all(bl[i] <= lo[i] + eps and bh[i] >= hi[i] - eps
                   for i in others):
            continue
        # encostada: b comeca/termina exatamente no plano, do lado de fora
        if pos_side and abs(bl[axis] - plane) < eps and bh[axis] > plane:
            return True
        if (not pos_side) and abs(bh[axis] - plane) < eps and bl[axis] < plane:
            return True
        # enterrada: o plano esta no interior de b
        if bl[axis] + eps < plane < bh[axis] - eps:
            return True
    return False


def face_dims(box, face):
    dx = box.x[1] - box.x[0]
    dy = box.y[1] - box.y[0]
    dz = box.z[1] - box.z[0]
    return {"front": (dx, dy), "back": (dx, dy), "left": (dz, dy),
            "right": (dz, dy), "top": (dx, dz), "bottom": (dx, dz)}[face]


# ----------------------------------------------------------------------
# Pintura procedural
# ----------------------------------------------------------------------
def clamp(a):
    return np.clip(a, 0, 255)


def solid(w, h, c, a=255):
    arr = np.zeros((h, w, 4), float)
    arr[..., :3] = c
    arr[..., 3] = a
    return arr


def noisy(arr, rng, amp):
    if amp:
        arr[..., :3] += rng.integers(-amp, amp + 1, (arr.shape[0], arr.shape[1], 1))
    return arr


def plate(w, h, rng, base, hi, lo, noise=3, rivets=True):
    arr = noisy(solid(w, h, base), rng, noise)
    if w >= 4 and h >= 4:
        arr[0, :, :3] = hi
        arr[:, 0, :3] = hi
        arr[-1, :, :3] = lo
        arr[:, -1, :3] = lo
    if rivets and w >= 10 and h >= 10:
        for (x, y) in ((2, 2), (w - 3, 2), (2, h - 3), (w - 3, h - 3)):
            arr[y, x, :3] = np.minimum(np.array(hi) + 25, 255)
            arr[y + 1, x + 1, :3] = lo
    return arr


def vseams(arr, step, lo, hi, off=0):
    h, w = arr.shape[:2]
    for x in range(off, w, step):
        if 0 < x < w - 1:
            arr[:, x, :3] = lo
            if x + 1 < w - 1:
                arr[:, x + 1, :3] = hi
    return arr


def hseams(arr, step, lo, hi, off=0):
    h, w = arr.shape[:2]
    for y in range(off, h, step):
        if 0 < y < h - 1:
            arr[y, :, :3] = lo
            if y + 1 < h - 1:
                arr[y + 1, :, :3] = hi
    return arr


def k_graphite(w, h, r, c):
    return plate(w, h, r, (58, 60, 69), (84, 87, 98), (32, 33, 39), 3)


def k_dark(w, h, r, c):
    return plate(w, h, r, (42, 43, 51), (66, 68, 78), (22, 23, 28), 2)


def k_dark_alt(w, h, r, c):
    a = plate(w, h, r, (50, 52, 60), (76, 79, 90), (27, 28, 33), 3)
    if w > 12:
        vseams(a, 8, (24, 25, 30), (52, 54, 62), 8)
    return a


def k_steel(w, h, r, c):
    a = plate(w, h, r, (106, 110, 121), (148, 153, 166), (66, 69, 77), 2)
    a[..., :3] += r.integers(-5, 6, (1, w, 1))
    return clamp(a)


def k_gold(w, h, r, c):
    a = plate(w, h, r, (230, 174, 22), (255, 222, 84), (152, 104, 6), 3, False)
    if w >= 8 and h >= 8:
        a[1:-1, 1:-1, :3] += r.integers(-4, 5, (h - 2, w - 2, 1))
    return clamp(a)


def k_gold_dull(w, h, r, c):
    a = plate(w, h, r, (186, 142, 38), (226, 184, 70), (118, 84, 14), 4, False)
    if w >= 10 and h >= 6:
        hseams(a, 6, (150, 110, 24), (210, 168, 56), 3)
    return clamp(a)


def k_bronze(w, h, r, c):
    a = plate(w, h, r, (122, 84, 52), (152, 110, 72), (80, 52, 31), 4, False)
    vseams(a, 6, (84, 55, 32), (146, 104, 66), 3)
    if h >= 12 and w >= 6:
        for x in range(1, w - 3, 6):
            a[2, x + 2, :3] = (170, 128, 84)
            a[h - 3, x + 2, :3] = (170, 128, 84)
    return clamp(a)


def k_bronze_hi(w, h, r, c):
    a = plate(w, h, r, (150, 106, 66), (186, 140, 92), (98, 64, 38), 3, False)
    return a


def k_wall_inner(w, h, r, c):
    a = plate(w, h, r, (38, 39, 46), (56, 58, 66), (21, 22, 26), 2, False)
    vseams(a, 8, (20, 21, 25), (56, 58, 67), 4)
    for x in range(4, w - 2, 8):
        if h > 10:
            a[2, x, :3] = (84, 87, 96)
            a[h - 3, x, :3] = (84, 87, 96)
    return clamp(a)


def k_plate_big(w, h, r, c):
    a = plate(w, h, r, (74, 77, 86), (104, 108, 119), (44, 46, 53), 3, True)
    if w > 14 and h > 14:
        # moldura interna rebaixada
        a[3, 3:w - 3, :3] = (48, 50, 57)
        a[h - 4, 3:w - 3, :3] = (48, 50, 57)
        a[3:h - 3, 3, :3] = (48, 50, 57)
        a[3:h - 3, w - 4, :3] = (48, 50, 57)
        a[4, 4:w - 4, :3] = (98, 102, 113)
        # divisoria horizontal
        yy = int(h * 0.62)
        a[yy, 4:w - 4, :3] = (46, 48, 55)
        a[yy + 1, 4:w - 4, :3] = (100, 104, 115)
        # marcas pequenas no canto inferior
        for i in range(3):
            a[h - 7, 6 + i * 3, :3] = (36, 37, 43)
            a[h - 8, 6 + i * 3, :3] = (110, 114, 125)
        # puxador
        a[int(h * 0.45):int(h * 0.45) + 4, w - 9:w - 7, :3] = (30, 31, 36)
    return clamp(a)


def k_vent(w, h, r, c):
    a = solid(w, h, (22, 23, 27))
    for y in range(h):
        m = y % 3
        if m == 0:
            a[y, :, :3] = (82, 85, 94)
        elif m == 1:
            a[y, :, :3] = (10, 10, 12)
    a[:, :2, :3] = (60, 62, 70)
    a[:, -2:, :3] = (36, 37, 43)
    a[0, :, :3] = (96, 100, 110)
    return a


def k_floor_plate(w, h, r, c):
    a = plate(w, h, r, (66, 69, 78), (96, 100, 111), (38, 40, 46), 2, False)
    for y in range(1, h - 1):
        for x in range(1, w - 1):
            if ((x // 4) + (y // 4)) % 2 == 0:
                a[y, x, :3] -= 7
    return clamp(a)


def k_header_top(w, h, r, c):
    a = plate(w, h, r, (40, 42, 49), (64, 66, 76), (22, 23, 27), 3, True)
    if w > 10:
        vseams(a, 8, (24, 25, 30), (56, 58, 66), 8)
    if h > 10:
        hseams(a, 8, (24, 25, 30), (56, 58, 66), 8)
    return a


def k_header_under(w, h, r, c):
    a = plate(w, h, r, (34, 35, 42), (52, 54, 62), (18, 19, 23), 2, False)
    hseams(a, 6, (20, 21, 25), (48, 50, 58), 3)
    return a


def k_panel_box(w, h, r, c):
    a = plate(w, h, r, (50, 52, 60), (76, 79, 90), (26, 27, 32), 3, True)
    if w >= 14 and h >= 14:
        a[3, 3:w - 3, :3] = (30, 31, 36)
        a[h - 4, 3:w - 3, :3] = (30, 31, 36)
        a[3:h - 3, 3, :3] = (30, 31, 36)
        a[3:h - 3, w - 4, :3] = (30, 31, 36)
        a[4:h - 4, 4:w - 4, :3] *= 1.12
        a[h // 2 - 2:h // 2 + 3, 6:w - 6, :3] = (36, 37, 43)
        a[h // 2 - 2, 6:w - 6, :3] = (22, 23, 27)
        for yy in range(h - 12, h - 6, 2):
            a[yy, 6:w - 6, :3] = (28, 29, 34)
    return clamp(a)


def k_screen(w, h, r, c):
    a = plate(w, h, r, (14, 15, 18), (38, 40, 46), (8, 8, 10), 1, False)
    for y in range(2, h - 2, 2):
        a[y, 1:-1, :3] = (18, 19, 23)
    return a


# ---- painel de pocoes (densidade 2) ----------------------------------
BOTTLE = [
    "...ccc...",
    "...ggg...",
    "...glg...",
    "...glg...",
    "..glllg..",
    ".glhllllg",
    "glhlllllg",
    "glhlllllg",
    "glllllllg",
    "glllllllg",
    "glllllllg",
    ".glllllg.",
    "..ggggg..",
]
POTION_COLORS = {
    "red": ((226, 38, 48), (255, 140, 140)),
    "blue": ((40, 148, 242), (150, 215, 255)),
    "purple": ((158, 66, 218), (212, 156, 255)),
}


def bottle_pixels(w, h):
    """Retorna lista (x, y, char, cor) com a posicao dos tres frascos."""
    out = []
    slot = (w - 4) / 3.0
    for i, name in enumerate(("red", "blue", "purple")):
        cx = 2 + slot * i + slot / 2.0
        x0 = int(round(cx - 4.5))
        y0 = 3
        for yy, row in enumerate(BOTTLE):
            for xx, ch in enumerate(row):
                if ch != ".":
                    out.append((x0 + xx, y0 + yy, ch, name))
    return out


def k_potion_panel(w, h, r, c):
    a = plate(w, h, r, (26, 27, 33), (46, 48, 56), (12, 12, 15), 1, False)
    for (x, y, ch, name) in bottle_pixels(w, h):
        if not (0 <= x < w and 0 <= y < h):
            continue
        base, hi = POTION_COLORS[name]
        if ch == "c":
            col = (118, 78, 40)
        elif ch == "g":
            col = (150, 156, 168)
        elif ch == "h":
            col = tuple(int(v * 0.55) for v in hi)
        else:
            col = tuple(int(v * 0.58) for v in base)
        a[y, x, :3] = col
    # divisoria e visor
    ytop = 18
    if h > ytop + 6:
        a[ytop, 1:w - 1, :3] = (10, 10, 12)
        a[ytop + 1, 1:w - 1, :3] = (54, 56, 64)
        sx0, sx1, sy0, sy1 = 3, w - 3, ytop + 4, h - 3
        a[sy0:sy1, sx0:sx1, :3] = (9, 9, 12)
        a[sy0, sx0:sx1, :3] = (4, 4, 5)
        a[sy1 - 1, sx0:sx1, :3] = (46, 48, 56)
        a[sy0:sy1, sx0, :3] = (4, 4, 5)
        a[sy0:sy1, sx1 - 1, :3] = (46, 48, 56)
        for y in range(sy0 + 2, sy1 - 1, 3):
            a[y, sx0 + 1:sx1 - 1, :3] = (14, 15, 19)
    return clamp(a)


# ---- emissivos --------------------------------------------------------
def pulse(ctx, amp_idle=0.06, amp_act=0.14):
    f = ctx["frame"] / float(FRAMES)
    amp = amp_act if ctx["active"] else amp_idle
    return 1.0 + amp * math.sin(2 * math.pi * f)


def bar_core(length, thick, ctx, vertical_rows=True):
    """Gera barra com eixo longo no eixo 0 (altura)."""
    arr = np.zeros((length, thick, 4), float)
    arr[..., 3] = 255
    p = pulse(ctx)
    edge = np.array((214, 140, 0), float)
    mid = np.array((255, 204, 28), float)
    core = np.array((255, 246, 150), float)
    if ctx["active"]:
        core = np.array((255, 252, 210), float)
    for x in range(thick):
        d = abs((x + 0.5) - thick / 2.0) / (thick / 2.0)
        if thick <= 2:
            col = mid
        elif d > 0.85:
            col = edge
        elif d > 0.45:
            col = mid
        else:
            col = core
        arr[:, x, :3] = col
    # onda que corre pela barra
    t = (ctx["frame"] / float(FRAMES)) * length
    for y in range(length):
        dist = min(abs(y - t), length - abs(y - t))
        boost = max(0.0, 1.0 - dist / 3.0) * (46 if ctx["active"] else 18)
        arr[y, :, :3] += boost
    arr[..., :3] *= p
    if thick >= 3 and length >= 3:
        arr[0, :, :3] = edge * p
        arr[-1, :, :3] = edge * p
    return clamp(arr)


def k_g_bar(w, h, r, c):
    if h >= w:
        return bar_core(h, w, c)
    return bar_core(w, h, c).transpose(1, 0, 2).copy()


def k_g_red(w, h, r, c):
    p = pulse(c, 0.05, 0.10)
    if h >= w:
        L, T = h, w
    else:
        L, T = w, h
    arr = np.zeros((L, T, 4), float)
    arr[..., 3] = 255
    for x in range(T):
        d = abs((x + 0.5) - T / 2.0) / (T / 2.0)
        if T <= 2:
            col = (190, 18, 16)
        elif d > 0.8:
            col = (104, 6, 6)
        elif d > 0.4:
            col = (178, 16, 14)
        else:
            col = (226, 40, 28)
        arr[:, x, :3] = col
    arr[..., :3] *= p
    arr = clamp(arr)
    return arr if h >= w else arr.transpose(1, 0, 2).copy()


def k_g_grid(w, h, r, c):
    p = pulse(c)
    a = solid(w, h, (255, 222, 84))
    line = np.array((206, 128, 0), float)
    for x in range(0, w, 4):
        a[:, x, :3] = line
    for y in range(0, h, 4):
        a[y, :, :3] = line
    a[0, :, :3] = (190, 118, 0)
    a[-1, :, :3] = (190, 118, 0)
    a[:, 0, :3] = (190, 118, 0)
    a[:, -1, :3] = (190, 118, 0)
    # faixa que varre a grade
    t = (c["frame"] / float(FRAMES)) * h
    for y in range(h):
        dist = min(abs(y - t), h - abs(y - t))
        a[y, :, :3] += max(0.0, 1.0 - dist / 3.0) * (44 if c["active"] else 16)
    a[..., :3] *= p
    return clamp(a)


def k_g_floor(w, h, r, c):
    p = pulse(c, 0.06, 0.16)
    a = solid(w, h, (255, 214, 56))
    line = np.array((204, 124, 0), float)
    bright = np.array((255, 246, 160), float)
    cx, cy = (w - 1) / 2.0, (h - 1) / 2.0
    for y in range(h):
        for x in range(w):
            dx = abs(x - cx) / (w / 2.0)
            dy = abs(y - cy) / (h / 2.0)
            d = max(dx, dy)
            col = None
            if d > 0.92:
                col = line
            elif 0.74 < d <= 0.82 or 0.46 < d <= 0.54:
                col = line
            elif d < 0.2:
                col = bright
            elif abs(x - cx) < 0.9 or abs(y - cy) < 0.9:
                col = (240, 180, 20)
            if col is not None:
                a[y, x, :3] = col
    # anel que expande
    ring = (c["frame"] / float(FRAMES)) * 1.0
    for y in range(h):
        for x in range(w):
            dx = abs(x - cx) / (w / 2.0)
            dy = abs(y - cy) / (h / 2.0)
            d = max(dx, dy)
            dist = min(abs(d - ring), 1.0 - abs(d - ring))
            a[y, x, :3] += max(0.0, 1.0 - dist / 0.12) * (50 if c["active"] else 16)
    a[..., :3] *= p
    return clamp(a)


BOLT = [(0.55, 0.00), (0.10, 0.56), (0.43, 0.56), (0.28, 1.00),
        (0.92, 0.38), (0.58, 0.38), (0.80, 0.00)]


def erode(mask):
    m = mask.copy()
    m[1:, :] &= mask[:-1, :]
    m[:-1, :] &= mask[1:, :]
    m[:, 1:] &= mask[:, :-1]
    m[:, :-1] &= mask[:, 1:]
    m[0, :] = False
    m[-1, :] = False
    m[:, 0] = False
    m[:, -1] = False
    return m


def k_g_bolt(w, h, r, c):
    ss = 8
    img = Image.new("L", (w * ss, h * ss), 0)
    dr = ImageDraw.Draw(img)
    pts = [((1 + x * (w - 2)) * ss, (1 + y * (h - 2)) * ss) for x, y in BOLT]
    dr.polygon(pts, fill=255)
    big = np.asarray(img, float).reshape(h, ss, w, ss).mean(axis=(1, 3))
    mask = big >= 127
    inner = erode(mask)
    core = erode(inner)
    p = pulse(c, 0.06, 0.14)
    a = np.zeros((h, w, 4), float)
    a[mask, :3] = (236, 128, 0)
    a[inner, :3] = (255, 208, 36)
    a[core, :3] = (255, 244, 150)
    a[mask, 3] = 255
    a[..., :3] *= p
    return clamp(a)


def k_g_potion(w, h, r, c):
    a = np.zeros((h, w, 4), float)
    if not c["active"]:
        return a
    p = pulse(c, 0.0, 0.12)
    for (x, y, ch, name) in bottle_pixels(w, h):
        if ch in ("l", "h") and 0 <= x < w and 0 <= y < h:
            base, hi = POTION_COLORS[name]
            a[y, x, :3] = hi if ch == "h" else base
            a[y, x, 3] = 255
    a[..., :3] *= p
    return clamp(a)


def k_glass_dark(w, h, r, c):
    a = solid(w, h, (22, 30, 40))
    a = noisy(a, r, 3)
    if w > 2:
        a[:, 0, :3] = (40, 56, 72)
    return clamp(a)


def k_g_liquid(w, h, r, c):
    """Glowing potion mix rising in the tube (only when the container is filled)."""
    a = np.zeros((h, w, 4), float)
    if not c["active"]:
        return a
    f = c["frame"] / float(FRAMES)
    cols = [np.array(v[0], float) for v in POTION_COLORS.values()]
    for y in range(h):
        t = ((h - 1 - y) / max(1, h - 1) + f) % 1.0
        k = t * len(cols)
        i0 = int(k) % len(cols)
        i1 = (i0 + 1) % len(cols)
        mix = cols[i0] * (1 - (k - int(k))) + cols[i1] * (k - int(k))
        a[y, :, :3] = mix
        a[y, :, 3] = 255
    # rising bubbles
    rng = np.random.default_rng(5)
    for b in range(max(1, w * h // 40)):
        bx = rng.integers(0, w)
        by = int((rng.integers(0, h) - f * h)) % h
        a[by, bx, :3] = (255, 240, 255)
    a[..., :3] *= pulse(c, 0.0, 0.12)
    return clamp(a)


KINDS = {
    "graphite": k_graphite, "dark": k_dark, "dark_alt": k_dark_alt,
    "steel": k_steel, "gold": k_gold, "gold_dull": k_gold_dull, "bronze": k_bronze,
    "bronze_hi": k_bronze_hi, "wall_inner": k_wall_inner,
    "plate_big": k_plate_big, "vent": k_vent, "floor_plate": k_floor_plate,
    "header_top": k_header_top, "header_under": k_header_under,
    "panel_box": k_panel_box, "screen": k_screen,
    "potion_panel": k_potion_panel,
    "g:bar": k_g_bar, "g:red": k_g_red, "g:grid": k_g_grid,
    "g:floor": k_g_floor, "g:bolt": k_g_bolt, "g:potion": k_g_potion,
    "glass_dark": k_glass_dark, "g:liquid": k_g_liquid,
}
KIND_DENS = {"g:bolt": 2, "potion_panel": 2, "g:potion": 2}


def paint(kind, w, h, seed, frame=0, active=False):
    rng = np.random.default_rng(seed)
    ctx = {"frame": frame, "active": active}
    arr = KINDS[kind](w, h, rng, ctx)
    return clamp(arr).astype(np.uint8)


# ----------------------------------------------------------------------
# Atlas
# ----------------------------------------------------------------------
class Packer:
    def __init__(self, size):
        self.size = size
        self.x = self.y = self.row_h = 0
        self.items = []

    def try_pack(self, rects):
        self.x = self.y = self.row_h = 0
        placed = {}
        for key, (w, h) in sorted(rects.items(), key=lambda kv: (-kv[1][1], -kv[1][0])):
            pw, ph = w + 2, h + 2
            if self.x + pw > self.size:
                self.x = 0
                self.y += self.row_h
                self.row_h = 0
            if self.y + ph > self.size or pw > self.size:
                return None
            placed[key] = (self.x + 1, self.y + 1)
            self.x += pw
            self.row_h = max(self.row_h, ph)
        return placed


def pack(rects):
    for size in (64, 128, 256, 512, 1024):
        placed = Packer(size).try_pack(rects)
        if placed is not None:
            return size, placed
    raise RuntimeError("atlas grande demais")


def blit(img, arr, x, y):
    h, w = arr.shape[:2]
    img[y:y + h, x:x + w] = arr
    # extrude 1px (evita sangramento de textura nas bordas)
    img[y - 1, x:x + w] = arr[0]
    img[y + h, x:x + w] = arr[-1]
    img[y - 1:y + h + 1, x - 1] = img[y - 1:y + h + 1, x]
    img[y - 1:y + h + 1, x + w] = img[y - 1:y + h + 1, x + w - 1]


# ----------------------------------------------------------------------
# Descricao da maquina
# ----------------------------------------------------------------------
def disc(m, name, cx, cz, r_in, r_out, y, step=0.1, gap=None, skip=(), occlude=True, **mats):
    """Annulus (or disc when r_in = 0) rasterised into boxes on a grid of `step`
    blocks; `gap` = (from, to) degrees left out (0 = front, positive to the right)."""
    n = int(math.ceil(r_out / step))
    k = 0
    for row in range(-n, n):
        z0 = cz + row * step
        z1 = z0 + step
        zc = (z0 + z1) / 2.0
        run = None
        for col in range(-n, n + 1):
            x0 = cx + col * step
            xc = x0 + step / 2.0
            d = math.hypot(xc - cx, zc - cz)
            ok = r_in <= d <= r_out
            if ok and gap is not None:
                ang = math.degrees(math.atan2(xc - cx, -(zc - cz)))
                ok = not (gap[0] <= ang <= gap[1])
            if ok and col < n:
                run = (run[0], x0 + step) if run else (x0, x0 + step)
                continue
            if run:
                m.box("%s_%d" % (name, k), (round(run[0], 4), round(run[1], 4)), y,
                      (round(z0, 4), round(z1, 4)), skip=skip, occlude=occlude, **mats)
                k += 1
                run = None
    return k


def polar(cx, cz, r, deg):
    """Point at `deg` degrees from the front (positive = right) on a circle."""
    a = math.radians(deg)
    return cx + r * math.sin(a), cz - r * math.cos(a)


def build_machine():
    """Transformation chamber v8: a round S.T.A.R. Labs style pod.

    Cabin centre (2.0, 0.95), floor at 0.5 (the Java side relies on both)."""
    m = Model("transformation_chamber", 8.0)
    B = m.box
    CX, CZ = 2.0, 0.95
    FL = 0.5          # cabin floor
    TOP = 4.2         # bottom of the crown

    # ----- stepped round dais -----
    disc(m, "dais1", CX, CZ, 0.0, 1.95, (0.0, 0.18), all="dark", top="graphite")
    disc(m, "dais_glow", CX, CZ, 1.78, 1.88, (0.18, 0.2), top="g:bar",
         skip=("front", "back", "left", "right", "bottom"), occlude=False)
    disc(m, "dais2", CX, CZ, 0.0, 1.72, (0.18, 0.34), all="graphite", top="dark_alt")
    disc(m, "dais_gold", CX, CZ, 1.5, 1.6, (0.34, 0.37), all="gold",
         skip=("bottom",), occlude=False)
    disc(m, "dais3", CX, CZ, 0.0, 1.45, (0.34, FL), all="steel", top="floor_plate")
    disc(m, "floor_glow", CX, CZ, 0.0, 0.82, (FL, FL + 0.02), top="g:floor",
         skip=("front", "back", "left", "right", "bottom"), occlude=False)
    # entrance step in front
    B("step", (1.35, 2.65), (0.0, 0.25), (-1.0, -0.75), all="graphite", top="floor_plate",
      front="steel")
    B("step_led", (1.45, 2.55), (0.06, 0.14), (-1.03, -1.0), all="g:bar")

    # ----- curved back wall (open front) -----
    disc(m, "wall", CX, CZ, 1.3, 1.62, (FL, TOP), step=0.12, gap=(-70, 70),
         all="dark", top="header_top")
    # vertical energy slits on the inside of the wall
    for i, deg in enumerate((-125, -100, 100, 125, 155, -155)):
        x, z = polar(CX, CZ, 1.27, deg)
        B("slit_%d" % i, (round(x - 0.05, 4), round(x + 0.05, 4)), (1.0, 3.6),
          (round(z - 0.05, 4), round(z + 0.05, 4)), all="g:bar")
    # emblem on the back wall, facing the cabin
    zb = CZ + 1.26
    B("plaque", (1.45, 2.55), (1.6, 3.3), (zb - 0.05, zb), all="dark", front="wall_inner")
    B("plaque_rim_t", (1.4, 2.6), (3.28, 3.36), (zb - 0.09, zb), all="gold")
    B("plaque_rim_b", (1.4, 2.6), (1.54, 1.62), (zb - 0.09, zb), all="gold")
    B("plaque_rim_l", (1.4, 1.48), (1.62, 3.28), (zb - 0.09, zb), all="gold")
    B("plaque_rim_r", (2.52, 2.6), (1.62, 3.28), (zb - 0.09, zb), all="gold")
    B("bolt", (1.58, 2.42), (1.78, 3.12), (zb - 0.12, zb - 0.09), front="g:bolt",
      skip=("back", "left", "right", "top", "bottom"), occlude=False)

    # ----- two front pillars framing the entrance -----
    for side, deg in (("L", -72), ("R", 72)):
        x, z = polar(CX, CZ, 1.46, deg)
        xa, xb = round(x - 0.2, 4), round(x + 0.2, 4)
        za, zb2 = round(z - 0.2, 4), round(z + 0.2, 4)
        B("pil_%s" % side, (xa, xb), (FL, TOP), (za, zb2), all="graphite", front="steel")
        B("pil_base_%s" % side, (xa - 0.05, xb + 0.05), (FL, FL + 0.3), (za - 0.05, zb2 + 0.05),
          all="steel", top="graphite")
        B("pil_cap_%s" % side, (xa - 0.05, xb + 0.05), (TOP - 0.3, TOP), (za - 0.05, zb2 + 0.05),
          all="steel")
        for j, yy in enumerate((1.4, 2.8)):
            B("pil_ring_%s%d" % (side, j), (xa - 0.03, xb + 0.03), (yy, yy + 0.08),
              (za - 0.03, zb2 + 0.03), all="gold")
        B("pil_bar_%s" % side, (round(x - 0.06, 4), round(x + 0.06, 4)), (FL + 0.45, TOP - 0.45),
          (za - 0.03, za), all="g:bar")

    # ----- floating energy rings around the cabin -----
    for j, yy in enumerate((1.35, 2.75)):
        disc(m, "halo%d" % j, CX, CZ, 1.08, 1.18, (yy, yy + 0.08), step=0.08, gap=(-38, 38),
             all="g:bar", occlude=False)

    # ----- crown -----
    disc(m, "crown1", CX, CZ, 0.0, 1.7, (TOP, TOP + 0.25), step=0.12, all="graphite",
         top="header_top", bottom="header_under")
    disc(m, "crown_gold", CX, CZ, 1.6, 1.74, (TOP + 0.08, TOP + 0.17), step=0.12, all="gold",
         occlude=False)
    disc(m, "crown2", CX, CZ, 0.0, 1.25, (TOP + 0.25, TOP + 0.45), step=0.12, all="dark_alt",
         top="header_top")
    disc(m, "emitter", CX, CZ, 0.0, 0.9, (TOP - 0.03, TOP), bottom="g:grid",
         skip=("front", "back", "left", "right", "top"), occlude=False)
    # tesla coil on top
    disc(m, "coil", CX, CZ, 0.0, 0.3, (TOP + 0.45, TOP + 1.05), step=0.06, all="steel")
    for j, yy in enumerate((TOP + 0.55, TOP + 0.72, TOP + 0.89)):
        disc(m, "coil_ring%d" % j, CX, CZ, 0.3, 0.42, (yy, yy + 0.07), step=0.06, all="gold",
             occlude=False)
    disc(m, "coil_top", CX, CZ, 0.0, 0.22, (TOP + 1.05, TOP + 1.2), step=0.06, all="gold")
    # lightning plate on the front of the crown
    zf = CZ - 1.7
    B("crown_plate", (1.55, 2.45), (TOP + 0.02, TOP + 0.43), (zf - 0.02, zf + 0.25), all="dark",
      front="dark_alt")
    B("crown_bar", (1.62, 2.38), (TOP + 0.16, TOP + 0.28), (zf - 0.05, zf - 0.02), all="g:bar")

    # ----- capacitors at the back corners -----
    for side, x in (("L", 0.38), ("R", 3.62)):
        zc = 2.62
        disc(m, "cap_%s" % side, x, zc, 0.0, 0.26, (0.0, 3.3), step=0.06, all="graphite",
             top="steel")
        for j in range(5):
            yy = 0.6 + j * 0.5
            disc(m, "cap_ring_%s%d" % (side, j), x, zc, 0.26, 0.34, (yy, yy + 0.12), step=0.06,
                 all="g:red", occlude=False)
        disc(m, "cap_top_%s" % side, x, zc, 0.0, 0.32, (3.3, 3.42), step=0.06, all="gold")
    # cables from the capacitors into the back wall
    for side, x0, x1 in (("L", 0.55, 0.95), ("R", 3.05, 3.45)):
        B("cable_%s" % side, (x0, x1), (2.9, 3.05), (2.4, 2.55), all="dark")

    # ----- side connector for the potion container (left, by the controller) -----
    B("mount", (0.0, 0.28), (0.0, 1.9), (0.35, 1.2), all="graphite", front="dark_alt",
      left="panel_box", top="steel")
    B("mount_gold", (0.0, 0.3), (1.9, 1.96), (0.33, 1.22), all="gold")
    B("mount_led", (-0.02, 0.0), (0.4, 1.6), (0.7, 0.85), all="g:bar")
    return m


def build_container():
    """Potion container v8: a glass tube of glowing liquid between four posts."""
    m = Model("potion_container", 2.0)
    B = m.box
    C = 0.5
    # base
    B("base1", (0.02, 0.98), (0.0, 0.16), (0.02, 0.98), all="dark", top="graphite")
    B("base2", (0.08, 0.92), (0.16, 0.3), (0.08, 0.92), all="graphite", top="dark_alt",
      front="steel")
    B("base_led", (0.25, 0.75), (0.19, 0.26), (0.05, 0.08), all="g:bar")
    # four corner posts with gold rings
    for i, (x, z) in enumerate(((0.1, 0.1), (0.76, 0.1), (0.1, 0.76), (0.76, 0.76))):
        B("post%d" % i, (x, x + 0.14), (0.3, 2.9), (z, z + 0.14), all="steel", top="graphite")
        for j, yy in enumerate((0.9, 1.9)):
            B("post_ring%d_%d" % (i, j), (x - 0.02, x + 0.16), (yy, yy + 0.06), (z - 0.02, z + 0.16),
              all="gold")
    # tube: dark glass core and the glowing liquid around it (lit when filled)
    disc(m, "tube_core", C, C, 0.0, 0.26, (0.42, 2.78), step=0.04, all="glass_dark")
    disc(m, "liquid", C, C, 0.0, 0.28, (0.44, 2.76), step=0.04, all="g:liquid", occlude=False,
         skip=("top", "bottom"))
    disc(m, "collar_lo", C, C, 0.0, 0.36, (0.3, 0.44), step=0.04, all="gold")
    disc(m, "collar_hi", C, C, 0.0, 0.36, (2.76, 2.9), step=0.04, all="gold")
    # cap with a small coil
    B("cap", (0.06, 0.94), (2.9, 3.04), (0.06, 0.94), all="graphite", top="header_top",
      bottom="header_under")
    disc(m, "cap_coil", C, C, 0.0, 0.14, (3.04, 3.2), step=0.04, all="steel")
    disc(m, "cap_coil_ring", C, C, 0.14, 0.19, (3.08, 3.12), step=0.04, all="gold", occlude=False)
    return m


# ----------------------------------------------------------------------
# Emissao
# ----------------------------------------------------------------------
def collect(models):
    """Calcula faces visiveis e seus tamanhos em texels."""
    jobs = {}
    for m in models:
        for b in m.boxes:
            for f in FACES:
                kind = b.mats.get(f)
                if kind is None or f in b.skip:
                    continue
                if f == "bottom" and b.y[0] <= 0.0 + 1e-9:
                    continue
                if covered(b, f, m.boxes):
                    continue
                dens = b.dens.get(f, KIND_DENS.get(kind, 1))
                du, dv = face_dims(b, f)
                tw = max(1, int(round(du * D * dens)))
                th = max(1, int(round(dv * D * dens)))
                jobs[(m.name, b.name, f)] = (kind, tw, th)
    return jobs


def px(v, scale):
    return round(v * 16.0 / scale, 4)


def element(b, scale, uvs, atlas_main, atlas_glow, jobs, model_name):
    x0, x1 = b.x
    y0, y1 = b.y
    z0, z1 = b.z
    frm = [px(x0, scale), px(y0, scale), px(1 - z1, scale)]
    to = [px(x1, scale), px(y1, scale), px(1 - z0, scale)]
    faces = {}
    allglow = True
    for f in FACES:
        key = (model_name, b.name, f)
        if key not in jobs:
            continue
        kind, tw, th = jobs[key]
        glow = kind.startswith("g:")
        allglow = allglow and glow
        u0, v0, u1, v1 = uvs[key]
        face = {"uv": [u0, v0, u1, v1], "texture": "#g" if glow else "#m"}
        if glow:
            face["forge_data"] = {"block_light": 15, "sky_light": 15,
                                  "ambient_occlusion": False}
        faces[CARDINAL[f]] = face
    el = {"name": b.name, "from": frm, "to": to, "faces": faces}
    if allglow:
        el["shade"] = False
    return el if faces else None


def main():
    machine = build_machine()
    container = build_container()
    models = [machine, container]
    jobs = collect(models)

    main_rects = {k: (v[1], v[2]) for k, v in jobs.items() if not v[0].startswith("g:")}
    glow_rects = {k: (v[1], v[2]) for k, v in jobs.items() if v[0].startswith("g:")}
    msize, mpos = pack(main_rects)
    gsize, gpos = pack(glow_rects)

    # ---- atlas principal ----
    main_img = np.zeros((msize, msize, 4), np.uint8)
    for key, (x, y) in mpos.items():
        kind, tw, th = jobs[key]
        seed = sum(ord(c) * (i + 3) for i, c in enumerate("".join(key)))
        blit(main_img, paint(kind, tw, th, seed), x, y)
    # ---- atlas emissivo (animado) ----
    def glow_atlas(active):
        frames = []
        for fr in range(FRAMES):
            img = np.zeros((gsize, gsize, 4), np.uint8)
            for key, (x, y) in gpos.items():
                kind, tw, th = jobs[key]
                blit(img, paint(kind, tw, th, 7, fr, active), x, y)
            frames.append(img)
        return np.concatenate(frames, axis=0)

    os.makedirs(os.path.join(OUT, "textures", "block"), exist_ok=True)
    os.makedirs(os.path.join(OUT, "models", "block"), exist_ok=True)
    tdir = os.path.join(OUT, "textures", "block")
    Image.fromarray(main_img, "RGBA").save(os.path.join(tdir, "machine_atlas.png"), optimize=True)
    for name, active, ft in (("machine_glow", False, 8), ("machine_glow_active", True, 2)):
        Image.fromarray(glow_atlas(active), "RGBA").save(os.path.join(tdir, name + ".png"), optimize=True)
        with open(os.path.join(tdir, name + ".png.mcmeta"), "w") as fh:
            json.dump({"animation": {"frametime": ft, "interpolate": True}}, fh, indent=2)

    # ---- UVs ----
    uvs = {}
    for key, (x, y) in mpos.items():
        _, tw, th = jobs[key]
        uvs[key] = [round(x / msize * 16, 5), round(y / msize * 16, 5),
                    round((x + tw) / msize * 16, 5), round((y + th) / msize * 16, 5)]
    for key, (x, y) in gpos.items():
        _, tw, th = jobs[key]
        uvs[key] = [round(x / gsize * 16, 5), round(y / gsize * 16, 5),
                    round((x + tw) / gsize * 16, 5), round((y + th) / gsize * 16, 5)]

    mdir = os.path.join(OUT, "models", "block")

    def write(name, data):
        with open(os.path.join(mdir, name + ".json"), "w") as fh:
            json.dump(data, fh, indent=1)

    for m, credit in ((machine, "Flash mod - transformation chamber v8"),
                      (container, "Flash mod - potion container v8")):
        els = [e for e in (element(b, m.scale, uvs, None, None, jobs, m.name)
                           for b in m.boxes) if e]
        data = {
            "credit": credit,
            "render_type": "minecraft:cutout",
            "transform": {"origin": [0, 0, 0], "translation": [0, 0, 0],
                          "scale": [m.scale] * 3},
            "textures": {"m": "flash:block/machine_atlas",
                         "g": "flash:block/machine_glow",
                         "particle": "flash:block/machine_atlas"},
            "elements": els,
        }
        write(m.name, data)
        alt = {"parent": "flash:block/" + m.name,
               "textures": {"g": "flash:block/machine_glow_active"}}
        write(m.name + ("_active" if m is machine else "_filled"), alt)

    info = {"main_atlas": msize, "glow_atlas": gsize,
            "faces": len(jobs), "machine_elements": len(machine.boxes),
            "container_elements": len(container.boxes)}
    print(json.dumps(info))


if __name__ == "__main__":
    main()
