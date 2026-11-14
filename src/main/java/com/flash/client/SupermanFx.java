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
        if (kind == SupermanFxPacket.CRACKS) {
            SupermanCracks.add(new Vec3(x, y, z), scale);
            return;
        }
        EFFECTS.add(new Fx(kind, new Vec3(x, y, z), dir, scale, mc.level.getGameTime()));
        if (EFFECTS.size() > 200) {
            EFFECTS.remove(0);
        }
    }

    // ------------------------------------------------------------------
    // Frost left by the freeze breath and ice on frozen creatures
    // ------------------------------------------------------------------

    private static final ResourceLocation[] FROST_TEX = {ptex("frost_0"), ptex("frost_1"), ptex("frost_2"),
            ptex("frost_3")};
    private static final ResourceLocation ICE_TEX = ptex("ice_crystal");
    private static final ResourceLocation SNOW_TEX = ptex("snowflake");

    private static ResourceLocation ptex(String name) {
        return new ResourceLocation(FlashMod.MODID, "textures/particle/superman/" + name + ".png");
    }

    private record Frost(Vec3 pos, Vec3 normal, float size, long start, float spin, int tex) {
    }

    private static final List<Frost> FROST = new ArrayList<>();
    private static final int FROST_LIFE = 500;

    /** Throws a few rays inside the breath cone and frosts the faces they reach. */
    private static void frostTick(ClientLevel level, Player p) {
        Vec3 eye = p.getEyePosition();
        Vec3 look = p.getLookAngle();
        for (int i = 0; i < 3; i++) {
            Vec3 d = look.add((level.random.nextDouble() - 0.5D) * 0.5D, (level.random.nextDouble() - 0.5D) * 0.5D,
                    (level.random.nextDouble() - 0.5D) * 0.5D).normalize();
            net.minecraft.world.phys.BlockHitResult hit = level.clip(new net.minecraft.world.level.ClipContext(eye,
                    eye.add(d.scale(12.0D)), net.minecraft.world.level.ClipContext.Block.COLLIDER,
                    net.minecraft.world.level.ClipContext.Fluid.ANY, p));
            if (hit.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK) {
                continue;
            }
            Vec3 n = Vec3.atLowerCornerOf(hit.getDirection().getNormal());
            FROST.add(new Frost(hit.getLocation().add(n.scale(0.008D + FROST.size() % 9 * 0.0005D)), n,
                    0.5F + level.random.nextFloat() * 0.7F, level.getGameTime(), level.random.nextFloat() * 6.28F,
                    level.random.nextInt(5)));
        }
        while (FROST.size() > 900) {
            FROST.remove(0);
        }
    }

    private static void renderFrost(MultiBufferSource buffers, Matrix4f m, Vec3 cam, float time) {
        for (Frost f : FROST) {
            float age = time - f.start();
            if (age < 0.0F || age > FROST_LIFE || cam.distanceToSqr(f.pos()) > 64.0D * 64.0D) {
                continue;
            }
            float grow = Math.min(1.0F, age / 12.0F);
            float melt = age > FROST_LIFE - 120 ? (FROST_LIFE - age) / 120.0F : 1.0F;
            ResourceLocation tex = f.tex() < 4 ? FROST_TEX[f.tex()] : SNOW_TEX;
            VertexConsumer c = buffers.getBuffer(RenderType.entityTranslucent(tex));
            Vec3 n = f.normal();
            Vec3 ref = Math.abs(n.y) > 0.9D ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
            Vec3 s = n.cross(ref).normalize();
            Vec3 t = n.cross(s).normalize();
            double cs = Math.cos(f.spin());
            double sn = Math.sin(f.spin());
            float size = f.size() * (0.4F + 0.6F * grow);
            Vec3 u = s.scale(cs).add(t.scale(sn)).scale(size * 0.5D);
            Vec3 v = t.scale(cs).subtract(s.scale(sn)).scale(size * 0.5D);
            Vec3 p = f.pos();
            Vec3[] q = {p.subtract(u).subtract(v), p.add(u).subtract(v), p.add(u).add(v), p.subtract(u).add(v)};
            float[][] uv = {{0, 1}, {1, 1}, {1, 0}, {0, 0}};
            float a = 0.85F * melt * grow;
            for (int k = 0; k < 4; k++) {
                Vector4f w = m.transform(new Vector4f((float) q[k].x, (float) q[k].y, (float) q[k].z, 1.0F));
                c.vertex(w.x(), w.y(), w.z(), 0.86F, 0.95F, 1.0F, a, uv[k][0], uv[k][1], OverlayTexture.NO_OVERLAY,
                        0xF000F0, (float) n.x, (float) n.y, (float) n.z);
            }
        }
    }

    private static final ResourceLocation ICE_BLOCK = new ResourceLocation("minecraft", "textures/block/ice.png");

    /**
     * Freezing creatures: ice spikes grow out of the body while it gets colder; once frozen solid (like the pack)
     * the creature is locked in a block of ice bristling with spikes.
     */
    private static void renderFrozen(MultiBufferSource buffers, Matrix4f m, ClientLevel level, Vec3 cam,
                                     Vector3f left, Vector3f up, float partial) {
        VertexConsumer c = null;
        for (net.minecraft.world.entity.LivingEntity living : ClientEntityIndex.FROZEN) {
            net.minecraft.world.entity.Entity e = living;
            if (e.isRemoved() || living.getTicksFrozen() <= 20
                    || e.distanceToSqr(cam) > 48.0D * 48.0D || e.isInvisible() || !e.isAlive()) {
                continue;
            }
            if (e == Minecraft.getInstance().player && Minecraft.getInstance().options.getCameraType().isFirstPerson()) {
                continue;
            }
            if (c == null) {
                c = buffers.getBuffer(RenderType.entityTranslucent(ICE_BLOCK));
            }
            int light = net.minecraft.client.renderer.LevelRenderer.getLightColor(level, e.blockPosition());
            float cold = Math.min(1.0F, living.getTicksFrozen() / 100.0F);
            float solid = Math.max(0.0F, Math.min(1.0F, (living.getTicksFrozen() - 100) / 25.0F));
            Vec3 at = e.getPosition(partial);
            net.minecraft.world.phys.AABB box = e.getBoundingBox().move(at.subtract(e.position())).inflate(0.12D);
            if (solid > 0.0F) {
                // the block of ice grows from the feet up
                double top = box.minY + (box.maxY - box.minY) * solid;
                iceBox(c, m, box.minX, box.minY, box.minZ, box.maxX, top, box.maxZ, 0.92F, light);
            }
            java.util.Random rnd = new java.util.Random(e.getId() * 341873128712L);
            float w = e.getBbWidth();
            float h = e.getBbHeight();
            float size = Math.min(1.5F, 0.55F + 0.45F * Math.max(w, h * 0.6F));
            int shards = 10 + rnd.nextInt(7);
            for (int i = 0; i < shards; i++) {
                double ang = rnd.nextDouble() * Math.PI * 2.0D;
                double hgt = 0.1D + rnd.nextDouble() * 0.85D;
                Vec3 out = new Vec3(Math.cos(ang), (rnd.nextDouble() - 0.35D) * 1.1D, Math.sin(ang)).normalize();
                Vec3 base = new Vec3(at.x + Math.cos(ang) * w * 0.5D, at.y + hgt * h, at.z + Math.sin(ang) * w * 0.5D);
                float len = (0.35F + 0.45F * rnd.nextFloat()) * size * (0.25F + 0.75F * cold);
                float rad = (0.08F + 0.07F * rnd.nextFloat()) * size * (0.4F + 0.6F * cold);
                spike(c, m, base.subtract(out.scale(len * 0.15D)), out, len, rad, rnd.nextFloat() * 6.28F, light,
                        0.6F + 0.3F * cold);
            }
        }
    }

    private static void iceBox(VertexConsumer c, Matrix4f m, double x0, double y0, double z0, double x1, double y1,
                               double z1, float a, int light) {
        float w = (float) (x1 - x0);
        float h = (float) (y1 - y0);
        float d = (float) (z1 - z0);
        // each face with its own normal; uv repeats the ice texture once per block
        iceQuad(c, m, x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0, w, h, 0, 0, -1, a, light);
        iceQuad(c, m, x1, y0, z1, x0, y0, z1, x0, y1, z1, x1, y1, z1, w, h, 0, 0, 1, a, light);
        iceQuad(c, m, x0, y0, z1, x0, y0, z0, x0, y1, z0, x0, y1, z1, d, h, -1, 0, 0, a, light);
        iceQuad(c, m, x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0, d, h, 1, 0, 0, a, light);
        iceQuad(c, m, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, w, d, 0, 1, 0, a, light);
        iceQuad(c, m, x0, y0, z1, x1, y0, z1, x1, y0, z0, x0, y0, z0, w, d, 0, -1, 0, a, light);
    }

    private static void iceQuad(VertexConsumer c, Matrix4f m, double ax, double ay, double az, double bx, double by,
                                double bz, double cx, double cy, double cz, double dx, double dy, double dz, float us,
                                float vs, float nx, float ny, float nz, float a, int light) {
        double[][] q = {{ax, ay, az, 0, vs}, {bx, by, bz, us, vs}, {cx, cy, cz, us, 0}, {dx, dy, dz, 0, 0}};
        for (int pass = 0; pass < 2; pass++) {
            for (int k = 0; k < 4; k++) {
                double[] v = q[pass == 0 ? k : 3 - k];
                Vector4f w = m.transform(new Vector4f((float) v[0], (float) v[1], (float) v[2], 1.0F));
                c.vertex(w.x(), w.y(), w.z(), 0.86F, 0.94F, 1.0F, a * 0.8F, (float) v[3], (float) v[4],
                        OverlayTexture.NO_OVERLAY, light, nx, ny, nz);
            }
        }
    }

    /** Four-sided ice spike from {@code base} along {@code dir}, lit from above like the pack's shards. */
    private static void spike(VertexConsumer c, Matrix4f m, Vec3 base, Vec3 dir, float len, float rad, float spin,
                              int light, float alpha) {
        Vec3 ref = Math.abs(dir.y) > 0.9D ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 s = dir.cross(ref).normalize();
        Vec3 t = dir.cross(s).normalize();
        Vec3 tip = base.add(dir.scale(len));
        Vec3[] ring = new Vec3[4];
        for (int i = 0; i < 4; i++) {
            double a = spin + i * Math.PI / 2.0D;
            ring[i] = base.add(s.scale(Math.cos(a) * rad)).add(t.scale(Math.sin(a) * rad));
        }
        for (int i = 0; i < 4; i++) {
            Vec3 c0 = ring[i];
            Vec3 c1 = ring[(i + 1) % 4];
            Vec3 mid = c0.add(c1).scale(0.5D).subtract(base).normalize();
            Vec3 n = mid.scale(0.8D).add(dir.scale(0.35D)).normalize();
            float lam = (float) Math.max(0.0D, n.x * 0.3176D + n.y * 0.9075D + n.z * 0.2269D);
            float shade = 0.72F + 0.28F * lam;
            float r = 0.66F * shade + (lam > 0.6F ? (lam - 0.6F) * 0.6F : 0.0F);
            float g = 0.86F * shade + (lam > 0.6F ? (lam - 0.6F) * 0.4F : 0.0F);
            float b = shade;
            Vec3[] q = {c0, c1, tip, tip};
            float[][] uv = {{0.0F, 1.0F}, {0.5F, 1.0F}, {0.25F, 0.0F}, {0.25F, 0.0F}};
            for (int pass = 0; pass < 2; pass++) {
                for (int k = 0; k < 4; k++) {
                    int idx = pass == 0 ? k : 3 - k;
                    Vector4f w = m.transform(new Vector4f((float) q[idx].x, (float) q[idx].y, (float) q[idx].z, 1.0F));
                    c.vertex(w.x(), w.y(), w.z(), Math.min(1.0F, r), Math.min(1.0F, g), Math.min(1.0F, b), alpha,
                            uv[idx][0], uv[idx][1], OverlayTexture.NO_OVERLAY, light, (float) n.x, (float) n.y,
                            (float) n.z);
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Burnt trail of the heat vision
    // ------------------------------------------------------------------

    private record Scorch(Vec3 pos, Vec3 normal, float size, long start, float spin) {
    }

    private static final List<Scorch> SCORCH = new ArrayList<>();
    private static final Map<UUID, Long> LAST_SCORCH = new HashMap<>();
    private static final Map<UUID, Vec3> LAST_SCORCH_POS = new HashMap<>();
    private static final int SCORCH_LIFE = 320;

    /** Leaves a burnt mark where a heat beam touches a block (at most one per tick and player). */
    public static void scorch(UUID player, Vec3 pos, Vec3 normal, boolean laser) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        long now = mc.level.getGameTime();
        Long last = LAST_SCORCH.get(player);
        Vec3 lastPos = LAST_SCORCH_POS.get(player);
        if (last != null && last == now) {
            return;
        }
        if (lastPos != null && lastPos.distanceToSqr(pos) < 0.04D && last != null && now - last < 6L) {
            return;
        }
        LAST_SCORCH.put(player, now);
        LAST_SCORCH_POS.put(player, pos);
        SCORCH.add(new Scorch(pos.add(normal.scale(0.012D + SCORCH.size() % 7 * 0.0006D)), normal,
                (laser ? 0.75F : 0.5F) * (0.85F + mc.level.random.nextFloat() * 0.3F), now,
                mc.level.random.nextFloat() * 6.28F));
        if (SCORCH.size() > 700) {
            SCORCH.remove(0);
        }
    }

    private static void renderScorch(MultiBufferSource buffers, Matrix4f m, long gameTime, float partial) {
        if (SCORCH.isEmpty()) {
            return;
        }
        VertexConsumer dark = buffers.getBuffer(RenderType.entityTranslucent(GLOW));
        VertexConsumer hot = buffers.getBuffer(RenderType.eyes(GLOW));
        float time = gameTime + partial;
        for (Scorch sc : SCORCH) {
            float age = time - sc.start();
            if (age < 0.0F || age > SCORCH_LIFE) {
                continue;
            }
            float fade = age > SCORCH_LIFE - 100 ? (SCORCH_LIFE - age) / 100.0F : 1.0F;
            decal(dark, m, sc, sc.size(), 0.05F, 0.03F, 0.02F, 0.85F * fade, false);
            // the glowing edge cools down over three seconds
            float glow = Math.max(0.0F, 1.0F - age / 60.0F);
            if (glow > 0.0F) {
                decal(hot, m, sc, sc.size() * 0.8F, 1.0F, 0.32F, 0.05F, glow * 0.9F, true);
            }
        }
    }

    private static void decal(VertexConsumer c, Matrix4f m, Scorch sc, float size, float r, float g, float b,
                              float a, boolean additive) {
        Vec3 n = sc.normal();
        Vec3 ref = Math.abs(n.y) > 0.9D ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 s = n.cross(ref).normalize();
        Vec3 t = n.cross(s).normalize();
        double cs = Math.cos(sc.spin());
        double sn = Math.sin(sc.spin());
        Vec3 u = s.scale(cs).add(t.scale(sn)).scale(size * 0.5D);
        Vec3 v = t.scale(cs).subtract(s.scale(sn)).scale(size * 0.5D);
        Vec3 p = sc.pos();
        Vec3[] q = {p.subtract(u).subtract(v), p.add(u).subtract(v), p.add(u).add(v), p.subtract(u).add(v)};
        float[][] uv = {{0, 1}, {1, 1}, {1, 0}, {0, 0}};
        for (int i = 0; i < 4; i++) {
            Vector4f w = m.transform(new Vector4f((float) q[i].x, (float) q[i].y, (float) q[i].z, 1.0F));
            if (additive) {
                c.vertex(w.x(), w.y(), w.z(), r * a, g * a, b * a, 1.0F, uv[i][0], uv[i][1],
                        OverlayTexture.NO_OVERLAY, 0xF000F0, (float) n.x, (float) n.y, (float) n.z);
            } else {
                c.vertex(w.x(), w.y(), w.z(), r, g, b, a, uv[i][0], uv[i][1], OverlayTexture.NO_OVERLAY,
                        0xF000F0, (float) n.x, (float) n.y, (float) n.z);
            }
        }
        for (int i = 3; i >= 0; i--) {
            Vector4f w = m.transform(new Vector4f((float) q[i].x, (float) q[i].y, (float) q[i].z, 1.0F));
            if (additive) {
                c.vertex(w.x(), w.y(), w.z(), r * a, g * a, b * a, 1.0F, uv[i][0], uv[i][1],
                        OverlayTexture.NO_OVERLAY, 0xF000F0, (float) n.x, (float) n.y, (float) n.z);
            } else {
                c.vertex(w.x(), w.y(), w.z(), r, g, b, a, uv[i][0], uv[i][1], OverlayTexture.NO_OVERLAY,
                        0xF000F0, (float) n.x, (float) n.y, (float) n.z);
            }
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
        SCORCH.removeIf(sc -> now - sc.start() > SCORCH_LIFE);
        FROST.removeIf(f -> now - f.start() > FROST_LIFE);
        if (now % 2L == 0L) {
            for (Player p : mc.level.players()) {
                if (SupermanState.isSuperman(p) && SupermanState.flag(p, SupermanPowers.F_FREEZE)) {
                    frostTick(mc.level, p);
                }
            }
        }
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


        renderFrost(buffers, m, cam, time);
        for (ResourceLocation tex : FROST_TEX) {
            buffers.endBatch(RenderType.entityTranslucent(tex));
        }
        buffers.endBatch(RenderType.entityTranslucent(SNOW_TEX));
        renderFrozen(buffers, m, level, cam, left, up, partial);
        buffers.endBatch(RenderType.entityTranslucent(ICE_BLOCK));

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
                // only the ball: no square glow around it
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
                // with the pack shaders the eye and impact flares come from the beam shader
                boolean shaders = SupermanShaders.ready();
                if (!firstPerson && !shaders) {
                    for (int s = -1; s <= 1; s += 2) {
                        billboard(glow, m, eye.add(look.scale(0.26D)).add(side.scale(s * 0.065D)).add(0, 0.02D, 0),
                                left, up, laser ? 0.35F : 0.22F, 1.0F, 0.25F, 0.1F, 1.0F, 0.0F, 0.0F, 1.0F, 1.0F);
                    }
                }
                Vec3 hit = SupermanClient.beamEnd(p.getUUID());
                if (hit != null) {
                    float pulse = 0.8F + 0.2F * (float) Math.sin(time * 1.7F);
                    if (!shaders) {
                        billboard(glow, m, hit, left, up, (laser ? 1.6F : 1.0F) * pulse, 1.0F, 0.35F, 0.08F, 1.0F,
                                0.0F, 0.0F, 1.0F, 1.0F);
                    }
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
