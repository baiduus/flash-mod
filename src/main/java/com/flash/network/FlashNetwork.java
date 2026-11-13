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

    private static final String PROTOCOL = "39";
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

        CHANNEL.messageBuilder(FlashRingPacket.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(FlashRingPacket::encode)
                .decoder(FlashRingPacket::new)
                .consumerMainThread((message, context) -> message.handle(context.get()))
                .add();

        CHANNEL.messageBuilder(FlashRingSyncPacket.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(FlashRingSyncPacket::encode)
                .decoder(FlashRingSyncPacket::new)
                .consumerMainThread((message, context) -> message.handle(context.get()))
                .add();

        CHANNEL.messageBuilder(HulkSyncPacket.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(HulkSyncPacket::encode)
                .decoder(HulkSyncPacket::new)
                .consumerMainThread((message, context) -> message.handle(context.get()))
                .add();

        CHANNEL.messageBuilder(HulkTransformPacket.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(HulkTransformPacket::encode)
                .decoder(HulkTransformPacket::new)
                .consumerMainThread((message, context) -> message.handle(context.get()))
                .add();

        CHANNEL.messageBuilder(HulkAbilityPacket.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(HulkAbilityPacket::encode)
                .decoder(HulkAbilityPacket::new)
                .consumerMainThread((message, context) -> message.handle(context.get()))
                .add();

        CHANNEL.messageBuilder(HulkSettingsPacket.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(HulkSettingsPacket::encode)
                .decoder(HulkSettingsPacket::new)
                .consumerMainThread((message, context) -> message.handle(context.get()))
                .add();

        CHANNEL.messageBuilder(HulkLevelPacket.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(HulkLevelPacket::encode)
                .decoder(HulkLevelPacket::new)
                .consumerMainThread((message, context) -> message.handle(context.get()))
                .add();

        CHANNEL.messageBuilder(HulkAnimPacket.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(HulkAnimPacket::encode)
                .decoder(HulkAnimPacket::new)
                .consumerMainThread((message, context) -> message.handle(context.get()))
                .add();

        CHANNEL.messageBuilder(HulkThrowPacket.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(HulkThrowPacket::encode)
                .decoder(HulkThrowPacket::new)
                .consumerMainThread((message, context) -> message.handle(context.get()))
                .add();

        CHANNEL.messageBuilder(SupermanSyncPacket.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SupermanSyncPacket::encode)
                .decoder(SupermanSyncPacket::new)
                .consumerMainThread((message, context) -> message.handle(context.get()))
                .add();

        CHANNEL.messageBuilder(SupermanAbilityPacket.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(SupermanAbilityPacket::encode)
                .decoder(SupermanAbilityPacket::new)
                .consumerMainThread((message, context) -> message.handle(context.get()))
                .add();

        CHANNEL.messageBuilder(SupermanTransformPacket.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(SupermanTransformPacket::encode)
                .decoder(SupermanTransformPacket::new)
                .consumerMainThread((message, context) -> message.handle(context.get()))
                .add();

        CHANNEL.messageBuilder(SupermanSettingsPacket.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(SupermanSettingsPacket::encode)
                .decoder(SupermanSettingsPacket::new)
                .consumerMainThread((message, context) -> message.handle(context.get()))
                .add();

        CHANNEL.messageBuilder(ThorSyncPacket.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ThorSyncPacket::encode)
                .decoder(ThorSyncPacket::new)
                .consumerMainThread((message, context) -> message.handle(context.get()))
                .add();

        CHANNEL.messageBuilder(ThorAbilityPacket.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ThorAbilityPacket::encode)
                .decoder(ThorAbilityPacket::new)
                .consumerMainThread((message, context) -> message.handle(context.get()))
                .add();

        CHANNEL.messageBuilder(ThorTransformPacket.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ThorTransformPacket::encode)
                .decoder(ThorTransformPacket::new)
                .consumerMainThread((message, context) -> message.handle(context.get()))
                .add();

        CHANNEL.messageBuilder(ThorSettingsPacket.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ThorSettingsPacket::encode)
                .decoder(ThorSettingsPacket::new)
                .consumerMainThread((message, context) -> message.handle(context.get()))
                .add();

        CHANNEL.messageBuilder(ThorFxPacket.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ThorFxPacket::encode)
                .decoder(ThorFxPacket::new)
                .consumerMainThread((message, context) -> message.handle(context.get()))
                .add();

        CHANNEL.messageBuilder(SupermanCutPacket.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SupermanCutPacket::encode)
                .decoder(SupermanCutPacket::new)
                .consumerMainThread((message, context) -> message.handle(context.get()))
                .add();

        CHANNEL.messageBuilder(SupermanSuitPacket.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(SupermanSuitPacket::encode)
                .decoder(SupermanSuitPacket::new)
                .consumerMainThread((message, context) -> message.handle(context.get()))
                .add();

        CHANNEL.messageBuilder(SupermanAnimPacket.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SupermanAnimPacket::encode)
                .decoder(SupermanAnimPacket::new)
                .consumerMainThread((message, context) -> message.handle(context.get()))
                .add();

        CHANNEL.messageBuilder(SupermanFxPacket.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SupermanFxPacket::encode)
                .decoder(SupermanFxPacket::new)
                .consumerMainThread((message, context) -> message.handle(context.get()))
                .add();
    
        CHANNEL.messageBuilder(LexSyncPacket.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(LexSyncPacket::encode)
                .decoder(LexSyncPacket::new)
                .consumerMainThread((message, context) -> message.handle(context.get()))
                .add();

        CHANNEL.messageBuilder(LexFxPacket.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(LexFxPacket::encode)
                .decoder(LexFxPacket::new)
                .consumerMainThread((message, context) -> message.handle(context.get()))
                .add();

        CHANNEL.messageBuilder(LexKeyPacket.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(LexKeyPacket::encode)
                .decoder(LexKeyPacket::new)
                .consumerMainThread((message, context) -> message.handle(context.get()))
                .add();

        CHANNEL.messageBuilder(YasSyncPacket.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(YasSyncPacket::encode)
                .decoder(YasSyncPacket::new)
                .consumerMainThread((message, context) -> message.handle(context.get()))
                .add();

        CHANNEL.messageBuilder(YasFxPacket.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(YasFxPacket::encode)
                .decoder(YasFxPacket::new)
                .consumerMainThread((message, context) -> message.handle(context.get()))
                .add();

        CHANNEL.messageBuilder(YasDialogPacket.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(YasDialogPacket::encode)
                .decoder(YasDialogPacket::new)
                .consumerMainThread((message, context) -> message.handle(context.get()))
                .add();

        CHANNEL.messageBuilder(YasAnswerPacket.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(YasAnswerPacket::encode)
                .decoder(YasAnswerPacket::new)
                .consumerMainThread((message, context) -> message.handle(context.get()))
                .add();

        CHANNEL.messageBuilder(YasSettingsPacket.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(YasSettingsPacket::encode)
                .decoder(YasSettingsPacket::new)
                .consumerMainThread((message, context) -> message.handle(context.get()))
                .add();

        CHANNEL.messageBuilder(YasTelekinesisPacket.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(YasTelekinesisPacket::encode)
                .decoder(YasTelekinesisPacket::new)
                .consumerMainThread((message, context) -> message.handle(context.get()))
                .add();

        CHANNEL.messageBuilder(YasRealityPacket.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(YasRealityPacket::encode)
                .decoder(YasRealityPacket::new)
                .consumerMainThread((message, context) -> message.handle(context.get()))
                .add();

        CHANNEL.messageBuilder(YasBlackHolePacket.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(YasBlackHolePacket::encode)
                .decoder(YasBlackHolePacket::new)
                .consumerMainThread((message, context) -> message.handle(context.get()))
                .add();

        CHANNEL.messageBuilder(ScarletSyncPacket.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ScarletSyncPacket::encode)
                .decoder(ScarletSyncPacket::new)
                .consumerMainThread((message, context) -> message.handle(context.get()))
                .add();

        CHANNEL.messageBuilder(ScarletActionPacket.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ScarletActionPacket::encode)
                .decoder(ScarletActionPacket::new)
                .consumerMainThread((message, context) -> message.handle(context.get()))
                .add();

        CHANNEL.messageBuilder(ScarletFxPacket.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ScarletFxPacket::encode)
                .decoder(ScarletFxPacket::new)
                .consumerMainThread((message, context) -> message.handle(context.get()))
                .add();

        CHANNEL.messageBuilder(ScarletCooldownPacket.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ScarletCooldownPacket::encode)
                .decoder(ScarletCooldownPacket::new)
                .consumerMainThread((message, context) -> message.handle(context.get()))
                .add();

        CHANNEL.messageBuilder(ScarletSettingsPacket.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ScarletSettingsPacket::encode)
                .decoder(ScarletSettingsPacket::new)
                .consumerMainThread((message, context) -> message.handle(context.get()))
                .add();

        CHANNEL.messageBuilder(HeroProgressPacket.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(HeroProgressPacket::encode)
                .decoder(HeroProgressPacket::new)
                .consumerMainThread((message, context) -> message.handle(context.get()))
                .add();

        CHANNEL.messageBuilder(SupermanLaunchPacket.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(SupermanLaunchPacket::encode)
                .decoder(SupermanLaunchPacket::new)
                .consumerMainThread((message, context) -> message.handle(context.get()))
                .add();
    }
}
