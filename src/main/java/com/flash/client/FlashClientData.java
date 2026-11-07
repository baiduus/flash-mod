package com.flash.client;

/**
 * Read-only client copy of the server progression. It is filled by
 * {@code FlashSyncPacket}; the HUD and the K screen never guess values.
 */
public final class FlashClientData {

    private static boolean hasPowers;
    private static int maxLevel;
    private static int selectedLevel;
    private static double distance;
    private static double totalDistance;
    private static boolean destroyBlocks;
    private static boolean temporary;
    private static int temporaryRemaining;
    private static final boolean[] ABILITY_ACTIVE = new boolean[32];
    private static final int[] ABILITY_COOLDOWN = new int[32];

    private FlashClientData() {
    }

    public static void set(boolean hasPowers, int maxLevel, int selectedLevel,
                           double distance, double totalDistance) {
        set(hasPowers, maxLevel, selectedLevel, distance, totalDistance, false, false, 0);
    }

    public static void set(boolean hasPowers, int maxLevel, int selectedLevel,
                           double distance, double totalDistance,
                           boolean destroyBlocks, boolean temporary, int temporaryRemaining) {
        set(hasPowers, maxLevel, selectedLevel, distance, totalDistance,
                destroyBlocks, temporary, temporaryRemaining, null);
    }

    public static void set(boolean hasPowers, int maxLevel, int selectedLevel,
                           double distance, double totalDistance,
                           boolean destroyBlocks, boolean temporary, int temporaryRemaining,
                           int[] cooldowns) {
        FlashClientData.hasPowers = hasPowers;
        FlashClientData.maxLevel = Math.max(0, maxLevel);
        FlashClientData.selectedLevel = Math.max(0, selectedLevel);
        FlashClientData.distance = Math.max(0.0D, distance);
        FlashClientData.totalDistance = Math.max(0.0D, totalDistance);
        FlashClientData.destroyBlocks = destroyBlocks;
        FlashClientData.temporary = temporary;
        FlashClientData.temporaryRemaining = Math.max(0, temporaryRemaining);
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        FlashClientData.temporaryAt = mc.level != null ? mc.level.getGameTime() : 0L;
        if (cooldowns != null) {
            setAbilityCooldowns(cooldowns);
        }
    }

    public static void reset() {
        set(false, 0, 0, 0.0D, 0.0D);
        java.util.Arrays.fill(ABILITY_ACTIVE, false);
        java.util.Arrays.fill(ABILITY_COOLDOWN, 0);
    }

    public static void tick() {
        for (int i = 0; i < ABILITY_COOLDOWN.length; i++) {
            if (ABILITY_COOLDOWN[i] > 0) {
                ABILITY_COOLDOWN[i]--;
            }
        }
    }

    public static void setAbilityState(int id, boolean active, int cooldownTicks) {
        if (id < 0 || id >= ABILITY_ACTIVE.length) {
            return;
        }
        ABILITY_ACTIVE[id] = active;
        ABILITY_COOLDOWN[id] = Math.max(0, cooldownTicks);
    }

    public static void setAbilityCooldowns(int[] cooldowns) {
        for (int i = 0; i < ABILITY_COOLDOWN.length && i < cooldowns.length; i++) {
            ABILITY_COOLDOWN[i] = Math.max(0, cooldowns[i]);
        }
    }

    public static boolean isAbilityActive(int id) {
        return id >= 0 && id < ABILITY_ACTIVE.length && ABILITY_ACTIVE[id];
    }

    public static int getActiveAbilityMask() {
        int mask = 0;
        for (int i = 0; i < ABILITY_ACTIVE.length; i++) {
            if (ABILITY_ACTIVE[i]) {
                mask |= 1 << i;
            }
        }
        return mask;
    }

    public static int getAbilityCooldown(int id) {
        return id >= 0 && id < ABILITY_COOLDOWN.length ? ABILITY_COOLDOWN[id] : 0;
    }

    public static boolean isTemporary() {
        return temporary;
    }

    public static int getTemporaryRemaining() {
        return temporaryRemaining;
    }

    private static long temporaryAt;

    /** Ticks left of the borrowed powers, counted down since the last sync. */
    public static int getTemporaryRemainingNow() {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        long now = mc.level != null ? mc.level.getGameTime() : temporaryAt;
        return (int) Math.max(0L, temporaryRemaining - (now - temporaryAt));
    }

    public static boolean isDestroyBlocks() {
        return destroyBlocks;
    }

    public static void setDestroyBlocksOptimistic(boolean enabled) {
        destroyBlocks = enabled;
    }

    /**
     * Optimistic update used only between sending a selection request and the
     * authoritative server sync. It keeps rapid key/wheel presses from all
     * resolving to the same old level.
     */
    public static void setSelectedLevelOptimistic(int level) {
        if (hasPowers) {
            selectedLevel = Math.max(0, Math.min(level, maxLevel));
        }
    }

    public static boolean hasPowers() {
        return hasPowers;
    }

    public static int getMaxLevel() {
        return maxLevel;
    }

    public static int getSelectedLevel() {
        return selectedLevel;
    }

    public static double getDistance() {
        return distance;
    }

    public static double getTotalDistance() {
        return totalDistance;
    }
}
