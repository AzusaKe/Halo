package network.azusake.halo.render;

import network.azusake.halo.HaloMod;
import network.azusake.halo.compat.ysm.YsmHeadCapture;
import network.azusake.halo.physics.RenderHeadCapture;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.common.MinecraftForge;
import org.joml.Matrix4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Registers the halo renderer with Forge's world-render pipeline.
 *
 * <p>Halos are drawn <em>after</em> entities so they always appear on top of
 * the entity they are attached to.  The glow layer uses additive blending and
 * renders correctly against both opaque and translucent geometry.</p>
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
        MinecraftForge.EVENT_BUS.addListener((RenderLevelStageEvent event) -> {
            if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_CUTOUT_BLOCKS) {
                RenderHeadCapture.clearFrame();
                // This is the last stable Forge stage before entities. Captured
                // player-head matrices are camera-relative, so retain the root
                // view matrix for conversion back to world space.
                Matrix4f viewMatrix = new Matrix4f(event.getPoseStack().last().pose());
                RenderHeadCapture.setViewMatrix(viewMatrix);
                YsmHeadCapture.beginFrame(
                    viewMatrix,
                    event.getCamera().getPosition(),
                    event.getFrustum()
                );
            } else if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
                HaloRenderer.getInstance().renderHalos(
                    event.getPoseStack(),
                    event.getCamera(),
                    event.getPartialTick()
                );
            }
        });

        LOG.info("[HaloRenderListener] registered on RenderLevelStageEvent.AFTER_ENTITIES");
    }
}
