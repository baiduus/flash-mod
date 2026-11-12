package com.flash.worldgen;

import com.flash.registry.ModBlocks;
import com.flash.registry.ModStructures;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

/**
 * Mjolnir's impact crater: a bowl about 13 blocks across and 4 deep (twice a TNT blast), lined with blackstone,
 * basalt, coarse dirt and gravel, glowing magma cracks running out from the centre, a few fires and a thrown-up
 * rim; scorched streaks spread on the ground around it. At the bottom, on a cracked obsidian heart, the hammer.
 */
public class ThorCraterPiece extends StructurePiece {

    static final int GROUND = 6;
    private static final int S = ThorCraterStructure.SIZE;
    private static final int H = ThorCraterStructure.HEIGHT;
    private static final double C = (S - 1) / 2.0D;
    private static final double RADIUS = 6.6D;
    private static final double DEPTH = 4.2D;

    public ThorCraterPiece(int centerX, int baseY, int centerZ) {
        super(ModStructures.THOR_CRATER_PIECE.get(), 0, ThorCraterStructure.box(centerX, baseY, centerZ));
        this.setOrientation(Direction.NORTH);
    }

    public ThorCraterPiece(CompoundTag tag) {
        super(ModStructures.THOR_CRATER_PIECE.get(), tag);
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
    }

    private static BlockState lining(RandomSource r, double d) {
        int roll = r.nextInt(100);
        if (d < 0.35D) {
            // the heart of the impact: molten
            return roll < 45 ? Blocks.BLACKSTONE.defaultBlockState() : roll < 70 ? Blocks.BASALT.defaultBlockState()
                    : roll < 88 ? Blocks.MAGMA_BLOCK.defaultBlockState() : Blocks.OBSIDIAN.defaultBlockState();
        }
        return roll < 30 ? Blocks.COARSE_DIRT.defaultBlockState() : roll < 50 ? Blocks.GRAVEL.defaultBlockState()
                : roll < 72 ? Blocks.BLACKSTONE.defaultBlockState() : roll < 86 ? Blocks.BASALT.defaultBlockState()
                : roll < 94 ? Blocks.TUFF.defaultBlockState() : Blocks.MAGMA_BLOCK.defaultBlockState();
    }

    /** Glowing cracks: six rays out from the centre. */
    private static boolean crack(double dx, double dz, long salt) {
        double a = Math.atan2(dz, dx);
        for (int i = 0; i < 6; i++) {
            double ray = i * Math.PI / 3.0D + ((salt >> (i * 3)) & 7) * 0.08D;
            double diff = Math.abs(Math.atan2(Math.sin(a - ray), Math.cos(a - ray)));
            if (diff < 0.09D) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structures, ChunkGenerator generator,
                            RandomSource random, BoundingBox box, ChunkPos chunkPos, BlockPos pivot) {
        long salt = this.boundingBox.minX() * 31L + this.boundingBox.minZ();
        for (int x = 0; x < S; x++) {
            for (int z = 0; z < S; z++) {
                double dx = x - C;
                double dz = z - C;
                double d = Math.sqrt(dx * dx + dz * dz) / RADIUS;
                if (d > 1.25D) {
                    continue;
                }
                if (d > 1.0D) {
                    // scorched streaks on the ground around the crater
                    if (random.nextInt(3) == 0) {
                        this.placeBlock(level, random.nextBoolean() ? Blocks.COARSE_DIRT.defaultBlockState()
                                : Blocks.GRAVEL.defaultBlockState(), x, GROUND - 1, z, box);
                    }
                    continue;
                }
                int floor = GROUND - 1 - (int) Math.round(DEPTH * (1.0D - d * d));
                for (int y = floor + 1; y < H; y++) {
                    this.placeBlock(level, Blocks.AIR.defaultBlockState(), x, y, z, box);
                }
                for (int y = floor; y >= 0; y--) {
                    BlockState b = y == floor ? (crack(dx, dz, salt) && d > 0.15D ? Blocks.MAGMA_BLOCK.defaultBlockState()
                            : lining(random, d)) : y >= floor - 1 ? Blocks.BLACKSTONE.defaultBlockState()
                            : Blocks.STONE.defaultBlockState();
                    this.placeBlock(level, b, x, y, z, box);
                }
                // the rim thrown up by the impact
                if (d > 0.84D && random.nextInt(4) > 0) {
                    this.placeBlock(level, lining(random, 1.0D), x, GROUND, z, box);
                    if (d < 0.95D && random.nextInt(4) == 0) {
                        this.placeBlock(level, Blocks.COARSE_DIRT.defaultBlockState(), x, GROUND + 1, z, box);
                    }
                } else if (d > 0.2D && d < 0.8D && random.nextInt(22) == 0) {
                    this.placeBlock(level, Blocks.NETHERRACK.defaultBlockState(), x, floor, z, box);
                    this.placeBlock(level, Blocks.FIRE.defaultBlockState(), x, floor + 1, z, box);
                }
            }
        }
        // the heart: cracked obsidian and crying obsidian, the hammer standing in it
        int c = (int) C;
        int bottom = GROUND - 1 - (int) Math.round(DEPTH);
        for (int x = c - 1; x <= c + 1; x++) {
            for (int z = c - 1; z <= c + 1; z++) {
                this.placeBlock(level, (x + z) % 2 == 0 ? Blocks.CRYING_OBSIDIAN.defaultBlockState()
                        : Blocks.OBSIDIAN.defaultBlockState(), x, bottom, z, box);
                this.placeBlock(level, Blocks.AIR.defaultBlockState(), x, bottom + 1, z, box);
            }
        }
        this.placeBlock(level, ModBlocks.MJOLNIR_STONE.get().defaultBlockState(), c, bottom + 1, c, box);
    }
}
