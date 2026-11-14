package com.flash.client;

import com.mojang.blaze3d.vertex.BufferBuilder;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Glowing shapes drawn as additive coloured quads (QUADS, POSITION_COLOR, blended SRC_ALPHA + ONE): the drawing
 * kit of the Green Lanterns mod (its ClientRender: soft crossed planes, discs, rays of light, fresnel spheres,
 * cones, tubes), with God Wand's jagged energy and some rune work of this mod's own, for effects built of light
 * instead of particles.
 */
public final class VfxDraw {

    private VfxDraw() {
    }

    public static void vert(BufferBuilder buf, Matrix4f m, double x, double y, double z, float r, float g, float b,
                            float a) {
        buf.vertex(m, (float) x, (float) y, (float) z).color(r, g, b, Mth.clamp(a, 0.0F, 1.0F)).endVertex();
    }

    public static void vert(BufferBuilder buf, Matrix4f m, Vec3 p, float r, float g, float b, float a) {
        vert(buf, m, p.x, p.y, p.z, r, g, b, a);
    }

    /** The colour towards white. */
    public static float mix(float c, float k) {
        return c + (1.0F - c) * k;
    }

    /** A soft band of light from a to b: three crossed planes, bright on the line, clear at the edges. */
    public static void beam(BufferBuilder buf, Matrix4f m, Vec3 a, Vec3 b, float w, float r, float g, float bl,
                            float alpha) {
        Vec3 d = b.subtract(a);
        double len = d.length();
        if (len < 1.0E-4D || alpha <= 0.002F) {
            return;
        }
        Vec3 dn = d.scale(1.0D / len);
        Vec3 ref = Math.abs(dn.y) > 0.99D ? new Vec3(1.0D, 0.0D, 0.0D) : new Vec3(0.0D, 1.0D, 0.0D);
        Vec3 u = dn.cross(ref).normalize();
        Vec3 v = dn.cross(u).normalize();
        for (int k = 0; k < 3; k++) {
            double ang = Math.PI * k / 3.0D;
            Vec3 s = u.scale(Math.cos(ang)).add(v.scale(Math.sin(ang))).scale(w);
            vert(buf, m, a.subtract(s), r, g, bl, 0.0F);
            vert(buf, m, a, r, g, bl, alpha);
            vert(buf, m, b, r, g, bl, alpha);
            vert(buf, m, b.subtract(s), r, g, bl, 0.0F);
            vert(buf, m, a, r, g, bl, alpha);
            vert(buf, m, a.add(s), r, g, bl, 0.0F);
            vert(buf, m, b.add(s), r, g, bl, 0.0F);
            vert(buf, m, b, r, g, bl, alpha);
        }
    }

    /** A band of light in three layers (glow, colour, white-hot core), as the Lanterns' beams. */
    public static void energy(BufferBuilder buf, Matrix4f m, Vec3 a, Vec3 b, float w, float r, float g, float bl,
                              float alpha) {
        beam(buf, m, a, b, w * 2.4F, r, g, bl, 0.2F * alpha);
        beam(buf, m, a, b, w, mix(r, 0.3F), mix(g, 0.3F), mix(bl, 0.3F), 0.55F * alpha);
        beam(buf, m, a, b, w * 0.32F, mix(r, 0.85F), mix(g, 0.85F), mix(bl, 0.85F), alpha);
    }

    /** A path of points drawn as energy. */
    public static void path(BufferBuilder buf, Matrix4f m, Vec3[] pts, float w, float r, float g, float b,
                            float alpha) {
        for (int i = 0; i + 1 < pts.length; i++) {
            energy(buf, m, pts[i], pts[i + 1], w, r, g, b, alpha);
        }
    }

