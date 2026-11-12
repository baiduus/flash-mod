package com.flash.lex;

import com.flash.FlashMod;
import com.flash.entity.FlashSuitStand;
import com.flash.entity.LexMissile;
import com.flash.item.LexArmorItem;
import com.flash.network.FlashNetwork;
import com.flash.network.LexFxPacket;
import com.flash.network.LexSyncPacket;
import com.flash.power.HeroBalance;
import com.flash.registry.ModSounds;
import com.flash.superman.SupermanAbilities;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

/**
 * Lex Luthor's war suit (the Iron Man Mark 6 of Legends: Superheroes in Lex's colours), server side. With the four
 * pieces on, and no powers of his own (it is a human's machine), the pilot gets Superman's powers at level 5 toned
 * down, and the Mark 6 systems (see {@link LexAbility}), all paid from one battery in the chestplate: about 15
 * minutes of normal use (flying and the weapons cost more). At 0 % everything stops until it is charged on the
 * armour stand fed with redstone (2 % a second, full in 50 s).
 */
@Mod.EventBusSubscriber(modid = FlashMod.MODID)
public final class LexSuit {

    /** 100 % in 15 minutes of normal use. */
    public static final float DRAIN_PER_TICK = LexArmorItem.MAX_ENERGY / (15.0F * 60.0F * 20.0F);
    /** 2 % a second on a powered stand. */
    public static final float CHARGE_PER_TICK = 2.0F / 20.0F;

    public static final int F_ON = 1;
    public static final int F_BEAM = 2;
    public static final int F_UNIBEAM = 4;
    public static final int F_UNI_CHARGING = 8;
    public static final int F_OVERDRIVE = 16;
    public static final int F_OPEN = 32;
    public static final int F_DIVE = 64;

    private static final UUID DAMAGE_ID = UUID.fromString("6c1d2e30-9a4b-4f0e-8a11-4c4c55a7e001");
    private static final UUID SPEED_ID = UUID.fromString("6c1d2e30-9a4b-4f0e-8a11-4c4c55a7e002");
    /** Superman's punch at level 5 (about 26): the suit gives 60 % of it. */
    private static final double PUNCH_BONUS = 15.0D;
    private static final int OVERDRIVE_TICKS = 200;
    private static final int UNIBEAM_CHARGE = 20;
    private static final int UNIBEAM_MAX = 100;

    private static final class Pilot {
        int flags;
        int sentFlags = -1;
        final long[] cooldownUntil = new long[LexAbility.values().length];
        boolean cooldownsDirty = true;
        long overdriveUntil;
        int unibeamTicks = -1;
        int diveTicks = -1;
        float pending;
        final List<long[]> missiles = new ArrayList<>();
    }

    private static final Map<UUID, Pilot> PILOTS = new HashMap<>();
    private static final Set<UUID> FLYERS = new HashSet<>();

    private LexSuit() {
    }

    private static Pilot pilot(Player player) {
        return PILOTS.computeIfAbsent(player.getUUID(), id -> new Pilot());
    }

    /** The suit works for this player right now: all four pieces, no hero powers, charge left. */
    public static boolean active(Player player) {
        return LexArmorItem.wearingFull(player) && !HeroBalance.isHero(player)
                && LexArmorItem.energy(player.getItemBySlot(EquipmentSlot.CHEST)) > 0.0F;
    }

    public static boolean overdrive(Player player) {
        Pilot p = PILOTS.get(player.getUUID());
        return p != null && (p.flags & F_OVERDRIVE) != 0;
    }

    /** Ability power of the suit: Superman's at level 5, 60 % of it (12 = one of its punches); overdrive +50 %. */
    public static float power(Player player) {
        float base = (float) ((1.0D + PUNCH_BONUS) / 12.0D);
        return overdrive(player) ? base * 1.5F : base;
    }

    private static float energy(ServerPlayer player) {
        return LexArmorItem.energy(player.getItemBySlot(EquipmentSlot.CHEST));
    }

    private static boolean spend(ServerPlayer player, float amount) {
        ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
        float e = LexArmorItem.energy(chest);
        if (overdrive(player)) {
            amount *= 2.0F;
        }
        if (e < amount) {
            return false;
        }
        LexArmorItem.setEnergy(chest, e - amount);
        return true;
    }

