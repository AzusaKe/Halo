package network.azusake.halo.render;

import network.azusake.halo.HaloMod;
import network.azusake.halo.compat.emf.EmfHeadCapture;
import network.azusake.halo.compat.ysm.YsmHeadCapture;
import network.azusake.halo.physics.RenderHeadCapture;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.common.MinecraftForge;
import org.joml.Matrix4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Registers the halo renderer with Forge's staged world-render pipeline.
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
     * Register the halo renderer with the Forge render event bus.
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
        MinecraftForge.EVENT_BUS.addListener((RenderLevelStageEvent context) -> {
            if (context.getStage() != RenderLevelStageEvent.Stage.AFTER_CUTOUT_BLOCKS) return;
            // The world-render stack is at its root (the camera view
            // matrix) right before entities render.  Captured head
            // matrices are camera-relative, so the halo pipeline needs
            // this view matrix to recover world-space anchors.
            Matrix4f viewMatrix = new Matrix4f(context.getPoseStack().last().pose());
            RenderHeadCapture.beginFrame(
                viewMatrix,
                context.getCamera().getPosition(),
                context.getFrustum(),
                context.getPartialTick(),
                Minecraft.getInstance().level
            );
            EmfHeadCapture.beginFrame(viewMatrix, context.getCamera().getPosition());
            YsmHeadCapture.beginFrame(
                viewMatrix,
                context.getCamera().getPosition(),
                context.getFrustum()
            );
        });

        MinecraftForge.EVENT_BUS.addListener((RenderLevelStageEvent context) -> {
            if (context.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
            HaloRenderer.getInstance().renderHalos(
                context.getPoseStack(),
                context.getCamera(),
                context.getPartialTick()
            );
        });

        MinecraftForge.EVENT_BUS.addListener((RenderLevelStageEvent context) -> {
            if (context.getStage() == RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS)
                HaloRenderer.getInstance().submitDeferredMeshes();
        });

        LOG.info("[HaloRenderListener] registered for Forge entity and translucent render stages");
    }
}
