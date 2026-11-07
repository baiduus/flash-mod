package com.flash.client;

import com.flash.FlashMod;
import com.flash.network.SupermanFxPacket;
import com.flash.superman.SupermanPowers;
import com.flash.superman.SupermanState;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * The effect textures of the Superman pack drawn in the world (additive glow):
 * sonic rings, ground shockwaves, the solar blast, the sun that grows around him
 * during the Ultimate and its light pillars, heat vision glows at the eyes and on
 * impact (laser clash when two beams meet), the breath cones and the trail of a
 * fast flight.
 */
@Mod.EventBusSubscriber(modid = FlashMod.MODID, value = Dist.CLIENT)
public final class SupermanFx {

    private static final ResourceLocation RING = tex("ring");
    private static final ResourceLocation GLOW = tex("glow");
    private static final ResourceLocation SUN = tex("sun");
    private static final ResourceLocation SOLAR = tex("solar");
    private static final ResourceLocation TRAIL = tex("trail");
    private static final ResourceLocation FLOW = tex("breath_flow");
    private static final ResourceLocation CLASH = tex("clash");

    private static ResourceLocation tex(String name) {
        return new ResourceLocation(FlashMod.MODID, "textures/fx/superman/" + name + ".png");
    }

    private SupermanFx() {
    }

    // ------------------------------------------------------------------
    // One-shot effects
    // ------------------------------------------------------------------

    private static final class Fx {
        final int kind;
        final Vec3 pos;
        final Vec3 dir;
        final float scale;
        final long start;

        Fx(int kind, Vec3 pos, Vec3 dir, float scale, long start) {
            this.kind = kind;
            this.pos = pos;
            this.dir = dir;
            this.scale = scale;
            this.start = start;
        }

        int life() {
            return kind == SupermanFxPacket.SOLAR_BLAST ? 26 : kind == SupermanFxPacket.SHOCKWAVE ? 16 : 11;
        }
    }

    private static final List<Fx> EFFECTS = new ArrayList<>();

