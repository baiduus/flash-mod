package com.flash.infinity;

import com.google.gson.JsonObject;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The energy beams (Palladium's energy_beam, the Power Stone's power_blast): from the eyes along the look, growing at
 * its speed up to max_distance; the first entity in its way is hurt (damage; the Power Stone's: 2^level, infinite
 * when maxed) and set on fire; where it meets blocks it lights fires and smelts them; the Power Stone's maxed beam
 * explodes. Drawn by the client (InfinityBeamRenderer).
 */
public final class InfinityBeams {

    private static final Map<String, Double> LENGTH = new HashMap<>();

    private InfinityBeams() {
    }

    private static String key(ServerPlayer p, InfPowers.Ability a) {
        return p.getUUID() + "/" + a.key();
    }

    /** The Power Blast locks on: the nearest creature within 25 degrees of the aim, in sight (client draws the same). */
    public static LivingEntity lock(net.minecraft.world.level.Level level, net.minecraft.world.entity.player.Player p,
                                    Vec3 eye, Vec3 look, double max) {
        LivingEntity best = null;
        double bestDot = Math.cos(Math.toRadians(25.0D));
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(max),
                e -> e != p && e.isAlive() && !e.isSpectator() && !(e instanceof net.minecraft.world.entity.decoration
                        .ArmorStand) && !(e instanceof net.minecraft.world.entity.player.Player pl && pl.isCreative()))) {
            Vec3 to = e.position().add(0.0D, e.getBbHeight() * 0.5D, 0.0D).subtract(eye);
            double d = to.length();
            if (d > max || d < 0.5D) {
                continue;
            }
            double dot = to.scale(1.0D / d).dot(look);
            if (dot > bestDot && level.clip(new ClipContext(eye, eye.add(to), ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE, p)).getType() == HitResult.Type.MISS) {
                bestDot = dot;
                best = e;
            }
        }
        return best;
    }

    static void tick(ServerPlayer p, InfPowers.Ability a, boolean on, float damage, boolean maxed) {
        String k = key(p, a);
        if (!on) {
            LENGTH.remove(k);
            return;
        }
        JsonObject j = a.json;
        double max = j.has("max_distance") ? j.get("max_distance").getAsDouble() : 30.0D;
        double speed = j.has("speed") ? j.get("speed").getAsDouble() : 1.0D;
        double len = Math.min(max, LENGTH.getOrDefault(k, 0.0D) + Math.max(0.1D, speed) * 4.0D);
        LENGTH.put(k, len);
        ServerLevel level = p.serverLevel();
        Vec3 eye = p.getEyePosition();
        Vec3 aim = p.getLookAngle();
        if ("flash:power_blast".equals(a.type)) {
            LivingEntity locked = lock(level, p, eye, aim, max);
            if (locked != null) {
                aim = locked.position().add(0.0D, locked.getBbHeight() * 0.5D, 0.0D).subtract(eye).normalize();
                len = max;
            }
        }
        Vec3 end = eye.add(aim.scale(len));
        BlockHitResult block = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
                p));
        Vec3 stop = block.getType() == HitResult.Type.MISS ? end : block.getLocation();
        EntityHitResult eh = ProjectileUtil.getEntityHitResult(p, eye, stop, p.getBoundingBox().expandTowards(
                stop.subtract(eye)).inflate(1.0D), e -> e instanceof LivingEntity && e.isAlive() && !e.isSpectator()
                && !(e instanceof net.minecraft.world.entity.player.Player pl && pl.isCreative()),
                eye.distanceToSqr(stop));
        int fire = j.has("set_on_fire_seconds") ? j.get("set_on_fire_seconds").getAsInt() : 0;
        if (eh != null) {
            Entity e = eh.getEntity();
            if (e.invulnerableTime <= 10) {
                e.hurt(level.damageSources().indirectMagic(p, p), damage);
                if (fire > 0) {
                    e.setSecondsOnFire(fire);
                }
            }
            return;
        }
        if (block.getType() != HitResult.Type.BLOCK) {
            return;
        }
        BlockPos pos = block.getBlockPos();
        if (InfPowers.bool(j, "smelt_blocks", false) && p.tickCount % 5 == 0) {
            smelt(level, pos);
        }
        if (InfPowers.bool(j, "cause_fire", false) && p.tickCount % 4 == 0) {
            BlockPos f = pos.relative(block.getDirection());
            if (level.isEmptyBlock(f) && level.getGameRules().getBoolean(
                    net.minecraft.world.level.GameRules.RULE_DOFIRETICK)) {
                BlockState fs = BaseFireBlock.getState(level, f);
                if (fs.canSurvive(level, f)) {
                    level.setBlock(f, fs, Block.UPDATE_ALL);
                }
            }
        }
        if (maxed && j.has("max_explosion_power")) {
            float power = j.get("max_explosion_power").getAsFloat();
            if (power > 0.0F && p.tickCount % 2 == 0) {
                Vec3 at = block.getLocation();
                level.explode(p, at.x, at.y, at.z, power, Level.ExplosionInteraction.TNT);
            }
        }
        if (maxed && j.has("max_commands_on_block_hit")) {
            InfinityAbilities.commandsAt(p, j.get("max_commands_on_block_hit"), block.getLocation());
        } else if (j.has("commands_on_block_hit") && p.tickCount % 4 == 0) {
            InfinityAbilities.commandsAt(p, j.get("commands_on_block_hit"), block.getLocation());
        }
    }

    private static void smelt(ServerLevel level, BlockPos pos) {
        BlockState st = level.getBlockState(pos);
        if (st.isAir() || st.getDestroySpeed(level, pos) < 0.0F) {
            return;
        }
        ItemStack in = new ItemStack(st.getBlock());
        if (in.isEmpty()) {
            return;
        }
        var r = level.getRecipeManager().getRecipeFor(RecipeType.SMELTING,
                new net.minecraft.world.SimpleContainer(in), level);
        if (r.isEmpty()) {
            return;
        }
        ItemStack out = r.get().getResultItem(level.registryAccess());
        if (out.getItem() instanceof net.minecraft.world.item.BlockItem bi) {
            level.setBlock(pos, bi.getBlock().defaultBlockState(), Block.UPDATE_ALL);
        }
    }
}
