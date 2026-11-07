"""Builds playerAnimator emotes from the procedural poses of the Superman pack.

usage: python3 tools/gen_superman_poses.py <pack>/codigo_legivel <mod>/src/main/resources/assets/flash/player_animation

* super_combo.json  - Combo24Client$ComboPose: 35 keys over 135 ticks with the hit-stops (ground version)
* solar_bask.json   - Solar26Client$Frame.solar(t): arms open to the sun, breathing (loop)
* solar_vertical.json - Solar26Client$Frame.vertical(): flying straight up, fists overhead
Values are radians (degrees: false). Bends are left out (no bendy limbs in this mod).
"""
import json
import math
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
from pseudo_static import parse  # noqa: E402

src, dst = sys.argv[1], sys.argv[2]
combo = parse(os.path.join(src, "combo24", "Combo24Client$ComboPose.java"))


def mirror(k):
    return [k[4], -k[5], -k[6], k[7], k[0], -k[1], -k[2], k[3], k[8], -k[9], -k[10], k[11], k[14], k[15], k[12],
            k[13], k[16], k[17]]


names = {"PUNCH_L_LOAD": "PUNCH_LOAD", "PUNCH_L_HIT": "PUNCH_HIT", "PUNCH_L_FOLLOW": "PUNCH_FOLLOW"}
keys = []
for k in combo["KEYS"]:
    if isinstance(k, str):
        keys.append(mirror(combo[names[k]]))
    else:
        keys.append(k)
times = combo["TIMES"]
eases = [int(e) for e in combo["EASES"]]
beats = combo["HIT_STOP_BEATS"]
lengths = combo["HIT_STOP_LENGTH"]


def ease(kind, u):
    if kind == 1:
        return 1 - (1 - u) ** 4
    if kind == 2:
        return 1 - (1 - u) ** 3
    if kind == 3:
        return u ** 3
    if kind == 4:
        return u
    return u * u * (3 - 2 * u)


def pose_time(t):
    for b, h in zip(beats, lengths):
        if b <= t < b + h:
            return b + 0.12 * (t - b)
        if b + h <= t < b + h + 6:
            start = b + 0.12 * h
            return start + (t - (b + h)) * ((b + h + 6 - start) / 6)
    return t


def sample(t):
    if t <= times[0]:
        return keys[0]
    if t >= times[-1]:
        return keys[-1]
    i = 1
    while t >= times[i]:
        i += 1
    e = ease(eases[i], (t - times[i - 1]) / (times[i] - times[i - 1]))
    a, b = keys[i - 1], keys[i]
    return [a[k] + (b[k] - a[k]) * e for k in range(18)]


def move(tick, v):
    return {
        "tick": tick, "easing": "linear",
        "rightArm": {"pitch": v[0], "yaw": v[1], "roll": v[2]},
        "leftArm": {"pitch": v[4], "yaw": v[5], "roll": v[6]},
        "body": {"pitch": v[8], "yaw": v[9], "roll": v[10], "y": v[16]},
        "torso": {"pitch": v[8] * 0.12, "yaw": v[9] * 0.18, "roll": 0.0},
        "rightLeg": {"pitch": v[12], "yaw": 0.0, "roll": 0.06},
        "leftLeg": {"pitch": v[14], "yaw": 0.0, "roll": -0.06},
    }


def write(name, moves, end, loop, ret=0, desc=""):
    data = {"version": 3, "name": name, "author": "Superman Rise (converted)", "description": desc,
            "emote": {"beginTick": 0, "endTick": end, "stopTick": end + 6, "isLoop": loop, "returnTick": ret,
                      "degrees": False, "easeBeforeKeyframe": True, "moves": moves}}
    with open(os.path.join(dst, name + ".json"), "w") as f:
        json.dump(data, f, indent=1)


moves = [move(t, sample(pose_time(t))) for t in range(0, 136)]
write("super_combo", moves, 135, False, desc="Super Combo: approach, uppercut, two air punches, slam (hits at 12, 40, 69, 117)")


def solar(t):
    breath = math.sin(t * 0.08)
    sway = math.sin(t * 0.045 + 1.3)
    return {
        "body": {"pitch": -0.12 + 0.012 * breath, "y": 0.025 * breath},
        "head": {"pitch": -0.55 - 0.03 * breath},
        "rightArm": {"pitch": 0.22 + 0.02 * sway, "yaw": -0.35, "roll": 1.68 + 0.04 * breath},
        "leftArm": {"pitch": 0.22 - 0.02 * sway, "yaw": 0.35, "roll": -1.68 - 0.04 * breath},
        "rightLeg": {"pitch": 0.1 + 0.015 * sway, "roll": 0.05},
        "leftLeg": {"pitch": 0.05 - 0.015 * sway, "roll": -0.05},
    }


period = 2 * math.pi / 0.045  # one full sway (the breath is close to a whole number of cycles in it)
end = int(round(period))
moves = []
for t in range(0, end + 1, 2):
    m = {"tick": t, "easing": "linear"}
    m.update(solar(t))
    moves.append(m)
write("solar_bask", moves, end, True, desc="Basking in the sun high up: arms open, chest to the sky")

vertical = {"head": {"pitch": -0.48}, "rightArm": {"pitch": -2.8, "roll": 0.1}, "leftArm": {"pitch": -2.55, "roll": -0.1},
            "rightLeg": {"pitch": -0.08}, "leftLeg": {"pitch": -0.08}}
write("solar_vertical", [dict({"tick": 0, "easing": "linear"}, **vertical), dict({"tick": 20, "easing": "linear"}, **vertical)],
      20, True, desc="Flying straight up to the sun, fists overhead")
print("ok")