    // ------------------------------------------------------------------
    // Keys (LexKeyPacket)
    // ------------------------------------------------------------------

    public static void key(ServerPlayer player, int index, boolean pressed) {
        LexAbility[] all = LexAbility.values();
        if (index < 0 || index >= all.length || !LexArmorItem.wearingFull(player) || HeroBalance.isHero(player)) {
            return;
        }
        LexAbility a = all[index];
        Pilot s = pilot(player);
        if (!active(player)) {
            if (pressed) {
                player.displayClientMessage(Component.translatable("message.flash.lex_no_energy"), true);
            }
            stopBeams(player, s);
            return;
        }
        if (a.kind() == LexAbility.Kind.HOLD) {
            if (a == LexAbility.REPULSOR_BEAM) {
                setFlag(s, F_BEAM, pressed);
                SupermanAbilities.anim(player, pressed ? "lex_beam" : "none");
                if (pressed) {
                    sound(player, ModSounds.LEX_REPULSOR_CHARGE.get(), 0.8F, 1.2F);
                }
            } else if (a == LexAbility.UNIBEAM) {
                if (pressed) {
                    if (cooling(player, s, a) || s.unibeamTicks >= 0) {
                        return;
                    }
                    s.unibeamTicks = 0;
                    setFlag(s, F_UNI_CHARGING, true);
                    SupermanAbilities.anim(player, "lex_unibeam");
                    sound(player, ModSounds.LEX_UNIBEAM_CHARGE.get(), 1.0F, 1.0F);
                } else if (s.unibeamTicks >= 0) {
                    endUnibeam(player, s);
                }
            }
            return;
        }
        if (!pressed || cooling(player, s, a)) {
            return;
        }
        if (!spend(player, a.energy())) {
            player.displayClientMessage(Component.translatable("message.flash.lex_no_energy"), true);
            return;
        }
        boolean used = switch (a) {
            case REPULSOR_BLAST -> repulsorBlast(player);
            case LASERS -> lasers(player);
            case MISSILES -> missiles(player, s);
            case POWER_THRUST -> thrust(player);
            case POWER_DIVE -> dive(player, s);
            case OVERDRIVE -> overdriveOn(player, s);
            case FACEPLATE -> faceplate(player, s);
            default -> false;
        };
        if (used) {
            setCooldown(s, a, player.level().getGameTime());
        } else {
            LexArmorItem.setEnergy(player.getItemBySlot(EquipmentSlot.CHEST), energy(player) + a.energy());
        }
    }

    private static boolean cooling(ServerPlayer player, Pilot s, LexAbility a) {
        long left = s.cooldownUntil[a.ordinal()] - player.level().getGameTime();
        if (left > 0) {
            player.displayClientMessage(Component.translatable("message.flash.thor_cooldown", (left + 19) / 20), true);
            return true;
        }
        return false;
    }

    private static void setCooldown(Pilot s, LexAbility a, long now) {
        s.cooldownUntil[a.ordinal()] = now + a.cooldown();
        s.cooldownsDirty = true;
    }

    private static void setFlag(Pilot s, int flag, boolean on) {
        s.flags = on ? s.flags | flag : s.flags & ~flag;
    }

    private static void stopBeams(ServerPlayer player, Pilot s) {
        if ((s.flags & F_BEAM) != 0) {
            setFlag(s, F_BEAM, false);
            SupermanAbilities.anim(player, "none");
        }
        if (s.unibeamTicks >= 0) {
            endUnibeam(player, s);
        }
    }

    // ------------------------------------------------------------------
    // The systems
    // ------------------------------------------------------------------

    /** The palm: where the beams and blasts leave from. */
    private static Vec3 palm(ServerPlayer player) {
        Vec3 look = player.getLookAngle();
        Vec3 side = new Vec3(-look.z, 0.0D, look.x).normalize();
        return player.getEyePosition().add(look.scale(0.8D)).add(side.scale(0.3D)).add(0.0D, -0.4D, 0.0D);
    }

    private static Vec3 chest(ServerPlayer player) {
        return player.position().add(0.0D, player.getBbHeight() * 0.68D, 0.0D).add(player.getLookAngle().scale(0.4D));
    }

