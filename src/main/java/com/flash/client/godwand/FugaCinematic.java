package com.flash.client.godwand;

/** (God Wand's Fuga cutscene is not part of this port; its easing stays, the rest of the Fuga uses it.) */
public final class FugaCinematic {

    private FugaCinematic() {
    }

    static float smooth(double age, double start, double end) {
        float t = (float) Math.max(0.0, Math.min(1.0, (age - start) / (end - start)));
        return t * t * (3.0F - 2.0F * t);
    }

    public static float environmentGain() {
        FugaRenderer.Frame frame = FugaRenderer.localFrame(0.0F);
        if (frame != null && frame.phase() == 2) {
            return 0.25F + 0.75F * smooth(frame.age(), 260.0, 300.0);
        }
        return 1.0F;
    }
}
