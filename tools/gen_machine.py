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
def build_machine():
    m = Model("transformation_chamber", 8.0)
    B, S = m.box, m.sym
    ZB = 3.4          # plano traseiro do corpo
    ZW = 1.9          # parede do fundo da cabine
    YO = 3.8          # topo da abertura
    # ----- Base escalonada -----
    B("b1_c", (0.3, 3.7), (0, 0.2), (-0.55, 3.75), all="dark", top="graphite")
    S("b1_s", (0, 0.3), (0, 0.2), (-0.25, 3.45), all="dark", top="graphite")
    B("b2_c", (0.25, 3.75), (0.2, 0.4), (-0.35, 3.55), all="graphite", top="dark_alt")
    S("b2_s", (0.05, 0.25), (0.2, 0.4), (-0.15, 3.35), all="graphite", top="dark_alt")
    B("tread", (0.9, 3.1), (0.2, 0.3), (-0.5, -0.35), all="steel", top="floor_plate")
    S("b_gold_f", (0.05, 0.5), (0.4, 0.47), (-0.15, 0.3), all="gold")
    S("b_gold_r", (0.05, 0.5), (0.4, 0.47), (3.05, 3.35), all="gold")
    S("b_led_f", (0.35, 1.0), (0.05, 0.16), (-0.58, -0.55), all="g:bar")
    S("b_led_r", (0.35, 1.0), (0.05, 0.16), (3.75, 3.78), all="g:bar")
    B("b_led_rs1", (4.0, 4.03), (0.05, 0.16), (0.05, 1.05), all="g:bar")
    B("b_led_rs2", (4.0, 4.03), (0.05, 0.16), (2.2, 3.2), all="g:bar")
    B("b_led_ls2", (-0.03, 0.0), (0.05, 0.16), (2.2, 3.2), all="g:bar")
    B("b_led_ls1", (-0.03, 0.0), (0.05, 0.16), (1.2, 1.95), all="g:bar")

    # ----- Plataforma de entrada -----
    B("floor_front", (1.1, 2.9), (0.4, 0.5), (-0.3, 0.2),
      all="graphite", top="floor_plate", front="steel")
    B("floor_back", (1.1, 2.9), (0.4, 0.5), (1.7, ZW), all="graphite", top="floor_plate")
    B("floor_l", (1.1, 1.3), (0.4, 0.5), (0.2, 1.7), all="graphite", top="floor_plate")
    B("floor_r", (2.7, 2.9), (0.4, 0.5), (0.2, 1.7), all="graphite", top="floor_plate")
    B("floor_glow", (1.3, 2.7), (0.4, 0.47), (0.2, 1.7), top="g:floor",
      skip=("front", "back", "left", "right", "bottom"))

    # ----- Carcacas e corpo traseiro -----
    S("casing", (0.15, 0.5), (0.4, 4.2), (0.3, ZB), all="graphite",
      front="dark", top="header_top")
    B("rear_body", (0.5, 3.5), (0.4, YO, ), (ZW, ZB),
      all="dark", front="wall_inner", back="dark")

    # ----- Colunas frontais (projetam-se alem da carcaca) -----
    S("col", (0.5, 1.1), (0.4, YO), (-0.3, ZW), all="graphite",
      front="steel", right="bronze", left="steel")
    S("mod_f", (0.3, 1.1), (0.4, 0.85), (-0.35, 0.3), all="steel", top="graphite")
    S("mod_f2", (0.4, 1.05), (0.85, 1.0), (-0.27, 0.3), all="graphite", front="dark_alt")
    S("mod_f_gold", (0.3, 1.1), (0.85, 0.9), (-0.38, -0.35), all="gold")
    S("col_ring_lo", (0.45, 1.15), (1.0, 1.06), (-0.34, 0.3), all="gold")
    S("col_cap", (0.45, 1.15), (3.45, YO), (-0.35, 0.3), all="steel", front="graphite")
    S("col_ring_hi", (0.45, 1.15), (3.39, 3.45), (-0.34, 0.3), all="gold")
    # barras amarelas e faixas vermelhas (frente das colunas)
    B("bar_front_L", (0.74, 0.92), (1.2, 3.25), (-0.35, -0.3), all="g:bar")
    B("bar_front_R", (3.08, 3.26), (1.2, 3.25), (-0.35, -0.3), all="g:bar")
    B("red_front_L", (0.56, 0.66), (1.15, 3.3), (-0.33, -0.3), all="g:red")
    B("red_front_R", (3.34, 3.44), (1.15, 3.3), (-0.33, -0.3), all="g:red")
    # barras e faixas nas laterais das colunas (visiveis nas vistas laterais)
    B("bar_side_L", (0.46, 0.5), (1.2, 3.25), (-0.12, 0.08), all="g:bar")
    B("bar_side_R", (3.5, 3.54), (1.2, 3.25), (-0.12, 0.08), all="g:bar")
    B("red_side_L", (0.47, 0.5), (1.15, 3.3), (-0.26, -0.18), all="g:red")
    B("red_side_R", (3.5, 3.53), (1.15, 3.3), (-0.26, -0.18), all="g:red")
    # barras na parede interna
    B("bar_in_L", (1.1, 1.14), (1.1, 3.3), (0.05, 0.22), all="g:bar")
    B("bar_in_R", (2.86, 2.9), (1.1, 3.3), (0.05, 0.22), all="g:bar")
    for i, z0 in enumerate((0.5, 0.98, 1.46)):
        B("slat_L%d" % i, (1.1, 1.13), (1.05, 3.3), (z0, z0 + 0.16), all="bronze_hi",
          skip=("back", "front", "top", "bottom", "left"))
        B("slat_R%d" % i, (2.87, 2.9), (1.05, 3.3), (z0, z0 + 0.16), all="bronze_hi",
          skip=("back", "front", "top", "bottom", "right"))

    # ----- Travessa superior -----
    B("lintel", (0.45, 3.55), (YO, 4.2), (-0.35, ZB), all="gold_dull",
      bottom="header_under", top="header_top", back="graphite")
    B("lintel_under_L", (0.45, 1.1), (YO - 0.06, YO), (-0.3, ZW), all="steel",
      skip=("front",))
    B("lintel_under_R", (2.9, 3.55), (YO - 0.06, YO), (-0.3, ZW), all="steel",
      skip=("front",))
    B("hdr", (0.15, 3.85), (4.2, 4.55), (-0.2, ZB + 0.1), all="graphite",
      top="header_top", front="dark_alt")
    S("hdr_gold", (0.2, 1.35), (4.26, 4.5), (-0.3, -0.2), all="gold")
    S("hdr_gold_edge", (0.15, 0.3), (4.2, 4.7), (-0.3, 0.2), all="gold")
    S("hdr_side_gold1", (0.1, 0.15), (4.28, 4.46), (0.3, 1.1), all="gold")
    S("hdr_side_gold2", (0.1, 0.15), (4.28, 4.46), (1.5, 2.3), all="gold")
    B("frame_c", (1.15, 2.85), (4.3, 4.78), (-0.5, -0.2), all="dark", front="dark_alt")
    B("bar_c", (1.3, 2.7), (4.44, 4.62), (-0.54, -0.5), all="g:bar")
    B("frame_c_top", (1.05, 2.95), (4.78, 4.86), (-0.46, 0.4), all="graphite",
      top="header_top")
    B("tier2", (0.5, 3.5), (4.55, 4.78), (0.0, 3.0), all="dark_alt",
      front="graphite", top="header_top")
    B("top_mod", (1.45, 2.55), (4.86, 5.14), (0.2, 1.6), all="steel",
      front="graphite", top="gold")
    B("top_rim", (1.39, 2.61), (5.14, 5.2), (0.14, 1.66), all="gold")
    B("top_frame", (1.5, 2.5), (4.94, 5.1), (0.16, 0.2), all="dark")
    B("top_bar", (1.57, 2.43), (4.98, 5.06), (0.12, 0.16), all="g:bar")
    # orelhas escuras nos cantos superiores
    S("ear_f", (0.15, 0.85), (4.55, 4.95), (0.1, 1.2), all="dark",
      front="graphite", top="header_top")
    S("ear_f_cap", (0.25, 0.75), (4.95, 5.03), (0.2, 1.05), all="steel")
    S("ear_r", (0.15, 0.85), (4.55, 4.95), (2.2, ZB), all="dark", top="header_top")
    S("ear_r_cap", (0.25, 0.75), (4.95, 5.03), (2.3, 3.2), all="steel")
    S("ear_gold", (0.1, 0.2), (4.6, 4.9), (0.1, 0.7), all="gold")
    # luz do teto
    B("ceil_frame", (1.15, 2.85), (YO - 0.04, YO), (0.1, 1.8), all="dark",
      skip=("top",))
    B("ceil_grid", (1.25, 2.75), (YO - 0.08, YO - 0.04), (0.2, 1.7), bottom="g:grid",
      skip=("front", "back", "left", "right", "top"))

    # ----- Fundo da cabine -----
    B("plaque", (1.45, 2.55), (1.25, 3.15), (ZW - 0.06, ZW), all="dark",
      front="wall_inner")
    B("plaque_top", (1.4, 2.6), (3.13, 3.21), (ZW - 0.1, ZW), all="gold")
    B("plaque_bot", (1.4, 2.6), (1.19, 1.27), (ZW - 0.1, ZW), all="gold")
    B("plaque_l", (1.4, 1.48), (1.27, 3.13), (ZW - 0.1, ZW), all="gold")
    B("plaque_r", (2.52, 2.6), (1.27, 3.13), (ZW - 0.1, ZW), all="gold")
    B("bolt", (1.58, 2.42), (1.45, 2.95), (ZW - 0.14, ZW - 0.1), front="g:bolt",
      skip=("back", "left", "right", "top", "bottom"), occlude=False)
    B("wall_bar_frame", (1.2, 2.8), (0.55, 0.78), (ZW - 0.04, ZW), all="dark")
    B("wall_bar", (1.3, 2.7), (0.6, 0.72), (ZW - 0.08, ZW - 0.04), all="g:bar")

    # ----- Traseira -----
    B("back_frame", (1.0, 3.0), (0.75, 3.7), (ZB, ZB + 0.04), all="dark")
    B("back_panel", (1.12, 2.88), (0.87, 3.58), (ZB + 0.04, ZB + 0.1), all="plate_big")
    B("back_vent_frame", (1.44, 2.56), (2.92, 3.5), (ZB + 0.1, ZB + 0.12), all="dark")
    B("back_vent", (1.5, 2.5), (2.98, 3.44), (ZB + 0.12, ZB + 0.15), all="dark",
      back="vent")
    S("bcol", (0.35, 0.9), (0.7, 3.7), (ZB, ZB + 0.14), all="steel", back="steel")
    S("bcol_base", (0.3, 0.95), (0.4, 0.7), (ZB, ZB + 0.22), all="steel",
      top="graphite", back="graphite")
    S("bcol_cap", (0.3, 0.95), (3.6, 3.8), (ZB, ZB + 0.2), all="graphite")
    B("bar_back_L", (0.56, 0.7), (1.0, 3.45), (ZB + 0.14, ZB + 0.18), all="g:bar")
    B("bar_back_R", (3.3, 3.44), (1.0, 3.45), (ZB + 0.14, ZB + 0.18), all="g:bar")
    B("red_back_L", (0.4, 0.48), (0.95, 3.5), (ZB + 0.14, ZB + 0.17), all="g:red")
    B("red_back_R", (3.52, 3.6), (0.95, 3.5), (ZB + 0.14, ZB + 0.17), all="g:red")

    # ----- Laterais -----
    # direita: nervuras, painel e modulo tecnico traseiro
    B("rib_R0", (3.85, 3.93), (0.5, 4.1), (0.3, 0.42), all="dark")
    B("side_panel_R1", (3.85, 3.89), (0.6, 3.95), (0.55, 1.95), all="steel",
      right="graphite")
    B("rib_R1", (3.85, 3.93), (0.5, 4.1), (1.95, 2.07), all="dark")
    B("rib_R2", (3.85, 3.93), (0.5, 4.1), (3.2, ZB), all="dark")
    B("mod_R", (3.85, 4.3), (0.9, 2.75), (2.1, 3.25), all="dark", right="panel_box",
      front="graphite", back="graphite", top="steel")
    B("mod_R_gold", (3.85, 4.32), (0.9, 1.0), (2.1, 3.25), all="gold")
    B("mod_R_lip", (3.85, 4.34), (2.75, 2.83), (2.06, 3.29), all="steel")
    B("mod_R_leg", (3.85, 4.1), (0.4, 0.9), (2.1, 3.25), all="graphite")
    B("side_gold_R", (3.85, 3.91), (0.4, 0.5), (0.3, 2.1), all="gold")
    B("side_gold_R2", (3.85, 3.91), (0.4, 0.5), (3.25, ZB), all="gold")
    # esquerda: conector do painel de pocoes + nervuras
    B("mount", (0.0, 0.15), (0.4, 3.0), (-0.1, 0.95), all="graphite",
      front="dark_alt", skip=("left",))
    B("mount_gold", (0.0, 0.15), (0.4, 0.46), (-0.1, 0.95), all="gold", skip=("left",))
    B("rib_L0", (0.07, 0.15), (0.5, 4.1), (0.3, 0.42), all="dark")
    B("side_panel_L1", (0.11, 0.15), (0.6, 3.95), (0.95, 1.95), all="steel",
      left="graphite")
    B("rib_L1", (0.07, 0.15), (0.5, 4.1), (1.95, 2.07), all="dark")
    B("side_panel_L2", (0.11, 0.15), (0.6, 3.95), (2.07, 3.2), all="graphite",
      left="steel")
    B("rib_L2", (0.07, 0.15), (0.5, 4.1), (3.2, ZB), all="dark")
    B("side_gold_L", (0.09, 0.15), (0.4, 0.5), (0.95, ZB), all="gold")
    return m


