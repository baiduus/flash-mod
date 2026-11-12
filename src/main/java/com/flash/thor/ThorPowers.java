package com.flash.thor;

import com.flash.network.FlashNetwork;
import com.flash.network.ThorSyncPacket;
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
 * The Thor power, kept in the player's persistent data ("flash_thor"): form (Thor / civil), level 1..10 with XP,
 * the storm energy, the Mjolnir charge (0..100 %, 150 % with Overcharge), flags of the running abilities, cooldowns
 * and the chosen suit. Only one power at a time: getting Thor takes the Flash, Hulk and Superman powers away.
 */
public final class ThorPowers {

    public static final String KEY = "flash_thor";
    private static final String FORM = "thor";
    private static final String LEVEL = "level";
    private static final String XP = "xp";
    private static final String ENERGY = "energy";
    private static final String CHARGE = "charge";
    private static final String FLAGS = "flags";
    private static final String DESTROY = "destroyBlocks";
    private static final String COOLDOWNS = "cooldowns";
    private static final String NO_COOLDOWNS = "noCooldowns";
    private static final String INFINITE = "infiniteEnergy";
    private static final String SUIT = "suit";
    private static final String HELMET = "helmetOff";
    private static final String FURY_UNTIL = "furyUntil";
    private static final String OVER_UNTIL = "overUntil";

    public static final int MAX_LEVEL = 10;
    public static final float MAX_ENERGY = 100.0F;
    public static final float MAX_XP = 100.0F;
    public static final float MAX_CHARGE = 100.0F;
    public static final float OVER_CHARGE = 150.0F;

    public static final int F_FLIGHT = 1;
    public static final int F_FLYING = 2;
    public static final int F_BLAST = 4;
    public static final int F_SPIN = 8;
    public static final int F_FURY = 16;
    /** (Old Overcharge flag: the Thunder's Fury does its job now; never set.) */
    public static final int F_OVERCHARGE = 32;
    /** Thunder's Fury: 15 s. */
    public static final int FURY_TICKS = 300;
    public static final int F_ULTIMATE = 64;
    /** Crouch + right click: the hammer drinks lightning. */
    public static final int F_CHARGING = 128;
    /** The Mjolnir is out of the hand (thrown or lying somewhere). */
    public static final int F_THROWN = 256;
    public static final int F_COMBO = 512;

    /** The weapon whirls in the hand (Cosmic Judgement, the charge of the ultimates). */
    public static final int F_TWIRL = 1024;
    /** Stormbreaker Block held: physical damage from the front is stopped. */
    public static final int F_BLOCK = 2048;
    /** Devourer King domain up: 75 % less damage taken. */
    public static final int F_DOMAIN = 4096;

    public static final int TRANSIENT = F_BLAST | F_SPIN | F_ULTIMATE | F_CHARGING | F_COMBO | F_TWIRL | F_BLOCK
            | F_DOMAIN;

    private static final UUID DAMAGE_ID = UUID.fromString("5d2a1c90-77e1-4c5b-9a0e-3f4b5c6d7e01");
    private static final UUID SPEED_ID = UUID.fromString("5d2a1c90-77e1-4c5b-9a0e-3f4b5c6d7e02");
    private static final UUID HEALTH_ID = UUID.fromString("5d2a1c90-77e1-4c5b-9a0e-3f4b5c6d7e03");
    private static final UUID KNOCKBACK_ID = UUID.fromString("5d2a1c90-77e1-4c5b-9a0e-3f4b5c6d7e04");
    private static final UUID PUNCH_ID = UUID.fromString("5d2a1c90-77e1-4c5b-9a0e-3f4b5c6d7e05");
    private static final UUID STEP_ID = UUID.fromString("5d2a1c90-77e1-4c5b-9a0e-3f4b5c6d7e06");

    private ThorPowers() {
    }

    // ------------------------------------------------------------------
    // Queries
    // ------------------------------------------------------------------

    private static final String HAMMER_GEN = "hammerGen";

    /** Which hammer of this player is the real one: each new one summoned from nowhere is a new generation, the
     * older ones vanish (no duplicates). */
    public static int hammerGen(Player player) {
        return has(player) ? tag(player).getInt(HAMMER_GEN) : 0;
    }

    public static int newHammerGen(Player player) {
        CompoundTag t = tag(player).copy();
        int g = t.getInt(HAMMER_GEN) + 1;
        t.putInt(HAMMER_GEN, g);
        save(player, t);
        return g;
    }

    static CompoundTag tag(Player player) {
        return player.getPersistentData().getCompound(KEY);
    }

    static void save(Player player, CompoundTag tag) {
        player.getPersistentData().put(KEY, tag);
    }

