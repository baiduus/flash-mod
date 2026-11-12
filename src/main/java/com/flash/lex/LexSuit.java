package com.flash.lex;

import com.flash.FlashMod;
import com.flash.entity.FlashSuitStand;
import com.flash.entity.LexMissile;
import com.flash.item.LexArmorItem;
import com.flash.power.HeroBalance;
import com.flash.registry.ModSounds;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

/**
 * Lex Luthor's war suit, server side. With the four pieces on (and no powers of his own: it is a human's machine)
 * the pilot gets the powers of Superman at level 5, toned down, all paid from one battery in the chestplate:
 * flight (double jump), strength, armour, the Kryptonite beam from the palm (held) and the shoulder missiles
 * (press). The battery lasts about 15 minutes of normal use (flying costs more); at 0 % everything stops until it
 * is charged again on the armour stand fed with redstone (2 % a second, full in 50 s).
 */
@Mod.EventBusSubscriber(modid = FlashMod.MODID)
public final class LexSuit {

    /** 100 % in 15 minutes of normal use. */
    public static final float DRAIN_PER_TICK = LexArmorItem.MAX_ENERGY / (15.0F * 60.0F * 20.0F);
    /** 2 % a second on a powered stand. */
    public static final float CHARGE_PER_TICK = 2.0F / 20.0F;
    public static final float MISSILE_COST = 3.0F;
    public static final float BEAM_COST_PER_TICK = 0.04F;
    private static final int MISSILE_COOLDOWN = 30;

    private static final UUID DAMAGE_ID = UUID.fromString("6c1d2e30-9a4b-4f0e-8a11-4c4c55a7e001");
    private static final UUID SPEED_ID = UUID.fromString("6c1d2e30-9a4b-4f0e-8a11-4c4c55a7e002");
    /** Superman's punch at level 5 (about 26), the suit gives 60 % of it. */
    private static final double PUNCH_BONUS = 15.0D;

    private static final Set<UUID> FLYERS = new HashSet<>();
    private static final Set<UUID> BEAMING = new HashSet<>();
    private static final Map<UUID, Long> LAST_MISSILE = new HashMap<>();
    private static final Map<UUID, Float> PENDING = new HashMap<>();
    private static final DustParticleOptions BEAM_DUST = new DustParticleOptions(new Vector3f(0.45F, 1.0F, 0.4F), 1.2F);

    private LexSuit() {
    }

    /** The suit works for this player right now: all four pieces, no hero powers, charge left. */
    public static boolean active(Player player) {
        return LexArmorItem.wearingFull(player) && !HeroBalance.isHero(player)
                && LexArmorItem.energy(player.getItemBySlot(EquipmentSlot.CHEST)) > 0.0F;
    }

    /** Ability power of the suit: Superman's at level 5, 60 % of it (12 = one of its punches). */
    public static float power() {
        return (float) ((1.0D + PUNCH_BONUS) / 12.0D);
    }

    // ------------------------------------------------------------------
    // Keys (LexKeyPacket)
    // ------------------------------------------------------------------

    public static final int KEY_MISSILES = 0;
    public static final int KEY_BEAM = 1;

    public static void key(ServerPlayer player, int key, boolean pressed) {
        if (!LexArmorItem.wearingFull(player) || HeroBalance.isHero(player)) {
            return;
        }
        if (!active(player)) {
            if (pressed) {
                player.displayClientMessage(Component.translatable("message.flash.lex_no_energy"), true);
            }
            BEAMING.remove(player.getUUID());
            return;
        }
        if (key == KEY_BEAM) {
            if (pressed) {
                BEAMING.add(player.getUUID());
                com.flash.superman.SupermanAbilities.anim(player, "lex_beam");
            } else if (BEAMING.remove(player.getUUID())) {
                com.flash.superman.SupermanAbilities.anim(player, "none");
            }
            return;
        }
        if (key == KEY_MISSILES && pressed) {
            long now = player.level().getGameTime();
            Long last = LAST_MISSILE.get(player.getUUID());
            if (last != null && now - last < MISSILE_COOLDOWN) {
                return;
            }
            LAST_MISSILE.put(player.getUUID(), now);
            missiles(player);
        }
    }

