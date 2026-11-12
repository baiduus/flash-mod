package com.flash.yas;

import com.flash.network.FlashNetwork;
import com.flash.network.YasDialogPacket;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.PacketDistributor;

/**
 * The Guardian of the Transcendence, at the centre of the floating island (YasIslandPiece): a robed figure of
 * light that floats over its altar and watches whoever comes. Right click: it asks whether he wishes to transcend
 * (YasDialogScreen); the answer goes to YasTranscendence. It cannot be hurt, pushed or made to leave.
 */
public class YasGuardian extends PathfinderMob {

    public YasGuardian(EntityType<? extends YasGuardian> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
        this.setPersistenceRequired();
        this.setCustomName(Component.translatable("entity.flash.yas_guardian"));
        this.setCustomNameVisible(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes().add(Attributes.MAX_HEALTH, 1000.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.0D).add(Attributes.KNOCKBACK_RESISTANCE, 1.0D);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new LookAtPlayerGoal(this, Player.class, 16.0F, 1.0F));
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        if (player instanceof ServerPlayer sp) {
            if (YasPowers.has(sp)) {
                sp.displayClientMessage(Component.translatable("message.flash.yas_already"), false);
            } else if (!YasTranscendence.running(sp)) {
                FlashNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp), new YasDialogPacket(this.getId()));
            }
        }
        return InteractionResult.sidedSuccess(this.level().isClientSide);
    }

    @Override
    public void tick() {
        super.tick();
        this.setDeltaMovement(this.getDeltaMovement().multiply(0.0D, 0.0D, 0.0D));
        if (this.level() instanceof ServerLevel level && this.tickCount % 6 == 0) {
            level.sendParticles(ParticleTypes.END_ROD, this.getX(), this.getY() + 1.0D, this.getZ(), 1, 0.4D, 0.7D,
                    0.4D, 0.01D);
        }
    }
}
