package com.flash.thor;

import com.flash.FlashMod;
import com.flash.entity.MjolnirEntity;
import com.flash.item.MjolnirItem;
import com.flash.network.FlashNetwork;
import com.flash.network.SupermanFxPacket;
import com.flash.network.ThorFxPacket;
import com.flash.registry.ModItems;
import com.flash.registry.ModSounds;
import com.flash.superman.SupermanAbilities;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
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
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

/**
 * The abilities of Thor (the Mjolnir set of Legends: Superheroes), server side. Damage follows the punch of Thor
 * (see {@link ThorPowers#power}): a base value of 12 is one punch.
 */
@Mod.EventBusSubscriber(modid = FlashMod.MODID)
public final class ThorAbilities {

    private static final class Session {
        int sparkTarget = -1;
        int sparkTicks = -1;
        int vortexTicks = -1;
        Vec3 vortexDir;
        Vec3 tornado;
        int tornadoTicks = -1;
        int ultTicks = -1;
        Vec3 ultStart;
        Vec3 ultTarget;
        final List<Pending> pending = new ArrayList<>();
    }

    private record Pending(long at, Consumer<ServerPlayer> action) {
    }

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    private ThorAbilities() {
    }

    private static Session session(ServerPlayer player) {
        return SESSIONS.computeIfAbsent(player.getUUID(), id -> new Session());
    }

    private static void later(ServerPlayer player, int ticks, Consumer<ServerPlayer> action) {
        session(player).pending.add(new Pending(player.level().getGameTime() + ticks, action));
    }

    // ------------------------------------------------------------------
    // The hammer
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

