package com.flash.spider;

import com.flash.FlashMod;
import com.flash.network.FlashNetwork;
import com.flash.network.HeroFxPacket;
import com.flash.network.HeroSyncPacket;
import com.flash.scarlet.ScarletAbilities;
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
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

/**
 * Spider-Man's powers (R / Z in his modes, SpiderClient; cooldowns shown as the Scarlet Witch's):
 * <ul>
 * <li><b>Webs</b>: R a web shot (whoever it hits is stuck in a cocoon of web); Z a web net thrown in front of
 * him, catching everyone in it.</li>
 * <li><b>Pull</b>: R yanks a creature to him on a web line; Z a web-zip: a line shot to where he looks pulls
 * him there.</li>
 * <li><b>Combat</b>: R a flying kick (he leaps at whatever is in front of him); Z the spider-sense heightened:
 * 10 s seeing every enemy around, slowing them, and dodging most attacks.</li>
 * </ul>
 * Passive: the spider-sense warns him (the squiggles over his head) of creatures coming for him and arrows on
 * their way; he dodges some arrows. His swing line (SpiderLinePacket) is shown to the others from here.
 */
@Mod.EventBusSubscriber(modid = FlashMod.MODID)
public final class SpiderAbilities {

    public static final int WEB_SHOT = 0;
    public static final int WEB_NET = 1;
    public static final int YANK = 2;
    public static final int ZIP = 3;
    public static final int KICK = 4;
    public static final int SENSE = 5;
    /** The suit picked in the K menu (value: its index). */
    public static final int SUIT = 9;

    // what the clients draw (HeroFxPacket types)
    public static final int FX_SHOT = 0;
    public static final int FX_NET = 1;
    public static final int FX_YANK = 2;
    public static final int FX_ZIP = 3;
    public static final int FX_LINE = 4;
    public static final int FX_LINE_OFF = 5;
    public static final int FX_WEBBED = 6;
    public static final int FX_SENSE = 7;
    public static final int FX_KICK = 8;
    public static final int FX_BITE = 9;
    public static final int FX_DODGE = 10;

    /** Stuck in his web: entity -> game time it breaks free. */
    private static final Map<UUID, Long> WEBBED = new HashMap<>();
    private static final Map<UUID, LivingEntity> WEBBED_E = new HashMap<>();
    /** The flying kick under way: player -> ticks left; and who it already hit. */
    private static final Map<UUID, Integer> KICKING = new HashMap<>();
    private static final Map<UUID, Set<UUID>> KICKED = new HashMap<>();
    /** The heightened spider-sense: player -> game time it ends. */
    private static final Map<UUID, Long> SENSING = new HashMap<>();
    /** Last warning of the passive sense: player -> game time. */
    private static final Map<UUID, Long> WARNED = new HashMap<>();

    private SpiderAbilities() {
    }

    public static void act(ServerPlayer p, int action, int value) {
        if (!SpiderPowers.has(p) || p.isSpectator()) {
            return;
        }
        switch (action) {
            case WEB_SHOT -> {
                if (ScarletAbilities.ready(p, "web_shot")) {
                    webShot(p);
                }
            }
            case WEB_NET -> {
                if (ScarletAbilities.ready(p, "web_net")) {
                    webNet(p);
                }
            }
            case YANK -> {
                if (!ScarletAbilities.cooling(p, "yank") && yank(p)) {
                    ScarletAbilities.start(p, "yank", com.flash.scarlet.ScarletCooldowns.ticks("yank"));
                }
            }
            case ZIP -> {
                if (!ScarletAbilities.cooling(p, "zip") && zip(p)) {
                    ScarletAbilities.start(p, "zip", com.flash.scarlet.ScarletCooldowns.ticks("zip"));
                }
            }
            case KICK -> {
                if (ScarletAbilities.ready(p, "spider_kick")) {
                    kick(p);
                }
            }
            case SENSE -> {
                if (ScarletAbilities.ready(p, "sense")) {
                    sense(p);
                }
            }
            case SUIT -> SpiderPowers.setSuit(p, value);
            default -> {
            }
        }
    }

