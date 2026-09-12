package network.azusake.halo.compat.iris;

import com.google.gson.*;
import java.util.regex.Pattern;

/** Load-time edits of Halo's own Iris program variant; never edits a shared vanilla/pack program. */
public final class IrisMeshShaderSource {
    private static final Pattern MAIN = Pattern.compile("\\bvoid\\s+main\\s*\\(\\s*(?:void\\s*)?\\)");
    private static final Pattern SAMPLE = Pattern.compile(
        "\\b(texture|textureLod|textureGrad|texelFetch)\\s*\\(\\s*(gtexture|texture|tex)\\s*,");
    private IrisMeshShaderSource() {}

    public static String patch(String path, String source) {
        if (path.endsWith(".json")) return json(source);
        if (path.endsWith(".vsh")) return vertex(source);
        if (path.endsWith(".fsh")) return fragment(source);
        throw new IllegalArgumentException("Halo mesh does not support a geometry/tessellation stage in gbuffers_textured: " + path);
    }

    private static String json(String source) {
        JsonObject json = JsonParser.parseString(source).getAsJsonObject();
        JsonArray uniforms = json.getAsJsonArray("uniforms");
        add(uniforms, "HaloMaskTexture", "int", 1);
        add(uniforms, "HaloMaskEnabled", "int", 0);
        add(uniforms, "HaloMaskMode", "int", 0);
        add(uniforms, "HaloMaskThreshold", "float", .5f);
        add(uniforms, "HaloMaskOffset", "float", 0, 0);
        return json.toString();
    }

    private static void add(JsonArray uniforms, String name, String type, Number... values) {
        JsonObject uniform = new JsonObject();
        uniform.addProperty("name", "iris_" + name);
        uniform.addProperty("type", type);
        uniform.addProperty("count", values.length);
        JsonArray array = new JsonArray();
        for (Number value : values) array.add(value);
        uniform.add("values", array);
        uniforms.add(uniform);
    }

    private static String vertex(String source) {
        var uv = Pattern.compile("\\bin\\s+vec2\\s+(iris_UV0|UV0)\\s*;").matcher(source);
        if (!uv.find())
            throw new IllegalArgumentException("Iris mesh shader has no base UV attribute");
        String attribute = uv.group(1);
        source = renameMain(source);
        return declarations(source, "out vec2 halo_meshUV;\n")
            + "\nvoid main() { halo_meshUV = " + attribute + "; halo_meshMain(); }\n";
    }

    private static String fragment(String source) {
        var samples = SAMPLE.matcher(source);
        if (!samples.find()) throw new IllegalArgumentException("Iris mesh shader has no supported base-texture sample");
        source = samples.replaceAll("halo_$1($2,");
        // Iris's fixed vanilla alpha test belongs to the cloned program only.
        source = source.replaceAll("\\buniform\\s+float\\s+iris_currentAlphaTest\\s*;",
            "const float iris_currentAlphaTest = 0.0;");
        source = source.replaceAll("\\buniform\\s+float\\s+AlphaTestValue\\s*;",
            "const float AlphaTestValue = 0.0;");
        source = renameMain(source);
        return declarations(source, """
            in vec2 halo_meshUV;
            uniform sampler2D iris_HaloMaskTexture;
            uniform int iris_HaloMaskEnabled;
            uniform int iris_HaloMaskMode;
            uniform float iris_HaloMaskThreshold;
            uniform vec2 iris_HaloMaskOffset;
            float halo_maskAlpha() {
                if (iris_HaloMaskEnabled == 0) return 1.0;
                ivec2 dimensions = textureSize(iris_HaloMaskTexture, 0);
                ivec2 pixel = min(ivec2(floor(fract(halo_meshUV + iris_HaloMaskOffset) * vec2(dimensions))), dimensions - ivec2(1));
                float gray = texelFetch(iris_HaloMaskTexture, pixel, 0).r;
                return iris_HaloMaskMode == 1 ? step(iris_HaloMaskThreshold, gray) : gray;
            }
            vec4 halo_material(vec4 color) {
                color.a *= halo_maskAlpha();
                if (color.a <= 0.0) discard;
                return color;
            }
            vec4 halo_texture(sampler2D tex, vec2 uv) { return halo_material(texture(tex, uv)); }
            vec4 halo_texture(sampler2D tex, vec2 uv, float bias) { return halo_material(texture(tex, uv, bias)); }
            vec4 halo_textureLod(sampler2D tex, vec2 uv, float lod) { return halo_material(textureLod(tex, uv, lod)); }
            vec4 halo_textureGrad(sampler2D tex, vec2 uv, vec2 dx, vec2 dy) { return halo_material(textureGrad(tex, uv, dx, dy)); }
            vec4 halo_texelFetch(sampler2D tex, ivec2 uv, int lod) { return halo_material(texelFetch(tex, uv, lod)); }
            """) + "\nvoid main() { if (halo_maskAlpha() <= 0.0) discard; halo_meshMain(); }\n";
    }

    private static String renameMain(String source) {
        var main = MAIN.matcher(source);
        if (!main.find()) throw new IllegalArgumentException("Iris mesh shader has no main function");
        return main.replaceFirst("void halo_meshMain()");
    }

    private static String declarations(String source, String declarations) {
        // Keep version/extension directives ahead of declarations (Iris supplies preprocessed GLSL).
        var header = Pattern.compile("\\A(?:\\s*#[^\\r\\n]*(?:\\r?\\n|$))+").matcher(source);
        if (!header.find()) throw new IllegalArgumentException("Iris mesh shader has no GLSL version header");
        int split = header.end();
        return source.substring(0, split) + "\n" + declarations + source.substring(split);
    }
}
