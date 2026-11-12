package com.flash.worldgen;

import com.flash.registry.ModStructures;
import com.mojang.serialization.Codec;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

/**
 * The floating island of the transcendence, with its top at Y = 500 (the overworld goes up to 575 in this mod).
 * Very rare; {@code /locate structure flash:yas_island}.
 */
public class YasIslandStructure extends Structure {

    public static final Codec<YasIslandStructure> CODEC = simpleCodec(YasIslandStructure::new);
    public static final int SIZE = 49;
    public static final int HEIGHT = 40;
    /** The grass of the island is at Y = 500. */
    public static final int TOP_Y = 500;

    public YasIslandStructure(StructureSettings settings) {
        super(settings);
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        ChunkPos chunk = context.chunkPos();
        int cx = chunk.getMiddleBlockX();
        int cz = chunk.getMiddleBlockZ();
        int baseY = TOP_Y - YasIslandPiece.TOP;
        if (baseY + HEIGHT > context.heightAccessor().getMaxBuildHeight()) {
            return Optional.empty(); // a world without the taller overworld
        }
        return Optional.of(new GenerationStub(new BlockPos(cx, baseY, cz), builder ->
                builder.addPiece(new YasIslandPiece(cx, baseY, cz))));
    }

    @Override
    public StructureType<?> type() {
        return ModStructures.YAS_ISLAND.get();
    }

    static BoundingBox box(int centerX, int baseY, int centerZ) {
        int minX = centerX - SIZE / 2;
        int minZ = centerZ - SIZE / 2;
        return new BoundingBox(minX, baseY, minZ, minX + SIZE - 1, baseY + HEIGHT - 1, minZ + SIZE - 1);
    }
}
