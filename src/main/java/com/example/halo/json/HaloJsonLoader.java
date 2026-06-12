package com.example.halo.json;

import com.example.halo.HaloMod;
import com.example.halo.data.HaloDefinition;
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

    private HaloJsonLoader() {
        // utility class
    }

    /**
     * Register resource reload listeners for both server data and client resources.
     * Safe to call more than once — subsequent calls are no-ops.
     */
    public static void register() {
        ResourceManagerHelper.get(ResourceType.SERVER_DATA)
            .registerReloadListener(new ServerListener());

        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES)
            .registerReloadListener(new ClientListener());

        LOG.info("HaloJsonLoader registered for SERVER_DATA and CLIENT_RESOURCES");
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

    private static void reload(ResourceManager manager) {
        DEFINITIONS.clear();

        Identifier scanRoot = new Identifier(HaloMod.MOD_ID, DEFINITIONS_PATH);
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
            return new Identifier(HaloMod.MOD_ID, "halo_definitions_server");
        }

        @Override
        public void reload(ResourceManager manager) {
            HaloJsonLoader.reload(manager);
        }
    }

    private static class ClientListener implements SimpleSynchronousResourceReloadListener {
        @Override
        public Identifier getFabricId() {
            return new Identifier(HaloMod.MOD_ID, "halo_definitions_client");
        }

        @Override
        public void reload(ResourceManager manager) {
            HaloJsonLoader.reload(manager);
        }
    }
}
