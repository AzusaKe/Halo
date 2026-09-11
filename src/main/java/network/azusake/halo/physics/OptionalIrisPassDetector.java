package network.azusake.halo.physics;

import net.minecraftforge.fml.ModList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;

/** Optional Iris pass probe used as a hard safety gate for anchor captures. */
final class OptionalIrisPassDetector {

    private static final String IRIS_MOD_ID = "iris";
    private static final Logger LOGGER = LoggerFactory.getLogger("halo");
    private static volatile Probe probe;
    private static volatile boolean diagnosticEmitted;

    private OptionalIrisPassDetector() {
    }

    static boolean isMainPass() {
        if (!ModList.get().isLoaded(IRIS_MOD_ID)) {
            return true;
        }
        Probe current = probe;
        if (current == null) {
            synchronized (OptionalIrisPassDetector.class) {
                current = probe;
                if (current == null) {
                    current = createProbe();
                    probe = current;
                }
            }
        }
        try {
            return current.isKnownMainPass();
        } catch (Exception error) {
            warnUnknown(error);
            return false;
        }
    }

    private static Probe createProbe() {
        try {
            Class<?> apiClass = Class.forName("net.irisshaders.iris.api.v0.IrisApi", false,
                OptionalIrisPassDetector.class.getClassLoader());
            Object api = apiClass.getMethod("getInstance").invoke(null);
            Method shadowPass = apiClass.getMethod("isRenderingShadowPass");
            return () -> !(boolean) shadowPass.invoke(api);
        } catch (Throwable error) {
            warnUnknown(error);
            return () -> false;
        }
    }

    private static void warnUnknown(Throwable error) {
        if (!diagnosticEmitted) {
            synchronized (OptionalIrisPassDetector.class) {
                if (!diagnosticEmitted) {
                    diagnosticEmitted = true;
                    LOGGER.warn("Iris is present but its render pass could not be classified; "
                        + "rejecting external head-anchor submissions for safety", error);
                }
            }
        }
    }

    @FunctionalInterface
    private interface Probe {
        boolean isKnownMainPass() throws Exception;
    }
}
