package com.flash.storm;

import com.flash.FlashMod;
import com.flash.network.FlashNetwork;
import com.flash.network.StormSyncPacket;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

/**
 * Storm (Ororo Munroe), the mistress of the weather (struck by lightning without powers, StormCalling; or
 * {@code /storm give}):
 * <ul>
 * <li>flies on the winds (a little slower than Superman at level 10), the flight key as the other heroes';</li>
 * <li>lightning cannot hurt her: it heals her; no fall damage; tougher than the Scarlet Witch;</li>
 * <li>her eyes turn white and lightning crawls on her while she flies or calls the storm (YasLayer);</li>
 * <li>four modes (StormAbilities): Lightning, Winds, Weather and Ice; her lightning is Man of Tomorrow's Shazam
 * bolt, its strikes leave its burn marks on the ground.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = FlashMod.MODID)
public final class StormPowers {

    public static final String KEY = "flash_storm";
    /** Damage taken: a bit tougher than the Scarlet Witch (~8 Iron Golem slaps). */
    public static final float DAMAGE_FACTOR = 0.18F;
    /** Strength of her storm (abilities = twelfths of a punch, as the other heroes'): Superman ~level 6. */
    public static final float POWER = 2.5F;

    private StormPowers() {
    }

    public static boolean has(Player p) {
        return p.getPersistentData().contains(KEY);
    }

    public static void grant(ServerPlayer p) {
        if (has(p)) {
            return;
        }
        for (com.flash.power.HeroPowers.Hero h : com.flash.power.HeroPowers.Hero.values()) {
            if (h.has(p)) {
                com.flash.power.HeroPowers.revokeOf(p, h);
            }
        }
        com.flash.scarlet.ScarletPowers.revoke(p);
        p.getPersistentData().put(KEY, new CompoundTag());
        sync(p);
    }

    public static void revoke(ServerPlayer p) {
        if (!has(p)) {
            return;
        }
        p.getPersistentData().remove(KEY);
        if (!p.isCreative() && !p.isSpectator()) {
            var ab = p.getAbilities();
            ab.mayfly = false;
            ab.flying = false;
            ab.setFlyingSpeed(0.05F);
            p.onUpdateAbilities();
        }
        sync(p);
    }

    public static void sync(ServerPlayer p) {
        FlashNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> p),
                new StormSyncPacket(p.getId(), has(p), has(p) && p.getAbilities().flying));
    }

    private static final java.util.Set<java.util.UUID> WAS_FLYING = new java.util.HashSet<>();

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer p)) {
            return;
        }
        boolean st = has(p);
        if (st && com.flash.power.HeroPowers.hasOther(p, com.flash.power.HeroPowers.Hero.STORM)) {
            revoke(p); // another power came after her
            st = false;
        }
        if (st) {
            flight(p);
        }
        boolean flying = st && p.getAbilities().flying;
        if (flying != WAS_FLYING.contains(p.getUUID())) {
            if (flying) {
                WAS_FLYING.add(p.getUUID());
            } else {
                WAS_FLYING.remove(p.getUUID());
            }
            sync(p);
        }
    }

    private static void flight(ServerPlayer p) {
        if (p.isCreative() || p.isSpectator()) {
            return;
        }
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

    /** Lightning feeds her: it does not hurt, it heals. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onAttack(LivingAttackEvent event) {
        if (event.getEntity() instanceof Player p && has(p) && event.getSource().is(DamageTypes.LIGHTNING_BOLT)) {
            event.setCanceled(true);
            p.heal(2.0F);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onHurt(LivingHurtEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && has(p)
                && !event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            event.setAmount(event.getAmount() * DAMAGE_FACTOR);
        }
    }

    @SubscribeEvent
    public static void onFall(LivingFallEvent event) {
        if (event.getEntity() instanceof Player p && has(p)) {
            event.setCanceled(true);
        }
    }

    /** The takeoff (flight key held on the ground): 0 the winds gather, 1 ~20 blocks up in a gust, 2 cancelled. */
    public static void launch(ServerPlayer p, int phase) {
        if (!has(p) || p.getAbilities().flying) {
            return;
        }
        ServerLevel level = p.serverLevel();
        if (phase == 0) {
            com.flash.superman.SupermanAbilities.anim(p, "charge_takeoff9");
            level.playSound(null, p.blockPosition(), SoundEvents.ELYTRA_FLYING, SoundSource.PLAYERS, 0.6F, 1.4F);
            return;
        }
        if (phase != 1) {
            com.flash.superman.SupermanAbilities.anim(p, "none");
            return;
        }
        flight(p);
        p.setDeltaMovement(p.getDeltaMovement().x, 2.0D, p.getDeltaMovement().z);
        p.hurtMarked = true;
        com.flash.superman.SupermanAbilities.anim(p, "charged_launch9");
        com.flash.superman.SupermanAbilities.groundPuff(level, p.position(), 3.5D, 40);
        level.sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 0.3D, p.getZ(), 20, 1.2D, 0.2D, 1.2D, 0.25D);
        StormAbilities.fx(p, com.flash.network.StormFxPacket.TAKEOFF, p.position(), p.position(), 1.0F);
        StormAbilities.fx(p, com.flash.network.StormFxPacket.ARC, p.position().add(0.0D, 1.0D, 0.0D),
                p.position().add(0.0D, -0.1D, 0.0D), 1.0F);
        level.playSound(null, p.blockPosition(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 0.8F, 1.6F);
        StormAbilities.casting(p);
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
                    new StormSyncPacket(target.getId(), true, target.getAbilities().flying));
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
}
