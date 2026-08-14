package network.azusake.halo.json;

import network.azusake.halo.animation.*;
import network.azusake.halo.data.HaloDampingConfig;
import network.azusake.halo.data.HaloDefinition;
import network.azusake.halo.data.HaloPositioning;
import network.azusake.halo.data.OrientationMode;
import network.azusake.halo.data.SchemaVersion;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.StreamSupport;

/**
 * Gson {@link JsonDeserializer} for {@link HaloDefinition}.
 *
 * <p>Handles the layered/grouped model format with per-group transforms,
 * orientation mode, and polymorphic primitive types.</p>
 *
 * <h3>Schema Versioning Strategy</h3>
 * <p>Each halo definition JSON may carry a {@code "version"} field (e.g. {@code "1.0.10"}).
 * When absent, the definition is treated as {@link SchemaVersion#CURRENT}.  The version
 * is parsed via {@link SchemaVersion#parse(String)} and threaded through all parsing
 * methods so that future breaking changes can be handled with version-gated branches:</p>
 * <pre>{@code
 *   if (version.isBefore(new SchemaVersion(1, 1, 0))) {
 *       // old format path
 *   } else {
 *       // new format path
 *   }
 * }</pre>
 * <p><b>When introducing a breaking change:</b></p>
 * <ol>
 *   <li>Bump {@link SchemaVersion#CURRENT} to the new version</li>
 *   <li>Add a version branch in the affected parse method(s)</li>
 *   <li>Keep the old code path under the {@code isBefore} guard</li>
 * </ol>
 * <p>Do NOT freeze or copy the entire parser — keep all version branches inline
 * within the same deserializer.  Only extract helper methods when a single
 * parse method grows too complex (more than ~2 levels of version nesting).</p>
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

        // Schema version: defaults to CURRENT when absent (backward compat with pre-versioning files)
        SchemaVersion schemaVersion = SchemaVersion.CURRENT;
        if (root.has("version")) {
            try {
                schemaVersion = SchemaVersion.parse(root.get("version").getAsString());
            } catch (Exception e) {
                throw new JsonParseException("Invalid 'version' field: " + root.get("version").getAsString(), e);
            }
            if (schemaVersion.compareTo(SchemaVersion.CURRENT) > 0) {
                LOG.warn("[Halo] Definition '{}' requires schema version {} but this mod only supports up to {}. " +
                    "Please update the Halo mod — unrecognized fields will be skipped and rendering may fail.",
                    root.has("id") ? root.get("id").getAsString() : "<unknown>",
                    schemaVersion, SchemaVersion.CURRENT);
            }
        }

        Identifier id = parseId(root, "id");
        HaloModel model = parseModel(root, schemaVersion);
        Optional<LayerAnimation> animation = parseLayerAnimation(root.get("animation"));
        HaloPositioning positioning = parsePositioning(root.getAsJsonObject("positioning"));
        boolean allowAngularMomentum = root.has("allow_angular_momentum")
            ? root.get("allow_angular_momentum").getAsBoolean()
            : false;
        HaloDampingConfig damping = parseDamping(root.getAsJsonObject("damping"), allowAngularMomentum);
        boolean hideOnSleep = root.has("hide_on_sleep")
            ? root.get("hide_on_sleep").getAsBoolean()
            : false;
        boolean displayInInvisible = root.has("display_in_invisible")
            ? root.get("display_in_invisible").getAsBoolean()
            : false;

        // Startup / shutdown transition animations
        Optional<StartupAnimationConfig> startupAnimation = parseStartupAnimation(root.get("startup"));
        Optional<StartupAnimationConfig> shutdownAnimation = parseStartupAnimation(root.get("shutdown"));

        return new HaloDefinition(id, model, animation, positioning, damping, hideOnSleep, displayInInvisible, schemaVersion, startupAnimation, shutdownAnimation);
    }

    // ------------------------------------------------------------------
    // Model & Layers (new format)
    // ------------------------------------------------------------------

    private HaloModel parseModel(JsonObject root, SchemaVersion version) {
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

        // Groups (replaces old flat "layers")
        List<HaloGroup> groups = new ArrayList<>();
        if (root.has("layers")) {
            for (JsonElement elem : root.getAsJsonArray("layers")) {
                groups.add(parseGroup(elem.getAsJsonObject()));
            }
        }
        // Backward compat: old "shape" field → single group with locked mode
        else if (root.has("shape")) {
            JsonObject shapeObj = root.getAsJsonObject("shape");
            groups.addAll(convertLegacyShape(shapeObj));
        }

        // SYNC mode: configurable angular offset (Euler YXZ in degrees)
        Quaternionf syncOffset = new Quaternionf(); // identity default
        if (mode == OrientationMode.SYNC && root.has("sync_offset")) {
            JsonArray offArr = root.getAsJsonArray("sync_offset");
            float offYaw   = (float) Math.toRadians(offArr.get(0).getAsDouble());
            float offPitch = (float) Math.toRadians(offArr.get(1).getAsDouble());
            float offRoll  = offArr.size() > 2 ? (float) Math.toRadians(offArr.get(2).getAsDouble()) : 0f;
            // Same YXZ order as group rotations: yaw (Y), pitch (X), roll (Z)
            syncOffset.rotateY(offYaw).rotateX(offPitch).rotateZ(offRoll);
        }

        return new HaloModel(mode, groups, syncOffset);
    }

    private HaloGroup parseGroup(JsonObject obj) {
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

        // Alpha / glow inheritance toggles (default true — descendants inherit
        // this group's effective alpha/glow multiplicatively). Set false to cut
        // the chain so this group's subtree starts fresh from 1.0.
        boolean inheritAlpha = !obj.has("inherit_alpha") || obj.get("inherit_alpha").getAsBoolean();
        boolean inheritGlow = !obj.has("inherit_glow") || obj.get("inherit_glow").getAsBoolean();

        // Group animation (per-group, visual-only, optional)
        Optional<LayerAnimation> groupAnim = parseLayerAnimation(obj.get("animation"));

        // Primitives: support "primitives" array (new) or "primitive" single object (backward compat)
        List<HaloPrimitive> primitives;
        if (obj.has("primitives")) {
            JsonArray primArr = obj.getAsJsonArray("primitives");
            primitives = new ArrayList<>(primArr.size());
            for (JsonElement elem : primArr) {
                primitives.add(parsePrimitive(elem.getAsJsonObject()));
            }
        } else if (obj.has("primitive")) {
            primitives = List.of(parsePrimitive(obj.getAsJsonObject("primitive")));
        } else {
            primitives = List.of();
        }

        // Children: optional recursive sub-groups
        List<HaloGroup> children = List.of();
        if (obj.has("children")) {
            JsonArray childArr = obj.getAsJsonArray("children");
            children = new ArrayList<>(childArr.size());
            for (JsonElement elem : childArr) {
                children.add(parseGroup(elem.getAsJsonObject()));
            }
        }

        return new HaloGroup(id, position, rotation, scale, primitives, glowing, inheritAlpha, inheritGlow, groupAnim, children);
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
        List<AnimationTerm> sx = parseAnimationTerms(animObj, "scale", "x");
        List<AnimationTerm> sy = parseAnimationTerms(animObj, "scale", "y");
        List<AnimationTerm> sz = parseAnimationTerms(animObj, "scale", "z");
        List<AnimationTerm> alphaTerms = parseScalarTerms(animObj, "alpha");
        List<AnimationTerm> glowTerms = parseScalarTerms(animObj, "glow");

        LayerAnimation result = new LayerAnimation(ox, oy, oz, ry, rp, rr, sx, sy, sz, alphaTerms, glowTerms);
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
        return parseTermArray(arr);
    }

    /**
     * Extract a list of {@link AnimationTerm}s from a flat scalar channel:
     * {@code parent.key → [...]} (used by {@code alpha} and {@code glow}).
     * Returns an empty list if the key is missing, null, or empty.
     */
    private List<AnimationTerm> parseScalarTerms(JsonObject parent, String key) {
        if (!parent.has(key) || parent.get(key).isJsonNull()) return List.of();
        return parseTermArray(parent.getAsJsonArray(key));
    }

    /**
     * Parse a JSON array of animation term objects into {@link AnimationTerm}s.
     * Term syntax is shared by axis channels and scalar channels.
     */
    private List<AnimationTerm> parseTermArray(JsonArray arr) {
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
                    t.has("start") ? t.get("start").getAsDouble() : 0.0,
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
            case "ring" -> parseRingPrimitive(obj);
            default -> throw new JsonParseException("Unknown primitive type: " + type);
        };
    }

    private BillboardPrimitive parseBillboardPrimitive(JsonObject obj) {
        Identifier texture = Identifier.tryParse(obj.get("texture").getAsString());
        Vector2f size = gson.fromJson(obj.get("size"), Vector2f.class);
        return new BillboardPrimitive(texture, size);
    }

    private RingPrimitive parseRingPrimitive(JsonObject obj) {
        // Outer texture: try "outer_texture" first, fall back to "texture"
        Identifier outerTexture;
        if (obj.has("outer_texture")) {
            outerTexture = Identifier.tryParse(obj.get("outer_texture").getAsString());
        } else {
            outerTexture = Identifier.tryParse(obj.get("texture").getAsString());
        }

        // Inner texture: optional, defaults to outer
        Identifier innerTexture = obj.has("inner_texture")
            ? Identifier.tryParse(obj.get("inner_texture").getAsString())
            : null;

        // Size: [radius, width]
        Vector2f size = gson.fromJson(obj.get("size"), Vector2f.class);

        // Segments: default 32
        int segments = obj.has("segments") ? obj.get("segments").getAsInt() : 32;

        return new RingPrimitive(outerTexture, innerTexture, size, segments);
    }

    /**
     * Convert old-style "shape" block to a list of layers for backward compatibility.
     */
    private List<HaloGroup> convertLegacyShape(JsonObject shapeObj) {
        List<HaloGroup> groups = new ArrayList<>();
        String type = shapeObj.get("type").getAsString();

        switch (type) {
            case "billboard" -> {
                BillboardPrimitive bp = parseBillboardPrimitive(shapeObj);
                groups.add(new HaloGroup(Vec3d.ZERO, bp));
            }
            case "multi_billboard" -> {
                for (JsonElement elem : shapeObj.getAsJsonArray("layers")) {
                    BillboardPrimitive bp = parseBillboardPrimitive(elem.getAsJsonObject());
                    groups.add(new HaloGroup(Vec3d.ZERO, bp));
                }
            }
            default -> throw new JsonParseException("Unknown legacy shape type: " + type);
        }
        return groups;
    }

    // --- Positioning & Damping (unchanged) ---

    private HaloPositioning parsePositioning(JsonObject obj) {
        if (obj == null) return new HaloPositioning(Vec3d.ZERO, 1.0);
        Vec3d offset = gson.fromJson(obj.get("offset"), Vec3d.class);
        double scale = obj.has("scale") ? obj.get("scale").getAsDouble() : 1.0;
        return new HaloPositioning(offset, scale);
    }

    private HaloDampingConfig parseDamping(JsonObject obj, boolean allowAngularMomentum) {
        if (obj == null) {
            return new HaloDampingConfig(0.15, 0.1, 3.0, 180.0, allowAngularMomentum, 0.3, 45.0);
        }
        double angularMomentumFactor = obj.has("angularMomentumFactor")
            ? obj.get("angularMomentumFactor").getAsDouble() : 0.3;
        double maxAngularMomentumDegrees = obj.has("maxAngularMomentumDegrees")
            ? obj.get("maxAngularMomentumDegrees").getAsDouble() : 45.0;
        return new HaloDampingConfig(
            obj.get("linearFactor").getAsDouble(),
            obj.get("angularFactor").getAsDouble(),
            obj.get("maxLinearDistance").getAsDouble(),
            obj.get("maxAngularDegrees").getAsDouble(),
            allowAngularMomentum,
            angularMomentumFactor,
            maxAngularMomentumDegrees
        );
    }

    // ------------------------------------------------------------------
    // Startup / Shutdown animation parsing
    // ------------------------------------------------------------------

    /**
     * Parse an optional startup/shutdown animation configuration from a JSON element.
     *
     * @param element the JSON element (may be null or missing)
     * @return the parsed config, or {@code Optional.empty()} if absent
     */
    private Optional<StartupAnimationConfig> parseStartupAnimation(JsonElement element) {
        if (element == null || element.isJsonNull() || !element.isJsonObject()) {
            return Optional.empty();
        }
        JsonObject obj = element.getAsJsonObject();

        // Parse segments array
        List<TransitionAnimation.TransitionSegment> segments = List.of();
        if (obj.has("segments") && obj.get("segments").isJsonArray()) {
            JsonArray arr = obj.getAsJsonArray("segments");
            List<TransitionAnimation.TransitionSegment> list = new ArrayList<>(arr.size());
            for (JsonElement elem : arr) {
                list.add(parseTransitionSegment(elem.getAsJsonObject()));
            }
            segments = Collections.unmodifiableList(list);
        }

        // Parse id_overrides object
        // Each value can be either:
        //   - An object with a "segments" array: { "segments": [...] }
        //   - A direct array (shorthand): [ ... ]
        Map<String, List<TransitionAnimation.TransitionSegment>> idOverrides = Map.of();
        if (obj.has("id_overrides") && obj.get("id_overrides").isJsonObject()) {
            JsonObject overridesObj = obj.getAsJsonObject("id_overrides");
            Map<String, List<TransitionAnimation.TransitionSegment>> map = new HashMap<>();
            for (Map.Entry<String, JsonElement> entry : overridesObj.entrySet()) {
                String groupId = entry.getKey();
                JsonElement value = entry.getValue();
                JsonArray arr;
                if (value.isJsonArray()) {
                    arr = value.getAsJsonArray();
                } else if (value.isJsonObject()) {
                    JsonObject valueObj = value.getAsJsonObject();
                    if (valueObj.has("segments") && valueObj.get("segments").isJsonArray()) {
                        arr = valueObj.getAsJsonArray("segments");
                    } else {
                        LOG.warn("[Halo] id_override '{}' has no 'segments' array, skipping", groupId);
                        continue;
                    }
                } else {
                    LOG.warn("[Halo] id_override '{}' has unexpected type, skipping", groupId);
                    continue;
                }
                List<TransitionAnimation.TransitionSegment> list = new ArrayList<>(arr.size());
                for (JsonElement elem : arr) {
                    list.add(parseTransitionSegment(elem.getAsJsonObject()));
                }
                map.put(groupId, Collections.unmodifiableList(list));
            }
            idOverrides = Collections.unmodifiableMap(map);
        }

        if (segments.isEmpty() && idOverrides.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(new StartupAnimationConfig(segments, idOverrides));
    }

    /**
     * Parse a single transition segment from JSON.
     */
    private TransitionAnimation.TransitionSegment parseTransitionSegment(JsonObject obj) {
        double duration = obj.get("duration").getAsDouble();
        EasingType easing = EasingType.LINEAR;
        if (obj.has("easing")) {
            easing = EasingType.fromString(obj.get("easing").getAsString());
        }

        TransitionAnimation.TransitionProperty offset = obj.has("offset")
            ? parseTransitionProperty(obj.getAsJsonObject("offset"), 3)
            : null;
        TransitionAnimation.TransitionProperty scale = obj.has("scale")
            ? parseTransitionProperty(obj.getAsJsonObject("scale"), 3)
            : null;
        TransitionAnimation.TransitionProperty opacity = obj.has("opacity")
            ? parseTransitionProperty(obj.getAsJsonObject("opacity"), 1)
            : null;

        return new TransitionAnimation.TransitionSegment(duration, easing, offset, scale, opacity);
    }

    /**
     * Parse a transition property (from/to arrays) from JSON.
     * Handles both array-valued properties (offset, scale) and single-valued
     * properties (opacity, stored as float[1]).
     *
     * @param obj       the JSON object for this property
     * @param componentCount expected number of components (3 for offset/scale, 1 for opacity)
     */
    private TransitionAnimation.TransitionProperty parseTransitionProperty(JsonObject obj, int componentCount) {
        float[] from = null;
        float[] to = null;
        Double propertyDuration = null;
        EasingType propertyEasing = null;

        if (obj.has("from") && !obj.get("from").isJsonNull()) {
            from = parseFloatArray(obj.get("from"), componentCount);
        }
        if (obj.has("to") && !obj.get("to").isJsonNull()) {
            to = parseFloatArray(obj.get("to"), componentCount);
        }
        if (obj.has("duration") && !obj.get("duration").isJsonNull()) {
            propertyDuration = obj.get("duration").getAsDouble();
        }
        if (obj.has("easing") && !obj.get("easing").isJsonNull()) {
            propertyEasing = EasingType.fromString(obj.get("easing").getAsString());
        }

        if (from == null && to == null) {
            return null;
        }
        return new TransitionAnimation.TransitionProperty(from, to, propertyDuration, propertyEasing);
    }

    /**
     * Parse a JSON element into a float array of the given size.
     * Handles both JSON arrays and single numbers (for opacity).
     */
    private float[] parseFloatArray(JsonElement element, int componentCount) {
        if (element.isJsonArray()) {
            JsonArray arr = element.getAsJsonArray();
            float[] result = new float[Math.max(arr.size(), componentCount)];
            for (int i = 0; i < arr.size(); i++) {
                result[i] = arr.get(i).getAsFloat();
            }
            return result;
        } else {
            // Single number (e.g. opacity "from": 0.0)
            return new float[]{element.getAsFloat()};
        }
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
