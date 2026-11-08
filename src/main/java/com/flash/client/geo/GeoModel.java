package com.flash.client.geo;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * A Bedrock / GeckoLib geometry file ({@code *.geo.json}) drawn without GeckoLib.
 *
 * <p>Everything is converted once to the space of the vanilla entity models
 * (pixels, Y down, feet at y = 24): {@code java = (x, 24 - y, z)} and the
 * rotations keep their values (applied Z, Y, X like a vanilla ModelPart). The
 * cubes are built exactly like vanilla box-UV cubes (same faces, same UV
 * layout and the same mirror rule); per-face UV is supported too. Bones keep
 * animation offsets that {@link GeoAnimation} fills before each render.
 */
public final class GeoModel {

    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    /** One face: 4 corners (x, y, z, u, v) in pixels / normalised UV, and the normal. */
    private record Quad(float[][] v, float nx, float ny, float nz) {
    }

    private static final class Cube {
        final List<Quad> quads = new ArrayList<>();
        float px;
        float py;
        float pz;
        float rx;
        float ry;
        float rz;
        boolean rotated;
    }

    public static final class Bone {
        public final String name;
        final float px;
        final float py;
        final float pz;
        final float rx;
        final float ry;
        final float rz;
        final List<Cube> cubes = new ArrayList<>();
        final List<Bone> children = new ArrayList<>();
        /** 0 = model texture, 1 = player skin (64 x 64) subtree. */
        int group;

        // animation state (pixels / degrees / factor), reset before every frame
        public float ax;
        public float ay;
        public float az;
        public float arx;
        public float ary;
        public float arz;
        public float sx = 1.0F;
        public float sy = 1.0F;
        public float sz = 1.0F;
        /** False: the bone and its children are not drawn. */
        public boolean visible = true;
        /** True: only this bone's own cubes are skipped (the children are drawn). */
        public boolean hideCubes;
        /** Bend of the limb (playerAnimator / bendy-lib style): direction around Y and amount, in radians. */
        public float bendAxis;
        public float bendValue;

        Bone(String name, float px, float py, float pz, float rx, float ry, float rz) {
            this.name = name;
            this.px = px;
            this.py = py;
            this.pz = pz;
            this.rx = rx;
            this.ry = ry;
            this.rz = rz;
        }

        void reset() {
            ax = ay = az = 0.0F;
            arx = ary = arz = 0.0F;
            sx = sy = sz = 1.0F;
            visible = true;
            hideCubes = false;
            bendAxis = 0.0F;
            bendValue = 0.0F;
        }

        public float pivotX() {
            return px;
        }

        public float pivotY() {
            return py;
        }

        public float pivotZ() {
            return pz;
        }
    }

    private final Map<String, Bone> bones = new LinkedHashMap<>();
    private final List<Bone> roots = new ArrayList<>();
    /** Lowest and highest y (java space) of all cubes, and the centre in x / z. */
    private float minY = Float.MAX_VALUE;
    private float maxY = -Float.MAX_VALUE;
    private float minX = Float.MAX_VALUE;
    private float maxX = -Float.MAX_VALUE;
    private float minZ = Float.MAX_VALUE;
    private float maxZ = -Float.MAX_VALUE;

    private GeoModel() {
    }

    public Bone bone(String name) {
        return bones.get(name);
    }

    /** Model height in pixels. */
    public float height() {
        return maxY - minY;
    }

    public float centerX() {
        return (minX + maxX) * 0.5F;
    }

    public float centerY() {
        return (minY + maxY) * 0.5F;
    }

    public float centerZ() {
        return (minZ + maxZ) * 0.5F;
    }

    public void reset() {
        for (Bone b : bones.values()) {
            b.reset();
        }
    }

    /** Parent of every bone (for walking down to one bone). */
    private final Map<Bone, Bone> parentOf = new HashMap<>();

