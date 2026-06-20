package network.azusake.halo.json;

import network.azusake.halo.animation.*;
import network.azusake.halo.data.HaloDampingConfig;
import network.azusake.halo.data.HaloDefinition;
import network.azusake.halo.data.HaloPositioning;
import network.azusake.halo.data.OrientationMode;
import network.azusake.halo.shape.*;
import com.google.gson.*;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
import org.joml.Vector2f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Gson {@link JsonDeserializer} for {@link HaloDefinition}.
 *
 * <p>Handles the new layered model format with per-layer transforms,
 * orientation mode, and polymorphic primitive types.</p>
 */
public class HaloDefinitionDeserializer implements JsonDeserializer<HaloDefinition> {

    private static final Logger LOG = LoggerFactory.getLogger("halo");

    private final Gson gson;

    public HaloDefinitionDeserializer() {
        this.gson = new GsonBuilder()
            .registerTypeAdapter(Vec3d.class, new Vec3dAdapter())
            .registerTypeAdapter(Vector2f.class, new Vec2fAdapter())
            .registerTypeAdapter(Identifier.class, new IdentifierAdapter())
            .create();
    }

    public Gson getGson() {
        return gson;
    }

    // ------------------------------------------------------------------
    // Top-level deserialization
    // ------------------------------------------------------------------

    @Override
    public HaloDefinition deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
        throws JsonParseException {

        JsonObject root = json.getAsJsonObject();

        Identifier id = parseId(root, "id");
        HaloModel model = parseModel(root);
        Optional<LayerAnimation> animation = parseLayerAnimation(root.get("animation"));
        HaloPositioning positioning = parsePositioning(root.getAsJsonObject("positioning"));
        HaloDampingConfig damping = parseDamping(root.getAsJsonObject("damping"));

        return new HaloDefinition(id, model, animation, positioning, damping);
    }

    // ------------------------------------------------------------------
    // Model & Layers (new format)
    // ------------------------------------------------------------------

    private HaloModel parseModel(JsonObject root) {
        // Orientation mode: "locked" (default), "free", or "sync"
        OrientationMode mode = OrientationMode.LOCKED;
        if (root.has("orientation_mode")) {
            String modeStr = root.get("orientation_mode").getAsString().toLowerCase();
            mode = switch (modeStr) {
                case "free" -> OrientationMode.FREE;
                case "sync" -> OrientationMode.SYNC;
                default -> OrientationMode.LOCKED;
            };
        }

        // Layers array (replaces old "shape")
        List<HaloLayer> layers = new ArrayList<>();
        if (root.has("layers")) {
            for (JsonElement elem : root.getAsJsonArray("layers")) {
                layers.add(parseLayer(elem.getAsJsonObject()));
            }
        }
        // Backward compat: old "shape" field → single layer with locked mode
        else if (root.has("shape")) {
            JsonObject shapeObj = root.getAsJsonObject("shape");
            layers.addAll(convertLegacyShape(shapeObj));
        }

        // SYNC mode: configurable angular offset (Euler YXZ in degrees)
        Quaternionf syncOffset = new Quaternionf(); // identity default
        if (mode == OrientationMode.SYNC && root.has("sync_offset")) {
            JsonArray offArr = root.getAsJsonArray("sync_offset");
            float offYaw   = (float) Math.toRadians(offArr.get(0).getAsDouble());
            float offPitch = (float) Math.toRadians(offArr.get(1).getAsDouble());
            float offRoll  = offArr.size() > 2 ? (float) Math.toRadians(offArr.get(2).getAsDouble()) : 0f;
            // Same YXZ order as layer rotations: yaw (Y), pitch (X), roll (Z)
            syncOffset.rotateY(offYaw).rotateX(offPitch).rotateZ(offRoll);
        }

        return new HaloModel(mode, layers, syncOffset);
    }

