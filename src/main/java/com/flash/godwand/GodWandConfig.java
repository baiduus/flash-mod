package com.flash.godwand;

import net.minecraft.util.Mth;

/**
 * God Wand's settings (its own defaults; no file): cooldowns by skill and level, damage, the terrain each skill
 * may change. Whether a cast may destroy terrain is the caster's (Yas') destruction switch, GodWandSkills.destroys.
 * <p>
 * Ported from God Wand by HyIsNoob (MIT for the code; its assets are used with the author's permission).
 */
public final class GodWandConfig {

    public static final Server SERVER = new Server();

    private GodWandConfig() {
    }

    private static int tierIndex(int level, int length) {
        return Mth.clamp(level - 1, 0, length - 1);
    }

    public static int skillCooldownTicks(int skill, int level) {
        int ticks = switch (skill) {
            case 0 -> SERVER.spatialPathCooldownTicks;
            case 1 -> SERVER.singularityCooldownTicks[tierIndex(level, 3)];
            case 2 -> SERVER.earthDrillCooldownTicks[tierIndex(level, 4)];
            case 3 -> SERVER.supernovaCooldownTicks;
            case 4 -> SERVER.stoneCannonCooldownTicks[tierIndex(level, 4)];
            case 5 -> SERVER.atomicCooldownTicks;
            case 6 -> SERVER.fugaCooldownTicks;
            default -> 0;
        };
        return ticks == 0 ? 0 : cooldownTicks(ticks);
    }

    public static float skillDamageMultiplier(int skill, int level) {
        return SERVER.damageMultiplier;
    }

    public static int cooldownTicks(int ticks) {
        return Math.max(1, Math.round(ticks * SERVER.cooldownMultiplier));
    }

    public static final class Server {
        public float cooldownMultiplier = 1.0F;
        public float damageMultiplier = 1.0F;
        public int spatialPathCooldownTicks = 20;
        public int[] singularityCooldownTicks = {60, 120, 200};
        public int[] earthDrillCooldownTicks = {80, 140, 220, 320};
        public int[] stoneCannonCooldownTicks = {40, 80, 140, 220};
        public int supernovaCooldownTicks = 900;
        public int atomicCooldownTicks = 1200;
        public int fugaCooldownTicks = 1200;
        public float supernovaInstantKillHealthThreshold = 50.0F;
        public boolean supernovaBlockLift = true;
        public boolean earthDrillBlockLift = true;
        public int supernovaTerrainRadius = 120;
        public int supernovaMaxDisplays = 1800;
        public int atomicTerrainRadius = 98;
        public float atomicDamage = 9999.0F;
        public float atomicShockwaveDamage = 15.0F;
        public int fugaTerrainRadius = 48;
        public float fugaDamage = 9999.0F;

        public boolean isDimensionAllowed(String dimensionId) {
            return true;
        }

        public boolean isDimensionBlacklisted(String dimensionId) {
            return false;
        }
    }
}
