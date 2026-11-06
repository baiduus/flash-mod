package com.flash.item;

import com.flash.registry.ModItems;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Shared helpers for the suit ring and the suit stand. */
public final class FlashSuitUtil {

    /** Tag put on pieces that were created by the ring (they vanish when taken off). */
    public static final String RING_TAG = "flash_ring_suit";
    private static final String STORED = "flash_stored_";

    public static final EquipmentSlot[] SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private FlashSuitUtil() {
    }

    public static boolean isSuit(ItemStack stack) {
        return stack.getItem() instanceof FlashSuitItem;
    }

    public static boolean isRingPiece(ItemStack stack) {
        return isSuit(stack) && stack.hasTag() && stack.getTag().getBoolean(RING_TAG);
    }

    /** Zoom suit piece (the ring gives it while the V-9 is running). */
    public static ItemStack zoomPiece(EquipmentSlot slot) {
        return new ItemStack(switch (slot) {
            case HEAD -> ModItems.ZOOM_SUIT_HELMET.get();
            case CHEST -> ModItems.ZOOM_SUIT_CHESTPLATE.get();
            case LEGS -> ModItems.ZOOM_SUIT_LEGGINGS.get();
            default -> ModItems.ZOOM_SUIT_BOOTS.get();
        });
    }

    /** Piece of any of the mod's suits. */
    public static ItemStack piece(EquipmentSlot slot, FlashSuitMaterial suit) {
        return switch (suit) {
            case NEGATIVE -> piece(slot, true);
            case ZOOM -> zoomPiece(slot);
            case PHANTOM -> new ItemStack(switch (slot) {
                case HEAD -> ModItems.PHANTOM_SUIT_HELMET.get();
                case CHEST -> ModItems.PHANTOM_SUIT_CHESTPLATE.get();
                case LEGS -> ModItems.PHANTOM_SUIT_LEGGINGS.get();
                default -> ModItems.PHANTOM_SUIT_BOOTS.get();
            });
            default -> piece(slot, false);
        };
    }

    public static ItemStack piece(EquipmentSlot slot, boolean negative) {
        return new ItemStack(switch (slot) {
            case HEAD -> negative ? ModItems.NEGATIVE_SUIT_HELMET.get() : ModItems.FLASH_SUIT_HELMET.get();
            case CHEST -> negative ? ModItems.NEGATIVE_SUIT_CHESTPLATE.get() : ModItems.FLASH_SUIT_CHESTPLATE.get();
            case LEGS -> negative ? ModItems.NEGATIVE_SUIT_LEGGINGS.get() : ModItems.FLASH_SUIT_LEGGINGS.get();
            default -> negative ? ModItems.NEGATIVE_SUIT_BOOTS.get() : ModItems.FLASH_SUIT_BOOTS.get();
        });
    }

    /** Gives the stack to the player, dropping it when the inventory is full. */
    public static void giveOrDrop(Player player, ItemStack stack) {
        if (!stack.isEmpty() && !player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    /** Ring: puts the whole suit on the player, keeping the clothes it replaces inside the ring. */
    public static void deployFromRing(Player player, ItemStack ring) {
        boolean negative = com.flash.power.FlashPowers.isNegative(player);
        boolean zoom = com.flash.power.FlashV9.isActive(player); // V-9 running: Zoom's black suit
        CompoundTag stored = ring.getOrCreateTag();
        for (EquipmentSlot slot : SLOTS) {
            ItemStack current = player.getItemBySlot(slot);
            if (!current.isEmpty() && !isSuit(current)) {
                if (stored.contains(STORED + slot.getName())) {
                    giveOrDrop(player, ItemStack.of(stored.getCompound(STORED + slot.getName())));
                }
                stored.put(STORED + slot.getName(), current.save(new CompoundTag()));
            }
            ItemStack suit = zoom ? zoomPiece(slot) : piece(slot, negative);
            suit.getOrCreateTag().putBoolean(RING_TAG, true);
            if (isSuit(current)) {
                continue; // already wearing a suit piece in this slot
            }
            player.setItemSlot(slot, suit);
        }
        effects(player);
    }

    /** Ring: removes the suit and returns the clothes that were stored. */
    public static void stowToRing(Player player, ItemStack ring) {
        CompoundTag stored = ring.getTag();
        for (EquipmentSlot slot : SLOTS) {
            ItemStack worn = player.getItemBySlot(slot);
            if (!isSuit(worn)) {
                continue;
            }
            boolean fromRing = isRingPiece(worn);
            player.setItemSlot(slot, ItemStack.EMPTY);
            if (!fromRing) {
                giveOrDrop(player, worn); // a crafted piece is never destroyed
            }
            String key = STORED + slot.getName();
            if (stored != null && stored.contains(key)) {
                ItemStack back = ItemStack.of(stored.getCompound(key));
                stored.remove(key);
                if (player.getItemBySlot(slot).isEmpty()) {
                    player.setItemSlot(slot, back);
                } else {
                    giveOrDrop(player, back);
                }
            }
        }
        effects(player);
    }

    private static void effects(Player player) {
        if (player.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, player.getX(), player.getY() + 1.0D,
                    player.getZ(), 40, 0.35D, 0.8D, 0.35D, 0.2D);
            level.playSound(null, player.blockPosition(), SoundEvents.ARMOR_EQUIP_NETHERITE,
                    SoundSource.PLAYERS, 1.0F, 1.3F);
            level.playSound(null, player.blockPosition(), SoundEvents.BEACON_DEACTIVATE,
                    SoundSource.PLAYERS, 0.5F, 2.0F);
        }
    }

    /** Stand: swaps suit pieces between the wearer and the stand. */
    public static boolean swapWithStand(Player player, LivingEntity stand) {
        boolean wearing = countSuit(player) > 0;
        boolean standHas = countSuit(stand) > 0;
        if (!wearing && !standHas) {
            return false;
        }
        for (EquipmentSlot slot : SLOTS) {
            ItemStack mine = player.getItemBySlot(slot);
            ItemStack theirs = stand.getItemBySlot(slot);
            if (wearing) {
                if (!isSuit(mine)) {
                    continue;
                }
                stand.setItemSlot(slot, mine);
                player.setItemSlot(slot, ItemStack.EMPTY);
                giveOrDrop(player, theirs); // whatever the stand held goes to the player
            } else {
                if (!isSuit(theirs)) {
                    continue;
                }
                player.setItemSlot(slot, theirs);
                stand.setItemSlot(slot, ItemStack.EMPTY);
                giveOrDrop(player, mine);   // the clothes replaced go to the inventory
            }
        }
        if (player.level() instanceof ServerLevel level) {
            level.playSound(null, stand.blockPosition(), SoundEvents.ARMOR_EQUIP_NETHERITE,
                    SoundSource.PLAYERS, 1.0F, 1.0F);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, stand.getX(), stand.getY() + 1.0D,
                    stand.getZ(), 25, 0.3D, 0.7D, 0.3D, 0.15D);
        }
        return true;
    }

    private static int countSuit(LivingEntity entity) {
        return FlashSuitItem.countWorn(entity);
    }
}
