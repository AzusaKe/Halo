package network.azusake.halo.compat.emf;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmfCompatTest {

    @Test
    @DisplayName("EMF 3.3.6 is the verified lower bound and newer versions remain eligible")
    void lowerBoundVersionGate() {
        assertFalse(EmfVersionGate.isSupportedVersion("3.3.5"));
        assertTrue(EmfVersionGate.isSupportedVersion("3.3.6"));
        assertTrue(EmfVersionGate.isSupportedVersion("4.0.0"));
        assertTrue(EmfVersionGate.isSupportedVersion("3.3.6+mc26.3"));
        assertFalse(EmfVersionGate.isSupportedVersion("3.1.0"));
        assertFalse(EmfVersionGate.isSupportedVersion("3.0.17"));
        assertFalse(EmfVersionGate.isSupportedVersion(null));
    }

    @Test
    @DisplayName("26.3 EMF 3.3.6 uses the packed-colour ModelPart render ABI")
    void renderAbiSignature() {
        assertEquals("render", Emf263Symbols.RENDER_METHOD_NAMED);
        assertEquals("method_22699", Emf263Symbols.RENDER_METHOD_INTERMEDIARY);
        assertEquals(
            "(Lcom/mojang/blaze3d/vertex/PoseStack;"
                + "Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V",
            Emf263Symbols.RENDER_DESCRIPTOR_NAMED);
        assertEquals(
            "(Lnet/minecraft/class_4587;Lnet/minecraft/class_4588;III)V",
            Emf263Symbols.RENDER_DESCRIPTOR_INTERMEDIARY);
    }
}

