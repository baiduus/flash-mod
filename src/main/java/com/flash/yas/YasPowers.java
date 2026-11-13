package com.flash.yas;

import com.flash.FlashMod;
import com.flash.network.FlashNetwork;
import com.flash.network.YasSyncPacket;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

/**
 * Yas, the hero of the transcendence: the powers of Hand of God (YasHandOfGod), the body crossed by white and
 * yellow lightning, white eyes. Only got by transcending on the floating island (YasTranscendence) or by the
 * {@code /yas} command. The power is bound to whoever transcended: he can take it out with a syringe and put it
 * back, nobody else can take a Yas capsule (the mark {@link #TRANSCENDED} stays with him for good).
 */
@Mod.EventBusSubscriber(modid = FlashMod.MODID)
public final class YasPowers {

    /** The power itself (as the other heroes: a compound in the player data). */
    public static final String KEY = "flash_yas";
    /** Transcended once: the UUID of the player is the only owner of his Yas essence. */
    public static final String TRANSCENDED = "flash_yas_transcended";

    private YasPowers() {
    }

    public static boolean has(Player p) {
        return p.getPersistentData().contains(KEY);
    }

    public static boolean transcended(Player p) {
        return p.getPersistentData().getBoolean(TRANSCENDED);
    }

    /** Becomes Yas (the other powers go, one power at a time). */
    public static void grant(ServerPlayer p) {
        if (has(p)) {
            return;
        }
        clearOthers(p);
        CompoundTag tag = new CompoundTag();
        tag.putInt("level", 1);
        tag.putString("owner", p.getUUID().toString());
        p.getPersistentData().put(KEY, tag);
        p.getPersistentData().putBoolean(TRANSCENDED, true);
        YasHandOfGod.apply(p, true);
        sync(p);
        com.flash.power.HeroAdvancements.award(p, com.flash.power.HeroAdvancements.YAS);
    }

    public static void revoke(ServerPlayer p) {
        if (!has(p)) {
            return;
        }
        p.getPersistentData().remove(KEY);
        stopFlight(p);
        removeAttributes(p);
        YasHandOfGod.apply(p, false);
        sync(p);
    }

    /** The powers of the other heroes go (the same rule as theirs). */
    private static void clearOthers(ServerPlayer p) {
        for (com.flash.power.HeroPowers.Hero h : com.flash.power.HeroPowers.Hero.values()) {
            if (h != com.flash.power.HeroPowers.Hero.YAS && h.has(p)) {
                com.flash.power.HeroPowers.revokeOf(p, h);
            }
        }
        com.flash.scarlet.ScarletPowers.revoke(p);
    }

