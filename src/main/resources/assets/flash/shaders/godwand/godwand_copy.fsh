#version 150

uniform sampler2D DiffuseSampler;
uniform vec2 OutSize;
uniform vec2 InSize;

in vec2 texCoord;
out vec4 fragColor;
void main() { fragColor = texture(DiffuseSampler, texCoord); }
