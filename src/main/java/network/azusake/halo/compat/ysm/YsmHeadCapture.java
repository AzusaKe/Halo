package network.azusake.halo.compat.ysm;

import network.azusake.halo.config.HaloModConfigStore;
import network.azusake.halo.physics.RenderHeadCapture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import org.joml.Matrix4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Per-frame YSM Head locator captures, isolated from the vanilla path. */
public final class YsmHeadCapture {

    private static final Logger LOGGER = LoggerFactory.getLogger("halo");

    private static final Map<UUID, CapturedHead> CURRENT = new ConcurrentHashMap<>();
    private static final Map<UUID, CapturedHead> PREVIOUS = new ConcurrentHashMap<>();
    private static final Set<String> EMITTED_DIAGNOSTICS = ConcurrentHashMap.newKeySet();

    private static volatile boolean adapterBroken;

    private YsmHeadCapture() {
    }

    /**
     * Capture the first YSM base-model pass, then release the bracketed living
     * entity even when YSM skips or cancels its vanilla renderer. Later
     * material passes do not need the context once the UUID-keyed matrix has
     * been stored.
     */
    public static void captureAndReleaseEntity(Object animatedModel, MatrixStack matrices) {
        try {
            capture(animatedModel, matrices);
        } finally {
            RenderHeadCapture.end();
        }
    }

    /** Called by the optional YSM Mixin at the base model render pass. */
    public static void capture(Object animatedModel, MatrixStack matrices) {
        if (!HaloModConfigStore.get().isExperimentalYsmAnchorEnabled()) {
            infoOnce("feature-disabled",
                "[YSM Compat] YSM render hook is active, but experimentalYsmAnchorEnabled=false; "
                    + "using Halo fallback");
            return;
        }
        if (adapterBroken) {
            return;
        }

        LivingEntity entity = RenderHeadCapture.getCurrentEntity();
        if (entity == null) {
            infoOnce("non-living-render",
                "[YSM Compat] ignored a YSM preview or non-living render; waiting for a bracketed living entity");
            return;
        }
        if (animatedModel == null || matrices == null) {
            warnOnce("missing-render-arguments",
                "[YSM Compat] living-entity render hook received missing model or matrix data; using Halo fallback");
            return;
        }

        UUID uuid = entity.getUuid();
        if (CURRENT.containsKey(uuid)) {
            return;
        }

        try {
            Matrix4f headMatrix = YsmV265Adapter.captureHeadMatrix(
                animatedModel,
                new Matrix4f(matrices.peek().getPositionMatrix())
            );
            if (headMatrix == null) {
                warnOnce("unusable-head",
                    "[YSM Compat] rendered YSM model has no finite, non-degenerate Head locator; using Halo fallback");
                return;
            }
            if (CURRENT.putIfAbsent(uuid, new CapturedHead(new Matrix4f(headMatrix))) == null) {
                infoOnce("head-captured",
                    "[YSM Compat] Head matrix captured from a bracketed YSM living-entity render");
            }
        } catch (Throwable error) {
            adapterBroken = true;
            warnOnce("adapter-failure",
                "[YSM Compat] YSM 2.6.5 symbol adapter failed; disabling experimental capture for this session: "
                    + error.getClass().getSimpleName() + ": " + error.getMessage());
        }
    }

    /** Advance exactly once at BEFORE_ENTITIES; previous data lives one frame. */
    public static void advanceFrame() {
        PREVIOUS.clear();
        PREVIOUS.putAll(CURRENT);
        CURRENT.clear();
    }

    public static CapturedHead getCurrent(UUID uuid) {
        return CURRENT.get(uuid);
    }

    public static CapturedHead getPrevious(UUID uuid) {
        return PREVIOUS.get(uuid);
    }

    /** Drop captures that are intentionally ineligible for anchor use. */
    public static void discard(UUID uuid) {
        CURRENT.remove(uuid);
        PREVIOUS.remove(uuid);
    }

    /** Record that the default provider actually returned a YSM-derived anchor. */
    public static void markAnchorConsumed(boolean previousFrame) {
        infoOnce("anchor-consumed",
            previousFrame
                ? "[YSM Compat] YSM anchor consumed by Halo entity provider (previous-frame capture)"
                : "[YSM Compat] YSM anchor consumed by Halo entity provider (current-frame capture)");
    }

    /** Record a finite capture that could not be converted to a finite anchor. */
    public static void markAnchorConversionFailed() {
        warnOnce("anchor-conversion-failure",
            "[YSM Compat] captured Head matrix could not be converted to a finite Halo anchor; using fallback");
    }

    static void clearForTests() {
        CURRENT.clear();
        PREVIOUS.clear();
        EMITTED_DIAGNOSTICS.clear();
        adapterBroken = false;
    }

    static void recordForTests(UUID uuid, Matrix4f matrix) {
        CURRENT.put(uuid, new CapturedHead(new Matrix4f(matrix)));
    }

    private static void warnOnce(String key, String message) {
        if (EMITTED_DIAGNOSTICS.add(key)) {
            LOGGER.warn(message);
        }
    }

    private static void infoOnce(String key, String message) {
        if (EMITTED_DIAGNOSTICS.add(key)) {
            LOGGER.info(message);
        }
    }

    public record CapturedHead(Matrix4f headMatrix) {
    }
}
