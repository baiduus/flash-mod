package com.flash.doom;

import com.flash.FlashMod;
import com.flash.network.FlashNetwork;
import com.flash.network.HeroFxPacket;
import com.flash.network.HeroSyncPacket;
import com.flash.scarlet.ScarletAbilities;
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
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
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
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

/**
 * Doctor Doom's powers (R / Z in his modes, DoomClient; cooldowns shown as the Scarlet Witch's):
 * <ul>
 * <li><b>Gauntlets</b>: R a bolt of energy from the gauntlet; R held, a beam; Z a discharge of lightning in front
 * of him that throws everything back.</li>
 * <li><b>Force Field</b>: R his shield, 10 s (what hits him barely reaches him, arrows bounce off); Z a wave of
 * force pushing everything around away.</li>
 * <li><b>Sorcery</b>: R a mystic bolt that seeks the nearest enemy in front of him; Z the ritual of Latveria: a
 * circle of runes where he looks, erupting a moment later and throwing up whatever stands in it.</li>
 * </ul>
 * His bolts are drawn in light by DoomVfx (the same additive light as the other heroes' VfxDraw effects).
 */
@Mod.EventBusSubscriber(modid = FlashMod.MODID)
public final class DoomAbilities {

    public static final int BLAST = 0;
    public static final int BEAM = 1;
    public static final int DISCHARGE = 2;
    public static final int SHIELD = 3;
    public static final int WAVE = 4;
    public static final int MYSTIC = 5;
    public static final int RITUAL = 6;
    /** The suit key (U): the armour on / off (his powers stay). */
    public static final int SUIT_TOGGLE = 9;
    /** The force field's radius: nobody gets in. */
    public static final double FIELD_RADIUS = 2.6D;

    public static final int FX_BOLT = 0;
    public static final int FX_BEAM = 1;
    public static final int FX_DISCHARGE = 2;
    public static final int FX_SHIELD = 3;
    public static final int FX_WAVE = 4;
    public static final int FX_MYSTIC = 5;
    public static final int FX_MYSTIC_HIT = 6;
    public static final int FX_RITUAL = 7;
    public static final int FX_ERUPT = 8;
    public static final int FX_TAKEOFF = 9;
    public static final int FX_GRIMOIRE = 10;
    public static final int FX_SHIELD_HIT = 11;
    /** Nothing drawn: he casts (his arms; power: 1 the mystic bolt, 2 the ritual). */
    public static final int FX_CAST = 12;

    private static final int SHIELD_TICKS = 200;
    private static final int RITUAL_TICKS = 30;

    /** The force field: player -> game time it ends. */
    private static final Map<UUID, Long> SHIELDS = new HashMap<>();

    /** A mystic bolt on its way. */
    private static final class Bolt {
        final ServerPlayer owner;
        Vec3 pos;
        Vec3 vel;
        int age;
        LivingEntity prey;

        Bolt(ServerPlayer owner, Vec3 pos, Vec3 vel) {
            this.owner = owner;
            this.pos = pos;
            this.vel = vel;
        }
    }

    /** A ritual circle charging. */
    private static final class Ritual {
        final ServerPlayer owner;
        final Vec3 at;
        int age;

        Ritual(ServerPlayer owner, Vec3 at) {
            this.owner = owner;
            this.at = at;
        }
    }

    private static final List<Bolt> BOLTS = new ArrayList<>();
    private static final List<Ritual> RITUALS = new ArrayList<>();

    private DoomAbilities() {
    }

