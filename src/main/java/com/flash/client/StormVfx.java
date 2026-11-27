package com.flash.client;

import com.flash.FlashMod;
import com.flash.network.StormFxPacket;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/**
 * Storm's visuals built of light (VfxDraw: the Lanterns' glows, rays and spheres, God Wand's shockwave over the
 * screen), around the Shazam lightning StormClient still draws:
 * <ul>
 * <li>a bolt from the sky: a glow along it, a flare and a ring of force where it lands, a sphere of light, the
 * screen shaken by the shockwave; arcs and her stream: a soft glow along, flares at the ends;</li>
 * <li>her eyes and body lighting up when she calls on the storm: a ring of lightning-blue light round her;</li>
 * <li>a gust: streaks of wind and rings of air rushing ahead of her;</li>
 * <li>her tornado: a funnel of spinning bands of air with rings climbing it and dust on the ground; her flight:
 * a small funnel of the same under her feet;</li>
 * <li>hail: streaks of ice falling in a frosted circle; frost: a ring of ice light running out from her with
 * crystals on it;</li>
 * <li>taking off: a ring of wind and a gust upwards; the power coming into her: a pillar and a burst of
 * lightning-blue light.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = FlashMod.MODID, value = Dist.CLIENT)
public final class StormVfx {

    // lightning blue, ice and wind
    private static final float[] BLUE = {0.45F, 0.75F, 1.0F};
    private static final float[] ICE = {0.65F, 0.9F, 1.0F};
    private static final float[] WIND = {0.85F, 0.9F, 0.95F};

    private static final class Fx {
        final int type;
        final int caster;
        Vec3 a;
        Vec3 b;
        final float power;
        final int life;
        final long seed;
        int age;

        Fx(int type, int caster, Vec3 a, Vec3 b, float power, int life, long seed) {
            this.type = type;
            this.caster = caster;
            this.a = a;
            this.b = b;
            this.power = power;
            this.life = life;
            this.seed = seed;
        }
    }

    private static final List<Fx> ACTIVE = new ArrayList<>();
    private static int clock;

    private StormVfx() {
    }

    private static int life(StormFxPacket p) {
        return switch (p.type()) {
            case StormFxPacket.SKY -> 16;
            case StormFxPacket.ARC -> 6;
            case StormFxPacket.BEAM -> 4;
            case StormFxPacket.CAST -> 24;
            case StormFxPacket.GUST -> 14;
            case StormFxPacket.TORNADO -> 6;
            case StormFxPacket.HAIL -> Math.max(10, (int) p.power());
            case StormFxPacket.FROST -> 24;
            case StormFxPacket.TAKEOFF -> 18;
            case StormFxPacket.CALL -> 40;
            default -> 0;
        };
    }

    public static void spawn(StormFxPacket p) {
        int life = life(p);
        if (life <= 0) {
            return;
        }
        if (p.type() == StormFxPacket.TORNADO || p.type() == StormFxPacket.BEAM || p.type() == StormFxPacket.CAST) {
            for (Fx f : ACTIVE) {
                if (f.type == p.type() && f.caster == p.caster()
                        && (p.type() != StormFxPacket.TORNADO || f.a.distanceToSqr(p.from()) < 9.0D)) {
                    f.a = p.from();
                    f.b = p.to();
                    f.age = Math.min(f.age, 1);
                    return;
                }
            }
        }
        if (ACTIVE.size() > 120) {
            ACTIVE.remove(0);
        }
        ACTIVE.add(new Fx(p.type(), p.caster(), p.from(), p.to(), p.power(), life, p.seed()));
        if (p.type() == StormFxPacket.SKY && p.power() >= 1.0F || p.type() == StormFxPacket.CALL) {
            Vec3 at = p.to();
            Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
            if (cam.distanceToSqr(at) < 80.0D * 80.0D) {
                com.flash.client.godwand.GodWandPostFxManager.triggerImpact(at.x, at.y + 0.5D, at.z, 2, 14,
                        Math.min(1.6F, 0.8F + p.power() * 0.3F));
            }
        }
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            ACTIVE.clear();
            return;
        }
        if (mc.isPaused()) {
            return;
        }
        clock++;
        for (Iterator<Fx> it = ACTIVE.iterator(); it.hasNext(); ) {
            Fx f = it.next();
            if (++f.age >= f.life) {
                it.remove();
            }
        }
    }

    private static boolean anyFlying(Minecraft mc) {
        for (Player p : mc.level.players()) {
            if (StormClient.flying(p) && !p.isInvisible()) {
                return true;
            }
        }
        return false;
    }

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || ACTIVE.isEmpty() && !anyFlying(mc)) {
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
            for (Player p : mc.level.players()) {
                if (StormClient.flying(p) && !p.isInvisible()) {
                    // a little tornado carrying her: its tip below, wide under her feet, lightning in it
                    FlashTornadoVfx.speedTornado(buf, m, p.getPosition(pt).add(0.0D, -2.3D, 0.0D), cam, left, up, t,
                            BLUE, 0.7F, 0.23F, 0.33D, p.getId() * 131L);
                    VfxDraw.glow(buf, m, p.getPosition(pt).add(0.0D, 0.05D, 0.0D), new Vec3(1.0D, 0.0D, 0.0D),
                            new Vec3(0.0D, 0.0D, 1.0D), 1.2D, BLUE[0], BLUE[1], BLUE[2], 0.25F);
                }
            }
        } finally {
            VfxDraw.end(buf);
            ps.popPose();
        }
    }

    private static float ease(float k) {
        float u = 1.0F - Mth.clamp(k, 0.0F, 1.0F);
        return 1.0F - u * u * u;
    }

    /** Lightning strobing as real strokes do. */
    private static float strobe(long seed, float age) {
        long h = (seed + (int) age * 0x9E3779B97F4A7C15L) >>> 40;
        return (h & 3L) == 0L ? 0.4F : 1.0F;
    }

    /** A crackling bolt of her lightning: blue glow, pale colour, white core. */
    private static void bolt(BufferBuilder buf, Matrix4f m, Vec3 a, Vec3 b, long seed, float w, float alpha) {
        if (alpha <= 0.01F) {
            return;
        }
        Vec3[] pts = VfxDraw.jagged(a, b, seed, 1.1D, 4);
        VfxDraw.path(buf, m, pts, w * 4.0F, BLUE[0], BLUE[1], BLUE[2], 0.22F * alpha);
        VfxDraw.path(buf, m, pts, w, 0.75F, 0.9F, 1.0F, 0.85F * alpha);
        VfxDraw.path(buf, m, pts, w * 0.3F, 1.0F, 1.0F, 1.0F, alpha);
    }

    private static void draw(BufferBuilder buf, Matrix4f m, Minecraft mc, Fx f, float pt, float t, Vec3 cam,
                             Vec3 left, Vec3 up) {
        float age = f.age + pt;
        float k = Mth.clamp(age / f.life, 0.0F, 1.0F);
        float fade = 1.0F - k;
        Vec3 yUp = new Vec3(0.0D, 1.0D, 0.0D);
        Vec3 a = f.a;
        Vec3 b = f.b;
        long tick = (long) age;
        switch (f.type) {
            case StormFxPacket.SKY -> {
                // a bolt out of the storm: the clouds light up, a blinding strike, the ground torn with lightning
                float big = Math.max(0.6F, f.power);
                float st = strobe(f.seed, age);
                VfxDraw.glow(buf, m, a, left, up, 10.0D * big, BLUE[0], BLUE[1], BLUE[2], 0.18F * fade * st);
                VfxDraw.beam(buf, m, a, b, 1.4F * big, BLUE[0], BLUE[1], BLUE[2], 0.16F * fade * st);
                VfxDraw.flare(buf, m, b.add(0.0D, 0.5D, 0.0D), left, up, 1.4D * big, 0.75F, 0.9F, 1.0F,
                        Math.max(0.0F, 1.0F - age / 6.0F));
                float kk = Mth.clamp(age / 10.0F, 0.0F, 1.0F);
                VfxDraw.sphere(buf, m, b.add(0.0D, 0.4D, 0.0D), 0.5D + 1.8D * big * ease(kk), cam, BLUE[0], BLUE[1],
                        BLUE[2], 0.55F * (1.0F - kk), t, 2.2D);
                VfxDraw.shockwave(buf, m, b, 0.5D + 5.5D * big * ease(k), 1.2D * fade, BLUE[0], BLUE[1], BLUE[2],
                        fade);
                Random rnd = new Random(f.seed);
                for (int i = 0; i < 7; i++) {
                    double ang = Math.PI * 2.0D * i / 7.0D + rnd.nextDouble() * 0.5D;
                    double len = (2.0D + rnd.nextDouble() * 2.5D) * big * ease(Math.min(1.0F, age / 4.0F));
                    Vec3 to = b.add(Math.cos(ang) * len, 0.05D, Math.sin(ang) * len);
                    Vec3[] pts = VfxDraw.jagged(b.add(0.0D, 0.05D, 0.0D), to, f.seed + i * 31L + tick / 2, 1.0D, 3);
                    for (int j = 0; j < pts.length; j++) {
                        pts[j] = new Vec3(pts[j].x, b.y + 0.05D + Math.abs(pts[j].y - b.y) * 0.2D, pts[j].z);
                    }
                    VfxDraw.path(buf, m, pts, 0.14F, BLUE[0], BLUE[1], BLUE[2], 0.3F * fade * st);
                    VfxDraw.path(buf, m, pts, 0.04F, 1.0F, 1.0F, 1.0F, fade * st);
                    VfxDraw.crack(buf, m, b, ang + 0.3D, len * 0.7D, f.seed + i, 1.0F, 0.05D, BLUE[0], BLUE[1],
                            BLUE[2], 0.8F * fade);
                }
                VfxDraw.sparks(buf, m, b.add(0.0D, 0.3D, 0.0D), f.seed, 26, 2.5D * big, age / 12.0F, 1.0D, 0.75F,
                        0.9F, 1.0F, 1.0F);
            }
            case StormFxPacket.ARC -> {
                VfxDraw.beam(buf, m, a, b, 0.45F, BLUE[0], BLUE[1], BLUE[2], 0.16F * fade);
                VfxDraw.flare(buf, m, b, left, up, 0.35D, 0.75F, 0.9F, 1.0F, fade);
            }
            case StormFxPacket.BEAM -> {
                // her stream: a river of lightning from the hands, branching and crackling
                float al = Mth.clamp((f.life - age) / 2.0F, 0.0F, 1.0F);
                VfxDraw.beam(buf, m, a, b, 0.6F, BLUE[0], BLUE[1], BLUE[2], 0.12F * al);
                for (int i = 0; i < 3; i++) {
                    bolt(buf, m, a, b, f.seed + i * 97L + (long) (t * 1.5F), i == 0 ? 0.08F : 0.045F, al);
                }
                Vec3[] hx = VfxDraw.helix(a, b, 0.22D, Math.max(2.0D, a.distanceTo(b) / 1.5D), t * 0.7D, 60);
                VfxDraw.path(buf, m, hx, 0.03F, 0.85F, 0.95F, 1.0F, 0.45F * al);
                VfxDraw.flare(buf, m, a, left, up, 0.45D, 0.75F, 0.9F, 1.0F, al);
                VfxDraw.flare(buf, m, b, left, up, 0.9D, 0.75F, 0.9F, 1.0F, al);
                VfxDraw.burst(buf, m, b, left, up, 1.6F, BLUE[0], BLUE[1], BLUE[2], 0.8F * al, -t * 0.4F);
                for (int g = 0; g < 2; g++) {
                    long gen = (long) (t / 4.0F) - g;
                    VfxDraw.sparks(buf, m, b, f.seed + gen * 977L, 8, 1.5D, (t - gen * 4.0F) / 8.0F, 0.6D, 0.75F, 0.9F,
                            1.0F, 1.0F);
                }
            }
            case StormFxPacket.CAST -> {
                // the goddess of the storm: her eyes white fire, lightning about her, the wind circling
                Entity e = mc.level.getEntity(f.caster);
                if (e == null) {
                    return;
                }
                Vec3 pos = e.getPosition(pt);
                float al = Mth.clamp((f.life - age) / 8.0F, 0.0F, 1.0F) * Mth.clamp(age / 3.0F, 0.0F, 1.0F);
                boolean me = e == mc.getCameraEntity() && mc.options.getCameraType().isFirstPerson();
                if (!me) {
                    Vec3 look = e.getViewVector(pt);
                    Vec3 side = new Vec3(-look.z, 0.0D, look.x).normalize().scale(0.07D);
                    Vec3 eye = e.getEyePosition(pt).add(look.scale(0.26D));
                    VfxDraw.flare(buf, m, eye.add(side), left, up, 0.14D, 0.9F, 0.96F, 1.0F, al);
                    VfxDraw.flare(buf, m, eye.subtract(side), left, up, 0.14D, 0.9F, 0.96F, 1.0F, al);
                    VfxDraw.glow(buf, m, pos.add(0.0D, 1.0D, 0.0D), left, up, 1.6D, BLUE[0], BLUE[1], BLUE[2], 0.18F * al);
                }
                Random rnd = new Random(f.seed + tick);
                for (int i = 0; i < 3; i++) {
                    Vec3 p0 = pos.add(rnd.nextGaussian() * 0.35D, 0.2D + rnd.nextDouble() * 1.7D, rnd.nextGaussian() * 0.35D);
                    bolt(buf, m, p0, p0.add(rnd.nextGaussian() * 0.6D, rnd.nextGaussian() * 0.5D, rnd.nextGaussian() * 0.6D),
                            rnd.nextLong(), 0.03F, 0.9F * al);
                }
                for (int i = 0; i < 6; i++) {
                    double ang = t * 0.25D + i * Math.PI / 3.0D;
                    double y = 0.3D + (i % 3) * 0.6D;
                    Vec3 p0 = pos.add(Math.cos(ang) * 1.1D, y, Math.sin(ang) * 1.1D);
                    Vec3 p1 = pos.add(Math.cos(ang + 0.6D) * 1.1D, y + 0.1D, Math.sin(ang + 0.6D) * 1.1D);
                    VfxDraw.beam(buf, m, p0, p1, 0.02F, WIND[0], WIND[1], WIND[2], 0.5F * al);
                }
            }
            case StormFxPacket.GUST -> {
                // a blast of wind: streaks of air and rings rushing ahead
                Vec3 dir = b.lengthSqr() < 1.0E-6D ? new Vec3(0.0D, 0.0D, 1.0D) : b.normalize();
                Random rnd = new Random(f.seed);
                double run = 2.0D + 16.0D * ease(k);
                for (int i = 0; i < 22; i++) {
                    Vec3 off = new Vec3(rnd.nextGaussian() * 0.8D, rnd.nextGaussian() * 0.5D, rnd.nextGaussian() * 0.8D);
                    double lead = run * (0.5D + 0.5D * rnd.nextDouble());
                    Vec3 head = a.add(dir.scale(lead)).add(off.scale(1.0D + lead * 0.15D));
                    Vec3 tail = head.subtract(dir.scale(2.5D + rnd.nextDouble() * 3.0D));
                    VfxDraw.beam(buf, m, tail, head, 0.03F, 1.0F, 1.0F, 1.0F, 0.45F * fade);
                    VfxDraw.beam(buf, m, tail, head, 0.14F, WIND[0], WIND[1], WIND[2], 0.12F * fade);
                }
                for (int r = 0; r < 4; r++) {
                    double d = run * (0.2D + r * 0.25D);
                    VfxDraw.ring(buf, m, a.add(dir.scale(d)), dir, 0.8D + d * 0.2D, 0.1D, (float) t, WIND[0], WIND[1],
                            WIND[2], 0.35F * fade);
                }
                VfxDraw.cone(buf, m, a, dir, run, 1.0D + run * 0.2D, WIND[0], WIND[1], WIND[2], 0.07F * fade);
            }
            case StormFxPacket.TORNADO -> {
                // her tornado: the wind's own, wide, with her lightning
                float al = Mth.clamp((f.life - age) / 3.0F, 0.0F, 1.0F);
                FlashTornadoVfx.speedTornado(buf, m, a, cam, left, up, t, BLUE, al, 1.4F, 1.6D, f.seed);
            }
            case StormFxPacket.HAIL -> {
                // hail: shards of ice falling hard into a frosted circle, glinting where they strike
                float in = Mth.clamp(age / 6.0F, 0.0F, 1.0F);
                float al = in * Mth.clamp((f.life - age) / 10.0F, 0.0F, 1.0F);
                Vec3 g = a.add(0.0D, 0.06D, 0.0D);
                VfxDraw.ring(buf, m, g, yUp, 4.2D, 0.12D, t * 0.02F, ICE[0], ICE[1], ICE[2], 0.7F * al);
                VfxDraw.ring(buf, m, g, yUp, 3.6D, 0.4D, 0.0F, ICE[0], ICE[1], ICE[2], 0.2F * al);
                VfxDraw.glow(buf, m, g, new Vec3(1.0D, 0.0D, 0.0D), new Vec3(0.0D, 0.0D, 1.0D), 4.4D, ICE[0], ICE[1],
                        ICE[2], 0.22F * al);
                VfxDraw.glow(buf, m, a.add(0.0D, 10.0D, 0.0D), left, up, 5.0D, 0.6F, 0.7F, 0.85F, 0.12F * al);
                Random rnd = new Random(f.seed);
                for (int i = 0; i < 40; i++) {
                    double x = (rnd.nextDouble() - 0.5D) * 8.0D;
                    double z = (rnd.nextDouble() - 0.5D) * 8.0D;
                    double ph = ((age * (0.08D + rnd.nextDouble() * 0.04D) + rnd.nextDouble()) % 1.0D);
                    double y = 10.0D * (1.0D - ph);
                    Vec3 head = a.add(x, y, z);
                    VfxDraw.beam(buf, m, head.add(0.0D, 1.6D, 0.0D), head, 0.05F, ICE[0], ICE[1], ICE[2], 0.7F * al);
                    VfxDraw.beam(buf, m, head.add(0.0D, 0.5D, 0.0D), head, 0.02F, 1.0F, 1.0F, 1.0F, 0.9F * al);
                    if (ph > 0.9D) {
                        VfxDraw.flare(buf, m, a.add(x, 0.15D, z), left, up, 0.12D, ICE[0], ICE[1], ICE[2], al);
                    }
                }
            }
            case StormFxPacket.FROST -> {
                // the frost running out of her: a ring of ice light, shards of ice standing up on it, glints
                double r = Math.max(2.0D, f.power) * ease(k);
                Vec3 c = a.add(0.0D, 0.06D, 0.0D);
                VfxDraw.shockwave(buf, m, a, Math.max(0.3D, r), 1.0D * fade, ICE[0], ICE[1], ICE[2], fade);
                VfxDraw.glow(buf, m, c, new Vec3(1.0D, 0.0D, 0.0D), new Vec3(0.0D, 0.0D, 1.0D), r, ICE[0], ICE[1],
                        ICE[2], 0.2F * fade);
                Random rnd = new Random(f.seed);
                for (int i = 0; i < 30; i++) {
                    double ang = rnd.nextDouble() * Math.PI * 2.0D;
                    double d = r * (0.8D + rnd.nextDouble() * 0.2D);
                    Vec3 base = c.add(Math.cos(ang) * d, 0.0D, Math.sin(ang) * d);
                    Vec3 tip = base.add(Math.cos(ang) * 0.25D, 0.5D + rnd.nextDouble() * 1.0D, Math.sin(ang) * 0.25D);
                    VfxDraw.energy(buf, m, base, tip, 0.07F, ICE[0], ICE[1], ICE[2], 0.85F * fade);
                    if (i % 3 == 0) {
                        VfxDraw.flare(buf, m, tip, left, up, 0.1D, 0.9F, 0.97F, 1.0F, fade);
                    }
                }
                for (int i = 0; i < 8; i++) {
                    VfxDraw.crack(buf, m, a, Math.PI * 2.0D * i / 8.0D, r * 0.9D, f.seed + i, 1.0F, 0.05D, ICE[0],
                            ICE[1], ICE[2], 0.7F * fade);
                }
            }
            case StormFxPacket.TAKEOFF -> {
                VfxDraw.shockwave(buf, m, a, 0.5D + 5.0D * ease(k), 1.0D * fade, WIND[0], WIND[1], WIND[2], fade);
                Random rnd = new Random(f.seed);
                for (int i = 0; i < 14; i++) {
                    double ang = rnd.nextDouble() * Math.PI * 2.0D;
                    double d = 0.4D + rnd.nextDouble() * 1.2D;
                    Vec3 base = a.add(Math.cos(ang) * d, 0.2D + age * 0.35D, Math.sin(ang) * d);
                    VfxDraw.beam(buf, m, base, base.add(0.0D, 1.5D + rnd.nextDouble(), 0.0D), 0.025F, 1.0F, 1.0F, 1.0F,
                            0.5F * fade);
                }
            }
            case StormFxPacket.CALL -> {
                // the storm's power coming down into her: a column of lightning, the sky torn open
                float grow = ease(k);
                float st = strobe(f.seed, age);
                VfxDraw.pillar(buf, m, a, a.y, a.y + 80.0D, cam, 2.0F * fade, BLUE[0], BLUE[1], BLUE[2], 0.45F * fade);
                VfxDraw.pillar(buf, m, a, a.y, a.y + 80.0D, cam, 0.5F * fade, 1.0F, 1.0F, 1.0F, 0.8F * fade * st);
                for (int i = 0; i < 4; i++) {
                    Random rnd = new Random(f.seed + i * 13L + tick / 2);
                    Vec3 from = a.add(rnd.nextGaussian() * 4.0D, 30.0D, rnd.nextGaussian() * 4.0D);
                    bolt(buf, m, from, a.add(0.0D, 1.0D, 0.0D), rnd.nextLong(), 0.08F, fade * st);
                }
                VfxDraw.sphere(buf, m, a.add(0.0D, 1.0D, 0.0D), 0.5D + 3.0D * grow, cam, BLUE[0], BLUE[1], BLUE[2],
                        0.55F * fade, t, 2.0D);
                VfxDraw.shockwave(buf, m, a, 0.5D + 8.0D * grow, 1.6D * fade, BLUE[0], BLUE[1], BLUE[2], fade);
                VfxDraw.flare(buf, m, a.add(0.0D, 1.2D, 0.0D), left, up, 3.0D, 0.75F, 0.9F, 1.0F,
                        Math.max(0.0F, 1.0F - age / 10.0F));
                VfxDraw.glow(buf, m, a.add(0.0D, 30.0D, 0.0D), left, up, 18.0D, BLUE[0], BLUE[1], BLUE[2],
                        0.15F * fade * st);
            }
            default -> {
            }
        }
    }
}
