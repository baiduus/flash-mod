package com.flash.doom;

import com.flash.FlashMod;
import com.flash.network.FlashNetwork;
import com.flash.network.HeroSyncPacket;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
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
 * Doctor Doom (Victor von Doom), sorcerer and scientist in his armour: a player with no power wearing a full
 * netherite armour who crouches and uses an enchanting table reads the grimoire of Latveria and is asked whether he
 * wants the power (DoomCalling); or {@code /doom give}, or his capsule.
 * <ul>
 * <li>his armour: very tough (fire cannot burn him, explosions hurt little), heavy gauntlets, no fall damage,
 * repair systems that heal him;</li>
 * <li>flies on the boot thrusters of his armour (the flight key, as the other heroes');</li>
 * <li>three modes (DoomAbilities): Gauntlets (bolts, a beam, a discharge), Force Field (a shield, a wave) and
 * Sorcery (a mystic bolt that seeks its prey, a ritual circle that erupts);</li>
 * <li>Legends: Superheroes' Doctor Doom armour, hood and cape.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = FlashMod.MODID)
public final class DoomPowers {

    public static final String KEY = "flash_doom";
    /** Damage taken in his armour: tougher than Storm. */
    public static final float DAMAGE_FACTOR = 0.14F;
    /** Taken through the force field. */
    public static final float SHIELD_FACTOR = 0.15F;
    public static final float PUNCH_BONUS = 5.0F;

    public static final int F_FLYING = 1;
    public static final int F_SHIELD = 2;

    private static final java.util.Map<java.util.UUID, Integer> SENT = new java.util.HashMap<>();

    private DoomPowers() {
    }

    public static boolean has(Player p) {
        return p.getPersistentData().contains(KEY);
    }

    public static void grant(ServerPlayer p) {
        if (has(p)) {
            return;
        }
        for (com.flash.power.HeroPowers.Hero h : com.flash.power.HeroPowers.Hero.values()) {
            if (h != com.flash.power.HeroPowers.Hero.DOOM && h.has(p)) {
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
        DoomAbilities.forget(p);
        if (!p.isCreative() && !p.isSpectator()) {
            var ab = p.getAbilities();
            ab.mayfly = false;
            ab.flying = false;
            ab.setFlyingSpeed(0.05F);
            p.onUpdateAbilities();
        }
        sync(p);
    }

    public static int flags(ServerPlayer p) {
        if (!has(p)) {
            return 0;
        }
        return (p.getAbilities().flying ? F_FLYING : 0) | (DoomAbilities.shielded(p) ? F_SHIELD : 0);
    }

    public static void sync(ServerPlayer p) {
        int f = flags(p);
        SENT.put(p.getUUID(), f);
        FlashNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> p),
                new HeroSyncPacket(HeroSyncPacket.DOOM, p.getId(), has(p), f));
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
        if (com.flash.power.HeroPowers.hasOther(p, com.flash.power.HeroPowers.Hero.DOOM)) {
            revoke(p); // another power came after him
            return;
        }
        flight(p);
        p.clearFire();
        // the armour's repair systems
        if (p.tickCount % 40 == 0 && p.getHealth() < p.getMaxHealth() && p.isAlive()) {
            p.heal(1.0F);
        }
        Integer sent = SENT.get(p.getUUID());
        if (sent == null || sent != flags(p)) {
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

    /** The takeoff (flight key held on the ground): 0 the thrusters warm up, 1 up in a blast, 2 cancelled. */
    public static void launch(ServerPlayer p, int phase) {
        if (!has(p) || p.getAbilities().flying) {
            return;
        }
        ServerLevel level = p.serverLevel();
        if (phase == 0) {
            com.flash.superman.SupermanAbilities.anim(p, "charge_takeoff9");
            level.playSound(null, p.blockPosition(), SoundEvents.FURNACE_FIRE_CRACKLE, SoundSource.PLAYERS, 1.0F, 0.6F);
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
        com.flash.superman.SupermanAbilities.groundPuff(level, p.position(), 3.0D, 30);
        level.sendParticles(ParticleTypes.FLAME, p.getX(), p.getY() + 0.1D, p.getZ(), 40, 0.6D, 0.1D, 0.6D, 0.15D);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, p.getX(), p.getY() + 0.1D, p.getZ(), 20, 0.8D, 0.1D, 0.8D,
                0.05D);
        level.playSound(null, p.blockPosition(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 1.2F, 0.6F);
        level.playSound(null, p.blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.5F, 1.6F);
        DoomAbilities.fx(p, DoomAbilities.FX_TAKEOFF, p.position(), p.position(), 1.0F, -1);
    }

    /** Fire and lava cannot burn him in his armour. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onAttack(LivingAttackEvent event) {
        if (event.getEntity() instanceof Player p && has(p) && event.getSource().is(DamageTypeTags.IS_FIRE)) {
            event.setCanceled(true);
        }
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
        if (!(event.getEntity() instanceof ServerPlayer p) || !has(p)
                || event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return;
        }
        float f = DAMAGE_FACTOR;
        if (event.getSource().is(DamageTypeTags.IS_EXPLOSION)) {
            f *= 0.5F;
        }
        if (DoomAbilities.shielded(p)) {
            f *= SHIELD_FACTOR;
            DoomAbilities.shieldHit(p);
        }
        event.setAmount(event.getAmount() * f);
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
                    new HeroSyncPacket(HeroSyncPacket.DOOM, target.getId(), true, flags(target)));
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
