package com.flash.storm;

import com.flash.FlashMod;
import com.flash.network.FlashNetwork;
import com.flash.network.StormActionPacket;
import com.flash.network.StormFxPacket;
import com.flash.scarlet.ScarletAbilities;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

/**
 * Storm's powers (R / Z in her modes, StormClient; cooldowns shown as the Scarlet Witch's):
 * <ul>
 * <li><b>Lightning</b>: R calls a bolt from the sky onto where she looks (it leaps to two more near); R held, a
 * stream of lightning from her hands; Z, chain lightning jumping through up to six foes.</li>
 * <li><b>Winds</b>: R a gust that throws everything in front of her away; Z a tornado where she looks, 8 s, lifting
 * and spinning what it catches, lightning inside.</li>
 * <li><b>Weather</b>: R turns the weather (clear, rain, thunderstorm); Z the Fury of the Storm: 12 s of thunderstorm
 * around her, the sky striking every monster near (and whoever hits her).</li>
 * <li><b>Ice</b>: R hail on where she looks, 3 s; Z a wave of cold around her: creatures frozen and slowed, water
 * turned to ice.</li>
 * </ul>
 * The bolts are Man of Tomorrow's Shazam lightning (StormClient), and where they hit the ground they leave its burn
 * marks.
 */
@Mod.EventBusSubscriber(modid = FlashMod.MODID)
public final class StormAbilities {

    private static final double RANGE = 64.0D;
    private static final Map<UUID, long[]> BEAM = new HashMap<>();

    private StormAbilities() {
    }

    private static float power() {
        return StormPowers.POWER;
    }

    public static void act(ServerPlayer p, int action, int value) {
        if (!StormPowers.has(p) || p.isSpectator()) {
            return;
        }
        switch (action) {
            case StormActionPacket.STRIKE -> {
                if (ScarletAbilities.ready(p, "storm_strike")) {
                    strike(p);
                }
            }
            case StormActionPacket.BEAM -> beam(p);
            case StormActionPacket.CHAIN -> {
                if (ScarletAbilities.ready(p, "storm_chain")) {
                    chain(p);
                }
            }
            case StormActionPacket.GUST -> {
                if (ScarletAbilities.ready(p, "gust")) {
                    gust(p);
                }
            }
            case StormActionPacket.TORNADO -> {
                if (ScarletAbilities.ready(p, "tornado")) {
                    tornado(p);
                }
            }
            case StormActionPacket.WEATHER -> {
                if (ScarletAbilities.ready(p, "weather")) {
                    weather(p);
                }
            }
            case StormActionPacket.FURY -> {
                if (ScarletAbilities.ready(p, "fury_storm")) {
                    fury(p);
                }
            }
            case StormActionPacket.HAIL -> {
                if (ScarletAbilities.ready(p, "hail")) {
                    hail(p);
                }
            }
            case StormActionPacket.FROST -> {
                if (ScarletAbilities.ready(p, "frost")) {
                    frost(p);
                }
            }
            default -> {
            }
        }
    }

