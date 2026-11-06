package com.flash.hulk;

import com.flash.ability.FlashAbility;
import net.minecraft.network.chat.Component;

/**
 * Hulk abilities, one per rage level. They use the same keys as the Flash
 * abilities ({@link #keySlot} = the Flash ability whose key is shared), since a
 * player only ever has one of the two powers.
 */
public enum HulkAbility {

    GROUND_SMASH("ground_smash", 2, 160, FlashAbility.BARRAGE),
    CHARGE("charge", 3, 120, FlashAbility.WIND_HANDS),
    ROAR("roar", 4, 400, FlashAbility.PHASING),
    THUNDERCLAP("thunderclap", 5, 300, FlashAbility.SPEED_TORNADO),
    BOULDER("boulder", 6, 200, FlashAbility.LIGHTNING_STRIKE),
    FURY("fury", 7, 1200, FlashAbility.ACCELERATION_FORCE),
    GAMMA_WAVE("gamma_wave", 8, 600, FlashAbility.MASS_PUNCH),
    WORLD_BREAKER("world_breaker", 10, 900, FlashAbility.SPEED_MANIPULATION);

    private final String id;
    private final int level;
    private final int cooldown;
    private final FlashAbility keySlot;

    HulkAbility(String id, int level, int cooldown, FlashAbility keySlot) {
        this.id = id;
        this.level = level;
        this.cooldown = cooldown;
        this.keySlot = keySlot;
    }

    public String id() {
        return this.id;
    }

    public int level() {
        return this.level;
    }

    public int cooldown() {
        return this.cooldown;
    }

    public FlashAbility keySlot() {
        return this.keySlot;
    }

    public Component displayName() {
        return Component.translatable("ability.flash.hulk." + this.id);
    }

    public Component description() {
        return Component.translatable("ability.flash.hulk." + this.id + ".description");
    }

    public static HulkAbility byId(int id) {
        HulkAbility[] values = values();
        return id >= 0 && id < values.length ? values[id] : null;
    }
}
