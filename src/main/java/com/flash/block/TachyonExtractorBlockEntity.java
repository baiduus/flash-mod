package com.flash.block;

import com.flash.registry.ModBlockEntities;
import com.flash.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * State of a Tachyon Extractor: the input (a Torn Suit, or Nether fungi), the
 * result waiting to be taken and the extraction progress. Progress only moves
 * while the block is powered; cutting the redstone pauses it and the value is
 * saved with the world.
 */
public class TachyonExtractorBlockEntity extends BlockEntity {

    public static final int TOTAL_TICKS = 200;
    /** Nether fungi needed for one corrupted matter. */
    public static final int FUNGI_PER_MATTER = 4;
    private static final int HUM_INTERVAL = 40;

    private ItemStack input = ItemStack.EMPTY;
    private ItemStack output = ItemStack.EMPTY;
    private int progress;

    // client-only animation state
    public int animTicks;
    public int clientProgress;

    public TachyonExtractorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TACHYON_EXTRACTOR.get(), pos, state);
    }

    // ------------------------------------------------------------------
    // Item rules
    // ------------------------------------------------------------------

    static boolean isFungus(ItemStack stack) {
        return stack.is(Items.NETHER_WART) || stack.is(Items.CRIMSON_FUNGUS)
                || stack.is(Items.WARPED_FUNGUS);
    }

    private boolean hasValidInput() {
        if (this.input.isEmpty()) {
            return false;
        }
        if (TachyonExtractorBlock.isSuit(this.input)) {
            return true;
        }
        return isFungus(this.input) && this.input.getCount() >= FUNGI_PER_MATTER;
    }

    private boolean outputHasRoomFor() {
        if (this.output.isEmpty()) {
            return true;
        }
        if (TachyonExtractorBlock.isSuit(this.input)) {
            return this.output.is(ModItems.TACHYON.get())
                    && this.output.getCount() + 3 <= this.output.getMaxStackSize();
        }
        return this.output.is(ModItems.CORRUPTED_MATTER.get())
                && this.output.getCount() + 1 <= this.output.getMaxStackSize();
    }

    boolean canProcess() {
        return hasValidInput() && outputHasRoomFor();
    }

    // ------------------------------------------------------------------
    // Interaction (server)
    // ------------------------------------------------------------------

    void insert(Player player, ItemStack held) {
        if (TachyonExtractorBlock.isSuit(held)) {
            if (!this.input.isEmpty()) {
                player.displayClientMessage(TachyonExtractorBlock.msg("message.flash.extractor_busy"), true);
                return;
            }
            this.input = held.split(1);
        } else if (isFungus(held)) {
            if (!this.input.isEmpty() && !ItemStack.isSameItem(this.input, held)) {
                player.displayClientMessage(TachyonExtractorBlock.msg("message.flash.extractor_busy"), true);
                return;
            }
            if (!this.input.isEmpty() && this.input.getCount() >= this.input.getMaxStackSize()) {
                player.displayClientMessage(TachyonExtractorBlock.msg("message.flash.extractor_busy"), true);
                return;
            }
            int room = this.input.isEmpty() ? held.getMaxStackSize()
                    : this.input.getMaxStackSize() - this.input.getCount();
            int moved = Math.min(room, held.getCount());
            ItemStack part = held.split(moved);
            if (this.input.isEmpty()) {
                this.input = part;
            } else {
                this.input.grow(part.getCount());
            }
            if (this.input.getCount() < FUNGI_PER_MATTER) {
                player.displayClientMessage(TachyonExtractorBlock.msg("message.flash.extractor_need"), true);
            }
        } else {
            player.displayClientMessage(TachyonExtractorBlock.msg("message.flash.extractor_wrong"), true);
            return;
        }
        this.level.playSound(null, this.worldPosition, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.5F, 1.3F);
        syncToClients();
    }

    void takeResult(Player player) {
        if (this.output.isEmpty()) {
            if (!this.input.isEmpty() && !getBlockState().getValue(TachyonExtractorBlock.POWERED)) {
                player.displayClientMessage(TachyonExtractorBlock.msg("message.flash.extractor_no_power"), true);
            }
            return;
        }
        player.getInventory().placeItemBackInInventory(this.output);
        this.output = ItemStack.EMPTY;
        this.level.playSound(null, this.worldPosition, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.5F, 0.9F);
        syncToClients();
    }

    /** Shift + empty hand: take the input back (progress is lost). */
    void returnInput(Player player) {
        if (this.input.isEmpty()) {
            takeResult(player);
            return;
        }
        player.getInventory().placeItemBackInInventory(this.input);
        this.input = ItemStack.EMPTY;
        this.progress = 0;
        this.level.playSound(null, this.worldPosition, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.5F, 0.9F);
        syncToClients();
    }

    void dropContents(Level level, BlockPos pos) {
        Containers.dropItemStack(level, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, this.input);
        Containers.dropItemStack(level, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, this.output);
        this.input = ItemStack.EMPTY;
        this.output = ItemStack.EMPTY;
    }

    /** 0..15 for comparators. */
    int comparatorSignal() {
        return Math.min(15, this.progress * 15 / TOTAL_TICKS);
    }

    // ------------------------------------------------------------------
    // Ticking
    // ------------------------------------------------------------------

    public static void serverTick(Level level, BlockPos pos, BlockState state,
                                  TachyonExtractorBlockEntity extractor) {
        boolean powered = state.getValue(TachyonExtractorBlock.POWERED);
        if (extractor.input.isEmpty() && extractor.progress != 0) {
            extractor.progress = 0;
            extractor.syncToClients();
        }
        boolean working = powered && extractor.canProcess();

        if (working) {
            extractor.progress++;
            if (extractor.progress % HUM_INTERVAL == 1) {
                level.playSound(null, pos, SoundEvents.BEACON_AMBIENT, SoundSource.BLOCKS, 0.4F, 1.5F);
            }
            if (extractor.progress >= TOTAL_TICKS) {
                extractor.finish(level, pos);
            } else if (extractor.progress % 10 == 0) {
                extractor.syncToClients();
            }
            extractor.setChanged();
        }
        if (state.getValue(TachyonExtractorBlock.WORKING) != working) {
            level.setBlock(pos, state.setValue(TachyonExtractorBlock.WORKING, working),
                    net.minecraft.world.level.block.Block.UPDATE_ALL);
            extractor.syncToClients();
        }
    }


    public static void clientTick(Level level, BlockPos pos, BlockState state,
                                  TachyonExtractorBlockEntity extractor) {
        extractor.animTicks++;
        if (state.getValue(TachyonExtractorBlock.WORKING)
                && extractor.clientProgress < TOTAL_TICKS) {
            extractor.clientProgress++;
        }
    }

    private void finish(Level level, BlockPos pos) {
        if (TachyonExtractorBlock.isSuit(this.input)) {
            this.input.shrink(1);
            int count = 1 + level.random.nextInt(3);
            addOutput(ModItems.TACHYON.get().getDefaultInstance(), count);
        } else {
            this.input.shrink(FUNGI_PER_MATTER);
            addOutput(ModItems.CORRUPTED_MATTER.get().getDefaultInstance(), 1);
        }
        if (this.input.isEmpty()) {
            this.input = ItemStack.EMPTY;
        }
        this.progress = 0;
        level.playSound(null, pos, SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.BLOCKS, 0.6F, 2.0F);
        syncToClients();
    }

    private void addOutput(ItemStack stack, int count) {
        if (this.output.isEmpty()) {
            stack.setCount(count);
            this.output = stack;
        } else {
            this.output.grow(count);
        }
    }

    // ------------------------------------------------------------------
    // Client helpers
    // ------------------------------------------------------------------

    /** What the chamber shows: the input while there is one, otherwise the finished result. */
    public ItemStack displayStack() {
        return this.input.isEmpty() ? this.output : this.input;
    }

    public float progressFraction() {
        return Math.min(1.0F, this.clientProgress / (float) TOTAL_TICKS);
    }

    public boolean isDone() {
        return this.input.isEmpty() && !this.output.isEmpty();
    }

    void syncToClients() {
        this.setChanged();
        if (this.level != null) {
            BlockState state = getBlockState();
            this.level.sendBlockUpdated(this.worldPosition, state, state, 3);
        }
    }

    // ------------------------------------------------------------------
    // Persistence and network
    // ------------------------------------------------------------------

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("Input", this.input.save(new CompoundTag()));
        tag.put("Output", this.output.save(new CompoundTag()));
        tag.putInt("Progress", this.progress);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        this.input = ItemStack.of(tag.getCompound("Input"));
        this.output = ItemStack.of(tag.getCompound("Output"));
        this.progress = tag.getInt("Progress");
        this.clientProgress = this.progress;
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        saveAdditional(tag);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(this.worldPosition).inflate(0.5D);
    }
}
