package com.flash.block;

import com.flash.hulk.HulkPowers;
import com.flash.registry.ModBlockEntities;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** The gamma sequence of the gamma chamber. */
public class GammaChamberBlockEntity extends BlockEntity {

    public static final int SEQUENCE_TICKS = 160;
    private UUID occupant;
    private int ticks = -1;

    public GammaChamberBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.GAMMA_CHAMBER.get(), pos, state);
    }

    static boolean isOnPad(Player player, BlockPos pos) {
        return player.getX() >= pos.getX() - 0.1D && player.getX() <= pos.getX() + 1.1D
                && player.getZ() >= pos.getZ() - 0.1D && player.getZ() <= pos.getZ() + 1.1D
                && player.getY() >= pos.getY() && player.getY() <= pos.getY() + 1.5D;
    }

    void start(Player player, Level level, BlockPos pos, BlockState state) {
        if (HulkPowers.has(player) && !HulkPowers.isTemporary(player)) {
            player.displayClientMessage(Component.translatable("message.flash.hulk_already"), true);
            return;
        }
        this.occupant = player.getUUID();
        this.ticks = 0;
        level.setBlock(pos, state.setValue(GammaChamberBlock.ACTIVE, true), 3);
        level.playSound(null, pos, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.4F, 0.6F);
        player.displayClientMessage(Component.translatable("message.flash.gamma_chamber_started"), true);
        this.setChanged();
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, GammaChamberBlockEntity chamber) {
        if (chamber.ticks < 0 || !(level instanceof ServerLevel server)) {
            return;
        }
        Player player = chamber.occupant == null ? null : level.getPlayerByUUID(chamber.occupant);
        if (!(player instanceof ServerPlayer occupant) || !occupant.isAlive()
                || occupant.distanceToSqr(Vec3.atBottomCenterOf(pos)) > 4.0D) {
            chamber.stop(level, pos, state, true);
            if (player != null) {
                player.displayClientMessage(Component.translatable("message.flash.gamma_chamber_aborted"), true);
            }
            return;
        }
        chamber.ticks++;
        float t = chamber.ticks / (float) SEQUENCE_TICKS;
        double cx = pos.getX() + 0.5D;
        double cz = pos.getZ() + 0.5D;
        double y0 = pos.getY() + 0.25D;
        // keep the occupant in the middle of the tube
        if (Math.hypot(occupant.getX() - cx, occupant.getZ() - cz) > 0.2D) {
            occupant.connection.teleport(cx, y0, cz, occupant.getYRot(), occupant.getXRot());
        }
        occupant.setDeltaMovement(Vec3.ZERO);
        // spiral of gamma rising faster and faster
        int n = 2 + (int) (t * 8);
        for (int i = 0; i < n; i++) {
            double a = chamber.ticks * (0.25D + t * 0.5D) + i * Math.PI * 2.0D / n;
            double h = (chamber.ticks * 0.06D + i * 0.4D) % 2.8D;
            server.sendParticles(com.flash.registry.ModParticles.GAMMA.get(), cx + Math.cos(a) * 0.7D, y0 + h, cz + Math.sin(a) * 0.7D,
                    1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
        if (chamber.ticks % 4 == 0) {
            server.sendParticles(ParticleTypes.ELECTRIC_SPARK, cx, y0 + 1.2D, cz, 3 + (int) (t * 10), 0.3D, 0.8D, 0.3D, 0.05D);
        }
        if (chamber.ticks % 20 == 0) {
            server.playSound(null, pos, SoundEvents.BEACON_AMBIENT, SoundSource.BLOCKS, 1.6F, 0.5F + t);
            occupant.hurt(server.damageSources().magic(), 0.5F);
        }
        if (chamber.ticks >= SEQUENCE_TICKS) {
            server.sendParticles(com.flash.registry.ModParticles.GAMMA.get(), cx, y0 + 1.2D, cz, 200, 0.8D, 1.4D, 0.8D, 0.0D);
            server.sendParticles(ParticleTypes.FLASH, cx, y0 + 1.2D, cz, 2, 0.0D, 0.0D, 0.0D, 0.0D);
            server.playSound(null, pos, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.BLOCKS, 1.6F, 0.6F);
            if (HulkPowers.isTemporary(occupant)) {
                HulkPowers.revoke(occupant);
            }
            HulkPowers.grant(occupant, true);
            occupant.displayClientMessage(Component.translatable("message.flash.gamma_exposed"), false);
            chamber.stop(level, pos, state, false);
            // the chamber cannot hold the new Hulk: it blows up (he is left out of his own blast)
            level.removeBlock(pos, false);
            level.explode(occupant, cx, y0 + 1.0D, cz, 5.0F, Level.ExplosionInteraction.TNT);
        }
    }

    /** @param keepCapsule true when interrupted (the capsule stays in the machine) */
    private void stop(Level level, BlockPos pos, BlockState state, boolean keepCapsule) {
        this.ticks = -1;
        this.occupant = null;
        BlockState current = level.getBlockState(pos);
        if (current.getBlock() instanceof GammaChamberBlock) {
            level.setBlock(pos, current.setValue(GammaChamberBlock.ACTIVE, false)
                    .setValue(GammaChamberBlock.LOADED, keepCapsule), 3);
        }
        this.setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("Ticks", this.ticks);
        if (this.occupant != null) {
            tag.putUUID("Occupant", this.occupant);
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        this.ticks = tag.contains("Ticks") ? tag.getInt("Ticks") : -1;
        this.occupant = tag.hasUUID("Occupant") ? tag.getUUID("Occupant") : null;
    }

    /** The model is 3.5 blocks tall. */
    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox() {
        return new net.minecraft.world.phys.AABB(this.worldPosition).expandTowards(0.0D, 7.0D, 0.0D).inflate(1.2D);
    }
}
