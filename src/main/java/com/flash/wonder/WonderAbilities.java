package com.flash.wonder;

import com.flash.FlashMod;
import com.flash.network.FlashNetwork;
import com.flash.network.HeroFxPacket;
import com.flash.network.HeroSyncPacket;
import com.flash.scarlet.ScarletAbilities;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

/**
 * Wonder Woman's powers (R / Z in her modes, WonderClient; cooldowns shown as the Scarlet Witch's):
 * <ul>
 * <li><b>Lasso of Truth</b>: R lassoes a creature: bound in the golden rope it cannot move (and glows); Z the lasso
 * whirled around her, lashing everything near.</li>
 * <li><b>Bracelets of Submission</b>: R her guard, 4 s: arrows and the like are sent back at whoever shot them,
 * blows barely reach her; Z the bracelets crossed and struck together: a golden shockwave ahead.</li>
 * <li><b>Warrior</b>: R her shield thrown: it bounces from enemy to enemy (up to three) and comes back; Z the
 * Amazonian Cataclysm: she leaps high and crashes down, golden lightning and a shockwave all around.</li>
 * </ul>
 * Passive: her bracelets turn some arrows away (35 %).
 */
@Mod.EventBusSubscriber(modid = FlashMod.MODID)
public final class WonderAbilities {

    public static final int LASSO = 0;
    public static final int LASSO_SPIN = 1;
    public static final int GUARD = 2;
    public static final int CLASH = 3;
    public static final int SHIELD_THROW = 4;
    public static final int CATACLYSM = 5;
    public static final int SUIT = 9;
    public static final int SUIT_TOGGLE = 10;
    /** The conjure key: her sword and shield in her hands / sent away (WonderArms). */
    public static final int SUMMON_ARMS = 11;

    public static final int FX_LASSO = 0;
    public static final int FX_SPIN = 1;
    public static final int FX_GUARD = 2;
    public static final int FX_DEFLECT = 3;
    public static final int FX_CLASH = 4;
    public static final int FX_SHIELD = 5;
    public static final int FX_SHIELD_HIT = 6;
    public static final int FX_SHIELD_BACK = 7;
    public static final int FX_LEAP = 8;
    public static final int FX_CATACLYSM = 9;
    public static final int FX_TAKEOFF = 10;
    public static final int FX_CALL = 11;

    private static final int GUARD_TICKS = 80;
    private static final int BIND_TICKS = 100;

    /** Bound in the lasso: entity -> [game time free, the one who holds it]. */
    private static final Map<UUID, Long> BOUND = new HashMap<>();
    private static final Map<UUID, LivingEntity> BOUND_E = new HashMap<>();
    private static final Map<UUID, Long> GUARDS = new HashMap<>();

    /** The shield on its way. */
    private static final class Shield {
        final ServerPlayer owner;
        Vec3 pos;
        Vec3 vel;
        int age;
        int hits;
        boolean back;
        final Set<UUID> struck = new HashSet<>();
        LivingEntity next;

        Shield(ServerPlayer owner, Vec3 pos, Vec3 vel) {
            this.owner = owner;
            this.pos = pos;
            this.vel = vel;
        }
    }

    /** The Cataclysm: up, then down. */
    private static final class Leap {
        final ServerPlayer owner;
        int age;
        boolean falling;

        Leap(ServerPlayer owner) {
            this.owner = owner;
        }
    }

    private static final List<Shield> SHIELDS = new ArrayList<>();
    private static final Map<UUID, Leap> LEAPS = new HashMap<>();

    private WonderAbilities() {
    }

    public static void act(ServerPlayer p, int action, int value) {
        if (!WonderPowers.has(p) || p.isSpectator()) {
            return;
        }
        switch (action) {
            case LASSO -> {
                if (!ScarletAbilities.cooling(p, "lasso") && lasso(p)) {
                    ScarletAbilities.start(p, "lasso", com.flash.scarlet.ScarletCooldowns.ticks("lasso"));
                }
            }
            case LASSO_SPIN -> {
                if (ScarletAbilities.ready(p, "lasso_whip")) {
                    spin(p);
                }
            }
            case GUARD -> {
                if (ScarletAbilities.ready(p, "bracelet_guard")) {
                    guard(p);
                }
            }
            case CLASH -> {
                if (ScarletAbilities.ready(p, "bracelet_clash")) {
                    clash(p);
                }
            }
            case SHIELD_THROW -> {
                if (!thrown(p) && ScarletAbilities.ready(p, "shield_throw")) {
                    throwShield(p);
                }
            }
            case CATACLYSM -> {
                if (!LEAPS.containsKey(p.getUUID()) && ScarletAbilities.ready(p, "cataclysm")) {
                    leap(p);
                }
            }
            case SUIT -> WonderPowers.setSuit(p, value);
            case SUIT_TOGGLE -> WonderPowers.toggleSuited(p);
            case SUMMON_ARMS -> WonderArms.toggle(p);
            default -> {
            }
        }
    }

