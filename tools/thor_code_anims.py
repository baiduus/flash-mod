#!/usr/bin/env python3
"""Writes the Thor animations that Legends: Superheroes makes in code (AnimationThorLightningStrike,
AnimationThorStormStriker, AnimationHammerSpin, AnimationHammerBlock, AnimationGodBlast, AnimationWeaponOfAKing,
AnimationStormbreakerSlam...) as PlayerAnimator emotes (player_animation/thor_*.json).

Each pose gives, per part, rotations in degrees (pitch = xRot, yaw = yRot, roll = zRot, the Minecraft ones) and
position offsets in pixels from the rest pose. Parts not given stay at rest. Run from the repository root.
"""
import json
import os

OUT = "src/main/resources/assets/flash/player_animation"
REST = {
    "head": (0.0, 0.0, 0.0),
    "torso": (0.0, 0.0, 0.0),
    "rightArm": (-5.0, 2.0, 0.0),
    "leftArm": (5.0, 2.0, 0.0),
    "rightLeg": (-1.9, 12.0, 0.0),
    "leftLeg": (1.9, 12.0, 0.0),
}


def part(name, pose):
    p = pose.get(name, {})
    rx, ry, rz = REST[name]
    out = {"pitch": p.get("p", 0.0), "yaw": p.get("y", 0.0), "roll": p.get("r", 0.0)}
    out["x"] = rx + p.get("dx", 0.0)
    out["y"] = ry + p.get("dy", 0.0)
    out["z"] = rz + p.get("dz", 0.0)
    return out


def emote(name, frames, loop=False, return_tick=0, stop_extra=6):
    moves = []
    for tick, easing, pose in frames:
        m = {"tick": tick, "easing": easing}
        for n in REST:
            m[n] = part(n, pose)
        moves.append(m)
    end = frames[-1][0]
    data = {
        "version": 3,
        "name": name,
        "author": "Herois_baiduu (from the code animations of Legends: Superheroes)",
        "description": "Thor: " + name,
        "emote": {
            "beginTick": 0,
            "endTick": end,
            "stopTick": 100000 if loop else end + stop_extra,
            "isLoop": loop,
            "returnTick": return_tick,
            "degrees": True,
            "easeBeforeKeyframe": True,
            "moves": moves,
        },
    }
    with open(os.path.join(OUT, name + ".json"), "w") as f:
        json.dump(data, f, indent=1)


REST_POSE = {}
S = "easeInOutSine"
L = "linear"

# The hammer raised to the sky, the bolt comes down on it (tick 10), then lowered.
RAISED = {"rightArm": {"p": -172.0, "r": 8.0}, "leftArm": {"p": -15.0, "r": -20.0}, "head": {"p": -30.0},
          "torso": {"p": -4.0}}
emote("thor_lightning_strike", [
    (0, S, REST_POSE),
    (6, S, RAISED),
    (10, L, {**RAISED, "rightArm": {"p": -176.0, "r": 4.0}}),
    (13, S, {**RAISED, "leftArm": {"p": -20.0, "r": -35.0}}),
    (22, S, RAISED),
    (30, S, REST_POSE),
])

# Hammer Spin (the ability): the arm forward, the hammer whirls in front (the item turns by itself).
SPIN_A = {"rightArm": {"p": -88.0, "y": -8.0, "r": 4.0}, "leftArm": {"p": -12.0, "r": -10.0}}
SPIN_B = {"rightArm": {"p": -92.0, "y": -12.0, "r": -4.0}, "leftArm": {"p": -12.0, "r": -10.0}}
emote("thor_hammer_spin", [(0, S, REST_POSE), (4, S, SPIN_A), (9, S, SPIN_B), (14, S, SPIN_A)], loop=True,
      return_tick=4)

# Lightning Blast (held): the hammer pointed where he looks, the free hand back.
BLAST = {"rightArm": {"p": -90.0, "y": -6.0}, "leftArm": {"p": 20.0, "r": -14.0}, "torso": {"y": -10.0}}
emote("thor_lightning_blast", [(0, S, REST_POSE), (4, S, BLAST), (12, S, BLAST)], loop=True, return_tick=4)

