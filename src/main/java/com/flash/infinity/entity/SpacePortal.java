package com.flash.infinity.entity;

import com.flash.infinity.InfinityEntities;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;
import org.joml.Vector3f;

/**
 * One end of a Space Stone portal (the original's PortalManager): it opens (3 s), stands open (temporary ones close
 * after their time), and closes again when hit. Whatever walks into its face comes out of its partner's face, in
 * its own dimension, a second's cooldown before the next crossing.
 */
public class SpacePortal extends Entity {

    private static final EntityDataAccessor<Integer> AGE = SynchedEntityData.defineId(SpacePortal.class,
            EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> CLOSING = SynchedEntityData.defineId(SpacePortal.class,
            EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> COLOR = SynchedEntityData.defineId(SpacePortal.class,
            EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> FIRE = SynchedEntityData.defineId(SpacePortal.class,
            EntityDataSerializers.BOOLEAN);
    public static final int OPEN_TICKS = 60;
    public static final int CLOSE_TICKS = 60;

    private UUID partner;
    private ResourceKey<Level> partnerDim;
    private long expiresAt = -1L;

    public SpacePortal(EntityType<? extends SpacePortal> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    public static SpacePortal create(ServerLevel level, Vec3 at, float yaw, int color, boolean fire) {
        SpacePortal p = new SpacePortal(InfinityEntities.SPACE_PORTAL.get(), level);
        p.moveTo(at.x, at.y, at.z, yaw, 0.0F);
        p.entityData.set(COLOR, color);
        p.entityData.set(FIRE, fire);
        return p;
    }

    public void link(SpacePortal other) {
        this.partner = other.getUUID();
        this.partnerDim = other.level().dimension();
    }

    public void expireAt(long gameTime) {
        this.expiresAt = gameTime;
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(AGE, 0);
        this.entityData.define(CLOSING, -1);
        this.entityData.define(COLOR, 0x5BA9FF);
        this.entityData.define(FIRE, false);
    }

    public int color() {
        return this.entityData.get(COLOR);
    }

    public boolean fire() {
        return this.entityData.get(FIRE);
    }

    /** 0 closed .. 1 open (opening and closing). */
    public float openness(float pt) {
        int closing = this.entityData.get(CLOSING);
        float age = this.entityData.get(AGE) + pt;
        float open = Math.min(1.0F, age / OPEN_TICKS);
        if (closing >= 0) {
            open = Math.min(open, Math.max(0.0F, 1.0F - (age - closing) / CLOSE_TICKS));
        }
        return open;
    }

    public void close() {
        if (this.entityData.get(CLOSING) < 0) {
            this.entityData.set(CLOSING, this.entityData.get(AGE));
            this.playSound(com.flash.infinity.InfinitySounds.get("space_stone_portal"), 1.0F, 0.6F);
        }
    }

    /** The way its face looks (out of the portal). */
    public Vec3 normal() {
        float yaw = this.getYRot() * ((float) Math.PI / 180.0F);
        return new Vec3(-Math.sin(yaw), 0.0D, Math.cos(yaw));
    }

    @Override
    public void tick() {
        super.tick();
        int age = this.entityData.get(AGE) + 1;
        this.entityData.set(AGE, age);
        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }
        int closing = this.entityData.get(CLOSING);
        if (closing >= 0 && age - closing > CLOSE_TICKS) {
            this.discard();
            return;
        }
        if (this.expiresAt >= 0 && level.getGameTime() >= this.expiresAt) {
            close();
        }
        SpacePortal other = partner(level);
        if (other == null || other.isRemoved()) {
            if (age > 40) {
                close();
            }
            return;
        }
        if (openness(0.0F) < 0.6F) {
            return;
        }
        if (age % 4 == 0) {
            int c = color();
            level.sendParticles(new DustParticleOptions(new Vector3f(((c >> 16) & 255) / 255.0F,
                    ((c >> 8) & 255) / 255.0F, (c & 255) / 255.0F), 1.2F), this.getX(), this.getY() + 1.25D,
                    this.getZ(), 4, 0.5D, 0.8D, 0.5D, 0.0D);
        }
        // through: what touches its face crosses
        Vec3 n = normal();
        Vec3 c = this.position().add(0.0D, 1.25D, 0.0D);
        for (Entity e : level.getEntities(this, new AABB(c, c).inflate(1.0D, 1.3D, 1.0D),
                e -> !(e instanceof SpacePortal) && !e.isPassenger() && !e.isSpectator())) {
            Vec3 rel = e.position().add(0.0D, e.getBbHeight() * 0.5D, 0.0D).subtract(c);
            double depth = rel.dot(n);
            Vec3 side = rel.subtract(n.scale(depth));
            if (Math.abs(depth) > 0.6D || side.horizontalDistance() > 0.85D || Math.abs(rel.y) > 1.25D) {
                continue;
            }
            CompoundTag d = e.getPersistentData();
            if (d.getLong("flash_portal_until") > level.getGameTime()) {
                continue;
            }
            d.putLong("flash_portal_until", level.getGameTime() + 20L);
            through(e, other);
        }
    }

    private SpacePortal partner(ServerLevel level) {
        if (this.partner == null) {
            return null;
        }
        ServerLevel there = this.partnerDim == null ? level : level.getServer().getLevel(this.partnerDim);
        if (there == null) {
            return null;
        }
        Entity e = there.getEntity(this.partner);
        return e instanceof SpacePortal sp ? sp : null;
    }

    private static void through(Entity e, SpacePortal to) {
        Vec3 out = to.position().add(to.normal().scale(1.5D));
        ServerLevel there = (ServerLevel) to.level();
        float yaw = to.getYRot();
        there.playSound(null, out.x, out.y, out.z, com.flash.infinity.InfinitySounds.get("space_stone_portal"),
                SoundSource.PLAYERS, 0.8F, 1.0F);
        if (e instanceof net.minecraft.server.level.ServerPlayer sp) {
            sp.teleportTo(there, out.x, out.y, out.z, yaw, sp.getXRot());
        } else if (e.level() == there) {
            e.teleportTo(out.x, out.y, out.z);
            e.setYRot(yaw);
        } else {
            e.changeDimension(there, new net.minecraftforge.common.util.ITeleporter() {
                @Override
                public net.minecraft.world.level.portal.PortalInfo getPortalInfo(Entity entity, ServerLevel dest,
                        java.util.function.Function<ServerLevel, net.minecraft.world.level.portal.PortalInfo> d) {
                    return new net.minecraft.world.level.portal.PortalInfo(out, entity.getDeltaMovement(), yaw,
                            entity.getXRot());
                }
            });
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        // hit: it closes (the original's closing click)
        if (!this.level().isClientSide && source.getEntity() instanceof Player) {
            close();
            if (this.level() instanceof ServerLevel level) {
                SpacePortal o = partner(level);
                if (o != null) {
                    o.close();
                }
            }
        }
        return true;
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public boolean isAttackable() {
        return true;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.hasUUID("Partner")) {
            this.partner = tag.getUUID("Partner");
        }
        if (tag.contains("PartnerDimension")) {
            this.partnerDim = ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                    new ResourceLocation(tag.getString("PartnerDimension")));
        }
        this.expiresAt = tag.contains("ExpiresAt") ? tag.getLong("ExpiresAt") : -1L;
        this.entityData.set(COLOR, tag.getInt("Color"));
        this.entityData.set(FIRE, tag.getBoolean("Fire"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (this.partner != null) {
            tag.putUUID("Partner", this.partner);
        }
        if (this.partnerDim != null) {
            tag.putString("PartnerDimension", this.partnerDim.location().toString());
        }
        tag.putLong("ExpiresAt", this.expiresAt);
        tag.putInt("Color", color());
        tag.putBoolean("Fire", fire());
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    public BlockPos feet() {
        return this.blockPosition();
    }
}
