package com.flash.menu;

import com.flash.block.PotionContainerBlockEntity;
import com.flash.registry.ModBlocks;
import com.flash.registry.ModMenus;
import com.flash.transformation.PotionRequirement;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Menu of the side potion container. It keeps the visual organisation of the
 * version 1 machine screen (three dedicated slots) but is attached to the
 * "Recipiente de Poções" block and shows whether it is connected to a chamber.
 */
public class PotionContainerMenu extends AbstractContainerMenu {

    public static final int MACHINE_SLOTS = 3;
    private static final int PLAYER_INVENTORY_START = MACHINE_SLOTS;
    private static final int PLAYER_INVENTORY_END = MACHINE_SLOTS + 36;
    private static final int DATA_CONNECTED = 0;

    private final ContainerData data;
    private final ContainerLevelAccess access;

    public PotionContainerMenu(int id, Inventory playerInventory, BlockPos pos) {
        this(
                id,
                playerInventory,
                resolveContainer(playerInventory, pos),
                resolveData(playerInventory, pos),
                ContainerLevelAccess.create(playerInventory.player.level(), pos)
        );
    }

    private PotionContainerMenu(int id, Inventory playerInventory, Container container,
                                ContainerData data, ContainerLevelAccess access) {
        super(ModMenus.POTION_CONTAINER.get(), id);
        this.data = data;
        this.access = access;
        this.addDataSlots(data);

        for (PotionRequirement requirement : PotionRequirement.values()) {
            final PotionRequirement required = requirement;
            this.addSlot(new Slot(container, requirement.slot(), 62 + requirement.slot() * 18, 35) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return required.matches(stack);
                }

                @Override
                public int getMaxStackSize() {
                    return 1;
                }
            });
        }

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                this.addSlot(new Slot(playerInventory, column + row * 9 + 9, 8 + column * 18, 84 + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            this.addSlot(new Slot(playerInventory, column, 8 + column * 18, 142));
        }
    }

    private static Container resolveContainer(Inventory inventory, BlockPos pos) {
        BlockEntity blockEntity = inventory.player.level().getBlockEntity(pos);
        if (blockEntity instanceof PotionContainerBlockEntity container) {
            return container;
        }
        return new SimpleContainer(PotionContainerBlockEntity.SLOT_COUNT);
    }

    private static ContainerData resolveData(Inventory inventory, BlockPos pos) {
        BlockEntity blockEntity = inventory.player.level().getBlockEntity(pos);
        if (blockEntity instanceof PotionContainerBlockEntity container) {
            return container.getDataAccess();
        }
        return new SimpleContainerData(1);
    }

    public boolean isConnected() {
        return this.data.get(DATA_CONNECTED) != 0;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }

        ItemStack stack = slot.getItem();
        ItemStack result = stack.copy();

        if (index < MACHINE_SLOTS) {
            if (!this.moveItemStackTo(stack, PLAYER_INVENTORY_START, PLAYER_INVENTORY_END, true)) {
                return ItemStack.EMPTY;
            }
        } else {
            boolean moved = false;
            for (PotionRequirement requirement : PotionRequirement.values()) {
                if (!requirement.matches(stack)) {
                    continue;
                }
                Slot target = this.slots.get(requirement.slot());
                if (!target.hasItem()
                        && this.moveItemStackTo(stack, requirement.slot(), requirement.slot() + 1, false)) {
                    moved = true;
                    break;
                }
            }
            if (!moved) {
                return ItemStack.EMPTY;
            }
        }

        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }

        if (stack.getCount() == result.getCount()) {
            return ItemStack.EMPTY;
        }
        slot.onTake(player, stack);
        return result;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(this.access, player, ModBlocks.POTION_CONTAINER.get());
    }
}
