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

    /** Velocity-9: five extra levels for five minutes (see {@link com.flash.power.FlashV9}). */
    public static final SyringeContent V9 = register(new SyringeContent(
            "v9", 2, ModItems.V9_CAPSULE::get, com.flash.power.FlashV9::inject));

    /** Gamma radiation: the Hulk (removes the Acceleration Force). */
    public static final SyringeContent GAMMA = register(new SyringeContent(
            "gamma", 3, ModItems.GAMMA_CAPSULE::get, com.flash.hulk.HulkPowers::injectGamma));

    /** The power capsules: Superman, Hulk, Flash and Thor for good (see {@link com.flash.power.HeroPowers}). */
    public static final SyringeContent KRYPTONIAN_BLOOD = register(new SyringeContent(
            "kryptonian_blood", 4, ModItems.KRYPTONIAN_BLOOD_CAPSULE::get,
            p -> com.flash.power.HeroPowers.inject(p, com.flash.power.HeroPowers.Hero.SUPERMAN)));
    public static final SyringeContent HULK_ESSENCE = register(new SyringeContent(
            "hulk_essence", 5, ModItems.HULK_ESSENCE_CAPSULE::get, p -> injectHulk(p, false)));
    /** The Red Hulk (the green gamma turned in the Processor). */
    public static final SyringeContent RED_HULK_ESSENCE = register(new SyringeContent(
            "red_hulk_essence", 8, ModItems.RED_HULK_ESSENCE_CAPSULE::get, p -> injectHulk(p, true)));
    public static final SyringeContent FLASH_ESSENCE = register(new SyringeContent(
            "flash_essence", 6, ModItems.FLASH_ESSENCE_CAPSULE::get,
            p -> com.flash.power.HeroPowers.inject(p, com.flash.power.HeroPowers.Hero.FLASH)));
    public static final SyringeContent THOR_ESSENCE = register(new SyringeContent(
            "thor_essence", 7, ModItems.THOR_ESSENCE_CAPSULE::get, SyringeContents::injectThor));
    /** Yas (the transcendence): the syringe checks the owner of the essence before (SyringeItem). */
    public static final SyringeContent YAS_ESSENCE = register(new SyringeContent(
            "yas_essence", 9, ModItems.YAS_ESSENCE_CAPSULE::get,
            p -> com.flash.power.HeroPowers.inject(p, com.flash.power.HeroPowers.Hero.YAS)));

    /** The Scarlet Witch's Chaos. */
    public static final SyringeContent SCARLET_ESSENCE = register(new SyringeContent(
            "scarlet_essence", 10, ModItems.SCARLET_ESSENCE_CAPSULE::get,
            p -> com.flash.power.HeroPowers.inject(p, com.flash.power.HeroPowers.Hero.SCARLET)));
    /** Storm's power of the storms. */
    public static final SyringeContent STORM_ESSENCE = register(new SyringeContent(
            "storm_essence", 11, ModItems.STORM_ESSENCE_CAPSULE::get,
            p -> com.flash.power.HeroPowers.inject(p, com.flash.power.HeroPowers.Hero.STORM)));

    private static boolean injectHulk(net.minecraft.server.level.ServerPlayer p, boolean red) {
        if (!com.flash.power.HeroPowers.inject(p, com.flash.power.HeroPowers.Hero.HULK)) {
            return false;
        }
        com.flash.hulk.HulkPowers.setRed(p, red);
        return true;
    }

    /** Only the worthy hold the power of Thor (raid won and 100 hostile creatures: HeroAdvancements). */
    private static boolean injectThor(net.minecraft.server.level.ServerPlayer p) {
        if (!com.flash.power.HeroAdvancements.isWorthy(p)) {
            p.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.flash.not_worthy"),
                    true);
            return false;
        }
        return com.flash.power.HeroPowers.inject(p, com.flash.power.HeroPowers.Hero.THOR);
    }

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
