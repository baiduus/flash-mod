package com.flash.block;

import com.flash.menu.PotionContainerMenu;
import com.flash.registry.ModBlockEntities;
import com.flash.transformation.PotionRequirement;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The side "Recipiente de Poções": three strictly validated slots. It only
 * stores items; the chamber consumes them when a transformation succeeds.
 */
public class PotionContainerBlockEntity extends BlockEntity implements Container, MenuProvider {

    public static final int SLOT_COUNT = 3;
    private static final String TAG_ITEMS = "Items";

    private NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);

    private final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(int index) {
            return index == 0 && isConnectedToChamber() ? 1 : 0;
        }

        @Override
        public void set(int index, int value) {
            // Client-side display only.
        }

        @Override
        public int getCount() {
            return 1;
        }
    };

    public PotionContainerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.POTION_CONTAINER.get(), pos, state);
    }

    public ContainerData getDataAccess() {
        return this.dataAccess;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, PotionContainerBlockEntity container) {
        boolean filled = container.hasAllPotions();
        if (state.hasProperty(PotionContainerBlock.FILLED)
                && state.getValue(PotionContainerBlock.FILLED) != filled) {
            level.setBlock(pos, state.setValue(PotionContainerBlock.FILLED, filled), 3);
        }
    }

    public boolean isConnectedToChamber() {
        return this.level != null
                && TransformationChamberBlock.chamberNextToContainer(this.level, this.worldPosition) != null;
    }

    public List<PotionRequirement> missingRequirements() {
        return PotionRequirement.missing(this.items);
    }

    public boolean hasAllPotions() {
        return this.missingRequirements().isEmpty();
    }

    /**
     * Replaces each valid potion by an empty glass bottle. Called once, after
     * a successful transformation.
     */
    public void consumePotions() {
        for (PotionRequirement requirement : PotionRequirement.values()) {
            int slot = requirement.slot();
            if (slot >= this.items.size()) {
                continue;
            }
            ItemStack stack = this.items.get(slot);
            if (!requirement.matches(stack)) {
                continue;
            }
            if (stack.getCount() > 1) {
                stack.shrink(1);
            } else {
                this.items.set(slot, new ItemStack(Items.GLASS_BOTTLE));
            }
        }
        this.setChanged();
    }

    // ------------------------------------------------------------------
    // Container
    // ------------------------------------------------------------------

    @Override
    public int getContainerSize() {
        return SLOT_COUNT;
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack stack : this.items) {
            if (!stack.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        return slot >= 0 && slot < SLOT_COUNT ? this.items.get(slot) : ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        if (slot < 0 || slot >= SLOT_COUNT || amount <= 0) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = this.items.get(slot);
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        if (stack.getCount() <= amount) {
            this.items.set(slot, ItemStack.EMPTY);
            this.setChanged();
            return stack;
        }
        ItemStack split = stack.split(amount);
        this.setChanged();
        return split;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        if (slot < 0 || slot >= SLOT_COUNT) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = this.items.get(slot);
        this.items.set(slot, ItemStack.EMPTY);
        return stack;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (slot < 0 || slot >= SLOT_COUNT) {
            return;
        }
        this.items.set(slot, stack);
        if (stack.getCount() > this.getMaxStackSize()) {
            stack.setCount(this.getMaxStackSize());
        }
        this.setChanged();
    }

    @Override
    public boolean stillValid(Player player) {
        if (this.level == null || this.level.getBlockEntity(this.worldPosition) != this) {
            return false;
        }
        return player.distanceToSqr(
                this.worldPosition.getX() + 0.5D,
                this.worldPosition.getY() + 0.5D,
                this.worldPosition.getZ() + 0.5D
        ) <= 64.0D;
    }

    @Override
    public void clearContent() {
        for (int i = 0; i < this.items.size(); i++) {
            this.items.set(i, ItemStack.EMPTY);
        }
        this.setChanged();
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        PotionRequirement requirement = PotionRequirement.bySlot(slot);
        return requirement != null && requirement.matches(stack);
    }

    // ------------------------------------------------------------------
    // Persistence
    // ------------------------------------------------------------------

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        this.items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
        if (tag.contains(TAG_ITEMS)) {
            ContainerHelper.loadAllItems(tag, this.items);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        ContainerHelper.saveAllItems(tag, this.items);
    }

    // ------------------------------------------------------------------
    // MenuProvider
    // ------------------------------------------------------------------

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.flash.potion_container");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new PotionContainerMenu(id, inventory, this.worldPosition);
    }
}
