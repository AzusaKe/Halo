package network.azusake.halo.compat.iris;

import com.google.gson.*;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/** Load-time edits of Halo's own Iris program variant; never edits a shared vanilla/pack program. */
public final class IrisMeshShaderSource {
    private static final Pattern MAIN = Pattern.compile("\\bvoid\\s+main\\s*\\(\\s*(?:void\\s*)?\\)");
    private static final Pattern SAMPLE = Pattern.compile(
        "\\b(texture2DLod|texture2D|textureLod|textureGrad|texture|texelFetch)\\s*\\(\\s*(gtexture|texture|tex)\\s*,");
    private static final Pattern CUSTOM_SAMPLE = Pattern.compile(
        "\\btexture(?:2D)?_[A-Za-z0-9_]+\\s*\\(\\s*(?:gtexture|texture|tex)\\s*,");
    private static final Pattern VEC3_INPUT = Pattern.compile(
        "\\b(?:flat\\s+|smooth\\s+|noperspective\\s+)?in\\s+vec3\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*;");
    private static final Pattern VEC3_DERIVATIVE = Pattern.compile(
        "\\bvec3\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*=\\s*(dFdx|dFdy)\\s*\\(\\s*([A-Za-z_][A-Za-z0-9_]*)\\s*\\)\\s*;");
    private static final Pattern NORMALIZED_CROSS = Pattern.compile(
        "\\bvec3\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*=\\s*normalize\\s*\\(\\s*cross\\s*\\(\\s*([A-Za-z_][A-Za-z0-9_]*)\\s*,\\s*([A-Za-z_][A-Za-z0-9_]*)\\s*\\)\\s*\\)\\s*;");
    private IrisMeshShaderSource() {}

    public static String patch(String path, String source) {
        return patch(path, source, false);
    }

    public static String patch(String path, String source, boolean directionalLighting) {
        if (path.endsWith(".json")) return json(source, directionalLighting);
        if (path.endsWith(".vsh")) return vertex(source, directionalLighting);
        if (path.endsWith(".fsh")) return fragment(source, directionalLighting);
        throw new IllegalArgumentException("Halo mesh does not support a geometry/tessellation stage in gbuffers_textured: " + path);
    }

    /** Whether this stage receives Halo's generic smooth-world-normal replacement. */
    static boolean replacesWorldDerivativeFaceNormal(String path, String source, boolean directionalLighting) {
        return directionalLighting && path.endsWith(".fsh") && worldDerivativeFaceNormal(source) != null;
    }

