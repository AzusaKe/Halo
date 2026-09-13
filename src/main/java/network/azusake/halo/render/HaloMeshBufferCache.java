package network.azusake.halo.render;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gl.VertexBuffer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import network.azusake.halo.core.render.MeshDraw;
import network.azusake.halo.core.render.MeshIndexWriter;
import network.azusake.halo.core.render.TriangleMesh;
import network.azusake.halo.core.render.VisualResources;
import network.azusake.halo.mixin.VertexBufferAccessor;
import org.joml.Matrix4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Render-thread-owned GPU geometry for one immutable visual-resource generation. */
final class HaloMeshBufferCache implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger("HaloMeshBuffers");
    private static final int ELEMENT_ARRAY_BUFFER = 0x8893;
    private static final int STATIC_DRAW = 0x88E4;
    private static final int STREAM_DRAW = 0x88E0;

    private final long generation;
    private final GenerationMeshCache<MeshBuffer> buffers;
    private final int modelUploads;
    private final int staticIndexUploads;

    private HaloMeshBufferCache(long generation, GenerationMeshCache<MeshBuffer> buffers,
                                int modelUploads, int staticIndexUploads) {
        this.generation = generation;
        this.buffers = buffers;
        this.modelUploads = modelUploads;
        this.staticIndexUploads = staticIndexUploads;
    }

    static HaloMeshBufferCache empty() {
        return new HaloMeshBufferCache(Long.MIN_VALUE, GenerationMeshCache.empty(), 0, 0);
    }

    HaloMeshBufferCache updated(VisualResources resources) {
        RenderSystem.assertOnRenderThread();
        var update = buffers.updated(resources.generation(), resources.meshes(), (id, mesh) -> new MeshBuffer(mesh),
            (id, error) ->
                LOG.error("Could not upload mesh {}; cached rendering will fall back to CPU submission", id, error));
        HaloMeshBufferCache replacement = new HaloMeshBufferCache(resources.generation(), update.cache(),
            update.created(), update.created() * 2);
        Stats stats = replacement.stats();
        LOG.info("Prepared mesh generation {}: {} model VBO upload(s), {} static EBO upload(s), {} unique vertices, {} triangles",
            stats.generation(), stats.modelUploads(), stats.staticIndexUploads(), stats.vertices(), stats.triangles());
        return replacement;
    }

    boolean draw(long expectedGeneration, MeshDraw draw, Matrix4f outerModelView,
                 Matrix4f projection, ShaderProgram shader) {
        if (generation != expectedGeneration) return false;
        MeshBuffer buffer = buffers.get(draw.model());
        if (buffer == null) return false;
        buffer.draw(draw, outerModelView, projection, shader);
        return true;
    }

    @Override public void close() {
        RenderSystem.assertOnRenderThread();
        Stats stats = stats();
        buffers.close();
        if (stats.vertices() > 0) {
            LOG.info("Released mesh generation {}: {} unique vertices, {} triangles, {} dynamic EBO upload(s)",
                stats.generation(), stats.vertices(), stats.triangles(), stats.dynamicIndexUploads());
        }
    }

    Stats stats() {
        int vertices = 0, triangles = 0;
        long dynamic = 0;
        for (MeshBuffer buffer : buffers.values()) {
            vertices += buffer.mesh.vertexCount();
            triangles += buffer.mesh.triangleCount();
            dynamic += buffer.dynamicIndexUploads;
        }
        return new Stats(generation, modelUploads, staticIndexUploads, dynamic, vertices, triangles);
    }

    record Stats(long generation, int modelUploads, int staticIndexUploads,
                 long dynamicIndexUploads, int vertices, int triangles) {}

    private static final class MeshBuffer implements GenerationMeshCache.Owned {
        private final TriangleMesh mesh;
        private final VertexBuffer vertices;
        private final MeshIndexWriter indices;
        private final ByteBuffer indexBytes;
        private final IntBuffer indexInts;
        private final int normalElements;
        private final int mirroredElements;
        private final int dynamicElements;
        private long dynamicIndexUploads;

        private MeshBuffer(TriangleMesh mesh) {
            this.mesh = mesh;
            indices = new MeshIndexWriter(mesh);
            indexBytes = ByteBuffer.allocateDirect(Math.multiplyExact(indices.indexCount(), Integer.BYTES))
                .order(ByteOrder.nativeOrder());
            indexInts = indexBytes.asIntBuffer();
            vertices = new VertexBuffer(VertexBuffer.Usage.STATIC);
            int normal = -1, mirrored = -1, dynamic = -1;
            try {
                uploadVertices(mesh);
                VertexBufferAccessor accessor = (VertexBufferAccessor) (Object) vertices;
                accessor.halo$setIndexCount(indices.indexCount());
                accessor.halo$setIndexType(VertexFormat.IndexType.INT);
                accessor.halo$setSharedSequentialIndexBuffer(null);
                normal = GlStateManager._glGenBuffers();
                mirrored = GlStateManager._glGenBuffers();
                dynamic = GlStateManager._glGenBuffers();
                uploadStatic(normal, false);
                uploadStatic(mirrored, true);
                vertices.bind();
                GlStateManager._glBindBuffer(ELEMENT_ARRAY_BUFFER, dynamic);
                GlStateManager._glBufferData(ELEMENT_ARRAY_BUFFER, indexBytes.capacity(), STREAM_DRAW);
                VertexBuffer.unbind();
            } catch (RuntimeException | OutOfMemoryError error) {
                if (normal >= 0) RenderSystem.glDeleteBuffers(normal);
                if (mirrored >= 0) RenderSystem.glDeleteBuffers(mirrored);
                if (dynamic >= 0) RenderSystem.glDeleteBuffers(dynamic);
                vertices.close();
                throw error;
            }
            normalElements = normal;
            mirroredElements = mirrored;
            dynamicElements = dynamic;
        }

        private void uploadVertices(TriangleMesh mesh) {
            var builder = new BufferBuilder(Math.max(256,
                Math.multiplyExact(mesh.vertexCount(), VertexFormats.POSITION_TEXTURE_COLOR.getVertexSizeByte())));
            builder.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_TEXTURE_COLOR);
            for (int vertex = 0; vertex < mesh.vertexCount(); vertex++) {
                builder.vertex(mesh.x(vertex), mesh.y(vertex), mesh.z(vertex))
                    .texture(mesh.u(vertex), mesh.v(vertex)).color(255, 255, 255, 255).next();
            }
            vertices.bind();
            vertices.upload(builder.end());
            VertexBuffer.unbind();
        }

        private void uploadStatic(int buffer, boolean mirrored) {
            indexInts.clear();
            indices.writeSourceOrder(indexInts, mirrored);
            indexBytes.clear();
            vertices.bind();
            GlStateManager._glBindBuffer(ELEMENT_ARRAY_BUFFER, buffer);
            GlStateManager._glBufferData(ELEMENT_ARRAY_BUFFER, indexBytes, STATIC_DRAW);
            VertexBuffer.unbind();
        }

        private void draw(MeshDraw draw, Matrix4f outerModelView, Matrix4f projection, ShaderProgram shader) {
            vertices.bind();
            if (draw.blend()) {
                indexInts.clear();
                indices.write(indexInts, draw, true);
                indexBytes.clear();
                GlStateManager._glBindBuffer(ELEMENT_ARRAY_BUFFER, dynamicElements);
                GlStateManager._glBufferData(ELEMENT_ARRAY_BUFFER, indexBytes, STREAM_DRAW);
                dynamicIndexUploads++;
            } else {
                GlStateManager._glBindBuffer(ELEMENT_ARRAY_BUFFER,
                    draw.mirrored() ? mirroredElements : normalElements);
            }
            Matrix4f modelView = new Matrix4f(outerModelView).mul(new Matrix4f().set(draw.localToView()));
            vertices.draw(modelView, projection, shader);
            VertexBuffer.unbind();
        }

        @Override public void close() {
            vertices.close();
            RenderSystem.glDeleteBuffers(normalElements);
            RenderSystem.glDeleteBuffers(mirroredElements);
            RenderSystem.glDeleteBuffers(dynamicElements);
        }
    }
}
