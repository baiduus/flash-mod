package com.flash.client;

import com.flash.ability.FlashAbility;
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
 * Ribbon-based visuals for the hand tornadoes (Wind Hands) and the punch
 * barrage. Everything is drawn with the same glowing ribbon as the lightning,
 * so it matches the rest of the mod, and it turns red for the Negative Force.
 */
@Mod.EventBusSubscriber(modid = com.flash.FlashMod.MODID, value = Dist.CLIENT)
public final class FlashAbilityEffectsRenderer {

    private static final double MAX_DISTANCE_SQR = 48.0D * 48.0D;

    /** True while drawing a player under the V-9 (render thread only). */
    private static boolean v9;

    // Wind palette: pale cyan-white (positive) / hot white-red (negative).
    private static final float[] WIND_GLOW = {0.45F, 0.80F, 1.0F, 0.16F};
    private static final float[] WIND_MID = {0.75F, 0.93F, 1.0F, 0.38F};
    private static final float[] WIND_CORE = {1.0F, 1.0F, 1.0F, 0.90F};

    private FlashAbilityEffectsRenderer() {
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
        int wind = 1 << FlashAbility.WIND_HANDS.ordinal();
        int barrage = 1 << FlashAbility.BARRAGE.ordinal();

        List<Player> players = new ArrayList<>();
        for (Player player : level.players()) {
            if (!player.isAlive() || player.isSpectator()
                    || (FlashClientVisualLevels.activeMask(player) & (wind | barrage)) == 0
                    || player.distanceToSqr(localPlayer) > MAX_DISTANCE_SQR) {
                continue;
            }
            if (event.getFrustum().isVisible(player.getBoundingBoxForCulling().inflate(5.0D))) {
                players.add(player);
            }
        }
        if (players.isEmpty()) {
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
        for (Player player : players) {
            int mask = FlashClientVisualLevels.activeMask(player);
            boolean negative = FlashClientVisualLevels.isNegative(player);
            float time = player.tickCount + partialTick;
            // V-9: every effect of this player turns blue, like the lightning
            v9 = FlashClientVisualLevels.isV9(player);
            FlashLightningRibbon.blue = v9;
            try {
                if ((mask & wind) != 0) {
                    renderWind(consumer, matrix, cameraPos, player, partialTick, time, negative);
                }
                if ((mask & barrage) != 0) {
                    renderBarrage(consumer, matrix, cameraPos, player, partialTick, time, negative);
                }
            } finally {
                FlashLightningRibbon.blue = false;
                v9 = false;
            }
        }
        buffers.endBatch(FlashLightningRibbon.RENDER_TYPE);
        // the light of the twisters and of the fists (FlashVfx)
        var buf = VfxDraw.begin();
        try {
            for (Player player : players) {
                int mask = FlashClientVisualLevels.activeMask(player);
                float[] c = FlashVfx.color(FlashClientVisualLevels.isNegative(player), FlashClientVisualLevels.isV9(player));
                float time = player.tickCount + partialTick;
                Vec3 look = player.getViewVector(partialTick).normalize();
                Vec3 right = rightOf(look);
                for (int side = -1; side <= 1; side += 2) {
                    Vec3 h = hand(player, partialTick, look, right, side);
                    if ((mask & wind) != 0) {
                        FlashVfx.twister(buf, matrix, h, look, time, c);
                    }
                    if ((mask & barrage) != 0) {
                        double jab = Math.max(0.0D, Math.sin(time * 2.6D + (side > 0 ? 0.0D : Math.PI)));
                        FlashVfx.fist(buf, matrix, h.add(look.scale(0.4D + jab * 0.9D)), event.getCamera(), time, c);
                    }
                }
            }
        } finally {
            VfxDraw.end(buf);
        }
        poseStack.popPose();
    }

    private static Vec3 hand(Player player, float partialTick, Vec3 look, Vec3 right, double side) {
        return player.getEyePosition(partialTick)
                .add(0.0D, -0.52D, 0.0D)
                .add(right.scale(0.42D * side))
                .add(look.scale(0.35D));
    }

    private static Vec3 rightOf(Vec3 look) {
        Vec3 right = look.cross(new Vec3(0.0D, 1.0D, 0.0D));
        if (right.lengthSqr() < 1.0E-6D) {
            right = new Vec3(1.0D, 0.0D, 0.0D);
        }
        return right.normalize();
    }

    // ------------------------------------------------------------------
    // Wind Hands: two spinning twisters opening out of the hands
    // ------------------------------------------------------------------

    private static void renderWind(VertexConsumer consumer, Matrix4f matrix, Vec3 cameraPos,
                                   Player player, float partialTick, float time, boolean negative) {
        Vec3 look = player.getViewVector(partialTick).normalize();
        Vec3 right = rightOf(look);
        Vec3 up = right.cross(look).normalize();
        float[] glow = v9 ? FlashLightningRibbon.BLUE_GLOW_COLOR : negative ? FlashLightningRibbon.RED_GLOW_COLOR : WIND_GLOW;
        float[] mid = v9 ? FlashLightningRibbon.BLUE_MID_COLOR : negative ? FlashLightningRibbon.RED_MID_COLOR : WIND_MID;
        float[] core = v9 ? FlashLightningRibbon.BLUE_CORE_COLOR : negative ? FlashLightningRibbon.RED_CORE_COLOR : WIND_CORE;
        float fade = 0.55F + 0.45F * Mth.sin(time * 0.6F) * 0.5F + 0.2F;

        for (int side = -1; side <= 1; side += 2) {
            Vec3 origin = hand(player, partialTick, look, right, side);
            // Dense funnel: many interleaved spiral lines with different twist,
            // radius and length so it reads as a thick mass of wind.
            for (int strand = 0; strand < 16; strand++) {
                int segments = 30;
                List<Vec3> points = new ArrayList<>(segments + 1);
                double twist = 9.0D + (strand % 4) * 2.2D;
                double spread = 0.70D + (strand % 5) * 0.14D;
                double length = 4.8D + (strand % 3) * 1.1D + (strand % 2) * 0.8D;
                double phase = time * (0.50D + (strand % 3) * 0.11D) * side
                        + strand * (Mth.TWO_PI / 16.0D) * 1.7D;
                for (int i = 0; i <= segments; i++) {
                    double t = i / (double) segments;
                    double distance = 0.2D + t * length;
                    double radius = 0.04D + t * t * spread + t * 0.15D;
                    double angle = phase + t * twist * side;
                    points.add(origin
                            .add(look.scale(distance))
                            .add(right.scale(Math.cos(angle) * radius))
                            .add(up.scale(Math.sin(angle) * radius)));
                }
                float w = 0.55F + (strand % 4) * 0.12F;
                FlashLightningRibbon.drawArc(consumer, matrix, cameraPos, points,
                        0.050F * w, 0.022F * w, 0.008F * w, glow, mid, core, fade);
            }

            // Fine electric sparks riding the funnel.
            RandomSource random = RandomSource.create(
                    player.getUUID().getLeastSignificantBits() + side * 31L + (long) (time * 0.5F) * 7919L);
            for (int k = 0; k < 4; k++) {
                double t0 = 0.2D + random.nextDouble() * 0.6D;
                double angle = random.nextDouble() * Mth.TWO_PI;
                double r = 0.1D + t0 * t0 * 1.0D;
                Vec3 start = origin.add(look.scale(0.25D + t0 * 7.0D))
                        .add(right.scale(Math.cos(angle) * r)).add(up.scale(Math.sin(angle) * r));
                List<Vec3> branch = FlashLightningRibbon.buildBranch(start, random, time, k);
                FlashLightningRibbon.drawBolt(consumer, matrix, cameraPos, branch, 0.006F, 0.9F, negative);
            }
        }
    }

    // ------------------------------------------------------------------
    // Barrage: a storm of punch streaks and impact stars
    // ------------------------------------------------------------------

    private static void renderBarrage(VertexConsumer consumer, Matrix4f matrix, Vec3 cameraPos,
                                      Player player, float partialTick, float time, boolean negative) {
        Vec3 look = player.getViewVector(partialTick).normalize();
        Vec3 right = rightOf(look);
        Vec3 up = right.cross(look).normalize();
        long frame = (long) time;
        RandomSource random = RandomSource.create(
                player.getUUID().getMostSignificantBits() ^ (frame * 0x9E3779B97F4A7C15L));

        // Fast fist streaks: straight bright lines shooting from both hands.
        for (int i = 0; i < 9; i++) {
            double side = (i & 1) == 0 ? 1.0D : -1.0D;
            Vec3 origin = hand(player, partialTick, look, right, side);
            double spreadX = (random.nextDouble() - 0.5D) * 0.55D;
            double spreadY = (random.nextDouble() - 0.5D) * 0.55D;
            double length = 1.6D + random.nextDouble() * 1.5D;
            Vec3 end = origin.add(look.scale(length))
                    .add(right.scale(spreadX)).add(up.scale(spreadY));
            List<Vec3> points = new ArrayList<>();
            int segments = 4;
            for (int s = 0; s <= segments; s++) {
                double t = s / (double) segments;
                Vec3 p = origin.lerp(end, t);
                if (s != 0 && s != segments) {
                    p = p.add(right.scale((random.nextDouble() - 0.5D) * 0.08D))
                            .add(up.scale((random.nextDouble() - 0.5D) * 0.08D));
                }
                points.add(p);
            }
            FlashLightningRibbon.drawBolt(consumer, matrix, cameraPos, points,
                    0.010F, 1.0F, negative);
        }

        // Impact star + expanding shock ring in front of the fists.
        double reach = 2.3D;
        Vec3 centre = player.getEyePosition(partialTick).add(0.0D, -0.4D, 0.0D).add(look.scale(reach));
        for (int i = 0; i < 10; i++) {
            double angle = random.nextDouble() * Mth.TWO_PI;
            double length = 0.25D + random.nextDouble() * 0.55D;
            Vec3 dir = right.scale(Math.cos(angle)).add(up.scale(Math.sin(angle)));
            List<Vec3> ray = new ArrayList<>();
            ray.add(centre.add(dir.scale(0.08D)));
            ray.add(centre.add(dir.scale(length * 0.5D)).add(look.scale((random.nextDouble() - 0.5D) * 0.1D)));
            ray.add(centre.add(dir.scale(length)));
            FlashLightningRibbon.drawBolt(consumer, matrix, cameraPos, ray, 0.008F, 0.95F, negative);
        }
        float ringPhase = (time * 0.5F) % 1.0F;
        double ringRadius = 0.2D + ringPhase * 0.9D;
        int ringSegments = 20;
        List<Vec3> ring = new ArrayList<>(ringSegments + 1);
        for (int i = 0; i <= ringSegments; i++) {
            double angle = i * (Mth.TWO_PI / ringSegments);
            ring.add(centre.add(right.scale(Math.cos(angle) * ringRadius))
                    .add(up.scale(Math.sin(angle) * ringRadius)));
        }
        float[] glow = v9 ? FlashLightningRibbon.BLUE_GLOW_COLOR : negative ? FlashLightningRibbon.RED_GLOW_COLOR : FlashLightningRibbon.GLOW_COLOR;
        float[] mid = v9 ? FlashLightningRibbon.BLUE_MID_COLOR : negative ? FlashLightningRibbon.RED_MID_COLOR : FlashLightningRibbon.MID_COLOR;
        float[] core = v9 ? FlashLightningRibbon.BLUE_CORE_COLOR : negative ? FlashLightningRibbon.RED_CORE_COLOR : FlashLightningRibbon.CORE_COLOR;
        FlashLightningRibbon.drawArc(consumer, matrix, cameraPos, ring,
                0.05F, 0.022F, 0.008F, glow, mid, core, 1.0F - ringPhase);
    }
}
