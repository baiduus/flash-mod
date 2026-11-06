package com.flash.client;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * High-intensity client trail with seven independent energy streams.
 *
 * <p>Each stream follows the player's path with its own lateral/vertical
 * offset, so the result is a cluster of simultaneous discharges around the
 * trajectory instead of a single line behind the player. Points are stored in
 * world space and fade individually. No per-tick trail packets are required.</p>
 */
@Mod.EventBusSubscriber(modid = com.flash.FlashMod.MODID, value = Dist.CLIENT)
public final class FlashTrailTracker {

    static final int LIFETIME_TICKS = 10;
    static final int STREAM_COUNT = 7;
    static final int MAX_POINTS_PER_STREAM = 12;
    private static final double MIN_STEP_SQR = 0.0025D;
    private static final double MAX_STEP_SQR = 64.0D;

    private static final double[] SIDE_OFFSET = {-0.26D, 0.26D, -0.13D, 0.13D, 0.0D, -0.04D, 0.04D};
    private static final double[] UP_OFFSET = {-0.15D, 0.12D, 0.0D, 0.28D, 0.16D, -0.25D, 0.38D};

    private static final Map<UUID, TrailState> STATES = new HashMap<>();

    private FlashTrailTracker() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            STATES.clear();
            return;
        }

        long gameTime = level.getGameTime();
        Set<UUID> seen = new HashSet<>();
        for (Player player : level.players()) {
            UUID id = player.getUUID();
            seen.add(id);
            int playerLevel = FlashClientVisualLevels.get(player);
            if (playerLevel < 3 || !player.isAlive() || player.isSpectator()) {
                STATES.remove(id);
                continue;
            }

            TrailState state = STATES.computeIfAbsent(id, key -> new TrailState(key, gameTime));
            state.level = playerLevel;
            state.negative = FlashClientVisualLevels.isNegative(player);
            // Never draw the trail of the local player in first person: it
            // would cover the view.
            if (player == minecraft.player && minecraft.options.getCameraType().isFirstPerson()) {
                state.clearPoints();
                state.initialized = false;
                continue;
            }
            Vec3 current = player.position();
            if (!state.initialized) {
                state.initialized = true;
                state.lastPosition = current;
                continue;
            }

            Vec3 previous = state.lastPosition;
            state.lastPosition = current;
            double dx = current.x - previous.x;
            double dz = current.z - previous.z;
            double distanceSqr = dx * dx + dz * dz;

            if (distanceSqr > MAX_STEP_SQR) {
                // Teleport: drop the old path instead of drawing a world-long line.
                state.clearPoints();
                continue;
            }
            if (distanceSqr < MIN_STEP_SQR && Math.abs(current.y - previous.y) < 0.08D) {
                state.expire(gameTime);
                continue;
            }

            Vec3 motion = current.subtract(previous);
            Vec3 horizontal = new Vec3(motion.x, 0.0D, motion.z);
            if (horizontal.lengthSqr() > 1.0E-6D) {
                state.lastDirection = horizontal.normalize();
            }
            Vec3 side = new Vec3(-state.lastDirection.z, 0.0D, state.lastDirection.x).normalize();

            for (int lane = 0; lane < STREAM_COUNT; lane++) {
                RandomSource random = RandomSource.create(
                        state.seed + gameTime * 0x9E3779B97F4A7C15L
                                + lane * 0x632BE59BD9B4E019L);
                double jitterSide = (random.nextDouble() - 0.5D) * 0.10D;
                double jitterUp = (random.nextDouble() - 0.5D) * 0.09D;
                Vec3 point = current.add(0.0D, 0.92D, 0.0D)
                        .add(side.scale(SIDE_OFFSET[lane] + jitterSide))
                        .add(0.0D, UP_OFFSET[lane] + jitterUp, 0.0D);
                state.streams[lane].points.addLast(new TrailPoint(point, gameTime, lane, current));
                while (state.streams[lane].points.size() > MAX_POINTS_PER_STREAM) {
                    state.streams[lane].points.removeFirst();
                }
            }
            state.expire(gameTime);
        }

        STATES.entrySet().removeIf(entry -> !seen.contains(entry.getKey()));
        for (TrailState state : STATES.values()) {
            state.expire(gameTime);
        }
    }

    public static Collection<TrailState> states() {
        return STATES.values();
    }

    static final class TrailState {
        final TrailStream[] streams = new TrailStream[STREAM_COUNT];
        final long seed;
        final UUID id;
        Vec3 lastPosition = Vec3.ZERO;
        Vec3 lastDirection = new Vec3(0.0D, 0.0D, 1.0D);
        boolean initialized;
        int level = 3;
        boolean negative;

        TrailState(UUID id, long gameTime) {
            this.id = id;
            this.seed = id.getMostSignificantBits()
                    ^ Long.rotateLeft(id.getLeastSignificantBits(), 31);
            for (int i = 0; i < STREAM_COUNT; i++) {
                this.streams[i] = new TrailStream(i);
            }
        }

        void expire(long gameTime) {
            for (TrailStream stream : this.streams) {
                Iterator<TrailPoint> iterator = stream.points.iterator();
                while (iterator.hasNext()) {
                    TrailPoint point = iterator.next();
                    if (gameTime - point.createdAt >= LIFETIME_TICKS) {
                        iterator.remove();
                    }
                }
            }
        }

        void clearPoints() {
            for (TrailStream stream : this.streams) {
                stream.points.clear();
            }
        }
    }

    static final class TrailStream {
        final int lane;
        final Deque<TrailPoint> points = new ArrayDeque<>();

        TrailStream(int lane) {
            this.lane = lane;
        }
    }

    /** {@code base} is the player's own position when the point was recorded. */
    record TrailPoint(Vec3 position, long createdAt, int lane, Vec3 base) {
    }
}
