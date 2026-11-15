package com.flash.doom;

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
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

/**
 * The way to Doctor Doom: armour and sorcery. A player with no power, in a full netherite armour, crouching and
 * using an enchanting table, reads the grimoire of Latveria in it: he is asked whether he wants the power
 * (HeroOfferScreen). Yes: he becomes Doom; no: nothing (he can read it again).
 * <p>
 * {@code /doom give | remove | offer [player]}.
 */
@Mod.EventBusSubscriber(modid = FlashMod.MODID)
public final class DoomCalling {

    private static final Map<UUID, Integer> ASKED = new HashMap<>();

    private DoomCalling() {
    }

    private static boolean eligible(ServerPlayer p) {
        return com.flash.scarlet.ScarletChaos.eligible(p) && !p.isSpectator();
    }

    /** The full netherite armour on. */
    public static boolean armoured(ServerPlayer p) {
        return p.getItemBySlot(EquipmentSlot.HEAD).is(Items.NETHERITE_HELMET)
                && p.getItemBySlot(EquipmentSlot.CHEST).is(Items.NETHERITE_CHESTPLATE)
                && p.getItemBySlot(EquipmentSlot.LEGS).is(Items.NETHERITE_LEGGINGS)
                && p.getItemBySlot(EquipmentSlot.FEET).is(Items.NETHERITE_BOOTS);
    }

    @SubscribeEvent
    public static void onUse(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || !p.isShiftKeyDown()
                || !event.getLevel().getBlockState(event.getPos()).is(Blocks.ENCHANTING_TABLE)) {
            return;
        }
        if (!eligible(p)) {
            return;
        }
        if (!armoured(p)) {
            p.displayClientMessage(Component.translatable("message.flash.doom.need_armour"), true);
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (ASKED.containsKey(p.getUUID())) {
            return;
        }
        ServerLevel level = p.serverLevel();
        var at = event.getPos().getCenter();
        level.sendParticles(ParticleTypes.ENCHANT, at.x, at.y + 0.8D, at.z, 80, 0.6D, 0.6D, 0.6D, 1.0D);
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, at.x, at.y + 0.8D, at.z, 20, 0.3D, 0.3D, 0.3D, 0.02D);
        offer(p);
    }

    public static void offer(ServerPlayer p) {
        ASKED.put(p.getUUID(), p.server.getTickCount());
        p.serverLevel().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENCHANTMENT_TABLE_USE,
                SoundSource.PLAYERS, 1.0F, 0.6F);
        p.serverLevel().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.EVOKER_PREPARE_WOLOLO,
                SoundSource.PLAYERS, 0.6F, 0.6F);
        FlashNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new HeroOfferPacket(HeroSyncPacket.DOOM));
    }

    public static void answer(ServerPlayer p, boolean accept) {
        if (ASKED.remove(p.getUUID()) == null) {
            return;
        }
        ServerLevel level = p.serverLevel();
        if (accept && eligible(p)) {
            DoomPowers.grant(p);
            p.heal(p.getMaxHealth());
            level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.EVOKER_CAST_SPELL, SoundSource.PLAYERS,
                    1.5F, 0.6F);
            level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ANVIL_USE, SoundSource.PLAYERS, 0.8F, 0.6F);
            level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS,
                    1.2F, 0.7F);
            DoomAbilities.fx(p, DoomAbilities.FX_GRIMOIRE, p.position(), p.position(), 1.0F, -1);
            p.sendSystemMessage(Component.translatable("message.flash.doom.accepted"));
        } else {
            level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BOOK_PUT, SoundSource.PLAYERS, 1.0F, 0.8F);
            p.displayClientMessage(Component.translatable("message.flash.doom.refused"), true);
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
        event.getDispatcher().register(Commands.literal("doom")
                .requires(source -> source.hasPermission(2))
                .then(sub("give", DoomPowers::grant, "commands.flash.doom.given"))
                .then(sub("remove", DoomPowers::revoke, "commands.flash.doom.removed"))
                .then(sub("offer", DoomCalling::offer, "commands.flash.doom.offered"))
                .then(sub("god", DoomAscension::makeGod, "commands.flash.doom.god"))
                .then(sub("ungod", DoomAscension::unmakeGod, "commands.flash.doom.ungod")));
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
