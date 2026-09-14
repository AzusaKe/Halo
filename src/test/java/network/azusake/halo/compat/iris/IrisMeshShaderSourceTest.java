package network.azusake.halo.compat.iris;

import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class IrisMeshShaderSourceTest {
    @Test void nativeAndIrisMasksUseOnlyTheMaskTextureNativeSize() throws IOException {
        String nativeSource, nativeVertex, nativeJson;
        try (var input = getClass().getResourceAsStream("/assets/halo/shaders/core/mesh.fsh")) {
            assertNotNull(input);
            nativeSource = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        try (var input = getClass().getResourceAsStream("/assets/halo/shaders/core/mesh.vsh")) {
            assertNotNull(input);
            nativeVertex = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        try (var input = getClass().getResourceAsStream("/assets/halo/shaders/core/mesh.json")) {
            assertNotNull(input);
            nativeJson = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertTrue(nativeSource.contains("textureSize(Sampler1, 0)"));
        assertFalse(nativeSource.contains("textureSize(Sampler0"));
        assertTrue(nativeSource.contains("floor(fract(texCoord0 + MaskOffset) * vec2(dimensions))"));
        assertTrue(nativeVertex.contains("texelFetch(Sampler2, LightCoord / 16, 0)"));
        var nativeProgram = JsonParser.parseString(nativeJson).getAsJsonObject();
        assertTrue(nativeProgram.getAsJsonArray("samplers").toString().contains("Sampler2"));
        assertTrue(nativeProgram.getAsJsonArray("uniforms").toString().contains("LightCoord"));

        String iris = IrisMeshShaderSource.patch("test.fsh",
            "#version 330 core\nuniform sampler2D gtexture;\nin vec2 uv;\nout vec4 color;\nvoid main(){color=texture(gtexture,uv);}");
        assertTrue(iris.contains("textureSize(iris_HaloMaskTexture, 0)"));
        assertFalse(iris.contains("textureSize(gtexture"));
    }

    @Test void maskChangesOnlyAlbedoSamplesBeforePackLighting() {
        String fragment = """
            #version 330 core
            #extension GL_ARB_gpu_shader5 : enable
            uniform sampler2D gtexture, shadowtex0;
            uniform float iris_currentAlphaTest;
            in vec2 uv;
            out vec4 iris_FragData0;
            void main() {
                vec4 albedo = texture(gtexture, uv);
                float shadow = texture(shadowtex0, uv).r;
                iris_FragData0 = vec4(albedo.rgb * shadow, albedo.a);
                if (!(iris_FragData0.a > iris_currentAlphaTest)) discard;
            }
            """;
        String patched = IrisMeshShaderSource.patch("test.fsh", fragment);
        assertTrue(patched.contains("halo_texture(gtexture, uv)"));
        assertTrue(patched.contains("texture(shadowtex0, uv).r"));
        assertTrue(patched.contains("vec4(albedo.rgb * shadow, albedo.a)"));
        assertTrue(patched.contains("const float iris_currentAlphaTest = 0.0;"));
        assertTrue(patched.indexOf("#extension") < patched.indexOf("in vec2 halo_meshUV"));
        assertTrue(patched.contains("fract(halo_meshUV + iris_HaloMaskOffset)"));
        assertTrue(patched.contains("texelFetch(iris_HaloMaskTexture, pixel, 0).r"));

        String compatibility = IrisMeshShaderSource.patch("entity.fsh", """
            #version 330 compatibility
            uniform sampler2D gtexture;
            in vec2 uv;
            out vec4 color;
            void main() { color = texture2D(gtexture, uv); }
            """, true);
        assertTrue(compatibility.contains("halo_texture2D(gtexture, uv)"));

        String custom = IrisMeshShaderSource.patch("entity.fsh", """
            #version 330 compatibility
            uniform sampler2D gtexture, normals;
            in vec2 uv;
            out vec4 color;
            vec4 texture2D_POMSwitch(sampler2D sampler, vec2 coord, vec4 derivatives, bool pom, float lod) {
                return texture(sampler, coord);
            }
            void main() {
                color = texture2D_POMSwitch(gtexture, uv, vec4(dFdx(uv), dFdy(uv)), false, 0.0);
                color += texture2D_POMSwitch(normals, uv, vec4(0), false, 0.0);
            }
            """, true);
        assertTrue(custom.contains("halo_material(texture2D_POMSwitch(gtexture, uv, vec4(dFdx(uv), dFdy(uv)), false, 0.0))"));
        assertTrue(custom.contains("color += texture2D_POMSwitch(normals"), "non-albedo samples stay untouched");
    }

    @Test void uvVaryingUsesOriginalAttributeAndUniformsHavePerProgramDefaults() {
        String vertex = IrisMeshShaderSource.patch("test.vsh", "#version 330 core\nin vec2 iris_UV0;\nin ivec2 iris_UV2;\nvoid main(void) { gl_Position=vec4(iris_UV2,0,1); }");
        assertTrue(vertex.contains("halo_meshUV = iris_UV0; halo_meshMain();"));
        assertTrue(vertex.contains("vec4(iris_HaloLightCoord,0,1)"));
        var json = JsonParser.parseString(IrisMeshShaderSource.patch("test.json", "{\"uniforms\":[]}")).getAsJsonObject();
        var uniforms = json.getAsJsonArray("uniforms");
        assertEquals(7, uniforms.size());
        assertEquals("iris_HaloMaskTexture", uniforms.get(0).getAsJsonObject().get("name").getAsString());
        assertEquals(1, uniforms.get(0).getAsJsonObject().getAsJsonArray("values").get(0).getAsInt());
        var litJson = JsonParser.parseString(IrisMeshShaderSource.patch(
            "test.json", "{\"uniforms\":[]}", true)).getAsJsonObject();
        assertEquals(3, litJson.getAsJsonArray("uniforms").get(0).getAsJsonObject()
            .getAsJsonArray("values").get(0).getAsInt());
    }

    @Test void unsupportedStagesFailInsteadOfSilentlyDrawingWrongMaterial() {
        assertThrows(IllegalArgumentException.class, () -> IrisMeshShaderSource.patch("mesh.gsh", "#version 330\nvoid main() {}"));
        assertThrows(IllegalArgumentException.class, () -> IrisMeshShaderSource.patch("mesh.fsh", "#version 330\nvoid main() {}"));
    }

    @Test void irisFallbackKeepsMaskAndZeroOnlyAlphaCutoff() {
        String vertex = IrisMeshShaderSource.patch("test.vsh", "#version 150 core\nin vec2 UV0;\nin ivec2 UV2;\nvoid main() { gl_Position=vec4(UV2,0,1); }");
        assertTrue(vertex.contains("halo_meshUV = UV0;"));
        assertTrue(vertex.contains("vec4(iris_HaloLightCoord,0,1)"));
        String fragment = IrisMeshShaderSource.patch("test.fsh", "#version 150 core\nuniform float AlphaTestValue;\nuniform sampler2D gtexture;\nin vec2 texCoord;\nout vec4 fragColor;\nvoid main() { fragColor = texture(gtexture, texCoord); if (fragColor.a <= AlphaTestValue) discard; }");
        assertTrue(fragment.contains("const float AlphaTestValue = 0.0;"));
        assertTrue(fragment.contains("halo_texture(gtexture, texCoord)"));
        assertTrue(fragment.contains("iris_HaloLegacyAlphaCutoff != 0"));
    }

    @Test void genericWorldDerivativeNormalUsesUploadedSmoothNormal() {
        String vertex = IrisMeshShaderSource.patch("entity.vsh", """
            #version 430 core
            uniform mat4 gbufferModelViewInverse;
            uniform mat3 iris_NormalMat;
            in vec3 iris_Normal;
            in vec2 iris_UV0;
            in ivec2 iris_UV2;
            void main() { gl_Position = vec4(iris_UV2, 0, 1); }
            """, true);
        assertTrue(vertex.contains("out vec3 halo_meshWorldNormal;"));
        assertTrue(vertex.contains(
            "halo_meshWorldNormal = normalize(mat3(gbufferModelViewInverse) * iris_NormalMat * iris_Normal);"));

        String fragmentSource = """
            #version 430 core
            uniform sampler2D tex;
            in vec3 surfaceWorldPosition;
            in vec2 surfaceUv;
            layout(location = 2) out vec4 normalTarget;
            void main() {
                vec3 horizontalChange = dFdx(surfaceWorldPosition);
                vec3 verticalChange = dFdy(surfaceWorldPosition);
                vec3 reconstructedNormal = normalize(cross(horizontalChange, verticalChange));
                normalTarget = texture(tex, surfaceUv) + vec4(reconstructedNormal, 1.0);
            }
            """;
        String fragment = IrisMeshShaderSource.patch("entity.fsh", fragmentSource, true);
        assertTrue(fragment.contains("in vec3 halo_meshWorldNormal;"));
        assertTrue(fragment.contains(
            "vec3 reconstructedNormal = (gl_FrontFacing ? 1.0 : -1.0) * normalize(halo_meshWorldNormal);"));
        assertFalse(fragment.contains("normalize(cross(horizontalChange, verticalChange))"));

        String flat = IrisMeshShaderSource.patch("entity.fsh", fragmentSource, false);
        assertFalse(flat.contains("halo_meshWorldNormal"));
        assertTrue(flat.contains(
            "vec3 reconstructedNormal = normalize(cross(horizontalChange, verticalChange));"));
    }

    @Test void derivativeNormalWithAmbiguousSpaceRemainsUntouched() {
        String source = """
            #version 430 core
            uniform sampler2D tex;
            in vec3 surfacePosition;
            in vec2 surfaceUv;
            out vec4 normalTarget;
            void main() {
                vec3 horizontalChange = dFdx(surfacePosition);
                vec3 verticalChange = dFdy(surfacePosition);
                vec3 reconstructedNormal = normalize(cross(horizontalChange, verticalChange));
                normalTarget = texture(tex, surfaceUv) + vec4(reconstructedNormal, 1.0);
            }
            """;
        String patched = IrisMeshShaderSource.patch("entity.fsh", source, true);
        assertFalse(patched.contains("halo_meshWorldNormal"));
        assertTrue(patched.contains(
            "vec3 reconstructedNormal = normalize(cross(horizontalChange, verticalChange));"));
    }
}
