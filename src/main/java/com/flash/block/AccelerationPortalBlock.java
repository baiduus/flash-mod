package com.flash.block;

import com.flash.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
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
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Invisible marker of the circular Acceleration Force portal. The block has no
 * collision and no model; the actual energy ring is rendered by
 * {@code AccelerationPortalRenderer}. Server-side crossing detection lives in
 * {@link FlashAccelerationPortal} and does not depend on this block being
 * touched.
 */
public class AccelerationPortalBlock extends BaseEntityBlock {

    public static final EnumProperty<Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;

    public AccelerationPortalBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(AXIS, Axis.X));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AXIS);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AccelerationPortalBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                 BlockEntityType<T> type) {
        if (level.isClientSide) {
            return null;
        }
        return createTickerHelper(type, ModBlockEntities.ACCELERATION_PORTAL.get(),
                AccelerationPortalBlockEntity::serverTick);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                               CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                        CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (!level.isClientSide
                && entity instanceof net.minecraft.server.level.ServerPlayer player) {
            FlashAccelerationPortal.checkCrossing(player, player.position());
        }
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!(level.getBlockEntity(pos) instanceof AccelerationPortalBlockEntity portal)
                || !portal.isReady()) {
            return;
        }

        double yaw = Math.toRadians(portal.yaw());
        double rightX = Math.cos(yaw);
        double rightZ = Math.sin(yaw);
        double normalX = -Math.sin(yaw);
        double normalZ = Math.cos(yaw);
        double radius = portal.radius();

        for (int i = 0; i < 4; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double ringRadius = radius * (0.90D + random.nextDouble() * 0.16D);
            double x = pos.getX() + 0.5D + rightX * Math.cos(angle) * ringRadius
                    + normalX * (random.nextDouble() - 0.5D) * 0.35D;
            double y = pos.getY() + 0.5D + Math.sin(angle) * ringRadius;
            double z = pos.getZ() + 0.5D + rightZ * Math.cos(angle) * ringRadius
                    + normalZ * (random.nextDouble() - 0.5D) * 0.35D;
            level.addParticle(ParticleTypes.ELECTRIC_SPARK, x, y, z,
                    (random.nextDouble() - 0.5D) * 0.06D,
                    (random.nextDouble() - 0.5D) * 0.06D,
                    (random.nextDouble() - 0.5D) * 0.06D);
        }

        if (random.nextFloat() < 0.30F) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double ringRadius = radius * (0.95D + random.nextDouble() * 0.10D);
            double x = pos.getX() + 0.5D + rightX * Math.cos(angle) * ringRadius;
            double y = pos.getY() + 0.5D + Math.sin(angle) * ringRadius;
            double z = pos.getZ() + 0.5D + rightZ * Math.cos(angle) * ringRadius;
            level.addParticle(ParticleTypes.SOUL_FIRE_FLAME, x, y, z,
                    (random.nextDouble() - 0.5D) * 0.02D,
                    (random.nextDouble() - 0.5D) * 0.02D,
                    (random.nextDouble() - 0.5D) * 0.02D);
        }

        if (random.nextFloat() < 0.35F) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double glowRadius = radius * (0.55D + random.nextDouble() * 0.60D);
            level.addParticle(ParticleTypes.END_ROD,
                    pos.getX() + 0.5D + rightX * Math.cos(angle) * glowRadius,
                    pos.getY() + 0.5D - portal.groundOffset() + 0.08D,
                    pos.getZ() + 0.5D + rightZ * Math.cos(angle) * glowRadius,
                    (random.nextDouble() - 0.5D) * 0.01D,
                    0.01D + random.nextDouble() * 0.01D,
                    (random.nextDouble() - 0.5D) * 0.01D);
        }

        if (random.nextFloat() < 0.02F) {
            level.playLocalSound(pos.getX() + 0.5D, pos.getY() + 0.5D,
                    pos.getZ() + 0.5D, net.minecraft.sounds.SoundEvents.PORTAL_AMBIENT,
                    net.minecraft.sounds.SoundSource.BLOCKS, 0.35F,
                    0.6F + random.nextFloat() * 0.3F, false);
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos,
                         BlockState newState, boolean moving) {
        if (!level.isClientSide
                && !state.is(newState.getBlock())
                && level.getBlockEntity(pos) instanceof AccelerationPortalBlockEntity portal) {
            FlashAccelerationPortal.unregister(portal.owner(), pos);
        }
        super.onRemove(state, level, pos, newState, moving);
    }

    @Override
    public java.util.List<ItemStack> getDrops(BlockState state,
                                              net.minecraft.world.level.storage.loot.LootParams.Builder params) {
        return java.util.Collections.emptyList();
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        if (rotation == Rotation.CLOCKWISE_90 || rotation == Rotation.COUNTERCLOCKWISE_90) {
            return state.setValue(AXIS,
                    state.getValue(AXIS) == Axis.X ? Axis.Z : Axis.X);
        }
        return state;
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state;
    }
}
