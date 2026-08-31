package network.azusake.halo.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import network.azusake.halo.HaloMod;
import net.minecraftforge.fml.loading.FMLPaths;

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
        return FMLPaths.CONFIGDIR.get()
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
            Files.writeString(file, GSON.toJson(config));
        } catch (IOException e) {
            HaloMod.LOGGER.warn("Failed to save Halo mod config {}: {}", file, e.getMessage());
        }
    }

    private static HaloModConfig readOrCreate(Path file) {
        if (!Files.exists(file)) {
            HaloModConfig defaults = new HaloModConfig();
            save(defaults, file);
            return defaults;
        }

        try {
            String raw = Files.readString(file);
            if (raw.isBlank()) {
                // An empty file counts as defaults; backfill so the file is self-documenting.
                HaloModConfig defaults = new HaloModConfig();
                save(defaults, file);
                return defaults;
            }

            JsonElement root = JsonParser.parseString(raw);
            if (!root.isJsonObject()) {
                HaloMod.LOGGER.warn("Halo mod config {} is not a JSON object; using defaults", file);
                return new HaloModConfig();
            }

            JsonObject document = root.getAsJsonObject();
            HaloModConfig parsed = GSON.fromJson(document, HaloModConfig.class);
            if (parsed == null) {
                HaloMod.LOGGER.warn("Halo mod config {} contains no data; using defaults", file);
                return new HaloModConfig();
            }

            List<String> migratedFields = new ArrayList<>();
            int level = parsed.getCommandPermissionLevel();
            if (level < 0 || level > 4) {
                HaloMod.LOGGER.warn(
                    "Halo mod config {} has out-of-range commandPermissionLevel={}; clamping to [0, 4]",
                    file, level);
                parsed.setCommandPermissionLevel(level); // clamps
                document.addProperty("commandPermissionLevel", parsed.getCommandPermissionLevel());
                migratedFields.add("commandPermissionLevel (normalized)");
            } else if (!document.has("commandPermissionLevel")
                || document.get("commandPermissionLevel").isJsonNull()) {
                document.addProperty("commandPermissionLevel", parsed.getCommandPermissionLevel());
                migratedFields.add("commandPermissionLevel");
            }

            if (!migratedFields.isEmpty()) {
                saveMigratedDocument(document, file, migratedFields);
            }
            return parsed;
        } catch (IOException e) {
            HaloMod.LOGGER.warn("Failed to read Halo mod config {}: {}; using defaults", file, e.getMessage());
            return new HaloModConfig();
        } catch (Exception e) {
            HaloMod.LOGGER.warn("Failed to parse Halo mod config {}: {}; using defaults", file, e.getMessage());
            return new HaloModConfig();
        }
    }

    private static void saveMigratedDocument(JsonObject document, Path file, List<String> migratedFields) {
        try {
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            Files.writeString(file, GSON.toJson(document));
            HaloMod.LOGGER.info(
                "Migrated Halo mod config {} with missing or normalized field(s): {}",
                file, String.join(", ", migratedFields));
        } catch (IOException e) {
            // The already parsed in-memory configuration is still usable even
            // when the self-documenting migration cannot be persisted.
            HaloMod.LOGGER.warn(
                "Failed to persist migrated Halo mod config {}: {}",
                file, e.getMessage());
        }
    }
}
