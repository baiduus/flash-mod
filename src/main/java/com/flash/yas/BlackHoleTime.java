package com.flash.yas;

import com.flash.FlashMod;
import com.flash.entity.BlackHole;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The black hole and Hand of God's time (the Temporal: G marks a moment, G again turns back to it).
 * <p>
 * A black hole opened while its maker has a moment marked notes every block it tears away (where, what it was).
 * When she turns time back, once the rewind is over every one of them is put back as it was (Hand of God's own
 * recording does not hold the whole crater: it was left a hole). It is told of the area too
 * ({@code Volta.antesDeArea}), as Hand of God's sun and moon do. A hole opened without a marked moment, or whose
 * moment is dropped, keeps no notes.
 */
@Mod.EventBusSubscriber(modid = FlashMod.MODID)
public final class BlackHoleTime {

    private static final int BUDGET = 6000;
    private static Method recording;
    private static Method rewinding;
    private static Method beforeArea;
    private static boolean looked;

    private static final class Notes {
        final UUID owner;
        final ServerLevel level;
        final Long2ObjectOpenHashMap<BlockState> was = new Long2ObjectOpenHashMap<>();
        final long born;
        boolean wound;
        long[] keys;
        int i = -1;

        Notes(UUID owner, ServerLevel level) {
            this.owner = owner;
            this.level = level;
            this.born = level.getGameTime();
        }
    }

    /** By black hole (entity id), while it lives; then kept here until time is turned back or the moment dropped. */
    private static final java.util.Map<Integer, Notes> OPEN = new java.util.HashMap<>();
    private static final List<Notes> KEPT = new ArrayList<>();

    private BlackHoleTime() {
    }

    private static boolean ready() {
        if (!looked) {
            looked = true;
            try {
                Class<?> v = Class.forName("handofgod.tempo.Volta");
                recording = v.getMethod("gravando", ServerPlayer.class);
                rewinding = v.getMethod("rebobinando", ServerPlayer.class);
                beforeArea = v.getMethod("antesDeArea", ServerLevel.class, Vec3.class, double.class);
            } catch (ReflectiveOperationException | LinkageError e) {
                recording = null;
            }
        }
        return recording != null;
    }

    private static boolean call(Method m, ServerPlayer p) {
        try {
            return Boolean.TRUE.equals(m.invoke(null, p));
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }

    /** A black hole opens: with a moment marked, Hand of God is told of the area and the notes begin. */
    public static void opened(BlackHole hole, ServerPlayer owner, double radius) {
        if (!ready() || !call(recording, owner)) {
            return;
        }
        try {
            beforeArea.invoke(null, owner.serverLevel(), hole.position(), radius);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // still noted here
        }
        OPEN.put(hole.getId(), new Notes(owner.getUUID(), owner.serverLevel()));
    }

    /** A block the hole tears away (its state before). */
    public static void note(BlackHole hole, ServerLevel level, BlockPos pos, BlockState state) {
        Notes n = OPEN.get(hole.getId());
        if (n != null) {
            n.was.putIfAbsent(pos.asLong(), state);
        }
    }

    /** The hole is gone: its notes wait for time to be turned back. */
    public static void closed(BlackHole hole) {
        Notes n = OPEN.remove(hole.getId());
        if (n != null && !n.was.isEmpty()) {
            KEPT.add(n);
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || (KEPT.isEmpty() && OPEN.isEmpty()) || !ready()) {
            return;
        }
        var server = event.getServer();
        List<Notes> all = new ArrayList<>(KEPT);
        all.addAll(OPEN.values());
        for (Notes n : all) {
            if (n.i >= 0) {
                continue;
            }
            ServerPlayer p = server.getPlayerList().getPlayer(n.owner);
            boolean back = p != null && call(rewinding, p);
            if (back) {
                n.wound = true;
            } else if (n.wound) {
                // the rewind is over: what the hole took is put back
                start(n);
            } else if (p == null || !call(recording, p) || n.level.getGameTime() - n.born > 20L * 60L * 30L) {
                // the moment was dropped (or never comes): nothing to put back
                n.i = Integer.MAX_VALUE;
            }
        }
        OPEN.values().removeIf(n -> n.i == Integer.MAX_VALUE);
        for (Iterator<Notes> it = KEPT.iterator(); it.hasNext(); ) {
            Notes n = it.next();
            if (n.i == Integer.MAX_VALUE) {
                it.remove();
            } else if (n.i >= 0) {
                if (put(n)) {
                    it.remove();
                }
            }
        }
    }

    private static void start(Notes n) {
        n.keys = n.was.keySet().toLongArray();
        // from the bottom up (what lies on top is put back on something)
        java.util.Arrays.sort(n.keys);
        long[] byY = n.keys;
        Long[] boxed = new Long[byY.length];
        for (int k = 0; k < byY.length; k++) {
            boxed[k] = byY[k];
        }
        java.util.Arrays.sort(boxed, (a, b) -> Integer.compare(BlockPos.getY(a), BlockPos.getY(b)));
        for (int k = 0; k < byY.length; k++) {
            byY[k] = boxed[k];
        }
        n.i = 0;
        if (!KEPT.contains(n)) {
            KEPT.add(n);
            OPEN.values().remove(n);
        }
    }

    /** A slice of the putting back; true when all is back. */
    private static boolean put(Notes n) {
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        int end = Math.min(n.keys.length, n.i + BUDGET);
        for (; n.i < end; n.i++) {
            at.set(n.keys[n.i]);
            BlockState was = n.was.get(n.keys[n.i]);
            if (was != null && n.level.isLoaded(at) && n.level.getBlockState(at) != was) {
                n.level.setBlock(at, was, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            }
        }
        return n.i >= n.keys.length;
    }
}
