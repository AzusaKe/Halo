package network.azusake.halo.compat.ysm;

import network.azusake.halo.config.HaloModConfigStore;
import network.azusake.halo.physics.RenderHeadCapture;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
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
    private static volatile CaptureFrame captureFrame;

    private YsmHeadCapture() {
    }

    /**
     * Capture the first YSM base-model pass, then release the bracketed living
     * entity even when YSM skips or cancels its vanilla renderer. Later
     * material passes do not need the context once the UUID-keyed matrix has
     * been stored.
     */
    public static void captureAndReleaseEntity(Object animatedModel, Object matrixArgument) {
        try {
            if (!(matrixArgument instanceof PoseStack matrices)) {
                warnOnce("invalid-render-arguments",
                    "[YSM Compat] Forge render hook received an unexpected matrix argument type; "
                        + "using Halo fallback");
                return;
            }
            capture(animatedModel, matrices);
        } catch (Throwable error) {
            warnOnce("render-hook-failure",
                "[YSM Compat] Forge render hook failed safely; using Halo fallback: "
                    + error.getClass().getSimpleName() + ": " + error.getMessage());
        } finally {
            RenderHeadCapture.end();
        }
    }

    /** Called by the optional YSM Mixin at the base model render pass. */
    public static void capture(Object animatedModel, PoseStack matrices) {
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
            if (RenderHeadCapture.isAuxiliaryYsmPass()) {
                infoOnce("auxiliary-view-rejected",
                    "[YSM Compat] rejected a YSM shadow/auxiliary render whose root matrix did not match "
                        + "the main world entity pass");
            } else {
                infoOnce("non-living-render",
                    "[YSM Compat] ignored a YSM preview or non-living render; "
                        + "waiting for a bracketed living entity");
            }
            return;
        }
        CaptureFrame frame = captureFrame;
        if (frame == null || !isVisible(frame, entity)) {
            infoOnce("outside-main-frustum",
                "[YSM Compat] ignored a YSM auxiliary-pass render outside the main camera frustum");
            return;
        }
        if (animatedModel == null || matrices == null) {
            warnOnce("missing-render-arguments",
                "[YSM Compat] living-entity render hook received missing model or matrix data; using Halo fallback");
            return;
        }

        UUID uuid = entity.getUUID();
        if (CURRENT.containsKey(uuid)) {
            return;
        }

        try {
            Matrix4f headMatrix = YsmV265Adapter.captureHeadMatrix(
                animatedModel,
                new Matrix4f(matrices.last().pose())
            );
            if (headMatrix == null) {
                warnOnce("unusable-head",
                    "[YSM Compat] rendered YSM model has no finite, non-degenerate Head locator; using Halo fallback");
                return;
            }
            if (CURRENT.putIfAbsent(uuid, new CapturedHead(
                new Matrix4f(headMatrix),
                new Matrix4f(frame.viewMatrix),
                frame.cameraPos
            )) == null) {
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

    /** Snapshot the exact main-camera frame used by captures in this entity pass. */
    public static void beginFrame(Matrix4f viewMatrix, Vec3 cameraPos, Frustum frustum) {
        PREVIOUS.clear();
        PREVIOUS.putAll(CURRENT);
        CURRENT.clear();
        captureFrame = viewMatrix == null || cameraPos == null || frustum == null
            ? null
            : new CaptureFrame(new Matrix4f(viewMatrix), cameraPos, new Frustum(frustum));
    }

    /** Main-camera visibility gate used before consuming cached YSM matrices. */
    public static boolean isVisibleToMainCamera(LivingEntity entity) {
        CaptureFrame frame = captureFrame;
        return frame != null && entity != null && isVisible(frame, entity);
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
        captureFrame = null;
    }

    public static void markDispatcherContextAccepted(LivingEntity entity) {
        infoOnce("dispatcher-context-accepted",
            "[YSM Compat] STEP context: main-pass EntityRenderDispatcher context accepted for "
                + entity.getClass().getName());
    }

    public static void markDispatcherContextRejected() {
        infoOnce("dispatcher-context-rejected",
            "[YSM Compat] STEP context: rejected a living-entity dispatcher context because its root "
                + "matrix did not match the recorded main entity pass");
    }

    public static void markRenderHookInvoked() {
        infoOnce("render-hook-invoked",
            "[YSM Compat] STEP hook: injected Forge living-renderer hook invoked");
    }

    public static void markGenericRenderHookInvoked() {
        infoOnce("generic-render-hook-invoked",
            "[YSM Compat] STEP hook: injected Forge generic-entity renderer hook invoked");
    }

    public static void markProviderInvoked(boolean player) {
        infoOnce(player ? "player-provider-invoked" : "living-provider-invoked",
            player
                ? "[YSM Compat] STEP provider: player YSM-aware anchor provider invoked"
                : "[YSM Compat] STEP provider: generic living-entity YSM anchor provider invoked");
    }

    public static void markProviderMiss(boolean player) {
        infoOnce(player ? "player-provider-miss" : "living-provider-miss",
            player
                ? "[YSM Compat] FALLBACK player: no eligible current/previous YSM Head capture was available"
                : "[YSM Compat] FALLBACK living entity: no eligible current/previous YSM Head capture was available");
    }

    public static void markProviderOutsideFrustum(boolean player) {
        infoOnce(player ? "player-provider-outside-frustum" : "living-provider-outside-frustum",
            player
                ? "[YSM Compat] FALLBACK player: outside the main camera frustum"
                : "[YSM Compat] FALLBACK living entity: outside the main camera frustum");
    }

    public static void markLocalFirstPersonFallback() {
        infoOnce("local-first-person-fallback",
            "[YSM Compat] FALLBACK player: local first-person intentionally uses the camera anchor");
    }

    static void recordForTests(UUID uuid, Matrix4f matrix) {
        CURRENT.put(uuid, new CapturedHead(new Matrix4f(matrix), new Matrix4f(), Vec3.ZERO));
    }

    static void recordForTests(UUID uuid, Matrix4f matrix, Matrix4f viewMatrix, Vec3 cameraPos) {
        CURRENT.put(uuid, new CapturedHead(
            new Matrix4f(matrix), new Matrix4f(viewMatrix), cameraPos));
    }

    static void advanceFrameForTests() {
        PREVIOUS.clear();
        PREVIOUS.putAll(CURRENT);
        CURRENT.clear();
    }

    private static boolean isVisible(CaptureFrame frame, LivingEntity entity) {
        try {
            return frame.frustum.isVisible(entity.getBoundingBoxForCulling());
        } catch (Throwable error) {
            warnOnce("frustum-check-failure",
                "[YSM Compat] main-camera frustum check failed; rejecting auxiliary capture: "
                    + error.getClass().getSimpleName());
            return false;
        }
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

    public record CapturedHead(Matrix4f headMatrix, Matrix4f viewMatrix, Vec3 cameraPos) {
    }

    private record CaptureFrame(Matrix4f viewMatrix, Vec3 cameraPos, Frustum frustum) {
    }
}