    public static boolean has(Player player) {
        return player.getPersistentData().contains(KEY);
    }

    /** True in the God of Thunder form (false = civil clothes). */
    public static boolean isThor(Player player) {
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

    public static float charge(Player player) {
        return has(player) ? tag(player).getFloat(CHARGE) : 0.0F;
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

    public static boolean infiniteEnergy(Player player) {
        return has(player) && tag(player).getBoolean(INFINITE);
    }

    public static int suit(Player player) {
        return has(player) ? ThorSuits.clamp(tag(player).getInt(SUIT)) : 0;
    }

    /** The weapon of the chosen suit (ThorSuits.CLASSIC / MCU / COSMIC / STORMBREAKER). */
    public static int weapon(Player player) {
        return ThorSuits.weapon(suit(player));
    }

    public static boolean stormbreaker(Player player) {
        return weapon(player) == ThorSuits.STORMBREAKER;
    }

    public static boolean cosmic(Player player) {
        return weapon(player) == ThorSuits.COSMIC;
    }

    public static boolean helmetOff(Player player) {
        return has(player) && tag(player).getBoolean(HELMET);
    }

    /** Punch of Thor (a normal empty-hand punch is 1): 9.6 at level 1 up to 42 at level 10. */
    public static double punchDamage(int level) {
        return 6.0D + 3.6D * Math.max(1, level);
    }

    /** The abilities follow the punch: their base values are twelfths of it (24 = two punches). */
    public static float power(Player player) {
        float p = (float) punchDamage(level(player)) / 12.0F;
        return flag(player, F_FURY) ? p * 1.25F : p;
    }

    /** Damage taken as Thor is multiplied by this: 40 % less at level 1, 76 % at level 10. */
    public static float damageFactor(Player player) {
        float f = Math.max(0.24F, 0.64F - 0.04F * level(player));
        return flag(player, F_FURY) ? f * 0.85F : f;
    }

    // ------------------------------------------------------------------
    // Getting / losing the power
    // ------------------------------------------------------------------

    public static boolean grant(ServerPlayer player, boolean transform) {
        if (has(player)) {
            if (transform && !isThor(player)) {
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
        tag.putFloat(CHARGE, 50.0F);
        tag.putBoolean(DESTROY, true);
        save(player, tag);
        player.displayClientMessage(Component.translatable("message.flash.thor_granted"), false);
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
        ThorAbilities.stopAll(player);
        stopFlight(player);
        ThorAbilities.takeHammerAway(player);
        player.getPersistentData().remove(KEY);
        removeAttributes(player);
        sync(player);
    }

    /** Only one power at a time. */
    private static void clearOtherPowers(ServerPlayer player) {
        if (com.flash.superman.SupermanPowers.has(player)) {
            com.flash.superman.SupermanPowers.revoke(player);
        }
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
            player.displayClientMessage(Component.translatable("message.flash.thor_none"), true);
            return;
        }
        setForm(player, !isThor(player));
    }

    public static void setForm(ServerPlayer player, boolean thor) {
        ThorAbilities.stopAll(player);
        CompoundTag tag = tag(player).copy();
        tag.putBoolean(FORM, thor);
        tag.putInt(FLAGS, thor ? tag.getInt(FLAGS) & ~TRANSIENT : 0);
        save(player, tag);
        if (thor) {
            applyAttributes(player);
            player.setHealth(player.getMaxHealth());
            ThorAbilities.giveHammer(player);
            if (player.level() instanceof ServerLevel level) {
                ThorAbilities.strikeVisual(level, player.position());
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, player.getX(), player.getY() + 1.0D, player.getZ(),
                        60, 0.5D, 1.0D, 0.5D, 0.3D);
            }
            com.flash.superman.SupermanAbilities.anim(player, "thor_thortransform");
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    com.flash.registry.ModSounds.THOR_GRAB.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
            player.displayClientMessage(Component.translatable("message.flash.thor_form"), true);
        } else {
            stopFlight(player);
            removeAttributes(player);
            player.displayClientMessage(Component.translatable("message.flash.thor_civil"), true);
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

    public static void setCharge(ServerPlayer player, float value) {
        if (!has(player)) {
            return;
        }
        // the Thunder's Fury lets the Mjolnir hold up to 150 % of charge
        float max = flag(player, F_FURY) ? OVER_CHARGE : MAX_CHARGE;
        CompoundTag tag = tag(player).copy();
        tag.putFloat(CHARGE, Math.max(0.0F, Math.min(max, value)));
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

    public static void setDestroy(ServerPlayer player, boolean destroy) {
        if (!has(player)) {
            return;
        }
        CompoundTag tag = tag(player).copy();
        tag.putBoolean(DESTROY, destroy);
        save(player, tag);
        syncSelf(player);
    }

    public static void setSuit(ServerPlayer player, int index) {
        if (!has(player)) {
            return;
        }
        CompoundTag tag = tag(player).copy();
        tag.putInt(SUIT, ThorSuits.clamp(index));
        save(player, tag);
        sync(player);
    }

    public static void setHelmetOff(ServerPlayer player, boolean off) {
        if (!has(player)) {
            return;
        }
        CompoundTag tag = tag(player).copy();
        tag.putBoolean(HELMET, off);
        save(player, tag);
        sync(player);
    }

    public static void setTimer(ServerPlayer player, int flag, int ticks) {
        CompoundTag tag = tag(player).copy();
        tag.putLong(flag == F_FURY ? FURY_UNTIL : OVER_UNTIL, player.level().getGameTime() + ticks);
        save(player, tag);
        setFlag(player, flag, true);
    }

    public static void setLevel(ServerPlayer player, int level, float xpPercent) {
        if (!has(player)) {
            grant(player, false);
        }
        CompoundTag tag = tag(player).copy();
        tag.putInt(LEVEL, Math.max(1, Math.min(MAX_LEVEL, level)));
        tag.putFloat(XP, Math.max(0.0F, Math.min(MAX_XP, xpPercent)));
        save(player, tag);
        if (isThor(player)) {
            applyAttributes(player);
        }
        sync(player);
    }

    /** XP from a level to the next: 150 at level 1, 360 at 2, 630 at 3 ... 2970 at 9 (as Superman). */
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
        float xp = tag.getFloat(XP) + amount * 100.0F / xpNeeded(level);
        if (xp >= MAX_XP) {
            level++;
            tag.putInt(LEVEL, level);
            tag.putFloat(XP, 0.0F);
            save(player, tag);
            player.displayClientMessage(Component.translatable("message.flash.thor_level", level), false);
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    net.minecraft.sounds.SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0F, 1.2F);
            if (player.level() instanceof ServerLevel sl) {
                ThorAbilities.strikeVisual(sl, player.position());
            }
            if (isThor(player)) {
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

    public static int cooldown(Player player, ThorAbility ability) {
        if (!has(player) || noCooldowns(player)) {
            return 0;
        }
        long until = tag(player).getCompound(COOLDOWNS).getLong(ability.id());
        return (int) Math.max(0L, until - player.level().getGameTime());
    }

    public static void setCooldown(ServerPlayer player, ThorAbility ability) {
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

    /** Under the open sky in a thunderstorm (or in the rain): the storm feeds the God of Thunder. */
    public static int storm(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        BlockPos at = BlockPos.containing(player.getX(), player.getEyeY(), player.getZ());
        if (!level.canSeeSky(at)) {
            return 0;
        }
        if (level.isThundering()) {
            return 2;
        }
        return level.isRainingAt(at) ? 1 : 0;
    }

    public static void tick(ServerPlayer player) {
        if (!has(player)) {
            return;
        }
        boolean thor = isThor(player);
        long now = player.level().getGameTime();
        CompoundTag t = tag(player);
        if (flag(player, F_FURY) && now >= t.getLong(FURY_UNTIL)) {
            setFlag(player, F_FURY, false);
            // the charge over 100 % goes away with the fury
            setCharge(player, charge(player));
            player.displayClientMessage(Component.translatable("message.flash.thor_fury_end"), true);
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    com.flash.registry.ModSounds.THOR_OVERCHARGE_END.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
        }
        if (flag(player, F_OVERCHARGE)) {
            setFlag(player, F_OVERCHARGE, false);
        }
        // storm energy: 1 per second, 2 in the rain, 4 in a thunderstorm; faster while the hammer drinks lightning
        int storm = storm(player);
        float gain = (storm == 2 ? 0.2F : storm == 1 ? 0.1F : 0.05F) + (flag(player, F_CHARGING) ? 0.25F : 0.0F);
        float e = t.getFloat(ENERGY);
        if (e < MAX_ENERGY) {
            setEnergy(player, e + gain);
        }
        if (storm == 2) {
            addXp(player, 0.03F);
        }
        boolean airborne = thor && player.getAbilities().flying;
        if (airborne != flag(player, F_FLYING)) {
            setFlag(player, F_FLYING, airborne);
        }
        if (thor) {
            keepFlight(player);
            // Regeneration II always, IV in the rain or a thunderstorm
            {
                var regen = player.getEffect(net.minecraft.world.effect.MobEffects.REGENERATION);
                int amp = player.level().isRaining() ? 3 : 1;
                if (regen != null && regen.isAmbient() && regen.getAmplifier() > amp) {
                    // the rain stopped: back to II
                    player.removeEffect(net.minecraft.world.effect.MobEffects.REGENERATION);
                    regen = null;
                }
                if (regen == null || regen.getAmplifier() < amp || regen.getDuration() < 60) {
                    player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                            net.minecraft.world.effect.MobEffects.REGENERATION, 120, amp, true, false, true));
                }
            }
            if (player.tickCount % 40 == 0) {
                applyAttributes(player);
            }
            // the combustion of Superman: diving from the heights Thor becomes a meteor
            com.flash.superman.SupermanMeteor.tick(player, power(player), destroyBlocks(player), true);
        } else {
            com.flash.superman.SupermanMeteor.clear(player);
        }
        ThorAbilities.tick(player);
        if (player.tickCount % 10 == 0) {
            syncSelf(player);
        }
    }

    // ------------------------------------------------------------------
    // Flight (aimed by the client, as Superman's: the vanilla speed stays 0)
    // ------------------------------------------------------------------

    public static void startFlight(ServerPlayer player) {
        var abilities = player.getAbilities();
        abilities.mayfly = true;
        abilities.flying = true;
        abilities.setFlyingSpeed(0.0F);
        player.onUpdateAbilities();
        player.fallDistance = 0.0F;
        player.setDeltaMovement(player.getDeltaMovement().add(0.0D, 0.6D, 0.0D));
        player.hurtMarked = true;
    }

    private static void keepFlight(ServerPlayer player) {
        var abilities = player.getAbilities();
        // with the weapon in hand Thor can fly: double jump takes off (as Superman), the flight key too
        boolean can = ThorAbilities.holdingHammer(player);
        if (can && !flag(player, F_FLIGHT)) {
            setFlag(player, F_FLIGHT, true);
        }
        boolean creative = player.isCreative() || player.isSpectator();
        if (can) {
            if (!abilities.mayfly || Math.abs(abilities.getFlyingSpeed()) > 1.0E-4F) {
                abilities.mayfly = true;
                abilities.setFlyingSpeed(0.0F);
                player.onUpdateAbilities();
            }
            if (abilities.flying) {
                player.fallDistance = 0.0F;
            }
        } else if (!creative && (abilities.mayfly || abilities.flying)) {
            // no hammer in hand: no flight
            abilities.mayfly = false;
            abilities.flying = false;
            abilities.setFlyingSpeed(0.05F);
            player.onUpdateAbilities();
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
        if (has(player)) {
            CompoundTag tag = tag(player).copy();
            tag.putInt(FLAGS, tag.getInt(FLAGS) & ~F_FLIGHT);
            save(player, tag);
        }
    }

    // ------------------------------------------------------------------
    // Attributes
    // ------------------------------------------------------------------

    public static void applyAttributes(ServerPlayer player) {
        if (!isThor(player)) {
            removeAttributes(player);
            return;
        }
        int level = level(player);
        set(player, Attributes.ATTACK_DAMAGE, DAMAGE_ID, punchDamage(level) - 1.0D, AttributeModifier.Operation.ADDITION);
        set(player, Attributes.MOVEMENT_SPEED, SPEED_ID, 0.1D + 0.02D * level, AttributeModifier.Operation.MULTIPLY_BASE);
        set(player, Attributes.MAX_HEALTH, HEALTH_ID, 4.0D + 2.6D * level, AttributeModifier.Operation.ADDITION);
        set(player, Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK_ID, 0.4D + 0.05D * level, AttributeModifier.Operation.ADDITION);
        set(player, Attributes.ATTACK_KNOCKBACK, PUNCH_ID, 0.8D + 0.15D * level, AttributeModifier.Operation.ADDITION);
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
        instance.addTransientModifier(new AttributeModifier(id, "thor", amount, op));
    }

    public static void removeAttributes(ServerPlayer player) {
        remove(player, Attributes.ATTACK_DAMAGE, DAMAGE_ID);
        remove(player, Attributes.MOVEMENT_SPEED, SPEED_ID);
        remove(player, Attributes.MAX_HEALTH, HEALTH_ID);
        remove(player, Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK_ID);
        remove(player, Attributes.ATTACK_KNOCKBACK, PUNCH_ID);
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

    private static ThorSyncPacket packet(ServerPlayer player) {
        ThorAbility[] abilities = ThorAbility.values();
        int[] cds = new int[abilities.length];
        for (int i = 0; i < abilities.length; i++) {
            cds[i] = cooldown(player, abilities[i]);
        }
        return new ThorSyncPacket(player.getUUID(), has(player), isThor(player), level(player), xp(player),
                energy(player), charge(player), flags(player), destroyBlocks(player), cds, noCooldowns(player),
                infiniteEnergy(player), suit(player), helmetOff(player), storm(player));
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

    /** Death keeps the power, the level and the energy; the player comes back in civil clothes. */
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
