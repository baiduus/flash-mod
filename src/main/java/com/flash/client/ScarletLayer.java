package com.flash.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * The Scarlet Witch's aura: a shell of Hand of God's red telekinetic energy (telecinese_energia, flowing like the
 * charged creeper's) over her whole body while she flies or right after she casts.
 */
public class ScarletLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

    private static final ResourceLocation ENERGY = new ResourceLocation("hand_of_god",
            "textures/entity/telecinese_energia.png");
    private final PlayerModel<AbstractClientPlayer> shell;

    public ScarletLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
        this.shell = new PlayerModel<>(LayerDefinition.create(PlayerModel.createMesh(new CubeDeformation(0.6F), false),
                64, 64).bakeRoot(), false);
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer p, float limbSwing,
                       float limbSwingAmount, float partial, float age, float headYaw, float headPitch) {
        if (p.isInvisible() || !ScarletClient.aura(p)) {
            return;
        }
        this.getParentModel().copyPropertiesTo(this.shell);
        this.shell.leftSleeve.copyFrom(this.shell.leftArm);
        this.shell.rightSleeve.copyFrom(this.shell.rightArm);
        this.shell.leftPants.copyFrom(this.shell.leftLeg);
        this.shell.rightPants.copyFrom(this.shell.rightLeg);
        this.shell.jacket.copyFrom(this.shell.body);
        this.shell.hat.copyFrom(this.shell.head);
        float t = age * 0.01F;
        var buffer = buffers.getBuffer(RenderType.energySwirl(ENERGY, t % 1.0F, (t * 0.7F) % 1.0F));
        float pulse = 0.75F + 0.25F * (float) Math.sin(age * 0.3F);
        this.shell.renderToBuffer(pose, buffer, 0xF000F0, OverlayTexture.NO_OVERLAY, 1.0F * pulse, 0.18F * pulse,
                0.24F * pulse, 1.0F);
    }
}
