package com.flash.yas;

import com.flash.FlashMod;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.features.TreeFeatures;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * The Manipulation of Reality on any land:
 * <ul>
 * <li>in the Overworld Hand of God's wave rewrites the land into another biome; the biome it becomes is chosen from
 * where the hero stands (the desert gets a forest, the snow flowers, the savanna a dark forest, the plains a field
 * of flowers...), set on Hand of God's config as she walks;</li>
 * <li>where Hand of God cannot (the Nether, the End...) this wave of the mod does it: the dead ground turns to grass
 * and earth, lava to water, the fungi to trees and leaves, and grass, flowers and trees grow on it.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = FlashMod.MODID)
public final class YasRealityWave {

    private static final int RADIUS = 40;
    private static final int BAND = 20;
    private static final List<Wave> WAVES = new ArrayList<>();
    private static final Map<UUID, Long> LAST = new HashMap<>();
    private static String lastBiome;

    private static final class Wave {
        final ServerLevel level;
        final BlockPos center;
        final UUID owner;
        int r;
        /** What it changed: pos -> the block before (RealityJournal). */
        final it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<BlockState> journal =
                new it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<>();
        /** 0 spreading, 1 the land stays rewritten, 2 going back. */
        int phase;
        long until;
        java.util.List<Long>[] rings;
        int back;

        Wave(ServerLevel level, BlockPos center, UUID owner) {
            this.level = level;
            this.center = center;
            this.owner = owner;
        }
    }

    private YasRealityWave() {
    }

    // ------------------------------------------------------------------
    // The Overworld: the biome Hand of God writes, chosen from where she stands
    // ------------------------------------------------------------------

