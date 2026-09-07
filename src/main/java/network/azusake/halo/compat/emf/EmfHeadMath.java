package network.azusake.halo.compat.emf;

import network.azusake.halo.api.HeadAnchor;
import network.azusake.halo.physics.RenderHeadMath;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/** Converts an EMF-rendered head part matrix into Halo's 6-DOF anchor. */
public final class EmfHeadMath {

    private EmfHeadMath() {
    }

    /** Convert using the exact camera frame that produced this capture. */
    public static HeadAnchor toHeadAnchor(EmfHeadCapture.CapturedHead captured) {
        if (captured == null || captured.headMatrix() == null
            || captured.viewMatrix() == null || captured.cameraPos() == null
            || !isFinite(captured.headMatrix()) || !isFinite(captured.viewMatrix())
            || !isFinite(captured.cameraPos())) {
            return null;
        }

        Matrix4f inverseView = new Matrix4f(captured.viewMatrix()).invert(new Matrix4f());
        if (!isFinite(inverseView)) {
            return null;
        }
        Matrix4f worldMatrix = inverseView.mul(captured.headMatrix(), new Matrix4f());
        if (!isFinite(worldMatrix)) {
            return null;
        }

        try {
            Vec3d center = RenderHeadMath.headCenter(worldMatrix, captured.cameraPos());
            float[] ypr = RenderHeadMath.toYawPitchRoll(worldMatrix);
            HeadAnchor anchor = new HeadAnchor(center, ypr[0], ypr[1], ypr[2]);
            return isFinite(anchor) ? anchor : null;
        } catch (Throwable ignored) {
            return null;
        }
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

    private static boolean isFinite(Vec3d value) {
        return Double.isFinite(value.x) && Double.isFinite(value.y) && Double.isFinite(value.z);
    }

    private static boolean isFinite(HeadAnchor anchor) {
        return anchor != null && isFinite(anchor.headCenter())
            && Float.isFinite(anchor.yaw())
            && Float.isFinite(anchor.pitch())
            && Float.isFinite(anchor.roll());
    }
}
