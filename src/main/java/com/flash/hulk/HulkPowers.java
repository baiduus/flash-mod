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
 * The Hulk (gamma power). A player has it or not ({@link #has}); with it, a key
 * switches between Bruce Banner (no powers) and the Hulk ({@link #isTransformed}).
 * As the Hulk the player is bigger and gets growing strength, resistance,
 * regeneration, speed and the super jump; strength and resistance grow with the
 * rage meter (0..100), which rises when hitting and being hit and calms down slowly.
 * Only one power at a time: getting the Hulk removes the Acceleration Force and
 * vice versa. Everything is stored in the player's persistent data.
 */
public final class HulkPowers {

    public static final String KEY = "flash_hulk";
    private static final String TRANSFORMED = "transformed";
    private static final String RAGE = "rage";
    private static final String LAST_FIGHT = "lastFight";

    public static final float MAX_RAGE = 100.0F;
    public static final float SCALE = 1.5F;

    public static final DustParticleOptions GAMMA_DUST =
            new DustParticleOptions(new Vector3f(0.25F, 1.0F, 0.2F), 1.4F);

    private static final UUID DAMAGE_ID = UUID.fromString("5b0c9a52-6c11-4d43-9b3f-2a6e1f0c7a01");
    private static final UUID SPEED_ID = UUID.fromString("5b0c9a52-6c11-4d43-9b3f-2a6e1f0c7a02");
    private static final UUID HEALTH_ID = UUID.fromString("5b0c9a52-6c11-4d43-9b3f-2a6e1f0c7a03");
    private static final UUID KNOCKBACK_ID = UUID.fromString("5b0c9a52-6c11-4d43-9b3f-2a6e1f0c7a04");
    private static final UUID PUNCH_ID = UUID.fromString("5b0c9a52-6c11-4d43-9b3f-2a6e1f0c7a05");
    private static final UUID REACH_ID = UUID.fromString("5b0c9a52-6c11-4d43-9b3f-2a6e1f0c7a06");
    private static final UUID STEP_ID = UUID.fromString("5b0c9a52-6c11-4d43-9b3f-2a6e1f0c7a07");

    private HulkPowers() {
    }

    // ------------------------------------------------------------------
    // Queries
    // ------------------------------------------------------------------

    public static boolean has(Player player) {
        return player.getPersistentData().contains(KEY);
    }

    public static boolean isTransformed(Player player) {
        return has(player) && player.getPersistentData().getCompound(KEY).getBoolean(TRANSFORMED);
    }

    public static float rage(Player player) {
        return has(player) ? player.getPersistentData().getCompound(KEY).getFloat(RAGE) : 0.0F;
    }

    /** 0..1 */
    public static float rageFraction(Player player) {
        return rage(player) / MAX_RAGE;
    }

    private static CompoundTag tag(Player player) {
        return player.getPersistentData().getCompound(KEY);
    }

    private static void save(Player player, CompoundTag tag) {
        player.getPersistentData().put(KEY, tag);
    }

    // ------------------------------------------------------------------
    // Getting / losing the power
    // ------------------------------------------------------------------

    /**
     * Gamma radiation: the player gets the Hulk (losing the Acceleration Force).
     * @param transform true to turn into the Hulk at once (the accident / the bomb)
     * @return false when the player already had it
     */
    public static boolean grant(ServerPlayer player, boolean transform) {
        if (has(player)) {
            if (transform && !isTransformed(player)) {
                setTransformed(player, true);
            }
            return false;
        }
        clearAccelerationForce(player);
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(TRANSFORMED, false);
        tag.putFloat(RAGE, 0.0F);
        save(player, tag);
        player.displayClientMessage(Component.translatable("message.flash.hulk_granted"), false);
        if (transform) {
            setTransformed(player, true);
        } else {
            sync(player);
        }
        return true;
    }

    public static void revoke(ServerPlayer player) {
        if (!has(player)) {
            return;
        }
        if (isTransformed(player)) {
            setTransformed(player, false);
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

    /** Gamma syringe. */
    public static boolean injectGamma(ServerPlayer player) {
        if (has(player)) {
            player.displayClientMessage(Component.translatable("message.flash.hulk_already"), true);
            return false;
        }
        grant(player, false);
        burst(player);
        player.displayClientMessage(Component.translatable("message.flash.hulk_key_hint"), true);
        return true;
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
            tag.putFloat(RAGE, 0.0F);
        }
        save(player, tag);
        applyAttributes(player);
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
    // Rage and attributes
    // ------------------------------------------------------------------

    public static void addRage(ServerPlayer player, float amount) {
        if (!isTransformed(player)) {
            return;
        }
        CompoundTag tag = tag(player).copy();
        tag.putFloat(RAGE, Math.min(MAX_RAGE, tag.getFloat(RAGE) + amount));
        tag.putLong(LAST_FIGHT, player.level().getGameTime());
        save(player, tag);
    }

    /** Damage taken by the Hulk is multiplied by this (resistance grows with rage). */
    public static float damageFactor(Player player) {
        return 1.0F - (0.5F + 0.3F * rageFraction(player));
    }

    public static void tick(ServerPlayer player) {
        if (!has(player)) {
            return;
        }
        if (!isTransformed(player)) {
            if (player.tickCount % 100 == 0) {
                removeAttributes(player);
            }
            return;
        }
        player.clearFire();
        if (player.tickCount % 20 == 0) {
            // regeneration, faster when furious
            player.heal(1.0F + (rage(player) >= 50.0F ? 1.0F : 0.0F));
        }
        CompoundTag tag = tag(player);
        if (player.tickCount % 40 == 0 && tag.getFloat(RAGE) > 0.0F
                && player.level().getGameTime() - tag.getLong(LAST_FIGHT) > 100L) {
            CompoundTag copy = tag.copy();
            copy.putFloat(RAGE, Math.max(0.0F, copy.getFloat(RAGE) - 3.0F));
            save(player, copy);
        }
        if (player.tickCount % 10 == 0) {
            applyAttributes(player);
            syncSelf(player);
        }
    }

    public static void applyAttributes(ServerPlayer player) {
        removeAttributes(player);
        if (!isTransformed(player)) {
            return;
        }
        float rage = rageFraction(player);
        add(player, Attributes.ATTACK_DAMAGE, DAMAGE_ID, 8.0D + 14.0D * rage, AttributeModifier.Operation.ADDITION);
        add(player, Attributes.MOVEMENT_SPEED, SPEED_ID, 0.6D, AttributeModifier.Operation.MULTIPLY_BASE);
        add(player, Attributes.MAX_HEALTH, HEALTH_ID, 20.0D, AttributeModifier.Operation.ADDITION);
        add(player, Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK_ID, 1.0D, AttributeModifier.Operation.ADDITION);
        add(player, Attributes.ATTACK_KNOCKBACK, PUNCH_ID, 1.5D + 2.0D * rage, AttributeModifier.Operation.ADDITION);
        add(player, net.minecraftforge.common.ForgeMod.ENTITY_REACH.get(), REACH_ID, 1.5D, AttributeModifier.Operation.ADDITION);
        add(player, net.minecraftforge.common.ForgeMod.STEP_HEIGHT_ADDITION.get(), STEP_ID, 0.6D, AttributeModifier.Operation.ADDITION);
    }

    private static void add(ServerPlayer player, Attribute attribute, UUID id, double amount,
                            AttributeModifier.Operation operation) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance != null) {
            instance.addTransientModifier(new AttributeModifier(id, "flash_hulk", amount, operation));
        }
    }

    public static void removeAttributes(ServerPlayer player) {
        remove(player, Attributes.ATTACK_DAMAGE, DAMAGE_ID);
        remove(player, Attributes.MOVEMENT_SPEED, SPEED_ID);
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
        if (instance != null) {
            instance.removeModifier(id);
        }
    }

    // ------------------------------------------------------------------
    // Sync
    // ------------------------------------------------------------------

    public static void sync(ServerPlayer player) {
        FlashNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player),
                new HulkSyncPacket(player.getUUID(), has(player), isTransformed(player), rage(player)));
    }

    private static void syncSelf(ServerPlayer player) {
        FlashNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new HulkSyncPacket(player.getUUID(), has(player), isTransformed(player), rage(player)));
    }

    public static void syncTo(ServerPlayer listener, ServerPlayer target) {
        FlashNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> listener),
                new HulkSyncPacket(target.getUUID(), has(target), isTransformed(target), rage(target)));
    }

    /** Death keeps the power but the player comes back as Bruce Banner. */
    public static void copy(Player from, Player to) {
        if (from.getPersistentData().contains(KEY)) {
            CompoundTag tag = from.getPersistentData().getCompound(KEY).copy();
            tag.putBoolean(TRANSFORMED, false);
            tag.putFloat(RAGE, 0.0F);
            to.getPersistentData().put(KEY, tag);
        }
    }
}
