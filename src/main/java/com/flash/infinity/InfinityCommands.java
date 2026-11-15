package com.flash.infinity;

import com.flash.FlashMod;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The original's /infinity commands (what its abilities run): the Soul Stone's zombies and locating, the Time Stone's
 * time set; the Orb, the Soul Guardian and the Snap join them (InfinityOrb, Vormir, InfinityGauntlet).
 */
@Mod.EventBusSubscriber(modid = FlashMod.MODID)
public final class InfinityCommands {

    private InfinityCommands() {
    }

    private static boolean ability(ServerPlayer p, String type, boolean bought) {
        InfinityServer.State s = InfinityServer.state(p);
        for (InfPowers.Power pw : InfPowers.all().values()) {
            for (InfPowers.Ability a : pw.abilities.values()) {
                if (type.equals(a.type) && (bought ? s.purchased(a.key()) || a.buys.isEmpty() && s.powers.contains(
                        pw.id) : s.enabled(a.key()))) {
                    return true;
                }
            }
        }
        return false;
    }

    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("infinity")
                .then(Commands.literal("soul")
                        .then(Commands.literal("zombies").executes(c -> {
                            ServerPlayer p = c.getSource().getPlayerOrException();
                            return SoulStone.zombies(p);
                        }))
                        .then(Commands.literal("locateentity")
                                .then(Commands.argument("name", StringArgumentType.greedyString()).executes(c -> {
                                    ServerPlayer p = c.getSource().getPlayerOrException();
                                    if (!ability(p, "flash:locate_player", false)) {
                                        c.getSource().sendFailure(Component.translatable(
                                                "message.flash.infinity.need_soul"));
                                        return 0;
                                    }
                                    String name = StringArgumentType.getString(c, "name");
                                    LivingEntity e = SoulStone.locate(p, name);
                                    if (e == null) {
                                        c.getSource().sendFailure(Component.translatable(
                                                "message.flash.infinity.not_found", name));
                                        return 0;
                                    }
                                    c.getSource().sendSuccess(() -> Component.translatable(
                                            "message.flash.infinity.located", e.getDisplayName(),
                                            e.level().dimension().location().toString(),
                                            e.blockPosition().toShortString()), false);
                                    return 1;
                                }))))
                .then(Commands.literal("time").then(Commands.literal("set")
                        .then(Commands.argument("time", LongArgumentType.longArg(0L)).executes(c -> {
                            ServerPlayer p = c.getSource().getPlayerOrException();
                            if (!ability(p, "flash:time_set", false)) {
                                c.getSource().sendFailure(Component.translatable("message.flash.infinity.need_time"));
                                return 0;
                            }
                            TimeStone.setTime(p, LongArgumentType.getLong(c, "time"));
                            return 1;
                        }))))
                .then(InfinityExtraCommands.orb())
                .then(InfinityExtraCommands.gauntlet())
                .then(InfinityExtraCommands.soulGuardian()));
    }
}
