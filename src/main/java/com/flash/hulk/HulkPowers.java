package com.flash.hulk;

import com.flash.network.FlashNetwork;
import com.flash.network.HulkSyncPacket;
import java.util.UUID;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.PacketDistributor;
import org.joml.Vector3f;

/**
 * The Hulk (gamma power). With it, a key switches between Bruce Banner (no
 * powers) and the Hulk. As the Hulk, hitting and being hit fills the rage bar;
 * a full bar is a new level (1..10) and the bar starts again. Every level makes
 * the Hulk bigger (from a bit taller than a player up to 7 blocks), stronger,
 * tougher, faster and unlocks an ability ({@link HulkAbility}). The gamma
 * syringe gives a temporary Hulk at level 4. Only one power at a time: the Hulk
 * removes the Acceleration Force and vice versa. Stored in persistent data.
 */
public final class HulkPowers {

    public static final String KEY = "flash_hulk";
    private static final String TRANSFORMED = "transformed";
    private static final String RAGE = "rage";
    private static final String LEVEL = "level";
    private static final String TEMP_UNTIL = "tempUntil";
    private static final String FURY_UNTIL = "furyUntil";
    private static final String DESTROY = "destroyBlocks";
    private static final String GROW = "grow";
    private static final String COOLDOWNS = "cooldowns";
    private static final String SELECTED = "selected";
    private static final String RED = "red";
    private static final String LIFE_USED = "lifeUsed";
    private static final String EXTRA_UNTIL = "extraUntil";
    /** Extra life bonus: 2 min 30 s. */
    public static final int EXTRA_TICKS = 3000;
    private static final String HEIGHT = "height";

    /** Smallest height (blocks) the height bar allows. */
    public static final float MIN_HEIGHT = 2.0F;
    private static final int REGEN_TICKS = 80;

    public static final float MAX_RAGE = 100.0F;
    public static final int MAX_LEVEL = 10;
    public static final int TEMP_LEVEL = 4;
    public static final int TEMP_TICKS = 5 * 60 * 20;
    public static final int FURY_TICKS = 20 * 20;

    /** Size at level 1 and level 10 (7 blocks tall). */
    public static final float MIN_SCALE = 1.15F;
    public static final float MAX_SCALE = 7.0F / 1.8F;

    public static final DustParticleOptions GAMMA_DUST =
            new DustParticleOptions(new Vector3f(0.25F, 1.0F, 0.2F), 1.4F);
    public static final DustParticleOptions FURY_DUST =
            new DustParticleOptions(new Vector3f(1.0F, 0.15F, 0.1F), 1.3F);

    private static final UUID DAMAGE_ID = UUID.fromString("5b0c9a52-6c11-4d43-9b3f-2a6e1f0c7a01");
    private static final UUID SPEED_ID = UUID.fromString("5b0c9a52-6c11-4d43-9b3f-2a6e1f0c7a02");
    private static final UUID HEALTH_ID = UUID.fromString("5b0c9a52-6c11-4d43-9b3f-2a6e1f0c7a03");
    private static final UUID KNOCKBACK_ID = UUID.fromString("5b0c9a52-6c11-4d43-9b3f-2a6e1f0c7a04");
    private static final UUID PUNCH_ID = UUID.fromString("5b0c9a52-6c11-4d43-9b3f-2a6e1f0c7a05");
    private static final UUID REACH_ID = UUID.fromString("5b0c9a52-6c11-4d43-9b3f-2a6e1f0c7a06");
    private static final UUID STEP_ID = UUID.fromString("5b0c9a52-6c11-4d43-9b3f-2a6e1f0c7a07");
    private static final UUID BLOCK_REACH_ID = UUID.fromString("5b0c9a52-6c11-4d43-9b3f-2a6e1f0c7a08");

