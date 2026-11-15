package com.flash.client;

import com.flash.FlashMod;
import com.flash.network.HeroFxPacket;
import com.flash.wonder.WonderAbilities;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
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
 * Wonder Woman's powers drawn in golden light (VfxDraw, additive): the Lasso of Truth (the glowing rope from her hand,
 * its loops round whoever it binds, the whirl), her bracelets (the guard's glow, the sparks of what they turn away,
 * the shockwave of the clash), her thrown shield (the model itself, spinning, with its trail) and the Amazonian
 * Cataclysm (rings, walls and a pillar of gold).
 */
@Mod.EventBusSubscriber(modid = FlashMod.MODID, value = Dist.CLIENT)
public final class WonderVfx {

    private static final float[] GOLD = {1.0F, 0.8F, 0.32F};
    private static final float[] WHITE = {1.0F, 0.95F, 0.8F};
    private static final float[] RED = {1.0F, 0.3F, 0.22F};

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

    /** A shield on its way: where it was, where it is, when it was last heard of. */
    private static final class Flying {
        Vec3 prev;
        Vec3 cur;
        float seen;
    }

    private static final List<Fx> ACTIVE = new ArrayList<>();
    private static final Map<Integer, Flying> SHIELDS = new HashMap<>();
    private static float clock;

    private WonderVfx() {
    }

    public static void reset() {
        ACTIVE.clear();
        SHIELDS.clear();
    }

    /** Her shield is out of her hands (not drawn on her back). */
    static boolean shieldOut(int caster) {
        Flying f = SHIELDS.get(caster);
        return f != null && clock - f.seen <= 3.0F;
    }

