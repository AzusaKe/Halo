package network.azusake.halo.physics;

import java.lang.reflect.Method;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Optional, diagnostics-only access to Iris render-pass state.
 *
 * <p>This class deliberately uses reflection for the Iris API so Halo keeps no
 * compile-time or runtime dependency on Iris.  Failure to probe Iris is
 * reported as {@link Status#UNKNOWN} and never affects capture selection.</p>
 */
final class OptionalIrisDiagnostics {

    private static final String IRIS_MOD_ID = "iris";
    private static final String IRIS_API_CLASS = "net.irisshaders.iris.api.v0.IrisApi";
    private static final Logger LOGGER = LoggerFactory.getLogger("halo");
    private static volatile boolean diagnosticEmitted;

    private static volatile Probe probe;

    private OptionalIrisDiagnostics() { /* utility class */ }

    static Snapshot snapshot() {
        if (!FabricLoader.getInstance().isModLoaded(IRIS_MOD_ID)) {
            return new Snapshot(false, Status.FALSE, Status.FALSE, "not-loaded");
        }

        Probe current = probe;
        if (current == null) {
            synchronized (OptionalIrisDiagnostics.class) {
                current = probe;
                if (current == null) {
                    current = createProbe();
                    probe = current;
                }
            }
        }
        return current.snapshot();
    }

    private static Probe createProbe() {
        try {
            Class<?> apiClass = Class.forName(IRIS_API_CLASS, false,
                OptionalIrisDiagnostics.class.getClassLoader());
            Method getInstance = apiClass.getMethod("getInstance");
            Object api = getInstance.invoke(null);
            Method shaderPackInUse = apiClass.getMethod("isShaderPackInUse");
            Method renderingShadowPass = apiClass.getMethod("isRenderingShadowPass");
            return new ReflectiveProbe(api, shaderPackInUse, renderingShadowPass);
        } catch (ReflectiveOperationException | LinkageError error) {
            warnUnknown(error.getClass().getSimpleName());
            return new FailedProbe(error.getClass().getSimpleName());
        }
    }

    enum Status {
        TRUE,
        FALSE,
        UNKNOWN
    }

    record Snapshot(boolean irisLoaded, Status shaderPackInUse,
                    Status shadowPass, String probe) {}

    private interface Probe {
        Snapshot snapshot();
    }

    private record ReflectiveProbe(Object api, Method shaderPackInUse,
                                   Method renderingShadowPass) implements Probe {
        @Override
        public Snapshot snapshot() {
            try {
                boolean shaders = (boolean) shaderPackInUse.invoke(api);
                boolean shadows = (boolean) renderingShadowPass.invoke(api);
                return new Snapshot(true, status(shaders), status(shadows), "iris-api-v0");
            } catch (ReflectiveOperationException | LinkageError error) {
                warnUnknown(error.getClass().getSimpleName());
                return new Snapshot(true, Status.UNKNOWN, Status.UNKNOWN,
                    error.getClass().getSimpleName());
            }
        }
    }

    private record FailedProbe(String failure) implements Probe {
        @Override
        public Snapshot snapshot() {
            return new Snapshot(true, Status.UNKNOWN, Status.UNKNOWN, failure);
        }
    }

    private static Status status(boolean value) {
        return value ? Status.TRUE : Status.FALSE;
    }

    private static void warnUnknown(String failure) {
        if (!diagnosticEmitted) {
            synchronized (OptionalIrisDiagnostics.class) {
                if (!diagnosticEmitted) {
                    diagnosticEmitted = true;
                    LOGGER.warn("Iris is present but its render pass could not be classified ({}); "
                        + "rejecting head-anchor submissions for safety", failure);
                }
            }
        }
    }
}