    private static void missiles(ServerPlayer player) {
        ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
        float e = LexArmorItem.energy(chest);
        if (e < MISSILE_COST) {
            player.displayClientMessage(Component.translatable("message.flash.lex_no_energy"), true);
            return;
        }
        LexArmorItem.setEnergy(chest, e - MISSILE_COST);
        ServerLevel level = player.serverLevel();
        HitResult hit = com.flash.superman.SupermanAbilities.trace(player, 64.0D);
        Entity target = hit instanceof EntityHitResult eh ? eh.getEntity() : null;
        Vec3 look = player.getLookAngle();
        Vec3 side = new Vec3(-look.z, 0.0D, look.x).normalize();
        com.flash.superman.SupermanAbilities.anim(player, "lex_missile_fire");
        level.playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.LEX_MISSILE_LAUNCH.get(),
                SoundSource.PLAYERS, 1.2F, 1.0F);
        for (int i = 0; i < 2; i++) {
            // one from each shoulder pod
            Vec3 from = player.getEyePosition().add(side.scale(i == 0 ? 0.45D : -0.45D)).add(0.0D, -0.2D, 0.0D)
                    .add(look.scale(0.4D));
            Vec3 dir = look.add(0.0D, 0.06D, 0.0D).add(side.scale(i == 0 ? 0.08D : -0.08D));
            level.addFreshEntity(new LexMissile(level, player, from, dir, target, 6.0F * power()));
            level.sendParticles(ParticleTypes.CLOUD, from.x, from.y, from.z, 4, 0.05D, 0.05D, 0.05D, 0.02D);
        }
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
        boolean pilot = full && !HeroBalance.isHero(player);
        ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
        float energy = full ? LexArmorItem.energy(chest) : 0.0F;
        boolean on = pilot && energy > 0.0F;
        attributes(player, on);
        flight(player, on);
        if (!on) {
            if (BEAMING.remove(player.getUUID())) {
                com.flash.superman.SupermanAbilities.anim(player, "none");
            }
            return;
        }
        boolean flying = player.getAbilities().flying;
        float drain = DRAIN_PER_TICK * (flying ? 1.8F : 1.0F);
        if (BEAMING.contains(player.getUUID())) {
            drain += BEAM_COST_PER_TICK;
            beam(player);
        }
        if (flying && player.tickCount % 2 == 0) {
            ServerLevel level = player.serverLevel();
            level.sendParticles(ParticleTypes.SMALL_FLAME, player.getX(), player.getY() - 0.1D, player.getZ(), 2, 0.12D,
                    0.05D, 0.12D, 0.0D);
            level.sendParticles(ParticleTypes.SMOKE, player.getX(), player.getY() - 0.2D, player.getZ(), 1, 0.1D, 0.05D,
                    0.1D, 0.0D);
        }
        // the battery (written to the item in small steps, not every tick)
        float pending = PENDING.getOrDefault(player.getUUID(), 0.0F) + drain;
        if (pending >= 0.05F) {
            float after = Math.max(0.0F, energy - pending);
            LexArmorItem.setEnergy(chest, after);
            warnings(player, energy, after);
            pending = 0.0F;
        }
        PENDING.put(player.getUUID(), pending);
    }

    private static void warnings(ServerPlayer player, float before, float after) {
        for (float mark : new float[] {20.0F, 5.0F}) {
            if (before > mark && after <= mark) {
                player.level().playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.LEX_LOW_ENERGY.get(),
                        SoundSource.PLAYERS, 1.0F, 1.0F);
                player.displayClientMessage(Component.translatable("message.flash.lex_low", Math.round(mark)), true);
            }
        }
        if (before > 0.0F && after <= 0.0F) {
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.LEX_EMPTY.get(),
                    SoundSource.PLAYERS, 1.0F, 1.0F);
            player.displayClientMessage(Component.translatable("message.flash.lex_empty"), false);
        }
    }

    /** The Kryptonite beam from the palm: a pulse every 4 ticks (60 % of Superman's heat vision at level 5). */
    private static void beam(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        HitResult hit = com.flash.superman.SupermanAbilities.trace(player, 32.0D);
        Vec3 from = player.getEyePosition().add(player.getLookAngle().scale(0.6D)).add(0.0D, -0.35D, 0.0D);
        Vec3 to = hit.getLocation();
        Vec3 d = to.subtract(from);
        int steps = (int) Math.min(40, d.length() * 2.0D);
        for (int i = 0; i < steps; i++) {
            Vec3 p = from.add(d.scale(i / (double) steps));
            level.sendParticles(BEAM_DUST, p.x, p.y, p.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, to.x, to.y, to.z, 3, 0.1D, 0.1D, 0.1D, 0.05D);
        if (player.tickCount % 4 == 0 && hit instanceof EntityHitResult eh && eh.getEntity() instanceof LivingEntity t) {
            t.invulnerableTime = 0;
            t.hurt(level.damageSources().playerAttack(player), 0.9F * power());
        }
    }

    private static void attributes(ServerPlayer player, boolean on) {
        set(player, Attributes.ATTACK_DAMAGE, DAMAGE_ID, on ? PUNCH_BONUS : 0.0D, AttributeModifier.Operation.ADDITION);
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

    /** Double jump flies (as Superman), only while the battery lasts. */
    private static void flight(ServerPlayer player, boolean on) {
        var ab = player.getAbilities();
        boolean creative = player.isCreative() || player.isSpectator();
        if (on) {
            if (!ab.mayfly || FLYERS.add(player.getUUID())) {
                ab.mayfly = true;
                if (!creative) {
                    ab.setFlyingSpeed(0.07F);
                }
                player.onUpdateAbilities();
                FLYERS.add(player.getUUID());
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
            // written every half second (+1 %): the stand sends the new charge to the clients
            LexArmorItem.setEnergy(chest, e + CHARGE_PER_TICK * 10.0F);
            if (level instanceof ServerLevel sl) {
                sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, stand.getX(), stand.getY() + 1.2D, stand.getZ(), 3, 0.25D,
                        0.4D, 0.25D, 0.02D);
            }
            if (e + CHARGE_PER_TICK * 10.0F >= LexArmorItem.MAX_ENERGY) {
                level.playSound(null, stand.getX(), stand.getY(), stand.getZ(), ModSounds.LEX_EQUIP.get(),
                        SoundSource.BLOCKS, 0.8F, 1.3F);
            }
        }
    }
}
