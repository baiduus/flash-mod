package com.flash.infinity;

import com.flash.FlashMod;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** The original's sounds (sounds.json "infinity.*"). */
public final class InfinitySounds {

    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS,
            FlashMod.MODID);
    private static final Map<String, RegistryObject<SoundEvent>> BY_NAME = new HashMap<>();
    private static final String[] NAMES = {"space_stone_portal", "space_stone_grab", "space_stone_random",
            "infinity_stone_equip", "use_infinity_gauntlet", "mind_stone_phase", "nano_gauntlet", "power_stone_beam1",
            "energy_blast", "power_stone_explode", "reality_stone_bubble", "reality_stone_invis", "reality_stone_size",
            "reality_stone", "soul_stone", "space_stone_forcefield", "stone_remove", "time_stone", "np52"};

    static {
        for (String n : NAMES) {
            String id = "infinity." + n;
            BY_NAME.put(n, SOUNDS.register(id, () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(
                    FlashMod.MODID, id))));
        }
    }

    private InfinitySounds() {
    }

    public static SoundEvent get(String name) {
        RegistryObject<SoundEvent> r = BY_NAME.get(name);
        return r == null ? null : r.get();
    }
}
