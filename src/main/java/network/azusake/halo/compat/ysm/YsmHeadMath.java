package network.azusake.halo.compat.ysm;

import network.azusake.halo.anchor.AnchorPoseMath;
import network.azusake.halo.api.v2.AnchorPose;
import network.azusake.halo.api.v2.AnchorRotation;
import network.azusake.halo.api.v2.AnchorVec3;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/** Converts a final YSM Head locator matrix into Halo's 6-DOF anchor. */
public final class YsmHeadMath {

    private YsmHeadMath() {
    }

    /** Convert using the exact camera frame that produced this capture. */
    public static AnchorPose toAnchorPose(YsmHeadCapture.CapturedHead captured, Vec3d localOffset) {
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
        Vec3d localOffset,
        Vec3d cameraPos,
        Matrix4f viewMatrix
    ) {
        return network.azusake.halo.core.CapturedModelMath.resolve(
            viewSpace==null?null:viewSpace.get(new float[16]),viewMatrix==null?null:viewMatrix.get(new float[16]),
            cameraPos==null?null:network.azusake.halo.platform.PlatformTypes.core(cameraPos),
            localOffset==null?null:network.azusake.halo.platform.PlatformTypes.core(localOffset),
            new network.azusake.halo.core.Vec3d(0,0,-1),new network.azusake.halo.core.Vec3d(0,1,0));
    }
}