    public static void fx(ServerPlayer p, int type, Vec3 a, Vec3 b, float power, int target) {
        FlashNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> p),
                new HeroFxPacket(HeroSyncPacket.SPIDER, p.getId(), type, a, b, power, target));
    }

    /** His right wrist, where the webs come out. */
    public static Vec3 hand(Player p) {
        Vec3 look = p.getLookAngle();
        Vec3 side = new Vec3(-look.z, 0.0D, look.x).normalize().scale(0.35D);
        return p.getEyePosition().add(look.scale(0.4D)).add(side).add(0.0D, -0.4D, 0.0D);
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
                p.getBoundingBox().expandTowards(end.subtract(eye)).inflate(inflate),
                e -> foe(p, e), eye.distanceToSqr(end));
        return ent != null ? ent : block;
    }

    static boolean foe(ServerPlayer p, Entity e) {
        return e instanceof LivingEntity l && l.isAlive() && e != p
                && !(e instanceof Player pl && (pl.isCreative() || pl.isSpectator()))
                && !(e instanceof net.minecraft.world.entity.decoration.ArmorStand);
    }

    private static void hurt(ServerPlayer p, LivingEntity e, float amount) {
        e.invulnerableTime = 0;
        e.hurt(p.damageSources().playerAttack(p), amount);
    }

    // ------------------------------------------------------------------
    // Webs
    // ------------------------------------------------------------------

    /** Caught in his web: stuck where it is, wrapped in a cocoon (drawn by the clients). */
    public static void web(ServerPlayer p, LivingEntity e, int ticks) {
        long until = e.level().getGameTime() + ticks;
        WEBBED.put(e.getUUID(), until);
        WEBBED_E.put(e.getUUID(), e);
        e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, 6, false, false));
        e.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, ticks, 2, false, false));
        e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, ticks, 1, false, false));
        if (e instanceof Mob m) {
            m.getNavigation().stop();
        }
        fx(p, FX_WEBBED, e.position(), e.position(), ticks, e.getId());
    }

    public static boolean webbed(Entity e) {
        Long until = WEBBED.get(e.getUUID());
        return until != null && e.level().getGameTime() < until;
    }

    private static void webShot(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        HitResult hit = aim(p, 48.0D, 0.6D);
        Vec3 from = hand(p);
        Vec3 to = hit.getType() == HitResult.Type.MISS ? p.getEyePosition().add(p.getLookAngle().scale(48.0D))
                : hit.getLocation();
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.SLIME_SQUISH_SMALL, SoundSource.PLAYERS, 0.9F,
                1.6F);
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENDER_PEARL_THROW, SoundSource.PLAYERS, 0.4F,
                1.9F);
        if (hit instanceof EntityHitResult eh && eh.getEntity() instanceof LivingEntity e) {
            to = e.position().add(0.0D, e.getBbHeight() * 0.6D, 0.0D);
            hurt(p, e, 2.0F);
            web(p, e, 90);
            level.playSound(null, e.getX(), e.getY(), e.getZ(), SoundEvents.SLIME_BLOCK_PLACE, SoundSource.PLAYERS,
                    1.0F, 1.2F);
        }
        fx(p, FX_SHOT, from, to, hit.getType() == HitResult.Type.MISS ? 0.0F : 1.0F, -1);
    }

    private static void webNet(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        Vec3 eye = p.getEyePosition();
        Vec3 look = p.getLookAngle();
        List<LivingEntity> in = level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(13.0D),
                e -> foe(p, e));
        int caught = 0;
        for (LivingEntity e : in) {
            Vec3 to = e.position().add(0.0D, e.getBbHeight() * 0.5D, 0.0D).subtract(eye);
            double d = to.length();
            if (d > 13.0D || d < 0.01D || to.normalize().dot(look) < 0.72D) {
                continue;
            }
            if (!p.hasLineOfSight(e)) {
                continue;
            }
            hurt(p, e, 3.0F);
            web(p, e, 130);
            e.setDeltaMovement(e.getDeltaMovement().add(look.x * 0.5D, 0.1D, look.z * 0.5D));
            caught++;
        }
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.SLIME_BLOCK_FALL, SoundSource.PLAYERS, 1.2F,
                1.5F);
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENDER_DRAGON_FLAP, SoundSource.PLAYERS, 0.5F,
                2.0F);
        fx(p, FX_NET, hand(p), look, caught, -1);
    }

    // ------------------------------------------------------------------
    // Pull
    // ------------------------------------------------------------------

    private static boolean yank(ServerPlayer p) {
        HitResult hit = aim(p, 32.0D, 0.8D);
        if (!(hit instanceof EntityHitResult eh) || !(eh.getEntity() instanceof LivingEntity e)) {
            return false;
        }
        Vec3 dir = p.position().add(0.0D, 0.6D, 0.0D).subtract(e.position());
        double d = dir.length();
        Vec3 v = dir.normalize().scale(Math.min(2.2D, 0.5D + d * 0.13D)).add(0.0D, 0.35D + d * 0.01D, 0.0D);
        // the big ones barely move: the line pulls him a little toward them instead
        double heavy = e.getBbWidth() * e.getBbHeight();
        if (heavy > 4.0D) {
            v = v.scale(0.25D);
            p.setDeltaMovement(p.getDeltaMovement().add(dir.normalize().scale(-0.9D)).add(0.0D, 0.3D, 0.0D));
            p.hurtMarked = true;
        }
        e.setDeltaMovement(v);
        e.hurtMarked = true;
        hurt(p, e, 2.0F);
        if (e instanceof Mob m) {
            m.getNavigation().stop();
        }
        p.serverLevel().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.FISHING_BOBBER_RETRIEVE,
                SoundSource.PLAYERS, 1.0F, 1.4F);
        fx(p, FX_YANK, hand(p), e.position().add(0.0D, e.getBbHeight() * 0.6D, 0.0D), 1.0F, e.getId());
        return true;
    }

    private static boolean zip(ServerPlayer p) {
        Vec3 eye = p.getEyePosition();
        Vec3 end = eye.add(p.getLookAngle().scale(56.0D));
        HitResult hit = p.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        if (hit.getType() == HitResult.Type.MISS) {
            return false;
        }
        Vec3 at = hit.getLocation();
        Vec3 dir = at.subtract(p.position());
        double d = dir.length();
        Vec3 v = dir.normalize().scale(Math.min(3.0D, 0.9D + d * 0.09D)).add(0.0D, 0.45D, 0.0D);
        p.setDeltaMovement(v);
        p.hurtMarked = true;
        p.fallDistance = 0.0F;
        p.serverLevel().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.FISHING_BOBBER_THROW,
                SoundSource.PLAYERS, 1.0F, 1.3F);
        fx(p, FX_ZIP, hand(p), at, 1.0F, -1);
        return true;
    }

    // ------------------------------------------------------------------
    // Combat
    // ------------------------------------------------------------------

    private static void kick(ServerPlayer p) {
        Vec3 look = p.getLookAngle();
        p.setDeltaMovement(look.scale(1.7D).add(0.0D, 0.28D, 0.0D));
        p.hurtMarked = true;
        p.fallDistance = 0.0F;
        KICKING.put(p.getUUID(), 12);
        KICKED.put(p.getUUID(), new HashSet<>());
        p.serverLevel().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP,
                SoundSource.PLAYERS, 1.0F, 0.7F);
        fx(p, FX_KICK, p.position(), look, 1.0F, -1);
    }

    private static void sense(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        SENSING.put(p.getUUID(), level.getGameTime() + 200L);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(28.0D),
                e -> foe(p, e) && (e instanceof net.minecraft.world.entity.monster.Enemy
                        || e instanceof Mob m && m.getTarget() == p))) {
            e.addEffect(new MobEffectInstance(MobEffects.GLOWING, 200, 0, false, false));
            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 1, false, false));
        }
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS,
                1.4F, 1.6F);
        fx(p, FX_SENSE, p.position(), p.position(), 2.0F, -1);
    }

    public static boolean sensing(Player p) {
        Long until = SENSING.get(p.getUUID());
        return until != null && p.level().getGameTime() < until;
    }

    // ------------------------------------------------------------------
    // The swing line (moved on his own client)
    // ------------------------------------------------------------------

    public static void line(ServerPlayer p, boolean on, Vec3 anchor) {
        if (!SpiderPowers.has(p)) {
            return;
        }
        p.fallDistance = 0.0F;
        if (on) {
            if (anchor.distanceToSqr(p.position()) > 100.0D * 100.0D) {
                return;
            }
            p.serverLevel().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.FISHING_BOBBER_THROW,
                    SoundSource.PLAYERS, 0.6F, 1.6F);
        }
        fx(p, on ? FX_LINE : FX_LINE_OFF, anchor, anchor, 1.0F, -1);
    }

    // ------------------------------------------------------------------
    // Every tick: the webbed, the kicks, the sense
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        for (Iterator<Map.Entry<UUID, LivingEntity>> it = WEBBED_E.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, LivingEntity> en = it.next();
            LivingEntity e = en.getValue();
            Long until = WEBBED.get(en.getKey());
            if (e == null || !e.isAlive() || e.isRemoved() || until == null || e.level().getGameTime() >= until) {
                it.remove();
                WEBBED.remove(en.getKey());
                continue;
            }
            // stuck: it keeps falling, it does not walk
            Vec3 v = e.getDeltaMovement();
            e.setDeltaMovement(v.x * 0.2D, Math.min(v.y, 0.0D), v.z * 0.2D);
            if (e instanceof Mob m) {
                m.getNavigation().stop();
                m.setAggressive(false);
            }
        }
        for (Iterator<Map.Entry<UUID, Integer>> it = KICKING.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Integer> en = it.next();
            ServerPlayer p = event.getServer().getPlayerList().getPlayer(en.getKey());
            int left = en.getValue() - 1;
            if (p == null || left <= 0) {
                it.remove();
                KICKED.remove(en.getKey());
                continue;
            }
            en.setValue(left);
            p.fallDistance = 0.0F;
            Set<UUID> done = KICKED.computeIfAbsent(p.getUUID(), k -> new HashSet<>());
            Vec3 look = p.getLookAngle();
            for (LivingEntity e : p.serverLevel().getEntitiesOfClass(LivingEntity.class,
                    p.getBoundingBox().inflate(1.3D).expandTowards(look.scale(0.8D)), e -> foe(p, e))) {
                if (!done.add(e.getUUID())) {
                    continue;
                }
                hurt(p, e, 10.0F);
                e.knockback(1.6D, -look.x, -look.z);
                e.setDeltaMovement(e.getDeltaMovement().add(0.0D, 0.35D, 0.0D));
                e.hurtMarked = true;
                p.serverLevel().sendParticles(ParticleTypes.SWEEP_ATTACK, e.getX(), e.getY() + e.getBbHeight() * 0.6D,
                        e.getZ(), 1, 0.0D, 0.0D, 0.0D, 0.0D);
                p.serverLevel().playSound(null, e.getX(), e.getY(), e.getZ(), SoundEvents.PLAYER_ATTACK_KNOCKBACK,
                        SoundSource.PLAYERS, 1.2F, 0.8F);
                // he bounces off what he kicked
                p.setDeltaMovement(look.scale(-0.4D).add(0.0D, 0.45D, 0.0D));
                p.hurtMarked = true;
                it.remove();
                KICKED.remove(p.getUUID());
                break;
            }
        }
        long time = event.getServer().overworld().getGameTime();
        if (time % 5 != 0) {
            return;
        }
        SENSING.values().removeIf(t -> t < time - 20L);
        for (ServerPlayer p : event.getServer().getPlayerList().getPlayers()) {
            if (SpiderPowers.has(p) && !p.isSpectator()) {
                passiveSense(p);
            }
        }
    }

    /** The spider-sense tingles: a creature coming for him, an arrow on its way. */
    private static void passiveSense(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        long now = level.getGameTime();
        Long last = WARNED.get(p.getUUID());
        if (last != null && now - last < 60L) {
            return;
        }
        LivingEntity threat = null;
        for (Mob m : level.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(18.0D),
                m -> m.isAlive() && m.getTarget() == p)) {
            threat = m;
            break;
        }
        boolean arrow = false;
        if (threat == null) {
            for (Projectile pr : level.getEntitiesOfClass(Projectile.class, p.getBoundingBox().inflate(12.0D),
                    pr -> pr.getOwner() != p && pr.getDeltaMovement().lengthSqr() > 0.04D)) {
                Vec3 to = p.position().add(0.0D, 1.0D, 0.0D).subtract(pr.position());
                if (to.normalize().dot(pr.getDeltaMovement().normalize()) > 0.9D) {
                    arrow = true;
                    break;
                }
            }
        }
        if (threat == null && !arrow) {
            return;
        }
        WARNED.put(p.getUUID(), now);
        if (threat != null) {
            threat.addEffect(new MobEffectInstance(MobEffects.GLOWING, 50, 0, false, false));
        }
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS,
                1.2F, 1.9F);
        fx(p, FX_SENSE, p.position(), p.position(), 1.0F, threat != null ? threat.getId() : -1);
    }

    /**
     * The sense makes him dodge: some arrows always (40 %), and with the heightened sense most arrows (85 %) and
     * many blows (60 %).
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onAttack(LivingAttackEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || !SpiderPowers.has(p) || event.getSource().getEntity() == p) {
            return;
        }
        boolean projectile = event.getSource().getDirectEntity() instanceof Projectile;
        boolean melee = !projectile && event.getSource().getDirectEntity() instanceof LivingEntity;
        if (!projectile && !melee) {
            return;
        }
        boolean heightened = sensing(p);
        float chance = projectile ? (heightened ? 0.85F : 0.4F) : (heightened ? 0.6F : 0.0F);
        if (p.getRandom().nextFloat() >= chance) {
            return;
        }
        event.setCanceled(true);
        Entity from = event.getSource().getDirectEntity();
        Vec3 away = p.position().subtract(from.position()).multiply(1.0D, 0.0D, 1.0D);
        Vec3 side = new Vec3(-away.z, 0.0D, away.x);
        side = side.lengthSqr() < 1.0E-4D ? new Vec3(1.0D, 0.0D, 0.0D) : side.normalize();
        if (p.getRandom().nextBoolean()) {
            side = side.scale(-1.0D);
        }
        p.setDeltaMovement(side.scale(0.7D).add(0.0D, 0.3D, 0.0D));
        p.hurtMarked = true;
        if (projectile) {
            from.discard();
        }
        p.serverLevel().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.PLAYER_ATTACK_NODAMAGE,
                SoundSource.PLAYERS, 1.0F, 1.5F);
        fx(p, FX_DODGE, p.position(), side, 1.0F, -1);
    }

    @SubscribeEvent
    public static void onLeave(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        KICKING.remove(id);
        KICKED.remove(id);
        SENSING.remove(id);
        WARNED.remove(id);
    }
}
