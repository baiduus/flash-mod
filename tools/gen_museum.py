"""Designs the Flash Museum and writes worldgen/FlashMuseumData.java.
usage: python3 gen_museum.py <out FlashMuseumData.java> [preview_dir]

Canonical frame: x to the right (0..W-1, centre x=19), z from the entrance (z=0)
to the back wall, y=0 is the foundation row, y=1 the floor.  The Java piece
rotates / mirrors it for the chosen orientation."""
import math, sys, os
W, D, H = 39, 51, 36
CX = 19
grid = {}          # (x,y,z) -> key

def put(x, y, z, k):
    if 0 <= x < W and 0 <= y < H and 0 <= z < D:
        grid[(x, y, z)] = k

def box(x0, y0, z0, x1, y1, z1, k):
    for x in range(min(x0, x1), max(x0, x1) + 1):
        for y in range(min(y0, y1), max(y0, y1) + 1):
            for z in range(min(z0, z1), max(z0, z1) + 1):
                put(x, y, z, k)

def air(x0, y0, z0, x1, y1, z1):
    for x in range(min(x0, x1), max(x0, x1) + 1):
        for y in range(min(y0, y1), max(y0, y1) + 1):
            for z in range(min(z0, z1), max(z0, z1) + 1):
                grid.pop((x, y, z), None)

FLIP = {"E": "W", "W": "E", "N": "N", "S": "S"}
def mir(k):
    if k and "_" in k and k.rsplit("_", 1)[1] in FLIP and k.split("_")[0].endswith(("STB", "STT")):
        a, d = k.rsplit("_", 1)
        return a + "_" + FLIP[d]
    return k

def sym(x, y, z, k):
    put(x, y, z, k); put(W - 1 - x, y, z, mir(k))

def symbox(x0, y0, z0, x1, y1, z1, k):
    box(x0, y0, z0, x1, y1, z1, k); box(W - 1 - x0, y0, z0, W - 1 - x1, y1, z1, mir(k))

def symair(x0, y0, z0, x1, y1, z1):
    air(x0, y0, z0, x1, y1, z1); air(W - 1 - x0, y0, z0, W - 1 - x1, y1, z1)

# --------------------------------------------------------------------------
# 1. Foundation floor and plaza
# --------------------------------------------------------------------------
for x in range(W):
    for z in range(D):
        put(x, 1, z, "FLOOR")
# plaza in front of the building: chequer and gilded bolt-shaped inlay
for x in range(5, 34):
    for z in range(0, 6):
        put(x, 1, z, "DS" if (x + z) % 2 == 0 else "BLK")
for z in range(0, 6):
    for x in (16, 22):
        put(x, 1, z, "GILD")
    for x in range(17, 22):
        put(x, 1, z, "RCARPET_FLOOR")
# garden strips with planters at both ends of the plaza
for x in list(range(0, 5)) + list(range(34, 39)):
    for z in range(0, 6):
        put(x, 1, z, "MOSS" if (x * 7 + z * 3) % 5 else "DS")
        if (x + z) % 3 == 0:
            put(x, 2, z, "AZALEA")
        elif (x + z) % 3 == 1:
            put(x, 2, z, "MOSSC")

# --------------------------------------------------------------------------
# 2. Outer shell
# --------------------------------------------------------------------------
# aisle volumes (x 3..35, z 6..50) up to y=13
box(3, 2, 6, 35, 13, 6, "WALL")
box(3, 2, 50, 35, 13, 50, "WALL")
box(3, 2, 6, 3, 13, 50, "WALL")
box(35, 2, 6, 35, 13, 50, "WALL")
box(3, 2, 7, 35, 13, 7, "WALL")       # front wall is 2 thick

# plinth and cornice bands
for x in range(3, 36):
    put(x, 2, 6, "BLKB"); put(x, 12, 6, "GILD" if x % 2 else "BLKB")
for z in range(6, 51):
    put(3, 2, z, "BLKB"); put(35, 2, z, "BLKB")
    put(3, 12, z, "GILD" if z % 2 else "BLKB"); put(35, 12, z, "GILD" if z % 2 else "BLKB")
for x in range(3, 36):
    put(x, 2, 50, "BLKB"); put(x, 12, 50, "GILD" if x % 2 else "BLKB")

# piers on the long walls, tall stained windows between them
pier_z = [6, 12, 18, 24, 30, 36, 42, 48, 50]
for z in pier_z:
    for y in range(2, 14):
        put(3, y, z, "QB" if 3 <= y <= 11 else "BLKB")
        put(35, y, z, "QB" if 3 <= y <= 11 else "BLKB")
