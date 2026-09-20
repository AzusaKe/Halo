package network.azusake.halo.render;

import java.nio.IntBuffer;
import network.azusake.halo.core.render.MeshIndexWriter;
import network.azusake.halo.core.render.TriangleMesh;

/** Resident corners preserve Iris per-face attributes while indices retain core ordering. */
record MeshVertexLayout(boolean iris, boolean sourceQuad) {
    boolean expanded() { return iris && !sourceQuad; }
    int vertexCount(TriangleMesh mesh) { return expanded() ? mesh.triangleCount() * 3 : mesh.vertexCount(); }
    int vertexAt(TriangleMesh mesh, int corner) { return expanded() ? mesh.index(corner) : corner; }
    void sourceIndices(MeshIndexWriter writer, IntBuffer target, boolean mirrored) {
        if (expanded()) writer.writeExpandedSourceOrder(target, mirrored);
        else writer.writeSourceOrder(target, mirrored);
    }
    void sortedIndices(MeshIndexWriter writer, IntBuffer target, boolean mirrored) {
        if (expanded()) writer.writeExpandedPrepared(target, mirrored);
        else writer.writePrepared(target, mirrored);
    }
}
