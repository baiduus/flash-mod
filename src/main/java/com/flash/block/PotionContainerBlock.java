package com.flash.block;

import com.flash.registry.ModBlockEntities;
import com.flash.registry.ModBlocks;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
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
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.network.NetworkHooks;

/**
 * Side extension of the machine. Right-click opens the "Recipiente de Poções"
 * interface. Breaking it drops the three stored potions and safely aborts an
 * active transformation in the adjacent chamber.
 *
 * <p>The model uses the same 2.2 scale as the rescaled chamber, so the panel is
 * about 1 block wide and 2.48 blocks tall. Its selection shape matches that
 * volume, which makes the whole visible panel clickable, including its top and
 * the protruding side details.</p>
 */
public class PotionContainerBlock extends BaseEntityBlock {

    private static final VoxelShape SHAPE = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 16.0D, 16.0D);

    public static final BooleanProperty FILLED = BooleanProperty.create("filled");
    public static final DirectionProperty FACING = DirectionProperty.create(
            "facing",
            Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST
    );

    public PotionContainerBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FILLED, Boolean.FALSE)
                .setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FILLED, FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Level level = context.getLevel();
        BlockPos origin = context.getClickedPos();
        for (int y = 1; y <= PotionContainerPartBlock.MAX_LAYER; y++) {
            BlockPos cell = origin.above(y);
            if (level.isOutsideBuildHeight(cell) || !level.getBlockState(cell).canBeReplaced()) {
                if (!level.isClientSide && context.getPlayer() != null) {
                    context.getPlayer().displayClientMessage(
                            Component.translatable("message.flash.machine_no_space"), true);
                }
                return null;
            }
        }
        return this.defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);
        if (level.isClientSide || oldState.is(this)) {
            return;
        }
        for (int y = 1; y <= PotionContainerPartBlock.MAX_LAYER; y++) {
            BlockPos cell = pos.above(y);
            if (!level.isOutsideBuildHeight(cell) && level.getBlockState(cell).canBeReplaced()) {
                level.setBlock(cell, ModBlocks.POTION_CONTAINER_PART.get().defaultBlockState()
                                .setValue(PotionContainerPartBlock.LAYER, y),
                        Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            }
        }
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
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PotionContainerBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) {
            return null;
        }
        return createTickerHelper(
                type,
                ModBlockEntities.POTION_CONTAINER.get(),
                PotionContainerBlockEntity::serverTick
        );
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof PotionContainerBlockEntity container) {
            NetworkHooks.openScreen(serverPlayer, container, buffer -> buffer.writeBlockPos(pos));
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock()) && !level.isClientSide) {
            TransformationChamberBlockEntity chamber =
                    TransformationChamberBlock.chamberNextToContainer(level, pos);
            if (chamber != null) {
                chamber.onPotionContainerRemoved();
            }
            Block part = ModBlocks.POTION_CONTAINER_PART.get();
            for (int y = 1; y <= PotionContainerPartBlock.MAX_LAYER; y++) {
                if (level.getBlockState(pos.above(y)).is(part)) {
                    level.setBlock(pos.above(y), Blocks.AIR.defaultBlockState(),
                            Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS);
                }
            }
            if (level.getBlockEntity(pos) instanceof PotionContainerBlockEntity container) {
                Containers.dropContents(level, pos, container);
                container.clearContent();
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }
}