# Stormbreaker Block: the axe across the body, the other hand on the haft.
BLOCK = {"rightArm": {"p": -78.0, "y": -38.0, "r": 10.0}, "leftArm": {"p": -66.0, "y": 34.0},
         "torso": {"p": 6.0}, "rightLeg": {"p": 8.0}, "leftLeg": {"p": -10.0, "dz": -1.0}}
emote("thor_block", [(0, S, REST_POSE), (3, S, BLOCK), (10, S, BLOCK)], loop=True, return_tick=3)

# Storm Striker: the coil, the hammer up with both hands in the air (looped while airborne)...
STRIKER_AIR = {"rightArm": {"p": -165.0, "y": -10.0, "r": 16.0}, "leftArm": {"p": -160.0, "y": 14.0, "r": -14.0},
               "head": {"p": 10.0}, "torso": {"p": -6.0},
               "rightLeg": {"p": -40.0, "dy": -1.0, "dz": -1.5}, "leftLeg": {"p": 12.0}}
emote("thor_striker_air", [(0, S, {"rightArm": {"p": -40.0}, "leftArm": {"p": -40.0}, "torso": {"p": 18.0},
                                   "rightLeg": {"p": -30.0, "dz": -2.0}, "leftLeg": {"p": -30.0, "dz": -2.0}}),
                           (5, S, STRIKER_AIR), (12, S, {**STRIKER_AIR, "rightLeg": {"p": -30.0, "dz": -1.0}}),
                           (19, S, STRIKER_AIR)], loop=True, return_tick=5)
# ... and the slam on the ground: down on one knee, the hammer on the ground in front.
SLAM = {"rightArm": {"p": -62.0, "y": -10.0}, "leftArm": {"p": -58.0, "y": 12.0}, "head": {"p": 18.0},
        "torso": {"p": 30.0, "dz": -1.0}, "rightLeg": {"p": -78.0, "dy": -1.0, "dz": -2.0},
        "leftLeg": {"p": 34.0, "dy": -2.0, "dz": 1.5}}
emote("thor_striker_slam", [(0, L, STRIKER_AIR), (3, "easeInCubic", SLAM), (16, S, SLAM), (24, S, REST_POSE)])

# Hammer Spark: the hammer from above on the head of the target (in the air).
SMASH_UP = {"rightArm": {"p": -175.0, "r": 10.0}, "leftArm": {"p": -40.0, "r": -40.0}, "torso": {"p": -10.0},
            "rightLeg": {"p": -35.0, "dz": -1.5}, "leftLeg": {"p": 20.0}}
SMASH_DOWN = {"rightArm": {"p": -40.0, "r": 6.0}, "leftArm": {"p": -70.0, "r": -30.0}, "torso": {"p": 24.0},
              "head": {"p": 20.0}, "rightLeg": {"p": -50.0, "dz": -2.0}, "leftLeg": {"p": -10.0}}
emote("thor_spark_smash", [(0, S, REST_POSE), (4, S, SMASH_UP), (7, "easeInCubic", SMASH_DOWN), (14, S, SMASH_DOWN),
                           (22, S, REST_POSE)])

# Lightning Vortex: leaning into the dash, the hammer whirling ahead, the body turned.
VORTEX = {"rightArm": {"p": -100.0, "y": -14.0}, "leftArm": {"p": 30.0, "r": -26.0}, "torso": {"p": 20.0, "y": -14.0},
          "head": {"p": -14.0}, "rightLeg": {"p": 26.0}, "leftLeg": {"p": -34.0, "dz": -1.0}}
emote("thor_vortex", [(0, S, REST_POSE), (3, S, VORTEX), (10, S, {**VORTEX, "torso": {"p": 22.0, "y": 10.0}}),
                      (17, S, VORTEX)], loop=True, return_tick=3)

