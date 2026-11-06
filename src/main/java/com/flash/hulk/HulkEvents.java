package com.flash.hulk;

import com.flash.FlashMod;
import java.util.List;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Hulk gameplay hooks (both sides where noted). */
@Mod.EventBusSubscriber(modid = FlashMod.MODID)
public final class HulkEvents {

    private HulkEvents() {
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.player instanceof ServerPlayer player) {
            HulkPowers.tick(player);
        }
    }

    /** The Hulk is 1.5 times bigger: hitbox and eyes too (both sides). */
    @SubscribeEvent
    public static void onSize(EntityEvent.Size event) {
        if (!(event.getEntity() instanceof Player player) || !player.isAddedToWorld()) {
            return;
        }
        if (!HulkState.isTransformed(player)) {
            return;
        }
        // always from the vanilla size of the pose (the old size may already be the Hulk's,
        // scaling it again would grow the player on every refresh)
        EntityDimensions vanilla = event.getNewSize();
        float eyes = event.getNewEyeHeight();
        float scale = HulkState.scale(player);
        event.setNewSize(vanilla.scale(scale), false);
        event.setNewEyeHeight(eyes * scale);
    }

    /** Super jump (both sides: the client moves the player). Crouch + jump = huge leap. */
    @SubscribeEvent
    public static void onJump(LivingEvent.LivingJumpEvent event) {
        if (!(event.getEntity() instanceof Player player) || !HulkState.isTransformed(player)) {
            return;
        }
        Vec3 motion = player.getDeltaMovement();
        if (player.isShiftKeyDown()) {
            Vec3 look = player.getLookAngle();
            Vec3 flat = new Vec3(look.x, 0.0D, look.z);
            flat = flat.lengthSqr() < 1.0E-4D ? Vec3.ZERO : flat.normalize();
            player.setDeltaMovement(flat.x * 3.2D, 1.7D, flat.z * 3.2D);
        } else {
            player.setDeltaMovement(motion.x, motion.y * 1.75D, motion.z);
        }
        player.hasImpulse = true;
    }

    /** No fall damage; a heavy landing sends a shockwave around. */
    @SubscribeEvent
    public static void onFall(LivingFallEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !HulkPowers.isTransformed(player)) {
            return;
        }
        float distance = event.getDistance();
        event.setDamageMultiplier(0.0F);
        event.setDistance(0.0F);
        if (distance > 4.0F && player.level() instanceof ServerLevel level) {
            shockwave(player, level, distance);
        }
    }

    private static void shockwave(ServerPlayer player, ServerLevel level, float distance) {
        double radius = 3.0D + Math.min(6.0D, distance / 4.0D);
        float damage = 6.0F + Math.min(24.0F, distance * 0.6F);
        List<LivingEntity> hit = level.getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(radius, 2.0D, radius), e -> e != player && e.isAlive());
        for (LivingEntity target : hit) {
            Vec3 away = target.position().subtract(player.position());
            double d = Math.max(0.5D, away.length());
            target.hurt(level.damageSources().playerAttack(player), (float) (damage * (1.0D - Math.min(0.8D, d / (radius + 1.0D)))));
            Vec3 push = new Vec3(away.x, 0.0D, away.z).normalize().scale(1.6D);
            target.setDeltaMovement(push.x, 0.6D, push.z);
            target.hurtMarked = true;
        }
        net.minecraft.world.level.block.state.BlockState ground = level.getBlockState(player.blockPosition().below());
        if (!ground.isAir()) {
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground),
                    player.getX(), player.getY() + 0.1D, player.getZ(), 120, radius / 2.0D, 0.2D, radius / 2.0D, 0.25D);
        }
        level.sendParticles(ParticleTypes.EXPLOSION, player.getX(), player.getY() + 0.3D, player.getZ(),
                6, radius / 3.0D, 0.1D, radius / 3.0D, 0.0D);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.2F, 0.6F);
        HulkPowers.addRage(player, 4.0F);
    }

    /** Resistance (grows with rage), immunity to fire and falls; fights feed the rage. */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onHurt(LivingHurtEvent event) {
        if (event.getEntity() instanceof ServerPlayer victim && HulkPowers.isTransformed(victim)) {
            if (event.getSource().is(DamageTypeTags.IS_FIRE) || event.getSource().is(DamageTypeTags.IS_FALL)) {
                event.setCanceled(true);
                return;
            }
            // level 9: indestructible skin, explosions and projectiles do nothing
            if (HulkPowers.level(victim) >= 9 && (event.getSource().is(DamageTypeTags.IS_EXPLOSION)
                    || event.getSource().is(DamageTypeTags.IS_PROJECTILE))) {
                event.setCanceled(true);
                return;
            }
            if (!event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
                event.setAmount(event.getAmount() * HulkPowers.damageFactor(victim));
            }
            HulkPowers.addRage(victim, 7.0F);
        }
        if (event.getSource().getEntity() instanceof ServerPlayer attacker && HulkPowers.isTransformed(attacker)) {
            HulkPowers.addRage(attacker, 5.0F);
        }
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        HulkPowers.copy(event.getOriginal(), event.getEntity());
        if (event.getEntity() instanceof ServerPlayer player) {
            HulkPowers.removeAttributes(player);
            HulkPowers.sync(player);
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            HulkPowers.applyAttributes(player);
            HulkPowers.sync(player);
            player.refreshDimensions();
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            HulkPowers.applyAttributes(player);
            HulkPowers.sync(player);
            player.refreshDimensions();
        }
    }

    @SubscribeEvent
    public static void onDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            HulkPowers.applyAttributes(player);
            HulkPowers.sync(player);
            player.refreshDimensions();
        }
    }

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getEntity() instanceof ServerPlayer listener && event.getTarget() instanceof ServerPlayer target) {
            HulkPowers.syncTo(listener, target);
        }
    }

    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("hulk")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("give")
                        .executes(c -> give(c.getSource().getPlayerOrException(), c.getSource()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(c -> give(EntityArgument.getPlayer(c, "player"), c.getSource()))))
                .then(Commands.literal("remove")
                        .executes(c -> remove(c.getSource().getPlayerOrException(), c.getSource()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(c -> remove(EntityArgument.getPlayer(c, "player"), c.getSource())))));
    }

    private static int give(ServerPlayer player, net.minecraft.commands.CommandSourceStack source) {
        HulkPowers.grant(player, false);
        source.sendSuccess(() -> Component.translatable("commands.flash.hulk.give", player.getDisplayName()), true);
        return 1;
    }

    private static int remove(ServerPlayer player, net.minecraft.commands.CommandSourceStack source) {
        HulkPowers.revoke(player);
        source.sendSuccess(() -> Component.translatable("commands.flash.hulk.remove", player.getDisplayName()), true);
        return 1;
    }
}
