package com.flash.godwand;

import com.mojang.math.Transformation;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class SpatialMath {
   private static final Vector3f PIVOT = new Vector3f(0.5F, 0.5F, 0.5F);

   public static Vec3 pathLooseBlockVelocity(Vec3 position, Vec3 center, Vec3 forward) {
      Vec3 side = new Vec3(-forward.z, 0.0, forward.x).normalize();
      double sign = position.subtract(center).dot(side) >= 0.0 ? 1.0 : -1.0;
      return side.scale(sign * 0.65).add(0.0, 0.18, 0.0);
   }

   private SpatialMath() {
   }

   public static Transformation createPivotTransform(Vec3 offset, Quaternionf rotation, float scale) {
      Vector3f center = new Vector3f(0.5F, 0.5F, 0.5F);
      Vector3f scaledRotatedCenter = new Vector3f(center).mul(scale).rotate(rotation);
      Vector3f translation = new Vector3f(
         (float)offset.x + center.x - scaledRotatedCenter.x,
         (float)offset.y + center.y - scaledRotatedCenter.y,
         (float)offset.z + center.z - scaledRotatedCenter.z
      );
      return new Transformation(translation, rotation, new Vector3f(scale, scale, scale), null);
   }

   public static Transformation createPivotTransform(Vector3f offset, Quaternionf rotation, float scale) {
      return createPivotTransform(new Vec3(offset.x, offset.y, offset.z), rotation, scale);
   }

   public static Vector3f compensatePivot(Vector3f displacement, Quaternionf rotation) {
      Vector3f rotatedPivot = new Vector3f(PIVOT);
      rotation.transform(rotatedPivot);
      Vector3f result = new Vector3f(displacement);
      result.add(PIVOT);
      result.sub(rotatedPivot);
      return result;
   }

   public static Transformation createThinBeamTransform(Vec3 startPos, Vec3 endPos, float thickness) {
      Vec3 diff = endPos.subtract(startPos);
      float length = (float)diff.length();
      if (length < 0.01F) {
         return new Transformation(new Vector3f(0.0F, 0.0F, 0.0F), new Quaternionf(), new Vector3f(0.0F, 0.0F, 0.0F), null);
      }

      Vector3f dir = new Vector3f((float)diff.x / length, (float)diff.y / length, (float)diff.z / length);
      Quaternionf rot = new Quaternionf().rotationTo(new Vector3f(0.0F, 0.0F, 1.0F), dir);
      Vector3f localOffset = new Vector3f(-thickness * 0.5F, -thickness * 0.5F, 0.0F);
      rot.transform(localOffset);
      return new Transformation(localOffset, rot, new Vector3f(thickness, thickness, length), null);
   }

   public static Vec3 computeDynamicAuraOffset(BlockPos pos, Vec3 playerPos, Vec3 lookDirHoriz, double auraRadius, int ageTicks) {
      Vec3 blockCenter = Vec3.atCenterOf(pos);
      Vec3 fromPlayer = blockCenter.subtract(playerPos);
      double forwardProj = fromPlayer.x * lookDirHoriz.x + fromPlayer.z * lookDirHoriz.z;
      Vec3 spinePoint = new Vec3(
         playerPos.x + lookDirHoriz.x * Mth.clamp(forwardProj, -1.5, 7.0), blockCenter.y, playerPos.z + lookDirHoriz.z * Mth.clamp(forwardProj, -1.5, 7.0)
      );
      Vec3 radial = blockCenter.subtract(spinePoint);
      double perpDist = Math.sqrt(radial.x * radial.x + radial.z * radial.z);
      Vec3 rightVec = new Vec3(-lookDirHoriz.z, 0.0, lookDirHoriz.x);
      double sideSign = radial.x * rightVec.x + radial.z * rightVec.z >= 0.0 ? 1.0 : -1.0;
      Vec3 pushDir = perpDist < 0.15 ? rightVec.scale(sideSign) : new Vec3(radial.x / perpDist, 0.0, radial.z / perpDist);
      double spatialWave = Math.sin(pos.getX() * 0.5 + pos.getZ() * 0.5 - ageTicks * 0.18);
      if (perpDist > 2.0) {
         double rippleFactor = Math.max(0.0, 1.0 - (perpDist - 2.0) / 1.4);
         double rippleLift = 0.24 + (0.28 + Math.max(0.0, spatialWave) * 0.42) * rippleFactor;
         return new Vec3(pushDir.x * (0.18 + 0.35 * rippleFactor), rippleLift, pushDir.z * (0.18 + 0.35 * rippleFactor));
      } else {
         double targetCorridorRadius = auraRadius + 0.65 + spatialWave * 0.15;
         double pushOutDist = Math.max(1.35, targetCorridorRadius - perpDist);
         double relY = Math.max(0.0, blockCenter.y - playerPos.y);
         double archFactor = Math.max(0.0, 1.0 - perpDist / 2.1);
         double archLift = 0.85 + relY * 0.55 + archFactor * 1.65 + spatialWave * 0.18;
         Vec3 fullArchOffset = new Vec3(pushDir.x * pushOutDist, archLift, pushDir.z * pushOutDist);
         if (forwardProj > 1.8) {
            double approach = Mth.clamp((4.5 - forwardProj) / 2.7, 0.0, 1.0);
            double t = approach * approach * (3.0 - 2.0 * approach);
            double aloofPush = 0.2 + 0.1 * Math.abs(spatialWave);
            double aloofLift = 0.32 + 0.12 * Math.sin(pos.getX() * 0.5 + pos.getZ() * 0.5 + ageTicks * 0.14);
            Vec3 aloofOffset = new Vec3(pushDir.x * aloofPush, aloofLift, pushDir.z * aloofPush);
            return aloofOffset.lerp(fullArchOffset, t);
         } else {
            return fullArchOffset;
         }
      }
   }

   public static Quaternionf computeArchwayRotation(BlockPos pos, Vec3 playerPos, Vec3 lookDirHoriz, int ageTicks) {
      Vec3 blockCenter = Vec3.atCenterOf(pos);
      Vec3 rel = blockCenter.subtract(playerPos);
      Vec3 rightVec = new Vec3(-lookDirHoriz.z, 0.0, lookDirHoriz.x);
      double perpDist = Math.abs(rel.x * rightVec.x + rel.z * rightVec.z);
      float side = rel.x * rightVec.x + rel.z * rightVec.z >= 0.0 ? 1.0F : -1.0F;
      if (perpDist > 2.0) {
         float rippleAngle = (float)Math.toRadians(14.0 * side);
         return new Quaternionf().rotateY((float)Math.atan2(lookDirHoriz.x, lookDirHoriz.z)).rotateZ(rippleAngle);
      } else {
         float relY = (float)Mth.clamp(blockCenter.y - playerPos.y, 0.0, 3.0);
         float peelAngle = (float)Math.toRadians((38.0 + relY * 14.0) * side);
         float wavePitch = (float)Math.sin(ageTicks * 0.14 + (pos.getX() + pos.getZ()) * 0.4) * 0.16F;
         Quaternionf fullRot = new Quaternionf().rotateY((float)Math.atan2(lookDirHoriz.x, lookDirHoriz.z)).rotateZ(peelAngle).rotateX(wavePitch);
         double forwardProj = rel.x * lookDirHoriz.x + rel.z * lookDirHoriz.z;
         if (forwardProj > 1.8) {
            double approach = Mth.clamp((4.5 - forwardProj) / 2.7, 0.0, 1.0);
            float t = (float)(approach * approach * (3.0 - 2.0 * approach));
            float aloofAngle = (float)Math.toRadians(12.0 * side);
            Quaternionf aloofRot = new Quaternionf().rotateY((float)Math.atan2(lookDirHoriz.x, lookDirHoriz.z)).rotateZ(aloofAngle).rotateX(wavePitch * 0.4F);
            return new Quaternionf(aloofRot).slerp(fullRot, t);
         } else {
            return fullRot;
         }
      }
   }

   public static Vec3 computeAuraDisplacement(BlockPos pos, Vec3 playerPos, Vec3 lookDirHoriz, double auraRadius) {
      return computeDynamicAuraOffset(pos, playerPos, lookDirHoriz, auraRadius, 0);
   }

   public static boolean canOpenPathBlock(int blockY, double feetY, boolean allowDown) {
      return allowDown || blockY + 1.0 > feetY + 0.125;
   }

   public static boolean supportsPathFeet(AABB surface, AABB player) {
      return surface.minY < player.minY
         && surface.maxY <= player.minY + 0.125
         && surface.maxY >= player.minY - 0.0625
         && surface.maxX > player.minX
         && surface.minX < player.maxX
         && surface.maxZ > player.minZ
         && surface.minZ < player.maxZ;
   }

   public static double distanceToPathAura(Vec3 block, Vec3 chest, Vec3 look, double lead) {
      double y = chest.y + (block.y - chest.y) * (block.y > chest.y ? 0.8918918918918918 : 1.0);
      return distanceToAuraSpine(new Vec3(block.x, y, block.z), chest, look, lead);
   }

   public static Vec3 auraArrowIntersection(Vec3 start, Vec3 end, Vec3 center) {
      Vec3 a = start.subtract(center).multiply(0.30303030303030304, 0.30303030303030304, 0.30303030303030304);
      Vec3 d = end.subtract(start).multiply(0.30303030303030304, 0.30303030303030304, 0.30303030303030304);
      double aa = d.lengthSqr();
      if (aa < 1.0E-10) {
         return null;
      } else {
         double b = a.dot(d);
         double c = a.lengthSqr() - 1.0;
         double determinant = b * b - aa * c;
         if (determinant < 0.0) {
            return null;
         } else {
            double t = c <= 0.0 ? 0.0 : (-b - Math.sqrt(determinant)) / aa;
            if (!(t < 0.0) && !(t > 1.0)) {
               Vec3 hit = start.lerp(end, t);
               return end.subtract(start).dot(hit.subtract(center)) >= 0.0 ? null : hit;
            } else {
               return null;
            }
         }
      }
   }

   public static Vec3 deflectAuraArrow(Vec3 velocity, Vec3 normal) {
      Vec3 tangent = normal.cross(new Vec3(0.0, 1.0, 0.0));
      if (tangent.lengthSqr() < 0.001) {
         tangent = normal.cross(new Vec3(1.0, 0.0, 0.0));
      }

      tangent = tangent.normalize();
      if (tangent.dot(velocity) < 0.0) {
         tangent = tangent.reverse();
      }

      return normal.scale(0.65).add(tangent.scale(0.75)).normalize().scale(velocity.length() * 0.55);
   }

   public static Vec3 auraMobVelocity(Vec3 velocity, Vec3 normal, double distance) {
      double radial = velocity.dot(normal);
      double force = Mth.clamp((4.3 - distance) * 0.12, 0.04, 0.32);
      Vec3 response = velocity.add(normal.scale(Math.max(force, radial) - radial));
      return new Vec3(response.x, Mth.clamp(response.y, -0.15, 0.15), response.z);
   }

   public static float auraVisibility(double facing) {
      double t = Mth.clamp((facing - 0.25) / 0.65, 0.0, 1.0);
      return (float)(1.0 - 0.94 * t * t * (3.0 - 2.0 * t));
   }

   public static double distanceToAuraSpine(Vec3 blockCenter, Vec3 chestPos, Vec3 lookDirHoriz, double forwardLead) {
      Vec3 rel = blockCenter.subtract(chestPos);
      double proj = rel.x * lookDirHoriz.x + rel.z * lookDirHoriz.z;
      double clampedProj = Mth.clamp(proj, 0.0, forwardLead);
      Vec3 closestPoint = new Vec3(chestPos.x + lookDirHoriz.x * clampedProj, chestPos.y, chestPos.z + lookDirHoriz.z * clampedProj);
      return blockCenter.distanceTo(closestPoint);
   }

   public static Vec3 computeInteractiveEvasion(Vec3 actualDebrisWorldPos, Vec3 playerChestPos, double evasionRadius) {
      Vec3 diff = actualDebrisWorldPos.subtract(playerChestPos);
      double dist = diff.length();
      if (!(dist >= evasionRadius) && !(dist < 0.001)) {
         double factor = (evasionRadius - dist) / evasionRadius;
         double strength = factor * factor * 2.8;
         Vec3 dir = diff.normalize();
         return new Vec3(dir.x * strength, Math.max(0.35, dir.y * strength + 0.45 * factor), dir.z * strength);
      } else {
         return Vec3.ZERO;
      }
   }

   public static Vec3 computeCanopyVortexOffset(BlockPos pos, Vec3 blastCenter, double blastRadius, int ageTicks) {
      double dx = pos.getX() + 0.5 - blastCenter.x;
      double dz = pos.getZ() + 0.5 - blastCenter.z;
      double hash = hash01(pos);
      int ringIndex = (int)(Math.abs(pos.asLong()) % 3L);
      double baseAngle = Math.atan2(dz, dx) + hash * 0.5;
      double angularSpeed = (ringIndex == 1 ? -0.045 : 0.04) * (0.85 + hash * 0.35);
      double theta = baseAngle + ageTicks * angularSpeed;
      return computeCanopyVortexInternal(pos, blastCenter, blastRadius, ringIndex, theta, hash, 1.0);
   }

   public static Vec3 computeCanopyVortexOffset(BlockPos pos, Vec3 blastCenter, double blastRadius, float customAngle, double radiusScale) {
      double dx = pos.getX() + 0.5 - blastCenter.x;
      double dz = pos.getZ() + 0.5 - blastCenter.z;
      double hash = hash01(pos);
      int ringIndex = (int)(Math.abs(pos.asLong()) % 3L);
      double baseAngle = Math.atan2(dz, dx) + hash * 0.5;
      double dir = ringIndex == 1 ? -1.0 : 1.0;
      double theta = baseAngle + customAngle * dir * (0.85 + hash * 0.35);
      return computeCanopyVortexInternal(pos, blastCenter, blastRadius, ringIndex, theta, hash, radiusScale);
   }

   private static Vec3 computeCanopyVortexInternal(
      BlockPos pos, Vec3 blastCenter, double blastRadius, int ringIndex, double theta, double hash, double radiusScale
   ) {
      Vec3 blockCenter = Vec3.atCenterOf(pos);

      double baseR = switch (ringIndex) {
         case 0 -> blastRadius * (0.85 + hash * 0.4);
         case 1 -> blastRadius * (0.68 + hash * 0.32);
         default -> blastRadius * (1.02 + hash * 0.42);
      };
      double ringRadius = baseR * radiusScale;
      double lx = Math.cos(theta) * ringRadius;
      double lz = Math.sin(theta) * ringRadius;
      double ly = ((hash - 0.5) * 1.8 + Math.sin(theta * 2.0) * 0.35) * radiusScale;
      double rotX = 0.0;
      double rotZ = 0.0;
      if (ringIndex == 0) {
         rotZ = Math.toRadians(28.0);
      } else if (ringIndex == 1) {
         rotX = Math.toRadians(28.0);
      } else {
         rotX = Math.toRadians(-22.0);
         rotZ = Math.toRadians(-22.0);
      }

      double x1 = lx * Math.cos(rotZ) - ly * Math.sin(rotZ);
      double y1 = lx * Math.sin(rotZ) + ly * Math.cos(rotZ);
      double z1 = lz;
      double finalX = x1;
      double finalY = y1 * Math.cos(rotX) - z1 * Math.sin(rotX);
      double finalZ = y1 * Math.sin(rotX) + z1 * Math.cos(rotX);
      double targetWorldX = blastCenter.x + finalX;
      double targetWorldY = Math.max(blastCenter.y - blastRadius * 0.45, blastCenter.y + finalY);
      double targetWorldZ = blastCenter.z + finalZ;
      return new Vec3(targetWorldX - blockCenter.x, targetWorldY - blockCenter.y, targetWorldZ - blockCenter.z);
   }

   public static Vec3 computeDynamicBlackHoleOrbit(
      BlockPos pos, Vec3 corePos, double currentInfluenceRadius, float swirlAngle, float entryProgress, double collapseFactor
   ) {
      Vec3 blockCenter = Vec3.atCenterOf(pos);
      double dx = blockCenter.x - corePos.x;
      double dz = blockCenter.z - corePos.z;
      double initialHorizDist = Math.sqrt(dx * dx + dz * dz);
      double hash = hash01(pos);
      int ringIndex = (int)(Math.abs(pos.asLong()) % 3L);
      double baseAngle = Math.atan2(dz, dx) + hash * 0.8;
      double dir = ringIndex == 1 ? -1.0 : 1.0;
      double theta = baseAngle + swirlAngle * dir * (0.8 + hash * 0.45);

      double targetOrbitR = switch (ringIndex) {
         case 0 -> currentInfluenceRadius * (0.55 + hash * 0.35);
         case 1 -> currentInfluenceRadius * (0.42 + hash * 0.28);
         default -> currentInfluenceRadius * (0.68 + hash * 0.38);
      };
      targetOrbitR = Math.max(2.5, targetOrbitR) * collapseFactor;
      double smoothEntry = entryProgress * entryProgress * (3.0 - 2.0 * entryProgress);
      double currentR = Mth.lerp(smoothEntry, initialHorizDist * collapseFactor, targetOrbitR);
      double lx = Math.cos(theta) * currentR;
      double lz = Math.sin(theta) * currentR;
      double ly = ((hash - 0.5) * 3.2 + Math.sin(theta * 2.0) * 0.65) * collapseFactor;
      double rotX = ringIndex == 1 ? Math.toRadians(28.0) : (ringIndex == 2 ? Math.toRadians(-22.0) : 0.0);
      double rotZ = ringIndex == 0 ? Math.toRadians(28.0) : (ringIndex == 2 ? Math.toRadians(-22.0) : 0.0);
      double x1 = lx * Math.cos(rotZ) - ly * Math.sin(rotZ);
      double y1 = lx * Math.sin(rotZ) + ly * Math.cos(rotZ);
      double z1 = lz;
      double finalX = x1;
      double finalY = y1 * Math.cos(rotX) - z1 * Math.sin(rotX);
      double finalZ = y1 * Math.sin(rotX) + z1 * Math.cos(rotX);
      double targetWorldX = corePos.x + finalX;
      double targetWorldY = corePos.y + finalY;
      double targetWorldZ = corePos.z + finalZ;
      double currentWorldX = Mth.lerp(Math.min(1.0, smoothEntry * 1.35), blockCenter.x + lx * 0.25, targetWorldX);
      double currentWorldY = Mth.lerp(smoothEntry, blockCenter.y + 0.8, targetWorldY);
      double currentWorldZ = Mth.lerp(Math.min(1.0, smoothEntry * 1.35), blockCenter.z + lz * 0.25, targetWorldZ);
      return new Vec3(currentWorldX - blockCenter.x, currentWorldY - blockCenter.y, currentWorldZ - blockCenter.z);
   }

   public static Vec3 computeSpiralReturnOffset(Vec3 currentOffset, BlockPos pos) {
      double angle = 0.55 * ((pos.asLong() & 1L) == 0L ? 1.0 : -1.0);
      double cos = Math.cos(angle);
      double sin = Math.sin(angle);
      double rx = (currentOffset.x * cos - currentOffset.z * sin) * 0.22;
      double rz = (currentOffset.x * sin + currentOffset.z * cos) * 0.22;
      double ry = currentOffset.y * 0.18 + 0.25;
      return new Vec3(rx, ry, rz);
   }

   public static double hash01(BlockPos pos) {
      long h = pos.asLong() * -7046029254386353131L;
      h ^= h >>> 30;
      h *= -4658895280553007687L;
      h ^= h >>> 27;
      return (h & 16777215L) / 1.6777216E7;
   }

   public static double hashCoordinateToPhase(long posAsLong) {
      long h = posAsLong * 25214903917L + 11L;
      return (h >> 16 & 65535L) / 65535.0 * 2.0 * Math.PI;
   }
}
