package com.flash.client;

import com.flash.FlashMod;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Cloth cape in the style of the Man of Tomorrow mod (its CapeShape6 / CapeCloth23): a 24 x 12 sheet of 1.37 blocks,
 * the top three rows sewn to the shoulders, the rest simulated in world space (gravity, air drag that makes it billow
 * and stream behind when flying, inertia when speeding up or turning), kept out of the head, body, arms and legs and
 * lying on the ground. Drawn with thickness: outside, lining and hems.
 */
@Mod.EventBusSubscriber(modid = FlashMod.MODID, value = Dist.CLIENT)
public final class SupermanCape {

    static final int ROWS = 24;
    static final int COLS = 12;
    private static final int NR = ROWS + 1;
    private static final int NC = COLS + 1;
    private static final int N = NR * NC;
    static final float LENGTH = 1.3685F;
    private static final int PINNED_ROWS = 3;
    static final float GRAVITY = 0.045F;
    static final float WIND_CAP = 2.6F;
    static final float CN = 0.3F;
    static final float CT = 0.03F;
    static final float RETAIN_REST = 0.925F;
    static final float RETAIN_FAST = 0.95F;
    static final float INERTIA = 0.2F;
    static final float INERTIA_CAP = 0.3F;
    static final float THICKNESS = 0.015F;
    static final float GROUND_GAP = 0.012F;
    static final float GROUND_FRICTION = 0.4F;
    static final float SUIT_GAP = 0.03F;
    static final float SHEAR = 0.75F;
    static final float BEND_ROW = 0.45F;
    static final float BEND_COL = 0.3F;
    private static final int ITERATIONS = 6;
    private static final float SUBSTEP = 0.25F;

    /** Rest shape in cape space (body space moved 0.02 down and 0.11 back; y down, +z behind the back). */
    private static final float[] REST = new float[N * 3];
    private static int[] la;
    private static int[] lb;
    private static float[] ll;
    private static float[] ls;

    /** Collision boxes (pixels, in the part's own space): x0, y0, z0, x1, y1, z1. */
    private static final float[][] BOXES = {
            {-4, -8, -4, 4, 0, 4}, // head
            {-4, 0, -2, 4, 12, 2}, // body
            {-3, -2, -2, 1, 10, 2}, // right arm
            {-1, -2, -2, 3, 10, 2}, // left arm
            {-2, 0, -2, 2, 12, 2}, // right leg
            {-2, 0, -2, 2, 12, 2}}; // left leg

    private static final Matrix4f VIEW_INV = new Matrix4f();
    private static boolean viewValid;

    static {
        restShape();
        buildLinks();
    }

    private static final class State {
        final float[] p = new float[N * 3];
        final float[] o = new float[N * 3];
        final float[] pins = new float[N * 3];
        double ex;
        double ey;
        double ez;
        float vx;
        float vy;
        float vz;
        float last = Float.NaN;
    }

    private static final Map<AbstractClientPlayer, State> STATES = new WeakHashMap<>();

    private SupermanCape() {
    }

    // ------------------------------------------------------------------
    // Rest shape and links
    // ------------------------------------------------------------------

    private static float smooth(float t) {
        return t * t * (3.0F - 2.0F * t);
    }

