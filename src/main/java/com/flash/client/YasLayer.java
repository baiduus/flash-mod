package com.flash.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.Random;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Matrix4f;

/**
 * Yas's body: white and yellow lightning crawling over the skin (a new shape every few ticks) and the eyes
 * glowing white. Also drawn on the player while the transcendence lights him up.
 */
public class YasLayer<T extends LivingEntity, M extends HumanoidModel<T>> extends RenderLayer<T, M> {

    /** Each part: its box in model pixels (x0, y0, z0, x1, y1, z1), from the part's pivot. */
    private static final float[][] BOXES = {
            {-4, -8, -4, 4, 0, 4}, {-4, 0, -2, 4, 12, 2}, {-3, -2, -2, 1, 10, 2}, {-1, -2, -2, 3, 10, 2},
            {-2, 0, -2, 2, 12, 2}, {-2, 0, -2, 2, 12, 2}};

    public YasLayer(RenderLayerParent<T, M> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, T entity, float limbSwing,
                       float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (entity.isInvisible() || !(YasClient.isYas(entity) || YasClient.ascending(entity))) {
            return;
        }
        M m = this.getParentModel();
        VertexConsumer c = buffers.getBuffer(RenderType.lightning());
        ModelPart[] parts = {m.head, m.body, m.rightArm, m.leftArm, m.rightLeg, m.leftLeg};
        Random r = new Random((long) (ageInTicks / 3.0F) * 31337L + entity.getId());
        for (int i = 0; i < parts.length; i++) {
            if (r.nextInt(3) == 0) {
                continue;
            }
            pose.pushPose();
            parts[i].translateAndRotate(pose);
            crawl(c, pose.last().pose(), r, BOXES[i]);
            pose.popPose();
        }
        // the eyes always shine
        pose.pushPose();
        m.head.translateAndRotate(pose);
        eyes(c, pose.last().pose(), ageInTicks);
        pose.popPose();
    }

    /** A jagged bolt over one face of the part's box, a little outside the skin. */
    private static void crawl(VertexConsumer c, Matrix4f m, Random r, float[] b) {
        int face = r.nextInt(4);
        float inflate = 0.35F;
        float[] p0 = point(r, b, face, inflate);
        float[] p1 = point(r, b, face, inflate);
        boolean white = r.nextBoolean();
        float[] col = white ? new float[] {1.0F, 1.0F, 1.0F} : YasClient.PALE_YELLOW;
        float px = p0[0];
        float py = p0[1];
        float pz = p0[2];
        for (int s = 1; s <= 5; s++) {
            float t = s / 5.0F;
            float nx = p0[0] + (p1[0] - p0[0]) * t + (s < 5 ? (r.nextFloat() - 0.5F) * 1.6F : 0.0F);
            float ny = p0[1] + (p1[1] - p0[1]) * t + (s < 5 ? (r.nextFloat() - 0.5F) * 1.6F : 0.0F);
            float nz = p0[2] + (p1[2] - p0[2]) * t;
            seg(c, m, px, py, pz, nx, ny, nz, 0.7F, col, 0.45F);
            seg(c, m, px, py, pz, nx, ny, nz, 0.28F, new float[] {1.0F, 1.0F, 1.0F}, 0.95F);
            px = nx;
            py = ny;
            pz = nz;
        }
    }

    private static float[] point(Random r, float[] b, int face, float inflate) {
        float x = b[0] + r.nextFloat() * (b[3] - b[0]);
        float y = b[1] + r.nextFloat() * (b[4] - b[1]);
        float z = b[2] + r.nextFloat() * (b[5] - b[2]);
        switch (face) {
            case 0 -> z = b[2] - inflate;
            case 1 -> z = b[5] + inflate;
            case 2 -> x = b[0] - inflate;
            default -> x = b[3] + inflate;
        }
        return new float[] {x, y, z};
    }

    /** A segment as two crossed ribbons, in pixels. */
    private static void seg(VertexConsumer c, Matrix4f m, float x0, float y0, float z0, float x1, float y1, float z1,
                            float w, float[] col, float a) {
        float k = 1.0F / 16.0F;
        float hw = w * 0.5F;
        float[][] offs = {{hw, 0.0F, 0.0F}, {0.0F, 0.0F, hw}, {0.0F, hw, 0.0F}};
        for (float[] o : offs) {
            for (int face = 0; face < 2; face++) {
                float sg = face == 0 ? 1.0F : -1.0F;
                v(c, m, (x0 - o[0] * sg) * k, (y0 - o[1] * sg) * k, (z0 - o[2] * sg) * k, col, a);
                v(c, m, (x1 - o[0] * sg) * k, (y1 - o[1] * sg) * k, (z1 - o[2] * sg) * k, col, a);
                v(c, m, (x1 + o[0] * sg) * k, (y1 + o[1] * sg) * k, (z1 + o[2] * sg) * k, col, a);
                v(c, m, (x0 + o[0] * sg) * k, (y0 + o[1] * sg) * k, (z0 + o[2] * sg) * k, col, a);
            }
        }
    }

    /** The eyes: two white glowing slits on the front of the head, with a soft halo. */
    private static void eyes(VertexConsumer c, Matrix4f m, float age) {
        float pulse = 0.85F + 0.15F * (float) Math.sin(age * 0.25F);
        float z = -4.06F / 16.0F;
        for (float x0 : new float[] {-3.0F, 1.0F}) {
            rect(c, m, x0, -4.0F, x0 + 2.0F, -3.0F, z, new float[] {1.0F, 1.0F, 1.0F}, pulse);
            rect(c, m, x0 - 0.6F, -4.6F, x0 + 2.6F, -2.4F, z - 0.002F, new float[] {1.0F, 1.0F, 0.92F},
                    0.35F * pulse);
        }
    }

    private static void rect(VertexConsumer c, Matrix4f m, float x0, float y0, float x1, float y1, float z,
                             float[] col, float a) {
        float k = 1.0F / 16.0F;
        v(c, m, x0 * k, y0 * k, z, col, a);
        v(c, m, x1 * k, y0 * k, z, col, a);
        v(c, m, x1 * k, y1 * k, z, col, a);
        v(c, m, x0 * k, y1 * k, z, col, a);
        v(c, m, x0 * k, y1 * k, z, col, a);
        v(c, m, x1 * k, y1 * k, z, col, a);
        v(c, m, x1 * k, y0 * k, z, col, a);
        v(c, m, x0 * k, y0 * k, z, col, a);
    }

    private static void v(VertexConsumer c, Matrix4f m, float x, float y, float z, float[] col, float a) {
        c.vertex(m, x, y, z).color(col[0], col[1], col[2], a).endVertex();
    }
}
