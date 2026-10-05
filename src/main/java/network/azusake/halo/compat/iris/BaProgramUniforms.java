package network.azusake.halo.compat.iris;

import com.google.gson.*;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import network.azusake.halo.core.render.BaMaterial;

/** Adapter-owned uniform ABI. Names are shared with the generated pinned pack. */
public final class BaProgramUniforms {
    private BaProgramUniforms() {}
    public static final Map<String, List<Float>> DEFAULTS = read();
    private static Map<String, List<Float>> read() {
        try (var stream = BaProgramUniforms.class.getResourceAsStream("/assets/halo/ba/parameters-v1.json")) {
            if (stream == null) throw new IllegalStateException("Missing BA uniform metadata");
            var root = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            var result = new TreeMap<String, List<Float>>();
            root.entrySet().forEach(entry -> {
                var values = new ArrayList<Float>();
                if (entry.getValue().isJsonArray()) entry.getValue().getAsJsonArray().forEach(v -> values.add(v.getAsFloat()));
                else values.add(entry.getValue().getAsFloat());
                result.put(entry.getKey(), List.copyOf(values));
            });
            return Collections.unmodifiableMap(result);
        } catch (java.io.IOException error) { throw new ExceptionInInitializerError(error); }
    }
    public static String name(String socket) {
        return "HaloBA_" + (socket.equals("mask") ? "mask_default" : socket.replaceAll("[^a-zA-Z0-9_]", "_"));
    }
    /** Full per-draw upload, including defaults: a later instance never inherits prior values. */
    public static Map<String, List<Float>> values(BaMaterial material) {
        var result = new TreeMap<>(DEFAULTS);
        material.parameters().values().forEach((socket, value) -> result.put(name(socket), value));
        return Collections.unmodifiableMap(result);
    }
    public static String patchJson(String source) {
        var json = JsonParser.parseString(source).getAsJsonObject();
        var uniforms = json.getAsJsonArray("uniforms");
        DEFAULTS.forEach((name, value) -> add(uniforms, name, "float", value));
        add(uniforms,"HaloBA_type","int",List.of(0f));
        add(uniforms,"HaloBA_hasMask","int",List.of(0f));
        add(uniforms,"HaloBA_hasSpec","int",List.of(0f));
        add(uniforms,"HaloBA_fixedLight","float",List.of(-1f));
        add(uniforms,"HaloBA_objectDirection","float",List.of(0f,0f,1f));
        add(uniforms,"HaloBA_viewToReference","matrix3x3",List.of(1f,0f,0f,0f,1f,0f,0f,0f,1f));
        return json.toString();
    }
    private static void add(JsonArray uniforms, String name, String type, List<Float> values) {
        JsonObject uniform = new JsonObject(); uniform.addProperty("name","iris_"+name); uniform.addProperty("type",type);
        uniform.addProperty("count",values.size()); JsonArray data=new JsonArray(); values.forEach(data::add);
        uniform.add("values",data); uniforms.add(uniform);
    }
}
