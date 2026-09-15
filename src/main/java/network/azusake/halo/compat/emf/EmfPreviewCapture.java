package network.azusake.halo.compat.emf;

import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import network.azusake.halo.render.PlayerPreviewCapture;
import org.slf4j.LoggerFactory;

/** Consumes the actual EMF head after animation, never a vanilla height estimate or world cache. */
final class EmfPreviewCapture {
    private static boolean reportedCapture;
    private static boolean reportedFailure;
    private static final ThreadLocal<Boolean> POSE_ONLY = ThreadLocal.withInitial(() -> false);
    private static final VertexConsumer DISCARD = new VertexConsumer() {
        public VertexConsumer vertex(double x, double y, double z) { return this; }
        public VertexConsumer color(int r, int g, int b, int a) { return this; }
        public VertexConsumer texture(float u, float v) { return this; }
        public VertexConsumer overlay(int u, int v) { return this; }
        public VertexConsumer light(int u, int v) { return this; }
        public VertexConsumer normal(float x, float y, float z) { return this; }
        public void next() {}
        public void fixedColor(int r, int g, int b, int a) {}
        public void unfixColor() {}
    };
    private EmfPreviewCapture() {}

    static boolean isPoseOnly() { return POSE_ONLY.get(); }

    /**
     * EMF evaluates its animation in the part's render override. Reach that override once,
     * then cancel at our existing capture hook before EMF selects textures or emits geometry.
     * This uses the current posed model, including CEM animations, even with no visible body.
     */
    static void capturePose(MatrixStack matrices, ModelPart head) {
        var scope = PlayerPreviewCapture.current();
        if (scope == null || scope.hasModelHead() || isPoseOnly()) return;
        POSE_ONLY.set(true);
        matrices.push();
        try {
            head.render(matrices, DISCARD, 0, 0, 1, 1, 1, 0);
        } catch (Throwable error) {
            reportFailure(error);
        } finally {
            matrices.pop();
            POSE_ONLY.remove();
        }
    }

    static void capture(MatrixStack matrices, ModelPart part) {
        var scope = PlayerPreviewCapture.current();
        if (scope == null || scope.hasModelHead() || matrices == null) return;
        try {
            var pose = EmfHeadMath.toAnchorPose(new EmfHeadCapture.CapturedHead(
                EmfHeadCapture.captureHeadMatrix(matrices, part), scope.root(), Vec3d.ZERO));
            if (scope.captureModelHead(pose) && !reportedCapture) {
                reportedCapture = true;
                LoggerFactory.getLogger("halo").info("[EMF Compat] preview head captured in isolated GUI scope");
            }
        } catch (Throwable error) {
            reportFailure(error);
        }
    }

    private static void reportFailure(Throwable error) {
        if (!reportedFailure) {
            reportedFailure = true;
            LoggerFactory.getLogger("halo").warn("[EMF Compat] preview head capture failed; skipping this preview", error);
        }
    }
}
