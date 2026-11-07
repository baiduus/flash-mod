#version 150

// Fix42 Laser Blast: red light on the scene + heat haze, one full-screen pass before the beams are drawn.
// The scene colour is a copy of the main target; the depth is the scene depth (main, or translucent under Fabulous).

uniform sampler2D SceneSampler;
uniform sampler2D DepthSampler;

uniform mat4 InvProjView;
uniform vec2 ScreenSize;
uniform float Time;
// x: light strength on the albedo, y: albedo gain (scene colour -> albedo), z: additive glow, w: darkness 0..1
uniform vec4 LightParams;
// x: how much the beam's light swamps the ambient's green and blue (the "scene x red tint" part)
uniform vec4 LightParams2;
// x: haze amplitude in px at 1080p, y: sky haze strength, z: light count, w: first-person path count
uniform vec4 FxParams;

// per light: (start.xyz camera-relative, intensity), (end.xyz, core radius), (line scale, impact scale, first person, haze radius)
uniform vec4 Lights[24];
// per first-person path: (x0, y0, x1, y1) px, (band at start px, band at end px, intensity, 0)
uniform vec4 FpPaths[8];

out vec4 fragColor;

const vec3 BEAM_LIGHT = vec3(1.0, 0.06, 0.035);
const vec3 IMPACT_LIGHT = vec3(1.0, 0.12, 0.05);
const vec3 SKY_HAZE = vec3(0.55, 0.0, 0.004);
const float KNEE = 1.2;
const float RANGE = 5.5;
const float IMPACT_KNEE = 0.85;
const float IMPACT_RANGE = 4.5;

float fall(float inner, float outer, float x) {
    return 1.0 - smoothstep(inner, outer, x);
}

vec3 worldAt(vec2 uv, float depth) {
    vec4 p = InvProjView * vec4(uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    return p.xyz / p.w;
}

float window(float x) {
    float t = clamp(1.0 - x * x, 0.0, 1.0);
    return t * t;
}

float hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

float vnoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash12(i), hash12(i + vec2(1.0, 0.0)), u.x), mix(hash12(i + vec2(0.0, 1.0)), hash12(i + vec2(1.0, 1.0)), u.x), u.y);
}

// hot-air refraction offset (about +-1), rising and boiling; s = resolution scale (height / 1080)
vec2 shimmer(vec2 px, float s) {
    vec2 p = px / (13.0 * s);
    vec2 q1 = p + vec2(0.0, -Time * 2.4);
    vec2 q2 = p * 2.1 + vec2(Time * 0.4, -Time * 4.6);
    float nx = (vnoise(q1) - 0.5) + 0.55 * (vnoise(q2 + vec2(31.7, 11.3)) - 0.5);
    float ny = (vnoise(q1 + vec2(17.1, 5.9)) - 0.5) + 0.55 * (vnoise(q2 + vec2(7.3, 23.9)) - 0.5);
    return vec2(nx, ny) * 1.3;
}

// closest approach of the segments [p1,q1] and [p2,q2]: (distance, parameter on the first, parameter on the second)
vec3 segSeg(vec3 p1, vec3 q1, vec3 p2, vec3 q2) {
    vec3 d1 = q1 - p1;
    vec3 d2 = q2 - p2;
    vec3 r = p1 - p2;
    float a = dot(d1, d1);
    float e = dot(d2, d2);
    float f = dot(d2, r);
    float s = 0.0;
    float t = 0.0;
    if (a <= 1.0e-6 && e <= 1.0e-6) {
        s = 0.0;
        t = 0.0;
    } else if (a <= 1.0e-6) {
        t = clamp(f / e, 0.0, 1.0);
    } else {
        float c = dot(d1, r);
        if (e <= 1.0e-6) {
            s = clamp(-c / a, 0.0, 1.0);
        } else {
            float b = dot(d1, d2);
            float denom = a * e - b * b;
            s = denom > 1.0e-6 * a * e ? clamp((b * f - c * e) / denom, 0.0, 1.0) : 0.0;
            t = (b * s + f) / e;
            if (t < 0.0) {
                t = 0.0;
                s = clamp(-c / a, 0.0, 1.0);
            } else if (t > 1.0) {
                t = 1.0;
                s = clamp((b - c) / a, 0.0, 1.0);
            }
        }
    }
    return vec3(length(p1 + d1 * s - (p2 + d2 * t)), s, t);
}

