package com.flash.worldgen;

import com.flash.registry.ModStructures;
import com.mojang.serialization.Codec;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.ChunkGenerator;

/**
 * The Flash Museum: a red and gold building with a giant lightning emblem on
 * the front, galleries of suits and villains, and a hall that leads straight to
 * the room holding the chest with the Torn Suit. It spawns on fairly flat dry
 * land and can be found with {@code /locate structure flash:flash_museum}.
 */
public class FlashMuseumStructure extends Structure {

    public static final Codec<FlashMuseumStructure> CODEC = simpleCodec(FlashMuseumStructure::new);

    public FlashMuseumStructure(StructureSettings settings) {
        super(settings);
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        ChunkPos chunk = context.chunkPos();
        int cx = chunk.getMiddleBlockX();
        int cz = chunk.getMiddleBlockZ();
        ChunkGenerator generator = context.chunkGenerator();
        LevelHeightAccessor heights = context.heightAccessor();
        RandomState random = context.randomState();

        int half = WIDTH / 2;
        int[][] samples = {{0, 0}, {-half, -half}, {half, -half}, {-half, half}, {half, half},
                {0, -half}, {0, half}, {-half, 0}, {half, 0}};
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        int centerY = 0;
        for (int i = 0; i < samples.length; i++) {
            int x = cx + samples[i][0];
            int z = cz + samples[i][1];
            int y = generator.getFirstOccupiedHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, heights, random);
            NoiseColumn column = generator.getBaseColumn(x, z, heights, random);
            BlockState below = column.getBlock(y - 1);
            if (!below.getFluidState().isEmpty()) {
                return Optional.empty();
            }
            if (i == 0) {
                centerY = y;
            }
            min = Math.min(min, y);
            max = Math.max(max, y);
        }
        if (centerY <= generator.getSeaLevel() || max - min > 8) {
            return Optional.empty();
        }

        Direction direction = Direction.Plane.HORIZONTAL.getRandomDirection(context.random());
        int baseY = min - 1;
        return Optional.of(new GenerationStub(new BlockPos(cx, baseY, cz), builder ->
                builder.addPiece(new FlashMuseumPiece(cx, baseY, cz, direction))));
    }

    @Override
    public StructureType<?> type() {
        return ModStructures.FLASH_MUSEUM.get();
    }

    /** Footprint of the building before orientation. */
    public static final int WIDTH = FlashMuseumData.W;
    public static final int DEPTH = FlashMuseumData.D;
    public static final int HEIGHT = FlashMuseumData.H;

    static BoundingBox box(int centerX, int baseY, int centerZ, Direction direction) {
        boolean sideways = direction.getAxis() == Direction.Axis.X;
        int sx = sideways ? DEPTH : WIDTH;
        int sz = sideways ? WIDTH : DEPTH;
        int minX = centerX - sx / 2;
        int minZ = centerZ - sz / 2;
        return new BoundingBox(minX, baseY, minZ, minX + sx - 1, baseY + HEIGHT - 1, minZ + sz - 1);
    }
}
