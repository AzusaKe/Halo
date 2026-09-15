package network.azusake.halo.compat.ysm;

import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import network.azusake.halo.config.HaloModConfigStore;
import network.azusake.halo.api.v2.*;
import org.joml.Matrix4f;
import org.slf4j.LoggerFactory;

/** Uses the same version-gated Head locator as the world, with a call-local GUI root. */
final class YsmPreviewCapture {
    private static boolean reportedCapture;
    private static boolean reportedFailure;
    private YsmPreviewCapture() {}

    static void capture(Object model, MatrixStack matrices) {
        var scope = HaloAnchorApi.currentPreviewContext();
        if (scope == null || scope.hasModelAnchor() || model == null || matrices == null
                || !HaloModConfigStore.get().isExperimentalYsmAnchorEnabled()) return;
        try {
            Matrix4f head = YsmV265Adapter.captureHeadMatrix(model,
                new Matrix4f(matrices.peek().getPositionMatrix()));
            double[] offset = HaloModConfigStore.get().getExperimentalYsmHeadLocalOffset();
            var pose = YsmHeadMath.toAnchorPose(head, new Vec3d(offset[0], offset[1], offset[2]),
                Vec3d.ZERO, new Matrix4f().set(scope.sceneToView()));
            if (pose != null && YsmHeadCapture.YSM_SOURCE.submitPreview(scope, new PreviewAnchorPose(pose.position().x(), pose.position().y(),
                    pose.position().z(), pose.rotation())) && !reportedCapture) {
                reportedCapture = true;
                LoggerFactory.getLogger("halo").info("[YSM Compat] preview Head locator captured in isolated GUI scope");
            }
        } catch (Throwable error) {
            // A failed preview must not disable or overwrite the independent world capture path.
            if (!reportedFailure) {
                reportedFailure = true;
                LoggerFactory.getLogger("halo").warn("[YSM Compat] preview Head capture failed; skipping this preview", error);
            }
        }
    }
}
