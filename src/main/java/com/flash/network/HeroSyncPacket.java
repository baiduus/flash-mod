package com.flash.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

/**
 * Server -> clients: a player is (or is no longer) Spider-Man / Doctor Doom, with a number of that hero's own
 * (Spider-Man: his suit; Doom: his flags, DoomPowers.F_*).
 */
public record HeroSyncPacket(int hero, int entity, boolean has, int data) {

    public static final int SPIDER = 0;
    public static final int DOOM = 1;
    public static final int WONDER = 2;
    public static final int ULTRON = 3;
    /** (HeroFxPacket only: the Hulk's effects, HulkVfx.) */
    public static final int HULK = 4;
    /** (HeroFxPacket only: the Flash's effects, FlashCineVfx.) */
    public static final int FLASH = 5;

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeByte(this.hero);
        buffer.writeVarInt(this.entity);
        buffer.writeBoolean(this.has);
        buffer.writeVarInt(this.data);
    }

    public HeroSyncPacket(FriendlyByteBuf buffer) {
        this(buffer.readByte(), buffer.readVarInt(), buffer.readBoolean(), buffer.readVarInt());
    }

    public void handle(NetworkEvent.Context context) {
        context.setPacketHandled(true);
        net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                () -> () -> {
                    if (this.hero == SPIDER) {
                        com.flash.client.SpiderClient.set(this.entity, this.has, this.data);
                    } else if (this.hero == WONDER) {
                        com.flash.client.WonderClient.set(this.entity, this.has, this.data);
                    } else if (this.hero == ULTRON) {
                        com.flash.client.UltronClient.set(this.entity, this.has, this.data);
                    } else {
                        com.flash.client.DoomClient.set(this.entity, this.has, this.data);
                    }
                });
    }
}
