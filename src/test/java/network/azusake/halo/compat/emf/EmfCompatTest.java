package network.azusake.halo.compat.emf;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmfCompatTest {
    @Test void lowerBoundVersionGate() {
        assertTrue(EmfVersionGate.isSupportedVersion("3.1.1"));
        assertTrue(EmfVersionGate.isSupportedVersion("4.0.0"));
        assertFalse(EmfVersionGate.isSupportedVersion("3.1.0"));
        assertFalse(EmfVersionGate.isSupportedVersion(null));
    }

    @Test void renderAbiSignature() {
        assertEquals("render", Emf261Symbols.RENDER_METHOD_NAMED);
        assertEquals("method_22699", Emf261Symbols.RENDER_METHOD_INTERMEDIARY);
    }
}

