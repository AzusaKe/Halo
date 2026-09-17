package network.azusake.halo.render;

import java.util.Random;
import network.azusake.halo.core.Identifier;
import network.azusake.halo.core.render.*;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MeshDrawWorkspaceTest {
    @Test void matricesAreBitExactToTheOriginalPathAndCommandsRemainImmutable() {
        var workspace = new MeshDrawWorkspace();
        var random = new Random(231);
        var id = new Identifier("halo:test");
        for (int i = 0; i < 1000; i++) {
            Matrix4f local = new Matrix4f().translation(random.nextFloat(), random.nextFloat(), random.nextFloat())
                .rotateXYZ(random.nextFloat(), random.nextFloat(), random.nextFloat())
                .scale(i % 7 == 0 ? 0 : -2, 3, i % 11 == 0 ? 1.0e-10f : 4);
            if (i % 5 == 0) local.identity();
            Matrix4f outer = new Matrix4f().rotationYXZ(.1f, .3f, .7f).translate(1, 2, -100);
            if (i % 13 == 0) outer.identity();
            float[] input = local.get(new float[16]);
            var command = new MeshDraw(id, id, input, true, false, true, true, 1,1,1,1,
                false, new MaterialState.Mesh(null));
            Matrix4f expected = new Matrix4f(outer).mul(new Matrix4f().set(command.localToView()));
            Matrix3f normal = new Matrix3f(expected);
            float determinant = normal.determinant();
            if (Float.isFinite(determinant) && Math.abs(determinant) > 1.0e-8f) normal.invert().transpose();
            else normal.identity();
            Matrix4f actual = workspace.modelView(outer, command);
            assertBits(expected.get(new float[16]), actual.get(new float[16]));
            assertBits(normal.get(new float[9]), workspace.normal(actual).get(new float[9]));
            assertBits(input, command.localToView());
            float[] copy = command.localToView(); copy[0] = 999;
            assertBits(input, command.localToView());
            var nested = new MeshDrawWorkspace(); nested.modelView(new Matrix4f().scale(5), command);
            assertBits(expected.get(new float[16]), actual.get(new float[16]));
        }
    }
    private static void assertBits(float[] expected, float[] actual) {
        for (int i = 0; i < expected.length; i++)
            assertEquals(Float.floatToRawIntBits(expected[i]), Float.floatToRawIntBits(actual[i]), "element " + i);
    }
}
