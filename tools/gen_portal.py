"""Gera as texturas do portal da Forca de Aceleracao (vortice e brilho no chao).

Uso: python3 tools/gen_portal.py <pasta textures/effect>
"""
import os
import sys

import numpy as np
from PIL import Image

OUT = sys.argv[1] if len(sys.argv) > 1 else "."
N = 256
rng = np.random.default_rng(1015)


def smooth(a, b, x):
    t = np.clip((x - a) / (b - a), 0.0, 1.0)
    return t * t * (3 - 2 * t)


def value_noise(size, cells, seed):
    r = np.random.default_rng(seed)
    grid = r.random((cells + 1, cells + 1))
    ys = np.linspace(0, cells, size, endpoint=False)
    xs = np.linspace(0, cells, size, endpoint=False)
    y0 = ys.astype(int)
    x0 = xs.astype(int)
    fy = (ys - y0)[:, None]
    fx = (xs - x0)[None, :]
    fy = fy * fy * (3 - 2 * fy)
    fx = fx * fx * (3 - 2 * fx)
    g00 = grid[y0][:, x0]
    g01 = grid[y0][:, x0 + 1]
    g10 = grid[y0 + 1][:, x0]
    g11 = grid[y0 + 1][:, x0 + 1]
    return (g00 * (1 - fx) + g01 * fx) * (1 - fy) + (g10 * (1 - fx) + g11 * fx) * fy


yy, xx = np.mgrid[0:N, 0:N]
dx = (xx + 0.5) / N * 2 - 1
dy = (yy + 0.5) / N * 2 - 1
r = np.sqrt(dx * dx + dy * dy)
th = np.arctan2(dy, dx)


def vortex():
    # fundo azul-marinho / preto, mais escuro no centro (profundidade)
    depth = smooth(0.0, 0.95, r)
    col = np.zeros((N, N, 3))
    col[..., 0] = 3 + 12 * depth
    col[..., 1] = 6 + 26 * depth
    col[..., 2] = 18 + 70 * depth

    # bracos espirais convergindo para o centro
    warp = value_noise(N, 6, 11) * 0.9 + value_noise(N, 14, 12) * 0.35
    spiral = th * 3.0 + np.log(r + 0.04) * 5.5 + warp * 2.2
    arms = np.abs(np.sin(spiral)) ** 0.5
    line = np.exp(-((1 - arms) * 9.0))               # linhas finas
    fine_s = th * 7.0 + np.log(r + 0.04) * 9.0 + warp * 4.0
    fine = np.exp(-((1 - np.abs(np.sin(fine_s))) * 14.0))

    blue = np.array([60, 150, 255])
    gold = np.array([255, 196, 50])
    fade_in = smooth(0.08, 0.5, r) * (1 - 0.15 * smooth(0.8, 1.0, r))
    for c in range(3):
        col[..., c] += line * fade_in * blue[c] * 0.16
        col[..., c] += fine * fade_in * (gold[c] * 0.22 * smooth(0.45, 0.9, r)
                                         + blue[c] * 0.12 * (1 - smooth(0.45, 0.9, r)))

    # fissuras eletricas azuis irregulares
    cr = value_noise(N, 9, 21)
    cr = cr + 0.25 * value_noise(N, 31, 22)
    cracks = np.exp(-(np.abs(cr - 0.55) * 80.0)) * smooth(0.12, 0.6, r)
    for c in range(3):
        col[..., c] += cracks * np.array([120, 190, 255])[c] * 0.45

    # clarao dourado suave perto da borda para fundir com o anel
    rim = smooth(0.72, 0.97, r)
    for c in range(3):
        col[..., c] += rim * np.array([255, 170, 30])[c] * 0.22

    alpha = smooth(1.0, 0.86, r) * 0.97
    alpha *= 0.82 + 0.18 * smooth(0.0, 0.5, r)       # centro levemente mais translucido
    img = np.zeros((N, N, 4), np.uint8)
    img[..., :3] = np.clip(col, 0, 255).astype(np.uint8)
    img[..., 3] = np.clip(alpha * 255, 0, 255).astype(np.uint8)
    return img


def glow():
    base = (1 - np.clip(r, 0, 1)) ** 2.2
    n = value_noise(N, 8, 33)
    rings = np.zeros_like(r)
    for i, rr in enumerate((0.30, 0.43, 0.55, 0.66, 0.77, 0.87, 0.95)):
        wob = rr + 0.015 * np.sin(th * (3 + i) + i * 1.7) + (n - 0.5) * 0.03
        rings += np.exp(-((r - wob) * (46 - i * 3)) ** 2) * (0.75 - i * 0.06)
    spokes = np.exp(-((np.sin(th * 6 + n * 4) * 0.5 + 0.5) - 1) ** 2 * 6) * smooth(0.95, 0.2, r) * 0.12
    a = np.clip(base * 0.55 + rings * 0.9 + spokes, 0, 1) * smooth(1.0, 0.82, r)
    img = np.zeros((N, N, 4), np.uint8)
    img[..., 0] = 255
    img[..., 1] = np.clip(170 + 70 * rings, 0, 255).astype(np.uint8)
    img[..., 2] = np.clip(20 + 70 * rings, 0, 255).astype(np.uint8)
    img[..., 3] = np.clip(a * 255, 0, 255).astype(np.uint8)
    return img


os.makedirs(OUT, exist_ok=True)
Image.fromarray(vortex(), "RGBA").save(os.path.join(OUT, "portal_vortex.png"), optimize=True)
Image.fromarray(glow(), "RGBA").save(os.path.join(OUT, "portal_glow.png"), optimize=True)
print("ok")
