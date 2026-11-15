package com.flash.client;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Doctor Doom's boot jets, drawn on his legs as they are posed (the flight leans and lays the body down: the jets
 * follow his feet, pointing out of the soles), and where his feet are in the world for the flames and smoke of the
 * thrusters (DoomClient).
 */
public class DoomLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

    private static final float[] JET = {0.45F, 0.75F, 1.0F};
    private static final float[] GOD_JET = {1.0F, 0.82F, 0.4F};
    /** Entity -> [right foot, its jet direction, left foot, its jet direction], and the tick they were seen. */
    private static final Map<Integer, Vec3[]> FEET = new HashMap<>();
    private static final Map<Integer, Integer> SEEN = new HashMap<>();

    public DoomLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    /** His feet as last drawn (null: not drawn lately, e.g. in first person). */
    static Vec3[] feet(AbstractClientPlayer p) {
        Integer t = SEEN.get(p.getId());
        return t != null && p.tickCount - t <= 2 ? FEET.get(p.getId()) : null;
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player,
                       float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw,
                       float headPitch) {
        Minecraft mc = Minecraft.getInstance();
        if (!DoomClient.flying(player) || player.isInvisible() || mc.screen != null && player == mc.player) {
            return;
        }
        Camera cam = mc.gameRenderer.getMainCamera();
        Quaternionf toWorld = new Quaternionf().rotationY(-(cam.getYRot() + 180.0F) * Mth.DEG_TO_RAD)
                .rotateX(-cam.getXRot() * Mth.DEG_TO_RAD);
        PlayerModel<AbstractClientPlayer> model = this.getParentModel();
        float[] jet = DoomClient.god(player) ? GOD_JET : JET;
        float flick = 0.85F + 0.15F * Mth.sin(ageInTicks * 2.3F + player.getId());
        Vec3[] out = new Vec3[4];
        BufferBuilder buf = VfxDraw.begin();
        try {
            ModelPart[] legs = {model.rightLeg, model.leftLeg};
            for (int i = 0; i < 2; i++) {
                ModelPart leg = legs[i];
                pose.pushPose();
                pose.translate(leg.x / 16.0F, leg.y / 16.0F, leg.z / 16.0F);
                pose.mulPose(new Quaternionf().rotationZYX(leg.zRot, leg.yRot, leg.xRot));
                Matrix4f m = pose.last().pose();
                Vec3 sole = new Vec3(0.0D, 12.2D / 16.0D, 0.0D);
                Vec3 tail = new Vec3(0.0D, (12.2D + 9.0D * flick) / 16.0D, 0.0D);
                VfxDraw.energy(buf, m, sole, tail, 0.09F, jet[0], jet[1], jet[2], 0.85F);
                Matrix4f inv = new Matrix4f(m).invert();
                Vector3f right = inv.transformDirection(new Vector3f(1.0F, 0.0F, 0.0F)).normalize();
                Vector3f up = inv.transformDirection(new Vector3f(0.0F, 1.0F, 0.0F)).normalize();
                VfxDraw.glow(buf, m, sole, new Vec3(right), new Vec3(up), 0.3D, jet[0], jet[1], jet[2], 0.55F * flick);
                Vector3f w = m.transformPosition(new Vector3f(0.0F, 12.2F / 16.0F, 0.0F));
                toWorld.transform(w);
                Vector3f d = m.transformDirection(new Vector3f(0.0F, 1.0F, 0.0F));
                toWorld.transform(d);
                out[i * 2] = cam.getPosition().add(w.x, w.y, w.z);
                out[i * 2 + 1] = new Vec3(d.x, d.y, d.z).normalize();
                pose.popPose();
            }
        } finally {
            VfxDraw.end(buf);
        }
        FEET.put(player.getId(), out);
        SEEN.put(player.getId(), player.tickCount);
    }
}
