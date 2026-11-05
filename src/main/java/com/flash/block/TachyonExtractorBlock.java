package com.flash.block;

import com.flash.registry.ModBlockEntities;
import com.flash.registry.ModItems;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * One-block machine that extracts tachyons from a Torn Suit (and turns Nether
 * fungi into corrupted matter), powered by redstone. The glass door faces the
 * player who placed it.
 */
public class TachyonExtractorBlock extends BaseEntityBlock {

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    public static final BooleanProperty WORKING = BooleanProperty.create("working");

    public TachyonExtractorBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(POWERED, false)
                .setValue(WORKING, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, POWERED, WORKING);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection().getOpposite())
                .setValue(POWERED, context.getLevel().hasNeighborSignal(context.getClickedPos()));
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TachyonExtractorBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return createTickerHelper(type, ModBlockEntities.TACHYON_EXTRACTOR.get(),
                    TachyonExtractorBlockEntity::clientTick);
        }
        return createTickerHelper(type, ModBlockEntities.TACHYON_EXTRACTOR.get(),
                TachyonExtractorBlockEntity::serverTick);
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block,
                                BlockPos fromPos, boolean moving) {
        if (level.isClientSide()) {
            return;
        }
        boolean powered = level.hasNeighborSignal(pos);
        if (powered != state.getValue(POWERED)) {
            level.setBlock(pos, state.setValue(POWERED, powered), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!(level.getBlockEntity(pos) instanceof TachyonExtractorBlockEntity extractor)) {
            return InteractionResult.PASS;
        }
        ItemStack held = player.getItemInHand(hand);
        if (held.isEmpty()) {
            if (player.isShiftKeyDown()) {
                extractor.returnInput(player);
            } else {
                extractor.takeResult(player);
            }
            return InteractionResult.CONSUME;
        }
        extractor.insert(player, held);
        return InteractionResult.CONSUME;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!state.is(newState.getBlock())) {
            if (level.getBlockEntity(pos) instanceof TachyonExtractorBlockEntity extractor) {
                extractor.dropContents(level, pos);
            }
        }
        super.onRemove(state, level, pos, newState, moving);
    }

    @Override
    public boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof TachyonExtractorBlockEntity extractor
                ? extractor.comparatorSignal() : 0;
    }

    /** Small yellow sparks around the coil while working (client side only, discreet). */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(WORKING)) {
            return;
        }
        Direction facing = state.getValue(FACING);
        boolean suit = level.getBlockEntity(pos) instanceof TachyonExtractorBlockEntity extractor
                && isSuit(extractor.displayStack());
        net.minecraft.core.particles.ParticleOptions main = suit
                ? ParticleTypes.ELECTRIC_SPARK
                : new net.minecraft.core.particles.DustParticleOptions(
                        new org.joml.Vector3f(0.85F, 0.05F, 0.08F), 1.0F);

        // Inside the chamber: sparks / red motes orbiting the spinning item.
        for (int i = 0; i < 2; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double radius = 0.10D + random.nextDouble() * 0.12D;
            double dx = Math.cos(angle) * radius;
            double dz = Math.sin(angle) * radius;
            double dy = 0.06D + random.nextDouble() * 0.38D;
            double wx;
            double wz;
            switch (facing) {
                case SOUTH -> { wx = -dx; wz = -dz; }
                case EAST -> { wx = -dz; wz = dx; }
                case WEST -> { wx = dz; wz = -dx; }
                default -> { wx = dx; wz = dz; }
            }
            level.addParticle(main, pos.getX() + 0.5D + wx, pos.getY() + dy,
                    pos.getZ() + 0.5D + wz, -wz * 0.15D, 0.01D, wx * 0.15D);
        }
        if (random.nextInt(3) == 0) {
            level.addParticle(suit ? ParticleTypes.END_ROD : ParticleTypes.SMOKE,
                    pos.getX() + 0.5D + (random.nextDouble() - 0.5D) * 0.3D,
                    pos.getY() + 0.12D,
                    pos.getZ() + 0.5D + (random.nextDouble() - 0.5D) * 0.3D,
                    0.0D, 0.03D, 0.0D);
        }

        // Coil on top.
        if (random.nextInt(4) == 0) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double radius = 0.4D * random.nextDouble();
            level.addParticle(ParticleTypes.ELECTRIC_SPARK,
                    pos.getX() + 0.5D + Math.cos(angle) * radius,
                    pos.getY() + 1.0D + random.nextDouble() * 0.3D,
                    pos.getZ() + 0.5D + Math.sin(angle) * radius,
                    0.0D, 0.02D, 0.0D);
        }
    }

    static boolean isSuit(ItemStack stack) {
        return stack.is(ModItems.TORN_SUIT.get());
    }

    static Component msg(String key) {
        return Component.translatable(key);
    }
}