    /**
     * A jagged bolt of raw energy from a to b (midpoint displacement, as God Wand's arcs): the same seed, the same
     * shape.
     */
    public static Vec3[] jagged(Vec3 a, Vec3 b, long seed, double jag, int depth) {
        int n = 1 << depth;
        Vec3[] pts = new Vec3[n + 1];
        pts[0] = a;
        pts[n] = b;
        java.util.Random rnd = new java.util.Random(seed);
        Vec3 d = b.subtract(a);
        double len = d.length();
        Vec3 dn = len < 1.0E-6D ? new Vec3(0.0D, 1.0D, 0.0D) : d.scale(1.0D / len);
        Vec3 ref = Math.abs(dn.y) > 0.95D ? new Vec3(1.0D, 0.0D, 0.0D) : new Vec3(0.0D, 1.0D, 0.0D);
        Vec3 u = dn.cross(ref).normalize();
        Vec3 v = dn.cross(u).normalize();
        double off = Math.min(len * 0.12D, 2.5D) * jag;
        for (int step = n; step > 1; step /= 2) {
            for (int i = 0; i < n; i += step) {
                Vec3 mid = pts[i].add(pts[i + step]).scale(0.5D);
                double a1 = rnd.nextDouble() * Math.PI * 2.0D;
                double s = (rnd.nextDouble() - 0.3D) * off;
                pts[i + step / 2] = mid.add(u.scale(Math.cos(a1) * s)).add(v.scale(Math.sin(a1) * s));
            }
            off *= 0.55D;
        }
        return pts;
    }

    /** Points of a helix wound around the line a-b (turns, radius, turning phase). */
    public static Vec3[] helix(Vec3 a, Vec3 b, double radius, double turns, double phase, int n) {
        Vec3 d = b.subtract(a);
        double len = d.length();
        Vec3 dn = len < 1.0E-6D ? new Vec3(0.0D, 1.0D, 0.0D) : d.scale(1.0D / len);
        Vec3 ref = Math.abs(dn.y) > 0.95D ? new Vec3(1.0D, 0.0D, 0.0D) : new Vec3(0.0D, 1.0D, 0.0D);
        Vec3 u = dn.cross(ref).normalize();
        Vec3 v = dn.cross(u).normalize();
        Vec3[] pts = new Vec3[n + 1];
        for (int i = 0; i <= n; i++) {
            double k = (double) i / n;
            double ang = phase + k * turns * Math.PI * 2.0D;
            // pinched at both ends
            double rr = radius * Math.sin(Math.PI * Math.min(1.0D, k * 1.15D)) * (k > 0.87D ? (1.0D - k) / 0.13D : 1.0D);
            pts[i] = a.add(d.scale(k)).add(u.scale(Math.cos(ang) * rr)).add(v.scale(Math.sin(ang) * rr));
        }
        return pts;
    }

    /** A square tube of light (shaded sides). */
    public static void tube(BufferBuilder buf, Matrix4f m, Vec3 a, Vec3 b, double t, float cr, float cg, float cb,
                            float alpha) {
        Vec3 e = b.subtract(a);
        double len = e.length();
        if (len < 1.0E-6D) {
            return;
        }
        Vec3 en = e.scale(1.0D / len);
        Vec3 ref = Math.abs(en.y) > 0.95D ? new Vec3(1.0D, 0.0D, 0.0D) : new Vec3(0.0D, 1.0D, 0.0D);
        Vec3 u1 = en.cross(ref).normalize();
        Vec3 u2 = en.cross(u1).normalize();
        Vec3 p1 = u1.scale(t);
        Vec3 p2 = u2.scale(t);
        Vec3[] o = {p1.add(p2), p1.subtract(p2), p1.scale(-1.0D).subtract(p2), p1.scale(-1.0D).add(p2)};
        float[] shade = {1.0F, 0.65F, 0.4F, 0.75F};
        for (int k = 0; k < 4; k++) {
            Vec3 o0 = o[k];
            Vec3 o1 = o[(k + 1) % 4];
            float s = shade[k];
            vert(buf, m, a.add(o0), cr * s, cg * s, cb * s, alpha);
            vert(buf, m, a.add(o1), cr * s, cg * s, cb * s, alpha);
            vert(buf, m, b.add(o1), cr * s, cg * s, cb * s, alpha);
            vert(buf, m, b.add(o0), cr * s, cg * s, cb * s, alpha);
        }
    }

