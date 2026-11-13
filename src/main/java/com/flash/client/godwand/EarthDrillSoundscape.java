package com.flash.client.godwand;

import net.minecraft.util.Mth;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance.Attenuation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

public final class EarthDrillSoundscape {
   private static final ResourceLocation AUDIO = com.flash.godwand.GodWandSounds.id("earth_drill_sequence");
   private static final String PLACEHOLDER_SHA = "225b65ec21cc27259fd742d6e1488a69c28f4a0365301d533eeaf433e3c60443";
   private static final List<SoundInstance> voices = new ArrayList<>();
   private static ClientLevel world;
   private static long start;
   private static int lastAge;
   private static float gain;
   private static boolean custom;

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

   static float distanceGain(double distance) {
      return (float)Mth.clamp((160.0 - distance) / 112.0, 0.0, 1.0);
   }

   public static void start(Vec3 center) {
      reset();
      Minecraft client = Minecraft.getInstance();
      if (client != null && client.level != null && client.player != null) {
         world = client.level;
         start = world.getGameTime();
         lastAge = -1;
         gain = distanceGain(client.player.position().distanceTo(center));
         Optional<Resource> resource = client.getResourceManager().getResource(new ResourceLocation("flash", "sounds/godwand/earth_drill_sequence.ogg"));
         if (resource.isPresent()) {
            try (InputStream input = resource.get().open()) {
               custom = isCustomAudio(input.readAllBytes());
            } catch (IOException ignored) {
               custom = false;
            }
         }

         if (custom) {
            playAt(AUDIO, 0.9F, 1.0F, center);
         } else {
            cue(0);
         }

         lastAge = 0;
      }
   }

   public static void tick(Minecraft client) {
      if (world != null) {
         if (world == client.level && client.player != null) {
            int age = (int)(world.getGameTime() - start);
            if (!custom) {
               if (age < 0 || age > 200) {
                  reset();
                  return;
               }

               int first = Math.max(lastAge + 1, age - 2);
               if (lastAge < 167 && 167 < first) {
                  cue(167);
               }

               for (int tick = first; tick <= Math.min(age, 176); tick++) {
                  cue(tick);
               }
            } else if (age < 0 || age > 1200) {
               reset();
               return;
            }

            lastAge = Math.max(lastAge, age);
         } else {
            reset();
         }
      }
   }

   private static void cue(int age) {
      if (age == 0) {
         play(SoundEvents.NETHERITE_BLOCK_BREAK, 0.7F, 0.7F);
         play(SoundEvents.BEACON_ACTIVATE, 0.35F, 0.85F);
      }

      if (age > 0 && age <= 88 && age % 12 == 0) {
         play(SoundEvents.BASALT_BREAK, 0.38F, 0.65F + age * 0.004F);
      }

      if (age >= 38 && age <= 158 && age % 20 == 0) {
         play(SoundEvents.GRINDSTONE_USE, 0.38F + age * 0.001F, 0.65F + age * 0.004F);
      }

      if (age >= 96 && age <= 158 && age % 18 == 0) {
         play(SoundEvents.ELYTRA_FLYING, 0.35F + (age - 96) * 0.003F, 0.8F + (age - 96) * 0.008F);
      }

      if (age == 159) {
         play(SoundEvents.GENERIC_EXPLODE, 0.8F, 1.15F);
      }

      if (age == 167) {
         play(SoundEvents.GENERIC_EXPLODE, 0.9F, 0.55F);
         play(SoundEvents.GENERIC_EXPLODE, 0.7F, 0.6F);
      }
   }

   private static void play(SoundEvent event, float volume, float pitch) {
      play(event.getLocation(), volume, pitch);
   }

   private static void play(ResourceLocation id, float volume, float pitch) {
      SimpleSoundInstance sound = new SimpleSoundInstance(
         id, SoundSource.PLAYERS, volume * gain, pitch, RandomSource.create(), false, 0, Attenuation.NONE, 0.0, 0.0, 0.0, true
      );
      voices.add(sound);
      Minecraft.getInstance().getSoundManager().play(sound);
   }

   private static void playAt(ResourceLocation id, float volume, float pitch, Vec3 pos) {
      SimpleSoundInstance sound = new SimpleSoundInstance(
         id, SoundSource.PLAYERS, volume * gain, pitch, RandomSource.create(), false, 0, Attenuation.NONE, pos.x, pos.y, pos.z, false
      );
      voices.add(sound);
      Minecraft.getInstance().getSoundManager().play(sound);
   }

   public static void reset() {
      Minecraft client = Minecraft.getInstance();
      if (client != null) {
         for (SoundInstance voice : voices) {
            client.getSoundManager().stop(voice);
         }
      }

      voices.clear();
      world = null;
      custom = false;
   }
}