    /** On the transformation: the hammer comes to the hand, flying in from the sky when it is not carried. */
    public static void giveHammer(ServerPlayer player) {
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
        MjolnirEntity e = new MjolnirEntity(level, player, new ItemStack(ModItems.MJOLNIR.get()), 0.6F);
        Vec3 back = player.getLookAngle().multiply(-1.0D, 0.0D, -1.0D).normalize();
        Vec3 from = player.position().add(back.scale(18.0D)).add(0.0D, 14.0D, 0.0D);
        e.setPos(from.x, from.y, from.z);
        e.recall();
        level.addFreshEntity(e);
        sound(player, ModSounds.THOR_THROW.get(), 1.0F, 0.8F);
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

    public static void throwHammer(ServerPlayer player, ItemStack stack, float force) {
        ServerLevel level = player.serverLevel();
        float charge = ThorPowers.charge(player) / 100.0F;
        MjolnirEntity e = new MjolnirEntity(level, player, stack, force);
        double speed = (1.6D + 1.8D * force) * (1.0D + 0.35D * charge);
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
        sound(player, ModSounds.THOR_THROW.get(), 1.0F, 0.9F + 0.2F * force);
    }

    public static void catchHammer(ServerPlayer player, ItemStack stack) {
        ItemStack s = stack.isEmpty() ? new ItemStack(ModItems.MJOLNIR.get()) : stack.copy();
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

    /** The thrown hammer hits a creature on its way. */
    public static void hammerStrike(ServerPlayer player, LivingEntity target, float force, Vec3 motion) {
        ServerLevel level = player.serverLevel();
        float power = ThorPowers.power(player);
        float charge = ThorPowers.charge(player) / 100.0F;
        float damage = (12.0F + 18.0F * force) * power * (1.0F + 0.3F * charge);
        target.invulnerableTime = 0;
        target.hurt(level.damageSources().playerAttack(player), damage);
        Vec3 push = motion.lengthSqr() < 1.0E-4D ? Vec3.ZERO : motion.normalize().scale(1.0D + force);
        target.setDeltaMovement(push.x, 0.35D, push.z);
        target.hurtMarked = true;
        if (charge > 0.2F) {
            bolt(level, target.position().add(0.0D, target.getBbHeight() + 1.5D, 0.0D),
                    target.position().add(0.0D, target.getBbHeight() * 0.5D, 0.0D), 0.8F);
        }
        level.playSound(null, target.getX(), target.getY(), target.getZ(), ModSounds.THOR_HIT.get(),
                SoundSource.PLAYERS, 1.0F, 1.0F);
        ThorPowers.addXp(player, 0.5F);
    }

    /** The thrown hammer stops on a wall / the ground: a small shockwave. */
    public static void hammerImpact(ServerPlayer player, Vec3 at, float force) {
        ServerLevel level = player.serverLevel();
        float charge = ThorPowers.charge(player) / 100.0F;
        double radius = 2.0D + 3.0D * force * (0.5D + charge);
        float power = ThorPowers.power(player);
        for (LivingEntity e : around(level, player, at, radius)) {
            double f = 1.0D - e.position().distanceTo(at) / (radius + 1.0D);
            e.invulnerableTime = 0;
            e.hurt(level.damageSources().playerAttack(player), (float) (6.0F * power * force * f));
            push(e, at, 0.6D + f);
        }
        level.playSound(null, at.x, at.y, at.z, ModSounds.THOR_THUMP.get(), SoundSource.PLAYERS, 1.2F, 1.0F);
        SupermanAbilities.groundPuff(level, at, radius * 0.5D, 14);
        SupermanAbilities.fxAt(level, SupermanFxPacket.SHOCKWAVE, at, new Vec3(0, 1, 0), (float) radius);
    }

    /** A melee hit with the hammer in hand: the charge shocks the target. */
    public static void hammerHit(ServerPlayer player, LivingEntity target) {
        float charge = ThorPowers.charge(player);
        boolean fury = ThorPowers.flag(player, ThorPowers.F_FURY);
        if (charge >= 5.0F || fury) {
            ServerLevel level = player.serverLevel();
            float power = ThorPowers.power(player);
            target.invulnerableTime = 0;
            target.hurt(level.damageSources().playerAttack(player), 3.0F * power);
            bolt(level, player.getEyePosition().add(player.getLookAngle().scale(0.6D)),
                    target.position().add(0.0D, target.getBbHeight() * 0.5D, 0.0D), 0.6F);
            if (!fury) {
                ThorPowers.setCharge(player, charge - 5.0F);
            }
        }
        sound(player, ModSounds.THOR_HIT.get(), 0.8F, 1.0F);
    }

    /** Crouch + right click with the hammer: it drinks lightning from the sky (the charge bar). */
    public static void chargeTick(ServerPlayer player, int held) {
        ServerLevel level = player.serverLevel();
        float charge = ThorPowers.charge(player);
        ThorPowers.setCharge(player, charge + 0.8F);
        if (held % 20 == 0) {
            boolean sky = level.canSeeSky(player.blockPosition().above());
            Vec3 top = player.position().add(0.0D, player.getBbHeight() + 0.6D, 0.0D);
            if (sky) {
                bolt(level, top.add(0.0D, 24.0D, 0.0D), top, 1.4F);
                level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.LIGHTNING_BOLT_THUNDER,
                        SoundSource.WEATHER, 0.6F, 1.3F);
            }
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, top.x, top.y, top.z, 20, 0.3D, 0.3D, 0.3D, 0.2D);
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
        if (cooling(player, ability)) {
            return;
        }
        if (!ThorPowers.spend(player, ability.energy())) {
            player.displayClientMessage(Component.translatable("message.flash.thor_no_energy"), true);
            return;
        }
        boolean used = switch (ability) {
            case SUMMON -> summon(player);
            case LIGHTNING_STRIKE -> lightningStrike(player);
            case STORM_STRIKER -> stormStriker(player);
            case HAMMER_SPARK -> hammerSpark(player);
            case LIGHTNING_VORTEX -> vortex(player);
            case WARRIORS_FURY -> fury(player);
            case MIGHTY_TORNADO -> tornado(player);
            case OVERCHARGE -> overcharge(player);
            case GOD_OF_THUNDER -> godOfThunder(player);
            default -> false;
        };
        if (used) {
            ThorPowers.setCooldown(player, ability);
        } else {
            ThorPowers.setEnergy(player, ThorPowers.energy(player) + ability.energy());
        }
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
        int flag = ability == ThorAbility.LIGHTNING_BLAST ? ThorPowers.F_BLAST : ThorPowers.F_SPIN;
        if (pressed && ability.needsHammer() && !holdingHammer(player)) {
            player.displayClientMessage(Component.translatable("message.flash.thor_need_hammer"), true);
            return;
        }
        ThorPowers.setFlag(player, flag, pressed);
        if (pressed) {
            sound(player, ability == ThorAbility.LIGHTNING_BLAST ? ModSounds.THOR_LIGHTNING.get()
                    : ModSounds.THOR_SPIN.get(), 1.0F, 1.0F);
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
        ThorPowers.setFlag(player, ThorPowers.F_FLIGHT, true);
        player.getAbilities().mayfly = true;
        player.onUpdateAbilities();
        player.setDeltaMovement(player.getDeltaMovement().x, 2.0D, player.getDeltaMovement().z);
        player.hurtMarked = true;
        ServerLevel level = player.serverLevel();
        SupermanAbilities.cracks(level, player.position(), 3.5F);
        SupermanAbilities.anim(player, "charged_launch9");
        SupermanAbilities.groundPuff(level, player.position(), 2.5D, 30);
        sound(player, ModSounds.THOR_THROW.get(), 1.4F, 0.8F);
        strikeVisual(level, player.position());
        ThorPowers.sync(player);
    }

    public static void stopAll(ServerPlayer player) {
        Session s = SESSIONS.remove(player.getUUID());
        if (s != null && s.sparkTarget >= 0 && player.serverLevel().getEntity(s.sparkTarget) instanceof LivingEntity t) {
            t.setNoGravity(false);
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
        ServerLevel level = player.serverLevel();
        int flags = ThorPowers.flags(player);
        if ((flags & ThorPowers.F_BLAST) != 0) {
            if (ThorPowers.spend(player, ThorAbility.LIGHTNING_BLAST.energy())) {
                blastTick(player, level);
            } else {
                ThorPowers.setFlag(player, ThorPowers.F_BLAST, false);
                player.displayClientMessage(Component.translatable("message.flash.thor_no_energy"), true);
            }
        }
        if ((flags & ThorPowers.F_SPIN) != 0) {
            if (!holdingHammer(player) || !ThorPowers.spend(player, ThorAbility.HAMMER_SPIN.energy())) {
                ThorPowers.setFlag(player, ThorPowers.F_SPIN, false);
            } else {
                spinTick(player, level);
            }
        }
        if ((flags & ThorPowers.F_FURY) != 0 && player.tickCount % 4 == 0) {
            Vec3 c = player.position().add(0.0D, 1.0D, 0.0D);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y, c.z, 6, 0.4D, 0.8D, 0.4D, 0.1D);
        }
        if ((flags & ThorPowers.F_OVERCHARGE) != 0 && player.tickCount % 20 == 0) {
            overchargeArcs(player, level);
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
    }

    // ------------------------------------------------------------------
    // Summon Mjolnir
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
            sound(player, ModSounds.THOR_THROW.get(), 1.0F, 0.8F);
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
            target.invulnerableTime = 0;
            // 15 per pulse in the original, 20 in the Warrior's Fury
            target.hurt(level.damageSources().playerAttack(player), 15.0F * power * bonus);
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
        if (player.tickCount % 20 == 0) {
            sound(player, ModSounds.THOR_LIGHTNING.get(), 0.6F, 1.2F);
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
                living.invulnerableTime = 0;
                living.hurt(level.damageSources().playerAttack(player), 3.0F * ThorPowers.power(player));
                push(living, player.position(), 0.9D);
            }
        }
        if (player.tickCount % 2 == 0) {
            double a = player.tickCount * 0.9D;
            Vec3 right = new Vec3(-player.getLookAngle().z, 0, player.getLookAngle().x).normalize();
            Vec3 p = c.add(right.scale(Math.cos(a) * 0.8D)).add(0.0D, Math.sin(a) * 0.8D, 0.0D);
            level.sendParticles(ParticleTypes.CLOUD, p.x, p.y, p.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, 2, 0.05D, 0.05D, 0.05D, 0.05D);
        }
        if (player.tickCount % 20 == 0) {
            sound(player, ModSounds.THOR_SPIN.get(), 0.8F, 1.0F);
        }
    }

    // ------------------------------------------------------------------
    // Lightning Strike: a bolt from the sky on Thor himself, a shockwave all around
    // ------------------------------------------------------------------

    private static boolean lightningStrike(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        if (!level.canSeeSky(BlockPos.containing(player.getEyePosition()))) {
            player.displayClientMessage(Component.translatable("message.flash.thor_need_sky"), true);
            return false;
        }
        SupermanAbilities.anim(player, "thor_lightningclash");
        later(player, 8, p -> {
            ServerLevel l = p.serverLevel();
            strikeVisual(l, p.position());
            double radius = 8.0D;
            float power = ThorPowers.power(p);
            for (LivingEntity e : around(l, p, p.position(), radius)) {
                double f = 1.0D - e.position().distanceTo(p.position()) / (radius + 1.0D);
                e.invulnerableTime = 0;
                e.hurt(l.damageSources().playerAttack(p), (float) (12.0F * power * (0.4D + 0.6D * f)));
                push(e, p.position(), 0.8D + 1.6D * f);
                if (p.getRandom().nextFloat() < 0.05F) {
                    stun(e, 40);
                }
            }
            SupermanAbilities.fxAt(l, SupermanFxPacket.SHOCKWAVE, p.position().add(0.0D, 0.1D, 0.0D), new Vec3(0, 1, 0),
                    (float) radius);
            SupermanAbilities.cracks(l, p.position(), 4.0F);
            ThorPowers.setCharge(p, ThorPowers.charge(p) + 25.0F);
            ThorPowers.addXp(p, 1.0F);
        });
        return true;
    }

    // ------------------------------------------------------------------
    // Storm Striker: the hammer slammed on the ground (20), then a lightning wave (8)
    // ------------------------------------------------------------------

    private static boolean stormStriker(ServerPlayer player) {
        SupermanAbilities.anim(player, "thor_hammerslam");
        later(player, 15, p -> {
            ServerLevel l = p.serverLevel();
            Vec3 at = p.position().add(p.getLookAngle().multiply(1.0D, 0.0D, 1.0D).normalize().scale(1.2D));
            double radius = 10.0D;
            float power = ThorPowers.power(p);
            for (LivingEntity e : around(l, p, at, radius)) {
                double f = 1.0D - e.position().distanceTo(at) / (radius + 1.0D);
                e.invulnerableTime = 0;
                e.hurt(l.damageSources().playerAttack(p), (float) (20.0F * power * (0.35D + 0.65D * f)));
                push(e, at, 0.6D + 1.2D * f);
            }
            l.playSound(null, at.x, at.y, at.z, ModSounds.THOR_STORM_STRIKER.get(), SoundSource.PLAYERS, 1.6F, 1.0F);
            SupermanAbilities.fxAt(l, SupermanFxPacket.SHOCKWAVE, at.add(0.0D, 0.1D, 0.0D), new Vec3(0, 1, 0), 10.0F);
            SupermanAbilities.cracks(l, at, 6.0F);
            SupermanAbilities.groundPuff(l, at, 3.0D, 30);
            if (ThorPowers.destroyBlocks(p)) {
                l.explode(p, at.x, at.y, at.z, 2.0F, Level.ExplosionInteraction.TNT);
            }
            ThorPowers.addXp(p, 1.5F);
        });
        later(player, 25, p -> {
            ServerLevel l = p.serverLevel();
            double radius = 14.0D;
            float power = ThorPowers.power(p);
            for (LivingEntity e : around(l, p, p.position(), radius)) {
                e.invulnerableTime = 0;
                e.hurt(l.damageSources().playerAttack(p), 8.0F * power);
                bolt(l, e.position().add(0.0D, e.getBbHeight() + 4.0D, 0.0D),
                        e.position().add(0.0D, e.getBbHeight() * 0.5D, 0.0D), 1.0F);
            }
            for (int i = 0; i < 6; i++) {
                double a = i * Math.PI / 3.0D;
                Vec3 g = p.position().add(Math.cos(a) * 6.0D, 0.2D, Math.sin(a) * 6.0D);
                bolt(l, p.position().add(0.0D, 1.0D, 0.0D), g, 0.9F);
            }
            l.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS,
                    1.2F, 1.2F);
        });
        return true;
    }

