package com.flash.spider;

import com.flash.FlashMod;
import com.flash.network.FlashNetwork;
import com.flash.network.HeroOfferPacket;
import com.flash.network.HeroSyncPacket;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.HashMap;
import java.util.Iterator;
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
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.monster.CaveSpider;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

/**
 * The way to Spider-Man: a player with no power bitten by a cave spider has a chance ({@link #CHANCE}) that the
 * bite changes him: a moment later he is asked whether he wants the power (HeroOfferScreen). Yes: he becomes
 * Spider-Man; no: nothing, the next bite may ask again.
 * <p>
 * {@code /spiderman give | remove | offer [player]}.
 */
@Mod.EventBusSubscriber(modid = FlashMod.MODID)
public final class SpiderCalling {

    /** Chance of a cave spider's bite to change him (1 in 4). */
    public static final float CHANCE = 0.25F;
    private static final Map<UUID, Integer> ASKED = new HashMap<>();
    private static final Map<UUID, Integer> PENDING = new HashMap<>();

    private SpiderCalling() {
    }

    private static boolean eligible(ServerPlayer p) {
        return com.flash.scarlet.ScarletChaos.eligible(p) && !p.isSpectator();
    }

    @SubscribeEvent
    public static void onBite(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || !(event.getSource().getEntity() instanceof CaveSpider)
                || !eligible(p)) {
            return;
        }
        UUID id = p.getUUID();
        if (ASKED.containsKey(id) || PENDING.containsKey(id) || p.getRandom().nextFloat() >= CHANCE) {
            return;
        }
        PENDING.put(id, p.server.getTickCount() + 40);
        p.displayClientMessage(Component.translatable("message.flash.spider.bitten"), true);
        p.serverLevel().sendParticles(ParticleTypes.WITCH, p.getX(), p.getY() + 1.0D, p.getZ(), 20, 0.3D, 0.6D, 0.3D,
                0.02D);
    }

    public static void offer(ServerPlayer p) {
        ASKED.put(p.getUUID(), p.server.getTickCount());
        p.serverLevel().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.SPIDER_AMBIENT, SoundSource.PLAYERS,
                0.8F, 1.4F);
        FlashNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new HeroOfferPacket(HeroSyncPacket.SPIDER));
    }

    public static void answer(ServerPlayer p, boolean accept) {
        if (ASKED.remove(p.getUUID()) == null) {
            return;
        }
        ServerLevel level = p.serverLevel();
        if (accept && eligible(p)) {
            SpiderPowers.grant(p);
            p.removeEffect(MobEffects.POISON);
            p.heal(p.getMaxHealth());
            level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.SPIDER_STEP, SoundSource.PLAYERS, 1.2F,
                    0.8F);
            level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE,
                    SoundSource.PLAYERS, 1.2F, 1.6F);
            SpiderAbilities.fx(p, SpiderAbilities.FX_BITE, p.position(), p.position(), 1.0F, -1);
            SpiderAbilities.fx(p, SpiderAbilities.FX_SENSE, p.position(), p.position(), 2.0F, -1);
            p.sendSystemMessage(Component.translatable("message.flash.spider.accepted"));
        } else {
            level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.SPIDER_DEATH, SoundSource.PLAYERS, 0.5F,
                    1.6F);
            p.displayClientMessage(Component.translatable("message.flash.spider.refused"), true);
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        int now = event.getServer().getTickCount();
        for (Iterator<Map.Entry<UUID, Integer>> it = PENDING.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Integer> en = it.next();
            ServerPlayer p = event.getServer().getPlayerList().getPlayer(en.getKey());
            if (p == null) {
                it.remove();
                continue;
            }
            if (now < en.getValue()) {
                continue;
            }
            it.remove();
            if (p.isAlive() && eligible(p)) {
                offer(p);
            }
        }
        ASKED.values().removeIf(t -> now - t > 6000);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        ASKED.remove(event.getEntity().getUUID());
        PENDING.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("spiderman")
                .requires(source -> source.hasPermission(2))
                .then(sub("give", SpiderPowers::grant, "commands.flash.spider.given"))
                .then(sub("remove", SpiderPowers::revoke, "commands.flash.spider.removed"))
                .then(sub("offer", SpiderCalling::offer, "commands.flash.spider.offered")));
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
