package com.flash.infinity;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/** The Infinity Gauntlet: using it opens its stone menu; its tooltip lists the stones inside. */
public class InfinityGauntletItem extends Item {

    public InfinityGauntletItem(Properties p) {
        super(p);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player p, InteractionHand hand) {
        // its menu opens with the Open Gauntlet key (G), not with the right click
        return InteractionResultHolder.pass(p.getItemInHand(hand));
    }

    @Override
    public boolean isFoil(ItemStack s) {
        return InfinityGauntlet.stones(s).size() == InfinityGauntlet.SLOTS;
    }

    @Override
    public void appendHoverText(ItemStack s, Level level, List<Component> tip, TooltipFlag flag) {
        List<InfinityStone> in = new java.util.ArrayList<>(InfinityGauntlet.stones(s));
        in.sort(java.util.Comparator.comparingInt(x -> x.order));
        for (InfinityStone st : in) {
            tip.add(Component.literal(" - ").append(Component.translatable("item.flash." + st.id))
                    .withStyle(st.tooltip));
        }
        String skin = InfinityGauntlet.skin(s);
        if (!skin.isEmpty()) {
            tip.add(Component.translatable("tooltip.flash.infinity.skin",
                    InfPowers.str(InfinityGauntlet.skins().getOrDefault(skin, new com.google.gson.JsonObject()),
                            "title", skin)).withStyle(ChatFormatting.GRAY));
        }
    }
}
