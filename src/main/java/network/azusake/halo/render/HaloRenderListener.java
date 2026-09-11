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

/** Registers the halo renderer with Forge's world-render pipeline. */
public final class HaloRenderListener {
    private static final Logger LOG = LoggerFactory.getLogger(HaloMod.MOD_ID);
    private static boolean registered;

    private HaloRenderListener() {
    }

    public static void register() {
        if (registered) {
            LOG.warn("[HaloRenderListener] already registered — skipping");
            return;
        }
        registered = true;
        MinecraftForge.EVENT_BUS.addListener((RenderLevelStageEvent event) -> {
            if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_CUTOUT_BLOCKS) {
                Matrix4f viewMatrix = new Matrix4f(event.getPoseStack().last().pose());
                RenderHeadCapture.beginFrame(
                    viewMatrix, event.getCamera().getPosition(), event.getFrustum(),
                    event.getPartialTick(), Minecraft.getInstance().level);
                EmfHeadCapture.beginFrame(viewMatrix, event.getCamera().getPosition());
                YsmHeadCapture.beginFrame(
                    viewMatrix, event.getCamera().getPosition(), event.getFrustum());
            } else if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
                HaloRenderer.getInstance().renderHalos(
                    event.getPoseStack(), event.getCamera(), event.getPartialTick());
            }
        });
        LOG.info("[HaloRenderListener] registered on RenderLevelStageEvent.AFTER_ENTITIES");
    }
}