    // ------------------------------------------------------------------
    // Hammer Spark: dash, grab and throw the enemy up, the hammer carries it higher and nails it to the ground
    // ------------------------------------------------------------------

    private static boolean hammerSpark(ServerPlayer player) {
        Session s = session(player);
        if (s.sparkTicks >= 0) {
            return false;
        }
        HitResult hit = trace(player, 16.0D);
        if (!(hit instanceof EntityHitResult eh) || !(eh.getEntity() instanceof LivingEntity)) {
            player.displayClientMessage(Component.translatable("message.flash.thor_no_target"), true);
            return false;
        }
        s.sparkTarget = eh.getEntity().getId();
        s.sparkTicks = 0;
        SupermanAbilities.anim(player, "thor_hammer_spark");
        ThorPowers.setFlag(player, ThorPowers.F_COMBO, true);
        return true;
    }

    private static void sparkTick(ServerPlayer player, ServerLevel level, Session s) {
        int t = s.sparkTicks++;
        Entity e = level.getEntity(s.sparkTarget);
        if (!(e instanceof LivingEntity target) || !target.isAlive() || t > 70) {
            if (e instanceof LivingEntity dead) {
                dead.setNoGravity(false);
            }
            s.sparkTicks = -1;
            s.sparkTarget = -1;
            ThorPowers.setFlag(player, ThorPowers.F_COMBO, false);
            return;
        }
        float power = ThorPowers.power(player);
        if (t <= 5) {
            // the dash
            Vec3 tp = target.position();
            Vec3 flat = new Vec3(tp.x - player.getX(), 0.0D, tp.z - player.getZ());
            flat = flat.lengthSqr() < 1.0E-4D ? player.getLookAngle() : flat.normalize();
            Vec3 at = tp.subtract(flat.scale(1.2D + target.getBbWidth() * 0.5D));
            Vec3 step = player.position().add(at.subtract(player.position()).scale(t >= 4 ? 1.0D : 0.45D));
            face(player, step, tp.add(0.0D, target.getBbHeight() * 0.6D, 0.0D));
        } else if (t == 6) {
            // grabbed and thrown up with the free hand
            target.invulnerableTime = 0;
            target.hurt(level.damageSources().playerAttack(player), 10.0F * power);
            target.setDeltaMovement(0.0D, 1.3D, 0.0D);
            target.hurtMarked = true;
            sound(player, ModSounds.THOR_SPIN_ATTACK.get(), 1.0F, 1.0F);
        } else if (t == 14) {
            // the hammer thrown after it: higher, then down
            float charge = ThorPowers.charge(player) / 100.0F;
            target.invulnerableTime = 0;
            target.hurt(level.damageSources().playerAttack(player), (14.0F + 7.0F * charge) * power);
            bolt(level, player.getEyePosition(), target.position().add(0.0D, target.getBbHeight() * 0.5D, 0.0D), 1.0F);
            target.setDeltaMovement(0.0D, 0.9D, 0.0D);
            target.hurtMarked = true;
            sound(player, ModSounds.THOR_THROW.get(), 1.0F, 1.0F);
        } else if (t == 22) {
            target.setDeltaMovement(0.0D, -3.0D, 0.0D);
            target.hurtMarked = true;
        } else if (t > 22 && (target.onGround() || t >= 60)) {
            Vec3 at = target.position();
            target.invulnerableTime = 0;
            target.hurt(level.damageSources().playerAttack(player), 12.0F * power);
            stun(target, 30);
            strikeVisual(level, at);
            SupermanAbilities.fxAt(level, SupermanFxPacket.SHOCKWAVE, at.add(0.0D, 0.1D, 0.0D), new Vec3(0, 1, 0), 5.0F);
            SupermanAbilities.cracks(level, at, 3.5F);
            for (LivingEntity o : around(level, player, at, 4.0D)) {
                if (o != target) {
                    o.invulnerableTime = 0;
                    o.hurt(level.damageSources().playerAttack(player), 6.0F * power);
                    push(o, at, 1.0D);
                }
            }
            ThorPowers.addXp(player, 2.0F);
            s.sparkTicks = -1;
            s.sparkTarget = -1;
            ThorPowers.setFlag(player, ThorPowers.F_COMBO, false);
        }
    }

