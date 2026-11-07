package com.flash.superman;

import com.flash.ability.FlashAbility;
import net.minecraft.network.chat.Component;

/**
 * Superman abilities. Like the Hulk, they share the keys of the Flash abilities
 * ({@link #keySlot}); the charged punch uses V and the grab J (special keys).
 * A player only ever has one of the three powers.
 */
public enum SupermanAbility {

    FLIGHT("flight", 1, 0, 0.0F, Kind.TOGGLE, FlashAbility.WIND_HANDS, 0),
    SUPER_SPEED("super_speed", 1, 0, 0.0F, Kind.TOGGLE, FlashAbility.MASS_PUNCH, 0),
    HEAT_VISION("heat_vision", 2, 0, 0.3F, Kind.HOLD, FlashAbility.BARRAGE, 0),
    CHARGED_PUNCH("charged_punch", 3, 30, 3.0F, Kind.CHARGE, null, SupermanAbility.KEY_V),
    SUPER_BREATH("super_breath", 4, 0, 0.12F, Kind.HOLD, FlashAbility.LIGHTNING_STRIKE, 0),
    XRAY("xray", 5, 0, 0.0F, Kind.TOGGLE, FlashAbility.PHASING, 0),
    FREEZE_BREATH("freeze_breath", 6, 0, 0.15F, Kind.HOLD, FlashAbility.SPEED_TORNADO, 0),
    GRAB("grab", 7, 20, 0.0F, Kind.PRESS, null, SupermanAbility.KEY_J),
    THUNDERCLAP("thunderclap", 8, 200, 10.0F, Kind.PRESS, FlashAbility.TIME_SLOW, 0),
    TIME_SLOW("time_slow", 9, 0, 0.04F, Kind.TOGGLE, FlashAbility.DASH_PUNCH, 0),
    SUPER_COMBO("super_combo", 9, 400, 35.0F, Kind.PRESS, FlashAbility.SPEED_MANIPULATION, 0),
    SOLAR_ULTIMATE("solar_ultimate", 10, 1200, 80.0F, Kind.PRESS, FlashAbility.ACCELERATION_FORCE, 0);

    /** How the key works: one press, a switch, held down, or held to charge and released. */
    public enum Kind { PRESS, TOGGLE, HOLD, CHARGE }

    public static final int KEY_V = 1;
    public static final int KEY_J = 2;

    private final String id;
    private final int level;
    private final int cooldown;
    private final float energy;
    private final Kind kind;
    private final FlashAbility keySlot;
    private final int specialKey;

    SupermanAbility(String id, int level, int cooldown, float energy, Kind kind, FlashAbility keySlot, int specialKey) {
        this.id = id;
        this.level = level;
        this.cooldown = cooldown;
        this.energy = energy;
        this.kind = kind;
        this.keySlot = keySlot;
        this.specialKey = specialKey;
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

    /** Energy per use (press / charge) or per tick (hold / toggle). */
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

    public boolean perTick() {
        return this.kind == Kind.HOLD || this.kind == Kind.TOGGLE;
    }

    public Component displayName() {
        return Component.translatable("ability.flash.superman." + this.id);
    }

    public Component description() {
        return Component.translatable("ability.flash.superman." + this.id + ".description");
    }

    public static SupermanAbility byId(int id) {
        SupermanAbility[] values = values();
        return id >= 0 && id < values.length ? values[id] : null;
    }
}
