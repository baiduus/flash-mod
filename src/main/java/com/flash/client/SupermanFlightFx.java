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
import java.util.Random;
import java.util.UUID;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
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
 * Flight effects of the pack (FlightFx46): dust burst on takeoff, downdraft dust while hovering near the ground,
 * a dust wake under a fast low flight, vapor under a fast climb, a vapor collar ring now and then at speed, the
 * vapor "dome" (mach cone) in front at top speed and the wingtip vortex trails from the hands.
 */
@Mod.EventBusSubscriber(modid = FlashMod.MODID, value = Dist.CLIENT)
public final class SupermanFlightFx {

    private static final ResourceLocation[] VAPOR = tex("vapor", 4);
    private static final ResourceLocation[] DUST = tex("dust", 4);
    private static final ResourceLocation TRAIL = new ResourceLocation(FlashMod.MODID, "textures/fx/superman/trail.png");
    private static final ResourceLocation FLOW = new ResourceLocation(FlashMod.MODID,
            "textures/fx/superman/breath_flow.png");
    private static final int MAX_PUFFS = 1600;
    private static final Random RNG = new Random();

    private static ResourceLocation[] tex(String name, int n) {
        ResourceLocation[] out = new ResourceLocation[n];
        for (int i = 0; i < n; i++) {
            out[i] = new ResourceLocation(FlashMod.MODID, "textures/particle/superman/" + name + "_" + i + ".png");
        }
        return out;
    }

    private SupermanFlightFx() {
    }

    // ------------------------------------------------------------------
    // Puffs (soft billboards with drag)
    // ------------------------------------------------------------------

    private static final class Puff {
        double x;
        double y;
        double z;
        double vx;
        double vy;
        double vz;
        int age;
        int life;
        float s0;
        float s1;
        float rot;
        float spin;
        float r;
        float g;
        float b;
        float a0;
        float drag;
        float rise;
        boolean dust;
        int cell;
    }

    private static final List<Puff> PUFFS = new ArrayList<>();

    private static void puff(boolean dust, double x, double y, double z, double vx, double vy, double vz, int life,
                             float s0, float s1, float a0, float drag, float rise) {
        if (PUFFS.size() >= MAX_PUFFS) {
            PUFFS.remove(0);
        }
        Puff p = new Puff();
        p.x = x;
        p.y = y;
        p.z = z;
        p.vx = vx;
        p.vy = vy;
        p.vz = vz;
        p.life = life;
        p.s0 = s0;
        p.s1 = s1;
        p.a0 = a0;
        p.drag = drag;
        p.rise = rise;
        p.dust = dust;
        p.cell = RNG.nextInt(4);
        p.rot = RNG.nextFloat() * 6.28F;
        p.spin = (RNG.nextFloat() - 0.5F) * 0.06F;
        p.r = dust ? 0.78F : 1.0F;
        p.g = dust ? 0.72F : 1.0F;
        p.b = dust ? 0.64F : 1.0F;
        PUFFS.add(p);
    }

    // ------------------------------------------------------------------
    // Per player
    // ------------------------------------------------------------------

    private static final class State {
        boolean wasFlying;
        boolean wasClimbing;
        int burstCooldown;
        int collar;
        final Deque<Vec3[]> hands = new ArrayDeque<>();
        float dome;
    }

    private static final Map<UUID, State> STATES = new HashMap<>();

    private static boolean flying(Player p) {
        return p == Minecraft.getInstance().player ? p.getAbilities().flying
                : SupermanState.flag(p, SupermanPowers.F_FLYING);
    }

