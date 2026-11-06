package com.flash.client;

import com.flash.FlashMod;
import com.flash.client.geo.GeoAnimation;
import com.flash.client.geo.GeoModel;
import com.flash.hulk.HulkAbility;
import com.flash.hulk.HulkState;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.HumanoidArm;

/**
 * Draws the Hulk with the geometry, textures and animations of the Hulk asset pack
 * (hulk.geo.json / hulk.animation.json and the transformation model): the
 * movement animation is picked from what the player is doing, abilities play
 * their own animation (sent by the server) and turning into / out of the Hulk
 * plays the 2-second transformation.
 */
public final class HulkAnimator {

    public static final ResourceLocation TEXTURE = new ResourceLocation(FlashMod.MODID, "textures/entity/hulk/hulk.png");
    public static final ResourceLocation TRANSFORM_TEXTURE =
            new ResourceLocation(FlashMod.MODID, "textures/entity/hulk/hulk_transform.png");
    private static final ResourceLocation GEO = new ResourceLocation(FlashMod.MODID, "geo/hulk.geo.json");
    private static final ResourceLocation TRANSFORM_GEO =
            new ResourceLocation(FlashMod.MODID, "geo/hulk_transformation.geo.json");
    private static final ResourceLocation ANIMS = new ResourceLocation(FlashMod.MODID, "animations/hulk.animation.json");
    private static final ResourceLocation TRANSFORM_ANIMS =
            new ResourceLocation(FlashMod.MODID, "animations/hulk_transformation.animation.json");

    /** Transformation length (ticks): the files' "transform" / "detransform" are 2 s. */
    public static final int TRANSFORM_TICKS = 40;

    private static GeoModel model;
    private static GeoModel transformModel;
    private static Map<String, GeoAnimation> anims;
    private static Map<String, GeoAnimation> transformAnims;

    /**
     * An animation being played: from {@code offset} seconds, for {@code ticks} ticks when > 0
     * (looping), otherwise until its end.
     */
    private record Playing(String animation, long start, int ticks, float offset) {
        Playing(String animation, long start) {
            this(animation, start, 0, 0.0F);
        }
    }

    /**
     * Holding a rock (with its size) or a creature since {@code start}; a small creature is
     * held in the right hand only.
     */
    private record Hold(boolean rock, long start, float size, boolean oneHand) {
    }

    private static final Map<UUID, Hold> HOLDS = new ConcurrentHashMap<>();
    /** Arm bones posed by the holding animation while the legs keep walking. */
    private static final java.util.Set<String> ARM_BONES = java.util.Set.of("RightArm", "RightArmBottom",
            "LeftArm", "LeftArmBottom", "RIGHT_ITEM", "LEFT_ITEM", "GrabPoint");
    private static final java.util.Set<String> RIGHT_ARM = java.util.Set.of("RightArm", "RightArmBottom",
            "RIGHT_ITEM", "GrabPoint");
    private static final String LIFT = "rubble_smash";
    /** Moment of "rubble_smash" with the arms up (the hold pose) and the start of the throw. */
    private static final float HOLD_POSE = 0.6F;
    private static final float THROW_FROM = 0.8F;

    private record Transform(boolean toHulk, long start, float hulkScale) {
    }

    private static final Map<UUID, Playing> PLAYING = new ConcurrentHashMap<>();
    private static final Map<UUID, Transform> TRANSFORMS = new ConcurrentHashMap<>();
    /** Game time each player left the ground (for the jump animation). */
    private static final Map<UUID, Long> AIRBORNE = new ConcurrentHashMap<>();
    private static final Map<UUID, Boolean> LEFT_SWING = new ConcurrentHashMap<>();
    private static final Map<UUID, Float> LAST_ATTACK = new ConcurrentHashMap<>();

    private HulkAnimator() {
    }

    // ------------------------------------------------------------------
    // Resources
    // ------------------------------------------------------------------

    private static void ensureLoaded() {
        if (model == null) {
            model = GeoModel.load(GEO, null);
            transformModel = GeoModel.load(TRANSFORM_GEO, "banner");
            anims = GeoAnimation.load(ANIMS);
            transformAnims = GeoAnimation.load(TRANSFORM_ANIMS);
        }
    }

    /** Resource reload: everything is read again on the next frame. */
    public static void reset() {
        model = null;
        transformModel = null;
        anims = null;
        transformAnims = null;
    }

