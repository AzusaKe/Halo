package network.azusake.halo.config;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link HaloModConfigStore} file load/save behaviour.
 *
 * <p>Tests inject a temporary directory via the {@code Path} overloads, so no
 * loader environment dependency is touched.</p>
 */
class HaloModConfigStoreTest {

    @TempDir
    Path tempDir;

    private Path configFile() {
        return tempDir.resolve("halo-azusake").resolve("halo_mod_config.json");
    }

    @Test
    @DisplayName("missing file is created with defaults")
    void missingFileCreatedWithDefaults() throws Exception {
        HaloModConfig config = HaloModConfigStore.load(configFile());

        assertEquals(2, config.getCommandPermissionLevel());
        assertFalse(config.isExperimentalYsmAnchorEnabled());
        assertArrayEquals(new double[]{0.0, 0.0, 0.0}, config.getExperimentalYsmHeadLocalOffset());
        assertTrue(Files.exists(configFile()));

        String content = Files.readString(configFile());
        assertTrue(content.contains("commandPermissionLevel"));
        assertTrue(content.contains("experimentalYsmAnchorEnabled"));
        assertTrue(content.contains("experimentalYsmHeadLocalOffset"));
        assertTrue(content.contains("2"));
    }

    @Test
    @DisplayName("blank file counts as defaults and is backfilled")
    void blankFileCountsAsDefaults() throws Exception {
        Files.createDirectories(configFile().getParent());
        Files.writeString(configFile(), "");

        HaloModConfig config = HaloModConfigStore.load(configFile());

        assertEquals(2, config.getCommandPermissionLevel());
        assertTrue(Files.readString(configFile()).contains("2"));
    }

    @Test
    @DisplayName("empty JSON object keeps and persists defaults")
    void emptyObjectKeepsDefaults() throws Exception {
        Files.createDirectories(configFile().getParent());
        Files.writeString(configFile(), "{}");

        HaloModConfig config = HaloModConfigStore.load(configFile());

        assertEquals(2, config.getCommandPermissionLevel());
        JsonObject persisted = persistedJson();
        assertEquals(2, persisted.get("commandPermissionLevel").getAsInt());
        assertFalse(persisted.get("experimentalYsmAnchorEnabled").getAsBoolean());
        assertEquals(3, persisted.getAsJsonArray("experimentalYsmHeadLocalOffset").size());
    }

    @Test
    @DisplayName("valid permission level is loaded")
    void validLevelLoaded() throws Exception {
        Files.createDirectories(configFile().getParent());
        Files.writeString(configFile(), "{\"commandPermissionLevel\": 4}");

        HaloModConfig config = HaloModConfigStore.load(configFile());

        assertEquals(4, config.getCommandPermissionLevel());
        assertEquals(4, HaloModConfigStore.getPermissionLevel());
        assertFalse(persistedJson().get("experimentalYsmAnchorEnabled").getAsBoolean());
    }

    @Test
    @DisplayName("out-of-range level is clamped to [0, 4]")
    void outOfRangeLevelClamped() throws Exception {
        Files.createDirectories(configFile().getParent());
        Files.writeString(configFile(), "{\"commandPermissionLevel\": 9}");

        HaloModConfig config = HaloModConfigStore.load(configFile());

        assertEquals(4, config.getCommandPermissionLevel());
        assertEquals(4, persistedJson().get("commandPermissionLevel").getAsInt());
    }

    @Test
    @DisplayName("corrupt JSON falls back to defaults without crashing")
    void corruptJsonFallsBackToDefaults() throws Exception {
        Files.createDirectories(configFile().getParent());
        Files.writeString(configFile(), "{not valid json!!");

        HaloModConfig config = HaloModConfigStore.load(configFile());

        assertEquals(2, config.getCommandPermissionLevel());
    }

