package com.flash.superman;

import com.flash.FlashMod;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Superman gameplay hooks: steel body, sync, commands. */
@Mod.EventBusSubscriber(modid = FlashMod.MODID)
public final class SupermanEvents {

    private SupermanEvents() {
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.player instanceof ServerPlayer player) {
            SupermanPowers.tick(player);
        }
    }

    /**
     * Jumps (both sides: the client moves the player): a normal jump reaches three
     * blocks; crouch + jump is an aimed leap that grows with the level.
     */
    @SubscribeEvent
    public static void onJump(net.minecraftforge.event.entity.living.LivingEvent.LivingJumpEvent event) {
        if (!(event.getEntity() instanceof net.minecraft.world.entity.player.Player player)
                || !SupermanState.isSuperman(player) || player.getAbilities().flying) {
            return;
        }
        int level = player.level().isClientSide ? SupermanState.level() : SupermanPowers.level(player);
        net.minecraft.world.phys.Vec3 motion = player.getDeltaMovement();
        if (player.isShiftKeyDown()) {
            net.minecraft.world.phys.Vec3 look = player.getLookAngle().normalize();
            double power = 1.4D + 0.12D * Math.max(1, level);
            player.setDeltaMovement(look.x * power, Math.max(0.6D, look.y * power + 0.5D), look.z * power);
            if (player.level().isClientSide) {
                player.playSound(com.flash.registry.ModSounds.TAKEOFF.get(), 0.8F, 1.2F);
            }
        } else {
            player.setDeltaMovement(motion.x, Math.max(motion.y, 0.68D), motion.z);
        }
    }

    /** Steel body: projectiles bounce off, no fire, falls, drowning, freezing or own blasts. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onAttack(LivingAttackEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer victim) || !SupermanPowers.isSuperman(victim)) {
            return;
        }
        DamageSource source = event.getSource();
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return;
        }
        if (source.is(DamageTypeTags.IS_PROJECTILE) || source.is(DamageTypeTags.IS_FIRE)
                || source.is(DamageTypeTags.IS_FALL) || source.is(DamageTypeTags.IS_DROWNING)
                || source.is(DamageTypeTags.IS_FREEZING)
                || (source.is(DamageTypeTags.IS_EXPLOSION) && source.getEntity() == victim)) {
            event.setCanceled(true);
            if (source.is(DamageTypeTags.IS_FIRE)) {
                victim.clearFire();
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onHurt(LivingHurtEvent event) {
        if (event.getEntity() instanceof ServerPlayer victim && SupermanPowers.isSuperman(victim)
                && !event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            event.setAmount(event.getAmount() * SupermanPowers.damageFactor(victim));
            SupermanPowers.addXp(victim, 0.6F);
            // whoever is held and hits Superman breaks free
            if (event.getSource().getEntity() != null) {
                SupermanAbilities.breakFree(victim, event.getSource().getEntity());
            }
        }
        if (event.getSource().getEntity() instanceof ServerPlayer attacker && attacker != event.getEntity()
                && SupermanPowers.isSuperman(attacker)) {
            SupermanPowers.addXp(attacker, 0.4F);
        }
    }

    @SubscribeEvent
    public static void onFall(LivingFallEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && SupermanPowers.isSuperman(player)) {
            float distance = event.getDistance();
            event.setDamageMultiplier(0.0F);
            event.setDistance(0.0F);
            // more than 10 blocks: the ground cracks and he lands in the superhero pose
            if (distance > 10.0F) {
                SupermanAbilities.heroLanding(player, distance);
            }
        }
    }

    /** Superman can always fly (mayfly), so a fall comes as this event instead of LivingFallEvent. */
    @SubscribeEvent
    public static void onFlyableFall(net.minecraftforge.event.entity.player.PlayerFlyableFallEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && SupermanPowers.isSuperman(player)
                && event.getDistance() > 10.0F) {
            SupermanAbilities.heroLanding(player, event.getDistance());
        }
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        SupermanPowers.copy(event.getOriginal(), event.getEntity(), event.isWasDeath());
        if (event.getEntity() instanceof ServerPlayer player) {
            SupermanPowers.removeAttributes(player);
        }
    }

    private static void refresh(ServerPlayer player) {
        if (!SupermanPowers.has(player)) {
            return;
        }
        SupermanPowers.clearTransient(player);
        SupermanPowers.applyAttributes(player);
        if (SupermanPowers.isSuperman(player) && SupermanPowers.flag(player, SupermanPowers.F_FLIGHT)) {
            player.getAbilities().mayfly = true;
            player.getAbilities().setFlyingSpeed(SupermanPowers.flySpeed(SupermanPowers.level(player)));
            player.onUpdateAbilities();
        }
        SupermanPowers.sync(player);
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            refresh(player);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            SupermanAbilities.stopAll(player);
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            refresh(player);
        }
    }

    @SubscribeEvent
    public static void onDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            refresh(player);
        }
    }

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getEntity() instanceof ServerPlayer listener && event.getTarget() instanceof ServerPlayer target
                && SupermanPowers.has(target)) {
            SupermanPowers.syncTo(listener, target);
        }
    }

    /** Superman's own blasts never hurt or push him. */
    @SubscribeEvent
    public static void onDetonate(net.minecraftforge.event.level.ExplosionEvent.Detonate event) {
        if (event.getExplosion().getIndirectSourceEntity() instanceof ServerPlayer p && SupermanPowers.has(p)) {
            event.getAffectedEntities().remove(p);
        }
    }

    // ------------------------------------------------------------------
    // Commands
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("superman")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("give")
                        .executes(c -> give(c.getSource().getPlayerOrException(), c.getSource()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(c -> give(EntityArgument.getPlayer(c, "player"), c.getSource()))))
                .then(Commands.literal("remove")
                        .executes(c -> remove(c.getSource().getPlayerOrException(), c.getSource()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(c -> remove(EntityArgument.getPlayer(c, "player"), c.getSource()))))
                .then(Commands.literal("level")
                        .then(Commands.argument("level", IntegerArgumentType.integer(1, SupermanPowers.MAX_LEVEL))
                                .executes(c -> level(c.getSource().getPlayerOrException(),
                                        IntegerArgumentType.getInteger(c, "level"), c.getSource()))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(c -> level(EntityArgument.getPlayer(c, "player"),
                                                IntegerArgumentType.getInteger(c, "level"), c.getSource())))))
                .then(Commands.literal("energy")
                        .then(Commands.argument("amount", IntegerArgumentType.integer(0, 100))
                                .executes(c -> energy(c.getSource().getPlayerOrException(),
                                        IntegerArgumentType.getInteger(c, "amount"), c.getSource()))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(c -> energy(EntityArgument.getPlayer(c, "player"),
                                                IntegerArgumentType.getInteger(c, "amount"), c.getSource()))))
                        .then(Commands.literal("infinite")
                                .then(infinite("on", true))
                                .then(infinite("off", false))))
                .then(Commands.literal("cooldown")
                        .then(cooldownMode("reset", null))
                        .then(cooldownMode("off", Boolean.TRUE))
                        .then(cooldownMode("on", Boolean.FALSE))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> infinite(String name, boolean on) {
        return Commands.literal(name)
                .executes(c -> infinite(c.getSource().getPlayerOrException(), on, c.getSource()))
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(c -> infinite(EntityArgument.getPlayer(c, "player"), on, c.getSource())));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> cooldownMode(String name, Boolean disable) {
        return Commands.literal(name)
                .executes(c -> cooldowns(c.getSource().getPlayerOrException(), disable, c.getSource()))
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(c -> cooldowns(EntityArgument.getPlayer(c, "player"), disable, c.getSource())));
    }

    private static boolean check(ServerPlayer player, CommandSourceStack source) {
        if (!SupermanPowers.has(player)) {
            source.sendFailure(Component.translatable("commands.flash.superman.none", player.getDisplayName()));
            return false;
        }
        return true;
    }

    private static int give(ServerPlayer player, CommandSourceStack source) {
        SupermanPowers.grant(player, false);
        source.sendSuccess(() -> Component.translatable("commands.flash.superman.give", player.getDisplayName()), true);
        return 1;
    }

    private static int remove(ServerPlayer player, CommandSourceStack source) {
        SupermanPowers.revoke(player);
        source.sendSuccess(() -> Component.translatable("commands.flash.superman.remove", player.getDisplayName()), true);
        return 1;
    }

    private static int level(ServerPlayer player, int level, CommandSourceStack source) {
        SupermanPowers.setLevel(player, level, 0.0F);
        source.sendSuccess(() -> Component.translatable("commands.flash.superman.level", player.getDisplayName(), level), true);
        return 1;
    }

    private static int energy(ServerPlayer player, int amount, CommandSourceStack source) {
        if (!check(player, source)) {
            return 0;
        }
        SupermanPowers.setEnergy(player, amount);
        SupermanPowers.syncSelf(player);
        source.sendSuccess(() -> Component.translatable("commands.flash.superman.energy", player.getDisplayName(), amount), true);
        return 1;
    }

    private static int infinite(ServerPlayer player, boolean on, CommandSourceStack source) {
        if (!check(player, source)) {
            return 0;
        }
        SupermanPowers.setInfiniteEnergy(player, on);
        source.sendSuccess(() -> Component.translatable(on ? "commands.flash.superman.infinite_on"
                : "commands.flash.superman.infinite_off", player.getDisplayName()), true);
        return 1;
    }

    private static int cooldowns(ServerPlayer player, Boolean disable, CommandSourceStack source) {
        if (!check(player, source)) {
            return 0;
        }
        SupermanPowers.resetCooldowns(player, disable);
        String key = disable == null ? "commands.flash.superman.cooldown_reset"
                : disable ? "commands.flash.superman.cooldown_off" : "commands.flash.superman.cooldown_on";
        source.sendSuccess(() -> Component.translatable(key, player.getDisplayName()), true);
        return 1;
    }
}
