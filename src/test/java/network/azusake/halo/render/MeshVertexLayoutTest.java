package network.azusake.halo.render;

import java.nio.IntBuffer;
import network.azusake.halo.core.render.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MeshVertexLayoutTest {
    private static final TriangleMesh MESH = new TriangleMesh(
        new float[]{0,0,0, 1,0,-3, 1,1,-3, 0,1,0},
        new float[]{0,0, 1,0, 1,1, 0,1}, new int[]{0,1,2, 2,3,0});

    @Test void expandedCornersAndSortedIndicesRecoverExactlyTheSameAuthoredVertices() {
        var writer = new MeshIndexWriter(MESH);
        writer.prepareBackToFrontTransform(.4f, -.6f, 1, -7);
        for (boolean mirrored : new boolean[]{false,true}) {
            for (boolean sorted : new boolean[]{false,true}) {
                var unique = IntBuffer.allocate(6);
                var corners = IntBuffer.allocate(6);
                var nativeLayout = new MeshVertexLayout(false,false);
                var irisLayout = new MeshVertexLayout(true,false);
                if (sorted) {
                    nativeLayout.sortedIndices(writer,unique,mirrored);
                    irisLayout.sortedIndices(writer,corners,mirrored);
                } else {
                    nativeLayout.sourceIndices(writer,unique,mirrored);
                    irisLayout.sourceIndices(writer,corners,mirrored);
                }
                assertEquals(6,irisLayout.vertexCount(MESH));
                for (int i=0;i<6;i++) assertEquals(unique.get(i),irisLayout.vertexAt(MESH,corners.get(i)));
            }
        }
    }
    @Test void billboardsKeepFourCornersForIrisQuadTangentsAndMidpointUv() {
        var quad = new MeshVertexLayout(true,true);
        assertEquals(4,quad.vertexCount(MESH));
        var indices = IntBuffer.allocate(6);
        quad.sourceIndices(new MeshIndexWriter(MESH),indices,false);
        for(int i=0;i<6;i++) assertEquals(MESH.index(i),indices.get(i));
    }
}
