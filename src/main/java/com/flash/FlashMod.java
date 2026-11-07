package com.flash;

import com.flash.config.FlashClientConfig;
import com.flash.network.FlashNetwork;
import com.flash.registry.ModBlockEntities;
import com.flash.registry.ModBlocks;
import com.flash.registry.ModCreativeTabs;
import com.flash.registry.ModItems;
import com.flash.registry.ModMenus;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/**
 * Entry point of the Flash mod.
 *
 * <p>Systems are kept independent:</p>
 * <ul>
 *     <li>{@code com.flash.block}/{@code menu}: chamber and potion container.</li>
 *     <li>{@code com.flash.transformation}: potion validation and sequence.</li>
 *     <li>{@code com.flash.power}: levels, progression, combustion, regeneration and visuals.</li>
 *     <li>{@code com.flash.network}: server/client progression synchronisation.</li>
 * </ul>
 */
@Mod(FlashMod.MODID)
public final class FlashMod {

    public static final String MODID = "flash";

    public FlashMod() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();

        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, FlashClientConfig.SPEC);

        FlashNetwork.register();
        com.flash.power.FlashCombustion.registerProtection(
                player -> com.flash.item.FlashSuitItem.countWorn(player) > 0);

        ModBlocks.BLOCKS.register(modBus);
        ModItems.ITEMS.register(modBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modBus);
        com.flash.registry.ModEntities.ENTITY_TYPES.register(modBus);
        ModMenus.MENUS.register(modBus);
        ModCreativeTabs.CREATIVE_MODE_TABS.register(modBus);
        com.flash.registry.ModParticles.PARTICLE_TYPES.register(modBus);
        com.flash.registry.ModSounds.SOUNDS.register(modBus);
        com.flash.registry.ModStructures.STRUCTURE_TYPES.register(modBus);
        com.flash.registry.ModStructures.PIECE_TYPES.register(modBus);
    }
}
