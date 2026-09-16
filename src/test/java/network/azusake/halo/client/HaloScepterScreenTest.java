package network.azusake.halo.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HaloScepterScreenTest {

    @Test
    void acceptsThe26_3PrimaryMouseButton() {
        assertTrue(HaloScepterScreen.isPrimaryClick(event(InputConstants.MOUSE_BUTTON_LEFT)));
        assertFalse(HaloScepterScreen.isPrimaryClick(event(InputConstants.MOUSE_BUTTON_RIGHT)));
    }

    private static MouseButtonEvent event(int button) {
        return new MouseButtonEvent(0.0, 0.0, new MouseButtonInfo(button, 0));
    }
}
