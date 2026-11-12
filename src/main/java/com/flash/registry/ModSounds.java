package com.flash.registry;

import com.flash.FlashMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Sounds of the Superman pack (assets/flash/sounds/superman). */
public final class ModSounds {

    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, FlashMod.MODID);

    public static final RegistryObject<SoundEvent> HEAT_START = sound("heat_start");
    public static final RegistryObject<SoundEvent> HEAT_LOOP = sound("heat_loop");
    public static final RegistryObject<SoundEvent> HEAT_BLAST_LOOP = sound("heat_blast_loop");
    public static final RegistryObject<SoundEvent> COLD_BREATH_LOOP = sound("cold_breath_loop");
    public static final RegistryObject<SoundEvent> SUPER_BREATH_LOOP = sound("super_breath_loop");
    public static final RegistryObject<SoundEvent> TAKEOFF = sound("takeoff");
    public static final RegistryObject<SoundEvent> LANDING = sound("landing");
    public static final RegistryObject<SoundEvent> DIVE = sound("dive");
    public static final RegistryObject<SoundEvent> CHARGE = sound("charge");
    public static final RegistryObject<SoundEvent> PUNCH_SONIC = sound("punch_sonic");
    public static final RegistryObject<SoundEvent> SOLAR_CHARGE = sound("solar_charge");
    public static final RegistryObject<SoundEvent> SOLAR_ROAR = sound("solar_roar");
    public static final RegistryObject<SoundEvent> SOLAR_BEAM = sound("solar_beam");
    public static final RegistryObject<SoundEvent> CLARK_REVEAL = sound("clark_reveal");
    public static final RegistryObject<SoundEvent> CLARK_SPIN = sound("clark_spin");
    public static final RegistryObject<SoundEvent> CLARK_RIP = sound("clark_rip");

    private ModSounds() {
    }

    public static final RegistryObject<SoundEvent> THOR_SPIN = thor("mjolnir_spin");
    public static final RegistryObject<SoundEvent> THOR_SPIN_ATTACK = thor("mjolnir_spin_attack");
    public static final RegistryObject<SoundEvent> THOR_BLOCK = thor("mjolnir_block");
    public static final RegistryObject<SoundEvent> THOR_HIT = thor("mjolnir_hit");
    public static final RegistryObject<SoundEvent> THOR_THROW = thor("mjolnir_thrown");
    public static final RegistryObject<SoundEvent> THOR_HUM = thor("mjolnir_hum");
    public static final RegistryObject<SoundEvent> THOR_GRAB = thor("mjolnir_grab");
    public static final RegistryObject<SoundEvent> THOR_THUMP = thor("mjolnir_thump");
    public static final RegistryObject<SoundEvent> THOR_LIGHTNING = thor("mjolnir_lightning_strike");
    public static final RegistryObject<SoundEvent> THOR_STORM_STRIKER = thor("mjolnir_storm_striker");
    public static final RegistryObject<SoundEvent> THOR_OVERCHARGE = thor("overcharge");
    public static final RegistryObject<SoundEvent> THOR_OVERCHARGE_START = thor("overcharge_start");
    public static final RegistryObject<SoundEvent> THOR_OVERCHARGE_LOOP = thor("overcharge_loop");
    public static final RegistryObject<SoundEvent> THOR_OVERCHARGE_END = thor("overcharge_end");
    public static final RegistryObject<SoundEvent> THOR_GOD_BLAST = thor("god_blast");
    /** The Lightning Blast (the Shazam blast of Man of Tomorrow): start, held loop, the zap of each pulse. */
    /** Lex Luthor's war suit (sounds of Man of Tomorrow's LexCorp mech and armours). */
    public static final RegistryObject<SoundEvent> LEX_MISSILE_LAUNCH = lex("missile_launch");
    public static final RegistryObject<SoundEvent> LEX_MISSILE_FLY = lex("missile_fly");
    public static final RegistryObject<SoundEvent> LEX_MISSILE_BOOM = lex("missile_boom");
    public static final RegistryObject<SoundEvent> LEX_LOW_ENERGY = lex("low_energy");
    public static final RegistryObject<SoundEvent> LEX_BEAM_LOOP = lex("beam_loop");
    public static final RegistryObject<SoundEvent> LEX_EQUIP = lex("equip");
    public static final RegistryObject<SoundEvent> LEX_EMPTY = lex("empty");
    /** The Iron Man sounds of Legends: Superheroes (the Mark 6 systems of the suit). */
    public static final RegistryObject<SoundEvent> LEX_REPULSOR_BLAST = lex("repulsor_blast");
    public static final RegistryObject<SoundEvent> LEX_REPULSOR_BEAM = lex("repulsor_beam");
    public static final RegistryObject<SoundEvent> LEX_REPULSOR_CONTINUOUS = lex("repulsor_continuous");
    public static final RegistryObject<SoundEvent> LEX_REPULSOR_CHARGE = lex("repulsor_charge");
    public static final RegistryObject<SoundEvent> LEX_REPULSOR_IMPACT = lex("repulsor_impact");
    public static final RegistryObject<SoundEvent> LEX_UNIBEAM_CHARGE = lex("unibeam_charge");
    public static final RegistryObject<SoundEvent> LEX_UNIBEAM_BLAST = lex("unibeam_blast");
    public static final RegistryObject<SoundEvent> LEX_UNIBEAM_POWERDOWN = lex("unibeam_powerdown");
    public static final RegistryObject<SoundEvent> LEX_LASER = lex("laser");
    public static final RegistryObject<SoundEvent> LEX_MISSILE = lex("missile");
    public static final RegistryObject<SoundEvent> LEX_MICROMISSILES = lex("micromissiles");
    public static final RegistryObject<SoundEvent> LEX_OVERDRIVE = lex("overdrive");
    public static final RegistryObject<SoundEvent> LEX_OVERDRIVE_OFF = lex("overdrive_off");
    public static final RegistryObject<SoundEvent> LEX_HELMET_OPEN = lex("helmet_open");
    public static final RegistryObject<SoundEvent> LEX_HELMET_CLOSE = lex("helmet_close");
    public static final RegistryObject<SoundEvent> LEX_POWERDOWN = lex("powerdown");
    public static final RegistryObject<SoundEvent> LEX_RECHARGE = lex("recharge");
    public static final RegistryObject<SoundEvent> LEX_THRUST_START = lex("thrust_start");
    public static final RegistryObject<SoundEvent> LEX_THRUST_LOOP = lex("thrust_loop");
    public static final RegistryObject<SoundEvent> LEX_THRUST_END = lex("thrust_end");

    public static final RegistryObject<SoundEvent> THOR_BLAST_START = thor("blast_start");
    public static final RegistryObject<SoundEvent> THOR_BLAST_LOOP = thor("blast_loop");
    public static final RegistryObject<SoundEvent> THOR_BLAST_ZAP = thor("blast_zap");
    public static final RegistryObject<SoundEvent> THOR_SB_HIT = thor("stormbreaker_hit");
    public static final RegistryObject<SoundEvent> THOR_SB_THROW = thor("stormbreaker_thrown");
    public static final RegistryObject<SoundEvent> THOR_SB_THUMP = thor("stormbreaker_thump");
    public static final RegistryObject<SoundEvent> THOR_BIFROST_ARRIVE = thor("bifrost_arrive");
    public static final RegistryObject<SoundEvent> THOR_BIFROST_AWAY = thor("bifrost_away");
    public static final java.util.List<RegistryObject<SoundEvent>> THOR_LINES = java.util.stream.IntStream
            .rangeClosed(1, 15).mapToObj(i -> thor("thor_line" + i)).toList();

    private static RegistryObject<SoundEvent> lex(String name) {
        String id = "lex." + name;
        return SOUNDS.register(id, () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(FlashMod.MODID, id)));
    }

    private static RegistryObject<SoundEvent> thor(String name) {
        String id = "thor." + name;
        return SOUNDS.register(id, () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(FlashMod.MODID, id)));
    }

    private static RegistryObject<SoundEvent> sound(String name) {
        String id = "superman." + name;
        return SOUNDS.register(id, () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(FlashMod.MODID, id)));
    }
}
