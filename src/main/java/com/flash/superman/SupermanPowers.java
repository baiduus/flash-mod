package com.flash.superman;

import com.flash.network.FlashNetwork;
import com.flash.network.SupermanSyncPacket;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.PacketDistributor;

/**
 * Superman (Kryptonian power). A key switches between Clark Kent (no powers) and
 * Superman. Solar energy (0..100) recharges under the sun, faster high up, and
 * pays for the abilities. Absorbing sunlight and fighting fills the progress
 * bar; a full bar is a new level (1..10): stronger, tougher, faster flight and a
 * new ability ({@link SupermanAbility}). Only one power at a time (removes the
 * Hulk and the Acceleration Force). Stored in persistent data.
 */
public final class SupermanPowers {

    public static final String KEY = "flash_superman";
    private static final String FORM = "superman";
    private static final String LEVEL = "level";
    private static final String XP = "xp";
    private static final String ENERGY = "energy";
    private static final String FLAGS = "flags";
    private static final String DESTROY = "destroyBlocks";
    private static final String COOLDOWNS = "cooldowns";
    private static final String NO_COOLDOWNS = "noCooldowns";
    private static final String INFINITE = "infiniteEnergy";
    private static final String SUIT = "suit";
    private static final String COMBO = "combo";
    private static final String LASER_CUT = "laserCut";
    /** Game time when a temporary power (the Kryptonian baby) ends, 0 = permanent. */
    private static final String TEMP_UNTIL = "tempUntil";

    public static final int MAX_LEVEL = 10;
    public static final float MAX_ENERGY = 100.0F;
    public static final float MAX_XP = 100.0F;
    /** Height above which the sun recharges three times faster. */
    public static final int HIGH_ALTITUDE = 200;

    // flags (bit mask, synced to everyone around for the visuals)
    public static final int F_FLIGHT = 1;
    public static final int F_SPEED = 2;
    public static final int F_SLOW = 4;
    public static final int F_HEAT = 8;
    public static final int F_FREEZE = 16;
    public static final int F_BREATH = 32;
    public static final int F_CHARGE = 64;
    public static final int F_ULTIMATE = 128;
    public static final int F_HOLDING = 256;
    public static final int F_COMBO = 512;
    /** Heat vision in laser mode (crouch + key): stronger, melts faster, costs more. */
    public static final int F_LASER = 1024;
    /** Really in the air with flight on (the client decides it; synced so others see the flight poses). */
    public static final int F_FLYING = 2048;
    /** Flags that only live while a key is held (cleared on login, death, form change). */
    public static final int TRANSIENT = F_HEAT | F_FREEZE | F_BREATH | F_CHARGE | F_ULTIMATE | F_HOLDING | F_COMBO
            | F_LASER;

    private static final UUID DAMAGE_ID = UUID.fromString("7c1d2e63-8a24-4f55-a1b2-3c4d5e6f7a01");
    private static final UUID SPEED_ID = UUID.fromString("7c1d2e63-8a24-4f55-a1b2-3c4d5e6f7a02");
    private static final UUID HEALTH_ID = UUID.fromString("7c1d2e63-8a24-4f55-a1b2-3c4d5e6f7a03");
    private static final UUID KNOCKBACK_ID = UUID.fromString("7c1d2e63-8a24-4f55-a1b2-3c4d5e6f7a04");
    private static final UUID PUNCH_ID = UUID.fromString("7c1d2e63-8a24-4f55-a1b2-3c4d5e6f7a05");
    private static final UUID REACH_ID = UUID.fromString("7c1d2e63-8a24-4f55-a1b2-3c4d5e6f7a06");
    private static final UUID STEP_ID = UUID.fromString("7c1d2e63-8a24-4f55-a1b2-3c4d5e6f7a07");
    private static final UUID RUN_ID = UUID.fromString("7c1d2e63-8a24-4f55-a1b2-3c4d5e6f7a08");

    private SupermanPowers() {
    }

    // ------------------------------------------------------------------
    // Queries
    // ------------------------------------------------------------------

    private static CompoundTag tag(Player player) {
        return player.getPersistentData().getCompound(KEY);
    }

    private static void save(Player player, CompoundTag tag) {
        player.getPersistentData().put(KEY, tag);
    }

