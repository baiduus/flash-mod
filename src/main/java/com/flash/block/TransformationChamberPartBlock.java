package com.flash.block;

import com.flash.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Invisible block that fills one cell of the big chamber model. It carries the
 * collision/selection shape of its cell, forwards right-clicks and redstone
 * updates to the controller and takes the whole machine down when broken.
 */
public class TransformationChamberPartBlock extends Block {

    public static final DirectionProperty FACING = TransformationChamberBlock.FACING;
    /** Model cell along x (0..4), z (0..4 = -3..1) and y (0..5). */
    public static final IntegerProperty MX = IntegerProperty.create("mx", 0, MachineLayout.CELLS_X - 1);
    public static final IntegerProperty MZ = IntegerProperty.create("mz", 0, MachineLayout.CELLS_Z - 1);
    public static final IntegerProperty MY = IntegerProperty.create("my", 0, MachineLayout.CELLS_Y - 1);

    public TransformationChamberPartBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.SOUTH)
                .setValue(MX, 0).setValue(MZ, 0).setValue(MY, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, MX, MZ, MY);
    }

    public static BlockState stateFor(Direction facing, int mx, int mz, int my) {
        return ModBlocks.TRANSFORMATION_CHAMBER_PART.get().defaultBlockState()
                .setValue(FACING, facing).setValue(MX, mx).setValue(MZ, mz).setValue(MY, my);
    }

    /** Position of the controller that owns this part. */
    public static BlockPos controllerPos(BlockPos pos, BlockState state) {
        int[] o = MachineLayout.cellOffset(state.getValue(FACING), state.getValue(MX), state.getValue(MZ));
        return pos.offset(-o[0], -state.getValue(MY), -o[1]);
    }

    private static BlockPos controllerOf(Level level, BlockPos pos, BlockState state) {
        BlockPos controller = controllerPos(pos, state);
        return level.getBlockState(controller).is(ModBlocks.TRANSFORMATION_CHAMBER.get()) ? controller : null;
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
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block,
                                BlockPos fromPos, boolean isMoving) {
        if (level.isClientSide) {
            return;
        }
        BlockPos controller = controllerOf(level, pos, state);
        if (controller != null && level.getBlockEntity(controller) instanceof TransformationChamberBlockEntity chamber) {
            chamber.refreshPower();
        }
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
        return new ItemStack(ModBlocks.TRANSFORMATION_CHAMBER.get());
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return MachineLayout.shape(state.getValue(FACING), state.getValue(MX), state.getValue(MZ), state.getValue(MY));
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return this.getShape(state, level, pos, context);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }
}
