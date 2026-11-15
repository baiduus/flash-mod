package com.flash.client.infinity;

import com.flash.FlashMod;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** The Reality Stone on the client: players' sizes (drawn and their bodies), true invisibility, the saved block. */
@Mod.EventBusSubscriber(modid = FlashMod.MODID, value = Dist.CLIENT)
public final class RealityClient {

    private static final Map<Integer, Float> SCALES = new HashMap<>();
    private static BlockState saved;
    private static boolean hooked;

    private RealityClient() {
    }

    static void hook() {
        if (!hooked) {
            hooked = true;
            com.flash.infinity.RealityStone.clientScale = e -> SCALES.getOrDefault(e.getId(), 1.0F);
        }
    }

    static void scale(int entity, float s) {
        hook();
        if (Math.abs(s - 1.0F) < 1.0E-4F) {
            SCALES.remove(entity);
        } else {
            SCALES.put(entity, s);
        }
        Minecraft mc = Minecraft.getInstance();
        Entity e = mc.level == null ? null : mc.level.getEntity(entity);
        if (e != null) {
            e.refreshDimensions();
        }
    }

    static void saved(CompoundTag t) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            saved = NbtUtils.readBlockState(mc.level.holderLookup(net.minecraft.core.registries.Registries.BLOCK), t);
        }
    }

    public static BlockState saved() {
        return saved;
    }

    public static void reset() {
        SCALES.clear();
        saved = null;
    }

    /** Drawn at its size; truly invisible: not drawn at all (armour and held items too). */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPre(RenderPlayerEvent.Pre event) {
        Player p = event.getEntity();
        Minecraft mc = Minecraft.getInstance();
        boolean invisible = p == mc.player ? InfinityClient.anyOn("palladium:invisibility")
                : InfinityClient.visible(p, "palladium:invisibility");
        if (invisible && !(p == mc.player && mc.player.isSpectator())) {
            event.setCanceled(true);
            return;
        }
        float s = SCALES.getOrDefault(p.getId(), 1.0F);
        if (s != 1.0F) {
            event.getPoseStack().pushPose();
            event.getPoseStack().scale(s, s, s);
            SCALED.put(p.getId(), true);
        }
    }

    private static final Map<Integer, Boolean> SCALED = new HashMap<>();

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPost(RenderPlayerEvent.Post event) {
        if (SCALED.remove(event.getEntity().getId()) != null) {
            event.getPoseStack().popPose();
        }
    }
}
