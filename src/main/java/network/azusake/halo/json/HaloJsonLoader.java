package network.azusake.halo.json;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import network.azusake.halo.HaloMod;
import network.azusake.halo.data.HaloDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.InputStreamReader;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Loads halo definitions from server data packs and client resource packs. */
public final class HaloJsonLoader {
    private static final Logger LOG = LoggerFactory.getLogger(HaloMod.MOD_ID);
    private static final String DEFINITIONS_PATH = "halo_definitions";
    private static final Map<ResourceLocation, HaloDefinition> DEFINITIONS = new LinkedHashMap<>();
    private static final HaloDefinitionDeserializer DESERIALIZER = new HaloDefinitionDeserializer();
    private static final Set<ResourceLocation> SERVER_IDS = new LinkedHashSet<>();
    private static final Set<ResourceLocation> CLIENT_IDS = new LinkedHashSet<>();
    private static final Map<UUID, Set<ResourceLocation>> CLIENT_REPORTED = new ConcurrentHashMap<>();
    private static volatile boolean serverRegistered;
    private static volatile boolean clientRegistered;
    private HaloJsonLoader() {}

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
    public static Map<ResourceLocation, HaloDefinition> getDefinitions() {
        return Collections.unmodifiableMap(DEFINITIONS);
    }
    public static Optional<HaloDefinition> getDefinition(ResourceLocation id) { return Optional.ofNullable(DEFINITIONS.get(id)); }
    public static void putClientReportedDefs(UUID uuid, Set<ResourceLocation> ids) {
        CLIENT_REPORTED.put(uuid, Collections.unmodifiableSet(new LinkedHashSet<>(ids)));
    }
    public static void removeClientReportedDefs(UUID uuid) { CLIENT_REPORTED.remove(uuid); }
    public static Set<ResourceLocation> getClientReportedDefIds() {
        Set<ResourceLocation> result = new LinkedHashSet<>();
        CLIENT_REPORTED.values().forEach(result::addAll);
        return result;
    }
    public static Set<ResourceLocation> getClientReportedDefs(UUID uuid) { return CLIENT_REPORTED.getOrDefault(uuid, Set.of()); }
    public static Set<ResourceLocation> getAllKnownDefinitionIds() {
        Set<ResourceLocation> result = new LinkedHashSet<>(DEFINITIONS.keySet());
        result.addAll(getClientReportedDefIds());
        return result;
    }

    private static void reload(ResourceManager manager, Set<ResourceLocation> ownIds) {
        ownIds.forEach(DEFINITIONS::remove);
        ownIds.clear();
        Map<ResourceLocation, Resource> resources = manager.listResources(DEFINITIONS_PATH,
            id -> id.getPath().endsWith(".json"));
        for (Map.Entry<ResourceLocation, Resource> entry : resources.entrySet()) {
            try (InputStreamReader reader = new InputStreamReader(entry.getValue().open())) {
                JsonElement root = JsonParser.parseReader(reader);
                HaloDefinition def = DESERIALIZER.deserialize(root, HaloDefinition.class, null);
                DEFINITIONS.put(def.id(), def);
                ownIds.add(def.id());
            } catch (Exception e) {
                LOG.warn("Skipping malformed halo definition {}: {}", entry.getKey(), e.getMessage());
            }
        }
        LOG.info("Halo definition registry now holds {} entries", DEFINITIONS.size());
    }

    private static final class Loader extends SimplePreparableReloadListener<Void> {
        private final Set<ResourceLocation> ids;
        private Loader(Set<ResourceLocation> ids) { this.ids = ids; }
        @Override protected Void prepare(ResourceManager manager, ProfilerFiller profiler) { return null; }
        @Override protected void apply(Void ignored, ResourceManager manager, ProfilerFiller profiler) {
            HaloJsonLoader.reload(manager, ids);
        }
    }
}