    public static void act(ServerPlayer p, int action, int value) {
        if (!DoomPowers.has(p) || p.isSpectator()) {
            return;
        }
        switch (action) {
            case BLAST -> {
                if (ScarletAbilities.ready(p, "doom_blast")) {
                    blast(p);
                }
            }
            case BEAM -> beam(p);
            case DISCHARGE -> {
                if (ScarletAbilities.ready(p, "discharge")) {
                    discharge(p);
                }
            }
            case SHIELD -> {
                if (ScarletAbilities.ready(p, "force_field")) {
                    shield(p);
                }
            }
            case WAVE -> {
                if (ScarletAbilities.ready(p, "force_wave")) {
                    wave(p);
                }
            }
            case MYSTIC -> {
                if (ScarletAbilities.ready(p, "mystic_bolt")) {
                    mystic(p);
                }
            }
            case RITUAL -> {
                if (!ScarletAbilities.cooling(p, "ritual") && ritual(p)) {
                    ScarletAbilities.start(p, "ritual", com.flash.scarlet.ScarletCooldowns.ticks("ritual"));
                }
            }
            case SUIT_TOGGLE -> DoomPowers.toggleSuited(p);
            default -> {
            }
        }
    }

    public static void fx(ServerPlayer p, int type, Vec3 a, Vec3 b, float power, int target) {
        FlashNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> p),
                new HeroFxPacket(HeroSyncPacket.DOOM, p.getId(), type, a, b, power, target));
    }

    static void forget(ServerPlayer p) {
        SHIELDS.remove(p.getUUID());
    }

    /** His right gauntlet. */
    public static Vec3 hand(Player p) {
        Vec3 look = p.getLookAngle();
        Vec3 side = new Vec3(-look.z, 0.0D, look.x).normalize().scale(0.38D);
        return p.getEyePosition().add(look.scale(0.55D)).add(side).add(0.0D, -0.38D, 0.0D);
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

    static boolean foe(ServerPlayer p, Entity e) {
        return e instanceof LivingEntity l && l.isAlive() && e != p
                && !(e instanceof Player pl && (pl.isCreative() || pl.isSpectator()))
                && !(e instanceof net.minecraft.world.entity.decoration.ArmorStand);
    }

    private static void hurt(ServerPlayer p, LivingEntity e, float amount) {
        e.invulnerableTime = 0;
        e.hurt(p.damageSources().indirectMagic(p, p), amount);
    }

    private static Vec3 end(ServerPlayer p, HitResult hit, double range) {
        if (hit instanceof EntityHitResult eh) {
            Entity e = eh.getEntity();
            return e.position().add(0.0D, e.getBbHeight() * 0.55D, 0.0D);
        }
        return hit.getType() == HitResult.Type.MISS ? p.getEyePosition().add(p.getLookAngle().scale(range))
                : hit.getLocation();
    }

    // ------------------------------------------------------------------
    // Gauntlets
    // ------------------------------------------------------------------

    private static void blast(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        HitResult hit = aim(p, 56.0D, 0.5D);
        Vec3 to = end(p, hit, 56.0D);
        if (hit instanceof EntityHitResult eh && eh.getEntity() instanceof LivingEntity e) {
            hurt(p, e, 9.0F);
            Vec3 push = p.getLookAngle();
            e.knockback(0.8D, -push.x, -push.z);
        }
        if (hit.getType() != HitResult.Type.MISS) {
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, to.x, to.y, to.z, 20, 0.3D, 0.3D, 0.3D, 0.3D);
        }
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.TRIDENT_THUNDER, SoundSource.PLAYERS, 0.35F,
                2.0F);
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.7F,
                2.0F);
        fx(p, FX_BOLT, hand(p), to, hit.getType() == HitResult.Type.MISS ? 0.0F : 1.0F, -1);
    }

    /** The beam held (sent every few ticks by his client, which keeps its length and cooldown). */
    private static void beam(ServerPlayer p) {
        if (ScarletAbilities.cooling(p, "doom_beam")) {
            return;
        }
        ServerLevel level = p.serverLevel();
        HitResult hit = aim(p, 40.0D, 0.4D);
        Vec3 to = end(p, hit, 40.0D);
        if (hit instanceof EntityHitResult eh && eh.getEntity() instanceof LivingEntity e) {
            hurt(p, e, 3.5F);
            e.setSecondsOnFire(2);
        }
        if (p.tickCount % 6 == 0) {
            level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, 1.0F,
                    1.8F);
        }
        fx(p, FX_BEAM, hand(p), to, hit.getType() == HitResult.Type.MISS ? 0.0F : 1.0F, -1);
    }

    private static void discharge(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        Vec3 eye = p.getEyePosition();
        Vec3 look = p.getLookAngle();
        int n = 0;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(11.0D),
                e -> foe(p, e))) {
            Vec3 to = e.position().add(0.0D, e.getBbHeight() * 0.5D, 0.0D).subtract(eye);
            double d = to.length();
            if (d > 11.0D || d < 0.01D || to.normalize().dot(look) < 0.6D) {
                continue;
            }
            hurt(p, e, 8.0F);
            e.knockback(1.8D, -look.x, -look.z);
            e.setDeltaMovement(e.getDeltaMovement().add(0.0D, 0.3D, 0.0D));
            e.hurtMarked = true;
            fx(p, FX_BOLT, hand(p), e.position().add(0.0D, e.getBbHeight() * 0.5D, 0.0D), 0.6F, -1);
            n++;
        }
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS,
                0.8F, 1.7F);
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS,
                1.0F, 1.4F);
        fx(p, FX_DISCHARGE, hand(p), look, n, -1);
    }

    // ------------------------------------------------------------------
    // Force field
    // ------------------------------------------------------------------

    public static boolean shielded(Player p) {
        Long until = SHIELDS.get(p.getUUID());
        return until != null && p.level().getGameTime() < until;
    }

    private static void shield(ServerPlayer p) {
        SHIELDS.put(p.getUUID(), p.level().getGameTime() + SHIELD_TICKS);
        p.serverLevel().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BEACON_ACTIVATE,
                SoundSource.PLAYERS, 1.2F, 1.4F);
        fx(p, FX_SHIELD, p.position(), p.position(), SHIELD_TICKS, -1);
        DoomPowers.sync(p);
    }

    /**
     * The force field keeps everyone out: whoever comes inside is pushed gently back to its edge (not thrown far).
     */
    private static void barrier(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        Vec3 c = p.position().add(0.0D, p.getBbHeight() * 0.5D, 0.0D);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(FIELD_RADIUS + 0.5D),
                e -> e != p && e.isAlive() && !e.isSpectator()
                        && !(e instanceof net.minecraft.world.entity.decoration.ArmorStand))) {
            Vec3 d = e.position().add(0.0D, e.getBbHeight() * 0.5D, 0.0D).subtract(c);
            double reach = FIELD_RADIUS + e.getBbWidth() * 0.5D;
            double dist = d.length();
            if (dist >= reach) {
                continue;
            }
            Vec3 flat = new Vec3(d.x, 0.0D, d.z);
            Vec3 out = flat.lengthSqr() < 1.0E-4D ? new Vec3(1.0D, 0.0D, 0.0D) : flat.normalize();
            double depth = (reach - dist) / reach;
            Vec3 v = e.getDeltaMovement();
            double into = v.x * out.x + v.z * out.z;
            if (into < 0.0D) {
                // its step inward is stopped at the field
                v = v.subtract(out.scale(into));
            }
            v = v.add(out.scale(0.12D + 0.35D * depth));
            e.setDeltaMovement(v.x, Math.max(v.y, 0.05D), v.z);
            e.hurtMarked = true;
            if (e.tickCount % 10 == 0) {
                fx(p, FX_SHIELD_HIT, p.position(), p.position(), 1.0F, -1);
                level.playSound(null, e.getX(), e.getY(), e.getZ(), SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS,
                        0.5F, 2.0F);
            }
        }
    }

    static void shieldHit(ServerPlayer p) {
        p.serverLevel().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS,
                0.8F, 1.6F);
        fx(p, FX_SHIELD_HIT, p.position(), p.position(), 1.0F, -1);
    }

    /** Arrows and the like bounce off the field. */
    @SubscribeEvent
    public static void onProjectile(ProjectileImpactEvent event) {
        if (event.getRayTraceResult() instanceof EntityHitResult eh && eh.getEntity() instanceof ServerPlayer p
                && shielded(p) && event.getProjectile().getOwner() != p) {
            Projectile pr = event.getProjectile();
            event.setCanceled(true);
            pr.setDeltaMovement(pr.getDeltaMovement().scale(-0.4D));
            pr.hurtMarked = true;
            shieldHit(p);
        }
    }

    private static void wave(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(9.0D),
                e -> foe(p, e) && e.distanceToSqr(p) < 81.0D)) {
            Vec3 away = e.position().subtract(p.position());
            double d = Math.max(1.0D, away.length());
            hurt(p, e, (float) (8.0D - d * 0.5D));
            e.knockback(3.0D - d * 0.2D, -away.x / d, -away.z / d);
            e.setDeltaMovement(e.getDeltaMovement().add(0.0D, 0.45D, 0.0D));
            e.hurtMarked = true;
        }
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 0.8F,
                1.5F);
        fx(p, FX_WAVE, p.position().add(0.0D, 1.0D, 0.0D), p.position(), 9.0F, -1);
    }

    // ------------------------------------------------------------------
    // Sorcery
    // ------------------------------------------------------------------

    private static void mystic(ServerPlayer p) {
        Vec3 from = hand(p);
        BOLTS.add(new Bolt(p, from, p.getLookAngle().scale(1.1D)));
        fx(p, FX_CAST, from, from, 1.0F, -1);
        p.serverLevel().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.EVOKER_CAST_SPELL,
                SoundSource.PLAYERS, 1.0F, 1.3F);
        p.serverLevel().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ILLUSIONER_CAST_SPELL,
                SoundSource.PLAYERS, 0.8F, 1.0F);
    }

    private static boolean ritual(ServerPlayer p) {
        Vec3 eye = p.getEyePosition();
        HitResult hit = p.level().clip(new ClipContext(eye, eye.add(p.getLookAngle().scale(40.0D)),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        HitResult e = aim(p, 40.0D, 0.6D);
        Vec3 at = e instanceof EntityHitResult eh ? eh.getEntity().position() : hit.getLocation();
        if (e.getType() == HitResult.Type.MISS && hit.getType() == HitResult.Type.MISS) {
            return false;
        }
        // down onto the ground under the point
        HitResult g = p.level().clip(new ClipContext(at.add(0.0D, 0.5D, 0.0D), at.add(0.0D, -16.0D, 0.0D),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        if (g.getType() != HitResult.Type.MISS) {
            at = g.getLocation();
        }
        RITUALS.add(new Ritual(p, at));
        fx(p, FX_CAST, at, at, 2.0F, -1);
        p.serverLevel().playSound(null, at.x, at.y, at.z, SoundEvents.EVOKER_PREPARE_SUMMON, SoundSource.PLAYERS, 1.4F,
                0.8F);
        fx(p, FX_RITUAL, at, at, 5.0F, RITUAL_TICKS);
        return true;
    }

    // ------------------------------------------------------------------
    // Every tick: the bolts on their way, the rituals charging, the fields running out
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        for (Iterator<Bolt> it = BOLTS.iterator(); it.hasNext(); ) {
            Bolt b = it.next();
            if (b.owner.isRemoved() || ++b.age > 80) {
                it.remove();
                continue;
            }
            ServerLevel level = b.owner.serverLevel();
            // it seeks the nearest enemy in front of it
            if (b.prey == null || !b.prey.isAlive()) {
                b.prey = null;
                double best = 24.0D * 24.0D;
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(b.pos, b.pos)
                        .inflate(24.0D), e -> foe(b.owner, e))) {
                    Vec3 to = e.position().add(0.0D, e.getBbHeight() * 0.5D, 0.0D).subtract(b.pos);
                    double d = to.lengthSqr();
                    if (d < best && to.normalize().dot(b.vel.normalize()) > 0.3D) {
                        best = d;
                        b.prey = e;
                    }
                }
            }
            if (b.prey != null) {
                Vec3 want = b.prey.position().add(0.0D, b.prey.getBbHeight() * 0.5D, 0.0D).subtract(b.pos)
                        .normalize().scale(1.25D);
                b.vel = b.vel.add(want.subtract(b.vel).scale(0.22D));
            }
            Vec3 next = b.pos.add(b.vel);
            HitResult wall = level.clip(new ClipContext(b.pos, next, ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE, b.owner));
            LivingEntity struck = null;
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(b.pos, next).inflate(0.6D),
                    e -> foe(b.owner, e))) {
                struck = e;
                break;
            }
            fx(b.owner, FX_MYSTIC, b.pos, wall.getType() != HitResult.Type.MISS ? wall.getLocation() : next, 1.0F, -1);
            if (struck != null || wall.getType() != HitResult.Type.MISS) {
                Vec3 at = struck != null ? struck.position().add(0.0D, struck.getBbHeight() * 0.5D, 0.0D)
                        : wall.getLocation();
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(2.5D),
                        e -> foe(b.owner, e))) {
                    hurt(b.owner, e, e == struck ? 13.0F : 5.0F);
                    e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 1));
                }
                level.playSound(null, at.x, at.y, at.z, SoundEvents.EVOKER_FANGS_ATTACK, SoundSource.PLAYERS, 1.2F,
                        1.2F);
                level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.5F, 1.8F);
                fx(b.owner, FX_MYSTIC_HIT, at, at, 1.0F, -1);
                it.remove();
                continue;
            }
            b.pos = next;
        }
        for (Iterator<Ritual> it = RITUALS.iterator(); it.hasNext(); ) {
            Ritual r = it.next();
            if (r.owner.isRemoved()) {
                it.remove();
                continue;
            }
            ServerLevel level = r.owner.serverLevel();
            r.age++;
            if (r.age % 8 == 0 && r.age < RITUAL_TICKS) {
                level.sendParticles(ParticleTypes.ENCHANT, r.at.x, r.at.y + 0.5D, r.at.z, 30, 2.5D, 0.3D, 2.5D, 0.5D);
            }
            if (r.age < RITUAL_TICKS) {
                continue;
            }
            it.remove();
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(r.at, r.at)
                    .inflate(5.0D, 3.0D, 5.0D), e -> foe(r.owner, e)
                    && e.position().multiply(1, 0, 1).distanceToSqr(r.at.multiply(1, 0, 1)) < 25.0D)) {
                hurt(r.owner, e, 16.0F);
                e.setDeltaMovement(e.getDeltaMovement().x * 0.3D, 1.25D, e.getDeltaMovement().z * 0.3D);
                e.hurtMarked = true;
                e.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 30, 0));
            }
            level.playSound(null, r.at.x, r.at.y, r.at.z, SoundEvents.EVOKER_CAST_SPELL, SoundSource.PLAYERS, 2.0F,
                    0.6F);
            level.playSound(null, r.at.x, r.at.y, r.at.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.2F,
                    0.9F);
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, r.at.x, r.at.y + 0.2D, r.at.z, 80, 2.5D, 0.2D, 2.5D,
                    0.25D);
            fx(r.owner, FX_ERUPT, r.at, r.at, 5.0F, -1);
        }
        if (!SHIELDS.isEmpty()) {
            for (Map.Entry<UUID, Long> en : SHIELDS.entrySet()) {
                ServerPlayer p = event.getServer().getPlayerList().getPlayer(en.getKey());
                if (p != null && p.level().getGameTime() < en.getValue()) {
                    barrier(p);
                }
            }
            for (Iterator<Map.Entry<UUID, Long>> it = SHIELDS.entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<UUID, Long> en = it.next();
                ServerPlayer p = event.getServer().getPlayerList().getPlayer(en.getKey());
                if (p == null) {
                    it.remove();
                } else if (p.level().getGameTime() >= en.getValue()) {
                    it.remove();
                    p.serverLevel().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BEACON_DEACTIVATE,
                            SoundSource.PLAYERS, 1.0F, 1.4F);
                    DoomPowers.sync(p);
                }
            }
        }
    }
}