// surface normal from the depth of the 4 neighbours (the side with the smaller depth step, so edges stay clean)
vec3 normalAt(ivec2 ip, float depth, vec3 P, vec2 uv) {
    ivec2 hi = textureSize(DepthSampler, 0) - 1;
    float dl = texelFetch(DepthSampler, clamp(ip - ivec2(1, 0), ivec2(0), hi), 0).r;
    float dr = texelFetch(DepthSampler, clamp(ip + ivec2(1, 0), ivec2(0), hi), 0).r;
    float dd = texelFetch(DepthSampler, clamp(ip - ivec2(0, 1), ivec2(0), hi), 0).r;
    float du = texelFetch(DepthSampler, clamp(ip + ivec2(0, 1), ivec2(0), hi), 0).r;
    vec2 px = 1.0 / ScreenSize;
    vec3 dx = abs(dr - depth) < abs(depth - dl) ? worldAt(uv + vec2(px.x, 0.0), dr) - P : P - worldAt(uv - vec2(px.x, 0.0), dl);
    vec3 dy = abs(du - depth) < abs(depth - dd) ? worldAt(uv + vec2(0.0, px.y), du) - P : P - worldAt(uv - vec2(0.0, px.y), dd);
    vec3 n = cross(dx, dy);
    float len = length(n);
    if (len < 1.0e-12) {
        return -normalize(P);
    }
    n /= len;
    return dot(n, P) > 0.0 ? -n : n;
}

