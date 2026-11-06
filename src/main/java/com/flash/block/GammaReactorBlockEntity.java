package com.flash.block;

import com.flash.hulk.GammaBurst;
import com.flash.hulk.HulkPowers;
import com.flash.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Overload countdown of the gamma reactor. */
public class GammaReactorBlockEntity extends BlockEntity {

    public static final int OVERLOAD_TICKS = 100;
    private int overload = -1;

    public GammaReactorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.GAMMA_REACTOR.get(), pos, state);
    }

    /** @return true when the overload has just begun */
    public boolean startOverload() {
        if (this.overload >= 0) {
            return false;
        }
        this.overload = 0;
        this.setChanged();
        return true;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, GammaReactorBlockEntity reactor) {
        if (reactor.overload < 0 || !(level instanceof ServerLevel server)) {
            return;
        }
        reactor.overload++;
        double x = pos.getX() + 0.5D;
        double y = pos.getY() + 0.5D;
        double z = pos.getZ() + 0.5D;
        float t = reactor.overload / (float) OVERLOAD_TICKS;
        int count = 2 + (int) (t * 14);
        server.sendParticles(com.flash.registry.ModParticles.GAMMA.get(), x, y + 0.6D, z, count, 0.4D + t, 0.5D + t, 0.4D + t, 0.0D);
        if (reactor.overload % 4 == 0) {
            server.sendParticles(ParticleTypes.ELECTRIC_SPARK, x, y + 0.6D, z, 4, 0.5D, 0.5D, 0.5D, 0.05D);
        }
        if (reactor.overload % Math.max(4, 20 - (int) (t * 16)) == 0) {
            server.playSound(null, pos, SoundEvents.NOTE_BLOCK_BIT.value(), SoundSource.BLOCKS, 1.0F, 0.6F + t);
        }
        if (reactor.overload % 20 == 0) {
            server.playSound(null, pos, SoundEvents.BEACON_AMBIENT, SoundSource.BLOCKS, 1.5F, 0.6F + t * 1.2F);
        }
        if (reactor.overload >= OVERLOAD_TICKS) {
            server.removeBlock(pos, false);
            GammaBurst.explode(server, new Vec3(x, y, z), 7.0D);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("Overload", this.overload);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        this.overload = tag.contains("Overload") ? tag.getInt("Overload") : -1;
    }
}
