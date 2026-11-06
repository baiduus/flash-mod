package com.flash.client;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

/** Client cache: which players carry the ring in the slot. */
public final class FlashClientRing {

    private static final Set<UUID> EQUIPPED = ConcurrentHashMap.newKeySet();
    private static final Set<UUID> AMPLIFIER = ConcurrentHashMap.newKeySet();

    private FlashClientRing() {
    }

    public static void set(UUID id, boolean equipped, boolean amplifier) {
        if (equipped) {
            EQUIPPED.add(id);
        } else {
            EQUIPPED.remove(id);
        }
        if (amplifier) {
            AMPLIFIER.add(id);
        } else {
            AMPLIFIER.remove(id);
        }
    }

    public static boolean hasAmplifier(Player player) {
        return AMPLIFIER.contains(player.getUUID());
    }

    public static boolean isLocalAmplifier() {
        Player player = Minecraft.getInstance().player;
        return player != null && AMPLIFIER.contains(player.getUUID());
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
        AMPLIFIER.clear();
    }
}
