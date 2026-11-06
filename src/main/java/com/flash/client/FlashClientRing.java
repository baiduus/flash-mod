package com.flash.client;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

/** Client cache: which players carry the ring in the slot. */
public final class FlashClientRing {

    private static final Set<UUID> EQUIPPED = ConcurrentHashMap.newKeySet();

    private FlashClientRing() {
    }

    public static void set(UUID id, boolean equipped) {
        if (equipped) {
            EQUIPPED.add(id);
        } else {
            EQUIPPED.remove(id);
        }
    }

    public static boolean has(Player player) {
        return EQUIPPED.contains(player.getUUID());
    }

    public static boolean isLocalEquipped() {
        Player player = Minecraft.getInstance().player;
        return player != null && EQUIPPED.contains(player.getUUID());
    }

    public static void reset() {
        EQUIPPED.clear();
    }
}
