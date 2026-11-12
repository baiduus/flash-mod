package com.flash.lex;

import com.flash.FlashMod;
import com.flash.item.LexArmorItem;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * {@code /lex cooldown reset|off|on [player]}: clears the cooldowns of the suit, or turns them off / back on (kept
 * on the player). {@code /lex energy <0-100> [player]}: sets the battery of the chestplate worn.
 */
@Mod.EventBusSubscriber(modid = FlashMod.MODID)
public final class LexCommands {

    private LexCommands() {
    }

    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("lex")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("cooldown")
                        .then(cooldownMode("reset", null))
                        .then(cooldownMode("off", Boolean.TRUE))
                        .then(cooldownMode("on", Boolean.FALSE)))
                .then(Commands.literal("energy")
                        .then(Commands.argument("amount", IntegerArgumentType.integer(0, 100))
                                .executes(c -> energy(c.getSource().getPlayerOrException(),
                                        IntegerArgumentType.getInteger(c, "amount"), c.getSource()))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(c -> energy(EntityArgument.getPlayer(c, "player"),
                                                IntegerArgumentType.getInteger(c, "amount"), c.getSource()))))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> cooldownMode(String name, Boolean disable) {
        return Commands.literal(name)
                .executes(c -> cooldowns(c.getSource().getPlayerOrException(), disable, c.getSource()))
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(c -> cooldowns(EntityArgument.getPlayer(c, "player"), disable, c.getSource())));
    }

    private static int cooldowns(ServerPlayer player, Boolean disable, CommandSourceStack source) {
        LexSuit.resetCooldowns(player, disable);
        String key = disable == null ? "commands.flash.lex.cooldown_reset"
                : disable ? "commands.flash.lex.cooldown_off" : "commands.flash.lex.cooldown_on";
        source.sendSuccess(() -> Component.translatable(key, player.getDisplayName()), true);
        return 1;
    }

    private static int energy(ServerPlayer player, int amount, CommandSourceStack source) {
        ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
        if (!LexArmorItem.isLex(chest)) {
            source.sendFailure(Component.translatable("commands.flash.lex.no_suit", player.getDisplayName()));
            return 0;
        }
        LexArmorItem.setEnergy(chest, amount);
        source.sendSuccess(() -> Component.translatable("commands.flash.lex.energy", player.getDisplayName(), amount),
                true);
        return 1;
    }
}
