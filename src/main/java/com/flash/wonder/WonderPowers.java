package com.flash.wonder;

import com.flash.FlashMod;
import com.flash.network.FlashNetwork;
import com.flash.network.HeroSyncPacket;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

/**
 * Wonder Woman (Diana of Themyscira), demigoddess and Amazon warrior: a player with no power, a sword in the hand
 * and a shield in the other, who brings down a Ravager is asked by the Amazons whether she wants the power
 * (WonderCalling); or {@code /wonderwoman give}, or her capsule.
 * <ul>
 * <li>a demigoddess' body: great strength, very tough (between Doom and Superman), heals, no fall damage; her
 * bracelets turn away some of the arrows that reach her;</li>
 * <li>she flies (the flight key, as the other heroes');</li>
 * <li>three modes (WonderAbilities): the Lasso of Truth, the Bracelets of Submission, the Warrior (her shield, the
 * Amazonian Cataclysm);</li>
 * <li>Legends: Superheroes' Wonder Woman (with or without the tiara, picked in the K menu), her shield on her back
 * and her lasso at her hip; the suit key takes the suit off (her powers stay).</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = FlashMod.MODID)
public final class WonderPowers {

    public static final String KEY = "flash_wonder";
    private static final String SUIT = "suit";
    private static final String UNSUITED = "unsuited";
    public static final float DAMAGE_FACTOR = 0.11F;
    public static final float PUNCH_BONUS = 12.0F;

    /** In the sync (HeroSyncPacket data): bits over her suit's index. */
    public static final int F_UNSUITED = 1 << 8;
    public static final int F_FLYING = 1 << 9;
    public static final int F_GUARD = 1 << 10;

    public static final String[] SUITS = {"wonder_woman", "wonder_woman_no_tiara"};

    private static final java.util.Map<java.util.UUID, Integer> SENT = new java.util.HashMap<>();

    private WonderPowers() {
    }

    public static boolean has(Player p) {
        return p.getPersistentData().contains(KEY);
    }

    public static void grant(ServerPlayer p) {
        if (has(p)) {
            return;
        }
        for (com.flash.power.HeroPowers.Hero h : com.flash.power.HeroPowers.Hero.values()) {
            if (h != com.flash.power.HeroPowers.Hero.WONDER && h.has(p)) {
                com.flash.power.HeroPowers.revokeOf(p, h);
            }
        }
        p.getPersistentData().put(KEY, new CompoundTag());
        sync(p);
    }

    public static void revoke(ServerPlayer p) {
        if (!has(p)) {
            return;
        }
        p.getPersistentData().remove(KEY);
        WonderAbilities.forget(p);
        if (!p.isCreative() && !p.isSpectator()) {
            var ab = p.getAbilities();
            ab.mayfly = false;
            ab.flying = false;
            ab.setFlyingSpeed(0.05F);
            p.onUpdateAbilities();
        }
        sync(p);
    }

    public static int suit(Player p) {
        int s = p.getPersistentData().getCompound(KEY).getInt(SUIT);
        return s < 0 || s >= SUITS.length ? 0 : s;
    }

    public static boolean suited(Player p) {
        return has(p) && !p.getPersistentData().getCompound(KEY).getBoolean(UNSUITED);
    }

    public static void setSuit(ServerPlayer p, int index) {
        if (!has(p)) {
            return;
        }
        CompoundTag tag = p.getPersistentData().getCompound(KEY);
        tag.putInt(SUIT, Math.floorMod(index, SUITS.length));
        p.getPersistentData().put(KEY, tag);
        sync(p);
    }

    /** The suit key: the suit on or off (her powers stay). */
    public static void toggleSuited(ServerPlayer p) {
        if (!has(p)) {
            return;
        }
        CompoundTag tag = p.getPersistentData().getCompound(KEY);
        tag.putBoolean(UNSUITED, !tag.getBoolean(UNSUITED));
        p.getPersistentData().put(KEY, tag);
        p.serverLevel().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ARMOR_EQUIP_GOLD,
                SoundSource.PLAYERS, 1.0F, 1.0F);
        sync(p);
    }

    public static int data(ServerPlayer p) {
        if (!has(p)) {
            return 0;
        }
        return suit(p) | (suited(p) ? 0 : F_UNSUITED) | (p.getAbilities().flying ? F_FLYING : 0)
                | (WonderAbilities.guarding(p) ? F_GUARD : 0);
    }

    public static void sync(ServerPlayer p) {
        int d = data(p);
        SENT.put(p.getUUID(), d);
        FlashNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> p),
                new HeroSyncPacket(HeroSyncPacket.WONDER, p.getId(), has(p), d));
    }

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer p)) {
            return;
        }
        if (!has(p)) {
            if (SENT.remove(p.getUUID()) != null) {
                sync(p);
            }
            return;
        }
        if (com.flash.power.HeroPowers.hasOther(p, com.flash.power.HeroPowers.Hero.WONDER)) {
            revoke(p); // another power came after her
            return;
        }
        if (!p.isCreative() && !p.isSpectator()) {
            var ab = p.getAbilities();
            if (!ab.mayfly || ab.getFlyingSpeed() != 0.0F) {
                ab.mayfly = true;
                ab.setFlyingSpeed(0.0F);
                p.onUpdateAbilities();
            }
            if (ab.flying) {
                p.fallDistance = 0.0F;
            }
        }
        // a demigoddess heals fast
        if (p.tickCount % 25 == 0 && p.getHealth() < p.getMaxHealth() && p.isAlive()) {
            p.heal(1.0F);
        }
        Integer sent = SENT.get(p.getUUID());
        if (sent == null || sent != data(p)) {
            sync(p);
        }
    }

    /** The takeoff (flight key held on the ground): 0 she crouches, 1 up she goes, 2 cancelled. */
    public static void launch(ServerPlayer p, int phase) {
        if (!has(p) || p.getAbilities().flying) {
            return;
        }
        ServerLevel level = p.serverLevel();
        if (phase == 0) {
            com.flash.superman.SupermanAbilities.anim(p, "charge_takeoff9");
            return;
        }
        if (phase != 1) {
            com.flash.superman.SupermanAbilities.anim(p, "none");
            return;
        }
        p.setDeltaMovement(p.getDeltaMovement().x, 2.0D, p.getDeltaMovement().z);
        p.hurtMarked = true;
        com.flash.superman.SupermanAbilities.anim(p, "charged_launch9");
        com.flash.superman.SupermanAbilities.groundPuff(level, p.position(), 3.0D, 30);
        level.playSound(null, p.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.2F, 0.6F);
        WonderAbilities.fx(p, WonderAbilities.FX_TAKEOFF, p.position(), p.position(), 1.0F, -1);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPunch(LivingHurtEvent event) {
        if (event.getSource().getDirectEntity() instanceof ServerPlayer p && has(p)
                && event.getSource().getEntity() == p && event.getSource().is(net.minecraft.world.damagesource
                .DamageTypes.PLAYER_ATTACK)) {
            event.setAmount(event.getAmount() + PUNCH_BONUS);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onHurt(LivingHurtEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && has(p)
                && !event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            // (resistance: the Lanterns', HeroBalance)
        }
    }

    @SubscribeEvent
    public static void onFall(LivingFallEvent event) {
        if (event.getEntity() instanceof Player p && has(p)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            sync(p);
        }
    }

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof ServerPlayer target && event.getEntity() instanceof ServerPlayer viewer
                && has(target)) {
            FlashNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> viewer),
                    new HeroSyncPacket(HeroSyncPacket.WONDER, target.getId(), true, data(target)));
        }
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        CompoundTag old = event.getOriginal().getPersistentData();
        if (old.contains(KEY)) {
            event.getEntity().getPersistentData().put(KEY, old.getCompound(KEY).copy());
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            sync(p);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        SENT.remove(event.getEntity().getUUID());
    }
}