for zc in (9, 15, 21, 27, 39, 45):
    for dz in range(-1, 2):
        z = zc + dz
        for y in range(4, 11):
            k = "RGLASS" if (y + dz) % 2 else "YGLASS"
            if y == 10 and dz != 0:
                k = "WALL"
            if y == 4:
                k = "BLKB"
            put(3, y, z, k); put(35, y, z, k)

# --------------------------------------------------------------------------
# 3. Front: plaza columns, portico, frontispiece, towers
# --------------------------------------------------------------------------
# portico (open colonnade)
box(7, 1, 2, 31, 1, 5, "QS")
for x in range(17, 22):
    for z in range(0, 6):
        put(x, 2, z, "RCARPET")
for x in (16, 22):
    for z in range(0, 6):
        put(x, 2, z, "YCARPET")
for x in (8, 12, 16, 22, 26, 30):
    put(x, 2, 3, "QC"); put(x, 3, 3, "QPB")
    for y in range(4, 10):
        put(x, y, 3, "QP")
    put(x, 10, 3, "QC")
box(7, 11, 2, 31, 11, 5, "QS")
for x in range(7, 32):
    put(x, 12, 2, "GILD" if x % 2 else "BLK")
    put(x, 12, 3, "QS"); put(x, 12, 4, "QS"); put(x, 12, 5, "QS")
box(7, 13, 2, 31, 13, 5, "QSLAB_B")
# cornice stairs facing the plaza
for x in range(7, 32):
    put(x, 12, 1, "QSTT_N") if False else None
    put(x, 13, 1, "QSTB_N")
# lanterns hanging under the portico roof
for x in (10, 14, 24, 28):
    put(x, 10, 4, "HLANT")

# frontispiece wall above the entrance with the big emblem
box(9, 14, 6, 29, 28, 7, "QB")
for x in range(9, 30):
    put(x, 14, 6, "GILD" if x % 2 else "BLKB")
    put(x, 28, 6, "GILD"); put(x, 28, 7, "BLKB")
for y in range(14, 29):
    put(9, y, 6, "BLKB"); put(29, y, 6, "BLKB")
    put(10, y, 6, "GILD" if y % 2 else "BLKB"); put(28, y, 6, "GILD" if y % 2 else "BLKB")
# stepped pediment crown
for i, hw in enumerate((9, 7, 5, 3, 1)):
    y = 29 + i
    for x in range(CX - hw, CX + hw + 1):
        put(x, y, 6, "QB"); put(x, y, 7, "QB")
    put(CX - hw, y, 6, "QSTB_E"); put(CX + hw, y, 6, "QSTB_W")
    put(CX - hw, y, 7, "QSTB_E"); put(CX + hw, y, 7, "QSTB_W")
put(CX, 34, 6, "ROD"); put(CX, 35, 6, "ROD")
for x in (9, 29):
    put(x, 29, 6, "GOLD"); put(x, 30, 6, "ROD")

# emblem: dark disc, gilded ring, orange + yellow bolt (bolt cells are mirrored in Java)
ECX, ECY = CX, 21
for dx in range(-7, 8):
    for dy in range(-7, 8):
        dist = math.hypot(dx, dy)
        if dist <= 7.0:
            ring = dist > 5.9
            glow = ring and round(math.degrees(math.atan2(dy, dx)) / 30.0) % 2 == 0
            put(ECX + dx, ECY + dy, 6, ("SHROOM" if glow else "GILD") if ring else "BLKB")
bolt = [(2,5),(3,5),(1,4),(2,4),(3,4),(0,3),(1,3),(2,3),(-1,2),(0,2),(1,2),(-2,1),(-1,1),(0,1),(1,1),
        (2,1),(3,1),(0,0),(1,0),(2,0),(-1,-1),(0,-1),(1,-1),(-2,-2),(-1,-2),(0,-2),(-3,-3),(-2,-3),
        (-1,-3),(-3,-4),(-2,-4),(-4,-5)]
for dx, dy in bolt:
    put(ECX + dx, ECY + dy, 6, "ORANGE")
for dx, dy in bolt:
    if (dx + dy) % 2 == 0:
        put(ECX + dx, ECY + dy, 6, "YELLOW")

# grand doorway (7 wide, 8 high) with an arched head
air(16, 2, 6, 22, 9, 7)
for y in range(2, 11):
    k = "GILD" if y % 2 == 0 else "BLKB"
    put(15, y, 6, k); put(23, y, 6, k); put(15, y, 7, k); put(23, y, 7, k)
