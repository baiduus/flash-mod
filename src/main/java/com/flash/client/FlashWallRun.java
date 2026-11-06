package com.flash.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Wall running: with the speed on, touching a wall and holding jump climbs it.
 * The climbing speed grows with the selected speed level.
 */
final class FlashWallRun {

    private FlashWallRun() {
    }

    static void tick(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.screen != null || !FlashClientData.hasPowers()
                || player.isSpectator() || player.getAbilities().flying
                || player.isPassenger() || player.isInWater() || player.isInLava()
                || player.onClimbable() || player.isFallFlying()) {
            return;
        }
        int level = FlashClientData.getSelectedLevel();
        if (level <= 0 || !minecraft.options.keyJump.isDown()) {
            return;
        }
        // A wall within a few centimetres of the body (the floor is excluded by shrinking the box).
        boolean touching = !player.level().noCollision(player,
                player.getBoundingBox().inflate(0.12D, -0.1D, 0.12D));
        if (!touching) {
            return;
        }
        double climb = 0.16D + 0.045D * level;
        Vec3 motion = player.getDeltaMovement();
        player.setDeltaMovement(motion.x, Math.max(motion.y, climb), motion.z);
        player.fallDistance = 0.0F;
    }
}
