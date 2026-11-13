package com.flash.godwand;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public class DisplacedBlockRecord {
   public final BlockPos pos;
   public final ResourceKey<Level> worldKey;
   public final BlockState originalState;
   public final BlockState state;
   public final CompoundTag blockEntityNbt;
   public final CompoundTag nbt;
   public UUID displayEntityUuid;
   public int ageTicks;
   public int maxLifetimeTicks;
   public int spawnTickDelay;
   public final Vector3f homePos;
   public final Vector3f baseDisplacement;
   public final Vector3f targetDisplacement;
   public final Quaternionf targetRotation;
   public final Vector3f targetScale;
   public final boolean isFullCube;
   public boolean transformed;
   public int animationStage;
   public float hoverYaw;
   public final DisplacedBlockRecord.DisplacementMode mode;
   public UUID boundPlayerUuid;
   public int restoreTick;
   public Vector3f currentOffset;
   public Quaternionf currentRotation;
   public Vector3f currentScale;
   public Vec3 blastCenter;
   public double blastRadius;
   public int openDurationTicks = 8;
   public boolean permanentDestruction = false;
   public BlockPos bridgeDestination;

   public void writeBridgeData(CompoundTag nbt) {
      if (this.bridgeDestination != null) {
         nbt.putLong("bridgeDestination", this.bridgeDestination.asLong());
         if (this.boundPlayerUuid != null) {
            nbt.putUUID("bridgeOwner", this.boundPlayerUuid);
         }
      }
   }

   public void readBridgeData(CompoundTag nbt) {
      if (nbt.contains("bridgeDestination")) {
         this.bridgeDestination = BlockPos.of(nbt.getLong("bridgeDestination"));
      }

      this.boundPlayerUuid = nbt.hasUUID("bridgeOwner") ? nbt.getUUID("bridgeOwner") : null;
   }

   public DisplacedBlockRecord(
      BlockPos pos,
      ResourceKey<Level> worldKey,
      BlockState state,
      CompoundTag nbt,
      Vector3f targetDisplacement,
      Quaternionf targetRotation,
      Vector3f targetScale,
      int maxLifetimeTicks,
      boolean isFullCube,
      DisplacedBlockRecord.DisplacementMode mode,
      UUID boundPlayerUuid
   ) {
      this.pos = pos.immutable();
      this.worldKey = worldKey;
      this.originalState = state;
      this.state = state;
      this.blockEntityNbt = nbt;
      this.nbt = nbt;
      this.displayEntityUuid = null;
      this.ageTicks = 0;
      this.maxLifetimeTicks = maxLifetimeTicks;
      this.spawnTickDelay = 1;
      this.homePos = new Vector3f(this.pos.getX(), this.pos.getY(), this.pos.getZ());
      this.baseDisplacement = new Vector3f(targetDisplacement);
      this.targetDisplacement = targetDisplacement;
      this.targetRotation = targetRotation != null ? new Quaternionf(targetRotation) : new Quaternionf();
      this.targetScale = targetScale != null ? new Vector3f(targetScale) : new Vector3f(1.0F, 1.0F, 1.0F);
      this.isFullCube = isFullCube;
      this.transformed = false;
      this.animationStage = 0;
      this.hoverYaw = 0.0F;
      this.mode = mode != null ? mode : DisplacedBlockRecord.DisplacementMode.TIMED_LIFECYCLE;
      this.boundPlayerUuid = boundPlayerUuid;
      this.restoreTick = 0;
      this.currentOffset = new Vector3f(targetDisplacement);
      this.currentRotation = new Quaternionf(this.targetRotation);
      this.currentScale = new Vector3f(this.targetScale);
      this.blastCenter = null;
      this.blastRadius = 0.0;
   }

   public DisplacedBlockRecord(
      BlockPos pos,
      ResourceKey<Level> worldKey,
      BlockState state,
      CompoundTag nbt,
      Vector3f targetDisplacement,
      Quaternionf targetRotation,
      Vector3f targetScale,
      int maxLifetimeTicks,
      boolean isFullCube
   ) {
      this(
         pos,
         worldKey,
         state,
         nbt,
         targetDisplacement,
         targetRotation,
         targetScale,
         maxLifetimeTicks,
         isFullCube,
         DisplacedBlockRecord.DisplacementMode.TIMED_LIFECYCLE,
         null
      );
   }

   public DisplacedBlockRecord(
      BlockPos pos,
      ResourceKey<Level> worldKey,
      BlockState state,
      CompoundTag nbt,
      Vector3f targetDisplacement,
      Quaternionf targetRotation,
      int maxLifetimeTicks,
      boolean isFullCube
   ) {
      this(
         pos,
         worldKey,
         state,
         nbt,
         targetDisplacement,
         targetRotation,
         new Vector3f(1.0F, 1.0F, 1.0F),
         maxLifetimeTicks,
         isFullCube,
         DisplacedBlockRecord.DisplacementMode.TIMED_LIFECYCLE,
         null
      );
   }

   public DisplacedBlockRecord(
      BlockPos pos, BlockState state, CompoundTag nbt, Vector3f targetDisplacement, Quaternionf targetRotation, int maxLifetimeTicks, boolean isFullCube
   ) {
      this(
         pos,
         Level.OVERWORLD,
         state,
         nbt,
         targetDisplacement,
         targetRotation,
         new Vector3f(1.0F, 1.0F, 1.0F),
         maxLifetimeTicks,
         isFullCube,
         DisplacedBlockRecord.DisplacementMode.TIMED_LIFECYCLE,
         null
      );
   }

   public BlockPos pos() {
      return this.pos;
   }

   public BlockState originalState() {
      return this.originalState;
   }

   public BlockState state() {
      return this.state;
   }

   public CompoundTag blockEntityNbt() {
      return this.blockEntityNbt;
   }

   public CompoundTag nbt() {
      return this.nbt;
   }

   public Vec3 getCurrentOffset() {
      return new Vec3(this.currentOffset.x, this.currentOffset.y, this.currentOffset.z);
   }

   public void beginRestoration() {
      if (!this.permanentDestruction && this.restoreTick == 0) {
         this.restoreTick = 1;
         this.animationStage = 3;
      }
   }

   public void interruptRestoration() {
      if (this.restoreTick > 0) {
         this.restoreTick = 0;
         this.animationStage = 2;
      }
   }

   public void resetRestoreTick() {
      this.restoreTick = 0;
   }

   public boolean isRestoring() {
      return this.restoreTick > 0;
   }

   public enum DisplacementMode {
      PATH_WALKER,
      HYSTERESIS_AURA,
      TIMED_LIFECYCLE,
      CANOPY_VORTEX,
      CUSTOM_SCRIPTED;
   }
}
