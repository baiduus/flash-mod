package com.flash.thor;

import com.flash.entity.MjolnirEntity;
import com.flash.item.MjolnirItem;
import com.flash.network.FlashNetwork;
import com.flash.network.ThorFxPacket;
import com.flash.registry.ModItems;
import com.flash.registry.ModSounds;
import com.flash.superman.SupermanAbilities;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

/**
 * The abilities of Thor (the Mjolnir, Stormbreaker and Cosmic King sets of Legends: Superheroes), server side.
 * Damage follows the punch of Thor (see {@link ThorPowers#power}): a base value of 12 is one punch.
 */
public final class ThorAbilities {

    private static final class Session {
        // Hammer Spark (the combo)
        int sparkTarget = -1;
        int sparkTicks = -1;
        int sparkSmash = -1;
        int sparkFall = -1;
        // Lightning Vortex
        int vortexTicks = -1;
        final List<Integer> vortexTargets = new ArrayList<>();
        int vortexIndex;
        int vortexZip;
        Vec3 vortexDir;
        Vec3 vortexLast;
        // Mighty Tornado
        Vec3 tornado;
        int tornadoTicks = -1;
        // God of Thunder
        int ultTicks = -1;
        Vec3 ultStart;
        Vec3 ultTarget;
        // Storm Striker (the leap)
        int strikerTicks = -1;
        // God Blast
        int godBlastTicks = -1;
        // Bifrost
        int bifrostTicks = -1;
        Vec3 bifrostDest;
        // Weapon of a King
        int kingTicks = -1;
        int kingTarget = -1;
        Vec3 kingStart;
        // Cosmic Judgement
        int judgeTicks = -1;
        final List<Integer> judged = new ArrayList<>();
        // Devourer King
        int domainTicks = -1;
        Vec3 domainCenter;
        // Stormbreaker melee chain
        int axeCombo;
        long axeLast;
        final List<Pending> pending = new ArrayList<>();
    }

    private record Pending(long at, Consumer<ServerPlayer> action) {
    }

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    /** The animations that have a mirrored version for the weapon in the left hand. */
    private static final Set<String> LEFT = Set.of("thor_bifrost", "thor_bifrostflight", "thor_hammerslam",
            "thor_hammerslamdrop", "thor_thortransform");
    private static final String BIFROST_HOME = "flash_bifrost_home";
    /** The lightning sent right now is purple (a Cosmic King is casting). */
    private static boolean cosmicCaster;

    private ThorAbilities() {
    }

    private static Session session(ServerPlayer player) {
        return SESSIONS.computeIfAbsent(player.getUUID(), id -> new Session());
    }

    private static void later(ServerPlayer player, int ticks, Consumer<ServerPlayer> action) {
        session(player).pending.add(new Pending(player.level().getGameTime() + ticks, action));
    }

    private static void caster(ServerPlayer player) {
        cosmicCaster = ThorPowers.cosmic(player);
    }

    // ------------------------------------------------------------------
    // The weapon (Mjolnir, MCU Mjolnir, Cosmic King Mjolnir or Stormbreaker: it follows the suit)
    // ------------------------------------------------------------------

    public static boolean holdingHammer(ServerPlayer player) {
        return player.getMainHandItem().getItem() instanceof MjolnirItem
                || player.getOffhandItem().getItem() instanceof MjolnirItem;
    }

    private static boolean hasHammerItem(ServerPlayer player) {
        for (ItemStack s : player.getInventory().items) {
            if (s.getItem() instanceof MjolnirItem) {
                return true;
            }
        }
        return player.getOffhandItem().getItem() instanceof MjolnirItem;
    }

    /** A new weapon item for the current suit. */
    private static ItemStack newWeapon(ServerPlayer player) {
        ItemStack s = new ItemStack(ModItems.MJOLNIR.get());
        MjolnirItem.setWeapon(s, ThorPowers.weapon(player));
        return s;
    }

    /** The weapons carried turn into the one of the suit (a Stormbreaker suit gets Stormbreaker...). */
    private static void syncWeapons(ServerPlayer player) {
        int w = ThorPowers.weapon(player);
        for (ItemStack s : player.getInventory().items) {
            if (s.getItem() instanceof MjolnirItem) {
                MjolnirItem.setWeapon(s, w);
            }
        }
        for (ItemStack s : player.getInventory().offhand) {
            if (s.getItem() instanceof MjolnirItem) {
                MjolnirItem.setWeapon(s, w);
            }
        }
    }

    private static boolean axe(ServerPlayer player) {
        return ThorPowers.stormbreaker(player);
    }

    private static SoundEvent throwSound(ServerPlayer player) {
        return axe(player) ? ModSounds.THOR_SB_THROW.get() : ModSounds.THOR_THROW.get();
    }

    private static SoundEvent hitSound(ServerPlayer player) {
        return axe(player) ? ModSounds.THOR_SB_HIT.get() : ModSounds.THOR_HIT.get();
    }

    /** On the transformation: the weapon comes to the hand, flying in from the sky when it is not carried. */
    public static void giveHammer(ServerPlayer player) {
        syncWeapons(player);
        if (hasHammerItem(player) || MjolnirEntity.find(player) != null) {
            return;
        }
        MjolnirEntity lying = MjolnirEntity.nearestStatue(player, 256.0D);
        if (lying != null) {
            lying.callTo(player);
            return;
        }
        summonFromAfar(player);
    }

    private static void summonFromAfar(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        MjolnirEntity e = new MjolnirEntity(level, player, newWeapon(player), 0.6F);
        Vec3 back = player.getLookAngle().multiply(-1.0D, 0.0D, -1.0D).normalize();
        Vec3 from = player.position().add(back.scale(18.0D)).add(0.0D, 14.0D, 0.0D);
        e.setPos(from.x, from.y, from.z);
        e.recall();
        level.addFreshEntity(e);
        sound(player, throwSound(player), 1.0F, 0.8F);
    }

    /** Revoked: the hammer goes away with the power. */
    public static void takeHammerAway(ServerPlayer player) {
        player.getInventory().clearOrCountMatchingItems(s -> s.getItem() instanceof MjolnirItem, -1,
                player.inventoryMenu.getCraftSlots());
        MjolnirEntity e = MjolnirEntity.find(player);
        if (e != null) {
            e.discard();
        }
    }

    /** The weapon item in hand (main hand first), or empty. */
    private static ItemStack weaponInHand(ServerPlayer player) {
        if (player.getMainHandItem().getItem() instanceof MjolnirItem) {
            return player.getMainHandItem();
        }
        return player.getOffhandItem().getItem() instanceof MjolnirItem ? player.getOffhandItem() : ItemStack.EMPTY;
    }

    public static MjolnirEntity throwHammer(ServerPlayer player, ItemStack stack, float force) {
        ServerLevel level = player.serverLevel();
        float charge = ThorPowers.charge(player) / 100.0F;
        MjolnirItem.setWeapon(stack, ThorPowers.weapon(player));
        MjolnirEntity e = new MjolnirEntity(level, player, stack, force);
        double speed = Math.min(4.5D, (1.6D + 1.8D * force) * (1.0D + 0.35D * charge));
        e.setDeltaMovement(player.getLookAngle().scale(speed));
        level.addFreshEntity(e);
        // out of the hand
        if (player.getMainHandItem() == stack) {
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        } else if (player.getOffhandItem() == stack) {
            player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        } else {
            player.getInventory().removeItem(stack);
        }
        ThorPowers.setFlag(player, ThorPowers.F_THROWN, true);
        player.swing(InteractionHand.MAIN_HAND, true);
        sound(player, throwSound(player), 1.0F, 0.9F + 0.2F * Math.min(1.0F, force));
        return e;
    }

    public static void catchHammer(ServerPlayer player, ItemStack stack) {
        ItemStack s = stack.isEmpty() ? newWeapon(player) : stack.copy();
        if (ThorPowers.has(player)) {
            MjolnirItem.setWeapon(s, ThorPowers.weapon(player));
        }
        // to the hand that is empty right now (main hand first)
        if (player.getMainHandItem().isEmpty()) {
            player.setItemInHand(InteractionHand.MAIN_HAND, s);
        } else if (player.getOffhandItem().isEmpty()) {
            player.setItemInHand(InteractionHand.OFF_HAND, s);
        } else if (!player.getInventory().add(s)) {
            player.drop(s, false);
        }
        ThorPowers.setFlag(player, ThorPowers.F_THROWN, false);
        sound(player, ModSounds.THOR_GRAB.get(), 1.0F, 1.0F);
    }

    /** The thrown weapon hits a creature on its way. */
    public static void hammerStrike(ServerPlayer player, LivingEntity target, float force, Vec3 motion) {
        caster(player);
        ServerLevel level = player.serverLevel();
        float power = ThorPowers.power(player);
        float charge = ThorPowers.charge(player) / 100.0F;
        float damage = (8.0F + 10.0F * force) * power * (1.0F + 0.3F * charge);
        hurt(player, target, damage);
        Vec3 push = motion.lengthSqr() < 1.0E-4D ? Vec3.ZERO : motion.normalize().scale(0.8D + 0.4D * force);
        target.setDeltaMovement(push.x, 0.35D, push.z);
        target.hurtMarked = true;
        if (charge > 0.2F || force >= 2.0F) {
            bolt(level, target.position().add(0.0D, target.getBbHeight() + 1.5D, 0.0D),
                    target.position().add(0.0D, target.getBbHeight() * 0.5D, 0.0D), force >= 2.0F ? 1.6F : 0.8F);
        }
        if (force >= 2.0F) {
            // Weapon of a King: the axe brings the storm with it
            strikeVisual(level, target.position());
            stun(target, 40);
            fx(level, ThorFxPacket.IMPACT, target.position(), Vec3.ZERO, 5.0F);
        }
        level.playSound(null, target.getX(), target.getY(), target.getZ(), hitSound(player), SoundSource.PLAYERS,
                1.0F, 1.0F);
        ThorPowers.addXp(player, 0.5F);
    }

