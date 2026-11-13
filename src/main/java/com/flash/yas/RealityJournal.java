package com.flash.yas;

import com.flash.FlashMod;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Makes the Manipulation of Reality give the land back exactly as it was.
 * <p>
 * Hand of God plans each wave as a list of blocks (where, what was there, what it becomes) and, going back, puts
 * the old block only where the new one is still unchanged (not to undo what a player built meanwhile). But the
 * leaves it grows change by themselves (their distance to the trunk, as the neighbours change), so going back it
 * took the trunks and left every crown in the air. Here, when one of its waves is over, its own list is read again
 * and every block still of the kind the wave put there (whatever its little states) goes back to what was there,
 * a few thousand a tick, without neighbour updates (no cascade of leaves decaying, no lag).
 * <p>
 * Also: no fog at the edge of a rewritten area for those outside it (Hand of God's client option, turned off).
 */
@Mod.EventBusSubscriber(modid = FlashMod.MODID)
public final class RealityJournal {

    private static final int BUDGET = 4000;
    private static boolean looked;
    private static java.lang.reflect.Method actives;
    private static Field id;
    private static Field phase;
    private static Field pos;
    private static Field orig;
    private static Field fin;

    /** A wave seen alive: its list (kept when it leaves Hand of God's). */
    private static final class Seen {
        long[] pos;
        BlockState[] orig;
        BlockState[] fin;
        ServerLevel level;
    }

    private static final Map<UUID, Seen> SEEN = new HashMap<>();

    /** A finished wave being put right. */
    private static final class Fix {
        final Seen s;
        int i;

        Fix(Seen s) {
            this.s = s;
        }
    }

    private static final List<Fix> FIXES = new ArrayList<>();

    private RealityJournal() {
    }

    private static boolean ready() {
        if (!looked) {
            looked = true;
            try {
                actives = Class.forName("handofgod.realidade.Realidade").getMethod("ativas");
                Class<?> t = Class.forName("handofgod.realidade.Transformacao");
                id = t.getField("id");
                phase = t.getField("fase");
                pos = t.getDeclaredField("pos");
                orig = t.getDeclaredField("orig");
                fin = t.getDeclaredField("fin");
                pos.setAccessible(true);
                orig.setAccessible(true);
                fin.setAccessible(true);
            } catch (ReflectiveOperationException | LinkageError e) {
                actives = null;
            }
        }
        return actives != null;
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !ready()) {
            return;
        }
        // the waves alive now (and their lists, kept)
        Set<UUID> alive = new HashSet<>();
        try {
            Object list = actives.invoke(null);
            if (list instanceof List<?> l) {
                for (Object t : l) {
                    UUID key = (UUID) id.get(t);
                    alive.add(key);
                    Seen s = SEEN.computeIfAbsent(key, k -> new Seen());
                    long[] p = (long[]) pos.get(t);
                    if (p != null) {
                        s.pos = p;
                        s.orig = (BlockState[]) orig.get(t);
                        s.fin = (BlockState[]) fin.get(t);
                    }
                    if (s.level == null) {
                        s.level = levelOf(event.getServer(), t);
                    }
                    Object f = phase.get(t);
                    if (f != null && "FIM".equals(f.toString())) {
                        alive.remove(key);
                    }
                }
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            return;
        }
        // a wave gone (gone back): its leftovers are put right
        for (Iterator<Map.Entry<UUID, Seen>> it = SEEN.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Seen> en = it.next();
            if (!alive.contains(en.getKey())) {
                it.remove();
                Seen s = en.getValue();
                if (s.pos != null && s.orig != null && s.fin != null && s.level != null && !permanent()) {
                    FIXES.add(new Fix(s));
                }
            }
        }
        if (!FIXES.isEmpty()) {
            fix(FIXES.get(0));
            if (FIXES.get(0).i >= FIXES.get(0).s.pos.length) {
                FIXES.remove(0);
            }
        }
    }

    private static ServerLevel levelOf(net.minecraft.server.MinecraftServer server, Object t) {
        // the wave's owner's level (its centre is in it); the overworld without him
        try {
            UUID owner = (UUID) t.getClass().getField("dono").get(t);
            var p = owner == null ? null : server.getPlayerList().getPlayer(owner);
            if (p != null) {
                return p.serverLevel();
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // the overworld
        }
        return server.overworld();
    }

    private static boolean permanent() {
        try {
            Object v = Class.forName("handofgod.Config").getField("REALIDADE_PERMANENTE").get(null);
            return v instanceof net.minecraftforge.common.ForgeConfigSpec.BooleanValue b && b.get();
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            return false;
        }
    }

    private static void fix(Fix f) {
        Seen s = f.s;
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        int end = Math.min(s.pos.length, f.i + BUDGET);
        for (; f.i < end; f.i++) {
            BlockState was = s.orig[f.i];
            BlockState put = s.fin[f.i];
            if (was == null || put == null || was.getBlock() == put.getBlock()) {
                continue;
            }
            at.set(s.pos[f.i]);
            if (!s.level.isLoaded(at)) {
                continue;
            }
            BlockState now = s.level.getBlockState(at);
            // still what the wave put (its kind: a leaf whose distance changed is still that leaf)
            if (now.getBlock() == put.getBlock()) {
                s.level.setBlock(at, was, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            }
        }
    }
}
