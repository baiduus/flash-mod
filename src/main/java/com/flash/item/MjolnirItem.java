package com.flash.item;

import com.flash.entity.MjolnirEntity;
import com.flash.thor.ThorAbilities;
import com.flash.thor.ThorPowers;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

/**
 * Mjolnir. Only the worthy (Thor) can use it: the unworthy can barely move with it in hand. Hold the right click and
 * let go to throw it (the longer, the harder); crouch while holding the right click and the hammer drinks lightning
 * (the charge of the hammer, shown on the HUD). It comes back with the summon key.
 */
public class MjolnirItem extends Item {

    private final Multimap<Attribute, AttributeModifier> modifiers;

    public MjolnirItem() {
        super(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant());
        this.modifiers = ImmutableMultimap.<Attribute, AttributeModifier>builder()
                .put(Attributes.ATTACK_DAMAGE, new AttributeModifier(BASE_ATTACK_DAMAGE_UUID, "mjolnir", 4.0D,
                        AttributeModifier.Operation.ADDITION))
                .put(Attributes.ATTACK_SPEED, new AttributeModifier(BASE_ATTACK_SPEED_UUID, "mjolnir", -2.6D,
                        AttributeModifier.Operation.ADDITION))
                .build();
    }

    @Override
    public Multimap<Attribute, AttributeModifier> getDefaultAttributeModifiers(EquipmentSlot slot) {
        return slot == EquipmentSlot.MAINHAND ? this.modifiers : super.getDefaultAttributeModifiers(slot);
    }

    @Override
    public void initializeClient(java.util.function.Consumer<net.minecraftforge.client.extensions.common.IClientItemExtensions> consumer) {
        consumer.accept(new net.minecraftforge.client.extensions.common.IClientItemExtensions() {
            @Override
            public net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return com.flash.client.MjolnirItemRenderer.get();
            }
        });
    }

    public static boolean worthy(Player player) {
        return ThorPowers.isThor(player) || com.flash.thor.ThorState.isThor(player);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!worthy(player)) {
            if (!level.isClientSide) {
                player.displayClientMessage(Component.translatable("message.flash.thor_unworthy"), true);
            }
            return InteractionResultHolder.fail(stack);
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return 72000;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.SPEAR;
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remaining) {
        if (!(entity instanceof ServerPlayer player) || !ThorPowers.isThor(player)) {
            return;
        }
        boolean charging = player.isShiftKeyDown();
        ThorPowers.setFlag(player, ThorPowers.F_CHARGING, charging);
        if (charging) {
            ThorAbilities.chargeTick(player, getUseDuration(stack) - remaining);
        }
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int remaining) {
        if (!(entity instanceof ServerPlayer player) || !ThorPowers.isThor(player)) {
            return;
        }
        boolean wasCharging = ThorPowers.flag(player, ThorPowers.F_CHARGING);
        ThorPowers.setFlag(player, ThorPowers.F_CHARGING, false);
        int held = getUseDuration(stack) - remaining;
        if (wasCharging || player.isShiftKeyDown() || held < 5) {
            return;
        }
        float force = Math.min(1.0F, held / 20.0F);
        ThorAbilities.throwHammer(player, stack, force);
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (attacker instanceof ServerPlayer player && ThorPowers.isThor(player)) {
            ThorAbilities.hammerHit(player, target);
        }
        return true;
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (level.isClientSide || !(entity instanceof Player player) || player.isCreative()) {
            return;
        }
        boolean inHand = selected || player.getOffhandItem() == stack;
        if (inHand && !worthy(player) && level.getGameTime() % 10L == 0L) {
            // whosoever holds this hammer, if he be worthy...
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 6, false, false, true));
            player.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 20, 4, false, false, true));
            if (level.getGameTime() % 60L == 0L) {
                player.displayClientMessage(Component.translatable("message.flash.thor_unworthy"), true);
            }
        }
        if (inHand && worthy(player) && level instanceof ServerLevel sl && level.getGameTime() % 80L == 0L
                && ThorPowers.charge(player) > 60.0F) {
            sl.playSound(null, player.getX(), player.getY(), player.getZ(),
                    com.flash.registry.ModSounds.THOR_HUM.get(), SoundSource.PLAYERS, 0.4F, 1.0F);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.flash.mjolnir").withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC));
        tooltip.add(Component.translatable("tooltip.flash.mjolnir.2").withStyle(ChatFormatting.GRAY));
    }

    /** Thrown hammers out in the world are linked to their owner (one at a time). */
    public static boolean isOut(ServerPlayer player) {
        return MjolnirEntity.find(player) != null;
    }
}