    public static boolean has(Player player) {
        return player.getPersistentData().contains(KEY);
    }

    /** True in the Superman form (false = Clark Kent). */
    public static boolean isSuperman(Player player) {
        return has(player) && tag(player).getBoolean(FORM);
    }

    public static int level(Player player) {
        return has(player) ? Math.max(1, Math.min(MAX_LEVEL, tag(player).getInt(LEVEL))) : 0;
    }

    public static float xp(Player player) {
        return has(player) ? tag(player).getFloat(XP) : 0.0F;
    }

    public static float energy(Player player) {
        if (!has(player)) {
            return 0.0F;
        }
        return infiniteEnergy(player) ? MAX_ENERGY : tag(player).getFloat(ENERGY);
    }

    public static int flags(Player player) {
        return has(player) ? tag(player).getInt(FLAGS) : 0;
    }

    public static boolean flag(Player player, int flag) {
        return (flags(player) & flag) != 0;
    }

    public static boolean destroyBlocks(Player player) {
        return has(player) && tag(player).getBoolean(DESTROY);
    }

    public static boolean noCooldowns(Player player) {
        return has(player) && tag(player).getBoolean(NO_COOLDOWNS);
    }

    public static int suit(Player player) {
        return has(player) ? SupermanSuits.clamp(tag(player).getInt(SUIT)) : 0;
    }

    public static void setSuit(ServerPlayer player, int index) {
        if (!has(player)) {
            return;
        }
        CompoundTag tag = tag(player).copy();
        tag.putInt(SUIT, SupermanSuits.clamp(index));
        save(player, tag);
        sync(player);
    }

    /** Ticks left of a temporary power, 0 when permanent (or none). */
    public static int temporaryTicks(Player player) {
        if (!has(player)) {
            return 0;
        }
        long until = tag(player).getLong(TEMP_UNTIL);
        return until <= 0L ? 0 : (int) Math.max(0L, until - player.level().getGameTime());
    }

    /** Temporary Superman (the Kryptonian baby): granted and transformed at once, gone when the time runs out. */
    /** Kryptonian baby: Superman for good, starting at level 1 (an old temporary power becomes permanent). */
    public static void grantFromBaby(ServerPlayer player) {
        if (has(player)) {
            if (tag(player).getLong(TEMP_UNTIL) > 0L) {
                CompoundTag tag = tag(player).copy();
                tag.remove(TEMP_UNTIL);
                save(player, tag);
            }
            if (!isSuperman(player)) {
                setForm(player, true);
            } else {
                sync(player);
            }
        } else {
            grant(player, true);
        }
        player.displayClientMessage(Component.translatable("message.flash.superman_baby"), false);
    }

    public static void grantTemporary(ServerPlayer player, int ticks) {
        boolean had = has(player);
        if (had && tag(player).getLong(TEMP_UNTIL) <= 0L) {
            // already permanent: just transforms
            if (!isSuperman(player)) {
                setForm(player, true);
            }
            return;
        }
        if (!had) {
            grant(player, false);
        }
        CompoundTag tag = tag(player).copy();
        long base = Math.max(player.level().getGameTime(), tag.getLong(TEMP_UNTIL));
        tag.putLong(TEMP_UNTIL, base + ticks);
        save(player, tag);
        if (!isSuperman(player)) {
            setForm(player, true);
        } else {
            sync(player);
        }
        player.displayClientMessage(Component.translatable("message.flash.superman_temporary", ticks / 1200), false);
    }

    public static boolean infiniteEnergy(Player player) {
        return has(player) && tag(player).getBoolean(INFINITE);
    }

    /** Strength of the abilities: 0.6 at level 1 up to 1.05 at level 10. */
    public static float power(Player player) {
        // the abilities follow the punch, as the Hulk's: their base values are twelfths of a punch (24 = two
        // punches); about 1x at level 1 up to 3.7x at level 10
        return (float) (1.0D + bonusDamage(level(player))) / 12.0F;
    }

    /** Damage taken in the Superman form is multiplied by this (steel body). */
    public static float damageFactor(Player player) {
        // tougher every level: 80 % less damage at level 1, 91 % at level 10. Set from the Warden: its slap took
        // 2.5 hearts and should take 1, so the old factor (51 % / 77 %) times 0.4
        float old = Math.max(0.07F, 0.48F - 0.041F * level(player));
        float once = 0.15F + 0.85F * old;
        return (0.05F + 0.85F * once) * 0.4F;
    }

