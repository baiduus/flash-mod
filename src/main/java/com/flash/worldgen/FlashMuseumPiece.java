package com.flash.worldgen;

import com.flash.registry.ModStructures;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

/**
 * Builds the Flash Museum block by block. Coordinates are written for a
 * building whose entrance faces south (canonical frame: x to the right, z from
 * the entrance towards the back, y = 0 is the foundation row); the base class
 * rotates / mirrors the result for the chosen orientation.
 *
 * <pre>
 *   z 0-2    facade tower with the lightning emblem and the entrance
 *   z 3-8    lobby with reception desk
 *   z 9-22   hall: suit gallery (left), rogues gallery (right), red carpet in the middle
 *   z 23-24  wall with the golden archway
 *   z 25-29  Suit Room: pedestal and chest
 * </pre>
 */
public class FlashMuseumPiece extends StructurePiece {

    public static final ResourceLocation SUIT_LOOT =
            new ResourceLocation("flash", "chests/flash_museum_suit");

    private static final int W = FlashMuseumStructure.WIDTH;   // 33
    private static final int D = FlashMuseumStructure.DEPTH;   // 31
    private static final int FLOOR = 1;
    private static final int CEILING = 10;

    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private static final BlockState RED = Blocks.RED_CONCRETE.defaultBlockState();
    private static final BlockState DARK_RED = Blocks.RED_TERRACOTTA.defaultBlockState();
    private static final BlockState YELLOW = Blocks.YELLOW_CONCRETE.defaultBlockState();
    private static final BlockState WHITE = Blocks.WHITE_CONCRETE.defaultBlockState();
    private static final BlockState GRAY = Blocks.GRAY_CONCRETE.defaultBlockState();
    private static final BlockState BLACK = Blocks.BLACK_CONCRETE.defaultBlockState();
    private static final BlockState QUARTZ = Blocks.SMOOTH_QUARTZ.defaultBlockState();
    private static final BlockState GOLD = Blocks.GOLD_BLOCK.defaultBlockState();
    private static final BlockState GLASS = Blocks.GLASS.defaultBlockState();
    private static final BlockState LANTERN = Blocks.SEA_LANTERN.defaultBlockState();
    private static final BlockState STONE_BRICKS = Blocks.STONE_BRICKS.defaultBlockState();
    private static final BlockState BLACKSTONE = Blocks.POLISHED_BLACKSTONE.defaultBlockState();
    private static final BlockState CARPET = Blocks.RED_CARPET.defaultBlockState();
    private static final BlockState ROD = Blocks.LIGHTNING_ROD.defaultBlockState();
    private static final BlockState END_ROD = Blocks.END_ROD.defaultBlockState();

    public FlashMuseumPiece(int centerX, int baseY, int centerZ, Direction orientation) {
        super(ModStructures.FLASH_MUSEUM_PIECE.get(), 0,
                FlashMuseumStructure.box(centerX, baseY, centerZ, orientation));
        this.setOrientation(orientation);
    }

    public FlashMuseumPiece(CompoundTag tag) {
        super(ModStructures.FLASH_MUSEUM_PIECE.get(), tag);
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context,
                                         CompoundTag tag) {
    }

    // ------------------------------------------------------------------
    // Small helpers
    // ------------------------------------------------------------------

    private void fill(WorldGenLevel level, BoundingBox box, int x0, int y0, int z0,
                      int x1, int y1, int z1, BlockState state) {
        this.generateBox(level, box, x0, y0, z0, x1, y1, z1, state, state, false);
    }

    private void put(WorldGenLevel level, BoundingBox box, BlockState state, int x, int y, int z) {
        this.placeBlock(level, state, x, y, z, box);
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structures, ChunkGenerator generator,
                            RandomSource random, BoundingBox box, ChunkPos chunkPos, BlockPos pivot) {
        // ---- foundation, floor and a clear volume (hills and trees would otherwise bury it)
        for (int x = 0; x < W; x++) {
            for (int z = 0; z < D; z++) {
                this.fillColumnDown(level, STONE_BRICKS, x, 0, z, box);
            }
        }
        this.generateAirBox(level, box, 0, FLOOR, 0, W - 1, FlashMuseumStructure.HEIGHT - 1, D - 1);
        fill(level, box, 0, 0, 0, W - 1, 0, D - 1, STONE_BRICKS);

        shell(level, box);
        floor(level, box);
        facade(level, box);
        lobby(level, box);
        hall(level, box);
        archway(level, box);
        suitRoom(level, box, random);
        lighting(level, box);
    }

