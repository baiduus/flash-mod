#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Maquina de Transformacao do Flash v9 - remodelada do zero.

Um acelerador da Forca de Aceleracao: plataforma octogonal em degraus,
gaiola de nervuras com filetes de luz em volta da cabine, um anel acelerador
horizontal com tres ims (capacitores vermelhos), torres de bobina atras,
reator com o emblema do raio virado para dentro, coroa em cone com para-raios.

Mantem o que o Java usa: centro da cabine (2.0, 0.95) no desenho, piso a 0.5,
frente aberta (Z negativo), altura total 5.4, pegada X 0..4 / Z -1.04..2.96 e o
encaixe do conteiner de pocoes do lado esquerdo.

Reaproveita a infraestrutura de gen_machine.py (caixas, pintura, atlas, UVs) e
gera de novo o conteiner e a camara gama como antes.

Uso: python3 gen_machine_v2.py <pasta_de_saida>
"""
import math
import sys
import os

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen_machine as gm  # noqa: E402
import numpy as np  # noqa: E402

plate = gm.plate
vseams = gm.vseams
hseams = gm.hseams
clamp = gm.clamp


# ----------------------------------------------------------------------
# Pinturas novas
# ----------------------------------------------------------------------
def k_crimson(w, h, r, c):
    a = plate(w, h, r, (142, 20, 26), (190, 46, 48), (84, 9, 13), 3)
    if w > 14:
        vseams(a, 10, (72, 7, 10), (168, 34, 38), 10)
    return clamp(a)


def k_crimson_dark(w, h, r, c):
    a = plate(w, h, r, (96, 12, 18), (134, 28, 32), (52, 5, 9), 2)
    if h > 14:
        hseams(a, 9, (46, 4, 8), (118, 22, 26), 9)
    return clamp(a)


def k_chrome(w, h, r, c):
    a = plate(w, h, r, (168, 174, 186), (214, 220, 230), (112, 116, 128), 2, rivets=False)
    # a soft vertical sheen
    for x in range(w):
        a[:, x, :3] += 14 * math.sin(math.pi * (x + 0.5) / max(1, w))
    return clamp(a)


def k_carbon(w, h, r, c):
    a = gm.solid(w, h, (30, 31, 37))
    for y in range(h):
        for x in range(w):
            if ((x // 2) + (y // 2)) % 2 == 0:
                a[y, x, :3] = (40, 41, 49)
    a[0, :, :3] = (58, 60, 70)
    a[:, 0, :3] = (58, 60, 70)
    return clamp(a)


def k_tank(w, h, r, c):
    a = plate(w, h, r, (70, 72, 82), (104, 108, 120), (40, 41, 48), 2, rivets=False)
    hseams(a, 6, (34, 35, 41), (90, 93, 104), 3)
    return clamp(a)


gm.KINDS.update({
    "crimson": k_crimson,
    "crimson_dark": k_crimson_dark,
    "chrome": k_chrome,
    "carbon": k_carbon,
    "tank": k_tank,
})


# ----------------------------------------------------------------------
# Geometria auxiliar
# ----------------------------------------------------------------------
def poly(m, name, cx, cz, r_in, r_out, y, sides=8, step=0.1, gap=None, rot=22.5, skip=(), occlude=True, **mats):
    """Anel (ou disco com r_in = 0) de um poligono regular de `sides` lados,
    rasterizado em caixas; `gap` = (de, ate) graus omitidos (0 = frente)."""
    n = int(math.ceil(r_out / step)) + 1
    k = 0
    seg = 2 * math.pi / sides
    rr = math.radians(rot)

    def inside(d, ang, r):
        if r <= 0:
            return False
        a = (ang + rr) % seg - seg / 2.0
        return d <= r * math.cos(seg / 2.0) / math.cos(a)

    for row in range(-n, n):
        z0 = cz + row * step
        z1 = z0 + step
        zc = (z0 + z1) / 2.0
        run = None
        for col in range(-n, n + 1):
            x0 = cx + col * step
            xc = x0 + step / 2.0
            d = math.hypot(xc - cx, zc - cz)
            ang = math.atan2(xc - cx, -(zc - cz))
            ok = inside(d, ang, r_out) and not inside(d, ang, r_in)
            if ok and gap is not None:
                deg = math.degrees(ang)
                ok = not (gap[0] <= deg <= gap[1])
            if ok and col < n:
                run = (run[0], x0 + step) if run else (x0, x0 + step)
                continue
            if run:
                m.box("%s_%d" % (name, k), (round(run[0], 4), round(run[1], 4)), y,
                      (round(z0, 4), round(z1, 4)), skip=skip, occlude=occlude, **mats)
                k += 1
                run = None
    return k


def post(m, name, x, z, half, y, **mats):
    m.box(name, (round(x - half, 4), round(x + half, 4)), y, (round(z - half, 4), round(z + half, 4)), **mats)


def build_machine():
    """Transformation chamber v9: the Speed Force accelerator.

    Cabin centre (2.0, 0.95), floor at 0.5 (the Java side relies on both)."""
    m = gm.Model("transformation_chamber", 8.0)
    B = m.box
    CX, CZ = 2.0, 0.95
    FL = 0.5
    TOP = 3.95

    # ----- octagonal stepped platform -----
    poly(m, "base1", CX, CZ, 0.0, 1.95, (0.0, 0.2), all="crimson_dark", top="carbon")
    poly(m, "base2", CX, CZ, 0.0, 1.62, (0.2, 0.36), all="crimson", top="carbon")
    poly(m, "base2_rim", CX, CZ, 1.5, 1.62, (0.36, 0.39), all="gold", skip=("bottom",), occlude=False)
    poly(m, "base3", CX, CZ, 0.0, 1.3, (0.36, FL), all="chrome", top="floor_plate")
    gm.disc(m, "floor_glow", CX, CZ, 0.0, 0.78, (FL, FL + 0.02), top="g:floor",
            skip=("front", "back", "left", "right", "bottom"), occlude=False)
    # light channels running out on the platform (at the octagon's corners)
    for i in range(8):
        deg = i * 45.0
        if -30 <= ((deg + 180) % 360 - 180) <= 30:
            continue
        x0, z0 = gm.polar(CX, CZ, 1.66, deg)
        x1, z1 = gm.polar(CX, CZ, 1.88, deg)
        post(m, "chan_%d" % i, (x0 + x1) / 2, (z0 + z1) / 2, 0.06, (0.2, 0.23), all="g:bar",
             skip=("bottom",), occlude=False)
    # front ramp (two steps) with a light strip
    B("step1", (1.25, 2.75), (0.0, 0.18), (-1.0, -0.78), all="crimson", top="carbon", front="crimson_dark")
    B("step2", (1.35, 2.65), (0.18, 0.34), (-0.78, -0.55), all="crimson", top="carbon", front="crimson_dark")
    B("step_led1", (1.4, 2.6), (0.07, 0.12), (-1.03, -1.0), all="g:bar")
    B("step_led2", (1.45, 2.55), (0.24, 0.29), (-0.8, -0.78), all="g:bar")

    # ----- the cage: ribs round the cabin, each with a filament of light -----
    for i, deg in enumerate((-72, -108, -144, 180, 144, 108, 72)):
        x, z = gm.polar(CX, CZ, 1.2, deg)
        post(m, "rib_%d" % i, x, z, 0.06, (FL, TOP), all="chrome")
        xi, zi = gm.polar(CX, CZ, 1.12, deg)
        post(m, "rib_glow_%d" % i, xi, zi, 0.025, (FL + 0.3, TOP - 0.3), all="g:bar", occlude=False)
    # rings binding the cage at three heights (open in front)
    for j, yy in enumerate((1.2, 2.6, 3.6)):
        gm.disc(m, "band%d" % j, CX, CZ, 1.16, 1.28, (yy, yy + 0.06), step=0.06, gap=(-62, 62),
                all="gold", occlude=False)

    # ----- the two front pillars, tapering -----
    for side, deg in (("L", -60), ("R", 60)):
        x, z = gm.polar(CX, CZ, 1.42, deg)
        post(m, "pil_base_%s" % side, x, z, 0.26, (FL, 1.0), all="crimson_dark", top="carbon")
        post(m, "pil_%s" % side, x, z, 0.19, (1.0, TOP), all="crimson", front="crimson_dark")
        post(m, "pil_cap_%s" % side, x, z, 0.24, (TOP - 0.25, TOP), all="chrome")
        for k, yy in enumerate((1.55, 2.35, 3.15)):
            post(m, "pil_ring_%s%d" % (side, k), x, z, 0.215, (yy, yy + 0.07), all="gold", occlude=False)
        # a slot of light down the pillar, facing the entrance
        B("pil_slot_%s" % side, (round(x - 0.05, 4), round(x + 0.05, 4)), (1.1, TOP - 0.35),
          (round(z - 0.21, 4), round(z - 0.19, 4)), all="g:bar", occlude=False)

    # ----- the accelerator ring and its magnets -----
    poly(m, "ring", CX, CZ, 1.5, 1.74, (2.05, 2.3), sides=16, step=0.08, gap=(-42, 42), rot=0.0,
         all="crimson", top="crimson_dark", bottom="carbon")
    gm.disc(m, "ring_glow", CX, CZ, 1.44, 1.5, (2.11, 2.24), step=0.06, gap=(-42, 42), all="g:bar",
            occlude=False)
    for k, deg in enumerate((-110, 180, 110)):
        x, z = gm.polar(CX, CZ, 1.62, deg)
        post(m, "magnet_%d" % k, x, z, 0.22, (1.92, 2.43), all="chrome", top="carbon")
        post(m, "magnet_core_%d" % k, x, z, 0.235, (2.12, 2.23), all="g:red", occlude=False)

    # ----- the back: wall, emblem facing the cabin, reactor -----
    poly(m, "wall", CX, CZ, 1.3, 1.48, (FL, TOP), sides=16, step=0.08, gap=(-118, 118), rot=0.0,
         all="crimson_dark", top="carbon")
    zb = CZ + 1.28
    B("plaque", (1.5, 2.5), (1.5, 3.1), (zb - 0.04, zb), all="carbon", front="carbon")
    B("plaque_rim_t", (1.45, 2.55), (3.08, 3.14), (zb - 0.07, zb), all="gold")
    B("plaque_rim_b", (1.45, 2.55), (1.46, 1.52), (zb - 0.07, zb), all="gold")
    B("plaque_rim_l", (1.45, 1.51), (1.52, 3.08), (zb - 0.07, zb), all="gold")
    B("plaque_rim_r", (2.49, 2.55), (1.52, 3.08), (zb - 0.07, zb), all="gold")
    B("bolt", (1.6, 2.4), (1.62, 2.98), (zb - 0.1, zb - 0.07), front="g:bolt",
      skip=("back", "left", "right", "top", "bottom"), occlude=False)
    # reactor behind the wall
    B("reactor", (1.2, 2.8), (0.0, 2.7), (2.45, 2.95), all="tank", top="carbon", back="vent")
    B("reactor_band", (1.18, 2.82), (1.25, 1.37), (2.43, 2.97), all="gold", occlude=False)
    for k, x in enumerate((1.45, 2.0, 2.55)):
        B("reactor_tube_%d" % k, (round(x - 0.07, 4), round(x + 0.07, 4)), (2.7, 3.3), (2.62, 2.76),
          all="g:red", occlude=False)

    # ----- coil towers at the back corners -----
    for side, x in (("L", 0.42), ("R", 3.58)):
        zc = 2.55
        poly(m, "tower_%s" % side, x, zc, 0.0, 0.34, (0.0, 3.2), sides=6, step=0.06, rot=0.0,
             all="crimson_dark", top="chrome")
        for k in range(4):
            yy = 0.7 + k * 0.6
            poly(m, "tower_coil_%s%d" % (side, k), x, zc, 0.3, 0.4, (yy, yy + 0.14), sides=6, step=0.05,
                 rot=0.0, all="g:red", occlude=False)
        gm.disc(m, "tower_top_%s" % side, x, zc, 0.0, 0.22, (3.2, 3.36), step=0.05, all="gold")
    for side, x0, x1 in (("L", 0.62, 1.05), ("R", 2.95, 3.38)):
        B("cable_%s" % side, (x0, x1), (2.75, 2.88), (2.5, 2.62), all="carbon")

    # ----- crown, cone and lightning rod -----
    poly(m, "crown", CX, CZ, 0.0, 1.62, (TOP, TOP + 0.22), all="crimson", top="carbon", bottom="header_under")
    poly(m, "crown_rim", CX, CZ, 1.56, 1.66, (TOP + 0.07, TOP + 0.15), all="gold", occlude=False)
    gm.disc(m, "emitter", CX, CZ, 0.0, 0.85, (TOP - 0.03, TOP), bottom="g:grid",
            skip=("front", "back", "left", "right", "top"), occlude=False)
    poly(m, "cone1", CX, CZ, 0.0, 1.05, (TOP + 0.22, TOP + 0.42), all="crimson_dark", top="carbon")
    poly(m, "cone2", CX, CZ, 0.0, 0.72, (TOP + 0.42, TOP + 0.6), all="crimson", top="carbon")
    poly(m, "cone3", CX, CZ, 0.0, 0.45, (TOP + 0.6, TOP + 0.76), all="chrome")
    post(m, "rod", CX, CZ, 0.05, (TOP + 0.76, 5.24), all="chrome")
    for k, yy in enumerate((TOP + 0.9, TOP + 1.08)):
        gm.disc(m, "rod_ring%d" % k, CX, CZ, 0.06, 0.17, (yy, yy + 0.05), step=0.04, all="g:bar", occlude=False)
    post(m, "rod_tip", CX, CZ, 0.09, (5.24, 5.4), all="gold")
    # a lightning plate on the front of the crown
    zf = CZ - 1.55
    B("crown_plate", (1.6, 2.4), (TOP + 0.02, TOP + 0.2), (zf - 0.05, zf + 0.2), all="carbon")
    B("crown_bar", (1.66, 2.34), (TOP + 0.07, TOP + 0.15), (zf - 0.08, zf - 0.05), all="g:bar")

    # ----- side connector for the potion container (left, by the controller) - as before -----
    B("mount", (0.0, 0.28), (0.0, 1.9), (0.35, 1.2), all="crimson_dark", front="crimson",
      left="panel_box", top="chrome")
    B("mount_gold", (0.0, 0.3), (1.9, 1.96), (0.33, 1.22), all="gold")
    B("mount_led", (-0.02, 0.0), (0.4, 1.6), (0.7, 0.85), all="g:bar")
    return m


gm.build_machine = build_machine

if __name__ == "__main__":
    gm.main()
