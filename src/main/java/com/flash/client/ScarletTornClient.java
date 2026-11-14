package com.flash.client;

import com.flash.FlashMod;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Players torn apart by the Scarlet Witch, here: their body (armour and all) is not drawn for those five seconds,
 * and Hand of God's pieces of them stay instead of vanishing at once (they vanish when "the creature came back",
 * and a torn player never left: PedacosMixin asks here).
 */
@Mod.EventBusSubscriber(modid = FlashMod.MODID, value = Dist.CLIENT)
public final class ScarletTornClient {

    /** Entity id -> game time it is whole again. */
    private static final Int2LongOpenHashMap TORN = new Int2LongOpenHashMap();

    private ScarletTornClient() {
    }

    public static void receive(int entity, int ticks) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        if (ticks <= 0) {
            TORN.remove(entity);
        } else {
            TORN.put(entity, mc.level.getGameTime() + ticks);
        }
    }

    public static void raised() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof DeathScreen) {
            mc.setScreen(null);
        }
    }

    public static boolean torn(Entity e) {
        if (e == null || TORN.isEmpty()) {
            return false;
        }
        Minecraft mc = Minecraft.getInstance();
        long until = TORN.getOrDefault(e.getId(), -1L);
        return until >= 0L && mc.level != null && mc.level.getGameTime() < until;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRender(RenderPlayerEvent.Pre event) {
        if (torn(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.phase != TickEvent.Phase.END || TORN.isEmpty()) {
            return;
        }
        if (mc.level == null) {
            TORN.clear();
            return;
        }
        long now = mc.level.getGameTime();
        TORN.int2LongEntrySet().removeIf(en -> now >= en.getLongValue() + 20L);
    }
}
