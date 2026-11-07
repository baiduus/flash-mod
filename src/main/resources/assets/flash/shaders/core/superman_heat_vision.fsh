#version 150

// Original procedural light: a circular tube, a soft red corona, and eye/impact flares.
// Tube: Color.r = 0; Color.g selects red sheath (0) / white incandescent core (1).
// Corona: Color.r = 1; UV.x is distance in metres; UV.y spans the glow width.
// Color.b strengthens traveling detail for first person; r=g=1 identifies a flow streak.
// Flare: UV.x = -1-u, UV.y = v, where u/v each span 0..1.
// RGB is derived here. Vertex alpha controls the effect's strength.
uniform float Phase;

in vec4 vertexColor;
in vec2 beamCoord;

out vec4 fragColor;

const vec3 SCARLET = vec3(1.0, 0.006, 0.016);
const vec3 CORAL = vec3(1.0, 0.018, 0.030);
const vec3 INCANDESCENT = vec3(1.0, 1.0, 1.0);
const vec3 FIRST_RED = vec3(1.0, 0.009, 0.025);
const vec3 FIRST_HOT = vec3(1.0, 0.075, 0.095);

float gaussian(float radius, float sigma) {
    float q = radius / sigma;
    return exp(-q * q);
}

// Broaden a subpixel filament smoothly while preserving its integrated light.
float filteredGaussian(float radius, float sigma, float footprint) {
    float filteredSigma = sqrt(sigma * sigma + footprint * footprint / 12.0);
    return (sigma / filteredSigma) * gaussian(radius, filteredSigma);
}

float flowNoise(float x) {
    float cell = floor(x);
    float blend = fract(x);
    blend = blend * blend * (3.0 - 2.0 * blend);
    float a = fract(sin(cell * 127.1 + 31.7) * 43758.5453);
    float b = fract(sin((cell + 1.0) * 127.1 + 31.7) * 43758.5453);
    return mix(a, b, blend);
}

