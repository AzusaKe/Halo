package network.azusake.halo.json;

import com.google.gson.*;
import network.azusake.halo.HaloMod;
import network.azusake.halo.data.EntityAnchorProfile;
import network.azusake.halo.data.PoseAnchor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.InputStreamReader;
import java.util.*;

/** Loads entity anchor profiles from data/resource packs. */
public final class EntityAnchorLoader {
    private static final Logger LOG = LoggerFactory.getLogger(HaloMod.MOD_ID);
    private static final String PATH = "entity_anchors";
    private static final Map<ResourceLocation, EntityAnchorProfile> PROFILES = new LinkedHashMap<>();
    private static final Set<ResourceLocation> SERVER_IDS = new LinkedHashSet<>();
    private static final Set<ResourceLocation> CLIENT_IDS = new LinkedHashSet<>();
    private static volatile boolean serverRegistered;
    private static volatile boolean clientRegistered;
    private static final Gson GSON = new GsonBuilder()
        .registerTypeAdapter(Vec3.class, new Vec3Adapter())
        .registerTypeAdapter(ResourceLocation.class, new ResourceLocationAdapter()).create();
    private EntityAnchorLoader() {}
    public static void register() {
        if (serverRegistered) return;
        serverRegistered = true;
        MinecraftForge.EVENT_BUS.addListener((AddReloadListenerEvent event) ->
            event.addListener(new Loader(SERVER_IDS)));
    }
    public static void registerClientResources() {
        if (clientRegistered) return;
        clientRegistered = true;
        MinecraftForge.EVENT_BUS.addListener((RegisterClientReloadListenersEvent event) ->
            event.registerReloadListener(new Loader(CLIENT_IDS)));
    }
    public static void registerClientResources(IEventBus bus) {
        if (clientRegistered) return;
        clientRegistered = true;
        bus.addListener((RegisterClientReloadListenersEvent event) ->
            event.registerReloadListener(new Loader(CLIENT_IDS)));
    }
    public static Optional<EntityAnchorProfile> getProfile(ResourceLocation id) { return Optional.ofNullable(PROFILES.get(id)); }
    private static void reload(ResourceManager manager, Set<ResourceLocation> ownIds) {
        ownIds.forEach(PROFILES::remove);
        ownIds.clear();
        Map<ResourceLocation, Resource> resources = manager.listResources(PATH, id -> id.getPath().endsWith(".json"));
        for (Map.Entry<ResourceLocation, Resource> entry : resources.entrySet()) {
            try (InputStreamReader reader = new InputStreamReader(entry.getValue().open())) {
                EntityAnchorProfile profile = deserialize(JsonParser.parseReader(reader));
                PROFILES.put(profile.entity(), profile);
                ownIds.add(profile.entity());
            } catch (Exception e) {
                LOG.warn("Skipping malformed entity anchor profile {}: {}", entry.getKey(), e.getMessage());
            }
        }
    }
    static EntityAnchorProfile deserialize(JsonElement json) {
        JsonObject root = json.getAsJsonObject();
        ResourceLocation entity = new ResourceLocation(root.get("entity").getAsString());
        String defaultPose = root.get("default_pose").getAsString();
        Map<String, PoseAnchor> poses = new LinkedHashMap<>();
        for (var entry : root.getAsJsonObject("poses").entrySet()) {
            JsonObject pose = entry.getValue().getAsJsonObject();
            Vec3 pivot = GSON.fromJson(pose.get("pivot"), Vec3.class);
            Vec3 head = GSON.fromJson(pose.get("head_center_vector"), Vec3.class);
            poses.put(entry.getKey(), new PoseAnchor(pivot, head));
        }
        return new EntityAnchorProfile(entity, defaultPose, Collections.unmodifiableMap(poses));
    }
    private static final class Loader extends SimplePreparableReloadListener<Void> {
        private final Set<ResourceLocation> ids;
        private Loader(Set<ResourceLocation> ids) { this.ids = ids; }
        @Override protected Void prepare(ResourceManager manager, ProfilerFiller profiler) { return null; }
        @Override protected void apply(Void ignored, ResourceManager manager, ProfilerFiller profiler) {
            EntityAnchorLoader.reload(manager, ids);
        }
    }
    static class Vec3Adapter implements JsonDeserializer<Vec3> {
        @Override public Vec3 deserialize(JsonElement json, java.lang.reflect.Type type, JsonDeserializationContext context) {
            JsonArray a = json.getAsJsonArray();
            return new Vec3(a.get(0).getAsDouble(), a.get(1).getAsDouble(), a.get(2).getAsDouble());
        }
    }
    static class ResourceLocationAdapter implements JsonDeserializer<ResourceLocation> {
        @Override public ResourceLocation deserialize(JsonElement json, java.lang.reflect.Type type, JsonDeserializationContext context) {
            return new ResourceLocation(json.getAsString());
        }
    }
}
