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
                    funnel(buf, m, p.getPosition(pt).add(0.0D, 0.15D, 0.0D), -2.2D, 0.12D, 0.85D, t, 0.45F);
                }
            }
        } finally {
            VfxDraw.end(buf);
            ps.popPose();
        }
    }

    /** A funnel of air: spinning bands from a narrow end (at c) to a wide one (height h, may be negative). */
    private static void funnel(BufferBuilder buf, Matrix4f m, Vec3 c, double h, double r0, double r1, float t,
                               float alpha) {
        int bands = 5;
        int steps = 14;
        for (int k = 0; k < bands; k++) {
            Vec3 prev = null;
            for (int i = 0; i <= steps; i++) {
                double s = (double) i / steps;
                double r = r0 + (r1 - r0) * s;
                double a = t * 0.45D + k * Math.PI * 2.0D / bands + s * 4.0D;
                Vec3 q = c.add(Math.cos(a) * r, h * s, Math.sin(a) * r);
                if (prev != null) {
                    float al = alpha * (float) Math.sin(Math.PI * Math.min(1.0D, s * 1.1D));
                    VfxDraw.beam(buf, m, prev, q, 0.08F + 0.12F * (float) s, WIND[0], WIND[1], WIND[2], al);
                }
                prev = q;
            }
        }
        // rings climbing it
        Vec3 axis = new Vec3(0.0D, 1.0D, 0.0D);
        for (int i = 0; i < 3; i++) {
            double s = ((t * 0.04D + i / 3.0D) % 1.0D);
            double r = r0 + (r1 - r0) * s;
            VfxDraw.ring(buf, m, c.add(0.0D, h * s, 0.0D), axis, r, 0.04D + 0.06D * s, 0.0F, WIND[0], WIND[1],
                    WIND[2], alpha * 0.8F * (float) Math.sin(Math.PI * s));
        }
    }

    private static void draw(BufferBuilder buf, Matrix4f m, Minecraft mc, Fx f, float pt, float t, Vec3 cam,
                             Vec3 left, Vec3 up) {
        float age = f.age + pt;
        float k = Mth.clamp(age / f.life, 0.0F, 1.0F);
        float fade = 1.0F - k;
        Vec3 yUp = new Vec3(0.0D, 1.0D, 0.0D);
        Vec3 a = f.a;
        Vec3 b = f.b;
        switch (f.type) {
            case StormFxPacket.SKY -> {
                float grow = 1.0F - fade * fade;
                float big = Math.max(0.6F, f.power);
                VfxDraw.beam(buf, m, a, b, 1.6F * big, BLUE[0], BLUE[1], BLUE[2], 0.25F * fade);
                VfxDraw.burst(buf, m, b.add(0.0D, 0.5D, 0.0D), left, up, 3.0F * big * (0.5F + grow), BLUE[0], BLUE[1],
                        BLUE[2], 0.9F * fade, t * 0.2F);
                VfxDraw.ring(buf, m, b.add(0.0D, 0.08D, 0.0D), yUp, 0.5D + 5.0D * big * grow, 0.3D * fade + 0.04D,
                        0.0F, BLUE[0], BLUE[1], BLUE[2], 0.8F * fade);
                VfxDraw.sphere(buf, m, b.add(0.0D, 0.4D, 0.0D), 0.6D + 1.8D * big * grow, cam, BLUE[0], BLUE[1],
                        BLUE[2], 0.5F * fade * fade, t, 2.2D);
            }
            case StormFxPacket.ARC -> {
                VfxDraw.beam(buf, m, a, b, 0.5F, BLUE[0], BLUE[1], BLUE[2], 0.2F * fade);
                VfxDraw.burst(buf, m, b, left, up, 0.6F, BLUE[0], BLUE[1], BLUE[2], 0.6F * fade, t * 0.4F);
            }
            case StormFxPacket.BEAM -> {
                float al = Mth.clamp((f.life - age) / 2.0F, 0.0F, 1.0F);
                VfxDraw.energy(buf, m, a, b, 0.25F, BLUE[0], BLUE[1], BLUE[2], 0.4F * al);
                VfxDraw.burst(buf, m, a, left, up, 0.8F, BLUE[0], BLUE[1], BLUE[2], 0.7F * al, t * 0.5F);
                VfxDraw.burst(buf, m, b, left, up, 1.6F, BLUE[0], BLUE[1], BLUE[2], 0.8F * al, -t * 0.4F);
                VfxDraw.glow(buf, m, b, left, up, 1.0D, BLUE[0], BLUE[1], BLUE[2], 0.4F * al);
            }
            case StormFxPacket.CAST -> {
                Entity e = mc.level.getEntity(f.caster);
                if (e == null) {
                    return;
                }
                Vec3 pos = e.getPosition(pt);
                float al = Mth.clamp((f.life - age) / 8.0F, 0.0F, 1.0F) * Mth.clamp(age / 3.0F, 0.0F, 1.0F);
                VfxDraw.ring(buf, m, pos.add(0.0D, 1.0D, 0.0D), new Vec3(Math.sin(t * 0.1D), 1.0D, Math.cos(t * 0.1D)),
                        1.0D, 0.04D, t * 0.2F, BLUE[0], BLUE[1], BLUE[2], 0.7F * al);
                VfxDraw.glow(buf, m, pos.add(0.0D, 1.0D, 0.0D), left, up, 1.4D, BLUE[0], BLUE[1], BLUE[2], 0.2F * al);
                boolean me = e == mc.getCameraEntity() && mc.options.getCameraType().isFirstPerson();
                if (!me) {
                    // her eyes, white
                    Vec3 eye = e.getEyePosition(pt);
                    VfxDraw.burst(buf, m, eye, left, up, 0.5F, 0.9F, 0.95F, 1.0F, 0.7F * al, t * 0.3F);
                }
            }
            case StormFxPacket.GUST -> {
                Vec3 dir = b.lengthSqr() < 1.0E-6D ? new Vec3(0.0D, 0.0D, 1.0D) : b.normalize();
                Random rnd = new Random(f.seed);
                double run = 2.0D + 14.0D * (1.0D - fade * fade);
                for (int i = 0; i < 10; i++) {
                    Vec3 off = new Vec3(rnd.nextGaussian() * 0.8D, rnd.nextGaussian() * 0.5D, rnd.nextGaussian() * 0.8D);
                    double lead = run * (0.6D + 0.4D * rnd.nextDouble());
                    Vec3 head = a.add(dir.scale(lead)).add(off.scale(1.0D + lead * 0.15D));
                    Vec3 tail = head.subtract(dir.scale(3.0D + rnd.nextDouble() * 2.0D));
                    VfxDraw.beam(buf, m, tail, head, 0.12F, WIND[0], WIND[1], WIND[2], 0.5F * fade);
                }
                for (int r = 0; r < 3; r++) {
                    double d = run * (0.3D + r * 0.3D);
                    VfxDraw.ring(buf, m, a.add(dir.scale(d)), dir, 0.8D + d * 0.18D, 0.06D, 0.0F, WIND[0], WIND[1],
                            WIND[2], 0.5F * fade);
                }
            }
            case StormFxPacket.TORNADO -> {
                float al = Mth.clamp((f.life - age) / 3.0F, 0.0F, 1.0F);
                funnel(buf, m, a, 14.0D, 0.6D, 5.0D, t, 0.55F * al);
                VfxDraw.ring(buf, m, a.add(0.0D, 0.1D, 0.0D), yUp, 3.0D, 0.6D, t * 0.3F, 0.75F, 0.7F, 0.6F, 0.25F * al);
            }
            case StormFxPacket.HAIL -> {
                float in = Mth.clamp(age / 6.0F, 0.0F, 1.0F);
                float al = in * Mth.clamp((f.life - age) / 10.0F, 0.0F, 1.0F);
                VfxDraw.ring(buf, m, a.add(0.0D, 0.06D, 0.0D), yUp, 4.0D, 0.15D, 0.0F, ICE[0], ICE[1], ICE[2], 0.7F * al);
                VfxDraw.glow(buf, m, a.add(0.0D, 0.06D, 0.0D), new Vec3(1.0D, 0.0D, 0.0D), new Vec3(0.0D, 0.0D, 1.0D),
                        4.2D, ICE[0], ICE[1], ICE[2], 0.25F * al);
                Random rnd = new Random(f.seed);
                for (int i = 0; i < 26; i++) {
                    double x = (rnd.nextDouble() - 0.5D) * 8.0D;
                    double z = (rnd.nextDouble() - 0.5D) * 8.0D;
                    double ph = ((age * 0.09D + rnd.nextDouble()) % 1.0D);
                    double y = 9.0D * (1.0D - ph);
                    Vec3 head = a.add(x, y, z);
                    VfxDraw.beam(buf, m, head.add(0.0D, 1.4D, 0.0D), head, 0.07F, ICE[0], ICE[1], ICE[2], 0.8F * al);
                }
            }
            case StormFxPacket.FROST -> {
                double r = Math.max(2.0D, f.power) * (1.0D - fade * fade);
                Vec3 c = a.add(0.0D, 0.06D, 0.0D);
                VfxDraw.ring(buf, m, c, yUp, Math.max(0.3D, r), 0.35D * fade + 0.05D, 0.0F, ICE[0], ICE[1], ICE[2],
                        0.85F * fade);
                VfxDraw.wall(buf, m, c, Math.max(0.3D, r), 1.0D * fade, ICE[0], ICE[1], ICE[2], 0.3F * fade);
                Random rnd = new Random(f.seed);
                for (int i = 0; i < 24; i++) {
                    double ang = rnd.nextDouble() * Math.PI * 2.0D;
                    double d = r * (0.85D + rnd.nextDouble() * 0.15D);
                    Vec3 base = c.add(Math.cos(ang) * d, 0.0D, Math.sin(ang) * d);
                    Vec3 tip = base.add(Math.cos(ang) * 0.2D, 0.4D + rnd.nextDouble() * 0.8D, Math.sin(ang) * 0.2D);
                    VfxDraw.energy(buf, m, base, tip, 0.08F, ICE[0], ICE[1], ICE[2], 0.8F * fade);
                }
            }
            case StormFxPacket.TAKEOFF -> {
                float grow = 1.0F - fade * fade;
                VfxDraw.ring(buf, m, a.add(0.0D, 0.06D, 0.0D), yUp, 0.5D + 4.5D * grow, 0.3D * fade + 0.04D, 0.0F,
                        WIND[0], WIND[1], WIND[2], 0.8F * fade);
                funnel(buf, m, a, 4.0D * grow + 0.5D, 0.3D, 1.6D, t, 0.6F * fade);
            }
            case StormFxPacket.CALL -> {
                float grow = 1.0F - fade * fade;
                VfxDraw.pillar(buf, m, a, a.y, a.y + 60.0D, cam, 1.6F * fade, BLUE[0], BLUE[1], BLUE[2], 0.5F * fade);
                VfxDraw.pillar(buf, m, a, a.y, a.y + 60.0D, cam, 0.5F * fade, 1.0F, 1.0F, 1.0F, 0.8F * fade);
                VfxDraw.sphere(buf, m, a.add(0.0D, 1.0D, 0.0D), 0.5D + 3.0D * grow, cam, BLUE[0], BLUE[1], BLUE[2],
                        0.6F * fade, t, 2.0D);
                VfxDraw.ring(buf, m, a.add(0.0D, 0.06D, 0.0D), yUp, 0.5D + 7.0D * grow, 0.3D, 0.0F, BLUE[0], BLUE[1],
                        BLUE[2], 0.8F * fade);
                VfxDraw.burst(buf, m, a.add(0.0D, 1.0D, 0.0D), left, up, 4.0F * (0.5F + grow), BLUE[0], BLUE[1],
                        BLUE[2], 0.9F * fade, t * 0.2F);
            }
            default -> {
            }
        }
    }
}
