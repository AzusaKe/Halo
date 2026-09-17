package network.azusake.halo.config;

import com.google.gson.*;
import net.fabricmc.loader.api.FabricLoader;
import network.azusake.halo.HaloMod;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.*;
import java.util.*;

/** Persistent, instance-wide overrides for registered halo-source priorities. */
public final class HaloSourcePriorityStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "halo_source_priorities.json";
    private static Map<String, Integer> current = new LinkedHashMap<>();
    private static JsonObject document = defaults();
    private static Path currentFile;

    private HaloSourcePriorityStore() {}

    public record LoadResult(boolean success, String message) {}

    public static Path defaultFile() {
        return FabricLoader.getInstance().getConfigDir().resolve("halo-azusake").resolve(FILE_NAME);
    }

    public static synchronized LoadResult load() { return load(defaultFile()); }

    public static synchronized LoadResult load(Path file) {
        try {
            JsonObject loaded = defaults();
            if (Files.exists(file)) {
                JsonElement parsed = JsonParser.parseString(Files.readString(file));
                if (!parsed.isJsonObject()) throw new JsonParseException("root must be a JSON object");
                loaded = parsed.getAsJsonObject();
            }
            Map<String, Integer> values = decode(loaded);
            document = loaded.deepCopy();
            if (!document.has("schema")) document.addProperty("schema", 1);
            current = new LinkedHashMap<>(values);
            currentFile = file;
            if (!Files.exists(file)) write(file);
            return new LoadResult(true, "loaded " + values.size() + " priority entries");
        } catch (IOException | RuntimeException ex) {
            HaloMod.LOGGER.warn("Failed to load halo source priorities {}: {}", file, ex.getMessage());
            currentFile = file;
            return new LoadResult(false, ex.getMessage());
        }
    }

    public static synchronized Map<String, Integer> priorities() { return Map.copyOf(current); }
    public static synchronized Path file() { return currentFile == null ? defaultFile() : currentFile; }

    public static synchronized void set(String sourceId, int priority) {
        current.put(sourceId, priority);
        save();
    }

    /** Merge generated/default/effective entries while retaining unregistered and unknown data. */
    public static synchronized boolean merge(Map<String, Integer> values) {
        boolean changed = false;
        for (var entry : values.entrySet()) {
            Integer old = current.put(entry.getKey(), entry.getValue());
            if (!Objects.equals(old, entry.getValue())) changed = true;
        }
        if (changed) save();
        return changed;
    }

    private static Map<String, Integer> decode(JsonObject root) {
        Map<String, Integer> values = new LinkedHashMap<>();
        JsonElement priorities = root.get("priorities");
        if (priorities == null) return values;
        if (!priorities.isJsonObject()) throw new JsonParseException("priorities must be an object");
        for (var entry : priorities.getAsJsonObject().entrySet()) {
            if (!entry.getValue().isJsonPrimitive() || !entry.getValue().getAsJsonPrimitive().isNumber())
                throw new JsonParseException("priority for " + entry.getKey() + " must be an integer");
            try {
                values.put(entry.getKey(), new BigDecimal(entry.getValue().getAsString()).intValueExact());
            } catch (RuntimeException ex) {
                throw new JsonParseException("priority for " + entry.getKey() + " is outside the signed 32-bit range");
            }
        }
        return values;
    }

    private static void save() {
        try { write(file()); }
        catch (IOException ex) { HaloMod.LOGGER.warn("Failed to save halo source priorities {}: {}", file(), ex.getMessage()); }
    }

    private static void write(Path file) throws IOException {
        if (file.getParent() != null) Files.createDirectories(file.getParent());
        JsonObject priorities = new JsonObject();
        current.forEach(priorities::addProperty);
        document.addProperty("schema", 1);
        document.add("priorities", priorities);
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(temporary, GSON.toJson(document));
        try {
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static JsonObject defaults() {
        JsonObject root = new JsonObject(); root.addProperty("schema", 1); root.add("priorities", new JsonObject()); return root;
    }
}
