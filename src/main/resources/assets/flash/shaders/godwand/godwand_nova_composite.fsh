#version 150
uniform float BloomStrength;


uniform sampler2D DiffuseSampler;
uniform sampler2D BloomSampler;
uniform vec2 OutSize;
uniform vec2 InSize;
uniform vec2 AuxiliarySize;

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec3 scene = texture(DiffuseSampler, texCoord).rgb;
    vec3 bloom = texture(BloomSampler, texCoord).rgb;
    vec3 color = 1.0 - (1.0 - scene) * exp(-bloom * BloomStrength);
    fragColor = vec4(color, 1.0);
}
