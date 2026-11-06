package com.flash.client.geo;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

/**
 * One animation of a Bedrock / GeckoLib {@code *.animation.json} file: per bone
 * rotation (degrees), position (pixels) and scale keyframes, linearly
 * interpolated. Applied additively onto the bones of a {@link GeoModel}.
 */
public final class GeoAnimation {

    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    private static final class Channel {
        final float[] times;
        final float[][] values;

        Channel(float[] times, float[][] values) {
            this.times = times;
            this.values = values;
        }

        void sample(float t, float[] out) {
            int n = times.length;
            if (n == 1 || t <= times[0]) {
                System.arraycopy(values[0], 0, out, 0, 3);
                return;
            }
            if (t >= times[n - 1]) {
                System.arraycopy(values[n - 1], 0, out, 0, 3);
                return;
            }
            int i = 1;
            while (i < n - 1 && times[i] < t) {
                i++;
            }
            float t0 = times[i - 1];
            float t1 = times[i];
            float f = t1 - t0 <= 1.0E-6F ? 1.0F : (t - t0) / (t1 - t0);
            for (int k = 0; k < 3; k++) {
                out[k] = values[i - 1][k] + (values[i][k] - values[i - 1][k]) * f;
            }
        }
    }

    private record BoneTrack(String bone, Channel rotation, Channel position, Channel scale) {
    }

    public final String name;
    public final float length;
    public final boolean loop;
    private final List<BoneTrack> tracks;

    private GeoAnimation(String name, float length, boolean loop, List<BoneTrack> tracks) {
        this.name = name;
        this.length = length;
        this.loop = loop;
        this.tracks = tracks;
    }

    /** Applies the pose at {@code seconds} (looped or held on the last frame). */
    public void apply(GeoModel model, float seconds) {
        float t = seconds;
        if (loop && length > 0.0F) {
            t = seconds % length;
            if (t < 0.0F) {
                t += length;
            }
        } else {
            t = Math.max(0.0F, Math.min(length, seconds));
        }
        float[] v = new float[3];
        for (BoneTrack track : tracks) {
            GeoModel.Bone bone = model.bone(track.bone());
            if (bone == null) {
                continue;
            }
            if (track.rotation() != null) {
                track.rotation().sample(t, v);
                bone.arx += v[0];
                bone.ary += v[1];
                bone.arz += v[2];
            }
            if (track.position() != null) {
                track.position().sample(t, v);
                // bedrock y up -> java y down
                bone.ax += v[0];
                bone.ay -= v[1];
                bone.az += v[2];
            }
            if (track.scale() != null) {
                track.scale().sample(t, v);
                bone.sx *= v[0];
                bone.sy *= v[1];
                bone.sz *= v[2];
            }
        }
    }

    // ------------------------------------------------------------------
    // Loading
    // ------------------------------------------------------------------

    public static Map<String, GeoAnimation> load(ResourceLocation location) {
        Map<String, GeoAnimation> out = new HashMap<>();
        try (Reader reader = Minecraft.getInstance().getResourceManager().getResourceOrThrow(location).openAsReader()) {
            JsonObject animations = JsonParser.parseReader(reader).getAsJsonObject().getAsJsonObject("animations");
            for (Map.Entry<String, JsonElement> entry : animations.entrySet()) {
                JsonObject a = entry.getValue().getAsJsonObject();
                boolean loop = a.has("loop") && a.get("loop").isJsonPrimitive()
                        && a.get("loop").getAsJsonPrimitive().isBoolean() && a.get("loop").getAsBoolean();
                float maxTime = 0.0F;
                List<BoneTrack> tracks = new ArrayList<>();
                if (a.has("bones")) {
                    for (Map.Entry<String, JsonElement> be : a.getAsJsonObject("bones").entrySet()) {
                        JsonObject channels = be.getValue().getAsJsonObject();
                        Channel rot = channel(channels.get("rotation"), 0.0F);
                        Channel pos = channel(channels.get("position"), 0.0F);
                        Channel scl = channel(channels.get("scale"), 1.0F);
                        for (Channel c : new Channel[] {rot, pos, scl}) {
                            if (c != null) {
                                maxTime = Math.max(maxTime, c.times[c.times.length - 1]);
                            }
                        }
                        tracks.add(new BoneTrack(be.getKey(), rot, pos, scl));
                    }
                }
                float length = a.has("animation_length") ? a.get("animation_length").getAsFloat() : maxTime;
                out.put(entry.getKey(), new GeoAnimation(entry.getKey(), Math.max(0.05F, length), loop, tracks));
            }
        } catch (Exception ex) {
            LOGGER.error("Could not load animations {}", location, ex);
        }
        return out;
    }

    private static Channel channel(JsonElement el, float def) {
        if (el == null || el.isJsonNull()) {
            return null;
        }
        if (el.isJsonArray() || el.isJsonPrimitive()) {
            return new Channel(new float[] {0.0F}, new float[][] {vector(el, def)});
        }
        JsonObject o = el.getAsJsonObject();
        if (o.has("vector") && !o.get("vector").isJsonObject()) {
            return new Channel(new float[] {0.0F}, new float[][] {vector(o.get("vector"), def)});
        }
        List<Float> times = new ArrayList<>();
        List<float[]> values = new ArrayList<>();
        for (Map.Entry<String, JsonElement> kf : o.entrySet()) {
            float time;
            try {
                time = Float.parseFloat(kf.getKey());
            } catch (NumberFormatException ex) {
                continue;
            }
            JsonElement v = kf.getValue();
            if (v.isJsonObject()) {
                JsonObject k = v.getAsJsonObject();
                v = k.has("vector") ? k.get("vector") : k.has("post") ? k.get("post") : k.get("pre");
                if (v != null && v.isJsonObject() && v.getAsJsonObject().has("vector")) {
                    v = v.getAsJsonObject().get("vector");
                }
            }
            if (v == null) {
                continue;
            }
            times.add(time);
            values.add(vector(v, def));
        }
        if (times.isEmpty()) {
            return null;
        }
        // keyframes sorted by time
        Integer[] order = new Integer[times.size()];
        for (int i = 0; i < order.length; i++) {
            order[i] = i;
        }
        java.util.Arrays.sort(order, (x, y) -> Float.compare(times.get(x), times.get(y)));
        float[] t = new float[order.length];
        float[][] val = new float[order.length][];
        for (int i = 0; i < order.length; i++) {
            t[i] = times.get(order[i]);
            val[i] = values.get(order[i]);
        }
        return new Channel(t, val);
    }

    private static float[] vector(JsonElement el, float def) {
        float[] out = {def, def, def};
        if (el.isJsonArray()) {
            JsonArray a = el.getAsJsonArray();
            for (int i = 0; i < 3 && i < a.size(); i++) {
                out[i] = number(a.get(i), def);
            }
        } else if (el.isJsonPrimitive()) {
            float f = number(el, def);
            out[0] = out[1] = out[2] = f;
        }
        return out;
    }

    private static float number(JsonElement el, float def) {
        try {
            return el.getAsFloat();
        } catch (Exception ex) {
            return def; // molang expressions are not supported (none in the Hulk files)
        }
    }
}