    private static boolean repulsorBlast(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        HitResult hit = SupermanAbilities.trace(player, 40.0D);
        Vec3 from = palm(player);
        Vec3 to = hit.getLocation();
        SupermanAbilities.anim(player, "lex_repulsor");
        sound(player, ModSounds.LEX_REPULSOR_BLAST.get(), 1.0F, 1.0F);
        fx(level, LexFxPacket.BLAST, player.getId(), from, to);
        if (hit instanceof EntityHitResult eh && eh.getEntity() instanceof LivingEntity t) {
            hurt(player, t, 5.0F * power(player));
            Vec3 push = player.getLookAngle().scale(1.1D);
            t.setDeltaMovement(push.x, 0.35D, push.z);
            t.hurtMarked = true;
        }
        level.playSound(null, to.x, to.y, to.z, ModSounds.LEX_REPULSOR_IMPACT.get(), SoundSource.PLAYERS, 0.8F, 1.0F);
        level.sendParticles(ParticleTypes.FLASH, to.x, to.y, to.z, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, to.x, to.y, to.z, 10, 0.2D, 0.2D, 0.2D, 0.2D);
        return true;
    }

    /** Continuous repulsor beam: a pulse every 4 ticks. */
    private static void beamTick(ServerPlayer player, Pilot s) {
        if (!spend(player, LexAbility.REPULSOR_BEAM.energy())) {
            setFlag(s, F_BEAM, false);
            SupermanAbilities.anim(player, "none");
            return;
        }
        if (player.tickCount % 4 != 0) {
            return;
        }
        HitResult hit = SupermanAbilities.trace(player, 32.0D);
        if (hit instanceof EntityHitResult eh && eh.getEntity() instanceof LivingEntity t) {
            hurt(player, t, 1.2F * power(player));
            t.setDeltaMovement(t.getDeltaMovement().add(player.getLookAngle().scale(0.15D)));
            t.hurtMarked = true;
        }
        if (player.tickCount % 12 == 0) {
            sound(player, ModSounds.LEX_REPULSOR_CONTINUOUS.get(), 0.7F, 1.0F);
        }
    }

    /** Unibeam: the chest charges for a second, then a thick beam (held, up to 5 s). */
    private static void unibeamTick(ServerPlayer player, Pilot s) {
        int t = s.unibeamTicks++;
        if (t < UNIBEAM_CHARGE) {
            return;
        }
        if (t == UNIBEAM_CHARGE) {
            setFlag(s, F_UNI_CHARGING, false);
            setFlag(s, F_UNIBEAM, true);
            sound(player, ModSounds.LEX_UNIBEAM_BLAST.get(), 1.4F, 1.0F);
        }
        if (t > UNIBEAM_CHARGE + UNIBEAM_MAX || !spend(player, LexAbility.UNIBEAM.energy())) {
            endUnibeam(player, s);
            return;
        }
        if (t % 4 != 0) {
            return;
        }
        ServerLevel level = player.serverLevel();
        Vec3 from = chest(player);
        Vec3 look = player.getLookAngle();
        BlockHitResult block = level.clip(new ClipContext(from, from.add(look.scale(40.0D)), ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, player));
        Vec3 end = block.getLocation();
        Vec3 ab = end.subtract(from);
        for (Entity e : level.getEntities(player, new AABB(from, end).inflate(1.8D), x -> x instanceof LivingEntity
                && x.isAlive())) {
            Vec3 p = e.position().add(0.0D, e.getBbHeight() * 0.5D, 0.0D);
            double k = Math.max(0.0D, Math.min(1.0D, p.subtract(from).dot(ab) / Math.max(1.0E-4D, ab.lengthSqr())));
            if (p.distanceTo(from.add(ab.scale(k))) < 1.8D) {
                hurt(player, (LivingEntity) e, 2.8F * power(player));
                e.setDeltaMovement(e.getDeltaMovement().add(look.scale(0.3D)));
                e.hurtMarked = true;
            }
        }
        if (block.getType() != HitResult.Type.MISS) {
            level.sendParticles(ParticleTypes.LAVA, end.x, end.y, end.z, 2, 0.2D, 0.2D, 0.2D, 0.0D);
            level.sendParticles(ParticleTypes.LARGE_SMOKE, end.x, end.y, end.z, 3, 0.3D, 0.3D, 0.3D, 0.02D);
        }
    }

