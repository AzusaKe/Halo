package network.azusake.halo.platform;

import java.lang.reflect.Method;
import net.neoforged.fml.loading.LoadingModList;
import net.minecraft.client.Minecraft;
import network.azusake.halo.HaloMod;

/** Optional reflection bridge for ReforgedPlayMod 1.21.1-0.3; no hard mod dependency. */
public final class ReplayModRenderClock {
    private static final RenderFrameClock CLOCK = new RenderFrameClock();
    private static boolean checked, available;
    private static Class<?> rendererInterface, videoRenderer;
    private static Method getHandler, getRenderInfo, getFramesDone, getRenderSettings, getFps;

    private ReplayModRenderClock() {}

    private static void initialize() throws ReflectiveOperationException {
        checked = true;
        var mods = LoadingModList.get();
        if (mods == null) return;
        var modFile = mods.getModFileById("reforgedplaymod");
        if (modFile == null || modFile.getMods().stream().noneMatch(mod ->
                "reforgedplaymod".equals(mod.getModId()) && "0.3".equals(mod.getVersion().toString()))) return;
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
        HaloMod.LOGGER.info("ReforgedPlayMod 1.21.1-0.3 export clock enabled");
    }

    /** One coherent sample for animation milliseconds and physics nanoseconds. */
    public static Time sample() {
        Object capture = null;
        int frame = 0, fps = 0;
        try {
            if (!checked) initialize();
            if (available) {
                var client = Minecraft.getInstance();
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
