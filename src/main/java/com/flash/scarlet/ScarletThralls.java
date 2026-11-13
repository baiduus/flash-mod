package com.flash.scarlet;

import com.flash.FlashMod;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The minds the Scarlet Witch holds:
 * <ul>
 * <li><b>allies</b>: creatures that follow her and attack whoever hits her (or whatever she hits), never her nor
 * each other; with {@code fury} they attack every creature near them instead;</li>
 * <li><b>puppets</b>: players walked by her will toward the point she looks at, their eyes turned there, unable to
 * hit or use anything.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = FlashMod.MODID)
public final class ScarletThralls {

    private static final class Ally {
        final UUID owner;
        long until;
        final boolean fury;

        Ally(UUID owner, long until, boolean fury) {
            this.owner = owner;
            this.until = until;
            this.fury = fury;
        }
    }

    private record Puppet(UUID owner, long until) {
    }

    /** By entity UUID. */
    private static final Map<UUID, Ally> ALLIES = new HashMap<>();
    private static final Map<UUID, Puppet> PUPPETS = new HashMap<>();

    private ScarletThralls() {
    }

    /** The creature serves her for {@code ticks}. */
    public static void ally(ServerPlayer owner, Mob mob, int ticks, boolean fury) {
        ALLIES.put(mob.getUUID(), new Ally(owner.getUUID(), owner.level().getGameTime() + ticks, fury));
        if (mob.getTarget() == owner) {
            mob.setTarget(null);
        }
        mob.setPersistenceRequired();
        ServerLevel level = owner.serverLevel();
        level.sendParticles(ScarletPowers.RED, mob.getX(), mob.getEyeY(), mob.getZ(), 20, 0.3D, 0.3D, 0.3D, 0.0D);
    }

