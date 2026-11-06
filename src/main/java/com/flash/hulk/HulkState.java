package com.flash.hulk;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.entity.player.Player;

/**
 * Side-independent view of "is this player the Hulk right now": the server reads
 * the persistent data, the client a cache filled by {@code HulkSyncPacket}. Used by
 * code that runs on both sides (size, jumping, rendering decisions).
 */
public final class HulkState {

    private static final Set<UUID> CLIENT_TRANSFORMED = ConcurrentHashMap.newKeySet();
    private static final Set<UUID> CLIENT_HAS = ConcurrentHashMap.newKeySet();
    private static volatile float clientRage;

    private HulkState() {
    }

    public static boolean isTransformed(Player player) {
        if (player.level().isClientSide) {
            return CLIENT_TRANSFORMED.contains(player.getUUID());
        }
        return HulkPowers.isTransformed(player);
    }

    public static boolean has(Player player) {
        if (player.level().isClientSide) {
            return CLIENT_HAS.contains(player.getUUID());
        }
        return HulkPowers.has(player);
    }

    /** Client: called by the sync packet. */
    public static void setClient(UUID id, boolean has, boolean transformed, float rage, boolean local) {
        if (has) {
            CLIENT_HAS.add(id);
        } else {
            CLIENT_HAS.remove(id);
        }
        if (transformed) {
            CLIENT_TRANSFORMED.add(id);
        } else {
            CLIENT_TRANSFORMED.remove(id);
        }
        if (local) {
            clientRage = rage;
        }
    }

    public static float clientRage() {
        return clientRage;
    }

    public static void resetClient() {
        CLIENT_TRANSFORMED.clear();
        CLIENT_HAS.clear();
        clientRage = 0.0F;
    }
}
