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
            default -> 0;
        };
        if (mc.level.getEntity(p.caster()) instanceof Player cp) {
            DoomPose.cast(cp, p.type(), p.power());
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
                if (DoomClient.shielded(p)) {
                    Vec3 c = p.getPosition(pt).add(0.0D, p.getBbHeight() * 0.5D, 0.0D);
                    double r = DoomAbilities.FIELD_RADIUS;
                    VfxDraw.sphere(buf, m, c, r, cam, FIELD[0], FIELD[1], FIELD[2], 0.5F, t, 2.6D);
                    VfxDraw.hexCircle(buf, m, c.add(0.0D, -p.getBbHeight() * 0.5D + 0.05D, 0.0D), r, t,
                            FIELD[0], FIELD[1], FIELD[2], 0.45F);
                    VfxDraw.ring(buf, m, c, new Vec3(0.0D, 1.0D, 0.0D), r + 0.05D, 0.05D, t * 0.05F, FIELD[0], FIELD[1],
                            FIELD[2], 0.5F + 0.2F * (float) Math.sin(t * 0.3F));
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
            if (e instanceof Player p && DoomClient.shielded(p)) {
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

    private static void draw(BufferBuilder buf, Matrix4f m, Minecraft mc, Fx f, float pt, float t, Vec3 cam,
                             Vec3 left, Vec3 up) {
        float age = f.age + pt;
        float k = Math.min(1.0F, age / f.life);
        float fade = 1.0F - k;
        switch (f.type) {
            case DoomAbilities.FX_BOLT -> {
                Vec3 a = handOf(mc, f, pt);
                Vec3[] pts = VfxDraw.jagged(a, f.b, f.seed + (long) (age * 0.5F), 0.22D, 4);
                VfxDraw.path(buf, m, pts, 0.08F, GAUNT[0], GAUNT[1], GAUNT[2], fade);
                VfxDraw.glow(buf, m, a, left, up, 0.4D, GAUNT[0], GAUNT[1], GAUNT[2], 0.8F * fade);
                if (f.power > 0.0F) {
                    VfxDraw.burst(buf, m, f.b, left, up, 0.9F + k, GAUNT[0], GAUNT[1], GAUNT[2], fade, t * 0.2F);
                }
            }
            case DoomAbilities.FX_BEAM -> {
                Vec3 a = handOf(mc, f, pt);
                VfxDraw.energy(buf, m, a, f.b, 0.14F, GAUNT[0], GAUNT[1], GAUNT[2], 0.9F * fade + 0.1F);
                Vec3[] arc = VfxDraw.jagged(a, f.b, f.seed, 0.35D, 4);
                VfxDraw.path(buf, m, arc, 0.04F, GAUNT[0], GAUNT[1], GAUNT[2], 0.7F * fade);
                VfxDraw.glow(buf, m, a, left, up, 0.5D, GAUNT[0], GAUNT[1], GAUNT[2], 0.9F);
                if (f.power > 0.0F) {
                    VfxDraw.glow(buf, m, f.b, left, up, 0.9D, GAUNT[0], GAUNT[1], GAUNT[2], 0.8F);
                }
            }
            case DoomAbilities.FX_DISCHARGE -> {
                Vec3 a = handOf(mc, f, pt);
                Vec3 dir = f.b.normalize();
                Vec3 side = new Vec3(-dir.z, 0.0D, dir.x);
                side = side.lengthSqr() < 1.0E-4D ? new Vec3(1.0D, 0.0D, 0.0D) : side.normalize();
                Vec3 vup = side.cross(dir).normalize();
                for (int i = 0; i < 9; i++) {
                    double ax = ((i * 53) % 9 - 4) / 4.0D * 0.55D;
                    double ay = ((i * 29) % 5 - 2) / 2.0D * 0.3D;
                    Vec3 d = dir.add(side.scale(ax)).add(vup.scale(ay)).normalize();
                    Vec3 end = a.add(d.scale(4.0D + 7.0D * Math.min(1.0F, age / 3.0F)));
                    VfxDraw.path(buf, m, VfxDraw.jagged(a, end, f.seed + i * 977L + (long) age, 0.5D, 4), 0.06F,
                            GAUNT[0], GAUNT[1], GAUNT[2], fade);
                }
                VfxDraw.burst(buf, m, a, left, up, 1.2F, GAUNT[0], GAUNT[1], GAUNT[2], fade, t * 0.3F);
            }
            case DoomAbilities.FX_SHIELD -> {
                Entity e = mc.level.getEntity(f.caster);
                Vec3 c = e != null ? e.getPosition(pt).add(0.0D, 1.0D, 0.0D) : f.a.add(0.0D, 1.0D, 0.0D);
                VfxDraw.sphere(buf, m, c, 0.3D + DoomAbilities.FIELD_RADIUS * k, cam, FIELD[0], FIELD[1], FIELD[2],
                        fade, t, 2.0D);
                VfxDraw.ring(buf, m, c, new Vec3(0.0D, 1.0D, 0.0D), 0.5D + 2.5D * k, 0.12D, t * 0.1F, FIELD[0],
                        FIELD[1], FIELD[2], fade);
            }
            case DoomAbilities.FX_SHIELD_HIT -> {
                Entity e = mc.level.getEntity(f.caster);
                if (e != null) {
                    Vec3 c = e.getPosition(pt).add(0.0D, 1.0D, 0.0D);
                    VfxDraw.sphere(buf, m, c, DoomAbilities.FIELD_RADIUS + 0.1D + 0.2D * k, cam, 0.8F, 1.0F, 0.8F,
                            0.8F * fade, t, 1.6D);
                }
            }
            case DoomAbilities.FX_WAVE -> {
                double r = 0.5D + f.power * k;
                Vec3 c = f.a;
                VfxDraw.ring(buf, m, c, new Vec3(0.0D, 1.0D, 0.0D), r, 0.35D, t * 0.1F, FIELD[0], FIELD[1], FIELD[2],
                        fade);
                VfxDraw.wall(buf, m, f.b, r, 1.6D * fade + 0.2D, FIELD[0], FIELD[1], FIELD[2], 0.6F * fade);
                VfxDraw.sphere(buf, m, c, r * 0.6D, cam, FIELD[0], FIELD[1], FIELD[2], 0.4F * fade, t, 3.0D);
            }
            case DoomAbilities.FX_MYSTIC -> {
                VfxDraw.energy(buf, m, f.a, f.b, 0.13F, MYSTIC[0], MYSTIC[1], MYSTIC[2], fade);
                Vec3[] hx = VfxDraw.helix(f.a, f.b, 0.22D, 1.5D, t * 0.6D, 10);
                VfxDraw.path(buf, m, hx, 0.035F, MYSTIC[0], MYSTIC[1], MYSTIC[2], 0.8F * fade);
                if (f.age < 2.0F) {
                    VfxDraw.glow(buf, m, f.b, left, up, 0.55D, MYSTIC[0], MYSTIC[1], MYSTIC[2], 1.0F);
                    VfxDraw.rune(buf, m, f.b.x, f.b.y, f.b.z, 0.25D, t * 0.3D, (int) (f.seed & 15), MYSTIC[0],
                            MYSTIC[1], MYSTIC[2], 0.9F);
                }
            }
            case DoomAbilities.FX_MYSTIC_HIT -> {
                VfxDraw.sphere(buf, m, f.a, 0.4D + 2.8D * k, cam, MYSTIC[0], MYSTIC[1], MYSTIC[2], fade, t, 2.2D);
                VfxDraw.burst(buf, m, f.a, left, up, 1.5F + 2.0F * k, MYSTIC[0], MYSTIC[1], MYSTIC[2], fade, t * 0.2F);
                VfxDraw.runeRing(buf, m, f.a.add(0.0D, -0.6D, 0.0D), 1.2D + 1.5D * k, 6, t * 0.08F, MYSTIC[0],
                        MYSTIC[1], MYSTIC[2], fade);
            }
            case DoomAbilities.FX_RITUAL -> {
                float charge = Math.min(1.0F, age / Math.max(1, f.target));
                double r = f.power * Math.min(1.0F, age / 6.0F);
                float a = 0.4F + 0.6F * charge;
                VfxDraw.runeRing(buf, m, f.a, r, 10, t * (0.03F + 0.12F * charge), MYSTIC[0], MYSTIC[1], MYSTIC[2], a);
                VfxDraw.hexCircle(buf, m, f.a.add(0.0D, 0.03D, 0.0D), r * 0.7D, t * 0.5F, FIELD[0], FIELD[1],
                        FIELD[2], a * 0.8F);
                VfxDraw.ring(buf, m, f.a.add(0.0D, 0.04D, 0.0D), new Vec3(0.0D, 1.0D, 0.0D), r * 0.35D, 0.06D,
                        -t * 0.15F, MYSTIC[0], MYSTIC[1], MYSTIC[2], a);
                VfxDraw.wall(buf, m, f.a, r, 0.6D * charge, MYSTIC[0], MYSTIC[1], MYSTIC[2], 0.35F * charge);
            }
            case DoomAbilities.FX_ERUPT -> {
                double r = f.power;
                VfxDraw.pillar(buf, m, f.a, f.a.y, f.a.y + 9.0D * (1.0F - fade * fade * 0.5F), cam,
                        (float) r * 0.9F * fade, MYSTIC[0], MYSTIC[1], MYSTIC[2], 0.7F * fade);
                VfxDraw.pillar(buf, m, f.a, f.a.y, f.a.y + 12.0D, cam, (float) r * 0.25F * fade, 1.0F, 1.0F, 0.9F,
                        0.9F * fade);
                VfxDraw.ring(buf, m, f.a.add(0.0D, 0.1D, 0.0D), new Vec3(0.0D, 1.0D, 0.0D), r + 4.0D * k, 0.4D,
                        t * 0.1F, MYSTIC[0], MYSTIC[1], MYSTIC[2], fade);
                VfxDraw.runeRing(buf, m, f.a, r, 10, t * 0.2F, MYSTIC[0], MYSTIC[1], MYSTIC[2], fade);
                VfxDraw.wall(buf, m, f.a, r, 3.5D * fade, MYSTIC[0], MYSTIC[1], MYSTIC[2], 0.5F * fade);
            }
            case DoomAbilities.FX_TAKEOFF -> {
                VfxDraw.ring(buf, m, f.a.add(0.0D, 0.05D, 0.0D), new Vec3(0.0D, 1.0D, 0.0D), 0.5D + 3.5D * k, 0.3D,
                        0.0F, JET[0], JET[1], JET[2], fade);
                VfxDraw.glow(buf, m, f.a.add(0.0D, 0.3D, 0.0D), left, up, 1.5D * fade, 1.0F, 0.7F, 0.4F, fade);
            }
            case DoomAbilities.FX_GRIMOIRE -> {
                Entity e = mc.level.getEntity(f.caster);
                Vec3 c = e != null ? e.getPosition(pt) : f.a;
                float in = Math.min(1.0F, age / 8.0F);
                VfxDraw.runeRing(buf, m, c, 2.2D, 12, t * 0.05F, MYSTIC[0], MYSTIC[1], MYSTIC[2], in * fade);
                VfxDraw.hexCircle(buf, m, c.add(0.0D, 0.03D, 0.0D), 1.6D, t * 0.4F, FIELD[0], FIELD[1], FIELD[2],
                        in * fade);
                VfxDraw.wall(buf, m, c, 2.2D, 2.6D * in, MYSTIC[0], MYSTIC[1], MYSTIC[2], 0.4F * fade);
                VfxDraw.sphere(buf, m, c.add(0.0D, 1.0D, 0.0D), 1.2D + 0.4D * k, cam, FIELD[0], FIELD[1], FIELD[2],
                        0.5F * fade, t, 2.0D);
            }
            default -> {
            }
        }
    }
}