    public static void fx(ServerPlayer p, int type, Vec3 a, Vec3 b, float power, int target) {
        FlashNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> p),
                new HeroFxPacket(HeroSyncPacket.WONDER, p.getId(), type, a, b, power, target));
    }

    static void forget(ServerPlayer p) {
        GUARDS.remove(p.getUUID());
        LEAPS.remove(p.getUUID());
        SHIELDS.removeIf(s -> s.owner == p);
    }

    public static Vec3 hand(Player p) {
        Vec3 look = p.getLookAngle();
        Vec3 side = new Vec3(-look.z, 0.0D, look.x).normalize().scale(0.36D);
        return p.getEyePosition().add(look.scale(0.45D)).add(side).add(0.0D, -0.4D, 0.0D);
    }

    static boolean foe(ServerPlayer p, Entity e) {
        return e instanceof LivingEntity l && l.isAlive() && e != p
                && !(e instanceof Player pl && (pl.isCreative() || pl.isSpectator()))
                && !(e instanceof net.minecraft.world.entity.decoration.ArmorStand);
    }

    private static HitResult aim(ServerPlayer p, double range, double inflate) {
        Vec3 eye = p.getEyePosition();
        Vec3 end = eye.add(p.getLookAngle().scale(range));
        HitResult block = p.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
                p));
        if (block.getType() != HitResult.Type.MISS) {
            end = block.getLocation();
        }
        EntityHitResult ent = ProjectileUtil.getEntityHitResult(p, eye, end,
                p.getBoundingBox().expandTowards(end.subtract(eye)).inflate(inflate), e -> foe(p, e),
                eye.distanceToSqr(end));
        return ent != null ? ent : block;
    }

    private static void hurt(ServerPlayer p, LivingEntity e, float amount) {
        e.invulnerableTime = 0;
        // not a punch (her punch bonus is for her fists)
        e.hurt(p.damageSources().mobAttack(p), amount);
    }

    // ------------------------------------------------------------------
    // The Lasso of Truth
    // ------------------------------------------------------------------

    public static boolean bound(Entity e) {
        Long until = BOUND.get(e.getUUID());
        return until != null && e.level().getGameTime() < until;
    }

    private static boolean lasso(ServerPlayer p) {
        HitResult hit = aim(p, 26.0D, 0.9D);
        ServerLevel level = p.serverLevel();
        level.playSound(null, p.getX(), p.getY(), p.getZ(), com.flash.registry.ModSounds.WONDER_LASSO.get(),
                SoundSource.PLAYERS, 1.0F, 1.0F);
        if (!(hit instanceof EntityHitResult eh) || !(eh.getEntity() instanceof LivingEntity e)) {
            Vec3 to = hit.getType() == HitResult.Type.MISS ? p.getEyePosition().add(p.getLookAngle().scale(26.0D))
                    : hit.getLocation();
            fx(p, FX_LASSO, hand(p), to, 0.0F, -1);
            return false;
        }
        long until = level.getGameTime() + BIND_TICKS;
        BOUND.put(e.getUUID(), until);
        BOUND_E.put(e.getUUID(), e);
        e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, BIND_TICKS, 6, false, false));
        e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, BIND_TICKS, 2, false, false));
        e.addEffect(new MobEffectInstance(MobEffects.GLOWING, BIND_TICKS, 0, false, false));
        hurt(p, e, 3.0F);
        // drawn a little toward her
        Vec3 pull = p.position().subtract(e.position());
        if (pull.lengthSqr() > 9.0D) {
            e.setDeltaMovement(pull.normalize().scale(0.8D).add(0.0D, 0.3D, 0.0D));
            e.hurtMarked = true;
        }
        if (e instanceof Mob m) {
            m.getNavigation().stop();
        }
        level.playSound(null, e.getX(), e.getY(), e.getZ(), SoundEvents.LEASH_KNOT_PLACE, SoundSource.PLAYERS, 1.2F,
                0.8F);
        fx(p, FX_LASSO, hand(p), e.position(), BIND_TICKS, e.getId());
        return true;
    }

    private static void spin(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(6.5D),
                e -> foe(p, e) && e.distanceToSqr(p) < 6.5D * 6.5D)) {
            Vec3 away = e.position().subtract(p.position());
            double d = Math.max(0.5D, away.length());
            hurt(p, e, 8.0F);
            e.knockback(1.4D, -away.x / d, -away.z / d);
            e.hurtMarked = true;
        }
        level.playSound(null, p.getX(), p.getY(), p.getZ(), com.flash.registry.ModSounds.WONDER_LASSO.get(),
                SoundSource.PLAYERS, 1.2F, 0.8F);
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0F,
                0.7F);
        fx(p, FX_SPIN, p.position(), p.position(), 6.5F, -1);
    }

    // ------------------------------------------------------------------
    // The Bracelets
    // ------------------------------------------------------------------

    public static boolean guarding(Player p) {
        Long until = GUARDS.get(p.getUUID());
        return until != null && p.level().getGameTime() < until;
    }

    private static void guard(ServerPlayer p) {
        GUARDS.put(p.getUUID(), p.level().getGameTime() + GUARD_TICKS);
        p.serverLevel().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ANVIL_PLACE, SoundSource.PLAYERS,
                0.6F, 1.8F);
        fx(p, FX_GUARD, p.position(), p.position(), GUARD_TICKS, -1);
        WonderPowers.sync(p);
    }

    private static void clash(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        Vec3 eye = p.getEyePosition();
        Vec3 look = p.getLookAngle();
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(14.0D),
                e -> foe(p, e))) {
            Vec3 to = e.position().add(0.0D, e.getBbHeight() * 0.5D, 0.0D).subtract(eye);
            double d = to.length();
            if (d > 14.0D || d < 0.01D || to.normalize().dot(look) < 0.5D) {
                continue;
            }
            hurt(p, e, (float) (14.0D - d * 0.5D));
            e.knockback(2.6D - d * 0.1D, -look.x, -look.z);
            e.setDeltaMovement(e.getDeltaMovement().add(0.0D, 0.4D, 0.0D));
            e.hurtMarked = true;
        }
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 1.2F, 1.6F);
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS,
                0.8F, 1.8F);
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 0.6F,
                1.6F);
        fx(p, FX_CLASH, hand(p), look, 14.0F, -1);
    }

    /** Arrows and the like: sent back by the guard, turned away by the bracelets now and then. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onProjectile(ProjectileImpactEvent event) {
        if (!(event.getRayTraceResult() instanceof EntityHitResult eh) || !(eh.getEntity() instanceof ServerPlayer p)
                || !WonderPowers.has(p) || event.getProjectile().getOwner() == p) {
            return;
        }
        boolean guard = guarding(p);
        if (!guard && p.getRandom().nextFloat() >= 0.35F) {
            return;
        }
        Projectile pr = event.getProjectile();
        event.setCanceled(true);
        Entity shooter = pr.getOwner();
        Vec3 back = shooter != null && guard
                ? shooter.position().add(0.0D, shooter.getBbHeight() * 0.6D, 0.0D).subtract(pr.position()).normalize()
                : pr.getDeltaMovement().scale(-1.0D).normalize();
        double speed = Math.max(1.2D, pr.getDeltaMovement().length());
        pr.setDeltaMovement(back.scale(speed));
        pr.hurtMarked = true;
        if (guard) {
            pr.setOwner(p);
        }
        p.serverLevel().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ANVIL_PLACE, SoundSource.PLAYERS,
                0.5F, 2.0F);
        fx(p, FX_DEFLECT, pr.position(), back, 1.0F, -1);
    }

    /** With the guard up, blows from in front barely reach her and throw the attacker back. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onAttack(LivingAttackEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || !guarding(p)
                || !(event.getSource().getDirectEntity() instanceof LivingEntity a) || a == p) {
            return;
        }
        Vec3 to = a.position().subtract(p.position()).normalize();
        if (to.dot(p.getLookAngle().multiply(1.0D, 0.0D, 1.0D).normalize()) < 0.2D) {
            return;
        }
        event.setCanceled(true);
        a.knockback(1.2D, -to.x, -to.z);
        a.hurtMarked = true;
        p.serverLevel().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ANVIL_PLACE, SoundSource.PLAYERS,
                0.6F, 1.7F);
        fx(p, FX_DEFLECT, p.position().add(to.scale(0.6D)).add(0.0D, 1.2D, 0.0D), to, 1.0F, -1);
    }

    // ------------------------------------------------------------------
    // The Warrior
    // ------------------------------------------------------------------

    public static boolean thrown(Player p) {
        for (Shield s : SHIELDS) {
            if (s.owner == p) {
                return true;
            }
        }
        return false;
    }

    private static void throwShield(ServerPlayer p) {
        Vec3 from = hand(p);
        Shield s = new Shield(p, from, p.getLookAngle().scale(1.7D));
        HitResult hit = aim(p, 40.0D, 0.9D);
        if (hit instanceof EntityHitResult eh && eh.getEntity() instanceof LivingEntity e) {
            s.next = e;
        }
        SHIELDS.add(s);
        p.serverLevel().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.TRIDENT_THROW, SoundSource.PLAYERS,
                1.0F, 0.7F);
    }

    private static void leap(ServerPlayer p) {
        Vec3 look = p.getLookAngle();
        p.setDeltaMovement(look.x * 0.6D, 1.7D, look.z * 0.6D);
        p.hurtMarked = true;
        p.fallDistance = 0.0F;
        if (p.getAbilities().flying) {
            p.getAbilities().flying = false;
            p.onUpdateAbilities();
        }
        LEAPS.put(p.getUUID(), new Leap(p));
        p.serverLevel().playSound(null, p.getX(), p.getY(), p.getZ(), com.flash.registry.ModSounds.WONDER_CATACLYSM.get(),
                SoundSource.PLAYERS, 1.4F, 1.0F);
        fx(p, FX_LEAP, p.position(), p.position(), 1.0F, -1);
    }

    private static void crash(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        Vec3 at = p.position();
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(11.0D, 5.0D,
                11.0D), e -> foe(p, e))) {
            Vec3 away = e.position().subtract(at);
            double d = Math.max(0.5D, away.length());
            if (d > 11.0D) {
                continue;
            }
            hurt(p, e, (float) (24.0D - d * 1.2D));
            e.knockback(2.2D, -away.x / d, -away.z / d);
            e.setDeltaMovement(e.getDeltaMovement().add(0.0D, 0.9D - d * 0.05D, 0.0D));
            e.hurtMarked = true;
        }
        for (int i = 0; i < 4; i++) {
            double a = i * Math.PI / 2.0D + p.getRandom().nextDouble();
            var bolt = net.minecraft.world.entity.EntityType.LIGHTNING_BOLT.create(level);
            if (bolt != null) {
                bolt.moveTo(at.x + Math.cos(a) * 5.0D, at.y, at.z + Math.sin(a) * 5.0D);
                bolt.setVisualOnly(true);
                level.addFreshEntity(bolt);
            }
        }
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y + 0.2D, at.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 2.0F, 0.7F);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 2.0F, 0.8F);
        fx(p, FX_CATACLYSM, at, at, 11.0F, -1);
    }

    // ------------------------------------------------------------------
    // Every tick
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        for (Iterator<Map.Entry<UUID, LivingEntity>> it = BOUND_E.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, LivingEntity> en = it.next();
            LivingEntity e = en.getValue();
            Long until = BOUND.get(en.getKey());
            if (e == null || !e.isAlive() || e.isRemoved() || until == null || e.level().getGameTime() >= until) {
                it.remove();
                BOUND.remove(en.getKey());
                continue;
            }
            Vec3 v = e.getDeltaMovement();
            e.setDeltaMovement(v.x * 0.15D, Math.min(v.y, 0.0D), v.z * 0.15D);
            if (e instanceof Mob m) {
                m.getNavigation().stop();
                m.setTarget(null);
            }
        }
        tickShields();
        for (Iterator<Leap> it = LEAPS.values().iterator(); it.hasNext(); ) {
            Leap l = it.next();
            ServerPlayer p = l.owner;
            if (p.isRemoved() || !p.isAlive()) {
                it.remove();
                continue;
            }
            l.age++;
            p.fallDistance = 0.0F;
            if (!l.falling && (p.getDeltaMovement().y <= 0.1D || l.age > 14)) {
                l.falling = true;
                p.setDeltaMovement(p.getDeltaMovement().x * 0.4D, -3.2D, p.getDeltaMovement().z * 0.4D);
                p.hurtMarked = true;
            }
            if (l.falling && (p.onGround() || l.age > 60)) {
                it.remove();
                crash(p);
            }
        }
        long now = event.getServer().overworld().getGameTime();
        GUARDS.entrySet().removeIf(en -> {
            ServerPlayer p = event.getServer().getPlayerList().getPlayer(en.getKey());
            if (p == null) {
                return true;
            }
            if (p.level().getGameTime() >= en.getValue()) {
                WonderPowers.sync(p);
                return true;
            }
            return false;
        });
        if (now < 0) {
            GUARDS.clear();
        }
    }

    private static void tickShields() {
        for (Iterator<Shield> it = SHIELDS.iterator(); it.hasNext(); ) {
            Shield s = it.next();
            ServerPlayer p = s.owner;
            if (p.isRemoved() || ++s.age > 140) {
                it.remove();
                continue;
            }
            ServerLevel level = p.serverLevel();
            Vec3 target;
            if (s.back) {
                target = p.position().add(0.0D, 1.2D, 0.0D);
                if (s.pos.distanceToSqr(target) < 2.0D) {
                    it.remove();
                    level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ARMOR_EQUIP_IRON,
                            SoundSource.PLAYERS, 1.0F, 1.2F);
                    fx(p, FX_SHIELD_BACK, s.pos, s.pos, 1.0F, -1);
                    continue;
                }
            } else if (s.next != null && s.next.isAlive()) {
                target = s.next.position().add(0.0D, s.next.getBbHeight() * 0.5D, 0.0D);
            } else {
                target = null;
            }
            if (target != null) {
                Vec3 want = target.subtract(s.pos).normalize().scale(s.back ? 2.0D : 1.8D);
                s.vel = s.vel.add(want.subtract(s.vel).scale(0.5D));
            }
            Vec3 next = s.pos.add(s.vel);
            if (!s.back) {
                HitResult wall = level.clip(new ClipContext(s.pos, next, ClipContext.Block.COLLIDER,
                        ClipContext.Fluid.NONE, p));
                if (wall.getType() != HitResult.Type.MISS || s.age > 30) {
                    s.back = true;
                    if (wall.getType() != HitResult.Type.MISS) {
                        level.playSound(null, s.pos.x, s.pos.y, s.pos.z, SoundEvents.SHIELD_BLOCK,
                                SoundSource.PLAYERS, 1.0F, 0.8F);
                        next = wall.getLocation();
                    }
                }
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(s.pos, next).inflate(0.7D),
                        e -> foe(p, e) && !s.struck.contains(e.getUUID()))) {
                    s.struck.add(e.getUUID());
                    hurt(p, e, 10.0F);
                    e.knockback(0.9D, -s.vel.x, -s.vel.z);
                    level.playSound(null, e.getX(), e.getY(), e.getZ(), SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS,
                            1.2F, 0.6F);
                    fx(p, FX_SHIELD_HIT, e.position().add(0.0D, e.getBbHeight() * 0.5D, 0.0D), s.vel, 1.0F, -1);
                    // on to the next one near (three at most), then home
                    s.hits++;
                    s.next = null;
                    if (s.hits >= 3) {
                        s.back = true;
                        break;
                    }
                    double best = 12.0D * 12.0D;
                    for (LivingEntity o : level.getEntitiesOfClass(LivingEntity.class, e.getBoundingBox().inflate(12.0D),
                            o -> foe(p, o) && !s.struck.contains(o.getUUID()))) {
                        double d = o.distanceToSqr(e);
                        if (d < best) {
                            best = d;
                            s.next = o;
                        }
                    }
                    if (s.next == null) {
                        s.back = true;
                    }
                    break;
                }
            }
            fx(p, FX_SHIELD, s.pos, next, s.age, -1);
            s.pos = next;
        }
    }
}
