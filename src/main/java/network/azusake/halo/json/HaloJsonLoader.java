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
import java.util.concurrent.ConcurrentHashMap;

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

    /**
     * Definitions loaded by the client's {@code SERVER_DATA} listener.
     * On a dedicated-server client only {@code SERVER_DATA} has access to the
     * mod's bundled {@code data/halo/halo_definitions/} files — {@code CLIENT_RESOURCES}
     * only scans {@code assets/}.  This set isolates client-side data-pack definitions
     * from actual server-side (integrated-server) definitions so reloads don't cross-talk.
     */
    private static final Set<Identifier> clientServerDataIds = new LinkedHashSet<>();

    private HaloJsonLoader() {
        // utility class
    }

    /** Prevent double-registration of each listener type. */
    private static volatile boolean serverRegistered;
    private static volatile boolean clientRegistered;
    private static volatile boolean clientServerDataRegistered;

    // ------------------------------------------------------------------
    // Client-reported definition IDs (C2S defs_report)
    // ------------------------------------------------------------------

    /**
     * Per-player set of halo definition IDs reported by that client.
     * Written on the server thread, read by Brigadier commands (also server
     * thread), so a plain {@link java.util.concurrent.ConcurrentHashMap} gives
     * more than enough safety.
     */
    private static final Map<UUID, Set<Identifier>> clientReportedDefs = new ConcurrentHashMap<>(8);

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
     * Register a SERVER_DATA resource reload listener on the physical client.
     *
     * <p>This is required on a dedicated-server client because the mod's bundled
     * halo-definition JSON files live under {@code data/halo/halo_definitions/},
     * which belongs to the {@code SERVER_DATA} resource type.  The
     * {@code CLIENT_RESOURCES} listener (registered above) only scans
     * {@code assets/} and will never see those files.  Without this listener a
     * dedicated-server client has zero halo definitions loaded and every halo
     * falls through to the magenta placeholder.</p>
     *
     * <p>On an integrated server the {@code HaloMod#onInitialize} listener
     * already covers {@code SERVER_DATA}, so this listener is an <em>extra</em>
     * client-side pass.  Its IDs are tracked separately
     * ({@link #clientServerDataIds}) so that reloads do not interfere with
     * the server-managed registry.</p>
     *
     * <p>Safe to call more than once — subsequent calls are no-ops.</p>
     */
    public static void registerClientServerDataResources() {
        if (clientServerDataRegistered) {
            return;
        }
        clientServerDataRegistered = true;
        ResourceManagerHelper.get(ResourceType.SERVER_DATA)
            .registerReloadListener(new ClientServerDataListener());

        LOG.info("HaloJsonLoader registered for SERVER_DATA (client-side)");
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
    // Client-reported definition IDs
    // ------------------------------------------------------------------

    /**
     * Store a client's reported definition IDs (called from the C2S handler).
     */
    public static void putClientReportedDefs(UUID playerUuid, Set<Identifier> ids) {
        clientReportedDefs.put(playerUuid, Collections.unmodifiableSet(new LinkedHashSet<>(ids)));
        LOG.debug("Client {} reported {} definition(s)", playerUuid, ids.size());
    }

    /**
     * Remove a client's reported definitions (called on disconnect).
     */
    public static void removeClientReportedDefs(UUID playerUuid) {
        clientReportedDefs.remove(playerUuid);
        LOG.debug("Removed client-reported definitions for {}", playerUuid);
    }

    /**
     * Return the union of all client-reported definition IDs.
     */
    public static Set<Identifier> getClientReportedDefIds() {
        Set<Identifier> all = new LinkedHashSet<>();
        for (var set : clientReportedDefs.values()) {
            all.addAll(set);
        }
        return all;
    }

    /**
     * Return all known definition IDs — server-loaded definitions plus
     * client-reported ones.  Used by {@code /halo list} and tab-completion.
     */
    public static Set<Identifier> getAllKnownDefinitionIds() {
        Set<Identifier> all = new LinkedHashSet<>(DEFINITIONS.keySet());
        all.addAll(getClientReportedDefIds());
        return all;
    }

    /**
     * Whether a definition ID is present in the server's own registry
     * (i.e. its JSON is installed on the server).
     */
    public static boolean isServerLoaded(Identifier id) {
        return DEFINITIONS.containsKey(id);
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

    /**
     * Client-side SERVER_DATA listener.
     *
     * <p>Registers under a distinct Fabric ID so it coexists with the
     * server-side SERVER_DATA listener on an integrated server without
     * id collisions.  Tracks loaded IDs in {@link #clientServerDataIds}
     * so reloads only replace definitions this listener owns.</p>
     */
    private static class ClientServerDataListener implements SimpleSynchronousResourceReloadListener {
        @Override
        public Identifier getFabricId() {
            return new Identifier(HaloMod.MOD_ID, "halo_definitions_client_data");
        }

        @Override
        public void reload(ResourceManager manager) {
            HaloJsonLoader.reload(manager, clientServerDataIds);
        }
    }
}
