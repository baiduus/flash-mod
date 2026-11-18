package com.flash.yas;

import com.flash.FlashMod;
import com.flash.network.FlashNetwork;
import com.flash.network.PlayerMorphPacket;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * The Manipulation of Reality on a player (R looking at him, in the mode of Reality): he becomes an animal of the
 * farm. The Scarlet Witch's lasts 10 seconds; Yas' until he dies or Yas does it to him again. Changed, he is drawn as
 * the animal for everyone (MorphClient) and can't hit, break, place or use anything.
 */
@Mod.EventBusSubscriber(modid = FlashMod.MODID)
public final class PlayerMorph {

    private static final EntityType<?>[] FARM = {EntityType.SHEEP, EntityType.PIG, EntityType.COW,
            EntityType.CHICKEN, EntityType.RABBIT};
    private static final int SCARLET_TICKS = 200;

    private record Morph(String type, long until, UUID caster) {
    }

    private static final Map<UUID, Morph> MORPHS = new HashMap<>();

    private PlayerMorph() {
    }

    public static boolean morphed(ServerPlayer p) {
        return MORPHS.containsKey(p.getUUID());
    }

    /** Yas or the Scarlet Witch on another player. */
    public static void cast(ServerPlayer caster, ServerPlayer victim) {
        boolean yas = YasPowers.has(caster);
        boolean scarlet = com.flash.scarlet.ScarletPowers.has(caster);
        if (!yas && !scarlet || victim == caster || victim.isSpectator() || victim.isCreative()) {
            return;
        }
        Morph had = MORPHS.get(victim.getUUID());
        if (yas && had != null) {
            // Yas' gesture again undoes it
            end(victim, true);
            return;
        }
        if (scarlet && !com.flash.scarlet.ScarletAbilities.ready(caster, "reality_mob")) {
            return;
        }
        EntityType<?> into = FARM[victim.getRandom().nextInt(FARM.length)];
        long now = victim.level().getGameTime();
        Morph m = new Morph(String.valueOf(ForgeRegistries.ENTITY_TYPES.getKey(into)), yas ? -1L : now + SCARLET_TICKS,
                caster.getUUID());
        MORPHS.put(victim.getUUID(), m);
        send(victim, m.type);
        wave(victim.serverLevel(), victim);
        victim.displayClientMessage(Component.translatable(yas ? "message.flash.morph_yas" : "message.flash.morph",
                caster.getDisplayName(), into.getDescription()), true);
    }

    private static void wave(ServerLevel level, ServerPlayer v) {
        YasReality.play(level, v.position(), "realidade_onda", SoundEvents.ILLUSIONER_CAST_SPELL, 1.0F);
        level.sendParticles(ParticleTypes.FLASH, v.getX(), v.getY() + 1.0D, v.getZ(), 1, 0.0D, 0.0D, 0.0D, 0.0D);
        level.sendParticles(ParticleTypes.END_ROD, v.getX(), v.getY() + 1.0D, v.getZ(), 40, 0.5D, 0.8D, 0.5D, 0.1D);
    }

    private static void end(ServerPlayer p, boolean fx) {
        if (MORPHS.remove(p.getUUID()) == null) {
            return;
        }
        send(p, "");
        if (fx) {
            wave(p.serverLevel(), p);
        }
    }

    private static void send(ServerPlayer p, String type) {
        FlashNetwork.CHANNEL.send(PacketDistributor.ALL.noArg(), new PlayerMorphPacket(p.getId(), type));
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || MORPHS.isEmpty()) {
            return;
        }
        for (Iterator<Map.Entry<UUID, Morph>> it = MORPHS.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Morph> e = it.next();
            ServerPlayer p = event.getServer().getPlayerList().getPlayer(e.getKey());
            if (p == null) {
                it.remove();
                continue;
            }
            if (e.getValue().until >= 0 && p.level().getGameTime() >= e.getValue().until) {
                it.remove();
                send(p, "");
                wave(p.serverLevel(), p);
            }
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            end(p, false);
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) {
            return;
        }
        // what the others are now, for the one who just came
        for (Map.Entry<UUID, Morph> e : MORPHS.entrySet()) {
            ServerPlayer o = p.server.getPlayerList().getPlayer(e.getKey());
            if (o != null) {
                FlashNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p),
                        new PlayerMorphPacket(o.getId(), e.getValue().type));
            }
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            end(p, false);
        }
    }

    @SubscribeEvent
    public static void onRespawnOrTravel(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && MORPHS.containsKey(p.getUUID())) {
            // a new entity id on the other side
            send(p, MORPHS.get(p.getUUID()).type);
        }
    }

    // an animal can't fight, build or use things

    @SubscribeEvent
    public static void onAttack(AttackEntityEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && morphed(p)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (event.getPlayer() instanceof ServerPlayer p && morphed(p)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onUse(PlayerInteractEvent.RightClickItem event) {
        if (event.getEntity() instanceof ServerPlayer p && morphed(p)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onUseBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getEntity() instanceof ServerPlayer p && morphed(p)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onUseEntity(PlayerInteractEvent.EntityInteract event) {
        if (event.getEntity() instanceof ServerPlayer p && morphed(p)) {
            event.setCanceled(true);
        }
    }

    static ResourceLocation id(String type) {
        return new ResourceLocation(type);
    }
}
