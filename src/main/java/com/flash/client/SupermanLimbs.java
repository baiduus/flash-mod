package com.flash.client;

import com.flash.FlashMod;
import com.flash.superman.SupermanCut;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

/**
 * Laser cut, client side: the cut parts of humanoid models are hidden, the cut limb falls off (drawn with the
 * creature's own model part and texture), tumbles and lies on the ground, and blood (bone dust for skeletons,
 * green for creepers and slimes...) spurts from the wound.
 */
@Mod.EventBusSubscriber(modid = FlashMod.MODID, value = Dist.CLIENT)
public final class SupermanLimbs {

    private static final int LIFE = 600;
    private static final Map<Integer, Integer> MASKS = new HashMap<>();
    private static final List<Piece> PIECES = new ArrayList<>();

    private static final class Piece {
        final ModelPart part;
        final ModelPart overlay;
        final ResourceLocation texture;
        final Vector3f color;
        float center;
        Vec3 pos;
        Vec3 prev;
        Vec3 vel;
        float rx;
        float ry;
        float rz;
        float prx;
        float pry;
        float prz;
        float sx;
        float sy;
        float sz;
        int age;
        boolean resting;

        Piece(ModelPart part, ModelPart overlay, ResourceLocation texture, Vector3f color, Vec3 pos, Vec3 vel,
              float yaw) {
            this.part = part;
            this.overlay = overlay;
            this.texture = texture;
            this.color = color;
            this.pos = pos;
            this.prev = pos;
            this.vel = vel;
            this.ry = yaw;
            this.pry = yaw;
        }
    }

    private SupermanLimbs() {
    }

    public static void reset() {
        MASKS.clear();
        PIECES.clear();
    }

    private static Vector3f bloodColor(Entity e) {
        if (e instanceof AbstractSkeleton) {
            return new Vector3f(0.88F, 0.86F, 0.8F);
        }
        if (e instanceof Creeper || e instanceof Slime) {
            return new Vector3f(0.3F, 0.75F, 0.2F);
        }
        if (e instanceof EnderMan) {
            return new Vector3f(0.6F, 0.2F, 0.75F);
        }
        if (e instanceof Zombie) {
            return new Vector3f(0.45F, 0.05F, 0.04F);
        }
        return new Vector3f(0.7F, 0.02F, 0.02F);
    }

    private static void blood(ClientLevel level, Vector3f color, double x, double y, double z, int n, double speed) {
        RandomSource r = level.random;
        DustParticleOptions dust = new DustParticleOptions(color, 1.3F);
        for (int i = 0; i < n; i++) {
            level.addParticle(dust, x + (r.nextDouble() - 0.5D) * 0.2D, y + (r.nextDouble() - 0.5D) * 0.2D,
                    z + (r.nextDouble() - 0.5D) * 0.2D, (r.nextDouble() - 0.5D) * speed, r.nextDouble() * speed,
                    (r.nextDouble() - 0.5D) * speed);
        }
    }

