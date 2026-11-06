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
            case THUNDERCLAP -> {
                thunderclap(player, level);
                RUNNING.put(player.getUUID(), new Running(ability, player.getLookAngle().normalize(),
                        player.getEyePosition(), new int[] {0}));
            }
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
        } else if (run.ability() == HulkAbility.THUNDERCLAP) {
            thunderclapTick(player, level, run.origin(), run.dir(), t);
            if (t >= 6) {
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

    /**
     * A real explosion caused by the Hulk (it breaks blocks like TNT; with destruction off it only
     * hurts). The Hulk himself is left out of it in {@code HulkEvents.onDetonate}.
     */
    static void blast(ServerPlayer player, ServerLevel level, double x, double y, double z, float power) {
        level.explode(player, x, y, z, power, HulkPowers.destroyBlocks(player)
                ? net.minecraft.world.level.Level.ExplosionInteraction.TNT
                : net.minecraft.world.level.Level.ExplosionInteraction.NONE);
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

    /** Level 2: punches the ground - a TNT explosion where the fist lands. */
    private static void groundSmash(ServerPlayer player, ServerLevel level) {
        Vec3 dir = flatLook(player);
        double ahead = player.getBbWidth() * 0.5D + 1.5D;
        BlockPos g = ground(level, player.getX() + dir.x * ahead, player.getY(), player.getZ() + dir.z * ahead);
        float damage = 8.0F + 2.0F * power(player);
        player.swing(InteractionHand.MAIN_HAND, true);
        player.swing(InteractionHand.OFF_HAND, true);
        for (int d = 1; d <= 6; d++) {
            Vec3 c = player.position().add(dir.scale(d));
            for (LivingEntity e : around(player, new AABB(c, c).inflate(1.6D, 2.0D, 1.6D))) {
                hit(player, e, damage * (1.0F - d * 0.05F), dir.scale(0.6D).add(0.0D, 0.9D, 0.0D));
            }
        }
        dust(level, g, 30);
        // TNT is power 4; a little stronger every level (6.25 at level 10)
        float tnt = 4.0F + 0.25F * (HulkPowers.activeLevel(player) - 1);
        blast(player, level, g.getX() + 0.5D, g.getY() + 0.5D, g.getZ() + 0.5D, tnt);
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
            // everything but the hardest blocks (obsidian, bedrock...) is smashed through
            BlockPos.betweenClosedStream(new AABB(front.minX, front.minY + 0.6D, front.minZ,
                            front.maxX, front.maxY, front.maxZ).inflate(0.5D, 0.0D, 0.5D))
                    .limit(160)
                    .forEach(p -> {
                        BlockState state = level.getBlockState(p);
                        if (!state.isAir() && state.getFluidState().isEmpty() && !state.hasBlockEntity()
                                && state.getDestroySpeed(level, p) >= 0.0F
                                && state.getDestroySpeed(level, p) < 30.0F) {
                            level.destroyBlock(p, level.random.nextInt(4) == 0, player);
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
        if (HulkPowers.destroyBlocks(player)) {
            // the roar shatters glass and strips leaves around
            BlockPos center = player.blockPosition();
            int broken = 0;
            for (BlockPos p : BlockPos.betweenClosed(center.offset(-10, -4, -10), center.offset(10, 8, 10))) {
                BlockState state = level.getBlockState(p);
                if ((state.is(net.minecraft.tags.BlockTags.LEAVES)
                        || state.is(net.minecraftforge.common.Tags.Blocks.GLASS)
                        || state.is(net.minecraftforge.common.Tags.Blocks.GLASS_PANES))
                        && p.distSqr(center) <= 100.0D) {
                    level.destroyBlock(p, false, player);
                    if (++broken >= 300) {
                        break;
                    }
                }
            }
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

    /** Level 5: thunderclap - a clap that sends a frontal explosion forward (see thunderclapTick). */
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

    /** Thunderclap blast: a row of explosions flying forward, up to 15 blocks ahead. */
    private static void thunderclapTick(ServerPlayer player, ServerLevel level, Vec3 eye, Vec3 look, int t) {
        double d = 1.5D + 2.25D * t;
        Vec3 c = eye.add(look.scale(d));
        blast(player, level, c.x, c.y, c.z, 3.0F);
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
            // gamma radiation sickness: Poison II and Nausea II
            e.addEffect(new MobEffectInstance(MobEffects.POISON, 200, 1), player);
            e.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 200, 1), player);
        }
        blast(player, level, player.getX(), player.getY() + 0.5D, player.getZ(), 5.0F);
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

    /** Level 10: stomps so hard that the ground explodes in rings spreading around the Hulk. */
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
        int n = Mth.ceil(r * 3.0D);
        for (int i = 0; i < n; i++) {
            double a = i * Math.PI * 2.0D / n;
            dust(level, ground(level, origin.x + Math.cos(a) * r, origin.y, origin.z + Math.sin(a) * r), 3);
        }
        // the ground blows up along the ring
        if (t % 8 == 0) {
            int blasts = Math.max(4, Mth.ceil(Math.PI * 2.0D * r / 6.0D));
            double offset = level.random.nextDouble() * Math.PI * 2.0D;
            for (int i = 0; i < blasts; i++) {
                double a = offset + i * Math.PI * 2.0D / blasts;
                BlockPos g = ground(level, origin.x + Math.cos(a) * r, origin.y, origin.z + Math.sin(a) * r);
                blast(player, level, g.getX() + 0.5D, g.getY() + 0.5D, g.getZ() + 0.5D, 3.5F);
            }
        }
        level.playSound(null, origin.x, origin.y, origin.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS,
                1.2F, 0.4F + t * 0.01F);
    }

    public static void reset(ServerPlayer player) {
        RUNNING.remove(player.getUUID());
    }
}