    private HaloLayer parseLayer(JsonObject obj) {
        // Optional id
        Optional<String> id = obj.has("id")
            ? Optional.of(obj.get("id").getAsString())
            : Optional.empty();

        // Position (default origin)
        Vec3d position = obj.has("position")
            ? gson.fromJson(obj.get("position"), Vec3d.class)
            : Vec3d.ZERO;

        // Rotation: Euler [yaw, pitch, roll] in degrees → Quaternionf
        Quaternionf rotation = new Quaternionf();
        if (obj.has("rotation")) {
            JsonArray rotArr = obj.getAsJsonArray("rotation");
            float yaw   = (float) Math.toRadians(rotArr.get(0).getAsDouble());
            float pitch = (float) Math.toRadians(rotArr.get(1).getAsDouble());
            float roll  = rotArr.size() > 2 ? (float) Math.toRadians(rotArr.get(2).getAsDouble()) : 0f;
            // YXZ order: yaw (Y), pitch (X), roll (Z)
            rotation.rotateY(yaw).rotateX(pitch).rotateZ(roll);
        }

        // Scale (default 1.0)
        float scale = obj.has("scale") ? obj.get("scale").getAsFloat() : 1.0f;

        // Glowing toggle (default true)
        boolean glowing = !obj.has("glowing") || obj.get("glowing").getAsBoolean();

        // Layer animation (per-layer, visual-only, optional)
        Optional<LayerAnimation> layerAnim = parseLayerAnimation(obj.get("animation"));

        // Primitive
        HaloPrimitive primitive = parsePrimitive(obj.getAsJsonObject("primitive"));

        return new HaloLayer(id, position, rotation, scale, primitive, glowing, layerAnim);
    }

    // --- Layer animation (per-layer visual animation) ---

    /**
     * Parse an optional per-layer animation block.
     * Returns {@code Optional.empty()} if the block is missing, null, or empty.
     */
    private Optional<LayerAnimation> parseLayerAnimation(JsonElement element) {
        if (element == null || element.isJsonNull()) return Optional.empty();
        JsonObject animObj = element.getAsJsonObject();
        if (animObj.size() == 0) return Optional.empty();

        List<AnimationTerm> ox = parseAnimationTerms(animObj, "offset", "x");
        List<AnimationTerm> oy = parseAnimationTerms(animObj, "offset", "y");
        List<AnimationTerm> oz = parseAnimationTerms(animObj, "offset", "z");
        List<AnimationTerm> ry = parseAnimationTerms(animObj, "rotation", "yaw");
        List<AnimationTerm> rp = parseAnimationTerms(animObj, "rotation", "pitch");
        List<AnimationTerm> rr = parseAnimationTerms(animObj, "rotation", "roll");

        LayerAnimation result = new LayerAnimation(ox, oy, oz, ry, rp, rr);
        return result.isEmpty() ? Optional.empty() : Optional.of(result);
    }

    /**
     * Extract a list of {@link AnimationTerm}s from a nested JSON structure:
     * {@code parent.group.axis → [...]}.
     * Returns an empty list if any level of the path is missing.
     */
    private List<AnimationTerm> parseAnimationTerms(JsonObject parent, String group, String axis) {
        if (!parent.has(group)) return List.of();
        JsonElement groupElem = parent.get(group);
        if (groupElem.isJsonNull()) return List.of();
        JsonObject groupObj = groupElem.getAsJsonObject();
        if (!groupObj.has(axis)) return List.of();
        JsonArray arr = groupObj.getAsJsonArray(axis);
        if (arr.isEmpty()) return List.of();

        List<AnimationTerm> terms = new ArrayList<>(arr.size());
        for (JsonElement elem : arr) {
            JsonObject t = elem.getAsJsonObject();
            String function = t.get("function").getAsString().toLowerCase();
            terms.add(switch (function) {
                case "sin" -> new AnimationTerm.Sin(
                    t.get("A").getAsDouble(),
                    t.get("omega").getAsDouble(),
                    t.has("phi") ? t.get("phi").getAsDouble() : 0.0);
                case "cos" -> new AnimationTerm.Cos(
                    t.get("A").getAsDouble(),
                    t.get("omega").getAsDouble(),
                    t.has("phi") ? t.get("phi").getAsDouble() : 0.0);
                case "linear" -> new AnimationTerm.Linear(
                    t.get("speed").getAsDouble());
                default -> throw new JsonParseException("Unknown animation term function: " + function);
            });
        }
        return Collections.unmodifiableList(terms);
    }

    private HaloPrimitive parsePrimitive(JsonObject obj) {
        if (obj == null) throw new JsonParseException("Missing primitive object in layer");
        String type = obj.get("type").getAsString();
        return switch (type) {
            case "billboard" -> parseBillboardPrimitive(obj);
            default -> throw new JsonParseException("Unknown primitive type: " + type);
        };
    }

    private BillboardPrimitive parseBillboardPrimitive(JsonObject obj) {
        Identifier texture = Identifier.tryParse(obj.get("texture").getAsString());
        Vector2f size = gson.fromJson(obj.get("size"), Vector2f.class);
        GlowLayer glow = obj.has("glow") && !obj.get("glow").isJsonNull()
            ? parseGlowLayer(obj.getAsJsonObject("glow"))
            : null;
        return new BillboardPrimitive(texture, size, glow);
    }

