package com.flash.client.infinity;

import com.flash.infinity.entity.StoneClone;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

/** An illusion: its player's model and skin (or the disguise's), armour and held items. */
public class CloneRenderer extends HumanoidMobRenderer<StoneClone, PlayerModel<StoneClone>> {

    public CloneRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new PlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER), false), 0.5F);
        this.addLayer(new HumanoidArmorLayer<>(this, new HumanoidModel<>(ctx.bakeLayer(
                ModelLayers.PLAYER_INNER_ARMOR)), new HumanoidModel<>(ctx.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)),
                ctx.getModelManager()));
    }

    @Override
    public ResourceLocation getTextureLocation(StoneClone c) {
        Minecraft mc = Minecraft.getInstance();
        Player o = c.ownerPlayer();
        if (o instanceof AbstractClientPlayer ap) {
            return ap.getSkinTextureLocation();
        }
        if (c.owner() != null && mc.getConnection() != null) {
            PlayerInfo info = mc.getConnection().getPlayerInfo(c.owner());
            if (info != null) {
                return info.getSkinLocation();
            }
        }
        return c.owner() == null ? DefaultPlayerSkin.getDefaultSkin() : DefaultPlayerSkin.getDefaultSkin(c.owner());
    }
}
