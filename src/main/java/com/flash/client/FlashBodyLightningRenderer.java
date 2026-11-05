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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/**
 * World-space electric aura around every transformed Flash player.
 *
 * <p>The aura is built from many short arcs anchored to the front, back, arms,
 * legs and sides of the body. Compared with the previous version the
 * individual arcs are thinner and shorter, there are more of them and they
 * move noticeably faster. The colour ramp and the lightning texture are the
 * same approved yellow electric style; only the distribution and density
 * changed.</p>
 */
@Mod.EventBusSubscriber(modid = com.flash.FlashMod.MODID, value = Dist.CLIENT)
public final class FlashBodyLightningRenderer {

    private static final float BODY_WIDTH_SCALE = 0.64F;
    private static final double MAX_RENDER_DISTANCE_SQR = 64.0D * 64.0D;

    /**
     * Body zones used to distribute the arcs. angle 0 points in front of the
     * player (local +Z), PI points behind and +/-PI/2 to the sides.
     */
    private static final double[][] ZONES = {
            // angle, radius, height, vertical drift, span
            {0.00D, 0.30D, 1.15D, 0.20D, 0.95D},   // chest, front
            {Math.PI, 0.30D, 1.10D, -0.15D, 0.90D}, // chest, back
            {-Math.PI / 2.0D, 0.42D, 0.98D, 0.10D, 1.05D}, // left side
            {Math.PI / 2.0D, 0.42D, 0.98D, -0.10D, 1.05D}, // right side
            {-0.58D, 0.50D, 1.28D, 0.25D, 0.80D},  // left arm
            {0.58D, 0.50D, 1.28D, -0.20D, 0.80D},  // right arm
            {-0.32D, 0.27D, 0.48D, 0.10D, 0.70D},  // left leg
            {0.32D, 0.27D, 0.48D, -0.10D, 0.70D},  // right leg
            {0.12D, 0.22D, 1.63D, 0.15D, 0.65D},   // head
            {Math.PI - 0.18D, 0.30D, 0.82D, -0.20D, 0.75D}, // hips, back
            {-0.16D, 0.27D, 1.00D, 0.05D, 0.90D},  // chest, front-low
            {Math.PI + 0.20D, 0.38D, 1.46D, 0.12D, 0.85D}  // shoulders, back
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

        List<Player> visible = new ArrayList<>();
        for (Player player : level.players()) {
            if (!player.isAlive()
                    || player.isSpectator()
                    || FlashClientVisualLevels.get(player) < 3
                    || player.distanceToSqr(localPlayer) > MAX_RENDER_DISTANCE_SQR) {
                continue;
            }
            if (event.getFrustum().isVisible(player.getBoundingBoxForCulling().inflate(1.8D))) {
                visible.add(player);
            }
        }
        if (visible.isEmpty()) {
            return;
        }

        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        VertexConsumer consumer = buffers.getBuffer(FlashLightningRibbon.RENDER_TYPE);
        PoseStack poseStack = event.getPoseStack();
        Camera camera = event.getCamera();
        Vec3 cameraPos = camera.getPosition();
        float partialTick = event.getPartialTick();

        poseStack.pushPose();
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        Matrix4f matrix = poseStack.last().pose();

        for (Player player : visible) {
            renderPlayer(consumer, matrix, cameraPos, player,
                    FlashClientVisualLevels.get(player), partialTick, localPlayer, minecraft);
        }

        poseStack.popPose();
        buffers.endBatch(FlashLightningRibbon.RENDER_TYPE);
    }

