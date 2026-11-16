package com.flash.infinity;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** The /infinity commands of the Orb, the Gauntlet and the Soul Guardian. */
final class InfinityExtraCommands {

    private InfinityExtraCommands() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> orb() {
        return InfinityOrb.command();
    }

    /**
     * /infinity gauntlet snap [hostile|players|destroy_stones] (run by the gauntlet's abilities, or an operator wearing
     * it), /infinity gauntlet skin set &lt;id&gt; | clear.
     */
    static LiteralArgumentBuilder<CommandSourceStack> gauntlet() {
        return Commands.literal("gauntlet").requires(s -> s.hasPermission(2))
                .then(Commands.literal("snap")
                        .executes(c -> snap(c.getSource(), InfinityGauntlet.SnapType.HALF))
                        .then(Commands.literal("hostile")
                                .executes(c -> snap(c.getSource(), InfinityGauntlet.SnapType.HOSTILE)))
                        .then(Commands.literal("players")
                                .executes(c -> snap(c.getSource(), InfinityGauntlet.SnapType.PLAYERS)))
                        .then(Commands.literal("destroy_stones").executes(c -> {
                            ServerPlayer p = c.getSource().getPlayerOrException();
                            if (InfinityGauntlet.worn(p).isEmpty()) {
                                return 0;
                            }
                            InfinityGauntlet.destroyStones(p);
                            return 1;
                        })))
                .then(Commands.literal("skin")
                        .then(Commands.literal("set").then(Commands.argument("skin", StringArgumentType.string())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(InfinityGauntlet.skins().keySet(),
                                        b))
                                .executes(c -> {
                                    ServerPlayer p = c.getSource().getPlayerOrException();
                                    ItemStack g = InfinityGauntlet.worn(p);
                                    String id = StringArgumentType.getString(c, "skin");
                                    if (g.isEmpty() || !InfinityGauntlet.skins().containsKey(id)) {
                                        c.getSource().sendFailure(Component.translatable(
                                                "message.flash.infinity.no_gauntlet"));
                                        return 0;
                                    }
                                    g.getOrCreateTag().putString(InfinityGauntlet.SKIN, id);
                                    return 1;
                                })))
                        .then(Commands.literal("clear").executes(c -> {
                            InfinityGauntlet.clearSkin(c.getSource().getPlayerOrException(), null);
                            return 1;
                        })));
    }

    private static int snap(CommandSourceStack src, InfinityGauntlet.SnapType type)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        if (InfinityGauntlet.worn(p).isEmpty()) {
            src.sendFailure(Component.translatable("message.flash.infinity.no_gauntlet"));
            return 0;
        }
        return InfinityGauntlet.snap(p, type) ? 1 : 0;
    }

    static LiteralArgumentBuilder<CommandSourceStack> soulGuardian() {
        return InfinityExtras.soulGuardian();
    }
}
