package com.flash.client;

import com.flash.FlashMod;
import com.flash.ability.FlashAbility;
import com.flash.entity.FlashSuitStand;
import com.flash.item.LexArmorItem;
import com.flash.lex.LexSuit;
import com.flash.network.FlashNetwork;
import com.flash.network.LexKeyPacket;
import com.flash.registry.ModSounds;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Lex Luthor's suit on the client: the keys (the ability keys of the heroes: missiles on the 2nd, the beam held on
 * the 3rd), the beam hum while it is held, and the battery bar on the left of the screen (also the charge of a
 * suit on an armour stand you look at).
 */
@Mod.EventBusSubscriber(modid = FlashMod.MODID, value = Dist.CLIENT)
public final class LexClient {

    private static boolean missileDown;
    private static boolean beamDown;
    private static BeamSound beamSound;

    private LexClient() {
    }

    private static boolean pilot(Player p) {
        return p != null && LexArmorItem.wearingFull(p) && !com.flash.power.HeroBalance.isHero(p);
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.phase != TickEvent.Phase.END || mc.player == null) {
            return;
        }
        boolean usable = pilot(mc.player) && mc.screen == null;
        KeyMapping mk = FlashAbilityKeys.mapping(FlashAbility.MASS_PUNCH);
        KeyMapping bk = FlashAbilityKeys.mapping(FlashAbility.BARRAGE);
        boolean m = usable && mk != null && mk.isDown();
        boolean b = usable && bk != null && bk.isDown();
        if (m != missileDown) {
            missileDown = m;
            if (m) {
                FlashNetwork.CHANNEL.sendToServer(new LexKeyPacket(LexSuit.KEY_MISSILES, true));
            }
        }
        if (b != beamDown) {
            beamDown = b;
            FlashNetwork.CHANNEL.sendToServer(new LexKeyPacket(LexSuit.KEY_BEAM, b));
        }
        boolean beaming = b && LexArmorItem.energy(mc.player.getItemBySlot(EquipmentSlot.CHEST)) > 0.0F;
        if (beaming && (beamSound == null || beamSound.isStopped())) {
            beamSound = new BeamSound(mc.player);
            mc.getSoundManager().play(beamSound);
        }
    }

    private static final class BeamSound extends AbstractTickableSoundInstance {
        private final Player player;

        BeamSound(Player player) {
            super(ModSounds.LEX_BEAM_LOOP.get(), SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
            this.player = player;
            this.looping = true;
            this.volume = 0.7F;
            this.x = player.getX();
            this.y = player.getEyeY();
            this.z = player.getZ();
        }

        @Override
        public void tick() {
            if (!beamDown || this.player.isRemoved()
                    || LexArmorItem.energy(this.player.getItemBySlot(EquipmentSlot.CHEST)) <= 0.0F) {
                this.stop();
                return;
            }
            this.x = this.player.getX();
            this.y = this.player.getEyeY();
            this.z = this.player.getZ();
        }
    }

    /** The battery bar on the left of the screen. */
    public static final class Hud implements IGuiOverlay {
        @Override
        public void render(ForgeGui gui, GuiGraphics g, float partialTick, int width, int height) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.options.hideGui) {
                return;
            }
            ItemStack chest = ItemStack.EMPTY;
            boolean stand = false;
            if (LexArmorItem.wearingFull(mc.player)) {
                chest = mc.player.getItemBySlot(EquipmentSlot.CHEST);
            } else if (mc.hitResult instanceof EntityHitResult eh && eh.getEntity() instanceof FlashSuitStand s
                    && LexArmorItem.isLex(s.getItemBySlot(EquipmentSlot.CHEST))) {
                chest = s.getItemBySlot(EquipmentSlot.CHEST);
                stand = true;
            }
            if (chest.isEmpty()) {
                return;
            }
            float e = LexArmorItem.energy(chest);
            float f = e / LexArmorItem.MAX_ENERGY;
            boolean low = e < 20.0F;
            boolean blink = low && (mc.player.tickCount / 8) % 2 == 0;
            int barH = Math.min(120, height / 2);
            int x = 8;
            int y = (height - barH) / 2;
            int w = 8;
            g.fill(x - 2, y - 2, x + w + 2, y + barH + 2, 0xC0101418);
            g.fill(x - 1, y - 1, x + w + 1, y + barH + 1, 0xFF4A2A66);
            g.fill(x, y, x + w, y + barH, 0xFF0C1410);
            int filled = Math.round(barH * f);
            int color = e <= 0.0F ? 0xFF555555 : low ? (blink ? 0xFFFF4040 : 0xFFB02020) : 0xFF6CFF6C;
            if (filled > 0) {
                g.fill(x, y + barH - filled, x + w, y + barH, color);
                g.fill(x, y + barH - filled, x + 2, y + barH, 0x40FFFFFF);
            }
            for (int i = 1; i < 10; i++) {
                int ty = y + barH * i / 10;
                g.fill(x, ty, x + w, ty + 1, 0x60000000);
            }
            String pct = Math.round(e) + "%";
            g.drawString(mc.font, pct, x + w + 5, y + barH - filled - 4 < y ? y : y + barH - Math.max(filled, 8),
                    low ? 0xFFFF6060 : 0xFFB6FFB0, true);
            Component label = Component.translatable(stand ? "hud.flash.lex_charging" : "hud.flash.lex_energy");
            g.drawString(mc.font, label, x - 2, y - 13, 0xFFC9A4FF, true);
            if (low && !stand) {
                g.drawString(mc.font, Component.translatable("hud.flash.lex_low"), x - 2, y + barH + 6,
                        blink ? 0xFFFF4040 : 0xFF902020, true);
            }
        }
    }
}
