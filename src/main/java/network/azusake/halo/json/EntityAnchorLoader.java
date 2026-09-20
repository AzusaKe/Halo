package network.azusake.halo.json;

import network.azusake.halo.HaloMod;
import network.azusake.halo.data.EntityAnchorProfile;
import com.google.gson.JsonElement;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;
import net.neoforged.bus.api.IEventBus;
import network.azusake.halo.core.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.*;

/**
 * NeoForge resource reload listener that scans {@code entity_anchors/} in all
 * datapacks (server) and resource packs (client), parses every {@code .json}
 * file into an {@link EntityAnchorProfile}, and exposes them through a static
 * registry.
 *
 * <p>Loaded profiles are stored in a static {@link LinkedHashMap} keyed by
 * entity type {@link Identifier}.  Client-pack entries override server-pack
 * entries with the same entity key, preserving the established resource-loading
 * precedence.</p>
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
        NeoForge.EVENT_BUS.addListener((AddServerReloadListenersEvent event) ->
            event.addListener(net.minecraft.resources.Identifier.fromNamespaceAndPath("halo", "entity_anchors"), new ServerListener()));
        LOG.info("EntityAnchorLoader registered for SERVER_DATA");
    }

    /**
     * Register the client (resource-pack) listener.  Safe to call more than once.
     */
    public static void registerClientResources(IEventBus modBus) {
        if (clientRegistered) {
            return;
        }
        clientRegistered = true;
        modBus.addListener((AddClientReloadListenersEvent event) ->
            event.addListener(net.minecraft.resources.Identifier.fromNamespaceAndPath("halo", "entity_anchors_client"), new ClientListener()));
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
        Map<net.minecraft.resources.Identifier, net.minecraft.server.packs.resources.Resource> resources = manager.listResources(
            PROFILES_PATH,
            id -> id.getPath().endsWith(".json")
        );

        LOG.info("Found {} entity anchor profile(s) to load", resources.size());

        for (Map.Entry<net.minecraft.resources.Identifier, net.minecraft.server.packs.resources.Resource> entry : resources.entrySet()) {
            var fileId = entry.getKey();
            try (var input = entry.getValue().open()) {
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

    private static class ServerListener extends SimplePreparableReloadListener<Void> {
        @Override protected Void prepare(ResourceManager manager, ProfilerFiller profiler) { return null; }
        @Override protected void apply(Void ignored, ResourceManager manager, ProfilerFiller profiler) {
            EntityAnchorLoader.reload(manager, serverLoadedIds);
        }
    }

    private static class ClientListener extends SimplePreparableReloadListener<Void> {
        @Override protected Void prepare(ResourceManager manager, ProfilerFiller profiler) { return null; }
        @Override protected void apply(Void ignored, ResourceManager manager, ProfilerFiller profiler) {
            EntityAnchorLoader.reload(manager, clientLoadedIds);
        }
    }

    // ------------------------------------------------------------------
    // Reusable type adapters (mirrored from HaloDefinitionDeserializer)
    // ------------------------------------------------------------------

    public static void clearServerResources() { CATALOG.replace(0, Map.of()); }
}
