package com.flash.infinity;

import com.flash.FlashMod;
import com.flash.infinity.entity.PowerShot;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

/**
 * The Power Stone: its level (0 to 11, each one bought; the wheel changes it while Damage Control is on), the damage
 * that doubles with each level (infinite when maxed with the five other stones), the energy blast, the beam, the super
 * punch, the rockets, the cyclone, the meteor storm, and what it does to whoever holds it unprotected.
 */
@Mod.EventBusSubscriber(modid = FlashMod.MODID)
public final class PowerStone {

    private static final int COLOR = 0xFF00FF;

    private PowerStone() {
    }

    private static InfPowers.Ability levelAbility(InfinityServer.State s) {
        InfPowers.Power pw = InfPowers.get("power_stone");
        if (pw == null || !s.powers.contains("power_stone")) {
            return null;
        }
        for (InfPowers.Ability a : pw.abilities.values()) {
            if ("flash:power_level".equals(a.type)) {
                return a;
            }
        }
        return null;
    }

    /** The highest level bought (the first n unlock abilities). */
    static int maxUnlocked(InfinityServer.State s, InfPowers.Ability lvl) {
        int n = 0;
        for (String id : InfinityPowers.strings(lvl.json.get("unlock_abilities"))) {
            InfPowers.Ability u = lvl.power.ability(id);
            if (u == null || !InfinityPowers.unlocked(s, u)) {
                break;
            }
            n++;
        }
        return Math.min(n, lvl.json.has("max_level") ? lvl.json.get("max_level").getAsInt() : 11);
    }

    public static int level(ServerPlayer p) {
        InfinityServer.State s = InfinityServer.state(p);
        InfPowers.Ability lvl = levelAbility(s);
        if (lvl == null) {
            return 0;
        }
        return Math.max(0, Math.min(maxUnlocked(s, lvl), InfinityServer.score(p, "DamageCycle")));
    }

    public static boolean maxed(ServerPlayer p) {
        InfinityServer.State s = InfinityServer.state(p);
        InfPowers.Ability lvl = levelAbility(s);
        if (lvl == null || level(p) < (lvl.json.has("max_level") ? lvl.json.get("max_level").getAsInt() : 11)) {
            return false;
        }
        for (String pw : InfinityPowers.strings(lvl.json.get("max_requires_powers"))) {
            if (!s.powers.contains(pw.substring(pw.indexOf(':') + 1))) {
                return false;
            }
        }
        return true;
    }

    /** 2^level (infinite when maxed). */
    public static float damage(ServerPlayer p) {
        return maxed(p) ? 2.1474836E9F : (float) Math.pow(2.0D, level(p));
    }

    static void levelTick(ServerPlayer p, InfinityServer.State s, InfPowers.Ability a) {
        int max = maxUnlocked(s, a);
        int cur = InfinityServer.score(p, "DamageCycle");
        if (cur > max || cur < 0) {
            InfinityServer.setScore(p, "DamageCycle", Math.max(0, Math.min(max, cur)));
        }
    }

    static void change(ServerPlayer p, InfPowers.Ability a) {
        InfinityServer.State s = InfinityServer.state(p);
        InfPowers.Ability lvl = levelAbility(s);
        if (lvl == null) {
            return;
        }
        int v = Math.max(0, Math.min(maxUnlocked(s, lvl), InfinityServer.score(p, "DamageCycle")
                + a.json.get("amount").getAsInt()));
        InfinityServer.setScore(p, "DamageCycle", v);
        p.level().playSound(null, p.blockPosition(), SoundEvents.COMPARATOR_CLICK, SoundSource.PLAYERS, 0.6F,
                0.8F + v * 0.08F);
        InfinityServer.sync(p, true);
    }