    public static void sync(ServerPlayer p) {
        YasSyncPacket packet = new YasSyncPacket(p.getId(), has(p), has(p) && p.getAbilities().flying,
                lightning(p), eyes(p));
        FlashNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> p), packet);
    }

    private static final java.util.Set<java.util.UUID> WAS_FLYING = new java.util.HashSet<>();

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer p)) {
            return;
        }
        YasHandOfGod.guard(p, has(p) || com.flash.scarlet.ScarletPowers.has(p));
        boolean yas = has(p);
        if (yas) {
            flight(p);
            if (p.tickCount % 20 == 0) {
                applyAttributes(p);
            }
            regeneration(p);
            // diving from Y = 200 or higher she burns like a meteor
            com.flash.superman.SupermanMeteor.tick(p, METEOR_POWER, true, false);
        }
        boolean flying = yas && p.getAbilities().flying;
        if (flying != WAS_FLYING.contains(p.getUUID())) {
            if (flying) {
                WAS_FLYING.add(p.getUUID());
            } else {
                WAS_FLYING.remove(p.getUUID());
            }
            sync(p);
        }
    }

    /**
     * Yas flies (double jump, as the other heroes), steered on the client by the mod's aimed flight
     * (SupermanClient), faster than Superman at level 10.
     */
    private static void flight(ServerPlayer p) {
        if (p.isCreative() || p.isSpectator()) {
            return;
        }
        var ab = p.getAbilities();
        boolean changed = false;
        if (!ab.mayfly) {
            ab.mayfly = true;
            changed = true;
        }
        if (ab.getFlyingSpeed() != 0.0F) {
            ab.setFlyingSpeed(0.0F);
            changed = true;
        }
        if (changed) {
            p.onUpdateAbilities();
        }
        p.fallDistance = 0.0F;
    }

    // ------------------------------------------------------------------
    // Her body: 5 times tougher than Superman at level 10, a harder punch
    // ------------------------------------------------------------------

    /** Damage taken: a fifth of what Superman at level 10 takes (SupermanPowers.damageFactor). */
    public static float damageFactor() {
        float old = 0.07F;
        float once = 0.15F + 0.85F * old;
        return (0.05F + 0.85F * once) * 0.4F / 5.0F;
    }

    /** Her meteor dive hits harder than Superman's at level 10 (3.7). */
    private static final float METEOR_POWER = 5.0F;

    /** The punch: half again Superman's at level 10 (43.2 extra). */
    public static final double PUNCH_BONUS = 43.2D * 1.5D;

    private static final java.util.UUID DAMAGE_ID = java.util.UUID.fromString("6f1b7a8e-3c0d-4d6f-9a42-1d5e7c0a9b11");
    private static final java.util.UUID HEALTH_ID = java.util.UUID.fromString("6f1b7a8e-3c0d-4d6f-9a42-1d5e7c0a9b12");
    private static final java.util.UUID KNOCKBACK_ID = java.util.UUID.fromString("6f1b7a8e-3c0d-4d6f-9a42-1d5e7c0a9b13");
    private static final java.util.UUID PUNCH_ID = java.util.UUID.fromString("6f1b7a8e-3c0d-4d6f-9a42-1d5e7c0a9b14");
    private static final java.util.UUID SPEED_ID = java.util.UUID.fromString("6f1b7a8e-3c0d-4d6f-9a42-1d5e7c0a9b15");
    private static final java.util.UUID STEP_ID = java.util.UUID.fromString("6f1b7a8e-3c0d-4d6f-9a42-1d5e7c0a9b16");

    private static void applyAttributes(ServerPlayer p) {
        var add = net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADDITION;
        set(p, net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE, DAMAGE_ID, PUNCH_BONUS, add);
        set(p, net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH, HEALTH_ID, 40.0D, add);
        set(p, net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK_ID, 1.0D, add);
        set(p, net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_KNOCKBACK, PUNCH_ID, 3.5D, add);
        set(p, net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED, SPEED_ID, 0.5D,
                net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.MULTIPLY_BASE);
        set(p, net.minecraftforge.common.ForgeMod.STEP_HEIGHT_ADDITION.get(), STEP_ID, 0.6D, add);
    }

    private static void set(ServerPlayer p, net.minecraft.world.entity.ai.attributes.Attribute attribute,
                            java.util.UUID id, double amount,
                            net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation op) {
        var instance = p.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        var current = instance.getModifier(id);
        if (current != null && Math.abs(current.getAmount() - amount) < 1.0E-6D) {
            return;
        }
        if (current != null) {
            instance.removeModifier(id);
        }
        instance.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(id, "yas",
                amount, op));
    }

    private static void removeAttributes(ServerPlayer p) {
        for (var pair : new Object[][] {
                {net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE, DAMAGE_ID},
                {net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH, HEALTH_ID},
                {net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK_ID},
                {net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_KNOCKBACK, PUNCH_ID},
                {net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED, SPEED_ID},
                {net.minecraftforge.common.ForgeMod.STEP_HEIGHT_ADDITION.get(), STEP_ID}}) {
            var instance = p.getAttribute((net.minecraft.world.entity.ai.attributes.Attribute) pair[0]);
            if (instance != null) {
                instance.removeModifier((java.util.UUID) pair[1]);
            }
        }
        if (p.getHealth() > p.getMaxHealth()) {
            p.setHealth(p.getMaxHealth());
        }
    }

    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOW)
    public static void onHurt(net.minecraftforge.event.entity.living.LivingHurtEvent event) {
        if (event.getEntity() instanceof ServerPlayer victim && has(victim)
                && !event.getSource().is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            event.setAmount(event.getAmount() * damageFactor());
        }
    }

    /**
     * The flight key tapped (Yas and the Scarlet Witch), as Thor's: flying stops; not flying, she takes off (the
     * server sets the flight and a lift, so the game does not switch it off on the ground).
     */
    public static void toggleFlight(ServerPlayer p) {
        var ab = p.getAbilities();
        if (ab.flying) {
            ab.flying = false;
            p.onUpdateAbilities();
            return;
        }
        ab.mayfly = true;
        ab.flying = true;
        ab.setFlyingSpeed(0.0F);
        p.onUpdateAbilities();
        p.fallDistance = 0.0F;
        p.setDeltaMovement(p.getDeltaMovement().add(0.0D, 0.6D, 0.0D));
        p.hurtMarked = true;
        com.flash.superman.SupermanAbilities.groundPuff(p.serverLevel(), p.position(), 1.5D, 16);
    }

    /**
     * The charged takeoff (flight key held on the ground): 0 coils, 1 shoots ~30 blocks up in a burst of pale
     * lightning (she flies at the top, SupermanClient), 2 cancels.
     */
    public static void launch(ServerPlayer p, int phase) {
        if (!has(p) || p.getAbilities().flying) {
            return;
        }
        if (phase == 0) {
            com.flash.superman.SupermanAbilities.anim(p, "charge_takeoff9");
            return;
        }
        if (phase != 1) {
            com.flash.superman.SupermanAbilities.anim(p, "none");
            return;
        }
        flight(p);
        // ~30 blocks (Superman and Thor: 2.0, ~20 blocks)
        p.setDeltaMovement(p.getDeltaMovement().x, 2.6D, p.getDeltaMovement().z);
        p.hurtMarked = true;
        var level = p.serverLevel();
        com.flash.superman.SupermanAbilities.cracks(level, p.position(), 4.0F);
        com.flash.superman.SupermanAbilities.anim(p, "charged_launch9");
        com.flash.superman.SupermanAbilities.groundPuff(level, p.position(), 3.0D, 40);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD, p.getX(), p.getY() + 0.5D, p.getZ(),
                40, 0.6D, 0.3D, 0.6D, 0.25D);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK, p.getX(), p.getY() + 0.5D,
                p.getZ(), 40, 0.8D, 0.3D, 0.8D, 0.4D);
        level.playSound(null, p.blockPosition(), net.minecraft.sounds.SoundEvents.LIGHTNING_BOLT_THUNDER,
                net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 1.6F);
    }

    /** Regeneration I always; near death (2 hearts or less) Regeneration III. */
    private static void regeneration(ServerPlayer p) {
        int amp = p.getHealth() <= 4.0F ? 2 : 0;
        var regen = p.getEffect(net.minecraft.world.effect.MobEffects.REGENERATION);
        // renewed while it still has a few seconds (renewing every tick would break the healing rhythm)
        if (regen == null || regen.getAmplifier() < amp || regen.getDuration() < 40) {
            p.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.REGENERATION, 120, amp, true, false, true));
        }
    }

    /** Yas never takes fall damage. */
    @SubscribeEvent
    public static void onFall(net.minecraftforge.event.entity.living.LivingFallEvent event) {
        if (event.getEntity() instanceof Player p && has(p)) {
            event.setCanceled(true);
        }
    }

    /** Back on the ground when the power goes. */
    private static void stopFlight(ServerPlayer p) {
        if (p.isCreative() || p.isSpectator()) {
            return;
        }
        var ab = p.getAbilities();
        ab.mayfly = false;
        ab.flying = false;
        ab.setFlyingSpeed(0.05F);
        p.onUpdateAbilities();
    }

    @SubscribeEvent
    public static void onServerStarted(net.minecraftforge.event.server.ServerStartedEvent event) {
        YasHandOfGod.enablePlayerTargets();
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            sync(p);
            if (!com.flash.scarlet.ScarletPowers.has(p)) {
                YasHandOfGod.apply(p, has(p));
            }
        }
    }

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof ServerPlayer target && event.getEntity() instanceof ServerPlayer viewer
                && has(target)) {
            FlashNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> viewer),
                    new YasSyncPacket(target.getId(), true, target.getAbilities().flying, lightning(target),
                            eyes(target)));
        }
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        CompoundTag old = event.getOriginal().getPersistentData();
        CompoundTag now = event.getEntity().getPersistentData();
        if (old.contains(KEY)) {
            now.put(KEY, old.getCompound(KEY).copy());
        }
        if (old.getBoolean(TRANSCENDED)) {
            now.putBoolean(TRANSCENDED, true);
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            sync(p);
        }
    }

    /** Her look, chosen in her K menu (seen by everyone): the lightning on the body, the glow of the eyes. */
    public static boolean lightning(Player p) {
        return !p.getPersistentData().getCompound(KEY).getBoolean("noLightning");
    }

    public static boolean eyes(Player p) {
        return !p.getPersistentData().getCompound(KEY).getBoolean("noEyes");
    }

    public static void setLook(ServerPlayer p, boolean lightning, boolean eyes) {
        if (!has(p)) {
            return;
        }
        CompoundTag tag = p.getPersistentData().getCompound(KEY);
        tag.putBoolean("noLightning", !lightning);
        tag.putBoolean("noEyes", !eyes);
        p.getPersistentData().put(KEY, tag);
        sync(p);
    }

    public static Component name() {
        return Component.translatable("hero.flash.yas");
    }
}
