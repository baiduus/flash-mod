package com.flash.client.godwand;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryStack;

/**
 * God Wand's post-processing chains on 1.20.1: the pass list of its own effect files (targets, inputs, outputs and
 * the default value of every uniform), run with its fragment shaders (GLSL 150 versions of God Wand's, with
 * 1.20.1's depth convention) over a full-screen triangle. Uniforms set with {@link #setUniform} override the
 * defaults in every pass that declares them.
 */
public final class GodWandPostChain implements AutoCloseable {

    private static final String MAIN = "minecraft:main";
    private static final Map<String, Integer> PROGRAMS = new HashMap<>();
    private static int vertexShader;
    private static int vao;

    private final String name;
    private final Map<String, float[]> values = new HashMap<>();
    private final Map<String, RenderTarget> targets = new LinkedHashMap<>();
    private final List<Pass> passes = new ArrayList<>();
    private int width;
    private int height;

    private record Input(String sampler, String target, boolean depth, boolean bilinear) {
    }

    private record Uniform(String name, String type, float[] value) {
    }

    private record Pass(int program, String output, List<Input> inputs, List<Uniform> uniforms,
                        Map<String, Integer> locations) {
        int location(String uniform) {
            return this.locations.computeIfAbsent(uniform, u -> GL20.glGetUniformLocation(this.program, u));
        }
    }

    public GodWandPostChain(String name) {
        this.name = name;
    }

    public void setUniform(String name, float... value) {
        this.values.put(name, value);
    }

    public void setUniform(String name, Matrix4f value) {
        this.values.put(name, value.get(new float[16]));
    }

    // ------------------------------------------------------------------
    // Loading
    // ------------------------------------------------------------------

    private static String read(ResourceLocation id) throws IOException {
        try (InputStream in = Minecraft.getInstance().getResourceManager().getResourceOrThrow(id).open()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static int compile(int kind, String source, String what) {
        int shader = GL20.glCreateShader(kind);
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            String log = GL20.glGetShaderInfoLog(shader, 32768);
            GL20.glDeleteShader(shader);
            throw new IllegalStateException("God Wand shader " + what + " failed: " + log);
        }
        return shader;
    }

    private static int program(String fragment) throws IOException {
        Integer cached = PROGRAMS.get(fragment);
        if (cached != null) {
            return cached;
        }
        if (vertexShader == 0) {
            vertexShader = compile(GL20.GL_VERTEX_SHADER,
                    read(new ResourceLocation("flash", "shaders/godwand/screenquad.vsh")), "screenquad");
        }
        int fs = compile(GL20.GL_FRAGMENT_SHADER,
                read(new ResourceLocation("flash", "shaders/godwand/" + fragment + ".fsh")), fragment);
        int program = GL20.glCreateProgram();
        GL20.glAttachShader(program, vertexShader);
        GL20.glAttachShader(program, fs);
        GL20.glLinkProgram(program);
        if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
            String log = GL20.glGetProgramInfoLog(program, 32768);
            GL20.glDeleteProgram(program);
            throw new IllegalStateException("God Wand program " + fragment + " failed to link: " + log);
        }
        GL20.glDetachShader(program, fs);
        GL20.glDeleteShader(fs);
        PROGRAMS.put(fragment, program);
        return program;
    }

    /** Builds the targets (at this size) and the passes the first time, and again when the window changes. */
    public void resize(int w, int h) throws IOException {
        if (!this.passes.isEmpty() && this.width == w && this.height == h) {
            return;
        }
        this.closeTargets();
        this.passes.clear();
        JsonObject def;
        try (BufferedReader reader = Minecraft.getInstance().getResourceManager().openAsReader(
                new ResourceLocation("flash", "godwand/post_effect/" + this.name + ".json"))) {
            def = JsonParser.parseReader(reader).getAsJsonObject();
        }
        for (Map.Entry<String, JsonElement> e : def.getAsJsonObject("targets").entrySet()) {
            String key = e.getKey();
            int tw = w;
            int th = h;
            if (key.contains("emit") || key.contains("blur")) {
                tw = GodWandPostFxManager.bloomDimension(w);
                th = GodWandPostFxManager.bloomDimension(h);
            } else if (key.contains("half")) {
                tw = Math.max(1, (w + 1) / 2);
                th = Math.max(1, (h + 1) / 2);
            }
            TextureTarget target = new TextureTarget(tw, th, false, Minecraft.ON_OSX);
            target.setClearColor(0.0F, 0.0F, 0.0F, 0.0F);
            this.targets.put(key, target);
        }
        for (JsonElement el : def.getAsJsonArray("passes")) {
            JsonObject p = el.getAsJsonObject();
            String fragment = p.get("fragment_shader").getAsString();
            fragment = fragment.substring(fragment.lastIndexOf('/') + 1);
            List<Input> inputs = new ArrayList<>();
            for (JsonElement in : p.getAsJsonArray("inputs")) {
                JsonObject i = in.getAsJsonObject();
                inputs.add(new Input(i.get("sampler_name").getAsString() + "Sampler", i.get("target").getAsString(),
                        i.has("use_depth_buffer") && i.get("use_depth_buffer").getAsBoolean(),
                        i.has("bilinear") && i.get("bilinear").getAsBoolean()));
            }
            List<Uniform> uniforms = new ArrayList<>();
            if (p.has("uniforms")) {
                for (Map.Entry<String, JsonElement> block : p.getAsJsonObject("uniforms").entrySet()) {
                    for (JsonElement u : block.getValue().getAsJsonArray()) {
                        JsonObject o = u.getAsJsonObject();
                        JsonElement v = o.get("value");
                        float[] data;
                        if (v.isJsonArray()) {
                            JsonArray a = v.getAsJsonArray();
                            data = new float[a.size()];
                            for (int k = 0; k < data.length; k++) {
                                data[k] = a.get(k).getAsFloat();
                            }
                        } else {
                            data = new float[] {v.getAsFloat()};
                        }
                        uniforms.add(new Uniform(o.get("name").getAsString(), o.get("type").getAsString(), data));
                    }
                }
            }
            this.passes.add(new Pass(program(fragment), p.get("output").getAsString(), inputs, uniforms,
                    new HashMap<>()));
        }
        this.width = w;
        this.height = h;
    }

