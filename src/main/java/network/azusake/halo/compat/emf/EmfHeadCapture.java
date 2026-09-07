package network.azusake.halo.compat.emf;

import network.azusake.halo.physics.RenderHeadCapture;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Per-frame capture of the head part rendered by Entity Model Features. */
public final class EmfHeadCapture {

    private static final Logger LOGGER = LoggerFactory.getLogger("halo");
    private static final Map<UUID, CapturedHead> CURRENT = new ConcurrentHashMap<>();
    private static final Map<UUID, CapturedHead> PREVIOUS = new ConcurrentHashMap<>();
    private static final Set<String> EMITTED_DIAGNOSTICS = ConcurrentHashMap.newKeySet();

    private static volatile CaptureFrame captureFrame;

    private EmfHeadCapture() {
    }

    /** Advance the EMF buffers at the same point as vanilla and YSM buffers. */
    public static void beginFrame(Matrix4f viewMatrix, Vec3d cameraPos) {
        PREVIOUS.clear();
        PREVIOUS.putAll(CURRENT);
        CURRENT.clear();
        captureFrame = viewMatrix == null || cameraPos == null
            ? null
            : new CaptureFrame(new Matrix4f(viewMatrix), new Vec3d(cameraPos.x, cameraPos.y, cameraPos.z));
    }

    /** Called by the optional EMFModelPart mixin at the head of its render method. */
    public static void capture(MatrixStack matrices, ModelPart part) {
        Object candidate = part;
        if (!(candidate instanceof EmfPartNameAccess namedPart)
            || !"head".equals(namedPart.halo$getEmfPartName())
            || !part.visible || part.hidden) {
            return;
        }

        LivingEntity entity = RenderHeadCapture.getCurrentEntity();
        CaptureFrame frame = captureFrame;
        if (entity == null || frame == null || matrices == null
            || RenderHeadCapture.isAuxiliaryYsmPass()) {
            return;
        }

        UUID uuid = entity.getUuid();
        if (CURRENT.containsKey(uuid)) {
            return;
        }

        try {
            // EMFModelPart.render receives the stack before ModelPart's local
            // transform is applied.  Reproduce that transform on a temporary
            // stack without mutating EMF's live render stack.
            matrices.push();
            try {
                part.rotate(matrices);
                Matrix4f headMatrix = new Matrix4f(matrices.peek().getPositionMatrix());
                if (!isFinite(headMatrix)) {
                    warnOnce("non-finite-head",
                        "[EMF Compat] rejected a non-finite EMF head matrix; using Halo fallback");
                    return;
                }
                if (CURRENT.putIfAbsent(uuid, new CapturedHead(
                    headMatrix,
                    new Matrix4f(frame.viewMatrix),
                    frame.cameraPos
                )) == null) {
                    infoOnce("head-captured",
                        "[EMF Compat] head matrix captured from EMFModelPart.render");
                }
            } finally {
                matrices.pop();
            }
        } catch (Throwable error) {
            warnOnce("capture-failure",
                "[EMF Compat] EMF head capture failed; using Halo fallback: "
                    + error.getClass().getSimpleName() + ": " + error.getMessage());
        }
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

    static void clearForTests() {
        CURRENT.clear();
        PREVIOUS.clear();
        EMITTED_DIAGNOSTICS.clear();
        captureFrame = null;
    }

    static void recordForTests(UUID uuid, Matrix4f matrix, Matrix4f viewMatrix, Vec3d cameraPos) {
        CURRENT.put(uuid, new CapturedHead(
            new Matrix4f(matrix), new Matrix4f(viewMatrix),
            new Vec3d(cameraPos.x, cameraPos.y, cameraPos.z)));
    }

    static void advanceFrameForTests() {
        PREVIOUS.clear();
        PREVIOUS.putAll(CURRENT);
        CURRENT.clear();
    }

    private static boolean isFinite(Matrix4f matrix) {
        float[] values = new float[16];
        matrix.get(values);
        for (float value : values) {
            if (!Float.isFinite(value)) {
                return false;
            }
        }
        return true;
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

    public record CapturedHead(Matrix4f headMatrix, Matrix4f viewMatrix, Vec3d cameraPos) {
    }

    private record CaptureFrame(Matrix4f viewMatrix, Vec3d cameraPos) {
    }
}
