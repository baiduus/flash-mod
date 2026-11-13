package com.flash.godwand;

import net.minecraft.util.Mth;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongComparators;
import it.unimi.dsi.fastutil.longs.LongHeapPriorityQueue;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap.Types;

final class AtomicCrater {
   private final BlockPos center;
   private final int radius;
   private CompletableFuture<AtomicTerrainPlanner.Columns> pending;
   private AtomicTerrainPlanner.Columns columns;
   private int column;
   private final LongHeapPriorityQueue surface = new LongHeapPriorityQueue(0, LongComparators.OPPOSITE_COMPARATOR);

   AtomicCrater(BlockPos center, int radius) {
      this.center = center.immutable();
      this.radius = Mth.clamp(radius, 0, 98);
      if (this.radius > 0) {
         this.pending = AtomicTerrainPlanner.request(this.radius);
      }
   }

   static int terrainRadius(int configuredRadius) {
      return Math.max(0, Math.min(configuredRadius, 98));
   }

   boolean finished() {
      return this.radius == 0 || this.columns != null && this.column >= this.columns.offsets().length && this.surface.isEmpty();
   }

   int carve(ServerLevel world, int budget) {
      return this.carve(world, budget, this.radius);
   }

   int carve(ServerLevel world, int budget, float expandedRadius) {
      return this.carve(world, budget, expandedRadius, Long.MAX_VALUE);
   }

   int carve(ServerLevel world, int budget, float expandedRadius, long deadline) {
      if (this.radius != 0 && budget > 0 && System.nanoTime() < deadline) {
         if (this.columns == null) {
            if (this.pending == null) {
               this.pending = AtomicTerrainPlanner.request(this.radius);
            }

            if (this.pending == null || !this.pending.isDone()) {
               return 0;
            }

            this.columns = this.pending.join();
            this.pending = null;
         }

         if (this.center.getY() >= world.getMinBuildHeight() && this.center.getY() < (world.getMaxBuildHeight() - 1) + 1) {
            int bottom = Math.max(world.getMinBuildHeight(), this.center.getY() - 16);
            int ceiling = Math.min((world.getMaxBuildHeight() - 1), this.center.getY() + 1024);
            int inspected = 0;
            Long2ObjectOpenHashMap<LevelChunk> chunks = new Long2ObjectOpenHashMap();

            while (
               this.column < this.columns.offsets().length
                  && inspected < budget
                  && ((inspected & 31) != 0 || System.nanoTime() < deadline)
                  && !(this.columns.distances()[this.column] > expandedRadius * expandedRadius)
            ) {
               int index = this.column++;
               int packed = this.columns.offsets()[index];
               int x = this.center.getX() + (short)packed;
               int z = this.center.getZ() + (short)(packed >> 16);
               inspected++;
               LevelChunk chunk = loadedChunk(world, chunks, x, z);
               if (chunk != null) {
                  int y = Math.min(ceiling, chunk.getHeight(Types.WORLD_SURFACE, x & 15, z & 15));
                  if (y >= bottom) {
                     this.surface.enqueue((long)y << 32 | index & 4294967295L);
                  }
               }
            }

            MutableBlockPos pos = new MutableBlockPos();

            while (!this.surface.isEmpty() && inspected < budget && ((inspected & 31) != 0 || System.nanoTime() < deadline)) {
               long cell = this.surface.firstLong();
               int index = (int)cell;
               int y = (int)(cell >> 32);
               if (this.columns.distances()[index] > expandedRadius * expandedRadius) {
                  break;
               }

               this.surface.dequeueLong();
               inspected++;
               int packed = this.columns.offsets()[index];
               int x = this.center.getX() + (short)packed;
               int z = this.center.getZ() + (short)(packed >> 16);
               LevelChunk chunk = loadedChunk(world, chunks, x, z);
               if (chunk != null) {
                  pos.set(x, y, z);
                  BlockState state = chunk.getBlockState(pos);
                  if (SpatialDisplacementManager.canDisplace(world, pos, state)) {
                     world.setBlock(pos, Blocks.AIR.defaultBlockState(), 50);
                  }

                  if (--y >= bottom) {
                     this.surface.enqueue((long)y << 32 | index & 4294967295L);
                  }
               }
            }

            return inspected;
         } else {
            this.column = this.columns.offsets().length;
            this.surface.clear();
            return 0;
         }
      } else {
         return 0;
      }
   }

   private static LevelChunk loadedChunk(ServerLevel world, Long2ObjectOpenHashMap<LevelChunk> cache, int x, int z) {
      long key = (long)(x >> 4) << 32 | z >> 4 & 4294967295L;
      LevelChunk chunk = (LevelChunk)cache.get(key);
      if (chunk == null) {
         chunk = world.getChunkSource().getChunkNow(x >> 4, z >> 4);
         if (chunk != null) {
            cache.put(key, chunk);
         }
      }

      return chunk;
   }
}
