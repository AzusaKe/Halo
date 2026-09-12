package network.azusake.halo.compat.emf;

import network.azusake.halo.api.v2.AnchorPose;
import network.azusake.halo.physics.RenderHeadMath;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/** Converts an EMF-rendered head part matrix into Halo's 6-DOF anchor. */
public final class EmfHeadMath {

    private EmfHeadMath() {
    }

    /** Convert using the exact camera frame that produced this capture. */
    public static AnchorPose toAnchorPose(EmfHeadCapture.CapturedHead captured) {
        if(captured==null)return null;
        return network.azusake.halo.core.CapturedModelMath.resolve(
            captured.headMatrix()==null?null:captured.headMatrix().get(new float[16]),
            captured.viewMatrix()==null?null:captured.viewMatrix().get(new float[16]),
            captured.cameraPos()==null?null:network.azusake.halo.platform.PlatformTypes.core(captured.cameraPos()),
            new network.azusake.halo.core.Vec3d(0,-.25,0),new network.azusake.halo.core.Vec3d(0,0,-1),new network.azusake.halo.core.Vec3d(0,-1,0));
    }
}
