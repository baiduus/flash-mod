package com.flash.worldgen;

import com.flash.registry.ModEntities;
import com.flash.registry.ModStructures;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EndRodBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

/**
 * The floating island of the transcendence, made from nothing:
 * <ul>
 * <li>a round island of grass about 31 blocks across, its edge broken by noise, over a long hanging root of stone,
 * calcite and deepslate down to a point 22 blocks below, crossed by veins of gold and amethyst, glowstone and
 * light crystals hanging from it;</li>
 * <li>small rocks floating around it;</li>
 * <li>on top a sanctuary: a round floor of quartz and calcite with a ring of gold, eight pillars of light, white
 * and yellow flowers around it, a raised altar of chiseled quartz in the middle where the Guardian of the
 * Transcendence floats.</li>
 * </ul>
 */
public class YasIslandPiece extends StructurePiece {

    /** Local y of the grass. */
    static final int TOP = 26;
    private static final int S = YasIslandStructure.SIZE;
    private static final int H = YasIslandStructure.HEIGHT;
    private static final double C = (S - 1) / 2.0D;
    private static final double RADIUS = 15.5D;
    private static final double DEPTH = 22.0D;

    public YasIslandPiece(int centerX, int baseY, int centerZ) {
        super(ModStructures.YAS_ISLAND_PIECE.get(), 0, YasIslandStructure.box(centerX, baseY, centerZ));
        this.setOrientation(Direction.NORTH);
    }

    public YasIslandPiece(CompoundTag tag) {
        super(ModStructures.YAS_ISLAND_PIECE.get(), tag);
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
    }

    /** A smooth wobble of the edge (the same in every chunk: no random here). */
    private static double edge(double angle, long salt) {
        double a = angle + (salt & 0xFF) * 0.01D;
        return 1.0D + 0.09D * Math.sin(a * 3.0D) + 0.06D * Math.sin(a * 7.0D + 1.3D) + 0.04D * Math.sin(a * 11.0D);
    }

    private static double hash(int x, int y, int z) {
        long h = x * 73856093L ^ y * 19349663L ^ z * 83492791L;
        h ^= h >>> 13;
        h *= 0x5bd1e995L;
        h ^= h >>> 15;
        return (h & 0xFFFF) / 65535.0D;
    }

