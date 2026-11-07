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
        // jump grows with the active level: close at level 1, very far at level 10
        double t = (Math.max(1, HulkState.activeLevel(player)) - 1) / (double) (HulkPowers.MAX_LEVEL - 1);
        if (player.isShiftKeyDown()) {
            Vec3 look = player.getLookAngle();
            Vec3 flat = new Vec3(look.x, 0.0D, look.z);
            flat = flat.lengthSqr() < 1.0E-4D ? Vec3.ZERO : flat.normalize();
            double forward = 1.0D + 3.2D * t;
            player.setDeltaMovement(flat.x * forward, 0.8D + 1.3D * t, flat.z * forward);
        } else {
            player.setDeltaMovement(motion.x, motion.y * (1.2D + 0.8D * t), motion.z);
        }
        player.hasImpulse = true;
    }

    /** The Hulk's own explosions (abilities, rock) never hurt or throw him. */
    @SubscribeEvent
    public static void onDetonate(net.minecraftforge.event.level.ExplosionEvent.Detonate event) {
        if (event.getExplosion().getIndirectSourceEntity() instanceof Player hulk && HulkState.isTransformed(hulk)) {
            event.getAffectedEntities().remove(hulk);
        }
        // indestructible skin (level 9): explosions neither hurt nor push him
        event.getAffectedEntities().removeIf(e -> e instanceof ServerPlayer p && HulkPowers.isTransformed(p)
                && HulkPowers.level(p) >= 9);
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
        // whoever is held and hits the Hulk breaks free and falls
        if (event.getEntity() instanceof ServerPlayer hulk && event.getSource().getEntity() != null) {
            HulkAbilities.breakFree(hulk, event.getSource().getEntity());
        }
    }

    /** Indestructible skin (level 9): arrows and other projectiles bounce off without hitting. */
    @SubscribeEvent
    public static void onAttacked(net.minecraftforge.event.entity.living.LivingAttackEvent event) {
        if (event.getEntity() instanceof ServerPlayer victim && HulkPowers.isTransformed(victim)
                && HulkPowers.level(victim) >= 9 && event.getSource().is(DamageTypeTags.IS_PROJECTILE)) {
            // cancelled here, the hit fails and the arrow bounces back
            event.setCanceled(true);
        }
    }

    /** Indestructible skin (level 9): no knockback from creatures or players. */
    @SubscribeEvent
    public static void onKnockback(net.minecraftforge.event.entity.living.LivingKnockBackEvent event) {
        if (event.getEntity() instanceof net.minecraft.world.entity.player.Player victim
                && HulkState.isTransformed(victim) && levelOf(victim) >= 9) {
            event.setCanceled(true);
        }
    }

    private static int levelOf(net.minecraft.world.entity.player.Player player) {
        return player.level().isClientSide ? HulkState.level() : HulkPowers.level(player);
    }

    /** Extra life: the first death of a Hulk is cancelled. */
    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOW)
    public static void onDeath(net.minecraftforge.event.entity.living.LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && !event.isCanceled()
                && !event.getSource().is(net.minecraft.world.damagesource.DamageTypes.FELL_OUT_OF_WORLD)
                && !event.getSource().is(net.minecraft.world.damagesource.DamageTypes.GENERIC_KILL)
                && HulkPowers.tryExtraLife(player)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        HulkPowers.copy(event.getOriginal(), event.getEntity());
        if (event.isWasDeath()) {
            // a normal death and respawn: the extra life is ready again
            HulkPowers.resetExtraLife(event.getEntity());
        }
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
                .then(Commands.literal("level")
                        .then(Commands.argument("level", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, HulkPowers.MAX_LEVEL))
                                .executes(c -> level(c.getSource().getPlayerOrException(),
                                        com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "level"), c.getSource()))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(c -> level(EntityArgument.getPlayer(c, "player"),
                                                com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "level"), c.getSource())))))
                .then(Commands.literal("rage")
                        .then(Commands.argument("percent", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 100))
                                .executes(c -> rage(c.getSource().getPlayerOrException(),
                                        com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "percent"), c.getSource()))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(c -> rage(EntityArgument.getPlayer(c, "player"),
                                                com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "percent"), c.getSource())))))
                .then(Commands.literal("red")
                        .then(redMode("on", true))
                        .then(redMode("off", false)))
                .then(Commands.literal("cooldown")
                        .then(cooldownMode("reset", null))
                        .then(cooldownMode("off", Boolean.TRUE))
                        .then(cooldownMode("on", Boolean.FALSE)))
                .then(Commands.literal("remove")
                        .executes(c -> remove(c.getSource().getPlayerOrException(), c.getSource()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(c -> remove(EntityArgument.getPlayer(c, "player"), c.getSource())))));
    }

    /** /hulk red on|off [player]: the Red Hulk (red skin and HUD). */
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<net.minecraft.commands.CommandSourceStack>
            redMode(String name, boolean red) {
        return Commands.literal(name)
                .executes(c -> red(c.getSource().getPlayerOrException(), red, c.getSource()))
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(c -> red(EntityArgument.getPlayer(c, "player"), red, c.getSource())));
    }

    private static int red(ServerPlayer player, boolean red, net.minecraft.commands.CommandSourceStack source) {
        if (!HulkPowers.has(player)) {
            source.sendFailure(Component.translatable("commands.flash.hulk.none", player.getDisplayName()));
            return 0;
        }
        HulkPowers.setRed(player, red);
        source.sendSuccess(() -> Component.translatable(red ? "commands.flash.hulk.red_on" : "commands.flash.hulk.red_off",
                player.getDisplayName()), true);
        return 1;
    }

    /** /hulk cooldown reset|off|on [player]: clears the cooldowns, or turns them off / back on. */
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<net.minecraft.commands.CommandSourceStack>
            cooldownMode(String name, Boolean disable) {
        return Commands.literal(name)
                .executes(c -> cooldowns(c.getSource().getPlayerOrException(), disable, c.getSource()))
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(c -> cooldowns(EntityArgument.getPlayer(c, "player"), disable, c.getSource())));
    }

    private static int cooldowns(ServerPlayer player, Boolean disable, net.minecraft.commands.CommandSourceStack source) {
        if (!HulkPowers.has(player)) {
            source.sendFailure(Component.translatable("commands.flash.hulk.none", player.getDisplayName()));
            return 0;
        }
        HulkPowers.resetCooldowns(player, disable);
        String key = disable == null ? "commands.flash.hulk.cooldown_reset"
                : disable ? "commands.flash.hulk.cooldown_off" : "commands.flash.hulk.cooldown_on";
        source.sendSuccess(() -> Component.translatable(key, player.getDisplayName()), true);
        return 1;
    }

    private static int give(ServerPlayer player, net.minecraft.commands.CommandSourceStack source) {
        HulkPowers.grant(player, false);
        source.sendSuccess(() -> Component.translatable("commands.flash.hulk.give", player.getDisplayName()), true);
        return 1;
    }

    private static int level(ServerPlayer player, int level, net.minecraft.commands.CommandSourceStack source) {
        HulkPowers.setLevel(player, level, 0.0F);
        source.sendSuccess(() -> Component.translatable("commands.flash.hulk.level", player.getDisplayName(), level), true);
        return 1;
    }

    private static int rage(ServerPlayer player, int percent, net.minecraft.commands.CommandSourceStack source) {
        if (!HulkPowers.has(player)) {
            HulkPowers.grant(player, false);
        }
        HulkPowers.setLevel(player, HulkPowers.level(player), percent);
        source.sendSuccess(() -> Component.translatable("commands.flash.hulk.rage", player.getDisplayName(), percent), true);
        return 1;
    }

    private static int remove(ServerPlayer player, net.minecraft.commands.CommandSourceStack source) {
        HulkPowers.revoke(player);
        source.sendSuccess(() -> Component.translatable("commands.flash.hulk.remove", player.getDisplayName()), true);
        return 1;
    }
}