    // ------------------------------------------------------------------
    // Lightning Vortex: spinning forward, a vortex of lightning shocking everyone in the way
    // ------------------------------------------------------------------

    private static boolean vortex(ServerPlayer player) {
        Session s = session(player);
        s.vortexTicks = 0;
        s.vortexDir = player.getLookAngle().normalize();
        sound(player, ModSounds.THOR_SPIN_ATTACK.get(), 1.2F, 0.9F);
        return true;
    }

    private static void vortexTick(ServerPlayer player, ServerLevel level, Session s) {
        int t = s.vortexTicks++;
        if (t > 14) {
            s.vortexTicks = -1;
            return;
        }
        Vec3 v = s.vortexDir.scale(1.1D);
        player.setDeltaMovement(v.x, Math.max(v.y, 0.05D), v.z);
        player.hurtMarked = true;
        player.fallDistance = 0.0F;
        Vec3 c = player.position().add(0.0D, 1.0D, 0.0D);
        float power = ThorPowers.power(player);
        for (LivingEntity e : around(level, player, c, 3.2D)) {
            if (e.invulnerableTime > 10) {
                continue;
            }
            e.invulnerableTime = 0;
            e.hurt(level.damageSources().playerAttack(player), 7.0F * power);
            push(e, c, 1.1D);
            bolt(level, c, e.position().add(0.0D, e.getBbHeight() * 0.5D, 0.0D), 0.7F);
        }
        double a = t * 1.3D;
        for (int i = 0; i < 3; i++) {
            double b = a + i * 2.1D;
            Vec3 p = c.add(Math.cos(b) * 1.4D, Math.sin(b * 0.5D) * 0.6D, Math.sin(b) * 1.4D);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, 3, 0.05D, 0.05D, 0.05D, 0.05D);
            level.sendParticles(ParticleTypes.CLOUD, p.x, p.y, p.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
    }

    // ------------------------------------------------------------------
    // Warrior's Fury: 20 s of rage (lightning all over the body, -15 % damage taken, +25 % damage, shock punches)
    // ------------------------------------------------------------------

    private static boolean fury(ServerPlayer player) {
        ThorPowers.setTimer(player, ThorPowers.F_FURY, 400);
        strikeVisual(player.serverLevel(), player.position());
        line(player);
        player.displayClientMessage(Component.translatable("message.flash.thor_fury"), true);
        return true;
    }

    /** Fury: a punch has 75 % chance to shock. */
    public static void furyPunch(ServerPlayer player, LivingEntity target) {
        if (player.getRandom().nextFloat() < 0.75F) {
            ServerLevel level = player.serverLevel();
            target.invulnerableTime = 0;
            target.hurt(level.damageSources().playerAttack(player), 4.0F * ThorPowers.power(player));
            bolt(level, target.position().add(0.0D, target.getBbHeight() + 2.0D, 0.0D),
                    target.position().add(0.0D, target.getBbHeight() * 0.5D, 0.0D), 0.7F);
        }
    }

    // ------------------------------------------------------------------
    // Mighty Tornado: a giant tornado of wind and lightning that pulls creatures and projectiles
    // ------------------------------------------------------------------

    private static boolean tornado(ServerPlayer player) {
        HitResult hit = trace(player, 24.0D);
        Vec3 at = hit.getType() == HitResult.Type.MISS ? player.position().add(player.getLookAngle().scale(12.0D))
                : hit.getLocation();
        // on the ground below the point
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
                living.invulnerableTime = 0;
                living.hurt(level.damageSources().playerAttack(player), 4.0F * power);
                if (player.getRandom().nextInt(3) == 0) {
                    bolt(level, c.add(0.0D, 12.0D, 0.0D), living.position().add(0.0D, living.getBbHeight() * 0.5D, 0.0D),
                            0.9F);
                }
            }
        }
        // the funnel
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
    // Overcharge: +50 % charge (up to 150 %), absorbs energy damage and arcs to the creatures around
    // ------------------------------------------------------------------

