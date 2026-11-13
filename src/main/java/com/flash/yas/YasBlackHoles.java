package com.flash.yas;

import com.flash.entity.BlackHole;
import com.flash.registry.ModEntities;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The black hole of the Manipulation of the Celestial (Z in that mode): opened where Yas looks (up to 48 blocks,
 * a few blocks off the ground she looks at); one at a time, Z again collapses it.
 */
public final class YasBlackHoles {

    private YasBlackHoles() {
    }

    public static void cast(ServerPlayer p) {
        if (!YasPowers.has(p)) {
            return;
        }
        ServerLevel level = p.serverLevel();
        for (BlackHole open : level.getEntitiesOfClass(BlackHole.class, p.getBoundingBox().inflate(256.0D),
                b -> p.getUUID().equals(b.owner()))) {
            if (!open.collapsing()) {
                open.collapse();
                return;
            }
        }
        Vec3 eye = p.getEyePosition();
        Vec3 look = p.getLookAngle();
        BlockHitResult hit = level.clip(new ClipContext(eye, eye.add(look.scale(48.0D)), ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, p));
        Vec3 at = hit.getType() == HitResult.Type.MISS ? eye.add(look.scale(24.0D))
                : hit.getLocation().subtract(look.scale(6.0D)).add(0.0D, 3.0D, 0.0D);
        if (at.distanceTo(eye) < 8.0D) {
            at = eye.add(look.scale(8.0D));
        }
        BlackHole hole = ModEntities.BLACK_HOLE.get().create(level);
        if (hole == null) {
            return;
        }
        hole.moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
        hole.setOwner(p.getUUID());
        level.addFreshEntity(hole);
        YasReality.play(level, at, "lua_impacto_sugar", net.minecraft.sounds.SoundEvents.WARDEN_SONIC_CHARGE, 0.5F);
    }
}
