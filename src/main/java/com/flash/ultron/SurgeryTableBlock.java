package com.flash.ultron;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The surgery table: an operating table the size of a bed (two blocks, placed as a bed), with an arch over the chest
 * holding two robotic claws and a laser. Ultron lies down on it (right-click) and the claws operate: in a few
 * seconds the nanocore is taken out of his body and handed back to him (UltronPowers.revoke). Anyone else finds
 * nothing to take out.
 *
 * <p>Lying on it is the vanilla sleep (the table answers as a bed, Forge's isBed), kept under the hundred ticks that
 * would count for skipping the night; it is allowed by day too (SurgeryTableBlockEntity.onSleepTime).</p>
 */
public class SurgeryTableBlock extends BaseEntityBlock {

    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final EnumProperty<BedPart> PART = BlockStateProperties.BED_PART;
    public static final BooleanProperty OPERATING = BooleanProperty.create("operating");
    private static final VoxelShape SHAPE = Shapes.or(Block.box(0.5D, 0.0D, 0.5D, 15.5D, 11.0D, 15.5D));

    public SurgeryTableBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH)
                .setValue(PART, BedPart.FOOT).setValue(OPERATING, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PART, OPERATING);
    }

    /** The head block (where the patient's head lies and the machine is drawn) of a table block. */
    public static BlockPos headOf(BlockPos pos, BlockState state) {
        return state.getValue(PART) == BedPart.HEAD ? pos : pos.relative(state.getValue(FACING));
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction dir = context.getHorizontalDirection();
        BlockPos pos = context.getClickedPos();
        BlockPos head = pos.relative(dir);
        Level level = context.getLevel();
        return level.getBlockState(head).canBeReplaced(context) && level.getWorldBorder().isWithinBounds(head)
                ? this.defaultBlockState().setValue(FACING, dir) : null;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer,
                            ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide) {
            BlockPos head = pos.relative(state.getValue(FACING));
            level.setBlock(head, state.setValue(PART, BedPart.HEAD), 3);
            level.blockUpdated(pos, Blocks.AIR);
            state.updateNeighbourShapes(level, pos, 3);
        }
    }

    @Override
    public BlockState updateShape(BlockState state, Direction dir, BlockState other, LevelAccessor level, BlockPos pos,
                                  BlockPos otherPos) {
        Direction toOther = state.getValue(PART) == BedPart.FOOT ? state.getValue(FACING)
                : state.getValue(FACING).getOpposite();
        if (dir == toOther) {
            return other.is(this) && other.getValue(PART) != state.getValue(PART)
                    ? state : Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, dir, other, level, pos, otherPos);
    }

    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && player.isCreative() && state.getValue(PART) == BedPart.FOOT) {
            // the creative break of the foot: the head goes without a drop
            BlockPos head = pos.relative(state.getValue(FACING));
            BlockState hs = level.getBlockState(head);
            if (hs.is(this) && hs.getValue(PART) == BedPart.HEAD) {
                level.setBlock(head, Blocks.AIR.defaultBlockState(), 35);
                level.levelEvent(player, 2001, head, Block.getId(hs));
            }
        }
        super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
                                 BlockHitResult hit) {
        return interact(level, pos, state, player);
    }

    /**
     * Ultron lying down on the table (also reached from the right-click event, SurgeryTableBlock.Clicks, so that
     * nothing else can swallow the click first).
     */
    static InteractionResult interact(Level level, BlockPos pos, BlockState state, Player player) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(player instanceof ServerPlayer p) || !(state.getBlock() instanceof SurgeryTableBlock block)) {
            return InteractionResult.PASS;
        }
        org.slf4j.Logger log = com.mojang.logging.LogUtils.getLogger();
        BlockPos head = headOf(pos, state);
        BlockState hs = level.getBlockState(head);
        log.info("[flash] surgery table clicked at {} by {} (ultron={}, head={})", pos, p.getGameProfile().getName(),
                UltronPowers.has(p), hs.is(block));
        if (!hs.is(block)) {
            return InteractionResult.PASS;
        }
        if (!(level.getBlockEntity(head) instanceof SurgeryTableBlockEntity)) {
            // (lost: made again)
            level.setBlockEntity(new SurgeryTableBlockEntity(head, hs));
        }
        if (!(level.getBlockEntity(head) instanceof SurgeryTableBlockEntity table)) {
            return InteractionResult.PASS;
        }
        if (table.busy()) {
            p.displayClientMessage(Component.translatable("message.flash.surgery.busy"), false);
            return InteractionResult.CONSUME;
        }
        if (!UltronPowers.has(p)) {
            p.displayClientMessage(Component.translatable("message.flash.surgery.nothing"), false);
            return InteractionResult.CONSUME;
        }
        if (p.isSleeping()) {
            p.stopSleepInBed(true, true);
        }
        table.start(p);
        return InteractionResult.CONSUME;
    }

    /** The click on the table, caught first (before anything else can take it). */
    @net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = com.flash.FlashMod.MODID)
    public static final class Clicks {
        private Clicks() {
        }

        @net.minecraftforge.eventbus.api.SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.HIGHEST)
        public static void onRightClick(net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock event) {
            Level level = event.getLevel();
            BlockState state = level.getBlockState(event.getPos());
            if (!(state.getBlock() instanceof SurgeryTableBlock) || event.getHand() != InteractionHand.MAIN_HAND) {
                return;
            }
            InteractionResult r = interact(level, event.getPos(), state, event.getEntity());
            event.setCanceled(true);
            event.setCancellationResult(r == InteractionResult.PASS ? InteractionResult.SUCCESS : r);
        }
    }

    // ------------------------------------------------------------------
    // a bed for Forge (the patient lies on it)
    // ------------------------------------------------------------------

    @Override
    public boolean isBed(BlockState state, BlockGetter level, BlockPos pos, @Nullable Entity sleeper) {
        return true;
    }

    @Override
    public Direction getBedDirection(BlockState state, net.minecraft.world.level.LevelReader level, BlockPos pos) {
        return state.getValue(FACING);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(PART) == BedPart.HEAD ? new SurgeryTableBlockEntity(pos, state) : null;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, UltronRegistry.SURGERY_TABLE_BE.get(),
                SurgeryTableBlockEntity::serverTick);
    }

    @Override
    public long getSeed(BlockState state, BlockPos pos) {
        BlockPos o = pos.relative(state.getValue(FACING), state.getValue(PART) == BedPart.HEAD ? 0 : 1);
        return net.minecraft.util.Mth.getSeed(o.getX(), pos.getY(), o.getZ());
    }

    /** (BedBlock's own helper reads these same properties.) */
    static boolean sameProperties() {
        return FACING == BedBlock.FACING && PART == BedBlock.PART;
    }
}