# God Blast: the hammer raised gathering (20 ticks), then pointed forward with both hands for the beam.
GB_UP = {"rightArm": {"p": -170.0, "r": 6.0}, "leftArm": {"p": -170.0, "r": -6.0}, "head": {"p": -25.0}}
GB_FWD = {"rightArm": {"p": -90.0, "y": -12.0}, "leftArm": {"p": -88.0, "y": 22.0}, "torso": {"p": 6.0},
          "rightLeg": {"p": 16.0}, "leftLeg": {"p": -16.0, "dz": -1.0}}
emote("thor_god_blast", [(0, S, REST_POSE), (8, S, GB_UP), (20, L, GB_UP), (24, "easeOutQuad", GB_FWD),
                         (42, S, GB_FWD), (50, S, REST_POSE)])

# Weapon of a King: the axe raised behind, the free hand aims at the target; at tick 60 the throw.
WK_AIM = {"rightArm": {"p": -165.0, "y": 10.0, "r": 20.0}, "leftArm": {"p": -90.0, "y": 16.0}, "torso": {"y": -16.0},
          "head": {"p": -6.0}, "rightLeg": {"p": 10.0}, "leftLeg": {"p": -12.0}}
WK_THROW = {"rightArm": {"p": -40.0, "y": -20.0}, "leftArm": {"p": 20.0, "r": -24.0}, "torso": {"p": 16.0, "y": 14.0},
            "rightLeg": {"p": -20.0}, "leftLeg": {"p": 18.0}}
emote("thor_weapon_of_a_king", [(0, S, REST_POSE), (8, S, WK_AIM), (58, L, WK_AIM), (62, "easeOutQuad", WK_THROW),
                                (72, S, WK_THROW), (80, S, REST_POSE)])

# Cosmic Judgement: the hammer whirling over the head, then slammed down.
CJ_UP = {"rightArm": {"p": -176.0, "r": 4.0}, "leftArm": {"p": -30.0, "r": -38.0}, "head": {"p": -22.0}}
CJ_DOWN = {"rightArm": {"p": -55.0}, "leftArm": {"p": -20.0, "r": -40.0}, "torso": {"p": 26.0},
           "rightLeg": {"p": -40.0, "dz": -1.5}, "leftLeg": {"p": 22.0}}
emote("thor_cosmic_judgement", [(0, S, REST_POSE), (5, S, CJ_UP), (30, L, CJ_UP), (33, "easeInCubic", CJ_DOWN),
                                (42, S, CJ_DOWN), (50, S, REST_POSE)])

# Devourer King: floating with the arms open to the storm (looped for the whole domain).
DK = {"rightArm": {"p": -140.0, "r": 42.0}, "leftArm": {"p": -140.0, "r": -42.0}, "head": {"p": -18.0},
      "rightLeg": {"p": 8.0, "r": 4.0}, "leftLeg": {"p": -6.0, "r": -4.0}}
emote("thor_devourer_king", [(0, S, REST_POSE), (10, S, DK), (30, S, {**DK, "head": {"p": -26.0}}), (50, S, DK)],
      loop=True, return_tick=10)

# Stormbreaker Storm Striker: the axe up with both hands, slammed into the ground.
SB_UP = {"rightArm": {"p": -168.0, "r": 10.0}, "leftArm": {"p": -168.0, "r": -10.0}, "torso": {"p": -8.0},
         "head": {"p": -10.0}}
SB_DOWN = {"rightArm": {"p": -50.0, "y": -8.0}, "leftArm": {"p": -50.0, "y": 8.0}, "torso": {"p": 34.0, "dz": -1.0},
           "head": {"p": 20.0}, "rightLeg": {"p": -40.0, "dz": -1.5}, "leftLeg": {"p": 22.0, "dz": 1.0}}
emote("thor_stormbreaker_slam", [(0, S, REST_POSE), (6, S, SB_UP), (10, "easeInCubic", SB_DOWN), (22, S, SB_DOWN),
                                 (30, S, REST_POSE)])

