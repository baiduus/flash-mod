package com.flash.client;

import com.flash.FlashMod;
import com.flash.network.MachineFxPacket;
import com.flash.transformation.TransformationSequence;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/**
 * The transformation in the Flash's machine, drawn in light (VfxDraw: the Lanterns' glows, rays and spheres,
 * God Wand's jagged lightning and screen shockwave/impact frames):
 * <ul>
 * <li>charging (the six seconds): the Speed Force emblem lighting on the floor, sparks of light racing round the
 * accelerator ring faster and faster, rings of light climbing the cage, arcs jumping between the ribs, a growing
 * glow round him;</li>
 * <li>the strike: a bolt from the rod on the roof down through him, a flare, a sphere of light, the screen
 * shaken;</li>
 * <li>done: a burst of golden light out of the cabin, a ring running over the ground, rays, God Wand's impact
 * frames.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = FlashMod.MODID, value = Dist.CLIENT)
public final class MachineVfx {

    private static final float[] GOLD = {1.0F, 0.72F, 0.12F};
    private static final float[] WHITE = {1.0F, 0.96F, 0.8F};

    private static final class Run {
        Vec3 chest;
        double top;
        float progress;
        int strike = -1;
        int done = -1;
        long seed = new Random().nextLong();
    }

    private static final Map<Long, Run> RUNS = new HashMap<>();

    private MachineVfx() {
    }

    private static long key(double x, double z) {
        return ((long) Math.floor(x) << 32) ^ ((long) Math.floor(z) & 0xFFFFFFFFL);
    }

    public static void receive(MachineFxPacket p) {
        long k = key(p.x(), p.z());
        Run r = RUNS.computeIfAbsent(k, n -> new Run());
        r.chest = new Vec3(p.x(), p.y(), p.z());
        r.top = p.top();
        Minecraft mc = Minecraft.getInstance();
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        boolean near = cam.distanceToSqr(r.chest) < 80.0D * 80.0D;
        switch (p.stage()) {
            case MachineFxPacket.RUNNING -> {
                if (r.done >= 0) {
                    // a new transformation in the same machine
                    r.done = -1;
                    r.strike = -1;
                }
                r.progress = p.progress();
            }
            case MachineFxPacket.STRIKE -> {
                r.strike = 0;
                if (near) {
                    com.flash.client.godwand.GodWandPostFxManager.triggerImpact(p.x(), p.y(), p.z(), 2, 16, 1.3F);
                }
            }
            case MachineFxPacket.DONE -> {
                r.done = 0;
                if (near) {
                    com.flash.client.godwand.GodWandPostFxManager.triggerImpact(p.x(), p.y(), p.z(), 5, 20, 1.2F);
                }
                if (mc.level != null) {
                    for (int i = 0; i < 40; i++) {
                        mc.level.addParticle(ParticleTypes.ELECTRIC_SPARK, p.x(), p.y(), p.z(),
                                (mc.level.random.nextDouble() - 0.5D) * 1.2D, mc.level.random.nextDouble() * 0.6D,
                                (mc.level.random.nextDouble() - 0.5D) * 1.2D);
                    }
                }
            }
            default -> RUNS.remove(k);
        }
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (mc.level == null) {
            RUNS.clear();
            return;
        }
        if (mc.isPaused() || RUNS.isEmpty()) {
            return;
        }
        for (Iterator<Run> it = RUNS.values().iterator(); it.hasNext(); ) {
            Run r = it.next();
            if (r.done >= 0) {
                if (++r.done > 40) {
                    it.remove();
                }
                continue;
            }
            r.progress++;
            if (r.strike >= 0) {
                r.strike++;
            }
            if (r.progress > TransformationSequence.TOTAL_TICKS + 40) {
                it.remove();
            }
        }
    }

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || RUNS.isEmpty()) {
            return;
        }
        Camera camera = event.getCamera();
        Vec3 cam = camera.getPosition();
        Vec3 left = new Vec3(camera.getLeftVector());
        Vec3 up = new Vec3(camera.getUpVector());
        float pt = event.getPartialTick();
        PoseStack ps = event.getPoseStack();
        ps.pushPose();
        ps.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f m = ps.last().pose();
        BufferBuilder buf = VfxDraw.begin();
        try {
            for (Run r : RUNS.values()) {
                if (r.chest != null && r.chest.distanceToSqr(cam) < 128.0D * 128.0D) {
                    draw(buf, m, r, pt, cam, left, up);
                }
            }
        } finally {
            VfxDraw.end(buf);
            ps.popPose();
        }
    }

    private static void draw(BufferBuilder buf, Matrix4f m, Run r, float pt, Vec3 cam, Vec3 left, Vec3 up) {
        Vec3 yUp = new Vec3(0.0D, 1.0D, 0.0D);
        Vec3 chest = r.chest;
        Vec3 floor = chest.add(0.0D, -1.0D, 0.0D);
        if (r.done >= 0) {
            float k = Mth.clamp((r.done + pt) / 40.0F, 0.0F, 1.0F);
            float grow = 1.0F - (1.0F - k) * (1.0F - k);
            float fade = (1.0F - k) * (1.0F - k);
            VfxDraw.sphere(buf, m, chest, 0.5D + 5.0D * grow, cam, GOLD[0], GOLD[1], GOLD[2], 0.75F * fade, r.done, 2.2D);
            VfxDraw.ring(buf, m, floor.add(0.0D, 0.05D, 0.0D), yUp, 1.0D + 11.0D * grow, 0.4D * (1.0F - k) + 0.05D,
                    0.0F, GOLD[0], GOLD[1], GOLD[2], 0.9F * fade);
            VfxDraw.burst(buf, m, chest, left, up, 2.0F + 6.0F * grow, WHITE[0], WHITE[1], WHITE[2], 0.9F * fade,
                    r.done * 0.05F);
            VfxDraw.pillar(buf, m, chest, floor.y, r.top + 30.0D * grow, cam, 1.4F * fade, GOLD[0], GOLD[1], GOLD[2],
                    0.6F * fade);
            return;
        }
        float t = r.progress + pt;
        float k = Mth.clamp(t / TransformationSequence.TOTAL_TICKS, 0.0F, 1.0F);
        float in = Mth.clamp(t / 15.0F, 0.0F, 1.0F);
        // the emblem on the floor
        VfxDraw.hexCircle(buf, m, floor.add(0.0D, 0.02D, 0.0D), 1.05D, t, GOLD[0], GOLD[1], GOLD[2], 0.85F * in);
        // sparks of light racing round the accelerator ring, faster and faster
        double ringY = floor.y + 1.6D;
        double speed = 0.08D + 0.5D * k * k;
        for (int i = 0; i < 4; i++) {
            double a = t * speed + i * Math.PI / 2.0D;
            Vec3 head = new Vec3(chest.x + Math.sin(a) * 1.47D, ringY, chest.z - Math.cos(a) * 1.47D);
            for (int tail = 1; tail <= 5; tail++) {
                double ab = a - tail * 0.12D;
                double aa = a - (tail - 1) * 0.12D;
                Vec3 p0 = new Vec3(chest.x + Math.sin(ab) * 1.47D, ringY, chest.z - Math.cos(ab) * 1.47D);
                Vec3 p1 = new Vec3(chest.x + Math.sin(aa) * 1.47D, ringY, chest.z - Math.cos(aa) * 1.47D);
                VfxDraw.beam(buf, m, p0, p1, 0.12F, GOLD[0], GOLD[1], GOLD[2], 0.8F * in * (1.0F - tail / 6.0F));
            }
            VfxDraw.glow(buf, m, head, left, up, 0.3D, WHITE[0], WHITE[1], WHITE[2], 0.9F * in);
        }
        // rings of light climbing the cage
        for (int i = 0; i < 3; i++) {
            double s = ((t * (0.012D + 0.03D * k) + i / 3.0D) % 1.0D);
            VfxDraw.ring(buf, m, floor.add(0.0D, s * 3.4D, 0.0D), yUp, 1.12D, 0.05D, 0.0F, GOLD[0], GOLD[1], GOLD[2],
                    0.6F * in * (float) Math.sin(Math.PI * s));
        }
        // arcs between the ribs
        Random rnd = new Random(r.seed + (long) (t / 2.0F));
        int arcs = 1 + (int) (k * 5.0F);
        for (int i = 0; i < arcs; i++) {
            double a0 = Math.toRadians(80.0D + rnd.nextDouble() * 200.0D);
            double a1 = a0 + (rnd.nextDouble() - 0.5D) * 1.2D;
            Vec3 p0 = new Vec3(chest.x + Math.sin(a0) * 1.12D, floor.y + 0.3D + rnd.nextDouble() * 3.0D,
                    chest.z - Math.cos(a0) * 1.12D);
            Vec3 p1 = new Vec3(chest.x + Math.sin(a1) * 0.4D, chest.y + (rnd.nextDouble() - 0.5D) * 1.2D,
                    chest.z - Math.cos(a1) * 0.4D);
            VfxDraw.path(buf, m, VfxDraw.jagged(p0, p1, rnd.nextLong(), 1.0D, 3), 0.04F, GOLD[0], GOLD[1], GOLD[2], 0.8F);
        }
        // the growing glow round him
        VfxDraw.glow(buf, m, chest, left, up, 0.8D + 1.2D * k, GOLD[0], GOLD[1], GOLD[2], 0.15F + 0.35F * k);
        VfxDraw.sphere(buf, m, chest, 0.9D, cam, GOLD[0], GOLD[1], GOLD[2], 0.1F + 0.35F * k, t, 2.4D);
        // the strike: a bolt from the rod through him
        if (r.strike >= 0) {
            float s = r.strike + pt;
            float al = Mth.clamp(1.0F - s / 14.0F, 0.0F, 1.0F);
            if (al > 0.0F) {
                Vec3 rod = new Vec3(chest.x, r.top, chest.z);
                Vec3[] bolt = VfxDraw.jagged(rod.add(0.0D, 40.0D, 0.0D), floor, r.seed + (long) (s / 2.0F), 0.4D, 5);
                VfxDraw.path(buf, m, bolt, 0.35F, WHITE[0], WHITE[1], WHITE[2], al);
                VfxDraw.burst(buf, m, chest, left, up, 3.5F * (1.3F - al * 0.3F), WHITE[0], WHITE[1], WHITE[2], al,
                        s * 0.2F);
                VfxDraw.sphere(buf, m, chest, 0.6D + 2.0D * (1.0F - al), cam, WHITE[0], WHITE[1], WHITE[2], 0.6F * al, s,
                        2.0D);
                VfxDraw.burst(buf, m, rod, left, up, 1.5F, WHITE[0], WHITE[1], WHITE[2], al, -s * 0.3F);
            }
        }
    }
}
