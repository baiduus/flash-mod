package com.flash.infinity;

import com.flash.FlashMod;
import com.flash.network.FlashNetwork;
import com.flash.network.InfinitySyncPacket;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

/**
 * The Reality Stone: copying a block and pasting it over others (or turning things into bubbles), true invisibility,
 * changing size (the original uses Pehkui: here the mod does it itself: the body, its reach and its speed, gliding to
 * the new size), the weather. Lead into gold.
 */
@Mod.EventBusSubscriber(modid = FlashMod.MODID)
public final class RealityStone {

    private static final String SAVED = "flash_reality_block";
    private static final String SCALE = "flash_scale";
    private static final UUID REACH_ID = UUID.fromString("8a3b9d10-2c41-4b7e-9f0a-1d2e3f405162");
    private static final UUID ENTITY_REACH_ID = UUID.fromString("8a3b9d10-2c41-4b7e-9f0a-1d2e3f405163");
    private static final UUID SPEED_ID = UUID.fromString("8a3b9d10-2c41-4b7e-9f0a-1d2e3f405164");

    private RealityStone() {
    }

    // ------------------------------------------------------------------
    // Copy and paste
    // ------------------------------------------------------------------

    private static BlockHitResult look(ServerPlayer p, double range) {
        Vec3 eye = p.getEyePosition();
        return p.level().clip(new ClipContext(eye, eye.add(p.getLookAngle().scale(range)), ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE, p));
    }

