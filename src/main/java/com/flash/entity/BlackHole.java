package com.flash.entity;

import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Yas' black hole (the Manipulation of the Celestial, Z): a sphere of darkness in a burning disk of matter, drawn
 * with Hand of God's textures (BlackHoleRenderer). For {@link #LIFE} ticks it pulls everything around (creatures,
 * players, items, loose blocks it tears from the ground), crushes what reaches its heart, and then collapses in a
 * flash that throws everything back. The one who made it is not pulled; pressing Z again collapses it early.
 */
public class BlackHole extends Entity {

    public static final int GROW = 30;
    public static final int LIFE = 15 * 20;
    public static final int COLLAPSE = 24;
    public static final float RADIUS = 1.6F;
    private static final double PULL = 32.0D;
    private static final EntityDataAccessor<Integer> AGE = SynchedEntityData.defineId(BlackHole.class,
            EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> END = SynchedEntityData.defineId(BlackHole.class,
            EntityDataSerializers.INT);
    private static final EntityDataAccessor<Optional<UUID>> OWNER = SynchedEntityData.defineId(BlackHole.class,
            EntityDataSerializers.OPTIONAL_UUID);

    public BlackHole(EntityType<? extends BlackHole> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(AGE, 0);
        this.entityData.define(END, LIFE);
        this.entityData.define(OWNER, Optional.empty());
    }

    public void setOwner(UUID owner) {
        this.entityData.set(OWNER, Optional.ofNullable(owner));
    }

    public UUID owner() {
        return this.entityData.get(OWNER).orElse(null);
    }

    public int age() {
        return this.entityData.get(AGE);
    }

    /** Collapses now (Z again). */
    public void collapse() {
        int end = age() + COLLAPSE;
        if (end < this.entityData.get(END)) {
            this.entityData.set(END, end);
        }
    }

    public boolean collapsing() {
        return age() >= this.entityData.get(END) - COLLAPSE;
    }

    /** 0..1 of the size now. */
    public float size(float partial) {
        float a = age() + partial;
        int end = this.entityData.get(END);
        float grow = Mth.clamp(a / GROW, 0.0F, 1.0F);
        float shrink = Mth.clamp((end - a) / COLLAPSE, 0.0F, 1.0F);
        return grow * grow * (3.0F - 2.0F * grow) * Math.min(1.0F, shrink * 1.2F);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            return;
        }
        int age = age() + 1;
        this.entityData.set(AGE, age);
        ServerLevel level = (ServerLevel) this.level();
        int end = this.entityData.get(END);
        if (age >= end) {
            burst(level);
            this.discard();
            return;
        }
        float size = size(0.0F);
        Vec3 c = this.position();
        double pull = PULL * Math.max(0.25F, size);
        if (age % 40 == 1) {
            play(level, "lua_zumbido", SoundEvents.BEACON_AMBIENT, 2.5F, 0.5F);
        }
        // the spiral of matter falling in
        for (int i = 0; i < 4; i++) {
            double a = this.random.nextDouble() * Math.PI * 2.0D;
            double r = 4.0D + this.random.nextDouble() * 6.0D;
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, c.x + Math.cos(a) * r, c.y + (this.random.nextDouble()
                    - 0.5D) * 1.5D, c.z + Math.sin(a) * r, 0, -Math.cos(a) * 0.8D, 0.0D, -Math.sin(a) * 0.8D, 1.0D);
        }
        UUID owner = owner();
        AABB box = new AABB(c, c).inflate(pull);
        for (Entity e : level.getEntities(this, box, e -> e != this && !(e instanceof BlackHole))) {
            if (owner != null && owner.equals(e.getUUID())) {
                continue;
            }
            if (e instanceof Player p && (p.isCreative() || p.isSpectator())) {
                continue;
            }
            Vec3 to = c.subtract(e.position().add(0.0D, e.getBbHeight() / 2.0D, 0.0D));
            double d = to.length();
            if (d > pull || d < 1.0E-3D) {
                continue;
            }
            double f = (0.06D + 0.9D * Math.pow(1.0D - d / pull, 2.0D)) * size;
            Vec3 dir = to.scale(1.0D / d);
            // a little swirl around
            Vec3 swirl = new Vec3(-dir.z, 0.0D, dir.x).scale(f * 0.35D);
            e.setDeltaMovement(e.getDeltaMovement().scale(0.9D).add(dir.scale(f)).add(swirl));
            e.hurtMarked = true;
            e.fallDistance = 0.0F;
            if (d < RADIUS * size + 0.8D) {
                if (e instanceof LivingEntity living) {
                    living.invulnerableTime = 0;
                    living.hurt(level.damageSources().fellOutOfWorld(), 8.0F);
                } else if (e instanceof ItemEntity || e instanceof ExperienceOrb || e instanceof FallingBlockEntity) {
                    e.discard();
                }
            }
        }
        // the ground torn away
        if (size > 0.6F && !collapsing()) {
            for (int i = 0; i < 3; i++) {
                BlockPos pos = BlockPos.containing(c.x + (this.random.nextDouble() - 0.5D) * 20.0D,
                        c.y + (this.random.nextDouble() - 0.5D) * 20.0D, c.z + (this.random.nextDouble() - 0.5D) * 20.0D);
                BlockState state = level.getBlockState(pos);
                float hard = state.getDestroySpeed(level, pos);
                if (state.isAir() || hard < 0.0F || hard > 50.0F || level.getBlockEntity(pos) != null
                        || !state.getFluidState().isEmpty()) {
                    continue;
                }
                FallingBlockEntity fall = FallingBlockEntity.fall(level, pos, state);
                fall.dropItem = false;
                fall.setNoGravity(true);
                fall.setDeltaMovement(c.subtract(Vec3.atCenterOf(pos)).normalize().scale(0.4D));
            }
        }
    }

    private void burst(ServerLevel level) {
        Vec3 c = this.position();
        play(level, "lua_impacto_estalo", SoundEvents.GENERIC_EXPLODE, 3.0F, 0.6F);
        level.sendParticles(ParticleTypes.FLASH, c.x, c.y, c.z, 2, 0.0D, 0.0D, 0.0D, 0.0D);
        level.sendParticles(ParticleTypes.SONIC_BOOM, c.x, c.y, c.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        level.sendParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, 120, 0.3D, 0.3D, 0.3D, 0.8D);
        UUID owner = owner();
        for (Entity e : level.getEntities(this, new AABB(c, c).inflate(16.0D), e -> !(e instanceof BlackHole))) {
            if (owner != null && owner.equals(e.getUUID())) {
                continue;
            }
            Vec3 away = e.position().subtract(c);
            double d = Math.max(1.0D, away.length());
            e.setDeltaMovement(e.getDeltaMovement().add(away.normalize().scale(3.0D / Math.sqrt(d)))
                    .add(0.0D, 0.5D, 0.0D));
            e.hurtMarked = true;
            if (e instanceof FallingBlockEntity fall) {
                fall.setNoGravity(false);
            }
        }
    }

    private void play(ServerLevel level, String hog, SoundEvent fallback, float volume, float pitch) {
        SoundEvent s = ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("hand_of_god", hog));
        level.playSound(null, this.getX(), this.getY(), this.getZ(), s != null ? s : fallback, SoundSource.PLAYERS,
                volume, s != null ? 1.0F : pitch);
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 256.0D * 256.0D;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        this.entityData.set(AGE, tag.getInt("Age"));
        if (tag.hasUUID("Owner")) {
            setOwner(tag.getUUID("Owner"));
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("Age", age());
        if (owner() != null) {
            tag.putUUID("Owner", owner());
        }
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}