    private static void endUnibeam(ServerPlayer player, Pilot s) {
        boolean fired = (s.flags & F_UNIBEAM) != 0;
        s.unibeamTicks = -1;
        setFlag(s, F_UNIBEAM, false);
        setFlag(s, F_UNI_CHARGING, false);
        SupermanAbilities.anim(player, "none");
        if (fired) {
            sound(player, ModSounds.LEX_UNIBEAM_POWERDOWN.get(), 1.0F, 1.0F);
            setCooldown(s, LexAbility.UNIBEAM, player.level().getGameTime());
        }
    }

    /** The wrist lasers: a sweep in front that cuts everything within 9 blocks. */
    private static boolean lasers(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        SupermanAbilities.anim(player, "lex_lasers");
        sound(player, ModSounds.LEX_LASER.get(), 1.2F, 1.0F);
        Vec3 look = player.getLookAngle().multiply(1.0D, 0.0D, 1.0D).normalize();
        fx(level, LexFxPacket.LASER, player.getId(), palm(player), look);
        double cos = Math.cos(Math.toRadians(75.0D));
        for (Entity e : level.getEntities(player, player.getBoundingBox().inflate(9.0D), x -> x instanceof LivingEntity
                && x.isAlive())) {
            Vec3 d = e.position().subtract(player.position()).multiply(1.0D, 0.0D, 1.0D);
            if (d.length() > 9.0D || (d.length() > 0.5D && d.normalize().dot(look) < cos)) {
                continue;
            }
            hurt(player, (LivingEntity) e, 7.0F * power(player));
            e.setSecondsOnFire(3);
        }
        return true;
    }

    /** Homing micro missiles: six, shared out between the targets in sight, two at a time from the shoulders. */
    private static boolean missiles(ServerPlayer player, Pilot s) {
        ServerLevel level = player.serverLevel();
        List<LivingEntity> targets = new ArrayList<>();
        HitResult hit = SupermanAbilities.trace(player, 64.0D);
        if (hit instanceof EntityHitResult eh && eh.getEntity() instanceof LivingEntity t) {
            targets.add(t);
        }
        Vec3 look = player.getLookAngle();
        for (Entity e : level.getEntities(player, player.getBoundingBox().inflate(40.0D), x -> x instanceof LivingEntity
                && x.isAlive() && !targets.contains(x))) {
            Vec3 d = e.position().add(0.0D, e.getBbHeight() * 0.5D, 0.0D).subtract(player.getEyePosition());
            if (d.normalize().dot(look) > 0.9D && player.hasLineOfSight(e)) {
                targets.add((LivingEntity) e);
            }
        }
        targets.sort((a, b) -> Double.compare(a.distanceToSqr(player), b.distanceToSqr(player)));
        SupermanAbilities.anim(player, "lex_missile_fire");
        sound(player, ModSounds.LEX_MICROMISSILES.get(), 1.2F, 1.0F);
        long now = level.getGameTime();
        for (int i = 0; i < 6; i++) {
            int target = targets.isEmpty() ? -1 : targets.get(i % targets.size()).getId();
            s.missiles.add(new long[] {now + 2L * (i / 2) + 2L, target, i % 2});
        }
        return true;
    }

    private static void missileTick(ServerPlayer player, Pilot s) {
        if (s.missiles.isEmpty()) {
            return;
        }
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        var it = s.missiles.iterator();
        while (it.hasNext()) {
            long[] m = it.next();
            if (now < m[0]) {
                continue;
            }
            it.remove();
            Vec3 look = player.getLookAngle();
            Vec3 side = new Vec3(-look.z, 0.0D, look.x).normalize();
            boolean left = m[2] == 0;
            Vec3 from = player.getEyePosition().add(side.scale(left ? 0.4D : -0.4D)).add(0.0D, -0.25D, 0.0D);
            Vec3 dir = look.add(0.0D, 0.25D, 0.0D).add(side.scale(left ? 0.25D : -0.25D));
            Entity target = m[1] >= 0 ? level.getEntity((int) m[1]) : null;
            level.addFreshEntity(new LexMissile(level, player, from, dir, target, 3.5F * power(player)));
            level.sendParticles(ParticleTypes.CLOUD, from.x, from.y, from.z, 3, 0.05D, 0.05D, 0.05D, 0.02D);
            level.playSound(null, from.x, from.y, from.z, ModSounds.LEX_MISSILE.get(), SoundSource.PLAYERS, 0.8F, 1.1F);
        }
    }

