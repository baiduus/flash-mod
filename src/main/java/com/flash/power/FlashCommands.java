package com.flash.power;

import com.flash.FlashMod;
import com.flash.ability.FlashAbility;
import com.flash.ability.FlashAbilityManager;
import com.flash.ability.FlashDimensionTravel;
import com.flash.network.FlashSyncPacket;
import com.flash.network.FlashVisualSyncPacket;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Admin/debug commands. The mod is fully playable without them; they exist
 * for testing the progression, levels, combustion and regeneration.
 */
@Mod.EventBusSubscriber(modid = FlashMod.MODID)
public final class FlashCommands {

    private FlashCommands() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("flash")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("grant")
                        .executes(context -> grant(context.getSource(), context.getSource().getPlayerOrException()))
                        .then(Commands.argument("target", EntityArgument.player())
                                .executes(context -> grant(context.getSource(),
                                        EntityArgument.getPlayer(context, "target")))))
                .then(Commands.literal("revoke")
                        .executes(context -> revoke(context.getSource(), context.getSource().getPlayerOrException()))
                        .then(Commands.argument("target", EntityArgument.player())
                                .executes(context -> revoke(context.getSource(),
                                        EntityArgument.getPlayer(context, "target")))))
                .then(Commands.literal("level")
                        .then(Commands.argument("level", IntegerArgumentType.integer(
                                        FlashSpeedLevels.MIN_LEVEL, FlashSpeedLevels.BASE_MAX_LEVEL))
                                .executes(context -> level(context.getSource(),
                                        context.getSource().getPlayerOrException(),
                                        IntegerArgumentType.getInteger(context, "level")))
                                .then(Commands.argument("target", EntityArgument.player())
                                        .executes(context -> level(context.getSource(),
                                                EntityArgument.getPlayer(context, "target"),
                                                IntegerArgumentType.getInteger(context, "level"))))))
                .then(Commands.literal("unlock")
                        .then(Commands.argument("level", IntegerArgumentType.integer(
                                        1, FlashSpeedLevels.BASE_MAX_LEVEL))
                                .executes(context -> unlock(context.getSource(),
                                        context.getSource().getPlayerOrException(),
                                        IntegerArgumentType.getInteger(context, "level")))
                                .then(Commands.argument("target", EntityArgument.player())
                                        .executes(context -> unlock(context.getSource(),
                                                EntityArgument.getPlayer(context, "target"),
                                                IntegerArgumentType.getInteger(context, "level"))))))
                .then(Commands.literal("rank")
                        .then(Commands.argument("level", IntegerArgumentType.integer(
                                        1, FlashSpeedLevels.BASE_MAX_LEVEL))
                                .executes(context -> rank(context.getSource(),
                                        context.getSource().getPlayerOrException(),
                                        IntegerArgumentType.getInteger(context, "level")))
                                .then(Commands.argument("target", EntityArgument.player())
                                        .executes(context -> rank(context.getSource(),
                                                EntityArgument.getPlayer(context, "target"),
                                                IntegerArgumentType.getInteger(context, "level"))))))
                .then(Commands.literal("distance")
                        .then(Commands.argument("blocks", DoubleArgumentType.doubleArg(0.0D, 1_000_000.0D))
                                .executes(context -> distance(context.getSource(),
                                        context.getSource().getPlayerOrException(),
                                        DoubleArgumentType.getDouble(context, "blocks")))))
                .then(Commands.literal("destroyblocks")
                        .then(Commands.argument("value", BoolArgumentType.bool())
                                .executes(context -> destroyBlocks(context.getSource(),
                                        context.getSource().getPlayerOrException(),
                                        BoolArgumentType.getBool(context, "value")))
                                .then(Commands.argument("target", EntityArgument.player())
                                        .executes(context -> destroyBlocks(context.getSource(),
                                                EntityArgument.getPlayer(context, "target"),
                                                BoolArgumentType.getBool(context, "value"))))))
                .then(Commands.literal("ability")
                        .then(Commands.argument("ability", IntegerArgumentType.integer(0, 7))
                                .executes(context -> castAbility(context.getSource(),
                                        context.getSource().getPlayerOrException(),
                                        IntegerArgumentType.getInteger(context, "ability")))
                                .then(Commands.argument("target", EntityArgument.player())
                                        .executes(context -> castAbility(context.getSource(),
                                                EntityArgument.getPlayer(context, "target"),
                                                IntegerArgumentType.getInteger(context, "ability"))))))
                .then(Commands.literal("cooldowns")
                        .then(Commands.argument("value", BoolArgumentType.bool())
                                .executes(context -> cooldowns(context.getSource(),
                                        context.getSource().getPlayerOrException(),
                                        BoolArgumentType.getBool(context, "value")))
                                .then(Commands.argument("target", EntityArgument.player())
                                        .executes(context -> cooldowns(context.getSource(),
                                                EntityArgument.getPlayer(context, "target"),
                                                BoolArgumentType.getBool(context, "value"))))))
                .then(Commands.literal("negative")
                        .then(Commands.argument("value", BoolArgumentType.bool())
                                .executes(context -> negative(context.getSource(),
                                        context.getSource().getPlayerOrException(),
                                        BoolArgumentType.getBool(context, "value")))
                                .then(Commands.argument("target", EntityArgument.player())
                                        .executes(context -> negative(context.getSource(),
                                                EntityArgument.getPlayer(context, "target"),
                                                BoolArgumentType.getBool(context, "value"))))))
                .then(Commands.literal("status")
                        .executes(context -> status(context.getSource(), context.getSource().getPlayerOrException()))
                        .then(Commands.argument("target", EntityArgument.player())
                                .executes(context -> status(context.getSource(),
                                        EntityArgument.getPlayer(context, "target"))))));
    }

    private static int cooldowns(CommandSourceStack source, ServerPlayer target, boolean enabled) {
        FlashAbilityManager.setCooldownsEnabled(target, enabled);
        source.sendSuccess(() -> Component.translatable(
                enabled ? "commands.flash.cooldowns.on" : "commands.flash.cooldowns.off",
                target.getDisplayName()), true);
        return 1;
    }

    /** Test helper: gives or removes the Negative Acceleration Force (level kept). */
    private static int negative(CommandSourceStack source, ServerPlayer target, boolean enabled) {
        if (enabled) {
            FlashPowers.grantNegative(target);
        } else {
            FlashPowers.setNegative(target, false);
        }
        FlashSpeed.apply(target);
        FlashStep.apply(target);
        FlashStatusEffects.onLevelChanged(target);
        FlashSyncPacket.send(target);
        FlashVisualSyncPacket.send(target);
        source.sendSuccess(() -> Component.translatable(
                enabled ? "commands.flash.negative.on" : "commands.flash.negative.off",
                target.getDisplayName()), true);
        return 1;
    }

    private static int destroyBlocks(CommandSourceStack source, ServerPlayer target, boolean enabled) {
        FlashPowers.setDestroyBlocksEnabled(target, enabled);
        FlashSyncPacket.send(target);
        source.sendSuccess(() -> Component.translatable(
                enabled
                        ? "message.flash.destroy_blocks_enabled"
                        : "message.flash.destroy_blocks_disabled"), true);
        return 1;
    }

    private static int castAbility(CommandSourceStack source, ServerPlayer target, int abilityId) {
        FlashAbility ability = FlashAbility.byId(abilityId);
        if (ability == null) {
            source.sendFailure(Component.literal("Unknown ability id: " + abilityId));
            return 0;
        }
        FlashAbilityManager.onPress(target, ability);
        source.sendSuccess(() -> Component.translatable(
                "commands.flash.ability.success", target.getDisplayName(),
                ability.displayName()), true);
        return 1;
    }

    private static int grant(CommandSourceStack source, ServerPlayer target) {
        FlashPowers.grant(target);
        FlashSpeed.apply(target);
        FlashStep.apply(target);
        FlashStatusEffects.onLevelChanged(target);
        FlashSyncPacket.send(target);
        FlashVisualSyncPacket.send(target);
        source.sendSuccess(() -> Component.translatable(
                "commands.flash.grant.success", target.getDisplayName()), true);
        target.displayClientMessage(Component.translatable("message.flash.powers_granted"), false);
        return 1;
    }

    private static int revoke(CommandSourceStack source, ServerPlayer target) {
        FlashPowers.revoke(target);
        FlashSpeed.remove(target);
        FlashStep.remove(target);
        FlashCombustion.reset(target, true);
        FlashStatusEffects.reset(target);
        FlashAbilityManager.reset(target);
        if (FlashDimensionTravel.isInAccelerationForce(target)) {
            FlashDimensionTravel.exit(target);
        }
        FlashSyncPacket.send(target);
        FlashVisualSyncPacket.send(target, 0);
        source.sendSuccess(() -> Component.translatable(
                "commands.flash.revoke.success", target.getDisplayName()), true);
        return 1;
    }

    private static int level(CommandSourceStack source, ServerPlayer target, int level) {
        if (!FlashPowers.hasPowers(target)) {
            source.sendFailure(Component.translatable("commands.flash.no_powers", target.getDisplayName()));
            return 0;
        }
        if (!FlashPowers.setSelectedLevel(target, level)) {
            source.sendFailure(Component.translatable("commands.flash.level_locked", level,
                    FlashPowers.getMaxLevel(target)));
            return 0;
        }
        FlashSpeed.apply(target);
        FlashStep.apply(target);
        FlashCombustion.onLevelChanged(target);
        FlashStatusEffects.onLevelChanged(target);
        FlashSyncPacket.send(target);
        FlashVisualSyncPacket.send(target);
        source.sendSuccess(() -> Component.translatable(
                "commands.flash.level.success", target.getDisplayName(), level), true);
        target.displayClientMessage(Component.translatable("message.flash.level_selected", level), true);
        return 1;
    }

    private static int unlock(CommandSourceStack source, ServerPlayer target, int level) {
        if (!FlashPowers.hasPowers(target)) {
            FlashPowers.grant(target);
        }
        FlashPowers.setMaxLevel(target, level);
        FlashSpeed.apply(target);
        FlashStep.apply(target);
        FlashCombustion.onLevelChanged(target);
        FlashStatusEffects.onLevelChanged(target);
        FlashSyncPacket.send(target);
        FlashVisualSyncPacket.send(target);
        source.sendSuccess(() -> Component.translatable(
                "commands.flash.unlock.success", target.getDisplayName(), FlashPowers.getMaxLevel(target)), true);
        return 1;
    }

    /**
     * Compatibility command from version 1: unlocks and selects the level.
     */
    private static int rank(CommandSourceStack source, ServerPlayer target, int level) {
        if (!FlashPowers.hasPowers(target)) {
            FlashPowers.grant(target);
        }
        FlashPowers.setMaxLevel(target, level);
        FlashPowers.setSelectedLevel(target, level);
        FlashSpeed.apply(target);
        FlashStep.apply(target);
        FlashCombustion.onLevelChanged(target);
        FlashStatusEffects.onLevelChanged(target);
        FlashSyncPacket.send(target);
        FlashVisualSyncPacket.send(target);
        source.sendSuccess(() -> Component.translatable(
                "commands.flash.rank.success", target.getDisplayName(), level), true);
        return 1;
    }

    private static int distance(CommandSourceStack source, ServerPlayer target, double blocks) {
        if (!FlashPowers.hasPowers(target)) {
            FlashPowers.grant(target);
        }
        FlashPowers.setDistance(target, blocks);
        FlashSyncPacket.send(target);
        source.sendSuccess(() -> Component.translatable(
                "commands.flash.distance.success", target.getDisplayName(), blocks), true);
        return 1;
    }

    private static int status(CommandSourceStack source, ServerPlayer target) {
        boolean powers = FlashPowers.hasPowers(target);
        int max = FlashPowers.getMaxLevel(target);
        int selected = FlashPowers.getSelectedLevel(target);
        int toNext = FlashPowers.getBlocksToNextLevel(target);
        boolean maxed = FlashPowers.isMaxLevel(target);

        source.sendSuccess(() -> Component.translatable(
                "commands.flash.status.header", target.getDisplayName()), false);
        source.sendSuccess(() -> Component.translatable(
                "commands.flash.status.powers",
                Component.translatable(powers ? "commands.flash.yes" : "commands.flash.no")), false);
        source.sendSuccess(() -> Component.translatable(
                "commands.flash.status.level", selected), false);
        source.sendSuccess(() -> Component.translatable(
                "commands.flash.status.max", max, FlashSpeedLevels.MAX_LEVEL), false);
        if (maxed) {
            source.sendSuccess(() -> Component.translatable("commands.flash.status.maxed"), false);
        } else {
            source.sendSuccess(() -> Component.translatable(
                    "commands.flash.status.next",
                    (int) Math.floor(FlashPowers.getDistance(target)),
                    FlashSpeedLevels.BLOCKS_PER_LEVEL,
                    toNext), false);
        }
        source.sendSuccess(() -> Component.translatable(
                "commands.flash.status.combustion",
                Component.translatable(FlashCombustion.isCombustionLevel(target)
                        ? "commands.flash.yes" : "commands.flash.no")), false);
        return 1;
    }
}
