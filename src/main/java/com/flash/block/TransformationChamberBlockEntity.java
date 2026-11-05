package com.flash.block;

import com.flash.power.FlashPowers;
import com.flash.power.FlashProgression;
import com.flash.power.FlashSpeed;
import com.flash.power.FlashStep;
import com.flash.power.FlashStatusEffects;
import com.flash.network.FlashSyncPacket;
import com.flash.network.FlashVisualSyncPacket;
import com.flash.registry.ModBlockEntities;
import com.flash.transformation.PotionRequirement;
import com.flash.transformation.TransformationSequence;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Main chamber controller.
 *
 * <p>It owns the transformation state machine and the "occupant" session:
 * a player enters with right-click, is gently kept inside the chamber and
 * leaves by sneaking. Powers are granted only to the tracked occupant, never
 * to nearby players. Potions are read from and consumed in the adjacent
 * {@link PotionContainerBlockEntity}.</p>
 */
public class TransformationChamberBlockEntity extends BlockEntity {

    /**
     * Interior floor height of the rescaled model. The old model used a
     * vertical transform of 4.8; the new 3.66-block version uses 3.072.
     */
    public static final double BASE_HEIGHT = 3.55D * 3.072D / 16.0D;
    private static final double INSIDE_RADIUS = 0.04D;
    private static final int SNEAK_GRACE_TICKS = 10;

    private static final String TAG_ACTIVE = "Active";
    private static final String TAG_PROGRESS = "Progress";
    private static final String TAG_LIGHTNING_APPLIED = "LightningApplied";
    private static final String TAG_LAST_POWERED = "LastPowered";
    private static final String TAG_WEATHER_CAPTURED = "WeatherCaptured";
    private static final String TAG_PREVIOUS_RAINING = "PreviousRaining";
    private static final String TAG_PREVIOUS_THUNDERING = "PreviousThundering";
    private static final String TAG_OCCUPANT = "Occupant";
    private static final String TAG_ENTERED_AT = "EnteredAt";

    private boolean active;
    private int progress;
    private boolean lightningApplied;
    private boolean lastPowered;
    private boolean weatherCaptured;
    private boolean previousRaining;
    private boolean previousThundering;

    private UUID occupant;
    private long enteredAt;

