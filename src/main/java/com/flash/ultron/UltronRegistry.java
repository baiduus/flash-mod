package com.flash.ultron;

import com.flash.FlashMod;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Ultron's things: the nanocore, the surgery table and the sentries of his Legion. */
@Mod.EventBusSubscriber(modid = FlashMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class UltronRegistry {

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, FlashMod.MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, FlashMod.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(
            ForgeRegistries.BLOCK_ENTITY_TYPES, FlashMod.MODID);
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(
            ForgeRegistries.ENTITY_TYPES, FlashMod.MODID);

    /** Ultron's nanocore: whoever uses it becomes Ultron (for now only by command). */
    public static final RegistryObject<Item> NANOCORE = ITEMS.register("ultron_nanocore",
            () -> new UltronNanocoreItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant()));

    public static final RegistryObject<Block> SURGERY_TABLE = BLOCKS.register("surgery_table",
            () -> new SurgeryTableBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(4.0F, 8.0F)
                    .sound(SoundType.NETHERITE_BLOCK).noOcclusion().requiresCorrectToolForDrops()));
    public static final RegistryObject<Item> SURGERY_TABLE_ITEM = ITEMS.register("surgery_table",
            () -> new BlockItem(SURGERY_TABLE.get(), new Item.Properties().stacksTo(1)));

    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<SurgeryTableBlockEntity>> SURGERY_TABLE_BE =
            BLOCK_ENTITIES.register("surgery_table", () -> BlockEntityType.Builder.of(SurgeryTableBlockEntity::new,
                    SURGERY_TABLE.get()).build(null));

    public static final RegistryObject<EntityType<UltronSentry>> SENTRY = ENTITIES.register("ultron_sentry",
            () -> EntityType.Builder.<UltronSentry>of(UltronSentry::new, MobCategory.MISC).sized(0.6F, 1.6F)
                    .clientTrackingRange(10).fireImmune().build("ultron_sentry"));

    private UltronRegistry() {
    }

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        BLOCK_ENTITIES.register(bus);
        ENTITIES.register(bus);
    }

    @SubscribeEvent
    public static void onAttributes(net.minecraftforge.event.entity.EntityAttributeCreationEvent event) {
        event.put(SENTRY.get(), UltronSentry.attributes().build());
    }
}
