package com.flash.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/** Player -> server: one of the Scarlet Witch's own powers (ScarletAbilities), at an entity or at nothing (-1). */
public record ScarletActionPacket(int action, int entity, int value) {

    public static final int MIND = 0;
    public static final int MIND_VARIANT = 1;
    public static final int BOLT = 2;
    public static final int BEAM = 3;
    public static final int BLAST = 4;
    public static final int HEAL = 5;
    public static final int RAISE = 6;
    public static final int DISMEMBER = 7;

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeByte(this.action);
        buffer.writeVarInt(this.entity);
        buffer.writeVarInt(this.value);
    }

    public ScarletActionPacket(FriendlyByteBuf buffer) {
        this(buffer.readByte(), buffer.readVarInt(), buffer.readVarInt());
    }

    public void handle(NetworkEvent.Context context) {
        context.setPacketHandled(true);
        ServerPlayer p = context.getSender();
        if (p != null) {
            com.flash.scarlet.ScarletAbilities.act(p, this.action, this.entity, this.value);
        }
    }
}
