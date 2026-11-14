package com.flash.client;

import com.flash.FlashMod;
import com.flash.network.ThorFxPacket;
import com.flash.thor.ThorPowers;
import com.flash.thor.ThorState;
import com.flash.thor.ThorSuits;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Thor's lightning, drawn with the blue ribbon of the mod (purple for the Cosmic King): bolts sent by the server,
 * the continuous Lightning Blast, the arcs of the Thunder's Fury, and the effects that Legends: Superheroes makes
 * with Photon, remade here: the charge aura of the God of Thunder (rings and stars around Thor, the hammer
 * crackling), its impact (bolts along the ground, glowing cracks, energy smoke), the Storm Striker shockwave (a ring
 * of lightning running out), the Stormbreaker waves, the Bifrost (the rainbow bridge and its scorch mark), the dome
 * of the Devourer King and the beam of the God Blast.
 */
@Mod.EventBusSubscriber(modid = FlashMod.MODID, value = Dist.CLIENT)
public final class ThorFx {

    private record Bolt(List<Vec3> points, long start, int life, float scale, boolean cosmic) {
    }

    private record Fx(int kind, Vec3 a, Vec3 b, float scale, long start, int life, boolean cosmic, long seed) {
    }

    private static final int SCORCH = 100;
    private static final ResourceLocation SCORCH_TEXTURE = new ResourceLocation(FlashMod.MODID,
            "textures/thor/bifrost_scorch.png");

    public static final float[] PURPLE_GLOW = {0.45F, 0.08F, 1.0F, 0.24F};
    public static final float[] PURPLE_MID = {0.78F, 0.42F, 1.0F, 0.45F};
    public static final float[] PURPLE_CORE = {0.97F, 0.88F, 1.0F, 0.92F};

    private static final List<Bolt> BOLTS = new ArrayList<>();
    private static final List<Fx> EFFECTS = new ArrayList<>();
    private static final Random RANDOM = new Random();

    private ThorFx() {
    }

    public static void reset() {
        BOLTS.clear();
        EFFECTS.clear();
    }

    private static boolean cosmic(Player p) {
        return ThorState.weapon(p) == ThorSuits.COSMIC;
    }

