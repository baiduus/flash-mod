package com.flash.infinity;

import com.flash.FlashMod;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterials;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * The blocks and items of the Infinity Stones (ported from Pugmeowla's Infinity Stone Core, with permission): the
 * metals and alloys, the planets' stones, the six stones, their containers, the Gauntlet. Generated from the
 * original's addon definitions (stack sizes, rarities, fire resistance, block hardness kept).
 */
public final class InfinityItems {

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, FlashMod.MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, FlashMod.MODID);

    private InfinityItems() {
    }

    private static Item.Properties props(int stack, Rarity rarity, boolean fire) {
        Item.Properties p = new Item.Properties().stacksTo(stack).rarity(rarity);
        return fire ? p.fireResistant() : p;
    }

    private static RegistryObject<Block> block(String name, MapColor color, SoundType sound, float destroy,
                                               float resist, boolean tool) {
        RegistryObject<Block> b = BLOCKS.register(name, () -> {
            BlockBehaviour.Properties p = BlockBehaviour.Properties.of().mapColor(color).sound(sound)
                    .strength(destroy, resist);
            return new Block(tool ? p.requiresCorrectToolForDrops() : p);
        });
        ITEMS.register(name, () -> new BlockItem(b.get(), new Item.Properties()));
        return b;
    }

    public static final RegistryObject<Block> DWARF_STAR_ALLOY_BLOCK = block("dwarf_star_alloy_block", MapColor.COLOR_BLACK, SoundType.NETHERITE_BLOCK, 1.5F, 6.0F, false);
    public static final RegistryObject<Block> DWARF_STAR_METEOR = block("dwarf_star_meteor", MapColor.COLOR_BLACK, SoundType.NETHERITE_BLOCK, 1.5F, 6.0F, false);
    public static final RegistryObject<Block> GOLD_TITANIUM_ALLOY_BLOCK = block("gold_titanium_alloy_block", MapColor.COLOR_YELLOW, SoundType.NETHERITE_BLOCK, 1.5F, 6.0F, false);
    public static final RegistryObject<Block> MORAG_STONE = block("morag_stone", MapColor.COLOR_BLACK, SoundType.STONE, 1.5F, 6.0F, false);
    public static final RegistryObject<Block> STEEL_BLOCK = block("steel_block", MapColor.COLOR_BLACK, SoundType.NETHERITE_BLOCK, 1.5F, 6.0F, false);
    public static final RegistryObject<Block> SVARTELHEIM_SAND = block("svartelheim_sand", MapColor.COLOR_PURPLE, SoundType.SAND, 0.75F, 1.0F, false);
    public static final RegistryObject<Block> SVARTELHEIM_STONE = block("svartelheim_stone", MapColor.COLOR_BLACK, SoundType.STONE, 1.5F, 6.0F, false);
    public static final RegistryObject<Block> TITANIUM_BLOCK = block("titanium_block", MapColor.COLOR_BLACK, SoundType.NETHERITE_BLOCK, 1.5F, 6.0F, false);
    public static final RegistryObject<Block> URU_BLOCK = block("uru_block", MapColor.COLOR_BLACK, SoundType.NETHERITE_BLOCK, 1.5F, 6.0F, false);
    public static final RegistryObject<Block> URU_ORE = block("uru_ore", MapColor.COLOR_BLACK, SoundType.STONE, 1.5F, 6.0F, false);
    public static final RegistryObject<Block> VORMIR_SAND = block("vormir_sand", MapColor.COLOR_PURPLE, SoundType.SAND, 0.75F, 1.0F, false);
    public static final RegistryObject<Block> VORMIR_STONE = block("vormir_stone", MapColor.COLOR_PURPLE, SoundType.STONE, 7.5F, 1.0F, true);

    public static final RegistryObject<Item> UNIT_52_D_UNIT = ITEMS.register("52-d_unit", () -> new Item(props(1, Rarity.COMMON, false)));
    public static final RegistryObject<Item> AETHER = ITEMS.register("aether", () -> new Item(props(1, Rarity.EPIC, true)));
    public static final RegistryObject<Item> ASPECTS_OF_EXISTANCE = ITEMS.register("aspects_of_existance", () -> new Item(props(64, Rarity.EPIC, true)));
    public static final RegistryObject<Item> BEST_FRIENDS_SOUL = ITEMS.register("best_friends_soul", () -> new Item(props(1, Rarity.EPIC, true)));
    public static final RegistryObject<Item> COAL_DUST = ITEMS.register("coal_dust", () -> new Item(props(64, Rarity.COMMON, false)));
    public static final RegistryObject<Item> DOUBLE_EDGED_SWORD = ITEMS.register("double_edged_sword", () -> new SwordItem(Tiers.NETHERITE, 7, -3.0F, props(1, Rarity.EPIC, true)));
    public static final RegistryObject<Item> DWARF_STAR_ALLOY_DUST = ITEMS.register("dwarf_star_alloy_dust", () -> new Item(props(64, Rarity.COMMON, false)));
    public static final RegistryObject<Item> DWARF_STAR_ALLOY_INGOT = ITEMS.register("dwarf_star_alloy_ingot", () -> new Item(props(64, Rarity.COMMON, true)));
    public static final RegistryObject<Item> DWARF_STAR_ALLOY_NUGGET = ITEMS.register("dwarf_star_alloy_nugget", () -> new Item(props(64, Rarity.COMMON, false)));
    public static final RegistryObject<Item> DWARF_STAR_ALLOY_PLATE = ITEMS.register("dwarf_star_alloy_plate", () -> new Item(props(64, Rarity.COMMON, true)));
    public static final RegistryObject<Item> EMPTY_AETHER = ITEMS.register("empty_aether", () -> new Item(props(1, Rarity.EPIC, true)));
    public static final RegistryObject<Item> EMPTY_EYE_OF_AGAMOTTO = ITEMS.register("empty_eye_of_agamotto", () -> new ArmorItem(ArmorMaterials.IRON, ArmorItem.Type.CHESTPLATE, props(64, Rarity.EPIC, true)));
    public static final RegistryObject<Item> EMPTY_SCEPTER = ITEMS.register("empty_scepter", () -> new SwordItem(Tiers.IRON, 3, -2.4F, props(1, Rarity.EPIC, true)));
    public static final RegistryObject<Item> EMPTY_TESSERACT = ITEMS.register("empty_tesseract", () -> new Item(props(1, Rarity.EPIC, true)));
    public static final RegistryObject<Item> ENDOSYM_INGOT = ITEMS.register("endosym_ingot", () -> new Item(props(64, Rarity.COMMON, true)));
    public static final RegistryObject<Item> EYE_OF_AGAMOTTO = ITEMS.register("eye_of_agamotto", () -> new ArmorItem(ArmorMaterials.IRON, ArmorItem.Type.CHESTPLATE, props(64, Rarity.EPIC, true)));
    public static final RegistryObject<Item> FANCY_BOTTLE = ITEMS.register("fancy_bottle", () -> new Item(props(1, Rarity.EPIC, true)));
    public static final RegistryObject<Item> FERRO_TITANIUM_GOLD_ALLOY_INGOT = ITEMS.register("ferro-titanium-gold_alloy_ingot", () -> new Item(props(64, Rarity.COMMON, false)));
    public static final RegistryObject<Item> GOLD_DUST = ITEMS.register("gold_dust", () -> new Item(props(64, Rarity.COMMON, false)));
    public static final RegistryObject<Item> GOLD_PLATE = ITEMS.register("gold_plate", () -> new Item(props(64, Rarity.COMMON, false)));
    public static final RegistryObject<Item> GOLD_TITANIUM_ALLOY_DUST = ITEMS.register("gold_titanium_alloy_dust", () -> new Item(props(64, Rarity.COMMON, false)));
    public static final RegistryObject<Item> GOLD_TITANIUM_ALLOY_HANDLE = ITEMS.register("gold_titanium_alloy_handle", () -> new Item(props(64, Rarity.COMMON, false)));
    public static final RegistryObject<Item> GOLD_TITANIUM_ALLOY_INGOT = ITEMS.register("gold_titanium_alloy_ingot", () -> new Item(props(64, Rarity.COMMON, false)));
    public static final RegistryObject<Item> GOLD_TITANIUM_ALLOY_NUGGET = ITEMS.register("gold_titanium_alloy_nugget", () -> new Item(props(64, Rarity.COMMON, false)));
    public static final RegistryObject<Item> GOLD_TITANIUM_ALLOY_PLATE = ITEMS.register("gold_titanium_alloy_plate", () -> new Item(props(64, Rarity.COMMON, false)));
    public static final RegistryObject<Item> HAMMER = ITEMS.register("hammer", () -> new Item(props(64, Rarity.COMMON, false)));
    public static final RegistryObject<Item> INFINITY_GAUNTLET_CAST = ITEMS.register("infinity_gauntlet_cast", () -> new Item(props(1, Rarity.RARE, true)));
    public static final RegistryObject<Item> INJECTION = ITEMS.register("injection", () -> new Item(props(1, Rarity.COMMON, false)));
    public static final RegistryObject<Item> IRON_DUST = ITEMS.register("iron_dust", () -> new Item(props(64, Rarity.COMMON, false)));
    public static final RegistryObject<Item> IRON_PLATE = ITEMS.register("iron_plate", () -> new Item(props(64, Rarity.COMMON, false)));
    public static final RegistryObject<Item> MIND_STONE = ITEMS.register("mind_stone", () -> new InfinityStoneItem(InfinityStone.MIND, props(1, Rarity.EPIC, true)));
    public static final RegistryObject<Item> ORB = ITEMS.register("orb", () -> new Item(props(1, Rarity.EPIC, true)));
    public static final RegistryObject<Item> PORTAL = ITEMS.register("portal", () -> new Item(props(1, Rarity.EPIC, true)));
    public static final RegistryObject<Item> POWER_STONE = ITEMS.register("power_stone", () -> new InfinityStoneItem(InfinityStone.POWER, props(1, Rarity.EPIC, true)));
    public static final RegistryObject<Item> PUGMEOWLA = ITEMS.register("pugmeowla", () -> new Item(props(1, Rarity.EPIC, true)));
    public static final RegistryObject<Item> RAW_URU = ITEMS.register("raw_uru", () -> new Item(props(64, Rarity.COMMON, false)));
    public static final RegistryObject<Item> REALITY_STONE = ITEMS.register("reality_stone", () -> new InfinityStoneItem(InfinityStone.REALITY, props(1, Rarity.EPIC, true)));
    public static final RegistryObject<Item> SCEPTER = ITEMS.register("scepter", () -> new SwordItem(Tiers.IRON, 3, -2.4F, props(1, Rarity.EPIC, true)));
    public static final RegistryObject<Item> SHARDS_OF_EXISTANCE = ITEMS.register("shards_of_existance", () -> new Item(props(64, Rarity.EPIC, true)));
    public static final RegistryObject<Item> SINGLE_EDGED_SWORD = ITEMS.register("single_edged_sword", () -> new SwordItem(Tiers.NETHERITE, 5, -2.0F, props(1, Rarity.EPIC, true)));
    public static final RegistryObject<Item> SOUL_GUARDIAN_TELEPHONE = ITEMS.register("soul_guardian_telephone", () -> new Item(props(1, Rarity.EPIC, true)));
    public static final RegistryObject<Item> SOUL_INFUSED_NETHERITE_INGOT = ITEMS.register("soul_infused_netherite_ingot", () -> new Item(props(64, Rarity.COMMON, true)));
    public static final RegistryObject<Item> SOUL_STONE = ITEMS.register("soul_stone", () -> new InfinityStoneItem(InfinityStone.SOUL, props(1, Rarity.EPIC, true)));
    public static final RegistryObject<Item> SPACE_STONE = ITEMS.register("space_stone", () -> new InfinityStoneItem(InfinityStone.SPACE, props(1, Rarity.EPIC, true)));
    public static final RegistryObject<Item> STEEL_DUST = ITEMS.register("steel_dust", () -> new Item(props(64, Rarity.COMMON, false)));
    public static final RegistryObject<Item> STEEL_INGOT = ITEMS.register("steel_ingot", () -> new Item(props(64, Rarity.COMMON, false)));
    public static final RegistryObject<Item> STEEL_NUGGET = ITEMS.register("steel_nugget", () -> new Item(props(64, Rarity.COMMON, false)));
    public static final RegistryObject<Item> STEEL_PLATE = ITEMS.register("steel_plate", () -> new Item(props(64, Rarity.COMMON, false)));
    public static final RegistryObject<Item> TESSERACT = ITEMS.register("tesseract", () -> new Item(props(1, Rarity.EPIC, true)));
    public static final RegistryObject<Item> TIME_STONE = ITEMS.register("time_stone", () -> new InfinityStoneItem(InfinityStone.TIME, props(1, Rarity.EPIC, true)));
    public static final RegistryObject<Item> TITANIUM_DUST = ITEMS.register("titanium_dust", () -> new Item(props(64, Rarity.COMMON, false)));
    public static final RegistryObject<Item> TITANIUM_INGOT = ITEMS.register("titanium_ingot", () -> new Item(props(64, Rarity.COMMON, false)));
    public static final RegistryObject<Item> TITANIUM_NUGGET = ITEMS.register("titanium_nugget", () -> new Item(props(64, Rarity.COMMON, false)));
    public static final RegistryObject<Item> TITANIUM_PLATE = ITEMS.register("titanium_plate", () -> new Item(props(64, Rarity.COMMON, false)));
    public static final RegistryObject<Item> URU_BLADE = ITEMS.register("uru_blade", () -> new Item(props(64, Rarity.EPIC, false)));
    public static final RegistryObject<Item> URU_DUST = ITEMS.register("uru_dust", () -> new Item(props(64, Rarity.COMMON, false)));
    public static final RegistryObject<Item> URU_INGOT = ITEMS.register("uru_ingot", () -> new Item(props(64, Rarity.COMMON, false)));
    public static final RegistryObject<Item> URU_NUGGET = ITEMS.register("uru_nugget", () -> new Item(props(64, Rarity.COMMON, false)));
    public static final RegistryObject<Item> URU_PLATE = ITEMS.register("uru_plate", () -> new Item(props(64, Rarity.COMMON, false)));
    /** The Cosmi-Rod (the Universal Weapon). */
    public static final RegistryObject<Item> COSMI_ROD = ITEMS.register("cosmi-rod", () -> new Item(props(1,
            Rarity.EPIC, true)));
    /** The Infinity Gauntlet (its stones, the Snap: InfinityGauntlet). */
    public static final RegistryObject<Item> INFINITY_GAUNTLET = ITEMS.register("infinity_gauntlet", () -> new InfinityGauntletItem(props(1, Rarity.EPIC, true)));

    /** The original's creative tabs, in its order. */
    public static final java.util.List<java.util.function.Supplier<Item>> TAB_STONES = java.util.List.of(
            POWER_STONE::get,
            SPACE_STONE::get,
            REALITY_STONE::get,
            SOUL_STONE::get,
            TIME_STONE::get,
            MIND_STONE::get,
            ORB::get,
            TESSERACT::get,
            AETHER::get,
            BEST_FRIENDS_SOUL::get,
            EYE_OF_AGAMOTTO::get,
            SCEPTER::get,
            INFINITY_GAUNTLET::get,
            INFINITY_GAUNTLET_CAST::get,
            COSMI_ROD::get,
            ASPECTS_OF_EXISTANCE::get,
            SHARDS_OF_EXISTANCE::get,
            DOUBLE_EDGED_SWORD::get,
            SINGLE_EDGED_SWORD::get,
            FANCY_BOTTLE::get,
            SOUL_GUARDIAN_TELEPHONE::get,
            EMPTY_TESSERACT::get,
            EMPTY_SCEPTER::get,
            EMPTY_EYE_OF_AGAMOTTO::get,
            EMPTY_AETHER::get,
            PORTAL::get,
            PUGMEOWLA::get);
    public static final java.util.List<java.util.function.Supplier<Item>> TAB_RESOURCES = java.util.List.of(
            HAMMER::get,
            INJECTION::get,
            COAL_DUST::get,
            () -> DWARF_STAR_METEOR.get().asItem(),
            DWARF_STAR_ALLOY_DUST::get,
            DWARF_STAR_ALLOY_INGOT::get,
            DWARF_STAR_ALLOY_NUGGET::get,
            DWARF_STAR_ALLOY_PLATE::get,
            () -> DWARF_STAR_ALLOY_BLOCK.get().asItem(),
            GOLD_DUST::get,
            GOLD_PLATE::get,
            GOLD_TITANIUM_ALLOY_DUST::get,
            GOLD_TITANIUM_ALLOY_HANDLE::get,
            GOLD_TITANIUM_ALLOY_INGOT::get,
            GOLD_TITANIUM_ALLOY_NUGGET::get,
            GOLD_TITANIUM_ALLOY_PLATE::get,
            () -> GOLD_TITANIUM_ALLOY_BLOCK.get().asItem(),
            IRON_DUST::get,
            IRON_PLATE::get,
            STEEL_DUST::get,
            STEEL_INGOT::get,
            STEEL_NUGGET::get,
            STEEL_PLATE::get,
            () -> STEEL_BLOCK.get().asItem(),
            TITANIUM_DUST::get,
            TITANIUM_INGOT::get,
            TITANIUM_NUGGET::get,
            TITANIUM_PLATE::get,
            () -> TITANIUM_BLOCK.get().asItem(),
            () -> URU_ORE.get().asItem(),
            RAW_URU::get,
            URU_BLADE::get,
            URU_DUST::get,
            URU_INGOT::get,
            URU_NUGGET::get,
            URU_PLATE::get,
            () -> URU_BLOCK.get().asItem(),
            () -> MORAG_STONE.get().asItem(),
            () -> SVARTELHEIM_SAND.get().asItem(),
            () -> SVARTELHEIM_STONE.get().asItem(),
            () -> VORMIR_SAND.get().asItem(),
            () -> VORMIR_STONE.get().asItem(),
            UNIT_52_D_UNIT::get,
            ENDOSYM_INGOT::get,
            FERRO_TITANIUM_GOLD_ALLOY_INGOT::get,
            SOUL_INFUSED_NETHERITE_INGOT::get);
}
