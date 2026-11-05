package com.flash.registry;

import com.flash.FlashMod;
import com.flash.block.AccelerationPortalBlock;
import com.flash.block.PotionContainerBlock;
import com.flash.block.PotionContainerPartBlock;
import com.flash.block.TachyonExtractorBlock;
import com.flash.block.TransformationChamberBlock;
import com.flash.block.TransformationChamberPartBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlocks {

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, FlashMod.MODID);

    public static final RegistryObject<Block> TRANSFORMATION_CHAMBER = BLOCKS.register(
            "transformation_chamber",
            () -> new TransformationChamberBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(6.0F, 8.0F)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()
                    .lightLevel(state -> state.getValue(TransformationChamberBlock.ACTIVE) ? 15 : 10)));

    public static final RegistryObject<Block> POTION_CONTAINER = BLOCKS.register(
            "potion_container",
            () -> new PotionContainerBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(4.0F, 6.0F)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()
                    .lightLevel(state -> state.getValue(PotionContainerBlock.FILLED) ? 13 : 7)));

    public static final RegistryObject<Block> TRANSFORMATION_CHAMBER_PART = BLOCKS.register(
            "transformation_chamber_part",
            () -> new TransformationChamberPartBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(6.0F, 8.0F)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()
                    .noLootTable()
                    .lightLevel(state -> 10)));

    public static final RegistryObject<Block> POTION_CONTAINER_PART = BLOCKS.register(
            "potion_container_part",
            () -> new PotionContainerPartBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(4.0F, 6.0F)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()
                    .noLootTable()
                    .lightLevel(state -> 7)));

    public static final RegistryObject<Block> ACCELERATION_STONE = BLOCKS.register(
            "acceleration_stone",
            () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLACK)
                    .strength(8.0F, 12.0F)
                    .sound(SoundType.STONE)
                    .requiresCorrectToolForDrops()));

    public static final RegistryObject<Block> ACCELERATION_CRYSTAL = BLOCKS.register(
            "acceleration_crystal",
            () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.GOLD)
                    .strength(3.0F, 6.0F)
                    .sound(SoundType.GLASS)
                    .lightLevel(state -> 15)
                    .requiresCorrectToolForDrops()));

    public static final RegistryObject<Block> ACCELERATION_PORTAL = BLOCKS.register(
            "acceleration_portal",
            () -> new AccelerationPortalBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.GOLD)
                    .noCollission()
                    .noOcclusion()
                    .lightLevel(state -> 15)
                    .strength(-1.0F, 3600000.0F)
                    .sound(SoundType.GLASS)));

    public static final RegistryObject<Block> TACHYON_EXTRACTOR = BLOCKS.register(
            "tachyon_extractor",
            () -> new TachyonExtractorBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(4.0F, 6.0F)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()
                    .lightLevel(state -> state.getValue(TachyonExtractorBlock.POWERED) ? 10 : 0)));

    public static final RegistryObject<Block> NEGATIVE_STONE = BLOCKS.register(
            "negative_stone",
            () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.CRIMSON_NYLIUM)
                    .strength(8.0F, 12.0F)
                    .sound(SoundType.STONE)
                    .requiresCorrectToolForDrops()));

    public static final RegistryObject<Block> NEGATIVE_CRYSTAL = BLOCKS.register(
            "negative_crystal",
            () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_RED)
                    .strength(3.0F, 6.0F)
                    .sound(SoundType.GLASS)
                    .lightLevel(state -> 15)
                    .requiresCorrectToolForDrops()));

    private ModBlocks() {
    }
}
