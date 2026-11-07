#version 150

// Fix42 Laser Blast bloom, step 1 (half resolution): the beam's bright pixels inside a mask around each projected beam.

uniform sampler2D MainSampler;
uniform vec2 SrcSize;
// x: path count, y: threshold low, z: threshold high
uniform vec4 BloomParams;

// per path: (x0, y0, x1, y1) px, (1/w at start, 1/w at end, focal px, first person),
//           (impact x, y, 1/w or 0, intensity), (eye x, y, 1/w or 0, visible half width of the beam in blocks)
uniform vec4 Paths[64];

in vec2 texCoord;
out vec4 fragColor;

float fall(float inner, float outer, float x) {
    return 1.0 - smoothstep(inner, outer, x);
}

float beamMask(vec2 p) {
    float m = 0.0;
    int n = int(BloomParams.x + 0.5);
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
        // the core and its red mantle, perspective-correct (1/w is linear along the projected beam)
        float r = clamp(clamp(e.w * 0.8, 0.1, 0.5) * b.z * mix(b.x, b.y, t), 2.5, SrcSize.y * mix(0.06, 0.025, fp));
        float w = fall(r * 0.6, r + 1.5, dist);
        if (c.z > 0.0) {
            float ri = clamp(e.w * 2.0 * b.z * c.z, 3.0, SrcSize.y * mix(0.12, 0.05, fp));
            w = max(w, fall(ri * 0.3, ri, length(p - c.xy)));
        }
        if (e.z > 0.0) {
            float rs = clamp(e.w * 0.7 * b.z * e.z, 2.0, SrcSize.y * 0.06);
            w = max(w, fall(rs * 0.3, rs, length(p - e.xy)));
        }
        m = max(m, w * c.w);
    }
    return m;
}

vec3 emissive(vec3 c) {
    float peak = max(c.r, max(c.g, c.b));
    float w = smoothstep(BloomParams.y, BloomParams.z, peak);
    // the beam is red or white-hot: keep those, drop green/blue highlights (sky, leaves, water)
    float hue = clamp((c.r - max(c.g, c.b) * 0.8) * 3.0, 0.0, 1.0);
    float white = smoothstep(0.82, 0.95, min(c.r, min(c.g, c.b)));
    return c * (w * max(hue, white));
}

void main() {
    vec2 p = texCoord * SrcSize;
    float m = beamMask(p);
    if (m <= 0.002) {
        fragColor = vec4(0.0, 0.0, 0.0, 1.0);
        return;
    }
    vec2 inv = 1.0 / SrcSize;
    vec3 c = emissive(texture(MainSampler, (p + vec2(-0.5, -0.5)) * inv).rgb)
        + emissive(texture(MainSampler, (p + vec2(0.5, -0.5)) * inv).rgb)
        + emissive(texture(MainSampler, (p + vec2(-0.5, 0.5)) * inv).rgb)
        + emissive(texture(MainSampler, (p + vec2(0.5, 0.5)) * inv).rgb);
    fragColor = vec4(c * (0.25 * m), 1.0);
}
