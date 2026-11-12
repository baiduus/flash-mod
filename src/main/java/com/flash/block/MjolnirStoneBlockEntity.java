package com.flash.block;

import com.flash.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Only there so the hammer can be drawn (MjolnirStoneRenderer). */
public class MjolnirStoneBlockEntity extends BlockEntity {

    public MjolnirStoneBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MJOLNIR_STONE.get(), pos, state);
    }
}
