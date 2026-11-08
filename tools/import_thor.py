"""Copies the Thor pack (Legends: Superheroes) into the mod and converts its player animations.

usage: python3 tools/import_thor.py <thor_completo dir> <mod>/src/main/resources/assets/flash
"""
import json
import os
import shutil
import sys

src, dst = sys.argv[1], sys.argv[2]

# suits: model + textures
suits = sorted(f[:-9] for f in os.listdir(os.path.join(src, "trajes/modelos_3d")) if f.endswith(".geo.json"))
os.makedirs(os.path.join(dst, "geo/thor"), exist_ok=True)
for s in suits:
    shutil.copy(os.path.join(src, "trajes/modelos_3d", s + ".geo.json"), os.path.join(dst, "geo/thor", s + ".geo.json"))
    tdir = os.path.join(src, "trajes/texturas", s)
    out = os.path.join(dst, "textures/thor_suits", s)
    os.makedirs(out, exist_ok=True)
    for name, target in (("layer_0.png", "layer_0.png"), ("layer_1.png", "layer_1.png"),
                         ("layer_0_helmetless.png", "layer_0_helmetless.png"), ("eyes_3.png", "eyes.png"),
                         ("cape.png", "cape.png")):
        p = os.path.join(tdir, name)
        if os.path.exists(p):
            shutil.copy(p, os.path.join(out, target))

# weapons
os.makedirs(os.path.join(dst, "textures/thor"), exist_ok=True)
for n in ("mjolnir", "mjolnirmcu", "stormbreaker"):
    shutil.copy(os.path.join(src, "armas/modelos_3d", n + ".geo.json"), os.path.join(dst, "geo/thor", n + ".geo.json"))
for f in os.listdir(os.path.join(src, "armas/texturas")):
    shutil.copy(os.path.join(src, "armas/texturas", f), os.path.join(dst, "textures/thor", f))

# sounds
sd = os.path.join(dst, "sounds/thor")
os.makedirs(sd, exist_ok=True)
for f in os.listdir(os.path.join(src, "sons")):
    if f.endswith(".ogg"):
        shutil.copy(os.path.join(src, "sons", f), os.path.join(sd, f))
pack = json.load(open(os.path.join(src, "sons/sounds_trecho.json")))
sj_path = os.path.join(dst, "sounds.json")
sj = json.load(open(sj_path))
for k, v in pack.items():
    sounds = []
    for s in v["sounds"]:
        name = s["name"] if isinstance(s, dict) else s
        name = name.replace("legends_superheroes:", "flash:thor/")
        sounds.append({"name": name, "stream": False} if isinstance(s, dict) else name)
    sj["thor." + k] = {"category": "player", "sounds": sounds, "subtitle": "subtitles.flash.thor"}
json.dump(sj, open(sj_path, "w"), indent=2)
open(sj_path, "a").write("\n")

# animations: Bedrock (Blockbench) -> playerAnimator emote (degrees)
BONES = {"head": "head", "torso": "torso", "body": "body", "right_arm": "rightArm", "left_arm": "leftArm",
         "right_leg": "rightLeg", "left_leg": "leftLeg"}
anim = json.load(open(os.path.join(src, "animacoes_do_jogador/player_thor.animation.json")))
os.makedirs(os.path.join(dst, "player_animation"), exist_ok=True)


def frames(channel):
    if channel is None:
        return {}
    if isinstance(channel, list):
        return {0.0: channel}
    out = {}
    for t, v in channel.items():
        vec = v["vector"] if isinstance(v, dict) else v
        if isinstance(v, dict) and "post" in v:
            vec = v["post"] if isinstance(v["post"], list) else v["post"]["vector"]
        out[float(t)] = [float(x) if not isinstance(x, str) else 0.0 for x in vec]
    return out


for full, a in anim["animations"].items():
    name = "thor_" + full.split(".")[-1]
    length = float(a.get("animation_length", 1.0))
    ticks = {}
    for bone, ch in a.get("bones", {}).items():
        part = BONES.get(bone)
        if part is None:
            continue
        for t, (x, y, z) in frames(ch.get("rotation")).items():
            k = ticks.setdefault(round(t * 20), {})
            # Bedrock -> Java model space: x and y mirrored
            k.setdefault(part, {}).update({"pitch": -x, "yaw": -y, "roll": z})
        for t, (x, y, z) in frames(ch.get("position")).items():
            k = ticks.setdefault(round(t * 20), {})
            if part == "body":
                k.setdefault(part, {}).update({"x": -x / 16.0, "y": y / 16.0, "z": z / 16.0})
            else:
                k.setdefault(part, {}).update({"x": -x, "y": -y, "z": z})
    moves = []
    for t in sorted(ticks):
        m = {"tick": t, "easing": "linear"}
        m.update(ticks[t])
        moves.append(m)
    end = max(1, round(length * 20))
    emote = {"version": 3, "name": name, "author": "Legends: Superheroes",
             "description": "Thor: " + full,
             "emote": {"beginTick": 0, "endTick": end, "stopTick": end + 6, "isLoop": False, "returnTick": 0,
                       "degrees": True, "easeBeforeKeyframe": True, "moves": moves}}
    json.dump(emote, open(os.path.join(dst, "player_animation", name + ".json"), "w"), indent=1)
print(len(suits), "suits;", len(anim["animations"]), "animations")
print(" ".join(suits))
