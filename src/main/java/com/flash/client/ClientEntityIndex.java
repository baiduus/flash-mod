package com.flash.client;

import com.flash.FlashMod;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The few kinds of entity the mod draws by hand (God Wand's arcane orbs and stone cannon shots, the Chaos energy,
 * creatures frozen solid), found once a tick in one pass over the world's entities, instead of every render hook
 * walking all of them every frame. A list may hold an entity removed since the tick: the drawers skip those.
 */
@Mod.EventBusSubscriber(modid = FlashMod.MODID, value = Dist.CLIENT)
public final class ClientEntityIndex {

    public static final List<Entity> ARCANE_ORBS = new ArrayList<>();
    public static final List<Entity> STONE_CANNONS = new ArrayList<>();
    public static final List<Entity> CHAOS = new ArrayList<>();
    public static final List<LivingEntity> FROZEN = new ArrayList<>();

    private ClientEntityIndex() {
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) {
            return;
        }
        ARCANE_ORBS.clear();
        STONE_CANNONS.clear();
        CHAOS.clear();
        FROZEN.clear();
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof LivingEntity living) {
                if (living.getTicksFrozen() > 20) {
                    FROZEN.add(living);
                }
            } else if (e instanceof com.flash.godwand.ArcaneOrbEntity) {
                ARCANE_ORBS.add(e);
            } else if (e instanceof com.flash.godwand.StoneCannonProjectileEntity) {
                STONE_CANNONS.add(e);
            } else if (e instanceof com.flash.entity.ChaosEnergy) {
                CHAOS.add(e);
            }
        }
    }
}
