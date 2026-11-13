package com.flash.godwand;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntArrays;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadPoolExecutor.AbortPolicy;

final class AtomicTerrainPlanner {
   private static final ExecutorService WORKER = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.SECONDS, new ArrayBlockingQueue<>(8), task -> {
      Thread thread = new Thread(task, "God Wand terrain planner");
      thread.setDaemon(true);
      return thread;
   }, new AbortPolicy());

   static CompletableFuture<AtomicTerrainPlanner.Columns> request(int radius) {
      return request(radius, true);
   }

   static CompletableFuture<AtomicTerrainPlanner.Columns> request(int radius, boolean keepPedestal) {
      try {
         return CompletableFuture.supplyAsync(() -> plan(radius, keepPedestal), WORKER);
      } catch (RejectedExecutionException busy) {
         return null;
      }
   }

   static AtomicTerrainPlanner.Columns plan(int radius) {
      return plan(radius, true);
   }

   static AtomicTerrainPlanner.Columns plan(int radius, boolean keepPedestal) {
      if (radius >= 0 && radius <= 98) {
         IntArrayList offsets = new IntArrayList();
         IntArrayList distances = new IntArrayList();

         for (SupernovaCraterCursor cursor = new SupernovaCraterCursor(radius); !cursor.finished(); cursor.advance()) {
            int x = cursor.x();
            int z = cursor.z();
            int distance = x * x + z * z;
            if ((!keepPedestal || distance > 9) && distance <= radius * radius) {
               offsets.add(x & 65535 | z << 16);
               distances.add(distance);
            }
         }

         int[] radialDistances = distances.toIntArray();
         int[] radialOffsets = offsets.toIntArray();
         IntArrays.quickSort(radialDistances, radialOffsets);
         return new AtomicTerrainPlanner.Columns(radialOffsets, radialDistances);
      } else {
         throw new IllegalArgumentException("Atomic terrain radius out of bounds");
      }
   }

   record Columns(int[] offsets, int[] distances) {
   }
}
