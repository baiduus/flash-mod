package com.flash.ultron;

import com.flash.FlashMod;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * {@code /ultron nanocore [player]}: a nanocore in the inventory; {@code /ultron give [player]}: becomes Ultron at
 * once; {@code /ultron remove [player]}: Ultron taken out (as the surgery table does, the nanocore back).
 */
@Mod.EventBusSubscriber(modid = FlashMod.MODID)
public final class UltronCommands {

    /** /ultron cooldown off: his powers have no cooldowns (kept on the player). */
    public static final String NO_COOLDOWNS = "flash_ultron_no_cooldowns";

    private UltronCommands() {
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> cooldownMode(String name,
                                                                                                    Boolean disable) {
        return Commands.literal(name)
                .executes(c -> cooldowns(c.getSource().getPlayerOrException(), disable, c.getSource()))
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(c -> cooldowns(EntityArgument.getPlayer(c, "player"), disable, c.getSource())));
    }

    /** reset (null), off (true: none from now on) or on (false: back to normal). */
    private static int cooldowns(ServerPlayer p, Boolean disable, CommandSourceStack source) {
        if (disable != null) {
            p.getPersistentData().putBoolean(NO_COOLDOWNS, disable);
        }
        com.flash.scarlet.ScarletAbilities.clearCooldowns(p, "u_");
        String key = disable == null ? "commands.flash.ultron.cooldown_reset"
                : disable ? "commands.flash.ultron.cooldown_off" : "commands.flash.ultron.cooldown_on";
        source.sendSuccess(() -> Component.translatable(key, p.getDisplayName()), true);
        return 1;
    }

    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("ultron")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("nanocore")
                        .executes(c -> nanocore(c.getSource().getPlayerOrException(), c.getSource()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(c -> nanocore(EntityArgument.getPlayer(c, "player"), c.getSource()))))
                .then(Commands.literal("give")
                        .executes(c -> give(c.getSource().getPlayerOrException(), c.getSource()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(c -> give(EntityArgument.getPlayer(c, "player"), c.getSource()))))
                .then(Commands.literal("cooldown")
                        .then(cooldownMode("reset", null))
                        .then(cooldownMode("off", Boolean.TRUE))
                        .then(cooldownMode("on", Boolean.FALSE)))
                .then(Commands.literal("remove")
                        .executes(c -> remove(c.getSource().getPlayerOrException(), c.getSource()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(c -> remove(EntityArgument.getPlayer(c, "player"), c.getSource())))));
    }

    private static int nanocore(ServerPlayer p, CommandSourceStack source) {
        ItemStack core = new ItemStack(UltronRegistry.NANOCORE.get());
        if (!p.getInventory().add(core)) {
            p.drop(core, false);
        }
        source.sendSuccess(() -> Component.translatable("commands.flash.ultron.nanocore", p.getDisplayName()), true);
        return 1;
    }

    private static int give(ServerPlayer p, CommandSourceStack source) {
        UltronPowers.grant(p);
        source.sendSuccess(() -> Component.translatable("commands.flash.ultron.give", p.getDisplayName()), true);
        return 1;
    }

    private static int remove(ServerPlayer p, CommandSourceStack source) {
        if (!UltronPowers.has(p)) {
            source.sendFailure(Component.translatable("commands.flash.ultron.not", p.getDisplayName()));
            return 0;
        }
        UltronPowers.revoke(p);
        source.sendSuccess(() -> Component.translatable("commands.flash.ultron.remove", p.getDisplayName()), true);
        return 1;
    }
}