    /** A disc in the plane e1-e2: rim colour outside, a brighter inside. */
    public static void disc(BufferBuilder buf, Matrix4f m, Vec3 c, Vec3 e1, Vec3 e2, double radius, float rr, float rg,
                            float rb, float ir, float ig, float ib, float alpha) {
        if (alpha <= 0.002F) {
            return;
        }
        int seg = 18;
        double inner = radius * 0.72D;
        for (int j = 0; j < seg; j++) {
            double a0 = Math.PI * 2 * j / seg;
            double a1 = Math.PI * 2 * (j + 1) / seg;
            Vec3 q0 = c.add(e1.scale(Math.cos(a0) * inner)).add(e2.scale(Math.sin(a0) * inner));
            Vec3 q1 = c.add(e1.scale(Math.cos(a1) * inner)).add(e2.scale(Math.sin(a1) * inner));
            Vec3 p0 = c.add(e1.scale(Math.cos(a0) * radius)).add(e2.scale(Math.sin(a0) * radius));
            Vec3 p1 = c.add(e1.scale(Math.cos(a1) * radius)).add(e2.scale(Math.sin(a1) * radius));
            vert(buf, m, c, mix(ir, 0.3F), mix(ig, 0.3F), mix(ib, 0.3F), alpha);
            vert(buf, m, q0, ir, ig, ib, alpha * 0.9F);
            vert(buf, m, q1, ir, ig, ib, alpha * 0.9F);
            vert(buf, m, c, mix(ir, 0.3F), mix(ig, 0.3F), mix(ib, 0.3F), alpha);
            vert(buf, m, q0, rr, rg, rb, alpha);
            vert(buf, m, p0, rr, rg, rb, 0.0F);
            vert(buf, m, p1, rr, rg, rb, 0.0F);
            vert(buf, m, q1, rr, rg, rb, alpha);
        }
    }

    /** A soft round glow (bright centre, clear rim) in the plane e1-e2 (the camera's for a billboard). */
    public static void glow(BufferBuilder buf, Matrix4f m, Vec3 c, Vec3 e1, Vec3 e2, double radius, float r, float g,
                            float b, float a) {
        if (a <= 0.002F || radius <= 0.0D) {
            return;
        }
        int seg = 18;
        for (int j = 0; j < seg; j++) {
            double a0 = Math.PI * 2.0D * j / seg;
            double a1 = Math.PI * 2.0D * (j + 1) / seg;
            Vec3 p0 = c.add(e1.scale(Math.cos(a0) * radius)).add(e2.scale(Math.sin(a0) * radius));
            Vec3 p1 = c.add(e1.scale(Math.cos(a1) * radius)).add(e2.scale(Math.sin(a1) * radius));
            vert(buf, m, c, r, g, b, a);
            vert(buf, m, p0, r, g, b, 0.0F);
            vert(buf, m, p1, r, g, b, 0.0F);
            vert(buf, m, c, r, g, b, a);
        }
    }

