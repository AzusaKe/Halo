package network.azusake.halo.compat.caustica;

import network.azusake.halo.render.HaloMeshResources;
import network.azusake.halo.physics.RenderHeadCapture;
import net.minecraft.client.Minecraft;
import org.joml.Matrix4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Optional boundary: default Halo classes never resolve a Caustica API type. */
public final class CausticaBridge {
    static final Logger LOG=LoggerFactory.getLogger("HaloCaustica");
    public static final boolean ENABLED=Boolean.getBoolean("halo.caustica.experimental");
    private static volatile CausticaSession session;
    private static volatile boolean capturing;
    private CausticaBridge() { }
    static void opened(CausticaSession value) { session=value; }
    static void closed(CausticaSession value) { if(session==value){ session=null;capturing=false; } }
    public static boolean usingRt() { return ENABLED && session!=null && capturing; }
    public static void beginCapture() {
        if(!ENABLED || session==null)return;
        var client=Minecraft.getInstance();
        var runtime=dev.comfyfluffy.caustica.minecraft.client.CausticaClientComposition.current().runtime();
        capturing=client.level!=null && runtime.frameActive() && !runtime.requiresSourceWorldFallback();
        if(!capturing)return;
        var camera=client.gameRenderer.mainCamera();
        float delta=client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        RenderHeadCapture.beginFrame(new Matrix4f(),camera.position(),null,delta,client.level);
    }
    public static void finishCapture(boolean validSnapshot) {
        var value=session;if(!usingRt() || !validSnapshot || value==null)return;
        var client=Minecraft.getInstance();
        value.frame(network.azusake.halo.render.HaloRenderer.getInstance().renderRetainedScene(
            client.gameRenderer.mainCamera(),client.getDeltaTracker().getGameTimeDeltaPartialTick(false)));
    }
    /** Live frame flag, rather than installed-mod/session detection, gates vanilla submission. */
    public static void vanillaFrame() { capturing=false;var value=session;if(value!=null)value.hide(); }
    public static void resourcesChanged() { var value=session;if(ENABLED && value!=null)value.reload(); }
}
