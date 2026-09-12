package network.azusake.halo.json;

import network.azusake.halo.HaloMod;
import network.azusake.halo.data.HaloDefinition;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import network.azusake.halo.core.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Fabric resource reload listener that scans {@code halo_definitions/} in all
 * datapacks (server) and resource packs (client), parses every {@code .json}
 * file into a {@link HaloDefinition}, and exposes them through a static registry.
 */
public final class HaloJsonLoader {

    private static final Logger LOG = LoggerFactory.getLogger(HaloMod.MOD_ID);
    private static final String DEFINITIONS_PATH = "halo_definitions";

    private static final network.azusake.halo.core.runtime.DefinitionResources RESOURCES = new network.azusake.halo.core.runtime.DefinitionResources();
    public static network.azusake.halo.core.runtime.DefinitionSnapshot snapshot() { return RESOURCES.snapshot(); }

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

    // ------------------------------------------------------------------
    // Client-reported definition IDs (C2S defs_report)
    // ------------------------------------------------------------------

    /**
     * Per-player set of halo definition IDs reported by that client.
     * Written on the server thread, read by Brigadier commands (also server
     * thread), so a plain {@link java.util.concurrent.ConcurrentHashMap} gives
     * more than enough safety.
     */
    private static final network.azusake.halo.core.runtime.DefinitionReports clientReportedDefs = new network.azusake.halo.core.runtime.DefinitionReports();

    /**
     * Register resource reload listener for server data packs.
     * <p>Halo definitions live under {@code data/&lt;ns&gt;/halo_definitions/}
     * and are loaded by the server-side resource manager.</p>
     * <p>本模组内置定义一律放在 {@code assets/}（资源包）中供客户端渲染使用，
     * 服务器本身不需要任何光环定义。此处保留 {@code data/} 监听器的用途是：
     * 为后续"光环定义由服务器统一管理"的多人同步方案预留——即服务端通过
     * 数据包下发权威定义、不允许客户端自行添加的情况。当前模组内置的
     * {@code data/} 定义目录为空，此监听器不会加载任何内容。</p>
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
        return RESOURCES.legacyDefinitions();
    }

    /**
     * Look up a single definition by id.
     */
    public static Optional<HaloDefinition> getDefinition(Identifier id) {
        return Optional.ofNullable(RESOURCES.legacyDefinitions().get(id));
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
        return clientReportedDefs.all();
    }

    /**
     * Return the definition IDs reported by a single player, or an empty set
     * if that player hasn't reported yet.
     */
    public static Set<Identifier> getClientReportedDefs(UUID playerUuid) {
        return clientReportedDefs.get(playerUuid);
    }

    /**
     * Return all known definition IDs — server-loaded definitions plus
     * client-reported ones.  Used by {@code /halo list} and tab-completion.
     */
    public static Set<Identifier> getAllKnownDefinitionIds() {
        Set<Identifier> all = new LinkedHashSet<>(RESOURCES.legacyDefinitions().keySet());
        all.addAll(getClientReportedDefIds());
        return all;
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
        var loaded = new ArrayList<network.azusake.halo.core.runtime.ResourceInput>();
        var resources = manager.findResources(DEFINITIONS_PATH, id -> id.getPath().endsWith(".json"));
        for (var entry : resources.entrySet()) {
            try (var input = entry.getValue().getInputStream()) {
                loaded.add(new network.azusake.halo.core.runtime.ResourceInput(
                    network.azusake.halo.platform.PlatformTypes.core(entry.getKey()), entry.getValue().getResourcePackName(),
                    new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)));
            } catch (java.io.IOException ex) {
                LOG.warn("Could not read halo resource {}: {}", entry.getKey(), ex.getMessage());
            }
        }
        for (var problem : RESOURCES.reload(sourceSet == clientLoadedIds ? 1 : 0, loaded)) {
            LOG.warn("Skipping malformed halo definition {} from {}: {}", problem.resource(), problem.source(), problem.message());
        }
        LOG.info("Halo definition registry now holds {} entries", RESOURCES.legacyDefinitions().size());
    }

    // ------------------------------------------------------------------
    // Listener implementations
    // ------------------------------------------------------------------

    private static class ServerListener implements SimpleSynchronousResourceReloadListener {
        @Override
        public net.minecraft.util.Identifier getFabricId() {
            return new net.minecraft.util.Identifier(HaloMod.MOD_ID, "halo_definitions");
        }

        @Override
        public void reload(ResourceManager manager) {
            HaloJsonLoader.reload(manager, serverLoadedIds);
        }
    }

    private static class ClientListener implements SimpleSynchronousResourceReloadListener {
        @Override
        public net.minecraft.util.Identifier getFabricId() {
            return new net.minecraft.util.Identifier(HaloMod.MOD_ID, "halo_definitions_client");
        }

        @Override
        public void reload(ResourceManager manager) {
            HaloJsonLoader.reload(manager, clientLoadedIds);
        }
    }
    public static void clearServerResources() { RESOURCES.reload(0, List.of()); clientReportedDefs.clear(); }
}
