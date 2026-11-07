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
        if (RUNNING.containsKey(player.getUUID()) || DELAYED.containsKey(player.getUUID())) {
            return;
        }
        ServerLevel level = (ServerLevel) player.level();
        Hold hold = HOLDS.get(player.getUUID());
        if (hold != null) {
            // second press: throw what is held (rock or creature)
            if (hold.kind() == HOLD_ROCK && ability == HulkAbility.BOULDER) {
                // the throw comes with the force bar (HulkThrowPacket) when the key is released
                return;
            } else if (hold.kind() == HOLD_ENTITY && ability == HulkAbility.GRAB) {
                // the throw comes with the force bar (HulkThrowPacket) when V is released
                return;
            }
            return;
        }
        int anim = ability.ordinal();
        switch (ability) {
            // the fists hit the ground 0.35 s into the "smash" animation
            case GROUND_SMASH -> delay(player, 7, p -> groundSmash(p, (ServerLevel) p.level()));
            case CHARGE -> RUNNING.put(player.getUUID(), new Running(ability, flatLook(player), player.position(), new int[] {0, 0}));
            case ROAR -> {
                roar(player, level);
                anim = -1;
            }
            // the hands meet 0.8 s into the "thunderclap" animation
            case THUNDERCLAP -> delay(player, 16, p -> {
                int steps = thunderclap(p, (ServerLevel) p.level());
                RUNNING.put(p.getUUID(), new Running(HulkAbility.THUNDERCLAP, p.getLookAngle().normalize(),
                        p.getEyePosition(), new int[] {0, steps}));
            });
            case BOULDER -> {
                // first press: rip a rock out of the ground and hold it over the head
                HOLDS.put(player.getUUID(), new Hold(HOLD_ROCK, -1, pickRock(player, level), player.tickCount));
                // the size of the rock goes with the code (200 + size x 10)
                sendAnim(player, 200 + Math.round(com.flash.entity.HulkBoulder.sizeFor(HulkPowers.activeLevel(player)) * 10.0F));
                return;
            }
            case FURY -> {
                fury(player, level);
                anim = -1;
            }
            case GAMMA_WAVE -> gammaWave(player, level);
            case WORLD_BREAKER -> {
                RUNNING.put(player.getUUID(), new Running(ability, flatLook(player), player.position(), new int[] {0, 0}));
                level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.RAVAGER_ROAR,
                        SoundSource.PLAYERS, 2.0F, 0.5F);
            }
            case GRAB -> {
                LivingEntity target = grabTarget(player);
                if (target == null) {
                    player.displayClientMessage(Component.translatable("message.flash.hulk_grab_none"), true);
                    return;
                }
                HOLDS.put(player.getUUID(), new Hold(HOLD_ENTITY, target.getId(), ItemStack.EMPTY, player.tickCount));
                level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.RAVAGER_ATTACK,
                        SoundSource.PLAYERS, 1.5F, 0.6F);
                sendAnim(player, oneHand(player, target) ? ANIM_HOLD_SMALL : ANIM_HOLD_ENTITY);
                return;
            }
            default -> {
            }
        }
        finish(player, ability, anim);
    }

    /** Cooldown, sync and the animation for everyone around (anim < 0 = none). */
    private static void finish(ServerPlayer player, HulkAbility ability, int anim) {
        HulkPowers.setCooldown(player, ability);
        HulkPowers.syncSelf(player);
        if (anim >= 0) {
            sendAnim(player, anim);
        }
    }

    // animation codes of HulkAnimPacket besides the ability ids
    public static final int ANIM_HOLD_ROCK = 100;
    public static final int ANIM_THROW_ROCK = 101;
    public static final int ANIM_HOLD_ENTITY = 102;
    public static final int ANIM_THROW_ENTITY = 103;
    public static final int ANIM_RELEASE = 104;
    public static final int ANIM_HOLD_SMALL = 105;

    /** Small creatures (by hitbox, compared with the Hulk's size) are held in one hand. */
    static boolean oneHand(ServerPlayer hulk, net.minecraft.world.entity.Entity target) {
        return target.getBbHeight() <= Math.max(1.0F, hulk.getBbHeight() * 0.3F)
                && target.getBbWidth() <= Math.max(0.9F, hulk.getBbWidth() * 0.5F);
    }

    private static void sendAnim(ServerPlayer player, int anim) {
        com.flash.network.FlashNetwork.CHANNEL.send(
                net.minecraftforge.network.PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player),
                new com.flash.network.HulkAnimPacket(player.getUUID(), anim));
    }

    // ------------------------------------------------------------------
    // Delayed hits (in time with the animations)
    // ------------------------------------------------------------------

    private record Delayed(int due, java.util.function.Consumer<ServerPlayer> action) {
    }

    private static final Map<UUID, Delayed> DELAYED = new ConcurrentHashMap<>();

    private static void delay(ServerPlayer player, int ticks, java.util.function.Consumer<ServerPlayer> action) {
        DELAYED.put(player.getUUID(), new Delayed(player.tickCount + ticks, action));
    }

    // ------------------------------------------------------------------
    // Holding a rock or a creature
    // ------------------------------------------------------------------

    private static final int HOLD_ROCK = 0;
    private static final int HOLD_ENTITY = 1;

    private record Hold(int kind, int entityId, ItemStack rock, int since) {
    }

    private static final Map<UUID, Hold> HOLDS = new ConcurrentHashMap<>();

    /** Thrown creatures: they take damage when they hit something. */
    private record Thrown(UUID thrower, float damage, int since) {
    }

    private static final Map<Integer, Thrown> THROWN = new ConcurrentHashMap<>();

    public static boolean isHolding(net.minecraft.world.entity.player.Player player) {
        return HOLDS.containsKey(player.getUUID());
    }

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) {
            return;
        }
        ServerLevel level = (ServerLevel) player.level();
        boolean hulk = HulkPowers.isTransformed(player) && player.isAlive();
        Delayed delayed = DELAYED.get(player.getUUID());
        if (delayed != null) {
            if (!hulk) {
                DELAYED.remove(player.getUUID());
            } else if (player.tickCount >= delayed.due()) {
                DELAYED.remove(player.getUUID());
                delayed.action().accept(player);
            }
        }
        tickHold(player, level, hulk);
        tickThrown(player, level);
        tickFlights(player, level);
        Running run = RUNNING.get(player.getUUID());
        if (run == null) {
            return;
        }
        if (!hulk) {
            RUNNING.remove(player.getUUID());
            return;
        }
        int t = ++run.ticks()[0];
        if (run.ability() == HulkAbility.CHARGE) {
            chargeTick(player, level, run.dir(), t);
            if (t >= 10) {
                RUNNING.remove(player.getUUID());
            }
        } else if (run.ability() == HulkAbility.THUNDERCLAP) {
            thunderclapTick(player, level, run.origin(), run.dir(), t, run.ticks()[1]);
            if (t >= run.ticks()[1]) {
                RUNNING.remove(player.getUUID());
            }
        } else if (run.ability() == HulkAbility.WORLD_BREAKER) {
            worldBreakerTick(player, level, run.origin(), t);
            // the "tectonic_fury" animation is 5 s: the ability lasts as long
            if (t >= 100) {
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

    /** All the Hulk's ability hits deal twice their base damage (he is far stronger than a player). */
    private static final float ABILITY_DAMAGE = 2.0F;

    private static void hit(ServerPlayer player, LivingEntity target, float damage, Vec3 push) {
        target.invulnerableTime = 0;
        target.hurt(player.damageSources().playerAttack(player), damage * ABILITY_DAMAGE);
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

    /** Reach of the thunderclap by active level: 5 -> 5 blocks, 6 -> 7, 7 -> 10, 8 -> 13, 9 -> 16, 10 -> 20. */
    public static double clapRange(int level) {
        int[] reach = {5, 5, 5, 5, 5, 5, 7, 10, 13, 16, 20};
        return reach[Math.max(0, Math.min(10, level))];
    }

    /**
     * Level 5: thunderclap - when the hands meet, a frontal blast whose reach grows with the
     * active level. Returns how many explosions follow (thunderclapTick).
     */
    private static int thunderclap(ServerPlayer player, ServerLevel level) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle().normalize();
        float damage = 20.0F + 3.0F * power(player);
        double range = clapRange(HulkPowers.activeLevel(player));
        for (LivingEntity e : around(player, player.getBoundingBox().inflate(range + 2.0D))) {
            Vec3 d = e.getBoundingBox().getCenter().subtract(eye);
            double dist = d.length();
            if (dist > range + e.getBbWidth() || dist < 0.01D || d.normalize().dot(look) < 0.8D) {
                continue;
            }
            hit(player, e, (float) (damage * (1.0D - dist / (range * 1.6D))), look.scale(3.0D).add(0.0D, 0.6D, 0.0D));
        }
        // the shockwave: rings of air flying forward
        for (int d = 1; d <= range; d += 2) {
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
        return Math.max(1, (int) Math.ceil((range - 1.5D) / 2.25D));
    }

    /** Thunderclap blast: a row of explosions flying forward up to the reach of the level. */
    private static void thunderclapTick(ServerPlayer player, ServerLevel level, Vec3 eye, Vec3 look, int t, int steps) {
        double range = 1.5D + 2.25D * steps;
        double d = Math.min(range, 1.5D + 2.25D * t);
        Vec3 c = eye.add(look.scale(d));
        blast(player, level, c.x, c.y, c.z, 3.0F);
    }

    /** Level 6 (first press): rips a rock out of the ground; the Hulk holds it over his head. */
    private static ItemStack pickRock(ServerPlayer player, ServerLevel level) {
        Vec3 dir = flatLook(player);
        BlockPos g = ground(level, player.getX() + dir.x * 1.5D, player.getY(), player.getZ() + dir.z * 1.5D);
        BlockState state = level.getBlockState(g);
        ItemStack rock = new ItemStack(movable(level, g) && state.getBlock().asItem() != net.minecraft.world.item.Items.AIR
                ? state.getBlock().asItem() : Blocks.COBBLESTONE.asItem());
        if (HulkPowers.destroyBlocks(player) && movable(level, g)) {
            level.destroyBlock(g, false, player);
        }
        dust(level, g, 20);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.STONE_BREAK,
                SoundSource.PLAYERS, 1.4F, 0.5F);
        return rock;
    }

    /** Level 6 (second press): throws the rock that is held. */
    /**
     * The key was released: throws what is held with the charged force (0..1); after a minute
     * of charge a held creature gets the super throw (about 2000 blocks).
     */
    public static void throwHeld(ServerPlayer player, float charge, boolean superThrow) {
        Hold hold = HOLDS.get(player.getUUID());
        if (hold == null || !HulkPowers.isTransformed(player)) {
            return;
        }
        HOLDS.remove(player.getUUID());
        ServerLevel level = (ServerLevel) player.level();
        if (hold.kind() == HOLD_ROCK) {
            throwRock(player, level, hold.rock(), charge);
            finish(player, HulkAbility.BOULDER, ANIM_THROW_ROCK);
        } else {
            // the server checks the minute itself (since the grab)
            boolean full = superThrow && (HulkPowers.noCooldowns(player)
                    || player.tickCount - hold.since() >= SUPER_TICKS);
            throwEntity(player, level, hold, charge, full);
            finish(player, HulkAbility.GRAB, ANIM_THROW_ENTITY);
        }
    }

    /** Charge time of the super throw (1 minute). */
    public static final int SUPER_TICKS = 1200;

    /** A held creature that hits the Hulk breaks free and drops to the ground. */
    public static void breakFree(ServerPlayer hulk, net.minecraft.world.entity.Entity attacker) {
        Hold hold = HOLDS.get(hulk.getUUID());
        if (hold == null || hold.kind() != HOLD_ENTITY || hold.entityId() != attacker.getId()) {
            return;
        }
        HOLDS.remove(hulk.getUUID());
        attacker.setDeltaMovement(0.0D, -0.3D, 0.0D);
        attacker.hurtMarked = true;
        sendAnim(hulk, ANIM_RELEASE);
        HulkPowers.setCooldown(hulk, HulkAbility.GRAB);
        HulkPowers.syncSelf(hulk);
        hulk.displayClientMessage(Component.translatable("message.flash.hulk_grab_escaped"), true);
    }

    private static void throwRock(ServerPlayer player, ServerLevel level, ItemStack rock, float charge) {
        com.flash.entity.HulkBoulder boulder = new com.flash.entity.HulkBoulder(level, player);
        boulder.setItem(rock.isEmpty() ? new ItemStack(Blocks.COBBLESTONE) : rock);
        float size = com.flash.entity.HulkBoulder.sizeFor(HulkPowers.activeLevel(player));
        boulder.setSize(size);
        boulder.setDamage((10.0F + 2.0F * power(player)) * (0.6F + 0.4F * size) * ABILITY_DAMAGE
                * (0.4F + 0.6F * charge));
        boulder.setCharge(charge);
        boulder.setPos(player.getX(), player.getY() + player.getBbHeight() * 0.95D, player.getZ());
        // the fuller the bar, the farther it goes
        boulder.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, 0.9F + 2.4F * charge, 0.5F);
        level.addFreshEntity(boulder);
        player.swing(InteractionHand.MAIN_HAND, true);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.RAVAGER_ATTACK,
                SoundSource.PLAYERS, 1.4F, 0.5F);
    }

    /** The creature in front of the Hulk, in reach of his hands. */
    private static LivingEntity grabTarget(ServerPlayer player) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle().normalize();
        double reach = 2.5D + player.getBbWidth() * 1.5D;
        LivingEntity best = null;
        double bestScore = Double.MAX_VALUE;
        for (LivingEntity e : around(player, player.getBoundingBox().inflate(reach + 1.0D))) {
            if (HOLDS.values().stream().anyMatch(h -> h.entityId() == e.getId())) {
                continue;
            }
            Vec3 d = e.getBoundingBox().getCenter().subtract(eye);
            double dist = d.length();
            if (dist > reach + e.getBbWidth() || d.normalize().dot(look) < 0.55D) {
                continue;
            }
            double score = dist - d.normalize().dot(look) * 2.0D;
            if (score < bestScore) {
                bestScore = score;
                best = e;
            }
        }
        return best;
    }

    /** Keeps the held creature over the Hulk's head; the rock / creature is dropped if he changes back. */
    private static void tickHold(ServerPlayer player, ServerLevel level, boolean hulk) {
        Hold hold = HOLDS.get(player.getUUID());
        if (hold == null) {
            return;
        }
        if (!hulk) {
            HOLDS.remove(player.getUUID());
            sendAnim(player, ANIM_RELEASE);
            return;
        }
        if (hold.kind() != HOLD_ENTITY) {
            return;
        }
        net.minecraft.world.entity.Entity e = level.getEntity(hold.entityId());
        if (!(e instanceof LivingEntity target) || !target.isAlive()) {
            HOLDS.remove(player.getUUID());
            sendAnim(player, ANIM_RELEASE);
            return;
        }
        Vec3 f = flatLook(player);
        double x;
        double y;
        double z;
        if (oneHand(player, target)) {
            // in the right hand, in front of the chest
            double fwd = 0.35D + player.getBbWidth() * 0.6D;
            double side = player.getBbWidth() * 0.45D;
            x = player.getX() + f.x * fwd - f.z * side;
            z = player.getZ() + f.z * fwd + f.x * side;
            y = player.getY() + player.getBbHeight() * 0.5D - target.getBbHeight() * 0.5D;
        } else {
            // with both hands, lifted over the head after the grab
            double lift = Math.min(1.0D, (player.tickCount - hold.since()) / 10.0D);
            x = player.getX() + f.x * (0.4D + player.getBbWidth() * 0.3D);
            z = player.getZ() + f.z * (0.4D + player.getBbWidth() * 0.3D);
            y = player.getY() + player.getBbHeight() * (0.45D + 0.6D * lift);
        }
        target.setDeltaMovement(Vec3.ZERO);
        target.fallDistance = 0.0F;
        if (HulkPowers.isRed(player)) {
            // the Red Hulk's hands burn whoever he holds
            target.setSecondsOnFire(3);
        }
        if (target instanceof ServerPlayer held) {
            held.connection.teleport(x, y, z, held.getYRot(), held.getXRot());
        } else {
            target.teleportTo(x, y, z);
        }
    }

    private static void throwEntity(ServerPlayer player, ServerLevel level, Hold hold, float charge, boolean superThrow) {
        net.minecraft.world.entity.Entity e = level.getEntity(hold.entityId());
        if (!(e instanceof LivingEntity target) || !target.isAlive()) {
            return;
        }
        Vec3 look = player.getLookAngle().normalize();
        if (superThrow) {
            superThrow(player, level, target, look);
            return;
        }
        double force = (1.8D + 0.18D * HulkPowers.activeLevel(player)) * (0.35D + 0.9D * charge);
        target.setDeltaMovement(look.scale(force).add(0.0D, 0.35D, 0.0D));
        target.hurtMarked = true;
        THROWN.put(target.getId(), new Thrown(player.getUUID(),
                (10.0F + 2.5F * power(player)) * ABILITY_DAMAGE * (0.5F + 0.7F * charge), player.tickCount));
        player.swing(InteractionHand.MAIN_HAND, true);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.RAVAGER_ROAR,
                SoundSource.PLAYERS, 1.2F, 0.9F);
    }

    // ------------------------------------------------------------------
    // Super throw: a minute of charge sends the creature about 2000 blocks away
    // ------------------------------------------------------------------

    private static final double SUPER_DISTANCE = 2000.0D;
    private static final int SUPER_FLIGHT = 100;
    /** Impact of the super throw: 6 TNTs (TNT = 4). */
    private static final float SUPER_BLAST = 24.0F;

    /** A creature on its super-throw flight: moved along the arc every tick. */
    private record Flight(UUID thrower, Vec3 from, Vec3 dir, double peak, int since) {
    }

    private static final Map<Integer, Flight> FLIGHTS = new ConcurrentHashMap<>();

    private static void superThrow(ServerPlayer player, ServerLevel level, LivingEntity target, Vec3 look) {
        Vec3 flat = new Vec3(look.x, 0.0D, look.z);
        flat = flat.lengthSqr() < 1.0E-4D ? flatLook(player) : flat.normalize();
        FLIGHTS.put(target.getId(), new Flight(player.getUUID(), target.position(), flat,
                150.0D + Math.max(0.0D, look.y) * 150.0D, player.tickCount));
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.RAVAGER_ROAR,
                SoundSource.PLAYERS, 3.0F, 0.4F);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GENERIC_EXPLODE,
                SoundSource.PLAYERS, 2.0F, 0.6F);
        level.sendParticles(ParticleTypes.SONIC_BOOM, target.getX(), target.getY() + 1.0D, target.getZ(), 1,
                0.0D, 0.0D, 0.0D, 0.0D);
        player.swing(InteractionHand.MAIN_HAND, true);
    }

    private static void tickFlights(ServerPlayer player, ServerLevel level) {
        if (FLIGHTS.isEmpty()) {
            return;
        }
        FLIGHTS.entrySet().removeIf(entry -> {
            Flight f = entry.getValue();
            if (!f.thrower().equals(player.getUUID())) {
                return false;
            }
            net.minecraft.world.entity.Entity e = level.getEntity(entry.getKey());
            LivingEntity target = e instanceof LivingEntity living && living.isAlive() ? living : null;
            int age = player.tickCount - f.since();
            double t0 = Math.min(1.0D, (age - 1) / (double) SUPER_FLIGHT);
            double t1 = Math.min(1.0D, age / (double) SUPER_FLIGHT);
            Vec3 from = flightPoint(f, Math.max(0.0D, t0));
            Vec3 to = flightPoint(f, t1);
            // the whole path of this tick, every half block: the first solid block is the impact
            Vec3 impact = null;
            double length = to.distanceTo(from);
            int steps = Math.max(1, (int) Math.ceil(length / 0.5D));
            for (int i = 1; i <= steps && t1 > 0.03D; i++) {
                Vec3 p = from.lerp(to, i / (double) steps);
                BlockPos at = BlockPos.containing(p.x, p.y + 0.5D, p.z);
                if (!level.getBlockState(at).getCollisionShape(level, at).isEmpty()) {
                    impact = from.lerp(to, Math.max(0, i - 1) / (double) steps);
                    break;
                }
            }
            if (impact == null && t1 >= 1.0D) {
                // landing: on the ground at the end of the arc
                int gy = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING,
                        (int) Math.floor(to.x), (int) Math.floor(to.z));
                impact = new Vec3(to.x, gy, to.z);
            }
            if (impact != null) {
                if (target != null) {
                    move(target, impact.x, impact.y, impact.z);
                    target.fallDistance = 0.0F;
                    target.invulnerableTime = 0;
                    target.hurt(player.damageSources().playerAttack(player), 40.0F * ABILITY_DAMAGE);
                }
                // the impact blows up like 6 TNTs (even if the creature is gone)
                blast(player, level, impact.x, impact.y + 0.5D, impact.z, SUPER_BLAST);
                return true;
            }
            if (target == null) {
                // the creature is gone (unloaded): the flight goes on to its impact
                return false;
            }
            // keep the area loaded around the flying creature
            level.getChunkSource().addRegionTicket(net.minecraft.server.level.TicketType.POST_TELEPORT,
                    new net.minecraft.world.level.ChunkPos(BlockPos.containing(to)), 1, target.getId());
            target.fallDistance = 0.0F;
            target.setDeltaMovement(Vec3.ZERO);
            move(target, to.x, to.y, to.z);
            if (player.tickCount % 2 == 0) {
                level.sendParticles(ParticleTypes.CLOUD, to.x, to.y, to.z, 4, 0.3D, 0.3D, 0.3D, 0.02D);
            }
            return false;
        });
    }

    /** Point of the super-throw arc at {@code t} (0..1). */
    private static Vec3 flightPoint(Flight f, double t) {
        return new Vec3(f.from().x + f.dir().x * SUPER_DISTANCE * t,
                f.from().y + 4.0D * f.peak() * t * (1.0D - t),
                f.from().z + f.dir().z * SUPER_DISTANCE * t);
    }

    private static void move(LivingEntity target, double x, double y, double z) {
        if (target instanceof ServerPlayer held) {
            held.connection.teleport(x, y, z, held.getYRot(), held.getXRot());
        } else {
            target.teleportTo(x, y, z);
        }
    }

    /** Creatures thrown by this Hulk crash: damage when they hit a wall or the ground. */
    private static void tickThrown(ServerPlayer player, ServerLevel level) {
        if (THROWN.isEmpty()) {
            return;
        }
        THROWN.entrySet().removeIf(entry -> {
            Thrown thrown = entry.getValue();
            if (!thrown.thrower().equals(player.getUUID())) {
                return false;
            }
            int age = player.tickCount - thrown.since();
            net.minecraft.world.entity.Entity e = level.getEntity(entry.getKey());
            if (!(e instanceof LivingEntity target) || !target.isAlive() || age > 80) {
                return true;
            }
            if (age > 2 && (target.horizontalCollision || target.onGround())) {
                target.invulnerableTime = 0;
                target.hurt(player.damageSources().playerAttack(player), thrown.damage());
                dust(level, target.blockPosition().below(), 25);
                level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.GENERIC_EXPLODE,
                        SoundSource.PLAYERS, 1.0F, 0.7F);
                HulkPowers.addRage(player, 3.0F);
                return true;
            }
            return false;
        });
    }

    /** Level 7: gamma fury - 20 seconds of more damage, toughness and faster healing. */
    private static void fury(ServerPlayer player, ServerLevel level) {
        HulkPowers.startFury(player);
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
        for (int ring = 1; ring <= 10; ring++) {
            for (int i = 0; i < 24; i++) {
                double a = i * Math.PI * 2.0D / 24.0D;
                level.sendParticles(com.flash.registry.ModParticles.GAMMA.get(), player.getX() + Math.cos(a) * ring, player.getY() + 0.6D,
                        player.getZ() + Math.sin(a) * ring, 1, 0.1D, 0.3D, 0.1D, 0.0D);
            }
        }
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.RAVAGER_ROAR,
                SoundSource.PLAYERS, 3.0F, 0.4F);
    }

    /** Ticks of the 8 stomps of the "tectonic_fury" animation (0.65 s, 0.95 s, 1.65 s, ...). */
    private static final int[] STOMPS = {13, 19, 33, 39, 53, 59, 73, 79};

    /** Level 10: tectonic fury - every stomp blows up a wider ring of ground around the Hulk. */
    private static void worldBreakerTick(ServerPlayer player, ServerLevel level, Vec3 origin, int t) {
        int stomp = -1;
        for (int i = 0; i < STOMPS.length; i++) {
            if (STOMPS[i] == t) {
                stomp = i;
            }
        }
        if (stomp < 0) {
            return;
        }
        double r = 3.0D + stomp * 2.2D;
        float damage = 18.0F + 2.0F * power(player);
        for (LivingEntity e : around(player, new AABB(origin, origin).inflate(r + 1.5D, 4.0D, r + 1.5D))) {
            double d = Math.hypot(e.getX() - origin.x, e.getZ() - origin.z);
            if (Math.abs(d - r) > 1.8D) {
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
        int blasts = Math.max(4, Mth.ceil(Math.PI * 2.0D * r / 6.0D));
        double offset = level.random.nextDouble() * Math.PI * 2.0D;
        for (int i = 0; i < blasts; i++) {
            double a = offset + i * Math.PI * 2.0D / blasts;
            BlockPos g = ground(level, origin.x + Math.cos(a) * r, origin.y, origin.z + Math.sin(a) * r);
            blast(player, level, g.getX() + 0.5D, g.getY() + 0.5D, g.getZ() + 0.5D, 3.5F);
        }
        level.playSound(null, origin.x, origin.y, origin.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS,
                1.4F, 0.5F + stomp * 0.04F);
    }

    public static void reset(ServerPlayer player) {
        RUNNING.remove(player.getUUID());
        DELAYED.remove(player.getUUID());
        HOLDS.remove(player.getUUID());
    }
}
