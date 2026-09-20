package network.azusake.halo.compat.ysm;

import net.minecraft.world.phys.Vec3;
import network.azusake.halo.api.v2.AnchorPose;
import org.joml.Matrix4f;

/** Converts a final YSM Head locator matrix into Halo's 6-DOF anchor. */
public final class YsmHeadMath {

    private YsmHeadMath() {
    }

    /** Convert using the exact camera frame that produced this capture. */
    public static AnchorPose toAnchorPose(YsmHeadCapture.CapturedHead captured, Vec3 localOffset) {
        if (captured == null) {
            return null;
        }
        return toAnchorPose(
            captured.headMatrix(),
            localOffset,
            captured.cameraPos(),
            captured.viewMatrix()
        );
    }

    public static AnchorPose toAnchorPose(
        Matrix4f viewSpace,
        Vec3 localOffset,
        Vec3 cameraPos,
        Matrix4f viewMatrix
    ) {
        return network.azusake.halo.core.CapturedModelMath.resolve(
            viewSpace==null?null:viewSpace.get(new float[16]),viewMatrix==null?null:viewMatrix.get(new float[16]),
            cameraPos==null?null:network.azusake.halo.platform.PlatformTypes.core(cameraPos),
            localOffset==null?null:network.azusake.halo.platform.PlatformTypes.core(localOffset),
            new network.azusake.halo.core.Vec3d(0,0,-1),new network.azusake.halo.core.Vec3d(0,1,0));
    }
}
