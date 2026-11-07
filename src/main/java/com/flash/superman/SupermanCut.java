package com.flash.superman;

import com.flash.network.FlashNetwork;
import com.flash.network.SupermanCutPacket;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

/**
 * Laser cut (option of the K menu): the heat vision and the Laser Blast cut the limbs off humanoid creatures where
 * the beam hits (arm, leg or head). No arm = weaker, no legs = slow, no head = dead. Other creatures just bleed.
 */
public final class SupermanCut {

    public static final int RIGHT_ARM = 1;
    public static final int LEFT_ARM = 2;
    public static final int RIGHT_LEG = 4;
    public static final int LEFT_LEG = 8;
    public static final int HEAD = 16;
    /** Bleeding only (creatures without limbs that can be cut). */
    public static final int BLEED = 0;

    private static final String TAG = "flash_cut";
    private static final String PROGRESS = "flash_cut_progress";

    private SupermanCut() {
    }

    public static boolean humanoid(LivingEntity e) {
        return e instanceof Zombie || e instanceof AbstractSkeleton || e instanceof AbstractPiglin || e instanceof Player;
    }

    public static void onLaserHit(ServerPlayer player, LivingEntity target, Vec3 hit, boolean laser) {
        if (!SupermanPowers.laserCut(player) || target == player) {
            return;
        }
        CompoundTag data = target.getPersistentData();
        // the first hit already cuts (the beam kills fast), then one more limb every few hits, and the killing hit
        // always takes one
        int progress = data.getInt(PROGRESS) + 1;
        data.putInt(PROGRESS, progress);
        int every = laser ? 2 : 3;
        if (progress != 1 && progress % every != 0 && target.isAlive()) {
            return;
        }
        if (!humanoid(target)) {
            send(target, 0, BLEED, hit);
            return;
        }
        int mask = data.getInt(TAG);
        boolean boss = target.getType().is(net.minecraftforge.common.Tags.EntityTypes.BOSSES);
        int limb = pick(target, hit, mask, boss);
        if (limb == 0) {
            send(target, mask, BLEED, hit);
            return;
        }
        mask |= limb;
        data.putInt(TAG, mask);
        send(target, mask, limb, hit);
        target.level().playSound(null, hit.x, hit.y, hit.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS,
                1.0F, 0.6F);
        apply(player, target, mask, limb);
    }

    /** Limb where the beam hits: head high up, arms in the middle, legs below, the side from the hit. */
    private static int pick(LivingEntity target, Vec3 hit, int mask, boolean boss) {
        double h = (hit.y - target.getY()) / Math.max(0.1D, target.getBbHeight());
        double yaw = Math.toRadians(target.yBodyRot);
        // the entity's right side
        double rx = -Math.cos(yaw);
        double rz = -Math.sin(yaw);
        boolean right = (hit.x - target.getX()) * rx + (hit.z - target.getZ()) * rz >= 0.0D;
        int[] order;
        if (h > 0.78D) {
            order = new int[] {HEAD, right ? RIGHT_ARM : LEFT_ARM, right ? LEFT_ARM : RIGHT_ARM};
        } else if (h > 0.4D) {
            order = new int[] {right ? RIGHT_ARM : LEFT_ARM, right ? LEFT_ARM : RIGHT_ARM, HEAD};
        } else {
            order = new int[] {right ? RIGHT_LEG : LEFT_LEG, right ? LEFT_LEG : RIGHT_LEG,
                    right ? RIGHT_ARM : LEFT_ARM};
        }
        for (int limb : order) {
            if ((mask & limb) == 0 && !(boss && limb == HEAD)) {
                return limb;
            }
        }
        return 0;
    }

    private static void apply(ServerPlayer player, LivingEntity target, int mask, int limb) {
        int legs = Integer.bitCount(mask & (RIGHT_LEG | LEFT_LEG));
        int arms = Integer.bitCount(mask & (RIGHT_ARM | LEFT_ARM));
        int forever = 20 * 60 * 30;
        if (legs > 0) {
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, forever, legs == 2 ? 4 : 1, false,
                    false));
        }
        if (arms > 0) {
            target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, forever, arms - 1, false, false));
        }
        if (limb == HEAD && target.isAlive()) {
            target.invulnerableTime = 0;
            target.hurt(target.level().damageSources().playerAttack(player), Float.MAX_VALUE);
        }
    }

    private static void send(LivingEntity target, int mask, int limb, Vec3 hit) {
        FlashNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> target),
                new SupermanCutPacket(target.getId(), mask, limb, (float) hit.x, (float) hit.y, (float) hit.z));
    }
}
