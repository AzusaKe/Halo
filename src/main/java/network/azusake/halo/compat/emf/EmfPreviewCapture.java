package network.azusake.halo.compat.emf;

import net.minecraft.client.model.geom.ModelPart;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.phys.Vec3;
import network.azusake.halo.api.v2.*;
import org.joml.Matrix4f;
import org.slf4j.LoggerFactory;

/** Consumes the actual EMF head after animation, never a vanilla height estimate or world cache. */
final class EmfPreviewCapture {
    private static boolean reportedCapture;
    private static boolean reportedFailure;
    private static final ThreadLocal<ModelPart> POSE_ONLY = new ThreadLocal<>();
    private static final VertexConsumer DISCARD = new VertexConsumer() {
        public VertexConsumer setColor(int color) { return this; }
        public VertexConsumer setLineWidth(float width){return this;}
        public VertexConsumer addVertex(float x, float y, float z) { return this; }
        public VertexConsumer setColor(int r, int g, int b, int a) { return this; }
        public VertexConsumer setUv(float u, float v) { return this; }
        public VertexConsumer setUv1(int u, int v) { return this; }
        public VertexConsumer setUv2(int u, int v) { return this; }
        public VertexConsumer setNormal(float x, float y, float z) { return this; }
    };
    private EmfPreviewCapture() {}

    static boolean isPoseOnly() { return POSE_ONLY.get() != null; }
    static boolean isPoseOnlyHead(ModelPart part) { return POSE_ONLY.get() == part; }

    /**
     * EMF evaluates its animation in the part's render override. Reach that override once,
     * then cancel at our existing capture hook before EMF selects textures or emits geometry.
     * This uses the current posed model, including CEM animations, even with no visible body.
     */
    static void capturePose(PoseStack matrices, ModelPart head) {
        var scope = HaloAnchorApi.currentPreviewContext();
        if (scope == null || scope.hasModelAnchor() || isPoseOnly()) return;
        POSE_ONLY.set(head);
        matrices.pushPose();
        try {
            head.render(matrices, DISCARD, 0, 0, 0xFFFFFFFF);
        } catch (Throwable error) {
            reportFailure(error);
        } finally {
            matrices.popPose();
            POSE_ONLY.remove();
        }
    }

    static void capture(PoseStack matrices, ModelPart part) {
        var scope = HaloAnchorApi.currentPreviewContext();
        if (scope == null || scope.hasModelAnchor() || matrices == null) return;
        try {
            var pose = EmfHeadMath.toAnchorPose(new EmfHeadCapture.CapturedHead(
                EmfHeadCapture.captureHeadMatrix(matrices, part), new Matrix4f().set(scope.sceneToView()), Vec3.ZERO));
            if (pose != null && EmfHeadCapture.EMF_SOURCE.submitPreview(scope, new PreviewAnchorPose(pose.position().x(), pose.position().y(),
                    pose.position().z(), pose.rotation())) && !reportedCapture) {
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
