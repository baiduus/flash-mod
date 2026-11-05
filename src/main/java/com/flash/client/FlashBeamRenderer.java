package com.flash.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/**
 * Client renderer for the bright lightning beam and the mass-punch shockwave.
 * It reuses the same ribbon implementation as the body aura and the trail.
 */
@Mod.EventBusSubscriber(modid = com.flash.FlashMod.MODID, value = Dist.CLIENT)
public final class FlashBeamRenderer {

    private static final List<Beam> BEAMS = new ArrayList<>();

    private FlashBeamRenderer() {
    }

    public static void add(Vec3 start, Vec3 end, int color,
                           int durationTicks, float width, boolean negative) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || durationTicks <= 0) {
            return;
        }
        BEAMS.add(new Beam(start, end, color, durationTicks, width, negative,
                level.getGameTime(), level.random.nextLong()));
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES
                || BEAMS.isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            BEAMS.clear();
            return;
        }

        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        VertexConsumer consumer = buffers.getBuffer(FlashLightningRibbon.RENDER_TYPE);
        PoseStack poseStack = event.getPoseStack();
        Camera camera = event.getCamera();
        Vec3 cameraPos = camera.getPosition();
        float now = level.getGameTime() + event.getPartialTick();

        poseStack.pushPose();
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        Matrix4f matrix = poseStack.last().pose();

        Iterator<Beam> iterator = BEAMS.iterator();
        while (iterator.hasNext()) {
            Beam beam = iterator.next();
            float age = now - beam.createdAt;
            if (age >= beam.duration) {
                iterator.remove();
                continue;
            }
            if (!event.getFrustum().isVisible(beam.bounds())) {
                continue;
            }
            float fade = Mth.clamp(1.0F - age / beam.duration, 0.0F, 1.0F);
            fade = fade * fade * (3.0F - 2.0F * fade);
            renderBeam(consumer, matrix, cameraPos, beam, now, fade);
        }

        poseStack.popPose();
        buffers.endBatch(FlashLightningRibbon.RENDER_TYPE);
    }

    private static void renderBeam(VertexConsumer consumer, Matrix4f matrix,
                                   Vec3 cameraPos, Beam beam, float time,
                                   float fade) {
        Vec3 direction = beam.end.subtract(beam.start);
        if (direction.lengthSqr() < 1.0E-6D) {
            return;
        }
        Vec3 normal = direction.normalize();
        Vec3 side = normal.cross(new Vec3(0.0D, 1.0D, 0.0D));
        if (side.lengthSqr() < 1.0E-6D) {
            side = normal.cross(new Vec3(1.0D, 0.0D, 0.0D));
        }
        side = side.normalize();
        Vec3 up = side.cross(normal).normalize();

        RandomSource random = RandomSource.create(beam.seed ^ (long) (time * 13.0F));
        for (int strand = -1; strand <= 1; strand++) {
            int segments = 26;
            List<Vec3> points = new ArrayList<>(segments + 1);
            for (int i = 0; i <= segments; i++) {
                double t = i / (double) segments;
                double envelope = Math.sin(Math.PI * t);
                double lateral = (random.nextDouble() - 0.5D) * 0.16D * envelope
                        + strand * 0.075D * beam.width;
                double vertical = (random.nextDouble() - 0.5D) * 0.12D * envelope;
                points.add(beam.start.add(direction.scale(t))
                        .add(side.scale(lateral))
                        .add(up.scale(vertical)));
            }
            float width = beam.width * (strand == 0 ? 1.0F : 0.75F);
            FlashLightningRibbon.drawArc(consumer, matrix, cameraPos, points,
                    0.095F * width, 0.042F * width, 0.015F * width,
                    beam.negative ? FlashLightningRibbon.RED_GLOW_COLOR : FlashLightningRibbon.GLOW_COLOR,
                    beam.negative ? FlashLightningRibbon.RED_MID_COLOR : FlashLightningRibbon.MID_COLOR,
                    beam.negative ? FlashLightningRibbon.RED_CORE_COLOR : FlashLightningRibbon.CORE_COLOR, fade);

            if (random.nextFloat() < 0.85F) {
                Vec3 origin = points.get(4 + random.nextInt(segments - 8));
                List<Vec3> branch = FlashLightningRibbon.buildBranch(
                        origin, random, time, random.nextDouble() * Mth.TWO_PI);
                FlashLightningRibbon.drawArc(consumer, matrix, cameraPos, branch,
                        0.045F * width, 0.020F * width, 0.008F * width,
                        beam.negative ? FlashLightningRibbon.RED_GLOW_COLOR : FlashLightningRibbon.GLOW_COLOR,
                        beam.negative ? FlashLightningRibbon.RED_MID_COLOR : FlashLightningRibbon.MID_COLOR,
                        beam.negative ? FlashLightningRibbon.RED_CORE_COLOR : FlashLightningRibbon.CORE_COLOR, fade);
            }
        }

        // Bright impact flare at the destination.
        RandomSource flare = RandomSource.create(beam.seed ^ 0x51ED270BL);
        for (int i = 0; i < 5; i++) {
            Vec3 flareDirection = new Vec3(
                    flare.nextDouble() - 0.5D,
                    flare.nextDouble() - 0.5D,
                    flare.nextDouble() - 0.5D
            ).normalize().scale(0.35D + flare.nextDouble() * 0.65D);
            List<Vec3> flarePoints = new ArrayList<>();
            flarePoints.add(beam.end);
            flarePoints.add(beam.end.add(flareDirection));
            FlashLightningRibbon.drawArc(consumer, matrix, cameraPos, flarePoints,
                    0.045F, 0.020F, 0.008F,
                    beam.negative ? FlashLightningRibbon.RED_GLOW_COLOR : FlashLightningRibbon.GLOW_COLOR,
                    beam.negative ? FlashLightningRibbon.RED_MID_COLOR : FlashLightningRibbon.MID_COLOR,
                    beam.negative ? FlashLightningRibbon.RED_CORE_COLOR : FlashLightningRibbon.CORE_COLOR, fade);
        }
    }

    private record Beam(Vec3 start, Vec3 end, int color, int duration,
                        float width, boolean negative, float createdAt, long seed) {
        net.minecraft.world.phys.AABB bounds() {
            return new net.minecraft.world.phys.AABB(
                    Math.min(start.x, end.x), Math.min(start.y, end.y),
                    Math.min(start.z, end.z), Math.max(start.x, end.x),
                    Math.max(start.y, end.y), Math.max(start.z, end.z)
            ).inflate(1.0D);
        }
    }
}
