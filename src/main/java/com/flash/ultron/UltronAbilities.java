package com.flash.ultron;

import com.flash.FlashMod;
import com.flash.network.FlashNetwork;
import com.flash.network.HeroFxPacket;
import com.flash.network.HeroSyncPacket;
import com.flash.scarlet.ScarletAbilities;
import com.flash.superman.SupermanAbilities;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.TntBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

/**
 * Ultron's powers: R and Z in his five modes (UltronClient), cooldowns shown as the Scarlet Witch's. All drawn in
 * orange light by UltronVfx.
 * <ul>
 * <li><b>Energy</b>: R a repulsor blast from the palm; Z the Annihilation Beam from the eyes, 2.5 s.</li>
 * <li><b>Combat</b>: R a thruster charge that runs through everything in the way; Z he seizes the creature he looks
 * at, holds it up in his claw and hurls it.</li>
 * <li><b>Legion</b>: R three flying Ultron drones built beside him (up to six); Z an order: all on the creature he
 * looks at (at nothing: back to him, repaired).</li>
 * <li><b>Control</b>: R hacks what he looks at: a creature fights for him for a minute (a player gets a virus), a
 * machine obeys (TNT primed, doors and gates opened or shut, levers pulled, buttons pressed); Z an electromagnetic
 * pulse all around that hurts, throws and paralyses.</li>
 * <li><b>Core</b>: R Ultron Prime: 15 s of overcharge (stronger, faster, his light blazing); Z the core's
 * detonation: he charges for a second in the air and bursts in a wave of orange fire (no block broken).</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = FlashMod.MODID)
public final class UltronAbilities {

    public static final int BLAST = 0;
    public static final int ANNIHILATE = 1;
    public static final int CHARGE = 2;
    public static final int GRAB = 3;
    public static final int LEGION = 4;
    public static final int COMMAND = 5;
    public static final int HACK = 6;
    public static final int EMP = 7;
    public static final int PRIME = 8;
    public static final int NOVA = 9;

    public static final int FX_BLAST = 0;
    public static final int FX_BEAM = 1;
    public static final int FX_CHARGE = 2;
    public static final int FX_GRAB = 3;
    public static final int FX_THROW = 4;
    public static final int FX_BUILD = 5;
    public static final int FX_SHOT = 6;
    public static final int FX_HACK = 7;
    public static final int FX_EMP = 8;
    public static final int FX_PRIME = 9;
    public static final int FX_NOVA_CHARGE = 10;
    public static final int FX_NOVA = 11;
    public static final int FX_TAKEOFF = 12;
    public static final int FX_HIT = 13;

    private static final int BEAM_TICKS = 50;
    private static final int CHARGE_TICKS = 7;
    private static final int GRAB_TICKS = 24;
    private static final int HACK_TICKS = 1200;
    private static final int PRIME_TICKS = 300;
    private static final int NOVA_TICKS = 20;
    private static final int MAX_DRONES = 6;
    public static final double EMP_RADIUS = 10.0D;
    public static final double NOVA_RADIUS = 7.5D;

    private static final Map<UUID, Integer> BEAMS = new HashMap<>();

    private static final class Charge {
        int left = CHARGE_TICKS;
        final Vec3 dir;
        final Set<UUID> struck = new HashSet<>();

        Charge(Vec3 dir) {
            this.dir = dir;
        }
    }

    private static final Map<UUID, Charge> CHARGES = new HashMap<>();

    private static final class Grab {
        final LivingEntity victim;
        int left = GRAB_TICKS;

        Grab(LivingEntity victim) {
            this.victim = victim;
        }
    }

    private static final Map<UUID, Grab> GRABS = new HashMap<>();
    private static final Map<UUID, Integer> NOVAS = new HashMap<>();
    private static final Map<UUID, Long> PRIMES = new HashMap<>();

    private record Hack(UUID owner, long until) {
    }

    private static final Map<UUID, Hack> HACKED = new HashMap<>();

    private UltronAbilities() {
    }

    public static void act(ServerPlayer p, int action, int value) {
        if (!UltronPowers.has(p) || p.isSpectator() || p.isSleeping()) {
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
                    SupermanAbilities.anim(p, "lex_unibeam");
                    sound(p, SoundEvents.BEACON_ACTIVATE, 1.3F, 1.6F);
                    sound(p, SoundEvents.WARDEN_SONIC_CHARGE, 0.8F, 1.8F);
                    UltronPowers.sync(p);
                }
            }
            case CHARGE -> {
                if (!CHARGES.containsKey(p.getUUID()) && ScarletAbilities.ready(p, "u_charge")) {
                    charge(p);
                }
            }
            case GRAB -> {
                if (!GRABS.containsKey(p.getUUID()) && !ScarletAbilities.cooling(p, "u_grab") && grab(p)) {
                    ScarletAbilities.start(p, "u_grab", com.flash.scarlet.ScarletCooldowns.ticks("u_grab"));
                }
            }
            case LEGION -> {
                if (drones(p).size() >= MAX_DRONES) {
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
            case PRIME -> {
                if (!prime(p) && ScarletAbilities.ready(p, "u_prime")) {
                    primeOn(p);
                }
            }
            case NOVA -> {
                if (!NOVAS.containsKey(p.getUUID()) && ScarletAbilities.ready(p, "u_nova")) {
                    NOVAS.put(p.getUUID(), NOVA_TICKS);
                    SupermanAbilities.anim(p, "lex_unibeam");
                    sound(p, SoundEvents.BEACON_POWER_SELECT, 1.5F, 0.5F);
                    sound(p, SoundEvents.WARDEN_SONIC_CHARGE, 1.2F, 0.7F);
                    fx(p, FX_NOVA_CHARGE, p.position(), p.position(), NOVA_TICKS, -1);
                }
            }
            default -> {
            }
        }
    }

    public static boolean beaming(Player p) {
        return BEAMS.containsKey(p.getUUID());
    }

    public static boolean prime(Player p) {
        Long until = PRIMES.get(p.getUUID());
        return until != null && p.level().getGameTime() < until;
    }

    /** Ability power: half again in Ultron Prime. */
    public static float power(Player p) {
        return prime(p) ? 1.5F : 1.0F;
    }

    static void forget(ServerPlayer p) {
        BEAMS.remove(p.getUUID());
        CHARGES.remove(p.getUUID());
        GRABS.remove(p.getUUID());
        NOVAS.remove(p.getUUID());
        PRIMES.remove(p.getUUID());
        HACKED.values().removeIf(h -> h.owner.equals(p.getUUID()));
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    /** On Ultron's side: himself, his drones, what he hacked. */
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

    /** Draws something of Ultron's (or of his drones) for whoever sees it. */
    public static void fx(Entity src, int type, Vec3 a, Vec3 b, float power, int target) {
        HeroFxPacket pkt = new HeroFxPacket(HeroSyncPacket.ULTRON, src.getId(), type, a, b, power, target);
        if (src instanceof ServerPlayer sp) {
            FlashNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> sp), pkt);
        } else {
            FlashNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY.with(() -> src), pkt);
        }
    }

    private static void sound(ServerPlayer p, net.minecraft.sounds.SoundEvent s, float volume, float pitch) {
        p.serverLevel().playSound(null, p.getX(), p.getY(), p.getZ(), s, SoundSource.PLAYERS, volume, pitch);
    }

    static void hurt(Entity by, Player owner, LivingEntity t, float amount) {
        t.invulnerableTime = 0;
        t.hurt(by.damageSources().indirectMagic(by, owner), amount);
    }

    static List<UltronSentry> drones(ServerPlayer p) {
        return p.serverLevel().getEntitiesOfClass(UltronSentry.class, p.getBoundingBox().inflate(128.0D),
                s -> p.getUUID().equals(s.owner()));
    }

    // ------------------------------------------------------------------
    // Energy
    // ------------------------------------------------------------------

    private static void blast(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        HitResult hit = SupermanAbilities.trace(p, 48.0D);
        Vec3 to = hit.getLocation();
        SupermanAbilities.anim(p, "lex_repulsor");
        sound(p, SoundEvents.FIRECHARGE_USE, 1.0F, 0.7F);
        sound(p, SoundEvents.BLAZE_SHOOT, 0.8F, 0.6F);
        level.playSound(null, to.x, to.y, to.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.7F, 1.5F);
        boolean struck = false;
        if (hit instanceof EntityHitResult eh && eh.getEntity() instanceof LivingEntity t && foe(p, t)) {
            hurt(p, p, t, 10.0F * power(p));
            Vec3 push = p.getLookAngle().scale(1.4D);
            t.setDeltaMovement(push.x, 0.45D, push.z);
            t.hurtMarked = true;
            struck = true;
        }
        // a small burst where it lands: whatever is close takes some of it
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(to, to).inflate(1.8D),
                e -> foe(p, e))) {
            if (!struck || e != ((EntityHitResult) hit).getEntity()) {
                hurt(p, p, e, 4.0F * power(p));
            }
        }
        fx(p, FX_BLAST, palm(p), to, struck ? 1.0F : 0.0F, -1);
    }

    private static void beamTick(ServerPlayer p) {
        Integer left = BEAMS.get(p.getUUID());
        if (left == null) {
            return;
        }
        if (left <= 0 || !p.isAlive()) {
            BEAMS.remove(p.getUUID());
            SupermanAbilities.anim(p, "none");
            UltronPowers.sync(p);
            return;
        }
        BEAMS.put(p.getUUID(), left - 1);
        ServerLevel level = p.serverLevel();
        HitResult hit = SupermanAbilities.trace(p, 40.0D);
        Vec3 to = hit.getLocation();
        if (left % 2 == 0) {
            fx(p, FX_BEAM, p.getEyePosition(), to, hit.getType() == HitResult.Type.MISS ? 0.0F : 1.0F, -1);
        }
        if (left % 10 == 0) {
            sound(p, SoundEvents.BEACON_AMBIENT, 1.2F, 2.0F);
        }
        if (left % 3 == 0) {
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(to, to).inflate(1.2D),
                    e -> foe(p, e))) {
                hurt(p, p, e, 4.0F * power(p));
                e.setSecondsOnFire(4);
            }
            if (hit instanceof BlockHitResult bh && level.getGameRules().getBoolean(
                    net.minecraft.world.level.GameRules.RULE_MOBGRIEFING) && left % 9 == 0) {
                BlockPos above = bh.getBlockPos().relative(bh.getDirection());
                if (level.isEmptyBlock(above)) {
                    level.setBlockAndUpdate(above, net.minecraft.world.level.block.BaseFireBlock.getState(level,
                            above));
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Combat
    // ------------------------------------------------------------------

    private static void charge(ServerPlayer p) {
        Vec3 dir = p.getLookAngle().normalize();
        CHARGES.put(p.getUUID(), new Charge(dir));
        SupermanAbilities.anim(p, "lex_power_thrust");
        sound(p, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, 1.4F, 0.6F);
        sound(p, SoundEvents.ENDER_DRAGON_FLAP, 1.0F, 1.4F);
        fx(p, FX_CHARGE, p.position(), p.position().add(dir.scale(14.0D)), CHARGE_TICKS, -1);
    }

    private static void chargeTick(ServerPlayer p) {
        Charge c = CHARGES.get(p.getUUID());
        if (c == null) {
            return;
        }
        if (--c.left < 0 || !p.isAlive()) {
            CHARGES.remove(p.getUUID());
            p.setDeltaMovement(p.getDeltaMovement().scale(0.25D));
            p.hurtMarked = true;
            return;
        }
        p.setDeltaMovement(c.dir.scale(2.2D));
        p.hurtMarked = true;
        p.fallDistance = 0.0F;
        ServerLevel level = p.serverLevel();
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(1.4D)
                .expandTowards(c.dir.scale(2.2D)), e -> foe(p, e) && !c.struck.contains(e.getUUID()))) {
            c.struck.add(e.getUUID());
            hurt(p, p, e, 12.0F * power(p));
            e.setDeltaMovement(c.dir.x * 2.0D, 0.8D, c.dir.z * 2.0D);
            e.hurtMarked = true;
            level.playSound(null, e.getX(), e.getY(), e.getZ(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.8F,
                    0.6F);
            fx(p, FX_HIT, e.position().add(0.0D, e.getBbHeight() * 0.5D, 0.0D), Vec3.ZERO, 1.0F, e.getId());
        }
    }

    private static boolean grab(ServerPlayer p) {
        HitResult hit = SupermanAbilities.trace(p, 10.0D);
        if (!(hit instanceof EntityHitResult eh) || !(eh.getEntity() instanceof LivingEntity t) || !foe(p, t)) {
            p.displayClientMessage(Component.translatable("message.flash.ultron.grab_none"), true);
            return false;
        }
        GRABS.put(p.getUUID(), new Grab(t));
        SupermanAbilities.anim(p, "lex_repulsor");
        sound(p, SoundEvents.CHAIN_PLACE, 1.4F, 0.6F);
        sound(p, SoundEvents.IRON_GOLEM_ATTACK, 1.0F, 0.8F);
        fx(p, FX_GRAB, palm(p), t.getEyePosition(), GRAB_TICKS, t.getId());
        return true;
    }

    private static void grabTick(ServerPlayer p) {
        Grab g = GRABS.get(p.getUUID());
        if (g == null) {
            return;
        }
        LivingEntity t = g.victim;
        if (!t.isAlive() || t.level() != p.level() || !p.isAlive()) {
            GRABS.remove(p.getUUID());
            return;
        }
        Vec3 look = p.getLookAngle();
        Vec3 hold = p.getEyePosition().add(look.scale(1.6D + t.getBbWidth())).add(0.0D, 0.4D - t.getBbHeight() * 0.5D,
                0.0D);
        if (--g.left > 0) {
            // held up in the claw, struggling
            Vec3 pull = hold.subtract(t.position()).scale(0.5D);
            t.setDeltaMovement(pull);
            t.hurtMarked = true;
            t.fallDistance = 0.0F;
            if (t instanceof Mob m) {
                m.setTarget(null);
            }
            if (g.left % 8 == 0) {
                hurt(p, p, t, 2.0F * power(p));
            }
            return;
        }
        GRABS.remove(p.getUUID());
        t.setDeltaMovement(look.scale(3.0D).add(0.0D, 0.6D, 0.0D));
        t.hurtMarked = true;
        hurt(p, p, t, 10.0F * power(p));
        SupermanAbilities.anim(p, "lex_repulsor");
        sound(p, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.4F, 0.6F);
        sound(p, SoundEvents.FIREWORK_ROCKET_LAUNCH, 1.0F, 0.6F);
        fx(p, FX_THROW, hold, hold.add(look.scale(10.0D)), 1.0F, t.getId());
        THROWN.put(t.getUUID(), new Thrown(t, p, 40));
    }

    /** A thrown creature: whatever it crashes into takes the blow too. */
    private record Thrown(LivingEntity body, ServerPlayer by, int ticks) {
    }

    private static final Map<UUID, Thrown> THROWN = new HashMap<>();

    private static void thrownTick() {
        for (Iterator<Map.Entry<UUID, Thrown>> it = THROWN.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Thrown> en = it.next();
            Thrown th = en.getValue();
            LivingEntity b = th.body();
            if (!b.isAlive() || th.ticks() <= 0 || (b.tickCount > 2 && (b.onGround() || b.horizontalCollision))) {
                if (b.isAlive() && (b.onGround() || b.horizontalCollision) && b.level() instanceof ServerLevel level) {
                    level.playSound(null, b.getX(), b.getY(), b.getZ(), SoundEvents.GENERIC_EXPLODE,
                            SoundSource.PLAYERS, 0.8F, 1.2F);
                    level.sendParticles(ParticleTypes.EXPLOSION, b.getX(), b.getY() + 0.5D, b.getZ(), 1, 0, 0, 0, 0);
                    hurt(th.by(), th.by(), b, 6.0F);
                    for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, b.getBoundingBox()
                            .inflate(2.0D), e -> e != b && foe(th.by(), e))) {
                        hurt(th.by(), th.by(), e, 6.0F);
                    }
                }
                it.remove();
                continue;
            }
            en.setValue(new Thrown(b, th.by(), th.ticks() - 1));
        }
    }

    // ------------------------------------------------------------------
    // Legion
    // ------------------------------------------------------------------

    private static void legion(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        int room = Math.min(3, MAX_DRONES - drones(p).size());
        for (int i = 0; i < room; i++) {
            double a = p.getYRot() * Math.PI / 180.0D + Math.PI + (i - 1) * 0.9D;
            Vec3 at = p.position().add(-Math.sin(a) * 2.4D, 1.2D + i * 0.3D, Math.cos(a) * 2.4D);
            UltronSentry s = UltronSentry.create(level, p);
            s.moveTo(at.x, at.y, at.z, p.getYRot(), 0.0F);
            level.addFreshEntity(s);
            fx(p, FX_BUILD, at, at, 1.0F, s.getId());
        }
        sound(p, SoundEvents.IRON_GOLEM_REPAIR, 1.4F, 0.6F);
        sound(p, SoundEvents.BEACON_POWER_SELECT, 1.0F, 1.5F);
        p.displayClientMessage(Component.translatable("message.flash.ultron.legion"), true);
    }

    private static void command(ServerPlayer p) {
        HitResult hit = SupermanAbilities.trace(p, 64.0D);
        List<UltronSentry> all = drones(p);
        if (all.isEmpty()) {
            p.displayClientMessage(Component.translatable("message.flash.ultron.no_legion"), true);
            return;
        }
        if (hit instanceof EntityHitResult eh && eh.getEntity() instanceof LivingEntity t && foe(p, t)) {
            for (UltronSentry s : all) {
                s.setTarget(t);
                fx(p, FX_HACK, s.getEyePosition(), t.getEyePosition(), 0.0F, t.getId());
            }
            p.displayClientMessage(Component.translatable("message.flash.ultron.command_attack"), true);
        } else {
            for (UltronSentry s : all) {
                s.setTarget(null);
                s.teleportTo(p.getX() + (s.getRandom().nextDouble() - 0.5D) * 4.0D, p.getY() + 1.5D,
                        p.getZ() + (s.getRandom().nextDouble() - 0.5D) * 4.0D);
                s.heal(s.getMaxHealth());
                fx(p, FX_BUILD, s.position(), s.position(), 0.6F, s.getId());
            }
            p.displayClientMessage(Component.translatable("message.flash.ultron.command_recall"), true);
        }
        sound(p, SoundEvents.NOTE_BLOCK_BIT.value(), 1.0F, 0.6F);
    }

    // ------------------------------------------------------------------
    // Control
    // ------------------------------------------------------------------

    private static boolean hack(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        HitResult hit = SupermanAbilities.trace(p, 32.0D);
        if (hit instanceof BlockHitResult bh && hit.getType() == HitResult.Type.BLOCK) {
            return machine(p, bh.getBlockPos());
        }
        if (!(hit instanceof EntityHitResult eh) || !(eh.getEntity() instanceof LivingEntity t) || !foe(p, t)) {
            p.displayClientMessage(Component.translatable("message.flash.ultron.hack_none"), true);
            return false;
        }
        fx(p, FX_HACK, palm(p), t.getEyePosition(), 1.0F, t.getId());
        level.playSound(null, t.getX(), t.getY(), t.getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1.0F,
                1.6F);
        if (t instanceof ServerPlayer victim) {
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
        if (mob instanceof net.minecraft.world.entity.animal.IronGolem golem) {
            golem.setPlayerCreated(true);
        }
        p.displayClientMessage(Component.translatable("message.flash.ultron.hacked", mob.getDisplayName()), true);
        return true;
    }

    /** A machine obeys him: TNT, doors, trapdoors, gates, levers, buttons, lamps... */
    private static boolean machine(ServerPlayer p, BlockPos pos) {
        ServerLevel level = p.serverLevel();
        BlockState s = level.getBlockState(pos);
        boolean done = false;
        if (s.getBlock() instanceof TntBlock) {
            TntBlock.explode(level, pos);
            level.removeBlock(pos, false);
            done = true;
        } else if (s.getBlock() instanceof LeverBlock lever) {
            lever.pull(s, level, pos);
            done = true;
        } else if (s.getBlock() instanceof ButtonBlock button) {
            if (!s.getValue(ButtonBlock.POWERED)) {
                button.press(s, level, pos);
            }
            done = true;
        } else if (s.hasProperty(BlockStateProperties.OPEN)) {
            BlockPos at = pos;
            if (s.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF) && s.getValue(
                    BlockStateProperties.DOUBLE_BLOCK_HALF) == net.minecraft.world.level.block.state.properties
                    .DoubleBlockHalf.UPPER) {
                at = pos.below();
                s = level.getBlockState(at);
            }
            if (s.hasProperty(BlockStateProperties.OPEN)) {
                boolean open = !s.getValue(BlockStateProperties.OPEN);
                level.setBlock(at, s.setValue(BlockStateProperties.OPEN, open), 10);
                if (s.getBlock() instanceof net.minecraft.world.level.block.DoorBlock door) {
                    door.setOpen(p, level, s, at, open);
                }
                level.playSound(null, at, open ? SoundEvents.IRON_DOOR_OPEN : SoundEvents.IRON_DOOR_CLOSE,
                        SoundSource.BLOCKS, 1.0F, 1.0F);
                done = true;
            }
        } else if (s.hasProperty(BlockStateProperties.LIT)) {
            level.setBlock(pos, s.setValue(BlockStateProperties.LIT, !s.getValue(BlockStateProperties.LIT)), 2);
            done = true;
        } else if (s.getBlock() instanceof net.minecraft.world.level.block.DispenserBlock) {
            level.scheduleTick(pos, s.getBlock(), 1);
            done = true;
        } else if (s.getBlock() instanceof net.minecraft.world.level.block.NoteBlock) {
            level.blockEvent(pos, s.getBlock(), 0, 0);
            done = true;
        }
        if (!done) {
            p.displayClientMessage(Component.translatable("message.flash.ultron.hack_none"), true);
            return false;
        }
        Vec3 c = net.minecraft.world.phys.Vec3.atCenterOf(pos);
        fx(p, FX_HACK, palm(p), c, 2.0F, -1);
        level.playSound(null, pos, SoundEvents.NOTE_BLOCK_BIT.value(), SoundSource.BLOCKS, 1.0F, 1.8F);
        p.displayClientMessage(Component.translatable("message.flash.ultron.machine", s.getBlock().getName()), true);
        return true;
    }

    private static void emp(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        double r = EMP_RADIUS * (prime(p) ? 1.3D : 1.0D);
        sound(p, SoundEvents.WARDEN_SONIC_BOOM, 1.4F, 1.4F);
        sound(p, SoundEvents.BEACON_DEACTIVATE, 1.6F, 0.5F);
        fx(p, FX_EMP, p.position().add(0.0D, 1.0D, 0.0D), p.position(), (float) r, -1);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(r),
                e -> e.distanceToSqr(p) <= r * r && foe(p, e))) {
            hurt(p, p, e, 8.0F * power(p));
            Vec3 out = e.position().subtract(p.position());
            out = new Vec3(out.x, 0.0D, out.z);
            out = out.lengthSqr() < 1.0E-4D ? new Vec3(0, 0, 1) : out.normalize();
            e.setDeltaMovement(out.x * 1.5D, 0.55D, out.z * 1.5D);
            e.hurtMarked = true;
            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 5));
            e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 2));
            if (e instanceof Mob m) {
                m.setTarget(null);
                m.getNavigation().stop();
            }
        }
    }

    // ------------------------------------------------------------------
    // Core
    // ------------------------------------------------------------------

    private static void primeOn(ServerPlayer p) {
        PRIMES.put(p.getUUID(), p.level().getGameTime() + PRIME_TICKS);
        p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, PRIME_TICKS, 2, false, false));
        p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, PRIME_TICKS, 2, false, false));
        p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, PRIME_TICKS, 1, false, false));
        p.addEffect(new MobEffectInstance(MobEffects.JUMP, PRIME_TICKS, 1, false, false));
        sound(p, SoundEvents.BEACON_ACTIVATE, 1.6F, 0.6F);
        sound(p, SoundEvents.WITHER_SPAWN, 0.6F, 1.6F);
        fx(p, FX_PRIME, p.position(), p.position(), PRIME_TICKS, -1);
        p.displayClientMessage(Component.translatable("message.flash.ultron.prime"), true);
        UltronPowers.sync(p);
    }

    private static void novaTick(ServerPlayer p) {
        Integer left = NOVAS.get(p.getUUID());
        if (left == null) {
            return;
        }
        if (!p.isAlive()) {
            NOVAS.remove(p.getUUID());
            return;
        }
        // hanging in the air while the core builds up
        p.setDeltaMovement(p.getDeltaMovement().multiply(0.3D, 0.0D, 0.3D).add(0.0D, left > NOVA_TICKS - 4 ? 0.25D
                : 0.0D, 0.0D));
        p.hurtMarked = true;
        p.fallDistance = 0.0F;
        if (left > 0) {
            NOVAS.put(p.getUUID(), left - 1);
            return;
        }
        NOVAS.remove(p.getUUID());
        ServerLevel level = p.serverLevel();
        double r = NOVA_RADIUS * (prime(p) ? 1.3D : 1.0D);
        Vec3 c = p.position().add(0.0D, 1.0D, 0.0D);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 3.0F, 0.6F);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 2.0F, 0.6F);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y, c.z, 1, 0, 0, 0, 0);
        fx(p, FX_NOVA, c, c, (float) r, -1);
        SupermanAbilities.anim(p, "none");
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(r),
                e -> foe(p, e) && e.distanceToSqr(c) <= r * r)) {
            double d = Math.sqrt(e.distanceToSqr(c));
            float dmg = (float) (24.0D * (1.0D - 0.6D * d / r)) * power(p);
            hurt(p, p, e, dmg);
            e.setSecondsOnFire(5);
            Vec3 out = e.position().subtract(c);
            out = out.lengthSqr() < 1.0E-4D ? new Vec3(0, 1, 0) : out.normalize();
            e.setDeltaMovement(out.x * 2.2D, 0.9D, out.z * 2.2D);
            e.hurtMarked = true;
        }
    }

    // ------------------------------------------------------------------
    // ticks
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer p)) {
            return;
        }
        beamTick(p);
        chargeTick(p);
        grabTick(p);
        novaTick(p);
        Long until = PRIMES.get(p.getUUID());
        if (until != null && p.level().getGameTime() >= until) {
            PRIMES.remove(p.getUUID());
            UltronPowers.sync(p);
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (!THROWN.isEmpty()) {
            thrownTick();
        }
        if (HACKED.isEmpty()) {
            return;
        }
        MinecraftServer server = event.getServer();
        List<UUID> gone = new ArrayList<>();
        for (Map.Entry<UUID, Hack> en : HACKED.entrySet()) {
            Hack h = en.getValue();
            ServerPlayer owner = server.getPlayerList().getPlayer(h.owner);
            Mob mob = find(server, en.getKey());
            if (mob == null || !mob.isAlive() || owner == null || !UltronPowers.has(owner)
                    || mob.level().getGameTime() > h.until) {
                if (mob != null && mob.getTarget() == owner) {
                    mob.setTarget(null);
                }
                gone.add(en.getKey());
                continue;
            }
            tickHacked(mob, owner);
        }
        gone.forEach(HACKED::remove);
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
        if (mob.tickCount % 40 == 0) {
            fx(mob, FX_HACK, mob.getEyePosition(), mob.getEyePosition(), 3.0F, mob.getId());
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

    /** (for the client: the drone ids are not needed) */
    static ClipContext clip(Player p, double range) {
        Vec3 eye = p.getEyePosition();
        return new ClipContext(eye, eye.add(p.getLookAngle().scale(range)), ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, p);
    }
}
