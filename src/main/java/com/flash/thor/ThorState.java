package com.flash.thor;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.entity.player.Player;

/**
 * Side-independent view of the Thor state: the server reads the persistent data, the client a cache filled by
 * {@code ThorSyncPacket} (form, flags and suit of every player, the full state of the local one).
 */
public final class ThorState {

    private static final Map<UUID, Integer> CLIENT = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> SUITS = new ConcurrentHashMap<>();
    private static final int FORM_BIT = 1 << 30;
    private static final int HELMET_BIT = 1 << 29;

    private static volatile int level;
    private static volatile float xp;
    private static volatile float energy;
    private static volatile float charge;
    private static volatile boolean destroy = true;
    private static volatile boolean noCooldowns;
    private static volatile boolean infinite;
    private static volatile int storm;
    private static volatile long syncedAt;
    private static final int[] COOLDOWNS = new int[ThorAbility.values().length];

    private ThorState() {
    }

    public static boolean has(Player player) {
        return player.level().isClientSide ? CLIENT.containsKey(player.getUUID()) : ThorPowers.has(player);
    }

    public static boolean isThor(Player player) {
        if (player.level().isClientSide) {
            Integer f = CLIENT.get(player.getUUID());
            return f != null && (f & FORM_BIT) != 0;
        }
        return ThorPowers.isThor(player);
    }

    public static boolean flag(Player player, int flag) {
        if (player.level().isClientSide) {
            Integer f = CLIENT.get(player.getUUID());
            return f != null && (f & flag) != 0;
        }
        return ThorPowers.flag(player, flag);
    }

    public static int suit(Player player) {
        if (player.level().isClientSide) {
            return SUITS.getOrDefault(player.getUUID(), 0);
        }
        return ThorPowers.suit(player);
    }

    public static boolean helmetOff(Player player) {
        if (player.level().isClientSide) {
            Integer f = CLIENT.get(player.getUUID());
            return f != null && (f & HELMET_BIT) != 0;
        }
        return ThorPowers.helmetOff(player);
    }

    public static void setClient(UUID id, boolean has, boolean thor, int flags, boolean helmetOff, int suit,
                                 boolean local, int lvl, float x, float e, float c, boolean dst, int[] cds,
                                 boolean noCd, boolean inf, int st, long now) {
        if (has) {
            CLIENT.put(id, flags | (thor ? FORM_BIT : 0) | (helmetOff ? HELMET_BIT : 0));
            SUITS.put(id, suit);
        } else {
            CLIENT.remove(id);
            SUITS.remove(id);
        }
        if (local) {
            level = lvl;
            xp = x;
            energy = e;
            charge = c;
            destroy = dst;
            noCooldowns = noCd;
            infinite = inf;
            storm = st;
            syncedAt = now;
            for (int i = 0; i < COOLDOWNS.length; i++) {
                COOLDOWNS[i] = cds != null && i < cds.length ? cds[i] : 0;
            }
        }
    }

    /** Optimistic local change of the suit / helmet (the K menu), until the server confirms it. */
    public static void setLocalLook(UUID id, int suit, Boolean helmetOff) {
        SUITS.put(id, suit);
        if (helmetOff != null) {
            CLIENT.computeIfPresent(id, (k, f) -> helmetOff ? f | HELMET_BIT : f & ~HELMET_BIT);
        }
    }

    public static int level() {
        return level;
    }

    public static float xp() {
        return xp;
    }

    public static float energy() {
        return energy;
    }

    public static float charge() {
        return charge;
    }

    public static boolean destroyBlocks() {
        return destroy;
    }

    public static void setDestroyOptimistic(boolean value) {
        destroy = value;
    }

    public static boolean noCooldowns() {
        return noCooldowns;
    }

    public static boolean infiniteEnergy() {
        return infinite;
    }

    /** 0 clear sky, 1 rain, 2 thunderstorm (above the local player). */
    public static int storm() {
        return storm;
    }

    public static int cooldown(ThorAbility ability, long now) {
        int c = COOLDOWNS[ability.ordinal()];
        int elapsed = syncedAt == 0L ? 0 : (int) Math.max(0L, now - syncedAt);
        return c > 0 ? Math.max(0, c - elapsed) : 0;
    }

    public static void resetClient() {
        CLIENT.clear();
        SUITS.clear();
        level = 0;
        xp = 0.0F;
        energy = 0.0F;
        charge = 0.0F;
        syncedAt = 0L;
    }
}