    /** Extra punch damage (a normal empty-hand punch is 1). */
    public static double bonusDamage(int level) {
        // the Hulk's punch minus 10 %: 10.8 at level 1 up to 43.2 at level 10
        return (8.0D + 4.0D * Math.max(1, level)) * 0.9D;
    }

    public static double punchDamage(int level) {
        return 1.0D + bonusDamage(level);
    }

    /**
     * Vanilla flying speed: 0, the client drives the aimed flight itself (see SupermanClient), so the
     * creative flight input adds nothing on top.
     */
    public static float flySpeed(int level) {
        // the aimed flight moves the player itself (SupermanClient): the vanilla flight speed stays off
        return 0.0F;
    }

    /** Old creative-style speed (unused). */
    public static float oldFlySpeed(int level) {
        return 0.06F + 0.012F * Math.max(1, level);
    }

    // ------------------------------------------------------------------
    // Getting / losing the power
    // ------------------------------------------------------------------

    public static boolean grant(ServerPlayer player, boolean transform) {
        if (has(player)) {
            if (transform && !isSuperman(player)) {
                setForm(player, true);
            }
            return false;
        }
        clearOtherPowers(player);
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(FORM, false);
        tag.putInt(LEVEL, 1);
        tag.putFloat(XP, 0.0F);
        tag.putFloat(ENERGY, MAX_ENERGY);
        tag.putBoolean(DESTROY, true);
        save(player, tag);
        player.displayClientMessage(Component.translatable("message.flash.superman_granted"), false);
        if (transform) {
            setForm(player, true);
        } else {
            sync(player);
        }
        return true;
    }

    public static void revoke(ServerPlayer player) {
        if (!has(player)) {
            return;
        }
        SupermanAbilities.stopAll(player);
        stopFlight(player);
        player.getPersistentData().remove(KEY);
        removeAttributes(player);
        sync(player);
    }

    /** Only one power at a time: the Hulk and the speedster powers go away. */
    private static void clearOtherPowers(ServerPlayer player) {
        if (com.flash.hulk.HulkPowers.has(player)) {
            com.flash.hulk.HulkPowers.revoke(player);
        }
        if (com.flash.power.FlashPowers.hasPowers(player) || com.flash.power.FlashPowers.hasPermanentPowers(player)
                || player.getPersistentData().contains(com.flash.power.FlashV9.KEY)) {
            com.flash.power.FlashPowers.revoke(player);
            player.getPersistentData().remove(com.flash.power.FlashV9.KEY);
            com.flash.ability.FlashAbilityManager.reset(player);
            com.flash.power.FlashSpeed.remove(player);
            com.flash.power.FlashStep.remove(player);
            com.flash.power.FlashStatusEffects.reset(player);
            com.flash.network.FlashSyncPacket.send(player);
            com.flash.network.FlashVisualSyncPacket.send(player, 0);
        }
    }

    // ------------------------------------------------------------------
    // Form
    // ------------------------------------------------------------------

    public static void toggle(ServerPlayer player) {
        if (!has(player)) {
            player.displayClientMessage(Component.translatable("message.flash.superman_none"), true);
            return;
        }
        setForm(player, !isSuperman(player));
    }

