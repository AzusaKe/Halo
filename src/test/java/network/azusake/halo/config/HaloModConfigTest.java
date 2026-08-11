package network.azusake.halo.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for the {@link HaloModConfig} command-system configuration.
 */
class HaloModConfigTest {

    @Test
    @DisplayName("default commandPermissionLevel is 2")
    void defaultPermissionLevel() {
        assertEquals(2, new HaloModConfig().getCommandPermissionLevel());
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
