package com.flash.registry;

import com.flash.FlashMod;
import com.flash.block.PotionContainerBlockEntity;
import com.flash.block.TransformationChamberBlockEntity;
import com.flash.block.AccelerationPortalBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlockEntities {

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, FlashMod.MODID);

    public static final RegistryObject<BlockEntityType<TransformationChamberBlockEntity>> TRANSFORMATION_CHAMBER =
            BLOCK_ENTITIES.register("transformation_chamber", () ->
                    BlockEntityType.Builder.of(
                            TransformationChamberBlockEntity::new,
                            ModBlocks.TRANSFORMATION_CHAMBER.get()
                    ).build(null));

    public static final RegistryObject<BlockEntityType<PotionContainerBlockEntity>> POTION_CONTAINER =
            BLOCK_ENTITIES.register("potion_container", () ->
                    BlockEntityType.Builder.of(
                            PotionContainerBlockEntity::new,
                            ModBlocks.POTION_CONTAINER.get()
                    ).build(null));

    public static final RegistryObject<BlockEntityType<AccelerationPortalBlockEntity>> ACCELERATION_PORTAL =
            BLOCK_ENTITIES.register("acceleration_portal", () ->
                    BlockEntityType.Builder.of(
                            AccelerationPortalBlockEntity::new,
                            ModBlocks.ACCELERATION_PORTAL.get()
                    ).build(null));

    private ModBlockEntities() {
    }
}
