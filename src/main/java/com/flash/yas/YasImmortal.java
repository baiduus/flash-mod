package com.flash.yas;

import com.flash.FlashMod;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Yas, above everything (the other heroes are on the Green Lanterns' scale, HeroBalance):
 * <ul>
 * <li>nothing hurts her: no hit lands, whatever it is (monsters, heroes, the ring, the void, /kill); should anything
 * still get to her life, she never dies; fallen into the void, she is put back on the ground above it;</li>
 * <li>every hit of hers takes off at least 1000 (BRUTAL), through bubbles, armour and every defence;</li>
 * <li>the Lanterns' ring forces its damage through every other mod's defence (its DanoAnel): not through the
 * heroes' of this mod: their own defences hold against the ring as against anything.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = FlashMod.MODID)
public final class YasImmortal {

    private YasImmortal() {
    }

    // ------------------------------------------------------------------
    // Immortal
    // ------------------------------------------------------------------

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && YasPowers.has(p)) {
            event.setCanceled(true);
            p.setHealth(p.getMaxHealth());
            p.deathTime = 0;
            p.clearFire();
            p.invulnerableTime = 20;
        }
    }

    /** Fallen through the void: back on the highest ground of that spot (or the world's spawn), unhurt. */
    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer p) || !YasPowers.has(p)) {
            return;
        }
        if (p.getHealth() <= 0.0F && !p.isRemoved()) {
            p.setHealth(p.getMaxHealth());
            p.deathTime = 0;
        }
        ServerLevel level = p.serverLevel();
        if (p.getY() < level.getMinBuildHeight() - 24) {
            int x = p.getBlockX();
            int z = p.getBlockZ();
            int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
            double y;
            if (top > level.getMinBuildHeight()) {
                y = top + 1.0D;
            } else {
                var spawn = level.getSharedSpawnPos();
                x = spawn.getX();
                z = spawn.getZ();
                y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) + 1.0D;
            }
            p.teleportTo(level, x + 0.5D, y, z + 0.5D, p.getYRot(), p.getXRot());
            p.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            p.fallDistance = 0.0F;
            p.setHealth(p.getMaxHealth());
        }
    }

    // ------------------------------------------------------------------
    // The Green Lanterns' ring
    // ------------------------------------------------------------------

    private static boolean lookedRing;
    private static java.lang.reflect.Method hasRing;
    private static java.lang.reflect.Method energy;
    private static java.lang.reflect.Field forced;

    private static void lookRing() {
        if (lookedRing) {
            return;
        }
        lookedRing = true;
        try {
            Class<?> data = Class.forName("com.lanternasverdes.data.AnelData");
            hasRing = data.getMethod("temAnel", Player.class);
            energy = data.getMethod("getEnergia", Player.class);
        } catch (ReflectiveOperationException | LinkageError e) {
            hasRing = null;
        }
        try {
            forced = Class.forName("com.lanternasverdes.logic.DanoAnel").getDeclaredField("ORIGINAL");
            forced.setAccessible(true);
        } catch (ReflectiveOperationException | LinkageError e) {
            forced = null;
        }
    }

    /** A player bearing a lit ring (Green Lanterns mod). */
    private static boolean ringBearer(Entity e) {
        lookRing();
        if (hasRing == null || !(e instanceof Player p)) {
            return false;
        }
        try {
            return (Boolean) hasRing.invoke(null, p) && (Float) energy.invoke(null, p) > 0.0F;
        } catch (ReflectiveOperationException | ClassCastException | LinkageError ex) {
            return false;
        }
    }

    /** Any hero of the mod (Yas, Superman, the Hulk, Thor, the Flash, the Scarlet Witch, Storm, Lex in his suit...). */
    public static boolean hero(Player p) {
        return com.flash.power.HeroBalance.hero(p);
    }

    /** The ring's forcing of its damage, taken off the heroes (it is noted there by its HIGHEST handler). */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onRingHit(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || !hero(p)) {
            return;
        }
        lookRing();
        if (forced != null) {
            try {
                ((Map<?, ?>) forced.get(null)).remove(p.getId());
            } catch (ReflectiveOperationException | ClassCastException ignored) {
                // the Lanterns changed
            }
        }
    }

    /** Yas behind a hit (her thrown things too). */
    private static ServerPlayer yasAttacker(DamageSource s) {
        Entity e = s.getEntity();
        if (e == null && s.getDirectEntity() instanceof Projectile pr) {
            e = pr.getOwner();
        }
        return e instanceof ServerPlayer p && YasPowers.has(p) ? p : null;
    }

    private static boolean yas(Entity e) {
        return e instanceof ServerPlayer p && YasPowers.has(p);
    }

    /** Nothing ever hurts her: no hit lands (the void, /kill and the ring included). */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onAttacked(LivingAttackEvent event) {
        if (yas(event.getEntity())) {
            event.setCanceled(true);
            event.getEntity().clearFire();
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onHurtYas(LivingHurtEvent event) {
        if (yas(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onDamageYas(LivingDamageEvent event) {
        if (yas(event.getEntity())) {
            event.setCanceled(true);
            event.setAmount(0.0F);
        }
    }

    /** Through the bubble and whatever else stops a blow: hers always land. */
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onAttackBy(LivingAttackEvent event) {
        if (event.isCanceled() && !yas(event.getEntity()) && yasAttacker(event.getSource()) != null) {
            event.setCanceled(false);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onHurtBy(LivingHurtEvent event) {
        if (!yas(event.getEntity()) && yasAttacker(event.getSource()) != null) {
            event.setCanceled(false);
            event.setAmount(Math.max(BRUTAL, event.getAmount()));
        }
    }

    /** After armour, enchantments and every defence: at least 1000. */
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onDamageBy(LivingDamageEvent event) {
        if (!yas(event.getEntity()) && yasAttacker(event.getSource()) != null) {
            event.setCanceled(false);
            event.setAmount(Math.max(BRUTAL, event.getAmount()));
        }
    }

    /** The least any hit of hers takes off. */
    public static final float BRUTAL = 1000.0F;
}
