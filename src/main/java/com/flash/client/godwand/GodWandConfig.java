package com.flash.client.godwand;

/** God Wand's client settings (its defaults). */
public final class GodWandConfig {

    public static final Client CLIENT = new Client();

    private GodWandConfig() {
    }

    public static final class Client {
        public boolean postEffects = true;
        public boolean impactFrames = true;
        public boolean cameraFov = true;
        public boolean skyBlackout = true;
        public boolean targetMarker = true;
        public boolean atomicCutscene = true;
        public boolean fugaCutscene = true;
        public float cameraShake = 1.0F;
        public float bloomStrength = 0.85F;
        public int bloomDownsample = 4;
    }
}