    /** The thrown weapon stops on a wall / the ground: a small shockwave. */
    public static void hammerImpact(ServerPlayer player, Vec3 at, float force) {
        caster(player);
        ServerLevel level = player.serverLevel();
        float charge = ThorPowers.charge(player) / 100.0F;
        double radius = Math.min(7.0D, 2.0D + 3.0D * force * (0.5D + charge));
        float power = ThorPowers.power(player);
        for (LivingEntity e : around(level, player, at, radius)) {
            double f = 1.0D - e.position().distanceTo(at) / (radius + 1.0D);
            hurt(player, e, (float) (4.0F * power * Math.min(1.5F, force) * f));
            push(e, at, 0.6D + f);
        }
        level.playSound(null, at.x, at.y, at.z, axe(player) ? ModSounds.THOR_SB_THUMP.get() : ModSounds.THOR_THUMP.get(),
                SoundSource.PLAYERS, 1.2F, 1.0F);
        SupermanAbilities.groundPuff(level, at, radius * 0.5D, 14);
        fx(level, ThorFxPacket.RING, at, Vec3.ZERO, (float) radius);
    }

    /** A melee hit with the weapon in hand: the charge shocks the target; Stormbreaker chains its 4 swings. */
    public static void hammerHit(ServerPlayer player, LivingEntity target) {
        caster(player);
        float charge = ThorPowers.charge(player);
        boolean fury = ThorPowers.flag(player, ThorPowers.F_FURY);
        ServerLevel level = player.serverLevel();
        if (charge >= 5.0F || fury) {
            float power = ThorPowers.power(player);
            float shock = 2.5F * power * (ThorPowers.cosmic(player) ? 1.15F : 1.0F);
            hurt(player, target, shock);
            bolt(level, player.getEyePosition().add(player.getLookAngle().scale(0.6D)),
                    target.position().add(0.0D, target.getBbHeight() * 0.5D, 0.0D), 0.6F);
            herald(player, target, shock * 0.5F);
            if (!fury) {
                ThorPowers.setCharge(player, charge - 5.0F);
            }
        }
        if (axe(player) && MjolnirItem.stormbreaker(player.getMainHandItem())) {
            Session s = session(player);
            long now = level.getGameTime();
            s.axeCombo = now - s.axeLast <= 30L ? (s.axeCombo + 1) % 4 : 0;
            s.axeLast = now;
            anim(player, "thor_stormbreaker_" + (s.axeCombo + 1));
        }
        sound(player, hitSound(player), 0.8F, 1.0F);
    }

    /** Herald of Thunder (Cosmic King): every shock jumps to up to two more enemies nearby. */
    private static void herald(ServerPlayer player, LivingEntity from, float damage) {
        if (!ThorPowers.cosmic(player)) {
            return;
        }
        ServerLevel level = player.serverLevel();
        int n = 0;
        for (LivingEntity e : around(level, player, from.position(), 5.0D)) {
            if (e == from) {
                continue;
            }
            hurt(player, e, damage);
            bolt(level, from.position().add(0.0D, from.getBbHeight() * 0.5D, 0.0D),
                    e.position().add(0.0D, e.getBbHeight() * 0.5D, 0.0D), 0.5F);
            if (++n >= 2) {
                break;
            }
        }
    }