    @Test
    @DisplayName("unknown keys are preserved while missing fields are backfilled")
    void unknownKeysIgnored() throws Exception {
        Files.createDirectories(configFile().getParent());
        Files.writeString(configFile(), "{\"commandPermissionLevel\": 3, \"futureOption\": true}");

        HaloModConfig config = HaloModConfigStore.load(configFile());

        assertEquals(3, config.getCommandPermissionLevel());
        JsonObject persisted = persistedJson();
        assertTrue(persisted.get("futureOption").getAsBoolean());
        assertFalse(persisted.get("experimentalYsmAnchorEnabled").getAsBoolean());
        assertTrue(persisted.has("experimentalYsmHeadLocalOffset"));
    }

    @Test
    @DisplayName("save writes the config file and it reloads")
    void saveWritesAndReloads() throws Exception {
        HaloModConfig config = new HaloModConfig();
        config.setCommandPermissionLevel(4);
        config.setExperimentalYsmAnchorEnabled(true);
        config.setExperimentalYsmHeadLocalOffset(new double[]{0.125, 0.25, -0.5});

        HaloModConfigStore.save(config, configFile());

        assertTrue(Files.exists(configFile()));
        HaloModConfig loaded = HaloModConfigStore.load(configFile());
        assertEquals(4, loaded.getCommandPermissionLevel());
        assertTrue(loaded.isExperimentalYsmAnchorEnabled());
        assertArrayEquals(new double[]{0.125, 0.25, -0.5}, loaded.getExperimentalYsmHeadLocalOffset());
    }

    @Test
    @DisplayName("invalid YSM offset in JSON falls back to zero")
    void invalidYsmOffsetFallsBack() throws Exception {
        Files.createDirectories(configFile().getParent());
        Files.writeString(configFile(), "{\"experimentalYsmAnchorEnabled\":true,"
            + "\"experimentalYsmHeadLocalOffset\":[1.0,2.0]}");

        HaloModConfig loaded = HaloModConfigStore.load(configFile());

        assertTrue(loaded.isExperimentalYsmAnchorEnabled());
        assertArrayEquals(new double[]{0.0, 0.0, 0.0}, loaded.getExperimentalYsmHeadLocalOffset());
        assertArrayEquals(
            new double[]{0.0, 0.0, 0.0},
            new double[]{
                persistedJson().getAsJsonArray("experimentalYsmHeadLocalOffset").get(0).getAsDouble(),
                persistedJson().getAsJsonArray("experimentalYsmHeadLocalOffset").get(1).getAsDouble(),
                persistedJson().getAsJsonArray("experimentalYsmHeadLocalOffset").get(2).getAsDouble()
            });
    }

    @Test
    @DisplayName("a partially migrated config receives only the remaining defaults")
    void partialConfigIsBackfilled() throws Exception {
        Files.createDirectories(configFile().getParent());
        Files.writeString(configFile(), "{\"commandPermissionLevel\":1,"
            + "\"experimentalYsmAnchorEnabled\":true}");

        HaloModConfig loaded = HaloModConfigStore.load(configFile());

        assertEquals(1, loaded.getCommandPermissionLevel());
        assertTrue(loaded.isExperimentalYsmAnchorEnabled());
        JsonObject persisted = persistedJson();
        assertTrue(persisted.get("experimentalYsmAnchorEnabled").getAsBoolean());
        assertTrue(persisted.has("experimentalYsmHeadLocalOffset"));
    }

    @Test
    @DisplayName("loading a fully migrated config is idempotent")
    void migratedConfigIsIdempotent() throws Exception {
        Files.createDirectories(configFile().getParent());
        Files.writeString(configFile(), "{\"commandPermissionLevel\":2}");

        HaloModConfigStore.load(configFile());
        String afterMigration = Files.readString(configFile());
        HaloModConfigStore.load(configFile());

        assertEquals(afterMigration, Files.readString(configFile()));
    }

    private JsonObject persistedJson() throws Exception {
        return JsonParser.parseString(Files.readString(configFile())).getAsJsonObject();
    }
}