# Lex Luthor's suit: the shoulder pods fire (both arms forward, a recoil), and the beam from the palm (held).
LEX_AIM = {"rightArm": {"p": -90.0, "y": -6.0}, "leftArm": {"p": -90.0, "y": 6.0}, "torso": {"p": 4.0}}
LEX_KICK = {"rightArm": {"p": -105.0, "y": -6.0}, "leftArm": {"p": -105.0, "y": 6.0}, "torso": {"p": -6.0},
            "head": {"p": -6.0}}
emote("lex_missile_fire", [(0, S, REST_POSE), (4, S, LEX_AIM), (6, "easeOutQuad", LEX_KICK), (12, S, LEX_AIM),
                           (20, S, REST_POSE)])
emote("lex_beam", [(0, S, REST_POSE), (4, S, LEX_AIM), (12, S, {**LEX_AIM, "torso": {"p": 6.0}})], loop=True,
      return_tick=4)
# repulsor blast: the right palm out, a kick back
REP = {"rightArm": {"p": -92.0, "y": -4.0}, "leftArm": {"p": -10.0, "r": -8.0}}
emote("lex_repulsor", [(0, S, REST_POSE), (3, S, REP), (5, "easeOutQuad", {**REP, "rightArm": {"p": -108.0, "y": -4.0}}),
                       (10, S, REP), (16, S, REST_POSE)])
# unibeam: arms back, chest out (looped while it fires)
UNI = {"rightArm": {"p": 30.0, "r": 30.0}, "leftArm": {"p": 30.0, "r": -30.0}, "torso": {"p": -12.0},
       "head": {"p": -8.0}, "rightLeg": {"p": 10.0}, "leftLeg": {"p": -14.0, "dz": -1.0}}
emote("lex_unibeam", [(0, S, REST_POSE), (8, S, UNI), (20, S, {**UNI, "torso": {"p": -14.0}})], loop=True,
      return_tick=8)
# wrist lasers: both arms out to the sides at shoulder height, sweeping in
LAS_A = {"rightArm": {"p": -90.0, "y": 60.0}, "leftArm": {"p": -90.0, "y": -60.0}}
LAS_B = {"rightArm": {"p": -90.0, "y": -40.0}, "leftArm": {"p": -90.0, "y": 40.0}}
emote("lex_lasers", [(0, S, REST_POSE), (3, S, LAS_A), (13, L, LAS_B), (20, S, REST_POSE)])
# power thrust: arms back, body leaning into it
THR = {"rightArm": {"p": 40.0, "r": 12.0}, "leftArm": {"p": 40.0, "r": -12.0}, "torso": {"p": 20.0},
       "rightLeg": {"p": 20.0}, "leftLeg": {"p": 14.0}}
emote("lex_thrust", [(0, S, REST_POSE), (3, S, THR), (14, S, THR), (22, S, REST_POSE)])
# power dive: fist down, head first
DIVE = {"rightArm": {"p": -170.0}, "leftArm": {"p": -170.0}, "head": {"p": 20.0}, "rightLeg": {"p": 10.0},
        "leftLeg": {"p": -6.0}}
emote("lex_dive", [(0, S, REST_POSE), (4, S, DIVE), (12, S, DIVE)], loop=True, return_tick=4)
# the landing of the dive: one knee and a fist on the ground
LAND = {"rightArm": {"p": -20.0, "dz": -1.0}, "leftArm": {"p": -60.0, "r": -20.0}, "torso": {"p": 28.0},
        "head": {"p": 12.0}, "rightLeg": {"p": -80.0, "dy": -1.0, "dz": -2.0}, "leftLeg": {"p": 30.0, "dy": -2.0,
                                                                                         "dz": 1.5}}
emote("lex_land", [(0, L, DIVE), (3, "easeInCubic", LAND), (18, S, LAND), (28, S, REST_POSE)])

print("ok")