void main() {
    ivec2 ip = ivec2(gl_FragCoord.xy);
    vec2 uv = gl_FragCoord.xy / ScreenSize;
    float depth = texelFetch(DepthSampler, ip, 0).r;
    bool sky = depth >= 1.0;
    vec3 P = worldAt(uv, sky ? 0.99999 : depth);
    float rayLen = length(P);
    int lightCount = int(FxParams.z + 0.5);
    int pathCount = int(FxParams.w + 0.5);
    float s = ScreenSize.y / 1080.0;

    // ---- heat haze: where the view ray passes close behind a beam, or over its impact
    float haze = 0.0;
    float hazeAt = 0.0;
    float skyGlow = 0.0;
    for (int i = 0; i < 8; ++i) {
        if (i >= lightCount) {
            break;
        }
        vec4 la = Lights[i * 3];
        vec4 lb = Lights[i * 3 + 1];
        vec4 lc = Lights[i * 3 + 2];
        if (lc.z < 0.5) {
            vec3 ss = segSeg(vec3(0.0), P, la.xyz, lb.xyz);
            float camD = ss.y * rayLen;
            float along = ss.z * length(lb.xyz - la.xyz);
            float w = la.w * fall(lc.w * 0.2, lc.w, ss.x) * smoothstep(0.8, 2.5, camD) * smoothstep(0.3, 1.5, along);
            if (w > haze) {
                haze = w;
                hazeAt = camD;
            }
            if (sky) {
                skyGlow += la.w * exp(-ss.x * ss.x / 0.12) * smoothstep(0.4, 1.5, along);
            }
        }
        vec3 c = lb.xyz + vec3(0.0, 0.35, 0.0);
        float tt = clamp(dot(c, P) / max(dot(P, P), 1.0e-4), 0.0, 1.0);
        vec3 q = P * tt - c;
        q.y *= 0.7;
        float camI = tt * rayLen;
        float wi = la.w * fall(0.2, 1.1, length(q)) * smoothstep(0.8, 2.5, camI);
        if (wi > haze) {
            haze = wi;
            hazeAt = camI;
        }
    }
    for (int i = 0; i < 4; ++i) {
        if (i >= pathCount) {
            break;
        }
        vec4 pa = FpPaths[i * 2];
        vec4 pb = FpPaths[i * 2 + 1];
        vec2 d = pa.zw - pa.xy;
        float t = clamp(dot(gl_FragCoord.xy - pa.xy, d) / max(dot(d, d), 1.0e-3), 0.0, 1.0);
        float dist = length(gl_FragCoord.xy - (pa.xy + d * t));
        float band = max(mix(pb.x, pb.y, t), 1.0);
        float w = pb.z * fall(band * 0.15, band, dist) * smoothstep(0.05, 0.3, t);
        if (w > haze) {
            haze = w;
            hazeAt = 0.0;
        }
    }

    vec4 base = texelFetch(SceneSampler, ip, 0);
    vec3 col = base.rgb;
    if (haze > 0.004) {
        vec2 off = shimmer(gl_FragCoord.xy, s) * (FxParams.x * s * min(haze, 1.0));
        vec2 uvR = (gl_FragCoord.xy + off) / ScreenSize;
        float dR = texture(DepthSampler, uvR).r;
        float distR = dR >= 1.0 ? 1.0e6 : length(worldAt(uvR, dR));
        // a sample in front of the hot air is a foreground edge: keep the pixel instead of smearing it
        if (distR > hazeAt - 0.25) {
            col = texture(SceneSampler, uvR).rgb;
        }
    }

    // ---- red light: a line light along every beam and a point light at every impact
    vec3 add = vec3(0.0);
    if (!sky && lightCount > 0) {
        float reach = 0.0;
        for (int i = 0; i < 8; ++i) {
            if (i >= lightCount) {
                break;
            }
            vec3 a = Lights[i * 3].xyz;
            vec3 b = Lights[i * 3 + 1].xyz;
            vec3 ab = b - a;
            float t = clamp(dot(P - a, ab) / max(dot(ab, ab), 1.0e-6), 0.0, 1.0);
            reach += step(length(a + ab * t - P), RANGE) + step(length(b - P), IMPACT_RANGE + 0.5);
        }
        if (reach > 0.0) {
            vec3 N = normalAt(ip, depth, P, uv);
            vec3 sum = vec3(0.0);
            for (int i = 0; i < 8; ++i) {
                if (i >= lightCount) {
                    break;
                }
                vec4 la = Lights[i * 3];
                vec4 lb = Lights[i * 3 + 1];
                vec4 lc = Lights[i * 3 + 2];
                vec3 ab = lb.xyz - la.xyz;
                float t = clamp(dot(P - la.xyz, ab) / max(dot(ab, ab), 1.0e-6), 0.0, 1.0);
                vec3 v = la.xyz + ab * t - P;
                float d = length(v);
                float lineFall = window(d / RANGE) / (1.0 + d * d / (KNEE * KNEE));
                float lam = clamp((dot(N, v / max(d, 1.0e-4)) + 0.3) / 1.3, 0.0, 1.0);
                lam = mix(lam, 1.0, fall(0.0, 0.5, d));
                sum += BEAM_LIGHT * (la.w * lc.x * lineFall * lam);
                // the impact's hot plasma sits in front of the surface it hits: light from a little way back up the beam,
                // so the wall around the hit is lit too (a light exactly on the wall grazes it)
                float len = length(ab);
                vec3 ipos = lb.xyz - ab * (min(0.45, len * 0.1) / max(len, 1.0e-4));
                vec3 vi = ipos - P;
                float di = length(vi);
                float pointFall = window(di / IMPACT_RANGE) / (1.0 + di * di / (IMPACT_KNEE * IMPACT_KNEE));
                float lami = clamp((dot(N, vi / max(di, 1.0e-4)) + 0.25) / 1.25, 0.0, 1.0);
                lami = mix(lami, 1.0, fall(0.0, 0.35, di));
                sum += IMPACT_LIGHT * (la.w * lc.y * pointFall * lami);
            }
            vec3 albedo = col * LightParams.y;
            add = albedo * sum * LightParams.x + sum * LightParams.z;
            // scene x red tint: the beam's light swamps the ambient's green and blue, like an exposure set for the laser;
            // lit grass reads crimson instead of orange
            float swamp = LightParams2.x * (1.0 - exp(-4.0 * sum.r));
            col *= vec3(1.0, 1.0 - swamp, 1.0 - swamp * 1.05);
        }
    }
    if (sky && skyGlow > 0.0) {
        add += SKY_HAZE * (min(skyGlow, 1.5) * FxParams.y);
    }
    // soft shoulder: adds light without clipping; zero light leaves the pixel exactly as it was
    vec3 room = max(vec3(1.0) - col, vec3(1.0e-4));
    vec3 outc = col + room * (vec3(1.0) - exp(-add / room));
    fragColor = vec4(outc, base.a);
}
