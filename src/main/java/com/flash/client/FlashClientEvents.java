package com.flash.client;

import com.flash.FlashMod;
import com.flash.registry.ModBlockEntities;
import com.flash.registry.ModBlocks;
import com.flash.registry.ModItems;
import com.flash.registry.ModMenus;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = FlashMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class FlashClientEvents {

    private FlashClientEvents() {
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            // Force the custom Forge ArmPose to be created during client
            // setup, before any player model is rendered.
            FlashAbilityPoses.WIND_HANDS.name();
            MenuScreens.register(
                    ModMenus.POTION_CONTAINER.get(),
                    PotionContainerScreen::new
            );
            ItemBlockRenderTypes.setRenderLayer(ModBlocks.TRANSFORMATION_CHAMBER.get(), RenderType.translucent());
            ItemBlockRenderTypes.setRenderLayer(ModBlocks.POTION_CONTAINER.get(), RenderType.translucent());
            ItemBlockRenderTypes.setRenderLayer(ModBlocks.ACCELERATION_PORTAL.get(), RenderType.translucent());
            // Syringe model changes with its content (flash:content: 0 empty, 1 negative force, ...).
            net.minecraft.client.renderer.item.ItemProperties.register(
                    ModItems.SYRINGE.get(),
                    new net.minecraft.resources.ResourceLocation("flash", "content"),
                    (stack, level, entity, seed) -> com.flash.item.SyringeItem.modelIndex(stack));
        });
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(com.flash.registry.ModEntities.FLASH_SUIT_STAND.get(),
                FlashSuitStandRenderer::new);
        event.registerEntityRenderer(com.flash.registry.ModEntities.ACCELERATION_PHANTOM.get(),
                AccelerationPhantomRenderer::new);
        event.registerEntityRenderer(com.flash.registry.ModEntities.HULK_BOULDER.get(),
                net.minecraft.client.renderer.entity.ThrownItemRenderer::new);
        event.registerBlockEntityRenderer(
                ModBlockEntities.ACCELERATION_PORTAL.get(),
                AccelerationPortalRenderer::new
        );
        event.registerBlockEntityRenderer(
                ModBlockEntities.TACHYON_EXTRACTOR.get(),
                TachyonExtractorRenderer::new
        );
    }

    @SubscribeEvent
    public static void onAddLayers(EntityRenderersEvent.AddLayers event) {
        for (String skin : new String[] {"default", "slim"}) {
            net.minecraft.client.renderer.entity.EntityRenderer<?> renderer = event.getSkin(skin);
            if (renderer instanceof net.minecraft.client.renderer.entity.player.PlayerRenderer player) {
                player.addLayer(new FlashRingLayer(player));
                player.addLayer(new FlashAmplifierLayer(player));
                player.addLayer(new HulkClient.Layer(player));
            }
        }
    }

    @SubscribeEvent
    public static void onRegisterOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("flash_speed", new FlashHud());
        event.registerAboveAll("flash_abilities", new FlashAbilityHud());
    }
}