    static void copy(ServerPlayer p, InfinityServer.State s, InfPowers.Ability a) {
        BlockHitResult hit = look(p, 64.0D);
        String air = InfPowers.str(a.json, "air_unlock", "");
        BlockState st;
        if (hit.getType() == HitResult.Type.MISS) {
            InfPowers.Ability au = air.isEmpty() ? null : a.power.ability(air);
            if (au == null || !InfinityPowers.unlocked(s, au)) {
                return;
            }
            st = Blocks.AIR.defaultBlockState();
        } else {
            st = p.level().getBlockState(hit.getBlockPos());
        }
        p.getPersistentData().put(SAVED, NbtUtils.writeBlockState(st));
        SpaceStone.sound(p.level(), p.position(), "reality_stone", 1.0F, 1.2F);
        p.displayClientMessage(Component.translatable("message.flash.infinity.reality_saved", st.getBlock().getName()),
                true);
        CompoundTag t = new CompoundTag();
        t.put("reality_block", NbtUtils.writeBlockState(st));
        FlashNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new InfinitySyncPacket(t));
    }

    public static BlockState saved(Player p) {
        if (!p.getPersistentData().contains(SAVED)) {
            return null;
        }
        return NbtUtils.readBlockState(p.level().holderLookup(net.minecraft.core.registries.Registries.BLOCK),
                p.getPersistentData().getCompound(SAVED));
    }

    static void paste(ServerPlayer p, InfPowers.Ability a) {
        BlockState st = saved(p);
        if (st == null) {
            p.displayClientMessage(Component.translatable("message.flash.infinity.reality_nothing"), true);
            return;
        }
        ServerLevel level = p.serverLevel();
        int radius = p.isShiftKeyDown() ? (a.json.has("sneak_radius") ? a.json.get("sneak_radius").getAsInt() : 3)
                : 1;
        BlockHitResult hit = look(p, 64.0D);
        if (st.isAir()) {
            // air: the looked-at entity or block becomes bubbles
            Vec3 eye = p.getEyePosition();
            Vec3 end = hit.getType() == HitResult.Type.MISS ? eye.add(p.getLookAngle().scale(64.0D))
                    : hit.getLocation();
            EntityHitResult eh = ProjectileUtil.getEntityHitResult(p, eye, end, p.getBoundingBox().expandTowards(
                    end.subtract(eye)).inflate(1.0D), e -> !e.isSpectator() && e != p, eye.distanceToSqr(end));
            if (eh != null) {
                bubbles(level, eh.getEntity().position().add(0.0D, eh.getEntity().getBbHeight() * 0.5D, 0.0D));
                Entity e = eh.getEntity();
                if (InfinityImmunity.immune(e, "reality")) {
                    return;
                }
                if (e instanceof LivingEntity l) {
                    l.hurt(level.damageSources().indirectMagic(p, p), Float.MAX_VALUE);
                    if (l.isAlive() && !(l instanceof Player)) {
                        l.discard();
                    }
                } else {
                    e.discard();
                }
                return;
            }
        }
        if (hit.getType() != HitResult.Type.BLOCK) {
            return;
        }
        BlockPos c = hit.getBlockPos();
        int changed = 0;
        for (BlockPos pos : BlockPos.betweenClosed(c.offset(-radius + 1, -radius + 1, -radius + 1),
                c.offset(radius - 1, radius - 1, radius - 1))) {
            if (radius > 1 && pos.distSqr(c) > (radius - 0.5D) * (radius - 0.5D)) {
                continue;
            }
            BlockState old = level.getBlockState(pos);
            if (old.isAir() || old.getDestroySpeed(level, pos) < 0.0F || !level.mayInteract(p, pos)
                    || !p.mayBuild()) {
                continue;
            }
            if (st.isAir()) {
                bubbles(level, Vec3.atCenterOf(pos));
            } else if (st.is(Blocks.GOLD_BLOCK) && isLead(old)) {
                InfinityAdvancements.grant(p, "lead_to_gold");
            }
            level.setBlock(pos.immutable(), st, Block.UPDATE_ALL);
            changed++;
        }
        if (changed > 0) {
            SpaceStone.sound(level, Vec3.atCenterOf(c), "reality_stone", 1.0F, 1.0F);
            InfinityAdvancements.grant(p, "screw_equal_exchange");
        }
        // gold pasted on dropped lead: gold
        if (st.is(Blocks.GOLD_BLOCK)) {
            for (ItemEntity it : level.getEntitiesOfClass(ItemEntity.class, new net.minecraft.world.phys.AABB(c)
                    .inflate(radius))) {
                if (it.getItem().is(ItemTags.create(new ResourceLocation("forge", "ingots/lead")))) {
                    it.setItem(new ItemStack(Items.GOLD_INGOT, it.getItem().getCount()));
                    InfinityAdvancements.grant(p, "lead_to_gold");
                }
            }
        }
    }

    private static boolean isLead(BlockState st) {
        return st.is(BlockTags.create(new ResourceLocation("forge", "storage_blocks/lead")))
                || st.is(BlockTags.create(new ResourceLocation("forge", "ores/lead")));
    }

    private static void bubbles(ServerLevel level, Vec3 at) {
        level.sendParticles(ParticleTypes.BUBBLE_POP, at.x, at.y, at.z, 30, 0.4D, 0.4D, 0.4D, 0.05D);
        level.sendParticles(ParticleTypes.BUBBLE, at.x, at.y, at.z, 20, 0.4D, 0.4D, 0.4D, 0.05D);
        SpaceStone.sound(level, at, "reality_stone_bubble", 1.0F, 1.0F);
    }

    // ------------------------------------------------------------------
    // Size
    // ------------------------------------------------------------------

    /** The scale the player glides to (from its SizeCycle step and the size ability's steps). */
    static float target(ServerPlayer p, InfPowers.Ability a) {
        int score = InfinityServer.score(p, a.json.has("property") ? a.json.get("property").getAsString()
                : "SizeCycle");
        var steps = a.json.getAsJsonArray("steps");
        int first = a.json.has("first_step") ? a.json.get("first_step").getAsInt() : 0;
        int i = Math.max(0, Math.min(steps.size() - 1, score - first));
        return steps.get(i).getAsFloat();
    }

    static void scale(ServerPlayer p, InfPowers.Ability a, boolean on) {
        boolean motion = InfinityPowers.strings(a.json.get("scale_types")).contains("motion");
        float want = on ? target(p, a) : 1.0F;
        if (motion) {
            AttributeInstance sp = p.getAttribute(Attributes.MOVEMENT_SPEED);
            if (sp != null) {
                sp.removeModifier(SPEED_ID);
                if (want != 1.0F) {
                    sp.addTransientModifier(new AttributeModifier(SPEED_ID, "flash_reality_size", want - 1.0F,
                            AttributeModifier.Operation.MULTIPLY_TOTAL));
                }
            }
            return;
        }
        CompoundTag d = p.getPersistentData();
        float cur = d.contains(SCALE) ? d.getFloat(SCALE) : 1.0F;
        int duration = a.json.has("duration") ? a.json.get("duration").getAsInt() : 10;
        float step = Math.max(0.02F, Math.abs(want - cur) / Math.max(1, duration));
        float next = cur < want ? Math.min(want, cur + step) : Math.max(want, cur - step);
        if (Math.abs(next - cur) > 1.0E-4F || !d.contains(SCALE)) {
            setScale(p, next);
        }
    }

    public static float scaleOf(Entity e) {
        CompoundTag d = e.getPersistentData();
        return d.contains(SCALE) ? d.getFloat(SCALE) : 1.0F;
    }

    static void setScale(ServerPlayer p, float s) {
        if (Math.abs(s - 1.0F) < 1.0E-4F) {
            p.getPersistentData().remove(SCALE);
        } else {
            p.getPersistentData().putFloat(SCALE, s);
        }
        reach(p, s);
        p.refreshDimensions();
        CompoundTag t = new CompoundTag();
        t.putInt("scale_entity", p.getId());
        t.putFloat("scale", s);
        FlashNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> p), new InfinitySyncPacket(t));
    }

    private static void reach(ServerPlayer p, float s) {
        for (var pair : new Object[][] {{ForgeMod.BLOCK_REACH.get(), REACH_ID}, {ForgeMod.ENTITY_REACH.get(),
                ENTITY_REACH_ID}}) {
            AttributeInstance inst = p.getAttribute((net.minecraft.world.entity.ai.attributes.Attribute) pair[0]);
            if (inst == null) {
                continue;
            }
            inst.removeModifier((UUID) pair[1]);
            if (s != 1.0F) {
                inst.addTransientModifier(new AttributeModifier((UUID) pair[1], "flash_reality_size", s - 1.0F,
                        AttributeModifier.Operation.MULTIPLY_TOTAL));
            }
        }
    }

    /** The body itself, scaled. */
    @SubscribeEvent
    public static void onSize(EntityEvent.Size event) {
        Entity e = event.getEntity();
        if (!(e instanceof Player)) {
            return;
        }
        float s = e.level() != null && e.level().isClientSide ? clientScale.apply(e) : scaleOf(e);
        if (Math.abs(s - 1.0F) > 1.0E-4F) {
            event.setNewSize(event.getNewSize().scale(s));
            event.setNewEyeHeight(event.getNewEyeHeight() * s);
        }
    }

    /** The client's scales (RealityClient). */
    public static java.util.function.Function<Entity, Float> clientScale = e -> 1.0F;

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent event) {
        // no size ability on: back to normal
        if (event.phase == TickEvent.Phase.END && event.player instanceof ServerPlayer p && p.tickCount % 5 == 0
                && p.getPersistentData().contains(SCALE) && !MindStone.has(p, "flash:dynamic_scale")) {
            float cur = scaleOf(p);
            float next = cur < 1.0F ? Math.min(1.0F, cur + 0.1F) : Math.max(1.0F, cur - 0.4F);
            setScale(p, next);
        }
    }

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof ServerPlayer target && event.getEntity() instanceof ServerPlayer viewer
                && target.getPersistentData().contains(SCALE)) {
            CompoundTag t = new CompoundTag();
            t.putInt("scale_entity", target.getId());
            t.putFloat("scale", scaleOf(target));
            FlashNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> viewer), new InfinitySyncPacket(t));
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && p.getPersistentData().contains(SCALE)) {
            setScale(p, scaleOf(p));
        }
        if (event.getEntity() instanceof ServerPlayer p && p.getPersistentData().contains(SAVED)) {
            CompoundTag t = new CompoundTag();
            t.put("reality_block", p.getPersistentData().getCompound(SAVED).copy());
            FlashNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new InfinitySyncPacket(t));
        }
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        CompoundTag old = event.getOriginal().getPersistentData();
        if (old.contains(SAVED)) {
            event.getEntity().getPersistentData().put(SAVED, old.getCompound(SAVED).copy());
        }
    }

    /** infinity:score_step: the wheel moves a score between min and max, with a click. */
    static void scoreStep(ServerPlayer p, InfPowers.Ability a) {
        String obj = a.json.get("objective").getAsString();
        int v = InfinityServer.score(p, obj) + a.json.get("amount").getAsInt();
        v = Math.max(a.json.get("min").getAsInt(), Math.min(a.json.get("max").getAsInt(), v));
        InfinityServer.setScore(p, obj, v);
        if (a.json.has("sound")) {
            var se = net.minecraftforge.registries.ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation(
                    a.json.get("sound").getAsString()));
            if (se != null) {
                p.level().playSound(null, p.blockPosition(), se, SoundSource.PLAYERS, 0.6F, 1.0F);
            }
        }
        SpaceStone.sound(p.level(), p.position(), "reality_stone_size", 0.5F, 1.0F);
    }

    /** palladium:invisibility: hidden for real (the effect, and drawn nowhere: RealityClient). */
    static void invisible(ServerPlayer p, boolean on) {
        if (on) {
            p.setInvisible(true);
        } else if (!p.hasEffect(net.minecraft.world.effect.MobEffects.INVISIBILITY)) {
            p.setInvisible(false);
        }
    }
}