    /** CapeShape6.make for the hanging cape (direction straight down, no tension, time 0). */
    private static void restShape() {
        float fx = 0.0F;
        float fy = 1.0F;
        float fz = 0.16F;
        float len = Mth.sqrt(fx * fx + fy * fy + fz * fz);
        fx /= len;
        fy /= len;
        fz /= len;
        float px = 0.0F;
        float py = 0.0F;
        float pz = 0.036F;
        for (int r = 0; r <= ROWS; r++) {
            float t = r / (float) ROWS;
            float bendT = smooth(Mth.clamp((t - 0.05F) / 0.58F, 0.0F, 1.0F));
            float dx = fx * bendT;
            float dy = 1.0F + (fy - 1.0F) * bendT;
            float dz = 0.13F + (fz - 0.13F) * bendT;
            float dl = Mth.sqrt(dx * dx + dy * dy + dz * dz);
            dx /= dl;
            dy /= dl;
            dz /= dl;
            if (r > 0) {
                px += dx * LENGTH / ROWS;
                py += dy * LENGTH / ROWS;
                pz += dz * LENGTH / ROWS;
            }
            float inv = 1.0F / Math.max(1.0E-4F, 1.0F + dy);
            float rx = 1.0F - dx * dx * inv;
            float ry = -dx;
            float rz = -dx * dz * inv;
            float nx = ry * dz - rz * dy;
            float ny = rz * dx - rx * dz;
            float nz = rx * dy - ry * dx;
            float halfWidth = 0.37F + 0.145F * smooth(Mth.clamp(t / 0.36F, 0.0F, 1.0F)) + 0.02F * t;
            float waveAmp = 0.02F * t * t;
            for (int c = 0; c <= COLS; c++) {
                float u = c / (float) COLS;
                float side = u * 2.0F - 1.0F;
                float wave = (float) (Math.sin(-t * 6.8D + side * 0.8D) + 0.22D * Math.sin(-t * 9.2D - side * 1.1D))
                        * waveAmp;
                float ripple = (float) Math.cos(u * Math.PI * 4.0D - t * 1.4D) * 0.006F * t * t;
                int k = (r * NC + c) * 3;
                REST[k] = px + rx * side * halfWidth + nx * (wave + ripple);
                REST[k + 1] = py + ry * side * halfWidth + ny * (wave + ripple);
                REST[k + 2] = pz + rz * side * halfWidth + nz * (wave + ripple);
            }
        }
    }

    private static void link(List<float[]> out, int r0, int c0, int r1, int c1, float stiffness) {
        if (r1 < 0 || r1 > ROWS || c1 < 0 || c1 > COLS) {
            return;
        }
        int a = r0 * NC + c0;
        int b = r1 * NC + c1;
        if (r0 < PINNED_ROWS && r1 < PINNED_ROWS) {
            return; // both sewn on
        }
        float dx = REST[a * 3] - REST[b * 3];
        float dy = REST[a * 3 + 1] - REST[b * 3 + 1];
        float dz = REST[a * 3 + 2] - REST[b * 3 + 2];
        out.add(new float[] {a, b, Mth.sqrt(dx * dx + dy * dy + dz * dz), stiffness});
    }

    private static void buildLinks() {
        List<float[]> out = new ArrayList<>();
        for (int r = 0; r <= ROWS; r++) {
            for (int c = 0; c <= COLS; c++) {
                link(out, r, c, r, c + 1, 1.0F);
                link(out, r, c, r + 1, c, 1.0F);
                link(out, r, c, r + 1, c + 1, SHEAR);
                link(out, r, c, r + 1, c - 1, SHEAR);
                link(out, r, c, r + 2, c, BEND_ROW);
                link(out, r, c, r, c + 2, BEND_COL);
            }
        }
        la = new int[out.size()];
        lb = new int[out.size()];
        ll = new float[out.size()];
        ls = new float[out.size()];
        for (int i = 0; i < out.size(); i++) {
            float[] l = out.get(i);
            la[i] = (int) l[0];
            lb[i] = (int) l[1];
            ll[i] = l[2];
            ls[i] = l[3];
        }
    }