    /**
     * Applies to {@code pose} the transforms of every bone from the root down to {@code name}
     * (with the current animation), then moves to that bone's pivot: things drawn afterwards
     * are held by that bone (an item in a hand, ...).
     */
    public boolean transformToBone(String name, PoseStack pose) {
        Bone target = bones.get(name);
        if (target == null) {
            return false;
        }
        List<Bone> path = new ArrayList<>();
        for (Bone b = target; b != null; b = parentOf.get(b)) {
            path.add(0, b);
        }
        for (Bone bone : path) {
            pose.translate((bone.px + bone.ax) / 16.0F, (bone.py + bone.ay) / 16.0F, (bone.pz + bone.az) / 16.0F);
            float rx = bone.rx + rad(bone.arx);
            float ry = bone.ry + rad(bone.ary);
            float rz = bone.rz + rad(bone.arz);
            if (rx != 0.0F || ry != 0.0F || rz != 0.0F) {
                pose.mulPose(new Quaternionf().rotationZYX(rz, ry, rx));
            }
            pose.scale(bone.sx, bone.sy, bone.sz);
            pose.translate(-bone.px / 16.0F, -bone.py / 16.0F, -bone.pz / 16.0F);
        }
        pose.translate(target.px / 16.0F, target.py / 16.0F, target.pz / 16.0F);
        return true;
    }

    // ------------------------------------------------------------------
    // Loading
    // ------------------------------------------------------------------

    /**
     * @param skinSubtree name of a bone whose subtree uses the player skin (64 x 64 UV), or null
     */
    public static GeoModel load(ResourceLocation location, String skinSubtree) {
        return load(location, skinSubtree, false);
    }

