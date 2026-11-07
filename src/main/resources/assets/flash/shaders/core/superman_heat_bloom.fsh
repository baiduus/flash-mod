#version 150

// Fix42 (laser workspace override): vertices with blue < .75 are a Laser Blast's bloom (HeatGlow23.captureBlast, and the
// Uplift / finisher bloom ribbons; blue - .5 = eye x .1). Its white light near the core keeps a tenth of the travelling
// brightness waves (the white core stays steady), while its red shoulder and skirt shake harder and on their own: width
// pulsing (2.5x the old ribbon wobble and ripple), a noisy edge running along the beam, the waves at 2.5x and a
// flicker. Every term keeps its old mean. Normal heat vision (blue 1) keeps the original bloom below exactly.

in vec4 vertexColor;
in vec2 beamCoord;
uniform float Phase;
out vec4 fragColor;

float hash(vec2 p) { return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453); }
float noise(vec2 p) {
    vec2 cell = floor(p), f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(cell), hash(cell + vec2(1, 0)), f.x), mix(hash(cell + vec2(0, 1)), hash(cell + vec2(1, 1)), f.x), f.y);
}

void main() {
    float radial = abs(beamCoord.y * 2.0 - 1.0);
    float phase = Phase;
    float edge = 1.0 - smoothstep(0.68, 1.0, radial);
    float root = smoothstep(0.015, 0.14, beamCoord.x);
    float waves = 0.04 * sin(beamCoord.x * 7.0 - phase * 13.0) + 0.02 * sin(beamCoord.x * 18.0 - phase * 29.0);
    vec3 radiance;
    if (vertexColor.b < 0.75) {
        float eyePh = (vertexColor.b - 0.5) * 17.0;
        float pulse = 0.0875 * sin(phase * 37.0 + eyePh) + 0.0375 * sin(beamCoord.x * 3.1 - phase * 24.0 + eyePh * 0.18);
        float edgeN = noise(vec2(beamCoord.x * 2.2 - phase * 31.0, 7.3 + eyePh * 3.0)) - 0.5;
        float flick = noise(vec2(phase * 23.0 + eyePh * 4.1, 3.7)) - 0.5;
        // the red parts read a radius scaled by the pulse and the edge noise: their skirt breathes in and out
        float rr = radial / (1.0 + pulse + 0.14 * edgeN);
        float white = exp(-radial * radial * 29.0) * 0.24;
        float warm = exp(-rr * rr * 9.0) * 0.13;
        float scarlet = exp(-rr * rr * 4.5) * 0.065;
        float whiteFlow = 0.94 + 0.1 * waves;
        float redFlow = 0.94 + 2.5 * waves + 0.12 * flick;
        radiance = vec3(1.0, 0.985, 0.98) * white * whiteFlow
                 + (vec3(1.0, 0.045, 0.065) * warm + vec3(1.0, 0.008, 0.025) * scarlet) * redFlow;
    } else {
        // A white-hot filament with a scarlet shoulder and soft red outer skirt.
        float white = exp(-radial * radial * 29.0) * 0.24;
        float warm = exp(-radial * radial * 9.0) * 0.13;
        float scarlet = exp(-radial * radial * 4.5) * 0.065;
        float flow = 0.94 + waves;
        radiance = (vec3(1.0, 0.985, 0.98) * white
                  + vec3(1.0, 0.045, 0.065) * warm
                  + vec3(1.0, 0.008, 0.025) * scarlet) * flow;
    }
    // SRC_ALPHA + ONE contributes light without painting over the background.
    // COLOR_WRITE and LEQUAL preserve world depth and never draw through a wall.
    fragColor = vec4(radiance, vertexColor.a * edge * root);
}
