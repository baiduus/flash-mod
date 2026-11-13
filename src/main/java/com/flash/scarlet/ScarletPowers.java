package com.flash.scarlet;

import com.flash.FlashMod;
import com.flash.network.FlashNetwork;
import com.flash.network.ScarletSyncPacket;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;
import org.joml.Vector3f;

/**
 * The Scarlet Witch (only by the {@code /scarlet} command for now): the chaos magic.
 * <ul>
 * <li>flies as fast as Superman, wrapped in the red aura of Hand of God's telekinesis (ScarletLayer); the flight key
 * held on the ground: a red takeoff of ~20 blocks;</li>
 * <li>a little tougher than the Flash: about six slaps of an Iron Golem kill her;</li>
 * <li>her modes (YasAbilities / ScarletClient): Manipulation of Reality and Telekinesis (Hand of God's, without the
 * implosion and with a dismemberment of only five seconds), Telepathy and Mind Control, Energy Projection, Necromancy
 * and Resurrection (ScarletAbilities).</li>
 * </ul>
 * One power at a time: she loses the others (and Yas) when she becomes the Scarlet Witch, and the Scarlet Witch goes
 * when another power comes.
 */
@Mod.EventBusSubscriber(modid = FlashMod.MODID)
public final class ScarletPowers {

    public static final String KEY = "flash_scarlet";
    /** Red of the chaos magic (particles). */
    public static final DustParticleOptions RED = new DustParticleOptions(new Vector3f(0.92F, 0.06F, 0.16F), 1.6F);
    public static final DustParticleOptions DARK = new DustParticleOptions(new Vector3f(0.55F, 0.0F, 0.12F), 2.2F);

    private ScarletPowers() {
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
        if (com.flash.yas.YasPowers.has(p)) {
            com.flash.yas.YasPowers.revoke(p);
        }
        p.getPersistentData().put(KEY, new CompoundTag());
        com.flash.yas.YasHandOfGod.apply(p, true);
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
        if (!com.flash.yas.YasPowers.has(p)) {
            com.flash.yas.YasHandOfGod.apply(p, false);
        }
        ScarletThralls.release(p);
        sync(p);
    }

    public static void sync(ServerPlayer p) {
        FlashNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> p),
                new ScarletSyncPacket(p.getId(), has(p), has(p) && p.getAbilities().flying));
    }

    private static final java.util.Set<java.util.UUID> WAS_FLYING = new java.util.HashSet<>();

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer p)) {
            return;
        }
        boolean sw = has(p);
        if (sw && (com.flash.power.HeroPowers.hasAny(p) || com.flash.yas.YasPowers.has(p))) {
            // another power came after her: it wins
            revoke(p);
            sw = false;
        }
        if (sw) {
            flight(p);
            if (p.getAbilities().flying && p.tickCount % 2 == 0) {
                p.serverLevel().sendParticles(RED, p.getX(), p.getY() + 0.9D, p.getZ(), 2, 0.3D, 0.5D, 0.3D, 0.0D);
            }
        }
        boolean flying = sw && p.getAbilities().flying;
        if (flying != WAS_FLYING.contains(p.getUUID())) {
            if (flying) {
                WAS_FLYING.add(p.getUUID());
            } else {
                WAS_FLYING.remove(p.getUUID());
            }
            sync(p);
        }
    }

    /** She flies (double jump or the flight key), steered by the mod's aimed flight at Superman's speed. */
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
    }

    /**
     * Damage taken: an Iron Golem slap does 7.5 to 21.5 (about 14.5): six of them, about 87, kill her (20 hearts of
     * life): 20 / 87.
     */
    public static final float DAMAGE_FACTOR = 0.23F;

    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOW)
    public static void onHurt(net.minecraftforge.event.entity.living.LivingHurtEvent event) {
        if (event.getEntity() instanceof ServerPlayer victim && has(victim)
                && !event.getSource().is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            event.setAmount(event.getAmount() * DAMAGE_FACTOR);
        }
    }

    /** She floats down: no fall damage. */
    @SubscribeEvent
    public static void onFall(net.minecraftforge.event.entity.living.LivingFallEvent event) {
        if (event.getEntity() instanceof Player p && has(p)) {
            event.setCanceled(true);
        }
    }

    /** The red takeoff (flight key held on the ground): 0 gathers, 1 shoots ~20 blocks up, 2 cancels. */
    public static void launch(ServerPlayer p, int phase) {
        if (!has(p) || p.getAbilities().flying) {
            return;
        }
        ServerLevel level = p.serverLevel();
        if (phase == 0) {
            com.flash.superman.SupermanAbilities.anim(p, "charge_takeoff9");
            play(level, p.position(), "telecinese_tensao", SoundEvents.BEACON_POWER_SELECT, 1.2F);
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
        // a ring of red energy and a column of it
        for (int i = 0; i < 48; i++) {
            double a = i * Math.PI * 2.0D / 48.0D;
            level.sendParticles(RED, p.getX() + Math.cos(a) * 2.2D, p.getY() + 0.1D, p.getZ() + Math.sin(a) * 2.2D,
                    1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
        level.sendParticles(energy(), p.getX(), p.getY() + 0.8D, p.getZ(), 50, 0.5D, 0.8D, 0.5D, 0.2D);
        level.sendParticles(DARK, p.getX(), p.getY() + 0.5D, p.getZ(), 40, 0.6D, 1.2D, 0.6D, 0.0D);
        play(level, p.position(), "telecinese_arremesso", SoundEvents.ILLUSIONER_CAST_SPELL, 0.8F);
    }

    /** Hand of God's telekinesis particle (red energy), or a red dust without it. */
    public static ParticleOptions energy() {
        var t = ForgeRegistries.PARTICLE_TYPES.getValue(new ResourceLocation("hand_of_god", "telecinese"));
        return t instanceof ParticleOptions o ? o : RED;
    }

    public static void play(ServerLevel level, Vec3 at, String hog, SoundEvent fallback, float pitch) {
        SoundEvent s = ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("hand_of_god", hog));
        level.playSound(null, at.x, at.y, at.z, s != null ? s : fallback, SoundSource.PLAYERS, 1.4F,
                s != null ? 1.0F : pitch);
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            sync(p);
            if (has(p)) {
                com.flash.yas.YasHandOfGod.apply(p, true);
            }
        }
    }

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof ServerPlayer target && event.getEntity() instanceof ServerPlayer viewer
                && has(target)) {
            FlashNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> viewer),
                    new ScarletSyncPacket(target.getId(), true, target.getAbilities().flying));
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

    public static Component name() {
        return Component.translatable("hero.flash.scarlet");
    }
}
