package network.azusake.halo.config;

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
 * loader (FabricLoader) dependency is touched.</p>
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
        assertTrue(config.isExperimentalYsmAnchorEnabled());
        assertArrayEquals(new double[]{0, 0, 0}, config.getExperimentalYsmHeadLocalOffset());
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
        String persisted = Files.readString(configFile());
        assertTrue(persisted.contains("experimentalYsmAnchorEnabled"));
        assertTrue(persisted.contains("experimentalYsmHeadLocalOffset"));
        assertTrue(Files.readString(configFile()).contains("2"));
    }

    @Test
    @DisplayName("empty JSON object keeps defaults")
    void emptyObjectKeepsDefaults() throws Exception {
        Files.createDirectories(configFile().getParent());
        Files.writeString(configFile(), "{}");

        HaloModConfig config = HaloModConfigStore.load(configFile());

        assertEquals(2, config.getCommandPermissionLevel());
    }

    @Test
    @DisplayName("valid permission level is loaded")
    void validLevelLoaded() throws Exception {
        Files.createDirectories(configFile().getParent());
        Files.writeString(configFile(), "{\"commandPermissionLevel\": 4}");

        HaloModConfig config = HaloModConfigStore.load(configFile());

        assertEquals(4, config.getCommandPermissionLevel());
        assertEquals(4, HaloModConfigStore.getPermissionLevel());
    }

    @Test
    @DisplayName("out-of-range level is clamped to [0, 4]")
    void outOfRangeLevelClamped() throws Exception {
        Files.createDirectories(configFile().getParent());
        Files.writeString(configFile(), "{\"commandPermissionLevel\": 9}");

        HaloModConfig config = HaloModConfigStore.load(configFile());

        assertEquals(4, config.getCommandPermissionLevel());
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
    @DisplayName("unknown keys are ignored")
    void unknownKeysIgnored() throws Exception {
        Files.createDirectories(configFile().getParent());
        Files.writeString(configFile(), "{\"commandPermissionLevel\": 3, \"futureOption\": true}");

        HaloModConfig config = HaloModConfigStore.load(configFile());

        assertEquals(3, config.getCommandPermissionLevel());
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
        assertArrayEquals(new double[]{0.125, 0.25, -0.5},
            loaded.getExperimentalYsmHeadLocalOffset());
    }

    @Test
    @DisplayName("explicit false keeps YSM compatibility disabled")
    void explicitFalseDisablesYsmCompatibility() throws Exception {
        Files.createDirectories(configFile().getParent());
        Files.writeString(configFile(), "{\"experimentalYsmAnchorEnabled\":false}");

        HaloModConfig loaded = HaloModConfigStore.load(configFile());

        assertFalse(loaded.isExperimentalYsmAnchorEnabled());
        assertTrue(Files.readString(configFile()).contains("\"experimentalYsmAnchorEnabled\": false"));
    }

    @Test
    @DisplayName("invalid YSM offset is normalized and persisted")
    void invalidYsmOffsetIsNormalized() throws Exception {
        Files.createDirectories(configFile().getParent());
        Files.writeString(configFile(), "{\"experimentalYsmAnchorEnabled\":true,"
            + "\"experimentalYsmHeadLocalOffset\":[1.0,2.0]}");

        HaloModConfig loaded = HaloModConfigStore.load(configFile());

        assertTrue(loaded.isExperimentalYsmAnchorEnabled());
        assertArrayEquals(new double[]{0, 0, 0}, loaded.getExperimentalYsmHeadLocalOffset());
        assertTrue(Files.readString(configFile()).contains("0.0"));
    }
}