    public TransformationChamberBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TRANSFORMATION_CHAMBER.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state,
                                  TransformationChamberBlockEntity chamber) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        chamber.tickOccupant(serverLevel, pos, state);

        boolean powered = serverLevel.hasNeighborSignal(pos);
        if (powered && !chamber.lastPowered) {
            chamber.tryStart(serverLevel, pos, state);
        }
        chamber.lastPowered = powered;

        if (chamber.active) {
            chamber.tickSequence(serverLevel, pos, state);
        } else if (state.getValue(TransformationChamberBlock.ACTIVE)) {
            serverLevel.setBlock(pos, state.setValue(TransformationChamberBlock.ACTIVE, false), Block.UPDATE_ALL);
            chamber.restoreWeather(serverLevel);
        }
    }

    // ------------------------------------------------------------------
    // Occupant session
    // ------------------------------------------------------------------

    /**
     * Called by the block when the player right-clicks the chamber.
     */
    public void tryEnter(ServerPlayer player) {
        if (!(this.level instanceof ServerLevel serverLevel)) {
            return;
        }
        BlockState state = this.getBlockState();

        if (this.occupant != null) {
            if (this.occupant.equals(player.getUUID())) {
                player.displayClientMessage(Component.translatable("message.flash.chamber_already_inside"), true);
            } else {
                player.displayClientMessage(Component.translatable("message.flash.chamber_occupied"), true);
            }
            return;
        }
        if (this.active) {
            player.displayClientMessage(Component.translatable("message.flash.chamber_busy"), true);
            return;
        }
        if (player.isShiftKeyDown()) {
            return;
        }

        double centerX = centerX(this.worldPosition, state);
        double centerZ = centerZ(this.worldPosition, state);
        double baseY = this.worldPosition.getY() + BASE_HEIGHT + 0.001D;

        AABB target = player.getBoundingBox().move(
                centerX - player.getX(),
                baseY - player.getY(),
                centerZ - player.getZ()
        );
        if (!serverLevel.noCollision(player, target)) {
            player.displayClientMessage(Component.translatable("message.flash.chamber_obstructed"), true);
            return;
        }

        this.occupant = player.getUUID();
        this.enteredAt = serverLevel.getGameTime();
        player.connection.teleport(centerX, baseY, centerZ,
                state.getValue(TransformationChamberBlock.FACING).toYRot(), 0.0F);
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0.0F;
        FlashProgression.reset(player);
        serverLevel.playSound(null, this.worldPosition, SoundEvents.IRON_DOOR_OPEN,
                SoundSource.BLOCKS, 0.8F, 1.1F);
        player.displayClientMessage(Component.translatable("message.flash.chamber_entered"), true);
        this.setChanged();
    }

    private void tickOccupant(ServerLevel level, BlockPos pos, BlockState state) {
        if (this.occupant == null) {
            if (this.active) {
                this.abort(level, pos, state, true);
            }
            return;
        }

        Player player = level.getPlayerByUUID(this.occupant);
        if (!(player instanceof ServerPlayer serverPlayer) || !serverPlayer.isAlive()
                || serverPlayer.isSpectator() || serverPlayer.level() != level) {
            this.clearOccupant();
            if (this.active) {
                this.abort(level, pos, state, true);
            }
            return;
        }

        if (this.active && !isInside(serverPlayer, pos, state)) {
            this.abort(level, pos, state, true);
            serverPlayer.displayClientMessage(
                    Component.translatable("message.flash.transformation_interrupted"), true);
            return;
        }

        if (serverPlayer.isShiftKeyDown()
                && level.getGameTime() - this.enteredAt > SNEAK_GRACE_TICKS) {
            this.exit(serverPlayer, level, pos, state);
            return;
        }

        double centerX = centerX(pos, state);
        double centerZ = centerZ(pos, state);
        double dx = serverPlayer.getX() - centerX;
        double dz = serverPlayer.getZ() - centerZ;

        if (Math.abs(dx) > INSIDE_RADIUS || Math.abs(dz) > INSIDE_RADIUS) {
            double nextX = centerX + Math.max(-INSIDE_RADIUS, Math.min(INSIDE_RADIUS, dx));
            double nextZ = centerZ + Math.max(-INSIDE_RADIUS, Math.min(INSIDE_RADIUS, dz));
            serverPlayer.connection.teleport(nextX, serverPlayer.getY(), nextZ,
                    serverPlayer.getYRot(), serverPlayer.getXRot());
            Vec3 motion = serverPlayer.getDeltaMovement();
            serverPlayer.setDeltaMovement(0.0D, motion.y, 0.0D);
            serverPlayer.fallDistance = 0.0F;
            FlashProgression.reset(serverPlayer);
        }

        if (serverPlayer.getY() < pos.getY() + BASE_HEIGHT - 0.1D) {
            serverPlayer.connection.teleport(centerX, pos.getY() + BASE_HEIGHT, centerZ,
                    serverPlayer.getYRot(), serverPlayer.getXRot());
            serverPlayer.setDeltaMovement(Vec3.ZERO);
            FlashProgression.reset(serverPlayer);
        }
    }

    private void exit(ServerPlayer player, ServerLevel level, BlockPos pos, BlockState state) {
        if (this.active) {
            this.abort(level, pos, state, true);
            player.displayClientMessage(Component.translatable("message.flash.transformation_interrupted"), true);
        }

        Direction facing = state.getValue(TransformationChamberBlock.FACING);
        double baseY = pos.getY() + BASE_HEIGHT + 0.001D;
        double frontX = centerX(pos, state);
        double frontZ = pos.getZ() + 0.5D + facing.getStepZ() * 1.6D;
        AABB frontTarget = player.getBoundingBox().move(
                frontX - player.getX(),
                baseY - player.getY(),
                frontZ - player.getZ()
        );
        if (level.noCollision(player, frontTarget)) {
            player.connection.teleport(frontX, baseY, frontZ,
                    player.getYRot(), player.getXRot());
            player.setDeltaMovement(Vec3.ZERO);
            player.fallDistance = 0.0F;
            FlashProgression.reset(player);
            this.clearOccupant();
            level.playSound(null, pos, SoundEvents.IRON_DOOR_CLOSE,
                    SoundSource.BLOCKS, 0.8F, 1.0F);
            player.displayClientMessage(Component.translatable("message.flash.chamber_exited"), true);
            return;
        }

        Direction[] directions = {
                facing,
                facing.getClockWise(),
                facing.getCounterClockWise(),
                facing.getOpposite()
        };
        for (Direction direction : directions) {
            BlockPos targetPos = pos.relative(direction);
            double targetX = targetPos.getX() + 0.5D;
            double targetZ = targetPos.getZ() + 0.5D;
            AABB target = player.getBoundingBox().move(
                    targetX - player.getX(),
                    baseY - player.getY(),
                    targetZ - player.getZ()
            );
            if (level.noCollision(player, target)) {
                player.connection.teleport(targetX, baseY, targetZ,
                        player.getYRot(), player.getXRot());
                player.setDeltaMovement(Vec3.ZERO);
                player.fallDistance = 0.0F;
                FlashProgression.reset(player);
                this.clearOccupant();
                level.playSound(null, pos, SoundEvents.IRON_DOOR_CLOSE,
                        SoundSource.BLOCKS, 0.8F, 1.0F);
                player.displayClientMessage(Component.translatable("message.flash.chamber_exited"), true);
                return;
            }
        }

        // No safe teleport target: release the session instead of trapping the player.
        this.clearOccupant();
        player.displayClientMessage(Component.translatable("message.flash.chamber_exit_blocked"), true);
    }

    private void clearOccupant() {
        this.occupant = null;
        this.enteredAt = 0L;
        this.setChanged();
    }

    public boolean isOccupant(Player player) {
        return this.occupant != null && this.occupant.equals(player.getUUID());
    }

    private ServerPlayer getOccupantPlayer(ServerLevel level) {
        if (this.occupant == null) {
            return null;
        }
        Player player = level.getPlayerByUUID(this.occupant);
        return player instanceof ServerPlayer serverPlayer && serverPlayer.isAlive()
                && !serverPlayer.isSpectator() ? serverPlayer : null;
    }

    private static boolean isInside(ServerPlayer player, BlockPos pos, BlockState state) {
        double dx = player.getX() - centerX(pos, state);
        double dz = player.getZ() - centerZ(pos, state);
        if (Math.abs(dx) > 1.28D || Math.abs(dz) > 1.28D) {
            return false;
        }
        return player.getY() >= pos.getY() - 0.5D && player.getY() <= pos.getY() + 4.2D;
    }

    public static double centerX(BlockPos pos, BlockState state) {
        Direction left = state.getValue(TransformationChamberBlock.FACING).getCounterClockWise();
        // 1.60 model blocks scaled by the same 0.64 factor as the model.
        return pos.getX() + 0.5D + left.getStepX() * 1.024D;
    }

    public static double centerZ(BlockPos pos, BlockState state) {
        Direction back = state.getValue(TransformationChamberBlock.FACING).getOpposite();
        // 1.80 model blocks scaled by the same 0.64 factor as the model.
        return pos.getZ() + 0.5D + back.getStepZ() * 1.152D;
    }

    private static Vec3 cabinCenter(BlockPos pos, BlockState state) {
        return new Vec3(centerX(pos, state),
                pos.getY() + BASE_HEIGHT + 1.0D,
                centerZ(pos, state));
    }

    // ------------------------------------------------------------------
    // Transformation
    // ------------------------------------------------------------------

    private void tryStart(ServerLevel level, BlockPos pos, BlockState state) {
        if (this.active) {
            return;
        }

        PotionContainerBlockEntity container = findContainer(level, pos);
        if (container == null) {
            notifyNearby(level, pos, Component.translatable("message.flash.no_container"));
            return;
        }

        List<PotionRequirement> missing = container.missingRequirements();
        if (!missing.isEmpty()) {
            notifyNearby(level, pos, Component.translatable(
                    "message.flash.missing_potions",
                    PotionRequirement.describe(missing)
            ));
            return;
        }

        ServerPlayer player = this.getOccupantPlayer(level);
        if (player == null) {
            notifyNearby(level, pos, Component.translatable("message.flash.no_player_inside"));
            return;
        }
        if (FlashPowers.hasPowers(player)) {
            notifyNearby(level, pos, Component.translatable("message.flash.already_has_powers"));
            return;
        }

        this.active = true;
        this.progress = 0;
        this.lightningApplied = false;
        this.weatherCaptured = true;
        this.previousRaining = level.isRaining();
        this.previousThundering = level.isThundering();

        TransformationSequence.startStorm(level);
        TransformationSequence.playStartSound(level, pos);
        level.setBlock(pos, state.setValue(TransformationChamberBlock.ACTIVE, true), Block.UPDATE_ALL);
        notifyNearby(level, pos, Component.translatable("message.flash.transformation_started"));
        this.setChanged();
    }

    private void tickSequence(ServerLevel level, BlockPos pos, BlockState state) {
        this.progress++;
        Vec3 center = cabinCenter(pos, state);

        if (this.progress % 4 == 0) {
            TransformationSequence.spawnAmbientParticles(level, center, this.progress);
        }
        if (this.progress == 20) {
            TransformationSequence.playChargeSound(level, pos);
        }
        if (!this.lightningApplied && this.progress >= TransformationSequence.LIGHTNING_TICK) {
            ServerPlayer occupant = this.getOccupantPlayer(level);
            if (occupant != null) {
                TransformationSequence.strikeLightning(level, occupant);
                this.lightningApplied = true;
                this.setChanged();
            }
        }
        if (this.progress >= TransformationSequence.TOTAL_TICKS) {
            this.finishSequence(level, pos, state);
            return;
        }
        if (this.progress % 20 == 0) {
            this.setChanged();
        }
    }

    private void finishSequence(ServerLevel level, BlockPos pos, BlockState state) {
        ServerPlayer player = this.getOccupantPlayer(level);
        PotionContainerBlockEntity container = findContainer(level, pos);
        Vec3 center = cabinCenter(pos, state);

        if (player != null && container != null && !FlashPowers.hasPowers(player)) {
            FlashPowers.grant(player);
            FlashSpeed.apply(player);
            FlashStep.apply(player);
            FlashStatusEffects.onLevelChanged(player);
            FlashSyncPacket.send(player);
            FlashVisualSyncPacket.send(player);
            player.displayClientMessage(Component.translatable("message.flash.powers_granted"), false);
            player.displayClientMessage(Component.translatable("message.flash.start_level"), false);
            container.consumePotions();
            TransformationSequence.spawnFinishParticles(level, center);
            TransformationSequence.playFinishSound(level, pos);
        } else {
            notifyNearby(level, pos, Component.translatable("message.flash.transformation_failed"));
        }

        this.restoreWeather(level);
        this.active = false;
        this.progress = 0;
        this.lightningApplied = false;
        level.setBlock(pos, state.setValue(TransformationChamberBlock.ACTIVE, false), Block.UPDATE_ALL);
        this.setChanged();
    }

    private PotionContainerBlockEntity findContainer(ServerLevel level, BlockPos pos) {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockEntity blockEntity = level.getBlockEntity(pos.relative(direction));
            if (blockEntity instanceof PotionContainerBlockEntity container) {
                return container;
            }
        }
        return null;
    }

    /**
     * Called by the container when it is broken. A running sequence is safely
     * cancelled without consuming or duplicating items.
     */
    public void onPotionContainerRemoved() {
        if (!(this.level instanceof ServerLevel level)) {
            return;
        }
        BlockPos pos = this.worldPosition;
        BlockState state = this.getBlockState();
        boolean wasActive = this.active;
        this.abort(level, pos, state, true);
        if (wasActive) {
            ServerPlayer player = this.getOccupantPlayer(level);
            if (player != null) {
                player.displayClientMessage(
                        Component.translatable("message.flash.container_removed"), true);
            }
        }
    }

    /**
     * Called by the block when the chamber itself is broken.
     */
    public void onChamberRemoved() {
        if (this.level instanceof ServerLevel level) {
            this.restoreWeather(level);
            ServerPlayer player = this.getOccupantPlayer(level);
            if (player != null) {
                player.displayClientMessage(Component.translatable("message.flash.chamber_removed"), true);
            }
        }
        this.active = false;
        this.progress = 0;
        this.lightningApplied = false;
        this.occupant = null;
        this.setChanged();
    }

    private void abort(ServerLevel level, BlockPos pos, BlockState state, boolean resetState) {
        this.restoreWeather(level);
        this.active = false;
        this.progress = 0;
        this.lightningApplied = false;
        if (resetState && state.hasProperty(TransformationChamberBlock.ACTIVE)
                && state.getValue(TransformationChamberBlock.ACTIVE)) {
            level.setBlock(pos, state.setValue(TransformationChamberBlock.ACTIVE, false), Block.UPDATE_ALL);
        }
        this.setChanged();
    }

    private void restoreWeather(ServerLevel level) {
        if (this.weatherCaptured) {
            TransformationSequence.restoreWeather(level, this.previousRaining, this.previousThundering);
            this.weatherCaptured = false;
            this.setChanged();
        }
    }

    private static void notifyNearby(ServerLevel level, BlockPos pos, Component message) {
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) <= 100.0D) {
                player.displayClientMessage(message, true);
            }
        }
    }

    // ------------------------------------------------------------------
    // Persistence
    // ------------------------------------------------------------------

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        this.active = tag.getBoolean(TAG_ACTIVE);
        this.progress = tag.getInt(TAG_PROGRESS);
        this.lightningApplied = tag.getBoolean(TAG_LIGHTNING_APPLIED);
        this.lastPowered = tag.getBoolean(TAG_LAST_POWERED);
        this.weatherCaptured = tag.getBoolean(TAG_WEATHER_CAPTURED);
        this.previousRaining = tag.getBoolean(TAG_PREVIOUS_RAINING);
        this.previousThundering = tag.getBoolean(TAG_PREVIOUS_THUNDERING);
        this.occupant = tag.hasUUID(TAG_OCCUPANT) ? tag.getUUID(TAG_OCCUPANT) : null;
        this.enteredAt = tag.getLong(TAG_ENTERED_AT);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putBoolean(TAG_ACTIVE, this.active);
        tag.putInt(TAG_PROGRESS, this.progress);
        tag.putBoolean(TAG_LIGHTNING_APPLIED, this.lightningApplied);
        tag.putBoolean(TAG_LAST_POWERED, this.lastPowered);
        tag.putBoolean(TAG_WEATHER_CAPTURED, this.weatherCaptured);
        tag.putBoolean(TAG_PREVIOUS_RAINING, this.previousRaining);
        tag.putBoolean(TAG_PREVIOUS_THUNDERING, this.previousThundering);
        if (this.occupant != null) {
            tag.putUUID(TAG_OCCUPANT, this.occupant);
        }
        tag.putLong(TAG_ENTERED_AT, this.enteredAt);
    }
}
