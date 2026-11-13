package com.flash.client.godwand;

/** God Wand's client settings (its defaults; the cutscenes and the post shaders are not part of this port). */
public final class GodWandConfig {

    public static final Client CLIENT = new Client();

    private GodWandConfig() {
    }

    public static final class Client {
        public boolean postEffects = false;
        public boolean impactFrames = true;
        public boolean cameraFov = true;
        public boolean skyBlackout = true;
        public boolean targetMarker = true;
        public boolean atomicCutscene = false;
        public boolean fugaCutscene = false;
        public float cameraShake = 1.0F;
        public float bloomStrength = 0.85F;
    }
}
