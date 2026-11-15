package com.flash.client.infinity;

import com.flash.FlashMod;
import com.flash.infinity.InfinityStone;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterNamedRenderTypesEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** The stones on the client: their colours, the glowing render type of their models, the bar. */
@Mod.EventBusSubscriber(modid = FlashMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class InfinityClientSetup {

    private InfinityClientSetup() {
    }

    @SubscribeEvent
    public static void onColors(RegisterColorHandlersEvent.Item event) {
        for (InfinityStone s : InfinityStone.values()) {
            event.register((stack, tint) -> tint == 0 ? 0xFF000000 | s.color : -1, s.item());
        }
    }

    /** The stones' models ask for "flash:stone": see-through and bright (the original's stone render type). */
    @SubscribeEvent
    public static void onRenderTypes(RegisterNamedRenderTypesEvent event) {
        event.register("stone", RenderType.translucent(), Sheets.translucentCullBlockSheet());
    }

    @SubscribeEvent
    public static void onRenderers(net.minecraftforge.client.event.EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(com.flash.infinity.InfinityEntities.SPACE_PORTAL.get(),
                SpacePortalRenderer::new);
        event.registerEntityRenderer(com.flash.infinity.InfinityEntities.BLACK_HOLE.get(), BlackHoleRenderer::new);
    }

    @SubscribeEvent
    public static void onOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("flash_infinity", new InfinityHud());
    }
}
