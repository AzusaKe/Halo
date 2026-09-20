#version 330
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>
#moj_import <minecraft:light.glsl>
in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;
in ivec2 UV2;
in vec3 Normal;
uniform sampler2D Sampler2;
layout(std140) uniform HaloMaterial { mat3 HaloNormal; vec4 HaloMaskParams; vec4 HaloOffsetLight; };
out vec2 texCoord0;
out vec4 vertexColor;
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