    // ------------------------------------------------------------------
    // Structure
    // ------------------------------------------------------------------

    private void shell(WorldGenLevel level, BoundingBox box) {
        // red walls with a yellow band, flat gray roof with a red parapet
        fill(level, box, 0, FLOOR, 0, W - 1, CEILING, D - 1, RED);
        this.generateAirBox(level, box, 1, FLOOR + 1, 1, W - 2, CEILING - 1, D - 2);
        fill(level, box, 0, CEILING, 0, W - 1, CEILING, D - 1, GRAY);
        fill(level, box, 0, CEILING + 1, 0, W - 1, CEILING + 1, 0, RED);
        fill(level, box, 0, CEILING + 1, D - 1, W - 1, CEILING + 1, D - 1, RED);
        fill(level, box, 0, CEILING + 1, 0, 0, CEILING + 1, D - 1, RED);
        fill(level, box, W - 1, CEILING + 1, 0, W - 1, CEILING + 1, D - 1, RED);
        // yellow speed band around the building
        fill(level, box, 0, 9, 0, W - 1, 9, 0, YELLOW);
        fill(level, box, 0, 9, D - 1, W - 1, 9, D - 1, YELLOW);
        fill(level, box, 0, 9, 0, 0, 9, D - 1, YELLOW);
        fill(level, box, W - 1, 9, 0, W - 1, 9, D - 1, YELLOW);
        // dark skirting
        fill(level, box, 0, FLOOR + 1, 0, W - 1, FLOOR + 1, 0, BLACK);
        fill(level, box, 0, FLOOR + 1, D - 1, W - 1, FLOOR + 1, D - 1, BLACK);
        fill(level, box, 0, FLOOR + 1, 0, 0, FLOOR + 1, D - 1, BLACK);
        fill(level, box, W - 1, FLOOR + 1, 0, W - 1, FLOOR + 1, D - 1, BLACK);
        // big windows: front wings and both sides
        fill(level, box, 2, 4, 0, 7, 8, 0, GLASS);
        fill(level, box, W - 8, 4, 0, W - 3, 8, 0, GLASS);
        for (int z : new int[] {5, 12, 19}) {
            fill(level, box, 0, 4, z, 0, 8, z + 3, GLASS);
            fill(level, box, W - 1, 4, z, W - 1, 8, z + 3, GLASS);
        }
    }

    private void floor(WorldGenLevel level, BoundingBox box) {
        // checkered quartz / gray floor, a dark ring and a red runner down the middle
        for (int x = 1; x < W - 1; x++) {
            for (int z = 1; z < D - 1; z++) {
                put(level, box, ((x + z) & 1) == 0 ? QUARTZ : GRAY, x, FLOOR, z);
            }
        }
        fill(level, box, 15, FLOOR, 1, 17, FLOOR, 22, BLACK);
        fill(level, box, 15, FLOOR + 1, 1, 17, FLOOR + 1, 22, CARPET);
        fill(level, box, 14, FLOOR, 1, 14, FLOOR, 22, YELLOW);
        fill(level, box, 18, FLOOR, 1, 18, FLOOR, 22, YELLOW);
    }

