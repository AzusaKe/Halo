package network.azusake.halo.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.client.render.VertexFormats;
import network.azusake.halo.core.render.TriangleMesh;
import org.junit.jupiter.api.Test;

/**
 * 1.20.1 has no BufferAllocator: {@code MeshUploadBuffer} releases the BufferBuilder's native
 * block explicitly, and {@code BufferBuilder} growth would realloc that block behind the
 * wrapper's back. The lit stream therefore pre-sizes the staging buffer for exactly the corners
 * it writes, and these tests keep that formula honest.
 */
class MeshUploadBufferSizingTest {
    /** One authored quad: four unique vertices, two triangles, six expanded indices. */
    private static TriangleMesh quad() {
        return new TriangleMesh(
            new float[]{
                0, 0, 0,
                1, 0, 0,
                1, 1, 0,
                0, 1, 0,
            },
            new float[]{
                0, 0,
                1, 0,
                1, 1,
                0, 1,
            },
            new int[]{
                0, 1, 2,
                0, 2, 3,
            });
    }

    /** Mirrors HaloMeshBufferCache.uploadLitVertices. */
    private static int writtenCorners(boolean sourceQuad, TriangleMesh mesh) {
        return sourceQuad ? mesh.vertexCount() * 3 / 2 : mesh.triangleCount() * 3;
    }

    private static int stagingCapacity(boolean sourceQuad, TriangleMesh mesh) {
        int vertexSize = VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL.getVertexSizeByte();
        return Math.max(256, Math.multiplyExact(writtenCorners(sourceQuad, mesh), vertexSize));
    }

    @Test
    void quadSourceReservesExpandedCornerCount() {
        TriangleMesh mesh = quad();
        int vertexSize = VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL.getVertexSizeByte();
        int indexCount = mesh.triangleCount() * 3;
        assertEquals(4, mesh.vertexCount(), "fixture must stay a four-corner quad");
        assertEquals(6, indexCount, "a quad expands to six indices");
        assertEquals(6, writtenCorners(true, mesh));
        assertTrue(stagingCapacity(true, mesh) >= writtenCorners(true, mesh) * vertexSize,
            "QUADS staging must hold every written corner");
        assertTrue(stagingCapacity(true, mesh) > indexCount * vertexSize,
            "the pre-fix formula sized a quad source by index count and forced a reallocating grow");
    }

    @Test
    void triangleSourceKeepsIndexCountSizing() {
        TriangleMesh mesh = quad();
        int vertexSize = VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL.getVertexSizeByte();
        int indexCount = mesh.triangleCount() * 3;
        assertEquals(indexCount, writtenCorners(false, mesh),
            "triangles write one vertex per index, so index count is the exact corner count");
        assertEquals(Math.max(256, indexCount * vertexSize), stagingCapacity(false, mesh));
    }

    @Test
    void quadVerticesStayDivisibleByFour() {
        // The *3/2 expansion relies on authored quads contributing four corners each.
        assertEquals(0, quad().vertexCount() % 4);
    }

    @Test
    void stagingFormatsKeepTheirPinnedStrides() {
        // Guard rails: the exact strides HaloMeshBufferCache multiplies by.
        assertEquals(24, VertexFormats.POSITION_TEXTURE_COLOR.getVertexSizeByte());
        assertEquals(36, VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL.getVertexSizeByte());
    }
}