    public static void fx(HeroFxPacket p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        if (p.type() == WonderAbilities.FX_SHIELD) {
            Flying f = SHIELDS.get(p.caster());
            if (f == null || clock - f.seen > 3.0F) {
                if (mc.level.getEntity(p.caster()) instanceof Player cp) {
                    WonderPose.cast(cp, p.type());
                }
                f = new Flying();
                f.cur = p.from();
                SHIELDS.put(p.caster(), f);
            }
            f.prev = f.cur;
            f.cur = p.to();
            f.seen = clock;
            return;
        }
        if (p.type() == WonderAbilities.FX_SHIELD_BACK) {
            SHIELDS.remove(p.caster());
        }
        int life = switch (p.type()) {
            case WonderAbilities.FX_LASSO -> p.target() >= 0 ? Math.max(10, (int) p.power()) : 9;
            case WonderAbilities.FX_SPIN -> 14;
            case WonderAbilities.FX_GUARD -> 10;
            case WonderAbilities.FX_DEFLECT -> 8;
            case WonderAbilities.FX_CLASH -> 16;
            case WonderAbilities.FX_SHIELD_HIT -> 8;
            case WonderAbilities.FX_SHIELD_BACK -> 6;
            case WonderAbilities.FX_LEAP -> 14;
            case WonderAbilities.FX_CATACLYSM -> 30;
            case WonderAbilities.FX_TAKEOFF -> 12;
            case WonderAbilities.FX_CALL -> 60;
            default -> 0;
        };
        if (mc.level.getEntity(p.caster()) instanceof Player cp) {
            WonderPose.cast(cp, p.type());
        }
        if (life > 0 && ACTIVE.size() < 400) {
            ACTIVE.add(new Fx(p, life));
        }
        Vec3 a = p.from();
        switch (p.type()) {
            case WonderAbilities.FX_DEFLECT, WonderAbilities.FX_SHIELD_HIT -> {
                for (int i = 0; i < 10; i++) {
                    mc.level.addParticle(ParticleTypes.ELECTRIC_SPARK, a.x, a.y, a.z, rnd(0.5D), rnd(0.5D),
                            rnd(0.5D));
                }
                mc.level.addParticle(ParticleTypes.CRIT, a.x, a.y, a.z, rnd(0.4D), rnd(0.4D), rnd(0.4D));
            }
            case WonderAbilities.FX_CATACLYSM -> {
                for (int i = 0; i < 80; i++) {
                    double ang = i / 80.0D * Math.PI * 2.0D;
                    double s = 0.6D + Math.random() * 0.8D;
                    mc.level.addParticle(ParticleTypes.CLOUD, a.x, a.y + 0.2D, a.z, Math.cos(ang) * s, 0.05D,
                            Math.sin(ang) * s);
                }
                for (int i = 0; i < 40; i++) {
                    mc.level.addParticle(ParticleTypes.ELECTRIC_SPARK, a.x + rnd(6.0D), a.y + Math.random() * 3.0D,
                            a.z + rnd(6.0D), 0.0D, 0.1D, 0.0D);
                }
            }
            case WonderAbilities.FX_CALL -> {
                for (int i = 0; i < 60; i++) {
                    double ang = i / 60.0D * Math.PI * 2.0D;
                    mc.level.addParticle(ParticleTypes.END_ROD, a.x + Math.cos(ang) * 1.4D, a.y + 3.0D,
                            a.z + Math.sin(ang) * 1.4D, -Math.cos(ang) * 0.08D, -0.15D, -Math.sin(ang) * 0.08D);
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
        SHIELDS.values().removeIf(f -> clock - f.seen > 10.0F);
        // the shield's golden trail
        for (Flying f : SHIELDS.values()) {
            if (clock - f.seen <= 1.0F && Math.random() < 0.6D) {
                mc.level.addParticle(ParticleTypes.END_ROD, f.cur.x, f.cur.y, f.cur.z, 0.0D, 0.0D, 0.0D);
            }
        }
    }

    private static boolean any(Minecraft mc) {
        if (!SHIELDS.isEmpty()) {
            return true;
        }
        for (int id : WonderClient.ids()) {
            Entity e = mc.level.getEntity(id);
            if (e instanceof Player p && WonderClient.guarding(p)) {
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
        // the shields in flight (the model itself)
        if (!SHIELDS.isEmpty()) {
            MultiBufferSource.BufferSource src = mc.renderBuffers().bufferSource();
            for (Flying f : SHIELDS.values()) {
                if (clock - f.seen > 3.0F) {
                    continue;
                }
                Vec3 at = f.prev.lerp(f.cur, Mth.clamp(pt, 0.0F, 1.0F));
                Vec3 v = f.cur.subtract(f.prev);
                ps.pushPose();
                ps.translate(at.x - cam.x, at.y - cam.y, at.z - cam.z);
                float yaw = (float) Mth.atan2(v.x, v.z);
                ps.mulPose(Axis.YP.rotation(yaw));
                // nearly flat, spinning like a disc thrown
                ps.mulPose(Axis.XP.rotationDegrees(-12.0F));
                ps.mulPose(Axis.YP.rotationDegrees(t * 45.0F));
                // the model's space has y down
                ps.scale(1.0F, -1.0F, 1.0F);
                int light = LevelRenderer.getLightColor(mc.level, BlockPos.containing(at));
                WonderLayer.drawShield(ps, src, light, 0.9F, false);
                ps.popPose();
            }
            src.endBatch();
        }
        ps.pushPose();
        ps.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f m = ps.last().pose();
        BufferBuilder buf = VfxDraw.begin();
        try {
            for (Fx f : ACTIVE) {
                draw(buf, m, mc, f, pt, t, cam, left, up);
            }
            for (Flying f : SHIELDS.values()) {
                if (clock - f.seen <= 3.0F) {
                    Vec3 at = f.prev.lerp(f.cur, Mth.clamp(pt, 0.0F, 1.0F));
                    Vec3 back = at.subtract(f.cur.subtract(f.prev).scale(2.5D));
                    VfxDraw.energy(buf, m, back, at, 0.18F, GOLD[0], GOLD[1], GOLD[2], 0.5F);
                    VfxDraw.glow(buf, m, at, left, up, 0.7D, GOLD[0], GOLD[1], GOLD[2], 0.45F);
                }
            }
            for (Player p : mc.level.players()) {
                if (!WonderClient.isWonder(p) || !WonderClient.guarding(p) || p.isInvisible()) {
                    continue;
                }
                // the bracelets crossed before her, shining
                Vec3 look = p.getViewVector(pt).multiply(1.0D, 0.0D, 1.0D);
                look = look.lengthSqr() < 1.0E-4D ? new Vec3(0.0D, 0.0D, 1.0D) : look.normalize();
                Vec3 c = p.getPosition(pt).add(0.0D, 1.3D, 0.0D).add(look.scale(0.55D));
                Vec3 side = new Vec3(-look.z, 0.0D, look.x);
                float pulse = 0.6F + 0.25F * Mth.sin(t * 0.5F);
                VfxDraw.glow(buf, m, c, side, new Vec3(0.0D, 1.0D, 0.0D), 0.55D, GOLD[0], GOLD[1], GOLD[2], pulse);
                VfxDraw.disc(buf, m, c.add(look.scale(0.15D)), side, new Vec3(0.0D, 1.0D, 0.0D), 0.9D, GOLD[0],
                        GOLD[1], GOLD[2], WHITE[0], WHITE[1], WHITE[2], 0.18F * pulse);
            }
        } finally {
            VfxDraw.end(buf);
            ps.popPose();
        }
    }

    /** Her right hand: the camera's in first person, else in front of her shoulder. */
    private static Vec3 handOf(Minecraft mc, int caster, Vec3 fallback, float pt) {
        Entity e = mc.level.getEntity(caster);
        if (e instanceof Player p) {
            boolean me = p == mc.getCameraEntity() && mc.options.getCameraType().isFirstPerson();
            if (me) {
                Camera cam = mc.gameRenderer.getMainCamera();
                return cam.getPosition().add(new Vec3(cam.getLookVector()).scale(0.7D))
                        .add(new Vec3(cam.getLeftVector()).scale(-0.35D)).add(new Vec3(cam.getUpVector()).scale(-0.3D));
            }
            Vec3 look = p.getViewVector(pt);
            Vec3 side = new Vec3(-look.z, 0.0D, look.x).normalize().scale(0.36D);
            return p.getEyePosition(pt).add(look.scale(0.5D)).add(side).add(0.0D, -0.4D, 0.0D);
        }
        return fallback;
    }

    /** A rope with a little sag between two points. */
    private static Vec3[] rope(Vec3 a, Vec3 b, double sag, double wave, float t) {
        int n = 14;
        Vec3[] pts = new Vec3[n + 1];
        for (int i = 0; i <= n; i++) {
            double k = i / (double) n;
            double s = Math.sin(k * Math.PI);
            pts[i] = a.lerp(b, k).add(0.0D, -sag * s + Math.sin(k * 9.0D + t * 0.8D) * wave * s, 0.0D);
        }
        return pts;
    }

    private static void loops(BufferBuilder buf, Matrix4f m, Vec3 base, double radius, double height, float t,
                              float alpha) {
        for (int i = 0; i < 3; i++) {
            Vec3 c = base.add(0.0D, height * (0.3D + i * 0.22D), 0.0D);
            VfxDraw.ring(buf, m, c, new Vec3(0.0D, 1.0D, 0.0D), radius, 0.05D, t * 0.1F + i, GOLD[0], GOLD[1],
                    GOLD[2], alpha);
        }
    }

    private static void draw(BufferBuilder buf, Matrix4f m, Minecraft mc, Fx f, float pt, float t, Vec3 cam,
                             Vec3 left, Vec3 up) {
        float age = f.age + pt;
        float k = Math.min(1.0F, age / f.life);
        float fade = 1.0F - k;
        switch (f.type) {
            case WonderAbilities.FX_LASSO -> {
                Vec3 hand = handOf(mc, f.caster, f.a, pt);
                Entity target = f.target >= 0 ? mc.level.getEntity(f.target) : null;
                if (target != null && target.isAlive()) {
                    // bound: the rope held taut, the loops round it, glowing
                    float end = Math.min(1.0F, (f.life - age) / 6.0F);
                    float in = Math.min(1.0F, age / 3.0F);
                    Vec3 tp = target.getPosition(pt);
                    Vec3 mid = tp.add(0.0D, target.getBbHeight() * 0.55D, 0.0D);
                    Vec3 to = hand.lerp(mid, in);
                    float a = end * (0.75F + 0.2F * Mth.sin(t * 0.6F));
                    Vec3[] r = rope(hand, to, 0.15D, 0.02D, t);
                    VfxDraw.path(buf, m, r, 0.06F, GOLD[0], GOLD[1], GOLD[2], a);
                    VfxDraw.path(buf, m, r, 0.02F, WHITE[0], WHITE[1], WHITE[2], a);
                    if (in >= 1.0F) {
                        loops(buf, m, tp, target.getBbWidth() * 0.75D + 0.1D, target.getBbHeight(), t, a);
                        VfxDraw.glow(buf, m, mid, left, up, target.getBbHeight() * 0.7D, GOLD[0], GOLD[1], GOLD[2],
                                0.25F * a);
                    }
                } else {
                    // a throw that caught nothing: out and back
                    float out = age < 4.0F ? age / 4.0F : Math.max(0.0F, 1.0F - (age - 4.0F) / 5.0F);
                    Vec3 to = hand.lerp(f.b, out);
                    VfxDraw.path(buf, m, rope(hand, to, 0.4D, 0.08D, t), 0.05F, GOLD[0], GOLD[1], GOLD[2], 0.85F);
                    VfxDraw.ring(buf, m, to, f.b.subtract(hand).normalize(), 0.35D, 0.05D, t * 0.3F, GOLD[0], GOLD[1],
                            GOLD[2], 0.8F);
                }
            }
            case WonderAbilities.FX_SPIN -> {
                // the lasso whirled round her
                Entity e = mc.level.getEntity(f.caster);
                Vec3 c = e != null ? e.getPosition(pt).add(0.0D, 1.1D, 0.0D) : f.a.add(0.0D, 1.1D, 0.0D);
                double r = 1.0D + f.power * Math.min(1.0F, age / 4.0F);
                int n = 24;
                Vec3[] ring = new Vec3[n + 1];
                double rot = t * 0.9D;
                for (int i = 0; i <= n; i++) {
                    double ang = rot + i / (double) n * Math.PI * 1.6D;
                    double rr = r * (0.25D + 0.75D * i / n);
                    ring[i] = c.add(Math.cos(ang) * rr, Math.sin(ang * 3.0D) * 0.15D, Math.sin(ang) * rr);
                }
                VfxDraw.path(buf, m, ring, 0.07F, GOLD[0], GOLD[1], GOLD[2], fade);
                VfxDraw.ring(buf, m, c, new Vec3(0.0D, 1.0D, 0.0D), r, 0.25D, t * 0.2F, GOLD[0], GOLD[1], GOLD[2],
                        0.5F * fade);
                VfxDraw.wall(buf, m, c.add(0.0D, -1.0D, 0.0D), r, 1.5D * fade, GOLD[0], GOLD[1], GOLD[2], 0.3F * fade);
            }
            case WonderAbilities.FX_GUARD -> {
                Entity e = mc.level.getEntity(f.caster);
                Vec3 c = e != null ? e.getPosition(pt).add(0.0D, 1.1D, 0.0D) : f.a.add(0.0D, 1.1D, 0.0D);
                VfxDraw.burst(buf, m, c, left, up, 0.8F + 1.2F * k, GOLD[0], GOLD[1], GOLD[2], fade, t * 0.3F);
                VfxDraw.ring(buf, m, c, new Vec3(0.0D, 1.0D, 0.0D), 0.4D + 1.4D * k, 0.1D, 0.0F, GOLD[0], GOLD[1],
                        GOLD[2], fade);
            }
            case WonderAbilities.FX_DEFLECT -> {
                VfxDraw.burst(buf, m, f.a, left, up, 0.4F + 0.6F * k, WHITE[0], WHITE[1], WHITE[2], fade, t * 0.4F);
                VfxDraw.glow(buf, m, f.a, left, up, 0.5D, GOLD[0], GOLD[1], GOLD[2], fade);
            }
            case WonderAbilities.FX_CLASH -> {
                // the shockwave of the bracelets: rings racing ahead in a cone
                Vec3 a = handOf(mc, f.caster, f.a, pt);
                Vec3 dir = f.b.lengthSqr() < 1.0E-4D ? new Vec3(0.0D, 0.0D, 1.0D) : f.b.normalize();
                if (age < 2.5F) {
                    VfxDraw.glow(buf, m, a, left, up, 1.2D, WHITE[0], WHITE[1], WHITE[2], 1.0F);
                    VfxDraw.burst(buf, m, a, left, up, 1.6F, GOLD[0], GOLD[1], GOLD[2], 1.0F, t * 0.3F);
                }
                for (int i = 0; i < 4; i++) {
                    float kk = Mth.clamp(k * 1.4F - i * 0.12F, 0.0F, 1.0F);
                    if (kk <= 0.0F || kk >= 1.0F) {
                        continue;
                    }
                    double d = f.power * kk;
                    VfxDraw.ring(buf, m, a.add(dir.scale(d)), dir, 0.4D + d * 0.55D, 0.25D + d * 0.03D, t * 0.2F,
                            GOLD[0], GOLD[1], GOLD[2], (1.0F - kk) * 0.9F);
                }
            }
            case WonderAbilities.FX_SHIELD_HIT -> {
                VfxDraw.burst(buf, m, f.a, left, up, 0.9F + k, GOLD[0], GOLD[1], GOLD[2], fade, t * 0.3F);
                VfxDraw.glow(buf, m, f.a, left, up, 0.9D, WHITE[0], WHITE[1], WHITE[2], fade);
            }
            case WonderAbilities.FX_SHIELD_BACK -> VfxDraw.glow(buf, m, f.a, left, up, 0.6D * fade, GOLD[0], GOLD[1],
                    GOLD[2], fade);
            case WonderAbilities.FX_LEAP, WonderAbilities.FX_TAKEOFF -> {
                VfxDraw.ring(buf, m, f.a.add(0.0D, 0.05D, 0.0D), new Vec3(0.0D, 1.0D, 0.0D), 0.5D + 3.5D * k, 0.3D,
                        0.0F, GOLD[0], GOLD[1], GOLD[2], fade);
                VfxDraw.glow(buf, m, f.a.add(0.0D, 0.3D, 0.0D), left, up, 1.5D * fade, GOLD[0], GOLD[1], GOLD[2],
                        fade);
            }
            case WonderAbilities.FX_CATACLYSM -> {
                double r = f.power;
                Vec3 g = f.a.add(0.0D, 0.08D, 0.0D);
                VfxDraw.ring(buf, m, g, new Vec3(0.0D, 1.0D, 0.0D), 0.5D + r * k, 0.6D, t * 0.1F, GOLD[0], GOLD[1],
                        GOLD[2], fade);
                VfxDraw.ring(buf, m, g, new Vec3(0.0D, 1.0D, 0.0D), 0.3D + r * 0.7D * k, 0.3D, -t * 0.1F, RED[0],
                        RED[1], RED[2], 0.7F * fade);
                VfxDraw.wall(buf, m, f.a, 0.5D + r * k, 3.0D * fade, GOLD[0], GOLD[1], GOLD[2], 0.5F * fade);
                VfxDraw.pillar(buf, m, f.a, f.a.y, f.a.y + 14.0D, cam, 1.6F * fade, GOLD[0], GOLD[1], GOLD[2],
                        0.7F * fade);
                VfxDraw.pillar(buf, m, f.a, f.a.y, f.a.y + 18.0D, cam, 0.4F * fade, WHITE[0], WHITE[1], WHITE[2],
                        0.9F * fade);
                if (age < 6.0F) {
                    VfxDraw.glow(buf, m, f.a.add(0.0D, 1.0D, 0.0D), left, up, 4.0D * (1.0F - age / 6.0F), WHITE[0],
                            WHITE[1], WHITE[2], 1.0F);
                }
                for (int i = 0; i < 6; i++) {
                    double ang = i / 6.0D * Math.PI * 2.0D + f.seed % 7;
                    Vec3 end = f.a.add(Math.cos(ang) * r * k, 0.2D, Math.sin(ang) * r * k);
                    VfxDraw.path(buf, m, VfxDraw.jagged(f.a.add(0.0D, 0.2D, 0.0D), end, f.seed + i * 131L
                            + (long) (age * 0.5F), 0.6D, 4), 0.07F, GOLD[0], GOLD[1], GOLD[2], fade);
                }
            }
            case WonderAbilities.FX_CALL -> {
                Entity e = mc.level.getEntity(f.caster);
                Vec3 c = e != null ? e.getPosition(pt) : f.a;
                float in = Math.min(1.0F, age / 8.0F);
                VfxDraw.pillar(buf, m, c, c.y, c.y + 40.0D, cam, 1.2F * in * fade, GOLD[0], GOLD[1], GOLD[2],
                        0.6F * fade);
                VfxDraw.ring(buf, m, c.add(0.0D, 0.05D, 0.0D), new Vec3(0.0D, 1.0D, 0.0D), 1.8D, 0.2D, t * 0.05F,
                        GOLD[0], GOLD[1], GOLD[2], in * fade);
                VfxDraw.wall(buf, m, c, 1.8D, 2.6D * in, GOLD[0], GOLD[1], GOLD[2], 0.4F * fade);
            }
            default -> {
            }
        }
    }
}