def build_container():
    m = Model("potion_container", 2.0)
    B = m.box
    B("p1", (-0.35, 1), (0, 0.2), (-0.3, 1.0), all="dark", top="graphite")
    B("p1_led", (-0.2, 0.8), (0.05, 0.16), (-0.33, -0.3), all="g:bar")
    B("p2", (-0.3, 1), (0.2, 0.4), (-0.1, 1.0), all="graphite", top="dark_alt")
    B("p2_gold", (-0.3, 0.2), (0.4, 0.46), (-0.05, 0.3), all="gold")
    B("pedestal", (0.0, 1.0), (0.4, 1.5), (0.1, 1.0), all="graphite",
      front="dark_alt", right="dark")
    B("ped_gold", (0.0, 1.0), (0.4, 0.46), (0.06, 0.1), all="gold")
    B("ped_step", (0.1, 0.9), (0.9, 1.5), (-0.1, 0.1), all="graphite", front="steel",
      top="graphite")
    B("ped_step_gold", (0.1, 0.9), (0.9, 0.96), (-0.14, -0.1), all="gold")
    B("side_box", (-0.35, 0.0), (0.4, 3.0), (0.2, 1.0), all="dark_alt",
      front="graphite", left="graphite")
    B("side_box_gold", (-0.35, 0.0), (0.9, 0.96), (0.16, 0.2), all="gold")
    B("housing", (-0.3, 1.0), (1.5, 3.2), (0.2, 1.0), all="dark", front="graphite",
      top="header_top")
    B("housing_top", (-0.3, 1.0), (3.0, 3.2), (-0.5, 1.0), all="graphite",
      top="header_top", front="dark_alt")
    B("housing_gold", (-0.3, 1.0), (3.0, 3.06), (-0.56, -0.5), all="gold")
    B("pf_top", (-0.3, 1.0), (2.88, 3.0), (-0.45, 0.0), all="gold")
    B("pf_bot", (-0.3, 1.0), (1.5, 1.62), (-0.45, 0.0), all="gold")
    B("pf_l", (-0.3, -0.22), (1.62, 2.88), (-0.45, 0.0), all="gold")
    B("pf_r", (0.92, 1.0), (1.62, 2.88), (-0.45, 0.0), all="gold")
    B("pf_back", (-0.22, 0.92), (1.62, 2.88), (0.0, 0.1), all="dark",
      front="potion_panel", dens={"front": 2})
    B("pf_liquid", (-0.22, 0.92), (1.62, 2.88), (-0.02, 0.0), front="g:potion",
      dens={"front": 2}, skip=("back", "left", "right", "top", "bottom"), occlude=False)
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

    for m, credit in ((machine, "Flash mod - transformation chamber v7"),
                      (container, "Flash mod - potion panel v7")):
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
