package com.flash.item;

import com.flash.entity.FlashSuitStand;
import com.flash.registry.ModEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.List;
import org.jetbrains.annotations.Nullable;

/** Places a {@link FlashSuitStand}. */
public class FlashSuitStandItem extends Item {

    public FlashSuitStandItem() {
        super(new Item.Properties().stacksTo(16));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Direction direction = context.getClickedFace();
        if (direction == Direction.DOWN) {
            return InteractionResult.FAIL;
        }
        Level level = context.getLevel();
        BlockPos pos = new BlockPlaceContext(context).getClickedPos();
        Vec3 bottom = Vec3.atBottomCenterOf(pos);
        AABB box = ModEntities.FLASH_SUIT_STAND.get().getDimensions()
                .makeBoundingBox(bottom.x, bottom.y, bottom.z);
        if (!level.noCollision(null, box) || !level.getEntities(null, box).isEmpty()) {
            return InteractionResult.FAIL;
        }
        if (level instanceof ServerLevel server) {
            FlashSuitStand stand = ModEntities.FLASH_SUIT_STAND.get().create(server);
            if (stand == null) {
                return InteractionResult.FAIL;
            }
            float yaw = Mth.floor((Mth.wrapDegrees(context.getRotation() - 180.0F) + 22.5F) / 45.0F) * 45.0F;
            stand.moveTo(bottom.x, bottom.y, bottom.z, yaw, 0.0F);
            stand.setShowArms(true);
            stand.setNoBasePlate(true);
            server.addFreshEntity(stand);
            server.playSound(null, bottom.x, bottom.y, bottom.z, SoundEvents.ARMOR_STAND_PLACE,
                    SoundSource.BLOCKS, 0.75F, 0.8F);
        }
        context.getItemInHand().shrink(1);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
                                List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.flash.stand.1").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.flash.stand.2").withStyle(ChatFormatting.DARK_GRAY));
    }
}