for x in range(15, 24):
    put(x, 10, 6, "GOLD" if x == CX else "GILD"); put(x, 10, 7, "GILD")
air(16, 10, 6, 22, 10, 7)
for x in range(16, 23):
    put(x, 10, 6, "GOLD" if x == CX else "GILD")
put(CX - 3, 9, 6, "OXSTB_E") if False else None
for x in (16, 22):
    put(x, 9, 6, "GILD"); put(x, 9, 7, "GILD")
air(17, 9, 6, 21, 9, 7)
for y in range(2, 9):
    pass
# door guard lanterns
for x in (14, 24):
    put(x, 2, 5, "BLKB"); put(x, 3, 5, "BLKB"); put(x, 4, 5, "LANT")

# corner towers
def tower(x0):
    x1 = x0 + 5
    box(x0, 2, 2, x1, 20, 7, "WALL")
    for y in range(2, 21):
        for (x, z) in ((x0, 2), (x1, 2), (x0, 7), (x1, 7)):
            put(x, y, z, "QB" if 3 <= y <= 19 else "BLKB")
    for y in range(6, 18, 4):
        for x in (x0 + 2, x0 + 3):
            put(x, y, 2, "YGLASS"); put(x, y + 1, 2, "RGLASS"); put(x, y + 2, 2, "YGLASS")
    for x in range(x0, x1 + 1):
        for z in (2, 7):
            put(x, 20, z, "GILD")
    for z in range(2, 8):
        for x in (x0, x1):
            put(x, 20, z, "GILD")
    # pyramid roof
    layers = [(x0, x1, 2, 7), (x0 + 1, x1 - 1, 3, 6), (x0 + 2, x1 - 2, 4, 5)]
    for i, (xa, xb, za, zb) in enumerate(layers):
        box(xa, 21 + i, za, xb, 21 + i, zb, "OXR")
    put(x0 + 2, 24, 4, "GOLD"); put(x0 + 2, 25, 4, "ROD"); put(x0 + 2, 26, 4, "ROD")
    put(x0 + 3, 24, 5, "AIR")
tower(0); tower(33)
# tower faces stay open to the plaza: clear the part inside the aisle
air(4, 2, 7, 5, 13, 7)

# --------------------------------------------------------------------------
# 4. Interior: aisles, nave, colonnades
# --------------------------------------------------------------------------
air(4, 2, 8, 34, 12, 49)           # carve the whole interior first
# nave walls (x 12 / 26) go up to y=18 with clerestory windows
box(12, 2, 8, 12, 18, 23, "WALL"); box(26, 2, 8, 26, 18, 23, "WALL")
box(13, 14, 8, 25, 14, 8, "AIR")
# openings between the nave and the aisles: arches between columns
col_z = [8, 12, 16, 20, 24]
for z in range(8, 24):
    if z in col_z:
        continue
    for y in range(2, 11):
        for x in (12, 26):
            air(x, y, z, x, y, z)
for z in col_z:
    for x in (12, 26):
        for y in range(2, 14):
            put(x, y, z, "QP")
        put(x, 2, z, "QC"); put(x, 3, z, "QC"); put(x, 13, z, "QC")
        for y in range(14, 19):
            put(x, y, z, "QB")
# arch spandrels
for z in range(8, 24):
    if z in col_z:
        continue
    for x in (12, 26):
        put(x, 11, z, "QS"); put(x, 12, z, "GILD" if z % 2 else "BLKB")
# clerestory windows
for z in range(9, 23):
    if z % 4 == 0:
        continue
    for x in (12, 26):
        for y in (15, 16, 17):
            put(x, y, z, "YGLASS" if (y + z) % 2 else "RGLASS")
# nave end wall with grand opening to the rotunda
box(13, 2, 24, 25, 18, 24, "QB")
air(15, 2, 24, 23, 10, 24)
for x in range(15, 24):
    put(x, 11, 24, "GILD" if x != CX else "GOLD")
for y in range(2, 12):
    put(14, y, 24, "GILD" if y % 2 == 0 else "BLKB"); put(24, y, 24, "GILD" if y % 2 == 0 else "BLKB")
# nave roof and skylight
box(12, 19, 8, 26, 19, 24, "OXR")
box(11, 19, 7, 27, 19, 7, "QS"); box(11, 19, 7, 11, 19, 24, "QS"); box(27, 19, 7, 27, 19, 24, "QS")
for x in range(11, 28):
    put(x, 20, 7, "QB" if x % 2 else "GILD")