    private static void renderPlayer(VertexConsumer consumer, Matrix4f matrix, Vec3 cameraPos,
                                     Player player, int level, float partialTick,
                                     Player localPlayer, Minecraft minecraft) {
        Vec3 position = player.getPosition(partialTick);
        float yaw = Mth.rotLerp(partialTick, player.yRotO, player.getYRot());
        float time = player.tickCount + partialTick;
        boolean firstPerson = player == localPlayer
                && minecraft.options.getCameraType().isFirstPerson();

        long seed = player.getUUID().getMostSignificantBits()
                ^ Long.rotateLeft(player.getUUID().getLeastSignificantBits(), 23);

        // Many more, much smaller arcs than before. The first-person count is
        // lower so the view is not obscured.
        int arcCount = firstPerson ? 12 + level / 4 : 17 + level;
        float widthScale = BODY_WIDTH_SCALE * (0.92F + level * 0.012F);

        for (int arc = 0; arc < arcCount; arc++) {
            RandomSource random = RandomSource.create(seed + 0x9E3779B97F4A7C15L * (arc + 1L));
            double[] zone = ZONES[arc % ZONES.length];
            boolean longArc = arc % 7 == 0;

            double startAngle = zone[0]
                    + (random.nextDouble() - 0.5D) * 0.28D
                    + Math.sin(time * (0.10D + (arc % 5) * 0.015D) + arc * 0.73D) * 0.18D;
            double span = (longArc ? 2.4D : zone[4])
                    * (0.65D + random.nextDouble() * 0.55D)
                    * (random.nextBoolean() ? 1.0D : -1.0D);
            double startRadius = Math.max(0.14D, zone[1]
                    + (random.nextDouble() - 0.5D) * 0.10D);
            double startY = zone[2]
                    + (random.nextDouble() - 0.5D) * 0.22D
                    + Math.sin(time * 0.07D + arc * 1.31D) * 0.10D;

            double vertical = zone[3] + (random.nextDouble() - 0.5D) * 0.45D;
            double outward = random.nextDouble() < 0.35D
                    ? 0.10D + random.nextDouble() * 0.30D
                    : 0.0D;
            double bulge = 0.02D + random.nextDouble() * 0.07D;
            double phase = random.nextDouble() * Mth.TWO_PI;
            int segments = longArc ? 10 + random.nextInt(4) : 6 + random.nextInt(3);

            List<Vec3> points = buildArc(position, yaw, time, startAngle, span,
                    startRadius, startY, vertical, outward, bulge, phase, segments, random);

            FlashLightningRibbon.drawArc(consumer, matrix, cameraPos, points,
                    FlashLightningRibbon.BODY_GLOW_WIDTH * widthScale,
                    FlashLightningRibbon.BODY_MID_WIDTH * widthScale,
                    FlashLightningRibbon.BODY_CORE_WIDTH * widthScale,
                    1.0F);

            if (random.nextFloat() < 0.58F) {
                Vec3 branchStart = points.get(1 + random.nextInt(Math.max(1, segments - 2)));
                List<Vec3> branch = FlashLightningRibbon.buildBranch(
                        branchStart, random, time, phase);
                FlashLightningRibbon.drawArc(consumer, matrix, cameraPos, branch,
                        FlashLightningRibbon.BRANCH_GLOW_WIDTH * widthScale,
                        FlashLightningRibbon.BRANCH_MID_WIDTH * widthScale,
                        FlashLightningRibbon.BRANCH_CORE_WIDTH * widthScale,
                        1.0F);
            }
            if (longArc && random.nextFloat() < 0.30F) {
                Vec3 branchStart = points.get(2 + random.nextInt(Math.max(1, segments - 3)));
                List<Vec3> branch = FlashLightningRibbon.buildBranch(
                        branchStart, random, time, phase + 1.7D);
                FlashLightningRibbon.drawArc(consumer, matrix, cameraPos, branch,
                        FlashLightningRibbon.BRANCH_GLOW_WIDTH * widthScale,
                        FlashLightningRibbon.BRANCH_MID_WIDTH * widthScale,
                        FlashLightningRibbon.BRANCH_CORE_WIDTH * widthScale,
                        0.9F);
            }
        }

        // A pair of fast shoulder arcs in first person keeps energy visible on
        // both sides of the screen without covering the crosshair.
        if (firstPerson) {
            for (int side = -1; side <= 1; side += 2) {
                double angle = side * (0.72D + Math.sin(time * 0.22D) * 0.12D);
                List<Vec3> arc = buildArc(position, yaw, time,
                        angle, side * (0.75D + 0.25D * Math.sin(time * 0.15D)),
                        0.56D, 1.02D + Math.sin(time * 0.12D + side) * 0.18D,
                        0.18D, 0.10D, 0.04D, side * 1.3D, 7,
                        RandomSource.create(seed ^ side));
                FlashLightningRibbon.drawArc(consumer, matrix, cameraPos, arc,
                        FlashLightningRibbon.BODY_GLOW_WIDTH * widthScale,
                        FlashLightningRibbon.BODY_MID_WIDTH * widthScale,
                        FlashLightningRibbon.BODY_CORE_WIDTH * widthScale,
                        1.0F);
            }
        }
    }

    private static List<Vec3> buildArc(Vec3 position, float yaw, float time,
                                       double startAngle, double span, double startRadius,
                                       double startY, double vertical, double outward,
                                       double bulge, double phase, int segments,
                                       RandomSource random) {
        List<Vec3> points = new ArrayList<>(segments + 1);
        double yawRadians = yaw * Mth.DEG_TO_RAD;
        double[] wobble = new double[(segments + 1) * 2];
        for (int i = 0; i < wobble.length; i++) {
            wobble[i] = random.nextDouble() - 0.5D;
        }

        for (int i = 0; i <= segments; i++) {
            double t = i / (double) segments;
            double taper = Math.pow(Math.sin(Math.PI * t), 0.65D);
            // Faster flicker and flow than the previous version.
            double flicker = 0.55D + 0.45D * Math.sin(time * 3.3D + phase + i * 2.1D);
            double flow = Math.sin(time * 0.62D + phase + t * 6.0D) * 0.09D * t;
            double angle = startAngle + span * t + flow;
            double radius = startRadius
                    + Math.sin(Math.PI * t) * bulge
                    + outward * t * t
                    + Math.sin(time * 0.85D + phase + t * 3.4D) * 0.018D
                    + wobble[i * 2] * 0.12D * taper * flicker;
            double y = startY + vertical * t
                    + Math.sin(Math.PI * t) * 0.06D
                    + Math.sin(time * 0.78D + phase + t * 4.5D) * 0.028D
                    + wobble[i * 2 + 1] * 0.11D * taper * flicker;
            double worldAngle = yawRadians + angle;
            points.add(new Vec3(
                    position.x - Math.sin(worldAngle) * radius,
                    position.y + y,
                    position.z + Math.cos(worldAngle) * radius
            ));
        }
        return points;
    }
}
