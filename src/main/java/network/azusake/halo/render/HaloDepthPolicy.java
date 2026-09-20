package network.azusake.halo.render;

/** Iris's translucent entity/particle programs require surface depth for their composite passes. */
final class HaloDepthPolicy {
    private HaloDepthPolicy() {}

    static com.mojang.blaze3d.platform.CompareOp comparison(boolean depthTest) {
        // Both 26.2 world and PIP projections clear to zero and use reverse-Z.
        return depthTest ? com.mojang.blaze3d.platform.CompareOp.GREATER_THAN_OR_EQUAL
                         : com.mojang.blaze3d.platform.CompareOp.ALWAYS_PASS;
    }

    static boolean writesDepth(RenderEnvironment environment, boolean shaderPack,
                               boolean depthTest, boolean requestedWrite) {
        // Keep native/preview semantics. In an Iris world pass, the nearest surviving
        // translucent fragment must also identify its surface to fog and sky resolve.
        // Color submissions remain sorted back-to-front, including each mesh's triangles.
        return requestedWrite || (environment == RenderEnvironment.WORLD && shaderPack && depthTest);
    }
}
