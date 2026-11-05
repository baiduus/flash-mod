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
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

/**
 * Builds the Flash Museum: an old, abandoned neoclassical building (deepslate
 * and stone brick walls, oxidised copper roof, cracked windows, moss, vines and
 * dim soul lanterns) around a faded red carpet. Coordinates are written for a
 * building whose entrance faces south (canonical frame: x to the right, z from
 * the entrance towards the back, y = 0 is the foundation row); the base class
 * rotates / mirrors the result for the chosen orientation.
 *
 * <pre>
 *   z 0      facade with columns, pediment and the entrance
 *   z 1-8    lobby with a rotten reception desk
 *   z 9-22   hall: old showcases on both sides, columns, faded carpet
 *   z 23-24  wall with the gilded archway
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
    private static final BlockState DEEPSLATE_BRICKS = Blocks.DEEPSLATE_BRICKS.defaultBlockState();
    private static final BlockState CRACKED_DEEPSLATE = Blocks.CRACKED_DEEPSLATE_BRICKS.defaultBlockState();
    private static final BlockState STONE_BRICKS = Blocks.STONE_BRICKS.defaultBlockState();
    private static final BlockState MOSSY_BRICKS = Blocks.MOSSY_STONE_BRICKS.defaultBlockState();
    private static final BlockState COBBLED = Blocks.COBBLED_DEEPSLATE.defaultBlockState();
    private static final BlockState MOSSY_COBBLE = Blocks.MOSSY_COBBLESTONE.defaultBlockState();
    private static final BlockState TILES = Blocks.DEEPSLATE_TILES.defaultBlockState();
    private static final BlockState CRACKED_TILES = Blocks.CRACKED_DEEPSLATE_TILES.defaultBlockState();
    private static final BlockState POLISHED = Blocks.POLISHED_DEEPSLATE.defaultBlockState();
    private static final BlockState BLACKSTONE = Blocks.POLISHED_BLACKSTONE.defaultBlockState();
    private static final BlockState BLACK_BRICKS = Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
    private static final BlockState GILDED = Blocks.GILDED_BLACKSTONE.defaultBlockState();
    private static final BlockState GOLD = Blocks.GOLD_BLOCK.defaultBlockState();
    private static final BlockState QUARTZ_PILLAR = Blocks.QUARTZ_PILLAR.defaultBlockState();
    private static final BlockState CHISELED_QUARTZ = Blocks.CHISELED_QUARTZ_BLOCK.defaultBlockState();
    private static final BlockState OXIDIZED = Blocks.OXIDIZED_CUT_COPPER.defaultBlockState();
    private static final BlockState WEATHERED = Blocks.WEATHERED_CUT_COPPER.defaultBlockState();
    private static final BlockState GLASS = Blocks.GLASS.defaultBlockState();
    private static final BlockState MOSS = Blocks.MOSS_BLOCK.defaultBlockState();
    private static final BlockState MOSS_CARPET = Blocks.MOSS_CARPET.defaultBlockState();
    private static final BlockState CARPET = Blocks.RED_CARPET.defaultBlockState();
    private static final BlockState COBWEB = Blocks.COBWEB.defaultBlockState();
    private static final BlockState CHAIN = Blocks.CHAIN.defaultBlockState();
    private static final BlockState SOUL_LANTERN_HANGING =
            Blocks.SOUL_LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true);
    private static final BlockState SOUL_LANTERN = Blocks.SOUL_LANTERN.defaultBlockState();
    private static final BlockState ROD = Blocks.LIGHTNING_ROD.defaultBlockState();
    private static final BlockState DARK_OAK = Blocks.DARK_OAK_PLANKS.defaultBlockState();
    private static final BlockState DARK_OAK_SLAB = Blocks.DARK_OAK_SLAB.defaultBlockState();
    private static final BlockState CANDLE = Blocks.CANDLE.defaultBlockState();

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

    /** Weathered wall block: mostly deepslate bricks, with cracks, moss and rubble. */
    private static BlockState aged(RandomSource random) {
        int roll = random.nextInt(100);
        if (roll < 52) {
            return DEEPSLATE_BRICKS;
        } else if (roll < 74) {
            return CRACKED_DEEPSLATE;
        } else if (roll < 86) {
            return MOSSY_BRICKS;
        } else if (roll < 94) {
            return STONE_BRICKS;
        }
        return COBBLED;
    }

    private static BlockState agedFloor(RandomSource random) {
        int roll = random.nextInt(100);
        if (roll < 55) {
            return TILES;
        } else if (roll < 78) {
            return CRACKED_TILES;
        } else if (roll < 90) {
            return POLISHED;
        } else if (roll < 96) {
            return MOSSY_COBBLE;
        }
        return MOSS;
    }

    private void agedBox(WorldGenLevel level, BoundingBox box, RandomSource random,
                         int x0, int y0, int z0, int x1, int y1, int z1) {
        for (int x = x0; x <= x1; x++) {
            for (int y = y0; y <= y1; y++) {
                for (int z = z0; z <= z1; z++) {
                    put(level, box, aged(random), x, y, z);
                }
            }
        }
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structures, ChunkGenerator generator,
                            RandomSource random, BoundingBox box, ChunkPos chunkPos, BlockPos pivot) {
        // foundation and a clear volume (hills and trees would otherwise bury it)
        for (int x = 0; x < W; x++) {
            for (int z = 0; z < D; z++) {
                this.fillColumnDown(level, random.nextInt(4) == 0 ? MOSSY_BRICKS : STONE_BRICKS, x, 0, z, box);
            }
        }
        this.generateAirBox(level, box, 0, FLOOR, 0, W - 1, FlashMuseumStructure.HEIGHT - 1, D - 1);

        floor(level, box, random);
        walls(level, box, random);
        facade(level, box, random);
        roof(level, box, random);
        hall(level, box, random);
        archway(level, box, random);
        suitRoom(level, box, random);
        decay(level, box, random);
        lighting(level, box, random);
    }

    // ------------------------------------------------------------------
    // Structure
    // ------------------------------------------------------------------

    private void floor(WorldGenLevel level, BoundingBox box, RandomSource random) {
        for (int x = 0; x < W; x++) {
            for (int z = 0; z < D; z++) {
                put(level, box, agedFloor(random), x, FLOOR, z);
            }
        }
        // chequered inlay in the lobby and the hall
        for (int x = 4; x <= W - 5; x++) {
            for (int z = 2; z <= 21; z++) {
                if (((x + z) & 1) == 0 && random.nextInt(3) != 0) {
                    put(level, box, POLISHED, x, FLOOR, z);
                }
            }
        }
    }

    private void walls(WorldGenLevel level, BoundingBox box, RandomSource random) {
        // outer shell: weathered deepslate / stone bricks
        agedBox(level, box, random, 0, FLOOR + 1, 0, W - 1, CEILING - 1, 0);
        agedBox(level, box, random, 0, FLOOR + 1, D - 1, W - 1, CEILING - 1, D - 1);
        agedBox(level, box, random, 0, FLOOR + 1, 0, 0, CEILING - 1, D - 1);
        agedBox(level, box, random, W - 1, FLOOR + 1, 0, W - 1, CEILING - 1, D - 1);
        // plinth and a gilded cornice that survived the years
        for (int x = 0; x < W; x++) {
            put(level, box, BLACK_BRICKS, x, FLOOR + 1, 0);
            put(level, box, BLACK_BRICKS, x, FLOOR + 1, D - 1);
            if (random.nextInt(3) != 0) {
                put(level, box, random.nextInt(4) == 0 ? GILDED : BLACK_BRICKS, x, CEILING - 1, 0);
            }
        }
        for (int z = 0; z < D; z++) {
            put(level, box, BLACK_BRICKS, 0, FLOOR + 1, z);
            put(level, box, BLACK_BRICKS, W - 1, FLOOR + 1, z);
            if (random.nextInt(3) != 0) {
                put(level, box, random.nextInt(4) == 0 ? GILDED : BLACK_BRICKS, 0, CEILING - 1, z);
                put(level, box, random.nextInt(4) == 0 ? GILDED : BLACK_BRICKS, W - 1, CEILING - 1, z);
            }
        }
        // tall windows on both long sides, some panes gone, some cracked glass
        for (int z : new int[] {5, 11, 17}) {
            for (int side : new int[] {0, W - 1}) {
                for (int dz = 0; dz < 3; dz++) {
                    for (int y = FLOOR + 3; y <= FLOOR + 6; y++) {
                        int roll = random.nextInt(10);
                        BlockState state = roll < 4 ? GLASS : (roll < 8 ? AIR : aged(random));
                        if (y == FLOOR + 6 && dz != 1) {
                            state = aged(random);
                        }
                        put(level, box, state, side, y, z + dz);
                    }
                }
            }
        }
        // back wall of the suit room: a faded gilded frieze
        for (int x = 3; x < W - 3; x += 2) {
            put(level, box, random.nextInt(3) == 0 ? AIR : GILDED, x, 8, D - 1);
        }
    }

    private void facade(WorldGenLevel level, BoundingBox box, RandomSource random) {
        // four columns in front of the entrance
        for (int x : new int[] {8, 11, 21, 24}) {
            int top = random.nextInt(5) == 0 ? CEILING - 3 : CEILING - 1;
            for (int y = FLOOR + 1; y <= top; y++) {
                put(level, box, y == FLOOR + 1 || y == top ? CHISELED_QUARTZ : QUARTZ_PILLAR, x, y, 0);
            }
            if (top < CEILING - 1) {
                put(level, box, Blocks.COBBLESTONE.defaultBlockState(), x, FLOOR + 2, 1);
            }
        }
        // grand entrance: 5 wide, 6 tall, rounded top, framed in gilded blackstone
        this.generateAirBox(level, box, 14, FLOOR + 1, 0, 18, FLOOR + 5, 0);
        this.generateAirBox(level, box, 15, FLOOR + 6, 0, 17, FLOOR + 6, 0);
        for (int y = FLOOR + 1; y <= FLOOR + 6; y++) {
            put(level, box, y % 2 == 0 ? GILDED : BLACK_BRICKS, 13, y, 0);
            put(level, box, y % 2 == 0 ? GILDED : BLACK_BRICKS, 19, y, 0);
        }
        for (int x = 13; x <= 19; x++) {
            put(level, box, x == 16 ? GOLD : GILDED, x, FLOOR + 7, 0);
        }
        put(level, box, BLACK_BRICKS, 14, FLOOR + 6, 0);
        put(level, box, BLACK_BRICKS, 18, FLOOR + 6, 0);
        // a broken door leaf hanging in the doorway
        put(level, box, Blocks.DARK_OAK_TRAPDOOR.defaultBlockState(), 14, FLOOR + 2, 1);
        put(level, box, Blocks.DARK_OAK_TRAPDOOR.defaultBlockState(), 18, FLOOR + 3, 1);

        // pediment: stepped triangle above the entrance with an oxidised medallion
        for (int row = 0; row < 6; row++) {
            int half = 12 - row * 2;
            for (int x = 16 - half; x <= 16 + half; x++) {
                put(level, box, random.nextInt(6) == 0 ? CRACKED_DEEPSLATE : DEEPSLATE_BRICKS,
                        x, CEILING + row, 0);
                put(level, box, aged(random), x, CEILING + row, 1);
            }
        }
        fill(level, box, 15, CEILING + 1, 0, 17, CEILING + 3, 0, OXIDIZED);
        put(level, box, GOLD, 16, CEILING + 2, 0);
        put(level, box, ROD, 16, CEILING + 6, 0);
        put(level, box, ROD, 4, CEILING + 1, 0);
        put(level, box, ROD, 28, CEILING + 1, 0);
    }

    private void roof(WorldGenLevel level, BoundingBox box, RandomSource random) {
        for (int x = 0; x < W; x++) {
            for (int z = 0; z < D; z++) {
                boolean edge = x == 0 || z == 0 || x == W - 1 || z == D - 1;
                put(level, box, edge ? OXIDIZED : (random.nextInt(3) == 0 ? WEATHERED : OXIDIZED),
                        x, CEILING, z);
            }
        }
        // collapsed sections letting the light in
        int[][] holes = {{5, 12, 3, 3}, {24, 6, 3, 2}, {26, 17, 2, 3}, {10, 3, 2, 2}};
        for (int[] hole : holes) {
            for (int x = hole[0]; x < hole[0] + hole[2]; x++) {
                for (int z = hole[1]; z < hole[1] + hole[3]; z++) {
                    put(level, box, AIR, x, CEILING, z);
                    if (random.nextInt(3) == 0) {
                        put(level, box, MOSS_CARPET, x, FLOOR + 1, z);
                    }
                }
            }
            put(level, box, MOSS, hole[0] + 1, FLOOR, hole[1] + 1);
            put(level, box, Blocks.FLOWERING_AZALEA.defaultBlockState(), hole[0] + 1, FLOOR + 1, hole[1] + 1);
        }
        // central skylight with a broken glass roof
        for (int x = 12; x <= 20; x++) {
            for (int z = 12; z <= 18; z++) {
                int roll = random.nextInt(10);
                put(level, box, roll < 5 ? GLASS : (roll < 8 ? AIR : WEATHERED), x, CEILING, z);
            }
        }
    }

    // ------------------------------------------------------------------
    // Interior
    // ------------------------------------------------------------------

    private void hall(WorldGenLevel level, BoundingBox box, RandomSource random) {
        // faded carpet runner from the door to the archway
        for (int z = 1; z <= 24; z++) {
            for (int x = 15; x <= 17; x++) {
                int roll = random.nextInt(10);
                if (roll < 6) {
                    put(level, box, CARPET, x, FLOOR + 1, z);
                } else if (roll < 8) {
                    put(level, box, MOSS_CARPET, x, FLOOR + 1, z);
                }
            }
        }
        // two rows of columns, several of them broken
        for (int x : new int[] {9, 23}) {
            for (int z = 7; z <= 22; z += 5) {
                int top = random.nextInt(4) == 0 ? FLOOR + 2 + random.nextInt(3) : CEILING - 1;
                for (int y = FLOOR + 1; y <= top; y++) {
                    BlockState state = y == FLOOR + 1 || y == CEILING - 1 ? BLACK_BRICKS : aged(random);
                    put(level, box, state, x, y, z);
                }
                if (top < CEILING - 1) {
                    put(level, box, Blocks.GRAVEL.defaultBlockState(), x + 1, FLOOR + 1, z);
                    put(level, box, COBBLED, x, FLOOR + 1, z + 1);
                    put(level, box, MOSS_CARPET, x - 1, FLOOR + 1, z);
                }
            }
        }
        // reception desk, rotten and covered in cobwebs
        for (int x = 11; x <= 21; x++) {
            if (x >= 15 && x <= 17) {
                continue; // keep the carpet path open
            }
            put(level, box, DARK_OAK, x, FLOOR + 1, 5);
            if (random.nextInt(4) != 0) {
                put(level, box, DARK_OAK_SLAB, x, FLOOR + 2, 5);
            }
        }
        put(level, box, CANDLE, 12, FLOOR + 2, 5);
        put(level, box, CANDLE, 20, FLOOR + 2, 5);
        put(level, box, COBWEB, 13, FLOOR + 2, 6);

        // old showcases against both side walls
        int[] colors = {0x6B1E1E, 0x8A7A2A, 0x4A1414, 0x7A6A20, 0x5A1A1A, 0x6E6224};
        int index = 0;
        for (int z : new int[] {10, 15, 20}) {
            showcase(level, box, random, 3, z, colors[index++ % colors.length], false);
            showcase(level, box, random, W - 4, z, colors[index++ % colors.length], index % 2 == 0);
        }
    }

    /** 3x3 old glass case with an armor stand in a faded leather suit. */
    private void showcase(WorldGenLevel level, BoundingBox box, RandomSource random,
                          int cx, int cz, int color, boolean suit) {
        fill(level, box, cx - 1, FLOOR + 1, cz - 1, cx + 1, FLOOR + 1, cz + 1, BLACKSTONE);
        for (int x = cx - 1; x <= cx + 1; x++) {
            for (int z = cz - 1; z <= cz + 1; z++) {
                boolean edge = x == cx - 1 || x == cx + 1 || z == cz - 1 || z == cz + 1;
                if (!edge) {
                    continue;
                }
                for (int y = FLOOR + 2; y <= FLOOR + 5; y++) {
                    if (random.nextInt(100) < 22) {
                        continue; // shattered pane
                    }
                    put(level, box, GLASS, x, y, z);
                }
            }
        }
        fill(level, box, cx - 1, FLOOR + 6, cz - 1, cx + 1, FLOOR + 6, cz + 1, suit ? GILDED : BLACK_BRICKS);
        put(level, box, COBWEB, cx - 1, FLOOR + 5, cz - 1);
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

    private void archway(WorldGenLevel level, BoundingBox box, RandomSource random) {
        // wall between hall and suit room with a gilded archway on the carpet path
        agedBox(level, box, random, 1, FLOOR + 1, 23, W - 2, CEILING - 1, 24);
        this.generateAirBox(level, box, 15, FLOOR + 1, 23, 17, 6, 24);
        for (int y = FLOOR + 1; y <= 7; y++) {
            put(level, box, y % 2 == 0 ? GILDED : BLACK_BRICKS, 14, y, 23);
            put(level, box, y % 2 == 0 ? GILDED : BLACK_BRICKS, 18, y, 23);
        }
        for (int x = 14; x <= 18; x++) {
            put(level, box, x == 16 ? GOLD : GILDED, x, 7, 23);
        }
        put(level, box, ROD, 14, 8, 23);
        put(level, box, ROD, 18, 8, 23);
        // fallen stones on the threshold (still easy to walk over)
        put(level, box, COBBLED, 15, FLOOR + 1, 22);
        put(level, box, Blocks.GRAVEL.defaultBlockState(), 17, FLOOR + 1, 22);
        for (int x : new int[] {12, 20}) {
            fill(level, box, x, FLOOR + 1, 22, x, FLOOR + 3, 22, BLACK_BRICKS);
            put(level, box, SOUL_LANTERN, x, FLOOR + 4, 22);
        }
    }

    private void suitRoom(WorldGenLevel level, BoundingBox box, RandomSource random) {
        // dark tiled floor with gilded accents and a worn carpet to the pedestal
        for (int x = 2; x <= W - 3; x++) {
            for (int z = 25; z <= 29; z++) {
                put(level, box, random.nextInt(5) == 0 ? GILDED : (random.nextBoolean() ? BLACK_BRICKS : BLACKSTONE),
                        x, FLOOR, z);
            }
        }
        for (int z = 25; z <= 28; z++) {
            for (int x = 15; x <= 17; x++) {
                if (random.nextInt(5) != 0) {
                    put(level, box, CARPET, x, FLOOR + 1, z);
                }
            }
        }
        // pedestal with the chest
        fill(level, box, 15, FLOOR + 1, 27, 17, FLOOR + 2, 29, BLACK_BRICKS);
        fill(level, box, 15, FLOOR + 2, 27, 17, FLOOR + 2, 29, GILDED);
        for (int x : new int[] {15, 17}) {
            put(level, box, ROD, x, FLOOR + 3, 27);
            put(level, box, ROD, x, FLOOR + 3, 29);
        }
        BlockPos chestPos = new BlockPos(this.getWorldX(16, 28), this.getWorldY(FLOOR + 3), this.getWorldZ(16, 28));
        Direction toEntrance = this.getOrientation() == null ? Direction.SOUTH : this.getOrientation().getOpposite();
        BlockState chest = Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, toEntrance);
        this.createChest(level, box, random, chestPos, SUIT_LOOT, chest);
        // dim soul lanterns on posts and candles, so the room glows faintly
        for (int x : new int[] {4, 10, 22, 28}) {
            fill(level, box, x, FLOOR + 1, 27, x, FLOOR + 3, 27, BLACK_BRICKS);
            put(level, box, SOUL_LANTERN, x, FLOOR + 4, 27);
        }
        put(level, box, CANDLE, 15, FLOOR + 3, 28);
        put(level, box, CANDLE, 17, FLOOR + 3, 28);
        put(level, box, COBWEB, 2, 8, 26);
        put(level, box, COBWEB, W - 3, 8, 26);
    }

    /** Overgrowth and rubble: moss patches, vines along the walls, fallen stone, cobwebs. */
    private void decay(WorldGenLevel level, BoundingBox box, RandomSource random) {
        for (int i = 0; i < 38; i++) {
            int x = 1 + random.nextInt(W - 2);
            int z = 1 + random.nextInt(D - 2);
            switch (random.nextInt(4)) {
                case 0 -> {
                    put(level, box, MOSS, x, FLOOR, z);
                    put(level, box, MOSS_CARPET, x, FLOOR + 1, z);
                }
                case 1 -> {
                    put(level, box, COBBLED, x, FLOOR + 1, z);
                    if (random.nextBoolean()) {
                        put(level, box, Blocks.GRAVEL.defaultBlockState(), x, FLOOR + 1, z + 1);
                    }
                }
                case 2 -> put(level, box, COBWEB, x, CEILING - 1, z);
                default -> put(level, box, MOSS_CARPET, x, FLOOR + 1, z);
            }
        }
        // vines hanging from the ceiling along both long walls
        for (int z = 2; z < D - 3; z += 2) {
            if (random.nextInt(3) == 0) {
                continue;
            }
            int length = 2 + random.nextInt(5);
            for (int y = CEILING - 1; y > CEILING - 1 - length; y--) {
                put(level, box, Blocks.VINE.defaultBlockState().setValue(VineBlock.WEST, true), 1, y, z);
            }
            if (random.nextInt(3) != 0) {
                for (int y = CEILING - 1; y > CEILING - 1 - length; y--) {
                    put(level, box, Blocks.VINE.defaultBlockState().setValue(VineBlock.EAST, true),
                            W - 2, y, z + 1);
                }
            }
        }
    }

    private void lighting(WorldGenLevel level, BoundingBox box, RandomSource random) {
        // sparse hanging soul lanterns: old and dim on purpose
        for (int x : new int[] {6, 16, 26}) {
            for (int z : new int[] {4, 10, 15, 21}) {
                if (random.nextInt(4) == 0) {
                    continue; // burnt out
                }
                put(level, box, CHAIN, x, CEILING - 1, z);
                put(level, box, CHAIN, x, CEILING - 2, z);
                put(level, box, SOUL_LANTERN_HANGING, x, CEILING - 3, z);
            }
        }
        // soft hidden light so the chest room is never pitch black
        put(level, box, Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 8), 16, 8, 26);
        put(level, box, Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 7), 16, 8, 6);
    }
}
