package com.flash.infinity;

import com.flash.FlashMod;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * The Temple Beacon (the original's temple/TempleBeacon): the beacon at the heart of an Infinity Temple. It always
 * shines, with no pyramid; glass above tints its beam like a beacon's. It drops nothing.
 */
public final class TempleBeacon {

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, FlashMod.MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, FlashMod.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(
            ForgeRegistries.BLOCK_ENTITY_TYPES, FlashMod.MODID);

    public static final RegistryObject<Block> BLOCK = BLOCKS.register("temple_beacon",
            () -> new TempleBeaconBlock(BlockBehaviour.Properties.copy(Blocks.BEACON).noLootTable()));
    public static final RegistryObject<Item> ITEM = ITEMS.register("temple_beacon",
            () -> new BlockItem(BLOCK.get(), new Item.Properties()));
    @SuppressWarnings("ConstantConditions")
    public static final RegistryObject<BlockEntityType<Beam>> BLOCK_ENTITY = BLOCK_ENTITIES.register("temple_beacon",
            () -> BlockEntityType.Builder.of(Beam::new, BLOCK.get()).build(null));

    private TempleBeacon() {
    }

    public record Section(float[] color, int height) {
    }

    public static final class Beam extends BlockEntity {
        public List<Section> sections = List.of();

        public Beam(BlockPos pos, BlockState state) {
            super(BLOCK_ENTITY.get(), pos, state);
        }

        static void clientTick(Level level, BlockPos pos, BlockState state, Beam b) {
            if (level.getGameTime() % 10L == 0L || b.sections.isEmpty()) {
                b.sections = compute(level, pos);
            }
        }

        @Override
        public net.minecraft.world.phys.AABB getRenderBoundingBox() {
            return INFINITE_EXTENT_AABB;
        }
    }

    private static List<Section> compute(Level level, BlockPos pos) {
        List<Section> out = new ArrayList<>();
        float[] color = {1.0F, 1.0F, 1.0F};
        int height = 0;
        int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, pos.getX(), pos.getZ());
        BlockPos.MutableBlockPos m = pos.mutable();
        for (int y = pos.getY() + 1; y < Math.max(top, pos.getY() + 1) + 1 && y < level.getMaxBuildHeight(); y++) {
            m.setY(y);
            BlockState s = level.getBlockState(m);
            float[] tint = s.getBeaconColorMultiplier(level, m, pos);
            if (tint != null) {
                float[] mixed = out.isEmpty() && height == 0 ? tint : new float[] {(color[0] + tint[0]) / 2.0F,
                        (color[1] + tint[1]) / 2.0F, (color[2] + tint[2]) / 2.0F};
                if (!Arrays.equals(mixed, color)) {
                    if (height > 0) {
                        out.add(new Section(color, height));
                    }
                    color = mixed;
                    height = 0;
                }
            } else if (s.getLightBlock(level, m) >= 15 && !s.is(Blocks.BEDROCK)) {
                break;
            }
            height++;
        }
        out.add(new Section(color, Math.max(1, height) + 512));
        return out;
    }

    static final class TempleBeaconBlock extends BaseEntityBlock {
        TempleBeaconBlock(BlockBehaviour.Properties p) {
            super(p);
        }

        @Override
        public RenderShape getRenderShape(BlockState state) {
            return RenderShape.MODEL;
        }

        @Override
        public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
            return new Beam(pos, state);
        }

        @Override
        public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                      BlockEntityType<T> type) {
            return level.isClientSide ? createTickerHelper(type, BLOCK_ENTITY.get(), Beam::clientTick) : null;
        }

        @Override
        public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
            return true;
        }
    }
}
