package com.flash.worldgen;

import com.flash.registry.ModStructures;
import com.mojang.serialization.Codec;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

/**
 * A crashed Kryptonian ship: a small crystal pod half buried in a scorched crater, with a chest of debris and the
 * Kryptonian baby inside. Rare; {@code /locate structure flash:kryptonian_ship} finds one.
 */
public class KryptonianShipStructure extends Structure {

    public static final Codec<KryptonianShipStructure> CODEC = simpleCodec(KryptonianShipStructure::new);
    public static final int WIDTH = 15;
    public static final int DEPTH = 9;
    public static final int HEIGHT = 9;

    public KryptonianShipStructure(StructureSettings settings) {
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
        int[][] samples = {{0, 0}, {-7, -4}, {7, -4}, {-7, 4}, {7, 4}};
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (int[] s : samples) {
            int y = generator.getFirstOccupiedHeight(cx + s[0], cz + s[1], Heightmap.Types.WORLD_SURFACE_WG, heights,
                    random);
            NoiseColumn column = generator.getBaseColumn(cx + s[0], cz + s[1], heights, random);
            BlockState below = column.getBlock(y - 1);
            if (!below.getFluidState().isEmpty()) {
                return Optional.empty();
            }
            min = Math.min(min, y);
            max = Math.max(max, y);
        }
        if (min <= generator.getSeaLevel() || max - min > 4) {
            return Optional.empty();
        }
        Direction direction = Direction.Plane.HORIZONTAL.getRandomDirection(context.random());
        // local y 3 is the ground: the pod sinks into it
        int baseY = min - 3;
        return Optional.of(new GenerationStub(new BlockPos(cx, baseY, cz), builder ->
                builder.addPiece(new KryptonianShipPiece(cx, baseY, cz, direction))));
    }

    @Override
    public StructureType<?> type() {
        return ModStructures.KRYPTONIAN_SHIP.get();
    }

    static BoundingBox box(int centerX, int baseY, int centerZ, Direction direction) {
        boolean sideways = direction.getAxis() == Direction.Axis.X;
        int sx = sideways ? DEPTH : WIDTH;
        int sz = sideways ? WIDTH : DEPTH;
        int minX = centerX - sx / 2;
        int minZ = centerZ - sz / 2;
        return new BoundingBox(minX, baseY, minZ, minX + sx - 1, baseY + HEIGHT - 1, minZ + sz - 1);
    }
}
