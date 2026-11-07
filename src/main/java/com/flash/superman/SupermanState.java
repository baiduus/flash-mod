package com.flash.superman;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.entity.player.Player;

/**
 * Side-independent view of the Superman state: the server reads the persistent
 * data, the client a cache filled by {@code SupermanSyncPacket} (form and flags
 * of every player, the full state of the local one).
 */
public final class SupermanState {

    /** uuid -> flags (bit 31 = Superman form); presence = has the power. */
    private static final Map<UUID, Integer> CLIENT = new ConcurrentHashMap<>();
    private static final int FORM_BIT = 1 << 30;

    private static volatile int level;
    private static volatile float xp;
    private static volatile float energy;
    private static volatile boolean destroy = true;
    private static volatile boolean noCooldowns;
    private static volatile boolean infinite;
    private static volatile boolean sun;
    private static volatile long syncedAt;
    private static final int[] COOLDOWNS = new int[SupermanAbility.values().length];

    private SupermanState() {
    }

    public static boolean has(Player player) {
        return player.level().isClientSide ? CLIENT.containsKey(player.getUUID()) : SupermanPowers.has(player);
    }

    public static boolean isSuperman(Player player) {
        if (player.level().isClientSide) {
            Integer f = CLIENT.get(player.getUUID());
            return f != null && (f & FORM_BIT) != 0;
        }
        return SupermanPowers.isSuperman(player);
    }

    public static boolean flag(Player player, int flag) {
        if (player.level().isClientSide) {
            Integer f = CLIENT.get(player.getUUID());
            return f != null && (f & flag) != 0;
        }
        return SupermanPowers.flag(player, flag);
    }

    public static void setClient(UUID id, boolean has, boolean superman, int flags, boolean local, int lvl, float x,
                                 float e, boolean dst, int[] cds, boolean noCd, boolean inf, boolean s, long now) {
        if (has) {
            CLIENT.put(id, flags | (superman ? FORM_BIT : 0));
        } else {
            CLIENT.remove(id);
        }
        if (local) {
            level = lvl;
            xp = x;
            energy = e;
            destroy = dst;
            noCooldowns = noCd;
            infinite = inf;
            sun = s;
            syncedAt = now;
            for (int i = 0; i < COOLDOWNS.length; i++) {
                COOLDOWNS[i] = cds != null && i < cds.length ? cds[i] : 0;
            }
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

    public static boolean inSun() {
        return sun;
    }

    public static int cooldown(SupermanAbility ability, long now) {
        int c = COOLDOWNS[ability.ordinal()];
        int elapsed = syncedAt == 0L ? 0 : (int) Math.max(0L, now - syncedAt);
        return c > 0 ? Math.max(0, c - elapsed) : 0;
    }

    private static final Map<UUID, Integer> SUITS = new ConcurrentHashMap<>();
    private static volatile int tempTicks;
    private static volatile long tempSyncedAt;

    public static void setSuit(UUID id, int suit, int temp, long now) {
        SUITS.put(id, suit);
        if (temp >= 0) {
            tempTicks = temp;
            tempSyncedAt = now;
        }
    }

    /** Suit index chosen by a player (client side; 0 = classic). */
    public static int suit(Player player) {
        if (player.level().isClientSide) {
            return SUITS.getOrDefault(player.getUUID(), 0);
        }
        return SupermanPowers.suit(player);
    }

    /** Ticks left of the local temporary power (0 = permanent). */
    public static int temporaryTicks(long now) {
        return tempTicks <= 0 ? 0 : (int) Math.max(0L, tempTicks - (now - tempSyncedAt));
    }

    private static volatile int combo;
    private static volatile boolean laserCut;

    public static void setOptions(int c, boolean cut) {
        combo = c;
        laserCut = cut;
    }

    /** Local player's combo move (see {@link SupermanCombos}). */
    public static int combo() {
        return combo;
    }

    public static boolean laserCut() {
        return laserCut;
    }

    public static void resetClient() {
        SUITS.clear();
        tempTicks = 0;
        CLIENT.clear();
        level = 0;
        xp = 0.0F;
        energy = 0.0F;
        syncedAt = 0L;
    }
}
