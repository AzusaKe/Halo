package network.azusake.halo.json;

import network.azusake.halo.HaloMod;
import network.azusake.halo.data.EntityAnchorProfile;
import network.azusake.halo.data.PoseAnchor;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStreamReader;
import java.util.*;

/**
 * NeoForge resource reload listener that scans {@code entity_anchors/} in all
 * datapacks (server) and resource packs (client), parses every {@code .json}
 * file into an {@link EntityAnchorProfile}, and exposes them through a static
 * registry.
 *
 * <p>Loaded profiles are stored in a static {@link LinkedHashMap} keyed by
 * entity type {@link ResourceLocation}.  Client-pack entries override server-pack
 * entries with the same entity key, matching the NeoForge resource-loading
 * convention.</p>
 *
 * <p>Mirrors {@link HaloJsonLoader} in registration pattern, scan path
 * convention (both {@code SERVER_DATA} and {@code CLIENT_RESOURCES}), and
 * {@code /reload} behaviour.</p>
 */
public final class EntityAnchorLoader {

    private static final Logger LOG = LoggerFactory.getLogger(HaloMod.MOD_ID);
    private static final String PROFILES_PATH = "entity_anchors";

    private static final Map<ResourceLocation, EntityAnchorProfile> PROFILES = new LinkedHashMap<>();

    /** IDs loaded by the server listener. */
    private static final Set<ResourceLocation> serverLoadedIds = new LinkedHashSet<>();

    /** IDs loaded by the client listener. */
    private static final Set<ResourceLocation> clientLoadedIds = new LinkedHashSet<>();

    private static volatile boolean serverRegistered;
    private static volatile boolean clientRegistered;

    // Gson: reuse HaloDefinitionDeserializer's Vec3dAdapter approach
    private static final Gson GSON = new GsonBuilder()
        .registerTypeAdapter(Vec3.class, new Vec3dAdapter())
        .registerTypeAdapter(ResourceLocation.class, new ResourceLocationAdapter())
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
        // AddReloadListenerEvent fires on every data-pack reload; the
        // listener is re-added to each fresh reload's listener map, so the
        // registration is safe to keep for the lifetime of the game.
        NeoForge.EVENT_BUS.addListener(AddReloadListenerEvent.class, event ->
            event.addListener(new ServerListener()));
        LOG.info("EntityAnchorLoader registered for SERVER_DATA");
    }

    /**
     * Register the client (resource-pack) listener.  Safe to call more than once.
     */
    public static void registerClientResources(IEventBus modEventBus) {
        if (clientRegistered) {
            return;
        }
        clientRegistered = true;
        // RegisterClientReloadListenersEvent is an IModBusEvent fired once while the
        // Minecraft instance is constructed, on the logical client.
        modEventBus.addListener(RegisterClientReloadListenersEvent.class, event ->
            event.registerReloadListener(new ClientListener()));
        LOG.info("EntityAnchorLoader registered for CLIENT_RESOURCES");
    }

    // ------------------------------------------------------------------
    // Registry access
    // ------------------------------------------------------------------

    /**
     * Look up a single profile by entity type identifier.
     */
    public static Optional<EntityAnchorProfile> getProfile(ResourceLocation entityId) {
        return Optional.ofNullable(PROFILES.get(entityId));
    }

    // ------------------------------------------------------------------
    // Reload logic
    // ------------------------------------------------------------------

    private static void reload(ResourceManager manager, Set<ResourceLocation> sourceSet) {
        // Remove only entries previously loaded by this source
        for (ResourceLocation id : sourceSet) {
            PROFILES.remove(id);
        }
        sourceSet.clear();

        Map<ResourceLocation, net.minecraft.server.packs.resources.Resource> resources = manager.listResources(
            PROFILES_PATH,
            id -> id.getPath().endsWith(".json")
        );

        LOG.info("Found {} entity anchor profile(s) to load", resources.size());

        for (Map.Entry<ResourceLocation, net.minecraft.server.packs.resources.Resource> entry : resources.entrySet()) {
            ResourceLocation fileId = entry.getKey();
            try (InputStreamReader reader = new InputStreamReader(entry.getValue().open())) {
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

        ResourceLocation entity = ResourceLocation.tryParse(root.get("entity").getAsString());
        String defaultPose = root.get("default_pose").getAsString();

        Map<String, PoseAnchor> poses = new LinkedHashMap<>();
        var posesObj = root.getAsJsonObject("poses");
        for (var poseEntry : posesObj.entrySet()) {
            String poseKey = poseEntry.getKey();
            var poseObj = poseEntry.getValue().getAsJsonObject();

            Vec3 pivot = GSON.fromJson(poseObj.get("pivot"), Vec3.class);
            Vec3 headCenterVec = GSON.fromJson(poseObj.get("head_center_vector"), Vec3.class);

            poses.put(poseKey, new PoseAnchor(pivot, headCenterVec));
        }

        return new EntityAnchorProfile(entity, defaultPose, Collections.unmodifiableMap(poses));
    }

    // ------------------------------------------------------------------
    // Resource listeners
    // ------------------------------------------------------------------

    private static class ServerListener extends SimplePreparableReloadListener<Void> {
        @Override
        protected Void prepare(ResourceManager manager, ProfilerFiller profiler) {
            return null;
        }

        @Override
        protected void apply(Void data, ResourceManager manager, ProfilerFiller profiler) {
            EntityAnchorLoader.reload(manager, serverLoadedIds);
        }
    }

    private static class ClientListener extends SimplePreparableReloadListener<Void> {
        @Override
        protected Void prepare(ResourceManager manager, ProfilerFiller profiler) {
            return null;
        }

        @Override
        protected void apply(Void data, ResourceManager manager, ProfilerFiller profiler) {
            EntityAnchorLoader.reload(manager, clientLoadedIds);
        }
    }

    // ------------------------------------------------------------------
    // Reusable type adapters (mirrored from HaloDefinitionDeserializer)
    // ------------------------------------------------------------------

    static class Vec3dAdapter implements com.google.gson.JsonDeserializer<Vec3> {
        @Override
        public Vec3 deserialize(JsonElement json, java.lang.reflect.Type typeOfT,
                                  com.google.gson.JsonDeserializationContext context) {
            var arr = json.getAsJsonArray();
            return new Vec3(arr.get(0).getAsDouble(), arr.get(1).getAsDouble(), arr.get(2).getAsDouble());
        }
    }

    static class ResourceLocationAdapter implements com.google.gson.JsonDeserializer<ResourceLocation> {
        @Override
        public ResourceLocation deserialize(JsonElement json, java.lang.reflect.Type typeOfT,
                                       com.google.gson.JsonDeserializationContext context) {
            return ResourceLocation.tryParse(json.getAsString());
        }
    }
}