    /**
     * @param glowByName bones whose name starts with "glow" (and their children) go to group 1,
     *                   with the model's own texture size (drawn full-bright by the renderer)
     */
    public static GeoModel load(ResourceLocation location, String skinSubtree, boolean glowByName) {
        GeoModel model = new GeoModel();
        try (Reader reader = Minecraft.getInstance().getResourceManager().getResourceOrThrow(location).openAsReader()) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            JsonObject geo = root.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
            JsonObject desc = geo.getAsJsonObject("description");
            int texW = desc.get("texture_width").getAsInt();
            int texH = desc.get("texture_height").getAsInt();
            JsonArray boneArray = geo.getAsJsonArray("bones");
            Map<String, String> parents = new HashMap<>();
            for (JsonElement e : boneArray) {
                JsonObject b = e.getAsJsonObject();
                String name = b.get("name").getAsString();
                float[] pivot = vec(b, "pivot", 0.0F);
                float[] rot = vec(b, "rotation", 0.0F);
                Bone bone = new Bone(name, pivot[0], 24.0F - pivot[1], pivot[2],
                        rad(rot[0]), rad(rot[1]), rad(rot[2]));
                model.bones.put(name, bone);
                if (b.has("parent")) {
                    parents.put(name, b.get("parent").getAsString());
                }
            }
            for (Bone bone : model.bones.values()) {
                Bone parent = parents.containsKey(bone.name) ? model.bones.get(parents.get(bone.name)) : null;
                if (parent != null) {
                    parent.children.add(bone);
                    model.parentOf.put(bone, parent);
                } else {
                    model.roots.add(bone);
                }
            }
            if (skinSubtree != null && model.bones.containsKey(skinSubtree)) {
                markGroup(model.bones.get(skinSubtree), 1);
            }
            if (glowByName) {
                for (Bone bone : model.bones.values()) {
                    if (bone.name.startsWith("glow")) {
                        markGroup(bone, 1);
                    } else if (bone.name.startsWith("glass")) {
                        markGroup(bone, 2); // translucent, drawn last
                    }
                }
            }
            for (JsonElement e : boneArray) {
                JsonObject b = e.getAsJsonObject();
                Bone bone = model.bones.get(b.get("name").getAsString());
                if (!b.has("cubes")) {
                    continue;
                }
                int w = bone.group == 1 && !glowByName ? 64 : texW;
                int h = bone.group == 1 && !glowByName ? 64 : texH;
                for (JsonElement ce : b.getAsJsonArray("cubes")) {
                    bone.cubes.add(model.cube(ce.getAsJsonObject(), w, h));
                }
            }
        } catch (Exception ex) {
            LOGGER.error("Could not load model {}", location, ex);
        }
        if (model.minY > model.maxY) {
            model.minY = model.maxY = 0.0F;
            model.minX = model.maxX = model.minZ = model.maxZ = 0.0F;
        }
        return model;
    }

    private static void markGroup(Bone bone, int group) {
        bone.group = group;
        for (Bone c : bone.children) {
            markGroup(c, group);
        }
    }

    private static float rad(float deg) {
        return deg * ((float) Math.PI / 180.0F);
    }

    private static float[] vec(JsonObject o, String key, float def) {
        float[] out = {def, def, def};
        if (o.has(key) && o.get(key).isJsonArray()) {
            JsonArray a = o.getAsJsonArray(key);
            for (int i = 0; i < 3 && i < a.size(); i++) {
                out[i] = a.get(i).getAsFloat();
            }
        }
        return out;
    }

    private Cube cube(JsonObject c, int texW, int texH) {
        float[] origin = vec(c, "origin", 0.0F);
        float[] size = vec(c, "size", 0.0F);
        float inflate = c.has("inflate") ? c.get("inflate").getAsFloat() : 0.0F;
        boolean mirror = c.has("mirror") && c.get("mirror").getAsBoolean();
        // java space box
        float x0 = origin[0];
        float y0 = 24.0F - (origin[1] + size[1]);
        float z0 = origin[2];
        float dx = size[0];
        float dy = size[1];
        float dz = size[2];
        minX = Math.min(minX, x0);
        maxX = Math.max(maxX, x0 + dx);
        minY = Math.min(minY, y0);
        maxY = Math.max(maxY, y0 + dy);
        minZ = Math.min(minZ, z0);
        maxZ = Math.max(maxZ, z0 + dz);

        Cube cube = new Cube();
        if (c.has("rotation")) {
            float[] rot = vec(c, "rotation", 0.0F);
            float[] pivot = vec(c, "pivot", 0.0F);
            cube.rotated = rot[0] != 0.0F || rot[1] != 0.0F || rot[2] != 0.0F;
            cube.px = pivot[0];
            cube.py = 24.0F - pivot[1];
            cube.pz = pivot[2];
            cube.rx = rad(rot[0]);
            cube.ry = rad(rot[1]);
            cube.rz = rad(rot[2]);
        }

        // vanilla ModelPart.Cube corners
        float ax0 = x0 - inflate;
        float ay0 = y0 - inflate;
        float az0 = z0 - inflate;
        float ax1 = x0 + dx + inflate;
        float ay1 = y0 + dy + inflate;
        float az1 = z0 + dz + inflate;
        if (mirror) {
            float t = ax1;
            ax1 = ax0;
            ax0 = t;
        }
        float[] p7 = {ax0, ay0, az0};
        float[] p0 = {ax1, ay0, az0};
        float[] p1 = {ax1, ay1, az0};
        float[] p2 = {ax0, ay1, az0};
        float[] p3 = {ax0, ay0, az1};
        float[] p4 = {ax1, ay0, az1};
        float[] p5 = {ax1, ay1, az1};
        float[] p6 = {ax0, ay1, az1};

        // faces: java DOWN (y min = visual top), UP, WEST, NORTH, EAST, SOUTH
        float[][][] corners = {
                {p4, p3, p7, p0},
                {p1, p2, p6, p5},
                {p7, p3, p6, p2},
                {p0, p7, p2, p1},
                {p4, p0, p1, p5},
                {p3, p4, p5, p6}};
        float[][] normals = {{0, -1, 0}, {0, 1, 0}, {-1, 0, 0}, {0, 0, -1}, {1, 0, 0}, {0, 0, 1}};
        float[][] uvs = new float[6][];
        JsonElement uvEl = c.get("uv");
        if (uvEl != null && uvEl.isJsonObject()) {
            // per-face UV (bedrock face names)
            JsonObject faces = uvEl.getAsJsonObject();
            String[] names = {"up", "down", "west", "north", "east", "south"};
            for (int i = 0; i < 6; i++) {
                if (!faces.has(names[i])) {
                    continue;
                }
                JsonObject f = faces.getAsJsonObject(names[i]);
                float[] uv = vec(f, "uv", 0.0F);
                float[] us = vec(f, "uv_size", 0.0F);
                uvs[i] = i == 1
                        ? new float[] {uv[0], uv[1] + us[1], uv[0] + us[0], uv[1]}
                        : new float[] {uv[0], uv[1], uv[0] + us[0], uv[1] + us[1]};
            }
        } else {
            float u = 0.0F;
            float v = 0.0F;
            if (uvEl != null && uvEl.isJsonArray()) {
                u = uvEl.getAsJsonArray().get(0).getAsFloat();
                v = uvEl.getAsJsonArray().get(1).getAsFloat();
            }
            float f7 = u;
            float f8 = u + dz;
            float f9 = u + dz + dx;
            float f10 = u + dz + dx + dx;
            float f11 = u + dz + dx + dz;
            float f12 = u + dz + dx + dz + dx;
            float f13 = v;
            float f14 = v + dz;
            float f15 = v + dz + dy;
            uvs[0] = new float[] {f8, f13, f9, f14};
            uvs[1] = new float[] {f9, f14, f10, f13};
            uvs[2] = new float[] {f7, f14, f8, f15};
            uvs[3] = new float[] {f8, f14, f9, f15};
            uvs[4] = new float[] {f9, f14, f11, f15};
            uvs[5] = new float[] {f11, f14, f12, f15};
        }
        for (int i = 0; i < 6; i++) {
            if (uvs[i] == null) {
                continue;
            }
            float u1 = uvs[i][0] / texW;
            float v1 = uvs[i][1] / texH;
            float u2 = uvs[i][2] / texW;
            float v2 = uvs[i][3] / texH;
            float[][] q = {
                    {corners[i][0][0], corners[i][0][1], corners[i][0][2], u2, v1},
                    {corners[i][1][0], corners[i][1][1], corners[i][1][2], u1, v1},
                    {corners[i][2][0], corners[i][2][1], corners[i][2][2], u1, v2},
                    {corners[i][3][0], corners[i][3][1], corners[i][3][2], u2, v2}};
            float nx = normals[i][0];
            if (mirror) {
                float[][] r = {q[3], q[2], q[1], q[0]};
                q = r;
                nx = -nx;
            }
            cube.quads.add(new Quad(q, nx, normals[i][1], normals[i][2]));
        }
        return cube;
    }

    // ------------------------------------------------------------------
    // Rendering
    // ------------------------------------------------------------------

    /** Draws every bone of {@code group} (the transforms of all bones always apply). */
    public void render(PoseStack pose, VertexConsumer consumer, int light, int overlay, int group,
                       float r, float g, float b, float a) {
        for (Bone root : roots) {
            renderBone(root, pose, consumer, light, overlay, group, r, g, b, a, true);
        }
    }

    /**
     * Draws one bone and its children; with {@code ownRotation} false the bone's own rest
     * rotation is skipped (used for the first-person arm).
     */
    public void renderBone(Bone bone, PoseStack pose, VertexConsumer consumer, int light, int overlay, int group,
                           float r, float g, float b, float a, boolean ownRotation) {
        if (!bone.visible || Math.abs(bone.sx) < 1.0E-4F || Math.abs(bone.sy) < 1.0E-4F
                || Math.abs(bone.sz) < 1.0E-4F) {
            return;
        }
        pose.pushPose();
        pose.translate((bone.px + bone.ax) / 16.0F, (bone.py + bone.ay) / 16.0F, (bone.pz + bone.az) / 16.0F);
        float rx = (ownRotation ? bone.rx : 0.0F) + rad(bone.arx);
        float ry = (ownRotation ? bone.ry : 0.0F) + rad(bone.ary);
        float rz = (ownRotation ? bone.rz : 0.0F) + rad(bone.arz);
        if (rx != 0.0F || ry != 0.0F || rz != 0.0F) {
            pose.mulPose(new Quaternionf().rotationZYX(rz, ry, rx));
        }
        if (bone.sx != 1.0F || bone.sy != 1.0F || bone.sz != 1.0F) {
            pose.scale(bone.sx, bone.sy, bone.sz);
        }
        pose.translate(-bone.px / 16.0F, -bone.py / 16.0F, -bone.pz / 16.0F);
        if (bone.group == group && !bone.hideCubes) {
            for (Cube cube : bone.cubes) {
                if (Math.abs(bone.bendValue) > 1.0E-3F) {
                    drawBentCube(bone, cube, pose, consumer, light, overlay, r, g, b, a);
                } else {
                    drawCube(cube, pose, consumer, light, overlay, r, g, b, a);
                }
            }
        }
        for (Bone child : bone.children) {
            renderBone(child, pose, consumer, light, overlay, group, r, g, b, a, true);
        }
        pose.popPose();
    }

    /**
     * A limb cube bent in the middle like bendy-lib does it: the half away from the bone's pivot (forearm, shin)
     * turns by the bend around an axis lying across the limb (direction {@code bendAxis} around Y), the middle by half
     * of it so the two halves stay joined (the sign checked in game: knees back, elbows forward).
     */
    private static void drawBentCube(Bone bone, Cube cube, PoseStack pose, VertexConsumer consumer, int light,
                                     int overlay, float r, float g, float b, float a) {
        float minY = Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        float minX = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float minZ = Float.MAX_VALUE;
        float maxZ = -Float.MAX_VALUE;
        for (Quad q : cube.quads) {
            for (float[] v : q.v()) {
                minX = Math.min(minX, v[0]);
                maxX = Math.max(maxX, v[0]);
                minY = Math.min(minY, v[1]);
                maxY = Math.max(maxY, v[1]);
                minZ = Math.min(minZ, v[2]);
                maxZ = Math.max(maxZ, v[2]);
            }
        }
        if (maxY - minY < 1.0E-3F) {
            drawCube(cube, pose, consumer, light, overlay, r, g, b, a);
            return;
        }
        float mid = (minY + maxY) * 0.5F;
        boolean farIsMax = Math.abs(maxY - bone.py) >= Math.abs(minY - bone.py);
        float cx = (minX + maxX) * 0.5F;
        float cz = (minZ + maxZ) * 0.5F;
        Matrix4f full = bendMatrix(cx, mid, cz, bone.bendAxis, bone.bendValue);
        Matrix4f half = bendMatrix(cx, mid, cz, bone.bendAxis, bone.bendValue * 0.5F);
        if (cube.rotated) {
            pose.pushPose();
            pose.translate(cube.px / 16.0F, cube.py / 16.0F, cube.pz / 16.0F);
            pose.mulPose(new Quaternionf().rotationZYX(cube.rz, cube.ry, cube.rx));
            pose.translate(-cube.px / 16.0F, -cube.py / 16.0F, -cube.pz / 16.0F);
        }
        Matrix4f matrix = pose.last().pose();
        Matrix3f normalMatrix = pose.last().normal();
        for (Quad q : cube.quads) {
            float[][] v = q.v();
            boolean anyNear = false;
            boolean anyFar = false;
            for (float[] c : v) {
                boolean far = farIsMax ? c[1] > mid + 1.0E-4F : c[1] < mid - 1.0E-4F;
                anyFar |= far;
                anyNear |= !far;
            }
            if (anyNear && anyFar) {
                // a side face across the joint: two quads, split at the middle
                float[][] near = new float[4][];
                float[][] farQ = new float[4][];
                for (int i = 0; i < 4; i++) {
                    float[] c = v[i];
                    boolean far = farIsMax ? c[1] > mid : c[1] < mid;
                    float[] partner = null;
                    for (int k = 1; k <= 3 && partner == null; k += 2) {
                        float[] o = v[(i + k) % 4];
                        boolean ofar = farIsMax ? o[1] > mid : o[1] < mid;
                        if (ofar != far) {
                            partner = o;
                        }
                    }
                    if (partner == null) {
                        partner = v[(i + 2) % 4];
                    }
                    float t = (mid - c[1]) / (partner[1] - c[1]);
                    float[] m = {c[0] + (partner[0] - c[0]) * t, mid, c[2] + (partner[2] - c[2]) * t,
                            c[3] + (partner[3] - c[3]) * t, c[4] + (partner[4] - c[4]) * t};
                    if (far) {
                        farQ[i] = c;
                        near[i] = m;
                    } else {
                        near[i] = c;
                        farQ[i] = m;
                    }
                }
                emit(near, q, matrix, normalMatrix, null, half, mid, consumer, light, overlay, r, g, b, a);
                emit(farQ, q, matrix, normalMatrix, full, half, mid, consumer, light, overlay, r, g, b, a);
            } else {
                emit(v, q, matrix, normalMatrix, anyFar ? full : null, half, mid, consumer, light, overlay, r, g, b,
                        a);
            }
        }
        if (cube.rotated) {
            pose.popPose();
        }
    }

    private static Matrix4f bendMatrix(float cx, float cy, float cz, float axis, float value) {
        return new Matrix4f().translation(cx / 16.0F, cy / 16.0F, cz / 16.0F)
                .rotateY(axis).rotateX(value).rotateY(-axis)
                .translate(-cx / 16.0F, -cy / 16.0F, -cz / 16.0F);
    }

    /** One quad: the corners on the middle line take {@code half}, the others {@code bend} (null = unbent). */
    private static void emit(float[][] v, Quad q, Matrix4f matrix, Matrix3f normalMatrix, Matrix4f bend,
                             Matrix4f half, float mid, VertexConsumer consumer, int light, int overlay, float r,
                             float g, float b, float a) {
        Vector3f n = new Vector3f(q.nx(), q.ny(), q.nz());
        if (bend != null) {
            bend.transformDirection(n);
        }
        normalMatrix.transform(n);
        Vector4f p = new Vector4f();
        for (float[] c : v) {
            p.set(c[0] / 16.0F, c[1] / 16.0F, c[2] / 16.0F, 1.0F);
            if (Math.abs(c[1] - mid) < 1.0E-4F) {
                half.transform(p);
            } else if (bend != null) {
                bend.transform(p);
            }
            matrix.transform(p);
            consumer.vertex(p.x(), p.y(), p.z(), r, g, b, a, c[3], c[4], overlay, light, n.x(), n.y(), n.z());
        }
    }

    private static void drawCube(Cube cube, PoseStack pose, VertexConsumer consumer, int light, int overlay,
                                 float r, float g, float b, float a) {
        if (cube.rotated) {
            pose.pushPose();
            pose.translate(cube.px / 16.0F, cube.py / 16.0F, cube.pz / 16.0F);
            pose.mulPose(new Quaternionf().rotationZYX(cube.rz, cube.ry, cube.rx));
            pose.translate(-cube.px / 16.0F, -cube.py / 16.0F, -cube.pz / 16.0F);
        }
        Matrix4f matrix = pose.last().pose();
        Matrix3f normalMatrix = pose.last().normal();
        Vector3f n = new Vector3f();
        Vector4f p = new Vector4f();
        for (Quad quad : cube.quads) {
            normalMatrix.transform(n.set(quad.nx(), quad.ny(), quad.nz()));
            for (float[] v : quad.v()) {
                matrix.transform(p.set(v[0] / 16.0F, v[1] / 16.0F, v[2] / 16.0F, 1.0F));
                consumer.vertex(p.x(), p.y(), p.z(), r, g, b, a, v[3], v[4], overlay, light, n.x(), n.y(), n.z());
            }
        }
        if (cube.rotated) {
            pose.popPose();
        }
    }
}