for z in range(8, 24):
    put(11, 20, z, "QB" if z % 2 else "GILD"); put(27, 20, z, "QB" if z % 2 else "GILD")
for x in range(17, 22):
    for z in range(10, 22):
        put(x, 19, z, "GLASS")
for z in range(10, 22):
    put(16, 19, z, "GILD"); put(22, 19, z, "GILD")
# aisle roofs
box(4, 13, 8, 11, 13, 49, "OXR"); box(27, 13, 8, 34, 13, 49, "OXR")
box(12, 13, 25, 26, 13, 49, "OXR")
for x in list(range(3, 12)) + list(range(27, 36)):
    for z in (6, 50):
        put(x, 14, z, "QB" if x % 2 else "BLKB")
for z in range(6, 51):
    put(3, 14, z, "QB" if z % 2 else "BLKB"); put(35, 14, z, "QB" if z % 2 else "BLKB")

# floors: chequer in the nave and aisles, carpet down the middle
for x in range(4, 35):
    for z in range(8, 50):
        put(x, 1, z, "DT" if (x + z) % 2 else "DS")
for x in range(13, 26):
    for z in range(8, 24):
        put(x, 1, z, "QS" if (x + z) % 2 else "BLK")
for z in range(0, 24):
    for x in range(17, 22):
        put(x, 2, z, "RCARPET")
    put(16, 2, z, "YCARPET"); put(22, 2, z, "YCARPET")

# chandeliers in the nave
for z in (11, 17, 22):
    for y in range(15, 19):
        put(CX, y, z, "CHAIN")
    put(CX, 14, z, "HLANT")
    for dx, dz in ((2, 0), (-2, 0), (0, 2), (0, -2)):
        put(CX + dx, 15, z + dz, "HLANT")
        for y in range(16, 19):
            put(CX + dx, y, z + dz, "CHAIN")
    for dx in (-1, 1):
        put(CX + dx, 15, z, "CHAINX") if False else None
# column lanterns
for z in col_z:
    put(11, 7, z, "LANT") if False else None

# --------------------------------------------------------------------------
# 5. Rotunda with the dome
# --------------------------------------------------------------------------
RC = (CX, 34)
DOME_Y = 18
def hr(x, z):
    return math.hypot(x - RC[0], z - RC[1])

# solid masses between the rotunda and the side galleries / back rooms
box(9, 2, 25, 29, 13, 49, "WALL")
# galleries (aisles) to the sides of the rotunda: x4..8 and x30..34
air(4, 2, 25, 8, 12, 49); air(30, 2, 25, 34, 12, 49)
# sanctum (suit room) behind the rotunda
air(14, 2, 46, 24, 10, 49)
# carve rotunda interior
for x in range(8, 31):
    for z in range(22, 47):
        r = hr(x, z)
        if r <= 8.5:
            air(x, 2, z, x, 34, z)
# drum (wall ring) y 2..13 and dome above
shell_in, shell_out = 8.5, 10.6
for x in range(7, 32):
    for z in range(22, 47):
        r = hr(x, z)
        if shell_in < r <= shell_out:
            for y in range(2, DOME_Y):
                if y == 2 or y == DOME_Y - 1 or y == 13:
                    put(x, y, z, "BLKB" if y == 2 else "GILD")
                else:
                    put(x, y, z, "QB")
            ang = math.atan2(z - RC[1], x - RC[0])
            # pilasters every 45 degrees
            k = round(ang / (math.pi / 4))
            off = abs(ang - k * math.pi / 4) * r
            if off < 0.9:
                for y in range(3, DOME_Y - 1):
                    if y != 13:
                        put(x, y, z, "QP")
            else:
                # windows halfway between the pilasters
                k2 = round((ang - math.pi / 8) / (math.pi / 4))
                off2 = abs(ang - (k2 * math.pi / 4 + math.pi / 8)) * r
                if off2 < 1.3:
                    for y in list(range(6, 11)) + list(range(14, 17)):
                        put(x, y, z, "YGLASS" if (y % 2) else "RGLASS")
# dome
for x in range(7, 32):
    for z in range(22, 47):
        for y in range(DOME_Y, DOME_Y + 13):
            dy = y - DOME_Y
            d3 = math.sqrt(hr(x, z) ** 2 + dy * dy)
            if shell_in < d3 <= shell_out:
                ang = math.atan2(z - RC[1], x - RC[0])
                k = round(ang / (math.pi / 4))
                off = abs(ang - k * math.pi / 4) * max(hr(x, z), 0.01)
                if y == DOME_Y:
                    put(x, y, z, "GILD")
                elif off < 0.95 and hr(x, z) > 1.0:
                    put(x, y, z, "GOLD" if (y % 3 == 0) else "GILD")
                else:
                    put(x, y, z, "OXD")
