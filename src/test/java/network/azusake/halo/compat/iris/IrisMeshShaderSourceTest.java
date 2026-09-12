package network.azusake.halo.compat.iris;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class IrisMeshShaderSourceTest {
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
    }

    @Test void uvVaryingUsesOriginalAttributeAndUniformsHavePerProgramDefaults() {
        String vertex = IrisMeshShaderSource.patch("test.vsh", "#version 330 core\nin vec2 iris_UV0;\nvoid main(void) { gl_Position=vec4(0); }");
        assertTrue(vertex.contains("halo_meshUV = iris_UV0; halo_meshMain();"));
        var json = JsonParser.parseString(IrisMeshShaderSource.patch("test.json", "{\"uniforms\":[]}")).getAsJsonObject();
        var uniforms = json.getAsJsonArray("uniforms");
        assertEquals(5, uniforms.size());
        assertEquals("iris_HaloMaskTexture", uniforms.get(0).getAsJsonObject().get("name").getAsString());
        assertEquals(1, uniforms.get(0).getAsJsonObject().getAsJsonArray("values").get(0).getAsInt());
    }

    @Test void unsupportedStagesFailInsteadOfSilentlyDrawingWrongMaterial() {
        assertThrows(IllegalArgumentException.class, () -> IrisMeshShaderSource.patch("mesh.gsh", "#version 330\nvoid main() {}"));
        assertThrows(IllegalArgumentException.class, () -> IrisMeshShaderSource.patch("mesh.fsh", "#version 330\nvoid main() {}"));
    }

    @Test void irisFallbackKeepsMaskAndZeroOnlyAlphaCutoff() {
        String vertex = IrisMeshShaderSource.patch("test.vsh", "#version 150 core\nin vec2 UV0;\nvoid main() { gl_Position=vec4(0); }");
        assertTrue(vertex.contains("halo_meshUV = UV0;"));
        String fragment = IrisMeshShaderSource.patch("test.fsh", "#version 150 core\nuniform float AlphaTestValue;\nuniform sampler2D gtexture;\nin vec2 texCoord;\nout vec4 fragColor;\nvoid main() { fragColor = texture(gtexture, texCoord); if (fragColor.a <= AlphaTestValue) discard; }");
        assertTrue(fragment.contains("const float AlphaTestValue = 0.0;"));
        assertTrue(fragment.contains("halo_texture(gtexture, texCoord)"));
    }
}
