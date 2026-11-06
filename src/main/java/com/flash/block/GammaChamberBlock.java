package com.flash.block;

import com.flash.registry.ModBlockEntities;
import com.flash.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * Gamma chamber (the Hulk's machine). Put a gamma capsule in it (right click with
 * the capsule), step onto the pad inside the tube and right click with an empty
 * hand: the gamma sequence starts and ends with the transformation. Only the
 * pad is solid; the tube around it is drawn by the model (needs 3 free blocks above).
 */
public class GammaChamberBlock extends BaseEntityBlock {

    public static final BooleanProperty LOADED = BooleanProperty.create("loaded");
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");
    private static final VoxelShape PAD = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 4.0D, 16.0D);

    public GammaChamberBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(LOADED, false).setValue(ACTIVE, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LOADED, ACTIVE);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        for (int i = 1; i <= 3; i++) {
            if (!context.getLevel().getBlockState(pos.above(i)).canBeReplaced(context)) {
                return null;
            }
        }
        return this.defaultBlockState();
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return true;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(level.getBlockEntity(pos) instanceof GammaChamberBlockEntity chamber)) {
            return InteractionResult.PASS;
        }
        ItemStack held = player.getItemInHand(hand);
        if (state.getValue(ACTIVE)) {
            player.displayClientMessage(Component.translatable("message.flash.gamma_chamber_running"), true);
            return InteractionResult.CONSUME;
        }
        if (!state.getValue(LOADED)) {
            if (held.is(ModItems.GAMMA_CAPSULE.get())) {
                if (!player.getAbilities().instabuild) {
                    held.shrink(1);
                }
                level.setBlock(pos, state.setValue(LOADED, true), 3);
                level.playSound(null, pos, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 1.0F, 1.4F);
                player.displayClientMessage(Component.translatable("message.flash.gamma_chamber_loaded"), true);
            } else {
                player.displayClientMessage(Component.translatable("message.flash.gamma_chamber_empty"), true);
            }
            return InteractionResult.CONSUME;
        }
        if (!GammaChamberBlockEntity.isOnPad(player, pos)) {
            player.displayClientMessage(Component.translatable("message.flash.gamma_chamber_enter"), true);
            return InteractionResult.CONSUME;
        }
        chamber.start(player, level, pos, state);
        return InteractionResult.CONSUME;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return PAD;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new GammaChamberBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        return level.isClientSide ? null
                : createTickerHelper(type, ModBlockEntities.GAMMA_CHAMBER.get(), GammaChamberBlockEntity::serverTick);
    }
}