    /** scale_attack_damage: the fists too. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onHurt(LivingHurtEvent event) {
        if (event.getSource().getEntity() instanceof ServerPlayer p && event.getSource().is(DamageTypes.PLAYER_ATTACK)
                && event.getSource().getDirectEntity() == p && levelAbility(InfinityServer.state(p)) != null
                && InfinityPowers.unlocked(InfinityServer.state(p), levelAbility(InfinityServer.state(p)))) {
            float m = damage(p);
            if (m > 1.0F) {
                event.setAmount(Math.min(2.1474836E9F, event.getAmount() * m));
            }
        }
    }

    // ------------------------------------------------------------------
    // Shots
    // ------------------------------------------------------------------

    /** From the hand the stone is on (the off hand with the Gauntlet). */
    static Vec3 hand(ServerPlayer p) {
        Vec3 look = p.getLookAngle();
        boolean off = InfinityGauntlet.isGauntlet(p.getOffhandItem());
        double side = (p.getMainArm() == net.minecraft.world.entity.HumanoidArm.RIGHT) != off ? -0.36D : 0.36D;
        Vec3 sv = new Vec3(-look.z, 0.0D, look.x).normalize().scale(-side);
        return p.getEyePosition().add(look.scale(0.6D)).add(sv).add(0.0D, -0.35D, 0.0D);
    }

    static void energyBlast(ServerPlayer p, InfPowers.Ability a) {
        float base = Math.max(16.0F, a.json.has("damage") ? a.json.get("damage").getAsFloat() : 3.0F);
        float speed = a.json.has("speed") ? a.json.get("speed").getAsFloat() : 1.5F;
        Vec3 aim = p.getLookAngle();
        net.minecraft.world.entity.LivingEntity locked = InfinityBeams.lock(p.level(), p, p.getEyePosition(), aim, 48.0D);
        if (locked != null) {
            aim = locked.position().add(0.0D, locked.getBbHeight() * 0.5D, 0.0D).subtract(hand(p)).normalize();
        }
        PowerShot s = PowerShot.shoot(p, hand(p), aim, Math.max(1.4F, speed),
                Math.min(2.1474836E9F, base * Math.max(1.0F, damage(p))), COLOR, false);
        s.homing = true;
        SpaceStone.sound(p.level(), p.position(), "energy_blast", 1.0F, 1.0F);
        p.swing(InteractionHand.OFF_HAND, true);
    }

    static void rocketBurst(ServerPlayer p, InfPowers.Ability a) {
        int n = a.json.has("rockets") ? a.json.get("rockets").getAsInt() : 5;
        float scale = level(p) / 11.0F;
        for (int i = 0; i < n; i++) {
            float spread = (i - (n - 1) / 2.0F) * 12.0F;
            Vec3 dir = p.getLookAngle().yRot(spread * (float) Math.PI / 180.0F).add(0.0D, 0.15D, 0.0D);
            PowerShot.shoot(p, hand(p), dir, 1.0F, 5.0F * scale, COLOR, true);
        }
        SpaceStone.sound(p.level(), p.position(), "power_stone_explode", 0.6F, 1.4F);
    }

    // ------------------------------------------------------------------
    // The super punch
    // ------------------------------------------------------------------

    private static final class Punch {
        final ServerPlayer p;
        Vec3 pos;
        final Vec3 dir;
        double left;
        final double speed;
        final double size;
        final float damage;
        final double knock;
        final Set<UUID> hit = new HashSet<>();

        Punch(ServerPlayer p, Vec3 pos, Vec3 dir, double range, double speed, double size, float damage, double knock) {
            this.p = p;
            this.pos = pos;
            this.dir = dir;
            this.left = range;
            this.speed = speed;
            this.size = size;
            this.damage = damage;
            this.knock = knock;
        }
    }

    private static final java.util.List<Punch> PUNCHES = new java.util.ArrayList<>();