    public static void puppet(ServerPlayer owner, ServerPlayer victim, int ticks) {
        PUPPETS.put(victim.getUUID(), new Puppet(owner.getUUID(), owner.level().getGameTime() + ticks));
        victim.addEffect(new MobEffectInstance(MobEffects.CONFUSION, ticks + 40, 0, false, false));
        victim.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.flash.scarlet_puppet",
                owner.getDisplayName()), true);
    }

    public static boolean isAllyOf(Entity e, UUID owner) {
        Ally a = ALLIES.get(e.getUUID());
        return a != null && a.owner.equals(owner);
    }

    public static boolean puppeted(Entity e) {
        return PUPPETS.containsKey(e.getUUID());
    }

    /** Everything she holds goes free (she is no longer the Scarlet Witch). */
    public static void release(ServerPlayer owner) {
        ALLIES.values().removeIf(a -> a.owner.equals(owner.getUUID()));
        PUPPETS.values().removeIf(p -> p.owner.equals(owner.getUUID()));
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || (ALLIES.isEmpty() && PUPPETS.isEmpty())) {
            return;
        }
        var server = event.getServer();
        for (Iterator<Map.Entry<UUID, Ally>> it = ALLIES.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Ally> en = it.next();
            Ally a = en.getValue();
            ServerPlayer owner = server.getPlayerList().getPlayer(a.owner);
            Mob mob = find(server, en.getKey());
            if (mob == null || !mob.isAlive() || owner == null || mob.level().getGameTime() > a.until
                    || !ScarletPowers.has(owner)) {
                if (mob != null && mob.getTarget() == owner) {
                    mob.setTarget(null);
                }
                it.remove();
                continue;
            }
            tickAlly(mob, owner, a);
        }
        for (Iterator<Map.Entry<UUID, Puppet>> it = PUPPETS.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Puppet> en = it.next();
            ServerPlayer victim = server.getPlayerList().getPlayer(en.getKey());
            ServerPlayer owner = server.getPlayerList().getPlayer(en.getValue().owner());
            if (victim == null || owner == null || !victim.isAlive() || victim.level() != owner.level()
                    || victim.level().getGameTime() > en.getValue().until() || !ScarletPowers.has(owner)) {
                it.remove();
                continue;
            }
            Vec3 eye = owner.getEyePosition();
            Vec3 goal = owner.level().clip(new ClipContext(eye, eye.add(owner.getLookAngle().scale(32.0D)),
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner)).getLocation();
            Vec3 to = new Vec3(goal.x - victim.getX(), 0.0D, goal.z - victim.getZ());
            double d = to.length();
            Vec3 move = d > 1.0D ? to.scale(0.28D / d) : Vec3.ZERO;
            victim.setDeltaMovement(move.x, victim.getDeltaMovement().y, move.z);
            victim.hurtMarked = true;
            if (victim.tickCount % 3 == 0) {
                victim.lookAt(EntityAnchorArgument.Anchor.EYES, goal);
            }
            if (victim.tickCount % 5 == 0) {
                victim.serverLevel().sendParticles(ScarletPowers.RED, victim.getX(), victim.getEyeY() + 0.3D,
                        victim.getZ(), 4, 0.25D, 0.15D, 0.25D, 0.0D);
            }
        }
    }

    private static void tickAlly(Mob mob, ServerPlayer owner, Ally a) {
        LivingEntity target = mob.getTarget();
        // in fury they turn on everything but her, her other servants included (the hysteria: on each other)
        if (target == owner || (!a.fury && target != null && isAllyOf(target, a.owner) && !isFury(target))) {
            mob.setTarget(null);
            target = null;
        }
        if (a.fury) {
            if (target == null || !target.isAlive()) {
                LivingEntity near = null;
                double best = 16.0D * 16.0D;
                for (LivingEntity o : mob.level().getEntitiesOfClass(LivingEntity.class,
                        mob.getBoundingBox().inflate(16.0D), o -> o != mob && o != owner && o.isAlive()
                                && (isFury(o) || !isAllyOf(o, a.owner))
                                && !(o instanceof Player p && (p.isCreative() || p.isSpectator()))
                                && !(o instanceof net.minecraft.world.entity.decoration.ArmorStand))) {
                    double d = o.distanceToSqr(mob);
                    if (d < best) {
                        best = d;
                        near = o;
                    }
                }
                if (near != null) {
                    mob.setTarget(near);
                }
            }
        } else {
            // whoever hits her, or whatever she hits
            LivingEntity foe = owner.getLastHurtByMob();
            if (foe == null || owner.tickCount - owner.getLastHurtByMobTimestamp() > 200) {
                foe = owner.getLastHurtMob();
                if (foe != null && owner.tickCount - owner.getLastHurtMobTimestamp() > 200) {
                    foe = null;
                }
            }
            if (foe != null && foe != mob && foe.isAlive() && !isAllyOf(foe, a.owner)) {
                if (mob.getTarget() != foe) {
                    mob.setTarget(foe);
                }
            } else if (target == null && mob instanceof PathfinderMob pm && mob.distanceToSqr(owner) > 10.0D * 10.0D) {
                pm.getNavigation().moveTo(owner, 1.2D);
            }
            if (mob.distanceToSqr(owner) > 48.0D * 48.0D && mob.level() == owner.level()) {
                mob.teleportTo(owner.getX(), owner.getY(), owner.getZ());
            }
        }
        strike(mob, a);
        if (mob.tickCount % 12 == 0 && mob.level() instanceof ServerLevel level) {
            level.sendParticles(ScarletPowers.RED, mob.getX(), mob.getEyeY() + 0.2D, mob.getZ(), 2, 0.15D, 0.1D,
                    0.15D, 0.0D);
        }
    }

    private static boolean isFury(Entity e) {
        Ally a = ALLIES.get(e.getUUID());
        return a != null && a.fury;
    }

    /**
     * Creatures that cannot fight by themselves (a sheep, a cow, a villager...) still go for whom she points at:
     * they run to it and hit it (a hit a second, a little stronger the bigger they are).
     */
    private static void strike(Mob mob, Ally a) {
        LivingEntity t = mob.getTarget();
        if (t == null || !t.isAlive() || mob.getAttribute(Attributes.ATTACK_DAMAGE) != null) {
            return;
        }
        if (mob.tickCount % 10 == 0) {
            mob.getNavigation().moveTo(t, 1.35D);
            mob.getLookControl().setLookAt(t, 30.0F, 30.0F);
        }
        double reach = mob.getBbWidth() * 2.0F * mob.getBbWidth() * 2.0F + t.getBbWidth() + 1.5D;
        long now = mob.level().getGameTime();
        Long last = LAST_HIT.get(mob.getUUID());
        if (mob.distanceToSqr(t) <= reach && (last == null || now - last >= 20L)) {
            LAST_HIT.put(mob.getUUID(), now);
            mob.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
            float dmg = 2.0F + mob.getMaxHealth() * 0.12F;
            t.hurt(mob.damageSources().mobAttack(mob), Math.min(10.0F, dmg));
            Vec3 push = t.position().subtract(mob.position()).normalize().scale(0.4D);
            t.setDeltaMovement(t.getDeltaMovement().add(push.x, 0.2D, push.z));
            t.hurtMarked = true;
        }
    }

    private static final Map<UUID, Long> LAST_HIT = new HashMap<>();

    private static Mob find(net.minecraft.server.MinecraftServer server, UUID id) {
        for (ServerLevel level : server.getAllLevels()) {
            if (level.getEntity(id) instanceof Mob m) {
                return m;
            }
        }
        return null;
    }

    /** An ally never turns on her or on her other allies. */
    @SubscribeEvent
    public static void onTarget(LivingChangeTargetEvent event) {
        Ally a = ALLIES.get(event.getEntity().getUUID());
        LivingEntity t = event.getNewTarget();
        if (a == null || t == null) {
            return;
        }
        if (t.getUUID().equals(a.owner) || (!a.fury && isAllyOf(t, a.owner) && !isFury(t))) {
            event.setCanceled(true);
        }
    }

    /** A puppet cannot hit or use anything. */
    @SubscribeEvent
    public static void onAttack(AttackEntityEvent event) {
        if (puppeted(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onUseItem(PlayerInteractEvent.RightClickItem event) {
        if (puppeted(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onUseBlock(PlayerInteractEvent.RightClickBlock event) {
        if (puppeted(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onBreak(PlayerInteractEvent.LeftClickBlock event) {
        if (puppeted(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onUseEntity(PlayerInteractEvent.EntityInteract event) {
        if (puppeted(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    static AABB around(Entity e, double r) {
        return e.getBoundingBox().inflate(r);
    }
}
