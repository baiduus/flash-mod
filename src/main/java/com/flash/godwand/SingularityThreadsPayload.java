package com.flash.godwand;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

/** Server -> players around: the threads of a Singularity (the beam displays' ids). */
public record SingularityThreadsPayload(List<Integer> beamIds, int recallTicks) {

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(Math.min(144, this.beamIds.size()));
        for (int i = 0; i < Math.min(144, this.beamIds.size()); i++) {
            buffer.writeVarInt(this.beamIds.get(i));
        }
        buffer.writeVarInt(this.recallTicks);
    }

    public static SingularityThreadsPayload decode(FriendlyByteBuf buffer) {
        int count = Math.min(144, buffer.readVarInt());
        List<Integer> ids = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            ids.add(buffer.readVarInt());
        }
        return new SingularityThreadsPayload(ids, buffer.readVarInt());
    }

    public void handle(NetworkEvent.Context context) {
        context.setPacketHandled(true);
        net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                () -> () -> com.flash.client.godwand.SingularityThreadRenderer.receive(this));
    }
}
