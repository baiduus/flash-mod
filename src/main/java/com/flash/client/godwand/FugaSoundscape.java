package com.flash.client.godwand;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Map.Entry;
import com.flash.godwand.FugaFxPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance.Attenuation;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

public final class FugaSoundscape {
   static final String CHARGE = "fuga_charge";
   static final String HOLD = "fuga_hold";
   static final String RELEASE = "fuga_release";
   static final String END = "fuga_end";
   static final String PLACEHOLDER_SHA = "9dfd4844812c24144949e8743103d18ff27928717aab16c69fabbc7225934eeb";
   private static final Map<Integer, FugaSoundscape.Cast> CASTS = new LinkedHashMap<>();
   private static final Map<String, Boolean> CUSTOM = new LinkedHashMap<>();
   private static ClientLevel world;

   private FugaSoundscape() {
   }

   private static ResourceLocation id(String namespace, String path) {
      return new ResourceLocation(namespace, path);
   }

   static boolean isCustomAudio(byte[] bytes) {
      if (bytes.length >= 4 && bytes[0] == 79 && bytes[1] == 103 && bytes[2] == 103 && bytes[3] == 83) {
         try {
            return !HexFormat.of()
               .formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))
               .equals("9dfd4844812c24144949e8743103d18ff27928717aab16c69fabbc7225934eeb");
         } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
         }
      } else {
         return false;
      }
   }

   private static boolean custom(Minecraft client, String name) {
      return CUSTOM.computeIfAbsent(name, n -> {
         Optional<Resource> resource = client.getResourceManager().getResource(id("flash", "sounds/godwand/" + n + ".ogg"));
         if (resource.isEmpty()) {
            return false;
         }

         try (InputStream input = resource.get().open()) {
            return isCustomAudio(input.readAllBytes());
         } catch (IOException e) {
            return false;
         }
      });
   }

   static void tick(Minecraft client, ClientLevel level, Map<Integer, FugaFxPayload> active) {
      if (level != world) {
         reset();
         world = level;
      }

      SoundManager sounds = client.getSoundManager();
      if (level != null && client.player != null && sounds != null && client.getResourceManager() != null) {
         long now = level.getGameTime();

         for (Entry<Integer, FugaFxPayload> entry : active.entrySet()) {
            FugaFxPayload p = entry.getValue();
            FugaSoundscape.Cast cast = CASTS.get(entry.getKey());
            if (cast == null || cast.token != p.startedAt()) {
               if (cast != null) {
                  stop(cast, true);
               }

               cast = new FugaSoundscape.Cast(p.startedAt());
               CASTS.put(entry.getKey(), cast);
            }

            if (p.phase() == cast.phase) {
               if (p.phase() == 0 && now - p.startedAt() >= 200L) {
                  if (cast.hold == null) {
                     cast.hold = new FugaSoundscape.Hold(com.flash.godwand.GodWandSounds.id("fuga_hold"), gain(client, p, false));
                     sounds.play(cast.hold);
                  }

                  if (!cast.readyCue && !custom(client, "fuga_charge")) {
                     cast.readyCue = true;
                     cue(client, "entity.blaze.shoot", p, 1.0F, 0.7F);
                  }
               }

               if (p.phase() == 2 && !cast.exploded && now - p.phaseAt() >= 80L) {
                  cast.exploded = true;
                  if (!custom(client, "fuga_end")) {
                     cue(client, "entity.generic.explode", p, 6.0F, 0.55F);
                  }
               }
            } else {
               int previous = cast.phase;
               cast.phase = p.phase();
               if (p.phase() == 0) {
                  if (custom(client, "fuga_charge")) {
                     cast.charge = play(client, "fuga_charge", gain(client, p, false));
                  } else {
                     cue(client, "entity.blaze.ambient", p, 1.5F, 0.65F);
                  }
               } else if (p.phase() == 1) {
                  fadeHold(cast);
                  if (cast.charge != null) {
                     sounds.stop(cast.charge);
                     cast.charge = null;
                  }

                  if (custom(client, "fuga_release")) {
                     cast.release = play(client, "fuga_release", gain(client, p, true));
                  } else {
                     cue(client, "item.firecharge.use", p, 3.0F, 0.6F);
                  }
               } else if (p.phase() == 2) {
                  fadeHold(cast);
                  if (custom(client, "fuga_end")) {
                     cast.end = play(client, "fuga_end", gain(client, p, false));
                  } else if (previous == 1) {
                     cue(client, "entity.blaze.shoot", p, 3.0F, 0.5F);
                  }
               }
            }
         }

         CASTS.entrySet().removeIf(entryx -> {
            if (active.containsKey(entryx.getKey())) {
               return false;
            } else {
               FugaSoundscape.Cast castx = (FugaSoundscape.Cast)entryx.getValue();
               if (castx.phase <= 0) {
                  fadeHold(castx);
                  stop(castx, true);
                  return true;
               } else {
                  fadeHold(castx);
                  return (castx.release == null || !sounds.isActive(castx.release)) && (castx.end == null || !sounds.isActive(castx.end));
               }
            }
         });
      }
   }

   private static float gain(Minecraft client, FugaFxPayload p, boolean target) {
      if (client.player.getId() == p.casterId()) {
         return 1.0F;
      }

      Vec3 eye = client.player.position();
      double distance = eye.distanceTo(new Vec3(p.x(), p.y(), p.z()));
      if (target) {
         distance = Math.min(distance, eye.distanceTo(new Vec3(p.tx(), p.ty(), p.tz())));
      }

      return AtomicRenderer.distanceGain(distance);
   }

   private static SimpleSoundInstance play(Minecraft client, String name, float gain) {
      if (gain <= 0.0F) {
         return null;
      }

      SimpleSoundInstance sound = new SimpleSoundInstance(
         com.flash.godwand.GodWandSounds.id(name), SoundSource.PLAYERS, gain, 1.0F, RandomSource.create(), false, 0, Attenuation.NONE, 0.0, 0.0, 0.0, true
      );
      client.getSoundManager().play(sound);
      return sound;
   }

   private static void cue(Minecraft client, String vanilla, FugaFxPayload p, float volume, float pitch) {
      client.getSoundManager()
         .play(
            new SimpleSoundInstance(
               id("minecraft", vanilla), SoundSource.PLAYERS, volume, pitch, RandomSource.create(), false, 0, Attenuation.LINEAR, p.x(), p.y(), p.z(), false
            )
         );
   }

   private static void fadeHold(FugaSoundscape.Cast cast) {
      if (cast.hold != null) {
         cast.hold.fading = true;
         cast.hold = null;
      }
   }

   private static void stop(FugaSoundscape.Cast cast, boolean all) {
      Minecraft client = Minecraft.getInstance();
      SoundManager sounds = client == null ? null : client.getSoundManager();
      if (sounds == null) {
         cast.charge = null;
         cast.hold = null;
      } else {
         if (cast.charge != null) {
            sounds.stop(cast.charge);
         }

         if (cast.hold != null) {
            sounds.stop(cast.hold);
         }

         if (all && cast.release != null) {
            sounds.stop(cast.release);
         }

         cast.charge = null;
         cast.hold = null;
      }
   }

   public static void reset() {
      for (FugaSoundscape.Cast cast : CASTS.values()) {
         stop(cast, true);
      }

      CASTS.clear();
      CUSTOM.clear();
      world = null;
   }

   private static final class Cast {
      final long token;
      int phase = -1;
      SimpleSoundInstance charge;
      SimpleSoundInstance release;
      SimpleSoundInstance end;
      FugaSoundscape.Hold hold;
      boolean readyCue;
      boolean exploded;
      Vec3 impact;
      long impactAt;

      Cast(long token) {
         this.token = token;
      }
   }

   private static final class Hold extends AbstractTickableSoundInstance {
      final float target;
      boolean fading;

      Hold(ResourceLocation sound, float gain) {
         super(SoundEvent.createVariableRangeEvent(sound), SoundSource.PLAYERS, RandomSource.create());
         this.looping = true;
         this.delay = 0;
         this.relative = true;
         this.attenuation = Attenuation.NONE;
         this.target = 0.6F * gain;
         this.volume = 0.001F;
      }

      public void tick() {
         if (this.fading) {
            this.volume = this.volume - this.target / 4.0F;
            if (this.volume <= 0.0F) {
               this.volume = 0.0F;
               this.stop();
            }
         } else {
            this.volume = Math.min(this.target, this.volume + this.target / 16.0F);
         }
      }
   }
}
