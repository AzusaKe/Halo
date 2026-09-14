#version 150

#moj_import <light.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in vec3 Normal;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform mat3 NormalMat;
uniform sampler2D Sampler2;
uniform ivec2 LightCoord;
uniform vec3 Light0_Direction;
uniform vec3 Light1_Direction;

out vec2 texCoord0;
out vec4 vertexColor;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    texCoord0 = UV0;
    vec3 transformedNormal = normalize(NormalMat * Normal);
    vertexColor = minecraft_mix_light(Light0_Direction, Light1_Direction, transformedNormal, Color)
        * texelFetch(Sampler2, LightCoord / 16, 0);
}
