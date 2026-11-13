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
        // the ground torn away: a sphere as wide as the crater of Hand of God's sun, growing with it
        if (size > 0.3F && !collapsing() && ownerDestroys(level)) {
            eat(level, c, age);
        }
        this.pulled.removeIf(id -> !(level.getEntity(id) instanceof FallingBlockEntity f) || !f.isAlive());
    }

    /** Its maker's block destruction switch (Yas' K menu): off, it pulls and crushes but breaks no block. */
    private boolean ownerDestroys(ServerLevel level) {
        UUID owner = owner();
        if (owner == null || !(level.getPlayerByUUID(owner) instanceof net.minecraft.world.entity.player.Player p)) {
            return true;
        }
        return com.flash.yas.YasDestruction.destroys(p);
    }

    /** Ids of the blocks it tore loose, flying into it (imploded when it collapses). */
    private final java.util.Set<Integer> pulled = new java.util.HashSet<>();
    /** How far through the sphere of offsets it has eaten. */
    private int eaten;
    private static int[] OFFSETS;
    private static int offsetsRadius;

    /** The radius of Hand of God's sun crater (its config), within 12..40. */
    private static int craterRadius() {
        try {
            Object v = Class.forName("handofgod.Config").getField("RAIO_CRATERA").get(null);
            if (v instanceof net.minecraftforge.common.ForgeConfigSpec.DoubleValue d) {
                return (int) Math.round(Mth.clamp(d.get(), 12.0D, 40.0D));
            }
        } catch (ReflectiveOperationException | LinkageError | IllegalStateException ignored) {
            // no Hand of God config: its default
        }
        return 32;
    }

    /** Every block offset of a sphere, nearest first (packed 8 bits a coordinate). */
    private static int[] offsets(int r) {
        if (OFFSETS != null && offsetsRadius == r) {
            return OFFSETS;
        }
        java.util.List<int[]> all = new java.util.ArrayList<>();
        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
                for (int z = -r; z <= r; z++) {
                    int d = x * x + y * y + z * z;
                    if (d <= r * r) {
                        all.add(new int[] {d, x, y, z});
                    }
                }
            }
        }
        all.sort(java.util.Comparator.comparingInt(a -> a[0]));
        int[] out = new int[all.size()];
        for (int i = 0; i < out.length; i++) {
            int[] a = all.get(i);
            out[i] = ((a[1] + 128) << 16) | ((a[2] + 128) << 8) | (a[3] + 128);
        }
        OFFSETS = out;
        offsetsRadius = r;
        return out;
    }

    private void eat(ServerLevel level, Vec3 c, int age) {
        int r = craterRadius();
        int[] all = offsets(r);
        // the reach grows over the first two thirds of its life
        float grow = Mth.clamp((age - GROW * 0.5F) / (LIFE * 0.66F), 0.0F, 1.0F);
        double reach = r * grow;
        double reach2 = reach * reach;
        BlockPos center = BlockPos.containing(c);
        int budget = 2500;
        int changed = 0;
        while (this.eaten < all.length && budget-- > 0 && changed < 900) {
            int o = all[this.eaten];
            int dx = (o >> 16 & 255) - 128;
            int dy = (o >> 8 & 255) - 128;
            int dz = (o & 255) - 128;
            if (dx * dx + dy * dy + dz * dz > reach2) {
                break;
            }
            this.eaten++;
            BlockPos pos = center.offset(dx, dy, dz);
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) {
                continue;
            }
            float hard = state.getDestroySpeed(level, pos);
            if (hard < 0.0F) {
                continue; // bedrock, barriers...
            }
            changed++;
            boolean fly = this.pulled.size() < 260 && state.getFluidState().isEmpty()
                    && level.getBlockEntity(pos) == null && this.random.nextInt(4) == 0;
            if (fly) {
                FallingBlockEntity fall = FallingBlockEntity.fall(level, pos, state);
                fall.dropItem = false;
                fall.setNoGravity(true);
                fall.time = -2000;
                fall.setDeltaMovement(c.subtract(Vec3.atCenterOf(pos)).normalize().scale(0.35D));
                this.pulled.add(fall.getId());
            } else {
                level.removeBlockEntity(pos);
                level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2 | 16);
                if (this.random.nextInt(40) == 0) {
                    level.sendParticles(new net.minecraft.core.particles.BlockParticleOption(
                            ParticleTypes.BLOCK, state), pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, 4,
                            0.3D, 0.3D, 0.3D, 0.1D);
                }
            }
        }
    }

    /**
     * The collapse: everything it pulled is crushed into its heart (the blocks still flying gone, the creatures in
     * its reach torn in), and it ends in a flash, turned inward.
     */
    private void burst(ServerLevel level) {
        Vec3 c = this.position();
        play(level, "lua_impacto_sugar", SoundEvents.WARDEN_SONIC_BOOM, 3.0F, 0.5F);
        play(level, "lua_impacto_estalo", SoundEvents.GENERIC_EXPLODE, 3.0F, 0.6F);
        for (int id : this.pulled) {
            if (level.getEntity(id) instanceof FallingBlockEntity f) {
                level.sendParticles(ParticleTypes.REVERSE_PORTAL, f.getX(), f.getY(), f.getZ(), 3, 0.2D, 0.2D, 0.2D,
                        0.3D);
                f.discard();
            }
        }
        this.pulled.clear();
        UUID owner = owner();
        double reach = PULL;
        for (Entity e : level.getEntities(this, new AABB(c, c).inflate(reach), e -> !(e instanceof BlackHole))) {
            if (owner != null && owner.equals(e.getUUID())) {
                continue;
            }
            if (e instanceof Player p && (p.isCreative() || p.isSpectator())) {
                continue;
            }
            double d = e.position().distanceTo(c);
            if (d > reach) {
                continue;
            }
            if (e instanceof FallingBlockEntity || e instanceof ItemEntity || e instanceof ExperienceOrb) {
                e.discard();
                continue;
            }
            if (e instanceof LivingEntity living) {
                living.invulnerableTime = 0;
                float f = (float) (1.0D - d / reach);
                // the heart crushes everything; the edge still hurts a lot
                living.hurt(level.damageSources().fellOutOfWorld(), d < 10.0D ? 1000.0F : 20.0F + 80.0F * f);
                Vec3 in = c.subtract(e.position()).normalize().scale(1.5D * f + 0.3D);
                e.setDeltaMovement(in);
                e.hurtMarked = true;
            }
        }
        // the light of the implosion, falling inward
        for (int i = 0; i < 160; i++) {
            double a = this.random.nextDouble() * Math.PI * 2.0D;
            double u = this.random.nextDouble() * 2.0D - 1.0D;
            double s = Math.sqrt(1.0D - u * u);
            double r = 6.0D + this.random.nextDouble() * 10.0D;
            double x = s * Math.cos(a) * r;
            double y = u * r;
            double z = s * Math.sin(a) * r;
            level.sendParticles(ParticleTypes.END_ROD, c.x + x, c.y + y, c.z + z, 0, -x * 0.08D, -y * 0.08D,
                    -z * 0.08D, 1.0D);
        }
        level.sendParticles(ParticleTypes.FLASH, c.x, c.y, c.z, 3, 0.0D, 0.0D, 0.0D, 0.0D);
        level.sendParticles(ParticleTypes.SONIC_BOOM, c.x, c.y, c.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
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
