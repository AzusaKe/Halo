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
import network.azusake.halo.core.Identifier;
import network.azusake.halo.core.Vec3d;
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

    private static final network.azusake.halo.core.DefinitionCatalog<EntityAnchorProfile> CATALOG = new network.azusake.halo.core.DefinitionCatalog<>();

    /** IDs loaded by the server listener. */
    private static final Set<Identifier> serverLoadedIds = new LinkedHashSet<>();

    /** IDs loaded by the client listener. */
    private static final Set<Identifier> clientLoadedIds = new LinkedHashSet<>();

    private static volatile boolean serverRegistered;
    private static volatile boolean clientRegistered;

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
        return Optional.ofNullable(CATALOG.snapshot().get(entityId));
    }

    // ------------------------------------------------------------------
    // Reload logic
    // ------------------------------------------------------------------

    private static void reload(ResourceManager manager, Set<Identifier> sourceSet) {
        Map<Identifier, EntityAnchorProfile> loaded = new LinkedHashMap<>();
        Map<net.minecraft.util.Identifier, net.minecraft.resource.Resource> resources = manager.findResources(
            PROFILES_PATH,
            id -> id.getPath().endsWith(".json")
        );

        LOG.info("Found {} entity anchor profile(s) to load", resources.size());

        for (Map.Entry<net.minecraft.util.Identifier, net.minecraft.resource.Resource> entry : resources.entrySet()) {
            var fileId = entry.getKey();
            try (var input = entry.getValue().getInputStream()) {
                EntityAnchorProfile profile = EntityAnchorParser.parse(new String(
                    input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));

                loaded.put(profile.entity(), profile);

                LOG.info("  Loaded entity anchor profile: {} ({} pose(s))",
                    profile.entity(), profile.poses().size());
            } catch (Exception e) {
                LOG.warn("  Skipping malformed entity anchor profile {}: {}", fileId, e.getMessage());
            }
        }

        CATALOG.replace(sourceSet == clientLoadedIds ? 1 : 0, loaded);
        LOG.info("Entity anchor registry now holds {} entries", CATALOG.snapshot().size());
    }

    // ------------------------------------------------------------------
    // JSON deserialization
    // ------------------------------------------------------------------

    static EntityAnchorProfile deserialize(JsonElement json) { return EntityAnchorParser.deserialize(json); }

    // ------------------------------------------------------------------
    // Resource listeners
    // ------------------------------------------------------------------

    private static class ServerListener implements SimpleSynchronousResourceReloadListener {
        @Override
        public net.minecraft.util.Identifier getFabricId() {
            return new net.minecraft.util.Identifier(HaloMod.MOD_ID, "entity_anchors");
        }

        @Override
        public void reload(ResourceManager manager) {
            EntityAnchorLoader.reload(manager, serverLoadedIds);
        }
    }

    private static class ClientListener implements SimpleSynchronousResourceReloadListener {
        @Override
        public net.minecraft.util.Identifier getFabricId() {
            return new net.minecraft.util.Identifier(HaloMod.MOD_ID, "entity_anchors_client");
        }

        @Override
        public void reload(ResourceManager manager) {
            EntityAnchorLoader.reload(manager, clientLoadedIds);
        }
    }

    // ------------------------------------------------------------------
    // Reusable type adapters (mirrored from HaloDefinitionDeserializer)
    // ------------------------------------------------------------------

    public static void clearServerResources() { CATALOG.replace(0, Map.of()); }
}