void main() {
    float strength = clamp(vertexColor.a, 0.0, 1.0);
    vec3 color;
    float alpha;

    if (beamCoord.x < 0.0) {
        vec2 p = vec2(-1.0 - beamCoord.x, beamCoord.y) * 2.0 - 1.0;
        float radius = length(p);
        float footprint = max(length(fwidth(p)), 0.0001);
        float halo = gaussian(radius, 0.61);
        float core = filteredGaussian(radius, 0.24, footprint);
        float ember = filteredGaussian(radius, 0.095, footprint);
        float edge = 1.0 - smoothstep(0.65, 1.0, radius);
        float pulse = 0.96 + 0.025 * sin(Phase * 19.0) + 0.015 * sin(Phase * 31.0);

        bool solar = vertexColor.r < 0.5;
        vec3 outer = solar ? vec3(1.0, 0.56, 0.055) : SCARLET;
        vec3 inner = solar ? vec3(1.0, 0.84, 0.24) : CORAL;
        vec3 hot = solar ? vec3(1.0, 0.88, 0.30) : INCANDESCENT;
        color = mix(outer, inner, core);
        color = mix(color, hot, clamp(ember * 1.08, 0.0, 1.0));
        alpha = (0.52 * halo + 0.80 * core + 1.0 * ember) * edge * pulse;
        if (solar && vertexColor.g > 0.5) {
            // A broad body-shaped luminous rim, not the tiny core of a point flare.
            // The silhouette occludes its center; soft translucent gold stays visible in daylight.
            float angle = atan(p.y, p.x);
            float flow = 0.5 + 0.5 * sin(angle * 4.0 - Phase * 2.4 + radius * 7.0);
            float rim = filteredGaussian(radius - 0.62, 0.21, footprint);
            float mantle = gaussian(radius, 0.86);
            color = mix(vec3(1.0, 0.57, 0.045), vec3(1.0, 0.85, 0.28), rim * 0.55 + flow * 0.18);
            alpha = (0.22 * mantle + rim * (0.48 + 0.10 * flow))
                  * (1.0 - smoothstep(0.80, 1.0, radius));
        }
    } else if (vertexColor.r < 0.5) {
        // The physical cylinder does not widen toward its endpoint. Its apparent fan shape
        // comes solely from perspective, retaining paired eyes and a clear center view.
        float core = vertexColor.g;
        float firstPerson = vertexColor.b;
        float travel = beamCoord.x - Phase * 3.4;
        float wave = 0.5 + 0.5 * sin(travel * 5.8);
        float packet = pow(wave, 4.0);
        float fine = flowNoise(travel * 5.0);
        float detail = 1.0 / (1.0 + fwidth(beamCoord.x) * 4.0);
        // Two genuinely separate layers: white light within a red sheath.
        float whiteMix = clamp(pow(core, 0.62) * 1.12, 0.0, 1.0);
        color = mix(mix(vec3(0.96, 0.0, 0.0), vec3(1.0, 0.05, 0.07), 0.32 + wave * 0.30),
                    INCANDESCENT, whiteMix);
        // A dedicated low-green/blue palette keeps first-person rays scarlet. Its
        // shader descriptor uses ordinary alpha blending over grass and sky.
        color = mix(color, mix(FIRST_RED, FIRST_HOT, core), firstPerson);
        float coreLight = mix(1.08 + 0.05 * wave, 0.34 + 0.38 * packet + 0.07 * fine, firstPerson);
        float sheathLight = mix(0.42 + detail * (0.18 * wave + 0.06 * fine),
                                0.08 + detail * (0.22 * packet + 0.06 * fine), firstPerson);
        alpha = mix(sheathLight, coreLight, core);
    } else if (vertexColor.g > 0.5) {
        float radius = abs(beamCoord.y * 2.0 - 1.0);
        float footprint = max(fwidth(beamCoord.y * 2.0), 0.0001);
        float glow = gaussian(radius, 0.64);
        float core = filteredGaussian(radius, 0.22, footprint);
        float trail = smoothstep(0.0, 0.62, beamCoord.x)
                    * (1.0 - smoothstep(0.72, 1.0, beamCoord.x));
        vec3 outer = vertexColor.b > 0.5 ? vec3(1.0, 0.71, 0.08) : FIRST_RED;
        vec3 hot = vertexColor.b > 0.5 ? vec3(1.0, 0.88, 0.30) : vec3(1.0, 1.0, 1.0);
        color = mix(outer, hot, core);
        alpha = (0.45 * glow + 0.90 * core) * trail;
    } else {
        float distance = beamCoord.x;
        float firstPerson = vertexColor.b;
        float across = beamCoord.y * 2.0 - 1.0;
        float travel = distance - Phase * 3.4;
        float wave = 0.5 + 0.5 * sin(travel * 5.8);
        float noise = flowNoise(travel * 3.0);
        float detail = 1.0 / (1.0 + fwidth(distance) * 3.0);
        // Smooth, tiny sideways drift gives a living filament, not a wavy hose.
        float drift = 0.018 * sin(travel * 8.5) + 0.009 * sin(travel * 18.0);
        float radius = abs(across - drift);
        float footprint = max(fwidth(across), 0.0001);
        float halo = gaussian(radius, 0.55 + 0.09 * wave);
        float core = filteredGaussian(radius, 0.28, footprint);
        float filament = filteredGaussian(radius, 0.10, footprint);
        float sideFilament = filteredGaussian(
            abs(across + 0.065 + 0.018 * sin(travel * 13.0)),
            0.027, footprint);
        // Fade against the unwarped edge so turbulence cannot reveal the quad.
        float edge = 1.0 - smoothstep(0.68, 1.0, abs(across));
        float pulse = mix(0.84 + 0.16 * wave, 0.64 + 0.36 * wave + 0.13 * noise, firstPerson);
        // Longitudinal red wisps animate around the white center without moving the aim axis.
        float wisp = gaussian(abs(across) - (0.43 + 0.07 * sin(travel * 4.4)), 0.075)
                   * pow(wave, 3.0) * detail;

        color = mix(vec3(0.92, 0.0, 0.0), vec3(1.0, 0.06, 0.08), clamp(core * 1.05 + 0.10, 0.0, 1.0));
        color = mix(color, INCANDESCENT, clamp(filament * 1.10, 0.0, 1.0));
        alpha = (0.52 * halo + 0.34 * core + 0.74 * filament
               + 0.16 * sideFilament + 0.12 * wisp * firstPerson) * edge * pulse;
        if (firstPerson > 0.5) {
            // Traveling streaks inhabit a translucent scarlet glow, with no hard quad
            // outline. Their brightness flows toward the target while its axis stays fixed.
            float packet = pow(wave, 3.0);
            float thread = filteredGaussian(radius, 0.075 + 0.04 * packet + 0.015 * noise, footprint);
            color = mix(FIRST_RED, FIRST_HOT, clamp(thread * 0.55 + wisp * 0.65, 0.0, 1.0));
            alpha = (halo * (0.16 + 0.12 * noise) + core * (0.13 + 0.16 * packet)
                   + thread * (0.23 + 0.22 * packet) + 0.30 * wisp + 0.12 * sideFilament)
                  * edge;
        }
    }

    // World/solar light uses additive blending; first-person rays use source-over.
    // Both materials write color without changing world depth.
    fragColor = vec4(color, clamp(alpha * strength, 0.0, 1.0));
}
