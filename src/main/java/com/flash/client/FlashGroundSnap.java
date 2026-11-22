package com.flash.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The speedster stays on the ground: running with the speed on, stepping off a block onto a lower one he is put
 * straight down onto it (up to 6 blocks) instead of flying off the edge, so the run follows the ground smoothly.
 * Not when jumping, flying, swimming, gliding or riding.
 */
final class FlashGroundSnap {

    private static final double MAX_DROP = 6.0D;
    private static boolean wasOnGround;

    private FlashGroundSnap() {
    }

    static void tick(Minecraft minecraft) {
        LocalPlayer p = minecraft.player;
        if (p == null) {
            wasOnGround = false;
            return;
        }
        boolean was = wasOnGround;
        wasOnGround = p.onGround();
        if (!was || p.onGround() || !FlashClientData.hasPowers() || FlashClientData.getSelectedLevel() <= 0
                || p.isSpectator() || p.getAbilities().flying || p.isPassenger() || p.isInWater()
                || p.isInLava() || p.onClimbable() || p.isFallFlying() || p.isShiftKeyDown()
                || minecraft.options.keyJump.isDown()) {
            return;
        }
        Vec3 v = p.getDeltaMovement();
        // only running off an edge: going fast, not on the way up
        if (v.y > 0.0D || v.x * v.x + v.z * v.z < 0.25D * 0.25D) {
            return;
        }
        AABB box = p.getBoundingBox();
        double d = 0.0D;
        double step = 0.25D;
        while (d < MAX_DROP && p.level().noCollision(p, box.move(0.0D, -(d + step), 0.0D))) {
            d += step;
        }
        if (d >= MAX_DROP) {
            return;
        }
        // finer, the last quarter block
        while (step > 0.01D) {
            step *= 0.5D;
            if (p.level().noCollision(p, box.move(0.0D, -(d + step), 0.0D))) {
                d += step;
            }
        }
        if (d < 0.02D) {
            return;
        }
        p.setPos(p.getX(), p.getY() - d, p.getZ());
        p.setDeltaMovement(v.x, 0.0D, v.z);
        p.setOnGround(true);
        p.fallDistance = 0.0F;
        wasOnGround = true;
    }
}
