package com.flash.godwand;

import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap.Types;

final class FugaCrater {
   private final BlockPos center;
   private final int radius;
   private CompletableFuture<AtomicTerrainPlanner.Columns> pending;
   private AtomicTerrainPlanner.Columns columns;
   private int column;
   private boolean inColumn;
   private boolean blastColumn;
   private int y;
   private int bottom;
   static final int SPIRES = 16;
   private final int[] raised = new int[16];
   static final int PRESS_SINK = 4;
   static final int PRESS_LIP = 3;
   private int[] pressGround;
   private int[] pressDone;
   private int pressSpan;

   FugaCrater(BlockPos center, int radius) {
      this.center = center.immutable();
      this.radius = Math.max(0, Math.min(64, radius));
      if (this.radius > 0) {
         this.pending = AtomicTerrainPlanner.request(reach(this.radius), false);
      }
   }

   boolean finished() {
      return this.radius == 0 || this.columns != null && this.column >= this.columns.offsets().length;
   }

   static int depth(int radius, int dx, int dz) {
      double angle = Math.atan2(dz, dx);
      double rim = radius * (0.9 + 0.05 * Math.sin(angle * 5.0 + 1.3) + 0.035 * Math.sin(angle * 11.0 + 0.4) + 0.015 * Math.sin(angle * 23.0));
      double r = Math.sqrt(dx * dx + dz * dz) / rim;
      if (r > 1.0) {
         return 0;
      }

      double grain = ((dx * 73856093 ^ dz * 19349663) & 0xFF) / 255.0;
      return 1 + (int)(radius * 0.6 * Math.sqrt(1.0 - r * r) * (0.86 + 0.14 * grain));
   }

   static int reach(int radius) {
      return Math.min(98, radius * 3 / 2);
   }

   static boolean blast(int radius, int dx, int dz) {
      return depth(radius, dx, dz) == 0 && dx * dx + dz * dz <= reach(radius) * reach(radius);
   }

   static int scour(int radius, int dx, int dz) {
      double out = (Math.sqrt(dx * dx + dz * dz) - radius * 0.9) / Math.max(1.0, reach(radius) - radius * 0.9);
      double grain = ((dx * 19349663 ^ dz * 83492791) & 0xFF) / 255.0;
      return grain < 0.2 ? 0 : (int)Math.round(Math.max(0.0, 1.0 - out) * 4.0 * grain);
   }