    public static void clear() {
        HOLDS.clear();
        PLAYING.clear();
        TRANSFORMS.clear();
        AIRBORNE.clear();
        LEFT_SWING.clear();
        LAST_ATTACK.clear();
    }

    /** Normalising factor: the Hulk model is drawn as tall as a player model (32 px) times the Hulk scale. */
    private static float fit(GeoModel m) {
        float h = m.height();
        return h > 1.0F ? 32.0F / h : 1.0F;
    }

    private static long now() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null ? mc.level.getGameTime() : 0L;
    }

    // ------------------------------------------------------------------
    // Events from the network
    // ------------------------------------------------------------------

    /** Ability used (server packet): its animation plays for that player. */
    public static void playAbility(UUID player, int code) {
        long now = now();
        switch (code) {
            case com.flash.hulk.HulkAbilities.ANIM_HOLD_ROCK -> {
                HOLDS.put(player, new Hold(true, now, 1.0F, false));
                PLAYING.remove(player);
                return;
            }
            case com.flash.hulk.HulkAbilities.ANIM_HOLD_ENTITY -> {
                HOLDS.put(player, new Hold(false, now, 1.0F, false));
                PLAYING.remove(player);
                return;
            }
            case com.flash.hulk.HulkAbilities.ANIM_HOLD_SMALL -> {
                HOLDS.put(player, new Hold(false, now, 1.0F, true));
                PLAYING.remove(player);
                return;
            }
            case com.flash.hulk.HulkAbilities.ANIM_THROW_ROCK, com.flash.hulk.HulkAbilities.ANIM_THROW_ENTITY -> {
                HOLDS.remove(player);
                PLAYING.put(player, new Playing(LIFT, now, 0, THROW_FROM));
                return;
            }
            case com.flash.hulk.HulkAbilities.ANIM_RELEASE -> {
                HOLDS.remove(player);
                return;
            }
            default -> {
            }
        }
        if (code >= 200 && code < 300) {
            // rock held, with its size
            HOLDS.put(player, new Hold(true, now, (code - 200) / 10.0F, false));
            PLAYING.remove(player);
            return;
        }
        HulkAbility ability = HulkAbility.byId(code);
        if (ability == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        switch (ability) {
            case GROUND_SMASH -> {
                net.minecraft.world.entity.player.Player p = mc.level != null ? mc.level.getPlayerByUUID(player) : null;
                PLAYING.put(player, new Playing(p != null && !p.onGround() ? "smash_air" : "smash", now));
            }
            // the charge simply uses the Hulk's run (10 ticks)
            case CHARGE -> PLAYING.put(player, new Playing("run", now, 10, 0.0F));
            case THUNDERCLAP -> PLAYING.put(player, new Playing("thunderclap", now));
            case GAMMA_WAVE -> PLAYING.put(player, new Playing("gamma_bombardment", now));
            case WORLD_BREAKER -> PLAYING.put(player, new Playing("tectonic_fury", now));
            default -> {
                // roar and gamma fury: no animation (just the roar)
            }
        }
    }

    /** The sync packet saw the form change: plays the transformation (not on login). */
    public static void onFormChange(UUID player, boolean toHulk, float hulkScale) {
        TRANSFORMS.put(player, new Transform(toHulk, now(), hulkScale));
        PLAYING.remove(player);
    }

    /** True while the transformation (either way) is playing: the skin stays hidden. */
    public static boolean transforming(AbstractClientPlayer player) {
        Transform t = TRANSFORMS.get(player.getUUID());
        if (t == null) {
            return false;
        }
        if (now() - t.start() >= TRANSFORM_TICKS || now() < t.start()) {
            TRANSFORMS.remove(player.getUUID());
            return false;
        }
        return true;
    }

    // ------------------------------------------------------------------
    // Third person
    // ------------------------------------------------------------------

    public static void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player,
                              float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                              float netHeadYaw, float headPitch) {
        ensureLoaded();
        int overlay = LivingEntityRenderer.getOverlayCoords(player, 0.0F);
        Transform tr = transforming(player) ? TRANSFORMS.get(player.getUUID()) : null;
        if (tr != null) {
            renderTransform(pose, buffers, light, overlay, player, tr, partialTick);
            return;
        }
        if (!HulkState.isTransformed(player)) {
            return;
        }
        GeoModel m = model;
        m.reset();
        animate(m, player, limbSwing, limbSwingAmount, partialTick, ageInTicks);
        GeoModel.Bone head = m.bone("Head");
        if (head != null) {
            head.ary += netHeadYaw;
            head.arx += headPitch;
        }
        float k = fit(m);
        pose.pushPose();
        // scale around the feet (y = 24 px)
        pose.translate(0.0F, 1.5F, 0.0F);
        pose.scale(k, k, k);
        pose.translate(0.0F, -1.5F, 0.0F);
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        m.render(pose, vc, light, overlay, 0, 1.0F, 1.0F, 1.0F, 1.0F);
        Hold hold = HOLDS.get(player.getUUID());
        if (hold != null && hold.rock() && (now() + partialTick - hold.start()) / 20.0F >= 0.3F) {
            drawHeldRock(pose, buffers, light, m, hold.size());
        }
        pose.popPose();
    }

    private static void animate(GeoModel m, AbstractClientPlayer player, float limbSwing, float limbSwingAmount,
                                float partialTick, float ageInTicks) {
        UUID id = player.getUUID();
        float time = (now() + partialTick);
        Playing playing = PLAYING.get(id);
        if (playing != null) {
            GeoAnimation a = anims.get(playing.animation());
            float elapsed = (time - playing.start()) / 20.0F;
            boolean alive = a != null && elapsed >= 0.0F && (playing.ticks() > 0
                    ? elapsed * 20.0F < playing.ticks() : playing.offset() + elapsed < a.length);
            if (alive) {
                a.apply(m, playing.ticks() > 0 ? elapsed % a.length : playing.offset() + elapsed);
                return;
            }
            PLAYING.remove(id);
        }
        Hold hold = HOLDS.get(id);
        if (hold != null) {
            GeoAnimation lift = anims.get(LIFT);
            float t = (time - hold.start()) / 20.0F;
            if (lift != null && t < HOLD_POSE && !hold.oneHand()) {
                // ripping the rock out / grabbing: the whole body
                lift.apply(m, hold.rock() ? t : Math.max(0.3F, t));
                return;
            }
        }
        boolean onGround = player.onGround();
        if (onGround || player.isInWater() || player.isPassenger()) {
            AIRBORNE.remove(id);
        } else {
            AIRBORNE.putIfAbsent(id, now());
        }
        GeoAnimation base;
        float t = ageInTicks / 20.0F;
        if (player.isPassenger()) {
            base = anims.get("sit");
        } else if (player.isSwimming() || (player.isInWater() && !onGround)) {
            base = anims.get("swim");
        } else if (!onGround && AIRBORNE.containsKey(id)) {
            float air = (time - AIRBORNE.get(id)) / 20.0F;
            GeoAnimation jump = anims.get("jump_from_ground");
            if (jump != null && player.getDeltaMovement().y > 0.0D && air < jump.length) {
                base = jump;
                t = air;
            } else {
                base = anims.get("fall");
            }
        } else if (limbSwingAmount > 0.08F) {
            base = anims.get(player.isSprinting() ? "run" : "walk");
            // one walk cycle per vanilla leg cycle (2 pi / 0.6662)
            if (base != null) {
                t = limbSwing / 9.43F * base.length;
            }
        } else {
            base = anims.get("idle_pose");
        }
        if (base != null) {
            java.util.function.Predicate<String> free = hold == null ? null
                    : hold.oneHand() ? bone -> !RIGHT_ARM.contains(bone) : bone -> !ARM_BONES.contains(bone);
            base.apply(m, t, free);
        }
        if (hold != null) {
            if (hold.oneHand()) {
                // the small creature in the right hand, arm forward
                GeoAnimation grab = anims.get("hulk_grab");
                if (grab != null) {
                    grab.apply(m, 0.2F, RIGHT_ARM::contains);
                }
                return;
            }
            GeoAnimation lift = anims.get(LIFT);
            if (lift != null) {
                // arms up holding the rock / creature, the legs keep walking
                lift.apply(m, HOLD_POSE, ARM_BONES::contains);
            }
            return;
        }
        // punch: alternating right / left swing over the movement
        float attack = player.getAttackAnim(partialTick);
        float last = LAST_ATTACK.getOrDefault(id, 0.0F);
        if (attack > 0.0F && last == 0.0F) {
            LEFT_SWING.put(id, !LEFT_SWING.getOrDefault(id, Boolean.TRUE));
        }
        LAST_ATTACK.put(id, attack);
        if (attack > 0.0F) {
            boolean left = LEFT_SWING.getOrDefault(id, Boolean.FALSE);
            if (player.getMainArm() == HumanoidArm.LEFT) {
                left = !left;
            }
            GeoAnimation swing = anims.get(left ? "swing_updated_left" : "swing_updated_right");
            if (swing != null) {
                swing.apply(m, attack * swing.length);
            }
        }
    }

    /** The rock between the two hands (midpoint of the item bones). */
    private static void drawHeldRock(PoseStack pose, MultiBufferSource buffers, int light, GeoModel m, float size) {
        PoseStack right = new PoseStack();
        PoseStack left = new PoseStack();
        if (!m.transformToBone("RIGHT_ITEM", right) || !m.transformToBone("LEFT_ITEM", left)) {
            return;
        }
        org.joml.Vector3f a = right.last().pose().getTranslation(new org.joml.Vector3f());
        org.joml.Vector3f b = left.last().pose().getTranslation(new org.joml.Vector3f());
        pose.pushPose();
        // a little above the hands
        pose.translate((a.x() + b.x()) * 0.5F, (a.y() + b.y()) * 0.5F - 0.3F * size, (a.z() + b.z()) * 0.5F);
        HulkBoulderRenderer.drawRock(pose, buffers, light, 1.2F * size);
        pose.popPose();
    }

    private static void renderTransform(PoseStack pose, MultiBufferSource buffers, int light, int overlay,
                                        AbstractClientPlayer player, Transform tr, float partialTick) {
        GeoModel m = transformModel;
        GeoAnimation a = transformAnims.get(tr.toHulk() ? "transform" : "detransform");
        float seconds = (now() + partialTick - tr.start()) / 20.0F;
        m.reset();
        if (a != null) {
            a.apply(m, seconds);
        }
        // the player renderer already scales by the current Hulk size: Bruce must look
        // player-sized and the Hulk as big as after the transformation
        float s = Math.max(1.0F, tr.hulkScale());
        float k = fit(model != null ? model : m);
        float bruce = tr.toHulk() ? 1.0F / s : 1.0F;
        float hulk = tr.toHulk() ? k : s * k;
        float grow = Math.max(0.0F, Math.min(1.0F, (seconds - 0.3F) / 0.95F));
        if (!tr.toHulk()) {
            grow = 1.0F - Math.max(0.0F, Math.min(1.0F, (seconds - 0.5F) / 0.8F));
        }
        float f = bruce + (hulk - bruce) * grow;
        pose.pushPose();
        pose.translate(0.0F, 1.5F, 0.0F);
        pose.scale(f, f, f);
        pose.translate(0.0F, -1.5F, 0.0F);
        m.render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(TRANSFORM_TEXTURE)), light, overlay, 0,
                1.0F, 1.0F, 1.0F, 1.0F);
        // Bruce is drawn with the player's own skin
        m.render(pose, buffers.getBuffer(RenderType.entityTranslucent(player.getSkinTextureLocation())), light,
                overlay, 1, 1.0F, 1.0F, 1.0F, 1.0F);
        pose.popPose();
    }

    // ------------------------------------------------------------------
    // First person
    // ------------------------------------------------------------------

    /** The Hulk's arm (from the same model) placed where the vanilla first-person arm is. */
    public static void renderArm(PoseStack pose, MultiBufferSource buffers, int light,
                                 PlayerModel<AbstractClientPlayer> parent, HumanoidArm side) {
        ensureLoaded();
        GeoModel m = model;
        m.reset();
        GeoModel.Bone arm = m.bone(side == HumanoidArm.RIGHT ? "RightArm" : "LeftArm");
        if (arm == null) {
            return;
        }
        net.minecraft.client.model.geom.ModelPart vanilla = side == HumanoidArm.RIGHT ? parent.rightArm : parent.leftArm;
        // a straight arm in the place of the normal hand, only a little bigger than a player's
        float k = fit(m) * 1.05F;
        pose.pushPose();
        pose.translate(vanilla.x / 16.0F, vanilla.y / 16.0F, vanilla.z / 16.0F);
        pose.mulPose(new org.joml.Quaternionf().rotationZYX(vanilla.zRot, vanilla.yRot, vanilla.xRot));
        pose.scale(k, k, k);
        // the Hulk's shoulder pivot lands on the vanilla shoulder
        pose.translate(-arm.pivotX() / 16.0F, -arm.pivotY() / 16.0F, -arm.pivotZ() / 16.0F);
        m.renderBone(arm, pose, buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE)), light,
                OverlayTexture.NO_OVERLAY, 0, 1.0F, 1.0F, 1.0F, 1.0F, false);
        pose.popPose();
    }
}