    public static void spawn(int kind, double x, double y, double z, float dx, float dy, float dz, float scale) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        Vec3 dir = new Vec3(dx, dy, dz);
        dir = dir.lengthSqr() < 1.0E-6D ? new Vec3(0, 1, 0) : dir.normalize();
        EFFECTS.add(new Fx(kind, new Vec3(x, y, z), dir, scale, mc.level.getGameTime()));
        if (EFFECTS.size() > 200) {
            EFFECTS.remove(0);
        }
    }

    // ------------------------------------------------------------------
    // Per-player state (trail history, boost and ultimate start)
    // ------------------------------------------------------------------

    private static final class Track {
        final Deque<Vec3> trail = new ArrayDeque<>();
        boolean boosting;
        long ultStart = -1L;
    }

    private static final Map<UUID, Track> TRACKS = new HashMap<>();

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.phase != TickEvent.Phase.END || mc.level == null || mc.isPaused()) {
            return;
        }
        long now = mc.level.getGameTime();
        EFFECTS.removeIf(f -> now - f.start > f.life());
        for (Player p : mc.level.players()) {
            if (!SupermanState.isSuperman(p)) {
                TRACKS.remove(p.getUUID());
                continue;
            }
            Track t = TRACKS.computeIfAbsent(p.getUUID(), id -> new Track());
            Vec3 v = p.position().subtract(p.xo, p.yo, p.zo);
            boolean flying = p == mc.player ? p.getAbilities().flying
                    : SupermanState.flag(p, SupermanPowers.F_FLIGHT) && !p.onGround();
            double speed = v.length();
            boolean fast = flying && speed > 0.9D;
            if (fast && !t.boosting) {
                // breaking into high speed: a sonic ring left behind
                spawn(SupermanFxPacket.RING, p.getX(), p.getY() + p.getBbHeight() * 0.5D, p.getZ(),
                        (float) v.x, (float) v.y, (float) v.z, 2.4F);
                if (p == mc.player) {
                    p.playSound(com.flash.registry.ModSounds.PUNCH_SONIC.get(), 0.6F, 1.4F);
                }
            }
            t.boosting = fast;
            if (fast) {
                t.trail.addFirst(p.position().add(0.0D, p.getBbHeight() * 0.45D, 0.0D));
            } else if (!t.trail.isEmpty()) {
                t.trail.removeLast();
            }
            while (t.trail.size() > 14) {
                t.trail.removeLast();
            }
            boolean ult = SupermanState.flag(p, SupermanPowers.F_ULTIMATE);
            if (ult && t.ultStart < 0L) {
                t.ultStart = now;
            } else if (!ult) {
                t.ultStart = -1L;
            }
        }
    }

    // ------------------------------------------------------------------
    // Rendering
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            return;
        }
        float partial = event.getPartialTick();
        float time = level.getGameTime() + partial;
        Camera camera = event.getCamera();
        Vec3 cam = camera.getPosition();
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f m = pose.last().pose();
        Vector3f left = new Vector3f(camera.getLeftVector());
        Vector3f up = new Vector3f(camera.getUpVector());
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();

        // one-shot effects
        for (Fx f : EFFECTS) {
            float age = (time - f.start) / f.life();
            if (age < 0.0F || age > 1.0F) {
                continue;
            }
            float fade = 1.0F - age;
            switch (f.kind) {
                case SupermanFxPacket.RING -> quadFacing(buffers.getBuffer(RenderType.eyes(RING)), m, f.pos, f.dir,
                        f.scale * (0.4F + 1.4F * ease(age)), 0.85F, 0.92F, 1.0F, fade * 0.9F);
                case SupermanFxPacket.SHOCKWAVE -> quadFacing(buffers.getBuffer(RenderType.eyes(RING)), m, f.pos,
                        new Vec3(0, 1, 0), f.scale * (0.2F + 1.8F * ease(age)), 1.0F, 0.93F, 0.82F, fade * 0.8F);
                default -> {
                    quadFacing(buffers.getBuffer(RenderType.eyes(RING)), m, f.pos, new Vec3(0, 1, 0),
                            f.scale * (0.1F + 2.0F * ease(age)), 1.0F, 0.78F, 0.25F, fade);
                    billboard(buffers.getBuffer(RenderType.eyes(GLOW)), m, f.pos, left, up,
                            f.scale * 0.9F * (0.3F + age), 1.0F, 0.85F, 0.45F, fade, 0.0F, 0.0F, 1.0F, 1.0F);
                }
            }
        }

        for (Player p : level.players()) {
            if (!SupermanState.isSuperman(p) || p.isInvisible()) {
                continue;
            }
            Track t = TRACKS.get(p.getUUID());
            Vec3 at = p.getPosition(partial);
            // ultimate: the sun growing around him and pillars of sunlight
            if (t != null && t.ultStart >= 0L) {
                float k = Math.min(1.0F, (time - t.ultStart) / 100.0F);
                Vec3 c = at.add(0.0D, 1.0D, 0.0D);
                sphere(buffers.getBuffer(RenderType.eyes(SUN)), m, c, 0.6F + 1.8F * k, time * 0.004F,
                        1.0F, 0.62F, 0.18F, 0.35F + 0.35F * k);
                billboard(buffers.getBuffer(RenderType.eyes(GLOW)), m, c, left, up, 2.5F + 4.0F * k,
                        1.0F, 0.75F, 0.3F, 0.5F + 0.4F * k, 0.0F, 0.0F, 1.0F, 1.0F);
                VertexConsumer beam = buffers.getBuffer(RenderType.eyes(SOLAR));
                for (int i = 0; i < 4; i++) {
                    double a = i * Math.PI / 2.0D + time * 0.02D;
                    double r = 3.0D * (1.0D - k) + 0.3D;
                    Vec3 base = at.add(Math.cos(a) * r, 0.0D, Math.sin(a) * r);
                    pillar(beam, m, cam, base, 60.0F, 0.5F + 0.6F * k, 1.0F, 0.85F, 0.4F,
                            0.4F + 0.4F * k, 0.0F, 0.25F);
                }
            }
            // heat vision: glowing eyes and the burning point
            if (SupermanState.flag(p, SupermanPowers.F_HEAT)) {
                boolean laser = SupermanState.flag(p, SupermanPowers.F_LASER);
                Vec3 eye = p.getEyePosition(partial);
                Vec3 look = p.getViewVector(partial);
                Vec3 side = look.cross(new Vec3(0, 1, 0));
                side = side.lengthSqr() < 1.0E-6D ? new Vec3(1, 0, 0) : side.normalize();
                VertexConsumer glow = buffers.getBuffer(RenderType.eyes(GLOW));
                boolean firstPerson = p == mc.player && mc.options.getCameraType().isFirstPerson();
                if (!firstPerson) {
                    for (int s = -1; s <= 1; s += 2) {
                        billboard(glow, m, eye.add(look.scale(0.26D)).add(side.scale(s * 0.065D)).add(0, 0.02D, 0),
                                left, up, laser ? 0.35F : 0.22F, 1.0F, 0.25F, 0.1F, 1.0F, 0.0F, 0.0F, 1.0F, 1.0F);
                    }
                }
                Vec3 hit = SupermanClient.beamEnd(p.getUUID());
                if (hit != null) {
                    float pulse = 0.8F + 0.2F * (float) Math.sin(time * 1.7F);
                    billboard(glow, m, hit, left, up, (laser ? 1.6F : 1.0F) * pulse, 1.0F, 0.35F, 0.08F, 1.0F,
                            0.0F, 0.0F, 1.0F, 1.0F);
                    // laser clash: two beams meeting
                    for (Player o : level.players()) {
                        if (o == p || !SupermanState.flag(o, SupermanPowers.F_HEAT)) {
                            continue;
                        }
                        Vec3 other = SupermanClient.beamEnd(o.getUUID());
                        if (other != null && other.distanceToSqr(hit) < 2.5D && p.getId() < o.getId()) {
                            clash(buffers, m, hit.add(other).scale(0.5D), left, up, time);
                        }
                    }
                }
            }
            // breath cones
            if (SupermanState.flag(p, SupermanPowers.F_FREEZE)) {
                cone(buffers.getBuffer(RenderType.eyes(FLOW)), m, p, partial, time, 0.55F, 0.85F, 1.0F, 0.5F);
            } else if (SupermanState.flag(p, SupermanPowers.F_BREATH)) {
                cone(buffers.getBuffer(RenderType.eyes(FLOW)), m, p, partial, time, 0.9F, 0.9F, 0.9F, 0.4F);
            }
            // high speed trail
            if (t != null && t.trail.size() > 1) {
                trail(buffers.getBuffer(RenderType.eyes(TRAIL)), m, cam, p, partial, t);
            }
        }
        pose.popPose();
        buffers.endBatch(RenderType.eyes(RING));
        buffers.endBatch(RenderType.eyes(GLOW));
        buffers.endBatch(RenderType.eyes(SUN));
        buffers.endBatch(RenderType.eyes(SOLAR));
        buffers.endBatch(RenderType.eyes(FLOW));
        buffers.endBatch(RenderType.eyes(TRAIL));
        buffers.endBatch(RenderType.eyes(CLASH));
    }

    private static float ease(float x) {
        return 1.0F - (1.0F - x) * (1.0F - x);
    }

    // ------------------------------------------------------------------
    // Shapes
    // ------------------------------------------------------------------

    /** Collects quad corners and emits them with both windings (the glow render type culls back faces). */
    private static final class Quad {
        private final double[][] v = new double[4][9];
        private int n;

        void add(double x, double y, double z, float r, float g, float b, float a, float u, float vv) {
            v[n++] = new double[] {x, y, z, r, g, b, a, u, vv};
        }

        void emit(VertexConsumer c, Matrix4f m) {
            for (int i = 0; i < 4; i++) {
                double[] q = v[i];
                put(c, m, q[0], q[1], q[2], (float) q[3], (float) q[4], (float) q[5], (float) q[6], (float) q[7], (float) q[8]);
            }
            for (int i = 3; i >= 0; i--) {
                double[] q = v[i];
                put(c, m, q[0], q[1], q[2], (float) q[3], (float) q[4], (float) q[5], (float) q[6], (float) q[7], (float) q[8]);
            }
            n = 0;
        }
    }

    private static void put(VertexConsumer c, Matrix4f m, double x, double y, double z, float r, float g, float b,
                            float a, float u, float v) {
        Vector4f p = m.transform(new Vector4f((float) x, (float) y, (float) z, 1.0F));
        // additive blending: the alpha is folded into the colour
        c.vertex(p.x(), p.y(), p.z(), r * a, g * a, b * a, 1.0F, u, v, OverlayTexture.NO_OVERLAY, 0xF000F0,
                0.0F, 1.0F, 0.0F);
    }

    private static void billboard(VertexConsumer c, Matrix4f m, Vec3 at, Vector3f left, Vector3f up, float size,
                                  float r, float g, float b, float a, float u0, float v0, float u1, float v1) {
        float h = size * 0.5F;
        double lx = left.x() * h, ly = left.y() * h, lz = left.z() * h;
        double ux = up.x() * h, uy = up.y() * h, uz = up.z() * h;
        put(c, m, at.x + lx - ux, at.y + ly - uy, at.z + lz - uz, r, g, b, a, u0, v1);
        put(c, m, at.x - lx - ux, at.y - ly - uy, at.z - lz - uz, r, g, b, a, u1, v1);
        put(c, m, at.x - lx + ux, at.y - ly + uy, at.z - lz + uz, r, g, b, a, u1, v0);
        put(c, m, at.x + lx + ux, at.y + ly + uy, at.z + lz + uz, r, g, b, a, u0, v0);
    }

    /** Square facing along {@code normal} (both sides drawn). */
    private static void quadFacing(VertexConsumer c, Matrix4f m, Vec3 at, Vec3 normal, float size, float r, float g,
                                   float b, float a) {
        Vec3 n = normal.normalize();
        Vec3 ref = Math.abs(n.y) > 0.9D ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 s = n.cross(ref).normalize().scale(size * 0.5D);
        Vec3 t = n.cross(s).normalize().scale(size * 0.5D);
        Vec3 p0 = at.subtract(s).subtract(t);
        Vec3 p1 = at.add(s).subtract(t);
        Vec3 p2 = at.add(s).add(t);
        Vec3 p3 = at.subtract(s).add(t);
        put(c, m, p0.x, p0.y, p0.z, r, g, b, a, 0, 1);
        put(c, m, p1.x, p1.y, p1.z, r, g, b, a, 1, 1);
        put(c, m, p2.x, p2.y, p2.z, r, g, b, a, 1, 0);
        put(c, m, p3.x, p3.y, p3.z, r, g, b, a, 0, 0);
        put(c, m, p3.x, p3.y, p3.z, r, g, b, a, 0, 0);
        put(c, m, p2.x, p2.y, p2.z, r, g, b, a, 1, 0);
        put(c, m, p1.x, p1.y, p1.z, r, g, b, a, 1, 1);
        put(c, m, p0.x, p0.y, p0.z, r, g, b, a, 0, 1);
    }

    /** Textured sphere (the sun), slowly turning. */
    private static void sphere(VertexConsumer c, Matrix4f m, Vec3 at, float radius, float spin, float r, float g,
                               float b, float a) {
        int rings = 10;
        int segs = 16;
        for (int i = 0; i < rings; i++) {
            double t0 = Math.PI * i / rings;
            double t1 = Math.PI * (i + 1) / rings;
            for (int j = 0; j < segs; j++) {
                double p0 = 2.0D * Math.PI * j / segs + spin * 6.0D;
                double p1 = 2.0D * Math.PI * (j + 1) / segs + spin * 6.0D;
                float u0 = j / (float) segs + spin;
                float u1 = (j + 1) / (float) segs + spin;
                float v0 = i / (float) rings;
                float v1 = (i + 1) / (float) rings;
                Vec3 a0 = sp(at, radius, t0, p0);
                Vec3 a1 = sp(at, radius, t0, p1);
                Vec3 b1 = sp(at, radius, t1, p1);
                Vec3 b0 = sp(at, radius, t1, p0);
                Quad q = new Quad();
                q.add(a0.x, a0.y, a0.z, r, g, b, a, u0, v0);
                q.add(b0.x, b0.y, b0.z, r, g, b, a, u0, v1);
                q.add(b1.x, b1.y, b1.z, r, g, b, a, u1, v1);
                q.add(a1.x, a1.y, a1.z, r, g, b, a, u1, v0);
                q.emit(c, m);
            }
        }
    }

    private static Vec3 sp(Vec3 c, double r, double theta, double phi) {
        return new Vec3(c.x + r * Math.sin(theta) * Math.cos(phi), c.y + r * Math.cos(theta),
                c.z + r * Math.sin(theta) * Math.sin(phi));
    }

    /** Vertical beam turned toward the camera around its own axis. */
    private static void pillar(VertexConsumer c, Matrix4f m, Vec3 cam, Vec3 base, float height, float width, float r,
                               float g, float b, float a, float u0, float u1) {
        Vec3 toCam = new Vec3(cam.x - base.x, 0.0D, cam.z - base.z);
        Vec3 side = toCam.lengthSqr() < 1.0E-6D ? new Vec3(1, 0, 0)
                : new Vec3(-toCam.z, 0.0D, toCam.x).normalize().scale(width * 0.5D);
        Vec3 top = base.add(0.0D, height, 0.0D);
        Quad q = new Quad();
        q.add(base.x - side.x, base.y, base.z - side.z, r, g, b, a, u0, 1);
        q.add(base.x + side.x, base.y, base.z + side.z, r, g, b, a, u1, 1);
        q.add(top.x + side.x, top.y, top.z + side.z, r, g, b, 0.0F, u1, 0);
        q.add(top.x - side.x, top.y, top.z - side.z, r, g, b, 0.0F, u0, 0);
        q.emit(c, m);
    }

    /** Cone of flowing air from the mouth along the look. */
    private static void cone(VertexConsumer c, Matrix4f m, Player p, float partial, float time, float r, float g,
                             float b, float a) {
        Vec3 look = p.getViewVector(partial);
        Vec3 mouth = p.getEyePosition(partial).add(look.scale(0.35D)).subtract(0.0D, 0.2D, 0.0D);
        Vec3 ref = Math.abs(look.y) > 0.9D ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 s = look.cross(ref).normalize();
        Vec3 t = look.cross(s).normalize();
        int segs = 12;
        int steps = 5;
        double length = 9.0D;
        float scroll = time * 0.06F;
        for (int k = 0; k < steps; k++) {
            double d0 = length * k / steps;
            double d1 = length * (k + 1) / steps;
            double r0 = 0.1D + d0 * 0.26D;
            double r1 = 0.1D + d1 * 0.26D;
            float a0 = a * (1.0F - k / (float) steps);
            float a1 = a * (1.0F - (k + 1) / (float) steps);
            for (int j = 0; j < segs; j++) {
                double w0 = 2.0D * Math.PI * j / segs;
                double w1 = 2.0D * Math.PI * (j + 1) / segs;
                Vec3 p00 = ring(mouth, look, s, t, d0, r0, w0);
                Vec3 p01 = ring(mouth, look, s, t, d0, r0, w1);
                Vec3 p11 = ring(mouth, look, s, t, d1, r1, w1);
                Vec3 p10 = ring(mouth, look, s, t, d1, r1, w0);
                float u0 = j / (float) segs;
                float u1 = (j + 1) / (float) segs;
                float v0 = (float) (d0 / length) - scroll;
                float v1 = (float) (d1 / length) - scroll;
                Quad q = new Quad();
                q.add(p00.x, p00.y, p00.z, r, g, b, a0, u0, v0);
                q.add(p10.x, p10.y, p10.z, r, g, b, a1, u0, v1);
                q.add(p11.x, p11.y, p11.z, r, g, b, a1, u1, v1);
                q.add(p01.x, p01.y, p01.z, r, g, b, a0, u1, v0);
                q.emit(c, m);
            }
        }
    }

    private static Vec3 ring(Vec3 o, Vec3 dir, Vec3 s, Vec3 t, double d, double r, double w) {
        return o.add(dir.scale(d)).add(s.scale(Math.cos(w) * r)).add(t.scale(Math.sin(w) * r));
    }

    /** Ribbon along the recent positions, facing the camera. */
    private static void trail(VertexConsumer c, Matrix4f m, Vec3 cam, Player p, float partial, Track t) {
        List<Vec3> pts = new ArrayList<>();
        pts.add(p.getPosition(partial).add(0.0D, p.getBbHeight() * 0.45D, 0.0D));
        Iterator<Vec3> it = t.trail.iterator();
        if (it.hasNext()) {
            it.next();
        }
        while (it.hasNext()) {
            pts.add(it.next());
        }
        int n = pts.size();
        for (int i = 0; i < n - 1; i++) {
            Vec3 a = pts.get(i);
            Vec3 b = pts.get(i + 1);
            Vec3 seg = b.subtract(a);
            Vec3 toCam = cam.subtract(a);
            Vec3 side = seg.cross(toCam);
            if (side.lengthSqr() < 1.0E-6D) {
                continue;
            }
            float w0 = 0.35F * (1.0F - i / (float) n);
            float w1 = 0.35F * (1.0F - (i + 1) / (float) n);
            Vec3 s = side.normalize();
            float al0 = 0.55F * (1.0F - i / (float) n);
            float al1 = 0.55F * (1.0F - (i + 1) / (float) n);
            float u0 = i / (float) n;
            float u1 = (i + 1) / (float) n;
            Quad q = new Quad();
            q.add(a.x - s.x * w0, a.y - s.y * w0, a.z - s.z * w0, 0.85F, 0.92F, 1.0F, al0, u0, 0);
            q.add(b.x - s.x * w1, b.y - s.y * w1, b.z - s.z * w1, 0.85F, 0.92F, 1.0F, al1, u1, 0);
            q.add(b.x + s.x * w1, b.y + s.y * w1, b.z + s.z * w1, 0.85F, 0.92F, 1.0F, al1, u1, 1);
            q.add(a.x + s.x * w0, a.y + s.y * w0, a.z + s.z * w0, 0.85F, 0.92F, 1.0F, al0, u0, 1);
            q.emit(c, m);
        }
    }

    /** Laser clash: glow, flares and cracks of the clash atlas (4 x 4 cells). */
    private static void clash(MultiBufferSource buffers, Matrix4f m, Vec3 at, Vector3f left, Vector3f up, float time) {
        VertexConsumer c = buffers.getBuffer(RenderType.eyes(CLASH));
        float pulse = 0.85F + 0.15F * (float) Math.sin(time * 2.1F);
        cell(c, m, at, left, up, 2.6F * pulse, 0, 0, 1.0F, 0.4F, 0.15F, 1.0F);
        cell(c, m, at, left, up, 3.4F, 2, 1, 1.0F, 0.75F, 0.5F, 0.9F);
        cell(c, m, at, left, up, 2.2F, 3, 2, 1.0F, 0.6F, 0.3F, 0.7F + 0.3F * pulse);
        cell(c, m, at, left, up, 1.6F, 0, 2, 1.0F, 0.85F, 0.6F, 0.6F);
    }

    private static void cell(VertexConsumer c, Matrix4f m, Vec3 at, Vector3f left, Vector3f up, float size, int col,
                             int row, float r, float g, float b, float a) {
        billboard(c, m, at, left, up, size, r, g, b, a, col / 4.0F, row / 4.0F, (col + 1) / 4.0F, (row + 1) / 4.0F);
    }
}