    /** Red tower on the front with the Flash emblem and the entrance. */
    private void facade(WorldGenLevel level, BoundingBox box) {
        fill(level, box, 9, FLOOR, 0, 23, 22, 2, RED);
        fill(level, box, 9, 22, 0, 23, 22, 2, YELLOW);
        fill(level, box, 9, FLOOR + 1, 0, 23, FLOOR + 1, 2, BLACK);
        // entrance opening, framed in gold
        this.generateAirBox(level, box, 14, FLOOR + 1, 0, 18, 6, 2);
        fill(level, box, 13, FLOOR + 1, 0, 13, 7, 0, GOLD);
        fill(level, box, 19, FLOOR + 1, 0, 19, 7, 0, GOLD);
        fill(level, box, 13, 7, 0, 19, 7, 0, GOLD);
        // entrance steps are not needed: the floor is flush with the ground

        // emblem: white disc, yellow ring and bolt, centred on x = 16
        boolean mirrored = this.getOrientation() == Direction.SOUTH || this.getOrientation() == Direction.WEST;
        int cx = 16;
        int cy = 15;
        int[][] bolt = {
                {2, 5}, {3, 5}, {1, 4}, {2, 4}, {3, 4}, {0, 3}, {1, 3}, {2, 3}, {-1, 2}, {0, 2}, {1, 2},
                {-2, 1}, {-1, 1}, {0, 1}, {1, 1}, {2, 1}, {3, 1}, {0, 0}, {1, 0}, {2, 0}, {-1, -1}, {0, -1},
                {1, -1}, {-2, -2}, {-1, -2}, {0, -2}, {-3, -3}, {-2, -3}, {-1, -3}, {-3, -4}, {-2, -4}, {-4, -5}
        };
        for (int dx = -7; dx <= 7; dx++) {
            for (int dy = -7; dy <= 7; dy++) {
                double dist = Math.sqrt(dx * dx + dy * dy);
                BlockState state = null;
                if (dist <= 7.2D) {
                    state = dist > 6.1D ? YELLOW : WHITE;
                }
                if (state == null) {
                    continue;
                }
                put(level, box, state, mirrored ? W - 1 - (cx + dx) : cx + dx, cy + dy, 0);
            }
        }
        for (int[] cell : bolt) {
            int bx = cx + cell[0];
            put(level, box, Blocks.ORANGE_CONCRETE.defaultBlockState(), mirrored ? W - 1 - bx : bx, cy + cell[1], 0);
        }
        for (int[] cell : bolt) {
            // a slightly smaller yellow core so the bolt reads as gold with an orange edge
            if ((cell[0] + cell[1]) % 2 != 0 || cell[1] < -4) {
                continue;
            }
            int bx = cx + cell[0];
            put(level, box, YELLOW, mirrored ? W - 1 - bx : bx, cy + cell[1], 0);
        }
        // lightning rods on the top corners of the tower
        put(level, box, ROD, 9, 23, 1);
        put(level, box, ROD, 23, 23, 1);
        put(level, box, ROD, 16, 23, 1);
    }

    private void lobby(WorldGenLevel level, BoundingBox box) {
        // reception desk on the right
        fill(level, box, 21, FLOOR + 1, 6, 27, FLOOR + 1, 6, QUARTZ);
        fill(level, box, 21, FLOOR + 2, 6, 27, FLOOR + 2, 6,
                Blocks.QUARTZ_SLAB.defaultBlockState());
        fill(level, box, 21, FLOOR + 1, 7, 21, FLOOR + 1, 7, QUARTZ);
        fill(level, box, 27, FLOOR + 1, 7, 27, FLOOR + 1, 7, QUARTZ);
        put(level, box, Blocks.LECTERN.defaultBlockState(), 24, FLOOR + 3, 6);
        // golden pylons beside the entrance
        for (int x : new int[] {11, 21}) {
            fill(level, box, x, FLOOR + 1, 4, x, FLOOR + 1, 4, GOLD);
            put(level, box, ROD, x, FLOOR + 2, 4);
        }
        // pennants of the speed band
        fill(level, box, 1, 6, 1, W - 2, 6, 1, YELLOW);
        // left lobby: a small "timeline" wall of gold and red blocks
        for (int i = 0; i < 6; i++) {
            fill(level, box, 3 + i * 2, FLOOR + 1, 7, 3 + i * 2, FLOOR + 1 + (i % 3), 7,
                    i % 2 == 0 ? GOLD : DARK_RED);
        }
        put(level, box, Blocks.LECTERN.defaultBlockState(), 6, FLOOR + 1, 4);
    }

