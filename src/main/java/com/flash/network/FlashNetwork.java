package com.flash.network;

import com.flash.FlashMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * Network channel of the mod. It only carries tiny state packets:
 * server -> client progression data and client -> server level selection.
 * Attribute modifiers are already synced by vanilla Minecraft.
 */
public final class FlashNetwork {

    private static final String PROTOCOL = "9";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(FlashMod.MODID, "main"),
            () -> PROTOCOL,
            PROTOCOL::equals,
            PROTOCOL::equals
    );

    private static int nextId = 0;

    private FlashNetwork() {
    }

    public static void register() {
        CHANNEL.messageBuilder(FlashSyncPacket.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(FlashSyncPacket::encode)
                .decoder(FlashSyncPacket::new)
                .consumerMainThread((message, context) -> message.handle(context.get()))
                .add();

        CHANNEL.messageBuilder(FlashSelectLevelPacket.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(FlashSelectLevelPacket::encode)
                .decoder(FlashSelectLevelPacket::new)
                .consumerMainThread((message, context) -> message.handle(context.get()))
                .add();

        CHANNEL.messageBuilder(FlashVisualSyncPacket.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(FlashVisualSyncPacket::encode)
                .decoder(FlashVisualSyncPacket::new)
                .consumerMainThread((message, context) -> message.handle(context.get()))
                .add();

        CHANNEL.messageBuilder(FlashAbilityPacket.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(FlashAbilityPacket::encode)
                .decoder(FlashAbilityPacket::new)
                .consumerMainThread((message, context) -> message.handle(context.get()))
                .add();

        CHANNEL.messageBuilder(FlashSettingsPacket.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(FlashSettingsPacket::encode)
                .decoder(FlashSettingsPacket::new)
                .consumerMainThread((message, context) -> message.handle(context.get()))
                .add();

        CHANNEL.messageBuilder(FlashAbilitySyncPacket.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(FlashAbilitySyncPacket::encode)
                .decoder(FlashAbilitySyncPacket::new)
                .consumerMainThread((message, context) -> message.handle(context.get()))
                .add();

        CHANNEL.messageBuilder(FlashBeamPacket.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(FlashBeamPacket::encode)
                .decoder(FlashBeamPacket::new)
                .consumerMainThread((message, context) -> message.handle(context.get()))
                .add();

        CHANNEL.messageBuilder(FlashToggleSpeedPacket.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(FlashToggleSpeedPacket::encode)
                .decoder(FlashToggleSpeedPacket::new)
                .consumerMainThread((message, context) -> message.handle(context.get()))
                .add();

        CHANNEL.messageBuilder(FlashMachineInteractPacket.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(FlashMachineInteractPacket::encode)
                .decoder(FlashMachineInteractPacket::new)
                .consumerMainThread((message, context) -> message.handle(context.get()))
                .add();
    }
}
