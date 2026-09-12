package network.azusake.halo.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import network.azusake.halo.HaloMod;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Loads and saves the file-backed {@link HaloModConfig} from
 * {@code config/halo-azusake/halo_mod_config.json}.
 *
 * <p>{@link #load()} is called once during mod initialisation and caches the
 * result; the {@code /halo} command tree reads {@link #getPermissionLevel()}
 * dynamically, so a future hot-reload hook only needs to call
 * {@link #load()} again.</p>
 *
 * <p>Load rules:</p>
 * <ul>
 *   <li>File missing or blank → defaults are written to disk.</li>
 *   <li>{@code {}} or missing fields → defaults are merged back into the file.</li>
 *   <li>Out-of-range values → clamped to {@code [0, 4]} with a warning.</li>
 *   <li>Corrupt JSON → warning + defaults (never crashes the game).</li>
 *   <li>Unknown keys → preserved during migration (backwards compatible).</li>
 * </ul>
 */
public final class HaloModConfigStore {

    private static final String CONFIG_DIR_NAME = "halo-azusake";
    private static final String CONFIG_FILE_NAME = "halo_mod_config.json";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static volatile HaloModConfig current = new HaloModConfig();

    private HaloModConfigStore() {
        // utility class
    }

    /** Resolve the default config file: {@code config/halo-azusake/halo_mod_config.json}. */
    private static Path defaultFile() {
        return FabricLoader.getInstance()
            .getConfigDir()
            .resolve(CONFIG_DIR_NAME)
            .resolve(CONFIG_FILE_NAME);
    }

    /**
     * Load (or create) the mod config from the default location and cache it.
     *
     * @return the loaded config
     */
    public static HaloModConfig load() {
        return load(defaultFile());
    }

    /**
     * Load (or create) the mod config from a specific file and cache it.
     *
     * <p>The {@code Path} overload keeps the file logic free of loader
     * dependencies so it can be unit-tested with temporary directories.</p>
     *
     * @param file the config file to read (or create)
     * @return the loaded config
     */
    public static HaloModConfig load(Path file) {
        HaloModConfig config = readOrCreate(file);
        current = config;
        return config;
    }

    /**
     * @return the currently cached mod config (defaults before {@link #load()} runs)
     */
    public static HaloModConfig get() {
        return current;
    }

    /**
     * @return the permission level required by the {@code /halo} command tree (default 2)
     */
    public static int getPermissionLevel() {
        return current.getCommandPermissionLevel();
    }

    /** Write the given config to the default location. */
    public static void save(HaloModConfig config) {
        save(config, defaultFile());
    }

    /** Write the given config to a specific file (creating parent directories). */
    public static void save(HaloModConfig config, Path file) {
        try {
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            Files.writeString(file, network.azusake.halo.core.ModConfigCodec.encode(config));
        } catch (IOException e) {
            HaloMod.LOGGER.warn("Failed to save Halo mod config {}: {}", file, e.getMessage());
        }
    }

    private static HaloModConfig readOrCreate(Path file) {
        try {
            var decoded=network.azusake.halo.core.ModConfigCodec.decode(Files.exists(file)?Files.readString(file):null);
            if(decoded.replacement()!=null) {
                try {
                    if(file.getParent()!=null)Files.createDirectories(file.getParent());
                    Files.writeString(file,decoded.replacement());
                } catch(IOException ex) { HaloMod.LOGGER.warn("Failed to save Halo mod config {}: {}",file,ex.getMessage()); }
            }
            return decoded.config();
        } catch(IOException ex) {
            HaloMod.LOGGER.warn("Failed to read Halo mod config {}: {}",file,ex.getMessage());
            return new HaloModConfig();
        }
    }
}