    public static void setForm(ServerPlayer player, boolean superman) {
        SupermanAbilities.stopAll(player);
        CompoundTag tag = tag(player).copy();
        tag.putBoolean(FORM, superman);
        tag.putInt(FLAGS, superman ? tag.getInt(FLAGS) & ~TRANSIENT & ~F_SPEED & ~F_SLOW : 0);
        save(player, tag);
        if (superman) {
            applyAttributes(player);
            player.setHealth(player.getMaxHealth());
            if (player.level() instanceof ServerLevel level) {
                level.sendParticles(com.flash.registry.ModParticles.SM_SPARKLE.get(), player.getX(),
                        player.getY() + 1.0D, player.getZ(), 40, 0.5D, 1.0D, 0.5D, 0.1D);
                level.sendParticles(com.flash.registry.ModParticles.SM_SOLAR_GLOW.get(), player.getX(),
                        player.getY() + 1.0D, player.getZ(), 6, 0.3D, 0.6D, 0.3D, 0.02D);
                level.sendParticles(ParticleTypes.FLASH, player.getX(), player.getY() + 1.0D, player.getZ(),
                        1, 0.0D, 0.0D, 0.0D, 0.0D);
            }
            // the shirt rips open, then the reveal
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    com.flash.registry.ModSounds.CLARK_RIP.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    com.flash.registry.ModSounds.CLARK_REVEAL.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
            player.displayClientMessage(Component.translatable("message.flash.superman_form"), true);
        } else {
            stopFlight(player);
            removeAttributes(player);
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    com.flash.registry.ModSounds.CLARK_SPIN.get(), SoundSource.PLAYERS, 0.8F, 1.0F);
            player.displayClientMessage(Component.translatable("message.flash.superman_clark"), true);
        }
        sync(player);
    }

    // ------------------------------------------------------------------
    // Changes
    // ------------------------------------------------------------------

    public static void setFlag(ServerPlayer player, int flag, boolean on) {
        if (!has(player)) {
            return;
        }
        CompoundTag tag = tag(player).copy();
        int before = tag.getInt(FLAGS);
        int after = on ? before | flag : before & ~flag;
        if (before == after) {
            return;
        }
        tag.putInt(FLAGS, after);
        save(player, tag);
        sync(player);
    }

    public static void clearTransient(ServerPlayer player) {
        if (!has(player)) {
            return;
        }
        CompoundTag tag = tag(player).copy();
        tag.putInt(FLAGS, tag.getInt(FLAGS) & ~TRANSIENT);
        save(player, tag);
    }

    public static void setEnergy(ServerPlayer player, float value) {
        if (!has(player)) {
            return;
        }
        CompoundTag tag = tag(player).copy();
        tag.putFloat(ENERGY, Math.max(0.0F, Math.min(MAX_ENERGY, value)));
        save(player, tag);
    }

    /** @return false (and nothing spent) when there is not enough energy. */
    public static boolean spend(ServerPlayer player, float amount) {
        if (infiniteEnergy(player) || amount <= 0.0F) {
            return true;
        }
        float e = energy(player);
        if (e < amount) {
            return false;
        }
        setEnergy(player, e - amount);
        return true;
    }

    public static void setInfiniteEnergy(ServerPlayer player, boolean value) {
        CompoundTag tag = tag(player).copy();
        tag.putBoolean(INFINITE, value);
        save(player, tag);
        sync(player);
    }

    /** Move of the combo key: 0 Super Combo, 1 Uplift, 2 Uplift Combo, 3 Chokehold, 4 Eye Pierce. */
    public static int combo(Player player) {
        return Math.floorMod(tag(player).getInt(COMBO), SupermanCombos.COUNT);
    }

    public static void setCombo(ServerPlayer player, int combo) {
        if (!has(player)) {
            return;
        }
        CompoundTag tag = tag(player).copy();
        tag.putInt(COMBO, Math.floorMod(combo, SupermanCombos.COUNT));
        save(player, tag);
        syncSelf(player);
    }

    /** Heat vision and laser cut off limbs. */
    public static boolean laserCut(Player player) {
        return tag(player).getBoolean(LASER_CUT);
    }

    public static void setLaserCut(ServerPlayer player, boolean cut) {
        if (!has(player)) {
            return;
        }
        CompoundTag tag = tag(player).copy();
        tag.putBoolean(LASER_CUT, cut);
        save(player, tag);
        syncSelf(player);
    }

    public static void setDestroy(ServerPlayer player, boolean destroy) {
        if (!has(player)) {
            return;
        }
        CompoundTag tag = tag(player).copy();
        tag.putBoolean(DESTROY, destroy);
        save(player, tag);
        syncSelf(player);
    }

    public static void setLevel(ServerPlayer player, int level, float xpPercent) {
        if (!has(player)) {
            grant(player, false);
        }
        CompoundTag tag = tag(player).copy();
        tag.putInt(LEVEL, Math.max(1, Math.min(MAX_LEVEL, level)));
        tag.putFloat(XP, Math.max(0.0F, Math.min(MAX_XP, xpPercent)));
        save(player, tag);
        if (isSuperman(player)) {
            applyAttributes(player);
        }
        sync(player);
    }

    /** Sunlight and fights fill the progress bar; a full bar is a new level. */
    /** XP from a level to the next: 150 at level 1, 360 at 2, 630 at 3 ... 2970 at 9. */
    public static float xpNeeded(int level) {
        int l = Math.max(1, level);
        return 150.0F * l + 30.0F * l * (l - 1);
    }

    public static void addXp(ServerPlayer player, float amount) {
        if (!has(player) || amount <= 0.0F) {
            return;
        }
        CompoundTag tag = tag(player).copy();
        int level = Math.max(1, tag.getInt(LEVEL));
        if (level >= MAX_LEVEL) {
            return;
        }
        // each level takes longer: XP stored as % of the bar, the gain divided by what the level needs
        float xp = tag.getFloat(XP) + amount * 100.0F / xpNeeded(level);
        if (xp >= MAX_XP) {
            xp = 0.0F;
            level++;
            tag.putInt(LEVEL, level);
            tag.putFloat(XP, xp);
            save(player, tag);
            player.displayClientMessage(Component.translatable("message.flash.superman_level", level), false);
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    net.minecraft.sounds.SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0F, 1.2F);
            if (player.level() instanceof ServerLevel sl) {
                sl.sendParticles(com.flash.registry.ModParticles.SM_SPARKLE.get(), player.getX(),
                        player.getY() + 1.0D, player.getZ(), 60, 0.6D, 1.0D, 0.6D, 0.2D);
            }
            if (isSuperman(player)) {
                applyAttributes(player);
            }
            sync(player);
            return;
        }
        tag.putFloat(XP, xp);
        save(player, tag);
    }

    // ------------------------------------------------------------------
    // Cooldowns
    // ------------------------------------------------------------------

    public static int cooldown(Player player, SupermanAbility ability) {
        if (!has(player) || noCooldowns(player)) {
            return 0;
        }
        long until = tag(player).getCompound(COOLDOWNS).getLong(ability.id());
        return (int) Math.max(0L, until - player.level().getGameTime());
    }

    public static void setCooldown(ServerPlayer player, SupermanAbility ability) {
        if (ability.cooldown() <= 0 || noCooldowns(player)) {
            return;
        }
        CompoundTag tag = tag(player).copy();
        CompoundTag cds = tag.getCompound(COOLDOWNS);
        cds.putLong(ability.id(), player.level().getGameTime() + ability.cooldown());
        tag.put(COOLDOWNS, cds);
        save(player, tag);
        syncSelf(player);
    }

    public static void resetCooldowns(ServerPlayer player, Boolean disable) {
        CompoundTag tag = tag(player).copy();
        tag.remove(COOLDOWNS);
        if (disable != null) {
            tag.putBoolean(NO_COOLDOWNS, disable);
        }
        save(player, tag);
        sync(player);
    }

    // ------------------------------------------------------------------
    // Tick
    // ------------------------------------------------------------------

    /** True when the player is under the open daytime sky. */
    public static boolean inSunlight(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        if (!level.dimensionType().hasSkyLight() || !level.isDay() || level.isRainingAt(player.blockPosition())) {
            return false;
        }
        return level.canSeeSky(BlockPos.containing(player.getX(), player.getEyeY(), player.getZ()));
    }

    /** Night under the open sky (the moon recharges Superman high up, slower than the sun). */
    public static boolean moonlight(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        if (!level.dimensionType().hasSkyLight() || level.isDay() || level.isRainingAt(player.blockPosition())) {
            return false;
        }
        return level.canSeeSky(BlockPos.containing(player.getX(), player.getEyeY(), player.getZ()));
    }

    public static void tick(ServerPlayer player) {
        if (!has(player)) {
            return;
        }
        long tempUntil = tag(player).getLong(TEMP_UNTIL);
        if (tempUntil > 0L && player.level().getGameTime() >= tempUntil) {
            // the Kryptonian baby wore off
            revoke(player);
            player.displayClientMessage(Component.translatable("message.flash.superman_temporary_end"), false);
            return;
        }
        boolean superman = isSuperman(player);
        boolean sun = inSunlight(player);
        boolean high = player.getY() > HIGH_ALTITUDE;
        // solar energy: full in about 40 s under the sun, three times faster high up; at night, up at the same
        // height, the moonlight recharges too (a third of the day rate)
        boolean moon = !sun && high && moonlight(player);
        float gain = sun ? (high ? 0.36F : 0.12F) : moon ? 0.12F : 0.015F;
        float e = tag(player).getFloat(ENERGY);
        if (e < MAX_ENERGY) {
            setEnergy(player, e + gain);
        }
        if (sun) {
            addXp(player, high ? 0.075F : 0.025F);
        }
        boolean airborne = superman && player.getAbilities().flying;
        if (airborne != flag(player, F_FLYING)) {
            setFlag(player, F_FLYING, airborne);
        }
        if (superman) {
            // Superman can always fly: flight key or double jump
            if (!flag(player, F_FLIGHT)) {
                setFlag(player, F_FLIGHT, true);
            }
            keepFlight(player);
            if (player.isInWater()) {
                player.setAirSupply(player.getMaxAirSupply());
            }
            applyRun(player, flag(player, F_SPEED));
            // the sun heals: Regeneration II under the open sky by day, III up high where it recharges;
            // in caves and at night the normal regeneration of the game
            if (sun) {
                int amp = high ? 2 : 1;
                var regen = player.getEffect(net.minecraft.world.effect.MobEffects.REGENERATION);
                // renewed while it still has a few seconds (the effect heals on its own timer: renewing it every
                // tick would break the rhythm)
                if (regen == null || regen.getAmplifier() < amp || regen.getDuration() < 60) {
                    player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                            net.minecraft.world.effect.MobEffects.REGENERATION, 120, amp, true, false, true));
                }
            }
            SupermanMeteor.tick(player);
        } else {
            SupermanMeteor.clear(player);
        }
        SupermanAbilities.tick(player);
        // the stats (damage, health, speed, knockback...) are recalculated every 2 seconds
        if (superman && player.tickCount % 40 == 0) {
            applyAttributes(player);
        }
        if (player.tickCount % 10 == 0) {
            syncSelf(player);
        }
    }

    // ------------------------------------------------------------------
    // Flight
    // ------------------------------------------------------------------

    public static void startFlight(ServerPlayer player) {
        var abilities = player.getAbilities();
        abilities.mayfly = true;
        abilities.flying = true;
        abilities.setFlyingSpeed(flySpeed(level(player)));
        player.onUpdateAbilities();
        player.fallDistance = 0.0F;
        player.setDeltaMovement(player.getDeltaMovement().add(0.0D, 0.6D, 0.0D));
        player.hurtMarked = true;
    }

    private static void keepFlight(ServerPlayer player) {
        var abilities = player.getAbilities();
        float speed = flySpeed(level(player));
        if (!abilities.mayfly || Math.abs(abilities.getFlyingSpeed() - speed) > 1.0E-4F) {
            abilities.mayfly = true;
            abilities.setFlyingSpeed(speed);
            player.onUpdateAbilities();
        }
        // only while flying: a real fall still counts (more than 10 blocks = the hero landing)
        if (abilities.flying) {
            player.fallDistance = 0.0F;
        }
    }

    public static void stopFlight(ServerPlayer player) {
        var abilities = player.getAbilities();
        boolean creative = player.isCreative() || player.isSpectator();
        if (!creative && (abilities.mayfly || abilities.flying)) {
            abilities.mayfly = false;
            abilities.flying = false;
        }
        abilities.setFlyingSpeed(0.05F);
        player.onUpdateAbilities();
        CompoundTag tag = tag(player).copy();
        if (has(player)) {
            tag.putInt(FLAGS, tag.getInt(FLAGS) & ~F_FLIGHT);
            save(player, tag);
        }
    }

    // ------------------------------------------------------------------
    // Attributes
    // ------------------------------------------------------------------

    private static void applyRun(ServerPlayer player, boolean on) {
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) {
            return;
        }
        boolean present = speed.getModifier(RUN_ID) != null;
        if (on && !present) {
            // four times faster
            speed.addTransientModifier(new AttributeModifier(RUN_ID, "superman_run", 3.0D,
                    AttributeModifier.Operation.MULTIPLY_TOTAL));
        } else if (!on && present) {
            speed.removeModifier(RUN_ID);
        }
    }

    public static void applyAttributes(ServerPlayer player) {
        if (!isSuperman(player)) {
            removeAttributes(player);
            return;
        }
        int level = level(player);
        set(player, Attributes.ATTACK_DAMAGE, DAMAGE_ID, bonusDamage(level), AttributeModifier.Operation.ADDITION);
        set(player, Attributes.MOVEMENT_SPEED, SPEED_ID, 0.15D + 0.03D * level, AttributeModifier.Operation.MULTIPLY_BASE);
        set(player, Attributes.MAX_HEALTH, HEALTH_ID, 4.0D + 3.0D * level, AttributeModifier.Operation.ADDITION);
        set(player, Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK_ID, 0.5D + 0.05D * level, AttributeModifier.Operation.ADDITION);
        set(player, Attributes.ATTACK_KNOCKBACK, PUNCH_ID, 1.0D + 0.2D * level, AttributeModifier.Operation.ADDITION);
        set(player, net.minecraftforge.common.ForgeMod.ENTITY_REACH.get(), REACH_ID, 1.0D, AttributeModifier.Operation.ADDITION);
        set(player, net.minecraftforge.common.ForgeMod.STEP_HEIGHT_ADDITION.get(), STEP_ID, 0.6D, AttributeModifier.Operation.ADDITION);
    }

    private static void set(ServerPlayer player, Attribute attribute, UUID id, double amount,
                            AttributeModifier.Operation op) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        AttributeModifier current = instance.getModifier(id);
        if (current != null && Math.abs(current.getAmount() - amount) < 1.0E-6D) {
            return;
        }
        if (current != null) {
            instance.removeModifier(id);
        }
        instance.addTransientModifier(new AttributeModifier(id, "superman", amount, op));
    }

    public static void removeAttributes(ServerPlayer player) {
        remove(player, Attributes.ATTACK_DAMAGE, DAMAGE_ID);
        remove(player, Attributes.MOVEMENT_SPEED, SPEED_ID);
        remove(player, Attributes.MOVEMENT_SPEED, RUN_ID);
        remove(player, Attributes.MAX_HEALTH, HEALTH_ID);
        remove(player, Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK_ID);
        remove(player, Attributes.ATTACK_KNOCKBACK, PUNCH_ID);
        remove(player, net.minecraftforge.common.ForgeMod.ENTITY_REACH.get(), REACH_ID);
        remove(player, net.minecraftforge.common.ForgeMod.STEP_HEIGHT_ADDITION.get(), STEP_ID);
        if (player.getHealth() > player.getMaxHealth()) {
            player.setHealth(player.getMaxHealth());
        }
    }

    private static void remove(ServerPlayer player, Attribute attribute, UUID id) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance != null && instance.getModifier(id) != null) {
            instance.removeModifier(id);
        }
    }

    // ------------------------------------------------------------------
    // Sync
    // ------------------------------------------------------------------

    private static SupermanSyncPacket packet(ServerPlayer player) {
        SupermanAbility[] abilities = SupermanAbility.values();
        int[] cds = new int[abilities.length];
        for (int i = 0; i < abilities.length; i++) {
            cds[i] = cooldown(player, abilities[i]);
        }
        return new SupermanSyncPacket(player.getUUID(), has(player), isSuperman(player), level(player), xp(player),
                energy(player), flags(player), destroyBlocks(player), cds, noCooldowns(player),
                infiniteEnergy(player), inSunlight(player), suit(player), temporaryTicks(player),
                combo(player) | (laserCut(player) ? 16 : 0));
    }

    public static void sync(ServerPlayer player) {
        FlashNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player), packet(player));
    }

    public static void syncSelf(ServerPlayer player) {
        FlashNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet(player));
    }

    public static void syncTo(ServerPlayer listener, ServerPlayer target) {
        FlashNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> listener), packet(target));
    }

    /** Death keeps the power, the level and the energy; the player comes back as Clark Kent. */
    public static void copy(Player from, Player to, boolean death) {
        if (from.getPersistentData().contains(KEY)) {
            CompoundTag tag = from.getPersistentData().getCompound(KEY).copy();
            if (death) {
                tag.putBoolean(FORM, false);
                tag.putInt(FLAGS, 0);
            } else {
                tag.putInt(FLAGS, tag.getInt(FLAGS) & ~TRANSIENT);
            }
            to.getPersistentData().put(KEY, tag);
        }
    }
}
