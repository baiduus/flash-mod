#version 150

// Fix42 Laser Blast bloom, last step: the scene (a copy of the main colour) plus the blurred beam light as a soft crimson
// glow, screen-blended so it never clips and never whitens or widens the beam's core, kept inside a perspective-correct
// envelope that hugs each beam (about 1.3x the beam's visible width); plus the first-person edge vignette.
// Written without blending: pixels away from the beams come out exactly as they went in.

uniform sampler2D SceneSampler;
uniform sampler2D BloomSampler;
// x: strength, y: first-person vignette alpha, z: dither seed, w: path count
uniform vec4 CompositeParams;

// same layout as laserfx42_extract
uniform vec4 Paths[64];

out vec4 fragColor;

const vec3 VIGNETTE = vec3(0.14, 0.0, 0.0);

float fall(float inner, float outer, float x) {
    return 1.0 - smoothstep(inner, outer, x);
}

float envelope(vec2 p, float H) {
    float m = 0.0;
    int n = int(CompositeParams.w + 0.5);
    for (int i = 0; i < 16; ++i) {
        if (i >= n) {
            break;
        }
        vec4 a = Paths[i * 4];
        vec4 b = Paths[i * 4 + 1];
        vec4 c = Paths[i * 4 + 2];
        vec4 e = Paths[i * 4 + 3];
        float fp = b.w;
        vec2 d = a.zw - a.xy;
        float t = clamp(dot(p - a.xy, d) / max(dot(d, d), 1.0e-3), 0.0, 1.0);
        float dist = length(p - (a.xy + d * t));
        // envelope radius: 1.3x the beam's visible half width, so the glow can never make the beam look wider
        float r = clamp(clamp(e.w * 1.3, 0.2, 0.8) * b.z * mix(b.x, b.y, t), 3.0, H * mix(0.09, 0.03, fp));
        float w = fall(r * 0.35, r, dist);
        if (c.z > 0.0) {
            float ri = clamp(e.w * 2.6 * b.z * c.z, 5.0, H * mix(0.16, 0.06, fp));
            w = max(w, fall(ri * 0.3, ri, length(p - c.xy)));
        }
        if (e.z > 0.0) {
            float rs = clamp(e.w * 1.15 * b.z * e.z, 3.0, H * 0.07);
            w = max(w, fall(rs * 0.3, rs, length(p - e.xy)));
        }
        m = max(m, w);
    }
    return m;
}

float ign(vec2 p) {
    return fract(52.9829189 * fract(dot(p, vec2(0.06711056, 0.00583715))));
}

void main() {
    ivec2 ip = ivec2(gl_FragCoord.xy);
    vec4 base = texelFetch(SceneSampler, ip, 0);
    vec3 col = base.rgb;
    vec2 size = vec2(textureSize(SceneSampler, 0));
    vec2 uv = gl_FragCoord.xy / size;
    if (CompositeParams.y > 0.0) {
        float aspect = size.x / size.y;
        vec2 q = (uv - 0.5) * vec2(aspect, 1.0);
        float r = length(q) / length(vec2(0.5 * aspect, 0.5));
        float v = CompositeParams.y * smoothstep(0.6, 1.0, r);
        col = mix(col, VIGNETTE, v);
    }
    float env = envelope(gl_FragCoord.xy, size.y);
    if (env > 0.0) {
        vec3 b = texture(BloomSampler, uv).rgb * (CompositeParams.x * env);
        float e = dot(b, vec3(0.6, 0.3, 0.1));
        // tinted by intensity, like the beam: bright red near the core, deep dark crimson at the fringe. The red is
        // screen-added (never clips, the white core stays as it is); green and blue only rise up to the tint's own level,
        // so the glow can never turn pink or orange
        float g = 1.0 - exp(-1.8 * e);
        vec3 tint = mix(vec3(80.0, 0.0, 0.0), vec3(255.0, 75.0, 60.0), pow(g, 0.7)) / 255.0;
        vec3 glow = tint * g;
        col.r += glow.r * (1.0 - col.r);
        col.gb = max(col.gb, min(col.gb + glow.gb * (vec2(1.0) - col.gb), tint.gb));
        if (g > 0.004) {
            col += vec3((ign(gl_FragCoord.xy + CompositeParams.z) - 0.5) / 255.0);
        }
    }
    fragColor = vec4(col, base.a);
}