# oculus
for x in range(15, 24):
    for z in range(30, 39):
        if hr(x, z) <= 2.6:
            for y in range(DOME_Y + 6, DOME_Y + 13):
                if (x, y, z) in grid and grid[(x, y, z)] in ("OXD", "GILD", "GOLD"):
                    grid[(x, y, z)] = "GLASS"
put(CX, DOME_Y + 11, RC[1], "GOLD"); put(CX, DOME_Y + 12, RC[1], "ROD"); put(CX, DOME_Y + 13, RC[1], "ROD")
for dx, dz in ((3, 0), (-3, 0), (0, 3), (0, -3)):
    pass
# arches in the drum: north (from the nave), west/east (galleries), south (sanctum)
def drum_opening(x0, x1, z0, z1, top):
    air(x0, 2, z0, x1, top, z1)
# north: reuse the nave end wall opening, carve the drum too
air(15, 2, 22, 23, 10, 26)
# west and east doors (5 wide, 8 high)
air(7, 2, 32, 11, 9, 36); air(27, 2, 32, 31, 9, 36)
for y in range(2, 11):
    for z in (31, 37):
        for x in (8, 9, 10, 28, 29, 30):
            if (x, y, z) in grid and grid[(x, y, z)] != "AIR":
                pass
for z in range(32, 37):
    for x in (8, 9, 10, 28, 29, 30):
        put(x, 10, z, "GILD")
# south tunnel to the sanctum
air(17, 2, 42, 21, 9, 46)
for y in range(2, 10):
    put(16, y, 43, "GILD" if y % 2 == 0 else "BLKB"); put(22, y, 43, "GILD" if y % 2 == 0 else "BLKB")
    put(16, y, 44, "QB"); put(22, y, 44, "QB")
for x in range(16, 23):
    put(x, 10, 43, "GILD"); put(x, 10, 44, "GILD")
# rotunda floor: concentric rings with gold spokes
for x in range(9, 30):
    for z in range(23, 46):
        r = hr(x, z)
        if r <= 8.5:
            if r < 2.0:
                k = "GOLD" if (x + z) % 2 else "SHROOM"
            elif r < 3.2:
                k = "BLK"
            elif r < 6.2:
                k = "QS" if (x + z) % 2 else "DS"
            elif r < 7.2:
                k = "RFLOOR"
            else:
                k = "BLK"
            ang = math.atan2(z - RC[1], x - RC[0])
            kk = round(ang / (math.pi / 4))
            if abs(ang - kk * math.pi / 4) * max(r, .01) < 0.55 and 3.2 <= r < 7.2:
                k = "GILD"
            put(x, 1, z, k)
# carpet from the nave across the rotunda to the sanctum
for z in range(24, 47):
    for x in range(17, 22):
        if grid.get((x, 2, z)) in (None, "AIR"):
            put(x, 2, z, "RCARPET")
    for x in (16, 22):
        if grid.get((x, 2, z)) in (None, "AIR") and not (z in range(30, 39) and False):
            put(x, 2, z, "YCARPET")
# central pedestal with the display suit
for x in range(18, 21):
    for z in range(33, 36):
        put(x, 2, z, "QS"); put(x, 3, z, "QC")
        put(x, 4, z, "GOLD" if (x, z) == (CX, 34) else "QS")
for dx in (-1, 1):
    for dz in (-1, 1):
        put(CX + dx, 5, 34 + dz, "LANT")
put(CX, 5, 34, "STAND_GOLD")
# lanterns on the wall pilasters (dim, warm)
for k in range(8):
    a = k * math.pi / 4
    x = round(RC[0] + math.cos(a) * 8.0); z = round(RC[1] + math.sin(a) * 8.0)
    if grid.get((x, 3, z)) in (None, "AIR"):
        put(x, 8, z, "LANT") if False else None

# --------------------------------------------------------------------------
# 6. Sanctum (suit room)
# --------------------------------------------------------------------------
box(13, 2, 45, 25, 10, 45, "QB")
air(17, 2, 45, 21, 9, 45)
for x in range(14, 25):
    for z in range(46, 50):
        put(x, 1, z, "GILD" if (x + z) % 3 == 0 else ("BLK" if (x + z) % 2 else "BLKB"))
box(13, 11, 45, 25, 13, 50, "WALL")
box(13, 2, 46, 13, 10, 49, "QB"); box(25, 2, 46, 25, 10, 49, "QB")
box(14, 11, 46, 24, 11, 49, "BLKB")
for x in range(14, 25):
    for z in range(46, 50):
        pass