    private static String json(String source, boolean directionalLighting) {
        JsonObject json = JsonParser.parseString(source).getAsJsonObject();
        JsonArray uniforms = json.getAsJsonArray("uniforms");
        // Entity programs reserve Sampler1 for the overlay texture. Keep the
        // existing particles variant on slot 1 and put Halo's mask on slot 3.
        add(uniforms, "HaloMaskTexture", "int", directionalLighting ? 3 : 1);
        add(uniforms, "HaloMaskEnabled", "int", 0);
        add(uniforms, "HaloMaskMode", "int", 0);
        add(uniforms, "HaloMaskThreshold", "float", .5f);
        add(uniforms, "HaloMaskOffset", "float", 0, 0);
        add(uniforms, "HaloLightCoord", "int", 240, 240);
        add(uniforms, "HaloLegacyAlphaCutoff", "int", 0);
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

    private static String vertex(String source, boolean directionalLighting) {
        var uv = Pattern.compile("\\bin\\s+vec2\\s+(iris_UV0|UV0)\\s*;").matcher(source);
        if (!uv.find())
            throw new IllegalArgumentException("Iris mesh shader has no base UV attribute");
        String attribute = uv.group(1);
        var light = Pattern.compile("\\bin\\s+ivec2\\s+(iris_UV2|UV2)\\s*;").matcher(source);
        if (!light.find())
            throw new IllegalArgumentException("Iris mesh shader has no lightmap UV attribute");
        String lightAttribute = light.group(1);
        int lightDeclarationEnd = light.end();
        source = source.substring(0, lightDeclarationEnd)
            + source.substring(lightDeclarationEnd).replaceAll("\\b" + Pattern.quote(lightAttribute) + "\\b",
                "iris_HaloLightCoord");
        boolean transmitWorldNormal = directionalLighting && supportsWorldNormalVertex(source);
        source = renameMain(source);
        String extra = transmitWorldNormal ? "out vec3 halo_meshWorldNormal;\n" : "";
        String normal = transmitWorldNormal
            ? " halo_meshWorldNormal = normalize(mat3(gbufferModelViewInverse) * iris_NormalMat * iris_Normal);"
            : "";
        return declarations(source, "out vec2 halo_meshUV;\n" + extra + "uniform ivec2 iris_HaloLightCoord;\n")
            + "\nvoid main() { halo_meshUV = " + attribute + "; halo_meshMain();" + normal + " }\n";
    }

    private static String fragment(String source, boolean directionalLighting) {
        FaceNormal faceNormal = directionalLighting ? worldDerivativeFaceNormal(source) : null;
        if (faceNormal != null) source = source.substring(0, faceNormal.start())
            + "vec3 " + faceNormal.name()
            + " = (gl_FrontFacing ? 1.0 : -1.0) * normalize(halo_meshWorldNormal);"
            + source.substring(faceNormal.end());
        var samples = SAMPLE.matcher(source);
        boolean standardSample = samples.find();
        if (standardSample) source = samples.replaceAll("halo_$1($2,");
        Wrap custom = wrapCustomBaseSamples(source);
        source = custom.source();
        if (!standardSample && !custom.changed()) throw new IllegalArgumentException(
            "Iris mesh shader has no supported base-texture sample; found " + samplingSummary(source));
        // Iris's fixed vanilla alpha test belongs to the cloned program only.
        source = source.replaceAll("\\buniform\\s+float\\s+iris_currentAlphaTest\\s*;",
            "const float iris_currentAlphaTest = 0.0;");
        source = source.replaceAll("\\buniform\\s+float\\s+AlphaTestValue\\s*;",
            "const float AlphaTestValue = 0.0;");
        source = renameMain(source);
        String smoothNormal = faceNormal != null ? "in vec3 halo_meshWorldNormal;\n" : "";
        return declarations(source, smoothNormal + """
            in vec2 halo_meshUV;
            uniform sampler2D iris_HaloMaskTexture;
            uniform int iris_HaloMaskEnabled;
            uniform int iris_HaloMaskMode;
            uniform float iris_HaloMaskThreshold;
            uniform vec2 iris_HaloMaskOffset;
            uniform int iris_HaloLegacyAlphaCutoff;
            float halo_maskAlpha() {
                if (iris_HaloMaskEnabled == 0) return 1.0;
                ivec2 dimensions = textureSize(iris_HaloMaskTexture, 0);
                ivec2 pixel = min(ivec2(floor(fract(halo_meshUV + iris_HaloMaskOffset) * vec2(dimensions))), dimensions - ivec2(1));
                float gray = texelFetch(iris_HaloMaskTexture, pixel, 0).r;
                return iris_HaloMaskMode == 1 ? step(iris_HaloMaskThreshold, gray) : gray;
            }
            vec4 halo_material(vec4 color) {
                color.a *= halo_maskAlpha();
                if (color.a <= 0.0 || (iris_HaloLegacyAlphaCutoff != 0 && color.a < 0.1)) discard;
                return color;
            }
            vec4 halo_texture(sampler2D tex, vec2 uv) { return halo_material(texture(tex, uv)); }
            vec4 halo_texture(sampler2D tex, vec2 uv, float bias) { return halo_material(texture(tex, uv, bias)); }
            vec4 halo_texture2D(sampler2D tex, vec2 uv) { return halo_material(texture(tex, uv)); }
            vec4 halo_texture2D(sampler2D tex, vec2 uv, float bias) { return halo_material(texture(tex, uv, bias)); }
            vec4 halo_texture2DLod(sampler2D tex, vec2 uv, float lod) { return halo_material(textureLod(tex, uv, lod)); }
            vec4 halo_textureLod(sampler2D tex, vec2 uv, float lod) { return halo_material(textureLod(tex, uv, lod)); }
            vec4 halo_textureGrad(sampler2D tex, vec2 uv, vec2 dx, vec2 dy) { return halo_material(textureGrad(tex, uv, dx, dy)); }
            vec4 halo_texelFetch(sampler2D tex, ivec2 uv, int lod) { return halo_material(texelFetch(tex, uv, lod)); }
            """) + "\nvoid main() { if (halo_maskAlpha() <= 0.0) discard; halo_meshMain(); }\n";
    }

    private static boolean supportsWorldNormalVertex(String source) {
        // These names belong to the public shader/Iris vertex ABI. No shader-pack
        // identifier is used: unsupported programs retain their original source.
        return declaration(source, "uniform", "mat4", "gbufferModelViewInverse")
            && declaration(source, "uniform", "mat3", "iris_NormalMat")
            && declaration(source, "in", "vec3", "iris_Normal");
    }

    private static boolean declaration(String source, String storage, String type, String name) {
        return Pattern.compile("\\b" + storage + "\\s+" + type + "\\s+" + Pattern.quote(name) + "\\s*;")
            .matcher(source).find();
    }

    private record Derivative(String function, String position) {}
    private record FaceNormal(int start, int end, String name) {}

    /**
     * Finds the generic GLSL idiom that reconstructs a world-space face normal
     * from screen derivatives of one position varying. Ambiguous coordinate
     * spaces and unrelated cross products deliberately remain untouched.
     */
    private static FaceNormal worldDerivativeFaceNormal(String source) {
        var worldInputs = new LinkedHashSet<String>();
        var inputs = VEC3_INPUT.matcher(source);
        while (inputs.find()) {
            String name = inputs.group(1);
            String semantic = name.toLowerCase(Locale.ROOT).replace("_", "");
            if (semantic.contains("world") && semantic.contains("pos")) worldInputs.add(name);
        }
        if (worldInputs.isEmpty()) return null;

        Map<String, Derivative> derivatives = new HashMap<>();
        var derivative = VEC3_DERIVATIVE.matcher(source);
        while (derivative.find()) derivatives.put(derivative.group(1),
            new Derivative(derivative.group(2), derivative.group(3)));

        var cross = NORMALIZED_CROSS.matcher(source);
        while (cross.find()) {
            Derivative x = derivatives.get(cross.group(2));
            Derivative y = derivatives.get(cross.group(3));
            if (x != null && y != null
                && x.function().equals("dFdx") && y.function().equals("dFdy")
                && x.position().equals(y.position()) && worldInputs.contains(x.position()))
                return new FaceNormal(cross.start(), cross.end(), cross.group(1));
        }
        return null;
    }

    private static String renameMain(String source) {
        var main = MAIN.matcher(source);
        if (!main.find()) throw new IllegalArgumentException("Iris mesh shader has no main function");
        return main.replaceFirst("void halo_meshMain()");
    }

    private static String samplingSummary(String source) {
        var calls = Pattern.compile("\\b((?:texture|texelFetch)[A-Za-z0-9_]*)\\s*\\(\\s*([A-Za-z_][A-Za-z0-9_]*)\\s*,")
            .matcher(source);
        var found = new LinkedHashSet<String>();
        while (calls.find() && found.size() < 8) found.add(calls.group(1) + "(" + calls.group(2) + ")");
        return found.isEmpty() ? "no texture calls" : String.join(", ", found);
    }

    private record Wrap(String source, boolean changed) {}

    private static Wrap wrapCustomBaseSamples(String source) {
        var calls = CUSTOM_SAMPLE.matcher(source);
        var result = new StringBuilder(source.length() + 32);
        int cursor = 0;
        boolean changed = false;
        while (calls.find(cursor)) {
            int open = source.indexOf('(', calls.start());
            int close = matchingParenthesis(source, open);
            if (close < 0) break;
            result.append(source, cursor, calls.start()).append("halo_material(")
                .append(source, calls.start(), close + 1).append(')');
            cursor = close + 1;
            changed = true;
        }
        if (!changed) return new Wrap(source, false);
        return new Wrap(result.append(source, cursor, source.length()).toString(), true);
    }

    private static int matchingParenthesis(String source, int open) {
        int depth = 0;
        for (int i = open; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '(') depth++;
            else if (c == ')' && --depth == 0) return i;
        }
        return -1;
    }

    private static String declarations(String source, String declarations) {
        // Keep version/extension directives ahead of declarations (Iris supplies preprocessed GLSL).
        var header = Pattern.compile("\\A(?:\\s*#[^\\r\\n]*(?:\\r?\\n|$))+").matcher(source);
        if (!header.find()) throw new IllegalArgumentException("Iris mesh shader has no GLSL version header");
        int split = header.end();
        return source.substring(0, split) + "\n" + declarations + source.substring(split);
    }
}
