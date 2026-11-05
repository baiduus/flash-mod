package com.flash.registry;

import com.flash.FlashMod;
import com.flash.item.PotionContainerItem;
import com.flash.item.TransformationChamberItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems {

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, FlashMod.MODID);

    public static final RegistryObject<Item> TRANSFORMATION_CHAMBER = ITEMS.register(
            "transformation_chamber",
            () -> new TransformationChamberItem(ModBlocks.TRANSFORMATION_CHAMBER.get(), new Item.Properties()));

    public static final RegistryObject<Item> POTION_CONTAINER = ITEMS.register(
            "potion_container",
            () -> new PotionContainerItem(ModBlocks.POTION_CONTAINER.get(), new Item.Properties()));

    public static final RegistryObject<Item> ACCELERATION_STONE = ITEMS.register(
            "acceleration_stone",
            () -> new net.minecraft.world.item.BlockItem(ModBlocks.ACCELERATION_STONE.get(),
                    new Item.Properties()));

    public static final RegistryObject<Item> ACCELERATION_CRYSTAL = ITEMS.register(
            "acceleration_crystal",
            () -> new net.minecraft.world.item.BlockItem(ModBlocks.ACCELERATION_CRYSTAL.get(),
                    new Item.Properties()));

    private ModItems() {
    }
}
