package com.flash.power;

import com.flash.FlashMod;
import com.flash.network.CombustionPacket;
import com.flash.network.FlashNetwork;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

/**
 * The Combustion of the Green Lanterns mod (lanternasverdes, its Combustao), for every hero of this mod who flies:
 * up above Y 500 and diving back down fast for two seconds, the hero catches fire and comes down as a meteor
 * (CombustionClient draws it); hitting the ground blasts a crater (wider the faster), throws and hurts what is
 * around and leaves fire on its rim. Braking in the air puts the flames out.
 */
@Mod.EventBusSubscriber(modid = FlashMod.MODID)
public final class HeroCombustion {

    private static final double MIN_HEIGHT = 500.0D;
    private static final double MIN_SPEED = 0.45D;
    private static final int MIN_TICKS = 40;
    private static final Map<UUID, State> STATES = new HashMap<>();

    private HeroCombustion() {
    }

    private static final class State {
        double lastY = Double.NaN;
        int descending;
        int still;
        boolean armed;
        int braking;
        boolean flames;
        double maxSpeed;
    }

    public static boolean inFlames(UUID id) {
        State s = STATES.get(id);
        return s != null && s.flames;
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        STATES.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer p)) {
            return;
        }
        boolean hero = com.flash.yas.YasImmortal.hero(p);
        State s = STATES.get(p.getUUID());
        if (!hero) {
            if (s != null) {
                if (s.flames) {
                    flames(p, false);
                }
                STATES.remove(p.getUUID());
            }
            return;
        }
        if (s == null) {
            s = new State();
            STATES.put(p.getUUID(), s);
        }
        double y = p.getY();
        double down = Double.isNaN(s.lastY) ? 0.0D : s.lastY - y;
        s.lastY = y;
        boolean flying = p.getAbilities().flying && !p.isPassenger();
        if (!s.flames) {
            if (!flying) {
                s.descending = 0;
                s.armed = false;
                return;
            }
            if (y >= MIN_HEIGHT) {
                s.armed = true;
            }
            if (down >= MIN_SPEED && s.armed) {
                s.descending++;
                s.still = 0;
                if (s.descending >= MIN_TICKS) {
                    s.flames = true;
                    s.braking = 0;
                    s.maxSpeed = down;
                    p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS,
                            1.2F, 0.7F);
                    p.displayClientMessage(Component.translatable("message.flash.combustion"), true);
                    flames(p, true);
                }
            } else if (s.descending > 0 && ++s.still > 6) {
                s.descending = 0;
                s.still = 0;
                if (y < MIN_HEIGHT) {
                    s.armed = false;
                }
            }
            return;
        }
        // burning down
        s.maxSpeed = Math.max(s.maxSpeed, down);
        ServerLevel level = p.serverLevel();
        Vec3 c = p.position().add(0.0D, 0.9D, 0.0D);
        level.sendParticles(ParticleTypes.FLAME, c.x, c.y, c.z, 10, 0.45D, 0.7D, 0.45D, 0.04D);
        level.sendParticles(ParticleTypes.LAVA, c.x, c.y, c.z, 2, 0.3D, 0.5D, 0.3D, 0.0D);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, c.x, c.y + 1.2D, c.z, 3, 0.3D, 0.3D, 0.3D, 0.02D);
        if (p.tickCount % 8 == 0) {
            level.playSound(null, p.blockPosition(), SoundEvents.FIRE_AMBIENT, SoundSource.PLAYERS, 1.0F, 1.4F);
        }
        boolean hit = p.onGround() || p.verticalCollision || down > 0.3D && heightAboveGround(p) < 1.3D
                || p.isInWater();
        if (hit) {
            impact(p, s.maxSpeed);
            s.flames = false;
            s.descending = 0;
            s.armed = false;
            flames(p, false);
        } else if (down < 0.3D) {
            if (++s.braking > 6) {
                s.flames = false;
                s.descending = 0;
                s.armed = false;
                flames(p, false);
            }
        } else {
            s.braking = 0;
        }
    }

    private static void flames(ServerPlayer p, boolean on) {
        FlashNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> p),
                new CombustionPacket(p.getId(), on ? 1 : 0, 0.0F, 0.0F, 0.0F, 0.0F));
    }

    private static double heightAboveGround(ServerPlayer p) {
        Vec3 a = p.position();
        BlockHitResult r = p.level().clip(new ClipContext(a, a.add(0.0D, -64.0D, 0.0D), ClipContext.Block.COLLIDER,
                ClipContext.Fluid.ANY, p));
        return r.getType() == HitResult.Type.MISS ? 64.0D : a.y - r.getLocation().y;
    }

    /** Whether this hero's blows break blocks (each hero's own switch; the others follow mobGriefing). */
    private static boolean breaks(ServerPlayer p) {
        if (com.flash.yas.YasPowers.has(p)) {
            return com.flash.yas.YasDestruction.destroys(p);
        }
        if (com.flash.superman.SupermanPowers.isSuperman(p)) {
            return com.flash.superman.SupermanPowers.destroyBlocks(p);
        }
        return p.serverLevel().getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
    }

    private static void impact(ServerPlayer p, double speed) {
        ServerLevel level = p.serverLevel();
        double radius = Mth.clamp(3.0D + speed * 2.2D, 4.0D, 9.0D);
        Vec3 center = p.position().add(0.0D, -1.0D, 0.0D);
        BlockPos base = BlockPos.containing(center);
        boolean breaks = breaks(p);
        if (breaks) {
            List<BlockPos> blocks = new ArrayList<>();
            int r = (int) Math.ceil(radius);
            for (int dx = -r; dx <= r; dx++) {
                for (int dy = -r; dy <= 2; dy++) {
                    for (int dz = -r; dz <= r; dz++) {
                        double ny = dy * 1.6D;
                        if (dx * dx + ny * ny + dz * dz > radius * radius) {
                            continue;
                        }
                        BlockPos b = base.offset(dx, dy, dz);
                        BlockState st = level.getBlockState(b);
                        float hard = st.getDestroySpeed(level, b);
                        if (!st.isAir() && hard >= 0.0F && hard <= 50.0F) {
                            blocks.add(b);
                        }
                    }
                }
            }
            blocks.sort(Comparator.comparingDouble(b -> b.distToCenterSqr(center)));
            int n = 0;
            for (BlockPos b : blocks) {
                if (n++ >= 700) {
                    break;
                }
                level.destroyBlock(b, level.random.nextFloat() < 0.08F, p);
            }
            // fire on the rim
            for (int i = 0; i < 10; i++) {
                double a = level.random.nextDouble() * Math.PI * 2.0D;
                double d = radius * (0.6D + level.random.nextDouble() * 0.4D);
                BlockPos b = BlockPos.containing(center.x + Math.cos(a) * d, center.y - 1.0D, center.z + Math.sin(a) * d);
                for (int k = 0; k < 4; k++) {
                    if (level.getBlockState(b).isAir() && !level.getBlockState(b.below()).isAir()) {
                        level.setBlockAndUpdate(b, Blocks.FIRE.defaultBlockState());
                        break;
                    }
                    b = b.below();
                }
            }
        }
        float damage = (float) (6.0D + speed * 6.0D);
        for (LivingEntity t : level.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(radius + 2.5D),
                x -> x != p && x.isAlive())) {
            Vec3 rel = t.position().subtract(center);
            double d = Math.max(0.5D, rel.length());
            t.hurt(level.damageSources().playerAttack(p),
                    (float) (damage * Mth.clamp(1.4D - d / (radius + 2.5D), 0.3D, 1.0D)));
            Vec3 out = rel.normalize().scale(1.2D + speed * 0.4D);
            t.setDeltaMovement(out.x, 0.5D + speed * 0.25D, out.z);
            t.hurtMarked = true;
        }
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, center.x, center.y + 1.0D, center.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        level.sendParticles(ParticleTypes.FLAME, center.x, center.y + 1.0D, center.z, 80, radius * 0.5D, 0.6D,
                radius * 0.5D, 0.15D);
        level.sendParticles(ParticleTypes.LAVA, center.x, center.y + 1.0D, center.z, 25, radius * 0.4D, 0.4D,
                radius * 0.4D, 0.04D);
        level.playSound(null, base, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 3.0F, 0.7F);
        Vec3 at = center.add(0.0D, 1.0D, 0.0D);
        FlashNetwork.CHANNEL.send(PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(at.x, at.y, at.z,
                256.0D, level.dimension())), new CombustionPacket(p.getId(), 2, (float) at.x, (float) at.y,
                (float) at.z, (float) radius));
        p.clearFire();
        p.fallDistance = 0.0F;
    }
}
