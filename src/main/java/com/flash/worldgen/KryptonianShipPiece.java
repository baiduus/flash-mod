package com.flash.worldgen;

import com.flash.registry.ModStructures;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EndRodBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

/**
 * The crashed Kryptonian pod: an elongated crystal hull (white quartz, calcite ribs, blue crystal windows and
 * fins), tilted nose-down in a scorched crater of coarse dirt, gravel and blackstone with embers, cracked open on
 * one side. The chest sits inside, lit by sea lanterns.
 */
public class KryptonianShipPiece extends StructurePiece {

    public static final ResourceLocation LOOT = new ResourceLocation("flash", "chests/kryptonian_ship");
    private static final int W = KryptonianShipStructure.WIDTH;
    private static final int D = KryptonianShipStructure.DEPTH;
    private static final int H = KryptonianShipStructure.HEIGHT;
    private static final int GROUND = 3;

    public KryptonianShipPiece(int centerX, int baseY, int centerZ, Direction orientation) {
        super(ModStructures.KRYPTONIAN_SHIP_PIECE.get(), 0, KryptonianShipStructure.box(centerX, baseY, centerZ,
                orientation));
        this.setOrientation(orientation);
    }

    public KryptonianShipPiece(CompoundTag tag) {
        super(ModStructures.KRYPTONIAN_SHIP_PIECE.get(), tag);
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
    }

    private static double hullCenterY(double x) {
        // nose (high x) dug lower into the ground
        return GROUND + 0.9D - (x - 7.0D) * 0.14D;
    }

    /** Ellipsoid value of the hull at a block (<= 1 inside). */
    private static double hull(int x, int y, int z) {
        double dx = (x - 7.0D) / 6.2D;
        double dy = (y - hullCenterY(x)) / 2.4D;
        double dz = (z - 4.0D) / 2.8D;
        return dx * dx + dy * dy + dz * dz;
    }

    private BlockState crater(RandomSource r) {
        int roll = r.nextInt(100);
        if (roll < 40) {
            return Blocks.COARSE_DIRT.defaultBlockState();
        } else if (roll < 62) {
            return Blocks.GRAVEL.defaultBlockState();
        } else if (roll < 82) {
            return Blocks.BLACKSTONE.defaultBlockState();
        } else if (roll < 92) {
            return Blocks.BASALT.defaultBlockState();
        } else if (roll < 97) {
            return Blocks.MAGMA_BLOCK.defaultBlockState();
        }
        return Blocks.NETHERRACK.defaultBlockState();
    }