    /** Distance to the ground below (up to {@code max}), or -1. */
    private static double ground(ClientLevel level, Player p, double max, Vec3[] hitOut) {
        Vec3 from = p.position();
        BlockHitResult hit = level.clip(new ClipContext(from, from.subtract(0.0D, max, 0.0D), ClipContext.Block.COLLIDER,
                ClipContext.Fluid.ANY, p));
        if (hit.getType() != HitResult.Type.BLOCK) {
            return -1.0D;
        }
        hitOut[0] = hit.getLocation();
        return from.y - hit.getLocation().y;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.phase != TickEvent.Phase.END || mc.level == null || mc.isPaused()) {
            return;
        }
        ClientLevel level = mc.level;
        // simulate
        Iterator<Puff> it = PUFFS.iterator();
        while (it.hasNext()) {
            Puff p = it.next();
            if (++p.age >= p.life) {
                it.remove();
                continue;
            }
            p.x += p.vx;
            p.y += p.vy;
            p.z += p.vz;
            p.vx *= p.drag;
            p.vy = p.vy * p.drag + p.rise;
            p.vz *= p.drag;
            p.rot += p.spin;
        }
        Vec3[] hit = new Vec3[1];
        for (Player p : level.players()) {
            if (!SupermanState.isSuperman(p) || p.isInvisible()) {
                STATES.remove(p.getUUID());
                continue;
            }
            State st = STATES.computeIfAbsent(p.getUUID(), id -> new State());
            boolean flying = flying(p);
            Vec3 v = p.position().subtract(p.xo, p.yo, p.zo);
            double speed = v.length();
            if (st.burstCooldown > 0) {
                st.burstCooldown--;
            }
            // takeoff burst
            if (flying && !st.wasFlying && st.burstCooldown == 0) {
                st.burstCooldown = 30;
                double d = ground(level, p, 2.5D, hit);
                Vec3 at = d >= 0.0D ? hit[0] : p.position();
                for (int i = 0; i < 26; i++) {
                    double a = i * Math.PI * 2.0D / 26.0D;
                    puff(d >= 0.0D, at.x, at.y + 0.15D, at.z, Math.cos(a) * 0.32D, 0.02D, Math.sin(a) * 0.32D,
                            28 + RNG.nextInt(12), 0.5F, 1.6F, 0.55F, 0.88F, 0.002F);
                }
                SupermanFx.spawn(SupermanFxPacket.RING, at.x, at.y + 0.2D, at.z, 0.0F, 1.0F, 0.0F, 3.0F);
            }
            st.wasFlying = flying;
            if (!flying) {
                st.hands.clear();
                st.dome = Math.max(0.0F, st.dome - 0.1F);
                continue;
            }
            double groundDist = ground(level, p, 5.0D, hit);
            // hovering low: the downdraft blows dust out
            if (speed < 0.3D && groundDist >= 0.0D && groundDist < 4.0D && p.tickCount % 3 == 0) {
                for (int i = 0; i < 3; i++) {
                    double a = RNG.nextDouble() * Math.PI * 2.0D;
                    float f = (float) (1.0D - groundDist / 4.0D);
                    puff(true, hit[0].x + Math.cos(a) * 0.4D, hit[0].y + 0.1D, hit[0].z + Math.sin(a) * 0.4D,
                            Math.cos(a) * 0.22D * f, 0.01D, Math.sin(a) * 0.22D * f, 26, 0.4F, 1.3F, 0.4F * f, 0.9F, 0.0F);
                }
            }
            // fast and low: a dust wake on the ground
            if (speed > 1.0D && groundDist >= 0.0D && groundDist < 5.0D) {
                float f = (float) (1.0D - groundDist / 5.0D);
                for (int i = 0; i < 2; i++) {
                    puff(true, hit[0].x + (RNG.nextDouble() - 0.5D), hit[0].y + 0.1D, hit[0].z + (RNG.nextDouble() - 0.5D),
                            -v.x * 0.08D + (RNG.nextDouble() - 0.5D) * 0.2D, 0.08D + RNG.nextDouble() * 0.1D,
                            -v.z * 0.08D + (RNG.nextDouble() - 0.5D) * 0.2D, 30, 0.6F, 1.8F, 0.5F * f, 0.9F, 0.0F);
                }
            }
            // fast climb: vapor bursting below the feet
            boolean climbing = v.y > 0.85D;
            if (climbing && !st.wasClimbing) {
                for (int i = 0; i < 14; i++) {
                    double a = i * Math.PI * 2.0D / 14.0D;
                    puff(false, p.getX(), p.getY() - 0.2D, p.getZ(), Math.cos(a) * 0.25D, -0.1D, Math.sin(a) * 0.25D,
                            22, 0.4F, 1.4F, 0.45F, 0.86F, 0.0F);
                }
            }
            st.wasClimbing = climbing;
            // vapor collar at speed
            if (speed > 1.2D && ++st.collar % 7 == 0) {
                Vec3 c = p.position().add(0.0D, p.getBbHeight() * 0.5D, 0.0D);
                SupermanFx.spawn(SupermanFxPacket.RING, c.x, c.y, c.z, (float) v.x, (float) v.y, (float) v.z, 1.3F);
                for (int i = 0; i < 6; i++) {
                    puff(false, c.x + (RNG.nextDouble() - 0.5D) * 0.8D, c.y + (RNG.nextDouble() - 0.5D) * 0.8D,
                            c.z + (RNG.nextDouble() - 0.5D) * 0.8D, v.x * 0.3D, v.y * 0.3D, v.z * 0.3D, 14, 0.3F, 0.9F,
                            0.22F, 0.8F, 0.0F);
                }
            }
            // mach dome at top speed
            st.dome += ((speed > 2.0D ? 1.0F : 0.0F) - st.dome) * 0.12F;
            // wingtip vortices from the hands
            if (speed > 1.0D) {
                Vec3 dir = v.normalize();
                Vec3 side = dir.cross(new Vec3(0, 1, 0));
                side = side.lengthSqr() < 1.0E-6D ? new Vec3(1, 0, 0) : side.normalize();
                Vec3 c = p.position().add(0.0D, p.getBbHeight() * 0.5D, 0.0D);
                st.hands.addFirst(new Vec3[] {c.add(side.scale(0.45D)).add(dir.scale(0.6D)),
                        c.subtract(side.scale(0.45D)).add(dir.scale(0.6D))});
            } else if (!st.hands.isEmpty()) {
                st.hands.removeLast();
            }
            while (st.hands.size() > 18) {
                st.hands.removeLast();
            }
        }
    }

    // ------------------------------------------------------------------
    // Drawing
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || (PUFFS.isEmpty() && STATES.isEmpty())) {
            return;
        }
        float partial = event.getPartialTick();
        Camera camera = event.getCamera();
        Vec3 cam = camera.getPosition();
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f m = pose.last().pose();
        Vector3f left = new Vector3f(camera.getLeftVector());
        Vector3f up = new Vector3f(camera.getUpVector());
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        for (Puff p : PUFFS) {
            float t = (p.age + partial) / p.life;
            float a = p.a0 * (t < 0.15F ? t / 0.15F : 1.0F - (t - 0.15F) / 0.85F);
            if (a <= 0.01F) {
                continue;
            }
            float size = p.s0 + (p.s1 - p.s0) * t;
            double x = p.x + p.vx * partial;
            double y = p.y + p.vy * partial;
            double z = p.z + p.vz * partial;
            int light = LevelRenderer.getLightColor(level, BlockPos.containing(x, y, z));
            VertexConsumer c = buffers.getBuffer(RenderType.entityTranslucent((p.dust ? DUST : VAPOR)[p.cell]));
            billboard(c, m, x, y, z, left, up, size, p.rot, p.r, p.g, p.b, a, light);
        }
        for (ResourceLocation r : DUST) {
            buffers.endBatch(RenderType.entityTranslucent(r));
        }
        for (ResourceLocation r : VAPOR) {
            buffers.endBatch(RenderType.entityTranslucent(r));
        }
        for (Player p : level.players()) {
            State st = STATES.get(p.getUUID());
            if (st == null) {
                continue;
            }
            if (st.hands.size() > 1) {
                VertexConsumer c = buffers.getBuffer(RenderType.eyes(TRAIL));
                vortex(c, m, cam, st, 0);
                vortex(c, m, cam, st, 1);
            }
            if (st.dome > 0.02F) {
                dome(buffers.getBuffer(RenderType.eyes(FLOW)), m, p, partial, level.getGameTime() + partial, st.dome);
            }
        }
        buffers.endBatch(RenderType.eyes(TRAIL));
        buffers.endBatch(RenderType.eyes(FLOW));
        pose.popPose();
    }

    private static void billboard(VertexConsumer c, Matrix4f m, double x, double y, double z, Vector3f left,
                                  Vector3f up, float size, float rot, float r, float g, float b, float a, int light) {
        float h = size * 0.5F;
        float cs = (float) Math.cos(rot);
        float sn = (float) Math.sin(rot);
        // rotated corners in the camera plane
        float[][] k = {{-1, -1}, {1, -1}, {1, 1}, {-1, 1}};
        float[][] uv = {{0, 1}, {1, 1}, {1, 0}, {0, 0}};
        for (int i = 0; i < 4; i++) {
            float u = (k[i][0] * cs - k[i][1] * sn) * h;
            float v = (k[i][0] * sn + k[i][1] * cs) * h;
            Vector4f w = m.transform(new Vector4f((float) (x + left.x() * u + up.x() * v),
                    (float) (y + left.y() * u + up.y() * v), (float) (z + left.z() * u + up.z() * v), 1.0F));
            c.vertex(w.x(), w.y(), w.z(), r, g, b, a, uv[i][0], uv[i][1], OverlayTexture.NO_OVERLAY, light,
                    0.0F, 1.0F, 0.0F);
        }
    }

    private static void put(VertexConsumer c, Matrix4f m, Vec3 p, float r, float g, float b, float a, float u, float v) {
        Vector4f w = m.transform(new Vector4f((float) p.x, (float) p.y, (float) p.z, 1.0F));
        c.vertex(w.x(), w.y(), w.z(), r * a, g * a, b * a, 1.0F, u, v, OverlayTexture.NO_OVERLAY, 0xF000F0,
                0.0F, 1.0F, 0.0F);
    }

    /** Spiral-free ribbon along the hand positions, fading out behind. */
    private static void vortex(VertexConsumer c, Matrix4f m, Vec3 cam, State st, int hand) {
        Vec3[][] pts = st.hands.toArray(new Vec3[0][]);
        int n = pts.length;
        for (int i = 0; i < n - 1; i++) {
            Vec3 a = pts[i][hand];
            Vec3 b = pts[i + 1][hand];
            Vec3 side = b.subtract(a).cross(cam.subtract(a));
            if (side.lengthSqr() < 1.0E-8D) {
                continue;
            }
            side = side.normalize();
            float w0 = 0.05F + 0.06F * i / n;
            float w1 = 0.05F + 0.06F * (i + 1) / n;
            float a0 = 0.34F * (1.0F - i / (float) n);
            float a1 = 0.34F * (1.0F - (i + 1) / (float) n);
            Vec3[] q = {a.subtract(side.scale(w0)), b.subtract(side.scale(w1)), b.add(side.scale(w1)),
                    a.add(side.scale(w0))};
            float[] al = {a0, a1, a1, a0};
            float[][] uv = {{i / (float) n, 0}, {(i + 1) / (float) n, 0}, {(i + 1) / (float) n, 1}, {i / (float) n, 1}};
            for (int k = 0; k < 4; k++) {
                put(c, m, q[k], 0.9F, 0.95F, 1.0F, al[k], uv[k][0], uv[k][1]);
            }
            for (int k = 3; k >= 0; k--) {
                put(c, m, q[k], 0.9F, 0.95F, 1.0F, al[k], uv[k][0], uv[k][1]);
            }
        }
    }

    /** Vapor cone around the body at top speed, opening backward from just ahead of the head. */
    private static void dome(VertexConsumer c, Matrix4f m, Player p, float partial, float time, float k) {
        Vec3 v = p.getPosition(partial).subtract(p.getPosition(partial - 1.0F));
        if (v.lengthSqr() < 1.0E-4D) {
            return;
        }
        Vec3 dir = v.normalize();
        Vec3 apex = p.getPosition(partial).add(0.0D, p.getBbHeight() * 0.5D, 0.0D).add(dir.scale(1.55D));
        Vec3 ref = Math.abs(dir.y) > 0.9D ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 s = dir.cross(ref).normalize();
        Vec3 t = dir.cross(s).normalize();
        double len = 2.2D + 1.6D * k;
        double rad = 1.4D + 1.4D * k;
        int segs = 16;
        int steps = 4;
        float scroll = time * 0.05F;
        for (int i = 0; i < steps; i++) {
            double f0 = i / (double) steps;
            double f1 = (i + 1) / (double) steps;
            for (int j = 0; j < segs; j++) {
                double w0 = Math.PI * 2.0D * j / segs;
                double w1 = Math.PI * 2.0D * (j + 1) / segs;
                Vec3 p00 = ringPt(apex, dir, s, t, -len * f0, rad * Math.sqrt(f0), w0);
                Vec3 p01 = ringPt(apex, dir, s, t, -len * f0, rad * Math.sqrt(f0), w1);
                Vec3 p11 = ringPt(apex, dir, s, t, -len * f1, rad * Math.sqrt(f1), w1);
                Vec3 p10 = ringPt(apex, dir, s, t, -len * f1, rad * Math.sqrt(f1), w0);
                float a0 = 0.55F * k * (float) (Math.sin(Math.PI * f0) + 0.15D);
                float a1 = 0.55F * k * (float) (Math.sin(Math.PI * f1) + 0.15D) * (i == steps - 1 ? 0.0F : 1.0F);
                Vec3[] q = {p00, p10, p11, p01};
                float[] al = {a0, a1, a1, a0};
                float[][] uv = {{j / (float) segs, (float) f0 - scroll}, {j / (float) segs, (float) f1 - scroll},
                        {(j + 1) / (float) segs, (float) f1 - scroll}, {(j + 1) / (float) segs, (float) f0 - scroll}};
                for (int n = 0; n < 4; n++) {
                    put(c, m, q[n], 0.92F, 0.96F, 1.0F, al[n] * 0.5F, uv[n][0], uv[n][1]);
                }
                for (int n = 3; n >= 0; n--) {
                    put(c, m, q[n], 0.92F, 0.96F, 1.0F, al[n] * 0.5F, uv[n][0], uv[n][1]);
                }
            }
        }
    }

    private static Vec3 ringPt(Vec3 o, Vec3 dir, Vec3 s, Vec3 t, double d, double r, double w) {
        return o.add(dir.scale(d)).add(s.scale(Math.cos(w) * r)).add(t.scale(Math.sin(w) * r));
    }
}
