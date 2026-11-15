package com.flash.wonder;

import com.flash.FlashMod;
import com.flash.registry.ModItems;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Wonder Woman's sword and shield, conjured into her hands in a flash of gold (the conjure key, C) and sent away
 * again with the same key. They exist only in her hands: let go of (thrown, left at her death, her power gone) they
 * vanish.
 */
@Mod.EventBusSubscriber(modid = FlashMod.MODID)
public final class WonderArms {

    private WonderArms() {
    }

    static boolean conjured(ItemStack s) {
        return s.is(ModItems.AMAZON_SWORD.get()) || s.is(ModItems.AMAZON_SHIELD.get());
    }

    private static boolean holdsAny(Player p) {
        Inventory inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (conjured(inv.getItem(i))) {
                return true;
            }
        }
        return false;
    }

    private static void clear(Player p) {
        Inventory inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (conjured(inv.getItem(i))) {
                inv.setItem(i, ItemStack.EMPTY);
            }
        }
    }

    /** The key: the sword and shield in her hands, or sent away. */
    public static void toggle(ServerPlayer p) {
        if (!WonderPowers.has(p)) {
            return;
        }
        ServerLevel level = p.serverLevel();
        if (holdsAny(p)) {
            clear(p);
            level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ARMOR_EQUIP_GOLD, SoundSource.PLAYERS,
                    1.0F, 0.7F);
            level.sendParticles(ParticleTypes.END_ROD, p.getX(), p.getY() + 1.0D, p.getZ(), 15, 0.4D, 0.5D, 0.4D,
                    0.05D);
            return;
        }
        put(p, p.getInventory().selected, new ItemStack(ModItems.AMAZON_SWORD.get()));
        put(p, Inventory.SLOT_OFFHAND, new ItemStack(ModItems.AMAZON_SHIELD.get()));
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ARMOR_EQUIP_NETHERITE, SoundSource.PLAYERS,
                1.0F, 1.2F);
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS,
                1.5F, 0.8F);
        level.sendParticles(ParticleTypes.END_ROD, p.getX(), p.getY() + 1.0D, p.getZ(), 40, 0.5D, 0.7D, 0.5D, 0.08D);
        WonderAbilities.fx(p, WonderAbilities.FX_GUARD, p.position(), p.position(), 1.0F, -1);
    }

    /** Into that slot (the main hand's or the offhand's); what was there goes to the bag, or to the ground. */
    private static void put(ServerPlayer p, int slot, ItemStack arm) {
        Inventory inv = p.getInventory();
        ItemStack old = inv.getItem(slot);
        inv.setItem(slot, arm);
        if (!old.isEmpty() && !inv.add(old)) {
            p.drop(old, false);
        }
    }

    @SubscribeEvent
    public static void onToss(ItemTossEvent event) {
        if (conjured(event.getEntity().getItem())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onDrops(LivingDropsEvent event) {
        event.getDrops().removeIf(e -> conjured(e.getItem()));
    }

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.player instanceof ServerPlayer p && p.tickCount % 20 == 0
                && !WonderPowers.has(p) && holdsAny(p)) {
            clear(p);
        }
    }
}
