package network.azusake.halo.render;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HaloDepthPolicyTest {
    @Test void translucentWorldSurfacesProvideDepthOnlyToShaderPackComposites() {
        assertTrue(HaloDepthPolicy.writesDepth(RenderEnvironment.WORLD, true, true, false));
        assertFalse(HaloDepthPolicy.writesDepth(RenderEnvironment.WORLD, false, true, false));
        assertFalse(HaloDepthPolicy.writesDepth(RenderEnvironment.GUI, true, true, false));
        assertFalse(HaloDepthPolicy.writesDepth(RenderEnvironment.WORLD, true, false, false));
    }

    @Test void anAuthoredDepthWriteIsNeverDisabled() {
        for (RenderEnvironment environment : RenderEnvironment.values())
            for (boolean shaders : new boolean[]{false, true})
                assertTrue(HaloDepthPolicy.writesDepth(environment, shaders, true, true));
    }
}
