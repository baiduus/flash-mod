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
        return isTransformed(player) ? scaleFor(level(player), growWithLevel(player)) : 1.0F;
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
            level.sendParticles(GAMMA_DUST, player.getX(), player.getY() + 1.2D, player.getZ(),
                    60, 0.6D, 1.0D, 0.6D, 0.0D);
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, player.getX(), player.getY() + 1.2D,
                    player.getZ(), 20, 0.6D, 1.0D, 0.6D, 0.0D);
        }
    }

    // ------------------------------------------------------------------
    // Settings, fury, cooldowns
    // ------------------------------------------------------------------

    public static void setSettings(ServerPlayer player, boolean destroy, boolean grow) {
        if (!has(player)) {
            return;
        }
        CompoundTag tag = tag(player).copy();
        tag.putBoolean(DESTROY, destroy);
        tag.putBoolean(GROW, grow);
        save(player, tag);
        applyAttributes(player);
        player.refreshDimensions();
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

    public static int cooldown(Player player, HulkAbility ability) {
        if (!has(player)) {
            return 0;
        }
        CompoundTag cds = tag(player).getCompound(COOLDOWNS);
        return (int) Math.max(0L, cds.getLong(ability.id()) - player.level().getGameTime());
    }

    public static void setCooldown(Player player, HulkAbility ability) {
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
        float cut = 0.35F + 0.045F * level(player) + (isFurious(player) ? 0.1F : 0.0F);
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
        if (player.tickCount % 10 == 0 && player.getHealth() < player.getMaxHealth()) {
            // regeneration: stronger every level and with a fuller rage bar (6 hearts/s at level 10)
            float perSecond = (0.5F + 0.25F * level(player)) * (1.0F + rageFraction(player));
            if (isFurious(player)) {
                perSecond *= 1.5F;
            }
            player.heal(perSecond / 2.0F);
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
    // Attributes
    // ------------------------------------------------------------------

    public static void applyAttributes(ServerPlayer player) {
        if (!isTransformed(player)) {
            removeAttributes(player);
            return;
        }
        int level = level(player);
        float scale = scale(player);
        double fury = isFurious(player) ? 1.5D : 1.0D;
        set(player, Attributes.ATTACK_DAMAGE, DAMAGE_ID, (4.0D + 2.0D * level) * fury, AttributeModifier.Operation.ADDITION);
        set(player, Attributes.MOVEMENT_SPEED, SPEED_ID, 0.3D + 0.04D * level, AttributeModifier.Operation.MULTIPLY_BASE);
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
                destroyBlocks(player), growWithLevel(player), cds);
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
