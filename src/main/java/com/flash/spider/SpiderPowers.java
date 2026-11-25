package com.flash.spider;

import com.flash.FlashMod;
import com.flash.network.FlashNetwork;
import com.flash.network.HeroSyncPacket;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
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
 * Spider-Man (Peter Parker): bitten by a cave spider without powers, he is asked whether he wants the power
 * (SpiderCalling); or {@code /spiderman give}, or his capsule.
 * <ul>
 * <li>the strength, speed and agility of a spider: stronger punches, a higher jump, quicker on his feet, no fall
 * damage, a healing factor;</li>
 * <li>he climbs walls and clings to them, and swings on his webs with the flight key (SpiderClient);</li>
 * <li>his spider-sense warns him of whatever comes for him (and he dodges some of it, SpiderAbilities);</li>
 * <li>three modes of his own (Webs, Pull, Combat: SpiderAbilities); his suit is chosen in the K menu
 * (Legends: Superheroes' Spider-Man suits).</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = FlashMod.MODID)
public final class SpiderPowers {

    public static final String KEY = "flash_spider";
    private static final String SUIT = "suit";
    private static final String UNSUITED = "unsuited";
    /** In the sync: the suit taken off (his powers stay). */
    public static final int UNSUITED_BIT = 1 << 8;
    /** Damage taken: tougher than a man, far from Superman (about the Scarlet Witch's). */
    public static final float DAMAGE_FACTOR = 0.3F;
    /** Added to his punches (a spider's strength: he lifts tons). */
    public static final float PUNCH_BONUS = 7.0F;

    private SpiderPowers() {
    }

    public static boolean has(Player p) {
        return p.getPersistentData().contains(KEY);
    }

    public static void grant(ServerPlayer p) {
        if (has(p)) {
            return;
        }
        for (com.flash.power.HeroPowers.Hero h : com.flash.power.HeroPowers.Hero.values()) {
            if (h != com.flash.power.HeroPowers.Hero.SPIDER && h.has(p)) {
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
        if (!p.isCreative() && !p.isSpectator()) {
            var ab = p.getAbilities();
            ab.mayfly = false;
            ab.flying = false;
            p.onUpdateAbilities();
        }
        p.removeEffect(MobEffects.JUMP);
        p.removeEffect(MobEffects.MOVEMENT_SPEED);
        sync(p);
    }

    public static int suit(Player p) {
        return has(p) ? SpiderSuits.clamp(p.getPersistentData().getCompound(KEY).getInt(SUIT)) : 0;
    }

    public static void setSuit(ServerPlayer p, int index) {
        if (!has(p)) {
            return;
        }
        CompoundTag tag = p.getPersistentData().getCompound(KEY);
        tag.putInt(SUIT, SpiderSuits.clamp(index));
        p.getPersistentData().put(KEY, tag);
        sync(p);
    }

    public static boolean suited(Player p) {
        return has(p) && !p.getPersistentData().getCompound(KEY).getBoolean(UNSUITED);
    }

    /** The suit key: the suit on or off (his powers stay). */
    public static void toggleSuited(ServerPlayer p) {
        if (!has(p)) {
            return;
        }
        CompoundTag tag = p.getPersistentData().getCompound(KEY);
        tag.putBoolean(UNSUITED, !tag.getBoolean(UNSUITED));
        p.getPersistentData().put(KEY, tag);
        p.serverLevel().playSound(null, p.getX(), p.getY(), p.getZ(), net.minecraft.sounds.SoundEvents.ARMOR_EQUIP_LEATHER,
                net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 1.0F);
        sync(p);
    }

    private static int data(Player p) {
        return suit(p) | (suited(p) ? 0 : UNSUITED_BIT);
    }

    public static void sync(ServerPlayer p) {
        FlashNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> p),
                new HeroSyncPacket(HeroSyncPacket.SPIDER, p.getId(), has(p), data(p)));
    }

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer p) || !has(p)) {
            return;
        }
        if (com.flash.power.HeroPowers.hasOther(p, com.flash.power.HeroPowers.Hero.SPIDER)) {
            revoke(p); // another power came after him
            return;
        }
        if (!p.isCreative() && !p.isSpectator()) {
            // "may fly" only so the long swings are not taken for cheating; he never flies
            var ab = p.getAbilities();
            if (!ab.mayfly || ab.flying) {
                ab.mayfly = true;
                ab.flying = false;
                p.onUpdateAbilities();
            }
        }
        if (p.tickCount % 20 == 0) {
            // a spider's legs: a higher jump, quicker on his feet
            p.addEffect(new MobEffectInstance(MobEffects.JUMP, 60, 1, true, false, false));
            p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 60, 0, true, false, false));
        }
        // the healing factor
        if (p.tickCount % 30 == 0 && p.getHealth() < p.getMaxHealth() && p.isAlive()) {
            p.heal(1.0F);
        }
    }

    /** His punches carry a spider's strength (before the heroes' balance scales them). */
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

    /** He always lands on his feet. */
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
                    new HeroSyncPacket(HeroSyncPacket.SPIDER, target.getId(), true, data(target)));
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
    public static void onDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            sync(p);
        }
    }
}
