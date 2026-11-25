package com.flash.ultron;

import com.flash.FlashMod;
import com.flash.network.FlashNetwork;
import com.flash.network.HeroSyncPacket;
import com.flash.power.HeroPowers;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import org.joml.Vector3f;

/**
 * Ultron: the nanocore (UltronNanocoreItem) covers the player in Ultron's vibranium body, armour and all. It does not
 * come out with the syringe as the other powers do: only the surgery table (SurgeryTableBlock) takes it out, and then
 * the nanocore is given back. For now the nanocore is only had by command ({@code /ultron give}, {@code /give}).
 * <ul>
 * <li>a vibranium body: very tough, strong punches, heals, needs no air, no fall damage, fire does not burn it;</li>
 * <li>he flies (the flight key, as the other heroes);</li>
 * <li>three modes (UltronAbilities): Energy, the Legion, Control;</li>
 * <li>he lives in the network: killed, his body is rebuilt once every 10 minutes.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = FlashMod.MODID)
public final class UltronPowers {

    public static final String KEY = "flash_ultron";
    private static final String REBOOT = "reboot";
    public static final float DAMAGE_FACTOR = 0.14F;
    public static final float PUNCH_BONUS = 11.0F;
    private static final long REBOOT_TICKS = 20L * 60L * 10L;

    public static final int F_FLYING = 1;
    public static final int F_BEAM = 2;
    public static final int F_PRIME = 4;

    public static final Vector3f RED = new Vector3f(1.0F, 0.12F, 0.08F);

    private static final Map<UUID, Integer> SENT = new HashMap<>();

    private UltronPowers() {
    }

    public static boolean has(Player p) {
        return p.getPersistentData().contains(KEY);
    }

    /** The nanocore spreads over him. */
    public static void grant(ServerPlayer p) {
        if (has(p)) {
            return;
        }
        for (HeroPowers.Hero h : HeroPowers.Hero.values()) {
            if (h != HeroPowers.Hero.ULTRON && h.has(p)) {
                HeroPowers.revokeOf(p, h);
            }
        }
        p.getPersistentData().put(KEY, new CompoundTag());
        ServerLevel level = p.serverLevel();
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.NETHERITE_BLOCK_PLACE, SoundSource.PLAYERS,
                1.2F, 0.6F);
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS,
                1.0F, 0.5F);
        level.sendParticles(new DustParticleOptions(new Vector3f(0.75F, 0.78F, 0.84F), 1.2F), p.getX(),
                p.getY() + 1.0D, p.getZ(), 120, 0.4D, 0.9D, 0.4D, 0.0D);
        level.sendParticles(new DustParticleOptions(RED, 1.0F), p.getX(), p.getY() + 1.4D, p.getZ(), 40, 0.3D, 0.4D,
                0.3D, 0.0D);
        sync(p);
    }

    /**
     * The body taken off (the surgery, or another power that came after): the nanocore goes back to him, so it is
     * never lost.
     */
    public static void revoke(ServerPlayer p) {
        if (!has(p)) {
            return;
        }
        p.getPersistentData().remove(KEY);
        UltronAbilities.forget(p);
        if (!p.isCreative() && !p.isSpectator()) {
            var ab = p.getAbilities();
            ab.mayfly = false;
            ab.flying = false;
            ab.setFlyingSpeed(0.05F);
            p.onUpdateAbilities();
        }
        ItemStack core = new ItemStack(UltronRegistry.NANOCORE.get());
        if (!p.getInventory().add(core)) {
            p.drop(core, false);
        }
        sync(p);
    }

    public static int data(ServerPlayer p) {
        if (!has(p)) {
            return 0;
        }
        return (p.getAbilities().flying ? F_FLYING : 0) | (UltronAbilities.beaming(p) ? F_BEAM : 0)
                | (UltronAbilities.prime(p) ? F_PRIME : 0);
    }

    public static void sync(ServerPlayer p) {
        int d = data(p);
        SENT.put(p.getUUID(), d);
        FlashNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> p),
                new HeroSyncPacket(HeroSyncPacket.ULTRON, p.getId(), has(p), d));
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
        if (HeroPowers.hasOther(p, HeroPowers.Hero.ULTRON)) {
            revoke(p); // another power came after him
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
        // a machine: no air needed, fire does not take, the vibranium mends itself
        p.setAirSupply(p.getMaxAirSupply());
        if (p.isOnFire()) {
            p.clearFire();
        }
        if (p.tickCount % 30 == 0 && p.getHealth() < p.getMaxHealth() && p.isAlive()) {
            p.heal(1.0F);
        }
        Integer sent = SENT.get(p.getUUID());
        if (sent == null || sent != data(p)) {
            sync(p);
        }
    }

    /** The takeoff (flight key held on the ground): 0 he crouches, 1 up he goes, 2 cancelled. */
    public static void launch(ServerPlayer p, int phase) {
        if (!has(p) || p.getAbilities().flying) {
            return;
        }
        if (phase == 0) {
            com.flash.superman.SupermanAbilities.anim(p, "charge_takeoff9");
            return;
        }
        if (phase != 1) {
            com.flash.superman.SupermanAbilities.anim(p, "none");
            return;
        }
        ServerLevel level = p.serverLevel();
        p.setDeltaMovement(p.getDeltaMovement().x, 2.0D, p.getDeltaMovement().z);
        p.hurtMarked = true;
        com.flash.superman.SupermanAbilities.anim(p, "charged_launch9");
        com.flash.superman.SupermanAbilities.groundPuff(level, p.position(), 3.0D, 30);
        level.playSound(null, p.blockPosition(), SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.PLAYERS, 1.2F, 0.5F);
        level.sendParticles(new DustParticleOptions(RED, 1.4F), p.getX(), p.getY(), p.getZ(), 30, 0.3D, 0.1D, 0.3D,
                0.0D);
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
            if (event.getSource().is(DamageTypeTags.IS_FIRE) || event.getSource().is(DamageTypeTags.IS_DROWNING)) {
                event.setCanceled(true);
                return;
            }
            // (resistance: the Lanterns', HeroBalance)
        }
    }

    @SubscribeEvent
    public static void onFall(LivingFallEvent event) {
        if (event.getEntity() instanceof Player p && has(p)) {
            event.setCanceled(true);
        }
    }

    /** He lives in the network: once every 10 minutes a deadly blow only breaks the body, which is rebuilt. */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || !has(p) || event.isCanceled()
                || event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return;
        }
        long now = p.serverLevel().getGameTime();
        CompoundTag tag = p.getPersistentData().getCompound(KEY);
        if (tag.getLong(REBOOT) > now) {
            return;
        }
        event.setCanceled(true);
        tag.putLong(REBOOT, now + REBOOT_TICKS);
        p.getPersistentData().put(KEY, tag);
        p.setHealth(p.getMaxHealth() * 0.5F);
        p.removeAllEffects();
        p.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE, 100, 4, false, false));
        ServerLevel level = p.serverLevel();
        level.sendParticles(ParticleTypes.EXPLOSION, p.getX(), p.getY() + 1.0D, p.getZ(), 3, 0.4D, 0.6D, 0.4D, 0.0D);
        level.sendParticles(new DustParticleOptions(RED, 1.5F), p.getX(), p.getY() + 1.0D, p.getZ(), 80, 0.6D, 1.0D,
                0.6D, 0.0D);
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS,
                1.4F, 0.5F);
        p.displayClientMessage(Component.translatable("message.flash.ultron.reboot").withStyle(ChatFormatting.RED),
                false);
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
                    new HeroSyncPacket(HeroSyncPacket.ULTRON, target.getId(), true, data(target)));
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
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            sync(p);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        SENT.remove(event.getEntity().getUUID());
    }
}
