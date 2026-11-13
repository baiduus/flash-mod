package com.flash.scarlet;

import com.flash.FlashMod;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.function.Consumer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** {@code /scarlet give [player]} makes him the Scarlet Witch; {@code /scarlet remove [player]} takes it away. */
@Mod.EventBusSubscriber(modid = FlashMod.MODID)
public final class ScarletCommands {

    private ScarletCommands() {
    }

    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("scarlet")
                .requires(source -> source.hasPermission(2))
                .then(sub("give", ScarletPowers::grant, "commands.flash.scarlet.given"))
                .then(sub("remove", ScarletPowers::revoke, "commands.flash.scarlet.removed")));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> sub(String name, Consumer<ServerPlayer> action,
                                                                  String key) {
        return Commands.literal(name)
                .executes(c -> run(c.getSource().getPlayerOrException(), c.getSource(), action, key))
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(c -> run(EntityArgument.getPlayer(c, "player"), c.getSource(), action, key)));
    }

    private static int run(ServerPlayer p, CommandSourceStack src, Consumer<ServerPlayer> action, String key) {
        action.accept(p);
        src.sendSuccess(() -> Component.translatable(key, p.getDisplayName()), true);
        return 1;
    }
}
