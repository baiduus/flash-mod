package com.flash.client;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

/**
 * Client cache of the speed level and active-ability mask of every tracked
 * player. The local player is read from the normal optimistic progression copy;
 * other players are updated by {@code FlashVisualSyncPacket}.
 */
public final class FlashClientVisualLevels {

    private static final Map<UUID, Integer> LEVELS = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> ACTIVE = new ConcurrentHashMap<>();

    private FlashClientVisualLevels() {
    }

    public static void set(UUID playerId, int level) {
        set(playerId, level, 0);
    }

    public static void set(UUID playerId, int level, int activeMask) {
        if (level < 1) {
            LEVELS.remove(playerId);
        } else {
            LEVELS.put(playerId, level);
        }
        if (activeMask == 0) {
            ACTIVE.remove(playerId);
        } else {
            ACTIVE.put(playerId, activeMask);
        }
    }

    public static int get(Player player) {
        if (player == Minecraft.getInstance().player) {
            return FlashClientData.hasPowers() ? FlashClientData.getSelectedLevel() : 0;
        }
        Integer level = LEVELS.get(player.getUUID());
        return level == null ? 0 : level;
    }

    public static int activeMask(Player player) {
        if (player == Minecraft.getInstance().player) {
            return FlashClientData.getActiveAbilityMask();
        }
        Integer mask = ACTIVE.get(player.getUUID());
        return mask == null ? 0 : mask;
    }

    public static boolean isActive(Player player, int abilityId) {
        return (activeMask(player) & (1 << abilityId)) != 0;
    }

    public static void reset() {
        LEVELS.clear();
        ACTIVE.clear();
    }
}
