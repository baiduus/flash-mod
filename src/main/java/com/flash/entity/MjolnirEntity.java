package com.flash.entity;

import com.flash.registry.ModEntities;
import com.flash.thor.ThorAbilities;
import com.flash.thor.ThorPowers;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The thrown Mjolnir: flies straight, goes through the creatures it hits (damage with the throw force and the
 * charge), stops on a wall with a shockwave and flies back to the owner's hand on its own (or when recalled),
 * going through blocks and hitting whatever is in the way.
 */
public class MjolnirEntity extends Projectile {

    private static final EntityDataAccessor<Boolean> RETURNING = SynchedEntityData.defineId(MjolnirEntity.class,
            EntityDataSerializers.BOOLEAN);
    private static final Map<UUID, MjolnirEntity> OUT = new ConcurrentHashMap<>();

    private ItemStack stack = ItemStack.EMPTY;
    private float force = 1.0F;
    private int flying;
    private int resting;
    private final Set<Integer> hit = new HashSet<>();

    public MjolnirEntity(EntityType<? extends MjolnirEntity> type, Level level) {
        super(type, level);
        this.noPhysics = false;
    }

    public MjolnirEntity(Level level, ServerPlayer owner, ItemStack stack, float force) {
        this(ModEntities.MJOLNIR.get(), level);
        this.setOwner(owner);
        this.stack = stack.copy();
        this.force = force;
        Vec3 eye = owner.getEyePosition();
        this.setPos(eye.x, eye.y - 0.2D, eye.z);
        OUT.put(owner.getUUID(), this);
    }

    /** The hammer of a player out in the world, if any. */
    public static MjolnirEntity find(ServerPlayer owner) {
        MjolnirEntity e = OUT.get(owner.getUUID());
        if (e != null && (e.isRemoved() || e.level() != owner.level())) {
            OUT.remove(owner.getUUID());
            return null;
        }
        return e;
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(RETURNING, false);
    }

    public boolean returning() {
        return this.entityData.get(RETURNING);
    }

    public void recall() {
        this.entityData.set(RETURNING, true);
        this.noPhysics = true;
        this.hit.clear();
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            Vec3 v = this.getDeltaMovement();
            this.setPos(this.getX() + v.x, this.getY() + v.y, this.getZ() + v.z);
            return;
        }
        ServerLevel level = (ServerLevel) this.level();
        Entity o = this.getOwner();
        if (!(o instanceof ServerPlayer owner) || !owner.isAlive() || owner.level() != level
                || !ThorPowers.has(owner)) {
            if (this.tickCount > 40) {
                this.drop();
            }
            return;
        }
        OUT.put(owner.getUUID(), this);
        if (!this.returning()) {
            this.flying++;
            if (this.resting > 0) {
                this.setDeltaMovement(Vec3.ZERO);
                if (++this.resting > 10) {
                    this.recall();
                }
                return;
            }
            Vec3 pos = this.position();
            Vec3 next = pos.add(this.getDeltaMovement());
            BlockHitResult block = level.clip(new ClipContext(pos, next, ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE, this));
            if (block.getType() != HitResult.Type.MISS) {
                next = block.getLocation();
            }
            this.hitAlong(owner, pos, next, false);
            this.setPos(next.x, next.y, next.z);
            if (block.getType() != HitResult.Type.MISS) {
                ThorAbilities.hammerImpact(owner, block.getLocation(), this.force);
                this.resting = 1;
                return;
            }
            if (this.flying > 30 || pos.distanceTo(owner.position()) > 56.0D) {
                this.recall();
            }
            return;
        }
        // back to the hand, through anything
        Vec3 target = owner.getEyePosition().subtract(0.0D, 0.4D, 0.0D);
        Vec3 pos = this.position();
        Vec3 d = target.subtract(pos);
        double dist = d.length();
        if (dist < 1.6D) {
            ThorAbilities.catchHammer(owner, this.stack);
            OUT.remove(owner.getUUID());
            this.discard();
            return;
        }
        double speed = Math.min(dist, 1.6D + 0.05D * this.tickCount);
        Vec3 next = pos.add(d.scale(speed / dist));
        this.hitAlong(owner, pos, next, true);
        this.setDeltaMovement(next.subtract(pos));
        this.setPos(next.x, next.y, next.z);
    }

    private void hitAlong(ServerPlayer owner, Vec3 from, Vec3 to, boolean back) {
        AABB box = new AABB(from, to).inflate(0.8D);
        for (Entity e : this.level().getEntities(this, box, x -> x instanceof LivingEntity && x != owner && x.isAlive())) {
            if (this.hit.add(e.getId())) {
                ThorAbilities.hammerStrike(owner, (LivingEntity) e, this.force * (back ? 0.6F : 1.0F),
                        this.getDeltaMovement());
            }
        }
    }

    private void drop() {
        if (!this.stack.isEmpty()) {
            this.level().addFreshEntity(new ItemEntity(this.level(), this.getX(), this.getY(), this.getZ(),
                    this.stack.copy()));
        }
        Entity o = this.getOwner();
        if (o != null) {
            OUT.remove(o.getUUID());
        }
        this.discard();
    }

    @Override
    public boolean isNoGravity() {
        return true;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double d) {
        return d < 128.0D * 128.0D;
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.put("stack", this.stack.save(new CompoundTag()));
        tag.putFloat("force", this.force);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.stack = ItemStack.of(tag.getCompound("stack"));
        this.force = tag.getFloat("force");
        this.recall();
    }

    public void playThump(ServerLevel level) {
        level.playSound(null, this.getX(), this.getY(), this.getZ(), com.flash.registry.ModSounds.THOR_THUMP.get(),
                SoundSource.PLAYERS, 1.0F, 1.0F);
    }
}
