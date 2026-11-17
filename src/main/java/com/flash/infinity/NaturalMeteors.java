package com.flash.infinity;

import com.flash.FlashMod;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Dwarf Star meteors falling on their own: at night in the Overworld, now and then (about once every 20 minutes of
 * night per player), a burning rock comes down 40-120 blocks from a player, blows a small crater and leaves a few
 * blocks of Dwarf Star Meteor (smelted into Dwarf Star Alloy) in it, wrapped in magma.
 * {@code flash_natural_meteor} on the falling block; {@link #fall} drops one at once (for tests).
 */
@Mod.EventBusSubscriber(modid = FlashMod.MODID)
public final class NaturalMeteors {

    private static final String TAG = "flash_natural_meteor";
    /** Chance per player per second of night. */
    private static final float CHANCE = 1.0F / 1200.0F;
    private static final Map<UUID, Long> LAST = new HashMap<>();

    private NaturalMeteors() {
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        ServerLevel over = event.getServer().overworld();
        long t = over.getGameTime();
        if (t % 20L == 0L) {
            long day = over.getDayTime() % 24000L;
            if (day > 13000L && day < 23000L) {
                for (ServerPlayer p : over.players()) {
                    if (p.isSpectator()) {
                        continue;
                    }
                    Long last = LAST.get(p.getUUID());
                    if (last != null && t - last < 6000L) {
                        continue;
                    }
                    if (p.getRandom().nextFloat() < CHANCE) {
                        LAST.put(p.getUUID(), t);
                        fall(p);
                    }
                }
            }
        }
        for (var e : over.getAllEntities()) {
            if (e instanceof FallingBlockEntity fb && fb.getPersistentData().contains(TAG)) {
                over.sendParticles(ParticleTypes.FLAME, fb.getX(), fb.getY(), fb.getZ(), 6, 0.5D, 0.5D, 0.5D, 0.03D);
                over.sendParticles(ParticleTypes.LARGE_SMOKE, fb.getX(), fb.getY() + 1.0D, fb.getZ(), 3, 0.4D, 0.4D,
                        0.4D, 0.01D);
                if (fb.onGround() || fb.isRemoved() || fb.tickCount > 600) {
                    land(over, fb.blockPosition(), over.getRandom());
                    fb.discard();
                }
            }
        }
    }

    /** A meteor on its way down, near this player. */
    public static void fall(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        RandomSource r = p.getRandom();
        double ang = r.nextDouble() * Math.PI * 2.0D;
        double dist = 40.0D + r.nextDouble() * 80.0D;
        int x = (int) (p.getX() + Math.cos(ang) * dist);
        int z = (int) (p.getZ() + Math.sin(ang) * dist);
        int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        int y = Math.min(level.getMaxBuildHeight() - 2, ground + 90);
        // it comes in at a slant, from 30 blocks off
        FallingBlockEntity m = FallingBlockEntity.fall(level, new BlockPos(x - 30, y, z),
                InfinityItems.DWARF_STAR_METEOR.get().defaultBlockState());
        m.dropItem = false;
        m.setDeltaMovement(0.3D, -1.0D, 0.0D);
        m.getPersistentData().putBoolean(TAG, true);
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.FIRECHARGE_USE, SoundSource.AMBIENT, 1.5F,
                0.5F);
        p.displayClientMessage(Component.translatable("message.flash.meteor_falling"), true);
    }

    /** The impact: a crater and the Dwarf Star rock in it. */
    private static void land(ServerLevel level, BlockPos at, RandomSource r) {
        level.explode(null, at.getX() + 0.5D, at.getY() + 0.5D, at.getZ() + 0.5D, 3.5F, true,
                Level.ExplosionInteraction.TNT);
        level.playSound(null, at, SoundEvents.GENERIC_EXPLODE, SoundSource.AMBIENT, 4.0F, 0.6F);
        // the crater's floor
        BlockPos floor = at;
        for (int i = 0; i < 8 && level.getBlockState(floor.below()).isAir(); i++) {
            floor = floor.below();
        }
        BlockState rock = InfinityItems.DWARF_STAR_METEOR.get().defaultBlockState();
        int count = 3 + r.nextInt(4);
        for (BlockPos pos : BlockPos.betweenClosed(floor.offset(-1, -1, -1), floor.offset(1, 1, 1))) {
            BlockState st = level.getBlockState(pos);
            if (st.getDestroySpeed(level, pos) < 0.0F) {
                continue;
            }
            if (count > 0 && r.nextInt(3) == 0 || pos.equals(floor)) {
                level.setBlockAndUpdate(pos, rock);
                count--;
            } else if (pos.getY() < floor.getY() + 1 && r.nextInt(2) == 0) {
                level.setBlockAndUpdate(pos, Blocks.MAGMA_BLOCK.defaultBlockState());
            }
        }
    }
}
