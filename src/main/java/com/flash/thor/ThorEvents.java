package com.flash.thor;

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

/** Thor gameplay hooks: the god's body, sync, commands ({@code /thor give | remove | level | energy | cooldown}). */
@Mod.EventBusSubscriber(modid = FlashMod.MODID)
public final class ThorEvents {

    private ThorEvents() {
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.player instanceof ServerPlayer player) {
            ThorPowers.tick(player);
        }
    }

    /** Lightning, fire and falls do not hurt the God of Thunder; his own blasts neither. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onAttack(LivingAttackEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer victim) || !ThorPowers.isThor(victim)) {
            return;
        }
        DamageSource source = event.getSource();
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return;
        }
        if (source.is(DamageTypeTags.IS_LIGHTNING) || source.is(DamageTypeTags.IS_FIRE)
                || source.is(DamageTypeTags.IS_FALL)
                || (source.is(DamageTypeTags.IS_EXPLOSION) && source.getEntity() == victim)) {
            event.setCanceled(true);
            if (source.is(DamageTypeTags.IS_FIRE)) {
                victim.clearFire();
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onHurt(LivingHurtEvent event) {
        if (event.getEntity() instanceof ServerPlayer victim && ThorPowers.isThor(victim)
                && !event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            float amount = event.getAmount() * ThorPowers.damageFactor(victim);
            if (ThorPowers.flag(victim, ThorPowers.F_SPIN)) {
                // the spinning hammer keeps most of it away
                amount *= 0.3F;
                victim.level().playSound(null, victim.getX(), victim.getY(), victim.getZ(),
                        com.flash.registry.ModSounds.THOR_BLOCK.get(), net.minecraft.sounds.SoundSource.PLAYERS, 0.8F, 1.0F);
            }
            if (ThorPowers.flag(victim, ThorPowers.F_OVERCHARGE) && (event.getSource().is(DamageTypeTags.IS_EXPLOSION)
                    || event.getSource().is(DamageTypeTags.IS_PROJECTILE) || event.getSource().is(DamageTypeTags.WITCH_RESISTANT_TO))) {
                amount *= 0.75F;
            }
            event.setAmount(amount);
            ThorPowers.addXp(victim, 0.5F);
        }
        if (event.getSource().getEntity() instanceof ServerPlayer attacker && attacker != event.getEntity()
                && ThorPowers.isThor(attacker)) {
            ThorPowers.addXp(attacker, 0.4F);
            if (event.getSource().getDirectEntity() == attacker && ThorPowers.flag(attacker, ThorPowers.F_FURY)
                    && event.getEntity().invulnerableTime <= 10) {
                ThorAbilities.furyPunch(attacker, event.getEntity());
            }
        }
    }

    @SubscribeEvent
    public static void onFall(LivingFallEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && ThorPowers.isThor(player)) {
            event.setDamageMultiplier(0.0F);
            event.setDistance(0.0F);
        }
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        ThorPowers.copy(event.getOriginal(), event.getEntity(), event.isWasDeath());
        if (event.getEntity() instanceof ServerPlayer player) {
            ThorPowers.removeAttributes(player);
        }
    }

    private static void refresh(ServerPlayer player) {
        if (!ThorPowers.has(player)) {
            return;
        }
        ThorPowers.clearTransient(player);
        ThorPowers.applyAttributes(player);
        ThorPowers.sync(player);
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
            ThorAbilities.stopAll(player);
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
                && ThorPowers.has(target)) {
            ThorPowers.syncTo(listener, target);
        }
    }

    /** Thor's own blasts never hurt or push him. */
    @SubscribeEvent
    public static void onDetonate(net.minecraftforge.event.level.ExplosionEvent.Detonate event) {
        if (event.getExplosion().getIndirectSourceEntity() instanceof ServerPlayer p && ThorPowers.has(p)) {
            event.getAffectedEntities().remove(p);
        }
    }

    // ------------------------------------------------------------------
    // Commands
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("thor")
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
                        .then(Commands.argument("level", IntegerArgumentType.integer(1, ThorPowers.MAX_LEVEL))
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
        if (!ThorPowers.has(player)) {
            source.sendFailure(Component.translatable("commands.flash.thor.none", player.getDisplayName()));
            return false;
        }
        return true;
    }

    private static int give(ServerPlayer player, CommandSourceStack source) {
        ThorPowers.grant(player, false);
        source.sendSuccess(() -> Component.translatable("commands.flash.thor.give", player.getDisplayName()), true);
        return 1;
    }

    private static int remove(ServerPlayer player, CommandSourceStack source) {
        ThorPowers.revoke(player);
        source.sendSuccess(() -> Component.translatable("commands.flash.thor.remove", player.getDisplayName()), true);
        return 1;
    }

    private static int level(ServerPlayer player, int level, CommandSourceStack source) {
        ThorPowers.setLevel(player, level, 0.0F);
        source.sendSuccess(() -> Component.translatable("commands.flash.thor.level", player.getDisplayName(), level), true);
        return 1;
    }

    private static int energy(ServerPlayer player, int amount, CommandSourceStack source) {
        if (!check(player, source)) {
            return 0;
        }
        ThorPowers.setEnergy(player, amount);
        ThorPowers.syncSelf(player);
        source.sendSuccess(() -> Component.translatable("commands.flash.thor.energy", player.getDisplayName(), amount), true);
        return 1;
    }

    private static int infinite(ServerPlayer player, boolean on, CommandSourceStack source) {
        if (!check(player, source)) {
            return 0;
        }
        ThorPowers.setInfiniteEnergy(player, on);
        source.sendSuccess(() -> Component.translatable(on ? "commands.flash.thor.infinite_on"
                : "commands.flash.thor.infinite_off", player.getDisplayName()), true);
        return 1;
    }

    private static int cooldowns(ServerPlayer player, Boolean disable, CommandSourceStack source) {
        if (!check(player, source)) {
            return 0;
        }
        ThorPowers.resetCooldowns(player, disable);
        String key = disable == null ? "commands.flash.thor.cooldown_reset"
                : disable ? "commands.flash.thor.cooldown_off" : "commands.flash.thor.cooldown_on";
        source.sendSuccess(() -> Component.translatable(key, player.getDisplayName()), true);
        return 1;
    }
}
