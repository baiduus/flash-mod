#version 150

// Fix42 Laser Blast bloom: dual-filter upsample (8-tap tent of the lower level) plus this level's own downsample.

uniform sampler2D Src;
uniform sampler2D Cur;
uniform vec2 SrcTexel;
// x: weight of the upsampled lower level, y: weight of this level
uniform vec2 Weights;

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec2 h = SrcTexel * 0.5;
    vec3 s = texture(Src, texCoord + vec2(-2.0 * h.x, 0.0)).rgb
        + texture(Src, texCoord + vec2(2.0 * h.x, 0.0)).rgb
        + texture(Src, texCoord + vec2(0.0, -2.0 * h.y)).rgb
        + texture(Src, texCoord + vec2(0.0, 2.0 * h.y)).rgb
        + (texture(Src, texCoord + vec2(-h.x, h.y)).rgb
        + texture(Src, texCoord + vec2(h.x, h.y)).rgb
        + texture(Src, texCoord + vec2(h.x, -h.y)).rgb
        + texture(Src, texCoord + vec2(-h.x, -h.y)).rgb) * 2.0;
    fragColor = vec4(s * (Weights.x / 12.0) + texture(Cur, texCoord).rgb * Weights.y, 1.0);
}
