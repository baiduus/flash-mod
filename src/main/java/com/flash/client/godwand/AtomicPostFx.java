package com.flash.client.godwand;

/** (God Wand's post-processing shaders are not part of this port: the renderers draw their fallback.) */
public final class AtomicPostFx {

    private AtomicPostFx() {
    }

    public static boolean available() {
        return false;
    }

    public static boolean enabled() {
        return false;
    }

    public static boolean shaderPackActive() {
        return false;
    }

    public static void prewarm() {
    }

    public static void reset() {
    }
}
