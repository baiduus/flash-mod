package com.flash.yas;

import com.flash.FlashMod;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.function.BiFunction;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * {@code /yas give [player]}: makes him Yas at once (marked as having transcended, so he owns his essence);
 * {@code /yas transcend [player]}: plays the whole transcendence and gives Yas, asking nothing;
 * {@code /yas remove [player]}: takes Yas away (the mark stays). {@code /locate structure flash:yas_island}
 * finds the floating island.
 */
@Mod.EventBusSubscriber(modid = FlashMod.MODID)
public final class YasCommands {

    private YasCommands() {
    }

    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("yas")
                .requires(source -> source.hasPermission(2))
                .then(sub("give", (p, src) -> {
                    YasPowers.grant(p);
                    return "commands.flash.yas.given";
                }))
                .then(sub("transcend", (p, src) -> {
                    YasTranscendence.begin(p, null, true);
                    return "commands.flash.yas.transcend";
                }))
                .then(sub("remove", (p, src) -> {
                    YasPowers.revoke(p);
                    return "commands.flash.yas.removed";
                })));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> sub(String name,
            BiFunction<ServerPlayer, CommandSourceStack, String> action) {
        return Commands.literal(name)
                .executes(c -> run(c.getSource().getPlayerOrException(), c.getSource(), action))
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(c -> run(EntityArgument.getPlayer(c, "player"), c.getSource(), action)));
    }

    private static int run(ServerPlayer p, CommandSourceStack src,
                           BiFunction<ServerPlayer, CommandSourceStack, String> action) {
        String key = action.apply(p, src);
        src.sendSuccess(() -> Component.translatable(key, p.getDisplayName()), true);
        return 1;
    }
}
