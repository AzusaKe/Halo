#version 330
#extension GL_ARB_separate_shader_objects : require
#include <minecraft:dynamictransforms.glsl>
uniform sampler2D Sampler0;
uniform sampler2D HaloMask;
layout(std140) uniform HaloMaterial { mat3 HaloNormal; vec4 HaloMaskParams; vec4 HaloOffsetLight; };
layout(location = 0) in vec2 texCoord0;
layout(location = 1) in vec4 vertexColor;
layout(location = 0) out vec4 fragColor;
void main() {
 vec4 color = texture(Sampler0, texCoord0) * vertexColor * ColorModulator;
 if (HaloMaskParams.x > 0.5) {
  ivec2 dimensions = textureSize(HaloMask, 0);
  ivec2 pixel = min(ivec2(floor(fract(texCoord0 + HaloOffsetLight.xy) * vec2(dimensions))), dimensions - ivec2(1));
  float gray = texelFetch(HaloMask, pixel, 0).r;
  color.a *= HaloMaskParams.y > 0.5 ? step(HaloMaskParams.z,gray) : gray;
 }
 if(color.a <= 0.0 || color.a < HaloMaskParams.w) discard;
 fragColor=color;
}
