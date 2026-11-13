package com.flash.yas;

import com.flash.FlashMod;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The Telekinesis of Hand of God for Yas (and the Scarlet Witch):
 * <ul>
 * <li>the held creature brought closer or pushed away (scroll while holding it): the distance of the hold
 * ({@code Telecinese.PEGADAS -> Pegada.distancia}) changed by reflection;</li>
 * <li>the charged throw (the key held while holding a creature, YasAbilities): when Hand of God lets it go, it is
 * carried along the look for 20 to ~200 blocks (by the charge), and hits what it meets.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = FlashMod.MODID)
public final class YasTelekinesis {

    public static final double MIN = 2.0D;
    public static final double MAX = 32.0D;
    /** Blocks per tick of a throw. */
    private static final double SPEED = 4.0D;
    private static boolean looked;
    private static Field holds;
    private static Field distance;
    private static Field target;

    /** A throw asked for, waiting for Hand of God to let the creature go. */
    private record Pending(LivingEntity e, Vec3 dir, double blocks, int charge, long until) {
    }

    /** A creature in flight. */
    private static final class Fling {
        final LivingEntity e;
        final UUID owner;
        final Vec3 dir;
        final int charge;
        int ticks;

        Fling(LivingEntity e, UUID owner, Vec3 dir, int ticks, int charge) {
            this.e = e;
            this.owner = owner;
            this.dir = dir;
            this.ticks = ticks;
            this.charge = charge;
        }
    }

    private static final Map<UUID, Pending> PENDING = new HashMap<>();
    private static final List<Fling> FLINGS = new ArrayList<>();

    private YasTelekinesis() {
    }

    private static boolean ready() {
        if (!looked) {
            looked = true;
            try {
                Class<?> t = Class.forName("handofgod.telecinese.Telecinese");
                holds = t.getDeclaredField("PEGADAS");
                holds.setAccessible(true);
                Class<?> pegada = Class.forName("handofgod.telecinese.Telecinese$Pegada");
                distance = pegada.getDeclaredField("distancia");
                distance.setAccessible(true);
                target = pegada.getDeclaredField("alvo");
                target.setAccessible(true);
            } catch (ReflectiveOperationException | LinkageError e) {
                holds = null;
            }
        }
        return holds != null;
    }

    private static boolean allowed(ServerPlayer p) {
        return YasPowers.has(p) || com.flash.scarlet.ScarletPowers.has(p);
    }

    private static Object hold(ServerPlayer p) throws ReflectiveOperationException {
        return ((Map<?, ?>) holds.get(null)).get(p.getUUID());
    }

    /** One step of the scroll: closer (negative) or farther (positive). */
    public static void move(ServerPlayer p, int steps) {
        if (!allowed(p) || !ready()) {
            return;
        }
        try {
            Object hold = hold(p);
            if (hold == null) {
                return;
            }
            double d = distance.getDouble(hold);
            double next = Math.max(MIN, Math.min(MAX, d + steps * Math.max(1.0D, d * 0.15D)));
            distance.setDouble(hold, next);
        } catch (ReflectiveOperationException | ClassCastException ignored) {
            // Hand of God changed: nothing to do
        }
    }

    /** The charged throw: the held creature goes 20 (no charge) to 200 blocks (full) once it is let go. */
    public static void charge(ServerPlayer p, int charge) {
        if (!allowed(p) || !ready()) {
            return;
        }
        try {
            Object hold = hold(p);
            if (hold == null || !(target.get(hold) instanceof LivingEntity e)) {
                return;
            }
            double blocks = 20.0D + 180.0D * charge / 100.0D;
            PENDING.put(p.getUUID(), new Pending(e, p.getLookAngle().normalize(), blocks, charge,
                    p.level().getGameTime() + 10L));
        } catch (ReflectiveOperationException | ClassCastException ignored) {
            // Hand of God changed: nothing to do
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || (PENDING.isEmpty() && FLINGS.isEmpty()) || !ready()) {
            return;
        }
        Map<?, ?> held;
        try {
            held = (Map<?, ?>) holds.get(null);
        } catch (ReflectiveOperationException e) {
            return;
        }
        for (Iterator<Map.Entry<UUID, Pending>> it = PENDING.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Pending> en = it.next();
            Pending pd = en.getValue();
            if (!pd.e().isAlive()) {
                it.remove();
                continue;
            }
            if (held.containsKey(en.getKey())) {
                // still held: waits (half a second at most)
                if (pd.e().level().getGameTime() > pd.until()) {
                    it.remove();
                }
                continue;
            }
            it.remove();
            FLINGS.add(new Fling(pd.e(), en.getKey(), pd.dir(), (int) Math.ceil(pd.blocks() / SPEED), pd.charge()));
            if (pd.e().level() instanceof ServerLevel level) {
                level.playSound(null, pd.e().blockPosition(), SoundEvents.TRIDENT_RIPTIDE_3, SoundSource.PLAYERS,
                        1.0F + pd.charge() / 100.0F, 0.7F);
            }
        }
        for (Iterator<Fling> it = FLINGS.iterator(); it.hasNext(); ) {
            Fling f = it.next();
            LivingEntity e = f.e;
            if (!e.isAlive() || !(e.level() instanceof ServerLevel level) || held.values().stream().anyMatch(
                    h -> heldIs(h, e))) {
                it.remove();
                continue;
            }
            if (f.ticks-- <= 0 || e.horizontalCollision || e.verticalCollision && f.dir.y < -0.2D) {
                if (e.horizontalCollision || e.verticalCollision) {
                    impact(level, f);
                }
                it.remove();
                continue;
            }
            e.setDeltaMovement(f.dir.scale(SPEED));
            e.hurtMarked = true;
            e.fallDistance = 0.0F;
            if (e.tickCount % 2 == 0) {
                level.sendParticles(ParticleTypes.CLOUD, e.getX(), e.getY() + e.getBbHeight() / 2.0D, e.getZ(), 2,
                        0.2D, 0.2D, 0.2D, 0.01D);
            }
            // what it meets on the way
            for (LivingEntity hit : level.getEntitiesOfClass(LivingEntity.class, e.getBoundingBox().inflate(0.6D),
                    o -> o != e && o.isAlive() && !o.getUUID().equals(f.owner))) {
                hit.hurt(level.damageSources().flyIntoWall(), 6.0F + f.charge * 0.08F);
                hit.setDeltaMovement(hit.getDeltaMovement().add(f.dir.scale(1.5D)).add(0.0D, 0.4D, 0.0D));
                hit.hurtMarked = true;
            }
        }
    }

    private static boolean heldIs(Object hold, LivingEntity e) {
        try {
            return target.get(hold) == e;
        } catch (ReflectiveOperationException ex) {
            return false;
        }
    }

    private static void impact(ServerLevel level, Fling f) {
        LivingEntity e = f.e;
        e.hurt(level.damageSources().flyIntoWall(), 8.0F + f.charge * 0.22F);
        e.setDeltaMovement(Vec3.ZERO);
        e.hurtMarked = true;
        level.sendParticles(ParticleTypes.EXPLOSION, e.getX(), e.getY() + 0.5D, e.getZ(), 2, 0.3D, 0.3D, 0.3D, 0.0D);
        level.playSound(null, e.blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.7F, 1.2F);
    }
}
