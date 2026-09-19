package network.azusake.halo.render;

import network.azusake.halo.HaloMod;
import network.azusake.halo.compat.emf.EmfHeadCapture;
import network.azusake.halo.compat.ysm.YsmHeadCapture;
import network.azusake.halo.physics.RenderHeadCapture;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Registers the halo renderer with Fabric's world-render pipeline.
 *
 * <p>Capture/geometry evaluation follows entities. Opaque shader-pack meshes submit
 * before Iris consumes its solid G-buffer; glowing, blended and vanilla meshes retain
 * the established late submission after translucent entity buffers are flushed.</p>
 *
 * <p>Usage: call {@link #register()} once during client initialisation.</p>
 */
public final class HaloRenderListener {

    private static final Logger LOG = LoggerFactory.getLogger(HaloMod.MOD_ID);

    private static boolean registered;

    private HaloRenderListener() {
        // utility class
    }

    /**
     * Register the halo renderer with the Fabric render event bus.
     * Idempotent — subsequent calls are no-ops.
     */
    public static void register() {
        if (registered) {
            LOG.warn("[HaloRenderListener] already registered — skipping");
            return;
        }
        registered = true;

        // Drop last frame's head captures right before entities render so the
        // halo pass (AFTER_ENTITIES) only sees this frame's captures; entities
        // that did not render this frame fall back to their previous provider.
        WorldRenderEvents.BEFORE_ENTITIES.register(context -> {
            // Fabric 1.21.1 does not expose a matrix stack at BEFORE_ENTITIES.
            // Entity capture matrices are already camera-relative world space,
            // so the inverse-view contract is the identity transform here.
            Matrix4f viewMatrix = new Matrix4f();
            RenderHeadCapture.beginFrame(
                viewMatrix,
                context.camera().getPos(),
                context.frustum(),
                context.tickCounter().getTickDelta(true),
                context.world()
            );
            EmfHeadCapture.beginFrame(viewMatrix, context.camera().getPos());
            YsmHeadCapture.beginFrame(
                viewMatrix,
                context.camera().getPos(),
                context.frustum()
            );
        });

        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            MatrixStack matrices = context.matrixStack() == null ? new MatrixStack() : context.matrixStack();
            HaloRenderer.getInstance().renderHalos(
                matrices,
                context.camera(),
                context.tickCounter().getTickDelta(true)
            );
        });

        WorldRenderEvents.AFTER_TRANSLUCENT.register(context ->
            HaloRenderer.getInstance().submitDeferredMeshes());

        LOG.info("[HaloRenderListener] registered on WorldRenderEvents.AFTER_ENTITIES");
    }
}
