package com.flash.power;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Real vanilla combustion caused by high speed levels.
 *
 * <p>From level {@link FlashSpeedLevels#FIRE_LEVEL} onwards a moving player is
 * set on fire with the normal Minecraft fire state. Stopping, lowering the
 * level or losing the powers removes <em>only</em> the fire owned by this
 * system. The class keeps a small per-player bookkeeping record so that lava,
 * fire blocks, other mods and vanilla sources are never extinguished by
 * accident.</p>
 *
 * <p>A future suit can register a {@link Protection}; protected players keep
 * their speed but are not ignited by the mod. The protection hook is kept
 * separate from the level checks on purpose.</p>
 */
public final class FlashCombustion {

    /** Fire duration applied while running. */
    private static final int FIRE_TICKS = 80;

    /**
     * Refresh a little before the fire would naturally wear off. Keeping a
     * known remainder lets us distinguish our own fire from external sources.
     */
    private static final int REFRESH_THRESHOLD = 60;

    /** Below this horizontal step the player counts as standing still. */
    private static final double MIN_MOVE_SQR = 2.5E-5D;

    /** Steps larger than this are teleports and must not count as running. */
    private static final double MAX_STEP_SQR = 16.0D;

    private static final Map<UUID, State> STATES = new ConcurrentHashMap<>();
    private static final List<Protection> PROTECTIONS = new CopyOnWriteArrayList<>();

    private FlashCombustion() {
    }

    public interface Protection {
        boolean protects(ServerPlayer player);
    }

    public static void registerProtection(Protection protection) {
        if (protection != null) {
            PROTECTIONS.add(protection);
        }
    }

    public static boolean isProtected(ServerPlayer player) {
        for (Protection protection : PROTECTIONS) {
            if (protection.protects(player)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether the selected level is allowed to cause combustion, regardless of
     * movement. Used by the HUD/commands; actual ignition additionally needs
     * movement.
     */
    public static boolean isCombustionLevel(ServerPlayer player) {
        return FlashPowers.hasPowers(player)
                && FlashPowers.getSelectedLevel(player) >= FlashSpeedLevels.FIRE_LEVEL;
    }

    public static boolean shouldCombust(ServerPlayer player) {
        return isCombustionLevel(player)
                && !isProtected(player)
                && !player.isSpectator()
                && player.isAlive()
                && !player.isInWaterRainOrBubble();
    }

    public static void tick(ServerPlayer player) {
        State state = STATES.computeIfAbsent(player.getUUID(), id -> new State());
        boolean moving = updateMovement(player, state);

        if (!shouldCombust(player) || !moving) {
            stopOwnedFire(player, state);
            return;
        }

        maintainFire(player, state);
    }

    /**
     * Called immediately after the selected level changes. Dropping below the
     * combustion level must not wait for the next tick to clean the mod fire.
     */
    public static void onLevelChanged(ServerPlayer player) {
        State state = STATES.get(player.getUUID());
        if (state == null) {
            return;
        }
        if (!isCombustionLevel(player) || isProtected(player)) {
            stopOwnedFire(player, state);
        }
    }

    public static void reset(ServerPlayer player, boolean clearOwnedFire) {
        State state = STATES.remove(player.getUUID());
        if (clearOwnedFire && state != null) {
            stopOwnedFire(player, state);
        }
    }

    public static void clear() {
        STATES.clear();
    }

    public static Predicate<ServerPlayer> protectionPredicate() {
        return FlashCombustion::isProtected;
    }

    private static void maintainFire(ServerPlayer player, State state) {
        int remaining = player.getRemainingFireTicks();

        // A source added more fire than our known remainder: it belongs to
        // lava/a fire block/another mod. Release ownership and leave it alone.
        if (state.ownedFireTicks <= 0 && remaining > 0) {
            return;
        }
        if (state.ownedFireTicks > 0 && remaining > state.ownedFireTicks + 2) {
            state.ownedFireTicks = 0;
            return;
        }

        if (state.ownedFireTicks > 0) {
            state.ownedFireTicks = remaining;
        }

        // Do not overwrite external fire that appeared while we were burning.
        if (isNearExternalFire(player)) {
            state.ownedFireTicks = 0;
            return;
        }

        if (remaining <= 0 || (state.ownedFireTicks > 0 && remaining <= REFRESH_THRESHOLD)) {
            player.setSecondsOnFire(FIRE_TICKS / 20);
            state.ownedFireTicks = FIRE_TICKS;
        }
    }

    private static void stopOwnedFire(ServerPlayer player, State state) {
        int owned = state.ownedFireTicks;
        state.ownedFireTicks = 0;
        if (owned <= 0) {
            return;
        }

        int remaining = player.getRemainingFireTicks();
        // Only clear the exact fire we applied. A longer/refreshed fire is
        // treated as external even if it started while the mod was active.
        if (!isNearExternalFire(player) && remaining <= owned + 2) {
            player.clearFire();
        }
    }

    private static boolean isNearExternalFire(ServerPlayer player) {
        if (player.isInLava()) {
            return true;
        }
        Level level = player.level();
        BlockPos feet = player.blockPosition();
        for (int dy = 0; dy <= 1; dy++) {
            BlockState state = level.getBlockState(feet.above(dy));
            if (state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE)) {
                return true;
            }
        }
        return false;
    }

    private static boolean updateMovement(ServerPlayer player, State state) {
        double x = player.getX();
        double z = player.getZ();
        if (!state.initialized) {
            state.initialized = true;
            state.lastX = x;
            state.lastZ = z;
            return false;
        }

        double dx = x - state.lastX;
        double dz = z - state.lastZ;
        state.lastX = x;
        state.lastZ = z;
        double distanceSqr = dx * dx + dz * dz;
        return distanceSqr >= MIN_MOVE_SQR && distanceSqr <= MAX_STEP_SQR;
    }

    private static final class State {
        private boolean initialized;
        private double lastX;
        private double lastZ;
        private int ownedFireTicks;
    }
}
