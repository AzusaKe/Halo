package network.azusake.halo.compat.ysm;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.phys.Vec3;
import network.azusake.halo.api.v2.AnchorSource;
import network.azusake.halo.api.v2.HaloAnchorApi;
import network.azusake.halo.config.HaloModConfigStore;
import network.azusake.halo.physics.OptionalIrisPassDetector;
import network.azusake.halo.physics.RenderHeadCapture;
import org.joml.Matrix4f;
import org.slf4j.LoggerFactory;
/** YSM submit-time capture; the core host owns world sample lifetime and arbitration. */
public final class YsmHeadCapture {
    static final AnchorSource YSM_SOURCE = HaloAnchorApi.register("halo:ysm");
    private static boolean reportedFailure;
    private static boolean reportedCapture;
    private YsmHeadCapture() {}
    public static void capture(Object renderData, PoseStack matrices) {
        if (HaloAnchorApi.isPreviewRendering()) {
            YsmPreviewCapture.capture(renderData, matrices);
            return;
        }
        var entity = RenderHeadCapture.currentEntity();
        if (entity == null || !entity.living() || RenderHeadCapture.isAuxiliaryPass()
                || !OptionalIrisPassDetector.isMainPass()
                || !HaloModConfigStore.get().isExperimentalYsmAnchorEnabled()) return;
        try {
            Matrix4f head = YsmV265Adapter.captureHeadMatrix(renderData, new Matrix4f(matrices.last().pose()));
            if (head == null) return;
            double[] offset = HaloModConfigStore.get().getExperimentalYsmHeadLocalOffset();
            // Dispatcher submit roots are camera-relative world space in 26.1.
            var camera = net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera();
            var pose = YsmHeadMath.toAnchorPose(head, new Vec3(offset[0], offset[1], offset[2]),
                camera.position(), new Matrix4f());
            if (pose != null && YSM_SOURCE.submit(entity.uuid(), pose) && !reportedCapture) {
                reportedCapture = true;
                LoggerFactory.getLogger("halo").info("[YSM Compat] world Head locator captured in main-camera scope");
            }
        } catch (Throwable error) {
            if (!reportedFailure) {
                reportedFailure = true;
                LoggerFactory.getLogger("halo").warn("[YSM Compat] world Head capture failed; using fallback", error);
            }
        }
    }
}
