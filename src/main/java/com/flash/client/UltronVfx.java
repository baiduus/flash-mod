package com.flash.client;

import com.flash.FlashMod;
import com.flash.network.HeroFxPacket;
import com.flash.ultron.UltronAbilities;
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
 * Ultron's powers drawn in light (VfxDraw, additive): the red of his core with a white-hot centre. The repulsor
 * blast and its burst, the Annihilation Beam from the eyes, the thruster charge's trail, the grip of the claw and the
 * throw, the drones built out of light and their bolts, the hack's link and the code ring over what he holds, the
 * electromagnetic pulse, Ultron Prime's aura and the core's detonation.
 */
@Mod.EventBusSubscriber(modid = FlashMod.MODID, value = Dist.CLIENT)
public final class UltronVfx {

    private static final float[] RED = {1.0F, 0.16F, 0.08F};
    private static final float[] HOT = {1.0F, 0.55F, 0.3F};
    private static final float[] WHITE = {1.0F, 0.95F, 0.9F};

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

    private UltronVfx() {
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
            case UltronAbilities.FX_BLAST -> 8;
            case UltronAbilities.FX_BEAM -> 3;
            case UltronAbilities.FX_CHARGE -> (int) p.power() + 8;
            case UltronAbilities.FX_GRAB -> (int) p.power();
            case UltronAbilities.FX_THROW -> 10;
            case UltronAbilities.FX_BUILD -> 18;
            case UltronAbilities.FX_SHOT -> 6;
            case UltronAbilities.FX_HACK -> p.power() >= 3.0F ? 12 : 16;
            case UltronAbilities.FX_EMP -> 16;
            case UltronAbilities.FX_PRIME -> 24;
            case UltronAbilities.FX_NOVA_CHARGE -> (int) p.power();
            case UltronAbilities.FX_NOVA -> 26;
            case UltronAbilities.FX_TAKEOFF -> 12;
            case UltronAbilities.FX_HIT -> 8;
            default -> 0;
        };
        if (life > 0 && ACTIVE.size() < 400) {
            ACTIVE.add(new Fx(p, life));
        }
        Vec3 b = p.to();
        switch (p.type()) {
            case UltronAbilities.FX_BLAST, UltronAbilities.FX_SHOT -> {
                for (int i = 0; i < 8; i++) {
                    mc.level.addParticle(ParticleTypes.ELECTRIC_SPARK, b.x, b.y, b.z, rnd(0.4D), rnd(0.4D), rnd(0.4D));
                }
                mc.level.addParticle(ParticleTypes.SMALL_FLAME, b.x, b.y, b.z, 0.0D, 0.02D, 0.0D);
            }
            case UltronAbilities.FX_BEAM -> {
                if (p.power() > 0.0F) {
                    mc.level.addParticle(ParticleTypes.FLAME, b.x, b.y, b.z, rnd(0.1D), 0.03D, rnd(0.1D));
                    mc.level.addParticle(ParticleTypes.SMOKE, b.x, b.y, b.z, rnd(0.05D), 0.05D, rnd(0.05D));
                }
            }
            case UltronAbilities.FX_NOVA -> {
                Vec3 a = p.from();
                for (int i = 0; i < 80; i++) {
                    double th = Math.random() * Math.PI * 2.0D;
                    double sp = 0.6D + Math.random() * 0.8D;
                    mc.level.addParticle(ParticleTypes.FLAME, a.x, a.y, a.z, Math.cos(th) * sp, rnd(0.6D),
                            Math.sin(th) * sp);
                }
            }
            case UltronAbilities.FX_EMP -> {
                Vec3 a = p.from();
                for (int i = 0; i < 50; i++) {
                    double th = Math.random() * Math.PI * 2.0D;
                    mc.level.addParticle(ParticleTypes.ELECTRIC_SPARK, a.x, a.y - 0.6D, a.z, Math.cos(th) * 1.2D,
                            0.05D, Math.sin(th) * 1.2D);
                }
            }
            case UltronAbilities.FX_HIT -> {
                for (int i = 0; i < 12; i++) {
                    mc.level.addParticle(ParticleTypes.ELECTRIC_SPARK, p.from().x, p.from().y, p.from().z,
                            rnd(0.6D), rnd(0.6D), rnd(0.6D));
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

    private static boolean anyPrime(Minecraft mc) {
        for (Player p : mc.level.players()) {
            if (UltronClient.prime(p)) {
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
        if (mc.level == null || (ACTIVE.isEmpty() && !anyPrime(mc))) {
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
                if (UltronClient.prime(p) && !p.isInvisible()) {
                    // Ultron Prime: his core blazing, a red heat around him
                    Vec3 c = p.getPosition(pt).add(0.0D, p.getBbHeight() * 0.55D, 0.0D);
                    float pulse = 0.5F + 0.25F * (float) Math.sin(t * 0.3F + p.getId());
                    VfxDraw.glow(buf, m, c, left, up, 1.6D, RED[0], RED[1], RED[2], 0.22F * pulse);
                    java.util.Random rnd = new java.util.Random(p.getId() * 7L + (long) (t / 2.0F));
                    for (int i = 0; i < 2; i++) {
                        Vec3 p0 = p.getPosition(pt).add(rnd.nextGaussian() * 0.35D, 0.2D + rnd.nextDouble() * 1.6D,
                                rnd.nextGaussian() * 0.35D);
                        Vec3 p1 = p0.add(rnd.nextGaussian() * 0.5D, rnd.nextGaussian() * 0.4D, rnd.nextGaussian() * 0.5D);
                        Vec3[] arc = VfxDraw.jagged(p0, p1, rnd.nextLong(), 1.2D, 3);
                        VfxDraw.path(buf, m, arc, 0.06F, RED[0], RED[1], RED[2], 0.4F * pulse);
                        VfxDraw.path(buf, m, arc, 0.02F, WHITE[0], WHITE[1], WHITE[2], pulse);
                    }
                    VfxDraw.glow(buf, m, p.getPosition(pt).add(0.0D, p.getBbHeight() * 0.72D, 0.0D)
                            .add(p.getViewVector(pt).scale(0.25D)), left, up, 0.35D, WHITE[0], WHITE[1], WHITE[2],
                            0.7F * pulse);
                    VfxDraw.ring(buf, m, p.getPosition(pt).add(0.0D, 0.05D, 0.0D), new Vec3(0.0D, 1.0D, 0.0D),
                            0.9D, 0.08D, t * 0.08F, RED[0], RED[1], RED[2], 0.5F * pulse);
                }
            }
        } finally {
            VfxDraw.end(buf);
            ps.popPose();
        }
    }

    /** The caster's palm (first person: from the camera's lower right). */
    private static Vec3 palm(Minecraft mc, Fx f, float pt) {
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
            return p.getEyePosition(pt).add(look.scale(0.6D)).add(side).add(0.0D, -0.42D, 0.0D);
        }
        return f.a;
    }

    /** The two eyes (first person: just under the camera). */
    private static Vec3[] eyes(Minecraft mc, Fx f, float pt) {
        Entity e = mc.level.getEntity(f.caster);
        if (e instanceof Player p) {
            Vec3 look = p.getViewVector(pt);
            Vec3 side = new Vec3(-look.z, 0.0D, look.x).normalize().scale(0.1D);
            boolean me = p == mc.getCameraEntity() && mc.options.getCameraType().isFirstPerson();
            Vec3 c = p.getEyePosition(pt).add(look.scale(me ? 0.4D : 0.28D)).add(0.0D, me ? -0.15D : 0.0D, 0.0D);
            return new Vec3[] {c.add(side), c.subtract(side)};
        }
        return new Vec3[] {f.a, f.a};
    }

    private static Vec3 entityAt(Minecraft mc, int id, float pt, Vec3 fallback) {
        Entity e = id >= 0 ? mc.level.getEntity(id) : null;
        return e != null ? e.getPosition(pt).add(0.0D, e.getBbHeight() * 0.55D, 0.0D) : fallback;
    }

    private static void draw(BufferBuilder buf, Matrix4f m, Minecraft mc, Fx f, float pt, float t, Vec3 cam,
                             Vec3 left, Vec3 up) {
        float age = f.age + pt;
        float k = Math.min(1.0F, age / Math.max(1, f.life));
        float fade = 1.0F - k;
        switch (f.type) {
            case UltronAbilities.FX_BLAST -> {
                // a repulsor blast: a bolt of red light out of the palm, a flare, a burst of fire where it lands
                Vec3 a = palm(mc, f, pt);
                float reach = Math.min(1.0F, age / 2.0F);
                Vec3 tip = a.lerp(f.b, reach);
                VfxDraw.beam(buf, m, a, tip, 0.6F, RED[0], RED[1], RED[2], 0.12F * fade);
                VfxDraw.energy(buf, m, a, tip, 0.15F, RED[0], RED[1], RED[2], fade);
                VfxDraw.path(buf, m, VfxDraw.helix(a, tip, 0.18D, Math.max(2.0D, a.distanceTo(tip) / 1.5D), t * 0.8D,
                        40), 0.03F, HOT[0], HOT[1], HOT[2], 0.6F * fade);
                VfxDraw.flare(buf, m, a, left, up, 0.4D, RED[0], RED[1], RED[2], fade);
                VfxDraw.flare(buf, m, tip, left, up, 0.5D, HOT[0], HOT[1], HOT[2], fade);
                if (reach >= 1.0F) {
                    VfxDraw.sphere(buf, m, f.b, 0.3D + 1.4D * ease(k), cam, HOT[0], HOT[1], HOT[2], 0.6F * fade, t, 2.0D);
                    VfxDraw.burst(buf, m, f.b, left, up, 0.8F + 1.8F * k, RED[0], RED[1], RED[2], fade, t * 0.2F);
                    VfxDraw.sparks(buf, m, f.b, f.seed, 18, 2.0D, k, 0.8D, HOT[0], HOT[1], HOT[2], 1.0F);
                }
            }
            case UltronAbilities.FX_BEAM -> {
                // the Annihilation Beam out of the eyes: two white-hot filaments in a red corona, data running down
                Vec3[] e = eyes(mc, f, pt);
                float flick = 0.85F + 0.15F * (float) Math.sin(t * 2.3F);
                for (Vec3 a : e) {
                    VfxDraw.beam(buf, m, a, f.b, 0.3F, RED[0], RED[1], RED[2], 0.1F * flick);
                    VfxDraw.energy(buf, m, a, f.b, 0.065F, RED[0], RED[1], RED[2], flick);
                    VfxDraw.beam(buf, m, a, f.b, 0.015F, 1.0F, 1.0F, 1.0F, flick);
                    VfxDraw.flare(buf, m, a, left, up, 0.14D, RED[0], RED[1], RED[2], flick);
                }
                Vec3 mid = e[0].lerp(e[1], 0.5D);
                double len = mid.distanceTo(f.b);
                if (len > 0.5D) {
                    Vec3 dn = f.b.subtract(mid).scale(1.0D / len);
                    for (int i = 0; i < 5; i++) {
                        double pos = (t * 1.2D + i * len / 5.0D) % len;
                        VfxDraw.glow(buf, m, mid.add(dn.scale(pos)), left, up, 0.09D, WHITE[0], WHITE[1], WHITE[2], 0.8F);
                    }
                }
                if (f.power > 0.0F) {
                    VfxDraw.flare(buf, m, f.b, left, up, 0.7D, HOT[0], HOT[1], HOT[2], 1.0F);
                    VfxDraw.burst(buf, m, f.b, left, up, 1.0F, RED[0], RED[1], RED[2], 0.7F, t * 0.4F);
                    long gen = (long) (t / 4.0F);
                    VfxDraw.sparks(buf, m, f.b, f.seed + gen * 977L, 8, 1.4D, (t - gen * 4.0F) / 8.0F, 0.7D, HOT[0],
                            HOT[1], HOT[2], 1.0F);
                }
            }
            case UltronAbilities.FX_CHARGE -> {
                // the thruster charge: a streak of red-hot exhaust behind him, the air crushed before him
                Entity e = mc.level.getEntity(f.caster);
                Vec3 now = e != null ? e.getPosition(pt).add(0.0D, 0.9D, 0.0D) : f.b;
                Vec3 start = f.a.add(0.0D, 0.9D, 0.0D);
                VfxDraw.beam(buf, m, start, now, 1.0F * fade, RED[0], RED[1], RED[2], 0.12F * fade);
                VfxDraw.energy(buf, m, start, now, 0.35F * fade, RED[0], RED[1], RED[2], fade);
                VfxDraw.energy(buf, m, start, now, 0.1F * fade, WHITE[0], WHITE[1], WHITE[2], fade);
                Vec3 dir = e != null ? e.getViewVector(pt) : new Vec3(0, 0, 1);
                VfxDraw.cone(buf, m, now.add(dir.scale(1.2D)), dir.scale(-1.0D), 1.6D, 1.1D, 1.0F, 0.8F, 0.75F,
                        0.15F * fade);
                VfxDraw.ring(buf, m, now, dir, 1.0D, 0.15D, t * 0.3F, HOT[0], HOT[1], HOT[2], fade);
            }
            case UltronAbilities.FX_GRAB -> {
                // the grip: a claw of red energy closing round the creature, lightning holding it
                Vec3 a = palm(mc, f, pt);
                Vec3 b = entityAt(mc, f.target, pt, f.b);
                float in = Math.min(1.0F, age / 3.0F);
                for (int i = 0; i < 2; i++) {
                    Vec3[] pts = VfxDraw.jagged(a, b, f.seed + i * 31L + (long) (age * 0.5F), 0.8D, 4);
                    VfxDraw.path(buf, m, pts, 0.12F, RED[0], RED[1], RED[2], 0.3F * in);
                    VfxDraw.path(buf, m, pts, 0.03F, WHITE[0], WHITE[1], WHITE[2], in);
                }
                for (int i = 0; i < 4; i++) {
                    double ang = t * 0.15D + i * Math.PI / 2.0D;
                    Vec3 tipC = b.add(Math.cos(ang) * 0.7D, 0.6D - i % 2 * 0.3D, Math.sin(ang) * 0.7D);
                    VfxDraw.energy(buf, m, b.add(Math.cos(ang) * 0.9D, -0.4D, Math.sin(ang) * 0.9D), tipC, 0.05F,
                            RED[0], RED[1], RED[2], in);
                }
                VfxDraw.ring(buf, m, b, new Vec3(0.0D, 1.0D, 0.0D), 0.75D, 0.06D, t * 0.2F, HOT[0], HOT[1], HOT[2], in);
                VfxDraw.flare(buf, m, a, left, up, 0.3D, RED[0], RED[1], RED[2], in);
            }
            case UltronAbilities.FX_THROW -> {
                Vec3 b = entityAt(mc, f.target, pt, f.b);
                VfxDraw.energy(buf, m, f.a, b, 0.3F * fade, RED[0], RED[1], RED[2], fade);
                VfxDraw.flare(buf, m, f.a, left, up, 1.0D, HOT[0], HOT[1], HOT[2], fade);
                VfxDraw.ring(buf, m, f.a, b.subtract(f.a), 0.5D + 1.5D * ease(k), 0.1D, 0.0F, WHITE[0], WHITE[1],
                        WHITE[2], fade);
            }
            case UltronAbilities.FX_BUILD -> {
                // a drone woven out of light: scan lines sweeping a column, wireframe rings closing, a flash
                Vec3 c = entityAt(mc, f.target, pt, f.a);
                float al = fade * f.power;
                for (int i = 0; i < 5; i++) {
                    double y = (i - 2.0D) * 0.3D * (1.0D - k);
                    VfxDraw.ring(buf, m, c.add(0.0D, y, 0.0D), new Vec3(0.0D, 1.0D, 0.0D), 0.25D + 0.6D * fade,
                            0.04D, t * 0.3F * (i % 2 == 0 ? 1 : -1), RED[0], RED[1], RED[2], al);
                }
                double scan = Math.sin(t * 0.6D) * 0.6D * fade;
                VfxDraw.ring(buf, m, c.add(0.0D, scan, 0.0D), new Vec3(0.0D, 1.0D, 0.0D), 0.7D * fade + 0.2D, 0.02D,
                        0.0F, WHITE[0], WHITE[1], WHITE[2], al);
                VfxDraw.flare(buf, m, c, left, up, 0.3D + 0.9D * k, HOT[0], HOT[1], HOT[2], al);
            }
            case UltronAbilities.FX_SHOT -> {
                Vec3 b = entityAt(mc, f.target, pt, f.b);
                float reach = Math.min(1.0F, age / 2.0F);
                Vec3 tip = f.a.lerp(b, reach);
                VfxDraw.energy(buf, m, f.a.lerp(b, Math.max(0.0F, reach - 0.4F)), tip, 0.06F, RED[0], RED[1], RED[2],
                        fade);
                VfxDraw.flare(buf, m, tip, left, up, 0.2D, HOT[0], HOT[1], HOT[2], fade);
            }
            case UltronAbilities.FX_HACK -> {
                if (f.power >= 3.0F) {
                    // a creature he holds: a ring of red code over its head, its eyes red
                    Entity e = mc.level.getEntity(f.target);
                    if (e != null) {
                        Vec3 c = e.getPosition(pt).add(0.0D, e.getBbHeight() + 0.35D, 0.0D);
                        VfxDraw.sigil(buf, m, c, new Vec3(0.0D, 1.0D, 0.0D), 0.38D, t * 2.0F, RED[0], RED[1], RED[2],
                                0.8F * fade);
                        VfxDraw.flare(buf, m, e.getEyePosition(pt), left, up, 0.12D, RED[0], RED[1], RED[2], fade);
                    }
                    break;
                }
                // the hack: a stream of data racing to it, blocks of code climbing the line, a ring locking on
                Vec3 a = f.power > 0.0F ? palm(mc, f, pt) : f.a;
                Vec3 b = f.power == 2.0F ? f.b : entityAt(mc, f.target, pt, f.b);
                float in = Math.min(1.0F, age / 3.0F);
                Vec3 tip = a.lerp(b, in);
                VfxDraw.path(buf, m, VfxDraw.jagged(a, tip, f.seed, 0.3D, 4), 0.025F, RED[0], RED[1], RED[2], fade);
                double len = a.distanceTo(tip);
                if (len > 0.3D) {
                    Vec3 dn = tip.subtract(a).scale(1.0D / len);
                    java.util.Random rnd = new java.util.Random(f.seed);
                    for (int i = 0; i < 10; i++) {
                        double pos = (t * (0.8D + rnd.nextDouble() * 0.6D) + rnd.nextDouble() * len) % len;
                        Vec3 q = a.add(dn.scale(pos)).add(rnd.nextGaussian() * 0.08D, rnd.nextGaussian() * 0.08D,
                                rnd.nextGaussian() * 0.08D);
                        VfxDraw.beam(buf, m, q, q.add(dn.scale(0.15D)), 0.03F, HOT[0], HOT[1], HOT[2], 0.9F * fade);
                    }
                }
                if (in >= 1.0F) {
                    VfxDraw.sigil(buf, m, b, b.subtract(a), 0.4D + 0.3D * k, t * 3.0F, RED[0], RED[1], RED[2], fade);
                    VfxDraw.flare(buf, m, b, left, up, 0.4D, HOT[0], HOT[1], HOT[2], fade);
                }
            }
            case UltronAbilities.FX_EMP -> {
                // the electromagnetic pulse: a sphere of red static bursting out, lightning crawling over it
                double r = f.power * ease(Math.min(1.0F, age / 6.0F));
                Vec3 c = f.a;
                VfxDraw.flare(buf, m, c, left, up, 1.8D, RED[0], RED[1], RED[2], Math.max(0.0F, 1.0F - age / 4.0F));
                VfxDraw.sphere(buf, m, c, r, cam, RED[0], RED[1], RED[2], 0.5F * fade, t, 2.4D);
                VfxDraw.shockwave(buf, m, f.b, r, 1.4D * fade, HOT[0], HOT[1], HOT[2], fade);
                java.util.Random rnd = new java.util.Random(f.seed + (long) (age / 2.0F));
                for (int i = 0; i < 10; i++) {
                    double th = rnd.nextDouble() * Math.PI * 2.0D;
                    double ph = (rnd.nextDouble() - 0.5D) * Math.PI;
                    Vec3 p0 = c.add(Math.cos(th) * Math.cos(ph) * r, Math.sin(ph) * r, Math.sin(th) * Math.cos(ph) * r);
                    Vec3 p1 = c.add(Math.cos(th + 0.5D) * Math.cos(ph) * r, Math.sin(ph + 0.3D) * r,
                            Math.sin(th + 0.5D) * Math.cos(ph) * r);
                    Vec3[] pts = VfxDraw.jagged(p0, p1, rnd.nextLong(), 1.2D, 3);
                    VfxDraw.path(buf, m, pts, 0.06F, RED[0], RED[1], RED[2], 0.4F * fade);
                    VfxDraw.path(buf, m, pts, 0.02F, WHITE[0], WHITE[1], WHITE[2], fade);
                }
                if (age < 1.0F) {
                    HulkVfx.quake(mc, c, 0.8F, 20.0D);
                }
            }
            case UltronAbilities.FX_PRIME -> {
                // Ultron Prime: the core erupting, a column of red, rings of power off the ground
                Entity e = mc.level.getEntity(f.caster);
                Vec3 c = e != null ? e.getPosition(pt) : f.a;
                VfxDraw.flare(buf, m, c.add(0.0D, 1.2D, 0.0D), left, up, 2.0D, RED[0], RED[1], RED[2],
                        Math.max(0.0F, 1.0F - age / 8.0F));
                VfxDraw.pillar(buf, m, c, c.y, c.y + 10.0D * ease(k) + 1.0D, cam, 1.2F * fade, RED[0], RED[1], RED[2],
                        0.6F * fade);
                VfxDraw.shockwave(buf, m, c, 0.5D + 4.0D * ease(k), 1.5D * fade, HOT[0], HOT[1], HOT[2], fade);
                VfxDraw.sphere(buf, m, c.add(0.0D, 1.0D, 0.0D), 0.8D + 1.4D * ease(k), cam, RED[0], RED[1], RED[2],
                        0.6F * fade, t, 2.0D);
                VfxDraw.sparks(buf, m, c.add(0.0D, 1.0D, 0.0D), f.seed, 30, 3.0D, k, 1.0D, HOT[0], HOT[1], HOT[2],
                        1.0F);
            }
            case UltronAbilities.FX_NOVA_CHARGE -> {
                // the core gathering itself: everything drawn in, a sphere shrinking white-hot
                Entity e = mc.level.getEntity(f.caster);
                Vec3 c = e != null ? e.getPosition(pt).add(0.0D, 1.0D, 0.0D) : f.a.add(0.0D, 1.0D, 0.0D);
                float charge = Math.min(1.0F, age / Math.max(1.0F, f.power));
                VfxDraw.sphere(buf, m, c, 2.4D - 1.8D * charge, cam, RED[0], RED[1], RED[2], 0.3F + 0.5F * charge, t,
                        2.0D);
                VfxDraw.flare(buf, m, c, left, up, 0.4D + 1.4D * charge, HOT[0], HOT[1], HOT[2], charge);
                for (int i = 0; i < 3; i++) {
                    VfxDraw.ring(buf, m, c, new Vec3(Math.sin(i * 2.1D), Math.cos(i * 2.1D), 0.4D).normalize(),
                            0.9D + 0.4D * i * (1.0F - charge), 0.05D, t * (0.2F + 0.1F * i), HOT[0], HOT[1], HOT[2],
                            charge);
                }
                java.util.Random rnd = new java.util.Random(f.caster * 17L + (long) (t / 2.0F));
                for (int i = 0; i < 8; i++) {
                    Vec3 o = c.add(rnd.nextGaussian() * 3.0D, rnd.nextGaussian() * 2.0D, rnd.nextGaussian() * 3.0D);
                    VfxDraw.beam(buf, m, o, o.lerp(c, 0.4D), 0.025F, HOT[0], HOT[1], HOT[2], 0.6F * charge);
                }
            }
            case UltronAbilities.FX_NOVA -> {
                // the core's detonation: a blinding flash, a wave of red fire, the sky lit
                double r = f.power * ease(Math.min(1.0F, age / 5.0F));
                VfxDraw.flare(buf, m, f.a, left, up, 5.0D, WHITE[0], WHITE[1], WHITE[2],
                        Math.max(0.0F, 1.0F - age / 8.0F));
                VfxDraw.sphere(buf, m, f.a, r, cam, HOT[0], HOT[1], HOT[2], fade, t, 1.6D);
                VfxDraw.sphere(buf, m, f.a, r * 0.6D, cam, WHITE[0], WHITE[1], WHITE[2], 0.7F * fade * fade, t, 3.0D);
                VfxDraw.shockwave(buf, m, f.a.add(0.0D, -0.9D, 0.0D), r * 1.4D, 2.5D * fade, RED[0], RED[1], RED[2],
                        fade);
                VfxDraw.pillar(buf, m, f.a, f.a.y - 1.0D, f.a.y + 18.0D, cam, (float) r * 0.3F * fade, HOT[0],
                        HOT[1], HOT[2], 0.6F * fade);
                for (int i = 0; i < 12; i++) {
                    VfxDraw.crack(buf, m, f.a.add(0.0D, -0.9D, 0.0D), Math.PI * 2.0D * i / 12.0D, r, f.seed + i,
                            Math.min(1.0F, age / 5.0F), 0.08D, RED[0], RED[1], RED[2], fade);
                }
                VfxDraw.sparks(buf, m, f.a, f.seed, 70, r * 1.2D, k, 2.0D, HOT[0], HOT[1], HOT[2], 1.0F);
                if (age < 1.0F) {
                    HulkVfx.quake(mc, f.a, 2.0F, 40.0D);
                }
            }
            case UltronAbilities.FX_TAKEOFF -> {
                VfxDraw.shockwave(buf, m, f.a, 0.5D + 3.5D * ease(k), 0.9D * fade, RED[0], RED[1], RED[2], fade);
                VfxDraw.flare(buf, m, f.a.add(0.0D, 0.3D, 0.0D), left, up, 1.0D, HOT[0], HOT[1], HOT[2], fade);
            }
            case UltronAbilities.FX_HIT -> {
                VfxDraw.flare(buf, m, f.a, left, up, 0.6D, HOT[0], HOT[1], HOT[2], fade);
                VfxDraw.burst(buf, m, f.a, left, up, 0.6F + 1.2F * k, RED[0], RED[1], RED[2], fade, t * 0.3F);
                VfxDraw.sparks(buf, m, f.a, f.seed, 12, 1.4D, k, 0.6D, HOT[0], HOT[1], HOT[2], 1.0F);
            }
            default -> {
            }
        }
    }

    private static float ease(float k) {
        float u = 1.0F - Math.min(1.0F, Math.max(0.0F, k));
        return 1.0F - u * u * u;
    }
}
