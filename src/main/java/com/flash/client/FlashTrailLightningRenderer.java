package com.flash.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/**
 * Draws the seven simultaneous trail streams. Segment alpha follows the age of
 * each point, so the tail fades smoothly while the newest part remains bright.
 */
@Mod.EventBusSubscriber(modid = com.flash.FlashMod.MODID, value = Dist.CLIENT)
public final class FlashTrailLightningRenderer {

    private FlashTrailLightningRenderer() {
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || FlashTrailTracker.states().isEmpty()) {
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

        for (FlashTrailTracker.TrailState state : FlashTrailTracker.states()) {
            for (FlashTrailTracker.TrailStream stream : state.streams) {
                if (stream.points.size() < 2) {
                    continue;
                }
                List<FlashTrailTracker.TrailPoint> points = new ArrayList<>(stream.points);
                AABB bounds = bounds(points);
                if (!event.getFrustum().isVisible(bounds)) {
                    continue;
                }

                float widthScale = 1.42F + state.level * 0.025F;
                for (int i = 1; i < points.size(); i++) {
                    FlashTrailTracker.TrailPoint older = points.get(i - 1);
                    FlashTrailTracker.TrailPoint newer = points.get(i);
                    float age = now - older.createdAt();
                    float fade = Mth.clamp(1.0F - age / FlashTrailTracker.LIFETIME_TICKS,
                            0.0F, 1.0F);
                    fade = fade * fade * (3.0F - 2.0F * fade);
                    if (fade <= 0.01F) {
                        continue;
                    }
                    fade = Math.min(1.0F, fade * 1.12F);

                    List<Vec3> segment = List.of(older.position(), newer.position());
                    FlashLightningRibbon.drawArc(consumer, matrix, cameraPos, segment,
                            FlashLightningRibbon.BODY_GLOW_WIDTH * widthScale,
                            FlashLightningRibbon.BODY_MID_WIDTH * widthScale,
                            FlashLightningRibbon.BODY_CORE_WIDTH * widthScale,
                            fade);

                    if (i >= 2 && i % 3 == 0) {
                        RandomSource branchRandom = RandomSource.create(
                                state.seed
                                        ^ (stream.lane * 0x9E3779B97F4A7C15L)
                                        ^ (older.createdAt() * 0x632BE59BD9B4E019L));
                        if (branchRandom.nextFloat() < 0.82F) {
                            List<Vec3> branch = FlashLightningRibbon.buildBranch(
                                    older.position(), branchRandom, now,
                                    branchRandom.nextDouble() * Mth.TWO_PI);
                            FlashLightningRibbon.drawArc(consumer, matrix, cameraPos, branch,
                                    FlashLightningRibbon.BRANCH_GLOW_WIDTH * widthScale * 1.25F,
                                    FlashLightningRibbon.BRANCH_MID_WIDTH * widthScale * 1.25F,
                                    FlashLightningRibbon.BRANCH_CORE_WIDTH * widthScale * 1.25F,
                                    fade);
                            if (branchRandom.nextFloat() < 0.35F) {
                                List<Vec3> secondary = FlashLightningRibbon.buildBranch(
                                        older.position(), branchRandom, now,
                                        branchRandom.nextDouble() * Mth.TWO_PI);
                                FlashLightningRibbon.drawArc(consumer, matrix, cameraPos, secondary,
                                        FlashLightningRibbon.BRANCH_GLOW_WIDTH * widthScale,
                                        FlashLightningRibbon.BRANCH_MID_WIDTH * widthScale,
                                        FlashLightningRibbon.BRANCH_CORE_WIDTH * widthScale,
                                        fade);
                            }
                        }
                    }
                }
            }
        }

        poseStack.popPose();
        buffers.endBatch(FlashLightningRibbon.RENDER_TYPE);
    }

    private static AABB bounds(List<FlashTrailTracker.TrailPoint> points) {
        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double minZ = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;
        double maxZ = -Double.MAX_VALUE;
        for (FlashTrailTracker.TrailPoint point : points) {
            Vec3 position = point.position();
            minX = Math.min(minX, position.x);
            minY = Math.min(minY, position.y);
            minZ = Math.min(minZ, position.z);
            maxX = Math.max(maxX, position.x);
            maxY = Math.max(maxY, position.y);
            maxZ = Math.max(maxZ, position.z);
        }
        return new AABB(minX, minY, minZ, maxX, maxY, maxZ).inflate(1.2D);
    }
}
