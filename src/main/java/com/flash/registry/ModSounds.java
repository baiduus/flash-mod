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

    private static RegistryObject<SoundEvent> sound(String name) {
        String id = "superman." + name;
        return SOUNDS.register(id, () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(FlashMod.MODID, id)));
    }
}
