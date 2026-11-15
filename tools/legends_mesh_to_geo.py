#!/usr/bin/env python3
"""Converts a Legends suit model (createMesh in a javap -c -constants dump) into a .geo.json
in the format read by com.flash.client.geo.GeoModel (vanilla ModelPart space: y down, feet at 24).

usage: legends_mesh_to_geo.py <javap dump> <identifier> <out.geo.json> [texW texH extraInflate]
"""
import json
import math
import re
import sys


class Builder:
    def __init__(self):
        self.cubes = []
        self.u = 0
        self.v = 0
        self.mirror = False


class Deform:
    def __init__(self, g):
        self.g = g


class Pose:
    def __init__(self, x, y, z, rx=0.0, ry=0.0, rz=0.0):
        self.o = (x, y, z)
        self.r = (rx, ry, rz)


class Part:
    def __init__(self, name, parent):
        self.name = name
        self.parent = parent
        self.children = {}
        self.cubes = []
        self.pose = Pose(0, 0, 0)


def num(tok):
    tok = tok.rstrip('fFdDlL')
    return float(tok)


def run(lines):
    root = Part('root', None)
    stack = []
    locals_ = {}
    for raw in lines:
        m = re.match(r'\s*\d+: (\w+)\s*(.*)$', raw)
        if not m:
            continue
        op, rest = m.group(1), m.group(2)
        comment = rest.split('//', 1)[1].strip() if '//' in rest else ''
        arg = rest.split('//', 1)[0].strip()
        if op.startswith('aload'):
            idx = int(op.split('_')[1]) if '_' in op else int(arg)
            stack.append(locals_.get(idx, Deform(0.0) if idx == 0 else None))
        elif op.startswith('astore'):
            idx = int(op.split('_')[1]) if '_' in op else int(arg)
            locals_[idx] = stack.pop()
        elif op.startswith('iload'):
            stack.append(0)
        elif op in ('fconst_0', 'fconst_1', 'fconst_2'):
            stack.append(float(op[-1]))
        elif op.startswith('iconst_'):
            s = op[7:]
            stack.append(-1 if s == 'm1' else int(s))
        elif op in ('bipush', 'sipush'):
            stack.append(int(arg))
        elif op in ('ldc', 'ldc_w'):
            mm = re.match(r'(float|int|String)\s+(.*)$', comment)
            if mm.group(1) == 'String':
                stack.append(mm.group(2))
            else:
                stack.append(num(mm.group(2)))
        elif op == 'fneg':
            stack.append(-stack.pop())
        elif op == 'new':
            stack.append('NEW')
        elif op == 'dup':
            stack.append(stack[-1])
        elif op == 'pop':
            stack.pop()
        elif op == 'getstatic':
            if 'CubeDeformation' in comment:
                stack.append(Deform(0.0))
            else:
                raise SystemExit('getstatic ' + comment)
        elif op in ('invokestatic', 'invokevirtual', 'invokespecial'):
            meth = comment.split('Method ', 1)[1]
            if 'CubeDeformation."<init>":(F)V' in meth:
                g = stack.pop()
                stack.pop()
                stack.pop()
                stack.append(Deform(g))
            elif 'HumanoidModel.m_170681_' in meth:
                stack.pop()
                stack.pop()
                stack.append('MESH')
            elif 'MeshDefinition.m_171576_' in meth:
                stack.pop()
                stack.append(root)
            elif 'CubeListBuilder.m_171558_' in meth:
                stack.append(Builder())
            elif 'CubeListBuilder.m_171514_' in meth:  # texOffs
                v = stack.pop()
                u = stack.pop()
                b = stack[-1]
                b.u, b.v = u, v
            elif 'CubeListBuilder.m_171480_' in meth:  # mirror()
                stack[-1].mirror = True
            elif 'CubeListBuilder.m_171555_' in meth:  # mirror(boolean)
                f = stack.pop()
                stack[-1].mirror = bool(f)
            elif 'CubeListBuilder.m_171488_' in meth:  # addBox(x,y,z,w,h,d,def)
                d = stack.pop()
                args = [stack.pop() for _ in range(6)][::-1]
                b = stack[-1]
                b.cubes.append((args, d.g, b.u, b.v, b.mirror))
            elif 'PartPose.m_171419_' in meth:
                z = stack.pop(); y = stack.pop(); x = stack.pop()
                stack.append(Pose(x, y, z))
            elif 'PartPose.m_171423_' in meth:
                rz = stack.pop(); ry = stack.pop(); rx = stack.pop()
                z = stack.pop(); y = stack.pop(); x = stack.pop()
                stack.append(Pose(x, y, z, rx, ry, rz))
            elif 'PartDefinition.m_171599_' in meth:
                pose = stack.pop()
                b = stack.pop()
                name = stack.pop()
                parent = stack.pop()
                p = Part(name, parent)
                p.cubes = b.cubes
                p.pose = pose
                parent.children[name] = p
                stack.append(p)
            else:
                raise SystemExit('unknown method ' + meth)
        elif op == 'areturn':
            break
        else:
            raise SystemExit('unknown op ' + op + ' ' + rest)
    return root


