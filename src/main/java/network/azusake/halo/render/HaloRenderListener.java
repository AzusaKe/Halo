package network.azusake.halo.render;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import net.neoforged.neoforge.common.NeoForge;
import network.azusake.halo.physics.OptionalIrisPassDetector;
import network.azusake.halo.physics.RenderHeadCapture;
import org.joml.Matrix4f;
/** Collect once, then consume the immutable output in the solid and late world stages. */
public final class HaloRenderListener {
    private static boolean registered;
    private static float tickDelta;
    private static final WorldFrameGate FRAME = new WorldFrameGate();
    private HaloRenderListener() {}
    static void beginFrame() { FRAME.beginFrame(); }
    public static void register() {
        if (registered) return;
        registered = true;
        NeoForge.EVENT_BUS.addListener(ExtractLevelRenderStateEvent.class, event -> {
            if (!FRAME.capture(OptionalIrisPassDetector.isMainPass())) return;
            tickDelta = event.getDeltaTracker().getGameTimeDeltaPartialTick(true);
            RenderHeadCapture.beginFrame(new Matrix4f(), event.getCamera().position(),
                event.getFrustum(), tickDelta, event.getLevel());
        });
        NeoForge.EVENT_BUS.addListener(SubmitCustomGeometryEvent.class, event -> {
            if (!FRAME.render(OptionalIrisPassDetector.isMainPass())) return;
            HaloRenderer.getInstance().renderHalos(event.getPoseStack(),
                Minecraft.getInstance().gameRenderer.mainCamera(), tickDelta);
        });
        NeoForge.EVENT_BUS.addListener(RenderLevelStageEvent.AfterOpaqueFeatures.class, event ->
            HaloRenderer.getInstance().submitSolidStage());
    }
}