# gilded frieze on the back wall
for x in range(14, 25):
    put(x, 10, 49, "GILD" if x % 2 else "BLKB")
# dais with the chest
box(17, 2, 47, 21, 2, 49, "BLKB"); box(17, 3, 47, 21, 3, 49, "GILD")
box(18, 3, 48, 20, 3, 49, "BLKB") if False else None
put(CX, 4, 48, "CHEST")
for dx in (-2, 2):
    for dz in (47, 49):
        put(CX + dx, 4, dz, "ROD"); put(CX + dx, 5, dz, "ROD")
for x in (15, 23):
    put(x, 2, 47, "BLKB"); put(x, 3, 47, "BLKB"); put(x, 4, 47, "LANT")
    put(x, 2, 49, "BLKB"); put(x, 3, 49, "BLKB"); put(x, 4, 49, "LANT")
for x in range(17, 22):
    put(x, 2, 46, "RCARPET")
# sanctum ceiling lamp
put(CX, 10, 47, "CHAIN"); put(CX, 9, 47, "HLANT") if False else None
put(CX, 9, 48, "HLANT")
put(CX, 10, 48, "CHAIN")

# --------------------------------------------------------------------------
# 7. Showcases (glass cases with a suit on a stand) and gallery details
# --------------------------------------------------------------------------
cases = []   # (x, z, west?) specials handled by Java
def case(cx, cz, suit):
    box(cx - 1, 2, cz - 1, cx + 1, 2, cz + 1, "BLKB")
    for x in range(cx - 1, cx + 2):
        for z in range(cz - 1, cz + 2):
            edge = x in (cx - 1, cx + 1) or z in (cz - 1, cz + 1)
            put(x, 2, z, "BLKB")
            if edge:
                for y in range(3, 7):
                    put(x, y, z, "GLASS")
            put(x, 7, z, "GILD" if suit else "BLKB")
    put(cx, 2, cz, "SEAL")      # lit plinth under the stand
    put(cx, 7, cz, "SEAL")      # and a light in the case roof
    put(cx, 3, cz, "STAND_SUIT" if suit else "STAND_OLD")
for z in (11, 17, 23):
    case(6, z, False); case(32, z, True)
for z in (28, 41, 47):
    case(6, z, z == 41); case(32, z, z != 41)
# wall lanterns / hanging lanterns in the aisles
for z in (9, 13, 19, 25, 30, 38, 44):
    put(8, 12, z, "CHAIN"); put(8, 11, z, "HLANT")
    put(30, 12, z, "CHAIN"); put(30, 11, z, "HLANT")
for z in (14, 20, 28, 40, 46):
    put(4, 8, z, "LANT") if False else None
# benches in the aisles
for z in (14, 20):
    for x in (9, 10):
        put(x, 2, z, "BENCH_" + ("E" if x == 9 else "W")) if False else None

# --------------------------------------------------------------------------
# 8. Extra splendour: plaza obelisks and the golden bolt hanging in the rotunda
# --------------------------------------------------------------------------
for ox in (2, 36):
    oz = 0
    box(ox - 1, 2, oz - 1, ox + 1, 2, oz + 1, "BLKB")
    put(ox, 2, oz, "GILD")
    for y in range(3, 9):
        put(ox, y, oz, "QP" if y < 8 else "QC")
    put(ox, 9, oz, "GOLD"); put(ox, 10, oz, "SHROOM"); put(ox, 11, oz, "ROD")
    for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        put(ox + dx, 3, oz + dz, "LANT")

# a giant gilded lightning bolt hanging under the dome (plane z = 34)
SCULPT = {17: (1, 3), 16: (0, 2), 15: (-1, 1), 14: (-2, 3), 13: (0, 2), 12: (-1, 1), 11: (-2, 0), 10: (-3, -2)}
for y, (a, b) in SCULPT.items():
    for dx in range(a, b + 1):
        edge = dx in (a, b)
        put(CX + dx, y, 34, "GOLD" if edge else "SHROOM")
for y in range(18, DOME_Y + 12):
    if grid.get((CX + 2, y, 34)) in (None, "AIR"):
        put(CX + 2, y, 34, "CHAIN")
    if grid.get((CX - 1, y, 34)) in (None, "AIR"):
        put(CX - 1, y, 34, "CHAIN")
for y in range(16, 18):
    put(CX - 1, y, 34, "CHAIN") if grid.get((CX - 1, y, 34)) in (None, "AIR") else None

