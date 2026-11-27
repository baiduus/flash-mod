package com.flash.client;

import com.flash.FlashMod;
import com.flash.network.ScarletFxPacket;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.DustParticleOptions;
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
import org.joml.Vector3f;

/**
 * Every visual of the Scarlet Witch, built of light (VfxDraw: the Green Lanterns' soft beams, discs, rays and
 * fresnel spheres, God Wand's jagged energy and screen shockwave, Hand of God's red energy, rune circles) instead
 * of the old clouds of dust. Her powers are the same; only how they look changed:
 * <ul>
 * <li>chaos bolt: a jagged bolt shooting out with two hex threads wound round it, a flash at her hand and on
 * the hit;</li>
 * <li>chaos beam: a living torrent (its jagged core changing every tick, the threads turning, rings running down
 * it), flaring where it hits;</li>
 * <li>chaos blast: a sphere of chaos swelling where it lands, a ring running over the ground, rays, a hex circle
 * under it and God Wand's shockwave over the screen;</li>
 * <li>mind wave: rings of force and a low wall of light running out from her, a hexagram under her feet;</li>
 * <li>healing: a ribbon of light to him and motes winding up his body;</li>
 * <li>necromancy: a hex circle burning on the ground and a dark pillar rising with the dead;</li>
 * <li>control: a thin thread of thought with beads running down it to the victim's eyes;</li>
 * <li>her servants: a hex halo over the head; her puppets: the halo and threads going up from it;</li>
 * <li>torn apart: a field turning over the pieces; whole again: it collapses and bursts;</li>
 * <li>taking off: a ring of force on the ground and a column of light; her flight: a ribbon of scarlet light
 * trailing behind (the Lanterns' flight trail) and her hands glowing with rays while she flies or casts;</li>
 * <li>the Chaos energy that offers the power: a sphere of chaos with two rings turning round it; accepted, a
 * pillar and a burst; refused, it collapses; Chaos saving her from death, a burst of scarlet.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = FlashMod.MODID, value = Dist.CLIENT)
public final class ScarletVfx {

    // scarlet, its pink-white heart and a dark crimson
    private static final float R = 1.0F;
    private static final float G = 0.07F;
    private static final float B = 0.2F;
    private static final float DR = 0.55F;
    private static final float DG = 0.0F;
    private static final float DB = 0.12F;

    private static final DustParticleOptions SPARK = new DustParticleOptions(new Vector3f(1.0F, 0.3F, 0.4F), 0.8F);
    private static final List<Fx> ACTIVE = new ArrayList<>();
    private static final Int2ObjectMap<ArrayDeque<Vec3>> TRAILS = new Int2ObjectOpenHashMap<>();
    private static final Random RND = new Random();
    private static int clock;

    private ScarletVfx() {
    }

    private static final class Fx {
        final int type;
        final int caster;
        Vec3 a;
        Vec3 b;
        final float power;
        final int life;
        final long seed;
        int age;

        Fx(int type, int caster, Vec3 a, Vec3 b, float power, int life) {
            this.type = type;
            this.caster = caster;
            this.a = a;
            this.b = b;
            this.power = power;
            this.life = life;
            this.seed = RND.nextLong();
        }
    }

    private static int life(int type) {
        return switch (type) {
            case ScarletFxPacket.BOLT -> 12;
            case ScarletFxPacket.BEAM -> 4;
            case ScarletFxPacket.BLAST -> 28;
            case ScarletFxPacket.WAVE -> 30;
            case ScarletFxPacket.HEAL -> 26;
            case ScarletFxPacket.RAISE -> 40;
            case ScarletFxPacket.LINK -> 16;
            case ScarletFxPacket.TAKEOFF -> 22;
            case ScarletFxPacket.GRANT -> 50;
            case ScarletFxPacket.SAVED -> 36;
            case ScarletFxPacket.THRALL -> 14;
            case ScarletFxPacket.PUPPET -> 7;
            case ScarletFxPacket.TORN -> 6;
            case ScarletFxPacket.WHOLE -> 22;
            case ScarletFxPacket.REFUSE -> 18;
            default -> 0;
        };
    }

    /** An effect from the server. */
    public static void spawn(ScarletFxPacket p) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        int life = life(p.type());
        if (level == null || life <= 0) {
            return;
        }
        if (p.type() == ScarletFxPacket.BEAM || p.type() == ScarletFxPacket.THRALL
                || p.type() == ScarletFxPacket.PUPPET || p.type() == ScarletFxPacket.TORN) {
            // kept alive by the next packet instead of piling up
            for (Fx f : ACTIVE) {
                if (f.type == p.type() && f.caster == p.caster()) {
                    f.a = p.from();
                    f.b = p.to();
                    f.age = Math.min(f.age, 1);
                    return;
                }
            }
        }
        if (ACTIVE.size() > 160) {
            ACTIVE.remove(0);
        }
        ACTIVE.add(new Fx(p.type(), p.caster(), p.from(), p.to(), p.power(), life));
        Vec3 b = p.to();
        switch (p.type()) {
            case ScarletFxPacket.BOLT -> sparks(level, b, 8, 0.25D);
            case ScarletFxPacket.BLAST -> {
                sparks(level, b, 24, 0.5D);
                level.addParticle(ParticleTypes.FLASH, b.x, b.y, b.z, 0.0D, 0.0D, 0.0D);
                Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
                if (cam.distanceToSqr(b) < 64.0D * 64.0D) {
                    com.flash.client.godwand.GodWandPostFxManager.triggerImpact(b.x, b.y, b.z, 2, 14,
                            Math.min(2.0F, 0.6F + p.power() * 0.15F));
                }
            }
            case ScarletFxPacket.HEAL -> {
                for (int i = 0; i < 6; i++) {
                    level.addParticle(ParticleTypes.HEART, b.x + (RND.nextDouble() - 0.5D), b.y + 0.4D + i * 0.3D,
                            b.z + (RND.nextDouble() - 0.5D), 0.0D, 0.05D, 0.0D);
                }
            }
            case ScarletFxPacket.RAISE -> {
                for (int i = 0; i < 20; i++) {
                    level.addParticle(ParticleTypes.SOUL, b.x + (RND.nextDouble() - 0.5D) * 1.6D, b.y + 0.1D,
                            b.z + (RND.nextDouble() - 0.5D) * 1.6D, 0.0D, 0.06D + RND.nextDouble() * 0.08D, 0.0D);
                }
            }
            case ScarletFxPacket.WHOLE, ScarletFxPacket.SAVED, ScarletFxPacket.GRANT -> sparks(level, b, 30, 0.45D);
            default -> {
            }
        }
    }

    private static void sparks(ClientLevel level, Vec3 c, int n, double speed) {
        for (int i = 0; i < n; i++) {
            double u = RND.nextDouble() * 2.0D - 1.0D;
            double ang = RND.nextDouble() * Math.PI * 2.0D;
            double s = Math.sqrt(1.0D - u * u);
            double v = speed * (0.4D + RND.nextDouble() * 0.6D);
            level.addParticle(SPARK, c.x, c.y, c.z, s * Math.cos(ang) * v, u * v, s * Math.sin(ang) * v);
        }
    }

    // ------------------------------------------------------------------
    // Ticks: ages, flight trails
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            ACTIVE.clear();
            TRAILS.clear();
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
        // her flight trail: the last positions of her chest
        for (Player p : mc.level.players()) {
            boolean flying = ScarletClient.flying(p) && !p.isInvisible();
            ArrayDeque<Vec3> trail = TRAILS.get(p.getId());
            if (flying) {
                if (trail == null) {
                    trail = new ArrayDeque<>();
                    TRAILS.put(p.getId(), trail);
                }
                trail.addFirst(p.position().add(0.0D, 0.9D, 0.0D));
                while (trail.size() > 18) {
                    trail.removeLast();
                }
            } else if (trail != null) {
                trail.pollLast();
                trail.pollLast();
                if (trail.isEmpty()) {
                    TRAILS.remove(p.getId());
                }
            }
        }
        if (!TRAILS.isEmpty()) {
            TRAILS.keySet().removeIf(id -> mc.level.getEntity(id) == null);
        }
    }

    // ------------------------------------------------------------------
    // Drawing
    // ------------------------------------------------------------------

    private static boolean anything(Minecraft mc) {
        if (!ACTIVE.isEmpty() || !TRAILS.isEmpty()) {
            return true;
        }
        for (Player p : mc.level.players()) {
            if (ScarletClient.aura(p)) {
                return true;
            }
        }
        return !ClientEntityIndex.CHAOS.isEmpty();
    }

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !anything(mc)) {
            return;
        }
        Camera camera = event.getCamera();
        Vec3 cam = camera.getPosition();
        float pt = event.getPartialTick();
        float t = clock + pt;
        Vector3f le = camera.getLeftVector();
        Vector3f up = camera.getUpVector();
        Vec3 left = new Vec3(le.x(), le.y(), le.z());
        Vec3 upv = new Vec3(up.x(), up.y(), up.z());
        PoseStack ps = event.getPoseStack();
        ps.pushPose();
        ps.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f m = ps.last().pose();
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(net.minecraft.client.renderer.GameRenderer::getPositionColorShader);
        BufferBuilder buf = Tesselator.getInstance().getBuilder();
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        try {
            for (Fx f : ACTIVE) {
                draw(buf, m, f, pt, t, cam, left, upv, mc);
            }
            trails(buf, m, mc, pt, t, cam, left, upv);
            hands(buf, m, mc, pt, t, left, upv);
            chaos(buf, m, mc, pt, t, cam, left, upv);
            BufferUploader.drawWithShader(buf.end());
        } finally {
            ps.popPose();
            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableBlend();
        }
    }

    private static Vec3 headOf(Minecraft mc, int id, Vec3 fallback, float pt) {
        Entity e = mc.level.getEntity(id);
        return e != null ? e.getPosition(pt).add(0.0D, e.getBbHeight() + 0.35D, 0.0D) : fallback;
    }

    private static float ease(float k) {
        float u = 1.0F - Mth.clamp(k, 0.0F, 1.0F);
        return 1.0F - u * u * u;
    }

    /**
     * A wisp of chaos magic flowing from a to b: a living curve that writhes with time (seeded), thick in the
     * middle, fading at both ends, crimson round a pink-white heart.
     */
    private static void wisp(BufferBuilder buf, Matrix4f m, Vec3 a, Vec3 b, long seed, float t, double amp, float w,
                             float al) {
        if (al <= 0.01F) {
            return;
        }
        Vec3 d = b.subtract(a);
        double len = d.length();
        if (len < 1.0E-3D) {
            return;
        }
        Vec3 dn = d.scale(1.0D / len);
        Vec3 ref = Math.abs(dn.y) > 0.95D ? new Vec3(1.0D, 0.0D, 0.0D) : new Vec3(0.0D, 1.0D, 0.0D);
        Vec3 u = dn.cross(ref).normalize();
        Vec3 v = dn.cross(u).normalize();
        Random rnd = new Random(seed);
        double f1 = 1.5D + rnd.nextDouble() * 2.0D;
        double f2 = 1.0D + rnd.nextDouble() * 2.5D;
        double ph1 = rnd.nextDouble() * Math.PI * 2.0D;
        double ph2 = rnd.nextDouble() * Math.PI * 2.0D;
        double sp = 0.15D + rnd.nextDouble() * 0.15D;
        int n = Math.max(10, Math.min(40, (int) (len * 4.0D)));
        Vec3 prev = a;
        for (int i = 1; i <= n; i++) {
            double k = (double) i / n;
            double env = Math.sin(Math.PI * k);
            double ox = Math.sin(k * Math.PI * f1 + t * sp + ph1) * amp * env;
            double oy = Math.cos(k * Math.PI * f2 - t * sp * 1.3D + ph2) * amp * env;
            Vec3 p = a.add(d.scale(k)).add(u.scale(ox)).add(v.scale(oy));
            float e = (float) Math.sqrt(Math.max(0.0D, Math.sin(Math.PI * (k - 0.5D / n))));
            VfxDraw.beam(buf, m, prev, p, w * (0.35F + 0.65F * e), R, G, B, 0.45F * al * e);
            VfxDraw.beam(buf, m, prev, p, w * 0.3F * (0.35F + 0.65F * e), 1.0F, 0.6F, 0.7F, 0.8F * al * e);
            prev = p;
        }
    }

    /** A sigil of the Darkhold turning in the plane facing n: two rings and runes of light between them. */
    private static void sigil(BufferBuilder buf, Matrix4f m, Vec3 c, Vec3 n, double r, float t, float al) {
        VfxDraw.ring(buf, m, c, n, r, 0.04D, t * 0.06F, R, 0.25F, 0.35F, 0.8F * al);
        VfxDraw.ring(buf, m, c, n, r * 0.72D, 0.025D, -t * 0.09F, R, G, B, 0.7F * al);
        Vec3 nn = n.normalize();
        Vec3 ref = Math.abs(nn.y) > 0.95D ? new Vec3(1.0D, 0.0D, 0.0D) : new Vec3(0.0D, 1.0D, 0.0D);
        Vec3 u = nn.cross(ref).normalize();
        Vec3 v = nn.cross(u).normalize();
        for (int i = 0; i < 8; i++) {
            double ang = t * 0.06D + i * Math.PI / 4.0D;
            Vec3 q = c.add(u.scale(Math.cos(ang) * r * 0.86D)).add(v.scale(Math.sin(ang) * r * 0.86D));
            Vec3 o = u.scale(-Math.sin(ang) * r * 0.06D).add(v.scale(Math.cos(ang) * r * 0.06D));
            VfxDraw.beam(buf, m, q.subtract(o), q.add(o), 0.02F, 1.0F, 0.55F, 0.65F, 0.9F * al);
        }
    }

    private static void draw(BufferBuilder buf, Matrix4f m, Fx f, float pt, float t, Vec3 cam, Vec3 left, Vec3 up,
                             Minecraft mc) {
        float age = f.age + pt;
        float k = Mth.clamp(age / f.life, 0.0F, 1.0F);
        float fade = 1.0F - k;
        Vec3 a = f.a;
        Vec3 b = f.b;
        Vec3 yUp = new Vec3(0.0D, 1.0D, 0.0D);
        switch (f.type) {
            case ScarletFxPacket.BOLT -> {
                // a lash of chaos: wisps writhing round a thin crackling core, shooting out in 3 ticks
                float run = Mth.clamp(age / 3.0F, 0.0F, 1.0F);
                Vec3 tip = a.add(b.subtract(a).scale(run));
                float al = Math.min(1.0F, fade * 2.0F);
                for (int i = 0; i < 4; i++) {
                    wisp(buf, m, a, tip, f.seed + i * 31L, t * 2.0F, 0.28D, 0.12F, al);
                }
                Vec3[] core = VfxDraw.jagged(a, tip, f.seed + (long) (age * 0.5F), 0.8D, 4);
                VfxDraw.path(buf, m, core, 0.05F, 1.0F, 0.7F, 0.75F, al);
                VfxDraw.flare(buf, m, a, left, up, 0.35D, R, 0.2F, 0.3F, al);
                if (run >= 1.0F) {
                    float s = Mth.clamp(1.0F - (age - 3.0F) / 8.0F, 0.0F, 1.0F);
                    VfxDraw.flare(buf, m, b, left, up, 0.8D, R, 0.2F, 0.3F, s);
                    VfxDraw.sphere(buf, m, b, 0.3D + 1.1D * (1.0F - s), cam, R, G, B, 0.5F * s, t, 2.0D);
                    VfxDraw.sparks(buf, m, b, f.seed, 16, 1.4D, (age - 3.0F) / 9.0F, 0.6D, R, 0.35F, 0.45F, 1.0F);
                } else {
                    VfxDraw.glow(buf, m, tip, left, up, 0.5D, 1.0F, 0.6F, 0.7F, 0.9F);
                }
            }
            case ScarletFxPacket.BEAM -> {
                // a torrent of chaos: wisps pouring and twisting round a hot core, rings of force down it
                float al = Mth.clamp((f.life - age) / 2.0F, 0.0F, 1.0F);
                VfxDraw.beam(buf, m, a, b, 0.6F, R, G, B, 0.12F * al);
                for (int i = 0; i < 6; i++) {
                    wisp(buf, m, a, b, f.seed + i * 47L, t * 2.5F, 0.45D, 0.16F, al);
                }
                Vec3[] core = VfxDraw.jagged(a, b, f.seed + clock, 0.5D, 4);
                VfxDraw.path(buf, m, core, 0.1F, R, 0.3F, 0.4F, 0.8F * al);
                VfxDraw.path(buf, m, core, 0.03F, 1.0F, 0.85F, 0.9F, al);
                double len = a.distanceTo(b);
                if (len > 0.5D) {
                    Vec3 dn = b.subtract(a).scale(1.0D / len);
                    for (int r = 0; r < 4; r++) {
                        double pos = ((t * 0.9D + r * len / 4.0D) % len);
                        sigil(buf, m, a.add(dn.scale(pos)), dn, 0.5D, t, 0.4F * al);
                    }
                }
                VfxDraw.flare(buf, m, a, left, up, 0.45D, R, 0.2F, 0.3F, al);
                VfxDraw.flare(buf, m, b, left, up, 0.9D, R, 0.2F, 0.3F, al);
                for (int g = 0; g < 2; g++) {
                    long gen = (long) (t / 4.0F) - g;
                    VfxDraw.sparks(buf, m, b, f.seed + gen * 977L, 8, 1.4D, (t - gen * 4.0F) / 8.0F, 0.5D, R, 0.35F,
                            0.45F, 1.0F);
                }
            }
            case ScarletFxPacket.BLAST -> {
                // a burst of chaos: a swelling sphere, wisps flung out, a ring of force, a hex circle burning
                double radius = Math.max(1.0F, f.power);
                float grow = ease(k);
                float al = fade * fade;
                if (age < 4.0F) {
                    for (int i = 0; i < 3; i++) {
                        wisp(buf, m, a, b, f.seed + i, t * 2.0F, 0.3D, 0.12F, 1.0F - age / 4.0F);
                    }
                }
                VfxDraw.flare(buf, m, b, left, up, 1.2D + radius * 0.3D, R, 0.2F, 0.3F, Math.max(0.0F, 1.0F - age / 6.0F));
                VfxDraw.sphere(buf, m, b, radius * (0.35D + 0.8D * grow), cam, R, G, B, 0.75F * al, t, 2.2D);
                VfxDraw.sphere(buf, m, b, radius * (0.2D + 0.4D * grow), cam, 1.0F, 0.5F, 0.6F, 0.55F * al, t, 1.2D);
                VfxDraw.shockwave(buf, m, new Vec3(b.x, b.y - 0.4D, b.z), radius * (0.3D + 1.6D * grow), 1.0D * fade,
                        R, G, B, al);
                for (int i = 0; i < 8; i++) {
                    double ang = Math.PI * 2.0D * i / 8.0D;
                    Vec3 out = b.add(Math.cos(ang) * radius * 1.6D * grow, (i % 2) * 0.8D * grow,
                            Math.sin(ang) * radius * 1.6D * grow);
                    wisp(buf, m, b, out, f.seed + i * 13L, t * 2.0F, 0.4D, 0.14F, al);
                }
                VfxDraw.hexCircle(buf, m, new Vec3(b.x, b.y - 0.45D, b.z), radius * 0.9D, t, R, G, B, al);
            }
            case ScarletFxPacket.WAVE -> {
                // the mind wave: rings of force rolling out, wisps rising round her, a hexagram under her feet
                double max = Math.max(2.0F, f.power);
                for (int w = 0; w < 3; w++) {
                    float kk = Mth.clamp((age - w * 3.0F) / (f.life - 6.0F), 0.0F, 1.0F);
                    if (kk <= 0.0F || kk >= 1.0F) {
                        continue;
                    }
                    VfxDraw.shockwave(buf, m, a, max * ease(kk), 1.8D * (1.0F - kk), R, G, B, (1.0F - kk) * 0.85F);
                }
                VfxDraw.hexCircle(buf, m, a.add(0.0D, 0.05D, 0.0D), 2.0D, t, R, G, B, fade);
                for (int i = 0; i < 6; i++) {
                    double ang = t * 0.05D + i * Math.PI / 3.0D;
                    Vec3 p0 = a.add(Math.cos(ang) * 1.2D, 0.0D, Math.sin(ang) * 1.2D);
                    wisp(buf, m, p0, p0.add(Math.cos(ang) * 0.6D, 2.8D, Math.sin(ang) * 0.6D), f.seed + i, t, 0.3D,
                            0.12F, fade);
                }
                VfxDraw.flare(buf, m, a.add(0.0D, 1.0D, 0.0D), left, up, 1.2D, R, 0.2F, 0.3F,
                        Math.max(0.0F, 1.0F - age / 6.0F));
            }
            case ScarletFxPacket.HEAL -> {
                // healing: a ribbon of rose light to him, motes winding up his body
                Vec3 to = b.add(0.0D, Math.max(1.0D, f.power) * 0.6D, 0.0D);
                float al = Math.min(1.0F, fade * 2.0F);
                wisp(buf, m, a, to, f.seed, t, 0.25D, 0.1F, al);
                Vec3[] ribbon = VfxDraw.helix(a, to, 0.18D, 2.0D, t * 0.4D, 30);
                VfxDraw.path(buf, m, ribbon, 0.05F, 1.0F, 0.55F, 0.65F, 0.7F * al);
                double h = Math.max(1.0D, f.power);
                for (int j = 0; j < 14; j++) {
                    double ph = (age * 0.06D + j / 14.0D) % 1.0D;
                    double ang = t * 0.25D + j * Math.PI * 2.0D / 14.0D;
                    Vec3 q = b.add(Math.cos(ang) * 0.6D, ph * h * 1.1D, Math.sin(ang) * 0.6D);
                    VfxDraw.flare(buf, m, q, left, up, 0.08D, 1.0F, 0.6F, 0.7F, 0.9F * al * (float) (1.0D - ph));
                }
                VfxDraw.glow(buf, m, b.add(0.0D, h * 0.5D, 0.0D), left, up, h * 0.9D, R, 0.3F, 0.4F, 0.3F * al);
                sigil(buf, m, b.add(0.0D, 0.05D, 0.0D), yUp, 0.9D, t, 0.8F * al);
            }
            case ScarletFxPacket.RAISE -> {
                // necromancy: the circle burning, the dark rising in spirals of chaos
                double h = Math.max(1.5D, f.power + 0.5D);
                float rise = Mth.clamp(age / 10.0F, 0.0F, 1.0F);
                float al = Math.min(1.0F, fade * 1.6F);
                VfxDraw.hexCircle(buf, m, b, 1.8D, t, R, G, B, al);
                VfxDraw.pillar(buf, m, b, b.y, b.y + h * rise * 1.4D, cam, 1.1F, DR, DG, DB, 0.5F * al);
                for (int i = 0; i < 5; i++) {
                    double ang = t * 0.08D + i * Math.PI * 2.0D / 5.0D;
                    Vec3 p0 = b.add(Math.cos(ang) * 1.3D, 0.0D, Math.sin(ang) * 1.3D);
                    wisp(buf, m, p0, b.add(0.0D, h * rise * 1.4D, 0.0D), f.seed + i, t, 0.35D, 0.14F, al);
                }
                VfxDraw.sphere(buf, m, b.add(0.0D, h * 0.5D, 0.0D), 0.9D + 0.3D * k, cam, DR, DG, DB, 0.35F * al, t,
                        1.6D);
            }
            case ScarletFxPacket.LINK -> {
                // control: a thread of thought to the victim's eyes, beads running down it
                float al = Math.min(1.0F, fade * 2.0F);
                wisp(buf, m, a, b, f.seed, t, 0.12D, 0.05F, al);
                double len = a.distanceTo(b);
                if (len > 0.1D) {
                    Vec3 dn = b.subtract(a).scale(1.0D / len);
                    for (int j = 0; j < 4; j++) {
                        double pos = ((age * 0.9D + j * len / 4.0D) % len);
                        VfxDraw.glow(buf, m, a.add(dn.scale(pos)), left, up, 0.14D, 1.0F, 0.6F, 0.65F, 0.9F * al);
                    }
                }
                VfxDraw.flare(buf, m, b, left, up, 0.35D, R, 0.2F, 0.3F, al);
                sigil(buf, m, b, b.subtract(a), 0.35D, t, al);
            }
            case ScarletFxPacket.TAKEOFF -> {
                VfxDraw.shockwave(buf, m, a, 0.5D + 5.0D * ease(k), 1.2D * fade, R, G, B, fade);
                VfxDraw.pillar(buf, m, a, a.y, a.y + 6.0D * (0.4D + ease(k)), cam, 1.0F * fade, R, G, B, 0.4F * fade);
                for (int i = 0; i < 5; i++) {
                    double ang = i * Math.PI * 2.0D / 5.0D;
                    Vec3 p0 = a.add(Math.cos(ang) * 0.8D, 0.0D, Math.sin(ang) * 0.8D);
                    wisp(buf, m, p0, p0.add(0.0D, 3.0D + 3.0D * ease(k), 0.0D), f.seed + i, t, 0.3D, 0.12F, fade);
                }
                VfxDraw.flare(buf, m, a.add(0.0D, 0.6D, 0.0D), left, up, 1.0D, R, 0.2F, 0.3F,
                        Math.max(0.0F, 1.0F - age / 6.0F));
            }
            case ScarletFxPacket.GRANT, ScarletFxPacket.SAVED -> {
                // Chaos answering: a blinding scarlet burst, wisps flung everywhere (and the column, granted)
                boolean grant = f.type == ScarletFxPacket.GRANT;
                Vec3 c = b.add(0.0D, 1.0D, 0.0D);
                float grow = ease(k);
                float al = fade;
                if (grant) {
                    VfxDraw.pillar(buf, m, b, b.y, b.y + 60.0D, cam, 1.4F * fade, R, G, B, 0.5F * al);
                    VfxDraw.pillar(buf, m, b, b.y, b.y + 60.0D, cam, 0.4F * fade, 1.0F, 0.75F, 0.8F, 0.8F * al);
                    VfxDraw.hexCircle(buf, m, b, 2.6D, t, R, G, B, al);
                }
                VfxDraw.flare(buf, m, c, left, up, grant ? 3.0D : 2.0D, R, 0.2F, 0.3F, Math.max(0.0F, 1.0F - age / 10.0F));
                VfxDraw.sphere(buf, m, c, (grant ? 3.0D : 1.8D) * grow, cam, R, G, B, 0.7F * al * al, t, 2.0D);
                VfxDraw.shockwave(buf, m, b, (grant ? 7.0D : 4.0D) * grow, 1.4D * al, R, G, B, al);
                for (int i = 0; i < 10; i++) {
                    double ang = Math.PI * 2.0D * i / 10.0D;
                    Vec3 out = c.add(Math.cos(ang) * 4.0D * grow, (i % 3 - 1) * 1.5D * grow, Math.sin(ang) * 4.0D * grow);
                    wisp(buf, m, c, out, f.seed + i, t * 2.0F, 0.5D, 0.15F, al);
                }
            }
            case ScarletFxPacket.THRALL, ScarletFxPacket.PUPPET -> {
                Vec3 head = headOf(mc, f.caster, b, pt);
                float al = Mth.clamp((f.life - age) / 3.0F, 0.0F, 1.0F);
                sigil(buf, m, head, yUp, 0.38D, t, al);
                VfxDraw.glow(buf, m, head, left, up, 0.45D, R, G, B, 0.25F * al);
                if (f.type == ScarletFxPacket.PUPPET) {
                    // the strings of a puppet, going up out of sight
                    for (int s = 0; s < 4; s++) {
                        double ang = t * 0.05D + s * Math.PI / 2.0D;
                        Vec3 p0 = head.add(Math.cos(ang) * 0.3D, -0.1D, Math.sin(ang) * 0.3D);
                        Vec3 p1 = p0.add(Math.cos(ang) * 0.4D, 4.0D, Math.sin(ang) * 0.4D);
                        wisp(buf, m, p0, p1, f.caster * 7L + s, t, 0.08D, 0.04F, al);
                    }
                }
            }
            case ScarletFxPacket.TORN -> {
                float al = Mth.clamp((f.life - age) / 3.0F, 0.0F, 1.0F);
                VfxDraw.hexCircle(buf, m, a, 1.4D, t, R, G, B, al);
                VfxDraw.sphere(buf, m, a.add(0.0D, 0.8D, 0.0D), 1.1D + 0.06D * Mth.sin(t * 0.7F), cam, R, G, B,
                        0.3F * al, t, 2.5D);
                for (int i = 0; i < 4; i++) {
                    double ang = t * 0.1D + i * Math.PI / 2.0D;
                    Vec3 c = a.add(0.0D, 0.8D, 0.0D);
                    wisp(buf, m, c.add(Math.cos(ang), 0.0D, Math.sin(ang)), c.add(-Math.cos(ang), 0.5D, -Math.sin(ang)),
                            f.seed + i, t, 0.4D, 0.1F, al);
                }
            }
            case ScarletFxPacket.WHOLE -> {
                Vec3 c = a.add(0.0D, 0.9D, 0.0D);
                if (k < 0.35F) {
                    float kk = k / 0.35F;
                    VfxDraw.sphere(buf, m, c, 1.6D * (1.0D - kk) + 0.2D, cam, R, G, B, 0.6F, t, 2.0D);
                } else {
                    float kk = (k - 0.35F) / 0.65F;
                    VfxDraw.flare(buf, m, c, left, up, 1.5D, R, 0.2F, 0.3F, 1.0F - kk);
                    VfxDraw.shockwave(buf, m, a, 0.4D + 3.0D * kk, 0.8D * (1.0F - kk), R, G, B, 1.0F - kk);
                }
            }
            case ScarletFxPacket.REFUSE -> {
                VfxDraw.sphere(buf, m, b, 0.7D * (1.0F - k) + 0.05D, cam, DR, DG, DB, 0.6F * fade, t, 1.5D);
                VfxDraw.glow(buf, m, b, left, up, 1.0D * (1.0F - k), DR, DG, DB, 0.5F * fade);
            }
            default -> {
            }
        }
    }

    /** Her flight trail: two wisps of chaos writhing behind her, a faint crimson haze. */
    private static void trails(BufferBuilder buf, Matrix4f m, Minecraft mc, float pt, float t, Vec3 cam, Vec3 left,
                               Vec3 up) {
        for (Int2ObjectMap.Entry<ArrayDeque<Vec3>> en : TRAILS.int2ObjectEntrySet()) {
            Entity e = mc.level.getEntity(en.getIntKey());
            if (e == null || en.getValue().size() < 2) {
                continue;
            }
            boolean me = e == mc.getCameraEntity() && mc.options.getCameraType().isFirstPerson();
            List<Vec3> pts = new ArrayList<>(en.getValue().size() + 1);
            Vec3 head = e.getPosition(pt).add(0.0D, 0.9D, 0.0D);
            pts.add(head);
            boolean first = true;
            for (Vec3 q : en.getValue()) {
                if (first) {
                    first = false;
                    continue;
                }
                pts.add(q);
            }
            int n = pts.size();
            for (int i = 0; i + 1 < n; i++) {
                float fr = 1.0F - (float) i / n;
                Vec3 p0 = pts.get(i);
                Vec3 p1 = pts.get(i + 1);
                Vec3 mid = p0.add(p1).scale(0.5D);
                float vis = me ? Mth.clamp((float) (mid.distanceTo(cam) - 2.2D) / 4.0F, 0.0F, 1.0F) : 1.0F;
                if (vis <= 0.01F) {
                    continue;
                }
                VfxDraw.beam(buf, m, p0, p1, 0.5F + 0.8F * fr, R, G, B, 0.07F * fr * vis);
                for (int w = 0; w < 2; w++) {
                    double ph0 = t * 0.3D + i * 0.6D + w * Math.PI;
                    double ph1 = t * 0.3D + (i + 1) * 0.6D + w * Math.PI;
                    double amp = 0.15D + 0.35D * (1.0F - fr);
                    Vec3 q0 = p0.add(Math.cos(ph0) * amp, Math.sin(ph0 * 1.3D) * amp, Math.sin(ph0) * amp);
                    Vec3 q1 = p1.add(Math.cos(ph1) * amp, Math.sin(ph1 * 1.3D) * amp, Math.sin(ph1) * amp);
                    VfxDraw.beam(buf, m, q0, q1, 0.12F * fr + 0.02F, R, 0.15F, 0.28F, 0.45F * fr * vis);
                    VfxDraw.beam(buf, m, q0, q1, 0.035F * fr + 0.01F, 1.0F, 0.65F, 0.75F, 0.7F * fr * vis);
                }
            }
        }
    }

    /** Her hands while she flies or casts: chaos flickering up off them like flames, a sigil at each wrist. */
    private static void hands(BufferBuilder buf, Matrix4f m, Minecraft mc, float pt, float t, Vec3 left, Vec3 up) {
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        for (Player p : mc.level.players()) {
            if (!ScarletClient.aura(p) || p.isInvisible()
                    || p == mc.getCameraEntity() && mc.options.getCameraType().isFirstPerson()) {
                continue;
            }
            double yaw = Math.toRadians(Mth.rotLerp(pt, p.yBodyRotO, p.yBodyRot));
            Vec3 pos = p.getPosition(pt);
            double hy = pos.y + (ScarletClient.flying(p) ? 1.3D : 0.75D) * p.getScale();
            for (int side = -1; side <= 1; side += 2) {
                Vec3 h = new Vec3(pos.x - Math.cos(yaw) * 0.38D * side, hy, pos.z - Math.sin(yaw) * 0.38D * side);
                float pulse = 0.8F + 0.2F * Mth.sin(t * 0.4F + side);
                VfxDraw.glow(buf, m, h, left, up, 0.3D * pulse, R, G, B, 0.5F);
                VfxDraw.flare(buf, m, h, left, up, 0.08D, 1.0F, 0.6F, 0.7F, 0.9F);
                for (int i = 0; i < 3; i++) {
                    Vec3 base = h.add(Math.cos(i * 2.1D + t * 0.1D) * 0.08D, -0.05D, Math.sin(i * 2.1D + t * 0.1D) * 0.08D);
                    VfxDraw.flame(buf, m, base, 0.35D + 0.1D * Math.sin(t * 0.5D + i), 0.06D, cam, t + i * 11.0F + side * 5.0F,
                            R, G, B, 0.6F);
                }
                sigil(buf, m, h, new Vec3(-Math.sin(yaw), 0.0D, Math.cos(yaw)), 0.2D, t * 2.0F * side, 0.7F * pulse);
            }
        }
    }

    /** The Chaos energy that comes to offer her power: a sphere of chaos, wisps writhing round it, rings turning. */
    private static void chaos(BufferBuilder buf, Matrix4f m, Minecraft mc, float pt, float t, Vec3 cam, Vec3 left,
                              Vec3 up) {
        for (Entity e : ClientEntityIndex.CHAOS) {
            if (e.isRemoved()) {
                continue;
            }
            float age = e.tickCount + pt;
            float grow = Mth.clamp(age / 20.0F, 0.0F, 1.0F);
            float pulse = 0.85F + 0.15F * Mth.sin(age * 0.25F);
            Vec3 c = e.getPosition(pt).add(0.0D, 0.25D, 0.0D);
            double r = 0.45D * grow * pulse;
            VfxDraw.sphere(buf, m, c, r * 1.2D, cam, R, G, B, 0.7F * grow, t, 1.8D);
            VfxDraw.flare(buf, m, c, left, up, r * 1.5D, R, 0.2F, 0.3F, 0.8F * grow);
            Vec3 n1 = new Vec3(Math.sin(age * 0.05D), 0.6D, Math.cos(age * 0.05D));
            Vec3 n2 = new Vec3(Math.cos(age * 0.07D), 0.2D, -Math.sin(age * 0.07D));
            sigil(buf, m, c, n1, r * 2.2D, age, 0.7F * grow);
            VfxDraw.ring(buf, m, c, n2, r * 2.7D, 0.03D, -age * 0.08F, R, G, B, 0.55F * grow);
            for (int i = 0; i < 5; i++) {
                double ang = age * 0.06D + i * Math.PI * 2.0D / 5.0D;
                Vec3 out = c.add(Math.cos(ang) * r * 3.5D, Math.sin(ang * 1.7D) * r * 2.0D, Math.sin(ang) * r * 3.5D);
                wisp(buf, m, c, out, e.getId() * 13L + i, age, r * 0.8D, 0.06F, 0.8F * grow);
            }
        }
    }
}
