package com.flash.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * The Tachyon Amplifier on the chest (redone from scratch, textures from tools/gen_amplifier_v2.py): a chest reactor
 * on a harness. An armoured back plate, an octagonal accelerator ring around a recessed core that glows and
 * breathes, a tachyon rotor spinning in front of the core (faster the faster the wearer runs), two capacitor tubes
 * on the sides and straps over the shoulders to the back. The core takes the colour opposite to the wearer's
 * lightning: positive -> red, negative -> yellow, V-9 -> blue.
 */
public class FlashAmplifierLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

    private static final ResourceLocation POSITIVE = new ResourceLocation("flash", "textures/misc/tachyon_amp_positive.png");
    private static final ResourceLocation NEGATIVE = new ResourceLocation("flash", "textures/misc/tachyon_amp_negative.png");
    private static final ResourceLocation V9 = new ResourceLocation("flash", "textures/misc/tachyon_amp_v9.png");

    private static final int FULL_BRIGHT = 0xF000F0;
    private static final float TEX_W = 32.0F;
    private static final float TEX_H = 16.0F;

    // single texels of the atlas (u, v)
    private static final int[] STEEL_D = {16, 0};
    private static final int[] STEEL = {17, 0};
    private static final int[] STEEL_L = {18, 0};
    private static final int[] GLOW = {19, 0};
    private static final int[] HOT = {20, 0};
    private static final int[] STRAP = {21, 0};
    private static final int[] GLOW_DARK = {22, 0};
    private static final int[] STEEL_XL = {23, 0};

    public FlashAmplifierLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player,
                       float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw,
                       float headPitch) {
        if (!FlashClientRing.hasAmplifier(player) || player.isInvisible() || FlashSpeedsterPose.isPosing(player)) {
            return; // (the speedster run draws it on its animated torso)
        }
        pose.pushPose();
        this.getParentModel().body.translateAndRotate(pose);
        draw(player, pose, buffers, light, partialTick);
        pose.popPose();
    }

    /** The device, the pose already at the torso (the body part, or the torso bone of the speedster run). */
    public static void draw(AbstractClientPlayer player, PoseStack pose, MultiBufferSource buffers, int light,
                            float partialTick) {
        // the front of the chest: the suit hugs the body (+0.28), other armour is inflated by 1.0, the bare
        // skin's jacket layer by 0.25
        net.minecraft.world.item.ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
        float inflate;
        if (chest.getItem() instanceof com.flash.item.FlashSuitItem) {
            inflate = 0.28F;
        } else if (chest.getItem() instanceof net.minecraft.world.item.ArmorItem) {
            inflate = 1.0F;
        } else {
            inflate = 0.25F;
        }
        float s = -2.0F - inflate - 0.01F;
        float back = 2.0F + inflate + 0.01F;
        float top = -inflate - 0.01F;
        ResourceLocation texture = FlashClientVisualLevels.isV9(player) ? V9
                : FlashClientVisualLevels.isNegative(player) ? NEGATIVE : POSITIVE;
        float time = player.tickCount + partialTick;
        float pulse = 0.7F + 0.3F * (0.5F + 0.5F * Mth.sin(time * 0.3F));
        double speed = player.position().subtract(player.xo, player.yo, player.zo).length();

        pose.pushPose();
        VertexConsumer c = buffers.getBuffer(RenderType.entityCutoutNoCull(texture));
        int glow = (int) (255 * pulse);

        // harness: straps up the chest, over the shoulders and down the back, with a buckle behind
        for (int side = -1; side <= 1; side += 2) {
            float x0 = side < 0 ? -3.1F : 2.3F;
            float x1 = x0 + 0.8F;
            box(pose, c, light, 255, x0, top, s - 0.2F, x1, 1.4F, s + 0.05F, STRAP, STRAP);
            box(pose, c, light, 255, x0, top - 0.25F, s - 0.2F, x1, top, back + 0.2F, STRAP, STRAP);
            box(pose, c, light, 255, x0, top, back - 0.05F, x1, 5.0F, back + 0.2F, STRAP, STRAP);
        }
        box(pose, c, light, 255, -3.1F, 4.4F, back - 0.05F, 3.1F, 5.2F, back + 0.25F, STRAP, STRAP);
        box(pose, c, light, 255, -0.9F, 4.2F, back + 0.2F, 0.9F, 5.4F, back + 0.45F, STEEL_L, STEEL_D);

        // back plate (8 x 8 texels on 6.6 x 6.6 px), half a pixel thick
        boxUv(pose, c, light, 255, -3.3F, 0.7F, s - 0.5F, 3.3F, 7.3F, s, 0, 0, 8, 8, STEEL_D);
        // the octagonal accelerator ring: four bars and four cut corners
        float z0 = s - 1.25F;
        float z1 = s - 0.5F;
        box(pose, c, light, 255, -1.5F, 1.2F, z0, 1.5F, 2.0F, z1, STEEL_L, STEEL_D);
        box(pose, c, light, 255, -1.5F, 6.0F, z0, 1.5F, 6.8F, z1, STEEL_L, STEEL_D);
        box(pose, c, light, 255, -2.8F, 2.5F, z0, -2.0F, 5.5F, z1, STEEL_L, STEEL_D);
        box(pose, c, light, 255, 2.0F, 2.5F, z0, 2.8F, 5.5F, z1, STEEL_L, STEEL_D);
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sy = -1; sy <= 1; sy += 2) {
                pose.pushPose();
                pose.translate(sx * 2.0F / 16.0F, (4.0F + sy * 2.1F) / 16.0F, 0.0F);
                pose.mulPose(Axis.ZP.rotationDegrees(sx * sy * 45.0F));
                box(pose, c, light, 255, -0.75F, -0.4F, z0, 0.75F, 0.4F, z1, STEEL_XL, STEEL_D);
                pose.popPose();
            }
        }
        // the glowing channel inside the ring
        box(pose, c, FULL_BRIGHT, glow, -2.0F, 2.0F, s - 0.7F, 2.0F, 6.0F, s - 0.5F, GLOW_DARK, GLOW_DARK);
        // the core, standing out of the channel
        boxUv(pose, c, FULL_BRIGHT, glow, -1.2F, 2.8F, s - 1.0F, 1.2F, 5.2F, s - 0.7F, 10, 0, 4, 4, GLOW);
        // the tachyon rotor: two thin blades spinning in front of the core
        float spin = time * (6.0F + (float) Math.min(40.0D, speed * 60.0D));
        pose.pushPose();
        pose.translate(0.0F, 4.0F / 16.0F, 0.0F);
        pose.mulPose(Axis.ZP.rotationDegrees(spin));
        for (int blade = 0; blade < 2; blade++) {
            pose.mulPose(Axis.ZP.rotationDegrees(90.0F * blade));
            box(pose, c, FULL_BRIGHT, 255, -1.7F, -0.18F, s - 1.2F, 1.7F, 0.18F, s - 1.05F, HOT, HOT);
        }
        pose.popPose();
        // the hub of the rotor
        box(pose, c, light, 255, -0.35F, 3.65F, s - 1.35F, 0.35F, 4.35F, s - 1.1F, STEEL_XL, STEEL_D);
        // the two capacitor tubes on the sides, with steel caps
        for (int sx = -1; sx <= 1; sx += 2) {
            float x0 = sx < 0 ? -3.35F : 2.85F;
            boxUv(pose, c, FULL_BRIGHT, glow, x0, 2.4F, s - 1.0F, x0 + 0.5F, 5.6F, s - 0.5F, 24, 0, 2, 6, GLOW);
            box(pose, c, light, 255, x0 - 0.1F, 1.9F, s - 1.1F, x0 + 0.6F, 2.4F, s - 0.45F, STEEL_L, STEEL_D);
            box(pose, c, light, 255, x0 - 0.1F, 5.6F, s - 1.1F, x0 + 0.6F, 6.1F, s - 0.45F, STEEL_L, STEEL_D);
        }
        pose.popPose();
    }

    /** A box in model pixels, every face the single texel {@code front} (front face) or {@code side}. */
    private static void box(PoseStack stack, VertexConsumer c, int light, int bright, float x0, float y0, float z0,
                            float x1, float y1, float z1, int[] front, int[] side) {
        float fu = (front[0] + 0.5F) / TEX_W;
        float fv = (front[1] + 0.5F) / TEX_H;
        faces(stack, c, light, bright, x0, y0, z0, x1, y1, z1, fu, fv, fu, fv, side);
    }

    /** A box whose front shows the {@code tw} x {@code th} texels at ({@code u}, {@code v}). */
    private static void boxUv(PoseStack stack, VertexConsumer c, int light, int bright, float x0, float y0, float z0,
                              float x1, float y1, float z1, int u, int v, int tw, int th, int[] side) {
        faces(stack, c, light, bright, x0, y0, z0, x1, y1, z1, u / TEX_W, v / TEX_H, (u + tw) / TEX_W,
                (v + th) / TEX_H, side);
    }

    private static void faces(PoseStack stack, VertexConsumer c, int light, int bright, float x0, float y0, float z0,
                              float x1, float y1, float z1, float fu0, float fv0, float fu1, float fv1, int[] side) {
        PoseStack.Pose pose = stack.last();
        Matrix4f m = pose.pose();
        Matrix3f n = pose.normal();
        float k = 1.0F / 16.0F;
        float ax = x0 * k, bx = x1 * k, ay = y0 * k, by = y1 * k, az = z0 * k, bz = z1 * k;
        float su = (side[0] + 0.5F) / TEX_W;
        float sv = (side[1] + 0.5F) / TEX_H;
        // front (-z), back (+z), the four sides
        quad(c, m, n, light, bright, ax, ay, az, bx, ay, az, bx, by, az, ax, by, az, fu0, fv0, fu1, fv1, 0, 0, -1);
        quad(c, m, n, light, bright, bx, ay, bz, ax, ay, bz, ax, by, bz, bx, by, bz, su, sv, su, sv, 0, 0, 1);
        quad(c, m, n, light, bright, ax, ay, bz, ax, ay, az, ax, by, az, ax, by, bz, su, sv, su, sv, -1, 0, 0);
        quad(c, m, n, light, bright, bx, ay, az, bx, ay, bz, bx, by, bz, bx, by, az, su, sv, su, sv, 1, 0, 0);
        quad(c, m, n, light, bright, ax, ay, bz, bx, ay, bz, bx, ay, az, ax, ay, az, su, sv, su, sv, 0, -1, 0);
        quad(c, m, n, light, bright, ax, by, az, bx, by, az, bx, by, bz, ax, by, bz, su, sv, su, sv, 0, 1, 0);
    }

    /** Corners in order: top-left, top-right, bottom-right, bottom-left of the texture rect. */
    private static void quad(VertexConsumer c, Matrix4f m, Matrix3f n, int light, int bright,
                             float x1, float y1, float z1, float x2, float y2, float z2,
                             float x3, float y3, float z3, float x4, float y4, float z4,
                             float u0, float v0, float u1, float v1, float nx, float ny, float nz) {
        vertex(c, m, n, light, bright, x1, y1, z1, u0, v0, nx, ny, nz);
        vertex(c, m, n, light, bright, x2, y2, z2, u1, v0, nx, ny, nz);
        vertex(c, m, n, light, bright, x3, y3, z3, u1, v1, nx, ny, nz);
        vertex(c, m, n, light, bright, x4, y4, z4, u0, v1, nx, ny, nz);
    }

    private static void vertex(VertexConsumer c, Matrix4f m, Matrix3f n, int light, int bright, float x, float y,
                               float z, float u, float v, float nx, float ny, float nz) {
        c.vertex(m, x, y, z).color(bright, bright, bright, 255).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(light).normal(n, nx, ny, nz).endVertex();
    }
}
