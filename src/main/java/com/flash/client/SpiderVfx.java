package com.flash.client;

import com.flash.FlashMod;
import com.flash.network.HeroFxPacket;
import com.flash.spider.SpiderAbilities;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/**
 * Spider-Man as in the films, over his real web (SpiderClient draws the strands): the "thwip" of a shot (a puff at the
 * wrist, a streak along the strand, a splash of web where it sticks), the net opening out as a web, the speed of a
 * zip, the tension of a yank, the spider-sense rippling round his head in red and blue (the danger slowed down),
 * the shock of his kicks, an afterimage left by a dodge and the radioactive glow of the bite.
 */
@Mod.EventBusSubscriber(modid = FlashMod.MODID, value = Dist.CLIENT)
public final class SpiderVfx {

    private static final float[] WEB = {0.92F, 0.95F, 1.0F};
    private static final float[] SENSE_RED = {1.0F, 0.18F, 0.15F};
    private static final float[] SENSE_BLUE = {0.25F, 0.5F, 1.0F};
    private static final float[] BITE = {0.45F, 0.3F, 1.0F};

    private static final class Fx {
        final int type;
        final int caster;
        final Vec3 a;
        final Vec3 b;
        final float power;
        final long seed;
        final int life;
        float age;

        Fx(HeroFxPacket p, int life) {
            this.type = p.type();
            this.caster = p.caster();
            this.a = p.from();
            this.b = p.to();
            this.power = p.power();
            this.seed = (long) (p.from().x * 37.0D + p.to().z * 11.0D) * 1009L ^ System.nanoTime();
            this.life = life;
        }
    }

    private static final List<Fx> ACTIVE = new ArrayList<>();
    private static float clock;

    private SpiderVfx() {
    }

    public static void reset() {
        ACTIVE.clear();
    }

    public static void fx(HeroFxPacket p) {
        int life = switch (p.type()) {
            case SpiderAbilities.FX_SHOT -> 8;
            case SpiderAbilities.FX_NET -> 14;
            case SpiderAbilities.FX_YANK -> 10;
            case SpiderAbilities.FX_ZIP -> 10;
            case SpiderAbilities.FX_WEBBED -> 12;
            case SpiderAbilities.FX_SENSE -> p.power() >= 2.0F ? 30 : 20;
            case SpiderAbilities.FX_KICK -> 10;
            case SpiderAbilities.FX_BITE -> 40;
            case SpiderAbilities.FX_DODGE -> 14;
            default -> 0;
        };
        if (life > 0 && ACTIVE.size() < 200) {
            ACTIVE.add(new Fx(p, life));
        }
        if (p.type() == SpiderAbilities.FX_KICK) {
            HulkVfx.quake(Minecraft.getInstance(), p.from(), 0.4F, 8.0D);
        }
    }

    private static float ease(float k) {
        float u = 1.0F - Math.min(1.0F, Math.max(0.0F, k));
        return 1.0F - u * u * u;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.phase != TickEvent.Phase.END || mc.level == null || mc.isPaused()) {
            return;
        }
        clock += 1.0F;
        for (Iterator<Fx> it = ACTIVE.iterator(); it.hasNext(); ) {
            Fx f = it.next();
            f.age += 1.0F;
            if (f.age > f.life) {
                it.remove();
            }
        }
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

    /** A splash of web: a starburst of strands and a ring, soft white. */
    private static void splash(BufferBuilder buf, Matrix4f m, Vec3 c, Vec3 left, Vec3 up, double r, long seed,
                               float al) {
        Random rnd = new Random(seed);
        for (int i = 0; i < 10; i++) {
            double ang = Math.PI * 2.0D * i / 10.0D + rnd.nextDouble() * 0.3D;
            Vec3 d = left.scale(Math.cos(ang)).add(up.scale(Math.sin(ang)));
            VfxDraw.beam(buf, m, c, c.add(d.scale(r * (0.6D + rnd.nextDouble() * 0.5D))), 0.015F, WEB[0], WEB[1],
                    WEB[2], 0.7F * al);
        }
        VfxDraw.ring(buf, m, c, left.cross(up), r * 0.6D, 0.015D, 0.0F, WEB[0], WEB[1], WEB[2], 0.6F * al);
        VfxDraw.glow(buf, m, c, left, up, r * 0.8D, WEB[0], WEB[1], WEB[2], 0.18F * al);
    }

