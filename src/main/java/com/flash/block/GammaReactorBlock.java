package com.flash.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Gamma reactor: a plain decorative block with no interaction. It is the core
 * ingredient of the Gamma Chamber, the only way to become the Hulk. (The block
 * entity stays registered so reactors already placed in worlds still load.)
 */
public class GammaReactorBlock extends BaseEntityBlock {

    public GammaReactorBlock(Properties properties) {
        super(properties);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new GammaReactorBlockEntity(pos, state);
    }
}
