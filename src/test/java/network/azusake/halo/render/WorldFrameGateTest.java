package network.azusake.halo.render;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WorldFrameGateTest {
    @Test void shadowsAndRepeatedStagesCannotAdvanceTheMainSimulationTwice() {
        var gate = new WorldFrameGate();
        gate.beginFrame();
        assertFalse(gate.capture(false));
        assertFalse(gate.render(false));
        assertFalse(gate.render(true));
        assertTrue(gate.capture(true));
        assertFalse(gate.capture(false));
        assertFalse(gate.capture(true));
        assertFalse(gate.render(false));
        assertTrue(gate.render(true));
        assertFalse(gate.render(true));
        gate.beginFrame();
        assertFalse(gate.render(true));
        assertTrue(gate.capture(true));
        assertTrue(gate.render(true));
    }
}
