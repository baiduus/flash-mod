package com.flash.client;

import com.flash.FlashMod;
import com.flash.doom.DoomAbilities;
import com.flash.network.HeroFxPacket;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
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
 * Doctor Doom's powers drawn in light (VfxDraw, additive, as the other heroes' effects): the electric green of his
 * gauntlets (bolts, the beam, the discharge), the emerald force field (a bubble of hexagons that ripples where it is
 * hit, the wave), his sorcery (the mystic bolt's trail, the ritual circle of runes and its eruption) and the blue
 * jets of his boot thrusters.
 */
@Mod.EventBusSubscriber(modid = FlashMod.MODID, value = Dist.CLIENT)
public final class DoomVfx {

    private static final float[] GAUNT = {0.35F, 1.0F, 0.62F};
    private static final float[] FIELD = {0.3F, 0.95F, 0.45F};
    private static final float[] MYSTIC = {0.62F, 1.0F, 0.28F};
    private static final float[] JET = {0.45F, 0.75F, 1.0F};
    /** God Emperor Doom: the Beyonders' gold-white. */
    private static final float[] GOD = {1.0F, 0.85F, 0.4F};
    private static final float[] WHITE = {1.0F, 0.97F, 0.88F};

    private static final class Fx {
        final int type;
        final int caster;
        final Vec3 a;
        final Vec3 b;
        final float power;
        final int target;
        final long seed;
        final int life;
        float age;

        Fx(HeroFxPacket p, int life) {
            this.type = p.type();
            this.caster = p.caster();
            this.a = p.from();
            this.b = p.to();
            this.power = p.power();
            this.target = p.target();
            this.seed = (long) (p.from().x * 31.0D + p.to().z * 17.0D) ^ System.nanoTime();
            this.life = life;
        }
    }

    private static final List<Fx> ACTIVE = new ArrayList<>();
    private static float clock;

    private DoomVfx() {
    }

    public static void reset() {
        ACTIVE.clear();
    }

    public static void fx(HeroFxPacket p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        int life = switch (p.type()) {
            case DoomAbilities.FX_BOLT -> 7;
            case DoomAbilities.FX_BEAM -> 4;
            case DoomAbilities.FX_DISCHARGE -> 9;
            case DoomAbilities.FX_SHIELD -> 14;
            case DoomAbilities.FX_SHIELD_HIT -> 7;
            case DoomAbilities.FX_WAVE -> 14;
            case DoomAbilities.FX_MYSTIC -> 9;
            case DoomAbilities.FX_MYSTIC_HIT -> 16;
            case DoomAbilities.FX_RITUAL -> Math.max(10, p.target()) + 2;
            case DoomAbilities.FX_ERUPT -> 24;
            case DoomAbilities.FX_TAKEOFF -> 12;
            case DoomAbilities.FX_GRIMOIRE -> 50;
            case DoomAbilities.FX_ANNIHILATE -> 14;
            case DoomAbilities.FX_REWRITE -> 40;
            case DoomAbilities.FX_ASCEND -> (int) p.power() + 10;
            case DoomAbilities.FX_ABSORB -> 40;
            default -> 0;
        };
        if (mc.level.getEntity(p.caster()) instanceof Player cp) {
            DoomPose.cast(cp, p.type(), p.power());
        }
        if (p.type() == DoomAbilities.FX_ABSORB) {
            Vec3 a = p.from();
            for (int i = 0; i < 40; i++) {
                mc.level.addParticle(ParticleTypes.SOUL, a.x + rnd(3.0D), a.y + rnd(3.0D), a.z + rnd(3.0D), 0.0D,
                        0.05D, 0.0D);
            }
        }
        if (life > 0 && ACTIVE.size() < 400) {
            ACTIVE.add(new Fx(p, life));
        }
        Vec3 b = p.to();
        switch (p.type()) {
            case DoomAbilities.FX_BOLT -> {
                if (p.power() > 0.0F) {
                    for (int i = 0; i < 6; i++) {
                        mc.level.addParticle(ParticleTypes.ELECTRIC_SPARK, b.x, b.y, b.z, rnd(0.3D), rnd(0.3D),
                                rnd(0.3D));
                    }
                }
            }
            case DoomAbilities.FX_MYSTIC -> mc.level.addParticle(ParticleTypes.ENCHANT, b.x, b.y, b.z, rnd(0.4D),
                    rnd(0.4D), rnd(0.4D));
            case DoomAbilities.FX_MYSTIC_HIT -> {
                for (int i = 0; i < 20; i++) {
                    mc.level.addParticle(ParticleTypes.HAPPY_VILLAGER, b.x, b.y, b.z, rnd(0.6D), rnd(0.6D), rnd(0.6D));
                }
            }
            case DoomAbilities.FX_GRIMOIRE -> {
                for (int i = 0; i < 60; i++) {
                    double a = i / 60.0D * Math.PI * 2.0D;
                    mc.level.addParticle(ParticleTypes.ENCHANT, p.from().x + Math.cos(a) * 1.5D, p.from().y + 2.5D,
                            p.from().z + Math.sin(a) * 1.5D, -Math.cos(a) * 1.2D, -1.5D, -Math.sin(a) * 1.2D);
                }
            }
            default -> {
            }
        }
    }

    private static double rnd(double s) {
        return (Math.random() - 0.5D) * s;
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
        if (mc.level == null || ACTIVE.isEmpty() && !any(mc)) {
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
                if (!DoomClient.isDoom(p) || p.isInvisible()) {
                    continue;
                }
                if (DoomClient.god(p)) {
                    // the crown of a god: a sigil turning over his head, gold light pouring off him, motes rising
                    Vec3 head = p.getPosition(pt).add(0.0D, p.getBbHeight() + 0.35D, 0.0D);
                    float pulse = 0.65F + 0.25F * (float) Math.sin(t * 0.15F + p.getId());
                    VfxDraw.sigil(buf, m, head, new Vec3(0.0D, 1.0D, 0.0D), 0.55D, t, GOD[0], GOD[1], GOD[2], pulse);
                    VfxDraw.runeRing(buf, m, head.add(0.0D, 0.05D, 0.0D), 0.42D, 6, -t * 0.05F, WHITE[0], WHITE[1],
                            WHITE[2], pulse);
                    VfxDraw.glow(buf, m, p.getPosition(pt).add(0.0D, p.getBbHeight() * 0.55D, 0.0D), left, up,
                            1.6D, GOD[0], GOD[1], GOD[2], 0.2F * pulse);
                    java.util.Random rnd = new java.util.Random(p.getId() * 31L);
                    for (int i = 0; i < 10; i++) {
                        double ang = rnd.nextDouble() * Math.PI * 2.0D;
                        double ph = (t * 0.02D + rnd.nextDouble()) % 1.0D;
                        Vec3 q = p.getPosition(pt).add(Math.cos(ang) * 0.6D, ph * 2.6D, Math.sin(ang) * 0.6D);
                        VfxDraw.flare(buf, m, q, left, up, 0.06D, GOD[0], GOD[1], GOD[2], (float) (1.0D - ph));
                    }
                }
                if (DoomClient.shielded(p)) {
                    // the emerald force field: a bubble of hexagonal plates, a ripple running over it
                    Vec3 c = p.getPosition(pt).add(0.0D, p.getBbHeight() * 0.5D, 0.0D);
                    double r = DoomAbilities.FIELD_RADIUS;
                    VfxDraw.hexShield(buf, m, c, r, cam, t, FIELD[0], FIELD[1], FIELD[2], 0.9F, 0.0F);
                    VfxDraw.hexCircle(buf, m, c.add(0.0D, -p.getBbHeight() * 0.5D + 0.05D, 0.0D), r, t,
                            FIELD[0], FIELD[1], FIELD[2], 0.45F);
                }
            }
        } finally {
            VfxDraw.end(buf);
            ps.popPose();
        }
    }

    private static boolean any(Minecraft mc) {
        for (int id : DoomClient.ids()) {
            Entity e = mc.level.getEntity(id);
            if (e instanceof Player p && (DoomClient.shielded(p) || DoomClient.god(p))) {
                return true;
            }
        }
        return false;
    }

    private static Vec3 handOf(Minecraft mc, Fx f, float pt) {
        Entity e = mc.level.getEntity(f.caster);
        if (e instanceof Player p) {
            boolean me = p == mc.getCameraEntity() && mc.options.getCameraType().isFirstPerson();
            if (me) {
                Camera cam = mc.gameRenderer.getMainCamera();
                return cam.getPosition().add(new Vec3(cam.getLookVector()).scale(0.7D))
                        .add(new Vec3(cam.getLeftVector()).scale(-0.35D)).add(new Vec3(cam.getUpVector()).scale(-0.3D));
            }
            Vec3 look = p.getViewVector(pt);
            Vec3 side = new Vec3(-look.z, 0.0D, look.x).normalize().scale(0.38D);
            return p.getEyePosition(pt).add(look.scale(0.55D)).add(side).add(0.0D, -0.38D, 0.0D);
        }
        return f.a;
    }

    private static float ease(float k) {
        float u = 1.0F - Math.min(1.0F, Math.max(0.0F, k));
        return 1.0F - u * u * u;
    }

    private static float strobe(long seed, float age) {
        long h = (seed + (int) age * 0x9E3779B97F4A7C15L) >>> 40;
        return (h & 3L) == 0L ? 0.4F : 1.0F;
    }

    /** A bolt of his gauntlets: green glow, bright colour, white core, side forks. */
    private static void bolt(BufferBuilder buf, Matrix4f m, Vec3 a, Vec3 b, long seed, float w, float[] c,
                             float alpha, int forks) {
        if (alpha <= 0.01F) {
            return;
        }
        Vec3[] pts = VfxDraw.jagged(a, b, seed, 1.0D, 4);
        VfxDraw.path(buf, m, pts, w * 4.0F, c[0], c[1], c[2], 0.22F * alpha);
        VfxDraw.path(buf, m, pts, w, VfxDraw.mix(c[0], 0.4F), VfxDraw.mix(c[1], 0.4F), VfxDraw.mix(c[2], 0.4F),
                0.85F * alpha);
        VfxDraw.path(buf, m, pts, w * 0.3F, 1.0F, 1.0F, 1.0F, alpha);
        java.util.Random rnd = new java.util.Random(seed ^ 0x5DEECE66DL);
        double len = a.distanceTo(b);
        for (int i = 0; i < forks && pts.length > 6; i++) {
            Vec3 from = pts[2 + rnd.nextInt(pts.length - 4)];
            Vec3 d = b.subtract(a).normalize().add(rnd.nextGaussian() * 0.7D, rnd.nextGaussian() * 0.6D,
                    rnd.nextGaussian() * 0.7D).normalize();
            Vec3[] fp = VfxDraw.jagged(from, from.add(d.scale(len * 0.25D)), rnd.nextLong(), 1.2D, 3);
            VfxDraw.path(buf, m, fp, w * 0.45F, c[0], c[1], c[2], 0.6F * alpha);
            VfxDraw.path(buf, m, fp, w * 0.15F, 1.0F, 1.0F, 1.0F, 0.8F * alpha);
        }
    }

    private static void draw(BufferBuilder buf, Matrix4f m, Minecraft mc, Fx f, float pt, float t, Vec3 cam,
                             Vec3 left, Vec3 up) {
        float age = f.age + pt;
        float k = Math.min(1.0F, age / f.life);
        float fade = 1.0F - k;
        Vec3 yUp = new Vec3(0.0D, 1.0D, 0.0D);
        long tick = (long) age;
        switch (f.type) {
            case DoomAbilities.FX_BOLT -> {
                // a bolt of the gauntlet: forking green lightning, the gauntlet flaring
                Vec3 a = handOf(mc, f, pt);
                bolt(buf, m, a, f.b, f.seed + tick / 2, 0.06F, GAUNT, fade * strobe(f.seed, age), 2);
                VfxDraw.flare(buf, m, a, left, up, 0.3D, GAUNT[0], GAUNT[1], GAUNT[2], fade);
                if (f.power > 0.0F) {
                    VfxDraw.flare(buf, m, f.b, left, up, 0.6D, GAUNT[0], GAUNT[1], GAUNT[2], fade);
                    VfxDraw.sparks(buf, m, f.b, f.seed, 12, 1.4D, k, 0.6D, GAUNT[0], GAUNT[1], GAUNT[2], 1.0F);
                }
            }
            case DoomAbilities.FX_BEAM -> {
                // the beam: a torrent of green energy with lightning crawling over it
                Vec3 a = handOf(mc, f, pt);
                float al = 0.9F * fade + 0.1F;
                VfxDraw.beam(buf, m, a, f.b, 0.5F, GAUNT[0], GAUNT[1], GAUNT[2], 0.12F * al);
                VfxDraw.energy(buf, m, a, f.b, 0.13F, GAUNT[0], GAUNT[1], GAUNT[2], al);
                for (int i = 0; i < 2; i++) {
                    bolt(buf, m, a, f.b, f.seed + i * 71L + (long) (t * 1.5F), 0.035F, GAUNT, 0.8F * al, 1);
                }
                VfxDraw.flare(buf, m, a, left, up, 0.4D, GAUNT[0], GAUNT[1], GAUNT[2], 1.0F);
                if (f.power > 0.0F) {
                    VfxDraw.flare(buf, m, f.b, left, up, 0.8D, GAUNT[0], GAUNT[1], GAUNT[2], 1.0F);
                    long gen = (long) (t / 4.0F);
                    VfxDraw.sparks(buf, m, f.b, f.seed + gen * 977L, 8, 1.4D, (t - gen * 4.0F) / 8.0F, 0.6D, GAUNT[0],
                            GAUNT[1], GAUNT[2], 1.0F);
                }
            }
            case DoomAbilities.FX_DISCHARGE -> {
                // both gauntlets let go: a storm of green lightning fanning out
                Vec3 a = handOf(mc, f, pt);
                Vec3 dir = f.b.normalize();
                Vec3 side = new Vec3(-dir.z, 0.0D, dir.x);
                side = side.lengthSqr() < 1.0E-4D ? new Vec3(1.0D, 0.0D, 0.0D) : side.normalize();
                Vec3 vup = side.cross(dir).normalize();
                for (int i = 0; i < 11; i++) {
                    double ax = ((i * 53) % 11 - 5) / 5.0D * 0.6D;
                    double ay = ((i * 29) % 5 - 2) / 2.0D * 0.3D;
                    Vec3 d = dir.add(side.scale(ax)).add(vup.scale(ay)).normalize();
                    Vec3 end = a.add(d.scale(4.0D + 8.0D * ease(Math.min(1.0F, age / 3.0F))));
                    bolt(buf, m, a, end, f.seed + i * 977L + tick, 0.05F, GAUNT, fade * strobe(f.seed + i, age), 1);
                }
                VfxDraw.flare(buf, m, a, left, up, 1.0D, GAUNT[0], GAUNT[1], GAUNT[2], fade);
                VfxDraw.cone(buf, m, a, dir, 8.0D * ease(k), 3.5D * ease(k), GAUNT[0], GAUNT[1], GAUNT[2], 0.1F * fade);
            }
            case DoomAbilities.FX_SHIELD -> {
                // the field going up: the plates spreading out from his chest
                Entity e = mc.level.getEntity(f.caster);
                Vec3 c = e != null ? e.getPosition(pt).add(0.0D, 1.0D, 0.0D) : f.a.add(0.0D, 1.0D, 0.0D);
                VfxDraw.hexShield(buf, m, c, 0.3D + DoomAbilities.FIELD_RADIUS * ease(k), cam, t, FIELD[0], FIELD[1],
                        FIELD[2], fade, 0.4F * fade);
                VfxDraw.flare(buf, m, c, left, up, 0.9D, FIELD[0], FIELD[1], FIELD[2], Math.max(0.0F, 1.0F - age / 5.0F));
            }
            case DoomAbilities.FX_SHIELD_HIT -> {
                Entity e = mc.level.getEntity(f.caster);
                if (e != null) {
                    Vec3 c = e.getPosition(pt).add(0.0D, 1.0D, 0.0D);
                    VfxDraw.hexShield(buf, m, c, DoomAbilities.FIELD_RADIUS + 0.05D + 0.15D * k, cam, t, 0.7F, 1.0F,
                            0.75F, 0.5F * fade, 0.8F * fade);
                }
            }
            case DoomAbilities.FX_WAVE -> {
                // the field thrown out: a wall of plates and a ring of force rolling away
                double r = 0.5D + f.power * ease(k);
                VfxDraw.shockwave(buf, m, f.b, r, 1.8D * fade, FIELD[0], FIELD[1], FIELD[2], fade);
                VfxDraw.hexShield(buf, m, f.a, r * 0.6D, cam, t, FIELD[0], FIELD[1], FIELD[2], 0.6F * fade, 0.0F);
                VfxDraw.hexCircle(buf, m, f.b.add(0.0D, 0.04D, 0.0D), r * 0.8D, t, FIELD[0], FIELD[1], FIELD[2],
                        0.6F * fade);
            }
            case DoomAbilities.FX_MYSTIC -> {
                // a mystic bolt: arcane wisps winding round a bright thread, a sigil where it starts
                VfxDraw.energy(buf, m, f.a, f.b, 0.1F, MYSTIC[0], MYSTIC[1], MYSTIC[2], fade);
                for (int i = 0; i < 3; i++) {
                    VfxDraw.wisp(buf, m, f.a, f.b, f.seed + i * 17L, t * 2.0F, 0.3D, 0.1F, MYSTIC[0], MYSTIC[1],
                            MYSTIC[2], fade);
                }
                VfxDraw.sigil(buf, m, f.a, f.b.subtract(f.a), 0.35D, t * 2.0F, MYSTIC[0], MYSTIC[1], MYSTIC[2], fade);
                VfxDraw.flare(buf, m, f.b, left, up, 0.4D, MYSTIC[0], MYSTIC[1], MYSTIC[2], fade);
            }
            case DoomAbilities.FX_MYSTIC_HIT -> {
                VfxDraw.flare(buf, m, f.a, left, up, 1.3D, MYSTIC[0], MYSTIC[1], MYSTIC[2],
                        Math.max(0.0F, 1.0F - age / 5.0F));
                VfxDraw.sphere(buf, m, f.a, 0.4D + 2.8D * ease(k), cam, MYSTIC[0], MYSTIC[1], MYSTIC[2], fade, t, 2.2D);
                VfxDraw.runeRing(buf, m, f.a.add(0.0D, -0.6D, 0.0D), 1.2D + 1.8D * ease(k), 8, t * 0.08F, MYSTIC[0],
                        MYSTIC[1], MYSTIC[2], fade);
                for (int i = 0; i < 6; i++) {
                    double ang = Math.PI * 2.0D * i / 6.0D;
                    Vec3 out = f.a.add(Math.cos(ang) * 3.0D * ease(k), (i % 2) * 1.2D * ease(k), Math.sin(ang) * 3.0D
                            * ease(k));
                    VfxDraw.wisp(buf, m, f.a, out, f.seed + i, t * 2.0F, 0.4D, 0.12F, MYSTIC[0], MYSTIC[1], MYSTIC[2],
                            fade);
                }
                VfxDraw.sparks(buf, m, f.a, f.seed, 24, 2.5D, k, 0.6D, MYSTIC[0], MYSTIC[1], MYSTIC[2], 1.0F);
            }
            case DoomAbilities.FX_RITUAL -> {
                // the ritual: circles of runes turning faster, the ground lit, the air thick with sorcery
                float charge = Math.min(1.0F, age / Math.max(1, f.target));
                double r = f.power * ease(Math.min(1.0F, age / 6.0F));
                float a = 0.4F + 0.6F * charge;
                VfxDraw.runeRing(buf, m, f.a, r, 12, t * (0.03F + 0.12F * charge), MYSTIC[0], MYSTIC[1], MYSTIC[2], a);
                VfxDraw.hexCircle(buf, m, f.a.add(0.0D, 0.03D, 0.0D), r * 0.7D, t * 0.5F, FIELD[0], FIELD[1],
                        FIELD[2], a * 0.8F);
                VfxDraw.sigil(buf, m, f.a.add(0.0D, 0.04D, 0.0D), yUp, r * 0.38D, -t * 2.0F, MYSTIC[0], MYSTIC[1],
                        MYSTIC[2], a);
                VfxDraw.wall(buf, m, f.a, r, 0.8D * charge, MYSTIC[0], MYSTIC[1], MYSTIC[2], 0.35F * charge);
                for (int i = 0; i < 6; i++) {
                    double ang = t * 0.04D + i * Math.PI / 3.0D;
                    Vec3 p0 = f.a.add(Math.cos(ang) * r, 0.0D, Math.sin(ang) * r);
                    VfxDraw.wisp(buf, m, p0, f.a.add(0.0D, 2.0D + 3.0D * charge, 0.0D), f.seed + i, t, 0.5D, 0.12F,
                            MYSTIC[0], MYSTIC[1], MYSTIC[2], 0.7F * charge);
                }
            }
            case DoomAbilities.FX_ERUPT -> {
                // the eruption: a column of green fire, the rune circle blazing, a ring of force
                double r = f.power;
                VfxDraw.flare(buf, m, f.a.add(0.0D, 1.0D, 0.0D), left, up, 2.5D, MYSTIC[0], MYSTIC[1], MYSTIC[2],
                        Math.max(0.0F, 1.0F - age / 6.0F));
                VfxDraw.pillar(buf, m, f.a, f.a.y, f.a.y + 12.0D * ease(Math.min(1.0F, age / 5.0F)), cam,
                        (float) r * 0.9F * fade, MYSTIC[0], MYSTIC[1], MYSTIC[2], 0.7F * fade);
                VfxDraw.pillar(buf, m, f.a, f.a.y, f.a.y + 16.0D, cam, (float) r * 0.25F * fade, 1.0F, 1.0F, 0.9F,
                        0.9F * fade);
                VfxDraw.shockwave(buf, m, f.a, r + 5.0D * ease(k), 2.0D * fade, MYSTIC[0], MYSTIC[1], MYSTIC[2], fade);
                VfxDraw.runeRing(buf, m, f.a, r, 12, t * 0.2F, MYSTIC[0], MYSTIC[1], MYSTIC[2], fade);
                for (int i = 0; i < 8; i++) {
                    double ang = Math.PI * 2.0D * i / 8.0D;
                    Vec3 p0 = f.a.add(Math.cos(ang) * r * 0.8D, 0.0D, Math.sin(ang) * r * 0.8D);
                    VfxDraw.flame(buf, m, p0, (2.0D + (i % 3)) * fade, 0.35D, cam, t + i * 7.0F, MYSTIC[0], MYSTIC[1],
                            MYSTIC[2], 0.7F * fade);
                }
                VfxDraw.sparks(buf, m, f.a.add(0.0D, 0.5D, 0.0D), f.seed, 40, r * 1.5D, k, 1.0D, MYSTIC[0], MYSTIC[1],
                        MYSTIC[2], 1.0F);
            }
            case DoomAbilities.FX_TAKEOFF -> {
                VfxDraw.shockwave(buf, m, f.a, 0.5D + 3.5D * ease(k), 0.8D * fade, JET[0], JET[1], JET[2], fade);
                VfxDraw.flare(buf, m, f.a.add(0.0D, 0.3D, 0.0D), left, up, 0.9D, 1.0F, 0.7F, 0.4F, fade);
            }
            case DoomAbilities.FX_ANNIHILATE -> {
                // the God Emperor's annihilation: a river of gold-white light wound with green lightning
                Vec3 a = handOf(mc, f, pt);
                float w = 0.45F * (0.4F + fade);
                VfxDraw.beam(buf, m, a, f.b, w * 3.0F, GOD[0], GOD[1], GOD[2], 0.15F * fade);
                VfxDraw.energy(buf, m, a, f.b, w, GOD[0], GOD[1], GOD[2], fade);
                VfxDraw.energy(buf, m, a, f.b, w * 0.35F, WHITE[0], WHITE[1], WHITE[2], fade);
                for (int i = 0; i < 3; i++) {
                    bolt(buf, m, a, f.b, f.seed + i * 131L + (long) (t * 2.0F), 0.06F, GAUNT, fade, 1);
                }
                VfxDraw.flare(buf, m, a, left, up, 1.0D, GOD[0], GOD[1], GOD[2], fade);
                VfxDraw.flare(buf, m, f.b, left, up, 2.0D + 2.0D * k, GOD[0], GOD[1], GOD[2], fade);
                VfxDraw.sphere(buf, m, f.b, 1.0D + 3.0D * ease(k), cam, GOD[0], GOD[1], GOD[2], 0.6F * fade, t, 2.0D);
                VfxDraw.sparks(buf, m, f.b, f.seed, 40, 4.0D, k, 1.0D, GOD[0], GOD[1], GOD[2], 1.0F);
            }
            case DoomAbilities.FX_REWRITE -> {
                // Secret Wars: reality rewritten, a dome of gold and a sea of runes
                double r = f.power;
                Vec3 c = f.a;
                VfxDraw.flare(buf, m, c.add(0.0D, 1.0D, 0.0D), left, up, 4.0D, GOD[0], GOD[1], GOD[2],
                        Math.max(0.0F, 1.0F - age / 10.0F));
                VfxDraw.sphere(buf, m, c.add(0.0D, 1.0D, 0.0D), 1.0D + r * ease(k), cam, GOD[0], GOD[1], GOD[2],
                        0.5F * fade, t, 3.0D);
                VfxDraw.shockwave(buf, m, c, 1.0D + r * ease(k), 4.0D * fade, GOD[0], GOD[1], GOD[2], fade);
                VfxDraw.runeRing(buf, m, c.add(0.0D, 0.05D, 0.0D), 4.0D + r * 0.4D * ease(k), 18, t * 0.03F,
                        MYSTIC[0], MYSTIC[1], MYSTIC[2], fade);
                VfxDraw.pillar(buf, m, c, c.y, c.y + 80.0D, cam, 2.5F * fade, WHITE[0], WHITE[1], WHITE[2], 0.8F * fade);
                for (int i = 0; i < 4; i++) {
                    VfxDraw.sigil(buf, m, c.add(0.0D, 2.0D + i * 2.5D, 0.0D), yUp, 1.5D + i * 0.8D,
                            t * (i % 2 == 0 ? 1.0F : -1.0F), GOD[0], GOD[1], GOD[2], fade);
                }
            }
            case DoomAbilities.FX_ASCEND -> {
                // the ascension: a column of gold from the sky, rings of runes climbing him
                Entity e = mc.level.getEntity(f.caster);
                Vec3 c = e != null ? e.getPosition(pt) : f.a;
                float charge = Math.min(1.0F, age / Math.max(1.0F, f.power));
                float out = Math.min(1.0F, (f.life - age) / 10.0F);
                VfxDraw.pillar(buf, m, f.a, f.a.y - 1.0D, c.y + 80.0D, cam, (0.6F + 2.2F * charge) * out, GOD[0],
                        GOD[1], GOD[2], 0.6F * out);
                VfxDraw.pillar(buf, m, f.a, f.a.y - 1.0D, c.y + 80.0D, cam, (0.2F + 0.6F * charge) * out, WHITE[0],
                        WHITE[1], WHITE[2], 0.9F * out);
                for (int i = 0; i < 4; i++) {
                    double y = (age * 0.08D + i * 0.8D) % 3.2D;
                    VfxDraw.sigil(buf, m, c.add(0.0D, 0.2D + y, 0.0D), yUp, 1.6D - y * 0.25D,
                            t * (i % 2 == 0 ? 1.0F : -1.0F), GOD[0], GOD[1], GOD[2], out * (float) (1.0D - y / 3.2D));
                }
                VfxDraw.sphere(buf, m, c.add(0.0D, 1.0D, 0.0D), 1.2D + charge * 1.2D, cam, GOD[0], GOD[1], GOD[2],
                        0.5F * out, t, 2.0D);
                VfxDraw.flare(buf, m, c.add(0.0D, 1.0D, 0.0D), left, up, 1.5D + charge * 2.0D, GOD[0], GOD[1], GOD[2],
                        0.6F * out);
                VfxDraw.runeRing(buf, m, f.a.add(0.0D, 0.05D, 0.0D), 6.0D, 16, t * 0.02F, MYSTIC[0], MYSTIC[1],
                        MYSTIC[2], out);
            }
            case DoomAbilities.FX_ABSORB -> {
                // a fallen boss' power pulled into him: a stream of wisps and gold light
                Entity e = mc.level.getEntity(f.caster);
                Vec3 to = e != null ? e.getPosition(pt).add(0.0D, 1.0D, 0.0D) : f.b;
                float head = Math.min(1.0F, age / 25.0F);
                Vec3 tip = f.a.lerp(to, head);
                for (int i = 0; i < 4; i++) {
                    VfxDraw.wisp(buf, m, f.a, tip, f.seed + i, t, 0.7D, 0.16F, MYSTIC[0], MYSTIC[1], MYSTIC[2], fade);
                }
                VfxDraw.energy(buf, m, f.a, tip, 0.18F, GOD[0], GOD[1], GOD[2], fade);
                VfxDraw.flare(buf, m, tip, left, up, 0.8D, WHITE[0], WHITE[1], WHITE[2], fade);
            }
            case DoomAbilities.FX_GRIMOIRE -> {
                Entity e = mc.level.getEntity(f.caster);
                Vec3 c = e != null ? e.getPosition(pt) : f.a;
                float in = Math.min(1.0F, age / 8.0F);
                VfxDraw.runeRing(buf, m, c, 2.2D, 12, t * 0.05F, MYSTIC[0], MYSTIC[1], MYSTIC[2], in * fade);
                VfxDraw.hexCircle(buf, m, c.add(0.0D, 0.03D, 0.0D), 1.6D, t * 0.4F, FIELD[0], FIELD[1], FIELD[2],
                        in * fade);
                for (int i = 0; i < 6; i++) {
                    double ang = t * 0.05D + i * Math.PI / 3.0D;
                    Vec3 p0 = c.add(Math.cos(ang) * 2.2D, 0.0D, Math.sin(ang) * 2.2D);
                    VfxDraw.wisp(buf, m, p0, c.add(0.0D, 2.2D, 0.0D), f.seed + i, t, 0.4D, 0.1F, MYSTIC[0], MYSTIC[1],
                            MYSTIC[2], in * fade);
                }
                VfxDraw.sphere(buf, m, c.add(0.0D, 1.0D, 0.0D), 1.2D + 0.4D * k, cam, FIELD[0], FIELD[1], FIELD[2],
                        0.4F * fade, t, 2.0D);
            }
            default -> {
            }
        }
    }
}