   int carve(ServerLevel world, int budget, long deadline, double impactAge) {
      if (FugaSequence.terrainActive(impactAge) && !this.finished() && budget > 0) {
         if (this.columns == null) {
            if (this.pending == null) {
               this.pending = AtomicTerrainPlanner.request(reach(this.radius), false);
            }

            if (this.pending == null || !this.pending.isDone()) {
               return 0;
            }

            this.columns = this.pending.join();
            this.pending = null;
         }

         int inspected = 0;
         MutableBlockPos pos = new MutableBlockPos();
         LevelChunk chunk = null;

         while (inspected < budget && !this.finished() && ((inspected & 31) != 0 || System.nanoTime() < deadline)) {
            int packed = this.columns.offsets()[this.column];
            int dx = (short)packed;
            int dz = (short)(packed >> 16);
            int x = this.center.getX() + dx;
            int z = this.center.getZ() + dz;
            inspected++;
            if (!this.inColumn) {
               int depth = depth(this.radius, dx, dz);
               this.blastColumn = depth == 0 && blast(this.radius, dx, dz);
               if ((depth != 0 || this.blastColumn) && this.center.getY() >= world.getMinBuildHeight() && this.center.getY() < (world.getMaxBuildHeight() - 1) + 1) {
                  chunk = world.getChunkSource().getChunkNow(x >> 4, z >> 4);
                  if (chunk == null) {
                     this.column++;
                  } else {
                     this.y = Math.min((world.getMaxBuildHeight() - 1), Math.min(this.center.getY() + 384, chunk.getHeight(Types.WORLD_SURFACE, x & 15, z & 15)));
                     this.bottom = Math.max(world.getMinBuildHeight(), this.center.getY() - (this.blastColumn ? 12 : depth));
                     if (this.y < this.bottom) {
                        this.column++;
                     } else {
                        this.inColumn = true;
                     }
                  }
               } else {
                  this.column++;
               }
            } else {
               if (chunk == null) {
                  chunk = world.getChunkSource().getChunkNow(x >> 4, z >> 4);
               }

               if (chunk == null) {
                  this.inColumn = false;
                  this.column++;
               } else {
                  pos.set(x, this.y, z);
                  BlockState state = chunk.getBlockState(pos);
                  if (!this.blastColumn) {
                     if (SpatialDisplacementManager.canDisplace(world, pos, state)) {
                        world.setBlock(pos, Blocks.AIR.defaultBlockState(), 50);
                     }

                     if (--this.y < this.bottom) {
                        this.inColumn = false;
                        this.column++;
                        this.scorch(world, chunk, pos.set(x, this.y, z), dx, dz);
                     }
                  } else {
                     boolean plant = state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES) || state.canBeReplaced();
                     if (!state.isAir() && (!plant || !state.getFluidState().isEmpty())) {
                        int gouge = state.getFluidState().isEmpty() ? scour(this.radius, dx, dz) : 0;
                        if (gouge > 0) {
                           this.blastColumn = false;
                           this.bottom = Math.max(this.bottom, this.y - gouge + 1);
                        } else {
                           double out = (Math.sqrt(dx * dx + dz * dz) - this.radius * 0.9) / Math.max(1.0, reach(this.radius) - this.radius * 0.9);
                           if (state.getFluidState().isEmpty() && ((dx * 73856093 ^ dz * 19349663) & 0xFF) / 255.0 > out) {
                              this.scorch(world, chunk, pos, dx, dz);
                           }

                           this.inColumn = false;
                           this.column++;
                        }
                     } else {
                        if (!state.isAir() && SpatialDisplacementManager.canDisplace(world, pos, state)) {
                           world.setBlock(pos, Blocks.AIR.defaultBlockState(), 50);
                        }

                        if (--this.y < this.bottom) {
                           this.inColumn = false;
                           this.column++;
                        }
                     }
                  }
               }
            }
         }

         return inspected;
      } else {
         return 0;
      }
   }

   static int[] spire(int radius, int i) {
      int h = i * 374761393 + radius * 668265263;
      h = (h ^ h >>> 13) * 1274126177;
      h ^= h >>> 16;
      double a = (h & 1023) / 1023.0 * Math.PI * 2.0;
      double r = radius * (0.25 + 0.5 * (h >>> 10 & 0xFF) / 255.0);
      return new int[]{
         (int)Math.round(Math.cos(a) * r), (int)Math.round(Math.sin(a) * r), 6 + (h >>> 18 & 15) * 2 / 3, 1 + (h >>> 22 & 1), 12 + (h >>> 23 & 31)
      };
   }

   int upheave(ServerLevel world, int budget, double impactAge) {
      if (this.radius >= 8 && budget > 0) {
         int used = 0;
         MutableBlockPos pos = new MutableBlockPos();

         for (int i = 0; i < 16 && used < budget; i++) {
            int[] s = spire(this.radius, i);
            int target = (int)Math.floor(s[2] * Math.min(1.0, Math.max(0.0, (impactAge - s[4]) / 8.0)));
            int x0 = this.center.getX() + s[0];
            int z0 = this.center.getZ() + s[1];
            LevelChunk chunk = world.getChunkSource().getChunkNow(x0 >> 4, z0 >> 4);
            if (chunk == null) {
               this.raised[i] = s[2];
            } else {
               int ground = chunk.getHeight(Types.WORLD_SURFACE, x0 & 15, z0 & 15);
               double out = Math.atan2(s[1], s[0]);

               while (this.raised[i] < target && used < budget) {
                  int layer = this.raised[i]++;
                  int y = ground + layer;
                  if (world.isOutsideBuildHeight(y)) {
                     break;
                  }

                  double w = s[3] * (1.0 - (double)layer / s[2]) + 0.6;
                  double lean = layer * 0.3;
                  int cx = x0 + (int)Math.round(Math.cos(out) * lean);
                  int cz = z0 + (int)Math.round(Math.sin(out) * lean);

                  for (int dx = -2; dx <= 2; dx++) {
                     for (int dz = -2; dz <= 2; dz++) {
                        if (!(dx * dx + dz * dz > w * w)) {
                           pos.set(cx + dx, y, cz + dz);
                           used++;
                           LevelChunk at = world.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
                           if (at != null) {
                              BlockState state = at.getBlockState(pos);
                              if (state.canBeReplaced() && SpatialDisplacementManager.canDisplace(world, pos, state)) {
                                 int roll = ((cx + dx) * 73856093 ^ y * 19349663 ^ (cz + dz) * 83492791) & 15;
                                 Block rock = roll < 2 ? Blocks.MAGMA_BLOCK : (roll < 9 ? Blocks.BLACKSTONE : Blocks.BASALT);
                                 world.setBlock(pos, rock.defaultBlockState(), 18);
                              }
                           }
                        }
                     }
                  }
               }
            }
         }

         return used;
      } else {
         return 0;
      }
   }

   static int pressShape(double progress, double contactRadius, double r) {
      if (progress <= 0.0 || contactRadius <= 0.0) {
         return 0;
      }

      if (r < contactRadius) {
         return (int)Math.round(4.0 * progress * (1.0 - r * r / (contactRadius * contactRadius)));
      }

      double lip = (r - contactRadius) / 3.0;
      return lip < 1.0 ? -((int)Math.round(2.0 * progress * (1.0 - lip))) : 0;
   }

   int press(ServerLevel world, int budget, double impactAge) {
      if (this.radius >= 8 && budget > 0) {
         double visual = FugaSequence.visualAge(impactAge);
         double t = Math.max(0.0, Math.min(1.0, (visual - 24.0) / 14.0));
         double progress = t * t * (3.0 - 2.0 * t);
         int maxContact = Math.min(26, (int)(this.radius * 0.55));
         double contact = maxContact * progress;
         if (this.pressGround == null) {
            this.pressSpan = maxContact + 3 + 1;
            int n = (2 * this.pressSpan + 1) * (2 * this.pressSpan + 1);
            this.pressGround = new int[n];
            this.pressDone = new int[n];
            Arrays.fill(this.pressGround, Integer.MIN_VALUE);
         }

         int used = 0;
         MutableBlockPos pos = new MutableBlockPos();

         for (int dx = -this.pressSpan; dx <= this.pressSpan && used < budget; dx++) {
            for (int dz = -this.pressSpan; dz <= this.pressSpan && used < budget; dz++) {
               int target = pressShape(progress, contact, Math.sqrt(dx * dx + dz * dz));
               int i = (dx + this.pressSpan) * (2 * this.pressSpan + 1) + dz + this.pressSpan;
               if (target != this.pressDone[i]) {
                  int x = this.center.getX() + dx;
                  int z = this.center.getZ() + dz;
                  LevelChunk chunk = world.getChunkSource().getChunkNow(x >> 4, z >> 4);
                  if (chunk != null) {
                     if (this.pressGround[i] == Integer.MIN_VALUE) {
                        this.pressGround[i] = chunk.getHeight(Types.WORLD_SURFACE, x & 15, z & 15);
                     }

                     int ground = this.pressGround[i];

                     while (this.pressDone[i] < target && used < budget) {
                        pos.set(x, ground - this.pressDone[i], z);
                        used++;
                        if (world.isOutsideBuildHeight(pos.getY())) {
                           break;
                        }

                        BlockState state = chunk.getBlockState(pos);
                        if (!state.isAir() && !SpatialDisplacementManager.canDisplace(world, pos, state)) {
                           this.pressDone[i] = target;
                           break;
                        }

                        world.setBlock(pos, Blocks.AIR.defaultBlockState(), 50);
                        this.pressDone[i]++;
                        this.scorch(world, chunk, pos.move(0, -1, 0), dx, dz);
                     }

                     while (this.pressDone[i] > target && used < budget) {
                        pos.set(x, ground + 1 - this.pressDone[i], z);
                        used++;
                        if (world.isOutsideBuildHeight(pos.getY())) {
                           break;
                        }

                        BlockState state = chunk.getBlockState(pos);
                        if (!state.canBeReplaced() || !SpatialDisplacementManager.canDisplace(world, pos, state)) {
                           this.pressDone[i] = target;
                           break;
                        }

                        int roll = (x * 73856093 ^ pos.getY() * 19349663 ^ z * 83492791) & 15;
                        world.setBlock(pos, (roll < 3 ? Blocks.BLACKSTONE : (roll < 6 ? Blocks.BASALT : Blocks.COARSE_DIRT)).defaultBlockState(), 18);
                        this.pressDone[i]--;
                     }
                  }
               }
            }
         }

         return used;
      } else {
         return 0;
      }
   }

   private void scorch(ServerLevel world, LevelChunk chunk, MutableBlockPos pos, int dx, int dz) {
      if (pos.getY() >= world.getMinBuildHeight()) {
         BlockState state = chunk.getBlockState(pos);
         if (!state.isAir() && SpatialDisplacementManager.canDisplace(world, pos, state)) {
            int roll = dx * 374761393 + dz * 668265263;
            roll = (roll ^ roll >>> 13) * 1274126177;
            roll = (roll ^ roll >>> 16) & 127;
            Block burnt = roll < 13 ? Blocks.MAGMA_BLOCK : (roll < 52 ? Blocks.BLACKSTONE : (roll < 70 ? Blocks.BASALT : null));
            if (burnt != null) {
               world.setBlock(pos, burnt.defaultBlockState(), 50);
            }
         }
      }
   }
}
