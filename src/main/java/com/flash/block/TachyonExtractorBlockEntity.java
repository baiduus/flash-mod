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
    // Recipes: the Processor works the materials of every hero
    // ------------------------------------------------------------------

    /** What goes in (how many at once) and what comes out. */
    private record Recipe(java.util.function.Predicate<ItemStack> input, int amount,
                          java.util.function.Supplier<net.minecraft.world.item.Item> output, int min, int max,
                          boolean single) {
        boolean accepts(ItemStack stack) {
            return !stack.isEmpty() && this.input.test(stack);
        }
    }

    /** Kryptonian cells needed for one capsule of Kryptonian blood. */
    public static final int CELLS_PER_BLOOD = 5;

    private static final java.util.List<Recipe> RECIPES = java.util.List.of(
            // Flash: a Torn Suit gives 1-3 tachyons; 4 Nether fungi give corrupted matter
            new Recipe(TachyonExtractorBlock::isSuit, 1, ModItems.TACHYON, 1, 3, true),
            new Recipe(TachyonExtractorBlockEntity::isFungus, FUNGI_PER_MATTER, ModItems.CORRUPTED_MATTER, 1, 1, false),
            // Superman: the 5 Kryptonian cells of a crashed ship give the Kryptonian blood
            new Recipe(s -> s.is(ModItems.KRYPTONIAN_CELL.get()), CELLS_PER_BLOOD, ModItems.KRYPTONIAN_BLOOD_CAPSULE,
                    1, 1, false),
            // Hulk: the gamma essence turns red (the Red Hulk), and the red one back to green, in 10 s of
            // deep blue, neon purple, magenta and white lightning
            new Recipe(s -> s.is(ModItems.HULK_ESSENCE_CAPSULE.get()), 1, ModItems.RED_HULK_ESSENCE_CAPSULE, 1, 1,
                    true),
            new Recipe(s -> s.is(ModItems.RED_HULK_ESSENCE_CAPSULE.get()), 1, ModItems.HULK_ESSENCE_CAPSULE, 1, 1,
                    true));

    /** The lightning of the gamma work: deep blue, neon purple, magenta, bright white. */
    public static final int[] GAMMA_COLORS = {0x1A2BFF, 0xA21CFF, 0xFF1FD2, 0xF4F0FF};

    /** Working a gamma essence (the Red Hulk recipe): the lightning effects. */
    public boolean isGammaWork() {
        return this.input.is(ModItems.HULK_ESSENCE_CAPSULE.get())
                || this.input.is(ModItems.RED_HULK_ESSENCE_CAPSULE.get());
    }

    static boolean isFungus(ItemStack stack) {
        return stack.is(Items.NETHER_WART) || stack.is(Items.CRIMSON_FUNGUS)
                || stack.is(Items.WARPED_FUNGUS);
    }

    private static Recipe recipeFor(ItemStack stack) {
        for (Recipe r : RECIPES) {
            if (r.accepts(stack)) {
                return r;
            }
        }
        return null;
    }

    /** Anything the Processor can work. */
    static boolean accepts(ItemStack stack) {
        return recipeFor(stack) != null;
    }

    private boolean hasValidInput() {
        Recipe r = recipeFor(this.input);
        return r != null && this.input.getCount() >= r.amount();
    }

    private boolean outputHasRoomFor() {
        if (this.output.isEmpty()) {
            return true;
        }
        Recipe r = recipeFor(this.input);
        return r != null && this.output.is(r.output().get())
                && this.output.getCount() + r.max() <= this.output.getMaxStackSize();
    }

    boolean canProcess() {
        return hasValidInput() && outputHasRoomFor();
    }

    // ------------------------------------------------------------------
    // Interaction (server)
    // ------------------------------------------------------------------

    void insert(Player player, ItemStack held) {
        Recipe r = recipeFor(held);
        if (r == null) {
            player.displayClientMessage(TachyonExtractorBlock.msg("message.flash.extractor_wrong"), true);
            return;
        }
        if (r.single()) {
            if (!this.input.isEmpty()) {
                player.displayClientMessage(TachyonExtractorBlock.msg("message.flash.extractor_busy"), true);
                return;
            }
            this.input = held.split(1);
        } else {
            if (!this.input.isEmpty() && (!ItemStack.isSameItem(this.input, held)
                    || this.input.getCount() >= this.input.getMaxStackSize())) {
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
            if (this.input.getCount() < r.amount()) {
                player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                        "message.flash.extractor_need", this.input.getCount(), r.amount(),
                        this.input.getHoverName()), true);
            }
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

        if (working && extractor.isGammaWork() && level instanceof net.minecraft.server.level.ServerLevel server) {
            gammaFx(server, pos, extractor.progress);
        }
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
        Recipe r = recipeFor(this.input);
        if (r == null) {
            this.progress = 0;
            return;
        }
        this.input.shrink(r.amount());
        int count = r.min() + (r.max() > r.min() ? level.random.nextInt(r.max() - r.min() + 1) : 0);
        addOutput(new ItemStack(r.output().get()), count);
        if (this.input.isEmpty()) {
            this.input = ItemStack.EMPTY;
        }
        this.progress = 0;
        level.playSound(null, pos, SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.BLOCKS, 0.6F, 2.0F);
        if (this.output.is(ModItems.RED_HULK_ESSENCE_CAPSULE.get()) || this.output.is(ModItems.HULK_ESSENCE_CAPSULE.get())) {
            if (level instanceof net.minecraft.server.level.ServerLevel server) {
                // the last discharge
                level.playSound(null, pos, SoundEvents.TRIDENT_THUNDER, SoundSource.BLOCKS, 0.8F, 1.4F);
                for (int c : GAMMA_COLORS) {
                    server.sendParticles(dust(c, 1.6F), pos.getX() + 0.5D, pos.getY() + 0.6D, pos.getZ() + 0.5D, 30,
                            0.6D, 0.6D, 0.6D, 0.05D);
                }
                server.sendParticles(net.minecraft.core.particles.ParticleTypes.FLASH, pos.getX() + 0.5D,
                        pos.getY() + 0.6D, pos.getZ() + 0.5D, 1, 0.0D, 0.0D, 0.0D, 0.0D);
            }
        }
        syncToClients();
    }

    private static net.minecraft.core.particles.DustParticleOptions dust(int rgb, float size) {
        return new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(((rgb >> 16) & 255) / 255.0F,
                ((rgb >> 8) & 255) / 255.0F, (rgb & 255) / 255.0F), size);
    }

    /** Sparks of the four colours around the chamber, crackling louder as the 10 s go by. */
    private static void gammaFx(net.minecraft.server.level.ServerLevel level, BlockPos pos, int progress) {
        double x = pos.getX() + 0.5D;
        double y = pos.getY() + 0.5D;
        double z = pos.getZ() + 0.5D;
        float t = progress / (float) TOTAL_TICKS;
        if (progress % 2 == 0) {
            int c = GAMMA_COLORS[level.random.nextInt(GAMMA_COLORS.length)];
            level.sendParticles(dust(c, 0.9F + t), x, y, z, 2 + (int) (t * 6), 0.45D, 0.4D, 0.45D, 0.02D);
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK, x, y, z,
                    1 + (int) (t * 4), 0.4D, 0.4D, 0.4D, 0.15D);
        }
        if (progress % 12 == 0) {
            level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 0.6F + t * 0.6F,
                    0.6F + t);
        }
        if (progress % 30 == 15) {
            level.playSound(null, pos, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.BLOCKS, 0.25F + t * 0.4F,
                    1.6F - t * 0.4F);
        }
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
        return new AABB(this.worldPosition).inflate(1.0D);
    }
}