    private void hall(WorldGenLevel level, BoundingBox box) {
        // suit gallery (left): four showcases; rogues gallery (right): four showcases
        int[] zs = {12, 17};
        int[][] suitColors = {{0xB3191E, 0}, {0xE8B820, 0}, {0x6E0A10, 0}, {0xD63A3A, 0}};
        int[][] rogueColors = {{0xF0C814, 1}, {0x151515, 1}, {0x2E6FD6, 1}, {0x2E8B3A, 1}};
        int index = 0;
        for (int xc : new int[] {4, 9}) {
            for (int zc : zs) {
                showcase(level, box, xc, zc, suitColors[index % 4][0], true);
                showcase(level, box, W - 1 - xc, zc, rogueColors[index % 4][0], false);
                index++;
            }
        }
        // back-left: the cosmic treadmill
        fill(level, box, 3, FLOOR + 1, 20, 10, FLOOR + 1, 22, BLACK);
        fill(level, box, 3, FLOOR + 1, 20, 10, FLOOR + 1, 20, YELLOW);
        fill(level, box, 3, FLOOR + 1, 22, 10, FLOOR + 1, 22, YELLOW);
        for (int x : new int[] {3, 10}) {
            put(level, box, ROD, x, FLOOR + 2, 20);
            put(level, box, ROD, x, FLOOR + 2, 22);
        }
        fill(level, box, 5, FLOOR + 2, 21, 8, FLOOR + 2, 21, Blocks.IRON_BLOCK.defaultBlockState());
        // back-right: a speed force column (glowing core inside a yellow glass ring)
        int cx = 27;
        int cz = 20;
        fill(level, box, cx - 1, FLOOR + 1, cz - 1, cx + 1, FLOOR + 1, cz + 1, GOLD);
        fill(level, box, cx - 1, FLOOR + 2, cz - 1, cx + 1, 8, cz + 1,
                Blocks.YELLOW_STAINED_GLASS.defaultBlockState());
        fill(level, box, cx, FLOOR + 2, cz, cx, 8, cz, Blocks.GLOWSTONE.defaultBlockState());
        put(level, box, ROD, cx, 9, cz);
        // trophy corners
        for (int x : new int[] {1, W - 2}) {
            fill(level, box, x, FLOOR + 1, 9, x, FLOOR + 3, 9, GOLD);
            put(level, box, Blocks.GOLD_BLOCK.defaultBlockState(), x, FLOOR + 4, 9);
        }
    }

