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
 * Yas is immortal, and set against the Green Lanterns mod (lanternasverdes):
 * <ul>
 * <li>she never dies: whatever would kill her (/kill and the void too) leaves her whole; fallen into the void, she
 * is put back on the ground above it;</li>
 * <li>the Lanterns' ring forces its damage through every other mod's defence (its DanoAnel): not through the
 * heroes' of this mod (all of them, hero()): their own defences hold against the ring as against anything;</li>
 * <li>as the ring does to the others, every hero's blows go through a ring bearer's defences (ring, suit, shield bubble):
 * what the Lanterns take off is put back, armour can take half of it at most, and against a player of more life
 * than a common one it grows as the ring's does (life / 20 to the 0.6, five times at most).</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = FlashMod.MODID)
public final class YasImmortal {

    private static final Map<Integer, Float> RAW = new HashMap<>();

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

    /** Any hero of the mod (Yas, Superman, the Hulk, Thor, the Flash, the Scarlet Witch, Storm, Lex in his suit). */
    public static boolean hero(Player p) {
        return YasPowers.has(p) || com.flash.power.HeroBalance.isHero(p) || com.flash.scarlet.ScarletPowers.has(p)
                || com.flash.storm.StormPowers.has(p) || com.flash.lex.LexSuit.active(p);
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

    private static ServerPlayer yasAttacker(DamageSource s) {
        Entity e = s.getEntity();
        if (e == null && s.getDirectEntity() instanceof Projectile pr) {
            e = pr.getOwner();
        }
        return e instanceof ServerPlayer p && hero(p) ? p : null;
    }

    private static boolean yasOnRing(LivingEntity victim, DamageSource s) {
        ServerPlayer hero = yasAttacker(s);
        // a ring bearer who is a hero himself is left to HeroBalance
        return hero != null && hero != victim && !victim.level().isClientSide && ringBearer(victim)
                && !(victim instanceof Player pv && hero(pv));
    }

    /** Through the bubble and whatever else a ring bearer has against a blow. */
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onAttackRing(LivingAttackEvent event) {
        if (event.isCanceled() && yasOnRing(event.getEntity(), event.getSource())) {
            event.setCanceled(false);
        }
    }

    /** The Lanterns' punch with the suit on (1 + the ring's strength of 10): the top of their common blows. */
    private static final float LANTERN_PUNCH = 11.0F;

    /**
     * A hero's blow on the Lanterns' scale: up to a Lantern's punch as it is, above it growing as the square root
     * (Superman's 44 becomes 22, two Lantern punches; a 445 of a hero's strongest blow, 70 as the Lanterns'
     * heaviest weapon), so every hero hits as hard as a ring does, the stronger still stronger.
     */
    public static float lanternScale(float amount) {
        return amount <= LANTERN_PUNCH ? amount : LANTERN_PUNCH * (float) Math.sqrt(amount / LANTERN_PUNCH);
    }

    /**
     * Every hero's damage on the Lanterns' scale (lanternScale), on anything; against a ring bearer below
     * (onHurtRingFirst), and between heroes HeroBalance sets it as before.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onHeroBlow(LivingHurtEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide || yasAttacker(event.getSource()) == null
                || victim instanceof Player pv && (hero(pv) || ringBearer(pv))) {
            return;
        }
        event.setAmount(lanternScale(event.getAmount()));
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onHurtRingFirst(LivingHurtEvent event) {
        LivingEntity victim = event.getEntity();
        RAW.remove(victim.getId());
        if (yasOnRing(victim, event.getSource())) {
            double life = victim.getMaxHealth();
            float mult = life <= 20.0D ? 1.0F : (float) Math.min(5.0D, Math.pow(life / 20.0D, 0.6D));
            float v = lanternScale(event.getAmount()) * mult;
            event.setAmount(v);
            RAW.put(victim.getId(), v);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onHurtRingLast(LivingHurtEvent event) {
        Float raw = RAW.get(event.getEntity().getId());
        if (raw != null) {
            event.setCanceled(false);
            if (event.getAmount() < raw) {
                event.setAmount(raw);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onDamageRing(LivingDamageEvent event) {
        Float raw = RAW.remove(event.getEntity().getId());
        if (raw != null) {
            event.setCanceled(false);
            if (event.getAmount() < raw * 0.5F) {
                event.setAmount(raw * 0.5F);
            }
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && !RAW.isEmpty()) {
            RAW.clear();
        }
    }
}