    private HulkPowers() {
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

    public static boolean isTransformed(Player player) {
        return has(player) && tag(player).getBoolean(TRANSFORMED);
    }

    public static boolean isTemporary(Player player) {
        return has(player) && tag(player).contains(TEMP_UNTIL);
    }

    public static int temporaryTicks(Player player) {
        return isTemporary(player)
                ? (int) Math.max(0L, tag(player).getLong(TEMP_UNTIL) - player.level().getGameTime()) : 0;
    }

    public static int level(Player player) {
        if (!has(player)) {
            return 0;
        }
        return Math.max(1, Math.min(MAX_LEVEL, tag(player).getInt(LEVEL)));
    }

    /** Level picked with the level selector (1..level); 0 stored = always the highest. */
    public static int activeLevel(Player player) {
        int max = level(player);
        if (max <= 0) {
            return 0;
        }
        int sel = tag(player).getInt(SELECTED);
        return sel <= 0 ? max : Math.max(1, Math.min(max, sel));
    }

    /** Height chosen in the options bar (blocks; 0 = not chosen yet). */
    public static float chosenHeight(Player player) {
        return has(player) ? tag(player).getFloat(HEIGHT) : 0.0F;
    }

    /** Tallest height the level allows (7 blocks at level 10). */
    public static float maxHeight(int level) {
        return scaleFor(level, true) * 1.8F;
    }

    /** Scale from the options: grows with the active level, or the exact chosen height. */
    public static float scaleOf(int activeLevel, int maxLevel, boolean grow, float height) {
        if (grow) {
            return scaleFor(activeLevel, true);
        }
        float h = height <= 0.0F ? MIN_SCALE * 1.8F : height;
        h = Math.max(MIN_HEIGHT, Math.min(maxHeight(maxLevel), h));
        return h / 1.8F;
    }

    public static float rage(Player player) {
        return has(player) ? tag(player).getFloat(RAGE) : 0.0F;
    }

    public static float rageFraction(Player player) {
        return rage(player) / MAX_RAGE;
    }

    public static boolean destroyBlocks(Player player) {
        return has(player) && tag(player).getBoolean(DESTROY);
    }

    public static boolean growWithLevel(Player player) {
        return has(player) && tag(player).getBoolean(GROW);
    }

    public static boolean isFurious(Player player) {
        return has(player) && tag(player).getLong(FURY_UNTIL) > player.level().getGameTime();
    }

    public static int furyTicks(Player player) {
        return has(player) ? (int) Math.max(0L, tag(player).getLong(FURY_UNTIL) - player.level().getGameTime()) : 0;
    }

    /** Body scale for a level (and the "grow with level" option). */
    public static float scaleFor(int level, boolean grow) {
        if (!grow) {
            return MIN_SCALE;
        }
        float t = (Math.max(1, Math.min(MAX_LEVEL, level)) - 1) / (float) (MAX_LEVEL - 1);
        return MIN_SCALE + (MAX_SCALE - MIN_SCALE) * t;
    }

    public static float scale(Player player) {
        return isTransformed(player)
                ? scaleOf(activeLevel(player), level(player), growWithLevel(player), chosenHeight(player)) : 1.0F;
    }

    // ------------------------------------------------------------------
    // Getting / losing the power
    // ------------------------------------------------------------------

    /**
     * Gamma radiation: the player gets the Hulk for good (losing the Acceleration Force).
     * @param transform true to turn into the Hulk at once
     * @return false when the player already had it
     */
    public static boolean grant(ServerPlayer player, boolean transform) {
        if (has(player) && !isTemporary(player)) {
            if (transform && !isTransformed(player)) {
                setTransformed(player, true);
            }
            return false;
        }
        boolean wasTemp = has(player);
        CompoundTag old = wasTemp ? tag(player) : new CompoundTag();
        clearAccelerationForce(player);
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(TRANSFORMED, false);
        tag.putFloat(RAGE, 0.0F);
        tag.putInt(LEVEL, 1);
        tag.putBoolean(DESTROY, !old.contains(DESTROY) || old.getBoolean(DESTROY));
        tag.putBoolean(GROW, !old.contains(GROW) || old.getBoolean(GROW));
        save(player, tag);
        player.displayClientMessage(Component.translatable("message.flash.hulk_granted"), false);
        if (transform) {
            setTransformed(player, true);
        } else {
            applyAttributes(player);
            player.refreshDimensions();
            sync(player);
        }
        return true;
    }

    /** Gamma syringe: a temporary Hulk at level 4 for 5 minutes. */
    public static boolean injectGamma(ServerPlayer player) {
        if (has(player)) {
            player.displayClientMessage(Component.translatable("message.flash.hulk_already"), true);
            return false;
        }
        if (com.flash.power.FlashPowers.hasPowers(player) || com.flash.power.FlashPowers.hasPermanentPowers(player)) {
            player.displayClientMessage(Component.translatable("message.flash.hulk_speedster"), true);
            return false;
        }
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(TRANSFORMED, false);
        tag.putFloat(RAGE, 0.0F);
        tag.putInt(LEVEL, TEMP_LEVEL);
        tag.putLong(TEMP_UNTIL, player.level().getGameTime() + TEMP_TICKS);
        tag.putBoolean(DESTROY, true);
        tag.putBoolean(GROW, true);
        save(player, tag);
        player.displayClientMessage(Component.translatable("message.flash.hulk_temporary"), false);
        setTransformed(player, true);
        return true;
    }

    public static void revoke(ServerPlayer player) {
        if (!has(player)) {
            return;
        }
        player.getPersistentData().remove(KEY);
        removeAttributes(player);
        removeRegeneration(player);
        player.refreshDimensions();
        sync(player);
    }

    /** Only one power at a time: the speedster powers go away. */
    private static void clearAccelerationForce(ServerPlayer player) {
        if (!com.flash.power.FlashPowers.hasPowers(player)
                && !com.flash.power.FlashPowers.hasPermanentPowers(player)
                && !player.getPersistentData().contains(com.flash.power.FlashV9.KEY)) {
            return;
        }
        com.flash.power.FlashPowers.revoke(player);
        player.getPersistentData().remove(com.flash.power.FlashV9.KEY);
        com.flash.ability.FlashAbilityManager.reset(player);
        com.flash.power.FlashSpeed.remove(player);
        com.flash.power.FlashStep.remove(player);
        com.flash.power.FlashStatusEffects.reset(player);
        com.flash.network.FlashSyncPacket.send(player);
        com.flash.network.FlashVisualSyncPacket.send(player, 0);
    }

    // ------------------------------------------------------------------
    // Transformation
    // ------------------------------------------------------------------

    public static void toggle(ServerPlayer player) {
        if (!has(player)) {
            player.displayClientMessage(Component.translatable("message.flash.hulk_none"), true);
            return;
        }
        setTransformed(player, !isTransformed(player));
    }

    public static void setTransformed(ServerPlayer player, boolean hulk) {
        CompoundTag tag = tag(player).copy();
        tag.putBoolean(TRANSFORMED, hulk);
        if (!hulk) {
            tag.remove(FURY_UNTIL);
        }
        save(player, tag);
        if (hulk) {
            applyAttributes(player);
        } else {
            removeAttributes(player);
            removeRegeneration(player);
        }
        player.refreshDimensions();
        if (hulk) {
            player.setHealth(player.getMaxHealth());
            burst(player);
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS, 1.4F, 0.7F);
            player.displayClientMessage(Component.translatable("message.flash.hulk_transformed"), true);
        } else {
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.PLAYER_BREATH, SoundSource.PLAYERS, 1.0F, 0.8F);
            player.displayClientMessage(Component.translatable("message.flash.hulk_banner"), true);
        }
        sync(player);
    }

    public static void burst(ServerPlayer player) {
        if (player.level() instanceof ServerLevel level) {
            level.sendParticles(com.flash.registry.ModParticles.GAMMA.get(), player.getX(), player.getY() + 1.2D, player.getZ(),
                    60, 0.6D, 1.0D, 0.6D, 0.0D);
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, player.getX(), player.getY() + 1.2D,
                    player.getZ(), 20, 0.6D, 1.0D, 0.6D, 0.0D);
        }
    }

    // ------------------------------------------------------------------
    // Settings, fury, cooldowns
    // ------------------------------------------------------------------

    public static void setSettings(ServerPlayer player, boolean destroy, boolean grow, float height) {
        if (!has(player)) {
            return;
        }
        CompoundTag tag = tag(player).copy();
        tag.putBoolean(DESTROY, destroy);
        tag.putBoolean(GROW, grow);
        if (height > 0.0F) {
            tag.putFloat(HEIGHT, Math.max(MIN_HEIGHT, Math.min(maxHeight(MAX_LEVEL), height)));
        }
        save(player, tag);
        applyAttributes(player);
        player.refreshDimensions();
        sync(player);
    }

    // ------------------------------------------------------------------
    // Extra life
    // ------------------------------------------------------------------

    /**
     * First death of a Hulk: he comes back at once, with extra strength and regeneration for
     * 2 min 30 s. A second death (the extra life is spent) is a normal death; the extra life
     * comes back after a normal respawn.
     * @return true when the death was cancelled
     */
    public static boolean tryExtraLife(ServerPlayer player) {
        if (!has(player) || tag(player).getBoolean(LIFE_USED)) {
            return false;
        }
        CompoundTag tag = tag(player).copy();
        tag.putBoolean(LIFE_USED, true);
        tag.putLong(EXTRA_UNTIL, player.level().getGameTime() + EXTRA_TICKS);
        save(player, tag);
        player.setHealth(player.getMaxHealth());
        player.removeAllEffects();
        player.clearFire();
        player.fallDistance = 0.0F;
        player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                net.minecraft.world.effect.MobEffects.DAMAGE_BOOST, EXTRA_TICKS, 2, false, true, true));
        player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                net.minecraft.world.effect.MobEffects.REGENERATION, EXTRA_TICKS, 3, false, true, true));
        player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE, 100, 4, false, false, true));
        if (player.level() instanceof ServerLevel level) {
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.TOTEM_OF_UNDYING, player.getX(),
                    player.getY() + player.getBbHeight() * 0.5D, player.getZ(), 120, player.getBbWidth(),
                    player.getBbHeight() * 0.5D, player.getBbWidth(), 0.6D);
            level.sendParticles(com.flash.registry.ModParticles.GAMMA.get(), player.getX(), player.getY() + 1.0D,
                    player.getZ(), 80, player.getBbWidth(), player.getBbHeight() * 0.5D, player.getBbWidth(), 0.05D);
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    net.minecraft.sounds.SoundEvents.TOTEM_USE, net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 0.8F);
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    net.minecraft.sounds.SoundEvents.RAVAGER_ROAR, net.minecraft.sounds.SoundSource.PLAYERS, 3.0F, 0.45F);
        }
        player.displayClientMessage(Component.translatable("message.flash.hulk_extra_life"), false);
        applyAttributes(player);
        sync(player);
        return true;
    }

    /** Ticks left of the extra life bonus (0 = none). */
    public static int extraTicks(Player player) {
        if (!has(player)) {
            return 0;
        }
        long left = tag(player).getLong(EXTRA_UNTIL) - player.level().getGameTime();
        return left > 0 ? (int) Math.min(Integer.MAX_VALUE, left) : 0;
    }

    /** After a normal respawn the extra life is ready again. */
    public static void resetExtraLife(Player player) {
        if (has(player)) {
            CompoundTag tag = tag(player).copy();
            tag.remove(LIFE_USED);
            tag.remove(EXTRA_UNTIL);
            player.getPersistentData().put(KEY, tag);
        }
    }

    /** Red Hulk: the same powers with red skin and a red HUD (command only for now). */
    public static boolean isRed(Player player) {
        return has(player) && tag(player).getBoolean(RED);
    }

    public static void setRed(ServerPlayer player, boolean red) {
        CompoundTag tag = tag(player).copy();
        tag.putBoolean(RED, red);
        save(player, tag);
        sync(player);
    }

    /** Level selector (key / Ctrl + key / Ctrl + scroll): moves the active level by one. */
    public static void selectLevel(ServerPlayer player, int direction) {
        if (!has(player)) {
            return;
        }
        int max = level(player);
        int next = Math.max(1, Math.min(max, activeLevel(player) + (direction > 0 ? 1 : -1)));
        CompoundTag tag = tag(player).copy();
        tag.putInt(SELECTED, next >= max ? 0 : next);
        save(player, tag);
        applyAttributes(player);
        player.refreshDimensions();
        player.displayClientMessage(Component.translatable("message.flash.hulk_select", next, max), true);
        sync(player);
    }

    /** Command: sets the rage level (1..10) and the rage bar (0..100). */
    public static void setLevel(ServerPlayer player, int level, float rage) {
        if (!has(player)) {
            grant(player, false);
        }
        CompoundTag tag = tag(player).copy();
        tag.putInt(LEVEL, Math.max(1, Math.min(MAX_LEVEL, level)));
        tag.putFloat(RAGE, Math.max(0.0F, Math.min(MAX_RAGE, rage)));
        tag.remove(TEMP_UNTIL);
        tag.remove(SELECTED);
        save(player, tag);
        applyAttributes(player);
        player.refreshDimensions();
        sync(player);
    }

    public static void startFury(ServerPlayer player) {
        CompoundTag tag = tag(player).copy();
        tag.putLong(FURY_UNTIL, player.level().getGameTime() + FURY_TICKS);
        save(player, tag);
        applyAttributes(player);
    }

    private static final String NO_COOLDOWNS = "noCooldowns";

    /** Test mode (command): the abilities have no cooldown. */
    public static boolean noCooldowns(Player player) {
        return has(player) && tag(player).getBoolean(NO_COOLDOWNS);
    }

    /** Command: clears the current cooldowns; {@code disable} non-null also turns test mode on/off. */
    public static void resetCooldowns(ServerPlayer player, Boolean disable) {
        CompoundTag tag = tag(player).copy();
        tag.remove(COOLDOWNS);
        if (disable != null) {
            tag.putBoolean(NO_COOLDOWNS, disable);
        }
        save(player, tag);
        syncSelf(player);
    }

    public static int cooldown(Player player, HulkAbility ability) {
        if (!has(player)) {
            return 0;
        }
        CompoundTag cds = tag(player).getCompound(COOLDOWNS);
        return (int) Math.max(0L, cds.getLong(ability.id()) - player.level().getGameTime());
    }

    public static void setCooldown(Player player, HulkAbility ability) {
        if (noCooldowns(player)) {
            return;
        }
        CompoundTag tag = tag(player).copy();
        CompoundTag cds = tag.getCompound(COOLDOWNS);
        cds.putLong(ability.id(), player.level().getGameTime() + ability.cooldown());
        tag.put(COOLDOWNS, cds);
        save(player, tag);
    }

    // ------------------------------------------------------------------
    // Rage and levels
    // ------------------------------------------------------------------

    /** Hits given / taken as the Hulk fill the rage bar; a full bar is a new level. */
    public static void addRage(ServerPlayer player, float base) {
        if (!isTransformed(player)) {
            return;
        }
        CompoundTag tag = tag(player).copy();
        int level = Math.max(1, tag.getInt(LEVEL));
        if (tag.contains(TEMP_UNTIL)) {
            return; // the temporary Hulk does not grow
        }
        float gain = base / (1.0F + 0.35F * (level - 1));
        float rage = tag.getFloat(RAGE) + gain;
        boolean levelUp = false;
        while (rage >= MAX_RAGE && level < MAX_LEVEL) {
            rage -= MAX_RAGE;
            level++;
            levelUp = true;
        }
        if (level >= MAX_LEVEL) {
            rage = Math.min(MAX_RAGE, rage);
        }
        tag.putFloat(RAGE, rage);
        tag.putInt(LEVEL, level);
        save(player, tag);
        if (levelUp) {
            applyAttributes(player);
            player.refreshDimensions();
            burst(player);
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS, 1.6F, 0.6F);
            player.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8F, 0.7F);
            player.displayClientMessage(Component.translatable("message.flash.hulk_level", level), false);
            sync(player);
        }
    }

    /** Damage taken by the Hulk is multiplied by this (tougher every level, more with fury). */
    public static float damageFactor(Player player) {
        float cut = 0.35F + 0.045F * activeLevel(player) + (isFurious(player) ? 0.1F : 0.0F);
        return 1.0F - Math.min(0.88F, cut);
    }

    public static void tick(ServerPlayer player) {
        if (!has(player)) {
            return;
        }
        CompoundTag tag = tag(player);
        long now = player.level().getGameTime();
        if (tag.contains(TEMP_UNTIL) && now >= tag.getLong(TEMP_UNTIL)) {
            revoke(player);
            player.displayClientMessage(Component.translatable("message.flash.hulk_temporary_end"), false);
            return;
        }
        if (!isTransformed(player)) {
            return;
        }
        player.clearFire();
        if (player.tickCount % 10 == 0) {
            applyRegeneration(player);
        }
        // the Red Hulk burns: flames and embers rising from the body
        if (isRed(player) && player.level() instanceof ServerLevel fire && player.tickCount % 2 == 0) {
            double w = player.getBbWidth() * 0.45D;
            double h = player.getBbHeight();
            fire.sendParticles(net.minecraft.core.particles.ParticleTypes.FLAME, player.getX(),
                    player.getY() + h * 0.5D, player.getZ(), 3, w, h * 0.35D, w, 0.01D);
            fire.sendParticles(net.minecraft.core.particles.ParticleTypes.SMALL_FLAME, player.getX(),
                    player.getY() + h * 0.85D, player.getZ(), 2, w * 0.6D, h * 0.12D, w * 0.6D, 0.01D);
            if (player.tickCount % 6 == 0) {
                fire.sendParticles(net.minecraft.core.particles.ParticleTypes.LARGE_SMOKE, player.getX(),
                        player.getY() + h * 0.9D, player.getZ(), 1, w * 0.5D, h * 0.1D, w * 0.5D, 0.01D);
                fire.sendParticles(net.minecraft.core.particles.ParticleTypes.LAVA, player.getX(),
                        player.getY() + h * 0.4D, player.getZ(), 1, w, h * 0.3D, w, 0.0D);
            }
        }
        if (isFurious(player) && player.level() instanceof ServerLevel level && player.tickCount % 3 == 0) {
            level.sendParticles(FURY_DUST, player.getX(), player.getY() + player.getBbHeight() * 0.5D,
                    player.getZ(), 3, player.getBbWidth() * 0.5D, player.getBbHeight() * 0.4D,
                    player.getBbWidth() * 0.5D, 0.0D);
        }
        if (player.tickCount % 10 == 0) {
            applyAttributes(player);
            syncSelf(player);
        }
    }

    // ------------------------------------------------------------------
    // Regeneration (the vanilla effect, like the Flash's)
    // ------------------------------------------------------------------

    /** Regeneration I at levels 1-3, II at 4-6, III at 7-9, IV at 10; the fury adds one (up to IV). */
    public static int regenerationAmplifier(Player player) {
        int lvl = activeLevel(player);
        int amp = (lvl - 1) / 3 + (isFurious(player) ? 1 : 0);
        return Math.max(0, Math.min(3, amp));
    }

    private static boolean isOurRegeneration(net.minecraft.world.effect.MobEffectInstance e) {
        return e != null && !e.isVisible() && !e.isAmbient() && e.getDuration() <= REGEN_TICKS;
    }

    private static void applyRegeneration(ServerPlayer player) {
        int amp = regenerationAmplifier(player);
        var effect = net.minecraft.world.effect.MobEffects.REGENERATION;
        net.minecraft.world.effect.MobEffectInstance current = player.getEffect(effect);
        boolean ours = isOurRegeneration(current);
        if (current != null && !ours && current.getAmplifier() >= amp) {
            return; // a stronger (or equal) potion is active: leave it alone
        }
        if (ours && current.getAmplifier() == amp && current.getDuration() > REGEN_TICKS / 2) {
            return;
        }
        if (ours && current.getAmplifier() != amp) {
            player.removeEffect(effect);
        }
        player.addEffect(new net.minecraft.world.effect.MobEffectInstance(effect, REGEN_TICKS, amp,
                false, false, true));
    }

    public static void removeRegeneration(ServerPlayer player) {
        var effect = net.minecraft.world.effect.MobEffects.REGENERATION;
        if (isOurRegeneration(player.getEffect(effect))) {
            player.removeEffect(effect);
        }
    }

    // ------------------------------------------------------------------
    // Attributes
    // ------------------------------------------------------------------

    public static void applyAttributes(ServerPlayer player) {
        if (!isTransformed(player)) {
            removeAttributes(player);
            return;
        }
        int level = activeLevel(player);
        float scale = scale(player);
        double fury = isFurious(player) ? 1.5D : 1.0D;
        set(player, Attributes.ATTACK_DAMAGE, DAMAGE_ID,
                bonusDamage(level, fury > 1.0D) * (extraTicks(player) > 0 ? 1.5D : 1.0D), AttributeModifier.Operation.ADDITION);
        set(player, Attributes.MOVEMENT_SPEED, SPEED_ID, 0.45D + 0.06D * level, AttributeModifier.Operation.MULTIPLY_BASE);
        set(player, Attributes.MAX_HEALTH, HEALTH_ID, 10.0D + 3.0D * level, AttributeModifier.Operation.ADDITION);
        set(player, Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK_ID, 1.0D, AttributeModifier.Operation.ADDITION);
        set(player, Attributes.ATTACK_KNOCKBACK, PUNCH_ID, 1.0D + 0.3D * level, AttributeModifier.Operation.ADDITION);
        set(player, net.minecraftforge.common.ForgeMod.ENTITY_REACH.get(), REACH_ID,
                1.0D + (scale - 1.0D) * 2.5D, AttributeModifier.Operation.ADDITION);
        set(player, net.minecraftforge.common.ForgeMod.BLOCK_REACH.get(), BLOCK_REACH_ID,
                (scale - 1.0D) * 2.5D, AttributeModifier.Operation.ADDITION);
        set(player, net.minecraftforge.common.ForgeMod.STEP_HEIGHT_ADDITION.get(), STEP_ID,
                0.6D + (scale - 1.0D) * 0.8D, AttributeModifier.Operation.ADDITION);
    }

    /** Extra punch damage of a level (the fury multiplies it by 1.5). */
    public static double bonusDamage(int level, boolean furious) {
        // as strong as the lore: 13 at level 1 up to 49 at level 10 (x1.5 in fury)
        return (8.0D + 4.0D * Math.max(1, level)) * (furious ? 1.5D : 1.0D);
    }

    /** Punch damage of the Hulk (a normal empty-hand punch is 1). */
    public static double punchDamage(int level, boolean furious) {
        return 1.0D + bonusDamage(level, furious);
    }

    /** Puts the modifier only when it changed (re-adding it every time would reset the health). */
    private static void set(ServerPlayer player, Attribute attribute, UUID id, double amount,
                            AttributeModifier.Operation operation) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        AttributeModifier current = instance.getModifier(id);
        if (current != null && Math.abs(current.getAmount() - amount) < 1.0E-6D
                && current.getOperation() == operation) {
            return;
        }
        if (current != null) {
            instance.removeModifier(id);
        }
        instance.addTransientModifier(new AttributeModifier(id, "flash_hulk", amount, operation));
    }

    public static void removeAttributes(ServerPlayer player) {
        remove(player, Attributes.ATTACK_DAMAGE, DAMAGE_ID);
        remove(player, Attributes.MOVEMENT_SPEED, SPEED_ID);
        remove(player, Attributes.MAX_HEALTH, HEALTH_ID);
        remove(player, Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK_ID);
        remove(player, Attributes.ATTACK_KNOCKBACK, PUNCH_ID);
        remove(player, net.minecraftforge.common.ForgeMod.ENTITY_REACH.get(), REACH_ID);
        remove(player, net.minecraftforge.common.ForgeMod.BLOCK_REACH.get(), BLOCK_REACH_ID);
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

    private static HulkSyncPacket packet(ServerPlayer player) {
        HulkAbility[] abilities = HulkAbility.values();
        int[] cds = new int[abilities.length];
        for (int i = 0; i < abilities.length; i++) {
            cds[i] = cooldown(player, abilities[i]);
        }
        return new HulkSyncPacket(player.getUUID(), has(player), isTransformed(player), scale(player),
                level(player), rage(player), temporaryTicks(player), furyTicks(player),
                destroyBlocks(player), growWithLevel(player), cds, activeLevel(player), chosenHeight(player),
                isRed(player), extraTicks(player));
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

    /** Death keeps the power and the level; the player comes back as Bruce Banner. */
    public static void copy(Player from, Player to) {
        if (from.getPersistentData().contains(KEY)) {
            CompoundTag tag = from.getPersistentData().getCompound(KEY).copy();
            tag.putBoolean(TRANSFORMED, false);
            tag.remove(FURY_UNTIL);
            to.getPersistentData().put(KEY, tag);
        }
    }
}
