package com.flash.hulk;

import com.flash.FlashMod;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Server side of the Hulk abilities. */
@Mod.EventBusSubscriber(modid = FlashMod.MODID)
public final class HulkAbilities {

    /** Abilities that last a few ticks (charge, world breaker). */
    private static final Map<UUID, Running> RUNNING = new ConcurrentHashMap<>();

    private record Running(HulkAbility ability, Vec3 dir, Vec3 origin, int[] ticks) {
    }

    private HulkAbilities() {
    }

    public static void use(ServerPlayer player, HulkAbility ability) {
        if (!HulkPowers.isTransformed(player)) {
            player.displayClientMessage(Component.translatable("message.flash.hulk_not_transformed"), true);
            return;
        }
        if (HulkPowers.level(player) < ability.level()) {
            player.displayClientMessage(Component.translatable("message.flash.hulk_ability_locked",
                    ability.displayName(), ability.level()), true);
            return;
        }
        int cd = HulkPowers.cooldown(player, ability);
        if (cd > 0) {
            player.displayClientMessage(Component.translatable("message.flash.ability_cooldown",
                    ability.displayName(), (cd + 19) / 20), true);
            return;
        }
        if (RUNNING.containsKey(player.getUUID())) {
            return;
        }
        ServerLevel level = (ServerLevel) player.level();
        switch (ability) {
            case GROUND_SMASH -> groundSmash(player, level);
            case CHARGE -> RUNNING.put(player.getUUID(), new Running(ability, flatLook(player), player.position(), new int[] {0}));
            case ROAR -> roar(player, level);
            case THUNDERCLAP -> thunderclap(player, level);
            case BOULDER -> boulder(player, level);
            case FURY -> fury(player, level);
            case GAMMA_WAVE -> gammaWave(player, level);
            case WORLD_BREAKER -> {
                RUNNING.put(player.getUUID(), new Running(ability, flatLook(player), player.position(), new int[] {0}));
                level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.RAVAGER_ROAR,
                        SoundSource.PLAYERS, 2.0F, 0.5F);
            }
            default -> {
            }
        }
        HulkPowers.setCooldown(player, ability);
        HulkPowers.syncSelf(player);
    }

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) {
            return;
        }
        Running run = RUNNING.get(player.getUUID());
        if (run == null) {
            return;
        }
        if (!HulkPowers.isTransformed(player) || !player.isAlive()) {
            RUNNING.remove(player.getUUID());
            return;
        }
        ServerLevel level = (ServerLevel) player.level();
        int t = ++run.ticks()[0];
        if (run.ability() == HulkAbility.CHARGE) {
            chargeTick(player, level, run.dir(), t);
            if (t >= 10) {
                RUNNING.remove(player.getUUID());
            }
        } else if (run.ability() == HulkAbility.WORLD_BREAKER) {
            worldBreakerTick(player, level, run.origin(), t);
            if (t >= 40) {
                RUNNING.remove(player.getUUID());
            }
        } else {
            RUNNING.remove(player.getUUID());
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static Vec3 flatLook(ServerPlayer player) {
        Vec3 look = player.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0.0D, look.z);
        return flat.lengthSqr() < 1.0E-4D ? new Vec3(0.0D, 0.0D, 1.0D) : flat.normalize();
    }

    private static float power(ServerPlayer player) {
        return HulkPowers.activeLevel(player) * (HulkPowers.isFurious(player) ? 1.5F : 1.0F);
    }

    private static List<LivingEntity> around(ServerPlayer player, AABB box) {
        return player.level().getEntitiesOfClass(LivingEntity.class, box, e -> e != player && e.isAlive()
                && !(e instanceof net.minecraft.world.entity.player.Player p && (p.isSpectator() || p.isCreative())));
    }

    private static void hit(ServerPlayer player, LivingEntity target, float damage, Vec3 push) {
        target.invulnerableTime = 0;
        target.hurt(player.damageSources().playerAttack(player), damage);
        target.setDeltaMovement(push);
        target.hurtMarked = true;
        HulkPowers.addRage(player, 1.5F);
    }

    /** Top solid block of the column at (x, z) around the player's feet. */
    private static BlockPos ground(ServerLevel level, double x, double y, double z) {
        BlockPos pos = BlockPos.containing(x, y - 0.5D, z);
        for (int i = 0; i < 4; i++) {
            if (!level.getBlockState(pos).isAir() && level.getBlockState(pos.above()).isAir()) {
                return pos;
            }
            pos = level.getBlockState(pos).isAir() ? pos.below() : pos.above();
        }
        return BlockPos.containing(x, y - 0.5D, z);
    }

    private static boolean movable(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return !state.isAir() && state.getFluidState().isEmpty() && !state.hasBlockEntity()
                && state.getDestroySpeed(level, pos) >= 0.0F && state.getDestroySpeed(level, pos) < 30.0F
                && !state.is(Blocks.BEDROCK);
    }

    /** Throws a ground block up into the air (it lands again as a falling block). */
    private static void lift(ServerLevel level, BlockPos pos, double up, Vec3 push) {
        if (!movable(level, pos) || !level.getBlockState(pos.above()).isAir()) {
            return;
        }
        FallingBlockEntity block = FallingBlockEntity.fall(level, pos, level.getBlockState(pos));
        block.setDeltaMovement(push.x, up, push.z);
        block.hurtMarked = true;
        block.dropItem = false;
    }

    private static void dust(ServerLevel level, BlockPos pos, int count) {
        BlockState state = level.getBlockState(pos);
        if (!state.isAir()) {
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state),
                    pos.getX() + 0.5D, pos.getY() + 1.05D, pos.getZ() + 0.5D, count, 0.4D, 0.1D, 0.4D, 0.2D);
        }
    }

    // ------------------------------------------------------------------
    // Abilities
    // ------------------------------------------------------------------

    /** Level 2: smashes the ground; everything in a line ahead is hit and, with destruction on, lifted. */
    private static void groundSmash(ServerPlayer player, ServerLevel level) {
        Vec3 dir = flatLook(player);
        Vec3 side = new Vec3(-dir.z, 0.0D, dir.x);
        float damage = 8.0F + 2.0F * power(player);
        boolean destroy = HulkPowers.destroyBlocks(player);
        player.swing(InteractionHand.MAIN_HAND, true);
        player.swing(InteractionHand.OFF_HAND, true);
        int lifted = 0;
        for (int d = 1; d <= 10; d++) {
            Vec3 c = player.position().add(dir.scale(d));
            for (LivingEntity e : around(player, new AABB(c, c).inflate(1.6D, 2.0D, 1.6D))) {
                hit(player, e, damage * (1.0F - d * 0.04F), dir.scale(0.4D).add(0.0D, 0.9D, 0.0D));
            }
            for (int s = -1; s <= 1; s++) {
                Vec3 p = c.add(side.scale(s * (0.6D + d * 0.12D)));
                BlockPos g = ground(level, p.x, player.getY(), p.z);
                dust(level, g, 6);
                if (destroy && lifted < 30 && level.random.nextFloat() < 0.75F) {
                    lift(level, g, 0.35D + level.random.nextDouble() * 0.45D, dir.scale(0.05D));
                    lifted++;
                }
            }
        }
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GENERIC_EXPLODE,
                SoundSource.PLAYERS, 1.4F, 0.6F);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ANVIL_LAND,
                SoundSource.PLAYERS, 1.0F, 0.5F);
    }

    /** Level 3: runs forward, flattening whatever is in the way. */
    private static void chargeTick(ServerPlayer player, ServerLevel level, Vec3 dir, int t) {
        player.setDeltaMovement(dir.x * 1.5D, Math.min(0.0D, player.getDeltaMovement().y), dir.z * 1.5D);
        player.hurtMarked = true;
        float damage = 6.0F + 1.5F * power(player);
        for (LivingEntity e : around(player, player.getBoundingBox().inflate(1.0D))) {
            hit(player, e, damage, dir.scale(1.8D).add(0.0D, 0.5D, 0.0D));
        }
        if (HulkPowers.destroyBlocks(player)) {
            AABB front = player.getBoundingBox().move(dir.scale(1.0D)).deflate(0.05D, 0.0D, 0.05D);
            BlockPos.betweenClosedStream(new AABB(front.minX, front.minY + 0.6D, front.minZ,
                            front.maxX, front.maxY, front.maxZ))
                    .limit(40)
                    .forEach(p -> {
                        BlockState state = level.getBlockState(p);
                        if (!state.isAir() && !state.hasBlockEntity() && state.getDestroySpeed(level, p) >= 0.0F
                                && state.getDestroySpeed(level, p) <= 3.0F) {
                            level.destroyBlock(p, true, player);
                        }
                    });
        }
        level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 0.3D, player.getZ(),
                4, 0.4D, 0.1D, 0.4D, 0.02D);
        if (t == 1) {
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.RAVAGER_STEP,
                    SoundSource.PLAYERS, 1.6F, 0.6F);
        }
    }

    /** Level 4: a roar that weakens, slows and pushes everyone around. */
    private static void roar(ServerPlayer player, ServerLevel level) {
        for (LivingEntity e : around(player, player.getBoundingBox().inflate(12.0D))) {
            Vec3 away = e.position().subtract(player.position());
            Vec3 push = new Vec3(away.x, 0.0D, away.z).normalize().scale(1.3D).add(0.0D, 0.3D, 0.0D);
            e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 160, 1));
            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 160, 1));
            e.setDeltaMovement(push);
            e.hurtMarked = true;
        }
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.RAVAGER_ROAR,
                SoundSource.PLAYERS, 3.0F, 0.45F);
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        for (int i = 1; i <= 4; i++) {
            Vec3 p = eye.add(look.scale(i * 1.5D));
            level.sendParticles(ParticleTypes.SONIC_BOOM, p.x, p.y, p.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
        HulkPowers.addRage(player, 8.0F);
    }

    /** Level 5: thunderclap - a clap so strong that everything ahead is blown away and broken. */
    private static void thunderclap(ServerPlayer player, ServerLevel level) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle().normalize();
        float damage = 20.0F + 3.0F * power(player);
        double range = 20.0D;
        player.swing(InteractionHand.MAIN_HAND, true);
        player.swing(InteractionHand.OFF_HAND, true);
        for (LivingEntity e : around(player, player.getBoundingBox().inflate(range))) {
            Vec3 d = e.getBoundingBox().getCenter().subtract(eye);
            double dist = d.length();
            if (dist > range || dist < 0.01D || d.normalize().dot(look) < 0.82D) {
                continue;
            }
            hit(player, e, (float) (damage * (1.0D - dist / (range * 1.6D))), look.scale(3.0D).add(0.0D, 0.6D, 0.0D));
        }
        if (HulkPowers.destroyBlocks(player)) {
            int broken = 0;
            for (int d = 2; d <= 16 && broken < 90; d++) {
                double radius = 0.8D + d * 0.18D;
                Vec3 c = eye.add(look.scale(d));
                for (int i = 0; i < 10 && broken < 90; i++) {
                    double a = level.random.nextDouble() * Math.PI * 2.0D;
                    double r = level.random.nextDouble() * radius;
                    Vec3 side = look.cross(new Vec3(0.0D, 1.0D, 0.0D));
                    if (side.lengthSqr() < 1.0E-4D) {
                        side = new Vec3(1.0D, 0.0D, 0.0D);
                    }
                    side = side.normalize();
                    Vec3 up = side.cross(look).normalize();
                    Vec3 p = c.add(side.scale(Math.cos(a) * r)).add(up.scale(Math.sin(a) * r));
                    BlockPos pos = BlockPos.containing(p);
                    BlockState state = level.getBlockState(pos);
                    if (!state.isAir() && state.getFluidState().isEmpty() && !state.hasBlockEntity()
                            && state.getDestroySpeed(level, pos) >= 0.0F && state.getDestroySpeed(level, pos) < 30.0F) {
                        level.destroyBlock(pos, level.random.nextInt(3) == 0, player);
                        broken++;
                    }
                }
            }
        }
        // the shockwave: rings of air flying forward
        for (int d = 1; d <= 18; d += 2) {
            Vec3 c = eye.add(look.scale(d));
            level.sendParticles(ParticleTypes.CLOUD, c.x, c.y, c.z, 10 + d, 0.2D + d * 0.08D, 0.2D + d * 0.08D,
                    0.2D + d * 0.08D, 0.05D);
            if (d % 4 == 1) {
                level.sendParticles(ParticleTypes.SONIC_BOOM, c.x, c.y, c.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
            }
        }
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.LIGHTNING_BOLT_THUNDER,
                SoundSource.PLAYERS, 2.0F, 0.8F);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GENERIC_EXPLODE,
                SoundSource.PLAYERS, 1.6F, 0.5F);
    }

    /** Level 6: rips a rock out of the ground and throws it. */
    private static void boulder(ServerPlayer player, ServerLevel level) {
        Vec3 dir = flatLook(player);
        BlockPos g = ground(level, player.getX() + dir.x * 1.5D, player.getY(), player.getZ() + dir.z * 1.5D);
        BlockState state = level.getBlockState(g);
        ItemStack rock = new ItemStack(movable(level, g) && state.getBlock().asItem() != net.minecraft.world.item.Items.AIR
                ? state.getBlock().asItem() : Blocks.COBBLESTONE.asItem());
        if (HulkPowers.destroyBlocks(player) && movable(level, g)) {
            level.destroyBlock(g, false, player);
        }
        com.flash.entity.HulkBoulder boulder = new com.flash.entity.HulkBoulder(level, player);
        boulder.setItem(rock);
        boulder.setDamage(14.0F + 2.0F * power(player));
        boulder.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, 1.9F, 0.5F);
        level.addFreshEntity(boulder);
        player.swing(InteractionHand.MAIN_HAND, true);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.STONE_BREAK,
                SoundSource.PLAYERS, 1.4F, 0.5F);
    }

    /** Level 7: gamma fury - 20 seconds of more damage, toughness and faster healing. */
    private static void fury(ServerPlayer player, ServerLevel level) {
        HulkPowers.startFury(player);
        level.sendParticles(HulkPowers.FURY_DUST, player.getX(), player.getY() + 1.5D, player.getZ(), 80,
                player.getBbWidth(), player.getBbHeight() * 0.5D, player.getBbWidth(), 0.0D);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.RAVAGER_ROAR,
                SoundSource.PLAYERS, 2.5F, 0.4F);
        player.displayClientMessage(Component.translatable("message.flash.hulk_fury"), true);
    }

    /** Level 8: a burst of gamma radiation all around. */
    private static void gammaWave(ServerPlayer player, ServerLevel level) {
        double radius = 10.0D;
        float damage = 25.0F + 2.0F * power(player);
        for (LivingEntity e : around(player, player.getBoundingBox().inflate(radius))) {
            Vec3 away = e.position().subtract(player.position());
            double d = away.length();
            if (d > radius) {
                continue;
            }
            Vec3 push = new Vec3(away.x, 0.0D, away.z).normalize().scale(2.0D).add(0.0D, 0.7D, 0.0D);
            hit(player, e, (float) (damage * (1.0D - d / (radius * 1.5D))), push);
        }
        for (int ring = 1; ring <= 10; ring++) {
            for (int i = 0; i < 24; i++) {
                double a = i * Math.PI * 2.0D / 24.0D;
                level.sendParticles(HulkPowers.GAMMA_DUST, player.getX() + Math.cos(a) * ring, player.getY() + 0.6D,
                        player.getZ() + Math.sin(a) * ring, 1, 0.1D, 0.3D, 0.1D, 0.0D);
            }
        }
        level.sendParticles(ParticleTypes.FLASH, player.getX(), player.getY() + 1.0D, player.getZ(), 2, 0.0D, 0.0D, 0.0D, 0.0D);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_SONIC_BOOM,
                SoundSource.PLAYERS, 2.0F, 0.5F);
    }

    /** Level 10: stomps so hard that the ground breaks in rings around the Hulk. */
    private static void worldBreakerTick(ServerPlayer player, ServerLevel level, Vec3 origin, int t) {
        if (t % 4 != 0) {
            return;
        }
        double r = 2.0D + t * 0.4D;
        float damage = 18.0F + 2.0F * power(player);
        for (LivingEntity e : around(player, new AABB(origin, origin).inflate(r + 1.5D, 4.0D, r + 1.5D))) {
            double d = Math.hypot(e.getX() - origin.x, e.getZ() - origin.z);
            if (Math.abs(d - r) > 1.6D) {
                continue;
            }
            Vec3 away = new Vec3(e.getX() - origin.x, 0.0D, e.getZ() - origin.z).normalize();
            hit(player, e, damage, away.scale(0.8D).add(0.0D, 1.0D, 0.0D));
        }
        boolean destroy = HulkPowers.destroyBlocks(player);
        int n = Mth.ceil(r * 3.0D);
        for (int i = 0; i < n; i++) {
            double a = i * Math.PI * 2.0D / n;
            double x = origin.x + Math.cos(a) * r;
            double z = origin.z + Math.sin(a) * r;
            BlockPos g = ground(level, x, origin.y, z);
            dust(level, g, 3);
            if (destroy && i % 2 == 0) {
                lift(level, g, 0.5D + level.random.nextDouble() * 0.4D,
                        new Vec3(Math.cos(a), 0.0D, Math.sin(a)).scale(0.1D));
            }
        }
        level.playSound(null, origin.x, origin.y, origin.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS,
                1.2F, 0.4F + t * 0.01F);
    }

    public static void reset(ServerPlayer player) {
        RUNNING.remove(player.getUUID());
    }
}