    public static void handle(ThorFxPacket p) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            return;
        }
        boolean cosmic = (p.kind() & ThorFxPacket.COSMIC) != 0;
        int kind = p.kind() & 0xFF;
        Vec3 a = new Vec3(p.ax(), p.ay(), p.az());
        Vec3 b = new Vec3(p.bx(), p.by(), p.bz());
        long now = level.getGameTime();
        long seed = RANDOM.nextLong();
        switch (kind) {
            case ThorFxPacket.BOLT -> {
                add(a, b, p.scale(), 7, cosmic);
                level.addParticle(ParticleTypes.ELECTRIC_SPARK, b.x, b.y, b.z, 0.0D, 0.1D, 0.0D);
            }
            case ThorFxPacket.RING -> {
                EFFECTS.add(new Fx(kind, a, b, p.scale(), now, 14, cosmic, seed));
                for (int i = 0; i < 24; i++) {
                    double ang = i * Math.PI / 12.0D;
                    level.addParticle(ParticleTypes.ELECTRIC_SPARK, a.x + Math.cos(ang) * 0.8D, a.y + 0.2D,
                            a.z + Math.sin(ang) * 0.8D, Math.cos(ang) * p.scale() * 0.08D, 0.05D,
                            Math.sin(ang) * p.scale() * 0.08D);
                }
            }
            case ThorFxPacket.WAVE -> EFFECTS.add(new Fx(kind, a, b, p.scale(), now, 10, cosmic, seed));
            case ThorFxPacket.IMPACT -> {
                EFFECTS.add(new Fx(kind, a, b, p.scale(), now, 30, cosmic, seed));
                level.addParticle(ParticleTypes.FLASH, a.x, a.y + 0.5D, a.z, 0.0D, 0.0D, 0.0D);
                level.addParticle(ParticleTypes.EXPLOSION_EMITTER, a.x, a.y + 0.5D, a.z, 0.0D, 0.0D, 0.0D);
                for (int i = 0; i < 50; i++) {
                    double ang = RANDOM.nextDouble() * Math.PI * 2.0D;
                    double sp = 0.15D + RANDOM.nextDouble() * 0.35D;
                    level.addParticle(ParticleTypes.LARGE_SMOKE, a.x, a.y + 0.3D, a.z, Math.cos(ang) * sp, 0.04D,
                            Math.sin(ang) * sp);
                    level.addParticle(ParticleTypes.ELECTRIC_SPARK, a.x, a.y + 0.4D, a.z, Math.cos(ang) * sp * 2.0D,
                            0.2D + RANDOM.nextDouble() * 0.3D, Math.sin(ang) * sp * 2.0D);
                }
                for (int i = 0; i < 3; i++) {
                    Vec3 g = a.add(RANDOM.nextGaussian() * 2.0D, 0.2D, RANDOM.nextGaussian() * 2.0D);
                    add(g.add(0.0D, 30.0D, 0.0D), g, 2.0F, 10, cosmic);
                }
            }
            case ThorFxPacket.BIFROST -> {
                int life = Math.max(10, (int) p.bx());
                EFFECTS.add(new Fx(kind, a, b, p.scale(), now, life, cosmic, seed));
                EFFECTS.add(new Fx(SCORCH, a, b, p.scale(), now, 400, cosmic, seed));
                for (int i = 0; i < 40; i++) {
                    level.addParticle(ParticleTypes.END_ROD, a.x + RANDOM.nextGaussian() * p.scale() * 0.5D,
                            a.y + RANDOM.nextDouble() * 6.0D, a.z + RANDOM.nextGaussian() * p.scale() * 0.5D, 0.0D,
                            0.3D + RANDOM.nextDouble() * 0.4D, 0.0D);
                }
            }
            case ThorFxPacket.DOME -> EFFECTS.add(new Fx(kind, a, b, p.scale(), now, 22, true, seed));
            case ThorFxPacket.BEAM -> {
                EFFECTS.add(new Fx(kind, a, b, p.scale(), now, 4, cosmic, seed));
                for (int i = 0; i < 6; i++) {
                    level.addParticle(ParticleTypes.ELECTRIC_SPARK, b.x, b.y, b.z, RANDOM.nextGaussian() * 0.3D,
                            RANDOM.nextDouble() * 0.3D, RANDOM.nextGaussian() * 0.3D);
                }
            }
            default -> {
            }
        }
        if (EFFECTS.size() > 80) {
            EFFECTS.remove(0);
        }
    }

    private static void add(Vec3 a, Vec3 b, float scale, int life, boolean cosmic) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        BOLTS.add(new Bolt(jagged(a, b, 0.18D * Math.max(0.5F, scale), RANDOM), mc.level.getGameTime(), life, scale,
                cosmic));
        if (BOLTS.size() > 220) {
            BOLTS.remove(0);
        }
    }

    /** A zig-zag line from a to b. */
    private static List<Vec3> jagged(Vec3 a, Vec3 b, double amp, Random random) {
        List<Vec3> out = new ArrayList<>();
        Vec3 d = b.subtract(a);
        double len = d.length();
        int n = Math.max(3, (int) (len / 0.7D));
        Vec3 side = d.cross(new Vec3(0.0D, 1.0D, 0.0D));
        if (side.lengthSqr() < 1.0E-6D) {
            side = new Vec3(1.0D, 0.0D, 0.0D);
        }
        side = side.normalize();
        Vec3 up = side.cross(d).normalize();
        out.add(a);
        for (int i = 1; i < n; i++) {
            double t = i / (double) n;
            double w = amp * Math.min(1.0D, len / 3.0D) * (1.0D + 2.0D * Math.sin(t * Math.PI));
            out.add(a.add(d.scale(t)).add(side.scale((random.nextDouble() - 0.5D) * 2.0D * w))
                    .add(up.scale((random.nextDouble() - 0.5D) * 2.0D * w)));
        }
        out.add(b);
        return out;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.phase != TickEvent.Phase.END || mc.level == null || mc.isPaused()) {
            return;
        }
        ClientLevel level = mc.level;
        blastSounds(mc, level);
        for (Player p : level.players()) {
            if (!ThorState.isThor(p)) {
                continue;
            }
            boolean cosmic = cosmic(p);
            // Thunder's Fury: lightning all over the body
            if (ThorState.flag(p, ThorPowers.F_FURY)) {
                for (int i = 0; i < 2; i++) {
                    Vec3 c = p.position().add(0.0D, 0.3D + RANDOM.nextDouble() * 1.5D, 0.0D);
                    Vec3 o = c.add((RANDOM.nextDouble() - 0.5D) * 1.4D, (RANDOM.nextDouble() - 0.5D) * 1.0D,
                            (RANDOM.nextDouble() - 0.5D) * 1.4D);
                    add(c, o, 0.35F, 3, cosmic);
                }
            }
            // the charge aura of the ultimates (Photon's "god_of_thunder_chargeaura"): two rings of lightning
            // turning around Thor, stars of lightning, the hammer crackling
            if (ThorState.flag(p, ThorPowers.F_ULTIMATE) || ThorState.flag(p, ThorPowers.F_TWIRL)) {
                double t = (p.tickCount + RANDOM.nextDouble()) * 0.35D;
                for (int ring = 0; ring < 2; ring++) {
                    double h = ring == 0 ? 0.25D : 1.55D;
                    double r = ring == 0 ? 1.3D : 1.0D;
                    double a0 = t * (ring == 0 ? 1.0D : -1.3D) + RANDOM.nextDouble() * 0.6D;
                    Vec3 c = p.position().add(0.0D, h, 0.0D);
                    Vec3 from = c.add(Math.cos(a0) * r, RANDOM.nextGaussian() * 0.08D, Math.sin(a0) * r);
                    Vec3 to = c.add(Math.cos(a0 + 1.1D) * r, RANDOM.nextGaussian() * 0.08D, Math.sin(a0 + 1.1D) * r);
                    add(from, to, 0.45F, 2, cosmic);
                }
                Vec3 h = hand(p, 1.0F).add(0.0D, 0.5D, 0.0D);
                add(h, h.add(RANDOM.nextGaussian() * 0.7D, RANDOM.nextDouble() * 0.8D, RANDOM.nextGaussian() * 0.7D),
                        0.35F, 2, cosmic);
                if (p.tickCount % 2 == 0) {
                    Vec3 s = p.position().add(RANDOM.nextGaussian() * 1.2D, RANDOM.nextDouble() * 2.4D,
                            RANDOM.nextGaussian() * 1.2D);
                    level.addParticle(ParticleTypes.END_ROD, s.x, s.y, s.z, 0.0D, 0.02D, 0.0D);
                    level.addParticle(ParticleTypes.ELECTRIC_SPARK, s.x, s.y, s.z, 0.0D, 0.05D, 0.0D);
                }
            }
            // a charged hammer crackles; drinking lightning, even more
            boolean charging = ThorState.flag(p, ThorPowers.F_CHARGING);
            if (charging || (ThorState.flag(p, ThorPowers.F_FURY) && p.tickCount % 3 == 0)) {
                Vec3 h = hand(p, 1.0F);
                add(h, h.add((RANDOM.nextDouble() - 0.5D) * 1.2D, RANDOM.nextDouble() * 0.9D,
                        (RANDOM.nextDouble() - 0.5D) * 1.2D), 0.3F, 3, cosmic);
            }
        }
    }

    // ------------------------------------------------------------------
    // Lightning Blast sound: the blast of Man of Tomorrow's Shazam when it starts, its crackle while the key is
    // held; both stop the moment it is released (no old instance keeps playing)
    // ------------------------------------------------------------------

    private static final java.util.Map<java.util.UUID, BlastSound[]> BLAST_SOUNDS = new java.util.HashMap<>();

    private static void blastSounds(Minecraft mc, ClientLevel level) {
        for (Player p : level.players()) {
            boolean on = ThorState.isThor(p) && ThorState.flag(p, ThorPowers.F_BLAST);
            BlastSound[] s = BLAST_SOUNDS.get(p.getUUID());
            if (on && (s == null || s[1].isStopped())) {
                BlastSound start = new BlastSound(com.flash.registry.ModSounds.THOR_BLAST_START.get(), p, false);
                BlastSound loop = new BlastSound(com.flash.registry.ModSounds.THOR_BLAST_LOOP.get(), p, true);
                mc.getSoundManager().play(start);
                mc.getSoundManager().playDelayed(loop, 4);
                BLAST_SOUNDS.put(p.getUUID(), new BlastSound[] {start, loop});
            }
        }
        BLAST_SOUNDS.values().removeIf(s -> s[0].isStopped() && s[1].isStopped());
    }

    private static final class BlastSound extends net.minecraft.client.resources.sounds.AbstractTickableSoundInstance {
        private final Player player;

        BlastSound(net.minecraft.sounds.SoundEvent sound, Player player, boolean loop) {
            super(sound, net.minecraft.sounds.SoundSource.PLAYERS,
                    net.minecraft.client.resources.sounds.SoundInstance.createUnseededRandom());
            this.player = player;
            this.looping = loop;
            this.delay = 0;
            this.volume = loop ? 0.8F : 1.0F;
            this.x = player.getX();
            this.y = player.getEyeY();
            this.z = player.getZ();
        }

        @Override
        public void tick() {
            if (this.player.isRemoved() || !ThorState.isThor(this.player)
                    || !ThorState.flag(this.player, ThorPowers.F_BLAST)) {
                this.stop();
                return;
            }
            this.x = this.player.getX();
            this.y = this.player.getEyeY();
            this.z = this.player.getZ();
        }
    }

    /** About where the hand (with the hammer) is. */
    private static Vec3 hand(Player p, float partial) {
        Vec3 eye = p.getEyePosition(partial);
        Vec3 look = p.getViewVector(partial);
        Vec3 side = look.cross(new Vec3(0.0D, 1.0D, 0.0D));
        side = side.lengthSqr() < 1.0E-6D ? new Vec3(1.0D, 0.0D, 0.0D) : side.normalize();
        boolean first = p == Minecraft.getInstance().player && Minecraft.getInstance().options.getCameraType().isFirstPerson();
        return eye.add(look.scale(first ? 0.7D : 0.5D)).add(side.scale(0.35D)).subtract(0.0D, first ? 0.35D : 0.5D, 0.0D);
    }

    private static float[][] colors(boolean cosmic) {
        return cosmic ? new float[][] {PURPLE_GLOW, PURPLE_MID, PURPLE_CORE}
                : new float[][] {FlashLightningRibbon.BLUE_GLOW_COLOR, FlashLightningRibbon.BLUE_MID_COLOR,
                        FlashLightningRibbon.BLUE_CORE_COLOR};
    }

    private static void arc(VertexConsumer c, Matrix4f m, Vec3 cam, List<Vec3> pts, float w, boolean cosmic,
                            float alpha) {
        float[][] col = colors(cosmic);
        FlashLightningRibbon.drawArc(c, m, cam, pts, 0.16F * w, 0.07F * w, 0.025F * w, col[0], col[1], col[2], alpha);
    }

    /** A jagged loop (or part of one) on the ground around a center. */
    private static List<Vec3> loop(Vec3 c, double r, double from, double to, Random random, double jitter) {
        List<Vec3> pts = new ArrayList<>();
        int n = Math.max(8, (int) (r * Math.abs(to - from) * 1.6D));
        for (int i = 0; i <= n; i++) {
            double a = from + (to - from) * i / n;
            double rr = r + (random.nextDouble() - 0.5D) * jitter;
            pts.add(c.add(Math.cos(a) * rr, 0.15D + random.nextDouble() * jitter * 0.6D, Math.sin(a) * rr));
        }
        return pts;
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            return;
        }
        float partial = event.getPartialTick();
        float time = level.getGameTime() + partial;
        BOLTS.removeIf(b -> time - b.start() > b.life());
        EFFECTS.removeIf(f -> time - f.start() > f.life());
        List<Player> blasting = new ArrayList<>();
        for (Player p : level.players()) {
            if (ThorState.isThor(p) && ThorState.flag(p, ThorPowers.F_BLAST)) {
                blasting.add(p);
            }
        }
        if (BOLTS.isEmpty() && blasting.isEmpty() && EFFECTS.isEmpty()) {
            return;
        }
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        PoseStack pose = event.getPoseStack();
        Vec3 cam = event.getCamera().getPosition();
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f m = pose.last().pose();
        // scorch marks first (under the lightning)
        boolean scorch = false;
        for (Fx f : EFFECTS) {
            if (f.kind() == SCORCH) {
                scorch = true;
                break;
            }
        }
        if (scorch) {
            VertexConsumer decal = buffers.getBuffer(RenderType.entityTranslucent(SCORCH_TEXTURE));
            Matrix3f normal = pose.last().normal();
            for (Fx f : EFFECTS) {
                if (f.kind() != SCORCH) {
                    continue;
                }
                float age = time - f.start();
                float alpha = Math.min(1.0F, Math.min(age / 6.0F, (f.life() - age) / 80.0F));
                double half = 2.4D * f.scale();
                Vec3 c = f.a();
                float y = (float) (c.y + 0.02D);
                int light = LevelRenderer.getLightColor(level, BlockPos.containing(c.x, c.y + 0.2D, c.z));
                decalVertex(decal, m, normal, c.x - half, y, c.z - half, 0, 0, alpha, light);
                decalVertex(decal, m, normal, c.x - half, y, c.z + half, 0, 1, alpha, light);
                decalVertex(decal, m, normal, c.x + half, y, c.z + half, 1, 1, alpha, light);
                decalVertex(decal, m, normal, c.x + half, y, c.z - half, 1, 0, alpha, light);
            }
            buffers.endBatch(RenderType.entityTranslucent(SCORCH_TEXTURE));
        }
        VertexConsumer consumer = buffers.getBuffer(FlashLightningRibbon.RENDER_TYPE);
        Iterator<Bolt> it = BOLTS.iterator();
        while (it.hasNext()) {
            Bolt b = it.next();
            float age = time - b.start();
            float alpha = Math.max(0.0F, 1.0F - age / b.life());
            arc(consumer, m, cam, b.points(), b.scale(), b.cosmic(), alpha);
        }
        for (Fx f : EFFECTS) {
            drawFx(consumer, m, cam, f, time);
        }
        // the Lightning Blast: a bolt from the hand to whatever it hits, re-jagged every frame
        for (Player p : blasting) {
            Vec3 eye = p.getEyePosition(partial);
            Vec3 look = p.getViewVector(partial);
            Vec3 end = eye.add(look.scale(32.0D));
            BlockHitResult block = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE, p));
            Vec3 stop = block.getType() == HitResult.Type.MISS ? end : block.getLocation();
            AABB box = p.getBoundingBox().expandTowards(stop.subtract(eye)).inflate(1.0D);
            EntityHitResult hit = net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(p, eye, stop,
                    box, e -> !e.isSpectator() && e.isPickable(), eye.distanceToSqr(stop));
            if (hit != null) {
                stop = hit.getLocation();
            } else if (block.getType() != HitResult.Type.MISS) {
                // the burnt trail the beam leaves on the blocks, as Superman's heat vision
                SupermanScorch.paint(p.getUUID(), stop, Vec3.atLowerCornerOf(block.getDirection().getNormal()), false);
            }
            Vec3 from = hand(p, partial);
            boolean cosmic = cosmic(p);
            float[][] col = colors(cosmic);
            for (int k = 0; k < 2; k++) {
                FlashLightningRibbon.drawArc(consumer, m, cam, jagged(from, stop, 0.22D, RANDOM), 0.2F, 0.08F, 0.03F,
                        col[0], col[1], col[2], 1.0F);
            }
            if (RANDOM.nextInt(2) == 0) {
                level.addParticle(ParticleTypes.ELECTRIC_SPARK, stop.x, stop.y, stop.z,
                        RANDOM.nextGaussian() * 0.1D, 0.1D, RANDOM.nextGaussian() * 0.1D);
            }
        }
        buffers.endBatch(FlashLightningRibbon.RENDER_TYPE);
        // the light around the lightning (VfxDraw: the Lanterns' glows, rays and spheres, rune rings)
        glowPass(event, m, level, partial, time, blasting);
        pose.popPose();
    }

    private static float[] tone(boolean cosmic) {
        return cosmic ? new float[] {0.6F, 0.25F, 1.0F} : new float[] {0.35F, 0.65F, 1.0F};
    }

    private static void glowPass(RenderLevelStageEvent event, Matrix4f m, ClientLevel level, float partial, float time,
                                 List<Player> blasting) {
        var camera = event.getCamera();
        Vec3 cam = camera.getPosition();
        Vec3 left = new Vec3(camera.getLeftVector());
        Vec3 up = new Vec3(camera.getUpVector());
        Vec3 yUp = new Vec3(0.0D, 1.0D, 0.0D);
        var buf = VfxDraw.begin();
        try {
            // every bolt: a soft wide glow along it, a flare where it ends
            for (Bolt b : BOLTS) {
                float age = time - b.start();
                float alpha = Math.max(0.0F, 1.0F - age / b.life());
                float[] c = tone(b.cosmic());
                VfxDraw.path(buf, m, b.points(), 0.35F * b.scale(), c[0], c[1], c[2], 0.12F * alpha);
                if (b.scale() >= 1.0F) {
                    VfxDraw.burst(buf, m, b.points().get(b.points().size() - 1), left, up, 0.9F * b.scale(), c[0], c[1],
                            c[2], 0.6F * alpha, time * 0.3F);
                }
            }
            for (Fx f : EFFECTS) {
                float age = time - f.start();
                float k = Math.max(0.0F, Math.min(1.0F, age / f.life()));
                float fade = 1.0F - k;
                float[] c = tone(f.cosmic());
                Vec3 a = f.a();
                switch (f.kind()) {
                    case ThorFxPacket.RING -> {
                        double r = f.scale() * (1.0D - Math.pow(1.0D - k, 3.0D));
                        VfxDraw.ring(buf, m, a.add(0.0D, 0.15D, 0.0D), yUp, Math.max(0.3D, r), 0.3D, 0.0F, c[0], c[1],
                                c[2], 0.7F * fade);
                        VfxDraw.wall(buf, m, a, Math.max(0.3D, r), 1.4D * fade, c[0], c[1], c[2], 0.3F * fade);
                        VfxDraw.runeRing(buf, m, a, 1.6D, 12, time * 0.04F, c[0], c[1], c[2], fade);
                    }
                    case ThorFxPacket.WAVE -> {
                        Vec3 dir = f.b();
                        double base = Math.atan2(dir.z, dir.x);
                        double r = Math.max(0.5D, f.scale() - 4.0D + 4.0D * k);
                        for (int i = -4; i <= 4; i++) {
                            double ang = base + Math.toRadians(10.0D * i);
                            Vec3 g = a.add(Math.cos(ang) * r, 0.6D, Math.sin(ang) * r);
                            VfxDraw.glow(buf, m, g, left, up, 1.2D, c[0], c[1], c[2], 0.25F * fade);
                        }
                    }
                    case ThorFxPacket.IMPACT -> {
                        float grow = 1.0F - fade * fade;
                        VfxDraw.sphere(buf, m, a.add(0.0D, 0.5D, 0.0D), f.scale() * (0.2D + 0.6D * grow), cam, c[0],
                                c[1], c[2], 0.6F * fade * fade, time, 2.2D);
                        VfxDraw.ring(buf, m, a.add(0.0D, 0.1D, 0.0D), yUp, f.scale() * (0.2D + 1.1D * grow),
                                0.4D * fade + 0.05D, 0.0F, c[0], c[1], c[2], 0.8F * fade);
                        VfxDraw.burst(buf, m, a.add(0.0D, 0.8D, 0.0D), left, up, f.scale() * (0.6F + grow), c[0], c[1],
                                c[2], 0.8F * fade, time * 0.2F);
                        VfxDraw.runeRing(buf, m, a, f.scale() * 0.45D, 16, time * 0.03F, c[0], c[1], c[2], fade);
                        if (age < 1.0F) {
                            impactScreen(a, 2, 16, 1.2F);
                        }
                    }
                    case ThorFxPacket.BIFROST -> {
                        float in = Math.min(1.0F, age / 4.0F);
                        float out = Math.min(1.0F, (f.life() - age) / 8.0F);
                        float al = Math.max(0.0F, Math.min(in, out));
                        double r = f.scale() * (0.6D + 0.4D * in);
                        VfxDraw.pillar(buf, m, a, a.y, a.y + 120.0D, cam, (float) r * 2.2F, 1.0F, 1.0F, 1.0F,
                                0.2F * al);
                        for (int i = 0; i < 6; i++) {
                            float[] col = rainbow((i / 6.0F + time * 0.02F) % 1.0F);
                            VfxDraw.ring(buf, m, a.add(0.0D, 0.06D + i * 0.01D, 0.0D), yUp, r * (1.1D + i * 0.18D),
                                    0.12D, time * 0.05F, col[0], col[1], col[2], 0.7F * al);
                        }
                        VfxDraw.runeRing(buf, m, a, r * 2.4D, 24, time * 0.02F, 1.0F, 0.95F, 0.8F, al);
                        VfxDraw.burst(buf, m, a.add(0.0D, 1.0D, 0.0D), left, up, (float) r * 3.0F, 1.0F, 1.0F, 1.0F,
                                0.5F * al, time * 0.1F);
                    }
                    case ThorFxPacket.DOME -> {
                        float al = (float) Math.sin(k * Math.PI);
                        VfxDraw.sphere(buf, m, a, f.scale(), cam, 0.6F, 0.25F, 1.0F, 0.5F * al, time, 1.6D);
                        VfxDraw.ring(buf, m, a.add(0.0D, 0.1D, 0.0D), yUp, f.scale(), 0.4D, 0.0F, 0.6F, 0.25F, 1.0F,
                                0.7F * al);
                        VfxDraw.runeRing(buf, m, a, f.scale() * 0.7D, 20, time * 0.03F, 0.7F, 0.4F, 1.0F, al);
                    }
                    case ThorFxPacket.BEAM -> {
                        Vec3 b = f.b();
                        float al = 1.0F - k * 0.6F;
                        VfxDraw.energy(buf, m, a, b, 1.1F * f.scale(), c[0], c[1], c[2], 0.6F * al);
                        double len = a.distanceTo(b);
                        if (len > 1.0D) {
                            Vec3 dn = b.subtract(a).scale(1.0D / len);
                            for (int i = 0; i < 5; i++) {
                                double pos = (time * 2.0D + i * len / 5.0D) % len;
                                VfxDraw.ring(buf, m, a.add(dn.scale(pos)), dn, 1.6D * f.scale(), 0.12D, 0.0F, c[0],
                                        c[1], c[2], 0.5F * al);
                            }
                        }
                        VfxDraw.burst(buf, m, a, left, up, 2.5F * f.scale(), c[0], c[1], c[2], 0.8F * al, time * 0.3F);
                        VfxDraw.burst(buf, m, b, left, up, 4.0F * f.scale(), c[0], c[1], c[2], 0.9F * al, -time * 0.3F);
                        VfxDraw.sphere(buf, m, b, 1.5D * f.scale(), cam, c[0], c[1], c[2], 0.5F * al, time, 2.0D);
                    }
                    default -> {
                    }
                }
            }
            // the Lightning Blast: glow along it, flares at both ends
            for (Player p : blasting) {
                float[] c = tone(cosmic(p));
                Vec3 from = hand(p, partial);
                Vec3 to = p.getEyePosition(partial).add(p.getViewVector(partial).scale(32.0D));
                BlockHitResult block = level.clip(new ClipContext(p.getEyePosition(partial), to,
                        ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
                if (block.getType() != HitResult.Type.MISS) {
                    to = block.getLocation();
                }
                VfxDraw.beam(buf, m, from, to, 0.6F, c[0], c[1], c[2], 0.2F);
                VfxDraw.burst(buf, m, from, left, up, 0.8F, c[0], c[1], c[2], 0.7F, time * 0.4F);
                VfxDraw.burst(buf, m, to, left, up, 1.8F, c[0], c[1], c[2], 0.8F, -time * 0.3F);
            }
            // Thor charged: rings of light round him, the hammer glowing
            for (Player p : level.players()) {
                if (!ThorState.isThor(p)) {
                    continue;
                }
                boolean aura = ThorState.flag(p, ThorPowers.F_ULTIMATE) || ThorState.flag(p, ThorPowers.F_TWIRL);
                boolean charging = ThorState.flag(p, ThorPowers.F_CHARGING) || ThorState.flag(p, ThorPowers.F_FURY);
                if (!aura && !charging) {
                    continue;
                }
                float[] c = tone(cosmic(p));
                Vec3 pos = p.getPosition(partial);
                if (aura) {
                    VfxDraw.ring(buf, m, pos.add(0.0D, 0.25D, 0.0D), yUp, 1.3D, 0.08D, time * 0.2F, c[0], c[1], c[2],
                            0.7F);
                    VfxDraw.ring(buf, m, pos.add(0.0D, 1.55D, 0.0D), yUp, 1.0D, 0.06D, -time * 0.26F, c[0], c[1], c[2],
                            0.6F);
                    VfxDraw.runeRing(buf, m, pos, 1.9D, 10, time * 0.05F, c[0], c[1], c[2], 0.8F);
                    VfxDraw.glow(buf, m, pos.add(0.0D, 1.0D, 0.0D), left, up, 1.8D, c[0], c[1], c[2], 0.2F);
                }
                boolean me = p == Minecraft.getInstance().getCameraEntity()
                        && Minecraft.getInstance().options.getCameraType().isFirstPerson();
                if (!me) {
                    Vec3 h = hand(p, partial);
                    VfxDraw.glow(buf, m, h, left, up, 0.6D, c[0], c[1], c[2], 0.5F);
                    VfxDraw.burst(buf, m, h, left, up, 0.8F, c[0], c[1], c[2], 0.5F, time * 0.5F);
                }
            }
        } finally {
            VfxDraw.end(buf);
        }
    }

    private static final java.util.Set<Long> SCREENED = new java.util.HashSet<>();

    /** God Wand's shockwave over the screen, near enough. */
    private static void impactScreen(Vec3 at, int type, int ticks, float power) {
        long key = (long) (at.x * 31.0D) * 1000003L + (long) (at.z * 17.0D) * 7919L + (long) at.y;
        if (!SCREENED.add(key)) {
            return;
        }
        if (SCREENED.size() > 64) {
            SCREENED.clear();
        }
        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        if (cam.distanceToSqr(at) < 80.0D * 80.0D) {
            com.flash.client.godwand.GodWandPostFxManager.triggerImpact(at.x, at.y + 0.5D, at.z, type, ticks, power);
        }
    }

    private static void decalVertex(VertexConsumer c, Matrix4f m, Matrix3f n, double x, float y, double z, float u,
                                    float v, float alpha, int light) {
        c.vertex(m, (float) x, y, (float) z).color(1.0F, 1.0F, 1.0F, alpha).uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n, 0.0F, 1.0F, 0.0F).endVertex();
    }

    private static void drawFx(VertexConsumer c, Matrix4f m, Vec3 cam, Fx f, float time) {
        float age = time - f.start();
        float k = Math.max(0.0F, Math.min(1.0F, age / f.life()));
        // re-jagged a few times a second, the same within a step
        Random random = new Random(f.seed() + (long) (age / 2.0F) * 7919L);
        Vec3 a = f.a();
        switch (f.kind()) {
            case ThorFxPacket.RING -> {
                // Photon's "storm_striker_shockwave": a ring of lightning running out on the ground
                double r = f.scale() * (1.0D - Math.pow(1.0D - k, 3.0D));
                float alpha = 1.0F - k;
                arc(c, m, cam, loop(a, Math.max(0.3D, r), 0.0D, Math.PI * 2.0D, random, 0.35D), 0.9F, f.cosmic(), alpha);
                for (int i = 0; i < 6; i++) {
                    double ang = random.nextDouble() * Math.PI * 2.0D;
                    Vec3 in = a.add(Math.cos(ang) * r * 0.45D, 0.15D, Math.sin(ang) * r * 0.45D);
                    Vec3 out = a.add(Math.cos(ang) * r, 0.2D, Math.sin(ang) * r);
                    arc(c, m, cam, jagged(in, out, 0.18D, random), 0.5F, f.cosmic(), alpha * 0.8F);
                }
            }
            case ThorFxPacket.WAVE -> {
                // the Stormbreaker wave: an arc of lightning in a cone
                Vec3 dir = f.b();
                double base = Math.atan2(dir.z, dir.x);
                double half = Math.toRadians(40.0D);
                double r = Math.max(0.5D, f.scale() - 4.0D + 4.0D * k);
                float alpha = 1.0F - k;
                arc(c, m, cam, loop(a, r, base - half, base + half, random, 0.4D), 1.0F, f.cosmic(), alpha);
                for (int i = 0; i < 4; i++) {
                    double ang = base - half + random.nextDouble() * 2.0D * half;
                    Vec3 g = a.add(Math.cos(ang) * r, 0.1D, Math.sin(ang) * r);
                    arc(c, m, cam, jagged(g, g.add(0.0D, 1.5D + random.nextDouble() * 1.5D, 0.0D), 0.2D, random), 0.6F,
                            f.cosmic(), alpha);
                }
            }
            case ThorFxPacket.IMPACT -> {
                // Photon's "god_of_thunder_impact": bolts along the ground, glowing cracks, the impact ring
                float alpha = 1.0F - k;
                if (age < 15.0F) {
                    for (int i = 0; i < 12; i++) {
                        double ang = i * Math.PI / 6.0D + random.nextDouble() * 0.3D;
                        double len = f.scale() * (0.6D + 0.4D * random.nextDouble());
                        Vec3 out = a.add(Math.cos(ang) * len, 0.2D, Math.sin(ang) * len);
                        arc(c, m, cam, jagged(a.add(0.0D, 0.3D, 0.0D), out, 0.3D, random), 1.0F, f.cosmic(),
                                1.0F - age / 15.0F);
                    }
                    double rr = f.scale() * 0.35D * Math.min(1.0D, age / 6.0D);
                    arc(c, m, cam, loop(a, Math.max(0.3D, rr), 0.0D, Math.PI * 2.0D, random, 0.3D), 1.2F, f.cosmic(),
                            1.0F - age / 15.0F);
                }
                // the cracks stay still and fade
                Random still = new Random(f.seed());
                for (int i = 0; i < 9; i++) {
                    double ang = still.nextDouble() * Math.PI * 2.0D;
                    double len = f.scale() * (0.4D + 0.5D * still.nextDouble());
                    Vec3 out = a.add(Math.cos(ang) * len, 0.06D, Math.sin(ang) * len);
                    List<Vec3> crack = jagged(a.add(0.0D, 0.06D, 0.0D), out, 0.25D, still);
                    List<Vec3> flat = new ArrayList<>();
                    for (Vec3 p : crack) {
                        flat.add(new Vec3(p.x, a.y + 0.06D, p.z));
                    }
                    arc(c, m, cam, flat, 0.45F, f.cosmic(), alpha * 0.8F);
                }
            }
            case ThorFxPacket.BIFROST -> {
                // the rainbow bridge: strands of every color turning in a column down from the sky
                float in = Math.min(1.0F, age / 4.0F);
                float out = Math.min(1.0F, (f.life() - age) / 8.0F);
                float alpha = Math.max(0.0F, Math.min(in, out));
                double r = f.scale() * (0.6D + 0.4D * in);
                for (int i = 0; i < 12; i++) {
                    double ang = i * Math.PI / 6.0D + time * 0.25D;
                    float[] col = rainbow((i / 12.0F + time * 0.02F) % 1.0F);
                    float[] glow = {col[0], col[1], col[2], 0.35F};
                    float[] mid = {Math.min(1, col[0] + 0.3F), Math.min(1, col[1] + 0.3F), Math.min(1, col[2] + 0.3F),
                            0.55F};
                    float[] core = {1.0F, 1.0F, 1.0F, 0.8F};
                    Vec3 base = a.add(Math.cos(ang) * r, 0.0D, Math.sin(ang) * r);
                    List<Vec3> line = List.of(base, base.add(0.0D, 96.0D, 0.0D));
                    FlashLightningRibbon.drawArc(c, m, cam, line, 0.5F, 0.22F, 0.06F, glow, mid, core, alpha);
                }
                List<Vec3> core = List.of(a, a.add(0.0D, 96.0D, 0.0D));
                FlashLightningRibbon.drawArc(c, m, cam, core, (float) r * 0.8F, (float) r * 0.4F, (float) r * 0.15F,
                        new float[] {1.0F, 1.0F, 1.0F, 0.25F}, new float[] {1.0F, 1.0F, 1.0F, 0.45F},
                        new float[] {1.0F, 1.0F, 1.0F, 0.9F}, alpha);
            }
            case ThorFxPacket.DOME -> {
                // the Devourer King: a dome of cosmic lightning
                float alpha = (float) Math.sin(k * Math.PI) * 0.9F;
                double r = f.scale();
                for (int i = 0; i < 10; i++) {
                    double th = i * Math.PI / 5.0D + time * 0.02D;
                    List<Vec3> mer = new ArrayList<>();
                    for (int j = 0; j <= 10; j++) {
                        double ph = j * Math.PI / 20.0D;
                        mer.add(a.add(Math.cos(th) * Math.cos(ph) * r + random.nextGaussian() * 0.2D,
                                Math.sin(ph) * r * 0.75D + random.nextGaussian() * 0.2D,
                                Math.sin(th) * Math.cos(ph) * r + random.nextGaussian() * 0.2D));
                    }
                    arc(c, m, cam, mer, 1.0F, true, alpha);
                }
                arc(c, m, cam, loop(a, r, 0.0D, Math.PI * 2.0D, random, 0.4D), 1.1F, true, alpha);
                arc(c, m, cam, loop(a.add(0.0D, r * 0.45D, 0.0D), r * 0.8D, 0.0D, Math.PI * 2.0D, random, 0.4D), 0.8F,
                        true, alpha);
            }
            case ThorFxPacket.BEAM -> {
                // the God Blast: a giant beam
                float alpha = 1.0F - k * 0.6F;
                Vec3 b = f.b();
                arc(c, m, cam, jagged(a, b, 0.5D, random), 5.0F * f.scale(), f.cosmic(), alpha);
                arc(c, m, cam, jagged(a, b, 0.35D, random), 3.0F * f.scale(), f.cosmic(), alpha);
                arc(c, m, cam, List.of(a, b), 2.2F * f.scale(), f.cosmic(), alpha);
            }
            default -> {
            }
        }
    }

    private static float[] rainbow(float h) {
        float r = Math.abs(h * 6.0F - 3.0F) - 1.0F;
        float g = 2.0F - Math.abs(h * 6.0F - 2.0F);
        float b = 2.0F - Math.abs(h * 6.0F - 4.0F);
        return new float[] {clamp(r), clamp(g), clamp(b)};
    }

    private static float clamp(float v) {
        return Math.max(0.0F, Math.min(1.0F, v));
    }
}
