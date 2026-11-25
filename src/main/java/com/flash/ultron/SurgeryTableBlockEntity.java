package com.flash.ultron;

import com.flash.FlashMod;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.player.SleepingTimeCheckEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** The operation of the surgery table (on its head block): Ultron lies down, the claws work, the nanocore comes out. */
@Mod.EventBusSubscriber(modid = FlashMod.MODID)
public class SurgeryTableBlockEntity extends BlockEntity {

    /** Under the 100 ticks of sleep that count for skipping the night. */
    public static final int OPERATION_TICKS = 90;
    private static final DustParticleOptions RED = new DustParticleOptions(UltronPowers.RED, 0.8F);

    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    private UUID patient;
    private int ticks = -1;

    public SurgeryTableBlockEntity(BlockPos pos, BlockState state) {
        super(UltronRegistry.SURGERY_TABLE_BE.get(), pos, state);
    }

    public boolean busy() {
        return this.ticks >= 0;
    }

    void start(ServerPlayer p) {
        Level level = this.level;
        if (level == null) {
            return;
        }
        // out of the air and onto the table
        if (p.getAbilities().flying) {
            p.getAbilities().flying = false;
            p.onUpdateAbilities();
        }
        p.stopRiding();
        p.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        p.startSleeping(this.worldPosition);
        LOGGER.info("[flash] surgery started at {} on {} (sleeping={})", this.worldPosition,
                p.getGameProfile().getName(), p.isSleeping());
        this.patient = p.getUUID();
        this.ticks = 0;
        level.setBlock(this.worldPosition, this.getBlockState().setValue(SurgeryTableBlock.OPERATING, true), 3);
        level.playSound(null, this.worldPosition, SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 1.0F, 0.6F);
        level.playSound(null, this.worldPosition, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 0.8F, 1.6F);
        p.displayClientMessage(Component.translatable("message.flash.surgery.start"), true);
        this.setChanged();
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, SurgeryTableBlockEntity table) {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        if (table.ticks < 0) {
            if (state.getValue(SurgeryTableBlock.OPERATING)) {
                table.stop(level, pos);
            }
            return;
        }
        Player found = table.patient == null ? null : level.getPlayerByUUID(table.patient);
        Vec3 bed = Vec3.atBottomCenterOf(pos);
        String why = !(found instanceof ServerPlayer) ? "patient gone" : !found.isAlive() ? "dead"
                : !UltronPowers.has(found) ? "not Ultron" : found.position().distanceToSqr(bed) > 9.0D ? "walked away"
                : !found.isSleeping() && found.isShiftKeyDown() ? "got up" : null;
        if (why != null) {
            LOGGER.info("[flash] surgery at {} stopped: {}", pos, why);
            table.stop(level, pos);
            if (found instanceof ServerPlayer p2 && UltronPowers.has(p2)) {
                p2.displayClientMessage(Component.translatable("message.flash.surgery.aborted"), true);
            }
            return;
        }
        ServerPlayer p = (ServerPlayer) found;
        // kept on the table whatever wakes him (the flight, the day...): only getting up with shift stops it
        if (p.getAbilities().flying) {
            p.getAbilities().flying = false;
            p.onUpdateAbilities();
        }
        if (!p.isSleeping() || !p.getSleepingPos().map(pos::equals).orElse(false)) {
            p.startSleeping(pos);
        }
        p.setDeltaMovement(Vec3.ZERO);
        int t = table.ticks++;
        // over the chest: sparks, the laser's burn, the whine of the tools
        Vec3 chest = p.position().add(0.0D, 0.35D, 0.0D);
        if (t % 3 == 0) {
            server.sendParticles(ParticleTypes.ELECTRIC_SPARK, chest.x, chest.y, chest.z, 4, 0.2D, 0.1D, 0.2D, 0.15D);
            server.sendParticles(RED, chest.x, chest.y + 0.2D, chest.z, 3, 0.15D, 0.05D, 0.15D, 0.0D);
        }
        if (t % 10 == 0) {
            server.playSound(null, pos, SoundEvents.GRINDSTONE_USE, SoundSource.BLOCKS, 0.6F, 1.6F);
        }
        if (t % 16 == 0) {
            server.playSound(null, pos, SoundEvents.BEACON_AMBIENT, SoundSource.BLOCKS, 0.8F, 1.8F);
        }
        if (t == 40) {
            p.displayClientMessage(Component.translatable("message.flash.surgery.middle"), true);
        }
        if (table.ticks >= OPERATION_TICKS) {
            server.sendParticles(ParticleTypes.FLASH, chest.x, chest.y + 0.3D, chest.z, 1, 0, 0, 0, 0);
            server.sendParticles(new DustParticleOptions(new org.joml.Vector3f(0.75F, 0.78F, 0.84F), 1.2F), chest.x,
                    chest.y + 0.2D, chest.z, 60, 0.4D, 0.2D, 0.4D, 0.0D);
            server.playSound(null, pos, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 1.2F, 0.8F);
            server.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 1.0F, 0.6F);
            UltronPowers.revoke(p);
            p.displayClientMessage(Component.translatable("message.flash.surgery.done").withStyle(ChatFormatting.GREEN),
                    false);
            table.stop(level, pos);
            p.stopSleepInBed(true, true);
        }
    }

    private void stop(Level level, BlockPos pos) {
        this.ticks = -1;
        this.patient = null;
        BlockState s = level.getBlockState(pos);
        if (s.getBlock() instanceof SurgeryTableBlock && s.getValue(SurgeryTableBlock.OPERATING)) {
            level.setBlock(pos, s.setValue(SurgeryTableBlock.OPERATING, false), 3);
            level.playSound(null, pos, SoundEvents.PISTON_CONTRACT, SoundSource.BLOCKS, 1.0F, 0.6F);
        }
        this.setChanged();
    }

    /** Lying on the table is allowed by day too. */
    @SubscribeEvent
    public static void onSleepTime(SleepingTimeCheckEvent event) {
        event.getSleepingLocation().ifPresent(pos -> {
            if (event.getEntity().level().getBlockState(pos).getBlock() instanceof SurgeryTableBlock) {
                event.setResult(Event.Result.ALLOW);
            }
        });
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        // an operation is never resumed after a reload (the patient got up)
        this.ticks = -1;
    }

    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(this.worldPosition).inflate(2.0D, 0.0D, 2.0D).expandTowards(0.0D, 2.2D, 0.0D);
    }
}
