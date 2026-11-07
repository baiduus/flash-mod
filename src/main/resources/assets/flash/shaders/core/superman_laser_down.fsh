#version 150

// Fix42 Laser Blast bloom: dual-filter downsample (centre x4 + 4 diagonal bilinear taps one source texel out).

uniform sampler2D Src;
uniform vec2 SrcTexel;

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec2 o = SrcTexel;
    vec3 c = texture(Src, texCoord).rgb * 4.0
        + texture(Src, texCoord + vec2(-o.x, -o.y)).rgb
        + texture(Src, texCoord + vec2(o.x, -o.y)).rgb
        + texture(Src, texCoord + vec2(-o.x, o.y)).rgb
        + texture(Src, texCoord + vec2(o.x, o.y)).rgb;
    fragColor = vec4(c * 0.125, 1.0);
}