    // ------------------------------------------------------------------
    // View capture (world rotation of the level pose stack)
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_SKY) {
            VIEW_INV.set(event.getPoseStack().last().pose()).invert();
            viewValid = true;
        }
    }

    private static Matrix4f viewInverse() {
        if (viewValid) {
            return VIEW_INV;
        }
        Camera cam = Minecraft.getInstance().gameRenderer.getMainCamera();
        return new Matrix4f().rotationX(cam.getXRot() * Mth.DEG_TO_RAD)
                .rotateY((cam.getYRot() + 180.0F) * Mth.DEG_TO_RAD).invert();
    }

    private static boolean preview(Matrix4f m) {
        Vector3f col = new Vector3f();
        for (int i = 0; i < 3; i++) {
            m.getColumn(i, col);
            float len = col.length();
            if (!Float.isFinite(len) || len < 0.2F || len > 4.0F) {
                return true;
            }
        }
        return false;
    }

    private static Matrix4f partMatrix(ModelPart part) {
        PoseStack ps = new PoseStack();
        part.translateAndRotate(ps);
        return new Matrix4f(ps.last().pose());
    }

    // ------------------------------------------------------------------
    // Render
    // ------------------------------------------------------------------

    /** Draws the cape; {@code pose} is the player's model space (as in a render layer). */
    public static void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player,
                              PlayerModel<AbstractClientPlayer> parent, float partial, ResourceLocation texture) {
        Matrix4f model = pose.last().pose();
        Matrix4f capeToRoot = partMatrix(parent.body).translate(0.0F, 0.02F, 0.11F);
        float[] root = new float[N * 3];
        if (preview(model)) {
            transform(capeToRoot, REST, root);
        } else {
            simulate(player, parent, partial, model, capeToRoot, root);
        }
        draw(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(texture)), light, root);
    }

    /** The cape hanging still (a suit on the armour stand). */
    public static void renderRest(PoseStack pose, MultiBufferSource buffers, int light,
                                  net.minecraft.client.model.HumanoidModel<?> parent, ResourceLocation texture) {
        Matrix4f capeToRoot = partMatrix(parent.body).translate(0.0F, 0.02F, 0.11F);
        float[] root = new float[N * 3];
        transform(capeToRoot, REST, root);
        draw(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(texture)), light, root);
    }

    private static void transform(Matrix4f m, float[] in, float[] out) {
        Vector3f v = new Vector3f();
        for (int i = 0; i < N; i++) {
            m.transformPosition(in[i * 3], in[i * 3 + 1], in[i * 3 + 2], v);
            out[i * 3] = v.x;
            out[i * 3 + 1] = v.y;
            out[i * 3 + 2] = v.z;
        }
    }

    private static void simulate(AbstractClientPlayer player, PlayerModel<AbstractClientPlayer> parent, float partial,
                                 Matrix4f model, Matrix4f capeToRoot, float[] root) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        double ex = Mth.lerp(partial, player.xo, player.getX());
        double ey = Mth.lerp(partial, player.yo, player.getY());
        double ez = Mth.lerp(partial, player.zo, player.getZ());
        // model space -> world space relative to the player
        Matrix4f rootToRel = new Matrix4f().translation((float) (cam.x - ex), (float) (cam.y - ey),
                (float) (cam.z - ez)).mul(viewInverse()).mul(model);
        Matrix4f relToRoot = new Matrix4f(rootToRel).invert();
        Matrix4f capeToRel = new Matrix4f(rootToRel).mul(capeToRoot);
        State s = STATES.computeIfAbsent(player, k -> new State());
        float now = player.tickCount + partial;
        float dt = now - s.last;
        float[] pins = new float[N * 3];
        transform(capeToRel, REST, pins);
        if (Float.isNaN(s.last) || dt < 0.0F || dt > 10.0F || level == null) {
            System.arraycopy(pins, 0, s.p, 0, pins.length);
            System.arraycopy(pins, 0, s.o, 0, pins.length);
            System.arraycopy(pins, 0, s.pins, 0, pins.length);
            s.ex = ex;
            s.ey = ey;
            s.ez = ez;
            s.vx = s.vy = s.vz = 0.0F;
            s.last = now;
        } else if (dt > 1.0E-4F) {
            dt = Math.min(dt, 2.0F);
            step(s, level, parent, rootToRel, relToRoot, pins, ex, ey, ez, dt);
            s.last = now;
        }
        transform(relToRoot, s.p, root);
        // the sewn rows follow the shoulders exactly
        float[] rest = new float[N * 3];
        transform(capeToRoot, REST, rest);
        System.arraycopy(rest, 0, root, 0, PINNED_ROWS * NC * 3);
        for (float f : root) {
            if (!Float.isFinite(f)) {
                s.last = Float.NaN;
                System.arraycopy(rest, 0, root, 0, root.length);
                return;
            }
        }
    }

    private static void step(State s, ClientLevel level, PlayerModel<AbstractClientPlayer> parent, Matrix4f rootToRel,
                             Matrix4f relToRoot, float[] pins, double ex, double ey, double ez, float dt) {
        float[] p = s.p;
        float[] o = s.o;
        // the cloth is kept relative to the player: move it back by how far the player went
        float mx = (float) (ex - s.ex);
        float my = (float) (ey - s.ey);
        float mz = (float) (ez - s.ez);
        float vx = mx / dt;
        float vy = my / dt;
        float vz = mz / dt;
        float speed = Mth.sqrt(vx * vx + vy * vy + vz * vz);
        if (speed > 12.0F) {
            // teleport
            System.arraycopy(pins, 0, p, 0, p.length);
            System.arraycopy(pins, 0, o, 0, o.length);
        } else {
            // inertia: a part of a change of speed is felt by the cape
            float ax = (vx - s.vx) * INERTIA;
            float ay = (vy - s.vy) * INERTIA;
            float az = (vz - s.vz) * INERTIA;
            float al = Mth.sqrt(ax * ax + ay * ay + az * az);
            if (al > INERTIA_CAP) {
                ax *= INERTIA_CAP / al;
                ay *= INERTIA_CAP / al;
                az *= INERTIA_CAP / al;
            }
            int n0 = PINNED_ROWS * NC;
            float h0 = dt / Math.max(1, Math.min(8, (int) Math.ceil(dt / SUBSTEP)));
            for (int i = n0; i < N; i++) {
                o[i * 3] += ax * h0;
                o[i * 3 + 1] += ay * h0;
                o[i * 3 + 2] += az * h0;
            }
        }
        s.ex = ex;
        s.ey = ey;
        s.ez = ez;
        s.vx = vx;
        s.vy = vy;
        s.vz = vz;
        // the air seen by the cape
        float wx = -vx;
        float wy = -vy;
        float wz = -vz;
        float wl = Mth.sqrt(wx * wx + wy * wy + wz * wz);
        if (wl > WIND_CAP) {
            wx *= WIND_CAP / wl;
            wy *= WIND_CAP / wl;
            wz *= WIND_CAP / wl;
        }
        float fast = Mth.clamp(speed / 1.2F, 0.0F, 1.0F);
        float retainTick = Mth.lerp(fast, RETAIN_REST, RETAIN_FAST);
        int steps = Math.max(1, Math.min(8, (int) Math.ceil(dt / SUBSTEP)));
        float h = dt / steps;
        float retain = (float) Math.pow(retainTick, h);
        float[] normals = new float[N * 3];
        float[] prevPins = s.pins;
        float time = s.last;
        Map<Long, Float> ground = new HashMap<>();
        Matrix4f[] partInv = new Matrix4f[6];
        Matrix4f[] partMat = new Matrix4f[6];
        ModelPart[] parts = {parent.head, parent.body, parent.rightArm, parent.leftArm, parent.rightLeg, parent.leftLeg};
        for (int i = 0; i < 6; i++) {
            partMat[i] = partMatrix(parts[i]);
            partInv[i] = new Matrix4f(partMat[i]).invert();
        }
        int n0 = PINNED_ROWS * NC;
        for (int k = 0; k < steps; k++) {
            float a = (k + 1) / (float) steps;
            for (int i = 0; i < n0 * 3; i++) {
                o[i] = p[i];
                p[i] = Mth.lerp(a, prevPins[i], pins[i]);
            }
            normals(p, normals);
            time += h;
            for (int i = n0; i < N; i++) {
                int j = i * 3;
                float vx2 = (p[j] - o[j]) * retain;
                float vy2 = (p[j + 1] - o[j + 1]) * retain;
                float vz2 = (p[j + 2] - o[j + 2]) * retain;
                // air drag: strong across the cloth, weak along it (the cape catches the wind)
                float rx = wx * h - vx2;
                float ry = wy * h - vy2;
                float rz = wz * h - vz2;
                float nx = normals[j];
                float ny = normals[j + 1];
                float nz = normals[j + 2];
                float dn = rx * nx + ry * ny + rz * nz;
                float tx = rx - nx * dn;
                float ty = ry - ny * dn;
                float tz = rz - nz * dn;
                vx2 += (nx * dn * CN + tx * CT) * h;
                vy2 += (ny * dn * CN + ty * CT) * h;
                vz2 += (nz * dn * CN + tz * CT) * h;
                // a little flutter in the wind, more at the hem
                int r = i / NC;
                float t = r / (float) ROWS;
                float turb = (8.0E-4F + 0.016F * Math.min(wl, WIND_CAP)) * t * h;
                float ph = time * 1.9F + (i % NC) * 0.7F - r * 0.45F;
                vx2 += Mth.sin(ph) * turb * nx;
                vy2 += Mth.sin(ph * 1.3F) * turb * ny;
                vz2 += Mth.cos(ph) * turb * nz;
                vy2 -= GRAVITY * h * h;
                o[j] = p[j];
                o[j + 1] = p[j + 1];
                o[j + 2] = p[j + 2];
                p[j] += vx2;
                p[j + 1] += vy2;
                p[j + 2] += vz2;
            }
            for (int it = 0; it < ITERATIONS; it++) {
                solve(p, n0);
            }
            stretchLimit(p, n0);
            collideBody(p, o, n0, rootToRel, relToRoot, partMat, partInv);
            collideGround(p, o, n0, level, ex, ey, ez, ground);
        }
        System.arraycopy(pins, 0, s.pins, 0, pins.length);
    }

    private static void solve(float[] p, int n0) {
        for (int i = 0; i < la.length; i++) {
            int a = la[i];
            int b = lb[i];
            float wa = a < n0 ? 0.0F : 1.0F;
            float wb = b < n0 ? 0.0F : 1.0F;
            float w = wa + wb;
            if (w <= 0.0F) {
                continue;
            }
            int ja = a * 3;
            int jb = b * 3;
            float dx = p[jb] - p[ja];
            float dy = p[jb + 1] - p[ja + 1];
            float dz = p[jb + 2] - p[ja + 2];
            float len = Mth.sqrt(dx * dx + dy * dy + dz * dz);
            if (len < 1.0E-6F) {
                continue;
            }
            float rest = ll[i];
            // stretching is stiff, compression (folds) is soft
            float k = len > rest ? ls[i] : ls[i] * 0.35F;
            float c = (len - rest) / len * k / w;
            p[ja] += dx * c * wa;
            p[ja + 1] += dy * c * wa;
            p[ja + 2] += dz * c * wa;
            p[jb] -= dx * c * wb;
            p[jb + 1] -= dy * c * wb;
            p[jb + 2] -= dz * c * wb;
        }
    }

    /** No point gets further from its seam than the cloth allows (stops stretching when flying fast). */
    private static void stretchLimit(float[] p, int n0) {
        int top = (PINNED_ROWS - 1) * NC;
        float rowLen = LENGTH / ROWS;
        for (int i = n0; i < N; i++) {
            int r = i / NC;
            int c = i % NC;
            int a = (top + c) * 3;
            int j = i * 3;
            float max = (r - PINNED_ROWS + 1) * rowLen * 1.04F;
            float dx = p[j] - p[a];
            float dy = p[j + 1] - p[a + 1];
            float dz = p[j + 2] - p[a + 2];
            float len = Mth.sqrt(dx * dx + dy * dy + dz * dz);
            if (len > max && len > 1.0E-6F) {
                float f = max / len;
                p[j] = p[a] + dx * f;
                p[j + 1] = p[a + 1] + dy * f;
                p[j + 2] = p[a + 2] + dz * f;
            }
        }
    }

    private static void collideBody(float[] p, float[] o, int n0, Matrix4f rootToRel, Matrix4f relToRoot,
                                    Matrix4f[] partMat, Matrix4f[] partInv) {
        Vector3f v = new Vector3f();
        for (int i = n0; i < N; i++) {
            int j = i * 3;
            relToRoot.transformPosition(p[j], p[j + 1], p[j + 2], v);
            boolean moved = false;
            for (int b = 0; b < 6; b++) {
                float[] box = BOXES[b];
                partInv[b].transformPosition(v.x, v.y, v.z, v);
                float g = SUIT_GAP;
                float x0 = box[0] / 16.0F - g;
                float y0 = box[1] / 16.0F - g;
                float z0 = box[2] / 16.0F - g;
                float x1 = box[3] / 16.0F + g;
                float y1 = box[4] / 16.0F + g;
                float z1 = box[5] / 16.0F + g;
                if (v.x > x0 && v.x < x1 && v.y > y0 && v.y < y1 && v.z > z0 && v.z < z1) {
                    // out through the nearest face (the back counts as nearer: the cape belongs behind)
                    float px0 = v.x - x0;
                    float px1 = x1 - v.x;
                    float pz0 = (v.z - z0) * 1.6F;
                    float pz1 = (z1 - v.z) * 0.6F;
                    float py0 = b == 0 ? v.y - y0 : 99.0F;
                    float py1 = b == 0 ? y1 - v.y : 99.0F;
                    float m = Math.min(Math.min(Math.min(px0, px1), Math.min(pz0, pz1)), Math.min(py0, py1));
                    if (m == px0) {
                        v.x = x0;
                    } else if (m == px1) {
                        v.x = x1;
                    } else if (m == pz1) {
                        v.z = z1;
                    } else if (m == pz0) {
                        v.z = z0;
                    } else if (m == py0) {
                        v.y = y0;
                    } else {
                        v.y = y1;
                    }
                    moved = true;
                }
                partMat[b].transformPosition(v.x, v.y, v.z, v);
            }
            if (moved) {
                rootToRel.transformPosition(v.x, v.y, v.z, v);
                float dx = v.x - p[j];
                float dy = v.y - p[j + 1];
                float dz = v.z - p[j + 2];
                p[j] = v.x;
                p[j + 1] = v.y;
                p[j + 2] = v.z;
                // slide along the body (half of the push kept as speed)
                o[j] += dx * 0.5F;
                o[j + 1] += dy * 0.5F;
                o[j + 2] += dz * 0.5F;
            }
        }
    }

    private static void collideGround(float[] p, float[] o, int n0, ClientLevel level, double ex, double ey, double ez,
                                      Map<Long, Float> cache) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int i = n0; i < N; i++) {
            int j = i * 3;
            double wx = ex + p[j];
            double wy = ey + p[j + 1];
            double wz = ez + p[j + 2];
            pos.set(Mth.floor(wx), Mth.floor(wy), Mth.floor(wz));
            float top = top(level, pos, cache);
            if (Float.isNaN(top)) {
                continue;
            }
            float surface = (float) (pos.getY() + top + GROUND_GAP - ey);
            if (p[j + 1] < surface) {
                p[j + 1] = surface;
                if (o[j + 1] < surface) {
                    o[j + 1] = surface;
                }
                o[j] = p[j] - (p[j] - o[j]) * (1.0F - GROUND_FRICTION);
                o[j + 2] = p[j + 2] - (p[j + 2] - o[j + 2]) * (1.0F - GROUND_FRICTION);
            }
        }
    }

    /** Height of the top of the block's collision shape inside it (0..1), or NaN when empty. */
    private static float top(ClientLevel level, BlockPos pos, Map<Long, Float> cache) {
        long key = pos.asLong();
        Float cached = cache.get(key);
        if (cached != null) {
            return cached;
        }
        BlockState state = level.getBlockState(pos);
        VoxelShape shape = state.getCollisionShape(level, pos);
        float top = shape.isEmpty() ? Float.NaN : (float) shape.max(Direction.Axis.Y);
        cache.put(key, top);
        return top;
    }

    // ------------------------------------------------------------------
    // Mesh drawing
    // ------------------------------------------------------------------

    private static void normals(float[] p, float[] n) {
        for (int r = 0; r <= ROWS; r++) {
            int up = Math.max(0, r - 1) * NC;
            int dn = Math.min(ROWS, r + 1) * NC;
            int row = r * NC;
            for (int c = 0; c <= COLS; c++) {
                int k = (row + c) * 3;
                int l = (row + Math.max(0, c - 1)) * 3;
                int rr = (row + Math.min(COLS, c + 1)) * 3;
                int u = (up + c) * 3;
                int d = (dn + c) * 3;
                float ax = p[rr] - p[l];
                float ay = p[rr + 1] - p[l + 1];
                float az = p[rr + 2] - p[l + 2];
                float bx = p[d] - p[u];
                float by = p[d + 1] - p[u + 1];
                float bz = p[d + 2] - p[u + 2];
                float cx = ay * bz - az * by;
                float cy = az * bx - ax * bz;
                float cz = ax * by - ay * bx;
                float len = Mth.sqrt(cx * cx + cy * cy + cz * cz);
                if (len < 1.0E-7F) {
                    cx = 0.0F;
                    cy = 0.0F;
                    cz = 1.0F;
                    len = 1.0F;
                }
                n[k] = cx / len;
                n[k + 1] = cy / len;
                n[k + 2] = cz / len;
            }
        }
    }

    private static void draw(PoseStack pose, VertexConsumer vc, int light, float[] p) {
        float[] n = new float[N * 3];
        normals(p, n);
        Matrix4f m = pose.last().pose();
        Matrix3f nm = pose.last().normal();
        float half = THICKNESS * 0.5F;
        int overlay = OverlayTexture.NO_OVERLAY;
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                int[] q = {r * NC + c, r * NC + c + 1, (r + 1) * NC + c + 1, (r + 1) * NC + c};
                // outside (the cape art, seen from behind)
                for (int idx : q) {
                    int rr = idx / NC;
                    int cc = idx % NC;
                    vertex(vc, m, nm, p, n, idx, half, (1.0F + 10.0F * (1.0F - cc / (float) COLS)) / 64.0F,
                            (1.0F + 16.0F * rr / ROWS) / 32.0F, light, overlay, 1.0F);
                }
                // lining
                for (int x = 3; x >= 0; x--) {
                    int idx = q[x];
                    int rr = idx / NC;
                    int cc = idx % NC;
                    vertex(vc, m, nm, p, n, idx, -half, (12.0F + 10.0F * cc / (float) COLS) / 64.0F,
                            (1.0F + 16.0F * rr / ROWS) / 32.0F, light, overlay, -1.0F);
                }
            }
        }
        // hems: the two sides and the bottom
        for (int r = 0; r < ROWS; r++) {
            edge(vc, m, nm, p, n, r * NC, (r + 1) * NC, half, 0.5F / 64.0F, (1.0F + 16.0F * r / ROWS) / 32.0F,
                    (1.0F + 16.0F * (r + 1) / ROWS) / 32.0F, light, overlay);
            edge(vc, m, nm, p, n, r * NC + COLS, (r + 1) * NC + COLS, half, 11.5F / 64.0F,
                    (1.0F + 16.0F * r / ROWS) / 32.0F, (1.0F + 16.0F * (r + 1) / ROWS) / 32.0F, light, overlay);
        }
        for (int c = 0; c < COLS; c++) {
            int a = ROWS * NC + c;
            edge(vc, m, nm, p, n, a, a + 1, half, (11.0F + 10.0F * c / COLS) / 64.0F, 0.5F / 32.0F, 0.5F / 32.0F,
                    light, overlay);
        }
    }

    private static void vertex(VertexConsumer vc, Matrix4f m, Matrix3f nm, float[] p, float[] n, int idx, float off,
                               float u, float v, int light, int overlay, float sign) {
        int j = idx * 3;
        vc.vertex(m, p[j] + n[j] * off, p[j + 1] + n[j + 1] * off, p[j + 2] + n[j + 2] * off)
                .color(1.0F, 1.0F, 1.0F, 1.0F).uv(u, v).overlayCoords(overlay).uv2(light)
                .normal(nm, n[j] * sign, n[j + 1] * sign, n[j + 2] * sign).endVertex();
    }

    private static void edge(VertexConsumer vc, Matrix4f m, Matrix3f nm, float[] p, float[] n, int a, int b,
                             float half, float u, float va, float vb, int light, int overlay) {
        int ja = a * 3;
        int jb = b * 3;
        float ex = p[jb] - p[ja];
        float ey = p[jb + 1] - p[ja + 1];
        float ez = p[jb + 2] - p[ja + 2];
        float nx = ey * n[ja + 2] - ez * n[ja + 1];
        float ny = ez * n[ja] - ex * n[ja + 2];
        float nz = ex * n[ja + 1] - ey * n[ja];
        float l = Mth.sqrt(nx * nx + ny * ny + nz * nz);
        if (l < 1.0E-6F) {
            return;
        }
        nx /= l;
        ny /= l;
        nz /= l;
        float[][] pts = {
                {p[ja] + n[ja] * half, p[ja + 1] + n[ja + 1] * half, p[ja + 2] + n[ja + 2] * half, va},
                {p[jb] + n[jb] * half, p[jb + 1] + n[jb + 1] * half, p[jb + 2] + n[jb + 2] * half, vb},
                {p[jb] - n[jb] * half, p[jb + 1] - n[jb + 1] * half, p[jb + 2] - n[jb + 2] * half, vb},
                {p[ja] - n[ja] * half, p[ja + 1] - n[ja + 1] * half, p[ja + 2] - n[ja + 2] * half, va}};
        for (float[] q : pts) {
            vc.vertex(m, q[0], q[1], q[2]).color(1.0F, 1.0F, 1.0F, 1.0F).uv(u, q[3]).overlayCoords(overlay)
                    .uv2(light).normal(nm, nx, ny, nz).endVertex();
        }
    }
}
