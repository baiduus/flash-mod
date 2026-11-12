package com.flash.block;

import com.flash.power.HeroAdvancements;
import com.flash.power.HeroPowers;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Mjolnir stuck in the ground at the heart of the asteroid crater (ThorCraterPiece), drawn by MjolnirStoneRenderer.
 * Only the worthy can lift it (HeroAdvancements: a raid won and 100 hostile creatures killed): right click and the
 * power of Thor is his, with a thunderclap. Anyone else cannot move it. It cannot be broken or blown up.
 */
public class MjolnirStoneBlock extends BaseEntityBlock {

    private static final VoxelShape SHAPE = Block.box(5.0D, 0.0D, 5.0D, 11.0D, 9.0D, 11.0D);

    public MjolnirStoneBlock(Properties properties) {
        super(properties);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MjolnirStoneBlockEntity(pos, state);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
                                 BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        if (!(player instanceof ServerPlayer sp) || !(level instanceof ServerLevel server)) {
            return InteractionResult.SUCCESS;
        }
        if (!HeroAdvancements.isWorthy(sp)) {
            // it does not move: the hammer pushes back
            sp.displayClientMessage(Component.translatable("message.flash.not_worthy"), true);
            level.playSound(null, pos, SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 0.6F, 0.5F);
            Vec3 away = sp.position().subtract(Vec3.atCenterOf(pos)).multiply(1.0D, 0.0D, 1.0D);
            if (away.lengthSqr() > 1.0E-4D) {
                sp.push(away.normalize().x * 0.6D, 0.25D, away.normalize().z * 0.6D);
                sp.hurtMarked = true;
            }
            server.sendParticles(ParticleTypes.ELECTRIC_SPARK, pos.getX() + 0.5D, pos.getY() + 0.6D,
                    pos.getZ() + 0.5D, 25, 0.3D, 0.4D, 0.3D, 0.2D);
            return InteractionResult.CONSUME;
        }
        if (com.flash.thor.ThorPowers.has(sp)) {
            sp.displayClientMessage(Component.translatable("message.flash.mjolnir_already_thor"), true);
            return InteractionResult.CONSUME;
        }
        if (!HeroPowers.inject(sp, HeroPowers.Hero.THOR)) {
            return InteractionResult.CONSUME;
        }
        // lifted: the hammer is his, the sky answers
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(server);
        if (bolt != null) {
            bolt.moveTo(Vec3.atBottomCenterOf(pos));
            bolt.setVisualOnly(true);
            server.addFreshEntity(bolt);
        }
        server.sendParticles(ParticleTypes.ELECTRIC_SPARK, pos.getX() + 0.5D, pos.getY() + 1.0D, pos.getZ() + 0.5D,
                80, 0.6D, 1.0D, 0.6D, 0.4D);
        sp.displayClientMessage(Component.translatable("message.flash.mjolnir_lifted"), false);
        HeroAdvancements.award(sp, HeroAdvancements.THOR);
        return InteractionResult.CONSUME;
    }

    /** Sparks crawl over the hammer now and then. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(4) == 0) {
            level.addParticle(ParticleTypes.ELECTRIC_SPARK, pos.getX() + 0.3D + random.nextDouble() * 0.4D,
                    pos.getY() + 0.3D + random.nextDouble() * 0.6D, pos.getZ() + 0.3D + random.nextDouble() * 0.4D,
                    0.0D, 0.05D, 0.0D);
        }
    }
}
