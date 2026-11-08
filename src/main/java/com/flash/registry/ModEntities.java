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

    public static final RegistryObject<EntityType<com.flash.entity.AccelerationPhantom>> ACCELERATION_PHANTOM =
            ENTITY_TYPES.register("acceleration_phantom",
                    () -> EntityType.Builder.<com.flash.entity.AccelerationPhantom>of(
                                    com.flash.entity.AccelerationPhantom::new, MobCategory.MONSTER)
                            .sized(0.6F, 1.95F)
                            .fireImmune()
                            .clientTrackingRange(10)
                            .updateInterval(1) // it is very fast: send its position every tick
                            .build("acceleration_phantom"));

    public static final RegistryObject<EntityType<com.flash.entity.HulkBoulder>> HULK_BOULDER =
            ENTITY_TYPES.register("hulk_boulder",
                    () -> EntityType.Builder.<com.flash.entity.HulkBoulder>of(
                                    com.flash.entity.HulkBoulder::new, MobCategory.MISC)
                            .sized(0.6F, 0.6F)
                            .clientTrackingRange(6)
                            .updateInterval(5)
                            .build("hulk_boulder"));

    public static final RegistryObject<EntityType<com.flash.entity.MjolnirEntity>> MJOLNIR =
            ENTITY_TYPES.register("mjolnir",
                    () -> EntityType.Builder.<com.flash.entity.MjolnirEntity>of(
                                    com.flash.entity.MjolnirEntity::new, MobCategory.MISC)
                            .sized(0.5F, 0.5F)
                            .clientTrackingRange(10)
                            .updateInterval(1)
                            .build("mjolnir"));

    private ModEntities() {
    }
}
