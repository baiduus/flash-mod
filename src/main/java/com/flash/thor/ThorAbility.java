package com.flash.thor;

import com.flash.ability.FlashAbility;
import net.minecraft.network.chat.Component;

/**
 * Thor abilities (the Mjolnir set of Legends: Superheroes). Like the Hulk and Superman they share the keys of the
 * Flash abilities ({@link #keySlot}); Overcharge uses V and Hammer Spark J. Unlocked by level.
 */
public enum ThorAbility {

    FLIGHT("flight", 1, 0, 0.0F, Kind.TOGGLE, FlashAbility.WIND_HANDS, 0, true),
    SUMMON("summon", 1, 20, 0.0F, Kind.PRESS, FlashAbility.MASS_PUNCH, 0, false),
    LIGHTNING_BLAST("lightning_blast", 2, 0, 0.35F, Kind.HOLD, FlashAbility.BARRAGE, 0, false),
    HAMMER_SPIN("hammer_spin", 3, 0, 0.2F, Kind.HOLD, FlashAbility.LIGHTNING_STRIKE, 0, true),
    LIGHTNING_STRIKE("lightning_strike", 4, 160, 12.0F, Kind.PRESS, FlashAbility.PHASING, 0, false),
    STORM_STRIKER("storm_striker", 5, 200, 15.0F, Kind.PRESS, FlashAbility.SPEED_TORNADO, 0, true),
    HAMMER_SPARK("hammer_spark", 6, 240, 18.0F, Kind.PRESS, null, ThorAbility.KEY_J, true),
    LIGHTNING_VORTEX("lightning_vortex", 7, 200, 15.0F, Kind.PRESS, FlashAbility.TIME_SLOW, 0, true),
    WARRIORS_FURY("warriors_fury", 8, 1200, 30.0F, Kind.PRESS, FlashAbility.DASH_PUNCH, 0, false),
    MIGHTY_TORNADO("mighty_tornado", 9, 600, 30.0F, Kind.PRESS, FlashAbility.SPEED_MANIPULATION, 0, false),
    OVERCHARGE("overcharge", 9, 900, 0.0F, Kind.PRESS, null, ThorAbility.KEY_V, true),
    GOD_OF_THUNDER("god_of_thunder", 10, 1800, 70.0F, Kind.PRESS, FlashAbility.ACCELERATION_FORCE, 0, true);

    public enum Kind { PRESS, TOGGLE, HOLD }

    public static final int KEY_V = 1;
    public static final int KEY_J = 2;

    private final String id;
    private final int level;
    private final int cooldown;
    private final float energy;
    private final Kind kind;
    private final FlashAbility keySlot;
    private final int specialKey;
    private final boolean needsHammer;

    ThorAbility(String id, int level, int cooldown, float energy, Kind kind, FlashAbility keySlot, int specialKey,
                boolean needsHammer) {
        this.id = id;
        this.level = level;
        this.cooldown = cooldown;
        this.energy = energy;
        this.kind = kind;
        this.keySlot = keySlot;
        this.specialKey = specialKey;
        this.needsHammer = needsHammer;
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

    /** Energy per use (press) or per tick (hold / toggle). */
    public float energy() {
        return this.energy;
    }

    public Kind kind() {
        return this.kind;
    }

    public FlashAbility keySlot() {
        return this.keySlot;
    }

    public int specialKey() {
        return this.specialKey;
    }

    /** Only with the Mjolnir in hand. */
    public boolean needsHammer() {
        return this.needsHammer;
    }

    public Component displayName() {
        return Component.translatable("ability.flash.thor." + this.id);
    }

    public Component description() {
        return Component.translatable("ability.flash.thor." + this.id + ".description");
    }

    public static ThorAbility byId(int id) {
        ThorAbility[] values = values();
        return id >= 0 && id < values.length ? values[id] : null;
    }
}