    static void superPunch(ServerPlayer p, InfPowers.Ability a) {
        double mult = 1.0D + (Math.max(0.0D, (a.json.has("max_multiplier") ? a.json.get("max_multiplier").getAsDouble()
                : 5.0D) - 1.0D)) * level(p) / 11.0D;
        float base = a.json.has("damage") ? a.json.get("damage").getAsFloat() : 3.0F;
        PUNCHES.add(new Punch(p, hand(p), p.getLookAngle(), 12.0D * mult, 1.5D * mult, 1.5D * mult,
                Math.min(2.1474836E9F, base * damage(p)), 0.5D * mult));
        // the recoil
        p.setDeltaMovement(p.getDeltaMovement().subtract(p.getLookAngle().scale(0.5D * mult)));
        p.hurtMarked = true;
        p.swing(InteractionHand.MAIN_HAND, true);
        p.level().playSound(null, p.blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.8F, 1.4F);
    }

    // ------------------------------------------------------------------
    // The cyclone (controlled at full power, or the unprotected stone's)
    // ------------------------------------------------------------------

    static void cyclone(ServerPlayer p, InfPowers.Ability a, InfinityServer.State s) {
        ServerLevel level = p.serverLevel();
        int t = s.timer(a.key());
        int rings = Math.min(10, 1 + t / 10);
        double radius = 6.0D;
        for (int r = 0; r < rings; r++) {
            double rr = radius * (0.4D + 0.6D * r / 10.0D);
            for (int k = 0; k < 6; k++) {
                double ang = (t * 0.25D + k * Math.PI / 3.0D + r * 0.4D);
                double x = p.getX() + Math.cos(ang) * rr;
                double z = p.getZ() + Math.sin(ang) * rr;
                level.sendParticles(p.getRandom().nextInt(3) == 0 ? new DustParticleOptions(new Vector3f(0.76F,
                        0.16F, 0.82F), 2.0F) : ParticleTypes.SMOKE, x, p.getY() + r * 0.9D, z, 1, 0.1D, 0.1D, 0.1D,
                        0.02D);
            }
        }
        if (t % 20 == 0) {
            Vec3 dir = new Vec3(p.getRandom().nextGaussian(), 0.8D, p.getRandom().nextGaussian());
            PowerShot.shoot(p, p.position().add(0.0D, 2.0D, 0.0D), dir, 1.0F, 5.0F * level(p) / 11.0F, COLOR, true);
        }
        if (t % 40 == 0) {
            SpaceStone.sound(level, p.position(), "power_stone_beam1", 0.8F, 0.7F);
        }
    }

