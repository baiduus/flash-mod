package com.flash.item;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

/**
 * Reusable syringe. Empty by default; drag a capsule onto it in the inventory
 * to load it, then hold right click to inject. The content lives in the NBT
 * tag {@code Content} and is described by {@link SyringeContents}, so new
 * substances can be added without touching this class.
 */
public class SyringeItem extends Item {

    public static final String TAG_CONTENT = "Content";
    private static final int INJECT_TICKS = 40;

    public SyringeItem(Properties properties) {
        super(properties);
    }

    public static SyringeContent content(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(TAG_CONTENT)) {
            return null;
        }
        return SyringeContents.byId(tag.getString(TAG_CONTENT));
    }

    public static ItemStack filled(Item syringe, SyringeContent content) {
        ItemStack stack = new ItemStack(syringe);
        stack.getOrCreateTag().putString(TAG_CONTENT, content.id());
        return stack;
    }

    /** Model predicate value ({@code flash:content}): 0 empty, otherwise the content's model index. */
    public static float modelIndex(ItemStack stack) {
        SyringeContent content = content(stack);
        return content == null ? 0.0F : content.modelIndex();
    }

    /** The player a Yas capsule (and the syringe it was loaded in) belongs to. */
    public static final String YAS_OWNER = "YasOwner";
    public static final String YAS_OWNER_NAME = "YasOwnerName";

    /** Dragging a capsule onto the syringe in the inventory loads it. */
    @Override
    public boolean overrideOtherStackedOnMe(ItemStack stack, ItemStack other, Slot slot,
                                            ClickAction action, Player player, SlotAccess access) {
        if (stack.getCount() != 1 || content(stack) != null) {
            return false;
        }
        SyringeContent content = SyringeContents.byCapsule(other);
        if (content == null) {
            return false;
        }
        stack.getOrCreateTag().putString(TAG_CONTENT, content.id());
        if (other.hasTag() && other.getTag().hasUUID(YAS_OWNER)) {
            stack.getOrCreateTag().putUUID(YAS_OWNER, other.getTag().getUUID(YAS_OWNER));
            stack.getOrCreateTag().putString(YAS_OWNER_NAME, other.getTag().getString(YAS_OWNER_NAME));
        }
        other.shrink(1);
        player.playSound(SoundEvents.BOTTLE_FILL, 0.8F, 1.2F);
        if (!player.level().isClientSide()) {
            player.displayClientMessage(
                    Component.translatable("message.flash.syringe_filled"), true);
        }
        return true;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (content(stack) == null) {
            // empty: a hero takes his power out into a capsule
            if (!com.flash.power.HeroPowers.hasAny(player)) {
                return InteractionResultHolder.pass(stack);
            }
            if (!level.isClientSide && !com.flash.power.HeroPowers.canExtract(player)) {
                player.displayClientMessage(Component.translatable("message.flash.extract_temporary"), true);
                return InteractionResultHolder.fail(stack);
            }
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        SyringeContent content = content(stack);
        if (content == null && entity instanceof ServerPlayer player) {
            if (com.flash.power.HeroPowers.extract(player)) {
                level.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.PLAYER_HURT_SWEET_BERRY_BUSH, SoundSource.PLAYERS, 0.8F, 0.6F);
            }
            return stack;
        }
        if (content != null && entity instanceof ServerPlayer player) {
            if (content == SyringeContents.YAS_ESSENCE && !yasOwner(stack, player)) {
                // only the one who transcended can take Yas back
                player.displayClientMessage(Component.translatable("message.flash.yas_not_owner",
                        stack.hasTag() ? stack.getTag().getString(YAS_OWNER_NAME) : "?"), true);
                return stack;
            }
            if (content.injector().inject(player)) {
                level.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.PLAYER_HURT_SWEET_BERRY_BUSH, SoundSource.PLAYERS, 0.8F, 0.6F);
                if (!player.getAbilities().instabuild) {
                    stack.removeTagKey(TAG_CONTENT);
                    stack.removeTagKey(YAS_OWNER);
                    stack.removeTagKey(YAS_OWNER_NAME);
                    if (stack.getTag() != null && stack.getTag().isEmpty()) {
                        stack.setTag(null);
                    }
                }
            }
        }
        return stack;
    }

    private static boolean yasOwner(ItemStack stack, ServerPlayer player) {
        return com.flash.yas.YasPowers.transcended(player) && stack.hasTag() && stack.getTag().hasUUID(YAS_OWNER)
                && stack.getTag().getUUID(YAS_OWNER).equals(player.getUUID());
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return INJECT_TICKS;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BLOCK;
    }

    @Override
    public Component getName(ItemStack stack) {
        SyringeContent content = content(stack);
        if (content == null) {
            return super.getName(stack);
        }
        return Component.translatable("item.flash.syringe." + content.id());
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        SyringeContent content = content(stack);
        tooltip.add(Component.translatable(content == null
                ? "tooltip.flash.syringe.empty"
                : "tooltip.flash.syringe." + content.id()).withStyle(ChatFormatting.GRAY));
    }
}
