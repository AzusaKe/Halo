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
    @Test void worldFrameResetIsHookedBeforeExtractionInsteadOfAfterIt() throws Exception {
        var node = new org.objectweb.asm.tree.ClassNode();
        try (var stream = getClass().getResourceAsStream(
                "/network/azusake/halo/mixin/PreviewFrameLifecycleMixin.class")) {
            assertNotNull(stream);
            new org.objectweb.asm.ClassReader(stream).accept(node, 0);
        }
        int resetHooks = 0;
        for (var method : node.methods) {
            boolean resetsWorld = false;
            for (var instruction : method.instructions) {
                if (instruction instanceof org.objectweb.asm.tree.MethodInsnNode call
                        && call.owner.endsWith("/HaloRenderer") && call.name.equals("beginFrame")) {
                    resetsWorld = true;
                }
            }
            if (!resetsWorld) continue;
            resetHooks++;
            var annotations = method.visibleAnnotations == null
                ? new java.util.ArrayList<org.objectweb.asm.tree.AnnotationNode>()
                : new java.util.ArrayList<>(method.visibleAnnotations);
            if (method.invisibleAnnotations != null) annotations.addAll(method.invisibleAnnotations);
            var inject = annotations.stream().filter(a -> a.desc.endsWith("/Inject;")).findFirst().orElseThrow();
            int key = inject.values.indexOf("method");
            assertTrue(key >= 0);
            assertEquals(java.util.List.of("extract"), inject.values.get(key + 1),
                "Minecraft 26.2 calls extract before render; a render-HEAD reset erases the captured frame");
        }
        assertEquals(1, resetHooks);
        // The independent GUI draw lifecycle must never invalidate the earlier world extraction.
        var preview = new org.objectweb.asm.tree.ClassNode();
        try (var stream = getClass().getResourceAsStream(
                "/network/azusake/halo/render/PlayerPreviewRenderer.class")) {
            new org.objectweb.asm.ClassReader(stream).accept(preview, 0);
        }
        for (var method : preview.methods) for (var instruction : method.instructions) {
            if (instruction instanceof org.objectweb.asm.tree.MethodInsnNode call)
                assertFalse(call.owner.endsWith("/HaloRenderer") && call.name.equals("beginFrame"));
        }
    }
}
