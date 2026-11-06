package com.flash.registry;

import com.flash.FlashMod;
import com.flash.item.FlashTooltipItem;
import com.flash.item.PotionContainerItem;
import com.flash.item.SyringeItem;
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

    public static final RegistryObject<Item> TACHYON_EXTRACTOR = ITEMS.register(
            "tachyon_extractor",
            () -> new net.minecraft.world.item.BlockItem(ModBlocks.TACHYON_EXTRACTOR.get(),
                    new Item.Properties()));

    public static final RegistryObject<Item> NEGATIVE_STONE = ITEMS.register(
            "negative_stone",
            () -> new net.minecraft.world.item.BlockItem(ModBlocks.NEGATIVE_STONE.get(),
                    new Item.Properties()));

    public static final RegistryObject<Item> NEGATIVE_CRYSTAL = ITEMS.register(
            "negative_crystal",
            () -> new net.minecraft.world.item.BlockItem(ModBlocks.NEGATIVE_CRYSTAL.get(),
                    new Item.Properties()));

    public static final RegistryObject<Item> TORN_SUIT = ITEMS.register(
            "torn_suit",
            () -> new FlashTooltipItem(new Item.Properties().stacksTo(1),
                    "tooltip.flash.torn_suit", "tooltip.flash.torn_suit.2"));

    public static final RegistryObject<Item> TACHYON = ITEMS.register(
            "tachyon",
            () -> new FlashTooltipItem(new Item.Properties(), "tooltip.flash.tachyon"));

    public static final RegistryObject<Item> CORRUPTED_MATTER = ITEMS.register(
            "corrupted_matter",
            () -> new FlashTooltipItem(new Item.Properties(), "tooltip.flash.corrupted_matter"));

    public static final RegistryObject<Item> NEGATIVE_CAPSULE = ITEMS.register(
            "negative_capsule",
            () -> new FlashTooltipItem(new Item.Properties().stacksTo(16),
                    "tooltip.flash.negative_capsule"));

    public static final RegistryObject<Item> V9_CAPSULE = ITEMS.register(
            "v9_capsule",
            () -> new FlashTooltipItem(new Item.Properties().stacksTo(16),
                    "tooltip.flash.v9_capsule"));

    public static final RegistryObject<Item> SYRINGE = ITEMS.register(
            "syringe",
            () -> new SyringeItem(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> FLASH_SUIT_HELMET = ITEMS.register(
            "flash_suit_helmet",
            () -> new com.flash.item.FlashSuitItem(com.flash.item.FlashSuitMaterial.POSITIVE,
                    net.minecraft.world.item.ArmorItem.Type.HELMET));

    public static final RegistryObject<Item> FLASH_SUIT_CHESTPLATE = ITEMS.register(
            "flash_suit_chestplate",
            () -> new com.flash.item.FlashSuitItem(com.flash.item.FlashSuitMaterial.POSITIVE,
                    net.minecraft.world.item.ArmorItem.Type.CHESTPLATE));

    public static final RegistryObject<Item> FLASH_SUIT_LEGGINGS = ITEMS.register(
            "flash_suit_leggings",
            () -> new com.flash.item.FlashSuitItem(com.flash.item.FlashSuitMaterial.POSITIVE,
                    net.minecraft.world.item.ArmorItem.Type.LEGGINGS));

    public static final RegistryObject<Item> FLASH_SUIT_BOOTS = ITEMS.register(
            "flash_suit_boots",
            () -> new com.flash.item.FlashSuitItem(com.flash.item.FlashSuitMaterial.POSITIVE,
                    net.minecraft.world.item.ArmorItem.Type.BOOTS));

    public static final RegistryObject<Item> NEGATIVE_SUIT_HELMET = ITEMS.register(
            "negative_suit_helmet",
            () -> new com.flash.item.FlashSuitItem(com.flash.item.FlashSuitMaterial.NEGATIVE,
                    net.minecraft.world.item.ArmorItem.Type.HELMET));

    public static final RegistryObject<Item> NEGATIVE_SUIT_CHESTPLATE = ITEMS.register(
            "negative_suit_chestplate",
            () -> new com.flash.item.FlashSuitItem(com.flash.item.FlashSuitMaterial.NEGATIVE,
                    net.minecraft.world.item.ArmorItem.Type.CHESTPLATE));

    public static final RegistryObject<Item> NEGATIVE_SUIT_LEGGINGS = ITEMS.register(
            "negative_suit_leggings",
            () -> new com.flash.item.FlashSuitItem(com.flash.item.FlashSuitMaterial.NEGATIVE,
                    net.minecraft.world.item.ArmorItem.Type.LEGGINGS));

    public static final RegistryObject<Item> NEGATIVE_SUIT_BOOTS = ITEMS.register(
            "negative_suit_boots",
            () -> new com.flash.item.FlashSuitItem(com.flash.item.FlashSuitMaterial.NEGATIVE,
                    net.minecraft.world.item.ArmorItem.Type.BOOTS));

    public static final RegistryObject<Item> ZOOM_SUIT_HELMET = ITEMS.register(
            "zoom_suit_helmet",
            () -> new com.flash.item.FlashSuitItem(com.flash.item.FlashSuitMaterial.ZOOM,
                    net.minecraft.world.item.ArmorItem.Type.HELMET));

    public static final RegistryObject<Item> ZOOM_SUIT_CHESTPLATE = ITEMS.register(
            "zoom_suit_chestplate",
            () -> new com.flash.item.FlashSuitItem(com.flash.item.FlashSuitMaterial.ZOOM,
                    net.minecraft.world.item.ArmorItem.Type.CHESTPLATE));

    public static final RegistryObject<Item> ZOOM_SUIT_LEGGINGS = ITEMS.register(
            "zoom_suit_leggings",
            () -> new com.flash.item.FlashSuitItem(com.flash.item.FlashSuitMaterial.ZOOM,
                    net.minecraft.world.item.ArmorItem.Type.LEGGINGS));

    public static final RegistryObject<Item> ZOOM_SUIT_BOOTS = ITEMS.register(
            "zoom_suit_boots",
            () -> new com.flash.item.FlashSuitItem(com.flash.item.FlashSuitMaterial.ZOOM,
                    net.minecraft.world.item.ArmorItem.Type.BOOTS));

    public static final RegistryObject<Item> PHANTOM_FRAGMENT = ITEMS.register(
            "phantom_fragment",
            () -> new FlashTooltipItem(new Item.Properties().fireResistant(), "tooltip.flash.phantom_fragment"));

    public static final RegistryObject<Item> ACCELERATION_PHANTOM_SPAWN_EGG = ITEMS.register(
            "acceleration_phantom_spawn_egg",
            () -> new net.minecraftforge.common.ForgeSpawnEggItem(ModEntities.ACCELERATION_PHANTOM,
                    0x121214, 0xC8141C, new Item.Properties()));

    public static final RegistryObject<Item> PHANTOM_SUIT_HELMET = ITEMS.register(
            "phantom_suit_helmet",
            () -> new com.flash.item.FlashSuitItem(com.flash.item.FlashSuitMaterial.PHANTOM,
                    net.minecraft.world.item.ArmorItem.Type.HELMET));

    public static final RegistryObject<Item> PHANTOM_SUIT_CHESTPLATE = ITEMS.register(
            "phantom_suit_chestplate",
            () -> new com.flash.item.FlashSuitItem(com.flash.item.FlashSuitMaterial.PHANTOM,
                    net.minecraft.world.item.ArmorItem.Type.CHESTPLATE));

    public static final RegistryObject<Item> PHANTOM_SUIT_LEGGINGS = ITEMS.register(
            "phantom_suit_leggings",
            () -> new com.flash.item.FlashSuitItem(com.flash.item.FlashSuitMaterial.PHANTOM,
                    net.minecraft.world.item.ArmorItem.Type.LEGGINGS));

    public static final RegistryObject<Item> PHANTOM_SUIT_BOOTS = ITEMS.register(
            "phantom_suit_boots",
            () -> new com.flash.item.FlashSuitItem(com.flash.item.FlashSuitMaterial.PHANTOM,
                    net.minecraft.world.item.ArmorItem.Type.BOOTS));

    public static final RegistryObject<Item> GAMMA_CHAMBER = ITEMS.register(
            "gamma_chamber",
            () -> new net.minecraft.world.item.BlockItem(ModBlocks.GAMMA_CHAMBER.get(),
                    new Item.Properties()));

    public static final RegistryObject<Item> GAMMA_REACTOR = ITEMS.register(
            "gamma_reactor",
            () -> new net.minecraft.world.item.BlockItem(ModBlocks.GAMMA_REACTOR.get(),
                    new Item.Properties()));

    public static final RegistryObject<Item> GAMMA_CAPSULE = ITEMS.register(
            "gamma_capsule",
            () -> new FlashTooltipItem(new Item.Properties().stacksTo(16),
                    "tooltip.flash.gamma_capsule"));

    private ModItems() {
    }

    public static final RegistryObject<Item> FLASH_RING = ITEMS.register(
            "flash_ring", com.flash.item.FlashRingItem::new);

    public static final RegistryObject<Item> TACHYON_AMPLIFIER = ITEMS.register(
            "tachyon_amplifier", com.flash.item.FlashAmplifierItem::new);

    public static final RegistryObject<Item> FLASH_SUIT_STAND = ITEMS.register(
            "flash_suit_stand", com.flash.item.FlashSuitStandItem::new);
}