    /** The biome the land under her becomes. */
    public static String target(Holder<Biome> here) {
        ResourceKey<Biome> k = here.unwrapKey().orElse(Biomes.PLAINS);
        String id = k.location().getPath();
        if (id.contains("badlands")) {
            return "minecraft:jungle";
        }
        if (id.contains("desert")) {
            return "minecraft:forest";
        }
        if (id.contains("savanna")) {
            return "minecraft:dark_forest";
        }
        if (id.contains("snowy") || id.contains("frozen") || id.contains("ice") || id.contains("grove")
                || id.contains("jagged")) {
            return "minecraft:flower_forest";
        }
        if (id.contains("peaks") || id.contains("windswept") || id.contains("stony")) {
            return "minecraft:meadow";
        }
        if (id.contains("taiga")) {
            return "minecraft:meadow";
        }
        if (id.equals("dark_forest")) {
            return "minecraft:mushroom_fields";
        }
        if (id.contains("birch") || id.equals("meadow")) {
            return "minecraft:cherry_grove";
        }
        if (id.equals("flower_forest") || id.equals("cherry_grove")) {
            return "minecraft:forest";
        }
        if (id.equals("forest")) {
            return "minecraft:cherry_grove";
        }
        if (id.contains("jungle")) {
            return "minecraft:flower_forest";
        }
        if (id.contains("swamp")) {
            return "minecraft:jungle";
        }
        if (id.contains("mushroom")) {
            return "minecraft:plains";
        }
        if (id.contains("plains")) {
            return "minecraft:flower_forest";
        }
        if (id.contains("ocean") || id.contains("river") || id.contains("beach") || id.contains("shore")) {
            return "minecraft:plains";
        }
        return "minecraft:flower_forest";
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void setHogBiome(String biome) {
        if (biome.equals(lastBiome)) {
            return;
        }
        try {
            Object v = Class.forName("handofgod.Config").getField("REALIDADE_BIOMA").get(null);
            if (v instanceof net.minecraftforge.common.ForgeConfigSpec.ConfigValue cv) {
                cv.set(biome);
                lastBiome = biome;
            }
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            // no Hand of God
        }
    }

    // ------------------------------------------------------------------
    // Anywhere else: the mod's own wave
    // ------------------------------------------------------------------

    /** R on the ground where Hand of God cannot (not the Overworld): the wave starts at her feet. */
    public static void start(ServerPlayer p) {
        if (!(YasPowers.has(p) || com.flash.scarlet.ScarletPowers.has(p)) || p.level().dimension() == Level.OVERWORLD) {
            return;
        }
        long now = p.level().getGameTime();
        Long last = LAST.get(p.getUUID());
        if (last != null && now - last < 40L) {
            return;
        }
        for (Wave w : WAVES) {
            if (w.owner.equals(p.getUUID())) {
                return; // one at a time
            }
        }
        LAST.put(p.getUUID(), now);
        ServerLevel level = p.serverLevel();
        WAVES.add(new Wave(level, p.blockPosition(), p.getUUID()));
        YasReality.play(level, p.position(), "realidade_carga", SoundEvents.BEACON_ACTIVATE, 1.2F);
        YasReality.play(level, p.position(), "realidade_onda", SoundEvents.ILLUSIONER_CAST_SPELL, 0.8F);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        // the Overworld's target biome follows the hero (the last one who moved decides, every half second)
        if (event.getServer().getTickCount() % 10 == 0) {
            for (ServerPlayer p : event.getServer().getPlayerList().getPlayers()) {
                if (p.level().dimension() == Level.OVERWORLD
                        && (YasPowers.has(p) || com.flash.scarlet.ScarletPowers.has(p))) {
                    setHogBiome(target(p.level().getBiome(p.blockPosition())));
                }
            }
        }
        for (Iterator<Wave> it = WAVES.iterator(); it.hasNext(); ) {
            Wave w = it.next();
            if (w.phase == 0) {
                if (w.r >= RADIUS) {
                    if (permanent()) {
                        it.remove();
                        continue;
                    }
                    w.phase = 1;
                    w.until = w.level.getGameTime() + duration();
                    continue;
                }
                int from = w.r;
                w.r = Math.min(RADIUS, w.r + 1);
                RealityJournal.into = w.journal;
                try {
                    ring(w, from, w.r);
                } finally {
                    RealityJournal.into = null;
                }
            } else if (w.phase == 1) {
                if (w.level.getGameTime() >= w.until) {
                    startBack(w);
                }
            } else if (goBack(w)) {
                it.remove();
            }
        }
    }

    private static void ring(Wave w, int r0, int r1) {
        ServerLevel level = w.level;
        RandomSource rnd = level.random;
        double in2 = (double) r0 * r0;
        double out2 = (double) r1 * r1;
        ParticleOptions energy = energy();
        for (int dx = -r1; dx <= r1; dx++) {
            for (int dz = -r1; dz <= r1; dz++) {
                double d2 = dx * dx + dz * dz;
                if (d2 < in2 || d2 > out2) {
                    continue;
                }
                int x = w.center.getX() + dx;
                int z = w.center.getZ() + dz;
                column(level, rnd, x, z, w.center.getY());
                if (rnd.nextInt(6) == 0) {
                    level.sendParticles(energy, x + 0.5D, w.center.getY() + 1.0D + rnd.nextDouble() * 3.0D, z + 0.5D,
                            1, 0.2D, 0.4D, 0.2D, 0.02D);
                }
            }
        }
    }

    private static boolean permanent() {
        try {
            Object v = Class.forName("handofgod.Config").getField("REALIDADE_PERMANENTE").get(null);
            return v instanceof net.minecraftforge.common.ForgeConfigSpec.BooleanValue b && b.get();
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            return false;
        }
    }

    /** As long as Hand of God's own rewritten land lasts (its config), 60 s without it. */
    private static long duration() {
        try {
            Object v = Class.forName("handofgod.Config").getField("REALIDADE_DURACAO").get(null);
            if (v instanceof net.minecraftforge.common.ForgeConfigSpec.DoubleValue d) {
                return Math.max(20L, Math.round(d.get() * 20.0D));
            }
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            // no Hand of God
        }
        return 60L * 20L;
    }

    @SuppressWarnings("unchecked")
    private static void startBack(Wave w) {
        w.phase = 2;
        w.rings = new java.util.List[RADIUS + 2];
        for (int i = 0; i < w.rings.length; i++) {
            w.rings[i] = new ArrayList<>();
        }
        for (long key : w.journal.keySet()) {
            BlockPos p = BlockPos.of(key);
            int d = (int) Math.ceil(Math.sqrt(Math.pow(p.getX() - w.center.getX(), 2)
                    + Math.pow(p.getZ() - w.center.getZ(), 2)));
            w.rings[Math.min(w.rings.length - 1, d)].add(key);
        }
        w.back = w.rings.length - 1;
        YasReality.play(w.level, Vec3.atCenterOf(w.center), "realidade_desfeita", SoundEvents.ILLUSIONER_MIRROR_MOVE,
                0.8F);
    }

    /** The land going back, from the edge in (a ring a tick); true when all is as it was. */
    private static boolean goBack(Wave w) {
        if (w.back < 0) {
            return true;
        }
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        // the trees and plants first (from the top), then the ground
        java.util.List<Long> ring = w.rings[w.back];
        ring.sort((a, b) -> Integer.compare(BlockPos.getY(b), BlockPos.getY(a)));
        for (long key : ring) {
            pos.set(key);
            if (w.level.isLoaded(pos)) {
                w.level.setBlock(pos, w.journal.get(key), Block.UPDATE_CLIENTS);
            }
        }
        w.back--;
        return w.back < 0;
    }

    /** One column of the band around her height: rewritten from the top down. */
    private static void column(ServerLevel level, RandomSource rnd, int x, int z, int cy) {
        int depth = -1; // blocks of ground below the open sky of this column
        for (int y = cy + BAND; y >= cy - BAND; y--) {
            BlockPos pos = new BlockPos(x, y, z);
            BlockState s = level.getBlockState(pos);
            if (s.isAir()) {
                depth = -1;
                continue;
            }
            BlockState to = rewrite(s, depth < 0 ? 0 : depth + 1);
            if (to == null) {
                depth = s.blocksMotion() ? depth + 1 : -1;
                continue;
            }
            level.setBlock(pos, to, Block.UPDATE_CLIENTS);
            if (to.is(Blocks.GRASS_BLOCK) && depth < 0) {
                grow(level, rnd, pos.above());
            }
            depth = to.blocksMotion() ? (depth < 0 ? 0 : depth + 1) : -1;
        }
    }

    /** What a block of dead land becomes ({@code depth} 0 on the surface), or null to leave it. */
    private static BlockState rewrite(BlockState s, int depth) {
        Block b = s.getBlock();
        if (b == Blocks.NETHERRACK || b == Blocks.CRIMSON_NYLIUM || b == Blocks.WARPED_NYLIUM || b == Blocks.SOUL_SAND
                || b == Blocks.SOUL_SOIL || b == Blocks.BASALT || b == Blocks.SMOOTH_BASALT || b == Blocks.BLACKSTONE
                || b == Blocks.MAGMA_BLOCK || b == Blocks.END_STONE || b == Blocks.NETHER_QUARTZ_ORE
                || b == Blocks.NETHER_GOLD_ORE) {
            return depth == 0 ? Blocks.GRASS_BLOCK.defaultBlockState()
                    : depth <= 3 ? Blocks.DIRT.defaultBlockState() : Blocks.STONE.defaultBlockState();
        }
        if (b == Blocks.CRIMSON_STEM || b == Blocks.WARPED_STEM || b == Blocks.CRIMSON_HYPHAE
                || b == Blocks.WARPED_HYPHAE) {
            return Blocks.OAK_LOG.defaultBlockState();
        }
        if (b == Blocks.NETHER_WART_BLOCK || b == Blocks.WARPED_WART_BLOCK) {
            return Blocks.OAK_LEAVES.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true);
        }
        if (b == Blocks.LAVA) {
            return Blocks.WATER.defaultBlockState();
        }
        if (b == Blocks.FIRE || b == Blocks.SOUL_FIRE || b == Blocks.CRIMSON_ROOTS || b == Blocks.WARPED_ROOTS
                || b == Blocks.NETHER_SPROUTS || b == Blocks.CRIMSON_FUNGUS || b == Blocks.WARPED_FUNGUS
                || b == Blocks.WEEPING_VINES || b == Blocks.WEEPING_VINES_PLANT || b == Blocks.TWISTING_VINES
                || b == Blocks.TWISTING_VINES_PLANT || b == Blocks.CHORUS_PLANT || b == Blocks.CHORUS_FLOWER) {
            return Blocks.AIR.defaultBlockState();
        }
        return null;
    }

