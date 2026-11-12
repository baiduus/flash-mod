package com.flash.lex;

import com.flash.ability.FlashAbility;
import net.minecraft.network.chat.Component;

/**
 * The systems of Lex Luthor's war suit, the ones of Iron Man's Mark 6 in Legends: Superheroes: repulsor blast and
 * beam, unibeam, the wrist lasers, the homing micro missiles, power thrust and power dive, armour overdrive and the
 * faceplate. They use the ability keys of the heroes (the suit is for a player without powers). Energy is a
 * percentage of the battery; cooldowns are in ticks.
 */
public enum LexAbility {
    REPULSOR_BLAST("repulsor_blast", Kind.PRESS, FlashAbility.BARRAGE, 0.6F, 12),
    REPULSOR_BEAM("repulsor_beam", Kind.HOLD, FlashAbility.PHASING, 0.04F, 0),
    UNIBEAM("unibeam", Kind.HOLD, FlashAbility.SPEED_TORNADO, 0.12F, 200),
    LASERS("lasers", Kind.HOLD, FlashAbility.WIND_HANDS, 2.0F, 160),
    MISSILES("missiles", Kind.PRESS, FlashAbility.MASS_PUNCH, 3.0F, 100),
    POWER_THRUST("power_thrust", Kind.PRESS, FlashAbility.LIGHTNING_STRIKE, 1.0F, 40),
    POWER_DIVE("power_dive", Kind.PRESS, FlashAbility.DASH_PUNCH, 1.5F, 100),
    OVERDRIVE("overdrive", Kind.PRESS, FlashAbility.ACCELERATION_FORCE, 5.0F, 600),
    FACEPLATE("faceplate", Kind.PRESS, FlashAbility.TIME_SLOW, 0.0F, 10);

    public enum Kind { PRESS, HOLD }

    private final String id;
    private final Kind kind;
    private final FlashAbility key;
    private final float energy;
    private final int cooldown;

    LexAbility(String id, Kind kind, FlashAbility key, float energy, int cooldown) {
        this.id = id;
        this.kind = kind;
        this.key = key;
        this.energy = energy;
        this.cooldown = cooldown;
    }

    public String id() {
        return this.id;
    }

    public Kind kind() {
        return this.kind;
    }

    /** The hero ability whose key fires this system. */
    public FlashAbility keySlot() {
        return this.key;
    }

    public float energy() {
        return this.energy;
    }

    public int cooldown() {
        return this.cooldown;
    }

    public Component displayName() {
        return Component.translatable("ability.flash.lex." + this.id);
    }

    public Component description() {
        return Component.translatable("ability.flash.lex." + this.id + ".description");
    }
}
