package com.flash.item;

import com.flash.ability.FlashAbilityManager;
import com.flash.network.FlashSyncPacket;
import com.flash.network.FlashVisualSyncPacket;
import com.flash.power.FlashPowers;
import com.flash.power.FlashSpeed;
import com.flash.power.FlashStatusEffects;
import com.flash.power.FlashStep;
import com.flash.registry.ModItems;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;

/**
 * Registry of the substances a syringe can hold. To add a new one, add an
 * entry in the static block with a new {@code modelIndex} and a matching
 * override in {@code models/item/syringe.json}.
 */
public final class SyringeContents {

    private static final Map<String, SyringeContent> BY_ID = new LinkedHashMap<>();

    public static final SyringeContent NEGATIVE_FORCE = register(new SyringeContent(
            "negative_force", 1, ModItems.NEGATIVE_CAPSULE::get,
            SyringeContents::injectNegativeForce));

    private SyringeContents() {
    }

    private static SyringeContent register(SyringeContent content) {
        BY_ID.put(content.id(), content);
        return content;
    }

    public static SyringeContent byId(String id) {
        return BY_ID.get(id);
    }

    /** The content a capsule item carries, or null when the item is not a capsule. */
    public static SyringeContent byCapsule(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        for (SyringeContent content : BY_ID.values()) {
            if (stack.is(content.capsule().get())) {
                return content;
            }
        }
        return null;
    }

    /**
     * Negative Acceleration Force. A player without powers starts at level 1;
     * a Flash who already has the Positive force keeps the level reached.
     */
    private static boolean injectNegativeForce(ServerPlayer player) {
        if (FlashPowers.isNegative(player)) {
            player.displayClientMessage(
                    Component.translatable("message.flash.syringe_already"), true);
            return false;
        }
        FlashPowers.grantNegative(player);
        FlashSpeed.apply(player);
        FlashStep.apply(player);
        FlashStatusEffects.onLevelChanged(player);
        FlashAbilityManager.reset(player);
        FlashSyncPacket.send(player);
        FlashVisualSyncPacket.send(player);
        player.displayClientMessage(
                Component.translatable("message.flash.negative_granted"), false);
        player.displayClientMessage(Component.translatable(
                "message.flash.negative_level_kept", FlashPowers.getMaxLevel(player)), true);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 0.5F, 1.4F);
        return true;
    }
}
