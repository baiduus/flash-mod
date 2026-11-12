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
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = com.flash.FlashMod.MODID)
public class MjolnirEntity extends Projectile {

    private static final EntityDataAccessor<Boolean> RETURNING = SynchedEntityData.defineId(MjolnirEntity.class,
            EntityDataSerializers.BOOLEAN);
    /** Lying on the ground (dropped): only the worthy pick it up, with a right click. */
    private static final EntityDataAccessor<Boolean> STATUE = SynchedEntityData.defineId(MjolnirEntity.class,
            EntityDataSerializers.BOOLEAN);
    /** Which weapon it is (ThorSuits.CLASSIC / MCU / COSMIC / STORMBREAKER), for the renderer. */
    private static final EntityDataAccessor<Integer> WEAPON = SynchedEntityData.defineId(MjolnirEntity.class,
            EntityDataSerializers.INT);
    private static final Map<UUID, MjolnirEntity> OUT = new ConcurrentHashMap<>();

    private ItemStack stack = ItemStack.EMPTY;
    private float force = 1.0F;
    private int flying;
    private int resting;
    private final Set<Integer> hit = new HashSet<>();
    /** Weapon of a King: the creature it chases (-1 = none). */
    private int homing = -1;

    public MjolnirEntity(EntityType<? extends MjolnirEntity> type, Level level) {
        super(type, level);
        this.noPhysics = false;
    }

    public MjolnirEntity(Level level, ServerPlayer owner, ItemStack stack, float force) {
        this(ModEntities.MJOLNIR.get(), level);
        this.setOwner(owner);
        this.setStack(stack);
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
        this.entityData.define(STATUE, false);
        this.entityData.define(WEAPON, 0);
    }

    private void setStack(ItemStack stack) {
        this.stack = stack.copy();
        this.entityData.set(WEAPON, com.flash.item.MjolnirItem.weapon(stack));
    }

    public int weapon() {
        return this.entityData.get(WEAPON);
    }

    /** Chases a creature (Weapon of a King): it turns toward it on the way. */
    public void home(Entity target) {
        this.homing = target.getId();
    }

    public boolean returning() {
        return this.entityData.get(RETURNING);
    }

    public boolean statue() {
        return this.entityData.get(STATUE);
    }

    /** A dropped Mjolnir: it stays where it falls. */
    public static MjolnirEntity statue(Level level, ItemStack stack, Vec3 at) {
        MjolnirEntity e = new MjolnirEntity(ModEntities.MJOLNIR.get(), level);
        e.setStack(stack);
        e.entityData.set(STATUE, true);
        e.setPos(at.x, at.y, at.z);
        return e;
    }

    /** Called back by its worthy owner: it lifts off and flies to the hand. */
    public void callTo(ServerPlayer owner) {
        this.entityData.set(STATUE, false);
        this.setOwner(owner);
        OUT.put(owner.getUUID(), this);
        this.recall();
    }

    public void recall() {
        this.entityData.set(RETURNING, true);
        this.noPhysics = true;
        this.hit.clear();
    }

