package com.flash.power;

import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraftforge.common.ForgeMod;

/**
 * Level-dependent automatic step height.
 *
 * <p>Forge exposes the synced {@link ForgeMod#STEP_HEIGHT_ADDITION} attribute,
 * which is added to vanilla's base step height on both client and server. This
 * is the safest way to let the Flash run over 1-3 block obstacles: it uses the
 * normal Minecraft collision/headroom checks instead of teleporting the player
 * onto the obstacle.</p>
 *
 * <p>Level 0 keeps vanilla movement. Levels 1-3 allow one block, 4-7 two blocks
 * and 8-10 three blocks. The value is based on the selected level, never on the
 * highest unlocked level.</p>
 */
public final class FlashStep {

    public static final UUID MODIFIER_UUID = UUID.fromString("5f4b7e9a-2c1d-4e8f-9a7b-3d6c5e4f2a10");
    private static final String MODIFIER_NAME = "flash_step_height";

    /** Vanilla player step height before the Forge attribute is added. */
    private static final double VANILLA_STEP = 0.6D;

    private FlashStep() {
    }

    public static double targetStepHeight(int level) {
        if (level <= 0) {
            return VANILLA_STEP;
        }
        if (level <= 3) {
            return 1.0D;
        }
        if (level <= 7) {
            return 2.0D;
        }
        return 3.0D;
    }

    public static void apply(ServerPlayer player) {
        AttributeInstance attribute = player.getAttribute(ForgeMod.STEP_HEIGHT_ADDITION.get());
        if (attribute == null) {
            return;
        }

        if (!FlashPowers.hasPowers(player)) {
            attribute.removeModifier(MODIFIER_UUID);
            return;
        }

        int level = FlashPowers.getSelectedLevel(player);
        if (level <= FlashSpeedLevels.MIN_LEVEL) {
            // Level 0 must behave exactly like vanilla: no step bonus at all.
            attribute.removeModifier(MODIFIER_UUID);
            return;
        }
        double amount = targetStepHeight(level) - VANILLA_STEP;

        AttributeModifier existing = attribute.getModifier(MODIFIER_UUID);
        if (existing != null
                && Math.abs(existing.getAmount() - amount) < 1.0E-6D
                && existing.getOperation() == AttributeModifier.Operation.ADDITION) {
            return;
        }
        if (existing != null) {
            attribute.removeModifier(MODIFIER_UUID);
        }
        attribute.addTransientModifier(new AttributeModifier(
                MODIFIER_UUID,
                MODIFIER_NAME,
                amount,
                AttributeModifier.Operation.ADDITION
        ));
    }

    public static void remove(ServerPlayer player) {
        AttributeInstance attribute = player.getAttribute(ForgeMod.STEP_HEIGHT_ADDITION.get());
        if (attribute != null) {
            attribute.removeModifier(MODIFIER_UUID);
        }
    }
}
