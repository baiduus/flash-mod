package com.flash.entity;

import com.flash.hulk.GammaBurst;
import com.flash.registry.ModEntities;
import com.flash.registry.ModItems;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;

/** Thrown gamma bomb: explodes on impact, everyone close becomes the Hulk. */
public class GammaBomb extends ThrowableItemProjectile {

    public GammaBomb(EntityType<? extends GammaBomb> type, Level level) {
        super(type, level);
    }

    public GammaBomb(Level level, LivingEntity thrower) {
        super(ModEntities.GAMMA_BOMB.get(), thrower, level);
    }

    @Override
    protected Item getDefaultItem() {
        return ModItems.GAMMA_BOMB.get();
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (this.level() instanceof ServerLevel level) {
            GammaBurst.explode(level, this.position(), 6.0D);
            this.discard();
        }
    }
}
