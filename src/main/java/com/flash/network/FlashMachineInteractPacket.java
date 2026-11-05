package com.flash.network;

import com.flash.block.PotionContainerBlockEntity;
import com.flash.block.TransformationChamberBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkHooks;

/**
 * Client -> server interaction with a large machine.
 *
 * <p>The chamber is a single block whose model extends several blocks around
 * its controller cell. Vanilla validates the interaction against the
 * controller cell, which is too far away when the player clicks the front
 * centre of the machine. The client performs a custom footprint raycast and
 * sends this small packet instead; the server still validates the distance and
 * owns the actual enter/menu action.</p>
 */
public record FlashMachineInteractPacket(BlockPos pos, int handId) {

    private static final double MAX_DISTANCE_SQR = 8.0D * 8.0D;

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeBlockPos(this.pos);
        buffer.writeVarInt(this.handId);
    }

    public FlashMachineInteractPacket(FriendlyByteBuf buffer) {
        this(buffer.readBlockPos(), buffer.readVarInt());
    }

    public void handle(NetworkEvent.Context context) {
        ServerPlayer player = context.getSender();
        if (player == null || player.level() == null) {
            context.setPacketHandled(true);
            return;
        }
        if (player.distanceToSqr(Vec3.atCenterOf(this.pos)) > MAX_DISTANCE_SQR) {
            context.setPacketHandled(true);
            return;
        }

        BlockEntity blockEntity = player.level().getBlockEntity(this.pos);
        if (blockEntity instanceof TransformationChamberBlockEntity chamber) {
            chamber.tryEnter(player);
        } else if (blockEntity instanceof PotionContainerBlockEntity container) {
            NetworkHooks.openScreen(player, container,
                    buffer -> buffer.writeBlockPos(this.pos));
        }
        context.setPacketHandled(true);
    }

    public InteractionHand hand() {
        return this.handId == InteractionHand.OFF_HAND.ordinal()
                ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
    }
}
