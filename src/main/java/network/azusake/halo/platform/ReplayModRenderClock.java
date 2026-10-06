package network.azusake.halo.platform;

import java.lang.reflect.Method;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import network.azusake.halo.HaloMod;

/** Optional reflection bridge for ReplayMod 1.20.1-2.6.23; no hard mod dependency. */
public final class ReplayModRenderClock {
    private static final RenderFrameClock CLOCK = new RenderFrameClock();
    private static boolean checked, available;
    private static Class<?> rendererInterface, videoRenderer;
    private static Method getHandler, getRenderInfo, getFramesDone, getRenderSettings, getFps;

    private ReplayModRenderClock() {}

    private static void initialize() throws ReflectiveOperationException {
        checked = true;
        var mod = FabricLoader.getInstance().getModContainer("replaymod");
        if (mod.isEmpty() || !"1.20.1-2.6.23".equals(
                mod.get().getMetadata().getVersion().getFriendlyString())) return;
        var loader = ReplayModRenderClock.class.getClassLoader();
        rendererInterface = Class.forName(
            "com.replaymod.render.hooks.EntityRendererHandler$IEntityRenderer", false, loader);
        var handler = Class.forName("com.replaymod.render.hooks.EntityRendererHandler", false, loader);
        var info = Class.forName("com.replaymod.render.capturer.RenderInfo", false, loader);
        var settings = Class.forName("com.replaymod.render.RenderSettings", false, loader);
        videoRenderer = Class.forName("com.replaymod.render.rendering.VideoRenderer", false, loader);
        getHandler = rendererInterface.getMethod("replayModRender_getHandler");
        getRenderInfo = handler.getMethod("getRenderInfo");
        getFramesDone = info.getMethod("getFramesDone");
        getRenderSettings = info.getMethod("getRenderSettings");
        getFps = settings.getMethod("getFramesPerSecond");
        available = true;
        HaloMod.LOGGER.info("ReplayMod 1.20.1-2.6.23 export clock enabled");
    }

    /** One coherent sample for animation milliseconds and physics nanoseconds. */
    public static Time sample() {
        Object capture = null;
        int frame = 0, fps = 0;
        try {
            if (!checked) initialize();
            if (available) {
                var client = MinecraftClient.getInstance();
                if (client != null && rendererInterface.isInstance(client.gameRenderer)) {
                    Object handler = getHandler.invoke(client.gameRenderer);
                    if (handler != null) {
                        Object info = getRenderInfo.invoke(handler);
                        // Screenshot exports also install a handler, but have no video frame clock.
                        if (videoRenderer.isInstance(info)) {
                            capture = info;
                            frame = ((Number) getFramesDone.invoke(info)).intValue();
                            fps = ((Number) getFps.invoke(getRenderSettings.invoke(info))).intValue();
                        }
                    }
                }
            }
        } catch (ReflectiveOperationException | LinkageError | RuntimeException failure) {
            available = false;
            HaloMod.LOGGER.warn("ReplayMod export clock unavailable; using normal client time", failure);
        }
        var time = CLOCK.sample(System.currentTimeMillis(), System.nanoTime(), capture, frame, fps);
        return new Time(time.millis(), time.nanos());
    }

    public static long nowMillis() { return sample().millis(); }
    public record Time(long millis, long nanos) {}
}
