package com.flash.power;

import java.util.UUID;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;

/**
 * Applies the currently selected Flash speed level as a temporary attribute
 * modifier. Level 0 simply removes the modifier, returning the player to
 * vanilla movement speed.
 */
public final class FlashSpeed {

    public static final UUID MODIFIER_UUID = UUID.fromString("7f9e2f6a-3c1b-4d8e-9a0f-5b6c7d8e9f01");
    private static final String MODIFIER_NAME = "flash_speed";

    private FlashSpeed() {
    }

    public static void apply(ServerPlayer player) {
        AttributeInstance attribute = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (attribute == null) {
            return;
        }

        if (!FlashPowers.hasPowers(player)) {
            attribute.removeModifier(MODIFIER_UUID);
            return;
        }

        int level = FlashPowers.getSelectedLevel(player);
        if (level <= FlashSpeedLevels.MIN_LEVEL) {
            attribute.removeModifier(MODIFIER_UUID);
            return;
        }

        double bonus = FlashSpeedLevels.attributeBonus(level);
        AttributeModifier existing = attribute.getModifier(MODIFIER_UUID);
        if (existing != null
                && Math.abs(existing.getAmount() - bonus) < 1.0E-6D
                && existing.getOperation() == AttributeModifier.Operation.MULTIPLY_TOTAL) {
            return;
        }
        if (existing != null) {
            attribute.removeModifier(MODIFIER_UUID);
        }
        attribute.addTransientModifier(new AttributeModifier(
                MODIFIER_UUID,
                MODIFIER_NAME,
                bonus,
                AttributeModifier.Operation.MULTIPLY_TOTAL
        ));
    }

    public static void remove(ServerPlayer player) {
        AttributeInstance attribute = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (attribute != null) {
            attribute.removeModifier(MODIFIER_UUID);
        }
    }

    /**
     * Client-safe query used by the HUD.
     */
    public static boolean hasModifier(Player player) {
        AttributeInstance attribute = player.getAttribute(Attributes.MOVEMENT_SPEED);
        return attribute != null && attribute.getModifier(MODIFIER_UUID) != null;
    }

    public static double speedBonus(Player player) {
        AttributeInstance attribute = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (attribute == null) {
            return 0.0D;
        }
        AttributeModifier modifier = attribute.getModifier(MODIFIER_UUID);
        return modifier == null ? 0.0D : modifier.getAmount();
    }
}
