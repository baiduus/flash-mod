package com.flash.client.godwand;

import com.flash.FlashMod;
import com.flash.client.FlashKeys;
import com.flash.godwand.CannonPressurePayload;
import com.flash.godwand.GodWandSkills;
import com.flash.godwand.ImpactFxPayload;
import com.flash.network.FlashNetwork;
import com.flash.network.YasWandPacket;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Yas' God Wand skills on her side: the keys (R holds and lets go; the path key in the Reality mode), who is
 * charging what (for the orb in the hand, the stone, the bow of fire, the poses), and God Wand's renderers, sounds,
 * impact frames and camera shake each frame / tick. Ported from God Wand by HyIsNoob.
 */
@Mod.EventBusSubscriber(modid = FlashMod.MODID, value = Dist.CLIENT)
public final class GodWandClient {

    /** entity id -> {skill, client game time it began}. */
    private static final Map<Integer, long[]> CHARGES = new HashMap<>();
    /** The skill R is held for now (-1 none). */
    private static int held = -1;

    private GodWandClient() {
    }

    // ------------------------------------------------------------------
    // Who charges what
    // ------------------------------------------------------------------

    public static void charge(int entity, int skill) {
        Minecraft mc = Minecraft.getInstance();
        if (skill < 0) {
            CHARGES.remove(entity);
        } else if (mc.level != null) {
            CHARGES.put(entity, new long[] {skill, mc.level.getGameTime()});
        }
    }

    public static boolean charging(Entity e, int skill) {
        long[] c = e == null ? null : CHARGES.get(e.getId());
        return c != null && c[0] == skill;
    }

    public static int chargeTicks(Entity e) {
        long[] c = e == null ? null : CHARGES.get(e.getId());
        Minecraft mc = Minecraft.getInstance();
        return c == null || mc.level == null ? 0 : (int) Math.max(0L, mc.level.getGameTime() - c[1]);
    }

    // ------------------------------------------------------------------
    // The keys (called by YasAbilities each tick)
    // ------------------------------------------------------------------

    /** R in one of the God Wand modes ({@code skill} 1..6; -1: another mode or none). */
    public static void tickMode(Minecraft mc, int skill) {
        boolean down = skill > 0 && mc.screen == null && mc.player != null && FlashKeys.YAS_USE.isDown();
        if (held >= 0 && (skill != held || mc.screen != null || mc.player == null)) {
            send(held, GodWandSkills.CANCEL, 0);
            held = -1;
        }
        if (down && held < 0) {
            held = skill;
            send(skill, GodWandSkills.PRESS, 0);
        } else if (!down && held >= 0) {
            send(held, GodWandSkills.RELEASE, 0);
            held = -1;
        }
    }

    /** The Spatial Path key (Reality mode): on / off; with Shift the bridge under her feet. */
    public static void tickPath(Minecraft mc, boolean enabled) {
        while (FlashKeys.YAS_PATH.consumeClick()) {
            if (enabled && mc.screen == null && mc.player != null) {
                send(GodWandSkills.PATH, GodWandSkills.PRESS, net.minecraft.client.gui.screens.Screen.hasShiftDown()
                        ? 1 : 0);
            }
        }
    }

    private static void send(int skill, int action, int value) {
        FlashNetwork.CHANNEL.sendToServer(new YasWandPacket(skill, action, value));
    }

    // ------------------------------------------------------------------
    // The effects
    // ------------------------------------------------------------------

    public static void impact(ImpactFxPayload payload) {
        if (payload.fxType() == 4) {
            SupernovaSoundscape.start(new Vec3(payload.x(), payload.y(), payload.z()));
        }
        if (payload.fxType() == 6) {
            EarthDrillSoundscape.start(new Vec3(payload.x(), payload.y(), payload.z()));
        }
        if (payload.fxType() == 8) {
            CustomImpactFrameOverlay.trigger();
        }
        SupernovaAccretionRenderer.receive(payload);
        ImpactDebrisRenderer.receive(payload);
    }

