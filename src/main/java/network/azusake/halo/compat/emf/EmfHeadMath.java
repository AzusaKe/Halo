package network.azusake.halo.compat.emf;

import network.azusake.halo.api.v2.AnchorPose;
import network.azusake.halo.physics.RenderHeadMath;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** Converts an EMF-rendered head part matrix into Halo's 6-DOF anchor. */
public final class EmfHeadMath {

    private EmfHeadMath() {
    }

    /** Convert using the exact camera frame that produced this capture. */
    public static AnchorPose toAnchorPose(EmfHeadCapture.CapturedHead captured) {
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
            return RenderHeadMath.toAnchorPose(worldMatrix, captured.cameraPos());
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

    private static boolean isFinite(Vec3 value) {
        return Double.isFinite(value.x) && Double.isFinite(value.y) && Double.isFinite(value.z);
    }
}
