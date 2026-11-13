package com.flash.client.godwand;

import net.minecraft.util.Mth;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance.Attenuation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

public final class StoneCannonWindSound extends AbstractTickableSoundInstance {
   private static StoneCannonWindSound instance;
   private float target;

   private StoneCannonWindSound(float initial) {
      super(SoundEvents.ELYTRA_FLYING, SoundSource.PLAYERS, RandomSource.create());
      this.relative = true;
      this.looping = true;
      this.delay = 0;
      this.attenuation = Attenuation.NONE;
      this.volume = this.target = initial;
      this.pitch = 0.8F;
   }

   public static float windVolume(int ticks) {
      float ramp = Mth.clamp((ticks - 50) / 30.0F, 0.0F, 1.0F);
      return 0.18F * ramp * ramp;
   }

   public static void update(Minecraft client) {
      if (client.player != null && client.level != null && true) {
         if (GodWandClient.charging(client.player, 4)) {
            float volume = windVolume(GodWandClient.chargeTicks(client.player));
            if (volume < 0.003F) {
               reset();
            } else {
               if (instance == null || instance.isStopped()) {
                  instance = new StoneCannonWindSound(volume);
                  client.getSoundManager().play(instance);
               }

               instance.target = volume;
            }
         } else {
            reset();
         }
      } else {
         reset();
      }
   }

   public static void reset() {
      if (instance != null) {
         instance.stop();
         Minecraft client = Minecraft.getInstance();
         if (client != null) {
            client.getSoundManager().stop(instance);
         }

         instance = null;
      }
   }

   public void tick() {
      this.volume = this.volume + (this.target - this.volume) * 0.18F;
      this.pitch = this.pitch + (0.8F + this.target * 2.0F - this.pitch) * 0.12F;
   }
}
