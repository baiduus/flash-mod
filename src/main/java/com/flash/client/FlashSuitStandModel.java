package com.flash.client;

import com.flash.FlashMod;
import com.flash.entity.FlashSuitStand;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;

/**
 * The mod's suit stand, modelled from scratch: a tachyon display dais, one
 * central pole, a slim armature torso with a lightning emblem, a smooth
 * mannequin head and thin arms. It keeps the humanoid part names and pivots, so
 * the suit (armour) still sits exactly where it would on a player.
 */
public class FlashSuitStandModel extends HumanoidModel<FlashSuitStand> {

    public static final ModelLayerLocation LAYER =
            new ModelLayerLocation(new ResourceLocation(FlashMod.MODID, "suit_stand"), "main");

    public FlashSuitStandModel(ModelPart root) {
        super(root);
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("head", CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-3.0F, -7.0F, -3.0F, 6, 6, 6)      // mannequin head
                        .texOffs(24, 0).addBox(-1.0F, -1.0F, -1.0F, 2, 1, 2),    // neck
                PartPose.ZERO);
        root.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
        root.addOrReplaceChild("body", CubeListBuilder.create()
                        .texOffs(0, 12).addBox(-5.0F, 0.0F, -1.5F, 10, 2, 3)     // shoulder bar
                        .texOffs(0, 17).addBox(-3.5F, 2.0F, -1.0F, 7, 5, 2)      // chest frame + emblem
                        .texOffs(26, 12).addBox(-1.0F, 7.0F, -1.0F, 2, 4, 2)     // spine
                        .texOffs(18, 24).addBox(-3.0F, 10.0F, -1.5F, 6, 2, 3)    // hip ring
                        .texOffs(34, 12).addBox(-1.0F, 12.0F, -1.0F, 2, 10, 2)   // pole
                        .texOffs(0, 30).addBox(-4.5F, 22.0F, -4.5F, 9, 1, 9)     // upper dais
                        .texOffs(0, 40).addBox(-6.0F, 23.0F, -6.0F, 12, 1, 12),  // base
                PartPose.ZERO);
        root.addOrReplaceChild("right_arm", CubeListBuilder.create()
                        .texOffs(44, 0).addBox(-2.0F, -1.5F, -1.5F, 3, 3, 3)     // shoulder joint
                        .texOffs(44, 6).addBox(-1.5F, 1.5F, -1.0F, 2, 9, 2),     // arm rod
                PartPose.offset(-5.0F, 2.0F, 0.0F));
        root.addOrReplaceChild("left_arm", CubeListBuilder.create().mirror()
                        .texOffs(44, 0).addBox(-1.0F, -1.5F, -1.5F, 3, 3, 3)
                        .texOffs(44, 6).addBox(-0.5F, 1.5F, -1.0F, 2, 9, 2),
                PartPose.offset(5.0F, 2.0F, 0.0F));
        // no legs: the stand stands on its pole (the leggings and boots still use these pivots)
        root.addOrReplaceChild("right_leg", CubeListBuilder.create(), PartPose.offset(-1.9F, 12.0F, 0.0F));
        root.addOrReplaceChild("left_leg", CubeListBuilder.create(), PartPose.offset(1.9F, 12.0F, 0.0F));
        return LayerDefinition.create(mesh, 64, 64);
    }

    /** A display stand: always the same, upright pose (arms slightly away from the body). */
    @Override
    public void setupAnim(FlashSuitStand stand, float limbSwing, float limbSwingAmount,
                          float ageInTicks, float netHeadYaw, float headPitch) {
        for (ModelPart part : new ModelPart[] {this.head, this.hat, this.body,
                this.rightArm, this.leftArm, this.rightLeg, this.leftLeg}) {
            part.xRot = 0.0F;
            part.yRot = 0.0F;
            part.zRot = 0.0F;
        }
        this.rightArm.zRot = 0.10F;
        this.leftArm.zRot = -0.10F;
        this.hat.copyFrom(this.head);
    }
}
