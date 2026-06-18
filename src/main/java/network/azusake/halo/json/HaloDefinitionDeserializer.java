package network.azusake.halo.json;

import network.azusake.halo.animation.*;
import network.azusake.halo.data.HaloDampingConfig;
import network.azusake.halo.data.HaloDefinition;
import network.azusake.halo.data.HaloPositioning;
import network.azusake.halo.shape.*;
import com.google.gson.*;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector2f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * Gson {@link JsonDeserializer} for {@link HaloDefinition}.
 * <p>
 * Handles polymorphic shape and curve types via a {@code "type"} discriminator field.
 * Also registers custom adapters for {@link Vec3d}, {@link Vector2f}, and {@link Identifier}.
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

    /**
     * Return the pre-configured Gson instance for standalone use (e.g. in tests).
     */
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
        HaloShape shape = parseShape(root.getAsJsonObject("shape"));
        HaloAnimation animation = parseAnimation(root.getAsJsonObject("animation"));
        HaloPositioning positioning = parsePositioning(root.getAsJsonObject("positioning"));
        HaloDampingConfig damping = parseDamping(root.getAsJsonObject("damping"));

        return new HaloDefinition(id, shape, animation, positioning, damping);
    }

    // ------------------------------------------------------------------
    // Sub-parsers
    // ------------------------------------------------------------------

    private Identifier parseId(JsonObject root, String key) {
        String raw = root.get(key).getAsString();
        return Identifier.tryParse(raw);
    }

    private HaloShape parseShape(JsonObject obj) {
        if (obj == null) return null;
        String type = obj.get("type").getAsString();
        return switch (type) {
            case "billboard" -> parseBillboard(obj);
            case "multi_billboard" -> parseMultiBillboard(obj);
            default -> throw new JsonParseException("Unknown shape type: " + type);
        };
    }

    private BillboardShape parseBillboard(JsonObject obj) {
        Identifier texture = Identifier.tryParse(obj.get("texture").getAsString());
        Vector2f size = gson.fromJson(obj.get("size"), Vector2f.class);
        GlowLayer glow = obj.has("glow") && !obj.get("glow").isJsonNull()
            ? parseGlowLayer(obj.getAsJsonObject("glow"))
            : null;
        return new BillboardShape(texture, size, glow);
    }

    private MultiBillboardShape parseMultiBillboard(JsonObject obj) {
        List<BillboardShape> layers = new ArrayList<>();
        for (JsonElement elem : obj.getAsJsonArray("layers")) {
            layers.add(parseBillboard(elem.getAsJsonObject()));
        }
        return new MultiBillboardShape(layers);
    }

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

    // --- Animation ---

    private HaloAnimation parseAnimation(JsonObject obj) {
        if (obj == null) return HaloAnimation.EMPTY;

        List<PositionCurve> posCurves = new ArrayList<>();
        if (obj.has("positionCurves")) {
            for (JsonElement elem : obj.getAsJsonArray("positionCurves")) {
                JsonObject c = elem.getAsJsonObject();
                PositionCurve.PositionAxis axis = PositionCurve.PositionAxis.valueOf(
                    c.get("axis").getAsString().toUpperCase());
                AnimationCurve curve = parseCurve(c.getAsJsonObject("curve"));
                posCurves.add(new PositionCurve(axis, curve));
            }
        }

        List<RotationCurve> rotCurves = new ArrayList<>();
        if (obj.has("rotationCurves")) {
            for (JsonElement elem : obj.getAsJsonArray("rotationCurves")) {
                JsonObject c = elem.getAsJsonObject();
                RotationCurve.RotationAxis axis = RotationCurve.RotationAxis.valueOf(
                    c.get("axis").getAsString().toUpperCase());
                AnimationCurve curve = parseCurve(c.getAsJsonObject("curve"));
                rotCurves.add(new RotationCurve(axis, curve));
            }
        }

        return new HaloAnimation(posCurves, rotCurves);
    }

    private AnimationCurve parseCurve(JsonObject obj) {
        if (obj == null) throw new JsonParseException("Missing curve object");
        String type = obj.get("type").getAsString();
        return switch (type) {
            case "constant" -> new ConstantCurve(obj.get("value").getAsDouble());
            case "linear" -> new LinearCurve(
                obj.get("start").getAsDouble(),
                obj.get("speed").getAsDouble());
            case "oscillate" -> new OscillateCurve(
                obj.get("amplitude").getAsDouble(),
                obj.get("frequency").getAsDouble(),
                obj.has("phase") ? obj.get("phase").getAsDouble() : 0.0);
            default -> throw new JsonParseException("Unknown curve type: " + type);
        };
    }

    // --- Positioning & Damping ---

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
    // Custom type adapters
    // ------------------------------------------------------------------

    /**
     * Adapter: JSON array {@code [x, y, z]} ⇄ {@link Vec3d}.
     */
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

    /**
     * Adapter: JSON array {@code [x, y]} ⇄ {@link Vector2f}.
     */
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

    /**
     * Adapter: JSON string {@code "namespace:path"} ⇄ {@link Identifier}.
     */
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