    /** Power thrust: the boots push hard where the pilot looks. */
    private static boolean thrust(ServerPlayer player) {
        Vec3 v = player.getLookAngle().scale(2.4D);
        player.setDeltaMovement(v.x, Math.max(v.y, 0.3D), v.z);
        player.hurtMarked = true;
        ServerLevel level = player.serverLevel();
        level.sendParticles(ParticleTypes.FLAME, player.getX(), player.getY(), player.getZ(), 20, 0.2D, 0.1D, 0.2D, 0.05D);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, player.getX(), player.getY(), player.getZ(), 8, 0.2D, 0.1D, 0.2D,
                0.02D);
        sound(player, ModSounds.LEX_MISSILE_LAUNCH.get(), 1.0F, 1.4F);
        SupermanAbilities.anim(player, "lex_thrust");
        return true;
    }

    /** Power dive: from the air, straight down where he looks, and a shockwave where he lands. */
    private static boolean dive(ServerPlayer player, Pilot s) {
        if (player.onGround()) {
            player.displayClientMessage(Component.translatable("message.flash.lex_dive_air"), true);
            return false;
        }
        player.getAbilities().flying = false;
        player.onUpdateAbilities();
        Vec3 look = player.getLookAngle();
        player.setDeltaMovement(look.x * 1.2D, -2.6D, look.z * 1.2D);
        player.hurtMarked = true;
        s.diveTicks = 0;
        setFlag(s, F_DIVE, true);
        SupermanAbilities.anim(player, "lex_dive");
        sound(player, ModSounds.LEX_MISSILE_LAUNCH.get(), 1.0F, 0.8F);
        return true;
    }

    private static void diveTick(ServerPlayer player, Pilot s) {
        int t = s.diveTicks++;
        player.fallDistance = 0.0F;
        if (t < 2 || !(player.onGround() || player.isInWater() || t > 80)) {
            if (player.getDeltaMovement().y > -2.0D) {
                player.setDeltaMovement(player.getDeltaMovement().add(0.0D, -0.3D, 0.0D));
                player.hurtMarked = true;
            }
            return;
        }
        s.diveTicks = -1;
        setFlag(s, F_DIVE, false);
        SupermanAbilities.anim(player, "lex_land");
        ServerLevel level = player.serverLevel();
        Vec3 at = player.position();
        for (Entity e : level.getEntities(player, player.getBoundingBox().inflate(5.0D), x -> x instanceof LivingEntity
                && x.isAlive())) {
            double f = 1.0D - e.distanceTo(player) / 6.0D;
            if (f <= 0.0D) {
                continue;
            }
            hurt(player, (LivingEntity) e, (float) (7.0F * power(player) * (0.4D + 0.6D * f)));
            Vec3 away = e.position().subtract(at).multiply(1.0D, 0.0D, 1.0D).normalize();
            e.setDeltaMovement(away.x * (0.8D + f), 0.5D, away.z * (0.8D + f));
            e.hurtMarked = true;
        }
        SupermanAbilities.cracks(level, at, 4.0F);
        SupermanAbilities.groundPuff(level, at, 2.5D, 30);
        fx(level, LexFxPacket.DIVE, player.getId(), at, at);
        level.playSound(null, at.x, at.y, at.z, ModSounds.LEX_MISSILE_BOOM.get(), SoundSource.PLAYERS, 1.6F, 0.8F);
    }

    /** Armour overdrive: 10 s at +50 % power, the weapons spending twice the energy. */
    private static boolean overdriveOn(ServerPlayer player, Pilot s) {
        s.overdriveUntil = player.level().getGameTime() + OVERDRIVE_TICKS;
        setFlag(s, F_OVERDRIVE, true);
        sound(player, ModSounds.LEX_OVERDRIVE.get(), 1.2F, 1.0F);
        player.displayClientMessage(Component.translatable("message.flash.lex_overdrive"), true);
        return true;
    }

    private static boolean faceplate(ServerPlayer player, Pilot s) {
        boolean open = (s.flags & F_OPEN) == 0;
        setFlag(s, F_OPEN, open);
        sound(player, open ? ModSounds.LEX_HELMET_OPEN.get() : ModSounds.LEX_HELMET_CLOSE.get(), 1.0F, 1.0F);
        return true;
    }

    // ------------------------------------------------------------------
    // Tick
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) {
            return;
        }
        boolean full = LexArmorItem.wearingFull(player);
        boolean pilotOk = full && !HeroBalance.isHero(player);
        ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
        float energy = full ? LexArmorItem.energy(chest) : 0.0F;
        boolean on = pilotOk && energy > 0.0F;
        attributes(player, on);
        flight(player, on);
        Pilot s = PILOTS.get(player.getUUID());
        if (!on) {
            if (s != null) {
                boolean was = (s.flags & F_ON) != 0;
                stopBeams(player, s);
                s.missiles.clear();
                s.diveTicks = -1;
                int keep = s.flags & F_OPEN;
                s.flags = keep;
                if (was && pilotOk) {
                    sound(player, ModSounds.LEX_POWERDOWN.get(), 1.0F, 1.0F);
                }
                sync(player, s);
                if (!full) {
                    PILOTS.remove(player.getUUID());
                    sendFlags(player, 0, null);
                }
            }
            return;
        }
        s = pilot(player);
        setFlag(s, F_ON, true);
        long now = player.level().getGameTime();
        if ((s.flags & F_OVERDRIVE) != 0 && now >= s.overdriveUntil) {
            setFlag(s, F_OVERDRIVE, false);
            sound(player, ModSounds.LEX_OVERDRIVE_OFF.get(), 1.0F, 1.0F);
        }
        if ((s.flags & F_BEAM) != 0) {
            beamTick(player, s);
        }
        if (s.unibeamTicks >= 0) {
            unibeamTick(player, s);
        }
        if (s.diveTicks >= 0) {
            diveTick(player, s);
        }
        missileTick(player, s);
        boolean flying = player.getAbilities().flying;
        if (flying && player.tickCount % 2 == 0) {
            ServerLevel level = player.serverLevel();
            level.sendParticles(ParticleTypes.SMALL_FLAME, player.getX(), player.getY() - 0.1D, player.getZ(), 2, 0.12D,
                    0.05D, 0.12D, 0.0D);
            level.sendParticles(ParticleTypes.SMOKE, player.getX(), player.getY() - 0.2D, player.getZ(), 1, 0.1D, 0.05D,
                    0.1D, 0.0D);
        }
        // the battery (written to the item in small steps, not every tick)
        s.pending += DRAIN_PER_TICK * (flying ? 1.8F : 1.0F) * ((s.flags & F_OVERDRIVE) != 0 ? 2.0F : 1.0F);
        float now2 = LexArmorItem.energy(chest);
        if (s.pending >= 0.05F) {
            float after = Math.max(0.0F, now2 - s.pending);
            LexArmorItem.setEnergy(chest, after);
            s.pending = 0.0F;
        }
        warnings(player, energy, LexArmorItem.energy(chest));
        sync(player, s);
    }

    private static void sync(ServerPlayer player, Pilot s) {
        if (s.flags == s.sentFlags && !s.cooldownsDirty && player.tickCount % 40 != 0) {
            return;
        }
        long now = player.level().getGameTime();
        int[] cds = new int[s.cooldownUntil.length];
        for (int i = 0; i < cds.length; i++) {
            cds[i] = (int) Math.max(0L, s.cooldownUntil[i] - now);
        }
        sendFlags(player, s.flags, cds);
        s.sentFlags = s.flags;
        s.cooldownsDirty = false;
    }

    private static void sendFlags(ServerPlayer player, int flags, int[] cds) {
        int[] c = cds != null ? cds : new int[LexAbility.values().length];
        FlashNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player),
                new LexSyncPacket(player.getId(), flags, c));
    }

    private static void warnings(ServerPlayer player, float before, float after) {
        for (float mark : new float[] {20.0F, 5.0F}) {
            if (before > mark && after <= mark) {
                sound(player, ModSounds.LEX_LOW_ENERGY.get(), 1.0F, 1.0F);
                player.displayClientMessage(Component.translatable("message.flash.lex_low", Math.round(mark)), true);
            }
        }
        if (before > 0.0F && after <= 0.0F) {
            sound(player, ModSounds.LEX_EMPTY.get(), 1.0F, 1.0F);
            player.displayClientMessage(Component.translatable("message.flash.lex_empty"), false);
        }
    }

    private static void attributes(ServerPlayer player, boolean on) {
        double punch = on ? PUNCH_BONUS * (overdrive(player) ? 1.5D : 1.0D) : 0.0D;
        set(player, Attributes.ATTACK_DAMAGE, DAMAGE_ID, punch, AttributeModifier.Operation.ADDITION);
        set(player, Attributes.MOVEMENT_SPEED, SPEED_ID, on ? 0.25D : 0.0D, AttributeModifier.Operation.MULTIPLY_BASE);
    }

    private static void set(ServerPlayer player, net.minecraft.world.entity.ai.attributes.Attribute attribute, UUID id,
                            double amount, AttributeModifier.Operation op) {
        AttributeInstance inst = player.getAttribute(attribute);
        if (inst == null) {
            return;
        }
        AttributeModifier old = inst.getModifier(id);
        if (amount == 0.0D) {
            if (old != null) {
                inst.removeModifier(id);
            }
            return;
        }
        if (old == null || old.getAmount() != amount) {
            inst.removeModifier(id);
            inst.addTransientModifier(new AttributeModifier(id, "flash_lex", amount, op));
        }
    }

    /** Thrusters: double jump flies (as Superman), only while the battery lasts. */
    private static void flight(ServerPlayer player, boolean on) {
        var ab = player.getAbilities();
        boolean creative = player.isCreative() || player.isSpectator();
        if (on) {
            if (FLYERS.add(player.getUUID()) || !ab.mayfly) {
                ab.mayfly = true;
                if (!creative) {
                    ab.setFlyingSpeed(overdrive(player) ? 0.1F : 0.07F);
                }
                player.onUpdateAbilities();
            }
            if (ab.flying) {
                player.fallDistance = 0.0F;
            }
        } else if (FLYERS.remove(player.getUUID()) && !creative) {
            ab.mayfly = false;
            ab.flying = false;
            ab.setFlyingSpeed(0.05F);
            player.onUpdateAbilities();
        }
    }

    /** The suit takes about half of every hit. */
    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        if (event.getEntity() instanceof Player p && !p.level().isClientSide && active(p)) {
            event.setAmount(event.getAmount() * 0.5F);
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static void hurt(ServerPlayer player, LivingEntity target, float amount) {
        target.invulnerableTime = 0;
        target.hurt(player.serverLevel().damageSources().playerAttack(player), amount);
    }

    private static void sound(ServerPlayer player, SoundEvent sound, float volume, float pitch) {
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), sound, SoundSource.PLAYERS, volume,
                pitch);
    }

    private static void fx(ServerLevel level, int kind, int entity, Vec3 a, Vec3 b) {
        FlashNetwork.CHANNEL.send(PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(a.x, a.y, a.z,
                96.0D, level.dimension())), new LexFxPacket(kind, entity, a.x, a.y, a.z, b.x, b.y, b.z));
    }

    // ------------------------------------------------------------------
    // Charging on the armour stand
    // ------------------------------------------------------------------

    /** Called every tick by the stand: with the chestplate on it and redstone power under or next to it. */
    public static void standTick(FlashSuitStand stand) {
        ItemStack chest = stand.getItemBySlot(EquipmentSlot.CHEST);
        if (!LexArmorItem.isLex(chest)) {
            return;
        }
        var level = stand.level();
        var pos = stand.blockPosition();
        boolean powered = level.hasNeighborSignal(pos) || level.hasNeighborSignal(pos.below())
                || level.getBestNeighborSignal(pos.below()) > 0;
        float e = LexArmorItem.energy(chest);
        if (!powered || e >= LexArmorItem.MAX_ENERGY) {
            return;
        }
        if (stand.tickCount % 10 == 0) {
            // +1 % every half second
            LexArmorItem.setEnergy(chest, e + CHARGE_PER_TICK * 10.0F);
            if (level instanceof ServerLevel sl) {
                sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, stand.getX(), stand.getY() + 1.2D, stand.getZ(), 3, 0.25D,
                        0.4D, 0.25D, 0.02D);
            }
            if (e + CHARGE_PER_TICK * 10.0F >= LexArmorItem.MAX_ENERGY) {
                level.playSound(null, stand.getX(), stand.getY(), stand.getZ(), ModSounds.LEX_RECHARGE.get(),
                        SoundSource.BLOCKS, 0.8F, 1.0F);
            }
        }
    }
}
