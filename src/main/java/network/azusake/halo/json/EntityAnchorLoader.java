package network.azusake.halo.json;

import network.azusake.halo.HaloMod;
import network.azusake.halo.data.EntityAnchorProfile;
import network.azusake.halo.data.PoseAnchor;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStreamReader;
import java.util.*;

/**
 * Fabric resource reload listener that scans {@code entity_anchors/} in all
 * datapacks (server) and resource packs (client), parses every {@code .json}
 * file into an {@link EntityAnchorProfile}, and exposes them through a static
 * registry.
 *
 * <p>Loaded profiles are stored in a static {@link LinkedHashMap} keyed by
 * entity type {@link Identifier}.  Client-pack entries override server-pack
 * entries with the same entity key, matching the Fabric resource-loading
 * convention.</p>
 *
 * <p>Mirrors {@link HaloJsonLoader} in registration pattern, scan path
 * convention (both {@code SERVER_DATA} and {@code CLIENT_RESOURCES}), and
 * {@code /reload} behaviour.</p>
 */
public final class EntityAnchorLoader {

    private static final Logger LOG = LoggerFactory.getLogger(HaloMod.MOD_ID);
    private static final String PROFILES_PATH = "entity_anchors";

    private static final Map<Identifier, EntityAnchorProfile> PROFILES = new LinkedHashMap<>();

    /** IDs loaded by the server listener. */
    private static final Set<Identifier> serverLoadedIds = new LinkedHashSet<>();

    /** IDs loaded by the client listener. */
    private static final Set<Identifier> clientLoadedIds = new LinkedHashSet<>();

    private static volatile boolean serverRegistered;
    private static volatile boolean clientRegistered;

    // Gson: reuse HaloDefinitionDeserializer's Vec3dAdapter approach
    private static final Gson GSON = new GsonBuilder()
        .registerTypeAdapter(Vec3d.class, new Vec3dAdapter())
        .registerTypeAdapter(Identifier.class, new IdentifierAdapter())
        .create();

    private EntityAnchorLoader() { /* utility */ }

    // ------------------------------------------------------------------
    // Registration
    // ------------------------------------------------------------------

    /**
     * Register the server (data-pack) listener.  Safe to call more than once.
     */
    public static void register() {
        if (serverRegistered) {
            return;
        }
        serverRegistered = true;
        ResourceManagerHelper.get(ResourceType.SERVER_DATA)
            .registerReloadListener(new ServerListener());
        LOG.info("EntityAnchorLoader registered for SERVER_DATA");
    }

    /**
     * Register the client (resource-pack) listener.  Safe to call more than once.
     */
    public static void registerClientResources() {
        if (clientRegistered) {
            return;
        }
        clientRegistered = true;
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES)
            .registerReloadListener(new ClientListener());
        LOG.info("EntityAnchorLoader registered for CLIENT_RESOURCES");
    }

    // ------------------------------------------------------------------
    // Registry access
    // ------------------------------------------------------------------

    /**
     * Look up a single profile by entity type identifier.
     */
    public static Optional<EntityAnchorProfile> getProfile(Identifier entityId) {
        return Optional.ofNullable(PROFILES.get(entityId));
    }

    // ------------------------------------------------------------------
    // Reload logic
    // ------------------------------------------------------------------

    private static void reload(ResourceManager manager, Set<Identifier> sourceSet) {
        // Remove only entries previously loaded by this source
        for (Identifier id : sourceSet) {
            PROFILES.remove(id);
        }
        sourceSet.clear();

        Map<Identifier, net.minecraft.resource.Resource> resources = manager.findResources(
            PROFILES_PATH,
            id -> id.getPath().endsWith(".json")
        );

        LOG.info("Found {} entity anchor profile(s) to load", resources.size());

        for (Map.Entry<Identifier, net.minecraft.resource.Resource> entry : resources.entrySet()) {
            Identifier fileId = entry.getKey();
            try (InputStreamReader reader = new InputStreamReader(entry.getValue().getInputStream())) {
                JsonElement root = JsonParser.parseReader(reader);
                EntityAnchorProfile profile = deserialize(root);

                PROFILES.put(profile.entity(), profile);
                sourceSet.add(profile.entity());
                LOG.info("  Loaded entity anchor profile: {} ({} pose(s))",
                    profile.entity(), profile.poses().size());
            } catch (Exception e) {
                LOG.warn("  Skipping malformed entity anchor profile {}: {}", fileId, e.getMessage());
            }
        }

        LOG.info("Entity anchor registry now holds {} entries", PROFILES.size());
    }

    // ------------------------------------------------------------------
    // JSON deserialization
    // ------------------------------------------------------------------

    static EntityAnchorProfile deserialize(JsonElement json) {
        var root = json.getAsJsonObject();

        Identifier entity = Identifier.tryParse(root.get("entity").getAsString());
        String defaultPose = root.get("default_pose").getAsString();

        Map<String, PoseAnchor> poses = new LinkedHashMap<>();
        var posesObj = root.getAsJsonObject("poses");
        for (var poseEntry : posesObj.entrySet()) {
            String poseKey = poseEntry.getKey();
            var poseObj = poseEntry.getValue().getAsJsonObject();

            Vec3d pivot = GSON.fromJson(poseObj.get("pivot"), Vec3d.class);
            Vec3d headCenterVec = GSON.fromJson(poseObj.get("head_center_vector"), Vec3d.class);

            poses.put(poseKey, new PoseAnchor(pivot, headCenterVec));
        }

        return new EntityAnchorProfile(entity, defaultPose, Collections.unmodifiableMap(poses));
    }

    // ------------------------------------------------------------------
    // Resource listeners
    // ------------------------------------------------------------------

    private static class ServerListener implements SimpleSynchronousResourceReloadListener {
        @Override
        public Identifier getFabricId() {
            return new Identifier(HaloMod.MOD_ID, "entity_anchors");
        }

        @Override
        public void reload(ResourceManager manager) {
            EntityAnchorLoader.reload(manager, serverLoadedIds);
        }
    }

    private static class ClientListener implements SimpleSynchronousResourceReloadListener {
        @Override
        public Identifier getFabricId() {
            return new Identifier(HaloMod.MOD_ID, "entity_anchors_client");
        }

        @Override
        public void reload(ResourceManager manager) {
            EntityAnchorLoader.reload(manager, clientLoadedIds);
        }
    }

    // ------------------------------------------------------------------
    // Reusable type adapters (mirrored from HaloDefinitionDeserializer)
    // ------------------------------------------------------------------

    static class Vec3dAdapter implements com.google.gson.JsonDeserializer<Vec3d> {
        @Override
        public Vec3d deserialize(JsonElement json, java.lang.reflect.Type typeOfT,
                                  com.google.gson.JsonDeserializationContext context) {
            var arr = json.getAsJsonArray();
            return new Vec3d(arr.get(0).getAsDouble(), arr.get(1).getAsDouble(), arr.get(2).getAsDouble());
        }
    }

    static class IdentifierAdapter implements com.google.gson.JsonDeserializer<Identifier> {
        @Override
        public Identifier deserialize(JsonElement json, java.lang.reflect.Type typeOfT,
                                       com.google.gson.JsonDeserializationContext context) {
            return Identifier.tryParse(json.getAsString());
        }
    }
}