    private static boolean overcharge(ServerPlayer player) {
        ThorPowers.setTimer(player, ThorPowers.F_OVERCHARGE, 300);
        ThorPowers.setCharge(player, ThorPowers.charge(player) + 50.0F);
        sound(player, ModSounds.THOR_OVERCHARGE_START.get(), 1.0F, 1.0F);
        sound(player, ModSounds.THOR_OVERCHARGE.get(), 1.0F, 1.0F);
        strikeVisual(player.serverLevel(), player.position());
        return true;
    }

    private static void overchargeArcs(ServerPlayer player, ServerLevel level) {
        float power = ThorPowers.power(player);
        Vec3 c = player.position().add(0.0D, 1.2D, 0.0D);
        int n = 0;
        for (LivingEntity e : around(level, player, c, 7.0D)) {
            if (n++ >= 4) {
                break;
            }
            e.invulnerableTime = 0;
            e.hurt(level.damageSources().playerAttack(player), 3.0F * power);
            bolt(level, c, e.position().add(0.0D, e.getBbHeight() * 0.5D, 0.0D), 0.6F);
        }
        if (player.tickCount % 60 == 0) {
            sound(player, ModSounds.THOR_OVERCHARGE_LOOP.get(), 0.6F, 1.0F);
        }
    }

