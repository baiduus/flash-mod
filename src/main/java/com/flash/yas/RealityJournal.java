package com.flash.yas;

import com.flash.FlashMod;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Makes the Manipulation of Reality give the land back exactly as it was.
 * <p>
 * Hand of God's wave rewrites the land and later restores what it recorded; the trees it grew can reach past what
 * it restores (their crowns stayed in the air when the trunks went back). So while one of its waves is alive, every
 * tree, leaf and plant its code puts in the world is noted here (where, what was there before, what it put); when
 * no wave is left, whatever of those is still standing is put back as it was.
 * <p>
 * The mod's own wave (the Nether and the End, YasRealityWave) records everything it changes the same way, into
 * the wave being drawn ({@link #into}), and undoes it when its time is over.
 */
@Mod.EventBusSubscriber(modid = FlashMod.MODID)
public final class RealityJournal {

    /** Hand of God has a reality wave alive (polled each tick). */
    private static volatile boolean hogActive;
    /** One of its waves is going back (what it writes then is the old land, not growth). */
    private static volatile boolean hogUndoing;
    private static Thread serverThread;
    /** Hand of God's growth: pos -> {before, put}. */
    private static final Long2ObjectOpenHashMap<BlockState[]> GROWN = new Long2ObjectOpenHashMap<>();
    private static ServerLevel grownLevel;
    /** The mod's wave being drawn now (its journal), or null. */
    static Long2ObjectOpenHashMap<BlockState> into;

    private RealityJournal() {
    }

    /** From the mixin, before every block change of a chunk. */
    public static void record(LevelChunk chunk, BlockPos pos, BlockState state) {
        if (Thread.currentThread() != serverThread || !(chunk.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (into != null) {
            into.putIfAbsent(pos.asLong(), chunk.getBlockState(pos));
            return;
        }
        if (!hogActive || !grows(state)) {
            return;
        }
        boolean fromReality = StackWalker.getInstance().walk(s -> s.limit(64)
                .anyMatch(f -> f.getClassName().startsWith("handofgod.realidade")));
        if (!fromReality) {
            return;
        }
        long key = pos.asLong();
        BlockState[] seen = GROWN.get(key);
        if (seen == null) {
            if (hogUndoing) {
                return;
            }
            GROWN.put(key, new BlockState[] {chunk.getBlockState(pos), state});
            grownLevel = level;
        } else {
            seen[1] = state;
        }
    }

    /** What a wave grows: trees, leaves, plants, vines (not the ground itself). */
    private static boolean grows(BlockState s) {
        return s.is(BlockTags.LEAVES) || s.is(BlockTags.LOGS) || s.is(BlockTags.FLOWERS) || s.is(BlockTags.SAPLINGS)
                || s.is(BlockTags.REPLACEABLE_BY_TREES) || s.getBlock() instanceof net.minecraft.world.level.block.VineBlock
                || s.getBlock() instanceof net.minecraft.world.level.block.HugeMushroomBlock
                || s.getBlock() instanceof net.minecraft.world.level.block.CocoaBlock
                || s.getBlock() instanceof net.minecraft.world.level.block.BeehiveBlock
                || s.is(net.minecraft.world.level.block.Blocks.MANGROVE_ROOTS)
                || s.is(net.minecraft.world.level.block.Blocks.MOSS_CARPET);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.START) {
            return;
        }
        serverThread = Thread.currentThread();
        boolean active = hogWaves();
        if (hogActive && !active && !GROWN.isEmpty()) {
            restoreGrown();
        }
        hogActive = active;
    }

    private static boolean hogWaves() {
        try {
            Object list = Class.forName("handofgod.realidade.Realidade").getMethod("ativas").invoke(null);
            if (!(list instanceof List<?> l) || l.isEmpty()) {
                hogUndoing = false;
                return false;
            }
            boolean undoing = false;
            for (Object t : l) {
                Object phase = t.getClass().getField("fase").get(t);
                if (phase != null && "DESFAZENDO".equals(phase.toString())) {
                    undoing = true;
                }
            }
            hogUndoing = undoing;
            Object permanent = Class.forName("handofgod.Config").getField("REALIDADE_PERMANENTE").get(null);
            return !(permanent instanceof net.minecraftforge.common.ForgeConfigSpec.BooleanValue b && b.get());
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            return false;
        }
    }

    /** All waves gone: what they grew and is still there goes back to what was there. */
    private static void restoreGrown() {
        ServerLevel level = grownLevel;
        if (level != null) {
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            for (var en : GROWN.long2ObjectEntrySet()) {
                pos.set(en.getLongKey());
                BlockState[] seen = en.getValue();
                if (level.isLoaded(pos) && level.getBlockState(pos) == seen[1]) {
                    level.setBlock(pos, seen[0], Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                }
            }
        }
        GROWN.clear();
        grownLevel = null;
    }
}
