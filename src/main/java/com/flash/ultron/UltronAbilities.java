package com.flash.ultron;

import com.flash.FlashMod;
import com.flash.scarlet.ScarletAbilities;
import com.flash.superman.SupermanAbilities;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

/**
 * Ultron's powers (R / Z in his modes, UltronClient; cooldowns shown as the Scarlet Witch's):
 * <ul>
 * <li><b>Energy</b>: R an energy blast from the palm; Z the Annihilation Beam, from the eyes, for 2 s.</li>
 * <li><b>Legion</b>: R three Ultron sentries are built beside him (up to six), fighting for him for a minute; Z an
 * order: they all go for the creature he looks at (looking at nothing: they come back to him, repaired).</li>
 * <li><b>Control</b>: R hacks the creature he looks at: for a minute it fights for him (a player is infected
 * instead: slowed, weakened, dazed); Z an electromagnetic pulse all around that hurts, throws and paralyses.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = FlashMod.MODID)
public final class UltronAbilities {

    public static final int BLAST = 0;
    public static final int ANNIHILATE = 1;
    public static final int LEGION = 2;
    public static final int COMMAND = 3;
    public static final int HACK = 4;
    public static final int EMP = 5;

    private static final int BEAM_TICKS = 40;
    private static final int HACK_TICKS = 1200;
    private static final int MAX_SENTRIES = 6;
    private static final DustParticleOptions RED = new DustParticleOptions(UltronPowers.RED, 1.1F);
    private static final DustParticleOptions RED_BIG = new DustParticleOptions(UltronPowers.RED, 2.0F);
    private static final DustParticleOptions WHITE = new DustParticleOptions(new Vector3f(1.0F, 0.85F, 0.8F), 0.8F);

    /** The Annihilation Beam: player -> ticks left. */
    private static final Map<UUID, Integer> BEAMS = new HashMap<>();

    private record Hack(UUID owner, long until) {
    }

    /** Hacked creatures, by entity UUID. */
    private static final Map<UUID, Hack> HACKED = new HashMap<>();

    private UltronAbilities() {
    }

    public static void act(ServerPlayer p, int action, int value) {
        if (!UltronPowers.has(p) || p.isSpectator()) {
            return;
        }
        switch (action) {
            case BLAST -> {
                if (ScarletAbilities.ready(p, "u_blast")) {
                    blast(p);
                }
            }
            case ANNIHILATE -> {
                if (!beaming(p) && ScarletAbilities.ready(p, "u_beam")) {
                    BEAMS.put(p.getUUID(), BEAM_TICKS);
                    p.serverLevel().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BEACON_ACTIVATE,
                            SoundSource.PLAYERS, 1.2F, 1.8F);
                    UltronPowers.sync(p);
                }
            }
            case LEGION -> {
                if (sentries(p).size() >= MAX_SENTRIES) {
                    p.displayClientMessage(Component.translatable("message.flash.ultron.legion_full"), true);
                } else if (ScarletAbilities.ready(p, "u_legion")) {
                    legion(p);
                }
            }
            case COMMAND -> {
                if (ScarletAbilities.ready(p, "u_command")) {
                    command(p);
                }
            }
            case HACK -> {
                if (!ScarletAbilities.cooling(p, "u_hack") && hack(p)) {
                    ScarletAbilities.start(p, "u_hack", com.flash.scarlet.ScarletCooldowns.ticks("u_hack"));
                }
            }
            case EMP -> {
                if (ScarletAbilities.ready(p, "u_emp")) {
                    emp(p);
                }
            }
            default -> {
            }
        }
    }

    public static boolean beaming(Player p) {
        return BEAMS.containsKey(p.getUUID());
    }

    static void forget(ServerPlayer p) {
        BEAMS.remove(p.getUUID());
        HACKED.values().removeIf(h -> h.owner.equals(p.getUUID()));
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    /** On Ultron's side: himself, his sentries, the creatures he hacked. */
    public static boolean friend(UUID owner, Entity e) {
        if (e == null) {
            return false;
        }
        if (e.getUUID().equals(owner)) {
            return true;
        }
        if (e instanceof UltronSentry s && owner.equals(s.owner())) {
            return true;
        }
        Hack h = HACKED.get(e.getUUID());
        return h != null && h.owner.equals(owner);
    }

    static boolean foe(ServerPlayer p, Entity e) {
        return e instanceof LivingEntity l && l.isAlive() && !friend(p.getUUID(), e)
                && !(e instanceof Player pl && (pl.isCreative() || pl.isSpectator())) && !(e instanceof ArmorStand);
    }

    public static Vec3 palm(Player p) {
        Vec3 look = p.getLookAngle();
        Vec3 side = new Vec3(-look.z, 0.0D, look.x).normalize().scale(0.36D);
        return p.getEyePosition().add(look.scale(0.6D)).add(side).add(0.0D, -0.45D, 0.0D);
    }

    static void line(ServerLevel level, Vec3 a, Vec3 b, DustParticleOptions dust, double step) {
        Vec3 d = b.subtract(a);
        double len = d.length();
        int n = (int) Math.min(160, Math.max(2, len / step));
        for (int i = 0; i <= n; i++) {
            Vec3 at = a.add(d.scale(i / (double) n));
            level.sendParticles(dust, at.x, at.y, at.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
    }

    private static void hurt(ServerPlayer p, LivingEntity t, float amount) {
        t.invulnerableTime = 0;
        t.hurt(p.damageSources().indirectMagic(p, p), amount);
    }

    static List<UltronSentry> sentries(ServerPlayer p) {
        return p.serverLevel().getEntitiesOfClass(UltronSentry.class, p.getBoundingBox().inflate(96.0D),
                s -> p.getUUID().equals(s.owner()));
    }

    // ------------------------------------------------------------------
    // Energy
    // ------------------------------------------------------------------

    private static void blast(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        HitResult hit = SupermanAbilities.trace(p, 40.0D);
        Vec3 from = palm(p);
        Vec3 to = hit.getLocation();
        SupermanAbilities.anim(p, "lex_repulsor");
        line(level, from, to, RED_BIG, 0.35D);
        line(level, from, to, WHITE, 0.7D);
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 1.0F,
                0.6F);
        level.playSound(null, to.x, to.y, to.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.6F, 1.6F);
        level.sendParticles(ParticleTypes.FLASH, to.x, to.y, to.z, 1, 0, 0, 0, 0);
        level.sendParticles(RED_BIG, to.x, to.y, to.z, 20, 0.3D, 0.3D, 0.3D, 0.0D);
        if (hit instanceof EntityHitResult eh && eh.getEntity() instanceof LivingEntity t && foe(p, t)) {
            hurt(p, t, 10.0F);
            Vec3 push = p.getLookAngle().scale(1.3D);
            t.setDeltaMovement(push.x, 0.4D, push.z);
            t.hurtMarked = true;
        }
    }

    private static void beamTick(ServerPlayer p) {
        Integer left = BEAMS.get(p.getUUID());
        if (left == null) {
            return;
        }
        if (left <= 0 || !p.isAlive()) {
            BEAMS.remove(p.getUUID());
            UltronPowers.sync(p);
            return;
        }
        BEAMS.put(p.getUUID(), left - 1);
        ServerLevel level = p.serverLevel();
        HitResult hit = SupermanAbilities.trace(p, 36.0D);
        Vec3 look = p.getLookAngle();
        Vec3 side = new Vec3(-look.z, 0.0D, look.x).normalize().scale(0.09D);
        Vec3 eye = p.getEyePosition().add(look.scale(0.3D));
        Vec3 to = hit.getLocation();
        line(level, eye.add(side), to, RED, 0.45D);
        line(level, eye.subtract(side), to, RED, 0.45D);
        level.sendParticles(ParticleTypes.SMALL_FLAME, to.x, to.y, to.z, 3, 0.1D, 0.1D, 0.1D, 0.01D);
        if (left % 8 == 0) {
            level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, 1.0F,
                    2.0F);
        }
        if (left % 3 == 0 && hit instanceof EntityHitResult eh && eh.getEntity() instanceof LivingEntity t
                && foe(p, t)) {
            hurt(p, t, 3.5F);
            t.setSecondsOnFire(3);
        }
    }

    // ------------------------------------------------------------------
    // Legion
    // ------------------------------------------------------------------

    private static void legion(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        int room = Math.min(3, MAX_SENTRIES - sentries(p).size());
        for (int i = 0; i < room; i++) {
            double a = p.getYRot() * Math.PI / 180.0D + Math.PI + (i - 1) * 0.9D;
            Vec3 at = p.position().add(-Math.sin(a) * 2.2D, 0.2D, Math.cos(a) * 2.2D);
            UltronSentry s = UltronSentry.create(level, p);
            s.moveTo(at.x, at.y, at.z, p.getYRot(), 0.0F);
            level.addFreshEntity(s);
            level.sendParticles(RED_BIG, at.x, at.y + 0.8D, at.z, 30, 0.3D, 0.6D, 0.3D, 0.0D);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y + 0.8D, at.z, 15, 0.3D, 0.6D, 0.3D, 0.1D);
        }
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.IRON_GOLEM_REPAIR, SoundSource.PLAYERS, 1.2F,
                0.6F);
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.0F,
                1.5F);
        p.displayClientMessage(Component.translatable("message.flash.ultron.legion"), true);
    }

    private static void command(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        HitResult hit = SupermanAbilities.trace(p, 48.0D);
        List<UltronSentry> all = sentries(p);
        if (all.isEmpty()) {
            p.displayClientMessage(Component.translatable("message.flash.ultron.no_legion"), true);
            return;
        }
        if (hit instanceof EntityHitResult eh && eh.getEntity() instanceof LivingEntity t && foe(p, t)) {
            for (UltronSentry s : all) {
                s.setTarget(t);
                line(level, s.getEyePosition(), t.getEyePosition(), RED, 0.8D);
            }
            level.sendParticles(RED_BIG, t.getX(), t.getY() + t.getBbHeight() + 0.4D, t.getZ(), 12, 0.2D, 0.1D, 0.2D,
                    0.0D);
            p.displayClientMessage(Component.translatable("message.flash.ultron.command_attack"), true);
        } else {
            for (UltronSentry s : all) {
                s.setTarget(null);
                s.teleportTo(p.getX() + (s.getRandom().nextDouble() - 0.5D) * 3.0D, p.getY(),
                        p.getZ() + (s.getRandom().nextDouble() - 0.5D) * 3.0D);
                s.heal(s.getMaxHealth());
                level.sendParticles(RED, s.getX(), s.getY() + 0.8D, s.getZ(), 15, 0.2D, 0.5D, 0.2D, 0.0D);
            }
            p.displayClientMessage(Component.translatable("message.flash.ultron.command_recall"), true);
        }
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.NOTE_BLOCK_BIT.value(), SoundSource.PLAYERS,
                1.0F, 0.6F);
    }

    // ------------------------------------------------------------------
    // Control
    // ------------------------------------------------------------------

    private static boolean hack(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        HitResult hit = SupermanAbilities.trace(p, 32.0D);
        if (!(hit instanceof EntityHitResult eh) || !(eh.getEntity() instanceof LivingEntity t) || !foe(p, t)) {
            p.displayClientMessage(Component.translatable("message.flash.ultron.hack_none"), true);
            return false;
        }
        line(level, palm(p), t.getEyePosition(), RED, 0.4D);
        level.playSound(null, t.getX(), t.getY(), t.getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1.0F,
                1.6F);
        if (t instanceof ServerPlayer victim) {
            // a player cannot be rewritten, but the virus slows him down
            victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 160, 2));
            victim.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 160, 1));
            victim.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 160, 0));
            victim.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 160, 2));
            victim.displayClientMessage(Component.translatable("message.flash.ultron.infected", p.getDisplayName()),
                    true);
            return true;
        }
        if (!(t instanceof Mob mob)) {
            return false;
        }
        HACKED.put(mob.getUUID(), new Hack(p.getUUID(), level.getGameTime() + HACK_TICKS));
        mob.setPersistenceRequired();
        if (mob.getTarget() == p || friend(p.getUUID(), mob.getTarget())) {
            mob.setTarget(null);
        }
        level.sendParticles(RED_BIG, mob.getX(), mob.getEyeY(), mob.getZ(), 25, 0.3D, 0.3D, 0.3D, 0.0D);
        p.displayClientMessage(Component.translatable("message.flash.ultron.hacked", mob.getDisplayName()), true);
        return true;
    }

    private static void emp(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        double r = 9.0D;
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 1.2F,
                1.6F);
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1.5F,
                0.5F);
        for (int ring = 1; ring <= 3; ring++) {
            double rr = r * ring / 3.0D;
            int n = (int) (rr * 10);
            for (int i = 0; i < n; i++) {
                double a = i * Math.PI * 2.0D / n;
                level.sendParticles(ring == 3 ? RED_BIG : RED, p.getX() + Math.cos(a) * rr, p.getY() + 0.8D,
                        p.getZ() + Math.sin(a) * rr, 1, 0.0D, 0.1D, 0.0D, 0.0D);
            }
        }
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.getX(), p.getY() + 1.0D, p.getZ(), 60, r * 0.4D, 0.8D,
                r * 0.4D, 0.3D);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(r),
                e -> e.distanceToSqr(p) <= r * r && foe(p, e))) {
            hurt(p, e, 7.0F);
            Vec3 out = e.position().subtract(p.position());
            out = new Vec3(out.x, 0.0D, out.z);
            out = out.lengthSqr() < 1.0E-4D ? new Vec3(0, 0, 1) : out.normalize();
            e.setDeltaMovement(out.x * 1.4D, 0.5D, out.z * 1.4D);
            e.hurtMarked = true;
            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 3));
            e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 1));
            if (e instanceof Mob m) {
                m.setTarget(null);
            }
        }
    }

    // ------------------------------------------------------------------
    // ticks
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.player instanceof ServerPlayer p && !BEAMS.isEmpty()) {
            beamTick(p);
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || HACKED.isEmpty()) {
            return;
        }
        MinecraftServer server = event.getServer();
        for (Iterator<Map.Entry<UUID, Hack>> it = HACKED.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Hack> en = it.next();
            Hack h = en.getValue();
            ServerPlayer owner = server.getPlayerList().getPlayer(h.owner);
            Mob mob = find(server, en.getKey());
            if (mob == null || !mob.isAlive() || owner == null || !UltronPowers.has(owner)
                    || mob.level().getGameTime() > h.until) {
                if (mob != null && mob.getTarget() == owner) {
                    mob.setTarget(null);
                }
                it.remove();
                continue;
            }
            tickHacked(mob, owner);
        }
    }

    private static Mob find(MinecraftServer server, UUID id) {
        for (ServerLevel level : server.getAllLevels()) {
            if (level.getEntity(id) instanceof Mob m) {
                return m;
            }
        }
        return null;
    }

    private static void tickHacked(Mob mob, ServerPlayer owner) {
        LivingEntity target = mob.getTarget();
        if (target != null && friend(owner.getUUID(), target)) {
            mob.setTarget(null);
            target = null;
        }
        LivingEntity foe = owner.getLastHurtByMob();
        if (foe == null || owner.tickCount - owner.getLastHurtByMobTimestamp() > 200) {
            foe = owner.getLastHurtMob();
            if (foe != null && owner.tickCount - owner.getLastHurtMobTimestamp() > 200) {
                foe = null;
            }
        }
        if (foe != null && foe != mob && foe.isAlive() && !friend(owner.getUUID(), foe)) {
            if (mob.getTarget() != foe) {
                mob.setTarget(foe);
            }
        } else if (target == null && mob.tickCount % 20 == 0) {
            // hunts the monsters near its master
            LivingEntity near = null;
            double best = 14.0D * 14.0D;
            for (LivingEntity o : mob.level().getEntitiesOfClass(LivingEntity.class, mob.getBoundingBox()
                    .inflate(14.0D), o -> o instanceof Enemy && o != mob && o.isAlive()
                    && !friend(owner.getUUID(), o))) {
                double d = o.distanceToSqr(mob);
                if (d < best) {
                    best = d;
                    near = o;
                }
            }
            if (near != null) {
                mob.setTarget(near);
            } else if (mob instanceof PathfinderMob pm && mob.distanceToSqr(owner) > 10.0D * 10.0D) {
                pm.getNavigation().moveTo(owner, 1.2D);
            }
        }
        if (mob.distanceToSqr(owner) > 48.0D * 48.0D && mob.level() == owner.level()) {
            mob.teleportTo(owner.getX(), owner.getY(), owner.getZ());
        }
        if (mob.tickCount % 10 == 0 && mob.level() instanceof ServerLevel level) {
            level.sendParticles(RED, mob.getX(), mob.getY() + mob.getBbHeight() + 0.3D, mob.getZ(), 2, 0.15D, 0.05D,
                    0.15D, 0.0D);
        }
    }

    /** Nothing on his side turns on him or on each other. */
    @SubscribeEvent
    public static void onTarget(LivingChangeTargetEvent event) {
        Entity e = event.getEntity();
        LivingEntity t = event.getNewTarget();
        if (t == null) {
            return;
        }
        UUID owner = null;
        if (e instanceof UltronSentry s) {
            owner = s.owner();
        } else {
            Hack h = HACKED.get(e.getUUID());
            if (h != null) {
                owner = h.owner;
            }
        }
        if (owner != null && friend(owner, t)) {
            event.setCanceled(true);
        }
    }
}
