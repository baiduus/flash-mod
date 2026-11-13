package com.flash.client;

import com.flash.FlashMod;
import com.flash.hulk.HulkPowers;
import com.flash.hulk.HulkState;
import com.flash.network.FlashNetwork;
import com.flash.network.HulkTransformPacket;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Client side of the Hulk: bigger body, the animated Hulk model (HulkAnimator), first-person arm and the H key. */
@Mod.EventBusSubscriber(modid = FlashMod.MODID, value = Dist.CLIENT)
public final class HulkClient {

    private static final boolean[] KEY_DOWN = new boolean[com.flash.hulk.HulkAbility.values().length];
    private static int scaledDepth;

    private HulkClient() {
    }

    /** Key of a Hulk ability: the Flash ability key it shares, or V for the grab. */
    public static net.minecraft.client.KeyMapping key(com.flash.hulk.HulkAbility ability) {
        return ability.keySlot() == null ? FlashKeys.INCREASE_SPEED : FlashAbilityKeys.mapping(ability.keySlot());
    }

    /** Force bar of the rock throw: 2 seconds to fill. */
    private static final int CHARGE_TICKS = 40;
    private static boolean charging;
    private static boolean chargingGrab;
    /** A minute of charge for the super throw of a held creature. */
    public static final int SUPER_TICKS = com.flash.hulk.HulkAbilities.SUPER_TICKS;
    private static boolean chargeArmed;
    private static int chargeTicks;
    /** Ticks of the second press on the grab key (-1 = none); shorter than the tap = let go. */
    private static int grabPress = -1;
    private static final int GRAB_TAP_TICKS = 5;

    public static boolean charging() {
        return charging;
    }

    public static boolean chargingGrab() {
        return charging && chargingGrab;
    }

    /** Charge for the super throw: a minute, or just the full bar with the cooldowns off. */
    public static int superTicks() {
        return HulkState.noCooldowns() ? CHARGE_TICKS : SUPER_TICKS;
    }

    /** Ticks left until the super throw is ready (0 = ready). */
    public static int superTicksLeft() {
        return Math.max(0, superTicks() - chargeTicks);
    }

    public static float chargeFraction() {
        return Math.min(1.0F, chargeTicks / (float) CHARGE_TICKS);
    }

    static void resetModel() {
        HulkAnimator.reset();
    }

    /** Transformed, or playing the transformation: only the Hulk model is drawn. */
    private static boolean hulkVisible(net.minecraft.world.entity.player.Player player) {
        return HulkState.isTransformed(player)
                || (player instanceof AbstractClientPlayer p && HulkAnimator.transforming(p));
    }

    /** The Hulk is drawn 1.5 times bigger (the hitbox grows the same, see HulkEvents). */
    @SubscribeEvent
    public static void onRenderPre(RenderPlayerEvent.Pre event) {
        if (hulkVisible(event.getEntity())) {
            // only the Hulk is visible: the player's own skin (and its outer layer) is hidden
            PlayerModel<AbstractClientPlayer> skin = event.getRenderer().getModel();
            skin.head.visible = false;
            skin.hat.visible = false;
            skin.body.visible = false;
            skin.jacket.visible = false;
            skin.rightArm.visible = false;
            skin.leftArm.visible = false;
            skin.rightSleeve.visible = false;
            skin.leftSleeve.visible = false;
            skin.rightLeg.visible = false;
            skin.leftLeg.visible = false;
            skin.rightPants.visible = false;
            skin.leftPants.visible = false;
            event.getPoseStack().pushPose();
            float scale = HulkState.scale(event.getEntity());
            event.getPoseStack().scale(scale, scale, scale);
            scaledDepth++;
        }
    }

    @SubscribeEvent
    public static void onRenderPost(RenderPlayerEvent.Post event) {
        if (scaledDepth > 0 && hulkVisible(event.getEntity())) {
            event.getPoseStack().popPose();
            scaledDepth--;
        }
    }

