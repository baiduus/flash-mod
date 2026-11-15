package com.flash.wonder;

import com.flash.FlashMod;
import com.flash.network.FlashNetwork;
import com.flash.network.HeroOfferPacket;
import com.flash.network.HeroSyncPacket;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.monster.Ravager;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.SwordItem;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

/**
 * The way to Wonder Woman: the warrior's trial. A player with no power, a sword in the hand and a shield in the
 * other, who brings down a Ravager is asked by the Amazons whether she wants the power of Themyscira
 * (HeroOfferScreen). Yes: she becomes Wonder Woman; no: nothing (another Ravager asks again).
 * <p>
 * {@code /wonderwoman give | remove | offer [player]}.
 */
@Mod.EventBusSubscriber(modid = FlashMod.MODID)
public final class WonderCalling {

    private static final Map<UUID, Integer> ASKED = new HashMap<>();

    private WonderCalling() {
    }

    private static boolean eligible(ServerPlayer p) {
        return com.flash.scarlet.ScarletChaos.eligible(p) && !p.isSpectator();
    }

    /** A sword in the main hand and a shield in the other. */
    public static boolean armed(ServerPlayer p) {
        return p.getMainHandItem().getItem() instanceof SwordItem
                && p.getOffhandItem().getItem() instanceof ShieldItem;
    }

    @SubscribeEvent
    public static void onKill(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof Ravager) || !(event.getSource().getEntity() instanceof ServerPlayer p)
                || !eligible(p)) {
            return;
        }
        if (!armed(p)) {
            p.displayClientMessage(Component.translatable("message.flash.wonder.need_arms"), true);
            return;
        }
        if (!ASKED.containsKey(p.getUUID())) {
            offer(p);
        }
    }

    public static void offer(ServerPlayer p) {
        ASKED.put(p.getUUID(), p.server.getTickCount());
        ServerLevel level = p.serverLevel();
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0F,
                1.2F);
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS,
                1.0F, 0.8F);
        level.sendParticles(ParticleTypes.END_ROD, p.getX(), p.getY() + 1.0D, p.getZ(), 40, 0.6D, 1.0D, 0.6D, 0.05D);
        FlashNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new HeroOfferPacket(HeroSyncPacket.WONDER));
    }

    public static void answer(ServerPlayer p, boolean accept) {
        if (ASKED.remove(p.getUUID()) == null) {
            return;
        }
        ServerLevel level = p.serverLevel();
        if (accept && eligible(p)) {
            WonderPowers.grant(p);
            p.heal(p.getMaxHealth());
            level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS,
                    0.8F, 1.4F);
            level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS,
                    1.2F, 1.2F);
            WonderAbilities.fx(p, WonderAbilities.FX_CALL, p.position(), p.position(), 1.0F, -1);
            p.sendSystemMessage(Component.translatable("message.flash.wonder.accepted"));
        } else {
            level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS,
                    1.0F, 1.0F);
            p.displayClientMessage(Component.translatable("message.flash.wonder.refused"), true);
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            int now = event.getServer().getTickCount();
            ASKED.values().removeIf(t -> now - t > 6000);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        ASKED.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("wonderwoman")
                .requires(source -> source.hasPermission(2))
                .then(sub("give", WonderPowers::grant, "commands.flash.wonder.given"))
                .then(sub("remove", WonderPowers::revoke, "commands.flash.wonder.removed"))
                .then(sub("offer", WonderCalling::offer, "commands.flash.wonder.offered")));
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
