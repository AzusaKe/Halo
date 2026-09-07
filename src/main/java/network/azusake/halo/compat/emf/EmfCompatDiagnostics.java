package network.azusake.halo.compat.emf;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;

/** Runtime status shared by the optional Mixin plugin and Forge notifier. */
public final class EmfCompatDiagnostics {

    public static final String USER_ERROR_MESSAGE =
        "当前Halo模组的EMF兼容代码无法再适用于加载版本的emf模组，请前往源码库汇报";

    private static final Logger LOGGER = LoggerFactory.getLogger("halo");
    private static final AtomicBoolean INCOMPATIBLE = new AtomicBoolean(false);
    private static final AtomicBoolean CHAT_REPORTED = new AtomicBoolean(false);
    private static volatile String detail = "unknown";

    private EmfCompatDiagnostics() {
    }

    public static void reportIncompatible(String reason) {
        detail = reason == null || reason.isBlank() ? "unknown" : reason;
        if (INCOMPATIBLE.compareAndSet(false, true)) {
            LOGGER.error("[EMF Compat] {} ({})", USER_ERROR_MESSAGE, detail);
        }
    }

    public static boolean isIncompatible() {
        return INCOMPATIBLE.get();
    }

    public static String detail() {
        return detail;
    }

    public static boolean markChatReported() {
        return isIncompatible() && CHAT_REPORTED.compareAndSet(false, true);
    }
}
