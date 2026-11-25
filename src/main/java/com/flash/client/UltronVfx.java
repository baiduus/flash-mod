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
                    VfxDraw.glow(buf, m, c, left, up, 1.4D, RED[0], RED[1], RED[2], 0.22F * pulse);
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
                Vec3 a = palm(mc, f, pt);
                // the bolt flies out in the first ticks, then the line fades
                float reach = Math.min(1.0F, age / 2.0F);
                Vec3 tip = a.lerp(f.b, reach);
                VfxDraw.energy(buf, m, a, tip, 0.16F, RED[0], RED[1], RED[2], fade);
                VfxDraw.glow(buf, m, a, left, up, 0.45D, HOT[0], HOT[1], HOT[2], fade);
                VfxDraw.glow(buf, m, tip, left, up, 0.6D, WHITE[0], WHITE[1], WHITE[2], fade);
                if (reach >= 1.0F) {
                    VfxDraw.burst(buf, m, f.b, left, up, 0.8F + 1.6F * k, RED[0], RED[1], RED[2], fade, t * 0.2F);
                    VfxDraw.sphere(buf, m, f.b, 0.3D + 1.2D * k, cam, HOT[0], HOT[1], HOT[2], 0.6F * fade, t, 2.0D);
                }
            }
            case UltronAbilities.FX_BEAM -> {
                Vec3[] e = eyes(mc, f, pt);
                float flick = 0.85F + 0.15F * (float) Math.sin(t * 2.3F);
                for (Vec3 a : e) {
                    VfxDraw.energy(buf, m, a, f.b, 0.07F, RED[0], RED[1], RED[2], flick);
                    VfxDraw.glow(buf, m, a, left, up, 0.16D, WHITE[0], WHITE[1], WHITE[2], 0.9F);
                }
                VfxDraw.energy(buf, m, e[0].lerp(e[1], 0.5D), f.b, 0.2F, RED[0], RED[1], RED[2], 0.35F * flick);
                if (f.power > 0.0F) {
                    VfxDraw.glow(buf, m, f.b, left, up, 0.8D, HOT[0], HOT[1], HOT[2], 0.9F);
                    VfxDraw.burst(buf, m, f.b, left, up, 0.7F, RED[0], RED[1], RED[2], 0.7F, t * 0.4F);
                }
            }
            case UltronAbilities.FX_CHARGE -> {
                Entity e = mc.level.getEntity(f.caster);
                Vec3 now = e != null ? e.getPosition(pt).add(0.0D, 0.9D, 0.0D) : f.b;
                Vec3 start = f.a.add(0.0D, 0.9D, 0.0D);
                VfxDraw.energy(buf, m, start, now, 0.5F * fade, RED[0], RED[1], RED[2], fade);
                VfxDraw.energy(buf, m, start, now, 0.15F * fade, WHITE[0], WHITE[1], WHITE[2], fade);
                VfxDraw.ring(buf, m, now, e != null ? e.getViewVector(pt) : new Vec3(0, 0, 1), 0.9D, 0.18D,
                        t * 0.3F, HOT[0], HOT[1], HOT[2], fade);
            }
            case UltronAbilities.FX_GRAB -> {
                Vec3 a = palm(mc, f, pt);
                Vec3 b = entityAt(mc, f.target, pt, f.b);
                float in = Math.min(1.0F, age / 3.0F);
                VfxDraw.path(buf, m, VfxDraw.jagged(a, b, f.seed + (long) (age * 0.5F), 0.18D, 3), 0.05F, RED[0],
                        RED[1], RED[2], in);
                VfxDraw.energy(buf, m, a, b, 0.08F, HOT[0], HOT[1], HOT[2], 0.6F * in);
                VfxDraw.ring(buf, m, b, new Vec3(0.0D, 1.0D, 0.0D), 0.7D, 0.08D, t * 0.2F, RED[0], RED[1], RED[2],
                        in);
                VfxDraw.ring(buf, m, b.add(0.0D, 0.4D, 0.0D), new Vec3(0.0D, 1.0D, 0.0D), 0.55D, 0.06D, -t * 0.25F,
                        HOT[0], HOT[1], HOT[2], in);
            }
            case UltronAbilities.FX_THROW -> {
                Vec3 b = entityAt(mc, f.target, pt, f.b);
                VfxDraw.energy(buf, m, f.a, b, 0.3F * fade, RED[0], RED[1], RED[2], fade);
                VfxDraw.burst(buf, m, f.a, left, up, 1.2F + k, HOT[0], HOT[1], HOT[2], fade, t * 0.3F);
            }
            case UltronAbilities.FX_BUILD -> {
                // the drone woven out of light: a column of rings closing, then a flash
                Vec3 c = entityAt(mc, f.target, pt, f.a);
                for (int i = 0; i < 4; i++) {
                    double y = (i - 1.5D) * 0.35D * (1.0D - k);
                    VfxDraw.ring(buf, m, c.add(0.0D, y, 0.0D), new Vec3(0.0D, 1.0D, 0.0D), 0.25D + 0.6D * fade,
                            0.06D, t * 0.3F * (i % 2 == 0 ? 1 : -1), RED[0], RED[1], RED[2], fade * f.power);
                }
                VfxDraw.glow(buf, m, c, left, up, 0.4D + 0.8D * fade, HOT[0], HOT[1], HOT[2], fade * f.power);
            }
            case UltronAbilities.FX_SHOT -> {
                Vec3 b = entityAt(mc, f.target, pt, f.b);
                float reach = Math.min(1.0F, age / 2.0F);
                Vec3 tip = f.a.lerp(b, reach);
                VfxDraw.energy(buf, m, f.a, tip, 0.07F, RED[0], RED[1], RED[2], fade);
                VfxDraw.glow(buf, m, tip, left, up, 0.3D, WHITE[0], WHITE[1], WHITE[2], fade);
            }
            case UltronAbilities.FX_HACK -> {
                if (f.power >= 3.0F) {
                    // a creature he holds: the red code ring over its head
                    Entity e = mc.level.getEntity(f.target);
                    if (e != null) {
                        Vec3 c = e.getPosition(pt).add(0.0D, e.getBbHeight() + 0.35D, 0.0D);
                        VfxDraw.hexCircle(buf, m, c, 0.35D, t, RED[0], RED[1], RED[2], 0.7F * fade);
                        VfxDraw.ring(buf, m, c, new Vec3(0.0D, 1.0D, 0.0D), 0.4D, 0.04D, t * 0.2F, HOT[0], HOT[1],
                                HOT[2], 0.8F * fade);
                    }
                    break;
                }
                Vec3 a = f.power > 0.0F ? palm(mc, f, pt) : f.a;
                Vec3 b = f.power == 2.0F ? f.b : entityAt(mc, f.target, pt, f.b);
                float in = Math.min(1.0F, age / 3.0F);
                Vec3 tip = a.lerp(b, in);
                VfxDraw.path(buf, m, VfxDraw.jagged(a, tip, f.seed, 0.12D, 4), 0.035F, RED[0], RED[1], RED[2], fade);
                VfxDraw.path(buf, m, VfxDraw.helix(a, tip, 0.12D, 4.0D, t * 0.5D, 24), 0.02F, HOT[0], HOT[1],
                        HOT[2], fade);
                if (in >= 1.0F) {
                    VfxDraw.hexCircle(buf, m, b, 0.6D * (0.5F + k), t, RED[0], RED[1], RED[2], fade);
                    VfxDraw.glow(buf, m, b, left, up, 0.5D, HOT[0], HOT[1], HOT[2], fade);
                }
            }
            case UltronAbilities.FX_EMP -> {
                double r = f.power * Math.min(1.0F, age / 6.0F);
                Vec3 c = f.a;
                VfxDraw.sphere(buf, m, c, r, cam, RED[0], RED[1], RED[2], 0.55F * fade, t, 2.4D);
                VfxDraw.ring(buf, m, f.b.add(0.0D, 0.1D, 0.0D), new Vec3(0.0D, 1.0D, 0.0D), r, 0.3D, t * 0.1F,
                        HOT[0], HOT[1], HOT[2], fade);
                VfxDraw.wall(buf, m, f.b, r, 1.2D * fade + 0.2D, RED[0], RED[1], RED[2], 0.5F * fade);
                for (int i = 0; i < 6; i++) {
                    double th = i * Math.PI / 3.0D + f.seed % 7;
                    Vec3 end = c.add(Math.cos(th) * r, Math.sin(th * 1.7D) * 0.6D, Math.sin(th) * r);
                    VfxDraw.path(buf, m, VfxDraw.jagged(c, end, f.seed + i * 131L + (long) age, 0.5D, 3), 0.04F,
                            WHITE[0], WHITE[1], WHITE[2], fade);
                }
            }
            case UltronAbilities.FX_PRIME -> {
                Entity e = mc.level.getEntity(f.caster);
                Vec3 c = e != null ? e.getPosition(pt) : f.a;
                VfxDraw.pillar(buf, m, c, c.y, c.y + 6.0D * fade + 1.0D, cam, 1.2F * fade, RED[0], RED[1], RED[2],
                        0.6F * fade);
                VfxDraw.ring(buf, m, c.add(0.0D, 0.1D, 0.0D), new Vec3(0.0D, 1.0D, 0.0D), 0.5D + 3.0D * k, 0.3D,
                        t * 0.1F, HOT[0], HOT[1], HOT[2], fade);
                VfxDraw.sphere(buf, m, c.add(0.0D, 1.0D, 0.0D), 0.8D + 1.2D * k, cam, RED[0], RED[1], RED[2],
                        0.6F * fade, t, 2.0D);
            }
            case UltronAbilities.FX_NOVA_CHARGE -> {
                Entity e = mc.level.getEntity(f.caster);
                Vec3 c = e != null ? e.getPosition(pt).add(0.0D, 1.0D, 0.0D) : f.a.add(0.0D, 1.0D, 0.0D);
                float charge = Math.min(1.0F, age / Math.max(1.0F, f.power));
                VfxDraw.sphere(buf, m, c, 2.2D - 1.6D * charge, cam, RED[0], RED[1], RED[2], 0.3F + 0.5F * charge, t,
                        2.0D);
                VfxDraw.glow(buf, m, c, left, up, 0.5D + 1.2D * charge, WHITE[0], WHITE[1], WHITE[2], charge);
                for (int i = 0; i < 3; i++) {
                    VfxDraw.ring(buf, m, c, new Vec3(Math.sin(i * 2.1D), Math.cos(i * 2.1D), 0.4D).normalize(),
                            0.9D + 0.4D * i * (1.0F - charge), 0.05D, t * (0.2F + 0.1F * i), HOT[0], HOT[1], HOT[2],
                            charge);
                }
            }
            case UltronAbilities.FX_NOVA -> {
                double r = f.power * Math.min(1.0F, age / 5.0F);
                VfxDraw.sphere(buf, m, f.a, r, cam, HOT[0], HOT[1], HOT[2], fade, t, 1.6D);
                VfxDraw.sphere(buf, m, f.a, r * 0.6D, cam, WHITE[0], WHITE[1], WHITE[2], 0.7F * fade * fade, t, 3.0D);
                VfxDraw.ring(buf, m, f.a.add(0.0D, -0.9D, 0.0D), new Vec3(0.0D, 1.0D, 0.0D), r * 1.3D, 0.6D,
                        t * 0.1F, RED[0], RED[1], RED[2], fade);
                VfxDraw.burst(buf, m, f.a, left, up, (float) r * 1.2F, RED[0], RED[1], RED[2], fade, t * 0.1F);
                VfxDraw.pillar(buf, m, f.a, f.a.y - 1.0D, f.a.y + 14.0D, cam, (float) r * 0.3F * fade, HOT[0],
                        HOT[1], HOT[2], 0.6F * fade);
            }
            case UltronAbilities.FX_TAKEOFF -> {
                VfxDraw.ring(buf, m, f.a.add(0.0D, 0.05D, 0.0D), new Vec3(0.0D, 1.0D, 0.0D), 0.5D + 3.5D * k, 0.3D,
                        0.0F, RED[0], RED[1], RED[2], fade);
                VfxDraw.glow(buf, m, f.a.add(0.0D, 0.3D, 0.0D), left, up, 1.5D * fade, HOT[0], HOT[1], HOT[2], fade);
            }
            case UltronAbilities.FX_HIT -> {
                VfxDraw.burst(buf, m, f.a, left, up, 0.6F + 1.2F * k, RED[0], RED[1], RED[2], fade, t * 0.3F);
                VfxDraw.glow(buf, m, f.a, left, up, 0.7D * fade, WHITE[0], WHITE[1], WHITE[2], fade);
            }
            default -> {
            }
        }
    }
}
