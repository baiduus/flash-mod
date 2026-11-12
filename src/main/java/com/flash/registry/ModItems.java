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

    public static final RegistryObject<Item> MJOLNIR = ITEMS.register("mjolnir", com.flash.item.MjolnirItem::new);
    public static final RegistryObject<Item> KRYPTONIAN_BABY = ITEMS.register("kryptonian_baby",
            com.flash.item.KryptonianBabyItem::new);
    public static final RegistryObject<Item> KRYPTONIAN_DEBRIS = ITEMS.register("kryptonian_debris",
            () -> new FlashTooltipItem(new Item.Properties(), "tooltip.flash.kryptonian_debris",
                    "tooltip.flash.kryptonian_debris.2"));

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

    /** Five of them lie in every crashed Kryptonian ship; the Processor turns five into Kryptonian blood. */
    public static final RegistryObject<Item> KRYPTONIAN_CELL = ITEMS.register("kryptonian_cell",
            () -> new FlashTooltipItem(new Item.Properties().stacksTo(16).rarity(net.minecraft.world.item.Rarity.RARE),
                    "tooltip.flash.kryptonian_cell", "tooltip.flash.kryptonian_cell.2"));

    /** The power capsules (made in the Processor or taken out of a hero with an empty syringe). */
    public static final RegistryObject<Item> KRYPTONIAN_BLOOD_CAPSULE = ITEMS.register("kryptonian_blood_capsule",
            () -> new FlashTooltipItem(new Item.Properties().stacksTo(16).rarity(net.minecraft.world.item.Rarity.EPIC),
                    "tooltip.flash.kryptonian_blood_capsule", "tooltip.flash.power_capsule"));
    public static final RegistryObject<Item> HULK_ESSENCE_CAPSULE = ITEMS.register("hulk_essence_capsule",
            () -> new FlashTooltipItem(new Item.Properties().stacksTo(16).rarity(net.minecraft.world.item.Rarity.EPIC),
                    "tooltip.flash.hulk_essence_capsule", "tooltip.flash.power_capsule"));
    public static final RegistryObject<Item> FLASH_ESSENCE_CAPSULE = ITEMS.register("flash_essence_capsule",
            () -> new FlashTooltipItem(new Item.Properties().stacksTo(16).rarity(net.minecraft.world.item.Rarity.EPIC),
                    "tooltip.flash.flash_essence_capsule", "tooltip.flash.power_capsule"));
    public static final RegistryObject<Item> THOR_ESSENCE_CAPSULE = ITEMS.register("thor_essence_capsule",
            () -> new FlashTooltipItem(new Item.Properties().stacksTo(16).rarity(net.minecraft.world.item.Rarity.EPIC),
                    "tooltip.flash.thor_essence_capsule", "tooltip.flash.power_capsule"));

    /** Lex Luthor's green and purple war suit. */
    public static final RegistryObject<Item> LEX_HELMET = ITEMS.register("lex_armor_helmet",
            () -> new com.flash.item.LexArmorItem(net.minecraft.world.item.ArmorItem.Type.HELMET));
    public static final RegistryObject<Item> LEX_CHESTPLATE = ITEMS.register("lex_armor_chestplate",
            () -> new com.flash.item.LexArmorItem(net.minecraft.world.item.ArmorItem.Type.CHESTPLATE));
    public static final RegistryObject<Item> LEX_LEGGINGS = ITEMS.register("lex_armor_leggings",
            () -> new com.flash.item.LexArmorItem(net.minecraft.world.item.ArmorItem.Type.LEGGINGS));
    public static final RegistryObject<Item> LEX_BOOTS = ITEMS.register("lex_armor_boots",
            () -> new com.flash.item.LexArmorItem(net.minecraft.world.item.ArmorItem.Type.BOOTS));
    public static final RegistryObject<Item> LEX_MISSILE = ITEMS.register("lex_missile",
            () -> new Item(new Item.Properties()));

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