    /**
     * The Hulk swims hard: in the water he goes where he looks (under it, up and down too), fast and faster with the
     * level, quicker still sprinting; jump rises and sneak dives. Standing still, the water does as always.
     */
    @SubscribeEvent
    public static void onSwim(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.START || !event.player.level().isClientSide
                || event.player != Minecraft.getInstance().player
                || !(event.player instanceof net.minecraft.client.player.LocalPlayer p)) {
            return;
        }
        if (!HulkState.isTransformed(p) || !p.isInWater() || p.getAbilities().flying || p.isPassenger()) {
            return;
        }
        float forward = p.input.forwardImpulse;
        float strafe = p.input.leftImpulse;
        boolean up = p.input.jumping;
        boolean down = p.input.shiftKeyDown;
        if (forward == 0.0F && strafe == 0.0F && !up && !down) {
            return;
        }
        int level = Math.max(1, HulkState.level());
        // 0.6 blocks a tick at level 1 up to 1.0 at level 10 (12 to 20 blocks a second), half again sprinting
        double speed = (0.555D + 0.045D * level) * (p.isSprinting() ? 1.5D : 1.0D);
        net.minecraft.world.phys.Vec3 look = p.getLookAngle();
        boolean under = p.isUnderWater() || p.isSwimming();
        net.minecraft.world.phys.Vec3 flat = new net.minecraft.world.phys.Vec3(look.x, 0.0D, look.z);
        flat = flat.lengthSqr() < 1.0E-6D ? new net.minecraft.world.phys.Vec3(0.0D, 0.0D, 1.0D) : flat.normalize();
        net.minecraft.world.phys.Vec3 dir = under ? look : flat;
        net.minecraft.world.phys.Vec3 right = new net.minecraft.world.phys.Vec3(-flat.z, 0.0D, flat.x);
        net.minecraft.world.phys.Vec3 target = dir.scale(forward * speed).add(right.scale(-strafe * speed * 0.6D));
        net.minecraft.world.phys.Vec3 cur = p.getDeltaMovement();
        double vy = under ? target.y : cur.y;
        if (up) {
            vy = Math.max(vy, 0.4D);
        }
        if (down) {
            vy = Math.min(vy, -0.4D);
        }
        target = new net.minecraft.world.phys.Vec3(target.x, vy, target.z);
        p.setDeltaMovement(cur.add(target.subtract(cur).scale(0.3D)));
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || Minecraft.getInstance().player == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        while (FlashKeys.HULK_TRANSFORM.consumeClick()) {
            if (HulkState.has(mc.player)) {
                FlashNetwork.CHANNEL.sendToServer(new HulkTransformPacket());
            }
        }
        // Hulk abilities share the keys of the Flash abilities
        com.flash.hulk.HulkAbility[] abilities = com.flash.hulk.HulkAbility.values();
        boolean active = mc.screen == null && HulkState.isTransformed(mc.player);
        boolean holdingRock = mc.player != null && HulkAnimator.holdingRock(mc.player.getUUID());
        boolean holdingCreature = mc.player != null && HulkAnimator.holdingCreature(mc.player.getUUID());
        for (int i = 0; i < abilities.length; i++) {
            net.minecraft.client.KeyMapping key = key(abilities[i]);
            boolean down = active && key != null && key.isDown();
            boolean rock = abilities[i] == com.flash.hulk.HulkAbility.BOULDER;
            boolean grab = abilities[i] == com.flash.hulk.HulkAbility.GRAB;
            if ((rock && holdingRock) || (grab && holdingCreature)) {
                // holding: a new press charges the force bar, the release throws
                if (grab) {
                    // like Superman: a tap lets the creature go, holding fills the bar, the release throws
                    if (!down && !charging && grabPress < 0) {
                        chargeArmed = true;
                    } else if (down && chargeArmed) {
                        chargeArmed = false;
                        grabPress = 0;
                    } else if (down && grabPress >= 0) {
                        grabPress++;
                        if (!charging && grabPress >= GRAB_TAP_TICKS) {
                            charging = true;
                            chargingGrab = true;
                            chargeTicks = 0;
                        } else if (charging) {
                            chargeTicks = Math.min(superTicks(), chargeTicks + 1);
                        }
                    } else if (!down && grabPress >= 0) {
                        grabPress = -1;
                        if (charging) {
                            charging = false;
                            FlashNetwork.CHANNEL.sendToServer(new com.flash.network.HulkThrowPacket(chargeFraction(),
                                    chargeTicks >= superTicks()));
                        } else {
                            // just lets go
                            FlashNetwork.CHANNEL.sendToServer(new com.flash.network.HulkThrowPacket(-1.0F, false));
                        }
                    }
                    KEY_DOWN[i] = down;
                    continue;
                }
                if (!down && !charging) {
                    chargeArmed = true;
                } else if (down && chargeArmed) {
                    charging = true;
                    chargingGrab = grab;
                    chargeArmed = false;
                    chargeTicks = 0;
                } else if (down && charging) {
                    chargeTicks = Math.min(grab ? superTicks() : CHARGE_TICKS, chargeTicks + 1);
                } else if (!down && charging) {
                    charging = false;
                    FlashNetwork.CHANNEL.sendToServer(new com.flash.network.HulkThrowPacket(chargeFraction(),
                            grab && chargeTicks >= superTicks()));
                }
                KEY_DOWN[i] = down;
                continue;
            }
            if ((rock && !holdingCreature) || (grab && !holdingRock)) {
                if (charging && chargingGrab == grab) {
                    charging = false;
                }
                if (!holdingRock && !holdingCreature) {
                    chargeArmed = false;
                    grabPress = -1;
                }
            }
            if (down && !KEY_DOWN[i]) {
                FlashNetwork.CHANNEL.sendToServer(new com.flash.network.HulkAbilityPacket(i));
            }
            KEY_DOWN[i] = down;
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        HulkState.resetClient();
        HulkAnimator.clear();
        scaledDepth = 0;
    }

    /** First person: the Hulk's arm (same pose vanilla gives the hand; the skin arm is not drawn). */
    static void drawArm(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player,
                        PlayerModel<AbstractClientPlayer> parent, HumanoidArm arm) {
        parent.attackTime = 0.0F;
        parent.crouching = false;
        parent.swimAmount = 0.0F;
        parent.setupAnim(player, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F);
        parent.rightArm.xRot = 0.0F;
        parent.leftArm.xRot = 0.0F;
        HulkAnimator.renderArm(pose, buffers, light, parent, arm);
    }

    /** Third person: the Hulk's body over the skin. */
    public static class Layer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

        public Layer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player,
                           float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                           float netHeadYaw, float headPitch) {
            if (player.isInvisible() || !hulkVisible(player)) {
                return;
            }
            HulkAnimator.render(pose, buffers, light, player, limbSwing, limbSwingAmount, partialTick, ageInTicks,
                    netHeadYaw, headPitch);
        }
    }
}
