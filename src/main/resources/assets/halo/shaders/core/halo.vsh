#version 330
#extension GL_ARB_separate_shader_objects : require
#include <minecraft:dynamictransforms.glsl>
#include <minecraft:projection.glsl>
#include <minecraft:light.glsl>
layout(location = 0) in vec3 Position;
layout(location = 1) in vec4 Color;
layout(location = 2) in vec2 UV0;
layout(location = 3) in ivec2 UV1;
layout(location = 4) in ivec2 UV2;
layout(location = 5) in vec3 Normal;
uniform sampler2D Sampler2;
layout(std140) uniform HaloMaterial { mat3 HaloNormal; vec4 HaloMaskParams; vec4 HaloOffsetLight; };
layout(location = 0) out vec2 texCoord0;
layout(location = 1) out vec4 vertexColor;
void main() {
 gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
 texCoord0 = UV0;
 vec4 color = Color;
#ifdef HALO_LIT
 color = minecraft_mix_light(Light0_Direction, Light1_Direction, normalize(HaloNormal * Normal), color);
#endif
#ifdef HALO_GUI
 vertexColor = color;
#else
 vertexColor = color * texelFetch(Sampler2, ivec2(HaloOffsetLight.zw), 0);
#endif
}