    /** Crouch + right click with the weapon: it drinks lightning (the charge bar), anywhere. */
    public static void chargeTick(ServerPlayer player, int held) {
        caster(player);
        ServerLevel level = player.serverLevel();
        float charge = ThorPowers.charge(player);
        ThorPowers.setCharge(player, charge + 0.8F);
        if (held % 20 == 0) {
            Vec3 top = player.position().add(0.0D, player.getBbHeight() + 0.6D, 0.0D);
            bolt(level, top.add(0.0D, 24.0D, 0.0D), top, 1.4F);
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.LIGHTNING_BOLT_THUNDER,
                    SoundSource.WEATHER, 0.6F, 1.3F);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, top.x, top.y, top.z, 20, 0.3D, 0.3D, 0.3D, 0.2D);
        }
    }

    /** Right click held (the throw wind-up): the whirl is heard while the weapon actually spins. */
    public static void whirlTick(ServerPlayer player, int held) {
        if (held > 3 && held % 12 == 4) {
            sound(player, ModSounds.THOR_SPIN.get(), 0.6F, 1.1F + Math.min(0.3F, held / 60.0F));
        }
    }

    // ------------------------------------------------------------------
    // Keys
    // ------------------------------------------------------------------

    public static void key(ServerPlayer player, ThorAbility ability, boolean pressed) {
        if (!ThorPowers.has(player) || player.isSpectator()) {
            return;
        }
        if (!ThorPowers.isThor(player)) {
            if (pressed) {
                player.displayClientMessage(Component.translatable("message.flash.thor_need_form"), true);
            }
            return;
        }
        if (ability.hidden()) {
            return;
        }
        caster(player);
        if (ThorPowers.level(player) < ability.level()) {
            if (pressed) {
                player.displayClientMessage(Component.translatable("message.flash.thor_locked", ability.level()), true);
            }
            return;
        }
        if (ability.kind() == ThorAbility.Kind.HOLD) {
            hold(player, ability, pressed);
            return;
        }
        if (!pressed) {
            return;
        }
        if (ability.needsHammer() && !holdingHammer(player)) {
            player.displayClientMessage(Component.translatable("message.flash.thor_need_hammer"), true);
            return;
        }
        if (ability.kind() == ThorAbility.Kind.TOGGLE) {
            toggle(player, ability);
            return;
        }
        if (busy(player) && ability != ThorAbility.SUMMON && ability != ThorAbility.WARRIORS_FURY) {
            return;
        }
        if (cooling(player, ability)) {
            return;
        }
        if (!ThorPowers.spend(player, ability.energy())) {
            player.displayClientMessage(Component.translatable("message.flash.thor_no_energy"), true);
            return;
        }
        int w = ThorPowers.weapon(player);
        boolean sb = w == ThorSuits.STORMBREAKER;
        boolean cosmic = w == ThorSuits.COSMIC;
        boolean used = switch (ability) {
            case SUMMON -> summon(player);
            case LIGHTNING_STRIKE -> lightningStrike(player);
            case STORM_STRIKER -> sb ? axeStriker(player) : stormStriker(player);
            case HAMMER_SPARK -> hammerSpark(player);
            case LIGHTNING_VORTEX -> sb ? bifrost(player) : cosmic ? judgement(player) : vortex(player);
            case WARRIORS_FURY -> fury(player);
            case MIGHTY_TORNADO -> tornado(player);
            case GOD_OF_THUNDER -> sb ? weaponOfAKing(player) : cosmic ? devourer(player)
                    : player.isShiftKeyDown() ? godBlast(player) : godOfThunder(player);
            default -> false;
        };
        if (used) {
            ThorPowers.setCooldown(player, ability);
        } else {
            ThorPowers.setEnergy(player, ThorPowers.energy(player) + ability.energy());
        }
    }

    /** A move that takes over the body is running (no other one starts on top of it). */
    private static boolean busy(ServerPlayer player) {
        Session s = SESSIONS.get(player.getUUID());
        return s != null && (s.sparkTicks >= 0 || s.vortexTicks >= 0 || s.ultTicks >= 0 || s.strikerTicks >= 0
                || s.godBlastTicks >= 0 || s.bifrostTicks >= 0 || s.kingTicks >= 0 || s.judgeTicks >= 0
                || s.domainTicks >= 0);
    }

    private static boolean cooling(ServerPlayer player, ThorAbility ability) {
        int cd = ThorPowers.cooldown(player, ability);
        if (cd > 0) {
            player.displayClientMessage(Component.translatable("message.flash.thor_cooldown", (cd + 19) / 20), true);
            return true;
        }
        return false;
    }

    private static void hold(ServerPlayer player, ThorAbility ability, boolean pressed) {
        boolean block = ability == ThorAbility.HAMMER_SPIN && axe(player);
        int flag = ability == ThorAbility.LIGHTNING_BLAST ? ThorPowers.F_BLAST : block ? ThorPowers.F_BLOCK
                : ThorPowers.F_SPIN;
        if (pressed && ability.needsHammer() && !holdingHammer(player)) {
            player.displayClientMessage(Component.translatable("message.flash.thor_need_hammer"), true);
            return;
        }
        if (pressed && busy(player)) {
            return;
        }
        boolean was = ThorPowers.flag(player, flag);
        ThorPowers.setFlag(player, flag, pressed);
        if (pressed && !was) {
            anim(player, ability == ThorAbility.LIGHTNING_BLAST ? "thor_lightning_blast"
                    : block ? "thor_block" : "thor_hammer_spin");
            sound(player, ability == ThorAbility.LIGHTNING_BLAST ? ModSounds.THOR_LIGHTNING.get()
                    : block ? ModSounds.THOR_BLOCK.get() : ModSounds.THOR_SPIN.get(), 1.0F, 1.0F);
        } else if (!pressed && was) {
            anim(player, "none");
        }
    }

    private static void toggle(ServerPlayer player, ThorAbility ability) {
        if (ability == ThorAbility.FLIGHT) {
            boolean on = !player.getAbilities().flying;
            if (on) {
                ThorPowers.setFlag(player, ThorPowers.F_FLIGHT, true);
                ThorPowers.startFlight(player);
                sound(player, ModSounds.THOR_SPIN.get(), 1.0F, 1.2F);
                SupermanAbilities.groundPuff(player.serverLevel(), player.position(), 1.5D, 16);
            } else {
                player.getAbilities().flying = false;
                player.onUpdateAbilities();
            }
            ThorPowers.sync(player);
            player.displayClientMessage(Component.translatable(on ? "message.flash.thor_flight_on"
                    : "message.flash.thor_flight_off"), true);
        }
    }

    /** Flight key held on the ground: 0 coil, 1 the ground cracks and Thor shoots ~20 blocks up, 2 cancelled. */
    public static void launch(ServerPlayer player, int phase) {
        if (!ThorPowers.isThor(player) || player.getAbilities().flying) {
            return;
        }
        if (phase == 0) {
            SupermanAbilities.anim(player, "charge_takeoff9");
            return;
        }
        if (phase != 1) {
            SupermanAbilities.anim(player, "none");
            return;
        }
        if (!holdingHammer(player)) {
            SupermanAbilities.anim(player, "none");
            player.displayClientMessage(Component.translatable("message.flash.thor_need_hammer"), true);
            return;
        }
        caster(player);
        ThorPowers.setFlag(player, ThorPowers.F_FLIGHT, true);
        player.getAbilities().mayfly = true;
        player.onUpdateAbilities();
        player.setDeltaMovement(player.getDeltaMovement().x, 2.0D, player.getDeltaMovement().z);
        player.hurtMarked = true;
        ServerLevel level = player.serverLevel();
        SupermanAbilities.cracks(level, player.position(), 3.5F);
        SupermanAbilities.anim(player, "charged_launch9");
        SupermanAbilities.groundPuff(level, player.position(), 2.5D, 30);
        sound(player, throwSound(player), 1.4F, 0.8F);
        strikeVisual(level, player.position());
        fx(level, ThorFxPacket.RING, player.position(), Vec3.ZERO, 4.0F);
        ThorPowers.sync(player);
    }

    public static void stopAll(ServerPlayer player) {
        Session s = SESSIONS.remove(player.getUUID());
        if (s != null) {
            ServerLevel level = player.serverLevel();
            if (s.sparkTarget >= 0 && level.getEntity(s.sparkTarget) instanceof LivingEntity t) {
                t.setNoGravity(false);
            }
            for (int id : s.judged) {
                if (level.getEntity(id) instanceof LivingEntity t) {
                    t.setNoGravity(false);
                }
            }
        }
        if (ThorPowers.has(player)) {
            ThorPowers.clearTransient(player);
        }
        player.setNoGravity(false);
    }

    // ------------------------------------------------------------------
    // Tick
    // ------------------------------------------------------------------

    public static void tick(ServerPlayer player) {
        if (!ThorPowers.isThor(player)) {
            if (SESSIONS.containsKey(player.getUUID())) {
                stopAll(player);
            }
            return;
        }
        caster(player);
        ServerLevel level = player.serverLevel();
        if (player.tickCount % 20 == 0) {
            syncWeapons(player);
        }
        int flags = ThorPowers.flags(player);
        if ((flags & ThorPowers.F_BLAST) != 0) {
            if (ThorPowers.spend(player, ThorAbility.LIGHTNING_BLAST.energy())) {
                blastTick(player, level);
            } else {
                ThorPowers.setFlag(player, ThorPowers.F_BLAST, false);
                anim(player, "none");
                player.displayClientMessage(Component.translatable("message.flash.thor_no_energy"), true);
            }
        }
        if ((flags & ThorPowers.F_SPIN) != 0) {
            if (!holdingHammer(player) || !ThorPowers.spend(player, ThorAbility.HAMMER_SPIN.energy())) {
                ThorPowers.setFlag(player, ThorPowers.F_SPIN, false);
                anim(player, "none");
            } else {
                spinTick(player, level);
            }
        }
        if ((flags & ThorPowers.F_BLOCK) != 0) {
            if (!holdingHammer(player) || !ThorPowers.spend(player, ThorAbility.HAMMER_SPIN.energy())) {
                ThorPowers.setFlag(player, ThorPowers.F_BLOCK, false);
                anim(player, "none");
            }
        }
        if ((flags & ThorPowers.F_FURY) != 0) {
            if (player.tickCount % 4 == 0) {
                Vec3 c = player.position().add(0.0D, 1.0D, 0.0D);
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y, c.z, 6, 0.4D, 0.8D, 0.4D, 0.1D);
            }
            if (player.tickCount % 20 == 0) {
                furyArcs(player, level);
            }
        }
        Session s = SESSIONS.get(player.getUUID());
        if (s == null) {
            return;
        }
        long now = level.getGameTime();
        Iterator<Pending> it = s.pending.iterator();
        List<Pending> due = new ArrayList<>();
        while (it.hasNext()) {
            Pending p = it.next();
            if (now >= p.at()) {
                it.remove();
                due.add(p);
            }
        }
        for (Pending p : due) {
            p.action().accept(player);
        }
        if (s.sparkTicks >= 0) {
            sparkTick(player, level, s);
        }
        if (s.vortexTicks >= 0) {
            vortexTick(player, level, s);
        }
        if (s.tornadoTicks >= 0) {
            tornadoTick(player, level, s);
        }
        if (s.ultTicks >= 0) {
            ultTick(player, level, s);
        }
        if (s.strikerTicks >= 0) {
            strikerTick(player, level, s);
        }
        if (s.godBlastTicks >= 0) {
            godBlastTick(player, level, s);
        }
        if (s.bifrostTicks >= 0) {
            bifrostTick(player, level, s);
        }
        if (s.kingTicks >= 0) {
            kingTick(player, level, s);
        }
        if (s.judgeTicks >= 0) {
            judgeTick(player, level, s);
        }
        if (s.domainTicks >= 0) {
            domainTick(player, level, s);
        }
    }

    // ------------------------------------------------------------------
    // Summon Mjolnir / Stormbreaker
    // ------------------------------------------------------------------

    private static boolean summon(ServerPlayer player) {
        MjolnirEntity out = MjolnirEntity.find(player);
        if (out != null) {
            out.recall();
            return true;
        }
        // lying on the ground somewhere around: it lifts off and flies to the hand
        MjolnirEntity lying = MjolnirEntity.nearestStatue(player, 256.0D);
        if (lying != null && !holdingHammer(player)) {
            lying.callTo(player);
            sound(player, throwSound(player), 1.0F, 0.8F);
            return true;
        }
        if (holdingHammer(player)) {
            player.displayClientMessage(Component.translatable("message.flash.thor_hammer_in_hand"), true);
            return false;
        }
        // in the inventory: straight to the hand
        for (int i = 0; i < player.getInventory().items.size(); i++) {
            ItemStack st = player.getInventory().items.get(i);
            if (st.getItem() instanceof MjolnirItem) {
                if (player.getMainHandItem().isEmpty()) {
                    player.setItemInHand(InteractionHand.MAIN_HAND, st.copy());
                    player.getInventory().items.set(i, ItemStack.EMPTY);
                    sound(player, ModSounds.THOR_GRAB.get(), 1.0F, 1.0F);
                    return true;
                }
                player.displayClientMessage(Component.translatable("message.flash.thor_hand_busy"), true);
                return false;
            }
        }
        summonFromAfar(player);
        return true;
    }

    // ------------------------------------------------------------------
    // Lightning Blast (held): a continuous bolt, a pulse every half second
    // ------------------------------------------------------------------

    private static void blastTick(ServerPlayer player, ServerLevel level) {
        if (player.tickCount % 10 != 0) {
            return;
        }
        HitResult hit = trace(player, 32.0D);
        float power = ThorPowers.power(player);
        float charge = ThorPowers.charge(player);
        float bonus = charge > 0.0F ? 1.2F : 1.0F;
        if (charge > 0.0F) {
            ThorPowers.setCharge(player, charge - 2.0F);
        }
        if (hit instanceof EntityHitResult eh && eh.getEntity() instanceof LivingEntity target) {
            hurt(player, target, 6.0F * power * bonus);
            herald(player, target, 3.0F * power);
            if (player.getRandom().nextFloat() < 0.15F) {
                stun(target, 20);
            }
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, target.getX(), target.getY() + target.getBbHeight() * 0.5D,
                    target.getZ(), 16, 0.3D, 0.4D, 0.3D, 0.3D);
            ThorPowers.addXp(player, 0.3F);
        } else if (hit instanceof BlockHitResult bh && hit.getType() != HitResult.Type.MISS) {
            Vec3 p = bh.getLocation();
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, 10, 0.2D, 0.2D, 0.2D, 0.2D);
        }
    }

    // ------------------------------------------------------------------
    // Hammer Spin (held): a vortex in front that keeps damage and projectiles away
    // ------------------------------------------------------------------

    private static void spinTick(ServerPlayer player, ServerLevel level) {
        Vec3 c = player.getEyePosition().add(player.getLookAngle().scale(1.2D));
        for (Entity e : level.getEntities(player, player.getBoundingBox().inflate(4.0D), x -> x.isAlive())) {
            if (e instanceof Projectile pr && pr.getOwner() != player) {
                Vec3 away = e.position().subtract(player.position()).normalize();
                e.setDeltaMovement(away.scale(Math.max(0.6D, e.getDeltaMovement().length())));
                e.hurtMarked = true;
            } else if (e instanceof LivingEntity living && e.distanceTo(player) < 3.0D && player.tickCount % 10 == 0) {
                hurt(player, living, 2.0F * ThorPowers.power(player));
                push(living, player.position(), 0.9D);
            }
        }
        if (player.tickCount % 2 == 0) {
            double a = player.tickCount * 0.9D;
            Vec3 right = new Vec3(-player.getLookAngle().z, 0, player.getLookAngle().x).normalize();
            Vec3 p = c.add(right.scale(Math.cos(a) * 0.8D)).add(0.0D, Math.sin(a) * 0.8D, 0.0D);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, 2, 0.05D, 0.05D, 0.05D, 0.05D);
        }
        if (player.tickCount % 20 == 0) {
            sound(player, ModSounds.THOR_SPIN.get(), 0.8F, 1.0F);
        }
    }

    // ------------------------------------------------------------------
    // Lightning Strike: the hammer raised, a bolt comes down on it (anywhere, caves too), charging it and
    // shocking everything around
    // ------------------------------------------------------------------

    private static boolean lightningStrike(ServerPlayer player) {
        anim(player, "thor_lightning_strike");
        sound(player, ModSounds.THOR_LIGHTNING.get(), 1.0F, 1.0F);
        later(player, 10, p -> {
            caster(p);
            ServerLevel l = p.serverLevel();
            Vec3 hammer = p.position().add(p.getLookAngle().multiply(1.0D, 0.0D, 1.0D).normalize().scale(0.25D))
                    .add(0.0D, p.getBbHeight() + 0.7D, 0.0D);
            // the bolt from above (drawn through the ceiling in a cave) onto the hammer
            for (int i = 0; i < 3; i++) {
                bolt(l, hammer.add(l.random.nextGaussian() * 0.6D, 30.0D, l.random.nextGaussian() * 0.6D), hammer,
                        2.0F);
            }
            strikeVisual(l, p.position());
            double radius = 6.0D;
            float power = ThorPowers.power(p);
            for (LivingEntity e : around(l, p, p.position(), radius)) {
                double f = 1.0D - e.position().distanceTo(p.position()) / (radius + 1.0D);
                hurt(p, e, (float) (10.0F * power * (0.4D + 0.6D * f)));
                push(e, p.position(), 0.6D + 1.2D * f);
                bolt(l, hammer, e.position().add(0.0D, e.getBbHeight() * 0.5D, 0.0D), 0.7F);
            }
            fx(l, ThorFxPacket.RING, p.position(), Vec3.ZERO, (float) radius + 1.0F);
            SupermanAbilities.cracks(l, p.position(), 3.0F);
            // the hammer drinks the bolt
            ThorPowers.setCharge(p, ThorPowers.charge(p) + 35.0F);
            ThorPowers.setEnergy(p, ThorPowers.energy(p) + 10.0F);
            ThorPowers.addXp(p, 1.0F);
        });
        return true;
    }

    // ------------------------------------------------------------------
    // Storm Striker: a ~10 block leap with the hammer up, then down on the ground: cracks, a bolt and a ring of
    // lightning (works standing or on the move: the leap keeps the run)
    // ------------------------------------------------------------------

    private static boolean stormStriker(ServerPlayer player) {
        Session s = session(player);
        s.strikerTicks = 0;
        anim(player, "thor_striker_air");
        Vec3 flat = player.getLookAngle().multiply(1.0D, 0.0D, 1.0D);
        flat = flat.lengthSqr() < 1.0E-4D ? Vec3.ZERO : flat.normalize();
        Vec3 run = player.getDeltaMovement().multiply(1.0D, 0.0D, 1.0D);
        player.setDeltaMovement(run.scale(0.6D).add(flat.scale(0.45D)).add(0.0D, 1.32D, 0.0D));
        player.hurtMarked = true;
        player.getAbilities().flying = false;
        player.onUpdateAbilities();
        sound(player, ModSounds.THOR_SPIN_ATTACK.get(), 1.0F, 0.9F);
        SupermanAbilities.groundPuff(player.serverLevel(), player.position(), 1.5D, 16);
        return true;
    }

    private static void strikerTick(ServerPlayer player, ServerLevel level, Session s) {
        int t = s.strikerTicks++;
        player.fallDistance = 0.0F;
        if (player.getAbilities().flying) {
            player.getAbilities().flying = false;
            player.onUpdateAbilities();
        }
        if (t % 2 == 0) {
            Vec3 h = player.position().add(0.0D, player.getBbHeight() + 0.8D, 0.0D);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, h.x, h.y, h.z, 4, 0.2D, 0.2D, 0.2D, 0.1D);
        }
        // a little push down after the top, so the slam comes down hard
        if (t > 10 && player.getDeltaMovement().y < 0.0D && player.getDeltaMovement().y > -1.6D) {
            player.setDeltaMovement(player.getDeltaMovement().add(0.0D, -0.12D, 0.0D));
            player.hurtMarked = true;
        }
        if (t < 4 || !(player.onGround() || player.isInWater() || t > 100)) {
            return;
        }
        s.strikerTicks = -1;
        Vec3 at = player.position();
        anim(player, "thor_striker_slam");
        double radius = 7.0D;
        float power = ThorPowers.power(player);
        for (LivingEntity e : around(level, player, at, radius)) {
            double f = 1.0D - e.position().distanceTo(at) / (radius + 1.0D);
            hurt(player, e, (float) (16.0F * power * (0.35D + 0.65D * f)));
            push(e, at, 0.6D + 1.2D * f);
        }
        level.playSound(null, at.x, at.y, at.z, ModSounds.THOR_STORM_STRIKER.get(), SoundSource.PLAYERS, 1.6F, 1.0F);
        strikeVisual(level, at);
        bolt(level, at.add(0.0D, 30.0D, 0.0D), at.add(0.0D, 0.2D, 0.0D), 2.2F);
        fx(level, ThorFxPacket.RING, at, Vec3.ZERO, 9.0F);
        SupermanAbilities.cracks(level, at, 6.0F);
        SupermanAbilities.groundPuff(level, at, 3.0D, 30);
        if (ThorPowers.destroyBlocks(player)) {
            level.explode(player, at.x, at.y, at.z, 2.0F, Level.ExplosionInteraction.TNT);
        }
        ThorPowers.addXp(player, 1.5F);
        // the lightning runs out on the ground
        later(player, 6, p -> {
            caster(p);
            ServerLevel l = p.serverLevel();
            float pw = ThorPowers.power(p);
            for (LivingEntity e : around(l, p, at, 12.0D)) {
                hurt(p, e, 5.0F * pw);
                bolt(l, e.position().add(0.0D, e.getBbHeight() + 4.0D, 0.0D),
                        e.position().add(0.0D, e.getBbHeight() * 0.5D, 0.0D), 1.0F);
            }
            for (int i = 0; i < 8; i++) {
                double a = i * Math.PI / 4.0D;
                bolt(l, at.add(0.0D, 0.3D, 0.0D), at.add(Math.cos(a) * 7.0D, 0.2D, Math.sin(a) * 7.0D), 0.9F);
            }
            fx(l, ThorFxPacket.RING, at, Vec3.ZERO, 13.0F);
            l.playSound(null, at.x, at.y, at.z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 1.2F, 1.2F);
        });
    }

    // ------------------------------------------------------------------
    // Stormbreaker Storm Striker: the axe slammed in front, then three waves of lightning in a cone
    // ------------------------------------------------------------------

    private static boolean axeStriker(ServerPlayer player) {
        anim(player, "thor_stormbreaker_slam");
        Vec3 dir = player.getLookAngle().multiply(1.0D, 0.0D, 1.0D);
        Vec3 fwd = dir.lengthSqr() < 1.0E-4D ? new Vec3(0, 0, 1) : dir.normalize();
        later(player, 10, p -> {
            caster(p);
            ServerLevel l = p.serverLevel();
            Vec3 at = p.position().add(fwd.scale(1.5D));
            float power = ThorPowers.power(p);
            for (LivingEntity e : around(l, p, at, 4.5D)) {
                hurt(p, e, 13.0F * power);
                push(e, at, 1.2D);
            }
            l.playSound(null, at.x, at.y, at.z, ModSounds.THOR_SB_THUMP.get(), SoundSource.PLAYERS, 1.6F, 0.9F);
            SupermanAbilities.cracks(l, at, 3.5F);
            SupermanAbilities.groundPuff(l, at, 2.0D, 24);
            fx(l, ThorFxPacket.RING, at, Vec3.ZERO, 5.0F);
            if (ThorPowers.destroyBlocks(p)) {
                l.explode(p, at.x, at.y, at.z, 1.5F, Level.ExplosionInteraction.TNT);
            }
            for (int w = 0; w < 3; w++) {
                double reach = 5.0D + w * 4.0D;
                later(p, 4 + w * 5, q -> axeWave(q, at, fwd, reach));
            }
            ThorPowers.addXp(p, 1.5F);
        });
        return true;
    }

    private static void axeWave(ServerPlayer player, Vec3 from, Vec3 dir, double reach) {
        caster(player);
        ServerLevel level = player.serverLevel();
        float power = ThorPowers.power(player);
        double cos = Math.cos(Math.toRadians(40.0D));
        for (LivingEntity e : around(level, player, from, reach + 0.5D)) {
            Vec3 d = e.position().subtract(from).multiply(1.0D, 0.0D, 1.0D);
            double dist = d.length();
            if (dist < reach - 4.5D || (dist > 0.5D && d.normalize().dot(dir) < cos)) {
                continue;
            }
            hurt(player, e, 6.0F * power);
            push(e, from, 0.7D);
            bolt(level, e.position().add(0.0D, e.getBbHeight() + 3.0D, 0.0D),
                    e.position().add(0.0D, e.getBbHeight() * 0.5D, 0.0D), 0.8F);
        }
        fx(level, ThorFxPacket.WAVE, from, dir, (float) reach);
        level.playSound(null, from.x + dir.x * reach, from.y, from.z + dir.z * reach, SoundEvents.LIGHTNING_BOLT_IMPACT,
                SoundSource.PLAYERS, 1.0F, 1.3F);
    }

    // ------------------------------------------------------------------
    // Hammer Spark: the target thrown high, the hammer thrown after it, a bolt, Thor leaps after it (knees and
    // elbows bent), catches the hammer on the way and nails it to the ground from above
    // ------------------------------------------------------------------

    private static boolean hammerSpark(ServerPlayer player) {
        Session s = session(player);
        if (s.sparkTicks >= 0) {
            return false;
        }
        HitResult hit = trace(player, 8.0D);
        if (!(hit instanceof EntityHitResult eh) || !(eh.getEntity() instanceof LivingEntity)) {
            player.displayClientMessage(Component.translatable("message.flash.thor_no_target"), true);
            return false;
        }
        s.sparkTarget = eh.getEntity().getId();
        s.sparkTicks = 0;
        s.sparkSmash = -1;
        s.sparkFall = -1;
        anim(player, "thor_hammer_spark");
        ThorPowers.setFlag(player, ThorPowers.F_COMBO, true);
        return true;
    }

    private static void endSpark(ServerPlayer player, Session s, LivingEntity target) {
        if (target != null) {
            target.setNoGravity(false);
        }
        player.setNoGravity(false);
        s.sparkTicks = -1;
        s.sparkTarget = -1;
        s.sparkSmash = -1;
        s.sparkFall = -1;
        ThorPowers.setFlag(player, ThorPowers.F_COMBO, false);
    }

    private static void sparkTick(ServerPlayer player, ServerLevel level, Session s) {
        int t = s.sparkTicks++;
        Entity e = level.getEntity(s.sparkTarget);
        if (!(e instanceof LivingEntity target) || !target.isAlive() || t > 120) {
            endSpark(player, s, e instanceof LivingEntity l ? l : null);
            return;
        }
        float power = ThorPowers.power(player);
        player.fallDistance = 0.0F;
        Vec3 mid = target.position().add(0.0D, target.getBbHeight() * 0.5D, 0.0D);
        if (s.sparkFall >= 0) {
            // 7. nailed down: cracks and a bolt where it lands (a crater of about one TNT with destruction on)
            int f = s.sparkFall++;
            if (target.onGround() || target.isInWater() || f > 30) {
                Vec3 at = target.position();
                hurt(player, target, 8.0F * power);
                stun(target, 30);
                strikeVisual(level, at);
                bolt(level, at.add(0.0D, 26.0D, 0.0D), at.add(0.0D, 0.3D, 0.0D), 1.8F);
                fx(level, ThorFxPacket.RING, at, Vec3.ZERO, 6.0F);
                SupermanAbilities.cracks(level, at, 4.0F);
                SupermanAbilities.groundPuff(level, at, 2.5D, 30);
                for (LivingEntity o : around(level, player, at, 4.0D)) {
                    if (o != target) {
                        hurt(player, o, 5.0F * power);
                        push(o, at, 1.0D);
                    }
                }
                if (ThorPowers.destroyBlocks(player)) {
                    level.explode(player, at.x, at.y, at.z, 4.0F, Level.ExplosionInteraction.TNT);
                } else {
                    level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y + 0.5D, at.z, 1, 0, 0, 0, 0);
                    level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 2.0F,
                            0.9F);
                }
                ThorPowers.addXp(player, 2.0F);
                endSpark(player, s, target);
            }
            return;
        }
        if (t <= 4) {
            // closing in (no teleport: a run toward it)
            Vec3 d = target.position().subtract(player.position()).multiply(1.0D, 0.0D, 1.0D);
            if (d.length() > 2.2D) {
                Vec3 v = d.normalize().scale(Math.min(0.9D, d.length() * 0.3D));
                player.setDeltaMovement(v.x, player.getDeltaMovement().y, v.z);
                player.hurtMarked = true;
            }
        } else if (t == 5) {
            // 1. thrown up high
            hurt(player, target, 7.0F * power);
            target.setDeltaMovement(0.0D, 1.55D, 0.0D);
            target.hurtMarked = true;
            sound(player, ModSounds.THOR_SPIN_ATTACK.get(), 1.0F, 1.0F);
            SupermanAbilities.groundPuff(level, target.position(), 1.5D, 16);
        } else if (t == 9) {
            // 2. the hammer thrown at it
            ItemStack weapon = weaponInHand(player);
            if (!weapon.isEmpty()) {
                MjolnirEntity h = throwHammer(player, weapon, 0.5F);
                Vec3 to = mid.subtract(player.getEyePosition());
                h.setDeltaMovement(to.normalize().scale(2.6D));
                h.home(target);
            }
        } else if (t == 13) {
            // 3. the bolt; it stays up there, stunned
            hurt(player, target, 6.0F * power);
            bolt(level, mid.add(0.0D, 26.0D, 0.0D), mid, 1.8F);
            strikeVisual(level, target.position());
            if (!(target instanceof Player)) {
                target.setNoGravity(true);
            }
            stun(target, 40);
        } else if (t == 14) {
            // 4. the leap: knees and elbows bent first
            SupermanAbilities.anim(player, "charge_takeoff9");
        } else if (t == 18) {
            SupermanAbilities.anim(player, "charged_launch9");
            SupermanAbilities.cracks(level, player.position(), 2.5F);
            SupermanAbilities.groundPuff(level, player.position(), 2.0D, 20);
            sound(player, ModSounds.THOR_SPIN_ATTACK.get(), 1.0F, 0.8F);
            player.setNoGravity(true);
            // 5. the hammer back to the hand on the way up
            MjolnirEntity out = MjolnirEntity.find(player);
            if (out != null) {
                out.recall();
            }
        }
        if (t >= 13 && s.sparkSmash < 0) {
            // held in the air
            target.setDeltaMovement(Vec3.ZERO);
            target.hurtMarked = true;
            target.fallDistance = 0.0F;
        }
        if (t >= 18 && s.sparkSmash < 0) {
            // flying up to it, steered every tick
            Vec3 aim = target.position().add(0.0D, target.getBbHeight() + 0.4D, 0.0D)
                    .add(player.position().subtract(target.position()).multiply(1.0D, 0.0D, 1.0D).normalize().scale(1.0D));
            Vec3 d = aim.subtract(player.position());
            double len = d.length();
            Vec3 v = len < 1.0E-3D ? Vec3.ZERO : d.scale(Math.min(1.4D, len) / len);
            player.setDeltaMovement(v);
            player.hurtMarked = true;
            if (len < 1.6D || t > 40) {
                // 6. from above, on the head
                s.sparkSmash = t;
                anim(player, "thor_spark_smash");
                player.setDeltaMovement(Vec3.ZERO);
            }
        }
        if (s.sparkSmash >= 0) {
            player.setDeltaMovement(Vec3.ZERO);
            player.hurtMarked = true;
            if (t == s.sparkSmash + 6) {
                hurt(player, target, 10.0F * power);
                bolt(level, player.getEyePosition(), mid, 1.2F);
                sound(player, hitSound(player), 1.4F, 0.8F);
                target.setNoGravity(false);
                target.setDeltaMovement(0.0D, -3.2D, 0.0D);
                target.hurtMarked = true;
                player.setNoGravity(false);
                player.setDeltaMovement(0.0D, 0.15D, 0.0D);
                player.hurtMarked = true;
                s.sparkFall = 0;
            }
        }
    }

    // ------------------------------------------------------------------
    // Lightning Vortex: Thor becomes a bolt that jumps from enemy to enemy (up to four zips, each one a shock that
    // arcs to the ones around), then whirls the storm around him and blows it out
    // ------------------------------------------------------------------

    private static boolean vortex(ServerPlayer player) {
        Session s = session(player);
        ServerLevel level = player.serverLevel();
        s.vortexTicks = 0;
        s.vortexIndex = 0;
        s.vortexZip = 0;
        s.vortexTargets.clear();
        s.vortexDir = player.getLookAngle().normalize();
        s.vortexLast = player.position().add(0.0D, 1.0D, 0.0D);
        Vec3 look = player.getLookAngle();
        List<LivingEntity> found = around(level, player, player.position(), 14.0D);
        found.removeIf(e -> e.position().subtract(player.position()).normalize().dot(look) < -0.2D);
        found.sort((a, b) -> Double.compare(a.distanceToSqr(player), b.distanceToSqr(player)));
        for (int i = 0; i < Math.min(4, found.size()); i++) {
            s.vortexTargets.add(found.get(i).getId());
        }
        anim(player, "thor_vortex");
        ThorPowers.setFlag(player, ThorPowers.F_TWIRL, true);
        player.setNoGravity(true);
        sound(player, ModSounds.THOR_SPIN_ATTACK.get(), 1.2F, 0.9F);
        sound(player, ModSounds.THOR_LIGHTNING.get(), 1.0F, 1.2F);
        return true;
    }

    private static void vortexTick(ServerPlayer player, ServerLevel level, Session s) {
        int t = s.vortexTicks++;
        float power = ThorPowers.power(player);
        player.fallDistance = 0.0F;
        Vec3 c = player.position().add(0.0D, 1.0D, 0.0D);
        // the trail of the bolt Thor became
        bolt(level, s.vortexLast, c, 0.9F);
        s.vortexLast = c;
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y, c.z, 6, 0.3D, 0.5D, 0.3D, 0.15D);
        if (s.vortexIndex < Math.max(1, s.vortexTargets.size())) {
            // zipping
            Entity e = s.vortexTargets.isEmpty() ? null : level.getEntity(s.vortexTargets.get(s.vortexIndex));
            Vec3 to = e instanceof LivingEntity l && l.isAlive() ? l.position()
                    : player.position().add(s.vortexDir.multiply(1.0D, 0.0D, 1.0D).scale(1.6D));
            Vec3 d = to.subtract(player.position());
            double len = d.length();
            int zip = s.vortexZip++;
            if (e == null && s.vortexTargets.isEmpty()) {
                // nobody around: a forward rush of 10 blocks
                player.setDeltaMovement(s.vortexDir.scale(1.4D).add(0.0D, 0.02D, 0.0D));
                player.hurtMarked = true;
                for (LivingEntity o : around(level, player, c, 3.0D)) {
                    if (o.invulnerableTime <= 10) {
                        hurt(player, o, 5.0F * power);
                        push(o, c, 1.0D);
                    }
                }
                if (zip >= 7) {
                    s.vortexIndex = 1;
                    s.vortexZip = 0;
                }
                return;
            }
            if (len > 1.8D && zip < 8) {
                player.setDeltaMovement(d.scale(Math.min(2.2D, len) / len));
                player.hurtMarked = true;
                return;
            }
            player.setDeltaMovement(Vec3.ZERO);
            player.hurtMarked = true;
            if (e instanceof LivingEntity target && target.isAlive()) {
                Vec3 mid = target.position().add(0.0D, target.getBbHeight() * 0.5D, 0.0D);
                hurt(player, target, 5.5F * power);
                stun(target, 10);
                bolt(level, c, mid, 1.1F);
                for (LivingEntity o : around(level, player, target.position(), 3.5D)) {
                    if (o != target) {
                        hurt(player, o, 2.5F * power);
                        bolt(level, mid, o.position().add(0.0D, o.getBbHeight() * 0.5D, 0.0D), 0.6F);
                    }
                }
                level.playSound(null, mid.x, mid.y, mid.z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS,
                        0.8F, 1.5F);
            }
            s.vortexIndex++;
            s.vortexZip = 0;
            return;
        }
        // the whirl: everyone around pulled in, then blown out
        int w = s.vortexZip++;
        if (w < 14) {
            player.setDeltaMovement(Vec3.ZERO);
            player.hurtMarked = true;
            for (LivingEntity e : around(level, player, c, 7.0D)) {
                Vec3 in = c.subtract(e.position()).multiply(1.0D, 0.0D, 1.0D);
                Vec3 swirl = new Vec3(-in.z, 0.0D, in.x).normalize();
                Vec3 m = (in.lengthSqr() > 2.0D ? in.normalize().scale(0.25D) : Vec3.ZERO).add(swirl.scale(0.3D));
                e.setDeltaMovement(m.x, 0.08D, m.z);
                e.hurtMarked = true;
            }
            for (int i = 0; i < 3; i++) {
                double a = w * 0.9D + i * 2.1D;
                bolt(level, c, c.add(Math.cos(a) * 3.5D, Math.sin(a * 0.7D) * 1.2D, Math.sin(a) * 3.5D), 0.7F);
            }
            return;
        }
        double radius = 7.0D;
        for (LivingEntity e : around(level, player, c, radius)) {
            double f = 1.0D - e.position().distanceTo(c) / (radius + 1.0D);
            hurt(player, e, (float) (9.0F * power * (0.4D + 0.6D * f)));
            push(e, c, 1.0D + 1.4D * f);
        }
        for (int i = 0; i < 10; i++) {
            double a = i * Math.PI / 5.0D;
            bolt(level, c, c.add(Math.cos(a) * radius, -0.6D, Math.sin(a) * radius), 1.0F);
        }
        fx(level, ThorFxPacket.RING, player.position(), Vec3.ZERO, (float) radius + 1.0F);
        level.playSound(null, c.x, c.y, c.z, ModSounds.THOR_STORM_STRIKER.get(), SoundSource.PLAYERS, 1.2F, 1.2F);
        player.setNoGravity(false);
        ThorPowers.setFlag(player, ThorPowers.F_TWIRL, false);
        anim(player, "none");
        ThorPowers.addXp(player, 1.5F);
        s.vortexTicks = -1;
    }

    // ------------------------------------------------------------------
    // Thunder's Fury (Warrior's Fury + Overcharge in one): 15 s, eyes glowing, lightning all over the body,
    // +25 % damage, -15 % damage taken, -25 % energy damage, the Mjolnir charge up to 150 % (+50 % at once),
    // punches that shock and arcs to the enemies around. 20 s of cooldown.
    // ------------------------------------------------------------------

    private static boolean fury(ServerPlayer player) {
        ThorPowers.setTimer(player, ThorPowers.F_FURY, ThorPowers.FURY_TICKS);
        ThorPowers.setCharge(player, ThorPowers.charge(player) + 50.0F);
        anim(player, "thor_lightningclash");
        strikeVisual(player.serverLevel(), player.position());
        fx(player.serverLevel(), ThorFxPacket.RING, player.position(), Vec3.ZERO, 5.0F);
        sound(player, ModSounds.THOR_OVERCHARGE_START.get(), 1.0F, 1.0F);
        line(player);
        player.displayClientMessage(Component.translatable("message.flash.thor_fury"), true);
        return true;
    }

    /** Fury: a punch has 75 % chance to shock. */
    public static void furyPunch(ServerPlayer player, LivingEntity target) {
        caster(player);
        if (player.getRandom().nextFloat() < 0.75F) {
            ServerLevel level = player.serverLevel();
            float d = 2.5F * ThorPowers.power(player);
            hurt(player, target, d);
            bolt(level, target.position().add(0.0D, target.getBbHeight() + 2.0D, 0.0D),
                    target.position().add(0.0D, target.getBbHeight() * 0.5D, 0.0D), 0.7F);
            herald(player, target, d * 0.5F);
        }
    }

    private static void furyArcs(ServerPlayer player, ServerLevel level) {
        float power = ThorPowers.power(player);
        Vec3 c = player.position().add(0.0D, 1.2D, 0.0D);
        int n = 0;
        for (LivingEntity e : around(level, player, c, 6.0D)) {
            if (n++ >= 2) {
                break;
            }
            hurt(player, e, 1.5F * power);
            bolt(level, c, e.position().add(0.0D, e.getBbHeight() * 0.5D, 0.0D), 0.6F);
        }
        if (player.tickCount % 60 == 0) {
            sound(player, ModSounds.THOR_OVERCHARGE_LOOP.get(), 0.5F, 1.0F);
        }
    }

    // ------------------------------------------------------------------
    // Mighty Tornado: a giant tornado of wind and lightning that pulls creatures and projectiles
    // ------------------------------------------------------------------

    private static boolean tornado(ServerPlayer player) {
        HitResult hit = trace(player, 24.0D);
        Vec3 at = hit.getType() == HitResult.Type.MISS ? player.position().add(player.getLookAngle().scale(12.0D))
                : hit.getLocation();
        ServerLevel level = player.serverLevel();
        BlockHitResult down = level.clip(new ClipContext(at, at.subtract(0.0D, 20.0D, 0.0D), ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, player));
        if (down.getType() != HitResult.Type.MISS) {
            at = down.getLocation();
        }
        Session s = session(player);
        s.tornado = at;
        s.tornadoTicks = 0;
        sound(player, ModSounds.THOR_SPIN.get(), 2.0F, 0.6F);
        return true;
    }

    private static void tornadoTick(ServerPlayer player, ServerLevel level, Session s) {
        int t = s.tornadoTicks++;
        if (t > 160) {
            s.tornadoTicks = -1;
            return;
        }
        Vec3 c = s.tornado;
        float power = ThorPowers.power(player);
        for (Entity e : level.getEntities(player, new AABB(c, c).inflate(11.0D, 14.0D, 11.0D), x -> x.isAlive())) {
            Vec3 d = c.subtract(e.position());
            double flat = Math.sqrt(d.x * d.x + d.z * d.z);
            if (flat > 11.0D) {
                continue;
            }
            Vec3 in = new Vec3(d.x, 0.0D, d.z).normalize();
            Vec3 swirl = new Vec3(-in.z, 0.0D, in.x);
            double pull = flat > 2.0D ? 0.18D : 0.0D;
            double lift = e.getY() < c.y + 10.0D ? 0.16D : -0.05D;
            Vec3 m = e.getDeltaMovement().scale(0.6D).add(in.scale(pull)).add(swirl.scale(0.35D)).add(0.0D, lift, 0.0D);
            e.setDeltaMovement(m);
            e.hurtMarked = true;
            e.fallDistance = 0.0F;
            if (e instanceof LivingEntity living && t % 10 == 0) {
                hurt(player, living, 2.0F * power);
                if (player.getRandom().nextInt(3) == 0) {
                    bolt(level, c.add(0.0D, 12.0D, 0.0D), living.position().add(0.0D, living.getBbHeight() * 0.5D, 0.0D),
                            0.9F);
                }
            }
        }
        for (int i = 0; i < 6; i++) {
            double h = (t * 0.7D + i * 2.3D) % 14.0D;
            double r = 0.8D + h * 0.45D;
            double a = t * 0.6D + i * 1.05D + h;
            Vec3 p = c.add(Math.cos(a) * r, h, Math.sin(a) * r);
            level.sendParticles(ParticleTypes.CLOUD, p.x, p.y, p.z, 2, 0.2D, 0.2D, 0.2D, 0.02D);
            if (i % 2 == 0) {
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, 2, 0.1D, 0.1D, 0.1D, 0.1D);
            }
        }
        if (t % 20 == 0) {
            level.playSound(null, c.x, c.y, c.z, SoundEvents.ELYTRA_FLYING, SoundSource.PLAYERS, 1.2F, 0.6F);
        }
        if (t % 30 == 0) {
            bolt(level, c.add(0.0D, 16.0D, 0.0D), c, 1.4F);
        }
    }

    // ------------------------------------------------------------------
    // God of Thunder (ultimate): Thor rises into the storm with the hammer whirling (the charge aura), the sky
    // pours lightning into it, he picks the point where he looks and comes down on it like a thunderbolt
    // ------------------------------------------------------------------

    private static boolean godOfThunder(ServerPlayer player) {
        Session s = session(player);
        if (s.ultTicks >= 0) {
            return false;
        }
        s.ultTicks = 0;
        s.ultStart = player.position();
        s.ultTarget = null;
        anim(player, "thor_god_thunder");
        ThorPowers.setFlag(player, ThorPowers.F_ULTIMATE, true);
        ThorPowers.setFlag(player, ThorPowers.F_TWIRL, true);
        player.getAbilities().flying = false;
        player.onUpdateAbilities();
        line(player);
        sound(player, ModSounds.THOR_LIGHTNING.get(), 1.4F, 0.8F);
        return true;
    }

    private static void ultTick(ServerPlayer player, ServerLevel level, Session s) {
        int t = s.ultTicks++;
        float power = ThorPowers.power(player);
        player.fallDistance = 0.0F;
        Vec3 hand = player.position().add(0.0D, player.getBbHeight() + 0.8D, 0.0D);
        if (t < 45) {
            // up into the storm (moved, not teleported), then hanging there gathering
            player.setNoGravity(true);
            Vec3 goal = s.ultStart.add(0.0D, 9.0D, 0.0D);
            Vec3 d = goal.subtract(player.position());
            player.setDeltaMovement(d.scale(t < 25 ? 0.18D : 0.3D));
            player.hurtMarked = true;
            for (LivingEntity e : around(level, player, s.ultStart, 16.0D)) {
                Vec3 in = s.ultStart.subtract(e.position()).multiply(1.0D, 0.0D, 1.0D);
                if (in.lengthSqr() > 4.0D) {
                    e.setDeltaMovement(e.getDeltaMovement().add(in.normalize().scale(0.1D)));
                    e.hurtMarked = true;
                }
                e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 3, false, false));
            }
            if (t % (t < 25 ? 5 : 2) == 0) {
                bolt(level, hand.add(level.random.nextGaussian() * 3.0D, 16.0D, level.random.nextGaussian() * 3.0D), hand,
                        t < 25 ? 1.3F : 1.8F);
            }
            if (t % 10 == 0) {
                level.playSound(null, hand.x, hand.y, hand.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER,
                        0.8F, 1.2F);
            }
            if (t == 44) {
                HitResult hit = trace(player, 48.0D);
                Vec3 aim = hit.getType() == HitResult.Type.MISS ? player.position().add(player.getLookAngle().scale(20.0D))
                        : hit.getLocation();
                BlockHitResult down = level.clip(new ClipContext(aim.add(0.0D, 1.0D, 0.0D), aim.subtract(0.0D, 40.0D, 0.0D),
                        ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
                s.ultTarget = down.getType() != HitResult.Type.MISS ? down.getLocation() : aim;
                ThorPowers.setFlag(player, ThorPowers.F_TWIRL, false);
                anim(player, "thor_striker_air");
                sound(player, ModSounds.THOR_SPIN_ATTACK.get(), 1.4F, 0.7F);
            }
            return;
        }
        // the dive
        Vec3 d = s.ultTarget.subtract(player.position());
        double len = d.length();
        if (len > 1.5D && !player.onGround() && t < 90) {
            player.setDeltaMovement(d.scale(Math.min(2.4D, len) / len));
            player.hurtMarked = true;
            bolt(level, player.position().add(0.0D, 1.0D, 0.0D), player.position().subtract(d.normalize().scale(3.0D))
                    .add(0.0D, 1.0D, 0.0D), 1.0F);
            return;
        }
        Vec3 at = player.onGround() ? player.position() : s.ultTarget;
        player.setNoGravity(false);
        player.setDeltaMovement(Vec3.ZERO);
        player.hurtMarked = true;
        anim(player, "thor_striker_slam");
        double radius = 10.0D;
        for (LivingEntity e : around(level, player, at, radius)) {
            double f = 1.0D - e.position().distanceTo(at) / (radius + 1.0D);
            hurt(player, e, (float) (30.0F * power * (0.35D + 0.65D * f)));
            stun(e, 40);
            push(e, at, 1.0D + 2.0D * f);
        }
        for (int i = 0; i < 6; i++) {
            double a = i * Math.PI / 3.0D;
            strikeVisual(level, at.add(Math.cos(a) * 5.0D, 0.0D, Math.sin(a) * 5.0D));
        }
        strikeVisual(level, at);
        fx(level, ThorFxPacket.IMPACT, at, Vec3.ZERO, 11.0F);
        SupermanAbilities.cracks(level, at, 8.0F);
        SupermanAbilities.groundPuff(level, at, 4.0D, 50);
        level.playSound(null, at.x, at.y, at.z, ModSounds.THOR_STORM_STRIKER.get(), SoundSource.PLAYERS, 2.0F, 0.8F);
        if (ThorPowers.destroyBlocks(player)) {
            level.explode(player, at.x, at.y, at.z, 4.0F, Level.ExplosionInteraction.TNT);
        }
        ThorPowers.addXp(player, 4.0F);
        ThorPowers.setFlag(player, ThorPowers.F_ULTIMATE, false);
        s.ultTicks = -1;
    }

    // ------------------------------------------------------------------
    // God Blast (crouch + ultimate, Mjolnir): the hammer raised drinking lightning, then a giant beam forward
    // ------------------------------------------------------------------

    private static boolean godBlast(ServerPlayer player) {
        Session s = session(player);
        s.godBlastTicks = 0;
        anim(player, "thor_god_blast");
        ThorPowers.setFlag(player, ThorPowers.F_ULTIMATE, true);
        ThorPowers.setFlag(player, ThorPowers.F_TWIRL, true);
        sound(player, ModSounds.THOR_GOD_BLAST.get(), 1.5F, 1.0F);
        line(player);
        return true;
    }

    private static void godBlastTick(ServerPlayer player, ServerLevel level, Session s) {
        int t = s.godBlastTicks++;
        Vec3 hand = player.position().add(0.0D, player.getBbHeight() + 0.8D, 0.0D);
        if (t < 22) {
            if (t % 3 == 0) {
                bolt(level, hand.add(level.random.nextGaussian() * 2.0D, 18.0D, level.random.nextGaussian() * 2.0D), hand,
                        1.6F);
            }
            return;
        }
        if (t == 22) {
            ThorPowers.setFlag(player, ThorPowers.F_TWIRL, false);
        }
        if (t > 42) {
            ThorPowers.setFlag(player, ThorPowers.F_ULTIMATE, false);
            s.godBlastTicks = -1;
            return;
        }
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        Vec3 from = eye.add(look.scale(1.0D)).subtract(0.0D, 0.25D, 0.0D);
        BlockHitResult block = level.clip(new ClipContext(eye, eye.add(look.scale(48.0D)), ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, player));
        Vec3 end = block.getType() == HitResult.Type.MISS ? eye.add(look.scale(48.0D)) : block.getLocation();
        if (t % 2 == 0) {
            fx(level, ThorFxPacket.BEAM, from, end, 1.0F);
        }
        if (t % 5 == 2) {
            float power = ThorPowers.power(player);
            AABB box = new AABB(from, end).inflate(2.0D);
            for (Entity e : level.getEntities(player, box, x -> x instanceof LivingEntity && x.isAlive())) {
                Vec3 p = e.position().add(0.0D, e.getBbHeight() * 0.5D, 0.0D);
                Vec3 ab = end.subtract(from);
                double k = Math.max(0.0D, Math.min(1.0D, p.subtract(from).dot(ab) / ab.lengthSqr()));
                if (p.distanceTo(from.add(ab.scale(k))) < 2.2D) {
                    hurt(player, (LivingEntity) e, 9.0F * power);
                    push((LivingEntity) e, from, 1.2D);
                }
            }
            if (block.getType() != HitResult.Type.MISS) {
                fx(level, ThorFxPacket.RING, end, Vec3.ZERO, 3.0F);
            }
        }
        if (t == 30 && block.getType() != HitResult.Type.MISS && ThorPowers.destroyBlocks(player)) {
            level.explode(player, end.x, end.y, end.z, 3.0F, Level.ExplosionInteraction.TNT);
        }
    }

    // ------------------------------------------------------------------
    // Bifrost (Stormbreaker): the rainbow bridge. On a creature in sight: it comes down on it. Otherwise it takes
    // Thor where he looks (up to 256 blocks). Crouch: saves this place; looking straight up: back to it.
    // ------------------------------------------------------------------

    private static boolean bifrost(ServerPlayer player) {
        Session s = session(player);
        ServerLevel level = player.serverLevel();
        CompoundTag data = player.getPersistentData();
        if (player.isShiftKeyDown()) {
            CompoundTag home = new CompoundTag();
            home.putDouble("x", player.getX());
            home.putDouble("y", player.getY());
            home.putDouble("z", player.getZ());
            home.putString("dim", level.dimension().location().toString());
            data.put(BIFROST_HOME, home);
            player.displayClientMessage(Component.translatable("message.flash.thor_bifrost_saved"), true);
            return false;
        }
        HitResult hit = trace(player, 64.0D);
        if (hit instanceof EntityHitResult eh && eh.getEntity() instanceof LivingEntity target) {
            anim(player, "thor_bifrost");
            int id = target.getId();
            later(player, 8, p -> {
                caster(p);
                ServerLevel l = p.serverLevel();
                if (l.getEntity(id) instanceof LivingEntity t && t.isAlive()) {
                    Vec3 at = t.position();
                    fx(l, ThorFxPacket.BIFROST, at, new Vec3(24, 0, 0), 1.8F);
                    hurt(p, t, 16.0F * ThorPowers.power(p));
                    t.setDeltaMovement(0.0D, 1.0D, 0.0D);
                    t.hurtMarked = true;
                    l.playSound(null, at.x, at.y, at.z, ModSounds.THOR_BIFROST_ARRIVE.get(), SoundSource.PLAYERS,
                            1.4F, 1.0F);
                }
            });
            return true;
        }
        Vec3 dest = null;
        if (player.getXRot() < -70.0F && data.contains(BIFROST_HOME)) {
            CompoundTag home = data.getCompound(BIFROST_HOME);
            if (home.getString("dim").equals(level.dimension().location().toString())) {
                dest = new Vec3(home.getDouble("x"), home.getDouble("y"), home.getDouble("z"));
            } else {
                player.displayClientMessage(Component.translatable("message.flash.thor_bifrost_other_dim"), true);
                return false;
            }
        }
        if (dest == null) {
            Vec3 eye = player.getEyePosition();
            Vec3 far = eye.add(player.getLookAngle().scale(256.0D));
            BlockHitResult b = level.clip(new ClipContext(eye, far, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
                    player));
            Vec3 aim = b.getType() == HitResult.Type.MISS ? eye.add(player.getLookAngle().scale(128.0D))
                    : b.getLocation().subtract(player.getLookAngle().scale(0.6D));
            BlockHitResult ground = level.clip(new ClipContext(aim, aim.subtract(0.0D, 96.0D, 0.0D),
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, player));
            dest = ground.getType() == HitResult.Type.MISS ? aim : ground.getLocation();
        }
        // room to stand
        for (int i = 0; i < 12 && !level.noCollision(player, player.getBoundingBox().move(dest.subtract(player.position())));
             i++) {
            dest = dest.add(0.0D, 1.0D, 0.0D);
        }
        s.bifrostTicks = 0;
        s.bifrostDest = dest;
        anim(player, "thor_bifrost");
        return true;
    }

    private static void bifrostTick(ServerPlayer player, ServerLevel level, Session s) {
        int t = s.bifrostTicks++;
        player.fallDistance = 0.0F;
        if (t == 8) {
            anim(player, "thor_bifrostflight");
            fx(level, ThorFxPacket.BIFROST, player.position(), new Vec3(30, 0, 0), 1.6F);
            sound(player, ModSounds.THOR_BIFROST_AWAY.get(), 1.4F, 1.0F);
        }
        if (t == 16) {
            Vec3 d = s.bifrostDest;
            player.teleportTo(level, d.x, d.y, d.z, player.getYRot(), player.getXRot());
            player.setDeltaMovement(Vec3.ZERO);
            player.hurtMarked = true;
            fx(level, ThorFxPacket.BIFROST, d, new Vec3(30, 0, 0), 1.6F);
            level.playSound(null, d.x, d.y, d.z, ModSounds.THOR_BIFROST_ARRIVE.get(), SoundSource.PLAYERS, 1.4F, 1.0F);
            float power = ThorPowers.power(player);
            for (LivingEntity e : around(level, player, d, 3.5D)) {
                hurt(player, e, 4.0F * power);
                push(e, d, 1.2D);
            }
            ThorPowers.addXp(player, 1.0F);
            s.bifrostTicks = -1;
        }
    }

    // ------------------------------------------------------------------
    // Weapon of a King (Stormbreaker ultimate): the target locked, Thor wrapped in lightning for 3 s, then
    // Stormbreaker thrown at it, chasing it through everything in the way
    // ------------------------------------------------------------------

    private static boolean weaponOfAKing(ServerPlayer player) {
        HitResult hit = trace(player, 48.0D);
        if (!(hit instanceof EntityHitResult eh) || !(eh.getEntity() instanceof LivingEntity target)) {
            player.displayClientMessage(Component.translatable("message.flash.thor_no_target"), true);
            return false;
        }
        Session s = session(player);
        s.kingTicks = 0;
        s.kingTarget = target.getId();
        s.kingStart = player.position();
        anim(player, "thor_weapon_of_a_king");
        ThorPowers.setFlag(player, ThorPowers.F_ULTIMATE, true);
        line(player);
        return true;
    }

    private static void kingTick(ServerPlayer player, ServerLevel level, Session s) {
        int t = s.kingTicks++;
        player.fallDistance = 0.0F;
        Entity e = level.getEntity(s.kingTarget);
        LivingEntity target = e instanceof LivingEntity l && l.isAlive() ? l : null;
        if (t < 60) {
            player.setNoGravity(true);
            Vec3 goal = s.kingStart.add(0.0D, 1.5D * Math.min(1.0D, t / 40.0D), 0.0D);
            player.setDeltaMovement(goal.subtract(player.position()).scale(0.3D));
            player.hurtMarked = true;
            Vec3 c = player.position().add(0.0D, 1.0D, 0.0D);
            if (t % 3 == 0) {
                bolt(level, c.add(level.random.nextGaussian() * 2.0D, 18.0D, level.random.nextGaussian() * 2.0D), c, 1.4F);
            }
            if (t % 15 == 0) {
                level.playSound(null, c.x, c.y, c.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, 0.8F,
                        1.0F);
            }
            if (target != null) {
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 10, 2, false, false));
                target.addEffect(new MobEffectInstance(MobEffects.GLOWING, 10, 0, false, false));
            }
            return;
        }
        player.setNoGravity(false);
        ThorPowers.setFlag(player, ThorPowers.F_ULTIMATE, false);
        s.kingTicks = -1;
        ItemStack weapon = weaponInHand(player);
        if (weapon.isEmpty()) {
            return;
        }
        MjolnirEntity axe = throwHammer(player, weapon, 2.5F);
        if (target != null) {
            Vec3 to = target.position().add(0.0D, target.getBbHeight() * 0.5D, 0.0D).subtract(player.getEyePosition());
            axe.setDeltaMovement(to.normalize().scale(3.2D));
            axe.home(target);
        }
        ThorPowers.addXp(player, 3.0F);
    }

    // ------------------------------------------------------------------
    // Cosmic Judgement (Cosmic King): the hammer whirling over the head holds every enemy around in the air,
    // shocked by purple bolts; then it comes down and they blow up, healing Thor
    // ------------------------------------------------------------------

    private static boolean judgement(ServerPlayer player) {
        Session s = session(player);
        s.judgeTicks = 0;
        s.judged.clear();
        for (LivingEntity e : around(player.serverLevel(), player, player.position(), 8.0D)) {
            s.judged.add(e.getId());
        }
        anim(player, "thor_cosmic_judgement");
        ThorPowers.setFlag(player, ThorPowers.F_TWIRL, true);
        sound(player, ModSounds.THOR_SPIN.get(), 1.4F, 0.7F);
        return true;
    }

    private static void judgeTick(ServerPlayer player, ServerLevel level, Session s) {
        int t = s.judgeTicks++;
        Vec3 hand = player.position().add(0.0D, player.getBbHeight() + 0.9D, 0.0D);
        if (t < 33) {
            for (int id : s.judged) {
                if (level.getEntity(id) instanceof LivingEntity e && e.isAlive()) {
                    if (!(e instanceof Player)) {
                        e.setNoGravity(true);
                    }
                    double up = t < 10 ? 0.12D : 0.0D;
                    e.setDeltaMovement(0.0D, up, 0.0D);
                    e.hurtMarked = true;
                    e.fallDistance = 0.0F;
                    if ((t + id) % 5 == 0) {
                        bolt(level, hand, e.position().add(0.0D, e.getBbHeight() * 0.5D, 0.0D), 0.8F);
                    }
                }
            }
            if (t % 8 == 0) {
                sound(player, ModSounds.THOR_SPIN.get(), 0.9F, 0.8F);
            }
            return;
        }
        float power = ThorPowers.power(player);
        int hit = 0;
        for (int id : s.judged) {
            if (level.getEntity(id) instanceof LivingEntity e && e.isAlive()) {
                e.setNoGravity(false);
                hurt(player, e, 14.0F * power);
                push(e, player.position(), 1.5D);
                bolt(level, e.position().add(0.0D, 14.0D, 0.0D), e.position().add(0.0D, e.getBbHeight() * 0.5D, 0.0D),
                        1.3F);
                hit++;
            }
        }
        player.heal(Math.min(20.0F, 4.0F * hit));
        fx(level, ThorFxPacket.IMPACT, player.position(), Vec3.ZERO, 8.0F);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.THOR_STORM_STRIKER.get(),
                SoundSource.PLAYERS, 1.6F, 1.1F);
        SupermanAbilities.cracks(level, player.position(), 4.0F);
        ThorPowers.setFlag(player, ThorPowers.F_TWIRL, false);
        ThorPowers.addXp(player, 2.0F);
        s.judged.clear();
        s.judgeTicks = -1;
    }

    // ------------------------------------------------------------------
    // Devourer King (Cosmic King ultimate): 8 s of domain. Thor floats 10 blocks up in a dome of cosmic
    // lightning: nobody inside can leave, purple bolts keep falling on them, Thor takes 75 % less damage
    // ------------------------------------------------------------------

    private static final double DOMAIN = 14.0D;

    private static boolean devourer(ServerPlayer player) {
        Session s = session(player);
        s.domainTicks = 0;
        s.domainCenter = player.position();
        anim(player, "thor_devourer_king");
        ThorPowers.setFlag(player, ThorPowers.F_ULTIMATE, true);
        ThorPowers.setFlag(player, ThorPowers.F_DOMAIN, true);
        player.getAbilities().flying = false;
        player.onUpdateAbilities();
        line(player);
        sound(player, ModSounds.THOR_OVERCHARGE_START.get(), 1.5F, 0.6F);
        return true;
    }

    private static void domainTick(ServerPlayer player, ServerLevel level, Session s) {
        int t = s.domainTicks++;
        Vec3 c = s.domainCenter;
        player.fallDistance = 0.0F;
        player.setNoGravity(true);
        Vec3 goal = c.add(0.0D, 10.0D * Math.min(1.0D, t / 20.0D), 0.0D);
        player.setDeltaMovement(goal.subtract(player.position()).scale(0.3D));
        player.hurtMarked = true;
        float power = ThorPowers.power(player);
        List<LivingEntity> inside = new ArrayList<>();
        for (LivingEntity e : around(level, player, c, DOMAIN + 6.0D)) {
            Vec3 d = e.position().subtract(c).multiply(1.0D, 0.0D, 1.0D);
            if (d.length() > DOMAIN + 5.0D) {
                continue;
            }
            inside.add(e);
            if (d.length() > DOMAIN - 1.0D) {
                // the dome does not let anyone out
                Vec3 in = d.normalize().scale(-0.7D);
                e.setDeltaMovement(in.x, 0.1D, in.z);
                e.hurtMarked = true;
            }
            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 1, false, false));
        }
        if (t % 10 == 5 && !inside.isEmpty()) {
            Vec3 from = player.position().add(0.0D, 1.2D, 0.0D);
            for (int i = 0; i < Math.min(4, inside.size()); i++) {
                LivingEntity e = inside.get(level.random.nextInt(inside.size()));
                hurt(player, e, 3.5F * power);
                bolt(level, from, e.position().add(0.0D, e.getBbHeight() * 0.5D, 0.0D), 1.2F);
            }
            level.playSound(null, from.x, from.y, from.z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 0.8F,
                    0.7F);
        }
        if (t % 20 == 0) {
            fx(level, ThorFxPacket.DOME, c, Vec3.ZERO, (float) DOMAIN);
        }
        if (t < 160) {
            return;
        }
        // the domain collapses on whoever is still inside
        for (LivingEntity e : inside) {
            hurt(player, e, 10.0F * power);
            bolt(level, e.position().add(0.0D, 16.0D, 0.0D), e.position().add(0.0D, e.getBbHeight() * 0.5D, 0.0D),
                    1.5F);
        }
        fx(level, ThorFxPacket.IMPACT, c, Vec3.ZERO, (float) DOMAIN);
        level.playSound(null, c.x, c.y, c.z, ModSounds.THOR_STORM_STRIKER.get(), SoundSource.PLAYERS, 2.0F, 0.7F);
        player.setNoGravity(false);
        anim(player, "none");
        ThorPowers.setFlag(player, ThorPowers.F_ULTIMATE, false);
        ThorPowers.setFlag(player, ThorPowers.F_DOMAIN, false);
        ThorPowers.addXp(player, 4.0F);
        s.domainTicks = -1;
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /** Plays an animation of Thor, the mirrored one when the weapon is only in the left hand. */
    private static void anim(ServerPlayer player, String name) {
        if (LEFT.contains(name) && !(player.getMainHandItem().getItem() instanceof MjolnirItem)
                && player.getOffhandItem().getItem() instanceof MjolnirItem) {
            name = name + "left";
        }
        SupermanAbilities.anim(player, name);
    }

    private static void hurt(ServerPlayer player, LivingEntity target, float amount) {
        target.invulnerableTime = 0;
        target.hurt(player.serverLevel().damageSources().playerAttack(player), amount);
    }

    /** A vanilla lightning bolt that only shows and sounds (the damage is done by the ability). */
    public static void strikeVisual(ServerLevel level, Vec3 at) {
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt != null) {
            bolt.moveTo(at.x, at.y, at.z);
            bolt.setVisualOnly(true);
            level.addFreshEntity(bolt);
        }
    }

    /** A drawn lightning bolt between two points (seen by everyone around; purple for a Cosmic King). */
    public static void bolt(ServerLevel level, Vec3 a, Vec3 b, float scale) {
        fx(level, ThorFxPacket.BOLT, a, b, scale);
    }

    /** One of the effects drawn by ThorFx (rings, impacts, the Bifrost...), seen by everyone around. */
    public static void fx(ServerLevel level, int kind, Vec3 a, Vec3 b, float scale) {
        int k = kind | (cosmicCaster ? ThorFxPacket.COSMIC : 0);
        FlashNetwork.CHANNEL.send(PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(a.x, a.y, a.z,
                128.0D, level.dimension())), new ThorFxPacket(k, a.x, a.y, a.z, b.x, b.y, b.z, scale));
    }

    private static List<LivingEntity> around(ServerLevel level, ServerPlayer player, Vec3 at, double radius) {
        List<LivingEntity> out = new ArrayList<>();
        for (Entity e : level.getEntities(player, new AABB(at, at).inflate(radius), x -> x instanceof LivingEntity
                && x.isAlive() && x != player)) {
            if (e.position().distanceTo(at) <= radius + 0.5D) {
                out.add((LivingEntity) e);
            }
        }
        return out;
    }

    private static void push(LivingEntity e, Vec3 from, double strength) {
        Vec3 away = e.position().subtract(from).multiply(1.0D, 0.0D, 1.0D);
        away = away.lengthSqr() < 1.0E-4D ? new Vec3(0.0D, 0.0D, 1.0D) : away.normalize();
        e.setDeltaMovement(away.x * strength, 0.35D + 0.2D * strength, away.z * strength);
        e.hurtMarked = true;
    }

    private static void stun(LivingEntity e, int ticks) {
        e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, 6, false, false));
        e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, ticks, 2, false, false));
    }

    private static HitResult trace(ServerPlayer player, double range) {
        return SupermanAbilities.trace(player, range);
    }

    private static void sound(ServerPlayer player, SoundEvent sound, float volume, float pitch) {
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), sound, SoundSource.PLAYERS,
                volume, pitch);
    }

    /** One of the 15 lines of Thor ("For Asgard!"...). */
    private static void line(ServerPlayer player) {
        RandomSource r = player.getRandom();
        var lines = ModSounds.THOR_LINES;
        sound(player, lines.get(r.nextInt(lines.size())).get(), 1.0F, 1.0F);
    }
}
