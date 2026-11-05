package com.flash.block;

import com.flash.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Invisible upper cell of the tall potion container (layers 1..3 above the
 * controller). Keeps the full panel solid, clickable and removable.
 */
public class PotionContainerPartBlock extends Block {

    public static final int MAX_LAYER = 3;
    public static final IntegerProperty LAYER = IntegerProperty.create("layer", 1, MAX_LAYER);

    private static final VoxelShape FULL = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 16.0D, 16.0D);
    private static final VoxelShape TOP = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 3.2D, 16.0D);

    public PotionContainerPartBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(LAYER, 1));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LAYER);
    }

    private static BlockPos controllerOf(Level level, BlockPos pos, BlockState state) {
        BlockPos controller = pos.below(state.getValue(LAYER));
        return level.getBlockState(controller).is(ModBlocks.POTION_CONTAINER.get()) ? controller : null;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        BlockPos controller = controllerOf(level, pos, state);
        if (controller == null) {
            return InteractionResult.PASS;
        }
        BlockState controllerState = level.getBlockState(controller);
        return controllerState.getBlock().use(controllerState, level, controller, player, hand, hit);
    }

    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide) {
            BlockPos controller = controllerOf(level, pos, state);
            if (controller != null) {
                boolean drop = !player.isCreative()
                        && player.hasCorrectToolForDrops(level.getBlockState(controller));
                level.destroyBlock(controller, drop);
            }
        }
        super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock()) && !level.isClientSide) {
            BlockPos controller = controllerOf(level, pos, state);
            if (controller != null) {
                level.destroyBlock(controller, true);
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Override
    public ItemStack getCloneItemStack(BlockGetter level, BlockPos pos, BlockState state) {
        return new ItemStack(ModBlocks.POTION_CONTAINER.get());
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(LAYER) == MAX_LAYER ? TOP : FULL;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }
}