    /** Rays of light around a point (the Lanterns' explosaoLuz), turned by rot. */
    public static void burst(BufferBuilder buf, Matrix4f m, Vec3 c, Vec3 left, Vec3 up, float radius, float cr,
                             float cg, float cb, float alpha, float rot) {
        if (alpha <= 0.002F) {
            return;
        }
        int rays = 12;
        for (int k = 0; k < rays; k++) {
            double a = rot + Math.PI * 2 * k / rays;
            Vec3 d = left.scale(Math.cos(a)).add(up.scale(Math.sin(a)));
            Vec3 pe = left.scale(-Math.sin(a)).add(up.scale(Math.cos(a)));
            double len = radius * (k % 2 == 0 ? 1.0D : 0.62D);
            double wide = radius * 0.07D;
            Vec3 tip = c.add(d.scale(len));
            vert(buf, m, c.add(pe.scale(wide)), mix(cr, 0.7F), mix(cg, 0.7F), mix(cb, 0.45F), alpha);
            vert(buf, m, c.subtract(pe.scale(wide)), mix(cr, 0.7F), mix(cg, 0.7F), mix(cb, 0.45F), alpha);
            vert(buf, m, tip, cr, cg, cb, 0.0F);
            vert(buf, m, tip, cr, cg, cb, 0.0F);
        }
        glow(buf, m, c, left, up, radius * 0.28D, mix(cr, 0.85F), mix(cg, 0.9F), mix(cb, 0.6F), alpha);
    }

    /** A sphere of energy, its rim glowing (fresnel), its surface rippling (the Lanterns' bubble burst). */
    public static void sphere(BufferBuilder buf, Matrix4f m, Vec3 c, double radius, Vec3 cam, float cr, float cg,
                              float cb, float alpha, float t, double rimExp) {
        if (alpha <= 0.002F || radius <= 0.01D) {
            return;
        }
        int lat = 12;
        int lon = 20;
        Vec3[][] p = new Vec3[lat + 1][lon + 1];
        float[][] al = new float[lat + 1][lon + 1];
        for (int i = 0; i <= lat; i++) {
            double phi = Math.PI * i / lat;
            for (int j = 0; j <= lon; j++) {
                double th = Math.PI * 2 * j / lon;
                double wave = 1.0D + 0.05D * Math.sin(3.0D * th + t * 0.35D) * Math.sin(2.0D * phi + t * 0.28D);
                Vec3 n = new Vec3(Math.sin(phi) * Math.cos(th), Math.cos(phi), Math.sin(phi) * Math.sin(th));
                Vec3 pos = c.add(n.scale(radius * wave));
                Vec3 vc = cam.subtract(pos);
                double len = vc.length();
                double fres = len < 1.0E-6D ? 0.0D : 1.0D - Math.abs(n.dot(vc.scale(1.0D / len)));
                p[i][j] = pos;
                al[i][j] = (float) (alpha * (0.1D + 0.9D * Math.pow(fres, rimExp)));
            }
        }
        for (int i = 0; i < lat; i++) {
            for (int j = 0; j < lon; j++) {
                float shade = 0.85F + 0.15F * (float) Math.sin(j * 0.9D + t * 0.2D);
                vert(buf, m, p[i][j], cr * shade, cg, cb, al[i][j]);
                vert(buf, m, p[i][j + 1], cr * shade, cg, cb, al[i][j + 1]);
                vert(buf, m, p[i + 1][j + 1], cr * shade, cg, cb, al[i + 1][j + 1]);
                vert(buf, m, p[i + 1][j], cr * shade, cg, cb, al[i + 1][j]);
            }
        }
    }

    /** A band ring in any plane (normal n), turned by rot. */
    public static void ring(BufferBuilder buf, Matrix4f m, Vec3 c, Vec3 n, double radius, double w, float rot, float r,
                            float g, float b, float a) {
        if (a <= 0.002F || radius <= 0.0D) {
            return;
        }
        Vec3 nn = n.normalize();
        Vec3 ref = Math.abs(nn.y) > 0.95D ? new Vec3(1.0D, 0.0D, 0.0D) : new Vec3(0.0D, 1.0D, 0.0D);
        Vec3 u = nn.cross(ref).normalize();
        Vec3 v = nn.cross(u).normalize();
        int seg = Math.max(24, Math.min(96, (int) (radius * 10.0D)));
        double ri = Math.max(0.0D, radius - w);
        double ro = radius + w;
        for (int j = 0; j < seg; j++) {
            double a0 = rot + Math.PI * 2.0D * j / seg;
            double a1 = rot + Math.PI * 2.0D * (j + 1) / seg;
            Vec3 d0 = u.scale(Math.cos(a0)).add(v.scale(Math.sin(a0)));
            Vec3 d1 = u.scale(Math.cos(a1)).add(v.scale(Math.sin(a1)));
            Vec3 mid0 = c.add(d0.scale(radius));
            Vec3 mid1 = c.add(d1.scale(radius));
            vert(buf, m, c.add(d0.scale(ri)), r, g, b, 0.0F);
            vert(buf, m, c.add(d1.scale(ri)), r, g, b, 0.0F);
            vert(buf, m, mid1, r, g, b, a);
            vert(buf, m, mid0, r, g, b, a);
            vert(buf, m, mid0, r, g, b, a);
            vert(buf, m, mid1, r, g, b, a);
            vert(buf, m, c.add(d1.scale(ro)), r, g, b, 0.0F);
            vert(buf, m, c.add(d0.scale(ro)), r, g, b, 0.0F);
        }
    }