    public static void fx(ServerPlayer p, int type, Vec3 a, Vec3 b, float power) {
        FlashNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> p),
                new StormFxPacket(p.getId(), type, a, b, power, p.getRandom().nextLong()));
    }

    /** Her eyes and the lightning on her body light up a moment. */
    public static void casting(ServerPlayer p) {
        fx(p, StormFxPacket.CAST, p.position(), p.position(), 1.0F);
    }

    private static Vec3 hand(ServerPlayer p) {
        Vec3 look = p.getLookAngle();
        Vec3 side = new Vec3(-look.z, 0.0D, look.x).normalize().scale(-0.35D);
        return p.getEyePosition().add(look.scale(0.5D)).add(side).add(0.0D, -0.35D, 0.0D);
    }

    private static HitResult aim(ServerPlayer p, double range) {
        Vec3 eye = p.getEyePosition();
        Vec3 end = eye.add(p.getLookAngle().scale(range));
        HitResult block = p.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
                p));
        if (block.getType() != HitResult.Type.MISS) {
            end = block.getLocation();
        }
        EntityHitResult ent = ProjectileUtil.getEntityHitResult(p, eye, end,
                p.getBoundingBox().expandTowards(end.subtract(eye)).inflate(1.0D),
                e -> e instanceof LivingEntity && e.isAlive() && e != p, eye.distanceToSqr(end));
        return ent != null ? ent : block;
    }

    /** Where the aim meets the ground (or a creature's feet). */
    private static Vec3 ground(ServerPlayer p, HitResult hit) {
        Vec3 at = hit instanceof EntityHitResult eh ? eh.getEntity().position() : hit.getLocation();
        Vec3 down = at.add(0.0D, -24.0D, 0.0D);
        HitResult g = p.level().clip(new ClipContext(at.add(0.0D, 0.5D, 0.0D), down, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.ANY, p));
        return g.getType() == HitResult.Type.MISS ? at : g.getLocation();
    }

    /** The sky over a point: up to the first roof, or 40 blocks. */
    private static Vec3 sky(ServerLevel level, Vec3 at, Entity who) {
        Vec3 top = at.add(0.0D, 40.0D, 0.0D);
        HitResult roof = level.clip(new ClipContext(at.add(0.0D, 2.5D, 0.0D), top, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, who));
        return roof.getType() == HitResult.Type.MISS ? top : roof.getLocation().add(0.0D, -0.2D, 0.0D);
    }

    private static void hurt(ServerPlayer p, LivingEntity e, float amount, boolean fire) {
        e.invulnerableTime = 0;
        e.hurt(p.damageSources().indirectMagic(p, p), amount);
        if (fire) {
            e.setSecondsOnFire(3);
        }
    }

    private static boolean foe(ServerPlayer p, Entity e) {
        return e instanceof LivingEntity l && l.isAlive() && e != p
                && !(e instanceof Player pl && (pl.isCreative() || pl.isSpectator()))
                && !(e instanceof net.minecraft.world.entity.decoration.ArmorStand);
    }

    // ------------------------------------------------------------------
    // Lightning
    // ------------------------------------------------------------------

    /** A bolt from the sky onto a point: damage around, a leap to two more, thunder and a burn mark. */
    static void skyBolt(ServerPlayer p, Vec3 at, float damage, int leaps) {
        ServerLevel level = p.serverLevel();
        Vec3 from = sky(level, at, p);
        fx(p, StormFxPacket.SKY, from, at, 1.0F);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, 2.5F,
                0.9F + level.random.nextFloat() * 0.2F);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.WEATHER, 1.5F, 1.0F);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y + 0.2D, at.z, 30, 0.8D, 0.2D, 0.8D, 0.3D);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y + 0.2D, at.z, 8, 0.6D, 0.1D, 0.6D, 0.02D);
        List<LivingEntity> hit = level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(2.5D, 3.0D, 2.5D),
                e -> foe(p, e));
        for (LivingEntity e : hit) {
            float f = (float) Math.max(0.4D, 1.0D - e.position().distanceTo(at) / 3.5D);
            hurt(p, e, damage * f, true);
        }
        if (leaps > 0) {
            Set<LivingEntity> done = new HashSet<>(hit);
            Vec3 last = at;
            for (int i = 0; i < leaps; i++) {
                LivingEntity next = nearest(p, level, last, 8.0D, done);
                if (next == null) {
                    break;
                }
                done.add(next);
                Vec3 to = next.position().add(0.0D, next.getBbHeight() * 0.5D, 0.0D);
                fx(p, StormFxPacket.ARC, last.add(0.0D, 0.3D, 0.0D), to, 0.8F);
                hurt(p, next, damage * 0.5F, false);
                last = to;
            }
        }
    }

    private static LivingEntity nearest(ServerPlayer p, ServerLevel level, Vec3 at, double r, Set<LivingEntity> not) {
        LivingEntity best = null;
        double bd = r * r;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(r),
                e -> foe(p, e) && !not.contains(e))) {
            double d = e.position().distanceToSqr(at);
            if (d < bd) {
                bd = d;
                best = e;
            }
        }
        return best;
    }

    private static void strike(ServerPlayer p) {
        casting(p);
        HitResult hit = aim(p, RANGE);
        skyBolt(p, ground(p, hit), 9.0F * power(), 2);
        com.flash.superman.SupermanAbilities.anim(p, "none");
    }

    /** The stream from her hands (every 3 ticks while R is held; 5 s on end, then it cools down). */
    private static void beam(ServerPlayer p) {
        if (ScarletAbilities.cooling(p, "storm_beam") || ScarletAbilities.cooling(p, "storm_beam_tick")) {
            return;
        }
        long now = p.level().getGameTime();
        ScarletAbilities.start(p, "storm_beam_tick", 2);
        long[] streak = BEAM.computeIfAbsent(p.getUUID(), k -> new long[] {now, now});
        if (now - streak[1] > 10L) {
            streak[0] = now;
        }
        streak[1] = now;
        if (now - streak[0] > 100L) {
            ScarletAbilities.start(p, "storm_beam", com.flash.scarlet.ScarletCooldowns.ticks("storm_beam"));
            BEAM.remove(p.getUUID());
            return;
        }
        casting(p);
        HitResult hit = aim(p, 40.0D);
        Vec3 to = hit.getLocation();
        fx(p, StormFxPacket.BEAM, hand(p), to, hit.getType() == HitResult.Type.BLOCK ? 1.0F : 0.0F);
        if (p.tickCount % 6 == 0) {
            p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, 0.6F,
                    2.0F);
        }
        if (hit instanceof EntityHitResult eh && eh.getEntity() instanceof LivingEntity e) {
            hurt(p, e, 1.0F * power(), false);
        }
        p.serverLevel().sendParticles(ParticleTypes.ELECTRIC_SPARK, to.x, to.y, to.z, 4, 0.2D, 0.2D, 0.2D, 0.2D);
    }

    /** Chain lightning from her hands: up to six foes, each 15 % weaker. */
    private static void chain(ServerPlayer p) {
        casting(p);
        ServerLevel level = p.serverLevel();
        HitResult hit = aim(p, 24.0D);
        LivingEntity first = hit instanceof EntityHitResult eh && eh.getEntity() instanceof LivingEntity l ? l
                : nearest(p, level, hit.getLocation(), 6.0D, new HashSet<>());
        Vec3 from = hand(p);
        level.playSound(null, p.blockPosition(), SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 1.2F, 1.4F);
        if (first == null) {
            fx(p, StormFxPacket.ARC, from, hit.getLocation(), 1.0F);
            return;
        }
        Set<LivingEntity> done = new HashSet<>();
        LivingEntity cur = first;
        float dmg = 6.0F * power();
        for (int i = 0; i < 6 && cur != null; i++) {
            done.add(cur);
            Vec3 to = cur.position().add(0.0D, cur.getBbHeight() * 0.5D, 0.0D);
            fx(p, StormFxPacket.ARC, from, to, 1.0F);
            hurt(p, cur, dmg, false);
            cur.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 2));
            dmg *= 0.85F;
            from = to;
            cur = nearest(p, level, to, 10.0D, done);
        }
    }

    // ------------------------------------------------------------------
    // Winds
    // ------------------------------------------------------------------

    private static void gust(ServerPlayer p) {
        casting(p);
        ServerLevel level = p.serverLevel();
        Vec3 look = p.getLookAngle();
        Vec3 eye = p.getEyePosition();
        level.playSound(null, p.blockPosition(), SoundEvents.ENDER_DRAGON_FLAP, SoundSource.PLAYERS, 1.6F, 1.3F);
        for (int i = 0; i < 40; i++) {
            Vec3 d = look.add((level.random.nextDouble() - 0.5D) * 0.6D, (level.random.nextDouble() - 0.5D) * 0.4D,
                    (level.random.nextDouble() - 0.5D) * 0.6D).normalize();
            level.sendParticles(ParticleTypes.CLOUD, eye.x + look.x, eye.y + look.y - 0.3D, eye.z + look.z, 0, d.x,
                    d.y, d.z, 1.2D);
        }
        for (Entity e : level.getEntities(p, p.getBoundingBox().expandTowards(look.scale(16.0D)).inflate(3.0D))) {
            Vec3 to = e.position().subtract(p.position());
            double dist = to.length();
            if (dist > 16.0D || dist < 0.1D || to.normalize().dot(look) < 0.6D) {
                continue;
            }
            double f = 1.0D - dist / 18.0D;
            e.setDeltaMovement(e.getDeltaMovement().add(look.scale(2.6D * f)).add(0.0D, 0.6D * f, 0.0D));
            e.hurtMarked = true;
            e.clearFire();
            if (e instanceof LivingEntity l && foe(p, l)) {
                hurt(p, l, 2.0F * power() * (float) f, false);
            }
        }
    }

    private static final class Tornado {
        final ServerPlayer owner;
        final ServerLevel level;
        Vec3 at;
        int ticks;

        Tornado(ServerPlayer owner, Vec3 at) {
            this.owner = owner;
            this.level = owner.serverLevel();
            this.at = at;
        }
    }

    private static final List<Tornado> TORNADOES = new ArrayList<>();

    private static void tornado(ServerPlayer p) {
        casting(p);
        Vec3 at = ground(p, aim(p, 40.0D));
        TORNADOES.add(new Tornado(p, at));
        p.serverLevel().playSound(null, at.x, at.y, at.z, SoundEvents.ELYTRA_FLYING, SoundSource.PLAYERS, 2.0F, 0.6F);
    }

    private static void tickTornado(Tornado t) {
        ServerLevel level = t.level;
        t.ticks++;
        // the funnel: rising rings, wider at the top
        for (int i = 0; i < 14; i++) {
            double h = (t.ticks * 0.6D + i * 1.1D) % 14.0D;
            double r = 0.6D + h * 0.32D;
            double a = t.ticks * 0.55D + i * 1.7D + h * 0.6D;
            level.sendParticles(i % 3 == 0 ? ParticleTypes.POOF : ParticleTypes.CLOUD, t.at.x + Math.cos(a) * r,
                    t.at.y + h, t.at.z + Math.sin(a) * r, 0, -Math.sin(a) * 0.4D, 0.15D, Math.cos(a) * 0.4D, 1.0D);
        }
        if (t.ticks % 9 == 0) {
            double a = level.random.nextDouble() * Math.PI * 2.0D;
            Vec3 from = t.at.add(Math.cos(a) * 1.5D, 3.0D + level.random.nextDouble() * 8.0D, Math.sin(a) * 1.5D);
            Vec3 to = t.at.add(Math.cos(a + 2.0D) * 2.5D, 1.0D + level.random.nextDouble() * 9.0D,
                    Math.sin(a + 2.0D) * 2.5D);
            fx(t.owner, StormFxPacket.ARC, from, to, 0.8F);
        }
        if (t.ticks % 30 == 1) {
            level.playSound(null, t.at.x, t.at.y, t.at.z, SoundEvents.ELYTRA_FLYING, SoundSource.PLAYERS, 1.5F, 0.5F);
        }
        for (Entity e : level.getEntities(t.owner, new AABB(t.at, t.at).inflate(10.0D, 14.0D, 10.0D))) {
            if (e instanceof Player pl && (pl.isCreative() || pl.isSpectator())) {
                continue;
            }
            Vec3 axis = new Vec3(t.at.x - e.getX(), 0.0D, t.at.z - e.getZ());
            double d = axis.length();
            if (d > 10.0D) {
                continue;
            }
            Vec3 in = d > 0.01D ? axis.scale(1.0D / d) : Vec3.ZERO;
            Vec3 around = new Vec3(-in.z, 0.0D, in.x);
            double lift = e.getY() - t.at.y < 12.0D ? 0.22D : -0.05D;
            e.setDeltaMovement(e.getDeltaMovement().scale(0.6D).add(in.scale(0.25D)).add(around.scale(0.5D))
                    .add(0.0D, lift, 0.0D));
            e.hurtMarked = true;
            e.fallDistance = 0.0F;
            if (t.ticks % 10 == 0 && e instanceof LivingEntity l && foe(t.owner, l)) {
                hurt(t.owner, l, 0.8F * power(), false);
            }
        }
    }

    // ------------------------------------------------------------------
    // Weather
    // ------------------------------------------------------------------

    private static void weather(ServerPlayer p) {
        casting(p);
        ServerLevel level = p.serverLevel();
        if (!level.dimensionType().hasSkyLight() || level.dimensionType().hasCeiling()) {
            p.displayClientMessage(Component.translatable("message.flash.storm.no_sky"), true);
            return;
        }
        String now;
        if (level.isThundering()) {
            level.setWeatherParameters(12000, 0, false, false);
            now = "clear";
        } else if (level.isRaining()) {
            level.setWeatherParameters(0, 6000, true, true);
            now = "thunder";
        } else {
            level.setWeatherParameters(0, 6000, true, false);
            now = "rain";
        }
        fx(p, StormFxPacket.SKY, p.position().add(0.0D, 40.0D, 0.0D), p.position().add(0.0D, 2.2D, 0.0D), 0.6F);
        level.playSound(null, p.blockPosition(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, 2.0F, 0.7F);
        p.displayClientMessage(Component.translatable("message.flash.storm.weather." + now), true);
    }

    private static final Map<UUID, Long> FURY = new HashMap<>();

    private static void fury(ServerPlayer p) {
        casting(p);
        ServerLevel level = p.serverLevel();
        FURY.put(p.getUUID(), level.getGameTime() + 240L);
        if (level.dimensionType().hasSkyLight() && !level.dimensionType().hasCeiling()) {
            level.setWeatherParameters(0, 400, true, true);
        }
        fx(p, StormFxPacket.SKY, p.position().add(0.0D, 40.0D, 0.0D), p.position(), 1.2F);
        level.playSound(null, p.blockPosition(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, 3.0F, 0.6F);
    }

    private static void tickFury(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        if (level.getGameTime() % 8 != 0) {
            return;
        }
        if (level.getGameTime() % 24 == 0) {
            casting(p);
        }
        List<LivingEntity> foes = level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(24.0D),
                e -> foe(p, e) && (e instanceof Enemy || e == p.getLastHurtByMob()
                        || e instanceof net.minecraft.world.entity.Mob m && m.getTarget() == p));
        if (foes.isEmpty()) {
            // the storm still strikes around her
            double a = level.random.nextDouble() * Math.PI * 2.0D;
            double r = 6.0D + level.random.nextDouble() * 14.0D;
            Vec3 at = p.position().add(Math.cos(a) * r, 0.0D, Math.sin(a) * r);
            HitResult g = level.clip(new ClipContext(at.add(0.0D, 10.0D, 0.0D), at.add(0.0D, -20.0D, 0.0D),
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, p));
            if (g.getType() != HitResult.Type.MISS && level.random.nextInt(2) == 0) {
                skyBolt(p, g.getLocation(), 3.0F * power(), 0);
            }
            return;
        }
        LivingEntity t = foes.get(level.random.nextInt(foes.size()));
        skyBolt(p, t.position(), 6.0F * power(), 0);
    }

    // ------------------------------------------------------------------
    // Ice
    // ------------------------------------------------------------------

    private record Hail(ServerPlayer owner, Vec3 at, long until) {
    }

    private static final List<Hail> HAIL = new ArrayList<>();

    private static void hail(ServerPlayer p) {
        casting(p);
        Vec3 at = ground(p, aim(p, 48.0D));
        HAIL.add(new Hail(p, at, p.level().getGameTime() + 60L));
        p.serverLevel().playSound(null, at.x, at.y, at.z, SoundEvents.GLASS_BREAK, SoundSource.WEATHER, 1.2F, 0.6F);
    }

    private static void tickHail(Hail h) {
        ServerLevel level = h.owner.serverLevel();
        var ice = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.ICE.defaultBlockState());
        for (int i = 0; i < 12; i++) {
            double x = h.at.x + (level.random.nextDouble() - 0.5D) * 8.0D;
            double z = h.at.z + (level.random.nextDouble() - 0.5D) * 8.0D;
            level.sendParticles(ice, x, h.at.y + 8.0D, z, 0, 0.0D, -1.0D, 0.0D, 1.2D);
            level.sendParticles(ParticleTypes.SNOWFLAKE, x, h.at.y + 6.0D, z, 0, 0.0D, -0.9D, 0.0D, 1.0D);
        }
        if (level.getGameTime() % 5 == 0) {
            level.playSound(null, h.at.x, h.at.y, h.at.z, SoundEvents.GLASS_HIT, SoundSource.WEATHER, 0.8F,
                    0.8F + level.random.nextFloat() * 0.6F);
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class,
                    new AABB(h.at, h.at).inflate(4.0D, 4.0D, 4.0D), e -> foe(h.owner, e))) {
                hurt(h.owner, e, 0.7F * power(), false);
                e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1));
            }
        }
    }

    private static void frost(ServerPlayer p) {
        casting(p);
        ServerLevel level = p.serverLevel();
        double r = 10.0D;
        level.playSound(null, p.blockPosition(), SoundEvents.POWDER_SNOW_STEP, SoundSource.PLAYERS, 2.0F, 0.5F);
        level.playSound(null, p.blockPosition(), SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1.2F, 0.5F);
        for (int i = 0; i < 120; i++) {
            double a = i * Math.PI * 2.0D / 120.0D;
            level.sendParticles(ParticleTypes.SNOWFLAKE, p.getX(), p.getY() + 0.4D, p.getZ(), 0, Math.cos(a), 0.05D,
                    Math.sin(a), 0.8D);
        }
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(r),
                e -> foe(p, e) && e.distanceToSqr(p) <= r * r)) {
            hurt(p, e, 3.0F * power(), false);
            e.setTicksFrozen(Math.max(e.getTicksFrozen(), e.getTicksRequiredToFreeze() + 160));
            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 3));
            e.clearFire();
        }
        // the water around turns to ice (frosted: it melts back)
        BlockPos c = p.blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(c.offset(-10, -3, -10), c.offset(10, 2, 10))) {
            if (pos.distSqr(c) > r * r) {
                continue;
            }
            if (level.getBlockState(pos).is(Blocks.WATER) && level.getFluidState(pos).isSource()
                    && level.getBlockState(pos.above()).isAir()) {
                level.setBlockAndUpdate(pos, Blocks.FROSTED_ICE.defaultBlockState());
                level.scheduleTick(pos, Blocks.FROSTED_ICE, 60 + level.random.nextInt(60));
            }
        }
    }

    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        for (Iterator<Tornado> it = TORNADOES.iterator(); it.hasNext(); ) {
            Tornado t = it.next();
            if (t.ticks >= 160 || t.owner.isRemoved()) {
                it.remove();
                continue;
            }
            tickTornado(t);
        }
        for (Iterator<Hail> it = HAIL.iterator(); it.hasNext(); ) {
            Hail h = it.next();
            if (h.owner.isRemoved() || h.owner.level().getGameTime() > h.until()) {
                it.remove();
                continue;
            }
            tickHail(h);
        }
        if (!FURY.isEmpty()) {
            for (Iterator<Map.Entry<UUID, Long>> it = FURY.entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<UUID, Long> en = it.next();
                ServerPlayer p = event.getServer().getPlayerList().getPlayer(en.getKey());
                if (p == null || !StormPowers.has(p) || p.level().getGameTime() > en.getValue()) {
                    it.remove();
                    continue;
                }
                tickFury(p);
            }
        }
    }
}
