package com.flash.mixin;

import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/** The Time Stone's flow of time (the original uses Chrono API): the server's tick length, from TimeStone. */
@Mixin(MinecraftServer.class)
public abstract class ServerTickRateMixin {

    @ModifyConstant(method = "m_130011_()V", constant = @Constant(longValue = 50L), remap = false)
    private long flash$tickLength(long original) {
        return com.flash.infinity.TimeStone.msPerTick();
    }
}
