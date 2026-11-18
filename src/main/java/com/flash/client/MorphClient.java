package com.flash.client;

import com.flash.FlashMod;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/** Players changed by the Manipulation of Reality (PlayerMorph): drawn as the creature instead of themselves. */
@Mod.EventBusSubscriber(modid = FlashMod.MODID, value = Dist.CLIENT)
public final class MorphClient {

    private static final Map<Integer, EntityType<?>> TYPES = new HashMap<>();
    private static final Map<Integer, LivingEntity> BODIES = new HashMap<>();

    private MorphClient() {
    }

    public static void set(int id, String type) {
        BODIES.remove(id);
        if (type == null || type.isEmpty()) {
            TYPES.remove(id);
            return;
        }
        EntityType<?> t = ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation(type));
        if (t != null) {
            TYPES.put(id, t);
        }
    }

    private static LivingEntity body(Player p) {
        EntityType<?> t = TYPES.get(p.getId());
        if (t == null) {
            return null;
        }
        LivingEntity b = BODIES.get(p.getId());
        if (b == null || b.level() != p.level()) {
            Entity e = t.create(p.level());
            if (!(e instanceof LivingEntity le)) {
                return null;
            }
            b = le;
            b.setNoGravity(true);
            b.setSilent(true);
            BODIES.put(p.getId(), b);
        }
        return b;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            TYPES.clear();
            BODIES.clear();
            return;
        }
        for (Map.Entry<Integer, LivingEntity> e : BODIES.entrySet()) {
            if (!(mc.level.getEntity(e.getKey()) instanceof Player p)) {
                continue;
            }
            LivingEntity b = e.getValue();
            double dx = p.getX() - p.xo;
            double dz = p.getZ() - p.zo;
            b.walkAnimation.update((float) Math.min(1.0D, Math.sqrt(dx * dx + dz * dz) * 4.0D), 0.4F);
            b.tickCount = p.tickCount;
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRender(RenderPlayerEvent.Pre event) {
        Player p = event.getEntity();
        LivingEntity b = body(p);
        if (b == null) {
            return;
        }
        event.setCanceled(true);
        b.setPos(p.getX(), p.getY(), p.getZ());
        b.xo = p.xo;
        b.yo = p.yo;
        b.zo = p.zo;
        b.xOld = p.xOld;
        b.yOld = p.yOld;
        b.zOld = p.zOld;
        b.setYRot(p.getYRot());
        b.yRotO = p.yRotO;
        b.setXRot(p.getXRot());
        b.xRotO = p.xRotO;
        b.yBodyRot = p.yBodyRot;
        b.yBodyRotO = p.yBodyRotO;
        b.yHeadRot = p.yHeadRot;
        b.yHeadRotO = p.yHeadRotO;
        b.setOnGround(p.onGround());
        b.hurtTime = p.hurtTime;
        b.deathTime = p.deathTime;
        float yaw = net.minecraft.util.Mth.lerp(event.getPartialTick(), p.yRotO, p.getYRot());
        Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(b).render(b, yaw, event.getPartialTick(),
                event.getPoseStack(), event.getMultiBufferSource(), event.getPackedLight());
    }
}