def to_geo(root, ident, tex_w, tex_h, extra):
    bones = []
    used = set()

    def walk(part, parent_name, ox, oy, oz):
        px, py, pz = ox + part.pose.o[0], oy + part.pose.o[1], oz + part.pose.o[2]
        name = part.name
        if name in used:
            name = (parent_name or 'p') + '_' + name
        used.add(name)
        bone = {'name': name, 'pivot': [round(px, 5), round(24.0 - py, 5), round(pz, 5)]}
        if parent_name:
            bone['parent'] = parent_name
        r = part.pose.r
        if any(abs(a) > 1e-7 for a in r):
            bone['rotation'] = [round(math.degrees(a), 5) for a in r]
        cubes = []
        for (x, y, z, w, h, d), g, u, v, mir in part.cubes:
            c = {'origin': [round(px + x, 5), round(24.0 - (py + y + h), 5), round(pz + z, 5)],
                 'size': [w, h, d], 'uv': [u, v]}
            infl = g + extra
            if abs(infl) > 1e-7:
                c['inflate'] = round(infl, 5)
            if mir:
                c['mirror'] = True
            cubes.append(c)
        if cubes:
            bone['cubes'] = cubes
        bones.append(bone)
        for ch in part.children.values():
            walk(ch, name, px, py, pz)

    # Legends builds the limbs at offset 0 and copies the player model's pose onto them when drawing, so the
    # limbs sit at the vanilla player pivots
    vanilla = {'head': (0, 0, 0), 'body': (0, 0, 0), 'right_arm': (-5, 2, 0), 'left_arm': (5, 2, 0),
               'right_leg': (-1.9, 12, 0), 'left_leg': (1.9, 12, 0)}
    for top, base in vanilla.items():
        if top in root.children:
            part = root.children[top]
            if all(abs(a) < 1e-7 for a in part.pose.o):
                part.pose.o = base
            walk(part, None, 0.0, 0.0, 0.0)
    return {'format_version': '1.12.0', 'minecraft:geometry': [{
        'description': {'identifier': 'geometry.' + ident, 'texture_width': tex_w, 'texture_height': tex_h,
                        'visible_bounds_width': 3, 'visible_bounds_height': 3.5,
                        'visible_bounds_offset': [0, 1.25, 0]},
        'bones': bones}]}


def main():
    dump, ident, out = sys.argv[1:4]
    tex_w = int(sys.argv[4]) if len(sys.argv) > 4 else 150
    tex_h = int(sys.argv[5]) if len(sys.argv) > 5 else 100
    extra = float(sys.argv[6]) if len(sys.argv) > 6 else 0.02
    text = open(dump).read()
    body = text.split('createMesh(', 1)[1].split('Code:', 1)[1]
    body = re.split(r'\n\s*\n', body, maxsplit=1)[0]
    root = run(body.splitlines())
    geo = to_geo(root, ident, tex_w, tex_h, extra)
    with open(out, 'w') as f:
        json.dump(geo, f, indent=1)
    n = sum(len(b.get('cubes', [])) for b in geo['minecraft:geometry'][0]['bones'])
    print(out, len(geo['minecraft:geometry'][0]['bones']), 'bones', n, 'cubes')


if __name__ == '__main__':
    main()