    // ------------------------------------------------------------------
    // Running
    // ------------------------------------------------------------------

    private RenderTarget target(String id) {
        return MAIN.equals(id) ? Minecraft.getInstance().getMainRenderTarget() : this.targets.get(id);
    }

    public void process() {
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        if (vao == 0) {
            vao = GL30.glGenVertexArrays();
        }
        RenderSystem.disableBlend();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.colorMask(true, true, true, true);
        int maxUnits = 0;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            for (Pass pass : this.passes) {
                RenderTarget out = this.target(pass.output);
                if (out == null) {
                    continue;
                }
                out.bindWrite(true);
                GL20.glUseProgram(pass.program);
                int unit = 0;
                float[] inSize = null;
                float[] auxSize = null;
                for (Input in : pass.inputs) {
                    RenderTarget src = this.target(in.target);
                    if (src == null) {
                        continue;
                    }
                    int texture = in.depth ? src.getDepthTextureId() : src.getColorTextureId();
                    RenderSystem.activeTexture(GL13.GL_TEXTURE0 + unit);
                    RenderSystem.bindTexture(texture);
                    int filter = in.bilinear ? GL11.GL_LINEAR : GL11.GL_NEAREST;
                    GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, filter);
                    GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, filter);
                    int loc = pass.location(in.sampler);
                    if (loc >= 0) {
                        GL20.glUniform1i(loc, unit);
                    }
                    float[] size = {src.width, src.height};
                    if (inSize == null) {
                        inSize = size;
                    } else if (auxSize == null) {
                        auxSize = size;
                    }
                    unit++;
                }
                maxUnits = Math.max(maxUnits, unit);
                set(pass, "OutSize", out.width, out.height);
                if (inSize != null) {
                    set(pass, "InSize", inSize[0], inSize[1]);
                }
                set(pass, "AuxiliarySize", auxSize != null ? auxSize[0] : 0.0F, auxSize != null ? auxSize[1] : 0.0F);
                for (Uniform u : pass.uniforms) {
                    float[] data = this.values.getOrDefault(u.name, u.value);
                    int loc = pass.location(u.name);
                    if (loc < 0 || data == null) {
                        continue;
                    }
                    switch (u.type) {
                        case "float" -> GL20.glUniform1f(loc, data[0]);
                        case "vec2" -> GL20.glUniform2f(loc, data[0], data[1]);
                        case "vec3" -> GL20.glUniform3f(loc, data[0], data[1], data[2]);
                        case "matrix4x4" -> GL20.glUniformMatrix4fv(loc, false, stack.floats(data));
                        default -> {
                        }
                    }
                }
                GL30.glBindVertexArray(vao);
                GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
            }
        } finally {
            GL30.glBindVertexArray(0);
            BufferUploader.invalidate();
            GL20.glUseProgram(0);
            // the main target is sampled plain again (and every unit we used is let go)
            for (int i = 0; i < maxUnits; i++) {
                RenderSystem.activeTexture(GL13.GL_TEXTURE0 + i);
                RenderSystem.bindTexture(0);
            }
            RenderSystem.activeTexture(GL13.GL_TEXTURE0);
            for (int texture : new int[] {main.getColorTextureId(), main.getDepthTextureId()}) {
                RenderSystem.bindTexture(texture);
                GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
                GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            }
            RenderSystem.bindTexture(0);
            main.bindWrite(true);
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
        }
    }

    private static void set(Pass pass, String uniform, float x, float y) {
        int loc = pass.location(uniform);
        if (loc >= 0) {
            GL20.glUniform2f(loc, x, y);
        }
    }

    private void closeTargets() {
        for (RenderTarget t : this.targets.values()) {
            t.destroyBuffers();
        }
        this.targets.clear();
    }

    @Override
    public void close() {
        this.closeTargets();
        this.passes.clear();
    }

    /** A resource reload: the programs are built again from the (maybe new) shader files. */
    public static void reloadPrograms() {
        for (int p : PROGRAMS.values()) {
            GL20.glDeleteProgram(p);
        }
        PROGRAMS.clear();
        if (vertexShader != 0) {
            GL20.glDeleteShader(vertexShader);
            vertexShader = 0;
        }
    }
}
