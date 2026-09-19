package network.azusake.halo.render;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import network.azusake.halo.physics.RenderHeadCapture;
import network.azusake.halo.compat.emf.EmfHeadCapture;
import org.joml.Matrix4f;
public final class HaloRenderListener {
    private static boolean registered;
    private static float tickDelta;
    private HaloRenderListener(){}
    public static void register(){
        if(registered)return;registered=true;
        LevelRenderEvents.END_EXTRACTION.register(context->{
            tickDelta=context.deltaTracker().getGameTimeDeltaPartialTick(true);
            var camera=context.camera();
            RenderHeadCapture.beginFrame(new Matrix4f(),camera.position(),camera.getCullFrustum(),tickDelta,context.level());
        });
        LevelRenderEvents.COLLECT_SUBMITS.register(context->HaloRenderer.getInstance().renderHalos(context.poseStack(),Minecraft.getInstance().gameRenderer.mainCamera(),tickDelta));

    }
}