    @Override
    public void tick() {
        if (!this.level().isClientSide && this.tickCount % 20 == 0
                && com.flash.item.MjolnirItem.stale(this.stack, this.level().getServer())) {
            this.discard();
            return;
        }
        if (this.statue()) {
            // lying on the ground: falls, then stays (only a worthy right click lifts it)
            this.baseTick();
            Vec3 v = this.getDeltaMovement();
            if (!this.onGround()) {
                v = v.add(0.0D, -0.08D, 0.0D);
            }
            this.move(net.minecraft.world.entity.MoverType.SELF, new Vec3(0.0D, v.y, 0.0D));
            this.setDeltaMovement(this.onGround() ? Vec3.ZERO : new Vec3(0.0D, v.y * 0.98D, 0.0D));
            return;
        }
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
        if (this.tickCount % 8 == 0 && this.resting == 0) {
            // the whoosh: only while it actually flies
            level.playSound(null, this.getX(), this.getY(), this.getZ(), com.flash.registry.ModSounds.THOR_SPIN.get(),
                    SoundSource.PLAYERS, 0.7F, 1.2F);
        }
        if (!this.returning()) {
            this.flying++;
            if (this.resting > 0) {
                this.setDeltaMovement(Vec3.ZERO);
                if (++this.resting > 10) {
                    this.recall();
                }
                return;
            }
            if (this.homing >= 0) {
                Entity t = level.getEntity(this.homing);
                if (t != null && t.isAlive() && !this.hit.contains(this.homing)) {
                    Vec3 to = t.position().add(0.0D, t.getBbHeight() * 0.5D, 0.0D).subtract(this.position());
                    double sp = this.getDeltaMovement().length();
                    this.setDeltaMovement(this.getDeltaMovement().normalize().scale(0.6D)
                            .add(to.normalize().scale(0.4D)).normalize().scale(sp));
                    this.flying = Math.min(this.flying, 20);
                }
                if (this.tickCount % 2 == 0) {
                    ThorAbilities.bolt(level, this.position(), this.position().subtract(this.getDeltaMovement()), 0.9F);
                }
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
            this.level().addFreshEntity(statue(this.level(), this.stack, this.position()));
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
    public boolean isPickable() {
        return this.statue();
    }

    @Override
    public float getPickRadius() {
        return 0.2F;
    }

    /** Right click on the hammer on the ground: the worthy take it to the empty hand, the others cannot lift it. */
    @Override
    public net.minecraft.world.InteractionResult interact(net.minecraft.world.entity.player.Player player,
                                                          net.minecraft.world.InteractionHand hand) {
        if (!this.statue()) {
            return net.minecraft.world.InteractionResult.PASS;
        }
        if (this.level().isClientSide) {
            return net.minecraft.world.InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer sp && ThorPowers.isThor(sp)) {
            ThorAbilities.catchHammer(sp, this.stack);
            this.discard();
        } else {
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.flash.thor_unworthy"),
                    true);
            this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                    com.flash.registry.ModSounds.THOR_BLOCK.get(), SoundSource.PLAYERS, 0.6F, 0.8F);
        }
        return net.minecraft.world.InteractionResult.CONSUME;
    }

    @Override
    public boolean hurt(net.minecraft.world.damagesource.DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean fireImmune() {
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
        tag.putBoolean("statue", this.statue());
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.setStack(ItemStack.of(tag.getCompound("stack")));
        this.force = tag.getFloat("force");
        if (tag.getBoolean("statue")) {
            this.entityData.set(STATUE, true);
        } else {
            this.recall();
        }
    }

    /** Every Mjolnir item thrown on the ground (Q, death, a full inventory...) turns into the hammer lying there. */
    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void onJoin(net.minecraftforge.event.entity.EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof net.minecraft.world.entity.item.ItemEntity item)
                || !(item.getItem().getItem() instanceof com.flash.item.MjolnirItem)) {
            return;
        }
        event.setCanceled(true);
        event.getLevel().addFreshEntity(statue(event.getLevel(), item.getItem(), item.position()));
    }

    /** The hammers lying around a player (loaded chunks), the nearest first. */
    public static MjolnirEntity nearestStatue(ServerPlayer player, double range) {
        // his own hammer lying anywhere loaded in this world, however far
        UUID me = player.getUUID();
        for (Entity e : player.serverLevel().getAllEntities()) {
            if (e instanceof MjolnirEntity m && m.isAlive() && m.statue()
                    && me.equals(com.flash.item.MjolnirItem.owner(m.stack))
                    && !com.flash.item.MjolnirItem.stale(m.stack, player.getServer())) {
                return m;
            }
        }
        MjolnirEntity best = null;
        double bd = Double.MAX_VALUE;
        for (MjolnirEntity e : player.serverLevel().getEntitiesOfClass(MjolnirEntity.class,
                player.getBoundingBox().inflate(range), MjolnirEntity::statue)) {
            double d = e.distanceToSqr(player);
            if (d < bd) {
                bd = d;
                best = e;
            }
        }
        return best;
    }

    public void playThump(ServerLevel level) {
        level.playSound(null, this.getX(), this.getY(), this.getZ(), com.flash.registry.ModSounds.THOR_THUMP.get(),
                SoundSource.PLAYERS, 1.0F, 1.0F);
    }
}
