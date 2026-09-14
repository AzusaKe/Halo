package network.azusake.halo.render;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gl.VertexBuffer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
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
            update.created() * 2, update.created() * 4);
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
        private final VertexBuffer flatVertices;
        private final VertexBuffer litVertices;
        private final MeshIndexWriter indices;
        private final ByteBuffer indexBytes;
        private final IntBuffer indexInts;
        private final int flatNormalElements;
        private final int flatMirroredElements;
        private final int flatDynamicElements;
        private final int litNormalElements;
        private final int litMirroredElements;
        private final int litDynamicElements;
        private long dynamicIndexUploads;

        private MeshBuffer(TriangleMesh mesh) {
            this.mesh = mesh;
            indices = new MeshIndexWriter(mesh);
            indexBytes = ByteBuffer.allocateDirect(Math.multiplyExact(indices.indexCount(), Integer.BYTES))
                .order(ByteOrder.nativeOrder());
            indexInts = indexBytes.asIntBuffer();
            flatVertices = new VertexBuffer(VertexBuffer.Usage.STATIC);
            litVertices = new VertexBuffer(VertexBuffer.Usage.STATIC);
            int flatNormal = -1, flatMirrored = -1, flatDynamic = -1;
            int litNormal = -1, litMirrored = -1, litDynamic = -1;
            try {
                uploadFlatVertices(mesh);
                uploadLitVertices(mesh);
                configure(flatVertices);
                configure(litVertices);
                flatNormal = GlStateManager._glGenBuffers();
                flatMirrored = GlStateManager._glGenBuffers();
                flatDynamic = GlStateManager._glGenBuffers();
                litNormal = GlStateManager._glGenBuffers();
                litMirrored = GlStateManager._glGenBuffers();
                litDynamic = GlStateManager._glGenBuffers();
                uploadStatic(flatNormal, false, false, flatVertices);
                uploadStatic(flatMirrored, true, false, flatVertices);
                allocateDynamic(flatDynamic, flatVertices);
                uploadStatic(litNormal, false, true, litVertices);
                uploadStatic(litMirrored, true, true, litVertices);
                allocateDynamic(litDynamic, litVertices);
            } catch (RuntimeException | OutOfMemoryError error) {
                for (int buffer : new int[]{flatNormal, flatMirrored, flatDynamic,
                        litNormal, litMirrored, litDynamic}) {
                    if (buffer >= 0) RenderSystem.glDeleteBuffers(buffer);
                }
                flatVertices.close();
                litVertices.close();
                throw error;
            }
            flatNormalElements = flatNormal;
            flatMirroredElements = flatMirrored;
            flatDynamicElements = flatDynamic;
            litNormalElements = litNormal;
            litMirroredElements = litMirrored;
            litDynamicElements = litDynamic;
        }

        private void configure(VertexBuffer buffer) {
            VertexBufferAccessor accessor = (VertexBufferAccessor) (Object) buffer;
            accessor.halo$setIndexCount(indices.indexCount());
            accessor.halo$setIndexType(VertexFormat.IndexType.INT);
            accessor.halo$setSharedSequentialIndexBuffer(null);
        }

        private void uploadFlatVertices(TriangleMesh mesh) {
            var builder = new BufferBuilder(Math.max(256,
                Math.multiplyExact(mesh.vertexCount(), VertexFormats.POSITION_TEXTURE_COLOR.getVertexSizeByte())));
            builder.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_TEXTURE_COLOR);
            for (int vertex = 0; vertex < mesh.vertexCount(); vertex++) {
                builder.vertex(mesh.x(vertex), mesh.y(vertex), mesh.z(vertex))
                    .texture(mesh.u(vertex), mesh.v(vertex)).color(255, 255, 255, 255).next();
            }
            flatVertices.bind();
            flatVertices.upload(builder.end());
            VertexBuffer.unbind();
        }

        private void uploadLitVertices(TriangleMesh mesh) {
            var format = VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL;
            var builder = new BufferBuilder(Math.max(256,
                Math.multiplyExact(indices.indexCount(), format.getVertexSizeByte())));
            builder.begin(VertexFormat.DrawMode.TRIANGLES, format);
            // Iris derives extended entity attributes such as tangents from each
            // consecutive triangle while BufferBuilder ends. An indexed unique-
            // vertex stream does not preserve those triangle boundaries, so the
            // lit stream deliberately expands to authored triangle-corner order.
            for (int corner = 0; corner < indices.indexCount(); corner++) {
                int vertex = mesh.index(corner);
                builder.vertex(mesh.x(vertex), mesh.y(vertex), mesh.z(vertex))
                    .color(255, 255, 255, 255)
                    .texture(mesh.u(vertex), mesh.v(vertex))
                    .overlay(OverlayTexture.DEFAULT_UV)
                    .light(LightmapTextureManager.MAX_LIGHT_COORDINATE)
                    .normal(mesh.normalX(vertex), mesh.normalY(vertex), mesh.normalZ(vertex))
                    .next();
            }
            litVertices.bind();
            litVertices.upload(builder.end());
            VertexBuffer.unbind();
        }

        private void uploadStatic(int buffer, boolean mirrored, boolean expanded, VertexBuffer owner) {
            indexInts.clear();
            if (expanded) indices.writeExpandedSourceOrder(indexInts, mirrored);
            else indices.writeSourceOrder(indexInts, mirrored);
            indexBytes.clear();
            owner.bind();
            GlStateManager._glBindBuffer(ELEMENT_ARRAY_BUFFER, buffer);
            GlStateManager._glBufferData(ELEMENT_ARRAY_BUFFER, indexBytes, STATIC_DRAW);
            VertexBuffer.unbind();
        }

        private void allocateDynamic(int buffer, VertexBuffer owner) {
            owner.bind();
            GlStateManager._glBindBuffer(ELEMENT_ARRAY_BUFFER, buffer);
            GlStateManager._glBufferData(ELEMENT_ARRAY_BUFFER, indexBytes.capacity(), STREAM_DRAW);
            VertexBuffer.unbind();
        }

        private void draw(MeshDraw draw, Matrix4f outerModelView, Matrix4f projection, ShaderProgram shader) {
            VertexBuffer selected = draw.directionalLighting() ? litVertices : flatVertices;
            boolean expanded = draw.directionalLighting();
            selected.bind();
            if (draw.blend()) {
                indexInts.clear();
                if (expanded) indices.writeExpanded(indexInts, draw, true);
                else indices.write(indexInts, draw, true);
                indexBytes.clear();
                GlStateManager._glBindBuffer(ELEMENT_ARRAY_BUFFER,
                    expanded ? litDynamicElements : flatDynamicElements);
                GlStateManager._glBufferData(ELEMENT_ARRAY_BUFFER, indexBytes, STREAM_DRAW);
                dynamicIndexUploads++;
            } else {
                GlStateManager._glBindBuffer(ELEMENT_ARRAY_BUFFER,
                    expanded
                        ? draw.mirrored() ? litMirroredElements : litNormalElements
                        : draw.mirrored() ? flatMirroredElements : flatNormalElements);
            }
            Matrix4f modelView = new Matrix4f(outerModelView).mul(new Matrix4f().set(draw.localToView()));
            if (draw.directionalLighting()) HaloMeshShader.setNormalMatrix(shader, modelView);
            selected.draw(modelView, projection, shader);
            VertexBuffer.unbind();
        }

        @Override public void close() {
            flatVertices.close();
            litVertices.close();
            RenderSystem.glDeleteBuffers(flatNormalElements);
            RenderSystem.glDeleteBuffers(flatMirroredElements);
            RenderSystem.glDeleteBuffers(flatDynamicElements);
            RenderSystem.glDeleteBuffers(litNormalElements);
            RenderSystem.glDeleteBuffers(litMirroredElements);
            RenderSystem.glDeleteBuffers(litDynamicElements);
        }
    }
}
