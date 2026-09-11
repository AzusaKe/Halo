package network.azusake.halo.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the {@link HaloModConfig} command-system configuration.
 */
class HaloModConfigTest {

    @Test
    @DisplayName("default commandPermissionLevel is 2")
    void defaultPermissionLevel() {
        HaloModConfig config = new HaloModConfig();
        assertEquals(2, config.getCommandPermissionLevel());
        assertTrue(config.isExperimentalYsmAnchorEnabled());
        assertArrayEquals(new double[]{0, 0, 0}, config.getExperimentalYsmHeadLocalOffset());
        config.setExperimentalYsmAnchorEnabled(false);
        assertFalse(config.isExperimentalYsmAnchorEnabled());
    }

    @Test
    @DisplayName("commandPermissionLevel clamped to [0, 4]")
    void permissionLevelClamped() {
        HaloModConfig config = new HaloModConfig();

        config.setCommandPermissionLevel(3);
        assertEquals(3, config.getCommandPermissionLevel());

        config.setCommandPermissionLevel(5);
        assertEquals(4, config.getCommandPermissionLevel());

        config.setCommandPermissionLevel(-1);
        assertEquals(0, config.getCommandPermissionLevel());

        config.setCommandPermissionLevel(0);
        assertEquals(0, config.getCommandPermissionLevel());

        config.setCommandPermissionLevel(4);
        assertEquals(4, config.getCommandPermissionLevel());
    }
}
