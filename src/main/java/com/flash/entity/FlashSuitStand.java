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
 * The mod's armour stand, for every hero. Crouch + right click swaps the four armour slots between the player and
 * the stand: any armour (the Flash suits, Lex Luthor's armour, vanilla or other mods' armour). Superman and Thor wear
 * no armour: transformed, with nothing to swap and an empty hand, crouch + right click puts the suit they wear on
 * display on the stand (once more takes it off). Lex's armour charges here with redstone.
 */
public class FlashSuitStand extends ArmorStand {

    /** Hero suit on display ("" none): the id of a Superman or Thor suit (SupermanSuitLayer draws it). */
    private static final net.minecraft.network.syncher.EntityDataAccessor<String> HERO_SUIT =
            net.minecraft.network.syncher.SynchedEntityData.defineId(FlashSuitStand.class,
                    net.minecraft.network.syncher.EntityDataSerializers.STRING);

    /** Entity tag of the museum exhibits: locked, the suit cannot be taken. */
    public static final String MUSEUM_TAG = "flash_museum_exhibit";

    public FlashSuitStand(EntityType<? extends ArmorStand> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(HERO_SUIT, "");
    }

    @Override
    public void addAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (!this.heroSuit().isEmpty()) {
            tag.putString("HeroSuit", this.heroSuit());
        }
    }

    @Override
    public void readAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.entityData.set(HERO_SUIT, tag.getString("HeroSuit"));
    }

    public String heroSuit() {
        return this.entityData.get(HERO_SUIT);
    }

    /** The suit the player wears as Superman or Thor now, or null. */
    private static String suitOf(Player player) {
        if (com.flash.thor.ThorPowers.isThor(player)) {
            return com.flash.thor.ThorSuits.id(com.flash.thor.ThorPowers.suit(player));
        }
        if (com.flash.superman.SupermanPowers.isSuperman(player)) {
            return com.flash.superman.SupermanSuits.id(com.flash.superman.SupermanPowers.suit(player));
        }
        return null;
    }

    /**
     * Crouch + right click on the stand: a hero transformed (Superman, Thor) with an empty hand and no armour on puts
     * his suit on display (or takes it down); otherwise the armour is swapped, and with nothing to swap an empty hand
     * takes a displayed suit down.
     */
    public boolean standClick(Player player) {
        boolean bare = true;
        for (net.minecraft.world.entity.EquipmentSlot slot : FlashSuitUtil.SLOTS) {
            bare &= player.getItemBySlot(slot).isEmpty();
        }
        if (bare && suitOf(player) != null && player.getMainHandItem().isEmpty()) {
            return this.toggleHeroSuit(player);
        }
        return FlashSuitUtil.swapWithStand(player, this) || this.toggleHeroSuit(player);
    }

    /** Puts the hero's suit on display, or takes it off when it is that one already. */
    private boolean toggleHeroSuit(Player player) {
        String id = suitOf(player);
        if (id == null || !player.getMainHandItem().isEmpty()) {
            if (!this.heroSuit().isEmpty() && player.getMainHandItem().isEmpty()) {
                // anyone can take a displayed suit down
                this.entityData.set(HERO_SUIT, "");
                this.displayFx(false);
                return true;
            }
            return false;
        }
        boolean same = id.equals(this.heroSuit());
        this.entityData.set(HERO_SUIT, same ? "" : id);
        this.displayFx(!same);
        player.displayClientMessage(net.minecraft.network.chat.Component.translatable(same
                ? "message.flash.stand_suit_off" : "message.flash.stand_suit_on"), true);
        return true;
    }

    private void displayFx(boolean on) {
        if (this.level() instanceof net.minecraft.server.level.ServerLevel level) {
            level.playSound(null, this.blockPosition(), on ? net.minecraft.sounds.SoundEvents.ARMOR_EQUIP_NETHERITE
                    : net.minecraft.sounds.SoundEvents.ARMOR_EQUIP_GENERIC, net.minecraft.sounds.SoundSource.PLAYERS,
                    1.0F, 1.0F);
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD, this.getX(), this.getY() + 1.0D,
                    this.getZ(), 20, 0.3D, 0.7D, 0.3D, 0.02D);
        }
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
            return this.standClick(player) ? InteractionResult.CONSUME : InteractionResult.PASS;
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