    private BlockState shell(RandomSource r, int x) {
        if (x % 3 == 1) {
            return Blocks.CALCITE.defaultBlockState();
        }
        int roll = r.nextInt(100);
        if (roll < 55) {
            return Blocks.SMOOTH_QUARTZ.defaultBlockState();
        } else if (roll < 80) {
            return Blocks.QUARTZ_BRICKS.defaultBlockState();
        } else if (roll < 92) {
            return Blocks.WHITE_CONCRETE.defaultBlockState();
        }
        return Blocks.DIORITE.defaultBlockState();
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structures, ChunkGenerator generator,
                            RandomSource random, BoundingBox box, ChunkPos chunkPos, BlockPos pivot) {
        // 1. the crater: a scorched bowl, a raised rim, embers here and there
        for (int x = 0; x < W; x++) {
            for (int z = 0; z < D; z++) {
                double ex = (x - 7.0D) / 7.6D;
                double ez = (z - 4.0D) / 4.6D;
                double d = Math.sqrt(ex * ex + ez * ez);
                if (d > 1.05D) {
                    continue;
                }
                int floor = GROUND - (int) Math.round(1.6D * Math.max(0.0D, 1.0D - d * d));
                for (int y = floor + 1; y < H; y++) {
                    this.placeBlock(level, Blocks.AIR.defaultBlockState(), x, y, z, box);
                }
                for (int y = floor; y >= 0; y--) {
                    this.placeBlock(level, y == floor ? this.crater(random) : Blocks.DIRT.defaultBlockState(),
                            x, y, z, box);
                }
                if (d > 0.82D && random.nextInt(3) > 0) {
                    this.placeBlock(level, random.nextBoolean() ? Blocks.COARSE_DIRT.defaultBlockState()
                            : Blocks.GRAVEL.defaultBlockState(), x, GROUND + 1, z, box);
                } else if (random.nextInt(14) == 0 && d < 0.8D) {
                    this.placeBlock(level, Blocks.NETHERRACK.defaultBlockState(), x, floor, z, box);
                    this.placeBlock(level, Blocks.FIRE.defaultBlockState(), x, floor + 1, z, box);
                }
            }
        }
        // 2. the hull
        for (int x = 0; x < W; x++) {
            for (int y = 0; y < H; y++) {
                for (int z = 0; z < D; z++) {
                    double v = hull(x, y, z);
                    if (v > 1.0D) {
                        continue;
                    }
                    if (v < 0.5D) {
                        this.placeBlock(level, Blocks.AIR.defaultBlockState(), x, y, z, box);
                        continue;
                    }
                    double cy = hullCenterY(x);
                    // torn open on the top and on one side (the way in)
                    boolean hole = (y > cy + 1.2D && random.nextInt(100) < 18)
                            || (z <= 2 && x >= 6 && x <= 8 && y >= Math.round(cy) - 1 && y <= Math.round(cy) + 1);
                    if (hole) {
                        this.placeBlock(level, Blocks.AIR.defaultBlockState(), x, y, z, box);
                        continue;
                    }
                    boolean window = Math.abs(y - cy - 0.4D) < 0.6D && x >= 3 && x <= 11 && x % 2 == 0;
                    this.placeBlock(level, window ? Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState()
                            : this.shell(random, x), x, y, z, box);
                }
            }
        }
        // 3. crystal fins on the back and a blue crystal tip
        for (int fx : new int[] {2, 3}) {
            int top = (int) Math.round(hullCenterY(fx) + 2.2D);
            this.placeBlock(level, Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState(), fx, top, 4, box);
            this.placeBlock(level, Blocks.END_ROD.defaultBlockState().setValue(EndRodBlock.FACING, Direction.UP),
                    fx, top + 1, 4, box);
            this.placeBlock(level, Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState(), fx, (int) Math.round(hullCenterY(fx)),
                    0, box);
            this.placeBlock(level, Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState(), fx, (int) Math.round(hullCenterY(fx)),
                    8, box);
        }
        this.placeBlock(level, Blocks.AMETHYST_BLOCK.defaultBlockState(), 0, (int) Math.round(hullCenterY(0)), 4, box);
        // 4. inside: floor, lights, debris and the chest
        int floorY = (int) Math.floor(hullCenterY(7) - 1.0D);
        for (int x = 3; x <= 11; x++) {
            for (int z = 2; z <= 6; z++) {
                if (hull(x, floorY, z) < 0.5D) {
                    this.placeBlock(level, Blocks.POLISHED_DIORITE.defaultBlockState(), x, floorY - 1, z, box);
                }
            }
        }
        this.placeBlock(level, Blocks.SEA_LANTERN.defaultBlockState(), 5, floorY - 1, 4, box);
        this.placeBlock(level, Blocks.SEA_LANTERN.defaultBlockState(), 9, floorY - 1, 4, box);
        this.placeBlock(level, Blocks.IRON_BARS.defaultBlockState(), 10, floorY, 3, box);
        this.placeBlock(level, Blocks.CHAIN.defaultBlockState(), 4, floorY, 5, box);
        this.placeBlock(level, Blocks.LIGHT_BLUE_STAINED_GLASS_PANE.defaultBlockState(), 9, floorY, 5, box);
        this.createChest(level, box, random, 7, floorY, 5, LOOT);
        // 5. debris thrown out behind the pod
        for (int i = 0; i < 6; i++) {
            int x = random.nextInt(3);
            int z = 1 + random.nextInt(D - 2);
            BlockState piece = switch (random.nextInt(4)) {
                case 0 -> Blocks.QUARTZ_SLAB.defaultBlockState();
                case 1 -> Blocks.IRON_BARS.defaultBlockState();
                case 2 -> Blocks.LIGHT_BLUE_STAINED_GLASS_PANE.defaultBlockState();
                default -> Blocks.CALCITE.defaultBlockState();
            };
            this.placeBlock(level, piece, x, GROUND + 1, z, box);
        }
    }
}
