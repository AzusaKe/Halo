package network.azusake.halo.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.lang.reflect.Type;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side halo state for the LOCAL phase (server without the mod).
 *
 * <p>Completely separate from {@link network.azusake.halo.manager.HaloManager}
 * (which is a server / shared-JVM component).  Data is keyed by server address
 * so halos set on different servers are isolated — disconnecting from server A
 * does not clear settings made on server B.
 *
 * <p>Data is persisted to {@code config/halo-azusake/halo_local_halos.json}
 * on every mutation and loaded on first access.</p>
 *
 * <p>In LOCAL phase the player may only set halos on themselves ({@code @s}).
 * This is enforced by {@link HaloLocalCommandHandler}, not by this class.</p>
 */
@Environment(EnvType.CLIENT)
public final class HaloLocalManager {

    private static final HaloLocalManager INSTANCE = new HaloLocalManager();

    private static final Path CONFIG_DIR = FabricLoader.getInstance()
        .getConfigDir().resolve("halo-azusake");

    private static final Path FILE = CONFIG_DIR.resolve("halo_local_halos.json");

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** Serialisation type for the JSON file format. */
    private static final Type STORAGE_TYPE = new TypeToken<Map<String, Map<String, String>>>() {}.getType();

    /**
     * Outer key: server identifier ({@code "host:port"}).
     * Inner map: entity UUID → halo definition ID.
     */
    private final ConcurrentHashMap<String, ConcurrentHashMap<UUID, Identifier>> serverHalos =
        new ConcurrentHashMap<>();

    private volatile boolean loaded = false;

    private HaloLocalManager() {
        // singleton — load() is lazy, called on first access
    }

    public static HaloLocalManager getInstance() {
        return INSTANCE;
    }

    // ------------------------------------------------------------------
    // Persistence
    // ------------------------------------------------------------------

    /** Ensure data is loaded from disk.  Safe to call multiple times. */
    private void ensureLoaded() {
        if (loaded) return;
        synchronized (this) {
            if (loaded) return;
            load();
            loaded = true;
        }
    }

    /** Read the JSON file and populate the in-memory map. */
    private void load() {
        if (!Files.exists(FILE)) return;

        try {
            String raw = Files.readString(FILE);
            // Gson's fromJson returns null for empty JSON objects; we tolerate that.
            Map<String, Map<String, String>> disk = GSON.fromJson(raw, STORAGE_TYPE);
            if (disk == null) return;

            for (var serverEntry : disk.entrySet()) {
                String serverKey = serverEntry.getKey();
                Map<String, String> uuidMap = serverEntry.getValue();
                if (uuidMap == null) continue;
                ConcurrentHashMap<UUID, Identifier> inner = new ConcurrentHashMap<>();
                for (var uuidEntry : uuidMap.entrySet()) {
                    try {
                        UUID uuid = UUID.fromString(uuidEntry.getKey());
                        Identifier defId = new Identifier(uuidEntry.getValue());
                        inner.put(uuid, defId);
                    } catch (IllegalArgumentException e) {
                        // skip malformed entries silently
                    }
                }
                if (!inner.isEmpty()) {
                    serverHalos.put(serverKey, inner);
                }
            }
        } catch (IOException e) {
            // File is corrupt or unreadable — start fresh
            System.err.println("[HaloLocalManager] Failed to load " + FILE + ": " + e.getMessage());
        }
    }

    /** Write the current in-memory map to disk. */
    private void save() {
        try {
            Files.createDirectories(CONFIG_DIR);

            // Build a plain LinkedHashMap<String, Map<String, String>> for JSON
            Map<String, Map<String, String>> disk = new LinkedHashMap<>();
            for (var serverEntry : serverHalos.entrySet()) {
                Map<String, String> uuidMap = new LinkedHashMap<>();
                for (var uuidEntry : serverEntry.getValue().entrySet()) {
                    uuidMap.put(uuidEntry.getKey().toString(), uuidEntry.getValue().toString());
                }
                if (!uuidMap.isEmpty()) {
                    disk.put(serverEntry.getKey(), uuidMap);
                }
            }

            Files.writeString(FILE, GSON.toJson(disk));
        } catch (IOException e) {
            System.err.println("[HaloLocalManager] Failed to save " + FILE + ": " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------------

    /**
     * Record (or replace) a halo on the given entity for the given server.
     */
    public void showHalo(String serverKey, UUID entityUuid, Identifier defId) {
        ensureLoaded();
        serverHalos
            .computeIfAbsent(serverKey, k -> new ConcurrentHashMap<>())
            .put(entityUuid, defId);
        save();
    }

    /**
     * Remove the halo from the given entity for the given server.
     */
    public void hideHalo(String serverKey, UUID entityUuid) {
        ensureLoaded();
        ConcurrentHashMap<UUID, Identifier> map = serverHalos.get(serverKey);
        if (map != null) {
            map.remove(entityUuid);
        }
        save();
    }

    /**
     * Look up the halo definition ID for an entity on a specific server.
     *
     * @return the definition ID, or {@link Optional#empty()} if none is set
     */
    public Optional<Identifier> getHalo(String serverKey, UUID entityUuid) {
        ensureLoaded();
        ConcurrentHashMap<UUID, Identifier> map = serverHalos.get(serverKey);
        if (map == null) return Optional.empty();
        return Optional.ofNullable(map.get(entityUuid));
    }

    /**
     * Return the set of entity UUIDs that have halos on the given server.
     * Used by the render pipeline to discover locally-managed halos.
     *
     * @return unmodifiable set of UUIDs (may be empty, never null)
     */
    public Set<UUID> getHalosForServer(String serverKey) {
        ensureLoaded();
        ConcurrentHashMap<UUID, Identifier> map = serverHalos.get(serverKey);
        if (map == null) return Set.of();
        return Collections.unmodifiableSet(map.keySet());
    }

    /**
     * Remove all halo data for a server.  Called on disconnect.
     */
    public void clearServer(String serverKey) {
        ensureLoaded();
        serverHalos.remove(serverKey);
        save();
    }

    // ------------------------------------------------------------------
    // Key construction
    // ------------------------------------------------------------------

    /**
     * Build a stable server key from a connection's remote address.
     *
     * <p>Uses {@code hostString:port} instead of {@code InetSocketAddress.toString()}
     * because the latter produces different formats depending on how the address
     * was constructed (e.g. {@code "localhost/127.0.0.1:25565"} vs
     * {@code "/127.0.0.1:25565"} vs {@code "localhost:25565"}).</p>
     *
     * @param address the remote socket address from the connection
     * @return stable {@code "host:port"} key, or {@code null} if address is not an {@link InetSocketAddress}
     */
    public static String serverKeyFromAddress(SocketAddress address) {
        if (address instanceof InetSocketAddress inet) {
            return inet.getHostString() + ":" + inet.getPort();
        }
        return null;
    }
}
