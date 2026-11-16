package com.flash.infinity;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkHooks;

/**
 * The Infinity Gauntlet's menu (the original's GauntletMenu): six stone slots in the order space, mind, reality, power,
 * time, soul, though any stone fits any slot, one each; the player's inventory under them. Opened with the gauntlet's
 * key (default G) while it is worn, or by using it in hand.
 */
public class GauntletMenu extends AbstractContainerMenu {

    /** Slot corners on the 256x256 texture (textures/gui/infinity_gauntlet.png). */
    public static final int[][] SLOTS = {{120, 52}, {109, 82}, {99, 52}, {141, 52}, {169, 62}, {78, 52}};
    public static final int INV_X = 48;
    public static final int INV_Y = 153;

    private final Player player;
    private final InteractionHand hand;
    private final SimpleContainer stones = new SimpleContainer(InfinityGauntlet.SLOTS);
    private final int lockedSlot;
    private boolean loading;

    public GauntletMenu(int id, Inventory inv, FriendlyByteBuf buf) {
        this(id, inv, buf.readEnum(InteractionHand.class));
    }

    public GauntletMenu(int id, Inventory inv, InteractionHand hand) {
        super(com.flash.registry.ModMenus.INFINITY_GAUNTLET.get(), id);
        this.player = inv.player;
        this.hand = hand;
        this.lockedSlot = hand == InteractionHand.MAIN_HAND ? inv.selected : -1;
        ItemStack g = gauntlet();
        this.loading = true;
        InfinityStone[] in = InfinityGauntlet.slots(g);
        for (int i = 0; i < InfinityGauntlet.SLOTS; i++) {
            this.stones.setItem(i, in[i] == null ? ItemStack.EMPTY : new ItemStack(in[i].item()));
        }
        this.loading = false;
        this.stones.addListener(this::changed);
        for (int i = 0; i < InfinityGauntlet.SLOTS; i++) {
            this.addSlot(new Slot(this.stones, i, SLOTS[i][0], SLOTS[i][1]) {
                @Override
                public boolean mayPlace(ItemStack s) {
                    return InfinityStone.of(s) != null;
                }

                @Override
                public int getMaxStackSize() {
                    return 1;
                }
            });
        }
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 9; c++) {
                this.addSlot(new Slot(inv, c + r * 9 + 9, INV_X + c * 18, INV_Y + r * 18));
            }
        }
        for (int c = 0; c < 9; c++) {
            final int index = c;
            this.addSlot(new Slot(inv, c, INV_X + c * 18, INV_Y + 58) {
                @Override
                public boolean mayPickup(Player p) {
                    return index != GauntletMenu.this.lockedSlot;
                }

                @Override
                public boolean mayPlace(ItemStack s) {
                    return index != GauntletMenu.this.lockedSlot;
                }
            });
        }
    }

    private ItemStack gauntlet() {
        ItemStack s = this.player.getItemInHand(this.hand);
        return InfinityGauntlet.isGauntlet(s) ? s : ItemStack.EMPTY;
    }

    private void changed(Container c) {
        if (this.loading || this.player.level().isClientSide) {
            return;
        }
        ItemStack g = gauntlet();
        if (g.isEmpty()) {
            return;
        }
        int before = InfinityGauntlet.stones(g).size();
        InfinityStone[] out = new InfinityStone[InfinityGauntlet.SLOTS];
        for (int i = 0; i < InfinityGauntlet.SLOTS; i++) {
            out[i] = InfinityStone.of(this.stones.getItem(i));
        }
        InfinityGauntlet.setSlots(g, out);
        int after = InfinityGauntlet.stones(g).size();
        if (this.player instanceof ServerPlayer sp) {
            if (after > before) {
                InfinityGauntlet.inserted(sp, g);
            } else if (after < before) {
                SpaceStone.sound(sp.level(), sp.position(), "stone_remove", 1.0F, 1.0F);
            }
        }
    }

    @Override
    public ItemStack quickMoveStack(Player p, int index) {
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack s = slot.getItem();
        ItemStack copy = s.copy();
        int n = InfinityGauntlet.SLOTS;
        if (index < n) {
            if (!this.moveItemStackTo(s, n, this.slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else {
            if (InfinityStone.of(s) == null) {
                return ItemStack.EMPTY;
            }
            boolean moved = false;
            for (int i = 0; i < n && !s.isEmpty(); i++) {
                Slot t = this.slots.get(i);
                if (!t.hasItem()) {
                    t.set(s.split(1));
                    moved = true;
                }
            }
            if (!moved) {
                return ItemStack.EMPTY;
            }
        }
        if (s.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return copy;
    }

    @Override
    public boolean stillValid(Player p) {
        return !gauntlet().isEmpty();
    }

    @Override
    public void removed(Player p) {
        super.removed(p);
        if (p instanceof ServerPlayer sp && !gauntlet().isEmpty()) {
            changed(this.stones);
        } else if (p instanceof ServerPlayer sp) {
            // the gauntlet is gone (dropped while open): nothing is lost
            for (int i = 0; i < this.stones.getContainerSize(); i++) {
                ItemStack s = this.stones.removeItemNoUpdate(i);
                if (!s.isEmpty() && !sp.getInventory().add(s)) {
                    sp.drop(s, false);
                }
            }
        }
    }

    /** Opens the worn gauntlet's menu (the offhand first). */
    public static void open(ServerPlayer p) {
        InteractionHand hand = InfinityGauntlet.isGauntlet(p.getOffhandItem()) ? InteractionHand.OFF_HAND
                : InfinityGauntlet.isGauntlet(p.getMainHandItem()) ? InteractionHand.MAIN_HAND : null;
        if (hand != null) {
            open(p, hand);
        }
    }

    public static void open(ServerPlayer p, InteractionHand hand) {
        NetworkHooks.openScreen(p, new SimpleMenuProvider((id, inv, pl) -> new GauntletMenu(id, inv, hand),
                Component.translatable("item.flash.infinity_gauntlet")), buf -> buf.writeEnum(hand));
    }
}
