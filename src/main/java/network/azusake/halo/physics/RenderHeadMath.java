package network.azusake.halo.physics;

import network.azusake.halo.api.HeadAnchor;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector4f;

/**
 * Pure math converting a captured player-head render matrix into the 6-DOF
 * head anchor consumed by the halo pipeline.
 *
 * <p>Conventions (verified against 1.20.1 vanilla): the player head cube spans
 * {@code x/y/z in [-4..4] / [-8..0] / [-4..4]} model units (16 px = 1 block),
 * so its geometric centre is {@code (0, -0.25, 0)} blocks in head-local space.
 * The crown (top of the head) is at model {@code -Y} and the face at model
 * {@code -Z}; after the renderer's {@code scale(-1,-1,1)} flip these map to
 * the visually correct world up / look direction.  {@link ModelPart} applies
 * its local transform as {@code translate(pivot/16) * rotationZYX(roll, yaw,
 * pitch) * scale}, which {@link #composeHeadMatrix} reproduces exactly.</p>
 */
public final class RenderHeadMath {

    /** Head box geometric centre in head-local space (blocks). */
    public static final float HEAD_BOX_CENTER_Y = -0.25f;

    private RenderHeadMath() { /* utility class */ }

    /** Rebuild the head's camera-relative world matrix from a capture. */
    public static Matrix4f composeHeadMatrix(RenderHeadCapture.CapturedHead captured) {
        Matrix4f m = new Matrix4f(captured.rootMatrix());
        m.translate(captured.pivotX() / 16f, captured.pivotY() / 16f, captured.pivotZ() / 16f);
        if (captured.pitch() != 0f || captured.yaw() != 0f || captured.roll() != 0f) {
            m.rotate(new Quaternionf().rotationZYX(captured.roll(), captured.yaw(), captured.pitch()));
        }
        m.scale(captured.xScale(), captured.yScale(), captured.zScale());
        return m;
    }

    /** World-space head centre from a camera-relative head matrix. */
    public static Vec3d headCenter(Matrix4f headWorldMatrix, Vec3d cameraPos) {
        Vector4f p = headWorldMatrix.transform(new Vector4f(0f, HEAD_BOX_CENTER_Y, 0f, 1f));
        return new Vec3d(cameraPos.x + p.x, cameraPos.y + p.y, cameraPos.z + p.z);
    }

    /**
     * Extract head orientation as yaw/pitch/roll (degrees, MC convention) that
     * reproduces the rendered head's look direction and crown via
     * {@code HeadFrameMath}.
     *
     * @return {@code {yaw, pitch, roll}} in degrees
     */
    public static float[] toYawPitchRoll(Matrix4f headWorldMatrix) {
        Vector4f origin = headWorldMatrix.transform(new Vector4f(0f, 0f, 0f, 1f));
        Vector4f f4 = headWorldMatrix.transform(new Vector4f(0f, 0f, -1f, 1f));
        Vec3d forward = new Vec3d(f4.x - origin.x, f4.y - origin.y, f4.z - origin.z).normalize();
        Vector4f u4 = headWorldMatrix.transform(new Vector4f(0f, -1f, 0f, 1f));
        Vec3d up = new Vec3d(u4.x - origin.x, u4.y - origin.y, u4.z - origin.z).normalize();
        // Gram-Schmidt against forward so a non-uniform model scale cannot
        // skew the head-up direction.
        up = up.subtract(forward.multiply(up.dotProduct(forward))).normalize();

        float yaw = (float) Math.toDegrees(Math.atan2(-forward.x, forward.z));
        float pitch = (float) Math.toDegrees(Math.asin(clamp(-forward.y)));

        Vec3d worldUp = new Vec3d(0, 1, 0);
        Vec3d right0;
        if (Math.abs(forward.dotProduct(worldUp)) > 0.999) {
            // Same near-parallel fallback as HeadFrameMath.
            float yawRad = (float) Math.toRadians(yaw);
            right0 = new Vec3d(-Math.cos(yawRad), 0, -Math.sin(yawRad));
        } else {
            right0 = forward.crossProduct(worldUp).normalize();
        }
        Vec3d headUp0 = right0.crossProduct(forward).normalize();
        float roll = (float) Math.toDegrees(Math.atan2(up.dotProduct(right0), up.dotProduct(headUp0)));
        return new float[]{yaw, pitch, roll};
    }

    /**
     * Full 6-DOF anchor from a capture.
     *
     * @param viewMatrix the frame's view matrix (world → camera).  Captured
     *                   head matrices are camera-relative (rotated by the
     *                   camera), so they are un-rotated back to world space
     *                   before extracting the position and Euler angles.
     *                   Pass the identity matrix when the capture is already
     *                   in world space (unit tests / degenerate capture).
     */
    public static HeadAnchor toHeadAnchor(RenderHeadCapture.CapturedHead captured, Vec3d cameraPos, Matrix4f viewMatrix) {
        Matrix4f head = viewToWorld(composeHeadMatrix(captured), viewMatrix);
        Vec3d center = headCenter(head, cameraPos);
        float[] ypr = toYawPitchRoll(head);
        return new HeadAnchor(center, ypr[0], ypr[1], ypr[2]);
    }

    /**
     * Convert a camera/view-space matrix back to world space using the frame's
     * view matrix.  The captured head matrix is {@code view × worldMatrix}, so
     * the world matrix is {@code view⁻¹ × viewMatrix}.  A {@code null} view
     * matrix (or a non-invertible one) leaves the matrix unchanged.
     */
    static Matrix4f viewToWorld(Matrix4f viewSpace, Matrix4f viewMatrix) {
        if (viewMatrix == null) {
            return new Matrix4f(viewSpace);
        }
        Matrix4f invView = new Matrix4f(viewMatrix).invert(new Matrix4f());
        if (invView == null) {
            return new Matrix4f(viewSpace);
        }
        return invView.mul(viewSpace, new Matrix4f());
    }

    private static double clamp(double value) {
        return Math.max(-1.0, Math.min(1.0, value));
    }
}