    /** Unprotected (the stone held bare, no Gauntlet, no Orb): its radiation burns everything round its holder. */
    static void unprotected(ServerPlayer p, InfPowers.Ability a, InfinityServer.State s) {
        cyclone(p, a, s);
        int t = s.timer(a.key());
        if (t % 10 == 0 && p.serverLevel().getGameRules().getBoolean(
                net.minecraft.world.level.GameRules.RULE_MOBGRIEFING)) {
            BlockPos c = p.blockPosition();
            int r = Math.min(6, 1 + t / 20);
            for (BlockPos pos : BlockPos.betweenClosed(c.offset(-r, -1, -r), c.offset(r, r, r))) {
                if (pos.distSqr(c) <= r * r && p.getRandom().nextInt(6) == 0) {
                    BlockState st = p.level().getBlockState(pos);
                    if (!st.isAir() && st.getDestroySpeed(p.level(), pos) >= 0.0F && !pos.equals(c.below())) {
                        p.level().destroyBlock(pos, false);
                    }
                }
            }
        }
        for (LivingEntity e : p.serverLevel().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(6.0D),
                e -> e != p)) {
            e.hurt(p.damageSources().magic(), 2.0F);
        }
    }

    // ------------------------------------------------------------------
    // The meteor storm
    // ------------------------------------------------------------------

    private static final Map<UUID, Integer> STORMS = new HashMap<>();
    private static final String METEOR = "flash_meteor";

    static void meteorStorm(ServerPlayer p, InfPowers.Ability a) {
        if (STORMS.containsKey(p.getUUID())) {
            return;
        }
        ServerLevel level = p.serverLevel();
        Vec3 eye = p.getEyePosition();
        BlockHitResult hit = level.clip(new ClipContext(eye, eye.add(p.getLookAngle().scale(200.0D)),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        if (hit.getType() == HitResult.Type.MISS) {
            return;
        }
        Vec3 at = hit.getLocation();
        int size = (a.json.has("size") ? a.json.get("size").getAsInt() : 4) + level(p);
        STORMS.put(p.getUUID(), 0);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.WITHER_SPAWN, SoundSource.PLAYERS, 2.0F, 0.5F);
        // the moon, broken into pieces on the way down
        for (int i = 0; i < size * 4; i++) {
            double ox = p.getRandom().nextGaussian() * size;
            double oz = p.getRandom().nextGaussian() * size;
            double y = Math.min(level.getMaxBuildHeight() - 2, at.y + 60.0D + p.getRandom().nextInt(40));
            FallingBlockEntity m = FallingBlockEntity.fall(level, BlockPos.containing(at.x + ox, y, at.z + oz),
                    p.getRandom().nextBoolean() ? InfinityItems.DWARF_STAR_METEOR.get().defaultBlockState()
                            : Blocks.MAGMA_BLOCK.defaultBlockState());
            m.dropItem = false;
            m.setDeltaMovement((at.x - m.getX()) * 0.01D, -1.5D, (at.z - m.getZ()) * 0.01D);
            m.getPersistentData().putFloat(METEOR, 2.0F + size * 0.3F);
            m.getPersistentData().putUUID("flash_meteor_by", p.getUUID());
        }
    }

    // ------------------------------------------------------------------
    // Every tick
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        for (java.util.Iterator<Punch> it = PUNCHES.iterator(); it.hasNext(); ) {
            Punch pu = it.next();
            ServerLevel level = pu.p.serverLevel();
            Vec3 next = pu.pos.add(pu.dir.scale(pu.speed));
            AABB box = new AABB(pu.pos, next).inflate(pu.size);
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, e -> e != pu.p
                    && !pu.hit.contains(e.getUUID()))) {
                pu.hit.add(e.getUUID());
                e.hurt(level.damageSources().playerAttack(pu.p), pu.damage);
                e.knockback(1.0D + pu.knock * 2.0D, -pu.dir.x, -pu.dir.z);
                e.setDeltaMovement(e.getDeltaMovement().add(pu.dir.scale(pu.knock)));
                e.hurtMarked = true;
            }
            if (level.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_MOBGRIEFING)) {
                BlockPos c = BlockPos.containing(next);
                int r = (int) Math.ceil(pu.size * 0.5D);
                for (BlockPos pos : BlockPos.betweenClosed(c.offset(-r, -r, -r), c.offset(r, r, r))) {
                    BlockState st = level.getBlockState(pos);
                    if (!st.isAir() && st.getDestroySpeed(level, pos) >= 0.0F && st.getDestroySpeed(level, pos) < 50.0F) {
                        level.destroyBlock(pos, true, pu.p);
                    }
                }
            }
            level.sendParticles(ParticleTypes.SONIC_BOOM, next.x, next.y, next.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
            pu.pos = next;
            pu.left -= pu.speed;
            if (pu.left <= 0.0D) {
                it.remove();
            }
        }
        for (ServerLevel level : event.getServer().getAllLevels()) {
            for (var e : level.getAllEntities()) {
                if (e instanceof FallingBlockEntity fb && fb.getPersistentData().contains(METEOR)) {
                    if (fb.tickCount % 2 == 0) {
                        level.sendParticles(ParticleTypes.FLAME, fb.getX(), fb.getY(), fb.getZ(), 4, 0.3D, 0.3D,
                                0.3D, 0.02D);
                    }
                    if (fb.onGround() || fb.isRemoved() || fb.tickCount > 400) {
                        float power = fb.getPersistentData().getFloat(METEOR);
                        level.explode(fb, fb.getX(), fb.getY(), fb.getZ(), power, Level.ExplosionInteraction.TNT);
                        fb.discard();
                    }
                }
            }
        }
        STORMS.entrySet().removeIf(en -> en.setValue(en.getValue() + 1) > 200);
    }
}