# --------------------------------------------------------------------------
# Output
# --------------------------------------------------------------------------
# keys -> java expressions (the java side wraps random aging)
PALETTE = {
    "AIR": "AIR",
    "FLOOR": "agedFloor(random)", "DS": "POLISHED", "DT": "TILES", "BLK": "BLACKSTONE",
    "BLKB": "BLACK_BRICKS", "GILD": "GILDED", "GOLD": "GOLD",
    "WALL": "aged(random)", "QB": "QUARTZ_BRICKS", "QS": "SMOOTH_QUARTZ", "QC": "CHISELED_QUARTZ",
    "QP": "QUARTZ_PILLAR", "QPB": "QUARTZ_BRICKS",
    "QSLAB_B": "stateSlabQuartz(false)",
    "QSTB_N": "stairQuartz(Direction.NORTH, Half.BOTTOM)", "QSTB_E": "stairQuartz(Direction.EAST, Half.BOTTOM)",
    "QSTB_W": "stairQuartz(Direction.WEST, Half.BOTTOM)", "QSTB_S": "stairQuartz(Direction.SOUTH, Half.BOTTOM)",
    "OXR": "roofCopper(random)", "OXD": "domeCopper(random)",
    "GLASS": "GLASS", "RGLASS": "RED_GLASS", "YGLASS": "YELLOW_GLASS",
    "RCARPET": "CARPET", "RCARPET_FLOOR": "CARPET", "YCARPET": "YELLOW_CARPET", "RFLOOR": "RED_TERRACOTTA",
    "ORANGE": "ORANGE_CONCRETE", "YELLOW": "YELLOW_CONCRETE",
    "LANT": "LANTERN", "HLANT": "LANTERN_HANGING", "CHAIN": "CHAIN", "ROD": "ROD",
    "MOSS": "MOSS", "MOSSC": "MOSS_CARPET", "AZALEA": "AZALEA",
    "SHROOM": "SHROOMLIGHT", "SEAL": "SEA_LANTERN",
    "CHEST": "AIR", "STAND_GOLD": "AIR", "STAND_SUIT": "AIR", "STAND_OLD": "AIR",
}
# the data file
used = sorted({k for k in grid.values()} | {"AIR"})
alphabet = [chr(c) for c in range(48, 123) if chr(c) not in '\\"\'']
CHAR = {k: alphabet[i] for i, k in enumerate(used)}
SPECIAL_CHARS = {"CHEST": CHAR["CHEST"], "STAND_GOLD": CHAR["STAND_GOLD"],
                 "STAND_SUIT": CHAR["STAND_SUIT"], "STAND_OLD": CHAR["STAND_OLD"],
                 "ORANGE": CHAR["ORANGE"], "YELLOW": CHAR["YELLOW"]}

def layer(y):
    rows = []
    for z in range(D):
        rows.append("".join(CHAR.get(grid.get((x, y, z), "AIR"), CHAR["AIR"]) if grid.get((x, y, z)) else CHAR["AIR"] for x in range(W)))
    return rows

out = sys.argv[1]
lines = []
lines.append("package com.flash.worldgen;\n")
lines.append("/** Generated by tools/gen_museum.py. Do not edit by hand. */")
lines.append("final class FlashMuseumData {\n")
lines.append(f"    static final int W = {W}, D = {D}, H = {H};")
lines.append(f"    static final char C_CHEST = '{SPECIAL_CHARS['CHEST']}', C_STAND_GOLD = '{SPECIAL_CHARS['STAND_GOLD']}',")
lines.append(f"            C_STAND_SUIT = '{SPECIAL_CHARS['STAND_SUIT']}', C_STAND_OLD = '{SPECIAL_CHARS['STAND_OLD']}',")
lines.append(f"            C_ORANGE = '{SPECIAL_CHARS['ORANGE']}', C_YELLOW = '{SPECIAL_CHARS['YELLOW']}', C_AIR = '{CHAR['AIR']}';\n")
lines.append("    /** layer[y][z] is a string of W characters (x = 0..W-1). */")
lines.append("    static final String[][] LAYERS = new String[H][];")
lines.append("    static {")
for y in range(H):
    rows = layer(y)
    if all(set(r) <= {CHAR["AIR"]} for r in rows):
        lines.append(f"        LAYERS[{y}] = null;")
        continue
    lines.append(f"        LAYERS[{y}] = new String[] {{")
    for r in rows:
        lines.append(f'            "{r}",')
    lines.append("        };")
