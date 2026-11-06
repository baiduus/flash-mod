package com.flash.hulk;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.entity.player.Player;

/**
 * Side-independent view of the Hulk state: the server reads the persistent data,
 * the client a cache filled by {@code HulkSyncPacket} (scale of every player and
 * the full state of the local one).
 */
public final class HulkState {

    /** uuid -> scale (> 0 while transformed); presence = has the power. */
    private static final Map<UUID, Float> CLIENT = new ConcurrentHashMap<>();

    // local player
    private static volatile int level;
    private static volatile float rage;
    private static volatile int tempTicks;
    private static volatile int furyTicks;
    private static volatile boolean destroy = true;
    private static volatile boolean grow = true;
    private static volatile long syncedAt;
    private static final int[] COOLDOWNS = new int[HulkAbility.values().length];

    private HulkState() {
    }

    public static boolean has(Player player) {
        return player.level().isClientSide ? CLIENT.containsKey(player.getUUID()) : HulkPowers.has(player);
    }

    public static boolean isTransformed(Player player) {
        if (player.level().isClientSide) {
            Float s = CLIENT.get(player.getUUID());
            return s != null && s > 0.0F;
        }
        return HulkPowers.isTransformed(player);
    }

    /** Body scale (1 when not transformed). */
    public static float scale(Player player) {
        if (player.level().isClientSide) {
            Float s = CLIENT.get(player.getUUID());
            return s != null && s > 0.0F ? s : 1.0F;
        }
        return HulkPowers.scale(player);
    }

    /** Client: called by the sync packet. */
    public static void setClient(UUID id, boolean has, boolean transformed, float scale, boolean local,
                                 int lvl, float rg, int temp, int fury, boolean dst, boolean grw, int[] cds,
                                 long now) {
        if (has) {
            CLIENT.put(id, transformed ? scale : 0.0F);
        } else {
            CLIENT.remove(id);
        }
        if (local) {
            level = lvl;
            rage = rg;
            tempTicks = temp;
            furyTicks = fury;
            destroy = dst;
            grow = grw;
            syncedAt = now;
            for (int i = 0; i < COOLDOWNS.length; i++) {
                COOLDOWNS[i] = cds != null && i < cds.length ? cds[i] : 0;
            }
        }
    }

    public static int level() {
        return level;
    }

    public static float rage() {
        return rage;
    }

    public static boolean destroyBlocks() {
        return destroy;
    }

    public static boolean grow() {
        return grow;
    }

    public static void setSettingsOptimistic(boolean dst, boolean grw) {
        destroy = dst;
        grow = grw;
    }

    private static int elapsed(long now) {
        return syncedAt == 0L ? 0 : (int) Math.max(0L, now - syncedAt);
    }

    public static int tempTicks(long now) {
        return tempTicks > 0 ? Math.max(0, tempTicks - elapsed(now)) : 0;
    }

    public static int furyTicks(long now) {
        return furyTicks > 0 ? Math.max(0, furyTicks - elapsed(now)) : 0;
    }

    public static int cooldown(HulkAbility ability, long now) {
        int c = COOLDOWNS[ability.ordinal()];
        return c > 0 ? Math.max(0, c - elapsed(now)) : 0;
    }

    public static void resetClient() {
        CLIENT.clear();
        level = 0;
        rage = 0.0F;
        tempTicks = 0;
        furyTicks = 0;
        syncedAt = 0L;
    }
}
