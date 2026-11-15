package com.flash.infinity.entity;

import com.flash.infinity.InfinityEntities;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;
import org.joml.Vector3f;

/**
 * The Space and Power Stones' black hole (the original's BlackHoleEntity): it grows in front of its maker and follows
 * their aim; thrown, it flies on, and later collapses. It pulls in everything within 20 blocks, swallows (kills)
 * what reaches it and tears up the blocks round it.
 */
public class BlackHole extends Entity {

    private static final EntityDataAccessor<Integer> SIZE = SynchedEntityData.defineId(BlackHole.class,
            EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> COLLAPSING = SynchedEntityData.defineId(BlackHole.class,
            EntityDataSerializers.BOOLEAN);
    public static final int MAX_SIZE = 120;
    private static final double PULL = 20.0D;
    private static final ResourceKey<DamageType> DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE,
            new ResourceLocation("flash", "black_hole"));

    private UUID creator;
    private boolean thrown;
    private int flying;
    private int prevSize;

    public BlackHole(EntityType<? extends BlackHole> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    public static BlackHole create(ServerLevel level, Player maker, Vec3 at) {
        BlackHole b = new BlackHole(InfinityEntities.BLACK_HOLE.get(), level);
        b.moveTo(at.x, at.y, at.z);
        b.creator = maker.getUUID();
        return b;
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(SIZE, 1);
        this.entityData.define(COLLAPSING, false);
    }

    public int size() {
        return this.entityData.get(SIZE);
    }

    public float size(float pt) {
        return this.prevSize + (size() - this.prevSize) * pt;
    }

    /** Its radius in blocks (what touches it is swallowed). */
    public double radius() {
        return size() / 40.0D;
    }

    public boolean thrown() {
        return this.thrown;
    }

    public UUID creator() {
        return this.creator;
    }

    public void collapse() {
        this.entityData.set(COLLAPSING, true);
    }

    public void launch(Vec3 dir) {
        this.thrown = true;
        this.setDeltaMovement(dir.normalize().scale(0.8D));
        this.level().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.WARDEN_SONIC_BOOM,
                SoundSource.PLAYERS, 1.2F, 0.4F);
    }

    @Override
    public void tick() {
        super.tick();
        this.prevSize = size();
        if (!(this.level() instanceof ServerLevel level)) {
            this.setPos(this.position().add(this.getDeltaMovement()));
            return;
        }
        Player maker = this.creator == null ? null : level.getPlayerByUUID(this.creator);
        if (this.entityData.get(COLLAPSING)) {
            int s = size() - 4;
            if (s <= 0) {
                level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.GENERIC_EXPLODE,
                        SoundSource.PLAYERS, 1.5F, 0.5F);
                this.discard();
                return;
            }
            this.entityData.set(SIZE, s);
        } else if (size() < MAX_SIZE) {
            this.entityData.set(SIZE, size() + 1);
        }
        if (this.thrown) {
            this.setPos(this.position().add(this.getDeltaMovement()));
            if (++this.flying > 200) {
                collapse();
            }
        } else if (maker != null && maker.isAlive()) {
            // in front of its maker, where they look
            Vec3 want = maker.getEyePosition().add(maker.getLookAngle().scale(4.0D + radius() * 2.0D));
            this.setPos(this.position().lerp(want, 0.35D));
        } else {
            collapse();
        }
        if (this.tickCount % 20 == 0) {
            level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.BEACON_AMBIENT,
                    SoundSource.PLAYERS, 1.5F, 0.3F);
        }
        attract(level, maker);
        if (this.tickCount % 2 == 0) {
            destroyBlocks(level);
        }
        suction(level);
    }

    private void attract(ServerLevel level, Player maker) {
        Vec3 c = this.position();
        double r = radius();
        for (Entity e : level.getEntities(this, new AABB(c, c).inflate(PULL), e -> e != maker
                && !(e instanceof BlackHole) && !(e instanceof SpacePortal) && !e.isSpectator()
                && !(e instanceof Player pl && pl.isCreative()) && !com.flash.infinity.InfinityImmunity.immune(e,
                "black_hole"))) {
            Vec3 to = c.subtract(e.position().add(0.0D, e.getBbHeight() * 0.5D, 0.0D));
            double d = to.length();
            if (d > PULL) {
                continue;
            }
            if (d < r + e.getBbWidth() * 0.5D) {
                if (e instanceof LivingEntity l) {
                    l.hurt(new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                            .getHolderOrThrow(DAMAGE), this, maker), 2.1474836E9F);
                } else {
                    e.discard();
                }
                continue;
            }
            double pull = 0.5D * (1.0D - d / PULL) * Math.max(0.3D, size() / (double) MAX_SIZE);
            e.setDeltaMovement(e.getDeltaMovement().scale(0.9D).add(to.normalize().scale(pull)));
            e.hurtMarked = true;
            e.fallDistance = 0.0F;
        }
    }

    private void destroyBlocks(ServerLevel level) {
        if (!level.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_MOBGRIEFING)) {
            return;
        }
        double r = radius() + 2.0D;
        BlockPos c = this.blockPosition();
        int ir = (int) Math.ceil(r);
        for (BlockPos p : BlockPos.betweenClosed(c.offset(-ir, -ir, -ir), c.offset(ir, ir, ir))) {
            if (p.distSqr(c) > r * r) {
                continue;
            }
            BlockState st = level.getBlockState(p);
            if (st.isAir() || st.getDestroySpeed(level, p) < 0.0F || st.is(Blocks.BEDROCK)) {
                continue;
            }
            level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
        }
    }

    private void suction(ServerLevel level) {
        for (int i = 0; i < 3; i++) {
            double a = this.random.nextDouble() * Math.PI * 2.0D;
            double b = this.random.nextDouble() * Math.PI - Math.PI / 2.0D;
            double d = PULL * (0.3D + this.random.nextDouble() * 0.7D);
            Vec3 at = this.position().add(Math.cos(a) * Math.cos(b) * d, Math.sin(b) * d, Math.sin(a) * Math.cos(b)
                    * d);
            Vector3f col = this.random.nextBoolean() ? new Vector3f(176 / 255.0F, 134 / 255.0F, 1.0F)
                    : new Vector3f(96 / 255.0F, 198 / 255.0F, 57 / 255.0F);
            Vec3 v = this.position().subtract(at).scale(0.1D);
            level.sendParticles(new DustParticleOptions(col, 2.0F), at.x, at.y, at.z, 0, v.x, v.y, v.z, 1.0D);
        }
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        this.entityData.set(SIZE, Math.max(1, tag.getInt("Size")));
        this.entityData.set(COLLAPSING, tag.getBoolean("Collapsing"));
        if (tag.hasUUID("Creator")) {
            this.creator = tag.getUUID("Creator");
        }
        this.thrown = true;
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("Size", size());
        tag.putBoolean("Collapsing", this.entityData.get(COLLAPSING));
        if (this.creator != null) {
            tag.putUUID("Creator", this.creator);
        }
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double d) {
        return d < 256.0D * 256.0D;
    }
}