    private static final Block[] FLOWERS = {Blocks.POPPY, Blocks.DANDELION, Blocks.CORNFLOWER, Blocks.AZURE_BLUET,
            Blocks.OXEYE_DAISY, Blocks.ALLIUM, Blocks.BLUE_ORCHID};

    /** On fresh grass: grass, flowers, now and then a tree. */
    private static void grow(ServerLevel level, RandomSource rnd, BlockPos at) {
        if (!level.getBlockState(at).isAir()) {
            return;
        }
        float roll = rnd.nextFloat();
        if (roll < 0.014F) {
            ResourceKey<ConfiguredFeature<?, ?>> tree = rnd.nextInt(3) == 0 ? TreeFeatures.BIRCH
                    : rnd.nextInt(4) == 0 ? TreeFeatures.FANCY_OAK : TreeFeatures.OAK;
            level.registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE).getHolder(tree)
                    .ifPresent(h -> h.value().place(level, level.getChunkSource().getGenerator(), rnd, at));
        } else if (roll < 0.06F) {
            level.setBlock(at, FLOWERS[rnd.nextInt(FLOWERS.length)].defaultBlockState(), Block.UPDATE_CLIENTS);
        } else if (roll < 0.36F) {
            level.setBlock(at, Blocks.GRASS.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    private static ParticleOptions energy() {
        var t = ForgeRegistries.PARTICLE_TYPES.getValue(new ResourceLocation("hand_of_god", "energia"));
        return t instanceof ParticleOptions o ? o : ParticleTypes.HAPPY_VILLAGER;
    }

    static boolean isLeafy(BlockState s) {
        return s.is(BlockTags.LEAVES);
    }

    static Vec3 at(BlockPos p) {
        return Vec3.atCenterOf(p);
    }
}
