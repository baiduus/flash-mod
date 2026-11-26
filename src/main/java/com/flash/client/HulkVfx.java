package com.flash.client;

import com.flash.FlashMod;
import com.flash.hulk.HulkFx;
import com.flash.network.HeroFxPacket;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/**
 * The Hulk's powers drawn in light, as in a film (VfxDraw, additive): gamma green with a white-hot heart (the Red
 * Hulk: fire red and orange). Every blow on the ground opens glowing cracks and rings of shock running over the
 * ground; the roar and the thunderclap are walls of air flying forward; the gamma wave is a blinding sphere with
 * lightning; the fury a living aura of gamma flames and burning eyes. Lens flares on every flash, sparks, debris and
 * the camera shaking when it is close.
 */
@Mod.EventBusSubscriber(modid = FlashMod.MODID, value = Dist.CLIENT)
public final class HulkVfx {

    private static final float[][] GREEN = {{0.12F, 0.95F, 0.18F}, {0.55F, 1.0F, 0.32F}, {0.9F, 1.0F, 0.82F}};
    private static final float[][] RED = {{1.0F, 0.16F, 0.03F}, {1.0F, 0.55F, 0.12F}, {1.0F, 0.92F, 0.72F}};

    private static final class Fx {
        final int type;
        final int caster;
        final Vec3 a;
        final Vec3 b;
        final float power;
        final boolean red;
        final int extra;
        final long seed;
        final int life;
        float age;

        Fx(HeroFxPacket p, int life) {
            this.type = p.type();
            this.caster = p.caster();
            this.a = p.from();
            this.b = p.to();
            this.power = p.power();
            int t = Math.max(0, p.target());
            this.red = (t & 1) != 0;
            this.extra = t >> 1;
            this.seed = (long) (p.from().x * 73.0D + p.from().z * 31.0D) * 7919L ^ System.nanoTime();
            this.life = life;
        }
    }

    private static final List<Fx> ACTIVE = new ArrayList<>();
    private static float clock;
    private static float shake;
    private static float shakeO;

    private HulkVfx() {
    }

    public static void reset() {
        ACTIVE.clear();
        shake = 0.0F;
    }

    private static float[][] pal(Fx f) {
        return f.red ? RED : GREEN;
    }

    private static double rnd(double s) {
        return (Math.random() - 0.5D) * s;
    }

    private static float ease(float k) {
        float u = 1.0F - Math.min(1.0F, Math.max(0.0F, k));
        return 1.0F - u * u * u;
    }

    /** The camera shakes for whoever is close (strength at the place, reach in blocks). */
    public static void quake(Minecraft mc, Vec3 at, float strength, double reach) {
        if (mc.player == null) {
            return;
        }
        double d = mc.player.position().distanceTo(at);
        if (d < reach) {
            shake = Math.min(2.5F, Math.max(shake, strength * (float) (1.0D - d / reach)));
        }
    }

