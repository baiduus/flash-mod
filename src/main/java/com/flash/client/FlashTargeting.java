package com.flash.client;

import com.flash.FlashMod;
import com.flash.block.PotionContainerBlock;
import com.flash.block.TransformationChamberBlock;
import com.flash.network.FlashMachineInteractPacket;
import com.flash.network.FlashNetwork;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Client-side interaction fix for the large single-block machine.
 *
 * <p>Minecraft only evaluates the shape of the block whose cell the look ray
 * actually enters. The chamber model extends about three blocks around its
 * controller cell, so a shape that covers the whole model is never considered
 * when the player aims at a decorative column or wall outside the controller
 * cell. This handler raycasts a generous footprint of nearby chambers and
 * replaces the vanilla hit result with a hit on the controller. A matching
 * right-click is sent through a tiny validated packet, because the vanilla
 * reach test uses the controller cell and would reject a click made at the
 * front centre of the machine.</p>
 */
@Mod.EventBusSubscriber(modid = FlashMod.MODID, value = Dist.CLIENT)
public final class FlashTargeting {

    private static final int SCAN_XZ = 6;
    private static final int SCAN_Y = 4;
    private static final double MACHINE_HALF_WIDTH = 3.05D;
    private static final double MACHINE_HEIGHT = 3.72D;

    private static BlockPos targetedMachine;

    private FlashTargeting() {
    }

    @SubscribeEvent
    public static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null
                || minecraft.screen != null || minecraft.options.hideGui) {
            return;
        }

        Vec3 start = event.getCamera().getPosition();
        Vec3 look = minecraft.player.getViewVector(event.getPartialTick());
        double reach = Math.max(4.5D, minecraft.player.getAttributeValue(
                net.minecraftforge.common.ForgeMod.BLOCK_REACH.get()));
        Vec3 end = start.add(look.scale(reach));

        BlockHitResult best = null;
        BlockPos bestPos = null;
        double bestDistance = Double.MAX_VALUE;
        BlockPos center = minecraft.player.blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(
                center.offset(-SCAN_XZ, -SCAN_Y, -SCAN_XZ),
                center.offset(SCAN_XZ, SCAN_Y, SCAN_XZ))) {
            BlockState state = minecraft.level.getBlockState(pos);
            if (!(state.getBlock() instanceof TransformationChamberBlock)
                    && !(state.getBlock() instanceof PotionContainerBlock)) {
                continue;
            }

            AABB box;
            if (state.getBlock() instanceof TransformationChamberBlock) {
                box = new AABB(pos.getX() - MACHINE_HALF_WIDTH, pos.getY(),
                        pos.getZ() - MACHINE_HALF_WIDTH,
                        pos.getX() + MACHINE_HALF_WIDTH, pos.getY() + MACHINE_HEIGHT,
                        pos.getZ() + MACHINE_HALF_WIDTH);
            } else {
                box = state.getShape(minecraft.level, pos).bounds().move(pos);
            }

            Optional<Vec3> clip = box.clip(start, end);
            if (clip.isEmpty() && box.contains(start)) {
                Vec3 controllerCenter = new Vec3(pos.getX() + 0.5D,
                        pos.getY() + 0.5D, pos.getZ() + 0.5D);
                if (controllerCenter.subtract(start).normalize().dot(look) > 0.0D) {
                    clip = Optional.of(controllerCenter);
                }
            }
            if (clip.isEmpty()) {
                continue;
            }

            double distance = clip.get().distanceToSqr(start);
            if (distance >= bestDistance) {
                continue;
            }
            bestDistance = distance;
            bestPos = pos.immutable();
            Vec3 point = clip.get();
            net.minecraft.core.Direction direction = net.minecraft.core.Direction.getNearest(
                    point.x - (pos.getX() + 0.5D),
                    point.y - (pos.getY() + 0.5D),
                    point.z - (pos.getZ() + 0.5D));
            best = new BlockHitResult(point, direction, bestPos, false);
        }

        if (best == null) {
            targetedMachine = null;
            return;
        }

        HitResult vanilla = minecraft.hitResult;
        if (vanilla == null || vanilla.getType() == HitResult.Type.MISS
                || vanilla.getLocation().distanceToSqr(start) > bestDistance) {
            minecraft.hitResult = best;
            targetedMachine = bestPos;
        } else {
            targetedMachine = null;
        }
    }

    /**
     * Intercepts a right-click aimed at the custom machine hit and sends the
     * dedicated interaction packet. The server validates the distance and
     * performs the same enter/open-screen action as the block itself.
     */
    @SubscribeEvent
    public static void onInteraction(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isUseItem() || targetedMachine == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.screen != null
                || minecraft.player.isShiftKeyDown()) {
            return;
        }
        BlockPos target = targetedMachine;
        InteractionHand hand = event.getHand();
        event.setCanceled(true);
        FlashNetwork.CHANNEL.sendToServer(
                new FlashMachineInteractPacket(target, hand.ordinal()));
        minecraft.player.swing(hand);
    }
}
