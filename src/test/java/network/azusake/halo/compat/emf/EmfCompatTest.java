package network.azusake.halo.compat.emf;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmfCompatTest {

    @Test
    @DisplayName("EMF 3.1.1 is the lower bound and newer versions remain eligible")
    void lowerBoundVersionGate() {
        assertTrue(EmfVersionGate.isSupportedVersion("3.1.1"));
        assertTrue(EmfVersionGate.isSupportedVersion("3.3.5"));
        assertTrue(EmfVersionGate.isSupportedVersion("4.0.0"));
        assertTrue(EmfVersionGate.isSupportedVersion("3.1.1+mc26.2"));
        assertFalse(EmfVersionGate.isSupportedVersion("3.1.0"));
        assertFalse(EmfVersionGate.isSupportedVersion("3.0.17"));
        assertFalse(EmfVersionGate.isSupportedVersion(null));
    }

    @Test
    @DisplayName("26.2 EMF uses the packed-colour ModelPart render ABI")
    void renderAbiSignature() {
        assertEquals("render", Emf262Symbols.RENDER_METHOD_NAMED);
        assertEquals("method_22699", Emf262Symbols.RENDER_METHOD_INTERMEDIARY);
        assertEquals(
            "(Lcom/mojang/blaze3d/vertex/PoseStack;"
                + "Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V",
            Emf262Symbols.RENDER_DESCRIPTOR_NAMED);
        assertEquals(
            "(Lnet/minecraft/class_4587;Lnet/minecraft/class_4588;III)V",
            Emf262Symbols.RENDER_DESCRIPTOR_INTERMEDIARY);
    }
}