    /** 3x3 glass case with an armor stand in a coloured leather suit. */
    private void showcase(WorldGenLevel level, BoundingBox box, int cx, int cz, int color, boolean suit) {
        fill(level, box, cx - 1, FLOOR + 1, cz - 1, cx + 1, FLOOR + 1, cz + 1, BLACKSTONE);
        for (int x = cx - 1; x <= cx + 1; x++) {
            for (int z = cz - 1; z <= cz + 1; z++) {
                boolean edge = x == cx - 1 || x == cx + 1 || z == cz - 1 || z == cz + 1;
                if (edge) {
                    fill(level, box, x, FLOOR + 2, z, x, FLOOR + 5, z, GLASS);
                }
            }
        }
        fill(level, box, cx - 1, FLOOR + 6, cz - 1, cx + 1, FLOOR + 6, cz + 1, suit ? GOLD : BLACK);
        put(level, box, Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 13), cx, FLOOR + 5, cz);
        spawnStand(level, box, cx, FLOOR + 2, cz, color, suit);
    }

    private void spawnStand(WorldGenLevel level, BoundingBox box, int x, int y, int z, int color, boolean suit) {
        BlockPos pos = new BlockPos(this.getWorldX(x, z), this.getWorldY(y), this.getWorldZ(x, z));
        if (!box.isInside(pos)) {
            return;
        }
        ArmorStand stand = new ArmorStand(level.getLevel(), pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
        Direction toEntrance = this.getOrientation() == null ? Direction.SOUTH : this.getOrientation().getOpposite();
        stand.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, toEntrance.toYRot(), 0.0F);
        stand.setShowArms(true);
        stand.setNoBasePlate(true);
        stand.setItemSlot(EquipmentSlot.HEAD, dyed(Items.LEATHER_HELMET, color));
        stand.setItemSlot(EquipmentSlot.CHEST, dyed(Items.LEATHER_CHESTPLATE, color));
        stand.setItemSlot(EquipmentSlot.LEGS, dyed(Items.LEATHER_LEGGINGS, color));
        stand.setItemSlot(EquipmentSlot.FEET, suit ? new ItemStack(Items.GOLDEN_BOOTS)
                : dyed(Items.LEATHER_BOOTS, color));
        level.addFreshEntity(stand);
    }

    private static ItemStack dyed(Item item, int rgb) {
        ItemStack stack = new ItemStack(item);
        stack.getOrCreateTagElement("display").putInt("color", rgb);
        return stack;
    }

    private void archway(WorldGenLevel level, BoundingBox box) {
        // wall between hall and suit room, with an open golden archway on the red carpet
        fill(level, box, 1, FLOOR + 1, 23, W - 2, CEILING - 1, 24, RED);
        fill(level, box, 1, 9, 23, W - 2, 9, 24, YELLOW);
        this.generateAirBox(level, box, 15, FLOOR + 1, 23, 17, 6, 24);
        fill(level, box, 14, FLOOR + 1, 23, 14, 7, 23, GOLD);
        fill(level, box, 18, FLOOR + 1, 23, 18, 7, 23, GOLD);
        fill(level, box, 14, 7, 23, 18, 7, 23, GOLD);
        put(level, box, ROD, 14, 8, 23);
        put(level, box, ROD, 18, 8, 23);
        fill(level, box, 15, FLOOR + 1, 23, 17, FLOOR + 1, 24, CARPET);
        // flank the archway with two display pillars (suits' emblem colours)
        for (int x : new int[] {12, 20}) {
            fill(level, box, x, FLOOR + 1, 22, x, FLOOR + 4, 22, YELLOW);
            put(level, box, LANTERN, x, FLOOR + 5, 22);
        }
    }

    private void suitRoom(WorldGenLevel level, BoundingBox box, RandomSource random) {
        // golden floor, red carpet to the pedestal, glowing walls
        fill(level, box, 2, FLOOR, 25, W - 3, FLOOR, 29, GOLD);
        fill(level, box, 15, FLOOR + 1, 25, 17, FLOOR + 1, 28, CARPET);
        fill(level, box, 1, 6, 25, 1, 6, 29, YELLOW);
        fill(level, box, W - 2, 6, 25, W - 2, 6, 29, YELLOW);
        // pedestal with the chest
        fill(level, box, 15, FLOOR + 1, 27, 17, FLOOR + 1, 29, GOLD);
        fill(level, box, 15, FLOOR + 2, 27, 17, FLOOR + 2, 29, GOLD);
        for (int x : new int[] {15, 17}) {
            put(level, box, ROD, x, FLOOR + 3, 27);
            put(level, box, ROD, x, FLOOR + 3, 29);
        }
        BlockPos chestPos = new BlockPos(this.getWorldX(16, 28), this.getWorldY(FLOOR + 3), this.getWorldZ(16, 28));
        Direction toEntrance = this.getOrientation() == null ? Direction.SOUTH : this.getOrientation().getOpposite();
        BlockState chest = Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, toEntrance);
        this.createChest(level, box, random, chestPos, SUIT_LOOT, chest);
        // sea lantern frieze and end rods so the room glows
        for (int x = 3; x < W - 3; x += 3) {
            put(level, box, LANTERN, x, 8, 26);
            put(level, box, LANTERN, x, 8, 28);
        }
        for (int x : new int[] {4, 10, 22, 28}) {
            fill(level, box, x, FLOOR + 1, 27, x, FLOOR + 3, 27, QUARTZ);
            put(level, box, END_ROD, x, FLOOR + 4, 27);
        }
    }

    private void lighting(WorldGenLevel level, BoundingBox box) {
        for (int x = 3; x < W - 2; x += 4) {
            for (int z = 4; z < 23; z += 4) {
                put(level, box, LANTERN, x, CEILING - 1, z);
            }
        }
        // keep the hidden structure blocks quiet in the sky light
        put(level, box, Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 12), 16, 8, 26);
    }
}
