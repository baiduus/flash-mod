package com.flash.ability;

import com.flash.power.FlashPowers;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

/**
 * Progressive Flash abilities. An ability is permanently unlocked when the
 * player's highest reached speed level is at least its required level, so
 * lowering the selected speed never removes an already learned ability.
 */
public enum FlashAbility {

    BARRAGE(1, "barrage", 160, false),
    WIND_HANDS(2, "wind_hands", 100, true),
    PHASING(3, "phasing", 0, true),
    SPEED_TORNADO(4, "speed_tornado", 240, true),
    LIGHTNING_STRIKE(5, "lightning_strike", 300, false),
    MASS_PUNCH(8, "mass_punch", 600, false),
    SPEED_MANIPULATION(9, "speed_manipulation", 1200, false),
    ACCELERATION_FORCE(10, "acceleration_force", 200, false);

    private final int requiredLevel;
    private final String id;
    private final int cooldownTicks;
    private final boolean hold;

    FlashAbility(int requiredLevel, String id, int cooldownTicks, boolean hold) {
        this.requiredLevel = requiredLevel;
        this.id = id;
        this.cooldownTicks = cooldownTicks;
        this.hold = hold;
    }

    public int requiredLevel() {
        return this.requiredLevel;
    }

    public String id() {
        return this.id;
    }

    public int cooldownTicks() {
        return this.cooldownTicks;
    }

    public int cooldownSeconds() {
        return Math.max(1, this.cooldownTicks / 20);
    }

    public boolean isHold() {
        return this.hold;
    }

    public boolean isUnlocked(Player player) {
        return FlashPowers.hasPowers(player)
                && FlashPowers.getMaxLevel(player) >= this.requiredLevel;
    }

    public Component displayName() {
        return Component.translatable("ability.flash." + this.id);
    }

    public Component description() {
        return Component.translatable("ability.flash." + this.id + ".description");
    }

    public String keyName() {
        return "key.flash.ability." + this.id;
    }

    public static FlashAbility byId(int id) {
        FlashAbility[] values = values();
        return id >= 0 && id < values.length ? values[id] : null;
    }
}
