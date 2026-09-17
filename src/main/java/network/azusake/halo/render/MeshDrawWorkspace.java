package network.azusake.halo.render;

import network.azusake.halo.core.render.MeshDraw;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/** Scratch storage owned by one lexical OBJ submission, never shared with nested views. */
final class MeshDrawWorkspace {
    private final Matrix4f local = new Matrix4f();
    private final Matrix4f modelView = new Matrix4f();
    private final Matrix3f normal = new Matrix3f();

    Matrix4f modelView(Matrix4f outer, MeshDraw draw) {
        local.set(draw.transform(0), draw.transform(1), draw.transform(2), draw.transform(3),
            draw.transform(4), draw.transform(5), draw.transform(6), draw.transform(7),
            draw.transform(8), draw.transform(9), draw.transform(10), draw.transform(11),
            draw.transform(12), draw.transform(13), draw.transform(14), draw.transform(15));
        return modelView.set(outer).mul(local);
    }

    Matrix3f normal(Matrix4f transform) {
        normal.set(transform);
        float determinant = normal.determinant();
        if (Float.isFinite(determinant) && Math.abs(determinant) > 1.0e-8f) normal.invert().transpose();
        else normal.identity();
        return normal;
    }
}
