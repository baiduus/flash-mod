package com.flash.block;

import com.flash.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
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
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The main transformation chamber. One controller block owns the whole large
 * structure (stepped base, opening, columns, header, roof and side technology).
 * The rendered model extends around the controller; the controller cell is the
 * front-left column.
 *
 * <p>The model was rescaled to be about 3.1 blocks wide, 3.66 blocks tall and
 * 3.15 blocks deep (a little over two blocks shorter than the previous 5.7
 * block version) while keeping the original proportions. The selection shape
 * follows the rotated model footprint, so right-clicking any part of the
 * machine reaches this controller block instead of only the small controller
 * column.</p>
 */
public class TransformationChamberBlock extends BaseEntityBlock {

    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");
    public static final DirectionProperty FACING = DirectionProperty.create(
            "facing",
            Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST
    );

    /**
     * Footprint of the baked model in block-local space. The model is authored
     * in a 0..16 pixel space and expanded by its root transform; these are the
     * transformed bounds for facing=south (model y = 0). The other facings use
     * the same box rotated around the block centre, exactly like the model.
     */
    private static final double MODEL_MIN_X = -0.11D;
    private static final double MODEL_MAX_X = 3.03D;
    private static final double MODEL_MIN_Y = 0.0D;
    private static final double MODEL_MAX_Y = 3.66D;
    private static final double MODEL_MIN_Z = -0.24D;
    private static final double MODEL_MAX_Z = 2.92D;

    private static final VoxelShape SHAPE_SOUTH = Block.box(
            MODEL_MIN_X, MODEL_MIN_Y, MODEL_MIN_Z,
            MODEL_MAX_X, MODEL_MAX_Y, MODEL_MAX_Z);
    private static final VoxelShape SHAPE_WEST = Block.box(
            MODEL_MIN_Z, MODEL_MIN_Y, 1.0D - MODEL_MAX_X,
            MODEL_MAX_Z, MODEL_MAX_Y, 1.0D - MODEL_MIN_X);
    private static final VoxelShape SHAPE_NORTH = Block.box(
            1.0D - MODEL_MAX_X, MODEL_MIN_Y, 1.0D - MODEL_MAX_Z,
            1.0D - MODEL_MIN_X, MODEL_MAX_Y, 1.0D - MODEL_MIN_Z);
    private static final VoxelShape SHAPE_EAST = Block.box(
            1.0D - MODEL_MAX_Z, MODEL_MIN_Y, MODEL_MIN_X,
            1.0D - MODEL_MIN_Z, MODEL_MAX_Y, MODEL_MAX_X);

    /**
     * Collision stays on the controller column. The machine is entered by
     * right-click (the player is teleported to the cabin), so a full-model
     * collision box would only risk trapping the player during the exit
     * teleport. The height matches the lower half of the rescaled model.
     */
    private static final VoxelShape COLLISION =
            Block.box(0.0D, 0.0D, 0.0D, 16.0D, 40.0D, 16.0D);

    public TransformationChamberBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(ACTIVE, Boolean.FALSE)
                .setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ACTIVE, FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TransformationChamberBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                 BlockEntityType<T> type) {
        if (level.isClientSide) {
            return null;
        }
        return createTickerHelper(
                type,
                ModBlockEntities.TRANSFORMATION_CHAMBER.get(),
                TransformationChamberBlockEntity::serverTick
        );
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (player.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof TransformationChamberBlockEntity chamber) {
            chamber.tryEnter(serverPlayer);
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock()) && !level.isClientSide
                && level.getBlockEntity(pos) instanceof TransformationChamberBlockEntity chamber) {
            chamber.onChamberRemoved();
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case SOUTH -> SHAPE_SOUTH;
            case WEST -> SHAPE_WEST;
            case NORTH -> SHAPE_NORTH;
            case EAST -> SHAPE_EAST;
            default -> Shapes.block();
        };
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return COLLISION;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }
}