    private static BlockState rock(int x, int y, int z, double depthFrac) {
        double n = hash(x, y, z);
        if (n < 0.03D) {
            return Blocks.GOLD_ORE.defaultBlockState();
        }
        if (n < 0.05D) {
            return Blocks.AMETHYST_BLOCK.defaultBlockState();
        }
        if (depthFrac > 0.55D) {
            return n < 0.55D ? Blocks.DEEPSLATE.defaultBlockState() : Blocks.CALCITE.defaultBlockState();
        }
        return n < 0.5D ? Blocks.STONE.defaultBlockState() : n < 0.8D ? Blocks.CALCITE.defaultBlockState()
                : Blocks.ANDESITE.defaultBlockState();
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structures, ChunkGenerator generator,
                            RandomSource random, BoundingBox box, ChunkPos chunkPos, BlockPos pivot) {
        long salt = this.boundingBox.minX() * 341873128712L + this.boundingBox.minZ() * 132897987541L;
        // 1. the island: grass on top, the hanging root of stone below
        for (int x = 0; x < S; x++) {
            for (int z = 0; z < S; z++) {
                double dx = x - C;
                double dz = z - C;
                double r = Math.sqrt(dx * dx + dz * dz);
                double edgeR = RADIUS * edge(Math.atan2(dz, dx), salt);
                if (r > edgeR) {
                    continue;
                }
                double f = r / edgeR;
                // depth of the root: deep in the middle, thin at the edge, a little uneven
                int depth = (int) Math.round(DEPTH * Math.pow(1.0D - f, 1.6D) + 2.0D + hash(x, 0, z) * 2.0D);
                for (int y = TOP - depth; y <= TOP; y++) {
                    BlockState b;
                    if (y == TOP) {
                        b = Blocks.GRASS_BLOCK.defaultBlockState();
                    } else if (y >= TOP - 2) {
                        b = Blocks.DIRT.defaultBlockState();
                    } else {
                        b = rock(x, y, z, (TOP - y) / DEPTH);
                    }
                    this.placeBlock(level, b, x, y, z, box);
                }
                // hanging light: glowstone and amethyst under the root
                double h = hash(x, 7, z);
                if (h < 0.035D && f < 0.8D) {
                    this.placeBlock(level, Blocks.GLOWSTONE.defaultBlockState(), x, TOP - depth - 1, z, box);
                } else if (h < 0.06D && f < 0.9D) {
                    this.placeBlock(level, Blocks.AMETHYST_CLUSTER.defaultBlockState()
                            .setValue(net.minecraft.world.level.block.AmethystClusterBlock.FACING, Direction.DOWN),
                            x, TOP - depth - 1, z, box);
                }
                // flowers on the grass outside the sanctuary
                if (r > 9.5D && r < edgeR - 1.0D) {
                    double fl = hash(x, 3, z);
                    BlockState flower = fl < 0.08D ? Blocks.OXEYE_DAISY.defaultBlockState()
                            : fl < 0.14D ? Blocks.DANDELION.defaultBlockState()
                            : fl < 0.18D ? Blocks.LILY_OF_THE_VALLEY.defaultBlockState()
                            : fl < 0.42D ? Blocks.GRASS.defaultBlockState() : null;
                    if (flower != null) {
                        this.placeBlock(level, flower, x, TOP + 1, z, box);
                    }
                }
            }
        }
        // 2. small rocks floating around it
        for (int i = 0; i < 7; i++) {
            double a = i * Math.PI * 2.0D / 7.0D + (salt & 0xF) * 0.3D;
            double dist = 19.0D + hash(i, 1, 2) * 3.5D;
            int rx = (int) Math.round(C + Math.cos(a) * dist);
            int rz = (int) Math.round(C + Math.sin(a) * dist);
            int ry = TOP - 4 - (int) (hash(i, 2, 3) * 12.0D);
            int size = 1 + (int) (hash(i, 3, 4) * 2.0D);
            for (int x = -size; x <= size; x++) {
                for (int y = -size; y <= 0; y++) {
                    for (int z = -size; z <= size; z++) {
                        if (x * x + z * z + y * y * 2 <= size * size + 1) {
                            this.placeBlock(level, y == 0 ? Blocks.MOSS_BLOCK.defaultBlockState()
                                    : rock(rx + x, ry + y, rz + z, 0.3D), rx + x, ry + y, rz + z, box);
                        }
                    }
                }
            }
        }
        // 3. the sanctuary
        int c = (int) C;
        for (int x = -8; x <= 8; x++) {
            for (int z = -8; z <= 8; z++) {
                double r = Math.sqrt(x * x + z * z);
                if (r > 8.4D) {
                    continue;
                }
                BlockState floor = r > 7.4D ? Blocks.GOLD_BLOCK.defaultBlockState()
                        : (Math.round(r) % 2 == 0) ? Blocks.SMOOTH_QUARTZ.defaultBlockState()
                        : Blocks.CALCITE.defaultBlockState();
                this.placeBlock(level, floor, c + x, TOP, c + z, box);
                for (int y = TOP + 1; y < H; y++) {
                    this.placeBlock(level, Blocks.AIR.defaultBlockState(), c + x, y, c + z, box);
                }
            }
        }
        // eight pillars of light
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4.0D;
            int px = c + (int) Math.round(Math.cos(a) * 6.5D);
            int pz = c + (int) Math.round(Math.sin(a) * 6.5D);
            for (int y = TOP + 1; y <= TOP + 4; y++) {
                this.placeBlock(level, y == TOP + 4 ? Blocks.CHISELED_QUARTZ_BLOCK.defaultBlockState()
                        : Blocks.QUARTZ_PILLAR.defaultBlockState(), px, y, pz, box);
            }
            this.placeBlock(level, Blocks.END_ROD.defaultBlockState().setValue(EndRodBlock.FACING, Direction.UP),
                    px, TOP + 5, pz, box);
            this.placeBlock(level, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, false),
                    px, TOP + 1, pz + (pz > c ? -1 : pz < c ? 1 : 0), box);
        }
        // the altar: two steps of quartz and a glowing heart
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                this.placeBlock(level, Blocks.QUARTZ_BRICKS.defaultBlockState(), c + x, TOP + 1, c + z, box);
            }
        }
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                this.placeBlock(level, x == 0 && z == 0 ? Blocks.SEA_LANTERN.defaultBlockState()
                        : Blocks.CHISELED_QUARTZ_BLOCK.defaultBlockState(), c + x, TOP + 2, c + z, box);
            }
        }
        // 4. the Guardian, once (in the chunk holding the altar)
        BlockPos altar = this.getWorldPos(c, TOP + 3, c);
        if (box.isInside(altar)) {
            var guardian = ModEntities.YAS_GUARDIAN.get().create(level.getLevel());
            if (guardian != null) {
                guardian.moveTo(altar.getX() + 0.5D, altar.getY(), altar.getZ() + 0.5D, random.nextFloat() * 360.0F,
                        0.0F);
                level.addFreshEntity(guardian);
            }
        }
    }
}
