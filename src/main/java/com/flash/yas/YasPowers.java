package com.flash.yas;

import com.flash.FlashMod;
import com.flash.network.FlashNetwork;
import com.flash.network.YasSyncPacket;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

/**
 * Yas, the hero of the transcendence: the powers of Hand of God (YasHandOfGod), the body crossed by white and
 * yellow lightning, white eyes. Only got by transcending on the floating island (YasTranscendence) or by the
 * {@code /yas} command. The power is bound to whoever transcended: he can take it out with a syringe and put it
 * back, nobody else can take a Yas capsule (the mark {@link #TRANSCENDED} stays with him for good).
 */
@Mod.EventBusSubscriber(modid = FlashMod.MODID)
public final class YasPowers {

    /** The power itself (as the other heroes: a compound in the player data). */
    public static final String KEY = "flash_yas";
    /** Transcended once: the UUID of the player is the only owner of his Yas essence. */
    public static final String TRANSCENDED = "flash_yas_transcended";

    private YasPowers() {
    }

    public static boolean has(Player p) {
        return p.getPersistentData().contains(KEY);
    }

    public static boolean transcended(Player p) {
        return p.getPersistentData().getBoolean(TRANSCENDED);
    }

    /** Becomes Yas (the other powers go, one power at a time). */
    public static void grant(ServerPlayer p) {
        if (has(p)) {
            return;
        }
        clearOthers(p);
        CompoundTag tag = new CompoundTag();
        tag.putInt("level", 1);
        tag.putString("owner", p.getUUID().toString());
        p.getPersistentData().put(KEY, tag);
        p.getPersistentData().putBoolean(TRANSCENDED, true);
        YasHandOfGod.apply(p, true);
        sync(p);
        com.flash.power.HeroAdvancements.award(p, com.flash.power.HeroAdvancements.YAS);
    }

    public static void revoke(ServerPlayer p) {
        if (!has(p)) {
            return;
        }
        p.getPersistentData().remove(KEY);
        stopFlight(p);
        YasHandOfGod.apply(p, false);
        sync(p);
    }

    /** The powers of the other heroes go (the same rule as theirs). */
    private static void clearOthers(ServerPlayer p) {
        for (com.flash.power.HeroPowers.Hero h : com.flash.power.HeroPowers.Hero.values()) {
            if (h != com.flash.power.HeroPowers.Hero.YAS && h.has(p)) {
                com.flash.power.HeroPowers.revokeOf(p, h);
            }
        }
    }

    public static void sync(ServerPlayer p) {
        YasSyncPacket packet = new YasSyncPacket(p.getId(), has(p), has(p) && p.getAbilities().flying);
        FlashNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> p), packet);
    }

    private static final java.util.Set<java.util.UUID> WAS_FLYING = new java.util.HashSet<>();

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer p)) {
            return;
        }
        YasHandOfGod.guard(p, has(p));
        boolean yas = has(p);
        if (yas) {
            flight(p);
        }
        boolean flying = yas && p.getAbilities().flying;
        if (flying != WAS_FLYING.contains(p.getUUID())) {
            if (flying) {
                WAS_FLYING.add(p.getUUID());
            } else {
                WAS_FLYING.remove(p.getUUID());
            }
            sync(p);
        }
    }

    /**
     * Yas flies (double jump, as the other heroes), steered on the client by the mod's aimed flight
     * (SupermanClient), faster than Superman at level 10.
     */
    private static void flight(ServerPlayer p) {
        if (p.isCreative() || p.isSpectator()) {
            return;
        }
        var ab = p.getAbilities();
        boolean changed = false;
        if (!ab.mayfly) {
            ab.mayfly = true;
            changed = true;
        }
        if (ab.getFlyingSpeed() != 0.0F) {
            ab.setFlyingSpeed(0.0F);
            changed = true;
        }
        if (changed) {
            p.onUpdateAbilities();
        }
        p.fallDistance = 0.0F;
    }

    /** Yas never takes fall damage. */
    @SubscribeEvent
    public static void onFall(net.minecraftforge.event.entity.living.LivingFallEvent event) {
        if (event.getEntity() instanceof Player p && has(p)) {
            event.setCanceled(true);
        }
    }

    /** Back on the ground when the power goes. */
    private static void stopFlight(ServerPlayer p) {
        if (p.isCreative() || p.isSpectator()) {
            return;
        }
        var ab = p.getAbilities();
        ab.mayfly = false;
        ab.flying = false;
        ab.setFlyingSpeed(0.05F);
        p.onUpdateAbilities();
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            sync(p);
            YasHandOfGod.apply(p, has(p));
        }
    }

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof ServerPlayer target && event.getEntity() instanceof ServerPlayer viewer
                && has(target)) {
            FlashNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> viewer),
                    new YasSyncPacket(target.getId(), true, target.getAbilities().flying));
        }
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        CompoundTag old = event.getOriginal().getPersistentData();
        CompoundTag now = event.getEntity().getPersistentData();
        if (old.contains(KEY)) {
            now.put(KEY, old.getCompound(KEY).copy());
        }
        if (old.getBoolean(TRANSCENDED)) {
            now.putBoolean(TRANSCENDED, true);
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            sync(p);
        }
    }

    public static Component name() {
        return Component.translatable("hero.flash.yas");
    }
}
