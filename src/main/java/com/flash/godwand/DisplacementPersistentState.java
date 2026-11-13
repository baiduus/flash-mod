package com.flash.godwand;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The blocks God Wand lifted away and has not put back yet, kept with the world: after a crash they are put back
 * when the world loads (and the leftover displays are removed).
 */
public class DisplacementPersistentState extends SavedData {

    public static final String IDENTIFIER = "flash_godwand_displacements";
    private final List<DisplacedBlockRecord> unrestoredRecords = new ArrayList<>();
    private ResourceKey<Level> worldKey;

    public void setWorldKey(ResourceKey<Level> worldKey) {
        this.worldKey = worldKey;
    }

    public static DisplacementPersistentState createFromNbt(CompoundTag nbt) {
        DisplacementPersistentState state = new DisplacementPersistentState();
        ListTag list = nbt.getList("displaced_records", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag recNbt = list.getCompound(i);
            BlockPos pos = new BlockPos(recNbt.getInt("x"), recNbt.getInt("y"), recNbt.getInt("z"));
            if (!recNbt.contains("state", Tag.TAG_COMPOUND)) {
                continue;
            }
            BlockState blockState = NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), recNbt.getCompound("state"));
            CompoundTag beNbt = recNbt.contains("blockEntity", Tag.TAG_COMPOUND) ? recNbt.getCompound("blockEntity") : null;
            String worldKeyStr = recNbt.getString("worldKey");
            ResourceKey<Level> wKey = worldKeyStr.isEmpty() ? Level.OVERWORLD
                    : ResourceKey.create(Registries.DIMENSION, new ResourceLocation(worldKeyStr));
            DisplacedBlockRecord record = new DisplacedBlockRecord(pos, wKey, blockState, beNbt, new Vector3f(),
                    new Quaternionf(), new Vector3f(1.0F, 1.0F, 1.0F), 0, recNbt.getBoolean("isFullCube"));
            record.readBridgeData(recNbt);
            state.unrestoredRecords.add(record);
        }
        return state;
    }

    private static CompoundTag write(DisplacedBlockRecord record) {
        CompoundTag recNbt = new CompoundTag();
        recNbt.putInt("x", record.pos.getX());
        recNbt.putInt("y", record.pos.getY());
        recNbt.putInt("z", record.pos.getZ());
        recNbt.put("state", NbtUtils.writeBlockState(record.state));
        if (record.nbt != null) {
            recNbt.put("blockEntity", record.nbt);
        }
        recNbt.putString("worldKey", record.worldKey.location().toString());
        recNbt.putBoolean("isFullCube", record.isFullCube);
        record.writeBridgeData(recNbt);
        return recNbt;
    }

    @Override
    public CompoundTag save(CompoundTag nbt) {
        ListTag list = new ListTag();
        ResourceKey<Level> targetKey = this.worldKey != null ? this.worldKey : Level.OVERWORLD;
        for (DisplacedBlockRecord record : SpatialDisplacementManager.getActiveRecordsForWorld(targetKey)) {
            list.add(write(record));
        }
        for (DisplacedBlockRecord record : this.unrestoredRecords) {
            if (this.worldKey == null || record.worldKey.equals(this.worldKey)) {
                list.add(write(record));
            }
        }
        nbt.put("displaced_records", list);
        return nbt;
    }

    public static DisplacementPersistentState getServerState(ServerLevel world) {
        DisplacementPersistentState state = world.getDataStorage().computeIfAbsent(
                DisplacementPersistentState::createFromNbt, DisplacementPersistentState::new, IDENTIFIER);
        state.setWorldKey(world.dimension());
        return state;
    }

    public void restoreAndCleanup(ServerLevel world) {
        if (!this.unrestoredRecords.isEmpty()) {
            List<DisplacedBlockRecord> toRestore = new ArrayList<>();
            for (DisplacedBlockRecord r : this.unrestoredRecords) {
                if (r.worldKey.equals(world.dimension())) {
                    SpatialDisplacementManager.recoverBridge(r);
                    if (r.bridgeDestination == null) {
                        toRestore.add(r);
                    }
                }
            }
            if (!toRestore.isEmpty()) {
                SpatialDisplacementManager.executeTwoPassRestoration(world, toRestore);
                this.unrestoredRecords.removeAll(toRestore);
                com.mojang.logging.LogUtils.getLogger().info("[flash] God Wand: put back {} lifted blocks in {}", toRestore.size(),
                        world.dimension().location());
            }
        }
        this.unrestoredRecords.removeIf(r -> r.worldKey.equals(world.dimension()) && r.bridgeDestination != null);
        this.setDirty();
    }

    /** A display left over from before (a crash, a restart): gone. */
    public static boolean leftover(Entity entity) {
        return entity instanceof Display.BlockDisplay && entity.getTags().contains("godwand_displaced");
    }

    public List<DisplacedBlockRecord> getUnrestoredRecords() {
        return this.unrestoredRecords;
    }
}