    private static void draw(BufferBuilder buf, Matrix4f m, Minecraft mc, Fx f, float pt, float t, Vec3 cam,
                             Vec3 left, Vec3 up) {
        float age = f.age + pt;
        float k = Math.min(1.0F, age / f.life);
        float fade = 1.0F - k;
        Entity e = f.caster >= 0 ? mc.level.getEntity(f.caster) : null;
        boolean me = e != null && e == mc.getCameraEntity() && mc.options.getCameraType().isFirstPerson();
        switch (f.type) {
            case SpiderAbilities.FX_SHOT, SpiderAbilities.FX_YANK -> {
                // thwip: a puff at the wrist, a streak racing along the strand, the splash where it sticks
                float run = Math.min(1.0F, age / 2.0F);
                Vec3 tip = f.a.lerp(f.b, run);
                Vec3 tail = f.a.lerp(f.b, Math.max(0.0F, run - 0.35F));
                VfxDraw.beam(buf, m, tail, tip, 0.05F, WEB[0], WEB[1], WEB[2], 0.35F * fade);
                if (!me) {
                    VfxDraw.glow(buf, m, f.a, left, up, 0.25D, WEB[0], WEB[1], WEB[2], 0.5F * fade);
                }
                if (run >= 1.0F && (f.power > 0.0F || f.type == SpiderAbilities.FX_YANK)) {
                    splash(buf, m, f.b, left, up, 0.4D + 0.5D * ease(k), f.seed, fade);
                }
                if (f.type == SpiderAbilities.FX_YANK) {
                    // the line pulled taut: a shiver of tension down it
                    double len = f.a.distanceTo(f.b);
                    if (len > 0.5D) {
                        Vec3 dn = f.b.subtract(f.a).scale(1.0D / len);
                        double pos = (age * 2.0D) % len;
                        VfxDraw.glow(buf, m, f.a.add(dn.scale(pos)), left, up, 0.12D, 1.0F, 1.0F, 1.0F, fade);
                    }
                }
            }
            case SpiderAbilities.FX_NET -> {
                // the net opening out ahead: rings and spokes of a web flying forward
                Vec3 dir = f.b.lengthSqr() < 1.0E-6D ? new Vec3(0.0D, 0.0D, 1.0D) : f.b.normalize();
                double d = 1.0D + 10.0D * ease(Math.min(1.0F, age / 8.0F));
                double r = 0.3D + 2.2D * ease(Math.min(1.0F, age / 8.0F));
                Vec3 c = f.a.add(dir.scale(d));
                Vec3 ref = Math.abs(dir.y) > 0.95D ? new Vec3(1.0D, 0.0D, 0.0D) : new Vec3(0.0D, 1.0D, 0.0D);
                Vec3 u = dir.cross(ref).normalize();
                Vec3 v = dir.cross(u).normalize();
                int spokes = 8;
                for (int i = 0; i < spokes; i++) {
                    double ang = Math.PI * 2.0D * i / spokes;
                    Vec3 o = u.scale(Math.cos(ang)).add(v.scale(Math.sin(ang)));
                    VfxDraw.beam(buf, m, c, c.add(o.scale(r)), 0.015F, WEB[0], WEB[1], WEB[2], 0.8F * fade);
                }
                for (int ring = 1; ring <= 3; ring++) {
                    double rr = r * ring / 3.0D;
                    for (int i = 0; i < spokes; i++) {
                        double a0 = Math.PI * 2.0D * i / spokes;
                        double a1 = Math.PI * 2.0D * (i + 1) / spokes;
                        Vec3 p0 = c.add(u.scale(Math.cos(a0) * rr)).add(v.scale(Math.sin(a0) * rr));
                        Vec3 p1 = c.add(u.scale(Math.cos(a1) * rr)).add(v.scale(Math.sin(a1) * rr));
                        VfxDraw.beam(buf, m, p0, p1, 0.012F, WEB[0], WEB[1], WEB[2], 0.7F * fade);
                    }
                }
            }
            case SpiderAbilities.FX_ZIP -> {
                // a zip: speed lines streaming past him toward where he flies
                if (e == null) {
                    return;
                }
                Vec3 pos = e.getPosition(pt).add(0.0D, e.getBbHeight() * 0.5D, 0.0D);
                Vec3 dir = f.b.subtract(f.a);
                if (dir.lengthSqr() < 1.0E-4D) {
                    return;
                }
                dir = dir.normalize();
                Random rnd = new Random(f.seed);
                for (int i = 0; i < 12; i++) {
                    Vec3 o = pos.add(rnd.nextGaussian() * 0.6D, rnd.nextGaussian() * 0.7D, rnd.nextGaussian() * 0.6D);
                    double len = 1.5D + rnd.nextDouble() * 2.0D;
                    VfxDraw.beam(buf, m, o.subtract(dir.scale(0.3D)), o.subtract(dir.scale(0.3D + len)), 0.012F,
                            WEB[0], WEB[1], WEB[2], 0.45F * fade);
                }
            }
            case SpiderAbilities.FX_WEBBED -> splash(buf, m, f.a.add(0.0D, 0.8D, 0.0D), left, up,
                    0.6D + 1.0D * ease(k), f.seed, fade);
            case SpiderAbilities.FX_SENSE -> {
                // the spider-sense: waves rippling out of his head, red and blue, the world slowed round him
                if (e == null) {
                    return;
                }
                boolean strong = f.power >= 2.0F;
                Vec3 head = e.getPosition(pt).add(0.0D, e.getBbHeight() + 0.1D, 0.0D);
                int waves = strong ? 5 : 3;
                for (int i = 0; i < waves; i++) {
                    float kk = Math.max(0.0F, Math.min(1.0F, (age - i * 2.5F) / (f.life * 0.6F)));
                    if (kk <= 0.0F || kk >= 1.0F) {
                        continue;
                    }
                    float[] c = i % 2 == 0 ? SENSE_RED : SENSE_BLUE;
                    double r = 0.3D + (strong ? 3.0D : 1.4D) * ease(kk);
                    // wavy, as in the comics
                    int n = 28;
                    Vec3 prev = null;
                    for (int j = 0; j <= n; j++) {
                        double ang = Math.PI * 2.0D * j / n;
                        double wob = 1.0D + 0.12D * Math.sin(ang * 6.0D + t * 0.6D);
                        Vec3 q = head.add(Math.cos(ang) * r * wob, Math.sin(ang * 3.0D) * 0.05D, Math.sin(ang) * r * wob);
                        if (prev != null) {
                            VfxDraw.beam(buf, m, prev, q, 0.025F, c[0], c[1], c[2], 0.8F * (1.0F - kk));
                        }
                        prev = q;
                    }
                }
                if (!me) {
                    float on = Math.min(1.0F, (f.life - age) / 6.0F);
                    for (int s = -1; s <= 1; s += 2) {
                        double ang = t * 0.4D;
                        Vec3 side = new Vec3(Math.cos(ang) * 0.35D * s, 0.0D, Math.sin(ang) * 0.35D * s);
                        VfxDraw.beam(buf, m, head.add(side), head.add(side.scale(2.2D)).add(0.0D, 0.25D, 0.0D), 0.02F,
                                SENSE_RED[0], SENSE_RED[1], SENSE_RED[2], 0.8F * on);
                    }
                }
                if (strong && !me) {
                    VfxDraw.glow(buf, m, head, left, up, 1.2D, SENSE_RED[0], SENSE_RED[1], SENSE_RED[2], 0.2F * fade);
                }
            }
            case SpiderAbilities.FX_KICK -> {
                // a kick: a ring of shock off the foot, the air pushed out in streaks
                Vec3 dir = f.b.lengthSqr() < 1.0E-6D ? new Vec3(0.0D, 0.0D, 1.0D) : f.b.normalize();
                Vec3 at = f.a.add(0.0D, 0.6D, 0.0D);
                VfxDraw.ring(buf, m, at, dir, 0.2D + 1.6D * ease(k), 0.05D, 0.0F, 1.0F, 1.0F, 1.0F, 0.8F * fade);
                VfxDraw.ring(buf, m, at.add(dir.scale(0.4D * k)), dir, 0.15D + 1.0D * ease(k), 0.18D, 0.0F, WEB[0],
                        WEB[1], WEB[2], 0.3F * fade);
                VfxDraw.flare(buf, m, at, left, up, 0.35D, WEB[0], WEB[1], WEB[2], Math.max(0.0F, 1.0F - age / 3.0F));
                VfxDraw.sparks(buf, m, at, f.seed, 10, 1.4D, k, 0.3D, 1.0F, 1.0F, 1.0F, 0.8F);
            }
            case SpiderAbilities.FX_DODGE -> {
                // a dodge: the blur of where he was, fading, streaks toward where he went
                Vec3 at = f.a.add(0.0D, 0.9D, 0.0D);
                VfxDraw.glow(buf, m, at, left, up.scale(1.8D), 0.55D, 0.85F, 0.25F, 0.25F, 0.3F * fade);
                VfxDraw.glow(buf, m, at, left, up.scale(1.8D), 0.35D, SENSE_BLUE[0], SENSE_BLUE[1], SENSE_BLUE[2],
                        0.25F * fade);
                Vec3 dir = f.b.lengthSqr() < 1.0E-6D ? new Vec3(1.0D, 0.0D, 0.0D) : f.b.normalize();
                Random rnd = new Random(f.seed);
                for (int i = 0; i < 6; i++) {
                    Vec3 o = at.add(rnd.nextGaussian() * 0.3D, rnd.nextGaussian() * 0.6D, rnd.nextGaussian() * 0.3D);
                    VfxDraw.beam(buf, m, o, o.subtract(dir.scale(1.5D)), 0.012F, 1.0F, 1.0F, 1.0F, 0.5F * fade);
                }
            }
            case SpiderAbilities.FX_BITE -> {
                // the radioactive bite: violet light swirling round him, motes climbing, the change taking hold
                Vec3 c = f.a;
                float in = Math.min(1.0F, age / 6.0F);
                for (int i = 0; i < 3; i++) {
                    VfxDraw.ring(buf, m, c.add(0.0D, 0.3D + i * 0.7D, 0.0D), new Vec3(Math.sin(t * 0.1D + i), 3.0D,
                            Math.cos(t * 0.1D + i)), 0.8D, 0.03D, t * 0.2F, BITE[0], BITE[1], BITE[2], 0.7F * in * fade);
                }
                Random rnd = new Random(f.seed);
                for (int i = 0; i < 16; i++) {
                    double ang = rnd.nextDouble() * Math.PI * 2.0D + t * 0.05D;
                    double ph = (age * 0.03D + rnd.nextDouble()) % 1.0D;
                    Vec3 q = c.add(Math.cos(ang) * 0.7D, ph * 2.2D, Math.sin(ang) * 0.7D);
                    VfxDraw.flare(buf, m, q, left, up, 0.06D, BITE[0], BITE[1], BITE[2], in * fade * (float) (1.0D - ph));
                }
                VfxDraw.glow(buf, m, c.add(0.0D, 1.0D, 0.0D), left, up, 1.4D, BITE[0], BITE[1], BITE[2], 0.2F * in * fade);
            }
            default -> {
            }
        }
    }
}
