package com.flash.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Every block change of a chunk passes here: the changes made by Yas' Hand of God powers are refused while her
 * block destruction is off (YasDestruction); everything else goes on untouched. (SRG name, no refmap: the mod is
 * only built for the game, not run in the dev environment.)
 */
@Mixin(LevelChunk.class)
public abstract class LevelChunkGuardMixin {

    @Inject(method = "m_6978_", at = @At("HEAD"), cancellable = true, remap = false)
    private void flash$guard(BlockPos pos, BlockState state, boolean moved,
                             CallbackInfoReturnable<BlockState> cir) {
        LevelChunk self = (LevelChunk) (Object) this;
        if (com.flash.yas.YasDestruction.refuse(self, pos, state)) {
            cir.setReturnValue(null);
        }
    }
}
