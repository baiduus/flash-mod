#version 150

// Fix42 laser screen effects: one triangle covering the screen (vertices -1,-1 / 3,-1 / -1,3).
in vec3 Position;

out vec2 texCoord;

void main() {
    gl_Position = vec4(Position.xy, 0.0, 1.0);
    texCoord = Position.xy * 0.5 + 0.5;
}
