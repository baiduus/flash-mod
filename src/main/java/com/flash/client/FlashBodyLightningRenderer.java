package com.flash.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/**
 * Electric aura hugging the body of transformed Flash players (level 3+).
 *
 * <p>Rebuilt from scratch: a handful of short, thin bolts that crackle close to
 * the body and run from orange at the base to yellow at the tip. The shape is
 * re-rolled every other tick so it flickers instead of gliding. It is never
 * drawn for the local player in first person, so the view stays clear.</p>
 */
@Mod.EventBusSubscriber(modid = com.flash.FlashMod.MODID, value = Dist.CLIENT)
public final class FlashBodyLightningRenderer {

    private static final double MAX_RENDER_DISTANCE_SQR = 48.0D * 48.0D;
    private static final float BOLT_WIDTH = 0.0075F;

    /** Zones around the body: angle (0 = front), radius, height, vertical drift. */
    private static final double[][] ZONES = {
            {0.00D, 0.30D, 1.15D, 0.18D},
            {Math.PI, 0.30D, 1.10D, -0.14D},
            {-Math.PI / 2.0D, 0.40D, 0.98D, 0.10D},
            {Math.PI / 2.0D, 0.40D, 0.98D, -0.10D},
            {-0.58D, 0.46D, 1.28D, 0.16D},
            {0.58D, 0.46D, 1.28D, -0.16D},
            {-0.32D, 0.26D, 0.48D, 0.10D},
            {0.32D, 0.26D, 0.48D, -0.10D},
            {0.12D, 0.22D, 1.63D, 0.12D},
    };

    private FlashBodyLightningRenderer() {
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        Player localPlayer = minecraft.player;
        if (level == null || localPlayer == null) {
            return;
        }
        boolean firstPerson = minecraft.options.getCameraType().isFirstPerson();

        List<Player> visible = new ArrayList<>();
        for (Player player : level.players()) {
            if (!player.isAlive() || player.isSpectator()
                    || FlashClientVisualLevels.get(player) < 3
                    || (player == localPlayer && (firstPerson
                        || !com.flash.config.FlashClientConfig.BODY_LIGHTNING.get()))
                    || player.distanceToSqr(localPlayer) > MAX_RENDER_DISTANCE_SQR) {
                continue;
            }
            if (event.getFrustum().isVisible(player.getBoundingBoxForCulling().inflate(1.0D))) {
                visible.add(player);
            }
        }
        if (visible.isEmpty()) {
            return;
        }

        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        VertexConsumer consumer = buffers.getBuffer(FlashLightningRibbon.RENDER_TYPE);
        PoseStack poseStack = event.getPoseStack();
        Vec3 cameraPos = event.getCamera().getPosition();
        float partialTick = event.getPartialTick();

        poseStack.pushPose();
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        Matrix4f matrix = poseStack.last().pose();
        for (Player player : visible) {
            renderPlayer(consumer, matrix, cameraPos, player,
                    FlashClientVisualLevels.get(player), partialTick);
        }
        poseStack.popPose();
        buffers.endBatch(FlashLightningRibbon.RENDER_TYPE);
    }

    private static void renderPlayer(VertexConsumer consumer, Matrix4f matrix, Vec3 cameraPos,
                                     Player player, int level, float partialTick) {
        Vec3 position = player.getPosition(partialTick);
        double yaw = Mth.rotLerp(partialTick, player.yBodyRotO, player.yBodyRot) * Mth.DEG_TO_RAD;
        float time = player.tickCount + partialTick;
        long frame = (long) (time * 0.5F);
        long seed = player.getUUID().getMostSignificantBits()
                ^ Long.rotateLeft(player.getUUID().getLeastSignificantBits(), 23);

        boolean negative = FlashClientVisualLevels.isNegative(player);
        int count = 5 + level / 3;
        int offset = (int) (frame % ZONES.length);
        for (int arc = 0; arc < count; arc++) {
            RandomSource random = RandomSource.create(
                    seed + 0x9E3779B97F4A7C15L * (arc + 1L) + frame * 0x632BE59BD9B4E019L);
            double[] zone = ZONES[(arc * 2 + offset) % ZONES.length];

            double angle = zone[0] + (random.nextDouble() - 0.5D) * 0.35D;
            double span = (0.30D + random.nextDouble() * 0.40D) * (random.nextBoolean() ? 1.0D : -1.0D);
            double radius = zone[1] + (random.nextDouble() - 0.5D) * 0.08D;
            double y = zone[2] + (random.nextDouble() - 0.5D) * 0.30D;
            int segments = 4 + random.nextInt(2);

            List<Vec3> points = new ArrayList<>(segments + 1);
            for (int i = 0; i <= segments; i++) {
                double t = i / (double) segments;
                double a = yaw + angle + span * t;
                double r = radius + Math.sin(Math.PI * t) * 0.05D
                        + (i == 0 || i == segments ? 0.0D : (random.nextDouble() - 0.5D) * 0.07D);
                double py = y + zone[3] * t
                        + (i == 0 || i == segments ? 0.0D : (random.nextDouble() - 0.5D) * 0.07D);
                points.add(new Vec3(position.x - Math.sin(a) * r, position.y + py,
                        position.z + Math.cos(a) * r));
            }
            FlashLightningRibbon.drawBolt(consumer, matrix, cameraPos, points,
                    BOLT_WIDTH, 1.0F, negative);

            if (random.nextFloat() < 0.30F) {
                Vec3 start = points.get(1 + random.nextInt(segments - 1));
                List<Vec3> branch = FlashLightningRibbon.buildBranch(start, random, time, arc);
                FlashLightningRibbon.drawBolt(consumer, matrix, cameraPos, branch,
                        BOLT_WIDTH * 0.6F, 0.9F, negative);
            }
        }
    }
}