    public static void cannon(CannonPressurePayload payload) {
        CannonPressureRenderer.receive(payload);
        ImpactDebrisRenderer.receive(payload);
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_WEATHER) {
            return;
        }
        PoseStack matrices = event.getPoseStack();
        Camera camera = event.getCamera();
        float delta = event.getPartialTick();
        try {
            ArcaneOrbEntityRenderer.renderAll(matrices, camera, delta);
            StoneCannonEntityRenderer.renderAll(matrices, camera, delta);
            CannonPressureRenderer.render(matrices, camera, delta);
            DrillWindRenderer.render(matrices, camera, delta);
            ImpactDebrisRenderer.render(matrices, camera, delta);
            SingularityThreadRenderer.render(matrices, camera, delta);
            SpatialAuraRenderer.render(matrices, camera, delta);
            StoneCannonChargeRenderer.render(matrices, camera, delta);
            ArcaneOrbChargeRenderer.render(matrices, camera, delta);
            SupernovaAccretionRenderer.render(matrices, camera, delta);
            AtomicRenderer.render(matrices, camera, delta);
            FugaRenderer.render(matrices, camera, delta);
        } finally {
            GodWandGeometry.flush();
        }
    }

    @SubscribeEvent
    public static void onOverlay(RenderGuiOverlayEvent.Post event) {
        if (event.getOverlay().id().equals(net.minecraftforge.client.gui.overlay.VanillaGuiOverlay.VIGNETTE.id())) {
            CustomImpactFrameOverlay.render(event.getGuiGraphics());
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) {
            return;
        }
        CHARGES.keySet().removeIf(id -> client.level.getEntity(id) == null);
        AtomicRenderer.tick(client);
        FugaRenderer.tick(client);
        SupernovaAccretionRenderer.tickCharge(client);
        StoneCannonWindSound.update(client);
        EarthDrillSoundscape.tick(client);
        GodWandCinematicEngine.onClientTick();
        CustomImpactFrameOverlay.onClientTick();
    }

    @SubscribeEvent
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        float[] shake = GodWandCinematicEngine.shake((float) event.getPartialTick());
        if (shake != null) {
            event.setPitch(event.getPitch() + shake[0]);
            event.setYaw(event.getYaw() + shake[1]);
            event.setRoll(event.getRoll() + shake[2]);
        }
    }

    @SubscribeEvent
    public static void onFov(ViewportEvent.ComputeFov event) {
        if (event.usedConfiguredFov()) {
            event.setFOV(event.getFOV() * GodWandCinematicEngine.getFovMultiplier((float) event.getPartialTick()));
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        CHARGES.clear();
        held = -1;
        GodWandGeometry.reset();
        AtomicRenderer.reset();
        FugaRenderer.reset();
        DrillWindRenderer.reset();
        CannonPressureRenderer.reset();
        SingularityThreadRenderer.reset();
        SpatialAuraRenderer.reset();
        StoneCannonWindSound.reset();
        EarthDrillSoundscape.reset();
        SupernovaAccretionRenderer.reset();
        ImpactDebrisRenderer.reset();
        GodWandCinematicEngine.reset();
        CustomImpactFrameOverlay.reset();
    }

    // ------------------------------------------------------------------
    // The poses: the bow of fire (Fuga), the raised arms (I am Atomic), aiming the others
    // ------------------------------------------------------------------

    public static final HumanoidModel.ArmPose WAND_POSE = HumanoidModel.ArmPose.create("FLASH_GODWAND", true,
            (model, entity, arm) -> {
                if (arm == HumanoidArm.RIGHT) {
                    pose(model, entity);
                }
            });

    private static void pose(HumanoidModel<?> model, LivingEntity entity) {
        float partial = Minecraft.getInstance().getFrameTime();
        if (FugaPose.apply(entity.getId(), partial, model.rightArm, model.leftArm, model.body, model.head)) {
            FugaPose.stance(entity.getId(), partial, model.rightLeg, model.leftLeg);
            return;
        }
        AtomicRenderer.Frame frame = AtomicRenderer.frameFor(entity.getId(), partial);
        boolean charging = charging(entity, GodWandSkills.ATOMIC);
        if (!charging && frame == null) {
            return;
        }
        AtomicPose.apply(model, entity, frame, frame == null ? chargeTicks(entity) + partial : frame.age());
    }

    @SubscribeEvent
    public static void onRenderPlayer(RenderPlayerEvent.Pre event) {
        if (!(event.getEntity() instanceof AbstractClientPlayer p)) {
            return;
        }
        PlayerModel<AbstractClientPlayer> model = event.getRenderer().getModel();
        int id = p.getId();
        boolean fuga = FugaRenderer.frameFor(id, event.getPartialTick()) != null;
        boolean atomic = AtomicRenderer.frameFor(id, event.getPartialTick()) != null || charging(p, GodWandSkills.ATOMIC);
        if (fuga || atomic) {
            model.rightArmPose = WAND_POSE;
            model.leftArmPose = WAND_POSE;
        } else if (charging(p, GodWandSkills.SINGULARITY) || charging(p, GodWandSkills.EARTH_DRILL)
                || charging(p, GodWandSkills.STONE_CANNON)) {
            model.rightArmPose = HumanoidModel.ArmPose.BOW_AND_ARROW;
            model.leftArmPose = HumanoidModel.ArmPose.BOW_AND_ARROW;
        }
    }
}
