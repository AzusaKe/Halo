#version 150

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform vec4 ColorModulator;
uniform int MaskEnabled;
uniform int MaskMode;
uniform float MaskThreshold;
uniform vec2 MaskOffset;

in vec2 texCoord0;
in vec4 vertexColor;
out vec4 fragColor;

void main() {
    vec4 color = texture(Sampler0, texCoord0) * vertexColor * ColorModulator;
    if (MaskEnabled != 0) {
        // Exact nearest, repeating level-zero sampling, independent of a shared texture's filter/wrap state.
        ivec2 dimensions = textureSize(Sampler1, 0);
        ivec2 pixel = min(ivec2(floor(fract(texCoord0 + MaskOffset) * vec2(dimensions))), dimensions - ivec2(1));
        float gray = texelFetch(Sampler1, pixel, 0).r;
        color.a *= MaskMode == 1 ? step(MaskThreshold, gray) : gray;
    }
    if (color.a <= 0.0) discard;
    fragColor = color;
}
