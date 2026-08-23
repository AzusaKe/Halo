package network.azusake.halo.physics;

import java.lang.reflect.Method;

/**
 * Optional, diagnostics-only access to Iris render-pass state.
 *
 * <p>This class deliberately uses reflection for the Iris API so Halo keeps no
 * compile-time or runtime dependency on Iris or a loader-specific mod-list API.
 * An unavailable API means Iris is not installed; other probe failures are
 * reported as {@link Status#UNKNOWN} and never affect rendering directly.</p>
 */
final class OptionalIrisDiagnostics {

    private static final String IRIS_API_CLASS = "net.irisshaders.iris.api.v0.IrisApi";

    private static volatile Probe probe;

    private OptionalIrisDiagnostics() { /* utility class */ }

    static Snapshot snapshot() {
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
        } catch (ClassNotFoundException error) {
            return NotLoadedProbe.INSTANCE;
        } catch (ReflectiveOperationException | LinkageError error) {
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

    private enum NotLoadedProbe implements Probe {
        INSTANCE;

        @Override
        public Snapshot snapshot() {
            return new Snapshot(false, Status.FALSE, Status.FALSE, "not-loaded");
        }
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
}