    // ------------------------------------------------------------------
    // God of Thunder (ultimate): rises gathering lightning, pulls and slows the enemies, then dives on the aim
    // ------------------------------------------------------------------

    private static boolean godOfThunder(ServerPlayer player) {
        Session s = session(player);
        if (s.ultTicks >= 0) {
            return false;
        }
        s.ultTicks = 0;
        s.ultStart = player.position();
        SupermanAbilities.anim(player, "thor_god_thunder");
        ThorPowers.setFlag(player, ThorPowers.F_ULTIMATE, true);
        line(player);
        return true;
    }

    private static void ultTick(ServerPlayer player, ServerLevel level, Session s) {
        int t = s.ultTicks++;
        float power = ThorPowers.power(player);
        if (t < 40) {
            // up into the storm
            Vec3 at = s.ultStart.add(0.0D, 7.0D * Math.sin(Math.min(1.0D, t / 30.0D) * Math.PI / 2.0D), 0.0D);
            player.setNoGravity(true);
            player.connection.teleport(at.x, at.y, at.z, player.getYRot(), player.getXRot());
            player.setDeltaMovement(Vec3.ZERO);
            player.fallDistance = 0.0F;
            for (LivingEntity e : around(level, player, s.ultStart, 16.0D)) {
                Vec3 in = s.ultStart.subtract(e.position()).multiply(1.0D, 0.0D, 1.0D);
                if (in.lengthSqr() > 4.0D) {
                    e.setDeltaMovement(e.getDeltaMovement().add(in.normalize().scale(0.12D)));
                    e.hurtMarked = true;
                }
                e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 3, false, false));
            }
            if (t % 5 == 0) {
                Vec3 top = player.position().add(0.0D, 2.2D, 0.0D);
                bolt(level, top.add(level.random.nextGaussian() * 3.0D, 14.0D, level.random.nextGaussian() * 3.0D), top,
                        1.3F);
                level.playSound(null, top.x, top.y, top.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER,
                        0.8F, 1.2F);
            }
            if (t == 39) {
                HitResult hit = trace(player, 48.0D);
                Vec3 aim = hit.getType() == HitResult.Type.MISS ? player.position().add(player.getLookAngle().scale(20.0D))
                        : hit.getLocation();
                BlockHitResult down = level.clip(new ClipContext(aim.add(0.0D, 1.0D, 0.0D), aim.subtract(0.0D, 30.0D, 0.0D),
                        ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
                s.ultTarget = down.getType() != HitResult.Type.MISS ? down.getLocation() : aim;
                s.ultStart = player.position();
            }
            return;
        }
        if (t < 50) {
            double k = (t - 39) / 10.0D;
            Vec3 at = s.ultStart.add(s.ultTarget.subtract(s.ultStart).scale(k * k));
            player.connection.teleport(at.x, at.y, at.z, player.getYRot(), player.getXRot());
            player.setDeltaMovement(Vec3.ZERO);
            player.fallDistance = 0.0F;
            return;
        }
        // the hammer comes down
        Vec3 at = s.ultTarget;
        player.setNoGravity(false);
        double radius = 10.0D;
        for (LivingEntity e : around(level, player, at, radius)) {
            double f = 1.0D - e.position().distanceTo(at) / (radius + 1.0D);
            e.invulnerableTime = 0;
            e.hurt(level.damageSources().playerAttack(player), (float) (40.0F * power * (0.35D + 0.65D * f)));
            stun(e, 50);
            push(e, at, 1.0D + 2.0D * f);
        }
        for (int i = 0; i < 5; i++) {
            double a = i * Math.PI * 2.0D / 5.0D;
            strikeVisual(level, at.add(Math.cos(a) * 4.0D, 0.0D, Math.sin(a) * 4.0D));
        }
        strikeVisual(level, at);
        SupermanAbilities.fxAt(level, SupermanFxPacket.SHOCKWAVE, at.add(0.0D, 0.1D, 0.0D), new Vec3(0, 1, 0), 12.0F);
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
    // Helpers
    // ------------------------------------------------------------------

    /** A vanilla lightning bolt that only shows and sounds (the damage is done by the ability). */
    public static void strikeVisual(ServerLevel level, Vec3 at) {
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt != null) {
            bolt.moveTo(at.x, at.y, at.z);
            bolt.setVisualOnly(true);
            level.addFreshEntity(bolt);
        }
    }

    /** A drawn lightning bolt between two points (seen by everyone around). */
    public static void bolt(ServerLevel level, Vec3 a, Vec3 b, float scale) {
        FlashNetwork.CHANNEL.send(PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(a.x, a.y, a.z,
                96.0D, level.dimension())), new ThorFxPacket(ThorFxPacket.BOLT, a.x, a.y, a.z, b.x, b.y, b.z, scale));
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

    private static void face(ServerPlayer player, Vec3 at, Vec3 look) {
        Vec3 d = look.subtract(at.add(0.0D, player.getEyeHeight(), 0.0D));
        float yaw = (float) (Math.toDegrees(Math.atan2(d.z, d.x)) - 90.0D);
        float pitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
        player.connection.teleport(at.x, at.y, at.z, yaw, pitch);
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0.0F;
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

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        // nothing global yet (the sessions run in the player tick)
    }
}
