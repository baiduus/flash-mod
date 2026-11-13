package com.flash.client.godwand;

import net.minecraft.util.Mth;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance.Attenuation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

public final class SupernovaSoundscape {
   private static final ResourceLocation AUDIO = com.flash.godwand.GodWandSounds.id("supernova_sequence");
   private static final String PLACEHOLDER_SHA = "225b65ec21cc27259fd742d6e1488a69c28f4a0365301d533eeaf433e3c60443";
   private static SupernovaSoundscape.Charge chargeSound;
   private static SimpleSoundInstance sequence;
   private static ClientLevel world;
   private static boolean custom;
   private static boolean customChecked;
   private static boolean charging;
   private static boolean activated;
   private static int cancelGraceTicks;
   private static long activationTime;

   static boolean isCustomAudio(byte[] bytes) {
      if (bytes.length >= 4 && bytes[0] == 79 && bytes[1] == 103 && bytes[2] == 103 && bytes[3] == 83) {
         try {
            return !HexFormat.of()
               .formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))
               .equals("225b65ec21cc27259fd742d6e1488a69c28f4a0365301d533eeaf433e3c60443");
         } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
         }
      } else {
         return false;
      }
   }

   private static boolean checkCustom(Minecraft client) {
      if (customChecked) {
         return custom;
      }

      Optional<Resource> resource = client.getResourceManager().getResource(new ResourceLocation("flash", "sounds/godwand/supernova_sequence.ogg"));
      if (resource.isPresent()) {
         try (InputStream input = resource.get().open()) {
            custom = isCustomAudio(input.readAllBytes());
         } catch (IOException ignored) {
            custom = false;
         }
      }

      customChecked = true;
      return custom;
   }

   static float volume(double age, float charge) {
      return age < 0.0 && charge > 0.0F ? 0.08F + Mth.clamp(charge, 0.0F, 1.0F) * 0.18F : 0.0F;
   }

   public static void start(Vec3 center) {
      Minecraft client = Minecraft.getInstance();
      if (client != null && client.player != null) {
         checkCustom(client);
         activated = true;
         charging = false;
         cancelGraceTicks = 0;
         if (client.level != null) {
            world = client.level;
            activationTime = client.level.getGameTime();
         }

         if (chargeSound != null) {
            client.getSoundManager().stop(chargeSound);
            chargeSound = null;
         }

         if (custom) {
            if (sequence != null && client.getSoundManager().isActive(sequence)) {
               return;
            }

            if (sequence != null) {
               client.getSoundManager().stop(sequence);
               sequence = null;
            }

            sequence = new SimpleSoundInstance(AUDIO, SoundSource.PLAYERS, 1.0F, 1.0F, RandomSource.create(), false, 0, Attenuation.NONE, 0.0, 0.0, 0.0, true);
            client.getSoundManager().play(sequence);
         }
      }
   }

   static void tick(double age, float charge, float distance) {
      Minecraft client = Minecraft.getInstance();
      if (client != null && client.level != null && client.player != null) {
         if (world == null) {
            world = client.level;
         }

         if (world != client.level) {
            reset();
         } else {
            checkCustom(client);
            if (custom) {
               if (sequence != null && !client.getSoundManager().isActive(sequence)) {
                  sequence = null;
                  activated = false;
                  charging = false;
                  cancelGraceTicks = 0;
               }

               if (charge > 0.0F) {
                  cancelGraceTicks = 0;
                  if (!charging && !activated) {
                     if (sequence != null) {
                        client.getSoundManager().stop(sequence);
                        sequence = null;
                     }

                     charging = true;
                     sequence = new SimpleSoundInstance(
                        AUDIO, SoundSource.PLAYERS, 1.0F, 1.0F, RandomSource.create(), false, 0, Attenuation.NONE, 0.0, 0.0, 0.0, true
                     );
                     client.getSoundManager().play(sequence);
                  }

                  if (chargeSound != null) {
                     client.getSoundManager().stop(chargeSound);
                     chargeSound = null;
                  }
               } else if (charging && !activated) {
                  cancelGraceTicks++;
                  if (cancelGraceTicks > 15) {
                     if (sequence != null) {
                        client.getSoundManager().stop(sequence);
                        sequence = null;
                     }

                     charging = false;
                     cancelGraceTicks = 0;
                  }
               }
            } else {
               float target = volume(age, charge);
               if (target <= 0.0F) {
                  if (chargeSound != null) {
                     client.getSoundManager().stop(chargeSound);
                  }

                  chargeSound = null;
                  return;
               }

               if (chargeSound == null || chargeSound.isStopped()) {
                  chargeSound = new SupernovaSoundscape.Charge(target);
                  client.getSoundManager().play(chargeSound);
               }

               chargeSound.target = target;
               chargeSound.tone = 0.75F + charge * 0.3F;
            }
         }
      } else {
         reset();
      }
   }

   public static void reset() {
      Minecraft client = Minecraft.getInstance();
      if (client != null) {
         if (chargeSound != null) {
            client.getSoundManager().stop(chargeSound);
         }

         if (sequence != null) {
            client.getSoundManager().stop(sequence);
         }
      }

      chargeSound = null;
      sequence = null;
      customChecked = false;
      charging = false;
      activated = false;
      cancelGraceTicks = 0;
      world = null;
   }

   private static final class Charge extends AbstractTickableSoundInstance {
      float target;
      float tone = 0.75F;

      Charge(float initial) {
         super(SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, RandomSource.create());
         this.relative = true;
         this.looping = true;
         this.delay = 0;
         this.attenuation = Attenuation.NONE;
         this.volume = this.target = initial;
         this.pitch = this.tone;
      }

      public void tick() {
         this.volume = this.volume + (this.target - this.volume) * 0.15F;
         this.pitch = this.pitch + (this.tone - this.pitch) * 0.1F;
      }
   }
}
