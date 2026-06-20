package network.azusake.halo.json;

import network.azusake.halo.HaloMod;
import network.azusake.halo.data.HaloDefinition;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStreamReader;
import java.util.*;

/**
 * Fabric resource reload listener that scans {@code halo_definitions/} in all
 * datapacks (server) and resource packs (client), parses every {@code .json}
 * file into a {@link HaloDefinition}, and exposes them through a static registry.
 */
public final class HaloJsonLoader {

    private static final Logger LOG = LoggerFactory.getLogger(HaloMod.MOD_ID);
    private static final String DEFINITIONS_PATH = "halo_definitions";

    private static final Map<Identifier, HaloDefinition> DEFINITIONS = new LinkedHashMap<>();
    private static final HaloDefinitionDeserializer DESERIALIZER = new HaloDefinitionDeserializer();

    /** Definitions loaded by the server (data-pack) listener.  Cleared and repopulated on reload. */
    private static final Set<Identifier> serverLoadedIds = new LinkedHashSet<>();

    /** Definitions loaded by the client (resource-pack) listener.  Cleared and repopulated on reload. */
    private static final Set<Identifier> clientLoadedIds = new LinkedHashSet<>();

    private HaloJsonLoader() {
        // utility class
    }

    /** Prevent double-registration of each listener type. */
    private static volatile boolean serverRegistered;
    private static volatile boolean clientRegistered;

    /**
     * Register resource reload listener for server data packs.
     * <p>Halo definitions live under {@code data/&lt;ns&gt;/halo_definitions/}
     * and are loaded by the server-side resource manager.</p>
     * <p>Safe to call more than once — subsequent calls are no-ops.</p>
     */
    public static void register() {
        if (serverRegistered) {
            return;
        }
        serverRegistered = true;
        ResourceManagerHelper.get(ResourceType.SERVER_DATA)
            .registerReloadListener(new ServerListener());

        LOG.info("HaloJsonLoader registered for SERVER_DATA");
    }

    /**
     * Register resource reload listener for client resource packs.
     * <p>Called from {@code HaloModClient} so that halo definitions are also
     * available on the logical client for rendering.  In single-player the
     * server-side listener typically loads first; this ensures client-only
     * packs can also contribute definitions.</p>
     * <p>Safe to call more than once — subsequent calls are no-ops.</p>
     */
    public static void registerClientResources() {
        if (clientRegistered) {
            return;
        }
        clientRegistered = true;
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES)
            .registerReloadListener(new ClientListener());

        LOG.info("HaloJsonLoader registered for CLIENT_RESOURCES");
    }

    /**
     * Return an unmodifiable view of all currently loaded definitions.
     */
    public static Map<Identifier, HaloDefinition> getDefinitions() {
        return Collections.unmodifiableMap(DEFINITIONS);
    }

    /**
     * Look up a single definition by id.
     */
    public static Optional<HaloDefinition> getDefinition(Identifier id) {
        return Optional.ofNullable(DEFINITIONS.get(id));
    }

    // ------------------------------------------------------------------
    // Reload logic (shared)
    // ------------------------------------------------------------------

    /**
     * Scan and load definitions from the given resource manager, tracking them
     * in {@code sourceSet} so that a later reload from the same source replaces
     * only its own definitions — definitions loaded by the other source are
     * left untouched.
     *
     * @param manager   the resource manager to scan
     * @param sourceSet the set of IDs previously loaded by this source;
     *                  will be cleared and repopulated with the new IDs
     */
    private static void reload(ResourceManager manager, Set<Identifier> sourceSet) {
        // Remove only the definitions that were previously loaded from this source
        for (Identifier id : sourceSet) {
            DEFINITIONS.remove(id);
        }
        sourceSet.clear();

        Map<Identifier, net.minecraft.resource.Resource> resources = manager.findResources(
            DEFINITIONS_PATH,
            id -> id.getPath().endsWith(".json")
        );

        LOG.info("Found {} halo definition(s) to load", resources.size());

        for (Map.Entry<Identifier, net.minecraft.resource.Resource> entry : resources.entrySet()) {
            Identifier fileId = entry.getKey();
            try (InputStreamReader reader = new InputStreamReader(entry.getValue().getInputStream())) {
                JsonElement root = JsonParser.parseReader(reader);
                HaloDefinition def = DESERIALIZER.deserialize(root, HaloDefinition.class, null);

                DEFINITIONS.put(def.id(), def);
                sourceSet.add(def.id());
                LOG.info("  Loaded halo definition: {}", def.id());
            } catch (Exception e) {
                LOG.warn("  Skipping malformed halo definition {}: {}", fileId, e.getMessage());
            }
        }

        LOG.info("Halo definition registry now holds {} entries", DEFINITIONS.size());
    }

    // ------------------------------------------------------------------
    // Listener implementations
    // ------------------------------------------------------------------

    private static class ServerListener implements SimpleSynchronousResourceReloadListener {
        @Override
        public Identifier getFabricId() {
            return new Identifier(HaloMod.MOD_ID, "halo_definitions");
        }

        @Override
        public void reload(ResourceManager manager) {
            HaloJsonLoader.reload(manager, serverLoadedIds);
        }
    }

    private static class ClientListener implements SimpleSynchronousResourceReloadListener {
        @Override
        public Identifier getFabricId() {
            return new Identifier(HaloMod.MOD_ID, "halo_definitions_client");
        }

        @Override
        public void reload(ResourceManager manager) {
            HaloJsonLoader.reload(manager, clientLoadedIds);
        }
    }
}