    /**
     * Convert old-style "shape" block to a list of layers for backward compatibility.
     */
    private List<HaloLayer> convertLegacyShape(JsonObject shapeObj) {
        List<HaloLayer> layers = new ArrayList<>();
        String type = shapeObj.get("type").getAsString();

        switch (type) {
            case "billboard" -> {
                BillboardPrimitive bp = parseBillboardPrimitive(shapeObj);
                layers.add(new HaloLayer(Vec3d.ZERO, bp));
            }
            case "multi_billboard" -> {
                for (JsonElement elem : shapeObj.getAsJsonArray("layers")) {
                    BillboardPrimitive bp = parseBillboardPrimitive(elem.getAsJsonObject());
                    layers.add(new HaloLayer(Vec3d.ZERO, bp));
                }
            }
            default -> throw new JsonParseException("Unknown legacy shape type: " + type);
        }
        return layers;
    }

    // --- Glow & Pulse (unchanged) ---

    private GlowLayer parseGlowLayer(JsonObject obj) {
        Identifier texture = Identifier.tryParse(obj.get("texture").getAsString());
        Vector2f size = gson.fromJson(obj.get("size"), Vector2f.class);
        int color = obj.get("color").getAsInt();
        float alpha = obj.get("alpha").getAsFloat();
        PulseConfig pulse = obj.has("pulse") && !obj.get("pulse").isJsonNull()
            ? parsePulse(obj.getAsJsonObject("pulse"))
            : null;
        return new GlowLayer(texture, size, color, alpha, pulse);
    }

    private PulseConfig parsePulse(JsonObject obj) {
        float amplitude = obj.get("amplitude").getAsFloat();
        float frequency = obj.get("frequency").getAsFloat();
        float phase = obj.has("phase") ? obj.get("phase").getAsFloat() : 0f;
        return new PulseConfig(amplitude, frequency, phase);
    }

    // --- Positioning & Damping (unchanged) ---

    private HaloPositioning parsePositioning(JsonObject obj) {
        if (obj == null) return new HaloPositioning(Vec3d.ZERO, 1.0);
        Vec3d offset = gson.fromJson(obj.get("offset"), Vec3d.class);
        double scale = obj.has("scale") ? obj.get("scale").getAsDouble() : 1.0;
        return new HaloPositioning(offset, scale);
    }

    private HaloDampingConfig parseDamping(JsonObject obj) {
        if (obj == null) {
            return new HaloDampingConfig(0.15, 0.1, 3.0, 180.0);
        }
        return new HaloDampingConfig(
            obj.get("linearFactor").getAsDouble(),
            obj.get("angularFactor").getAsDouble(),
            obj.get("maxLinearDistance").getAsDouble(),
            obj.get("maxAngularDegrees").getAsDouble()
        );
    }

    // ------------------------------------------------------------------
    // Sub-parsers
    // ------------------------------------------------------------------

    private Identifier parseId(JsonObject root, String key) {
        String raw = root.get(key).getAsString();
        return Identifier.tryParse(raw);
    }

    // ------------------------------------------------------------------
    // Custom type adapters
    // ------------------------------------------------------------------

    private static class Vec3dAdapter implements JsonDeserializer<Vec3d>, JsonSerializer<Vec3d> {
        @Override
        public Vec3d deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) {
            JsonArray arr = json.getAsJsonArray();
            return new Vec3d(arr.get(0).getAsDouble(), arr.get(1).getAsDouble(), arr.get(2).getAsDouble());
        }

        @Override
        public JsonElement serialize(Vec3d src, Type typeOfSrc, JsonSerializationContext context) {
            JsonArray arr = new JsonArray();
            arr.add(src.x);
            arr.add(src.y);
            arr.add(src.z);
            return arr;
        }
    }

    private static class Vec2fAdapter implements JsonDeserializer<Vector2f>, JsonSerializer<Vector2f> {
        @Override
        public Vector2f deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) {
            JsonArray arr = json.getAsJsonArray();
            return new Vector2f(arr.get(0).getAsFloat(), arr.get(1).getAsFloat());
        }

        @Override
        public JsonElement serialize(Vector2f src, Type typeOfSrc, JsonSerializationContext context) {
            JsonArray arr = new JsonArray();
            arr.add(src.x);
            arr.add(src.y);
            return arr;
        }
    }

    private static class IdentifierAdapter implements JsonDeserializer<Identifier>, JsonSerializer<Identifier> {
        @Override
        public Identifier deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) {
            return Identifier.tryParse(json.getAsString());
        }

        @Override
        public JsonElement serialize(Identifier src, Type typeOfSrc, JsonSerializationContext context) {
            return new JsonPrimitive(src.toString());
        }
    }
}