lines.append("    }\n")
lines.append("    /** Character -> palette key (resolved by FlashMuseumPiece.resolve). */")
lines.append("    static String key(char c) {")
lines.append("        switch (c) {")
for k, c in CHAR.items():
    lines.append(f"            case '{c}': return \"{k}\";")
lines.append("            default: return \"AIR\";")
lines.append("        }")
lines.append("    }")
lines.append("}")
open(out, "w").write("\n".join(lines) + "\n")
print("blocks:", len(grid), "keys:", len(used), "size:", W, D, H)

# --------------------------------------------------------------------------
# Previews
# --------------------------------------------------------------------------
if len(sys.argv) > 2:
    from PIL import Image
    pd = sys.argv[2]; os.makedirs(pd, exist_ok=True)
    COL = {"FLOOR": (90, 90, 95), "DS": (70, 70, 75), "DT": (55, 55, 60), "BLK": (30, 28, 34), "BLKB": (38, 34, 42),
           "GILD": (230, 190, 70), "GOLD": (250, 210, 50), "WALL": (95, 95, 100), "QB": (232, 226, 214),
           "QS": (240, 236, 226), "QC": (225, 220, 205), "QP": (235, 230, 218), "QPB": (232, 226, 214),
           "QSLAB_B": (240, 236, 226), "QSTB_N": (232, 226, 214), "QSTB_E": (232, 226, 214), "QSTB_W": (232, 226, 214),
           "QSTB_S": (232, 226, 214), "OXR": (60, 150, 130), "OXD": (50, 160, 140), "GLASS": (170, 200, 220),
           "RGLASS": (200, 50, 50), "YGLASS": (230, 200, 60), "RCARPET": (170, 20, 30), "RCARPET_FLOOR": (170, 20, 30),
           "YCARPET": (220, 190, 40), "RFLOOR": (150, 60, 45), "ORANGE": (240, 120, 20), "YELLOW": (250, 220, 20),
           "LANT": (255, 200, 90), "HLANT": (255, 200, 90), "CHAIN": (60, 60, 70), "ROD": (200, 120, 80),
           "MOSS": (80, 120, 40), "MOSSC": (90, 130, 50), "AZALEA": (60, 140, 60), "CHEST": (150, 100, 40),
           "SHROOM": (255, 170, 60), "SEAL": (200, 230, 230),
           "STAND_GOLD": (255, 255, 255), "STAND_SUIT": (255, 255, 255), "STAND_OLD": (255, 255, 255)}
    def c(k, shade=1.0):
        r, g, b = COL.get(k, (255, 0, 255))
        return (int(r * shade), int(g * shade), int(b * shade))
    S = 12
    # front elevation: first block along +z (from the plaza), lit slightly by depth
    im = Image.new("RGB", (W * S, H * S), (135, 190, 235)); px = im.load()
    for x in range(W):
        for y in range(H):
            for z in range(D):
                k = grid.get((x, y, z))
                if k and k not in ("AIR",):
                    for i in range(S):
                        for j in range(S):
                            px[x * S + i, (H - 1 - y) * S + j] = c(k, max(0.55, 1 - z * 0.008))
                    break
    im.save(pd + "/front.png")
    # side elevation from +x
    im = Image.new("RGB", (D * S, H * S), (135, 190, 235)); px = im.load()
    for z in range(D):
        for y in range(H):
            for x in range(W - 1, -1, -1):
                k = grid.get((x, y, z))
                if k and k not in ("AIR",):
                    for i in range(S):
                        for j in range(S):
                            px[z * S + i, (H - 1 - y) * S + j] = c(k)
                    break
    im.save(pd + "/side.png")
    # plans at several heights
    for yy in (2, 3, 6, 10):
        im = Image.new("RGB", (W * S, D * S), (20, 20, 24)); px = im.load()
        for x in range(W):
            for z in range(D):
                k = grid.get((x, yy, z)) or grid.get((x, 1, z))
                col = c(k) if k else (20, 20, 24)
                if not grid.get((x, yy, z)):
                    col = tuple(int(v * 0.5) for v in col)
                for i in range(S):
                    for j in range(S):
                        px[x * S + i, z * S + j] = col
        im.save(pd + f"/plan_y{yy}.png")
    # cross section through x=19
    im = Image.new("RGB", (D * S, H * S), (135, 190, 235)); px = im.load()
    for z in range(D):
        for y in range(H):
            k = grid.get((CX, y, z))
            if k and k != "AIR":
                for i in range(S):
                    for j in range(S):
                        px[z * S + i, (H - 1 - y) * S + j] = c(k)
    im.save(pd + "/section_x19.png")
