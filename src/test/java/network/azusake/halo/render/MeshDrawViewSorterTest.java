package network.azusake.halo.render;

import java.util.List;
import java.util.Map;
import network.azusake.halo.core.Identifier;
import network.azusake.halo.core.render.MaterialState;
import network.azusake.halo.core.render.MeshDraw;
import network.azusake.halo.core.render.TriangleMesh;
import network.azusake.halo.core.render.VisualResources;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MeshDrawViewSorterTest {
    private static final Identifier TEXTURE = new Identifier("halo:texture");

    @Test void transparentInstancesUseTheSameOuterViewAsGpuSubmission() {
        Identifier left = new Identifier("halo:left");
        Identifier right = new Identifier("halo:right");
        Identifier opaque = new Identifier("halo:opaque");
        var resources = new VisualResources(1, Map.of(
            left, triangle(-2), right, triangle(4), opaque, triangle(0)), Map.of());
        MeshDraw leftDraw = draw(left, true);
        MeshDraw rightDraw = draw(right, true);
        MeshDraw opaqueDraw = draw(opaque, false);
        List<MeshDraw> source = List.of(opaqueDraw, leftDraw, rightDraw);

        List<MeshDraw> sorted = MeshDrawViewSorter.backToFront(source, resources,
            new Matrix4f().rotateY((float) (Math.PI / 2)));
        assertEquals(List.of(opaqueDraw, rightDraw, leftDraw), sorted);
        assertEquals(List.of(opaqueDraw, leftDraw, rightDraw), source);
    }

    @Test void noAllocationFastPathRemainsForZeroOrOneTransparentDraw() {
        Identifier model = new Identifier("halo:single");
        var resources = new VisualResources(1, Map.of(model, triangle(0)), Map.of());
        List<MeshDraw> draws = List.of(draw(model, true));
        assertSame(draws, MeshDrawViewSorter.backToFront(draws, resources, new Matrix4f()));
        List<MeshDraw> repeated = List.of(draw(model, true), draw(model, true));
        assertSame(repeated, MeshDrawViewSorter.backToFront(repeated, resources,
            new Matrix4f().translate(0, 0, -12)));
    }

    private static TriangleMesh triangle(float centerX) {
        return new TriangleMesh(new float[]{centerX - 1,0,0, centerX + 1,0,0, centerX,1,0},
            new float[]{0,0,1,0,.5f,1}, new int[]{0,1,2});
    }

    private static MeshDraw draw(Identifier model, boolean blend) {
        return new MeshDraw(model, TEXTURE, new Matrix4f().get(new float[16]), true, blend, true, !blend,
            1, 1, 1, 1, false, new MaterialState.Mesh(null));
    }
}
