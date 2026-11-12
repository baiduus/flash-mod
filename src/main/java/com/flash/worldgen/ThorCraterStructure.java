package com.flash.worldgen;

import com.flash.registry.ModStructures;
import com.mojang.serialization.Codec;
import java.util.Optional;
import net.minecraft.core.BlockPos;
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
 * The crater where Mjolnir fell like an asteroid: a scorched bowl about twice the size of a TNT blast, with the
 * hammer stuck in the middle (only the worthy lift it). Rare; {@code /locate structure flash:thor_crater}.
 */
public class ThorCraterStructure extends Structure {

    public static final Codec<ThorCraterStructure> CODEC = simpleCodec(ThorCraterStructure::new);
    /** 17 x 17 blocks around the impact; local y {@link ThorCraterPiece#GROUND} is the ground. */
    public static final int SIZE = 17;
    public static final int HEIGHT = 12;

    public ThorCraterStructure(StructureSettings settings) {
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
        int[][] samples = {{0, 0}, {-7, -7}, {7, -7}, {-7, 7}, {7, 7}};
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
        int baseY = min - ThorCraterPiece.GROUND;
        return Optional.of(new GenerationStub(new BlockPos(cx, baseY, cz), builder ->
                builder.addPiece(new ThorCraterPiece(cx, baseY, cz))));
    }

    @Override
    public StructureType<?> type() {
        return ModStructures.THOR_CRATER.get();
    }

    static BoundingBox box(int centerX, int baseY, int centerZ) {
        int minX = centerX - SIZE / 2;
        int minZ = centerZ - SIZE / 2;
        return new BoundingBox(minX, baseY, minZ, minX + SIZE - 1, baseY + HEIGHT - 1, minZ + SIZE - 1);
    }
}
