package com.flash.godwand;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

/** Vanilla sounds whichever way 1.20.1 holds them, and God Wand's own (assets/flash/sounds/godwand). */
public final class GodWandSounds {

    private GodWandSounds() {
    }

    public static SoundEvent ev(SoundEvent e) {
        return e;
    }

    public static SoundEvent ev(Holder<SoundEvent> e) {
        return e.value();
    }

    /** One of God Wand's sounds ({@code fuga_charge}, {@code atomic_blast}...), as named in sounds.json. */
    public static ResourceLocation id(String name) {
        return new ResourceLocation(com.flash.FlashMod.MODID, "gw." + name);
    }

    public static SoundEvent own(String name) {
        return SoundEvent.createVariableRangeEvent(id(name));
    }
}