    private static HumanoidModel<?> humanoid(Entity e) {
        EntityRenderer<?> renderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(e);
        if (renderer instanceof LivingEntityRenderer<?, ?> lr && lr.getModel() instanceof HumanoidModel<?> hm) {
            return hm;
        }
        return null;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static ResourceLocation texture(Entity e) {
        EntityRenderer renderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(e);
        return renderer.getTextureLocation(e);
    }

    public static void onCut(int entityId, int mask, int limb, float x, float y, float z) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            return;
        }
        if (limb < 0) {
            // respawned: all limbs back
            MASKS.remove(entityId);
            return;
        }
        Entity e = level.getEntity(entityId);
        Vector3f color = e != null ? bloodColor(e) : new Vector3f(0.7F, 0.02F, 0.02F);
        if (limb == SupermanCut.BLEED || e == null) {
            blood(level, color, x, y, z, 14, 0.25D);
            return;
        }
        MASKS.put(entityId, mask);
        HumanoidModel<?> model = humanoid(e);
        blood(level, color, x, y, z, 30, 0.35D);
        if (model == null) {
            return;
        }
        ModelPart part;
        ModelPart overlay = null;
        double height;
        switch (limb) {
            case SupermanCut.HEAD -> {
                part = model.head;
                overlay = model.hat;
                height = 0.9D;
            }
            case SupermanCut.RIGHT_ARM -> {
                part = model.rightArm;
                overlay = model instanceof PlayerModel<?> pm ? pm.rightSleeve : null;
                height = 0.72D;
            }
            case SupermanCut.LEFT_ARM -> {
                part = model.leftArm;
                overlay = model instanceof PlayerModel<?> pm ? pm.leftSleeve : null;
                height = 0.72D;
            }
            case SupermanCut.RIGHT_LEG -> {
                part = model.rightLeg;
                overlay = model instanceof PlayerModel<?> pm ? pm.rightPants : null;
                height = 0.38D;
            }
            default -> {
                part = model.leftLeg;
                overlay = model instanceof PlayerModel<?> pm ? pm.leftPants : null;
                height = 0.38D;
            }
        }
        float yaw = e instanceof LivingEntity le ? le.yBodyRot : e.getYRot();
        double side = limb == SupermanCut.RIGHT_ARM || limb == SupermanCut.RIGHT_LEG ? -1.0D
                : limb == SupermanCut.HEAD ? 0.0D : 1.0D;
        double yr = Math.toRadians(yaw);
        Vec3 sideDir = new Vec3(Math.cos(yr), 0.0D, Math.sin(yr)).scale(side);
        Vec3 at = e.position().add(sideDir.scale(0.3D * e.getBbWidth() / 0.6D)).add(0.0D, e.getBbHeight() * height, 0.0D);
        RandomSource r = level.random;
        Vec3 vel = sideDir.scale(0.12D).add((r.nextDouble() - 0.5D) * 0.1D, 0.18D + r.nextDouble() * 0.1D,
                (r.nextDouble() - 0.5D) * 0.1D);
        Piece p = new Piece(part, overlay, texture(e), color, at, vel, yaw);
        p.center = limb == SupermanCut.HEAD ? -0.25F : limb == SupermanCut.RIGHT_ARM || limb == SupermanCut.LEFT_ARM
                ? 0.25F : 0.375F;
        p.sx = (r.nextFloat() - 0.5F) * 30.0F;
        p.sy = (r.nextFloat() - 0.5F) * 20.0F;
        p.sz = (r.nextFloat() - 0.5F) * 30.0F + (float) side * 12.0F;
        PIECES.add(p);
        if (PIECES.size() > 64) {
            PIECES.remove(0);
        }
    }

    // ------------------------------------------------------------------
    // Hiding the cut parts
    // ------------------------------------------------------------------

    private static final List<ModelPart> HIDDEN = new ArrayList<>();

    @SubscribeEvent
    public static void onRenderLivingPre(RenderLivingEvent.Pre<?, ?> event) {
        Integer mask = MASKS.get(event.getEntity().getId());
        if (mask == null || mask == 0) {
            return;
        }
        EntityModel<?> m = event.getRenderer().getModel();
        if (!(m instanceof HumanoidModel<?> hm)) {
            return;
        }
        PlayerModel<?> pm = m instanceof PlayerModel<?> p ? p : null;
        hide(mask, SupermanCut.HEAD, hm.head, hm.hat);
        hide(mask, SupermanCut.RIGHT_ARM, hm.rightArm, pm != null ? pm.rightSleeve : null);
        hide(mask, SupermanCut.LEFT_ARM, hm.leftArm, pm != null ? pm.leftSleeve : null);
        hide(mask, SupermanCut.RIGHT_LEG, hm.rightLeg, pm != null ? pm.rightPants : null);
        hide(mask, SupermanCut.LEFT_LEG, hm.leftLeg, pm != null ? pm.leftPants : null);
        // the stump bleeds a little
        if (event.getEntity().tickCount % 6 == 0 && event.getEntity().level() instanceof ClientLevel level
                && !Minecraft.getInstance().isPaused()) {
            Entity e = event.getEntity();
            blood(level, bloodColor(e), e.getX(), e.getY() + e.getBbHeight() * 0.6D, e.getZ(), 1, 0.05D);
        }
    }

    private static void hide(int mask, int bit, ModelPart a, ModelPart b) {
        if ((mask & bit) == 0) {
            return;
        }
        if (a.visible) {
            a.visible = false;
            HIDDEN.add(a);
        }
        if (b != null && b.visible) {
            b.visible = false;
            HIDDEN.add(b);
        }
    }

    @SubscribeEvent
    public static void onRenderLivingPost(RenderLivingEvent.Post<?, ?> event) {
        if (!HIDDEN.isEmpty()) {
            for (ModelPart p : HIDDEN) {
                p.visible = true;
            }
            HIDDEN.clear();
        }
    }

    // ------------------------------------------------------------------
    // Falling limbs
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.phase != TickEvent.Phase.END || mc.isPaused()) {
            return;
        }
        ClientLevel level = mc.level;
        if (level == null) {
            reset();
            return;
        }
        if (level.getGameTime() % 40L == 0L) {
            MASKS.keySet().removeIf(id -> level.getEntity(id) == null);
        }
        Iterator<Piece> it = PIECES.iterator();
        while (it.hasNext()) {
            Piece p = it.next();
            p.prev = p.pos;
            p.prx = p.rx;
            p.pry = p.ry;
            p.prz = p.rz;
            if (++p.age > LIFE) {
                it.remove();
                continue;
            }
            if (p.age < 30 && p.age % 3 == 0) {
                blood(level, p.color, p.pos.x, p.pos.y, p.pos.z, 1, 0.03D);
            }
            if (p.resting) {
                continue;
            }
            p.vel = p.vel.add(0.0D, -0.05D, 0.0D).scale(0.98D);
            Vec3 next = p.pos.add(p.vel);
            BlockPos below = BlockPos.containing(next.x, next.y - 0.13D, next.z);
            var shape = level.getBlockState(below).getCollisionShape(level, below);
            if (!shape.isEmpty() && next.y - 0.13D <= below.getY() + shape.max(net.minecraft.core.Direction.Axis.Y)) {
                double top = below.getY() + shape.max(net.minecraft.core.Direction.Axis.Y) + 0.13D;
                next = new Vec3(next.x, top, next.z);
                p.vel = new Vec3(p.vel.x * 0.5D, -p.vel.y * 0.2D, p.vel.z * 0.5D);
                p.sx *= 0.5F;
                p.sy *= 0.5F;
                p.sz *= 0.5F;
                // lie flat
                if (Math.abs(p.vel.y) < 0.04D && p.vel.horizontalDistanceSqr() < 0.002D) {
                    p.resting = true;
                    p.rx = 90.0F;
                    p.rz = 0.0F;
                }
            } else {
                p.rx += p.sx;
                p.ry += p.sy;
                p.rz += p.sz;
            }
            p.pos = next;
        }
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES || PIECES.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            return;
        }
        float pt = event.getPartialTick();
        Vec3 cam = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        for (Piece p : PIECES) {
            double x = p.prev.x + (p.pos.x - p.prev.x) * pt;
            double y = p.prev.y + (p.pos.y - p.prev.y) * pt;
            double z = p.prev.z + (p.pos.z - p.prev.z) * pt;
            pose.pushPose();
            pose.translate(x - cam.x, y - cam.y, z - cam.z);
            pose.mulPose(Axis.YP.rotationDegrees(180.0F - (p.pry + (p.ry - p.pry) * pt)));
            pose.mulPose(Axis.XP.rotationDegrees(p.prx + (p.rx - p.prx) * pt));
            pose.mulPose(Axis.ZP.rotationDegrees(p.prz + (p.rz - p.prz) * pt));
            pose.scale(-1.0F, -1.0F, 1.0F);
            // centred on the limb (the part's pivot is the shoulder / hip / neck)
            pose.translate(0.0F, -p.center, 0.0F);
            int light = LevelRenderer.getLightColor(level, BlockPos.containing(x, y + 0.2D, z));
            var vc = buffers.getBuffer(RenderType.entityCutoutNoCull(p.texture));
            draw(p.part, pose, vc, light);
            if (p.overlay != null) {
                draw(p.overlay, pose, buffers.getBuffer(RenderType.entityTranslucent(p.texture)), light);
            }
            pose.popPose();
        }
        buffers.endBatch();
    }

    /** Renders a part shared with the creature's model at the origin, restoring its pose afterwards. */
    private static void draw(ModelPart part, PoseStack pose, com.mojang.blaze3d.vertex.VertexConsumer vc, int light) {
        float x = part.x;
        float y = part.y;
        float z = part.z;
        float xr = part.xRot;
        float yr = part.yRot;
        float zr = part.zRot;
        boolean visible = part.visible;
        part.setPos(0.0F, 0.0F, 0.0F);
        part.setRotation(0.0F, 0.0F, 0.0F);
        part.visible = true;
        part.render(pose, vc, light, OverlayTexture.NO_OVERLAY);
        part.setPos(x, y, z);
        part.setRotation(xr, yr, zr);
        part.visible = visible;
    }
}