    /** A wall of light rising from a ring on the ground (radius, height), fading upwards. */
    public static void wall(BufferBuilder buf, Matrix4f m, Vec3 c, double radius, double height, float r, float g,
                            float b, float a) {
        if (a <= 0.002F || radius <= 0.0D) {
            return;
        }
        int seg = Math.max(24, Math.min(96, (int) (radius * 8.0D)));
        for (int j = 0; j < seg; j++) {
            double a0 = Math.PI * 2.0D * j / seg;
            double a1 = Math.PI * 2.0D * (j + 1) / seg;
            double x0 = c.x + Math.cos(a0) * radius;
            double z0 = c.z + Math.sin(a0) * radius;
            double x1 = c.x + Math.cos(a1) * radius;
            double z1 = c.z + Math.sin(a1) * radius;
            vert(buf, m, x0, c.y, z0, r, g, b, a);
            vert(buf, m, x1, c.y, z1, r, g, b, a);
            vert(buf, m, x1, c.y + height, z1, r, g, b, 0.0F);
            vert(buf, m, x0, c.y + height, z0, r, g, b, 0.0F);
        }
    }

    /** A vertical band of light standing at c, turned to the camera. */
    public static void pillar(BufferBuilder buf, Matrix4f m, Vec3 c, double y0, double y1, Vec3 cam, float w, float r,
                              float g, float b, float a) {
        if (a <= 0.002F) {
            return;
        }
        double dx = cam.x - c.x;
        double dz = cam.z - c.z;
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1.0E-4D) {
            dx = 1.0D;
            dz = 0.0D;
            len = 1.0D;
        }
        double sx = -dz / len * w;
        double sz = dx / len * w;
        vert(buf, m, c.x - sx, y0, c.z - sz, r, g, b, 0.0F);
        vert(buf, m, c.x, y0, c.z, r, g, b, a);
        vert(buf, m, c.x, y1, c.z, r, g, b, a * 0.6F);
        vert(buf, m, c.x - sx, y1, c.z - sz, r, g, b, 0.0F);
        vert(buf, m, c.x, y0, c.z, r, g, b, a);
        vert(buf, m, c.x + sx, y0, c.z + sz, r, g, b, 0.0F);
        vert(buf, m, c.x + sx, y1, c.z + sz, r, g, b, 0.0F);
        vert(buf, m, c.x, y1, c.z, r, g, b, a * 0.6F);
    }

    /** A flat glowing bar on a horizontal plane. */
    public static void bar(BufferBuilder buf, Matrix4f m, double x0, double y, double z0, double x1, double z1,
                           double w, float r, float g, float b, float a) {
        double dx = x1 - x0;
        double dz = z1 - z0;
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1.0E-5D || a <= 0.002F) {
            return;
        }
        double nx = -dz / len * w;
        double nz = dx / len * w;
        vert(buf, m, x0 - nx, y, z0 - nz, r, g, b, a);
        vert(buf, m, x1 - nx, y, z1 - nz, r, g, b, a);
        vert(buf, m, x1 + nx, y, z1 + nz, r, g, b, a);
        vert(buf, m, x0 + nx, y, z0 + nz, r, g, b, a);
    }

    /** A hex circle on the ground: two rings turning against each other, a hexagram and six runes. */
    public static void hexCircle(BufferBuilder buf, Matrix4f m, Vec3 c, double radius, float t, float r, float g,
                                 float b, float a) {
        if (a <= 0.002F) {
            return;
        }
        Vec3 up = new Vec3(0.0D, 1.0D, 0.0D);
        double y = c.y + 0.04D;
        Vec3 at = new Vec3(c.x, y, c.z);
        ring(buf, m, at, up, radius, 0.08D, t * 0.05F, r, g, b, 0.85F * a);
        ring(buf, m, at, up, radius * 0.76D, 0.055D, -t * 0.08F, mix(r, 0.3F), mix(g, 0.3F), mix(b, 0.3F), 0.75F * a);
        float rot = t * 0.05F;
        for (int tri = 0; tri < 2; tri++) {
            for (int k = 0; k < 3; k++) {
                double a0 = rot + tri * Math.PI / 3.0D + Math.PI * 2.0D * k / 3.0D;
                double a1 = rot + tri * Math.PI / 3.0D + Math.PI * 2.0D * (k + 1) / 3.0D;
                double rr = radius * 0.74D;
                bar(buf, m, c.x + Math.cos(a0) * rr, y, c.z + Math.sin(a0) * rr, c.x + Math.cos(a1) * rr,
                        c.z + Math.sin(a1) * rr, 0.045D, r, g, b, 0.8F * a);
            }
        }
        for (int k = 0; k < 6; k++) {
            double ang = rot + Math.PI * 2.0D * k / 6.0D + Math.PI / 6.0D;
            rune(buf, m, c.x + Math.cos(ang) * radius * 0.88D, y + 0.01D, c.z + Math.sin(ang) * radius * 0.88D,
                    radius * 0.075D, ang, k, mix(r, 0.4F), mix(g, 0.4F), mix(b, 0.4F), 0.9F * a);
        }
    }

    /** A little rune of a few strokes (six shapes). */
    public static void rune(BufferBuilder buf, Matrix4f m, double x, double y, double z, double s, double a, int kind,
                            float r, float g, float b, float al) {
        double ca = Math.cos(a);
        double sa = Math.sin(a);
        double[][] strokes = switch (kind % 6) {
            case 0 -> new double[][] {{0, -1, 0, 1}, {-0.7, -0.3, 0.7, 0.3}, {-0.7, 0.3, 0.7, -0.3}};
            case 1 -> new double[][] {{-0.6, -1, -0.6, 1}, {-0.6, 0, 0.6, 1}, {-0.6, 0, 0.6, -1}};
            case 2 -> new double[][] {{0, -1, -0.7, 0.6}, {0, -1, 0.7, 0.6}, {-0.7, 0.6, 0.7, 0.6}};
            case 3 -> new double[][] {{-0.7, -1, 0.7, 1}, {0.7, -1, -0.7, 1}, {0, -1, 0, 1}};
            case 4 -> new double[][] {{-0.6, -1, 0.6, -1}, {0.6, -1, -0.6, 1}, {-0.6, 1, 0.6, 1}};
            default -> new double[][] {{0, -1, 0, 1}, {0, -0.2, 0.7, -0.9}, {0, 0.3, -0.7, -0.4}};
        };
        for (double[] st : strokes) {
            double x0 = x + (st[0] * ca - st[1] * sa) * s;
            double z0 = z + (st[0] * sa + st[1] * ca) * s;
            double x1 = x + (st[2] * ca - st[3] * sa) * s;
            double z1 = z + (st[2] * sa + st[3] * ca) * s;
            bar(buf, m, x0, y, z0, x1, z1, s * 0.15D, r, g, b, al);
        }
    }
}
