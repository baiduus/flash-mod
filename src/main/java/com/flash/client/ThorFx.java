package com.flash.client;

import com.flash.FlashMod;
import com.flash.network.ThorFxPacket;
import com.flash.thor.ThorPowers;
import com.flash.thor.ThorState;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.particles.ParticleTypes;
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
import org.joml.Matrix4f;

/**
 * Thor's lightning, drawn with the blue ribbon of the mod: bolts sent by the server (strikes, arcs, hits), the
 * continuous Lightning Blast from the hand of who holds it, the arcs over the body in the Warrior's Fury and the
 * crackle around a charged Mjolnir.
 */
@Mod.EventBusSubscriber(modid = FlashMod.MODID, value = Dist.CLIENT)
public final class ThorFx {

    private record Bolt(List<Vec3> points, long start, int life, float scale) {
    }

    private static final List<Bolt> BOLTS = new ArrayList<>();
    private static final Random RANDOM = new Random();

    private ThorFx() {
    }

    public static void reset() {
        BOLTS.clear();
    }

    public static void handle(ThorFxPacket p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        if (p.kind() == ThorFxPacket.BOLT) {
            add(new Vec3(p.ax(), p.ay(), p.az()), new Vec3(p.bx(), p.by(), p.bz()), p.scale(), 7);
            mc.level.addParticle(ParticleTypes.ELECTRIC_SPARK, p.bx(), p.by(), p.bz(), 0.0D, 0.1D, 0.0D);
        }
    }

    private static void add(Vec3 a, Vec3 b, float scale, int life) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        BOLTS.add(new Bolt(jagged(a, b, 0.18D * Math.max(0.5F, scale)), mc.level.getGameTime(), life, scale));
        if (BOLTS.size() > 160) {
            BOLTS.remove(0);
        }
    }

    /** A zig-zag line from a to b. */
    private static List<Vec3> jagged(Vec3 a, Vec3 b, double amp) {
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
            out.add(a.add(d.scale(t)).add(side.scale((RANDOM.nextDouble() - 0.5D) * 2.0D * w))
                    .add(up.scale((RANDOM.nextDouble() - 0.5D) * 2.0D * w)));
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
        for (Player p : level.players()) {
            if (!ThorState.isThor(p)) {
                continue;
            }
            // Warrior's Fury: lightning all over the body
            if (ThorState.flag(p, ThorPowers.F_FURY) || ThorState.flag(p, ThorPowers.F_ULTIMATE)) {
                for (int i = 0; i < 2; i++) {
                    Vec3 c = p.position().add(0.0D, 0.3D + RANDOM.nextDouble() * 1.5D, 0.0D);
                    Vec3 o = c.add((RANDOM.nextDouble() - 0.5D) * 1.4D, (RANDOM.nextDouble() - 0.5D) * 1.0D,
                            (RANDOM.nextDouble() - 0.5D) * 1.4D);
                    add(c, o, 0.35F, 3);
                }
            }
            // a charged hammer crackles; drinking lightning, even more
            boolean charging = ThorState.flag(p, ThorPowers.F_CHARGING);
            if (charging || (ThorState.flag(p, ThorPowers.F_OVERCHARGE) && p.tickCount % 3 == 0)) {
                Vec3 h = hand(p, 1.0F);
                add(h, h.add((RANDOM.nextDouble() - 0.5D) * 1.2D, RANDOM.nextDouble() * 0.9D,
                        (RANDOM.nextDouble() - 0.5D) * 1.2D), 0.3F, 3);
            }
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
        List<Player> blasting = new ArrayList<>();
        for (Player p : level.players()) {
            if (ThorState.isThor(p) && ThorState.flag(p, ThorPowers.F_BLAST)) {
                blasting.add(p);
            }
        }
        if (BOLTS.isEmpty() && blasting.isEmpty()) {
            return;
        }
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer consumer = buffers.getBuffer(FlashLightningRibbon.RENDER_TYPE);
        PoseStack pose = event.getPoseStack();
        Vec3 cam = event.getCamera().getPosition();
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f m = pose.last().pose();
        Iterator<Bolt> it = BOLTS.iterator();
        while (it.hasNext()) {
            Bolt b = it.next();
            float age = time - b.start();
            float alpha = Math.max(0.0F, 1.0F - age / b.life());
            float w = b.scale();
            FlashLightningRibbon.drawArc(consumer, m, cam, b.points(), 0.16F * w, 0.07F * w, 0.025F * w,
                    FlashLightningRibbon.BLUE_GLOW_COLOR, FlashLightningRibbon.BLUE_MID_COLOR,
                    FlashLightningRibbon.BLUE_CORE_COLOR, alpha);
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
            }
            Vec3 from = hand(p, partial);
            for (int k = 0; k < 2; k++) {
                FlashLightningRibbon.drawArc(consumer, m, cam, jagged(from, stop, 0.22D), 0.2F, 0.08F, 0.03F,
                        FlashLightningRibbon.BLUE_GLOW_COLOR, FlashLightningRibbon.BLUE_MID_COLOR,
                        FlashLightningRibbon.BLUE_CORE_COLOR, 1.0F);
            }
            if (RANDOM.nextInt(2) == 0) {
                level.addParticle(ParticleTypes.ELECTRIC_SPARK, stop.x, stop.y, stop.z,
                        RANDOM.nextGaussian() * 0.1D, 0.1D, RANDOM.nextGaussian() * 0.1D);
            }
        }
        pose.popPose();
        buffers.endBatch(FlashLightningRibbon.RENDER_TYPE);
    }
}
