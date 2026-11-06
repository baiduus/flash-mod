package com.flash.registry;

import com.flash.FlashMod;
import com.flash.entity.FlashSuitStand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEntities {

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, FlashMod.MODID);

    public static final RegistryObject<EntityType<FlashSuitStand>> FLASH_SUIT_STAND =
            ENTITY_TYPES.register("flash_suit_stand",
                    () -> EntityType.Builder.<FlashSuitStand>of(FlashSuitStand::new, MobCategory.MISC)
                            .sized(0.5F, 1.975F)
                            .clientTrackingRange(10)
                            .updateInterval(20)
                            .build("flash_suit_stand"));

    private ModEntities() {
    }
}
