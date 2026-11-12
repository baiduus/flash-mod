package com.flash.entity;

import com.flash.item.FlashSuitUtil;
import com.flash.registry.ModItems;
import java.util.List;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Armor stand of the mod. Crouch + right click: the suit worn by the player goes
 * to the stand, or (when the player has no suit on) the stand's suit goes on the player.
 */
public class FlashSuitStand extends ArmorStand {

    /** Entity tag of the museum exhibits: locked, the suit cannot be taken. */
    public static final String MUSEUM_TAG = "flash_museum_exhibit";

    public FlashSuitStand(EntityType<? extends ArmorStand> type, Level level) {
        super(type, level);
    }

    /** Museum exhibit: invulnerable (except to creative players) and nothing can be taken or swapped. */
    public void lockAsExhibit() {
        this.addTag(MUSEUM_TAG);
        this.setInvulnerable(true);
    }

    public boolean isExhibit() {
        return this.getTags().contains(MUSEUM_TAG);
    }

    @Override
    public InteractionResult interactAt(Player player, Vec3 hit, InteractionHand hand) {
        if (!player.getAbilities().instabuild
                && (this.isExhibit() || (!this.level().isClientSide && FlashMuseumGuard.isExhibit(this)))) {
            return InteractionResult.FAIL;
        }
        if (player.isShiftKeyDown() && !this.isMarker()) {
            if (this.level().isClientSide) {
                return InteractionResult.SUCCESS;
            }
            return FlashSuitUtil.swapWithStand(player, this)
                    ? InteractionResult.CONSUME : InteractionResult.PASS;
        }
        return super.interactAt(player, hit, hand);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean result = super.hurt(source, amount);
        if (this.isRemoved() && !this.level().isClientSide) {
            // Broken: the vanilla drop is replaced by this mod's stand.
            List<ItemEntity> drops = this.level().getEntitiesOfClass(ItemEntity.class,
                    this.getBoundingBox().inflate(1.5D, 1.0D, 1.5D),
                    e -> e.getItem().is(Items.ARMOR_STAND) && e.tickCount < 3);
            for (ItemEntity drop : drops) {
                drop.setItem(new ItemStack(ModItems.FLASH_SUIT_STAND.get()));
            }
        }
        return result;
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide) {
            // Lex Luthor's suit charges here when the stand is fed with redstone
            com.flash.lex.LexSuit.standTick(this);
        }
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(ModItems.FLASH_SUIT_STAND.get());
    }
}
