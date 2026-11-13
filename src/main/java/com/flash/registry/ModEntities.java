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

    public static final RegistryObject<EntityType<com.flash.entity.LexMissile>> LEX_MISSILE =
            ENTITY_TYPES.register("lex_missile",
                    () -> EntityType.Builder.<com.flash.entity.LexMissile>of(
                                    com.flash.entity.LexMissile::new, MobCategory.MISC)
                            .sized(0.3F, 0.3F)
                            .clientTrackingRange(8)
                            .updateInterval(1)
                            .build("lex_missile"));

    public static final RegistryObject<EntityType<com.flash.yas.YasGuardian>> YAS_GUARDIAN =
            ENTITY_TYPES.register("yas_guardian",
                    () -> EntityType.Builder.<com.flash.yas.YasGuardian>of(com.flash.yas.YasGuardian::new,
                                    MobCategory.MISC)
                            .sized(0.6F, 1.95F)
                            .fireImmune()
                            .clientTrackingRange(10)
                            .build("yas_guardian"));

    public static final RegistryObject<EntityType<com.flash.entity.MjolnirEntity>> MJOLNIR =
            ENTITY_TYPES.register("mjolnir",
                    () -> EntityType.Builder.<com.flash.entity.MjolnirEntity>of(
                                    com.flash.entity.MjolnirEntity::new, MobCategory.MISC)
                            .sized(0.5F, 0.5F)
                            .clientTrackingRange(10)
                            .updateInterval(1)
                            .build("mjolnir"));

    public static final RegistryObject<EntityType<com.flash.entity.HumanNpc>> HUMAN =
            ENTITY_TYPES.register("human",
                    () -> EntityType.Builder.<com.flash.entity.HumanNpc>of(com.flash.entity.HumanNpc::new,
                                    MobCategory.CREATURE)
                            .sized(0.6F, 1.8F)
                            .clientTrackingRange(10)
                            .build("human"));

    public static final RegistryObject<EntityType<com.flash.entity.BlackHole>> BLACK_HOLE =
            ENTITY_TYPES.register("black_hole",
                    () -> EntityType.Builder.<com.flash.entity.BlackHole>of(com.flash.entity.BlackHole::new,
                                    MobCategory.MISC)
                            .sized(1.0F, 1.0F)
                            .fireImmune()
                            .clientTrackingRange(16)
                            .updateInterval(2)
                            .build("black_hole"));

    public static final RegistryObject<EntityType<com.flash.entity.ChaosEnergy>> CHAOS_ENERGY =
            ENTITY_TYPES.register("chaos_energy",
                    () -> EntityType.Builder.<com.flash.entity.ChaosEnergy>of(com.flash.entity.ChaosEnergy::new,
                                    MobCategory.MISC)
                            .sized(0.8F, 0.8F)
                            .fireImmune()
                            .clientTrackingRange(8)
                            .build("chaos_energy"));

    private ModEntities() {
    }
}
