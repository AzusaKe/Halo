package network.azusake.halo.render;

import network.azusake.halo.api.v2.AnchorPose;
import network.azusake.halo.core.Vec3d;
import network.azusake.halo.physics.RenderHeadMath;
import org.joml.Matrix4f;

/** Removes the GUI root before reusing the existing block-sized head-center/axis conversion. */
public final class PreviewHeadMath {
    private PreviewHeadMath() {}
    public static AnchorPose toAnchor(Matrix4f previewRoot, RenderHeadMath.HeadCapture capture) {
        float determinant = previewRoot.determinant();
        if (!Float.isFinite(determinant) || Math.abs(determinant) < 1e-8f) return null;
        Matrix4f head = new Matrix4f(previewRoot).invert().mul(RenderHeadMath.composeHeadMatrix(capture));
        if (!head.isFinite()) return null;
        try { return RenderHeadMath.toAnchorPose(head, new Vec3d(0, 0, 0)); }
        catch (IllegalArgumentException error) { return null; }
    }
}