    public static void fx(HeroFxPacket p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        int life = switch (p.type()) {
            case HulkFx.SMASH -> 34;
            case HulkFx.CHARGE -> 10;
            case HulkFx.ROAR -> 28;
            case HulkFx.CLAP -> 24;
            case HulkFx.THROW -> p.power() >= 2.0F ? 26 : 14;
            case HulkFx.FURY -> (int) p.power();
            case HulkFx.GAMMA_WAVE -> 44;
            case HulkFx.STOMP -> 30;
            case HulkFx.LAND -> 26;
            case HulkFx.TRANSFORM -> 32;
            case HulkFx.EXTRA_LIFE -> 46;
            case HulkFx.IMPACT -> 56;
            case HulkFx.PUNCH -> 10;
            case HulkFx.RIP -> 16;
            default -> 0;
        };
        if (life <= 0) {
            return;
        }
        if (p.type() == HulkFx.FURY) {
            // one aura per Hulk
            ACTIVE.removeIf(f -> f.type == HulkFx.FURY && f.caster == p.caster());
        }
        if (ACTIVE.size() < 500) {
            ACTIVE.add(new Fx(p, life));
        }
        boolean red = (Math.max(0, p.target()) & 1) != 0;
        ParticleOptions glowP = red ? ParticleTypes.FLAME : com.flash.registry.ModParticles.GAMMA.get();
        Vec3 a = p.from();
        switch (p.type()) {
            case HulkFx.SMASH, HulkFx.LAND, HulkFx.STOMP -> {
                double r = p.type() == HulkFx.STOMP ? p.power() : p.type() == HulkFx.LAND ? p.power() : 3.0D;
                int n = p.type() == HulkFx.SMASH ? 46 : 30;
                for (int i = 0; i < n; i++) {
                    double th = Math.random() * Math.PI * 2.0D;
                    double sp = 0.25D + Math.random() * 0.45D;
                    double rr = p.type() == HulkFx.STOMP ? r : Math.random() * 1.5D;
                    mc.level.addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, a.x + Math.cos(th) * rr, a.y + 0.2D,
                            a.z + Math.sin(th) * rr, Math.cos(th) * sp * 0.4D, 0.02D + Math.random() * 0.06D,
                            Math.sin(th) * sp * 0.4D);
                    if (i % 2 == 0) {
                        mc.level.addParticle(glowP, a.x + Math.cos(th) * rr, a.y + 0.3D, a.z + Math.sin(th) * rr,
                                Math.cos(th) * sp * 0.3D, 0.1D + Math.random() * 0.2D, Math.sin(th) * sp * 0.3D);
                    }
                }
                quake(mc, a, p.type() == HulkFx.SMASH ? 1.6F : p.type() == HulkFx.STOMP ? 1.2F : 1.0F, 28.0D);
            }
            case HulkFx.ROAR -> quake(mc, a, 0.9F, 20.0D);
            case HulkFx.CLAP -> {
                for (int i = 0; i < 30; i++) {
                    Vec3 d = p.to().scale(0.5D + Math.random() * 1.6D).add(rnd(0.6D), rnd(0.6D), rnd(0.6D));
                    mc.level.addParticle(ParticleTypes.CLOUD, a.x, a.y, a.z, d.x, d.y, d.z);
                }
                quake(mc, a, 1.4F, 26.0D);
            }
            case HulkFx.GAMMA_WAVE, HulkFx.IMPACT -> {
                for (int i = 0; i < 120; i++) {
                    double th = Math.random() * Math.PI * 2.0D;
                    double sp = 0.4D + Math.random() * 1.2D;
                    mc.level.addParticle(glowP, a.x, a.y + 0.8D, a.z, Math.cos(th) * sp, Math.random() * 0.5D,
                            Math.sin(th) * sp);
                    if (i % 3 == 0) {
                        mc.level.addParticle(ParticleTypes.LARGE_SMOKE, a.x, a.y + 0.5D, a.z, Math.cos(th) * sp * 0.5D,
                                0.05D + Math.random() * 0.15D, Math.sin(th) * sp * 0.5D);
                    }
                }
                quake(mc, a, 2.2F, 40.0D);
            }
            case HulkFx.PUNCH -> {
                for (int i = 0; i < 10; i++) {
                    mc.level.addParticle(glowP, a.x, a.y, a.z, rnd(0.5D), rnd(0.5D), rnd(0.5D));
                }
                quake(mc, a, 0.35F + 0.4F * p.power(), 8.0D);
            }
            case HulkFx.TRANSFORM -> {
                for (int i = 0; i < 40; i++) {
                    mc.level.addParticle(glowP, a.x + rnd(1.5D), a.y + Math.random() * 2.5D, a.z + rnd(1.5D),
                            rnd(0.2D), 0.05D + Math.random() * 0.1D, rnd(0.2D));
                }
                if (p.power() > 0.0F) {
                    quake(mc, a, 0.8F, 16.0D);
                }
            }
            case HulkFx.CHARGE -> {
                if (((int) p.power()) % 3 == 1) {
                    quake(mc, a, 0.45F, 12.0D);
                }
            }
            default -> {
            }
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.phase != TickEvent.Phase.END || mc.level == null || mc.isPaused()) {
            return;
        }
        clock += 1.0F;
        shakeO = shake;
        shake *= 0.82F;
        if (shake < 0.01F) {
            shake = 0.0F;
        }
        for (Iterator<Fx> it = ACTIVE.iterator(); it.hasNext(); ) {
            Fx f = it.next();
            f.age += 1.0F;
            if (f.age > f.life) {
                it.remove();
                continue;
            }
            if (f.type == HulkFx.FURY) {
                Entity e = mc.level.getEntity(f.caster);
                if (e == null || !e.isAlive()) {
                    it.remove();
                } else if (((int) f.age) % 2 == 0) {
                    // embers rising off the raging body
                    double w = e.getBbWidth() * 0.5D;
                    mc.level.addParticle(f.red ? ParticleTypes.FLAME : com.flash.registry.ModParticles.GAMMA.get(),
                            e.getX() + rnd(w * 2.0D), e.getY() + Math.random() * e.getBbHeight(),
                            e.getZ() + rnd(w * 2.0D), 0.0D, 0.06D, 0.0D);
                }
            }
        }
    }

    /** The shaking camera near the blows. */
    @SubscribeEvent
    public static void onCamera(ViewportEvent.ComputeCameraAngles event) {
        float s = shakeO + (shake - shakeO) * (float) event.getPartialTick();
        if (s <= 0.0F) {
            return;
        }
        float t = clock + (float) event.getPartialTick();
        event.setPitch(event.getPitch() + (float) Math.sin(t * 2.7D) * s * 1.1F);
        event.setYaw(event.getYaw() + (float) Math.sin(t * 3.3D + 1.0D) * s * 0.8F);
        event.setRoll(event.getRoll() + (float) Math.sin(t * 2.1D + 2.0D) * s * 0.7F);
    }

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || ACTIVE.isEmpty()) {
            return;
        }
        Camera camera = event.getCamera();
        Vec3 cam = camera.getPosition();
        Vec3 left = new Vec3(camera.getLeftVector());
        Vec3 up = new Vec3(camera.getUpVector());
        float pt = event.getPartialTick();
        float t = clock + pt;
        PoseStack ps = event.getPoseStack();
        ps.pushPose();
        ps.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f m = ps.last().pose();
        BufferBuilder buf = VfxDraw.begin();
        try {
            for (Fx f : ACTIVE) {
                draw(buf, m, mc, f, pt, t, cam, left, up);
            }
        } finally {
            VfxDraw.end(buf);
            ps.popPose();
        }
    }

    private static Entity caster(Minecraft mc, Fx f) {
        return f.caster >= 0 ? mc.level.getEntity(f.caster) : null;
    }

    /** Glowing cracks out of c (count, length), grown over the first ticks, fading at the end. */
    private static void cracks(BufferBuilder buf, Matrix4f m, Fx f, Vec3 c, int count, double len, float age,
                               float grow, float fade, double w) {
        float[][] cl = pal(f);
        java.util.Random rnd = new java.util.Random(f.seed);
        for (int i = 0; i < count; i++) {
            double ang = Math.PI * 2.0D * i / count + rnd.nextDouble() * 0.5D;
            double l = len * (0.6D + rnd.nextDouble() * 0.6D);
            float g = Math.min(1.0F, age / grow);
            VfxDraw.crack(buf, m, c, ang, l, f.seed + i * 131L, g, w, cl[0][0], cl[0][1], cl[0][2], fade);
        }
    }

    private static void draw(BufferBuilder buf, Matrix4f m, Minecraft mc, Fx f, float pt, float t, Vec3 cam,
                             Vec3 left, Vec3 up) {
        float age = f.age + pt;
        float k = Math.min(1.0F, age / Math.max(1, f.life));
        float fade = 1.0F - k;
        float[][] c = pal(f);
        float[] deep = c[0];
        float[] hot = c[1];
        float[] white = c[2];
        Vec3 vup = new Vec3(0.0D, 1.0D, 0.0D);
        switch (f.type) {
            case HulkFx.SMASH -> {
                Vec3 g = f.a;
                float lv = Math.max(1.0F, f.power) / 10.0F;
                double reach = 6.0D + 6.0D * lv;
                // the blinding moment of the blow
                float flash = Math.max(0.0F, 1.0F - age / 6.0F);
                VfxDraw.flare(buf, m, g.add(0.0D, 0.6D, 0.0D), left, up, 2.2D + 1.5D * lv, hot[0], hot[1], hot[2],
                        flash);
                VfxDraw.sphere(buf, m, g, 0.4D + reach * 0.35D * ease(k * 2.2F), cam, hot[0], hot[1], hot[2],
                        0.55F * Math.max(0.0F, 1.0F - k * 2.2F), t, 2.2D);
                // three rings of shock running over the ground, one after the other
                for (int w = 0; w < 3; w++) {
                    float kw = Math.max(0.0F, (age - w * 3.5F) / (f.life * 0.65F));
                    if (kw > 0.0F && kw < 1.0F) {
                        VfxDraw.shockwave(buf, m, g, 0.6D + reach * ease(kw), 1.4D * (1.0D - kw), w == 0 ? hot[0]
                                : deep[0], w == 0 ? hot[1] : deep[1], w == 0 ? hot[2] : deep[2], (1.0F - kw)
                                * (w == 0 ? 1.0F : 0.6F));
                    }
                }
                cracks(buf, m, f, g, 11, reach * 0.75D, age, 6.0F, Math.min(1.0F, fade * 2.2F), 0.07D + 0.05D * lv);
                VfxDraw.glow(buf, m, g.add(0.0D, 0.05D, 0.0D), new Vec3(1.0D, 0.0D, 0.0D), new Vec3(0.0D, 0.0D, 1.0D),
                        2.5D + reach * 0.3D, deep[0], deep[1], deep[2], 0.5F * fade);
                VfxDraw.sparks(buf, m, g.add(0.0D, 0.3D, 0.0D), f.seed, 42, 5.0D + 4.0D * lv, age / 20.0F, 2.5D,
                        hot[0], hot[1], hot[2], 1.0F);
            }
            case HulkFx.CHARGE -> {
                Entity e = caster(mc, f);
                Vec3 base = e != null ? e.getPosition(pt) : f.a;
                double h = e != null ? e.getBbHeight() : 2.5D;
                Vec3 back = f.b.scale(-1.0D);
                Vec3 mid = base.add(0.0D, h * 0.5D, 0.0D);
                // speed lines streaming off the body
                java.util.Random rnd = new java.util.Random(f.seed);
                for (int i = 0; i < 9; i++) {
                    Vec3 o = mid.add(rnd.nextDouble() - 0.5D, (rnd.nextDouble() - 0.5D) * h, rnd.nextDouble() - 0.5D);
                    double len = 2.0D + rnd.nextDouble() * 3.0D;
                    VfxDraw.beam(buf, m, o.add(back.scale(0.4D)), o.add(back.scale(0.4D + len)), 0.06F, hot[0],
                            hot[1], hot[2], 0.5F * fade);
                }
                // the air crushed in front, the ground shaking under the feet
                VfxDraw.ring(buf, m, mid.add(f.b.scale(0.9D)), f.b, 0.9D + 0.8D * k, 0.12D, t * 0.2F, white[0],
                        white[1], white[2], 0.35F * fade);
                VfxDraw.shockwave(buf, m, f.a, 0.6D + 2.2D * ease(k), 0.6D * fade, deep[0], deep[1], deep[2],
                        0.55F * fade);
                VfxDraw.glow(buf, m, mid, left, up, 1.8D, deep[0], deep[1], deep[2], 0.18F * fade);
            }
            case HulkFx.ROAR -> {
                Vec3 look = f.b.normalize();
                float open = Math.max(0.0F, 1.0F - age / 8.0F);
                VfxDraw.flare(buf, m, f.a.add(look.scale(0.5D)), left, up, 1.1D, hot[0], hot[1], hot[2], open);
                // walls of sound flying out of the mouth
                for (int i = 0; i < 6; i++) {
                    double d = age * 1.1D - i * 2.2D;
                    if (d <= 0.2D || d > f.power) {
                        continue;
                    }
                    float a = (float) (1.0D - d / f.power);
                    VfxDraw.ring(buf, m, f.a.add(look.scale(d)), look, 0.5D + d * 0.42D, 0.07D + d * 0.02D,
                            t * 0.1F, white[0], white[1], white[2], 0.6F * a);
                    VfxDraw.ring(buf, m, f.a.add(look.scale(d)), look, 0.4D + d * 0.4D, 0.25D + d * 0.05D, 0.0F,
                            hot[0], hot[1], hot[2], 0.25F * a);
                }
                VfxDraw.cone(buf, m, f.a, look, Math.min(f.power, age * 1.2D), 0.6D + Math.min(f.power, age * 1.2D)
                        * 0.4D, hot[0], hot[1], hot[2], 0.12F * fade);
                // the ground blown away all around
                Entity e = caster(mc, f);
                Vec3 feet = e != null ? e.getPosition(pt) : f.a.subtract(0.0D, 2.0D, 0.0D);
                VfxDraw.shockwave(buf, m, feet, 1.0D + 11.0D * ease(k), 0.9D * fade, deep[0], deep[1], deep[2],
                        0.55F * fade);
            }
            case HulkFx.CLAP -> {
                Vec3 look = f.b.normalize();
                float flash = Math.max(0.0F, 1.0F - age / 5.0F);
                VfxDraw.flare(buf, m, f.a, left, up, 3.0D, white[0], white[1], white[2], flash);
                VfxDraw.burst(buf, m, f.a, left, up, 2.5F * flash + 0.5F, hot[0], hot[1], hot[2], flash, t * 0.1F);
                double range = Math.max(4.0D, f.power);
                for (int i = 0; i < 7; i++) {
                    double d = age * 2.4D - i * range / 7.0D;
                    if (d <= 0.3D || d > range) {
                        continue;
                    }
                    float a = (float) (1.0D - d / range);
                    Vec3 at = f.a.add(look.scale(d));
                    VfxDraw.ring(buf, m, at, look, 0.5D + d * 0.32D, 0.08D, 0.0F, white[0], white[1], white[2],
                            0.75F * a);
                    VfxDraw.ring(buf, m, at, look, 0.45D + d * 0.3D, 0.35D, 0.0F, hot[0], hot[1], hot[2], 0.3F * a);
                    VfxDraw.glow(buf, m, at, left, up, 0.4D + d * 0.18D, hot[0], hot[1], hot[2], 0.12F * a);
                }
                double reach = Math.min(range, age * 2.4D);
                VfxDraw.cone(buf, m, f.a, look, reach, 0.5D + reach * 0.32D, deep[0], deep[1], deep[2], 0.14F * fade);
                VfxDraw.sparks(buf, m, f.a, f.seed, 30, 4.0D, age / 14.0F, 1.0D, white[0], white[1], white[2], 1.0F);
            }
            case HulkFx.THROW -> {
                Vec3 look = f.b.normalize();
                boolean big = f.power >= 2.0F;
                double len = big ? 30.0D : 3.0D + 4.0D * f.power;
                Vec3 end = f.a.add(look.scale(len * ease(Math.min(1.0F, age / 4.0F))));
                VfxDraw.energy(buf, m, f.a, end, big ? 0.6F : 0.2F, hot[0], hot[1], hot[2], 0.8F * fade);
                VfxDraw.flare(buf, m, f.a, left, up, big ? 3.0D : 1.0D, hot[0], hot[1], hot[2],
                        Math.max(0.0F, 1.0F - age / 6.0F));
                for (int i = 0; i < (big ? 6 : 2); i++) {
                    double d = (age * 1.8D + i * len / 6.0D) % len;
                    VfxDraw.ring(buf, m, f.a.add(look.scale(d)), look, (big ? 1.2D : 0.5D) + d * 0.05D, 0.09D,
                            0.0F, white[0], white[1], white[2], 0.5F * fade);
                }
            }
            case HulkFx.FURY -> {
                Entity e = caster(mc, f);
                if (e == null) {
                    return;
                }
                boolean me = e == mc.getCameraEntity() && mc.options.getCameraType().isFirstPerson();
                Vec3 base = e.getPosition(pt);
                double h = e.getBbHeight();
                double w = e.getBbWidth();
                float on = Math.min(1.0F, age / 10.0F) * Math.min(1.0F, (f.life - age) / 20.0F);
                float pulse = 0.75F + 0.25F * (float) Math.sin(t * 0.45D);
                // the burst when it starts
                if (age < 14.0F) {
                    float k0 = age / 14.0F;
                    VfxDraw.shockwave(buf, m, base, 0.5D + 7.0D * ease(k0), 2.0D * (1.0F - k0), hot[0], hot[1], hot[2],
                            1.0F - k0);
                    if (!me) {
                        VfxDraw.flare(buf, m, base.add(0.0D, h * 0.6D, 0.0D), left, up, 2.5D, hot[0], hot[1], hot[2],
                                1.0F - k0);
                    }
                }
                // tongues of gamma fire round the body
                int tongues = 12;
                for (int i = 0; i < tongues; i++) {
                    double ang = Math.PI * 2.0D * i / tongues + t * 0.03D;
                    double rr = w * (0.55D + 0.1D * Math.sin(t * 0.2D + i));
                    Vec3 at = base.add(Math.cos(ang) * rr, h * (0.05D + 0.08D * (i % 3)), Math.sin(ang) * rr);
                    double fh = h * (0.55D + 0.25D * Math.sin(t * 0.3D + i * 1.7D));
                    VfxDraw.flame(buf, m, at, fh, w * 0.22D, cam, t + i * 7.0F, deep[0], deep[1], deep[2],
                            0.55F * on * pulse);
                    VfxDraw.flame(buf, m, at, fh * 0.6D, w * 0.1D, cam, t + i * 5.0F, hot[0], hot[1], hot[2],
                            0.6F * on);
                }
                if (!me) {
                    VfxDraw.glow(buf, m, base.add(0.0D, h * 0.55D, 0.0D), left, up, h * 0.75D, deep[0], deep[1],
                            deep[2], 0.2F * on * pulse);
                    // burning eyes
                    Vec3 look = e.getViewVector(pt);
                    Vec3 side = new Vec3(-look.z, 0.0D, look.x).normalize().scale(w * 0.1D);
                    Vec3 eye = e.getEyePosition(pt).add(look.scale(w * 0.32D));
                    VfxDraw.flare(buf, m, eye.add(side), left, up, 0.22D * w, hot[0], hot[1], hot[2], on);
                    VfxDraw.flare(buf, m, eye.subtract(side), left, up, 0.22D * w, hot[0], hot[1], hot[2], on);
                }
                // arcs of gamma crackling over the body
                long tick = (long) (t / 2.0F);
                java.util.Random rnd = new java.util.Random(f.seed + tick);
                for (int i = 0; i < 3; i++) {
                    Vec3 p0 = base.add((rnd.nextDouble() - 0.5D) * w * 1.2D, rnd.nextDouble() * h,
                            (rnd.nextDouble() - 0.5D) * w * 1.2D);
                    Vec3 p1 = p0.add((rnd.nextDouble() - 0.5D) * w, (rnd.nextDouble() - 0.3D) * h * 0.4D,
                            (rnd.nextDouble() - 0.5D) * w);
                    VfxDraw.path(buf, m, VfxDraw.jagged(p0, p1, rnd.nextLong(), 1.4D, 3), 0.04F, white[0], white[1],
                            white[2], 0.7F * on);
                }
                VfxDraw.ring(buf, m, base.add(0.0D, 0.05D, 0.0D), vup, w * 1.1D, 0.12D, t * 0.06F, hot[0], hot[1],
                        hot[2], 0.45F * on * pulse);
            }
            case HulkFx.GAMMA_WAVE -> {
                double rad = Math.max(4.0D, f.power);
                Vec3 c0 = f.a.add(0.0D, 1.0D, 0.0D);
                float flash = Math.max(0.0F, 1.0F - age / 7.0F);
                VfxDraw.flare(buf, m, c0, left, up, 4.5D, hot[0], hot[1], hot[2], flash);
                float kk = Math.min(1.0F, age / 22.0F);
                VfxDraw.sphere(buf, m, c0, rad * ease(kk), cam, deep[0], deep[1], deep[2], 0.75F * (1.0F - kk), t,
                        2.0D);
                VfxDraw.sphere(buf, m, c0, rad * 0.6D * ease(kk), cam, white[0], white[1], white[2],
                        0.35F * (1.0F - kk), t, 3.0D);
                VfxDraw.shockwave(buf, m, f.a, rad * ease(kk), 2.2D * (1.0F - kk), hot[0], hot[1], hot[2], 1.0F - kk);
                float k2 = Math.max(0.0F, (age - 6.0F) / 24.0F);
                if (k2 < 1.0F) {
                    VfxDraw.shockwave(buf, m, f.a, rad * 1.3D * ease(k2), 1.0D * (1.0F - k2), deep[0], deep[1],
                            deep[2], 0.6F * (1.0F - k2));
                }
                // a pillar of gamma into the sky
                VfxDraw.pillar(buf, m, f.a, f.a.y, f.a.y + 16.0D, cam, (float) (1.4D * fade + 0.2D), hot[0], hot[1],
                        hot[2], 0.6F * fade);
                VfxDraw.pillar(buf, m, f.a, f.a.y, f.a.y + 10.0D, cam, (float) (0.4D * fade + 0.05D), white[0],
                        white[1], white[2], 0.8F * fade);
                // lightning out to the edge of the wave
                java.util.Random rnd = new java.util.Random(f.seed + (long) (age / 2.0F));
                for (int i = 0; i < 14; i++) {
                    double ang = rnd.nextDouble() * Math.PI * 2.0D;
                    double d = rad * ease(kk) * (0.6D + rnd.nextDouble() * 0.4D);
                    Vec3 tip = f.a.add(Math.cos(ang) * d, 0.2D + rnd.nextDouble() * 2.5D, Math.sin(ang) * d);
                    VfxDraw.path(buf, m, VfxDraw.jagged(c0, tip, rnd.nextLong(), 1.0D, 4), 0.08F, hot[0], hot[1],
                            hot[2], 0.8F * fade);
                    VfxDraw.path(buf, m, VfxDraw.jagged(c0, tip, rnd.nextLong(), 0.6D, 3), 0.03F, white[0], white[1],
                            white[2], fade);
                }
                cracks(buf, m, f, f.a, 16, rad * 0.9D, age, 10.0F, fade, 0.1D);
                VfxDraw.sparks(buf, m, c0, f.seed, 70, rad * 0.9D, age / 26.0F, 2.0D, hot[0], hot[1], hot[2], 1.0F);
            }
            case HulkFx.STOMP -> {
                double r = Math.max(1.0D, f.power);
                float lift = 1.0F + f.extra * 0.12F;
                float kk = Math.min(1.0F, age / 14.0F);
                VfxDraw.shockwave(buf, m, f.a, (r - 1.5D) + 3.0D * ease(kk), 2.4D * lift * (1.0F - kk), hot[0], hot[1],
                        hot[2], 1.0F - kk);
                VfxDraw.shockwave(buf, m, f.a, r * 0.5D * ease(kk), 0.6D * (1.0F - kk), deep[0], deep[1], deep[2],
                        0.5F * (1.0F - kk));
                cracks(buf, m, f, f.a, 6 + f.extra * 2, r, age, 5.0F, fade, 0.08D);
                // columns of light where the ring breaks the ground
                int n = 6 + f.extra;
                for (int i = 0; i < n; i++) {
                    double ang = Math.PI * 2.0D * i / n + f.extra * 0.4D;
                    Vec3 at = f.a.add(Math.cos(ang) * r, 0.0D, Math.sin(ang) * r);
                    double hh = (2.5D + f.extra * 0.4D) * ease(Math.min(1.0F, age / 4.0F)) * fade;
                    VfxDraw.pillar(buf, m, at, at.y, at.y + hh, cam, 0.5F, hot[0], hot[1], hot[2], 0.7F * fade);
                    VfxDraw.glow(buf, m, at.add(0.0D, 0.4D, 0.0D), left, up, 1.0D, deep[0], deep[1], deep[2],
                            0.35F * fade);
                }
                VfxDraw.flare(buf, m, f.a.add(0.0D, 0.5D, 0.0D), left, up, 1.6D + f.extra * 0.2D, hot[0], hot[1],
                        hot[2], Math.max(0.0F, 1.0F - age / 5.0F));
            }
            case HulkFx.LAND -> {
                double r = Math.max(2.0D, f.power);
                VfxDraw.shockwave(buf, m, f.a, 0.5D + r * 1.2D * ease(k), 1.4D * fade, hot[0], hot[1], hot[2], fade);
                VfxDraw.shockwave(buf, m, f.a, 0.3D + r * 0.7D * ease(k), 0.5D * fade, deep[0], deep[1], deep[2],
                        0.6F * fade);
                cracks(buf, m, f, f.a, 7, r * 0.8D, age, 4.0F, fade, 0.07D);
                VfxDraw.flare(buf, m, f.a.add(0.0D, 0.3D, 0.0D), left, up, 1.2D + r * 0.15D, hot[0], hot[1], hot[2],
                        Math.max(0.0F, 1.0F - age / 5.0F));
                VfxDraw.sparks(buf, m, f.a.add(0.0D, 0.2D, 0.0D), f.seed, 24, 2.0D + r * 0.5D, age / 16.0F, 1.5D,
                        hot[0], hot[1], hot[2], 1.0F);
            }
            case HulkFx.TRANSFORM -> {
                Entity e = caster(mc, f);
                Vec3 base = e != null ? e.getPosition(pt) : f.a;
                double h = e != null ? e.getBbHeight() : 2.0D;
                boolean grow = f.power > 0.0F;
                boolean me = e == mc.getCameraEntity() && mc.options.getCameraType().isFirstPerson();
                Vec3 mid = base.add(0.0D, h * 0.5D, 0.0D);
                if (!me) {
                    VfxDraw.flare(buf, m, mid, left, up, grow ? 2.4D : 1.2D, hot[0], hot[1], hot[2],
                            Math.max(0.0F, 1.0F - age / 8.0F));
                }
                VfxDraw.sphere(buf, m, mid, h * (0.5D + 0.6D * ease(k)), cam, deep[0], deep[1], deep[2],
                        (grow ? 0.6F : 0.3F) * fade, t, 2.0D);
                if (grow) {
                    VfxDraw.shockwave(buf, m, base, 0.5D + 5.0D * ease(k), 1.6D * fade, hot[0], hot[1], hot[2], fade);
                    java.util.Random rnd = new java.util.Random(f.seed + (long) (age / 2.0F));
                    for (int i = 0; i < 5; i++) {
                        Vec3 p0 = mid.add((rnd.nextDouble() - 0.5D) * h * 0.6D, (rnd.nextDouble() - 0.5D) * h,
                                (rnd.nextDouble() - 0.5D) * h * 0.6D);
                        Vec3 p1 = p0.add((rnd.nextDouble() - 0.5D) * h, (rnd.nextDouble() - 0.5D) * h * 0.6D,
                                (rnd.nextDouble() - 0.5D) * h);
                        VfxDraw.path(buf, m, VfxDraw.jagged(p0, p1, rnd.nextLong(), 1.2D, 3), 0.05F, white[0],
                                white[1], white[2], 0.85F * fade);
                    }
                    for (int i = 0; i < 10; i++) {
                        double ang = Math.PI * 2.0D * i / 10.0D;
                        Vec3 at = base.add(Math.cos(ang) * h * 0.35D, 0.0D, Math.sin(ang) * h * 0.35D);
                        VfxDraw.flame(buf, m, at, h * (0.8D + 0.3D * Math.sin(i + t * 0.3D)) * fade, h * 0.09D, cam,
                                t + i * 9.0F, deep[0], deep[1], deep[2], 0.7F * fade);
                    }
                }
            }
            case HulkFx.EXTRA_LIFE -> {
                Entity e = caster(mc, f);
                Vec3 base = e != null ? e.getPosition(pt) : f.a;
                float[] gold = {1.0F, 0.85F, 0.3F};
                VfxDraw.pillar(buf, m, base, base.y, base.y + 24.0D, cam, (float) (1.8D * fade), gold[0], gold[1],
                        gold[2], 0.6F * fade);
                VfxDraw.pillar(buf, m, base, base.y, base.y + 18.0D, cam, (float) (0.5D * fade), white[0], white[1],
                        white[2], 0.9F * fade);
                for (int i = 0; i < 5; i++) {
                    double y = ((age * 0.25D + i * 1.2D) % 6.0D);
                    VfxDraw.ring(buf, m, base.add(0.0D, y, 0.0D), vup, 1.6D - y * 0.15D, 0.08D, t * 0.1F, hot[0],
                            hot[1], hot[2], 0.7F * fade * (float) (1.0D - y / 6.0D));
                }
                VfxDraw.shockwave(buf, m, base, 0.5D + 6.0D * ease(k), 1.2D * fade, gold[0], gold[1], gold[2], fade);
                VfxDraw.flare(buf, m, base.add(0.0D, 1.2D, 0.0D), left, up, 2.5D, gold[0], gold[1], gold[2],
                        Math.max(0.0F, 1.0F - age / 10.0F));
            }
            case HulkFx.IMPACT -> {
                double rad = Math.max(4.0D, f.power);
                Vec3 c0 = f.a.add(0.0D, 0.8D, 0.0D);
                VfxDraw.flare(buf, m, c0, left, up, 6.0D, white[0], white[1], white[2],
                        Math.max(0.0F, 1.0F - age / 9.0F));
                float kk = Math.min(1.0F, age / 26.0F);
                VfxDraw.sphere(buf, m, c0, rad * 0.8D * ease(kk), cam, hot[0], hot[1], hot[2], 0.8F * (1.0F - kk), t,
                        1.8D);
                VfxDraw.shockwave(buf, m, f.a, rad * 1.4D * ease(kk), 3.0D * (1.0F - kk), hot[0], hot[1], hot[2],
                        1.0F - kk);
                float k2 = Math.max(0.0F, (age - 5.0F) / 30.0F);
                if (k2 < 1.0F) {
                    VfxDraw.shockwave(buf, m, f.a, rad * 2.0D * ease(k2), 1.2D * (1.0F - k2), deep[0], deep[1],
                            deep[2], 0.55F * (1.0F - k2));
                }
                // a mushroom of light: the column and its rising cap
                double top = 4.0D + 14.0D * ease(k);
                VfxDraw.pillar(buf, m, f.a, f.a.y, f.a.y + top, cam, (float) (rad * 0.25D * fade + 0.3D), hot[0],
                        hot[1], hot[2], 0.55F * fade);
                VfxDraw.ring(buf, m, f.a.add(0.0D, top, 0.0D), vup, rad * 0.4D + rad * 0.4D * ease(k), rad * 0.15D,
                        t * 0.05F, deep[0], deep[1], deep[2], 0.45F * fade);
                VfxDraw.glow(buf, m, f.a.add(0.0D, top, 0.0D), left, up, rad * 0.6D, hot[0], hot[1], hot[2],
                        0.3F * fade);
                cracks(buf, m, f, f.a, 18, rad * 1.1D, age, 8.0F, fade, 0.14D);
                VfxDraw.sparks(buf, m, c0, f.seed, 90, rad * 1.2D, age / 30.0F, 3.0D, hot[0], hot[1], hot[2], 1.0F);
            }
            case HulkFx.PUNCH -> {
                Vec3 way = f.b.lengthSqr() < 1.0E-6D ? new Vec3(0.0D, 0.0D, 1.0D) : f.b.normalize();
                float pw = 0.6F + f.power;
                VfxDraw.flare(buf, m, f.a, left, up, 0.7D * pw, hot[0], hot[1], hot[2], fade);
                VfxDraw.burst(buf, m, f.a, left, up, 1.4F * pw * (0.4F + 0.6F * ease(k)), hot[0], hot[1], hot[2],
                        fade, (float) (f.seed % 7L));
                VfxDraw.ring(buf, m, f.a.add(way.scale(0.3D * k)), way, 0.3D + 1.6D * pw * ease(k), 0.08D, 0.0F,
                        white[0], white[1], white[2], 0.8F * fade);
                VfxDraw.ring(buf, m, f.a.add(way.scale(0.6D * k)), way, 0.2D + 1.0D * pw * ease(k), 0.25D, 0.0F,
                        deep[0], deep[1], deep[2], 0.35F * fade);
                VfxDraw.sparks(buf, m, f.a, f.seed, 16, 1.6D * pw, k, 0.6D, hot[0], hot[1], hot[2], 1.0F);
            }
            case HulkFx.RIP -> {
                cracks(buf, m, f, f.a, 6, 2.2D, age, 3.0F, fade, 0.06D);
                VfxDraw.flare(buf, m, f.a.add(0.0D, 0.3D, 0.0D), left, up, 0.9D, hot[0], hot[1], hot[2],
                        Math.max(0.0F, 1.0F - age / 5.0F));
                VfxDraw.sparks(buf, m, f.a, f.seed, 14, 1.5D, age / 12.0F, 1.0D, hot[0], hot[1], hot[2], 1.0F);
            }
            default -> {
            }
        }
    }
}
